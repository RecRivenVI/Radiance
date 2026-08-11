# Radiance / MCVR 上游能力与来源账本

> 当前项目名称统一为 Radiance。本文保留 2026-09-10 审计快照、结论和历史引用；其中旧称谓仅说明当时语境，不定义当前身份。后续工程与历史整合见 [当前工程记录](migration-template-configuration.md)。

本文件是范围 A 的只读源码审计记录，先落盘首批能力单元，再增量补充。它不代表构建、启动或视觉验收。

## 基线与证据边界

- Radiance 上游候选：`R-U = 414d8e330a2fc6cb1e8630cc95f2302b2b97a0e8`；唯一 Initial port：`R-P = cd67362ebb5d9f49c7e23e53f51d35d25409e6a2`，其唯一父为 `R-U`。
- MCVR 上游候选：`N-U = 9905c81b1999f5845bf66d13501d371c16adf561`；唯一 Initial port：`N-P = b611d24a044384290beaf93131ad51bf14e3d107`，其唯一父为 `N-U`。
- 当前 Radiance `stonecutter` HEAD 为 `9fe7811cf8b27f97b8b77307e982e3cc3942aee5`，源码大量 dirty；以下“当前对应”只描述工作树中静态发现，绝不归因于 HEAD。
- 起始封存 `D:\Workspaces\Artifacts\OldPortAudit\20260910\Radiance-start.json` 的 SHA-256 为 `713715EB775C3A803A30CF2147B81D46CF01060D935DA54CB76EF07991D304C5`。双上游独立 archive（父提交）由主代理另存：Radiance tar `0AE2CABBEBBCB945427E88BC1AAE0775F30297E3F7608A989FECB821B9C2D34D`，MCVR tar `137EFE9AA2ADE643C05ED67038CBBBE3E90115BF753067117738D91B383051F0`。
- `R-U/R-P` 和 `N-U/N-P` 都以精确提交读取；没有用 latest 分支推断遗漏。双上游已存在精确只读 extract：`D:\Workspaces\Artifacts\RadiancePortAudit\20260910-152809\reference-extracts\Radiance-414d8e3`（tar SHA-256 `0AE2CABBEBBCB945427E88BC1AAE0775F30297E3F7608A989FECB821B9C2D34D`）与 `...\MCVR-9905c81`（tar SHA-256 `137EFE9AA2ADE643C05ED67038CBBBE3E90115BF753067117738D91B383051F0`）；无需再次提取，恢复点以主状态文档为准。

字段约定：`入口` 是能触达该单元的 Java/Gradle/Mixin/native 入口；`输入/输出/状态/依赖` 是静态契约；`适配` 采用“保留/重命名/Loader 适配/跨 ABI 联动/删除候选/未知”。

## 首批 Java 能力单元

### U-J01 Java 启动、资源 staging 与 native 装载（配对 U-N01/U-N02）

- 上游符号/路径：`R-U:src/main/java/com/radiance/client/RadianceClient.java` 的 `onInitializeClient`, `copyFileFromResource`, `copyFolderFromResource`；Fabric 入口在 `R-U:src/main/resources/fabric.mod.json`。
- 输入/输出/状态/依赖：输入为 Minecraft run directory、OS 名称和 JAR 内 `core.dll|core.lib|libcore.so|shaders/|modules/`；输出为 `<run>/radiance` 下库、shader、模块，随后调用 `RendererProxy.initFolderPath`, `Pipeline.initFolderPath`, `Options.readOptions`, `Pipeline.reloadAllModuleEntries`；依赖 Fabric client entrypoint、JNI native 库和资源布局。
- 当前对应：`versions/1.21.1-neoforge/src/main/java/io/github/recrivenvi/radiance/client/RadianceClient.java`；`platform/RadiancePlatform.java`、`platform/neoforge/RadianceNeoForge.java` 与 `mixins/vulkan_render_integration/MinecraftClientMixins.java` 触发一次性 `initialize()`，库名改为 `radiance_native.dll|radiance_native.lib|libradiance_native.so`。
- 适配/证据/未知：`Loader 适配 + staging 联动`；R-P 仍沿用 upstream `core.dll|core.lib|libcore.so` names，R-P 的 NeoForge/Gradle 只改变产物接入方式；当前 dirty target 后续才改为 `radiance_native.*`。是否在最终 JAR、最终 target 任务和真实客户端中可装载未验证；current dirty 不等于完成。

### U-J02 选项状态、持久化与视频设置入口（配对 U-N02/U-N05）

- 上游符号/路径：`R-U:src/main/java/com/radiance/client/option/Options.java`；`client/option/{DLSSMode,DenoiserMode,UpscalerQuality,UpscalerType}.java`；`mixins/vulkan_options/{GameOptionsScreenMixins,VideoOptionsScreenMixins}.java`；`client/util/CategoryVideoOptionEntry.java`。
- 输入/输出/状态/依赖：静态字段保存 FPS/vsync、DLSS、upscaler、denoiser、ray bounces、chunk rebuild batch/thread、`collectChunkEmission`；从 `<radiance>/options.properties` 读取并回写；设置器同时调用 native setters，emission 开关触发 `TextureProxy.flushEmissionTiles`、`ChunkProxy.rebuildAll` 或 shader-pack fallback。入口是 Minecraft Video Options mixin。
- 当前对应：同名类已迁到 `versions/1.21.1-neoforge/src/main/java/io/github/recrivenvi/radiance/client/option/`，视频 mixin 仍在目标目录；`Options` 仍保留上述键和 native setter。
- 适配/证据/未知：`重命名 + Loader API 适配`；R-U/R-P `Options.java` 初始 port 未被删除，包名和 Minecraft API 变化是主要改动。未做运行时选项切换、跨帧状态或 native 实际接收验证。

### U-J03 Pipeline 图、module 发现、构建与持久化（配对 U-N05）

- 上游符号/路径：`R-U:src/main/java/com/radiance/client/pipeline/{Pipeline,ModuleEntry,Module,Presets}.java`；`pipeline/config/{AttributeConfig,ImageConfig}.java`；GUI `client/gui/RenderPipelineScreen.java`。
- 输入/输出/状态/依赖：扫描 `<radiance>/modules/*.yaml`，按 `name` 建 module entries；module image config 以格式匹配连接，输出要求 `R8G8B8A8_UNORM`；维护 module list、image connection、`PRESET|PIPELINE` mode、active preset；把 module/attribute/位置/selected shader pack 写入 `<radiance>/pipeline.yaml` 并能回读，在 native `buildNative` 前构造 direct buffers。
- 当前对应：`versions/1.21.1-neoforge/src/main/java/io/github/recrivenvi/radiance/client/pipeline/` 下同名 7 类；`resources/modules/{dlss,fsr_upscaler,nrd,post_render,ray_tracing,temporal_accumulation,tone_mapping,xess_sr}.yaml` 仍存在。
- 适配/证据/未知：`重命名 + Loader API 适配 + ABI 联动`；R-U `Pipeline` 方法集合含 `build`, `assemble*`, `savePipeline`, `loadPipeline`, `getAttributes`, `buildNative`，R-P 仅作 API/映射调整。GUI 是否能实际编辑、保存后重建和回退未验证。

### U-J04 Shader pack 发现、manifest、动态属性与选择 GUI（配对 U-N07/U-N08）

