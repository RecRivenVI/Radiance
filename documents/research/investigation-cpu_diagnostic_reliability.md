# CPU 诊断开销、可信度与城市成本调查

2026-10-18，本轮先改进观察器，再对同一冻结城市副本采样；没有实施新的渲染优化、修改 Voxy 适配、运行 Prism 或提交源码。结论覆盖 1080p、32 视距已加载城市的静止阶段，不冒充加载期、移动、其他世界或 4K 的完整性能结论。

## 问题

此前 1080p 记录的 profile 隐含执行内容比对和全区段资源盘点，使诊断自身进入平均帧耗时及长帧。还需区分分派次数与实际网格、CPU 墙钟与活跃执行、嵌套阶段与独立工作，以及 JFR 的录制边界。不能继续将所有 CPU 时间归为顶点转换，也不能从仪表减负推出渲染器提速。

## 方法

### 实现范围与采样合同

- native profile ABI 升至 2：timing、内容审计、资源 census 分开；旧 ABI 不匹配时拒绝挂接。正常计时仍记录 GPU 区间与工作计数，但不再复制整张元数据用于观察，也不自动遍历全量区段做显存去重。
- `nativePerformance` 不再同时开启分配标签表；需要时单独启用 `nativeAllocations`。基础 VMA 总计数、故障环、AS 生命周期和默认 Aftermath 保留。
- collector 使用固定双数组，在短锁内取得快照，解锁后序列化；不在队列锁内做流式格式化，不每帧另分配整个快照数组。仍有界，溢出显式计数。
- `FRAME_ONLY` 保留同一探针、基础取证、逐帧输出与原产品查询，只关闭嵌套 profile 和额外 GPU 范围；`TIMED` 为常规分段；生产者、JFR、内容审计为单独窗口。
- 每窗核对预期／实际帧数、帧号连续性、Java self 分区、失败帧、writer/native/输出容量丢失；坏样本拒绝分析。长帧不裁剪，GPU 缺失不补零；观察器边界费用另列。
- JFR 使用 JDK 自带 profile 配置，额外请求 execution/native 10 ms 和线程 CPU load 1 s，容量上限 32 MiB。实际每线程样本率不保证等于 100 Hz；native 方法样本不是完整 C++ 栈。

自动回归经过生产 Scope、collector、异步报告 writer 和实际 CSV 解析接缝，包括异常展开、并发 drain、丢记录、末批缺失、分区错误、长间隔及 JFR 数据格式。不是仅查源码字符串。

### 场景与身份

复用 `dynamic-baked-comparison.zip`，SHA-256 `468B12B806BB2D705B23C9A123F88439BE00A8E02DAAE2A9062BFB86A4CC0FE5`；每进程前恢复并逐文件核对 206 个世界文件。日夜与天气循环固定，实体、动画和正常 tick 不冻结。相机为 `(95.784462690, 141.607489199, 12.897247229)`，yaw `53.53503`、pitch `32.74906`。

实际 framebuffer 1920×1080，实际 PT dispatch 为 1114×626，渲染／模拟距离 32，Vanilla PT、RR Balanced、SHARC、Reflex 2，FG／VSync／区块发光收集关闭；保持同一兼容模组组合、聊天屏幕与静音。启动伴随工具三次均报告 `activationGuard=applied`，但逐帧 Minecraft 缓存 `focused` 均为 true；没有单独采集实际 OS／GLFW 焦点，不能宣称已证明无焦点。这里的 1080p 是输出尺寸，不是原生追踪尺寸。Voxy 代码保留，但实验未加载 Voxy 模组。每次至少预热 60 秒、队列连续平静 10 秒后开始；窗间重新等平静，最后执行重载、保存和正常关闭。

硬件为 i7-14700K（WMI 为 20 核、28 逻辑处理器）、RTX 4080 SUPER，驱动 617.14，实际运行 Java 21.0.11。JFR CPUInformation 的旧 CPUID 解码将混合架构写为 14 cores，但同样报告 28 hwThreads，不拿 14 作为物理核数。另有外部 Minecraft 进程，本轮只记录，没有操作；共享 GPU 负载和运行间波动限制亚毫秒收益归因。未改线程优先级、频率或驱动设置。

