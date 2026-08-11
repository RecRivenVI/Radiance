# Aeronautics levitite tessellation implementation

## Status

The bounded four-stage shader and four-control-point patch raster chain is implemented and source-frozen. Aeronautics' fixed levitite vertex, tessellation-control, tessellation-evaluation, and fragment resources now travel from Veil's processed `VeilShaderSource` objects through the Radiance external shader registry to real Vulkan shader modules and a patch-list graphics pipeline. Sorted Minecraft QUADS retain their selected quad order while each six-index triangle group is converted to four patch controls.

The current shared Java tree compiles and 25 selected tests pass. A no-window test read the actual Aeronautics 1.3.2 levitite stages and their actual Aero/Veil includes, ran the production ProgramModel/ShaderTranslator path, and generated four non-empty SPIR-V modules with `glslangValidator -V --target-env vulkan1.2`.

The root task's unified native build produced `core.dll` and the tessellation GPU-test executable. The independent no-window Vulkan fixture passed: a real four-stage patch pipeline consumed exactly four UINT16 indices and produced exactly the expected inner 12x12 pixels while the outside stayed clear. This fixture does not compile or draw the Aeronautics program. Separately, the actual four levitite resources passed offline SPIR-V compilation after Radiance translation. No game/client or visual acceptance run was performed. The root-owned world/section renderer still supplies levitite geometry and per-draw state; these two results do not prove that levitite blocks are visible in a world.

## Four-stage program chain

`ExternalShaderMetadata` carries optional tessellation-control/evaluation sources and a patch control count. `VeilShaderBridge.compileProgram` accepts the fixed vertex/fragment/TCS/TES graph, requires a complete tessellation pair, reads `layout(vertices = N) out`, and rejects every value except the fixed four-control-point contract. `ShaderRegistry.createExternal` translates and materializes all four stages, includes every stage in the shader cache key, and passes the stage paths and patch count through `ShaderProxy.registerShader`; the resulting `ShaderDefinition` retains the real native program id used at draw time.

Native `ShaderProxy` forwards those values to `UIModule::registerOverlayDrawShader`. The UI module creates real TCS/TES shader modules with their Vulkan stage kinds, adds them between vertex and fragment, and uses `DynamicGraphicsPipelineBuilder::definePatchControlPoints(4)`. The builder selects `VK_PRIMITIVE_TOPOLOGY_PATCH_LIST`, supplies `VkPipelineTessellationStateCreateInfo`, and rejects patch pipelines when the device feature is unavailable. The fixed custom-FBO pipeline hook uses the same four-stage shader bundle and patch count.

All overlay descriptor bindings now expose `VK_SHADER_STAGE_ALL_GRAPHICS`; the uniform UBO and sampler array are therefore visible in TCS/TES as well as vertex/fragment. `RendererProxy.tessellationSupported()` returns true only when a live framework, device, pipeline, and UI module exist and the Vulkan device enabled the real `tessellationShader` feature.

## Quad index and draw contract

Minecraft QUADS may arrive with a sorted explicit triangle index buffer. `BufferProxy` therefore creates a second index buffer only for the tessellation variant:

- Sequential QUADS become `0,1,2,3` per quad.
- Explicit SHORT or INT groups must match Minecraft's `[a,b,c,c,d,a]` pattern.
- Each group becomes `[a,b,c,d]`; group order is unchanged, so sort-on-upload ordering is retained.
- Vertex count must be divisible by four, index byte counts must match, and every index must be in range.

Transient and persistent handles carry the patch buffer id/count alongside the ordinary triangle buffer. Persistent upload, index replacement, close, failure cleanup, and native buffer retirement include that third resource. `VertexBuffer.drawWithShader` snapshots all buffer ids and draw fields before `RenderSystem.recordRenderCall`, so a queued call does not later read fields changed by another upload or close.

At native draw time, non-tessellated shaders bind the original triangle index buffer/count. A tessellated shader requires the patch buffer, requires a nonzero count divisible by four, binds it using the original SHORT/INT index type, and records `drawIndexed(patchIndexCount, 1)`. It cannot silently draw the six-index triangle representation as patches.

## Coordinates and fixed levitite data

For external vertex/fragment programs the vertex shader remains the final position stage. For tessellated programs the vertex shader keeps Aeronautics' object/world position and the GL-to-Vulkan clip conversion is wrapped around the tessellation-evaluation `main` only:

```glsl
radianceExternalMain();
gl_Position.y = -gl_Position.y;
gl_Position.z = (gl_Position.z + gl_Position.w) * 0.5;
```

This preserves the original GL matrices and avoids applying the conversion once in vertex and again after tessellation. The wrapper executes after the user main, including when that main returns early.

The levitite fragment shader's fixed `gl_FragCoord.xy` expression is rewritten to use `RadianceTargetHeight - gl_FragCoord.y`. The synthetic float is filled from `FramebufferProxy.dimensions(DRAW_FRAMEBUFFER)`, which is the complete current draw-target snapshot; a missing or zero height fails the draw rather than substituting the swapchain or viewport height. Framebuffer samplers retain the separate payload-bit Y flip used for OpenGL FBO texture coordinates.

The fixed shader parser consumes levitite's matrices, vectors, scalars, integer layer/sublevel state, samplers, and arrays. It now supports both GLSL array spellings used by the actual resources, including Veil's `uniform float[6] VeilBlockFaceBrightness;`, and removes the original standalone uniform before emitting the std140 draw UBO. The actual resource compilation verifies the fixed `Noise`, `Sampler0`, `Sampler2`, `DiffuseDepthSampler`, material matrices, motion/offset fields, light array, screen/depth values, and four-stage varyings at SPIR-V compile time. Runtime values still depend on the root-owned world/section caller and Veil uniform updates.

