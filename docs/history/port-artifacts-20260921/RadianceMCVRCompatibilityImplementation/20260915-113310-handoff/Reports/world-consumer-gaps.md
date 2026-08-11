# World consumer gaps：Sable / Aeronautics Levitite / EndSea shadow

日期：2026-09-15  
调查方式：固定版本源码与调用链静态核对；未构建、未运行测试、未启动客户端、未使用 UI、未修改源码。  
范围：Radiance 与 MCVR 的实际可达世界消费者。普通 Sable 区块继续走现有 PT native instance/TLAS；本报告只列 PT 无法表达的额外 raster 层和 EndSea shadow group。Veil reload、Simulated diagram、Flywheel GL 后端架构不在本报告内。

## 结论

| 优先级 | 结论 | 状态 |
|---|---|---|
| P0 | EndSea final draw 已有 after-fuse defer；原 AFTER_LEVEL → Veil listener 回调仍存在，但当前 Radiance 没有把 shadow producer 与 final draw 组织成正确的 after-fuse 顺序和 named-FBO raster scope。 | 已证实时序/target 缺口 |
| P0/P1 | Aeronautics Levitite/Levitite Ghosts 被编译进 section 并会进入普通 ChunkProxy PT 提交，但 PT payload 只有 geometry type、layer name、texture 和顶点；没有原层的 tessellation shader、patch=4、world/sublevel uniform、ghost 双绘制语义。未知 layer name 在 MCVR SBT 中回退 default hit group。 | 已证实语义缺口 |
| P1 | Sable 的普通 Vanilla dispatcher 存在一个可能由 Veil fixed-buffer 事件间接触发的 inline 路径，但该路径位于 native fuse 前的 WORLD_STAGE；Radiance 当前没有一个由自己控制的 after-fuse extra-layer draw 入口。SectionRasterStorage 只恢复 VertexBuffer，不是消费者。 | 已证实入口/target 缺口 |
| 低/待核 | Sable FancySubLevelRenderDispatcher 在 2.0.5 源码中存在，但固定版本 SubLevelRenderer 只选择 VANILLA 或 SODIUM_REACHAROUND；本次未找到固定源码会创建 Fancy dispatcher 的调用者。不能把 Fancy 类存在本身当作当前缺口。 | 有界待核，暂不实施 |

## 1. Sable world section：storage 已有，独立 draw consumer 没有

### 当前 Radiance 链

文件：  
 D:\Workspaces\Repositories\GitHub\RecRivenVI\Radiance\src\main\java\com\radiance\mixins\vulkan_render_integration\WorldRendererMixins.java

方法 radiance$renderLevel：

* 145-154 在 HEAD 取消 vanilla renderLevel；没有 OpenGL fallthrough。
* 173-180 建立/加入 WorldMeshSink frame，调用 setupRender、SableSubLevelBridge.update 和 SectionRasterStorage.drain。
* 258-264 调用 ChunkProxy.setStorage、ChunkProxy.rebuild、queueSingleBlocks，然后只按 RenderType.chunkBufferLayers() 分发 NeoForge stage。
* 299-315 分发实体与 block-entity stage；323-328 分发粒子后只 queue SimulatedWorldEffects 的 EndSea final draw。
* 当前方法没有直接调用 SubLevelRenderDispatcher.renderSectionLayer 或 renderAfterSections。

文件：  
 D:\Workspaces\Repositories\GitHub\RecRivenVI\Radiance\src\main\java\com\radiance\compatibility\sable\SableSubLevelBridge.java

* prepareRenderer 65-85 只反射调用 SubLevelRenderDispatcher.preRenderChunks 和 updateCulling。
* update 88-126 只把 VanillaChunkedSubLevelRenderData 的 section 同步成 ChunkProxy external chunk；queueSingleBlocks 128-176 只处理 VanillaSingleSubLevelRenderData。
* 没有 renderSectionLayer、renderAfterSections 或固定层 draw 的桥。

文件：  
 D:\Workspaces\Repositories\GitHub\RecRivenVI\Radiance\src\main\java\com\radiance\client\render\SectionRasterStorage.java

