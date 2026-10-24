# 接管层增量工作归因与来源登记

2026-10-24。接管层来源归因确认并修复了缺席 Sable 的逐帧类加载开销；独立静态实例记录缓存虽通过正确性验证，但没有稳定整帧收益，已撤回生产集成，保留实验。使用者授权范围内没有改变世界内容、渲染语义、动画频率或质量，也没有扩展 GUI、第三方或 GPU 优化。

## 问题

冻结城市已有模型和 BLAS 复用，但上一轮 CPU 分段的实体主体约 19 ms，尚不能归结为动画或必要状态计算。动态实例仍逐帧重填列表，九张元数据表仍以整表内容比较为主；这些是架构缺口，不自动证明它们解释了实体主体耗时。

## 方法

基线源码 HEAD 为 `7a311c19d81ba2b64cdaef8e96193d73f07d47bb`，开始时工作树与暂存区干净。产品沿用 `components/render_diagnostics/build/gui-policy/manifest.json`，城市沿用 `validations/performance-vanilla/result/instancing/city-fixture.zip`。1920×1080、视距和模拟距离 32、随机覆盖、FG 关闭、RR Balanced 与 SHARC 保持原样，自动实例静音。不使用 Prism、Computer-Use、旧 MCVR 或重型 GPU 工具。

新增探针仅观察 Radiance 拥有的替代器、状态提取、部件绑定、批次打包、输出登记和来源探测。普通 TIMED、PRODUCERS 与 CPU_SAMPLES 分开；阶段 self 为扣除子区间后的墙钟分区，inclusive 不相加，JFR 命中数不换算为耗时。

上一 GPU 用例的 STOP 经原始 `capture-result.json` 核对，属于 Nsight 成功导出后结束目标，没有设备丢失；旧 STOP、失败生命周期与处理理由留在原案例。当前用例采用普通启动，不自动重试设备丢失。

## 发现

### 归因器修正与有效窗口

首次脚本在启动客户端之前因临时目录 `inspect.py` 遮蔽标准库而拒绝；外部临时脚本改以 Python 的安全路径选项执行，仓库脚本正常执行，没有删除旧脚本。首次已启动用例 `incremental-census-r32-1080-20261024-085905` 在首个归因帧触发旧探针的字段假设：绑定资源提供 `mesh()`，不具有 `mesh` 字段。已保存世界，无设备丢失，但不属于正常验收或性能通过；日志、STOP 和回收前证据保留。

修正探针后，旧构建跳过参数没有实际执行 Target 编译；运行入口通过源码／类时间检查拒绝启动陈旧探针。重新正常执行 probeClasses／probeTest 后才启动新用例。此预检没有启动另一个客户端。

`incremental-census-r32-1080-20261024-090716` 正常保存、重载与关闭，共 84 帧。发现旧探针逐实例反射及扫描版本列表会显著扰动输出登记：该阶段约 16.71 ms，整个归因窗约 60.14 ms，不能作为真实产品热点或收益基线。改为每个不可变模型最多解析一次，计量缓存有界且在窗口结束释放。

`incremental-attribution-r32-1080-20261024-091623` 正常保存、重载与关闭；普通 TIMED 118 帧均值 42.39 ms，修正后的 PRODUCERS 121 帧均值 41.55 ms。两窗分开，不据这一短窗差值报告收益。普通计时的状态 self 约 1.53 ms、姿态 0.25 ms、部件绑定 1.41 ms、批次打包 1.53 ms、输出登记 0.70 ms，实体未细分主体仍约 16.30 ms。

| 实体来源 | 每帧调用 | 每帧持久实例 | 归因窗 renderer inclusive ms |
| --- | ---: | ---: | ---: |
| 盔甲架 | 369 | 1,396 | 1.81 |
| 物品框（当前捕获入口） | 293 | 589 | 1.60 |
| 鸡 | 46 | 368 | 0.41 |
| 绘画（当前捕获入口） | 223 | 223 | 0.41 |
| 矿车 | 47 | 371 | 0.39 |
| 掉落物 | 约 46.44 | 约 54.44 | 0.28 |
| 羊 | 30 | 360 | 0.27 |
| 船 | 32 | 224 | 0.25 |
| 牛 | 44 | 264 | 0.21 |
| 村民 | 10 | 90 | 0.18 |

来源时间不包含外围所有工作，不能与 Java self 直接相加；未归属动态来源、BER 和第一人称没有硬摊给上述实体。来源为 capture 不等于每帧重新生成全部几何：绘画等仍使用现有局部资源。原始表、分段及加载身份在各案例，冷 CSV／日志逐条校验后归入 raw-evidence.zip。

### 未安装来源的重复探测

