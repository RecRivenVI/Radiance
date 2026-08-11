# 构建架构

构建由模板插件 `gradle/conventions/` 组织。它是一个包含构建，模块 `configuration` 负责事实与配置，`platform` 负责每个 Target 的构建、组件登记、日常实例、验证运行与发行核对，`component` 为项目组件提供公共设置，`compliance` 负责合规规则；`specification/` 是本规范的附件，不是 Gradle 模块。Target 的构建脚本只声明加载器插件与该 Target 独有的原生配置，项目组件只声明自己的构建内容，其余配置由插件统一提供。

## 组成

[settings.gradle.kts](../../../../settings.gradle.kts) 通过 `pluginManagement { includeBuild("gradle/conventions") }` 引入模板插件，只应用一个设置插件 `io.github.recrivenvi.conventions`，在 `targets {}` 中登记 Target，在 `components {}` 中登记项目组件。

| 模块 | 对外插件 | 职责 |
| --- | --- | --- |
| `configuration` | 无 | 提供 `targets {}` 登记；读取并校验项目事实、Target 事实、日常实例配置与每项验证的 `[instances]`，算出每个日常实例与验证实例的启动参数；在构建的 Gradle 对象上注册扩展 `repositoryConfiguration`，在根项目注册任务 `verifyConfiguration`、`printLaunchSettings` 与 `verifyInputs` |
| `platform` | `io.github.recrivenvi.conventions` | 插件入口：依次应用 `configuration` 的设置插件、foojay 工具链解析插件与 `compliance` 的设置插件；提供 `components {}` 登记；把实例与组件的名单交给 `compliance`；为根项目应用 `RootConventions`，为每个 Target 应用 `TargetConventions` |
| `component` | `io.github.recrivenvi.conventions.component` | 项目组件在自己的 `settings.gradle.kts` 中应用，见下文"项目组件"；只依赖 Spotless，因此组件不会带上 Minecraft 构建插件，也不必声明它们所在的 Maven 仓库 |
| `compliance` | 无 | 提供 `compliance {}` 设置；注册 `verifyCompliance` 与 `printVocabulary`；AGENTS.md 每一节由一个检查类实现，见 [mechanism-compliance](mechanism-compliance.md) |

模板插件是模板文件（G-08），项目不修改。它是一个普通的 Gradle 多项目构建，可以用 `.\gradlew.bat -p gradle/conventions check` 单独构建并运行全部测试。插件入口放在 `platform` 模块中，因为入口要应用另外两个模块的插件，而 `platform` 本来就依赖 `configuration`；放在 `configuration` 中会形成循环依赖。

## 插件类路径

ModDevGradle（下称 MDG）、Loom 与 Spotless 是 `platform` 模块的依赖，因此位于设置阶段的类路径上。Target 脚本按 ID 应用它们而不写版本；版本只在 [libs.versions.toml](../../../libs.versions.toml) 中定义：Spotless 等与 Minecraft 无关的工具由模板定义（F-06），MDG 与 Loom 的版本由项目按 [procedure-update_dependencies](../development/procedure-update_dependencies.md) 升级。反过来，Target 脚本不能再为这些插件指定版本（T-02）。`configuration` 与 `compliance` 用 tomlj 解析 TOML 文件，它同样是模板插件的依赖，不进入任何模组。

Loom 1.18 以 Java 25 编译，因此 Gradle 守护进程必须运行在 JDK 25 上，由 [gradle-daemon-jvm.properties](../../../gradle-daemon-jvm.properties) 保证。

## Target 约定

`TargetConventions` 在 Target 脚本执行之前应用。它从构建的 Gradle 对象读取仓库配置与组件登记，二者在设置脚本求值后、任何项目创建之前就已就绪；因此初始化脚本用 `allprojects {}` 提前访问项目时（CodeQL 的依赖分析就是这样），Target 约定也能正常应用。它依次完成：

