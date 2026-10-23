# 渲染接管目标的源码与迁移入口调查

2026-10-19，本次在保存现有实现后，只读调查 Radiance 与各有界目标，支撑[长期接管决定](../design/adr-7-render_takeover.md)及[阶段计划](../project/plan-render_takeover.md)。没有实现新接管、修改第三方源码、下载依赖、启动客户端或执行性能实验。

## 问题

明确哪些生产者已经具有模型和实例生命周期，哪些仍每帧展开顶点，哪些能提供真实变化来源；区分可复用入口、尚缺证明的假设和应继续保留的执行语义。

## 方法

复用 `D:/Workspaces/References/minecraft-references/` 的留存源码、当前 Radiance 和只读 Voxy 工作区。证据位于 `validations/performance-scene_diagnosis/result/takeover-survey-20261019/`；`source-evidence.json` 按原始字节记录 54 个选定文件的路径、大小和 SHA-256，包含六份来源登记元数据，读取后再次核对内容。该清单不是全部第三方源码快照或 renderer 穷尽清单。

使用实际构建输入执行四组 `javap`，覆盖 Minecraft 模型／粒子 API、Sable dispatcher 字节码、Simulated 柔性弹簧和扭簧 Visual API，以及 Catnip SuperByteBuffer。输出保存在 `minecraft-api.txt`、`sable-api.txt`、`simulated-api.txt`、`catnip-super_byte_buffer.txt`；命令和返回码在证据清单中。没有把这些 API 核对写作运行验证。

### 来源身份与限制

| 目标 | 本轮实际依据 | 边界 |
| --- | --- | --- |
| Minecraft 1.21.1 / NeoForge 21.1.259 | 留存源码为 NeoForge 21.1.250 补丁产物；另读实际 `neoforge-21.1.259.jar` 的模型及粒子 API | 未证明全部补丁、Mixin 和 CoreMod 结果等价；迁移前需取得相应方法的当前实现 |
| Flywheel 1.0.6 | 留存 exact-source-jars，来源 JAR SHA-256 `c251479dea729a568bedee28bb68021276f4b49be0a5b6a9603501729d5dfbfe` | 仍需核对消费者的实例类型和自定义 shader 能力 |
| Create 6.0.10 | 标签 `mc1.21.1-6.0.10`，提交 `ac0c444d9828da3453ae8cc65338e8de063286fb` | Catnip 缓冲及具体 Visual/BER 必须分别登记，不能以 Create 名称认定全已覆盖 |
| Catnip / Ponder 1.0.82 | 实际内嵌 `ponder-neoforge-1.0.82+mc1.21.1.jar` 中 SuperByteBuffer 的 API 字节码 | 当前类型是接口；迁移须追到实际实现及调用方，不按旧版本的同名具体类设计 |
| Create Aeronautics / Simulated 1.3.2 | 留存开发快照 `e720946bb5e84d618d0f1aa47e1a19ab2adb5bd7`，实际内嵌 Simulated JAR API | 源码声明版本不等于实际组合运行字节码逐类一致 |
| Sable 2.0.6 | 实际 JAR `9c199157843bf953a47dff625fc5bb41c01a90538d35ad18e6996bbcb8a617c2`；留存源码为 2.0.5 `6966d2928340de7631abcecf8549904b877df0a8` | 2.0.5 只作结构参考，当前 API 核对不能代替完整 2.0.6 语义核对 |
| 内嵌 Veil 4.3.2 | 留存标签提交 `540ad3778c1a5dafe905420adb21cb05e459b5c4` 与现有适配器 | Necromancer、Quasar、事件和 GUI 分开，不声称所有 shader 均可达或已适配 |
| Voxy 0.2.20-beta+1.21.1-neoforge | `D:/Workspaces/Repositories/GitHub/RecRivenVI/voxy/` 当前脏工作区，HEAD `11a3799ca0561098c17aea18266381d218de9654` | 记录选定文件指纹，不新宣称整个工作区与既有 JAR 完全一致；未写入该仓库 |

