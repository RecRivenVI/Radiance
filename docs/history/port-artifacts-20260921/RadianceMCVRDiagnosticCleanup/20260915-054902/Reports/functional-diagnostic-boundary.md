# Radiance / MCVR 功能诊断边界

基线：Radiance 414d8e330a2fc6cb1e8630cc95f2302b2b97a0e8，MCVR 9905c81b1999f5845bf66d13501d371c16adf561。本文只做源码审查，没有构建、启动客户端、打开 GUI 或修改仓库。native instrumentation 的清理由 native worker 处理；本文裁决 TimerQuery、F3 信息、SERVICE/GAME bootstrap API 及其测试边界。

## 直接裁决

| 范围 | 当前性质 | 建议 | 删除损失 |
| --- | --- | --- | --- |
| TimerQuery 三个 Mixin 与 RendererProxy GPU profile API | Minecraft TimerQuery 的 Vulkan 适配和真实 GPU timestamp 读取 | 保留 | F3 的 GPU 百分比与 ClientMetricsSamplersProvider 的 GPU sampler 消失 |
| MCVR Framework GPU profile | 每帧 Vulkan command 区间的真实 GPU 计时 | 保留 | 上层只能没有数据或得到 0，不能得到 GPU duration |
| RendererProxy.warmupCurrentPipeline | 首帧前重建当前 native pipeline 的有效业务逻辑 | 保留 | 当前 pipeline blueprint 可能在首帧尚未重建 |
| RendererDiagnostics.backendString 与 GlStateManager getString 路由 | Minecraft F3/system report 的 vendor、renderer、version 来源 | 保留 | F3 Display 信息失效或变成错误 OpenGL 假值 |
| RendererDiagnostics.captureOpenGlStrings | 当前源码只有定义，没有调用者 | 待删除（确认无外部调用后） | 当前源码无可见消费者；不能连带删除 backendString |
| NativeRuntime.identities | Provider 生产 handoff 的窗口身份校验；测试还检查五个 native handle 稳定 | 保留 | 失去错误窗口交接检测和 releaseLoading 生命周期断言 |
| BootstrapState.nativeIdentities | 当前静态搜索只有无调用包装 | 待删除（确认无外部 SPI 调用后） | 不影响 Provider 直接调用 NativeRuntime.identities |
| NativeRuntime.captureCanvas | 两个 bootstrapTest 硬件测试的真实 Vulkan canvas readback | 保留测试接缝 | 失去像素 parity 与 handoff/resize canvas 验收；迁移到 test-only native seam 后才可移除生产声明 |
| NativeRuntime.initialize/uploadTexture/renderFrame/handoff/releaseLoading/closeBeforeGame | 早期画面、同窗口 ownership、GAME handoff、资源释放 | 保留 | 早期 loading 或 GAME frame ownership 失效 |
| NativeRuntime.bindGameNatives | RadianceClient 为所有 GAME native owner 做动态 JNI 注册 | 保留 | GAME 侧 Renderer、Buffer、Texture、World 等 JNI 无法绑定 |

## TimerQuery 到 F3 的完整链

目标 Minecraft 的 TimerQuery 在 beginProfile/endProfile 中创建、开始和结束 OpenGL query，在 FrameProfile.isDone/get 中读取纳秒结果；没有 GL_ARB_timer_query 时原版 lazy loader 返回空。证据：[TimerQuery.java](D:/Workspaces/References/minecraft-references/minecraft-1.21.1/src/com/mojang/blaze3d/systems/TimerQuery.java:19)、[TimerQuery.java](D:/Workspaces/References/minecraft-references/minecraft-1.21.1/src/com/mojang/blaze3d/systems/TimerQuery.java:29)、[TimerQuery.java](D:/Workspaces/References/minecraft-references/minecraft-1.21.1/src/com/mojang/blaze3d/systems/TimerQuery.java:52)、[TimerQuery.java](D:/Workspaces/References/minecraft-references/minecraft-1.21.1/src/com/mojang/blaze3d/systems/TimerQuery.java:60)、[TimerQuery.java](D:/Workspaces/References/minecraft-references/minecraft-1.21.1/src/com/mojang/blaze3d/systems/TimerQuery.java:73)、[TimerQuery.java](D:/Workspaces/References/minecraft-references/minecraft-1.21.1/src/com/mojang/blaze3d/systems/TimerQuery.java:85)。

