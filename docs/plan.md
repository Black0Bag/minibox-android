# Android 实施计划（Plan）

## 里程碑

1. 决策与契约冻结。
2. Gradle/Compose/Navigation 3 工程。
3. REST/SSE/WS 网络地基。
4. 会话主流程。
5. 知识与管理。
6. 设备代理与真机分项验收。

## 任务拆解

- F0：模型、网络、连接诊断。→ v0.1.0 已完成（PR #2）
- F1：会话、SSE reducer、错误状态。→ v0.2.0 已完成（PR #3）
- F2：知识库与管理 DTO。→ v0.3.0 完成了「管理」半（权限/工具，PR #4）；**知识库半进行中（本任务 f2b-knowledge-base，v0.4.0）**
- F3：设备前台服务、RPC 和执行器。→ 未开始（依赖阶段 2 遗留的 WS 客户端）

## 计划偏移修正记录（2026-09-19）

- v0.3.0（权限+工具+rewind）实际对应 DEVELOPMENT_PLAN 阶段 4 的「管理」部分
  提前 + 阶段 3 的「简单 rewind」尾巴，编号与 plan.md 的 F2 定义不一致。
- 修正：知识库（/kb/*）作为 f2b-knowledge-base 继续完成 plan.md F2 的另一半；
  rewind 已交付不再重复。后续特性开工前先对表 docs/TODO/ROADMAP.md。
- 阶段 2 遗留：WS connect/hello/heartbeat 客户端未实现，列入 F3 前置。

## 本任务范围（f2b-knowledge-base，按 DEVELOPMENT_PLAN 阶段 4）

- 做：知识搜索、条目列表（分页）、条目详情/编辑/删除、条目创建、编译提交与作业状态轮询。
- 不做：/kb/distill（阶段 4 未点名）、/kb/snapshots 与 /kb/rollback（管理面回滚保持隐藏）。

## 验收节点

- Mock fixture 通过后才连接稳定后端。
- 模拟设备通过后才申请真机副作用授权。
- 每个屏幕通过语义、字体、深色和窄屏检查。

## 风险与回滚

- 风险：后端认证/审批/持久化契约未完全冻结。
- 回滚：阶段独立提交；首版隐藏高风险未完成入口；DTO 保持兼容。
