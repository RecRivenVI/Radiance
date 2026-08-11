# MainTarget 默认目标与纹理别名进度

## 计划中的所有权

- Java `MainTargetCompatibilityMixins` 拦截私有 `createFrameBuffer(II)`：调用真实构造后的 MainTarget，固定 `frameBufferId=0`，分配非零 color/depth texture 名称并注册 native frame alias。
- `RenderTargetStorageMixins.createBuffers` 遇到 MainTarget 时转给同一默认目标初始化接口，保证 resize 后仍是默认 FBO 0，不退化成普通离屏 FBO。
- `MinecraftClientMixins` 移除 Unsafe allocateInstance，以及 main target clear-color/clear/bind/resize 的旧跳过；保留 submit/present 与屏幕 blit 的现有 Vulkan 时序接管。
- `Textures` 拥有 alias name、alias kind 与 sampler；0 仍只表示无纹理。alias 不进入 world/PBR pipeline，只通知 UI texture array。
- `UIModule` 拥有每 frame 的真实默认 color/depth attachment、attachment view、alias sampled mirror 与 descriptor revision。每个 frame 只在其 fence 已回收后的 begin 阶段克隆 dirty descriptor table，不广播更新所有 in-flight sets。

## 计划接口

- Java/Native `FramebufferProxy.configureMainTargetAliases(colorId, depthId, width, height)`。
- Java/Native `FramebufferProxy.defaultStencilAvailable()`。
- `Textures::registerFrameAlias(id, MainColor/MainDepth)`、`frameAliasKind(id)`，alias release 绕过普通 fallback image 约束。
- `UIModule::bindFrameAlias/unbindTexture/prepareOverlayDescriptorTable(frameIndex)`。
- `UIModuleContext::writeMainDepth(worldPipelineContext)`：从实际 PostRender `firstHitDepthImage` 读取，并按当前 `WorldUBO.cameraProjMat` 转成 Vulkan 0..1 投影深度写入默认 depth attachment。

## 深度与采样方案

- first-hit 输入是 R16F linear view depth；不使用现有 `linearDepth/1000` 的 world-post depth。
- 新 fullscreen shader 用 `textureSize` 与目标 `gl_FragCoord/targetSize` 做 normalized source lookup，允许输入和默认 target 尺寸不同。
- `viewZ=-linearDepth`，以 projection matrix 的 `P[2][2], P[3][2], P[2][3], P[3][3]` 计算 `clipZ/clipW`，无效/天空值归远平面 1。
- 默认 depth 优先使用支持 sampled+depth/stencil attachment+transfer 的 D32F_S8，并创建 combined attachment view与depth-only sampled view；不支持时退回 D32F，Java `enableStencil` 不得把状态改为 true。
- main alias 采样绑定每-frame sampled mirror。默认 pass 结束后 copy 最新 color/depth，避免在同一 image 作为 attachment 时形成 descriptor feedback，也避免把 current frame image广播给其他 in-flight descriptor sets。

## 需要根协调的一行 hook

- 本任务不会自行修改 `pipeline.cpp`。完成 `UIModuleContext::writeMainDepth(...)` 后，请根在 `PipelineContext::fuseWorld()` 的 color blit/最终布局恢复之后、函数返回之前调用：
  `uiModuleContext->writeMainDepth(worldPipelineContext);`

## 当前状态

- [x] 已核 Unsafe MainTarget、RenderTarget resize/destruction、fuseWorld color 时序、first-hit depth来源与现有 descriptor 更新风险。
- [x] alias 与 descriptor revision。
- [x] 默认 D32F/D32F_S8 attachment/sample mirror。
- [x] first-hit → projection depth pass。
- [x] Java MainTarget 构造/resize/stencil 与 Unsafe/skip 清理。
- [x] owned-source compile、Java compile、无窗口 GPU shader test。
- [x] 根任务整 core 与 Java test/bootstrapTest 集成验证。
- [ ] 最终分发验证与游戏/画面验收。

## 已实施

