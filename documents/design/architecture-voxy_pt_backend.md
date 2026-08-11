# Voxy 专用路径追踪后端设计

本设计以 2026-10-28 当前 Voxy 工作区、登记构建及 Radiance Extra 适配为依据：在 Radiance 侧替换 Voxy 的 GL 渲染后端，复用其存储、摄入、模型归并和 CPU 压缩网格；按变化上传紧凑面，由 Vulkan 生成位置并建立持久 BLAS。当前配套 B 已修正全局光源索引，并通过限定七阶段功能验证，见[修复记录](../research/incident-voxy_lod_selector.md)；性能实验暂停，不将数据组织方式称为实测最优，自动验证与使用者视觉验收分别记录。前期发现见[静态评估](../research/survey-voxy_pt_integration.md)，实施边界见[计划](../project/plan-voxy_pt_integration.md)。

## 核查身份与测试输入

| 项目 | 当前核查结果 |
| --- | --- |
| Radiance／Extra | 当前工作区含大量既有修改；配套产物与源码以本轮运行回执为准，不能用 HEAD 替代 |
| Voxy 输入 | `inputs.toml` 的 `voxy`，当前普通构建；旧记录使用 `voxy_legacy` |
| 来源 | 使用者指定的本地 Voxy 工作区只读参考，包含未提交内容 |
| 对应检查 | 401 个项目类与当前编译输出一致，生产源码无较构建更新的文件；这是产物对应核查，不冒称本轮独立重建 |

本机路径只在 ignored `local.toml`。直接复用既有构建，不新增大文件副本，不修改 Voxy 源码或构建，不恢复性能实验。自动运行仅使用项目内隔离实例；实际验证后的配套包按用户授权投放 Prism，不启动游戏。

## 真实接入边界

以下 Voxy 路径相对只读仓库 `D:/Workspaces/Repositories/GitHub/RecRivenVI/voxy/`；来源解释与条件 Mixin 位于 Extra，Native 只处理公共 LOD 格式。

| 来源类与方法 | 已确认行为 | Radiance 侧处理 |
| --- | --- | --- |
| `client/VoxyClient.initVoxyClient` | 首先查询 GL capabilities，再创建共享 GL 索引并设置实例工厂 | 在任何 GL 初始化前进入专用兼容入口；保留合法的存储实例工厂及启用条件，不伪造 GL 能力 |
| `client/mixin/minecraft/MixinLevelRenderer` | 原地形绘制注入触发异步世界获取和 `VoxyRenderSystem` 创建 | 停止 GL renderer 创建；由 Radiance 明确的主世界边界推进数据生命周期，不依赖已被替代的原版绘制回调 |
| `commonImpl/VoxyInstance.requestWorld` | LOADING 的 future 只通知就绪；READY 才在锁内取得 `WorldLease` | 就绪后重新请求合法 lease；不能直接把 future 当 world，也不自行打开第二个存储连接 |
| `platform/ClientIngestion` | 已有无 Sodium 的客户端摄入路径 | 保留原摄入、光照就绪和导入，不重复扫描 Minecraft 区块 |
| `client/core/model/ModelStore` | 字段初始化即 `glGenSamplers`，随后分配 GL 缓冲和图集 | 完全不构造该类，不以空 GL 对象、Unsafe 分配或吞异常代替 |
| `ModelFactory` 构造与 `processUploads` | 模型生产主要在 CPU；构造调用 bakery `setupTexture`；上传函数访问 GL 与 `ModelStore` | 条件 Mixin 替换两个 GL 边界，以 Vulkan 模型表接收 CPU 结果 |
| `SoftwareModelTextureBakery.setupTexture` | 使用 GL 查询及回读 Minecraft atlas，交给软件栅格器 | 从受代际保护的 CPU 图集快照提供像素，不创建 GL context、不作 Vulkan 同步读回 |
| `RenderDataFactory.generateMesh` | 接受真实 `WorldEngine` 与 `ModelFactory`，返回 CPU `BuiltSection` | 复用原算法；每个有界工作槽复用一个 factory，关闭仅服务 GL 的 meshlet／occupancy 输出 |
| `RenderGenerationService`、`ModelBakerySubsystem` | 外围类型与构造绑定 `ModelStore`、GL 服务和单结果消费者 | 首轮不伪装成这些具体类；只编写小范围任务适配，调用已有生成算法，不复制整个调度器 |

