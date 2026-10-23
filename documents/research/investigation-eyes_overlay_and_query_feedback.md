# 眼睛覆盖层流程与统一验收反馈核验

2026-10-17，使用者确认六组合并目视通过，并报告三项成像隐藏失败、末影人眼睛轻微照亮头颅内部及一次浮空水晶相关崩溃。本次仅记录反馈、只读核对来源和现有路径；没有修改产品或着色器、启动游戏、性能实验、提交或推送。

## 问题

原版的眼睛覆盖层如何产生，是否本来会照亮模型内部；当前向内照明与已接受的物理发光／双面传光规则有什么关系。其他验收失败按事实登记，浮空水晶相关崩溃留待后续，不把假设写成根因。

最新安装 JAR 再核对为 `FB3908DB0B83BE9BD71D6DC7FFB3538116D07DEB0D7359E1EBA6682F4030A8CD`。其构建及内嵌身份沿[修复记录](experiment-companion_surface_repair.md)，本次未重新证明运行时 DLL 的加载身份。完整反馈由[现状](../project/phase-1-before_vanilla_closeout.md#本轮合并手工验收反馈)与[验收表](../development/testing-render_unification.md#2026-10-17-手工反馈)维护。

## 方法

- 读取 `D:/Workspaces/References/minecraft-references/minecraft-1.21.1/src/` 中的 `LivingEntityRenderer`、`EndermanRenderer`、`EndermanModel`、`EyesLayer`、`EnderEyesLayer`、`SpiderEyesLayer`、`RenderType` 与 `RenderStateShard`。
- 从当前 Target 的 `build/moddev/artifacts/neoforge-21.1.259-client-extra-aka-minecraft-resources.jar` 直接读取 `assets/minecraft/shaders/core/rendertype_eyes.{json,vsh,fsh}`，不凭 RenderType 名称推断 shader 行为，不创建 GL context。
- 核对现有 `emissive_eye_composition.hpp`、`source_draw_colour.glsl`、`emissive_overlay.glsl` 及两套 PT 中的实际调用，区分直接成像面规则与传光路径辐射。
- 只读保存已有崩溃报告和两份结束运行的日志，不进行复现。原文件保持不动；副本逐文件 SHA-256 核对，位于 `D:/Workspaces/Scratches/RadianceSemanticsAudit/acceptance-feedback-20261017/`，索引为其中的 `evidence-manifest.json`。
- 来源 Java 与实际 shader 条目的 SHA-256 保存在同目录的 `eyes-source-evidence.json`。首轮文档检查因存储脚本从标准输入执行时错误解析输出目录，将盘点 JSON 写到仓库根目录而失败；已原字节移回上述证据目录并校验哈希，不删除证据或放宽检查。首败日志为 `documentation-check.log`，纠正后的检查另存 `documentation-check-corrected.log`。

## 发现

### 原版绘制流程

1. `EndermanRenderer` 登记 `EnderEyesLayer`，后者选择 `enderman_eyes.png`；蜘蛛通过 `SpiderEyesLayer` 选择 `spider_eyes.png`。两者共用 `EyesLayer`。
2. `LivingEntityRenderer.render` 在设置姿态和动画后先提交身体，再依次调用附加层；渲染器调用顺序不等于立即完成 GPU 绘制，实际提交仍经 `MultiBufferSource` 批次。
3. `EyesLayer.render` 再次调用同一个父模型的 `renderToBuffer`，传入同一 PoseStack、`15728640` 的满亮 light 值及 `OverlayTexture.NO_OVERLAY`。它不创建两颗独立的眼球模型，不额外放大、偏移整层几何，也不重新计算另一套动画。
4. 专用纹理与模型 UV 决定哪些位置出现眼睛颜色。末影人仍是包含头、较小内层 hat 等部件的模型；“头颅内空间”不是由专门眼球光源定义的体积。
5. `RenderType.eyes` 选择专用 eyes shader、`ONE/ONE` 加法混合及 `COLOR_WRITE`。其余取 builder 默认值：`LEQUAL` 深度测试、背面剔除、关闭 lightmap 和 overlay。它测试现有深度、只写颜色，不另写深度；不是普通 source-over 半透明层。
6. 实际 eyes 顶点 shader 只变换位置并传递顶点颜色与 UV，不采样光照。片元 shader 计算纹理颜色 × 顶点颜色 × `ColorModulator` × 雾淡出；没有普通 cutout 的 alpha discard。由于 RGB 的混合因子为 ONE/ONE，不能把该层解释成通用的按 alpha 混合。
7. 原版结果是往通过深度／朝向测试的屏幕像素加上眼睛颜色。没有由此更新方块光照、投射光线或照亮头颅内部的流程；满亮参数也不等于原版生成了世界光源。

### 当前 PT 的差别与证据边界

当前已知加法眼睛与身体满足资格时，在同一个表面保留眼睛纹理、面标志及独立调色；不符合资格的层仍走各自路径。`sampleEmissiveOverlay` 返回眼睛辐射，两套 PT 的 closest-hit 将其加入路径辐射，不退回只显示发光的屏幕叠加。

`eyeOverlayTextureForQuery` 只在 `directImage` 为真时根据来源面状态隐藏该侧眼睛；传光查询不因此去掉背面眼睛辐射。因此从头颅内侧到达眼睛所在表面的传光路径，存在接收到眼睛辐射的代码通路。结合真实发光与双面传光政策，向内照亮内层模型具有机制上的解释，不等于凭空新增了眼球几何。

这与原版的颜色叠加不同，但与使用者要求全亮实际影响世界的决定有关。现有证据没有定位使用者观察像素的完整路径，不能排除共面层、法线、采样或历史造成的额外异常；不直接宣告“漏光已证实”或“全部正常”。是否另行定义厚实体外壳或单侧发光是新的材质语义取舍，本次不擅自实施，也不关闭眼睛对世界的照明贡献。

### 成像隐藏失败

使用者明确三项不通过：隐藏几何在一次折射后出现、船内水面未隐藏、第一人称自身发光效果的优先级表示未对摄像机隐藏。不能用眼睛等六组通过代替这三项专项通过。

静态入口仍存在直接成像身份、船内 water exclusion 以及独立 priority 查询；这只证明设计入口存在，不证明失败路径正确。`EntityProxy.priorityOnlyVisibilityFor` 当前对摄像机归属、脱离及睡眠参数没有分支，始终返回 PRIORITY_ONLY，为自身优先级隐藏遗漏提供了明确检查点；不能把这一处直接当作折射或水面失败的共同根因。本次不实施修复。

### 浮空水晶相关崩溃

使用者仅要求记录后续处理，认为历史回退可能丢失。实际保存的 `crash-2026-10-17_19.07.46-client.txt` 为 Java `UnsupportedOperationException`：Veil `ForgeRenderTypeStageHandler` 通过 `drawBlockLayer`／`renderSectionLayer` 绘制持久 VertexBuffer，在 `WORLD_STAGE[RenderLevelStageEvent.minecraft:after_block_entities]` 被 `RasterDrawBridge` 拒收，原因是缺少 RenderType/world material metadata。

该报告没有给出具体水晶实体身份，不能把“浮空水晶”擅自归类为 Minecraft EndCrystal，也不能据此宣布原回退确已丢失。异常处理之后，`2026-10-17-6.log.gz` 在 19:07:46.157 记录全部维度保存；完整 native close 未由本次核验确认。随后独立运行的 `2026-10-17-7.log.gz` 有保存和正常 `Stopping!`，不拿它补前一崩溃进程的关闭证据。这份异常不是设备丢失根因证据。

## 结论

六组目视按本次包的使用者反馈通过，第一人称／水／特殊标记不通过。世界物理线宽固定、远处屏幕中变细的理解正确。原版眼睛是复用模型的加法颜色层，没有内部照明；当前 PT 有真实辐射及背面传光通路，可解释向内照明的可能性，具体观察路径尚未证实。本轮保留产品、投放和既有规则，浮空水晶相关崩溃留待后续；未执行新的客户端、GPU 或性能测试，不扩大本次反馈到全部 SDK／长期稳定性或历史 GPU 根因。
