# Veil / Sable / Aeronautics-Simulated 兼容裁决

审查时间：2026-09-15；模式：源码优先、只读、未构建、未启动客户端。

参考源码是 Veil 4.3.2 与 Sable 2.0.5 的 1.21.1 NeoForge 精确快照；Aeronautics/Simulated/Offroad 是 Simulated-Project main 的精确 e720946 快照，项目声明版本 1.3.2 但没有 release tag。报告中的“待验证”不会被当作动态通过。

兼容矩阵只保留两组：

| 组合 | 源码裁决 | 主要门槛 |
| --- | --- | --- |
| 基础 | Radiance/MCVR + Sable 2.0.5 的基础世界/子层几何与共享 block/entity 路径；可复用的 Vulkan 保护保留 | CG-V08 culling TODO、CG-V09~V13 的 section/pose/BE/lighting/outline 动态验收 |
| 完整组合 | Radiance/MCVR + Veil 4.3.2 + Sable 2.0.5 + Aeronautics/Simulated/Offroad 1.3.2 | CG-V01、CG-V04~V06、CG-V14~V21 的 Veil stage/FBO/shader/custom layer contract；CG-V22 共享 crumbling 验收 |

当前没有动态通过结论。名称牌 50% 与用户主动选择的判定箱 PBR 发光按要求默认保留，普通 debug line 独立裁决；Plunger 专属坐标与普通手姿态/NeoForge callback 已拆为 CG-V02/CG-V03。Offroad 只审 multi-mining destructionProgress 进入共享 crumbling 路径，不把共享纹理问题归给 Offroad 专属。

## 条目索引

| ID | owner | function | priority | recommendation | static | dynamic |
| --- | --- | --- | --- | --- | --- | --- |
| CG-V01 | Veil | renderer 初始化与消费者可达性 | P0 | 待修复 | 已证实：初始化/消费者可达性冲突 | 待验证：日志中的 null consumer 已见；core.dll+0x12558d 的 native 根因未知 |
| CG-V02 | Simulated | Plunger first-person focus 坐标 | P1 | 待确认 | 静态意图明确；作用域只命中 Plunger getFirstPersonFocusPos | 待验证：相机局部契约与端点位置 |
| CG-V03 | Radiance/NeoForge | 普通第一人称手姿态与回调 | P1 | 保留 | 静态承接链完整，未发现应归入 Plunger 的全局手部缺陷 | 待验证：NeoForge callback 的具体消费者在完整组合中的材质/坐标 |
| CG-V04 | Veil | capability 与 RenderStateShard | P0 | 待重构 | 已证实：GL 状态被拒绝/跳过；Vulkan 等价资源未由此自动生成 | 待验证：每个组合 layer 的 native material/state 覆盖 |
| CG-V05 | Veil/Aeronautics/Simulated | FBO、离屏与 post pipeline | P0 | 待重构 | 已证实：源依赖 FBO/post；当前 OpenGL FBO 与 Veil post 被跳过 | 待验证：对应 Vulkan target 与 native post 视觉/生命周期 |
| CG-V06 | Veil/Sable/MCVR | shader、camera UBO 与 uniform 依赖 | P1 | 待重构 | 静态风险：现有 Vulkan shader cache/translator 存在，但 Veil block/processor 映射未证实 | 待验证：实际 custom shader 的 native registration 与 uniform 值 |
| CG-V07 | Sable | 客户端初始化与 renderer 选择 | P1 | 待确认 | 静态入口与精确类名承接存在；Veil callback 与 variant 完整性未证实 | 待验证：不同运行时组合的 dispatcher/renderData 生命周期 |
| CG-V08 | Sable | 子层可见性与 culling | P1 | 待修复 | 已证实：Sable Vanilla dispatcher.updateCulling 为空 | 待验证：Radiance/MCVR 是否在其他层提供等价 culling |
| CG-V09 | Sable | chunk compile 与 section mesh 承接 | P1 | 待确认 | 静态承接存在；custom layer/native material 完整性未证实 | 待验证：异步 compile、resize、dirty 与 native resource lifetime |
| CG-V10 | Sable | 实体 contained/tracking 位姿 | P1 | 待确认 | 静态桥接逻辑存在，未发现可直接认定的数学错误 | 待验证：复杂 tracking/passenger 与 native hit/visual parity |
| CG-V11 | Sable | block entity 与 dispatcher camera | P1 | 待确认 | 静态入口与 finally 清理存在；重复/漏项需运行验证 | 待验证：global/compiled/embedding 组合 |
| CG-V12 | Sable/Flywheel | sky-light 与 Flywheel lighting scene | P1 | 待重构 | 静态风险：源明确使用 GL buffer/indirect uniforms；当前 Radiance 只在 diagram 走 embedding 分支 | 待验证：Flywheel world/diagram 的 native承接 |
| CG-V13 | Sable/Radiance | block outline 与 hitbox/debug lines | P1 | 保留 | 静态保留路径存在；视觉/坐标 parity 未验证 | 待验证：所有 outline/debug line route 与 native emission |
| CG-V14 | Aeronautics | Levitite custom chunk layers | P1 | 待确认 | 静态确认层 id/cache 与 stage 枚举承接；shader/state 语义未证实 | 待验证：custom layer native material |
| CG-V15 | Aeronautics | 热气球 soft-light 离屏效果 | P1 | 待修复 | 已证实：源入口依赖 Veil AFTER_SOLID_BLOCKS + AdvancedFbo；当前没有对应 dispatch/adapter | 待验证：无客户端动态测试 |
| CG-V16 | Simulated | 客户端 stage/fixed buffer bootstrap | P0 | 待修复 | 已证实：单 JAR 官方入口存在；Veil stage 入口当前被取消 | 待验证：各 Veil stage 的 Vulkan 映射 |
| CG-V17 | Simulated | 自定义 RenderType、纹理与 Spring 顶点 | P1 | 待确认 | 静态承接部分已存在；custom shader/state 和 non-QUADS semantics 未证实 | 待验证：各 layer 的 native material parity |
| CG-V18 | Simulated | contraption diagram FBO/post 替换 | P1 | 保留 | 静态已存在明确 Vulkan replacement，source FBO/readback 不再执行 | 待验证：视觉/交互 parity 与 post target 生命周期 |
| CG-V19 | Simulated/Sable | diagram 世界几何、fluid、BE 与实体 | P1 | 待确认 | 静态链条具体存在且有 double precision 处理；组合材质/embedding 未证实 | 待验证：完整 diagram geometry parity |
| CG-V20 | Simulated | diagram UI、设置与早期/同窗口生命周期 | P1 | 待确认 | 静态实际为单 JAR 官方 SPI；Radiance 有 level/removed/finally guards | 待验证：早期显示、同窗口重入与销毁 |
| CG-V21 | Simulated | EndSea shadow map 与 AFTER_LEVEL | P1 | 待修复 | 已证实：AFTER_LEVEL/FBO/post 依赖没有当前 stage adapter | 待验证：无动态客户端测试 |
| CG-V22 | Offroad | multi-mining destruction progress | P1 | 待确认 | 静态共享路径已接入；无证据认定 Offroad 专属渲染缺陷 | 待验证：多 progress 与 native crumbling parity |
| CG-V23 | Radiance/MCVR | VkResult 错误传播与生命周期保护 | P0 | 保留 | 已证实：错误传播和生命周期保护是有效业务逻辑 | 待验证：与 122757 core 崩溃的关联；不能声称已修复 |
| CG-V24 | Radiance/Sable bridge | 本地日志、计数与检测探针 | P1 | 明确待全部移除 | 已证实：这些是观察性日志/探针，不是兼容实现 | 待验证：清理前的最后一轮 native correlation；core root 仍未知 |

## 逐项语义

### CG-V01｜Veil｜renderer 初始化与消费者可达性

**版本**：4.3.2 (mc1.21.1-neoforge; ref 540ad3778c1a5dafe905420adb21cb05e459b5c4)

**源码证据**：

- REF D:/Workspaces/References/minecraft-references/veil-4.3.2/src/neoforge/src/main/java/foundry/veil/forge/VeilForgeClient.java:26-38 — <init>, registerListeners 调 VeilClient.init、VeilRenderSystem.init、VeilReloadListeners 与 ForgeVeilRendererAvailableEvent
- REF D:/Workspaces/References/minecraft-references/veil-4.3.2/src/common/src/main/java/foundry/veil/api/client/render/VeilRenderSystem.java:1183-1194,1259-1267 — init 创建 renderer，renderPost 直接读取 renderer.getPostProcessingManager
- REF D:/Workspaces/References/minecraft-references/veil-4.3.2/src/neoforge/src/main/java/foundry/veil/forge/VeilForgeClientEvents.java:43-45 — clientDisconnected 读取 renderer.getLightRenderer
- CUR D:/Workspaces/Repositories/GitHub/RecRivenVI/Radiance/src/main/java/com/radiance/mixins/compatibility/veil/VeilRenderSystemMixins.java:35-38 — 取消 init、beginFrame、endFrame
- CUR D:/Workspaces/Repositories/GitHub/RecRivenVI/Radiance/src/main/java/com/radiance/mixins/compatibility/veil/VeilForgeClientMixins.java:16-39 — 跳过 Veil reload listeners 与 RendererAvailable 事件
- RUNTIME D:/Workspaces/Artifacts/RadianceMCVRAudit/20260914/Evidence/RuntimeLogs/20260914-122757.txt:600-602 — 首个 Veil setupLevelCamera 异常读取空 renderer；core.dll+0x12558d 仍未精确定位

**入口链**：VeilForgeClient 构造 -> VeilClient.init/bootstrap -> RegisterClientReloadListeners -> VeilRenderSystem.init -> VeilRenderer 创建 -> reload listener 与 RendererAvailable 事件 -> Veil/Sable/Simulated/Aeronautics 消费者。当前 init 被取消而消费者仍能由 LevelRenderer、事件和资源路径触发。

**所需 GL/源能力**：OpenGL context/capabilities、VeilRenderer 及其 shader/framebuffer/post/particle/light/editor managers、NeoForge reload/event bus

**当前 Vulkan 承接**：Radiance 由 WorldRendererMixins 接管世界帧并把几何交给 MCVR BufferProxy/EntityProxy；当前没有 VeilRenderer 对象或 Veil OpenGL 生命周期的 Vulkan adapter。

**缺口**：已证实的静态契约冲突是 init 被取消但 renderer 读者未统一门控；运行日志复现了精确的 Veil null consumer，但 native core 崩溃根因未知，不能将其认定为唯一原因。

**影响**：P0

**方案**：默认目标是补齐三层真实契约：CPU 可查询的 availability state、实际 Vulkan renderer/resource handles（target、descriptor、pipeline 等）以及各消费者的明确 route。Veil API 不应收到假 VeilRenderer；在真实资源与 route 尚未准备好时，才把 GL-only listener、editor、command、post、resource reader 作为明确的条件/代价门控，具体功能取舍交由用户决定；不要只取消 init 留下可读的 null 全局。

**共享依赖**：Radiance Java bootstrap、NeoForge event bus、Veil resource reload、Sable/SIM/Aeronautics Veil callbacks、MCVR native renderer

