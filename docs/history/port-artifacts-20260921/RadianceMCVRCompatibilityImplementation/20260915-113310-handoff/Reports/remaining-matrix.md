# Radiance / MCVR 剩余工程矩阵（有界只读核查）

调查时点：2026-09-15。本文只核对以下状态文件、最新子报告、两仓当前源码和固定参考源；未编译、未运行测试、未启动客户端/游戏、未使用 UI 自动化，未修改源码、旧报告或 Git 状态。除本文件外没有写入文件。

输入：

- 工作包基线：D:\Workspaces\Artifacts\RadianceMCVRCompatibilityImplementation\20260915-082227-handoff\Reports\workpackage-status.json（18 个 WP、52 个 CG ID）。
- 最新接续：D:\Workspaces\Artifacts\RadianceMCVRCompatibilityImplementation\20260915-113310-handoff\Reports\public-integration.md、D:\Workspaces\Artifacts\RadianceMCVRCompatibilityImplementation\20260915-113310-handoff\PROGRESS.md。
- 主要历史子报告：flywheel-completion、flywheel-review、readback-progress、readback-review、public-render-progress、veil-fbo、veil-lifecycle-progress、veil-shader、sable-custom-vertex-array、levitite-tessellation-implementation、mc-04.2。
- 当前源码：D:\Workspaces\Repositories\GitHub\RecRivenVI\Radiance 与 D:\Workspaces\Repositories\GitHub\RecRivenVI\MCVR。
- 固定参考：D:\Workspaces\References\minecraft-references。

## 结论前置

1. 最高优先级是 Flywheel 后端选择和 Simulated 的 Veil shader fallback。当前 Radiance 后端把 PT/TLAS 当作 Flywheel 实例几何的承接层，不能因为没有原 Flywheel OpenGL 类就要求复制 GL 架构；但 Radiance 与原 Flywheel INDIRECT 都是 priority 1000，且 Radiance 在 FlwImpl.init 尾部注册，BackendManagerImpl 又按稳定的 priority 降序选择，默认实际后端必须在固定组合中核实。
2. D:\Workspaces\Repositories\GitHub\RecRivenVI\Radiance\src\main\java\com\radiance\mixins\compatibility\simulated\SimulatedVeilRenderSystemMixins.java:24-49 仍在 diagram scope 对大多数 Veil shader 请求调用 vanilla RenderSystem.setShader，然后 cir.setReturnValue(null)。这和真实 Veil shader bridge 同时存在，可能使 laser/staff/spring/rope 等消费者静默降级。需要按实际消费者收窄或证明 fallback 必要；不能把“已注册 Veil shader bridge”当成该 fallback 已闭合。
3. 最新接续已经有 Release core/fixture、7/7 定向 CTest、custom VertexArray GPU 和依赖范围/哈希检查；这些是 native/合同/fixture 或声明约束证据，不是完整 Java/JNI 场景或客户端像素验收。最新 PROGRESS 还记录 EndSea/WorldRaster/Veil AFTER_LEVEL 的后续 Java 证据，以及随后数组 uniform、section storage 变动仍待联合运行，故不能复用较早通过结论覆盖最新源码。
4. EndSea 的 renderLevel 接续、根负责的 Sable world/culling 路由、以及 Veil VanillaShaderCompiler reload 已由其他所有者处理。本矩阵只记录它们对固定消费者的边界和待证项，不重复实现或要求复制其代码。
5. 目标边界是当前固定版本和真实可达消费者。对未知 raw MeshData、原生 OpenGL-only backend、未被固定组合调用的高级 GL 特性，结论为“拒绝/明确 deferred”，不泛化成全 GL 兼容缺口。

状态含义：PASS = 报告已有对应范围的静态、合同或 fixture 证据；待运行 = 源路径存在但尚无生产 Java/JNI/客户端证据；真实缺口 = 当前调用链仍有未承接的固定消费者或选择风险；委托 = 根或指定 worker 正在负责。

## 18 个工作包总览

