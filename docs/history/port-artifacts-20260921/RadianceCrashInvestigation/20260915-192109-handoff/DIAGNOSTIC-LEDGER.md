# Diagnostic ledger

本轮所有改动与运行均按“观察诊断 / 行为实验 / 正式修复”分开记录。临时诊断不得承担正常运行必需的状态修改；`USER_ACCEPTANCE=PENDING`。

## D-RESULT-01 — 运行结果分类修正

- 类型：纯观察诊断 / 结果汇总修正。
- 假设与目的：退出码 0 可能掩盖同一 PID 的新 fatal；零长度 dump 不能视为可用；没有 fatal 但被中断或尚未完成验收不能判为通过。
- 文件与符号：本轮新增 `Launch/Classify-RunResult.ps1` 及回归样例；不改变渲染源码和原始 `run-result.json`。
- 采集内容：PID、开始/结束时间、匹配时间窗内的新 `hs_err`、fatal 签名、dump 长度/可用性、退出码和完成状态。
- 影响：不改变时序、同步、内存或资源寿命；只生成修正后的分类结果。
- 证据：独立复核包的 A4 PID 91500/87940 两个 `ExitCode=0` fatal，PID 87940 dump 长度为 0。
- 状态：已完成脚本自测、历史结果回归和 A3 运行分类；原始记录未改写。A3 无新 fatal 但未完成正式验收，因此保留非验收状态。

## R-COLOR-01 — 颜色附件同步

- 类型：正式修复候选，先独立构建验证。
- 假设与目的：颜色附件写入发生在 `COLOR_ATTACHMENT_OUTPUT`，而现有最终 blit 前源阶段和 UI `loadOp=LOAD` 前目标阶段遗漏该阶段，导致 A3 的 WAW/RAW。
- 文件与符号：`MCVR/src/core/render/render_framework.cpp::FrameworkContext::fuseFinal()`；`MCVR/src/core/render/modules/ui_module.cpp::UIModuleContext::switchOverlayDraw()`，并检查同一颜色资源的 post/结束交接。
- 采集内容：保持现有 validation + synchronization validation；不启用 API 全量 trace、GPU-assisted、逐调用刷新、强制等待或跳过绘制。
- 影响：只扩大到真实颜色附件生产者/消费者的阶段与访问范围；不改变提交顺序，不关闭文字，不延迟释放。
- 预期证据损失：不再获得 D06 式全量 API 调用历史；保留有界 validation 日志和运行 fatal/dump，足以判断相关同步消息与 AV 是否仍出现。
- 状态：已实施并通过 Release 编译、24/24 CTest、Radiance Java test、distributedJar/verifyDistributedJar；A3 实机运行 `run-color-sync-a3-20260915-193610-395` 已正常退出，运行 `00:18:23.349`，用户确认本组正常运行。该运行无新 fatal、hs_err、转储或 `VUID-`/`WRITE_AFTER_WRITE`/`READ_AFTER_WRITE`/`SYNC-HAZARD` 命中；分类为 `NO_NEW_FATAL_NOT_ACCEPTANCE`。颜色同步消息是否在更长或其他实际路径中保持消除、以及与原始 AV 的因果关系，仍未完成证明。

## R-LIFE-01 — 已录制未提交资源寿命

- 类型：正式修复候选，独立于颜色同步。
- 假设与目的：`releaseTexture()` 普通分支在删除旧 image/sampler 前只等待队列；当前未提交 UI command buffer 不在 `vkQueueWaitIdle` 覆盖范围内。
- 文件与符号：`MCVR/src/core/render/textures.cpp::Textures::releaseTexture()`；调用链含 Java `AbstractTextureMixins` → `TextureProxy.releaseTextureId` → native `Textures::releaseTexture`；相关保护在 `render_framework.hpp/.cpp` 和 `ui_module.cpp`。
- 采集内容：优先用现有调用链、逐帧 fence、descriptor table 保留和 shared_ptr 所有权证明；若需运行记录，只做有界的释放/录制/提交代际事件，不做高频日志。
- 影响：拟让旧 image/sampler 覆盖当前 frame slot 的所有已录制引用直至 fence 完成；不永久保留，不添加额外 queue/device idle。
- 状态：调用链已确认存在可达缺口；颜色同步 A3 已封存。已实施最小 frame-slot retainer 保护（普通 `releaseTexture()` 分支保留旧 image/sampler 至当前 frame slot fence），并通过 Release 编译、24/24 CTest 及 Radiance JAR 验证。资源寿命组 A3 `run-resource-lifetime-a3-20260915-200145-881` 在用户接管前于首次世界加载文字绘制发生 fatal，未覆盖释放—旧表—fence 时序；尚未证明该缺口导致本次 AV。

## D-REAL-02 — 资源寿命组 A3 真实客户端运行

