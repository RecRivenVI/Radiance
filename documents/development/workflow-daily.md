# 日常工作流程

日常开发按"修改、运行、检查、提交"循环进行，每一步都在仓库根目录执行。规则以 [AGENTS.md](../../AGENTS.md) 为准。

## 修改

业务改动先在参考 Target 完成，再移植到其他 Target（T-05）。产品代码写在 `src/main/`，与游戏运行无关的单元测试写在 `src/test/`。修改项目组件时，组件会在下一次执行任务时自动重新构建；模板组件随模板版本更新，项目中不修改（G-08）。

## 运行实例

`<target>` 为 Target 名称，例如项目规范中的参考 Target：

```powershell
.\gradlew.bat :version:<target>:runClient
.\gradlew.bat :version:<target>:runServer
.\gradlew.bat :version:<target>:runClientMultiplayer
```

三个实例的数据分别保存在 `instances/<target>/client/`、`server/`、`client-multiplayer/`，彼此独立并长期保留；项目登记的额外实例按实例名另有目录。共享预设在 `instances.toml`，个人覆盖写在 `local.toml`。例如让多人客户端启动后自动加入本机服务端：

```toml
[client-multiplayer]
game-args = ["--quickPlayMultiplayer", "127.0.0.1:25565"]
```

1.20.1 Forge 的客户端以这种方式加入时偶尔会一直停在登录界面。原因在 Minecraft 1.20.1 的网络代码：连接刚建立时，发送登录请求的线程关闭读取，网络线程随后重新打开，两者存在竞争；关闭一方抢先时，客户端不再读取连接，既收不到服务端的 Forge 握手数据，也察觉不到服务端 30 秒后断开。这是游戏自身的问题，与模板无关；遇到时关闭这个客户端再启动一次。

确认某个实例每个设置的取值与来源，以及最终的参数：

```powershell
.\gradlew.bat printLaunchSettings --target=<target> --variant=client-multiplayer
```

改动产品行为、元数据或运行配置后，在受影响的 Target 上启动实例确认结果（W-02）。项目的自动化验证按 [format-validation](../reference/format-validation.md) 放置：验证工具是 `tool` 组件，游戏内代码在各 Target 的 `src/probe/`，验证数据在 `validations/`；文件、构建产物与接口等验证可以独立于游戏运行。每项验证的 `validation.md` 写明执行命令与通过标准；改动涉及它时按"运行"一节执行（V-10）。需要启动游戏的验证在 `validation.toml` 中声明角色，插件为它生成同时加载探针的运行任务，实例在 `validations/<name>/instance/` 中，与日常实例互不影响。新增验证见 [procedure-add_validation](procedure-add_validation.md)；探针代码与验证逻辑由项目编写（I-03）。

## 检查

```powershell
.\gradlew.bat spotlessApply
.\gradlew.bat check
```

`check` 包括合规规则（`verifyCompliance`）、配置校验（`verifyConfiguration`）、格式检查、所有组件的检查以及全部 Target 的编译，包括各 Target 的 `probe` 源码集。合规检查的用法见 [testing-compliance](testing-compliance.md)。

## 常用命令

命令从仓库根目录执行，Unix 使用 `./gradlew`。

| 命令 | 作用 |
| --- | --- |
| `.\gradlew.bat check` | 全部检查：合规规则、配置、本地输入、格式、Markdown、组件的检查与各 Target 编译 |
| `.\gradlew.bat checkRepository` | 不编译 Target、不读取本地输入的全部检查，供拿不到本地输入的持续集成使用 |
| `.\gradlew.bat verifyCompliance` | 只运行合规规则，加 `--strict` 时提示也视为失败 |
| `.\gradlew.bat verifyConfiguration` | 只校验配置文件 |
| `.\gradlew.bat verifyInputs` | 核对本地输入的路径与哈希 |
| `.\gradlew.bat lintMarkdown` | 只运行 Markdown 检查 rumdl |
| `.\gradlew.bat spotlessApply` | 按规范格式化，同时运行 rumdl 的自动修复 |
| `.\gradlew.bat printVocabulary` | 列出建议词与项目补充词 |
| `.\gradlew.bat build` | 构建全部 Target 并执行 `check` |
| `.\gradlew.bat collectRelease` | 构建全部 Target，把当前版本的发行 JAR 收集到 `build/release/<version>/` |
| `.\gradlew.bat verifyRelease` | 收集发行 JAR，并核对其中的元数据、许可文件与 `product` 组件，确认没有探针 |
| `.\gradlew.bat :version:<target>:runClient` | 启动日常实例；另有 `runClientMultiplayer`、`runServer` 与额外实例对应的任务 |
| `.\gradlew.bat printLaunchSettings --target=<target> --variant=<variant>` | 查看日常实例的最终参数及其来源；加 `--validation=<name>` 查看验证实例 |
| `.\gradlew.bat :version:<target>:runValidation<Name><Role>` | 启动一项验证的一个角色，同时加载探针；`<Name>` 与 `<Role>` 为验证名与角色名的大驼峰写法，通常由 `validation.md` 的"运行"一节或验证工具调用 |

## 提交

提交前确认 `check` 成功，改动影响发行 JAR 时 `verifyRelease` 也成功，改动已通过相应验证；涉及游戏运行的行为在日常实例中确认，并按 W-01 同步文档。首次提交时让 `gradlew` 带上可执行权限：

```powershell
git add --chmod=+x gradlew
```

推送后，GitHub Actions 会再运行一次 `check`。
