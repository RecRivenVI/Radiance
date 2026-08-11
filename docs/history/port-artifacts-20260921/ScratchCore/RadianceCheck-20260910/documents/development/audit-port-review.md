# Radiance 渲染与资源独立复核

> 当前项目名称统一为 Radiance。本文保留 2026-09-10 审计快照、结论和历史引用；其中旧称谓仅说明当时语境，不定义当前身份。后续工程与历史整合见 [当前工程记录](migration-template-configuration.md)。

本页只记录 C 范围（render stage capture/drop、GL/FBO 仿真/读回/截图/后处理、spectator/blur/outline/全屏效果、shader module/config 资源引用与消费、material/texture 生命周期和队列依赖）的源码证据。审计输入为当前 `stonecutter@9fe7811cf8b27f97b8b77307e982e3cc3942aee5` 脏工作树、封存的 `D:\Workspaces\Artifacts\OldPortAudit\20260910\Radiance-start.json`（SHA-256 `713715eb775c3a803a30cf2147b81d46cf01060d935da54cb76ef07991d304c5`），以及 Radiance `414d8e330a2fc6cb1e8630cc95f2302b2b97a0e8` / MCVR `9905c81b1999f5845bf66d13501d371c16adf561` 的只读 extract。未构建、未测试、未启动客户端；以下“确认”仅指静态路径/调用/资源事实，不等于运行或视觉验收。

## R-01：White Ash 上游特定顶点修正未保留（P-11 已完成版本反证，目标仍待决定）

- 旧路径/符号：Radiance `414d8e3` 的 `src/main/java/com/radiance/mixins/vulkan_render_integration/BillboardParticleMixins.java`，`@Mixin(BillboardParticle.class)` 在 `method_60374(...` HEAD 对 `WhiteAshParticle` 重写四个顶点，把两个尺寸参数固定为 `1/8`，随后 `ci.cancel()`。
- 当前路径/条件：`versions/1.21.1-neoforge/src/main/resources/radiance.mixins.json` 未注册 `BillboardParticleMixins`；当前 `versions/1.21.1-neoforge/src/main/java` 没有该类或同名替代。粒子主路径 `client/proxy/world/EntityProxy.java:1004-1077` 对普通四种 `ParticleRenderType` 直接调用 `particle.render(vertexConsumer, camera, tickDelta)`，没有 `WhiteAshParticle` 分支；自定义类型在 `:1086-1148` 也没有该尺寸重写。
- 输出副作用/风险：White Ash 的 vanilla billboard 顶点直接进入 PBR capture，静态上没有旧 port 的缩小几何修正；影响粒子尺寸/位置的世界光栅结果和反射/透明光线输入。P-11 已以准确的 1.21.1/1.21.4 原生类链反证两版 vanilla 都没有该 `1/8` 顶点修正，故可确认上游特定缩小/`light=0` 覆写未保留；不是整个 White Ash 粒子不渲染，也不是视觉验收失败。是否保留上游风格仍属产品目标决策，最终像素/光照仍待动态验证。

## R-02：`ClientChunkManager.onLightUpdate` 取消分支未移植，是否由 NeoForge 主动轮询替代尚未闭合

- 旧路径/符号：Radiance `414d8e3` 的 `src/main/java/com/radiance/mixins/vulkan_render_integration/ClientChunkManagerMixins.java` 在 `ClientChunkManager.onLightUpdate(LightType, ChunkSectionPos)` HEAD 直接取消回调。
- 当前路径/条件：当前资源配置未注册对应 mixin，当前 Java 也不存在同名类/`onLightUpdate` 注入。当前 `versions/1.21.1-neoforge/src/main/java/io/github/recrivenvi/radiance/mixins/vulkan_render_integration/WorldRendererMixins.java:160-161` 改为每帧显式调用 `level.pollLightUpdates()` 和 `level.getChunkSource().getLightEngine().runLightUpdates()`；`ChunkProxy` 的 rebuild 队列（`:191-343`）和 `BufferProxy.updateWorldUniform` 使用当前光照状态。
- 判定：确认旧取消接线不存在；不能仅凭此把它判为功能缺失，因为 NeoForge 的显式 light-engine 轮询可能是有意替代。需要真实同版本光照更新/区块重建场景，才能判定重复更新、漏更新或性能回归。

## R-03：CloudRenderer mixin 缺失，但已找到当前 CloudProxy 替代；不把搬迁误判为删除

