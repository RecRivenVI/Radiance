# 世界线条接管与旋转路径盘点

调查日期：2026-10-16。本次按使用者要求先交付整理前的静态盘点，未修改产品、着色器或第三方仓库，未启动游戏或性能实验。结论是线段展开已有共用实现，但来源接入、宽度与姿态合同尚未统一；支持某种顶点格式不等于所有来源已经接通。

## 问题

使用者确认上一候选的主菜单淡入与 sublevel 线条旋转验收通过，并要求先列出已接管、未接通、语义不完整及已有旋转修复，再实施统一。Create/Catnip 用 QUADS 构建的选中框、粗线模型与轮廓面不纳入线图元整理；不能只凭名称含 `Line` 或外观看起来像线将它们重新拆成线段。

验收候选 JAR 为 `6a26474b0c2e057a33ceacfd520034eb00de5561fd22e30a38e130e2a21388f6`，DLL 为 `6704b26420419a99990fae0c7dc3e4febfd72883f9eb2eb9639421f5ed6e863c`。本轮重新计算留存两文件的 SHA-256 一致；用户反馈证明其观察范围，不代填所有线条、PT 模式和历史效果通过，也不补写本轮实际加载模块证据。

## 方法

- Radiance HEAD 为 `72d2e4a3275e782418f59142e2f2ea1f01be4120`，读取当前脏工作树，包含必要未跟踪的捕获器与 native 截面助手；不将 HEAD 当作完整源码身份。
- 遍历现有参考库的 Minecraft 1.21.1、Create 6.0.10、Flywheel 1.0.6、Ponder 1.0.82、Sable 2.0.5、Sable Companion 1.6.0、Aeronautics bundled 1.3.2 与 Veil 4.3.2：共 11,541 个 Java 文件，100 个文件包含线段模式、宽度或形状辅助 API。后者包含模型宽度等误匹配，逐类判定，不作为“100 类线条”。
- 对当前登记的 Create、Sable 2.0.6、Aeronautics bundled 发行 JAR 及其内嵌 JAR 检查常量池：14 个 JAR、6,930 个类，15 个类引用目标线 API。常量池只证明引用存在，不证明调用在当前场景执行；也不证明没有反射、数字形式的 GL 枚举或程序生成的绘制。
- Minecraft 参考是 NeoForge 21.1.250 修补开发源码，不是未修补的原版源码。当前加载器新增调试入口另从缓存的 21.1.259 sources JAR 读取。Sable 参考为 2.0.5，实际 2.0.6 的相关类另外检查字节码引用；Aeronautics 参考来自已记录开发分支，实际发行类引用交叉核对。没有冒充所有方法逐字节相同。
- 证据目录为 `D:/Workspaces/Artifacts/Radiance/line-voxy-survey-20261016/`。`reference-lines.json` 保存命中行、读取文件哈希与参考来源；`artifact-lines.json` 保存实际包及类哈希、引用；`neoforge-sources.json` 固定加载器源码来源；`read-snapshot.json` 保存读取树的文件哈希和两仓库状态。脚本仅在此目录，不进入产品。

## 发现

### 已有底层合同

`StorageVertexConsumerProvider.getBuffer` 对 `LINES`、`LINE_STRIP`、`DEBUG_LINES`、`DEBUG_LINE_STRIP` 与 `POSITION_COLOR_NORMAL` / `POSITION_COLOR` 使用 `CapturedLineVertexConsumer`。每顶点保留位置、RGBA、原始 normal 与绘制基底；带矩阵的位置或带 Pose 的 normal 入口可以保留浮点基底，无矩阵的原始坐标不能恢复已丢失的局部姿态。

`EntityProxy` 经 JNI 发送来源格式 `CAPTURED_LINES=14` 与 owner frame；native `Entities::build` 使用 `line_geometry.hpp::segments/prism` 将线展开为方截面细棱柱。生成的三角面、颜色和场景引用共用后续 AS 路径。GPU 原始顶点转换不是这四类线段的展开入口。

这条链保留了端点方向和颜色插值，但目前不完整表达来源的线宽、屏幕覆盖、连接和所有状态：

