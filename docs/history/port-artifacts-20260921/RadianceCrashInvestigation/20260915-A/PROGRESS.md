# A 崩溃排查：真实 Minecraft A 取证回合（当前状态；历史记录保留）

当前阶段：已授权排查实施与真实实例取证。R-01/R-02保留，E-02已移除；原生驱动崩溃尚未解决。D08-D11独立无窗口负复现阶段已收束，不再新增同类fixture。允许本任务启动唯一隔离的Diagnostic/base实例，不允许ComputerUse/UI自动化或操作其他已有进程；不调整暂存区、不提交/推送。

## 2026-09-15 当前回合覆盖

- D10/D11已完成收尾、源码/EXE/PDB/日志/JSON已归档到`20260915-152956-handoff\D10-archive`和`D11-archive`；进程核对未发现`repro.exe`等所属测试进程，没有结束或触碰其他进程。完整指标和严格负结论见当前hand-off及DIAGNOSTICS.md。
- A2真实取证启动于2026-09-15 16:30:51 +08:00：监控PID 93224，Minecraft Java PID 48924，run `Evidence/run-a2-20260915-163051-131`。隔离JAR为`21CF6C...D550`，预期core.dll为`20F851...BD89`，PDB为`E177...B2BA`，启动前哈希预检通过；16:33:35在`VkLayer_api_dump.dll+0x498cb9`退出，不能作为渲染器崩溃结果。
- A2 API dump范围为0..2500，但启用全量详细时间/线程/帧/地址记录与逐调用flush；日志达到12,252,054,062字节，Java工作集约8.6GiB，随后API dump层自身发生DEP/空地址执行异常。同步validation开启，GPUAV/DebugPrintf/best-practices关闭；未屏蔽OBS/RTSS，未插入等待、延迟释放、跳过绘制或关闭文字。必要错误栈/配置/结果保留，12.25GB API日志与约9.09GB诊断层mdmp已移入Windows回收站。
- A3已于2026-09-15 16:42:04 +08:00启动并于16:56:05退出：监控PID 91132，Minecraft Java PID 74556，run `Evidence/run-a3-20260915-164204-116`。仅启用Khronos validation与sync检查，错误/警告重复消息限1；API dump、GPUAV、DebugPrintf、best-practices均关闭，未改变渲染同步或资源寿命。无新hs_err/mdmp；stdout/latest.log记录`SYNC-HAZARD-WRITE-AFTER-WRITE`与`SYNC-HAZARD-READ-AFTER-WRITE`，保留为观察证据，不作为修复或验收。
- 本回合人工边界：助手不操作窗口、不自动重启；A3已结束，未扩展D12或其他独立fixture。D06完整转储、A2/A3必要日志/配置和当前加载产物保留；D03/D04/D05替代转储、A2全量追踪、旧runtime和已归档诊断scratch按`20260915-152956-handoff/CLEANUP-20260915.md`移入Windows回收站。
- A4已于2026-09-15 17:13:02启动，监控PID 91172、Minecraft Java PID 73724，run `Evidence/run-a4-20260915-171302-062`；两者现已退出，未发出终止操作。启动前与启动时均核对同一去E-02 JAR/DLL/PDB哈希；`VulkanLayers=[]`，validation/API dump/sync validation/GPUAV/DebugPrintf/best-practices均关闭，仅移除子进程临时诊断环境变量。应用运行约28.247秒后在`nvoglv64.dll+0xf1c729`发生`EXCEPTION_ACCESS_VIOLATION`，读取`0xffffffffffffffff`，新mdmp原位为6,328,407,108 bytes；Java链为`ShaderProxy.draw`→`GuiGraphics.drawString`→`TitleScreen.render`。用户直接报告本次未做任何操作、主界面尚未加载即崩溃。A4是无诊断条件下对原崩溃的再次确认，原生崩溃仍未解决。
- A4结果、运行配置、证据文件哈希和证据边界已封存于`20260915-152956-handoff\A4-RESULT.md`；未改代码、驱动、Prism/存档/其他配置、同步或资源寿命。
- 用户要求复用A3配置再次启动；A3重试于2026-09-15 17:37:16启动，监控/父PID `73736`、Minecraft Java PID `87964`，run `Evidence/run-a3-20260915-173716-468`。启动前复核A3脚本/设置/JAR/DLL/PDB哈希与历史一致：仅加载Khronos validation并开启sync validation，API dump/GPUAV/DebugPrintf/best-practices关闭。应用运行约25.512750秒后仍在`nvoglv64.dll+0xf1c729`发生访问异常，读取`0xffffffffffffffff`；新mdmp为6,602,860,986 bytes。崩溃前仅见一条已知`SYNC-HAZARD-WRITE-AFTER-WRITE`，不是已证实的AV根因。结果已封存于`20260915-152956-handoff\A3-RETRY-RESULT.md`；不再自动重启。

