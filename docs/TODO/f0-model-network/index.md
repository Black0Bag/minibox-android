# F0 模型 + 网络 + 连接诊断

## 元数据

- 任务：实现 DTO 层、REST/SSE 网络客户端、Keystore 凭据存储、连接诊断 UI、Navigation 3 路由骨架。
- 作用域：`app/src/main/java/com/blackbag/minibox/core/`、`data/`、`navigation/`、`feature/connection/`
- 证据来源：BACKEND_API.md、后端 `health_handlers.go`、`config_handlers.go`、`transport/envelope.go`、Navigation 3 skill 迁移指南
- 版本：0.0.0 → 0.1.0

## 验收标准

1. `./gradlew assembleDebug` 通过（CI 自动验证）
2. `./gradlew :app:testDebugUnitTest` 通过（DTO 序列化 + Repository Mock + ViewModel 测试）
3. 连接诊断屏能输入配置、展示三步诊断结果（Mock 环境）

## 步骤

| # | 步骤 | 状态 |
| --- | --- | --- |
| S1 | core/model：Envelope、ProblemDetail、HealthData、ReadyData、ServerStatusData、ConnectionConfig | [DONE] |
| S2 | core/security：CredentialStore（EncryptedSharedPreferences） | [DONE] |
| S3 | core/network：MiniboxHttpClient（OkHttp + Bearer 拦截器）、RestClient、ErrorResponseHandler | [DONE] |
| S4 | core/network：SseClient 骨架（EventSource 工厂 + Authorization + Last-Event-ID 重连） | [DONE] |
| S5 | data：ConnectionRepository（health → ready → serverStatus 诊断序列） | [DONE] |
| S6 | navigation：Navigation 3 路由（ConnectionNavKey + AppNavDisplay） | [DONE] |
| S7 | feature/connection：ConnectionScreen + ViewModel + UiState | [DONE] |
| S8 | 测试：DTO 序列化 fixture、Repository Mock、ViewModel 测试 | [DONE] |
| S9 | libs.versions.toml 更新（navigation3 1.0.0 + security-crypto + lifecycle-viewmodel-nav3 + mockwebserver） | [DONE] |
| S10 | 文档回填 + 提交推送 | [DONE] |

## 决策记录

- Envelope data 用 JsonElement 而非泛型 T：对齐后端 json.RawMessage 设计，按 type 字段运行时分发。
- SSE 客户端仅骨架级：工厂 + 头部 + 重连逻辑，事件解析留 F1。
- WS 客户端推迟到 F3（设备代理）。
