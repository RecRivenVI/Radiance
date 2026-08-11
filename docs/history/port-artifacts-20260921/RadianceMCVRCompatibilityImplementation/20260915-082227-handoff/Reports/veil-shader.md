# Veil 4.3.2 Vulkan ShaderProgram bridge

## Status

The bounded vertex/fragment ShaderProgram bridge is implemented and frozen. The source path routes Veil's processed shader sources through `ShaderProxy.registerShader` to the real native Vulkan graphics-pipeline API, binds through the Veil wrapper, and supplies current uniforms, samplers, default matrices, and std140 shader blocks to the indexed draw payload. This path was verified by compilation, parser/layout tests, and binary descriptor checks; actual GPU compilation and draw consumption were not executed.

The current shared Radiance Java tree compiles. Seventeen selected tests pass. The fourteen new mixins are registered in the shared mixin config. No game/client, OpenGL context, GUI automation, GPU draw, commit, stage, or push was performed.

Aeronautics levitite tessellation remains a real unfinished fixed consumer. Sable/Flywheel custom vertex-array and std430 instance paths remain owned by the instancing backend. These gaps are not reported as covered by this graphics bridge.

## Real program compilation and reload

`DirectShaderCompiler` still receives Veil's fully preprocessed source, including imports, shader definitions, and `#veil:buffer` expansion. Its OpenGL compile call is replaced with a tracked source-stage object. `ShaderProgramImpl.recompile` accepts only a vertex/fragment graph, resolves a missing vertex to an explicit three-vertex fullscreen shader and a missing fragment to Veil's dummy fragment, then passes the processed pair through the extended Radiance `ShaderTranslator` and `ShaderProxy.registerShader`.

The resulting `CompiledProgram.program` value is the real native overlay/custom-target pipeline id for the initial QUADS variant. It is not an invented OpenGL program number. The Veil manager therefore owns a valid `ShaderProgramImpl` after reload, while subsequent draw modes create/reuse their own native variants through `ShaderRegistry`'s normal cache and stable source key.

Reload replacement frees captured stage handles, invalidates old CPU uniform objects, swaps the Veil program model, and retains native shader variants in the existing native key cache. A failed unsupported stage affects that program's normal Veil reload result; it does not turn manager presence into success.

## Bind, uniforms, blocks, and draw consumption

`ShaderProgramImpl.bind` records the exact current Veil program and its program-local blocks without calling OpenGL. Its `Wrapper.apply/clear` participates in `AppliedShaderState`, so `BufferUploader` and persistent `VertexBuffer.draw()` consume the exact selected Veil program rather than the last vanilla/global guess.

Default uniform handling no longer depends on the wrapper's dummy `ShaderInstance` fields. Both the Veil program and wrapper routes write live ModelViewMat, ProjMat, TextureMat, ColorModulator, fog, glint alpha, game time, line width, and screen size into Veil `ShaderUniformImpl` CPU storage. All Veil uniform setters update that storage while their OpenGL upload methods are suppressed. `ShaderProxy.createUniform` recognizes the external wrapper and packs those bytes into the native draw UBO.

The source parser supports scalar/vector/mat2/mat3/mat4 values, simple fixed arrays, samplers, and std140 blocks. It merges every supported value into the single native dynamic draw UBO, rewrites block field references to collision-free names, preserves std140 vector/matrix/array alignment, and copies live block serializer output at draw time. `VeilShaderBlockState` captures the real name-to-block binding; `SizedShaderBlockImpl` and `DynamicShaderBlockImpl` serialize current Java values without allocating an OpenGL buffer. Veil's normal unbind/free state bookkeeping still runs.