实现使用真实 `ModelFactory(mapper, null)`，前述 GL 边界已替换，输出队列由适配器接管；`VoxyModels` 在同步 native 接收之后才释放来源结果。`storage` 的 GL 上传路径不执行。`ModelFactory.free` 仍清空并释放 CPU 结果，关闭时先停生产者。

建议的写入范围为 Extra Target 的 `com.radiance.extra.compatibility.voxy` 和条件 compatibility Mixins；native 增加专用远景存储／解码模块，并在现有 `WorldPrepareContext` 追加持久版本。命中组登记沿用 `core/render/hit_groups.hpp`，属性读取对接 `shader/util/surface_geometry.glsl`、`material_decode.glsl` 和既有查询合同。原 `ChunkProxy`、Sable 外部区段槽和通用动态实体入队都不承担远景资源身份，不扩大为整个世界 API 重写。

`VoxyClientInstance` 可以保留存储实现，但其 renderer holder 类型仍是具体 GL renderer；不让 PT 后端冒充该对象。单独管理 PT session，并核对其 reload、关闭中的 GL 缓存清理及调试静态初始化，只有确定未建 GL 资源的路径才可调用。Voxy 缺席时不加载其类，未知 ABI 在初始化前明确拒绝，不造成无关实例崩溃。

## 数据链与所有权合同

```text
原 Voxy 摄入、存储、父级体素更新
→ Radiance PT 驻留／LOD 请求集合
→ 原 ModelFactory 与 RenderDataFactory
→ BuiltSection：8 字节面记录、8 组偏移、模型代际
→ 有界 native 所有权转移／一次复制到自有 staging
→ Vulkan compute 解码位置
→ 持久三角形 BLAS
→ 与精确近景共同发布的世界 TLAS
→ 公共材质、成像／传光、照明与历史合同
```

接口仅定义必要状态，不替换全局场景系统：

| 概念接口 | 必须携带的数据与约束 |
| --- | --- |
| `VoxyPtSession` | 连接／维度／`WorldIdentifier`、WorldEngine 与 mapper 身份、资源 epoch、合法 world lease |
| `ModelVersion` | 模型 ID、模型及纹理内容版本、biome 表版本、材质能力；CPU 已生成与 Vulkan 已就绪分开 |
| `FarSectionUpdate` | session、section key、请求代际、中心与邻区修订、模型 epoch、分组与长度、压缩面所有权 |
| `FarSceneSnapshot` | 不可变版本及其 GPU 执行依赖、无重叠 LOD 集合、近景覆盖版本、相对原点与历史身份；同命令缓冲先构建、屏障、再使用，不冒称 CPU 已观察到 GPU 完成 |
| `FarSectionRetirement` | 停止新引用与实际 GPU 最后完成点；不得只按 Java 对象失效销毁 |

section key 单独不是跨世界身份；负坐标使用整除语义，验证编码的层级和坐标范围，不截断静默混淆。`BuiltSection.free` 释放直接内存，JNI 不能排队后继续借用其地址。首轮采用同步复制到 native 所有的有界 staging 后确认接收，再释放 Java 缓冲；队列拒绝时调用方仍拥有结果。只在证明队列拥有完整缓冲寿命后再考虑免复制。

接收前核对 8 组偏移单调、面记录长度为 8 的倍数、组与总数一致、模型引用属于相同 epoch、位置与尺寸合法，以及字节／索引计数运算不溢出。错误不进入 GPU 构建，拒收路径仍释放本方已经接管的缓冲，保留旧有效场景与原始错误。

保留一个 session world lease 至其任务全部退出，单任务另持所需 `WorldSection` 引用；生命周期取消先停请求和 callbacks，再排空／释放工作数据，再归还 lease。资源重载或世界切换递增 epoch，旧结果不得发布。CPU 任务完成、命令记录完成、GPU 完成与场景发布分别记录，不能互相替代。