* publish 24-30 复制 MeshData 的 DrawState、vertex bytes、index bytes。
* drain 40-49 仅在 render thread 上检查 owner/compiled/origin 后调用 section.getBuffer(layer) 的 upload。
* Snapshot.upload 68-82 只上传 VertexBuffer/index buffer；没有 RenderType.setupRenderState、shader uniform、sublevel transform 或 draw。
* WorldRendererMixins 在 ChunkProxy.rebuild 之前调用 drain（179-180）。新 external compile 的快照由下一次 drain 才可见，这可以是设计上的一帧延迟，但当前更直接的问题是没有任何实际 section draw consumer。

### 固定 Sable 的真实原入口

固定引用：  
 D:\Workspaces\References\minecraft-references\sable-2.0.5\src\common\src\main\java\dev\ryanhcode\sable\mixin\sublevel_render\impl\vanilla\LevelRendererMixin.java

* 101-105 在 LevelRenderer.renderSectionLayer 的 ShaderInstance.clear 前调用 SubLevelRenderDispatcher.get().renderSectionLayer。
* 107-130 对 Veil LayeredRenderType 的各层再次 setup shader 并调用 dispatcher。

固定 dispatcher：  
 D:\Workspaces\References\minecraft-references\sable-2.0.5\src\common\src\main\java\dev\ryanhcode\sable\sublevel\render\dispatcher\VanillaSubLevelRenderDispatcher.java

* 136-166 的 renderSectionLayer 对每个 VanillaChunkedSubLevelRenderData 调用 renderChunkedSubLevel。
* 169-216 的 renderAfterSections 消费此前记录的 single-block layer。

固定 renderer 选择：  
 D:\Workspaces\References\minecraft-references\sable-2.0.5\src\common\src\main\java\dev\ryanhcode\sable\sublevel\render\SubLevelRenderer.java

* 77-99 的 SelectedRenderer 只有 VANILLA 和 SODIUM_REACHAROUND；后者继承 VanillaSubLevelRenderDispatcher。
* 因此固定默认 Sable 路径是可达的。FancySubLevelRenderDispatcher 不能仅因类存在就加入当前必修范围。

### 为什么这不是普通 PT 缺口

ChunkProxy.rebuildSingle 的 external 分支在：

 D:\Workspaces\Repositories\GitHub\RecRivenVI\Radiance\src\main\java\com\radiance\client\proxy\world\ChunkProxy.java:498-599

会遍历 SectionCompiler.Results.renderedLayers，并把 geometry type、renderLayer.name、texture、vertex format、vertex count 与地址送给 native。普通 Sable geometry 因此已有 PT 路径。这个 JNI payload 没有 ShaderInstance 名、Veil patch state、shader block 或 RenderType 的完整状态；SectionRasterStorage 是为 external raster owner 保留原 MeshData 的补偿层，不应误称为已经渲染。

可独立实现的最小边界：

1. 保留普通 Sable section 的 ChunkProxy PT 提交，避免再次绘制所有 vanilla layers。
2. 在 SableSubLevelBridge 或同一 world consumer 中只选择需要 raster 的额外 RenderType（固定目标至少 Levitite 与 Levitite Ghosts），读取已 drain 的 section buffers，并复用 Sable Vanilla dispatcher 的 transform/section iteration。
3. 该 draw 必须被 after-fuse 队列包住，进入 WorldRasterPass 的明确 target scope；inline WORLD_STAGE 只能作为兼容事件通知，不能作为最终色彩消费者。
4. single-block 的 renderAfterSections 只有在该层确实进入 single-block path 时才调用；不要为普通 PT geometry 全量调用 dispatcher，否则会重复。

## 2. Aeronautics Levitite：注册已补，特殊 raster consumer 仍未接上

### 原层的固定语义

固定引用：  
 D:\Workspaces\References\minecraft-references\aeronautics_bundled-1.3.2\src\aeronautics\common\src\main\java\dev\eriksonn\aeronautics\index\client\AeroRenderTypes.java

* 19-28：shader identity 是 aeronautics:levitite/levitite；禁用分支使用 RENDERTYPE_SOLID_SHADER，并关闭 color/depth mask。
* 30-47：levitite 使用 DefaultVertexFormat.BLOCK、QUADS、sortOnUpload、TRANSLUCENT、CULL、BLOCK_SHEET、LIGHTMAP，并附加 Veil patchState(4)。
* 49-65：levitite_ghosts 同样是 BLOCK/QUADS/translucent/patch=4，但 NO_CULL。