`VeilShaderBufferCache` can therefore discover and construct fixed registered blocks from the parsed `ShaderUniformCache`. The Simulated EndSea `VeilCamera` block is backed by the fixed CameraMatrices layout: ProjMat, IProjMat, ViewMat, IViewMat, IViewRotMat, CameraPosition, NearPlane, CameraBobOffset, and FarPlane. Sable's dynamic `SableSprites` std140 `vec4[2 * N]` source shape and serialized array bytes are also supported by the parser/packer, although its custom vertex-array draw remains an instancing-backend item described below.

`VeilShaderBridge.drawScreenQuad()` uses the currently bound ShaderProgram explicitly, creates a real transient three-vertex buffer, and calls `RasterDrawBridge` with the program wrapper. It does not depend on an undefined RenderSystem shader or a fake VAO.

External shaders keep their original OpenGL projection matrices and block data. `ShaderRegistry.createExternal` uses an external-only translator entry that renames the user vertex `main` and wraps it. After the user main returns, including through any early return inside it, the wrapper converts final clip coordinates with `y = -y` and `z = (z + w) * 0.5`. Vanilla shaders retain their existing ProjMat conversion and do not receive this second transform.

## Sampler and framebuffer orientation

Both vanilla and Veil uniform packers call `TextureProxy.isFramebufferTexture(id)`. Bit 31 of the sampler payload marks a framebuffer attachment; the remaining bits are the native descriptor index. Ordinary uploaded textures keep their UV direction.

The shader translator replaces shader-body `sampler2D` types, including function parameters, with `RadianceSampler2D`. It provides overloads for the fixed consumers' `texture`, `textureLod`, `textureSize`, and `texelFetch` calls. UV or texel Y is inverted only when the payload marks a framebuffer texture. This preserves OpenGL FBO sampling semantics over Vulkan attachments stored with a top-left origin, including target aliases.

The fixed Sable 2.0.5, Simulated 1.3.2, and Aeronautics 1.3.2 shader resources use no textureGrad/textureProj/textureGather/offset query that would require another overload. The only fixed source using `gl_FragCoord` is Aeronautics levitite's tessellated fragment shader. Since that program remains rejected at its tessellation stages, this bridge does not invent a target-height transform for an unreachable fragment stage.

## Fixed consumer matrix

| Consumer | Shader/data requirements | Result |
|---|---|---|
| Sable water occlusion region | Vanilla Position Shader, persistent Position VertexBuffer | Source is wired to the public persistent raster bridge; it does not require a Veil ShaderProgram. Actual GPU consumption remains unverified. |
| Simulated EndSea layers | Vertex+fragment, POSITION_COLOR_TEX_LIGHTMAP, default matrices/fog, VeilCamera std140 block, Sky/ShadowDepth/ShadowStrength samplers, ShadowVolumeSize and StartY | Source and parsing contracts are verified; FBO shadow sampler payloads carry Y-flip metadata. Actual native shader compilation, block consumption, and pixels remain unverified. |
| Simulated spread_end_sea post | Fragment-only fullscreen program and bound framebuffer samplers | The source path supplies an explicit fullscreen vertex stage and routes `drawScreenQuad` to the real raster API. This shader was not passed through a live `ShaderRegistry.createExternal`/native compiler run; post scheduling, targets, and pixels remain unverified. |
| Aeronautics burner_flame | Vertex+fragment, POSITION_TEX, FirePalette, FlameRenderTime, Intensity, Palette, fog defaults | Wrapper, uniform, sampler, and BufferUploader source wiring is present; actual GPU compilation/consumption remains unverified. |
| Aeronautics hot_air_overlay / heated balloon region | Vertex+fragment, POSITION_TEX_COLOR_NORMAL, Scroll, CutoffY, ColorModulator, Sampler0/1, persistent VertexBuffer and custom FBO | Veil wrapper, persistent buffer, custom-target, and framebuffer sampler source wiring is present. Actual FBO/post draw and pixels remain unverified. |
| Aeronautics heated occlusion region outside the overlay | Vanilla ShaderInstance, persistent POSITION_TEX_COLOR_NORMAL | Source is wired to the public raster bridge when a supported raster/custom-target scope is active; actual GPU draw remains unverified. |
| Sable fancy sublevel SableSprites | Dynamic std140 vec4 array, SableTransform, custom QuadPosition/SableNormal/SableData attributes | Block parsing and serialization layout contracts are verified, but the custom VertexArray/attribute buffer is not one of the public raster layouts. End-to-end draw remains a Flywheel/instancing backend task. |
| Aeronautics levitite | Vertex+fragment plus tess-control/evaluation, custom material uniforms, depth sampler and gl_FragCoord | Not complete. Tessellation capability remains false and the program is rejected explicitly. It requires a future native tessellation pipeline, viewport/target-height gl_FragCoord contract, and its Sodium/custom world draw integration. |
| Flywheel/Sable indirect instance shaders | std430 SSBOs, custom instance attributes, indirect draw buffers | Not complete in this bridge. Storage-block binding is rejected explicitly; the native instancing task owns the real storage/descriptor/draw contract. |

