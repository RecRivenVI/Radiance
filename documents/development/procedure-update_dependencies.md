# 升级依赖版本

## 适用范围

新增或升级依赖的模组与库，或升级加载器、构建插件、格式化工具、GitHub Actions 与 Gradle。升级 Minecraft 版本不属于本规程，按 [procedure-add_target](procedure-add_target.md) 新增 Target。模板文件（G-08）中的版本只随模板版本更新，不在本规程中修改。

## 步骤

1. 查询待升级依赖的发布版本：

   | 依赖 | 记录位置 | 查询地址 |
   | --- | --- | --- |
   | NeoForge | `versions/<target>/target.properties` | `https://maven.neoforged.net/releases/net/neoforged/neoforge/maven-metadata.xml` |
   | Forge | `versions/<target>/target.properties` | `https://maven.minecraftforge.net/net/minecraftforge/forge/maven-metadata.xml` |
   | Fabric Loader | `versions/<target>/target.properties` | `https://maven.fabricmc.net/net/fabricmc/fabric-loader/maven-metadata.xml` |
   | 随 Target 变化的模组与库，如 Fabric API、Sodium | `versions/<target>/target.properties` 的 `<名称>_version` | 依赖所在的 Maven 仓库，如 `https://api.modrinth.com/maven` |
   | 各 Target 共用同一版本的库 | `gradle/libs.versions.toml` | Maven Central 或依赖所在的 Maven 仓库 |
   | 闭源模组、本机的兄弟项目产物等本地输入 | `inputs.toml` 的 `sha256` | 使用者提供的文件，或 `source` 写明的获取地址 |
   | ModDevGradle | `gradle/libs.versions.toml` | `https://maven.neoforged.net/releases/net/neoforged/moddev-gradle/maven-metadata.xml` |
   | Loom | `gradle/libs.versions.toml` | `https://maven.fabricmc.net/net/fabricmc/fabric-loom/maven-metadata.xml` |
   | 其他构建工具 | `gradle/libs.versions.toml` | Maven Central 或 Gradle Plugin Portal |
   | GitHub Actions | `.github/workflows/*.yml` | 各 Action 仓库的最新发布 |
   | Gradle | `gradle/wrapper/gradle-wrapper.properties` | `https://services.gradle.org/versions/current` |

2. 按以下规则选择版本，并修改记录位置中的具体版本号；新增依赖时，在 Target 脚本中用 `target.module("<group>:<name>", "<名称>_version")` 或 `libs.<别名>` 引用它（F-04）：

   | 依赖 | 选择规则 |
   | --- | --- |
   | Forge | 该 Minecraft 版本的最新版，忽略推荐版本（T-06） |
   | NeoForge | 该 Minecraft 版本的最新版，包括 Beta（T-06） |
   | Fabric Loader | 支持该 Minecraft 版本的最新版（T-06） |
   | 依赖的模组 | 支持该 Minecraft 版本与加载器的最新发布版 |
   | 构建工具与 GitHub Actions | 最新稳定版，不使用 beta、snapshot 或 alpha |

3. 升级 Gradle 时执行下面的命令，`<sha256>` 取自 `https://services.gradle.org/distributions/gradle-<version>-bin.zip.sha256`；命令执行两次，第二次由新版本的 Gradle 重新生成 `gradlew`、`gradlew.bat` 与 `gradle/wrapper/gradle-wrapper.jar`。然后把 `NOTICE` 中的 Gradle 版本改为新版本：

   ```powershell
   .\gradlew.bat wrapper --gradle-version <version> --distribution-type bin --gradle-distribution-sha256-sum <sha256>
   ```

4. 需要改变守护进程的 JDK 版本时，执行 `.\gradlew.bat updateDaemonJvm --jvm-version=<java>`，并同步 `.github/workflows/check.yml` 中的 `java-version`。
5. 加载器版本变化时，按 W-01 同步 `documents/AGENTS.md` 的 Target 表、`README.md` 及其各语言版本，以及 `documents/usage/` 中列出版本的文档。
6. 依赖的模组或库变化时，按 W-01 同步加载器元数据中的依赖声明（版本要求用同名占位符）与 `documents/reference/dependency-<topic>.md`。
7. 本地输入变化时，在 `inputs.toml` 中更新 `version`、`description`、`source` 与 `sha256`，名称与版本按 F-05 取自模组元数据或上游发布，`sha256` 用 `Get-FileHash -Algorithm SHA256 <file>` 计算后改为小写；请使用者在自己的 `local.toml` 中写入新文件的路径，然后执行 `.\gradlew.bat verifyInputs`（F-05）。删除本地输入时，同时从 Target 脚本与所有实例设置的 `mods` 中删除对它的引用，并请使用者删除 `local.toml` 中 `[inputs]` 的对应路径（F-03）。
8. 执行 `.\gradlew.bat spotlessApply`，格式化工具升级可能改变格式。
9. 执行 `.\gradlew.bat check`；加载器、依赖的模组或库、内嵌的第三方库变化时，再执行 `.\gradlew.bat verifyRelease`。
10. 加载器或依赖的模组变化时，在受影响的 Target 上启动 `runClient` 与 `runServer`，按 [format-metadata](../reference/format-metadata.md) 的"运行端"一节判断产品是否正常加载。`runServer` 需要使用者已在 `local.toml` 中接受 EULA（I-02）；没有接受时请使用者决定，不代为写入（G-04），使用者不接受时跳过 `runServer`。

## 验收

- `.\gradlew.bat check` 成功，其中 S-05 与 D-12 确认 Target 表已同步；执行了第 9 步的 `verifyRelease` 时它也成功。
- 受影响的 Target 的客户端与独立服务端都能启动，产品的加载情况符合 [format-metadata](../reference/format-metadata.md) 的"运行端"一节的预期；使用者未接受 EULA 时，报告中写明独立服务端未验证。

## 禁止

- 在构建脚本中写版本号（T-02）。
- 手工修改第三方或生成的文件（C-09）；它们只由对应的 Gradle 任务生成。