- 上游符号/路径：`R-U:src/main/java/com/radiance/client/pipeline/Pipeline.java` 的 `getAvailableShaderPacks`, `readShaderPackChoice`, `isRadianceShaderPackManifest`, `shaderPackRequiresEmission`, `setShaderPack`；`client/gui/{ShaderPackScreen,ShaderPackSettingsScreen}.java`；`client/shader/{ShaderDefinition,ShaderField,ShaderRegistry,ShaderTranslator}.java`。
- 输入/输出/状态/依赖：输入为 `<radiance>/shaders/world/ray_tracing/vanilla-pt.zip|advanced.zip`、Java 中保留的 `restir-di.zip` 历史别名、`<game>/shaderpacks` 和 `configs.json`/YAML manifest；输出为排序后的 selectable choices、dynamic attributes 和临时 translated `.vert/.frag`；`collectChunkEmission=false` 时拒绝 requires-emission pack；ShaderRegistry 由 vanilla `ShaderProgram` uniform/sampler 元数据构造 std140 fields，SHA-1 key 生成临时 shader，再调用 `ShaderProxy.registerShader`。
- 当前对应：目标目录存在同名 pipeline GUI/shader 类；当前 component 的 built-in packs 位于 `components/vulkan-renderer/src/shader/world/ray_tracing/internal/{vanilla-pt,advanced}/configs.json`，Gradle/资源 staging 负责复制为运行时 shader pack。
- 适配/证据/未知：`重命名 + Loader/Minecraft API 适配 + native ABI 联动`；R-U/R-P 保留该链路。N-U/N-P 的 CMake 明确只将 `vanilla-pt`、`advanced` 列为 built-in，`restir-di.zip` 同时只是 legacy ZIP 清理/兼容名，没有同名 internal source/config 证据；不要将它写成真实发行 pack。当前 shader pack ZIP 生成、动态 uniform 对应和 third-party pack 兼容性仅静态可达，未启动验证。

### U-J05 Vulkan JNI proxy API（配对 U-N02）

- 上游符号/路径：`R-U:src/main/java/com/radiance/client/proxy/vulkan/{RendererProxy,BufferProxy,DrawCommandProxy,PipelineStateProxy,ShaderProxy,TextureProxy,WindowProxy}.java`。
- 输入/输出/状态/依赖：`RendererProxy` 管理 `initRenderer/acquireContext/submitCommand/present/fuseWorld/postBlur/close/takeScreenshot`；`BufferProxy` 传 geometry/vertex/index、world/sky/mapping/emission uniform；`TextureProxy` 管理 texture id、mip/format/filter/clamp/upload/emission tiles；`PipelineStateProxy` 转换 scissor/blend/depth/stencil/rasterization；全部 native 方法要求对应 JNI symbols 和 generated headers。
- 当前对应：`versions/1.21.1-neoforge/src/main/java/io/github/recrivenvi/radiance/client/proxy/vulkan/` 下同名类；当前 dirty component middleware 路径为 `components/vulkan-renderer/src/core/middleware/io_github_recrivenvi_radiance_client_proxy_vulkan_*.cpp`，库/manifest 契约改为 `radiance_native`。
- 适配/证据/未知：`包名重命名 + ABI 联动`；R-U 的 proxy 声明和 N-U/N-P 的 `com_radiance_client_proxy_vulkan_*.cpp` 是 Initial-port 时代候选，`io_github...` middleware rename 属于后续 Radiance 集成/current dirty，不应归因于 N-P。尚未生成/核对最终 JNI 或符号表。

### U-J06 世界 chunk capture、异步 rebuild 与几何传递（配对 U-N03）

- 上游符号/路径：`R-U:src/main/java/com/radiance/client/proxy/world/ChunkProxy.java` 的 `init`, `setStorage`, `enqueueRebuild`, `rebuildAll`, `rebuild(Camera)`, `rebuildSingle`, `isChunkReady`, `relocateSingle`, `invalidateSingle`；`mixins/vulkan_render_integration/{BuiltChunkStorageMixins,ChunkBuilderBuiltChunkMixins,ChunkBuilderMixins,SectionBuilderMixins,WorldRendererMixins}.java`。
- 输入/输出/状态/依赖：输入 BuiltChunkStorage、camera、ChunkRendererRegion/SectionBuilder 和 PBR `BuiltBuffer`；状态包括 rebuild queue、important/normal executors、pending full rebuild、native chunk slot；输出以 direct buffers 传 geometry type/group/texture/format/count/vertex address 和 `important` 标志到 native，关闭 vanilla terrain propagation/culling。
- 当前对应：`versions/.../client/proxy/world/ChunkProxy.java` 和目标目录上述 mixin 均存在；current target 还扩展了 `ResourceReloadCoordinator`、日志和 world-capture contract。native 对应为 component `core/render/chunks.{hpp,cpp}` 与 middleware `...proxy_world_ChunkProxy.cpp`。
- 适配/证据/未知：`Loader API 适配 + ABI 联动`；R-U/R-P diff 显示旧根 `ChunkProxy` 被移到 version target，N-U/N-P 保留并改 JNI/target。是否在 dirty current 中真正可见、ready 计数与异步生命周期是否正确，需主代理后续运行验证。

### U-J07 Entity、block entity、particle、weather、outline 与 hand capture（配对 U-N03/U-N08）

- 上游符号/路径：`R-U:src/main/java/com/radiance/client/proxy/world/EntityProxy.java` 的 `queueEntitiesBuild`, `queueBlockEntitiesRebuild`, `queueCrumblingRebuild`, `queueParticleRebuild`, `queueTargetBlockOutlineRebuild`, `queueWeatherBuild`, `queueHandRebuild`, `queueBuildWithoutClose`, `build`；`mixins/vulkan_render_integration/{EntityRendererMixins,EntityRenderDispatcherMixins,ParticleManagerMixins,ParticleMixins,GameRendererMixins,WorldRendererMixins}.java`。
- 输入/输出/状态/依赖：输入实体列表、render dispatcher、block breaking、particle queues、weather/outline/hand buffers；状态保存 render layer/content names、position、rayTracing/post flags、prebuilt BLAS 和 vertex/index direct buffers；输出 native batch，区分 world geometry、post-render geometry 与 per-layer metadata。
- 当前对应：`versions/.../client/proxy/world/EntityProxy.java` 存在且更大，含 name-tag priority/see-through 处理；目标 mixin 包含 `EntityRendererMixins`, `Particle*`, `HeldItemRendererMixins`, `HumanoidArmorLayerMixins`, `ModelPartWorldOutlineMixins` 等。native 为 component `core/render/entities.{hpp,cpp}` 与 middleware `...proxy_world_EntityProxy.cpp`。
- 适配/证据/未知：`Loader API 适配 + 语义扩展 + ABI 联动`；R-U 的 EntityProxy 在 R-P 从旧根移入 target，N-U/N-P 的 entity structures 保留。current name-tag/flag 改动不能推断与上游完全等价；实体、粒子、天气和手部尚未视觉验收。

### U-J08 主渲染帧、camera/uniform、GUI/overlay 与截图路径（配对 U-N01/U-N06）