当前三个 Mixin 是同一个功能适配，不能按“日志探针”拆除：

- [TimerQueryLazyLoaderMixins.java](D:/Workspaces/Repositories/GitHub/RecRivenVI/Radiance/src/main/java/com/radiance/mixins/vulkan_render_integration/TimerQueryLazyLoaderMixins.java:9) 无条件创建 TimerQuery，使 GLFW_NO_API 下 Minecraft 仍进入原有 profile API。
- [TimerQueryMixins.java](D:/Workspaces/Repositories/GitHub/RecRivenVI/Radiance/src/main/java/com/radiance/mixins/vulkan_render_integration/TimerQueryMixins.java:12) 把 glGenQueries 换成 RendererProxy.beginGpuProfile，并跳过 glBeginQuery/glEndQuery。
- [TimerQueryFrameProfileMixins.java](D:/Workspaces/Repositories/GitHub/RecRivenVI/Radiance/src/main/java/com/radiance/mixins/vulkan_render_integration/TimerQueryFrameProfileMixins.java:11) 把 isDone/get 的 OpenGL readiness/result 换成 RendererProxy.isGpuProfileReady/gpuProfileTimeNs，并跳过 OpenGL query 删除。
- [RendererProxy.java](D:/Workspaces/Repositories/GitHub/RecRivenVI/Radiance/src/main/java/com/radiance/client/proxy/vulkan/RendererProxy.java:82) 到 86 暴露三个对应 native 方法。

Minecraft 只有在 F3 debug screen 或 metrics recorder 工作时才 begin/end profile，见 [Minecraft.java](D:/Workspaces/References/minecraft-references/minecraft-1.21.1/src/net/minecraft/client/Minecraft.java:1171) 到 1214。profile 完成后计算 gpuUtilization 并追加 fpsString 的 GPU 百分比，见 [Minecraft.java](D:/Workspaces/References/minecraft-references/minecraft-1.21.1/src/net/minecraft/client/Minecraft.java:1237) 到 1269；ClientMetricsSamplersProvider 在 TimerQuery 存在时注册 gpuUtilization sampler，见 [ClientMetricsSamplersProvider.java](D:/Workspaces/References/minecraft-references/minecraft-1.21.1/src/net/minecraft/client/profiling/ClientMetricsSamplersProvider.java:32) 到 55。

MCVR 的计时是实际 GPU timestamp，不是 CPU 日志：

1. [com_radiance_client_proxy_vulkan_RendererProxy.cpp](D:/Workspaces/Repositories/GitHub/RecRivenVI/MCVR/src/core/middleware/com_radiance_client_proxy_vulkan_RendererProxy.cpp:291) 到 305 把 sequence 连接到 Framework。
2. [render_framework.cpp](D:/Workspaces/Repositories/GitHub/RecRivenVI/MCVR/src/core/render/render_framework.cpp:45) 到 55 为每个 context 创建两个 Vulkan timestamp query；[render_framework.cpp](D:/Workspaces/Repositories/GitHub/RecRivenVI/MCVR/src/core/render/render_framework.cpp:180) 到 189 读取 valid bits 和 timestamp period。
3. acquire 阶段写 TOP_OF_PIPE timestamp，见 [render_framework.cpp](D:/Workspaces/Repositories/GitHub/RecRivenVI/MCVR/src/core/render/render_framework.cpp:250) 到 280；submit 阶段写 BOTTOM_OF_PIPE timestamp，见 [render_framework.cpp](D:/Workspaces/Repositories/GitHub/RecRivenVI/MCVR/src/core/render/render_framework.cpp:331) 到 375。这覆盖上传、world、overlay、fuse command buffers，不宣称覆盖 present。
4. 下一次 acquire 在完成 fence 后调用 completeGpuProfile，读取两个 query、处理 valid-bit wrap、乘 timestampPeriod 转纳秒并写入完成表，见 [render_framework.cpp](D:/Workspaces/Repositories/GitHub/RecRivenVI/MCVR/src/core/render/render_framework.cpp:217) 到 263 与 693 到 751。
5. 无 query pool、无 context 或已有 profile sequence 时，beginGpuProfile 将 sequence 以 0 放入完成表，保证 API 可返回但代表没有硬件测量，见 [render_framework.cpp](D:/Workspaces/Repositories/GitHub/RecRivenVI/MCVR/src/core/render/render_framework.cpp:693) 到 718。