Voxy 的 dirty、state、biome callbacks 都是单消费者入口。GL 后端关闭时由 PT 适配器明确接管或按原所有者合同链式保留，不覆盖存储回调；挂接／撤销须与写入线程同步。不能覆盖一次 setter 就假定并发可见性成立。重试等待模型就绪事件，不在全队列中无限反复扫描。

快照一致性由 `VoxyCaptureLock` 与来源写入 Mixin 维护：`generateMesh` 的原始数组读取先在短读锁下克隆，本地来源 `WorldUpdater`／`WorldSection` 的实际写入入口持写锁并推进修订。网格构建不持全局锁，仅实际读取的中心／邻区内容版本变化时丢弃过时结果并重排；无关区段与接收光变化不触发失效。引用计数仍只负责内存存活，不能替代一致性协议；未约定的第三方直接原始数组写入不在本次来源覆盖证明中。

## GPU 表达与成本取舍

推荐保留压缩面与模型表供 hit shader 使用，只在新增或变化时 compute 写位置；BLAS 沿用硬件三角形遍历。首轮不接入 PTLAS、SER 或全局新 arena，也不让每个面成为一个 TLAS 实例。

| 内容 | 初步布局与成本 |
| --- | --- |
| 每个面 | 保留源 8 字节记录；生成 4 个 16 字节位置记录（float3 加既有策略字段），64 字节；保持当前场景位置／历史 ABI |
| 索引 | 按实际源 `[1,2,0,1,3,2]` 生成持久索引；验证正反面、源角点及拆分，不套用普通 QUADS 模式 |
| 模型 | 原记录 64 字节／模型，附材质与纹理页映射；按变化更新，不按顶点重复完整 PBR 属性 |
| BLAS | 按区段建立一份 geometry；首版 Voxy 区段使用 any-hit 表达混合材质及精确近景遮罩，不改变其他世界几何的 opaque 快速路径 |
| 命中程序 | 专用面属性读取器接入公共表面合同；相机、阴影、反射、连续折射和间接光使用同一已发布场景 |

compute 写入到 AS 构建读取、AS 构建到追踪之间建立正确依赖；scratch、位置、索引、压缩面、模型页及 BLAS 保留至实际最后引用完成。变更先生成新版本，完成后发布，已有帧保持旧版本；不以每次更新全局 idle 代替退休。仅改变颜色／biome 时不重建几何；边界、alpha 拓扑或模型形状发生变化时按实际依赖重建。

Java 不展开通用 PBR 顶点，也不逐帧重传全部远景；native 不先恢复完整通用顶点再转位置。这里消除的是重复生产与属性膨胀，compute 解码及 AS 构建仍然存在。程序化 AABB 求交可能省位置缓冲，但增加每次射线求交、材质与 ray query 适配成本，首轮不选，不能仅凭压缩率断言更快。

### 图集与预算

原 `RenderResourceReuse` 图集规格为 `16×3×256` 乘 `16×2×256` 个 RGBA8 像素，底层约 384 MiB，完整 mip 链约 512 MiB。模型表的逻辑最大容量不应强制变成实际纹理全量分配。

使用 Vulkan 按需分配的模型图集页和模型 ID 到页位置的目录，只上传已使用模型。每模型六张 16² RGBA8 底图约 6 KiB，完整 mip 约 8 KiB，尚不含 PBR 附图、对齐和 AS。先使用普通分页资源，不引入稀疏纹理扩展依赖。页复用必须等待在途帧与光源采样引用退休；模型 ID、纹理页和材质版本不能混同。

`ModelFactory` 自身最多编码 65,536 个模型 ID。GPU 页可回收不代表这些逻辑 ID 可以任意复用；达到来源上限必须明确报告并按完整模型 epoch 重建／替换，不能覆盖当前或在途模型。源表限额、GPU 预算和远景距离分别记录，不通过静默删面维持数字。

