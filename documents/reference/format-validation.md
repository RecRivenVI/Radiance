# 验证格式

项目的自动化验证适用于文件、构建产物、接口与游戏行为等对象，是否启动游戏由验证的目标决定。模板规定各部分的位置、验证目录的结构与命名，以及每项验证必须写明的工具、Target、命令与通过标准；需要启动游戏的验证由模板生成运行任务，见下文"验证运行"。模板不提供探针代码、验证逻辑或验证项目（I-03）。新增验证的步骤见 [procedure-add_validation](../development/procedure-add_validation.md)。

## 各部分的位置

| 部分 | 内容 | 位置 | 规则 |
| --- | --- | --- | --- |
| 验证工具 | 运行器、驱动、断言、报告与 Gradle 任务等与 Minecraft 无关的代码 | `tool` 组件 `components/<component>/` | L-04、L-08、V-05 |
| 游戏内代码 | 必须在游戏中运行、调用 Minecraft API 的代码 | `versions/<target>/src/probe/` | L-03、V-06 |
| 验证数据 | 一项验证的说明、配置、输入数据、实例与结果 | `validations/<type>-<topic>/` | V-01 至 V-04、V-07 至 V-10 |

验证工具是 `tool` 组件，在 `settings.gradle.kts` 的 `components {}` 中登记，它的测试与检查由组件的 `check` 执行，可以用任何语言编写，见 [procedure-add_component](../development/procedure-add_component.md)。工具负责准备输入、启动验证运行、分析结果与判定通过；`probe` 源码集与验证运行由模板配置，Target 的 `build.gradle.kts` 不写验证工具的配置（V-05）。工具与验证运行可以在 `validation.md` 的"运行"一节中依次执行；需要把它们合成一个任务时，工具以设置插件的形式提供：在 `settings.gradle.kts` 的 `pluginManagement` 中包含该组件并应用插件，由插件注册依赖验证运行的根项目任务。服务端与客户端要同时运行时，由工具分别启动两个进程。

## 命名

名称写作 `<type>-<topic>`，两个字段用 `-` 分隔：`<type>` 是一个小写英文词，`<topic>` 由小写英文单词或数字组成，组合词用 `_` 连接，如 `visual-title_screen`。类型词取自 [AGENTS.md](../../AGENTS.md) 第 9 节的 `validations` 建议词表；表外的词产生 V-02 提示，可以在 `settings.gradle.kts` 的 `compliance {}` 中登记补充词。

## 结构

| 位置 | 必需 | 内容 |
| --- | --- | --- |
| `validation.md` | 是 | 验证说明，见下文"验证说明" |
| `validation.toml` | 是 | 验证配置，见下文"验证配置" |
| `task/` | 否 | 验证工具读取的场景、夹具与其他输入数据，内部结构由项目定义 |
| `instance/` | 否 | 验证使用的临时实例，被 Git 忽略；验证运行使用 `instance/<target>/<角色>/`，其余结构由项目定义 |
| `result/` | 否 | 验证结果与证据，文件名、格式与内部结构由项目定义；保持验证工具写入的原样：不参与文本、格式化与 Markdown 规则，`.gitattributes` 让 Git 不转换其中的换行符；但不能包含代码文件（C-09、V-04） |

验证目录中只允许上述条目。`task`、`instance` 与 `result` 必须是目录；`validation.md` 与 `validation.toml` 必须是文件（V-03）。生成的实例与结果由验证任务产生（G-05）。验证只使用自己的 `instance/`，不读写日常实例（V-07）。

## 验证配置

`validation.toml` 顶层必须有以下两个键（V-08），其余键与表由项目定义，供验证工具读取：

| 键 | 类型 | 内容 |
| --- | --- | --- |
| `tool` | 字符串 | 执行这项验证的组件名称；必须是在 `settings.gradle.kts` 的 `components {}` 中用 `tool(...)` 登记的组件 |
| `targets` | 字符串数组 | 验证涉及的 Target 名称，必须已在 `targets {}` 中登记；只检查文件或构建产物、不涉及 Target 时写 `[]` |

```toml
tool = "<component>"
targets = ["<target>"]
```

`<component>` 为验证工具组件的名称，`<target>` 为 Target 名称。文件必须是合法的 TOML：插件在配置阶段读取每个 `validation.toml`，解析错误让构建失败。

需要启动游戏时，用 `[instances.<角色>]` 声明这项验证要启动的实例，见下文"验证运行"。`instances` 由插件读取，写错时构建在配置阶段失败（F-03）；其余键由项目定义。

## 验证说明

`validation.md` 遵守仓库通用的 Markdown 规则，并按项目的文档主语言包含三个二级标题（V-09），可以另加其他小节：

| 小节 | 英文名称 | 内容 |
| --- | --- | --- |
| 目标 | Goal | 这项验证证明什么，覆盖哪些行为 |
| 运行 | Run | 从仓库根目录执行这项验证的命令，必须放在代码块中；需要使用者参与的步骤也写在这里 |
| 通过标准 | Pass criteria | 能由命令结果、日志或 `result/` 中的文件判定的通过条件 |

改动涉及某项验证的 `tool`、`targets` 或"目标"所述的行为时，Agent 按"运行"一节执行它，按"通过标准"判断结果（V-10）。

## 验证运行

`validation.toml` 中的每个 `[instances.<角色>]` 表声明一个角色。插件为 `targets` 中每个已登记的 Target 与每个角色生成一个运行：

