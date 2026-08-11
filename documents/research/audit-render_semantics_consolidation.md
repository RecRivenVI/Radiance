# 同类绘制语义与机制整合审计

调查日期：2026-10-16。结论：建议按“姿态合同 → 目标与提交阶段 → 线请求 → 材质分类 → 颜色与覆盖率”推进，另有六个后续候选族。本轮只分类、追踪和提出合同，没有修改产品、着色器或第三方源码，没有构建发行包、启动游戏、测性能或执行 Git 写操作。候选不是已经获准实施的计划，也不代表已证明新的视觉故障。

## 问题

同一种输入可能经过实体捕获、通用世界事件、Sable、Flywheel、离屏光栅和不同 PT 管线，各自解释姿态、材质或提交顺序。只修一个来源容易留下另一条路径。此次先完成文件归属，再按入口、九项属性、重复信号和外部来源四个视角寻找共同决定者。

现行产品合同以 [adr-1](../design/adr-1-query_visibility.md) 为准：直接成像、连续折射、优先级成像和传光并非同一种查询；不恢复已否决的背景直通叠加。Ponder PT 保持封存，默认 Ponder 使用光栅。线条仅指明确的线图元请求，不把 Catnip 的 QUADS 框、绳索模型或光束归为线。

已有[线条盘点](survey-line_capture.md)与 [Voxy 调查](survey-voxy_pt_integration.md)是输入。本轮确认的接收缺口不等于新发现的运行故障；上一候选的菜单淡入、拉杆线条旋转已有用户反馈通过，不扩大为全部来源通过。旧研究中的冻结状态、未运行结论和设备丢失边界不变。

## 方法

### 读取身份与文件覆盖

HEAD 为 `72d2e4a3275e782418f59142e2f2ea1f01be4120`，以包含既有修改、删除和必要未跟踪文件的工作树为准。暂存区初始为空。逐文件 SHA-256、工作树状态及原始入口清单保存在 `D:/Workspaces/Scratches/RadianceSemanticsAudit/`，不是可构建源码快照，也不是运行产物身份。

| 指定范围 | 文件数 | 归入入口或合同域 | 无绘制语义决定 | 未分类 |
| --- | ---: | ---: | ---: | ---: |
| Target `src/main`，含资源 | 732 | 718 | 14 | 0 |
| native `jni` | 21 | 21 | 0 | 0 |
| native `src/core` | 226 | 207 | 19 | 0 |
| native `src/shader` | 258 | 257 | 1 | 0 |
| `render_diagnostics`，含判断域与排除域 | 58 | 27 | 31 | 0 |
| 合计 | **1,295** | **1,230** | **65** | **0** |

文件归属覆盖率为 **100%**，在深入候选前完成。排除的是构建、日志、纯统计、运行编排等不决定产品绘制语义的文件，并保留排除原因；不是把难以分析的绘制入口排除。生成目录、`third_party` 不在此计数中。209 个 Target 图像资产和其他二进制资源按路径、用途与哈希分类，没有冒充读取其画面。补充读取 `src/common/query_policy.hpp` 等直接合同依赖，单列为证据，不扩大上述分母。

覆盖率证明的是文件归属完整，不是全游戏行为正确、每个分支都执行过或所有反射调用已解析。具体证据分三类：**事实**是当前代码及确定调用关系；**风险**是可推导的失效方式；**未知**是尚缺当前版本运行、第三方方法体对应或视觉证据。

| 清单 | 内容及限制 |
| --- | --- |
| `files.json`、`file-entry-map.csv` | 所有文件的归属、理由、九属性标签、内容哈希；`breadth-initial.json` 保留初轮待归类结果 |
| `breadth-summary.json`、`coverage-by-scope.json` | 统计、HEAD、工作树及暂存区基线 |
| `interception-sites.json`、`entry-contracts.json` | 860 个注入、包装、访问器或 JNI 声明/实现位置；记录来源、图元/透传边界、目标、属性候选和注册情况，不是 860 种独立绘制 |
| `attribute-sites.json` | 全范围九属性词法命中，作为查找索引；不是每条都在修改该属性 |
| `identity-branches.json`、`repeated-small-constants.json` | 名称识别分支与重复数值的候选现场，逐族判断用途 |
| `shader-function-comparison.json` | 两套 PT 同名函数比较；不展开宏和 include，不能用哈希证明运行等价 |
| `external-producer-checks.json`、`loader-source-checks.json`、`companion-source-checks.json` | 本轮重读的外部生产者、当前加载器源码条目及哈希 |

下文现场均为此次工作树行号。缩写只用于控制报告体积：`J/` 为 `versions/1.21.1-neoforge/src/main/java/com/radiance/`；`N/` 为 `components/native_renderer/src/core/`；`S/` 为 `components/native_renderer/src/shader/`；`C/` 为该组件的 `src/common/`。机械清单保存完整路径。

### A：入口矩阵

所有范围文件均归入清单中的入口域；下表压缩重复入口，不把访问器、JNI 声明和实际生产者重复算成内容来源。图元未知时透传原始 mode/layout，不能按来源名称猜测。

