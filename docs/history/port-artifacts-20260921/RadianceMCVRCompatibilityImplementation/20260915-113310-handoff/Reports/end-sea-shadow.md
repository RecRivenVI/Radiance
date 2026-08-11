# Simulated EndSea shadow / SimpleSubLevelGroup consumer recovery

## Implemented scope

Preserved the fixed Simulated 1.3.2 `EndSeaShadowRenderer.renderShadowMap` and `SimpleSubLevelGroupRenderer.renderGroup` bodies. The original shadow callback still gathers the real Sable sublevels, renders their section/single-block/BE/entity consumers into the original shadow FBO, then runs `spread_end_sea` **five times**. No duplicate callback, replacement sea shader, fabricated geometry, or success-on-error path was added.

The parent task owns main-world callback deferral, post-fuse WORLD_RASTER execution, pre-callback `SectionRasterStorage.drain`, sea ordering, and extra section PT/storage policy. It confirmed that external sublevels retain all original layer VertexBuffers for the original Sable dispatcher consumers. Nested explicit-FBO/perspective callbacks retain their immediate scheduling.

## Recovery

- Shadow WrapMethod captures the actual prior `isRenderingShadowMap`, read/draw FBO bindings, and viewport; it restores them after success or failure. Other stages retain their original direct return path.
- Group WrapMethod snapshots actual prior `RENDERING_SIMPLE`, Simulated visual-manager drawing flag, Sable BE dispatcher camera override, shader light directions, model-view/projection, CameraMatrices, and FBO/viewport.
- Required original per-layer setup/apply/clear calls are wrapped to track active layer/shader. If a draw or shader compilation throws, remaining active state is cleared.
- Required original model-view stack push/pop calls are tracked. A failure before the body's own try/finally cannot leak its push; the actual entry matrix is restored.
- After the original entry buffer flush succeeds, a failed group discards its remaining unsubmitted global buffer builders and their backing allocations. It does not submit those partial batches into a later main/post target. Normal successful draws are not discarded. The original error remains primary; every restoration is attempted and cleanup errors are suppressed onto it.
- If group failure interrupts original light-texture restoration, the original light update receives the original caller partial tick. Actual entry shader light directions are restored separately.

## Original shader/FBO chain inspected from the fixed JAR

`assets/simulated/pinwheel/framebuffers/end_sea_shadows.json`: 1024x1024 RGBA8, depth=true, autoClear=false. Original callback binds/clears it before group rendering.

`spread_end_sea.json` retains depth mask and two blit stages: `end_sea_shadows -> swap -> end_sea_shadows`; swap is also 1024x1024 RGBA8 with depth. The unchanged fragment shader samples color/depth, explicitly writes `gl_FragDepth`, and spreads alpha strength. Five original pipeline runs mean ten original blit stages. Existing generic Veil shader/AdvancedFbo bridges remain responsible for these operations.

## Sources / registration

New helpers:

- `src/main/java/com/radiance/compatibility/simulated/SimulatedRenderRecovery.java`
- `src/main/java/com/radiance/compatibility/simulated/SimulatedFramebufferRecovery.java`
- `src/main/java/com/radiance/compatibility/simulated/SimulatedGroupRenderRecovery.java`

New required mixins/accessors (parent registers and gates):

- `compatibility.simulated.SimulatedEndSeaShadowMixins`
- `compatibility.simulated.SimulatedSimpleSubLevelGroupMixins`
- `compatibility.simulated.SimulatedGroupBlockEntityCameraAccessor`
- `compatibility.simulated.SimulatedGroupVisualizationAccessor`
- `compatibility.simulated.SimulatedGroupBufferSourceAccessor`

Cross-target accessors require the parent's Simulated/Sable/Flywheel presence gate. Fixed source field contracts: Sable `sable$cameraPos : Vec3` instance field on BE dispatcher; Simulated `sable$drawingDiagram : boolean` static field on Flywheel VisualizationManagerImpl. The test verifies descriptors/staticness directly from the fixed packaged mixin bytecode, and verifies vanilla BufferSource fields from the actual test runtime class.

Test: `src/test/java/com/radiance/compatibility/simulated/SimulatedEndSeaShadowTest.java`.

No shared JSON, WorldRenderer, ChunkProxy, native, WorldRaster, or AfterWorldRender changes were made by this subtask. No game/UI/Git mutation/deletion was performed.

## Verification status

Implementation is frozen. Parent-coordinated `compileJava`, `compileTestJava`, `generateJniHeaders`, and test execution **passed**; parent evidence is `../Evidence/world-final-contract-tests.log`. Actual `TEST-com.radiance.compatibility.simulated.SimulatedEndSeaShadowTest.xml` records **4 tests, 0 failures, 0 errors, 0 skipped** and is copied into this subtask's evidence directory. Tests cover primary shader-error propagation with cleanup failures, all-state recovery attempts, exact injected field descriptors/staticness, and all seven required original wrapper invocation signatures. Parent also converted true 128-byte PBR section vertices to BLOCK32 fields for extra/shadow persistent buffers and reports its data tests passed in the same batch. Source inspection and packaged framebuffer/shader resources are not live draw evidence.

Remaining: runtime Mixin application including cross-mod injected accessors; actual sublevel shadow coverage, native depth ping-pong, exception injection in a live render, and final EndSea/post ordering require parent native/runtime evidence. This subtask does not declare those pending capabilities verified.
