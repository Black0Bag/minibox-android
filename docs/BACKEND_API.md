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

## Android 首版 REST

- `/health`, `/ready`, `/server/status`
- `/conversations/` 和 `/{id}/messages`, `/{id}/rewind`
- `/permissions/`, `/tools/`
- `/kb/search`, `/kb/store`, `/kb/compile/{job_id}`
- `/device/` 和设备状态/命令审计
- `/monitor/metrics`, `/monitor/history`

集合路径建议保留尾斜杠。

## SSE

- 每个 session 只维护一个活动流。
- 保存最后处理 `id/seq`；重连发送 `Last-Event-ID`。
- 忽略 `: keepalive` 注释。
- 按 `event_id`/`seq` 去重，发现窗口缺口时刷新完整状态。
- `data` 是 JSON Envelope，不是纯文本 token。

## WebSocket

1. 连接后首请求 `method=connect`，包含 `client`, `protocol=1.0`, `auth`。
2. 设备 hello 使用外层 `method=device`，内层 `params.method=hello`。
3. 为每条 RPC 使用唯一 ID，pending map 按 ID 分发响应。
4. 保持单一读循环；写入可串行化。
5. 心跳、超时、断开时清理所有 pending。

## 当前阻塞项

- REST 没有通用用户认证，非 loopback 部署前必须补齐。
- Agent 审批事件当前自动拒绝，没有公开提交 API；首版不做可点击审批按钮。
- `TaskDependency` JSON 契约尚需后端冻结。
- 会话/团队/SSE 部分状态在内存中，重启语义未完全冻结。
- 升级和数据库回滚不进入首版可执行 UI。