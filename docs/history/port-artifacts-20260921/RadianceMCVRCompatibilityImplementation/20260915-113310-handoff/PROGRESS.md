# 最新有效检查点：工程交付完成，暂停待用户 A/B 人工验收

本任务最初只读交接已结束，用户本人正式授权后的实施已连续执行并完成工程收口；本次实际修改源码、构建、测试和调度有界代理，绝非只读。
Goal 工具现为 complete，对应源码/工程验证/单JAR/人工材料交付完成；不表示游戏画面或完整组合运行已通过。此前 blocked/null/active 均为下面保留的历史检查点。
最终入口：DELIVERY.md；18WP/52CG：Reports/workpackage-status.json；边界：Reports/final-verification.md；A/B：Reports/manual-acceptance.md。
最终源码冻结1199路径，复核0漂移。Java120项（118通过、2窗口测试跳过）；native24/24；内置shader237/237；JNI161导出齐全。final-reviewed-release.log统一Release/单JAR/A-B PASS，包含最后scene法线修正。JAR SHA256=00B73619F741F7FFEA876B34800DCF2682C5628A9609B66169EAE69DBFC3D836。
所有权：根已回收全部源码与报告；remaining_matrix/veil_reload已完成，未留后台工程任务。两仓HEAD/分支与原基线一致，index字节hash不变，staged为空；没有提交/推送/发布/结构重组，没有操作用户实例或启动交互游戏/UI。
剩余：用户A/B实际Mixin装载、完整组合运行和视觉验收；运行验证边界、未知消费者支持限制、Debug CRT与captureCanvas历史限制见最终报告。下一具体动作由用户按manual-acceptance.md启动A/B并回传结果；代理在此暂停。

---
以下为保留的按阶段历史记录；不能用其中旧授权/goal/未开始判断覆盖以上最终检查点。
# 当前实施索引

## 有效授权
2026-09-15 协调任务转达用户明确恢复全部剩余公共渲染承接与完整合法组合源码兼容；取代初始只读暂停。连续实施至工程交付，目视由用户执行。

## Goal 工具限制
现有目标仍被工具标记 blocked。get_goal 已核；create_goal 被拒绝：unfinished goal；update_goal 仅支持 complete/blocked，无 resume。未虚报 complete，继续执行已授权工程；最终实际完成再更新。

## 接管基线
Radiance 1.21.1-neoforge@414d8e330a2fc6cb1e8630cc95f2302b2b97a0e8；MCVR develop@9905c81b1999f5845bf66d13501d371c16adf561。Baseline 保存 1125 Git可见文件副本、manifest SHA256、两仓原 index 与 staged/working binary patches。保留所有旧材料。
引用旧实施 20260915-063333 和 20260915-082227-handoff，旧顶层过期，不继承其完成判断。

## 所有权
当前 list_agents 仅根；旧树不唤醒。根负责共享JSON/CMake、native编译与统一整合、世界/Sable路由。Sol High /root/veil_reload 仅Veil VanillaShaderCompiler reload bridge/mixin与定向测试，禁止派生。Gradle串行。

## 当前结论与下一步
Sable custom layout/indirect源已存在，缺5 mixin注册与GPU fixture注册，最新native未闭环。根接入并验证。Veil reload存在GL能力调用，由worker修复。Flywheel/levitite历史报告分别有后续检查点，须核最终源码与证据，非游戏验收。剩余18包、统一Release/JAR/A-B交付尚未完成。

## 边界
不调整index、不提交推送发布、不恢复结构重组、不改用户实例存档配置、不启动交互游戏或UI自动化；最多根+2子代理。旧报告只读；源码/测试通过范围分别记录。

## 11:40 接续里程碑
- 已登记5个Sable/Veil custom VertexArray mixin和无窗口GPU fixture。
- 恢复setupRender截断遗漏的Sable preRenderChunks/updateCulling调用；尊重capturedFrustum，不吞调用错误，未安装Sable不加载可选实现。
- Java compileJava/compileTestJava/generateJniHeaders PASS（Evidence/java-culling-build.log）；不等于Mixin运行或场景验收。
- native core+custom array fixture仍在编译，无最终结果。
- Luna Max /root/remaining_matrix只读核18包，报告新Reports/remaining-matrix.md；旧报告不写。

## 11:44 集成检查点
Release core+custom VertexArray fixture编译成功；native-integration-tests.log 7/7通过（custom VertexArray/FBO/tessellation GPU、instancing/readback/world合同与JNI覆盖）。旧BuiltinPacks源码seal当前0差异，可引用原237变体静态证据。
新增EndSea固定消费者接续：原renderDebug前注入被主体替换绕过，现保留其完整渲染实现并排到fuse后、Veil AFTER_LEVEL post之前，使用真实主目标color/depth；WorldRasterPass恢复矩阵/fog/viewport/target/状态。该路径仅针对明确世界raster效果，不代表PT捕获完成。新Java尚待统一编译。
Veil reload worker初版2测试通过，根已登记mixin；正在复核重叠reload generation，不能认定最终通过。