- 类型：纯观察诊断 / 正式修复候选的实机对照；不改变运行时同步策略。
- 假设与目的：检查最小 frame-slot retainer 是否能阻止当前真实客户端的原生 draw fatal，同时保留 A3 的低开销 validation/synchronization 证据。
- 文件与符号：`Launch/Run-ResourceLifetime.ps1`；`Textures::releaseTexture()` 普通分支；产品目录 `Products/resource-lifetime`；运行 `Evidence/run-resource-lifetime-a3-20260915-200145-881`。
- 采集内容与启用方式：A3 的 `VK_LAYER_KHRONOS_validation` + synchronization validation，`--quickPlaySingleplayer 新的世界`；API dump、GPU-assisted、DebugPrintf、best-practices 和高频/逐调用日志均关闭。保留 stdout/stderr、latest.log、匹配 hs_err，并以路径/大小索引 6,491,297,965-byte 转储，不复制大转储。
- 时序、同步、内存与寿命影响：只包含 R-LIFE-01 的旧 image/sampler frame-slot 保留；没有额外等待、串行化、延迟释放、跳过绘制或关闭文字。A3 层增加 validation 开销；外部 PrismLauncher PID `69680` 未操作。
- 产物与哈希：JAR `9D8BF6028C6C00604C6A38DD23FDA2E9B19F140F9C4D022D92E97F45D3A4D53B`；DLL `96E59835DFBB7E85CCCBE431A07F4F4E4F4DB7625122DA064793B55232465643`；PDB `FEEF6F9D1A4DBB155DCC656242D6E40052DC03AEE4FF4A286A2DE1840F63B26A`。
- 结论：用户接管前约 `39.897992 s` 在 `ReceivingLevelScreen.render` 的文字 `ShaderProxy.draw` 路径发生 `nvoglv64.dll+0xf1c708`、读取 `0x104` 的 fatal；分类器按精确签名规则标为 `OTHER_FATAL`。它是与原始 `+0xf1c729` 相邻的同类驱动位置，但不是精确原始故障签名。没有 VUID/WAW/RAW/SYNC-HAZARD 记录。该运行没有覆盖可确认的 `releaseTexture()` 释放—旧表—fence 时序，生命周期假设仍未决；完整结论见 `RESOURCE-LIFETIME-A3-RESULT.md`。
- 清理：本次专用脚本、隔离副本和关键日志/hs_err保留以供调查；大转储保留原位；未删除文件，未恢复 E-02。

## D-REAL-03 — 资源寿命 A3 产物隔离核对

- 类型：纯观察诊断 / 产物一致性核对。
- 假设与目的：确认资源寿命 A3 与已由用户确认正常的颜色同步 A3 之间没有未登记的 Java、资源或启动产物差异。
- 文件与符号：`Products/color-sync/Radiance.jar`、`Products/resource-lifetime/Radiance.jar`；使用 ZIP entry 内容哈希比较，不修改两个产物。
- 采集内容与启用方式：两份 JAR 均为 192 个 entry；仅 `core.dll` 和 `META-INF/radiance/runtime.index` 不同。前者对应本轮资源寿命源码改动，后者只记录 DLL runtime hash；其余 190 个 entry 内容及长度一致。
- 影响：不改变时序、同步、内存或资源寿命；只核对测试变量。
- 产物与哈希：颜色组 JAR `9345A714F2F34567856BDFC9F04ED597EAF3E40B2E57FFFC6E2480DA7558B852`，DLL `7FADBD0CE8FA062FC22989C15F7FECDCED9F3E1E38B1540DF07BED43FEF2C863`；资源寿命组 JAR `9D8BF6028C6C00604C6A38DD23FDA2E9B19F140F9C4D022D92E97F45D3A4D53B`，DLL `96E59835DFBB7E85CCCBE431A07F4F4E4F4DB7625122DA064793B55232465643`。
- 结论：本次两组真实运行的预定变量已隔离为 `core.dll` 的资源寿命改动；资源寿命 A3 的 `+0xf1c708`/`0x104` fatal 与颜色组无 fatal 的相关性仍不能单独证明因果。
- 清理：比较过程无临时源码或运行时诊断产生；两份正式候选产物及证据保留。

## 已保留/已撤销

- R-01、R-02：保留，已有验证证据。
- E-02：已移除，禁止恢复。
- D05–D11：作为局部负复现保留，不新增同类 D12。

## D-REAL-04 — 资源寿命 A4 真实客户端运行

- 类型：纯观察诊断 / 正式修复候选的真实客户端对照；不改变运行时同步策略。
- 假设与目的：在与 A3 完全相同的资源寿命产物上关闭显式 validation/synchronization，区分该 fatal 变体是否依赖显式诊断层，同时观察真实文字绘制路径。
- 文件与符号：`Launch/Run-ResourceLifetime.ps1`；`Textures::releaseTexture()` 普通分支；产品目录 `Products/resource-lifetime`；运行 `Evidence/run-resource-lifetime-a4-20260915-202344-080`。
- 采集内容与启用方式：A4，未显式启用 Vulkan validation/synchronization、API dump、GPU-assisted、DebugPrintf、best-practices；启动器清除相关显式层环境变量，但隐式层状态未知。使用 `--quickPlaySingleplayer 新的世界`，保留 stdout/stderr、latest.log、匹配 hs_err，并以路径/大小索引 `9,359,600,817`-byte 转储，不复制大转储。
- 时序、同步、内存与寿命影响：只包含 R-LIFE-01 的 frame-slot 保留；没有额外等待、串行化、延迟释放、跳过绘制或关闭文字。A4 不增加 validation 开销；外部 PrismLauncher PID `69680` 未操作。
- 产物与哈希：JAR `9D8BF6028C6C00604C6A38DD23FDA2E9B19F140F9C4D022D92E97F45D3A4D53B`；DLL `96E59835DFBB7E85CCCBE431A07F4F4E4F4DB7625122DA064793B55232465643`；PDB `FEEF6F9D1A4DBB155DCC656242D6E40052DC03AEE4FF4A286A2DE1840F63B26A`。
- 结论：用户接管前约 `23.995901 s` 在 `SubtitleOverlay.render` 的文字 `ShaderProxy.draw` 路径发生 `nvoglv64.dll+0xf1c708`、读取 `0x104` 的 fatal；分类器标为 `OTHER_FATAL`。它与资源寿命 A3 的同一变体重复，但不是原始 `+0xf1c729`/`0xffffffffffffffff` 精确签名。没有 VUID/WAW/RAW/SYNC-HAZARD 证据；没有释放—旧表—fence 事件证据，生命周期假设仍为证据不足。
- 性能与证据边界：采集器结束时间包含约 `9.36 GB` 转储写入，不代表客户端运行了约两分钟；A4 ready 记录被结果记录纠正。该对照削弱“显式诊断层导致 fatal 变体”的解释，但不能证明任何正式修复或根因。
- 清理：A4 运行日志、hs_err、结果和大转储索引保留；未删除文件，未恢复 E-02，未触及其他实例。

## D-REAL-05 — 资源寿命 A3/A4 故障指令与共同路径对照

