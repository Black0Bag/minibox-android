# 后端交接契约摘要

## 通道

| 通道 | 地址 | 用途 |
|---|---|---|
| REST | `http://<host>:<port>/api/v1/*` | 状态、会话、知识、工具、管理命令 |
| SSE | `/api/v1/stream?session_id=<id>` | Agent/系统事件和断线回放 |
| WS | `ws://<host>:<port>/device/ws` | 设备 JSON-RPC 双向通道 |

完整契约以后端仓库 `docs/api.md`、`docs/sse.md`、`docs/websocket.md` 为准。

## REST 通用模型

成功：

```kotlin
@Serializable
data class Envelope<T>(
    @SerialName("spec_version") val specVersion: String,
    @SerialName("event_id") val eventId: String,
    @SerialName("trace_id") val traceId: String,
    val seq: Int? = null,
    val timestamp: String,
    val producer: String,
    val source: String,
    val type: String,
    val data: T,
)
```

错误：`type`, `title`, `status`, `detail`, 可选 `instance`，Content-Type 为 `application/problem+json`。

不要把旧文档里的 `{"status":"ok"}` 当作 Envelope；真实成功响应包含 `spec_version/event_id/trace_id/...`。

**错误格式全局统一**（后端 2026-09-02 已落实）：业务错误、认证 401、路由 404、
方法 405 都是 `application/problem+json` + 顶层五字段。只需一个错误解析器：

```kotlin
@Serializable
data class ProblemDetail(
    val type: String,
    val title: String,
    val status: Int,
    val detail: String = "",
    val instance: String? = null,
)
```

`type` 是稳定字符串标识（不是 URI），全集见后端 `docs/api.md`。

## 认证

除 `/health`、`/ready`、`/device/ws` 外，所有 REST 端点与 SSE 流都需要：

```http
Authorization: Bearer <token>
```

Token 由后端首次启动生成于 `data/auth.token`（0600），需由用户手动录入 APP
并存入 Keystore 支持的安全存储。

实现要点：

- scheme 大小写不敏感，但建议统一发 `Bearer`。
- 401 响应带 `WWW-Authenticate: Bearer realm="minibox"`；Token 无效时追加
  `error="invalid_token"`，可据此区分「没填 Token」与「Token 填错了」。
- 401 响应体是 ProblemDetail（`type = "unauthorized"`）。
- 收到 401 不要自动重试，直接进入「凭据错误」连接状态。

## Android 首版 REST

- `/health`, `/ready`, `/server/status`
- `/conversations/` 和 `/{id}/messages`, `/{id}/rewind`
- `/permissions/`, `/tools/`
- `/kb/search`, `/kb/store`, `/kb/compile/{job_id}`
- `/device/` 和设备状态/命令审计
- `/monitor/metrics`, `/monitor/history`

集合路径建议保留尾斜杠。

## SSE

- **必须携带 `Authorization: Bearer <token>` 请求头**（`/api/v1/stream` 不在
  免认证白名单内）。Android 用 OkHttp + `okhttp-sse`：
  `EventSources.createFactory(client).newEventSource(request, listener)`，
  `request` 由 `Request.Builder().addHeader("Authorization", "Bearer $token")` 构造。
  浏览器原生 `EventSource` 不能设请求头，因此**不要照搬 Web 端示例**。
- 每个 session 只维护一个活动流。
- 保存最后处理 `id/seq`；重连发送 `Last-Event-ID`（同时仍需带 Authorization）。
- 忽略 `: keepalive` 注释。
- 按 `event_id`/`seq` 去重，发现窗口缺口时刷新完整状态（历史窗口 500 条/会话）。
- `data` 是 JSON Envelope，不是纯文本 token。
- 收到 401 停止重连并上报凭据错误。

### 会话消息是异步的

`POST /conversations/{id}/messages` 立即返回 `run_id`，回答通过 SSE 推送。
事件序列（含审批分支）见后端 `docs/sse.md`：

```text
agent.message.user → agent.run_started → agent.step_started/finished …
  [需审批] agent.approval_requested → (前端提交) → agent.approval_result → …
→ agent.run_finished → agent.message.assistant（或 agent.run_failed）
```

以 `agent.run_finished` 的 `state` 判定终态，不要只看是否收到助手消息。

## WebSocket

1. 连接后首请求 `method=connect`，包含 `client`, `protocol=1.0`, `auth`。
   注意：WS 用的是**设备凭据**（`data/device.cred`），不是 REST 的 Bearer Token。
2. 设备 hello 使用外层 `method=device`，内层 `params.method=hello`。
3. 为每条 RPC 使用唯一 ID，pending map 按 ID 分发响应。
4. 保持单一读循环；写入可串行化。
5. 心跳、超时、断开时清理所有 pending。

## 当前阻塞项与状态（2026-09-02 复核）

| 事项 | 状态 |
|---|---|
| REST 通用认证 | ✅ 已实现（Bearer Token，见「认证」章节） |
| Agent 审批提交 API | ✅ 已实现 `POST /api/v1/approvals/{run_id}`，可做真实允许/拒绝按钮 |
| `TaskDependency` JSON 契约 | ✅ 已冻结为 `id` / `depends_on`，有契约测试锁定 |
| teamwork 全域 DTO 命名 | ✅ 已统一 lower_snake_case（字段表见后端 `docs/api.md`） |
| 会话消息持久化 | ✅ 已落 `conversation_log`，重启恢复最近 50 条会话 |
| 团队项目 / SSE 历史 | ⚠️ 仍在内存，重启丢失；首版不要依赖其跨重启存在 |
| 编译作业（`/kb/compile`） | ⚠️ 作业表在内存，重启后 `job_id` 查不到 |
| 升级 / 数据库回滚 | ⚠️ 不进入首版可执行 UI |
| 浏览器代理（`browser.*`） | ❌ 后端仅有方法常量与占位 handler，Agent 无对应工具、Hub 无下发路径，尚不可用 |

两个编码细节（避免踩坑）：

- `discussion.proposals` 是**以轮次字符串为键的对象**（`{"1":[...],"2":[...]}`），
  不是数组。
- `circuit_breaker.timeout_ns` 是**纳秒整数**，展示前需换算。