- 旧路径/符号：Radiance `414d8e3` 的 `src/main/java/com/radiance/mixins/vulkan_render_integration/CloudRendererMixins.java` 在 `CloudRenderer.renderClouds(...)` HEAD 取消 vanilla cloud renderer，并把 cell mask 生成的云面放入 `StorageVertexConsumerProvider` / `EntityProxy.processWorldEntityRenderData`；其构造和 `close()` 也阻止 vanilla `VertexBuffer` 创建/销毁。
- 当前路径/条件：当前 `radiance.mixins.json` 不再注册 `CloudRendererMixins`，但 `versions/1.21.1-neoforge/src/main/java/io/github/recrivenvi/radiance/client/proxy/world/CloudProxy.java:65-122` 在 `WorldRendererMixins.java:314-325` 的非 OFF、非自定义维度云分支被调用；`CloudProxy.reloadCells`（`:124-160`）从 vanilla `textures/environment/clouds.png` 解码 mask，`rebuild`（`:166-182`）生成 `radiance_clouds` PBR 几何并提交；资源重载回调 `ReloadableResourceManagerImplMixins.java:43-51` 标记 mask dirty，`LevelRenderer.close` 注入 `WorldRendererMixins.java:345-348` 关闭缓存。
- 判定：旧 mixin 的物理路径不存在，但云能力有当前 Java 侧替代路径，不能记为确认缺失。仍未动态验证 translucent/fancy、维度自定义云、重载后缓存和关闭时序。

## R-04：基础纹理销毁没有回收 `_s/_n/_f` 辅助 Vulkan 纹理

- 分配路径：`versions/1.21.1-neoforge/src/main/java/io/github/recrivenvi/radiance/client/texture/AuxiliaryTextures.java:157-185` 为每个 base texture ID 懒分配 specular/normal/flag 各一个新 ID，并在 `TextureTracker.GLID2{Specular,Normal,Flag}GLID` 保存映射；这些 ID 通过 `TextureUtil.prepareImage` 进入 native `Textures::textures_`/`samplers`。
- 销毁路径：`versions/1.21.1-neoforge/src/main/java/io/github/recrivenvi/radiance/client/proxy/vulkan/TextureProxy.java:38-45` 只对传入的 base ID 调 `releaseTextureIdNative(id, fallbackId)`；`versions/1.21.1-neoforge/src/main/java/io/github/recrivenvi/radiance/client/texture/TextureTracker.java:17-23` 只删除 Java 映射和 sidecar 引用，没有返回或释放辅助 ID。唯一 native 释放实现 `components/vulkan-renderer/src/core/middleware/io_github_recrivenvi_radiance_client_proxy_vulkan_TextureProxy.cpp:18-25` 也只调用 `Textures::releaseTexture(baseId, fallbackId)`；`components/vulkan-renderer/src/core/render/textures.cpp:58-92` 只 erase 该单一 `id`，没有遍历辅助映射。
- 输出副作用/风险：base `AbstractTexture.releaseId`（`versions/1.21.1-neoforge/src/main/java/io/github/recrivenvi/radiance/mixins/vulkan_render_integration/AbstractTextureMixins.java:39-53`）或 GL delete redirect（`GlStateManagerMixins.java:138-153`）后，辅助 image/sampler 仍留在 native map/GPU；Java 映射虽被删，故后续没有可达回收入口。只要 base ID 真正进入 release（例如纹理对象销毁/重建），每个 base 最多留下三份孤儿资源；单纯 reload 若复用 base ID 只会 prepare/替换，不应直接推断每次都会增长。静态回收链断裂已确认；renderer reset/进程结束时的整体清理不能抵消进程内已释放 base 的累积，未动态量化泄漏速率。
- 归因边界（P-12）：`N-U=9905c81` 本身没有当前 base release 实现；`R-P=cd67362` 已有 Java 侧只 release base 的局部链，`N-P=b611d24` 已有 native 单 ID release，因此不是 ordinary component 搬迁把完整上游释放删掉。`Textures::reset()` 虽能清全体 map，但重载 begin/end 不调用它；同 ID prepare 的 retained 资源替换也不等于回收已失去 base 所有权的 aux。

## R-05：普通截图读回尺寸取了逻辑窗口，而 Vulkan 源图是 framebuffer 尺寸

- 当前读回路径：`versions/1.21.1-neoforge/src/main/java/io/github/recrivenvi/radiance/mixins/vulkan_render_integration/ScreenshotRecorderMixins.java:22-33` 和 `client/proxy/vulkan/RendererProxy.java:147-159` 都用 `Window.getScreenWidth()/getScreenHeight()` 创建 `NativeImage`，再把相同尺寸传到 `RendererProxy.takeScreenshot`；`NativeImageMixins.java:192-196` 的无 UI 路径也把这个 image 尺寸传给 native。
- 尺寸消费证据：同一当前 target 的 `MinecraftClientMixins.java:115-136` 为 MainTarget 字段取 `Window.getWidth()/getHeight()`，而 native `components/vulkan-renderer/src/core/render/render_framework.cpp:465-483` 用 `GLFW_GetFramebufferSize` 重建 swapchain，`UIModule`/world images 由该 swapchain extent 创建（例如 `components/vulkan-renderer/src/core/render/modules/ui_module.cpp:549-561`）。当前 1.21.1 `Window` 中 `getScreenWidth()/getScreenHeight()` 是逻辑窗口尺寸而 `getWidth()/getHeight()` 是 framebuffer 像素尺寸；主代理 P-14 已用准确的 1.21.1/1.21.4 Window 源码与映射核实：上游 ScreenshotRecorder 取到的 Yarn `getWidth()/getHeight()` 仍是 logical 尺寸。因此这是两版上游继承的 logical-vs-framebuffer 缺陷，不是字段改名或版本语义未决。
- 输出副作用/风险：`Framework::takeScreenshot`（`components/vulkan-renderer/src/core/render/render_framework.cpp:656-675`）要求 `width*height*channel` 恰好等于 Vulkan image buffer 大小，否则返回 `VK_ERROR_FORMAT_NOT_SUPPORTED`；因此 HiDPI 或任何 framebuffer/window 尺寸不一致的机器上，截图会在 Java `checkVkResult` 处失败，无法读回当前世界/UI 帧。普通 1:1 缩放静态上可通过，实际截图时序和缩放矩阵仍待客户端验证。

