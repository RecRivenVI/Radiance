# 当前项目状态

2026-11-02，使用者决定暂停 SER 接入，整理正式候选供 Prism 验收。产品源码不含 SER 开关、重排指令或未测 AS 策略钩子；独立实验与故障证据保留。候选保留透明均衡采样、SHARC 查询／训练权重修正、末次反弹省算及材质／首命中状态减负，配置选择不自动覆盖。性能与画面边界统一见[GPU 专项收尾](../research/experiment-gpu_optimization_closeout.md)。本轮只构建和投放，不启动客户端，不提交或推送。

同进程已完成窗口的普通帧为 22.01→18.17 ms，世界 PT 为 15.88→11.73 ms；末尾参考、多姿态和新候选视觉验收未完成，不承诺全城真实 60 FPS。SER 复现不依赖 Photoshop 运行或持续 AS 重建；最新实验还发现不崩溃的变体存在显著颜色差异，普通 coherent 补丁未能稳定解决设备丢失。SER 没有可信性能收益，不能作为正式候选优化项。原始调查见[执行优化实验](../research/experiment-sharc_execution.md)及[故障复测](../research/incident-ser_device_loss.md)。

## 当前候选

当前产品候选使用无 SER 的正式构建；Extra 沿用 `bb98550ed61a…`，API 16 不变。M 故障候选 `7e0d9825c89d…` 仅作诊断证据保留，禁止作为默认交付。SER 开启前四组窗口有效，末尾参考及后续 AS／NRD／Advanced 运行因设备丢失未执行。地图、配置和临时探针已恢复。转储确认为 Read 页错误，尚无具体 GLSL 行及根因，不自动重试。

前轮透明预算 K 本体 `e46d28eec779…` 完成构建、发行、严格合规、Native 82 项非 GPU／28 项 GPU 和 268 个着色器请求；城市、转头及重载正常退出，详见[透明预算实验](../research/experiment-transparent_budget.md)。这些证据不代替本轮改动的最终客户端验证。

2026-11-01，使用者决定暂缓 CPU 优化，启动 Native GPU 系统优化，允许有损质量预算及 NVIDIA 专用优化。首组透明预算已实现，已有个人选择不自动更改；需选择“均衡采样”才启用。最终 K 在有外部负载的水面点位，RR 普通帧由 51.79／45.34 ms 降至 34.57 ms，但参考自身漂移、尾部仍有波动，不作全城或独占设备收益承诺。中间 I 的种子分布缺陷已修正，不用其较低数字宣称最终帧率。手部 miss 专项没有稳定优先优化依据；NRD 独立启动正常，RR→SR 切换矩阵警告保留为独立缺口。实际设备能力、计数器缺口、质量验收和后续顺序见[GPU 计划](plan-gpu_optimization.md)。Nsight Qt 导出失败仍未形成逐着色器结论。

2026-11-01，普通材质恢复已有声明状态路径，未知变换和自定义图层仍独立判断；CPU 暂存按录制上下文复用，退出世界释放。命中组采用不可变内容版本，每个通道及帧槽独立解析，SHARC 与查询复用结果；真实变化及 GPU 在途版本保持原有更新和退休。自动功能验证已完成，整体帧时收益和人工体验分别判断。

2026-10-31，HDR 界面参考白与 Bloom 默认值修正已投放 Prism，本体 `78ba0ed3…`、Extra `de048d1c…`。界面默认跟随系统 SDR 白，世界参考白独立，Bloom 默认降为 3%，首版默认值一次性迁移；使用者反馈修改后画面看起来没有问题。此前及本轮反馈不代替 HDR 补帧专项视觉验收，详见[界面参考白记录](../research/investigation-hdr_ui_reference_white.md)。

2026-10-31，自动 GUI 白真实读取 Windows 的 480 nit，最终输出读回 481.56 nit；世界参考白仍为 200 nit。手动白、SDR／HDR 往返及自动设置界面回归通过，配置迁移保留非默认值及后续手选值。构建、发行校验、严格合规、82 项 CPU CTest、28 项 GPU CTest 和迁移测试通过。实际 HDR 体验等待本轮复核。