旧审计 RJ-45 将 TimerQuery、GPU profile 和 RendererProxy warmup 一起归入“排障遥测、明确待全部移除”。当前源码证明该归类过宽：TimerQuery 是 Minecraft API 适配，timestamp 是真实功能数据，warmup 是首帧 pipeline 业务。只应删除其余无功能日志或 native instrumentation。

## F3 信息边界

Minecraft F3 系统信息调用 GlUtil.getVendor/getRenderer/getOpenGLVersion，见 [DebugScreenOverlay.java](D:/Workspaces/References/minecraft-references/minecraft-1.21.1/src/net/minecraft/client/gui/components/DebugScreenOverlay.java:514) 到 535；GlUtil 最终调用 GlStateManager._getString，见 [GlUtil.java](D:/Workspaces/References/minecraft-references/minecraft-1.21.1/src/com/mojang/blaze3d/platform/GlUtil.java:19) 到 33。Radiance 将其导向 [GlStateManagerMixins.java](D:/Workspaces/Repositories/GitHub/RecRivenVI/Radiance/src/main/java/com/radiance/mixins/vulkan_render_integration/GlStateManagerMixins.java:267) 到 272 的 RendererDiagnostics.backendString；native fallback 从 Vulkan physical device 返回 vendor/name/API version，见 [com_radiance_client_proxy_vulkan_RendererProxy.cpp](D:/Workspaces/Repositories/GitHub/RecRivenVI/MCVR/src/core/middleware/com_radiance_client_proxy_vulkan_RendererProxy.cpp:152) 到 205。

RendererDiagnostics.captureOpenGlStrings 只有 [RendererDiagnostics.java](D:/Workspaces/Repositories/GitHub/RecRivenVI/Radiance/src/main/java/com/radiance/client/proxy/vulkan/RendererDiagnostics.java:17) 的定义，静态搜索没有调用者。它是可清理的无消费者 setter；backendString、GlStateManager getString redirect、DebugScreenOverlay 的 F3 路由必须保留。F3 GPU 行另由 TimerQuery 链提供，也不能删除。

## NativeRuntime identities/captureCanvas 与 bootstrapTest

NativeRuntime 声明位于 [NativeRuntime.java](D:/Workspaces/Repositories/GitHub/RecRivenVI/Radiance/src/bootstrap/java/com/radiance/bootstrap/NativeRuntime.java:11) 到 31。生产 Provider 在 [RadianceImmediateWindowProvider.java](D:/Workspaces/Repositories/GitHub/RecRivenVI/Radiance/src/bootstrap/java/com/radiance/bootstrap/RadianceImmediateWindowProvider.java:116) 到 165 创建 GLFW_NO_API 窗口、初始化 native renderer 和 LoadingRenderer，在 304 到 319 周期绘制 early frame；handoff 在 350 到 367 停止 early renderer、调用 NativeRuntime.handoff 并把同一窗口交给 GAME。GAME 侧 overlay 继续调用 provider.onGameFrame，见 [RadianceImmediateWindowProvider.java](D:/Workspaces/Repositories/GitHub/RecRivenVI/Radiance/src/bootstrap/java/com/radiance/bootstrap/RadianceImmediateWindowProvider.java:445) 到 505。

MCVR 的 [loading_renderer.cpp](D:/Workspaces/Repositories/GitHub/RecRivenVI/MCVR/src/core/loading/loading_renderer.cpp:387) 到 428 实现 initialize/uploadTexture/renderFrame：初始化复用已加载 GLFW，创建 Renderer，上传 image，验证 batch range 和 GAME/early/thread ownership，并将 canvas 合成到 UI target。handoff 在 430 到 434 设置 native ownership；releaseLoading/closeBeforeGame 在 482 到 485 分离 early resources 与完整 Renderer close。