## 已证实与待证实

已有 hs_err、运行 DLL 哈希与 command.obj relocation 证明崩溃在 vkCmdDrawIndexed 调用内部，触发链是字幕文本批次；未记录 VK_ERROR_DEVICE_LOST。具体无效对象尚未知，descriptor/UBO/生命周期/驱动均不是已证实原因。未把加载了 RTSS/OBS 当成致因证据。

Release 实例代码只在 DEBUG 中自行请求 validation；通过标准 Vulkan Loader 环境可启用层而无需改 DLL。D-01 无窗口 existing custom VertexArray fixture 退出0，真实精确12×12覆盖PASS；loader明确按 VK_INSTANCE_LAYERS 加载1.4.341 Khronos validation，日志中0 VUID。该结果仅证明预检 fixture 与诊断配置，不覆盖游戏调用链。

## 差异分类台账

| ID/类别 | 假设或目的 | 文件/证据 | 状态与移除时机 |
|---|---|---|---|
| D-01 临时诊断配置 | 标准validation能否在崩溃前报告首个实际非法Vulkan调用 | Launch/vk_layer_settings.txt、Run-A-Validation.ps1；复制冻结的4个Java参数/Classpath/Log4j文件；Evidence/run-*/console.log | 仅在独立PowerShell子进程设置Loader环境，未改系统设置；取得足够证据后、正式回归前，把Launch目录的诊断设置/启动脚本/专用参数文件移到Windows回收站，保留历史日志与本台账；不能只关开关 |
| 实验性行为调整 | 无 | 无 | 未强制等待、串行化、延迟释放、跳过绘制、关闭字幕或关闭功能；未关闭第三方注入层 |
| 正式修复 | 无 | 无 | 尚未找到首个有效validation错误，不凭候选原因修改资源行为 |

新增业务源码、原生/Java日志、探针、全局计数、诊断编译开关：均为0。没有恢复旧checkpoint/地址追踪设施。上述临时启动配置是当前允许保留的诊断检查点，不是完成清理的正式交付。

## 起点与产物隔离

- Baseline/Radiance、Baseline/MCVR：本轮开始时所有Git可见文件副本、缺失路径标记与SHA256清单，分支/HEAD、原index和status。
- Baseline/Radiance-formal-start.jar 和 core-formal-start.dll：正式起始产物封存。
- Diagnostic/base/mods/Radiance.jar：独立诊断副本，与正式起始JAR字节一致；没有静默覆盖正式A/B产物。
- JAR SHA256：3D19B2324F40E9758762519ED5FFDC3CE0F9399CFE4BE36DAD8F55A709E1C24D。
- DLL SHA256：AD8A0EC3460CE971554395777B4D6AAAEB6B5DD1BB0B112C7B763AF9F7FE991B。
- 原A验收的options、config、defaultconfigs、resourcepacks、saves及Radiance的options.properties/pipeline.yaml已复制到Diagnostic/base；未迁移/改写原文件，未复制旧pipeline缓存。
- 启动直接使用冻结的Java参数和单JAR，不执行Gradle、不重编译或更新mods。Baseline/diagnostic-inputs.json在启动前验证JAR、参数与validation库哈希。

## 用户复现命令

```powershell
& 'C:\Users\RavenYin\.cache\codex-runtimes\codex-primary-runtime\dependencies\native\powershell\pwsh.exe' -NoProfile -File 'D:\Workspaces\Artifacts\RadianceCrashInvestigation\20260915-A\Launch\Run-A-Validation.ps1'
```

这条命令会启动独立诊断游戏。进入复制的“新的世界”，保持原字幕设置并重走之前触发步骤；出现崩溃或首次明显异常后反馈。若没有复现，反馈运行多久和操作，不记为修复成功。每次运行生成Evidence/run-时间戳目录；stdout/stderr（含标准validation、loader、原游戏日志）写入console.log，退出后复制latest.log及hs_err。无需更改正式验收目录。

