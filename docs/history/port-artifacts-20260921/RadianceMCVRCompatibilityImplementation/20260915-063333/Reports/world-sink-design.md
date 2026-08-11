# WP-C02/C03/C05/C06：真实 World Sink 有界设计调查

- 调查性质：源码优先、只读设计；没有修改业务仓库、构建、启动游戏/GUI/Prism、清理或 Git 操作。
- 固定基线：Radiance HEAD 414d8e330a2fc6cb1e8630cc95f2302b2b97a0e8；MCVR HEAD 9905c81b1999f5845bf66d13501d371c16adf561。
- 参考：Minecraft 1.21.1 patched、NeoForge 21.1.250、Create 6.0.10、Ponder/Catnip 1.0.82。
- 路径前缀：Radiance = D:/Workspaces/Repositories/GitHub/RecRivenVI/Radiance；MCVR = D:/Workspaces/Repositories/GitHub/RecRivenVI/MCVR；References = D:/Workspaces/References/minecraft-references。下文省略前缀时仍按此绝对路径解析。
- 范围：只核对 WORLD_STAGE/DIMENSION_EFFECT MeshData 入口、VertexFormat/RenderType state、坐标原点、EntityProxy 真 native 提交复用、scope 异常退出和最小 world sink 契约。Framebuffer/target 架构不在本报告重审。

## 1. 直接结论

当前拒绝链是：

ClientHooks.dispatchRenderStage 或 DimensionSpecialEffects callback
→ scope wrapper
→ mod/Catnip 用 MultiBufferSource 构建 MeshData
→ RenderType.draw(MeshData)
→ BufferUploader._drawWithShader(MeshData)
→ BufferRendererMixins 发现不是 GUI/CAMERA_OVERLAY
→ MeshData.close、reportDiscard、取消绘制。

精确入口：

- Radiance RenderLevelStageCompatibilityMixins.java:21-40 在 IEventBus.post 外包 RenderCaptureContract.ScopeKind.WORLD_STAGE。
- Radiance DimensionSpecialEffectsCompatibility.java:23-33 在 renderSky、renderClouds、renderSnowAndRain 外包 ScopeKind.DIMENSION_EFFECT。
- Radiance WorldRendererMixins.java:138-335 以 HEAD 注入完全替换 LevelRenderer.renderLevel，再手动发各阶段事件。
- Radiance BufferRendererMixins.java:22-32 对所有非 UI route 关闭 MeshData；scope label 没有 world identity、RenderType、坐标或 native handle。

恢复事件、callback 返回 true、UI draw 或日志计数都不等于 world geometry 已进入 MCVR 的 Entities/BLAS/TLAS。真实 sink 必须在 RenderType 仍可取得的边界捕获 metadata，并在 EntityProxy.build 之前完成数据复制。

## 2. 两条已核验入口链

### 2.1 Create AFTER_PARTICLES

Create 6.0.10：

- References/create-6.0.10/src/src/main/java/com/simibubi/create/foundation/events/ClientEvents.java:235-258 的 onRenderWorld 只处理 Stage.AFTER_PARTICLES，取 event.getPoseStack、DefaultSuperRenderTypeBuffer.getInstance，调用 TrackBlockOutline、TrackTargetingClient、CouplingRenderer、CarriageCouplingRenderer、SchematicHandler 和 ChainConveyorInteractionHandler，最后 buffer.draw。
- References/ponder-1.0.82+mc1.21.1/src/net/createmod/catnip/render/DefaultSuperRenderTypeBuffer.java:34-60 的 draw 依次执行三个 phase 的 BufferSource.endBatch。
- References/minecraft-1.21.1/src/net/minecraft/client/renderer/MultiBufferSource.java:41-66,76-105 的 BufferSource 保留 RenderType 到 startedBuilders；endBatch 会 build MeshData、必要时 sortQuads，然后调用 RenderType.draw。
- References/minecraft-1.21.1/src/net/minecraft/client/renderer/RenderType.java:1143-1147 的 draw 依次 setupRenderState、BufferUploader.drawWithShader、clearRenderState。
- Radiance BufferRendererMixins.java:22-32 最终只收到 MeshData，RenderType 已经不在参数中。

