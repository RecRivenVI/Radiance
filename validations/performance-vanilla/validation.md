# 纯原版城市性能诊断

## 目标

对冻结的新原版城市区分 CPU、GPU 与等待，拆分成本并比较视距规模；也用于原版 renderer 替代的同条件候选对照，不混入第三方游戏模组。其他线程可以并行，其负载逐窗记录。历史诊断的产品身份不因后续候选而改变。

## 运行

从仓库根目录先编译探针；依赖任务可能重包组件，运行器始终使用冻结产品，不使用该重包结果：

```powershell
.\gradlew.bat :version:1.21.1-neoforge:probeClasses :version:1.21.1-neoforge:probeTest
.\gradlew.bat :render_diagnostics:runVanillaPerformance -Pcase=r32-1080
.\gradlew.bat :render_diagnostics:runVanillaPerformance -Pcase=r32-1440
.\gradlew.bat :render_diagnostics:runVanillaPerformance -Pcase=r24-1080
.\gradlew.bat :render_diagnostics:runVanillaPerformance -Pcase=r16-1080
.\gradlew.bat :render_diagnostics:runVanillaPerformance -Pcase=report
```

每次恢复同一个城市 ZIP，保持原相机、模拟距离和画质，只改变窗口尺寸或渲染视距。至少预热 60 秒并要求队列连续平静 10 秒；先 FRAME_ONLY，再 TIMED，最后 JFR_ONLY。详细 native TIMED 限 10 秒，避免先前出现过的原始表容量耗尽；其他窗默认 30 秒。自动实例静音、无焦点控制与输入隔离，关闭 FG 的既有夹具设置保持不变。

原阶段 6 的历史 ABBA 见[替代实验](../../documents/research/experiment-vanilla_renderer_replacement.md)，已被收尾候选取代的旧二进制不再作为可执行默认值。当前产品、基线与探针从下文阶段 3 的冻结清单读取，不继承上次运行的全局路径。每窗仍保留 p95/p99、CPU、GPU、阶段及干扰数据，不把帧数不同的进程拼成一个样本。

2026-10-20 有一次分析任务与 24 视距重叠，已在 `result/analysis-interference.json` 留痕；补充窗口使用以下命令，不是新候选或完整性能矩阵：

```powershell
python -X utf8 components/render_diagnostics/benchmark/vanilla_performance.py r24-1080 --plan throughput
```

JFR 解析在客户端全部关闭后进行，使用 `components/render_diagnostics/benchmark/jfr_cpu.py <录制文件> <输出文件> --skip-allocation --jfr-tool <JDK的jfr.exe>`；录制与工具路径从该案例和启动 JDK 取值。执行样本保留深栈，allocation 不在本轮分析范围，原始 JFR 仍保留。

第一节阶段 3（原阶段 7）先在已正常保存的楼内入口做短窗来源核销，不把计数窗口用于性能结论：

```powershell
python -X utf8 components/render_diagnostics/benchmark/takeover_instancing.py census
python -X utf8 components/render_diagnostics/benchmark/takeover_instancing.py analyze-census
```

候选的限定复核用 `census --product <仓库内发行包路径>`，结束后恢复人工入口原包和设置。来源探针不修改原版 renderer 或 ModelPart；云的单独驻留构建与动态实体提交分别计数。首次原始数据仍保留其计数范围差异。

在 `takeover-instancing/manifest.json` 固定产品与探针、`result/instancing/fixtures.json` 固定输入后，只执行一组楼内 r8／1080p、城市 r32／1080p和1440p的 ABBA：

```powershell
python -X utf8 components/render_diagnostics/benchmark/takeover_instancing.py prepare-fixtures
python -X utf8 components/render_diagnostics/benchmark/takeover_instancing.py pairs
```

基线是阶段 2 收尾验收包；沿用原世界内容、动画、质量和模拟设置，只控制规定的视距／分辨率。每个进程先 FRAME_ONLY 20 秒，再 TIMED 10 秒；并行游戏和整卡遥测逐窗记录。运行器仅跳过清单中已完成的进程，不自动重复失败；自然设备丢失停止整组。冻结输入不在 A／B 之间重新保存。

