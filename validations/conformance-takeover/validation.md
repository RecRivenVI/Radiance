# 模型与地形接管合同验证

## 目标

验证阶段 0～2 及已登记特殊世界材质的当前实现：实际物品框与绘画、共享资源和独立实例、原始生成器双执行对照、热帧不重复生产、材质状态、重载、删除重建，以及边界方块／水／附加区段几何的完成发布。1920×1080、静音，仅使用本项自己的实例，不计性能。

## 运行

134／135 定向运行使用下列模式；产物路径在 `core`／`extra` 参数中指定，不从旧 Git HEAD 重建基线：

```powershell
py -3.14 -X utf8 components/render_diagnostics/benchmark/world_materials.py startup candidate <core>
py -3.14 -X utf8 components/render_diagnostics/benchmark/world_materials.py surfaces candidate <core>
py -3.14 -X utf8 components/render_diagnostics/benchmark/world_materials.py end candidate <core> --extra <extra>
py -3.14 -X utf8 components/render_diagnostics/benchmark/world_materials.py endsea candidate <core> --extra <extra>
py -3.14 -X utf8 components/render_diagnostics/benchmark/world_materials.py endsea-far candidate <core> --extra <extra>
py -3.14 -X utf8 components/render_diagnostics/benchmark/world_materials.py height candidate <core>
py -3.14 -X utf8 components/render_diagnostics/benchmark/world_materials.py endsea-end candidate <core> --extra <extra>
py -3.14 -X utf8 components/render_diagnostics/benchmark/world_materials.py handoff candidate <core> --extra <extra>
py -3.14 -X utf8 components/render_diagnostics/benchmark/world_materials.py levitite candidate <core> --extra <extra>
py -3.14 -X utf8 components/render_diagnostics/benchmark/world_materials.py levitite-ponder candidate <core> --extra <extra>
```

`<core>` 和 `<extra>` 为冻结的配套发行 JAR 路径。运行器核对第三方输入哈希、记录实际配置、校验归档读回后回收复制环境；设备丢失保全停止，不自动重试。截图供本轮人工观察，不以生成图片代替视觉通过。

`handoff` 使用实际 Sable／Simulated 操作建立共享单方块，核对稳定窗口不再构建网格、单方块／多区段双向切换、拆分、返回主世界、重叠交接和重载。通过要求包含所有操作完成、交接无积压、共享命中、稳定窗口持续选择实例及正常退出；不进行性能测量，不以截图存在替代连续画面验收。当前任务经用户明确允许取消工作区容量限制时附加 `--workspace-limit-suspended`，其他存储上限仍适用。

重叠交接还核对取消一个作用域、主世界槽位重定位均不能发布另一个未准备好的作用域，回调分别且仅执行一次；来源卸载后跨多个真实渲染帧保留其等待中的区段，准备完成后能正常退休。大型保存结构使用现有运行器的 `--expected-ready`，必要时加 `--expected-source` 指定来源 UUID；同时断言登记完整、无脏区段及预期有内容区段数，空区段不要求 BLAS。未请求该断言时，正常退出不作为几何恢复证明。

外部目录仅在用户单独批准本轮 9 GiB 时附加 `--external-limit-gib 9`，默认仍为 8 GiB。正确性会话不织入改变来源资格的生产者统计包装，保留功能断言及故障取证。2026-10-27 的 E 配对候选通过全部交接操作，稳定窗口构建数不增长，11 次交接全部完成；证据与限制见[调查](../../documents/research/investigation-sable_model_handoff.md)，连续视觉仍待用户验收。

`height` 在三维度跨越地平线及最低高度，核对末地相同雾色不再随高度变黑；`endsea` 强制退休一次真实遮罩区段缓存再重建。2026-10-27 使用者要求直接投放并跳过部分验证，这两项新增客户端步骤尚未运行，不算通过。

`endsea-end` 使用末地中的真实来源夹具，保留位置、倾斜与缓存退休步骤，不加载无关的 Voxy。使用者若明确临时允许工作区达到 20 GiB，可在本轮命令中加 `--workspace-limit-gib 20`；默认仍为 16 GiB，硬上限不可提高。当前末地真实夹具已在 `visual-fixes-20261027-b` 配套包上完成三个姿态、遮罩缓存重建和正常关闭，具体身份及结果见其候选清单，不代替用户目视验收。

从仓库根目录执行：