**回退与损失**：没有 bridge 时，Veil deferred light、post、AdvancedFbo、Quasar/editor 等功能无法承诺；保留 Radiance Vulkan/原版几何回退。名称牌 50% 与用户主动选择的判定箱 PBR 发光必须默认保留；普通 debug line 另行裁决。

**验收**：静态确认入口与消费者契约；动态需在 Veil+Sable+Simulated+Aeronautics 组合中进入世界、资源 reload、切换世界并关闭，确认所有 renderer 读取者要么拿到真实 bridge、要么被明确门控，且分别记录 native 首错。

**状态**：静态：已证实：初始化/消费者可达性冲突；动态：待验证：日志中的 null consumer 已见；core.dll+0x12558d 的 native 根因未知；裁决：待修复

### CG-V02｜Simulated｜Plunger first-person focus 坐标

**版本**：1.3.2 bundled untagged main snapshot (ref e720946bb5e84d618d0f1aa47e1a19ab2adb5bd7; aeronautics/simulated/offroad)

**源码证据**：

- REF D:/Workspaces/References/minecraft-references/aeronautics_bundled-1.3.2/src/simulated/common/src/main/java/dev/simulated_team/simulated/content/entities/launched_plunger/LaunchedPlungerEntityRenderer.java:73-91,123-175 — getFirstPersonFocusPos 读取主相机 rotation、投影逆变换、itemProjMat/FOV；实体/绳索另有 Sable 位姿转换
- REF D:/Workspaces/References/minecraft-references/aeronautics_bundled-1.3.2/src/simulated/common/src/main/java/dev/simulated_team/simulated/content/items/plunger_launcher/PlungerLauncherItemRenderer.java:36-70,122-125 — renderItem 保存 focusPos 与 itemProjMat，发射粒子使用 focus point
- CUR D:/Workspaces/Repositories/GitHub/RecRivenVI/Radiance/src/main/java/com/radiance/mixins/compatibility/simulated/SimulatedLaunchedPlungerEntityRendererMixins.java:16-24 — 仅重定向 Quaternionf.transformInverse 并原样返回 focusPoint
- CUR D:/Workspaces/Repositories/GitHub/RecRivenVI/Radiance/src/main/java/com/radiance/mixins/vulkan_render_integration/HeldItemRendererMixins.java:52-88 — 普通手的独立入口与两手选择

**入口链**：PlungerLauncherItemRenderer.renderItem -> 保存相机局部 focusPos/itemProjMat -> LaunchedPlungerEntityRenderer.getFirstPersonFocusPos -> 当前仅抑制一次逆旋转 -> 绳索/粒子目标。普通手走另一个 HeldItemRenderer -> ClientHooks.renderSpecificFirstPersonHand 链。

**所需 GL/源能力**：Blaze3D RenderSystem projection state、Create/Catnip SuperByteBuffer/RenderType，Plunger 参考实现本身没有图表 FBO

**当前 Vulkan 承接**：Radiance 只在该 Plunger consumer 处改变坐标，普通持物由 EntityProxy HAND 捕获；当前 MCVR 没有通用 Plunger 投影状态。

**缺口**：当前 redirect 的静态意图是避免已 camera-local 顶点被二次逆旋转，但“已 camera-local”需要在实际 item matrix 与 consumer 坐标中验证；不能把该改动套到普通手姿态或 NeoForge callback。

**影响**：P1

**方案**：保留窄作用域 redirect 作为待验证方案；分别核对第一人称 owner、第三人称、Sable 子层中的 body/spool/rope/particle 端点，若失败只在 Plunger consumer 建立数据转换。

**共享依赖**：Simulated Plunger renderer、Create/Catnip/Flywheel、Sable pose、Radiance HeldItemRenderer/EntityProxy、NeoForge hand callback

**回退与损失**：移除 redirect 可能造成 focus point 二次旋转；扩大补丁可能移动所有持物。缺陷未证实前保留 Plunger 几何、绳索与粒子。

**验收**：动态验证上述三种 Plunger 视角和 Sable 子层，再渲染普通物品并确认 ClientHooks callback 的选择与姿态没有变化。

**状态**：静态：静态意图明确；作用域只命中 Plunger getFirstPersonFocusPos；动态：待验证：相机局部契约与端点位置；裁决：待确认

### CG-V03｜Radiance/NeoForge｜普通第一人称手姿态与回调

**版本**：Radiance 414d8e330a2fc6cb1e8630cc95f2302b2b97a0e8 / MCVR 9905c81b1999f5845bf66d13501d371c16adf561

**源码证据**：

- CUR D:/Workspaces/Repositories/GitHub/RecRivenVI/Radiance/src/main/java/com/radiance/mixins/vulkan_render_integration/GameRendererMixins.java:185-199 — renderItemInHand 改用手部 FOV 后调用 EntityProxy.queueHandRebuild
- CUR D:/Workspaces/Repositories/GitHub/RecRivenVI/Radiance/src/main/java/com/radiance/mixins/vulkan_render_integration/HeldItemRendererMixins.java:51-88 — attack/equip/bob、主副手选择、ClientHooks.renderSpecificFirstPersonHand、vanilla renderArmWithItem
- CUR D:/Workspaces/Repositories/GitHub/RecRivenVI/Radiance/src/main/java/com/radiance/client/proxy/world/EntityProxy.java:965-1001 — HAND StorageVertexConsumerProvider 与 queueBuild
- REF D:/Workspaces/References/minecraft-references/aeronautics_bundled-1.3.2/src/simulated/common/src/main/java/dev/simulated_team/simulated/content/items/plunger_launcher/PlungerLauncherItemRenderer.java:107-151 — Plunger RenderHandler 的 transformHand 只影响该物品

**入口链**：GameRenderer.renderItemInHand -> 手部 projection/FOV -> EntityProxy.queueHandRebuild -> HeldItemRenderer.radiance$renderItem -> NeoForge ClientHooks callback；callback 未接管时调用原版 renderArmWithItem，之后 HAND 几何进入 native queue。

**所需 GL/源能力**：Blaze3D PoseStack、手部投影矩阵、NeoForge ClientHooks 的 callback contract；禁止假定 OpenGL draw 已完成

**当前 Vulkan 承接**：StorageVertexConsumerProvider/PBRVertexConsumer 捕获顶点，EntityProxy 以 HAND flag 交给 MCVR；这是通用手路由，不读取 Plunger 专属 focus point。

**缺口**：Plunger 的坐标 redirect 与普通手姿态是两个契约；普通手的 callback 可能由其他物品/模组消费，任何全局 camera-local 修复都会影响 equip、bob、主副手与回调选择。

**影响**：P1

**方案**：保留现有普通手姿态、手部 FOV 与 ClientHooks callback 顺序；只为确有证据的 Plunger consumer 增加局部适配，禁止用禁用主手/副手作为兼容结果。

**共享依赖**：Minecraft GameRenderer/ItemInHandRenderer、NeoForge ClientHooks、Radiance EntityProxy/PBRVertexConsumer、Simulated Plunger RenderHandler

**回退与损失**：若回调或手部路由失败，损失是所有物品的第一人称视觉；不能以丢弃手部几何换取组合启动。

**验收**：动态用普通方块、普通物品、Simulated Plunger 分别测试主副手、攻击/装备动画、手部 FOV 与 callback 返回 true/false 两路。

**状态**：静态：静态承接链完整，未发现应归入 Plunger 的全局手部缺陷；动态：待验证：NeoForge callback 的具体消费者在完整组合中的材质/坐标；裁决：保留

### CG-V04｜Veil｜capability 与 RenderStateShard

**版本**：4.3.2 (mc1.21.1-neoforge; ref 540ad3778c1a5dafe905420adb21cb05e459b5c4)

**源码证据**：

- REF D:/Workspaces/References/minecraft-references/veil-4.3.2/src/common/src/main/java/foundry/veil/VeilClient.java:51-59 — main/translucent/particles/weather/clouds/item_entity dynamic buffer shards
- REF D:/Workspaces/References/minecraft-references/veil-4.3.2/src/common/src/main/java/foundry/veil/api/client/render/framebuffer/AdvancedFbo.java:146-190 — bindRead/bindDraw/unbind 通过 GlStateManager 绑定 FBO
- CUR D:/Workspaces/Repositories/GitHub/RecRivenVI/Radiance/src/main/java/com/radiance/mixins/compatibility/veil/VeilRenderSystemMixins.java:41-70 — 大量 OpenGL capability 统一返回 false
- CUR D:/Workspaces/Repositories/GitHub/RecRivenVI/Radiance/src/main/java/com/radiance/mixins/vulkan_render_integration/RenderPhaseMixins.java:37-42 — DynamicBufferShard 状态被跳过
- CUR D:/Workspaces/Repositories/GitHub/RecRivenVI/Radiance/src/main/java/com/radiance/mixins/vulkan_render_integration/OpenGlFramebufferCompatibilityMixins.java:24-107 — GL FBO 操作丢弃，glCheckFramebufferStatus 合成 COMPLETE

**入口链**：Veil/Sable/Simulated/Aeronautics 的 RenderType -> shaderState/dynamic buffer -> RenderStateShard.setup/clear -> Blaze3D/GL；Radiance 同时把部分 layer 顶点捕获到 StorageVertexConsumerProvider。

**所需 GL/源能力**：GL context、GL capability、RenderStateShard 的 setup/clear、副作用顺序、FBO attachments

**当前 Vulkan 承接**：世界几何的 RenderType layer 可被 WorldRendererMixins 遍历，PBRVertexConsumer 解析部分格式/材质；MCVR 不会自动执行任意 Veil GL state shard。

**缺口**：false capability 与合成 COMPLETE 只表示调用被拦截，不能证明对应 Vulkan resource 已存在；动态 buffer 输出目标、透明排序、color/depth mask、custom shader state 仍缺统一翻译。

**影响**：P0

**方案**：按 RenderType state 建立可审计的 Vulkan state/material adapter；不支持的 shard 在调用者入口明确 gate，并让状态报告“不承接”，不能伪造 FBO 完成。

**共享依赖**：Veil RenderState/AdvancedFbo、NeoForge stage、Sable dispatcher、Simulated/Aeronautics custom layers、MCVR PipelineState/TextureProxy

**回退与损失**：未适配的 GL-only layer 可丢失对应特效或回退到已有 vanilla/PBR layer；不能把所有世界 layer 全局禁用当作成功。

**验收**：静态逐一列出 custom layer 的 shader/texture/state；动态确认透明、深度、剔除、mask 和目标 attachment 在 Vulkan frame capture 中与源语义一致。

**状态**：静态：已证实：GL 状态被拒绝/跳过；Vulkan 等价资源未由此自动生成；动态：待验证：每个组合 layer 的 native material/state 覆盖；裁决：待重构

### CG-V05｜Veil/Aeronautics/Simulated｜FBO、离屏与 post pipeline

**版本**：4.3.2 (mc1.21.1-neoforge; ref 540ad3778c1a5dafe905420adb21cb05e459b5c4)

**源码证据**：

