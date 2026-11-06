# 项目内 GPU 故障取证

项目内客户端默认启用轻量事件与本地 Aftermath 基础采集，在 SERVICE 创建 Vulkan 设备之前接通。此配置不进入发行 JAR，不改变 Prism 启动参数；采集接通不表示故障根因已解决。

## 启动配置

```powershell
.\gradlew.bat :version:1.21.1-neoforge:runClient
.\gradlew.bat :version:1.21.1-neoforge:runClient -PgpuDiagnostics=light
.\gradlew.bat :version:1.21.1-neoforge:runClient -PgpuDiagnostics=off
```

`gpuDiagnostics` 支持 `aftermath`、`light` 和 `off`，默认第一项。配置覆盖项目生成的客户端运行，包括发行安装入口与验证实例；不覆盖服务端和数据生成。

| 配置 | 行为 |
| --- | --- |
| `aftermath` | Windows 加载外置 JVM agent，启用资源追踪与 shader 调试信息，并启用轻量取证；其他系统只有轻量取证 |
| `light` | 有界 CPU 事件、对象名称、实际 SPIR-V 留存，以及驱动支持的 checkpoint/device fault；不加载 SDK |
| `off` | 关闭本配置提供的取证与外置 agent；用于明确的无诊断对照 |

Windows 默认启动要求 [inputs.toml](../../inputs.toml) 的 `nsight_aftermath_sdk` 输入与 `local.toml` 路径相符。版本与允许的 SHA-256 以输入登记为准。SDK 来自本机安装的官方工具，私有归档含原许可；任务只在组件 `build/` 内校验和提取，不安装到系统、不打入产品。输入缺失、哈希不符或 agent 初始化失败会明确报错，不静默声称已采集；无 SDK 时可以显式选 `light`。

Aftermath 的设备功能需要驱动支持 `VK_NV_device_diagnostics_config`。查看启动日志中的 `Agent_OnLoad before SERVICE`、`enable` 结果，以及 `radiance-gpu-capabilities.log` 中实际创建设备的 flags 与能力。早期 SERVICE 的 stdout 可能尚未接通，仅有 agent 加载日志不足以证明设备配置生效。

## 证据与边界

以下路径相对本次实例目录；每次 Aftermath 与 SPIR-V 输出均有独立子目录，不覆盖前次运行。

| 输出 | 含义和容量 |
| --- | --- |
| `radiance-gpu-capabilities.log` | 实际设备创建后留存设备、队列、Aftermath flags、checkpoint/device fault 与对象命名能力 |
| `radiance-device-loss-trace.log` | 出错或正常 native 关闭时输出最近 4,096 个 CPU 事件、丢弃数、命令缓冲、队列、fence、对象地址及字节数 |
| `radiance-as-lifetime-fault.log`、`radiance-as-lifetime-close.log` | 最近 131,072 条 AS 创建、销毁和构建记录；逐实例成员记录需显式设置 `MCVR_AS_MEMBERSHIP_TRACE=1` |
| `radiance-gpu-fault.log` | 仅在真实设备丢失时查询支持的 device fault 与 GPU checkpoint；没有支持时如实记录能力 |
| `radiance-gpu-shaders/<运行>/` | 实际交给 Vulkan 的 SPIR-V、模块/来源索引及可用的 SDK shader hash；最多 512 个、合计 64 MiB |
| `gpu-diagnostics/<运行时间>-pid<进程>/` | SDK 回调产出的真实 `.nv-gpudmp` 和 `.nvdbg`；转储最多 4 个、每个 128 MiB、合计 512 MiB；shader 调试数据最多 4,096 个、每个 16 MiB、合计 512 MiB |

CPU 事件标签由记录自己持有；写入遇到忙槽或落后于较新的覆盖时丢弃并计数，不阻塞渲染。正常覆盖容量之外的旧事件不计入丢弃数，快照也可能跳过当时仍忙的槽。`observedFrame` 是记录时看到的 CPU 帧号，不是异步任务归属或 GPU 进度；`checkpoint-recorded-NOT-completed` 也只是 CPU 录制。只有驱动返回的 checkpoint、成功 fence 和其他完成条件才支持相应 GPU 进度判断。

资源日志覆盖主要缓冲、图像、描述符池和 AS，但不是完整的分配数据库；图像字节是应用估算，不是驱动/SDK 总显存。有限环会丢失很早的创建或历史，句柄/地址可能复用，不能只凭一次地址重合判断释放后使用。未实现完整的设备地址绑定报告。

SDK shader 调试回调延迟到故障时，正常运行目录为空属于正常情况。源码快照、原生及 agent PDB、shader 包和实际 SPIR-V 应一起保留。优化编译的 SPIR-V 不保证包含完整源码行信息；私有 SDK shader 也不保证能还原源码。跟踪日志不是 GPU crash dump。

