# Radiance / MCVR 兼容性状态归并（r2）

- 修订：20260915-040929-r2
- 来源：D:/Workspaces/Artifacts/RadianceMCVRDecisionReview/20260915-030125-bbc60ca3/Reports/compatibility-gaps.json
- 父记录：52 条，归并为 18 个技术工作包；CG 映射 52 条且 52 个唯一。
- 范围：只读重分类与根因合并。本轮不实施方案，不构建、不启动游戏/GUI/Prism、不清理、不产生 Git 变动。
- 写入边界：本修订目录下的 compat-state-groups.json 与本文件；父 Reports/compatibility-gaps.json 保持不变。

## 四种状态

### 确认实现缺口

当前源码直接显示入口被取消、消费者没有等价 native 承接、真实资源/生命周期缺失或目标版本/状态契约被破坏；这表示需要实现，不把一次运行验证包装成完成。

### 已有承接但端到端/运行验证缺口

源码已有入口、转交或正向承接，但尚无完整组合下的最终 native draw、资源、顺序、重入或错误传播证据；默认只验证，缺证不改代码。

### 历史功能目标未实现（真正 Flywheel backend）

历史功能目标依赖 Flywheel 1.0.6 的真实 Backend/Engine/Instancer/InstanceType 生命周期，而当前没有可绑定的 Vulkan backend consumer；普通 BER/SBB、Flywheel off、捕获 MeshData 或 GL reload fallback 不满足目标。

### 待证风险

源码能指出作用域、上下文或生命周期风险，但证据不足以判定最终丢失或实现缺失；必须先做目标化证据核对，不能据此改全局代码。

## 归类裁决证据

### DE-01：world_dimension_meshdata_reject

裁决：确认实现缺口

- D:/Workspaces/Repositories/GitHub/RecRivenVI/Radiance/src/main/java/com/radiance/client/render/RenderCaptureContract.java:64-82 — WORLD_STAGE/DIMENSION_EFFECT MeshData 分类为拒绝/unsupported。
- D:/Workspaces/Repositories/GitHub/RecRivenVI/Radiance/src/main/java/com/radiance/mixins/vulkan_render_integration/BufferRendererMixins.java:22-32 — draw 路径关闭并报告 discard。
- Create ClientContraption/ContraptionEntityRenderer 与 Ponder world consumer 的上游调用在父报告相应 CG-L11/CG-L12 中保留。

解释：事件或 VertexConsumer 被调用不能证明绘制保留；该 reject 是真实静态丢绘制证据。

### DE-02：true_flywheel_backend_boundary

裁决：历史功能目标未实现（真正 Flywheel backend）

- D:/Workspaces/References/minecraft-references/flywheel-1.0.6-neoforge/src/dev/engine_room/flywheel/backend/Backend.java:7-30 — Backend 生命周期/注册契约。
- D:/Workspaces/References/minecraft-references/flywheel-1.0.6-neoforge/src/dev/engine_room/flywheel/visualization/VisualizationManager.java:41-75 — RenderDispatcher 生命周期消费者。
- D:/Workspaces/Repositories/GitHub/RecRivenVI/Radiance/src/main/java/com/radiance/compatibility/flywheel/FlywheelCompatibility.java:6-20 — 当前仅为兼容入口，没有 Engine/Backend/Instancer consumer。
- D:/Workspaces/References/minecraft-references/create-6.0.10/src/src/main/java/com/simibubi/create/foundation/render/AllInstanceTypes.java:22-151 — Create instance layout/writer 目标。

解释：当前 stonecutter tree 的少量 Flywheel 兼容文件和普通 BER/SBB fallback 不构成真实 backend；L01/L02/L03/L05/L06/L07/L08/L09/V12 是缺少历史目标实现，而非 9 个独立重写。

### DE-03：positive_loader_routes

裁决：已有承接但端到端/运行验证缺口

- D:/Workspaces/Repositories/GitHub/RecRivenVI/Radiance/src/main/java/com/radiance/mixins/vulkan_render_integration/SectionBuilderMixins.java:102-124 — ModelData 进入模型构建路径。
- D:/Workspaces/Repositories/GitHub/RecRivenVI/Radiance/src/main/java/com/radiance/mixins/vulkan_render_integration/FluidRendererMixins.java:82-101 — Fluid 渲染输入有承接。
- 父报告 CG-R01/CG-R02/CG-R03/CG-R04 与 CG-V03 记录了 Biome/Fog、ModList/icon、ordinary hand/callback 的正向入口；尚缺完整 downstream/runtime 证据。

解释：正向 ModelData、Fluid、Fog 路由不计为必须重写；入口存在不能代替最终资源、uniform、材质和屏幕验收。

### DE-04：logical_id_resource_boundary

裁决：按资源/生命周期逐项判断

- D:/Workspaces/Repositories/GitHub/RecRivenVI/Radiance/src/main/java/com/radiance/mixins/vulkan_render_integration/OpenGlFramebufferCompatibilityMixins.java:21-108 — FBO bind/blit/storage/attach/delete 被拒绝，同时返回 synthetic id/complete status。
- MCVR native resource 对应关系仅在具有 image/view/bind/release/generation 证据时视为真实逻辑 ID；本轮不把所有非 OpenGL 整数 ID 统称假 ID。

解释：问题是没有资源/生命周期却报告成功或丢绘制；有真实 Vulkan 映射、状态和释放的逻辑 ID 保持合法。

### DE-05：hand_and_ponder_scope

裁决：维持消费者边界