| 属性 | 当前实际处理 | 整理时的边界 |
| --- | --- | --- |
| 长度 | 从变换后的端点计算；棱柱两端各外延半个宽度 | 原始端点长度与生成实体包围长度必须分开记录 |
| 宽度 | 调试路由读取 RenderType 声明宽度，再乘 0.0075；普通批次常用 0.0125；默认调试线为 2.5 × 0.0075 | 不是统一的原始宽度捕获；同一批次不能抹掉不同绘制的宽度 |
| 单位 | 目前明确采用世界空间厚度，未复刻窗口/距离补偿的像素宽度 | 这是既有表达选择，不将它误报为原版屏幕宽度完全等价；整理时不静默换政策 |
| 颜色 | 两端 RGBA 被保留，完全透明连接段被跳过 | 保留 alpha 不等于已证明原版混合/抗锯齿、渐变与 PT 结果等价 |
| 旋转 | 优先用逐顶点绘制基底；缺失时用 owner frame；模型优先级轮廓另读 normal 中的参考轴 | 三种职责仍并存，不能把来源切向与显式截面轴混用 |
| 缩放与连接 | 基底方向被归一化；独立闭合方棱柱，无专门折线接头 | 非均匀缩放/剪切、折线拐角和跨基底段仍需对应合同与行为回归 |
| 状态 | 专用入口携带 WORLD/优先级所有权及内容身份；调试内容统一自发光 | 不可把所有普通 BER 线都改成调试自发光或优先级线 |

`PipelineStateProxy.RasterizationState.setLineWidth` 连接 UI 光栅状态，但当前产品没有调用它的 Java 接管点。`EntityProxy.lineWidthOf` 只读取 RenderType 的声明值，并非实际 `RenderSystem.lineWidth` / 原始 GL 宽度变化的统一状态快照。不能因为 JNI 方法存在就声称该项已覆盖。

### 原版及加载器入口

“已接入”指静态生产者到消费者链存在，不代表本轮运行过该用例。

| 内容与原始入口 | 当前链路 | 状态与缺口 |
| --- | --- | --- |
| 方块选中框：`LevelRenderer.renderHitOutline/renderShape` | `queueTargetBlockOutlineRebuild`，保留 NeoForge highlight 事件与取消决定 | 已接入；WORLD 与 sublevel owner frame 已补；固定物理宽度 |
| 实体 F3+B 包围盒、眼高、观察向量，服务端调试向量 | `EntityRenderDispatcher` 捕获及 `finishDebugLineCapture` | 已接入；自体可见性单独保留，不应统一为全部可见 |
| F3+G：`ChunkBorderRenderer` 的 1/2 宽度折线 | `queueDebugGeometry` 按宽度分组，`DebugEmissionScope` 标记来源 | 已接入；Sable 设置可能替换生产内容，不应把其内容差异归因于截面生成 |
| 钓鱼线：`FishingHookRenderer` 的 `lineStrip` | 普通实体 Storage → 通用展开 | 已接入；当前普通实体批次固定 0.0125，未统一采用来源默认宽度 |
| 结构方块外框与不可见方块标记 | `StructureBlockRenderer` → BER Storage | 已接入；颜色保留，普通 BER 宽度合同仍不完整 |
| LevelRenderer 的 section path、section visibility 与捕获视锥边 | `queueLevelDebugGeometry` 调用原 `renderDebug` | 已有接入；section 图依赖原可见区段/遮挡图数据，不能据此认证 PT 自定义区段路径下内容完整 |
| 碰撞、邻居更新、结构、寻路、支撑面、振动范围、光区段、Breeze 调试线 | `DebugRenderer` 中的具体 renderer 与 shape/path 辅助方法 | API 与线段格式可接，但原版总入口默认只调用区块边界和 GameTest；未被调用的调试器不能记成默认可见，也不能自动认定是漏接 |
| NeoForge 粒子边界命令、BER 渲染边界命令 | 加载器事件监听器 → 全局 BufferSource | 通用事件线入口缺口；粒子入口有同步 endBatch，BER 入口另依赖后续 flush，不具备完整的事件内接收/排空保证 |
| F3 调试准星三轴：`GLX._renderCrosshair` | GUI 的 Tesselator/BufferUploader 光栅路径 | 屏幕 GUI，不进入世界 TLAS；需核对线宽状态，但不能当作遗漏的世界几何 |
| 拴绳、雷电、世界边界等外观狭长内容 | 原来源为带状/面图元 | 不是原版线图元，不纳入本次线段重写 |

