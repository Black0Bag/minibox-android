# Android 开发计划

## 阶段 0：决策门禁

- minSdk/targetSdk 已定（29/34，F0 落地）；网络范围、OpenAPI 时间点仍待确认。
- 确认首版屏幕：连接、会话、知识、设备、管理中的最小集合。
- 后端 P0 阻塞已全部解决（2026-09-02）：REST 认证、审批提交 API、
  关键 DTO JSON tag 均已就绪并有测试锁定，不再阻塞前端开工。

## 阶段 1：工程地基

- 创建 Gradle 工程、Version Catalog、Compose、Material 3、Navigation 3、序列化和测试。
- 建立主题、错误状态、加载状态和连接状态组件。
- 建立 `NavKey`、`rememberNavBackStack`、`NavDisplay`。
- CI 执行 unit test、lint 和 assemble。

## 阶段 2：网络与连接诊断

- Envelope/Problem DTO 和 fixture。
- REST `/health`, `/ready`, `/server/status`。
- SSE parser/reducer/Last-Event-ID。
- WS connect/hello/heartbeat/pending map。
- Keystore-backed device token。

## 阶段 3：会话主流程

- 会话列表、创建、详情、发送、简单 rewind。
- SSE 运行/步骤/消息/失败事件。
- 断网、429、500、解析失败和后端未就绪状态。
- 审批事件可实现真实「允许 / 拒绝」按钮（后端 `POST /approvals/{run_id}` 已就绪），
  运行终态后收起审批 UI。
- 消息发送是异步的：`POST .../messages` 返回 `run_id`，回答走 SSE，
  不要按同步请求处理。
- 不实现后端尚未支持的多轮后悔快照（rewind 只接收 `keep` 数量）。

## 阶段 4：知识与管理

- 知识搜索、列表、条目、编译状态。
- 工具风险、权限模式、模型列表、监控。
- 调度和团队根据 DTO 冻结情况逐项加入。
- 高风险升级/回滚保持隐藏。

## 阶段 5：设备代理

- 前台连接服务和通知。
- 能力注册、Android 权限引导、命令 dispatcher。
- 先只读能力，再逐项开放点击/输入等写能力。
- 模拟设备测试通过后，按单项授权做真机测试。

## 每阶段门禁

- 网络层 fixture 单测。
- Reducer/ViewModel 状态单测。
- Compose 语义与主流程 UI 测试。
- TalkBack、字体放大、深色、横屏/窄屏检查。
- 无凭据/屏幕/剪贴板敏感日志。
- 文档与后端契约一致性检查。

## 回滚策略

每阶段独立提交；网络 DTO 与 UI 分开变更。后端契约变化优先增加兼容字段，不直接破坏已发布 Android 客户端。