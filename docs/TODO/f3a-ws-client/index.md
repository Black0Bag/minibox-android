# f3a-ws-client（阶段 2 遗留欠账，F3 前置）

- 作用域: 设备 WebSocket 传输层：JSON-RPC 2.0 帧、状态机、pending map、心跳、重连
- 状态: 进行中
- 分支: feature/f3a-ws-client
- 版本: 0.4.0 → 0.5.0
- 计划依据: docs/plan.md 里程碑 3 遗留 + ROADMAP 欠账 #2（F3 设备代理前置）

## 契约依据（websocket.md + 后端源码已核对）

- 端点 `ws://<host>:<port>/device/ws`，文本 JSON 帧，JSON-RPC 2.0
- 首帧握手 `connect`：params {client:"android-device", protocol:"1.0", auth:"<device-token>"}；result {ok:true}
- 协议版本非 1.0 拒绝；认证失败无可用会话
- 应用错误码：-32001 未握手 / -32002 认证失败 / -32003 设备离线 / -32004 超时 / -32005 限流
- device 外层方法内层 method：hello/connect/disconnect/event；hello 上报 id/model/android/capabilities/permissions
- 并发请求必须唯一 id，响应按 id 归属（pending map）
- 设备 token：后端 GenerateDeviceCredential 随机 hex，经 setup 界面下发；Android 存 Keystore（AGENTS.md L54），日志禁打 auth

## 实现要求（websocket.md「Android 客户端实现要求」逐条）

1. 状态机 Disconnected → Connecting → Handshaking → Ready → Reconnecting
2. 唯一 ID + pending map
3. 单读循环；写串行化
4. 心跳失败主动重连，重连后重发握手与 hello
5. 日志不打印 auth/完整参数/屏幕剪贴板内容

## 本任务不做（F3 主体）

- 18 项设备执行器、前台服务、命令审批 UI、真机副作用

## 步骤

| 步骤 | 内容 | 状态 |
| --- | --- | --- |
| S1 | model：JsonRpcFrame（请求/响应/错误）、错误码常量 | 待办 |
| S2 | core/network：DeviceWsClient（状态机+单读循环+写队列+pending map+心跳+指数退避重连） | 待办 |
| S3 | core/security：CredentialStore 扩展 deviceToken | 待办 |
| S4 | feature/device：DeviceScreen（token 输入+连接控制+状态+心跳延迟显示） | 待办 |
| S5 | navigation：DeviceKey + ConnectionScreen 入口 | 待办 |
| S6 | 测试：JSON-RPC 帧序列化 + MockWebServer WS 升级（握手/心跳/pending 归属/断线重连） | 待办 |
| S7 | 版本 0.5.0 + CHANGELOG + 提交推送 + PR + CI + 合并 | 待办 |