证据根为 `validations/performance-scene_diagnosis/result/diagnostic-efficiency-20261018/`。早期 `source-and-artifact.json` 的 Git 文件清单过宽，含 515 个生成结果；原记录保留。`measurement-source-manifest.json` 仅从已记录哈希筛选出 2,049 个真正源码／配置／文档输入，不反推历史哈希；最终源码清单及 ZIP 另外生成，14 个 gitlink 单列。文件按原始字节 SHA-256，不规范化换行，路径排序后按 `path NUL size NUL lowercaseSHA LF` 汇总。诊断结果目录补入忽略规则，防止 JFR 和运行证据误入候选源码。

| 构件 | SHA-256 |
| --- | --- |
| 配套发行 JAR | `FDA7F66F8CE7949A0ED4D7F9449E922DE6EB5F205C1674C8636A1DB87FD46740` |
| 构建、内嵌、提取及实际加载 core | `C1BEF86448E17A6518738CE4DFA7F2DA035CD4C6F71A1D88F519DA1CF1374F90` |
| 内嵌 GAME | `74F7468D9A3F5137B053F51474D2748F691A618D6D4ED86976E205E973213F51` |

GAME 与上轮相同，shader 未作本轮修改。实际客户端为 ModDev GAME／probe 开发类路径，不能称为内嵌 GAME 的安装运行；实际 core 路径和哈希分别记录于两个成功案例的 `loaded-dll.json`。探针没有进入发行 JAR。

第一次入口 `calibration` 在 Gradle 配置阶段失败：临时 init script 未跳过不含 Target 的 included build，未启动游戏。日志、请求和更正回执保留；旧 `latest.log` 不能作为这次正常关闭证据。补上空项目判断，运行器同时加入日志新鲜度检查，随后独立案例 `calibration-b` 完成。不是一次 GPU 故障或被隐藏的崩溃重试。

## 发现

### 诊断自身的费用

PID 55312 同进程依序完成 6 个 30 秒窗口，计 6,984 帧；只计整帧 3,534 帧，分段 3,450 帧。所有窗口有效，无丢失、帧号缺口、失败帧或分区错误。

| 顺序／模式 | 帧数 | 平均间隔 ms | p95 ms | p99 ms |
| --- | ---: | ---: | ---: | ---: |
| 0 FRAME_ONLY | 1,207 | 24.861 | 27.496 | 30.254 |
| 1 TIMED | 1,153 | 26.019 | 29.432 | 32.496 |
| 2 TIMED | 1,163 | 25.812 | 29.719 | 35.049 |
| 3 FRAME_ONLY | 1,180 | 25.436 | 29.256 | 34.336 |
| 4 FRAME_ONLY | 1,147 | 26.164 | 30.212 | 34.102 |
| 5 TIMED | 1,134 | 26.463 | 30.977 | 35.672 |

按实际间隔数合并，25.476 对 26.095 ms，观察差 0.619 ms（约 2.43%）。这不是统计置信上限，也不是无探针产品损耗：两边保留基础取证，单窗均值范围相互重叠，GUI 消息和实体正常推进；不能把差值精确减到某个阶段或所有场景。常规 observer 帧尾约 0.13 ms，异步 writer 成本不全在其中。

PID 83032 再依序完成常规、生产者、JFR、内容审计、常规五窗，共 5,339 帧。常规两窗平均间隔 27.692／26.329 ms；生产者 28.683、JFR 29.726、内容审计 28.333 ms。三类重型窗口不计入正常帧率。

显式内容审计确认：元数据观察副本约 0.982 ms/帧，SBT 观察比对约 0.065 ms/帧；资源 census 在 1,059 帧中执行 35 次，合计均摊 0.456 ms/帧，单次平均约 13.81 ms、最大 19.63 ms。这些工作在全部常规窗口均未执行。这证明原计时路径包含会污染均值和长帧的诊断工作，不将全部历史长帧归因于它；原始旧数据保留，不回写为产品优化收益。

