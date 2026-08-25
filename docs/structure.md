# Android 目标结构（Structure）

## 目录结构

```text
app/src/main/java/.../
├── core/model/
├── core/network/
├── core/security/
├── core/designsystem/
├── data/
├── navigation/
├── feature/connection/
├── feature/chat/
├── feature/knowledge/
├── feature/device/
└── feature/admin/
```

当前尚无源码；先在 app 模块按 package 实现，达到明确规模后再拆 Gradle 模块。

## 模块划分

- model：DTO/领域显示模型。
- network：REST/SSE/WS。
- data：Repository 和缓存。
- feature：ViewModel、UiState、无状态 Compose 内容。
- device：前台连接服务和系统执行器。

## 数据流/调用关系

Compose → ViewModel → Repository → REST/SSE/WS → Go 后端；事件反向归约为 UiState。

## 依赖与外部接口

- minibox REST/SSE/WS。
- Android Accessibility、MediaProjection、Notification、TTS/STT。
- Keystore-backed credential storage。

## 关键入口文件

待创建：Application、MainActivity、AppNavDisplay、连接 composition root。

## 高风险模块

- 长连接生命周期和乱序响应。
- 设备权限/副作用。
- 凭据与屏幕数据。
- 后端契约漂移。