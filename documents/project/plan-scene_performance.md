# 多光源、半透明与高视距性能专项

2026-10-31，按使用者新的独立授权完成了[当前版本全链路审查与对照](../research/audit-renderer_work_precision_lifetime.md)，其基线、正常反弹数、观测口径及结果独立于下列历史实验。本轮保留减轻显存压力的实现；未证明首次加载改善，也不将本轮许可延续为未来自动测量或恢复其他算法设计任务。

已完成[CPU 任务减负与几何准备并行实验](../research/experiment-cpu_task_preparation.md)：减少空容器与空记录，稳定输入的拓扑准备可并行但默认关闭，尚无稳定整帧收益。最终串行世界流程成功，最终两线程注册失败另列；原 renderer、材质、完整几何与资源退休合同保持。

使用者随后搁置多线程并授权继续排查。[材质状态链调查](../research/investigation-material_state_cost.md)之后已完成[相邻状态桥接批量化实验](../research/experiment-material_state_batch.md)：请求／context 减负成立，ABBA 未证实整帧收益，因此批量化默认关闭。真实回调、Mojang 缓存、纹理写序和帧／视图状态保持，不以全局相同值过滤替代恢复。

随后获授权实施[绘画局部几何持久化](../research/experiment-painting_persistence.md)：实际减少每帧 13,632 顶点、223 个动态 BLAS 输入，TLAS 实例总数不变；保留逐格光照和材质状态。原型四窗与最终配对分别取得约 14.74%／14.59% 的真实帧时间下降，来源守卫、实际输入比较及重载验证完成；不同构件和失败候选分开记录。最终源码与产物身份以专项清单为准，没有同时扩大持久模型来源、全局场景表或线程范围。

此前已完成[诊断效率与 CPU 可信度专项](../research/investigation-cpu_diagnostic_reliability.md)。正常计时与生产者／JFR／内容盘点分窗，故障取证保留；该阶段先交付优化依据，随后 CPU 实施见上面的专项。没有复跑 4K，也不将更换观察器后的数字直接接入历史优化百分比。

## 目标

后续结构性优化统一纳入[渲染接管推进计划](plan-render_takeover.md)；本文件保留已执行的场景、诊断及比较安排。2026-10-20 使用者暂停当前及后续阶段性能对比，直到重新明确授权；现有待办和历史命令不构成恢复计时许可。接管层的正确性对照与必要功能测试继续。

2026-10-17，使用者确认普通半透明改进的视觉验收通过，同时报告部分半透明叠加场景明显卡顿。将此问题、多光源数量相关卡顿和高视距卡顿合并为一轮定向性能调查与优化，先由使用者准备前两种场景，高视距最后检查。视觉通过不等于性能或长期稳定性通过。

## 范围

先完成已经定位的 NeoForge 加载页淡出状态修复，再使用[场景诊断实例](../../validations/performance-scene_diagnosis/validation.md)挂接现有帧／原生／GPU 探针。保留画质、完整几何、当前可切换半透明、物理发光、历史和退休合同；不同时重写云、折射或优先级重复成像，不先假定所有卡顿同源。

只使用该验证自己的项目内实例；按随后授权只读复制 Prism 城市存档和图形配置，不在 Prism 运行或写入。不使用 Computer-Use，不提交或推送。自然设备丢失时保全证据并停止，不自动重试、修改 TDR 或注入故障。

## 步骤

