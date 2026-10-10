# 升级模板

## 适用范围

把派生项目的模板文件（G-08）升级到新的规范版本。

`<template-origin>` 从根目录 `NOTICE` 的模板归属链接读取。

本规程使用以下占位符：

| 占位符 | 含义 |
| --- | --- |
| `<old>` | 项目当前的规范版本 |
| `<new>` | 目标规范版本 |
| `<template>` | 模板仓库在标签 `<new>` 的检出目录，位于仓库之外（G-07） |

## 前置条件

- 项目是 Git 仓库；不是时，经使用者同意后执行 `git init`，S-06 的对照、G-06 与持续集成都依赖 Git。
- 工作区没有未提交的改动，或使用者已确认可以覆盖。
- 已检出模板：`git clone --branch <new> <template-origin> <template>`，并且没有在 `<template>` 中执行过构建，复制时不会带入构建输出。

## 步骤

1. 执行 `.\gradlew.bat check --continue` 并保存输出，作为升级前的状态。
2. 删除项目中的 `components/configuration`、`components/conventions` 与 `components/compliance`，再从 `<template>` 复制这三个目录。
3. 从 `<template>` 复制 `AGENTS.md`、`CLAUDE.md`、`.rumdl.toml` 与 `.github/workflows/specification.yml`，覆盖项目中的同名文件。
4. 把 Gradle Wrapper 作为整体处理：`<template>` 的 `gradle/wrapper/gradle-wrapper.properties` 中的 Gradle 版本高于项目时，从 `<template>` 复制 `gradlew`、`gradlew.bat`、`gradle/wrapper/gradle-wrapper.jar` 与 `gradle/wrapper/gradle-wrapper.properties`，再把 `NOTICE` 中的 Gradle 版本改为新版本；不高于项目时保留项目的这四个文件。
5. 对照 `<template>` 更新公共文件，保留项目自己增加的条目：`gradle/libs.versions.toml` 中模板工具的版本、`gradle/gradle-daemon-jvm.properties`、`.gitignore` 的必需规则（L-05）、`.gitattributes`、`.editorconfig`，以及 `.github/workflows/check.yml` 中模板提供的步骤。
6. 执行 `git -C <template> diff <old> <new> -- AGENTS.md documents/`，按其中规则与规程的变化修改项目。
7. 执行 `.\gradlew.bat spotlessApply`，再执行 `.\gradlew.bat check --continue`，逐条修正发现的问题；然后执行 `.\gradlew.bat verifyRelease`。
8. 在参考 Target 上执行 `.\gradlew.bat :version:<reference>:runClient` 与 `.\gradlew.bat :version:<reference>:runServer`，`<reference>` 为项目规范中的参考 Target；按 [format-metadata](../reference/format-metadata.md) 的"运行端"一节判断产品是否正常加载。`runServer` 需要使用者已在 `local.toml` 中接受 EULA（I-02）；没有接受时请使用者决定，不代为写入（G-04），使用者不接受时跳过 `runServer`。

## 验收

- `AGENTS.md` 声明"规范版本：`<new>`"。
- `.\gradlew.bat check` 与 `.\gradlew.bat verifyRelease` 成功，S-01 与 S-06 没有发现：模板文件与 `<new>` 的哈希清单逐一一致，模板组件中没有多余的文件。被 Git 忽略的构建输出不参与比较。
- `git ls-files -s gradlew` 输出的模式为 `100755`（C-06），`NOTICE` 中的 Gradle 版本与 `gradle/wrapper/gradle-wrapper.properties` 一致。
- 参考 Target 的客户端与独立服务端都能启动，产品的加载情况符合 [format-metadata](../reference/format-metadata.md) 的"运行端"一节的预期；使用者未接受 EULA 时，报告中写明独立服务端未验证。

## 禁止

- 修改复制进来的模板文件来让检查通过（G-08）；无法满足的规则向使用者报告。
- 为旧版本保留兼容代码、旧配置文件或别名（G-02）。