Create 的 TrackTargetingClient.java:113-132 又在 PoseStack 上执行 translate(BlockPos - camera)。Radiance WorldRendererMixins.java:337-340 的 radiance$levelPoseStack 已执行 translate(-camera)，并在 :279-304 传入 AFTER_ENTITIES、AFTER_BLOCK_ENTITIES、AFTER_PARTICLES。因此当前直接恢复 Create listener 会把相机平移做两次。该信息不会随 MeshData 保存，不能在 BufferUploader 数值层反推。

### 2.2 DimensionSpecialEffects callback

NeoForge 21.1.250 的 IDimensionSpecialEffectsExtension.java:24-49 定义：

- renderClouds(ClientLevel, ticks, partialTick, PoseStack, camX, camY, camZ, modelViewMatrix, projectionMatrix)；
- renderSky(ClientLevel, ticks, partialTick, modelViewMatrix, Camera, projectionMatrix, isFoggy, setupFog)；
- renderSnowAndRain(ClientLevel, ticks, partialTick, LightTexture, camX, camY, camZ)。

这些 boolean 的含义是阻止 vanilla 对应效果。ClientHooks.java:288-304 的 dispatchRenderStage 只 post event，RenderLevelStageEvent.java:28-35、:37-58 明确事件不可取消、无 result，且事件没有 MultiBufferSource 或 native submission handle。

Radiance WorldRendererMixins.java:216-233、:312-331 传入真实 level、相机、矩阵和 callback boolean，但 DimensionSpecialEffectsCompatibility.invoke 没有 capture accepted count。renderClouds 取得新 PoseStack；renderSnowAndRain 没有 PoseStack；renderSky 没有标准 world origin。raw Tesselator/BufferUploader draw 若没有显式 RenderType、坐标和 sink context，当前 API 无法恢复。

## 3. MeshData、格式与 state

Minecraft MeshData.java:14-23、:52-63、:85-87 只保存 vertex buffer、可选 index buffer 和 DrawState。DrawState 只有 format、vertexCount、indexCount、mode、indexType，没有 RenderType、shader、world、stage 或坐标来源。

当前实际格式：

- Catnip DefaultSuperRenderTypeBuffer.java:63-90 的 fixed buffers 以 RenderType 为 key，包含 block sheets、glint、water mask、destroy types 和 Ponder outline。
- Catnip ShadedBlockSbbBuilder.java:34-51 默认是 QUADS + DefaultVertexFormat.BLOCK。
- Radiance StorageVertexConsumerProvider.java:39-58 对 QUADS 采用 PBRVertexConsumer/PBR_TRIANGLE，其他 mode 采用 renderLayer.format。
- Radiance PBRVertexConsumer.java:94-141 要求 PBR stride 128 bytes；texture id 从 TextureManager 取得；alpha mode 从 layer name/transparency 推导。
- Radiance Constants.java:80-126 与 MCVR World.java:18-33 可识别 BLOCK、NEW_ENTITY、PARTICLE、POSITION、POSITION_COLOR、POSITION_COLOR_NORMAL、POSITION_COLOR_LIGHTMAP、POSITION_TEX、POSITION_TEX_COLOR、POSITION_COLOR_TEX_LIGHTMAP、POSITION_TEX_LIGHTMAP_COLOR、POSITION_TEX_COLOR_NORMAL 和 PBR_TRIANGLE。
- MCVR common/shared.hpp:118-168 的 PBRVertex/MaterialVertex 是当前 native ABI；core/render/vertex_formats.cpp:152-174 定义 PBR attribute offsets；core/vulkan/vertex.cpp:5-41 把 alpha/coordinate/feature bits 打进 packedData。

