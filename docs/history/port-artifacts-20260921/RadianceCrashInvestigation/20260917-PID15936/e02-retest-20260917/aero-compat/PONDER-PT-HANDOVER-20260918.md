# Ponder PT 开发过程、经验与交接

记录日期：2026-09-18。用途：冻结本阶段结论，准备开发其他内容；不是正式完成全部兼容性验收，也不自动授权新功能的范围。

## 1. 当前结论与工作边界

Ponder 已从简化预览 PT 接入现有完整 WorldPipeline，能够实际执行硬件光追、PBR 材质路径、所选降噪/升频、曝光和色调映射，再合成回 Ponder 界面。

本阶段最重要的结果：用户确认显式请求 RR 模型 D 后，Ponder 在平衡模式下恢复到接近此前 DLAA 的良好效果。此前 E 下有严重颗粒、模糊；关闭 SHARC 或抖动没有解决。当前将“RR 模型选择/其与场景输入的交互”作为有效定位方向，不再把它描述为 RR 没启动。

边界：没有在 D 改善后完成严格同场景同窗口尺寸的 E 反向复验；没有证明 E 普遍有问题、E 不支持正交投影或所有输入契约均正确。D 的改善是用户视觉验收；日志证实请求 hint=4，不是驱动内部最终模型/权重的证明。

产品代码存在大量既有未提交变动，本记录不等同于整个工作区的审查。保留所有独立改动，不 reset、stage、commit、push。后续功能不能混入 Ponder 收尾或其他历史修复，除非明确需要。本文仅记录，本轮不修改产品、不重建、不部署、不启停游戏。

## 2. 工作路径与当前部署

- Java 仓库：D:\Workspaces\Repositories\GitHub\RecRivenVI\Radiance
- Native/着色器仓库：D:\Workspaces\Repositories\GitHub\RecRivenVI\MCVR
- Native 构建：MCVR\build-radiance-1.21.1-neoforge
- 本次记录目录：D:\Workspaces\Artifacts\RadianceCrashInvestigation\20260917-PID15936\e02-retest-20260917\aero-compat
- 用户 Prism 实例：E:\Minecraft\PrismLauncherDev\instances\Radiance Test\minecraft
- 仓库诊断实例：D:\Workspaces\Repositories\GitHub\RecRivenVI\Radiance\run\ponder-readback-20260918-144319\full
- JAR：Radiance\build\libs\Radiance-0.1.5-alpha-neoforge-1.21.1.jar
- 两实例安装位置：各自 mods\Radiance-0.1.5-alpha-neoforge-1.21.1.jar
- 最后部署 JAR SHA256：334D02DFAD8248C173C7BF59CADB514511D64C233128627231EABC9E0AE6408E；本轮重新核实 Prism 磁盘 JAR 一致。
- 对应构建 DLL SHA256：BA60CC4152E39B547BB0E362C1BA146913709FF623429BB6486BB98D8DD1D941（前次构建记录，本轮未重新读 DLL）。
- 两实例曾写入 radiance-ponder-rr-preset.txt = D；本轮再次核实 Prism 文件为 D。
- 最近 Prism 日志包含 mode=1 presetHint=4 diagnosticOverride=1 input=2227x1191 output=3840x2054，表示后续用户验收窗口已不同于之前 2560x1440 抓帧，不应混作严格同分辨率对照。

重要：D 尚未正式写成 Ponder 的默认产品策略。当前依赖临时诊断文件。删文件并重建 Ponder pipeline/重启后会回到旧选择行为。文件只在 RR feature 创建时读取，不热更新。

## 3. 当前架构与源码入口

Radiance/src/main/java/com/radiance/ 下：
- compatibility/ponder/PonderPathTracer.java：执行原场景遍历、收集带 PBR 属性的 QUADS、分组和矩阵数据；保留动画/实体/方块实体来源。
- mixins/compatibility/ponder/PonderScenePathTracingMixins.java：包裹场景渲染与独立 scene ID。
- client/proxy/vulkan/PonderProxy.java：JNI trace/releaseScenes；必须加入 GAME_NATIVE_OWNERS 注册。
- mixins/compatibility/ponder/PonderUILifecycleMixins.java：退出界面释放。
- mixins/compatibility/ponder/PonderUIRenderHelperMixins.java：原 framebuffer 生命周期桥接。

MCVR 下：
- src/core/middleware/ponder_path_tracer.cpp：独立场景、WorldPipeline、几何、相机、运行记录和最终合成。
- src/core/render/scene_scope.hpp：thread_local RAII 场景上下文；临时路由 Renderer::world/buffers 与 FrameworkContext，嵌套/异常恢复。
- src/core/render/scene_camera.hpp：把 GUI 仿射相机分解成刚性视图与补偿投影，保留原 clip/ray 对应。
- src/core/render/buffers.hpp/.cpp：每场景 UBO、前帧历史、独立 jitter 序列。
- src/core/render/textures.*、pipeline.cpp、render_framework.cpp、renderer.cpp：材质绑定、上下文与生命周期。
- src/shader/preview/ponder_composite.comp：管线颜色和 primary-hit depth 遮罩合成。
- src/shader/world/ray_tracing/internal/{vanilla-pt,advanced}/：正交主光线与 footprint。
- src/core/render/modules/world/dlss/dlss_wrapper.cpp：模型诊断和 NGX 参数；dlss_module.cpp：RR 抓取入口。
- src/core/diagnostics/ponder_capture.hpp：有界 GPU readback；fsr_upscaler_module.cpp：FSR 抓取入口。

