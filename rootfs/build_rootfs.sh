#!/usr/bin/env bash
# 一键领养小肥鱼 —— arm64 Debian RootFS 交叉构建脚本（修正版，匹配真实 DSH）
#
# 真实 DSH（deepseek-ai/deepseek-harness）是 TypeScript + pnpm 单仓，要求 Node >= 24.9.0，
# 并有 napi 预编译原生模块（linux-arm64 预编译包已存在，无需交叉编译 Rust）。
# 因此本脚本与 PRD 原版不同：不用 Debian 自带 node（太旧），改为下载官方 Node 24 arm64，
# 并用 pnpm 安装依赖 + 构建，最后以 `dsh web`（headless）启动。
#
# 运行环境：
#   - 首选：原生 arm64 Linux（如 GitHub `ubuntu-24.04-arm` runner），构建快、无模拟开销。
#   - 备选：x86_64 Ubuntu + qemu-user-static（需注册 binfmt），chroot 内 pnpm build 会明显变慢。
#
# 产物（写入 <repo>/build/rootfs/）：
#   rootfs.tar.zst          压缩后的根文件系统
#   rootfs_manifest.json    校验清单（关键文件 + sha256）
#
# 待可行性验证确认：pnpm install/build 的精确最小命令（可能收窄为 build:web 等，避开 Electron 桌面构建）。
set -euo pipefail

# ---- 可配置参数 ----
ARCH="${ARCH:-arm64}"
SUITE="${SUITE:-bookworm}"
DSH_REPO="${DSH_REPO:-https://github.com/deepseek-ai/deepseek-harness.git}"
DSH_VERSION="${DSH_VERSION:-master}"
NODE_VERSION="${NODE_VERSION:-24.9.0}"     # DSH 要求 >= 24.9.0
PNPM_VERSION="${PNPM_VERSION:-11.28.5}"    # DSH packageManager 指定
DSH_BUILD_CMD="${DSH_BUILD_CMD:-build}"    # 默认全量 build；实测后可能收窄

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
OUT_DIR="${OUT_DIR:-$SCRIPT_DIR/../build/rootfs}"
ROOTFS="$OUT_DIR/rootfs"
HOST_ARCH="$(uname -m)"

# ---- 依赖检查 ----
for cmd in debootstrap zstd git tar curl; do
  command -v "$cmd" >/dev/null 2>&1 || { echo "[错误] 缺少依赖: $cmd（Ubuntu: apt install debootstrap zstd git tar curl）"; exit 1; }
done
if [ "$HOST_ARCH" != "aarch64" ] && [ "$HOST_ARCH" != "arm64" ]; then
  command -v qemu-aarch64-static >/dev/null 2>&1 || { echo "[错误] x86_64 主机需 qemu-user-static（apt install qemu-user-static）"; exit 1; }
fi

echo "==> 主机架构: $HOST_ARCH（$([ "$HOST_ARCH" = aarch64 ] || [ "$HOST_ARCH" = arm64 ] && echo 原生 arm64 || echo x86_64+qemu 模拟)）"
rm -rf "$ROOTFS"
mkdir -p "$OUT_DIR"

echo "==> 1/7 debootstrap 生成 arm64 基础系统"
if [ "$HOST_ARCH" = "aarch64" ] || [ "$HOST_ARCH" = "arm64" ]; then
  debootstrap --arch=arm64 "$SUITE" "$ROOTFS" http://deb.debian.org/debian
else
  debootstrap --arch=arm64 --foreign "$SUITE" "$ROOTFS" http://deb.debian.org/debian
  cp /usr/bin/qemu-aarch64-static "$ROOTFS/usr/bin/"
  chroot "$ROOTFS" /debootstrap/debootstrap --second-stage
fi

echo "==> 2/7 安装基础依赖（不含 Debian 自带 node，版本太旧）"
chroot "$ROOTFS" /bin/bash -c '
  export DEBIAN_FRONTEND=noninteractive
  apt-get update
  apt-get install -y --no-install-recommends \
    git python3 ca-certificates curl bash coreutils xz-utils
'

echo "==> 3/7 下载官方 Node $NODE_VERSION (arm64) 并安装到 /usr/local"
NODE_TARBALL="node-v${NODE_VERSION}-linux-arm64.tar.xz"
curl -fL "https://nodejs.org/dist/v${NODE_VERSION}/${NODE_TARBALL}" -o "/tmp/${NODE_TARBALL}"
mkdir -p "$ROOTFS/usr/local"
tar -xJf "/tmp/${NODE_TARBALL}" -C "$ROOTFS/usr/local" --strip-components=1
rm -f "/tmp/${NODE_TARBALL}"
chroot "$ROOTFS" /usr/local/bin/node --version

echo "==> 4/7 安装 pnpm $PNPM_VERSION"
chroot "$ROOTFS" /usr/local/bin/npm install -g "pnpm@${PNPM_VERSION}"

echo "==> 5/7 克隆 DSH"
chroot "$ROOTFS" /bin/bash -c "
  cd /root
  git clone --depth 1 --branch '$DSH_VERSION' '$DSH_REPO' dsh
"

echo "==> 6/7 安装依赖并构建（pnpm install 会拉取 linux-arm64 预编译原生模块）"
chroot "$ROOTFS" /bin/bash -c "
  cd /root/dsh
  pnpm install
  pnpm $DSH_BUILD_CMD
"

echo "==> 7/7 写入启动脚本并清理打包"
cp "$SCRIPT_DIR/start_dsh.sh" "$ROOTFS/root/start_dsh.sh"
chmod +x "$ROOTFS/root/start_dsh.sh"
chroot "$ROOTFS" /bin/bash -c '
  apt-get clean
  rm -rf /var/lib/apt/lists/* /usr/share/doc /usr/share/man /var/cache/apt /root/dsh/.git
'

tar -C "$ROOTFS" --numeric-owner -cf "$OUT_DIR/rootfs.tar" .
zstd -T0 -19 "$OUT_DIR/rootfs.tar" -o "$OUT_DIR/rootfs.tar.zst"
rm -f "$OUT_DIR/rootfs.tar"

# ---- 生成校验清单 ----
MANIFEST="$OUT_DIR/rootfs_manifest.json"
hash_start="$(sha256sum "$ROOTFS/root/start_dsh.sh" | cut -d' ' -f1)"
hash_node="$(sha256sum "$ROOTFS/usr/local/bin/node" | cut -d' ' -f1)"
hash_pkg="$(sha256sum "$ROOTFS/root/dsh/package.json" | cut -d' ' -f1)"
cat > "$MANIFEST" <<EOF
{
  "version": "${DSH_VERSION}",
  "criticalFiles": [
    "root/start_dsh.sh",
    "usr/local/bin/node",
    "root/dsh/package.json",
    "root/dsh/apps/cli/src/bin.ts"
  ],
  "hashes": {
    "root/start_dsh.sh": "${hash_start}",
    "usr/local/bin/node": "${hash_node}",
    "root/dsh/package.json": "${hash_pkg}"
  }
}
EOF

echo ""
echo "完成：$OUT_DIR/rootfs.tar.zst"
echo "清单：$MANIFEST"
echo "下一步：packaging/package_assets.sh 生成 assets 与分卷"
