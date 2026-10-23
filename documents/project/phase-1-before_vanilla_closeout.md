# 原版收尾前的项目记录

2026-10-20，阶段 0～3 目视通过后固定的既有状态与证据记录；当前执行只看 [项目状态](status-current.md)。本记录保留此前候选、失败、纠正及反馈，不将旧包结果转给新包。

## 目标

保留压缩当前状态页前的可回查事实和历史链接。

## 范围

截至阶段 0～3 合并交付的本地实现、运行和验收记录；其后决定从当前状态与对应实验记录取值。

## 步骤

1. **完成**：保持原段落内容与原相对链接，将标题下移一级。
2. **完成**：旧锚点的入链迁移到本阶段记录，当前状态页只保留当前候选、后续步骤和开放项。

## 结果

截至 2026-10-20，渲染统一 A/B/C/D、附加层、查询修复及船只专项已有使用者定向通过，具体候选与范围分别保留于下文。普通半透明改进和加载页淡出已获视觉通过，部分叠加明显卡顿；城市现场已完成采样与字体所有者范围修复对照，原生入队已细分，小几何配对资格扫描已改为查表并取得同城短时收益。随后按逐项授权完成 SBT、静态表和限定烘焙模型专项；最新产物与同城 1080p／1440p 结果见末尾动态专项，2160p 没有复测。原版云顺延，手臂与其他优先级内容重复成像仅登记、暂不调查。世界几何线默认不透明，透明实现保留。结构与构建迁移、依赖更新、模板对齐及 CI 收尾已经完成，工作区模板为 2.0.2。历史设备丢失事实、GPU 根因未知及公开二进制许可边界保留，不与本轮功能通过混用。迁移的源基线为 Radiance `585c561fd3711aedcdc243715b94145d630bb1ad`、MCVR `81c08145fc8d0f7ec12dedc0f4b303a8098822e6`。不从固定提交时间推断历史验收顺序。

### 当前工作：原版实体接管阶段 3

2026-10-19，使用者批准阶段 3 与城市性能对照。阶段 0～2 的实际测试包 `03439C84…` 已按随后授权投放 Prism，源与目标完整 SHA-256 一致，其他模组、存档文件清单及配置不变，未启动游戏；回执位于 `E:/Minecraft/PrismLauncherDev/.agent-runs/radiance-takeover-deploy-20261019-222100-09bd2b3d/deployment.json`。目前未收到新的目视缺陷或逐项反馈，不据此代填此前人工清单。阶段 3 以该原包作为 B，接管前冻结包作为 A，先复核城市剩余生产者，再实施 C；不扩展阶段 4 以后的来源，不再次操作 Prism。

范围、原始输入、首次失败及实际进度统一写入[阶段 3 实验](../research/experiment-entity_takeover.md)和[计划](plan-render_takeover.md)。2026-10-20 使用者随后暂停当前及后续阶段的性能对比，城市六次对照不再作为阶段 3 门槛；Goal 不变，继续正确性核对并交付合并手工实例。已完成的 FRAME_ONLY 窗口保留为初步数据，不据此下收益结论；自然设备丢失保全并停止，不自动重试。

2026-10-20 补充：按 [adr-10](../design/adr-10-vanilla_first_takeover.md) 改用原始 1.12.2 城市存档经原版优化后的纯原版夹具，最终城市对照不再使用带模组副本。Goal 保持不变，除已核验的 Flywheel accessor 外，其他修改者变体只登记。阶段 3 完成后准备仅 NeoForge/Radiance 的阶段 0～3 合并手工实例，2560×1440、音量 50%、音乐关闭、RecRivenVI；城市与小型夹具各一入口，不在 Prism 单独验收阶段 0～2。

范围措辞由 [adr-11](../design/adr-11-complete_drawing_scope.md) 明确为当前游戏版本与已纳入依赖在各自已验证范围内的所有绘制；绘制类型统一实现、固定巡检零无负责方／零未知的完整门槛及计数阶段排期已写入计划。此次仅修订文档，不改变当前 Goal、产品语义或兼容元数据。

纯原版城市已由实际 Optimize World 升级全部 20,811 个区块到 DataVersion 3955；原高度范围检查无大面积缺失，原始输入不变。设置复核纠正派生候选误设的模拟距离，恢复原 options.txt，正式夹具 SHA-256 为 `832A650F7DC6ECBA23822D49D767FD0A609F00B3FAFF6DF3F657E2CCED0F723E`；先前设置候选不参与最终对照。冻结 C 为 `3C83DEC9C17FC9EF032713C76476BF98FA09C8E6F214EF49DEFE0E7C8E3E7CD2`；生命周期、纯原版逐来源功能、实际未登记 Mixin 及单次带模组正确性用例均正常保存关闭。155 项注册来源中 104 项完成局部输入对照、51 项有明确保留原路径的原因，不代表所有附加层均已直接化或最终像素已验收。

02:54:16 按使用者决定正常关闭当时的城市 C 客户端：完整 FRAME_ONLY 已结束，TIMED 被主动中断，退出码 0、世界各维度保存和 native／SDK 关闭有日志，无设备丢失。后续进程及 1440p 组没有启动。已采窗口的逐组初步表和 MCP 重叠说明见阶段 3 实验；旧／新诊断探针分组保留，不补齐六次或推算收益。

阶段 0～3 的统一目视入口为[合并验收](../../validations/visual-takeover_stages/validation.md)，仅包含冻结 C 和 NeoForge，两个世界分别来自新城市和阶段 2＋3 小型夹具。最终完整源码为 `validations/conformance-entity_takeover/result/source-stage3-final.{json,zip}`，可恢复差量为 `D:/Workspaces/Scratches/RadianceUnification/takeover-stage-3.zip`。目视、完整 G3、GPU 根因、长期稳定性及公开许可均未由自动测试代填；本轮没有提交、推送或投放 Prism。

### 阶段 0～2 的限定自动门槛

2026-10-19 后续授权的阶段 0～2 和含水／附加几何的地形样板已完成限定自动门槛，该批次目视仍待使用者反馈；阶段 3 已由上节另行授权，不提交、推送或自行投放 Prism。[覆盖表](../reference/registry-takeover_coverage.md)登记实际来源及未知项。[本轮实验](../research/experiment-takeover_stages.md)记录 Voxy 26,216 根／8192 驻留预算的六阶段回归、六类骨架、1,024 次原生成器对照、热窗不再构建、绘画重载／离屏读回和连续地形完成链。GAME 348、原生 CPU 74/74、GPU 22/22、完整发行与严格合规通过；来源／功能／视觉边界分别核销。

同一小型夹具的 ABBA FRAME_ONLY 静止 8.608→7.936 ms、移动 8.620→7.871 ms，下降约 7.8%／8.7%，超过本轮两次同类均值差；非城市、无探针产品或全分辨率 1080p 承诺。候选 JAR `03439C84058C273B7D42A3E2ABE067EE10DF11F59FFC179FE3493C4FB17F0A8F`，core `E08BAFCD2B9A54F49339136BDE088FEC89CE42D5D09ECAEA2D3C9C61E45D84A1`；具体身份、原始分布、实际加载及所有中间失败见实验。完整 final 源码快照含必要未跟踪文件，阶段差量位于既定 Scratches 目录。人工清单、全进程显存峰值、严格 FG 后 GUI、未来来源、长期稳定性与既有 GPU 根因保持待验／未知；公开许可门禁不变。

验收原包固定于 `components/render_diagnostics/build/takeover-candidate/radiance-1.21.1-neoforge-0.1.5-alpha.jar`。末轮 Gradle 重包为 0489…，仅 ZIP 封装元数据和对应索引变化，未作为第二个验收候选；不混用它与 0343… 的运行记录。产品源码与全部有效载荷一致性检查见本轮实验；随后授权的 Prism 投放回执见本页开头。

2026-10-19，使用者随后澄清终点为[完整绘制体系接管](../design/adr-8-rendering_semantic_takeover.md)，替代最初限于几个生产者的解释：世界、GUI、离屏、程序、屏幕效果、加载和呈现均应有明确执行语义，原版与模组仅为迁移批次。接管层独立于 Flywheel，Flywheel 仅为来源之一。既有来源研究和实际 API 核对仍为局部证据；[阶段计划](plan-render_takeover.md)按能力／来源双轴推进。来源语义一比一不自动撤销既有已接受的 PT 和视觉决策。

随后授权的[关键路径非性能实测](../research/experiment-render_takeover_feasibility.md)已执行：无 Flywheel 的独立资源／实例输入最终显示红／绿控制面板，原版部件／烘焙模型 CPU 对照与离屏 GPU 数值读回通过；完整模组夹具完成线条、尺寸变化、重载与世界重进，既有 Flywheel 来源模型／实例非零。22 项 GPU 合同通过，首次 JPMS 探针包装失败与纠正单独保留。没有产品代码／shader 改动或性能测量，正式公共接管层尚未实现；任意世界程序、全部来源覆盖和完整视觉仍未证明。

后续[四项边界实验](../research/experiment-takeover_boundaries.md)完成共享资源的单实例替换、真实 UI／离屏读回与区段完成发布跟踪。SDK 可用但未观察到非零生成帧计数；严格 FG 后独立 GUI 呈现仍未闭合，标准 UI Recomposition 会处理 UI。实验支持继续阶段 0／1，不代表全来源程序、参数形变或生成帧视觉已经通过。