```toml
tool = "<component>"
targets = ["<target>"]

[instances.server]
memory-max = "2G"

[instances.client]
game-args = ["--quickPlayMultiplayer", "127.0.0.1:25565"]
```

| 项 | 规则 |
| --- | --- |
| 角色 | `client`、`client-multiplayer` 或 `server`，即内置的日常实例；`instances.toml` 中的额外实例不能作为角色 |
| 可写的键 | 与日常实例设置相同，取值规则与默认值见 [configuration-repository](configuration-repository.md)；空表表示全部使用默认值 |
| 不继承的设置 | 验证实例不读取 `instances.toml` 与 `local.toml` 中的实例设置，结果不随个人偏好变化；EULA 与本地输入的本机路径仍从 `local.toml` 读取 |
| 任务 | `:version:<target>:runValidation<验证名><角色>`，名称由验证目录名与角色名的各个单词首字母大写拼成，如 `smoke-launch` 的 `client-multiplayer` 角色为 `runValidationSmokeLaunchClientMultiplayer`；两项验证拼出相同名称时构建失败 |
| 实例目录 | `validations/<name>/instance/<target>/<角色>/`；首次运行的处理与日常实例相同：客户端跳过无障碍引导，服务端关闭在线验证与白名单，使用者接受 EULA 时写入 `eula.txt`，实例设置 `mods` 中的本地输入按相同规则同步进 `mods/` |
| 加载的模组 | 产品模组；Target 有 `src/probe/` 时还有探针模组 |
| IDE 运行配置 | 不生成；验证运行由 `validation.md` 的"运行"一节或验证工具启动 |

`targets` 为空或不存在时不能声明 `[instances]`；`targets` 中未登记的 Target 不生成运行，由 V-08 报告。没有 `[instances]` 的验证不生成运行。

1.20.1 Forge 的客户端用 `--quickPlayMultiplayer` 加入时偶尔停在登录界面，这是游戏自身的问题，见 [workflow-daily](../development/workflow-daily.md)；验证工具为加入设置超时，超时后重新启动客户端。

### 探针

Target 有 `src/probe/` 时，插件创建 `probe` 源码集，它可以调用 `main` 的代码，Fabric 还可以调用 `client` 的代码。探针是独立的模组，模组 ID 为 `<mod_id>_probe`：

| 加载器 | `src/probe/resources/` 中的元数据 |
| --- | --- |
| NeoForge | `META-INF/neoforge.mods.toml` |
| Forge | `META-INF/mods.toml`；产品有 `pack.mcmeta` 时探针也要有，否则 1.20.1 Forge 停在加载警告界面 |
| Fabric | `fabric.mod.json` |

模板的 Fabric Target 不依赖 Fabric API；探针需要游戏事件时用 Mixin 挂接，或由项目自行加入 Fabric API。

`src/main/resources/` 中有的元数据文件，`src/probe/resources/` 中缺少时构建在配置阶段失败。元数据中的占位符与产品相同，按 P-01 写在双引号字符串内，模组 ID 写作 `"${mod_id}_probe"`；探针对产品的依赖由项目声明。`templates/` 的展开同样适用于 `src/probe/templates/`。

`probe` 的类不进入任何 JAR，也不在日常实例与其他运行中加载（V-06）。`check` 编译 `probe` 源码集，探针的编译错误随 `gradlew check` 报告。

### 系统属性

验证运行把以下 JVM 系统属性传给游戏，探针据此读取输入与写入结果：

| 属性 | 值 |
| --- | --- |
| `validation.name` | 验证目录名 |
| `validation.target` | Target 名称 |
| `validation.role` | 角色名 |
| `validation.task` | `validations/<name>/task/` 的绝对路径，用 `/` 分隔 |
| `validation.result` | `validations/<name>/result/` 的绝对路径，用 `/` 分隔；目录由探针或验证工具创建 |

实例目录是游戏的工作目录。验证运行在游戏退出后结束；探针在得到结果后自行退出游戏，验证工具才能在无人值守时串联多个运行。

## 代码文件

除 `instance/` 外，验证目录中不能出现以下扩展名的文件，大小写不限（V-04）：

| 类别 | 扩展名 |
| --- | --- |
| 源码 | `c`、`cc`、`cpp`、`cs`、`cxx`、`go`、`h`、`hpp`、`java`、`kt`、`rs`、`scala`、`swift` |
| 脚本与构建脚本 | `bash`、`bat`、`cjs`、`cmd`、`cts`、`fish`、`gradle`、`groovy`、`js`、`jsx`、`kts`、`lua`、`mjs`、`mts`、`php`、`pl`、`ps1`、`psd1`、`psm1`、`py`、`rb`、`sh`、`ts`、`tsx`、`vbs`、`zsh` |
| 可执行与编译产物 | `class`、`dll`、`dylib`、`exe`、`jar`、`so`、`wasm` |

需要执行的逻辑写进验证工具组件或 `probe` 源码集，`task/` 中只放这些代码读取的数据。

## 检查边界

`verifyCompliance` 检查 V-01 至 V-04、V-08、V-09、L-03 中的 `probe` 源码集名称，以及适用于验证文件的通用文本和 Markdown 规则。它只读取 `validation.toml` 的 `tool` 与 `targets`；`[instances]` 由构建在配置阶段校验。`verifyCompliance` 不解析其他配置或结果，不判断验证是否通过，也不执行验证。V-05 至 V-07 与 V-10 由执行者遵守。验证方法、源码结构、其他配置内容、证据格式与验证工具的实现由项目决定。
