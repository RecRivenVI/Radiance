# NVIDIA 渲染路径与测量范围补充

2026-11-01，使用者明确允许并推荐为 DLSS-RR 与 NRD＋DLSS 使用仅 NVIDIA 硬件可用的优化；无相关设备或能力时自动关闭。本文补充当前 GPU 计划，不将 NRD 本身误判成 NVIDIA 专属，也不将 FSR／XeSS 的专项优化纳入本轮。

## 问题

需要把“可以使用厂商能力”落实为实际设备、渲染模式、编译变体和回退边界，并准备真实测量。其他线程可能启动纯原版客户端，使用者允许继续调查；不能只凭进程存在就否定数据，也不能未经观察就认定完全没有干扰。

## 方法

补读 [Streamline DLSS-RR 指南](https://github.com/NVIDIA-RTX/Streamline/blob/main/docs/ProgrammingGuideDLSS_RR.md)、[DLSS 指南](https://github.com/NVIDIA-RTX/Streamline/blob/main/docs/ProgrammingGuideDLSS.md)、[NRD 说明](https://github.com/NVIDIA-RTX/NRD)、[Vulkan SER 示例](https://docs.vulkan.org/samples/latest/samples/extensions/ray_tracing_invocation_reorder/README.html)及既有 RTXPT 设计。核对当前 `device.cpp`、`dlss_module.cpp`、`dlss_wrapper.cpp`、Streamline 适配和已安装工具。

## 发现

### 接入与回退

| 条件 | 处理 |
| --- | --- |
| 实际渲染设备为 NVIDIA，当前使用 DLSS-RR 或 NRD＋DLSS，且相关可选能力可用 | 允许为该路径启用已验证的专用变体；按单项收益决定是否保留 |
| 系统有 NVIDIA 显卡，但实际选中其他 Vulkan 设备 | 不启用 NVIDIA 专用变体，不把适配器存在当作可用 |
| DLSS 可用，但 SER／OMM 等单项能力不支持 | 仅关闭该项，继续使用可用的 DLSS 管线与普通追踪实现 |
| 没有可用 NVIDIA／DLSS 设备或 SDK 功能 | 自动禁用专用能力，沿用现有可用通用路径；不加载含未启用扩展的 shader |
| 选择 FSR／XeSS 等路径 | 本轮不为其建立专项优化；公共实现的自然收益保留，不人为破坏现有功能 |

官方指南要求针对指定适配器检查功能支持，并绑定正确设备。当前 `NgxContext` 名称下的 SR／RR 包装实际委托 Streamline，不能仅凭类名误写为另一套独立 NGX 执行链。已有设备扩展检查和 SR／RR／FG 能力查询应复用，专用技术再追加其自身 feature／property 检查。

设备创建时的可选扩展启用，与当前管线是否采用优化分开管理。模式切换、重载和设备重建必须使用相应编译／缓存身份，旧 GPU 版本遵守原有退休，不在当前模式不适用时误载专用变体。支持性不足正常回退；设备丢失仍保全并停止，不借“自动回退”重启故障设备。

### 优化重点

SER 和 OMM 作用于追踪阶段，不是 DLSS SDK 的开关。先看实际执行分歧、透明求交和构建代价，再判断是否适合。SER 的支持与编译可用性分别验证；OMM 对动态纹理、透明覆盖及特殊可见性保留合法回退，不能把所有透明材料都烘成固定遮罩。

DLSS-RR 重点核对反射／折射的深度、运动和材质引导，以及重复预处理、输入布局、同步与重建开销。NRD＋DLSS 分开计时去噪、合成及超分，检查低采样输入、降噪质量配置和缓冲读写。NRD 是普通 shader 库，本轮 NVIDIA 优化范围不意味着删除它在其他硬件上的功能。

RTXPT 的异步 RR 重叠作为候选资料，不能假设本项目 Vulkan／Streamline 同步与其 DirectX 实现一致。额外缓冲、上一帧历史依赖、交互延迟、GUI／HDR 和 FG 均需一起评估。首轮 FG 关闭，生成帧不算真实渲染收益。

### 其他客户端的处理

沿用现有观察器记录其他游戏的 PID、项目身份和启动时间，不记录完整认证命令行，不关闭或调整其进程。将重叠区间和整卡显存／时钟／负载与本进程帧记录关联；如需进一步归因，查看 Nsight 的各队列与上下文。

低占用、没有可见资源争用的重叠不自动判无效；新客户端的启动编译、显存挤占或 GPU 抢占明显改变窗口时，单独标注并限制结论。不能只凭“纯原版”推断负载为零，也不因一次偶发长帧删除不利样本。必要复测由数据决定，不要求所有其他线程绝对停机。

### 首次当前候选测量

当前配套仍为本体 `ca2bde5a…`、Extra `bb98550e…`，未修改 Native 或 shader。使用项目内既有 Newport City 副本的最近保存点，不操作 Prism：相机约 `(-132.83, 63.25, 165.15)`，yaw `-107.63`、pitch `9.19`。32 视距、输出 2560×1440、世界 PT 实际分派 1485×835、四次反弹、Vanilla PT／SHARC、DLSS-RR Balanced、FG 关闭；POM 关闭、原版云、真实水面，物理反射／折射采用现有确定性分支。普通半透明仍是随机覆盖，不能混同为已否决的前景／背景混合。资源包保留 SPBR Legacy 和 Mirror。RR 运行日志实际选择 preset F，记录配置请求与实际执行的区别。

等待 Java／Native 区段队列归零并持续平静后，先采 30 秒 FRAME_ONLY，再采独立的 10 秒 TIMED。前者保留基本探针与轻量故障取证，关闭嵌套计时，不等于完全没有探针；后者用于阶段归因，不用它替代正常吞吐。

| 窗口／区间 | 样本 | 均值 | p95 | p99 |
| --- | --- | --- | --- | --- |
| FRAME_ONLY 帧间隔 | 944 | 31.76 ms | 36.04 ms | 37.85 ms |
| TIMED 帧间隔 | 316 | 31.63 ms | 33.97 ms | 34.65 ms |
| TIMED GPU 主队列整帧 | 314 | 31.28 ms | 33.42 ms | 34.42 ms |
| TIMED 主世界 PT | 314 | 25.67 ms | 27.59 ms | 28.43 ms |
| TIMED DLSS-RR 模块 | 314 | 2.31 ms | 2.70 ms | 2.91 ms |
| TIMED SHARC 更新 | 314 | 0.89 ms | 1.18 ms | 1.38 ms |
| TIMED SHARC 解析 | 314 | 0.37 ms | 0.56 ms | 0.78 ms |

GPU 区间有包含关系，不能相加。三个在途帧的 GPU 尾部没有在窗口内返回，按缺失处理；没有写入丢失、失败帧或令牌缺口。FRAME_ONLY 均值对应约 31.49 个真实渲染帧／秒。两个顺序短窗口均值接近，不足以证明探针开销为零，也不是优化前后对照。此点位与上一轮手工巡游不同，不拿旧巡游均值作收益基线。

本次窗口没有观察到其他 Minecraft 客户端。整卡负载采样均值约 98.5%，整卡显存约 11.55～11.91 GiB，不能全部归给本进程；进程工作集约 6.87～6.88 GiB，private bytes 约 13.89～14.19 GiB。主世界 PT 区间约占主队列的 82%，说明下一步应细分追踪和材质工作；仍不能由区间时间直接判断 RT Core、SM 或访存哪一项饱和。

首个控制器在 Minecraft 单例就绪及配置核对阶段暴露工具错误，没有产生有效性能窗口。已修正控制器；诊断工具另将 `displayDefaultsRevision` 明确分类为存储迁移标记，保留它的值但不反射查找不存在的运行时字段，其他未知字段仍严格失败。两项针对性测试通过。故障过程正常退出、原始输入恢复，不是设备丢失或产品崩溃。

第一次 Nsight Systems 两秒采集返回成功，但启动晚于客户端退出，只作为硬件计数访问接通证据，不作为城市硬件负载数据。后续采集必须由同一个控制流程协调开始与退出，核对真实时间边界；计数器可读不等于热点已经定位。软件全路径抽样、Nsight Graphics 的逐着色器关联仍未完成。

补采将采集器启动与客户端退出放在同一控制流程中。Nsight Systems 2026.3.2 成功取得 Ada `ad10x-gfxt` 的两秒、约 1 kHz 整卡计数；从 SQLite 的会话起点与样本时间重新核对，真实区间为本地 03:33:00.289～03:33:02.299，完整位于城市静止保持阶段，早于 03:33:04.225 的退出请求，且在软件 TIMED 窗口结束后。没有 API 注入、锁频、CPU 采样或额外重型验证，不与软件工作量原子计数同时启用。

| Nsight 整卡指标 | 窗口均值 | p95 |
| --- | --- | --- |
| GR Active | 98.25% | 100% |
| SM Throughput／Issue Active | 11.78% | 29% |
| RTCORE Throughput | 5.83% | 9% |
| VRAM Throughput | 20.56% | 39% |
| CS Register Allocation | 73.30% | 92% |
| L1 Hit Rate | 49.21% | 80% |
| L2 Hit Rate | 94.30% | 99% |

这些是工具带单位的百分比字段，不解析未知编码的其他字段；不是各 shader 的耗时占比，也不能用 `100%－吞吐` 估算可提升倍数。该短窗口显示 GPU 持续活动，同时发射吞吐不高、寄存器分配较高；没有证据将问题简单归结为 RT Core 或显存带宽已经用尽。下一步优先检查世界 PT 的调用点活跃状态、依赖等待和分歧，仍须逐着色器归因才能确认根因。L1／L2 全卡命中率也不能直接代表某种材质或 BVH 的命中率。

补采前的 10 秒 FRAME_ONLY 均值 30.87 ms，独立三秒 TIMED 为 31.10 ms；这是同候选硬件连接复核，不能把比首轮略短写成优化收益。补采前后未观察到其他游戏客户端，正常退出并完成输入 SHA-256 恢复。原始记录为同级 `gpu-current-c-20261101/`，外部工具结果为 `city-active-throughput.nsys-rep`、SQLite 与 `city-active-throughput-analysis.json`。这仍是初步静止证据，没有完成移动、缩放或 NRD＋DLSS 的成本解释。

原始记录为 `validations/performance-sources/result/gpu-current-b-20261101/`；初次工具失败记录为同级 `gpu-current-20261101/`。Nsight 预设、工具输出及原始输入保护文件位于 `D:/Workspaces/Artifacts/Radiance/gpu-optimization-20261101/`。客户端正常保存关闭，地图、配置、音量及原日志按归档 SHA-256 恢复；没有设备丢失、自动重试或 Prism 修改。

## 结论

本轮已明确 DLSS-RR 和 NRD＋DLSS 的 NVIDIA 专项边界，并取得当前 DLSS-RR 城市点位的初始基线与有效整卡硬件窗口。逐能力自动回退是后续专用实现的必要条件；尚未新增 SER／OMM 或改变质量。FSR／XeSS 专项暂缓。下一步细分主世界 PT 的工作量、活跃状态及等待，再选优化；NRD＋DLSS 及移动／缩放仍需独立测量，不能由本次静止 RR 窗口代替。具体顺序见[GPU 执行计划](../project/plan-gpu_optimization.md)。