| 来源与实际接管层 | 图元 | 目标及关键决定 |
| --- | --- | --- |
| 原版区段、NeoForge 附加区段、外部区段；`ChunkProxy` → native `Chunks` | 来源区段 QUADS/材质分组 | 世界 PT；准入、版本、局部原点、面状态、材质、发布与退休 |
| 实体、BER、粒子；`EntityProxy` / `StorageVertexConsumerProvider` | 面、条带、显式线模式 | 世界 PT；姿态、材质、颜色、所有权；粒子不能一律当自发光或双面 |
| `ModelPart`、刚性捕获与普通完整捕获 | 局部面或完整顶点 | 世界 PT；稳定模型与本帧实例分离，回退保留原回调 |
| `RenderTypeWorldSinkMixins`、世界阶段事件 → `WorldMeshSink` | 当前只接收面模式 | 世界 PT；帧/资源代际、shader/材质快照、目标；明确线缺口见族 3 |
| `BufferUploader`、`VertexBuffer`、直接 GL 翻译、Veil VertexArray | mode/layout 或显式索引 | UI、离屏、获准光栅阶段或拒绝；必须具备场景/目标身份 |
| Sable 子世界实体、BER、区段及调试 | 继承实际来源模式 | 共享世界 PT；子世界姿态、pivot、精度、代际和所有权 |
| Create/Flywheel 模型与实例、embedding | 模型索引面，枚举含线不等于有活跃线生产者 | 世界 PT；模型常驻、实例姿态、材质 shader 能力与历史 |
| Simulated 弹簧、手杖、绳索、锁定标记 | 源码指定面或线，不能由用途分类 | 世界/优先级 PT 或 UI 预览；来源合同、染色、所有权和锚点 |
| 结构图解、物品预览、Ponder、地图 | 原面模式、文字及 UI | 离屏/GUI 光栅；投影、尺寸、混合、读回、淡出；Ponder PT hooks 未注册 |
| `ShaderRegistry`、Veil shader/FBO 适配、纹理代理 | shader 字段、资源和状态，不生产拓扑 | 解释绑定、布局、颜色空间、目标和资源身份 |
| native Entity/Instancing、CPU/GPU 转换、AS、world prepare | 规范化三角形、实例和元数据 | 展开、朝向、SBT、历史、描述符版本和在途寿命 |
| Vanilla/Advanced、优先级、降噪/升频、GUI/FG 合成 | 射线、计算或光栅 pass | 命中规则、材质求值、时序输入、覆盖率和呈现阶段 |
| 诊断静态分类器、运行 hooks、审计桥 | 观察原请求 | 输出归因/语义标签，不能成为产品合同的第二决定者 |

### B、C：固定属性与重复信号

九属性均作全范围检索，再沿代表调用链判断。JNI 只搬运时不算重新决定语义；多层出现同一个字段也不自动是缺陷。

| 属性 | Java 决定或改写现场 | native / shader 消费及候选 |
| --- | --- | --- |
| 姿态与变换 | Sable bridge、Flywheel embedding、EntityProxy、手持锚点 | world prepare 的坐标模式与历史；族 1、7 |
| 可见性 | `Constants.RayTracingFlags`、提交所有权 | `C/query_policy.hpp`、`S/util/ray.glsl`、优先级；既有 adr-1，见残留核查 |
| 材质语义 | PBR、WorldMeshSink、Flywheel、SpringDrawContract | native 材质打包、各 hit group；族 4、6、9 |
| 颜色与 alpha | setColor/Mix、UV1、ShaderColor、加载淡入 | surface_overlay、UI coverage、FG；族 5 |
| 尺寸 | 调试宽度、普通批次宽度、图解逻辑转物理像素 | 棱柱、viewport、SR/RR rect；族 3、7 |
| 生命周期与提交 | endBatch、事件、重载、模型身份 | 队列、描述符快照、fence 退休；族 2、11 |
| 目标分派 | RenderCaptureContract、RasterPreviewScope、VeilStageAdapter | UI/离屏/WorldPipeline 与默认目标；族 2 |
| 法线与朝向 | MaterialFaces、normal matrix、镜像与部件法线 | material_faces、拓扑配对、几何/BRDF 法线；族 1、8 |
| 来源识别 | shader/RenderType 名称、类身份、反射 API | contentName、SBT group、packed flags；贯穿族 2、4、6、9 |

词法清单包含 288 条名称/类识别候选，既有合理的版本适配，也有重复的材质推断，不能写成“288 个错误”。例如 Sable 反射调用是在适配外部 API；PBR 的 `solid/cutout` 判断和 native 的发光名字集合是在决定产品语义，后者更值得收敛。

重复常量也应按单位分组：线宽 0.0125 与 2.5 × 0.0075 是不同路由的厚度政策；CPU/GPU normalOffset 的 1e-5 是同一规则的副本；Flywheel polygon offset 的 1e-4 是另一种来源层叠偏移；阴影 0.0002 与纹理 cutout 0.1 不能因数字相似合并。跨语言 mode/flag/stride 的副本归族 9，算法内的随机数和质量参数不列为统一目标。