2026-10-31，新增“显示与 Bloom”设置：HDR 开关、世界参考白、界面白、自动／手动峰值及独立 Bloom。优先 HDR10，支持不足时 SDR 回退；scRGB 路径暂停 FG。82 项非 GPU CTest、28 项 GPU CTest、268 个着色器配置请求通过；两套算法完成完整场景，最终候选补测亮度原位更新和 HDR／SDR 往返，实际 GUI 白 120 nit 读回 120.47 nit。详见专项记录，内部读回不代替人工验收。

2026-10-31，`section-order-20261031` 配套候选完成两仓库检查、发行校验及严格合规，Native 81 项非 GPU 测试通过。隔离回归覆盖装拆、分离、合并、重载、普通成员刷新不创建交接及槽位优先级清除；同一城市副本的 422 个有内容区段全部就绪，准入 trace 无丢失、本次没有 `publish-held`。未测性能，不将加载顺序计数写成提速。实际连续加载及交接观感待使用者验收，保留输入到达、预算及并行完成造成的合法顺序差异。

2026-10-31，W 以每区块无损材质字典减轻驻留，水晶按真实内容接受新发布，修正程序薄层远坐标投影和独立区段形状通知。32 视距城市干净窗口 1080p 为 25.10→24.68 ms、1440p 为 25.27→24.18 ms；长期移动返回后为 60.49→23.58 ms，专用显存峰值 13.10→7.68 GiB。静止 CPU 改善未稳定成立，首次队列平静 164.73→192.35 秒，不能宣布加载提速。水晶无关编辑的资源定义创建 30→0；连续动画继续执行。具体波动、代价、基线与 GPU 口径见审查记录。当前配套等待人工体验，不继承旧候选的目视通过结论。

2026-10-30，T 修正主世界新编译请求提前退休已显示水晶来源快照的问题，外部区段同步使用相同所有权边界。R+S 在第一次邻近方块更新后复现主体退出提交集合；T+S 在八次放置／破坏、连续 216 个观察帧内保持主体，删除最后一个来源图层及全空气区段的唯一水晶后正确清空，F3+T 恢复。两仓库检查和发行核验通过，64 份实际 GPU 读回仍符合原公式；正常退出，无设备丢失。已投放 Prism 本体并核对哈希，Extra 继续沿用 S，未启动游戏。详见[区段更新闪失](../research/incident-levitite_section_publication.md)，人工连续观察待使用者复核。

Ponder 浮空水晶教程的偶发拉伸暂未复现，使用者再次复查也未触发；保持待复现，不标为已修复。已确认来源在 Ponder 中主动关闭世界特效，当前保留此行为，没有为该报告修改产品或重新投放。两次隔离采样与边界见[Ponder 调查](../research/investigation-levitite_ponder.md)。

2026-10-30，R+S 将主体的颜色细分与几何分开，去掉 16 倍主体几何扩张；GPU 生产颜色场与真实形变的两层重影，共用一份来源，接触淡化按局部变化更新。两仓库完整检查和发行核验通过，Native CPU 80/80、GPU 25/25 通过。Vanilla PT 的真实客户端覆盖一／二／三方块结构、正反速度、玻璃、无遮挡及 F3+T；48 份 GPU 读回的 30,720 个顶点无退化，另核对 4,000 个主体字段节点，客户端正常退出且无设备丢失。此前 P+Q 的 Advanced 回归也通过，构件身份分别保留。

同一 32×32 薄片、相同配置的正式平均帧间隔：主世界静止 30.382→7.752 ms，sublevel 静止 30.783→7.677 ms，形变 181.323→8.797 ms。输出 1920×1080，沿用 FSR、内分辨率 1280×720；不将此专用夹具收益推广为城市 FPS 或区段编译吞吐。方法、排除的初步样本及精度证据见[表面生产与规模开销](../research/investigation-levitite_surface_cost.md)。配套身份位于 `versions/1.21.1-neoforge/build/candidates/levitite-rs-20261030/candidate.json`。已将实际测过的 R+S 投放 Prism，仅替换本体及 Extra 并核对 SHA-256，没有启动游戏；人工验收待使用者完成。