- D:/Workspaces/Repositories/GitHub/RecRivenVI/Radiance/src/main/java/com/radiance/mixins/vulkan_render_integration/HeldItemRendererMixins.java:71,82 — 先后调用 ClientHooks.renderSpecificFirstPersonHand，再进入 renderArmWithItem。
- patched Minecraft renderArmWithItem 内含 IClientItemExtensions.applyForgeHandTransform；因此普通物品/弓的 loader callback 不能泛称全部丢失。
- Ponder/Catnip 为嵌入式源码组件关系，Ponder neoforge.mods.toml 声明 NeoForge/Minecraft/Flywheel 依赖；不虚构独立 Catnip 必需 mod。

解释：CG-L20/L24 的具体姿态/particle contract 缺口、CG-V03 的已有 hand route、CG-V02 的 Plunger 坐标风险分开归类。

## 工作包

### WP-H01：Flywheel Engine/Backend、Create visual embedding 与 instance ABI

- 类型：历史功能目标未实现（真正 Flywheel backend）
- 旧 CG IDs：CG-L01、CG-L02、CG-L03、CG-L05、CG-L08、CG-L09、CG-V12
- 消费者关联：Flywheel RenderDispatcher、Backend.REGISTRY/BackendManager、Engine、ClientContraption/ContraptionEntityRenderer、Create AllInstanceTypes/InstanceWriter，以及 Flywheel lighting scene 共同缺少 MCVR 的真实 backend consumer。
- 外部功能语义：保留 Create 动态 contraption visual、rotating/scrolling/transformed/fluid instance 的批处理与更新、Flywheel frame lifecycle、shader/uniform/material variant、sky-light/scene lighting；visualization 开启时不能让 Create 跳过缓存结构后无后端承接。
- 可替换 GL 手段：直接实现 Flywheel 1.0.6 的 Backend/Engine/RenderDispatcher/Instancer/InstanceType 对 MCVR native 的适配：用现有 storage buffer、descriptor、pipeline、TLAS/场景资源承接 Layout/InstanceWriter 与矩阵光照数据，并按 generation 管理释放；不能用普通 BER/SBB、SBB fallback 或捕获到的 MeshData 冒充真正 instancing，也不套一层 GL wrapper。
- 静态问题或风险：参考 Flywheel 有 Backend、Engine、RenderDispatcher、Uniforms、GlProgram 和 instance layout；当前 Radiance 只见兼容入口/普通捕获，没有 Backend/Engine 注册、Flywheel InstanceType/InstanceWriter consumer、Flywheel uniform/material bridge 或 LightStorage bridge。故这是历史目标未实现，不是等待一次运行验证的已存在能力。
- 依赖前置：
  - MCVR native descriptor、storage buffer、shader module、scene/TLAS 与 generation/lifetime contract
  - Flywheel Backend.REGISTRY、BackendManager、Engine、InstancerProvider、InstanceType/Layout/InstanceWriter
  - Create ClientContraption、AllInstanceTypes 与 visual 生命周期
  - CG-L04 资源 generation reload 与 WP-H02 的可选间接绘制
- 仅需验证、无证据不改代码：
  - 本轮只核对 API 注册点、instance stride/field、uniform 与 lighting 消费者是否存在；不得因 fallback 可见就把该目标标为已实现。
  - 在真正 backend 设计前，可用静态日志或最小调用图确认 Create visualization 开启时是否跳过 SBB；这项确认不改业务代码。
- 状态依据：历史功能目标缺少当前 checkout 可绑定的真实 Flywheel Vulkan backend 证据；普通 BER/SBB/Flywheel-off 仅为回退。

### WP-H02：Flywheel IndirectDraw/DepthPyramid 与 OitFramebuffer 目标

- 类型：历史功能目标未实现（真正 Flywheel backend）
- 旧 CG IDs：CG-L06、CG-L07
- 消费者关联：Flywheel IndirectDrawManager 的 compute culling/DepthPyramid 与 OitFramebuffer 的多附件 composite 属于真正 backend 的扩展消费者，依赖 WP-H01 的 instance draw。
- 外部功能语义：保留大规模 instance 的可见性剔除、depth pyramid 依赖、间接命令提交，以及 translucent instance 的排序/合成语义；不能把“没有崩溃”或关闭 OIT 当成目标完成。
- 可替换 GL 手段：先用 MCVR 的 native indexed draw、CPU/常规 GPU culling 和已有 depth 数据为受支持材质提供可审计路径；只在 material 语义确实需要时设计 Vulkan indirect buffer/barrier 或合成 pass。OIT 可以按能力分级，不能默认一比一重造 GL OIT/FBO。
- 静态问题或风险：当前无 culling group、depth pyramid image、SSBO barrier、indirect command adapter、Flywheel OIT attachment/pass；OpenGL compatibility layer 的拒绝也不会产生这些资源。
- 依赖前置：
  - WP-H01 的真实 instance storage/draw contract
  - MCVR queue、barrier、depth image 与 native material capability
  - Create/Flywheel translucent material 的排序和 blend 需求
- 仅需验证、无证据不改代码：
  - 只需核对每种 Create/Flywheel translucent material 是否真的依赖 OIT，以及 CPU culling 是否足以覆盖目标场景；没有需求证据时不新增 OIT/FBO 代码。
- 状态依据：历史目标的实现面在源码中缺失；本轮不把 fallback 或未来可选路径计为 Flywheel indirect/OIT 已实现。

### WP-C01：Flywheel shader/noise 资源 reload 交接