| ID | CG | 当前判定 | 下一动作 |
|---|---|---|---|
| WP-H01 | CG-L01/L02/L03/L05/L08/L09/V12 | Java/native/内置包合同已有证据；真实生命周期与场景仍待运行 | 固定 Radiance backend 后做 Flywheel/Create A/B |
| WP-H02 | CG-L06/L07 | 不复制原 GL；后端选择可能被原 INDIRECT 抢先，PT 透明语义未有生产证据 | 核 currentBackend/config，再跑透明/OIT 对照 |
| WP-C01 | CG-L04 | generation/cache 源路径有；原 FlwProgramsReloader 被取消，替代 reload 未有完整运行证据 | reload/noise/陈旧 generation 场景 |
| WP-C02 | CG-L11/L12 | WorldMeshSink、Create ValueBox 已接线；普通 stage/目标消费者未闭合 | Create/Ponder world capture A/B |
| WP-C03 | CG-L13/L14/L15/L23 | frame/stage 源路径与 EndSea 接续已有；最新 Java/目标顺序仍待统一验证 | 编译最新树并核 stage/output |
| WP-C04 | CG-L16/L17/V04/V05/V15/V21 | FBO/blit/diagram native 合同与 fixture 有；Veil Java 消费、readback/stencil/UI 未闭合 | 统一 core 后做 UI/post/diagram |
| WP-C05 | CG-L18/L19 | scope/token/异常回滚已写；嵌套和 fault/lifetime 尚无生产证据 | 异常、重入、readback 失败路径 |
| WP-C06 | CG-L20/L24 | 普通 hand 与四类 buffered particle 有静态路由；自定义 ParticleRenderType 语义可能折叠 | hand/particle A/B，核 blend/depth/light |
| WP-C07 | CG-L10 | glowing shader 名称和 PBR metadata 可达；无 Create 实景像素证据 | glowing block/contraption A/B |
| WP-C08 | CG-V01/V16 | Veil FBO/shader/lifecycle 源路径有；Simulated fallback 与动态 reload 仍有真实风险 | compiler worker 收束后做 diagram/reload |
| WP-C09 | CG-V08 | 委托根 Sable world/culling；本矩阵不重复实施 | 根验证 visibility queue/普通区块 PT |
| WP-V01 | CG-V09/V10/V11/V13 | custom array native/GPU/注册已有最新证据；Sable world、levitite/extra layer、reload 仍待 | 根的 Sable 场景与 compiler 后 A/B |
| WP-V02 | CG-R01/R02/R03/R04/V03 | ModelData/fluid/biome/fog/hand 的 mixin 路径有；icon 与实际消费者未运行核实 | 代表性模型/液体/雾/图标/手部 A/B |
| WP-V03 | CG-V18/V19/V20 | Diagram target/双坐标/液体实体源码路径有；像素和 nested UI 未证 | EndSea 接续后做 diagram A/B |
| WP-V04 | CG-V22/V23 | Flywheel crumbling 输入和 PT flag 有；未找到专用 Offroad bridge，生产调用/生命周期未证 | 固定 Offroad destruction 场景 |
| WP-R01 | CG-L21/L22/V06/V07/V14/V17 | 低层 contract 有；各自定义 layer/shader/target 的实际消费者仍需逐项闭合 | 仅对固定消费者做 A/B 清单 |
| WP-R02 | CG-V02 | Plunger focus redirect 源路径有；坐标作用域无生产视角证据 | first-person/detached A/B |
| WP-R03 | CG-V24 | 诊断和一次性 discard 机制有；无最终运行日志相关性/清理门禁证据 | 最终扫描并核预期 warning 集合 |

## 固定依赖与证据位置

- Radiance D:\Workspaces\Repositories\GitHub\RecRivenVI\Radiance\gradle.properties:5-18 固定 Minecraft 1.21.1、NeoForge 21.1.250、Create 6.0.10+mc1.21.1、Sable 2.0.5+mc1.21.1、Aeronautics 1.3.2+mc1.21.1、Veil 4.3.2。build.gradle:1-17 固定 ModDev 2.0.140 和 Java 21；build.gradle:104-131、114-125 显示 compileOnly/compatibility artifact/runtime/testRuntimeOnly 的实际依赖边界。
- D:\Workspaces\References\minecraft-references\REFERENCE_CONVENTION.md:1-11、25-39、41-77 规定身份、来源、不可变 source、hash 和 release revision unavailable 的记录方式。D:\Workspaces\References\minecraft-references\TARGETS-20260914.json 记录 Create 6.0.10、Sable 2.0.5、Aeronautics 1.3.2、Flywheel 1.0.6、Ponder 1.0.82、Veil 4.3.2 的固定 source/artifact；Flywheel revision 不可得，不能编造 commit。
- D:\Workspaces\Artifacts\RadianceMCVRCompatibilityImplementation\20260915-113310-handoff\Evidence\dependency-hash-recheck.json 证明三个外层 JAR 与上轮一致；dependency-ranges-result.log 与 dependency-ranges.tsv 证明 35 项安装的 CLIENT/BOTH 版本约束无 failure。它们证明声明约束和 artifact identity，不证明 ModLauncher 实际加载、Mixin 应用或场景像素。
- 同目录 builtin-source-recheck.json 的 Checked=163、Mismatches=[] 可沿用旧 237 变体 source seal；native-integration-tests.log 的 7/7 与 custom-array-gpu-run.log 是 native/fixture 范围；java-culling-build.log 是一次 Java compileJava/compileTestJava/generateJniHeaders 范围。最新源码继续变化时必须重新标记“待统一编译”。

## 逐项矩阵

### WP-H01 — Flywheel Engine/Backend、Create visual embedding、instance ABI（CG-L01/L02/L03/L05/L08/L09/V12）

- **当前源码/调用链**：D:\Workspaces\Repositories\GitHub\RecRivenVI\Radiance\src\main\java\com\radiance\compatibility\flywheel\RadianceFlywheelBackend.java:16-31 注册 native-supported backend；RadianceFlywheelEngine.java:131-152 在每帧上传 Minecraft 光照、模型、light sections 并调用 NativeInstancingProxy.render，:154-202 处理 crumbling/delete，:337-425 处理 instance/embedding/steal。MCVR src/core/render/instancing.cpp:221-324 校验/保存模型、材质、光照和 crumbling，:337-382 产出 preparedInstances；src/core/render/modules/world/ray_tracing/submodules/world_prepare.cpp:377-430 将 Flywheel model/BLAS 重用为 TLAS instance、hit-group、appearance/light range。该路径已经是固定 PT/TLAS 承接路径。
- **已有范围**：flywheel-completion.md:1-4、20-53、63-74、76-97 记录 Java/JNI/native/材质/光照/AO/透明/crumbling 合同、JDK21、368 ABI、237/237 builtin source seal；最新 Evidence/native-integration-tests.log 另有 7/7 定向 native 合同/fixture。它们没有生产 ClientLevel、GPU 资源释放、多个 VisualizationManager、reload/off、Create 像素证据。
- **真实缺口**：不是缺少原 Flywheel 类，而是固定组合中尚未证实完整生命周期和实际消费者：ClientLevel 光照变化、同一世界多个 manager/engine、资源 reload 后的 generation、frame fence/delete、BLAS/model reuse、motion history，以及 Create fluid/shadow/glint 和 PT lightmap 的实际颜色。后端是否真的选中 Radiance 见 WP-H02。
- **最小下一步**：先核定 currentBackend/config 选中 radiance:vulkan_instancing，再只跑一个 Flywheel 基础实例和一个 Create contraption 的 A/B；记录 instance/BLAS/TLAS、光照更新、reload/off、删除/重建和像素结果。若失败再针对调用链修复，不能由合同 PASS 推导场景 PASS。
- **历史验证范围**：flywheel-completion.md:85-97、Evidence/tessellation-integrated-build-final.log、Evidence/tessellation-and-readback-tests.log；范围是静态/编译/合同/内置 shader，不能代替客户端验收。

