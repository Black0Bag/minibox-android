# ROADMAP 对齐表

本文件把 `docs/plan.md` 五阶段里程碑与已发版本逐项对齐。**每个特性开工前必须先对表本文件**，防止范围漂移。

## 计划阶段 ↔ 版本对照

| plan.md 里程碑 | DEVELOPMENT_PLAN 阶段 | 内容 | 状态 | 版本 |
| --- | --- | --- | --- | --- |
| 1. 决策与契约冻结 | 阶段 0 | 契约冻结、后端 P0 阻塞确认 | ✅ | 交付前 |
| 2. Gradle/Compose/Nav3 工程 | 阶段 1 | 工程地基 + CI + 版本契约 | ✅ | 0.1.0 前（f0-engine-scaffold） |
| 3. REST/SSE/WS 网络地基 | 阶段 2 | Envelope/REST 诊断/SSE | ✅ REST+SSE | 0.1.0（PR #2） |
| | | **WS connect/hello/heartbeat** | ❌ 遗留 | F3 前置 |
| 4. 会话主流程 | 阶段 3 | 会话 CRUD + SSE 状态机 + 审批 + 简单 rewind | ✅ | 0.2.0（PR #3） |
| 5. 知识与管理 | 阶段 4 | **知识库**：搜索/列表/条目/编译状态 | 🔨 本任务 | 0.4.0（f2b） |
| | | **管理**：工具/权限/模型列表/监控 | ⚠️ 部分 | 0.3.0（PR #4）仅工具+权限；LLM 列表、监控未做 |
| 6. 设备代理 | 阶段 5 | 前台服务 + RPC + 执行器 | ❌ 未开始 | F3 |

## 已明确排除（政策性）

- `/tools/acquire`、`/upgrade/*`、`/kb/rollback`、`/kb/snapshots`：高风险，首版 UI 隐藏（AGENTS.md）。
- `/kb/distill`：DEVELOPMENT_PLAN 阶段 4 未点名，不做。
- 调度/团队：按 DTO 冻结情况逐项加入，未点名不做。

## 欠账清单（按计划顺序）

1. **知识库**（本任务 f2b-knowledge-base）：搜索、分页列表、条目 CRUD、编译作业轮询 → 0.4.0
2. **WS 客户端**（阶段 2 遗留）：connect/hello/heartbeat/pending map + Keystore device token → F3 前置
3. **管理面补全**（阶段 4 后半）：LLM 模型列表、监控 → 计划内待排
4. **设备代理**（阶段 5）：前台服务 + RPC dispatcher + 18 项执行器（先只读后写入）→ F3