每个 Ponder 场景独立 World/Entities/Buffers/WorldPipeline/BLAS/TLAS/降噪历史，复用主世界当前 blueprint、材质映射和光照环境快照。最多缓存两个场景支持过渡。记录在 overlay 命令流，共享设备与帧 fence，资源持有至 frame slot 回收。非 QUADS 保留原路径。内置两套 shader pack 已适配正交；外部包没有普遍兼容保证。

## 4. 开发与排查阶段

| 阶段 | 做法与结果 | 记录目录 |
| --- | --- | --- |
| 简化原型 | CPU BVH＋独立 compute PT，640 宽上限、8 spp、简化材质；能加载但模糊，不能满足真实完整链路要求。旧 ponder.comp 留存但不再是输出入口。 | ponder-pt-20260918 |
| 入口/崩溃处理 | 保留 framebuffer 附件修正与 PonderProxy JNI 注册修正；不要仅凭启动或构建成功推断场景渲染正确。 | 同阶段源码/日志及 full-chain 记录 |
| 完整接入 | 替换简化路径为独立 WorldPipeline，窗口尺寸输出、现有 PBR/RT/降噪/升频/曝光链。 | ponder-full-chain-20260918 |
| 天空运动向量 | 正交矩阵乘方向 w=0，原公式除 w 产生非有限值；增加退化/nonfinite 守卫返回零。用户确认奇怪边缘改善，降噪仍差。 | ponder-denoise-20260918 |
| 激活和历史诊断 | 实测 RT→DLSS→tone mapping→post render；handle 稳定至抽样 1200 帧，首帧 reset 后正常累计，success=1。只能证明调用被接受。 | ponder-rr-diagnostic-20260918 |
| 相机规范化 | 反射/非均匀缩放移到投影，视图 determinant 从 -1 到 +1，保持 P'V'=PV；数学测试通过，用户称降噪无改善且无新异常。 | ponder-camera-20260918 |
| RenderDoc 尝试 | 仓库测试启动时确认注入，但 NGX FAIL_PlatformError 导致 DLSS 跳过，不能作为 RR 对照；按用户要求转模组内 readback。 | ponder-gpu-capture-20260918；Radiance/run/renderdoc-20260918-142727 |
| GPU 抓帧 | RR 输入输出直接读回；确认 RR 有效执行但效果差，NRD-FSR 更干净。 | ponder-gpu-capture-20260918 |
| SHARC footprint | 更新 pass 降采样约 5 倍，正交 coneWidth 错用 launch extent；两 world.rgen 改传真实采样分辨率，8 shader 变体编译通过。用户关闭 SHARC 仍异常，不能认作此次画质修复。 | ponder-sharc-footprint-20260918 |
| 模型隔离 | 原 wrapper 显式指定 E 给非 DLAA 档，却漏设 DLAA hint；加入 Ponder-only D/E/Default 文件控制并恢复共享 NGX 参数。用户确认 D 下平衡效果接近 DLAA。 | ponder-rr-preset-20260918 |

曾通过的 mcvr.scene-scope 与 mcvr.scene-camera CTest、GLSL 编译和完整构建分别证明对应静态/数学条件，不替代游戏视觉验收。所有结果均是对应历史版本证据，本文未重跑测试。

## 5. 抓帧证据与复用方式

仓库诊断实例 full/radiance-ponder-captures 下：
- 17897139762846594：小场景 RR，8 帧。
- 17897143885713154：复杂场景 RR，1485x835→2560x1440。
- 17897147355730481：NRD-FSR，1706x960→2560x1440，比 RR 多约 32.1% 输入像素。不能当作相同输入采样量的定量比较。

Prism minecraft/radiance-ponder-captures 下：
- 17897159619891400：Balanced，SHARC＋jitter 开启；1485x835→2560x1440。
- 17897160438375040：DLAA，同设置；2560x1440→2560x1440。
- 两组各 8 帧 COMPLETE、RR result=1；16 帧相机矩阵一致，未发现 NaN/Inf。机械动画相位、随机采样和历史长度不严格相同。
- 同映射预览显示 Balanced 的颗粒在 RR 直接输出中已存在，DLAA 干净；不能归咎于其后的 UI 合成。
- 这两组发生在模型 D 诊断部署前；不要写成 D 的 GPU 对照。D 改善依据后续用户验收＋创建参数日志。