### WP-H02 — IndirectDraw / DepthPyramid / OitFramebuffer（CG-L06/L07）

- **当前源码/调用链**：固定参考 D:\Workspaces\References\minecraft-references\flywheel-1.0.6-neoforge\src\dev\engine_room\flywheel\backend\Backends.java:19-41 和 backend/engine/indirect/IndirectDrawManager.java:41-70、86-194 显示原 OpenGL INDIRECT 才直接拥有 DepthPyramid、compute culling 和 OIT framebuffer。Radiance 当前 RadianceFlywheelBackend.java:16-22 注册 priority 1000 的 Vulkan backend；FlywheelBackendRegistrationMixins.java:13-15 在 FlwImpl.init 尾部注册；其 Engine.render 进入 MCVR Instancing::render，preparedInstances 再进入 world_prepare.cpp:377-430。MCVR 当前没有直接调用原 IndirectDrawManager/DepthPyramid/OitFramebuffer 的消费者。
- **判定**：不因原 GL 类不在 MCVR 就判为“必须复制”的缺口。当前自有 backend 以 PT/TLAS、geometry hit group 和 material alpha mode 承接外部 instance 几何；Instancing::render 本身是上传/合同门，真正的场景承接在 world_prepare。可是存在一个实际可达的选择风险：原 INDIRECT 也是 1000，BackendManagerImpl.java:33-50 按 priority 降序排序，Java 稳定排序在同分时保留注册顺序；若没有显式配置，原 backend 可能先于 FlwImpl 尾部新增的 Radiance backend。即使 Radiance 被选中，translucent/order-independent 的 PT hit 语义也只有 shader/合同证据，没有固定场景证据。
- **最小下一步**：在固定组合实际打印/读取 BackendManagerImpl.currentBackend()/getBackendString 和 FlwConfig backend；确认 radiance:vulkan_instancing。再跑一个 translucent/order-independent Flywheel/Create 场景，核 alpha_mode.glsl、lightning/透明 hit-group、遮挡和 blend 结果。若配置落到 flywheel:indirect，明确该 OpenGL-only backend 为 unsupported/deferred 并修正选择边界；不要复制原 GL 管线。
- **历史验证范围**：flywheel-completion.md:45-53、89-97 只把第三方 indirect/SSBO、depth-only/custom shader/hit group 和 live visual 列为边界；当前没有证据证明原 GL backend 已被选中或其功能缺失于 PT 路径。

### WP-C01 — Flywheel shader/noise resource reload（CG-L04）

- **当前源码/调用链**：D:\Workspaces\Repositories\GitHub\RecRivenVI\Radiance\src\main\java\com\radiance\render\ResourceReloadCoordinator.java:10-83 管 generation、invalidate、RendererProxy begin/end 和 warmup；ReloadableResourceManagerImplMixins.java:20-51 在资源 reload 前后接入；ShaderRegistry.java:30-137、204-255 管缓存/外部 shader/generation。与此同时 FlywheelProgramsReloaderMixins.java:9-18 取消原 FlwProgramsReloader.onResourceManagerReload。
- **真实缺口**：替代 coordinator 的源结构存在，但取消原 Flywheel reloader 后，FlwPrograms、NoiseTextures 和异步 completion 是否都由当前替代链覆盖，尚无一次真实 reload/in-flight/stale generation 证据。不能用一次 Java compile 或 builtin source seal 代替动态资源交换。
- **最小下一步**：待当前数组/section 等并行变更收束，做一次最新树统一 Java/native 编译；随后在固定客户端 reload 含 Flywheel program/noise 的资源，观察 generation swap、失败回滚、旧 descriptor/engine 是否被拒绝，最后验证 renderer off/on。
- **历史验证范围**：flywheel-completion.md:45-53、76-97 证明静态 ABI/内置 shader；veil-lifecycle-progress.md:1-11 只把 renderer reload/default/custom target 和 dynamic buffers 留作 pending。

### WP-C02 — Create/Ponder 世界 MeshData → native world sink（CG-L11/L12）