### D：外部覆盖核查

复用 `D:/Workspaces/References/minecraft-references/`，本轮重读 30 个代表生产者文件，另读 Companion 的 4 个相关文件及 NeoForge 21.1.259 sources JAR 的 4 个条目。既有线条调查的更大搜索清单仍作为辅助证据，不将旧遍历冒充本轮逐方法审查。

| 来源与版本边界 | 对应本轮候选族的入口结果 | 未闭合覆盖 |
| --- | --- | --- |
| Minecraft 1.21.1 参考：21.1.250 修补开发源码 | RenderType、BufferBuilder、VertexBuffer、BufferUploader、实体/粒子/GameRenderer；族 1–9、11 的公共入口 | 不是未修补官方全量字节码；可执行/数字 GL、反射与动态生成调用未获运行证明 |
| NeoForge 当前 21.1.259 | 两个 bounds renderer、RenderLevelStage/Highlight；族 2、3、4、11 | 全局 BufferSource 的线提交/延迟 flush 缺口，不把“事件存在”记成接通 |
| Create 6.0.10 与 Catnip | ClientEvents 第 236 行世界事件，轨道 highlight、模型框；族 1–6、8 | highlight 专用入口与 AFTER_PARTICLES 通用入口不同；后者含线接收缺口，Catnip QUADS 不能转成线 |
| Flywheel 1.0.6 | Material、TransformedInstance、IndirectInstancer、GlPrimitive；族 1、4–11 | 持久模型使用自有实例布局；只找到线枚举不能认定任意线模型已支持 |
| Sable 实际 2.0.6，参考 2.0.5 | SubLevel、water occlusion、debug 来源，与当前 bridge/mixins 对照；族 1–8、11 | 2.0.6 发行类引用已由前序盘点固定，但本轮没有把所有参考方法冒充 2.0.6 逐字相同 |
| Aeronautics/Simulated bundled 1.3.2 | 弹簧、拉杆、绳索、手杖、图解；族 1–9、11 | 来源开发树与实际内嵌类身份沿用前序哈希记录；未知动态 pose/自定义 shader 不能凭类名认证 |
| Sable Companion 1.6.0 | 子世界与 Create 适配；跟随 Sable/Create 入口，族 1–4、11 | 不是独立的通用绘制后端；frogport 等事件线仍受公共接收限制 |
| Veil 4.3.2 | shader、stage、FBO、视锥与 Quasar/editor；族 1–7、9、11 | 底层 VertexArray 不经过 RenderType；只有具备具体材质/目标合同才可进 PT，编辑器未运行 |
| Ponder 1.0.82 | PonderUI 第 594–630 行原始投影/缓冲，RenderTypes；族 1、2、4、5、7 | 默认光栅活动；两项 PT hook 不在 mixin 注册中，不能作为当前多视图 PT 缺陷来源 |
| Voxy 移植版 | 仅引用前序静态调查，尚非支持目标 | 后续接入须先明确稳定空间、材质和所有权；本轮不修改或认证 Voxy |

外部表中“跟随入口”表示源码可达链，不代表所有内容都获得相同语义。按族检查的结果是：姿态不存在所有来源共用的完整记录；目标与提交有多套判断；线明确有接收缺口；材质、颜色和发光各有旁路；时序与布局分别由后端适配；资源重载已有协调器。没有将新第三方兼容能力计为完成。

## 发现

### 优先级总表

排序综合已知漏接/历史症状、覆盖面、线条与 Voxy 依赖、改动风险；不是收益或工期承诺。规模“小/中/大”表示相对范围。

| 顺序 | 候选族与唯一决定者 | 证据与风险 | 依赖、规模与建议 |
| --- | --- | --- | --- |
| 1 | 空间、姿态及法线变换合同 | 多来源重复组合，线条已发生过 roll 问题 | 无前置；大风险、中到大范围；先统一记录与验证，不同时改所有执行器 |
| 2 | 目标、阶段及提交边界 | 默认世界 PT 拒绝与 WORLD_RASTER 放行并存 | 依赖 1 的快照；高风险、中范围；先分清合法屏幕效果与世界几何 |
| 3 | 显式线请求与几何展开 | 确定的事件线拒收、宽度与姿态分散 | 等 1、2 的最小合同；中风险、中范围；不另建线专属姿态体系 |
| 4 | 表面材质/透明分类 | 同一语义由名字、shard、实例材质和 native 分别解释 | 依赖 2；高风险、大范围；保留类型差异 |
| 5 | 颜色、染色、覆盖率与淡出 | 同名 alpha 多义，部分公式已共享 | 依赖 4；高风险、中范围；优先统一输入含义 |
| 6 | 发光与接受照明 | 两套名字集合、粒子光照差值、实例 light mode | 依赖 4、5；高风险、中范围；不将 fullbright 自动变发光 |
| 7 | 视图相机、尺寸、时序输入及 reset | 后端独立阈值、坐标转换和历史状态 | 依赖 1、2、8 的身份；高风险、大范围；保留 SDK 适配 |
| 8 | 图元展开、偏移、配对与朝向 | 共用拓扑已有，但快路径准入和偏移还多处实现 | 依赖 1、4；高风险、中范围；先锁定等价测试 |
| 9 | 跨语言 ABI 与 shader 数据布局 | mode、alpha、flags、stride 在多层手写 | 为 3–8 分步提供基础；高风险、中范围；不顺带换顶点格式 |
| 10 | 两套 PT 的共享表面函数 | 同名同体及同名异体同时存在 | 在 4–8 明确语义后；中风险、中范围；不是合并算法 |
| 11 | 重载、持久身份与退休通知 | 已有协调器，多种 epoch 各有职责 | 按前面新增消费者增补；高风险、小到中范围；非立即重构项目 |

