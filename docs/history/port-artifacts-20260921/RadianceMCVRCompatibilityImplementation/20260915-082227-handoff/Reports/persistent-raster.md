# Persistent VertexBuffer and applied ShaderInstance raster bridge

## Status

Implementation is complete and frozen in the assigned module. The current shared Radiance Java tree compiles, nine selected contract tests pass, and `git diff --check` passes in both repositories. The root task's first native core checkpoint compiled the persistent buffer and shader middleware without an error in these files. One final native-only change made after that checkpoint routes diagram uniforms to the bound DRAW framebuffer extent; it is frozen for the root task's next unified incremental core build.

No Minecraft client, GUI, or GPU raster smoke test was run. Runtime pixels, Veil/Sable visual behavior, swapchain recreation under a live client, and device-loss behavior remain pending dynamic acceptance.

## Implemented contract

### Owned persistent vertex and index resources

`VertexBufferMixins` no longer marks every vanilla `VertexBuffer` invalid. Constructor OpenGL allocation calls are suppressed, then two real native persistent registry names are allocated transactionally. If the second allocation fails, the first name is released.

`upload(MeshData)` creates a new native vertex/index pair, initializes real `vk::DeviceLocalBuffer` storage with the correct Vulkan usage, copies direct vertex bytes, copies explicit index bytes or builds the exact Minecraft sequential/quad/line index pattern, then atomically replaces the Java handles and releases the previous pair. The old buffers are retained for the active frame so an already-recorded draw cannot observe freed storage. `uploadIndexBuffer(Result)` replaces only the index buffer. Both input objects close on success and every failure path.

Persistent uploads are queued independently of the per-frame transient upload vector. This permits a long-lived buffer to be built before the first acquired frame. At submit, the upload command buffer records transfer barriers and copies before the overlay/custom-target command buffer consumes the vertex and index buffers. The upload batch is retained through the frame fence. Each subsequent write must call `initializeBuffer` first, which renames the Vulkan buffer instead of overwriting storage that an older frame may still read.

`close()` invalidates the Java handles first, releases both native names idempotently, and cancels the legacy OpenGL deletion path. `bind()`/`unbind()` retain the vanilla render-thread contract but do not issue OpenGL commands.

### Applied ShaderInstance source and draw semantics

The source of `VertexBuffer.draw()` shader state is explicit: `ShaderProgramMixins` records the exact `ShaderInstance` at `ShaderInstance.apply()` and clears it at `clear()`. The binding is render-thread-local, replacing the OpenGL current-program state that does not exist under `GLFW_NO_API`. A direct `draw()` without a preceding apply fails explicitly.

`RasterDrawBridge` reads the current shader, compiles or reuses the matching native shader variant for the actual vertex format/draw mode, snapshots all shader uniform buffers and sampler texture IDs, maps the projection matrix to Vulkan clip space, appends the uniform payload, and records a real indexed draw into the active `UIModuleContext` pass.

`drawWithShader(modelView, projection, shader)` preserves the Minecraft 1.21.1 contract:

1. Off-render-thread calls use `RenderSystem.recordRenderCall` and copy both matrices before scheduling.
2. On the render thread, the supplied shader receives the supplied model-view/projection matrices and all vanilla default uniforms.
3. The shader is applied, the indexed draw is recorded, and `clear()` runs in `finally`.

`BufferUploader._drawWithShader(MeshData)` uses the same bridge and now closes `MeshData` in `try`-with-resources even when shader translation, native compilation, allocation, uniform packing, or draw recording fails.

### Raster/custom-target boundary

The bridge accepts only `VULKAN_UI` and `VULKAN_CUSTOM_TARGET` decisions from `RenderCaptureContract`. A raw persistent draw in the default world stage has no `RenderType`, canonical material, target generation, world origin, or TLAS lifetime contract, so it fails explicitly instead of being submitted as opaque world geometry or silently discarded.

This matches the earlier world-mesh boundary: vanilla/Sable chunk sections already use the owned chunk/world paths; Sable water-occlusion regions and Veil immediate buffers are raster consumers; a nonzero Vulkan DRAW framebuffer selects the custom-target raster path. This module does not claim TLAS/world-metadata coverage.

The diagram post uniform middleware now reads `Framebuffers::snapshot(DRAW_FRAMEBUFFER).extent` whenever a nonzero DRAW framebuffer is bound, and retains swapchain size only for framebuffer 0. A bound target with no drawable extent is an explicit error.

## Files

Radiance:

- `src/main/java/com/radiance/mixins/vulkan_render_integration/VertexBufferMixins.java`
- `src/main/java/com/radiance/mixins/vulkan_render_integration/ShaderProgramMixins.java`
- `src/main/java/com/radiance/mixins/vulkan_render_integration/BufferRendererMixins.java`
- `src/main/java/com/radiance/client/proxy/vulkan/BufferProxy.java`
- `src/main/java/com/radiance/client/proxy/vulkan/ShaderProxy.java`
- `src/main/java/com/radiance/client/render/AppliedShaderState.java`
- `src/main/java/com/radiance/client/render/RasterDrawBridge.java`
- `src/test/java/com/radiance/client/proxy/vulkan/PersistentBufferAllocationTest.java`
- `src/test/java/com/radiance/client/render/AppliedShaderStateTest.java`

MCVR:

- `src/core/render/buffers.cpp`
- `src/core/render/buffers.hpp`
- `src/core/middleware/com_radiance_client_proxy_vulkan_BufferProxy.cpp`
- `src/core/middleware/com_radiance_client_proxy_vulkan_ShaderProxy.cpp`

No excluded UI module, UI framebuffer, texture, pipeline, world, entity, Veil, or Flywheel implementation file was changed by this module.

## Mixin registration

Required entries:

- `vulkan_render_integration.ShaderProgramMixins`
- `vulkan_render_integration.VertexBufferMixins`

Both entries were already present in the current shared `src/main/resources/radiance.mixins.json` at lines 105 and 112. This module did not edit the shared JSON. `BufferRendererMixins` was also already registered.

## Source evidence

The implementation was checked against the transformed Minecraft 1.21.1/NeoForge 21.1.250 `VertexBuffer.java`, `BufferUploader.java`, and `ShaderInstance.java`, plus Veil 4.3.2 persistent buffer/level renderer calls and Sable 2.0.5 `SimpleCulledRenderRegion`. Exact reference and implementation SHA-256 values are recorded in `Evidence/persistent-raster-source-hashes.tsv`.

## Verification

PASS:

- `compileJava` over the current shared Radiance source tree.
- `compileTestJava`.
- `AppliedShaderStateTest`: 2/2.
- `PersistentBufferAllocationTest`: 3/3.
- `RenderCaptureContractTest`: 4/4.
- Radiance `git diff --check`.
- MCVR `git diff --check`.
- Root task's first native core compile checkpoint reported no error from this module's buffers or middleware.

Pending outside this module's final static/Java gate:

- Root task's unified incremental native core build after the final DRAW framebuffer extent change.
- Real acquired-frame GPU upload/draw/close smoke test.
- Client visual acceptance for UI, custom framebuffer, Veil immediate-buffer, and Sable raster consumers.
- Mixin runtime application/conflict validation in the packaged client.

Evidence: `Evidence/persistent-raster-java-verification.txt` and `Evidence/persistent-raster-source-hashes.tsv`.
