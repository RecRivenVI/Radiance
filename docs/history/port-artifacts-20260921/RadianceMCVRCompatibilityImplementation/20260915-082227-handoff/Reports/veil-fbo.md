# Veil 4.3.2 AdvancedFbo 与 Simulated Diagram 实施报告

日期：2026-09-15  
仓库：`D:\Workspaces\Repositories\GitHub\RecRivenVI\Radiance`  
参考：`D:\Workspaces\References\minecraft-references\veil-4.3.2`、`D:\Workspaces\References\minecraft-references\aeronautics_bundled-1.3.2`

## 所有权

本代理仅实现以下范围：

- `compatibility/veil/VeilAdvancedFboAccess.java`
- `compatibility/veil/VeilFramebufferCompatibility.java`
- `mixins/compatibility/veil/AdvancedFboImplMixins.java`
- `mixins/compatibility/veil/LegacyAdvancedFboMixins.java`
- `mixins/compatibility/veil/DSAAdvancedFboMixins.java`
- `mixins/compatibility/veil/AdvancedFboBuilderMixins.java`
- `mixins/compatibility/veil/AdvancedFboTextureAttachmentMixins.java`
- `mixins/compatibility/veil/AdvancedFboMutableTextureAttachmentMixins.java`
- `mixins/compatibility/veil/AdvancedFboRenderAttachmentMixins.java`
- `mixins/compatibility/veil/FramebufferStackMixins.java`
- `mixins/compatibility/veil/FramebufferManagerMixins.java`
- `compatibility/simulated/SimulatedDiagramCompatibility.java`
- `mixins/compatibility/simulated/SimulatedDiagramScreenMixins.java`
- `mixins/compatibility/simulated/SimulatedDiagramStickyNoteMixins.java`
- `test/.../compatibility/veil/VeilFramebufferCompatibilityTest.java`

未修改 `VeilRenderSystemMixins`、其它 Veil shader/lifecycle/event mixin、通用 Shader/Buffer/VertexBuffer/ShaderProgram/BufferRenderer mixin、MCVR native 源码或共享 mixin JSON。根代理已按交付清单注册 9 个新增 Veil mixin。

## 实施结果

### AdvancedFbo 和 attachment

- `AdvancedFbo.Builder` 保留 Veil 4.3.2 的真实 `AdvancedFboTextureAttachment`、`AdvancedFboRenderAttachment`、mutable texture wrapper 与 Legacy/DSA 实例，不返回替代对象或假 ID。
- Legacy 与 DSA `create()` 均分配真实 `FramebufferProxy` FBO；逐个创建、挂载 color/depth attachment，设置真实 draw buffers，并使用 native completeness 结果判定。失败会释放已经分配的 FBO/texture/renderbuffer，并保持 `id == -1`，可在 renderer 接通后再次调用 `create()`。
- texture attachment 使用真实 Texture ID 和 `prepareAttachmentTexture`；render attachment 使用真实 renderbuffer ID 和 storage；mutable attachment只引用调用方拥有的 texture，不错误释放外部资源。
- bind、read/draw selection、clear、resolve/blit、free 全部进入现有 FramebufferProxy 资源与状态链；不会合成 complete 或吞掉绘制。
- 固定能力边界是单 color attachment、single sample。texture filter 支持 fixed consumer 所需 nearest/linear、mipmap mode、CLAMP_TO_EDGE/REPEAT；不支持的 anisotropy、depth compare、不同 X/Y wrap、border/mirror wrap 会明确抛错。
- Manager 取得 `build(false)` 的手工目标时会真实调用 `create()`；资源未就绪时保留未创建状态，后续取得可重试。Veil 固定 `post.json`/`light.json` 的 RGB16F 由根代理在 native 映射为 RGBA16F attachment；Simulated `end_sea_shadows` 和 Diagram 的 RGBA8 已在底层支持。

### FramebufferStack