- **当前源码/调用链**：D:\Workspaces\Repositories\GitHub\RecRivenVI\Radiance\src\main\java\com\radiance\render\WorldMeshSink.java:32-73、108-209 建立 stage/frame/generation；:217-343 接收 RenderType mesh 并 queueWorldMesh；:384-475 只接受 MAIN_TARGET、支持明确 shader key/emissive。RenderTypeWorldSinkMixins.java:15-38 在 world/dimension scope 拦截 RenderType.draw(MeshData)。CreateValueBoxWorldGeometry.java:9-93 和 CreateValueBoxMixins.java:15-53 把 ValueBox layer/outline buffer 提交到 sink。MCVR world_mesh_contract.hpp:9-66 负责 topology、generation、translation、alpha/emission。
- **已有范围**：已有真实 sink、Create ValueBox capture 和 native world contract。最新 public-integration.md:11-15 明确 AFTER_LEVEL/WorldRasterPass 已单独承接固定 raster，普通 WORLD_STAGE 仍需真实 material bridge；PROGRESS.md:34-38 又记录 SectionRasterStorage 新变更尚待联合运行。
- **真实缺口**：Create contraption、Ponder WorldRenderUtils 的实际 MeshData→PT 接收/关闭/像素尚无客户端证据；non-MAIN target、explicit framebuffer、SKYBOX callback 由当前 RenderTypeSnapshot/DimensionSpecialEffectsCompatibility 明确拒绝，不能把这些 rejection 写成“全世界几何已支持”。Sky/cloud/weather 也不是 sink 的替代品。
- **最小下一步**：在固定 A/B 中只选一个 Create ValueBox 和一个 Ponder world scene，核 capture scope、accepted/discarded 计数、frame/generation、native close 和像素；另跑一个非 MAIN/SKYBOX case 确认日志是预期边界。SectionRasterStorage 的联合测试由根完成。
- **历史验证范围**：public-render-progress.md:3-6、public-integration.md:11-15；范围是 frame/lifecycle 源修复和合同，未完成生产消费者。

### WP-C03 — NeoForge dimension/stage/OutputStateShard/版本契约（CG-L13/L14/L15/L23）

- **当前源码/调用链**：D:\Workspaces\Repositories\GitHub\RecRivenVI\Radiance\src\main\java\com\radiance\mixins\vulkan_render_integration\GameRendererMixins.java:150-229 在 Camera.setup 后建立 world frame，LevelRenderer 借用并在 AFTER_LEVEL/hand 后统一 commit/close；WorldRendererMixins.java:145-390 替代 renderLevel、执行 sky/chunk/entity/block entity/particle/debug/cloud/weather 阶段；RenderLevelStageCompatibilityMixins.java:13-49 包裹 NeoForge dispatch；DimensionSpecialEffectsCompatibility.java:9-45 将 SKYBOX 与 default world 分开并拒绝没有真实 MeshData→TLAS bridge 的 callback。
- **已有范围**：父代理已把 EndSea 接续排到 fuse 后，并以 WorldRasterPass 承接明确 raster effect；public-integration.md:11-15、PROGRESS.md:29-38 记录该路径和 java-world-effects-reload-tests.log 范围。但 PROGRESS.md:35-38 也说明后续数组 uniform/section storage 变更不在该证据内。
- **真实缺口**：最新源码的统一 Java 编译尚需在并行变更后重做；Mixin 实际应用、AFTER_SKY/AFTER_LEVEL/hand 的现场顺序、相机/fog/viewport 恢复和 outputState 对固定消费者仍无客户端证据。SKYBOX/自定义 framebuffer 输出没有 native sink 消费者，保持明确 deferred。EndSea 的主体/接续不在本矩阵重复调查。
- **最小下一步**：统一编译最新树后，固定组合运行一次带 sky/cloud/weather、chunk、entity、AFTER_LEVEL post 的场景，记录 stage token/output target 顺序；只对确有消费者的 non-MAIN target 添加后续工作。
- **历史验证范围**：public-render-progress.md:3-6、public-integration.md:11-15；是 source/lifecycle checkpoint，不是完整事件运行验收。

### WP-C04 — Catnip UI、Veil post、跨模组离屏目标（CG-L16/L17/V04/V05/V15/V21）

- **当前源码/调用链**：D:\Workspaces\Repositories\GitHub\RecRivenVI\Radiance\src\main\java\com\radiance\compatibility\veil\VeilFramebufferCompatibility.java、VeilAdvancedFboAccess.java 及 compatibility/veil 下 8 个 FBO mixin 管 Veil FBO；MCVR src/core/render/framebuffers.hpp:21-228、framebuffers.cpp:15-177 管 attachment/status/bind；src/core/render/ui_framebuffer_blit.cpp 管 color/depth rectangle、clip/scissor、self-image snapshot；src/core/render/ui_diagram_target.cpp:26-143 管 diagram begin/abort/post；MainTargetCompatibilityMixins.java:37-90 建立主色/深度 alias。PonderUIRenderHelperMixins.java 是 Catnip/UI 入口。
- **已有范围**：veil-fbo.md:1-65 记录真实 attachment、single-sample completeness、stack LIFO、diagram lifecycle、9 mixin registration 和 Java/selected tests；public-render-progress.md:8-18 记录 shader、D16→D32、裁剪/翻转/scissor 六个 GPU case、Release core checkpoint；最新 native-integration-tests.log 也含 framebuffer/readback GPU 合同。
- **真实缺口**：不同高度目标的最新 viewport/Y flip、readback-review const fix 后的统一 core 仍需确认；color pixel、same-image non-overlap、stencil exact clip、nested diagram、MainTarget/readback/Veil post consumer 尚无生产闭环。Veil renderer reload/default/custom targets、dynamic buffers/direct GL consumer 和 MSAA/MRT/array/cubemap 等未被固定组合证明，不能扩大支持范围。
- **最小下一步**：纳入所有当前 native/Java 变更重新做一次 Release core/headers；然后按固定消费者顺序跑 Catnip screen、Veil post、Simulated diagram，分别核 main color/depth、stencil、nested target、readback 和状态恢复。失败项按具体 target 收窄。
- **历史验证范围**：readback-review.md:3-11 明确 source review, not runtime GPU acceptance；veil-fbo.md:54-73、public-render-progress.md:11-18；这些报告的 PASS 不能覆盖客户端。

