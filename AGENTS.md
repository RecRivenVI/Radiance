# 仓库规范

规范版本：1.2.1

本文件是采用本模板的仓库共用的规范，写给 Agent，也约束人类开发者；它与其他模板文件（G-08）随模板版本固定，项目内不修改。项目的信息与补充规则写在项目规范 [documents/AGENTS.md](documents/AGENTS.md)，它对整个仓库生效，只能补充本规范，不能放宽或改写。开始工作前读完这两份文件。

每条规则带编号和标记：

| 标记 | 含义 |
| --- | --- |
| [检查] | 由 `verifyCompliance` 机械检查，违反即构建失败 |
| [提示] | 由 `verifyCompliance` 检查，违反时给出警告；严格模式下视为失败 |
| [构建] | 由构建过程本身检查，如编译、格式检查、`verifyConfiguration`，违反即构建失败 |
| [判断] | 无法机械检查，由执行者负责遵守 |

检查结果按编号引用规则。机制说明见 `documents/design/`，配置定义见 `documents/reference/`，操作规程见 `documents/development/procedure-*.md`。

## 1. 总则

- **G-01** [判断] 只改动任务所需的范围，不改动无关的 Target、组件或文档。
- **G-02** [判断] 仓库只描述当前状态。不保留旧实现、兼容分支、注释掉的代码或"原先如何"的说明；需要追溯的内容由 Git 历史、决策记录和更新日志承担。为防止重复犯错而在注释中写明的修改原因除外（C-08）。
- **G-03** [判断] 每项事实只在一处定义，其他位置从该处取值（见第 4 节）。
- **G-04** [判断] 不代替使用者接受 Minecraft EULA：未经使用者明确授权，不写入或修改 `local.toml` 的 `eula` 键。
- **G-05** [判断] 不手工编辑生成内容：`build/`、`instances/`、`validations/*/instance/` 与 `validations/*/result/` 只由任务产生。
- **G-06** [检查] 被 `.gitignore` 忽略的文件不进入版本控制，不用 `git add -f` 绕过忽略规则。
- **G-07** [判断] 临时实验与探索脚本放在仓库之外。
- **G-08** [判断] 不修改模板文件：根目录的 `AGENTS.md`、`CLAUDE.md` 与 `.rumdl.toml`，`.github/workflows/specification.yml`，以及模板组件 `components/configuration`、`components/conventions` 与 `components/compliance`。它们只随模板版本一起更新，见 [procedure-upgrade_template](documents/development/procedure-upgrade_template.md)。
- **G-09** [检查] 仓库不跟踪 `jar`、`class`、`dll`、`so`、`dylib`、`exe` 文件，Gradle Wrapper 的 JAR 除外：构建产物由构建生成，仓库之外的文件按 F-05 作为本地输入登记。

## 2. 规范文件

- **S-01** [检查] 本文件开头以"规范版本：<版本>"声明规范版本，且与检查器实现的版本一致。
- **S-02** [检查] 本文件中标记为 [检查] 或 [提示] 的规则，与检查器实现的规则一一对应，标记与严重程度一致。
- **S-03** [检查] 第 9 节的建议词表与检查器内置的词表一致。
- **S-04** [检查] `CLAUDE.md` 可以省略；存在时内容只有 `@AGENTS.md` 与 `@documents/AGENTS.md` 两行。
- **S-05** [检查] 项目规范 `documents/AGENTS.md` 存在；其中项目信息表的产品显示名、模组 ID 与 Java 包和 `gradle.properties` 一致，文档主语言为"简体中文"或"English"，参考 Target 已登记，Target 表与 `settings.gradle.kts` 登记的 Target 及其 `target.properties` 一致。
- **S-06** [检查] 模板文件与检查器为当前规范版本记录的 SHA-256 清单一致，模板组件中没有清单之外的文件。
- **S-07** [检查] 根目录 `AGENTS.md` 与项目规范合计不超过 32 KiB，即 Codex 读取 AGENTS.md 的默认上限。

项目规范由以下小节组成：

| 小节 | 内容 |
| --- | --- |
| 项目信息 | 两列表格，列名为"项"与"值"，行为产品显示名、模组 ID、Java 包、入口类、业务、文档主语言、首页语言与参考 Target |
| Target | 三列表格，列名为"Target"、"加载器版本"与"Java" |
| 补充规则 | 项目自己的规则，编号写作 `X-<两位数字>`，标记只用 [判断] |

