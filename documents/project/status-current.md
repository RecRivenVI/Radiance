# 当前项目状态

截至 2026-10-14，结构与构建迁移、模板对齐及 CI 收尾已经完成，工作区模板为 2.0.2；本轮更新正式版依赖并升级模板，双向几何后的区块加载掉帧调查仍按使用者决定暂停。迁移的源基线为 Radiance `585c561fd3711aedcdc243715b94145d630bb1ad`、MCVR `81c08145fc8d0f7ec12dedc0f4b303a8098822e6`。不从固定提交时间推断历史验收顺序。

## 正式版依赖更新

2026-10-14 按正式发布选择依赖，未更改 Minecraft 版本、产品渲染逻辑或着色器。精确事实来源与内嵌依赖关系见[渲染与兼容依赖](../reference/dependency-rendering.md)。

| 项目 | 更新前 | 本次版本 |
| --- | --- | --- |
| NeoForge | 21.1.256 | 21.1.259，Radiance 自身支持下限仍为 21.1.62 |
| Sable | 2.0.5 | 2.0.6，包含其对应的 sable_rapier |
| Loom | 1.18.3 | 1.18.4 |
| SnakeYAML | 2.6 | 2.7 |
| ASM / Gson | 9.10.1 / 2.13.2 | 9.11 / 2.14.0 |
| Vulkan SDK | 1.4.341.1 | 1.4.363.0 |
| volk / Vulkan-Headers | SDK 1.4.357.0 / 1.4.357 | SDK 1.4.363.0 / 1.4.365 |

Streamline 2.14.1、DLSS 310.9.1、XeSS 3.0.2、NRD 4.17.3 及其余有正式版本的原生依赖已是核对时的最新正式版。NRD 4.18.0 与 Loom 1.19 的预发布不纳入；没有正式发布标签的 stb 保留既有固定提交。Create 6.0.10 与 Create Aeronautics 1.3.2 未更新，Veil 保持 Sable 2.0.6 内嵌的 4.3.2，不混装独立 4.5.2。

FidelityFX 官方 2.3.0 的完整源树未提供本项目使用的 `ffx_api_vk` 与 Vulkan 后端，不能直接替换现有 Vulkan 分支；保留该固定提交，没有通过关闭 FSR 规避兼容问题。正式版范围和后端缺口的核对记录保存在本轮证据目录。

升级同步修正两项构建与验证问题：CMake 重新配置时刷新旧 SDK 缓存；Sable 契约测试从 Target 读取版本，并仍验证实际加载 JAR 与字段契约。`local.toml` 仅更新已校验的 Sable 输入路径，未写 EULA。

本轮 `spotlessApply`、`check verifyRelease` 通过。GAME 250/250、probe 26/26（含新增采集器包装回归）、bootstrap 11 项通过与 2 项显式硬件引导对照跳过；工具 inventory 4/4、benchmark 7/7、Python inventory 8 项及诊断采集器 2/2 通过。原生非 GPU CTest 62/62、GPU CTest 15/15 通过；JNI 头文件、Maven GAME 边界、发行元数据、154 个必需条目及探针排除检查通过。

首次两项探针运行到达标题界面并正常退出，但原生采集器资源缺失，诊断功能不能算通过。升级前留存日志中也有相同失败。已将工具组件 JAR 的采集器资源明确纳入 probe 资源，保留产品包排除边界，并增加包装回归；`check-release-2.log` 记录修正后的完整检查。

修正后的 `run-diagnostics-fixed` 与 `run-benchmark-smoke-fixed` 均记录原生采集器实际挂接（`flags=8`，只采生命周期）、`AUDIT_SMOKE title-ready`、正常停止和 audit ledger 关闭；基准启动伴随组件为 `active=true`。这两项只验证工具加载，不产生性能结果。

日常客户端与 Create/Sable/Aeronautics 组合均完成启动和正常退出，无新增崩溃报告。组合日志仍有 7 条 Veil 着色器布局或 uniform 不支持报告，与升级前日志逐项相同；`veil-startup-comparison.json` 保留比较结果。这些是既有兼容边界，启动成功不代表所有 Veil 视觉路径通过。