- 类型：确认实现缺口
- 旧 CG IDs：CG-L04
- 消费者关联：FlwProgramsReloader.onResourceManagerReload -> FlwPrograms.reload/NoiseTextures.reload；当前 FlywheelProgramsReloaderMixins 在入口取消。
- 外部功能语义：资源包或手动 reload 后，Flywheel shader source、noise texture、program generation 必须与新 Minecraft generation 一起更新，旧资源必须可安全失效。
- 可替换 GL 手段：让 ResourceReloadCoordinator 以 listener/adapter 方式驱动 Flywheel shader/material source 到 MCVR ShaderRegistry、SPIR-V/pipeline generation，并在 swap 前后保持引用计数；不恢复 GL program reload。
- 静态问题或风险：源码链明确存在 reload consumer，当前 Mixin 在 HEAD cancel，且没有等价 Vulkan Flywheel listener；Minecraft reload 成功会与 Flywheel 资源仍为空/过期脱钩。
- 依赖前置：
  - WP-H01 的 shader/material consumer
  - Radiance ResourceReloadCoordinator、ShaderRegistry 与 generation swap
  - Flywheel FlwPrograms、NoiseTextures 的异步 reload contract
- 仅需验证、无证据不改代码：
  - 先静态确认 listener 是否只取消 Flywheel 路径、Radiance 自身 generation 是否已完成；这项验证不改变 Mixin。
  - 如果完整组合中没有 Flywheel visualization，仅记录该 consumer 未启用，不把它包装成修复。
- 状态依据：确认入口被取消且无替换 consumer；属于实现缺口。

### WP-C02：Create/Ponder 世界 MeshData 进入 native world sink

- 类型：确认实现缺口
- 旧 CG IDs：CG-L11、CG-L12
- 消费者关联：Create ContraptionEntityRenderer 与 Ponder 世界/场景 overlay 经过 VertexConsumer/BufferUploader；当前 RenderCaptureContract 对 WORLD_STAGE/DIMENSION_EFFECT MeshData 拒绝并由 BufferRendererMixins 关闭/discard。
- 外部功能语义：保留 contraption 结构、移动方块实体、Ponder 世界说明层/场景 overlay 的位置、材质、透明度和绘制顺序，使其进入世界渲染目标；不得以事件被调用掩盖绘制被吞。
- 可替换 GL 手段：建立带 world、坐标、material、camera stage 与生命周期 token 的 native world submission sink，复用 EntityProxy/已有 native geometry queue；对不支持的材质明确降级或报告，不为每个 Create/Ponder 组件分别重写 GL/FBO。
- 静态问题或风险：这是有直接源码证据的真实丢绘制：RenderCaptureContract 的 WORLD_STAGE/DIMENSION_EFFECT 分支拒绝 MeshData，BufferRendererMixins 随后关闭/报告 discard；不能归入仅待运行验证。
- 依赖前置：
  - MCVR world geometry buffer 与 camera/stage contract
  - Radiance RenderCaptureContract、BufferRendererMixins、EntityProxy
  - Create ClientContraption/ContraptionEntityRenderer 与 Ponder WorldRenderUtils
  - WP-C03 的 stage ordering/output contract
- 仅需验证、无证据不改代码：
  - 只验证 Create/Ponder consumer 的每个 MeshData 是否走到该拒绝分支及其 discard 计数；已有证据足够确认缺口，本轮不改业务代码。
- 状态依据：保留 world/dimension MeshData reject 的文件/符号/行证据，归为确认实现缺口。

### WP-C03：NeoForge dimension/stage/OutputStateShard 与目标版本契约

- 类型：确认实现缺口
- 旧 CG IDs：CG-L13、CG-L14、CG-L15、CG-L23
- 消费者关联：LevelRenderer 的 dimension effects 回调、RenderLevelStageEvent 顺序、RenderType.OutputStateShard，以及 NeoForge loader target/version 共同决定扩展渲染何时、以何状态进入 native pass。
- 外部功能语义：保留 renderSky/renderClouds/renderSnowAndRain 等维度回调的 boolean/状态语义、stage 的顺序与 camera/pose 上下文、RenderType output state 的目标绑定，并把目标 loader 对齐 NeoForge 21.1.250。
- 可替换 GL 手段：用 native pass scheduler、明确的 stage key、color/depth attachment 与 state/material key 映射承接回调；OutputStateShard 只映射实际状态，不以全局 no-op 隐藏需求，也不把版本漂移留给运行时猜测。
- 静态问题或风险：Dimension custom callback 存在 false-success/吞绘制风险；stage context/ordering 未被完整适配；OutputStateShard 在当前兼容路径被 no-op；active gradle.properties 为 NeoForge 21.1.248，与本审计目标 21.1.250 不一致。
- 依赖前置：
  - NeoForge 21.1.250 与 patched Minecraft 1.21.1 的 API/映射基线
  - WP-C02 的 world sink
  - MCVR native pass/attachment state
  - Radiance RenderType/ShaderRegistry metadata
- 仅需验证、无证据不改代码：
  - 先用静态入口链核对每个 dimension callback 的返回值、stage 顺序和 OutputStateShard 需求；未见对应 consumer 时不得称端到端可用。
  - 版本差异只需记录为 target gate；本轮不修改 Gradle 或业务源码。
- 状态依据：至少 callback false-success、stage/state no-op 与 target mismatch 已有静态证据，合并为同一 loader/pass 契约工作包。

### WP-C04：Catnip UI、Veil 后处理与跨模组离屏目标

