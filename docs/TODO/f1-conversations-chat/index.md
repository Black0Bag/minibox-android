# F1: 会话 + SSE 聊天闭环

- 作用域: Android 首版聊天功能：会话管理 + SSE 事件流 + 消息收发 + 审批
- 状态: 已完成（PR #3 已合并 main，v0.2.0，CI 全绿）
- 分支: feature/f1-conversations-chat
- 版本: 0.1.0 → 0.2.0

## 契约依据

- docs/BACKEND_API.md「Android 首版 REST」「SSE」章节
- 后端 /workspace/minibox/minibox/docs/api.md §2 会话、§11 审批
- 后端 /workspace/minibox/minibox/docs/sse.md 事件序列

## 关键契约

- Session: `id, title, created_at, updated_at, messages, mode`
- Message: `role, content, run_id?, at`（RFC3339）
- `POST /conversations/`（空体）→ 新 Session；`GET /conversations/` → Session[]（updated_at 倒序）
- `POST /conversations/{id}/messages` `{"message":"..."}` → `{"answer":"<run_id>"}`（异步）
- 集合路径保留尾斜杠
- SSE 事件: agent.message.user / run_started / step_started / step_finished /
  approval_requested / approval_result / run_finished / message.assistant / run_failed
- 终态判据: `agent.run_finished.state`（不是看 assistant 消息是否到达）
- 审批: SSE 收 approval_requested → `POST /approvals/{run_id}` `{"approved":bool}`
- SSE 客户端: 每会话单流；event_id/seq 去重；Last-Event-ID 重连；401 停止

## 步骤

| 步骤 | 内容 | 状态 |
| --- | --- | --- |
| S1 | model：Session、Message DTO + SSE 会话事件解析（typed events） | [DONE] |
| S2 | data：ConversationsRepository（list/create/get/sendMessage，尾斜杠） | [DONE] |
| S3 | data：ChatStreamRepository（SseClient 重连 + 去重 + 类型化事件 Flow） | [DONE] |
| S4 | data：ApprovalsRepository（POST /approvals/{run_id}） | [DONE]（并入 ConversationsRepository.submitApproval） |
| S5 | navigation：ConversationsKey + ChatKey 路由 | [DONE] |
| S6 | feature/conversations：会话列表屏 | [DONE] |
| S7 | feature/chat：聊天屏（消息 + 输入 + 运行状态 + 审批卡片） | [DONE] |
| S8 | 测试：DTO 序列化、Repository Mock、SSE 事件解析/去重 | [DONE] |
| S9 | 版本 0.2.0 + CHANGELOG + 文档回填 + 提交推送 | [DONE] |
