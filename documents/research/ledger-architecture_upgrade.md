# 渲染架构升级进度账本

2026-11-12

## 问题

两张冻结地图在移动和转头时仍超过 16.67 ms。

## 方法

按实测上界、适用场景和验证成本选下一项。沿用 Fable 的锚点候选包、相邻窗口、RR 对照量和图像验收。画质候选分别验证稳定画面、运动和能量，最后才决定默认值。

## 发现

### 卡片状态表

| 卡 | 状态 | 关键数字、开关与证据 |
| --- | --- | --- |
| M0 | 已关闭 | 测量完成，首命中 1.4～2.02 ms。无产品开关。 |
| S1 | 已落地默认开启 | 刚体准备与元数据 4.24／4.65 → 1.51 ms，上传 8.67 → 0.545 MB。九场景通过，`RADIANCE_SCENE_STORE=0\|1\|verify`。 |
| GS | 已关闭 | 两种等价候选无净收益，延后位置读取仍默认关闭，`RADIANCE_GS_DEFER_SHADOW_POSITIONS=1`。 |
| G1 | 已关闭 | 快速追踪档收益在噪声内。 |
| G2 | 已关闭 | 主光线强制不透明上界约 0.8 ms，微映射无净收益。 |
| S2 | 已实现默认关闭 | `RADIANCE_S2_MEDIUM_DIRECTORY=0\|1\|verify`。九场景通过，移动 1.467／1.201／1.446 ms，静止初测 1.321／0.898 ms。未达门槛。 |
| J1 | 已落地默认开启（CPU-1 补齐 Brushable 与热路径降耗） | 同帧接手和九场景已验证。城市 CPU 27.43 → 23.97 ms，显式选择约 13,100 → 1,547；`CPU-1` 补齐 `Brushable` 未拂拭休眠键、`BlockEntityPublications` 阻断计数按 `Owner` 缓存、`StorageVertexConsumerProvider`／`PBRVertexConsumer` 消除空绘制 `ByteBufferBuilder` 原生分配、`BoundPartCapture` 旗帜同层状态缓存及 `WorldMaterialsProbe` 关服前排空 `ChunkGenerationTask` 防活锁。`RADIANCE_J1_BLOCK_ENTITY_REST=0` 可回退。 |
| S3 | 刚体选择复用默认开启 | 城市实机约 13,100→568 行重协调，A／B／A 为 0.55／0.17／0.60 ms。静止对照图、重载及槽位主机对拍通过；GPU 与整帧漂移另计。 |
| S4 | 静态区段候选局部省约 0.15 ms | 城市约 16,845 行、Remix 约 18,723 行 CPU 变换降至零。Remix 整帧 22.15／21.53／21.30 ms，渲染线程无改善。保留默认关闭。 |
| J2 | 烘焙颜色复用与按源版本查找默认开启（CPU-1） | 城市存档有 250 个展示框、198 幅画，含 44 个地图。`CPU-1` 默认开启 `RADIANCE_J2_REUSE_UNTINTED_REQUEST`、对无染色面物品自动复用 `Source.untinted`，并将 `BakedModelTakeover.submit` 的全局 `VERSIONS` 线性扫描改为 `Source.versions` 按源 `O(1)` 查找。 |
| P1 | 由二期帧线程接续 | 默认三槽帧线程，`RADIANCE_NATIVE_FRAME_WORKER=0` 切回两槽串行。数据和剩余等待见下文。 |
| GA | 精确同路复用未获依据 | 玻璃转头 135,430 个引导抽样像素，100,401 个成像选择主导支路，精确同方向 0 个。保留独立引导。 |
| 校准 | 进行中 | 重放增加种子偏移与固定相机 64 帧累积，转换和分组已过主机检查。 |
| A、E、C | 已落地 | 引导前移、SHARC 重建后天空输入初始化、刚体稳定槽位分别已落地。C 剩余选择、相机变换与来源常驻分别见 S3、S4、J1、J2。 |
| B、G | 已部分完成 | 三项原优化生效。引导终点省上一帧法线在玻璃对照中画面接近逐像素一致，计时漂移，未并入。共享命中和 Advanced 仍待做。 |
| G3 | 进行中（`#3` 初测无损快 0.94～1.24 ms，待补三场景 ABBA） | Fable 已缩短多处跨追踪存活期。`GPU-3` 在 Remix 首轮重放中，`#3`（`prepareLayeredWorldLight`／`prepareLodLight` 延迟至 `default.rchit` 末尾并移除死尾 `sampleSurfaceState`）SPIR-V 减 `1,024 B`、画面 `mae=0.0000`（`gt16_px=0`、最大单像素差 `1.0`）、`pt-pass/world` 由 `17.794 / 18.096 ms` 降至 `16.859 ms`（`-0.935～-1.237 ms`）；`#2` 删参内联反致 `g23` 退化至 `20.111 ms`；`#5` 有损压缩（八面体 `geometricNormal` + RGB9E5）出现 `143` 个 `>16` 像素差且 SPIR-V 增 `4.4 KiB`（`18.707 ms`），暂存图路线关闭。 |
| G4 | 已关闭（三场景确认无收益） | 三场景（城市 `r32`／Remix／玻璃）深度 2 与深度 3 无论 SHARC 开关均逐字节完全一致（`0 / 3,686,400` 像素差），安静负载下 `pt-pass/world` 对称中位数差在城市 `-0.023 ms`、Remix `+0.011 ms`（均值 `+0.012 / +0.059 ms`），确认深度 2 静态上限不减寄存器／栈开销，保留深度 3。 |
| G5 | 低能量窄版已落地 | Remix 实机省 0.87 ms 主队列。移动玻璃六帧抽查保留边缘、颜色及运动表现。全面提前轮盘和缩短引导不采用。 |
| SER | 按用户决定暂停，默认关闭 | 同一实际模块只将不可达的重排指令换成等长 `OpNop` 即通过。补齐 Vulkan 内联查询能力和几何地址表权限后，原始模块仍报相同 GPU 读页错误。保留七次设备丢失证据，具体成因未闭合。 |
| GPU-1 | 已关闭（未达 1.5 ms 门槛，不进产品） | 城市 `r32` 重放：`multi4` 世界 PT 约 `-0.12～-0.21 ms`；跨槽位几何合并 `coal4`（`14,777→1,285` BLAS、`43,355→47,094` 槽位）20–37% 负载 ABCCBA 均值 `-0.760 ms`、3–7% 负载 ACCA 均值 `-0.852 ms`（安静单窗 `-0.24～-0.44 ms`），且增占设备 VMA 约 1.33 GiB。仅保留 `scene_replay` 诊断开关，不并入产品。 |
| GPU-2 | 已关闭（上限 0.27～0.34 ms 且 97.7%–99.9% 工作组活跃） | 三场景（城市 `r32`／Remix／玻璃）`sharc-resolve-gpu` 总耗时仅 `0.340 / 0.338 / 0.274 ms`（屏障 `0.0004 ms`、单工作组 `0.0034 ms`）；第 80 帧稳态表占用为 `891,276`（`10.62%`）、`891,275`（`10.62%`）、`498,714`（`5.95%`），因空间哈希均匀分布与老化淘汰需求，`64` 槽工作组活跃率达 `99.90% / 99.90% / 97.70%`，稀疏／间接压缩得不偿失。 |

