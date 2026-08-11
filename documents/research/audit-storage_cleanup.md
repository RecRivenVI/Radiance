# 存储清理调查

调查日期：2026-10-16。按使用者授权清理可再生客户端环境、构建副本并归档冷证据，保留当前脏工作区和必要输入，不启动游戏、不做性能实验、不提交或推送。使用者先选择保留证据原文件，随后批准无损归档，并追加 `Scratches/Radiance*` 范围与外部合计上限；以下保留这一执行顺序，不改写旧运行事实。

## 问题

清理前本轮实际范围为 315.106 GiB。目录累积了每次运行复制的 JAR、DLL、PDB、完整客户端与世界副本。旧有整体原地保留要求由本轮授权调整为冻结目录和有校验的证据归档。

大小为文件逻辑字节，不跟随重解析点，不是 NTFS 实际分配大小或同期卷可用空间变化。`RadianceFacesChunks` 含 26 个指向旧 MCVR 第三方库的目录联接；不重复统计或跟随删除其目标，所以实测 4.313 GiB，而不是外部评审跟随联接得到的约 8.4 GiB。旧 MCVR 仓库未修改。

## 方法

- 先检查本仓库 Java、Gradle 和原生进程，并核对共享 Gradle daemon 最近的项目目录；只发现其他项目任务，没有停止或操作它们。
- 先写 `D:/Workspaces/Artifacts/Radiance/storage-cleanup-20261016/manifest.json`，再按清单永久删除明确可再生内容。每次核对最终绝对路径、重解析边界、文件大小与修改时间；不确定项保留。任何审批阻塞不得更换方式绕过。
- 用 `git ls-files` 与必要未跟踪源码建立 1,818 个文件的清理前内容哈希，输入另记 SHA-256。保留 `.git/`、原生 `third_party/`、`instances/`、本地输入与当前构建输出，不手工删减其他构建目录。
- 每个调查使用 Windows 可直接打开的 `evidence-archive.zip`，格式为 ZIP Deflate；`evidence-archive-index.json` 记录每个原文件的相对路径、字节、修改时间、SHA-256 和条目名。只按 SHA-256 去重，同名或同大小不能证明重复。
- 每条索引都从 ZIP 完整读回比对 SHA-256，全部通过且原文件未变化后才删除该组原文件。失败时保留该组全部原文件并列为待决定；本轮 76 组均成功，未出现校验失败或审批阻塞。
- 只归档文本、源码脚本与画面捕获，不将 JAR、DLL、PDB、源码 ZIP、原始 JFR 或 Git 数据混入冷证据 ZIP。当前指针案例、待验案例、命名输入和在用 SDK 不参加冷归档。

## 发现

### 占用对照

以下为清理完成、完整检查通过后的快照；最后的目录复核写入同目录的 `storage-final.json`。本轮范围净减少 285.949 GiB。

| 范围 | 清理前 GiB | 清理后 GiB |
| --- | ---: | ---: |
| 仓库全部文件 | 284.839 | 21.687 |
| 仓库计入上限部分，不含 `.git/` 与原生 `third_party/` | 278.004 | 14.851 |
| 冻结的 `run/`、`dev/`、`logs/` | 235.929 | 1.918 |
| `build/manual-acceptance/` | 29.141 | 0.000 |
| 原生组件 `build/` | 5.039 | 5.039 |
| 验证实例与结果 | 1.950 | 1.950 |
| 外部 `Artifacts/Radiance/` | 25.954 | 7.407 |
| `Scratches/Radiance*` | 4.313 | 0.064 |
| 外部合计 | 30.267 | 7.471 |
| 本轮范围总计 | 315.106 | 29.158 |

仓库计入上限部分低于 16 GiB；冻结目录低于 2 GiB；验证生成目录低于 4 GiB；外部合计低于 8 GiB 硬上限。外部仍高于 6 GiB 软上限，在用 SDK 为 1.719 GiB，扣除 SDK 后为 5.750 GiB。按使用者明确决定保留 SDK，不为达标删减构建依赖。

