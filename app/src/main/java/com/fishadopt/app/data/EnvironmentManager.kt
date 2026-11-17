package com.fishadopt.app.data

import android.content.Context
import android.content.res.AssetManager
import android.os.StatFs
import android.system.Os
import com.fishadopt.app.util.Checksum
import com.fishadopt.app.util.VolumeMerger
import com.fishadopt.app.util.VolumeSource
import com.github.luben.zstd.ZstdInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream

/**
 * 环境管理（PRD 4.2）：RootFS 的解压、校验、修复与重置。
 * 后台耗时最长、失败率最高的模块，具备完整进度反馈与断点续接能力。
 */
class EnvironmentManager(
    private val context: Context,
    private val log: LogRepository,
) {
    private val _state = MutableStateFlow(EnvironmentState.UNINITIALIZED)
    val state: StateFlow<EnvironmentState> = _state.asStateFlow()

    private val _progress = MutableStateFlow(0f)
    val progress: StateFlow<Float> = _progress.asStateFlow()

    val rootfsDir: File get() = File(context.filesDir, "rootfs")
    private val tempDir: File get() = File(context.filesDir, "tmp")
    private val mergedZst: File get() = File(tempDir, "rootfs.tar.zst")
    private val mergedTar: File get() = File(tempDir, "rootfs.tar")
    private val progressFile: File get() = File(tempDir, "merge.progress")

    /**
     * proot-rs 以原生库形式随包分发，位于可执行的 native 库目录（避免 Android 10+ 禁止
     * 从应用可写数据目录执行二进制的问题）。
     */
    val prootBinary: File get() = File(context.applicationInfo.nativeLibraryDir, "libproot_rs.so")

    /** 内置 DSH 版本号（来自清单）。 */
    fun installedVersion(): String? = readManifest()?.version

    /** 刷新当前环境状态（不缓存，每次启动重算）。 */
    suspend fun refreshState() = withContext(Dispatchers.IO) {
        if (_state.value == EnvironmentState.EXTRACTING) return@withContext
        _state.value = when {
            !rootfsDir.exists() || (rootfsDir.list()?.isEmpty() == true) -> EnvironmentState.UNINITIALIZED
            verify() -> EnvironmentState.READY
            else -> EnvironmentState.CORRUPTED
        }
    }

    /**
     * 完整准备流程：空间校验 → 分卷合并 → zstd 解压 → tar 释放 → 校验。
     * @return 是否成功进入 READY 状态。
     */
    suspend fun prepareEnvironment(): Boolean = withContext(Dispatchers.IO) {
        val volumes = listVolumes()
        if (volumes.isEmpty()) {
            log.error("未找到 RootFS 资源（assets 缺少 rootfs.zst.* 分卷），请先执行打包脚本")
            _state.value = EnvironmentState.CORRUPTED
            return@withContext false
        }

        _state.value = EnvironmentState.EXTRACTING
        _progress.value = 0f

        try {
            // 1. 空间校验（解压前阻断，PRD 表9）
            val required = volumes.sumOf { it.size } * EXPANSION_FACTOR
            val available = availableBytes()
            if (available < required) {
                log.error("存储空间不足：约需 ${formatBytes(required)}，可用 ${formatBytes(available)}")
                _state.value = EnvironmentState.CORRUPTED
                return@withContext false
            }
            tempDir.mkdirs()

            // 2. 分卷合并（0~40%，支持断点续接）
            log.info("开始合并 RootFS 分卷（共 ${volumes.size} 卷）")
            val merged = VolumeMerger(volumes, mergedZst, progressFile).merge { p ->
                _progress.value = p.ratio * MERGE_FRACTION
            }
            if (!merged) {
                log.error("分卷合并未完成")
                _state.value = EnvironmentState.CORRUPTED
                return@withContext false
            }
            _progress.value = MERGE_FRACTION
            log.info("分卷合并完成")

            // 3. zstd 解压（~40~60%）
            log.info("正在解压 RootFS…")
            decompressZstd(mergedZst, mergedTar)
            _progress.value = DECOMPRESS_FRACTION
            log.info("解压完成")

            // 4. tar 释放（~60~95%）
            log.info("正在释放文件…")
            extractTar(mergedTar, rootfsDir)
            _progress.value = EXTRACT_FRACTION
            log.info("文件释放完成")

            // 5. 校验（~95~100%）
            if (!verify()) {
                log.error("RootFS 校验失败：关键文件缺失或哈希不匹配")
                _state.value = EnvironmentState.CORRUPTED
                return@withContext false
            }
            _progress.value = 1f
            cleanupTemp()
            _state.value = EnvironmentState.READY
            log.info("环境就绪，内置 DSH 版本：${installedVersion()}")
            true
        } catch (e: Exception) {
            log.error("环境准备失败：${e.message}")
            _state.value = EnvironmentState.CORRUPTED
            false
        }
    }

    /** 重置运行环境，回到未初始化状态（PRD 4.2：删除容器内全部数据、插件与配置）。 */
    suspend fun reset() = withContext(Dispatchers.IO) {
        _state.value = EnvironmentState.UNINITIALIZED
        _progress.value = 0f
        rootfsDir.deleteRecursively()
        tempDir.deleteRecursively()
        log.info("运行环境已重置")
    }

    // ---- 私有实现 ----

    private fun listVolumes(): List<VolumeSource> {
        // 从 rootfs_volumes.json 读各分卷大小（Android 对「压缩 asset」调 openFd 会抛异常，故免 openFd）
        val sizes = readVolumeSizes()
        return sizes.entries
            .sortedBy { it.key.substringAfterLast('.').toIntOrNull() ?: Int.MAX_VALUE }
            .map { (name, size) -> AssetVolumeSource(context.assets, name, size) }
    }

    private fun readVolumeSizes(): Map<String, Long> {
        return try {
            val json = context.assets.open("rootfs_volumes.json")
                .bufferedReader().use { it.readText() }
            val obj = org.json.JSONObject(json)
            val map = mutableMapOf<String, Long>()
            val keys = obj.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                map[k] = obj.getLong(k)
            }
            map
        } catch (_: Exception) {
            emptyMap()
        }
    }

    private fun decompressZstd(src: File, dst: File) {
        dst.parentFile?.mkdirs()
        ZstdInputStream(FileInputStream(src)).use { ins ->
            FileOutputStream(dst).use { out ->
                val buf = ByteArray(128 * 1024)
                while (true) {
                    val n = ins.read(buf)
                    if (n < 0) break
                    out.write(buf, 0, n)
                }
            }
        }
    }

    private fun extractTar(tarFile: File, dest: File) {
        dest.mkdirs()
        val destCanonical = dest.canonicalPath
        TarArchiveInputStream(FileInputStream(tarFile)).use { tar ->
            while (true) {
                val entry = tar.nextEntry ?: break
                val outFile = File(dest, entry.name)
                val canonical = outFile.canonicalPath
                val withinDest = canonical == destCanonical ||
                    canonical.startsWith(destCanonical + File.separator)
                if (!withinDest) {
                    log.warn("跳过越界条目：${entry.name}")
                    continue
                }
                when {
                    entry.isSymbolicLink -> {
                        outFile.parentFile?.mkdirs()
                        Os.symlink(entry.linkName, outFile.absolutePath)
                    }
                    entry.isDirectory -> outFile.mkdirs()
                    else -> {
                        outFile.parentFile?.mkdirs()
                        outFile.outputStream().use { out -> tar.copyTo(out) }
                    }
                }
            }
        }
    }

    private fun verify(): Boolean {
        val manifest = readManifest() ?: return false
        for (rel in manifest.criticalFiles) {
            if (!File(rootfsDir, rel).exists()) return false
        }
        for ((rel, expected) in manifest.hashes) {
            val f = File(rootfsDir, rel)
            if (!f.exists()) return false
            if (Checksum.sha256(f) != expected) return false
        }
        return true
    }

    private fun readManifest(): RootfsManifest? = try {
        context.assets.open(RootfsManifest.ASSET_NAME)
            .bufferedReader().use { it.readText() }
            .let { RootfsManifest.fromJson(it) }
    } catch (_: Exception) {
        null
    }

    private fun availableBytes(): Long = StatFs(context.filesDir.absolutePath).availableBytes

    private fun cleanupTemp() {
        mergedZst.delete()
        mergedTar.delete()
        progressFile.delete()
    }

    private fun formatBytes(bytes: Long): String {
        val gb = bytes / 1024.0 / 1024.0 / 1024.0
        val mb = bytes / 1024.0 / 1024.0
        return when {
            gb >= 1 -> String.format("%.2f GB", gb)
            mb >= 1 -> String.format("%.0f MB", mb)
            else -> String.format("%.0f KB", bytes / 1024.0)
        }
    }

    private class AssetVolumeSource(
        private val assets: AssetManager,
        private val name: String,
        override val size: Long,
    ) : VolumeSource {
        override fun openStream(): InputStream = assets.open(name)
    }

    private companion object {
        const val MERGE_FRACTION = 0.4f
        const val DECOMPRESS_FRACTION = 0.6f
        const val EXTRACT_FRACTION = 0.95f

        /** 空间估算系数：压缩包 + 解压 tar + 释放目录的保守余量。 */
        const val EXPANSION_FACTOR = 3L
    }
}