NativeRuntime.identities 在 [loading_renderer.cpp](D:/Workspaces/Repositories/GitHub/RecRivenVI/MCVR/src/core/loading/loading_renderer.cpp:466) 到 472 返回 window、VkInstance、VkPhysicalDevice、VkDevice、VkSwapchain 五个真实句柄。Provider 的 validateNativeWindow 在 [RadianceImmediateWindowProvider.java](D:/Workspaces/Repositories/GitHub/RecRivenVI/Radiance/src/bootstrap/java/com/radiance/bootstrap/RadianceImmediateWindowProvider.java:567) 到 578 使用其长度和 window identity 做生产交接检查；BootstrapState.nativeIdentities 在 [BootstrapState.java](D:/Workspaces/Repositories/GitHub/RecRivenVI/Radiance/src/bootstrap/java/com/radiance/bootstrap/BootstrapState.java:59) 到 61 当前无调用。结论是保留 NativeRuntime.identities，确认无外部 SPI 后可删无调用包装 BootstrapState.nativeIdentities。

NativeRuntime.captureCanvas 在 [loading_renderer.cpp](D:/Workspaces/Repositories/GitHub/RecRivenVI/MCVR/src/core/loading/loading_renderer.cpp:348) 到 361 等待 device idle，把最后提交的固定 loading canvas transfer 到 host buffer，再由 474 到 480 返回 RGBA bytes。当前静态调用者只有两个硬件测试：

- [LoadingSceneGpuParityTest.java](D:/Workspaces/Repositories/GitHub/RecRivenVI/Radiance/src/bootstrapTest/java/com/radiance/bootstrap/ui/LoadingSceneGpuParityTest.java:90) 到 132 将 OfficialGlReference 的 OpenGL pixels 与 Vulkan pixels 逐像素比较，要求 diffPixels/maxChannelDiff 为零。
- [LoadingHandoffGpuTest.java](D:/Workspaces/Repositories/GitHub/RecRivenVI/Radiance/src/bootstrapTest/java/com/radiance/bootstrap/ui/LoadingHandoffGpuTest.java:77) 到 136 检查 resize 后 canvas 尺寸、handoff 后 early frame、foreign thread 拒绝、GAME frame 边界以及 releaseLoading 前后五个 identity。

所以 captureCanvas 是仅由测试调用、但仍编译在生产 native 内的真实 GPU readback，不是临时日志；不能把消费者仅在测试误称为产物已隔离。直接删除会丢失两类硬件验收；若后续要缩小生产 JNI surface，应先把 readback 放入 test-only native seam，再删生产声明。本批不实施迁移。

bootstrapTest 是独立 SERVICE 测试 source set/task，见 [build.gradle](D:/Workspaces/Repositories/GitHub/RecRivenVI/Radiance/build.gradle:29) 到 43 与 209 到 215。LoadingSceneTest 是纯 Java draw protocol/layout 测试，见 [LoadingSceneTest.java](D:/Workspaces/Repositories/GitHub/RecRivenVI/Radiance/src/bootstrapTest/java/com/radiance/bootstrap/ui/LoadingSceneTest.java:17) 到 79；OfficialGlReference 只在测试 source set 使用 OpenGL，不能进入生产 JAR。

## 旧 DIAG-BOOT-01 的修正

旧 [完整差异台账.csv](D:/Workspaces/Artifacts/RadianceMCVRAudit/20260914/Reports/完整差异台账.csv:17) 将 identities、captureCanvas、Provider 日志和交接计数放在一个“明确待全部移除”组。当前源码的有界裁决如下：

- identities：生产窗口 ownership check，保留；测试额外证明五个 native handle 在 releaseLoading 后稳定。
- captureCanvas：生产无调用、测试有真实 GPU readback，保留测试接缝；删除前必须迁移两项硬件测试。
- BootstrapState.nativeIdentities：当前无调用包装，可清理。
- 旧表提到的 Provider.logIdentities 的打印及 handoffNanos、largestTransitionGap、transitionFrames 由根在本批已移除；调查读取的是并行修改后的状态，起始存在证据见 Baseline/Radiance/working-content.zip。这不是“起始不存在”的结论，保留的校验已改为 validateNativeWindow。
- handoff、firstGameFrame、renderFailure、nextTransitionFrame、resource validation 都是实际 ownership/生命周期逻辑，不能按 DIAG-BOOT 标签清理。

本审查未声明任何动态测试已运行；建议以保留的 F3/TimerQuery、identity guard 和 test-only canvas seam 作为后续实际验证的边界。

根整合补充：BootstrapState.nativeIdentities无调用包装已在本批移除；RendererDiagnostics.captureOpenGlStrings属于F3信息的历史setter，本批不扩大清理到无关死代码，保留。报告中的“待删除”是调查建议，不等于所有建议本批都实施。
