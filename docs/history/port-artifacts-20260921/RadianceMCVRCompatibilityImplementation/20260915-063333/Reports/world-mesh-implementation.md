# World / Dimension MeshData native sink implementation

Date: 2026-09-15

## Implemented boundary

`RenderType.draw(MeshData)` is now the only generic world-stage interception point. It retains the concrete `RenderType`, the still-live vertex/index buffers, draw mode and index type. Raw `BufferUploader` calls remain rejected in a default world scope because they no longer carry RenderType/material/source metadata.

The Java sink creates an explicit frame and stage contract containing the `ClientLevel` object identity, dimension key, monotonic world token, frame token, resource generation, camera origin, stage, coordinate space, target kind and source identity. A same-dimension re-entry with a different `ClientLevel` receives a new world token. Stage submissions are synchronous and are rejected once their token closes.

Only `DEFAULT_WORLD` submissions enter the TLAS route. A nonzero bound draw framebuffer, or a RenderType whose `OutputStateShard` is not `MAIN_TARGET`, remains on the Vulkan raster/custom-target path. `SKYBOX` is explicitly unsupported by this world sink.

## Mesh and material preservation

The sink accepts only formats enumerated by the existing Java/native vertex ABI and only surface modes: triangles, quads, triangle strips and triangle fans. Java validates byte counts from the declared format and index type. Native code copies the data before JNI returns.

If MeshData owns an index buffer, the actual short/int indices are copied. Triangle and sorted-quad index order is preserved. Triangle strips and fans are explicitly converted to triangle lists for BLAS input. When a quad MeshData has no explicit index buffer, the canonical six-index pattern is generated from each four vertices.

The RenderType snapshot carries the canonical texture resource, verified live native texture ID, concrete shader name, alpha mode, emission, geometry type and a material key containing transparency, depth, cull, lightmap, overlay, layering, output, texturing, write-mask, line and color-logic shard identities. Known vanilla RenderType/position shaders and Create's `glowing_shader` are accepted. Unknown shader identities are rejected with the actual key.

PBR source vertices keep their encoded alpha mode and emission; RenderType emission may raise their emission. Non-PBR converted vertices receive the captured RenderType alpha mode and emission instead of the old implicit opaque default. Shader/material keys and world generation metadata remain attached to the native entity geometry through BLAS/TLAS preparation.

## Lifecycle and exception safety

World frame and stage scopes use owner-thread and LIFO tokens. Native stage checkpoints roll back geometry queued by a listener when the listener throws. A frame exception rolls back the whole frame's queued data. Resource reload invalidates the resource generation and removes queued world-sink data. `LevelRenderer.close` invalidates the world identity. TLAS preparation filters world-sink entities whose world/frame/resource generation no longer matches.

Create ValueBox, `Gui.render`, `Screen.renderWithTooltip`, and the GameRenderer GUI tail now use method-level `try/finally` / `WrapMethod` cleanup rather than relying only on RETURN injection. Captured ValueBox providers and every produced MeshData are closed on success, rejection and exception.

The manual NeoForge stage PoseStack is now identity, matching Minecraft 1.21.1. Create's own `pos-camera` transform therefore occurs once and the sink declares those stage vertices as `CAMERA_SHIFT`.

Dimension callback booleans are paired with the accepted-world-mesh count. `renderSky=true` cannot suppress the ordinary Radiance sky when the SKYBOX sink accepted no geometry. Clouds and weather suppress their fallback only when the callback returned true and native world geometry was accepted.

## New mixin registrations required

- `vulkan_render_integration.RenderTypeWorldSinkMixins`
- `vulkan_render_integration.accessor.RenderTypeCompositeStateAccessor`
- `vulkan_render_integration.accessor.RenderTypeShaderStateAccessor`

## JNI contract

The existing JNI owner `com.radiance.client.proxy.world.EntityProxy` gained:

- `beginWorldMeshFrame(long,long,long): int`
- `beginWorldMeshStage(long,long,long,long,int): int`
- `queueWorldMesh(...): int`
- `endWorldMeshStage(long,long,long,long,boolean): void`
- `endWorldMeshFrame(long,long,long,boolean): void`
- `invalidateWorldMeshGeneration(long): void`

There is no new JNI owner. `queueWorldMesh` receives all vertex/index counts, byte lengths and pointers plus generation, stage, coordinates, source, material, alpha, emission, texture and shader metadata. Native return status distinguishes accepted, stale and unsupported submissions.

## Verification status

- `git diff --check`: PASS in Radiance and MCVR.
- Java compile/tests: PASS. `compileJava`, `test`, and `bootstrapTest` completed in the unified Gradle run; evidence: `Evidence/world-sink-java-tests-1.log`.
- MCVR core compile/link: pending final unified integration because the shared UI/Textures core is still changing.
- New native contract test: PASS. `tests/world_mesh_contract_test.cpp` compiled with an independent g++ C++17 command, then the registered MSVC/CMake Release target `mcvr_world_mesh_contract_test` built successfully and `ctest -C Release -R ^mcvr\.world-mesh-contract$` passed 1/1.
- Source freeze: `Evidence/world-mesh-source-freeze.sha256` records the exact SHA-256 values at this checkpoint.
- Client/visual acceptance: deferred by task boundary; no game or GUI launch was performed.

## Explicit remaining boundaries

- SKYBOX geometry still has no native sky consumer. Its MeshData is reported unsupported and cannot by itself suppress the standard sky.
- RenderTypes with non-main output remain dependent on the separate custom framebuffer raster pass; the world sink intentionally does not place them in TLAS.
- Shader semantics outside known vanilla shader families and Create `glowing_shader` remain unsupported even though their concrete shader names are reported.
- `AFTER_LEVEL` occurs before `EntityProxy.build` / `RendererProxy.fuseWorld` in the current replacement sequence. A Veil post effect reading the default target at that stage can observe a surface that has not yet received the fused world image. Callback completion is not evidence that this output ordering is correct.
- Persistent `VertexBuffer` is not covered by the MeshData sink. `VertexBuffer.upload` currently sees only MeshData and `draw` / `drawWithShader` later sees only shader/matrices; neither boundary has a RenderType, canonical texture, alpha/output state, stage origin provenance and live MeshData at the same time. Retaining the upload bytes alone would not make a valid world material contract.
- Concrete persistent-buffer callers were audited. Vanilla star/sky/dark/cloud buffers are bypassed by the Radiance LevelRenderer replacement; vanilla and Sable chunk-section buffers are routed through ChunkProxy/SableSubLevelBridge. Sable `SimpleCulledRenderRegion` (water-occlusion region) remains a real persistent VertexBuffer call with shader/matrix but no RenderType and belongs to a raster/occlusion target, not TLAS. Veil's `BufferUploader.lastImmediateBuffer.drawWithShader` likewise remains a raster/custom-shader path. Ponder `PonderWorldParticles` and placement helpers call raw BufferUploader and can only use the custom-target/UI route when an explicit framebuffer scope exists; they are not claimed as world-sink coverage.
- A future persistent-buffer bridge needs owned native vertex/index resources, replacement/close semantics, and source-side RenderType/target metadata. This implementation does not cache Java MeshData indefinitely or infer opaque material from the shader/stride.
- Runtime rendering, device synchronization and visual parity require the later client acceptance pass.
