# Fixed Offroad destruction / Simulated staff consumers

## Conclusions

1. Fixed **Offroad 1.3.2** destruction progress already enters Radiance's shared ordinary destruction and Flywheel crumbling consumers. The earlier WP-V04 statement that this input path was unknown is resolved by fixed source plus packaged Offroad bytecode; Offroad 2.0.5 in that matrix is a version mix-up with Sable 2.0.5.
2. A real ordinary crumbling material gap was found and fixed: Radiance previously mapped `CRUMBLING_TRANSPARENCY` to coverage alpha mode 10, losing Minecraft's multiplicative stage RGB blend. It now uses existing MCVR alpha mode 22. No native extension is required by this fix.
3. Fixed Simulated's missing `staff_overlay/staff_overlay` shader is a declared/registered but unproduced layer in the pinned combination. The actual staff beam emits Ponder outline geometry. No replacement shader/resource should be fabricated for that unused registration.

This report is source/package analysis plus the specifically authorized Java fix. Parent's `final-source-verified-build.log` reports full build/JNI/A-B preparation **PASS in 35s**. Actual copied `TEST-com.radiance.client.vertex.PBRVertexConsumerCrumblingTest.xml` records **2 tests, 0 failures, 0 errors, 0 skipped**. Evidence is `../Evidence/offroad-staff-consumers/`. No game/UI or Gradle/native build was run by this subtask; the successful tests are compiled ASM contracts, not live draw evidence.

## Offroad's real progress path

Reference root: `D:/Workspaces/References/minecraft-references/aeronautics_bundled-1.3.2/src/`.

Server `offroad/.../handlers/server/MultiMiningServerManager.java:235` serializes invalidity when progress >=10 and otherwise the byte stage. `ClientboundMultiMiningSync.java:79,98` reads it and calls `MultiMiningClientHandler.handleInboundClientUpdate`. `OffroadCommonEvents.java:37` ticks that client handler. `MultiMiningClientHandler.java:98-104` obtains the actual ClientLevel's LevelRenderer and calls `offroad$manuallyAddMultiDestructionProgress`.

Original registered `offroad/.../mixin/client/multimining_destruction_progress/LevelRenderMixin.java:58-91` keeps the multimining holder in `destroyingBlocks` and inserts real per-position `BlockDestructionProgress` records into the **same** LevelRenderer `destructionProgress` map. It calls `setProgress((byte) clientDataProgress)`, removes invalid entries, updates the holder tick, and its removeProgress wrapper removes all held inner progresses. This is not a separate Offroad rendering map.

The fixed packaged `dev.ryanhcode.offroad.offroad-neoforge-1.21.1-1.3.2.jar` was inspected with `javap -p -c`: the mixin has the real shared `destructionProgress : Long2ObjectMap` field; its computed-progress lambda calls `BlockDestructionProgress.setProgress` then accesses that field. The fixed source client mixin list registers this producer.

Radiance `WorldRendererMixins.java:305-312` sends the same map to BE capture, `FlywheelRenderBridge.Frame.beforeCrumbling`, and `EntityProxy.queueCrumblingRebuild`. No specialized Offroad bridge is necessary merely to forward the input.

### Flywheel visual target

`FlywheelRenderBridge.java:76-80,117-118` invokes the original dispatcher's `beforeCrumbling(context, destructionProgress)` API. Fixed Flywheel 1.0.6 `VisualizationManagerImpl.java:279-320,376-377` looks up `visualAtPos`, calls the real visual's `collectCrumblingInstances`, chooses `set.last()` progress, and passes nonempty instance lists to the selected engine. Blocks without a visual deliberately skip this branch and still have the ordinary destruction path below.

`RadianceFlywheelEngine.renderCrumbling` passes those real instance IDs, positions, progress and `textures/block/destroy_stage_<progress>.png` IDs through the existing JNI. MCVR `Instancing::renderCrumbling` associates them with live IDs; `beginFrame` clears old crumbling metadata and `deleteInstance` removes it. Prepared instance appearance selects the stage texture and crumbling flag; `vertex.glsl:426-440` computes transformed decal UV and multiplies actual fragment color by twice stage RGB. This is source-established consumer connectivity, not an in-game destruction test.

### Ordinary target and corrected material

`EntityProxy.queueCrumblingRebuild` selects each nonempty position's last stage within the original 32-block radius, creates the real `ModelBakery.DESTROY_TYPES[stage]` consumer, retains `SheetedDecalTextureGenerator` UVs, and calls `BlockRenderDispatcher.renderBreakingTexture` with actual block state/model data. These mesh buffers go through the existing PBR capture and ordinary entity TLAS route.