1. **完成**：限制加载页 fade 的作用范围，嵌套菜单、结束帧与异常收尾的自动验证和配套构建通过；连续过渡的目视结果仍由使用者确认，启动核对随下一步记录。
2. **完成**：已启动 1920×1080、静音的独立诊断实例，核对产品、探针、实际原生库和默认 GPU 采集。当时等待使用者准备超多光源及半透明卡顿场景，没有自动进入世界、取样、移动或退出；就绪不等于场景测量完成。
3. **进行中**：使用者先后准备半透明与多光源场景，已记录设置、实际 PT／降噪／升频／插帧状态、加载队列、来源产物与外部负载边界；相机坐标与可重复世界夹具尚未冻结，不复制活跃存档。
4. **完成**：按使用者实际准备顺序，先取得半透明 2,435 帧，再取得多光源 1,921 帧，各 60 秒，包含真实帧分布、CPU／native／主队列 GPU、JFR 及部分板卡遥测。此步骤只完成现场采样，不等于因果或优化通过，缺口见[首轮调查](../research/investigation-scene_performance_baselines.md)。
5. **进行中**：已完成[三个现场的初步盘点](../research/investigation-three_scene_paths.md)：半透明 GPU 分层、多光源现场的轮廓／拓扑 CPU 成本，以及天空显示条件影响传光的边界。补齐子阶段与相机观察后继续确定最小必要修复，不通过删光源、减画质或缩短追踪制造收益。
6. **完成**：城市副本与新探针已启动，使用者报告 32 视距加载完成后授权捕获，已取得 60 秒、1,065 帧的[固定相机现场](../research/investigation-city_loaded_r32.md)。字体弱引用范围修复已获后续授权并完成同夹具对照，持续 CPU 入队成本已独立细分，字体专项没有修改配对算法，后续优化见步骤 9；未自动运行 16／32 或实体数量 A/B，不把不同负载和候选的结果混为优化收益。
7. **完成**：按真实字体批次所有权登记／注销，保留旧页退休、同名替换及 F3+T；两个同夹具的 60 秒窗口、自动资源重载及正常保存关闭支持保留修复，不宣称全部长帧消失。
8. **完成**：补 native 入队聚合计时与输入计数，第三个 60 秒窗口定位到小几何配对资格扫描；该定位阶段不改配对语义。
9. **完成**：按随后授权将小桶查找改为栈上固定容量表，保留配对语义和 CPU 修复；旧算法对照、完整构建、非 GPU 69/69、相关 GPU 3/3 通过。两个新的同夹具 60 秒窗口显示资格检查 10.343→1.540 ms、整帧间隔 46.221→37.799 ms，见[专项记录](../research/experiment-reverse_polygon_lookup.md)；动态 tick、外部负载和短时样本限制保留，不自行扩大下一项优化。

## 结果

本轮已接受的视觉结果对应半透明 JAR `DB5D972E5CC97D7CC3AD6E68C2788683EED37D9AD2FB1BDEFA2B0E271E2CAB3F`，core `C80B26C8819B797C793B3FA1F975DB322437C953DBFBB3351E0B39DF93D3DFFA` 的既有投放记录；没有再次读取该手工进程，不补写未反馈组合的逐项通过。明显卡顿为使用者观察，原因、规模和优化收益均未测定。

探针沿用已有分段与有界输出，只增加显式本地请求入口，使 Agent 能在使用者准备好场景后开始采样。请求不改变相机、图形或世界状态，单次 1～120 秒，旧进程请求不跨启动重放；详细运行和证据路径以验证说明为准。诊断开销尚未在本轮量化。

### 本轮构建与自动验证

执行 `spotlessApply fixMarkdown check verifyRelease verifyCompliance --strict` 及诊断实例准备通过；GAME 311、probe 30、bootstrap 11 项通过／2 项跳过，原生非 GPU CTest 69/69，JNI 头文件与发行结构核对通过，严格合规零失败／零警告。加载页状态测试由 3 项增为 6 项；本地采样请求的 2 项测试覆盖身份去重、跨进程陈旧请求、标签、时长和读取容量边界。未修改 native 产品或 shader，不把先前 21 项 GPU 测试冒充本轮执行。

首轮完整检查在两处文档问题停止：新增列表空行以及标题改名后的旧锚点。修正后重跑上述命令成功，初次失败与重测日志均保留。没有删除或放宽检查。

新发行 JAR 为 `453B4EE515F1198D91710AD7D7FB9A476DE471637A369B98AAB437709E3D188D`，内嵌 GAME 为 `D028C950FE489F9CCE246A2ABF5EA4E98EC9C54DEE2BBA26889E827401D45712`；core 仍为 `C80B26C8819B797C793B3FA1F975DB322437C953DBFBB3351E0B39DF93D3DFFA`，构建安装与 JAR 内嵌一致。机器清单为 `D:/Workspaces/Scratches/RadianceSemanticsAudit/loading-fade-artifact.json`；源码增量及逐文件回读清单为同目录 `loading-fade-source-delta.zip` 与 `loading-fade-source-manifest.json`，其父清单保留原有完整基档与前一增量，不把 HEAD 单独当作脏工作区身份。

启动前仓库 15.703077 GiB、验证生成内容 2.805345 GiB、外部受保护输入／证据 7.979428 GiB，硬上限通过；外部软超沿用既有保护边界。本轮不清理历史证据或恢复备份，不修改 Prism，不暂存、提交或推送。