## Mixin registration

Registered current entries:

- VeilDirectShaderCompilerMixins
- VeilCompiledShaderMixins
- VeilShaderProgramImplMixins
- VeilCompiledProgramMixins
- VeilShaderUniformCacheMixins
- VeilShaderUniformImplMixins
- VeilShaderProgramWrapperMixins
- VeilShaderProgramInterfaceMixins
- VeilSizedShaderBlockMixins
- VeilDynamicShaderBlockMixins
- VeilWrapperShaderBlockMixins
- VeilShaderBlockStateMixins
- VeilShaderTextureMixins
- VeilShaderTextureCacheMixins

All use the `compatibility.veil.` prefix. The discarded `VeilShaderBlockImplMixins` was moved to the Recycle Bin and is not registered; cancelling its superclass free method would have skipped Veil's legitimate binding-state cleanup.

## Verification

PASS:

- Current shared-tree `compileJava` and regenerated JNI headers.
- Current shared-tree `compileTestJava`.
- `VeilShaderBridgeTest`: 7/7, including fixed 4.3.2 reflected method signatures, EndSea/VeilCamera parsing, SableSprites array layout, fullscreen gl_VertexID translation, external clip conversion after early return/block projection, native-order matrix packing, and lazy sampler fallback.
- `VeilMixinCompatibilityTest`: 4/4, including the restored transparency, GUI block, Particle, and F3 handlers while retaining raw-GL shader recompile interception.
- `ShaderTranslatorTest`: 3/3, including std140 arrays and framebuffer sampler wrappers passed through a user shader function parameter.
- `AfterWorldRenderTest`: 3/3, requested by the root task.
- Fixed Veil 4.3.2 binary descriptor audit: all 14 registered mixin targets passed exact owner, field, method, inheritance, and overload checks with `javap -p -s`.
- `git diff --check`.
- Exact Veil 4.3.2, Sable 2.0.5, Simulated 1.3.2, and Aeronautics 1.3.2 fixed JAR hashes captured.

The first test run compiled source successfully but JUnit could not discover the Veil test because Veil was compile-only. `build.gradle` now adds only `foundry.veil:veil-neoforge-1.21.1:4.3.2` as a non-transitive testRuntimeOnly dependency. The later selected runs passed; product runtime dependencies were not broadened.

Pending dynamic evidence:

- Root task's unified native build after the refreshed JNI headers.
- Runtime mixin application and Veil manager reload against the packaged client.
- Real GPU shader compilation and pixels for EndSea, burner, balloon overlay, and fullscreen post.
- Resource-reload replacement while frames are in flight.
- The explicitly unfinished tessellation, custom VertexArray, indirect, and SSBO consumers above.

Evidence:

- `Evidence/veil-shader-java-verification.txt`
- `Evidence/veil-shader-source-freeze.sha256`
- `Evidence/veil-shader-mixin-descriptor-audit.txt`
