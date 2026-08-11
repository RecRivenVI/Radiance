# GPU 优化资料与诊断准备

2026-11-01，基于当前含未提交改动的 Native、着色器和工具源码，以及 NVIDIA、Khronos 的开发资料，准备新一轮 GPU 测量。结论是先解释世界 PT 内部的实际工作和硬件限制，再调整执行方式及质量预算；本轮没有实施画质变化或取得新性能收益。

## 问题

使用者认可当前 CPU 接管架构，暂缓继续优化 CPU，并撤销本轮 GPU 工作中“任何质量都不能牺牲”的限制。目标是借鉴实时路径追踪产品的成本控制，改善城市移动、转头及放大水面倒影时的持续低帧和波动。允许比较有损近似，不等于把漏绘、缓存失效、错误运动历史或崩溃当作质量档位。

上一轮手工城市记录平均帧间隔约 33.87 ms，GPU 主队列约 26.97 ms，世界 PT 约 20.99 ms，DLSS 模块约 2.54 ms。这是材质状态／提交复用修正前的记录；区间存在包含和重叠关系，不能相加，也不能当作当前候选的 GPU 基线。原始入口为 `validations/performance-sources/result/manual-city-20261101/analysis.json`。

## 方法

阅读既有 GPU 预算和资源调查、Native 计时与 SHARC 观察实现、路径推进、命中程序、加速结构标志及编译设置。核对本机 Nsight 的实际命令行帮助和 Vulkan 设备能力；只查询工具与能力，不注入正在运行的客户端，不启动新游戏、不采性能窗口、不修改 NVIDIA 设置。

资料优先采用官方实现与原文，区分技术思路、具体 API 和其报告的特定场景收益。不下载整套示例资源，不将示例帧率当作 Minecraft 的性能目标证明。

## 发现

### 可借鉴的实现

