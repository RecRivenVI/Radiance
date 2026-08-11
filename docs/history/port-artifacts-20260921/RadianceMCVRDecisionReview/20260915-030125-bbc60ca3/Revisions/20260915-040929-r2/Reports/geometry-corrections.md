# Geometry corrections r2

范围：MC06 普通方块选框/Sable 选框，MC07 实体发光轮廓，MC17 painting/slime gel，MC22 lightning/beacon/fishing line/dragon death ray。

本修订只做源码链核验与方案记录，未实施业务或诊断清理，未构建、未启动客户端、未修改 Git。当前实现不等于动态验收通过。

关键边界：普通方块选框使用 BlockState VoxelShape、黑色 alpha 0.4、WORLD mask、content /vanilla；实体发光轮廓使用独立白色 priority_outline、PRIORITY_ONLY mask。当前 ModelPart union 只在实体 outline delegate 上存在，不能写入普通选框。判定箱 PBR 发光按用户选择保留，普通 debug line 单独裁决。

| ID | root | owner | function | static | dynamic | recommendation |
| --- | --- | --- | --- | --- | --- | --- |
| MC06-vanilla-block-outline | MC06 | Minecraft/Radiance | 普通方块选框 | 已证实：当前普通 selector 的入口、形状、颜色、WORLD route | 待验证：native prism 的厚度/遮挡和 debug emission 对最终 PT 色彩的影响 | 保留 |
| MC06-sable-block-outline | MC06 | Sable/Radiance | Sable 子层选框与 NeoForge highlight callback | 待验证：真实子层 callback、反射缺失回退与 native 坐标/遮挡 | 保留 | 保留 |
| MC07-entity-glow-outline | MC07 | Minecraft/Radiance/MCVR | 实体发光轮廓 priority overlay | 已证实：实体 outline 独立 storage、priority_outline group、PRIORITY_MASK 与白色 priority shader | 待验证：LivingEntity/自定义 renderer outline shape 与 camera 例外的最终画面 | 保留 |
| MC07-modelpart-outline-boundary | MC07 | Radiance | ModelPart outline 复杂合并边界 | 待确认：历史撤回后的最终轮廓策略；无动态结论 | 待确认 | 待确认 |
| MC17-painting | MC17 | Minecraft/Radiance | painting entity layer 与 no-height route | 待验证：native material、背面/反射与资源 reload | 保留 | 保留 |
| MC17-slime-gel | MC17 | Minecraft/Radiance | slime gel shell 与 invisible glow outline | 待验证：运行时 PBR transmission/outline 视觉和 mask | 保留 | 保留 |
| MC22-lightning | MC22 | Minecraft/Radiance/MCVR | lightning 四边形与世界 PT | 待验证：新增纹理与 native semanticEmission 的视觉 parity | 保留 | 保留 |
| MC22-beacon | MC22 | Minecraft/Radiance/MCVR | beacon beam 两层 block entity | 待验证：native alpha/emission 与 beacon shader 的最终 parity | 保留 | 保留 |
| MC22-fishing-line | MC22 | Minecraft/Radiance/MCVR | fishing hook 与 fishing line | 待验证：physical prism 与原版 lineStrip 的厚度、遮挡和 first-person anchor | 待确认 | 待确认 |
| MC22-dragon-death-ray | MC22 | Minecraft/Radiance/MCVR | dragon death rays 与 depth prepass | 待验证：PT alpha/emission、遮挡和死亡动画阶段 | 保留 | 保留 |

## Detailed rulings

### MC06-vanilla-block-outline — 普通方块选框

**Source evidence**

- REF D:/Workspaces/References/minecraft-references/minecraft-1.21.1/src/net/minecraft/client/renderer/LevelRenderer.java:1145-1154 — BLOCK hitResult、ClientHooks.onDrawHighlight 与 renderHitOutline
- REF D:/Workspaces/References/minecraft-references/minecraft-1.21.1/src/net/minecraft/client/renderer/LevelRenderer.java:2112-2218 — renderHitOutline/renderShape 使用 BlockState.getShape(CollisionContext)、VoxelShape.forAllEdges、0.4 alpha 黑色
- CUR D:/Workspaces/Repositories/GitHub/RecRivenVI/Radiance/src/main/java/com/radiance/client/proxy/world/EntityProxy.java:1199-1241,1244-1291 — event callback 与 /vanilla fallback
- CUR D:/Workspaces/Repositories/GitHub/RecRivenVI/Radiance/src/main/java/com/radiance/client/render/WorldOutlineGeometry.java:15-41 — collectShapeEdges/emitShapeEdges
- CUR D:/Workspaces/Repositories/GitHub/RecRivenVI/Radiance/src/main/java/com/radiance/client/proxy/world/EntityProxy.java:212-280 — RenderType.lines 到 EntityRenderData
- CUR D:/Workspaces/Repositories/GitHub/RecRivenVI/Radiance/src/main/java/com/radiance/client/constant/Constants.java:145-194,217-231 — reflect=false 的 WORLD_NO_REFLECT 与 WORLD mask
- CUR D:/Workspaces/Repositories/GitHub/RecRivenVI/MCVR/src/core/middleware/com_radiance_client_proxy_world_EntityProxy.cpp:7-64 — queueBuild JNI
- CUR D:/Workspaces/Repositories/GitHub/RecRivenVI/MCVR/src/core/render/entities.cpp:472-541,659-699,991-1140 — POSITION_COLOR_NORMAL/LINES 转 PBR 与 line prism
- CUR D:/Workspaces/Repositories/GitHub/RecRivenVI/MCVR/src/core/render/modules/world/ray_tracing/submodules/world_prepare.cpp:191-245 — TLAS instance 使用 rayTracingFlag mask

