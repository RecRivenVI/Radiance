# Sable 2.0.5 custom VertexArray bridge

## Status

The bounded Java and native source implementation is written and source-frozen. It replaces Sable's fixed OpenGL VAO, vertex/index/instance/indirect buffers, mapped staging ring, shader-source introspection, and multi-draw call with explicit Radiance/Vulkan resources and contracts.

The Java tree compiles, generated JNI headers match the new declarations, and 27 selected tests pass. The delegated root task entered a blocked state before registering the mixins, compiling native source, registering the GPU fixture, or running it. Those items remain pending and this report does not claim that the native pipeline or a Sable scene has run.

## Fixed callers and layout

The fixed Sable 2.0.5 JAR contains only two classes that directly reference Veil `VertexArray`:

- `FancySubLevelRenderDispatcher` creates the array, uploads the 192-byte six-face static vertex table and six BYTE quad indices, declares binding 0, binds it during `renderSectionLayer`, and frees it.
- `BucketRenderBuffer` owns the packed per-quad instance buffer and declares binding 1 when its storage changes.

`FancySubLevelCommandBuilder` is the third required consumer because it constructs and submits the indexed-indirect commands. Its fixed commands are standard `VkDrawIndexedIndirectCommand` fields in the same order:

```text
indexCount    = 6
instanceCount = slice.length
firstIndex    = 0
vertexOffset  = direction.ordinal * 4
firstInstance = slice.offset
```

The exact fixed vertex layout is:

| Binding | Rate | Stride | Location | Shader input | Encoding | Offset |
|---:|---|---:|---:|---|---|---:|
| 0 | vertex | 8 | 0 | `vec3 QuadPosition` | 3 signed BYTE, non-normalized / SSCALED | 0 |
| 0 | vertex | 8 | 1 | `vec3 SableNormal` | 3 signed BYTE, non-normalized / SSCALED | 4 |
| 1 | instance | 8 | 2 | `uvec2 SableData` | 2 unsigned INT, integer | 0 |

`SableData` retains Sable's packed position, light, texture id, section offset, and per-corner ambient-occlusion bits. The fixed `fancy_sublevel_vertex.glsl` still reconstructs `Position`, `Normal`, `Color`, `UV0`, and `UV2`; `SableSprites` supplies the original atlas coordinates and `SableTransform` supplies the per-sublevel transform.

## Veil VertexArray resource model

`VertexArray.create` now returns `RadianceVertexArray`, whose id names a real native VertexArray state object rather than an OpenGL VAO or invented success value. The native object stores explicit binding, attribute, and index-buffer state. Each owned buffer id names a persistent `vk::DeviceLocalBuffer` in the existing buffer manager.

`RadianceVertexArrayBuilder` implements Veil's builder calls and forwards every binding/attribute mutation to the native state. It accepts vertex or per-instance rate and the fixed BYTE/UNSIGNED_INT encodings. Unsupported divisors and 64-bit attributes fail explicitly.

Static uploads initialize and copy real persistent Vulkan buffers. Sable's six unsigned-BYTE indices are expanded in order to six UINT16 values because Vulkan has no BYTE index type. `firstIndex`, `vertexOffset`, `firstInstance`, `instanceCount`, and index order remain unchanged. Array free releases the native layout object and only its owned binding-0/index buffers; the externally-owned bucket buffer retains its independent lifetime.

## Bucket, staging, and indirect lifetime

`RadianceSableStagingBuffer` replaces the GL persistent mapped staging implementation with a native host allocation. Reservations remain four-byte aligned. `copy(destination, offset)` submits the exact written batch as a range update and resets the host range only after native staging has copied it. An unsubmitted batch causes `updateFencedAreas` to fail instead of being silently discarded.

`SableBufferBridge` maintains a complete CPU mirror for resize preservation. A range update first queues the native copy and only then commits the mirror, so a failed JNI operation is not recorded as a successful CPU update. Bucket growth creates a replacement Vulkan buffer, uploads the complete preserved mirror, and retires the prior buffer through the existing frame resource retainer.

Native range uploads use copy-on-write across submitted frames. The first update after submission creates a same-sized replacement, clones the complete old persistent staging contents, applies the changed range, swaps the live id record, and retains the prior device/staging buffer until its frame completes. Further ranges in the same recording frame share that replacement. Unmodified bytes therefore remain defined and CPU writes do not race a transfer already in flight.

The upload barrier now exposes persistent uploads to `DRAW_INDIRECT`, `VERTEX_ATTRIBUTE_INPUT`, and `INDEX_INPUT`, including `INDIRECT_COMMAND_READ`. The draw path resolves both vertex bindings, the converted index buffer, and the indirect buffer, then records `vkCmdDrawIndexedIndirect` with the original command count/stride.

Before recording, native validation reads the staged command data and checks:

- `firstIndex + indexCount` against the converted index buffer;
- every indexed vertex plus signed `vertexOffset` against the static binding capacity;
- `firstInstance + instanceCount` against the per-instance binding capacity;
- the indirect byte range and stride;
- runtime binding/attribute layout equality with the shader pipeline layout.