## R-06：NeoForge 渲染阶段事件的世界 MeshData 被当前协议主动丢弃

- 当前路径/条件：`versions/1.21.1-neoforge/src/main/java/io/github/recrivenvi/radiance/mixins/vulkan_render_integration/RenderLevelStageCompatibilityMixins.java:21-40` 仅将 `ClientHooks.dispatchRenderStage` 包在 `RenderCaptureContract.ScopeKind.WORLD_STAGE`；`WorldRendererMixins.java:239-333` 触发 AFTER_SKY、地形层、AFTER_ENTITIES、AFTER_BLOCK_ENTITIES、AFTER_PARTICLES、AFTER_WEATHER。随后 `BufferRendererMixins.java:22-61` 对非 GUI 的 `MeshData` 调用 `reportDiscard`、关闭 buffer 并取消原调用；`RenderCaptureContract.java:64-83` 明确把 WORLD_STAGE/DIMENSION_EFFECT 分类为 `REJECT_WORLD_MESH`。
- 输出副作用/风险：第三方 stage listener 若实际提交普通世界顶点，`reportDiscard` 会按 operation/scope/label 首次记录一次去重告警，随后 buffer 被关闭且原调用被取消；因此不是无提示成功，而是告警后没有 Vulkan 世界几何、材质或 TLAS，结果仍可丢帧。这不是把上游旧类名缺失误报为回归，而是当前协议的可见降级边界。现有源码未提供该 listener 已被等价转译为 Entity/Cloud/priority 路径的注册或调用证据。需用代表性 mod stage listener 动态确认是否在产品支持范围内。

## R-07：OpenGL FBO 兼容层伪造成功并吞掉真实附件/读回

- 当前路径/条件：`versions/1.21.1-neoforge/src/main/java/io/github/recrivenvi/radiance/mixins/vulkan_render_integration/OpenGlFramebufferCompatibilityMixins.java:29-108` 对 bind/blit/renderbuffer/delete/storage/attachment 等入口在 HEAD 取消并 `reportDiscard`，`glGenFramebuffers`/`glGenRenderbuffers` 返回 `0x60000000` 起的合成 ID，`glCheckFramebufferStatus` 固定返回 `GL_FRAMEBUFFER_COMPLETE`，`getBoundFramebuffer` 固定返回 0。与 `GlStateManagerMixins.java:15-272`、`GlBooleanStateMixins.java:20-59` 的状态镜像/GL no-op 组合后，调用方可观察到“complete”，但没有真实 FBO、颜色附件或 Vulkan 读回目标。
- 输出副作用/风险：遗留或第三方路径可能继续执行；FBO 兼容入口会按 operation/scope/label 首次记录一次去重告警，但随后仍伪造 `COMPLETE` 并丢弃真实操作，可能在后续读回处失败。这不等同于真实 OpenGL fallback。当前没有找到普通目标代码把这些合成 FBO 映射为 Vulkan attachment，也未找到覆盖非 vanilla 调用方的明确拒绝合同。需动态调用一个实际 FBO 使用者，判定它是否只影响明确不支持的 OpenGL 路径。

## 已审覆盖、未知与动态前提

本单元已静态查阅 Java/native 两侧的 render-stage capture/drop、GL/FBO 兼容层、截图/读回/后处理、spectator/outline/blur/full-screen effect 入口、shader module/config 的路径与 CMake 打包消费、纹理 auxiliary/base 生命周期及 reload begin/end/queue 等；另核对上游 Radiance 与历史 MCVR 的对应符号。已落盘条目 R-01～R-07，其中 R-03 的 CloudRenderer 缺失已确认由当前 `CloudProxy` 注册/调用/资源 reload 路径替代，不能按同名文件缺失判定。

未作客户端、HiDPI、实际 NeoForge stage listener、非 vanilla FBO 调用方、shader reload 后资源峰值、纹理 base 真正 release 后 native aux 是否可见残留等动态验证；未将源码存在、CMake 配置或普通构建等同于用户可见通过。R-01 的最终产品风格/视觉仍待决定，R-02 的光照回调等价性与 R-05 的实际 HiDPI 错误触发仍待动态验证；R-06/R-07 是当前协议的静态边界/风险观察，不宣称全功能通过。若继续取证，应以代表性第三方 stage/FBO 调用、HiDPI screenshot、reload→release→reuse 序列和动态 shader source 变更为最小场景，并核对 native validation/日志中的实际 discard、错误码和资源计数。