### 清理和保留

- 首轮删除旧客户端的模组、库、资源、natives、缓存、世界副本与重复二进制；没有删除正式世界、操作 Prism 或清理日常实例。
- 后续复核并清理运行资源副本：噪声与水纹理、管线缓存，以及 620 份 `radiance/shaders/world/ray_tracing/` 下的内置管线 ZIP。其 SHA-256 写入 `builtin-shader-resource-delete-plan.json`；26 份独立位于证据位置的管线 ZIP 与全部源码快照保留，没有为了容量继续删证据。
- 4 份 JFR JSON 导出与同目录原始 JFR 的事件类型计数相符，记录 JSON SHA-256、原始 JFR SHA-256 与重导出命令后删除。原始 JFR 保持可直接读取。历史 JSON 的 CRCRLF 与工具格式不承诺逐字节重现，重导出范围是相同事件和完整可用栈；清单为 `jfr-derived-export-index.json`。
- 110,215 个冷证据文件归档前为 10.663 GiB，ZIP 共 1.446 GiB。实际压缩率按每组记录，未采用评审的估算值冒充实际结果。
- 5 个命名输入保留，包括 Newport 城市 ZIP、Create、Sable、Aeronautics 和私有 Aftermath SDK。当前投放的备份 JAR 为 `6a26474b0c2e057a33ceacfd520034eb00de5561fd22e30a38e130e2a21388f6`，核心 DLL 为 `6704b26420419a99990fae0c7dc3e4febfd72883f9eb2eb9639421f5ed6e863c`。没有读取或改动 Prism 实例来重新验证投放。
- 当前指针与待验案例不做冷归档，特别是 `menu-line-fix-20261015-174944` 和 `static-scene-cache-20261015-160109`。此保护在追加授权后执行，不回造首轮按原授权删除的可再生客户端副本。
- 在用 SDK `D:/Workspaces/Artifacts/Radiance/dependency-update-20261014/vulkan-sdk/` 被 `components/native_renderer/build/v/CMakeCache.txt` 的 Vulkan 头文件、`shaderc_combined.lib`、`glslc`、`glslangValidator`、`vulkan-1.lib` 引用，完整保留。旧 `ci-fix-20261010-153325/sdk-copy-only/` 与两个安装包不被当前构建引用，已删除。
- 没有发现真实 GPU 或进程转储；不把跟踪日志、JFR 或画面输入称为 GPU crash dump。没有对应转储的旧 PDB 在记录身份与现有源码快照/清单后精简；明确冻结的当前和待验案例仍按保持原文件处理。
- 验证目前各有一套 Target/角色实例，没有需要移除的旧实例树或旧结果树，继续保留最近一套。没有启动验证客户端。

### 证据迁移和文档引用

| 原位置 | 新位置 | 处理 |
| --- | --- | --- |
| `build/manual-acceptance/` | `D:/Workspaces/Artifacts/Radiance/history-archive/manual-acceptance-20260924/` | 先移保留文件再删除原目录，冷证据随后进入该目录的 ZIP，源码 ZIP 等原文件继续保留 |
| `D:/Workspaces/Artifacts/Radiance/migration-20261009-1800/before/` | `D:/Workspaces/Artifacts/Radiance/history-archive/migration-before-20261009/` | 两个原 HEAD ZIP、15 份子模块源码归档通过 ZIP 完整性校验，引用的 README、文本与源码脚本迁移 |

迁移前备份的解包源码和构建副本已移除；原位置仅剩三份第三方 `.git` 数据，约 0.029 GiB，按 Git 保留边界未删除或移动引用。迁移调查根的其他证据仍在。`cleanup-receipt.json` 逐项记录原路径、新路径与字节数。