- 类型：纯观察诊断 / 现有真实转储分析；不改变渲染行为，不新增运行。
- 假设与目的：核对 A3/A4 是否为同一故障变体，区分显式诊断配置、文字路径和资源寿命假设的证据边界。
- 文件与符号：`Evidence/RESOURCE-LIFETIME-A3-A4-COMPARISON.md`；A4 `hs_err_pid80964.log`；共同调用路径为 `ShaderProxy.draw` → `RasterDrawBridge.draw` → `vkCmdDrawIndexed`。
- 采集内容：对照两次实际 JAR/DLL/PDB 哈希、显式层配置、用户接管前 fatal 时刻、Java 文字路径、转储大小；核对 A4 故障指令 `41 3b 80 04 01 00 00` 与寄存器 `R8=0`。
- 影响：零时序、同步、内存或资源寿命影响；不启动客户端、不添加日志、不改变驱动或实例配置。
- 结论：A3/A4 均重复 `nvoglv64.dll+0xf1c708` / read `0x104`，分别来自 `ReceivingLevelScreen` 与 `SubtitleOverlay` 的实际文字绘制；这削弱显式诊断层导致该变体的解释，并说明问题不局限于字幕。故障点显示驱动内部空指针访问，但不能单独证明应用句柄错误或 `releaseTexture()` 因果。两次均无释放—旧表—fence 事件，所以资源寿命假设仍为“静态缺口已确认、运行因果证据不足”；原始精确 `+0xf1c729` / `0xffffffffffffffff` 未在这两次复现。
- 产物与哈希：与 `D-REAL-02`、`D-REAL-04` 相同；JAR `9D8BF6028C6C00604C6A38DD23FDA2E9B19F140F9C4D022D92E97F45D3A4D53B`，DLL `96E59835DFBB7E85CCCBE431A07F4F4E4F4DB7625122DA064793B55232465643`，PDB `FEEF6F9D1A4DBB155DCC656242D6E40052DC03AEE4FF4A286A2DE1840F63B26A`。
- 清理：分析记录保留；无文件删除，无 E-02 恢复，无第三客户端启动。

## D-CLEAN-01 — 重复转储与诊断副本清理

- 类型：纯观察/证据保全与空间清理；不改变渲染行为，不启动客户端，不改变驱动或实例配置。
- 假设与目的：在保留两类故障签名的最小完整代表、匹配产品/符号和小型运行记录的前提下，移除重复完整转储、已知不完整全量 API 追踪、旧产品二进制副本和可重建运行时 scratch。
- 范围：仅 `20260915-A` 与 `20260915-192109-handoff` 中显式列出的 23 个目标，包含 6 个重复 `.mdmp`、2 个高体积 API 文本、3 个实例 JAR、8 个旧产品/构建二进制、1 个 JAR 解包目录和 3 个 `.radiance\runtime` 目录。绝对路径与根目录校验通过；无调查进程；目标连续 2 秒未写入。
- 采集内容：清理前/后目录字节和文件数、每个目标的长度/文件数、目标是否仍存在、回收站可枚举占用、D 盘可用空间、关键代表文件存在性。
- 影响：只减少磁盘上的调查副本；不改变同步、提交、资源寿命、内存或游戏行为。回收站仍占原卷空间，未清空回收站。
- 产物与替代证据：D06 原始代表 `hs_err_pid88976.mdmp` 与资源寿命 A3 代表 `hs_err_pid71984.mdmp` 保留；`diagnostic-e2-removed`、`color-sync`、`resource-lifetime` JAR/DLL/PDB 保留；旧产品的 hashes/source-freeze 与所有小型日志/报告保留。
- 结果：23 个目标、576 个文件、`39,842,698,520` bytes 全部 `SendToRecycleBin` 成功；调查目录从 `54,542,475,450` bytes 降至 `14,699,776,930` bytes。D 盘可用空间仍约 `99.577 GiB`，因为回收站未清空；未执行永久删除。
- 清理状态：完成。未恢复 E-02；保留的两份完整转储是后续离线分析的延后项；无失败项。

## E-SETUP-01 — E 盘 Prism 隔离实例准备与启动保护

- 类型：纯准备/启动归属观察；不改变渲染行为，不修改已有实例或全局 Prism 设置。
- 假设与目的：将后续真实客户端运行迁移到 E 盘专用 Prism 实例，复用已核定的 1.21.1/NeoForge 21.1.250/Java 21 与资源寿命产品，避免继续向 D 盘写大体积材料，并保证不与外部客户端并行。
- 文件与路径：新实例 `E:\Minecraft\PrismLauncherDev\instances\Radiance-Crash-A-1.21.1-20260915`；准备脚本 `Launch/Run-E-Prism-A4.ps1`；运行数据根 `E:\Minecraft\RadianceCrashDiagnostics`；详细记录 `E-PRISM-INSTANCE-SETUP.md`。
- 采集内容与影响：新实例只复制调查副本的配置、测试世界、Radiance 资源和 JAR；不复制认证缓存、旧转储或旧 runtime。新实例内 Java 路径固定到 Java 21，内存 512/8192 MiB；A4 不显式启用诊断层。未启动时不影响游戏。
- 产物与哈希：JAR `9D8BF6028C6C00604C6A38DD23FDA2E9B19F140F9C4D022D92E97F45D3A4D53B`；对应 DLL `96E59835DFBB7E85CCCBE431A07F4F4E4F4DB7625122DA064793B55232465643`；PDB `FEEF6F9D1A4DBB155DCC656242D6E40052DC03AEE4FF4A286A2DE1840F63B26A`。
- 结果：实例创建和静态核对完成；第一次启动被脚本因外部 Minecraft PID `55316` 拦截，未启动本任务客户端、未产生新日志/转储。外部 Prism/Minecraft、服务器和 Gradle 未操作。
- 清理/状态：准备中的 983-byte 误建孤立文件已进入 Windows Recycle Bin；无永久删除。实例已完成一次 A4 启动并记录结果，未自动重启。