`RenderDataFactory` 每工作槽仅 quad scratch 已有约 4 MiB，此外还有体素与邻面数组，因此工作槽有固定上限且复用。预算同时覆盖任务数、CPU 字节、上传字节、scratch、BLAS、图集和在途旧版本；给交互近景及既有外部区段留出容量。容量不足保留有效版本并延期，不清空仍有效几何，不读全数据库进显存，也不根据相机遮挡丢弃仍在 PT 驻留集合中的内容。

## 独立驻留与近远景交接

不复用 GL 的主相机视锥／遮挡结果作为 PT 场景集合。驻留以显式远景距离和空间层级定义，所选代理对所有光线完整参与；相机转向不应令其反射或阴影消失。这是有限远景代理场景，不是把数据库全部细节常驻。

维护一个不重叠的八叉树 LOD cut；父节点只有在需要替代它的所有子节点已就绪或已确认空时才退出。本帧使用不可变选择快照，父子替代与资源版本原子发布，不在同一体积同时留下两层代理。

精确近景为 16³，Voxy 节点为 `32³×2^LOD`，不能按两个 RenderSection 是否相等决定交接：

1. 从 native **实际 GPU 完成并发布**的近景版本和明确空版本建立 `NearCoverageSnapshot`，不使用 Java 已加载／主相机可见列表。
2. 完全未覆盖的远节点保留正常快速路径；完全被近景接管的远节点退出；首版所有 Voxy 区段使用覆盖判定与 non-opaque，其他来源不变；按覆盖状态细分的快速路径尚未实现。
3. 混合节点的 any-hit 只拒绝被精确近景覆盖的命中区域；按整数单元、源面所有权及半开区间定义边界，避免直接 float floor 或任意 epsilon 产生缝隙。大面跨多个 16³ 单元仍要逐命中点区分。
4. 所有查询与远景发光采样使用同一覆盖快照。选中 cut 的真实发光面建立紧凑索引，采样实际投影颜色／染色与 alpha，以面积 PDF 和 BSDF MIS 接入 Vanilla PT／Advanced；不重建细致发光遮罩。近景覆盖的样本直接拒绝，父子 cut 不重叠；不混入 Advanced 的近景区块 ReSTIR 表。
5. 近景将卸载时，先准备远景替代；必要的最后有效近景引用保留到远景 GPU 版本可发布，再联合切换，不能先删后补。驻留预算包含这段有界重叠。

混合节点遮罩比沿边界切碎／重建全部远景 BLAS 更适合初版，但带来边界 any-hit 成本；未来是否换分组由重新授权后的测量决定。源 Voxy 有面微扩张和 LOD 近似，几何接缝仍须专项验证，不通过扩大覆盖或混合淡入掩盖。近景和远景世界版本并非天然同刻，改变时需同事务标记失效并联合发布，不能以旧代理覆盖新近景后声称完全一致。

## 材质与原有渲染语义

2026-10-18 使用者明确接受：Voxy 有意舍弃的部分 PBR、动态纹理和精度，本身属于 LOD，按 Voxy 的设计语义保留。本接入不追补这些源后端未保留的信息，不新增 PBR 附图、动画重烘或额外原始模型追踪作为完成前提。近景精确内容与其他渲染来源不因远景代理而降级；只把实际翻译错误列为缺陷，不把已接受的源 LOD 取舍写作待修。

