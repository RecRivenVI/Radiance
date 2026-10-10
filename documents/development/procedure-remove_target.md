# 移除 Target

## 适用范围

停止维护一个 Target。`<target>` 表示要移除的 Target 名称。

## 前置条件

- 移除后至少还有一个已登记的 Target；项目规范的参考 Target 必须已登记（S-05）。

## 步骤

1. `<target>` 是项目规范中的参考 Target 时，向使用者确认新的参考 Target，并在 `documents/AGENTS.md` 的项目信息中改写。
2. `instances/<target>/` 存在时，告知使用者其中保存着这个 Target 的存档与设置，按使用者的选择把它移到仓库之外或删除。使用者答复之前不删除；目录留在原处时 I-04 会报告。
3. 删除 `versions/<target>/`。
4. 从 `settings.gradle.kts` 的 `targets {}` 中删除 `register("<target>")`。
5. 删除 `instances.toml` 中的 `[targets."<target>".*]` 表；`local.toml` 中有同名表时，告知使用者删除（F-03）。
6. 从每个 `validations/*/validation.toml` 的 `targets` 中删除 `<target>`（V-08）。某项验证只涉及 `<target>` 时，向使用者确认删除这项验证，还是改为验证其他 Target。删除各验证中被 Git 忽略的临时实例 `validations/*/instance/<target>/`。
7. 只被 `<target>` 的构建脚本使用的本地输入，向使用者确认是否一并删除；删除时同步 `inputs.toml` 与 `documents/reference/dependency-*.md`，并告知使用者删除 `local.toml` 中 `[inputs]` 的对应路径。
8. 按 W-01 同步 `documents/AGENTS.md` 的 Target 表、`README.md` 及其各语言版本，以及 `documents/usage/` 中列出版本的文档，并在更新日志的下一个版本中说明不再支持该版本。
9. 执行 `.\gradlew.bat check`。

## 验收

- `.\gradlew.bat check` 成功，S-05、D-12、I-04 与 V-08 没有发现。
- 下面的命令没有输出；更新日志与移植文档记录历史，可以继续提到 `<target>`（D-10）：

  ```powershell
  git grep -n "<target>" -- settings.gradle.kts instances.toml inputs.toml versions validations documents/AGENTS.md "README*.md" documents/usage .github
  ```

- `instances/<target>/` 不存在。

## 禁止

- 保留 `versions/<target>/` 中的任何文件以备后用（G-02）；需要时从 Git 历史恢复。
- 未经使用者同意删除 `instances/<target>/` 中的存档。