World sink 应只接受有明确 ABI 的格式；未知格式直接 UNSUPPORTED，不能按 stride 猜。EntityProxy.processEntityRenderData.java:244-248 过滤 QUADS、TRIANGLES、TRIANGLE_STRIP、LINE_STRIP、LINES、DEBUG_LINE_STRIP、DEBUG_LINES；TRIANGLE_FAN 虽在 Constants.DrawModes.java:44-73 中枚举，当前 EntityProxy 不收，native entities.cpp:890-1136 也没有可用 fan 分支，必须源头三角化或显式拒绝。

当前 EntityProxy 保留的 per-layer 信息：

- EntityRenderLayer 仍持有 RenderType：EntityProxy.java:1972-1974。
- queueBuildInternal 从 CompositeRenderType.state.textureState 读取 texture、从 Constants.GeometryTypes 读取 geometry type，再传 format、mode、vertex count、vertex address：EntityProxy.java:1734-1795。
- geometry type 只综合 layer name、reflect、sortOnUpload、transparency state：Constants.java:145-194。
- 没有传 shaderState、完整 transparency blend、depthTest、cull、lightmap、overlay、layering、output、texturing、writeMask、line/color logic、outline property。
- MCVR entities.cpp:549-840 的非 PBR VertexFormat 转换从零构造 VertexFormat::PBRVertex，最后只补 textureID；没有从 RenderType/transparency 写入 alphaMode，默认值仍为 OPAQUE。geometryType 为 WORLD_TRANSPARENT 不能自动恢复该 alpha semantic；direct Catnip MeshData 必须在 RenderType 边界先捕获并显式下沉 alpha/material key。
- MultiBufferSource.BufferSource.java:91-99 可能已经对 QUADS 生成 sort index；EntityProxy native queueBuild 主要按 mode/vertex count 重建 index，不能还原任意排序。
- BufferProxy.java:53-73 已有正确参考：indexBuffer 存在就复制实际 index bytes，不存在才按 mode 生成。

RenderType.CompositeState 的完整字段见 References/minecraft-1.21.1/src/net/minecraft/client/renderer/RenderType.java:1257-1319、:1332-1435。world sink 至少要保存 shader/material key、texture native id/generation、alpha/blend semantic、coordinate、geometry type、stage 和必要的 depth/cull/output semantic；无需把每个 GL action 一比一重建，但不能在 MeshData 层假设默认状态。

纹理整数 id 不应泛称假 ID：

- Radiance TextureUtilMixins.java:15-19 把 generateTextureId 转到 TextureProxy.generateTextureId。
- MCVR com_radiance_client_proxy_vulkan_TextureProxy.cpp:9-16 调用 Textures::allocateTexture。
- MCVR textures.cpp:52-60 在 textures_ 和 samplers 中分配 id，后续 initializeTextureImpl 创建 DeviceLocalImage。
- EntityProxy 取得的 TextureManager id 因此在当前映射中可以是真实 native logical id；sink 仍需校验它已经 initialize/bind 且属于当前 resource generation。

## 4. 坐标原点

Radiance WorldRendererMixins.java:161-164 读取 double camera position；:151 经 PlayerProxy.setCameraPos 写入 native；MCVR buffers.cpp:473-477 把 World::getCameraPos 写入 WorldUBO.cameraPos。

当前手动 stage stack：

- WorldRendererMixins.java:238-240、:279-304、:329-331 传入 radiance$levelPoseStack。
- WorldRendererMixins.java:337-340 对新 PoseStack 预先 translate(-camera)。

vanilla 参考：

- Minecraft LevelRenderer.java:994-1001 创建 identity PoseStack。
- :1033-1045 把实体相机扣除放在 renderEntity 参数中，随后把 identity-like PoseStack 交给 AFTER_ENTITIES。
- :1117-1119、:1185-1204、:1219-1229 继续复用该 stage stack。
- NeoForge RenderLevelStageEvent.java:48-58 仅在 poseStack 为 null 时新建 identity PoseStack。