1. 应用 `java` 与 Spotless；从事实设置 group 与 version，归档名为 `<mod_id>-<target>`。
2. 按 `java_version` 设置工具链与 `--release`，源码编码为 UTF-8。
3. 注册扩展 `target`，供脚本读取事实、声明随 Target 变化的依赖与引用本地输入，见 [configuration-repository](../reference/configuration-repository.md)；注册该 Target 的 `verifyInputs`，让编译任务依赖它。Loom 在配置阶段就解析类路径，因此缺失或不符的本地输入在配置阶段只是不提供文件，由 `verifyInputs` 在编译前报告原因。
4. 把 `src/main/templates/` 展开到 `build/generated/sources/templates/main/` 并加入源码，展开加载器元数据文件。展开值按字符串规则转义，因此占位符只能放在字符串中（P-01）。把根目录的 `LICENSE`（可带 `.md` 或 `.txt`）、`NOTICE`、`COPYING`、`COPYING.LESSER` 复制进模组的 `META-INF/`，把 `licenses/` 复制进 `META-INF/licenses/`；Target 脚本开启源码 JAR（`java { withSourcesJar() }`）时，源码 JAR 以同样的位置包含这些文件，1.x Fabric 重映射后的源码 JAR 也一样。
5. Target 有 `src/probe/` 时创建 `probe` 源码集：编译与运行类路径包含 `main` 的输出与类路径，Fabric 还包含 `client`；同样展开 `src/probe/templates/` 与元数据；让 `check` 编译它。探针缺少产品已有的元数据文件时在配置阶段失败。
6. 按加载器准备原生目录：NeoForge 与 Forge 把 `src/generated/resources/` 加入资源并排除数据生成的 `.cache/`；Fabric 在 Loom 应用后添加 Minecraft 与 Fabric Loader 依赖，1.x 版本另按 `mappings` 添加映射表，并开启 `client` 源码集。
7. 让每个 `product` 组件成为 `implementation` 依赖，并把它的 JAR 内容合并进模组 JAR。Gradle 按压缩包整体跟踪合并来源，脚本给 `jar` 加的包含与排除规则因此也列为 `jar` 的输入，规则改变时重新打包。注册该 Target 的 `verifyRelease`：构建发行 JAR，核对其中有本模组的加载器元数据、`META-INF/` 中的许可文件与 `product` 组件的全部条目，没有 `probe` 源码集的文件与探针的元数据；脚本开启源码 JAR 时一并核对其中的许可文件。模组需要由加载器原生的引导层包装时，Target 脚本把包装后的 JAR 作为发行 JAR 写到 `build/libs/<mod_id>-<target>-<version>.jar` 并挂到 `assemble` 上，模组 JAR 改写到其他位置；这时本模组的加载器元数据可以位于发行 JAR 中恰好一个内嵌 JAR 里，其他内嵌模组按元数据中的模组 ID 区分，内嵌 JAR 中出现探针的元数据时报告；许可文件与 `product` 组件的条目仍须直接位于发行 JAR 中。
8. 为每个日常实例注册准备任务：创建实例目录；把实例设置 `mods` 中的本地输入核对哈希后复制进 `mods/`，并移除上次复制、这次已不在列表中的副本，不覆盖或删除使用者自己的文件，规则见 [configuration-repository](../reference/configuration-repository.md)；服务端首次运行时写入 `online-mode=false` 与 `white-list=false`，让离线账号的第二名玩家可以加入；客户端首次运行时写入只含 `onboardAccessibility:false` 的 `options.txt`，跳过新实例的无障碍引导界面，其余设置由游戏补齐；文件已存在时都不覆盖；使用者接受 EULA 时写入 `eula.txt`。
9. 为每项验证 `targets` 中的这个 Target 与 `[instances]` 中的每个角色注册同样的准备任务，实例目录为 `validations/<name>/instance/<target>/<角色>/`。
10. 脚本求值结束后，确认应用了与加载器匹配的插件（T-03），再调用对应的适配器。

