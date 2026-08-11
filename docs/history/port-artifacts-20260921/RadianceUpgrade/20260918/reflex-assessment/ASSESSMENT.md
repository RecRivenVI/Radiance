# NVIDIA Reflex 接入评估 — 2026-09-18

结论：建议先在现有 NGX 链路上直接接入 Vulkan 低延迟扩展，分阶段验证，再决定是否迁移 Streamline。当前任务仅评估；未修改产品源码、依赖、配置、部署文件或启动游戏。

## 当前机器与代码实查

- 只读创建 Vulkan instance、枚举 physical device 和扩展后销毁，无 window/surface/device 创建。结果在 capabilities.json：RTX 4080 SUPER，VK_NV_low_latency2 **specVersion=2**；present_id/present_wait、对应第二版和 EXT_present_timing 也有公布。扩展存在不等于当前产品已启用或功能验收通过。
- 仓库 Vulkan header 的该扩展版本也是 2；volk 已有所需函数声明。不需要先升级 DLSS/NRD/FSR 来做这次接入。
- Device 的 Vulkan12 feature 链目前未开启 timelineSemaphore。接入时需查询该 feature 并按能力启用，不能只检查扩展名称。
- Radiance 当前是直接 NGX SR/RR/FG，不使用 Streamline。FG 的生成帧与真实帧由主线程、主队列分别呈现。
- MinecraftClientMixins 在 mainRenderTarget.unbindWrite 处 submitCommandAndPresent，随后 acquireContext；不能以“当前 swapchain imageIndex”作为 Reflex 帧编号，因为它循环复用。
- RenderSystem.flipFrame 仍执行两次 pollEvents；只移除了其中的 GLFW swap。flipFrame 由 Window.updateDisplay 在 runTick 尾部调用。客户端 tick、鼠标累计移动处理、GameRenderer.render 在下一次 runTick 中进行。因此仅在渲染前加入 sleep 会错过已经采样的输入。
- secondaryQueue 已用于区段构建，不能视为可随便复用的专用呈现队列。需要审核后台上传/BLAS 提交的帧归属。
- 用户通过 RTSS 确认 FG 有显示收益。该事实不证明 Reflex 或输入延迟；此次不再把此前的 CPU 第二次呈现阻塞猜测当成已证实根因。

## 路径比较（工程判断）

| 路径 | 影响范围 | 判断 |
|---|---|---|
| 直接 VK_NV_low_latency2 + 现有 NGX | 新低延迟控制对象、设备/交换链生命周期、Java 主循环标记、FG 帧归属、统计与设置 | 当前优先。保留已验收 SR/RR/FG 和 Ponder，便于差分回退。呈现均匀性与 PCL 统计仍需自己接。 |
| 只引入 Streamline Reflex，保留直接 NGX FG | 新运行时与启动生命周期，仍要解决现有双呈现归属 | 暂不推荐作为捷径。并不能自动把自管 FG 接成 Streamline 的 FG/Reflex 协同；此组合尚未做实机验证。 |
| Streamline 接管 FG + Reflex | Vulkan hook/loader、交换链、资源标签与保活、frame token、打包/签名、回归验证；SR/RR 是否继续 NGX另审 | 长期候选，当前改动较大。不能把已有自管双呈现与 Streamline 接管同时启用。 |