| 项目 | 既有来源与接入合同 |
| --- | --- |
| 位置、UV、绕序 | 成套核对 `quad_format.glsl`、`quad_util.glsl`、`block_model.glsl` 与 `SharedIndexBuffer`；含缩进、轴交换、重复贴图、边界微扩张和 LOD 缩放 |
| 基础色与 biome | 保留未受光的烘焙颜色及 tint；不把光照表、方向明暗或既有环境亮度再乘入 PT 基础色 |
| cutout 与覆盖率 | 原非透明分支丢弃阈值、半透明分支零 alpha 判定与颜色混合不同；通过专用层合同映射，不凭八分组猜 alpha 语义 |
| 单／双面及水 | 遵守 [adr-1](adr-1-query_visibility.md)的直接成像／传光区分；成对薄片一次界面、水面数值自命中与船内排除域保留，不能只套 GL 背面剔除 |
| PBR | 沿用 Voxy 代理已提供的颜色／深度与材质分类，不反推源已舍弃的 normal、roughness、metalness 或折射细节，不要求原始模型等价 |
| 发光与受光 | 读取 Voxy 明确保留的相关来源信息，映射到 Radiance 代理材质；不以环境 packed light 冒充物理发光，不重复烘入光照表。显示全亮时仍遵守 [adr-6](adr-6-fullbright_transport.md)的世界照明贡献要求 |
| 动态纹理 | 按 Voxy 实际快照和重新烘焙时机处理；若来源有意保留静态投影，不为恢复原版动画逐 tick 重烘模型。资源重载和来源实际更新仍正确失效 |
| 多层与程序化模型 | 保留 Voxy 六向投影的合并结果与近似，不拆回来源已经舍弃的多层拓扑；验证的是与 Voxy 输出一致，而非原 BakedQuad 完全等价 |
| 独立对象 | 实体、BER、粒子、Flywheel、Sable 动态结构继续走原路径，Voxy 不是它们的远景副本 |

CPU atlas 快照由 `VoxyAtlasSnapshot` 在真实 NativeImage 所有权任务内维护，保留来源的快照／重载合同；只镜像当前 block atlas，第 0 层最多 64M 像素，不为普通纹理或每次上传保留无限副本。纹理 owner 代际不匹配的旧上传不影响当前图集。

源 `ModelEntry` 以六张颜色／深度图、流体状态 ID 和 tint 颜色去重；不同原始 PBR 来源可能共用代理 ID。保留投影去重与网格合并，但在归并前附加来源尚存在的流体、alpha 类别和真实发光；生物群系相关的模型保留来源状态，防止不同染色提供者错误共用一个表。未染色与常量染色模型仍可合并；不恢复已经舍弃的 PBR 或动画。玻璃按 NeoForge 玻璃标签分类，不使用贴图名称白名单。

仍需验证代理本身的颜色、cutout、半透明、tint、流体分类和查询参与是否正确接入。源有意丢失动画／PBR 可以接受，不等于可以额外丢失源仍保留的 alpha、边界或更新；也不能拿近景回退冒称远处已接通。

### 光源、水面与 Iris 路径的静态结论

后续按使用者要求核对了[完整来源流程](../research/survey-voxy_lighting_water_iris.md)。Voxy 自身 emission 等级仍在 CPU metadata，而最终面的 light 已混合环境／自身亮度；适配层单独导出自身发光和来源流体分类，不能从高亮面反推光源。发光代理通过实际命中和间接传光影响世界，受同一近远景／LOD 覆盖限制；0–15 来源等级沿用项目既有映射，不新定物理单位。独立远景面积光采样已接入两套内建 PT；是否实际建立候选由运行计数与功能证据确认，不把它称为 ReSTIR 复用或实测性能收益。

水与含水方块已有专用网格和 fluid LUT；普通 Voxy 后端使用 alpha 混合，没有内建物理折射。保留其简化网格及静态投影，以实际流体来源接入 Radiance 既有表面和界面规则，不把整个透明组当水。远景水面直接复用对应 PT 的 Radiance 水体颜色、光学参数、动态法线和时间设置，以世界坐标采样；来源 alpha 继续承担有效表面覆盖，不能为消除颜色差异填满代理孔洞。来源烘焙的原版水 RGB 不参与共同水材质，Advanced 的侧面与缓存重建也使用同一光学基底。不另建远景水面算法或重建详细几何，不恢复来源已舍弃的 PBR。

Iris 通过 shaderpack patch 将未受光颜色、UV、face、lightmap、tint 与自定义 ID 分开输出，值得借鉴其数据分层。实现使用 Radiance 自有 surface 输入，不加载其 GL 管线，不把 customId 当统一材质枚举，不把“阅读 Iris 兼容路径”扩大成实现整套 Iris 兼容。ray query 与光源采样也要读取同一代理属性，不能只接 ray-tracing hit group。