### 1：空间、姿态及法线变换

**内容与现场。** `J/compatibility/sable/SableSubLevelBridge.java:488` 组合子世界 rotate/scale，再携带 pivot/worldPos；`J/client/proxy/world/EntityProxy.java:1222` 给 BER 的 PoseStack 乘 localTransform、平移 blockPos-pivot。`J/compatibility/flywheel/RadianceFlywheelEngine.java:449` 递归组合 embedding 的 pose/normal，`:643` 分开打包；`J/compatibility/simulated/SimulatedHandAnchor.java:15` 用 inverse worldView 求锚点。`N/render/modules/world/ray_tracing/submodules/world_prepare.cpp:399` 又按 WORLD/CAMERA/CAMERA_SHIFT 选择相机转换。

**具体差异。** 有的顶点已变换，有的局部模型保留给实例；有的使用完整 affine 和 normal matrix，线基底则被归一化；有的先做 double 原点差，有的携带 camera-relative float。它们不能共用一个“再乘一次矩阵”的接口。风险是重复旋转、错位、非均匀缩放、镜像及大坐标历史错误；本轮没有证明所有这些症状已发生。

**建议合同：** 一个空间/姿态记录的决定者，来源适配器只填值；执行器按类型消费，不反推已丢失信息。

| 输入 | 共同记录与规则 |
| --- | --- |
| 局部刚性几何 | 明确 local space、完整 object-to-scene、pivot、精度原点，顶点不再重复乘实例矩阵 |
| 已变换顶点 | 明确 applied space 与已应用步骤，不再猜 owner 变换 |
| 方向/线截面轴 | 与位置分开；保留完整基底及来源，不把源 normal 当 roll |
| BRDF 法线 | 独立 normal matrix，不能代替几何朝向 |
| 镜像、上一帧 | 记录 parity 补偿阶段、对象/部件/拓扑身份；补偿一次，历史不跨身份复用 |

依赖见总表。先以 Sable BER、Flywheel 和线条验证合同，再逐来源迁移；不把封存的 Ponder 相机因式分解当当前世界路径。验证须比较实际变换后位置/法线、负缩放及同帧多对象，而非仅比最终 RGB。

### 2：目标、阶段与提交边界

**内容与现场。** `J/client/render/RenderCaptureContract.java:64` 在 WORLD_STAGE 的默认 FBO 拒绝缺少材质的 raw draw，但明确 WORLD_RASTER 可放行。`J/compatibility/veil/VeilStageAdapter.java:80` 对未标记 listener 延后执行，`:130` 只以 AfterWorldRender 活动且 FBO=0 判断；`J/client/render/WorldRasterPass.java:67` 进入 WORLD_RASTER、绑定默认 FBO、回放。`J/client/render/RasterPreviewScope.java:17` 又在顶点生产时决定是否作为光栅预览。

**具体差异。** 决策分别基于 listener 标记、阶段、当前 FBO、ThreadLocal scope；有时在生产时判断，有时在 flush 时判断。确认存在允许后置世界光栅的通道，但尚未证明当前某个用户可达世界几何经此回放，因此不宣称已经发现具体画面违规。未知 listener 被宽泛放行的权限边界值得先收紧定义；不能因默认 FBO 就认定它是合法屏幕效果。

**建议合同：** 在明确场景边界内决定一次目标能力，提交记录携带 target、epoch、资源依赖和顺序，不任意重排整帧。

| 请求语义 | 允许去向 | 不允许的替代 |
| --- | --- | --- |
| 有完整材料和空间的世界几何 | 世界 PT，完整查询参与 | 因接收器不支持改到后置光栅 |
| GUI、相机屏幕覆盖 | UI/屏幕效果路径 | 自动加入世界 TLAS |
| 明确离屏场景 | 对应 FBO，保留后续采样/读回依赖 | 推迟到消费者之后 |
| 未说明语义的 raw world draw | 明确拒绝并报告缺失合同 | 仅凭 WORLD_RASTER/FBO0 获准 |

先验证 NeoForge/Create/Veil 的同步与延迟 endBatch，随后复用既有路由枚举；不另造一套平行渲染器。需专门检查屏幕效果和真正世界模型，避免错误关闭合法后处理。