### WP-C05 — capture scope、ValueBox、Screen 异常清理（CG-L18/L19）

- **当前源码/调用链**：D:\Workspaces\Repositories\GitHub\RecRivenVI\Radiance\src\main\java\com\radiance\render\RenderCaptureContract.java:9-16、65-125 以 token/scope 区分 GUI、camera/world、target 和一次性 discard；CreateValueBoxWorldGeometry.java:61-93 对 capture/commit/close 做异常恢复；GameRendererMixins.java:186-211、244-293 管 world/GUI close；SimulatedDiagramCompatibility.java:117-145 和 Veil FramebufferStack mixin 管 diagram target/abort/LIFO。
- **已有范围**：readback-review.md:3-7 记录 sampled image layout、dirty descriptor、retired UBO、main alias、one-shot command pool、nested abort 六项 source review 修复；第 9-11 行说明最新改动必须再进统一 core，fault injection 仍未测试。
- **真实缺口**：嵌套 Screen/ValueBox/diagram 重入、render-thread exception、readback fence 不确定、frameSubmitted 后追加 uniform、资源 generation 交错的生产行为尚无运行证据。源级 finally/abort 存在不等于异常后 native scope/alias/descriptor 一定恢复。
- **最小下一步**：在最新统一编译后，对固定 GUI/diagram 做一次正常、嵌套、抛异常、readback fence failure 组合，确认 scope depth、target stack、FBO/viewport 和 native close；只报告一次性预期 discard。
- **历史验证范围**：readback-review.md:5-11、veil-fbo.md:29-52、public-render-progress.md:12-18；主要是 source review/contract/fixture。

### WP-C06 — NeoForge 手部姿态与 ParticleRenderType consumer 契约（CG-L20/L24）

- **当前源码/调用链**：D:\Workspaces\Repositories\GitHub\RecRivenVI\Radiance\src\main\java\com\radiance\mixins\vulkan_render_integration\HeldItemRendererMixins.java:51-88 通过 camera/FOV/partial tick 路由主副手；EntityProxy.java:955-999 以 HAND/CAMERA 坐标提交；:1001-1170 处理粒子 capture，:1076-1081 只把四类 vanilla buffered ParticleRenderType 认作标准路由，其余走 custom direct particle capture；ParticleMixins.java、ParticleManagerMixins.java、CustomParticleBufferSourceMixins.java 负责 content/light/custom buffer。
- **已有范围**：普通 hand、四类 buffered particle、ItemPickup/MobAppearance custom buffer 有静态调用链；Flywheel-completion.md 的 light/material 合同可作为底层输入证据。
- **真实缺口**：没有 fixed client/runtime/pixel 证据；custom/第三方 ParticleRenderType 会落到 cutout/translucent 或直接 capture，原 begin/end 的 blend、depth、sort、light 语义可能折叠。睡眠、隐藏 GUI、spectator、左右手和粒子清理/重入也未现场核实。
- **最小下一步**：跑普通/副手、sleep/spectator、ItemPickup/MobAppearance、一个自定义 ParticleRenderType，逐项比较 alpha/depth/light/排序和 scope close；只有实际固定消费者失败才扩展映射。
- **历史验证范围**：workpackage-status.json 的旧 CG-L20/L24 字段仅是历史检查点；当前源审计没有把粒子消费者标成 runtime PASS。

### WP-C07 — Create GLOWING_SHADER material identity（CG-L10）

- **当前源码/调用链**：D:\Workspaces\Repositories\GitHub\RecRivenVI\Radiance\src\main\java\com\radiance\render\WorldMeshSink.java:384-475 的 RenderTypeSnapshot 只接受 MAIN_TARGET，并在 :466-468 识别支持的 shader key（含 create:glowing_shader）；PBRVertexConsumer.java:111-119 保存 128-byte PBR stride/alpha；FlywheelMaterialData.java:25-76 和 MCVR shader util/alpha_mode.glsl 定义透明、发光/光照 flags。
- **已有范围**：名称、RenderType 入口、PBR material metadata 和 builtin shader source seal 已有静态/合同证据。
- **真实缺口**：尚未证明 Create RegisterShadersEvent/PartialItemModelRenderer/实际 GLOWING_SHADER 的 shader registry、blend/emission、纹理和 contraption world consumer 能走到 PT 并产生正确像素。不能由字符串识别宣称 Create glowing 完成。
- **最小下一步**：固定 Create 6.0.10 中一个 glowing block/contraption，记录 shader key、material flags、texture/light 输入和 emission 像素；若该消费者不进入 WorldMeshSink，按实际入口补 bridge。
- **历史验证范围**：flywheel-completion.md:20-53、87-97 及 builtin-source-recheck.json 只覆盖静态 shader/材质合同，不含 Create 场景。

### WP-C08 — Veil 初始化与 Simulated stage/fixed-buffer 入口（CG-V01/V16）

