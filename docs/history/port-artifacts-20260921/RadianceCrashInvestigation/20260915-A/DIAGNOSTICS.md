# 本轮诊断台账

当前有效授权：2026-09-15 用户已扩大必要诊断范围；无需逐项申请。禁止自行启动交互游戏和 ComputerUse，复现命令交用户执行。保留已有未提交成果及正式 A/B 产物。当前原崩溃未定位完成。

## 分类与索引

纯观察：D-01、D-02、D-03、D-04。行为实验：E-01、E-02。独立成立的契约修复：R-01、R-02。任何实验存活结果都不能升级为正式修复。

所有相对路径以本目录为根。每个产品目录的 hashes.json 记录 JAR/DLL（D-04 起含 PDB）SHA-256；source-freeze.json 记录对应源码。Baseline 保存起点；Evidence 保存日志、运行记录、转储分析和旧启动输入哈希。正式验收产物未覆盖。

## D-01：标准 Vulkan validation

- 目的/假设：查找首次合法调用契约错误，不预设 descriptor 或设备丢失。
- 文件/符号/差异：Launch/Run-A-Validation.ps1、manualBaseRunVmArgs.txt、manualBaseRunProgramArgs.txt、manualBaseLegacyClasspath.txt、manualBaseLog4j2.xml、vk_layer_settings.txt；启动辅助相对本轮起点新增，无业务源码探针。
- 内容/启用/输出：隔离子进程 VK_INSTANCE_LAYERS、VK_LAYER_PATH、VK_LAYER_SETTINGS_PATH、VK_LOADER_DEBUG；warn/error 每条最多三次；Evidence/run-*/console.log、latest.log、hs_err。
- 影响：validation 增加 CPU、内存和调用开销，可能影响时序；没有应用级同步或资源生命周期修改。
- 产品：Baseline/Radiance-formal-start.jar、Products/diagnostic-r1、diagnostic-r2、diagnostic-d4-symbols；各自哈希文件及归档 diagnostic-*-inputs.json 关联运行版本。
- 结论：发现真实 03320/09600 及后续 01198/09600，分别促成 R-01/R-02；修后仍 CPU 访问异常，不能认定原崩溃已解决。
- 清理：待移除临时 Launch 辅助与环境设置；证据保留。正式回归不得沿用该启动环境。

## D-02：标准 GPU-assisted validation

- 目的/假设：标准 CPU 检查未覆盖动态 descriptor/缓冲访问，查找 shader 越界证据。
- 文件/差异：Launch/vk_layer_settings.txt 的 gpuav_enable、gpuav_shader_instrumentation、gpuav_buffers_validation；归档 Evidence/d02-layer-settings.txt。
- 内容/启用/输出：同 D-01 启动，SDK GPUAV 输出到 run-20260915-140539-426/console.log；无 Java/JNI/native 探针。
- 影响：shader 插桩、额外 GPU/内存和时序开销；不改变应用资源所有权。部分不支持检查由 SDK 禁用。
- 产品：diagnostic-r2，JAR 798BEFF493270D1FD2842AD223CE84EF1B83A0A46A6F81FA822B3B41DFE00E16，DLL 01E1F7871FE99AFBEF25E2C610F818DE9EDD263D3F16DCB5539794B28174923F。
- 结论：116.40 秒再次 CPU 访问异常；此前 0 VUID 不是 GPU/descriptor 正确性证明。
- 清理：GPUAV 配置已还原为标准 validation；整个临时配置文件仍待最终清除。

## D-03：JVM fatal 原生转储及标准离线调试器

- 目的/假设：确定 CPU 异常的实际调用链、寄存器及资源对象状态。
- 文件/差异：Launch/manualBaseRunVmArgs.txt 新增 -XX:+CreateCoredumpOnCrash；Scratches/RadianceCrashD03 的 SDK layout/管理解包工具，无项目源码修改。
- 内容/启用/输出：JVM 仅 fatal 时生成 Diagnostic/base/hs_err_pid*.mdmp；Microsoft 签名 CDB 离线读取，Evidence/d03-*.txt/json。dump 未上传。
- 影响：崩溃后较大的磁盘和保存耗时；离线读取不影响已结束的游戏；无运行必需状态修改。
- 产品：D-03 使用 diagnostic-r2；转储 SHA-256 见 Evidence/d03-dump-hash.json。
- 结论：core→Khronos validation→NVIDIA 的 vkCmdDrawIndexed；当前 UBO/descriptor state 有效。创建 pipeline 时的旧 layout 已销毁，但这本身不能证明违反 Vulkan 契约。原驱动异常根因仍未证实。
- 清理：JVM 参数、临时调试器下载/解包待回收；转储/分析作为证据保留。

## D-04：优化 Release 匹配符号