- 上游符号/路径：`R-U:src/main/java/com/radiance/mixins/vulkan_render_integration/{WorldRendererMixins,GameRendererMixins,MinecraftClientMixins,DrawContextMixins,ScreenMixins,WindowMixins}.java`；`client/proxy/world/PlayerProxy.java`；`client/proxy/vulkan/RendererProxy.java`。
- 输入/输出/状态/依赖：render frame 输入 camera/view/effected/projection/fog/sky/lightmap/overlay texture；WorldRenderer mixin 将 vanilla world render 替换为 terrain/entity/weather/cloud capture，GameRenderer 在 frame tail 调 `EntityProxy.build`、`RendererProxy.fuseWorld`，另负责 first-person overlay projection、blur、UI-free screenshot；Window 强制 GLFW `GLFW_NO_API` 并取消 GL context/capability 路径。
- 当前对应：`versions/.../mixins/vulkan_render_integration/{WorldRendererMixins,GameRendererMixins,MinecraftClientMixins,DrawContextMixins,ScreenMixins,WindowMixins}.java`、后续 dirty `earlywindow/bridge/*`、`platform/neoforge/NeoForgeVulkanWindowHandoff.java`；component native `core/render/render_framework.*`, `core/vulkan/window.*`, current renamed middleware Renderer/Window proxy。
- 适配/证据/未知：`NeoForge handoff + GLFW_NO_API + 后续 Early Window 联动`；R-P 只新增旧 `platform/neoforge/NeoForgeVulkanWindowHandoff.java` 与相关 mixin，当前 `earlywindow/bridge` 全套并非 cd67362 本身的证据；N-P 新增 target CMake/headers/tests。是否完整保留官方 Early Window lifecycle、loading overlay 和首帧 handoff，仅静态可达，未启动。

## 首批 Native 能力单元

### U-N01 Vulkan Framework、swapchain、frame contexts 与 fuse/present（配对 U-J01/U-J08）

- 上游符号/路径：`N-U:src/core/render/render_framework.{hpp,cpp}` 的 `Framework`, `FrameworkContext`, `FrameResourceRetainer`；`src/core/vulkan/{instance,physical_device,device,vma,swapchain,command,sync,window,framework}.{hpp,cpp}`。
- 输入/输出/状态/依赖：输入 GLFW window、swapchain image count、Vulkan instance/device/queues；每 frame 保存 upload/overlay/world/fuse command buffers、semaphores、fence、resource-retainer context；输出 acquire→upload/world/overlay→fuse→submit→present，resize/recreate 时维护 device/swapchain 状态。依赖 Vulkan-Headers/volk/VMA/GLFW/JNI host。
- 当前对应：`components/vulkan-renderer/src/core/render/render_framework.*` 与同目录 `core/vulkan/*`，current renamed middleware `.../io_github...RendererProxy.cpp`；Radiance early-window bridge 是 host 侧后续配套。
- 适配/证据/未知：`保留 + staging/target 适配 + 后续 ABI rename`；N-U→N-P 的 framework/vulkan core 文件均保留，`io_github...` 是 current dirty component 的 namespace integration，不是 N-P Initial port 文件名证据。没有 CMake/CTest/客户端执行证据，不能称 native frame 完成。

### U-N02 JNI middleware 与 Java/native ABI（配对 U-J05/U-J06/U-J07）

- 上游符号/路径：`N-U:src/core/middleware/com_radiance_client_*` 全组，包括 Options、Pipeline、Renderer/Buffer/Texture/Shader/Window/PipelineState/DrawCommand 及 world Chunk/Entity/Player。
- 输入/输出/状态/依赖：JNI `JNIEnv*`、primitive/UTF-8 strings、direct-buffer addresses、generated `com_radiance_*.h`；输出为 native renderer/pipeline/texture/chunk/entity 状态变化或 GPU resource handles；异常必须跨 JNI 边界处理。依赖 Java declarations、JNI headers、core CMake target。
- 当前对应：`components/vulkan-renderer/src/core/middleware/io_github_recrivenvi_radiance_client_*`，另有 `jni_exception.hpp`；CMake 接受 `RADIANCE_NATIVE_JNI_INCLUDE_DIR` 并计算 header SHA-256；target Java package 是 `io.github.recrivenvi.radiance`。
- 适配/证据/未知：`后续包名重命名 + generated-JNI/manifest 联动`；N-U/N-P Initial port middleware 仍是 `com_radiance_client_*`，`io_github...` 是 current dirty Radiance integration，不能把两者描述成同一提交的简单 rename。当前 JNI 头未在本审计生成，symbols/hash 与运行时异常语义待主代理证明。

### U-N03 Chunk/entity GPU staging、BLAS 与 world resource batches（配对 U-J06/U-J07）

- 上游符号/路径：`N-U:src/core/render/{chunks,entities,buffers,vertex_formats,world}.{hpp,cpp}`；`N-U:src/core/middleware/com_radiance_client_proxy_world_{ChunkProxy,EntityProxy}.cpp`。
- 输入/输出/状态/依赖：ChunkBuildTask/EntitiesBuildTask 输入 geometry type/group/content、texture/format/count、PBR vertex/index 地址、位置/flags；native 复制/pack position/material/index buffers，构造 chunk/entity BLAS，维护 build batches、light buffers、entity post batch；输出给 ray tracing/world modules 的 device addresses/BLAS/geometry metadata。依赖 VMA、device address、acceleration structure extensions、shader mapping。
- 当前对应：component `core/render/chunks.*`, `entities.*`, `buffers.*`, `vertex_formats.cpp`, `world.*` 及 renamed middleware；current Java target 的 ChunkProxy/EntityProxy 已增加 name-tag/semantic 分流。
- 适配/证据/未知：`保留 + ABI/语义扩展 + 后续 middleware rename`；N-U/N-P core 路径均在，N-P 新增 `index_patterns.hpp`/address tracker 等支撑；current `io_github...` 仅是后续集成路径。BLAS、light emission、post batch 的真实 GPU 生命周期尚未运行验证。

### U-N04 Vulkan resource/device-address/synchronization 层（配对 U-J05/U-J08）

- 上游符号/路径：`N-U:src/core/vulkan/{buffer,image,vertex,descriptor,pipeline,render_pass,dynamic_pipeline,framebuffer,sbt,as,device,command,sync}.{hpp,cpp}`；`src/common/{mapping,shared,singleton}.hpp`。
- 输入/输出/状态/依赖：输入 VMA allocations、VkImage/VkBuffer usage/layout、device feature queries、descriptor/pipeline descriptions；输出 reusable Vulkan wrappers、device addresses、descriptor tables、barriers and fences. 依赖 Vulkan 1.4-ish headers, volk, VMA, GLM；`address_binding_tracker` 不是 N-U 路径。
- 当前对应：component `core/vulkan/*` 和 `src/common/*` 均存在；N-P 另新增 `src/core/vulkan/address_binding_tracker.{hpp,cpp}`，current CMake 将 headers/extern 固定到 component。
- 适配/证据/未知：`保留 + target/build 适配`；N-U→N-P diff 为大量 implementation changes 而非路径删除。AMD layout、device fault、sync barriers 的行为未被本次静态审计执行证明。

### U-N05 Native module graph、WorldPipeline 与 image contracts（配对 U-J03）

- 上游符号/路径：`N-U:src/core/render/pipeline.{hpp,cpp}`, `world_module.{hpp,cpp}`, `modules/world/world_module.hpp`, `modules/world/*_module.{hpp,cpp}`，含 UI、post_render、ray_tracing、DLSS、FSR、NRD、SVGF、temporal accumulation、tone mapping、XeSS。
- 输入/输出/状态/依赖：JNI `WorldPipelineBuildParams` 提供 module names、input/output image indices/formats、attribute key/value；native blueprint 校验 image index 连续性，创建 per-frame shared images、module contexts、shader pack，再按拓扑/连接执行；输出 world pipeline image 0 给 fuse。
- 当前对应：component `core/render/pipeline.*`, `modules/world/*` 全部保留，Radiance target `Pipeline.java` 仍生成该 params；资源 `modules/*.yaml` 与 built-in `configs.json` 是配对输入。
- 适配/证据/未知：`保留 + JNI ABI/target staging 适配`；N-U/N-P 路径审计未见 module source tree 缺失，主要变化在 contracts、formats、fallback。真实 module availability/preset rebuild 仍未运行。