固定世界注册：  
 D:\Workspaces\References\minecraft-references\aeronautics_bundled-1.3.2\src\aeronautics\common\src\main\java\dev\eriksonn\aeronautics\AeronauticsClient.java:40-47

* 注册两个 block layer。
* levitite 固定缓冲阶段是 AFTER_BLOCK_ENTITIES。
* levititeGhosts 固定缓冲阶段是 AFTER_WEATHER。

固定 Sable sublevel 绘制语义：  
 D:\Workspaces\References\minecraft-references\aeronautics_bundled-1.3.2\src\aeronautics\common\src\main\java\dev\eriksonn\aeronautics\mixin\levitite\VanillaChunkedSubLevelRenderDataMixin.java:24-50

* levitite 先调用 LevititeShaderManager.prepareShaderForSublevel，再进入原 section draw。
* ghosts 仅在 manager.needsLayers() 时绘制；先 layerIndex=1 且禁用 depth，再绘制正常层，最后恢复 layerIndex=0 与 depth test。

固定主世界 shader 准备：  
 D:\Workspaces\References\minecraft-references\aeronautics_bundled-1.3.2\src\aeronautics\common\src\main\java\dev\eriksonn\aeronautics\mixin\render\vanilla\LevelRendererMixin.java:29-53

原 mixin 在 LevelRenderer.renderSectionLayer 的 ShaderInstance.apply 后处理 levitite world uniform，并在尾部恢复状态。Radiance 的 renderLevel HEAD cancel 使原版主循环不再替它承担最终 world consumer。

### 当前实现覆盖到哪里

当前注册修复：  
 D:\Workspaces\Repositories\GitHub\RecRivenVI\Radiance\src\main\java\com\radiance\mixins\compatibility\aeronautics\AeronauticsNeoForgeClientSetupMixins.java:28-52

只恢复 ForgeRenderTypeStageHandler 的 custom block layers、ChunkRenderTypeSet 的 list/array/bits。Veil 4.3.2 自己的 RenderTypeMixin 会把 custom layers 加到 chunkBufferLayers，因而 layer registration 不是本轮确认的缺口。

当前 section 编译：  
 D:\Workspaces\Repositories\GitHub\RecRivenVI\Radiance\src\main\java\com\radiance\mixins\vulkan_render_integration\SectionBuilderMixins.java:74-139

遍历 model.getRenderTypes 的每个 layer，map 初始容量虽然来自 chunkBufferLayers，但 beginBufferBuilding 会按实际 layer 建立 PBRVertexConsumer；因此 Levitite layer 可以进入 renderedLayers。该事实只证明 MeshData 已产生，不证明它按 Aeronautics shader 绘制。

当前 PT 转换：  
 D:\Workspaces\Repositories\GitHub\RecRivenVI\Radiance\src\main\java\com\radiance\client\proxy\world\ChunkProxy.java:498-599  
 D:\Workspaces\Repositories\GitHub\RecRivenVI\MCVR\src\core\render\chunks.cpp:1448-1502  
 D:\Workspaces\Repositories\GitHub\RecRivenVI\MCVR\src\core\render\modules\world\ray_tracing\submodules\world_prepare.cpp:535-556

* Radiance 把 Levitite layer name 作为 geometry group name，并用 Constants.GeometryTypes.getGeometryType 归类。
* Constants.java:145-193 对这种 sortOnUpload/translucent 层得到 WORLD_TRANSPARENT。
* MCVR chunks.cpp 只保存 group name；world_prepare.cpp:550-552 找不到 group 时使用 fallbackHitGroupIndex。
* 没有从该 payload 进入 aeronautics:levitite/levitite 的 tessellation control/evaluation、patch=4、LevititeShaderManager uniforms 或 ghosts 双绘制。不能把未知 group 的 fallback 当作 Levitite 支持。

Veil/Radiance 具备的底层能力不等于 world consumer 已存在：