- 目的/假设：旧 DLL 无 PDB，需精确 core 符号和局部对象，避免把就近导出名当函数。
- 文件/差异：MCVR/build/CMakeCache.txt 的 CMAKE_CXX_FLAGS_RELEASE 添加 /Zi，CMAKE_SHARED_LINKER_FLAGS_RELEASE 使用 /DEBUG:FULL /OPT:REF /OPT:ICF；原值 Evidence/d04-original-cmake-flags.json。无源码日志。
- 内容/启用/输出：Release 构建并独立保存 PDB；D-03 fatal dump 后 CDB 离线解析 Evidence/d04-symbol-stack.txt、d04-ui-draw-locals.txt。
- 影响：保留 /O2，符号/布局及编译产物可能改变，不视为纯字节不变对照；无显式同步、释放或绘制变更。
- 产品：Products/diagnostic-d4-symbols，JAR FB343DDD0FAF89EB32CEC0159CC212391ADF6629C024835BDFF1CB21F931F2FA，DLL 10B245ECD252732C308A5A68F0A1B666C92760EB03B5D1D558EA2305D5CD2E7D；PDB 哈希见 hashes.json。
- 结论：run-20260915-142838-387，27.22 秒同一 CPU 异常；core!UIModuleContext::drawIndexed，shaderId=3，uniformOffset=0x1680，indexCount=180，TitleScreen 文字；Evidence/d04-dump-hash.json 对应完整现场。
- 清理：待恢复原 CMake flags 后重新构建、安装和封装正式无诊断产物。PDB/诊断产物保留在独立目录。

## E-01：过滤 OBS/RTSS Vulkan 层

- 目的/假设：检验第三方注入层是否为必要触发条件。
- 文件/差异：仅启动子进程 VK_LOADER_LAYERS_DISABLE=VK_LAYER_OBS_HOOK,VK_LAYER_RTSS；无代码/应用配置更改。Evidence/third-party-layer-versions.json。
- 内容/启用/输出：Loader 明确确认过滤；run-20260915-135756-089。
- 影响：改变层链及其开销/行为；不操作 OBS/RTSS 进程，不作为正式兼容策略。
- 产品：diagnostic-r1；JAR 0FF46AC78AF6812E281D99BAE0E94E1FD731AB4A204620464A04329B0583E430，DLL 2017B205DD2B4A06C626401C6019B154BF746C94E1492C7B2AE8B7DBC396FBED。
- 结论：用户主菜单正常，之后进入世界；178.87 秒 PauseScreen 再次崩溃。过滤两层不能解决问题。
- 清理：实验子进程已结束，后续恢复原层组合；无源代码/配置残留。

## E-02：保留 pipeline 创建时 descriptor table

- 目的/假设：验证旧创建 layout 生命周期与驱动描述符消费异常是否相关。
- 文件/符号/差异：MCVR/src/core/vulkan/dynamic_pipeline.hpp 的 DynamicGraphicsPipeline、DynamicGraphicsPipelineBuilder 临时 experimentDescriptorTable_；dynamic_pipeline.cpp 的 definePipelineLayout/build 赋值。实验前原件 Baseline/e02-before；仅移除本实验新增 hunk，不回退其他变动。
- 内容/启用/输出：编译期实验，无运行开关；沿用 D-01/D-03/D-04，记录标准 validation、崩溃现场及用户主菜单/世界反馈；Evidence/e02-native-build.log。
- 影响：明确改变 descriptor table、pool、set layouts、pipeline layout 的释放时点，增加存活内存；不是纯观察，不是正式修复。没有 wait、跳绘制或并发调整。
- 产品：Products/diagnostic-e2-layout-lifetime；JAR 7CBC7B5915D5DA856D051BB4009F5F1067BC0CFA7B64EA6BDD2DD4144D807E51；DLL 5DA0FFEC2BC35B77C496D94C88B85C47BCEF703EB9C02F60B0543DE238B7084A；PDB D79B1D00514F50E4CD3260CA1989B84ADB4BEE8B73FAAF37CCE8CB7FBE99F19B。hashes.json、十项 source-freeze.json 已封存，包内 DLL 一致。Release 编译、安装、distributedJar/verifyDistributedJar/verifyRuntimeResources 通过；未重新宣称完整测试或运行通过。
- 差异位置：dynamic_pipeline.hpp:34、180；dynamic_pipeline.cpp:99、170；精确 hunk 见 Evidence/e02-header.diff、e02-implementation.diff。
- 结论：首次用户复现run-20260915-143633-077约99秒，世界保存与客户端Stopping记录存在；0 VUID/Validation Error/原生访问异常，运行DLL哈希匹配。用户反馈看起来无问题。退出码未记录；旧hs_err不能误算本次。一次未复现仅支持生命周期关联调查，未证明根因，整表保留同时影响pool/sets/layout和内存复用。Evidence/e02-run-review.json。
- 清理：按用户当前要求已移除两文件全部实验hunk；两文件SHA-256与Baseline/e02-before一致，src/tests中experimentDescriptorTable_及Temporary E-02零残留。正在重新构建去实验诊断版；历史E-02产物/证据单独保留，不再部署为当前启动版本。

## 正式修复与交付门槛

R-01：format 驱动完整 depth/stencil barrier，布局匹配 render pass；六文件起步，回归夹具修正。R-02：firstHitDepth 由 world 生产者导出只读布局，HUD 不根据过早 CPU snapshot 恢复 UNDEFINED；两处生产路径及 GPU 用例。最终八项源码见 diagnostic-r2/source-freeze.json。两者均不依赖诊断开关，相关 Java/CTest/GPU 证据见 PROGRESS.md；原 CPU 驱动崩溃仍存在。