本轮机器证据位于 `D:/Workspaces/Artifacts/Radiance/dependency-update-20261014/`。`check-release-1.log` 与 `check-release-2.log` 保留修正前后的完整构建，`gpu-test.log` 记录 GPU 测试，`junit-results-final.json` 记录修正后的 Java 用例统计。Vulkan SDK 使用仓库外 copy-only 安装，未修改系统环境、注册图层或驱动；构建环境显式指向此 SDK。

发行 JAR 的 `runPackagedClient` 完成安装式启动和正常退出，模组列表为 Minecraft 1.21.1、NeoForge 21.1.259 与 Radiance，无新增崩溃报告。本轮各入口主音量为 0，实际加载文件记录在各运行目录的 `native-loaded.json`；最终产物与源码清单分别为 `artifact-identity-final.json` 和 `source-manifest-final.json`。服务器因 `local.toml` 未接受 EULA 不运行；不执行性能实验，不声称视觉、世界流程或历史设备丢失根因已通过。公开二进制许可门禁保持关闭。

## 模板 2.0.2 升级

2026-10-14 由官方标签降序首项确定 2.0.2，克隆提交为 `b598a41405c57c2d29c15cda3d4fc937426b233e`。按新克隆的升级规程从 2.0.0 整体替换模板文件，旧 `gradle/conventions/` 通过 Windows 回收站移除；项目依赖升级、必要新文件与暂存区保持原状，不操作旧 MCVR 仓库。

新版将模板实现模块和 Java 包从 `target` 改为 `platform`，项目 Target 脚本同步两处类型引用；对外插件 ID 与组件登记方式保持一致。实例目录说明统一采用 `<variant>`，根目录格式检查增加 `.yaml`，本地模组元数据识别跳过未声明模组 ID 的占位文件；Gradle、模板工具和 Action 版本没有变化。项目公共配置中的补充项与本地设置保留，NOTICE 仅更新模板来源版本。

升级前 `check --continue` 成功，严格合规失败 0、警告 0。本轮证据位于 `D:/Workspaces/Artifacts/Radiance/template-upgrade-20261014-2.0.2/`；该目录中的源码指纹与暂存条目记录用于确认已有改动没有丢失。

升级后 `spotlessApply`、`check --continue`、`verifyRelease` 与 `verifyCompliance --strict` 通过；规范 2.0.2 的严格检查失败 0、警告 0，与升级前一致。123 个受保护模板文件与新标签逐字节相同，发行包的元数据、154 个必需条目与探针排除通过；原生非 GPU CTest 62/62 通过，其余检查包含已有任务的有效增量结果。

参考 Target 的 `runClient` 实际加载 Radiance 与 NeoForge 21.1.259，线程快照位于 `TitleScreen.render`，主音量为 0。确认后向命令行包含本仓库路径的对应 Java 进程请求正常关窗，日志记录 `Stopping!`，退出码 0，无新增崩溃报告。服务器因 `local.toml` 未接受 EULA 跳过；世界流程、视觉效果与性能没有重新验收。HEAD 与暂存条目不变；已有产品源码、着色器及依赖升级没有丢失或改写。

## 已保留实现

- Minecraft 1.21.1 NeoForge；SERVICE 早期 Vulkan 窗口、GAME 嵌套发现与错误生命周期保持原逻辑。
- acquire/recreate 状态修复、Streamline 缺席回退、DLSS 失败不消费无效输出、首个错误与设备丢失状态分离、JNI/worker 异常边界、保存与 native close 保持原证据范围。
- 默认世界双向几何；来源 cull/winding 继续捕获，`RADIANCE_WORLD_TWO_SIDED=0` 的可恢复实现仍保留。翘曲反向四边形采用一致拆分与配对所有权。
- 外部区段背压、代际保护、有界纹理编号、延迟上传身份、GPU 资源退休与缓存界限保持不变。
- 材质染色采用材质颜色覆盖；保留优先级、第一人称、普通半透明和物理透射的独立语义。
- 持久模型与高视距优化保留；历史性能数据不是本次迁移重新运行的结果。
- Ponder PT 已封存，当前 Ponder/UI3D 走默认光栅语义的 Vulkan 翻译；不恢复截图或 PT 实验。
- GAME Maven 与本地完整发行包用途分开，诊断改为验证专用 `radiance_probe`。

