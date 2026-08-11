# Radiance 配置指南

当前实际值与来源：根 instance.properties 保留 RecRivenVI 和 2560×1440，未新增堆默认；local.properties 可选且 ignored。五个验证 run 都按 client Cascade 消费这些偏好，但工作目录继续来自各自场景。`printInstanceConfiguration` 是通用实例配置查询，实际 ModDev 场景位置用 `:version:1.21.1-neoforge:inspectRunConfiguration` 核对。场景 schema 3 不再保存玩家名、分辨率或普通追加参数；隔离目录、Zume 配置位置和固定模组是场景输入。

下文通用 API 来自本轮冻结的真实模板。核心解析器和原始测试未重写；项目接线与工程验证见[工程记录](migration-template-configuration.md)。

## 通用配置 API

配置实现位于 `gradle/configuration/`，通过根 settings 的 included build 加载本地插件 `raven.repository-configuration`。它是构建基础设施，不是 Minecraft Target 或产品组件。根 build 只应用插件、`base` 并聚合配置测试。

## 配置权威与文件格式

| 文件 | 职责 | 本版支持 |
| --- | --- | --- |
| `gradle.properties` | Project Facts | 必填 `mod_id`、`mod_name`、`mod_group`、`mod_version`、`mod_environment`；其他项目事实使用 `mod_*` |
| `versions/<target>/target.properties` | Target Facts | 必填 `minecraft_version`、`loader`、`loader_version`、`java_version`；其他 Target 事实使用小写字母、数字与下划线 |
| `instance.properties` | 共享运行默认值 | 四级 `instance.*`，支持基础属性与额外的扁平运行属性 |
| `local.properties` | 本机工具路径与实例覆盖 | 可选；四级 `instance.*`、`java.<major>.home`、`launcher.prism.path` |

显式读取上述文件作为权威，不通过 `-Pmod_version=...` 或用户级 Gradle 配置另建产品事实覆盖层。根 gradle.properties 可保留 org.gradle.* 原生 JVM/性能选项，由 Gradle 按原生优先级读取；解析器将它们与 Project Facts 分离，不从 repositoryConfig.projectFacts 返回，也不参与实例 Cascade。org.gradle.jvmargs 控制构建 JVM，不作为 Minecraft 实例内存或追加参数。

文件采用 UTF-8、Java properties 语法；支持注释、转义与续行，重复键报错。Windows 路径推荐使用 `/`，例如 `C:/Tools/jdk-21`，避免 properties 与 JSON 双重反斜杠转义。相对本机路径相对于仓库根。

Target 文件中的版本和 Loader 必须与目录 ID 一致。Project Facts 不能放进 Target/local/instance 文件，Target Facts 和实例内存等不能混在一起；不引入 `.env`、其他 local 文件或 per-instance 配置权威。

产品验证的固定身份和隔离工作目录属于 `task/` 的场景约束，不增加第五级 Cascade。身份由同一场景输入供客户端、权限名单和断言消费；若解析出的 `playerName` 已设置且不同，启动前报错。未设置或相同时保留场景身份并只生成一次参数，不要求改变日常配置。普通运行偏好仍走统一 Cascade，不能用场景约束绕过覆盖规则。

## 四级 Cascade 与两个文件

对于每个 `(target, variant, property)`，在每个文件内按以下顺序选择第一个存在的键：

```properties
instance.[<target>].<variant>.<property>
instance.[<target>].<property>
instance.<variant>.<property>
instance.<property>
```

先在 `instance.properties` 得到共享默认值，再在 `local.properties` 独立求值；只要本机存在匹配键，就使用本机值。不能先合并文件再按具体程度搜索。

例如仓库的 `instance.[26.2-neoforge].client.memory=12G` 会被本机的 `instance.client.memory=8G` 覆盖为 `8G`。Target ID 带点号，必须整体放在方括号中。

所有属性共用这条解析路径。键存在且值为空仍算显式覆盖；空 extra args 清除共享追加参数，空内存等无效数值报错，不偷偷回退。

## 基础运行属性