性能探针激活期间，native 拆分 `entity.layout` 的拓扑、审计和表面层子阶段，主世界 GPU 增加 `pt-pass/` 时间戳。沿用 96 个区间的有界池，不新增同步等待；容量不足须按遗漏报告，不能记成零耗时。pass、模块和命令缓冲区间重叠，不得相加。Java 帧表的相机、地平线、时间天气是帧尾观察，不是 GPU uniform 读回。实际请求、场景和产物以对应性能验证为准。

常规计时与重型盘点独立：profile ABI 2 的 timing 位不包含元数据／SBT 内容审计和全区段资源 census；后两项只在明确的 `CONTENT_AUDIT` 窗口开启。`radiance.audit.nativePerformance=true` 只开启基础计时，逐分配标签统计另由 `radiance.audit.nativeAllocations=true` 开启。轻量故障环、AS 事件、正常默认 Aftermath 以及总体 VMA 统计保留。可信度与开销对照入口见[场景验证](../../validations/performance-scene_diagnosis/validation.md#诊断开销校准与-cpu-归因)。

`FLOW_AUDIT` 使用 ABI 2 的 flags=9，专门关联区块流水线与表更新，不开启资源 census。Java 汇总列到达、来源／邻居／交互变脏、编译、合并、延后及提交次数；Native 记录首次输入／更新、队列替换、完成、发布、扣留、过时、空结果与槽位迁移，并记录实际后台模式和队列族。Native 工作线程事件归入下一次排空的渲染帧区间，不是原始请求帧，首帧区间不完整；`backlog` 是瞬时数量，不能跨帧相加当作任务总数。`empty` 是确认空内容，不等同于卸载。

SBT 按通道与查询／更新角色、元数据按表记录旧／新长度、同位置实际变化条目、新增／移除条目及连续相同前后缀。顺序移动也可能改变同位置条目，不能由该计数直接认定几何内容改变；元数据另分主机上传、GPU 内部复制和新分配字节。比较借用现有缓存，不保留第二份完整表，额外扫描计入 `diagnostic.table-delta`。SBT 拆分句柄打包、分配、暂存复制及上传命令记录时间。

`ALLOCATION_TIMED` 使用 flags=17，增加显式分配边界计时，分开小资源池选择／锁等待、VMA 创建、命名、资源登记和缓冲销毁；对应 domain 5 仅为请求次数与字节，不是驻留普查。普通 TIMED 不启用这些额外区间。`CPU_SAMPLES` 在相同 flags=17 上另开 JFR，以 5ms 周期采样 Java／JNI 边界，记录 `radiance.RenderFrame` 的帧身份与区间，并保留类加载、编译、GC、安全点与锁等待事件。`jfr_cpu.py` 在 JFR 自身时钟内关联长帧；采样份额不等于耗时比例，嵌套暂停／类加载事件不能相加，JNI 栈不能充当完整 C++／驱动栈。已有 `RenderJfrExport` 可先过滤渲染线程及指定工作线程，再展开精简元数据，避免完整 CLI JSON 导出的膨胀；采样器启动可能改变 JIT 和时序，不将其启动尖峰当作产品帧率回归。

Java 光照计时区分客户端队列、光照包应用、区块启用、传播及区段通知，并记录本帧队列处理量。`LIGHT_PROPAGATION` 也包含可见存储图交换与通知，不等于纯传播算法耗时；观察不取消原版调用。FRAME_ONLY 仍关闭内部细分，不能事后从总帧时间还原某个函数。

domain 6 的 `chunk.enqueue-to-publish` 是 Native 入队到成功发布的生命周期延迟，含等待，不能加进 CPU 或 GPU 执行耗时；sum 除以同区间 published 数量才是平均延迟，max 表示区间最大值。跨采样边界的未排空尾部不用于速率结论。`frames.csv` 的 `submitted_fov_y` 从该帧实际提交的世界投影取得，无提交时为 NaN；实际内部尺寸读取 `pt.dispatch/<通道>/width` 与 `height`，不以输出尺寸推算。使用同一静止画面的 TIMED／FLOW_AUDIT 短窗口核对附加开销，FRAME_ONLY 仍不是无探针基线。

主世界 profile 同时记录 SHARC 更新、解析 GPU 区间，以及实际 ray tracing 分派宽高深和缓存解析容量。解析区间包含现有更新依赖屏障；容量不代表有效项数。统计 GPU 分布使用已返回样本，不能用 CPU 总帧数补零。硬件计数器失败与设备丢失分开处理，GPU 完成时间不作为硬件理论极限，见[城市 GPU 预算](../research/investigation-city_gpu_budget.md)。

`RADIANCE_SHARC_AUDIT=1` 在独立诊断运行启用抽样 GPU 计数。介质部分按每 16×16 像素选一点记录候选表面、准备端过滤、查询、AS 候选、材质解码、接触、续接及相机查询；它们是抽样事件，不是全屏精确射线总量。正式计时关闭此项。`MCVR_MEDIUM_CONTACT_FORCE_UNKNOWN=1` 仅强制准备结果走保守查询，用于与正常过滤对照，保持接触功能和着色器代码相同；不提供禁用接触的产品选项。

`MCVR_PIPELINE_EXECUTABLE_STATS=1` 在驱动提供扩展与功能时请求 Vulkan 管线 executable 统计，并记录驱动实际返回的条目。此项只用于诊断，不作默认性能配置；成功返回但条目数为零，表示没有可用统计，不能解释成零寄存器或零溢出。实际 SPIR-V 是否包含查询、驱动资源统计与实测耗时须分别判断。

## 错误收尾

城市性能、材质验证和独立重放的启动器用独立的内存监视线程保护测试进程。监视器每 250 ms 通过 Windows 系统计数检查可用物理内存和提交余量。物理余量降至 20 GiB 或提交余量降至 24 GiB 时终止本次测试。低于 40 GiB 后，若约一秒内持续以至少 3 GiB/s 下降，按趋势将在三秒内越线，也提前停止。内存读数超过 1.25 秒未更新时按监视失效处理。终止使用启动时取得的子进程句柄，不通过进程名称查找。

`SYSTEM_MEMORY_STOP.json` 保留停止原因和最近约八秒的读数。GPU、WDDM、日志采样与保护分开。`memory.jsonl` 记录系统提交、物理内存、内核池与测试进程私有内存。低余量时另外尝试记录一次前 12 个私有内存使用者，只记录名称、PID 和字节数。`wddm.jsonl` 分别记录专用与共享 GPU 内存。采样缺失或增长无法归属时保留未知；SDK 文件预算不代表 SDK 或驱动的运行内存预算。

定向客户端的 `diagnostics` 参数选择 `basic`、`resource` 或 `source`。基础档使用 CPU 故障记录，不加载本地 Aftermath；资源档复用 `MCVR_AFTERMATH_RESOURCE_ONLY`，额外保留实际 SPIR-V 与 SDK 哈希；源码档才启用源码级 shader 信息。正式计时使用同一诊断档位。历史停止证据不改写，当前对照恢复只认可事先登记的 151 号故障标记及其哈希。

真实设备丢失时保持首次错误和停止新提交；逻辑服务端保存顺序沿用既有实现。在已检测到设备丢失的 native close 与设备释放入口，agent 至多用 5 秒轮询 SDK 的异步转储状态，不等待故障 GPU idle；记录返回状态和查询结果。正常关闭的 idle 检查首次返回 device lost 时，也在后续主要资源释放前调用该入口；每进程只等待一次。预算到期、回调失败、容量拒绝或无转储都不能改写原始故障，也不能保证取得完整转储。

回调允许不同 SDK 线程并发进入，文件写入和预算受同一锁保护。正常关闭禁用采集；SDK 模块保留映射到进程退出，避免回调使用已卸载代码。输出写盘只在 shader 创建、故障回调和收尾发生；开销未进行性能量化，不能称为零开销。

## 验证与重型工具

由使用者准备多光源与半透明场景的持续诊断入口见[场景性能验证](../../validations/performance-scene_diagnosis/validation.md)。它加载探针但不授予主动实验权限，不自动进入、移动或退出；Agent 在场景就绪后通过有界、带场景标签的本地请求取样，输出到该项 `result/profiles/`。普通逐帧／CPU／主队列 GPU 分段沿用现有设施，GPU 输出缺失不作零值；不会因启动就宣布性能改善，也不会自动恢复此前压力矩阵。

```powershell
.\gradlew.bat :render_diagnostics:check :render_diagnostics:buildAftermathAgent :native_renderer:nativeTest
.\gradlew.bat :version:1.21.1-neoforge:runValidationSmokeRenderDiagnosticsClient
```

启动验证只到标题界面，由探针等待后正常退出，主音量为 0；不进入世界、不测性能、不注入设备丢失。独立测试覆盖并发事件、标签寿命、SDK 输入校验、回调写入预算与有界状态等待；没有伪造 GPU 转储。

同步验证、GPU-assisted、NVIDIA 光追验证、自动调用栈与额外 shader 错误检查均不由默认配置启用。需要时单独选工具、核对早期设备接入和实际日志，记录时序影响；不同时堆叠工具。不修改 TDR、全局 Monitor 设置或驱动，不制造非法 GPU 访问。自然发生设备丢失后停止当前用例，先保全证据，不自动重启。

官方接口依据：[Aftermath SDK](https://docs.nvidia.com/nsight-aftermath/SDK/index.html)、[设备诊断配置](https://docs.vulkan.org/refpages/latest/refpages/source/VK_NV_device_diagnostics_config.html)。本地诊断许可与公开二进制发布授权分别判断，现有发布门禁不变。