## 仍未闭合

- 真实 GPU 设备丢失根因、完整 G3、长期稳定性与生成帧动态视觉验收仍未知或待验；历史世界保存与 native close 的成功不能替代根因证明。
- 新的成像/完整 PT 参与分离、反射政策与未来单向贴图规则处于设计阶段；迁移不实现它们。
- 原生云光照色、粒子 packed-light 发光推导、DLSS viewport 释放失败后的身份、viewport 计数耗尽与遗留选项读写，保留静态审查所发现的缺口，不借迁移修改。
- labPBR 掠射角、云重写、部分结构图解/连接绳/反射细节和跨模式视觉等价保留各研究的状态。
- NVIDIA 等第三方运行库的公开分发授权未确认；本地验收和源码迁移不代表许可兼容。
- 所有性能实验按用户决定暂停；2026-10-10 的区块加载退化调查授权已于 2026-10-12 暂停，不代表恢复全部优化或压力矩阵。

## 历史证据

原始 `docs/`、relay、已完成审计和两份开发台账保存在 `D:/Workspaces/Artifacts/Radiance/history-archive/Radiance/docs/` 与 `D:/Workspaces/Artifacts/Radiance/history-archive/MCVR/docs/`，保持原文、日期和目录。仓库 Git 历史仍保留原文件。

原 `run/inventory-20261003/` 与其他 ignored 运行证据保持原地；它们不进入源码提交，也不是临时垃圾。

## 本次迁移验证

2026-10-09 首轮迁移使用固定标签 1.1.0 的仓库外克隆，模板提交为 `b989bdfb84e2d9308d47fdd6d0360241b0ae5709`。81 个受保护模板文件逐字节一致；未复制三个示例模组专用文档，模板 MIT 原文与 NOTICE 归属保留。十四个原生子模块保持 URL 与固定提交。

### 构建与自动验证

- 原 HEAD 的两个仓库由 `git archive HEAD` 导出后，使用原构建方式成功生成完整 `distributedJar`；RelWithDebInfo 与迁移后保持一致。
- GAME 250/250、probe 25/25、工具 inventory 4/4 与 benchmark 7/7 通过。bootstrap 11 项通过，2 项要求显式 GPU 环境的独立画面对照/窗口交接测试跳过；不把客户端启动替代它们。
- 原生 CTest 62/62 无 GPU 测试、15/15 GPU 测试通过；诊断采集器 2/2、Python inventory 8 项与离线绘制盘点测试通过。21 个 JNI 头文件、Maven GAME 边界和完整发行包结构验证通过。
- 首轮格式修正与 `verifyRelease` 已执行；当时 `check` 受模板 1.1.0 对四个二进制云噪声 `.raw` 的 C-01 误判阻塞，没有修改检查器或噪声绕过。该结果保留在首轮证据中；当前收尾按下节的新版检查结果判断。
- 首次默认配置缓存运行失败，原因是项目自身任务捕获脚本对象；已关闭配置缓存，构建缓存保留。首轮迁移当时尚未执行 Windows CI；后续前置条件修正与实际托管结果见本文末尾。

### 原包与迁移包比较

引导层 29 个类忽略行号后全部一致；GAME 605 个类中 604 个一致。唯一差异是 `ShaderPackScreen$ShaderPackListWidget` 的编译器生成桥接方法：用原始未修改源码在新的编译环境重编译，得到与迁移版相同的方法。不能把这项环境差异写成全部字节码一致。

225 个 PT 包内文件逐项比较，仅格式与命名空间结束注释改变；普通资源与着色器的每处差异、许可命名、元数据、原生重链接、索引哈希和包结构变化均有清单。原生库 204 个导出符号集合一致；PDB 路径、编译与链接来源不同，DLL 不逐字节相同。导出符号一致不等于完整运行等价。

### 客户端与验证运行

日常 `runClient` 与 `runPackagedClient` 分别到达标题界面，模组列表含 Radiance，均正常退出，无对应崩溃报告。日常窗口使用 `local.toml` 的 2560×1440；原生打包运行保留其默认 854×480。主音量均为 0。