## 3. 目录

| 位置 | 内容 |
| --- | --- |
| `versions/<target>/` | 一个 Minecraft 版本与加载器组合的模组源码根 |
| `components/<component>/` | 组件，每个都是独立的 Gradle 构建；项目组件在 `components {}` 中登记为 `product`（随模组发布）或 `tool`（构建与验证工具），`configuration`、`conventions`、`compliance` 是模板组件 |
| `instances/<target>/<variant>/` | 日常运行实例 |
| `licenses/` | 第三方许可原文 |
| `validations/<type>-<topic>/` | 一项验证的说明、配置、输入数据、实例与结果 |
| `documents/<category>/` | 项目文档，见第 9 节；`documents/AGENTS.md` 是项目规范 |
| `gradle/` | Wrapper、`libs.versions.toml` 与守护进程 JVM 要求 |
| `.github/workflows/` | 持续集成工作流；`specification.yml` 是模板文件 |

- **L-01** [检查] 根目录只允许：`AGENTS.md`、`CLAUDE.md`、`README.md`、`README-<区域代号>.md`、`NOTICE`、`LICENSE`（可带 `.md` 或 `.txt`）、`CONTRIBUTING.md`、`SECURITY.md`、`CODE_OF_CONDUCT.md`、`COPYING`、`COPYING.LESSER`、`.gitattributes`、`.gitmodules`、`.editorconfig`、`.gitignore`、`.rumdl.toml`、`settings.gradle.kts`、`build.gradle.kts`、`gradle.properties`、`inputs.toml`、`instances.toml`、`local.toml`、`gradlew`、`gradlew.bat`，以及上表中的目录。
- **L-02** [检查] `versions/` 下只有在 `settings.gradle.kts` 中登记的 Target 目录。
- **L-03** [检查] Target 目录只包含 `build.gradle.kts`、`target.properties` 与 `src/`。`src/` 下只有 `main`、`test`、`probe`、加载器的原生目录（Fabric 为 `client`、`datagen`、`gametest`，NeoForge 与 Forge 为 `generated`），以及在 `compliance {}` 中用 `sourceSets(...)` 登记、并在项目规范中写明用途的补充源码集。
- **L-04** [检查] `components/` 下只有组件目录，名称由小写英文单词组成并表达职责，多个词用 `_` 连接，不使用 `common`、`misc`、`shared`、`util`、`utils`、`helper`、`helpers`。
- **L-05** [检查] `.gitignore` 包含 `/local.toml`、`.gradle/`、`build/`、`/instances/**`、`!/instances/**/` 与 `/validations/*/instance/`。
- **L-06** [判断] 新增内容按下表放置。

| 内容 | 位置 |
| --- | --- |
| 产品代码与资源 | `versions/<target>/src/main/`；Fabric 的客户端部分在 `src/client/` |
| 编译前展开项目事实的 Java 源码 | `src/main/templates/` |
| 数据生成 | NeoForge 与 Forge 的产物在 `src/generated/resources/`；Fabric 的代码在 `src/datagen/`，产物在 `src/main/generated/` |
| 与游戏运行无关的单元测试 | `src/test/` |
| 必须在游戏中运行的验证代码 | `src/probe/`；Fabric 原生游戏测试在 `src/gametest/` |
| 随模组发布、与 Minecraft 无关的代码、原生库与资源 | `product` 组件 |
| 构建工具、验证工具与脚本 | `tool` 组件 |
| 组件使用的第三方源码与 Git 子模块 | `components/<component>/third_party/` |
| 随模组发布的第三方许可原文 | `licenses/` |
| 闭源模组、兄弟项目产物等仓库之外的文件 | 不进仓库，按 F-05 登记 |
| 验证的说明、配置、输入数据与结果 | `validations/<type>-<topic>/` |
| 项目信息与补充规则 | `documents/AGENTS.md` |
| 文档，包括组件的说明 | `documents/<category>/` |