- `Textures` 新增 MainColor/MainDepth frame alias；alias 只绑定 UIModule texture array，不进入 WorldPipeline/PBR。alias sampler 可继续响应 RenderTarget filter/clamp，删除 alias 不依赖普通 texture fallback image。
- `UIModule::bindTexture` 不再原地广播修改所有 frame descriptor sets。normal texture/alias变化只增加 dirty revision；当前 frame fence 回收后在 `begin()` 克隆并填充该 frame 的 descriptor table，旧 table 进入 `FrameResourceRetainer`。
- 默认 color/depth alias 各有每-frame sampled mirror。默认 pass结束、以及 fuseWorld color/depth写入后复制最新 attachment，descriptor 永远指向 mirror，从而避免同一 image 同时作为 color/depth attachment 与 sampler 的 feedback。
- 默认 depth 在设备支持 sampled+DS attachment+transfer 的情况下使用 D32F_S8，并以独立 combined attachment view建 RenderPass/Framebuffer；普通 sampler仍使用 depth-only默认view。能力不足时回退D32F，Java MainTarget `enableStencil()`会明确失败且不会把 `stencilEnabled` 改成true。
- 新 `overlay/main_depth.frag` 读取PostRender `firstHitDepthImage`，用实际source `textureSize`和目标normalized坐标采样，再按WorldUBO projection矩阵转换成Vulkan投影depth。原有linear-depth与world-post depth路径未改。
- 根已在 `PipelineContext::fuseWorld()` 最终color布局恢复后调用 `writeMainDepth()` 和 `captureMainAliases()`；没有后续UI draw时，alias也包含本帧world color/depth。
- Java `MainTargetCompatibilityMixins`拦截真实构造内部的`createFrameBuffer`：FBO名称固定0，color/depth为非零native alias名称。`RenderTargetStorageMixins`识别MainTarget resize并重建alias，不创建普通离屏FBO。
- `MinecraftClientMixins`已移除Unsafe allocateInstance、main clear-color/clear/bind/resize旧跳过；保留submit/present和现有屏幕blit接管。
- `radiance.mixins.json`保留WorldMesh三条新注册，并追加MainTarget mixin。

## 已验证

- Java `compileJava`（BellSoft JDK 21）：PASS。未固定JAVA_HOME时曾命中外部JDK25/major69的Gradle脚本兼容错误；固定项目JDK后通过，不是源码错误。
- owned native sources：`ui_module.cpp`、`ui_framebuffer.cpp`、`textures.cpp`、FramebufferProxy middleware独立Release defines/includes编译PASS；日志 `Evidence/main-target-owned-compile.log`。
- `main_depth.frag` glslang Vulkan 1.4编译PASS。
- RTX 4080 SUPER/Vulkan 1.4.351 无窗口GPU：first-hit R16F 2x2取样到default depth 4x4，四组linear depth按投影矩阵转出的GPU D32值与CPU clipZ/clipW reference一致；同时前四条framebuffer GPU case保持PASS。日志 `Evidence/main-depth-gpu-build.log`、`Evidence/main-depth-gpu-run.log`。
- 根任务统一 Release core 集成构建：PASS，已包含 `writeMainDepth` 与 `captureMainAliases` 两个 fuse hook；原始日志 `Evidence/public-native-checkpoint-build.log`。产物 `build/src/core/Release/core.dll` 为 30,763,520 bytes，SHA-256 `C1F2A7E0A971BD0C890795E9BC4B81C7FEB9F87CE97AAF29CDB5AB466281C0C0`。
- 根任务 Java `test` 与 `bootstrapTest`：PASS。

## 残余边界

- Main alias直接作为custom FBO texture attachment尚未承接；当前MainTarget本身使用默认FBO0，alias用于安全采样。自定义FBO仍要求`prepareAttachmentTexture`分配的真实attachment texture。
- MainTarget color/depth `downloadTexture`/readback仍由根后续接入；alias registry不把某一个current frame image伪装成全局普通texture。
- `writeMainDepth`依赖包含PostRenderModuleContext的world pipeline；无world/post context时保留已clear的默认depth并安全返回。
- 未运行游戏/GUI，尚未验证真实Minecraft构造时序、resize、enableStencil调用方、alias shader消费或画面遮挡；GPU测试是shader/原语级，不是Java/JNI端到端。
- 不同depth格式blit shader resolve、fractional clipping/scissor与diagram嵌套target恢复仍不在本子任务范围。

## 冻结状态

- MainTarget/alias/default-depth相关source已停止修改；无stage/commit/push。
- 根已转入公共持久raster VertexBuffer/Shader applied-state，本任务不再修改ShaderProxy或UI。
