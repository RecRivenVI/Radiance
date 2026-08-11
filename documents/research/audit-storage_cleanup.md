# 存储清理调查

调查日期：2026-10-16。按使用者授权清理可再生客户端环境、构建副本并归档冷证据，保留当前脏工作区和必要输入，不启动游戏、不做性能实验、不提交或推送。使用者先选择保留证据原文件，随后批准无损归档，并追加 `Scratches/Radiance*` 范围与外部合计上限；以下保留这一执行顺序，不改写旧运行事实。

2026-10-29，使用者批准长期退役规则，取代本记录原有“文档引用即保留”和逐轮放宽容量的做法。当前规则见本文末尾的[长期保留与退役](#长期保留与退役)，历史清理数字不作为当前占用。

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

### 当时保留的待定项

- 当时文档引用的原生构建有 8 个身份，额外三个未删。2026-10-29 的用户决定已关闭“仅因文档引用必须保留”的待定理由，旧身份仍可从 `retained-build-identities.json` 回查，实际退役另记清单。
- `unknown-retained.json` 保留初次待确认项目；其中已归档或被后续明确判为资源副本的项目，以最终清单的处置为准。最终保留的 432 项原文件共 0.299 GiB，包含应保留的原始 JFR、冻结案例和不能确认可再生的内容，逐项见 `unknown-final.json`；不将这批内容重新当作可删除任务。
- 迁移备份中受保护的第三方 Git 数据、当前与待验案例原文件不按普通冷证据删减；当前案例内 PDB 的后续收敛等待验收结束。
- `D:/Workspaces/Artifacts/MCVRSceneReplay/` 为 16.530 GiB，来自本线程 2026-09-24 的一次性捕获与回放，已由现有 CPU 热点研究引用。本次只统计，未修改或删除，未纳入本次释放量和上限。

### 自动验证

首次执行 `spotlessApply check verifyCompliance --strict` 时，S-07 报两份 AGENTS 合计 33,617 字节，超过 32 KiB；其余已执行构建、测试与 Markdown 检查未报错。仅精简项目规范的重复表述，保留数值和约束，将归档/PDB 细则链接到本记录，模板文件未修改。修正后重新运行同一检查，并用临时 init 脚本关闭 JVM 测试的增量与结果缓存，确认测试实际执行，不用旧测试结果代替删除后的验证。最终退出码与报告记录在清单和 `verification-final.log`。

修正后的完整检查退出码为 0，148 项任务中 51 项实际执行。GAME 257 项、探针 26 项通过；bootstrap 11 项通过、2 项因未启用 GPU 运行入口跳过。模板插件 162 项 JVM 测试通过；原生 CPU CTest 66/66、诊断采集器 2/2、采集辅助 2/2 通过，Python 输入/加载/清单测试分别 2、12、8 项通过；发行 JAR、JNI 与开发产物检查通过。严格合规失败 0、警告 0；两份 AGENTS 合计 32,754 字节。95 处归档引用均可定位到 ZIP 条目或目录前缀，另 2 处迁移后的原文件也存在。具体结果见 `verification-results.json` 与 `archive-reference-validation.json`。

构建使用仍保留的 SDK；不启动客户端、GPU 性能测试或外部实例。

## 结论

本轮清理和归档不改变产品源码、渲染语义或现有验收结论。已将可再生客户端副本与可回查证据分开，容量状态按实际统计记录；在用 SDK 和超过 5 套的文档引用构建保持明确边界，不以删除唯一证据换取表面达标。

### 长期保留与退役

以下为使用者于 2026-10-29 批准的现行规则，与项目规范 X-12 一同生效：

- 计入预算的仓库工作区排除根 `.git/` 和 `components/native_renderer/third_party/`，软上限 20 GiB、硬上限 25 GiB，日常目标为 12～14 GiB。报告同时列出未排除这些目录的实际总量，不混淆两种口径。
- 验证实例与结果合计仍为 8 GiB；`Artifacts/Radiance` 与 `Scratches/Radiance*` 合计仍为软 6 GiB、硬 8 GiB；冻结的 `run/`、`dev/`、`logs/` 合计仍不超过 2 GiB。
- 完整候选仅按实际角色保留：当前候选、Prism 在用版本、最近人工验收通过版本、当前性能基线、仍在追查的故障版本。角色重合时共用一套，不为每份报告再复制完整 JAR。PDB 等符号仅给当前候选及追查中的崩溃候选保留。
- 其他历史二进制在记录 SHA-256、对应源码快照及其校验值后移入回收站。旧报告不改写；退役清单负责关联原路径、哈希、源码位置、理由与回收结果。确切源码关联尚未建立的构建先保留并如实列明，不把当前源码假装成历史源码。
- 验收通过或任务结束后，验证保留结论、关键截图及当前性能基线的一套原始记录；完成结论后回收可再生实例、模组副本和缓存。地图、配置输入、仍在使用的研究实例不清。未完成故障的转储及其必要配套资料属于在查证据，不按普通历史测量淘汰。
- 源码、未提交改动、独立 Extra 仓库、Prism 实例、Native 增量编译缓存和仍在用的 SDK 保持原地。禁止为了配额反复删除并重建 Native 缓存。本次不改变打包方式。
- 保留实例的符号链接目标属于实际运行依赖，清理候选前一并核对。任务结束后，经明确确认退役的实例可回收运行环境，原位置的存档、配置及资源包输入继续保留。
- 构建和启动前检查占用；超过软上限先按上述生命周期自主清理。只有清理后仍超限，或需要删除结论依赖且无法重建的唯一证据，才请用户决定。所有删除移入回收站，不清空回收站、Git 引用或 reflog。

本轮清单与逐项回执位于 `D:/Workspaces/Artifacts/Radiance/storage-retirement-20261029/`；历史报告中已退役的二进制通过该目录的 `retired.json` 关联源码，不再更新旧报告的路径。

### 2026-10-29 清理记录

清理在城市重测和诊断回归结束后进行，没有打断客户端或启动新客户端。开始时仓库总量 28.713 GiB，计入预算 21.243 GiB，外部合计 5.999 GiB。先写文件清单与源文件／输入保护哈希，再记录二进制 SHA-256 和可定位的源码快照；源码 ZIP 逐一读回校验。旧 CPU 调查 DLL 的一处缺失源码通过历史哈希从既存快照恢复为覆盖包，660 个 Native 文件均与原清单相符，没有用当前源码冒充旧源码。

已请求回收 65 个过期二进制，共 8.919 GiB，以及 91 份旧 JFR 和 52 份旧逐帧记录，共 0.689 GiB。结论、截图、源码快照、地图配置和本轮修正设置后的 `retest-20261029` 基线采集集保留。回收后用户明确告知手动清空了回收站；回执同时保留 API 请求和这一用户确认，不把事后找不到记录归因为接口故障。

初次清理漏算了三处研究实例符号链接依赖，检查因此失败。用户随后明确选择退役旧阶段 0～3、128 号 `closeout-manual` 和 Sable handoff K 的运行环境。18 处模组／运行库／缓存目录已通过系统 Windows PowerShell 的回收接口处理并核对回收元数据，873 个存档、配置和其余保留文件逐一哈希不变。旧结果中一处指向退役 DLL 的符号链接单独留痕回收；没有修改旧报告引用、Prism、日常实例或 Extra 仓库。

正式保留的本体为 E 候选 `ca9eaf378b91…`，同时承担当前候选、Prism 在用及当前性能基线角色；最近人工验收通过的水材质候选为 `8b2daf7c1cb2…`。E 的匹配 PDB `e7fe940a0d37…` 保留，其他冻结符号退役。当前构建中的 Native 增量缓存及符号继续保留，不与历史候选符号副本混同。

另有六个中间 JAR 尚未建立确切源码关联，共约 0.923 GiB，及两个旧诊断 DLL 约 0.092 GiB，暂留待溯源，不因空间足够就伪造对应关系。它们不是继续保留所有文档引用构建的理由。完整路径和身份在 `retirement-plan.json` 与最终保留清单中，不需要为其放宽容量。

清理后的实际占用、保护哈希复核和最终构建结果以同目录的 `inventory-final.json`、`summary-final.json`、`operation-status.json` 和 `verification-final.log` 为准。完整检查重建了之前已经删掉的 Native 增量输出，因此净减少量小于退役文件总量；新生成的缓存留存，不再反复删除。

最终 `spotlessApply check verifyRelease verifyCompliance --strict` 成功：GAME 314 项、探针 51 项通过，bootstrap 13 项中 2 项 GPU 条件测试跳过，Native CPU 79/79，严格合规失败 0、警告 0，发行包 156 个必需条目核对通过。没有再启动客户端。验证完成后，将 Target 的解包目录及 bootstrap 资源中的 288 份 Native 载荷副本回收，共 525.48 MiB；逐文件与仍保留的 `native_renderer.jar` 内容比对一致。只有复制出来的载荷被移除，Native 编译／安装缓存、已验证 JAR 及 bootstrap 非载荷资源保留；这些副本由既有 `prepareRuntime` 与 `processBootstrapResources` 任务再生，没有改变打包方式。