### 接管层增量工作续项

2026-10-24 的来源登记候选只比较未安装来源的重复探测，保持原有模型、动画与完整场景。先完成 GAME／probe 自动测试和 distributedJar，再冻结实际构建与探针，执行一次 32 视距 1080p ABBA：

```powershell
python -X utf8 components/render_diagnostics/benchmark/takeover_incremental.py prepare
python -X utf8 components/render_diagnostics/benchmark/takeover_incremental.py pairs
python -X utf8 components/render_diagnostics/benchmark/takeover_incremental.py report
```

入口复用上面的正常保存、源资格、产物身份、队列平静、干扰记录及失败停止机制。冻结清单已存在时拒绝替换；不把缺席来源判定写成完整增量实例已完成。当前来源与归因窗的结果见[本轮研究](../../documents/research/experiment-takeover_incremental_work.md)。

已撤回生产集成的静态区段 CPU 记录原型保留独立清单 `takeover-incremental-scene/manifest.json`。当前工具只重算历史证据，不再运行旧包；完整源码、实验测试及当时的运行器在[研究记录](../../documents/research/experiment-takeover_incremental_work.md)引用的快照中，恢复实验需单独授权：

```powershell
python -X utf8 components/render_diagnostics/benchmark/takeover_incremental.py report-scene
```

当时 verify-scene 每帧比较缓存与原生成器的完整实例记录及历史，保留正常 F3+T／保存关闭门槛；正式 ABBA 关闭双执行检查。同一个发行包通过单进程环境 `MCVR_STATIC_INSTANCE_CACHE=0/1` 选择 A/B，缺席来源修正两边始终保留，不混入第一组收益。该原型仅省 CPU 记录生成，未消除完整 TLAS 输入、GPU 构建或全部元数据比较。

最终候选在完整构建／自动检查后冻结并执行一次不计时的城市加载、F3+T 与保存关闭。此入口拒绝覆盖已有最终清单，不重跑性能对照：

```powershell
python -X utf8 components/render_diagnostics/benchmark/takeover_incremental.py final
```

### 使用者配置的加载全链路

2026-10-24 新基准使用 `prism_rendering_baseline`，保留实际视距、模拟距离、DLSS SR、SHARC、独立着色器参数和调度设置。世界沿用已冻结的纯原版城市，不复制或修改正在使用的世界。产品使用已验证的最终候选，探针单独构建并固定：

```powershell
.\gradlew.bat :version:1.21.1-neoforge:probeClasses :version:1.21.1-neoforge:probeTest
python -X utf8 components/render_diagnostics/benchmark/loading_chain.py prepare --distance 32 --resolution 2160 --runtime --manifest validations/performance-vanilla/result/loading-chain-runtime-manifest.json
python -X utf8 components/render_diagnostics/benchmark/loading_chain.py run --manifest validations/performance-vanilla/result/loading-chain-runtime-manifest.json
```

从打开世界开始连续记录，进入后在逻辑服务端执行 `tp @s 0 512 0 facing 0 0 0`，按原版命令语义落在 `(0.5,512,0.5)`、朝向目标点，保持夹具原有创造飞行，不移动。使用者追加要求将本轮调整为 32 视距、3840×2160，其他画质与调度配置保留；自动实例继续覆盖主音量 0、失焦不暂停和窗口模式。先前 16／1080p 运行按使用者决定作废，不参与性能结论；其中发现的探针坐标判定和异步写盘收尾缺口在重测前修正。所有 Java/native 队列为空、磁盘读取／生成／客户端到达计数持续 10 秒无变化后，记录另外 20 秒平静窗口；最多 240 秒，未取得平静明确报告未取得。停止采集按帧边界执行，等待统计写盘后正常退出。平静不等于视觉收敛或完整显示证明。打开世界时嵌套加载帧合入外层墙钟，不覆盖外层计时；初始加载与传送的边界帧不作为固定视角稳态帧。

