# 仓库配置

本文定义构建插件读取的仓库配置。配置按归属分为五类：

| 类别 | 文件 | 内容 |
| --- | --- | --- |
| 项目事实 | `gradle.properties`、`versions/<target>/target.properties`、Target 目录名、`gradle/libs.versions.toml` | 模组身份、版本、运行端、加载器、依赖与工具版本 |
| 日常实例 | `instances.toml`、`local.toml` | 共享预设与个人覆盖：内存、窗口、玩家名、追加参数、额外实例与测试模组 |
| 验证实例 | `validations/<name>/validation.toml` 的 `[instances]` | 一项验证要启动的角色及其实例设置 |
| 本地输入 | `inputs.toml`、`local.toml` 的 `[inputs]` | 仓库之外的文件：说明、来源、允许的哈希与本机路径 |
| 本机许可 | `local.toml` 的 `eula` | 使用者是否接受 Minecraft EULA |

`validations/<name>/validation.toml` 中，插件读取 `targets` 与 `[instances]`，见下文"验证实例"；必需的 `tool` 与 `targets` 及目录结构见 [format-validation](format-validation.md)，其余键由项目自己的验证工具读取，不属于本文定义。

两条总规则：

- 扁平的事实沿用模组生态的惯例，写在 properties 文件中；结构化的配置写在 TOML 文件中。
- 每个设置只有一种写法。未知的键、重复的键、类型不符或取值无效时，构建在配置阶段失败。命令行的 `-P` 参数不覆盖任何配置。

错误信息的格式为 `<文件> [<键路径>]: <原因>`，键路径使用 TOML 的点号写法，例如 `instances.toml [targets."26.3-neoforge".client.memory-max]`。拼错的键名会附带最接近的正确写法。

## 项目事实

### gradle.properties

| 键 | 规则 |
| --- | --- |
| `mod_id` | 2–58 个小写字母、数字或下划线，以字母开头 |
| `mod_name` | 显示名称 |
| `mod_version` | 版本号，不含空格 |
| `mod_group` | 小写 Java 包名，同时作为 Maven group |
| `mod_side` | `both`、`client` 或 `server`，含义见 P-02 |
| `mod_authors` | 作者显示文本 |
| `mod_license` | 许可名称，建议使用 SPDX 标识符 |
| `mod_description` | 一句话描述 |

以上键全部必需，同一个键不能出现两次。可以另加以 `mod_` 开头的小写键作为模板变量。`org.gradle.*` 由 Gradle 按原生规则处理；模板启用配置缓存，并把 `org.gradle.workers.max` 设为 2。

### target.properties

| 键 | 规则 |
| --- | --- |
| `loader_version` | 必需。NeoForge 版本、Forge 版本（不含 Minecraft 前缀）或 Fabric Loader 版本 |
| `java_version` | 必需。编译与运行使用的 Java 主版本号，8–99 |
| `loader_minimum` | 可选。产品支持的最低加载器版本，供元数据的加载器版本要求使用 `${loader_minimum}`；未写时等于 `loader_version` |

1.x Fabric Target 使用 Loom 的重映射插件，可以选择映射表：

| 键 | 规则 |
| --- | --- |
| `mappings` | `mojang`（Mojang 官方映射，默认，推荐）或 `yarn`；只能写在 1.x Fabric Target 中。Mojang 映射与 NeoForge、Forge 使用相同的名称，代码更容易在加载器之间移植 |
| `yarn_version` | `mappings=yarn` 时必需，例如 `1.21.4+build.8`；其他情况不能写 |

可以另加小写下划线形式的键，作为模板变量与构建脚本可读取的事实；随 Target 变化的依赖版本写作 `<名称>_version`，例如 `sodium_version`。`target`、`minecraft_version`、`loader` 由目录名推导，不能写在此文件中。

### 构建脚本读取事实

Target 的构建脚本通过扩展 `target` 读取事实，不直接书写版本号（T-02、F-04）：

| 写法 | 结果 |
| --- | --- |
| `target.name` | Target 名称，例如 `26.3-fabric` |
| `target.fact("<key>")` | 一项事实的值；可读取全部 `mod_*`、`target.properties` 的键，以及 `target`、`minecraft_version`、`loader`；键不存在时构建失败并列出已有的键 |
| `target.module("<group>:<name>", "<key>")` | 依赖坐标 `<group>:<name>:<值>`，版本取自事实 `<key>` |
| `target.input("<name>")` | 本地输入的文件，见下文"本地输入" |

```kotlin
dependencies {
    compileOnly(target.module("maven.modrinth:sodium", "sodium_version"))
    implementation(libs.snakeyaml)
}
```