- 类型：确认实现缺口
- 旧 CG IDs：CG-L16、CG-L17、CG-V04、CG-V05、CG-V15、CG-V21
- 消费者关联：Catnip UIRenderHelper/Ponder stencil 与 transition、Veil framebuffer/post pipeline、Aeronautics soft-light layer、Simulated EndSea shadow/AFTER_LEVEL 共享 render target、attachment、stage 与生命周期边界。
- 外部功能语义：保留 Ponder UI 的 stencil/transition、Veil 后处理与 FBO consumer、Aeronautics 热气球 soft-light、EndSea shadow/AFTER_LEVEL 的可见效果及尺寸/re-entry/close 语义；不把所有离屏效果强行等同于一个 OIT 方案。
- 可替换 GL 手段：提供可复用的 Vulkan target abstraction，优先复用现有 native world color/depth、subpass/resolve 与 clip/mask；按效果选择直接 forward、mask、copy/resolve 或受支持的 post pass，只有证明需要独立 attachment 时才创建资源。Catnip/Ponder 不能靠取消 init 继续运行，Veil unsupported 时必须显式降级。
- 静态问题或风险：当前 OpenGlFramebufferCompatibilityMixins 会拒绝 bind/blit/storage/attach/delete，却返回 synthetic FBO id/GL_FRAMEBUFFER_COMPLETE；PonderUIRenderHelperMixins 取消 UIRenderHelper.init，ConfigScreen 仍无条件使用 framebuffer。Veil/Simulated/Aeronautics 的离屏/target consumer 没有相等 native contract。
- 依赖前置：
  - MCVR image/view/target、resize、resolve、clip/mask 和 destruction lifecycle
  - Ponder/Catnip UIRenderHelper 与 Ponder screen
  - Veil renderer/FBO/post stage、Aeronautics custom layer、Simulated EndSea stage
  - WP-C03 的 stage/output 与 WP-C05 的 close/exception cleanup
- 仅需验证、无证据不改代码：
  - 只需逐项确认实际 effect 是否请求独立 color/depth/stencil/resolve 与何种生命周期；没有 attachment 需求证据时不新增一比一 FBO/OIT。
  - 对 FBO 逻辑 ID 只检查是否有对应 native image/view、绑定和释放；只有无资源却回报成功的路径才记为 synthetic/false-success，其他逻辑 ID 不泛称假 ID。
- 状态依据：Catnip/Veil/跨模组目标的共同静态缺口是 target contract 被取消或拒绝；Ponder 中 Catnip 为嵌入式组件，不能虚构独立必需 mod。

### WP-C05：捕获 scope、ValueBox 与 Screen 异常清理

- 类型：确认实现缺口
- 旧 CG IDs：CG-L18、CG-L19
- 消费者关联：ValueBox/GUI/Screen 捕获 scope 在 RETURN/cancel 分支清理，和 MeshData/Framebuffer discard 形成生命周期交叉。
- 外部功能语义：异常、早退、关闭和 world re-entry 后必须释放 capture token、临时状态和 native references，同时保留原异常/取消语义，避免下一帧继承脏 scope。
- 可替换 GL 手段：使用 Java try/finally 或 token guard 与 MCVR generation/lifetime API；清理由 scope owner 统一完成，不依赖 GL delete，也不把异常吞成成功。
- 静态问题或风险：当前 ValueBox 与 GUI/Screen 相关 cleanup 只在特定 RETURN/cancel 分支执行，异常路径可能跳过；该问题与具体 GL 对象无关，是跨入口生命周期缺口。
- 依赖前置：
  - RenderCaptureContract scope/token
  - MCVR native resource generation/close
  - Screen/ValueBox reentry 与 render-thread exception policy
- 仅需验证、无证据不改代码：
  - 静态列出所有早退、异常、close、reload、world unload 路径，并以现有日志确认是否重复使用 token；未发现新证据前不扩写清理代码。
- 状态依据：已见清理路径不覆盖所有退出边界，归为确认实现缺口。

### WP-C06：NeoForge 手部姿态与 ParticleRenderType consumer 契约

- 类型：确认实现缺口
- 旧 CG IDs：CG-L20、CG-L24
- 消费者关联：HeldItemRenderer 的 pitch/hand 入口与 ParticleRenderType begin/end、状态、vertex format、绘制顺序是两个 loader/vanilla consumer 契约；不能把它们合并成“所有手部回调丢失”。
- 外部功能语义：普通物品/弓姿态仍沿 Minecraft patched 实现；NeoForge IClientItemExtensions.applyForgeHandTransform 与 ClientHooks.renderSpecificFirstPersonHand 的选择、取消和变换必须保留；自定义粒子必须执行对应 begin/state/format/end 与 sheet ordering。
- 可替换 GL 手段：保留原版/NeoForge hand callback 入口，只在 capture boundary 记录 camera/pose/material 到 native queue；粒子按 RenderType capability 选择 native begin/end/state 或明确普通 sheet fallback，不做全局 pose 重写。
- 静态问题或风险：HeldItemRendererMixins 已分别调用 ClientHooks.renderSpecificFirstPersonHand，随后进入 patched renderArmWithItem（内含 IClientItemExtensions.applyForgeHandTransform），因此回调并非全部丢失；可确认的缺口是 pitch interpolation 与非四种 vanilla sheet 的 begin/state/ordering 被跳过。
- 依赖前置：
  - Minecraft 1.21.1 patched HeldItemRenderer/ItemRenderer
  - NeoForge 21.1.250 ClientHooks 与 IClientItemExtensions
  - ParticleRenderType begin/end、vertex format 与 MCVR native material
  - EntityProxy HAND capture scope
- 仅需验证、无证据不改代码：
  - 普通物品和弓姿态先只做 vanilla 对照，确认 callback 选择与 applyForgeHandTransform；无证据时不改全局 hand path。
  - 对自定义粒子按 type 逐项记录 begin/state/ordering 缺口；仅有普通 sheet 证据时不扩展为所有粒子重写。
- 状态依据：区分原版姿态、NeoForge 手部回调和 Plunger 特有坐标；Plunger CG-V02 单独留在风险工作包。