`smoke-render_diagnostics` 与 `conformance-benchmark` 均记录 `AUDIT_SMOKE title-ready`、正常停止与 audit ledger 关闭；基准启动伴随组件实际 active=true。本次只验证启动与加载，不进行世界或性能实验。

首次诊断启动曾因跨加载域的 `PortableBenchmarkMod` 缺失而失败，已将基准入口辅助类一起归入 probe，逻辑不变，重测通过。首次基准启动曾因 261 字符的路径导致原生无法打开已提取的 shader，已缩短验证目录并重测通过；完整失败实例保留于仓库外，不掩去失败历史。

日常、发行 JAR、构建安装与实际提取/加载的 `core.dll` 已校验内容一致；JAR、DLL 完整哈希见下述机器清单。未进入世界，不宣称视觉、完整游戏流程、GPU 故障根因或长期稳定性通过。

打包客户端实际启动的 JAR 哈希为 `FC8A92524CF4FAC1E6793ECC9BC3AF2AA66D25D0E9629F5060596421130FEF87`。收尾重新执行构建会重打两个 PT ZIP，并修正 NOTICE 中的许可路径文字，因此最终 JAR 容器哈希变化。逐条比对差异仅为两个 ZIP 容器及对应 `runtime.index`、NOTICE，以及内嵌 GAME 中的 NOTICE 与 `game.sha256`；ZIP 内全部文件字节相同，类与 DLL 也相同。实际运行包和最终构建包的身份分别记录，不声称它们是同一个二进制文件。

### 证据索引

本次证据根为 `D:/Workspaces/Artifacts/Radiance/migration-20261009-1800/`，不是可随手删除的临时备份。

| 证据 | 内容 |
| --- | --- |
| `before/Radiance-585c561.zip`、`before/MCVR-81c0814.zip` | 迁移前 HEAD 的归档 |
| `before-build-short.log`、`check-final.log`、`check-closeout.log` | 原构建、自动测试与最终检查；早期失败日志另行保留 |
| `artifact-identity-final.json`、`source-manifest-final.json` | 最终产物及实际加载身份、完整源码候选范围与原始字节哈希 |
| `package-resource-comparison-final.json`、`shader-pack-content-comparison-final.json` | 每处包资源差异及逐文件 shader 比较 |
| `bytecode-bootstrap-final.log`、`bytecode-game-final.log`、`bytecode-source-control-result.log` | 忽略行号的字节码比较与原源码控制实验 |
| `native-export-comparison-final.json` | 原生完整导出符号集合比较 |
| `run-client.log`、`run-packaged-client.log` | 日常与发行安装入口运行 |
| `run-validation-diagnostics-retry.log`、`run-validation-benchmark-retry.log` | 两项验证重测通过；首次失败日志保留 |
| `template-verification-final.json`、`template-name-search-final.json`、`archive-verification.json` | 模板完整性、名称残留搜索及 243 份历史文档原文归档 |
| `recycled-paths*.json` | 迁移移除文件的回收站回执 |

## 模板升级收尾

2026-10-10 从远端标签降序列表的首项 1.2.1 克隆升级，提交为 `b849965bf8a0ac4f525d24106b68649c4e15e9f9`。使用者明确授权覆盖未提交的模板文件；项目迁移改动与原生子模块保留，MCVR 仓库未修改。

Gradle Wrapper 9.8.1、版本目录与守护进程 JVM 要求和新模板一致，保留原文件；公共配置保留 RAW/SPIR-V 二进制属性、Windows CI 与本地输入规则。CI 只上传合规报告，不上传发行包或运行库；模板升级当时尚未执行托管 CI；后续实际结果见本文末尾。

`clangFormatCheck` 在执行时先查 PATH，Windows 再调用 Visual Studio 的 `vswhere`；查找失败会说明安装“C++ Clang 工具”或补 PATH。此项的配置缓存验证独立于整条仍关闭配置缓存的构建链。

本轮证据目录为 `D:/Workspaces/Artifacts/Radiance/template-upgrade-20261010-1_2_1/`。`pre-upgrade-check.log` 保留升级前 RAW 误判与 clang-format 缺失的失败；`product-before.json` 和 `upgrade-audit.json` 用原始字节 SHA-256 检查产品源码与资源不变，`recycled-paths.json` 记录按规程替换的三个模板组件目录。