- **当前源码/调用链**：D:\Workspaces\Repositories\GitHub\RecRivenVI\Radiance\src\main\java\com\radiance\compatibility\veil 下 VeilShaderBridge、VeilRenderStateCompatibility、VeilFramebufferCompatibility、VeilDynamicBuffer* 与对应 mixin 负责初始化/target/shader；SimulatedDiagramCompatibility.java:64-70、117-145 负责 diagram target/scene/post。SimulatedVeilRenderSystemMixins.java:24-49 在 diagram scope 拦截 VeilRenderSystem.setShader：只放过 veil:core/blit_screen_effect，其余 laser/staff/spring/default 分支调用 vanilla RenderSystem.setShader，最后统一 cir.setReturnValue(null)。
- **真实缺口**：Veil renderer/default/custom target、dynamic buffer、reload 尚无完整生产证据；更具体的风险是上述 fallback 与真实 VeilShaderBridge 并存，可能绕过外部 shader program，并对未知请求静默返回 null。需要查固定 Simulated 2.0.5 实际 shader consumer（包括 rope/laser lens/accumulator diode 等）是否确实要求 vanilla fallback。EndSea 主体/接续由根代理处理，本项不重复调查。
- **最小下一步**：等待 /root/veil_reload 完成 VanillaShaderCompiler reload bridge/mixin 后，在最新树统一编译；静态列出 Simulated diagram 实际 setShader 资源，再运行 laser/staff/spring/rope 等最小 diagram。只保留已证明兼容的 fallback；未知/必需 Veil program 不得静默 null。随后做一次 renderer reload + diagram post A/B。
- **历史验证范围**：veil-lifecycle-progress.md:1-11、veil-shader.md:41-53、92-104 记录 shader/FBO bridge 和 pending consumer；public-integration.md:17-18 明确 Veil dynamic shader reload 与 18 WP 仍未完成。VanillaShaderCompiler 本身不在本代理实施范围。

### WP-C09 — Sable culling dispatch → native visibility queue（CG-V08）

- **当前源码/调用链**：D:\Workspaces\Repositories\GitHub\RecRivenVI\Radiance\src\main\java\com\radiance\compatibility\sable\SableSubLevelBridge.java、SableRenderSectionCompatibility.java、SableBufferBridge.java 与 compatibility/sable 下五个 mixin 连接 Sable sub-level/section；WorldRendererMixins.java:173-186、377-390 调用 setupRender、SableSubLevelBridge.update/prepareRenderer；MCVR world/chunks queue 是下游。
- **判定**：这是根代理明确负责的 Sable world/culling 路由，本矩阵不重复实现。最新 public-integration.md:3-9、PROGRESS.md:22-31 只证明 mixin 注册、preRenderChunks/updateCulling 源修复、Java compile 和 custom fixture；不证明客户端 visibility queue、普通区块 PT 或 levitite/额外 layer 输出。
- **最小下一步**：根在固定 Sable 2.0.5 场景核一次 visibility dispatch→queue→PT 实例/section storage→像素，并记录错误/空队列；普通区块走 PT，levitite 与额外 layer 按实际 shader consumer 单独承接。本文不修改 Sable 路由。
- **历史验证范围**：sable-custom-vertex-array.md:11-82 的 layout/source；public-integration.md:3-9、17-18 的最新 compile/fixture/未完成边界。

### WP-V01 — Sable chunk/mesh/entity/block entity/outline E2E（CG-V09/V10/V11/V13）

- **当前源码/调用链**：D:\Workspaces\Repositories\GitHub\RecRivenVI\Radiance\src\main\java\com\radiance\compatibility\sable\ 下 RadianceSableStagingBuffer、SableBufferBridge、SableRenderSectionCompatibility、SableSubLevelBridge；compatibility/sable/Sable* mixins 和 compatibility/veil/VeilVertexArray* mixin；MCVR Vulkan VertexArray JNI/native custom array。父代理最新接入并验证 five mixin registration/custom GPU fixture。
- **已有范围**：public-integration.md:3-9 与 Evidence/native-custom-array-build.log、custom-array-gpu-run.log、native-integration-tests.log 证明 Java registration、Release core/fixture、7/7 native CTest（含双 binding、UINT16、indirect offset GPU）；sable-custom-vertex-array.md:11-82 记录 binding0 BYTE stride8、binding1 uvec2 stride8 和 caller/layout。
- **真实缺口**：custom array 的 native/GPU fixture 已可标 PASS；生产 Sable section/entity/block entity/outline 的 Mixin 运行、普通区块 PT、levitite/extra layer、dynamic shader reload 和 client pixels 尚无证据。sable-custom-vertex-array.md:117 的 VanillaShaderCompiler boundary 仍属指定 worker。
- **最小下一步**：待根 Sable world/culling 与 /root/veil_reload 收束后跑一份 packaged Sable 2.0.5 A/B，覆盖普通 chunk、custom array section、entity/BE、outline、levitite/extra layer；以现场日志/像素决定是否需要工程变更。
- **历史验证范围**：最新 public-integration.md:3-9 优先于旧报告的“native/GPU pending”字段；旧报告 source/layout 范围不能扩写为生产 E2E。

### WP-V02 — NeoForge ModelData/Fluid/Biome/Fog、模组列表图标、普通手（CG-R01/R02/R03/R04/V03）