## Veil production preprocessing under NO_API

Veil 4.3.2 `ShaderManager.prepare` and `createDynamicProgram` called `GL.getCapabilities()` before constructing preprocessor contexts. Under Radiance's NO_API window this is a production failure, not only a unit-test limitation. `VeilShaderManagerCapabilitiesMixins` now redirects the two fixed calls to an explicit null capability object. `VeilShaderVersionProcessorMixins` handles that state by selecting GLSL 460 for Veil's default 110-core tree and cancels only the processor's OpenGL 4.1/3.3 branch.

A bytecode audit of all 13 fixed Veil shader processor classes found that only `ShaderVersionProcessor` dereferences `Context.glCapabilities`. The four fixed Sable processors and Simulated's EndSea transformer do not reference it; Aeronautics registers no shader preprocessor in its audited client entry. `ShaderFeatureProcessor` remains active and reads Veil's real supported-feature mask, which is backed by the Vulkan capability methods. The null GL object therefore does not turn all feature macros off or invent an OpenGL capability object.

The no-window resource test cannot invoke the exact private `ShaderManager.readShader`: its normal processor construction instantiates `ShaderInjectProcessor`, which requires the live `VeilRenderer` injection manager. The production root path now creates that real renderer, so the production method retains Veil processors, definitions, imports, resource loading, and external registration. For current evidence, the test expands the exact fixed Aero/Veil include resources and then executes the same ProgramModel/ShaderTranslator used after `readShader`. This proves the fixed four-stage source compiles after Radiance translation, but it is not evidence that a live resource reload or third-party dynamic injection ran.

`build.gradle` adds the fixed non-transitive Veil artifact only to the existing compatibility compile extraction configuration. The existing task extracts Veil's embedded `glsl-processor-0.2.3.jar` so the mixin uses the exact `GlslTree` type. Product runtime dependencies were not broadened.

## Mixin registration

The root task registered these new fixed-owner entries in `radiance.mixins.json`:

- `compatibility.veil.VeilPatchStateShardMixins`
- `compatibility.veil.VeilShaderManagerCapabilitiesMixins`
- `compatibility.veil.VeilShaderVersionProcessorMixins`

`VeilPatchStateShardMixins` intercepts the fixed 4.3.2 `PatchStateShard` apply/clear lambdas, records the patch vertex count in render-thread state, and cancels the raw `glPatchParameteri` calls. Uniform packing requires that active patch state to match the compiled program metadata.

## Verification

PASS:

- Current shared-tree `compileJava` after the final asynchronous draw snapshot change.
- Current shared-tree `generateJniHeaders` after the expanded ShaderProxy/RendererProxy/FramebufferProxy declarations.
- 25 selected JUnit tests, zero failures/errors/skips:
  - `VeilShaderBridgeTest`: 10, including actual fixed levitite resource translation and four-stage SPIR-V generation, TCS/TES varying locations, final-stage clip conversion, target-height rewrite, fixed Veil array spelling, std140/matrix packing, lazy sampler resolution, and fixed Veil ABI reflection.
  - `PersistentBufferAllocationTest`: 5, including SHORT and INT sorted-quad conversion and three-resource retirement.
  - `ShaderTranslatorTest`: 3.
  - `VeilMixinCompatibilityTest`: 4.
  - `AfterWorldRenderTest`: 3.
- Actual fixed Aeronautics 1.3.2 levitite vsh/tcsh/tesh/fsh plus Aero/Veil includes compiled into four non-empty Vulkan 1.2 SPIR-V files. Source and binary hashes are captured.
- Fixed Veil, embedded GLSL processor, Sable, Simulated, and Aeronautics artifact hashes and processor capability audit are captured.
- Root unified native build: `core.vcxproj` produced `build/src/core/Release/core.dll`; `mcvr_tessellation_gpu_test.vcxproj` produced its Release executable.
- Root headless `mcvr.tessellation-gpu` fixture: PASS in 0.47 s. Its real four-stage Vulkan pipeline bound exactly four UINT16 indices and validated all 144 expected colored pixels plus every clear exterior pixel. The fixture did not use the Aeronautics levitite shaders.
- Root combined native run: `mcvr.instancing-contract`, `mcvr.framebuffer-readback-contract`, `mcvr.tessellation-gpu`, and `mcvr.framebuffer-gpu` passed 4/4 in 1.32 s.

Pending or owned elsewhere:

- Runtime mixin application and exact private `ShaderManager.readShader` with a live Veil renderer/injection registry.
- Root world/section geometry route and runtime levitite values.
- A/B game visual acceptance for levitite and ghost layers.

## Evidence

- `Evidence/levitite-java-spirv-verification.txt`
- `Evidence/levitite-preprocessor-abi-audit.txt`
- `Evidence/levitite-external-processors-audit.txt`
- `Evidence/levitite-source-freeze.sha256`
- `Evidence/levitite-processed-levitite.vert` and `.vert.spv`
- `Evidence/levitite-processed-levitite.tesc` and `.tesc.spv`
- `Evidence/levitite-processed-levitite.tese` and `.tese.spv`
- `Evidence/levitite-processed-levitite.frag` and `.frag.spv`
- `Evidence/tessellation-integrated-build-final.log`
- `Evidence/tessellation-and-readback-tests.log`