数字、截图和构件身份见[场景库实验](experiment-scene_store_upgrade.md)。

### Opus 接手续测

- Advanced 三槽发行包两轮切换，光源表记录 0、10、0、8、0，重进世界后重新发布 8 项。覆盖灯具移动、资源重载及正常保存。区段光源表与几何在同帧录制。
- 区段 BLAS 压缩在 Remix 冻结重放省约 502 MiB 设备内存，世界 PT 持平。城市实机省约 449 MiB VMA 活跃设备分配，整帧 24.43／25.03／24.81 ms，P99 51.66／61.27／54.60 ms，候选保持关闭。32 格静态区域合并持平且额外占约 86 MiB，未采用。
- 新捕获的城市＋SPBR 32 视距快照已通过原版游戏资源重载和正常退出。匹配构件本体 `11bfbd4fd43c…`，Extra `52a49fb65298…`。
- Remix 32 相邻串行／两槽／三槽／串行为 24.96／23.03／21.40／24.46 ms，模拟开始至 GPU 完成为 42.27／50.71／47.33／41.45 ms。三槽的输入节流少约 2.98 ms 模拟至 GPU 完成时间，整帧增加约 1.87 ms，默认关闭。见[帧执行实验](experiment-frame_execution.md)。
- 保留选择按实例引用只重新解析受影响的组。城市静止 493 帧、约 11,556 条保留实例和 427 组，重新解析零条。Remix 移动 483 帧、平均 1,224 条保留实例，仅重新解析 0.02 条，变更提交约 175 条。每帧放置清单仍随可见实例数增长。
- Vanilla PT 连续引导沿后续光滑镜面传播。玻璃转头的世界 PT 为 41.46／41.67／43.25 ms，两侧漂移大于候选差。现有棋盘分层采样与 RR 的单次重建继续分开评估。
- 当前本地候选为本体 `df6320ed1d18…`、Extra `52a49fb65298…`。NRD 棋盘格的信号打包与相位已修复，BLACK 实机通过，默认仍关闭。约 150 格的 SPBR 铁栅栏转头对照中，八帧的可见栏杆材质恢复金属身份和 0.251 粗糙度，铁块对照保持 0.216。原始光追图仍有条纹及细栏杆覆盖变化。两组读回的裁切范围不同，分析时按记录的尺寸对齐，未验证完整的时序走样改善。
- 本体候选通过 `spotlessApply`、`check`、`verifyRelease`，完成远处材质夹具和正常保存。D 盘剩约 30.14 GiB，接近 30 GiB 底线，停止新增游戏及重放窗口。全组同包双图对照、SER 正式交付、低延迟节奏、来源全面按变化执行及 RR 棋盘格仍有缺口。

### 当前基线

11 月 12 日实机移动，两侧基线为 G 开、J1 关，取相邻窗口均值。SPBR 城市 CPU 实跑 27.43 ms、GPU 主队列 21.43 ms、整帧 27.70 ms。Remix 为 22.51、19.76、23.69 ms。J1 开启后城市为 23.97、20.86、25.50 ms，Remix 为 22.56、20.18、24.83 ms。GPU 小幅波动含 RR 通道变化，无法归给 J1。

### 门槛修订与设计变更

- 依补充一，S1 阶段门槛为 2.0 ms、上传 1.5 MB，整帧留给里程碑。采用分离的 SBT 和几何区间及稳定随机键。
- 依补充二，关闭 GS。G3 禁止用暂存图换载荷，按每减少 32 字节降 0.5 ms 和统计画面对照验收。
- 按规程 4.3，S2 复用不可变区段快照识别静态成员，生命周期巡检只处理瞬时及退役槽位。仍按现有几何地址发布，同范围更新后设备内复制未变行。主机换位对拍已通过，城市实测仍需确认前缀变长和设备复制的代价。性能门槛不变。
- Fable 的图像替代标准为同种子差 ≤ 换种子差三分之一、带符号均值绝对值 ≤ 0.01、无超过 16 级差的像素、红绿比差 ≤ 1e-4。64 帧累积用于辅助观察。

## 结论

