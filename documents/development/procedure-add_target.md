# 新增 Target

## 适用范围

为产品增加一个 Minecraft 版本与加载器组合。`<target>` 表示新 Target 的名称，格式见 T-01。

## 前置条件

- 加载器已发布支持该 Minecraft 版本的版本；按 T-06 选择最新版，NeoForge 包括 Beta。
- 加载器为 `fabric` 且 Minecraft 版本为 1.x、项目需要 Yarn 时，已查到对应的 Yarn 版本。
- 已查到加载器版本与该 Minecraft 版本要求的 Java 版本。

## 步骤

1. 选择同一加载器、版本最接近的现有 Target 作为来源 `<source>`。
2. 复制 `versions/<source>/` 为 `versions/<target>/`，删除复制来的 `build/`。
3. 在 `versions/<target>/target.properties` 中写入新的 `loader_version` 与 `java_version`；复制来的依赖版本事实（`<名称>_version`）改为支持新版本的发布版，选择规则见 [procedure-update_dependencies](procedure-update_dependencies.md)。
4. Fabric Target 按新的 Minecraft 版本确定插件与映射表（T-03、F-02）：26.1 及以后在 `build.gradle.kts` 中应用 `net.fabricmc.fabric-loom`，并删除 `target.properties` 中的 `mappings` 与 `yarn_version`；1.x 应用 `net.fabricmc.fabric-loom-remap`，映射表默认用 Mojang 官方映射，需要 Yarn 时写 `mappings=yarn` 与 `yarn_version`。
5. 在 `settings.gradle.kts` 的 `targets {}` 中按 Minecraft 版本顺序加入 `register("<target>")`。
6. 向使用者确认哪些已有验证也要覆盖 `<target>`，把它加入这些验证的 `validation.toml` 的 `targets`（V-08）。没有需要游戏的验证覆盖 `<target>` 时，删除复制来的 `src/probe/`。
7. 执行 `.\gradlew.bat :version:<target>:build`，按编译错误把 `src/` 中的每个源码集改写为新版本的 API，包括 `main`、Fabric 的 `client` 与保留下来的 `probe`。
8. 检查加载器元数据中的固定值，例如 `loaderVersion` 与 `pack.mcmeta` 的 `pack_format`，改为新版本的取值。
9. 把新版本与来源版本之间的 API 差异写入 `documents/development/porting-<topic>.md`（T-05）。
10. 按 W-01 同步 `documents/AGENTS.md` 的 Target 表、`README.md` 及其各语言版本，以及 `documents/usage/` 中列出版本的文档。
11. 执行 `.\gradlew.bat spotlessApply`，再执行 `.\gradlew.bat check` 与 `.\gradlew.bat verifyRelease`。
12. 执行 `.\gradlew.bat :version:<target>:runClient` 与 `.\gradlew.bat :version:<target>:runServer`，按 [format-metadata](../reference/format-metadata.md) 的"运行端"一节判断产品是否正常加载，确认后关闭游戏。`runServer` 需要使用者已在 `local.toml` 中接受 EULA（I-02）；没有接受时请使用者决定，不代为写入（G-04），使用者不接受时跳过 `runServer`。

## 验收

- `.\gradlew.bat check` 与 `.\gradlew.bat verifyRelease` 成功，其中 S-05 与 D-12 确认 Target 表已同步。
- 第 6 步加入的验证按各自 `validation.md` 的"运行"一节执行，满足"通过标准"（V-10）。
- 新 Target 的客户端与独立服务端都能启动，`instances/<target>/<variant>/logs/latest.log` 中产品的加载情况符合 [format-metadata](../reference/format-metadata.md) 的"运行端"一节的预期，且没有 `crash-reports/`；使用者未接受 EULA 时，报告中写明独立服务端未验证。

## 禁止

- 在 `build.gradle.kts` 中写版本号（T-02）。
- 为新 Target 修改其他 Target 的源码，或在源码中按版本加条件分支（T-04）。
