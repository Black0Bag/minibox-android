# minibox Android

minibox 的 Android 用户界面和设备代理。后端是 Agent 中枢，APP 通过 REST、SSE 和 WebSocket JSON-RPC 提供交互、连接管理及手机侧“眼耳口手”能力。

## 当前状态

**源码工程已建，当前版本 0.5.0。** 已交付：F0 网络地基与连接诊断（0.1.0）、F1 会话 + SSE 聊天闭环（0.2.0）、F2 权限/工具/rewind（0.3.0）、F2b 知识库（0.4.0）、F3a 设备 WebSocket 传输层（0.5.0）。进度以 `docs/TODO/ROADMAP.md` 对表为准。

待开发：F3 主体（前台服务、18 项设备执行器、命令审批 UI）与管理面补全（LLM 模型列表、监控）。

仍待决策（不阻塞当前开发）：

1. APP 连接后端的网络范围（minSdk/targetSdk 已定：29/34，REST 认证为固定 Bearer Token）；
2. 首版必须跨后端重启保留的团队项目状态（会话消息已由后端持久化）；
3. 是否生成 OpenAPI 以消除手写 DTO 漂移。

## 技术方向

- Kotlin
- Jetpack Compose + Material 3
- Navigation 3：可序列化 `NavKey`、可保存 back stack、`NavDisplay`
- ViewModel + StateFlow + 单向数据流
- REST + SSE + WebSocket 三通道客户端
- Android Keystore 支持的凭据存储
- Accessibility / MediaProjection / TTS / STT 等设备执行器（分阶段授权）

具体库版本以 `gradle/libs.versions.toml` 为准（沿用 minibile 已验证组合，以 Android 官方稳定版本和兼容矩阵为准）。

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