## 上一人工验收基线

2026-10-30，M 本体为 `c4af672f…`，Extra 沿用已验收 L `31dde891…`。来源先建立交接保护，已封闭的作用域随后刷新完成结果，再检查版本就绪；每个活动世界帧最多额外刷新一次，无待交接时不增加调用。既有准入阶段的完成回收和预算采样保留，不新增阻塞等待，不修改编译合并窗口。完整检查、发行核验、Native 80/80、GPU 24/24 与严格合规通过；实际 Sable 交接、重载、卸载和视距换代回归通过，同一城市的 422 个内容区段到齐并正常保存退出，没有设备丢失。候选身份为 `components/render_diagnostics/build/section-completion-m-20261030/candidate-m.json`，功能证据为 `validations/conformance-takeover/result/section-completion-m-20261030/`。已替换 Prism 本体并核对哈希，没有启动 Prism；没有新性能收益结论，使用者已确认分离后的衔接改善有效。

2026-10-30，L 本体为 `3e684149…`、Extra 为 `31dde891…`。两仓库检查及发行校验、Native 80/80 与 GPU 24/24 通过；真实交接、重载、客户端空间关闭和视距换代回归通过，同一城市 422 个内容区段到齐。首分钟没有再出现此前超过一秒的主世界完成后发布等待，大型结构观察到逐段可用；受其他客户端干扰，不作吞吐或 FPS 收益结论。此前卸载探针的服务器／客户端原因差异已校正，产品包未因此改变，使用者已确认验收通过，见[加载顺序与初次发布](../research/incident-section_loading_priority.md)。

2026-10-30，K 本体为 `b2706664…`、Extra 为 `7dc5c3c5…`。完成按持有者取消、槽位重定位、失效代际重收集、来源关闭后的保留区段姿态、失败退避与等待原因日志。两仓库完整检查及发行校验、Native 80/80 与 GPU 24/24 通过；实际 Sable 交接、重载、卸载、视距切换及大型结构 422 个内容区段均通过。未作新性能比较，连续视觉待验。身份、证据及边界见[区段交接收口](../research/incident-section_handoff_lifecycle.md)。

2026-10-30，J 本体为 `3ed8bd72f7fd56631486d5e6114d5627c949cbaa3c7af00bfb7a92c8a33c53e1`，Extra 仍为 D，Native 与 I 字节相同。同一 Prism 保存副本中，I 的 665 个已登记区段在队列清空后仍无可用几何；J 恢复全部 422 个有内容区段，截图确认结构显示。完整检查、发行校验及隔离功能运行通过，无设备丢失，人工交接体验待验。详见[空区段交接故障](../research/incident-sable_empty_handoff.md)。J 不继承 I 的性能数据。

2026-10-29，当前 I 本体 SHA-256 为 `8407f036f4e779a9e09f0c150dded0cc80e3a63d566cdbf4011708362ea739e0`，Extra 为 D `1093201e…`。已完成静态 TLAS 模板与所有权组、稳定目录复制计划、公共批次暂存复用、小型长期资源分配候选及预算归因。完整构建、发行和严格合规通过，Native CPU 80/80、GPU 24/24。正常配置的同 C 探针 FRAME_ONLY 对照为：1080p 静止 24.11→22.09 ms，1440p 静止 25.78→21.87 ms；移动倒序复核没有稳定收益，不宣称已改善动态加载。部分省时属于默认诊断的空转消除，不能全部算成普通 Prism 收益。I 的完整生命周期正常结束，无设备丢失，最后 WDDM 独占约 2.70 GiB，E 约 6.18 GiB；初次加载没有改善证据，实体移除后的 GPU 增时与超预算驻留相关，根因仍未定案，不能宣布全工况性能通过。全部边界、原始路径与剩余热点统一见[本轮实验](../research/experiment-runtime_efficiency.md)。未运行 4K 压力、未投放，人工视觉待验。