### 第三方来源与明确遗漏

| 内容 | 真实生产者 | 当前判定 |
| --- | --- | --- |
| 油门拉杆活动轮廓 | Simulated `ThrottleLeverRenderer.renderOutline`，`RenderType.lines()` | 普通 BER 已接入；上一候选已修局部基底并获用户验收；普通 BER 固定宽度问题仍在 |
| 绳索交互外框 | Simulated `RopeStrandRenderer.renderOutline`，真实线段；绳索本体为模型 | BER Storage 可接入；每段方向已先写进端点，Pose 保留公共绘制基底；未证明每段独立截面朝向与模型截面完全一致 |
| sublevel 调试 AABB、plot、pivot；实体依附 sublevel 的调试框 | `SableSubLevelBridge.queueDebugBoxes` 与原 Sable mixin | 有独立世界/owner 框捕获；来源配置和触发条件不合并为全时显示 |
| Sable 加载区段网格 | `SableChunkDebugRenderer`，由 ChunkBorder mixin 替换原 F3+G | 位于已有 DebugRenderer 捕获链；不是未接管的新网格；保留配置原语义 |
| Create 轨道常规 highlight 与剪贴板框 | `TrackBlockOutline.drawCustomBlockSelection`、`ClipboardValueSettingsHandler`，真正 `RenderType.lines()` | 事件拿到专用 Storage，可接；固定事件宽度。与下述 Catnip 模型框不同 |
| Create 曲线轨道框、链式输送机交互框，含 Sable frogport 适配 | `ClientEvents.onRenderWorld` → `DefaultSuperRenderTypeBuffer` → 真实线图元 | 正常进入通用事件绘制时被 `WorldMeshSink` 拒绝；属于当前接收合同缺口，而不是未发现来源 |
| Veil 视锥、Quasar 锚点、发射器形状/方向调试线 | `DebugFrustumRenderer`、`AnchorPoint`、`ParticleEditorInspector.MutableParticleEmitter` | 实际内嵌发行类有引用；传入专用 Storage 时格式可接，普通世界事件路径仍缺口；编辑器是否启用、具体何时 flush 尚无运行证据，不声称当前玩家必然能触发全部 API |
| Flywheel | `GlPrimitive` 有 LINES/LINE_STRIP 枚举；本轮未找到同版本生产实例直接选择该枚举的确定来源 | 枚举存在不等于有一条漏画线；不能宣布 Flywheel 任意线模型均已接管 |
| Ponder/Catnip 选中框、AABB/Line/BlockCluster Outline | `PonderRenderTypes.outlineSolid/Translucent`，QUADS 模型 | 按要求排除；保留现有模型与淡出，不做线段二次实体化 |

明确缺口位于 `WorldMeshSink.submit`：其 `isSurfaceMode` 只允许 TRIANGLES、QUADS、TRIANGLE_STRIP、TRIANGLE_FAN。`submitCaptured` 已能结束 `CapturedLineVertexConsumer`，随后仍调用这个只接受面的接收器。`RenderTypeWorldSinkMixins` 对默认世界目标的拒收不会自动退回合法 PT 线段，而会报告丢弃；原始世界光栅绘制还有拒绝边界。不能将“捕获器能够 build”写成已接通。

### 已有旋转修复的分布