- REF D:/Workspaces/References/minecraft-references/veil-4.3.2/src/common/src/main/java/foundry/veil/api/client/render/VeilRenderSystem.java:1259-1267,1281-1285 — renderPost 与 drawLights 读取 post/framebuffer managers
- REF D:/Workspaces/References/minecraft-references/aeronautics_bundled-1.3.2/src/aeronautics/common/src/main/java/dev/eriksonn/aeronautics/content/blocks/hot_air/balloon/effect/ClientBalloonEffectRenderer.java:41-76,90-162 — overlayFbo、shader/uniform、heat post pipeline
- REF D:/Workspaces/References/minecraft-references/aeronautics_bundled-1.3.2/src/simulated/common/src/main/java/dev/simulated_team/simulated/content/end_sea/EndSeaShadowRenderer.java:49-103,148-163 — end_sea FBO、shadow render 与 spread post
- CUR D:/Workspaces/Repositories/GitHub/RecRivenVI/Radiance/src/main/java/com/radiance/mixins/compatibility/veil/VeilRenderSystemMixins.java:73-89 — renderPost/drawLights/compositeLights/clearLevel 被取消
- CUR D:/Workspaces/Repositories/GitHub/RecRivenVI/Radiance/src/main/java/com/radiance/mixins/vulkan_render_integration/OpenGlFramebufferCompatibilityMixins.java:29-100 — FBO bind/attach/blit 被拒绝

**入口链**：Mod stage callback -> VeilRenderSystem/AdvancedFbo -> scene draw -> post uniform/pipeline -> screen composition；Aeronautics balloon 与 Simulated EndSea 是实际独立消费者。

**所需 GL/源能力**：OpenGL AdvancedFbo、color/depth attachments、read/draw binding、post ping-pong、GL texture/sampler

**当前 Vulkan 承接**：当前只看到 MCVR 通用世界/overlay pipeline，以及 Simulated diagram 的专用 DiagramState；没有 Aeronautics soft_light 或 Simulated end_sea 的通用 FBO/post adapter。

**缺口**：Veil 生命周期取消后，直接读取 renderer 的 mod post 仍可能空指针；即便调用被截断，也没有把源 FBO 的深度、颜色、迭代次数和 post uniform 语义交给 Vulkan。

**影响**：P0

**方案**：为每个离屏消费者定义真实 Vulkan render target、descriptor/pipeline 与 post contract，或在其入口明确报告不可用；先完成 CPU availability、真实 resource handles 和消费者 route，再接入 balloon/end_sea。

**共享依赖**：Veil PostProcessingManager/AdvancedFbo、Aeronautics ClientBalloonEffectRenderer、Simulated EndSea、MCVR framebuffer/pipeline

**回退与损失**：没有适配时可保留主体世界几何而丢失 soft-light、shadow spread、bloom 等效果；不得用关闭 Aeronautics/Simulated 主功能充当兼容。

**验收**：动态分别创建/重建/释放 overlay、shadow 与 post target，测试 resize、reload、世界退出和 renderer close 后不读旧 attachment。

**状态**：静态：已证实：源依赖 FBO/post；当前 OpenGL FBO 与 Veil post 被跳过；动态：待验证：对应 Vulkan target 与 native post 视觉/生命周期；裁决：待重构

### CG-V06｜Veil/Sable/MCVR｜shader、camera UBO 与 uniform 依赖

**版本**：4.3.2 (mc1.21.1-neoforge; ref 540ad3778c1a5dafe905420adb21cb05e459b5c4)

**源码证据**：

- REF D:/Workspaces/References/minecraft-references/veil-4.3.2/src/common/src/main/java/foundry/veil/api/client/render/CameraMatrices.java:76-101 — CameraMatrices.update 写 projection/view/inverse/cameraPosition 并 bind CAMERA shader block
- REF D:/Workspaces/References/minecraft-references/veil-4.3.2/src/common/src/main/java/foundry/veil/api/client/render/VeilRenderer.java:70-98,210-225,261-269 — ShaderManager、FramebufferManager、PostProcessingManager、CameraMatrices、LightRenderer 创建/消费者
- REF D:/Workspaces/References/minecraft-references/sable-2.0.5/src/common/src/main/java/dev/ryanhcode/sable/SableClient.java:32-39 — shader processors 与 sky-light shadow stage 通过 VeilEventPlatform 注册
- CUR D:/Workspaces/Repositories/GitHub/RecRivenVI/Radiance/src/main/java/com/radiance/mixins/vulkan_render_integration/WorldRendererMixins.java:187-195,225-237 — 当前世界 uniform 走 BufferProxy
- CUR D:/Workspaces/Repositories/GitHub/RecRivenVI/MCVR/src/core/vulkan/shader.cpp:202-243,391-464 — 已有 dependency hash 与磁盘 SPIR-V cache；不是每次无缓存重编
- CUR D:/Workspaces/Repositories/GitHub/RecRivenVI/Radiance/src/main/java/com/radiance/client/shader/ShaderRegistry.java:51-63,172-195,215-241 — dynamic shader metadata、native registration、reload warmup

**入口链**：Veil shader/resource reload -> shader processors/CameraMatrices UBO -> RenderType shader/uniform；Radiance 世界帧更新自己的 World/Sky uniform，ShaderRegistry 翻译带完整 metadata 的动态 ShaderInstance，MCVR 再通过已有 SPIR-V dependency cache。

**所需 GL/源能力**：GLSL/Veil shader blocks、RenderSystem projection/modelview、uniform uploads、GL shader capability；源 shader processor 的注入时序

**当前 Vulkan 承接**：MCVR 有 World/Sky/Overlay UBO 与 shader translator，并已有 ShaderSpirvCache；这承接了一部分统一字段，但没有证据表明 Veil CAMERA block、Sable processors 和自定义 uniform 自动映射。

**缺口**：源 Veil shader 依赖与当前 native UBO 命名/布局可能不一致；Veil stage 又被取消，导致 shader 注册路径与调用路径分离。不能把已有 SPIR-V cache 误述为 Veil shader 已兼容。

**影响**：P1

**方案**：建立 shader name/vertex format/uniform block 的显式映射和失败门控；复用现有 dependency-hash/SPIR-V cache 与 ShaderRegistry warmup，不另造“每次重编”结论。

**共享依赖**：Veil ShaderManager/CameraMatrices、Sable processors、Simulated/Aeronautics shaderState、Radiance ShaderRegistry、MCVR shader.cpp

**回退与损失**：未映射 shader 回退至对应 vanilla/PBR material 时会丢自定义光照/形变；保留几何和用户选择的名称牌/发光判定数据。

**验收**：静态核对每个自定义 shader 的输入、输出、uniform 与 native layout；动态 reload 后 warmup、绘制一帧并检查 shader/descriptor 无旧 generation。

**状态**：静态：静态风险：现有 Vulkan shader cache/translator 存在，但 Veil block/processor 映射未证实；动态：待验证：实际 custom shader 的 native registration 与 uniform 值；裁决：待重构

### CG-V07｜Sable｜客户端初始化与 renderer 选择

**版本**：2.0.5 (mc1.21.1-neoforge; ref 6966d2928340de7631abcecf8549904b877df0a8)

**源码证据**：

- REF D:/Workspaces/References/minecraft-references/sable-2.0.5/src/neoforge/src/main/java/dev/ryanhcode/sable/neoforge/SableNeoForgeClient.java:19-38 — SableClient.init、config load/reload、SubLevelRenderDispatcher reload listener、Flywheel warning
- REF D:/Workspaces/References/minecraft-references/sable-2.0.5/src/common/src/main/java/dev/ryanhcode/sable/SableClient.java:17-42 — Veil renderer available、shader processors、sky-light shadow callback、gizmo init
- REF D:/Workspaces/References/minecraft-references/sable-2.0.5/src/common/src/main/java/dev/ryanhcode/sable/sublevel/render/SubLevelRenderer.java:20-34,36-74,77-99 — static DEFAULT、dispatcher 创建/切换/free、SodiumCompat 选择
- CUR D:/Workspaces/Repositories/GitHub/RecRivenVI/Radiance/src/main/java/com/radiance/compatibility/sable/SableSubLevelBridge.java:318-346,350-401 — 反射获取 container/HELPER/sublevels
- CUR D:/Workspaces/Repositories/GitHub/RecRivenVI/Radiance/src/main/java/com/radiance/mixins/vulkan_render_integration/WorldRendererMixins.java:168-171,244-250 — 世界入口调用 setupRender 与 Sable bridge

**入口链**：SableNeoForgeClient -> SableClient.init -> Veil callbacks/processors -> SubLevelRenderer static selection -> ClientSubLevel.renderData -> Radiance 反射获取 container/renderData/pose。

**所需 GL/源能力**：Sable renderer dispatcher、Veil event platform、NeoForge reload/config bus、SodiumCompat/Flywheel presence

**当前 Vulkan 承接**：Radiance 从世界帧进入 SableSubLevelBridge，只识别精确的 VanillaChunkedSubLevelRenderData/VanillaSingleSubLevelRenderData，并把 sections/pose 交给 ChunkProxy。

**缺口**：单一依赖版本的类名、dispatcher 选择、reload 时序、Sodium/Flywheel 分支和 Veil callback 可达性尚未形成一个版本化 contract；不能把“反射未抛错”当成完整兼容。

**影响**：P1

**方案**：把 Sable renderer variant、method signatures、reload/free 顺序和 Veil availability 固化为 capability contract；对不支持的 renderData 明确降级并保留可诊断状态。

**共享依赖**：SableClient/NeoForge client、Veil platform、Sodium/Flywheel、Radiance Sable bridge、MCVR chunk pipeline

**回退与损失**：缺失 variant 时可仅渲染 Radiance 主世界并隐藏对应子层 geometry，代价应显式记录；不要静默把所有 sublevel 当普通世界。

**验收**：动态无 Sodium/有 Sodium、世界进入/退出、config reload、renderer 切换各跑一次并确认 renderData close/create 与 native external section 一致。

**状态**：静态：静态入口与精确类名承接存在；Veil callback 与 variant 完整性未证实；动态：待验证：不同运行时组合的 dispatcher/renderData 生命周期；裁决：待确认

### CG-V08｜Sable｜子层可见性与 culling

**版本**：2.0.5 (mc1.21.1-neoforge; ref 6966d2928340de7631abcecf8549904b877df0a8)

**源码证据**：

- REF D:/Workspaces/References/minecraft-references/sable-2.0.5/src/common/src/main/java/dev/ryanhcode/sable/sublevel/render/dispatcher/VanillaSubLevelRenderDispatcher.java:130-133 — updateCulling 仍是 TODO 空实现
- REF D:/Workspaces/References/minecraft-references/sable-2.0.5/src/common/src/main/java/dev/ryanhcode/sable/mixin/sublevel_render/impl/vanilla/LevelRendererMixin.java:61-76 — setupRender 注入调用 dispatcher.preRenderChunks/updateCulling
- REF D:/Workspaces/References/minecraft-references/sable-2.0.5/src/common/src/main/java/dev/ryanhcode/sable/sublevel/render/vanilla/VanillaChunkedSubLevelRenderData.java:131-190,286-361 — section grid/compiled sections/render loop
- CUR D:/Workspaces/Repositories/GitHub/RecRivenVI/Radiance/src/main/java/com/radiance/mixins/vulkan_render_integration/WorldRendererMixins.java:168-171,244-246 — setupRender 后更新 bridge，再 ChunkProxy.rebuild
- CUR D:/Workspaces/Repositories/GitHub/RecRivenVI/Radiance/src/main/java/com/radiance/client/proxy/world/ChunkProxy.java:195-238,267-358 — external section 同步、释放、重建