2026-10-29，城市性能基准复核发现此前夹具遗留 128 次最大反弹和前景／背景混合，旧调查对“四次反弹、随机覆盖”的描述错误，旧约 170／289 ms 不能代表正常游玩配置。已从当前 Prism 只读冻结正确参数和两份 SPBR，继续使用 E 产品、同一城市、32 视距且不带 Voxy；实际模块参数核验通过，1080p／1440p 的 FRAME_ONLY 均值为 24.38／25.32 ms，最小观察者的 1080p 为 24.71 ms。当前点位主要应看 CPU 来源和提交，1440p GPU 主队列约 22 ms。移动、对象增删与两次退出重进正常结束，无设备丢失；最后 VMA 块约 6.24 GiB、有效约 1.74 GiB，碎片问题仍成立。生命周期最后约 52 秒缺逐帧记录，资源记录完整，边界已标注。旧记录保留，更正与最新边界见[城市重测](../research/investigation-city_baseline_retest.md)。本轮工作区 22 GiB、验证和外部各 8 GiB，没有产品优化、提交、推送或新投放。

2026-10-29，当前配套为 `validations/conformance-takeover/result/source-efficiency-20261029/source-efficiency-e-20261029/candidate.json`。本体和 Extra 检查、发行与严格合规通过，Native 79 项 CPU／24 项 GPU 通过。弹簧稳定形状复用及 22 次原算法对照、Voxy 七阶段按实际 D 配套保留证据；E 增加内置物品路由崩溃修正。E 在同一冻结城市、32 视距、1080p 完成静止和固定移动，FRAME_ONLY 为 152.57／158.48 ms，GPU 世界 PT 为主要限制，无设备丢失，正常重载保存。基线在显存压力下主动停止，没有可比的稳态窗口，不能宣称整帧收益。细节与剩余范围见[实验记录](../research/experiment-source_efficiency.md)；本轮工作区临时允许 22 GiB，其他上限不变。

本配套已按授权投放 Prism，本体和 Extra 读回 SHA-256 一致，Voxy 保持原构建；其他模组、配置与存档核对不变，没有投放探针或启动游戏。回执在当前配套目录的 `prism-deployment.json`，仍待人工体验。

2026-10-29，水材质后续候选为 `validations/regression-voxy_integration/result/water-material-20261029/candidate.json`。去除 LoD 水的来源 RGB 例外，近远景与 Advanced 侧面／缓存共用水光学基底，复用前段水身份而不重复查纹理。完整检查、发行核验、严格合规、源码／包内各 268 个着色器变体及 3 项定向 GPU 通过。首次客户端在早期窗口分配处返回 `VkResult=-2`，未进入世界；用户释放内存后，同一冻结候选通过七阶段，实际移动 14.32 格、历史检查通过、正常保存退出，两次均无设备丢失。已按约定直接投放 Prism，仅替换本体，Extra 与 Voxy 哈希一致，10,567 个其他文件未变化，没有投放探针或启动游戏；回执为同目录 `prism-deployment.json`。新水面视觉仍待确认，失败与成功证据分别保留，见[本轮记录](../research/incident-voxy_water_material.md)。

2026-10-29，当前 Voxy 水面／历史配套为 `validations/regression-voxy_integration/result/water-history-20261029/candidate.json`。本体重新构建，Extra 产品沿用染色修复版，探针单独更新；79 项非 GPU、3 项定向 GPU、122 项 Extra 回归及源码／包内各 268 个着色器变体通过，发行与严格合规通过。实际客户端移动 14.4 格，Native 核对历史矩阵与着色器上一帧相机通过，七阶段正常保存退出，无设备丢失。同日按用户授权投放 Prism，仅替换本体，Extra 与启用的 Voxy 哈希核对一致；10,414 个其他文件保持不变，未投放探针或启动游戏，回执为同目录 `prism-deployment.json`。动态水面和用户抖动现象仍待体验确认，见[调查与测试边界](../research/incident-voxy_motion_and_water.md)。本轮工作区临时允许 22 GiB，其余容量限制不变。