因此应先把 manual stage PoseStack 语义改为 vanilla identity，避免 Create 自己的 pos-camera 再扣一次。之后显式规定：

- WORLD：顶点是对象局部或未经相机扣除的 world coordinates；origin 是 double 世界原点；native WORLD transform 使用 origin-camera。
- CAMERA_SHIFT：顶点已在 world-axis camera-relative 空间；origin 设为 0，由 native camera shift 把 camera origin 加回。Create TrackTargetingClient 在 identity stage stack 上产生的 pos-camera geometry 属于此类。
- CAMERA：顶点是明确 camera-space 并需要完整相机旋转；只用于明确声明的 camera/hand consumer，不能作为普通 world stage 默认值。
- SKYBOX：renderSky 不是普通 world entity；只有独立 sky semantic 和相机空间协议时才另建 sink，不能将 raw sky MeshData 塞入普通 TLAS。

MCVR World::Coordinates 定义于 world.hpp:47-51；world_prepare.cpp:216-240 的真实变换为 WORLD 平移 origin-camera、CAMERA 使用 transpose(cameraViewMatInv)、CAMERA_SHIFT 使用 cameraViewMatInv[3] 平移。coordinate 还被 vertex.cpp:5-17 打包并由 shader/util/vertex.glsl:93-99 解码，是实际 native 语义。

## 5. EntityProxy 可复用的真实 native 链

可复用的是 EntityProxy 的动态 geometry transport 和 MCVR Entities 真实资源链，不是把 stage 伪装为 UI，也不是把 transient stage geometry 塞进 ChunkProxy section。

Java：

- EntityProxy.processWorldEntityRenderData：EntityProxy.java:165-210、:225-280，把 RenderType -> VertexConsumer layer 建成 EntityRenderData/EntityRenderLayer。
- EntityProxy.queueBuild：EntityProxy.java:1557-1591、:1704-1819，把对象位置、ray tracing flags、post、geometry type/group/content、texture、format、mode、counts 和 pointers 组织成 JNI payload。
- GameRendererMixins.java:175-182 在 renderLevel 尾端依次调用 EntityProxy.build、RendererProxy.fuseWorld。

JNI/native：

- MCVR com_radiance_client_proxy_world_EntityProxy.cpp:7-56 把 pointers 组装成 EntitiesBuildTask；当前 world == nullptr 静默 return。
- MCVR entities.hpp:24-81 的 EntitiesBuildTask/EntityBuildData 有对象位置、coordinate、geometry names、PBR vertices、indices、BLAS address，但没有 world identity、dimension、stage、generation、完整 material state。
- MCVR entities.cpp:243-363 的 EntityBuildDataBatch::build 创建真实 DeviceLocalBuffer 的 position/material/index buffer、复制上传并构建 BLAS。
- MCVR entities.cpp:451-470 的 Entities::resetFrame 用 FrameResourceRetainer 保留上一批，换出当前 build/post batch。
- MCVR entities.cpp:472-505、:1155-1181 同步复制 JNI data 并建立 EntityBuildData。
- MCVR entities.cpp:1184-1215 的 Entities::build 上传 buffer，建立 EntityBatch/EntityPostBatch；close 清空 batch。
- MCVR world_prepare.cpp:191-240 从 EntityBatch 建 TLAS instance；:253-317 绑定 hit group、index/position/material addresses 与历史变换。

ChunkProxy 的 section path 只适合 compiled RenderSection：

- Radiance ChunkProxy.java:354-409、:496-565 以 section origin/id、compiled/dirty/rebuild 组织。
- MCVR ChunkProxy JNI 和 chunks.hpp:24-48、:205-220 没有 stage、per-object world generation、ray tracing flags、post 或 transient coordinate contract。

建议增加带上下文的 EntityProxy.queueWorldMesh，或同等新入口；不要让现有一次 queueBuild 的单一 coordinate 覆盖混合 stage。现有 queueBuildInternal 的 coordinate 是整批单值：EntityProxy.java:1564-1571、:1799-1819。