**Entry chain**：Minecraft hitResult -> EntityProxy.queueTargetBlockOutlineRebuild -> ClientHooks.onDrawHighlight；未 handled 时 BlockState.getShape -> WorldOutlineGeometry -> RenderType.lines -> content radiance:debug/block_outline/vanilla -> WORLD flag、WORLD coordinate、lineWidth 0.0075 -> MCVR queueBuild -> line prism BLAS/TLAS。

**Target/original**：原版使用完整 VoxelShape.forAllEdges，位置为 blockPos-camera，颜色 RGB(0,0,0)、alpha 0.4，RenderType.lines；它没有实体 ModelPart 的白色 outline。

**Current actual**：当前 ARGB.color(102, BLACK) 等价 alpha 0.4，shape edge 数学与原版 forAllEdges 对齐；event 未接管时 /vanilla 以 WORLD mask 捕获。native 将线段实体化为有宽度的 prism 后进入 PT。

**Geometry contract**：形状：BlockState collision context 的 VoxelShape edges。颜色：黑色 0x66 alpha。材质：POSITION_COLOR_NORMAL -> PBR colorLayer/normal，无实体纹理。几何类型因 reflect=false 为 WORLD_NO_REFLECT；坐标为 WORLD。

**Visibility/emission/mask/priority**：rayTracingFlag=WORLD，未使用 PRIORITY_ONLY，因此普通世界几何可遮挡选框；native entities.cpp 对 radiance:debug/* 统一把 albedoEmission 置 1，但黑色 colorLayer 不等于白色发光实体轮廓。priority_outline 不在此链。

**Finding**：正向承接：普通 selector 没有混入白 ModelPart entity outline，原版形状和颜色保留。明确风险：debug content 前缀使普通黑色 selector 也带 emission 标记，线宽 prism 与原版 raster line 的遮挡/厚度不同；静态不能认定为视觉错误。

**Proposal**：保持 /vanilla 的 WORLD 路由、0x66 黑色和完整 VoxelShape；将 emission 规则按 content/产品语义拆分，普通 selector 不继承 entity priority 白光。只在动态证据证明需要时调整 prism 宽度或深度 bias。

**Acceptance**：静态比较普通立方体、非整方块 VoxelShape、液体边界和自定义 shape 的 edge/颜色；动态确认 WORLD 表面会按预期遮挡、PT 可见，且没有白色实体 outline、PRIORITY_ONLY 或错误 texture。

**Status**：static=已证实：当前普通 selector 的入口、形状、颜色、WORLD route; dynamic=待验证：native prism 的厚度/遮挡和 debug emission 对最终 PT 色彩的影响; recommendation=保留

### MC06-sable-block-outline — Sable 子层选框与 NeoForge highlight callback

**Source evidence**

- REF D:/Workspaces/References/minecraft-references/sable-2.0.5/src/neoforge/src/main/java/dev/ryanhcode/sable/neoforge/mixin/block_outline_render/LevelRendererMixin.java:38-112 — onDrawHighlight 包装、SubLevelCamera.setCamera/setPose/clear
- REF D:/Workspaces/References/minecraft-references/minecraft-1.21.1/src/net/minecraft/client/renderer/LevelRenderer.java:1145-1154,2112-2218 — 原版 callback/fallback shape contract
- CUR D:/Workspaces/Repositories/GitHub/RecRivenVI/Radiance/src/main/java/com/radiance/compatibility/sable/SableSubLevelBridge.java:166-204,436-473,607-628 — blockOutlineContext、适配 camera、event/local transform
- CUR D:/Workspaces/Repositories/GitHub/RecRivenVI/Radiance/src/main/java/com/radiance/client/proxy/world/EntityProxy.java:1212-1238,1253-1291 — /event 与 /vanilla 两路
- CUR D:/Workspaces/Repositories/GitHub/RecRivenVI/Radiance/src/main/java/com/radiance/client/render/WorldOutlineGeometry.java:15-41 — fallback shape edge

**Entry chain**：BlockHitResult -> SableSubLevelBridge.blockOutlineContext -> local camera/pose 与 event transform -> ClientHooks.onDrawHighlight；event geometry 以 /event、WORLD flag、CAMERA_SHIFT queue；callback 未处理再以 /vanilla、WORLD coordinate 使用普通 VoxelShape path。

**Target/original**：Sable 在原版 LevelRenderer callback 外包裹 SubLevelCamera，使选框/模组 highlight 在子层局部相机下绘制；未处理时仍回到原版 BlockState shape edge。

**Current actual**：当前 bridge 反射创建 SubLevelCamera，applyEventTransform 后调用 NeoForge callback；finally 清理 adapted camera。fallback 继续用 blockState.getShape 和 WorldOutlineGeometry。两路都不是白色 ModelPart entity outline，也不是 PRIORITY_ONLY。

**Geometry contract**：event：callback 自己决定 RenderType/形状，content radiance:debug/block_outline/event，WORLD mask、CAMERA_SHIFT 坐标。fallback：黑色 0x66、VoxelShape edge、WORLD mask、WORLD 坐标。最终 native format/line prism 与 MC06-vanilla 共用。

**Visibility/emission/mask/priority**：正向承接：Sable 的专属坐标和 NeoForge callback 被单独保留。风险：SubLevelCamera 反射缺失/pose 失配时会回原相机，event 和 fallback 的坐标原点不同；不能把反射日志或空 geometry 当成功。

**Finding**：保留 event 与 vanilla 两条分支；验证失败时只针对 Sable camera/pose 或 route 修正，不把 Sable 选框改成实体 priority overlay，不把普通 selector 的黑色改成白色。

**Proposal**：动态在静止/旋转/缩放 Sable 子层选择普通方块和 callback 自定义 highlight，确认 eventCamera、localCamera、rotationPoint、颜色、遮挡及 /event 与 /vanilla 的坐标一致。

**Acceptance**：已证实：Sable 专属 adapted-camera 链与普通 fallback 分离

**Status**：static=待验证：真实子层 callback、反射缺失回退与 native 坐标/遮挡; dynamic=保留; recommendation=保留

### MC07-entity-glow-outline — 实体发光轮廓 priority overlay

**Source evidence**

- REF D:/Workspaces/References/minecraft-references/minecraft-1.21.1/src/net/minecraft/client/renderer/entity/LivingEntityRenderer.java:120-150 — entity visibility/glow render type 与 RenderType.outline
- CUR D:/Workspaces/Repositories/GitHub/RecRivenVI/Radiance/src/main/java/com/radiance/client/proxy/world/EntityProxy.java:316-338,526-535 — glow/custom outline provider、白色 1.0 emission、priority content
- CUR D:/Workspaces/Repositories/GitHub/RecRivenVI/Radiance/src/main/java/com/radiance/client/vertex/StorageOutlineVertexConsumerProvider.java:24-40,96-134 — outline color delegate
- CUR D:/Workspaces/Repositories/GitHub/RecRivenVI/Radiance/src/main/java/com/radiance/client/proxy/world/EntityProxy.java:587-592 — priorityOnlyVisibilityFor 与 sleep/detached 分支
- CUR D:/Workspaces/Repositories/GitHub/RecRivenVI/Radiance/src/main/java/com/radiance/client/constant/Constants.java:217-231 — PLAYER/PRIORITY_ONLY/WORLD mask values
- CUR D:/Workspaces/Repositories/GitHub/RecRivenVI/MCVR/src/core/render/entities.cpp:520-540,1138-1169 — priority_outline group 与 emission
- CUR D:/Workspaces/Repositories/GitHub/RecRivenVI/MCVR/src/core/render/modules/world/ray_tracing/submodules/world_prepare.cpp:244-265 — instance mask、hitGroupNames
- CUR D:/Workspaces/Repositories/GitHub/RecRivenVI/MCVR/src/shader/world/ray_tracing/internal/vanilla-pt/priority/priority.rgen:20-47 — PRIORITY_MASK trace 与 32-layer composite
- CUR D:/Workspaces/Repositories/GitHub/RecRivenVI/MCVR/src/shader/world/ray_tracing/internal/vanilla-pt/priority/outline.rchit:5-12 — 白色 vec3(4) priority payload
- CUR D:/Workspaces/Repositories/GitHub/RecRivenVI/MCVR/src/shader/world/ray_tracing/internal/vanilla-pt/priority/composite.comp:9-17 — priority foreground/background 合成

**Entry chain**：EntityRenderer glow/custom-outline decision -> EntityProxy outlineStorage -> StorageOutlineVertexConsumerProvider 白色轮廓 -> process content radiance:priority/outline、PRIORITY_ONLY、reflect=false -> native group priority_outline/emission -> priority TLAS mask -> priority geometry image -> composite。

**Target/original**：原版 glowing entity 使用独立 outline RenderType；普通可见 entity 的本体仍走自身 model RenderType。优先层可穿过世界显示 outline，和普通方块 selector 的 WORLD 遮挡语义不同。

**Current actual**：当前把 outline provider 设为白色，RenderType outline/affects-outline 几何进独立 storage；native priority_outline 组不采样普通 PBR surface，而由 outline.rchit 输出固定 vec3(4)。这是专门实体 outline 路径。

**Geometry contract**：形状来自实体 renderer 的 outline layer；颜色/强度由 priority.rchit 固定白色 4，Java white provider 是前置语义。材质是 priority payload，不是 ordinary WORLD material。实体 awake attached camera 使用 PLAYER 分支；sleep 或 detached 走正常 priority/worldVisibility 例外。

**Visibility/emission/mask/priority**：PRIORITY_ONLY mask 让 priority.rgen 不测试 WORLD 表面，形成 see-through overlay；priority composite 将 foreground/background 写回 output。该路径不应回填普通 BlockHitResult 的 /vanilla。

**Finding**：正向承接：实体 glow 与普通 block selector 的形状、颜色、mask、priority 已分开，用户选择的判定箱 PBR 发光不应被此实体白光路径覆盖。风险：priority 轮廓强度/自定义 outline geometry 的最终遮挡与 camera 例外仍未动态验证。

**Proposal**：保留独立 priority outline route；把任何颜色或强度调整限制在实体 glow contract。普通 selector 继续 WORLD/黑色，判定箱 PBR 发光另按用户选择的 debug hitbox contract 处理。

**Acceptance**：动态测试可见/不可见但发光/自定义 outline、墙后、attached first-person、sleeping、detached camera，检查 priority mask、白色 4 强度、世界遮挡和不影响普通 BlockHitResult。

**Status**：static=已证实：实体 outline 独立 storage、priority_outline group、PRIORITY_MASK 与白色 priority shader; dynamic=待验证：LivingEntity/自定义 renderer outline shape 与 camera 例外的最终画面; recommendation=保留

### MC07-modelpart-outline-boundary — ModelPart outline 复杂合并边界

**Source evidence**

- CUR D:/Workspaces/Repositories/GitHub/RecRivenVI/Radiance/src/main/java/com/radiance/client/vertex/StorageOutlineVertexConsumerProvider.java:43-50 — WorldOutlineVertexConsumer 的 delegate/outlineDelegate 语义
- CUR D:/Workspaces/Repositories/GitHub/RecRivenVI/Radiance/src/main/java/com/radiance/mixins/vulkan_render_integration/ModelPartWorldOutlineMixins.java:19-42 — ModelPart.compile 时 union cubes 并发射边
- CUR D:/Workspaces/Repositories/GitHub/RecRivenVI/Radiance/src/main/java/com/radiance/client/render/WorldOutlineGeometry.java:15-41 — union VoxelShape edges
- CUR D:/Workspaces/Repositories/GitHub/RecRivenVI/Radiance/src/main/java/com/radiance/client/proxy/world/EntityProxy.java:324-338,526-535 — entity outline storage 与 priority content
- CUR D:/Workspaces/Repositories/GitHub/RecRivenVI/Radiance/src/main/resources/radiance.mixins.json:84-87 — ModelPartWorldOutlineMixins 当前注册

**Entry chain**：实体 renderer 获取带 affects-outline 的 RenderType -> StorageOutlineVertexConsumerProvider 返回 WorldOutlineVertexConsumer -> ModelPart.compile 触发 mixin -> cubes union 为 VoxelShape -> line outlineDelegate -> entity priority outline storage。

**Target/original**：Minecraft 的 ModelPart.compile 原本向目标 VertexConsumer 编译 cube faces；当前工作树额外在 affects-outline consumer 上合并 cubes 生成 line graph。用户历史决定“复杂轮廓合并后来撤回简单 ModelPart”，该历史目标不能被当前文件自动视为已实现。

**Current actual**：当前静态代码确实存在 union cubes/Shapes.or/emitShapeEdges；它只在 ModelPart 的 entity outline delegate 上触发，不能到达 MC06 BlockHitResult 的普通 /vanilla fallback。输出最终仍进入 MC07 的 priority_outline/white path。

**Geometry contract**：形状是 ModelPart cubes 的 union edge graph，可能比简单 ModelPart outline 包含更多内部/合并边；颜色由 outline provider 白色，emission/priority 继承 MC07。它不是普通方块选框的 BlockState VoxelShape。

**Visibility/emission/mask/priority**：正向承接：作用域没有写入普通 selector。明确问题是当前工作树仍有复杂 union 实现，而历史决策要求需由 root 确认的简单 ModelPart 边界；静态不判断哪一个是最终产品目标。

**Finding**：以 root 的历史裁决为准：若简单 ModelPart 是最终目标，移除或限制 union mixin；若保留复杂合并，必须把它命名为 entity outline 专属并证明无重复/内部边。两种选择都不改变 MC06 /vanilla。

**Proposal**：动态用多 cube、重叠 cube、透明/自定义 entity model 和 glow entity 对照简单 ModelPart 目标，确认轮廓数量、内部边、白色 priority emission、墙后显示及普通方块 selector 不受影响。

**Acceptance**：已证实：当前存在复杂 ModelPart union 代码，且作用域只在 entity outline delegate

**Status**：static=待确认：历史撤回后的最终轮廓策略；无动态结论; dynamic=待确认; recommendation=待确认

### MC17-painting — painting entity layer 与 no-height route

**Source evidence**

- REF D:/Workspaces/References/minecraft-references/minecraft-1.21.1/src/net/minecraft/client/renderer/entity/PaintingRenderer.java:28-44,51-157 — entitySolid、painting atlas、面/背面与 light/overlay
- REF D:/Workspaces/References/minecraft-references/minecraft-1.21.1/src/net/minecraft/client/renderer/RenderType.java:74-84 — 原版 entity_solid NEW_ENTITY/no-transparency/lightmap/overlay
- CUR D:/Workspaces/Repositories/GitHub/RecRivenVI/Radiance/src/main/java/com/radiance/mixins/vulkan_render_integration/PaintingRendererMixins.java:19-44 — entity_solid_z_offset_forward layer 与 redirect
- CUR D:/Workspaces/Repositories/GitHub/RecRivenVI/Radiance/src/main/java/com/radiance/client/proxy/world/EntityProxy.java:1741-1769,1767-1784 — texture/geometry type/name/format metadata
- CUR D:/Workspaces/Repositories/GitHub/RecRivenVI/MCVR/src/shader/world/ray_tracing/internal/vanilla-pt/configs.json:700-716 — entity_solid_z_offset_forward -> world/no_height.rchit
- CUR D:/Workspaces/Repositories/GitHub/RecRivenVI/MCVR/src/shader/world/ray_tracing/internal/advanced/configs.json:1052-1064 — advanced no-height hit group

**Entry chain**：PaintingRenderer.render -> entitySolid(texture) -> current redirect entity_solid_z_offset_forward -> EntityProxy PBR capture -> WORLD_SOLID geometry/name -> MCVR config selects no_height hit shader。

**Target/original**：原版 1.21.1 layer 名为 entity_solid，NEW_ENTITY、QUADS、no transparency、lightmap、overlay；painting renderer 逐面使用 atlas UV/light/normal。当前为等状态的 z_offset_forward 名称，以匹配 MCVR no_height hit group。

**Current actual**：当前只改 layer identity/state builder，保留 texture、NEW_ENTITY、quad、lightmap、overlay、opaque state；MCVR configs 对该名字明确选择 no_height rchit，避免 painting surface 走 parallax height map。EntityProxy 仍交给普通 entity/world geometry capture。

**Geometry contract**：形状/UV/法线/光照来自源 painting renderer；材质为 opaque PBR entity/world solid，texture id 由 EntityProxy metadata。reflect=true 时 Constants.GeometryTypes 以 solid 名称归 WORLD_SOLID；不使用 priority mask。

**Visibility/emission/mask/priority**：正向承接：layer-name 到 no-height shader 是具体 Vulkan 适配，未删除 painting faces。风险是命名层与 native variant 的 runtime registration/材质结果未动态确认，不能把“配置存在”当作视觉通过。

**Finding**：保留当前专用 layer 与 no-height mapping；仅在动态证据证明 z offset 或 material 不符时调整该 painting contract。

**Proposal**：动态检查不同画布尺寸/方向/背面、atlas reload、PBR normal/texture、阴影/反射和子层 pose，确认 no_height 命中且不影响普通 entity_solid。

**Acceptance**：已证实：原版与当前 layer 的输入状态对应，MCVR no-height variant 存在

**Status**：static=待验证：native material、背面/反射与资源 reload; dynamic=保留; recommendation=保留

### MC17-slime-gel — slime gel shell 与 invisible glow outline

**Source evidence**

- REF D:/Workspaces/References/minecraft-references/minecraft-1.21.1/src/net/minecraft/client/renderer/entity/layers/SlimeOuterLayer.java:27-53 — invisible/glowing 分支、entityTranslucent 或 outline、model render
- CUR D:/Workspaces/Repositories/GitHub/RecRivenVI/Radiance/src/main/java/com/radiance/mixins/vulkan_render_integration/SlimeOuterLayerMaterialMixins.java:15-31 — 仅 gel shell renderToBuffer 包裹 transmission
- CUR D:/Workspaces/Repositories/GitHub/RecRivenVI/Radiance/src/main/java/com/radiance/client/vertex/PBRMaterialContext.java:17-53 — entity transmission scope
- CUR D:/Workspaces/Repositories/GitHub/RecRivenVI/Radiance/src/main/java/com/radiance/client/vertex/PBRVertexConsumer.java:353-357 — effectiveAlphaMode transmission
- CUR D:/Workspaces/Repositories/GitHub/RecRivenVI/Radiance/src/main/java/com/radiance/client/proxy/world/EntityProxy.java:324-338,526-535 — entity outline separate storage

**Entry chain**：SlimeOuterLayer.render -> invisible+glow 选 RenderType.outline，否则 entityTranslucent -> model.renderToBuffer -> current mixin 以 !slime.isInvisible() 推入 entityTransmission -> PBR alpha/material；outline 由独立 entity glow provider 捕获。

**Target/original**：原版可见 slime 外壳是 entityTranslucent，隐身但发光是 outline，普通隐身不绘制；材质/形状由 SlimeModel outer layer 提供。

**Current actual**：当前没有改形状或 layer 选择，只给可见 gel shell 设置 entity transmission；不可见 outline 明确不设置 dielectric transmission，保持 outline material。

**Geometry contract**：可见 shell：NEW_ENTITY/texture/alpha 进入 PBR transmission；隐身 glow：outline white/priority path，避免把 synthetic outline 当透明胶。EntityProxy 的 glow mask/priority 负责后续 overlay。

**Visibility/emission/mask/priority**：正向承接：visible gel 与 invisible glowing outline 的语义分离准确，且限定在 SlimeOuterLayer。问题仅需动态确认实体 renderer 的 outline/texture 与 PBR transmission 在 PT 中一致。

**Finding**：保留 scope；若发现透明度或 invisible glow 错误，只分别调整 gel transmission 或 entity priority outline，不把普通 selector/所有透明实体改成 gel。

**Proposal**：动态测试普通 slime、invisible slime、invisible+glowing、hurt/death、墙后 outline 与 reflection/refraction，确认 gel transmission、outline 白光和模型形状。

**Acceptance**：已证实：源分支和当前 transmission scope 对应

**Status**：static=待验证：运行时 PBR transmission/outline 视觉和 mask; dynamic=保留; recommendation=保留

### MC22-lightning — lightning 四边形与世界 PT

**Source evidence**

- REF D:/Workspaces/References/minecraft-references/minecraft-1.21.1/src/net/minecraft/client/renderer/entity/LightningBoltRenderer.java:21-87,89-123 — 8 段随机路径、四次 quad、alpha 0.3
- REF D:/Workspaces/References/minecraft-references/minecraft-1.21.1/src/net/minecraft/client/renderer/RenderType.java:526-552 — lightning 与 dragon_rays 的原版 format/state
- CUR D:/Workspaces/Repositories/GitHub/RecRivenVI/Radiance/src/main/java/com/radiance/mixins/vulkan_render_integration/RenderLayerMixins.java:16-43 — LIGHTNING 改为 POSITION_TEX_COLOR 并绑定 lightning.png
- CUR D:/Workspaces/Repositories/GitHub/RecRivenVI/Radiance/src/main/java/com/radiance/mixins/vulkan_render_integration/LightningEntityRendererMixins.java:11-56 — quad 重定向，UV 与 alpha 0.3
- CUR D:/Workspaces/Repositories/GitHub/RecRivenVI/Radiance/src/main/java/com/radiance/client/proxy/world/EntityProxy.java:212-280,557-565 — layer capture、WORLD flag、reflect=true
- CUR D:/Workspaces/Repositories/GitHub/RecRivenVI/Radiance/src/main/java/com/radiance/client/constant/Constants.java:145-194,217-231 — lightning transparency -> WORLD_TRANSPARENT 与 WORLD mask
- CUR D:/Workspaces/Repositories/GitHub/RecRivenVI/MCVR/src/core/render/entities.cpp:624-752,520-540,1138-1140 — POSITION_TEXTURE_COLOR、lightning semanticEmission

**Entry chain**：LightningBoltRenderer.render -> RenderType.lightning -> current quad redirect emits textured POSITION_TEX_COLOR quads -> EntityProxy WORLD entity -> native PBR texture/color -> WORLD_TRANSPARENT TLAS geometry。

**Target/original**：原版 1.21.1 lightning RenderType 是 POSITION_COLOR、LIGHTNING_TRANSPARENCY、COLOR_DEPTH_WRITE、无显式 texture state；renderer 四段每段四 quad，颜色 0.45/0.45/0.5、alpha 0.3。当前为 PBR 所需的 POSITION_TEX_COLOR + vanilla lightning block texture，并保留随机路径/alpha。

**Current actual**：当前 custom layer 保留 lightning name/output weather/lighting transparency，并添加 lightning.png UV。EntityProxy 传 texture id/color；reflect=true 使其归 WORLD_TRANSPARENT，未进入 priority。

**Geometry contract**：形状：源随机路径与 4 quad 结构保留。颜色/alpha：0.45,0.45,0.5,0.3。材质：PBR textured colorLayer，groupName lightning 被 native semanticEmission 标记。mask=WORLD，非 PRIORITY。

**Visibility/emission/mask/priority**：正向承接：lightning 仍是世界几何、参与 PT、带透明/发光语义。明确差异是当前新增纹理与 PBR emission，是否与原版 lightning shader 的纯颜色视觉相同只能动态确认。

**Finding**：保留当前 textured layer/quad route；比较纹理采样与原版纯色 lightning 的颜色/alpha，必要时只调整 lightning material/emission，不移除世界 geometry。

**Proposal**：动态检查 bolt seed、四面、alpha、纹理采样、世界遮挡/反射、距离与重建，确认 lightning 不被 priority 或 weather-only mask 错分。

**Acceptance**：已证实：当前有明确四 quad、texture/color、WORLD_TRANSPARENT、emission 路由

**Status**：static=待验证：新增纹理与 native semanticEmission 的视觉 parity; dynamic=保留; recommendation=保留

### MC22-beacon — beacon beam 两层 block entity

**Source evidence**

- REF D:/Workspaces/References/minecraft-references/minecraft-1.21.1/src/net/minecraft/client/renderer/blockentity/BeaconRenderer.java:26-43,52-130 — beam sections、height/color、inner/outer renderPart
- REF D:/Workspaces/References/minecraft-references/minecraft-1.21.1/src/net/minecraft/client/renderer/RenderType.java:190-199 — beacon_beam BLOCK format，内层 no-transparency，外层 translucent
- CUR D:/Workspaces/Repositories/GitHub/RecRivenVI/Radiance/src/main/java/com/radiance/client/proxy/world/EntityProxy.java:654-765,807-828 — block entity capture 与 WORLD flag
- CUR D:/Workspaces/Repositories/GitHub/RecRivenVI/Radiance/src/main/java/com/radiance/client/constant/Constants.java:145-194,217-231 — beacon_beam transparent geometry 与 WORLD mask
- CUR D:/Workspaces/Repositories/GitHub/RecRivenVI/Radiance/src/main/java/com/radiance/client/vertex/PBRVertexConsumer.java:152-189,353-357 — alpha mode 与 transmission/material
- CUR D:/Workspaces/Repositories/GitHub/RecRivenVI/MCVR/src/core/render/entities.cpp:520-540,1138-1140 — beacon_beam semanticEmission
- CUR D:/Workspaces/Repositories/GitHub/RecRivenVI/MCVR/src/core/render/modules/world/ray_tracing/submodules/world_prepare.cpp:244-265 — WORLD mask instance/hit group

**Entry chain**：BeaconBlockEntity renderer -> beaconBeam(texture,false/true) -> block entity StorageVertexConsumer -> EntityProxy process WORLD -> native beacon_beam group/material -> WORLD TLAS/PT。

**Target/original**：原版按 BeaconBeamSection 分高度/颜色，内层 beaconBeam false 写 color+depth，外层 true 写 alpha 32 的 translucent layer；双方使用 BLOCK vertices、UV、light、normal 和 beacon_beam.png。

**Current actual**：当前无 beacon 专属 mixin，复用 block entity capture；PBR 根据 layer transparency 分别处理 inner/outer，native group beacon_beam 进入 semanticEmission。BE geometry 是 WORLD，不是 priority。

**Geometry contract**：形状/UV/section height/color 来自源 renderer。内层为 opaque/cutout-like PBR with emission floor，外层为 coverage/translucent with emission。mask=WORLD，reflect=true，shadow/secondary visibility 由世界路径决定。

**Visibility/emission/mask/priority**：正向承接：未删除 beam sections 或把 beacon 当屏幕 overlay；独立 inner/outer layer 和 world PT route 存在。风险是 native emission/alpha 对 beacon shader 的精确颜色和外层混合需动态验证。

**Finding**：保留 block entity route 与 beacon_beam emission 语义；必要时只为 inner/outer alpha/emission 建明确材质映射，不改成 PRIORITY_ONLY 或单层白色实体 outline。

**Proposal**：动态检查多段 beam、颜色、section height、透明 outer、遮挡/反射、BE reload/世界切换，确认两层 geometry、texture/UV 和光照。

**Acceptance**：已证实：源两层 beacon contract 与当前 BE WORLD capture 对应

**Status**：static=待验证：native alpha/emission 与 beacon shader 的最终 parity; dynamic=保留; recommendation=保留

### MC22-fishing-line — fishing hook 与 fishing line

**Source evidence**

- REF D:/Workspaces/References/minecraft-references/minecraft-1.21.1/src/net/minecraft/client/renderer/entity/FishingHookRenderer.java:30-61,64-90 — hook quad、lineStrip、first-person/third-person hand anchor
- REF D:/Workspaces/References/minecraft-references/minecraft-1.21.1/src/net/minecraft/client/renderer/entity/FishingHookRenderer.java:97-124 — hook texture vertex 与 stringVertex 黑色/normal
- REF D:/Workspaces/References/minecraft-references/minecraft-1.21.1/src/net/minecraft/client/renderer/RenderType.java:614-627 — line_strip POSITION_COLOR_NORMAL、translucent、item target
- CUR D:/Workspaces/Repositories/GitHub/RecRivenVI/Radiance/src/main/java/com/radiance/client/proxy/world/EntityProxy.java:547-555,557-565 — FishingHook special case remains WORLD
- CUR D:/Workspaces/Repositories/GitHub/RecRivenVI/Radiance/src/main/java/com/radiance/client/proxy/world/EntityProxy.java:212-280 — line layer metadata/capture
- CUR D:/Workspaces/Repositories/GitHub/RecRivenVI/Radiance/src/main/java/com/radiance/client/constant/Constants.java:145-194,217-231 — reflect=true line_strip -> WORLD_TRANSPARENT 与 WORLD mask
- CUR D:/Workspaces/Repositories/GitHub/RecRivenVI/MCVR/src/core/render/entities.cpp:676-699,991-1129 — POSITION_COLOR_NORMAL and line-strip prism conversion
- CUR D:/Workspaces/Repositories/GitHub/RecRivenVI/Radiance/src/main/java/com/radiance/mixins/vulkan_render_integration/HeldItemRendererMixins.java:51-88 — ordinary hand route kept separate

**Entry chain**：FishingHookRenderer.render -> hook entityCutout + lineStrip stringVertex -> EntityProxy FishingHook branch WORLD/reflect=true -> native POSITION_COLOR_NORMAL line strip -> width prism -> WORLD_TRANSPARENT PT；first-person hand anchor remains source renderer math/ordinary HAND path。

**Target/original**：原版 hook 是 entityCutout 纹理 quad；line 是 17-sample lineStrip，黑色 string vertices、normal、无 texture，first-person anchor uses camera near plane/FOV and third-person body rotation.

**Current actual**：当前没有 FishingHook-specific transform mixin；hook 与 line 都从 EntityProxy entity storage 进入 native。MCVR 将 line strip 展开为 width prism，line width 是 queue build 参数而非 OpenGL line rasterization。

**Geometry contract**：形状：hook textured quad + 16/17 line samples。颜色：line black, alpha source opaque/line state. 材质：hook texture PBR，line colorLayer/normal no texture，emission 非 debug/priority。mask=WORLD，非 priority。

**Visibility/emission/mask/priority**：正向承接：FishingHook 明确走 WORLD，不被 HAND 或 priority 混淆；普通手 callback 与线锚点分开。问题/差异是 physical prism 厚度、端点与原版 raster line 不同，PT 遮挡行为需确认。

**Finding**：保留 hook/line world geometry 与 source hand anchor；只针对线宽、端点、camera FOV 或 Sable pose 建局部修正，不改普通手姿态，也不将 fishing line 置为 priority overlay。

**Proposal**：动态测试主副手/first-person/third-person、FOV/attack animation、detached/sleep、Sable 子层、hook 移动和世界遮挡，核对端点与 prism line width。

**Acceptance**：已证实：source line/hook 与 current WORLD capture 分开

**Status**：static=待验证：physical prism 与原版 lineStrip 的厚度、遮挡和 first-person anchor; dynamic=待确认; recommendation=待确认

### MC22-dragon-death-ray — dragon death rays 与 depth prepass

**Source evidence**

- REF D:/Workspaces/References/minecraft-references/minecraft-1.21.1/src/net/minecraft/client/renderer/entity/EnderDragonRenderer.java:47-81 — death body, eyes, dragonRays 与 dragonRaysDepth 两路
- REF D:/Workspaces/References/minecraft-references/minecraft-1.21.1/src/net/minecraft/client/renderer/entity/EnderDragonRenderer.java:96-139 — deterministic ray triangles、white/magenta colors、death progress
- REF D:/Workspaces/References/minecraft-references/minecraft-1.21.1/src/net/minecraft/client/renderer/RenderType.java:540-561 — dragon_rays POSITION_COLOR TRIANGLES 与 depth-only dragon_rays_depth
- CUR D:/Workspaces/Repositories/GitHub/RecRivenVI/Radiance/src/main/java/com/radiance/client/proxy/world/EntityProxy.java:236-243,557-565 — dragon_rays_depth 过滤、dragon entity WORLD capture
- CUR D:/Workspaces/Repositories/GitHub/RecRivenVI/MCVR/src/core/render/entities.cpp:520-540,659-699,1138-1140 — dragon_rays semanticEmission 与 POSITION_COLOR
- CUR D:/Workspaces/Repositories/GitHub/RecRivenVI/MCVR/src/shader/world/ray_tracing/internal/vanilla-pt/world/world.rgen:436-498 — WORLD/PLAYER/PRIORITY/PARTICLE 等主 mask 组合

**Entry chain**：EnderDragonRenderer deathTime -> renderRays dragon_rays + raster-only dragon_rays_depth -> EntityProxy process filters depth layer but captures colored dragon_rays as WORLD/reflect=true -> native POSITION_COLOR triangles -> dragon_rays world transparent/emissive PT geometry。

**Target/original**：原版 death rays 生成 deterministic random triangles，首点渐隐白色、其余 magenta；dragon_rays 使用 lightning transparency/color-only，dragon_rays_depth 是 depth-only raster prepass。当前目标是让彩色 ray geometry 参与 PT，depth-only 不重复造面。

**Current actual**：当前过滤 dragon_rays_depth（EntityProxy:236-243），保留 dragon_rays；groupName dragon_rays 被 native semanticEmission 标记，POSITION_COLOR 转 PBR colorLayer，WORLD flag 进入 native TLAS。

**Geometry contract**：形状/颜色来自 source renderRays。材质无 texture、colorLayer + emission=1，geometry type 由 lightning transparency 归 WORLD_TRANSPARENT。彩色 rays 使用 WORLD mask，可参与 PT；depth-only 没有 priority 或 WORLD instance。

**Visibility/emission/mask/priority**：正向承接：彩色 death ray 没被 depth prepass 取代，能成为世界 PT geometry；过滤深度层符合“ray-traced visibility 不需重复 depth surface”的设计。风险是最终 ray alpha/反射、dragon model death body 与 depth prepass removal 需动态确认。

**Finding**：保留 dragon_rays 彩色 PT geometry 与 depth-only 过滤；若动态发现遮挡缺失，只补明确的 visibility/depth contract，不恢复重复 depth mesh或禁用 dragon death ray。

**Proposal**：动态检查 deathTime 0→200、ray count/fade、白/洋红颜色、世界遮挡/反射、dragon body/eyes/death explosion 与 dragon_rays_depth 不产生重复/错误遮挡。

**Acceptance**：已证实：彩色 ray 捕获与 depth-only 过滤在当前链中明确分开

**Status**：static=待验证：PT alpha/emission、遮挡和死亡动画阶段; dynamic=保留; recommendation=保留