2026-10-29，Voxy 染色表配套为 `validations/regression-voxy_integration/result/tint-20261029/candidate.json`，使用 Veil 本体与新的 Extra。Extra 122 项回归、发行结构及严格合规通过；真实来源的三类染色在七个检查点均存在并通过表校验，来源修改实际推进模型和 Native 构建。旧 epoch 拒收、重载、世界退出重进、保存关闭通过，无设备丢失。两次探针前置失败已修正并保留证据，详见[染色表调查](../research/incident-voxy_tint_table.md)。没有重编 Native，不将限定功能通过当作颜色、接缝、连续移动或长期稳定性的人工验收。本轮工作区获准在 20 GiB 内完成，其余存储限制不变。 同日按用户授权更新 Prism 的 Extra 并重新启用实际测试的 Voxy，本体已是配套版本、未重复替换；三个产物哈希核对一致，10,387 个其他文件保持不变，未投放探针或启动游戏。回执为同目录 `prism-deployment.json`。

2026-10-28，当前 Veil C 配套为 `validations/conformance-takeover/result/veil-adapter-20261028/candidate-c.json`。CPU 布局按编译对象和 uniform cache 身份维护，Native 编号重用不交叉覆盖；来源 block 的变化通知、packed／global 所有权及混合状态保留，固定全屏绘制不重复生成或重绑。Native 只复用已结束在途引用且尺寸／用途匹配的退休缓冲。完整构建、79 项非 GPU CTest、2 项定向 GPU、120 项 Extra 回归和严格合规通过；最终项目内组合完成重载前后两次实际颜色读回、平台及实体检查并正常保存退出，无设备丢失。具体边界、首次探针作用域失败和证据见[本轮记录](../research/investigation-veil_resource_reuse.md)。没有性能收益或完整效果目视结论。2026-10-29 按使用者授权投放 Prism，两个 JAR 哈希一致，10,237 个其他文件保持不变；Voxy 继续禁用，没有启动游戏，回执为同目录 `prism-deployment.json`。

2026-10-28，当前 Catnip 包装合同整理候选为 `validations/conformance-takeover/result/received-light-20261028-b/candidate.json`，只更新 Extra、本体沿用已冻结 A 产物。使用者澄清安山门观察可能为贴图固有色差，后续源码核查未证明真实 Catnip 路径会产生探针主动构造的 Sable 包装组合；不再将前次合成输入结果当作门视觉缺陷的根因。按决定保留已知包装的一致性能力，直接 PT 输出跳过来源遍历，源纹理与预览不变。身份和验证以候选清单为准，详见[复核与更正](../research/review-catnip_wrapper_contract.md)；前次 73 组输入及原记录保留。不自动投放 Prism、不测性能、不提交、不推送。

2026-10-28，当前为配套来源适配候选 `validations/conformance-takeover/result/adapter-source-20261028-b/candidate.json`：Flywheel 脏实例队列、Catnip 局部模板、世界受光剥离、正式 instancing 后端和普通 BER 共同交接已经实现。两仓库最终构建／回归／发行／严格合规通过；A 的项目内装配、拆解、合并、分离、删除、重载会话通过，无设备丢失。Extra B 仅追加离屏回退域保护，已重跑自动检查，未重跑整场客户端；没有性能实验，动态画面待用户验收。详见[本轮记录](../research/investigation-create_persistent_sources.md)。配套 JAR 已投放 Prism 并核对哈希，其他模组、配置和存档未改动，没有启动游戏；回执见候选目录 `prism-deployment.json`。不提交、不推送。存储恢复原上限，前轮容量临时放宽不再延续。

2026-10-27，前序天空候选为验收修复 B，本体与 API 7 Extra 配套，唯一身份为 `validations/conformance-takeover/result/visual-fixes-20261027-b/candidate.json`。恢复末地传送门来源投影及逐层颜色累加，移除日月的虚拟地面裁切，保留物理透射材质的退出边界，并恢复原版无世界界面 60 FPS 限制。完整构建、79 项非 GPU、24 项 GPU、268 项着色器预检及四组发行包功能会话通过。首次候选的新增可选 alpha 读取空指针已定位并修正，失败证据保留，未投放；没有设备丢失或性能收益实验。当前候选的动态外观及用户原场景仍待人工验收，见[本轮记录](../research/incident-celestial_portal_and_medium.md)。未提交、推送；本轮明确续准工作区容量暂停，验证与外部目录仍执行 8 GiB。