最终交付前：逐项移除临时源码/启动/构建配置，回收临时工具；保留证据、正常错误、上游日志、F3 GPU 计时和独立修复；检查本轮残留并统一重建正式单 JAR 和哈希。无窗口通过与用户实际画面/退出验收分开记录。



### E-02清理后的当前产品

实验源码清理已完成，并实际重新编译封装。当前产品Products/diagnostic-e2-removed：JAR SHA-256 21CF6C1451B4021ADE6AEB53A9DFB931A40EFBE15B93C3D5D77FB34FC7B0D550，DLL 20F85170B43A822D93BFBD9FB12770CE5CCD78E2FAFC9B8B5E1B6AA62374BD89，PDB和源码冻结见hashes.json/source-freeze.json。旧E-02产物仅为留存证据，当前启动入口已切换去实验版。D-01/D-03/D-04观察诊断继续保留；本轮未自行启动游戏。编译/安装/封装核验通过，不等于运行或完整测试通过。

## D-05：独立无窗口生命周期复现与现场反查（已完成；负复现）
- 目的/假设：区分创建时pipeline layout、set layouts、pool/sets释放与动态UBO indexed draw异常的关系；检验maintenance4启用差异，不能预设驱动或应用根因。
- 文件/所有权：Sol High子代理descriptor_repro独占D:/Workspaces/Scratches/RadianceDescriptorRepro及Evidence/d05-*复现输出；不改业务源码/已有测试，不派生。根只离线dump分析与台账，最多2个活动线程。根文件输出d05-removed-stack.txt、d05-shader-state.txt、d05-shader3.txt，worker避免覆盖这些。
- 采集/启用：独立命令行无窗口Vulkan程序，多组释放策略和真实像素读回；标准validation回调及时flush。编译参数/命令和产品哈希由复现说明记录后归档，不进入游戏JAR。
- 影响：测试程序有GPU工作、分配/释放和fence等待，属于隔离的有界行为对照；不改变游戏进程或生产生命周期，不把测试fence应用为业务修复。
- 当前证据：去E-02 run-20260915-144641-190在23.07秒同址nvoglv64+f1c729 CPU异常，并非已证实死锁。新PID67420转储缺system info，CDB不可读，保留但不冒充完整现场。此前D04完整dumpshader3确认为rendertype_text，192字节uniform，两个stage使用dynamic UBO+纹理数组。
- 规范边界：VkGraphicsPipelineCreateInfo说明物理API>=1.3时实现不得在create调用结束后访问layout；当前物理API1.4.351。因此不能仅凭旧layout销毁就判应用违规。https://docs.vulkan.org/refpages/latest/refpages/source/VkGraphicsPipelineCreateInfo.html 。当前MCVR未显式启用maintenance4；对照结果不能自动升级为必须feature的结论。
- 产品版本/哈希：首批及追加对照的哈希、结果和说明已登记于对应 Evidence/d05-* 文件。结论：`NOT_REPRODUCED_IN_FIXTURE`，该阶段已收束，不再扩展同类 fixture。历史记录当时暂留 scratch；必要源码/脚本/产物已归档到 `D05-archive`，原 `RadianceDescriptorRepro` 已按清理台账移入 Windows 回收站，不增加业务诊断源码残留。

## D-06：真实游戏标准API关联追踪（首轮已完成；捕获范围不足，待扩大）
- 目的/信息缺口：无窗口生命周期单变量未复现，缺真实游戏创建layout/pipeline/descriptor更新/绑定/销毁与命令提交的完整前后关联。取首300帧完整Vulkan调用，包含启动前对象创建，不铺设业务日志。
- 文件/差异：新增Launch/D06/Run-A-ApiTrace.ps1、vk_layer_settings.txt、inputs.json；SDK原有VkLayer_api_dump.dll/.json未改。无Java/JNI/native源码修改。
- 采集/启用/输出：用户命令启动，显式VK_LAYER_LUNARG_api_dump与Khronos validation；output_range=0-300，pre_dump=true、flush=true、timestamp/thread/frame/detailed/address开启，shader二进制打印关闭。每次Evidence/run-d06-*/api-dump.txt、console.log、run-result.json，fatal dump仍Diagnostic/base。只复制本次新hs_err，不混入历史六份。进程退出码写入JSON。
- 影响：CPU/磁盘/内存日志开销、额外层调用以及时序变化；没有插入GPU等待、屏障、延迟释放或跳绘制。记录在300帧后停止，之后崩溃可能缺最近调用，明确此覆盖界限。每API刷盘优先保留崩溃前记录。
- 产品/哈希：业务JAR沿用diagnostic-e2-removed(21CF6C1451B4021ADE6AEB53A9DFB931A40EFBE15B93C3D5D77FB34FC7B0D550)，DLL20F85170B43A822D93BFBD9FB12770CE5CCD78E2FAFC9B8B5E1B6AA62374BD89；D06配置/脚本/API层哈希Launch/D06/inputs.json，标准VVL与启动输入仍由Baseline/diagnostic-inputs.json守卫。没有新行为实验产物。
- 当前结论：无窗口配置 smoke 已通过；首轮真实游戏 `run-d06-20260915-151251-845` 已取得可读完整转储，但 API dump 在 frame 299 停止而崩溃发生于 frame 1599，因此首轮关联追踪按“取证不完整”收束，不把它写成成功复现的完整调用链。新的真实运行准备将覆盖崩溃前后关键窗口，不再使用首300帧范围。
- 清理：最终需将整个临时Launch/D06辅助回收，环境只在独立pwsh子进程；必要证据保留。与D01/D03/D04一并审计清理，不能只关闭开关。正式修复不依赖此层。