### U-N06 Native UI/overlay rendering 与 post-render image path（配对 U-J08）

- 上游符号/路径：`N-U:src/core/render/modules/ui_module.{hpp,cpp}`；`src/shader/overlay/post/{blur.vert,blur.frag}`、`src/shader/world/post_render/{color_to_depth.vert,color_to_depth.frag,light_map.*,world_post*}`；`N-U:src/core/render/modules/world/post_render/post_render_module.*`。
- 输入/输出/状态/依赖：UI module 接收 overlay draw commands、scissor/blend/depth state、text/texture geometry；维护 overlay color/depth image 和 descriptor tables；post-render 读取 LDR/HDR/depth/motion/normal-roughness，输出 post-rendered image；依赖 native draw-state JNI、shader pack pass config 和 full-screen shader。
- 当前对应：component 保留 `core/render/modules/ui_module.*`, `post_render_module.*`, `shader/overlay/post/blur.*`, `shader/world/post_render/color_to_depth.*`；但 upstream 的 `light_map.*` 和 `world_post*.{vert,frag}` 在 N-P/current component 的 exact path 已删除（详见 U-N08）。
- 适配/证据/未知：`跨分层迁移 + 删除候选`；`git diff --summary N-U N-P` 明确列出这些删除/rename；当前 UI draw/fuse 尚未客户端验证，不能把 overlay 文件存在当作完整 post-render parity。

### U-N07 Ray-tracing world modules、shader-pack loader 与 built-in pack（配对 U-J03/U-J04/U-J07）

- 上游符号/路径：`N-U:src/core/render/modules/world/ray_tracing/{ray_tracing_module.*,submodules/world_prepare.*}`、`shader_pack/{shader_pack.*}`；`src/shader/world/ray_tracing/internal/{advanced,vanilla-pt}/` 的 configs/common/primary/world/volumetric/post_render（`priority/` 不在 N-U）；`src/common/mapping.hpp`、`N-U:extern/sharc`。
- 输入/输出/状态/依赖：输入 world/chunk/entity BLAS and mapping buffers、ray bounces/jitter/SHARC attributes、shader-pack config；输出 ray-traced radiance、albedo, normal/roughness, motion, first-hit/depth/fog/refraction images；loader 解析 configs，编译/加载 ray stages 与 execution descriptors。依赖 Vulkan RT, SHARC, shaderc/glslang, fixed extern snapshots。
- 当前对应：component 保留 advanced/vanilla-pt internal trees、configs/common/priority/world；其中 `priority/` 是 N-P 新增并由 current CMake 显式复制的 targetized extension；`shader_pack.*`, `ray_tracing_module.*`, `world_prepare.*` 均存在，目标 Java 仍有 `ray_tracing.yaml`。
- 适配/证据/未知：`保留 + package/build staging 适配`；N-U/N-P 的 core shader pack path 未删除，但 N-P 新增 `priority/` 并删除若干 post-render shader stages（U-N08）。没有生成 SPIR-V、读取 shader pack 或 RT runtime 证据。

### U-N08 Initial port 相对上游删除的 post-render text/star shader集合（配对 U-J07/U-J08）

- 上游符号/路径：N-U exact paths `src/shader/world/post_render/{light_map.frag,light_map.vert,world_post.frag,world_post.vert,world_post_star.frag,world_post_star.vert,world_post_text.frag,world_post_text.vert}`；`src/shader/world/ray_tracing/internal/{advanced,vanilla-pt}/post_render/{render_star.frag,render_star.vert,render_text.frag,render_text.vert}`；N-U `post_render_module.hpp/cpp` 含 `nameTagPostFlag`, `starCount_`, `RenderPass::Target::{NameTag,Star}`、star field buffer 和 text/star dynamic pass。
- 输入/输出/状态/依赖：text/name-tag/star entity post batches、first-hit depth、cloud transmittance、world uniforms；shader 输出对应 post-render target；依赖 N-U configs 中 `post_render_text`/`post_render_star` pass 引用和 Java entity post flags。
- 当前对应：N-P/current component exact path 缺失上述 16 个 shader；`git diff --diff-filter=DR --summary N-U N-P` 给出 16 条删除（仅 `light_map.vert` 74% rename 至 `overlay/post/diagram.vert`）。current `post_render_module` 只保留 weather/particle/text flags；current configs 无 `render_text`/`render_star` exact references，但仍有 `post_star_cloud_transmittance` outputs，Java target 另增 priority `name_tag_text` 路径；current 还保留 `render_world_post.{vert,frag}` 与 `priority/*` 等替代候选。
- 适配/证据/未知：`exact-path 删除记录 + 替代关系已静态复核`，不据此宣称行为断裂。高置信结论是 Initial port 删除了 upstream exact shader assets，并存在 `render_world_post`、priority name-tag、sky-miss/云透射替代候选；它们是否完整承接 text/name-tag/star 语义仍是动态未知。

## 第二批 Java 能力单元

### U-J09 PBR vertex consumer、材质字段与 alpha/text mode（配对 U-N09）

- 上游符号/路径：`R-U:src/main/java/com/radiance/client/vertex/{PBRVertexConsumer,PBRVertexFormatElements,PBRVertexFormats,StorageVertexConsumerProvider,StorageOutlineVertexConsumerProvider}.java`；`mixins/vulkan_render_integration/{SectionBuilderMixins,BlockModelRendererMixins,FluidRendererMixins,BufferRendererMixins,EntityRendererMixins}.java`。
- 输入/输出/状态/依赖：输入 vanilla `VertexConsumer` 调用、RenderLayer、texture/overlay/light/normal/color/glint；PBR consumer 维护 128-byte vertex stride、writable/required masks、texture id、alpha mode、post base；输出 PBR fields（position/normal/color/UV/light/glint/coordinate/emission）和 `BuiltBuffer`，依赖 custom `VertexFormatElement` IDs 6–23、LWJGL direct memory、native vertex layout。
- 当前对应：`versions/.../client/vertex/` 同名类存在；target mixin 仍在 `vulkan_render_integration/`，并新增 `PBRMaterialContext`、`StorageRoutingBufferSource` 等 dirty current helpers。native component 对应 `core/vulkan/vertex.*`, `core/render/vertex_formats.cpp`, `shader/util/vertex.glsl`。
- 适配/证据/未知：`Loader API 适配 + ABI/layout 联动`；R-U/R-P `PBRVertexConsumer` 为大改而非简单复制，`N-U/N-P` 同步调整 vertex layout/material packing。128-byte stride 与 shader offsets 的最终运行一致性未验证。

### U-J10 资源纹理 ID、PBR 辅助纹理与 atlas 路由（配对 U-N10）