2026-10-27，前一 Prism 候选为 `validations/conformance-takeover/result/endsea-sync-fog-20261027/candidate.json`：保留高度压黑和缓存修复，补齐 End Sea 同帧投影材质与前景雾合成。增量构建、Java 回归、发行包、13 项着色器及定向 GPU 回归通过；使用者要求直接投放，隔离客户端取消，人工视觉待验，见[本轮记录](../research/incident-end_sea_frame_and_fog.md)。Extra 沿用前一配套版本，没有启动 Prism；不能沿用旧候选的实际运行结论。

2026-10-27，134／135 号已形成世界材质、光照、End Sea 和启动配对候选，接口为 API 6；唯一身份为 `validations/conformance-takeover/result/world-materials-134/candidate-prepared.json`。130 仅作冻结基线。完整构建、79 项非 GPU、24 项 GPU、发行与严格合规通过，实际启动、材质、动态软遮罩及末地往返正常结束；实现、失败记录、配置和边界见[世界材质实验](../research/experiment-world_materials.md)。准备期间保持单窗口与连续完成帧，无自有缓存／显式复用自有缓存分别约 37.1／20.7 秒到菜单，未清理全局驱动缓存，不能宣称完全解决首次冷编译或全部原因。动态视觉待用户验收，没有操作 Prism、提交或推送。

2026-10-26，130／131／132 号已形成配套整体候选，本体与 Extra 使用 API 5。稳定定义、绑定、姿态和文字布局由来源维护；原版特殊路径、Sable 共享实体、显式 Native 端点插值及世界未受光材质已有实际自动证据。荧光文字显式发光与普通文字分开，动画和必要回调继续执行。完整构建、原生／GPU、组合与重载验证通过，城市和来源密集对照未证明稳定整帧提速，仍有加载长帧。具体结果、身份、失败及限制见[本轮实验](../research/experiment-persistent_sources.md)。人工视觉待验，使用者随后授权投放配套 Prism、不启动游戏；实际投放结果见候选清单关联回执。不提交或推送，历史验收不移记到新路径。