| 加载器 | 插件 | 适配器 |
| --- | --- | --- |
| `neoforge` | `net.neoforged.moddev` | `neoForge.enable` 设置版本并关闭重编译；登记产品模组与探针模组；版本仍需要时把 `product` 组件加入 `additionalRuntimeClasspath`；为每个日常实例与验证运行创建运行 |
| `forge` | `net.neoforged.moddev.legacyforge` | `legacyForge.enable` 设置 `<minecraft>-<loader_version>`；其余同上 |
| `fabric`，26.1 及以后 | `net.fabricmc.fabric-loom` | 登记产品模组，包括 `main`、`client` 与 `product` 组件，以及探针模组；为每个日常实例与验证运行创建运行 |
| `fabric`，1.x | `net.fabricmc.fabric-loom-remap` | 同上；Fabric Loader 以 `modImplementation` 添加，映射表为 `officialMojangMappings()` 或 `net.fabricmc:yarn:<yarn_version>:v2` |

适配器在求值结束后运行。Fabric 的 `client` 源码集必须在脚本执行前开启，脚本才能使用 `clientImplementation` 等配置，因此第 6 步监听 Loom 内部的 `fabric-loom` 插件，而不是 `net.fabricmc.fabric-loom`：在后者应用之前注册监听器，会让 Loom 自身的 `hasPlugin` 检查失效，使 Loom 误以为游戏仍被混淆而要求映射表。插件创建的运行在脚本执行时还不存在，Target 脚本要调整它们时，使用 `neoForge.runs.configureEach {}` 或 `loom.runs.configureEach {}` 这样的延迟配置。

日常实例与验证运行之外的运行，例如 Target 脚本自己创建的数据生成运行与 Loom 的游戏测试运行，目录统一设为 `versions/<target>/build/run/<运行名>/`（I-05），不会在 Target 目录中留下 L-03 不允许的文件。

验证运行与日常实例使用相同的启动参数规则；任务名、系统属性与其他规则见 [format-validation](../reference/format-validation.md)。两种插件中探针的接入方式如下：

| 项 | MDG | Loom |
| --- | --- | --- |
| 类路径 | 运行的源码集设为 `probe` | 运行的源码集设为 `probe` |
| 加载的模组 | 验证运行加载全部登记的模组；其他运行默认不加载探针模组 | 加载器只把类路径上的目录识别为模组，探针只在 `probe` 源码集的类路径上 |

没有 `src/probe/` 的 Target 上，验证运行使用 `main`（Fabric 客户端为 `client`）源码集，只加载产品模组。

## 加载器原生目录

| 加载器 | 目录 | 来源 | 模板的处理 |
| --- | --- | --- | --- |
| Fabric | `src/client/` | Loom 的 `splitEnvironmentSourceSets()` | 总是开启，并登记进产品模组 |
| Fabric | `src/datagen/`、`src/main/generated/` | Fabric API 的 `fabricApi.configureDataGeneration { createSourceSet = true }` 与默认输出目录 | 由 Target 脚本按需开启，运行目录移到构建目录 |
| Fabric | `src/gametest/` | Fabric API 的 `fabricApi.configureTests { createSourceSet = true }` | 由 Target 脚本按需开启 |
| NeoForge、Forge | `src/generated/resources/` | MDK 的数据生成输出目录 | 总是加入资源 |

NeoForge 与 Forge 没有独立的客户端源码集，客户端代码与服务端代码同在 `main` 中，用 `Dist` 区分。

## 项目组件

项目组件放在 `components/<component>/`，是独立的 Gradle 构建，在 `settings.gradle.kts` 中登记：

```kotlin
components {
    product("runtime_safety")
    tool("acceptance_gates")
}
```

| 种类 | 用途 | 构建中的处理 |
| --- | --- | --- |
| `product` | 随模组发布、与 Minecraft 无关的代码、原生库与资源 | 每个 Target 依赖它；它的 JAR 内容合并进模组 JAR，开发运行时也加入类路径 |
| `tool` | 构建工具、验证工具与脚本 | 只参与 `check` 与 `spotlessApply` |

