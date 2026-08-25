# Android 目标架构

## 说明

以下是待创建工程的目标结构，不代表当前已有源码。

## 分层

```text
app (composition + Navigation 3)
├── core:model
├── core:network
├── core:security
├── core:designsystem
├── data
├── feature:connection
├── feature:chat
├── feature:knowledge
├── feature:device
└── feature:admin
```

初期可先用单 app 模块按 package 分层，确认边界后再物理多模块化，避免空仓库过早复杂化。

## 单向数据流

```text
Composable -> user event -> ViewModel -> Repository -> client
Composable <- immutable UiState <- ViewModel <- Repository <- client event
```

- Screen 只负责绑定 ViewModel 和生命周期。
- Content Composable 接收状态与 callback，保持可 Preview/测试。
- 使用 `collectAsStateWithLifecycle()`。
- 长连接由 Repository/Service 持有，不由 Composable 持有。

## Navigation 3

- 每个路由实现 `@Serializable` + `NavKey`。
- 使用 `rememberNavBackStack(startKey)` 保存配置变更后的 back stack。
- 使用 `NavDisplay` + entry provider 映射内容。
- 顶层：会话、知识、设备、管理；是否使用多个 back stack 在 F1 验证。
- 路由只传 ID；详情数据由 ViewModel 加载。
- 导航 callback 使用生命周期安全的入口，避免快速重复点击。

## 网络

### REST

类型化 API + Envelope/Problem 适配器。Repository 暴露领域结果，不把 HTTP 类型泄漏给 UI。

### SSE

一个 session 一个 collector；解析 `id/event/data`，持久化最后 seq，退避重连。Reducer 必须是纯函数并可用 fixture 单测。

### WebSocket

前台服务持有连接。客户端只有一个读循环；pending map 用唯一 ID 关联响应。断线时失败所有 pending 并重新握手/hello。

## 设备执行

```text
WS command
 -> schema/capability validation
 -> Android permission check
 -> risk/approval state
 -> executor
 -> RPC result/error + audit summary
```

Accessibility、MediaProjection、TTS、STT、通知等执行器互相隔离。任何异常默认拒绝，不降级为无检查执行。

## 本地存储

- DataStore：地址、主题、非敏感设置、最后 SSE seq。
- Keystore-backed storage：设备 token。
- Room：只有离线草稿/必要索引需求明确后再引入。
- 后端数据永远以服务器为准。

## 依赖注入

`[ASK USER]` Hilt 还是手工构造。首版可使用小型 composition root；若模块和前台服务增长，再引入 Hilt。不要在没有源码规模证据时强制增加 DI 框架。