# Ponder 接入现有 WorldPipeline：实现与验收记录

日期：2026-09-18
仓库：
- D:\Workspaces\Repositories\GitHub\RecRivenVI\Radiance
- D:\Workspaces\Repositories\GitHub\RecRivenVI\MCVR
实例：E:\Minecraft\PrismLauncherDev\instances\Radiance Test\minecraft

## 起点与范围
用户确认简化版能够加载，但画面很糊、未达到 PT 效果，授权改为完整接入现有链路。此前 framebuffer 附件修正、JNI Proxy 注册修正保留。本轮不启动用户实例，不提交、不暂存。

## 接入方式
- 替换 ponder_path_tracer.cpp 内的 CPU BVH / 简化 compute PT；当前入口创建独立 WorldPipeline，使用主世界当前 blueprint 的模块、属性和 shader pack 配置。
- 每个场景独立 World / Entities、Buffers、WorldPipeline、ShaderPack runtime、相机 UBO、BLAS/TLAS、帧输出和降噪/升频/曝光历史。最多缓存两个场景以支持过渡动画；具体阶段由现有 blueprint 决定，并非强制同时启用 NRD 与 DLSS。
- SceneRecordingScope 以 thread_local RAII 限定旧接口的场景读取；Renderer::world/buffers 和 Framework::contexts/safeAcquireCurrentContext 在记录期间指向预览资源。主世界对象和其命令缓冲不替换；异常退出恢复原上下文，其他线程不受影响。
- 克隆的 FrameworkContext 共享设备和帧 fence，独立持有场景模块上下文；预览上传和渲染记录在 overlay 命令流中，保持两个场景各自渲染、合成的顺序。
- 几何按原 RenderType 分组，通过现有 Entities 构建、WorldPrepare、硬件 BLAS/TLAS 和现有命中着色器处理。保持原贴图、辅助材质映射和 PBR 顶点属性；含非 opaque alphaMode 的几何不标记为 Vulkan opaque，保留 any-hit 测试。
- 恢复到 Ponder 场景局部 block units，把 GUI 缩放移入投影。Vanilla PT 和 Advanced 的主光线均添加正交分支；正交光线锥使用固定像素宽度，避免透视距离导致错误 mip 模糊。
- 独立 pipeline 输出尺寸等于窗口尺寸，不再固定 640 宽；内部渲染比例沿用所选现有升频配置。
- 离屏输出使用 COLOR_ATTACHMENT_OPTIMAL，不将预览目标当成 swapchain PRESENT 图像。最终 compute 只做现有管线颜色与 primary-hit depth 背景遮罩合成，不负责另写一套材质/光照。
- 场景纹理绑定只更新已回收的当前 frame slot。Halton 序列改为 Buffers 实例成员，预览不消耗主世界抖动序列。
- PonderUI.removed 释放场景缓存，GPU 引用保留到帧回收；pipeline 重建、资源重载和关闭的 GPU-idle 边界提前关闭所有活跃/待回收预览，先于共享 SDK 退出。