Java --dry-run已执行且退出0，只验证加载主类/参数，未执行游戏main。用户尚未执行诊断游戏，因此实际游戏层加载与首个VUID仍待确认。标准validation只报告warn/error，同条消息最多3次；同步专项/GPU辅助/DebugPrintf未开启，若普通检查不足再针对具体假设决定。

## 下一动作与完成门槛

读取用户复现的首个有效VUID及其前后上下文，区分根因与连锁错误；必要时定向证据，不铺设全局框架。确认后修正式所有权/调用契约/同步，并核同类消费者，补必要回归。最后移除D-01全部临时启动/诊断辅助，再单独构建正式产物并做无诊断回归；在此之前不能称修复完成。

D-01首次用户授权启动已执行：run-20260915-134206-338。标准validation实际加载，0 VUID，但独立启动参数缺Java -cp，NeoForge在mod扫描找不到MinecraftServer.class退出。现补冻结legacy classpath为真实-cp后重试，仅修改诊断启动参数，无业务代码/产物变化；dry-run不能证明完整Loader启动通过。

D-01启动参数补充：legacy classpath并非完整Java classpath；改从原hs_err已记录的实际java_class_path恢复完整平台classpath（含neoforge-21.1.250.jar），排除空packagedLaunch源码输出目录。没有重建项目、改写JAR。

D-01按用户最新明确要求启动：run-20260915-134335-546，游戏窗口已出现，尚未操作画面。标准validation已取得首个有效错误VUID-VkImageMemoryBarrier2-image-03320：depth/stencil格式图像在未启用separateDepthStencilLayouts时barrier只覆盖部分aspect；随后09600报告stencil仍UNDEFINED。此为实际违反契约的证据，尚未证明等同原vkCmdDrawIndexed访问异常根因。后续需要按图像所有权/布局契约修复及回归，不以消失单条错误冒充原崩溃已修。

D-01本次启动最终结果：PID79440在13:44:05再次EXCEPTION_ACCESS_VIOLATION，nvoglv64.dll+0xf1c729，退出-1073740791；hs_err已随console日志保存run-20260915-134335-546。此前'窗口已出现'不是持续运行成功。另PID57776在13:42:57已由其他启动路径运行，未关闭/操作。下一步处理首个03320布局契约错误，并区分其与原驱动崩溃的关系，正式修复仍未完成。


## R-01 实施中（根代理独占本轮文件）
- 正式修复：overlay D32S8 barrier同时覆盖depth/stencil，并与render pass initialLayout保持DEPTH_STENCIL_ATTACHMENT_OPTIMAL；深度附件读写依赖覆盖early/late测试。未启用separateDepthStencilLayouts规避契约。
- 同类修复：抽出已有formatAspects映射至vulkan/image_format.hpp；DeviceLocalImage::fullSubresourceRange按format而非usage确定全部aspect。采样view和单aspect读回保持原语义。framebuffers.hpp沿用原命名接口。
- 已查同类：UI diagram和worldPostDepth仍是D32；framebuffer_readback已有formatAspects；纹理上传、导入shader资源及ray tracing使用fullSubresourceRange随修复受益。无等待/串行化/跳过绘制实验。
- 回归：现有framebuffer无窗口GPU用例改用生产range helper，D32S8增加只读布局往返，验证depth及stencil写掩码读回；不启用separateDepthStencilLayouts。待编译执行。
- 修改文件：MCVR/src/core/vulkan/{image.cpp,image_format.hpp}、src/core/render/{framebuffers.hpp,modules/ui_module.cpp}、tests/{CMakeLists.txt,framebuffer_gpu_test.cpp}。无新增诊断源码。D-01启动辅助暂保留用于复核，尚未到正式清理门槛。
- 下一动作：Release编译、validation下GPU/相关回归；分别封存修后诊断JAR/DLL及哈希，再启动独立实例复现。此处R-01只表示已确认契约缺陷的修复，不代表原驱动访问异常已解决。