## 6. 最小真实 World Sink 契约

建议新增 Radiance WorldMeshSink.java，或把等价能力放入 RenderCaptureContract。设计字段如下：

WorldFrameContext：

- worldInstanceToken：单调递增的 level load/re-entry generation。
- dimensionKey：ResourceKey<Level>。
- ClientLevel object identity：避免同 dimension 重入时误认。
- frameIndex：绑定当前 FrameworkContext/swapchain slot。
- resourceGeneration：绑定 texture/shader/native generation。
- cameraOrigin：double x/y/z。

WorldStageContext：

- parent WorldFrameContext。
- stageKey：AFTER_SKY、AFTER_ENTITIES、AFTER_BLOCK_ENTITIES、AFTER_PARTICLES、AFTER_WEATHER、DIMENSION_RENDER_SKY、DIMENSION_RENDER_CLOUDS 或 DIMENSION_RENDER_WEATHER。
- sourceId：稳定的 mod + consumer + object key；System.identityHashCode 只能作辅助去重。
- coordinateSpace：WORLD、CAMERA、CAMERA_SHIFT 或单独 SKYBOX。
- coordinateOrigin：double x/y/z。
- poseApplied：明确记录是否已经应用 model/camera transform。
- post、visibilityFlags、lineWidth、normalOffset 等当前 consumer 需要的语义。
- targetKind：DEFAULT_WORLD/TLAS、EXPLICIT_CUSTOM_TARGET 或 SKYBOX。active 非 0 framebuffer 或明确的 custom target 不得默认送入世界 TLAS；应交给 root 的真实 FramebufferProxy/MCVR custom pass 路径，并保留 target id/generation、shader key、matrix、viewport、blend/write semantic 和 draw order。

提交操作：

WorldMeshSink.submit(WorldStageContext context, RenderType renderType, MeshData mesh, sourceObjectId, contentName)
→ ACCEPTED(count,generation)、UNSUPPORTED(reason) 或 STALE(reason)。

规则：

1. worldInstanceToken 由 level object identity、dimension key 和单调 generation 共同校验，不能由 entity hashCode 代替。
2. frameIndex/resourceGeneration/stage token 失效时返回 STALE；不能把旧 payload 送给当前 Renderer::instance().world。
3. coordinateSpace/origin 必须由 producer 显式声明；sink 不从 scope label 或顶点数值推断。
4. stage token 只允许同步提交；异步 MeshData 在 token 结束后拒绝。
5. RenderType.draw 边界拿到的 RenderType 作为 per-layer key；没有 RenderType 的 direct BufferUploader 保持 unsupported。
6. 每个 payload 保存 vertex format、stride/count、draw mode、index type/count、vertex bytes，以及存在时的实际 index bytes。
7. 保存 canonical texture ResourceLocation、已验证的 native texture id/generation、shader/material key、alpha/blend semantic 和需要的 depth/cull/output semantic。
8. provider、MeshData、direct buffers 必须在 native queue 完成复制后关闭；native 不保留 Java direct pointer。
9. callback boolean 与 ACCEPTED count 分开；custom effect 返回 true 但没有 accepted native geometry 时不能称 world render success。
10. renderSky 默认走独立 SKYBOX gate；没有 sky consumer 时保持 unsupported 或明确 vanilla fallback/效果损失。
11. 只有 targetKind=DEFAULT_WORLD/TLAS 的 payload 才可进入本 world sink；EXPLICIT_CUSTOM_TARGET 的 MeshData 即使已有真实 framebuffer resource，也不能因此被当成 world geometry。

## 7. 推荐实现接缝和文件

### World 与显式 custom target 的分流

world sink 只接收具有世界几何语义、默认 world/TLAS 目标和有效 world/frame generation 的提交。root 最新引入的真实 FramebufferProxy、MCVR Framebuffers 和 UIModule custom pass 可以承接显式离屏目标，但这条路径要和 WorldMeshSink 分开：