登记会把组件作为包含构建引入，并把坐标 `components:<component>` 替换为该构建的根项目；直接用 `includeBuild` 包含的组件不会被当作 `product` 或 `tool`，L-08 会报告它。根项目的 `check` 与 `spotlessApply` 依赖每个包含构建的同名任务，因此组件的测试与格式检查随 `gradlew check` 运行。

项目组件的 `settings.gradle.kts` 应用 `io.github.recrivenvi.conventions.component`（L-08）：

```kotlin
pluginManagement {
    includeBuild("../../gradle/conventions")
}

plugins {
    id("io.github.recrivenvi.conventions.component")
}

rootProject.name = "runtime_safety"
```

该插件让组件使用仓库的 `gradle/libs.versions.toml` 与 Maven Central，并为组件根项目应用 `base` 与 Spotless：Kotlin DSL 用 ktfmt，常见文本、脚本、C/C++、Rust 与着色器源码做空白处理，应用 `java` 时 Java 用 google-java-format 并把 `--release` 默认设为各 Target 中最低的 Java 版本，测试使用 JUnit Platform。`build/`、`third_party/`、`node_modules/` 与 `dist/` 不参与格式化。

组件的构建内容写在它自己的 `build.gradle.kts` 中。不是 Java 写的工具，用 `Exec` 等任务调用自己的工具链，并让 `check` 依赖这些任务，例如运行 PowerShell 或 Python 测试、调用 clang-format 或 rustfmt 检查格式。工具优先选择跨平台的写法；只能在 Windows 上运行的工具在组件的说明文档中写明，项目的 `.github/workflows/check.yml` 相应使用 Windows 运行环境。

`product` 组件必须应用 `java` 或 `java-library`：Target 按 Java 运行时库解析它，从它的 JAR 中取得内容，只应用 `base` 的组件不提供这个 JAR。随模组发布、与游戏版本无关的非代码内容，例如光影包、资源包或动画数据，放在应用 `java` 的 `product` 组件的 `src/main/resources/` 中，随组件合并进每个 Target；需要单独发布的压缩包由组件的构建任务生成。

`product` 组件只合并自身的 JAR，不合并它依赖的第三方库。第三方库由 Target 以加载器原生方式内嵌，例如 NeoForge 与 Forge 的 `jarJar`、Fabric 的 `include`。组件的包名放在 `mod_group` 之下，避免与其他模组内嵌的同名包冲突。

## 原生组件

用 C、C++ 或 Rust 编写的原生库作为 `product` 组件：

| 内容 | 位置 |
| --- | --- |
| 原生源码与构建配置，如 `CMakeLists.txt`、`Cargo.toml` | `components/<component>/` |
| 第三方源码，包括 Git 子模块 | `components/<component>/third_party/`，子模块登记在根目录 `.gitmodules` |
| 原生库产物 | 组件 JAR 中的固定资源路径，例如 `natives/<os>-<arch>/`，由组件的 `jar` 任务从原生构建输出收集 |
| 加载原生库的 Java 代码 | 组件或 Target 的 `src/main/java/` |

`third_party/` 中的文件不参与合规检查、rumdl 与 Spotless，保持原样（C-09）。原生工具链（CMake、编译器、Cargo）由组件的 `build.gradle.kts` 调用；持续集成需要这些工具链时，在项目的 `.github/workflows/check.yml` 中安装。

## 根项目

`RootConventions` 应用 `base` 与 Spotless，并：