### D-05首批收束
12组各30秒，总290747帧、9303904次indexed draw、13955856个读回像素匹配，VUID/validation errors=0。结论严格为NOT_REPRODUCED_IN_FIXTURE。每组11条warning为主动过滤implicit层的loader提示；fixture appInfo初始为1.4，另追加1.3短对照。说明/命令/编译开销、源文件符号/版本SHA-256/清理项见Evidence/d05-description.md、d05-hashes.csv、d05-summary.csv。源码/EXE/PDB在Scratches独立生成，未接入游戏。待取证完成后回收scratch，仅保留证据副本。

### D-06无窗口配置验证通过
使用worker完成后的repro-sparse.exe all 1 0检查标准API层：218帧、6976次indexed draw、10464像素匹配，0 validation error；13条提示是11条implicit层过滤加2条显式层环境提示。Evidence/d06-headless-smoke.log及d06-headless-api-dump.txt(约27.5MB)确认调用前参数、真实句柄、线程/帧/时间和create/bind/destroy/submit都有输出。无窗口用例无present因此帧号不前进，不能用其验证300帧停止行为；该范围语义来自SDK配置文档。游戏D06不设置implicit层过滤，保留原组合。未启动交互游戏。

### D-05 API1.3追加对照
与游戏一致的apiVersion=1.3、sparse/all、业务销毁顺序及implicitreset，maintenance4 0/1各10秒均退出0、零VUID，真实像素匹配。见Evidence/d05-api13-runs.json及同前缀log/hash/说明。该追加仍不包含实际EXT动态状态与游戏调用链，结论仍是简化程序未复现。当前业务源码未因D05改变。

### D-08 真实文字 shader 对照收束
`D:\Workspaces\Scratches\RadianceDescriptorTextRepro` 使用实际 `rendertype_text` 翻译后的 SPIR-V、192-byte std140 uniform、PositionColorTexLight 28-byte vertex、uint16 index、descriptor array 及 22 项动态状态。`keep` 40.0005 s / 53,233 frames / 1,703,456 indexed draws / 2,555,184 像素，`all` 40.0002 s / 54,084 frames / 1,730,688 indexed draws / 2,596,032 像素；两组零 VUID、零 validation error、零像素错误、零访问异常。夹具没有生产游戏 render pass 的 depth/stencil attachment，因此仅为未复现负结果，不排除真实路径。

### D-09 D32S8 深度/模板精确对照
- 目的/假设：补齐 D08 缺少的 `VK_FORMAT_D32_SFLOAT_S8_UINT` depth|stencil attachment、combined color+depth/stencil render pass、clear/transition 和 depth/stencil pipeline state，判断该缺口是否是 NVIDIA `vkCmdDrawIndexed` CPU 异常的必要条件。
- 文件/所有权：根任务独占 `D:\Workspaces\Scratches\RadianceDescriptorTextDepthRepro`；只复制 D08 输入并修改 scratch，未修改 Radiance/MCVR 业务源码、启动配置或 Prism 实例。计划/结果见 `D:\Workspaces\Artifacts\RadianceCrashInvestigation\20260915-152956-handoff\D09-PLAN.md` 与 `D09-RESULT.md`。
- 采集/影响：`keep 40 0`、`all 40 0`，无窗口 Vulkan、fence 等待、8x6 像素读回；同步只属于夹具，不是游戏修复。日志为 `Evidence/d09-real-text-depth-stencil-sparse-*.log`，运行 JSON 为 `Evidence/d09-runs.json`。
- 产品/哈希：`repro.cpp` 730B28AFA16752DCBCE589BAF5D85C784D189D2F67A4A334159BE52E096CC2C2；`repro.exe` 89181CDDE1D6774B81D2A68AD424CA6E567913DB3DCAC09DB3B9A468D2CF9810；`repro.pdb` C92266A0CC81806E80344AB96B2F97B2CA7A7C1F18A8B74217BA292A95ADDF0D；实际 text SPIR-V 为 D08 同源输入，SHA-256 见 D09-RESULT.md。
- 结果：keep 40.0015 s / 54,673 frames / 1,749,536 indexed draws / 2,624,304 像素，all 40.0002 s / 53,976 frames / 1,727,232 indexed draws / 2,590,848 像素；合计 3,476,768 indexed draws、5,215,152 像素、0 VVL error、0 访问异常，退出码均为 0。严格结论为 `NOT_REPRODUCED_IN_FIXTURE_DEPTH_STENCIL`，不能升级为游戏问题排除或正式修复。
- 清理：D09 必要源码/脚本/产物与结果已归档到 `D09-archive`；原 `RadianceDescriptorTextDepthRepro` 已按清理台账移入 Windows 回收站。未接入正常运行，不依赖诊断开关。