### 启动和使用者补充反馈

实际开发客户端 PID 46560 已进入使用者操作的单人世界。native collector flags=3 接通；Aftermath agent 在 SERVICE 前启用，实际设备 `aftermathFlags=3`、diagnosticsConfig、device fault、checkpoint 和对象命名能力均留存。加载的 core 与上述构建／内嵌哈希一致，收据见同目录 `scene-diagnosis-loaded-modules.json`。随后收到两处就绪反馈后才请求对应窗口，见首轮调查；未测任何优化收益。

使用者确认加载页淡出问题修复通过。同时报告 Early Window 到菜单的长未响应，当前进程在原生重建范围内有 103 秒日志间隔；问题与证据边界另记[启动调查](../research/incident-startup_handoff_delay.md)。不自动重启、打断正在准备的场景或将它直接归因于 RR。场景准备、正式采样与后续优化仍分别记录。

当前两场景并非同条件互比：内容及焦点状态不同；半透明表现为 PT GPU 成本较高，多光源现场表现为 native 动态几何布局与来源线请求较重。仍须固定夹具与细分宽阶段，再做有依据的优化；不改产品或着色器来强行取得预期收益。

### 2026-10-18 城市综合诊断准备

PID 46560 已正常退出，控制台保留保存、停止与 Aftermath 正常禁用记录；没有新的设备丢失或 GPU 转储。两次旧采样之间删除并重建过同名 `New World`，因此剩余存档不能充当第一份半透明样本的严格复测夹具。

只读复制 Prism 的 `Newport City`，202 个文件、147,392,754 字节，不含会话锁。原存档复制前后逐文件 SHA-256 一致；新输入指纹为 `08F9DF933170604DD25AA466C58C16A39D3238830FA298D0465EBE785099067E`，不冒充 10-15 的旧城市输入。收据为 `D:/Workspaces/Scratches/RadianceSemanticsAudit/city-scene-copy-20261018.json`，原始夹具与可玩副本均在本验证实例内，原 `New World` 保留。

保留来源的 32 视距／32 模拟距离及 PT、SR/RR、Reflex、FG 等图形设置。项目准备任务只将本地副本调为 1920×1080、主音量／音乐 0；来源设置不变。相机和时间天气暂不自动修改，由使用者准备现场后再冻结窗口。

新增诊断仅细分 native 拓扑、审计、表面层整理和上传，增加有界逐 PT pass 的 GPU 时间戳，以及每帧 Java 相机／世界状态。旧 `entity.layout` 的 self 因子阶段拆分而改变，不能当作性能改善；GPU pass、模块和命令缓冲区间重叠，不能相加。没有修改 shader 或渲染规则。

构建前按存储政策，将已停用的 native `build/c/` 的文本证据校验归档后送入回收站；当前 `build/v/`、源码、第三方依赖和运行证据保留。收据为同证据目录的 `obsolete-native-c-build-recycle.json`。

补强探针后，`spotlessApply fixMarkdown check verifyRelease verifyCompliance --strict` 通过；GAME 311、probe 32、line JNI 4、bootstrap 11 项通过／2 跳过，非 GPU CTest 69/69，JNI／分发核对通过，严格合规零失败／零警告。新增测试覆盖相机 CSV 与区域设置，以及 GPU 标签复制、96 区间容量、遗漏和非阻塞读回；GPU 计时器单元测试使用假 Vulkan 函数，不称作真实 GPU 执行验证。

首次编译因 observer 局部变量重名失败，改名后重测通过。随后发现 Java GPU 合成测试默认寻找已停用的 `build/c/` 而跳过，改为当前 `build/v/`，最终实际执行两份翻译 shader 并通过；没有删除或放宽检查。三轮日志分别为 `scene-diag-detail-build.log`、`scene-diag-detail-build-retest.log` 和 `scene-diag-detail-final-check.log`。

新 JAR 为 `9D81A176ECDF6BAAEAC632B997876B82392122C259FF725B1840F54990A4D35C`，core 为 `81A42375A51E0843FA41E6C5B55D830FB07C939974D1511BC8295EEDC990533A`。源码清单和增量为同证据目录的 `scene-diag-detail-source-manifest.json`、`scene-diag-detail-source-delta.zip`，按清单列出的有序父档重建、逐文件 SHA-256 核对；构建与 JAR 内嵌核对为 `scene-diag-detail-artifact.json`。本轮不重新宣称全部 native GPU 用例、世界功能或性能通过；逐 pass 和相机诊断的真实窗口接通仍待使用者复现场景后确认。

