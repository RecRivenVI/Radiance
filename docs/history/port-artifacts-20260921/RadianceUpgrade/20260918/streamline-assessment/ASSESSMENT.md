# Streamline 整体迁移评估（含功能独立选择要求）

评估日期：2026-09-18。目标仓库：D:\Workspaces\Repositories\GitHub\RecRivenVI\Radiance 与同级 MCVR。仅调查现有代码及固定 tag v2.14.1 的官方文档/头文件；未改产品、配置、依赖或部署。

## 结论与范围

整体迁移可行，长期价值在于统一 NVIDIA 功能的帧标识、资源描述、能力检测和 FG/Reflex 协作。不是换 DLL 即可完成，也不保证迁移后自动提升画质或帧率。

用户新约束是硬要求：SR、RR、FG 可分别选择，不绑定 RT_DLSSRR；NRD、FSR、XeSS 继续存在。这里“整体迁移”指将**直接 NGX 的 NVIDIA 功能接入**替换为 Streamline，**不是所有算法都必须由 Streamline 管理**。NRD/FSR/XeSS 的现有实现留在 MCVR 渲染图中，NVIDIA 功能通过独立后端插入相应阶段。

与上一份 Reflex-only 评估的区别：只补低延迟时，直接 Vulkan 扩展改动较小；若目标是长期维护可组合的 SR/RR/FG/Reflex 产品，Streamline 值得作为目标架构。但必须分阶段迁移，保留旧部署版和同配置证据做对照。

## 配置与渲染图设计（建议，未实施）

配置分成：
- 降噪：NRD / RR / 关闭（高级诊断）。
- 分辨率重建：原生 / DLSS / FSR / XeSS；质量档独立。
- 帧生成：关闭 / DLSS FG；按设备能力提供倍数，首轮保持现有 2×。
- Reflex：关闭 / 开启 / 开启+Boost；不把它藏在某一条 RR 预设里。
- 模型：保留按功能分别全局选择 SR、RR、FG；主世界和 Ponder 继承同一选择，但拥有独立历史。

由 RenderFeaturePlan 之类的轻量计划层将这些选择编译为当前 YAML/模块图，现有预设变成快捷组合，不再是功能开关唯一来源。不要在 DLSSModule 初始化时顺便决定其他三个功能是否可用。记录 requested/effective 配置和明确失败原因，禁止无提示改写用户保存的组合。

| 组合 | 迁移要求/状态 |
|---|---|
| NRD + 原生、FSR 或 XeSS，FG 关 | 必须保持；Streamline 失败/不支持时也不能破坏这条链路 |
| NRD + DLSS SR，FG 关/开 | 一等验收组合；SR 后端迁移至 sl.dlss |
| NRD + FSR + DLSS FG | 一等目标组合；FG消费最终画面与独立PT深度/运动数据，不以RR/SR开关作门槛；尚未实测SL组合 |
| NRD + XeSS + DLSS FG | 同上；验证缩放后的图像与PT输入尺寸/抖动契约 |
| RR + DLSS 重建，FG 关/开 | 使用一次 RR evaluate 产生重建输出，不能再串一次 SR evaluate |
| RR + 原生尺寸，FG 关/开 | 目标采用 RR 的 DLAA/1:1配置，实际输入尺寸通过接口查询与验收确定 |
| RR + FSR/XeSS | 研究项：候选是先在低分辨率做1:1 RR，再外部超分。二次时域处理的性能/历史/质量需专项验证，不宣传为已支持，也不改成不可见的内部组合 |
| NRD 与 RR 同时降噪同一信号 | 不作为普通组合。两者是同一阶段的替代提供者；不能把互斥算法假装成任意开关叠加 |

逻辑开关独立，不代表底层算子完全独立：RR API 自身同时涉及重建与输出尺寸，必须由计划层融合这些选择，避免双重超分。关闭 SR 不应连带关闭 FG；关闭 RR 时回到所选 NRD 或明确的无降噪路径。

## 官方接口核对

