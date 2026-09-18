# F0 工程地基（Engine Scaffold）

## 元数据

- 任务：初始化 minibox-android 的 Gradle 工程骨架、CI 自动编译管线与版本契约。
- 作用域：仓库根、`gradle/`、`app/`、`ci/`、`.github/`。
- 参考机制：minibile 仓库（Black0Bag/minibile）的 CI 门禁与版本契约，仅借鉴机制不复制结构。
- 版本基线（沿用 minibile 已验证组合，2026-09 运行中）：
  - AGP 8.13.2 / Kotlin 2.2.21 / Gradle 8.13 / JDK 21 / Compose BOM 2026.02.01
  - compileSdk 36 / targetSdk 34 / minSdk 26
  - applicationId：`io.github.black0bag.minibox`

## 验收标准

1. `./gradlew assembleDebug` 在 CI 上产出 debug APK。
2. `./gradlew :app:testDebugUnitTest` 在 CI 上通过。
3. `release_version.py check-pr` 能阻断违反版本契约的 PR。
4. `pr_check.py plan` 按变更路径正确输出 android_resources / android_jvm / android_full 标志。

## 步骤

| # | 步骤 | 状态 |
| --- | --- | --- |
| 00 | 版本契约：VERSION + CHANGELOG + release_version.py（APPLICATION_ID 适配） | [DONE] |
| 01 | CI 检查脚本移植：pr_check.py / check_repo_hygiene.py / check_markdown_links.py / check_localizations.py | [DONE] |
| 02 | Gradle 根配置：settings / build / properties / wrapper（8.13 阿里云镜像 + SHA256） | [DONE] |
| 03 | Version catalog：libs.versions.toml（裁剪版，仅本阶段依赖） | [DONE] |
| 04 | app 模块：build.gradle.kts（versionCode 契约实现）+ manifest + res + 入口类 | [DONE] |
| 05 | CI workflow：pr-check.yml（fast → android_build → android_tests → candidate 四阶段） | [DONE] |
| 06 | AGENTS.md 执行准则 + docs/TODO 计划系统 | [DONE] |
| 07 | 本地静态验证：py_compile 全部脚本、YAML 语法、release_version check-current、Gradle 配置一致性 | [DONE] |
| 08 | 提交并推送，观察 CI 首跑结果，按报错迭代修复 | 待办 |
| 09 | 回填 docs/structure.md 与本索引，任务标记完成 | 待办 |

## 决策记录

- 不在初始化阶段引入 Navigation 3：仍处 alpha，且 F0 无路由需求；F1 引入时再验证版本（见 docs/plan.md 里程碑 2）。
- 不复制 minibile 的 namespace（`com.ai.assistance.operit` 为其历史遗留）；minibox 使用 `io.github.black0bag.minibox`。
- 不复制 minibile 的 Rust/JNI/STT/WebChat/manual-deps CI 步骤：minibox 无对应组件。
- 仓库原非标准 `wrapper/` 目录已删除，wrapper 统一放 `gradle/wrapper/`。