### D-06首次游戏结果与覆盖不足
run-d06-20260915-151251-845实际运行35.814883秒后TitleScreen文字路径同址CPU AV，命令于15:14:38退出-1073740791；VUID0。API追踪66.9MB只到Frame299/19.50秒层时间，漏掉崩溃调用尾部，此捕获范围不足不能冒充完成。新6.89GB转储CDB可读，d06-crash-stack.txt已保存。配置暂未改，优先分析完整dump，后续范围调整需登记；未再次启动游戏。

## D-07：完整EXT动态状态无窗口对照（已完成；负复现）
- 唯一ID/目的：D07；D05未覆盖真实动态管线状态，验证EXT动态状态切换及创建layout寿命组合是否触发CPU异常。
- 文件/符号：worker descriptor_repro独占Scratches/RadianceDescriptorDynamicRepro与Evidence/d07-*；以D05独立副本实现createDevice/createGraphicsPipeline/draw的状态查询、EXT入口加载、完整动态状态设置。旧D05冻结和业务源码不改，子代理不派生。根继续D06离线dump反查及索引，含根2活动线程。
- 采集/启用：无窗口GPU程序，匹配生产22项动态状态列表，切换blend等并真实像素读回，VVL报告和进程退出码落盘。初步2秒all smoke读回通过，正式keep/all maintenance4=false各40秒。
- 影响：独立测试中GPU绘制/同步fence/资源释放实验；不加入游戏，不修改游戏资源同步。无depth/stencil附件意味着不能验证实际depth/stencil图像效应，真实shader/其他层/多帧差异仍存在。
- 版本/哈希：Evidence/d07-* 已记录源码/EXE/PDB/结果。keep/all 各40秒共约354万 indexed draw、约531万像素检查，0 VUID/访问异常；严格结论为 `NOT_REPRODUCED_IN_FIXTURE_DYNAMIC`，该阶段已收束，不再扩展同类 fixture。必要源码/脚本/产物已归档到 `D07-archive`，原 `RadianceDescriptorDynamicRepro` 已按清理台账移入 Windows 回收站。不得把未复现当根因排除。

### D06本次继续离线取证
匹配ApiDump PDB成功读取崩溃帧计数frame_count=0x63f(1599)，should_dump_output=false，证明首300帧捕获止于加载画面早期；Evidence/d06-api-frame.txt。当前失败仍shaderId3/rendertype_text、indexCount48、uniformOffset0x1380。pipeline=0x8490000000849，绑定pipelineLayout=0xff40000000ff4和descriptorPool=0xff10000000ff1；创建不在已捕获早期API段。不可把早期段当完整重放。根未修改业务或启动游戏。

### D-10：生产式稀疏非零 descriptor 索引对照
- 目的/假设：验证 D06 中 set0/binding0 4096 项数组的低索引空洞是否与当前 draw 的非零采样器索引共同构成触发条件；不预设 NVIDIA 驱动或应用根因。
- 文件/所有权：根任务独占 `D:\Workspaces\Scratches\RadianceDescriptorTextSparseIdsRepro`，由 D09 scratch 复制后仅修改 `repro.cpp`；不修改业务源码、启动配置或 Prism 实例。计划见 `20260915-152956-handoff/D10-PLAN.md`。
- 采集/启用：保留真实 `rendertype_text` SPIR-V、D32S8 combined render pass、动态状态、动态 UBO 和 keep/all 生命周期；set0/binding0 逐项写入索引 3..106，0/1/2 保持未写入，uniform 取样器索引交替为 19/61；输出 `Evidence/d10-*.log` 与 `Evidence/d10-runs.json`。
- 影响：仅独立无窗口 fixture 的 GPU 工作、fence 等待和小尺寸像素读回；不改变游戏正常同步、释放或绘制路径。行为结果不能直接升级为正式修复。
- 产品/哈希：构建后登记 `repro.cpp`、EXE、PDB、SPIR-V 和 include 文件 SHA-256；源码与产物不进入游戏 JAR/DLL。
- 结论：构建、smoke 和 keep/all 对照均已完成；严格结论为 `NOT_REPRODUCED_IN_FIXTURE_SPARSE_NONZERO_IDS`。零错误/存活不等于真实游戏已解决，该阶段已收束，不再扩展同类 fixture。
- 清理：D10 源码/脚本/产物与结果已归档到 `D10-archive`；原 `RadianceDescriptorTextSparseIdsRepro` 已按清理台账移入 Windows 回收站。结果不依赖诊断开关。