### 当前执行顺序

二期 A6 的历史投放本体为 `0d959c3e8143…`，Extra 为 `52a49fb65298…`。城市 32／16 和 Remix 32 均值分别快 1.26／0.37／1.58 ms，Remix 32 的 P99 增加 3.60 ms。使用者反馈手感明显下降。模拟开始至 GPU 完成在城市 32 为 43.61→52.49 ms，在 Remix 32 为 40.92→48.09 ms。当前交接源码晚于该配套。见[二期帧执行实验](experiment-frame_execution.md)。

11 月 14 日按用户决定暂停 SER，已有诊断见 `D:/Workspaces/Artifacts/Radiance/ser-root-20261114/REPORT.md`。FG 冻结。后续基线本体 `468e0c9f6f78…`、Extra `52a49fb65298…`。

- 介质接触只检查等姿态稳定的邻接对。Remix 世界录制 8.62／7.70／8.69 ms，介质准备 1.60／0.71／1.66 ms。约 12,000 个已知邻接对退出逐帧巡检。GPU 空档仍主要落在世界录制，生产端的区段准入等待仍在。
- 空闲区段准入开启后通过九场景结构对拍。Remix 诊断命中 263／465 帧，生产端阻塞 4.34→3.32 ms，少量等待移至实例场景开头。正式关／开／关为 21.82／23.00／22.55 ms，P99 45.64／49.19／43.18 ms，模拟至 GPU 完成 48.01／50.16／49.55 ms。局部省等待未转成端到端收益，保持默认关闭。
- 绘画模型查找改用完整键及有界缓存，SBT 直接填入暂存缓冲。刚体清单直接封存，省去发布时的整份复制。休眠组放置页通过九场景对拍，Remix 361 帧选择 441,809 行，只重建 6 行。城市 469 帧选择 5,417,280 行，只重建 30 行。城市三处相关 CPU 阶段合计省 0.16 ms，整帧与延迟在关闭两侧范围内，保留候选默认关闭。
- Vanilla PT 修正上一帧眼点、正交光线方向、天空运动及小三角面历史法线。当前通过介质与运动 GPU 契约，两算法介质各 20 项及 NRD＋DLSS SR 的 WHITE 20 项属于前一候选。NRD 确定性透明分裂的 BLACK／WHITE 稀疏读回确认跳过半数成像、保留完整引导，匹配视角的平均亮度差在约 0.2% 内。Remix 的 WHITE 实机主队列省 0.82 ms，整帧均值持平，P99 增 1.72 ms。保留可选，默认关闭。RR 尚未证实拖影改善。
- SPBR 铁块条纹可追到贴图法线，铁栅栏还有独立覆盖走样。连续法线 mip 插值令铁块条纹更显眼。预过滤压淡横纹，但铁栅栏感知粗糙度从 0.251 升至 0.667，覆盖走样仍在，默认关闭。保留已有粗糙度、覆盖加权 mip 和视差配置。
- BLAS 完成队列与压缩记录使用独立锁。配套 `f650eb200629…`／`52a49fb65298…` 开启压缩通过九场景结构对拍。城市复测平均省约 446 MiB VMA 活跃设备分配，主队列多约 0.91 ms，压缩仍默认关闭。现有动态负载的构建耗时不足以支持 PTLAS 或 CLAS 替换。

11 月 14 日配套 `c3ec10ccb6ca…`／`52a49fb65298…` 移动与转头，城市 CPU／GPU／整帧为 14.27／23.19／24.83 ms，Remix 为 14.97／20.22／23.05 ms。尚无稳定的整包提速或延迟改善。帧时间和候选身份见[帧执行实验](experiment-frame_execution.md)，介质、来源、材质及加速结构结果见[场景库实验](experiment-scene_store_upgrade.md)。

续做构件为本体 `ff92cc33bca0…`、Extra `52a49fb65298…`。同配套实机整帧 A／B／A，城市 23.23／23.64／23.14 ms，Remix 28.20／21.55／22.62 ms。城市 GPU 主队列两次复现多 1.25／0.77 ms，分项世界 PT 多 0.41 ms，Remix 世界 PT 多 0.56 ms，RR 持平。同核心实机隔离世界 PT 为 15.99／14.34／15.30 ms，B 使用含旧引导错误的算法包，第三窗待可用提交恢复后完成。冻结重放未复现同幅差额。引导命中缩减历史法线计算的变体城市小幅波动，玻璃无净收益，城市图有变化，未采用。见[帧执行实验](experiment-frame_execution.md)。

17 时续测定位计数缺行来自旧计时脚本导入时清除开关。固定玻璃诊断确认延后读取减少 5.1% 的历史三角形采样读取。转头计时 42.45／42.54／42.33 ms、原始画面一致，无可辨认收益，保留实验包。产品修复材质半精度边界和上一帧槽的法线标志残留，沿用真实介质与天空引导。

Remix 的 Create 注册失败材料原样保留。一次定向诊断及三次无诊断启动均成功，诊断窗 1,779 次入队都有登记项，旧失败缺遗留键。旧折射夹具有位置被覆盖的问题，修正后同产品包的 RR 与 NRD 双层玻璃转头各 8 帧公共横向运动约 1.64～1.67 像素，无目标消失。SPBR 栅栏使用资源包自己的亚像素立柱，强制两张底图 alpha 不透明后原始命中仍同量级变化。RR 拖影和 SPBR 走样未关闭，下一步使用更快位移／反射物与内部覆盖参考隔离历史和采样。