| 资料 | 确认的做法 | 对 Radiance 的意义与边界 |
| --- | --- | --- |
| [NVIDIA 光追最佳实践](https://developer.nvidia.com/blog/best-practices-for-using-nvidia-rtx-ray-tracing-updated/) | 尽量使用不透明求交，减少 any-hit；控制重叠包围盒、动态 AS 更新、跨追踪调用的活跃状态及访存分歧 | 先检查实际 opaque 标志和热点，不能简单给所有几何强制 opaque，破坏裁切、所有者可见性和水面排除规则 |
| [Quake II RTX 路径说明](https://github.com/NVIDIA/Q2RTX/blob/master/src/refresh/vkpt/shader/path_tracer.h) | 分开主可见面、反射／折射、直接光和间接光；对后续反弹采用不同预算，镜面／透射使用专门的表面替换 | 证明实时游戏并不要求所有反弹同成本；分阶段也有中间缓冲、带宽和调度代价，不能照搬其二次反弹限制 |
| [RTXPT 设计](https://github.com/NVIDIA-RTX/RTXPT/blob/main/Docs/Design.md) | 统一光传输下按需分解路径；NEE 引导、SHARC 提前终止、主表面替换和不同层的降噪引导；使用 SER／OMM 等硬件能力 | Radiance 已有 SHARC、反射／折射及重建基础。评估已有路径如何利用缓存，不预设全部改写，也不因另一实现保留统一 pass 就认定多 pass 必然低效 |
| [NRD Sample](https://github.com/NVIDIA-RTX/NRD-Sample)及其 [simplex 配置](https://github.com/NVIDIA-RTX/NRD-Sample/blob/simplex/Shaders/Shared.hlsli) | 概率选择漫反射／镜面分量，复用上一帧或世界空间辐亮度，重点处理高效玻璃与降噪输入；提供 Vulkan 实现 | 优先参考水、玻璃、镜面及重建关系。不能只比较单帧噪声，更不能以该示例 Bistro 的帧率推断本项目应有帧率 |
| [RTX Remix 路径参数](https://docs.omniverse.nvidia.com/kit/docs/rtx_remix/latest/docs/runtimeinterface/renderingtab/remix-runtimeinterface-rendering-pathtracing.html) | 分别控制主路径交互及后续传光工作 | 把边界交互、真正散射深度和阴影预算分开观察，避免一个“四次反弹”滑块隐藏多类成本 |
| [RTXDI 接入说明](https://github.com/NVIDIA-RTX/RTXDI/blob/main/Doc/Integration.md) | DI、GI、PT 等重要性采样／复用算法解决不同问题，接入依赖材质、场景和可见性回调 | 先检查现有 Advanced／Vanilla 的光照工作。安装 SDK 或改名为 ReSTIR 不会自动产生收益，不把全面引入 RTXDI 当作前置条件 |

RTXPT 的主要实现使用 DirectX／HLSL；NRD Sample 则明确支持 Vulkan。采样、缓存和路径分解思路可以借鉴，资源 API、编译器、同步方式及闭源 SDK 重叠能力均须在本项目重新验证。

### 当前源码已经具备和仍然缺少的证据

- `core/diagnostics/gpu_profile.hpp` 提供每帧最多 96 个 GPU 区间，使用已有帧 fence 后的 availability 读回，没有新增 WAIT。结果是队列完成区间，包含依赖和调度，不能反推 SM／RT Core 利用率。
- 世界 PT、SHARC 更新／解析、模块和部分 AS 已有计时；分派宽高深也有计数。raygen 分派位置不是总追踪次数，主队列区间也不覆盖所有后台区块队列或 SDK 内部工作。
- `SharcAudit` 复用帧退休和 GPU 完成事件，提供八项抽样计数；当前 GLSL 观察点只在 Vanilla PT 的特定 SHARC 变体中，按固定 16×16 网格抽取。它不能代替全算法、全射线类别或所有材质的计数，固定相位也可能对细密内容产生采样偏差。
- 两套路径循环均有提前终止、缓存终止和不消耗散射深度的续接；太阳／局部光可见性还有额外查询。需要分别统计，不以用户设置的深度乘像素数估计总光线。
- 持久刚性模型已经依据材质和面规则设置 opaque 标志，不能把“没有不透明快速路径”当作已确认问题；需要补充场景分布，并核对其他生产者和实例强制标志。
- 主射线 payload 仍承载多类状态。完整介质历史已移在 raygen 私有状态，不能重复把旧问题列为待修。新的寄存器或 spill 怀疑需要实际 Shader Profiler 证据。
- 原生光追管线的 `maxPipelineRayRecursionDepth` 为 3；它是嵌套着色器调用深度，与用户设置的四次路径反弹不同，不能相互替换。
- 实际 shaderc 编译采用性能优化，但没有设置源码调试信息生成。Aftermath 的 SPIR-V 身份及 C++ PDB 不能直接补出 GPU GLSL 行号；源码关联应独立接通并保持优化、隔离缓存。

### 本机工具与能力

查询时 GPU 为 RTX 4080 SUPER、16,376 MiB，驱动 621.01。Nsight Graphics 2026.3.0、Nsight Systems 2026.3.2 可用。Graphics 的 Ada 计数组实际提供 Throughput、Top-Level Triage 和 Async Compute Triage；可分别启用 Real-Time Shader Profiler，支持有界时长、帧数及指标导出。

本机唯一列出的 Vulkan GPU 报告支持 `VK_EXT_ray_tracing_invocation_reorder`，reordering hint 为 REORDER；同时支持 NV 变体及 opacity micromap、pipeline executable properties。当前 Native 没有启用 SER／OMM；“驱动支持”不等于“项目已使用”。Khronos 的 [SER 示例](https://docs.vulkan.org/samples/latest/samples/extensions/ray_tracing_invocation_reorder/README.html)还特别说明部分 GLSL 工具链未支持 EXT 语言扩展，所以先使用项目实际编译器做最小编译和运行判定，不因系统工具较新就直接升级整套编译链。

本次输出没有列出 `VK_KHR_performance_query`，因此不把在 Native 内直接读取通用硬件计数器作为路线前提。[该扩展](https://docs.vulkan.org/refpages/latest/refpages/source/VK_KHR_performance_query.html)本身还涉及 profiling lock 和可能的多次采样；优先使用已经安装的 Nsight，不另造厂商计数器平台。

Nsight Systems 的环境自检提示非管理员、Sampling Environment Fail。这是其采样环境检查，不能据此宣布使用者已开放的 GPU 计数器权限再次关闭。Graphics 的实际设备连接和硬件计数尚未在本轮验证；首次正式取证时以其真实返回为准。

### 诊断分为三层

| 层次 | 用途 | 必须保留的解释边界 |
| --- | --- | --- |
| 低干扰帧记录 | 当前配置的真实帧间隔、GPU 分段、实际内／外分辨率、AS 数量和资源预算 | 保留 CPU 供给与等待作为排除项，不恢复一轮 CPU 架构优化；生成帧不计真实吞吐 |
| 软件工作量抽样 | 分开成像、漫反射、镜面／透射、阴影及 SHARC 更新的查询；统计 any-hit、材质、续接、终止和 POM／云步进 | 这是单独诊断变体，不能用其 FPS 作收益结论；抽样给出分母、比例及误差边界，不把 shader 调用数当作 BVH 节点访问数 |
| Nsight 短窗口 | 吞吐／占用、warp 等待、缓存和带宽、RT 活跃状态以及跨队列空洞 | 按 Ada 实际可见指标解释；硬件采样和软件原子计数分开开，避免多工具争用，不能把整卡利用率直接归因于某个 pass |

应遵循 [GPU Trace](https://docs.nvidia.com/nsight-graphics/UserGuide/gpu-trace-overview.html)和 [Shader Profiler](https://docs.nvidia.com/nsight-graphics/UserGuide/shader-profiler.html)的取证边界。特别关注 RT Live State 的调用点、保存字节与依赖样本；先查热点，再考虑 payload 缩减、分阶段或 SER。

硬件预设区分吞吐与着色器分析；首次使用单次采样，只有冻结静止场景确需额外计数时再多次采样。显式使用 `set-gpu-clocks=unaltered`，避免工具默认锁至基准频率导致与正常游玩混淆；需要锁频实验时单独记录，不覆盖正常 Boost 基线。

## 结论

调研、工具帮助与设备能力核对已完成。硬件取证预设和计数器清单位于 `D:/Workspaces/Artifacts/Radiance/gpu-optimization-20261101/`，仅准备，未注入客户端或取得新 GPU 硬件窗口。全路径软件计数及可关联的 shader 调试信息尚待接通；不能将计数器清单写成已经实测成功。

执行顺序和验收口径集中在[GPU 优化计划](../project/plan-gpu_optimization.md)。优先解释世界 PT 内部成本，随后分别推进低损执行优化、按视觉贡献分配预算，以及有证据支持的 SER／OMM 或阶段调整。实际性能、时间稳定性、内存代价和使用者观感共同决定保留哪些改动。