- `targetKind=DEFAULT_WORLD/TLAS`：允许进入 EntityProxy/Entities/WorldPrepare 的 world geometry batch。
- `targetKind=EXPLICIT_CUSTOM_TARGET`：保留真实 target resource identity、shader/material key、model/view/projection matrix、viewport、blend/write state、stage order 和 target generation，交给 custom target pass；不能仅因 framebuffer id 非零或资源真实存在就送进 TLAS。
- `targetKind=SKYBOX`：走 sky semantic；没有明确 sky consumer 时保持 unsupported。
- target id 只有同时有真实 image/view、绑定/使用状态、generation 和释放关系时才是合法逻辑资源。没有这些证据的 id 仍不能报告成功。

这项分流只定义 world sink 的输入边界，不重复 FramebufferProxy/UIModule 的实现细节。

### Radiance

- 新增 client/render/WorldMeshSink.java：context、generation、RenderType snapshot、MeshData/index copy、显式状态返回。
- 新增 mixins/vulkan_render_integration/RenderTypeWorldSinkMixins.java：在 RenderType.draw(MeshData) HEAD 处，只有完整 WorldStageContext 时调用 sink 并取消原 setup/draw；无 context 的 direct BufferUploader 继续 reject。
- 修改 client/render/RenderCaptureContract.java:20-83、:124-149：保留 GUI scope，增加 world context 或和 WorldMeshSink 建立独立 LIFO token。
- 修改 mixins/vulkan_render_integration/RenderLevelStageCompatibilityMixins.java:21-40：将当前 ClientLevel、stage、frame/world generation 传入 context。
- 修改 compatibility/neoforge/DimensionSpecialEffectsCompatibility.java:23-33：增加 ClientLevel、operation、coordinate/semantic；boolean 不代替 accepted。
- 修改 mixins/vulkan_render_integration/WorldRendererMixins.java:138-340：stage frame context、identity PoseStack 语义、异常 abort；不要用 callback 恢复代替 sink。
- 修改 mixins/vulkan_render_integration/BufferRendererMixins.java:22-32：保留未标记或 metadata 不完整的 reject。
- 修改 compatibility/create/CreateValueBoxWorldGeometry.java:22-65 和 mixins/compatibility/create/CreateValueBoxMixins.java:16-49：用 method-level finally 绑定 begin/finish，不能依赖 RETURN-only finish；在 queue/process/native 失败时关闭 provider。
- 修改 client/proxy/world/EntityProxy.java:165-280、:1557-1898、:1972-2000：新增 queueWorldMesh 或带 WorldStageContext 的 overload，按 stage/coordinate 分批，保存 per-layer state 和实际 index。
- 修改 mixins/vulkan_render_integration/MinecraftClientMixins.java:242-248：disconnect HEAD 使 world generation 失效；WorldRenderer.close 相关路径也要 abort 未完成 token。
- 如需访问 CompositeState 私有字段，新增 RenderTypeStateAccessor；不能用 layer name 猜 shader/output。

### MCVR

- core/middleware/com_radiance_client_proxy_world_EntityProxy.cpp:7-56：扩展 JNI payload，检查 active world/generation，world null/stale 返回明确失败。
- core/render/entities.hpp:24-81、:107-129 和 entities.cpp:472-505：增加 world token、resource generation、stage、source identity、per-geometry material、actual index bytes、coordinate/origin。
- core/render/entities.cpp:243-363、:1184-1215：复用真实 DeviceLocalBuffer、staging、BLAS、EntityBatch。
- core/render/world.hpp:47-83、world.cpp:11-40：增加 active world generation/identity；当前 resetFrame() 为空，不能承担 stale gate。
- core/render/render_framework.cpp:263-291：在 FrameResourceRetainer.beginFrame 后的 world/chunks/entities reset 边界冻结/清空 world sink batch。
- core/render/modules/world/ray_tracing/submodules/world_prepare.cpp:191-240：在 TLAS instance 前过滤 stale generation，按 per-object coordinate/origin 计算 transform。
- common/shared.hpp:118-168、core/vulkan/vertex.cpp:5-41、core/render/vertex_formats.cpp:152-174：如需 ABI 扩展，保持现有 PBR coordinate/packedData 兼容。