双镜八帧对齐后，RR 输出与原始颜色中心的最大差为 2.33 输出像素。旧 6.7 来自不同数据和估计方式。双层玻璃解遮时输出与引导同帧恢复。DLAA 栅栏参考的内部像素约 2.98 倍，原始命中数变异系数从 6.88% 降至 3.91%，仍有细杆覆盖波动。保留现有采样及画面默认值，两项均无全场景画质改善结论。见[场景库实验](experiment-scene_store_upgrade.md)。

### 11 月 15 日交接断点

本次保存的源码含 Vanilla PT 历史法线有效性与半精度边界修复、相关重建契约和前序成果。仓外 `run.py` 保留计数开关修复。11 月 14 日的独立重建 GPU 契约首轮未过，边界用值修订后尚未复测。同日 RR 运动夹具完成四段各八帧，使用独立的中间包。后续通过 `check`、`verifyRelease` 的中间包 `a5a7ee00dc1e…` 已留在 Target 的 `build/candidates/non-ser-continuation-20261114/history-normal-checked-a5a7ee00dc1e.jar`，包含随后撤回的诊断读回改动。该包的结果不记入新构件。原始日志、读回及故障标记继续保留。

11 月 15 日以当前产品源码完成增量构建、`spotlessApply`、`check` 和 `verifyRelease`。发行包位于 `build/release/0.1.5-alpha/radiance-1.21.1-neoforge-0.1.5-alpha.jar`，SHA-256 为 `c91095fa530593fe62022bf8b13a1fdbbfe71eab07942acbc204629747884fee`。构建日志在下述仓外入口的 `handoff-build-20261115.log`。本轮验证只到 Native 主机测试、Java／探针主机测试及发行包校验，未启动 GPU 契约、客户端或重放，也未投放 Prism。

### 11 月 15 日 Hydrogen 接续与存储收敛

工作树已按 `#112` 测试复核提交本地 checkpoint `d72d8190182b9b54fb93f5f6e52d717f6ec023b7`（`GPU-112-checkpoint-20261115.patch`）。随后完成 Tier 1–3 合规存储收敛，全部清理移入 Windows 回收站并生成 `D:\Workspaces\Artifacts\Radiance\storage-cleanup-20261115\{tier1_2_manifest.json,tier3_manifest.json,tier3_archives_index.json}`（Tier 3 共 24,600 条回收记录、288 份通过 SHA-256 读回校验的 Deflate 归档与 stripped 媒体包，3 处完全同哈希的规范快照 `runtime/dlss` 转为相对目录符号链接）。

实测 `storage_preflight(8, 20, 8)` 通过：`workspace = 19,934,948,418 B`（`18.566 GiB`，软上限 20 GiB）、`validations = 8,189,161,577 B`（`7.627 GiB`，上限 8 GiB）、`external = 5,350,018,708 B`（`4.983 GiB`，软上限 6 GiB）；`.\gradlew.bat verifyCompliance --strict --offline` 失败 0、警告 0（2372 个文件）。后续按 GPU/CPU 交替队列先推进 `GPU-1`（City `r32` 4×4×4 静态区域 BLAS 合并上界评估），SER 保持暂停、FG 保持冻结。

### GPU-1：4×4×4 静态区域 BLAS 合并上界评估（二期 G1／G2）

在 `components/native_renderer/src/core/diagnostics/scene_replay.cpp`（仅 `-Pnative.sceneReplay=true` 构建 `scene_replay_core.dll`，不进入正常 `core.dll`）实现两档仅重放开关：`RADIANCE_REPLAY_MERGE_STATIC_REGION=4`（仅打包 4×4×4 区域多几何 BLAS，简称 `multi4`）与叠加 `RADIANCE_REPLAY_COALESCE_GEOM=1`（按精确 `(hitGroup, geomFlags, InstanceAppearance 464 B, medium::Filter 24 B)` 在无 `primitives`／`neighbors`／`surfaceLayerAddress` 条件下跨区块拼合顶点、索引与 `MaterialDictionary`，简称 `coal4`）。使用从本地候选包 `c91095fa...` 提取的 `vanilla-pt.zip`，在城市 32 视距冻结快照（`execution152/region32`）完成等价与计时对照：