## Shader integration

`CustomVertexLayout` carries explicit multi-binding metadata through `ExternalShaderMetadata`, `ShaderRegistry`, the extended `ShaderProxy.registerShader` JNI payload, and `OverlayDynamicDrawShaderInfo`. Both dynamic and ordinary graphics pipeline builders now consume a vector of Vulkan binding descriptions; every existing single-binding format still produces a one-element vector.

`VeilShaderBridge` recognizes the processed fixed inputs `QuadPosition`, `SableNormal`, and `SableData`, selects the Sable layout, and retains existing std140 parsing for `SableSprites` and `SableTransform`. Custom layout data is included in the shader cache key so the native pipeline cannot be reused under a different binding contract.

The original `FancySubLevelRenderDispatcher.getDynamicProgram` queried attached OpenGL shaders with `glGetProgrami`, `glGetAttachedShaders`, and `glGetShaderSource`. `SableShaderBridge` replaces that private path with the resolved vertex/fragment sources already captured by `IShaderProgramExt`, preserves the active dynamic-buffer key, and calls Veil's real `ShaderManager.createDynamicProgram`. Sable's normal preprocessor still injects `fancy_sublevel_vertex`, `_sable_unpack`, definitions, `SableSprites`, and `SableTransform`; the completion callback retains the original normal-lighting and sky-shadow defaults.

`VeilVertexArrayBridge` requires the exact currently bound Veil program and an approved Vulkan UI/custom-target render scope. It packs the live program uniforms/blocks/samplers with the existing shader bridge and submits either direct indexed or fixed indexed-indirect work through the native array state. The root-owned world/culling integration must open the appropriate scope and call the existing Sable renderer; this module does not weaken the generic world-draw rejection.

## Required mixin registration

The source exists, but the root-owned shared JSON was not updated before the root task became blocked. Register these entries:

- `compatibility.veil.VeilVertexArrayMixins`
- `compatibility.sable.SableStagingBufferMixins`
- `compatibility.sable.SableBucketRenderBufferMixins`
- `compatibility.sable.SableFancyCommandBuilderMixins`
- `compatibility.sable.SableFancySubLevelRenderDispatcherMixins`

The fixed owners and method inventory are captured in `Evidence/sable-custom-vertex-caller-abi.txt`.

## Native GPU fixture registration

The fixture source is present but was not added to the root-owned test CMake file. Register:

- shaders: `tests/shaders/custom_vertex_array.vert` and `.frag`;
- executable: `tests/custom_vertex_array_gpu_test.cpp`, C++23, linked to `Vulkan::Vulkan`;
- test: `mcvr.custom-vertex-array-gpu`, passing the two generated SPIR-V paths and using skip return code 77.

The fixture uses the exact two-binding formats and UINT16 indices. Its indirect command deliberately sets `firstIndex=1`, `vertexOffset=1`, and `firstInstance=1`, so a path that drops any of those fields selects sentinel data or the wrong instance. Acceptance requires exactly the inner 12x12 pixels (144 total) to equal approximately `(51,204,102,255)` and every exterior pixel to remain clear.

## Verification

PASS:

- `compileJava` and `compileTestJava` on the current shared tree.
- `generateJniHeaders` for BufferProxy, ShaderProxy, and the new VertexArrayProxy.
- 27 selected JUnit tests, zero failures/errors/skips:
  - `CustomVertexLayoutTest`: 2;
  - `RadianceVertexArrayTest`: 2;
  - `VeilShaderBridgeTest`: 11, including the actual fixed Sable fancy include selecting the two-binding layout;
  - `PersistentBufferAllocationTest`: 5;
  - `ShaderTranslatorTest`: 3;
  - `VeilMixinCompatibilityTest`: 4.
- Scoped Radiance and MCVR `git diff --check`.
- Fixed Sable 2.0.5 and Veil 4.3.2 JAR caller/ABI audit.

Not verified:

- Native core compilation after the new JNI/layout/range/draw source.
- GPU fixture shader compilation, pipeline creation, indirect draw, or pixels.
- Runtime mixin application, live Sable dynamic-program compilation, resource reload, culling, or a rendered sublevel.
- Root-owned world scope and Sable renderer submission.

Additional production boundary: fixed Veil 4.3.2 `VanillaShaderCompiler.reload(Collection)` still calls `GL.getCapabilities()` before recompiling vanilla dynamic-buffer sources. This module only calls its safe static `getActiveDynamicBuffers` method and replaces Sable's later GL shader introspection. The remaining reload call belongs to the root-owned Veil lifecycle and must be replaced with a captured-source/Vulkan-aware implementation; it must not be reported as complete or changed to an empty success.

## Evidence

- `Evidence/sable-custom-vertex-java-verification.txt`
- `Evidence/sable-custom-vertex-caller-abi.txt`
- `Evidence/sable-custom-vertex-source-freeze.sha256`

No game, GUI, ComputerUse, staging, commit, or push was performed.