## 11:54 当前里程碑与所有权
- java-world-effects-reload-tests.log：联合Java与定向测试通过，包含EndSea/WorldRaster/Veil AFTER_LEVEL scope；后续数组uniform与section storage变动不在该证据内。
- Veil worker补同mask重叠reload ticket后5测试通过；随后发现固定float[6] VeilBlockFaceBrightness需要按名数组打包，现授权其扩展ShaderRegistry/ShaderProxy，仍在实施。
- 根新增SectionRasterStorage：Sable外部section的原MeshData被native PT消费关闭后，保存独立vertex/index副本供真实持久VertexBuffer离屏消费者；render-thread drain验证section owner/compiled identity/origin，清世界清队列。SectionRasterStorageTest验证源内存关闭后副本与排序索引完整，待联合运行。
- 根暂冻Java，Gradle窗口给veil_reload；Luna remaining_matrix仍只读报告。下一步闭合数组/section测试，继续18包和特定world shader输出，最终产物未交付。

## 11:58 重要验证修正
复核发现旧custom VertexArray GPU fixture和生产设备均未显式开启drawIndirectFirstInstance；生产也未开启multiDrawIndirect。故此前像素PASS不足以证明调用合法。根已在生产Device启用设备实际支持的两特性，在JNI验证enabled能力、maxDrawIndirectCount、4字节对齐与真实index存储大小；修正indirect末条无需完整stride尾padding的边界。
fixture现启用两特性并用两个3-index indirect command拼成目标12x12区域，覆盖multi draw和firstIndex/vertexOffset/firstInstance。native-indirect-features-build.log正在编译，重新GPU结果待记录。
Veil worker数组uniform等与根section/world scope共37定向Java测试已通过，报告正在freeze；根随后修复Sable动态program同mask源码更新cache key，采用source SHA256并异步释放旧future结果，Java还须复编。

## 2026-09-15 12:23:03 用户本人授权恢复（当前有效阶段）
当前阶段：已授权实施。最初只读交接已经结束；历史取消、只读与错误blocked不覆盖当前用户指令。工程目标尚未完成，连续执行至最终源码验证、单JAR及A/B人工验收材料交付，随后暂停等待用户目视。
事实记录：此前本任务已实施源码、构建测试并调度代理；上一份只读回复不能否定这些实际成果，不据此撤销。
Goal：本次get_goal返回null，已按原工程目标创建active goal，无token预算。工具状态与工程完成度分别记录。
恢复核验：18WP/52CG；Veil冻结13文件当前SHA256全部一致；修正indirect设备特性之后的native-indirect-features-build.log确实core/fixture链接成功，indirect-enabled-gpu-run.log复测1/1通过。旧7/7不代替修正后证据。最终统一验证仍待完成。
所有权：根负责Flywheel backend选择、共享JSON/native、world/Sable与最终整合。Sol veil_reload已归还旧shader所有权，现负责Simulated diagram真实shader消费者及相关专用mixin/tests；Luna remaining_matrix只读调查Sable/levitite extra layer及EndSea shadow缺口。含根最多3，不派生。Gradle当前由根统一管理。
下一动作：修复Flywheel相同priority及旧GL backend配置选择风险；并行核固定消费者，之后统一验证并更新18WP状态，不以记录缺口代替修复。

## 恢复实施后的第二检查点
根已修复Flywheel优先级/旧GL偏好映射并注册mixin；定向Java2测试与编译PASS，后续dispatcher错误传播变动待统一Java。native全部Release目标构建PASS；24项CTest有23通过，透明语义合同因仍搜索旧单一分支而失败。核实实际shader已显式区分additive/lightning/glint/crumbling/source-over后，更新合同逐分支检查，定向复测PASS，未修改shader效果。
发现Create CubeParticle真实自定义ParticleRenderType.begin绑定Ponder blank贴图，旧捕获未调用begin而错误使用粒子图集。根补公共custom particle begin/真实纹理与blend/depth元数据捕获，仍PT提交；新增代码初编符号错误已修，待复编。
Sol拥有VeilShaderBridge/SimulatedDiagramCompatibility及专用consumer，根不触碰；Luna继续有界world消费者调查。最终产物尚未交付。

