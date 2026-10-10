# 合规检查机制

合规检查把 AGENTS.md 中能够机械判定的规则变成可执行的测试。规范与检查器一起分发：规范规定"应当如何"，检查器在本地和持续集成中给出同样的判定。

## 规范与检查器的对应

AGENTS.md 的每条规则以 `- **<编号>** [<标记>]` 开头。检查器内置同一套规则目录，并在每次运行时核对规范本身：

| 规则 | 保证 |
| --- | --- |
| S-01 | AGENTS.md 声明的规范版本与检查器实现的版本一致 |
| S-02 | 标记为 [检查] 与 [提示] 的规则与检查器目录一一对应，严重程度一致 |
| S-03 | 第 9 节的建议词表与检查器内置词表一致 |
| S-04 | `CLAUDE.md` 只导入根目录 `AGENTS.md` 与项目规范 `documents/AGENTS.md` |
| S-05 | 项目规范的项目信息与 `gradle.properties` 一致，参考 Target 已登记，Target 表与登记的 Target 及 `target.properties` 一致 |
| S-06 | 模板文件与检查器记录的 SHA-256 清单一致，模板组件中没有清单之外的文件 |
| S-07 | 根目录 `AGENTS.md` 与项目规范合计不超过 32 KiB，超出部分会被 Codex 忽略 |

因此，修改规范中可检查的规则时必须同步修改检查器；只改一边，检查就会失败。规范版本即模板版本，写作 `<大版本>.<中版本>.<小版本>`，从 1.0.0 开始：改变已有规则的含义或删除规则提升大版本；新增规则或功能提升中版本；不改变规则的修正与文档改动提升小版本。

## 模板文件

以下文件是模板的一部分，所有采用同一规范版本的项目内容相同（G-08）：

| 模板文件 | 作用 |
| --- | --- |
| `AGENTS.md`、`CLAUDE.md` | 仓库规范与 Claude Code 的入口 |
| `.rumdl.toml` | D-04 的规则配置 |
| `.github/workflows/specification.yml` | 持续集成中对照模板发布版本复核模板文件 |
| `components/configuration`、`components/conventions`、`components/compliance` | 构建插件与检查器 |

模板文件受两层保护：

| 层 | 位置 | 能发现 |
| --- | --- | --- |
| 哈希清单 | 检查器资源 `template-files.sha256`，S-06 在每次 `verifyCompliance` 时核对 | 模板文件被修改、缺失，或模板组件中多出文件 |
| 发布版本对照 | `specification.yml` 取出模板仓库的标签 `<规范版本>`，逐个比较模板文件 | 连同哈希清单一起被修改的模板文件 |

清单与检查器在同一个组件中，能改检查器的人也能改清单，所以本地核对只防止无意的修改。持续集成中的对照以模板仓库的发布版本为准，能发现连同清单一起修改的模板文件；但对照工作流本身也在项目仓库中，同时修改它的人仍能绕过，要阻止这一点需要由仓库规则把它设为合并的必需检查，并限制对工作流文件的修改。模板仓库本身跳过对照。项目自己的信息与补充规则写在 `documents/AGENTS.md`，项目代码放在项目组件中，都不受清单约束。

发布新的模板版本时，同时修改模板文件、检查器的规则实现与测试，按变化程度提升规范版本，重新生成清单，为模板仓库打上标签 `<规范版本>`；项目升级时从两个标签之间 `AGENTS.md` 与文档的差异得知需要跟进的改动。清单的格式与 `sha256sum` 相同，每行是哈希、两个空格与路径，覆盖上表的全部文件，不含清单自身；在 Git Bash 中可以这样生成：

```bash
git ls-files --cached --others --exclude-standard -- AGENTS.md CLAUDE.md .rumdl.toml .github/workflows/specification.yml components/configuration components/conventions components/compliance | grep -v template-files.sha256 | sort | xargs sha256sum --text > components/compliance/src/main/resources/io/github/recrivenvi/compliance/template-files.sha256
```