```powershell
.\gradlew.bat :render_diagnostics:prepareTakeoverStages
$env:RADIANCE_CHUNK_TRACE = "1"
.\gradlew.bat :version:1.21.1-neoforge:runValidationConformanceTakeoverClient
Remove-Item Env:RADIANCE_CHUNK_TRACE
.\gradlew.bat :render_diagnostics:analyzeTakeoverStages
```

自然设备丢失保全证据并停止，不自动重试。准备和分析只由工具生成实例、结果和证据归档；保存的同一初始夹具可以供已授权的阶段 2 性能对照使用。

## 整体平台组合验证

区段顺序正确性复用已准备的冻结城市运行时，不采集帧时间窗口；`<fixture>` 是本仓库诊断组件 `build/` 内的已准备目录，`<core>`／`<extra>` 为本轮实际候选：

```powershell
py -3.14 -B -X utf8 components/render_diagnostics/benchmark/sublevel_investigation.py run --ordering-only --fixture-build <fixture> --label section-order --core <core> --extra <extra>
```

通过条件为实际产物匹配、客户端正常保存退出、无设备丢失；结合有界 trace 检查来源轮转、重要性、完成发布及明确交接的扣留原因。正常退出不证明城市加载顺序或所有内容正确，连续图像由使用者验收；本模式没有 FPS 或吞吐收益结论。

Veil 资源回归使用同一打包组合，但不加载已暂停的 Voxy。冻结的 `<core>`／`<extra>` 发行包完成构建后执行：

```powershell
py -3.14 -B -X utf8 components/render_diagnostics/benchmark/veil_validation.py <core> <extra>
```

只有使用者明确临时允许工作区达到 20 GiB 或以上时附加 `--workspace-limit-gib 20`，其余预算不变。必须在资源重载前后各完成一次 Veil 原始固定／动态 block 的变化缓存检查，以及固定全屏三角形的 Vulkan 离屏颜色读回，`veil-resources.json` 的 `checkpoints` 为 2；同时满足现有平台、实体与正常关闭检查。结果只证明这些实际路径和资源回归，不代表任意 Veil 程序、完整图解体验或性能收益。

重组验证复用本项小型夹具与原版 renderer 对照，另核对公共状态页面、三个原版相机效果、持久参数及 Extra 缺席／无目标／实际目标三种条件。只运行自己的项目实例，不测性能；输入 JAR 使用只读文件链接，避免复制二进制占用预算。开始前先完成两仓库构建，固定当前实际产物：

```powershell
python -X utf8 components/render_diagnostics/benchmark/platform_integration.py core
python -X utf8 components/render_diagnostics/benchmark/platform_integration.py extra-alone
python -X utf8 components/render_diagnostics/benchmark/platform_integration.py sources
```

每次结束须有 `PLATFORM_INTEGRATION PASS`、原版来源与重载对照、正常保存及 Native 关闭；实际适配来源与预期一致。失败先留存，修正明确原因后才追加验证，不盲目重启。设备丢失停止整个用例组。状态页只证明真实 CPU 路由，画面与人工验收分开记录。

## 通过标准

134／135 的材质补充在相同项目内夹具执行，`world_materials.py` 的 `startup`、`end`、`surfaces`、`endsea` 分别观察窗口交接、跨维度、连续介质／光源／曝光和实际来源遮罩。Core 与 Extra 路径传入本轮冻结产物；`endsea` 必须使用配套 Extra，不能只用公式测试代替深度及软强度纹理读回。

实际原生成器对照的顶点位置／法线符合声明容差，其余材质字节一致；稳定热窗资源构建数量不增长，直接提交持续发生。两个实例变换与外观独立，模型重载后使用新身份。地形实际附加 renderer 执行，连续边界更新的有效版本可追踪到 GPU 完成、发布、TLAS 记录及完成帧；旧版本不能覆盖新请求，构建中旧几何仍合法。正常保存与 native 关闭，无设备丢失、字体所有者错误或新崩溃。截图是使用者目视待验材料，不代填视觉通过。

## 整体候选人工体验

入口由工具准备，不启动；它是独立的新组合场景，既有用户验收实例不变。2560×1440、总音量 50%、音乐关闭、RecRivenVI，加载本体与 Extra、固定 Create／Sable／航空学／Voxy，以及既有 Custom Skin Loader 和 Zume 输入，不装探针。

从仓库根目录执行以下命令自行启动：

