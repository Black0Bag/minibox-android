# f4-integration（前后端四级联调）

- 作用域: 容器内构建并运行后端实例，APP 按 连接诊断 → 会话/SSE → 知识库 → 设备 WS 四级打通，记录并修复契约偏差
- 状态: 进行中
- 分支: feature/f4-integration
- 版本: 0.5.0 起（仅 docs/DTO 契约修正按 patch；含功能改动按 minor）
- 门禁: L3（运行服务与凭据；配置与 token 一律在版本控制外）
- 计划依据: docs/plan.md 任务拆解 F4 + 后端 docs/frontend-handoff.md「联调方式」

## 契约依据

- 后端 `docs/api.md`（REST + 错误 type 全集）、`docs/sse.md`（id/seq/重放）、`docs/websocket.md`（握手、-32001..-32005、命令回执）
- 认证：除 `/health`、`/ready`、`/device/ws` 外全部 Bearer Token；SSE 也带请求头
- 偏差处理顺序（AGENTS.md）：先改后端 `docs/` → 再改前端 DTO，每处单独提交

## 范围

做：

1. 容器内 `go build` + go 门禁（gofmt/vet/test/build）
2. 版本控制外本地配置（`~/.config/minibox/minibox.yaml` + `data/auth.token`），LLM key 不入 git/日志/文档
3. 服务级自检：`/health`、`/ready`、`/server/status`、SSE id/seq、WS 首帧握手
4. APP 四级联调（逐级检查单，现象由设备侧回传）：
   ① 连接诊断 → ② 会话创建/发送/SSE 终态/审批/rewind → ③ 知识库搜索/列表/CRUD/编译轮询 → ④ 设备 WS 握手/hello/心跳
5. 联调记录：方法/路径/脱敏参数、HTTP 状态+`type`、SSE `id/seq/event`、WS `id/method/error.code`

不做：

- 真实设备副作用（点击/输入/截屏等，属 F3）
- `/tools/acquire`、`/upgrade/*`、`/kb/rollback`、`/kb/snapshots` 高风险入口
- 后端 schema 变更与内存态落库（属确认制小项）

## 步骤

| # | 内容 | 状态 |
| --- | --- | --- |
| S1 | 建档 + docs/TODO 与 plan.md 回填 | [DONE] |
| S2 | 后端构建 + go 门禁四连 | [DONE] |
| S3 | 版本控制外配置 + 实例启动 + 服务级自检 | [DONE] |
| S4 | APP 四级联调（逐级检查单 + 现象回传） | [DONE] L1；L2–L4 待执行 |
| S5 | 契约偏差修复（docs 先行）+ 联调结论回填 | [DONE] L1 无契约偏差 |
| S6 | L1 阻塞修复：清单允许明文 http/ws（0.5.1，非契约问题，OS 策略拦截） | [DONE] |

## 偏差记录

| # | 级别 | 现象 | 根因 | 处置 |
| --- | --- | --- | --- | --- |
| 1 | L1 | health/ready/server_status 全部 `CLEARTEXT ... not permitted by network security policy` | targetSdk≥28 默认禁明文；后端仅 http/ws 且地址为裸 IP，network_security_config 无法按 IP 放行 | AndroidManifest 加 `usesCleartextTraffic="true"`（0.5.1）；后端契约无问题 |
| 2 | L1 | 裸 `/health` 返 401、挑战头 grep 未见 | 测试方路径漏 `/api/v1` 前缀；Go 将头名规范化为 `Www-Authenticate` | 非偏差，修正测试方法后全绿 |