- **当前源码/调用链**：D:\Workspaces\Repositories\GitHub\RecRivenVI\Radiance\src\main\java\com\radiance\mixins\vulkan_render_integration\SectionBuilderMixins.java:39-154 处理 ModelData、block entity、fluid、ClientHooks.addAdditionalGeometry；BlockModelRendererMixins.java:23-100 处理 tint/emission/PBR；FluidRendererMixins.java:28-660 处理 FluidSpriteCache、IClientFluidTypeExtensions、邻接/height/flow/UV/normal；WorldRendererMixins.java:188-196 负责 fog setup，EntityProxy weather/biome 路径提供 precipitation；LightmapTextureManagerMixins.java、NativeImageMixins.java:48-90 管 lightmap/texture upload，HeldItemRendererMixins.java:51-88 是普通手。当前 radiance.mixins.json 已登记这些 integration mixin；未见专门的 mod-list icon mixin，NativeImage 通用 upload fallback 可能覆盖其 texture subclass。
- **真实缺口**：上述调用链没有固定客户端/像素证据；ModelData variant、additional geometry、fluid tint/sprite/flow、biome shade/fog/weather、lightmap 和普通 hand 仍需实际消费者验证。模组列表图标是否确实走 NativeImage fallback、是否被 NeoForge 特殊 texture subclass 绕过（NativeImageMixins 注释已提示该风险）也未核实。不能把“没有专门 icon mixin”直接判成缺失，先核真实调用。
- **最小下一步**：在固定包做一个 ModelData block/BE、流体相邻区、昼夜/生物群系雾天气、mod list icon 和普通/副手场景，核 layer/texture/light/fog/hand 像素与资源关闭；只对固定 consumer 失败项补路径。
- **历史验证范围**：当前源路径为静态审计；workpackage-status.json 的旧 CG-R/V 字段没有当前实现证据，需以后续 A/B 结果覆盖。

### WP-V03 — Simulated diagram 世界几何、双坐标/液体实体/UI 生命周期（CG-V18/V19/V20）

- **当前源码/调用链**：D:\Workspaces\Repositories\GitHub\RecRivenVI\Radiance\src\main\java\com\radiance\compatibility\simulated\SimulatedDiagramCompatibility.java:64-70、117-145 检查 real diagram FBO、clear、scene、post/abort；:147 起用 MultiBufferSource 渲染 blocks/liquids/block entities/Flywheel/entities 并恢复矩阵/light state；SimulatedDiagramScreenMixins.java、SimulatedDiagramStickyNoteMixins.java 管 Screen/StickyNote 生命周期；MCVR src/core/render/ui_diagram_target.cpp:26-143 负责 target/post，FramebufferProxy readback 负责结果读取。HAND/CAMERA/世界坐标在 EntityProxy/world_prepare 中有区分。
- **真实缺口**：无客户端 diagram 像素、液体/实体/双坐标、nested Screen/StickyNote 和 abort-after-readback 证据；veil-fbo.md:52 的 greeble alpha 仍是 projection check，不是实际 readback。EndSea 固定消费者接续由根代理处理，不在此重复。
- **最小下一步**：根完成 EndSea/最新 Java 编译后，跑一个含 block/fluid/entity/Flywheel、StickyNote 和 nested diagram 的固定 diagram；核 color/depth/alpha readback、target stack、viewport/matrix/fog/close。
- **历史验证范围**：veil-fbo.md:29-52、54-73；veil-shader.md:41-53；均为源/合同/fixture或待运行 consumer matrix。

### WP-V04 — Offroad crumbling 共享进度与 MCVR VkResult/生命周期（CG-V22/V23）

- **当前源码/调用链**：D:\Workspaces\Repositories\GitHub\RecRivenVI\Radiance\src\main\java\com\radiance\compatibility\flywheel\RadianceFlywheelEngine.java:154-189 将 Flywheel crumbling block 的 instance/position/progress/texture stage 传到 NativeInstancingProxy.renderCrumbling；MCVR src/core/render/instancing.cpp:325-328 保存 crumbling，:337-356 在 preparedInstances 中设置 CRUMBLING/textureOverride；flywheel material alpha 22 和 shader util/alpha_mode.glsl 有静态定义。当前 Radiance 源中未找到专用 offroad bridge/caller。
- **真实缺口**：不能据“有 Flywheel crumbling API”证明 Offroad 2.0.5 的 destructionProgress 实际进入该路径；也没有固定 Offroad 场景证明 stage texture/alpha、queue/VkResult、generation/fence、close/unknown completion 的行为。readback-review.md:7 的 queue-idle/保留资源策略是源级 pending concern，不是 Offroad 验收。
- **最小下一步**：在固定组合触发一个 Offroad destruction consumer，核其调用链是否使用 Flywheel crumbling；记录 progress/stage、透明像素、VkResult 和 frame resource lifetime。若 Offroad 不走该 API，保持该 CG 明确 deferred，不扩建未达消费者的通用 bridge。
- **历史验证范围**：flywheel-completion.md:20-53、63-74 证明 crumbling/material 合同；最新 native-integration-tests.log 没有 Offroad 生产场景。

### WP-R01 — 自定义 renderer/capability/shader/init/custom layer 风险（CG-L21/L22/V06/V07/V14/V17）

