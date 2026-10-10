# 文档格式

本文列出 [AGENTS.md](../../AGENTS.md) 第 9 节三种文档写法的完整要求（D-09），以及第 8 节与第 9 节规定的固定写法在简体中文与英文中的对应名称。文档的语言按以下顺序确定：文件名带区域代号的译文按区域代号，`zh_*` 为简体中文，`en_*` 为英文；其他文档按项目规范中的"文档主语言"。区域代号写作 `<语言>_<地区>`，语言为小写字母，地区为大写字母，如 `zh_CN`、`en_US`（D-02）。其他语言的译文只按 D-13 检查二级标题数量。

## 写法

每个分类有默认写法，见 AGENTS.md 第 9 节的分类表；`procedure` 使用 Agent 写法，`plan` 与 `handoff` 也是。

| 写法 | 要求 |
| --- | --- |
| 玩家 | 称呼读者为"你"；先说明能做什么，再说明怎么做；开头注明适用的 Minecraft 版本与加载器；使用游戏内显示的名称、按键和菜单路径；配置项用表格列出文件、键、默认值、可选值与作用；命令写作 `/command <必填参数> [可选参数]` |
| 开发者 | 先写结论，再写细节；陈述事实与原因，不写空泛评价；命令从仓库根目录执行并放在代码块中；引用文件使用仓库相对路径链接；版本或加载器之间的差异用表格对照；不逐行复述源码，只解释源码表达不了的意图与约束 |
| Agent | 使用祈使句，一步一行，按执行顺序编号；路径和命令可以直接复制执行；占位符写作 `<name>` 并在首次出现时说明；验收条件必须能由命令结果或文件状态判定；引用规则时给出规则编号，不复述规则内容 |

三种写法都按同一方式写 Minecraft 版本与加载器：版本在前，正文写作 `1.20.1 Forge`、`26.3 NeoForge`，指代 Target 时写 Target 名称，如 `1.20.1-forge`；加载器自己的版本号写在加载器名称之后，如 `Forge 47.4.26`。

`procedure` 还可以包含"前置条件、禁止"两节；`plan` 与 `phase` 的步骤标注"待办、进行中、完成、放弃"之一，英文名称见下文。

## 固定小节

| 文档 | 简体中文 | 英文 |
| --- | --- | --- |
| `procedure` | 适用范围、步骤、验收 | Scope、Steps、Acceptance |
| `procedure` 的可选小节 | 前置条件、禁止 | Prerequisites、Prohibited |
| `plan`、`phase` | 目标、范围、步骤、结果 | Goal、Scope、Steps、Result |
| `adr` | 状态、背景、决策、后果 | Status、Context、Decision、Consequences |
| `research` 分类 | 问题、方法、发现、结论 | Question、Method、Findings、Conclusion |
| 验证目录的 `validation.md`（V-09） | 目标、运行、通过标准 | Goal、Run、Pass criteria |

小节必须是二级标题，名称与上表完全一致；可以另加其他小节。

## 状态与标签

| 用途 | 简体中文 | 英文 |
| --- | --- | --- |
| 决策记录的状态（D-06） | 提议中、已接受、已弃用、`已被 adr-<number> 取代` | Proposed、Accepted、Deprecated、`Superseded by adr-<number>` |
| `plan` 与 `phase` 的步骤状态（D-09） | 待办、进行中、完成、放弃 | To do、In progress、Done、Dropped |
| 图片替代文本的图号标签（D-08） | 图 | Figure |
| 更新日志的未发布标题（D-14） | `[未发布]` | `[Unreleased]` |
| 更新日志的改动类型（D-14） | 新增、变更、弃用、移除、修复、安全 | Added、Changed、Deprecated、Removed、Fixed、Security |

更新日志的标题两种语言都接受；其余写法按文档的语言选择一列，不混用。