各 Target 共用同一版本的依赖写在 `gradle/libs.versions.toml`，用 `libs.<别名>` 引用。下载这些依赖需要的额外 Maven 仓库在 Target 脚本的 `repositories {}` 中声明。加载器元数据中对这些模组的版本要求用同名占位符取值，例如 `"${sodium_version}"`。

### 模板变量

`src/main/templates/` 下的 Java 源码与加载器元数据文件在构建时展开以下变量，取值按字符串规则转义，因此占位符只能放在双引号字符串中（P-01）：

| 变量 | 来源 |
| --- | --- |
| 全部 `mod_*` | `gradle.properties` |
| 全部 Target 事实 | `target.properties` |
| `target`、`minecraft_version`、`loader` | Target 目录名 |
| `fabric_environment` | `mod_side` 为 `client` 时是 `client`，否则是 `*` |
| `display_test` | `both` → `MATCH_VERSION`，`client` → `IGNORE_ALL_VERSION`，`server` → `IGNORE_SERVER_VERSION`；只用于 Forge 的元数据 |
| `loader_minimum` | `target.properties` 中的同名键，未写时等于 `loader_version` |

## 日常实例

日常实例是开发者手动启动的 `client`、`client-multiplayer` 与 `server`，以及项目登记的额外实例，数据保存在 `instances/<target>/<variant>/`。共享预设写在 `instances.toml`，个人覆盖写在 `local.toml`，两者使用相同的表，都可以省略：

```toml
[all]
memory-max = "4G"
width = 1280
height = 720

[targets."26.3-neoforge".client]
memory-max = "6G"
```

| 表 | 作用范围 |
| --- | --- |
| `[all]` | 全部日常实例 |
| `[client]`、`[client-multiplayer]`、`[server]` | 全部 Target 的该实例 |
| `[targets."<target>".all]` | 一个 Target 的全部实例 |
| `[targets."<target>".<variant>]` | 一个 Target 的一个实例 |
| `[variants.<name>]` | 登记一个额外实例 |

| 键 | 类型 | 适用实例 | 默认值 | 规则 |
| --- | --- | --- | --- | --- |
| `memory-min` | 字符串 | 全部 | 等于 `memory-max` | 最小堆 `-Xms`，写法同 `memory-max`，不大于 `memory-max` |
| `memory-max` | 字符串 | 全部 | 客户端 `2G`，服务端 `1G` | 最大堆 `-Xmx`，正整数加 `K`、`M` 或 `G`，换算成的字节数不超过 64 位有符号整数的范围 |
| `width`、`height` | 整数 | 客户端 | `854`、`480` | 窗口尺寸，正整数 |
| `player-name` | 字符串 | 客户端 | 以 `client-multiplayer` 为基础时 `DevGuest`，其他 `DevPlayer` | 3–16 个 ASCII 字母、数字或下划线 |
| `port` | 整数 | 服务端 | `25565` | 1–65535 |
| `jvm-args` | 字符串数组 | 全部 | 空 | 追加的 JVM 参数 |
| `game-args` | 字符串数组 | 全部 | 空 | 追加的游戏参数 |
| `mods` | 字符串数组 | 全部 | 空 | 只在该实例中加载的模组，元素为 `inputs.toml` 中的输入名称 |

默认值取自 Minecraft 官方资料：官方启动器给客户端 2G 最大堆；官方服务端下载页的启动命令让服务端最小堆与最大堆都为 1G；游戏窗口默认 854×480；服务端默认端口 25565。玩家名没有官方默认值，模板取 `DevPlayer` 与 `DevGuest`，让两个客户端可以同时进入同一服务器。

`jvm-args` 与 `game-args` 不能重复设置其他设置项负责的参数（F-03）：

| 设置项 | 不能出现在追加参数中的写法 |
| --- | --- |
| `memory-min`、`memory-max` | `-Xms`、`-Xmx` |
| `width`、`height`、`player-name`、`port` | `--width`、`--height`、`--username`、`--port` |
| 实例目录与服务端模式 | `--gameDir`、`--nogui`、`nogui` |

另外，`jvm-args` 中出现 Java 8 已移除的永久代参数 `-XX:PermSize=` 或 `-XX:MaxPermSize=` 也会报错：Java 17 及以后遇到它们会拒绝启动。

解析分两步：

1. 合并文件：`local.toml` 中的键覆盖 `instances.toml` 中位于相同表、相同键名的值；数组整体替换，不拼接。
2. 选取取值：对每个实例、每个键，按 `[targets."<target>".<variant>]`、`[targets."<target>".all]`、`[<variant>]`、`[all]` 的顺序取第一个存在的值；额外实例在每一级之后还会查它的基础实例的表，即 `[targets."<target>".<variant>]`、`[targets."<target>".<base>]`、`[targets."<target>".all]`、`[<variant>]`、`[<base>]`、`[all]`。