## D-REAL-06 — E 盘 Prism A4 自动 Quick Play 原始 AV

- 类型：纯观察诊断 / 真实客户端对照；不改变渲染行为，不启动第二个客户端。
- 假设与目的：在资源寿命候选产品、A4 无显式验证层的条件下，确认原始 AV 是否仍可由真实文字绘制路径触发，并核对实际加载产物。
- 文件与符号：实例 E:/Minecraft/PrismLauncherDev/instances/Radiance-Crash-A-1.21.1-20260915；运行目录 E:/Minecraft/RadianceCrashDiagnostics/run-a4-20260915-214223-785；ShaderProxy.draw、RasterDrawBridge.draw、vkCmdDrawIndexed。
- 采集内容与启用方式：A4；无显式 validation/synchronization、API dump、GPU-assisted、DebugPrintf、best-practices；清除相关显式层环境变量；未断言隐式层。保留 latest.log、hs_err 和产物哈希；没有全量 API trace。
- 影响：无额外等待、串行化、延迟释放、跳过绘制、关闭文字或 E-02；真实客户端自动 Quick Play 到新的世界。
- 产物与哈希：JAR 9D8BF6028C6C00604C6A38DD23FDA2E9B19F140F9C4D022D92E97F45D3A4D53B；hs_err Dynamic libraries 直接列出的实际 core.dll 路径为 E:/Minecraft/PrismLauncherDev/instances/Radiance-Crash-A-1.21.1-20260915/minecraft/.radiance/runtime/e5325edf50c152109ad035221db77855c6563fd3921c0d4cb6c12768e76fbb63/core.dll，SHA-256 96E59835DFBB7E85CCCBE431A07F4F4E4F4DB7625122DA064793B55232465643；匹配 PDB FEEF6F9D1A4DBB155DCC656242D6E40052DC03AEE4FF4A286A2DE1840F63B26A。
- 结果：Java PID 93144；JVM elapsed 72.041583 s；日志确认玩家加入世界。新 hs_err 262375 bytes，SHA-256 E2C14ED1F84CEB0A651DD98EAD6F907F1511B59767340F004187693A8DFCE5E，精确为 EXCEPTION_ACCESS_VIOLATION at nvoglv64.dll+0xf1c729, reading 0xffffffffffffffff；Java/原生故障路径为 ShaderProxy.draw、RasterDrawBridge.draw、GuiGraphics.drawString、AbstractButton.renderWidget、PauseScreen.render 和 vkCmdDrawIndexed；本次没有完整转储。
- 指令级对照：当前 hs_err 的 fault bytes 为 41 8b 01...，即 mov eax, [r9]，且 R9=0xffffffffffffffff；D06/A4 保留 exact-AV hs_err 具有相同指令/寄存器模式。重复的是驱动内部访问模式，不等于已证明应用传入了某个错误句柄。
- 结果分类：采集脚本原始 run-result.json 因客户端在 PID 采样前退出而写为 LAUNCH_NO_JAVA；保留该原始记录，离线 run-result-corrected.json 按同一时间窗新 hs_err 修正为 FATAL_LOG。脚本已加入路径归一化和 early-fatal 归类，语法检查通过，脚本 SHA-256 442F64793F38AA3A3904845986E535D5098968797EF47DDB9F559AB408F285F3。
- 诊断消息：A4 latest.log 中 VUID-、Validation Error、WRITE_AFTER_WRITE、READ_AFTER_WRITE、SYNC-HAZARD 均为 0；这是未显式启用验证层的配置结果，不是同步正确性证明。
- 结论：原始精确 AV 在资源寿命候选产品的真实 A4 自动 Quick Play 中重新出现；它不证明 frame-slot retainer 是根因或已修复/加重，也没有给出 release—旧 descriptor—fence 事件。颜色同步修复和资源寿命修复与该 AV 的因果关系仍需保持区分。人工操作序列未由用户提供，不从日志猜测。
- 清理：hs_err、latest.log、修正结果、报告和脚本保留；未删除文件，未恢复 E-02，未操作服务端或其他客户端；未自动重启。

## D-STATIC-07 — Overlay pipeline layout 与 descriptor generation 兼容性核对

- 类型：纯观察诊断 / 静态 Vulkan 规范核对；不改变源码、命令录制、同步或资源寿命策略。
- 假设与目的：核实 `registerOverlayDrawShader()` 创建 pipeline 时使用的 `overlayDescriptorTables_[0]` layout，和实际 draw 时 `vkCmdBindDescriptorSets()` 使用的当前帧 descriptor table layout 是否构成真实的不兼容；避免把“不同句柄”或“旧 layout 被销毁”直接当成应用违规。
- 文件与符号：`MCVR/src/core/render/modules/ui_module.cpp::UIModule::registerOverlayDrawShader()`、`createOverlayDescriptorTable()`、`refreshOverlayDescriptorTable()`、`UIModuleContext::begin()`、`drawIndexed()`、`drawCustomVertexArray()`；`MCVR/src/core/vulkan/descriptor.cpp::DescriptorTable`。
- 采集/核对内容：创建时 layout 来自固定的 `overlayDescriptorTables_[0]`；dirty 刷新会新建同一 builder 生成的两组 set layout，当前 draw 使用当前帧 table 的 layout 和 set。set 0 的 combined-image-sampler binding 0..5、set 1 的两个 dynamic UBO、stage flags、descriptor binding flags、push-constant ranges 与 pipeline-layout flags 均来自同一固定定义；没有启用 `VK_PIPELINE_LAYOUT_CREATE_INDEPENDENT_SETS_BIT_EXT`。
- 规范依据：Khronos Vulkan 规范的 Pipeline Layout Compatibility 要求比较的是 identically defined descriptor set layouts、push constant ranges 和 independent-sets 条件；`vkCmdBindDescriptorSets` 还要求 descriptor set layout 与传入 layout 匹配。因此，不同 `VkPipelineLayout`/`VkDescriptorSetLayout` handle 本身不是不兼容证据。见 <https://docs.vulkan.org/spec/latest/chapters/descriptorsets.html>。
- 所有权边界：`DescriptorTable::bindSamplerImage()` 只把 sampler/image view 的原始 `VkDescriptorImageInfo` 写入 descriptor set，不持有 image/sampler 的 `shared_ptr`。已核对的实际 `Textures` 调用方在 image 重建、sampler 替换、显式 release 和 resource reload 路径分别通过当前 frame retainer 或 reload 保留容器覆盖旧代资源；`refreshOverlayDescriptorTable()` 也把旧 descriptor table 保留到当前 frame slot fence。`DescriptorTable` 自身不能替代资源寿命保护，这条边界仍保留给 R-LIFE-01 的运行证据。
- 影响：零运行时开销和零时序影响；未添加诊断、等待、串行化或实验行为。
- 结论：没有发现可直接支持“pipeline layout handle 不同即导致本次 AV”的证据，也没有在这条线上修改代码；该假设降为未证实线索。A4 的 `nvoglv64.dll+0xf1c729` 仍只能归因到驱动内部重复访问点，尚未与 layout 或资源释放建立因果链。
- 清理：无临时产物，无需删除；E-02 未恢复，未启动新客户端。