| 属性 | 含义 |
| --- | --- |
| `memory` | 游戏 JVM 最大堆，正整数加 `K` / `M` / `G`，不配置 Gradle daemon |
| `width`、`height` | 客户端窗口尺寸，正整数像素 |
| `playerName` | 客户端开发名，3–16 个 ASCII 字母、数字或下划线 |
| `extraJvmArgs` | 多行参数列表，兼容单行 JSON；追加 JVM 参数 |
| `extraGameArgs` | 多行参数列表，兼容单行 JSON；追加游戏/服务端参数 |

没有匹配的可选属性时保留生态默认。server 不消费 width/height/playerName；两个客户端分别解析自己的玩家名。默认值以根 `instance.properties` 为准。

```properties
instance.memory=4G
instance.extraJvmArgs=[
    -Xms1G
    -Xmx8G
    -Ddemo=hello world
    -ea
]
instance.extraGameArgs=[
    --demo
    two words
]
```

多行块每个非空行是一个完整参数，去掉首尾空白但保留行内空格、反斜杠和引号。开始行的值只有 `[`，结束行只有 `]`，不需要逗号或包裹引号；注释放在块外，块内不解释注释、转义或续行。不经过 shell、环境变量替换或再次按空格拆分。空字段、`[]` 和空块都表示不追加。兼容原单行 JSON 数组，仍可表达空字符串参数；其转义继续遵循 properties 和 JSON 两层语法。选择最终列表后只追加一次，不合并不同作用域或文件的候选列表。

最终顺序：生态原有参数 → 项目必要参数 → 选中的实例追加参数。列表中的 `-Xmx` 优先于选中的 `instance.memory`；无 `-Xmx` 时使用 memory。先完成各自的文件/作用域 Cascade，再应用此规则，因此共享列表中的 `-Xmx` 也优先于本机 memory；本机空列表可清除共享参数并恢复 memory。重复或无效 `-Xmx` 报错。`-Xms` 等其他参数交给 JVM 解释，不保证任意 GC 参数组合兼容所有 Java 版本。游戏 `--width`、`--height`、`--username`、`--gameDir` 仍使用专门属性和目录模型。

## Profile 与本机工具

`mod_environment` 只允许 `client`、`server`、`both`。`deployment(variant)` 返回通常的部署建议：

| Profile | client | client-multiplayer | server |
| --- | --- | --- | --- |
| client | INSTALL | INSTALL | ABSENT |
| server | PROJECT_DEFINED | ABSENT | INSTALL |
| both | INSTALL | INSTALL | INSTALL |

这不是自动 Mod 部署器，也不是双方必须安装的网络合同。client 项目仍应验证装 Mod 的客户端连接未装该 Mod 的服务器；server 项目反向验证；both 覆盖单人、Dedicated Multiplayer 与两端一致性。

`java.<major>.home` 指向对应 JDK 根目录，使用时检查 `bin/java`、`bin/javac` 和 `release` 中的 Java 版本。没有本机路径时，JavaExec 适配器使用 Gradle 原生 toolchain 选择 Target 所需版本。`launcher.prism.path` 提供可查询的 Launcher 可执行文件路径，显式配置校验检查其存在；插件不启动 Launcher。

这些配置不更换已经启动的 Gradle JVM，也不安装工具。Target 的编译 toolchain 仍应从 `java_version` 配置在原生 Java/Loader DSL 中；本机路径可以通过 `javaHome(major)` 供需要该路径的工具接入使用。

## Target 的 Kotlin DSL 接入

先创建真实 Target 的 build 和 facts，再在根 settings 调用 `includeTarget(...)`。helper 只做显式注册与目录映射，拒绝缺失或重复模块；不扫描目录。通用模板初始没有 Target；Radiance 的实际五节点以根 settings 为准，只有 NeoForge 1.21.1 是产品实现。

Target 构建脚本可以读取根扩展：