纯原版组合没有 Sable，`entityRenderTransform` 仍每实体两次进入 `entitySubLevel`，尝试 Class.forName 并捕获 ClassNotFoundException。块查询与子世界枚举具有相同缺口。JFR 的 51 个渲染线程样本中有 2 个完整定位到这条链；样本短且部分栈截断，只证明实际进入，不能将比例直接解释为耗时。

第一候选只在三个解析入口按实际加载器登记提前排除未安装来源。已安装但缺类的显式失败保持；没有冻结模型、删几何、改材质、改动画或增加并行。新自动回归通过隔离类加载器验证 1,000 次实体／块／枚举调用不尝试加载缺席类，并确认已安装却缺类时仍失败。

### 固定城市对照

唯一产品／探针／夹具清单为 `components/render_diagnostics/build/takeover-incremental/manifest.json`；运行入口为 `components/render_diagnostics/benchmark/takeover_incremental.py`。每个进程恢复同一 ZIP，先 FRAME_ONLY 20 秒，再 TIMED 10 秒，记录来源探测次数、实例数、几何生产、GPU、帧耗时分布和并行干扰。四次均正常重载、保存关闭，无设备丢失。

| 进程 | FRAME_ONLY 帧间隔均值 ms | p95 ms | p99 ms | TIMED 来源探测 ms |
| --- | ---: | ---: | ---: | ---: |
| A1 原包 | 36.105 | 40.217 | 49.006 | 10.336 |
| B1 门禁修正 | 23.428 | 27.188 | 41.521 | 0.044 |
| B2 门禁修正 | 23.655 | 27.705 | 33.320 | 0.045 |
| A2 原包 | 36.211 | 39.977 | 46.167 | 10.533 |

两次进程均值再平均为 36.158 → 23.541 ms，帧耗时下降约 34.9%，对应约 27.7 → 42.5 真实 FPS。没有按帧数混合进程，不把来源阶段节省与其他阶段相加。第一配对实际类加载尝试约 2,299 次／帧 → 0；动态 BLAS 输入仍为 48 项，刚性实例约 4,120 项，主世界静态记录 28,820 项。实体模拟在各自加载期间仍可产生小幅数量差异，不能宣称每个活动实体位级相同。

完整进程名、帧窗口、实际输入身份与配对差值在 `validations/performance-vanilla/result/incremental/comparison.json`。源快照为 `validations/performance-vanilla/result/chunk-pipeline/relocated-evidence/takeover-incremental-20261024/source-optional-source-guard.{zip,json}`，逐条读回 SHA-256 已核对。产品 SHA-256：原包 `86c1f1b11d330e3f41ef4ca334bab843811991e9ce5be0b7a7043b330bd4ba04`，门禁候选 `f1375688b60f8ad69c49a70beef792b3f7126bda9c828ec44afdc63843bec556`。两者内嵌 core 完全相同，不将后续 native 改动混入本组。

GAME 没有新增／移除类，仅桥接类及三个嵌套 record 的文件字节不同；JDK 21 javap 忽略行号后的代码核对确认三个 record 完全相同，实际指令修改只有桥接类。初始 Mixin 对可选类的探测没有改变；被消除的是运行期逐实体反复失败的来源查询。安装了 Sable 的组合仍执行原链路，不能直接套用本纯原版收益。

### 静态发布实例记录原型

修正后的 B1 分段中，主世界静态区段实例生成约 1.321 ms／帧；它仍对 28,820 条已发布记录逐一计算矩阵并登记。相比重新建立动态实例协议，本轮先复用已有增量区段发布作为一个局部闭环：相同发布对象、相同相机位置及相同实例／命中组前缀时，缓存 CPU 实例记录和对应历史矩阵；变化时按原算法重算，不冻结任何内容。

受测原型每个录制上下文单独持有缓存，只有重复键才建立记录，持续移动时继续直接生成。构建器获得独立记录副本，BLAS 与发布对象保持强所有权，GPU 仍按现有完成退休；退出世界释放缓存内容及容量。相机使用精确 double 位比较，包含负零，不用近似阈值代替语义判断。以下是已完成并撤回的实验，不是当前默认能力。

自动测试核对原生成器与缓存的完整矩阵、实例索引、查询遮罩、SBT 偏移、面标志、BLAS 所有者及历史；覆盖大坐标、连续移动、静止复用、前缀变化、同槽位替换、相同字节的新发布、空场景和完成退休。nativeTest 74 项、gpuTest 23 项、GAME／probe 回归、check、verifyRelease 及严格合规均通过，合规 0 失败／0 警告。

冻结清单为 `components/render_diagnostics/build/takeover-incremental-scene/manifest.json`，发行包 SHA-256 `da1a1d3c137fe988ae5876e2c46433f381d50e7e71731ba4bd7e5fc012b76907`。`takeover-incremental-scene-verify-r32-1080-20261024-100436` 在实际城市加载及重载期间开启逐帧原路径核对，正常保存关闭，无设备丢失；功能用例没有计时窗，不拿其双执行开销作性能数据。独立 ABBA 使用同一个发行包，通过 `MCVR_STATIC_INSTANCE_CACHE=0/1` 选择路径，核对开关始终关闭。