- 上游符号/路径：`R-U:src/main/java/com/radiance/client/texture/{TextureTracker,AuxiliaryTextures,AuxiliaryTextureReloader,IdentifierInputStream}.java`；`mixins/vanilla_resource_tracker/{TextureManagerMixins,NamespaceResourceManagerMixins,NativeImageMixins,NativeImageBackedTextureMixins,ReloadableTextureMixins,SpriteContentsMixins,TextureUtilMixins,DirectoryAtlasSourceMixins,SingleAtlasSourceMixins,UnstitchAtlasSourceMixins,PalettedPermutationsAtlasSourceMixins}.java`。
- 输入/输出/状态/依赖：输入 `Identifier`、NativeImage/resource stream、vanilla GL texture id、atlas sprite uploads；状态维护 `textureID2GLID`、GLID→base/specular/normal/flag maps 和 NativeImage sidecars；输出 tracked texture metadata、auxiliary image uploads/skip decisions。`_s` 为 specular、`_n` 为 normal、`_f` 为 `textures/flag`，依赖 `INativeImageExt` and `TextureProxy`。
- 当前对应：目标目录存在同名 tracker/auxiliary/mixin chain；current dirty `AuxiliaryTextures` 还加入 particle paths；资源 target 下可见大批 `*_s.png`、`flag/*_f.png`。native 对应 component `core/render/textures.*`, `emission.*`, TextureProxy middleware。
- 适配/证据/未知：`重命名 + Minecraft/Loader API 适配`；R-U/R-P 该链路保留但有 1.21.1 API 变更。当前 atlas reload、sidecar 生命周期和 mod resource precedence 只静态可达，未真实资源重载。

### U-J11 Mipmap、emission tile 提取与光源元数据（配对 U-N03/U-N10）

- 上游符号/路径：`R-U:src/main/java/com/radiance/client/texture/{MipmapUtil,EmissionRecorder}.java`；`client/texture/AuxiliaryTextures.java` 调用 `buildTileUpdate`；`client/option/Options.java` 的 `collectChunkEmission`。
- 输入/输出/状态/依赖：输入 albedo/specular NativeImage、upload region/offset/mipmap and texture dimensions；`MipmapUtil` 生成 gamma/alpha-aware chain，cutout alpha threshold 为 0.5；`EmissionRecorder` 按最多 8×8 subdivision 解析 LabPBR specular alpha、计算 UV bounds/平均 radiance、greedy merge 成 tile cells；输出 tile key/cells 到 `TextureProxy.uploadEmissionTile`，依赖 TextureTracker dimensions and native Emission R-tree.
- 当前对应：`versions/.../client/texture/{MipmapUtil,EmissionRecorder,AuxiliaryTextures}.java` 均存在；component `core/render/emission.*` 接收 cell upload 并建立 per-texture tile map/R-tree，`core/render/chunks.cpp` 将 light buffer 写入 BLAS/world data。
- 适配/证据/未知：`重命名 + ABI/资源语义联动`；R-U/R-P 与 N-U/N-P 均有 emission 相关改动。是否覆盖 particle/entity/atlas 以及 toggle 后清理旧 tiles，不能由静态存在断言，需运行时验证。

### U-J12 Pipeline/attribute/shader-pack GUI（配对 U-J03/U-J04/U-N05/U-N06）

- 上游符号/路径：`R-U:src/main/java/com/radiance/client/gui/{RenderPipelineScreen,ModuleAttributeScreen,ShaderPackScreen,ShaderPackSettingsScreen,AttributeWidgetUtil,ModuleNode,PotentialValuesBasedCallbacksNoValue}.java`。
- 输入/输出/状态/依赖：输入 module graph、attribute type/value/range/enum/vec3、available shader packs and parent Screen；状态为 node positions, selected widgets, scroll offsets and pending pipeline edits；输出 `Pipeline.connect/addModule/savePipeline/setShaderPack` calls, with bool/enum/int/float/string/vec3/range validation borders. 入口由 Video Options mixin 的 Pipeline/Shader Pack buttons。
- 当前对应：target `client/gui/` 全部同名类存在，且 imports 已切到 `io.github...`；target `radiance.mixins.json` 注册 `vulkan_options` 和 render integration GUI mixins。current target 的 GUI 是否能在 game 实际打开、编辑和回退，本审计不作 PASS。
- 适配/证据/未知：`重命名 + Loader/Minecraft GUI API 适配`；这些类在 R-P 未被整体删除。用户可见布局、mouse/keyboard interaction 与 dynamic attribute translation 是未验证项。

### U-J13 Loader-与第三方渲染兼容层（Initial port 相对上游新增，配对 U-N03/U-N06）

- 上游符号/路径：R-U 没有 `compatibility/` Java subtree；R-P 新增 `src/main/java/com/radiance/compatibility/{create,flywheel,neoforge,sable,simulated,veil}/` 与 `mixins/compatibility/{aeronautics,create,flywheel,ponder,sable,simulated,veil}/`，并在 target `radiance.mixins.json` 注册。
- 输入/输出/状态/依赖：Create ValueBox、Flywheel/ Ponder OpenGL paths、Sable external sublevel/render sections、Simulated diagram/Veil render system、Aeronautics client setup；通过 thread-local capture、render-state skips、reflection/bridge/accessors 把第三方 geometry/UI 接入 Radiance PBR buffers 或 Vulkan camera。依赖第三方 mod classes and loader event signatures。
- 当前对应：`versions/.../compatibility/` 和 `versions/.../mixins/compatibility/` 均存在，目标 mixin JSON 可达；其中 `SimulatedDiagramCompatibility`、`SableSubLevelBridge` 是明显的 dirty current 扩展。
- 适配/证据/未知：`Initial port 相对上游新增 + NeoForge/外部 mod 适配`，不是上游遗漏；R-P 新增路径证据来自 `git diff --diff-filter=A R-U R-P`。current dirty 可能继续扩展这些桥，但不反向改写 R-P 谱系。未启动第三方 mod，不能宣称兼容或 parity；Create/Sable/Aeronautics fixtures 仍须隔离。

### U-J14 NeoForge Early Window、平台与纯 Vulkan 接管（Initial port 相对上游新增，配对 U-N01）

- 上游符号/路径：R-U 只有 Fabric `fabric.mod.json`/`WindowMixins`，没有 Early Window 平台。R-P 新增旧 `versions/1.21.1-neoforge/src/main/java/com/radiance/platform/{RadiancePlatform,neoforge/NeoForgeVulkanWindowHandoff,RadianceNeoForge}.java` 与相关 `NeoForgeWindowHandoffMixins`；当前 dirty 后续才出现 `earlywindow/bridge/{OfficialGlBridge,OfficialGlfwBridge,OfficialProgressBridge,OfficialResourceBridge,RadianceEarlyWindowNative}.java` 及 `NeoForgeLoadingOverlayMixins`。
- 输入/输出/状态/依赖：输入 NeoForge window/event lifecycle、GLFW handle、official early-display progress/resource hooks；输出 borrowed-window handoff、Vulkan surface/device diagnostics、loading overlay and native library handoff；依赖 target native `src/main/native/early_window` 与 `integration/early-window/CMakeLists.txt`。
- 当前对应：target `platform/`, `earlywindow/bridge/`, `src/main/native/early_window/*.cpp|*.hpp`, `integration/early-window/` 均发现；Gradle staging 保留 `radiance_native` 与 `radiance_early_window` names。
- 适配/证据/未知：`R-P NeoForge platform addition + 后续 Early Window integration`，并非 R-U capability 的 direct counterpart；R-P target tree 只证明旧 handoff，当前 bridge/official patch 属于后续 dirty。当前 bridge 只具静态 source/build wiring 证据，未启动客户端或验证 official loading overlay。

### U-J15 原始 upstream-only Java mixin 差异追查（配对 U-N08/U-N15）