## 源码范围
Radiance:
- compatibility/ponder/PonderPathTracer.java：矩阵、分组采集、全窗口目标。
- client/proxy/vulkan/PonderProxy.java：扩展 trace 参数，新增 releaseScenes；仍走 GAME_NATIVE_OWNERS 注册。
- mixins/compatibility/ponder/PonderScenePathTracingMixins.java：独立场景 ID。
- mixins/compatibility/ponder/PonderUILifecycleMixins.java（新增）：离开界面释放。
- src/main/resources/radiance.mixins.json。
MCVR:
- src/core/middleware/ponder_path_tracer.cpp：现有 WorldPipeline 接入与最终合成。
- src/core/render/scene_scope.hpp（新增）：限定作用范围的场景读取。
- src/core/render/renderer.cpp、render_framework.cpp：场景读取路由。
- src/core/render/pipeline.cpp：离屏布局及预览回收边界。
- src/core/render/buffers.hpp/.cpp：独立 jitter 序列。
- src/core/render/textures.hpp/.cpp：预览 WorldPipeline 的材质纹理绑定。
- src/core/render/modules/ui_module.hpp：预览缓存与生命周期登记。
- src/core/render/modules/world/ray_tracing/ray_tracing_module.cpp：当前预览 frame slot 纹理绑定。
- src/core/render/modules/world/post_render/post_render_module.cpp：当前 slot 绑定、离屏布局、预览颜色写入同步。
- src/core/render/modules/world/tone_mapping/tone_mapping_module.cpp：离屏布局。
- src/shader/preview/ponder_composite.comp（新增）。旧 ponder.comp 保留但不再作为输出路径调用。
- 内置 vanilla-pt/world/world.rgen；advanced/world/world.rgen、primary/primary.rgen、common/primary_trace.glsl：正交主光线与纹理采样 footprint。
- tests/scene_scope_test.cpp、tests/CMakeLists.txt：作用域恢复和线程隔离测试。

## 已有验证
- mcvr.scene-scope CTest PASS：嵌套作用域、异常退出恢复、跨线程隔离。它不证明 GPU 正确性。
- Verify-Shaders.ps1 / shader-check.log：从两套内置包默认属性生成 defines；Vanilla PT world、Advanced primary/world 及配置中的 world 变体经 glslang Vulkan 1.3 编译通过。没有穷举全部用户属性组合。
- 完整 Java / native / shader / distributedJar 构建记录见 build*.log。最初修正了 descriptor sampler 绑定遗漏 array index；最终部署证据附后。

## 仍待运行验收
- 不宣称已验证画面正确、无崩溃、动画历史正确、帧率可接受。用户实例由用户启动。
- 首次进入需建立独立整条管线和 shader pack runtime；两场景过渡会额外占用显存。尚未做缓存复用和构建耗时优化。
- 当前共享主世界材质映射，复制当前主世界光照环境作为预览输入；不是独立配置好的 Ponder 灯光设计。
- 非 QUADS 图元仍保留原路径；原有 Ponder 光照坐标渐显和第三方特殊渲染器尚未逐个验收。
- 内置两套 shader pack 已适配正交主光线；外部自定义 pack 必须自行支持正交投影，不能据此宣称全部兼容。
- NRD/DLSS 等模块拥有独立实例/历史并按 blueprint 接入，但其在此正交动画场景中的实际质量、运动矢量与重投影尚未实测。
- 背景遮罩来自 primary depth；升频边缘、透明物体后景和后处理外扩效果需检查。

建议验收顺序：静态思索场景的清晰度/PBR/遮挡 → 方块与实体动画 → 旋转/场景过渡 → 关闭思索返回世界 → 再次进入。若失败，保留 latest.log、crash-report、hs_err 和 native 构建输出。

## 最终部署证据
时间：2026-09-18T13:19:18.4869494+08:00
prepareRuntime distributedJar: BUILD SUCCESSFUL in 1m 56s.
mcvr.scene-scope: 1/1 PASS. 两套内置包主光线着色器编译 PASS；git diff --check PASS（既有换行警告）。
已检查外层 JAR 的合成 SPIR-V、两套内置 zip 的正交分支，以及内嵌 GAME JAR 的 Proxy/lifecycle class。
javap trace descriptor: (JJIJIIII[Ljava/lang/String;[I)V; releaseScenes: ()V; DLL 两个符号均存在。
JAR（构建=实例）SHA256: 757DBE773EDB624F7D177DBF11BD21807980BF923EC8EB7B5D11C7B9AB1B5AFA
DLL（构建=内嵌）SHA256: A108F4E0180E5EC600A4101E02FCC3A01B15237BDC783188091712D54A72126B
实例未由代理启动。运行、视觉和性能验收 PENDING。