```powershell
python -X utf8 components/render_diagnostics/benchmark/platform_integration.py manual
```

进入“单人游戏”中的 `Audit Benchmark`。可在一次会话检查：

1. 中间原版实体、箱子、展示框、手持物与粒子；移动和转视角时内容、动画和阴影连续。
2. 两侧 Create 电机／转轴与四个 Sable 木板结构；靠近、交互与观察变换，材质及位置正常。复杂弹簧、图解和 Ponder 界面可随后按实际使用补验，本轮自动会话未替代它们的目视结果。
3. 设置内打开“绘制接管状态”，能区分专项来源、直接／捕获及驻留／瞬时；关闭后不持续统计。库存、文字、菜单模糊及已支持相机效果正常。
4. 执行 F3+T，保存返回主菜单再进入，观察资源、实例和界面能否继续正常；这项人工结果尚未填写。

不要重新执行 `prepare-manual` 覆盖已经使用的实例。运行中的候选不得构建覆盖其 JAR；需更新时先正常退出。当前包只作本地验收，不公开发布。

## 128 号配对收尾验收

复核使用独立的 `closeout`，人工入口使用另建的 `closeout-manual`，均不替换原 `client`。唯一产物与源码清单在 `result/platform-closeout/candidate.json`，自动结果和未确定的下蹲因果见项目的收尾实验记录。自动运行已使用冻结用户副本、32 视距、1920×1080、静音；图形效果在姿态采样结束后核验，未进行完整收益实验。

已准备的新人工入口为 2560×1440、总音量 50%、音乐关闭、RecRivenVI，保留配套 Extra、原模组组合与 Custom Skin Loader／Zume，不含探针；准备后没有启动。仅由使用者自行执行：

```powershell
python -X utf8 components/render_diagnostics/benchmark/platform_closeout.py manual
```

可在同一次体验检查原先点位站立、下蹲及恢复的帧率和冷暖变化，然后检查常用界面／第三方外观、F3+T 与保存重进。自动像素对照不代替这些目视结果。新旧人工入口各用自己的世界，不把一处的游玩更改自动同步到另一处；不要重新准备或构建覆盖已交给使用者的实例与产物。

## 129 号巨大子世界与 CPU 调查

来源为正常保存退出的 Prism Newport City 副本，冻结输入清单与配对候选分别在 `result/platform-sublevel/source.json`、`result/platform-sublevel/candidate.json`。不得重新冻结覆盖已有输入；每次用例从不可变 ZIP 解出自己的 `s129`，结束取证后回收复制客户端。未投放或启动 Prism，不混用缺失结构的基线与完整场景性能。

以下是已执行用例的复核方式；只有获准运行时才启动客户端，参数中的候选路径取自配对清单：

```powershell
py -3.14 -X utf8 components/render_diagnostics/benchmark/sublevel_investigation.py run --label candidate-1080 --core <core-jar> --extra <extra-jar>
py -3.14 -X utf8 components/render_diagnostics/benchmark/sublevel_investigation.py run --label candidate-2160-city --core <core-jar> --extra <extra-jar> --width 3840 --height 2160 --city-view
py -3.14 -X utf8 components/render_diagnostics/benchmark/analyze_sublevel.py <case-directory> --jfr-tool <jdk-jfr-executable>
```

巨大目标必须在同一副本出现，665 个区段不再全部 dirty，422 个有内容区段 Native-ready，且截图实际可见、正常保存退出、实际加载身份一致，无设备丢失。CPU 窗口要求 writer／Native 丢弃、token 缺口与失败帧为零，JFR 有实际渲染线程样本；总帧、CPU 与 GPU 各自统计，不相加。4K 朝向与移动高度变化必须随结果披露，不作同视角 A/B 收益证明。人工仍需观察巨大结构完整外观、运动和交互，未发生的场景不能列为通过。

## 来源维护场景架构

复用已冻结的 129 完整副本、加载器与模组。先运行功能回归，再在获准的安静环境中比较完整旧包和新配对包；不以缺失结构的用例作为收益基线。每项均重新解出自己的实例，1920×1080、32 视距、静音，其他质量配置不变：