完整 ABBA 均正常重载、保存关闭，无设备丢失：

| 进程 | FRAME_ONLY 均值 ms | p95 ms | p99 ms | TIMED 静态记录 ms |
| --- | ---: | ---: | ---: | ---: |
| A1 关闭缓存 | 24.375 | 28.459 | 31.794 | 1.310 |
| B1 开启缓存 | 23.853 | 27.483 | 31.188 | 0.975 |
| B2 开启缓存 | 25.150 | 29.614 | 34.954 | 1.207 |
| A2 关闭缓存 | 24.134 | 28.128 | 33.377 | 1.322 |

首配对约 28,263／28,820 条记录复用，第二次约 27,910 条；每个已准备录制上下文增加约 4.4 MiB CPU 记录容量，不是 GPU 上传。局部生成分别减少 0.335／0.116 ms，但仍须复制带强所有权的完整实例记录，再由 TLAS 构建器转换与上传。FRAME_ONLY 两配对分别减少 0.522 ms、增加 1.016 ms；两进程均值再平均为 24.255 → 24.502 ms，不能认定优化成功。

B2 主队列 GPU 时间也上升，没有检测到其他 Minecraft 进程，仍不能凭进程列表排除其他环境影响；不能把 B2 单独归因于缓存，也不剔除该次或追加挑选窗口。完整分布、CPU／GPU、计数及环境在 `validations/performance-vanilla/result/incremental/scene/comparison.json` 与各案例 analysis.json。站立转头可复用、移动相机位置时仍直接生成；本轮没有移动／加载期性能收益证据。

因此撤回生产中的缓存成员、调用、开关、新头文件和相应实验测试，三个原有 native 文件恢复到任务开始内容；头文件送入 Windows 回收站。完整源码和实验测试保留于原型 ZIP，冻结包与重算入口保留，便于追溯。当前产品只保留已证实的缺席来源门禁，不继续扩大这层缓存。未来增量架构需真正减少逐帧实例重组／复制及下游处理，不把已有元组再缓存一份视作闭环终点。

### 最终候选与闭合门槛

撤回原型后重新通过 spotlessApply、check、verifyRelease、verifyCompliance --strict 与 gpuTest，严格合规 0 失败／0 警告。最终包为 `components/render_diagnostics/build/takeover-incremental-final/radiance.jar`，SHA-256 `2b68c395760d0789f6af48d83002ee39327da03a685f4a3b5055b37456b8b67a`，与探针、城市输入的配套见同目录 manifest.json。原生组件源码与任务开始一致，只有 GAME 中三处来源门禁属于保留的产品逻辑变更。

`takeover-incremental-final-r32-1080-20261024-104213` 使用该包实际加载，来源资格通过，完成城市加载、F3+T、正常保存和原生关闭；功能窗没有性能采样，不将其拼入任一 ABBA。最终源码与记录快照为 `validations/performance-vanilla/result/chunk-pipeline/relocated-evidence/takeover-incremental-20261024/source-final.{zip,json}`，包含必要新文件和 14 个 gitlink。当前没有新的人工视觉验收，也没有向 Prism 投放。

为给最后一次隔离客户端留出软上限预留，将与 Target build/libs 中最终包 SHA-256 完全相同的 build/release 暂存副本送入回收站；实际构建包及冻结验收包保留，动作清单在 `validations/performance-vanilla/result/incremental/duplicate-release-cleanup.json`。客户端环境均在结论／文本证据归档后回收，没有删除唯一证据。

冻结原型后首次启动预检因验证目录的运行预留超出 4 GiB 约 2 MB 而拒绝，未启动客户端。两份本轮源码快照及索引复制到上面的专用 Artifacts 目录，逐文件 SHA-256 一致后原副本送入回收站；`relocation.json` 保留路径映射，没有删除唯一证据。原型快照名为 `source-static-instance-prototype.{zip,json}`。清出运行预留后才继续，不绕过容量门槛。

## 结论

未安装来源的重复探测被证明是当前纯原版场景的大开销，三处门禁已消除并取得重复整帧收益。静态记录原型正确性成立，整帧收益未重复成立，生产集成已撤回；不是新的默认优化或已完成的增量架构。完整来源控制器、Java/native 增量实例协议、逐记录 GPU 场景表尚未实现，完整 TLAS 输入转换、内容比较和动态实例打包仍存在。最终构件的构建／自动门槛与实际功能复核通过，新的人工视觉验收未进行。GUI 人工验收、第三方迁移、GPU 故障根因与公开二进制许可保持各自边界。不提交、不推送。