## D-REAL-08 — E 盘 Prism A4 启动阶段 exact AV（用户无操作）

- 类型：纯观察诊断 / 真实客户端复现；不改变渲染行为，不启动第二个客户端。
- 假设与目的：在同一资源寿命候选 JAR/DLL 上，记录不显式开启诊断时是否能在主界面完成前自动重现原始精确 AV，并把用户实际操作与日志推断分开。
- 文件与符号：实例 `E:\Minecraft\PrismLauncherDev\instances\Radiance-Crash-A-1.21.1-20260915`；运行归档 `E:\Minecraft\RadianceCrashDiagnostics\run-a4-20260915-223336-011`；报告 `Evidence/RUN-E-PRISM-A4-20260915-223336-011.md`；`ShaderProxy.draw`、`RasterDrawBridge.draw`、`TitleScreen.render`、`vkCmdDrawIndexed`。
- 采集内容与启用方式：A4 无显式 validation/synchronization、API dump、GPU-assisted、DebugPrintf、best-practices；无强制等待、延迟释放、跳过绘制或关闭文字；保留 `hs_err_pid87276.log`、`latest.log`、`instance.cfg` 快照和哈希。JVM 命令行没有 Vulkan 诊断参数；隐式层未作断言。hs_err 观察到 OBS hook 与 RTSS Vulkan layer DLL 已加载，仅作记录。
- 时序、同步、内存与寿命影响：零新增运行时同步或资源寿命影响；仅复制小型崩溃日志和配置快照，没有复制大型转储。
- 产物与哈希：实际 `Radiance.jar` `9D8BF6028C6C00604C6A38DD23FDA2E9B19F140F9C4D022D92E97F45D3A4D53B`；hs_err Dynamic libraries 实际加载 `core.dll` `96E59835DFBB7E85CCCBE431A07F4F4E4F4DB7625122DA064793B55232465643`；匹配 `core.pdb` `FEEF6F9D1A4DBB155DCC656242D6E40052DC03AEE4FF4A286A2DE1840F63B26A`。
- 结果：日志起始 `22:33:36.098 +08:00`，PID `87276` 在 JVM elapsed `21.636836 s`、`22:33:55` 发生 `EXCEPTION_ACCESS_VIOLATION` at `nvoglv64.dll+0xf1c729`，读取 `0xffffffffffffffff`。Java 路径为 `ShaderProxy.draw` → `RasterDrawBridge.draw` → `BufferUploader` Radiance rewrite → `GuiGraphics.drawString` → `AbstractButton.renderWidget` → `TitleScreen.render`；没有可用完整转储。`latest.log` 只到资源重载、字体、声音和纹理图集初始化，未达到完成主界面验收点。
- 用户操作边界：用户明确反馈本次无点击、无暂停、无切窗，主界面未加载完成即崩溃；该信息取代本运行先前的“未提供操作”状态。没有从 `TitleScreen.render`、`PauseScreen` 或日志结束位置推断按键。
- 结论：这是新的一次真实启动阶段原始精确 AV，强化了问题不依赖暂停菜单交互、也不局限字幕路径；仍未证明颜色同步、资源寿命、descriptor 状态或其他 Vulkan 契约中的任一项是根因。`USER_ACCEPTANCE` 仍为 `PENDING`。
- 清理：目标实例原文件未修改；小型归档已保留，未删除文件，未恢复 E-02，未操作其他 Java/游戏实例；未自动重启。

## D-STATIC-09 — 失败 draw 主机状态有界取证