- **当前源码/调用链**：低层入口集中在 D:\Workspaces\Repositories\GitHub\RecRivenVI\Radiance\src\main\java\com\radiance\render\RenderCaptureContract.java、RendererProxy.java、ShaderRegistry.java、compatibility/veil/VeilShaderBridge.java、compatibility/sable/SableShaderBridge.java、compatibility/simulated/SimulatedDiagramCompatibility.java，以及 mixins/compatibility/{create,flywheel,ponder,sable,simulated,veil,aeronautics}。RenderCaptureContract 对没有世界 metadata/TLAS 的 raw MeshData、未知 target 和 outside scope 会 reject/discard。
- **已有范围**：Veil shader 报告的 consumer matrix（veil-shader.md:41-53）至少列出 Sable water/custom array、Simulated EndSea/fullscreen、Aeronautics burner/balloon/levitite、Flywheel indirect/SSBO 和 Ponder/Catnip；其中不少明确未完成或只源 wiring。固定 builtin/ABI/Framebuffer fixture 不能代替这些外部 renderer。
- **真实缺口**：每个实际固定 consumer 的 shader key、vertex layout、target/attachment、blend/depth、resource reload、异常 close 和 PT/raster 归属没有逐一闭合。尤其 Aeronautics burner/balloon/levitite、Sable water/extra layer、Simulated fallback、Ponder Catnip 不能用一条“全 GL 兼容”结论覆盖。未被固定组合实际调用的 direct GL API 保持 unsupported/deferred。
- **最小下一步**：按已安装固定包建立最小 consumer 清单，每项只跑一个入口并记录 accepted/discarded、material/target、shader/reload、像素；先修真实调用链，再处理下一项。未知 raw MeshData 继续由 contract 拒绝。
- **历史验证范围**：veil-shader.md:1-9、41-53、76-104；flywheel-completion.md:45-53、89-97；public-integration.md:17-18。范围明确包含大量未运行项。

### WP-R02 — Plunger 第一人称 focus 坐标作用域隔离（CG-V02）

- **当前源码/调用链**：D:\Workspaces\Repositories\GitHub\RecRivenVI\Radiance\src\main\java\com\radiance\mixins\compatibility\simulated\SimulatedLaunchedPlungerEntityRendererMixins.java:10-24 只 redirect getFirstPersonFocusPos 的 Quaternionf.transformInverse，使已经是 camera-local 的 hand geometry 不再重复 inverse；普通 hand 下游为 HeldItemRendererMixins.java/EntityProxy.java:955-999。
- **真实缺口**：源级 redirect 不能证明第一人称 Plunger、detached camera、sleep/spectator、不同 camera orientation 下坐标仍不串入世界或 Sable pose conversion。没有客户端视角/像素证据。
- **最小下一步**：跑 Plunger first-person、第三人称/脱离焦点、camera yaw/pitch 和 sleep/spectator 对照，确认 focus/hand 仅在指定 scope 生效；失败才调整 mixin。
- **历史验证范围**：workpackage-status.json 的 CG-V02 仍是旧“未开始”快照；当前仅有源路径，不能标 PASS。

### WP-R03 — 临时日志/计数/探针证据闭环与清理门禁（CG-V24）

- **当前源码/调用链**：D:\Workspaces\Repositories\GitHub\RecRivenVI\Radiance\src\main\java\com\radiance\render\RendererDiagnostics.java:3-37 提供 GL diagnostic/fallback 记录；RenderCaptureContract.java:50、113-130 以 REPORTED_DISCARDS 做一次性 unsupported report；RendererProxy.java:119-122 记录 native close error。源中未发现可把任意路径标成已完成的永久临时计数器；旧注释/历史 System.out 不等于当前运行证据。
- **真实缺口**：没有最终固定场景日志证明 warning/discard 只包含预期 key、Sable bridge/Offroad lifetime 与 VkResult 可关联，也没有最新源码/证据 manifest 的最终清理扫描。报告不能把“没有看到临时 probe”当成运行闭环。
- **最小下一步**：所有根/worker 变更收束并完成固定 A/B 后，做一次只读源码扫描和 evidence manifest 对照；列出预期 discard/warning 集合、未解释项和需要清理的任务临时日志。不要删除用户文件或旧报告；没有授权时只记录清理门禁状态。
- **历史验证范围**：public-integration.md:8-9 的 source seal/dependency evidence、flywheel-completion.md:85-97 的 builtin/native evidence；均不包含客户端 warning correlation。

## 最小优先级队列

1. **P0：WP-H02** — 先确定 radiance:vulkan_instancing 是否真正被选中；同 priority=1000 的原 flywheel:indirect 是当前最具体的可达风险。选中 Radiance 后只验证一个透明/order-independent 场景，不复制 GL。
2. **P0：WP-C08/WP-C01** — 收束 Veil VanillaShaderCompiler worker 后，证明 Simulated fallback 不会绕过真实 Veil bridge，并验证 FlwPrograms/NoiseTextures 的 reload/generation。
3. **P1：WP-C04/WP-C05/WP-C03** — 把最新 readback/FBO/WorldRaster/数组/section 变更放入一次统一构建，再做 target、stage、nested/exception、readback 的小范围 A/B。
4. **P1：WP-C02/WP-C06/WP-C07/WP-V02/WP-V03/WP-V04/WP-R01/WP-R02** — 按真实固定 consumer 各取一个场景；只修现场证明的材质、坐标、生命周期或资源缺口。
5. **委托：WP-C09/WP-V01** — 根负责 Sable world/culling/ordinary PT、levitite/extra layer 和最终 Sable 场景；本文已记录 custom array native/GPU 证据，不重复实施。
6. **P2：WP-R03** — 最终 A/B 后做 warning/discard 与 evidence 清理门禁，避免把旧报告或 primitive fixture 误当交付证据。

## 证据边界

当前能安全标为 PASS 的只是各报告明确覆盖的 source/ABI/contract/native fixture/GPU fixture/source seal/dependency hash-range 范围。尚未运行生产 Java/JNI、Mixin application、固定完整 modpack、真实 ClientLevel、动态 reload、GPU 资源 release、A/B 像素的项目均保持“待运行”。这份矩阵不宣称全 GL 兼容，也不把 OpenGL-only 原 backend、无直接消费者的类或超出固定版本的 API 变成工程缺口。