### 3：显式线请求

**内容与现场。** `J/client/proxy/world/EntityProxy.java:123` 定义调试宽度 0.0075 × 来源单位，`:2363` 普通批次固定 0.0125；`:1647` 只读 RenderType 的声明宽度。`J/client/render/WorldMeshSink.java:514` 只接收面模式，尽管其 `submitCaptured` 能结束线捕获器。`N/render/line_geometry.hpp:38` 解码线布局，`:54` 生成方截面；`J/client/render/WorldOutlineGeometry.java:35` 另将派生轮廓参考轴编码进 normal。

**具体差异。** 调试/实体/事件三路的宽度、姿态来源、flush 保障不同；Create 曲线轨道、NeoForge bounds 与普通 Veil 事件线有明确接收缺口。逐顶点基底、owner 回退、派生边参考轴不是同一含义。已有通用展开不能证明接入完整；UI 线宽 JNI 存在也不能证明其上游状态已捕获。

**建议合同：** 继续原六步方案，但等族 1、2 的最小空间和提交合同确定后实施；端点、RGBA、原始宽度/单位、实际模式/分隔、显式基底、目标与查询所有权逐请求保存。

| 来源属性 | 保留项 |
| --- | --- |
| 真实 LINES/LINE_STRIP/DEBUG 模式及明确 GL 线请求 | 正确的源布局、长度、颜色与顺序；不能把 MC 重复顶点当原始 GL 线 |
| 线宽 | 保存源值及单位；暂不擅改当前物理厚度政策，混合宽度不能退化为批次单值 |
| 旋转/缩放 | 使用族 1 的姿态；无 Pose 明确回退，非均匀缩放/剪切另有规则 |
| Catnip QUADS 框、绳索面、光束模型 | 保持模型，不重新识别为线 |

须覆盖四模式、不同宽度同批次、透明端点、事件异常/延迟 flush 与镜像。实际线的颜色/alpha、连接样式和屏幕宽度等价仍需运行验证，不能用拉杆已验收代替。

### 4：材质与透明分类

**内容与现场。** `J/client/vertex/PBRVertexConsumer.java:274` 根据 RenderType 名字与 transparency shard 选 alpha mode，solid/cutout、translucent 与其他半透明不同。`J/client/render/WorldMeshSink.java:607` 先调用它，再对 ordered overlay 改为 23/24，`:630` 构建带 SBT 提示的 materialKey。`J/compatibility/flywheel/FlywheelMaterialData.java:53` 由实例材质枚举另行映射；`N/render/instancing.cpp:272` 再按 transparency 分配 default/lightning group。

**具体差异。** 来源不同但输出同时承担 alpha、几何类别和命中组选择；这些由 Java/native 各自决定。模式数字不同不直接证明错误，它可能是 additive/lightning/glint 的有意区分。风险是同一实际绘制经不同捕获路径变成玻璃、cutout 丢失或有序覆盖丢失。名字解析应留在版本化来源适配器，不能在多个消费者重复猜。

**建议合同：** 一个类型化表面描述，alpha 行为、光照、查询所有权与执行组分别表达。

| 表面行为 | 统一含义 |
| --- | --- |
| opaque | 覆盖完全，不能额外加背景直通 |
| cutout | 来源阈值/纹理 alpha 决定存在；不是平均半透明 |
| coverage | 分数覆盖，保留顺序/颜色关系 |
| physical transmission | 介质与折射按材料，不能由“透明”名称泛化 |
| additive、乘色、ordered overlay | 各保留来源运算和适用阶段，不全部映射成玻璃 |

不把全部几何强制 non-opaque；需要 any-hit 的材料与安全快速路径须有明确条件。先用同一绘制经通用事件/实体/实例入口的材料对照测试，再调整编码。

### 5：颜色、染色、覆盖率与淡出

**内容与现场。** `J/client/vertex/PBRVertexConsumer.java:656` 的乘色与 `:669` 的 SURFACE_MIX 有不同 alpha 含义；`J/compatibility/simulated/SpringDrawContract.java:16` 已显式选择表面混色。`S/util/surface_overlay.glsl:6` 统一混色与 UV1 覆盖；`N/render/ui_coverage.hpp:16` 则单独合成 FG 覆盖率，`:33` 使用 source-over。`J/client/loading/RadianceLoadingOverlay.java:118` 将 fade 放进 ShaderColor；光栅 uniform 由 `J/client/proxy/vulkan/ShaderProxy.java:80` 快照。

**具体差异。** alpha 可能是材质覆盖、表面染色强度或最终 UI 覆盖率；颜色也可能已受 ShaderColor 调制，不能在 native 再乘一次。弹簧/受击的求值公式已共享，不应以“统一”为名重写；菜单状态恢复已修并获反馈，不能重复登记缺陷。风险集中在不同入口是否标清含义、是否只应用一次，而不是断言现有公式错。

**建议合同：** 颜色记录区分来源编码/线性工作空间、调制阶段和 alpha 用途，公式由各目的域唯一拥有。