```powershell
py -3.14 -X utf8 components/render_diagnostics/benchmark/source_scene.py candidate --label candidate-functional --lifecycle
py -3.14 -X utf8 components/render_diagnostics/benchmark/source_scene.py vanilla --label vanilla-correctness
py -3.14 -X utf8 components/render_diagnostics/benchmark/source_scene.py factory --label source-correctness
py -3.14 -X utf8 components/render_diagnostics/benchmark/source_scene.py baseline --label baseline-a
py -3.14 -X utf8 components/render_diagnostics/benchmark/source_scene.py candidate --label candidate-b
py -3.14 -X utf8 components/render_diagnostics/benchmark/analyze_sublevel.py <case-directory> --jfr-tool <jdk-jfr-executable>
```

功能标准包括巨大结构恢复、来源空间与原始姿态公式一致、移除并重新提交区段后恢复发布、放置／移除方块的客户端状态收到、资源重载及正常退出世界再进入。放挖客户端状态不能冒充首个显示帧；Native 事件另核对 GPU 完成、发布和引用顺序。功能用例的重载、破坏性变化与额外追踪不进入性能结论。设备丢失立即保全并停止整组；其他失败须先定位修正，不原样重启。

性能分别报告加载、站立、固定移动的真实帧分布、渲染线程 CPU、相关阶段和资源峰值，核对样本完整性、实际产物身份及同条件。不得将局部节省相加为整帧收益，也不能将窗口中的调用量作为全部生产者覆盖率。每次结束保留日志、采样与截图，回收复制的客户端环境；使用者的外观验收保持独立。

2026-10-25 已完成纯原版、实际组合、巨大结构生命周期及旧／新／新／旧四次对照，唯一配对清单为 `result/source-scene/candidate.json`，结论见[本轮实验](../../documents/research/experiment-source_owned_scene.md)。原版／组合功能夹具不作为城市性能数据，额外参数聚合只在独立功能运行记录；没有重新混算正式窗口。

## 选中实例与来源删除交错回归

修复构件冻结在本体 `build/candidates/source-scene-fix/`，Extra 沿用原配对包。本项在真实组合夹具中，让一次已成功选择的不变实例在同一原生提交前收到来源删除；不制造 GPU 故障、不测性能。验证容量遵守项目规范 X-12。

```powershell
.\gradlew.bat :version:1.21.1-neoforge:probeDevelopmentJar
py -3.14 -X utf8 components/render_diagnostics/benchmark/source_scene.py retirement --label retirement-correctness
```

通过须有 `persistent-selection-regression.json` 的 `passed=true`、真实原生提交返回，以及原有组合对照、重载、保存和正常关闭通过。下一帧允许来源按当前状态重新登记；已删除快照不重新成为驻留版本。自然设备丢失保全后停止，不自动重试。该人为生命周期交错只在探针启用，本体发行包不含探针，且不代替用户对崩溃点位的复验。

## 持久来源、特殊状态与未受光材质

130／131／132 号在异常修复配对包上继续验证。证据保存在 `result/persistent-sources-130/`，产物及源码身份由其中的配对清单固定；不覆盖旧包。纯原版功能与组合功能使用以下入口，启用标志只作用于当前终端：

```powershell
$env:JAVA_TOOL_OPTIONS='-Dradiance.audit.persistentSources130=true -Dradiance.audit.rendererReplacement=true'
py -3.14 -X utf8 components/render_diagnostics/benchmark/platform_integration.py core
$env:JAVA_TOOL_OPTIONS='-Dradiance.audit.persistentSources130=true -Dradiance.audit.persistentSelectionRegression=true'
py -3.14 -X utf8 components/render_diagnostics/benchmark/platform_integration.py sources
Remove-Item Env:JAVA_TOOL_OPTIONS
```

通过标准包括实际源几何与材质对照、有效地图更新、第一人称动作、死亡射线与目标光束、嵌套粒子、邻域变化后的破坏覆盖，以及重载后部件增删；世界 lightmap 不进入基础色／发光，预览仍执行光照，染色仍改写材质。额外 Sable 用例检查内含、跟随、乘坐、进出、睡眠与名称姿态，并验证未知 Veil 状态不会被静态解释绕过。自动公式对照不代替结构运动和材质外观的人工验收。

性能使用同一冻结城市及同一探针，另用实体／文字密集场景。在安静环境获准后运行，不并行构建；候选路径从冻结清单取值：

```powershell
py -3.14 -X utf8 components/render_diagnostics/benchmark/sublevel_investigation.py run --label persistent-frame-only --core <core-jar> --extra <extra-jar> --frame-only
py -3.14 -X utf8 components/render_diagnostics/benchmark/analyze_sublevel.py <case-directory>
```

