# Compose UI 指南

## 设计目标

这是反复使用的 Agent 工具界面，不是营销站。优先清晰、扫描效率、稳定布局和可恢复状态。

## Compose 组件规则

- 公共 Composable：状态参数在前，事件 callback 其后，`modifier: Modifier = Modifier` 作为首个可选参数。
- modifier 应用于根布局。
- Screen 收集 ViewModel 状态；Content 无状态、可 Preview 和 UI 测试。
- 昂贵计算使用 `remember`；高频状态到阈值使用 `derivedStateOf`。
- 列表提供稳定 key；网络或数据库调用不得出现在组合体。
- 每个公共内容 Composable 提供 Light/Dark Preview。

## 布局

- 使用稳定的 toolbar、输入区、消息/设备列表尺寸约束，避免动态状态造成跳动。
- 卡片仅用于独立重复项或工具；不在卡片里继续嵌套卡片。
- 页面 section 使用普通布局，不做漂浮卡片墙。
- 圆角保持克制；使用 Material Theme，不硬编码整套单一色调。
- 文本允许换行；长 URL/模型名可截断并提供完整可访问文本。

## 控件选择

- 图标按钮：返回、刷新、发送/停止、复制等熟悉动作。
- Segmented control：权限模式或视图模式。
- Switch/checkbox：二元设置。
- Menu：模型、供应商等选项集。
- Slider/stepper/input：数值配置。
- 高风险动作使用明确文本、图标和确认信息，不只靠颜色。

## 屏幕状态

每个屏幕都应具备：Loading、Content、Empty、Recoverable Error、Blocking Error、Offline/Reconnecting。不要用 Toast 代替持久错误状态。

## 会话

- 消息区域和输入区域不互相遮挡。
- Agent 运行状态独立于助手消息，失败不能渲染成正常回答。
- SSE 回放事件不应重复产生消息。
- 发送按钮与停止按钮使用稳定尺寸，不因文案变化改变布局。

## 设备

- 设备列表突出在线、能力和最后活动时间。
- 危险命令显示目标、动作、参数摘要、审批/拒绝原因。
- 无权限能力禁用并提供进入系统设置的明确操作。

## 动效

只用于状态连续性：连接状态、列表插入、面板展开。遵守系统减少动画设置，不用持续装饰动画或干扰阅读的效果。