## 发现

### 当前实现已具备可延伸的基础

| 当前入口 | 已有能力 | 距离完整接管的差距 |
| --- | --- | --- |
| `EntityProxy` / `StorageVertexConsumerProvider` | 实际材质、所有者、普通／优先级／附加层及完整捕获 | 许多 renderer 仍创建 provider 并输出最终顶点；不是可直接删除的无用回调 |
| `RigidModelCapture.render` | 限定 SimpleBakedModel 复用局部网格、BLAS 和历史 | 原模型列表回调及 recipe 比较继续逐帧执行；`RECENT` 只留每模型一个候选，实际失配原因未分项测量 |
| `PaintingModelCapture` | 确认稳定来源的完整绘画几何常驻，保留逐格光照 | 每次仍捕获材质；光照进入几何身份，尚未成为统一实例属性合同 |
| `PartModelCapture` / `ModelPartPersistentMixins` | 可截获 `ModelPart.compile` 并保存局部几何 | 默认关闭，仍比较可变 cube 快照，light/overlay/color 进入缓存身份；不能当成全部骨骼接管 |
| `RadianceFlywheelEngine` | 实现 Engine，持有模型、实例，消费 changed/deleted 与 embedding | 仍遍历 handle；支持的实例 shader 有明确适配集合，不能自动代表所有 Create／航空学绘制 |
| `SableSubLevelBridge` / `SableBufferBridge` | 外部区段、插值姿态、持久缓冲范围上传 | 仍依赖 Sable 的多种 renderer 数据和普通捕获；各子来源的所有权应显式登记 |
| `VoxyPtSession` / `VoxyNative` | 世界 lease、dirty、紧凑网格、GPU 解码、持久远景资源 | LOD 覆盖、驻留、待构建与发布容量合同未闭合；完整视觉、部分光源边界仍未证明 |
| native `Entities` / 现有实例与场景代码 | 持久资源、按完成退休、来源面规则、SBT 和静态快照复用 | 新接口应沿这些生命周期演进，不能另建一套只按帧数释放的缓存 |

### 原版模型与粒子：适合提前提交状态，但没有通用 dirty 合同

`ModelPart` 的位置、角度、缩放、visible 和 skipDraw 为可直接修改的字段；实际 21.1.259 API 同样如此。仅拦截 `setPos`／`setRotation` 不足以宣称姿态变化全覆盖。可在原动画计算后按帧读取部件状态，几何在模型创建／烘焙和结构变化时登记。

`ItemRenderer.renderModelLists` 和 `ModelBlockRenderer.renderModel` 已有持久入口，但仍执行来源回调。下一步应明确模型选择、tint、light、overlay、foil、材质和附加层的依赖，把可证明稳定的模型注册与本帧状态分开；不能仅以 ItemStack 未变或模型对象相同取消所有处理。

`SingleQuadParticle.render` 根据本帧相机、partialTick、roll、尺寸、颜色、alpha、sprite 与光照生成四边形。可表达为共享基础面与实例参数；自定义 render、非四边形、轨迹和程序化粒子属于其他合同。相机朝向是来源语义，进入 PT 不代表擅自改成世界固定面。

### Create、Flywheel 与航空学不能按模组名整批替换

Flywheel 的 `Visual.update/delete`、`InstanceHandle.setChanged/setDeleted` 和独立模型／mesh 是明确入口。Create 的 `SingleAxisRotatingVisual` 创建 rotating instance，更新、受力、光照和销毁分别有方法；`ContraptionVisual` 另持有结构模型、子 Visual、actor 及 embedding。应复用这些状态，不恢复其相机遮挡剔除来决定 PT 场景是否存在。

Simulated 的 `TorsionSpringRenderer` 在 Flywheel visualization 可用时退出，而 `TorsionSpringVisual` 使用 rotating/oriented 实例并按 `beginFrame(partialTick)` 更新。它与柔性弹簧不是同一生产机制，接管时必须避免 BER 与 Visual 重复提交。