- 类型：纯观察诊断；不是正式修复，也不是行为实验。
- 假设与目的：把“失败 `vkCmdDrawIndexed` 前 pipeline/layout/descriptor/动态状态或旧资源代际不一致”变成可关联证据；不把故障现场 `R9=-1` 直接映射为某个 Vulkan 参数。原始 `nvoglv64.dll+0xf1c729` AV 尚未解决；R-01/R-02 保留；E-02 未恢复且已移除。
- 文件与符号：新增 `MCVR/src/core/diagnostics/draw_state_trace.hpp/.cpp`；接入 `UIModuleContext::drawIndexed()`、`UIModule::bindOverlayDescriptorTableResources()`、`Framework::acquireContext()`、`Textures::releaseTexture()`、`Java_com_radiance_client_proxy_vulkan_ShaderProxy_draw()`；Java 接入 `Radiance/src/main/java/com/radiance/client/proxy/vulkan/ShaderProxy.java`。
- 采集内容：Java sampler 源对象/源 ID、`SamplerN` 槽、resolved/encoded ID 和字段布局；native frame acquire/submit、UI command buffer、pipeline/layout、descriptor table/set、descriptor image/UBO、VBO/IBO/patch buffer、uniform offset/count、纹理初始化/替换/release/fallback/擦除及对象句柄。输出为有界 TSV；native 默认 `MCVR_DRAW_STATE_TRACE_MAX_EVENTS=32768`、`MCVR_DRAW_STATE_TRACE_FLUSH_EVERY=16`，Java 默认上限 512。建议专用运行将 native flush 调为 8。
- 启用方式与性能：只有 `MCVR_DRAW_STATE_TRACE_PATH` 存在才打开 native 文件；Java 还需 `RADIANCE_DRAW_STATE_TRACE=1`。native 每批刷新，不逐条刷盘；Java 只记每个 shader 首次 sampler 布局或可疑 sampler。没有 API 全量追踪、GPU-assisted、validation、强制等待、串行化、延迟释放、跳过绘制或关闭文字。性能影响预计远低于 D06 全量 trace，但专用运行仍不作为正常性能验收；崩溃可能丢失最后不足一个 flush 批次。
- 构建与产物：`cmake --build MCVR/build --config Release --target core -- /m:1` PASS；最终 `ctest ... -C Release --output-on-failure` 24/24 PASS（2.91 秒）；Radiance `compileJava` PASS；`cmake --install` 后 `preparePackagedClient --rerun-tasks` PASS。产品 JAR `E45BEDAC9A9EE1C6C9B67D7D49E06D4D98D12491783E133D897CA08218F81865`，DLL `1E4BB8F518323B7E32F7E34D41CDD67CC82EA03C2D3E37AF868AE08362D06C84`，PDB `0BF874664CAF6A0EE189B4CC832E10C04A1E8815C32B42209FBF24FC1C6192DE`；完整源快照与清单见 `D:\Workspaces\Artifacts\RadianceCrashInvestigation\20260916-004325-handoff`。
- 装配纠正：首次 Gradle 打包复用了旧 `Radiance/src/main/resources/core.dll`（`96E59835...`），未用于后续取证；显式安装最终 Release DLL 后重新打包，资源 DLL、build DLL 与产品 DLL 均为 `1E4BB8F518323B7E32F7E34D41CDD67CC82EA03C2D3E37AF868AE08362D06C84`。这是产物一致性修正，不是渲染修复。
- 当前结果：尚无 runtime trace。系统已有外部 Prism 子进程 PID `93076`（`2026-09-16 00:35:07 +08:00`，Java 25，未加载 `core.dll`），已核对为非本任务目标并保持不动；未在其存活期间启动第二个 Minecraft。待其结束后再启动一次唯一真实取证，先分析日志/fatal，再决定是否需要下一次。
- 结论与清理：诊断代码已可构建、默认关闭；尚未确认 sampler 负值、pipeline/layout/descriptor 失配或旧资源代际是否出现在真实失败 draw。源码/产物封存，未删除文件，未恢复 E-02，未调整 Git 暂存区、提交或推送。

## D-REAL-09 — 新取证产品 A4 启动阶段 fatal（未形成有效 trace）

- 类型：纯观察诊断 / 真实客户端运行；不是修复验收。
- 假设与目的：在包含本轮 Java/native 状态取证代码的新产品上观察主菜单自动文字绘制是否仍触发原始 AV，并在发生 fatal 时保留 hs_err；不把未成功传递的 trace 当作状态证据。
- 运行与归属：`E:\Minecraft\RadianceCrashDiagnostics\run-draw-state-a4-20260916-005345-618`；脚本开始 `2026-09-16T00:53:45.6181936+08:00`，无 Quick Play。实际 fatal PID `16656`，JVM elapsed `43.262739 s`，脚本未在窗口内捕获 PID，按时间窗新 hs_err 规则归类为 `FATAL_LOG_NO_PID`/fatal。原始 A 实例 JAR 运行后恢复为 `9D8BF602...`，没有保留候选 JAR 写入原实例。
- 产物与证据：候选 JAR `E45BEDAC9A9EE1C6C9B67D7D49E06D4D98D12491783E133D897CA08218F81865`；JAR 内嵌/实际加载 `core.dll` `1E4BB8F518323B7E32F7E34D41CDD67CC82EA03C2D3E37AF868AE08362D06C84`；PDB `0BF874664CAF6A0EE189B4CC832E10C04A1E8815C32B42209FBF24FC1C6192DE`；hs_err `542088` bytes，SHA-256 `2D318768B1A79F7039A216F42BCD28D4C57037ED2AEEAC1590E8BEDCB225EF77`；latest.log `23836` bytes，SHA-256 `C000F2CD6D855F37AD932C6D4356A533EAC28F23127716D5F8F828DA7F9BE908`；无完整转储。
- 崩溃现场：`EXCEPTION_ACCESS_VIOLATION` at `nvoglv64.dll+0xf1c729`，reading `0xffffffffffffffff`，`R9=-1`；Java/native 栈为 `ShaderProxy.draw(IIIIIIIJI)V` → `BufferUploader._drawWithShader` → `GuiGraphics.drawString` → `AbstractButton.renderWidget` → `TitleScreen.render`，仍是已知 `vkCmdDrawIndexed` 录制路径。
- 诊断有效性：没有 `draw-state-trace.tsv`，latest.log 没有 `[Radiance draw-state]`。原因是原脚本使用了已有 Prism 单实例 PID `75904` 的数据根，启动参数被单实例转发，实际 Java 没继承脚本环境；因此本次只证明新产品仍复现精确 AV，不提供 sampler/pipeline/layout/descriptor/UBO/资源代际结论。
- 后续准备：已创建隔离 Prism 数据根 `E:\Minecraft\RadianceCrashDiagnostics\isolate-draw-state-20260916`，实例副本 `Radiance-Crash-A-Trace-20260916`；修正脚本最终 SHA-256 `2B79131E83C8299707725452C705CDD4F1572502B7D7AE8924BC2B454E14655B`，通过 PowerShell 语法检查，改用 Prism `--dir` 使环境传递不经过现有单实例。隔离副本尚未启动。
- 结论与清理：原始 AV 本次仍出现；颜色同步/资源寿命与 AV 的因果关系仍未建立；没有人工操作记录，不从日志猜测；未删除文件、未恢复 E-02、未调整 Git 暂存区、未提交、未推送。