* D:\Workspaces\Repositories\GitHub\RecRivenVI\Radiance\src\main\java\com\radiance\compatibility\veil\VeilShaderBridge.java:348-355 为使用 gl_FragCoord 的外部 shader 写入 drawable target height，509-547 解析 tessellation pair 并要求 patch=4（529）。
* D:\Workspaces\Repositories\GitHub\RecRivenVI\Radiance\src\main\java\com\radiance\client\shader\ShaderRegistry.java:211-261 可注册 ExternalShaderMetadata 的 tesc/tese/patch。
* D:\Workspaces\Repositories\GitHub\RecRivenVI\MCVR\src\core\render\modules\ui_module.cpp:249-304 的 native dynamic raster pipeline 支持 tessellation；JNI 注册入口在 com_radiance_client_proxy_vulkan_ShaderProxy.cpp:132-137。
* 这些是可复用的 Vulkan raster 能力，当前没有一个 world method 用它消费 Levitite section。不要为此复制 Flywheel 的 GL IndirectDrawManager、DepthPyramid 或 OitFramebuffer。

### inline 间接路径的边界

固定 Veil 4.3.2：

* ForgeRenderTypeStageHandler.java:49-65 在固定 stage 结束时，对 custom block layer 调用 LevelRendererBlockLayerExtension.veil$drawBlockLayer。
* PipelineLevelRendererMixin.java:176-302 对非 LayeredRenderType 走 LevelRenderer.renderSectionLayer；对于 layered type 逐层绑定 VertexBuffer。
* 固定 Sable LevelRendererMixin.java:101-130 可能因此间接进入 SubLevelRenderDispatcher。

这条路径不能作为 Radiance 已完成的 after-fuse consumer：

* 当前 WorldRendererMixins 的 AFTER_BLOCK_ENTITIES/AFTER_WEATHER dispatch 在 native EntityProxy.build/fuse 前发生；GameRendererMixins.java:177-196 显示 frame 仍在 LevelRenderer 返回后才 commit、EntityProxy.build、RendererProxy.fuseWorld，再 AfterWorldRender.flush。
* 这条 inline 路径使用 WORLD_STAGE。VertexBuffer.draw 由 VertexBufferMixins.java:158-193 转到 RasterDrawBridge；只有 WORLD_RASTER 或明确非零 custom framebuffer 才有允许的路由。
* WorldRasterPass.java:16-73 当前只在 flush 中绑定 framebuffer 0，并建立 WORLD_RASTER；它没有把 Aero fixed buffer 事件自动重排到 flush，也没有保存 Levitite 的 per-sublevel/ghost 双绘制调用。

最小修复边界是“延后并过滤”：

* 让 after-fuse world consumer 对 Levitite 与 Levitite Ghosts 使用原 Sable/Aeronautics dispatcher 的 layer draw 语义；
* 仅对额外层执行，普通 Sable layers 留给现有 PT；
* 复用已存在的 VeilShaderBridge/ShaderRegistry/RasterDrawBridge，不重新做 shader reload；
* Ghosts 的 layerIndex、depth、cull、color/depth mask 必须跟固定源一致；
* 若仍保留 inline Veil fixed-buffer callback，应明确其只能填充/通知，不能把 pre-fuse raster 结果当作 final world pixels。

## 3. EndSea shadow group：listener 保留，producer/final 的时序与 target 未接通

### 当前 Radiance 链

文件：  
 D:\Workspaces\Repositories\GitHub\RecRivenVI\Radiance\src\main\java\com\radiance\compatibility\simulated\SimulatedWorldEffects.java:13-24

只在 simulated 加载时调用 WorldRasterPass.defer("simulated:end_sea", ...)，回调是 EndSeaRenderer.render(camera, renderer)。

调用点：  
 D:\Workspaces\Repositories\GitHub\RecRivenVI\Radiance\src\main\java\com\radiance\mixins\vulkan_render_integration\WorldRendererMixins.java:327-328

当前 Radiance 侧没有直接调用 EndSeaShadowRenderer.renderShadowMap，但这不等于没有入口。NeoForge 的 GameRenderer 在 LevelRenderer 返回后仍 dispatch AFTER_LEVEL；Radiance 的 GameRendererMixins.java:177-196 明确保持 world frame 到该 dispatch 之后，并在随后执行 EntityProxy.build、RendererProxy.fuseWorld、AfterWorldRender.flush。固定 Veil 的 NeoForgeVeilEventPlatform.java:79-91 仍把 Veil stage listener 注册到 NeoForge stage，原 SimulatedClient.java:33 的 EndSeaShadowRenderer listener 因而仍可由 AFTER_LEVEL 的 Veil post 间接触发。

