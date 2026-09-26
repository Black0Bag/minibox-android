# Android 目标结构（Structure）

## 目录结构

```text
app/src/main/java/com/blackbag/minibox/
├── MiniboxApplication.kt
├── MainActivity.kt
├── core/model/
├── core/network/
├── core/security/
├── data/
├── navigation/
├── feature/connection/
├── feature/conversations/
├── feature/chat/
├── feature/knowledge/
├── feature/settings/
└── feature/device/
```

源码已在 app 模块按 package 实现（当前版本 0.5.0）；达到明确规模后再拆 Gradle 模块。

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

- `app/src/main/java/com/blackbag/minibox/MiniboxApplication.kt`（F0）
- `app/src/main/java/com/blackbag/minibox/MainActivity.kt`（F0）
- `app/src/main/java/com/blackbag/minibox/navigation/AppNavDisplay.kt`（F1 起承载 Navigation 3 导航）

## 工程地基落地记录（F0，2026-09-18）

- 构建：Gradle 8.13 wrapper + AGP 8.13.2 + Kotlin 2.2.21 + Compose BOM 2026.02.01；compileSdk 36 / targetSdk 34 / minSdk 29 / JDK 17 字节码（CI 用 JDK 21 运行）。
- 版本契约：根目录 `VERSION`（bootstrap=0.0.0）→ `versionCode = major×1,000,000 + minor×1,000 + patch + 1`；`ci/script/release_version.py` 与 `app/build.gradle.kts` 双侧实现，CI `check-pr` 强制。
- CI 门禁：`.github/workflows/pr-check.yml` 四阶段 fast → android_build(assembleDebug) → android_tests(testDebugUnitTest) → candidate；仅 pull_request 触发（merge ref 双亲校验）。
- 发布：`.github/workflows/release-apk.yml` push main 触发 → 版本契约 + assembleDebug 成功后自动建 `v{VERSION}` Release（CHANGELOG notes + APK + sha256）；debug keystore 走 actions cache 保签名稳定。
- 首跑结果：PR #1 CI 全绿（run 35314481436）。

## 高风险模块

- 长连接生命周期和乱序响应。
- 设备权限/副作用。
- 凭据与屏幕数据。
- 后端契约漂移。