## R-01 验证与修后诊断启动
- Release core编译、cmake安装、Gradle build/test/bootstrapTest/单JAR资源检查通过。原生CTest 24/24。证据：r1-native-build.log、r1-native-install.log、r1-java-build.log、r1-ctest.log。
- 首轮framebuffer GPU像素断言虽PASS，但validation出现夹具自身view usage、shaderDemote feature、D16 sampled usage错误，未记为validation通过。已修夹具：transfer-only图像不创建无消费者view；查询并启用shader实际必需feature；D16深度布局配合法usage。
- 修后r1-framebuffer-validation-02.log：标准validation明确加载，0 VUID；D32S8深度/模板掩码、布局往返、MainDepth与depth blit真实读回通过。未启用separateDepthStencilLayouts。
- 修后诊断产物独立封存Products/diagnostic-r1；JAR 0FF46AC78AF6812E281D99BAE0E94E1FD731AB4A204620464A04329B0583E430；core.dll 2017B205DD2B4A06C626401C6019B154BF746C94E1492C7B2AE8B7DBC396FBED。已核包内DLL字节一致。source-freeze.json记录6个变更源码哈希。
- 临时诊断仍只有D-01 Launch配置，无业务源码探针；实验性行为调整0。未覆盖历史正式A/B或Baseline起始产物，只更新独立Diagnostic/base并保存r0输入清单。
- 下一步按用户授权启动修后诊断实例，等待实际世界/HUD复现。原驱动崩溃是否已解除尚未确认，不能称正式验收完成。必要时保留此诊断检查点；取得充分证据后才清理D-01并进行正式无诊断产物回归。
- 同类检查补充：ui_framebuffer、ui_framebuffer_blit、ui_diagram_target及framebuffer_readback已有按format的双aspect屏障，无需重写。fullSubresourceRange的纹理消费者是下载读回，不是此前台账误写的上传。
R-01实际启动：run-20260915-135549-676，PID91832，13:55:49启动；13:56窗口已出现，当时0 VUID/访问异常。未操作画面，世界/HUD复现等待用户。Java实际120项：118通过、2硬件窗口跳过、0失败/错误；两仓index哈希与本轮Baseline一致。此为诊断检查点，正式无诊断交付未完成。

## R-01游戏复核失败；E-01限定对照
- 修后run-20260915-135549-676在13:56:16（26.93秒）再次nvoglv64.dll+0xf1c729访问异常，0 VUID。Java栈为TitleScreen按钮文本→ShaderProxy.draw，证明不限于字幕/HUD。03320/09600已消失，R-01未解决原CPU驱动崩溃。
- E-01实验性启动调整：同一R-01 JAR/DLL、同一D-01 validation，仅当前子进程VK_LOADER_LAYERS_DISABLE=VK_LAYER_OBS_HOOK,VK_LAYER_RTSS。测试旧API的第三方层是否参与此驱动异常；不退出/配置OBS或RTSS、不关闭游戏功能、不更改正式启动。
- 依据Khronos Loader标准过滤接口：https://github.com/KhronosGroup/Vulkan-Loader/blob/main/docs/LoaderLayerInterface.md#layer-disable-filtering 。记录loader确认过滤及崩溃/存活时间。实验结束随子进程退出移除env，无新增源码辅助；成功也不等同正式修复。
E-01已执行：run-20260915-135756-089、PID41476，loader确认只禁用OBS/RTSS两层，standard validation保留；13:59观察进程存活83秒，0 VUID/访问异常，明显超过上次26.93秒。仍是对照线索，等待用户确认主菜单画面；不能由此认定某一层致因或把禁用层作为正式修复。层文件版本/哈希已存Evidence/third-party-layer-versions.json。

## E-01最终失败；R-02交接契约修复实施中
- 用户确认主菜单正常，随后实际进入世界。E-01运行151秒时已出现6条validation错误（01198 newLayout=UNDEFINED及09600 firstHitDepth GENERAL/UNDEFINED冲突），不能沿用更早0 VUID结论。
- 用户反馈已正常退出，但实际进程日志为14:00:55 PauseScreen按钮文字→ShaderProxy.draw访问异常，nvoglv64.dll+0xf1c708，178.87秒，退出-1073740791。保存日志证实“用户执行退出”不等于“正常退出成功”。排除“只要不加载OBS/RTSS就解决”的假设；不再使用该实验作为方案，后续恢复原层组合。
- R-02源码证据：Framework::submitCommand后录制world、提交顺序world→overlay；fuseWorld先录制的writeMainDepth却读取CPU当前layout（首帧UNDEFINED）并记录恢复该布局。正式修复由PostRender生产者在最后统一导出firstHitDepth为SHADER_READ_ONLY_OPTIMAL；HUD只按约定采样，不提前读取/修改生产者布局。不调整提交顺序、不额外等待。
- R-02文件：ui_framebuffer.cpp、world/post_render/post_render_module.cpp、tests/framebuffer_gpu_test.cpp。GPU fixture新增“消费者先录制、生产者先提交”的两个command buffer真实深度转换/读回用例。编译中。
- D-02下一定向诊断：仅SDK标准GPU-assisted validation检查动态索引descriptor和shader缓冲访问，配置仍在Launch/vk_layer_settings.txt，无诊断源码；标准校验未覆盖这类动态访问且CPU崩溃仍重复，是启用理由。D-02和D-01一并在最终正式回归前清理。