- 注册 `checkRepository`：依赖 `verifyCompliance`、`verifyConfiguration`、`lintMarkdown`、根项目与各 Target 的 `spotlessCheck`，以及所有包含构建的 `check`；它不编译 Target，也不要求本地输入，适合拿不到本地输入的持续集成；
- 让 `check` 依赖 `checkRepository` 与各 Target 的 `check`；`verifyInputs` 由 `configuration` 插件挂到 `check` 上；
- 让 `spotlessApply` 依赖所有包含构建的 `spotlessApply`；
- 让 `assemble` 依赖各 Target 的 `assemble`；
- 注册 `collectRelease`：构建全部 Target，把各 Target `build/libs/` 中当前版本的发行 JAR 同步到 `build/release/<version>/`，供发布时核对，见 [procedure-release](../development/procedure-release.md)；
- 注册 `verifyRelease`：依赖 `collectRelease` 与各 Target 的 `verifyRelease`，并确认 `build/release/<version>/` 中恰好有每个 Target 的一个发行 JAR；`.github/workflows/check.yml` 在 `check` 之后运行它；
- 检查根目录、`licenses/`、`documents/`、`validations/` 与 `.github/` 中文本文件的格式；验证的 `result/` 与 `local.toml` 除外。根目录、Target、组件与验证目录共用一份文本扩展名清单（`ComponentConventions.TEXT_EXTENSIONS`，包括 Java 构建脚本、C/C++、Rust、着色器与 `.lang`、`.mcfunction`、`.snbt`、access widener 等 Minecraft 格式），`spotlessApply` 修正这些文件；清单之外的文本文件仍由 C-01 至 C-04 报告，需要手工修正；
- 注册 `installRumdl`、`lintMarkdown` 与 `fixMarkdown`：`installRumdl` 按本机系统与处理器，把 rumdl 的 GitHub 发布包当作依赖解析，解压到 `build/tools/rumdl/`；发布包缓存在 Gradle 用户目录中，之后可以离线运行。Windows on Arm 使用 x86_64 版本，Linux 使用静态链接的 musl 版本。

## 格式

`spotlessApply` 按下表修正格式，并运行 rumdl 的自动修复；`check` 包含对应的检查（C-05、D-04）。

| 文件 | 工具与风格 |
| --- | --- |
| Java | google-java-format 的 AOSP 风格 |
| Kotlin DSL（`*.gradle.kts`） | ktfmt 的 kotlinlang 风格 |
| JSON 与 `pack.mcmeta` | Gson，缩进 4 个空格，以换行结束 |
| 其他文本文件 | 按 `.editorconfig` 去除行尾空白、把行首制表符换成空格并以换行结束 |
| Markdown | rumdl，规则配置在模板插件中的 `rumdl.toml` |

前四类由 Spotless 执行，作用范围见上文"根项目"与"项目组件"；模板插件自己的源码与规范附件由它自己构建中的 Spotless 处理。组件中的其他语言使用该语言的通用格式化工具，例如 clang-format 或 rustfmt，由组件自己的 `check` 检查。

## 产物

| 文件 | 内容 |
| --- | --- |
| `versions/<target>/build/libs/<mod_id>-<target>-<version>.jar` | 模组，包含合并进来的 `product` 组件与 `META-INF/` 中的许可文件；不包含 `probe` 源码集 |
| `versions/<target>/build/libs/<mod_id>-<target>-<version>-sources.jar` | Target 脚本开启源码 JAR 时生成，包含 `META-INF/` 中的许可文件；`collectRelease` 不收集它 |

1.20.1 Forge 与 1.x Fabric 在 `build/libs/` 中放重混淆或重映射后的发行 JAR，开发环境使用的 JAR 在 `build/devlibs/`。

## 配置缓存

模板的 [gradle.properties](../../../../gradle.properties) 默认启用配置缓存；项目自己的任务与它不兼容而需要关闭时，在 `gradle.properties` 中用注释写明不兼容的任务。所有配置文件都通过 Gradle 的文件内容提供器读取，修改、创建或删除它们都会使缓存失效。`validations/` 中有哪些验证由值来源 `ValidationDirectories` 列出，配置缓存每次构建都重新计算它，新增或删除验证目录同样会使缓存失效；Target 是否有 `src/probe/` 由 Gradle 记录的文件系统检查跟踪。
