# RenderTarget 真实 attachment storage 与 filter 路径

## 目标与依据

- 目标类：Minecraft 1.21.1 `com.mojang.blaze3d.pipeline.RenderTarget`。
- 原 `createBuffers` 在 NO_API 环境仍直接调用 `_texImage2D/_texParameter`；原私有 `setFilterMode(int, boolean)` 也只修改 GL texture parameters。
- 本轮新增 `RenderTargetStorageMixins`，在两个方法 HEAD 注入并仅在真实 Vulkan 路径完成后 cancel 原方法。
- 未修改 `MainTarget`；它使用自身 `allocateAttachments/createFrameBuffer` 路径，不调用本 mixin 覆盖的 `RenderTarget.createBuffers`。

## createBuffers 行为

1. 保留 `RenderSystem.assertOnRenderThreadOrInit` 与 `RenderSystem.maxSupportedTextureSize` 尺寸验证。
2. 先写入 `viewWidth/viewHeight/width/height`，再分配真实 native framebuffer 名称与 color texture 名称。
3. color storage 使用 1 mip 的 `GL_RGBA8`，随后设置 Vulkan nearest filter、nearest mip mode 与 clamp-to-edge。
4. `useDepth` 时创建独立 depth texture：普通深度使用 `GL_DEPTH_COMPONENT32F`；`stencilEnabled` 时使用 `GL_DEPTH32F_STENCIL8`。depth sampler 使用 nearest + clamp。
5. 挂 `COLOR_ATTACHMENT0`；普通深度挂 `DEPTH_ATTACHMENT`。NeoForge stencil 路径按 `useCombinedDepthStencilAttachment` 分别挂 combined attachment，或把同一 D32F_S8 image 分挂 depth 与 stencil。
6. 读取 `FramebufferProxy.checkStatus(FRAMEBUFFER)`；只有真实返回 `GL_FRAMEBUFFER_COMPLETE` 才继续。
7. 状态成功后调用原 `clear(getError)` 与 `unbindRead()`，保留 clear color、depth、bind/unbind 和 viewport 语义。

整个实现没有 `_texImage2D`、`_texParameter` 或合成 COMPLETE。

## filterMode 行为

- 私有 `setFilterMode(int, boolean)` 在 HEAD 被接管。
- 保留 `force || newMode != filterMode` 门控；只有 native `TextureProxy.setFilter` 成功后才更新字段。
- `GL_NEAREST` 映射 `VK_FILTER_NEAREST`，`GL_LINEAR` 映射 `VK_FILTER_LINEAR`；RenderTarget 只有单 mip，mipmap mode 保持 nearest。
- 未分配 color attachment 或未知 filter enum 会显式失败，不再落入 OpenGL 调用。

## metadata 与所有权

- color attachment 以 RGBA8/4 channel/level 0 写入 `TextureTracker.GLID2Texture`，保留正常纹理尺寸/format metadata。
- depth 与 depth-stencil 不写成 `TextureTracker.Texture(RGBA)`；它们不作为普通 PBR 世界材质注册。
- `destroyBuffers` 未覆盖：仍沿用目标类的 TextureUtil release、FBO delete 和 bind/unbind 契约；相关 GlStateManager hooks 由根任务接真实 `FramebufferProxy`。
- 新 `RenderTargetAllocationCleanup` 只承接失败回滚：先解绑 FBO，再释放 depth、color，最后删除 FBO。任一步清理失败仍继续处理其余资源，并把后续异常 suppressed 到首个清理失败。
- mixin 捕获 allocation/status/clear 阶段的 RuntimeException/Error；先把三个字段恢复 `-1`，执行回滚，回滚异常附加到原始分配失败后重新抛出原失败。

## 变更文件

- `src/main/java/com/radiance/mixins/vulkan_render_integration/RenderTargetStorageMixins.java`
- `src/main/java/com/radiance/client/render/RenderTargetAllocationCleanup.java`
- `src/test/java/com/radiance/client/render/RenderTargetAllocationCleanupTest.java`
- `src/main/resources/radiance.mixins.json`：注册新 client mixin。

## 验证

- `compileJava`：PASS，12 秒。
- 定向 `RenderTargetAllocationCleanupTest`：3/3 PASS；覆盖完整释放顺序、未分配资源跳过、单步失败后继续释放并重抛首错。
- `git diff --check`：PASS；staged paths 为 0。
- 证据摘要：`Evidence/render-target-storage-verification.txt`。

## 尚未覆盖

- 未启动游戏、GUI 或真实 GL/Vulkan 窗口；mock/callback 单测只证明失败资源所有权，不证明 attachment GPU 像素结果。
- MainTarget/default target 的真实 storage、默认 framebuffer snapshot、readback、custom target 绘制切换及 blit 由根任务继续集成。
- 本轮未移除 Ponder 的取消初始化路径；需等其消费者接到已验证的真实 target 后再处理。
- 未把 framebuffer color/depth attachment 当作普通 PBR 世界材质，也未实施 Veil/direct-GL 的完整消费者验收。