现行存储按项目规范 X-12：工作区软 20／硬 25 GiB，验证及外部各保留 8 GiB 硬上限。完整候选按当前、Prism 在用、最近验收、性能基线及在查故障角色保留，旧引用不再阻止退役；符号与原始验证记录另按生命周期收敛。规则与退役清单入口见[存储清理](../research/audit-storage_cleanup.md#长期保留与退役)，历史临时额度不再约束后续运行。

本轮基线是人工验收发现持久实例缺键后冻结的 `source-scene-fix` 配对包。它已修复同批次不变选择与来源删除交错，并补齐快速复用来源的强引用；原生 78 项、GPU 23 项、完整构建与发行核验、真实 JNI 交错已通过。原崩溃具体来源事件没有被记录，不能将它归因于某个第三方模组或 Java 版本；详见[故障记录](../research/incident-persistent_selection_retirement.md)。该历史包的投放授权不扩大为本轮自动投放授权。

2026-10-25，使用者批准[来源维护的持久场景](../design/adr-14-source_owned_scene.md)。本体与 Extra 已实现共同空间、通用区段前段、Sable 成员事件维护、Flywheel 父姿态／光照继承和局部模型打包前判变；已接管粒子具有来源寿命。配套构建、自动回归、发行核验、严格合规、纯原版／组合与完整城市生命周期通过，未发生设备丢失。四次城市对照中，巨大结构全就绪约 23.4→7.1 秒，移动约 25.32→23.67 ms；站立均值约 21.97→21.50 ms，但运行波动和尾部不支持稳定收益结论，详见[实验](../research/experiment-source_owned_scene.md)。新人工视觉仍待验收。验证目录本轮临时允许 5 GiB，其余存储及安全边界不变；不提交、不推送、不自动投放 Prism。

129 号已修复巨大 Sable 结构因空区段误判及有限调度轮次饥饿而不显示的问题。来源为使用者正常保存后的 Prism 配置、模组和 Newport City 完整副本，665 个区段中 422 个有内容区段实际就绪，截图确认结构恢复。唯一身份为 `validations/conformance-takeover/result/platform-sublevel/candidate.json`；根因、运行边界与 4K CPU 分布见[当时调查](../research/investigation-large_sublevel_cpu.md)。129 已按使用者随后授权投放 Prism，交接回执保留；不将这一操作自动记为全面视觉验收。

128 号的受限效果扩展、来源判断、资源所有权和 LOD 覆盖表修复保留，原配对身份仍为 `validations/conformance-takeover/result/platform-closeout/candidate.json`，历史边界见[收尾复核](../research/experiment-render_platform_closeout.md)。不覆盖 127／128 产物，也不将初步体验记为全部验收。

127 号整体重组的步骤一至四保留；其首轮清单在 `validations/conformance-takeover/result/platform-reorganization/candidate.json`，历史证据见[重组实验](../research/experiment-render_platform.md)。当前架构与正式接口分别见[架构说明](../design/architecture-native_renderer.md)、[公共合同](../reference/api-render_platform.md)。不混用新旧候选结果。

本体负责 Vanilla／NeoForge 与通用平台，第三方适配位于同级独立仓库 `../Radiance-Extra/`。Extra 有独立构建入口、固定本体开发依赖和两份实际发行包的组合运行，不内嵌本体或另一个渲染器。本体在 develop 保留上游历史，将自有改动合入 `Initial port`；[Extra 公开仓库](https://github.com/RecRivenVI/Radiance-Extra) 的 develop 首提交为 `Initial commit`（`02b6182681dd`），两边分别保存历史。

正式局部模型路径现在传持久身份及参数变化，Native 保留跨帧实例；瞬时内容另有入口。Voxy／Flywheel 的源格式解释在 Extra，Native 接收通用紧凑数据。现有区块、查询、纹理、历史和 GPU 退休保留。状态页能查看实际路由与 CPU 消费；它不是 GPU 完成、覆盖百分比或目视通过。

## 本轮验证与人工入口

129 本体和 Extra 的构建、自动回归与发行核验通过；原包在同一副本复现巨大结构缺失，新包两次正常显示并保存关闭，未发生设备丢失。3840×2160、32 视距的加载／站立／移动归因窗均完整，站立平均帧间隔 35.98 ms、渲染线程 CPU 23.34 ms、GPU 世界通道 34.56 ms，三者不能相加。输入内容与视角边界、采样开销和下一项建议见本轮调查；没有把缺失结构的旧包当作等内容收益基线，也未自动实施新一轮优化。

以下保留 128 的验证和人工入口，不冒充 129 新人工验收：

本体和 Extra 的构建、自动回归、发行核验及严格合规通过；新组合已完成实际效果输入／输出像素对照、资源先后顺序、F3+T、保存与正常关闭，无设备丢失。源码、DLL 及实际运行身份可回查，失败历史未覆盖。原全部 GPU 矩阵中未受影响的结果按范围复用，不将本轮称为所有 GPU／视觉条件通过。

用户保存点位的 32 视距、1080p 窗口，目标 LOD 组织段由慢窗约 24.26 ms 降至复核首窗约 1.86 ms；实例约 10246，没有减内容。首次站立帧间隔 p50 由 38.36 ms 至 13.44 ms，但原窗口仍在加载空远景节点，不能据此给整体提升百分比。“下蹲本身”触发异常的因果未确认；新的五个姿态窗口未见翻倍，末窗仍有波动，需用户补验。

新的[人工入口](../../validations/conformance-takeover/validation.md#128-号配对收尾验收)为 `closeout-manual`：2560×1440、音量 50%、音乐关闭、RecRivenVI，原模组组合和世界副本，不装探针，不自动启动。原 `client` 与旧人工入口保持不变。两份新 JAR 均冻结，后续构建不得覆盖。

以下是 127 保留的范围证据，不冒充 128 新验收：

本体和 Extra 的构建、自动回归、发行结构与严格合规检查通过，实际 Native INSTALL、非 GPU／GPU 合同通过。纯本体、Extra 无适配目标和实际模组组合均正常运行、重载、保存关闭；最后组合与当前候选匹配。实际来源计数包括四个 Sable 子世界、40 个 Flywheel 实例及 Voxy 节点发布，其他适配仅按真实记录描述，未遇到不算通过。

最初探针注入旧入口失败，退出时另有 Reflex DLL 异常；证据保留。修正真实探针目标后才追加验证，没有发生设备丢失；没有将无 GPU 转储解释为根因已明。

[人工启动与清单](../../validations/conformance-takeover/validation.md#整体候选人工体验)使用新建项目内组合场景、2560×1440、音量 50%、音乐关闭及 RecRivenVI，无探针，不操作 Prism 或旧人工实例。入口准备后不启动。需要观察原版／第三方动态外观、GUI、效果、离开重进及重载；自动输入对照不代替这次验收。

## 已保留的成果与证据

- 原版接管、模型实例化的历史使用者验收与源 renderer 对照保留在[原版收尾](../research/investigation-vanilla_drawing_closeout.md)及[实例化实验](../research/experiment-takeover_instancing.md)。覆盖表继续区分直接、捕获和未闭合，不能声称所有回调都已绕过。
- GUI 工程已按使用者决定收口，实际生成帧与全屏模糊启停见[GUI 实验](../research/experiment-gui_fg_policy.md)；人工动态视觉仍未验收，不擅自恢复严格 FG 后 GUI 或模糊背景继续补帧。
- [增量工作实验](../research/experiment-takeover_incremental_work.md)保留缺席来源查询的收益及被撤回的无稳定整帧收益原型，不把这些数字挪给本轮架构。
- [区块完整流水线](../research/experiment-chunk_pipeline.md)的 worker3 是重组前比较基线；加载和移动改善，同时严格平静更晚、资源峰值更高。本轮保留其正式路径，不将迁移算成新的性能收益。

## 仍然存在的限制

1. 每视图实例选择、相机派生数据、TLAS 和部分 GPU 表组织仍存在；没有完整 GPU 场景数据库。新增驻留、空间和更宽的通用输入有资源成本；本轮对照只覆盖冻结城市和参考功能夹具，不证明所有场景收益。
2. 原 renderer 捕获、未知修改者退回和有限程序翻译仍是正式能力。Extra 仅迁入已有适配，不宣称任意第三方程序、全部 GUI 或所有版本均支持。
3. 128 只复核用户保存的 32 视距验收夹具；它不是城市长期测试。跨维度、长期稳定、部分第三方复杂界面、完整 FG 视觉及模块替换代际未单独运行触发。
4. GPU 设备丢失与历史异常窗口根因未知。自然发生就保全并停止，不自动重试、不修改 TDR；NVIDIA 二进制公开许可也仍未闭合。
5. 云、介质、折射／发光账本、低高度天空修复验收、End Sea 遮罩位移、浮空水晶及优先级重复成像等事项见[路线图](roadmap-radiance.md)，按各自授权边界推进。

## 下一步与边界

当前配套包含 Veil 适配、Voxy 材质／水面／运动历史与本轮来源效率收敛，等待人工体验。Voxy 高压城市效率仍未通过完整 A/B 验收，GPU 世界 PT 和接近整卡显存上限的问题仍需处理；Create 小型预览及粒子的大规模整理没有展开。GUI、设置重写和其他专项保持原有边界。若后续获准提交，本体与 Extra 两个独立仓库分别处理，不能只提交本体而遗失 Extra 源码。

唯一证据和输入保留，复制客户端在结论记录后回收，重复打包输出按可再生缓存收敛；不碰旧用户人工实例、生产世界、应用管理引用、reflog 或旧 MCVR。外部占用的既有输入／SDK 例外维持，不新增外部大包。