**入口链**：LevelRenderer.setupRender -> Sable dispatcher.updateCulling -> sublevel section visibility -> compiled section draw；Radiance 仍触发 setupRender 的 Sable mixin，再由 ChunkProxy 维护 active external sections。

**所需 GL/源能力**：CullFrustum、Sable 子层 bounds/occlusion graph、section compiled state

**当前 Vulkan 承接**：ChunkProxy 能同步 active section、处理 dirty/rebuild，并将 geometry 交给 native；没有证据表明 Sable TODO 已被 Radiance 的 native rebuild 代替为等价 culling。

**缺口**：源 dispatcher 的核心 culling 入口没有实现，可能导致每个子层全部 section 进入 rebuild/draw 或错误可见；这是可证实的静态缺口，实际漏画/过度绘制待动态确认。

**影响**：P1

**方案**：补齐按子层 bounds、frustum 和 section compiled state 的可见性 contract，或明确由 MCVR 接管且提供同等输入；不要用全局禁用子层渲染规避。

**共享依赖**：Sable Vanilla/ReachAround dispatcher、Minecraft LevelRenderer setupRender、Radiance ChunkProxy、MCVR chunk culling

**回退与损失**：未补齐时可暂保 geometry 但出现性能和遮挡偏差；默认不牺牲子层主功能。

**验收**：用旋转/缩放/远近/水中子层测试可见 section 数、dirty rebuild、遮挡边界和 native draw list 与源 cull 结果。

**状态**：静态：已证实：Sable Vanilla dispatcher.updateCulling 为空；动态：待验证：Radiance/MCVR 是否在其他层提供等价 culling；裁决：待修复

### CG-V09｜Sable｜chunk compile 与 section mesh 承接

**版本**：2.0.5 (mc1.21.1-neoforge; ref 6966d2928340de7631abcecf8549904b877df0a8)

**源码证据**：

- REF D:/Workspaces/References/minecraft-references/sable-2.0.5/src/common/src/main/java/dev/ryanhcode/sable/sublevel/render/vanilla/VanillaChunkedSubLevelRenderData.java:203-237 — dirty section 近处同步/远处异步编译
- REF D:/Workspaces/References/minecraft-references/sable-2.0.5/src/common/src/main/java/dev/ryanhcode/sable/sublevel/render/vanilla/VanillaChunkedSubLevelRenderData.java:286-352 — renderChunkedSubLevel 设置模型矩阵、ChunkOffset/SkyLight uniform 后绑定 VertexBuffer
- REF D:/Workspaces/References/minecraft-references/sable-2.0.5/src/common/src/main/java/dev/ryanhcode/sable/sublevel/render/dispatcher/VanillaSubLevelRenderDispatcher.java:136-216 — layer render 与 single-block 延后 mesh
- CUR D:/Workspaces/Repositories/GitHub/RecRivenVI/Radiance/src/main/java/com/radiance/client/proxy/world/ChunkProxy.java:361-450,561-579 — rebuildSingle 捕获 section layer 并创建 native external chunk
- CUR D:/Workspaces/Repositories/GitHub/RecRivenVI/Radiance/src/main/java/com/radiance/compatibility/sable/SableSubLevelBridge.java:75-105,113-159 — chunk sections transform 与 single-block capture

**入口链**：Sable plot bounds -> renderData.resize/compileSections -> RenderSection.CompiledSection/VertexBuffer -> dispatcher.renderSectionLayer；Radiance 复用 Minecraft 编译结果，ChunkProxy rebuildSingle 将 layer consumer 数据送入 native。

**所需 GL/源能力**：Minecraft RenderType layer、VertexBuffer/CompiledSection、ChunkOffset、SableSkyLightScale、模型/法线/纹理格式

**当前 Vulkan 承接**：ChunkProxy 有 external native id、dirty queue、rebuild task 与 transform；SableBridge 对 chunked/single 两路均有入口，当前 world loop 遍历 RenderType.chunkBufferLayers。

**缺口**：仍需证明 custom RenderType 的 PBR material/alpha、compiled empty 状态、chunk offset 与 sublevel pose 在 native TLAS 中一一对应；静态承接不等于层语义完整。

**影响**：P1

**方案**：保留两路 section capture，补充 layer/material/compiled-state contract 与 generation-safe release；单块路径继续使用 double precision origin 处理。

**共享依赖**：Sable RenderSection/dispatcher、Minecraft BlockRenderDispatcher、Radiance ChunkProxy/SableBridge、MCVR world geometry

**回退与损失**：某层无法翻译时只回退该层或记录缺失 material；不要清空整套 sublevel section。

**验收**：动态核对普通方块、fluid、透明、自定义 Aeronautics/Simulated layer 的 section rebuild、纹理、法线、alpha、pose 与 native ray hit。

**状态**：静态：静态承接存在；custom layer/native material 完整性未证实；动态：待验证：异步 compile、resize、dirty 与 native resource lifetime；裁决：待确认

### CG-V10｜Sable｜实体 contained/tracking 位姿

**版本**：2.0.5 (mc1.21.1-neoforge; ref 6966d2928340de7631abcecf8549904b877df0a8)

**源码证据**：

- REF D:/Workspaces/References/minecraft-references/sable-2.0.5/src/common/src/main/java/dev/ryanhcode/sable/sublevel/ClientSubLevel.java:120-144,313-328 — tick 更新 pose/bounds，renderPose 对位置/方向/rotationPoint/scale 插值
- REF D:/Workspaces/References/minecraft-references/sable-2.0.5/src/common/src/main/java/dev/ryanhcode/sable/mixin/sublevel_render/impl/vanilla/LevelRendererMixin.java:80-97 — isSectionCompiled 按 containing sublevel 查询
- CUR D:/Workspaces/Repositories/GitHub/RecRivenVI/Radiance/src/main/java/com/radiance/compatibility/sable/SableSubLevelBridge.java:206-249 — contained 与 tracking entity 两分支、last/logical/render pose、passenger 分支
- CUR D:/Workspaces/Repositories/GitHub/RecRivenVI/Radiance/src/main/java/com/radiance/client/proxy/world/EntityProxy.java:282-568 — queueEntitiesBuild 调 Sable bridge 并生成 entity render data
- CUR D:/Workspaces/Repositories/GitHub/RecRivenVI/Radiance/src/main/java/com/radiance/compatibility/simulated/SimulatedDiagramCompatibility.java:351-369 — diagram 保留 Simulated LevelRenderer.renderEntity 路径

**入口链**：实体筛选 -> EntityProxy.queueEntitiesBuild -> entityRenderTransform：contained 用 world position + renderPose，tracking 用 last/logical inverse transform 后插值再套 renderPose；passenger 被单独排除。

**所需 GL/源能力**：Sable Pose3d/Quaternion/scale/rotationPoint、Minecraft EntityRenderDispatcher 的原点与 passenger 语义

**当前 Vulkan 承接**：EntityProxy 将几何/文本/outline capture 到 native，并在 diagram 中通过 invoker 保留 Simulated/Sable 的原始 renderEntity 注入。

**缺口**：两条 Sable 分支和 passenger 特殊行为需要与 Minecraft dispatcher 的平移顺序保持一致；静态代码没有证明载具、冻结实体、跨子层 tracking 的所有组合。

**影响**：P1

**方案**：保留 contained/tracking 分支，建立 entity origin/pose/rotation contract；对 passenger 继续遵循源语义并仅在证据支持时扩大适配。

**共享依赖**：Sable ClientSubLevel、Minecraft EntityRenderDispatcher、Radiance EntityProxy、Simulated diagram entity path

**回退与损失**：未承接实体只影响对应 entity rays/visuals；不要通过禁止所有实体解决变换不确定性。

**验收**：动态测试 contained、tracking、passenger、冻结实体、旋转缩放子层和 diagram 中实体的 world origin、法线、文本/outline。

**状态**：静态：静态桥接逻辑存在，未发现可直接认定的数学错误；动态：待验证：复杂 tracking/passenger 与 native hit/visual parity；裁决：待确认

### CG-V11｜Sable｜block entity 与 dispatcher camera

**版本**：2.0.5 (mc1.21.1-neoforge; ref 6966d2928340de7631abcecf8549904b877df0a8)

**源码证据**：

- REF D:/Workspaces/References/minecraft-references/sable-2.0.5/src/common/src/main/java/dev/ryanhcode/sable/mixin/sublevel_render/block_entity_render/LevelRendererMixin.java:60-100 — 构造 VanillaSubLevelBlockEntityRenderer，原版 renderLevel 中按 sublevel 变换并追加 renderBlockEntities
- REF D:/Workspaces/References/minecraft-references/sable-2.0.5/src/common/src/main/java/dev/ryanhcode/sable/sublevel/render/dispatcher/VanillaSubLevelRenderDispatcher.java:218-255 — chunked/single block entities、camera position 与 transformation
- CUR D:/Workspaces/Repositories/GitHub/RecRivenVI/Radiance/src/main/java/com/radiance/compatibility/sable/SableSubLevelBridge.java:259-300 — 反射收集 loaded chunks 的 block entities，设置 Sable camera，再 queueTransformedBlockEntities
- CUR D:/Workspaces/Repositories/GitHub/RecRivenVI/Radiance/src/main/java/com/radiance/mixins/vulkan_render_integration/WorldRendererMixins.java:285-293 — 主 block entity rebuild 后追加 Sable queue

**入口链**：WorldRenderer 主流程 -> vanilla/global BE queue -> SableSubLevelBridge.queueBlockEntities -> collect loaded chunk BEs -> local camera/transform -> EntityProxy.queueTransformedBlockEntities；原版 Sable LevelRenderer renderLevel 被 Radiance 取消。

**所需 GL/源能力**：BlockEntityRenderDispatcher camera position、PoseStack transform、render section compiled block entity list、Create/Flywheel BE extension

**当前 Vulkan 承接**：Radiance 有独立 queueTransformedBlockEntities 与 native storage；Sable dispatcher 的 chunked/single 两路逻辑被反射 bridge 重建。

**缺口**：需要确认 globalBlockEntities、compiled renderable BEs、Flywheel embedding 与 Sable loaded-chunk BEs 不重复、不漏；反射失败时当前仅日志/跳过，不能将空结果判定为成功。

**影响**：P1

**方案**：保留独立 Sable BE queue，建立去重、相机恢复、异常 finally 与 renderData generation contract；把支持范围写成 chunked/single/Flywheel variant。

**共享依赖**：Sable BE mixins/dispatcher、Minecraft BlockEntityRenderDispatcher、Radiance EntityProxy/SableBridge、Flywheel embedding

