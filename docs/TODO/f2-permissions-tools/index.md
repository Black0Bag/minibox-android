# F2: 权限 + 工具 + 会话回退

- 作用域: Android 管理面：权限模式查看/切换（yolo 高风险警示）+ 工具列表动态展示 + 聊天屏 rewind
- 状态: 已完成（PR #4 已合并 main，v0.3.0，CI 一次全绿）
- 分支: feature/f2-permissions-tools
- 版本: 0.2.0 → 0.3.0

## 契约依据（后端源码已核对）

- api.md §4 工具、§5 权限、§2 会话 rewind
- 后端 internal/app/tools_handlers.go：
  - GET /permissions/ → data {mode, modes[]}（modes 固定 4 值）
  - PATCH /permissions/mode {"mode":"..."} → data {mode}；非法模式 400 invalid_mode
  - GET /tools/ → data {count, tools[]}，toolView={name, description, metadata, json_schema}
- tools.Metadata：read_only/destructive/concurrency_safe/search_or_read/open_world/max_result_size/risk_tier(low|medium|high)/requires_approval

## 关键决策

- 不做 /tools/acquire（高风险，首版 UI 不暴露，与 upgrade 同策略）
- 设置合并单屏（权限卡片 + 工具列表），入口在会话列表 TopAppBar
- RestClient 补 PATCH 方法（通用，body JSON）
- yolo 模式切换需二次确认对话框（后端无确认，前端自担）
- rewind 复用 F1 ConversationsRepository.rewind，UI 加菜单+轮数对话框

## 步骤

| 步骤 | 内容 | 状态 |
| --- | --- | --- |
| S1 | model：PermissionsData、ToolInfo+ToolMetadata、ToolsData；RestClient.patch | [DONE] |
| S2 | data：PermissionsRepository、ToolsRepository | [DONE] |
| S3 | navigation：SettingsKey + 会话列表入口 | [DONE] |
| S4 | feature/settings：权限区（4 模式+yolo 二次确认）+ 工具区（risk_tier 标色） | [DONE] |
| S5 | feature/chat：rewind 菜单 + 轮数对话框 + 刷新 | [DONE] |
| S6 | 测试：DTO fixture + Repository MockWebServer | [DONE] |
| S7 | 版本 0.3.0 + CHANGELOG + 提交推送 | [DONE] |
