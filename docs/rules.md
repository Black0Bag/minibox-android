# Android 编码规则（Rules）

## 默认规则（创建即生效）

- 单向数据流，状态下沉、事件上行。
- Composable 不直接做 I/O。
- REST/SSE/WS 客户端分离，共用模型和错误语义。

## 命名规范

- Kotlin/Compose 按官方命名；路由为可序列化 `NavKey`。
- JSON DTO 显式映射后端 lower_snake_case。

## 代码风格

- 公共 Composable 提供 `modifier`、Light/Dark Preview。
- 使用 MaterialTheme，稳定尺寸和响应式约束。

## 错误处理

- Problem Details 转领域错误；网络、认证、限流、解析、业务失败分开。
- SSE/WS 重连有界并可取消。

## 日志与注释

- 日志脱敏；不记录 token、auth、屏幕、剪贴板和完整用户正文。

## 安全与隐私

- 凭据使用 Keystore-backed storage。
- 危险动作 fail-closed，必须检查权限/能力/审批。

## 测试与回归要求

- reducer、ViewModel、Repository、协议 fixture 和关键 Compose semantics 必测。
- 真机副作用不进入普通 CI。

## 禁止事项（反模式）

- 禁止直连后端 SQLite。
- 禁止复制 Agent/LLM 内核。
- 禁止实现后端不存在的可执行审批/升级/回滚 UI。