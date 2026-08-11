# 使用者准备场景的性能诊断

## 目标

在独立项目内实例中，由使用者分别准备超多光源及普通半透明叠加卡顿场景，再使用相同探针与设置调查；高视距最后检查。默认入口不自动构造、进入或改动世界，不自动开始计时或退出，不注入故障。显式城市对照模式见下文。加载页修复在同次启动中核对到标题界面，连续视觉由使用者判断。

## 运行

从仓库根目录执行：

```powershell
.\gradlew.bat :render_diagnostics:prepareSceneDiagnosis
.\gradlew.bat :version:1.21.1-neoforge:runValidationPerformanceSceneDiagnosisClient
```

实例为本项 `instance/1.21.1-neoforge/client/`，1920×1080，主音量及音乐为 0，名称为 RecRivenVI。只加载产品、探针及登记的 Create／Sable／Aeronautics 组合。按本次使用者授权只读复制 Prism 城市及图形设置，源文件保持不变；输入身份见[性能计划](../../documents/project/plan-scene_performance.md#2026-10-18-城市综合诊断准备)。未开启主动实验权限，不执行共享启动 smoke 的自动退出；默认 GPU 取证与 Aftermath 仍按项目规则启用。

场景准备好后由 Agent 执行下列命令；`sceneLabel` 使用英文、数字、下划线或连字符，`profileSeconds` 为 1～120。每次请求有独立身份，重复读取不重复开始；文件保留供核对。也可以在游戏内用 `/radianceaudit profile start 60` 取样。

```powershell
.\gradlew.bat :render_diagnostics:requestSceneProfile -PsceneLabel=many-lights -PprofileSeconds=60
.\gradlew.bat :render_diagnostics:requestSceneProfile -PsceneLabel=translucency -PprofileSeconds=60
```

以上采样命令应分别在对应场景就绪时执行，不能连续执行以覆盖未消费请求。准备场景阶段不计入正式窗口，不开启自动相机或生产者重型归因。原始帧及分段数据写入本项 `result/profiles/<时间>-pid<进程>/`；实际设置、产物、相机及其他程序负载由 Agent 记录，来源不同时不计算严格优化百分比。

帧表记录 Java 帧尾观察到的相机、玩家眼高、地平线、世界高度和时间天气，不等于 GPU uniform 读回。native `entity.layout` 细分拓扑、审计和表面层整理／上传，GPU 增加有界 `pt-pass/` 区间；这些区间与模块及命令缓冲嵌套，不能相加。默认手工入口的正式窗口仍等使用者复现并通知，不因启动自动开始；已授权固定夹具的显式对照使用下述入口。

主世界 profile 另记录 SHARC 更新／解析区间及实际分派尺寸，解析包含现有依赖屏障，容量不是有效缓存项数。GPU 分布按实际返回样本统计，不把缺失帧填零；倒数仅作为当前实现的条件性帧预算，不能证明硬件理论上限，见[城市 GPU 盘点](../../documents/research/investigation-city_gpu_budget.md)。

### 已固定城市副本的自动对照

仅在使用者已授权、客户端已关闭且 Agent 已逐文件验证同一夹具恢复时启用。复用本项实例，不访问 Prism；须有隔离标记和 `Newport City` 副本。临时恢复工具与夹具身份见[字体对照记录](../../documents/research/experiment-font_batch_ownership.md)，不能把不同世界状态当成严格收益。

```powershell
.\gradlew.bat :version:1.21.1-neoforge:runValidationPerformanceSceneDiagnosisClient -PcityComparisonLabel=city-font-candidate
```

该选项只对这项验证运行生效：使用既有启动 companion 阻止窗口主动激活并屏蔽输入，不修改游戏分辨率、画质或来源配置。探针进入副本、打开聊天屏幕、保持已保存相机；至少预热 60 秒且队列连续平静 10 秒后采样 60 秒，随后资源重载并正常保存退出，总期限 600 秒。实际焦点、相机、计数和生成帧仍按记录判读。缺少计数不视为平静，失败停止且不自动重试。

### 显式分辨率对照

仅在使用者授权的城市性能实验中使用 `radiance.audit.cityWidth`／`cityHeight` 与可选 `cityBorderless`。两维均为正数时，探针在标题界面调整并验证实际 framebuffer，5 秒不符即停止；均为 0 时保持默认行为，不修改正常客户端和发行包。高分辨率不得把启动请求值当作实际尺寸；同组对比保持窗口方式一致。

本轮的启动 init script、尺寸失败及同夹具四档证据见[分辨率实验](../../documents/research/experiment-city_resolution_scaling.md)。有 SDK 显存预算告警时保留该结果和限制，不通过关闭功能冒充无压力基准；自然设备丢失仍保全证据并停止，不重试。

### 动态烘焙模型对照

2026-10-18 的[专项实验](../../documents/research/experiment-dynamic_baked_models.md)复用上述城市入口，派生夹具只冻结日夜与天气循环，不冻结实体或改变画质。正式窗口使用同一候选 JAR，以 JVM 属性 `radiance.rigidModels=false/true` 选择完整输入或持久模型；`radiance.rigidParts` 保持 false。生产者 census、生命周期及双路径输入核对另开诊断窗口，不能纳入正式性能对照。

本轮可复查命令、init script、实际尺寸与配置均保存在 `result/dynamic-input-20261018/<case>/`，包括 1080p、1440p 的反向顺序；没有 2160p 运行。生命周期测试不自动证明最终图像等价，GPU 时间增加也不能只凭整卡负载归因到外部程序。

### 诊断开销校准与 CPU 归因

常规 profile 使用 `TIMED`，只计时和记录轻量工作量；不自动全扫区段显存或复制整张元数据进行第二次内容比对。分配标签归因单独由 `radiance.audit.nativeAllocations=true` 开启，不再隐含在 `nativePerformance` 中；总体 VMA 观察、轻量故障追踪及 Aftermath 不因此关闭。

已授权城市实验可显式设置 `radiance.audit.cityProfilePlan`：`standard` 为一个常规窗口，`calibration` 顺序为 `FRAME_ONLY/TIMED/TIMED/FRAME_ONLY/FRAME_ONLY/TIMED`，`attribution` 顺序为 `TIMED/PRODUCERS/CPU_SAMPLES/CONTENT_AUDIT/TIMED`。`cityProfileSeconds` 每窗 1～120 秒、默认 60；窗间至少等队列平静 10 秒，仍受同一 600 秒总期限约束。`FRAME_ONLY` 保留同一探针、帧记录与基础取证，只关闭嵌套 profile 和额外 GPU 区间，不能称为无探针产品。后三类专项窗口不混入正式性能比较。

后续正式帧率 A/B 使用 `cityProfilePlan=throughput` 的单个 `FRAME_ONLY` 窗口，另跑 `standard` 作阶段归因。避免优化减少调用次数时，连带少执行的嵌套计时也被误算成产品收益；两边仍须保持相同基础取证和帧记录配置。

已经准备并核对固定夹具后，可用 Target 的显式属性选择计划；不指定时保持原 `standard` 单窗行为：

```powershell
.\gradlew.bat :version:1.21.1-neoforge:runValidationPerformanceSceneDiagnosisClient -PcityComparisonLabel=cpu-calibration -PcityComparisonPlan=calibration -PcityComparisonSeconds=30
```

`CPU_SAMPLES` 使用 JDK 自带 JFR profile 配置，执行与 native 方法采样周期 10 ms，记录容量上限 32 MiB；只在窗口结束后导出 `cpu-samples.jfr`。JFR 栈比例不是墙钟毫秒，native 方法样本也不是完整 C++ 栈。`CONTENT_AUDIT` 显式恢复内容变化率比对和每 30 帧的资源盘点，供验证诊断干扰及更新比例。

`STATUS.txt` 必须包含 `finished=true`、`valid-cpu-sample=true`，并核对预期／实际帧数、帧号连续性、Java self 分区、失败帧与 writer/native/容量丢失。坏样本保留但不参与结论；不筛掉长帧，GPU 缺失不补零。线程 CPU 时间记录观察到的正增量公约数；纳秒单位不等于纳秒精度，不能逐帧相减推算等待。

从仓库根目录分析单个已完成的窗口，`<窗口目录>` 和 `<输出文件>` 为本项 `result/` 内的实际路径：

```powershell
python components/render_diagnostics/benchmark/profile_quality.py <窗口目录> <输出文件>
python components/render_diagnostics/benchmark/jfr_cpu.py <窗口目录>/cpu-samples.jfr <输出文件> --jfr-tool <JDK的jfr路径>
```

先按冻结世界、相机、画质、实际分辨率和运行产物核对可比性，再解释计时。观察器边界开销另记在 `observer` 域，不能当作渲染工作；该域也不包含全部异步 writer 成本。

生产者 CSV 的 `renderer_calls` 是分派入口调用，不保证真正执行 renderer；`::` 嵌套模型行不能与父行重复累计生成量。JFR allocation 每线程首次权重可能包含录制前累计，工具保留边界清单并排除出类型排名，不据此声称精确分配速率；ThreadCPULoad 需按实际 JVM 的处理器归一化语义解释。结果与限制见[本轮调查](../../documents/research/investigation-cpu_diagnostic_reliability.md)。

### CPU 准备并行对照

仅用于已授权的城市实验：进程环境变量 `RADIANCE_CPU_TOPOLOGY_WORKERS=0/1/2/4` 分别选择原串行入口、带分发整理的单线程对照、总计两个或四个参与线程。默认 0；原 renderer、材质回调、覆盖层和所有 GPU 操作仍在主线程。启用时懒启动后台线程，UI 场景不分发，少量工作及重复所有者回退原路径，任务在当前调用返回前全部结束。比较各案例的实际 core 哈希、几何计数、`entity.layout.topology-dispatch` 墙钟以及 `entity.topology.background-jobs`，后者证明实际工作交给了后台线程。`entity.topology.task-service` 是跨线程服务区间之和，不能加进主线程帧时间。不能将中间原型结果归给另一最终 DLL；未进世界或注册失败的运行不计入性能通过，详见[本轮实验](../../documents/research/experiment-cpu_task_preparation.md)。

### 材质状态桥接的隔离验证

`radiance.audit.materialStateContract=true` 仅在显式城市流程、探针与隔离标记同时有效时执行真实回调、嵌套／异常恢复及 64×64 GPU 读回；进入世界和重载后各执行一次，在性能窗口外运行。原始光栅验证入口与检查保留。`functional` 城市 plan 不建立任何 profile 窗口，只在队列平静后执行重载与正常退出。

批量桥接产品默认关闭，JVM 属性 `radiance.materialStateBatch=true` 仅供隔离对照。计数另需 `JavaExec.environment` 显式设置 `RADIANCE_STATE_AUDIT=1` 并请求 TIMED 窗口；FRAME_ONLY 对照关闭该变量及 producer census。计数使用 count，bytes 为 0，不称为 GPU 命令、锁争用或全部 JNI 数。

[本轮实验](../../documents/research/experiment-material_state_batch.md)保存冻结夹具、init script、实际命令、三个构件身份、加载 DLL、逐窗口质量和通过标准结果。正式对照未证实稳定净收益；不能凭字段不变率删除请求、重排回调或跳过纹理最后写入者。

### 绘画局部几何对照

`radiance.rigidPaintings` 默认 true，受 `rigidModels` 和来源资格守卫约束；用 JVM 属性 false 保留完整动态生成作为同包控制。未知绘画／sprite Mixin、包装 consumer、不支持的姿态及光栅预览不会强行持久化。项目内显式 `radiance.audit.paintingVerify=true` 只在主动实验权限和隔离实例生效：每 97 次成功捕获抽取一个，重载前后每阶段最多 512 个 draw，用实际原生成器比较位置、法线与其余材质字节，并要求重载后模型身份不复用。

双路径功能核对使用 `functional` plan，不建立正式性能窗口。`PAINTING_SOURCE` 记录 Mixin 选择阶段发布的目标冲突结论和有效资格；`PAINTING_MODEL_PROBE` 必须在重载前后均有非零检查并 PASS。正式 `throughput` 窗口关闭该开关及生产者 census；不得把少执行诊断的成本算成产品收益。限定来源和 CPU 输入比较也不等于所有自定义 renderer、动态视觉或最终像素已验证。

[绘画专项](../../documents/research/experiment-painting_persistence.md)分别固定原型 ABBA、最终默认构件及实际 DLL。冷 `timings.csv`／`counters.csv` 在对应 profile 的 `evidence-archive.zip` 中，索引保留 SHA-256；复算上面的 Python 分析器前须按索引恢复原 CSV，分析器没有自动读 ZIP 的能力。原始帧表、质量结论和元数据保持原文件，不能用不同构件的数据补齐缺失窗口。

## 通过标准

- 启动日志包含 `radiance`、`radiance_probe` 与 `Native diagnostic collector attached`，到达标题且无加载错误；默认 Aftermath 是否真实接通以设备能力与 agent 日志为准。
- 默认实例等待手工操作，不触发 `AUDIT_SMOKE normal-stop`，不自动进入世界或开始性能计时；显式城市对照须按上述边界结束，并报告资源重载字体违规计数和真实关闭结果。
- 每个已请求的窗口有对应场景标签、原始帧／CPU／原生／GPU 阶段及结束摘要，GPU 未返回样本不记为零；不将重叠阶段直接相加。
- 自然设备丢失立即停止本用例、保全现有数据，不自动重启；故障根因与性能结论独立。
- 尚未准备或未采样的场景明确待验；仅启动不构成性能通过或加载页视觉通过。