### 2026-10-18 字体登记修复与入队定位

随后获授权修复字体登记范围并细分入队；使用者操作结束后的项目内存档正常关闭，才固定同一城市 ZIP 夹具。两个字体窗口及第三个入队诊断窗口均完成资源重载、各维度保存与关闭，字体违规为 0，没有设备丢失或 GPU 转储。短时对照、热点数据、焦点／JFR／世界 tick 差异及未覆盖边界见[专项记录](../research/experiment-font_batch_ownership.md)。

当前发行 JAR 为 `A3D7C86388E9D94A9DB458A76893DC135DB6574346FC649C9B8F6B69FE7C087A`，core 为 `EB19B335A24FC4DC5AC202B164B8F32038CF5BB19C9A40ECD1EB47D6F74F7952`。产品、JNI、分发与完整构建检查通过；新增 native 代码只有 observer，GAME 只有两个字体生命周期类改变。未投放 Prism，不暂存、提交或推送。

完整源码清单为 `D:/Workspaces/Scratches/RadianceSemanticsAudit/font-owner-final-source-manifest.json`，按有序父档与对应 delta 逐文件校验，包含必要未跟踪源码、测试、构建配置与固定 gitlink。候选与最终记录清单分开，不把原 core 的字体 A/B 当作新诊断 core 的额外优化证据。运行解包副本按哈希保留实际身份收据后回收，夹具、原始帧表和 JFR 保留。

### 2026-10-18 小几何查表优化

资格检查优化与旧算法的结果、异常和读取顺序对照通过。新基线 PID 100568 与候选 PID 54424 均使用同一已校验城市 ZIP、相机与图形设置，不启用 JFR；两边原始帧、输入计数、资源重载、保存、native 关闭和默认取证保留，未发生设备丢失。收益与 GPU 区间不得相加，不把候选较小的平均间隔写成最大间隔也改善。

候选 JAR `78EFAEA43A5669B1401FF8BF8F48469638EEE8593AF8BA87D56F7FA9A100A350`，core `F9EA8DA7C2BDE40C21AFA93EC0B3DDA007A82A2E7EDEFFBB0CC038B0EFD24B64`；GAME 身份与前一包相同，构建／内嵌／实际加载 core 一致。证据目录仍为 `D:/Workspaces/Scratches/RadianceSemanticsAudit/`，源码范围由 `pair-table-candidate-source-manifest.json` 和维护结束后的 `pair-table-final-source-manifest.json` 分别固定。未投放 Prism，不提交或推送。

### 2026-10-18 SBT 内容复用

按后续授权先用新 observer 取得实际重复率，保持旧上传行为，再实现每个 SBT 最近版本的精确复用。同尺寸内容变化也重新准备不可变缓冲，已记录的版本随实际 GPU 完成退休；pipeline 重建后使用新对象与新 palette，重载不读旧 handle。

新基线 PID 88068 与候选 PID 107796 各一个 60 秒窗口，复用同一城市夹具、相机和设置；资源重载、字体违规检查、各维度保存、native close 与默认 Aftermath 均正常。SBT CPU 包含时间 1.928→0.135 ms，正式窗口逻辑上传 6.54 MB/帧→0；实际帧间隔 40.168→34.496 ms，p99 62.433→49.004 ms。静态几何与实例数一致，其他阶段也变化，单轮净观察不作为缓存独占收益或所有场景保证，详见[方法和限制](../research/experiment-sbt_content_reuse.md)。

完整配套检查通过，该轮包及源码依 `sbt-candidate-artifact.json`、`sbt-candidate-source-manifest.json` 与维护后的 `sbt-final-source-manifest.json`；GAME 和 shader 内容不变。尚未新增人工视觉、多视图客户端、加载吞吐或长期稳定验证；不扩展其他优化，不提交或推送。

### 2026-10-18 静态追加与元数据准备

按后续授权先取得新基线，再依实际变化率实施六表分段上传、受限精确复用和批量屏障。初版缓存副本成本与三轮候选的真实结果均见[专项实验](../research/experiment-static_scene_tables.md)，不混用上一轮 SBT 的收益。最终逻辑静态中间复制少 13,381,016 字节/帧，世界准备 CPU 10.347→8.921 ms；整帧 36.728→36.465 ms 基本持平，p95/p99 未改善，未确认整体提速。

