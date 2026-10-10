# 新增组件

## 适用范围

新增一个项目组件：随模组发布的 `product` 组件，包括与 Minecraft 无关的代码、原生库与资源；或 `tool` 组件，包括构建工具、验证工具与脚本。`<component>` 为组件名，由小写英文单词组成，多个词用 `_` 连接（L-04）。组件的构建方式见 [architecture-build](../design/architecture-build.md)，移除组件见 [procedure-remove_component](procedure-remove_component.md)。

## 步骤

1. 确认要放进组件的内容不引用 Minecraft 与加载器的类；引用它们的代码留在 Target 中（L-06）。
2. 创建 `components/<component>/settings.gradle.kts`：

   ```kotlin
   pluginManagement {
       includeBuild("../conventions")
   }

   plugins {
       id("io.github.recrivenvi.component")
   }

   rootProject.name = "<component>"
   ```

3. 创建 `components/<component>/build.gradle.kts`，按组件的内容编写。`product` 组件必须应用 `java` 或 `java-library`，Target 从它构建的 JAR 中取得类与资源；组件设置插件只应用 `base`，不生成 JAR：

   | 内容 | 写法 |
   | --- | --- |
   | Java 库 | 应用 `java-library`；源码放在 `src/main/java/` 中 `mod_group` 之下的包里，测试放在 `src/test/java/` |
   | 原生库 | 应用 `java`；注册调用 CMake 或 Cargo 的任务，让 `jar` 把产物收进 `natives/<os>-<arch>/`；让 `check` 依赖原生测试与格式检查任务 |
   | 脚本工具 | 注册运行脚本测试的 `Exec` 任务，让 `check` 依赖它；脚本优先跨平台，只能在 Windows 上运行时在说明文档中写明 |
   | 光影包、资源包等非代码内容 | 应用 `java`；内容放在 `src/main/resources/`，由 `jar` 打包后合并进模组 JAR；需要单独发布的压缩包由组件的任务生成 |

4. 把第三方源码放进 `components/<component>/third_party/`；Git 子模块用 `git submodule add <url> components/<component>/third_party/<name>` 添加，`<url>` 为子模块仓库地址，`<name>` 为子模块目录名。
5. 组件依赖第三方库时，在组件中用 `implementation(libs.<alias>)` 声明，`<alias>` 为 `gradle/libs.versions.toml` 中的别名；`product` 组件的第三方库还要由每个 Target 用加载器原生方式内嵌：NeoForge 与 Forge 用 `jarJar(libs.<alias>)`，Fabric 用 `include(libs.<alias>)`。
6. 在根目录 `settings.gradle.kts` 的 `components {}` 中登记：随模组发布的写 `product("<component>")`，其他写 `tool("<component>")`。
7. 在 `documents/design/architecture-<component>.md` 中说明组件的职责、接口与构建方式（W-01）。
8. 执行 `.\gradlew.bat spotlessApply`，再执行 `.\gradlew.bat check`。
9. `product` 组件另外执行 `.\gradlew.bat build`，再在参考 Target 上执行 `.\gradlew.bat :version:<reference>:runClient`，`<reference>` 为项目规范中的参考 Target；确认产品正常加载并调用到组件。

## 验收

- `.\gradlew.bat check` 成功，L-08 没有发现。
- `product` 组件：`.\gradlew.bat verifyRelease` 成功，它确认组件的类与资源出现在每个 Target 的发行 JAR 中。
- 第 9 步的日志中出现组件产生的输出，或产品调用组件的功能正常工作。

## 禁止

- 在 Target 的 `build.gradle.kts` 中为组件本身写依赖或打包配置（T-03、V-05）。
- 修改 `third_party/` 中的第三方源码（C-09）。
- 把组件放在模板组件 `configuration`、`conventions`、`compliance` 中（G-08）。