## R-02已编译与无窗口验证；D-02启动
- R-02 core Release及安装、Gradle build、Java测试和分发JAR核验通过；native24/24。r2-framebuffer-validation.log与r2-framebuffer-gpuav.log均0 VUID；后者确认GPU-assisted生效，未支持的ray query/mesh等检查由层说明关闭，不冒充覆盖。
- 分别封存Products/diagnostic-r2的JAR/DLL、hashes.json及8文件source-freeze.json；包内DLL核验一致。r1产物和各运行证据继续保留，未覆盖历史正式A/B。
- 下一次启动恢复OBS/RTSS原层组合，仅增加D-02标准GPU-assisted检查；无源代码探针。当前实际目标仍是修复CPU驱动崩溃并复核R-02游戏世界路径，尚未正式交付/清理。
D-02已启动run-20260915-140539-426，PID48008，14:05:39；修后R-02 JAR=798BEFF493270D1FD2842AD223CE84EF1B83A0A46A6F81FA822B3B41DFE00E16，DLL=01E1F7871FE99AFBEF25E2C610F818DE9EDD263D3F16DCB5539794B28174923F。当前由根代理独占8个修复/测试文件，未调用子代理。GPU-assisted已在日志确认，初始化较慢，暂未记录实际游戏复核完成。

## D-02未定位；D-03标准CPU转储准备
- D-02 run-20260915-140539-426于14:07:35再次nvoglv64.dll+0xf1c729（116.40秒），0 VUID。GPU-assisted未在CPU绘制录制异常前取得非法shader访问证据；不能认定descriptor/UBO已排除或已定位。
- driver反汇编：f1c708/f1c729落在同一条描述符记录读取路径，比较记录类型6/8等；这仅将取证缩小到驱动描述符消费现场，不足以证明应用的具体绑定错误。
- D-03准备：使用已安装Windows SDK setup仅layout下载标准命令行调试器到Scratches/RadianceCrashD03，不安装/启动调试GUI；下一诊断只加JVM标准CreateCoredumpOnCrash参数，在真正fatal时保留一次原生现场。新建诊断源码仍0。诊断选项/临时SDK下载随最终清理移至回收站，dump和分析结果作为证据保留。

## D-03现场 / D-04匹配符号诊断构建
- D-03 run-20260915-141149-874，PID81388，在14:12:18重现；完整mdmp约6.6GB，使用Microsoft签名CDB离线读取，记录Evidence/d03-cpu-stack.txt。保留dump在Diagnostic/base/hs_err_pid81388.mdmp，未上传。SDK仅布局下载并管理解包到Scratches/RadianceCrashD03，未安装或启动GUI。
- 完整栈确认core→Khronos validation→NVIDIA；旧Release没有匹配PDB，CDB显示的就近JNI导出名不能当作函数名。driver读取descriptor记录时R9=-1，仍不认定哪个应用对象/所有权错误。
- D-04临时构建配置：仅build/CMakeCache中Release CXX增加/Zi、shared linker增加/DEBUG:FULL /OPT:REF /OPT:ICF，保留/O2优化；不改业务源码或日志。原值保存Evidence/d04-original-cmake-flags.json。诊断DLL/PDB/JAR独立保存，最后恢复原构建flags并重建正式产物，此配置不能混入最终无诊断回归。
- 目的：匹配本次实际DLL的类型/局部/函数符号，定位descriptor生命周期/绑定对象。没有恢复checkpoint、地址追踪、全局计数。后续只运行这一有界取证，不继续增加猜测性的资源行为修改。