| 数据 | 处理 |
| --- | --- |
| 普通顶点乘色 | 仅乘一次，覆盖按材料定义 |
| 受力/受击表面混色 | alpha 是混合权重，不降低表面不透明性；沿用共享公式 |
| UV1 overlay | 保留对应贴图与反向 alpha 编码，不并入材质透射 |
| GUI fade / ShaderColor | 保留原绘制顺序与实际混合，结束/异常恢复状态 |
| FG UI coverage | 独立于 RGB 和背景依赖效果；不能用二值画面差分替代 |

依赖族 4、2。回归应验证背景效果前后 UI、半透明文字、图解纸张淡出、材质染色与 gamma；静态输入捕获不等于生成帧动态视觉通过。

### 6：发光与接受照明

**内容与现场。** `J/client/render/WorldMeshSink.java:708` 依据 shader/layer 名称判自发光；`N/render/entities.cpp:1292`、`:1832` 依据另一套 contentName/debug/semanticEmission 再改值。`J/client/render/ParticleEmissionCapture.java:24` 根据来源光值相对环境值的差确定粒子发光；`J/compatibility/flywheel/FlywheelMaterialData.java:12` 则携带 light shader 模式。`J/client/vertex/PBRMaterialContext.java` 另外提供嵌套的 emission 上下文。

**具体差异。** fullbright/unlit、baked light、发光贴图、粒子附加光与自发光辐射并不相同；Java 的 glowing/emissive 字串与 native 的 eyes/entity_alpha 等集合不一致，构成确定的多决定者，而非已证实的全部材料亮度错误。可能症状是跨入口亮度不同、粒子被烘焙光二次照明或无意照亮世界。

**建议合同：** 来源适配一次后携带发光原因、强度/纹理和接受照明方式；采样器和表面着色消费同一描述。

| 来源 | 不应自动推导的结论 |
| --- | --- |
| fullbright/light shader off | 不自动等于向世界发光 |
| 发光纹理或明确 emission | 保留空间分布、颜色与来源数值，不强行置 1 |
| 粒子光照覆盖 | 与环境光分开解析，不把全部光值当辐射 |
| 调试无光照/屏幕覆盖 | 保留其显示合同，不偷偷成为世界光源 |

依赖族 4、5，风险高；需验证表面外观与光源收集两端，并保留来源面标志，不能只统一一个 emissive bool。

### 7：相机、物理尺寸、时序输入与 reset

**内容与现场。** `N/render/streamline_runtime.cpp:237` 共用 SL 相机常量，包括 jitter 符号、motion 单位与正交识别。`N/render/modules/world/nrd/nrd_module.cpp:671` 单独翻转投影 Y，`:694` 写 jitter/历史，`:707` 设置运动矢量比例；`N/render/modules/world/xess_upscaler/xess_sr_module.cpp:410` 另用位置 1.0/方向 0.866 阈值判断 reset。`J/compatibility/simulated/DiagramPresentation.java:9` 已单独定义逻辑到物理像素换算。

**具体差异。** 各 SDK 需要不同输入，但“哪一视图、当前/上一帧、什么事件破坏历史”不应各自猜。独立阈值与 reset 状态确实存在，尚不能直接认定当前 NRD/SL 符号或范围错误。风险为 resize、重载、切视图、优先级颜色与 depth/motion 不对应时拖影或鬼影。

**建议合同：** 一份视图帧描述，SDK 转换留在适配器。

| 变化 | 共同身份与事件 |
| --- | --- |
| 普通相机移动/动画 | 同视图连续历史，保存对应当前/上一投影、姿态和时间 |
| 世界切换、资源/拓扑断代、尺寸改变 | 明确 reset 原因，不复用别的视图历史 |
| 临时输出失败 | 无效输出不得消费，恢复时重置相应历史 |
| GUI/离屏裁切 | 分开物理 viewport、追踪尺寸、合成 rect/UV；不把窗口等同内容尺寸 |

DLSS 已有 evaluate 输出有效性和失败 reset（`dlss_module.cpp:766`），不是本轮待补功能。此族风险高、需真实输入对照；不复活 Ponder PT，也不把所有 SDK 的 reset 阈值改成同一数值。

### 8：图元、位置偏移、配对与朝向

**内容与现场。** `N/render/geometry_topology.hpp:13` 已将最终位置交给共享 quad 修正；`N/render/chunks.cpp:355`、`N/render/entities.cpp:440` 复用该入口，Flywheel `N/render/instancing.cpp:292` 也进入实体构建，不是各自完全未修。`N/render/entities.cpp:41` 的 deferred 资格探测另模拟最终偏移；`:1695` CPU 与 `S/world/entity_convert.comp:55` GPU 各执行 1e-5 normalOffset。Flywheel `instancing.cpp:253` 另用来源 polygon-offset 的 1e-4。

**具体差异。** 共同拓扑修复已经存在；剩余重复在“最终位置究竟包括哪些偏移、何时允许 GPU 直转、上一帧图元身份怎样保持”。不同偏移有不同理由，不能合成一个万能 epsilon。风险是配对判断与实际位置不一致、镜像重复补偿或 motion 读取不同拓扑；本轮未用运行证据证明发生这些错误。