### CPU 阶段：不能把包含时间相加

下面以 5 个有效常规窗口、5,674 帧按帧数汇总 Java self；这些项互不重复。GPU 完成区间、native 下钻及另外列出的包含时间不能再相加。

| Java self 分区 | ms/帧 | 实际含义 |
| --- | ---: | --- |
| SUBMIT_PRESENT | 7.606 | 包含 native 世界准备、记录与提交，不是纯 GPU 等待 |
| ENTITIES | 3.813 | 实体生产路径自身，已扣嵌套观测阶段 |
| WORLD_RENDER_OTHER | 2.534 | 含未单列的原生入口；`EntityProxy.build` 下钻约 2.281 ms 包含时间 |
| BLOCK_ENTITIES | 2.421 | 区段／BER 遍历、容器、调度及其自身工作 |
| GEOMETRY_MARSHAL | 1.942 | 打包与 JNI 边界自身，包含时间约 2.866 ms |
| FACE_RESTORE | 1.495 | RenderSystem 状态恢复子调用 |
| GEOMETRY_FACE_STATE | 1.017 | 材质状态捕获自身，不含另列子阶段 |
| TICK | 0.925 | 客户端逻辑 tick |
| GEOMETRY_CLOSE | 0.881 | 结束／释放捕获容器和批次 |
| FACE_SETUP／CLEAR | 0.830／0.361 | 实际 RenderType 回调 |
| ACQUIRE | 0.518 | 不可整体称为 fence 等待 |

材质捕获包含时间约 3.724 ms/帧，覆盖 setup／clear／backup／restore 与自身。实体入口包含约 8.963 ms，BER 入口包含约 3.422 ms；这些包含已有上表项目，不能重新叠加。

| native 下钻 | ms/帧 | 边界 |
| --- | ---: | --- |
| 世界准备，包含 | 5.611 | 已处于 Java SUBMIT_PRESENT 内 |
| BLAS 录制，self | 1.382 | CPU 记录，不是 GPU 构建完成 |
| 元数据 staging 复制，self | 0.905 | 分配另约 0.05 ms，不能以分配代表整段 |
| 静态实例追加，self | 0.836 | 仍更新相机相对实例 |
| TLAS 构建准备／录制，self | 0.829 | 不是 GPU 的 TLAS 时间 |
| 实体元数据，self | 0.736 | 当前及历史数据组织 |
| 批次拓扑，self | 0.717 | `entity.layout` 的下级 |
| 小几何配对资格扫描，self | 0.391 | 仍有成本，但不再是主要数毫秒级全量搜索 |
| SBT lookup，包含 | 0.148 | 4 次/帧均复用，未上传 hit table |
| runtime buffers，包含 | 0.025 | 当前不应优先重写描述符 |

常规 fence 等待约 0.002 ms、swapchain acquire 约 0.005 ms、present 约 0.05 ms；帧限制器接近零。主队列 GPU 常规均值在 16.61～19.27 ms，和 CPU 重叠，不与 CPU 求和。当前证据支持 CPU 主线程是本场景的首要限制之一，不代表 GPU 在更高分辨率没有瓶颈。