## D-04符号版已封存并启动（当前有效检查点）
- 当前阶段仍为已授权错误排查与修复；用户确认已执行退出操作，不将历史只读阶段恢复为当前指令。工程与原崩溃修复均未完成。
- D-03匹配VVL符号离线核验：当前两项dynamic UBO均updated，buffer、descriptor set、当前layout未销毁。原pipeline创建时layout和set layouts已销毁；此现象不是已证实契约错误或驱动根因。原生set映射与内存现场已存d03-native-set-handle.txt及d03-native-descriptor-memory.txt。
- D-04 Release core/安装与distributedJar、verifyDistributedJar、verifyRuntimeResources通过；源码8项与R-02冻结逐项一致。仅增加优化构建符号，没有新增修复/诊断源码，不沿用为新游戏回归通过。
- Products/diagnostic-d4-symbols独立保存JAR/DLL/PDB及哈希；JAR FB343DDD0FAF89EB32CEC0159CC212391ADF6629C024835BDFF1CB21F931F2FA，DLL 10B245ECD252732C308A5A68F0A1B666C92760EB03B5D1D558EA2305D5CD2E7D，包内DLL一致。
- 已按用户启动授权运行run-20260915-142838-387。标准validation，恢复原层组合，GPUAV关闭，保留标准fatal dump参数；尚未确认主菜单/世界或正常退出。未操作游戏UI。
- 根代理继续独占8项R-01/R-02源码，无代理调度。下一动作：用本次匹配core PDB核对实际调用对象；不凭layout销毁或主菜单短暂成功添加生命周期猜测性修复。
- 待清理：D-01/D-02/D-03启动配置与辅助、D-04 CMake临时符号flags、Scratches调试器下载/解包；证据和诊断产物独立保留。正式无诊断重建/回归尚未进行。

## E-02有界生命周期对照准备（不是正式修复）
- D-04已在27.22秒再次TitleScreen文字绘制访问异常；匹配core PDB明确为UIModuleContext::drawIndexed→CommandBuffer::drawIndexed→validation→NVIDIA。证据d04-symbol-stack.txt和d04-ui-draw-locals.txt；实际shaderId=3，indexCount=180，dynamic uniformOffset=0x1680。未成功启动稳定实例。
- 假设：创建pipeline时使用的旧layout释放，与驱动CPU描述符消费异常存在关联。VVL当前有效绑定并未报告销毁，旧layout销毁本身不能作为违反Vulkan契约的结论。
- 实验性行为调整E-02：仅dynamic_pipeline.hpp/.cpp让DynamicGraphicsPipeline暂持创建时DescriptorTable，生命周期截止pipeline销毁。与标准D-04配置对照，不加wait、不跳绘制、不改变buffer数据或descriptor内容。
- 两文件实验前快照Baseline/e02-before。证据记录编译、产物哈希、崩溃/存活及validation；实验后移除这两个明确新增hunk，不能将延迟释放当正式修复。若不复现，再用无窗口有界复现验证具体所有权或驱动兼容关系。

当前最新授权更新：用户允许按信息缺口扩大诊断并要求统一台账，已建立DIAGNOSTICS.md记录D-01至D-04、E-01/E-02及R-01/R-02边界；禁止自行启动交互游戏立即生效。D-04启动发生在此前用户启动授权下，已崩溃结束。E-02只编译/封存，后续提供用户单行复现命令。

## E-02封存完成，等待用户一次复现（未自行启动）
- Release core、安装、distributedJar/verifyDistributedJar/verifyRuntimeResources通过；包内DLL核验一致。Products/diagnostic-e2-layout-lifetime记录JAR/DLL/PDB哈希及10文件冻结。R-01/R-02的8项源码逐项保持冻结，另两文件只有E-02实验hunk。
- JAR=7CBC7B5915D5DA856D051BB4009F5F1067BC0CFA7B64EA6BDD2DD4144D807E51；DLL=5DA0FFEC2BC35B77C496D94C88B85C47BCEF703EB9C02F60B0543DE238B7084A。
- E02-REPRO.md提供单行命令及主菜单→复制世界→暂停菜单→保存退出的一次复现步骤；日志Evidence/run-*，fatal转储Diagnostic/base。正式A/B未覆盖，暂存区哈希未改（Evidence/e02-index-check.json）。
- 当前DIAGNOSTICS.md为逐项诊断台账。根独占10个修复/测试/实验源码，无子代理。下一动作等待本次用户复现证据，再移除E-02实验hunk并判断是否需针对layout/descriptor的独立最小GPU复现；不得把实验存活当正式修复。

