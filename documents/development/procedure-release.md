# 发布新版本

## 适用范围

为产品模组发布一个新版本：确定版本号、整理更新日志、构建并检查全部 Target 的发行 JAR。上传到 Modrinth、CurseForge 等平台不在本规程中，由使用者完成。`<version>` 为新版本号，符合语义化版本。

## 前置条件

- 使用者已确认本次发布包含哪些改动，以及新版本号 `<version>`。
- 工作区没有未提交的改动。

## 步骤

1. 在 `gradle.properties` 中把 `mod_version` 改为 `<version>`。
2. 在 `documents/release/` 的更新日志中，把 `## [未发布]` 改为 `## [<version>] - <date>`，`<date>` 为当天日期，写作 `YYYY-MM-DD`；在其上方新建空的 `## [未发布]`（D-14）。
3. 核对更新日志的条目覆盖本次发布的全部玩家可见改动，玩家文档与新行为一致（W-01）。
4. 执行 `.\gradlew.bat spotlessApply`，再执行 `.\gradlew.bat check`。
5. 执行 `.\gradlew.bat verifyRelease`：它把每个 Target 的发行 JAR 收集到 `build/release/<version>/`，并核对每个 JAR 有加载器元数据（直接包含，或位于其中恰好一个内嵌 JAR 里）、`META-INF/` 中的许可文件与 `product` 组件的内容，没有 `probe` 源码集的类与元数据（V-06）；Target 开启了源码 JAR 时一并核对其中的许可文件。命令失败时按输出修正，再执行一次。
6. 在每个 Target 上执行 `runClient` 与 `runServer`，按 [format-metadata](../reference/format-metadata.md) 的"运行端"一节判断产品是否正常加载；`runServer` 需要使用者已在 `local.toml` 中接受 EULA（I-02）；没有接受时请使用者决定，不代为写入（G-04），使用者不接受时跳过 `runServer`。运行端为 `client` 或 `server` 时按 [procedure-switch_side](procedure-switch_side.md) 确认与未安装本模组的一端互通。
7. 向使用者报告 `build/release/<version>/` 中的文件清单与第 6 步的结果；提交、打标签与上传由使用者决定。

## 验收

- `.\gradlew.bat check` 成功。
- `.\gradlew.bat verifyRelease` 成功，`build/release/<version>/` 中的 JAR 数量等于已登记的 Target 数量。
- 更新日志的最新版本标题为 `[<version>] - <date>`，其上方是空的 `[未发布]`。

## 禁止

- 未经使用者同意提交、打标签、推送或上传。
- 发布开发环境的 JAR：1.20.1 Forge 与 1.x Fabric 在 `build/devlibs/` 中的 JAR 不是发行版。