### WP-C07：Create GLOWING_SHADER 的 RenderType material identity

- 类型：确认实现缺口
- 旧 CG IDs：CG-L10
- 消费者关联：Create RenderTypes.GLOWING_SHADER/partial item model -> RenderStateShard ShaderStateShard -> Radiance PBRVertexConsumer/Constants material classification。
- 外部功能语义：Create glowing item/partial model 必须保留自定义 shader identity、alpha/blend、纹理与输出 material；名称或 transparency 相同的普通 layer 不能替代 glowing shader。
- 可替换 GL 手段：从 RenderType CompositeState 提取稳定 shader/material key，交给 ShaderRegistry/MCVR SPIR-V pipeline 与 captured metadata；不通过 layer name 猜 geometry type，也不恢复 GL shader object。
- 静态问题或风险：当前 capture 保留顶点/贴图/alpha，但没有把 RenderType ShaderStateShard identity 绑定到 captured geometry；因此可能以普通 material 成功提交却失去 glowing 外观。
- 依赖前置：
  - Create RenderTypes、RegisterShadersEvent 与 PartialItemModelRenderer
  - Radiance PBRVertexConsumer/EntityProxy metadata
  - ShaderRegistry/ShaderTranslator 与 MCVR material pipeline
- 仅需验证、无证据不改代码：
  - 先静态比较 Create glowing RenderType 的 shader key、alpha mode、texture 与当前 capture metadata；未证明 downstream 丢失前不改普通材质分类。
- 状态依据：顶点捕获存在但 material identity consumer 缺失，属于确认实现缺口。

### WP-C08：Veil 初始化与 Simulated stage/fixed-buffer 入口

- 类型：确认实现缺口
- 旧 CG IDs：CG-V01、CG-V16
- 消费者关联：Veil client renderer/init consumer 与 Simulated client stage/fixed buffer bootstrap 都需要初始化、注册和 stage callback 被实际调用。
- 外部功能语义：保留 Veil renderer 初始化、Simulated diagram/fixed-buffer bootstrap、stage callback 的顺序和失败处理；取消入口不能被记录成初始化成功。
- 可替换 GL 手段：用显式 native feature gate、stage adapter、固定 buffer/descriptor 初始化与 reduced fallback 承接；只取消不支持的 GL consumer，并向上报告 unsupported，不吞回调。
- 静态问题或风险：当前存在 Veil init/renderer consumer 冲突和 Simulated stage/fixed buffer 路径被取消/缺少 adapter 的静态缺口；尚无端到端 native lifecycle。
- 依赖前置：
  - MCVR device/queue/context ready gate
  - Veil renderer lifecycle 与 Simulated client stage/fixed buffer API
  - WP-C04 target/attachment、WP-C05 close/re-entry
- 仅需验证、无证据不改代码：
  - 先确认每个 init/stage consumer 的调用次数、线程、顺序和失败回传；若只是未启用模组，不为其新增初始化实现。
- 状态依据：入口取消/无 native adapter 是具体实现缺口，和 V03/V18 等已存在承接区分。

### WP-C09：Sable culling dispatch 到 native visibility queue

- 类型：确认实现缺口
- 旧 CG IDs：CG-V08
- 消费者关联：Sable client culling/section visibility dispatch -> Radiance/Sable bridge -> MCVR visible geometry submission。
- 外部功能语义：保留 Sable 子世界的 section/entity 可见性剔除、摄像机相关结果和后续 geometry dispatch；空 dispatch 不能被当成完成。
- 可替换 GL 手段：复用已有 ChunkProxy/EntityProxy 的 native visible queue，或先提供明确的 CPU visibility path；只在有 depth/occlusion 证据时新增 GPU culling，避免先造 GL 等价层。
- 静态问题或风险：当前 Sable culling dispatch 为空/没有把可见结果交给 native consumer；因此即便 chunk/mesh 数据被捕获，剔除后仍可能没有绘制。
- 依赖前置：
  - Sable sub-level visibility/section API
  - Radiance ChunkProxy/EntityProxy 与 MCVR world queue
  - WP-H01 backend 或其普通 geometry fallback
- 仅需验证、无证据不改代码：
  - 先记录 section count、visible set 和 dispatch consumer 是否为零；没有运行或日志证据时不把“捕获有数据”当成 culling 修复。
- 状态依据：空 dispatch 是当前实现缺口，独立于 WP-V01 已有几何入口。

### WP-V01：Sable chunk/mesh/entity/block entity/outline 的端到端对照

- 类型：已有承接但端到端/运行验证缺口
- 旧 CG IDs：CG-V09、CG-V10、CG-V11、CG-V13
- 消费者关联：Sable chunk/mesh、entity、block entity 与 outline/debug route 已有 bridge/capture 入口，待核对最终 native draw、变换和生命周期。
- 外部功能语义：保留 Sable 子世界的地形几何、实体/方块实体姿态、outline/debug line 和 compiled/global set 更新；数据进入 bridge 不等于屏幕已正确绘制。
- 可替换 GL 手段：优先复用现有 native chunk/entity queue、material metadata 与 visibility result；只有对照证据指出缺项时补窄适配，不重写完整 GL renderer。
- 静态问题或风险：源码可见入口与 transfer，但 parent report 没有完整 frame/屏幕/重入证据；风险在重复提交、transform、compiled/global 集合和 debug target。
- 依赖前置：
  - WP-C09 culling dispatch
  - MCVR native geometry/material/outline target
  - Sable world re-entry 与 chunk compile lifecycle
- 仅需验证、无证据不改代码：
  - 该工作包默认只验证：每类 consumer 的输入 count、native submission、draw count、camera transform、重复帧和 unload/re-entry；没有证据不得改代码。