**回退与损失**：缺失 BE 时只丢对应 block entity visual/ray；保留方块 geometry 和用户选择的 outline/emission。

**验收**：动态测试普通 BE、包含 BE、single-block BE、Flywheel BE、世界切换/异常退出，核对每个 BE 一次、camera restore 和 native resource release。

**状态**：静态：静态入口与 finally 清理存在；重复/漏项需运行验证；动态：待验证：global/compiled/embedding 组合；裁决：待确认

### CG-V12｜Sable/Flywheel｜sky-light 与 Flywheel lighting scene

**版本**：2.0.5 (mc1.21.1-neoforge; ref 6966d2928340de7631abcecf8549904b877df0a8)

**源码证据**：

- REF D:/Workspaces/References/minecraft-references/sable-2.0.5/src/common/src/main/java/dev/ryanhcode/sable/sublevel/ClientSubLevel.java:143-178,184-217 — sky-light scale 缓存、亮度采样、scaleLightColor
- REF D:/Workspaces/References/minecraft-references/sable-2.0.5/src/neoforge/src/main/java/dev/ryanhcode/sable/neoforge/compatibility/flywheel/SableFlywheelLightStorage.java:44-70,184-206,300-313 — scene frame plan、section collection、changed section upload
- REF D:/Workspaces/References/minecraft-references/sable-2.0.5/src/neoforge/src/main/java/dev/ryanhcode/sable/neoforge/compatibility/flywheel/SableFlywheelEmbeddingUniforms.java:3-6 — lighting scene/matrix/sky scale uniform 名称
- REF D:/Workspaces/References/minecraft-references/sable-2.0.5/src/neoforge/src/main/resources/assets/flywheel/flywheel/internal/instancing/main.vert:9-28 — instancing storage buffer 输入
- REF D:/Workspaces/References/minecraft-references/sable-2.0.5/src/neoforge/src/main/resources/assets/flywheel/flywheel/internal/indirect/main.vert:8-20 — indirect storage buffer 输入
- CUR D:/Workspaces/Repositories/GitHub/RecRivenVI/Radiance/src/main/java/com/radiance/compatibility/simulated/SimulatedDiagramCompatibility.java:186-219,319-348 — LightTexture diagram scale 与 Visualization/Flywheel embedding 分支

**入口链**：ClientSubLevel.tick -> computeSubLevelSkyLight/cache -> Sable/Flywheel LightStorage scene plan -> changed section buffer upload -> Flywheel shader uniforms/instancing/indirect vertices；diagram 另行切换 light texture 并绘制 embedding。

**所需 GL/源能力**：OpenGL GlBuffer upload、Flywheel storage/indirect buffers、Sable shader processor uniforms、light texture/sky scale

**当前 Vulkan 承接**：Radiance 世界 UBO/PBR material 与 diagram 的 LightTextureExtension 已有承接；当前未见针对 Flywheel LightStorage 的通用 MCVR storage/indirect descriptor bridge。

**缺口**：Flywheel 的 GL buffer、scene id、matrix/sky uniforms 与 native TLAS/descriptor 的映射未证实；Sable 自己也警告其 full light-storage replacement 可能与 Flywheel 冲突。

**影响**：P1

**方案**：保留 Sable sky-light semantics；为 Flywheel scene/embedding 定义 native buffer/uniform contract，无法映射时只降级该 embedding 的 lighting，不能把所有 BE 禁用。

**共享依赖**：SableFlywheelLightStorage/EmbeddingUniforms、Flywheel instancing/indirect shader、Radiance diagram/EntityProxy、MCVR descriptors

**回退与损失**：降级会损失动态/子层光照精度，保留 geometry、普通 light texture 与用户发光效果。

**验收**：动态分别测试无 Flywheel、有 Flywheel、diagram embedding、场景重建和光照变化，检查 scene buffer generation 与 native descriptors。

**状态**：静态：静态风险：源明确使用 GL buffer/indirect uniforms；当前 Radiance 只在 diagram 走 embedding 分支；动态：待验证：Flywheel world/diagram 的 native承接；裁决：待重构

### CG-V13｜Sable/Radiance｜block outline 与 hitbox/debug lines

**版本**：2.0.5 (mc1.21.1-neoforge; ref 6966d2928340de7631abcecf8549904b877df0a8)

**源码证据**：

- REF D:/Workspaces/References/minecraft-references/sable-2.0.5/src/common/src/main/java/dev/ryanhcode/sable/mixin/debug_render/LevelRendererMixin.java:34-126 — RenderType.LINES 绘制 global bounds、rotation point、plot bounds、interpolation snapshots
- REF D:/Workspaces/References/minecraft-references/sable-2.0.5/src/neoforge/src/main/java/dev/ryanhcode/sable/neoforge/mixin/block_outline_render/LevelRendererMixin.java:38-112 — renderLevel hit outline、SubLevelCamera.setCamera/setPose/clear
- CUR D:/Workspaces/Repositories/GitHub/RecRivenVI/Radiance/src/main/java/com/radiance/compatibility/sable/SableSubLevelBridge.java:166-204,436-473,607-628 — blockOutlineContext、SubLevelCamera 与 event/local transform
- CUR D:/Workspaces/Repositories/GitHub/RecRivenVI/Radiance/src/main/java/com/radiance/client/proxy/world/EntityProxy.java:1093-1146,1199-1283 — debug line/target outline capture 与 Sable context
- CUR D:/Workspaces/Repositories/GitHub/RecRivenVI/Radiance/src/main/java/com/radiance/client/vertex/PBRVertexConsumer.java:51-62,192-213 — 文本模式/后处理材质编码

**入口链**：选中方块/实体 debug -> Sable blockOutlineContext -> adapted camera 或 local transform -> EntityProxy outline/debug capture -> MCVR PBR line/outline；Sable debug bounds 另走 RenderType.LINES。

**所需 GL/源能力**：Blaze3D PoseStack/RenderType.LINES、Sable SubLevelCamera、line depth/width/color state

**当前 Vulkan 承接**：Radiance 已有 target outline、debug line storage 和 PBR material routing；用户明确选择的名称牌 50% 与判定箱 PBR 发光属于必须默认保留的产品效果，普通 debug line 是独立的线框路径。

**缺口**：需要动态确认 adapted camera、localCamera、rotationPoint 和 PBR line emission 在子层/旋转/缩放下对齐；不能以取消 debug 或发光来规避坐标问题。

**影响**：P1

**方案**：保留判定箱 PBR 发光默认语义，普通 debug line 单独按其 source 颜色/深度语义处理；只修复有证据的 camera/transform/material 参数，禁止建议撤掉名称牌 50% 或判定箱 PBR 发光。

**共享依赖**：Sable block outline/debug mixins、Radiance SableBridge/EntityProxy/PBRVertexConsumer、MCVR debug/outline shaders

**回退与损失**：若某个普通 debug line source 不可映射，只丢该 source；用户选择的名称牌 50% 与判定箱 PBR 发光仍应有 Vulkan path。

**验收**：动态检查主世界/子层选中方块、bounds、interpolation bounds、实体 hitbox、name-tag alpha 与 PBR emission。

**状态**：静态：静态保留路径存在；视觉/坐标 parity 未验证；动态：待验证：所有 outline/debug line route 与 native emission；裁决：保留

### CG-V14｜Aeronautics｜Levitite custom chunk layers

**版本**：1.3.2 bundled untagged main snapshot (ref e720946bb5e84d618d0f1aa47e1a19ab2adb5bd7; aeronautics/simulated/offroad)

**源码证据**：

- REF D:/Workspaces/References/minecraft-references/aeronautics_bundled-1.3.2/src/aeronautics/neoforge/src/main/java/dev/eriksonn/aeronautics/neoforge/events/AeroNeoForgeClientEvents.java:49-75 — clientSetup 注册 levitite/ghosts ChunkRenderTypeSet、修复 layer ids、注册 stage
- REF D:/Workspaces/References/minecraft-references/aeronautics_bundled-1.3.2/src/aeronautics/common/src/main/java/dev/eriksonn/aeronautics/index/client/AeroRenderTypes.java:19-66,87-118 — BLOCK format、translucent/cull/no-cull、Veil shaderState、color/depth mask output
- REF D:/Workspaces/References/minecraft-references/aeronautics_bundled-1.3.2/src/aeronautics/common/src/main/java/dev/eriksonn/aeronautics/AeronauticsClient.java:26-48 — Veil block layer/fixed buffer 注册到 AFTER_BLOCK_ENTITIES/AFTER_WEATHER
- CUR D:/Workspaces/Repositories/GitHub/RecRivenVI/Radiance/src/main/java/com/radiance/mixins/compatibility/aeronautics/AeronauticsNeoForgeClientSetupMixins.java:28-52 — custom layers、chunkLayerId、ChunkRenderTypeSet cache
- CUR D:/Workspaces/Repositories/GitHub/RecRivenVI/Radiance/src/main/java/com/radiance/mixins/vulkan_render_integration/WorldRendererMixins.java:244-250 — 遍历 RenderType.chunkBufferLayers 并 dispatch NeoForge stage

**入口链**：Aero clientSetup -> ChunkRenderTypeSet/BlockRenderTypes -> custom RenderType layer + Veil shaderState -> chunk compile/stage；Radiance 预先同步 layer id/cache 并让世界循环遍历层。

**所需 GL/源能力**：BLOCK vertex format、translucent/cull/depth/color mask、Veil shaderState/patchState；文件有 GL11C glDisable/glEnable import 但本快照未发现调用，实际 state 调用是 RenderSystem。

**当前 Vulkan 承接**：Radiance 承接 custom layer 的枚举与 NeoForge stage dispatch，ChunkProxy 可捕获 layer 顶点；没有 Aero levitite shader 的 Vulkan material/patch translation。

**缺口**：layer registration 静态存在，但其 Veil shaderState 在 renderer init/stage 被跳过；ghosts 的 no-cull、透明、mask 和 shader output 不能仅凭 layer id 视为已兼容。

**影响**：P1

**方案**：保留 custom layer registration 与块的渲染层选择；为 levitite/ghosts 提供明确 Vulkan material/state，或仅门控该效果 layer 并保留块的基础模型路径。

**共享依赖**：Aeronautics NeoForge setup、Veil stage/fixed buffer、Minecraft chunk layers、Radiance ChunkProxy/MCVR material

**回退与损失**：未适配时可丢 levitite shader overlay/ghost 视觉，保留基础 block geometry；不得禁用 Aeronautics blocks/fluids 主功能。

**验收**：动态检查 solid/translucent/ghost layer 的 cull、depth/color mask、纹理、PBR、sublevel 与 native ray hit。

**状态**：静态：静态确认层 id/cache 与 stage 枚举承接；shader/state 语义未证实；动态：待验证：custom layer native material；裁决：待确认

### CG-V15｜Aeronautics｜热气球 soft-light 离屏效果

**版本**：1.3.2 bundled untagged main snapshot (ref e720946bb5e84d618d0f1aa47e1a19ab2adb5bd7; aeronautics/simulated/offroad)

**源码证据**：