绘画候选 `A667187A61305E67D1E95F37ECA6F8ED3D7CE5F62710E355E687DC3763BF910B` 已按授权投放 Prism。09:35:26 出现 Voxy `Publish Voxy LOD cut: Voxy selection budget` 应用异常；保存全部维度、Voxy worker 停止和 lease 归还有日志，无设备丢失字符串，native GPU 关闭完成尚未由本次确认。当前 64 的来源距离与根集合／8192 驻留预算的合同不一致已静态核实，具体传入数组长度未记录；10 月 18 日报告有同一异常，但旧产物身份不由本次推定。证据和后续范围见[研究](../research/survey-render_takeover_sources.md#voxy-暴露了接管需要闭合的预算合同)。使用者决定先禁用 Voxy 继续验收；未反馈的绘画视觉不代填通过。

#### 前置诊断与已保存实现

2026-10-18 已完成[诊断可信度与 CPU 调查](../research/investigation-cpu_diagnostic_reliability.md)：普通计时不再隐含执行整表观察副本、全区段显存 census 或分配标签归因；保留基础故障取证和 Aftermath。两次同城隔离流程完成 11 个有效窗口；发现基础 VMA 汇报仍受分配标签门控后修正，再以独立候选完成 1,099 帧接通确认。三次成功进程均完成重载及正常保存关闭，没有设备丢失。不同候选与焦点状态分开记录，重型归因不混入正式性能结果，诊断减负不算渲染器优化收益。

已实施空容器／空记录减负与[材质状态桥接实验](../research/experiment-material_state_batch.md)；后者局部请求减少，但没有稳定整帧收益，保持默认关闭。随后实施[绘画局部几何持久化](../research/experiment-painting_persistence.md)，保留逐格光照、完整网格和原材质状态，限定稳定来源使用常驻 BLAS；正式对照与最终构件分开记录。warm rigid recipe 与进一步事务减少尚未实施。GPU、加载期、Voxy 初步适配、此前视觉和未知故障边界各自保留，不由静止城市计时替代。

### 普通半透明候选

视频设置已加入“普通半透明”，默认随机覆盖，可选择前景／背景混合后应用；两套内置 PT 分别保存设置。主成像首层普通覆盖面使用实际场景背景，专用材质、物理透射和传光估计器保留，详见[机制](../design/mechanism-ordinary_translucency.md)与[本轮实验](../research/experiment-ordinary_translucency.md)。多层降噪仍共用最近表面的深度／运动，不能把自动合成通过当作动态观感通过。

新投放 JAR 为 `DB5D972E5CC97D7CC3AD6E68C2788683EED37D9AD2FB1BDEFA2B0E271E2CAB3F`，core 为 `C80B26C8819B797C793B3FA1F975DB322437C953DBFBB3351E0B39DF93D3DFFA`。完整构建与分发验证通过，原生 CPU 69/69、GPU 21/21，两个模式各 237 份 pack 变体编译／SPIR-V 通过；其他计数与证据见实验。仅替换已停止的授权 Prism 实例，其他文件哈希不变，没有启动客户端。

以上记录该候选构建与投放当时的事实；随后半透明视觉反馈见下节，船只包的目视通过保留原产物范围。构建与投放阶段没有性能实验、设备丢失根因或公开许可结论，亦没有 Git 暂存、提交或推送。

#### 半透明验收与性能专项

随后使用者确认“半透明改进已验收”，但部分叠加场景存在明显卡顿。该反馈按本节既有投放身份保存，视觉通过不扩展为性能通过，也不反推本轮已读取手工进程的运行身份。普通半透明、多光源数量相关卡顿与高视距卡顿已合并到[同一性能计划](plan-scene_performance.md)，先由使用者准备前两种场景，高视距最后处理。

当前授权先修复下节加载页问题，再启动[项目内探针实例](../../validations/performance-scene_diagnosis/validation.md)。准备期间不自动采样、进入世界或退出，其他功能计划不同时启动；不会把既有不同候选或不同负载的计时拼成新收益。

### 加载页过渡新增反馈

2026-10-17 使用者报告 NeoForge 大红色加载页淡出变为“先淡入再淡入然后强切”。[定向静态调查](../research/incident-loading_overlay_fade.md)确认 `RadianceLoadingOverlay` 在绘制下层菜单前设置加载页淡出 alpha，和 NeoForge 21.1.259 的状态作用顺序不同，会污染全景 draw 并在覆盖层移除后产生跳变条件；尚未捕获实际过渡，不能宣称已解释全部红色变化。计时公式与原版一致，native 的双重 alpha 也不能脱离原版合同直接判错。

本次只登记、调查与同步文档，没有修复、启动、构建或换包；此前首按钮闪白修复的使用者通过反馈保留原范围。

随后使用者授权修复，已将下层 screen 的颜色恢复放入实际背景绘制边界，加载页淡出仅交给 native 合成，不改计时与原生 alpha 公式。新增嵌套 screen、结束帧与异常收尾回归；本轮构建和启动结果随后记录，不由静态公式宣布目视通过。

修复配套构建及严格检查通过，GAME 311、probe 30、bootstrap 11 项通过／2 跳过，非 GPU CTest 69/69；加载页状态回归共 6 项通过。新 JAR `453B4EE515F1198D91710AD7D7FB9A476DE471637A369B98AAB437709E3D188D`、core `C80B26C8819B797C793B3FA1F975DB322437C953DBFBB3351E0B39DF93D3DFFA`，完整证据及边界见[性能计划](plan-scene_performance.md#本轮构建与自动验证)。Prism 仍保留已验收的半透明包，本轮只启动项目内开发诊断入口，完整加载页连续视觉仍待使用者确认。

随后使用者确认淡出问题已修复，本项定向目视通过。项目内 PID 46560 的产品／探针／Aftermath 已接通，已进入使用者手工操作的世界，尚无正式场景取样。同时新增 Early Window 到菜单长未响应反馈，[本次时序](../research/incident-startup_handoff_delay.md)将 103 秒无日志区间限定在原生重建范围；阻塞期栈未取得，不认定具体 SDK、GPU 等待或驱动根因，不自动重启。

随后使用者先准备半透明、再准备多光源现场，各取 60 秒，分别为 2,435／1,921 真实帧，无 writer/native 丢弃或设备丢失。[首轮调查](../research/investigation-scene_performance_baselines.md)显示半透明主要为 PT GPU 工作，多光源现场主要为动态线／轮廓、native 拓扑／布局等 CPU 工作；两份内容、焦点不同，没有优化前后 A/B，尚未固定相机与复测夹具。客户端继续由使用者操作，高视距最后检查，不自动重启或换包。

#### 三组初步盘点与城市准备

2026-10-18，已读取实际 F2 截图，完成[三组初步盘点](../research/investigation-three_scene_paths.md)。半透明确认有逐层照明放大机制，但没有实际分支命中分布；多光源窗口包含 67 个发光轮廓对象，拓扑准备是 CPU 重点候选；玩家高度触发的黑天空条件越界作用于传光查询，完整画面因果仍待对照。没有修改渲染规则或宣称优化通过。

使用者已正常退出旧客户端，并授权只读复制 Prism 城市，输入和新探针准备见[计划](plan-scene_performance.md#2026-10-18-城市综合诊断准备)。项目内等待使用者复现，再开始正式采样，不自动运行压力矩阵。旧窗口之间删除并重建过同名世界，其结果不与新城市合并。

#### 城市加载后的最新捕获

2026-10-18 使用者反馈旧 8 视距现场可能与实体数量有关，并授权捕获已加载的 32 视距城市。已取得[60 秒、1,065 帧的诊断](../research/investigation-city_loaded_r32.md)：帧间隔均值 56.358 ms，渲染线程 CPU 55.238 ms、主队列 GPU 16.233 ms；队列观测基本平静。实体／方块实体的持续输入成本与约半秒尖峰分开，尖峰内已采到字体批次所有者登记和弱引用清理。属于原始候选的定位证据；随后授权的[字体范围修复](../research/experiment-font_batch_ownership.md)已通过同夹具的两个短时窗口、资源重载和正常关闭，原生入队已在第三个同夹具窗口细分，主要成本为小几何配对资格扫描；本轮未修改该算法。实体增减 A/B 与长期稳定性仍未执行。

#### 小几何查表候选

按随后授权完成[反向成对面资格查找优化](../research/experiment-reverse_polygon_lookup.md)：只将不超过 64 面的小桶两两比较换为固定容量查表，保留精确最终位置、绕序、CPU 拓扑修复、薄片所有权、历史和资源退休。4,612 组旧算法对照、原生 CPU 69/69 及相关 GPU 3/3 通过，完整构建、分发和严格合规通过。

两个新的同城 60 秒窗口中，输入小桶均为 897 次／68,620 顶点每帧，资格检查 10.343→1.540 ms，native 入队 12.656→3.831 ms，真实帧间隔均值 46.221→37.799 ms；p99 61.549→53.549 ms。静态几何与 TLAS 数量相同，两边均完成资源重载、保存和正常关闭，无设备丢失。活跃世界 tick、其他 GPU 负载及单次样本仍有边界，最大帧间隔未改善，不承诺所有场景的固定收益。

该轮产物、完整源码、测试、机器证据及限制见专项记录；后续 SBT 与静态表候选另列，没有投放 Prism、暂存、提交或推送。

#### SBT 命中表复用候选

按随后授权完成[精确内容复用](../research/experiment-sbt_content_reuse.md)：新取证基线 5,976 次设置除 8 次首次观察外全部相同，仍逐帧上传整表；候选正式窗口 6,960 次全部复用，逻辑 SBT 上传从 6,544,896 降为 0 字节/帧。内容变化使用新缓冲，旧版本按 GPU 完成条件退休；不同管线及重建后的 SBT 不共享缓存。

完整构建／分发、GAME 314、probe 34、JNI 4、bootstrap 11 通过／2 跳过、native 非 GPU 70/70 与严格合规通过。同一城市的新一对 60 秒窗口中，SBT CPU 包含时间 1.928→0.135 ms，真实帧间隔均值 40.168→34.496 ms、p99 62.433→49.004 ms；几何与 TLAS 数量一致，两次重载、保存和关闭正常，无设备丢失。其他 CPU/GPU 阶段也有波动，不将整帧约 14.1% 的观察全部归因于缓存，尚无新人工视觉或长期稳定结论。

该轮本地包、完整源码与实际加载身份以专项记录的 `sbt-candidate`／`sbt-final` 清单为准；JAR 为 `77825162…`、core 为 `6429702A…`。Java 类、shader 及其他资源不变，未修改历史 MCVR 仓库，不暂存、提交或推送。

#### 静态场景表分段候选

按随后授权完成[分段追加及受限复用](../research/experiment-static_scene_tables.md)：先细分耗时和九表实际变化率，再省去每帧 13,381,016 逻辑字节的静态中间复制，保持相机变换和外部结构历史更新。初版全表 key 副本的成本转移已纠正，变化表恢复批量屏障；不改变动态顶点、完整场景或 shader。

新基线与最终同城 60 秒窗口中，静态表追加 CPU 1.703→0.030 ms，世界准备包含时间 10.347→8.921 ms；帧间隔均值 36.728→36.465 ms，基本持平，p95 45.148→46.216、p99 52.573→54.134 ms，不能宣称整体提速或尾部改善。几何／实例数一致，四轮有界流程均完成 F3+T、保存和正常关闭，没有设备丢失。

完整配套检查、非 GPU 71/71 与严格合规通过；尚无新人工视觉、多视图或长期稳定验收。当前本地 JAR `98B24AA8…`、core `AFB701C5…`，完整源码与实际加载身份依专项的 `metadata3-candidate`／`metadata-final` 记录；没有投放 Prism、Git 写入或旧 MCVR 修改。

### 世界最低高度以下的新观察

2026-10-17，使用者在上述项目内进程报告方块底面与下半天空同时全黑，并停留在异常场景。[定向记录](../research/incident-below_world_sky_darkening.md)已区分原版天空显示行为与当前 PT 间接环境查询的代码边界。随后读取 F2 图像，确认玩家低于最低高度；F3 玩家坐标不是 GPU 相机输入，相机／地平线逐帧记录由新探针补齐。不能把代码候选写成全部像素的完整根因；shader 未因此变化，性能专项继续保留。

### B、C、D 连续统一

按执行书 30 连续完成来源无关的空间、目标、跨语言 ABI、材质／颜色／发光、最终位置、视图帧与共享表面函数的工程收敛；族 11 沿现有协调器与资源所有权收口，不重写退休。最终配套检查及显式 RR、NRD/FSR 的项目内功能预检通过，当前产物、完整源码、累计检查点与[计划](plan-render_unification.md)对应。

明确修正来源阈值、逐 draw 状态／丢弃阶段、独立眼睛调色、退休 FontSet 缓存、已知裁切程序的硬件 opaque 资格、FSR 连续帧时间与失败输出等问题。41 组同名异体 shader 函数保留各自上下文，不能把全部差异宣称为正确或有益。原始失败、迁移反例与重测保留在各研究记录；不以旧候选成功代替最终运行。

[合并研究](../research/experiment-render_unification_bcd.md)按族记录 12 组已修缺陷和保留差异；GAME 292、JNI 4、probe 28、bootstrap 11 通过／2 跳过，非 GPU 67/67、GPU 16/16、pack 232 编译／SPIR-V 通过。最终补齐后的进程 68932 与 2636 分别完成 RR、NRD/FSR 的 resize、重载、保存重进及正常关闭；没有设备丢失，字体／线条及三处表面违规为零。239 份原生输入审计无报告项，仍不等于全部实际 GPU 画面。族 10 后续补齐两个纯几何函数，十份正文原样收敛，旧进程保持旧范围，见[最终补齐验证](../research/experiment-surface_geometry_completion.md)。源码、构建／内嵌／提取／加载 DLL 的完整身份由计划引用的 bcd-complete 清单固定。

合并[目视清单](../development/testing-render_unification.md)已收到本轮逐组结果，当前以[最新反馈](#本轮合并手工验收反馈)为准。End Sea 与未知程序的严格接收仍待 adr-3、adr-5；全亮的物理贡献已由使用者接受 adr-6，决定与实现完成度分开记录。没有性能实验、Git 写入或 GPU 根因结论。

#### 当前投放与新反馈

2026-10-17 按使用者授权将最终 B/C/D 候选投放 Prism，未代为启动。JAR 为 `64058C04E8FFD8D5817FB3D763D4F32D8CEE82D25A17C9F12E1BED00091EC838`，core 为 `F45C284CC8D341E535D1E5257B4422B29C6E7C1F69AB704FCAD0E4A886D631EB`；安装、内嵌索引及保留文件校验通过，其他模组和设置未改。回执为 `D:/Workspaces/Scratches/RadianceSemanticsAudit/bcd-complete-prism-deployment-receipt.json`。本轮新增维护文档不属于先前产品源码快照，但没有改变产品或投放包。

使用者自行运行后报告：蜘蛛／末影人眼睛的颜色和发光缺失；充能苦力怕电弧可见且发光，但纹理不滚动。按[新调查](../research/incident-emissive_layer_acceptance.md)分别登记失败，原自动合成夹具和字段测试不覆盖这些目视结论。眼睛层的合成资格／独立层遮挡仍是候选；电弧已定位普通 PT 未传递来源 TextureMat 的缺口，未实施修复。验收继续，当前不换包、不自动启动或重试。

使用者同时要求维护[语义调整决策索引](../reference/registry-semantic_decisions.md)，并明确显示全亮几何必须实际影响其他世界几何；不允许只有显示发光却无世界照明贡献的几何政策。既有查询例外的交点与实现合规性待核，不补写目视或实现通过。

#### 眼睛与电弧修复候选

随后按使用者授权修复两项问题，见[修复验证](../research/experiment-emissive_layer_repair.md)。眼睛合成保留独立面规则和调色，身体与眼睛不再因来源剔除不同而留下重合竞争；能量层在 native 接管前保留当次主 UV 变换。相关持久模型使用合法完整捕获回退，不全局关闭优化，物理发光保留。

完整门槛通过：GAME 296、JNI 4、probe 28，bootstrap 11 通过／2 跳过，非 GPU 67/67、GPU 16/16，pack 编译／SPIR-V 各 232；严格合规零失败／零警告。新 JAR `65A2E09463350081597CC814F56B5781617D5A1B71225ED234E53EB53BC8E422`、core `328E5F7EEA9C7969ECE37F8BEC2541658C050EB7BDA6AD4B94CC5AA161D56E0A`，清单为 `D:/Workspaces/Scratches/RadianceSemanticsAudit/eyes-energy-artifact.json`。没有启动客户端，新候选目视与实际加载仍待，不由旧包反馈代填。

使用者开始验收后，按授权进行[同类路径核验](../research/audit-companion_layer_and_uv_paths.md)：JDK 21 的无客户端接缝确认 breeze_wind 未变换主 UV，监守者／旋风人发光层与身体重合且不进入 eyes 合成。普通有序装饰层、持久部件保护与 Veil 自定义矩阵分别保留明确边界，未代填新的目视失败或通过。该次核验没有修改产品或更换 Prism 候选；例行 check 因 ZIP 目录时间字段重封装本地构建包，DLL／GAME／shader 内容不变，两个完整 JAR 身份及验证结果在该研究记录分别说明。

#### 同类附加层补齐候选

随后按使用者授权完成[定向修复与验证](../research/experiment-companion_surface_repair.md)：补齐 breeze_wind 的主 UV、调色和丢弃阶段；监守者／旋风人、普通装饰和已知 LEQUAL 盔甲层使用有序重合材质集合；末影龙保留眼睛合成所需完整捕获。普通装饰不强制发光，全亮层仍贡献世界照明。EQUAL decal、自定义混合与未证明的 Veil PT 矩阵路径不纳入等价承诺。

最终 GAME 298、JNI 4、probe 28、bootstrap 11 通过／2 跳过，非 GPU 68/68、GPU 17/17，两套 pack 编译及 SPIR-V 各 232；严格合规零失败／零警告。唯一新 JAR 为 `FB3908DB0B83BE9BD71D6DC7FFB3538116D07DEB0D7359E1EBA6682F4030A8CD`，core 为 `41656CE557962B04A366C6D957C09BC472C157775197028EA7E58A21647FEF1E`，身份、源码与部署回执分别由 companion-* 清单固定。使用者已允许替换；只投放、不启动，目视及实际加载仍待，旧包反馈不自动迁入。没有性能、世界稳定性或 GPU 根因闭合结论。

#### 本轮合并手工验收反馈

使用者于 2026-10-17 对最新候选逐组反馈。当前实例安装 JAR 再核对为 `FB3908DB0B83BE9BD71D6DC7FFB3538116D07DEB0D7359E1EBA6682F4030A8CD`，其内嵌 core 为上述 `41656CE5…47FEF1E`；本次只读核验没有重新证明运行时 DLL 加载身份，也未修改产品或重新投放。

通过六组：眼睛与动态附加层、普通装饰与覆盖顺序、线条与旋转结构、裁切／透明／受光、GUI 3D／运动历史／尺寸、重载与生命周期回归。通过范围按[验收清单](../development/testing-render_unification.md#2026-10-17-手工反馈)及使用者实际观察记录，不扩大到全部资源包、自定义表面程序、SDK 专项或长期稳定性。线条的世界物理厚度固定，距离越远屏幕线越细，使用者确认这一理解。

第一人称、水与特殊标记不通过：应在直接成像中隐藏的几何经一次折射后重现；船内实际水面未隐藏；第一人称自身发光效果的优先级表示未对摄像机隐藏。均作为实现缺陷待定向修复，不改变 adr-1 的连续折射及传光合同；自身表示的补充约束已记录于该决策。

另有末影人眼睛轻微照亮头颅内部的观察，已完成[原版流程与当前路径调查](../research/investigation-eyes_overlay_and_query_feedback.md)。原版眼睛是同模型加法颜色层，不产生世界照明；当前传光查询允许背面眼睛辐射，能解释向内照明的可行路径，但未取得该像素的路径证据，不将全部现象宣告正常或判为漏光缺陷。

使用者报告偶遇浮空水晶相关崩溃，并要求留待后续处理。19:07:46 报告的实际异常为 Veil 区块层进入 `WORLD_STAGE` 后缺少 RenderType/world material metadata 被拒收；不能由关联观察确定具体水晶对象或“历史回退丢失”。19:07:46.157 日志记录全部维度保存，完整 native close 未由本次核验确认，不混作设备丢失。原始报告与已结束的两次运行日志已复制并 SHA-256 核对，清单为 `D:/Workspaces/Scratches/RadianceSemanticsAudit/acceptance-feedback-20261017/evidence-manifest.json`。没有自动复现、游戏启动、产品改动、性能实验或 Git 写入。

#### 成像失败的集成完整性核验

随后按使用者要求完成[直接同类合同核验](../research/audit-query_visibility_integration.md)：自身优先级表示忽略观察者归属，现有 Java 断言固化旧行为；priority 续追踪按跳数强制转传光，尚未全部服从连续折射身份。普通主世界追踪已有身份传播，当前仍不能确定使用者一次折射后重现的具体路径。原版船的深度盖片与产品排除薄体积不等价，离线来源模型反例已证实排除域可漏掉船内水面；Veil 持久区块层则在实际目标分类前丢失来源交接。建议按可见性／续追踪、船内排除域、Veil 持久层三组收敛，后者保持使用者的后续处理安排。

这次仅代码核验、离线计算和维护记录，没有产品修复、新客户端或 GPU／性能运行。原六组目视通过保留，三个成像失败及独立崩溃仍未修复；眼睛向内照明保持独立证据边界。

#### 所有者与船内水面的集中修复候选

使用者随后授权实施[集中修复](../research/investigation-query_visibility_repairs.md)。Vanilla PT 的确定性反射分支会改写全局 payload，随后的折射从该值初始化，因而继承传光身份并允许自身命中；已改为两条分支读取同一首命中快照。旧 Prism 包原样调度代码在真实 TLAS 的受控 GPU 接缝失败，新代码通过。该证据锁定实际生产调度缺陷，仍不代替新包目视结果。

自身优先级表示继承主体的摄像机隐藏，且单独保留原物理材质资格；priority 真实循环先读取上一事件再推进用途，不再按第二步无条件转传光。船／箱船的水面排除域从来源盖片延伸到同模型的实际船底，原生有限凸域、统一水面消费者和帧内资源保活不变。竹筏无来源遮罩，不创建孔洞；自定义船型没有来源 bottom 时仍需专门适配证据。

完整 `spotlessApply check verifyRelease :native_renderer:gpuTest verifyCompliance --strict` 通过：GAME 305、JNI 4、probe 28，bootstrap 11 通过／2 跳过，CPU 68/68、GPU 18/18；232 个 pack 变体编译及 SPIR-V 通过。首次旧源码形态断言失败、纠正与最终重测均保留在修复记录，没有删掉检查。

新 JAR 为 `AEA65916F589775E00A03437AE220E5DF27774571204A05818FF43B95FE548B4`，core 为 `2B48A31D8A73D533DDC231AB5326306BC3186A44CB09ECEDC7D8066F99B6000A`，已于 20:21 投放 Prism，投放时未代为启动。源码、构建／内嵌／发行成员及部署身份由 `D:/Workspaces/Scratches/RadianceSemanticsAudit/query-repair-artifact.json`、`query-repair-source-manifest.json` 和 `query-repair-prism-deployment-receipt.json` 固定。实际提取／加载的机器证据尚待收集，不能沿用旧包加载记录。

交付时 Agent 未启动客户端或性能测试，三个修复的目视结果尚待使用者反馈；随后结果见下节。浮空水晶继续后续处理，不放开 Veil 世界后置光栅。前六组通过仍只属于旧 FB3908… 包，不自动迁入新候选。

#### 集中修复后的部分手工反馈

2026-10-17，使用者在继续手工验收时反馈当前 AEA65916…548B4 候选的以下结果，完整 JAR／core 身份见上节。本次只记录反馈与开发优先级，没有重新采集运行时 DLL 加载、设置或保存关闭证据，也不推定使用了全部 PT 模式。

| 项目 | 结果与证据边界 |
| --- | --- |
| 自身经折射后重新出现 | 使用者确认已修复；单面背面及所有连续介质组合不因此自动通过 |
| 第一人称自身发光轮廓 | 使用者确认正确隐藏 |
| 船内实际水面 | 使用者确认行为正确；不扩大到所有自定义船型 |
| 厚玻璃后方景物曾呈磨砂状 | 使用者认为此次似乎连带改善；属于暂定观察，随折射重做复核，不宣称确定修复或共同根因 |
| 乘船操控时的运动 | 新发现：游戏 FPS 明显高于船只运动更新频率，船只运动卡顿；作为优先系统性调查项，顶点转换／实例化仅是候选，尚未定位 |
| 手臂与折射手臂、其他优先级内容重复成像 | 仅登记；使用者明确暂不研究或修改，不加入当前复测要求 |

使用者同时将原版云、可切换半透明方案、统一折射适配、光源数量相关开销及原版无 PBR 发光账本列为优先开发，合并既有条目后的范围见[路线图](roadmap-radiance.md#本轮验收新增的优先开发)。本轮只维护记录，不启动这些开发、性能实验或新的全面审查。本次反馈摘要与候选关联保存在 `D:/Workspaces/Scratches/RadianceSemanticsAudit/query-repair-manual-feedback-20261017.json`；自动门槛、使用者目视与未来计划分别记录。

#### 船只运动插值修复候选

按使用者授权完成[实体运动插值调查与修复](../research/investigation-entity_motion_interpolation.md)。普通与 TextDisplay 的接管入口曾把 tick 的瞬时 yaw 交给 renderer，而原版先按 partial tick 插值；船只直接消费此参数，因此转向会按 tick 跳变。当前统一入口恢复原版线性插值，位置、桨动画、来源内部姿态、Sable 宿主、捕获与实例历史／退休均保留。矿车未取得轨道切线的回退、画及使用相同输入的模组实体一并受益，不对来源已经自行插值的动画再做滤波。

无客户端行为对照调用实际依赖中的 `LevelRenderer.renderEntity`，覆盖五种实体、四组角度和七个帧内时刻；旧行为两项失败，新三项回归通过。连同持久模型、船内排除域和可见性共 28 项定向通过；完整 `spotlessApply check verifyRelease verifyCompliance --strict` 通过，GAME 308、JNI 4、probe 28、bootstrap 11 通过／2 跳过，CPU CTest 68/68，严格合规零失败／零警告。GPU 专项与客户端未重跑，不把旧 GPU 18 项写成本轮执行。

唯一新 JAR 为 `171F38FC97FD46472B1690014BA2BB76981BDDBD1369B495E898B1D1FF222354`，core 仍为 `2B48A31D8A73D533DDC231AB5326306BC3186A44CB09ECEDC7D8066F99B6000A`。与此前部署比较，仅 EntityProxy 及其嵌套类发生 GAME 类字节变化；原生库、bootstrap 类、其他 GAME 资源与 shader 内容一致。两套 shader ZIP 的重封装改变容器及 runtime.index 哈希，逐成员内容已比对，不伪称整个运行库 ZIP 逐字节相同。清单为 `D:/Workspaces/Scratches/RadianceSemanticsAudit/boat-motion-artifact.json`、`boat-motion-source-manifest.json` 与 `boat-motion-package-comparison.json`。

使用者确认正常退出后，于 21:23 完成原子锁、停机核对、哈希校验和 Prism 替换，未启动；旧 JAR 已移入回收站，其他模组和配置哈希不变，回执为 `D:/Workspaces/Scratches/RadianceSemanticsAudit/boat-motion-prism-deployment-receipt.json`。交付时仅确认转向输入缺陷与自动回归，新包实际加载及目视尚待；随后手工结果见下节，旧查询候选的三项通过不自动迁移为本包完整验收。

#### 船只修复后的手工通过

2026-10-17，使用者反馈：“船只运动修复通过验收，加测了矿车也通过，船桨通过，船内水面剔除通过。”四项按当前投放的 171F38FC…222354 候选记录为目视通过，不继续要求重复相同用例；不扩大为任意模组实体、全部载具／PT 配置或长期稳定性通过。本次未重新读取运行时 DLL、设置及保存关闭日志，用户反馈与机器证据分别保留。

反馈原文和完整产物关联保存在 `D:/Workspaces/Scratches/RadianceSemanticsAudit/boat-motion-manual-acceptance-20261017.json`，先前源码快照、自动失败与重测记录不改写。船只专项从未完成工作中移除；随后使用者明确将半透明重写提到云之前，当前范围和入口核对见[路线图](roadmap-radiance.md#可切换半透明的保留边界)。本次没有产品改动、重建、投放、游戏启动或 Git 写入。

### 本轮复制环境清理待处理

最终两次功能证据、实际 SPIR-V 和内容哈希已独立保全。回收 `validations/regression-line_capture/instance/1.21.1-neoforge/client` 时 Windows 返回 “Attempted to perform an unauthorized operation”；目录仍有 783,883,566 字节，未换工具、永久删除或重复尝试。回执为 `D:/Workspaces/Scratches/RadianceSemanticsAudit/bcd-complete-client-recycled.json`。此项为操作系统回收失败待处理，不是 GPU 故障；不再启动该复制客户端，占用按最终 X-12 核对保留。

### 渲染统一阶段 A

使用者已确认阶段 A 线条总体良好和 F3+T 修复通过；默认不透明策略随后按 [adr-4](../design/adr-4-world_line_opacity.md)实施，可选透明路径保留。字体页批次的首次手工失败、候选修复及通过分别见[首次调查](../research/incident-font_texture_reload.md)与[修复验证](../research/experiment-font_texture_lifecycle.md)；随后自动运行识别的旧 FontSet 查找缓存为独立所有权缺口，见[来源缓存调查](../research/investigation-source_bitmap_lifetime.md)。

原不透明投放包 JAR `5E81F64663D7CDE9828257A53F8CB7831A92D0CCBF26EA12F76E536E9FA42CB2`、DLL `04DA0F2DF818EFB6288FFAB20AA758D1C208A6AD9B2E6A9808DE98853D8457D6` 只保留原验收范围；当前 B/C/D 投放身份与新反馈见上节。Create 全部交互、真实宿主变换和最终屏幕效果没有仅凭来源循环或包装器接缝就标为运行通过。

### 静态区块组织快照

2026-10-15 按使用者授权实施[静态场景快照](../research/experiment-static_chunk_scene_cache.md)。主区块地址、材质和命中组随发布变化重新组织；普通帧批量追加并调整动态前缀偏移，完整几何、相机与外部结构历史、代际及在途退休保留。GPU 上传、TLAS/SBT 与动态顶点路径没有同时重写。

同一 DLL 的参考/候选 16 视距平静区块组织为 3.513 → 0.726 ms/帧，真实帧间隔为 19.81 → 16.54 ms；背景整卡显存与系统负载明显变化，不能把全部帧耗时差归因于优化。候选 32 视距完整加载 17,427 区段，取得约 28.5 秒平静窗口，均值 43.19 ms/帧；参考 32 出现约 98 秒 `acquireContextNative` 边界停顿，最终只有 8,618 区段，未取得有效稳态或完整性能覆盖。没有设备丢失；保存和关闭完成不代表该轮性能通过。

已补分析器长帧覆盖检查和回归，保留原始分析及部分统计。四轮没有自动重跑，根因不从 JNI 位置推断；动态几何优化尚未开始。新增原生接缝回归通过，非 GPU CTest 65/65、GPU 16/16，完整检查与发行验证通过。产品身份、源码快照、实际模块和证据边界见研究记录；未提交、未推送。

随后按使用者要求投放同一产品候选到 Prism，没有启动游戏或修改其他模组及设置。使用者反馈“暂时判断不出是否有效，留待后续观察”，因此手工性能结论仍待观察；不由 CPU 阶段计数代填整体体验通过。部署 JAR/DLL 身份见[本轮视觉调查](../research/investigation-menu_fade_and_sublevel_lines.md)。

### 主菜单淡入与 sublevel 拉杆轮廓

2026-10-15 使用者报告主菜单“单人游戏”按钮在淡入时短暂闪白，以及 sublevel 油门拉杆活动部分的独立轮廓绕线轴转动。[定向调查](../research/investigation-menu_fade_and_sublevel_lines.md)确认加载覆盖层缺少 NeoForge 的默认混合状态收尾，普通 BER 线条遗漏 owner frame；拉杆轮廓并非原有普通选中框或 Catnip Outliner。离线算例证明基底遗漏会破坏旋转等变性，但菜单故障帧混合因子尚未捕获。

调查阶段没有修改产品。随后使用者授权实施：加载覆盖层补全普通/提前返回/异常路径的混合状态收尾；通用线条捕获立即复制绘制时的浮点基底，原生按各自真实顶点布局展开四种线段模式。没有针对拉杆名称特化，也不改变轮廓深度/优先级语义。无 Pose 的原始坐标来源保留明确回退，不能从已丢失的变换推断任意局部姿态。

配套构建、JNI/发行检查、257 项 GAME、26 项 probe、11 项 bootstrap（另 2 项跳过）、非 GPU CTest 66/66 与 GPU 16/16 通过；Java 实际捕获网格到原生截面函数的交叉检查通过。具体产物和初次失败→修正→重测顺序见[修复记录](../research/investigation-menu_fade_and_sublevel_lines.md#2026-10-15-授权修复与自动验证)。随后按授权只投放 Prism，由使用者启动与验收。2026-10-16 使用者明确反馈淡入和线条旋转通过；对应 JAR `6a26474b0c2e057a33ceacfd520034eb00de5561fd22e30a38e130e2a21388f6`、DLL `6704b26420419a99990fae0c7dc3e4febfd72883f9eb2eb9639421f5ed6e863c`，本轮留存文件哈希复核一致。该反馈不扩展为全部线条来源、性能或设备丢失通过。

### 线条统一盘点与 Voxy 评估

2026-10-16 的[线条来源盘点](../research/survey-line_capture.md)确认世界事件线接收、来源宽度与截面参考缺口，现已按阶段 A 实施并完成自动回归。无 Pose 来源仍保留明确 owner 回退；Create/Catnip 模型框排除在显式线整理之外，真正调用线 API 的轨道、输送机等通过来源适配器与通用接收器接入，完整交互目视待验。Veil、加载器调试与未默认启用路径分别保留证据边界。

同时只读检查使用者的 Voxy 移植工作区，形成[远景 PT 接入评估](../research/survey-voxy_pt_integration.md)。推荐从上传 GL 前的 `BuiltSection` 压缩面分叉，按变化解码为持久 AS 输入；该阶段仅静态阅读；后续实施状态和运行证据以下文接入实验为准，不能以该阶段的未运行描述覆盖新状态。

2026-10-18 使用者暂停继续优化，转入 Voxy 接入，确认既有测试 JAR 与本地源码一致，并接受其有意舍弃的 PBR、动态纹理及精度属于 LOD。Radiance 侧的无 GL 条件入口、压缩面 GPU 解码、持久 BLAS、代理材质、近远景覆盖和资源退休已实现；限定功能流程完成 Vanilla PT／Advanced、近远往返、F3+T 旧 epoch 拒收、保存退出和重进，未安装 Voxy 的菜单启动也通过。检查中修正了 Advanced 实例属性描述符缺失 raygen 权限及测试探针退出重入；失败候选与成功进程分别保留，见[接入实验](../research/experiment-voxy_pt_integration.md)。最终检查通过，已投放 Prism 而未启动，等待使用者目视。完整视觉、远景重要性光源采样、精确雾淡出及长期运行仍有边界。Voxy 与旧 MCVR 工作区不写入，不恢复性能实验。

### 项目内默认 GPU 取证

2026-10-15 使用者授权完善轻量取证并接通 Aftermath 基础采集。项目内客户端默认使用外置 agent，SDK 不进入产品包，重型验证按需开启，Prism 配置不变。事件自有标签、有界并发写入与丢弃统计，关联主要资源地址、命令缓冲、队列和 fence；CPU 录制不作为 GPU 完成证据。

首组接通进程 80992、92548 分别通过诊断与基准适配器标题界面验证，均正常退出。实际 SERVICE 设备启用 `aftermathFlags=3 diagnosticsConfig=1`，支持 device fault、checkpoint 与对象命名；SDK enable/disable 成功。没有真实转储、没有进入世界或运行性能测试，GPU 根因及完整 G3 仍未闭合。构建、发行检查、严格合规与相关自动回归通过，详见[本轮接通核验](../research/investigation-local_gpu_capture.md)和[使用方式](../development/debugging-gpu_diagnostics.md)。

### 成像与传光收敛

2026-10-15 的权威规则见[已接受决策](../design/adr-1-query_visibility.md)。本轮实现仍未提交，原有设备丢失事实和公开原生二进制许可边界不变；区块加载性能专项现已恢复，其他性能方向未重新启动。

Java 的来源状态与通道经过原有 JNI 传递；原生保留不透明硬件快路径、逐几何规则、镜像补偿和在途退休。相机与连续折射使用直接成像身份；反射、间接、阴影与公共 SHARC 使用传光身份。来源不可见面只从直接成像隐藏，不增加直通颜色项，不降低材质 alpha。

优先级内容使用独立的确定性覆盖率查询，再由对应 PT 的普通材质命中路径接受照明；保持背景先于前景的预乘合成与来源发光。旧 lightmap 亮度与固定颜色命中着色器不再包装。第一人称自身轮廓统一使用优先级通道，摄像机专用手部不进入世界传光和公共光照缓存。

船内水面排除数据由实际提交的封闭凸遮罩提取，在帧上下文中上传并保活，控制遮罩不进入 TLAS。相机、折射、反射与阴影在实际水面交点处过滤；Advanced 的发光水面采样和历史来源也执行相同排除。遮罩之外的其他水面保留。无效或不封闭的控制网格明确报错，不使用无限水面忽略规则。

水面离开起点采用按浮点精度缩放的数值偏移，后续查询的最小距离为 0；不设置“所有水”或整个材料的重复命中豁免。极薄界面和掠射角仍属于实际视觉检查边界。

本轮 `spotlessApply`、`check`、`verifyRelease` 与 `verifyCompliance --strict` 通过，严格合规失败 0、警告 0。原生非 GPU CTest 63/63、GPU CTest 16/16 通过；新增行为回归使用共用分类逻辑与真实 Vulkan AS，覆盖直接/传光掩码、镜像与来源面、重合薄片、实际船内遮罩及相距 0.0002 的合法水面。两套 PT 与 SHARC 的 238 个离线着色器变体全部编译通过。

新增 GPU 夹具首次遗漏 CULL_FRONT 的来源位，导致对应两个用例失败；修正夹具后 16/16 通过，未以放宽产品规则解决。首次与重测日志分别保留。优先级受光及其材质 G-buffer 在最终打包前完成编译核对，机器通过不代替视觉验收。

GAME 250/250、probe 26/26 及 bootstrap 11 项通过、2 项显式硬件对照跳过，均由本轮最终检查及 JUnit 记录核对。JNI 头文件、Maven GAME 边界和发行包结构检查通过，未把探针或诊断采集器放进产品包。

`regression-query_visibility` 的独立实例完成约 90 秒世界内流程：持物、Sable 组装、水面上方/下方、坐船、旁观者、客户端资源重载与服务端 `/reload`。船的记录为 `passenger=true`；日志记录全部维度保存、RR viewport 释放、原生场景释放、管线缓存落盘和进程退出码 0，无新增崩溃报告或 native fatal。没有启用单独的 close-return 生命周期用例，不将普通退出推论扩展为设备丢失关闭验收。

实际配置为 Vanilla PT、SHARC、DLSS RR Balanced、FG 关闭、Reflex 开启、1280×720、8 视距，主音量 0。启动伴随器记录 `correctnessOnly=true`、`frames=0` 与 `activationGuard=applied`，未运行性能采样。首次原生管线预热约 158 秒，其后进入世界；该启动事实不作为性能 A/B 结论。Advanced 本轮有编译与共用命中逻辑的 GPU 证据，尚无新的 Advanced 世界流程或视觉结论。

最终 JAR SHA-256 为 `7C7453DC866958986200E9682437E3460114D5D96905DE938FD152B3AA3E700C`，`core.dll` 为 `66FDBF6523A6972D780AE5DDB12B2AEC82F22B12231075477EBB659EEAAA1CFC`。构建 DLL、JAR 内嵌、隔离实例提取及实际加载 DLL 内容一致；匹配的 PDB 在证据目录保留。源码按原始文件字节计算 SHA-256，包含全部必要未跟踪文件、构建事实与子模块提交，不靠 HEAD 代表工作树。

本轮证据位于 `D:/Workspaces/Artifacts/Radiance/query-rules-20261015/`。`D:/Workspaces/Artifacts/Radiance/query-rules-20261015/evidence-archive.zip!release-gates-final.log`、`D:/Workspaces/Artifacts/Radiance/query-rules-20261015/evidence-archive.zip!final-gates.log` 与 `D:/Workspaces/Artifacts/Radiance/query-rules-20261015/evidence-archive.zip!compile-packs-final-2.log` 分别记录最终构建、GPU 回归与 238 个变体；完整源码与产物身份分别由 `D:/Workspaces/Artifacts/Radiance/query-rules-20261015/evidence-archive.zip!source-manifest-final.json` 和 `D:/Workspaces/Artifacts/Radiance/query-rules-20261015/evidence-archive.zip!artifact-identity-final.json` 固定。手工矩阵见[查询规则验收](../development/testing-query_visibility.md)。历史测试数和旧包的世界验收不计为本轮通过。

使用者随后明确授权向 Prism 的 `Radiance 1.21.1-neoforge` 实例投放同一 JAR，并亲自运行后反馈“视觉上没有问题”，同时再次复现区块加载严重卡顿。只将该反馈记为总体目视未发现问题，不补写未逐项反馈的折射、反射、船内水面或优先级专项通过；加载性能不通过，根因尚未确认。

此次 Prism 日志为 2026-10-15 04:18:42 至 04:33:23，加载 NeoForge 21.1.252、Create 6.0.10、Sable 2.0.6、Aeronautics 1.3.2。启动参数为 2560×1440，入世界日志记录 16 视距与 32 模拟距离；退出时设置为 Vanilla PT、SHARC、RR Balanced、FG 关闭，区块参数为 28 个线程、每批 32 项和 32 个批次。退出时设置不能证明整段运行未改动，不把这些数值直接认定为原因。

日志记录所有维度保存与 `Stopping!`，本次 Java 日志未出现设备丢失，但它不包含全部原生控制台输出，也没有足以定位加载卡顿的阶段计时。安装 JAR 哈希与上述候选一致。只读采证与原始反馈位于同一证据根的 `manual-feedback-20261015/`；未启动新客户端、未改动实例或世界、未恢复性能实验。

定向静态核对确认：`Chunks::queueChunkBuild` 在持有区块共享锁时调用 `ChunkBuildData::create`，构造函数对各组网格调用 `geometry::prepare`，遍历位置、复制索引并进行反向面配对；`WorldPrepareModule` 也获取同一个 `chunks->mutex()`。这是一条加载工作可能阻塞场景准备的明确串行路径，尚未测量锁等待、拓扑处理、批次打包与 GPU 构建各自的耗时，不能据此宣布根因已确认或优化已完成。后续测量应保留已接受的成像与传光规则。

### 区块加载专项诊断

2026-10-15 使用者重新授权专项。只读固定 Prism 最新进入的 Newport City 及原设置，工具在 `performance-chunk_loading` 验证中逐轮展开同一副本；源世界、Prism 安装包与旧 MCVR 均未改动。第一轮完成进入、一次 F3+A、全部维度保存和正常退出；第二轮初次加载的定点计时确认共享锁争用，随后在 F3+A 后发生真实设备丢失，已停止本组且未重启。

阶段计时证明：修复前 worker 在区块共享锁内执行拓扑准备，渲染线程读取网格参数及获取调度器时被同一把锁阻塞。一帧 266.43 ms 中，这两次等待约占 235.37 ms。第一轮加载帧间隔 p95 约 104 ms，队列平静后约 20 ms。这是修复前产物的诊断，不是优化前后收益。

第二轮原始 `VK_ERROR_DEVICE_LOST` 传播到 Java，世界全部维度保存与 RR viewport 释放有日志，完整 native close-return 和 GPU 根因未确认。Windows 同期 Event 153 已保留，不能作为致因证明。没有 GPU crash dump，不将性能跟踪当作转储，也不把构建通过写成第二轮运行通过。

完整方法、两轮不同产物、原始分布、配置差异与证据位置见[共享锁诊断](../research/investigation-chunk_loading_lock_contention.md)。该诊断阶段 `spotlessApply check verifyRelease` 通过，新增计时默认关闭；诊断包未投放 Prism，不改变先前候选的视觉反馈范围。随后按使用者授权实施下述修复，首次 GPU 故障独立保留，不扩大为无界调查。

### 共享锁修复与受阻对照

将区块输入复制和原有 CPU 拓扑准备移出共享锁；短锁内预留版本并记录完整句柄和弱对象身份，发布时重新核对代际、所有者和目标版本。世界重置后的同编号对象也不能接受旧准备结果。发光采样、GPU 上传、完成后发布和在途退休仍走原有受保护路径，进入 GPU 路径前再次检查 fatal。没有更改几何、材质、查询、画质或调度预算。

2026-10-15 的修复前对照从同一 Newport City 输入重新展开，保持原设置，关闭 F3+A，计划初次加载后 60 秒正常保存退出。实际加载旧 DLL `f9ba4c1e537798216a93a270654065cb18e7e62368e7a425f082e535ea5f1f05`；进程 74508 在 `world-ready` 后约 2.9 秒再次设备丢失，只有 3 个有效帧和 1 个失败帧。全部维度保存与原始错误保留有证据，完整 native close-return 和 GPU 根因仍未知。未自动重跑，也未启动修复后客户端，不能声称取得加载性能改善或新包世界验收。

代码、自动回归与证据索引见[锁范围修复](../research/investigation-chunk_loading_lock_fix.md)。原始实例、配置、源码、加载身份和 Windows 事件保留在 `D:/Workspaces/Artifacts/Radiance/chunk-lock-fix-20261015/baseline/`；`completed_run=false`，部分分析不算验证通过。本轮不向 Prism 投放，不提交或推送。

本次完整构建、`check`、`verifyRelease` 与严格合规通过，失败 0、警告 0；非 GPU CTest 64/64，GAME 250、probe 26、bootstrap 11 通过且 2 项跳过。候选 JAR 为 `ba8c80e2d9227f54920c33f6c552c6c701299ef07d67d496dc985048ede5c157`，DLL 为 `8ddb622be48d5138280b426147d2b2ead8875331a90def78f451c820c810c3e3`，构建、安装、内嵌一致且保留匹配 PDB。候选未启动、未提取或加载，GPU 合同未重跑；260 个 shader 与拓扑输入相对本次基线原始字节不变。不能把自动测试写成此包的世界或视觉通过。

### 默认诊断配置下的加载复测

2026-10-15 使用者重新授权后，用原始 Newport City 命名输入重新展开副本，只运行一次初次加载和 60 秒世界窗口。修复后进程 84244 完成保存、场景释放、正常设备关闭与退出，无设备丢失或新崩溃报告；Aftermath 在实际 SERVICE 设备上启用，SDK 正常禁用，没有故障转储属于正常运行的预期结果。

加载窗口真实帧间隔 p95 为 35.33 ms，确认平静后为 26.30 ms；两处关键主线程共享锁等待最大为 0.0069/0.0007 ms。首帧约 699 ms 与另有动态输入长帧仍保留，不能把锁范围改善当作整个卡顿专项或 GPU 根因闭合。首次工具分析因 Java 日志缺少原生 stdout 而失败，补充完整正常设备关闭跟踪的独立判据并通过 7 项回归后，同一原始运行分析通过，没有重启客户端。

完整设置、窗口、吞吐边界、实际加载身份与证据见[本轮复测](../research/investigation-chunk_loading_with_gpu_capture.md)。运行产品源码与上一轮固定快照相同；本轮只修分析工具并更新记录，不更改产品或 shader。没有同一诊断配置的修复前完整运行，因此不报告严格提速比例；未测放挖、F3+A、长期稳定性或新的视觉矩阵。

### 城市 16/32 视距对照

2026-10-15 按使用者授权从同一原始 Newport City 输入分别展开 16/32 视距副本，其他准备设置和诊断相同，各完成 115 秒世界流程、保存与关闭，没有设备丢失；实际加载 core 相同，两个 SPIR-V 多重集合一致。只补强视距覆盖、UTC 锚点和 JFR 采集，没有改产品或 shader。

32 视距最终 ready 主世界区段为 17,427，16 视距为 5,241，均没有新生成区块。确认队列平静分别用时约 86.54/15.30 秒；32 视距平静后的平均真实帧间隔仍约 80.10 ms，16 视距约 19.24 ms，共同末尾窗口支持该差异持续存在。背景 CPU/GPU 占用变化，不把该比值写成独占环境性能承诺。

主要 CPU 路径已定位为全量区块元数据组织及更大的动态几何输入：元数据缓存虽全命中，仍逐帧扫描槽位、追加实例与地址/历史/SBT 数据；动态逻辑顶点约 4 倍、动态 BLAS 输入约 3 倍。原共享锁等待与 GC 不能解释这次主要差距；GPU 完成区间也增大，但抢占、驻留和纯算法成本仍未分离。刚性模型实验没有开启，不以擅自改变功能开关制造收益。

详见[本轮对照](../research/investigation-city_distance_loading_comparison.md)。它不证明空世界退化同源、不关闭 GPU 根因或长期稳定性，也没有完成放挖延迟与视觉专项。原始两轮实例、JFR、日志、计数、源码和实际加载身份已分开保全，没有部署 Prism、提交或推送。

### 正式版依赖更新

2026-10-14 按正式发布选择依赖，未更改 Minecraft 版本、产品渲染逻辑或着色器。精确事实来源与内嵌依赖关系见[渲染与兼容依赖](../reference/dependency-rendering.md)。

| 项目 | 更新前 | 本次版本 |
| --- | --- | --- |
| NeoForge | 21.1.256 | 21.1.259，Radiance 自身支持下限仍为 21.1.62 |
| Sable | 2.0.5 | 2.0.6，包含其对应的 sable_rapier |
| Loom | 1.18.3 | 1.18.4 |
| SnakeYAML | 2.6 | 2.7 |
| ASM / Gson | 9.10.1 / 2.13.2 | 9.11 / 2.14.0 |
| Vulkan SDK | 1.4.341.1 | 1.4.363.0 |
| volk / Vulkan-Headers | SDK 1.4.357.0 / 1.4.357 | SDK 1.4.363.0 / 1.4.365 |

Streamline 2.14.1、DLSS 310.9.1、XeSS 3.0.2、NRD 4.17.3 及其余有正式版本的原生依赖已是核对时的最新正式版。NRD 4.18.0 与 Loom 1.19 的预发布不纳入；没有正式发布标签的 stb 保留既有固定提交。Create 6.0.10 与 Create Aeronautics 1.3.2 未更新，Veil 保持 Sable 2.0.6 内嵌的 4.3.2，不混装独立 4.5.2。

FidelityFX 官方 2.3.0 的完整源树未提供本项目使用的 `ffx_api_vk` 与 Vulkan 后端，不能直接替换现有 Vulkan 分支；保留该固定提交，没有通过关闭 FSR 规避兼容问题。正式版范围和后端缺口的核对记录保存在本轮证据目录。

升级同步修正两项构建与验证问题：CMake 重新配置时刷新旧 SDK 缓存；Sable 契约测试从 Target 读取版本，并仍验证实际加载 JAR 与字段契约。`local.toml` 仅更新已校验的 Sable 输入路径，未写 EULA。

本轮 `spotlessApply`、`check verifyRelease` 通过。GAME 250/250、probe 26/26（含新增采集器包装回归）、bootstrap 11 项通过与 2 项显式硬件引导对照跳过；工具 inventory 4/4、benchmark 7/7、Python inventory 8 项及诊断采集器 2/2 通过。原生非 GPU CTest 62/62、GPU CTest 15/15 通过；JNI 头文件、Maven GAME 边界、发行元数据、154 个必需条目及探针排除检查通过。

首次两项探针运行到达标题界面并正常退出，但原生采集器资源缺失，诊断功能不能算通过。升级前留存日志中也有相同失败。已将工具组件 JAR 的采集器资源明确纳入 probe 资源，保留产品包排除边界，并增加包装回归；`D:/Workspaces/Artifacts/Radiance/dependency-update-20261014/evidence-archive.zip!check-release-2.log` 记录修正后的完整检查。

修正后的 `run-diagnostics-fixed` 与 `run-benchmark-smoke-fixed` 均记录原生采集器实际挂接（`flags=8`，只采生命周期）、`AUDIT_SMOKE title-ready`、正常停止和 audit ledger 关闭；基准启动伴随组件为 `active=true`。这两项只验证工具加载，不产生性能结果。

日常客户端与 Create/Sable/Aeronautics 组合均完成启动和正常退出，无新增崩溃报告。组合日志仍有 7 条 Veil 着色器布局或 uniform 不支持报告，与升级前日志逐项相同；`D:/Workspaces/Artifacts/Radiance/dependency-update-20261014/evidence-archive.zip!veil-startup-comparison.json` 保留比较结果。这些是既有兼容边界，启动成功不代表所有 Veil 视觉路径通过。

本轮机器证据位于 `D:/Workspaces/Artifacts/Radiance/dependency-update-20261014/`。`D:/Workspaces/Artifacts/Radiance/dependency-update-20261014/evidence-archive.zip!check-release-1.log` 与 `D:/Workspaces/Artifacts/Radiance/dependency-update-20261014/evidence-archive.zip!check-release-2.log` 保留修正前后的完整构建，`D:/Workspaces/Artifacts/Radiance/dependency-update-20261014/evidence-archive.zip!gpu-test.log` 记录 GPU 测试，`D:/Workspaces/Artifacts/Radiance/dependency-update-20261014/evidence-archive.zip!junit-results-final.json` 记录修正后的 Java 用例统计。Vulkan SDK 使用仓库外 copy-only 安装，未修改系统环境、注册图层或驱动；构建环境显式指向此 SDK。

发行 JAR 的 `runPackagedClient` 完成安装式启动和正常退出，模组列表为 Minecraft 1.21.1、NeoForge 21.1.259 与 Radiance，无新增崩溃报告。本轮各入口主音量为 0，实际加载文件记录在各运行目录的 `native-loaded.json`；最终产物与源码清单分别为 `D:/Workspaces/Artifacts/Radiance/dependency-update-20261014/evidence-archive.zip!artifact-identity-final.json` 和 `D:/Workspaces/Artifacts/Radiance/dependency-update-20261014/evidence-archive.zip!source-manifest-final.json`。服务器因 `local.toml` 未接受 EULA 不运行；不执行性能实验，不声称视觉、世界流程或历史设备丢失根因已通过。公开二进制许可门禁保持关闭。

### 模板 2.0.2 升级

2026-10-14 由官方标签降序首项确定 2.0.2，克隆提交为 `b598a41405c57c2d29c15cda3d4fc937426b233e`。按新克隆的升级规程从 2.0.0 整体替换模板文件，旧 `gradle/conventions/` 通过 Windows 回收站移除；项目依赖升级、必要新文件与暂存区保持原状，不操作旧 MCVR 仓库。

新版将模板实现模块和 Java 包从 `target` 改为 `platform`，项目 Target 脚本同步两处类型引用；对外插件 ID 与组件登记方式保持一致。实例目录说明统一采用 `<variant>`，根目录格式检查增加 `.yaml`，本地模组元数据识别跳过未声明模组 ID 的占位文件；Gradle、模板工具和 Action 版本没有变化。项目公共配置中的补充项与本地设置保留，NOTICE 仅更新模板来源版本。

升级前 `check --continue` 成功，严格合规失败 0、警告 0。本轮证据位于 `D:/Workspaces/Artifacts/Radiance/template-upgrade-20261014-2.0.2/`；该目录中的源码指纹与暂存条目记录用于确认已有改动没有丢失。

升级后 `spotlessApply`、`check --continue`、`verifyRelease` 与 `verifyCompliance --strict` 通过；规范 2.0.2 的严格检查失败 0、警告 0，与升级前一致。123 个受保护模板文件与新标签逐字节相同，发行包的元数据、154 个必需条目与探针排除通过；原生非 GPU CTest 62/62 通过，其余检查包含已有任务的有效增量结果。

参考 Target 的 `runClient` 实际加载 Radiance 与 NeoForge 21.1.259，线程快照位于 `TitleScreen.render`，主音量为 0。确认后向命令行包含本仓库路径的对应 Java 进程请求正常关窗，日志记录 `Stopping!`，退出码 0，无新增崩溃报告。服务器因 `local.toml` 未接受 EULA 跳过；世界流程、视觉效果与性能没有重新验收。HEAD 与暂存条目不变；已有产品源码、着色器及依赖升级没有丢失或改写。

### 已保留实现

- Minecraft 1.21.1 NeoForge；SERVICE 早期 Vulkan 窗口、GAME 嵌套发现与错误生命周期保持原逻辑。
- acquire/recreate 状态修复、Streamline 缺席回退、DLSS 失败不消费无效输出、首个错误与设备丢失状态分离、JNI/worker 异常边界、保存与 native close 保持原证据范围。
- 传光使用双向几何，直接成像使用来源 cull/winding；旧全局环境开关不再覆盖已接受的查询合同。翘曲反向四边形采用一致拆分与配对所有权。
- 外部区段背压、代际保护、有界纹理编号、延迟上传身份、GPU 资源退休与缓存界限保持不变。
- 材质染色采用材质颜色覆盖；保留优先级、第一人称、普通半透明和物理透射的独立语义。
- 持久模型与高视距优化保留；历史性能数据不是本次迁移重新运行的结果。
- Ponder PT 已封存，当前 Ponder/UI3D 走默认光栅语义的 Vulkan 翻译；不恢复截图或 PT 实验。
- GAME Maven 与本地完整发行包用途分开，诊断改为验证专用 `radiance_probe`。

### 仍未闭合

- 真实 GPU 设备丢失根因、完整 G3、长期稳定性与生成帧动态视觉验收仍未知或待验；历史世界保存与 native close 的成功不能替代根因证明。
- 连续折射自身隐藏、船内水面及第一人称自身发光轮廓隐藏在 AEA65916… 候选获使用者通过；反射及其他未单独确认的专项仍保留其范围。171F38FC… 候选的船只运动、矿车、船桨和船内水面剔除已获使用者通过；手臂与其他优先级内容重复成像仅登记、暂缓研究。
- 原生云光照色、粒子 packed-light 发光推导、DLSS viewport 释放失败后的身份、viewport 计数耗尽与遗留选项读写，保留静态审查所发现的缺口，不借迁移修改。
- labPBR 掠射角、云重写、部分结构图解/连接绳/反射细节和跨模式视觉等价保留各研究的状态。
- NVIDIA 等第三方运行库的公开分发授权未确认；本地验收和源码迁移不代表许可兼容。
- 区块加载专项于 2026-10-15 另获恢复授权；其结果见本页对应记录。新增光源数量相关性能观察列入优先开发，尚未开展新实验，不代表恢复全部优化或压力矩阵。

### 历史证据

原始 `docs/`、relay、已完成审计和两份开发台账保存在 `D:/Workspaces/Artifacts/Radiance/history-archive/Radiance/docs/` 与 `D:/Workspaces/Artifacts/Radiance/history-archive/MCVR/docs/`，保持原文、日期和目录。仓库 Git 历史仍保留原文件。

原 `run/inventory-20261003/` 与其他 ignored 运行证据保持原地；它们不进入源码提交，也不是临时垃圾。

### 本次迁移验证

2026-10-09 首轮迁移使用固定标签 1.1.0 的仓库外克隆，模板提交为 `b989bdfb84e2d9308d47fdd6d0360241b0ae5709`。81 个受保护模板文件逐字节一致；未复制三个示例模组专用文档，模板 MIT 原文与 NOTICE 归属保留。十四个原生子模块保持 URL 与固定提交。

#### 构建与自动验证

- 原 HEAD 的两个仓库由 `git archive HEAD` 导出后，使用原构建方式成功生成完整 `distributedJar`；RelWithDebInfo 与迁移后保持一致。
- GAME 250/250、probe 25/25、工具 inventory 4/4 与 benchmark 7/7 通过。bootstrap 11 项通过，2 项要求显式 GPU 环境的独立画面对照/窗口交接测试跳过；不把客户端启动替代它们。
- 原生 CTest 62/62 无 GPU 测试、15/15 GPU 测试通过；诊断采集器 2/2、Python inventory 8 项与离线绘制盘点测试通过。21 个 JNI 头文件、Maven GAME 边界和完整发行包结构验证通过。
- 首轮格式修正与 `verifyRelease` 已执行；当时 `check` 受模板 1.1.0 对四个二进制云噪声 `.raw` 的 C-01 误判阻塞，没有修改检查器或噪声绕过。该结果保留在首轮证据中；当前收尾按下节的新版检查结果判断。
- 首次默认配置缓存运行失败，原因是项目自身任务捕获脚本对象；已关闭配置缓存，构建缓存保留。首轮迁移当时尚未执行 Windows CI；后续前置条件修正与实际托管结果见本文末尾。

#### 原包与迁移包比较

引导层 29 个类忽略行号后全部一致；GAME 605 个类中 604 个一致。唯一差异是 `ShaderPackScreen$ShaderPackListWidget` 的编译器生成桥接方法：用原始未修改源码在新的编译环境重编译，得到与迁移版相同的方法。不能把这项环境差异写成全部字节码一致。

225 个 PT 包内文件逐项比较，仅格式与命名空间结束注释改变；普通资源与着色器的每处差异、许可命名、元数据、原生重链接、索引哈希和包结构变化均有清单。原生库 204 个导出符号集合一致；PDB 路径、编译与链接来源不同，DLL 不逐字节相同。导出符号一致不等于完整运行等价。

#### 客户端与验证运行

日常 `runClient` 与 `runPackagedClient` 分别到达标题界面，模组列表含 Radiance，均正常退出，无对应崩溃报告。日常窗口使用 `local.toml` 的 2560×1440；原生打包运行保留其默认 854×480。主音量均为 0。

`smoke-render_diagnostics` 与 `conformance-benchmark` 均记录 `AUDIT_SMOKE title-ready`、正常停止与 audit ledger 关闭；基准启动伴随组件实际 active=true。本次只验证启动与加载，不进行世界或性能实验。

首次诊断启动曾因跨加载域的 `PortableBenchmarkMod` 缺失而失败，已将基准入口辅助类一起归入 probe，逻辑不变，重测通过。首次基准启动曾因 261 字符的路径导致原生无法打开已提取的 shader，已缩短验证目录并重测通过；完整失败实例保留于仓库外，不掩去失败历史。

日常、发行 JAR、构建安装与实际提取/加载的 `core.dll` 已校验内容一致；JAR、DLL 完整哈希见下述机器清单。未进入世界，不宣称视觉、完整游戏流程、GPU 故障根因或长期稳定性通过。

打包客户端实际启动的 JAR 哈希为 `FC8A92524CF4FAC1E6793ECC9BC3AF2AA66D25D0E9629F5060596421130FEF87`。收尾重新执行构建会重打两个 PT ZIP，并修正 NOTICE 中的许可路径文字，因此最终 JAR 容器哈希变化。逐条比对差异仅为两个 ZIP 容器及对应 `runtime.index`、NOTICE，以及内嵌 GAME 中的 NOTICE 与 `game.sha256`；ZIP 内全部文件字节相同，类与 DLL 也相同。实际运行包和最终构建包的身份分别记录，不声称它们是同一个二进制文件。

#### 证据索引

本次证据根为 `D:/Workspaces/Artifacts/Radiance/migration-20261009-1800/`，不是可随手删除的临时备份。

| 证据 | 内容 |
| --- | --- |
| `D:/Workspaces/Artifacts/Radiance/history-archive/migration-before-20261009/Radiance-585c561.zip`、同目录的 `MCVR-81c0814.zip` | 迁移前 HEAD 的归档；2026-10-16 清理时迁移，内容不变 |
| `D:/Workspaces/Artifacts/Radiance/migration-20261009-1800/evidence-archive.zip!before-build-short.log`、`D:/Workspaces/Artifacts/Radiance/migration-20261009-1800/evidence-archive.zip!check-final.log`、`D:/Workspaces/Artifacts/Radiance/migration-20261009-1800/evidence-archive.zip!check-closeout.log` | 原构建、自动测试与最终检查；早期失败日志另行保留 |
| `D:/Workspaces/Artifacts/Radiance/migration-20261009-1800/evidence-archive.zip!artifact-identity-final.json`、`D:/Workspaces/Artifacts/Radiance/migration-20261009-1800/evidence-archive.zip!source-manifest-final.json` | 最终产物及实际加载身份、完整源码候选范围与原始字节哈希 |
| `D:/Workspaces/Artifacts/Radiance/migration-20261009-1800/evidence-archive.zip!package-resource-comparison-final.json`、`D:/Workspaces/Artifacts/Radiance/migration-20261009-1800/evidence-archive.zip!shader-pack-content-comparison-final.json` | 每处包资源差异及逐文件 shader 比较 |
| `D:/Workspaces/Artifacts/Radiance/migration-20261009-1800/evidence-archive.zip!bytecode-bootstrap-final.log`、`D:/Workspaces/Artifacts/Radiance/migration-20261009-1800/evidence-archive.zip!bytecode-game-final.log`、`D:/Workspaces/Artifacts/Radiance/migration-20261009-1800/evidence-archive.zip!bytecode-source-control-result.log` | 忽略行号的字节码比较与原源码控制实验 |
| `D:/Workspaces/Artifacts/Radiance/migration-20261009-1800/evidence-archive.zip!native-export-comparison-final.json` | 原生完整导出符号集合比较 |
| `D:/Workspaces/Artifacts/Radiance/migration-20261009-1800/evidence-archive.zip!run-client.log`、`D:/Workspaces/Artifacts/Radiance/migration-20261009-1800/evidence-archive.zip!run-packaged-client.log` | 日常与发行安装入口运行 |
| `D:/Workspaces/Artifacts/Radiance/migration-20261009-1800/evidence-archive.zip!run-validation-diagnostics-retry.log`、`D:/Workspaces/Artifacts/Radiance/migration-20261009-1800/evidence-archive.zip!run-validation-benchmark-retry.log` | 两项验证重测通过；首次失败日志保留 |
| `D:/Workspaces/Artifacts/Radiance/migration-20261009-1800/evidence-archive.zip!template-verification-final.json`、`D:/Workspaces/Artifacts/Radiance/migration-20261009-1800/evidence-archive.zip!template-name-search-final.json`、`D:/Workspaces/Artifacts/Radiance/migration-20261009-1800/evidence-archive.zip!archive-verification.json` | 模板完整性、名称残留搜索及 243 份历史文档原文归档 |
| `recycled-paths*.json` | 迁移移除文件的回收站回执 |

### 模板升级收尾

2026-10-10 从远端标签降序列表的首项 1.2.1 克隆升级，提交为 `b849965bf8a0ac4f525d24106b68649c4e15e9f9`。使用者明确授权覆盖未提交的模板文件；项目迁移改动与原生子模块保留，MCVR 仓库未修改。

Gradle Wrapper 9.8.1、版本目录与守护进程 JVM 要求和新模板一致，保留原文件；公共配置保留 RAW/SPIR-V 二进制属性、Windows CI 与本地输入规则。CI 只上传合规报告，不上传发行包或运行库；模板升级当时尚未执行托管 CI；后续实际结果见本文末尾。

`clangFormatCheck` 在执行时先查 PATH，Windows 再调用 Visual Studio 的 `vswhere`；查找失败会说明安装“C++ Clang 工具”或补 PATH。此项的配置缓存验证独立于整条仍关闭配置缓存的构建链。

本轮证据目录为 `D:/Workspaces/Artifacts/Radiance/template-upgrade-20261010-1_2_1/`。`D:/Workspaces/Artifacts/Radiance/template-upgrade-20261010-1_2_1/evidence-archive.zip!pre-upgrade-check.log` 保留升级前 RAW 误判与 clang-format 缺失的失败；`D:/Workspaces/Artifacts/Radiance/template-upgrade-20261010-1_2_1/evidence-archive.zip!product-before.json` 和 `D:/Workspaces/Artifacts/Radiance/template-upgrade-20261010-1_2_1/evidence-archive.zip!upgrade-audit.json` 用原始字节 SHA-256 检查产品源码与资源不变，`D:/Workspaces/Artifacts/Radiance/template-upgrade-20261010-1_2_1/evidence-archive.zip!recycled-paths.json` 记录按规程替换的三个模板组件目录。

本轮 `spotlessApply` 与 PATH 中没有 clang-format 的 `check verifyRelease --continue` 成功；十二组格式检查实际执行，使用 `C:/Program Files/Microsoft Visual Studio/18/Community/VC/Tools/Llvm/x64/bin/clang-format.exe`，版本 22.1.3。原生非 GPU CTest 62/62 与诊断采集器 2/2 本轮执行通过；其他 Gradle 检查按任务输入复用或执行，不把缓存结果写成本轮全部重跑。没有重跑 GPU 与世界性能测试。

`verifyCompliance --strict` 报告 1.2.1、失败 0、警告 0，检查 1,765 个文件。81 个模板文件与新克隆逐字节一致，1,553 个产品源码及资源输入相对升级前哈希不变，四个 RAW 纹理保持原始字节；P-07 残留扫描为空。

工具查找验证覆盖无 PATH 时回退、PATH 优先与两处缺失时的预期失败。配置缓存第一次保存、第二次复用成功；在复用配置后移除工具路径，任务仍正确报告缺失，证明没有沿用缓存中的工具位置。全局配置缓存仍按首轮迁移结论关闭。

日常 `runClient` 本轮到达标题界面，日志列出 Radiance 0.1.5-alpha，主音量 0，正常退出且没有崩溃报告；进程 2344 的操作仅限标题界面。未进入世界，画面验收仍由使用者完成。`local.toml` 未接受 EULA，跳过 `runServer`，本机配置字节未改变。

补充 NOTICE 的模板来源链接后，首次发行包内容检查发现包内 NOTICE 尚未同步；这是收尾时文件修改发生在打包之后造成的身份差异，记录在 `D:/Workspaces/Artifacts/Radiance/template-upgrade-20261010-1_2_1/evidence-archive.zip!artifact-identity-before-final-package.json`，重新打包并核对后以 `D:/Workspaces/Artifacts/Radiance/template-upgrade-20261010-1_2_1/evidence-archive.zip!artifact-identity.json` 为准，不沿用旧包哈希。

`D:/Workspaces/Artifacts/Radiance/template-upgrade-20261010-1_2_1/evidence-archive.zip!spotless-apply.log`、`D:/Workspaces/Artifacts/Radiance/template-upgrade-20261010-1_2_1/evidence-archive.zip!check-verify-release.log`、`D:/Workspaces/Artifacts/Radiance/template-upgrade-20261010-1_2_1/evidence-archive.zip!strict-compliance.log` 与 `formatter-*.log` 保存上述命令结果；`D:/Workspaces/Artifacts/Radiance/template-upgrade-20261010-1_2_1/evidence-archive.zip!run-client.log` 与 `D:/Workspaces/Artifacts/Radiance/template-upgrade-20261010-1_2_1/evidence-archive.zip!client-latest.log` 保存运行记录。最终文档格式、发行包和严格检查保存到 `D:/Workspaces/Artifacts/Radiance/template-upgrade-20261010-1_2_1/evidence-archive.zip!closeout-verification.log` 与 `D:/Workspaces/Artifacts/Radiance/template-upgrade-20261010-1_2_1/evidence-archive.zip!strict-report-before-closeout.json`。本轮不提交或推送，暂存区保持升级前状态。

### 构建配置与许可说明对齐

2026-10-10 对齐 `gradle.properties`、Windows CI、双语首页标题与 NOTICE 结构。Gradle 设置只保留配置缓存、JVM 参数和工作线程上限；按使用者要求保留 4G 堆上限，移除显式并行执行和构建缓存设置。CI 的工具定位步骤移除，由原生组件自己定位 clang-format。

开启配置缓存后的 `verifyRelease` 实测失败：`configureNativeAudit`、`buildNativeAudit`、`generateBootstrapRuntimeIndex`、`generateEmbeddedGameMetadata`、`generateLauncherMetadata` 捕获 Gradle 脚本对象，缓存条目被丢弃。按使用者授权恢复 `false`，在配置项上方逐一列出不兼容任务，没有扩展任务去重写这些实现。

此前的 `spotlessApply` 缓存尝试因长时间文件集序列化扫描取消，线程记录与未完成日志保留，不记作通过。缓存失败报告、最终配置下的验证与客户端日志集中保存在 `D:/Workspaces/Artifacts/Radiance/template-alignment-20261010/`。

NOTICE 保留原有来源、许可与审查引用，按来源逐段排列并先列模板、再列 Wrapper；许可边界说明移到来源列表之前。第三方许可原文没有修改，公开二进制许可门禁保持未闭合。

### 迁移后发行包手工反馈

2026-10-10 使用者在已授权的 Prism 实例实测后反馈“实测没有发现问题”。对应发行 JAR 的 SHA-256 为 `620DB24C0103796D1F2B6EC78EF132CDC3BA33D459A3DC9AB3ADB8848FEE566A`，内嵌 `core.dll` 为 `1E511CA3EAA7B4F6A05B8B864E3919B6489FCD0B17B6F035D2C2B4A7577E2DB7`。投放及完整运行库索引校验证据位于 `D:/Workspaces/Artifacts/Radiance/prism-production-20261010-141718/`。

本次手工进程日志为 14:23:56 至 14:25:54，记录 Radiance 加载、集成服务端启动、全部维度保存与 `Stopping!`。反馈只表明本次游玩未观察到问题；没有逐项补写 Ponder、FG、长期稳定性或 GPU 故障根因验收通过。

使用者决定后续性能测试改用项目内验证实例与探针，操作规则写入项目规范 X-03。Prism 中已禁用的 `RadianceAudit-0.1.5-alpha.jar.disabled` 经模组 ID 与哈希确认后移入 Windows 回收站；Radiance 本体、其他模组、实例配置、世界及已有证据保留，没有启动游戏。移除回执与本次手工日志副本位于 `D:/Workspaces/Artifacts/Radiance/radiance-audit-removal-20261010-143434/`。

### 托管 CI 前置条件修正

2026-10-10 核对 `develop` 的 `92dd71054d21e5b94a259dba5980cba7f049f63b`：[Specification](https://github.com/RecRivenVI/Radiance/actions/runs/38033538400) 成功，[Check](https://github.com/RecRivenVI/Radiance/actions/runs/38033538329) 失败。失败来自托管 Windows 未安装 Vulkan SDK，以及 Python 盘点工具缺少 PyYAML；不是模板规则、产品编译逻辑或运行验收失败。

项目的 `check.yml` 补充固定版本的 Python、盘点依赖与官方完整 Vulkan SDK。版本来自版本目录和工具的 requirements 文件，SDK 提取到运行器临时目录；仍只上传合规报告。没有修改受保护的模板文件、产品渲染逻辑或公开二进制许可门禁。

干净 Python 虚拟环境先确认没有 PyYAML，按 requirements 安装后盘点行为测试 8/8 通过。本次失败日志、依赖安装与后续本地验证保存在 `D:/Workspaces/Artifacts/Radiance/ci-fix-20261010-153325/`；修正后的托管结果以对应提交的实际 Actions 记录为准，不沿用历史本地构建结果宣称云端通过。不启动游戏，不运行 GPU 或性能实验。

官方安装器的 `copy_only` 模式本地执行成功，独立 CMake 检查从该临时 SDK 找到 Vulkan 与 shaderc 的头文件、链接库和 glslangValidator。`spotlessApply`、`check verifyRelease` 本轮通过；后者 137 项任务中 43 项执行、94 项复用。原生非 GPU 测试 62/62、诊断采集器 2/2 实际执行通过，严格合规检查覆盖新增文件共 1,767 项，失败 0、警告 0。Java 已有的 Unsafe 与 Gradle 弃用警告保留，不将合规零警告扩大为所有工具无警告。

### 托管结果与恢复开发

2026-10-10，`develop` 提交 `cd25c20cd3cd99132dca85ca310233ad04d88490` 的 [Check](https://github.com/RecRivenVI/Radiance/actions/runs/38036771761) 与 [Specification](https://github.com/RecRivenVI/Radiance/actions/runs/38036771760) 均成功。Check 实际执行 `checkRepository`，上传内容仅为合规报告；这不替代本地完整发行包、世界运行或视觉验收。上述首轮迁移和升级期间的“尚未执行”只描述对应历史阶段。

使用者随后报告严重掉帧主要出现在区块加载期间，并认为与单向全部切换为双向的改动有关。当前记录为待调查的时间关联，没有匹配计时，尚未证明因果或定位到 CPU/GPU 阶段。当前产品代码未改变，默认双向与来源规则的可恢复性保持原状。

该阶段更新路线图并启动了项目内测试实例，确认实际加载的原生库与构建输出一致；当时尚未取得世界内的匹配性能对照，拓扑准备与共用互斥锁只是待测线索。2026-10-12 使用者暂停调查，2026-10-15 重新授权后取得定点等待证据并实施锁范围修复，性能 A/B 仍未完成；见[当前路线图](roadmap-radiance.md#区块加载共享锁修复与剩余长帧)。

### 仓库维护边界

后续开发只在本仓库进行，原生代码位于 `components/native_renderer`。旧 MCVR 仓库保留历史参考，不再写入开发内容；本次明确授权的历史折叠不改变迁移源基线、旧验收日期或产物对应关系。使用者恢复单一 `Initial port` 策略，具体元数据、签名和临时备份要求见[项目规范](../AGENTS.md)。

旧 MCVR 的配套冻结检查点为 `532c1bd02e2c98d3112db80d15594e9a8325ba07`，保留其移植与静态审查台账，树与迁移来源 `81c08145fc8d0f7ec12dedc0f4b303a8098822e6` 完全一致。该单向引用不更新历史验收产物，不要求以后在两个仓库之间同步开发。

### 模板 2.0.0 升级

2026-10-13 按远端标签版本降序列表首项，从 1.2.1 升级到 2.0.0，克隆提交为 `ffb142eb98a5cb22b0bbf5c8b4e17387f8bc4036`。执行新克隆的升级规程，保留现有工作区与暂存区，不提交或推送；旧 MCVR 仓库未操作。

三个旧模板组件迁为 `gradle/conventions/` 单一插件构建；根构建、项目组件和 Target 的插件入口与三个类型引用随之调整。通用规范文档移入该目录的 `specification/`，项目特有的原生环境、运行与配置缓存说明保留在项目的构建规程和原生架构文档中。删除旧工具入口和独立 rumdl 配置；rumdl 升至 0.2.79，其余模板工具版本、项目选定的加载器插件与 Minecraft 依赖保持原值。

新 G-08/S-06 保护范围包含 Wrapper、JVM 要求和统一插件；F-06 固定模板工具及 Action 版本，D-05 链接检查交给 rumdl，D-17 明确最少项目文档。NOTICE 同步模板版本与归属路径，项目 CI 保留 Windows、原生工具准备、本地输入边界和只上传合规报告的限制。静态盘点工具同步排除 `gradle/conventions/`，避免把模板代码计入项目渲染源码；使用真实文件收集入口验证排除生效且产品源码仍被收集。

升级前 `check --continue` 成功，合规失败 0、警告 0。升级后 `spotlessApply`、`check --continue`、`verifyRelease` 和 `verifyCompliance --strict` 成功；严格检查为 2.0.0、失败 0、警告 0。154 个发行包必需条目、元数据和探针排除符合要求，JNI 头文件与 GAME Maven 边界检查通过。

本次实际执行 GAME 250 项、probe 25 项、bootstrap 11 项通过，bootstrap 两项要求显式 GPU 环境的测试跳过；原生非 GPU CTest 62/62、诊断采集器 2/2、Python 盘点 8 项及离线盘点验证通过。新模板的 compliance/configuration/target 测试分别 63/65/30 项通过；工具 Java 基准与盘点测试沿用未变化输入的 UP-TO-DATE 结果，不记为本轮重跑。编译器仍提示已有 Unsafe 和过时 API 用法，Gradle 仍提示废弃接口；这些不属于合规警告。

参考 Target 的日常 `runClient` 使用 2560×1440、主音量 0。进程 52308 的日志列出 Radiance 0.1.5-alpha，完成资源加载并进入渲染主循环；窗口响应正常，未进入世界。通过仅匹配本仓库命令行的进程正常关闭，日志记录 `Stopping!`、场景释放和 SDK 待销毁资源为 0，任务成功且没有新崩溃报告。未使用 Computer-Use 或读取画面，菜单视觉不作为本轮通过项。`local.toml` 未接受 EULA，按授权跳过 `runServer`，该文件字节不变；没有运行 GPU 独立测试、世界验证或性能实验。

1,557 个产品源码及资源输入相对升级前原始字节哈希不变，122 个模板文件与克隆一致，暂存区条目未改变。按规程移除的 25 个文件或目录已进入 Windows 回收站，未永久删除。

证据位于 `D:/Workspaces/Artifacts/Radiance/template-upgrade-20261013/`：`D:/Workspaces/Artifacts/Radiance/template-upgrade-20261013/evidence-archive.zip!before-check.log`、`D:/Workspaces/Artifacts/Radiance/template-upgrade-20261013/evidence-archive.zip!after-check.log`、`D:/Workspaces/Artifacts/Radiance/template-upgrade-20261013/evidence-archive.zip!verify-release.log`、`D:/Workspaces/Artifacts/Radiance/template-upgrade-20261013/evidence-archive.zip!strict-report-final.json`、`D:/Workspaces/Artifacts/Radiance/template-upgrade-20261013/evidence-archive.zip!test-counts.json`、`D:/Workspaces/Artifacts/Radiance/template-upgrade-20261013/evidence-archive.zip!client-result.json`、`D:/Workspaces/Artifacts/Radiance/template-upgrade-20261013/evidence-archive.zip!run-client.log`、`D:/Workspaces/Artifacts/Radiance/template-upgrade-20261013/evidence-archive.zip!integrity-final.json` 与 `D:/Workspaces/Artifacts/Radiance/template-upgrade-20261013/evidence-archive.zip!recycled-paths.json` 分别记录基线、检查、发行包、合规、测试、运行、文件完整性和回收结果。客户端之后只更新文档记录与离线盘点工具的目录排除，不影响运行产品；收尾复核另存 `D:/Workspaces/Artifacts/Radiance/template-upgrade-20261013/evidence-archive.zip!final-format.log`、`D:/Workspaces/Artifacts/Radiance/template-upgrade-20261013/evidence-archive.zip!final-check.log`、`D:/Workspaces/Artifacts/Radiance/template-upgrade-20261013/evidence-archive.zip!strict-final.log` 和 `D:/Workspaces/Artifacts/Radiance/template-upgrade-20261013/evidence-archive.zip!inventory-template-exclusion.json`。未推送，因此本轮没有对应的 GitHub Actions 运行结果。

### 存储清理

2026-10-16 按使用者授权收敛旧运行副本和迁移前构建备份；保留脏工作区、必要输入、当前投放与唯一证据。证据位置映射、容量例外和本轮检查见[存储清理调查](../research/audit-storage_cleanup.md)。此项不改变现有视觉、性能与设备丢失结论。

同日追加授权的[补充核验](../research/audit-storage_cleanup_supplement.md)已清退三套被替代的旧构建，现存五个原生构建身份。旧 JAR、DLL、PDB 的完整哈希与对应源码快照统一由 `D:/Workspaces/Artifacts/Radiance/storage-cleanup-20261016/manifest.json` 的 `review15.builds` 定位：

| 历史核心 DLL 前缀 | 清单记录 | 仍保留的源码快照 |
| --- | --- | --- |
| `dedb2b00` | `review15.builds[0]` | `D:/Workspaces/Artifacts/Radiance/gpu-diagnostics-20261015/source-before/source-snapshot.zip` 与 `source-final/source-snapshot.zip` |
| `f9ba4c1e` | `review15.builds[1]` | `D:/Workspaces/Artifacts/Radiance/chunk-loading-20261015-044501/source-snapshot.zip` 与 `source-final-delta.zip` |
| `1e511ca3` | `review15.builds[2]` | `D:/Workspaces/Artifacts/Radiance/migration-20261009-1800/source-snapshot-final.zip` |

旧 `D:/Workspaces/Artifacts/MCVRSceneReplay/20260924/` 的现行取证入口为 `D:/Workspaces/Artifacts/Radiance/history-archive/MCVRSceneReplay/20260924/`，原顶层目录已移除。历史研究正文保留；其中明确引用的 JFR、分析文本和 JSON 保持原文件，其余冷证据按各调查目录的 `evidence-archive-index.json` 定位。旧回放程序仍处于退休状态。外部合计低于硬上限，但软上限尚超，保留理由见补充记录和清单，不再沿用首轮“仅 SDK 造成软超”的说明。

自动测试与手工实例的新窗口、声音、名称和辅助模组约定已写入[构建规程](../development/procedure-build.md)。本轮仅验证配置与构建输入，未启动游戏或修改 Prism，不补写运行与视觉通过。

### 城市 GPU 帧预算盘点

2026-10-18 已完成[GPU 盘点](../research/investigation-city_gpu_budget.md)：补齐 SHARC 分段和实际分派尺寸，正常城市窗口 CPU 1,625／GPU 1,623 样本；世界 PT 约 12.073 ms、主队列约 16.878 ms，SHARC 更新／解析约 0.217／0.160 ms。当前平均 GPU 预算仅供约 59～62 FPS 的条件性参考，不能证明硬件理论上限或稳定 60 FPS。Nsight 连接后因驱动硬件计数权限失败，未取得 GPU Trace；未改全局权限或频率，没有设备丢失。

该 GPU 盘点阶段尚未完成动态输入归因；随后授权的归因和烘焙模型专项见下节。GPU 仍首选细分世界 PT 的遍历／材质／透明路径，不优先重写成本较小的 SHARC 或 GPU 顶点转换。不以降低质量、删几何或冻结动态换帧率，不自行启动下一项优化。

### 城市四档分辨率实测

已完成同一城市副本的四档分辨率对照，低分辨率主要受 CPU 侧限制，2160p 转为 GPU 执行／等待主导并伴有显存预算告警。窗口尺寸失败、统一无边框后的主表、运行间波动及完整证据见[实验记录](../research/experiment-city_resolution_scaling.md)。仅探针增加显式视口门禁，产品身份不变；没有新的优化、视觉验收或 GPU 根因结论。

### 动态烘焙模型专项

随后按授权完成[生产者归因与持久烘焙模型优化](../research/experiment-dynamic_baked_models.md)。城市动态 PBR 顶点主要来自物品展示框；旧持久模型开关实际默认关闭，绘画和其他程序化来源也没有局部模型合同。本轮仅启用满足既有资格的烘焙模型，并减少重复 recipe 克隆；原 renderer、外观状态、实例历史、纹理代际和 native 完成退休保留，ModelPart 原型仍关闭。

正式 1080p 窗口实际 PBR 生成量约减少 69.5%，动态 BLAS 输入 609→407，但 TLAS 实例增加 292。GPU 区间与整帧的最终对照、重载／删除／回退检查、2160p 仅静态盘点的预算边界及冻结产物均见专项记录，不由减少顶点宣称全链路或 60 FPS 目标通过。未新增人工视觉或长期稳定性结论，不访问 Prism、不提交或推送。

### Voxy 保留后的 1080p 复测

2026-10-18 使用者保留 Voxy 初步适配，重新授权一次原城市 1080p 测量。[当前窗口](../research/experiment-city_1080p_recheck.md)保持原夹具、32 视距、Vanilla PT、RR Balanced 和相同几何计数，测量实例不加载 Voxy。PID 31920 正式采样 60 秒、2,079 帧，均值 28.870 ms（约 34.64 真实 FPS），p95／p99 为 36.279／43.886 ms；渲染线程 CPU 约 28.454 ms、主队列 GPU 区间约 17.396 ms。同期另有外部客户端，不把较历史样本的差值认定为单项优化收益。

当前源码与产物未改，F3+T 字体违规为 0，各维度保存、native 正常关闭和 Gradle 退出码 0 已核对；没有设备丢失。原始表、实际 DLL 及环境证据单独保留，不延伸为 Voxy 性能、完整视觉或长期稳定性通过，也未启动下一项优化。

### CPU 任务减负与并行准备

2026-10-19 完成[捕获容器减负与几何准备并行实验](../research/experiment-cpu_task_preparation.md)：缩小未使用的固定表、延迟创建破坏覆盖层 provider、在真实批次收齐后跳过空记录，同时保留刚性模型和来源回调。完整配套构建、自动回归、相关 GPU 三项及严格合规通过；最终默认串行候选完成世界采样、F3+T、保存和关闭。

中间控制与减负窗口观察到 25.285→24.173 ms，最终串行复查为 25.657 ms，不能宣称稳定的整帧提升。同一原生原型中两／四线程将拓扑准备约 0.730 ms 降到 0.458／0.336 ms，但四线程尾部变差，默认继续关闭；最终两线程复查在进世界前遇到 Create/Registrate 注册错误，已保存原始故障且未重试，不计入性能通过。全部有效窗口 30,612 帧无设备丢失，不延伸为长期稳定性、视觉验收或 GPU 根因闭合；源码和构件按专项清单固定，没有投放 Prism 或 Git 写入。

使用者随后决定多线程留待以后。本轮仅完成[材质状态成本调查](../research/investigation-material_state_cost.md)：重新汇总既有样本并核对当前源码与实际 NeoForge 字节码，将下一候选定位到保留真实回调的状态桥接批量化；没有修改产品或探针、启动新客户端或取得新的性能结果，当前构件身份不变。

### 2026-10-19 材质状态桥接实验

随后按使用者授权完成实际请求计数、相邻 JNI 批量化、真实回调／嵌套／异常和 GPU 像素读回验证，以及同城 ABBA。没有测得稳定整帧提升，因此实验开关默认关闭，多线程仍默认 0；具体窗口、不同产物与最终构件身份见[实验记录](../research/experiment-material_state_batch.md)。最终默认构件另执行无性能采样的同城功能流程，不混用性能包与最终包的运行结论。没有扩大视觉／加载期／4K／未知故障结论，未投放 Prism、提交或推送。

### 2026-10-19 绘画持久化

[绘画专项](../research/experiment-painting_persistence.md)已完成：为来源已确认稳定的原版绘画默认复用完整局部网格／BLAS，保留逐格光照、姿态、材质与完整场景；其他来源和容量不足仍完整生成。先用实际生产量选择目标，再确认每帧绘画动态顶点 13,632→0、动态 BLAS 输入 223→0，总 TLAS 实例数保持 18,774。材质状态捕获没有被删掉。

最终同一 JAR 的两个 60 秒窗口为 31.477→26.886 ms（约 31.77→37.19 真实 FPS），p95／p99 为 38.217／41.384→31.542／32.998 ms；原型四窗的独立结果为约 14.74% 帧时间下降，最终配对为 14.59%，不跨构件拼接。两组绝对帧率有明显波动，尚不是所有场景、无探针发行版或长期稳定性的保证。1080p 为输出，实际 PT 使用既有 RR Balanced 尺寸，不称原生全分辨率 1080p。

最终 JAR `A667187A61305E67D1E95F37ECA6F8ED3D7CE5F62710E355E687DC3763BF910B`，core `B023613AE93F98B4D54433ADB97D78D50DB1A380D4F8403F83FBE580F30A712C`。实际源生成器在重载前后共比较 1,024 draw／63,072 顶点，位置、法线和材质字节一致、模型身份更换；功能与性能构件的产品类和 core 完全相同。完整检查通过，GAME 337、probe 41、原生非 GPU 74/74，相关 GPU／生命周期检查通过；两个因来源资格信息不可用而安全回退的候选另记为失败，不冒充最终通过。各有效进程完成保存和关闭，没有设备丢失，没有新的人工视觉、Prism 投放、Git 暂存、提交或推送。
