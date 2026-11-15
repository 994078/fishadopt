package com.fishadopt.app.util

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream

/** 分卷数据源：提供每个分卷的大小与可打开的流（兼容文件与 assets）。 */
interface VolumeSource {
    val size: Long
    fun openStream(): InputStream
}

/** 基于文件的卷。 */
class FileVolumeSource(private val file: File) : VolumeSource {
    override val size: Long get() = file.length()
    override fun openStream(): InputStream = FileInputStream(file)
}

/**
 * 分卷合并 + 断点续接（PRD 4.2）。
 *
 * 将 `rootfs.zst.001` … `rootfs.zst.NNN` 按序拼接为目标文件。合并进度写入
 * [progressFile]（记录已写入字节数），进程被杀或应用切走后，再次调用 [merge]
 * 会从断点继续而非从头重来。
 */
class VolumeMerger(
    private val volumes: List<VolumeSource>,
    private val target: File,
    private val progressFile: File,
) {

    data class Progress(val written: Long, val total: Long) {
        val ratio: Float
            get() = if (total <= 0L) 0f else (written.toFloat() / total.toFloat()).coerceIn(0f, 1f)
    }

    /** 全部卷的字节总数。 */
    val totalBytes: Long = volumes.sumOf { it.size }

    /**
     * 执行（或续接）合并。每写完一个卷回调一次进度。
     * @return 是否已完整合并（写入全部字节）。
     */
    fun merge(onProgress: (Progress) -> Unit = {}): Boolean {
        var start = readProgress().coerceAtLeast(0L)
        // 目标文件比进度短说明曾被截断/损坏，回退重来
        if (target.length() < start) {
            start = 0L
            target.delete()
        }
        val sizes = volumes.map { it.size }
        val (startVol, startOffset) = seekPosition(sizes, start)

        target.parentFile?.mkdirs()
        var written = start
        val buf = ByteArray(64 * 1024)

        FileOutputStream(target, true).use { out -> // 追加模式，续接时不截断
            for (i in startVol until volumes.size) {
                val vol = volumes[i]
                val skip = if (i == startVol) startOffset else 0L
                vol.openStream().use { ins ->
                    var remaining = skip
                    while (remaining > 0) {
                        val want = minOf(buf.size.toLong(), remaining).toInt()
                        val n = ins.read(buf, 0, want)
                        if (n < 0) break
                        remaining -= n
                    }
                    while (true) {
                        val n = ins.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        written += n
                    }
                }
                writeProgress(written)
                onProgress(Progress(written, totalBytes))
            }
        }

        val done = written >= totalBytes
        if (done) progressFile.delete()
        return done
    }

    private fun readProgress(): Long =
        progressFile.takeIf { it.exists() }?.readText()?.trim()?.toLongOrNull() ?: 0L

    private fun writeProgress(value: Long) {
        progressFile.parentFile?.mkdirs()
        progressFile.writeText(value.toString())
    }

    companion object {
        /**
         * 根据全局字节偏移定位到 (卷下标, 卷内偏移)。
         * 偏移越过总长时返回 (卷数, 0)，表示已合并完成。
         */
        fun seekPosition(sizes: List<Long>, absoluteOffset: Long): Pair<Int, Long> {
            var off = absoluteOffset.coerceAtLeast(0L)
            for ((i, size) in sizes.withIndex()) {
                if (off < size) return i to off
                off -= size
            }
            return sizes.size to 0L
        }
    }
}