- REF D:/Workspaces/References/minecraft-references/aeronautics_bundled-1.3.2/src/aeronautics/common/src/main/java/dev/eriksonn/aeronautics/content/blocks/hot_air/balloon/effect/ClientBalloonEffectRenderer.java:28-76 — AFTER_SOLID_BLOCKS 入口、AeroConfig、BalloonMap、overlayFbo 创建/resize
- REF D:/Workspaces/References/minecraft-references/aeronautics_bundled-1.3.2/src/aeronautics/common/src/main/java/dev/eriksonn/aeronautics/content/blocks/hot_air/balloon/effect/ClientBalloonEffectRenderer.java:86-162 — shader/uniform、textures、front cull/polygon offset、overlay FBO、soft_light post
- CUR D:/Workspaces/Repositories/GitHub/RecRivenVI/Radiance/src/main/java/com/radiance/mixins/vulkan_render_integration/WorldRendererMixins.java:239-250,294-333 — 现有 NeoForge stage 列表没有 Aero Veil stage 适配
- CUR D:/Workspaces/Repositories/GitHub/RecRivenVI/Radiance/src/main/java/com/radiance/mixins/compatibility/veil/VeilNeoForgeEventPlatformMixins.java:14-19 — Veil onVeilRenderLevelStage 入口被取消

**入口链**：AeronauticsClient.registerEvents -> VeilRenderLevelStage AFTER_SOLID_BLOCKS -> BalloonMap -> AdvancedFbo overlay + hot_air_overlay shader/uniform -> soft_light PostPipeline。

**所需 GL/源能力**：AdvancedFbo color/depth、GL cull/polygon offset、两张热 overlay texture、shader uniforms、post pipeline

**当前 Vulkan 承接**：Radiance 世界循环当前没有 AFTER_SOLID_BLOCKS 的 Veil callback 路径，也没有 soft_light overlay target/post adapter。

**缺口**：这是具体的静态不可达/缺失链：balloon mechanics 可存在，但热气球 soft-light 视觉不会因现有通用 world stage 自动进入 MCVR。

**影响**：P1

**方案**：为 AFTER_SOLID_BLOCKS 建立有明确 target/material/uniform 的 Vulkan stage，或门控该视觉入口并保留 balloon mechanics/block geometry。

**共享依赖**：Aeronautics BalloonMap/ClientBalloon、Veil stage、MCVR overlay/post target、Radiance stage dispatch

**回退与损失**：回退损失仅是热气 overlay/soft-light；不得通过关闭热气球、流体或 Aeronautics 主系统实现“通过”。

**验收**：动态开关 config、空/非空 BalloonMap、resize、resource reload、世界退出，核对 overlay 目标和 post 生命周期。

**状态**：静态：已证实：源入口依赖 Veil AFTER_SOLID_BLOCKS + AdvancedFbo；当前没有对应 dispatch/adapter；动态：待验证：无客户端动态测试；裁决：待修复

### CG-V16｜Simulated｜客户端 stage/fixed buffer bootstrap

**版本**：1.3.2 bundled untagged main snapshot (ref e720946bb5e84d618d0f1aa47e1a19ab2adb5bd7; aeronautics/simulated/offroad)

**源码证据**：

- REF D:/Workspaces/References/minecraft-references/aeronautics_bundled-1.3.2/src/simulated/neoforge/src/main/java/dev/simulated_team/simulated/neoforge/SimulatedNeoForgeClient.java:14-25 — 单一客户端 Mod 入口注册 NeoForge events/Plunger handler 后调用 SimulatedClient.init
- REF D:/Workspaces/References/minecraft-references/aeronautics_bundled-1.3.2/src/simulated/common/src/main/java/dev/simulated_team/simulated/SimulatedClient.java:20-47 — partial models/resources、EndSea stage、shader processor、AFTER_PARTICLES/LENS/AFTER_LEVEL fixed buffers、Simulated stage
- REF D:/Workspaces/References/minecraft-references/aeronautics_bundled-1.3.2/src/simulated/common/src/main/java/dev/simulated_team/simulated/events/SimulatedCommonClientEvents.java:106-108 — Veil stage 消费 PhysicsStaff selection
- CUR D:/Workspaces/Repositories/GitHub/RecRivenVI/Radiance/src/main/java/com/radiance/mixins/compatibility/veil/VeilNeoForgeEventPlatformMixins.java:11-19 — Veil stage listener registration/dispatch 入口取消
- CUR D:/Workspaces/Repositories/GitHub/RecRivenVI/Radiance/src/main/java/com/radiance/mixins/vulkan_render_integration/WorldRendererMixins.java:240-331 — 当前显式 dispatch AFTER_SKY、custom layer、AFTER_ENTITIES/BLOCK_ENTITIES/PARTICLES/WEATHER

**入口链**：SimulatedNeoForgeClient 单 JAR -> SimulatedClient.init -> VeilEventPlatform 注册 stage/fixed buffers/shader processor；当前 Veil stage dispatch 被取消，Radiance 只显式发 NeoForge 标准阶段。

**所需 GL/源能力**：Veil event platform、fixed buffer registry、NeoForge RenderLevelStage、stage matrix/camera/frustum、custom RenderType

**当前 Vulkan 承接**：Radiance 标准阶段 dispatch 能覆盖部分 NeoForge listener，但没有把 VeilEventPlatform 的 AFTER_LEVEL/AFTER_PARTICLES/LENS/STAFF/PhysicsStaff consumers 自动迁移到同一 scope。

**缺口**：不能把单 JAR bootstrap 描述为 patched sidecar；静态实际缺口是官方 SPI callback 的消费者没有对应 Vulkan stage contract。

**影响**：P0

**方案**：保留官方单 JAR/SPI bootstrap，建立 Veil stage 到 Vulkan/NeoForge stage 的明确映射并为每个 fixed buffer 提供 material/target contract；不要删除主功能以避开 callback。

**共享依赖**：SimulatedClient、VeilEventPlatform、NeoForge ClientHooks、Radiance WorldRenderer、Aeronautics/Sable 共用 stage

**回退与损失**：未映射的 stage 只影响对应 overlay/selection/shadow；保留 Simulated world/entities/diagram 与用户选择的名称牌/发光判定。

**验收**：动态逐一触发 AFTER_LEVEL、AFTER_PARTICLES、AFTER_TRANSLUCENT_BLOCKS、AFTER_BLOCK_ENTITIES、AFTER_WEATHER，确认 listener 次序、camera、buffer 与 native queue。

**状态**：静态：已证实：单 JAR 官方入口存在；Veil stage 入口当前被取消；动态：待验证：各 Veil stage 的 Vulkan 映射；裁决：待修复

### CG-V17｜Simulated｜自定义 RenderType、纹理与 Spring 顶点

**版本**：1.3.2 bundled untagged main snapshot (ref e720946bb5e84d618d0f1aa47e1a19ab2adb5bd7; aeronautics/simulated/offroad)

**源码证据**：

- REF D:/Workspaces/References/minecraft-references/aeronautics_bundled-1.3.2/src/simulated/common/src/main/java/dev/simulated_team/simulated/index/SimRenderTypes.java:18-58,83-105,113-143 — staff/laser/lens/rope/spring shaderState、texture、format、layer getters
- CUR D:/Workspaces/Repositories/GitHub/RecRivenVI/Radiance/src/main/java/com/radiance/client/vertex/StorageVertexConsumerProvider.java:14-60 — QUADS 走 SimulatedVertexCompatibility，其他 mode 走 BufferBuilder
- CUR D:/Workspaces/Repositories/GitHub/RecRivenVI/Radiance/src/main/java/com/radiance/compatibility/simulated/SimulatedVertexCompatibility.java:13-51 — spring layer 识别、PBR Spring consumer、alpha/stress 转换
- CUR D:/Workspaces/Repositories/GitHub/RecRivenVI/Radiance/src/main/java/com/radiance/client/vertex/PBRVertexConsumer.java:94-141,152-189 — texture id、alpha mode、transmission/additive/coverage
- CUR D:/Workspaces/Repositories/GitHub/RecRivenVI/Radiance/src/main/java/com/radiance/mixins/compatibility/simulated/SimulatedVeilRenderSystemMixins.java:24-49 — 仅 diagram scope 下按路径选择 vanilla shader fallback

**入口链**：Simulated entity/item/stage -> SimRenderTypes shaderState/texture -> MultiBufferSource.getBuffer -> StorageVertexConsumerProvider -> PBR/BufferBuilder -> EntityProxy/ChunkProxy -> MCVR texture/material。

**所需 GL/源能力**：Veil shaderState、BLOCK/POSITION_TEX_COLOR/POSITION_COLOR/Spring custom format、texture atlas/sampler、transparency/cutout/lightmap/overlay

**当前 Vulkan 承接**：Radiance 已解析部分 PBR alpha/texture id，并把 spring 的 COLOR.a 当 stress 而非 opacity；MCVR 有 Vulkan textures/samplers/upload。diagram fallback 只在 diagram scope 生效。

**缺口**：staffOverlay/lens/rope/laser/spring 的 custom shader 与非 QUADS buffer、纹理寻址、lightmap/overlay 语义仍需逐层验证；不能假定 setShader fallback 覆盖世界阶段。

**影响**：P1

**方案**：保留所有 custom layer 的几何/纹理来源；为每个 format/state/shader 建立 material mapping，spring 保持 cutout 与 stress 语义，缺失时只降级该 layer。

**共享依赖**：Simulated SimRenderTypes/VeilRenderBridge、Radiance Storage/PBRVertexConsumer、MCVR TextureProxy/ShaderRegistry、Create/Catnip buffers

**回退与损失**：回退可损失 laser/staff/rope/spring 特殊 shading，但保留可见几何和基础纹理；不得通过禁用整套 Simulated renderer。

**验收**：动态逐层检查 world、diagram、first-person、sublevel、resource reload 的 vertex stride、texture id、alpha/cutout、lightmap/overlay 与 native descriptor。

**状态**：静态：静态承接部分已存在；custom shader/state 和 non-QUADS semantics 未证实；动态：待验证：各 layer 的 native material parity；裁决：待确认

### CG-V18｜Simulated｜contraption diagram FBO/post 替换

**版本**：1.3.2 bundled untagged main snapshot (ref e720946bb5e84d618d0f1aa47e1a19ab2adb5bd7; aeronautics/simulated/offroad)

**源码证据**：

- REF D:/Workspaces/References/minecraft-references/aeronautics_bundled-1.3.2/src/simulated/common/src/main/java/dev/simulated_team/simulated/content/entities/diagram/screen/DiagramScreen.java:173-178,337-389,391-463,742-746 — 三个 AdvancedFbo、glReadPixels greeble collision、renderChain、diagram PostPipeline、renderFBO
- REF D:/Workspaces/References/minecraft-references/aeronautics_bundled-1.3.2/src/simulated/common/src/main/java/dev/simulated_team/simulated/content/entities/diagram/screen/DiagramStickyNote.java:91-123,195-252 — note 三个 FBO 的 create/free/populate/render
- CUR D:/Workspaces/Repositories/GitHub/RecRivenVI/Radiance/src/main/java/com/radiance/mixins/compatibility/simulated/SimulatedDiagramScreenMixins.java:34-89 — FBO build 返回 null、read/unbind 跳过、renderFBO 重定向
- CUR D:/Workspaces/Repositories/GitHub/RecRivenVI/Radiance/src/main/java/com/radiance/mixins/compatibility/simulated/SimulatedDiagramStickyNoteMixins.java:43-93 — note FBO 返回 null、widget render 重定向
- CUR D:/Workspaces/Repositories/GitHub/RecRivenVI/Radiance/src/main/java/com/radiance/compatibility/simulated/SimulatedDiagramCompatibility.java:119-160 — DiagramState begin/post 与 diagram post uniform

