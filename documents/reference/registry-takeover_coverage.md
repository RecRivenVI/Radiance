# 绘制接管能力与来源覆盖表

本表是当前 `1.21.1-neoforge` 的工程登记，服务于[接管计划](../project/plan-render_takeover.md)。来源版本由 [Target 事实](../../versions/1.21.1-neoforge/target.properties)与[依赖登记](dependency-rendering.md)定义，不在表内重复版本。静态实现、实际运行和使用者画面验收分别记录；没有在样板中出现的来源不据此删除。

范围与完成门槛按 [adr-11](../design/adr-11-complete_drawing_scope.md)：当前游戏版本，以及已纳入依赖在各自已验证版本范围内的所有绘制。兼容范围与证明方式从[依赖登记](dependency-rendering.md#兼容范围与证明方式)取值；范围外版本／未登记修改自动退回，不能仅按安装模组名单放行。

本表按绘制能力列归并同类实现，行是薄来源适配器与核销批次，不是各建一套渲染器。最终每项必须登记直接接管、Radiance Vulkan 原样光栅或经批准差异；下面的捕获、待迁移和未知属于当前迁移状态，尚未满足全范围完成条件。最终巡检要求无负责方 0、无未知，具体路线、计数口径和阶段排期见计划；没有运行的项目不填通过。

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
| 物品框／`ItemRendererMixins`、`BlockModelRendererMixins` | 已发布烘焙模型、原版 override 选择和内置物品直接；有效地图及未登记自定义模型捕获 | 每层实际状态；已核对工厂的静态状态直接 | tint／light／overlay、foil 与轮廓保留；发光物品框作用域随输入保存 | 实例、层顺序、世界所有权直接 | 纹理所有者／重载直接 | 不适用 |
| 绘画／`PaintingRendererMixins` | 局部网格直接 | 世界；逐格光照及面状态保留 | 前后贴图与厚度直接 | 独立实例／历史直接 | 资源 epoch 与 GPU 退休直接 | 不适用 |
| 原版部件实体／BER／`EntityProxy`、`BoundPartCapture` | 符合资格的来源提取原动画／姿态并直接提交局部部件；条件退回逐项登记 | 实际状态；静态状态另作资格检查 | 眼睛／装甲／装饰／foil 按部件预绑定；滚动 UV 与非均匀变换可重放局部模板，不冒充全部 native 实例化 | 世界／优先级所有权保留；第一人称完整状态提取仍有残留 | 帧所有者、历史、退休直接 | 不适用 |
| 原版粒子／程序化／`EntityProxy.queueParticleRebuild`、`ParticleQuadTakeover` | 普通粒子参数直接；双面、颜色和生命周期前置计算已与原 render 对照；线请求和局部过程图元共用执行器 | 来源实际状态、程序和世界目标；线物理厚度为既定例外 | 裁切、alpha、颜色、光照／发光保留；CUSTOM 嵌套绘制仍执行原回调 | 完整世界 PT；custom／拾取模型同一网格后端，外层替代未完成 | 对象历史、纹理代际、重载与完成退休 | 不适用 |
| GUI／HUD／字体／`RasterDrawBridge`、`FontSet` | Vulkan 原样光栅 | 目标、viewport/scissor、深度／模板、混合与颜色写入 | 原版 shader、字形、颜色及层顺序；任意修改程序另行核销 | 附件清除、采样、后续叠加、读回即时依赖与请求计数 | 字体页、排队批次、重载直接 | 最终 GUI 合成；[96 号输入／暂停修复](../research/investigation-gui_frame_generation.md)完成限定回归，实际生成帧及严格绕过 FG 未验证 |
| 加载器／`RadianceLoadingOverlay`、SERVICE 引导 | 早期绘制原有 Vulkan 执行 | 早期窗口与加载过渡直接 | 淡出为原来源语义 | 唯一设备交接与 SERVICE 请求／消费计数 | 初始化失败／关闭直接 | GAME 世界融合与唯一呈现负责方 |
| Create／Flywheel／`RadianceEngine`、Visual 接口 | 共享模型／实例直接；BER 捕获 | embedding、crumbling 与实际层状态 | 实例 shader 已适配；剩余程序未知 | update／delete、嵌套 embedding 直接 | changed／deleted／重载直接 | 不适用 |
| Catnip／Outliner／Ponder 默认 | 缓存模型／真实线／模型边框捕获 | GUI 光栅与世界 PT 分开 | 真实材质捕获；纸张及非像素化为批准例外 | Ponder 默认离屏；不恢复 Ponder PT | 已有缓存与完成退休 | GUI 合成，公共请求待迁移 |
| Sable／`SableSubLevelBridge` | 区段直接，实体／粒子捕获 | renderPose 与内外坐标直接 | 表面、光照偏好与覆盖直接 | 子世界所有权／变换／历史直接 | 外部区段代际／退休直接 | 不适用 |
| Simulated／航空学 | 刚性部件捕获；柔性弹簧／绳索真实形变捕获 | 通用线空间合同直接 | cutout／受力染色／成对面直接 | 世界与结构图解分开；直接形变待迁移 | 帧快照与退休直接 | 不适用 |
| Veil／Necromancer／Quasar | 事件线直接；其余程序化捕获 | WORLD_RASTER 权限与合法屏幕效果分开 | 已知 shader 翻译；任意程序未知 | FBO／事件依赖捕获，公共合同待迁移 | 实际资源代际／退休 | 屏幕覆盖与世界区分 |
| Voxy／`VoxyPtSession`、`VoxySections` | 紧凑 LOD 面直接，无通用顶点展开 | 根覆盖／驻留／构建容量分别有界 | 来源软件烘焙、颜色、流体；Advanced 灯光待闭合 | 完整 cut／近远景交接，lease 与 dirty revision | 不可变节点／代际／GPU 引用退休 | 不适用 |

同一格的“直接”与“捕获”可以同时存在，分别对应显式列出的入口；不将二者合并为来源已全部接管。特殊第三方 renderer、外部 Mixin 对原方法的改写和未登记的动态模型保持未知或捕获，不从类名推断几何稳定。

## 样板的原始语义基准

| 项 | 权威生产位置与输入 | 接管要求／既定差异 |
| --- | --- | --- |
| 物品框体 | `ItemFrameRenderer.render` 的模型选择、姿态及 `RenderItemInFrameEvent` | 保留可见部件、事件取消、物品旋转和 map 分支；不跳过事件 |
| 框内物品 | `ItemRenderer.getModel/render`、render passes/types、`renderModelLists/renderQuadList` | 保留 override、tint 回调次序、alpha、foil、light、overlay；稳定模型只跳过重复局部展开 |
| 框体模型 | `ModelBlockRenderer.renderModel/renderQuadList` | tint 只影响有 tintIndex 的面；clamp 与 existingColor 语义和物品不同 |
| 绘画 | `PaintingRenderer.renderPainting`、`TextureAtlasSprite` | 保留前后、边厚、每格 light 查询与方向；来源被其他 Mixin 修改时不得直接假定稳定 |
| 地形 | `ChunkProxy` 准入／快照／附加 renderer → native 批次与 `Chunk1::enqueue` | 变化期间保留旧合法几何；只发布当前代际、当前 revision 的完成版本；相邻方块与水面不能缺失 |
| 世界与 GUI | `RasterPreviewScope`、`DrawTargetContract`、原始附件采样顺序 | 世界不后置光栅；GUI 与屏幕效果有独立域；本阶段不更改 SDK 呈现 |

光照、全亮实际发光、世界线物理厚度、不透明线和查询所有权等已经批准的差异，唯一决定入口仍是[语义决策索引](registry-semantic_decisions.md)。不将 PT 随机光照与原版最终 RGB 逐像素相等作为来源语义的定义。未批准的拓扑、颜色、覆盖、状态和时序差异仍是缺陷。

## 证据与未完成项

### 阶段 2（原阶段 6）的原版来源结论

153 个注册来源已通过独立原 renderer 与替代请求对照；155 项中另两项保留原执行，见下表。此数字只描述已运行的具体状态；普通末影龙通过不包含死亡光束，物品框通过不包含有效地图。原始逐项输入和实际替代判据读取 `validations/conformance-renderer/result/replacement-20261020-235152/entity-takeover.json` 的 `sourceRegistry[].replacement`，不能把其中为阶段 3 保留的 `route` 字段当成当前 renderer 判据。

| 当前路径 | 执行方式 | 对照与边界 |
| --- | --- | --- |
| 原版 living／玩家、普通末影龙、刚性实体和 BER | 对象状态、原版动画数学、显式根和局部部件资源；绕过原 renderer／ModelPart 遍历 | 玩家皮肤、非均匀幼年、受击、装备、可见性、模型切换及事件中替换模型均有实际对照；非均匀输出重放局部模板，尚非全面 native 实例化 |
| 眼睛、盔甲／trim、装饰、foil、能量层、披风、插箭／蜂刺、激流及手持物 | 显式有序部件／模型层绑定；同族执行器共用 | UV、颜色、覆盖与发光按原版输入比较；不变绑定资源复用，变化外观不冻结 |
| 烘焙及原版内置世界物品 | 原 override 和相机状态求值、显式模型请求；原装饰与材质继承 | 285 项世界物品组合；地图和未登记自定义扩展不在该结论内 |
| 普通与特殊四边形粒子 | 原参数数学、共享 billboard／旋转请求，不执行原 render | 109 种注册类型核销，实际可见面与原 render 对照；振动／尖啸双面、颜色过渡及烟花闪烁另有覆盖 |
| 光束、闪电、钓鱼线、展示实体、名称牌／标牌 | 状态到同族过程定义或文字请求；局部形状输入版本复用 | 原始顶点、层顺序和材料比较；文字仍经现有 Font 字形执行器，不属于常驻字形实例 |
| 未登记来源变换 | 按 adr-9 执行修改后的原路径 | 资格后事件改模型有反例；不通过模组名或扩大允许名单放行 |

### 逐项保留的原版执行路径

下列是实际残留，不写成“全部原版 renderer 已消失”。保留原版行为优先于用不等价的实体光照、缓存最后顶点或改变拓扑补足数量。

| 来源／状态 | 当前负责方与无法替代的具体原因 |
| --- | --- |
| `ber/minecraft:piston` | 原 `PistonHeadRenderer` 和世界方块细分；包含邻域遮挡、AO、光照、ModelData 与 NeoForge 活塞扩展回调。现有实体烘焙请求不具有同一输入合同，不能直接替换 |
| `entity/minecraft:falling_block` | 原下落方块的世界位置、状态、邻域和方块模型细分；现有物品／实体固定光照请求不等价 |
| 末影龙死亡、末影龙连接水晶，以及 EndCrystal 的 beamTarget 非空 | 原动态光束／死亡射线分支；普通模型对照没有证明这些不同拓扑／层的请求，资格主动保留整条原分支 |
| 有效地图物品框 | 原 MapRenderer 的动态地图纹理、装饰和字体；普通框与标准物品的直接请求不覆盖此依赖 |
| 第一人称 `queueHandRebuild` | 原手臂、使用动作和单双手地图的私有状态计算仍执行，未迁入同一个完整参数合同；第三人称手持层通过不等于第一人称执行已替代 |
| CUSTOM `MobAppearanceParticle`、`ItemPickupParticle` | 原嵌套实体模型、独立 buffer flush、发光作用域与历史所有权仍执行；普通四边形参数接口不包含这些依赖 |
| fire／leash／debug hitbox 等额外来源条件 | 来源资格保留原派发，以免遗漏依赖的附加几何；未使用降低更新频率的方式绕过 |
| 破坏覆盖 `SheetedDecalTextureGenerator` | 原世界几何及贴花投影消费者，完整保持破坏阶段；尚无直接的同输入表达 |
| 非均匀姿态、非 identity UV、非标准消费者或局部／绑定缓存容量不足 | 新 renderer 已提取状态；局部模板仍逐顶点重放，保留原输出，不冒充零展开；转入当前阶段 3（原 7）逐原因归属与实例化 |
| Font 世界字形、模型布局外的动态自定义定义 | 文字布局／外层请求已替代，字形执行和未知自定义模型仍由已知完整捕获路径负责 |

当前阶段 3 的[来源核销](../research/experiment-takeover_instancing.md)已把楼内 603 项逐帧对齐：局部版本的固定项数限制是主要模板重放原因。现改按字节预算驻留，并在原生以完整几何内容共享 BLAS；姿态、颜色、光照、材质代际与历史独立保留。最终楼内短窗剩 59 项动态输入，分别是船内排水控制体、世界字体与第一人称手臂／袖层；不把它们算作已持久化，也不把 TLAS 内容判变机制说成场景中必然可复用。非均匀姿态、滚动 UV、非标准消费者仍按上表保留精确模板重放。 指定三组配对与原版来源对照已完成，城市窗口保留 48 项动态输入；该冻结候选于 2026-10-22 获使用者目视验收通过；不扩张为严格 FG／GUI 分离或 GPU 开放项通过。

模型定义的稳定性依据原版 baker 的资源代际；部件列表／Cube 身份替换和模型更换可失效。任意第三方直接改写既有 Cube 内部浮点数组并不在“已验证原版不可变定义”的承诺内，需按第二节的来源修改合同适配。

### 已发现的来源修改者

按 [adr-10](../design/adr-10-vanilla_first_takeover.md) 区分实际接口与行为变化。除已验证的 Flywheel 接口外，本阶段不实现其他变体；版本或字节变化必须重新证明，不能仅依模组名放行。

| 修改者／实际版本 | 目标与变换记录 | 当前状态 |
| --- | --- | --- |
| Flywheel 1.0.6（Create 内嵌） | `ModelPartAccessor`；children getter 与 compile invoker，实际类 SHA-256 `4ba21b44859748e48364c7df927b317c897b96710394c121f944c3887b17ab32` | 只增加接口；精确上下文和字节登记，缺失或变化退回 |
| Veil 4.3.2（航空学内嵌） | `ModelManagerAccessor` | 已知修改，待翻译；当前烘焙来源退回 |
| Veil 4.3.2 | `RenderTypeMixin` 改写实际状态 | 已知修改，待翻译；不按普通工厂静态解释 |
| Sable 2.0.6 | `sable.mixins.json:respawn_point.sleeping.LivingEntityRendererMixin` | 已知修改，待翻译；相关实体生产者退回 |
| Create 6.0.10 | `create.mixins.json:client.PlayerRendererMixin` | 已定位先前未知的玩家修改，待翻译；玩家生产者退回 |

这些是本轮实际加载记录与嵌套类字节的事实，不把全体同模组类视为已证明兼容。

SOURCE 证据从表中的实际符号和[来源调查](../research/survey-render_takeover_sources.md)回查。前置 CPU/GPU 数值、重载和真实离屏依赖见[可行性实验](../research/experiment-render_takeover_feasibility.md)与[边界实验](../research/experiment-takeover_boundaries.md)。这些是旧骨架证据，不能替代本阶段候选的执行记录。

阶段 0～2 的新结果、产物、检查点与人工待验统一写入[计划结果](../project/plan-render_takeover.md#结果)和[当前状态](../project/status-current.md)。未迁移的八阶段来源、严格 FG 独立 GUI、设备丢失根因和长期稳定性继续保持未完成。

## 历史核销入口

阶段 3 的 104 项局部 Cube 输入对照与 51 项保留分类，仍保存于 `validations/conformance-entity_takeover/result/functional-20261020-011636/` 及[阶段 3 实验](../research/experiment-entity_takeover.md)。它们说明当时状态，不覆盖或否定阶段 6 的 renderer 替代结果。当前未替代来源以上表为准。