**建议合同：** 一个源图元及最终位置规范，保留展开来源，而非按顶点数猜 QUADS。

| 输入 | 规则 |
| --- | --- |
| 显式 QUADS、strip、fan、线 | 分别按来源拓扑解码；任意三角形不因六个索引就猜成 quad |
| 反向成对薄片 | 用现有精确配对及 provenance；不同 UV/两侧外观不能抹掉 |
| normalOffset、贴花偏移 | 明确坐标空间、单位、施加阶段，配对检查与实际执行一致 |
| 当前/上一帧 | 拓扑变体和身份不匹配时弃用该历史，不能仅按相同数量认可 |

保留现有 shared helper 与 GPU 回退资格，不重新从零实现弹簧特化。回归要走 CPU、deferred GPU、实例和显式索引的实际接缝。

### 9：ABI、标志与 shader 布局

**内容与现场。** `J/client/constant/Constants.java:84` 与 `N/render/world.hpp:19` 分别定义格式 ID，包含 13/14。`J/client/vertex/PBRVertexConsumer.java:437` 打包 compact 字段，`S/world/entity_convert.comp:24` 以 25/32 words 解释，`:48` 另拼 packed flags。`J/client/shader/ShaderRegistry.java:611` 与 `J/compatibility/veil/VeilShaderBridge.java:972` 各列 bool/uint/vector 的类型表。

**具体差异。** 同一 ABI 的 mode、stride、alpha 和 bit 意义有多份手写表；ShaderRegistry 的通用类型、Veil GL 类型、Mojang Uniform 数字码又是不同协议。当前 bool/uint 已有独立映射，不能因 Mojang 适配暂用整数容器就再报旧的有符号翻译缺陷。风险是扩展某条路径时漏更新另外一条。

**建议合同：** 一处定义字段语义和跨层映射；来源 GL/Mojang 类型转换保留独立适配。可先用共用测试向量/静态断言固定 ABI，再决定是否生成声明，不预设新 schema 框架。验证原始位模式、边界值、数组 stride、不同格式及未知值拒绝；依赖前面族的语义定稿，不借机压缩或量化浮点数据。

### 10：Vanilla/Advanced 的共享表面函数

**内容与现场。** `S/world/ray_tracing/internal/vanilla-pt/common/celestial.glsl:3` 与 `advanced/common/celestial.glsl:3` 的 normalize 等函数归一化正文相同；`vanilla-pt/world/default.rchit:131` 与 `advanced/common/surface_eval.glsl:85`、`advanced/world/default.rchit:169` 都定义 sampleSurfaceState，但正文不同。`vanilla-pt/common/end_portal.glsl:11` 与 `advanced/common/end_portal.glsl:11` 的颜色函数也不同。

**具体差异。** 词法提取获得跨两套管线的 103 个同名函数，其中 69 个名字对应的所有提取正文去注释/空白后相同。此统计不展开宏/绑定，既不证明同名必等价，也不证明 34 个异体都是错误。共享材质规则复制后易出现模式间不一致；Advanced 的采样、reservoir、体积光和云算法差异则应保留。

**建议合同：** 共同表面、alpha、颜色、朝向和几何数据解码由共享函数负责，算法层输入通过参数/明确变体提供；不把两套主追踪算法合并。先对不同函数逐项记录有意差异，再抽取同语义部分；依赖族 4–8，风险中等。验证至少是两模式同材料命中/材质输出的对照，不能只比文本哈希。

### 11：重载、持久身份与退休通知

**内容与现场。** `J/client/texture/ResourceReloadCoordinator.java:25` 已以固定锁顺序协调纹理任务、WorldMeshSink、刚性模型、图解和 ShaderRegistry。`J/client/vertex/RigidModelCapture.java:33` 使用 reloadEpoch，`:45` 再结合 level；`J/client/render/WorldMeshSink.java:491` 使用资源代际和世界序列。`J/client/texture/TextureTasks.java:52` 的身份检查与执行已在同一 monitor，`:69` 的旧释放隔离也存在；`N/render/texture_binding_snapshots.hpp:8` 与 `N/render/upload_retirement.hpp:7` 另负责 GPU 引用。

**具体差异。** 多类身份和失效通知确实需要同步，但已存在协调器，未发现“所有持久缓存都没有统一重载入口”的证据。不同 epoch 不应压成一个整数；CPU 任务失效不意味着 GPU 可以提前销毁。候选仅是将新增消费者的通知/失效义务纳入同一登记合同，避免下一次增加缓存漏挂。

| 事件 | 必须保留的独立职责 |
| --- | --- |
| 资源重载 | 旧 CPU 任务拒绝、材质/模型失效、必要历史 reset；原 GPU 引用继续按完成条件退休 |
| 世界切换/对象删除 | 世界与对象代际阻止旧发布，不清除其他有效来源 |
| 帧提交/完成 | 描述符内容与资源引用保持到真实 GPU 完成，不能按“过几帧”猜 |
| fatal/device-lost | 停止新提交、保留原错和有界采证，不能伪造 lost 跳过健康 GPU 同步 |