- **L-07** [检查] Target 中 `src/<源码集>/java/` 与 `src/<源码集>/templates/` 下的 Java 源码都位于 `mod_group` 对应的包目录或其子目录中。
- **L-08** [检查] 每个组件都有 `settings.gradle.kts` 与 `build.gradle.kts` 并被包含进构建；模板组件以外的组件在 `components {}` 中登记，并在 `settings.gradle.kts` 中应用 `io.github.recrivenvi.component` 插件。
- **L-09** [检查] `licenses/` 中的文件直接放在该目录下，命名为 `<来源>-<许可>.txt` 或 `.md`，两个字段的写法同 D-02 的 `<topic>`，许可字段可以含点号，如 `glfw-zlib.txt`、`gson-apache_2.0.txt`。
- **L-10** [检查] `.github/` 中只有 `workflows/<name>.yml`、`ISSUE_TEMPLATE/<name>.md` 或 `.yml`、`PULL_REQUEST_TEMPLATE.md`、`CODEOWNERS`、`FUNDING.yml` 与 `dependabot.yml`；`<name>` 的写法同 D-02 的 `<topic>`。

## 4. 事实与配置

| 事实 | 唯一来源 |
| --- | --- |
| 模组身份、版本、运行端、作者、许可、描述 | `gradle.properties` 的 `mod_*` 键 |
| Minecraft 版本与加载器 | Target 目录名 `<minecraft>-<loader>` |
| 加载器版本、Java 版本、随 Target 变化的依赖版本及其他 Target 事实 | `versions/<target>/target.properties` |
| 构建工具、插件与各 Target 共用的依赖版本 | `gradle/libs.versions.toml` |
| 组件及其用途 | `settings.gradle.kts` 的 `components {}` |
| 本地输入的说明、来源与允许的哈希 | `inputs.toml`，本机路径写在 `local.toml` 的 `[inputs]` |
| 日常实例预设 | `instances.toml`，本机覆盖写在 `local.toml` |
| 验证运行的实例设置 | `validation.toml` 的 `[instances]` |
| Minecraft EULA 同意 | `local.toml` 的 `eula` |
| 合规检查的项目设置 | `settings.gradle.kts` 的 `compliance {}` |

- **F-01** [构建] `gradle.properties` 只包含 `mod_*` 项目事实与 `org.gradle.*` 设置，必需的事实齐全且取值合法。
- **F-02** [构建] `target.properties` 只包含 Target 事实，必须有 `loader_version` 与 `java_version`，不写由目录名推导的 `target`、`minecraft_version`、`loader`；可选的 `mappings`、`yarn_version` 与 `loader_minimum` 见 configuration-repository。
- **F-03** [构建] `inputs.toml`、`instances.toml`、`local.toml` 与 `validation.toml` 的 `[instances]` 只包含已定义的键，所有写出的取值都合法；`jvm-args` 与 `game-args` 不重复设置其他实例设置项负责的参数。
- **F-04** [判断] 构建脚本与源码模板从上表的来源取值，不重复书写版本号或模组 ID：Target 的构建脚本用 `target.fact("<key>")` 读取事实，用 `target.module("<group>:<name>", "<key>")` 声明随 Target 变化的依赖，用 `target.input("<name>")` 引用本地输入；加载器元数据由 P-04 检查。
- **F-05** [判断] 仓库之外的文件只作为本地输入使用：在 `inputs.toml` 中写明说明、来源与允许的 SHA-256，在 `local.toml` 中写本机路径，构建脚本与实例配置只按名称引用，不写死本机路径，不引用仓库之外的目录。名称与版本取自模组元数据（由 `verifyInputs` 核对）或上游发布，不自拟。

全部键与取值规则见 [configuration-repository](documents/reference/configuration-repository.md)。

## 5. Target