**入口链**：DiagramScreen.init/create -> AdvancedFbo color/depth -> draw/renderChain -> diagram/outlined/final PostPipeline -> GUI renderFBO；Radiance 用 CPU/UI overlay：renderMain/renderNote -> DiagramState -> MCVR diagram post。

**所需 GL/源能力**：AdvancedFbo color/depth/readback、glReadPixels、post pipeline 的三 target 与 line/palette/fade uniforms

**当前 Vulkan 承接**：MCVR 有 DiagramState begin/post、OverlayPostUBO/BufferProxy.updateDiagramPostUniform 与 diagram overlay shader；当前 bridge 明确不调用源 GL FBO。

**缺口**：这是有意的 Vulkan 方案，但必须证明深度/outline/final 合成与 source diagram pipeline 等价；greeble 的 FBO 像素碰撞已改为 CPU 投影几何，边界/透明物体需验证。

**影响**：P1

**方案**：保留当前 diagram UI replacement 作为主路径，补齐 target/post/CPU hit-test 的 acceptance；不要恢复 GL FBO，也不要把整个 diagram UI 删除。

**共享依赖**：Simulated DiagramScreen/StickyNote、Sable sublevel chain、Radiance DiagramCompatibility/PipelineState、MCVR overlay post

**回退与损失**：若某 post pass 不可用，保留可交互 diagram 与几何，明确损失 outline/palette/fade；设置、力箭头、中心质量等 UI 不应消失。

**验收**：动态打开主 diagram/note、添加 greebles、透明/空几何、resize、close/reopen，核对选中区域、三 target 合成、post uniforms 和资源释放。

**状态**：静态：静态已存在明确 Vulkan replacement，source FBO/readback 不再执行；动态：待验证：视觉/交互 parity 与 post target 生命周期；裁决：保留

### CG-V19｜Simulated/Sable｜diagram 世界几何、fluid、BE 与实体

**版本**：1.3.2 bundled untagged main snapshot (ref e720946bb5e84d618d0f1aa47e1a19ab2adb5bd7; aeronautics/simulated/offroad)

**源码证据**：

- REF D:/Workspaces/References/minecraft-references/aeronautics_bundled-1.3.2/src/simulated/common/src/main/java/dev/simulated_team/simulated/util/SimpleSubLevelGroupRenderer.java:78-150,154-238 — diagram FBO 中 layer、single block、BE、Flywheel、entity render 顺序
- REF D:/Workspaces/References/minecraft-references/aeronautics_bundled-1.3.2/src/simulated/common/src/main/java/dev/simulated_team/simulated/content/entities/diagram/screen/DiagramScreen.java:434-463 — renderChain 后取得 diagram pipeline 并设置三个 framebuffer context
- CUR D:/Workspaces/Repositories/GitHub/RecRivenVI/Radiance/src/main/java/com/radiance/compatibility/simulated/SimulatedDiagramCompatibility.java:163-221,224-290 — local camera、Lighting、Sable chain、block/fluid/model capture
- CUR D:/Workspaces/Repositories/GitHub/RecRivenVI/Radiance/src/main/java/com/radiance/compatibility/simulated/SimulatedDiagramCompatibility.java:292-373 — BE/Flywheel embedding 与 LevelRendererInvoker entity route
- CUR D:/Workspaces/Repositories/GitHub/RecRivenVI/Radiance/src/main/java/com/radiance/client/vertex/StorageVertexConsumerProvider.java:38-75 — diagram buffer 最终由 capture provider finalize

**入口链**：diagram UI -> renderScene -> renderBlocks(fluid/model/single block) -> renderBlockEntitiesAndFlywheel -> renderEntities -> MultiBufferSource -> EntityProxy/MCVR overlay；Sable chain 由 SimpleSubLevelGroupRenderer.getRenderedChain 决定。

**所需 GL/源能力**：Blaze3D RenderSystem modelview/projection、Lighting/light texture、RenderType layers、Flywheel embedding、Sable pose

**当前 Vulkan 承接**：当前 bridge 创建 diagram ByteBuffer/BufferSource，使用 PBR/Storage capture、LevelPerspectiveCamera 与 PipelineStateProxy，并保持 Simulated 的 entity render invoker。

**缺口**：大坐标 double subtraction、子层链、fluid layer、block model data、BE/Flywheel embedding 的组合需要完整验证；当前静态承接不证明 Create/Flywheel 子路径，Explorer 负责其详细覆盖。

**影响**：P1

**方案**：保留 diagram 的 blocks/fluid/BE/entity 顺序与 Sable chain；将每个 layer 的 native material/embedding 交由对应 contract，失败只局部报告。

**共享依赖**：Simulated DiagramCompatibility、Sable ClientSubLevel/dispatcher、Create/Flywheel embedding、Radiance EntityProxy/MCVR overlay

**回退与损失**：回退时可暂丢单个 custom visual/embedding，保留方块/流体基础图和 diagram UI；不能把所有 contraption geometry 清空。

**验收**：动态大坐标连接子层、fluid、single block、普通/嵌入 BE、entity、透明层逐项检查变换、light、native hit/visual。

**状态**：静态：静态链条具体存在且有 double precision 处理；组合材质/embedding 未证实；动态：待验证：完整 diagram geometry parity；裁决：待确认

### CG-V20｜Simulated｜diagram UI、设置与早期/同窗口生命周期

**版本**：1.3.2 bundled untagged main snapshot (ref e720946bb5e84d618d0f1aa47e1a19ab2adb5bd7; aeronautics/simulated/offroad)

**源码证据**：

- REF D:/Workspaces/References/minecraft-references/aeronautics_bundled-1.3.2/src/simulated/neoforge/src/main/java/dev/simulated_team/simulated/neoforge/SimulatedNeoForgeClient.java:14-24 — 单一 @Mod client entry、IConfigScreenFactory、NeoForge event registration、SimulatedClient.init
- REF D:/Workspaces/References/minecraft-references/aeronautics_bundled-1.3.2/src/simulated/common/src/main/java/dev/simulated_team/simulated/content/entities/diagram/screen/DiagramScreen.java:172-240,262-289,391-431 — init/free、设置按钮、旋转/力/COM widgets、renderContents
- CUR D:/Workspaces/Repositories/GitHub/RecRivenVI/Radiance/src/main/java/com/radiance/mixins/compatibility/simulated/SimulatedDiagramScreenMixins.java:55-60,80-89 — camera 更新与同窗口 GUI render replacement
- CUR D:/Workspaces/Repositories/GitHub/RecRivenVI/Radiance/src/main/java/com/radiance/mixins/compatibility/simulated/SimulatedDiagramStickyNoteMixins.java:64-93 — note UI、config、arrow/COM route
- CUR D:/Workspaces/Repositories/GitHub/RecRivenVI/Radiance/src/main/java/com/radiance/compatibility/simulated/SimulatedDiagramCompatibility.java:134-160 — level/root removed guard、GUI scale、DiagramState finally

**入口链**：官方单 JAR NeoForge client entry -> SimulatedClient.init/config/UI -> DiagramScreen.init -> renderContents/window widgets；Radiance 保留 Simulated UI/settings/structure graph，仅替换 scene/FBO 绘制。

**所需 GL/源能力**：Minecraft GuiGraphics、same-window GUI scale、Screen/widget lifecycle、resource/config state；源 renderContents 依赖 Veil perspective/FBO null checks

**当前 Vulkan 承接**：Radiance 用 GUI 尺寸缩放进入 DiagramState，finally 恢复 post scope/projection/modelview；当前没有 sidecar/额外窗口证据。

**缺口**：需要验证屏幕早期开启、level 为 null、subLevel/diagram 被移除、resize、close/reopen 与 resource reload 的顺序；不能把有 UI 替换误写成完整生命周期已证实。

**影响**：P1

**方案**：保留单 JAR 官方 bootstrap、同窗口 UI、设置/结构图交互；以状态 guard 和 generation-safe cleanup 处理早期/重入，不扩大为新窗口或 sidecar。

**共享依赖**：Simulated Screen/StickyNote/config、Minecraft GuiGraphics、Radiance DiagramCompatibility/PipelineState、Sable sublevel lifecycle

**回退与损失**：早期不可渲染时只显示安全 GUI 或等待下一帧，保留设置和结构图入口；不能因早期状态禁用主客户端。

**验收**：动态在无 level、进入 level、世界切换、移除 diagram、resize、重开 note 中检查 UI 不崩、same-window 坐标正确、scope/FBO/native resources 可复用。

**状态**：静态：静态实际为单 JAR 官方 SPI；Radiance 有 level/removed/finally guards；动态：待验证：早期显示、同窗口重入与销毁；裁决：待确认

### CG-V21｜Simulated｜EndSea shadow map 与 AFTER_LEVEL

**版本**：1.3.2 bundled untagged main snapshot (ref e720946bb5e84d618d0f1aa47e1a19ab2adb5bd7; aeronautics/simulated/offroad)

**源码证据**：

- REF D:/Workspaces/References/minecraft-references/aeronautics_bundled-1.3.2/src/simulated/common/src/main/java/dev/simulated_team/simulated/content/end_sea/EndSeaShadowRenderer.java:49-103 — AFTER_LEVEL stage、EndSeaPhysics、shadow FBO、SimpleSubLevelGroupRenderer、spread_end_sea post
- REF D:/Workspaces/References/minecraft-references/aeronautics_bundled-1.3.2/src/simulated/common/src/main/java/dev/simulated_team/simulated/content/end_sea/EndSeaShadowRenderer.java:105-163 — void anchor texture draw 与 getShadowsFramebuffer 读取 Veil renderer
- REF D:/Workspaces/References/minecraft-references/aeronautics_bundled-1.3.2/src/simulated/common/src/main/java/dev/simulated_team/simulated/SimulatedClient.java:33-44 — EndSea shadow stage 与 Simulated stage 注册
- CUR D:/Workspaces/Repositories/GitHub/RecRivenVI/Radiance/src/main/java/com/radiance/mixins/compatibility/veil/VeilNeoForgeEventPlatformMixins.java:14-19 — Veil stage dispatch cancelled
- CUR D:/Workspaces/Repositories/GitHub/RecRivenVI/Radiance/src/main/java/com/radiance/mixins/vulkan_render_integration/WorldRendererMixins.java:240-331 — 显式阶段列表未包含 AFTER_LEVEL

**入口链**：SimulatedClient register EndSeaShadowRenderer -> Veil AFTER_LEVEL -> getShadowsFramebuffer -> shadow SimpleSubLevelGroupRenderer -> spread_end_sea pipeline；当前没有 Veil stage delivery 或 NeoForge AFTER_LEVEL equivalent。