建议随前面族按需完善，而不是立刻替换所有锁、缓存与 retainer。资源所有权属于高风险机制，不能仅为风格一致进行重构。

### 既有统一的残留核查

`C/query_policy.hpp` 是查询分类和掩码的共同来源，`S/util/ray.glsl:46` 的 queryTraversalFlags 在传光时移除来源面剔除，`S/util/material_faces.glsl:3` / `world_material_faces.glsl:26` 集中处理来源面和成对薄片；优先级经 `S/util/priority_image.glsl`、`priority_surface.glsl` 执行。Java 提交身份的数字副本归族 9，不另建第二张可见性决策表。

本轮没有确认一条足以推翻 adr-1 的旁路。`reflect=false`、`world_no_reflect`、旧 ordered-overlay 注释不能单独作为违反证据：`EntityProxy` 的优先级 groupName 和各 pass 的 SBT 映射会改变实际 hit shader，priority/background 的相关组映射到普通表面求值。是否正确必须沿“来源 → group → 当前 pass 配置 → hit shader”判断。

保留项包括实际 boat-water 几何排除与数值自命中规则。不能把水的其他合法界面全部跳过，不能把 priority 与直接成像叠加，不能把 source-cull 与 paired ownership 混成一个开关。该核查是静态合同回查，未运行全光线矩阵，不签发全项目可见性验收。

### 看似相同但必须分开的内容

| 内容 | 不合并的理由 |
| --- | --- |
| 世界 PT、GUI 光栅、离屏预览 | 目标与依赖不同；共用提交信息不代表强制同一种渲染算法 |
| 普通半透明、cutout、物理透射、加法覆盖 | 覆盖、介质和颜色运算不同，不使用单一“透明”开关 |
| 受力/受击染色与 UI alpha | 前者改变表面颜色，后者改变合成覆盖，不共用数值含义 |
| 点、方向、normal matrix、几何朝向 | 非均匀缩放、镜像下数学处理不同 |
| 普通 source facing 与成对面所有权 | adr-1 对直接成像/传光有不同规则；成对面避免重复界面是另一职责 |
| Catnip 模型框与显式线 | 底层图元不同；只能共享坐标/颜色合同，不能共用线拓扑重写 |
| 纹理 ID 代际、内容版本、世界/帧身份、GPU 退休 | 防止的错误不同；取消 CPU 任务不能替代 GPU 完成条件 |
| Vanilla 与 Advanced 采样算法 | 可共享表面求值，不代表共享光照算法与所有参数 |
| 来源适配器与产品后端 | 版本/模组差异应留在边界；诊断标签不是后端决定者 |
| 封存 Ponder PT 与活动通用世界路径 | 未注册 hook 不是当前正常路径；不为统一工作重新启用实验 |

## 结论

本轮交付 **11 个候选族**，没有把“看起来重复”直接转成修改任务。最明确的现有接入缺口仍是通用世界事件线；姿态和目标/提交边界决定了它能否以通用方式修好。因此不直接孤立执行线条六步方案，先完成族 1、2 所需的最小合同，再接通线；不必等所有姿态来源重构完才开始线条。

建议使用者决定：

1. 是否接受前三项顺序，以及只先做支撑线条的最小姿态/目标合同，暂不推进其余八族。
2. 线宽是否继续采用现有世界物理厚度政策；若要复刻原版屏幕像素宽度，应另明确 PT 中距离/反射/缩放下的表达，不在整合中静默改变。
3. 是否批准后续实现阶段对合法屏幕效果和真正世界几何逐类确认 WORLD_RASTER 权限；这次只指出静态权限边界，未擅自关闭任何内容。

没有新增产品架构决定，也不要求使用者提供 API 或同步方案。实现获准后，工程侧负责具体接口、调用链迁移和行为回归。

实际执行 `gradlew.bat fixMarkdown lintMarkdown verifyCompliance --strict` 成功：Markdown 检查 71 个文件无问题，严格合规检查 1,822 个文件，失败 0、警告 0。文件清单的 1,295 项内容哈希和文件集合不变，HEAD、暂存区及除本报告外的工作树状态不变；52 处完整现场引用均存在且行号有效。记录在上述 Scratches 目录的 `documentation-check.log` 与 `verification.json`。

运行文档检查前统计：仓库计入部分 14.852 GiB，旧 run/dev/logs 合计 1.918 GiB，验证生成目录 1.950 GiB，外部合计 7.891 GiB；所有硬上限满足。已有外部软超的保护证据/输入/SDK 例外沿用存储审计，本轮不擅自删证据。统计方法与完整数值见 `storage-preflight.json`。

没有执行游戏、GPU 测试、性能实验或发行包比较，不能据此关闭视觉、长期稳定性、设备丢失根因或公开二进制许可事项。文档检查不是产品行为回归。

审计到此结束，线条整合及其他候选实施继续暂停，等待使用者选择。
