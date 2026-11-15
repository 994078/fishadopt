#!/usr/bin/env bash
# 一键领养小肥鱼 —— 打包资产脚本
#
# 职责：
#   1. 下载并校验 proot-rs arm64 静态二进制，以原生库形式写入 jniLibs
#      （Android 10+ 禁止从应用可写 data 目录执行二进制，需走原生库目录）；
#   2. 将 rootfs.tar.zst 分卷（默认 100MB/卷）写入 assets；
#   3. 复制校验清单 rootfs_manifest.json 到 assets。
#
# 前置：先执行 rootfs/build_rootfs.sh 产出 build/rootfs/rootfs.tar.zst 与清单。
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
BUILD_DIR="$REPO_ROOT/build/rootfs"
ASSETS_DIR="$REPO_ROOT/app/src/main/assets"
JNI_DIR="$REPO_ROOT/app/src/main/jniLibs/arm64-v8a"

# ---- proot-rs 配置（默认使用 proot-me/proot-rs 官方 v0.1.0 的 arm64 Android 变体）----
PROOT_RS_URL="${PROOT_RS_URL:-https://github.com/proot-me/proot-rs/releases/download/v0.1.0/proot-rs-v0.1.0-aarch64-linux-android.tar.gz}"
PROOT_RS_SHA256="${PROOT_RS_SHA256:-}"
# 解包后二进制文件名（proot-rs 官方 tar.gz 内为 proot-rs）
PROOT_RS_BIN_NAME="${PROOT_RS_BIN_NAME:-proot-rs}"

# ---- 分卷大小 ----
CHUNK_SIZE="${CHUNK_SIZE:-100M}"

mkdir -p "$ASSETS_DIR" "$JNI_DIR"

# 1. proot-rs
echo "==> 处理 proot-rs"
if [ -n "$PROOT_RS_URL" ]; then
  TMP="$BUILD_DIR/proot-rs-dl"
  rm -rf "$TMP"; mkdir -p "$TMP"
  curl -fL "$PROOT_RS_URL" -o "$TMP/proot-rs.dl"
  # 官方发布为 .tar.gz，需解包；也兼容裸二进制
  case "$PROOT_RS_URL" in
    *.tar.gz|*.tgz|*.tar) tar -xf "$TMP/proot-rs.dl" -C "$TMP" ;;
  esac
  BIN=$(find "$TMP" -type f -name "$PROOT_RS_BIN_NAME" | head -n 1)
  if [ -z "$BIN" ]; then
    echo "    [错误] 未在下载内容中找到 $PROOT_RS_BIN_NAME"; exit 1
  fi
  cp "$BIN" "$JNI_DIR/libproot_rs.so"
  chmod +x "$JNI_DIR/libproot_rs.so"
  if [ -n "$PROOT_RS_SHA256" ]; then
    echo "$PROOT_RS_SHA256  $JNI_DIR/libproot_rs.so" | sha256sum -c -
    echo "    已下载并校验 proot-rs"
  else
    echo "    [警告] 未设置 PROOT_RS_SHA256，已解包但未校验，建议补填校验值"
  fi
  rm -rf "$TMP"
else
  echo "    [警告] 未设置 PROOT_RS_URL，跳过下载（请手动放置 $JNI_DIR/libproot_rs.so）"
fi

# 2. RootFS 分卷 + 清单
echo "==> 生成 RootFS 分卷"
ZST="$BUILD_DIR/rootfs.tar.zst"
if [ ! -f "$ZST" ]; then
  echo "    [错误] 缺少 $ZST，请先执行 rootfs/build_rootfs.sh"
  exit 1
fi
rm -f "$ASSETS_DIR"/rootfs.zst.*
split -d -a 3 -b "$CHUNK_SIZE" "$ZST" "$ASSETS_DIR/rootfs.zst."
echo "    分卷数：$(ls "$ASSETS_DIR"/rootfs.zst.* | wc -l)"

cp "$BUILD_DIR/rootfs_manifest.json" "$ASSETS_DIR/rootfs_manifest.json"

echo "完成：assets 与 jniLibs 已就绪，可执行 packaging/sign_and_build.sh 构建 APK"
