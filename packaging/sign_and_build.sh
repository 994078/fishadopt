#!/usr/bin/env bash
# 一键领养小肥鱼 —— 构建 + 签名脚本
#
# 前置：已执行 rootfs/build_rootfs.sh 与 packaging/package_assets.sh，
#       并安装 Android SDK / JDK 17（配置好 local.properties 的 sdk.dir）。
#
# 签名参数通过环境变量或 local.properties 提供：
#   KEYSTORE / KEYSTORE_ALIAS / STORE_PASS / KEY_PASS
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

KEYSTORE="${KEYSTORE:-$REPO_ROOT/release.keystore}"
KEYSTORE_ALIAS="${KEYSTORE_ALIAS:-fishadopt}"
STORE_PASS="${STORE_PASS:-}"
KEY_PASS="${KEY_PASS:-}"

cd "$REPO_ROOT"

echo "==> Gradle 构建 release APK"
# 需要 Gradle 8.9（CI 与本地均可用 `gradle` 命令；如需 wrapper，先在仓库根执行 `gradle wrapper`）
gradle assembleRelease

UNSIGNED="app/build/outputs/apk/release/app-release-unsigned.apk"
SIGNED="app/build/outputs/apk/release/fishadopt-release.apk"

if [ ! -f "$UNSIGNED" ]; then
  echo "[错误] 未找到未签名 APK：$UNSIGNED"
  exit 1
fi

if [ ! -f "$KEYSTORE" ] || [ -z "$STORE_PASS" ]; then
  echo "[提示] 未提供 keystore，保留未签名 APK（仅用于本地调试）"
  cp "$UNSIGNED" "app/build/outputs/apk/release/fishadopt-unsigned.apk"
  exit 0
fi

echo "==> apksigner 签名"
apksigner sign \
  --ks "$KEYSTORE" \
  --ks-key-alias "$KEYSTORE_ALIAS" \
  --ks-pass "pass:$STORE_PASS" \
  --key-pass "pass:${KEY_PASS:-$STORE_PASS}" \
  --out "$SIGNED" \
  "$UNSIGNED"

apksigner verify "$SIGNED"
echo "完成：$SIGNED"
