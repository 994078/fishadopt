# 一键领养小肥鱼 —— PC 版启动器

在 PC 上即可完整测试「一键领养」核心流程的桌面版本，无需编译安卓 APK、无需真机。
它 1:1 复刻安卓 APP 的界面与职责，架构一一对应：

| 安卓 APP | PC 版 |
|---|---|
| Kotlin 外壳 | `server.js`（Node 服务） |
| proot-rs 容器运行时 | 本机直接运行 DSH（无需容器） |
| WebView 承载 DSH 前端 | `iframe` 加载 DSH 前端 |
| EncryptedSharedPreferences 存密钥 | 本地 `data/api_key.json`（演示） |
| 前台服务 / 通知 | Node 进程 + 日志控制台 |

## 快速开始

```bash
cd pc-launcher
node server.js
```

然后浏览器打开 <http://127.0.0.1:8080>，按界面走完整流程：

**欢迎 → 环境检查 → 准备环境 → 一键领养 → 自动进入对话界面 → 日志/设置/关于**

> 依赖：仅需 Node.js（≥16），无任何 npm 依赖。

## 内置模拟 DSH

默认使用 `mock-dsh.js` 作为"小肥鱼"，让你在没有真实 DSH 时也能把
「启动 → 端口探测 → 加载对话界面 → 停止」整条链路跑通（含日志脱敏、密钥注入）。

## 接入真实 DSH

设置 `DSH_COMMAND` 环境变量指向真实 DSH 的启动命令即可：

```bash
# Linux / macOS（真实 DSH 的 Web 模式入口，先 cd 到 DSH 仓库根目录）
cd /path/to/deepseek-harness
DSH_COMMAND="node --import tsx/esm apps/cli/src/bin.ts web" node /path/to/server.js

# Windows (PowerShell)
cd D:\path\to\deepseek-harness
$env:DSH_COMMAND = "node --import tsx/esm apps/cli/src/bin.ts web"
node D:\path\to\server.js
```

| 环境变量 | 默认值 | 说明 |
|---|---|---|
| `LAUNCHER_PORT` | `8080` | 启动器 UI 端口 |
| `DSH_PORT` | `51146` | DSH 监听端口（演示避开 51145，可改回） |
| `DSH_COMMAND` | `node mock-dsh.js` | DSH 启动命令（空格分隔） |

> 密钥经环境变量 `DSH_KEY` 注入 DSH 进程，DSH 侧需读取 `DSH_KEY`（对应安卓版
> `start_dsh.sh` 里的 `export DEEPSEEK_API_KEY="$DSH_KEY"`）。日志在写入前统一脱敏。

## 与安卓版对照的验证点

- 主按钮文案随状态切换：准备环境 / 一键领养小肥鱼 / 停止小肥鱼
- 启动阶段文字：正在启动容器 → 正在等待服务
- 端口探测 60 秒超时按失败处理
- 日志三级颜色（info/warning/error），≤1000 行环形缓冲，暂停/清空/复制/导出
- 密钥脱敏（sk-… / Authorization / Cookie 被掩码）
- 运行中返回控制页需二次确认
- 重置环境 / 清除密钥二次确认

## 文件结构

```
pc-launcher/
├── server.js         后端：DSH 进程管理、端口探测、日志脱敏、密钥存储、REST API
├── mock-dsh.js       模拟 DSH（无真实 DSH 时的开箱测试）
├── package.json
├── public/           前端（7 屏：欢迎/向导/主控/对话/日志/设置/关于）
└── README.md
```