- 上游符号/路径：R-U exact `src/main/java/com/radiance/mixins/vulkan_render_integration/{BillboardParticleMixins,ClientChunkManagerMixins,CloudRendererMixins}.java`。
- 输入/输出/状态/依赖：`BillboardParticleMixins` 只针对 `WhiteAshParticle` 将 billboard 四顶点缩放到 ±1/8；`ClientChunkManagerMixins` 在 `onLightUpdate` HEAD 取消 vanilla light update；`CloudRendererMixins` 接管 cloud tessellation/fancy/fast colors, border flags, capture provider，并以 `EntityProxy.processWorldEntityRenderData` 送 native。依赖 1.21.4 Yarn method descriptors。
- 当前对应：当前 target 没有同名 basename；`CloudRenderer` 的语义替换候选是新增 `client/proxy/world/CloudProxy.java` + `WorldRendererMixins` 的 `CloudProxy.queue`，其静态代码确实重做 cell mask、fast/fancy faces、边界和 `EntityProxy.queueBuildWithoutClose`；particle 路径改由 current `EntityProxy`/`ParticleManagerMixins`，但没有直接 `BillboardParticle` resize mixin；没有直接 `ClientChunkManager` light-update replacement。
- 适配/证据/未知：`原始遗漏候选/待确认 API 重写`；basename inventory 为 upstream 140 Java files vs current target 211，只有上述 3 个 upstream names 无当前 basename。CloudProxy 的静态 geometry 对应较强；WhiteAsh 两版原生对照已闭合该特定恢复点，light-update 仍是 callback 取消差异。两者都不等于运行/视觉 parity，也不自动授权补回旧 mixin。

### U-J16 upstream 资源/模块/材质资产集合（配对 U-N07/U-N08/U-N09）

- 上游符号/路径：R-U `src/main/resources/` 共 354 个 tracked paths：`modules/{dlss,fsr_upscaler,nrd,post_render,ray_tracing,temporal_accumulation,tone_mapping,xess_sr}.yaml`、`assets/radiance/{icon.png,lang/*,textures/gui/render_pipeline/gear.png*}`、Minecraft redstone model/blockstate、`*_s.png`, `flag/*_f.png`, sun/moon/lightning and `XESS_LICENCE.txt`。
- 输入/输出/状态/依赖：module YAML 是 Java `ModuleEntry`/native module graph 输入；PBR assets 按 suffix/namespace 被 `AuxiliaryTextures` 分类；icon/lang/gear 是 GUI/UI 输入；XESS licence accompanies vendor dependency. 输出为 runtime `radiance/modules`、`radiance/shaders` and texture maps。
- 当前对应：target `src/main/resources` 356 files，模块 8 个、radiance assets 5 个、Minecraft texture/model assets 保留；`radiance.accesswidener` 无 exact target path，替换候选为 `META-INF/accesstransformer.cfg`。target 额外有 `META-INF/{mods.toml,neoforge.mods.toml}` 和 `fabric.mod.json`。
- 适配/证据/未知：`资源重定位 + NeoForge metadata/access transformer`；normalized exact-path 对比为 upstream 354 vs current target 356，仅 `radiance.accesswidener` 缺失。内容 hash、license semantics、runtime extraction and JAR packaging 未在此处验证。

### U-J17 Java 构建/Loader metadata 与 target authority（配对 U-N16）

- 上游符号/路径：R-U `build.gradle`（Fabric Loom 1.11-SNAPSHOT、Minecraft 1.21.4/Yarn、Java 21、SnakeYAML include）、`gradle.properties`、`settings.gradle`、`fabric.mod.json`/`radiance.accesswidener`。
- 输入/输出/状态/依赖：输入 Fabric loader/API/Minecraft mappings and `src/main/native/include` generated JNI headers；输出 Fabric JAR/source JAR、native resource packaging and `runClient` args；依赖 Gradle/Loom/Maven repositories, Java 21, SnakeYAML and external MCVR install.
- 当前对应：R-P 删除旧 `build.gradle`/`settings.gradle`，新增 `build.neoforge.gradle.kts`, Stonecutter settings/properties, target `build.gradle.kts`/`target.properties`, `META-INF/neoforge.mods.toml`; current root/target build wires component staging and Java 21 native target.
- 适配/证据/未知：`Loader/target authority 迁移`；R-U→R-P diff 明确显示构建文件/metadata replacement，current AGENTS further requires only `1.21.1-neoforge` implemented. No build was run by this audit; source wiring does not prove artifact or client acceptance.

## 第二批 Native 能力单元

### U-N09 Native PBR vertex layout、material packing 与 shader semantics（配对 U-J09）

- 上游符号/路径：`N-U:src/core/vulkan/vertex.{hpp,cpp}`, `src/core/render/vertex_formats.cpp`, `src/shader/util/{vertex,alpha_mode,labpbr,text_mode}.glsl`；N-P 新增/变更 `src/shader/util/{emissive_overlay,glint_material,priority_payload}.glsl`。
- 输入/输出/状态/依赖：输入 Java PBR vertex arrays/indices and material flags; native splits packed PositionVertex/MaterialVertex, uses device addresses and RT geometry; GLSL decodes `use*`, alpha mode, LabPBR normal/roughness/emission, glint and post payload. Dependencies are Vulkan vertex layouts, GLM, mapping/shared headers and shader pack include staging.
- 当前对应：component `core/vulkan/vertex.*`, `core/render/vertex_formats.cpp`, `shader/util/*` includes all N-P paths; current contract tests inspect glint/transparency semantics. Java PBR classes are target-relative counterpart.
- 适配/证据/未知：`保留 + Initial port material/GLSL extensions`；N-U/N-P diff lists additions and modifications but no core vertex path deletion. Shader compile and GPU material output unverified.

### U-N10 Native texture upload, mapping, emission cells and light buffers（配对 U-J10/U-J11）

- 上游符号/路径：`N-U:src/core/render/{textures,emission}.{hpp,cpp}`, `src/core/middleware/com_radiance_client_proxy_vulkan_{TextureProxy,BufferProxy}.cpp`, `src/common/mapping.hpp`。
- 输入/输出/状态/依赖：input texture allocation/format/mipmap/filter/clamp/uploads, Java emission tile cells and texture mapping; state keeps frame-retained upload queues, samplers, per-texture emission R-tree; output GPU images, mapping buffers and chunk light buffers. Dependencies VMA, staging buffers, Vulkan barriers, Java direct addresses.
- 当前对应：component renamed middleware and all `core/render/textures.*`, `emission.*`, `common/mapping.hpp`; current tests include `verify_texture_lifecycle_contract.cmake`, `verify_native_image_upload_contract.cmake`, `verify_resource_reload_contract.cmake`.
- 适配/证据/未知：`保留 + JNI namespace/staging adaptation`；N-U/N-P source paths remain and N-P adds tests/edge handling. No CTest or real upload/reload execution in this audit.

### U-N11 NVIDIA DLSS/NGX module and runtime dependency (配对 U-J02/U-J03)