FRAME_ONLY 的 `source-work.json` 必须为 `enabled=false`；该窗口只比较真实帧分布、聚合 CPU 和基本资源采样，不将候选独有的归因计数带入对照。归因窗单独报告绑定、姿态和文字工作量，不能将减少的调用数等同于整帧提速。设备丢失保全并停止，不自动重试。

## 世界材质、薄层与启动

134／135 号复用本项的冻结设置与自有夹具，不读取或启动 Prism。候选身份见 `result/world-materials-134/candidate-prepared.json`；运行名须每次唯一。下列占位符 `<core-jar>` 与 `<extra-jar>` 分别取该清单的本体和 Extra 产物路径。

```powershell
py -3.14 -X utf8 components/render_diagnostics/benchmark/world_materials.py startup prepared <core-jar>
py -3.14 -X utf8 components/render_diagnostics/benchmark/world_materials.py surfaces prepared <core-jar>
py -3.14 -X utf8 components/render_diagnostics/benchmark/world_materials.py endsea prepared <core-jar> --extra <extra-jar>
py -3.14 -X utf8 components/render_diagnostics/benchmark/world_materials.py end prepared <core-jar> --extra <extra-jar>
```

自动运行静音、1920×1080。仅可通过 `--reuse-cache <owned-cache.zip>` 复用本项保留的自有缓存，并登记来源哈希；首次自有缓存不等于驱动全局冷缓存。启动须到无加载覆盖层的可交互菜单，另核对已完成加载帧、最大间隔、单窗口与实际驱动创建时间。两次 `world` 分别是查询和 SHARC 更新，不合并计数。

算法包实际属性来自独立的 `radiance/shaders/world/ray_tracing/vanilla-pt.zip.txt`，预检与运行清单同时记录它和预设 YAML，不能只编译算法包默认值。材质与 End Sea 功能夹具显式使用新月光／曝光默认值和八次反弹，以覆盖多介质界面；不改变产品反弹默认值或用户配置。功能夹具单独清理自身目标区域，避免原压力场景遮住观察目标，不作为等内容性能对照。

材质运行覆盖连续介质、发光层与光源、固定曝光色调、无光室内、墨粒子、第一人称、日月与天气；截图与自动退出仅证明实际执行，不代替视觉验收。End Sea 须读回真实深度和五轮软边遮罩，保持指定的结构姿态，再移动／倾斜结构并移动相机，三阶段都有与四十八层相交的有效遮罩。末地须正常进入、停留、返回并退出。Java／着色器失败先定位修正；设备丢失保全并停止，不自动重试。

### 天空与随机远景功能回归

沿用上述 `world_materials.py`，`environment` 模式覆盖主世界晴天、雨、雷暴、失明、黑暗、水、熔岩、细雪、下界与末地，检查原始天空颜色不受状态／浸没污染，并保留实际截图。`height` 模式检查跨维度与低高度环境色。`surfaces` 模式继续覆盖嵌套介质及第一人称；GPU `medium-optics` 检查共享截止距离、传光不淡出、天气端点和原介质吸收。自动截图不代替用户外观验收，不据此报告性能收益。

```powershell
py -3.14 -X utf8 components/render_diagnostics/benchmark/world_materials.py environment environment-20261027 <core-jar>
py -3.14 -X utf8 components/render_diagnostics/benchmark/world_materials.py environment environment-advanced-20261027 <core-jar> --algorithm advanced
```

本轮使用者明确临时取消工作区容量限制，因此实际运行附加 `--workspace-limit-suspended`，逐次写入启动记录。该开关不改变默认上限，验证与外部目录的 8 GiB 上限继续生效。

### 天体、传送门、介质退出与菜单限帧

`surfaces` 另覆盖下半天空的日月朝向；真实下半球外观、传送门动画与玻璃后的 End Sea 仍由用户验收。原生 `medium-optics-gpu` 使用真实薄盒 TLAS 验证直接成像及传光查询均能命中退出面，离开后远处叠层不再增加玻璃吸收。`startup` 在可交互菜单记录八秒真实帧数，要求上限不超过 62 FPS；低帧率不自动作为启动性能通过。