逐帧 CPU 计数的正增量公约数为 15.625 ms，不能逐帧用墙钟相减求等待。[ThreadMXBean 文档](https://docs.oracle.com/en/java/javase/21/docs/api/java.management/java/lang/management/ThreadMXBean.html)也不承诺纳秒准确度。JFR 每线程首个 CPU load 区间同样单列，避免使用录制前基线；随后 render load 的 25 个样本平均 0.03516 是全机归一化值。按该版本 [HotSpot 实现](https://github.com/openjdk/jdk21u/blob/jdk-21.0.11-ga/src/hotspot/share/jfr/periodic/jfrThreadCPULoadEvent.cpp)乘实际 28 逻辑处理器，相当于约 98.44% 的单逻辑处理器时间。它支持主线程忙于执行的判断，不表示全机 CPU 用尽。

### 生产者、分配和剩余重复工作

生产者窗为 1,046 帧。`renderer_calls` 实际是分派入口计数，不保证已执行真实 renderer；`::` 烘焙模型行属于父入口，不累加顶点。face 时间是相关捕获工作，不保证与 renderer 包含时间互斥。

| 来源 | 入口/帧 | 生成顶点/帧 | 其他观测 |
| --- | ---: | ---: | --- |
| ItemFrameRenderer | 295 | 1,412 | 494 个持久实例总引用 52,548 顶点；本来源约 587 次 face、2.434 ms/帧 |
| PaintingRenderer | 223 | 13,632 | 223 个动态输入；自身约 0.732 ms，face 约 0.888 ms |
| ChestRenderer | 3,276 | 2,232 | 31 个实际网格／BLAS 候选，自身仅约 0.203 ms |
| BedRenderer | 987 | 1,368 | 19 个实际网格／BLAS 候选 |
| SpringRenderer | 16 | 1,312 | 程序化形变，不据此冻结 |
| 未归属动态来源 | 单列 | 约 53.60 | 不强行分给上述 renderer |

绘画约占当前完整 PBR 动态顶点的 59%，不是占整帧 59%。绘画源码自行展开有厚度的贴图网格，并逐 tile 读取光照；不能因为它没走 SimpleBakedModel，就把它当无条件静态常量。物品框两类烘焙入口的 self 约 0.835／0.674 ms；即使下游 BLAS 已复用，仍有 recipe 校验和临时对象工作。

JFR 文件 3,270,060 字节，render 线程共 382 个 execution/native 样本；提交 native、`EntityProxy.build`／`queueBuildSourcesV3`、材质恢复、纹理 filter 和 native allocator 均有实际栈证据。只作热路径交叉核验，不将样本占比换算为精确毫秒。

首次解析发现 Java 21 `jfr print --json` 的 duration 为 ISO-8601 字符串，已按实际数据修正并补回归，没有丢弃事件。更重要的是，25 个线程的首个 allocation 样本累计约 148.46 GB 权重，并非这 30 秒分配。该版本 [allocation sample](https://github.com/openjdk/jdk21u/blob/jdk-21.0.11-ga/src/hotspot/share/jfr/support/jfrObjectAllocationSample.cpp)按前次累计值作差，而 [thread-local 初始值](https://github.com/openjdk/jdk21u/blob/jdk-21.0.11-ga/src/hotspot/share/jfr/support/jfrThreadLocal.cpp)为零。工具保留边界清单与原 JFR，将每线程首次权重排除出类型排名；不是裁剪帧或隐藏卡顿。

之后的分配权重排名仍包含 fastutil 容器构造的 `long[]`／`Object[]`（约 3.42／3.38 GB 权重）、`RigidModelCapture.Quad` 的 `int[]`／`float[]`（约 1.80／0.62 GB 权重）、EntityRenderData、各 provider、矩阵及法线临时对象。这些是抽样压力指示，不能作为精确 MB/帧、单个对象大小或 native 分配。源码确认每次 BER 遍历仍创建捕获 provider 与若干容器，即使分派没有生产网格；786,432 是请求层的缓冲大小，并非每个空 provider 都预分配同等 native 字节。

JFR 窗口有 11 个 GC pause 事件，合计约 61.11 ms、最大 21.44 ms，未证实 GC 是常规平均帧的主因。JFR 自身窗口最大间隔 378.97 ms，不能算正常产品长帧。常规最大间隔 57.01 ms；尾部既有 FRAME_OTHER 突增，也有实体与 native 准备共同拉长，没有证据把全部尖峰归给一种循环。`start_interval` 属于前一帧的执行与边界费用，尾部分析按前一 token 对齐，未错接当前帧。

后台 CPU 也不能从栈样本数量排序：Netty／文件监视器有大量阻塞 native 样本，而渲染线程的 CPU load 接近一个逻辑处理器。排除首区间后，同一 JFR 窗口的服务端约为单逻辑处理器时间的 14.01%、profile writer 约 2.20%、JFR periodic tasks 约 10.72%；后者是重型诊断本身。C1／C2 编译线程仍活跃，各线程样本覆盖不齐，不把均值相加冒充全机占用。区块队列平静不等于 JIT 已完全稳定；后续正式收益 A/B 还应核对编译活动和运行间波动。本轮没有取得完整 C++／驱动采样栈，也没有证明全部残余长帧的根因。

静态表仍每帧逻辑上传 15,045,136 字节，其中 appearance 表 12,537,600 字节；固定静态段本来约 13,381,016 字节的中间 flatten 已避免，但 GPU staging 仍写整表。内容审计的“changed”是整表判定，不能解释成每条记录都变化；静态快照总体稳定，但窗口中仍发生一次重建。长期增量 GPU 表仍有价值，当前已测复制费用约 0.9 ms，不能包装成可直接消除整个 5.6 ms。

## 结论

### 建议的实施顺序

| 优先级 | 建议 | 直接证据与验收 |
| --- | --- | --- |
| 1 | 减少捕获 provider／容器的反复创建与销毁 | BER self 2.421 ms、close 0.881 ms、JFR 容器数组压力；先延迟真正请求缓冲前的重型状态或做有界复用，保持 renderer 回调及零输出判断。不按类名跳过 renderer；复用必须清理 draw state、所有者、纹理代际和历史，确认 JNI 使用结束后才归还 |
| 2 | 精简材质状态捕获及状态下发 | 包含 3.724 ms、物品框 587 次/帧；保留实际 setup／clear、颜色／UV 和采样时机，先统计重复字段与 JNI 调用，再在统一状态边界消除可证明冗余的下发。不按 RenderType 名称永久缓存，不直接删除恢复保护 |
| 3 | 改善 warm rigid recipe 查找，随后做绘画局部网格原型 | 已有持久实例仍有 Quad 构造和约 1.51 ms 的两类烘焙入口；RECENT 仅保留每模型一个最近候选，具体失配原因尚未逐类计数。绘画 13,632 顶点、223 动态输入可作为下一覆盖目标，但必须保留 tile 光照、变体、材质、姿态、轮廓、重载和历史；不缓存上一帧最终顶点 |
| 4 | 静态／动态 GPU 表分离与稳定索引 | staging 0.905 ms、静态实例追加 0.836 ms、实体元数据 0.736 ms；分开评估，不能将重叠包含时间相加。保留相机相对变换、SBT 索引、不可变版本和完成退休，不以原地覆盖在途表换速度 |
| 5 | 基于同一输入减少 native 批次准备与 AS 录制工作 | `EntityProxy.build` 包含 2.281 ms、BLAS 录制 self 1.382 ms；先用生产者工作减少带动下游，再考虑受限重用。不能无条件 refit、拆出每个小 cube 实例或用全局 idle |

第一项收益是待验证候选，不宣称整个 self 都能消失；第二、三项可按实际失配和不变性选择一条实施，不能同时铺开后无法归因。每轮均须证明同样内容下减少了创建／生成／上传／构建，再比较真实整帧及尾部。

后续正式帧率 A/B 使用新 `throughput` 计划的单个 `FRAME_ONLY` 窗口，另开 `TIMED` 归因；轻量窗口的实际路径已在本轮三次运行，新增计划选择另由单元回归覆盖。不能把调用次数减少后连带省下的嵌套 timer 成本算作独立产品收益。

本场景暂不优先继续改 SBT、GPU 顶点转换、描述符缓冲、区块后台调度、SHARC、PTLAS 或 SER；它们或已复用，或对应当前 CPU 开销较小，或不直接减少主要生产工作。静态样本不能排除加载期的独立调度问题。GPU 仍有约 17～19 ms，CPU 约 26 ms 也高于 60 FPS 的 16.67 ms 预算，因此不能仅凭这些局部预算保证 60 FPS，更不能证明 4K 120 FPS 理论上限。

### 验证与保留边界

11 个窗口共 12,323 帧均通过完整性检查。两次成功进程均完成 F3+T、字体违规 0、各维度保存和 native 正常关闭；默认 Aftermath 实际接通、正常禁用。没有设备丢失、GPU crash dump、自动故障重试、Prism 操作或新增视觉验收。独立故障根因、公开二进制许可、Voxy 初步适配与既有待验项目保持原边界。

最后静态核对发现分配标签与基础 telemetry 的旧联动：上述两个成功进程的 `flags=10` 没有触发原来受 allocation 位控制的每秒 VMA／13 类指标 callback。因此这组实测不包含该基础 callback，不能声称当时已保留它。已将呈现端门控改为 allocation 或 timing 任一开启，并补无分配标签的 collector 回归；修正后单独接通检查的产物、CPU 窗口与日志另列，不与前面候选合并。

修正后 PID 78828 在同一城市副本完成 30 秒 TIMED 窗口、1,099 帧，完整性检查通过；逐秒 VMA 总量和 13 类指标恢复，未启用 allocation 标签表或全量 census。`observer.basic-telemetry` 共 29 次，平均约 0.030 ms/次、最大 0.0494 ms，摊到全部帧约 0.000788 ms/帧。这只测调用端，异步汇报线程不含在此数字内。该次缓存 focus 与前面同为 true，平均间隔 27.312 ms、p95 31.825 ms、p99 37.543 ms；它是另一候选的接通确认，不作正式性能 A/B。完成 F3+T（字体违规 0）、三维度保存、正常关闭及 Aftermath 正常停止，没有转储或设备丢失。

该确认的发行 JAR 为 `0D0E92A9F4876443894E1521C3FC0DF162F61B3CB8C909FC026BC89B75EA380E`，构建／内嵌／实际加载 core 为 `35F759C58B8AEAD685AE4C7CC5E17F976B8426AC1577938C701F6A8A94552F9D`；GAME 与上表相同。两份实测 core 各留一份于证据根 `measured-binaries/` 用于复核，逐次提取副本不留。最终构建身份和全部源码输入另见 `final-source-manifest.json` 与维护回执，不把重新打包后的 JAR 哈希回填到已完成的旧进程。

最终门禁重新生成 NRD 并链接，JAR 为 `057D6ED54429BE443D97E44B5DC4055B8E67AEA50B7623A1AB4A8999FCFBBB44`、core 为 `750261931F59748315F6A5109A266A929AFF3EC2159E45A6AC661452E5A41647`。此精确 DLL 没有另跑客户端，不把前面进程写成它的运行通过。GAME 字节一致，121 个单文件 shader／运行资源哈希一致，两个 shader ZIP 字节改变但解包后的 124／159 个条目内容一致；旧包与比较清单保留，不以文件名相同代替核验。

本轮完整构建／自动回归和最终格式、严格合规结果由证据根中的维护记录保存；没有把旧测试数量当成本轮结果。当前已确认相同源码与构件的计时和捕获开关行为；分辨率／场景、动态视觉、移动与加载吞吐不自动获得通过。

最终执行 `spotlessApply check verifyRelease verifyCompliance --strict` 成功（42 分 35 秒，160 项任务，66 执行、94 命中缓存）：GAME 322、probe 41、JNI 4 通过，bootstrap 11 通过／2 跳过，native 非 GPU 72/72、collector 2/2、分析工具 3+4 项通过，发行包 155 个必需条目及探针排除通过，严格合规 0 失败／0 警告。未修改渲染或 shader，因此没有重跑独立 GPU 测试矩阵。门禁慢段的线程快照是 Gradle 文件集合遍历，不能写成游戏 CPU 采样或 GPU 卡死；未更改模板或跳过检查。之后仅更正报告事实，另执行 Markdown 修复与严格合规，不循环重建产品。

首次维护门禁执行中增补轻量计划选择和测试，造成旧编译类与新测试的时序失配，`throughput` 选择用例失败；失败日志保留。所有修改完成后重新编译并执行门禁，不跳过断言、检查或用旧测试结果冒充本轮通过。

成功案例记结论后，按 SHA-256 核对可再生运行库与发行包内容，再送回收站；世界恢复副本也在保存关闭后回收。原始 CSV、JFR、日志、shader、源码清单、夹具及构件身份不删，外部实例不动。最终存储复查和回收清单与维护证据一同保留。
