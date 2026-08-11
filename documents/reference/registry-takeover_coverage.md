# 绘制接管能力与来源覆盖表

本表是当前 `1.21.1-neoforge` 的工程登记，服务于[接管计划](../project/plan-render_takeover.md)。来源版本由 [Target 事实](../../versions/1.21.1-neoforge/target.properties)与[依赖登记](dependency-rendering.md)定义，不在表内重复版本。静态实现、实际运行和使用者画面验收分别记录；没有在样板中出现的来源不据此删除。

范围与完成门槛按 [adr-11](../design/adr-11-complete_drawing_scope.md)：当前游戏版本，以及已纳入依赖在各自已验证版本范围内的所有绘制。兼容范围与证明方式从[依赖登记](dependency-rendering.md#兼容范围与证明方式)取值；范围外版本／未登记修改自动退回，不能仅按安装模组名单放行。

本表按绘制能力列归并同类实现，行是薄来源适配器与核销批次，不是各建一套渲染器。最终每项必须登记直接接管、Radiance Vulkan 原样光栅或经批准差异；下面的捕获、待迁移和未知属于当前迁移状态，尚未满足全范围完成条件。最终巡检要求无负责方 0、无未知，具体路线、计数口径和阶段排期见计划；没有运行的项目不填通过。

## 本轮接入边界

当前公共入口、CPU／Native 数据寿命与驻留边界统一见[架构说明](../design/architecture-native_renderer.md#java-接入平台与来源)。下表中所有第三方专项入口由同级独立仓库 `../Radiance-Extra/` 管理，不随本体内嵌；未安装 Extra 时仅保留本体标准绘制入口和有限捕获。搬迁不把“捕获”改称“直接”，也不将历史人工验收转记为本候选的验收。

## 状态词

- **直接**：已有资源或语义请求入口；不表示该来源所有能力都已迁移。
- **捕获**：仍执行来源 renderer，接收其实际状态和顶点。
- **待迁移**：已找到合法来源与消费者，尚未实现新的直接合同。
- **未知**：程序或依赖尚未证明，不能当作不适用。
- **不适用**：对应来源没有这项职责。

## 双轴登记

| 来源与入口 | 图元／数据 | 状态／目标 | 表面／程序 | 场景／依赖 | 生命周期 | 加载／呈现 |
| --- | --- | --- | --- | --- | --- | --- |
| 原版地形／`ChunkProxy`、`ChunkBuildDispatch` | 区段／流体的自有生成器与完成发布直接；纯原版没有附加回调发送者 | 世界区段，邻区依赖；子世界另有变换 | 原版 PBR、裁切与流体直接；自定义程序不在本次原版核销内 | GPU 完成发布；样板接入资源版本 | 代际／取消／退休直接 | 不适用 |
| 物品框／`ItemRendererMixins`、`BlockModelRendererMixins` | 已发布烘焙模型、原版 override 选择和内置物品直接；有效地图使用图像／装饰／标签请求；未登记自定义模型捕获 | 每层实际状态；已核对工厂的静态状态直接 | tint／light／overlay、foil 与轮廓保留；发光物品框作用域随输入保存 | 实例、层顺序、世界所有权直接 | 纹理所有者／重载直接 | 不适用 |
| 绘画／`PaintingRendererMixins` | 局部网格直接 | 世界不携带逐格 lightmap；光栅预览保留原查询 | 前后贴图与厚度直接 | 独立实例／历史直接 | 资源 epoch 与 GPU 退休直接 | 不适用 |
| 原版部件实体／BER／`EntityProxy`、`BoundPartCapture` | 来源维护稳定部件树、局部定义及绑定；动画字段变化后更新相应姿态；未知变体退回 | 实际状态；静态状态另作资格检查 | 眼睛／装甲／装饰／foil 按部件预绑定；统一来源颜色、light、overlay、UV 为实例属性，层差异仍保留；可表达的非均匀仿射直接 | 世界／优先级／第一人称历史域分开；手部动作数学保留，绘制叶子直接 | 来源删除、当前批次、重载及 GPU 退休分开 | 不适用 |
| 原版粒子／程序化／`EntityProxy.queueParticleRebuild`、`ParticleQuadTakeover` | 普通粒子参数直接；幻象与拾取粒子拥有嵌套请求；线请求和局部过程图元共用执行器 | 来源实际状态、程序和世界目标；线物理厚度为既定例外 | 裁切、alpha、真实颜色及明确发光保留；世界不以环境 packed light 推断发光 | 完整世界 PT；未知 CUSTOM 仍执行实际回调 | 对象历史、纹理代际、重载与完成退休 | 不适用 |
| GUI／HUD／字体／`RasterDraws`、`FontSet` | Vulkan 原样光栅 | 目标、viewport/scissor、深度／模板、混合与颜色写入 | 原版 shader、字形、颜色及层顺序；任意修改程序另行核销 | 附件清除、采样、后续叠加、读回即时依赖与请求计数 | 字体页、排队批次、重载直接 | 最终 GUI 合成；[96 号输入／暂停修复](../research/investigation-gui_frame_generation.md)完成限定回归，实际生成帧及严格绕过 FG 未验证 |
| 加载器／`RadianceLoadingOverlay`、SERVICE 引导 | 早期绘制原有 Vulkan 执行 | 早期窗口与加载过渡直接 | 淡出为原来源语义 | 唯一设备交接与 SERVICE 请求／消费计数 | 初始化失败／关闭直接 | GAME 世界融合与唯一呈现负责方 |
| Create／Flywheel／`RadianceEngine`、Visual 接口 | 共享模型／实例直接；BER 捕获 | embedding、crumbling 与实际层状态 | 实例 shader 已适配；剩余程序未知 | update／delete、嵌套 embedding 直接 | changed／deleted／重载直接 | 不适用 |
| Catnip／Outliner／Ponder 默认 | 缓存模型／真实线／模型边框捕获 | GUI 光栅与世界 PT 分开 | 真实材质捕获；纸张及非像素化为批准例外 | Ponder 默认离屏；不恢复 Ponder PT | 已有缓存与完成退休 | GUI 合成，公共请求待迁移 |
| Sable／`SableSubLevelBridge` | 区段直接；稳定单块共享局部定义与持久实例；符合资格的实体／BER 共用 Vanilla 解释；未知动态来源实时求值 | Sable 最终 renderPose；内含与跟随坐标分开，乘坐不重复跟随 | 表面、光照偏好与覆盖直接 | 成员共同空间／局部姿态／历史；捕获附件仍用原世界变换 | 共用区段流水线与关联交接，当前替代版本完成后发布及退休；连续视觉待验 | 不适用 |
| Simulated／航空学 | 刚性部件捕获；柔性弹簧／绳索真实形变捕获 | 通用线空间合同直接 | cutout／受力染色／成对面直接 | 世界与结构图解分开；直接形变待迁移 | 帧快照与退休直接 | 不适用 |
| Veil／Necromancer／Quasar | 事件线直接；其余程序化捕获 | WORLD_RASTER 权限与合法屏幕效果分开 | 已知 shader 翻译；任意程序未知 | FBO／事件依赖捕获，公共合同待迁移 | 实际资源代际／退休 | 屏幕覆盖与世界区分 |
| Voxy／`VoxyPtSession`、`LodScene` | 紧凑 LOD 面直接，无通用顶点展开 | 根覆盖／驻留／构建容量分别有界 | 来源软件烘焙、颜色、流体；Advanced 灯光待闭合 | 完整 cut／近远景交接，lease 与 dirty revision | 不可变节点／代际／GPU 引用退休 | 不适用 |

同一格的“直接”与“捕获”可以同时存在，分别对应显式列出的入口；不将二者合并为来源已全部接管。特殊第三方 renderer、外部 Mixin 对原方法的改写和未登记的动态模型保持未知或捕获，不从类名推断几何稳定。

## 样板的原始语义基准

| 项 | 权威生产位置与输入 | 接管要求／既定差异 |
| --- | --- | --- |
| 物品框体 | `ItemFrameRenderer.render` 的模型选择、姿态及 `RenderItemInFrameEvent` | 保留可见部件、事件取消、物品旋转和 map 分支；不跳过事件 |
| 框内物品 | `ItemRenderer.getModel/render`、render passes/types、`renderModelLists/renderQuadList` | 保留 override、tint 回调次序、alpha、foil、light、overlay；稳定模型只跳过重复局部展开 |
| 框体模型 | `ModelBlockRenderer.renderModel/renderQuadList` | tint 只影响有 tintIndex 的面；clamp 与 existingColor 语义和物品不同 |
| 绘画 | `PaintingRenderer.renderPainting`、`TextureAtlasSprite` | 保留前后、边厚与方向；世界不携带原逐格 lightmap，预览保留；来源被其他 Mixin 修改时不得直接假定稳定 |
| 地形 | `ChunkProxy` 准入／快照／附加 renderer → native 批次与 `Chunk1::enqueue` | 变化期间保留旧合法几何；只发布当前代际、当前 revision 的完成版本；相邻方块与水面不能缺失 |
| 世界与 GUI | `RasterPreviewScope`、`DrawTargetContract`、原始附件采样顺序 | 世界不后置光栅；GUI 与屏幕效果有独立域；本阶段不更改 SDK 呈现 |

光照、全亮实际发光、世界线物理厚度、不透明线和查询所有权等已经批准的差异，唯一决定入口仍是[语义决策索引](registry-semantic_decisions.md)。不将 PT 随机光照与原版最终 RGB 逐像素相等作为来源语义的定义。未批准的拓扑、颜色、覆盖、状态和时序差异仍是缺陷。

## 证据与未完成项

### 阶段 2（原阶段 6）的原版来源结论

阶段 2 冻结候选当时有 153 个注册来源通过独立原 renderer 对照，另两项保留原执行；以下表格是该历史证据的边界，不代表当前能力。原记录读取 `validations/conformance-renderer/result/replacement-20261020-235152/entity-takeover.json`。130 号的纯原版运行已对照全部 155 个注册来源，并另外覆盖死亡射线、目标光束、有效地图、第一人称及嵌套粒子；不能用这 155 个名字推导任意第三方修改者或任意状态均通过。

| 阶段 2 冻结路径 | 当时执行方式 | 当时对照与边界 |
| --- | --- | --- |
| 原版 living／玩家、普通末影龙、刚性实体和 BER | 对象状态、原版动画数学、显式根和局部部件资源；绕过原 renderer／ModelPart 遍历 | 玩家皮肤、非均匀幼年、受击、装备、可见性、模型切换及事件中替换模型均有实际对照；非均匀输出重放局部模板，尚非全面 native 实例化 |
| 眼睛、盔甲／trim、装饰、foil、能量层、披风、插箭／蜂刺、激流及手持物 | 显式有序部件／模型层绑定；同族执行器共用 | UV、颜色、覆盖与发光按原版输入比较；不变绑定资源复用，变化外观不冻结 |
| 烘焙及原版内置世界物品 | 原 override 和相机状态求值、显式模型请求；原装饰与材质继承 | 285 项世界物品组合；地图和未登记自定义扩展不在该结论内 |
| 普通与特殊四边形粒子 | 原参数数学、共享 billboard／旋转请求，不执行原 render | 109 种注册类型核销，实际可见面与原 render 对照；振动／尖啸双面、颜色过渡及烟花闪烁另有覆盖 |
| 光束、闪电、钓鱼线、展示实体、名称牌／标牌 | 状态到同族过程定义或文字请求；局部形状输入版本复用 | 原始顶点、层顺序和材料比较；文字仍经现有 Font 字形执行器，不属于常驻字形实例 |
| 未登记来源变换 | 按 adr-9 执行修改后的原路径 | 资格后事件改模型有反例；不通过模组名或扩大允许名单放行 |

### 逐项保留的原版执行路径

130 号将历史残留接到共同请求能力。下表区分已替代的完整 renderer、仍执行的原版算法与资格回退；代码存在、几何对照、运行进入和人工验收分别记录，不写成“所有原版函数已消失”。

| 来源／状态 | 当前负责方与保留内容 |
| --- | --- |
| `ber/minecraft:piston`、`entity/minecraft:falling_block` | `VanillaWorldBlockRequests` 负责来源定义，保留原位置、种子、分支和 NeoForge 回调；标准模型按实际世界查询依赖复用细分结果，未知模型仍完整细分 |
| 末影龙死亡／连接水晶、EndCrystal beamTarget | 显式模型层、八边形光束与参数请求；真实变化的死亡射线继续执行原 renderRays 算法，不使主体整条退回；死亡 1／80／190 和目标光束已有独立几何对照 |
| 有效地图、单／双手地图 | `VanillaMapRequests` 保留背景、动态图像、装饰及字体；像素上传和装饰回调照常执行。有效／变化地图有独立对照，第一人称动作单独验证 |
| 第一人称 `queueHandRebuild` | 保留手选择、装备／挥动／使用动作及 NeoForge 事件计算；手臂、袖层、物品和地图在叶子层接管。不能将调用动作函数误记为仍捕获完整原绘制 |
| CUSTOM 幻象／拾取粒子 | 粒子持有局部模型／嵌套实体请求，保持渐变 alpha、实际全亮、三刻轨迹和独立历史；原版嵌套算法的逐状态对照与用户体验分开 |
| fire／leash／debug hitbox | 独立执行来源附属算法和通用几何／线请求，主体继续直接接管；没有把附属动态几何伪装成不变资源 |
| 破坏覆盖 | 按进度对象维护模型及依赖，保留原 SheetedDecal 投影算法、阶段和 ModelData。非标准模型及消费者保留精确执行 |
| 非均匀姿态、滚动 UV、非标准消费者或驻留不足 | 合法逆转置法线的仿射直接实例化；统一属性独立。层内差异继续保存，无法证明的消费者及当帧资源预算不足时精确重放，不省略几何 |
| 世界 Font | 来源维护布局与有序字形页资源；字体页换代、文本／样式变化时重建，混淆字形实时。GUI 保持既有 Vulkan 光栅；未知自定义文字／模型执行仍捕获 |

历史阶段 3 的[来源核销](../research/experiment-takeover_instancing.md)曾把楼内 603 项逐帧对齐，确认固定项数限制导致模板重放，并改为字节预算与完整几何 BLAS 共享。该冻结候选的楼内短窗剩 59 项动态输入、城市窗口剩 48 项，2026-10-22 获使用者目视验收通过；这些历史计数不代表本轮候选。当前非均匀姿态、UV、世界字体及第一人称的路径以上表为准，严格 FG／GUI 与 GPU 开放项不由该历史验收覆盖。

模型定义的稳定性依据原版 baker 的资源代际；部件列表／Cube 身份替换和模型更换可失效。任意第三方直接改写既有 Cube 内部浮点数组并不在“已验证原版不可变定义”的承诺内，需按第二节的来源修改合同适配。

### 已发现的来源修改者

按 [adr-10](../design/adr-10-vanilla_first_takeover.md) 区分实际接口与行为变化。本轮只补齐共同来源所必需、已经逐类审阅的变体；版本或字节变化必须重新证明，不能仅依模组名放行。实际配对运行与仍未通过的边界见[本轮实验](../research/experiment-persistent_sources.md)。

| 修改者／实际版本 | 目标与变换记录 | 当前状态 |
| --- | --- | --- |
| Flywheel 1.0.6（Create 内嵌） | `ModelPartAccessor`；children getter 与 compile invoker，实际类 SHA-256 `4ba21b44859748e48364c7df927b317c897b96710394c121f944c3887b17ab32` | 只增加接口；精确上下文和字节登记，缺失或变化退回 |
| Veil 4.3.2（Sable 内嵌） | `ResourceModelManagerAccessor` | getter-only，登记实际类字节后允许共同模型入口 |
| Veil 4.3.2 | 两个 `RenderTypeMixin`、`CompositeStateMixin` | 按实际追加状态判断；已排除的动态缓冲可共用，未知追加状态执行完整来源路径 |
| Sable 2.0.6 | 睡眠、名称姿态、实体照明／可见性、牵引绳修改 | Extra 在原求值位置补齐睡眠／名称回调；原照明、剔除与牵引绳入口继续按实际用途执行，逐类字节登记 |
| Flywheel 1.0.6／Ponder 1.0.82 | ModelBlockRenderer 专用消费者钩子／ItemRenderer 纹理管理器访问器 | 前者仅准备来源自己的 MeshEmitter，后者只读；实际类字节哈希登记，未知版本仍退回 |
| Extra 图解缓存 | ModelBlockRenderer 缓存访问器 | 只暴露已有线程局部缓存供清理，实际类字节登记，不修改网格算法 |
| Create 6.0.10 | 字体／盔甲 accessor 与 `HumanoidArmorLayerMixin` | accessor 只增加入口；自定义盔甲继续执行原图层回调，未改写为普通盔甲 |
| Create 6.0.10 | `create.mixins.json:client.PlayerRendererMixin` | 已定位先前未知的玩家修改，待翻译；玩家生产者退回 |

这些是本轮实际加载记录与嵌套类字节的事实，不把全体同模组类视为已证明兼容。

SOURCE 证据从表中的实际符号和[来源调查](../research/survey-render_takeover_sources.md)回查。前置 CPU/GPU 数值、重载和真实离屏依赖见[可行性实验](../research/experiment-render_takeover_feasibility.md)与[边界实验](../research/experiment-takeover_boundaries.md)。这些是旧骨架证据，不能替代本阶段候选的执行记录。

阶段 0～2 的新结果、产物、检查点与人工待验统一写入[计划结果](../project/plan-render_takeover.md#结果)和[当前状态](../project/status-current.md)。未迁移的八阶段来源、严格 FG 独立 GUI、设备丢失根因和长期稳定性继续保持未完成。

## 历史核销入口

阶段 3 的 104 项局部 Cube 输入对照与 51 项保留分类，仍保存于 `validations/conformance-entity_takeover/result/functional-20261020-011636/` 及[阶段 3 实验](../research/experiment-entity_takeover.md)。它们说明当时状态，不覆盖或否定阶段 6 的 renderer 替代结果。当前未替代来源以上表为准。

## Sable 持久内容的共同交接

API 9 的区段、局部块模型与 Flywheel 实例共用发布门槛；真实装配、拆解、分离、合并、父结构消失和删除回归已执行。即时 BER 与任意第三方程序化内容仍按现有来源执行，不能扩大为所有绘制均已原子交接。证据和范围见[共同交接调查](../research/investigation-sable_visual_handoff.md)。
