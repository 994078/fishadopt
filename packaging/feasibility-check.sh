#!/usr/bin/env bash
# 一键领养小肥鱼 —— DSH 手机端可行性验证脚本（在 x86_64 Linux 上运行）
#
# 目的：验证"手机 proot 里跑官方 DSH Web 模式"是否可行，回答四个问题：
#   1. pnpm install 能否成功（依赖能否固化）
#   2. 原生模块是否为预编译（是否免交叉编译）
#   3. pnpm build 能否完成（TypeScript 编译成本）
#   4. start:web 能否 headless 监听端口（脱离 Electron）
#
# 用法：bash packaging/feasibility-check.sh
# 产物：build/feasibility/report.md
set -uo pipefail   # 每步失败继续，收集全部信息

REPORT_DIR="${REPORT_DIR:-$PWD/build/feasibility}"
mkdir -p "$REPORT_DIR"
REPORT="$REPORT_DIR/report.md"

log() { echo "$@" | tee -a "$REPORT"; }
section() { log ""; log "=============================================="; log "## $1"; log "=============================================="; }

: > "$REPORT"
log "# DSH 手机端可行性验证报告"
log "生成时间: $(date -u '+%Y-%m-%d %H:%M:%S UTC')"
log "架构: $(uname -m) 内核: $(uname -r)"

section "0. 运行环境"
log "- Node: $(node --version 2>&1)"
log "- pnpm: $(pnpm --version 2>&1 || echo '未安装（将用 corepack 启用）')"
log "- git: $(git --version 2>&1)"

section "1. 克隆 DSH"
DSH_REPO="${DSH_REPO:-https://github.com/deepseek-ai/deepseek-harness.git}"
DSH_DIR="$REPORT_DIR/dsh"
if [ -d "$DSH_DIR/.git" ]; then
  log "已存在，跳过克隆"
else
  if git clone --depth 1 "$DSH_REPO" "$DSH_DIR" >> "$REPORT" 2>&1; then
    log "克隆成功"
  else
    log "克隆失败，终止"
    exit 1
  fi
fi
cd "$DSH_DIR"
log "- 提交: $(git rev-parse --short HEAD 2>&1)"

section "2. pnpm install（关键：是否拉到预编译原生模块）"
corepack enable >> "$REPORT" 2>&1 || true
if pnpm install --frozen-lockfile >> "$REPORT" 2>&1; then
  log "✓ pnpm install (frozen) 成功"
else
  log "frozen-lockfile 失败，尝试普通 install"
  if pnpm install >> "$REPORT" 2>&1; then
    log "✓ pnpm install 成功"
  else
    log "✗ pnpm install 失败（依赖无法固化，方案受阻）"
  fi
fi

section "3. 原生模块：预编译 vs 需编译"
log "-- 已解析出的原生 .node 二进制（预编译产物）："
find node_modules -name '*.node' 2>/dev/null | head -50 | tee -a "$REPORT"
log "-- native/system 下的预编译平台包："
find native packages -name 'prebuilds.json' 2>/dev/null | head -50 | tee -a "$REPORT"

section "4. pnpm build（TypeScript 编译，可能耗时）"
if pnpm build >> "$REPORT" 2>&1; then
  log "✓ pnpm build 成功"
else
  log "✗ pnpm build 失败（查看上方日志定位；可尝试仅 build:web）"
  if pnpm build:web >> "$REPORT" 2>&1; then
    log "✓ 降级 build:web 成功"
  else
    log "✗ build:web 也失败"
  fi
fi

section "5. 启动 web 模式（headless 验证）"
log "命令: pnpm start:web --no-open  (即 node --import tsx/esm apps/cli/src/bin.ts web --no-open)"
( pnpm start:web --no-open > "$REPORT_DIR/web.log" 2>&1 & echo $! > "$REPORT_DIR/web.pid" )
sleep 40
log "-- 探测常见端口（DSH web 默认 3080）："
for port in 3080 51145 51146 3000 8080 5173; do
  if (echo > /dev/tcp/127.0.0.1/$port) 2>/dev/null; then
    log "  ✓ 端口 $port 开放"
  fi
done
log "-- web.log 末尾 40 行："
tail -40 "$REPORT_DIR/web.log" >> "$REPORT" 2>&1 || true
if [ -f "$REPORT_DIR/web.pid" ]; then kill "$(cat "$REPORT_DIR/web.pid")" 2>/dev/null || true; fi

section "6. 体积占用（对手机 ROM/RAM 的参考）"
log "- node_modules 体积: $(du -sh node_modules 2>/dev/null | cut -f1)"
log "- 整个仓库体积: $(du -sh . 2>/dev/null | cut -f1)"
log "- 源码文件数: $(find packages apps native -type f 2>/dev/null | wc -l)"

section "结论速览"
log "1) pnpm install 成败 → 决定依赖能否固化"
log "2) 第3节是否列出 *.node 预编译二进制 → 决定是否免交叉编译"
log "3) pnpm build 成败 → 决定 TS 编译成本"
log "4) 第5节是否探测到端口 → 决定 headless 可行性"
log ""
log "报告结束：$REPORT"