保留完整时间轴、帧与工作线程计时、异步 GPU 返回样本、区块批次墙钟与进度。逐区段 trace 会改变场景准备，正式性能入口保持关闭；批次提交至完成包含排队和轮询，不作为纯 GPU 构建。每区段首次使用身份、服务端请求／I/O／解码精确拆分仍是覆盖缺口。JFR 仅采样、上限 32 MiB，不将样本比例当作精确独占耗时。正常保存关闭、来源资格与完整 STATUS 分别检查；设备丢失保全并停止，不自动重试。

正式基准另读 `prism_runtime_baseline`，使用源实例继承的 512 MiB 初始／16 GiB 最大堆；Java 同为 Eclipse Adoptium 21.0.11，源额外 JVM 参数为空。首个有效 32／4K 案例沿用了项目默认 8 GiB，只作为独立初测，不与对齐运行混合。`prepare` 拒绝覆盖已有冻结清单；重复测量只能在明确授权后使用同一清单显式运行，不自动重试失败。

### 区块流水线的 1080p 对照

当前完整加载比较采用全程 FRAME_ONLY，短热点采集才使用 TIMED／JFR。平静判据还要求 native 区段输入、批次准备、提交与完成的区间计数无变化，防止短批次在两个空队列采样间漏过。既有 4K 历史结果不重写。

先按上面的 Gradle 命令构建探针和发行包，再使用未占用的清单名和运行标签；下面的 `<name>`、`<case>` 与 `<product>` 分别为本轮新名称、唯一进程标签和仓库内已构建发行 JAR 路径，冻结后不得覆盖：

```powershell
python -X utf8 components/render_diagnostics/benchmark/chunk_pipeline.py freeze-probe <name>
python -X utf8 components/render_diagnostics/benchmark/chunk_pipeline.py freeze <name> --product <product>
python -X utf8 components/render_diagnostics/benchmark/chunk_pipeline.py run <name> --label <case>
```

`freeze --detail` 是最长 60 秒的独立 TIMED 热点窗，不作为完整加载时间。`freeze --movement` 则在初次平静观察后，从 `(0.5,512,0.5)` 沿 X 方向每 250 毫秒请求一个 8 格步点，到 256 格处返回，再等待平静；最多 360 秒。只保留一个未完成的原版服务端传送，并等待客户端确认，不阻塞渲染线程或跳过步点。此路线是受控流式加载负载，不是玩家步行视觉或输入延迟验收。分析将初始加载、平静、路线和返回后平静分开。

`freeze --interactions` 为独立交互窗：先平静后在区段边界交替正常放置／挖掘石头、玻璃、海晶灯及树叶，再移动 256 格后重复。只修改还原后的验证世界；不强制全区块刷新，不切换游戏模式，不以程序直接改块代替玩家操作。每次记录当时队列，第二窗是否真正处于后台加载按实测状态判断。依赖标脏、修订、原生构建／发布、场景记录与完成帧关联；空几何删除对应完成帧的缺席记录。Java/native 时钟用 JNI 调用前后夹定，保留校准区间和缺失／被合并的操作。追踪会增加槽位巡检，只用于延迟及正确性，不用于正式 FPS 收益；每窗 12 次不足以估计可靠的长期 p99。

每次从同一世界 ZIP 还原；不复用上次已生成的世界。保留随机世界活动、后台干扰和操作系统缓存的局限。工具失败停止且保留 STOP，不自动重试。客户端正常保存关闭后归档原始文本并回收其复制环境。

## 通过标准

每窗有完整 STATUS 和连续帧号，真实帧间隔、线程 CPU 与 CPU 墙钟分别记录；GPU 以同窗返回的完整帧取分布，不补零，不相加嵌套区间。生产者 profiling Mixin 不得让 PARTS／BAKED／STATIC_STATE 退回；JFR_ONLY 不开启 native 阶段计时。原始表、JFR、进程与整卡遥测归并后，只保留报告引用的数据和摘要。

其他游戏进程与 GPU 占用随窗口对齐，整卡读数不冒充本进程占用。正常保存关闭后回收客户端副本；自然设备丢失先保全并停止，不自动重试，不操作其他项目或 Prism。