当前真正缺的是消费时序和 target：WorldRendererMixins.java:327-328 先把 EndSeaRenderer final draw 放进 AfterWorldRender 队列；AFTER_LEVEL 的 Veil post 是后续才 defer 的。若不显式排序，final 可能先读取 shadow FBO，producer 才随后写入。WorldRasterPass.java:29-57 当前只建立主 world raster scope、绑定 target 0；它不会凭 defer 自动把 shadow listener 变成 named simulated:end_sea_shadows 的专用 pass，或代替其 spread pipeline。

### 固定 EndSea producer/consumer 合约

固定引用：  
 D:\Workspaces\References\minecraft-references\aeronautics_bundled-1.3.2\src\simulated\common\src\main\java\dev\simulated_team\simulated\SimulatedClient.java:31-44

SimulatedClient.init 在 33 注册 VeilEventPlatform.INSTANCE.onVeilRenderLevelStage(EndSeaShadowRenderer::renderShadowMap)，这是 shadow producer 的真实入口。

固定 shadow producer：  
 D:\Workspaces\References\minecraft-references\aeronautics_bundled-1.3.2\src\simulated\common\src\main\java\dev\simulated_team\simulated\content\end_sea\EndSeaShadowRenderer.java:38-103

* 49-53 只接受 Veil stage AFTER_LEVEL。
* 63-78 解析 physics、named framebuffer、正交投影及 shadow camera；shadow radius 是 128，shadow camera 的 y 为 physics.startY - radius，并对 x/z floor。
* 82-93 收集大范围 Sable ClientSubLevel，绑定并 clear FBO，调用 SimpleSubLevelGroupRenderer.renderGroup(..., fbo, ..., false)。
* 96-102 运行 simulated:spread_end_sea pipeline 五次。
* 152-158 为 EndSea final 提供 renderingShadowMap 与 lastRenderOrigin。

固定 final consumer：  
 D:\Workspaces\References\minecraft-references\aeronautics_bundled-1.3.2\src\simulated\common\src\main\java\dev\simulated_team\simulated\content\end_sea\EndSeaRenderer.java:62-165

* 69-71 在 shadow producer 期间主动跳过最终 EndSea。
* 82-88 绑定 simulated:end_sea shader，并从 named shadow FBO 的 depth attachment 与 color attachment 0 读取 ShadowDepthSampler/ShadowStrengthSampler。
* 96-112 使用 producer 的 lastRenderOrigin、radius 与 physics.startY；这不是可用一张主色图替代的简单 overlay。

固定 targets：  
 D:\Workspaces\References\minecraft-references\aeronautics_bundled-1.3.2\src\simulated\common\src\main\resources\assets\simulated\pinwheel\framebuffers\end_sea_shadows.json  
 D:\Workspaces\References\minecraft-references\aeronautics_bundled-1.3.2\src\simulated\common\src\main\resources\assets\simulated\pinwheel\post\spread_end_sea.json

* end_sea_shadows 是 depth=true、autoClear=false、1024x1024、RGBA8；final 同时依赖 depth 与 color strength attachment。
* spread_end_sea 先对 named FBO 做 veil:mask，再在 swap 与 named FBO 之间双向 blit；producer 源码按固定合约运行五次。

最小修复边界：

1. 在现有 after-fuse world queue 中，保证 AFTER_LEVEL 的 shadow producer 先于 EndSeaRenderer.render 执行；不再只按 WorldRenderer 中的出现顺序 defer final。
2. 增加一个专用 shadow-target pass（可以是 SimulatedWorldEffects 的 companion 或独立 world consumer）：解析 EndSeaShadowRenderer.getShadowsFramebuffer()，切到其 color/depth target，设置固定 shadow camera/projection/orientation，消费 Sable group 的可用 raster section buffers，恢复 target/state。
3. 按固定源码执行 spread_end_sea，完成后再调用已存在的 EndSeaRenderer.render。shadow pass 与 final draw 不能共享 WorldRasterPass 当前“强制 framebuffer 0”的实现而省略 named FBO。
4. 只承接 EndSea shadow group；Simulated diagram 的 SimpleSubLevelGroupRenderer.renderChain/DiagramScreen 路径不属于本项。

## 4. Fancy dispatcher：明确不扩大当前范围