## 历史、发布与更新复杂度

世界准备仅追加持久远景实例引用；未改变时不重建模型、压缩面或 BLAS，不逐帧从 Voxy 存储复制完整体素表。TLAS 构建仍受当前世界场景机制约束，不宣称静态帧完全没有实例组织成本。

相机只影响当前 TLAS 相对变换及视图参数，局部位置留在节点坐标；整数 section 原点先做精确差值，再转换浮点，不在本轮更改全局渲染原点。历史身份包含 session、section、模型与拓扑版本；LOD、材质或覆盖切换分别正确重置受影响历史和复用状态，不用旧 primitiveID 读取新拓扑。静态场景缓存、SBT 内容复用和 in-flight 引用继续使用实际 native 版本，不能借用 GL node ID。

## 验证门槛与未决项

首轮先验证“无 GL 数据后端＋一个已知压缩区段”，再接完整驻留和近远景交接。自动验证须经过实际 adapter／生成入口：缺失模型、中心／邻区变化、未知与明确空、旧 epoch 拒收、队列拒绝、异常退出、reload 和有限退休均有行为反例。GPU 解码与 CPU 参考对照所有方向、尺寸、负坐标、LOD、cutout／水及材质身份。

Vanilla PT 与 Advanced 均需实际命中验证，不能只登记一个新 hit group 名称便认为所有管线兼容。当前只允许内建 Vanilla PT／Advanced 的压缩属性解码器；选择其他着色器包时明确暂停远景并保留近景，不将紧凑面地址交给未适配的通用顶点读取器。Native 七项计数分别是驻留节点、选中节点、模型数、驻留几何字节、CPU 已记录构建、发光面和发光组数；Extra 追加生成／丢弃／忽略接收光／预算拒收／待提交及材质类别计数；它们不能证明 GPU 完成、传输总字节或总显存，也不是性能数据。未实现的细分计数不代填。

运行门槛包括近远景重叠／洞、父子 LOD 切换、相机外反射和阴影、连续折射、源静态／动画取舍、资源重载、世界／维度切换及菜单后资源回落；日志证明 GL 创建未发生、存储与摄入实际推进。构建、GPU 测试、短时运行与使用者视觉反馈分别记录。本轮运行项目内功能用例，不运行性能实验；实际结果以实施记录为准。

完整查询参与指被选取的 Voxy 代理共同参与 PT，并不承诺恢复远处原始细节；使用者已接受这一 LOD 边界。其源码许可禁止再分发，首轮仅通过外部 JAR 的接口和 Radiance 条件 Mixin 互操作，不复制其实现、不内嵌 JAR；原生运行库公开许可门禁保持不变。无法验证的第三方回调线程、ABI 或资源寿命先拒绝该兼容路径并清楚报告，不新建第二套图形 context。

## 更新与预算

每个节点至多有一份排队、构建或待提交版本；变化请求合并，过时结果退休。预算拒收的结果保留紧凑输入，容量未减少时不重新分配或重建；一次最多保留四份待提交、四份完成结果和一个工作中网格。每帧按展开字节预算提交，单个大节点允许原子完成，不能切断来源拓扑。未知存储保持未知，以有界缺席集合等待来源事件，不占有有效几何驻留身份。

来源快照去掉高八位接收光，使无消费者的 lightmap 不再参与合面；体素身份和生物群系仍完整保留。主线程不等待整段网格生成，模型写入仍与后台读取互斥。几何版本与位置缓冲决定运动历史，颜色表、近景覆盖或无关节点变化不再清空全部远景位置历史。

覆盖表、发光索引与模型表分别判变。模型或远景 cut 更新可以重建 header／光源索引并复用近景覆盖，不重新扫描整个近景场景。几何预算不代表全部显存：纹理页、模型／染色表、灯光索引、近景交接与在途旧版本均有各自所有权和额外成本。

`WorldUBO.lodGeometry` 是完整 GPU 场景表的几何索引加一，零表示没有远景；取自与 TLAS 相同的全局游标。CPU 临时数组不含稍后插入的静态目录，不能用其长度代替该索引。
