# Radiance native migration N0/N1 checkpoint

Updated: 2026-08-24  
Target: `1.21.1-neoforge` only  
Runtime launched: no  
Commits created: 0

## N0 implemented

- Radiance-owned generation-safe typed resource handles with deferred retirement by completed frame serial.
- Two-frame scheduler with explicit `idle → recording → submitted → presented → completed` transitions.
- Scheduler integrated into the actual Vulkan acquire/record/submit/present loop.
- One frame-level queue submit; high-frequency semantic calls do not submit or wait individually.
- Bounded native diagnostic ring; file output is drained in batches rather than opening a file per semantic event.
- Optional `VK_LAYER_KHRONOS_validation` plus `VK_EXT_debug_utils` messenger; unavailable validation is explicitly diagnosed.
- Swapchain generation tracked as a Radiance native resource handle.
- Official-frame upload uses one persistently mapped staging buffer per in-flight frame.
- Upload data is copied only when dirty and recorded into the same frame command buffer before blit/present.
- Normal upload/present does not call `vkDeviceWaitIdle` or `vkQueueWaitIdle`; device idle remains only at resource-resize, swapchain-recreation and shutdown boundaries.
- Existing zero-extent, resize, failure classification and orderly shutdown state machines remain.

## N1 foundation implemented

- Native `GlStateSnapshot` for texture units, object bindings, viewport, scissor, blend, depth/stencil, raster and color-mask state.
- Dirty-bit state tracking and deterministic `PipelineKey` inputs.
- Bounded `FrameCommandStream` for upload/clear/draw/blit/present packets, sealed once per frame.
- Aligned bounded `StagingArena` allocation model.
- The actual M1/M3 present loop emits its upload/render/present intent into the new command stream and feeds its aggregate metrics into the frame scheduler.

This is foundation, not GUI completion. Native buffer/image/sampler/shader/pipeline/descriptor objects and exact GL JNI integration remain N1 work.

## Rejected M4 containment

- `radiance.m4.enabled=false` and runtime milestone is `MIGRATION_N0`.
- The rejected 1 FPS CPU translator cannot be launched by current Gradle runtime configuration.
- Its logs and coverage remain preserved as failure evidence.
- Its historical source is not accepted architecture and will be removed after the clean N1 object/resource/draw path covers the required call boundary; it is not being patched or extended.

## Automated evidence

- Decision validator: PASS.
- Prohibited MCVR identity in authored new repository: 0.
- Authored generated binaries under `src`: 0.
- Native CTest: 14/14 PASS.
- Five-node build/package: PASS.
- Non-authorized node business class entries: 0/0/0/0.
- `1.21.1-neoforge` remains the only business-bearing artifact.
- Configuration cache: reused on second five-node build and second authorized-node build.
- PRIMARY S1 Stage/Verify/native/service/game hash manifest: PASS.

Current staged hashes:

- native DLL: `55A8859BA7FE5B4AAC08640DB3BB4E23A49B287C2778A7728939A5999DBD3392`
- SERVICE JAR: `43E053FD0156AA533B07672F18840EED90F8EAE855771D6CE4ED4C980CEDD4E5`
- GAME JAR: `DB97863BAF46BD106A7F1D6AC78C33DA0899C1C24848548FCBF48772529028A4`
- patched official Early Display: `27889640931955ED59E1E6656B8D9416835A79C4C5D1DBC82FAD8A7C0ABD5562`

## Acceptance boundary

- `N0_AUTOMATED = PASS`.
- `N0_RUNTIME = NOT_RUN`.
- `VULKAN_VALIDATION_RUNTIME = PENDING`.
- `M3_REGRESSION_ON_MIGRATED_CORE = PENDING`.
- `N1_GUI_SEMANTICS = STATIC_UNIT_ONLY`.
- `M4_USER_ACCEPTANCE = FAIL` remains unchanged.
- No client should be launched until N1 primitive fixtures and N2 official Early Window migration gates are ready.
