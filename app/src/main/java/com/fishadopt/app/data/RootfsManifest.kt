package com.fishadopt.app.data

import org.json.JSONObject

/**
 * RootFS 清单（由 packaging/package_assets.sh 生成 `rootfs_manifest.json` 放入 assets）。
 * 用于环境校验（PRD 4.2：关键文件存在性与哈希双重判断）。
 */
data class RootfsManifest(
    /** 内置 DSH 版本号。 */
    val version: String,
    /** 关键文件相对路径列表（必须存在）。 */
    val criticalFiles: List<String>,
    /** 相对路径 -> sha256（可为空，表示只做存在性校验）。 */
    val hashes: Map<String, String>,
) {
    companion object {
        const val ASSET_NAME = "rootfs_manifest.json"

        fun fromJson(json: String): RootfsManifest {
            val obj = JSONObject(json)
            val critical = obj.optJSONArray("criticalFiles")
            val files = mutableListOf<String>()
            if (critical != null) {
                for (i in 0 until critical.length()) files.add(critical.getString(i))
            }
            val hashObj = obj.optJSONObject("hashes")
            val hashes = mutableMapOf<String, String>()
            if (hashObj != null) {
                val keys = hashObj.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    hashes[k] = hashObj.getString(k)
                }
            }
            return RootfsManifest(
                version = obj.optString("version", "unknown"),
                criticalFiles = files,
                hashes = hashes,
            )
        }
    }
}