### D-11：真实尺寸/用途纹理对的稀疏 descriptor 对照
- 目的/假设：在 D10 已覆盖索引 0/1/2 空洞、3..106 占用及 19/61 非零取样后，补齐 D06 现场的 16x16 与 256x256 `R8G8B8A8_UNORM` 纹理、`usage=0x7` 和 sampler 参数，判断纹理资源形态是否为必要触发条件；不预设根因。
- 文件/所有权：根任务独占 `D:\Workspaces\Scratches\RadianceDescriptorTextRealTexturePairRepro`，由 D10 scratch 复制后仅修改 `repro.cpp` 与 `run.ps1`；不修改业务源码、启动配置或 Prism 实例。计划/结果见 `20260915-152956-handoff/D11-PLAN.md` 与 `D11-RESULT.md`。
- 采集/启用：保留实际 `rendertype_text` SPIR-V、192-byte UBO、D32S8 combined render pass、22 项动态状态、双动态 UBO和 keep/all 生命周期；索引 19 指向 16x16，61 指向 256x256；输出 `Evidence/d11-real-text-texture-pair-sparse-depth-stencil-*.log` 与 `Evidence/d11-runs.json`。
- 影响：仅独立无窗口 fixture 的 GPU 工作、fence 等待、纹理 clear/transition 和小尺寸像素读回；不改变游戏同步、释放、descriptor 更新或绘制路径。fixture 结果不能直接升级为正式修复。
- 产品/哈希：`repro.cpp` `29F888696374CD4FFD1B021E8A1BC523940CF1FA6BFBB777D587B5751CEB16D7`；`repro.exe` `96836A133DC9FAC2D20E76E42E7A6E62171C3A2D229904BBEAA8A5808F0157D8`；`repro.pdb` `943E422ABDADB6DD1645E3614223E9213D53C551DCF27AEAAEDB90A233B1727F`；其余输入哈希见 `D11-RESULT.md`。
- 结果：`keep` 40.0007 s / 42,084 frames / 1,346,688 indexed draws / 2,020,032 像素，`all` 40.0021 s / 44,038 frames / 1,409,216 indexed draws / 2,113,824 像素；合计 2,755,904 draws、4,133,856 像素、0 VVL error、0 访问异常，退出码均为 0。严格结论为 `NOT_REPRODUCED_IN_FIXTURE_REAL_TEXTURE_PAIR`。
- 清理：D11 scratch 和日志暂留至根因定位/正式交付清理阶段；只能按台账移入 Windows 回收站，失败则保留。未接入游戏，不依赖诊断开关。

### A2：真实 Minecraft A 扩展 API 关联取证（已结束；诊断层自崩）
- 目的/假设：结束 D08-D11 同类 fixture 扩展，回到真实 `Diagnostic/base` Minecraft A，捕获原生崩溃前的完整关键 API 区间，补足 D06 在 frame 299 停止而故障位于 frame 1599 的覆盖缺口；关联文字 pipeline、descriptor、纹理、uniform、VBO/IBO、资源生命周期及 submit/command 记录。
- 文件/所有权：新增 `Launch/A2/Run-A2-ApiTraceExpanded.ps1`、`Launch/A2/vk_layer_settings.txt`、`Launch/A2/inputs.json`；只使用已隔离的 `Diagnostic/base`，不修改 Radiance/MCVR 源码、Prism 实例、原始实例配置或存档。A2 归根任务独占，禁止与其他测试客户端并行。
- 采集/启用：子进程环境显式加载 `VK_LAYER_LUNARG_api_dump` 与 Khronos validation；api dump `output_range=0-2500`、`pre_dump=true`、`flush=true`、timestamp/thread/frame/detailed/address 开启、shader 二进制关闭；`validate_sync=true`，GPUAV/DebugPrintf/best-practices 关闭。每次输出 `Evidence/run-a2-*/api-dump.txt`、stdout/stderr、latest.log、run-result.json；新的 fatal mdmp 留在隔离 base 原位，不复制大型转储。
- 影响：api dump 与同步 validation 只增加观测层开销和日志量，不插入等待、屏障、延迟释放、跳过绘制、关闭文字或过滤 OBS/RTSS；`VK_*` 设置只存在于 A2 子进程环境。范围 0..2500 覆盖 D06 frame 1599 并保留无崩溃时的后续窗口，若进程提前退出或用户未完成操作须如实标为缺失。
- 产品/一致性：A2 运行前验证 Diagnostic/base `Radiance.jar` 与 `Products/diagnostic-e2-removed/Radiance.jar` 均为 `21CF6C1451B4021ADE6AEB53A9DFB931A40EFBE15B93C3D5D77FB34FC7B0D550`；预期 native `core.dll` 为 `20F85170B43A822D93BFBD9FB12770CE5CCD78E2FAFC9B8B5E1B6AA62374BD89`，符号 `core.pdb` 为 `E177D9DE386308D3B99F112244AC02C47B1E94E5BBB32F64F8824052C081B2BA`；实际加载路径和哈希由 run-result 记录。
- 当前结论：A2 于 2026-09-15 16:30:51 +08:00 启动，监控 PID `93224`、Java PID `48924`，run 为 `Evidence/run-a2-20260915-163051-131`；实际加载的 `core.dll` 为隔离 runtime 下的 `20F85170B43A822D93BFBD9FB12770CE5CCD78E2FAFC9B8B5E1B6AA62374BD89`，与去 E-02 产品一致。约 164 秒后 fatal 位于 `VkLayer_api_dump.dll+0x498cb9`，API dump 12,252,054,062 bytes，新增 mdmp 9,088,415,268 bytes；这是 API dump 诊断层不可用，不能算 Radiance/MCVR 崩溃或修复结果。完整分析见 `20260915-152956-handoff/A2-RESULT.md`。
- 清理：A2 运行结束后保留本次 stdout/stderr、latest/result、fatal 文本、配置和哈希；12,252,054,062-byte API 文本与 9,088,415,268-byte、CDB 不可读的诊断层 mdmp 已按 `20260915-152956-handoff/CLEANUP-20260915.md` 移入 Windows 回收站。Launch/A2 配置与脚本仍保留作可审计输入，正式版本不依赖 A2。