派生项目升级模板时按 [procedure-upgrade_template](../development/procedure-upgrade_template.md) 整体替换模板文件，不单独修改其中任何一处。

## 严重程度

| 标记 | 未满足时 |
| --- | --- |
| [检查] | 输出"失败"，构建失败 |
| [提示] | 输出"警告"，构建继续；严格模式下构建失败 |

提示用于"有建议但允许例外"的规则，例如文档类型词，以及 T-07 对多个 Target 中相同 Java 源码的提醒：T-07 只比较产品源码 `src/main/java/` 与 `src/client/java/` 下去掉回车后的完整内容，含有 `net.minecraft`、`com.mojang`、`net.neoforged`、`net.minecraftforge`、`net.fabricmc`、`cpw.mods` 或 `org.spongepowered` 的文件以及 `package-info.java` 不参与比较。项目可以登记补充词来消除反复出现的提示，也可以开启严格模式把提示当作错误；两者都在 `settings.gradle.kts` 的 `compliance {}` 中设置。

## 检查范围

检查器通过 `git ls-files --cached --others --exclude-standard` 确定属于仓库的文件，因此被 `.gitignore` 忽略的构建输出、实例数据与 `local.toml` 不参与检查；Git 子模块的内容不在清单中；组件 `third_party/` 中的文件也被排除（C-09）。仓库不是 Git 仓库时，改为遍历目录并跳过同样的位置。文本规则（C-01 至 C-04）作用于文本文件；`.gitattributes` 中标为 `binary` 或 `-text`，以及前 8 KiB 含空字节的文件按二进制跳过；链接规则 D-05 作用于仓库中所有 `.md` 文件；引用式链接的使用处与定义由 D-04 的 rumdl 规则 MD052 与 MD057 检查。验证目录 `result/` 中的结果与证据由验证工具写入并保持原样，不参与文本规则、D-04 与 D-05，但 V-04 仍然检查其中有没有代码文件（C-09）。文档内容规则 D-06、D-13 至 D-16 只作用于 `documents/` 的分类目录。D-06 按文档的语言选择小节名与状态写法，语言由译文的区域代号或项目规范中的"文档主语言"决定，对照见 [format-document](../reference/format-document.md)。D-13 按文件名末尾的区域代号找到原文，D-14 作用于类型词为 `changelog` 的文档，D-15 作用于 `usage` 与 `release` 中的文档，D-16 作用于 `research` 中的文档。

有三条规则不只依赖上述文件清单：G-06 用 `git ls-files --cached --ignored --exclude-standard` 找出已被跟踪却被忽略的文件，不是 Git 仓库时跳过；I-04 直接读取 `instances/` 的前两级目录，只核对 Target 与实例目录的名称，不读取实例内部的内容，`instances/` 不存在时通过，因此持续集成中同样适用；L-08 从 Gradle 取得实际包含进构建的组件，从 `conventions` 插件取得 `components {}` 中登记的 `product` 与 `tool` 组件，与 `components/` 下的目录核对：模板组件必须被包含，其他组件必须已登记，只用 `includeBuild` 包含的组件不会打包进模组，也不能执行验证；I-04 的实例名单来自 `configuration` 插件，包括 `instances.toml` 与 `local.toml` 中登记的额外实例。