因此，个人覆盖要写在与共享预设相同的表中才能替换它；写在更宽泛的表中不会压过共享的具体设置。写在实例表中的键必须适用于该实例，例如 `[server]` 中的 `width` 会报错；`[all]` 与 `[targets."<target>".all]` 中的键只作用于适用的实例。所有写出的值都会被校验，不存在"被覆盖所以不校验"的值。

独立服务端总是带 `--nogui` 启动。客户端实例首次启动前没有 `options.txt` 时，准备任务写入 `onboardAccessibility:false`，跳过新实例的无障碍引导界面；已有的 `options.txt` 不会被改动。

### 额外实例

需要更多长期保留的实例时，例如第二套多人测试存档，在 `[variants]` 中登记，`base` 取三种内置实例之一：

```toml
[variants.secondary-skins]
base = "client-multiplayer"

[secondary-skins]
mods = ["custom_skin_loader"]
```

| 项 | 规则 |
| --- | --- |
| 名称 | 小写英文单词或数字，用 `-` 连接；不能与内置实例重名，也不能是 `all`、`targets`、`variants`、`inputs`、`eula` |
| `base` | `client`、`client-multiplayer` 或 `server`；决定实例是客户端还是服务端，以及取值时参考哪张表 |
| 任务 | `run` 加实例名的驼峰写法，例如 `runSecondarySkins` |
| 目录 | `instances/<target>/<name>/` |

额外实例可以登记在 `instances.toml` 或 `local.toml` 中，但同一个名称只能登记一次。I-04 按登记的实例名检查 `instances/` 中的目录。

移除额外实例时，删除它在 `[variants]` 中的登记以及以它命名的表；`local.toml` 中的部分由使用者删除。各 Target 下的 `instances/<target>/<name>/` 保存着它的存档，按使用者的选择移到仓库之外或删除，留在原处时 I-04 会报告。

### 实例中的模组

`mods` 中的输入在每次启动实例前核对哈希后复制进实例的 `mods/` 文件夹，同一个输入只能列出一次。复制的文件记录在实例目录的 `.managed-mods` 中，每行是 SHA-256、两个空格与文件名。实例的 `mods/` 也可以放使用者自己的文件，准备任务按以下规则处理：

| 情况 | 处理 |
| --- | --- |
| 两个输入的文件名相同，或只有大小写不同 | 失败，不复制任何文件；把其中一个文件改名，并在 `local.toml` 中更新路径 |
| 输入文件位于这个实例的 `mods/` 中 | 失败；把它移到实例之外 |
| `mods/` 中已有同名文件，且不是记录中的副本 | 内容与输入相同时视为副本；不同时失败，不覆盖 |
| 记录中的副本被改动过，输入又要更新它 | 失败，不覆盖 |
| 输入从列表中删除 | 删除内容未被改动的副本；被改动过的副本保留，给出警告，不再管理 |
| 输入的文件名只改了大小写 | 删除内容未被改动的旧副本，按新名称复制；旧副本被改动过或已不是文件时失败，不覆盖也不删除 |

任何一项失败时，`mods/` 与记录都保持原样。实例只能加载在开发环境中可运行的模组：1.20.5 及以后的 NeoForge 发布版 JAR 与开发环境使用相同的名称，可以直接使用；Fabric Loader 在开发环境中会把 `mods/` 中的模组重映射到开发名称；1.20.1 Forge 的发布版 JAR 使用 SRG 名称，不能直接放进开发环境的实例。

所有日常实例都需要的模组，例如前置模组，不写在 `mods` 中，而是在 Target 脚本中用加载器原生的运行时依赖声明。

## 验证实例

验证在 `validation.toml` 中为每个要启动的角色写一张 `[instances.<角色>]` 表，`<角色>` 为 `client`、`client-multiplayer` 或 `server`：

```toml
tool = "acceptance_gates"
targets = ["26.3-neoforge"]

[instances.server]
port = 25580

[instances.client]
game-args = ["--quickPlayMultiplayer", "127.0.0.1:25580"]
```

| 规则 | 内容 |
| --- | --- |
| 可写的键 | 与日常实例的表相同，取值规则、默认值与"实例中的模组"的同步规则也相同 |
| 取值来源 | 只有这张表与默认值；`instances.toml` 与 `local.toml` 的 `[all]`、`[targets]`、角色表与 `[variants]` 都不参与 |
| 本机设置 | `eula` 与 `[inputs]` 中的本机路径仍从 `local.toml` 读取 |
| 失败的写法 | 未知的角色、额外实例名、不适用于该角色的键、`memory-min` 大于 `memory-max`、未登记的本地输入；`targets` 为空或缺失时写了 `[instances]` |