- **场景规模与资源开销**：`14,577` 个静态区块合并为 `1,085` 个 4×4×4 区域，`BLAS` 由 `14,777` 降至 `1,285`，TLAS 实例由 `16,626` 降至 `3,134`。`multi4` 将几何槽位由 `43,355` 增至 `78,778`（命中组 `65,842 → 102,350`，计时窗设备端 VMA 分配由 `5.086 GiB` 增至 `5.202 GiB`，约 `+0.116 GiB`）；`coal4` 将几何槽位压至 `47,094`（区块几何由 `35,423` 槽压为 `3,739` 合并几何，命中组 `70,666`），但重放并合缓冲使计时窗设备端 VMA 分配由 `5.086 GiB` 增至 `6.416 GiB`（约 `+1.330 GiB`，等价值亦为 `+1.329 GiB`），场景准备由约 `3.1 s` 升至 `8.6 s`。
- **等价性（SHARC 关，第 15 帧 `2560×1440` `RGBA16F`）**：两档均 0 非有限像素、天空与主体结构视觉一致（对照图位于 `D:\Workspaces\Artifacts\Radiance\architecture-upgrade\gpu1-static-region\`）；`coverage_sampling.glsl` 在未设 `sample_keys` 时按物理几何索引（`oldSlot` 对新槽位）散列产生预期随机噪点：`multi4` `mean_rgb 0.358859 → 0.358895`（`MAE 0.001666`），`coal4` `mean_rgb 0.358859 → 0.358902`（`MAE 0.001667`）。基线同配置连续两次 `timed_default` 逐字节完全一致（`0 / 3,686,400` 像素差异）。
- **性能上界与主机负载区分（`route="turn"`，30 预热 + 90 计时帧）**：
  - `run-20261115-193321`（桌面 GPU 基线 `1%–14%`，仅末尾 `A1_base` 出现 `34%` 尖峰，`ABBA`）：扣除尖峰 `A1` 后，`multi4` 相对 `A0_base`（`15.316 ms`）`pt-pass/world` 中位数仅差 `-0.11～-0.13 ms`。
  - `run-20261115-194516`（桌面持续 `20%–37%` GPU 负载下 `6` 窗对称 `ABCCBA`）：全窗因后台负载抬升约 `1.8 ms`（`A0/A1` 基线 `17.107 / 17.058 ms`），对称中位数差 `multi4` 为 `pt-pass/world -0.211 ms`／`main-queue-frame -0.077 ms`，`coal4` 为 `pt-pass/world -0.760 ms`／`main-queue-frame -0.691 ms`（单窗 `C0` 低至 `15.754 ms`，紧随 `C1` 为 `16.886 ms`）。
  - `run-20261115-195342`（桌面主要处于 `3%–7%` 低负载时的 `4` 窗对称 `ACCA`）：低负载窗 `A0_base` `15.251 ms`、`C0_coal4` `15.014 ms`、`C1_coal4` `14.816 ms`（安静单窗净差 `-0.24～-0.44 ms`）；因末尾 `A1_base` 遇到桌面微升至 `13%`（`16.282 ms`），对称中位数差为 `pt-pass/world -0.852 ms`／`main-queue-frame -0.918 ms`（均值 `-0.794 / -0.981 ms`）。
- **结论与清理**：不论按低负载单窗净差（`-0.24～-0.44 ms`）还是含尾部微扰的对称统计（`-0.76～-0.85 ms`），即便做到激进的跨区块顶点／材质字典合并（`coal4`），其最大上界仍明显低于二期规划设定的 `>= 1.5 ms` 产品门槛，而仅多几何合并的 `multi4` 更只有 `-0.1～-0.2 ms`。按规划关闭产品静态区域 BLAS 合并（`G1` 完成、`G2` 不立项），仅保留重放诊断路径供复验。重放原始 `.bin`（`618.75 MiB`）及冗余计时 `.png`（`113.11 MiB`）已按规范移入回收站（`D:\Workspaces\Artifacts\Radiance\storage-cleanup-20261115\gpu1_bins_manifest.json`），复核 `storage_preflight(8, 20, 8)` 通过（`workspace 18.650 GiB`、`validations 7.724 GiB`、`external 5.060 GiB`）。

### GPU-2：SHARC Resolve 稀疏／间接派发上界评估与 GPU-3 #4（递归深度 2）复核

在 `components/native_renderer/src/core/render/modules/world/ray_tracing/ray_tracing_module.cpp`（严格置于 `#ifdef MCVR_BUILD_SCENE_REPLAY` 下，不影响正常 `core.dll`）增加 `RADIANCE_REPLAY_SHARC_STATS_FRAME`（读回哈希表、累积与已解析缓冲，统计槽位占用与 64 槽工作组活跃率）与 `RADIANCE_REPLAY_SHARC_RESOLVE_GROUPS`（覆盖 `sharc-resolve-gpu` 派发工作组数以分离屏障与内核耗时），同时联合评估 `RADIANCE_RT_RECURSION_DEPTH=2`（`GPU-3 #4`／`G4`）。在城市 `r32`（`execution152/region32`）、Remix（`execution152/remix-optics-snapshot`）和玻璃（`snapshot`）三套快照完成第 80 帧稳态统计与 `6` 窗对称 `ABCCBA`（`route="turn"`，30 预热 + 90 计时帧，`run-20261115-212249` 与 `run-20261115-213628`）：

- **SHARC 哈希表稳态占用与工作组活跃率（`capacity = 8,388,608`，共 `131,072` 个 64 槽工作组，第 80 帧）**：
  - **城市 `r32`**：`occupiedSlots = 891,276`（`10.6248%`），其中当帧新写入 `freshUpdated = 46,991`、需由 `sharc-resolve-gpu` 逐帧推进帧计数与老化淘汰的 `staleAging = 844,285`；由于空间哈希在 `2^23` 表上均匀分布，含至少 1 个活跃槽位的 64 槽工作组达 **`130,944 / 131,072`（`99.9023%`）**。
  - **Remix**：`occupiedSlots = 891,275`（`10.6248%`），`freshUpdated = 46,991`、`staleAging = 844,284`，活跃工作组 **`130,944 / 131,072`（`99.9023%`）**。
  - **玻璃**：`occupiedSlots = 498,714`（`5.9451%`），`freshUpdated = 47,844`、`staleAging = 450,870`，活跃工作组 **`128,062 / 131,072`（`97.7036%`）**。
- **`sharc-resolve-gpu` 屏障与内核精确拆分**：
  - 仅执行 `RT -> Compute` 内存屏障（`0` 工作组，`run-20261115-212249`）：三场景中位数均为 **`0.0004 ms`（`0.4 µs`）**。
  - 屏障 + `1` 个工作组（`B_resolve1`，`run-20261115-213628`）：城市 `0.0034 ms`、Remix `0.0034 ms`、玻璃 `0.0033 ms`。
  - 完整 `131,072` 工作组（`A_base` 对称中位数／均值）：城市 **`0.3400 / 0.3488 ms`**（纯内核差 `0.3365 / 0.3455 ms`）、Remix **`0.3375 / 0.3469 ms`**（纯内核差 `0.3341 / 0.3436 ms`）、玻璃 **`0.2744 / 0.2814 ms`**（纯内核差 `0.2711 / 0.2781 ms`）。若不完整执行 resolve（`B_resolve1`），未解析缓存导致 `pt-pass/world` 在城市、Remix、玻璃分别劣化 `+3.407 / +3.306 / +13.315 ms`。
