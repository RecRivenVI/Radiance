# 由模板创建新模组

## 适用范围

把用本模板创建的仓库改成一个新的模组项目。开始前与使用者确定以下占位符：

| 占位符 | 含义 | 模板中的当前值 |
| --- | --- | --- |
| `<mod_id>` | 模组 ID | `ravens_mod_template` |
| `<package>` | Java 包名，同时作为 `mod_group` | `io.github.recrivenvi.modtemplate` |
| `<Entry>` | 入口类名与 Gradle 根项目名 | `RavensModTemplate` |
| `<name>` | 显示名称 | `Raven's Mod Template` |

模板登记的 Target 只是起点，新项目支持哪些 Minecraft 版本与加载器由使用者决定。改名只涉及项目自己的内容：模板文件（G-08）、`NOTICE` 中的模板归属与 `licenses/` 中模板的许可原文按规定保留模板的名称。

## 前置条件

- 仓库是 Git 仓库，`git remote get-url origin` 不指向模板仓库；否则 P-06 与 P-07 不会提示遗漏的改名。仓库直接克隆自模板仓库时，把 `origin` 改为新项目自己的仓库；还没有自己的仓库时执行 `git remote remove origin`，推送前再添加。

## 步骤

1. 向使用者确认新项目要支持的 Target 与参考 Target。对不需要的 Target 执行 [procedure-remove_target](procedure-remove_target.md)；缺少的 Target 在完成本规程后按 [procedure-add_target](procedure-add_target.md) 新增。
2. 在 `gradle.properties` 中修改全部 `mod_*` 事实，并把 `mod_version` 设为首个版本号。
3. 在 `settings.gradle.kts` 中把 `rootProject.name` 改为 `<Entry>`。
4. 对每个 Target 的 `versions/<target>/src/` 下每个源码集中的 `java` 与 `templates`，`<target>` 为 Target 名称：
   1. 把包目录 `io/github/recrivenvi/modtemplate` 移动为 `<package>` 对应的目录。
   2. 更新 `package` 与 `import` 语句。
   3. 把 `RavensModTemplate` 重命名为 `<Entry>`。
5. 把 `fabric.mod.json` 入口中的类名改为 `<Entry>`；包名由 `${mod_group}` 取值，不需要修改。
6. 用新模组的业务替换入口中的示例日志。
7. 为新模组选择许可。来自模板的文件仍受模板的 MIT 许可约束：先把模板的 `LICENSE` 复制为 `licenses/ravens_mod_template-mit.txt`，再更新 `gradle.properties` 的 `mod_license` 与根目录的 `LICENSE`，并在 `NOTICE` 开头写明本项目的许可，以及模板插件与规范来自 Raven's Mod Template。
8. 重写项目规范 `documents/AGENTS.md`、`README.md` 及其各语言版本、`documents/usage/` 与 `documents/release/`；新的更新日志从 `## [未发布]` 开始；至少保留一篇玩家文档与一份更新日志（D-17）。项目规范与根目录 `AGENTS.md` 合计不超过 32 KiB（S-07），项目规范只写项目信息、Target 表与项目特有的约束，操作细节写进 `documents/development/` 的规程。
9. 删除描述模板示例代码的 `documents/development/porting-entrypoint.md`，并删除其他文档中指向它的链接（D-05）。
10. 执行 `.\gradlew.bat spotlessApply`，再执行 `.\gradlew.bat check`。
11. 执行 `.\gradlew.bat :version:<reference>:runClient`，`<reference>` 为项目规范中的参考 Target；确认日志中加载器列出的模组里有新模组后关闭游戏。

## 验收

- `.\gradlew.bat check` 成功，`.\gradlew.bat verifyCompliance` 的输出中没有 P-06 与 P-07。
- `instances/<reference>/client/logs/latest.log` 中加载器列出的模组里有新模组。

## 禁止

- 修改模板文件（G-08）；项目特有的规则只写在 `documents/AGENTS.md`，项目代码放在项目组件中，见 [procedure-add_component](procedure-add_component.md)。
- 删除或改写 `NOTICE` 中的模板归属与 `licenses/` 中模板的许可原文。
- 保留旧包名、旧 ID 的兼容代码或别名（G-02）。
