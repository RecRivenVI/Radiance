# NeoForge/Create/Flywheel/Ponder 兼容缺口（源码优先）

本报告是本轮只读 Explorer 的新台账，目标为 NeoForge 21.1.250、Minecraft 1.21.1、Create 6.0.10、Flywheel 1.0.6、Ponder 1.0.82+mc1.21.1。业务仓库 Radiance 和 MCVR 均未写入；未构建、未启动游戏 GUI、未操作 Prism、未清理、未执行 Git 变更。RadianceMCVRAudit/20260914 只作为入口背景，未继承其中的通过结论。

compat-loader-create.json 有 24 项 CG-Lxx。每项都绑定了参考源码、当前 checkout 符号和行号，并区分 GAP_CONFIRMED 与 GAP_INFERRED，动态状态统一为待定或目标对齐后延期。

## 两个语义样例

1. CG-L01：NeoForge 的 LevelRenderer.renderLevel 进入 Flywheel LevelRendererMixin，在光照更新后建立 RenderContextImpl，随后按 onStartLevelRender → afterEntities → beforeCrumbling 调用 VisualizationManager.RenderDispatcher（Flywheel LevelRendererMixin.java:51-60,74-92；API VisualizationManager.java:41-75）。当前 WorldRendererMixins.radiance$renderLevel 在头部取消原方法，改走 ChunkProxy/EntityProxy（WorldRendererMixins.java:139-149,244-306），没有 RenderContext 或 dispatcher 桥，因此不能把当前普通捕获说成 Flywheel 实例化已实现。

2. CG-L04：Flywheel 注册的 FlwProgramsReloader.onResourceManagerReload 会调用 FlwPrograms.reload(manager) 和 NoiseTextures.reload(manager)（FlwProgramsReloader.java:7-17），注册链来自 FlywheelNeoForge.registerBackendEventListeners（FlywheelNeoForge.java:102-108）。当前 FlywheelProgramsReloaderMixins 在方法头 ci.cancel()（FlywheelProgramsReloaderMixins.java:9-18），所以整个源码 listener 被跳过；Radiance 自己的 reload generation 不能替代这两个 Flywheel 消费者。

## 范围与组件关系

Ponder 1.0.82 的源码树同时含有 net/createmod/ponder 和 net/createmod/catnip。其 META-INF/neoforge.mods.toml:23-42 声明 NeoForge、Minecraft、Flywheel 依赖，没有独立 Catnip mod 条目；本报告把 Catnip 当作 Ponder 工件中的库/命名空间。Ponder 的世界钩子和 Catnip 缓冲区仍单独列为 CG-L12，没有虚构一个必需的 Catnip mod。

Create 客户端初始化会注册 Flywheel 实例类型、模型替换 listener、SuperByteBuffer compartments、PartialModels 和 Create Ponder plugin（CreateClient.java:74-115）。这证明初始化和注册入口可达，但不证明下游 Vulkan backend 可达。Create ClientContraption 的 VirtualRenderWorld.supportsVisualization() 与 ContraptionEntityRenderer 的分支（ClientContraption.java:55-63；ContraptionEntityRenderer.java:116-143）是实例化 backend 必须接住的真实入口。

## 当前可达范围与主要缺口