**所需 GL/源能力**：Veil framebuffer manager、AdvancedFbo、orthographic shadow projection、post iterations、void anchor texture

**当前 Vulkan 承接**：当前 MCVR 无 Simulated end_sea framebuffer/post contract；Radiance 的 AFTER_LEVEL stage 也未在 WorldRendererMixins 显式 dispatch。

**缺口**：EndSea shadow/void anchor 的静态入口不可达或会读取空 Veil renderer；这与 diagram replacement 是不同功能，不能用 diagram 已兼容推断。

**影响**：P1

**方案**：为 AFTER_LEVEL 建立专用 native shadow target/post contract，或明确仅回退 EndSea shadow visual；保留 EndSea physics/anchors 和其它 Simulated 主功能。

**共享依赖**：Simulated EndSeaPhysics/ShadowRenderer、Sable sublevel group、Veil post/FBO、Radiance stage/MCVR world shadow

**回退与损失**：回退损失是 EndSea spread shadow/void anchor overlay；不得关闭 EndSea physics 或整套 Simulated。

**验收**：动态有/无 EndSea physics、void anchors、世界切换、resize、renderer close，检查 shadow target、post iteration 和 anchor material。

**状态**：静态：已证实：AFTER_LEVEL/FBO/post 依赖没有当前 stage adapter；动态：待验证：无动态客户端测试；裁决：待修复

### CG-V22｜Offroad｜multi-mining destruction progress

**版本**：1.3.2 bundled untagged main snapshot (ref e720946bb5e84d618d0f1aa47e1a19ab2adb5bd7; aeronautics/simulated/offroad)

**源码证据**：

- REF D:/Workspaces/References/minecraft-references/aeronautics_bundled-1.3.2/src/offroad/common/src/main/java/dev/ryanhcode/offroad/mixin/client/multimining_destruction_progress/LevelRenderMixin.java:23-75 — MultiMiningDestructionExtension、progress removal/addition
- REF D:/Workspaces/References/minecraft-references/aeronautics_bundled-1.3.2/src/offroad/common/src/main/java/dev/ryanhcode/offroad/handlers/client/MultiMiningClientHandler.java:23-35,44-108 — LEVEL_ATTACHED、tick、bulkUpdateDestructionProgress 调 extension
- REF D:/Workspaces/References/minecraft-references/aeronautics_bundled-1.3.2/src/offroad/common/src/main/java/dev/ryanhcode/offroad/handlers/client/MultiMiningClientHandler.java:109-170 — ClientBlockBreakingData tick 的 vanilla particles/sound
- CUR D:/Workspaces/Repositories/GitHub/RecRivenVI/Radiance/src/main/java/com/radiance/mixins/vulkan_render_integration/WorldRendererMixins.java:285-293 — 共享 destructionProgress 进入 queueCrumblingRebuild
- CUR D:/Workspaces/Repositories/GitHub/RecRivenVI/Radiance/src/main/java/com/radiance/client/proxy/world/EntityProxy.java:882-936 — 使用最后 progress stage 调 BlockRenderDispatcher.renderBreakingTexture 并捕获 crumbling layer

**入口链**：Offroad client handler -> LevelRenderer destructionProgress map 中的 MultiMining children -> Radiance WorldRenderer.queueCrumblingRebuild -> 最后 progress stage -> vanilla breaking texture -> shared native WORLD crumbling geometry。

**所需 GL/源能力**：Minecraft ModelBakery.DESTROY_TYPES、SheetedDecalTextureGenerator、vanilla crumbling RenderType/texture；Offroad 源没有专属 FBO/custom shader

**当前 Vulkan 承接**：Radiance 已经消费 LevelRenderer 的共享 destructionProgress 并将 breaking texture 送入 EntityProxy/MCVR；这条路径是共享设施，不应归为 Offroad 专属纹理 bug。

**缺口**：多进度集合取 sortedSet.last() 的语义、子层坐标、距离裁剪和 Offroad removal 清理需要动态核对；静态没有证据认定 Offroad 自身 custom shader/texture 缺失。

**影响**：P1

**方案**：保留共享 crumbling capture，验证 MultiMining map 的 add/remove/last progress 与 native layer；若异常只修共享 progress 消费，不要删除 Offroad multi-mining 或错归模组专属。

**共享依赖**：Offroad MultiMiningClientHandler/LevelRenderer extension、Minecraft destructionProgress、Radiance EntityProxy/WorldRenderer、MCVR crumbling material

**回退与损失**：失败时只丢 breaking overlay/部分 progress visual，保留方块挖掘逻辑、粒子与声音；不能关闭 multi-mining 主功能。

**验收**：动态普通挖掘、多目标 multi-mining、进度移除、子层方块、距离边界，核对每个 stage/texture 与资源释放。

**状态**：静态：静态共享路径已接入；无证据认定 Offroad 专属渲染缺陷；动态：待验证：多 progress 与 native crumbling parity；裁决：待确认

### CG-V23｜Radiance/MCVR｜VkResult 错误传播与生命周期保护

**版本**：Radiance 414d8e330a2fc6cb1e8630cc95f2302b2b97a0e8 / MCVR 9905c81b1999f5845bf66d13501d371c16adf561

**源码证据**：

- CUR D:/Workspaces/Repositories/GitHub/RecRivenVI/MCVR/src/core/middleware/jni_exception.hpp:19-29 — invokeForVkResult 返回 VkResult，异常转 Java IllegalStateException，失败回传 VK_ERROR_UNKNOWN
- CUR D:/Workspaces/Repositories/GitHub/RecRivenVI/MCVR/src/core/render/render_framework.cpp:646-685 — queue/device idle、close、recordFailure 设置 running=false 并保存 operation/VkResult
- CUR D:/Workspaces/Repositories/GitHub/RecRivenVI/MCVR/src/core/render/textures.cpp:74-80,187-209,378-382 — texture release/reload 等待 queue idle 与 fence 错误传播
- CUR D:/Workspaces/Repositories/GitHub/RecRivenVI/Radiance/src/main/java/com/radiance/client/texture/ResourceReloadCoordinator.java:25-81 — reload generation、native begin/end、失败 suppressed、warmup

**入口链**：JNI/native operation -> VkResult/exception -> Framework.recordFailure/lastFailure -> running=false 与 JNI 错误传播；纹理 reload 以 queue idle 和 generation retention 保护旧资源，Java coordinator 在所有 listener 完成后 publish。

**所需 GL/源能力**：Vulkan queue/fence/device lifecycle、resource generation、JNI exception boundary；不依赖 OpenGL

**当前 Vulkan 承接**：MCVR 已有错误返回、lastFailure、queue/device idle 与 reload generation；Radiance 也有 texture/shader generation coordinator，这些是有效业务保护，不是临时日志探针。

**缺口**：Veil/Sable/Simulated 资源若在错误 generation 仍读旧 descriptor 或 close 后调用 native，仍需将具体入口与 lastFailure 关联；当前 core.dll+0x12558d 不能由源码静态定位。

**影响**：P0

**方案**：保留并继续沿用 VkResult error propagation、queue/device idle、resource generation/lifetime protection；为每个 mod resource consumer 补 operation label 与 failed generation gate，不把错误吞成成功。

**共享依赖**：MCVR Framework/Textures/JNI、Radiance ResourceReloadCoordinator/ShaderRegistry、Veil/Sable/Simulated resource reload

**回退与损失**：错误时应停用对应 native frame/回退到安全 GUI 或等待下一 generation，保留已安全资源；不得把返回成功当作渲染已提交。

**验收**：动态制造 reload/resize/close 交错与 native failure，确认首个 VkResult、operation、Java 异常、running 状态和 descriptor retention 一致；另行调查 core 崩溃。

**状态**：静态：已证实：错误传播和生命周期保护是有效业务逻辑；动态：待验证：与 122757 core 崩溃的关联；不能声称已修复；裁决：保留

### CG-V24｜Radiance/Sable bridge｜本地日志、计数与检测探针

**版本**：Radiance 414d8e330a2fc6cb1e8630cc95f2302b2b97a0e8 / MCVR 9905c81b1999f5845bf66d13501d371c16adf561

**源码证据**：

- CUR D:/Workspaces/Repositories/GitHub/RecRivenVI/Radiance/src/main/java/com/radiance/compatibility/sable/SableSubLevelDiagnostics.java:22-65,68-161 — RADIANCE_SABLE_DIAG frame/compile/layer/callback/diagram layout 周期日志
- CUR D:/Workspaces/Repositories/GitHub/RecRivenVI/Radiance/src/main/java/com/radiance/compatibility/sable/SableSubLevelBridge.java:252-255,552-562,593-595 — entity/blockEntity/singleBlock count 与 reflection warning 日志
- CUR D:/Workspaces/Repositories/GitHub/RecRivenVI/Radiance/src/main/java/com/radiance/client/render/RenderCaptureContract.java:101-113 — unsupported discard 只记录一次的报告
- CUR D:/Workspaces/Repositories/GitHub/RecRivenVI/MCVR/src/core/vulkan/device.cpp:51-53,110-225 — diagnosticsRequested 与 device diagnostic extensions 的开关

**入口链**：世界帧/bridge/reflection/render capture -> 低频计数、布局、discard 或 warning log；这些输出帮助关联 RenderDoc/日志，但不创建 geometry、descriptor、TLAS 或修复 VkResult。

**所需 GL/源能力**：无额外 GL 需求；探针观察 Java/native 状态，不能替代 Vulkan error/lifetime contract

**当前 Vulkan 承接**：有效业务错误传播见 CG-V23；本条日志/检测不会让 Veil renderer、FBO、Sable culling 或 custom shader 变得可用。

**缺口**：探针可能造成噪声、误导“有回调即已渲染”的判断，并不能精确定位 core.dll+0x12558d；必须与真正的 VkResult/生命周期修复拆分。

**影响**：P1

**方案**：在完成有证据的功能/错误传播验证后，明确全部移除这些本地临时日志、计数和检测探针；移除不应删除 CG-V23 的 VkResult propagation/lifetime protection。

**共享依赖**：Radiance SableBridge/Diagnostics、RenderCaptureContract、MCVR device diagnostics、外部 runtime log

**回退与损失**：移除只损失诊断输出，不损失渲染/业务；保留正式错误返回和资源生命周期状态。

**验收**：静态确认每项探针无业务产物；动态清理前先用正式 VkResult/资源状态完成 correlation，清理后再确认无功能路径依赖。

**状态**：静态：已证实：这些是观察性日志/探针，不是兼容实现；动态：待验证：清理前的最后一轮 native correlation；core root 仍未知；裁决：明确待全部移除

## 边界与未决

本审查没有把 core.dll+0x12558d 或 runtime log 的首个 Veil null consumer 伪装成已精确修复；CG-V23 与 CG-V24 分开记录了有效的 VkResult/生命周期保护和应移除的本地观察探针。Create/Flywheel/Ponder 的深入 NeoForge 覆盖由 Explorer 负责，本报告只保留其作为 Sable/Simulated 共享依赖的接口边界。
