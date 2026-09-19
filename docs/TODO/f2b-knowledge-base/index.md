# f2b-knowledge-base（plan.md F2 后半）

- 作用域: 知识库 UI：搜索、分页列表、条目 CRUD、编译作业提交与轮询
- 状态: 已完成（PR #5 已合并 main，v0.4.0，CI 全绿）
- 分支: feature/f2b-knowledge-base
- 版本: 0.3.0 → 0.4.0
- 计划依据: docs/plan.md「F2：知识库与管理 DTO」+ DEVELOPMENT_PLAN 阶段 4「知识搜索、列表、条目、编译状态」

## 契约依据（后端源码已核对）

- api.md §3 知识库；internal/domain/memory/store.go + compile.go + internal/app/kb_handlers.go
- Entry: `id(int64), content(必填), source?, tags?, source_hash?, importance(float), access_count, tier(store|cache), created_at, updated_at, expires_at?`
- Hit: Entry 内嵌 + `score(float) + match_type(fts|vec|both)`
- CompileJob: `id, source, status(pending|processing|ready|failed), progress, total, error?, created_at, updated_at`
- `GET /kb/store?offset=&limit=` → `{entries[]}`（limit≤100 默认 20）
- `POST /kb/search` `{"query","top_k"}` → `{hits[]}`；top_k 默认 5
- 列表固定 TierStore（kb_handlers.go L66 硬编码）

## 明确不做（计划外）

- /kb/distill（阶段 4 未点名）
- /kb/snapshots、/kb/rollback（管理面回滚隐藏，AGENTS.md）

## 步骤

| 步骤 | 内容 | 状态 |
| --- | --- | --- |
| S1 | model：KnowledgeEntry、KbSearchHit、CompileJob、搜索/编译请求体；RestClient DELETE | [DONE] |
| S2 | data：KnowledgeRepository（search/list/get/store/update/delete/compile/job） | [DONE] |
| S3 | navigation：KnowledgeKey 路由 + 设置屏入口 | [DONE] |
| S4 | feature/knowledge：搜索屏（top_k + hits 列表 score/match_type 徽章） | [DONE] |
| S5 | feature/knowledge：条目列表（分页 offset/limit + 加载更多） | [DONE] |
| S6 | feature/knowledge：条目详情（编辑 content/tags/importance + 删除确认）+ 创建 | [DONE] |
| S7 | feature/knowledge：编译（source 提交 + 作业状态轮询 progress/total） | [DONE] |
| S8 | 测试：DTO fixture + Repository MockWebServer | [DONE] |
| S9 | 版本 0.4.0 + CHANGELOG + 提交推送 + PR + CI + 合并 | [DONE] |