1. 原版方块 outline 与 Sable 调试框显式传 owner orientation，解决结构姿态；入口仍在 `EntityProxy` / `SableSubLevelBridge`。
2. 普通实体与变换后 BER 批次有 owner frame 回退，用于无逐顶点绘制姿态的来源。
3. `WorldOutlineGeometry.emitShapeEdges` 给模型优先级轮廓的 normal 编码部件局部参考轴；native 依 `radiance:priority/outline` 单独解释。这是派生模型轮廓，不是来源切向。
4. 上一候选新增 `CapturedLineVertexConsumer` 立即复制浮点 Pose 基底，native 四模式共用 `segments/prism`；实际解决了本次用户反馈的拉杆轮廓。原始切向仍是切向，不再据其猜测 roll。
5. 手杖波动束、皮搋子绳和 Catnip 粗线存在自身模型/带状几何变换修正；它们不是本次四类线图元，应只检查坐标合同，不合并拓扑。

### 整理方案的范围

建议建立同一份线提交记录：来源身份与顺序、局部/最终端点、两端 RGBA、原始宽度值和单位、线模式及分隔、绘制基底及其来源、世界/离屏目标、深度与查询所有权。姿态先在信息尚存时捕获，不能事后从端点唯一恢复绕轴角；不得新建 GL context 查询状态。

复用已有截面函数，先接通通用世界事件线的生命周期与 flush，再让专用入口按同一记录提交。普通线和调试线共享几何机制，但保留各自颜色、材质、透明度和成像身份。模型轮廓只统一其派生边的姿态接口，不把 Catnip 模型变成线。宽度不得继续只放在整个实体批次上；屏幕像素宽度与物理厚度的关系需明确记录，不能默默改变当前视觉。

回归应验证混合宽度/颜色、四种模式、事件同步与延迟 flush、同帧不同变换、镜像/非均匀缩放、无 Pose 回退、合法索引和异常清理。原有用户验收只覆盖反馈用例，不代替这些新增合同。

### 2026-10-16 范围收窄后的汇总与接管建议

使用者进一步明确：只看底层明确提交线图元的绘制请求，不考虑外观像线的模型。此限定用于后续实施；前面的来源调查保留为静态证据，不据类名、用途或所属模组扩大范围。

纳入 Minecraft 的四类线模式及实际出现的直接 GL 线图元请求；不纳入以 QUADS、TRIANGLES 等面图元提交的 Catnip 框、绳索本体、光束和带状模型。直接 GL 的 LINE_LOOP 等仅在有真实请求时纳入，不把 API 枚举存在写成当前活跃来源。三角形线框光栅状态也不是独立的线图元请求。

| 组别 | 纳入的明确线请求 | 当前结论 |
| --- | --- | --- |
| 原版常用世界线 | 方块选中框，F3+B 盒/眼高/方向，F3+G，钓鱼线，结构方块框 | 专用接入链已存在；宽度采集和统一提交仍需整理 |
| 原版其他调试线 | section path/visibility、视锥边，碰撞/路径/邻居/支撑/振动/结构/光区段/Breeze 调试 | 有实现和线模式；部分来源非默认执行，或依赖原区段图，不记成全部运行通过 |
| Sable 世界线 | sublevel 调试框、加载区段网格、依附实体调试线 | 已有接入与 owner 姿态修复；保留配置和原始可见性 |
| Simulated BER 线 | 油门拉杆活动轮廓、绳索交互外框 | 已接入，拉杆旋转用户验收通过；绳索本体不纳入 |
| Create highlight 线 | 普通轨道、剪贴板 | 专用 highlight Storage 已接入；不是 Catnip 模型框 |
| Create 世界事件线 | 曲线轨道、链式输送机及 Sable frogport 适配 | 通用世界接收合同缺口，需接通而非改为模型特化 |
| NeoForge 边界调试线 | 粒子与 BER 调试命令 | 通用接收和延迟 flush 边界不完整 |
| Veil 线 API | 视锥、锚点、发射器形状/方向 | 存在实际包引用；普通事件链缺口，编辑器触发及 flush 尚待运行证据 |
| 本地派生的明确线请求 | `StorageOutlineVertexConsumerProvider` 获取 `RenderType.lines()`，通过 `outlineDelegate` 发出部件边 | 纳入提交与截面定向统一；不扩大或重写其模型轮廓生产算法 |
| 屏幕线 | GUI 调试准星三轴 | 纳入线请求分类，继续由 UI 目标消费，不进入世界 TLAS |
| 未确认的其他来源 | Flywheel 线模式枚举、直接 GL 额外调用 | 尚无确定活跃生产者；不捏造已遗漏内容或默认支持结论 |

