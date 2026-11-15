#!/bin/bash
# 一键领养小肥鱼 —— 容器内启动脚本（启动 DSH 的 Web 模式，headless）
#
# 真实 DSH（deepseek-ai/deepseek-harness）Web 模式入口为 `apps/cli/src/bin.ts` 的 "web" profile。
# 官方脚本 `pnpm start:web` = `node --import tsx/esm apps/cli/src/bin.ts web`。
# 实测关键参数：
#   --port <port>  指定监听端口（默认 3080）
#   --no-open      禁止尝试打开浏览器（容器内无浏览器，必须加）
#
# 注意：dsh web 会向 stdout 打印带 token 的访问地址，例如：
#   dsh web: http://127.0.0.1:3080/?token=<token>
# 外壳需解析出该 token，并用「带 token 的 URL」加载 WebView（token 是访问凭据，需脱敏）。
set -euo pipefail

# 注入 API Key（环境变量，进程结束即失效，不落盘）
export DEEPSEEK_API_KEY="$DSH_KEY"

# 端口（DSH web 默认 3080，此处用外壳传入的端口覆盖）
export DSH_PORT="${DSH_PORT:-3080}"
export PORT="$DSH_PORT"

cd /root/dsh

# 启动 DSH Web 模式（headless）
exec node --import tsx/esm apps/cli/src/bin.ts web --port "$DSH_PORT" --no-open