## 固定shader与粒子验证里程碑
Simulated消费者bridge、真实属性layout、spring原数据恢复：22定向Java测试、固定JAR六组12 shader stage离线Vulkan编译PASS，见simulated-shader-consumers.md；不能推导实际Mixin/画面通过。staff_overlay资源缺失仍需核实际消费者是否产生顶点，不能伪造资源。
根ParticleTypeCapture/Flywheel preference定向4测试与Java编译PASS（particle-and-backend-tests.log）。Sable反射错误不再一次warning后返回合成空集合/原相机；已安装却接口/类缺失明确失败，待统一Java。
Sol转入Flywheel/Sable embedding和BER回退的有界修复调查；Lunaworld消费者调查仍在执行。根持有EntityProxy/ParticleTypeCapture/SableSubLevelBridge/共享配置及native。Gradle空闲。

## Sable/Flywheel scene光照实施中
根已实现两native接口updateInstanceLighting/uploadLightSectionScene，沿用绝对section所有权与原6564byte采集数据，新增scene/relative坐标metadata。GPU section.w区分scene；appearance保持368byte，将原pad用于lightScene、entityLight0.w保存skyScale。独立lightTransform不再被world_prepare覆盖；shader按scene采样并缩放真实sky通道；embedded物理transform补engine origin。新增CPU坐标合同待执行。
Sol已写对应Java与Sable条件mixin、embedding继承/删除/dirty关系更新及移除强制BER覆盖，正在compile/JNI/定向验证。根native core构建运行中。新BuiltinPacks-final已237/237静态SPIRV PASS，旧seal不再覆盖新输入。

## 当前世界资源与统一验证检查点
当前仍已授权实施，goal active；工程交付尚未结束。
根已补世界额外层真实持久buffer与可见section列表：Aeronautics两种Levitite层不再混入普通PT材质；真实PBR128字段转换BLOCK32副本供原shader/索引消费，外部section保留全部层。Veil真实stage callback、fixed buffer/layer draw按after-fuse执行，EndSea shadow先于sea，最终post随后，保留原消费者。新增异常恢复由Sol负责，9Java已freeze并交还。
world-final-contract-tests.log联合Java/JNI/定向测试PASS；java-final-tests.log全量Java+bootstrap共118项，116通过、2硬件窗口测试跳过、0失败。native-final-build.log全Release目标PASS，native-final-tests.log 24/24PASS。以上不证明实际Mixin装载或游戏像素。
当前根正在prepareRuntime/build及隔离A/B目录打包（release-distribution-build.log），尚未最终成功。Luna独立核scene光照/ABI；Sol只读核Offroad破坏进度与staff_overlay实际消费者，分别拥有embedding-review.md和offroad-staff-consumers.md，不改代码，不派生。
下一动作：闭合审查发现，最终源码freeze、18WP/52CG实际状态、单JAR与依赖哈希、A/B人工指南。旧报告的过期“未开始”不覆盖本里程碑；源码和对应证据继续分别记录。


## A 基础组首次实际启动：失败（最新运行结论）
用户明确要求启动A验收，已执行runManualBase，使用交付哈希00B73619F741F7FFEA876B34800DCF2682C5628A9609B66169EAE69DBFC3D836及独立Acceptance/base。2026-09-15 13:13:47客户端在MainTarget Mixin装载失败，进程退出1，未进入主菜单。
直接错误：MainTargetCompatibilityMixins的@Shadow width未在MainTarget目标中找到。证据Evidence/acceptance-a-launch.log。A运行验收未通过；此前工程build/测试通过不能覆盖此运行阻塞。本次未修改源码或重打包来替换验收产物，未操作用户存档/Prism。


## 最新A验收修复与重启
用户已授权验收期间直接选择修复方法、验证并重启。MainTarget父类字段Mixin错误已修；build/tests/PASS，A单JAR更新，SHA256=3D19B2324F40E9758762519ED5FFDC3CE0F9399CFE4BE36DAD8F55A709E1C24D。第二次runManualBase已生成Minecraft NeoForge* 1.21.1窗口，PID49588，进程保持运行，未见同类FATAL；画面由用户确认。详Evidence/acceptance-a-current.json及acceptance-a-launch-02.log。B仍原版本，原最终冻结/哈希是修复前工程快照，不能覆盖此次源码。


## 当前阶段转入崩溃排查（用户新授权与边界）
新索引：D:\Workspaces\Artifacts\RadianceCrashInvestigation\20260915-A\PROGRESS.md。标准validation独立启动环境D-01已准备，尚未定位最终原因；本轮业务源码与正式产物未修改。不自行启动或操作交互游戏，用户执行单行PowerShell复现；不继承前一阶段自动重启边界。临时诊断/实验调整/正式修复分账，诊断辅助必须在最终正式回归前移至回收站清除。旧goal complete仅是前次工程交付快照，不等于此崩溃已修复。

