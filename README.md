# 一键领养小肥鱼（Android 客户端）

一款第三方爱好者工具：在安卓手机上"一键"启动原版 **DeepSeek Harness（DSH，社区称"小肥鱼"）**，保留其工具调用、文件读写、命令执行、网页访问等全部能力，行为与电脑端保持一致。

- **技术路线**：Kotlin 外壳 + proot-rs 用户态容器 + 预打包 Debian arm64 根文件系统（RootFS）。
- **交付形态**：ARM64 安卓侧载 APK。
- **与官方无关**：不提供算力与模型调用额度，模型调用通过用户自行填写的 DeepSeek API Key 完成，密钥仅存本机。

> 完整产品需求见《一键领养小肥鱼-安卓APP产品需求文档 V1.0(MVP)》。

## ⚠️ 重要：真实 DSH 与 PRD 假设不符（已实测纠正）

原始 PRD 假设 DSH 是「`node main.js` + `npm install`」的简单工具，**实测不成立**。真实 DSH（`deepseek-ai/deepseek-harness`，MIT）：

- TypeScript + **pnpm@11** 单仓，要求 **Node ≥ 24.9.0**；
- Web 模式入口为 `dsh web`（headless，脱离 Electron），**默认端口 3080**、带 **token 认证**、需 `--no-open`；
- 原生模块走 napi 预编译分发，**有 `linux-arm64` 预编译包**（免交叉编译 Rust）。

完整验证过程与结论见 [docs/可行性验证报告.md](docs/可行性验证报告.md)（已在 Windows 上实跑通 `pnpm install → pnpm build → dsh web` 全链路）。

## 目录结构

```
.
├── app/                        Android 应用工程（Kotlin + Jetpack Compose）
│   ├── src/main/java/com/fishadopt/app/
│   │   ├── di/                 手动依赖容器 + ViewModel 工厂
│   │   ├── ui/                 7 屏（欢迎/向导/主控/对话/日志/设置/关于）+ 主题 + 导航
│   │   ├── data/               环境管理、密钥存储、日志、设置、权限校验
│   │   ├── service/            前台服务、proot 进程托管、端口探测、进程守护、token 解析
│   │   └── util/               脱敏、proot 参数、分卷合并、校验
│   ├── src/main/assets/        proot-rs 与 RootFS 分卷（打包脚本注入）+ 许可文本
│   └── src/test/               JVM 单元测试
├── rootfs/                     RootFS 构建脚本（build_rootfs.sh / start_dsh.sh / Dockerfile）
├── packaging/                  资产打包 / APK 签名 / 可行性验证脚本
├── .github/workflows/          CI：可行性验证 + APK 构建（arm64 runner）
├── pc-launcher/                PC 版启动器（无依赖 Node 服务 + 模拟 DSH，已实测跑通）
├── docs/                       11 篇文档（构建指南 / 验收清单 / 可行性报告 / 速查卡等）
└── build/                      本机验证用的真实 DSH（已 gitignore，可删）
```

## 四层架构（PRD 3.2）

1. **应用外壳层**（Kotlin/Compose）：界面、权限向导、RootFS 解压/校验/重置、进程托管、通知与日志转发。
2. **容器运行时层**：内置 arm64 `proot-rs` 二进制（以原生库形式随包分发）。
3. **根文件系统层**：预打包 Debian arm64 RootFS（自装 Node 24 + pnpm + 已构建的 DSH）。
4. **Agent 运行层**：容器内以 `dsh web --port <port> --no-open` 启动的 headless Web 服务，WebView 加载带 token 的地址。

## 构建（推荐走 GitHub Actions 云端）

本地构建需 JDK 17 + Android SDK + Gradle 8.9，且 RootFS 需在 **arm64 Linux** 上构建（pnpm build 在 x86+qemu 下极慢）。推荐直接云端构建：

1. 把本目录推到 GitHub（步骤见 [docs/速查卡.md](docs/速查卡.md)）。
2. Actions 触发 **`Build Android APK`**（DSH 仓库/分支已填官方默认值，无需改）。
3. 下载 `apk` artifact，侧载到手机。

```bash
# 本地构建（需上述工具链 + arm64 Linux 构建机）
bash rootfs/build_rootfs.sh          # 产出 build/rootfs/rootfs.tar.zst
bash packaging/package_assets.sh     # 下载 proot-rs + 生成分卷
bash packaging/sign_and_build.sh     # gradle assembleRelease + 签名
```

## 关键安全设计（PRD 8 章）

- API Key 经 `EncryptedSharedPreferences`（Keystore 背书）加密存储，仅存本机；
- 启动经环境变量 `DSH_KEY` 注入容器，进程结束即失效，不落盘；
- 日志管道统一脱敏（密钥 / Authorization / Cookie / `?token=` 特征）；
- 网络请求仅由容器内 DSH 发起，外壳无任何上传（见 [docs/网络请求清单.md](docs/网络请求清单.md)）。

## 许可

- DSH：MIT License
- proot-rs：GPLv2
- 其余组件许可见 [app/src/main/assets/licenses/NOTICE.txt](app/src/main/assets/licenses/NOTICE.txt)

完整许可文本内置于应用"关于"页。