加载器规则（L-07、P-03 至 P-05）的字段清单见 [format-metadata](../reference/format-metadata.md)；P-04 按 TOML 与 JSON 的结构读取元数据，只认加载器实际读取的位置。T-02 逐行读取 Target 的 `build.gradle.kts`，跳过行注释与块注释，报告字符串中的版本号（网址中的数字除外）、三段式依赖坐标（包括用 `+` 或字符串模板拼出版本的写法）与插件版本 `id(...) version`；读取 `project.version` 等不含版本号的写法不受影响。D-12 的加载器列写作"<加载器名称> <版本>"或只写版本，最后一项必须与 `loader_version` 完全相同。D-14 按语义化版本 2.0.0 校验版本号并比较先后，构建元数据不参与比较。P-06 读取 Git 远程 `origin`：`gradle.properties` 仍使用模板自身的模组 ID 或 Java 包，而 `origin` 不指向模板仓库 `NOTICE` 归属记录指定的模板来源 时给出提示，提醒用模板创建的项目按"由模板创建新模组"的规程（`procedure-derive_project`）改名；仓库不是 Git 仓库或没有 `origin` 时同样提示。P-07 在同样条件下检查模板自身名称的残留，排除受保护模板文件、`NOTICE`、`licenses/`、验证的 `result/` 与由 P-06 负责的 `gradle.properties`；其他文件须按本项目改写，严格模式下残留提示会导致失败。

验证规则（V-01 至 V-04）检查 `validations/` 的目录命名、建议类型词、必需文件、允许条目，以及除 `instance/` 外没有源码、脚本或可执行文件；代码文件按扩展名识别，清单见 [format-validation](../reference/format-validation.md)。V-08 解析 `validation.toml`，核对 `tool` 是 `conventions` 插件登记的 `tool` 组件、`targets` 中的名称都是已登记的 Target；`conventions` 把 `product` 与 `tool` 组件的名称发布为根项目的额外属性 `io.github.recrivenvi.products` 与 `io.github.recrivenvi.tools`，检查器从这里读取。V-09 要求 `validation.md` 有"目标、运行、通过标准"三节，且"运行"一节含代码块。这两条让每项验证都写明由谁执行、覆盖哪些 Target、怎样执行与怎样算通过，Agent 不必猜测。`validation.toml` 的 `[instances]` 由 `configuration` 插件在配置阶段校验；检查器不解析其他配置或结果，不判断验证是否通过，也不执行验证。

## 输出

| 输出 | 位置 |
| --- | --- |
| 控制台 | 每条发现一行：级别、规则编号、位置、说明，最后一行为汇总 |
| JSON 报告 | `build/reports/compliance/report.json` |
| Markdown 报告 | `build/reports/compliance/report.md` |
| GitHub 注解 | 环境变量 `GITHUB_ACTIONS` 为 `true` 时，每条发现输出为 `::error` 或 `::warning` 注解 |
| GitHub 摘要 | 存在 `GITHUB_STEP_SUMMARY` 时追加 Markdown 报告 |

## 与其他检查的分工

[构建] 标记的规则不由 `verifyCompliance` 检查，而由构建过程负责：

| 规则 | 工具 | 任务 |
| --- | --- | --- |
| C-05 | Spotless：google-java-format、ktfmt、Gson 与通用空白步骤；组件中的其他语言由组件自己调用的工具负责 | `spotlessCheck` 与组件的 `check`；`spotlessApply` 自动修正 |
| D-04 | rumdl，配置为根目录 `.rumdl.toml` | `lintMarkdown`；`fixMarkdown` 自动修正，`spotlessApply` 会先运行它 |
| F-01 至 F-03 | `configuration` 插件 | `verifyConfiguration` |
| T-01、T-03 | `configuration` 与 `conventions` 插件 | 配置阶段 |

这些检查都挂在 `check` 上，所以 `gradlew check` 覆盖全部 [检查]、[提示] 与 [构建] 规则。拿不到本地输入时，`gradlew checkRepository` 覆盖除 Target 编译与 `verifyInputs` 以外的全部检查。rumdl 是单个原生程序，版本固定在 `gradle/libs.versions.toml`，由 Gradle 从它的 GitHub 发布页下载，本机与持续集成都不需要另外安装。

格式问题会被两处同时发现：Spotless 按共用的文本扩展名清单自动修正，检查器的 C-01 至 C-04 覆盖所有文本文件。执行一次 `gradlew spotlessApply` 后，清单中的文件两处都会通过；清单之外的文本文件按检查器的说明手工修正，见 [architecture-build](architecture-build.md)。