- 普通 vanilla/BER/SBB 捕获、部分区块 ModelData、四种 vanilla 粒子 sheet、FluidSpriteCache 与 IClientFluidTypeExtensions.getTintColor 已有源码入口；SectionBuilderMixins.java:102-124 保留了 ModelData 与 ClientHooks.addAdditionalGeometry，FluidRendererMixins.java:82-101 保留了自定义流体 tint/sprite 入口。它们的世界提交仍受 RenderCaptureContract 限制。
- Flywheel 默认实现不是 Vulkan：InstancedDrawManager 使用 VAO、TextureBuffer、GL program，IndirectDrawManager 使用 GL shader-storage barrier/compute/indirect draw，OIT 使用多附件 GL FBO。CG-L03、CG-L05、CG-L06、CG-L07、CG-L08、CG-L09 因此统一要求真实 backend 边界。
- 当前世界阶段并非事件未调用：Create/Ponder listener 会被 dispatch；问题是 BufferRendererMixins 对 WORLD_STAGE/DIMENSION_EFFECT 的 MeshData 路由为 reject 并关闭源 buffer（BufferRendererMixins.java:22-32；RenderCaptureContract.java:64-82）。这正是 CG-L11、CG-L12、CG-L13 的吞绘制/假成功来源。
- Catnip UIRenderHelper 的 FBO 使用真实 GL framebuffer、blit 和 stencil（UIRenderHelper.java:58-90,378-390）。当前兼容层对绑定、附件、存储和 blit 取消，却在生成和 status 查询处返回合成 ID/GL_FRAMEBUFFER_COMPLETE（OpenGlFramebufferCompatibilityMixins.java:21-27,29-108），见 CG-L17。此外 PonderUIRenderHelperMixins 直接取消唯一的 UIRenderHelper.init，使 BaseConfigScreen.prepareFrame/endFrame 的 framebuffer 为空，见 CG-L16。
- Create RenderTypes 的 glowing item layers 指向自定义 ShaderStateShard（RenderTypes.java:66-81,146-153）。当前 PBR 元数据按 layer 名称、透明度和贴图分类，没有保存 shader-state 身份（PBRVertexConsumer.java:124-140,152-189；Constants.java:145-193），所以 CG-L10 不是普通纹理缺口，而是自定义 shader/material 语义缺口。
- NeoForge 手部回调链没有被全部取消：当前 HeldItemRenderer 路径调用 ClientHooks.renderSpecificFirstPersonHand，再调用 patched renderArmWithItem，后者含 IClientItemExtensions.applyForgeHandTransform（HeldItemRendererMixins.java:71-85；patched ItemInHandRenderer.java:337-345,458-461）。CG-L20 只记录已核实的语义偏差：当前传 player.getXRot()，原版传 Mth.lerp(partialTick, xRotO, getXRot())。普通物品和弓姿态仍由 Minecraft 的 renderArmWithItem 分支负责；Plunger 特有坐标不在本项范围内。
- IClientItemExtensions.getCustomRenderer 的入口存在（patched ItemRenderer.java:123-159），但其直接 BufferUploader/自定义 state 可能运行在没有明确 camera/entity capture scope 的路径，见 CG-L21，该项标为推断并要求定向样例确认。
- CG-L22 是 caps 的保守边界：Create 会把重建的 BlockEntity 设到 VirtualRenderWorld（ClientContraption.java:145-165），而 VirtualRenderWorld.clear/setBlockEntities 只改 map（VirtualRenderWorld.java:136-152）。NeoForge 的 ILevelExtension 要求 capability 查询和失效语义由 Level/BlockEntity 生命周期承担（ILevelExtension.java:60-138）；本轮没有把外部 capability provider 的真实行为升级为已证实失败。

## 源码可实施的 Vulkan backend 边界

1. 在 Flywheel API 的 Backend.REGISTRY 注册一个明确的 Radiance backend。isSupported() 只在 native renderer、shader modules、descriptor/pipeline 和 target contract 全部 ready 时为 true；否则保留 OFF_BACKEND，让 Create 使用普通 BER/SBB。
2. 实现 Flywheel Engine 的 createVisualizationContext/createFramePlan/updateRenderOrigin/lightSections/render/renderCrumbling/delete，建立 RenderContext adapter，并在 Radiance 世界 pass 中按 onStartLevelRender → afterEntities → beforeCrumbling 调度。Create contraption 的 VisualEmbedding 映射到 native transform/instance storage。
3. 以 InstanceType/Layout/InstanceWriter 为 ABI，序列化 Create 的 rotating、scrolling、scrolling transformed、fluid 四类实例；为每个 instance/cull shader 建立 SPIR-V 模块和 descriptor layout。普通 VertexConsumer 捕获不能替代这一步。
4. 将 Flywheel 的五组 uniform blocks、material shader components、samplers 和 RenderType/Create custom shader key 映射到 Vulkan descriptor/push-constant 数据；不要继续依赖 GL uniform location 或 layer 名称推断。
5. 在 Engine 内实现 indirect compute/culling、depth pyramid、explicit barriers、indexed indirect draw 和 OIT 多附件/合成。Catnip/Ponder FBO 另走 Vulkan offscreen target API，target/stencil 不得用合成 ID 或伪完成状态。
6. 对 RenderLevelStageEvent、IDimensionSpecialEffectsExtension、Create/Ponder world buffers、custom particles 和 custom item renderer，统一使用带 world/camera identity、material、transform、target、close-on-throw 的 capture token。任何 unsupported 结果必须保留 fallback 或报告 loss，不能仅凭 callback 返回值称成功。

## 目标验收

默认只设两组验收：

- 基础组：Radiance + NeoForge 21.1.250，确认普通 vanilla 路径、GUI/UI route、ModelData/四种粒子路径和 unsupported FBO/世界 MeshData 的显式失败语义。
- 完整组：基础组 + Create 6.0.10 + Flywheel 1.0.6 + Ponder 1.0.82（Catnip 随 Ponder 工件），覆盖 Create kinetic/contraption/ValueBox、Flywheel dispatcher/实例/重载/OIT、Ponder GUI/world overlay、dimension effects、custom particle 和 item hand callback。

中间子集只用于定向归因，不作为通过结论。compat-loader-create-evidence.json 保存了先落盘的两个语义样例；compat-loader-create-ledger.json 保存完整行号台账。动态状态仍为 PENDING_RUNTIME、TARGETED_PENDING 或 DEFERRED_UNTIL_TARGET_ALIGNMENT，本轮没有运行时证据。