本轮 `spotlessApply` 与 PATH 中没有 clang-format 的 `check verifyRelease --continue` 成功；十二组格式检查实际执行，使用 `C:/Program Files/Microsoft Visual Studio/18/Community/VC/Tools/Llvm/x64/bin/clang-format.exe`，版本 22.1.3。原生非 GPU CTest 62/62 与诊断采集器 2/2 本轮执行通过；其他 Gradle 检查按任务输入复用或执行，不把缓存结果写成本轮全部重跑。没有重跑 GPU 与世界性能测试。

`verifyCompliance --strict` 报告 1.2.1、失败 0、警告 0，检查 1,765 个文件。81 个模板文件与新克隆逐字节一致，1,553 个产品源码及资源输入相对升级前哈希不变，四个 RAW 纹理保持原始字节；P-07 残留扫描为空。

工具查找验证覆盖无 PATH 时回退、PATH 优先与两处缺失时的预期失败。配置缓存第一次保存、第二次复用成功；在复用配置后移除工具路径，任务仍正确报告缺失，证明没有沿用缓存中的工具位置。全局配置缓存仍按首轮迁移结论关闭。

日常 `runClient` 本轮到达标题界面，日志列出 Radiance 0.1.5-alpha，主音量 0，正常退出且没有崩溃报告；进程 2344 的操作仅限标题界面。未进入世界，画面验收仍由使用者完成。`local.toml` 未接受 EULA，跳过 `runServer`，本机配置字节未改变。

补充 NOTICE 的模板来源链接后，首次发行包内容检查发现包内 NOTICE 尚未同步；这是收尾时文件修改发生在打包之后造成的身份差异，记录在 `artifact-identity-before-final-package.json`，重新打包并核对后以 `artifact-identity.json` 为准，不沿用旧包哈希。

`spotless-apply.log`、`check-verify-release.log`、`strict-compliance.log` 与 `formatter-*.log` 保存上述命令结果；`run-client.log` 与 `client-latest.log` 保存运行记录。最终文档格式、发行包和严格检查保存到 `closeout-verification.log` 与 `strict-report-final.json`。本轮不提交或推送，暂存区保持升级前状态。

## 构建配置与许可说明对齐

2026-10-10 对齐 `gradle.properties`、Windows CI、双语首页标题与 NOTICE 结构。Gradle 设置只保留配置缓存、JVM 参数和工作线程上限；按使用者要求保留 4G 堆上限，移除显式并行执行和构建缓存设置。CI 的工具定位步骤移除，由原生组件自己定位 clang-format。

开启配置缓存后的 `verifyRelease` 实测失败：`configureNativeAudit`、`buildNativeAudit`、`generateBootstrapRuntimeIndex`、`generateEmbeddedGameMetadata`、`generateLauncherMetadata` 捕获 Gradle 脚本对象，缓存条目被丢弃。按使用者授权恢复 `false`，在配置项上方逐一列出不兼容任务，没有扩展任务去重写这些实现。

此前的 `spotlessApply` 缓存尝试因长时间文件集序列化扫描取消，线程记录与未完成日志保留，不记作通过。缓存失败报告、最终配置下的验证与客户端日志集中保存在 `D:/Workspaces/Artifacts/Radiance/template-alignment-20261010/`。

NOTICE 保留原有来源、许可与审查引用，按来源逐段排列并先列模板、再列 Wrapper；许可边界说明移到来源列表之前。第三方许可原文没有修改，公开二进制许可门禁保持未闭合。

## 迁移后发行包手工反馈

2026-10-10 使用者在已授权的 Prism 实例实测后反馈“实测没有发现问题”。对应发行 JAR 的 SHA-256 为 `620DB24C0103796D1F2B6EC78EF132CDC3BA33D459A3DC9AB3ADB8848FEE566A`，内嵌 `core.dll` 为 `1E511CA3EAA7B4F6A05B8B864E3919B6489FCD0B17B6F035D2C2B4A7577E2DB7`。投放及完整运行库索引校验证据位于 `D:/Workspaces/Artifacts/Radiance/prism-production-20261010-141718/`。