- 上游符号/路径：`N-U:src/core/render/modules/world/dlss/{dlss_module,dlss_wrapper}.{hpp,cpp}`, `src/core/render/pipeline.cpp` `isDlssDeviceExtensionsCompatible`/`initNGXContext`; `N-U:.gitmodules` `extern/DLSS` points to `https://github.com/Ljiong201108/DLSS.git`.
- 输入/输出/状态/依赖：input HDR/albedo/normal/motion/depth images and DLSS mode; NGX context creates feature, dispatches evaluate, outputs processed radiance and upscaled depth/motion/normal. Dependency includes NGX headers/import libs/runtime and device extension checks; module registration is conditional on successful `initNGXContext`.
- 当前对应：component retains `core/render/modules/world/dlss/*` and `cmake/dlss.cmake`; current VENDOR records `extern/DLSS` at official `https://github.com/NVIDIA/DLSS.git` revision `a291cc7d...`, with nested NVIDIAImageScaling. `resources/modules/dlss.yaml` and Java DLSS option remain.
- 适配/证据/未知：`SDK source/repository adaptation + ABI/staging`；N-U→N-P `.gitmodules` changes custom fork to official NVIDIA URL, while current CMake maps debug/release import libs/runtime DLLs. License/runtime presence, NGX API parity and actual DLSS feature availability remain unknown.

### U-N12 FidelityFX FSR3 and Intel XeSS upscaler modules（配对 U-J02/U-J03）

- 上游符号/路径：`N-U:src/core/render/modules/world/fsr_upscaler/{fsr3_upscaler,fsr_setup,fsr_upscaler_module}.{hpp,cpp}`, `xess_upscaler/{xess_sr_module,xess_wrapper}.{hpp,cpp}`；shader `world/upscaler/*.comp`；`Pipeline::collectWorldModules` conditional XESS registration.
- 输入/输出/状态/依赖：upscalers consume color/depth/motion/first-hit-depth/normal-roughness, quality mode, sharpness/pre-exposure and camera reset state; output upscaled radiance/depth/motion/normal. FSR loads FFX Vulkan provider; XeSS checks device extensions and runtime library. Dependencies FidelityFX-SDK, XeSS headers/lib/DLLs, Vulkan device proc addresses.
- 当前对应：component retains module/source/shader paths; `components/vulkan-renderer/CMakeLists.txt` builds a generated FFX mirror with frame-generation/optical-flow/denoiser providers removed, while VENDOR pins `FidelityFX-SDK d08c34ca...` and `xess 8fe81bdb...`. Java options and YAML modules remain under target.
- 适配/证据/未知：`build-system adaptation + SDK runtime staging`；N-U/N-P modules not path-deleted, but CMake/generated mirrors differ. Current availability depends on runtime libraries and device extensions; no upscaler dispatch or visual evidence.

### U-N13 NRD/SVGF/temporal denoising chain and registration caveat（配对 U-J02/U-J03）

- 上游符号/路径：`N-U:src/core/render/modules/world/nrd/{nrd_module,nrd_wrapper}.{hpp,cpp}`, `svgf/{svgf_module,svgf_denoiser,blue_noise}.{hpp,cpp}`, `temporal_accumulation/temporal_accumulation_module.*`; shaders `world/nrd/*.comp` and `world/svgf/*.comp`; `Pipeline::collectWorldModules`.
- 输入/输出/状态/依赖：NRD consumes diffuse/specular/direct radiance, albedo, normal/roughness, motion/depth, hit-depth/fog/refraction and uploads Reblur settings; SVGF owns its denoiser pipeline; temporal accumulation consumes color/motion/normal and history. Outputs denoised/accumulated radiance. Dependencies NRD SDK/shader headers, Vulkan descriptors, per-frame history images.
- 当前对应：component retains all module/source/shader trees and target `resources/modules/{nrd,temporal_accumulation}.yaml`; N-P adds `nrd_glsl_compat.glsl`. However both N-U and N-P `pipeline.cpp` leave `SvgfModule` constructor/image registration commented as “Not working well”; source presence is not module reachability.
- 适配/证据/未知：`保留但 SVGF 明确不可达 + NRD SDK/build adaptation`；N-U and N-P line-level registration evidence shows this is upstream baseline behavior, not an Initial port omission. NRD runtime/device support and temporal history correctness unverified.

### U-N14 Tone mapping, exposure histogram and post-render image transforms（配对 U-J02/U-J03/U-N06）

- 上游符号/路径：`N-U:src/core/render/modules/world/tone_mapping/{tone_mapping_module}.{hpp,cpp}`；shaders `src/shader/world/tone_mapping/{hist,exposure,tone_mapping.vert,tone_mapping.frag}`；`post_render_module.*` input/output image contracts。
- 输入/输出/状态/依赖：tone mapping consumes denoised HDR radiance and settings (method, auto/manual exposure, meter mode, percentiles, white point, saturation, clamp); maintains histogram/exposure buffers and previous exposure; outputs LDR. Post-render consumes LDR/HDR/depth/motion/normal and may run dynamic passes. Dependencies shaderc/GLSL, descriptor tables and module graph image formats.
- 当前对应：component retains tone mapping source/shaders, post_render module and `resources/modules/tone_mapping.yaml`/`post_render.yaml`; current CMake installs shader packs from component. Current post-render config has copy/motion-blur/DOF stages but lacks N-U text/star shader assets (U-N08).
- 适配/证据/未知：`保留 + post-render asset split`；N-U/N-P diff shows post_render implementation shrink while tone mapping remains. Exposure state, DOF/motion-blur and LDR fuse not runtime validated.

### U-N15 Cloud, volumetric light, celestial and indirect sky shader set（配对 U-J07/U-J15）

- 上游符号/路径：`N-U:src/core/render/world.*`, `ray_tracing_module.*`, shader `ray_tracing/internal/{advanced,vanilla-pt}/{common/clouds.*,common/volumetric_cloud.*,volumetric_light/*,world/world.rgen,world/world_no_reflect.*,post_render/render_star.*}`。
- 输入/输出/状态/依赖：input sky angle/color, cloud weather/noise/height, camera/fog and RT world rays; outputs cloud transmittance, sky radiance, volumetric light and post-star cloud factor. Dependencies RT shader packs, cloud textures (`cloudnoise`, `cloudweather`, volumetric raw data), Vulkan RT and world uniform mapping.
- 当前对应：component retains cloud/volumetric shader files and `post_star_cloud_transmittance` declarations in both `advanced`/`vanilla-pt` configs/world shaders; Java current `CloudProxy` is a new capture route. Exact upstream post-render `render_star.vert/frag` are missing in N-P/current component, so star composite is not proven.
- 适配/证据/未知：`保留 + cloud Java rewrite + star deletion candidate`；exact missing list in U-N08 and current `rg` show no `render_star` references. Whether cloud transmittance is consumed by another pass or dead data is unknown.

### U-N16 Native SDK snapshots, CMake target, install/staging and license dependencies（配对 U-J01/U-J17）

- 上游符号/路径：`N-U:CMakeLists.txt`, `src/core/CMakeLists.txt`, `src/shader/CMakeLists.txt`, `.gitmodules`, `extern/{DLSS,FidelityFX-SDK,glfw,glm,json,minizip-ng,nrd,sharc,stb,tinyexpr,vma,volk,vulkan_headers,xess}` gitlinks；upstream installs `core` and resources into Java `src/main/resources`.
- 输入/输出/状态/依赖：inputs Java project root/generated JNI, VULKAN SDK/shaderc, fixed submodules; output `core.dll|libcore.so`, SPIR-V, built-in shader ZIPs and optional SDK DLLs. CMake enables FFX FSR3-upscaler, NRD, XeSS, minizip, nlohmann JSON and creates `vanilla-pt|advanced` packs; dependencies include all 14 external gitlinks and minizip fetches.
- 当前对应：component is a Radiance-tracked ordinary CMake tree with `CMakePresets.json`, `cmake/targets/1.21.1-neoforge.cmake`, `cmake/dlss.cmake`, `VENDOR.json`, pinned full `extern/`, generated FFX/NRD mirrors, vendored minizip dependency directories and `radiance-native-manifest.json.in`; install target is `build/native/install/1.21.1-neoforge` rather than authored Java resources. Current policy preserves `radiance_native` names.
- 适配/证据/未知：`component relocation + build/staging hardening + dependency pinning`；N-P adds target/preset/manifest/tests while R-P adds the Gradle/Stonecutter target side, and current AGENTS prohibits submodule/sibling MCVR dependency. No build performed here; CMake generator, SDK availability, manifest hashes and install output remain unverified.