Vanilla `RenderType.crumbling` is BLOCK / QUADS, sorted, COLOR_WRITE, polygon-offset layering, and `CRUMBLING_TRANSPARENCY`. Its actual RGB blend is `DST_COLOR / SRC_COLOR`, giving `2 * sourceRGB * destinationRGB`. Before this fix `PBRVertexConsumer.getAlphaMode` had no crumbling branch and returned coverage 10.

The new branch matches the actual transparency state and selects **22**, already defined as `ALPHA_MODE_FLYWHEEL_CRUMBLING` in MCVR. `Constants.GeometryTypes.getGeometryType` already selects WORLD_TRANSPARENT for that state. Ordinary entity world preparation copies real material buffers and supplies identity instance appearance. The existing vanilla-PT and advanced `transparent_only.rchit` alpha-22 branch reads **packed material alpha mode** and multiplies throughput by `2 * shadedRgb`; it does not require a Flywheel instance flag. Therefore ordinary overlays can reuse it without an appearance-flag, TLAS, shader, or native API change. Existing stage texture/UV and base geometry remain the original inputs.

Modified: `src/main/java/com/radiance/client/vertex/PBRVertexConsumer.java`. Added: `src/test/java/com/radiance/client/vertex/PBRVertexConsumerCrumblingTest.java`. Initial actual-RenderType execution was blocked by unbootstrapped registries. A real `SharedConstants.tryDetectVersion()` / `Bootstrap.bootStrap()` attempt then reached NeoForge's `FeatureFlagLoader.loadModdedFlags` with `LoadingModList.get()==null`. No fake loader, skipped test or production workaround was introduced. The final two tests use actual compiled bytecode: (1) production `getAlphaMode` compares the true CRUMBLING_TRANSPARENCY field and its equal branch returns 22, while ordinary coverage fallback remains 10; (2) actual vanilla crumbling builder uses BLOCK/QUADS and that same transparency field, and compiled WORLD_TRANSPARENT routing matches it. This is an ASM contract, **not real RenderType initialization/execution or in-game rendering**. Parent final retest passed: two tests, zero failures/errors/skips. Parent owns coordinated compile/test execution and remaining native/live visual evidence. No new mixin registration is needed.

## Simulated staff shader reachability

Fixed `SimRenderTypes.java:18-29,113-115` declares POSITION_COLOR / TRIANGLE_STRIP `staff_overlay/staff_overlay`. `SimulatedClient.java:41` registers its fixed buffer for AFTER_LEVEL. The packaged Simulated 1.3.2 JAR has no corresponding program vsh/fsh/json (previous shader-consumer report and this source/package scan agree).

The complete pinned source tree has **no staffOverlay use beyond that registration and getter**. A read-only scan of every `.class` entry in the fixed Simulated, Aeronautics and Offroad 1.3.2 JARs for `staffOverlay` / `staff_overlay` found only:

- `dev/simulated_team/simulated/SimulatedClient.class`
- `dev/simulated_team/simulated/index/SimRenderTypes.class`

There is no fixed packaged vertex-producing caller for this RenderType. Registering/flushing an empty fixed buffer does not apply its shader: vanilla `BufferSource.endBatch` calls `RenderType.draw` only for a non-null built mesh.

Actual staff rendering is reachable via `PhysicsStaffRenderHandler.java:57 -> PhysicsStaffClientHandler.onRender`; its beam creates `LineOutline`, sets real intensity/positions and calls `line.render`. Fixed Ponder 1.0.82 `LineOutline.java:36-44` requests `PonderRenderTypes.outlineSolid()` and emits cuboid-line vertices. That consumer uses the existing outline path, not the missing staff overlay shader. A new external caller or changed upstream version could activate the dormant layer, but that hypothetical path is outside the fixed combination and is not grounds to fabricate resources.

## Evidence boundaries and matrix update

- **Resolved source question:** Offroad input forwarding to shared destruction and conditional real Flywheel instance consumers.
- **Implemented source gap:** ordinary destruction overlay multiplication; compiled ASM contracts passed.
- **No active source gap found:** fixed missing staff-overlay resource has no vertex producer; actual staff beam uses Ponder outline.
- **Still runtime evidence:** live destruction stage appearance/alpha, queue/VkResult, generation/fence/close behavior and moving-sublevel geometry. Missing manual pixels are not proof of an additional disconnected source consumer.

EndSea's prior report was separately updated from the parent's actual successful unified build; its copied XML records four tests with zero failures/errors/skips.
