# 升级模板

## 适用范围

把派生项目的模板文件（G-08）升级到新的规范版本。

本规程使用以下占位符：

| 占位符 | 含义 |
| --- | --- |
| `<old>` | 项目当前的规范版本 |
| `<new>` | 目标规范版本 |
| `<template>` | 模板仓库在标签 `<new>` 的检出目录，位于仓库之外（G-07） |

## 前置条件

- 项目是 Git 仓库；不是时，经使用者同意后执行 `git init`，S-06 的对照、G-06 与持续集成都依赖 Git。
- 工作区中已有的改动保留；其中对模板文件的改动会被新版本覆盖，使用者已知晓。
- 已检出模板：`git clone --branch <new> https://github.com/RecRivenVI/Ravens-Mod-Template <template>`，并且没有在 `<template>` 中执行过构建，复制时不会带入构建输出。

## 步骤

1. 执行 `.\gradlew.bat check --continue` 并保存输出，作为升级前的状态。
2. 执行 `git -C <template> diff --name-only --no-renames --diff-filter=D <old> <new>`，列出模板在两个版本之间删除或移走的文件。其中位于 `<old>` 模板文件（G-08）范围内的，从项目中删除。位于 `documents/` 的，是已移入规范附件的规范文档或模板示例模组的文档：项目中存在同名文件时，用 `git -C <template> show <old>:<path>` 取出旧版本对照，`<path>` 为该文件路径；项目在其中写入的自己的内容按 AGENTS.md 第 9 节移进项目自己的文档，然后删除该文件，并把其他文档中指向它的链接改为规范附件中的对应文档（D-05）。
3. 项目中有 `gradle/conventions/` 时先删除它，再从 `<template>` 复制该目录。
4. 从 `<template>` 复制 `AGENTS.md`、`gradlew`、`gradlew.bat`、`gradle/wrapper/`、`gradle/gradle-daemon-jvm.properties` 与 `.github/workflows/specification.yml`，覆盖项目中的同名文件；Gradle 版本变化时，把 `NOTICE` 中的 Gradle 版本改为新版本。
5. 按 `<template>` 的 `settings.gradle.kts` 更新根目录 `settings.gradle.kts` 中引入模板插件的 `pluginManagement` 与 `plugins {}`，保留项目的 `targets {}`、`components {}` 与 `compliance {}`；按 [procedure-add_component](procedure-add_component.md) 第 2 步更新每个项目组件的 `settings.gradle.kts`。
6. 把 `<template>` 的 `gradle/libs.versions.toml` 中与 Minecraft 无关的工具条目写入项目的同名文件，覆盖同名条目，保留项目自己增加的条目与项目选定的 ModDevGradle、Loom 版本；把 `.github/workflows/` 中模板所用 Action 的版本改为 `<template>` 的 `check.yml` 中的版本（F-06）。
7. 对照 `<template>` 更新其他公共文件，保留项目自己增加的条目：`.gitignore` 的必需规则（L-05）、`.gitattributes`、`.editorconfig`，以及 `.github/workflows/check.yml` 中模板提供的步骤。
8. 执行 `git -C <template> diff -M <old> <new> -- AGENTS.md documents gradle/conventions/specification`，按其中规则与规程的变化修改项目；模板示例模组自己的文档（`documents/usage/`、`documents/release/` 与 `porting-*`）的变化不需要跟进。
9. 执行 `.\gradlew.bat spotlessApply`，再执行 `.\gradlew.bat check --continue`，逐条修正发现的问题；然后执行 `.\gradlew.bat verifyRelease`。
10. 在参考 Target `<reference>` 与其他每种加载器的一个 Target 上执行 `runClient` 与 `runServer`，例如 `.\gradlew.bat :version:<reference>:runClient`，`<reference>` 为项目规范中的参考 Target；按 [format-metadata](../reference/format-metadata.md) 的"运行端"一节判断产品是否正常加载。`runServer` 需要使用者已在 `local.toml` 中接受 EULA（I-02）；没有接受时请使用者决定，不代为写入（G-04），使用者不接受时跳过 `runServer`。

## 验收

- `AGENTS.md` 声明"规范版本：`<new>`"。
- `.\gradlew.bat check` 与 `.\gradlew.bat verifyRelease` 成功，S-01、S-06 与 F-06 没有发现：模板文件与 `<new>` 的哈希清单逐一一致，`gradle/conventions/` 中没有多余的文件，模板定义的工具版本与 `<new>` 相同。被 Git 忽略的构建输出不参与比较。
- 第 2 步要求删除的文件在项目中都不存在。
- `git ls-files -s gradlew` 输出的模式为 `100755`（C-06），`NOTICE` 中的 Gradle 版本与 `gradle/wrapper/gradle-wrapper.properties` 一致。
- 第 10 步的每个 Target 的客户端与独立服务端都能启动，产品的加载情况符合 [format-metadata](../reference/format-metadata.md) 的"运行端"一节的预期；使用者未接受 EULA 时，报告中写明独立服务端未验证。

## 禁止

- 修改复制进来的模板文件来让检查通过（G-08）；无法满足的规则向使用者报告。
- 为旧版本保留兼容代码、旧配置文件或别名（G-02）。
