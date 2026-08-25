# minibox Android

minibox 的 Android 用户界面和设备代理。后端是 Agent 中枢，APP 通过 REST、SSE 和 WebSocket JSON-RPC 提供交互、连接管理及手机侧“眼耳口手”能力。

## 当前状态

**工程准备阶段，尚未创建 Gradle/Kotlin 源码。**

当前仓库已经完成产品边界、后端契约、Compose/Navigation 3 架构、无障碍门禁和开发顺序建档。创建源码前仍需确认：

1. Android 最低版本与目标版本；
2. APP 连接后端的网络范围和 REST 认证方案；
3. 首版必须跨后端重启保留的会话/项目状态；
4. 是否在生成 Retrofit DTO 前先冻结 OpenAPI。

## 技术方向

- Kotlin
- Jetpack Compose + Material 3
- Navigation 3：可序列化 `NavKey`、可保存 back stack、`NavDisplay`
- ViewModel + StateFlow + 单向数据流
- REST + SSE + WebSocket 三通道客户端
- Android Keystore 支持的凭据存储
- Accessibility / MediaProjection / TTS / STT 等设备执行器（分阶段授权）

具体库版本在创建 Gradle 工程时以 Android 官方稳定版本和兼容矩阵为准，不在当前空仓库中伪造。

## 文档入口

| 文档 | 作用 |
|---|---|
| [`docs/README.md`](docs/README.md) | 前端文档总索引 |
| [`docs/BACKEND_API.md`](docs/BACKEND_API.md) | 后端 REST/SSE/WS 交接摘要 |
| [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) | 目标分层和模块边界 |
| [`docs/DEVELOPMENT_PLAN.md`](docs/DEVELOPMENT_PLAN.md) | 分阶段开发计划 |
| [`docs/UI_GUIDELINES.md`](docs/UI_GUIDELINES.md) | Compose UI 规则 |
| [`docs/ACCESSIBILITY.md`](docs/ACCESSIBILITY.md) | TalkBack、触控和语义门禁 |
| [`docs/goal.md`](docs/goal.md) | 前端目标与范围 |
| [`docs/plan.md`](docs/plan.md) | 当前实施计划 |
| [`docs/rules.md`](docs/rules.md) | 编码、安全和测试规则 |
| [`docs/structure.md`](docs/structure.md) | 目标目录结构 |

完整后端事实以 [`Black0Bag/minibox`](https://github.com/Black0Bag/minibox) 的 `docs/` 和源码为准。

## 首期交付

首期不做营销页，APP 第一屏直接是连接诊断或会话体验：

1. 后端地址、连接和就绪诊断；
2. 会话列表、创建、发送和错误展示；
3. SSE Agent 状态和断线续传；
4. WebSocket 设备握手、心跳和能力状态；
5. 设备工具只在真实权限与审批契约存在时开放。

## 安全底线

- 不在源码、日志、截图报告或 Git 中保存 API Key/设备 token。
- 前端不直接读取后端 SQLite。
- 升级、数据库恢复和设备写操作默认隐藏或拒绝，直到认证/审批链完整。
- 真机副作用逐能力授权测试，不做一键全权限测试。