## E-02首次用户复现：未复现原异常（不是正式修复）
- 用户反馈“这一版看起来没什么问题”。对应run-20260915-143633-077，14:36:33至14:38:12约99秒，进入复制世界，保存/暂停及Minecraft Stopping记录存在。退出码未由启动器写入文件，不能编造exit 0；目录内六份hs_err均为启动器复制的历史记录，不属于本轮。
- 标准validation已实际加载，console中0 VUID、0 Validation Error、0 EXCEPTION_ACCESS_VIOLATION；运行时core.dll哈希5DA0FFEC2BC35B77C496D94C88B85C47BCEF703EB9C02F60B0543DE238B7084A与E-02产品一致。Evidence/e02-run-review.json。
- 有效结论：E-02一次实际世界/菜单运行未复现，支持进一步调查创建时DescriptorTable存活期。它同时保留pool、sets、set layouts和pipeline layout，且改变内存复用/时序，尚不足以指认某对象或证明永久保留整表正确。
- 下一信息缺口：通过独立无窗口用例拆分旧pool/sets与旧layout释放，读取真实像素，并覆盖兼容layout下dynamic UBO的indexed draw；不能把整表延期释放直接升级为正式修复。已读现有framebuffer GPU harness，下一实现优先独立Scratches复现以保持业务修复冻结。
- 本次未构建、未启动游戏、未新增业务源码变动；已更新证据/台账。E-02源码实验仍在，独立诊断产品保留；最终清理和无诊断正式回归未完成。

## 用户要求清理未证实修复：E-02源码已移除，重新构建中
- 已按精确hunk删除dynamic_pipeline.hpp/.cpp的experimentDescriptorTable_和持有赋值；两文件逐字节哈希与Baseline/e02-before一致，src/tests检索实验标识零残留。没有整文件回退、暂存区操作或撤销其他变动。
- R-01/R-02八项源码逐一匹配原冻结；两项已有明确validation契约证据，保留。它们并未被宣称解决CPU崩溃。
- 本次是去除行为实验后的诊断对照版；D-01标准validation、D-03标准fatal转储、D-04优化符号构建保留以取证，不是最终无诊断正式验收产物。GPUAV与第三方层过滤均不启用。
- 正在重建Release core；完成后封存新产品和哈希、核包内DLL、更新独立Diagnostic/base。不会自行启动交互游戏，命令交用户。

## E-02清理后诊断版已交付（当前启动版本）
- Products/diagnostic-e2-removed：JAR 21CF6C1451B4021ADE6AEB53A9DFB931A40EFBE15B93C3D5D77FB34FC7B0D550；DLL 20F85170B43A822D93BFBD9FB12770CE5CCD78E2FAFC9B8B5E1B6AA62374BD89；PDB及10项源码冻结/哈希同目录。实验两文件恢复至实验前字节，仅按hunk清理。
- Release core编译、安装及distributedJar/verifyDistributedJar/verifyRuntimeResources通过；包内DLL一致。未重复完整Java/CTest，也未进行本版本游戏验收，不能沿用E-02单次未复现结果。
- 独立Diagnostic/base/mods/Radiance.jar已切换并更新启动输入guard；E-02旧产物、输入哈希和证据保留。正式A/B及暂存区未变。
- 当前标准validation/匹配PDB/fatal dump仍保留；只有未经证实的行为实验已清除。启动命令仍为Launch/Run-A-Validation.ps1，由用户运行，助手本轮未启动游戏。
- 下一动作：用户用去实验版复现，记录菜单/世界/暂停/退出和运行时长；读取新run证据。原崩溃根因未确认，正式无诊断交付未完成。

## 当前恢复：继续寻找正确修复，D-05有界并行取证
用户要求最终正确修复；不自行启动游戏。去E-02版本实际23.07秒同址CPU异常，进程已结束，新dump不完整。已核现存代理均完成后仅派Sol High descriptor_repro独立scratch无窗口复现（不改仓库）；根离线分析已有完整D04与源码。E-02保持移除，R-01/R-02保持冻结。目标是可重复区分pool/sets/layout及maintenance4行为，不将延迟释放当正式修复。DIAGNOSTICS.md D-05记录当前文件所有权/实验影响，下一动作审查复现结果并决定独立修复。