`documents/` 共更新 97 处引用，其中 95 处归档文件或虚拟目录改为 `归档路径!条目名`，目录前缀以 `/` 结尾。该写法用于检索 ZIP，不是可直接交给操作系统执行的文件路径；脚本需先按索引提取。调查根目录仍含源码 ZIP、JAR、原始 JFR 和索引，所以保留其有效目录引用。另 2 处 README 引用改为迁移存档中的原文件路径。完整替换前后表在 `document-archive-references.json`，下表列出修改范围；仅更新取证位置，不改写历史测试结论或证据文件内部的旧路径。

| 文档 | 引用改动数 |
| --- | ---: |
| `documents/project/status-current.md` | 57 |
| `documents/research/investigation-chunk_loading_lock_contention.md` | 5 |
| `documents/research/investigation-chunk_loading_lock_fix.md` | 5 |
| `documents/research/investigation-local_gpu_capture.md` | 7 |
| `documents/research/investigation-menu_fade_and_sublevel_lines.md` | 3 |
| `documents/research/investigation-pt_visual_and_performance_investigations.md` | 2 |
| `documents/research/investigation-raster_parity_and_ponder_retirement.md` | 6 |
| `documents/research/investigation-render_call_inventory_and_benchmarks.md` | 5 |
| `documents/research/investigation-vram_lifecycle_handoff.md` | 7 |

### 待使用者决定

- 文档引用的原生构建仍有 8 个身份，超过 5 套目标。优先保留当前投放、最近反馈、待验与当前诊断的 5 个身份；超出的 `dedb2b00…`、`f9ba4c1e…`、`1e511ca3…` 因文档仍明确引用而没有擅删。完整 SHA-256、路径、状态和 JAR 封装边界在 `retained-build-identities.json`。
- `unknown-retained.json` 保留初次待确认项目；其中已归档或被后续明确判为资源副本的项目，以最终清单的处置为准。最终保留的 432 项原文件共 0.299 GiB，包含应保留的原始 JFR、冻结案例和不能确认可再生的内容，逐项见 `unknown-final.json`；不将这批内容重新当作可删除任务。
- 迁移备份中受保护的第三方 Git 数据、当前与待验案例原文件不按普通冷证据删减；当前案例内 PDB 的后续收敛等待验收结束。
- `D:/Workspaces/Artifacts/MCVRSceneReplay/` 为 16.530 GiB，来自本线程 2026-09-24 的一次性捕获与回放，已由现有 CPU 热点研究引用。本次只统计，未修改或删除，未纳入本次释放量和上限。

### 自动验证

首次执行 `spotlessApply check verifyCompliance --strict` 时，S-07 报两份 AGENTS 合计 33,617 字节，超过 32 KiB；其余已执行构建、测试与 Markdown 检查未报错。仅精简项目规范的重复表述，保留数值和约束，将归档/PDB 细则链接到本记录，模板文件未修改。修正后重新运行同一检查，并用临时 init 脚本关闭 JVM 测试的增量与结果缓存，确认测试实际执行，不用旧测试结果代替删除后的验证。最终退出码与报告记录在清单和 `verification-final.log`。

修正后的完整检查退出码为 0，148 项任务中 51 项实际执行。GAME 257 项、探针 26 项通过；bootstrap 11 项通过、2 项因未启用 GPU 运行入口跳过。模板插件 162 项 JVM 测试通过；原生 CPU CTest 66/66、诊断采集器 2/2、采集辅助 2/2 通过，Python 输入/加载/清单测试分别 2、12、8 项通过；发行 JAR、JNI 与开发产物检查通过。严格合规失败 0、警告 0；两份 AGENTS 合计 32,754 字节。95 处归档引用均可定位到 ZIP 条目或目录前缀，另 2 处迁移后的原文件也存在。具体结果见 `verification-results.json` 与 `archive-reference-validation.json`。

构建使用仍保留的 SDK；不启动客户端、GPU 性能测试或外部实例。

## 结论

本轮清理和归档不改变产品源码、渲染语义或现有验收结论。已将可再生客户端副本与可回查证据分开，容量状态按实际统计记录；在用 SDK 和超过 5 套的文档引用构建保持明确边界，不以删除唯一证据换取表面达标。