触发：先确认目标实例；向游戏工作目录写 radiance-ponder-capture.request。进入 Ponder 的 RR/FSR 路径后自动接受并改名 request.accepted，同场景连续抓 8 帧；完成后下次请求可重触发，无需重启。避免覆盖已有待处理请求。
输出：radiance-ponder-captures/<stamp>/frame-N，含 images.tsv、原格式 *.bin、current/previous-camera.txt、backend.txt、status.txt；RR 有 rr-result.txt，FSR 的 dispatch.txt 不伪装成功返回值。
GPU readback 依赖 fence-retainer 和 event 检查，完成后 invalidate/readback；不插入 queue-idle 或另行提交。保存可能导致短时卡顿，不用于性能计时。
分析器：本记录目录/ponder-gpu-capture-20260918/analyze.py，调用 python analyze.py <capture-dir>，校验 COMPLETE 和字节数，输出 PNG 与 analysis.json。HDR 预览是统一 Reinhard＋gamma，不是游戏实际 tone mapper；全图 temporal difference 不是降噪评分。

## 6. 遗留事项，禁止误标为已修复

- 产品收尾：决定 Ponder 正式默认 D/可配置模型，移除或明确保留临时文件诊断；当前主世界保持原 E 策略。D/E 和 DLAA/Quality/Balanced 是两个独立维度。
- 反向 E 复验尚未完成；如果以后继续质量研究，控制场景、窗口尺寸、相机、历史收敛和模型。
- Halton 原有循环用 &&，base-3 提前结束会截断 base-2；HEAD 已存在，未修。初始误差较大，4096..8191 最大约 0.00354 输入像素，不能直接归因为持续严重噪声。
- 深度 R16F：旧复杂场景 694..711.5，只有 36 个前景深度值，步长 0.5；精度疑点，未改，未证明导致 RR 画质问题。
- FSR/XeSS linear_to_device_depth 仍为透视公式；NRD-FSR 实抓中 fsr-device-depth 奇数帧全零、偶数帧各 36274 个非有限值。资源/格式/转换/诊断路径须单独调查，不凭干净画面忽略。
- 最终 alpha 仍依据原始低分辨率 jittered depth，与重建颜色可能边缘不一致。
- 整个场景按单个实体组织，动态拓扑的稳定 primitive 对应、运动历史尚未彻底验证。
- 场景进入与过渡构建耗时、双场景显存、长期运行、资源重载、反复退出、第三方特殊 renderer、透明材质和原始渐显语义未做全面回归。
- 未完成全材质一致性验收；共享真正 PBR 管线不等于每种水、玻璃、折射、特殊层都已实测等价。
- 保留既有 layout keepalive 规避、outliner/ghost 等独立变更；本记录不重新归因或清理它们。

## 7. 后续开发可复用经验

1. 子世界应复用主渲染链，隔离 scene state、历史与生命周期。另写简化 PT 很快能出图，但材质与降噪差距会成为第二套实现负担。
2. GUI 相机往往是正交、反射、非均匀缩放的复合变换，透视假设会同时影响射线、sky motion、footprint、深度转换和重建 SDK。
3. 按“构建→实际调用→GPU 完成→输入有效→视觉可接受”逐层确认。API success 不能替代 GPU readback，干净输出也不能替代输入正确性检查。
4. 比较设置前先枚举隐藏变量：分辨率档位、模型 preset、输入尺寸、窗口尺寸、资源格式、历史、曝光和动画阶段。DLAA 好而 Balanced 差不自动证明超分倍率错误。
5. 对硬件/驱动敏感问题先检查观测工具是否改变被测系统。RenderDoc 注入导致 NGX 不可用时，转有界模组内 readback。
6. 单次实验只改变一个因素；有独立 before/after、diff、构建日志、JAR/DLL hash。无改善的实验也记下，避免后续反复尝试同一方向。
7. GPU 临时资源、descriptor/pipeline layout 与共享 SDK 参数都要明确所有权和恢复边界。Ponder 模型诊断采用 RAII 恢复共享 NGX 提示，避免污染主世界或后续 feature。
8. 用户手工画质确认应原样记录；不要把验收成功扩写成尚未完成的根因证明、性能结论或全部兼容承诺。

## 8. 构建、部署约定与进入下一个任务

在 Radiance 工作目录使用 Java 21：
```powershell
$env:JAVA_HOME='C:\Users\RavenYin\.gradle\jdks\eclipse_adoptium-21-amd64-windows.2'
.\gradlew.bat prepareRuntime distributedJar --no-daemon --console=plain
```
构建后将最新 JAR 备份替换到已授权的两个实例 mods，核对 SHA256；每次产品修改均按用户约定部署。用户 Prism 由用户启动验收。代理诊断优先使用仓库 run 中的既有/带时间戳目录；只有明确请求才触发 Prism 抓帧或启停。终止进程前必须证明实例归属，不操作无关 Java。删除只用回收站。

开始新内容前：读本文与该功能的直接源码；记录新任务自己的 before 状态；保持 Ponder 的已验收 D 配置；不要把“记录交接”解释为自动执行上述全部遗留项。新功能内容等待用户指定。
