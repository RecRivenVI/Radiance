# 加载器元数据格式

本文定义每个 Target 的加载器元数据文件、其中必须使用占位符的字段（P-03、P-04），以及资源路径与源码包目录的规则（P-05、L-07）。占位符在构建时展开，来源见 [configuration-repository](configuration-repository.md) 的"模板变量"。

## 元数据文件

每个 Target 的 `src/main/resources/` 中只有本加载器的元数据文件，其他加载器的元数据文件不能出现：

| 加载器 | 文件 |
| --- | --- |
| `forge` | `META-INF/mods.toml` |
| `neoforge` | `META-INF/neoforge.mods.toml` |
| `fabric` | `fabric.mod.json` |

`src/probe/` 中的探针是独立的模组，元数据文件与产品相同，模组 ID 写作 `"${mod_id}_probe"`；P-03 与 P-04 不检查它，写法见 [format-validation](format-validation.md)。

## 必须使用占位符的字段

检查器按 TOML 与 JSON 的结构读取元数据，字段必须位于加载器读取它的位置；无法解析的元数据文件同样被 P-04 报告。

### Forge 与 NeoForge

文件中有且只有一个 `[[mods]]` 条目；依赖都写在 `[[dependencies.${mod_id}]]` 中，表名中的 `${mod_id}` 可以不加引号（P-01）。

| 字段 | 写法 |
| --- | --- |
| 顶层的 `license` | `"${mod_license}"` |
| `[[mods]]` 的 `modId` | `"${mod_id}"` |
| `[[mods]]` 的 `version` | `"${mod_version}"` |
| `[[mods]]` 的 `displayName` | `"${mod_name}"` |
| `[[mods]]` 的 `authors` | `"${mod_authors}"` |
| `[[mods]]` 的 `description` | `"${mod_description}"` |
| `[[mods]]` 的 `displayTest` | Forge 写作 `"${display_test}"`；NeoForge 不写 |
| `modId` 为 `minecraft` 的依赖的 `versionRange` | 包含 `${minecraft_version}` |
| `modId` 为本加载器（`forge` 或 `neoforge`）的依赖的 `versionRange` | 包含 `${loader_version}` 或 `${loader_minimum}` |

`displayTest` 只有 Forge 读取。NeoForge 的加载器与本体都不读取这个字段，运行端由入口的 `dist` 参数与网络负载决定：客户端与服务端连接时，只有一方注册了另一方没有的必需网络负载才会拒绝连接。因此 NeoForge 元数据中出现 `displayTest` 会被 P-04 报告。

### Fabric

下表的字段都是顶层对象的成员，嵌套在其他对象中的同名字段不算数。版本要求可以写成字符串数组，其中一项使用占位符即可。

| 字段 | 写法 |
| --- | --- |
| `id` | `"${mod_id}"` |
| `version` | `"${mod_version}"` |
| `name` | `"${mod_name}"` |
| `description` | `"${mod_description}"` |
| `license` | `"${mod_license}"` |
| `authors` | `["${mod_authors}"]` |
| `environment` | `"${fabric_environment}"` |
| `depends.fabricloader` | 包含 `${loader_version}` 或 `${loader_minimum}` |
| `depends.minecraft` | 包含 `${minecraft_version}` |
| `depends.java` | 包含 `${java_version}` |
| `entrypoints` 中的每个入口 | 类名以 `${mod_group}.` 开头；写成对象时取 `value` |

表外的字段，例如图标、链接与 Mixin 配置，可以写字面值。

加载器版本要求的下限默认是编译用的 `loader_version`。产品能在更早的加载器上运行时，在 `target.properties` 中写 `loader_minimum`，并在元数据中改用 `${loader_minimum}`，例如 `"[${loader_minimum},)"`。

## 运行端

`gradle.properties` 的 `mod_side` 决定产品模组需要安装在哪里（P-02），Fabric 的 `environment` 与 Forge 的 `displayTest` 由它展开，取值见 [configuration-repository](configuration-repository.md) 的"模板变量"：

| `mod_side` | 含义 | 单人游戏 | 加入服务器的客户端 | 独立服务端 |
| --- | --- | --- | --- | --- |
| `both` | 客户端与服务端都必须安装 | 安装 | 必须安装 | 必须安装 |
| `client` | 仅客户端，可加入未安装本模组的服务器 | 安装 | 安装 | 不需要；安装了也不执行客户端逻辑 |
| `server` | 仅服务端，原版客户端可以加入 | 安装 | 不需要；安装了照常加载 | 安装 |

日常实例始终加载产品（I-01），在游戏中确认产品时按下表判断，其中 `runClientMultiplayer` 加入 `runServer`：

| `mod_side` | `runClient` | `runServer` | `runClientMultiplayer` |
| --- | --- | --- | --- |
| `both` 或 `server` | 出现产品的初始化输出 | 出现产品的初始化输出 | 出现产品的初始化输出，并加入服务器 |
| `client` | 出现产品的初始化输出 | 正常启动，产品的客户端逻辑不执行：Fabric 不加载产品，NeoForge 不构造客户端入口 | 出现产品的初始化输出，并加入服务器 |

三个实例都不应有 `crash-reports/` 或缺少模组的报错。与未安装本模组的一端互通不能用日常实例确认，需要另行测试：`client` 时用加载本模组的客户端加入原版服务端，`server` 时用普通启动器中的原版客户端加入 `runServer`。

切换运行端的步骤见 [procedure-switch_side](../development/procedure-switch_side.md)。

## 资源路径

`src/<源码集>/resources/assets/` 与 `src/<源码集>/resources/data/` 下的第一级目录是命名空间，其后是资源路径。两者只能使用小写英文字母、数字、`_`、`-` 与 `.`，路径用 `/` 分隔（P-05）。这是 Minecraft 资源位置的规则，不符合的资源会被游戏忽略或导致加载失败。

## 源码包目录

`src/<源码集>/java/` 与 `src/<源码集>/templates/` 下的 Java 源码位于 `mod_group` 对应的目录及其子目录（L-07）。`mod_group` 为 `com.example.examplemod` 时，源码位于 `com/example/examplemod/` 之下。
