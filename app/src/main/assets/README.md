# assets 目录说明

本目录存放运行时必需的二进制与数据分卷，**不在源码仓库内**，由
`packaging/package_assets.sh` 在打包阶段生成/注入：

- `proot-rs`        — arm64 静态编译的 proot-rs 二进制（GPLv2），构建时下载并校验 SHA256。
- `rootfs.zst.001` ~ `rootfs.zst.NNN` — Debian arm64 根文件系统分卷（zstd 压缩），
  由 `rootfs/build_rootfs.sh` 产出后经 `packaging/package_assets.sh` 分卷放入。

首次启动时，应用将这些资源解压到应用私有目录（`filesDir/rootfs`），
解压支持断点续接与关键文件哈希校验，详见 `EnvironmentManager`。

> 未注入这些文件时应用仍可编译，但运行时环境将停留在“未初始化”状态。
