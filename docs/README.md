# Android 前端文档

本目录描述 minibox Android 的**目标工程**。仓库当前尚无 Gradle/Kotlin 源码，文档中的模块名是计划边界，不是已实现声明。

## 开始顺序

1. [`goal.md`](goal.md)：目标和范围。
2. [`BACKEND_API.md`](BACKEND_API.md)：当前后端契约与阻塞项。
3. [`ARCHITECTURE.md`](ARCHITECTURE.md)：Compose、Navigation 3、网络和设备分层。
4. [`DEVELOPMENT_PLAN.md`](DEVELOPMENT_PLAN.md)：可执行阶段。
5. [`UI_GUIDELINES.md`](UI_GUIDELINES.md)：界面和状态规则。
6. [`ACCESSIBILITY.md`](ACCESSIBILITY.md)：无障碍门禁。
7. [`rules.md`](rules.md)：代码、安全和测试规则。

## 当前开放决策

- `[ASK USER]` Android `minSdk` / `targetSdk`。
- `[ASK USER]` APP 通过本机、局域网、VPN/隧道还是公网连接后端。
- `[ASK USER]` 首版哪些会话/团队状态必须跨后端重启恢复
  （会话消息已由后端持久化；团队项目与 SSE 历史仍在内存）。
- `[ASK USER]` OpenAPI 在 Android 开工前还是第一轮 DTO 后冻结。
- `[ASK USER]` 浏览器代理（WebView 执行 + 后端下发）排在哪个阶段。

已解决（2026-09-02）：REST 认证方式（固定 Bearer Token，`data/auth.token`）、
Agent 审批提交 API、teamwork DTO 命名冻结。

现在可以开始创建工程与网络层：认证、错误契约、审批链路和 teamwork DTO 都已
冻结且有测试锁定。剩余开放决策不阻塞网络层与连接诊断的实现。