### A3：可交互真实 Minecraft A validation 取证（已结束；观察结果已封存）
- 目的/假设：针对 A2 的实测开销，仅保留有限 Khronos validation 和同步检查，移除持续 API 全量追踪，以恢复人工可操作性；保留 JVM fatal dump、latest.log、stdout/stderr、JAR/DLL/PDB 一致性和已有 D06 完整转储证据。
- 文件/所有权：新增 `Launch/A3/Run-A3-InteractiveValidation.ps1`、`Launch/A3/vk_layer_settings.txt`、`Launch/A3/inputs.json`；只使用隔离 `Diagnostic/base`，不修改正式源码、Prism 或其他实例。A3 与 A2 不并行，一次只启动一个客户端。
- 采集/启用：仅加载 `VK_LAYER_KHRONOS_validation`；`report_flags=error,warn`、重复消息上限 1、`validate_sync=true`，GPUAV/DebugPrintf/best-practices 关闭；不启用 API dump，不设置 API 文件或逐调用 flush。A3 输出 `Evidence/run-a3-*`，新大型 mdmp 留在 base 原位。
- 证据损失/影响：失去 API 级全量 create/update/bind/destroy/submit 文本及地址级重放；保留 validation/sync 消息和 native fatal 现场。预期远低于 A2 的内存、CPU、磁盘写入压力，但仍不是无诊断性能验收。
- 当前结论：A3 脚本、配置、JAR/DLL/PDB、validation 层和冻结启动输入已通过哈希预检。A3 于 2026-09-15 16:42:04 +08:00 启动，监控 PID `91132`、Java PID `74556`，run 为 `Evidence/run-a3-20260915-164204-116`，于 16:56:05 退出码 0 结束；未生成 API dump、新 hs_err 或新 mdmp。stdout/latest.log 实际记录 `SYNC-HAZARD-WRITE-AFTER-WRITE`（前一 render pass 写入与后续 barrier 写入缺少充分同步）及 `SYNC-HAZARD-READ-AFTER-WRITE`（loadOp 读取与先前 layout transition 缺少 color-attachment-output 阶段依赖）。这两条是新的观察证据，不等于原生 AV 已解决，也不等于正式修复。
- 清理：A3 完整 run 目录、必要 Launch 输入、当前 `Diagnostic/base` 存档/配置和 `d4305629...` runtime 保留；旧 runtime、A2 巨型追踪/诊断层 mdmp、被 D06 替代的 D03/D04/D05 mdmp，以及已归档 D05/D07/D08/D09/D10/D11 scratch 已按 `20260915-152956-handoff/CLEANUP-20260915.md` 使用 Windows 回收站处置。其他 Radiance scratch 不在本轮范围内。

### 2026-09-15 调查产物清理台账

- 清理前 `20260915-A` 为 2,564 files / 49,231,020,192 bytes；清理后为 1,928 files / 8,538,382,898 bytes。第一批 13 个直接目标的文件长度合计 40,693,318,474 bytes；所有目标均先做绝对路径范围检查、进程归属检查和独占读锁探测。
- D03/D07/D08/D09 必要源码、脚本、EXE/PDB 与 Evidence 分别归档到 handoff 的 `D05-archive`、`D07-archive`、`D08-archive`、`D09-archive`（D05/D07 的历史 fixture 也一并封存）；D10/D11 原有归档保持不变。可重建 `.obj/.ilk/vc140.pdb` 未保留。
- 回收站移动没有永久删除或清空；D 盘可用空间没有因此增加（约 104.10 GB → 104.09 GB），因为回收站仍保留同卷数据。未清理用户其他实例、仓库、存档、配置或无关 scratch。

### A4：同产物无诊断真实运行（已结束；确认原生崩溃）