- **`GPU-2` 结论**：`sharc-resolve-gpu` 在三场景总耗时仅 `0.274～0.340 ms`；且因老化淘汰要求每帧扫描全部 `49.9 万～89.1 万` 个已占用槽位，`97.70%～99.90%` 的 64 槽工作组均非空，工作组级裁剪几乎无效；若改用槽位级活跃列表维护，则在 `sharc-update-gpu` 原子追加、`sharc-resolve-gpu` 淘汰压缩、间接参数写出与额外屏障及非连续随机访存上的代价高于现有 64 线程连续合并读 `hashKey == SHARC_EMPTY_ENTRY` 立即早退的 `0.27～0.34 ms` 总成本。按规程 4.2 关闭 `GPU-2`。
- **`GPU-3 #4`（`RADIANCE_RT_RECURSION_DEPTH=2`）三场景结论**：
  - **等价性**：无论在 SHARC 关（`equiv_nosharc` 第 15 帧与末帧）还是三场景 SHARC 开（`timed_default` 末帧），`depth=2` 与 `depth=3` 输出均为 **100% 逐字节完全一致**（`byte_exact: true`，`0 / 3,686,400` 像素差异，`max_abs_rgba_diff = 0.0`）。
  - **性能**：在桌面安静负载下（城市 GPU 利用率 `4%–8%`、Remix `0%–9%`），`C_depth2` 相对 `A_base` 的 `pt-pass/world` 对称中位数差在城市为 **`-0.0234 ms`**（`15.1897 → 15.1663 ms`，均值 `+0.0115 ms`），在 Remix 为 **`+0.0105 ms`**（`14.6522 → 14.6626 ms`，均值 `+0.0587 ms`），`main-queue-frame` 差为 `-0.0418 ms` 与 `-0.0145 ms`（玻璃安静单窗 `A0 38.275 ms` 对 `C0 38.769 ms`，末窗 `A1` 受桌面 `26%` 负载影响不计入净收益）。因 `vanilla-pt` 第 2 层仅发射 `SkipClosestHit` 阴影光线（只进 `shadow.rmiss`／`shadow.rahit`），驱动静态调用图栈分配在 `depth=2` 与 `depth=3` 下无实质差异，关闭 `#4`（`G4`）。
  - 本轮重放生成的 `.bin` 与冗余 `.png` 及被替代的 `run-20261115-212249` 验证目录已移入回收站（`D:\Workspaces\Artifacts\Radiance\storage-cleanup-20261115\gpu2_gpu3_bins_manifest.json`），`storage_preflight(8, 20, 8)` 通过（`workspace 18.677 GiB`、`validations 7.755 GiB`、`external 5.092 GiB`）。

### CPU-1：高频未入眠来源热路径收敛与关服活锁修复（J1／J2 续）

针对城市与 Remix 实机分项中占主导的未入眠／逐帧重访来源（城市 `Banner` `245` 个 `1.164 ms`、`ItemFrame` `250` 个 `0.798 ms`、`Painting` `198` 个 `0.221 ms`，Remix `Brushable` `105` 个）及 1.21.1 原版 `ChunkMap.processUnloads` 关服活锁完成收敛与修复：

- **`Brushable`（可疑沙子／砾石）入眠支持（`VanillaBlockDefinitionMixins.Brushable`）**：补齐 `radiance$restable = true` 与 `radiance$stableInputs`（当 `dusted <= 0 || direction == null || item.isEmpty()` 时返回稳定键 `Boolean.FALSE`，拂拭中返回 `null`），使 Remix 中 `105` 个未拂拭的 `BrushableBlockEntity` 通过 `BlockEntityPublications.selectStable` 进入空绘制休眠，不再每帧分配 `StorageVertexConsumerProvider` 和空 `Snapshot` 并阻止所在区段休眠。
- **`BlockEntityPublications` 与 `FontAtlasLifecycle` 热路径降耗**：在 `BlockEntityPublications.Owner` 上按实例缓存 `producedBlocker` 与 `notRestableBlocker` 计数槽位，消除未休眠方块实体（如 `245` 个 `Banner`、`21` 个 `Spawner`）每帧 ~266 次字符串拼接与 `ConcurrentSkipListMap` 查找；`Snapshot.close()` 在 `fontPages` 为空时跳过 `List.copyOf`；`FontAtlasLifecycle.capture` 在 `registeredPages.isEmpty()` 时立即早退。
- **`StorageVertexConsumerProvider`、`StorageRoutingBufferSource` 与 `PBRVertexConsumer` 原生 `ByteBufferBuilder` 零分配**：字节码核实 `MultiBufferSource.BufferSource` 在子类重写 `getBuffer` 与 `endBatch` 时从不访问 `sharedBuffer` 与 `fixedBuffers`，将两处 `super(new ByteBufferBuilder(0), new Object2ObjectLinkedOpenHashMap<>(0))` 改为 `super(null, Object2ObjectSortedMaps.emptyMap())`；同时将标准 `StorageVertexConsumerProvider` 创建的 `PBRVertexConsumer` 改为在首次 `beginVertex` 实际写入顶点时才延迟调用 `allocateBuffer()`，彻底消除被刚体／部件模型接管的方块实体与实体每帧约 3,500+ 次无用 `jemalloc` 原生内存分配与释放。
- **`Banner` 与 `BoundPartCapture` 同层绑定复用**：`VanillaBlockReplacement.add` 与 `BoundPartCapture.add` 对空部件列表（如墙上旗帜隐藏的 `pole`）立即早退；`VanillaBannerRequests.layer` 复用已解析的 `RenderType`；`BoundPartCapture` 增加同 `(layer, leaf, glint, glintTexture, context)` 的 `LayerInput`／`DrawStateSnapshot` 槽位缓存，使旗帜 `1 + N` 层图案共享同一 `Sheets.BANNER_SHEET` 状态而不重复查询纹理 owner、几何类型编码与材质颜色快照，并将 `submit()` 中两处 `.stream().allMatch(...)` 改为索引循环。
- **`BakedModelTakeover`（`J2` 展示框与烘焙模型）**：默认开启 `RADIANCE_J2_REUSE_UNTINTED_REQUEST`（`!"0".equals(...)`），在 `Source` 上预计算 `hasTintedQuads` 并常驻 `untinted` 列表，使无染色面物品与方块直接复用不可变 `untinted` 列表而免除每帧 `ArrayList` + `List.copyOf`；同时在 `Source` 上维护按源 `versions` 列表，将 `submit()` 的版本匹配由遍历全局 `VERSIONS`（至多 512 项）降为按源 `O(1)` 查找。
- **1.21.1 关服活锁修复（`WorldMaterialsProbe.serverQuiescent`）**：字节码定位确认 `MinecraftServer.stopServer()` 在 `removeTicketsOnClosing()` 后立即以 `() -> true` 调用 `ChunkMap.processUnloads`，若此时仍有跨维度／边界 `ChunkGenerationTask` 持有邻居区块的 `generationRefCount > 0`（`!isReadyForSaving()`）且 `saveSync.isDone()` 为真，`scheduleUnload` 会在主线程原地重入 `unloadQueue` 形成无限空转，永远无法回到 `waitUntilNextTick()` 消费生成完成回调。在 `WorldMaterialsProbe.finish(mc)` 调用 `mc.stop()` 前增加非阻塞 `serverQuiescent(mc)` 轮询：在服务端正常 tick（带 50 ms 预算与任务泵）期间先行调用 `removeTicketsOnClosing()`、`DistanceManager.runAllUpdates`、对残留 `ChunkGenerationTask` 标记 `markForCancellation()` 并排空 `runGenerationTasks()`，待所有维度 `generationRefCount == 0` 后再执行 `mc.stop()`（设 10 秒安全上限），从根源消除关服活锁。