官方当前发布页列出的最新正式版为 Streamline 2.14.1，并列有 VK_NV_low_latency2 支持；如果选这条路应锁正式版，而非 main。Streamline 的 DLSS-G 指南要求与它自己的 Reflex 集成一起使用，这个限制针对 Streamline DLSS-G，不应误读为当前直接 NGX FG 必须迁移。[发布页](https://github.com/NVIDIA-RTX/Streamline/releases/tag/v2.14.1)；[SL FG 指南](https://github.com/NVIDIA-RTX/Streamline/blob/main/docs/ProgrammingGuideDLSS_G.md)。

## 直接接入的关键约束

低延迟核心使用 swapchain opt-in、sleep mode、timeline semaphore wait 和阶段 markers。FG 的多次 present 必须归于同一应用帧，可利用 out-of-band present markers。驱动 v2 不能依赖 v3 才支持的显式 submission attribution。Reflex 应用帧编号与 KHR present ID 不同。[Vulkan WSI 规范](https://docs.vulkan.org/spec/latest/chapters/VK_KHR_surface/wsi.html#low-latency)。

工程方案：

1. 引入独立 ReflexController，保存能力、扩展版本、模式、timeline semaphore、单调递增 64 位真实帧 ID、交换链代际及诊断状态。JNI 传递帧边界，避免把暂停、20 TPS 服务端 tick、Ponder 子场景或生成帧当作新的应用帧。
2. 先接 markers 和报告，低延迟关闭。检查完整输入→模拟→渲染→呈现链，特别是第一帧、无世界帧、F3 饼图和资源重载。
3. 输入边界候选是现有 flipFrame 第一次 pollEvents 前：在前帧完成后的此处等待，再记录下一真实帧的模拟起点，让两次事件轮询及后续客户端模拟归于下一 ID。初次帧需单独启动，挂起/恢复需重置。替代方案是将主事件采样统一移到下一帧开头，但它对模组/窗口处理影响更大；第一阶段不搬移事件循环。
4. 现有 acquireContext 在输入采样前已完成是需要测量的排队因素；最初不同时改获取交换链时机。sleep 不能持有 TextureProxy monitor、chunk mutex 或队列锁；等待要能处理退出和 device lost。
5. 普通帧按一个 ID 成对发送模拟、渲染和呈现标记。FG 帧给两次真实调用加相同 ID 的 OOB 呈现归属；保留真实输出的标准 present 标记。v2 的隐式 submit 分组、额外拷贝与后台区段队列必须用日志/报告验证，不能将两次 present 当两次模拟或对生成帧重复 sleep。此处是首要技术验证点，尚未宣称正确。
6. 交换链重建须重建/重置相应低延迟状态；不能让旧对象的 sleep semaphore 等待或标记跑到新交换链。失败回退至原有渲染，不影响其他厂商和无扩展环境。
7. 设置候选为关闭、开启、开启+Boost，能力不足时禁用；FG 使用时推荐开启。Reflex 对无 FG 的 SR/RR/NRD-FSR 也有独立价值。初期 minimumIntervalUs=0，单独观察低延迟核心，不同时改现有限帧含义；正式接管限帧前必须解决双重等待并明确限制真实帧还是输出帧。

不能把 Ref﻿lex sleep 当作两张生成/真实图像的均匀呈现调度器。当前自管呈现的 pacing 是另外一项测量/改造；首次接入不要把它与 GUI 分离或异步呈现重写混成一个实验。

## 验证与完成边界

1. Marker-only：同一真实帧的阶段顺序、两次 FG present 同 ID、无缺失/重复、实际驱动延迟报告与本地 CPU/GPU时间相符。检查 v2 下后台提交是否混入错误帧。
2. 先 FG 关闭，对比 Reflex Off / On / Boost，再 FG 开启重复；固定场景、分辨率、渲染模型、焦点状态。主世界、Ponder、GUI/F3/F3+1、切窗口/分辨率、资源重载、退出都覆盖。
3. 同时收集真实帧率、输出帧率、GPU 完成耗时、排队时间、显示时间和延迟分布；不能凭开关可用、RTSS FPS 提升或 sleep 调用成功验收。Boost 另记录功耗与频率。
4. 产品阶段补齐 NVIDIA PCL/统计桥接、测试闪光标记及相应验证工具支持。直接 Vulkan 的 timing report 不能自动代替整套 PCL/输入到显示测量；工具兼容与所需依赖仍需核对。未安装 FrameView SDK，未启用全局验证 HUD、驱动 profile 或系统服务。
5. NVIDIA 的 Reflex 指南有 Off/On/Boost、标记、PCL 和工具验证要求；当前只有能力探测，未通过这些运行验收。[Reflex 指南](https://github.com/NVIDIA-RTX/Streamline/blob/main/docs/ProgrammingGuideReflex.md)。

建议实施顺序：能力与生命周期 → marker-only 原型及 FG v2 归属验证 → 输入前 sleep 和三种模式 → PCL/真实延迟验证 → 再评估呈现调度改造。若 marker-only 在现有双呈现/后台队列上无法稳定归属，应先解决帧边界或转向整体 Streamline 方案，不叠加更多启发式休眠。

预计改动落点：MCVR 的 device/swapchain/render_framework、dlss_frame_generation、独立控制器及 JNI；Radiance 的 MinecraftClientMixins/RenderSystemMixins、RendererProxy、Options、视频设置与本地化。评估产物仅在本 Artifacts 目录，无产品源码或部署变动。