- 用真实 READ/DRAW binding 和 native GL 坐标 viewport 快照替换 raw `glGet*`。
- `push/pop` 按栈契约使用 LIFO；保留同名 push 去重与 `lastPop` 行为。`clear` 恢复最外层进入前的 binding/viewport。
- 这修复了 Veil 4.3.2 源码 `removeFirst()` 所形成的 FIFO 恢复错误。

### DiagramScreen / StickyNote

- 删除了原 mixin 对 color/depth attachment、`build(true)`、`bindRead()`、`unbind()` 的空操作和 `build -> null`。
- 保留 Simulated 1.3.2 原始的三个目标创建/释放、FPS 节流、相机计算、UI `renderFBO(finalFbo)` 采样路径和 StickyNote 交互。
- 两个消费者都只重定向原 `DiagramScreen.draw(...)`：传入真实 `fbo` 和 `finalFbo`，执行 `beginTarget(sceneFbo)`、真实世界几何绘制、diagram uniform、`postTarget(finalFbo)`；异常走 `abortTarget()` 恢复父 binding/state，不在失败场景执行 post。
- `outlineFbo` 仍按上游生命周期真实分配和释放；native diagram target 使用同一后处理公式直接产生 outline/palette/fade 到 `finalFbo`，不再需要把 outline 作为独立 Java 中间目标。
- greeble 的 alpha 占用判断继续使用已有投影几何检查，避免无 OpenGL 环境下的 raw `glReadPixels`；最终图像不再由私有 diagram image 旁路合成。

## 验证证据

PASS：

- `gradlew.bat compileJava compileTestJava --no-daemon`：BUILD SUCCESSFUL，2026-09-15，本轮新增 helper/mixin 与真实依赖签名编译通过。
- `gradlew.bat test --tests com.radiance.compatibility.veil.VeilFramebufferCompatibilityTest --tests com.radiance.compatibility.veil.VeilMixinCompatibilityTest --no-daemon`：BUILD SUCCESSFUL，5 tests passed。最终源码变更后重复运行仍通过。
- `javap -p -s` 直接核对当前 compile artifact 中 Veil 4.3.2 的 `AdvancedFboImpl`、Legacy/DSA、Texture/Render/Mutable attachment，以及 Simulated 1.3.2 的 `DiagramScreen.draw`、`renderContents`、`DiagramStickyNote.populateFBO`；字段和 descriptor 与所有新增注入/重定向完全匹配。
- `git diff --check`：本所有权范围无 whitespace 错误。
- `radiance.mixins.json`：PowerShell `ConvertFrom-Json` 解析通过；9 个新增注册项存在（该共享文件由根代理修改）。
- 根代理报告 `public-core-third.log` 中 Framebuffer clear、RGB16F 和 DiagramTarget Release 链接通过；`diagram-shaders-build.log` 与 diagram_target 单编通过。此项是根代理的 native 检查点，不是本代理独立复跑。

一次早期测试尝试直接从 JUnit runtime 加载 compile-only Veil/Simulated JAR，因 Gradle 测试运行类路径按设计不含这些依赖而失败；该无效夹具已移动到回收站。最终测试改为无需运行时加载外部模组的能力边界合同并通过，外部 ABI 由上述真实 JAR `javap` 核验覆盖。

## 未完成边界

- 根代理最后报告：viewport 在不同高度目标切换时保持 GL 坐标并重新翻转 Y 的新修复已写但尚待下一次 core 编译。因此本报告不能宣称该最后 viewport 改动已构建通过。
- 没有运行游戏、GUI、ComputerUse 或 GPU 画面验收；Diagram、StickyNote、Veil post/light/end-sea 的实际像素结果仍待根代理完成 native 集成后交给用户目视。
- 没有为 MSAA、多个 color attachment、纯 S8、texture array/cubemap layer、不同轴 wrap、border sampling、anisotropy 或 depth compare 扩大底层 GL 兼容面；这些请求会真实失败而不是伪装支持。
- Java 定向测试不执行 JNI/GPU allocation；真实 framebuffer completeness、clear、blit 和 nested target 的运行证明依赖根代理 native tests/build 与后续人工运行验收。
- 所有变动保持未暂存、未提交、未推送。