- **T-01** [构建] Target 名称为 `<minecraft>-<loader>`，加载器取 `neoforge`、`forge` 或 `fabric`，并在 `settings.gradle.kts` 的 `targets {}` 中登记。
- **T-02** [检查] Target 的 `build.gradle.kts` 不写任何版本号，依赖的版本按 F-04 取值。
- **T-03** [构建] Target 的 `build.gradle.kts` 应用与加载器匹配的插件：`neoforge` 用 `net.neoforged.moddev`，`forge` 用 `net.neoforged.moddev.legacyforge`，`fabric` 在 26.1 及以后用 `net.fabricmc.fabric-loom`，在混淆的 1.x 版本用 `net.fabricmc.fabric-loom-remap`。其余构建配置由 `conventions` 插件提供，脚本中只写该 Target 独有的原生配置。
- **T-04** [判断] 源码、资源与代码风格遵循对应加载器生态的惯例。Target 之间不共享 Minecraft 相关源码，不使用预处理器或条件编译。
- **T-05** [判断] 业务改动先在项目规范指定的参考 Target 上完成并在游戏中确认，再逐个移植到其他 Target；版本或加载器之间的 API 差异记录在 `documents/development/porting-<topic>.md`。
- **T-06** [判断] 加载器选择支持该 Minecraft 版本的最新版本：Forge 不按推荐版本选择，NeoForge 包括 Beta。具体版本固定在 `target.properties`，不使用动态版本。
- **T-07** [提示] 多个 Target 的产品源码（`main` 与 Fabric 的 `client` 源码集）中内容相同、且不引用 Minecraft 与加载器的 Java 源码，移入 `product` 组件，各 Target 不各留一份。

## 6. 产品

- **P-01** [检查] 加载器元数据文件（`META-INF/mods.toml`、`META-INF/neoforge.mods.toml`、`fabric.mod.json`、`pack.mcmeta`）中的 `${...}` 占位符只写在双引号字符串内；`${mod_id}` 另可用于 TOML 表名。
- **P-02** [判断] `mod_side` 决定产品模组的运行端：`both` 两端都必须安装，`client` 仅客户端、可加入未安装本模组的服务器，`server` 仅服务端、原版客户端可以加入。限定运行端的代码按加载器原生方式组织：Fabric 放在 `client` 源码集，NeoForge 与 Forge 用 `Dist` 区分。切换步骤见 [procedure-switch_side](documents/development/procedure-switch_side.md)。
- **P-03** [检查] 每个 Target 的 `src/main/resources/` 只有与加载器对应的元数据文件：`forge` 为 `META-INF/mods.toml`，`neoforge` 为 `META-INF/neoforge.mods.toml`，`fabric` 为 `fabric.mod.json`。
- **P-04** [检查] 加载器元数据中的模组身份、版本、作者、许可、描述、Forge 与 Fabric 的运行端、入口类所在的包，以及对 Minecraft、加载器与 Java 的版本要求，都用占位符从第 4 节的来源取值；字段与占位符的对应见 [format-metadata](documents/reference/format-metadata.md)。
- **P-05** [检查] Target 的资源与数据生成产物中，`assets/` 与 `data/` 下的路径符合 Minecraft 资源位置规则：只用小写英文字母、数字、`_`、`-`、`.` 与目录分隔符。
- **P-06** [提示] 由模板创建的项目不沿用模板自身的模组 ID `ravens_mod_template` 与 Java 包 `io.github.recrivenvi.modtemplate`；仓库的 Git 远程 `origin` 指向模板仓库时除外。
- **P-07** [提示] 模板文件、`NOTICE` 与 `licenses/` 之外的文件不出现模板自身的名称 `ravens_mod_template`、`modtemplate` 与 `Raven's Mod Template`（不区分大小写）；仓库的 Git 远程 `origin` 指向模板仓库时除外。

## 7. 运行与测试

内置的日常实例为 `client`（单人客户端）、`client-multiplayer`（第二名玩家）与 `server`（独立服务端），由 `runClient`、`runClientMultiplayer`、`runServer` 启动，目录为 `instances/<target>/<实例>/`。项目可以在 `instances.toml` 的 `[variants]` 中登记以它们为基础的更多实例。

- **I-01** [判断] 日常实例始终加载产品模组，内容持久保留。只在某个实例中使用的测试模组用实例设置 `mods` 按本地输入名称引用。个人偏好写在 `local.toml`，共享预设写在 `instances.toml`。
- **I-02** [判断] 独立服务端需要使用者在 `local.toml` 中写入 `eula = true`；首次运行时插件写入 `online-mode=false` 与 `white-list=false`，已存在的文件不会被覆盖。
- **I-03** [判断] 模板不提供探针代码与验证逻辑，只按第 8 节为验证生成运行；验证由项目自行设计。
- **I-04** [检查] `instances/` 可以不存在；存在时只包含已登记 Target 的目录，每个 Target 目录中只有已登记实例的目录。实例内部的内容不检查。
- **I-05** [判断] 日常实例与验证运行之外的运行，如数据生成与游戏测试，由插件放在 `versions/<target>/build/run/<运行名>/`，不另设目录。