### GPU-3：路径追踪状态瘦身初测与 11 月 16 日 Hydrogen 停工交接断点

针对 `Radiance-G §8` 剩余子项 `#2`（`traceReconstructionSurface` 与 `world.rgen` 引导调用点瘦身）、`#3`（`default.rchit` 局部光源嵌套追踪延迟与死视差尾移除）、`#5`（`MainRay` 载荷瘦身）构建离线验证包并在 Remix 快照完成首轮 64 样本对照（结果位于 `validations/performance-scene_replay/result/gpu3-20261115/remix-W0-g2-g3-g23-g5-nosharc/`，主机快照 `AvailCommit 84.61→83.51 GiB`、`GPU 2%→29%`，临时 `.bin` 已移入回收站并登记于 `D:\Workspaces\Artifacts\Radiance\non-ser-continuation-20261114\hydrogen-gpu3-recycle-20261116.json`）：

- **`#3`（`g3`：`prepareLayeredWorldLight` + `prepareLodLight` 延迟至 `default.rchit` 末尾并删除 `localBounce < 1` 末尾不可达 `sampleSurfaceState`）**：`default.rchit-0.spv` 由 `530,772 B` 降至 `529,748 B`（`-1,024 B`）；与 `W0-s1-0` 对照 **100% 无损等价**（单帧 `mae = 0.0000`、`p99 = 0.0003`、`max_px = 1.0`、`gt16_px = 0`、`rg = 0.0`；64 帧均值 `mae = 0.0016`、`gt16_px = 0`、`max_px = 1.0`），且 Remix `pt-pass/world` 由 `17.794 ms`（`W0-s1-0`）／`18.096 ms`（`W0-s2-5`）降至 **`16.859 ms`（`-0.935～-1.237 ms`）**，`main-queue-frame` 由 `22.812 / 23.971 ms` 降至 **`21.968 ms`**。同时由于 `#3` 已清空 `default.rchit` 内部在 `line 1191` 之前的全部嵌套 `traceRayEXT`，`#6`（将末尾 `PendingSunLight` 阴影光线再上提至 `world.rgen`）需额外跨阶段回传 40 B 参数而不再缩小 `default.rchit` 栈帧。
- **`#2`（`g2`：删去 `traceReconstructionSurface` 值参数并直接读写全局 `mainRay`）**：破坏了 `world.rgen` 编译器对引导调用的活跃区间切分，`g23` 的 `pt-pass/world` 反退化至 `20.111 ms`（较 `g3` 慢 `+3.252 ms`），且产生 `130` 个 `> 16/255` 差异像素，排除激进删参写法。
- **`#5`（`g5`：`MainRay` `-36 B` 压缩，含移除 `lastScatterOrigin` `-12 B` + 八面体 `geometricNormal` `-8 B` + RGB9E5 `directLightRadiance`／`surfaceThroughput` `-16 B`）**：八面体与 RGB9E5 打包解包指令使 `default.rchit` 与 `world.rgen` SPIR-V 分别膨胀 `+4,400 B` 与 `+4,776 B`，`pt-pass/world` 为 `18.707 ms`（慢于 `g3` 的 `16.859 ms`），且法线／吞吐量化在电介质分支边界造成 `143` 个像素差 `> 16/255`，违反补充二硬门槛；后续仅保留无损移除 `lastScatterOrigin`（`-12 B`）作为 `#3` 的附加子项复验。
- **Hydrogen 本阶段已提交成果与补丁清单（`develop` 本地提交，未 `git push`）**：
  - `d72d8190182b9b54fb93f5f6e52d717f6ec023b7`：`#112` 重建材质 16 位半精度贴图端到端契约与弹簧材质 GPU 契约 checkpoint（`GPU-112-checkpoint-20261115.patch`）。
  - `db55fd279e79aa6c569828bb7e38ffbd08b225d3`：`GPU-1` 4×4×4 静态区域 BLAS 合并评估与关闭（`GPU1-static-region-blas-20261115.patch`）。
  - `a51fb0dd3a86b385a3193259373f5b4a79dd9429`：`GPU-2` `sharc-resolve-gpu` 审计与 `GPU-3 #4` 递归深度 2 三场景评估关闭（`GPU2-sharc-resolve-audit-20261115.patch`）。
  - `17f3313cf45905a15e7fb06b95c4842f83cf9800`：`CPU-1` `J1`/`J2` 高频未入眠来源热路径优化、`Brushable` 入眠与 1.21.1 关服活锁修复（`CPU1-j1-sources-and-shutdown-20261115.patch`）。