## D-05中间结果及D-06真实调用追踪准备
- full-array八组(keep/layout/pool/all × maintenance4 0/1)各30秒均退出0；标准VVL无VUID、真实像素匹配。sparse业务销毁顺序/implicitreset前两组也退出0，完整结果待worker收束。不能把负复现当原游戏已经正确。
- 根额外离线映射：D03 faultR8位于当前set1 layout对应原生分配范围，wrapped10120000001012映射raw f8bb7e62d0；VVL对应layout alive。Evidence/d05-driver-pointer-owner.txt、d05-current-set-layout.txt。此为范围关联，不声称驱动私有结构已完整还原。
- D06已准备标准SDK API dump前300帧完整调用关联，pre_dump+flush，保留当前去实验版JAR/PDB，无业务源码新增探针。Launch/D06/Run-A-ApiTrace.ps1语法验证通过；稍后无窗口smoke确认实际层输出，再交用户单行命令，不自行启动游戏。
- 当前未实施新的正式修复或恢复E02。根持有源码/索引，worker只scratch独立复现，两线程无文件冲突。继续以正确性证据为收口条件，原CPU崩溃仍未解决。

## D-05/D-06当前可交付诊断检查点
- 独立无窗口12组×30秒全部0error，另按游戏API1.3追加sparse/all/业务顺序/implicitreset×maintenance4 0/1各10秒均退出0、像素匹配/VUID0(见d05-api13-runs.json)。没有复现游戏异常，不据此提交生命周期或feature“修复”。
- D06标准API_dump+validation已经无窗口smoke确认实际输出，Launch/D06输入守卫全部通过；当前JAR仍21CF6C1451B4021ADE6AEB53A9DFB931A40EFBE15B93C3D5D77FB34FC7B0D550、DLL20F85170B43A822D93BFBD9FB12770CE5CCD78E2FAFC9B8B5E1B6AA62374BD89，无E02实验标识。无新业务源码更改，无提交/暂存区更改，无游戏自行启动。
- 用户下一具体动作：运行Launch/D06/Run-A-ApiTrace.ps1，先停主菜单；若未异常，再复制世界/暂停菜单/保存退出。首300帧API追踪、validation和退出结果共同存Evidence/run-d06-*。发生未响应时等待命令结束，避免再次截断fatal dump；助手收到反馈即关联真实create/update/bind/destroy/submit链。
- 当前工程授权有效，原崩溃最终修复未完成；E02已清除，R01/R02独立修复保留。临时诊断台账DIAGNOSTICS.md持续维护，D05 scratch与D06辅助待最终回收。不能把无窗口负复现、一次实验存活或新诊断配置当作正式修复。

## D-06首次实际游戏日志核对
- run-d06-20260915-151251-845已执行启动，JAR哈希匹配当前去实验版。PID88976于15:13:27、运行35.814883秒发生EXCEPTION_ACCESS_VIOLATION，nvoglv64.dll+0xf1c729。15:14:38命令结束，退出-1073740791。进程现已结束，不是仍在初始化。
- Java为TitleScreen文字→ShaderProxy.draw；匹配PDB原生栈core UIModuleContext::drawIndexed→CommandBuffer::drawIndexed→API_dump→validation→NVIDIA，仍与旧故障一致。console零VUID/Validation Error，不代表正确。
- API trace66936071字节，在Frame299、Time19498897us停止，早于崩溃；首300帧范围没覆盖真实触发尾部。此设置不足由助手负责，不把该trace称完整崩溃调用链，也不归因成用户未正确运行。尚未改变配置要求再复现。
- 新mdmp6892667961字节，CDB已成功读取，Evidence/d06-crash-stack.txt确认完整现场可用。本次用户只要求核日志；未改业务源码、未构建、未启动游戏。下一步先利用新完整dump与已得早期API关联，若必要再调整捕获范围，不立即让用户重复同样命令。

## 当前继续：D06完整现场 + D07动态状态对照
已核活跃代理，仅复用descriptor_repro独立新scratch D07；根离线D06dump。D06实际崩溃帧1599，原300帧捕获不足已有精确证据；当前pipeline/layout不在早期API段，不能仅靠该日志完成重放。D07覆盖生产EXT动态状态并40秒keep/all对照。业务保持E02已移除，R01/R02冻结，无新的猜测修复/游戏启动。下一动作核动态状态对照和完整descriptor状态，再选择有证据的代码修复或补全调用记录。