四轮同夹具有界流程完成重载、保存与正常关闭，无设备丢失；没有新增人工视觉、移动相机性能或长期运行结论。最终配套构建／自动回归、当前源码与实际加载 DLL 由 `metadata3-candidate`／`metadata-final` 记录固定。相机和历史更新保留，动态几何随后按新授权处理；本轮不再增加实验，不投放 Prism、不提交或推送。

### 2026-10-18 GPU 预算与理论边界

2026-10-18 已完成[GPU 盘点](../research/investigation-city_gpu_budget.md)：补齐 SHARC 分段和实际分派尺寸，正常城市窗口 CPU 1,625／GPU 1,623 样本；世界 PT 约 12.073 ms、主队列约 16.878 ms，SHARC 更新／解析约 0.217／0.160 ms。当前平均 GPU 预算仅供约 59～62 FPS 的条件性参考，不能证明硬件理论上限或稳定 60 FPS。Nsight 连接后因驱动硬件计数权限失败，未取得 GPU Trace；未改全局权限或频率，没有设备丢失。

后续 CPU 动态输入持久化仍需实际生产者归因；GPU 首选细分世界 PT 的遍历／材质／透明路径，不优先重写成本较小的 SHARC 或 GPU 顶点转换。不以降低质量、删几何或冻结动态换帧率，不自行启动下一项优化。产物、自动回归和实际运行证据见该记录；不投放 Prism、不提交或推送。

## 2026-10-18 四档分辨率对照

按新增授权完成 720p／1080p／1440p／2160p 的同夹具实测，完整结果、首次尺寸失败和预算边界见[分辨率实验](../research/experiment-city_resolution_scaling.md)。本轮采集结束，不重复 2160p 预算压力，不追加算法优化；后续先分离 GPU 预算／驻留与计算成本，并继续以动态输入生产者证据安排 CPU 工作。

## 2026-10-18 动态烘焙模型与显存归因

随后获授权先处理动态几何生产到 native 提交，复用已有 census 和计时，不恢复独立回放或修改场景总表。PID 116968 的独立归因窗口确认 ItemFrameRenderer 占城市实际 PBR 顶点约 71%，而 Chest／Bed 的高分派次数多数没有生产网格。旧 `radiance.rigidModels` 默认 false；不是因为场景里的烘焙几何完全无法复用。

产品范围只改 `RigidModelCapture`：启用满足既有合同的 SimpleBakedModel 路径，并用有界最近 recipe 精确比较减少 warm 克隆；新增 5 项生产 Recorder 接缝回归。原 renderer、材质回调、模型变化、回退次序、纹理代际、实例历史与 native 完成退休不变；ModelPart 默认关闭，绘画、程序化弹簧和其他未覆盖来源不强行实例化。

游戏内生命周期另跑 8 阶段并 PASS，包含重载、删除替换、共享姿态、glint／轮廓回退和 F3+A；该流程的非完整 profile 不进入性能对比。相关原生 GPU／生命周期 3/3、非 GPU 71/71、GAME 319、probe 34、JNI 4、bootstrap 11 通过／2 跳过，配套构建和严格合规通过。首次 CTest 路径不可用、基线 JAR 放错验证目录导致合规失败及实际纠正均见[专项](../research/experiment-dynamic_baked_models.md)，没有删除检查。

正式 A/B 使用同一冻结产品，在原城市副本上仅固定日夜与天气循环，两个分辨率采用反向顺序；每次恢复同一初始文件，预热并等队列平静后采样 60 秒，再重载、保存关闭。完整生成字节下降不等同实际 PCIe 流量；少动态 BLAS、多 TLAS 实例的成本一并报告，不把 CPU 节省直接加到 GPU 或整帧。当前专项没有 4K 运行、Prism 投放、Git 暂存、提交或推送。

2160p 读取旧 `res2160c` 与分配代码，区分 VMA 总分配、部分已挂接 kind 统计、逻辑纹理载荷及 WDDM 驻留。SDK／驱动私有分配和跨进程驻留未完整覆盖；条件资源分配仅列为候选，未以关闭功能、删除几何或缩短在途寿命修预算。正式窗口、源码／产物与收尾证据位于该验证的 `result/dynamic-input-20261018/`。
