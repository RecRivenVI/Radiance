# 移除组件

## 适用范围

删除一个项目组件。`<component>` 为组件名。模板组件 `configuration`、`conventions` 与 `compliance` 不能移除（G-08）。

## 步骤

1. 从 `settings.gradle.kts` 的 `components {}` 中删除 `product("<component>")` 或 `tool("<component>")`；组件以设置插件的形式提供功能时，同时删除 `pluginManagement` 中的 `includeBuild` 与 `plugins {}` 中的插件。
2. `product` 组件：Target 中仍调用组件的代码，向使用者确认删除还是改写进各 Target（T-04）；删除各 Target 为它的第三方库写的 `jarJar(...)` 或 `include(...)`。
3. `tool` 组件：`validation.toml` 中 `tool = "<component>"` 的验证，向使用者确认删除这些验证，还是改由其他 `tool` 组件执行（V-08）；只为这些验证存在的 `versions/<target>/src/probe/` 一并处理。
4. 组件的 `third_party/` 中有 Git 子模块时，对每个子模块 `<path>` 执行 `git submodule deinit -f <path>` 与 `git rm <path>`，`<path>` 为子模块在仓库中的路径。
5. 删除 `components/<component>/`。
6. 删除 `gradle/libs.versions.toml` 中只被这个组件使用的条目，以及 `licenses/` 中只属于它的第三方许可原文，并同步 `NOTICE`。
7. 删除 `documents/design/architecture-<component>.md`，以及其他文档中指向它的链接（D-05）。
8. 执行 `.\gradlew.bat spotlessApply`，再执行 `.\gradlew.bat check`。
9. `product` 组件另外在参考 Target 上执行 `.\gradlew.bat :version:<reference>:runClient`，`<reference>` 为项目规范中的参考 Target；确认产品正常加载。

## 验收

- `.\gradlew.bat check` 成功，L-04、L-08 与 V-08 没有发现。
- 下面的命令没有输出。它不搜索记录历史的文档：决策记录、阶段记录、研究记录、更新日志与移植文档可以继续提到 `<component>`（D-10），其中仍指导读者使用这个组件的操作说明按 D-10 改写或删除：

  ```powershell
  git grep -n "<component>" -- settings.gradle.kts gradle versions validations .gitmodules documents ":(exclude)documents/release" ":(exclude)documents/research" ":(exclude)documents/design/adr-*" ":(exclude)documents/project/phase-*" ":(exclude)documents/development/porting-*"
  ```

- `product` 组件的类与资源不再出现在 `versions/<target>/build/libs/` 的模组 JAR 中，`<target>` 为任一已登记的 Target。

## 禁止

- 在 Target 中保留只为这个组件存在的依赖、打包配置或无用代码（G-02）。
- 未经使用者同意删除仍在使用的验证或产品功能。