## 8. 验证

自动化验证可以检查文件、构建产物、接口或游戏行为，按 L-06 分三处放置：工具在 `tool` 组件，游戏内代码在 `src/probe/`，数据在 `validations/`。本规范规定位置、命名与每项验证必须写明的内容，验证方法与证据格式由项目决定。`validation.toml` 用 `[instances.<角色>]` 声明要启动的游戏时，插件为每个 Target 与角色生成同时加载探针的运行任务，见 [format-validation](documents/reference/format-validation.md)。

- **V-01** [检查] `validations/` 下只有验证目录，名称为 `<type>-<topic>`，写法同 D-02。
- **V-02** [提示] `<type>` 取自第 9 节 `validations` 一行的建议词；补充词在 `settings.gradle.kts` 的 `compliance {}` 中登记。
- **V-03** [检查] 验证目录只包含必需的 `validation.md` 与 `validation.toml`，以及可选的目录 `task/`、`instance/`（被 Git 忽略）与 `result/`；各条目的用途见 format-validation。
- **V-04** [检查] 验证目录中除 `instance/` 外不包含源码、脚本或可执行文件，扩展名清单见 [format-validation](documents/reference/format-validation.md)。
- **V-05** [判断] 验证工具的测试与检查由所在组件的 `check` 任务执行，随 `gradlew check` 一起运行；Target 的 `build.gradle.kts` 不为验证工具写配置。
- **V-06** [判断] `probe` 源码集只放必须在游戏中运行的验证代码，构成独立的模组 `<mod_id>_probe`，元数据文件与产品相同、由项目编写。它可以依赖 `main`，`main` 不依赖它；它只在验证运行中加载，不进入产品构件。
- **V-07** [判断] 验证只使用自己的 `instance/`，不读写 `instances/` 下的日常实例。
- **V-08** [检查] `validation.toml` 顶层的 `tool` 是执行这项验证、已在 `components {}` 中登记的 `tool` 组件；`targets` 是验证涉及的已登记 Target 名称数组，不涉及 Target 时写空数组。`[instances]` 由插件读取（F-03），其余键由项目定义。
- **V-09** [检查] `validation.md` 包含"目标、运行、通过标准"三节，"运行"一节用代码块给出从仓库根目录执行这项验证的命令；英文名称见 format-document。
- **V-10** [判断] 新增验证按 [procedure-add_validation](documents/development/procedure-add_validation.md) 执行。改动涉及某项验证的 `tool`、`targets` 或"目标"所述的行为时，任务完成前按它的"运行"一节执行，按"通过标准"判断结果，并如实告诉使用者（W-02）。

## 9. 文档

`documents/` 按用途分为七类。读者由分类的默认写法与文件名中的类型词体现，文件名不写读者。

| 位置 | 用途 | 默认写法 | 建议类型词 |
| --- | --- | --- | --- |
| `documents/usage` | 安装、配置、玩法与问题处理 | 玩家 | `installation`、`configuration`、`guide`、`feature`、`command`、`compatibility`、`troubleshooting`、`faq`、`limitation` |
| `documents/development` | 开发环境、构建、测试、移植与操作规程 | 开发者；`procedure` 为 Agent | `setup`、`build`、`workflow`、`testing`、`debugging`、`porting`、`contribution`、`compatibility`、`procedure` |
| `documents/design` | 概念、架构、机制与决策记录 | 开发者 | `overview`、`concept`、`principle`、`architecture`、`mechanism`、`flow`、`lifecycle`、`security` |
| `documents/project` | 范围、计划、状态、待办、交接与阶段记录 | 开发者；`plan` 与 `handoff` 为 Agent | `overview`、`scope`、`proposal`、`roadmap`、`milestone`、`plan`、`status`、`backlog`、`debt`、`handoff` |
| `documents/reference` | 接口、配置、格式、依赖与术语的精确定义 | 开发者 | `api`、`protocol`、`schema`、`registry`、`command`、`configuration`、`event`、`format`、`version`、`dependency`、`upstream`、`glossary`、`example` |
| `documents/research` | 调研、审计、实验与故障分析，记录某一时刻的发现 | 开发者 | `survey`、`investigation`、`audit`、`review`、`experiment`、`incident` |
| `documents/release` | 发布、变更、升级与迁移 | 玩家 | `release`、`changelog`、`upgrade`、`migration`、`support`、`deprecation` |
| `validations` | 验证目录的类型 | 开发者 | `smoke`、`feature`、`visual`、`regression`、`integration`、`compatibility`、`parity`、`conformance`、`performance`、`stability`、`migration`、`security` |

