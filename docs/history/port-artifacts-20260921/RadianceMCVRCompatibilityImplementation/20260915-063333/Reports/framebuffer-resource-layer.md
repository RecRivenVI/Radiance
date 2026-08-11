# MCVR Framebuffer 真实资源层

## 范围与所有权

- 新增 `src/core/render/framebuffers.hpp/.cpp`，由 `Renderer` 在 `Framework`、`Textures` 之后创建，并在 `Textures` 之前释放。
- 修改 `Textures` 只增加 attachment 专用 storage/resolve 能力及 0-name 修正；普通 sampled texture 初始化仍走原入口。
- 新增 `tests/framebuffer_contract_test.cpp` 与 `mcvr.framebuffer-contract` CTest。
- 未修改 Java、JNI middleware、`ui_module.*`、Vulkan `Image` wrapper 或其他业务模块；根任务负责默认目标、JNI、动态 render pass/framebuffer、clear/blit/readback 与活跃 pass 协调。

## 公开 API

`Framebuffers`：

- `allocateFramebuffer/deleteFramebuffer`
- `allocateRenderbuffer/deleteRenderbuffer`
- `bindFramebuffer(target, id)`、`bindRenderbuffer(id)`
- `readFramebufferBinding/drawFramebufferBinding/renderbufferBinding`
- `framebufferTexture(target, attachment, textureId, level)`
- `framebufferRenderbuffer(target, attachment, renderbufferId)`
- `renderbufferStorage(id, VkFormat, width, height, samples) -> StorageResult`
- `setDrawBuffers(target, span)`、`setReadBuffer(target, attachment)`
- `checkStatus(target) -> raw GL status`
- `snapshot(target) -> Snapshot`

`Snapshot` 包含 FBO id/status、extent/sample count、全部已挂 color attachment、合并后的 depth/stencil attachment、原始 draw buffer slots、按 slot 对齐的 optional draw colors，以及 read buffer/read color。每个 resolved attachment 包含真实 `shared_ptr<DeviceLocalImage>`、texture sampler（renderbuffer 为 null）、attachment 专用 `viewIndex`、mip level、format、aspect、extent 与 samples。

`Textures`：

- `initializeAttachmentTexture(id, levels, width, height, format, aspectMask)`
- `attachmentTexture(id, level, requiredAspect) -> optional<AttachmentImage>`

## 资源语义

- Framebuffer、renderbuffer、texture 三个名字空间相互独立；0 只表示默认目标、未绑定或脱附。Framebuffer/renderbuffer 从 1 起，Textures 的分配与 reset 也从 1 起。现有 Java PBR/Shader 消费者把 texture 0 当无纹理 sentinel，未发现依赖真实 texture 0 的 native consumer。
- 新 FBO 默认 draw/read buffer 都是 `COLOR_ATTACHMENT0`。MRT 只按 `setDrawBuffers` 明确选择的 slots 输出；Snapshot 不会把所有已挂 color 自动标为 active。
- 纹理 attachment 保存 texture ID + mip level，不缓存 image/view。每次 status/snapshot 都向 `Textures` 解析当前 generation，因此纹理重建后不会继续使用旧 view。
- attachment texture 在创建真实 Vulkan image 时查询 `vkGetPhysicalDeviceFormatProperties`，要求 optimal tiling 同时支持 sampled image 与 color 或 depth/stencil attachment。
- 普通 sampled binding 继续使用 `DeviceLocalImage` 默认 view index 0。attachment storage 为每个 mip 另外创建单 level/aspect view；组合 D/S format 分别创建 depth、stencil、depth|stencil attachment view，combined view 不进入普通 sampler binding。
- Renderbuffer storage 创建真实 `DeviceLocalImage`，带 color 或 depth/stencil attachment usage；format 不支持时返回 `UnsupportedFormat`。
- Snapshot 持有强引用，并把 image/sampler 加入当前 `FrameResourceRetainer`。renderbuffer 重新分配或删除时也保留旧 image，保护已录制但尚未完成的帧。
- 删除当前绑定的 framebuffer/renderbuffer 会把对应 binding 恢复为 0。附件引用已删除/未分配资源时状态不完整，不以新同名假对象替代。

## 完整性判定

- 0/default：`GL_FRAMEBUFFER_UNDEFINED`，由根任务从当前 swapchain/UI context 提供真实默认 target。
- 无附件：`GL_FRAMEBUFFER_INCOMPLETE_MISSING_ATTACHMENT`。
- 未分配、mip/view 不存在、format/aspect 不兼容：`GL_FRAMEBUFFER_INCOMPLETE_ATTACHMENT`。
- attachment extent 不一致：`GL_FRAMEBUFFER_INCOMPLETE_DIMENSIONS`。
- sample count 不一致或不是 1：`GL_FRAMEBUFFER_INCOMPLETE_MULTISAMPLE`。
- depth 与 stencil 来自不同 image：`GL_FRAMEBUFFER_UNSUPPORTED`，因为当前 Vulkan subpass 只承接一个 depth/stencil attachment。
- active draw/read slot 没有对应 color attachment：分别返回 `GL_FRAMEBUFFER_INCOMPLETE_DRAW_BUFFER/READ_BUFFER`。
- 只有以上真实条件全部满足才返回 `GL_FRAMEBUFFER_COMPLETE`。

## 验证

- 首次加入资源层后，现有 Release core 在根任务新增 Framebuffer JNI middleware 被下一次 glob 发现之前完成全量编译和链接：PASS。后续完整 core 由根任务在 JNI header/UI 接口完成后统一重编。
- 根任务并行修改 JNI/UI 后，单独用 core 的 Release defines/include contract 编译本任务拥有的 `framebuffers.cpp`、`textures.cpp`、`renderer.cpp`：PASS；原始日志 `Evidence/framebuffer-owned-sources-compile.log`。
- `framebuffer_contract_test.cpp` 独立 MSVC C++23 编译：PASS；原始日志 `Evidence/framebuffer-contract-manual-build.log`。
- 无窗口 CPU contract 执行：PASS；覆盖 missing/unallocated/incompatible、draw/read selection、extent mismatch、sample mismatch、非 1 sample、D/S image 与 mip identity、format aspect；原始日志 `Evidence/framebuffer-contract-manual-run.log`。
- `git diff --check`：PASS；无 stage/commit/push。

## 尚未集成与限制

- 本层不创建 VkRenderPass/VkFramebuffer，不开始/结束 render pass，也不执行 clear/blit/readback；这些操作必须由根任务在当前 `UIModuleContext` command buffer 上协调。
- `DeviceLocalImage` 当前把 `VkImageCreateInfo.samples` 固定为 1。本层对其他 sample count 明确返回 `UnsupportedSamples`，且 completeness 不会静默 COMPLETE。
- 纯 `VK_FORMAT_S8_UINT` 会因共同 image wrapper 的默认 depth view 规则被明确拒绝；stencil 通过 D24S8/D32S8 组合 storage 与独立 stencil/combined view 支持。
- 尚未执行真实 attachment render/clear/blit/readback GPU 测试，也未启动游戏或 GUI；CPU contract 与 core 编译不构成画面/第三方 Veil/direct-GL 验收。