- **接续建议（供 Sol 或下一会话直接恢复）**：
  1. 直接将已验证无损提速的 `GPU-3 #3`（以及可选的无损 `-12 B` `lastScatterOrigin` 移除）落入工作树（同步 `vanilla-pt` 与 `advanced` 的 `default.rchit`、`util/layered_light.glsl`、`util/lod_light.glsl`、`advanced/common/direct_light_eval.glsl`），跑三场景 `ABBA` 正式计时与 Native/Shader 契约测试后提交 `GPU-3`。
  2. 推进 `CPU-2`（JNI 批量提交／按变化增量同步与 `S2`／`S4` 实机复核）、`CPU-3`（帧线程 `sim-to-GPU` 输入延迟收敛）、`V-1`（RR 拖影与远处 SPBR 铁栅栏覆盖走样），最后用最新候选包完成 GPU 契约与实机四段运动回归。

日常重建从仓库根目录运行以下命令。使用本机 Python 3.14 及其已装的诊断依赖。

```powershell
$python314 = py -3.14 -c 'import sys; print(sys.executable)'
$env:Path = (Split-Path $python314) + ';' + $env:Path
.\gradlew.bat :version:1.21.1-neoforge:distributedJar check verifyRelease --offline
```

收到运行授权后，先跑修订后的 GPU 契约，再按[实机运动回归](../../validations/conformance-takeover/validation.md)补最终包的 RR／NRD 样本。历史失败和计数结果都在 `validations/performance-scene_replay/result/non-ser-continuation-20261114/`。沿用既有保护和兜底；需要定位城市引导成本时先取短计数窗。最终同包的双图性能与延迟、按变化提交的剩余全量访问、通用运动拖影和 SPBR 覆盖走样仍待处理。SER 继续暂停，FG 冻结。

```powershell
$env:PYTHONPATH = Join-Path (Get-Location) 'components/render_diagnostics/benchmark'
$env:RADIANCE_MEMORY_RECOVERY = ''
py -3.14 'D:/Workspaces/Artifacts/Radiance/non-ser-continuation-20261114/verify.py' contracts handoff-contracts
```

仓外入口为 `D:/Workspaces/Artifacts/Radiance/non-ser-continuation-20261114/`，依赖同级的 `architecture-phase-two-20261113/capture.py`、`architecture-20261112-g/` 及现有故障恢复登记。游戏对照使用 `validations/performance-scene_replay/result/execution150/inputs/` 的城市及 Remix 地图、设置和相应的 SPBR／Remix 资源包。Extra 源码 `8fdbe21`，以历史实测配套 `52a49fb65298…` 为对照；Create、Sable、航空学按 `inputs.toml` 和本机登记解析。复用冻结输入和探针，旧对照的数字按原构件身份解释。

### 已排除的路线

- 通用 BLAS 快速追踪档，两图收益在噪声内。
- 主光线 OMM，Remix 持平，玻璃约慢 3%，设备内存增加 0.31 GiB。
- 阴影 OMM，Remix 16.382／16.387／16.446 ms，设备内存增加 18.4 MiB。
- 阴影延后读位置，Remix 16.205／16.376／16.404 ms。
- 载荷搬到暂存图，Remix 16.417／17.290／16.192 ms，设备分配增加约 0.47 GiB。
- S1 首版阴影占位行，外观上传约 6.14 → 12.25 MB。
- 先全量拼表再原地判等，移动仍需拼接并扫描原数据。
- 仅合并 JNI 批次，未消除每帧约 13,100 条重新选择，不能替代来源常驻。
- `GPU-1` 4×4×4 静态区段 BLAS 合并（城市 `r32`：`multi4` 世界 PT `-0.12～-0.21 ms`；激进 `coal4` 跨区块几何／字典合并安静窗 `-0.24～-0.44 ms`、对称中位数 `-0.76～-0.85 ms` 并增占设备 VMA 约 1.33 GiB），低于 `1.5 ms` 产品门槛，不进产品。
- `GPU-2` SHARC Resolve 稀疏／间接派发（城市 `r32`、Remix、玻璃 `sharc-resolve-gpu` 总耗时仅 `0.340 / 0.338 / 0.274 ms`，其中屏障 `0.0004 ms`；第 80 帧稳态占用 `891k / 891k / 499k` 槽位，因老化淘汰与空间哈希分布，`99.90% / 99.90% / 97.70%` 的 64 槽工作组非空），压缩与间接派发开销大于收益。
- `GPU-3 #4`（`G4`）`RADIANCE_RT_RECURSION_DEPTH=2`（三场景与深度 3 逐字节完全一致，安静负载下城市与 Remix `pt-pass/world` 对称中位数差仅 `-0.023 ms` 与 `+0.011 ms`），无净收益。