## D-REAL-10 — 隔离 Prism 启动未产生 Minecraft Java

- 类型：纯观察诊断 / 启动封装核对；不是渲染测试、不是行为实验、不是修复验收。
- 假设与目的：用 Prism `--dir` 隔离原有单实例 IPC，使 `MCVR_DRAW_STATE_TRACE_PATH` 与 `RADIANCE_DRAW_STATE_TRACE=1` 能传给实际 Java；本次首先验证该启动前提，不继续增加独立无窗口复现。
- 文件与符号：启动脚本 `D:\Workspaces\Artifacts\RadianceCrashInvestigation\20260916-004325-handoff\Launch\Run-DrawStateTrace-A4.ps1`；运行目录 `E:\Minecraft\RadianceCrashDiagnostics\run-draw-state-a4-20260916-010601-000`；归档目录 `D:\Workspaces\Artifacts\RadianceCrashInvestigation\20260916-004325-handoff\Evidence\run-draw-state-a4-20260916-010601-000`；报告 `Evidence/D-REAL-10-ISOLATED-LAUNCH-NO-JAVA.md`。
- 采集内容与启用方式：候选 JAR `E45BEDAC...`，core `1E4BB8F5...`，native trace 上限 32768、批量刷新 8，Java trace 上限 512；API dump、validation、GPU-assisted、debug printf、best practices、强制等待、延迟释放和行为绕过均关闭。由于没有 Java 子进程，实际没有打开 Radiance trace。
- 运行事实：启动时间 `2026-09-16T01:06:01.0004165+08:00`，结束 `2026-09-16T01:08:21.6028738+08:00`，Prism PID `71988`，Minecraft Java PID 不存在；结果 `LAUNCH_NO_JAVA`，无新 hs_err、无 dump、无 draw-state trace。隔离 Prism 日志 19479 bytes，SHA-256 `117A571E2B6DADCA3251171269974353518041FACBA5AC7BBDD0E8549070A6AD`；`run-result.json` SHA-256 `19BFAA45C75818CB9E1DE7BAEF594B51B5478E93014BA052ECFDF9927E5FF0D5`；`launch.json` SHA-256 `12FC6E6E6F0BCF8C07C26903535BCE9E185BB5F5B33CA8A6151B9381EEAF40E7`。
- 观察到的启动差异：隔离根没有 `accounts.json`，Prism 日志先报缺失该文件，随后只显示实例 launching 和 Main window shown，没有创建 Java。原始账号文件未读取或复制；原 Prism 根、原 A 实例和其他进程未修改。
- 影响：不改变渲染时序、同步或资源寿命；只增加了隔离启动器目录和一轮启动检查。未取得任何渲染证据，也不把本次归类为产品成功或崩溃。
- 结论：直接失败是隔离 Prism 未进入 Minecraft Java；缺失 `accounts.json` 是最明确的启动上下文差异，但不能单独证明唯一根因。该隔离方案不再继续使用。
- 清理：确认没有指向隔离根的 Prism/Java/Minecraft 进程后，`E:\Minecraft\RadianceCrashDiagnostics\isolate-draw-state-20260916` 已于 `2026-09-16 01:11 +08:00` 通过 Windows Recycle Bin 移动并确认原路径不存在；没有永久删除，未恢复 E-02，未提交或推送。

## INSTANCE-BOUNDARY-01 — Prism 与代理测试实例归属修正

- 类型：调查流程与证据归属修正；不改变渲染行为、同步、资源寿命或 Git 状态。
- 事实：从本条起，`E:\Minecraft\PrismLauncherDev` 只作为用户手动验收环境；代理不得通过 Prism 启动/停止/复制/改写其中实例，也不接触其账号、全局配置、存档或其他实例。此前 D-REAL-01 至 D-REAL-09 的 Prism 运行记录保留为历史证据，不再作为后续测试入口。
- 代理测试位置：Radiance 项目目录 `D:\Workspaces\Repositories\GitHub\RecRivenVI\Radiance\run` 下的新的时间戳隔离子目录；不复制用户存档，必要世界由本任务创建；日志、转储和运行配置不进入 Git 提交。现有 `run\client` 与 `run\packaged-client` 含旧世界/日志，本轮不直接复用。
- 已废止入口：`Run-DrawStateTrace-A4.ps1` 以及 D-REAL-10 创建的 Prism 隔离数据根不再使用；隔离根已通过 Windows Recycle Bin 清理。原 Prism 根、用户测试实例和当前用户 Prism 进程未修改。
- 后续约束：启动任何代理客户端前先核对进程归属、目录、JAR/DLL/PDB 哈希和唯一实例状态；用户 GUI 操作仍由用户完成，代理不使用 ComputerUse。

## D-REAL-11 — Radiance run 独立实例 draw-state 有效取证（2026-09-16）