每个词的用途、使用时机，以及不同分类中同名或相近的词如何区分，见 [glossary-vocabulary](documents/reference/glossary-vocabulary.md)。

- **D-01** [检查] `documents/` 下只有上表的七个分类目录与项目规范 `AGENTS.md`；文档直接放在分类目录中，图片放在分类目录的 `images/` 中。
- **D-02** [检查] 文档命名为 `<type>-<topic>.md`；决策记录为 `design/adr-<number>-<topic>.md`；阶段记录为 `project/phase-<number>-<topic>.md`；译文在名称末尾加区域代号，如 `guide-first_machine-en_US.md`。`<type>` 是一个小写英文词；`<topic>` 由小写英文单词或数字组成，组合词用 `_` 连接；区域代号写作 `<语言>_<地区>`，如 `zh_CN`；`<number>` 为不带前导零的正整数，可用点号表示子编号，如 `1.1`，同类记录的编号不重复，每一级从 1 开始连续。
- **D-03** [提示] `<type>` 取自该分类的建议词。确需新词时，在 `settings.gradle.kts` 的 `compliance {}` 中登记，见 [configuration-repository](documents/reference/configuration-repository.md)。
- **D-04** [构建] Markdown 文件通过 rumdl 检查，规则配置在根目录 `.rumdl.toml`：第一行是全文唯一的一级标题，标题层级不跳级，代码块标明语言，不出现裸网址，以及 rumdl 默认启用的其他规则；`CLAUDE.md`、`third_party/` 与验证目录 `result/` 中的文件除外。
- **D-05** [检查] Markdown 中的相对链接指向存在的文件。
- **D-06** [检查] `procedure`、`plan`、`phase`、`adr` 与 `research` 分类的文档包含规定的固定小节，决策记录的状态只用规定的写法，`已被 adr-<number> 取代` 的取代者必须存在；小节与状态的中英文名称见 [format-document](documents/reference/format-document.md)。
- **D-07** [提示] 玩家写法的文档不出现仓库路径与构建命令。
- **D-08** [检查] 图片命名为 `<所属文档名>-<编号>.<扩展名>`，扩展名为 `png`、`jpg`、`jpeg`、`gif`、`webp` 或 `svg`；`<编号>` 为不带前导零的正整数，每篇文档从 1 开始连续编号。所属文档按编号顺序引用每张图片，替代文本以"<图号标签> <编号>"开头，图号标签见 format-document。
- **D-09** [判断] 按分类的默认写法撰写：玩家写法使用游戏内的名称并注明适用版本；开发者写法先写结论，命令放在代码块中；Agent 写法一步一行，占位符首次出现时说明，验收能由命令结果或文件状态判定。各写法的完整要求见 [format-document](documents/reference/format-document.md)。
- **D-10** [判断] 文档使用项目规范规定的主语言，代码标识、命令、路径与游戏内的英文名称保持原文；标题只用于真实的层级，段落简短，列表只用于并列项或步骤。文档描述当前状态，过时内容直接修改或删除；只有决策记录、阶段记录、研究记录、更新日志与移植文档记录历史：研究记录事后不改写，结论被采纳时写进决策记录或相应文档；被取代的决策记录保留原文，在"状态"小节写明取代者。
- **D-11** [判断] `README.md` 是仓库首页，只放简介、Target 列表、快速开始与文档入口，使用项目规范规定的首页语言。其他语言版本命名为 `README-<区域代号>.md`，区域代号写法同 D-02，如 `README-zh_CN.md`；各版本内容一致。
- **D-12** [检查] `README.md` 与各语言版本在一级标题后的第一段互相链接；其中的 Target 表与登记的 Target 及其加载器版本、Java 版本一致。
- **D-13** [检查] 每篇译文都有同一分类中的原文，二级标题数量与原文相同。
- **D-14** [检查] 更新日志按 Keep a Changelog 编写：二级标题为 `[未发布]` 或 `[<版本>] - <YYYY-MM-DD>`，版本号符合语义化版本，`[未发布]` 只能在最前，其余按版本从新到旧排列；三级标题只用"新增、变更、弃用、移除、修复、安全"。
- **D-15** [检查] 玩家写法的文档在一级标题后的第一段写出至少一个已登记 Target 的 Minecraft 版本号。
- **D-16** [检查] `research` 分类的文档在一级标题后的第一段写出调查日期，格式为 `YYYY-MM-DD`。