```powershell
py -3.14 -X utf8 components/render_diagnostics/benchmark/world_materials.py startup visual-fixes-menu <core-jar>
py -3.14 -X utf8 components/render_diagnostics/benchmark/world_materials.py surfaces visual-fixes-vpt <core-jar>
py -3.14 -X utf8 components/render_diagnostics/benchmark/world_materials.py surfaces visual-fixes-advanced <core-jar> --algorithm advanced
```

完整步骤和早期失败边界见[修复记录](../../documents/research/incident-celestial_portal_and_medium.md)。最终候选已运行，不重复占用这些用例名；新运行须使用新标签。

### labPBR 掠射角法线

`normals` 模式在自身实例生成常色 albedo、量化 RG 法线条带及漫反射／金属材质的资源包，确认包内法线资源实际加载后，从两侧掠射及正面观察。场景还经过已有连续介质夹具。没有第三方素材或性能采样；自动截图只证明实际执行，外观仍由使用者验收。

```powershell
py -3.14 -X utf8 components/render_diagnostics/benchmark/world_materials.py normals grazing-vpt <core-jar>
py -3.14 -X utf8 components/render_diagnostics/benchmark/world_materials.py normals grazing-advanced <core-jar> --algorithm advanced
```

原生 `mcvr.diagram-surface-gpu` 直接执行共用法线函数和 Disney BSDF，覆盖合法字节法线、极斜视线、镜像切线、着色基底与几何法线分离、漫反射／金属以及异常输入。要求输出有限、法线与视线同半球、理想反射在真实几何表面外、有效 BSDF 与 PDF 为正，原有稳定法线保持不变。旧实现的离线对照应失败，不能再以旧公式等价作为通过标准。记录见[掠射角调查](../../documents/research/incident-labpbr_grazing_normals.md)。

### Sable 与 Flywheel 共同交接

`handoff` 模式在前述局部模型检查后加入真实 Create 转轴，连续执行主世界装配、回主世界、胶水目标为另一 sublevel 的合并、从父结构再次分离、父结构完全消失的整体分离、直接删除及重载。检查关联区段不能越过未准备的参与者；每帧保留的实例数不能低于夹具中的两根转轴，并确认实际发生旧实例保留与新实例暂存。计数证明提交连续性，截图不代替动态视觉验收；没有性能窗口。

```powershell
py -3.14 -X utf8 components/render_diagnostics/benchmark/world_materials.py handoff sable-visual-handoff <core-jar> --extra <extra-jar>
```

运行仍须通过容量预检；仅在本轮使用者明确授权时附加工作区容量暂停及外部 9 GiB 参数。

### Sable 包装与 Catnip 材质受光

`RADIANCE_RECEIVED_LIGHT_CHECK=1` 将上述 `handoff` 会话限制为安山门与缓存模型属性采样：四个朝向、主世界／子世界状态、静态与运动部件、由探针主动构造的单层／嵌套包装、光栅预览和独立染色模板。仅当前终端启用，使用新运行标签；不运行整套交接或性能窗口。包装用例验证通用合同，不证明安山门的真实路径会生成这种组合。

```powershell
$env:RADIANCE_RECEIVED_LIGHT_CHECK = '1'
py -3.14 -B -X utf8 components/render_diagnostics/benchmark/world_materials.py handoff received-light-check <core-jar> --extra <extra-jar>
Remove-Item Env:RADIANCE_RECEIVED_LIGHT_CHECK
```

通过须有非空源几何，PT 未额外乘入固定方向明暗；单层／嵌套包装与直接 PT 一致；缓存预览保留源光栅明暗，RGBA、UV、overlay 和法线不因包装丢失。`world-materials.json` 的 `receivedLighting` 保留实际输入，`passed=true` 且正常退出、无设备丢失。不将颜色输入采样当作用户动态视觉验收。原输入对照见[当时调查](../../documents/research/investigation-sable_received_lighting.md)，真实路径与必要性更正见[合同复核](../../documents/research/review-catnip_wrapper_contract.md)。

`levitite` 创建主世界水晶、一个／两个／三个水晶的真实 Sable 结构，以及前方玻璃。探针在真实来源速度状态入口注入正反两组速度与角速度，不作为物理模拟证明。通过要求为主体和两层重影真实提交，三种外壳分别保持 384／640／896 个细分顶点且没有退化三角形；记录原始区段、远坐标原点、旋转中心和最终位置。稳定窗口模型定义不重复；之后移除玻璃取得无遮挡截图，再验证资源重载恢复、正常退出且无设备丢失。截图与提交计数不能代替颜色、反射、真实运动及空间交界淡化的人工验收。