```kotlin
import raven.gradle.RepositoryConfiguration

val config = rootProject.extensions.getByType<RepositoryConfiguration>()
val target = project.name
val projectFacts = config.projectFacts
val targetFacts = config.targetFacts(target)
val environment = config.environment
val javaVersion = config.javaVersion(target)

// 在对应 Loader 的原生运行配置中使用这些值。
val client = config.resolveInstance(target, "client")
// client.memory(), width(), height(), playerName(), extraJvmArgs(), extraGameArgs()
// 最终堆：client.effectiveMemory()；追加：client.jvmArgumentsWithoutMaxHeap()
// config.value(target, "client", "memory") 可查询最终值的 file/key/raw 来源。
```

Project group/version 已提供给根项目与正式 Target。Loader metadata、依赖、编译 toolchain 和运行 DSL 从这里消费事实，不在根 build 塞入 Loader-specific 分支。

Loader 适配器必须用 `effectiveMemory()` 设置原生最大堆属性，并只追加 `jvmArgumentsWithoutMaxHeap()`，保证最终一个 `-Xmx`。`memory()` 是专用属性的解析值，`extraJvmArgs()` 是未投影的完整选中列表，不能把它们直接同时用于启动。诊断命令额外显示 `effectiveMemory`，原属性及列表的来源仍可查询。

对于普通 JavaExec，提供可选的真实运行适配器：

```kotlin
import raven.gradle.JavaExecAdapter

// 在已有 JavaExec 任务的配置块内调用一次；主类与 classpath 仍由原生构建负责。
JavaExecAdapter.configure(
    this, config, target, "client",
    listOf("-Dproject.option=required"),
    emptyList()
)
```

它在任务执行前解析选中值、按需创建 `instances/<target>/<variant>/`，设置工作目录、最大堆和本机 JDK，追加项目参数与实例参数。普通 client 参数为 `--width`、`--height`、`--username`，server 不追加这些字段。原任务不得重复提供这些托管选项；重复接入也会报错。

Loader 可能在执行时自行改写参数、选择进程或有专用 run DSL，因此不能假定所有 Loader 任务都能直接套用 JavaExec 适配器。真实 Loader 接入应优先使用其原生配置 API，并验证最终进程。验证项目的专用 instance 路径仍由验证任务负责；没有额外的场景配置层。

## 检查与可见性

```powershell
.\gradlew.bat verifyConfiguration
.\gradlew.bat check
```

`verifyConfiguration` 显式检查事实职责、所有声明的基础运行值、已注册 Target 的四级解析及配置过的本机工具。`resolveInstance` 只校验实际选中的运行值；普通根 help/tasks 不要求 local 文件或 Minecraft 实例存在。

根 `check` 包含完整配置检查，根 `build` 通过 `check` 同样执行它：声明了无效客户端玩家名时，根检查失败是预期行为。Target 独立构建、测试和服务端任务只消费所需配置，不应因无关客户端玩家名失败。接入 Loader 时应保留这个边界，避免在任何任务配置阶段都解析客户端实例。

真实 Target 注册后可以查询最终结果及来源：

```powershell
.\gradlew.bat printInstanceConfiguration --target=26.2-neoforge --variant=client
```

示例 ID 不会自动注册模块。查询不会启动程序或创建实例，不打印 extra args 的具体值。`verifyConfiguration` 在空模板中明确报告零个已注册 Target。

`check` 同时运行配置库单元测试与 Gradle TestKit 功能测试。功能测试使用临时工程和独立 Java 参数探针，不是 Minecraft 测试。可用 `-PtestFixturesDirectory=<仓库外目录>` 指定临时工程位置；目录应预先存在。测试输入保留便于复核，不执行永久删除。

可额外传入 `-PalternateJdkHome=<另一个版本的JDK根目录>`，验证实例 JDK 与 Gradle JVM 不同的真实启动场景；未提供时只跳过这一项额外测试，其余测试照常执行。本机 JDK 的 executable 在配置任务时接入，让 Gradle 正确推导匹配的 Java launcher，不能在任务执行时才替换成另一版本。

配置文件通过 Gradle Provider 读取，配置缓存会感知内容变化及 local 文件的出现/消失。修改配置后无需清除整个 Gradle 缓存。
