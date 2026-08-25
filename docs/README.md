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
- `[ASK USER]` REST 认证方式和凭据轮换责任。
- `[ASK USER]` 首版哪些会话/团队状态必须跨后端重启恢复。
- `[ASK USER]` OpenAPI 在 Android 开工前还是第一轮 DTO 后冻结。

没有这些决策时可以创建纯网络 fixture 和 UI 原型，但不应发布可连接非 loopback 后端的生产版本。