固定引用：  
 D:\Workspaces\References\minecraft-references\sable-2.0.5\src\common\src\main\java\dev\ryanhcode\sable\sublevel\render\dispatcher\FancySubLevelRenderDispatcher.java

Fancy renderSectionLayer 218-287 使用自有 VertexArray、commandBuilder、dynamic program 与间接 draw；289-297 的 renderAfterSections/renderBlockEntities 仍是 TODO。Radiance 只在：

 D:\Workspaces\Repositories\GitHub\RecRivenVI\Radiance\src\main\java\com\radiance\mixins\compatibility\sable\SableFancySubLevelRenderDispatcherMixins.java:17-29

替换其 getDynamicProgram 为 SableShaderBridge；这不是主 world draw。固定 Sable SubLevelRenderer.java:77-99 没有 Fancy 选择项，且本次固定源码未找到 Fancy 实例化调用者。因此：

* 默认 VANILLA/ReachAround 的 Levitite extra-layer 缺口必须处理；
* Fancy 只有在一个固定运行组合被证明选中或另一个固定 mod 明确创建它时才重新开项；
* 不复制其 GL indirect architecture 到 MCVR Vulkan backend。

## 可独立实现边界与验证边界

| 文件/方法 | 可独立实现内容 | 不能在本调查中宣称 |
|---|---|---|
| WorldRendererMixins.radiance$renderLevel | 保留 PT 普通 world；把额外层和 shadow producer 排到 after-fuse queue | 当前源码已有最终 Levitite 像素 |
| SableSubLevelBridge + SectionRasterStorage | 为额外 RenderType 提供 owner/compiled/origin 安全的 buffer 消费，必要时处理一帧 snapshot latency | drain 已经等于 renderSectionLayer |
| WorldRasterPass 或专用 shadow pass | 建立明确 WORLD_RASTER 与 named EndSea FBO target，维护 state/target 恢复 | framebuffer 0 能替代 EndSea shadow FBO |
| AeroRenderTypes / LevititeShaderManager 原语义 | 重用 shader identity、patch=4、uniform、ghost layerIndex/depth/cull 语义 | generic WORLD_TRANSPARENT/fallback hit group 等同 Levitite |
| MCVR chunks/world_prepare | 仅在选择 PT 方案时注册显式 material/group contract | 本轮要求复制 Flywheel GL Backend |

### 已有与历史验证的边界

* 固定版本静态证据确认：Sable Vanilla/ReachAround section data 可达；Aeronautics Levitite layer/shader/patch/fixed stages 可达；EndSea named FBO、shadow producer、final sampler 合约可达。
* 现有 Radiance/MCVR native PT 路径仅证明普通 section geometry 可提交；本轮没有运行 native draw。
* 现有 Veil/Aeronautics diagram consumer 的历史报告只验证了六个固定 diagram shader consumer，并明确把 Levitite world/diagram consumer 留为 pending；它不能替代本报告的 world target 验证。
* 需要后续真实客户端验证：custom layer 是否在目标组合实际产生 section MeshData；snapshot drain 与 dispatcher draw 的 owner/transform；Levitite time/noise/depth sampler 与 ghost 两次绘制；EndSea shadow FBO depth/strength、spread pipeline、EndSea final sampler。该列表是待验证项，不是已实现项。
* 本轮没有把客户端待验、像素观察或 FBO 驱动结果写成源码 PASS。

## 固定版本证据位置

Radiance 固定组合：

 D:\Workspaces\Repositories\GitHub\RecRivenVI\Radiance\gradle.properties:5,16-18

其中是 Minecraft 1.21.1、Sable 2.0.5+mc1.21.1、Aeronautics 1.3.2+mc1.21.1、Veil 4.3.2。依赖声明与 compatibility compile/runtime 边界在：

 D:\Workspaces\Repositories\GitHub\RecRivenVI\Radiance\build.gradle:104-125

固定源码参考及其版本目录：

* D:\Workspaces\References\minecraft-references\sable-2.0.5
* D:\Workspaces\References\minecraft-references\aeronautics_bundled-1.3.2
* D:\Workspaces\References\minecraft-references\veil-4.3.2
* D:\Workspaces\References\minecraft-references\neoforge-21.1.250

以上是版本与源码位置证据，不是当前客户端运行证明。报告只新增本文件；未改旧报告、Radiance、MCVR 或 Git 状态。