### U-N17 Native contract tests, shader/manifest/JNI gates（Initial port 相对上游新增，配对全体）

- 上游符号/路径：N-U has no `tests/` tree. N-P adds `tests/CMakeLists.txt`, `contract_test.cpp` and 16 CMake verifiers (`verify_borrowed_window_contract`, `device_address_diagnostics`, `dlss_resource`, `emissive_overlay`, `entity_line_safety`, `glint`, `jni_coverage`, `manifest`, `native_image_upload`, `particle_world_geometry`, `resource_reload`, `shaders`, `texture_lifecycle`, `transparency_semantics`, `vanilla_effects`, `world_text`).
- 输入/输出/状态/依赖：tests inspect source contracts, generated JNI headers, middleware files, manifest target/JNI hashes, compiled SPIR-V and built-in ZIP contents; output CTest pass/fail evidence. Dependencies CMake/CTest, JNI headers, configured install tree and target Java source path.
- 当前对应：component `tests/` contains the same 18 tracked test files; current `CMakeLists.txt` wires `RADIANCE_NATIVE_BUILD_TESTS`, target Java source check, manifest and shader/install variables. Tests are static/contract gates, not game startup.
- 适配/证据/未知：`Initial port 相对上游新增 + targetized CTest`；N-P `git diff --diff-filter=A` proves no upstream tests existed。current `verify_shaders.cmake` 明确把旧 `world_post(_star|_text)`/`light_map` SPIR-V 列为 obsolete，同时要求 `priority/{priority.rgen,outline.rchit,text.rchit,composite.comp}` 进入两个 built-in ZIP，这支持“删除后有 priority 取代候选”的静态事实，但不证明功能 parity。本审计未运行 CTest/build，因此不报 PASS。

## 覆盖边界与遗留追查

当前已覆盖 34 个稳定单元（Java U-J01–U-J17、native U-N01–U-N17），覆盖启动/staging、Vulkan/JNI ABI、世界/实体/chunk capture、PBR/material、纹理与 emission、GUI/pipeline 序列化、RT/shader packs、UI/post-render、DLSS/FSR/XeSS、NRD/SVGF/temporal、tone mapping、cloud/volumetric、NeoForge platform、资源与 CMake/SDK/CTest。路径计数是审计导航证据，不是完成度指标。

### 已有高置信静态结论

- R-U/R-P 与 N-U/N-P 的唯一父关系、Initial port 提交和起始封存已核对；R-U Java 共 140 个 source paths，当前 1.21.1 target basename 对比仅缺 `BillboardParticleMixins`、`ClientChunkManagerMixins`、`CloudRendererMixins`。
- N-U source 共 353 paths；N-P/current component source 共 366 paths。按 `src/core/middleware/com_radiance_...`→current `io_github_recrivenvi_radiance_...` 的后续集成命名归一后，core/native source 均有对应；N-U→N-P exact 缺口是 16 个 post-render shader paths，N-P 新增 `priority/` 与 tests/target build。
- 主代理的签名扫描已报告 upstream Java 80 个 native declarations/16 个 owner、MCVR 81 个 exports；类型与 arity 均有同名候选，但当前 `io_github` namespace 仍必须以生成 JNI header、export symbol、manifest/hash 三者联锁证明。这个签名结果不替代运行时 ABI 验证。
- N-U/N-P `SvgfModule` 注册都处于注释禁用状态；不能因为 source/shader/YAML 存在就把 SVGF 当作可达模块。

### 已闭合的静态恢复点（不等于动态 PASS）

- P-03：双上游精确 extract/父提交来源已固定；upstream Java 80 个 native declarations/16 个 owner 与 MCVR 81 个 exports 的类型、arity 同名候选已完成源级配对。generated JNI、二进制 symbol 和运行时指针契约仍不属于静态闭合。
- P-06：module YAML→Java `Pipeline`→native constructors/image contracts、两个 built-in pack、manifest/resource staging、旧 16 个 post-render exact shader 删除及 `priority/*`/`render_world_post`/sky-miss 替代关系已静态登记；这不等于编译、打包或行为 parity。
- P-11：WhiteAsh 两版本原生实现对照已闭合“是否仅因缺同名 Mixin 就必须恢复”的特定问题；当前仍保留静态未找到同名 hook 与用户决策边界，不自动修复。
- R-02/R-03：`ClientChunkManager` light-update callback 差异、CloudRenderer→CloudProxy 静态替代入口和资源 reload/close 路径已登记；仍不能从静态代码推出光照或云视觉等价。

### 动态/授权未证清单

1. 生成 current target JNI headers，做 Java declaration↔native export name/type/arity、header SHA-256、manifest target/JNI hash 检查；验证 `radiance_native`、`radiance_early_window` 与 component output 的 staged library/resource names。
2. 对 U-N08 的替代链做运行前后验证：`render_world_post`、`priority/*`、sky miss/cloud-transmittance consumer 是否承接 text/name-tag/star 语义，仍不证明相等或缺失。
3. 动态验证 U-J15：WhiteAsh 特殊缩放、ClientChunkManager light-update cancellation 与 CloudProxy 的真实入口/渲染结果；这些需客户端/视觉证据，不应靠旧 mixin 名字推断。
4. 对所有 module YAML 与 native module registration 做 reachability/format contract 对照，尤其 DLSS NGX runtime、FSR provider、XeSS runtime/device extensions、NRD generated shader headers；SVGF 保持“源在、注册禁用”的已知状态。
5. P-05/Early Window 生产交付、GUI/third-party compatibility、全 OS/GPU/SDK 组合以及所有 barrier/queue/history/material 数学不变量仍未动态证实。
6. CMake/CTest/Gradle 或真实客户端只能在主任务另行授权后运行；本审计没有 build/test/startup/GUI/visual/third-party acceptance evidence，不修改源码、配置、Git 元数据或 runtime。

### 逐项谱系校准

- compatibility 类/桥、`ResourceReloadCoordinator`、`RendererDiagnostics` 以及旧 NeoForge handoff 是 R-P 相对 R-U 的新增；current dirty 可能在其上继续扩展。
- native `priority/*`、CTest、target CMake/manifest 是 N-P 相对 N-U 的新增；Java name-tag/material 等也有 R-P 相对上游的主动重写。
- official Early Window bridge 全套、`io_github` middleware namespace、component namespace/staging hardening，以及部分后续 name-tag/resource/diagnostic refinements 属于后续 dirty/current 集成，不能反向归给 R-P/N-P。
- `restir-di.zip` 仅有 Java/legacy cleanup 名称，没有 N-U/N-P built-in source/config；`vanilla-pt` 与 `advanced` 才是 CMake 明确的 built-in pack 名称。
- 恢复点与阶段登记以 [主审计状态](audit-port-status.md) 为准；未证实处保持未知，动态构建/测试/启动不在本轮授权内。