本次手工进程日志为 14:23:56 至 14:25:54，记录 Radiance 加载、集成服务端启动、全部维度保存与 `Stopping!`。反馈只表明本次游玩未观察到问题；没有逐项补写 Ponder、FG、长期稳定性或 GPU 故障根因验收通过。

使用者决定后续性能测试改用项目内验证实例与探针，操作规则写入项目规范 X-03。Prism 中已禁用的 `RadianceAudit-0.1.5-alpha.jar.disabled` 经模组 ID 与哈希确认后移入 Windows 回收站；Radiance 本体、其他模组、实例配置、世界及已有证据保留，没有启动游戏。移除回执与本次手工日志副本位于 `D:/Workspaces/Artifacts/Radiance/radiance-audit-removal-20261010-143434/`。

## 托管 CI 前置条件修正

2026-10-10 核对 `develop` 的 `92dd71054d21e5b94a259dba5980cba7f049f63b`：[Specification](https://github.com/RecRivenVI/Radiance/actions/runs/38033538400) 成功，[Check](https://github.com/RecRivenVI/Radiance/actions/runs/38033538329) 失败。失败来自托管 Windows 未安装 Vulkan SDK，以及 Python 盘点工具缺少 PyYAML；不是模板规则、产品编译逻辑或运行验收失败。

项目的 `check.yml` 补充固定版本的 Python、盘点依赖与官方完整 Vulkan SDK。版本来自版本目录和工具的 requirements 文件，SDK 提取到运行器临时目录；仍只上传合规报告。没有修改受保护的模板文件、产品渲染逻辑或公开二进制许可门禁。

干净 Python 虚拟环境先确认没有 PyYAML，按 requirements 安装后盘点行为测试 8/8 通过。本次失败日志、依赖安装与后续本地验证保存在 `D:/Workspaces/Artifacts/Radiance/ci-fix-20261010-153325/`；修正后的托管结果以对应提交的实际 Actions 记录为准，不沿用历史本地构建结果宣称云端通过。不启动游戏，不运行 GPU 或性能实验。

官方安装器的 `copy_only` 模式本地执行成功，独立 CMake 检查从该临时 SDK 找到 Vulkan 与 shaderc 的头文件、链接库和 glslangValidator。`spotlessApply`、`check verifyRelease` 本轮通过；后者 137 项任务中 43 项执行、94 项复用。原生非 GPU 测试 62/62、诊断采集器 2/2 实际执行通过，严格合规检查覆盖新增文件共 1,767 项，失败 0、警告 0。Java 已有的 Unsafe 与 Gradle 弃用警告保留，不将合规零警告扩大为所有工具无警告。

## 托管结果与恢复开发

2026-10-10，`develop` 提交 `cd25c20cd3cd99132dca85ca310233ad04d88490` 的 [Check](https://github.com/RecRivenVI/Radiance/actions/runs/38036771761) 与 [Specification](https://github.com/RecRivenVI/Radiance/actions/runs/38036771760) 均成功。Check 实际执行 `checkRepository`，上传内容仅为合规报告；这不替代本地完整发行包、世界运行或视觉验收。上述首轮迁移和升级期间的“尚未执行”只描述对应历史阶段。

使用者随后报告严重掉帧主要出现在区块加载期间，并认为与单向全部切换为双向的改动有关。当前记录为待调查的时间关联，没有匹配计时，尚未证明因果或定位到 CPU/GPU 阶段。当前产品代码未改变，默认双向与来源规则的可恢复性保持原状。

该阶段更新路线图并启动了项目内测试实例，确认实际加载的原生库与构建输出一致；尚未取得世界内的匹配性能对照，源码中的拓扑准备与共用互斥锁只是待测线索，不是已证明的根因。2026-10-12 使用者暂停调查；保留日志、产物身份及未知边界，见[当前路线图](roadmap-radiance.md#已暂停双向几何后的区块加载掉帧)。

## 仓库维护边界

后续开发只在本仓库进行，原生代码位于 `components/native_renderer`。旧 MCVR 仓库保留历史参考，不再写入开发内容；本次明确授权的历史折叠不改变迁移源基线、旧验收日期或产物对应关系。使用者恢复单一 `Initial port` 策略，具体元数据、签名和临时备份要求见[项目规范](../AGENTS.md)。

旧 MCVR 的配套冻结检查点为 `532c1bd02e2c98d3112db80d15594e9a8325ba07`，保留其移植与静态审查台账，树与迁移来源 `81c08145fc8d0f7ec12dedc0f4b303a8098822e6` 完全一致。该单向引用不更新历史验收产物，不要求以后在两个仓库之间同步开发。

## 模板 2.0.0 升级

2026-10-13 按远端标签版本降序列表首项，从 1.2.1 升级到 2.0.0，克隆提交为 `ffb142eb98a5cb22b0bbf5c8b4e17387f8bc4036`。执行新克隆的升级规程，保留现有工作区与暂存区，不提交或推送；旧 MCVR 仓库未操作。

三个旧模板组件迁为 `gradle/conventions/` 单一插件构建；根构建、项目组件和 Target 的插件入口与三个类型引用随之调整。通用规范文档移入该目录的 `specification/`，项目特有的原生环境、运行与配置缓存说明保留在项目的构建规程和原生架构文档中。删除旧工具入口和独立 rumdl 配置；rumdl 升至 0.2.79，其余模板工具版本、项目选定的加载器插件与 Minecraft 依赖保持原值。

新 G-08/S-06 保护范围包含 Wrapper、JVM 要求和统一插件；F-06 固定模板工具及 Action 版本，D-05 链接检查交给 rumdl，D-17 明确最少项目文档。NOTICE 同步模板版本与归属路径，项目 CI 保留 Windows、原生工具准备、本地输入边界和只上传合规报告的限制。静态盘点工具同步排除 `gradle/conventions/`，避免把模板代码计入项目渲染源码；使用真实文件收集入口验证排除生效且产品源码仍被收集。

升级前 `check --continue` 成功，合规失败 0、警告 0。升级后 `spotlessApply`、`check --continue`、`verifyRelease` 和 `verifyCompliance --strict` 成功；严格检查为 2.0.0、失败 0、警告 0。154 个发行包必需条目、元数据和探针排除符合要求，JNI 头文件与 GAME Maven 边界检查通过。

本次实际执行 GAME 250 项、probe 25 项、bootstrap 11 项通过，bootstrap 两项要求显式 GPU 环境的测试跳过；原生非 GPU CTest 62/62、诊断采集器 2/2、Python 盘点 8 项及离线盘点验证通过。新模板的 compliance/configuration/target 测试分别 63/65/30 项通过；工具 Java 基准与盘点测试沿用未变化输入的 UP-TO-DATE 结果，不记为本轮重跑。编译器仍提示已有 Unsafe 和过时 API 用法，Gradle 仍提示废弃接口；这些不属于合规警告。

参考 Target 的日常 `runClient` 使用 2560×1440、主音量 0。进程 52308 的日志列出 Radiance 0.1.5-alpha，完成资源加载并进入渲染主循环；窗口响应正常，未进入世界。通过仅匹配本仓库命令行的进程正常关闭，日志记录 `Stopping!`、场景释放和 SDK 待销毁资源为 0，任务成功且没有新崩溃报告。未使用 Computer-Use 或读取画面，菜单视觉不作为本轮通过项。`local.toml` 未接受 EULA，按授权跳过 `runServer`，该文件字节不变；没有运行 GPU 独立测试、世界验证或性能实验。

1,557 个产品源码及资源输入相对升级前原始字节哈希不变，122 个模板文件与克隆一致，暂存区条目未改变。按规程移除的 25 个文件或目录已进入 Windows 回收站，未永久删除。

证据位于 `D:/Workspaces/Artifacts/Radiance/template-upgrade-20261013/`：`before-check.log`、`after-check.log`、`verify-release.log`、`strict-report.json`、`test-counts.json`、`client-result.json`、`run-client.log`、`integrity-final.json` 与 `recycled-paths.json` 分别记录基线、检查、发行包、合规、测试、运行、文件完整性和回收结果。客户端之后只更新文档记录与离线盘点工具的目录排除，不影响运行产品；收尾复核另存 `final-format.log`、`final-check.log`、`strict-final.log` 和 `inventory-template-exclusion.json`。未推送，因此本轮没有对应的 GitHub Actions 运行结果。