- 状态依据：已有承接路径，缺的是端到端/运行验证，不归为确认无实现。

### WP-V02：NeoForge ModelData/Fluid/Biome/Fog、模组列表图标与普通手入口

- 类型：已有承接但端到端/运行验证缺口
- 旧 CG IDs：CG-R01、CG-R02、CG-R03、CG-R04、CG-V03
- 消费者关联：SectionBuilder ModelData、FluidRenderer、Biome/dimension fog、ModList/icon 以及 ordinary first-person hand callback 均有正向入口或承接；它们共享“上游输入已保留，需验证 downstream native consumer”的边界。
- 外部功能语义：保留 ModelData 驱动模型选择、Fluid 的流体面/材质、Biome/dimension fog 颜色与密度、模组列表/图标展示，以及普通物品/弓的 Minecraft 姿态和 NeoForge hand callback 选择。
- 可替换 GL 手段：继续使用已有 ModelData/Fluid/Fog/UI/hand native transfer；仅补真实 resource/material mapping、uniform 或 UI surface 的窄适配。正向 ModelData/Fluid/Fog 不计为必须重写，也不默认替换成一比一 GL/FBO。
- 静态问题或风险：父报告已有 SectionBuilderMixins、FluidRendererMixins、fog/UI/hand 入口证据；尚缺完整组合下的最终 resource ID、材质、uniform、callback return/pose 与屏幕结果。不能用“入口存在”笼统宣称全链完成。
- 依赖前置：
  - NeoForge 21.1.250 patched Minecraft 1.21.1
  - MCVR texture/resource/material/uniform consumer
  - WP-C03 的 stage/state，WP-C06 的手部契约
- 仅需验证、无证据不改代码：
  - 默认只验证 ModelData/Fluid/Fog 的输入值到 native draw/uniform、mod list/icon 的 screen 结果、ordinary hand callback/pose；无 downstream 丢失证据不改代码。
  - 如验证失败，按具体 consumer 定向归因，不把四个正向能力合并判成一个重写项目。
- 状态依据：已有承接但无完整运行证明；明确排除“正向 ModelData/Fluid/Fog 必须重写”。

### WP-V03：Simulated diagram 世界几何、双坐标/液体实体与 UI 生命周期

- 类型：已有承接但端到端/运行验证缺口
- 旧 CG IDs：CG-V18、CG-V19、CG-V20
- 消费者关联：Simulated diagram 的 Vulkan replacement、world geometry transfer 与 diagram UI/resize/early/null/re-entry 已有承接入口。
- 外部功能语义：保留 diagram 世界几何、fluid/block entity/entity、坐标转换、UI 打开/关闭/resize/early-null 与重新进入世界的行为。
- 可替换 GL 手段：复用 MCVR diagram native replacement、world queue、surface/target 生命周期；若坐标问题只在一层出现，修正该转换边界，不按每个 UI/FBO 重新实现 GL。
- 静态问题或风险：静态链显示 replacement/bridge 存在，但没有完整动态证据证明 double coordinate、fluid/BE/entity、UI null/resize/re-entry 在同一 generation 内一致。
- 依赖前置：
  - MCVR diagram/world native path
  - Simulated diagram renderer/UI
  - WP-C04 target lifecycle、WP-C05 re-entry cleanup
- 仅需验证、无证据不改代码：
  - 只做基础与完整组定向对照：坐标一次应用、fluid/BE/entity count、UI early/null/resize/close/re-entry；无证据不改现有 replacement。
- 状态依据：静态已有承接，问题等级是端到端/运行验证不足。

### WP-V04：Offroad crumbling 共享进度与 MCVR VkResult/生命周期

- 类型：已有承接但端到端/运行验证缺口
- 旧 CG IDs：CG-V22、CG-V23
- 消费者关联：Offroad shared destructionProgress/last-stage route 与 MCVR Vulkan VkResult、queue idle、generation/lifetime protection 均已有代码承接，需要跨帧/跨关闭验证。
- 外部功能语义：保留多个方块破坏阶段/进度的共享显示、错误结果向 Java/调用方传播、资源 generation 与 close/reload 的安全顺序；错误不能被吞成成功。
- 可替换 GL 手段：沿用现有 shared progress adapter 和 MCVR formal error/lifetime API；只对具体错误码、队列/资源关联缺口补窄修正，不以 GL fallback 重做破坏效果或错误路径。
- 静态问题或风险：源码说明 progress 与 formal VkResult/lifetime route 已存在，但还没有 full combination 的多进度、close/reload、实际错误 correlation 证据。
- 依赖前置：
  - Offroad shared destructionProgress consumer
  - MCVR device.cpp VkResult/queue idle/generation contract
  - WP-C05 cleanup 与 WP-R03 diagnostics correlation
- 仅需验证、无证据不改代码：
  - 默认只验证多 progress 同帧、错误码 correlation、queue idle、world unload/reload/close；如果正式错误传播已覆盖，不改为新增日志或 fallback。
- 状态依据：已有承接，需端到端/运行验证；不得与待清理探针混为实现。

### WP-R01：自定义 renderer、capability、shader/初始化与自定义 layer 的待证风险

