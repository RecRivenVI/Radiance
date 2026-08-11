# 成像隐藏与来源合同的集成完整性核验

2026-10-17，针对[合并验收反馈](../project/status-current.md#本轮合并手工验收反馈)核对直接同类调用链。现有统一合同值得保留，但观察者归属、优先级续追踪、控制遮罩含义与持久缓冲绘制的实际接入仍有缺口；不能以共享类型或辅助函数测试通过认定集成完整。本次仅调查、离线计算及维护记录，没有修改产品或着色器，没有启动游戏、GPU／性能实验、提交或推送。

## 问题

区分连续折射后隐藏几何重现、船内水面未隐藏、第一人称自身发光效果进入优先级成像的原因；检查这些失败是否来自共同合同缺口。浮空水晶相关崩溃只核对已保存的生产者与拒收链，继续保留后续修复状态。眼睛向内照明不预设为缺陷。

本次读取 HEAD 为 `72d2e4a3275e782418f59142e2f2ea1f01be4120`，工作树保留累计未提交实现；HEAD 不代表全部读取内容。实际输入的 SHA-256、离线脚本和结果位于 `D:/Workspaces/Scratches/RadianceSemanticsAudit/query-integration-audit-20261017/`，不改变当前 Prism 验收包。

## 方法

- 沿 Java 的实体／派生轮廓分类、原生场景发布、共同查询与面规则、Vanilla PT 和 Advanced 的主追踪／续追踪／缓存／优先级路径逐层核对。
- 枚举产品 any-hit 中 `acceptsWorldMaterialFace` 的全部调用，区分直接成像的显式用途与阴影的传光默认值。
- 比较原版 1.21.1 的 `BoatModel`、`BoatRenderer`、`Boat.floatBoat`、`EntityType` 与产品 `water_clip.hpp`、`water_clip.glsl`、遮罩发布及实际水面过滤。
- 比较 `VeilWorldGeometryAdapter`、`VeilWorldBlockLayersMixins`、`SectionRasterStorage`、`RasterDrawBridge`、`DrawTargetContract` 与已保存崩溃堆栈，不自动复现。
- 阅读现有 CPU／GPU 与 Java 回归的真实断言，查明它们验证了哪个边界。本次没有重新执行它们，不引用旧成功计数作为本次新运行。

## 发现

### 结论表

| 项目 | 可以确认的事实 | 尚不能确认的范围 |
| --- | --- | --- |
| 第一人称自身优先级隐藏 | `EntityProxy.priorityOnlyVisibilityFor` 无条件返回 PRIORITY_ONLY；主体虽用 PLAYER 分类，派生轮廓／优先级文字不继承摄像机自身隐藏。现有 Java 测试还断言该旧结果 | 不能把修正这一入口当作所有折射及水面问题共同修复 |
| 优先级续追踪 | `priority_image.glsl::tracePriorityLayer` 在第二次循环无条件清除直接成像身份并改用传光掩码，没有根据真实折射／反射或穿透继续事件推进 | 这是合同覆盖缺口，尚未证明它就是使用者在主世界观察到的折射重现路径 |
| 船内水面 | 来源 water mask 是深度盖片；产品将盖片封闭薄体积直接用作排除域。离线原版模型反例表明正常水面可以完全落在该薄体积之外 | 尚无使用者失败姿态的完整顶点／水面交点捕获；不能归因于 GPU 同步、FFT 位移或资源包 |
| Veil 持久区块层 | 兼容适配器恢复并调用了持久 VertexBuffer 绘制，而默认世界目标合同拒绝没有 RenderType/world material metadata 的裸缓冲；已有堆栈实际到达此拒收点 | 未确认具体浮空水晶对象或历史回退丢失，不直接恢复世界后置光栅权限 |
| 末影人眼睛向内照明 | 当前眼睛传光查询保留背面辐射，与全亮实际照明和双面传光有关 | 不是由静态读取证明的漏光；不与上述确定缺口合并修复 |

### 观察者归属没有贯穿派生表示

`EntityProxy.worldVisibilityFor` 会区分附着、清醒的摄像机实体、脱离摄像机与睡眠状态；`priorityOnlyVisibilityFor` 接收相同参数却全部忽略。普通与 Sable 分支在提交优先级文字和轮廓时共用后一结果，故不能只在某一轮廓 renderer 上特判。

`EntityOutlineVisibilityTest.attachedAwakeCameraEntityUsesFirstPersonHiddenVisibility` 只对身体要求 PLAYER，却对该实体的优先级表示仍要求 PRIORITY_ONLY。这是错误期望被测试固化，不能以现有测试通过否定使用者反馈。后续应共用所有者可见性决定，同时保留其他实体的优先级显示和自身的传光参与。

观察者身份、显示通道与查询用途是不同维度。Vulkan mask 是位相交判定，简单把 SELF 与 PRIORITY 两位相加不能表达“对自身摄像机隐藏，但在传光中存在”的完整条件；应由统一决定者产出正确通道或显式过滤条件，而非让派生入口各自猜测。

### 查询合同存在，实际循环仍可绕过

共同 `advanceQuery` 对连续折射保持直接成像、反射／漫反射后不可逆转为传光的规则正确。常规 Vanilla 与 Advanced 主追踪、secondary 追踪和 Advanced 缓存中能找到相应推进与保存；Disney 的玻璃采样也将反射与折射分别标为 1、2。未发现足够证据证明普通第一次折射必然丢失该身份，不能预写其根因。

产品 any-hit 共 17 处 `acceptsWorldMaterialFace` 调用：14 处显式传入成像身份，3 处阴影／云阴影使用默认传光，后者有意如此，不应改成相机剔除。逐调用清单在 `source-boundaries.json`。

但 `priority_image.glsl` 仍按 bounce 号决定第二次循环必为传光；`ui_primary_visibility.glsl` 仍以 `step == 0` 决定 UI owner 过滤。前者与连续折射合同不符；后者属于独立离屏所有权，且 Ponder PT 已封存，其当前可达范围需要在进入实现前确认，不能直接当作本次主世界失败根因或恢复 Ponder PT。

现有 `query_policy_test` 的 64 次折射循环只调用 `advanceQuery`；GPU `material_faces.comp` 自己构造 ray query 并显式选用途，也不执行真实 world／priority 追踪循环。它们适合验证共同数学与掩码，但不能发现实际循环绕开共同决定者、错误清位或生产者错分通道。后续必须补实际续追踪和缓存交接接缝，保留这些现有测试而非替换成更多源码字符串检查。

### 原版船的深度盖片不等于船内体积

原版 `RenderType.waterMask` 只写深度，`BoatRenderer` 在未完全潜水时提交 `waterPatch`。该模型与船底形状相同但位置提高，用于遮住它之后的水面，不是来源定义的排水实体。

`source_boundary_probe.py` 按原版模型盒、部件 X 旋转、1/16 尺度、renderer 的 Y 平移与镜像计算，无受伤／气泡倾斜的普通船盖片 Y 范围为相对船位置 `[0.375, 0.5625]`。原版浮力方程给出一个默认静止平衡例子：水面相对船位置为 `0.65 × 0.5625 = 0.365625`，比盖片下缘还低 `0.009375`；船底上缘为 `0.1875`。水面可以处于船内、在盖片之后，却不在盖片自身的薄体积中。

产品 `Entity::Entity` 将该盖片的闭合网格交给 `clipPlanes`，`world_prepare` 上传其平面，实际水面 any-hit 再以 `excludedWaterPosition` 做体积包含测试。因此这个反例不会排除水面。它证明当前排除域含义不充分，与实测失败吻合；仍不冒充对使用者实际帧的捕获。

既有 CPU 测试使用原点附近的普通立方体，GPU 测试使用人造裁切平面，没有原版 BoatModel、BoatRenderer 姿态与实际水位之间的关系。应以来源盖片和船体边界定义有界船内排除域，分别覆盖船／箱船／竹筏及宿主变换；原版 RaftModel 不实现 WaterPatchModel，无来源遮罩的筏不能按名称凭空建立排除域。保留船外和其他合法水面，不无限沿射线忽略水，也不用世界坐标常量或船名白名单补洞。

### 持久缓冲的来源语义在绘制入口断开

`SectionRasterStorage` 明确保留 `aeronautics:levitite`、`aeronautics:levitite_ghosts` 等额外层所需网格；`VeilWorldBlockLayersMixins` 能取得当次 RenderType，却只通过回调调用 `VeilWorldGeometryAdapter.withUploadedSections`。随后持久 `VertexBuffer.draw` 进入 `RasterDrawBridge`，后者只能查询 scope 和 FBO，未收到这层 RenderType 的世界材质或已登记来源权限。

`DrawTargetContract` 对默认世界的裸缓冲拒收符合既有安全边界。问题是来源适配器已接回绘制入口，却没有完成到合同消费者的交接；这与“故意拒绝未知世界绘制”不能混为一谈。现有 section 测试验证复制后的字节和寿命，Veil 测试验证回调未丢失，都没有执行“真实额外层 → 持久缓冲 → 实际目标分类”的闭环。

后续这组应集中核对两种已登记额外层和同一路由的持久绘制，补齐来源与目标语义。不因它崩溃就给所有 Veil listener 发 WORLD_RASTER 权限，也不自动将需参与世界 PT 的几何改为后置光栅。具体材质与是否属于既有允许边界须沿原来源确认；本次继续遵守使用者将该崩溃留待后续的安排。

## 结论

统一化已完成可复用的合同与大量消费逻辑，完整性尚未闭合：同一所有者的派生表示仍可错分，实际续追踪可绕开用途决定，控制网格的几何形状可被误当作其功能语义，持久绘制可在最终入口失去来源信息。应集中补齐这些直接同类边界，保留已通过的线条、材质与 GUI 能力，不重新改写整个渲染器。

建议实施范围分三组：先统一所有者可见性和实际查询续接，覆盖主体／轮廓／优先级文字、普通与 Sable、两套 PT 及实际缓存交接；再纠正船内排除域并同步所有水面消费者；Veil 持久层另组保留后续，补齐来源到目标的合同。三组分别有失败回归，不将未知主世界折射根因或眼睛内部照明先验绑到某一修复上。

本次的验证是来源与调用链核验、离线船模型反例以及现有测试覆盖审阅；没有执行新客户端、GPU 测试或性能实验。阶段 A/B/C/D 的六组通过继续保留，三个成像失败和浮空水晶相关崩溃没有被本调查修复。