`levitite` 还逐帧检查八次邻近普通方块放置／破坏期间未改动水晶的主体连续存在；删除最后一个来源图层及全空气区段内的唯一水晶后，旧表面必须退出集合。两种清空均不得遗留幽灵几何，资源重载后原有 sublevel 主体与重影继续恢复。

`levitite-ponder` 在世界中建立水晶结构，再打开实际注册的 Ponder 教程，在多个时间点及返回开头后保存画面、实际播放时间、特效开关和 UI 程序名称。`passed` 只表示运行过程没有异常；另须核对来源特效开关保持关闭、UI 不提交水晶特效程序，且正常退出、无设备丢失。超过教程长度的请求按实际播放时间计，不计为额外阶段。截图不替代连续动画的人工验收，也不能证明偶发问题不存在。

GPU 表面实现的 `levitite` 还要求运行下列读回核验。`<case>` 为本次工具输出的完整结果目录；必须覆盖三种壳体数量和两种速度符号、无退化且固定索引一致，位置误差不超过 0.004 方块、alpha 不超过 0.002。主体读回必须有非零节点，字段误差不超过 0.004，允许硬件过滤和浮点求值差异。

```powershell
py -3.14 -B -X utf8 components/render_diagnostics/benchmark/verify_computed_surfaces.py <case>
```

规模测试使用 `levitite-stress` 代替 `levitite`，其余构件参数相同。可用 `--settings-input <登记名称>` 载入已核对哈希的游玩配置，必须取得实际生效设置检查点。初次队列持续平静十秒、预热五秒后，创建同一个 32×32 薄片，分别测主世界静止、三十次普通方块修改、sublevel 静止及形变四个窗口。效果速度在实际消费入口固定，正确性读回关闭；新旧使用同一管线、输入和采样版本。`world-materials.json` 保留逐帧间隔、渲染线程 CPU、收集时间及定义创建数，四阶段完整、正常退出且无设备丢失才通过。实际收益按净帧时间报告，不以资源计数代替；此测试不是物理模拟吞吐或区段编译计时，也不承诺固定 FPS 门槛。

`endsea-far` 延续真实 End Sea 遮罩的平移、倾斜、缓存退休与层下观察，再将结构和摄像机移动到两组正负两千万坐标，读回真实遮罩并检查已发布材质原点没有提前丢失精度。夹具使用 Sable 的强加载票据度过目的地区块尚未到达的空窗，不改变产品加载规则。实际连续画面及 UV 精度同时参考 `native_renderer:gpuTest` 的 layered-coordinates 正反例；截图不能代替用户动态目视验收。

### 显示输出与发光

```powershell
py -3.14 -B -X utf8 components/render_diagnostics/benchmark/world_materials.py display display-output <core-jar>
```

`display` 在独立静音 1920×1080 实例创建海晶灯室内、玻璃和金属反射面，覆盖 SDR、Bloom 开关、自动 HDR／SDR、亮度原位更新与往返切换、室内外、昼夜、物品栏、设置和 F3+T。生产显示编码后的读回及 GUI 白／灰参考块须符合实际格式，曝光有限且为正；必须取得五组完整 `DISPLAY` 捕获，不能只凭正常退出判断编码通过。HDR 不可用时须正常回退；HDR 物理亮度、连续视觉和生成帧观感仍待实际显示器验收。添加 `--algorithm advanced` 检查另一内建算法。

Native 的 `mcvr.display-profile` 检查格式、能力和峰值选择；`mcvr.display-colour-gpu` 执行实际 sRGB、PQ、色域、亮部压缩及发光函数；`mcvr.fg-ui-gpu` 检查 FP16 GUI 覆盖和背景处理。两套实际着色器包定向编译使用现有 `compile_world_materials.py`，按本次产物及配置记录身份，不用内部浮点图像代替 HDR 窗口输出证明。

显示参数刷新或输出切换的定向回归可在同一命令前设置进程环境 `RADIANCE_DISPLAY_CONTROLS_ONLY=1`。它仅运行亮度原位更新、返回 SDR、返回 HDR、系统界面白和自动设置界面五场景，要求手动／自动两组完整读回；请求的参考白／界面白／峰值必须等于实际值，自动模式与当前显示器的系统白一致，参数更新窗口不得重建世界资源。该模式不代替此前完整场景与另一算法的验证。