- 类型：待证风险
- 旧 CG IDs：CG-L21、CG-L22、CG-V06、CG-V07、CG-V14、CG-V17
- 消费者关联：NeoForge custom item renderer、Create VirtualRenderWorld capability、Veil shader/camera UBO、Sable init/renderer variant、Aeronautics custom chunk layer、Simulated custom RenderType/texture/Spring vertex 是不同消费者；当前统一纳入证据门禁，不宣称共享实现已缺失或已完成。
- 外部功能语义：分别保留 custom item/armor renderer direct draw、virtual-level read capability/invalidation、Veil shader/camera UBO、Sable variant lifecycle、Aeronautics layer shader/state、Simulated custom vertex/texture 的原始语义。
- 可替换 GL 手段：先以每个 consumer 的 capture/native sink、descriptor/material key、capability policy 或明确 unsupported fallback 做定向适配；不因风险猜测而全局重写 renderer、capability 或 GL/FBO。
- 静态问题或风险：这些记录的静态链能指出作用域/映射风险，但没有足以确认最终丢失的共同证据；其中 capability/坐标/variant 依赖运行上下文，不能用“有 bridge”或“被调用”笼统结论。
- 依赖前置：
  - 对应模组的最小可复现实例和 runtime context
  - MCVR resource/material/descriptor 与 capture scope
  - NeoForge capability/hand contract、Create VirtualRenderWorld、Veil/Sable/Simulated/Aeronautics API
- 仅需验证、无证据不改代码：
  - 全部 ID 默认只验证：调用 context、资源/descriptor 是否真实存在、生命周期、unsupported return 与最终 draw；无直接证据不得改业务代码。
  - CG-L21 要区分 custom renderer direct draw 与 ordinary BER；CG-L22 只允许确认安全的 read capability，不伪造 server capability。
  - CG-V06/V07/V14/V17 分别按 shader/UBO、init variant、custom layer state、texture/vertex 证据归因，不合并成一个已确认缺口。
- 状态依据：待证风险；本轮只建立验证边界，另 Explorer/Plunger 的几何任务不在此工作包内。

### WP-R02：Plunger 第一人称 focus 坐标作用域隔离

- 类型：待证风险
- 旧 CG IDs：CG-V02
- 消费者关联：Simulated PlungerLauncherItemRenderer/LaunchedPlungerEntityRenderer 的 camera-local focusPos、itemProjMat、绳索/粒子端点；与普通 HeldItemRenderer/NeoForge hand callback 是独立入口。
- 外部功能语义：保留 Plunger 第一人称 focus point、item projection、发射粒子/绳索端点和 Sable 子层姿态；不得把该坐标补丁套到普通物品或弓。
- 可替换 GL 手段：保留窄作用域 Quaternionf.transformInverse redirect，或仅在 Plunger consumer 建立明确 camera-local 数据转换；不新增全局手部姿态或 FBO。
- 静态问题或风险：当前只见 Plunger consumer 的静态 redirect，是否已 camera-local 需按 item matrix、owner view、第三人称和 Sable sublayer 验证；不能从普通 hand callback 推断。
- 依赖前置：
  - Simulated Plunger renderer 与 focus/projection API
  - Sable pose conversion
  - Radiance HeldItemRenderer/EntityProxy 的普通 hand boundary
- 仅需验证、无证据不改代码：
  - 该 ID 由另一 Agent 负责几何精准对照；本工作包只保留作用域和验收接口，不在本轮修改普通手部或 Plunger 代码。
- 状态依据：待证风险，且明确隔离另一 Agent 的 Plunger 几何任务。

### WP-R03：临时日志/计数/探针的证据闭环与清理门禁

- 类型：待证风险
- 旧 CG IDs：CG-V24
- 消费者关联：SableSubLevelDiagnostics、SableSubLevelBridge 计数/reflection warning、RenderCaptureContract discard report 与 MCVR device diagnostics 开关是观察性输出，不是渲染 backend。
- 外部功能语义：保留正式 VkResult、资源生命周期和 unsupported 状态传播；临时观察输出只服务于关联错误和验证，不能改变绘制或伪造成功。
- 可替换 GL 手段：不以探针替换任何 GL/Vulkan resource；先使用正式状态/错误返回与最小 correlation，再在确认完成后移除本地临时 logs/counters/probes。
- 静态问题或风险：源码已表明探针不会创建 geometry、descriptor、TLAS 或修复 VkResult；“有回调/有计数”不能证明已绘制，且可能制造噪声和错误归因。
- 依赖前置：
  - WP-V04 的正式 VkResult/lifetime route
  - RenderCaptureContract unsupported discard
  - Sable bridge 与外部 runtime log correlation
- 仅需验证、无证据不改代码：
  - 只验证每项探针是否无业务产物、是否与正式错误码一一对应；在 correlation 未完成前不清理，在没有新增证据时不新增业务逻辑。
  - 完成证据闭环后，清理只针对临时日志/计数/探针，不删除 CG-V23 的正式错误传播。
- 状态依据：待证风险/观察性工具；本轮不把日志存在称为能力实现。

## CG 映射完整性