错误信息使用验证的文件路径，例如 `validations/smoke-join/validation.toml [instances.client.port]: port 不适用于 client`。任务名、实例目录与探针见 [format-validation](format-validation.md)。

## 本地输入

本地输入是构建或实例需要、但不能放进仓库的文件，例如闭源模组、只在本机存在的兄弟项目产物或测试用模组。它们分两处记录：

```toml
# inputs.toml，提交进仓库
[examplemod]
version = "1.0.0"
description = "闭源依赖模组"
source = "作者的发布页"
sha256 = ["3f1c...", "9a0b..."]
```

```toml
# local.toml，只在本机
[inputs]
examplemod = "D:/Mods/examplemod-1.0.0.jar"
```

| 文件 | 键 | 规则 |
| --- | --- | --- |
| `inputs.toml` | 表名 | 输入名称，小写字母、数字、`_` 与 `-`，以字母开头 |
| `inputs.toml` | `version` | 必需，文件的版本 |
| `inputs.toml` | `description` | 必需，说明文件是什么 |
| `inputs.toml` | `source` | 可选，从哪里获取 |
| `inputs.toml` | `sha256` | 必需，允许的 SHA-256 列表，每项 64 个小写十六进制字符 |
| `local.toml` | `[inputs]` 中的键 | 已声明的输入名称；值为文件路径，相对路径从仓库根目录算起 |

名称与版本不自拟（F-05）：

| 文件 | 名称 | 版本 |
| --- | --- | --- |
| 模组 | 元数据中的模组 ID：`neoforge.mods.toml` 或 `mods.toml` 的 `modId`，`fabric.mod.json` 的 `id` | 元数据中的 `version`；Forge 写作 `${file.jarVersion}` 时取 JAR 清单的 `Implementation-Version` |
| 其他文件 | Maven 坐标的 `artifactId`，或上游发布中的名称 | 上游发布的版本号 |

模组的名称与版本由 `verifyInputs` 读取 JAR 中的元数据核对，不一致时报出应写的名称与版本。

使用方式：

| 用途 | 写法 |
| --- | --- |
| 编译或运行依赖 | Target 脚本中 `compileOnly(target.input("<name>"))` 等加载器原生的依赖声明 |
| 只在某个实例中加载 | 实例设置 `mods = ["<name>"]` |
| 核对本机的全部输入 | `.\gradlew.bat verifyInputs` |

文件缺失、路径未写或哈希不在列表中时，`target.input()` 不提供任何文件，配置阶段照常完成；每个 Target 的 `verifyInputs` 在编译前核对该 Target 用到的输入，失败时说明缺的是什么、从哪里获取。实例设置 `mods` 在启动实例前核对。`verifyConfiguration` 只校验两个文件的写法，不读取输入文件，所以持续集成在没有输入文件时仍可运行 `checkRepository`。

## Minecraft EULA

| 设置 | 位置 | 作用 |
| --- | --- | --- |
| `eula = true` | `local.toml` 顶层 | 使用者接受 [Minecraft EULA](https://aka.ms/MinecraftEULA)。只能由使用者本人写入或明确授权写入（G-04） |

接受后，`runServer` 等服务端运行与服务端角色的验证运行在启动前写入 `eula.txt`；未接受时不写入，独立服务端按原版行为提示并退出。`eula` 只能写在 `local.toml` 中，写进 `instances.toml` 会报错。

## settings.gradle.kts

| 设置 | 作用 |
| --- | --- |
| `targets { register("<target>") }` | 登记 Target，顺序即构建的顺序 |
| `components { product("<component>") }` | 登记随模组发布的项目组件：每个 Target 依赖它，它的 JAR 内容合并进模组 |
| `components { tool("<component>") }` | 登记构建工具、验证工具或脚本组件：只参与 `check` 与 `spotlessApply` |
| `compliance { vocabulary("<location>", "<word>", ...) }` | 为某个位置登记补充建议词，位置取 AGENTS.md 第 9 节词表的第一列 |
| `compliance { strict = true }` | 让所有 [提示] 规则也导致失败 |
| `compliance { sourceSets("<name>") }` | 登记 L-03 之外的补充源码集，名称为小驼峰式的 Gradle 源码集名，用途写在项目规范中；源码集本身由 Target 脚本原生创建 |

## 查看解析结果

| 命令 | 输出 |
| --- | --- |
| `.\gradlew.bat verifyConfiguration` | 校验全部配置文件，并报告 Target 数量、验证数量与 EULA 状态 |
| `.\gradlew.bat printLaunchSettings --target=<target> --variant=<variant>` | 一个日常实例每个设置的取值与来源，以及最终的目录与参数 |
| `.\gradlew.bat printLaunchSettings --target=<target> --variant=<role> --validation=<name>` | 验证 `<name>` 中角色 `<role>` 的同样信息；`<role>` 默认为 `client` |