- 类型：纯观察诊断 / 真实客户端运行；不是行为实验或修复验收。
- 假设与目的：验证现有 Java/native draw-state 环境变量能抵达实际 Minecraft Java/native，并取得失败 draw 前的 pipeline、layout、descriptor、纹理代际、VBO/IBO、uniform 和 frame submit 状态。实例只在 Radiance 项目 `run\diagnostic-draw-state-20260916-212740\base` 创建，未使用 Prism、ComputerUse 或用户存档。
- 文件与符号：原始运行目录 `D:\Workspaces\Repositories\GitHub\RecRivenVI\Radiance\run\diagnostic-draw-state-20260916-212740`；小型归档 `D:\Workspaces\Artifacts\RadianceCrashInvestigation\20260916-004325-handoff\Evidence\run-direct-draw-state-20260916-212740`；详报 `Evidence/D-REAL-11-DIRECT-DRAW-STATE-FATAL.md`。启动命令、实际 game PID、JAR/DLL/PDB 路径和哈希记录在报告及 `launch-process.json`。
- 采集内容与启用方式：native trace 上限 `32768`、每 `8` 条刷新；Java trace 上限 `512`；清除显式 Vulkan layer/API dump 环境；validation、GPU-assisted、强制等待、延迟释放和跳过绘制均关闭。trace 有效写出 `32768` 条事件，类型计数 `FRAME=3075`、`DRAW=1375`、`UNIFORM=1376`、`TEXTURE=144`、`DESCRIPTOR=26798`，证明环境变量已传入并产生有效 native 记录。
- 时序、同步、内存与资源寿命影响：观察诊断只写有界文件；没有逐调用刷盘、全量 API dump、额外等待、串行化或资源释放改变。descriptor 记录过多，在约 `37.51 s` 达到上限；fatal 约 `95.0 s`，所以失败阶段 draw 不在 trace 内。该证据损失已登记，下一次专门取证可使用仍有界的更大容量/较低刷新频率。
- 运行结果：实际 Java PID `13148`，启动记录 `2026-09-16T21:28:56.9021995+08:00`，fatal `2026-09-16 21:30:44 +08:00`，JVM elapsed `94.939690 s`。实际加载 DLL `1E4BB8F5...`，匹配 PDB `0BF87466...`，运行 JAR `E45BEDAC...`。`nvoglv64.dll+0xf1c708` 读取 `0x104`，栈包含 `ShaderProxy.draw` → `Gui.renderSlot`/`renderItemHotbar`；有匹配 hs_err，无可用完整 dump。
- 取得结论：前 `37.51 s` 的 frame/command buffer/submitted 顺序扫描 `ANOMALIES=0`；没有已捕获 draw 的 descriptor table 缺失或同表 pipelineLayout 记录不匹配；Java 首次 sampler 解析为正值。真实路径确实产生多次 texture replace，但没有失败阶段资源代际配对。原始 AV 在修复前产品仍出现；不能用该前段 trace 排除失败阶段主机状态。
- 清理状态：客户端自然因 fatal 退出，未杀进程；原始运行目录与小型归档均保留，没有复制 `.radiance` 运行时/存档或大型转储。

## R-LIFE-02 — DescriptorTable 底层资源保活正式修复（2026-09-16）

- 类型：正式修复；不是诊断开关或行为实验。
- 假设与目的：防止旧 descriptor table 仍被尚未完成的录制/提交命令使用时，底层 image、sampler、buffer 或 TLAS 因容器替换/释放而失去最后一份所有权。
- 文件、符号及差异位置：`MCVR/src/core/vulkan/descriptor.hpp:76-111` 新增 `(set,binding,index)` 资源键、保活表和 helper；`descriptor.cpp:63,90,151,176,198,203-207` 在 `bindImage`、`bindSamplerImage`、buffer 写入、buffer 数组和 `bindAS` 成功更新后保存底层 `shared_ptr`。首个候选源码仍保留在 `Source/resource-lifetime-keepalive`；经 update-after-bind 复核后的当前源码快照与哈希见 `Evidence/R-LIFE-02-DESCRIPTOR-TABLE-KEEPALIVE.md` 及 `Source/resource-lifetime-keepalive-v2`。
- 采集/启用方式：正式代码无运行时诊断依赖；同一 table 槽位覆盖时不立即释放旧代，按 table 生命周期保留该槽位曾写入的每一代资源并按 `shared_ptr` 控制块去重，旧 table 的全部保活随现有 frame retainer/fence 结束而释放。未恢复 E-02，没有永久全局保留、队列等待、串行化、延迟释放、跳过绘制或关闭文字。
- 时序、同步、内存与资源寿命影响：`DescriptorTable` 在其 Vulkan descriptor layout/pool/set 生命周期内同时持有实际引用资源；当旧 table 由现有 retainer 在对应 fence 后销毁，资源才可释放。R-01/R-02 颜色与 depth/stencil 同步行为不变；本修复不证明 `vkQueueWaitIdle` 能覆盖尚未 submit 的命令，而是补上对象所有权。
- 产物与哈希：首个 `resource-lifetime-keepalive` 候选（`core.dll` `48DEA377...`、PDB `104F8C0D...`、JAR `9E3B47C9...`）已因覆盖即替换语义 superseded，仍保留但不用于真实对照。当前 v2 Release `core.dll` `44D3B6F8...`（30967808 bytes）、PDB `A4F994D6...`（45764608 bytes）、Radiance JAR `CEC66840...`（146642348 bytes）；JAR 内 core 与 standalone DLL 一致。详见 `Products/resource-lifetime-keepalive-v2/PRODUCT-MANIFEST.md`。
- 验证：`cmake --build ... --config Release` PASS；既有 CTest `24/24 PASS`；`cmake --build ... --target INSTALL` PASS；`Radiance .\gradlew.bat distributedJar --no-daemon` PASS。没有新增同类 headless 测试矩阵。
- 取得结论：源码层资源所有权缺口已确认，最小正式修复已构建；D-REAL-11 使用修复前产品，尚无修复后真实客户端证据，因此不能声称该缺口已证明为本次 NVIDIA AV 根因，也不能声称游戏问题已解决。
- 清理状态：本轮只新增保活正式代码、独立产品和报告；临时 trace 仍作为 D-REAL-11 证据保留，未关闭开关冒充清理；未恢复 E-02，未改 Git 暂存区、未提交、未推送。
- 交付边界：当前 v2 产品仍包含默认关闭的 D-STATIC-09 诊断代码，用于下一次修复后真实对照；因此它是修复验证检查点，不是已完成临时诊断清理的最终发布包。最终包须在修复对照后清理诊断代码并重新构建。