| CG ID | 类型 | 工作包 |
| --- | --- | --- |
| CG-L01 | 历史功能目标未实现（真正 Flywheel backend） | WP-H01 |
| CG-L02 | 历史功能目标未实现（真正 Flywheel backend） | WP-H01 |
| CG-L03 | 历史功能目标未实现（真正 Flywheel backend） | WP-H01 |
| CG-L05 | 历史功能目标未实现（真正 Flywheel backend） | WP-H01 |
| CG-L08 | 历史功能目标未实现（真正 Flywheel backend） | WP-H01 |
| CG-L09 | 历史功能目标未实现（真正 Flywheel backend） | WP-H01 |
| CG-V12 | 历史功能目标未实现（真正 Flywheel backend） | WP-H01 |
| CG-L06 | 历史功能目标未实现（真正 Flywheel backend） | WP-H02 |
| CG-L07 | 历史功能目标未实现（真正 Flywheel backend） | WP-H02 |
| CG-L04 | 确认实现缺口 | WP-C01 |
| CG-L11 | 确认实现缺口 | WP-C02 |
| CG-L12 | 确认实现缺口 | WP-C02 |
| CG-L13 | 确认实现缺口 | WP-C03 |
| CG-L14 | 确认实现缺口 | WP-C03 |
| CG-L15 | 确认实现缺口 | WP-C03 |
| CG-L23 | 确认实现缺口 | WP-C03 |
| CG-L16 | 确认实现缺口 | WP-C04 |
| CG-L17 | 确认实现缺口 | WP-C04 |
| CG-V04 | 确认实现缺口 | WP-C04 |
| CG-V05 | 确认实现缺口 | WP-C04 |
| CG-V15 | 确认实现缺口 | WP-C04 |
| CG-V21 | 确认实现缺口 | WP-C04 |
| CG-L18 | 确认实现缺口 | WP-C05 |
| CG-L19 | 确认实现缺口 | WP-C05 |
| CG-L20 | 确认实现缺口 | WP-C06 |
| CG-L24 | 确认实现缺口 | WP-C06 |
| CG-L10 | 确认实现缺口 | WP-C07 |
| CG-V01 | 确认实现缺口 | WP-C08 |
| CG-V16 | 确认实现缺口 | WP-C08 |
| CG-V08 | 确认实现缺口 | WP-C09 |
| CG-V09 | 已有承接但端到端/运行验证缺口 | WP-V01 |
| CG-V10 | 已有承接但端到端/运行验证缺口 | WP-V01 |
| CG-V11 | 已有承接但端到端/运行验证缺口 | WP-V01 |
| CG-V13 | 已有承接但端到端/运行验证缺口 | WP-V01 |
| CG-R01 | 已有承接但端到端/运行验证缺口 | WP-V02 |
| CG-R02 | 已有承接但端到端/运行验证缺口 | WP-V02 |
| CG-R03 | 已有承接但端到端/运行验证缺口 | WP-V02 |
| CG-R04 | 已有承接但端到端/运行验证缺口 | WP-V02 |
| CG-V03 | 已有承接但端到端/运行验证缺口 | WP-V02 |
| CG-V18 | 已有承接但端到端/运行验证缺口 | WP-V03 |
| CG-V19 | 已有承接但端到端/运行验证缺口 | WP-V03 |
| CG-V20 | 已有承接但端到端/运行验证缺口 | WP-V03 |
| CG-V22 | 已有承接但端到端/运行验证缺口 | WP-V04 |
| CG-V23 | 已有承接但端到端/运行验证缺口 | WP-V04 |
| CG-L21 | 待证风险 | WP-R01 |
| CG-L22 | 待证风险 | WP-R01 |
| CG-V06 | 待证风险 | WP-R01 |
| CG-V07 | 待证风险 | WP-R01 |
| CG-V14 | 待证风险 | WP-R01 |
| CG-V17 | 待证风险 | WP-R01 |
| CG-V02 | 待证风险 | WP-R02 |
| CG-V24 | 待证风险 | WP-R03 |

类型计数：确认实现缺口 21；已有承接但端到端/运行验证缺口 14；历史功能目标未实现（真正 Flywheel backend） 9；待证风险 8。

## 验收边界

- 基础组：基础组：Radiance + NeoForge 21.1.250，验证 vanilla/ordinary hand、loader callback、基础 world capture、资源/错误生命周期；每项只报告实际入口和 native consumer 证据。
- 完整组：完整组：Radiance + NeoForge 21.1.250 + Create 6.0.10 + Flywheel 1.0.6 + Ponder 1.0.82（Catnip 按 Ponder 嵌入组件处理）及父报告列出的完整组合；逐工作包验证目标功能语义、资源/状态/顺序、world/dimension/UI/reload/re-entry/close 与错误传播。完整组是整个组合，不把 Create/Ponder/Veil 等中间子集当单独通过关卡。
- 中间子集：中间子集只作定向归因：用于确认哪个 consumer/阶段/资源边界失败，不构成验收关卡，也不能替代完整组合。
- 本轮门禁：本轮只输出分类和证据边界；任何 proposal 均为源码可实施方向，未实施、未构建、未运行。

## 两条入口→消费者→缺口样例

### SAMPLE-01：World MeshData 被拒绝的入口→消费者→缺口

- 链：Create/Ponder world consumer -> VertexConsumer/BufferUploader -> BufferRendererMixins -> RenderCaptureContract.classify -> WORLD_STAGE/DIMENSION_EFFECT reject -> close/discard -> native world sink 缺失。
- 工作包：WP-C02
- 类型：确认实现缺口

### SAMPLE-02：Flywheel 真 backend 未实现的入口→消费者→缺口

- 链：Create visualization/AllInstanceTypes -> Flywheel BackendManager/Engine/Instancer/Uniforms -> 当前 Radiance FlywheelCompatibility 入口 -> 没有 Backend/Engine/InstanceType/InstanceWriter/lighting 的 MCVR consumer -> 只能 Flywheel off 或普通 fallback。
- 工作包：WP-H01
- 类型：历史功能目标未实现（真正 Flywheel backend）

## 关键说明

- 52 条父记录按共享根因归为 18 个技术工作包；一个工作包可关联多个模组消费者，但保留每个外部功能目标。
- World/dimension MeshData reject 保留真实源码证据；ModelData/Fluid/Fog 的正向承接归入验证工作包，不算必须重写。
- Flywheel true backend 的边界是 Backend/Engine/Instancer/InstanceType/InstanceWriter/资源生命周期与 MCVR native consumer；普通 BER、SBB、Flywheel off、GL reload cancel 或逻辑 capture 不满足。
- 当前只对类型争议点复核源码；不扩展为全仓调查。