## 8. Scope 异常退出

已用 try-with-resources 覆盖：

- RenderLevelStageCompatibilityMixins.java:30-40：eventBus.post 异常也关闭 scope。
- DimensionSpecialEffectsCompatibility.java:30-33：dimension callback 异常也关闭 scope。
- RenderCaptureContract.java:124-149：close 校验 owner thread/LIFO。
- EntityProxy.java:389-401、:472-487、:854-864、:1106-1127、:1224-1235：若干 entity/particle/transform/highlight 子路径已有 finally。

RETURN-only 风险：

- CreateValueBoxMixins.java:21-24 在 ValueBox.render HEAD begin，:46-49 只在 RETURN finish；Create ValueBox.java:75-102 中 outline/contents 等调用抛异常会跳过 finish，CAPTURES 和 provider 残留。
- GameRendererMixins.java:215-243 进入 GUI scope，:245-251 只在 RETURN 关闭。
- GuiRenderScopeCompatibilityMixins.java:21-37 和 ScreenRenderScopeCompatibilityMixins.java:20-36 都是 HEAD/RETURN。
- WorldRendererMixins.radiance$renderLevel.java:138-335 没有总 finally；queueBlockEntitiesRebuild.java:664-764 的 blockEntityRenderDispatcher.render 也没有每 provider 的异常清理。

因此 world sink 的 beginStage/endStage 必须在同一同步调用栈 try/finally 中；frame abort 需要清空待提交 batch，异常继续向上，不吞成成功。

## 9. 明确不能从当前 API 恢复的状态

- 在 BufferUploader._drawWithShader(MeshData) 处，RenderType/shader/output/depth/cull/target 已不可恢复；必须在 RenderType.draw 或 provider endBatch 捕获。
- MeshData 没有 world/dimension identity、stage、frame/resource generation。
- 顶点 bytes 没有 identity PoseStack、-camera、pos-camera、camera local 的 provenance；双重相机变换无法从最终 float 反解。
- 已关闭的 MeshData.indexBuffer 无法恢复 quad sort；按 mode 重建只适用于没有真实 index 的情况。
- 未声明的 TRIANGLE_FAN/未知 VertexFormat 不能安全重解释。
- raw renderSky MeshData 没有普通 world origin；只能另建 SKYBOX semantic，不能假定 TLAS world entity。
- event post、callback boolean、UI route、ShaderRegistry successful-use log 不能作为 native world submission 证据。

## 10. 推荐最小场景

1. Create AFTER_PARTICLES：stage stack 对齐 vanilla identity；Create 自己一次扣 camera；RenderType.draw 捕获 layer；以 CAMERA_SHIFT 提交 camera-relative geometry；保留真实 index；EntityProxy.build 后走 Entities buffer/BLAS/TLAS。
2. AFTER_ENTITIES/AFTER_WEATHER listener：producer 明确 WORLD 或 CAMERA_SHIFT；每层携带 RenderType/material；direct Tesselator + BufferUploader 无 RenderType 时保持 unsupported。
3. Dimension clouds/weather：callback boolean 只有 vanilla suppression 语义；accepted native count 独立；没有坐标/RenderType 时明确降级。
4. ValueBox exception：method-level finally 关闭 capture/provider/源 MeshData；disconnect/re-entry 递增 generation，旧 token 不能写当前 singleton world。

交付状态：源码证据和最小契约已明确；当前 checkout 仍没有通用 world-stage MeshData sink。动态验证、本轮实现和 GL/FBO target 架构留给后续工作。