- SL SR 保留 Auto/E/F/J/K/L/M（E/F 标为弃用）；RR 保留 Auto/D/E/F。其余不少枚举明确回落默认，不能作为新模型向用户展示。FG 公共头没有可选模型 preset 字段，仍保持 Auto，不因迁移虚构模型选择。[SR header](https://github.com/NVIDIA-RTX/Streamline/blob/v2.14.1/include/sl_dlss.h)、[RR header](https://github.com/NVIDIA-RTX/Streamline/blob/v2.14.1/include/sl_dlss_d.h)、[FG header](https://github.com/NVIDIA-RTX/Streamline/blob/v2.14.1/include/sl_dlss_g.h)。
- RR 有按 viewport 的独立选项/资源/evaluate。SL 运动矢量归一化与像素抖动的契约必须重新适配，不能照抄现有直接 NGX FG 的 mvecScale=1、clip-space jitter。[RR guide](https://github.com/NVIDIA-RTX/Streamline/blob/v2.14.1/docs/ProgrammingGuideDLSS_RR.md)、[常量定义](https://github.com/NVIDIA-RTX/Streamline/blob/v2.14.1/include/sl_consts.h)。
- FG 可按多个 viewport 标记，但共用一个 backbuffer，不支持多个 swapchain；异步接管 acquire/present，要求配套 SL Reflex。[FG guide](https://github.com/NVIDIA-RTX/Streamline/blob/v2.14.1/docs/ProgrammingGuideDLSS_G.md)。这些说明支持我们设计独立组合，不替代实机验收。

## 主要改造与风险（结合本仓库）

### 1. Vulkan 启动与调用分发：高风险

当前 Instance 调用 volkInitialize，随后 volkLoadInstance/volkLoadDevice。新建唯一 StreamlineRuntime，早于 Vulkan 实例初始化，查询各功能需要的设备能力。建议采用官方 manual hooking 路径：保留真实驱动分发表，另保存必须经过 interposer 的函数，不直接到处覆盖全局函数指针。尤其避免后续 volkLoadDevice 把代理入口覆盖回驱动入口。[Manual hooking guide](https://github.com/NVIDIA-RTX/Streamline/blob/v2.14.1/docs/ProgrammingGuideManualHooking.md)。

审计 GLFW surface 创建、VMA、第三方 NRD/FSR/XeSS 的 Vulkan 函数来源；必须保证交换链创建、图像获取、呈现、销毁使用一致的对象链路。所有 feature requirements 合并后再创建 device，并显式交付 Vulkan device/queue 信息。已有 secondaryQueue 用于区段构建，不假设可随意交给SL。

### 2. 替换直接 NGX：中高风险

模块调用接口可保留，新增后端适配层替换 dlss_wrapper 与 SR/RR Evaluate。NGX 生命周期归 SL 管理后，当前 initNGXContext/deinitNGXContext 不能继续独立管理同一运行时。

采用启动时选择 legacy 或 SL 的开发对照方式；不在同一设备/同一帧同时初始化两套 FG/NGX 所有权，也不承诺任意热切后端。旧路径仅在迁移验收期保留，避免长期两套生产代码。

### 3. 数据与命令状态：高风险

现有 dlss.yaml/dlss_sr.yaml 提供 HDR、albedo、normal/roughness、motion、linear depth、hit depth，并输出供后续模块使用的重建深度/运动/法线。迁移不能只替换颜色输出而丢掉这些辅助输出；继续提供现有辅助放大/转换阶段。

设统一 FrameInputs 描述颜色空间、输入/输出矩形、深度定义、MV单位、抖动、相机矩阵、scene ID、reset原因、资源有效期。SR/RR/FG 各自从该描述转换；SL evaluate 后重绑所需管线/描述符/动态状态，并同步自身 image-layout 跟踪，避免 SDK 内部状态与引擎缓存不一致。

资源 tag 的生命周期不能只靠主队列 fence；SL FG 可以异步使用资源。初期采用默认队列并行模式确保正确，后续才评估 eBlockNoClientQueues，并使用 SDK 的输入处理完成同步对象保护复用/销毁。回收覆盖纹理、viewport、窗口缩放与退出。

### 4. FG/Reflex 与统计：高风险，主要收益所在

停用现有 DlssFrameGeneration 的直接NGX evaluate、第二次 acquire/present、保留帧拷贝和配套信号量；host每个真实帧只调用一次SL代理present。保留/适配所需输入生产，不能让旧FG和SLFG叠加。

Java输入边界仍要接：当前事件轮询在flipFrame、模拟和鼠标处理在下一runTick。一个真实帧一个FrameToken，贯穿Reflex/PCL、SR/RR、FG；Ponder不是新的输入帧。SDK不替我们选择Minecraft的输入采样位置。

异步API错误通过回调存入原子状态，在渲染线程处理；不能把回调返回当作当前vkQueuePresent的同步结果直接destroy。回调不访问JNI或执行阻塞销毁。

现有F3计数按两次host present统计，迁移后失效。改由集中一次读取slDLSSGGetState的呈现计数累计输出FPS，所有UI/日志共享快照，避免多个消费者清空增量；保留用户要求“仅替换原版FPS数字，不加行”。这仍不是光学显示器延迟测量。

FG关闭的基线开销需要专门测量，SDK开关、插件加载、代理交换链状态是不同层次；按目标版本指引设计切换与重建，避免长期保留不必要的中间拷贝，也避免菜单每帧反复重建。

### 5. Ponder：必须单独验收

以独立viewport映射现有sceneId及其生命周期；主世界与Ponder分别存分辨率、相机、jitter、reset和历史，共享功能选择与当帧token。现有Ponder可以在一帧中多次调用绘制，必须确认“同viewport同token”不会反复改常量或误推进历史；必要时复用结果或为确实独立的view分配ID。

首阶段只迁移Ponder SR/RR，保留现有UI合成和FG范围。Ponder独立FG需定义其backbuffer子矩形、遮挡/透明度与主世界合成，不因为SDK支持多viewport就自动开启。

此前D模型解决Ponder质量问题是明确回归项；分别比较DLAA/质量/平衡下D/E/F，不能让SDK新默认模型覆盖用户显式D选择。

### 6. GUI问题不会自动消失

当前HUDless+二值差分mask只是近似UI覆盖。SL不会还原真实UI透明度，也不会自动修复背景模糊/反色等背景依赖。资源输入先保持可对照，另列GUI分离专项；不能用迁移掩盖这项已知限制。

### 7. 打包与降级

锁定正式SL 2.14.1及配套生产DLL，核验实际DLSS运行库版本/签名/哈希，更新runtime manifest与NOTICE。功能插件、公共依赖及底层NGX DLL的最终集合按发行包依赖核对，不能只复制三个DLSS DLL。明确关闭OTA/下载插件默认行为以保持复现实验。[General guide](https://github.com/NVIDIA-RTX/Streamline/blob/v2.14.1/docs/ProgrammingGuide.md)。

SL不可用时保留NRD/FSR/XeSS与无FG呈现；已经创建代理交换链后不可直接换回真实驱动函数继续操作同一对象，必须按生命周期回退或重启选择后端。支持能力按功能显示，禁止SL失败导致整个Minecraft无法启动。

## 建议实施阶段与通过条件

1. 配置/能力解耦及基线：现有预设映射为独立选择，先不改变输出；归档当前JAR/DLL和固定场景。
2. SL启动、manual hooks、错误回调和关闭流程；SR/RR/FG均不启用，验证NRD-FSR与NRD-XeSS、窗口重建、正常退出、RTSS和截图路径。
3. SR与RR迁移：先主世界再Ponder；模型与分辨率契约、辅助输出、图像状态验收。Legacy/SL按启动选择做A/B。
4. Reflex/PCL与帧token：先FG关闭验证，再允许下一步接管呈现。
5. SLFG替换手动双呈现：先2×，同时验证NRD+DLSS、NRD+FSR、NRD+XeSS、RR路径；F3与RTSS对照，实际延迟而非仅输出FPS验收。
6. 稳定后移除生产direct-NGX入口，保留档案回退；单独研究RR+外部超分、Ponder FG、更多倍数和更激进异步模式。

每阶段独立构建、部署授权实例并交给用户验收；不一次同时换加载器、算法输入、GUI合成和呈现策略。当前所有组合均是目标/评估，**没有SL实机运行证据**。

验收固定分辨率、模型、世界与相机；覆盖FG/Reflex开关、主世界/Ponder切换、F3/F3+1、blur/invert/半透明、读取截图、资源重载、切维度、窗口缩放、退出。GPU validation、时间线/资源寿命、显存稳定性、输出FPS和输入到显示延迟分别记录。若不满足已有NRD/FSR/XeSS功能、模型选择或Ponder质量，不能以“SL初始化成功”认定迁移完成。