- 目的/假设：在 A3 的 low-cost validation 观察完成后，关闭所有本轮临时诊断，用完全相同的去 E-02 JAR/DLL 在隔离 `Diagnostic/base` 中进行人工主菜单、世界、暂停和正常退出验收；不新增诊断，不扩展独立 fixture，不改变正式代码或同步行为。
- 文件/所有权：`Launch/A4/Run-A4-NoDiagnostics.ps1`（SHA-256 `AAD7E58AFBAD31DDEEEAF0719954C73C5EF13958FDE6C5E358824952494373FD`）、`Evidence/run-a4-20260915-171302-062` 与 `20260915-152956-handoff/A4-RESULT.md`；A4 的 Java/父监控 PID 为 `73724/91172`，检查结束时均已退出，未发出终止操作。
- 采集/启用：`VulkanLayers=[]`，validation、sync validation、GPUAV、DebugPrintf、best-practices、API dump 全部关闭。helper 只移除子进程的临时 `VK_*` 诊断变量，不屏蔽 OBS/RTSS，不插入等待、额外屏障、延迟释放、跳过绘制或关闭文字。保留 stdout/stderr、latest.log、run-result、新 hs_err/mdmp（若有）和 JAR/DLL/PDB 一致性。
- 产品/哈希：base/product `Radiance.jar` 均为 `21CF6C1451B4021ADE6AEB53A9DFB931A40EFBE15B93C3D5D77FB34FC7B0D550`；实际/产品 `core.dll` 均为 `20F85170B43A822D93BFBD9FB12770CE5CCD78E2FAFC9B8B5E1B6AA62374BD89`；PDB 为 `E177D9DE386308D3B99F112244AC02C47B1E94E5BBB32F64F8824052C081B2BA`。
- 结果：A4 应用运行约 `28.247038 s` 后退出码 `1`，发生 `EXCEPTION_ACCESS_VIOLATION`，问题帧为 `nvoglv64.dll+0xf1c729`，读取地址 `0xffffffffffffffff`；Java 当前链为 `ShaderProxy.draw`→`RasterDrawBridge`→`GuiGraphics.drawString`→`TitleScreen.render`，与 D06 的问题地址一致。新转储原位为 `Diagnostic/base/hs_err_pid73724.mdmp`、`6,328,407,108` bytes，未复制；fatal 文本、stdout/stderr、latest.log、run-result 和哈希已封存到 `A4-RESULT.md`。这是无临时诊断条件下的真实再现，不是正式修复。
- 用户操作边界：用户已直接报告 A4 启动后未进行任何操作，主界面尚未加载/显示即崩溃；不从日志推断更多操作。A4 结束后不自动重启、不开第二个客户端。A4 证明临时诊断层不是必要触发条件，但未区分应用资源/命令历史与驱动内部状态；下一步仅做现有 A4/D06 转储离线对比，不新增 D12 或同类 fixture。A3 的具体操作序列仍未记录。

### A3 重试：复用低开销 validation 配置（已结束；同地址原生崩溃）

- 目的/假设：按用户要求在同一隔离 `Diagnostic/base`、同一去 E-02 JAR/DLL/PDB 下重跑 A3 配置，观察 validation/sync validation 是否再次改变启动阶段结果；不添加新诊断，不改变正式渲染行为，不扩展独立 fixture。
- 文件/所有权：复用 `Launch/A3/Run-A3-InteractiveValidation.ps1`、`Launch/A3/vk_layer_settings.txt` 与冻结启动输入；新 run 为 `Evidence/run-a3-20260915-173716-468`，启动记录为 `20260915-152956-handoff/A3-RETRY-START.md`。本任务唯一新测试客户端 PID `87964`，父监控 PID `73736`；不操作其他 Java/游戏进程。
- 采集/启用：`VK_LAYER_KHRONOS_validation`、`validate_sync=true`、error/warn、重复消息上限 1；API dump、GPUAV、DebugPrintf、best-practices关闭。没有强制等待、额外同步、延迟释放、跳过绘制、关闭文字或屏蔽 OBS/RTSS。
- 产物/哈希：JAR `21CF6C1451B4021ADE6AEB53A9DFB931A40EFBE15B93C3D5D77FB34FC7B0D550`；实际匹配 `core.dll` `20F85170B43A822D93BFBD9FB12770CE5CCD78E2FAFC9B8B5E1B6AA62374BD89`；PDB `E177D9DE386308D3B99F112244AC02C47B1E94E5BBB32F64F8824052C081B2BA`；脚本/设置哈希见启动记录。
- 结果：Java PID `87964` 于约 `25.512750 s` 后退出码 `1`，问题帧为 `nvoglv64.dll+0xf1c729`，读取`0xffffffffffffffff`；与 A4/D06 相同。崩溃前记录一条`SYNC-HAZARD-WRITE-AFTER-WRITE`，属于有效同步观察但尚未证明是AV根因。新转储为`Diagnostic/base/hs_err_pid87964.mdmp`、`6,602,860,986` bytes，未复制；fatal文本、stdout/stderr、latest.log、run-result及哈希已封存到`20260915-152956-handoff/A3-RETRY-RESULT.md`。本次实际操作序列未由用户报告，不从日志推断。

### A4 用户启动尝试：未进入游戏（已记录）

- 用户执行无诊断启动命令后报告终端一闪而过、没有游戏窗口。现场没有新的`run-a4-*`目录、Java进程或转储；旧A4 run未变化。
- A4输入存在且哈希/隔离runtime预检通过，Windows PowerShell 5脚本解析通过；失败点仍未由可见错误确定，不归因于渲染器或产物。记录见`20260915-152956-handoff/A4-USER-START-FAILED.md`。
- 下一次仅调整启动宿主显示方式为`-NoNewWindow -Wait`，不调整诊断内容、代码、驱动或游戏配置。