## 10. 代码与格式

- **C-01** [检查] 文本文件使用 UTF-8 编码，不带字节顺序标记。
- **C-02** [检查] 文本文件使用 LF 换行；`*.bat` 与 `*.cmd` 使用 CRLF，因为 cmd.exe 读取 LF 换行的批处理文件会出错。
- **C-03** [检查] 文本文件以一个换行结束。
- **C-04** [检查] 文本文件不含行尾空白与制表符。
- **C-05** [构建] 格式由 Spotless 与 rumdl 统一，提交前执行 `gradlew spotlessApply` 自动修正；各类文件使用的工具与风格见 [architecture-build](documents/design/architecture-build.md)。组件中的其他语言使用该语言的通用格式化工具，如 clang-format、rustfmt，由组件的 `check` 任务检查。
- **C-06** [检查] `gradle-wrapper.properties` 固定 `distributionSha256Sum`；`gradlew` 在 Git 中具有可执行权限。
- **C-07** [判断] properties 文件写作 `key=value`，TOML 文件写作 `key = value`；相关键连续排列，不同组之间空一行。
- **C-08** [判断] 注释只说明代码或配置本身无法表达的约束、原因或用法，不保留注释掉的代码。为防止重复犯错，可以在注释中写明必要的历史修改原因。
- **C-09** [判断] 第三方与工具生成的文件保持原样：`gradlew`、`gradlew.bat`、`gradle/wrapper/`、`gradle/gradle-daemon-jvm.properties`、组件 `third_party/` 中的源码，以及验证工具写入 `validations/*/result/` 的结果与证据；后两者不参与 C-01 至 C-05、D-04 与 D-05 的检查与格式化，V-04 仍然适用；许可说明见 [NOTICE](NOTICE)。

## 11. 协作流程

- **W-01** [判断] 代码改动与下表中的同步项在同一次变更中完成。

| 变更 | 需要同步 |
| --- | --- |
| 新增或移除 Target | `settings.gradle.kts`、项目规范的 Target 表、各语言的 `README`、玩家安装文档 |
| 升级加载器或构建工具版本 | 对应事实来源、项目规范的 Target 表、各语言的 `README` |
| 新增或升级依赖的模组与库 | `target.properties` 或 `gradle/libs.versions.toml`、加载器元数据中的依赖声明、`documents/reference/dependency-*.md` |
| 新增或移除组件 | `settings.gradle.kts` 的 `components {}`、组件的说明文档 |
| 新增或更换本地输入 | `inputs.toml`、`documents/reference/dependency-*.md` |
| 修改配置键、构建插件或运行方式 | `components/` 中的测试、`documents/reference/` 与 `documents/design/` 的相应文档 |
| 修改玩家可见的行为 | `documents/usage/`、`documents/release/` 的更新日志 |
| 发布产品的新版本 | `gradle.properties` 的 `mod_version`、更新日志，步骤见 [procedure-release](documents/development/procedure-release.md) |
| 发布新的模板版本 | 模板文件、检查器的规则实现、测试与哈希清单；规范版本按变化程度提升 |

- **W-02** [判断] 任务完成时：`gradlew spotlessApply` 后 `gradlew check` 成功，改动影响发行 JAR 时 `gradlew verifyRelease` 也成功；行为变化已通过相应验证，涉及游戏运行的行为已在游戏中确认；同步项已完成；向使用者如实说明已验证与未验证的内容及原因。
- **W-03** [判断] 每次推送与拉取请求由 `.github/workflows/check.yml` 运行 `gradlew check verifyRelease`，由 `.github/workflows/specification.yml` 对照模板发布版本复核模板文件。持续集成拿不到本地输入时，`check.yml` 改为运行 `gradlew checkRepository`，其余检查在本机执行。

常用命令见 [workflow-daily](documents/development/workflow-daily.md)。