建议按以下边界整合，而非按模组建立新分支。

1. **在信息仍存在时捕获。** 复用 `CapturedLineVertexConsumer` 保存端点、两端 RGBA、绘制 Pose/基底；在 RenderType 状态生效和直接状态调用入口镜像实际宽度，在提交时冻结。不得在 native 工作线程读取变化中的全局状态，也不建 GL context 查询。
2. **统一逻辑线段，而非强行统一原始布局。** 记录模式、真实索引与分隔，按来源布局还原端点和连接关系。Minecraft LINES/LINE_STRIP 的重复顶点与真正 GL_LINES/GL_LINE_STRIP 不能按同一重复规则解码；不能把直接 GL_LINES 简单重标为现有 Minecraft LINES。
3. **以一次绘制的不可变描述提交。** 描述包含来源/顺序、线段及颜色、宽度值和单位、姿态来源、目标、材质/alpha、深度与成像所有权、世界/帧/资源代际。宽度按绘制或线组保存，不再由整个实体批次的固定值覆盖。只合并合同相同的组。直接 buffer 绘制还要保留正确的顶点/索引范围及资源引用；自定义 shader 若改变位置、颜色或宽度，必须有明确翻译，否则报告不支持，不能仅凭线模式就假定可按标准属性处理。
4. **所有世界入口交给一个线消费者。** 为 `WorldMeshSink` 补上实际线接收能力，专用 debug/highlight/BER/粒子和已有优先级边最终走同一线提交合同；沿用现有 JNI 长度检查、任务所有权、代际与在途退休，不另建一套异步生命周期。跨事件延迟 flush 必须保留所属场景/阶段，不能在作用域结束后落入 GUI 或下一帧。
5. **统一姿态的表达。** 原始 normal 仍保留原义；模型边的显式截面参考轴放入有类型的字段，取消靠内容名称解释 normal 的特殊分支。带 Pose 的逐顶点绘制基底优先，缺失时采用明确的 owner/world 回退并可诊断。若来源已经将局部变换压成原始坐标，不能只从端点推回唯一 roll；原始线图元本身没有方棱柱截面角度，后者是我们的几何化选择。
6. **复用一套世界几何展开。** 四类模式归一后共用现有截面助手。明确原始线长与端帽外延、物理厚度换算及折线连接的处理；这次先保持已接受的截面和物理宽度政策，不顺手换成圆管、改变接头或追求未获授权的像素宽度行为。镜像、基底退化、方向变化引起的参考轴切换需回归，不能以归一化掩盖所有缩放语义。
7. **目标和语义不合并。** 世界线进入现有 PT 场景；UI/离屏线按原目标消费，世界默认目标不得回退到后置光栅。调试自发光、普通 BER 受光、优先级可见性和 alpha 各按原合同保留，不能因共用几何就统一为同一种材质或叠加层。

实施顺序建议为：冻结来源/布局行为测试 → 补通用接收和 flush → 按绘制保留宽度/状态 → 迁移姿态字段和专用入口 → 删除已无调用的重复分支。验证覆盖同批不同宽度/alpha、四模式与真实索引、事件延迟排空、普通/优先级所有权、镜像与 sublevel 组合旋转、无 Pose 回退、取消/异常/重载及在途退休。旧自动回归与用户已通过用例保留，不能用源码字符串替代新增行为测试。本次仅形成建议，没有执行这些修改。

## 结论

先交付盘点，尚未实施统一。最优先的确定缺口是通用世界接收器拒收线图元及普通批次宽度覆盖；其次是多种姿态来源尚未表达为统一合同。Veil 编辑器、Flywheel 枚举和未默认调用的 Minecraft 调试器属于有边界的待验证项，不能合并成“全部漏画”。本轮无性能、GPU、游戏或新的视觉验证；保留当前候选与已有故障边界。
