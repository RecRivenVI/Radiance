# 运行合规检查

合规检查按 [AGENTS.md](../../AGENTS.md) 中标记为 [检查] 与 [提示] 的规则审查整个仓库。它是 `check` 的一部分，也可以单独运行。机制见 [mechanism-compliance](../design/mechanism-compliance.md)。

## 命令

| 命令 | 作用 |
| --- | --- |
| `.\gradlew.bat verifyCompliance` | 运行合规规则；有 [检查] 规则未满足时失败 |
| `.\gradlew.bat verifyCompliance --strict` | 同上，[提示] 规则未满足时也失败 |
| `.\gradlew.bat printVocabulary` | 列出每个位置的建议词与项目补充词 |

## 读取结果

每条发现占一行，依次是级别、规则编号、位置与说明：

```text
失败 D-02 documents/usage/Backpack.md: 应写作 <type>-<topic>.md：字段之间用 -，组合词用 _
警告 D-03 documents/usage/instalation-basics.md: 类型词 instalation 不在 documents/usage 的建议词中；最接近的建议词：installation
规范 1.2.1 合规检查：失败 1，警告 1，检查了 143 个文件
```

完整结果同时写入 `build/reports/compliance/report.json` 与 `report.md`。按规则编号在 AGENTS.md 中找到对应条款并修正。

## 处理提示

[提示] 表示偏离了建议但不一定是错误，处理方式有三种：

1. 改用建议词，例如把 `tutorial-backpack.md` 改名为 `guide-backpack.md`。
2. 新词确实更准确、且会在项目中反复使用时，在 `settings.gradle.kts` 中登记为补充词：

   ```kotlin
   compliance {
       vocabulary("documents/usage", "tutorial")
   }
   ```

3. 希望所有提示都被当作错误时，在 `compliance {}` 中写入 `strict = true`。

T-07 的提示说明多个 Target 的产品源码（`main` 与 Fabric 的 `client`）中有完全相同、且不引用游戏或加载器的 Java 源码；`probe`、`test` 等源码集不参与比较。处理方式是把它移进 `product` 组件，见 [procedure-add_component](procedure-add_component.md)；只是暂时相同、之后会按版本分化的源码可以保留，提示不会让构建失败。

## 持续集成

`.github/workflows/check.yml` 在每次推送与拉取请求时运行 `gradlew check verifyRelease`；项目依赖本地输入、持续集成拿不到这些文件时，改为运行 `gradlew checkRepository`，Target 的编译、`verifyInputs` 与 `verifyRelease` 在本机执行（W-03）。`.github/workflows/specification.yml` 同时取出模板仓库对应规范版本的标签，逐个比较模板文件（G-08、S-06）。在 GitHub Actions 中，每条发现会显示为对应文件上的注解：[检查] 显示为错误，[提示] 显示为警告；工作流摘要中还有一张汇总表。
