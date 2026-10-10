# 当前项目状态

截至 2026-10-10，本次为结构与构建迁移及模板升级收尾，源基线为 Radiance `585c561fd3711aedcdc243715b94145d630bb1ad`、MCVR `81c08145fc8d0f7ec12dedc0f4b303a8098822e6`。不从固定提交时间推断历史验收顺序。

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
- 性能测试按用户决定暂停；本次只执行构建、合同和迁移正确性验证。

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
- 首次默认配置缓存运行失败，原因是项目自身任务捕获脚本对象；已关闭配置缓存，构建缓存保留。Windows CI 尚未执行，托管运行环境的 Vulkan SDK 准备仍待验证。

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

Gradle Wrapper 9.8.1、版本目录与守护进程 JVM 要求和新模板一致，保留原文件；公共配置保留 RAW/SPIR-V 二进制属性、Windows CI 与本地输入规则。CI 只上传合规报告，不上传发行包或运行库；实际托管 CI 仍未执行。

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
