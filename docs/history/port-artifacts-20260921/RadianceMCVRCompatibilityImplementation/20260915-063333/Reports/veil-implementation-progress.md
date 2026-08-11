# Veil 4.3.2 完整组合实施进度

## 固定输入与边界

- 目标依赖为 `veil-neoforge-1.21.1-4.3.2.jar`、Sable `2.0.5+mc1.21.1`、Aeronautics/Simulated/Offroad `1.3.2`；路径与哈希来自本轮 `Evidence/dependency-files.json`。
- 源码依据为只读 `D:\Workspaces\References\minecraft-references\veil-4.3.2`、`sable-2.0.5` 与 `aeronautics_bundled-1.3.2`。
- 不修改通用 World/Entity 链，也不修改正在由根任务持有的 ShaderProxy、VertexBuffer/BufferProxy 及 native buffers。Veil 若需要公共 shader/UBO 能力，先把精确签名交给根任务。

## 可达消费者与实现分层

1. Veil 初始化与生命周期：让 `VeilRenderSystem.renderer()` 持有真实 `VeilRenderer`，保留 manager/reload-listener/event 创建链；用 Vulkan 兼容生命周期替代 VAO、shader-buffer-cache 和 profiler 的直接 OpenGL 帧操作。
2. AdvancedFbo：保留 Veil Builder、attachment、FramebufferManager、FramebufferStack 与 RenderTarget wrapper 语义；将 Legacy/DSA 的 framebuffer、renderbuffer、texture attachment、draw/read buffer、clear、blit、free 映射到 `FramebufferProxy`。不生成假 ID，不伪造 completeness。
3. 固定组合 UI：移除 Simulated Diagram/StickyNote 对 attachment/build/bind 的空替换，让其真实 FBO 被创建、绑定和释放；既有 Vulkan 世界内容提交仍负责画入所绑定的真实目标。
4. stage：恢复 NeoForge Veil stage 事件转发与固定 RenderType flush；保持 Aeronautics 已注册的 AFTER_BLOCK_ENTITIES/AFTER_WEATHER fixed buffers 可达。
5. shader/post/light：先恢复资源与 manager 加载；实际 pipeline draw 依赖根任务正在实现的持久 raster/applied shader。任何尚未接入的 Veil ShaderProgram/UBO 阶段会明确失败或保留具体 fallback，不报告为 Vulkan 已支持。

## 计划修改文件

Radiance（本子任务所有权）：

- `compatibility/veil/VeilVulkanLifecycle.java`（新增，生命周期与真实 framebuffer 资源协调）
- `mixins/compatibility/veil/VeilRenderSystemMixins.java`
- `mixins/compatibility/veil/VeilForgeClientMixins.java`
- `mixins/compatibility/veil/VeilForgeClientEventsMixins.java`
- `mixins/compatibility/veil/VeilForgeRenderTypeStageHandlerMixins.java`
- `mixins/compatibility/veil/VeilNeoForgeEventPlatformMixins.java`
- 新增 AdvancedFbo/attachment/dynamic-buffer 专用 mixin
- `mixins/compatibility/simulated/SimulatedDiagramScreenMixins.java`
- `mixins/compatibility/simulated/SimulatedDiagramStickyNoteMixins.java`
- `radiance.mixins.json`（只追加新注册并保留其他 worker 条目）

MCVR：优先复用现有 FramebufferProxy/JNI/UIModule，不先扩大 native 接口。若 AdvancedFbo 精确语义暴露缺口，再在修改前补充此计划并通知根任务。

## 当前限制

- 当前 framebuffer 底座明确拒绝 MSAA 与纯 S8；固定组合消费扫描会记录是否实际请求。
- Veil 可选 ImGui/debug editor 不属于固定组合主路径，仍保持无 OpenGL debug-label 行为。
- 真实游戏画面与窗口验收未获授权，不会把 Java 编译或无窗口 Vulkan 测试写成端到端画面通过。