实际 Catnip `SuperByteBuffer` 接口独立表达 transform、color、overlay、light、level light 和 scrolling UV，`renderInto` 消费 PoseStack/VertexConsumer。它位于当前内嵌 Ponder JAR；这些入口适合资源／实例拆分，但仅接口核对不能证明具体实现内部未重算顶点，也不构成恢复 Ponder PT 的理由。

`SpringRenderer` 计算真实曲线、分段、UV 和受力颜色，并双次发射表面；这是形变输入，不能只保留一个刚性矩阵。`RopeConnectorRenderer` 又分为 `RopeStrandRenderer` 与 knot 的 CachedBuffers。拉杆、绳索、手杖及结构图解应按实际底层来源拆分，不能将所有“线状物”都套进线图元合同。

结构图解的来源内容选择、渲染偏好和 presentation 独立于世界。沿用既有决策：取消低分辨率像素格与帧率限制，保留纸张配色和淡出；世界接管不能越过这个 UI 边界。

### Voxy 暴露了接管需要闭合的预算合同

本次 09:35:26 的 Prism 崩溃为 `Publish Voxy LOD cut: Voxy selection budget`，投放 JAR 身份已核对；10 月 18 日报告有相同异常，但旧报告产物身份未由本次重新确认。证据在 `../painting-20261019/incident-20261019-093527/` 相对于本轮证据目录的位置。

`VoxyPtSession.frame` 按来源距离加入请求 roots，`request` 限制实际节点驻留；二者是不同集合。JNI 与 `validateCut` 同时把 roots 和 cut 限为 8192。当前设置的 `section_render_distance=64` 经现有代码换算为 32768 方块，覆盖请求可以超过该限制。日志没有实际数组长度，不能声称已测得精确超限值。

世界全部维度保存与 Voxy worker 停止／lease 归还已有日志；没有设备丢失字符串，不把此异常当作 GPU 根因，也不把 lease 释放当作 native GPU close 的完成证据。使用者决定先禁用 Voxy 继续验收；本轮没有替换或启动实例，未获得其后新的视觉结果。

修复前必须分开定义请求覆盖、驻留、pending、有效 cut、上传和在途预算。不能简单截断 roots/cut、吞异常或无限提高上限；父子 LOD 与近景覆盖应共同发布完整版本。Voxy 的 `WorldEngine.setDirtyCallback` 是单个回调入口，复用时仍要保持原所有权和并发边界。

### 现有数据决定首先迁移什么

本轮绘画开启后的独立 TIMED 归因窗仍约 922 次材质捕获/帧，包含约 3.91 ms，其中物品框约 587 次／2.49 ms。物品框两个烘焙入口自身合计约 1.52 ms；BER 自身约 2.28 ms，次数不能冒充实际产出。元数据仍约 15.05 MB 逻辑上传/帧。阶段预算、嵌套和最终构件边界见[绘画实验](experiment-painting_persistence.md)。

这些数值支持首先完成原版高频刚性来源及其材质提交合同，不支持先重写所有粒子或增加拓扑线程。它们不是当前 Prism 实时数据，也不证明下一次接管必然省下整段时间。

## 结论

新目标可沿现有持久资源和显式后端推进，不需要从零替换整个 native engine。首个闭合样板选择物品展示框的模型／外观链，随后迁移部件、Flywheel/Create、航空学/Sable、粒子与 Voxy；Voxy 当前容量故障作为独立前置阻塞先安排修复。

仍需补齐实际注册 renderer、事件、材质、消费者与场景覆盖表，尤其 21.1.259 补丁结果、Sable 2.0.6 全部相关实现、Catnip 非 Visual 路径、自定义 Veil 程序及当前组合的 Mixin 影响。本次是入口与架构研究，不宣称全目标审查完成、产品实现或新的运行验证。
