# Raster equivalence and Ponder PT retirement assessment

迁移日期：2026-10-09；原始观察日期：各条目原日期。本次只迁移结构，不将历史结果提升为本轮验证。

## 问题

保留原调查的范围、证据等级与未完成事项。当前实现与批准的产品约束以[项目现状](../project/status-current.md)为索引。

## 方法

原始记录来自 Radiance 迁移前的已提交源码与对应证据。以下保留原文与日期，避免重新解释历史失败。

## 发现

Observed on: 2026-09-24.
Initial assessment status: investigating; retention and retirement changes below were proposed.
Evidence: static source/diff/test inspection only in this assessment; no new build, GPU test,
client launch, screenshot comparison, or performance measurement.

The appended [authorized implementation](#2026-09-24-authorized-implementation-and-simulated-source-comparison)
records subsequent changes and tests without retroactively changing that assessment's evidence.

## Scope and source identity

The question is whether translated Vulkan raster work preserves its original OpenGL observable
behavior, and which shared changes from the Ponder PT development should remain after its archive.
The main world's deliberately different PT lighting is not an OpenGL raster reference image.
The Ponder archive（归档位置：`D:\Workspaces\Artifacts\Radiance\history-archive\Radiance\docs\history\ponder-pt-2026-09-23.md`） remains authoritative for the user's decision
and retained visual failures. This assessment does not reactivate PT or reverse unrelated fixes.

Inspected dirty trees: Radiance HEAD `e4c5bc73c0270e8079ccc30a8ec1fc02dea75fb0` and MCVR HEAD
`8208f305a71d0ffa56e761cd7b62c1b667572cb4`. HEAD alone does not identify their uncommitted contents.
A 31-file targeted exact-byte SHA-256 manifest is retained at the non-portable local path
`D:\Workspaces\Repositories\GitHub\RecRivenVI\Radiance\build\manual-acceptance\evidence\20260923-faces-chunks\raster-parity-20260924-source-manifest.json`.
Its SHA-256 is `1E5E8557192973B7C9FB645719714AFA522DAB93BC1B4590E14305DCEA3E7CAE`.
It is an inspection fingerprint, not a complete build-input snapshot or new acceptance artifact.
Existing dirty code, tests, deployed artifacts and the user's client were left unchanged.

## What is and is not established

The current implementation does not justify a claim of universal raster equivalence. Restoring
Ponder's original producer/camera/transition is narrower than restoring every global render hook.
Three independent requirements must hold: original drawing intent reaches the translator; the
translator preserves state, geometry and shader semantics; the composed observable result agrees
with the reference. Passing only the last layer in a few scenes cannot prove unvisited producers.

Concrete source findings:

| Finding | Evidence and limit |
| --- | --- |
| Original entity shadow work is still suppressed unconditionally | `EntityRenderDispatcherMixins.cancelRenderShadow` cancels without a PT/raster scope check. A raster caller that requests this operation cannot receive its original geometry. This establishes an operation-level difference; it does not establish that every inventory/Ponder preview requests shadows. |
| The new original-lighting scope covers Catnip screens, not all raster producers | `CatnipRasterScreenMixins` is the only production caller of `RasterPreviewScope.enter`. `BlockModelRendererMixins` and `FluidRendererMixins` select their PT override whenever that scope is absent. Non-Catnip previews invoking those producers therefore need caller-level verification. Do not infer a visible error in every GUI item: item rendering can use a different producer. |
| External shader translation has a bounded expression treatment | `ShaderTranslator.translateExternalStages` rewrites literal `gl_FragCoord.xy`, and wraps position output for Y/depth conversion. This does not by itself establish equivalent handling of `gl_FragCoord.y`, whole-vector reads, alternate swizzles or all shader language constructs. These are test gaps, not a claim that a particular installed shader is failing. |
| Final GUI alpha has an intentional coverage role | `wrapFragmentCoverage` can force alpha to one for an unblended draw on the default GUI target; custom targets are excluded. This can be correct for FG coverage while differing from original stored alpha. Compare observable RGB, later destination-alpha consumers and readback separately; internal coverage must not silently replace an observable original alpha contract. |
| Unsupported calls are not automatically compatible | `DirectFaceState` and `RenderCaptureContract` explicitly reject unsupported capabilities/scopes. This is safer than illegal GL fallback but does not count as equivalence or successful translation. |

The inspected `PonderDefaultContractTest` checks Mixin registration, and `RasterPreviewScopeTest`
checks scope restoration. `ShaderCoverageGpuTest` compiles a translated fixture and delegates to a
Vulkan harness. MCVR framebuffer, tessellation and custom-array tests exercise useful Vulkan
behavior, but are not execution of the original OpenGL renderer beside the translated renderer.
No paired OpenGL/Vulkan reference runner was found in the inspected test paths. Historical passing
counts and the Catnip client smoke must retain their original, narrower evidence labels.

## Proposed equivalence evidence

### Reference and observation boundary

Use an isolated OpenGL client with the same Minecraft 1.21.1, NeoForge 21.1.250, Create 6.0.10,
Ponder 1.0.82 and the actual pinned companion mods/resources, without Radiance/MCVR. Preserve
the original mod configuration; do not add Sodium or another renderer solely for convenience
unless that is the selected reference configuration. Run the Vulkan candidate separately.
Do not create an OpenGL context inside Radiance's `GLFW_NO_API` client.

Keep GPU/driver, physical viewport, GUI scale, resource hashes, scene time, camera, animations,
lighting and draw order controlled. Compare raster outputs with identical incoming color/depth
attachments or a controlled background. A different PT world behind the GUI is not evidence of
incorrect raster translation. Compare real frames first; FG-generated dynamics are a separate gate.

Khronos documents different clip-depth conventions and fragment-coordinate origins:
[Vulkan depth guide](https://docs.vulkan.org/guide/latest/depth.html) and
[GLSL variable conventions](https://docs.vulkan.org/glsl/latest/chapters/variables.html).
Normalize only declared API coordinate/format conventions. Do not auto-align, blur, recolor or
rescale results to hide a translation error. Numerical/raster-edge tolerances must be declared
per case, with exact masks/state assertions where appropriate; global image similarity is not a
substitute for missing geometry, incorrect alpha, wrong depth or leaking scissor boundaries.

### Three complementary gates

1. **Producer coverage.** Record original draw intents and dispositions for item/entity previews,
   Ponder, Catnip configuration, Simulated diagrams, Veil custom targets and relevant overlays.
   Include requested shadow/lightmap/foil/outline work, direct state calls, framebuffer operations
   and entry points canceled before buffer creation. Reuse the external audit mod and existing
   capture ledger. Unknown/untranslated intents fail the exercised case; counts alone do not
   prove semantic equivalence or cover scenes never executed.
2. **Identical-input backend comparison.** Feed the same mesh, indices, uniforms, shader source,
   textures and state sequence through the original GL path and the production Vulkan translation
   path. Capture RGBA plus relevant depth/stencil/readback outputs. A fixture that reimplements a
   second Vulkan renderer instead of using the production translator is insufficient.
3. **Deterministic scene replay.** Compare real inventory items/entities, Ponder dwell/transition,
   moving fluids, glint, stencil-clipped Create configuration and diagrams at several GUI scales.
   Exercise target nesting, resize and resource reload, including state after returning to the
   main world. Dynamic behavior needs frame sequences, not one static image. User inspection
   complements machine differences; neither substitutes for the other.

Minimum backend cases: perspective/orthographic clipping and depth ranges; mirrored winding and
cull modes; depth compare/write; front/back stencil and masked clears; scissor/viewport including
partial offscreen rectangles; fractional/separate RGB-alpha blending and disabled blending;
logic-op/inversion; linear/sRGB formats and sampling; cutout/discard, texture filtering/mips,
lightmaps and foil; custom FBO color/depth/stencil and readback; uniforms, attributes and shader
built-ins; ordered UI/blur/inversion with draws both before and after the effect.

Report per version and exercised combination: equivalent within a declared bound, confirmed
difference, unsupported, or untested. An open mod ecosystem cannot be certified by changing the
label on a finite screenshot set. No nonzero tolerance may excuse a deterministic semantic loss.

## Disposition of Ponder-era shared changes

This table classifies dependencies, not just files or historical batch names. The old audit commit
`b0173ab855d4f693a0a62216ec07197b184490b5` already contained some Ponder infrastructure and
shader-pack reuse. A diff against it is corroboration, not an exhaustive pre-Ponder baseline.

| Change and current source | Main-world consequence | Recommendation |
| --- | --- | --- |
| Ponder capture, same-frame collection, common TLAS, owner-tagged transition geometry; `PonderPathTracer`, native `UiPathTracingProxy` | Only Ponder/UI PT consumers need this scene model | Keep archived with its tests/evidence; do not reactivate on normal GUI draws. |
| UI pixel budget/crop projection, composite depth coverage, geometry/BLAS cache and material-only UI rebuild | Savings and behavior belong to UI scenes; no evidence these caches accelerate normal world sections | Archive the active consumers. Preserve helpers as reusable code, without claiming an applied main-world optimization. |
| `uiPtCommandBuffer` allocation/begin/end/submission and readback handling in `render_framework.cpp` | An extra buffer remains in ordinary frames even with no UI PT caller | Candidate for lazy allocation/recording/submission only when an active UI PT batch exists. This is an isolation change, not permission to remove readback/fatal guards or every neighboring barrier. Cost has not been measured here. |
| `SceneRecordingScope`, alternate renderer lookups, recording-context count and offscreen layout branches | Main uses a single view; scope/context leakage would affect unrelated world resources | Isolate behind the archived UI execution boundary. Inert general extent/context APIs may remain; do not rebuild the whole framework merely to remove a dormant branch. Prove default-main behavior with regressions before simplifying. |
| Cross-view dispatch-image aliasing, per-view DLSS/NRD/ShaderPack slots and history maps | With `viewCount == 1`, the cross-view allocation saving does not occur; indexing remains more complex | Keep the multi-view specialization dormant/optional. Preserve normal frames-in-flight isolation and actual SDK cleanup; never collapse history slots merely because Ponder is off. |
| `uiSceneOwner`, high material bits and primary-ray owner predicate in both PT shaders | No intended main-world material rule; accidental nonzero owner/state would change visibility | Move/gate the UI-only specialization with archived PT. Keep existing first-person, single-sided, shadow and priority rules distinct; preserve ABI layout or update both producers/consumers together. No current main-world regression is proven by this branch alone. |
| Common perspective/orthographic ray construction and valid priority temporal inputs | General camera/input correctness, not inherently an FPS improvement | Retain, with primary/priority/background consistency tests and main-world temporal/visual acceptance. |
| Unchanged binding avoids new descriptor generation; immutable texture binding snapshots | Avoids needless generation work and protects recorded draws from later rebinding | Retain. The first is an optimization by reduced work; snapshot ownership is primarily a correctness fix and may retain extra resources while frames are in flight. |
| Java upload generations, bounded texture names, independent GPU retirement | Protects reload, animated textures and normal world/UI ownership even without Ponder | Retain as correctness/capacity fixes. Integer reuse alone is not sufficient, and no FPS gain is asserted. |
| Last-upload retirement, reusable bounded staging buffers, replacement sized by actual used bytes | Removes retained final batches and repeated large allocation after a one-off atlas upload | Retain as independent resource-management improvement; quantify memory/allocation/frame-time tradeoffs before claiming measured speedup. |
| Stable execution-buffer address with ordered per-pass updates; post-color synchronization; fatal stage guards | Used by main Advanced/Vanilla rendering and failure cleanup | Retain as independent correctness fixes, not Ponder remnants. They do not prove the historical device-loss cause. |
| FG fractional coverage, inversion/blur treatment and corresponding inputs | Independent FG contract, also used without Ponder | Retain and validate against GL real-frame composition separately. Ponder archive neither proves nor cancels FG equivalence. |
| Pipeline-layout keepalive, existing shader/cache reuse, section scheduling, face semantics and Sable fixes | Independent or pre-existing work | Preserve their own contracts/evidence. Do not relabel them as newly proven Ponder performance gains or discard by file-level rollback. |

Main-world single-view evidence: `WorldPipeline::init` takes view count one without an active
scene scope, and its image-alias branches require a later view. `ShaderPack::textureSlot` maps
shared resources to the view slot; DLSS and NRD allocate their per-view histories from that count.
This demonstrates absence of the specific multi-view saving for the main world, not zero overhead
or proof that all main-world temporal behavior is unchanged. The current framework unconditionally
includes the UI PT command buffer in normal submission; its existence is not evidence that it
contains active Ponder tracing after the archive.

## Proposed change order and acceptance

First establish the GL reference and smallest producer/state comparison cases, addressing the
confirmed scope gaps without changing main-world PT semantics. Then remove only active no-consumer
UI PT work, retaining dormant implementation and historical evidence. Do not roll back whole
`pipeline.cpp`, shader modules or texture files: independent safety repairs share those files.

For retirement candidates, compare current archived-Ponder build against the isolated-specialization
build with Ponder closed and then with raster Ponder/configuration open. Keep camera/world, presets,
resolution, mods, driver, warmup and duration equal. Record real CPU/GPU frame p50/p95/p99,
pipeline/image/descriptor/command counts, upload/BLAS counts, measured allocator memory and total
process/device memory separately. Zero UI PT pipeline/SDK-view creation and no UI PT command work
are useful structural gates; they cannot alone establish a visible or performance improvement.
Keep normal PT temporal reconstruction and FG settings fixed for each paired comparison.

No product retirement, parity harness implementation or new performance experiment was executed
in this assessment. Existing Ponder raster visual acceptance, GPU fault uncertainty, lichen pixel
loss and binary redistribution gates remain separate open items.

## 2026-09-24 authorized implementation and Simulated source comparison

This section supersedes the assessment-only execution boundary above. It does not turn the
assessment or the following bounded tests into universal OpenGL/Vulkan equivalence evidence.
Reference: Simulated 1.3.2 bundled with Aeronautics 1.3.2, Sable 2.0.5, Create 6.0.10,
Ponder 1.0.82 and Veil 4.3.2 on Minecraft 1.21.1 / NeoForge 21.1.250. Decompiled methods,
original shader/PNG assets, jar identity and bytecode are retained in
`build/manual-acceptance/evidence/20260924-raster-simulated/reference/`.

### Implemented production changes

| Area | Evidence and change | Evidence boundary |
| --- | --- | --- |
| Raster light/shadow scope | `RasterPreviewScope` recognizes actual GUI, camera-overlay and world-raster capture scopes; a nested world/dimension stage does not inherit an outer raster scope. Original entity shadow rasterization is canceled only on PT paths. Existing block/fluid light consumers use this scope. | Scope behavior tests; actual diagram, Create configuration and raster Ponder calls. This is not a comparison of all block/entity renderers with GL. |
| External fragment coordinates | The old translation replaced only the literal `gl_FragCoord.xy`; `.y` and whole-vector uses disagreed. Translate the complete built-in and supply target height for every spelling. Preserve z/w. | Production Java translation, glslc and real Vulkan GPU test cover whole vector, y and xy together. |
| Ponder-only command work | UI PT command storage is now lazy and begins/ends/submits only when the archived executor actually requests it. Preserve readback splitting, fences and normal world/overlay ordering. | Native behavioral test covers 500 ordinary frames with no UI allocation/recording, repeated requests and begin failure. No measured main-world FPS claim. |
| Diagram fading | The old implementation returned only a binary Bayer decision, losing the original eight-column palette gradient levels. Restore selected levels divided by seven; use original bottom-up logical coordinates for fade masks and directional outline sampling. | GPU sweep of 257 coverages x 32 x 32 pixels; reference shader and actual PNG rank comparison. Full-resolution presentation remains intentional; low-resolution upscaling is not restored. |
| Spring stress | Keep the original stress RGBA through diagram rasterization; stop forcing it to white. PT spring stress and entity hurt overlays call one surface-albedo blend helper before material evaluation. | Native GPU surface-blend test plus both shader-pack compilations. Stress is not opacity or additional emission. Actual stressed-spring/hurt visual comparison remains pending. |
| Staff stage | Only the callback whose pinned body invokes `PhysicsStaffRenderHandler.renderSelectionBox` bypasses Veil's deferred world-raster replay. Capture locks and the original animated LineOutline producer into the active default-world sink; custom FBO/non-world calls keep their original path. | Real bytecode target/static-signature check and actual native build ledger. This is not blanket translation of every Veil stage. |
| Lock icon | Original lock quads use full-bright lightmap, no depth test, no culling and the position/color/texture/lightmap shader. Preserve 0.1 cutout, ordinary PBR emission 1, world participation and camera-hidden `PRIORITY_ONLY` visibility; add the appropriate priority text hit group in both PT packs. | Source contract and runtime lock draw/build. Mirror, indirect-light, occlusion and perceived emission acceptance remain pending. |
| Staff/plunger attachment | Both original producers draw one world rope per active link and select a local first-person endpoint or a third-person hand endpoint. Their original first-person reprojection/FOV compensation was applied to geometry already captured with Radiance's hand FOV transform. Convert the captured anchor with the same inverse view as the held-item TLAS and add the actual camera origin. | Matrix/FOV/orientation behavior tests and world smoke. Third-person construction is unchanged. Fast motion, hand choice and exact visual attachment remain pending; the producer still caches the hand-rendered anchor. |

### What remains intentionally retained or unproven

Ponder PT collection/TLAS/history/budget/output code remains archived. Original raster Ponder is
active. The independent texture generation/descriptor snapshots, upload retirement, bounded
staging, pass-buffer safety, temporal guides, resource cleanup and layout keepalive remain.
Multi-view resource/history specialization is dormant for the normal single-view world; no
wholesale rollback or assertion of zero residual overhead was made.

The diagram implementation is **not fully original-equivalent**. In particular, decorative
greeble placement currently tests projected non-air block bounds, whereas original `addGreebles`
tests nonzero alpha in the postprocessed framebuffer. Partial models, cutout/faded pixels,
entities and block-entity geometry can differ. Restoring this exactly requires a correctly timed
diagram attachment/readback or equivalent coverage result, not another guessed AABB rule.
The current full-resolution diagram is also drawn each frame instead of reproducing the old
12-Hz cached FBO refresh. Palette, fade and camera fixes do not validate all force arrows,
sticky-note UI, connected structures or third-party geometry. Keep these observable differences
separate from the user's explicit removal of pixelated enlargement.

Staff inventory/held geometry uses the existing item PT capture; hover outlines use the Catnip
world sink; original END_ROD particles use particle PT. `staffOverlay` is declared/registered in
the pinned Simulated jar but no producer was found there; this is not a claim about unknown
add-ons. Beam shape, wave animation and original segment endpoints are retained. Original
third-person body placement has not been replaced with a second first-person rope.

### Evidence and acceptance

`20260924-raster-simulated` owns source manifests including necessary untracked inputs, build
logs, matched native symbols, shader checks, deployment manifests, isolated runs and captures.
The first runtime candidate failed Mixin preparation because the new beam callback omitted
`static`; it was corrected and covered by the pinned-bytecode regression. A subsequent probe
used an incorrect item ID, then an incorrect world-space beam target instead of plot coordinates;
these were diagnostic-fixture defects, recorded separately from product failures and visual passes.
See the development ledger for final product identity and observed cases.

Manual comparison still covers inventory entities/shadows/glint; diagrams at multiple GUI scales,
rotation, zoom, fade and sticky notes; stressed springs and hurt/white-flash surfaces under normal
PT lighting; lock occlusion/reflection/emission; moving staff/plunger endpoints in both hands and
camera modes. No original-GL replay or quantitative matched performance A/B was executed here.
Existing Veil unsupported-program reports, historical GPU-loss uncertainty and redistribution
licensing remain explicit limits; no dangerous fault injection, production-world operation or Git
history change is part of this work.

Final lock coverage correction: the original shader discards alpha below (not equal to) 0.1 and
the original layer does not alpha-blend surviving fragments. A text-only priority hit group would
retain fractional edge transparency. Both priority any-hit and closest-hit now use the material's
coverage rule for ordinary surfaces, while text keeps fractional alpha. GPU tests check the exact
0.1 boundary, the complete coverage sweep and unchanged fractional text. This replaces the initial
per-hit-group threshold-only implementation; it is a source correction, not retrospective visual
approval of the earlier candidate.

## 2026-09-24: Diagram semantic re-audit and correction

Status: investigating; static findings as distinguished below. No product modification, new
build, automated/GPU test, client run or visual acceptance in this re-audit.
Sources: clean Radiance `6e9b97a53049fad833e673da647ac517efde5fe3`, MCVR
`ead8d47d80ad2bc9cf81740c29f0ec230b61f92f`; Simulated 1.3.2 JAR SHA-256
`FDF9D250996A084B52FCED3A5B0089E879A5CAC05DC7BCF295AC1F4EBE5E2BFF`, Sable 2.0.5,
Create 6.0.10 and Veil 4.3.2. Actual Minecraft 1.21.1 / NeoForge **21.1.251** patched sources
were checked for buffer submission and section compilation. Earlier runtime evidence above used
21.1.250 and is not a new run of this snapshot.

This corrects the earlier assessment that only greeble occupancy and cached redraw remained.
The selection-only audit omitted renderer preferences, submission order and mutable resource
contents. Calling an original producer does not prove its draw consumes the original data at
the original time. Only removal of pixelated enlargement is an approved semantic exception.

### Coverage and original rendering preferences

| Inspected boundary | Original contract and current evidence boundary |
| --- | --- |
| `DiagramConfig`, main/note screen, entity creation, request/data packets | Camera/note scope, force groups/merging and root physics data are separate from scene membership. No invented spring/rope visibility toggle is justified. |
| `SimpleSubLevelGroupRenderer` selection | Recursively intersecting spatial bounds select sublevels, not traversal of every physical joint/rope. A connected endpoint does not automatically add another structure. |
| Original section/single-block path and current `renderBlocks` | Chunked rendering consumes compiled layers originally; the replacement rebuilds block/fluid models. Single-block dispatch is retained. Order and extension inputs differ below. |
| Original/current BE dispatch and patched `SectionCompiler` | Compiled local BEs, single-block BE and Flywheel embedding fallback are distinct. Offscreen renderers enter the global list instead of the compiled local list; the diagram does not directly invoke the world's global BE loop. |
| Simulated visualization mixins and embedding fallback | Original diagrams suppress normal Flywheel visualization and use ordinary BE rendering for embedded visuals. Do not append the world instance list or duplicate mechanisms. Other Sable backends are not exhaustively covered. |
| Original diagram LightTexture and Sable dispatcher mixins | Blocks use a custom lightmap multiplied by **0.65**; BEs/entities use its curve multiplied by **1.0**. This emphasizes details, not full-bright/PBR emission. Input block-light indices still matter. The section mixin also changes the first `setupDynamicEffects` call's `onSubLevel` argument while `RENDERING_SIMPLE`; that does not disable every shadow/fog/sky effect. |
| `SpringRenderer`, RenderType and shader | Only a controller with a partner emits the spring ribbon, after inherited smart-BE rendering. Stress RGBA is pulsing surface recoloring, not opacity/emission. Texture cutout is a separate rule. |
| `RopeStrandRenderer`, connector and winch | Strand ownership/usable points, attached/virtual state for knot/coil, kinetic parts and per-segment light all matter. Structure ropes use the solid block path, not the custom plunger rope shader. Connector/winch request global/offscreen rendering; this does not itself include them in every diagram list. |
| Producer-internal overlays | A rope can emit its hover outline; winch filtering and inherited smart-BE rendering can add behavior UI. No general world-overlay pass exists, but saying there are no interaction overlays is wrong. Reachability depends on selected producers and original state. |
| Entity predicate, plunger renderer/current anchor adapter | Original `renderChain` allows players. Membership is containing or tracking/vehicle sublevel after a bounded query, not all nearby entities. Plunger rope is a distinct producer. Secondary-camera anchor behavior needs a matched original draw before declaring a defect. The diagram entity draws paper, not a recursive preview. |
| Note scope, arrows/COM, world-only passes | Scope changes framing, not membership into a clipped 3D mini-world. Forces use original config/requested root data, not all visible structures summed together. No automatic sky/weather/global particle pass belongs to the group draw. |
| Sampler/NativeImage/TextureProxy through native upload/UI draw | Real Sampler2 is bound; content lifetime across diagram stages is not preserved. Binding identity is not immutable image content. |
| Original post/assets, current native post; main/note composition/cache | Default shader fixtures do not prove pack overrides, fractional placement, clipping, occupancy, caching or full GL parity. |
| Original-method recovery wrapper versus active custom entry | The existing recovery helper does not wrap the replacement currently drawing diagrams. |

Ignored static evidence is retained at the non-portable repository location
`build/research/20260924-diagram-reaudit/`: selected original classes/decompilation, three patched
Minecraft source files and `EVIDENCE.json`. Original assets/bytecode remain at
`build/manual-acceptance/evidence/20260924-raster-simulated/reference/`. Decompilation is static
inspection, not an executed renderer test.

### Confirmed differences

**D1: stage lightmap contents are not retained for their respective draws.**
`SimulatedDiagramCompatibility.renderScene` calls both original generators and `endBatch()`
between them, then restores the ordinary map. All mutate/upload one DynamicTexture. The actual
bound Sampler2 is used by `ShaderProxy`/`VeilShaderBridge`; white is a missing-binding fallback,
so a blanket white-lightmap diagnosis is withdrawn.

`NativeImageMixins`/`TextureProxy` reaches `Textures::queueUpload`, copying into staging and
queuing writes to the same image. `Framework::submitCommand` performs queued uploads before
recorded overlay draws. Binding snapshots preserve identity/lifetime, not separate 0.65/1.0/
restored content versions. A Java buffer flush is not a GPU draw between these writes. The
stage sampling contract is therefore lost. This does not establish which final pixel value
wins in every batch, an overlapping-copy validation result or a cause of historical GPU loss.

Repair requires immutable stage contents or explicitly ordered upload/draw resources, including
restoration for later consumers. White maps, arbitrary final-color multipliers and routine global
idle are not equivalent fixes. Test distinct lightmaps across both stages and a later ordinary
consumer through the real translator and attachment readback.

**D2: first-encounter order replaces original layer submission.**
Original `renderGroup` loops `RenderType.chunkBufferLayers()` and flushes each layer after all
structures. Current chunked rebuilding visits structures/blocks first; `DiagramBufferSource`
adds types to a linked fixed-buffer map and flushes afterward. Actual 1.21.1 `endBatch()` iterates
that map's key order. Translucent can precede a later encountered opaque/cutout layer. The BE
phase also lacks vanilla's original fixed/shared ordering. Visible severity needs overlap tests;
the order difference is confirmed. Preserve independent allocators needed by nested renderers
(the prior Offroad consumer invalidation), while restoring original phase/layer order.

**D3: fractional placement and clipping change.**
Original note paper/image share the fractional slide pose. The replacement rounds image GUI x
before conversion to physical pixels; paper keeps its float pose. Native `beginDiagram` clamps
the rectangle and uses the smaller viewport, while post uniforms keep the requested rectangle.
Offscreen content can be rescaled instead of cropped. An 88-pixel view at x=750 in an 800-pixel
window becomes a 50-pixel viewport, unlike an 88-pixel viewport with a 50-pixel scissor. Preserve
the actual GUI transform/unclipped viewport and intersect only the visible region. This is a
source/mathematical finding, not a new screenshot comparison.

**D4: final-alpha occupancy is approximated by block bounds.**
Original `addGreebles` reads nonzero postprocessed alpha after rendering contents. Current
`intersectsRenderedGeometry` projects non-air unit bounds, missing holes, partial blocks, fades,
BE-only details and entity silhouettes. Both false and missed occupancy are possible. Preserve
initialization timing and use final coverage or a proven equivalent without per-frame readback.

**D5: cached redraw cadence differs.**
Main/note FBOs refresh around 12 Hz with separate timers and original forced-update conditions.
Current replacements rebuild/draw every frame. Full resolution does not require changing camera
sampling or doing this extra work. Restore cached refresh/invalidation while retaining the
non-pixelated output, including orientation, scope, fade, resize, reload and close.

**D6: resource-pack post overrides are bypassed (conditional input).**
Original Veil resolves its program, palette and dither through resources. Native
`diagram_post.glsl` embeds the two eight-color palettes and `diagram_math.glsl` generates a
fixed Bayer pattern. Matching bundled defaults does not honor replacements. This does not say
the default palette is wrong. Applicable resources/programs must be consumed or the specific
unsupported override reported; default-only fixtures cannot establish pack equivalence.

**D7: compiled-section extension inputs are omitted (conditional producer).**
Original chunked rendering consumes compiled layers. The actual NeoForge compiler also runs
supplied `AddSectionGeometryEvent` renderers. Current `renderBlocks` only reconstructs models/
fluids and does not invoke these producers. Add-on section geometry therefore has a different
input path. No installed add-on's missing diagram was runtime-confirmed here. Preserve compiled
inputs or equivalent producer coverage, not unrelated world geometry added to compensate.

**D8: failure cleanup does not cover the active replacement.**
`SimulatedSimpleSubLevelGroupMixins` wraps original `renderGroup`; current screen/note rendering
calls custom `renderScene`. Its embedding camera override resets only after renderer success;
buffer close can prevent later matrix/projection/lightmap restoration; flush/post failures can
replace the original error. These are static exception-path gaps, not a reproduced crash.
Use structured cleanup at the actual entry, retain the first error and attach cleanup failures.
Existing recovery-helper tests alone do not exercise this path.

### Bounded unresolved questions

- Current chunked rebuilding bypasses original Sable section normal-lighting, sky-light scale
  and fog setup. Compare actual uniforms and block/fluid results before choosing a correction;
  `RENDERING_SIMPLE = true` does not prove the dispatcher hook executed.
- Check controller/owner inside and outside the selected chain, single-block versus chunked,
  and Flywheel/fallback. Predicates remain, but live BE populations and duplicate suppression
  were not enumerated. Do not infer an omission merely from a name or offscreen flag.
- World PT's plunger/staff attachment fix must not be undone from a secondary-camera hypothesis.
  Compare the actual original diagram producer before changing its camera/global state contract.
- Original FBO refresh avoids Veil perspective rendering; direct replacement lacks the guard.
  Nested-GUI reachability, inherited GUI depth and post scissor need bounded cases before
  claiming normal-use failures.
- Every entity shader, virtual mechanism, transition, resource pack and alternate Sable backend
  has not been tested. Producer presence, order and final visual equality are separate claims.

### Revised scope and acceptance

This is larger than two small tweaks but bounded to diagram producers, raster state/resources
and direct composition. Preserve phase inputs (D1/D2), placement/coverage/cache (D3/D4/D5),
conditional inputs and actual-entry cleanup (D6/D7/D8). Resolve Sable-state questions with
evidence before changing behavior. No global PT policy change or Ponder PT reactivation follows.

Acceptance must exercise the actual entry: block/mechanism lighting, opposite controller/owner
placement, attachment/virtual/hover states, transparent overlap, partial/cutout/entity/faded
coverage, note sliding/crop/GUI scales, cache/reload, asset overrides, section extensions and
renderer exceptions with subsequent state restoration. Compare the same original GL producer,
state and scene. Only pixelated enlargement may differ intentionally. Historical bounded tests
and user observations remain evidence of their actual cases, neither revoked nor expanded by
this static re-audit. No new matched original-GL run has been performed.

## 2026-09-24: Diagram presentation decision and implementation acceptance

Status: implemented; build-verified; bounded runtime comparison; visual acceptance pending.
The user explicitly retires the original diagram's approximately 12-Hz redraw cap and low-resolution
pixel enlargement. Render each displayed frame at the actual UI physical-pixel dimensions; retain
paper colors, outlines and original fade. The clarification was "remove the pixel grid, preserve
paper colors and original fade". The implementation keeps the original palette and fade thresholds,
with dither sampled at physical pixels rather than magnifying coarse texels; continuous-color
replacement was not requested. No separate low pixel-budget limit is authorized. This supersedes
D5's proposed restoration and the earlier narrower exception.

All other producer, ownership, ordering, lighting, resource-pack, interaction and cleanup semantics
remain original-derived. Source-level equivalence is insufficient: exercise actual cutout discard,
fractional alpha, blend factors, depth writes/tests, ordering and final post/GUI composition.
Separate default-asset matched-image evidence from resource overrides and human visual acceptance.

### Implementation and measured corrections

`SimulatedDiagramCompatibility` now wraps the original draw/group/post/GUI chain rather than
recreating its producer selection. Original spring controller, rope owner/attachment, virtual and
hover predicates therefore execute in their original renderer, as do force selection and entity
membership. This restores the semantic source; it is not visual acceptance of every predicate.

- D1: capture original 0.65/1.0 lightmap generators into independent immutable stage textures;
  preserve the ordinary map for later consumers. No white map or guessed color multiplier.
- D2/D7: use original layer iteration and section compiler, including additional section geometry;
  keep a diagram-local raster cache keyed by published section generation, origin and resource epoch.
- D3/D4: original fractional GUI composition remains active; framebuffer size follows physical UI
  dimensions. Decoration occupancy reads final alpha once during the original decoration pass.
- D6: original resource-resolved Veil post program, palette and dither execute through translation.
  Only logical paper-mask/outline dimensions are separated from physical sampling dimensions.
- D8: the active original draw now runs inside existing recovery, with explicit raster compiler
  cache/sublevel state cleanup on exceptions. The previous custom native diagram post is no longer
  this UI's active path; native product code is unchanged.

Two further differences were found through actual GL/Vulkan image comparison and corrected:
translucent indices must preserve Sable's published **compile-time** sorting, not use the diagram
camera; raster previews must honor original AO settings while world PT continues to disable AO.
`RasterCompiledSection` carries the immutable sorting policy and `RasterPreviewScope` restores the
original AO query only in raster scope. No change to world PT culling or material classification.

### Runtime evidence and limits

Non-portable evidence: Radiance `run/diagram-semantics-20260924/`, especially `gl-final`,
`vk-members` and `evidence/PIXEL_COMPARISON-members.json`. At 1280x720 / GUI scale 1, an original
assembled 71-block structure includes opaque iron, persistent cutout leaves, cobwebs, overlapping
red/blue glass, a slab and a chest. The final Vulkan package completed initial/90-degree views,
note activation, GUI scale 3, resource reload and normal save/exit (PID 75476, 128.106 seconds total).
Its recorded chain contains one structure and no eligible entities. The GL reference exited
normally (PID 21844, 67.433 seconds); runtime duration is **not** a performance comparison.

At the matched 256x192 resolution, raw material RGBA is byte-identical in all three captures.
Final paper alpha is identical; final RGB differs at 19 initial and 81 rotated pixels (maximum
channel difference 59). GL reports 24-bit depth; visible depth differences reach 4.47e-8. Strict
neighbor-depth comparisons in the original outline shader are a candidate explanation, not a
proved diagnosis. Do not claim full final-pixel equivalence. At scale 3, the actual Vulkan target
is 767x576 and both raw/final captures are byte-identical before and after reload.

Earlier `vk-final` / `vk-final-controlled` runs contain small, time-varying extra regions (138 / 57
initial raw pixels). Disabling random ticks/mob spawning and attempting fixture-entity cleanup did
not explain them. Added chain/entity inventory found no such regions in `vk-members`; that later
success does not retrospectively explain the earlier differences. Preserve those runs as unmatched,
unattributed evidence, not discarded failures or proved harmless entities.

Pending: spring/rope controller-owner placements, attachment/virtual/hover variants, populated note
scope/slide/fade, arbitrary post/palette overrides, actual section-extension geometry, alternate
Sable/Flywheel backends and live renderer-exception recovery. Note activation alone did not populate
a note scope. A bounded fixture cannot prove all UI3D raster paths equivalent.

## 2026-09-24: Deferred spring and plunger visual reports

Status: deferred by user; user-observed symptoms, causes unconfirmed.
Evidence: manual feedback after the preceding diagram deployment; no new capture, log/artifact
verification or original-GL reproduction performed for this report. The preceding deployment is
context, not an independently verified identity of the process in which these symptoms occurred.

| ID | User observation | Follow-up and acceptance boundary |
| --- | --- | --- |
| S1 | When a spring twists, apparent interior texture/geometry shows through and the spring becomes black. | Reproduce the same deformation in original and translated rendering; distinguish self-intersection/inner surfaces, winding/culling, UV/cutout and lighting. These are investigation candidates, not diagnosed causes. Preserve intended spring deformation/stress behavior; validate twisted and untwisted views without masking the symptom by forcing double-sided rendering or emission. The exact affected rendering context has not been isolated in this feedback. |
| S2 | In the structure diagram, the spring appears to lose its textured cutout and becomes solid black. | Compare the actual spring producer, texture/alpha discard, lighting and pre/post-paper targets against the original at the same state. Require the original texture holes and paper presentation to survive; ordinary block cutout fixture results do not cover this spring shader. |
| S3 | The attached plunger itself renders, but its rope endpoint is displaced in the structure diagram. | Compare original secondary-camera/local-to-world transforms and actual attachment endpoints, including rotation/scale and holder visibility. Require the rope to join the intended plunger/attachment point in diagram views. Do not treat this as a missing entity, infer a separate first-person rope design, or revert world-camera anchor fixes without evidence. |

Keep all three reports separate until a shared cause is demonstrated. They are not visual acceptance
of the affected mechanism paths, nor evidence that every diagram cutout/rope is broken. The user
requested recording for later work only: no repair, new diagnostic run, build or deployment follows
from this entry. Physical-resolution presentation, paper colors/fade and archived Ponder PT decisions
remain unchanged.

## 2026-09-27: Spring material provenance and S2 sampler translation

Status: investigating; S2 sampler translation repair implemented and build/automated-verified in
the Radiance worktree above `a39db18f68b95ddea7d67e5c25e48aa2d0c3e3d5`, but the bounded
Vulkan spring image failed its coverage gate and a later frame encountered real device loss. S1
world-PT and torsion symptoms remain undiagnosed. This section advances the separate 2026-09-24
S1/S2 reports; it does not convert either into visual acceptance.

Source: the bundled Simulated 1.3.2 JAR SHA-256
`FDF9D250996A084B52FCED3A5B0089E879A5CAC05DC7BCF295AC1F4EBE5E2BFF`, its
`SpringRenderer`, `SimRenderTypes`, `spring.vsh/.fsh`, body PNGs and torsion-spring model; the
fixed Veil 4.3.2 program wrapper; and Minecraft 1.21.1/NeoForge 21.1.251 source generated by
the paired Gradle build. Source snapshots are retained under
`build/manual-acceptance/evidence/20260924-raster-simulated/reference/` and
`build/research/20260924-diagram-reaudit/` (non-portable build evidence).

The flexible span is drawn only by a controller with a paired spring. `SpringRenderer` chooses
the size-specific `textures/block/spring/*spring.png`, constructs a spline, distributes a twist
over its segments and emits two sets of four faces with per-vertex UV, normal, light and stress
RGBA. The body PNGs have genuine binary alpha holes (the medium body is 50% alpha-zero); their
opaque colors are gray rather than black. The separate `*_spring_end.png` textures belong to
anchor models. `torsion_spring_inside.png` is an explicit, sometimes black, texture on the
separate torsion-spring block model; that material alone does not explain a black flexible span.

The original spring fragment shader samples `Sampler0`, discards alpha below 0.1, then applies
normal lighting, a stress-color RGB mix controlled by stress alpha, lightmap and fog. Its
RenderType binds the body texture at shader slot 0, enables the lightmap at slot 2, disables
transparency, and retains the Minecraft composite defaults of back-face culling, LEQUAL depth
and color/depth writes. `createCompositeState(true)` affects outlines; it does not alter culling.
Minecraft's `ShaderInstance.setDefaultUniforms` assigns `Sampler0..11` from shader texture slots
before draw, and Veil's wrapper forwards those names to `setTexture`. Fixed Veil 4.3.2 bytecode
shows `ShaderProgramImpl.setDefaultUniforms` unconditionally delegating to that wrapper method;
direct Veil post/effect callers of the generic method therefore receive the same default sampler
assignments, not just `ShaderInstance` wrapper draws. `Wrapper.apply` calls `bindSamplers(0)`
afterward, allowing a later Veil named binding to override the defaults. This call order is the
reason the repair captures named texture IDs during default setup rather than relying only on
the `SamplerN` spelling or a later ambient texture-unit lookup.

In the V2 Vulkan diagram bridge, `VeilShaderBridge.setDefaultUniforms` omitted that named
sampler capture. The implicit fallback instead numbered uniforms in source declaration order.
The actual spring VSH declares `Sampler1`, `Sampler2` before its FSH declares `Sampler0`, so the
old bridge assigned `Sampler0` to slot 2 and `Sampler2` to slot 1. This is a concrete
translation mismatch capable of replacing body texture/cutout with lightmap or overlay data.
The repair captures declared `Sampler0..11` from `RenderSystem.getShaderTexture(i)` at draw setup,
before Veil's explicit named bindings can override them. Its fallback also respects exact
`SamplerN` names and explicit GLSL `layout(binding=...)`, including an explicit declaration in a
later stage; conflicting explicit bindings and unsupported sampler arrays fail at compilation.
No texture, alpha threshold, stress, culling, light or paper-presentation semantic was changed.
This source-level correction does not by itself explain or close the observed S2 image failure.

| Producer and view | Original GL reference | Candidate Vulkan diagram | World PT |
| --- | --- | --- | --- |
| Flexible spring, untwisted, stress off | Original producer/pair passed and visible cutout coil was captured at 256x192; no user visual acceptance. | Pair survived to capture; Java spring draw API returned with 288 requested indices and `Sampler0=190`/`Sampler2=215`, but 767x576 raw FBO had no coil hit in the center corridor. True device loss later makes GPU completion/descriptor content unresolved. **S2 fails.** | Separate PBR vertex capture uses 0.1 cutout and stress as surface RGB mix; matched world image pending. |
| Flexible spring, untwisted, stress on | Original shader mix contract established statically; matched stressed image pending. | Not exercised. | Not exercised. |
| Flexible spring, twisted, stress off/on | Original spline/winding established statically; matched pose image pending. | Not exercised; rotating the paper is not twisting the spring. | Does not use `VeilShaderBridge`; twist, face-hit and lighting diagnosis pending. |
| Torsion-spring block model, rotated, stress not applicable | Explicit inside-face texture established statically; matched view pending. | Ordinary block/BE route; no spring-shader sampler inference. | Ordinary model route; dark inside-face pixels may be original material, pending matched view. |

The existing non-spring diagram block-cutout fixture cannot prove flexible-spring shader parity.
An opt-in Audit fixture now places a real same-sublevel controller/partner pair through the
original public block-entity methods, checks the client pair before opening and at each capture,
then captures raw and post-paper targets. It is a deterministic translation fixture, not the
player-linked two-sublevel physics workflow. The initial position is untwisted and stress-free.

### Bounded run evidence and failure boundary

Non-portable evidence: `run/spring-s2-20260927/` with the
`evidence/SPRING_S2_EVIDENCE.json` comparison, retained GL/Vulkan raw/paper FBOs, and
`evidence/diagnostic-artifacts/` source/JAR/core/PDB/hash manifest. GL PID 112016 exited
normally with the coil present. In the corresponding interior coil corridor, GL had 703/880
alpha-positive pixels and depth hits. The visible, non-iconified, actually unfocused Vulkan
diagnostic PID 111912 sampled 2742 frames at 1280x720 with no OS foreground ownership; those
samples are not all world frames. Its FBO was 767x576 by the intentional physical-resolution
policy. The scaled center corridor had 0/7920 alpha-positive pixels and no depth hits.

The client controller/partner remained valid at every Vulkan capture. Sixteen bounded trace
records show calls for the packaged `simulated:spring/spring` shader with 288 requested indices;
the Java `ShaderProxy.draw` call returned. These records exclude an uncalled spring producer but
do **not** prove native `vkCmdDraw*` recording or GPU execution. Java selected `Sampler0` texture
ID 190 and `Sampler2` lightmap ID 215; neither ID alone proves native descriptor content or sampled
texels. Full raw-FBO readback returned, but it does not isolate spring command completion.

At 10:51:08 the first explicit client failure was `vkWaitForFences(frame)` returning Vulkan
`-4` (`VK_ERROR_DEVICE_LOST`) during acquisition after a resource reload; shutdown reported the
retained device loss. Windows `nvlddmkm` Event 153 was recorded at 10:50:41, 10:51:06 and
10:51:08, the first near the logged spring calls. Temporal proximity is not a root-cause proof.
The server saved its worlds after the exception; the process exited `-1`, not normally. A prior
Vulkan attempt failed before world entry from a launcher-induced GLFW 0x0 window; another reached
FBO capture but had a zero-size main screenshot target and exited abnormally. They are retained
separately from the valid GL reference and the real-loss diagnostic run. No further GPU run was
started after the device loss.

## 2026-09-27: Source-grounded flexible-spring retranslation candidate

Status: implementation and bounded lower-layer validation in progress. The preceding failed S2
FBO and later device-loss records remain failures, not visual acceptance of this candidate. The
original user request supersedes the earlier patch-by-patch spring adapter approach. The pinned
starting dirty worktree is frozen in non-portable
`run/spring-retranslation-20260927/evidence/BASELINE.zip` and `BASELINE.json` before replacement.
No Prism deployment or full diagram client run has occurred for this candidate.

The Simulated 1.3.2 JAR `FDF9D250996A084B52FCED3A5B0089E879A5CAC05DC7BCF295AC1F4EBE5E2BFF`
remains the producer authority. `SpringRenderer.renderSafe` requires a controller with a paired
partner; it generates the spline, frame transport, distributed twist, two reverse-cyclic sets of
four quads per segment, UV lanes, source shading normals, light UV and stress RGBA. The two sets
can share corners yet triangulate a twisted non-planar quad on different diagonals. They cannot
be deduplicated or rewound. Stress alpha is an RGB overlay weight; texture alpha alone determines
the `alpha < 0.1` discard, so alpha exactly `0.1` survives. Original spring RenderType uses
`SPRING_FORMAT` (`Stress` in BLOCK's Color byte slot), 32-byte QUADS, body PNG, Veil spring shader,
back-face culling, LEQUAL and color/depth writes. `TextureStateShard(texture,false,false)` requests
nearest/no mip; address mode and any actual resource metadata remain live texture state, not a
hard-coded sampler in this adapter. The separate torsion spring uses the baked partial model and
`RenderType.solid()` with its explicit dark inside texture; it is excluded from the ribbon contract.

| Prior spring-specific path | Replacement and invariant |
| --- | --- |
| `SimulatedVertexCompatibility.SpringPBRVertexConsumer` subclass and texture-prefix matching | `SpringDrawContract.from` accepts the original memoized spring factory and byte ABI, independent of its live texture resource ID. `SpringWorldLowering` creates a fresh full-width PBR consumer per draw; no rigid/pose cache can retain a prior spline. |
| Spring entry in `VeilSimulatedShaderAdapter.CONSUMERS` | `SpringRasterLowering` owns the original loc0..4 input ABI and forwards the live Veil light directions. Generic Veil defaults still capture the original RenderSystem `Sampler0..11`, lightmap, ColorModulator, fog and per-draw matrices. The raster draw checks its **uploaded MeshData** format/stride before `ShaderProxy.draw`; a 128-byte world mesh is rejected. |
| Implicit spring material inferred from generic vertex alpha/emission context | Explicit world `MaterialPolicy`: alpha mode 9 (`>=0.1`), stress surface RGB mix, no inherited particle/outline emission floor or transmission override, no rigid capture, and original vertex normal as a BRDF base. Genuine resource-pack PBR texture emission remains available. |

`SpringShaderSourceGuard` checks the live Veil program definition, both stage IDs, the exact
`veil:light`/`veil:fog` include sets, empty external binding/dependency sets, and SHA-256 of the
five original JSON/VSH/FSH/include resources. It also compares all functions and declarations
after parsing the trusted source with the pinned GLSL parser against Veil's actual preprocessed
stage source. A post-resource modification of `gl_Position`, UV, output RGB/alpha or sampler
layout is rejected, even if IDs/includes are unchanged. PNG replacements and an alternate body
texture passed through the original `SimRenderTypes.spring(texture)` factory remain live.
An unrecognized shader variant fails explicitly; the old translated `f092...` cache key is only
an artifact identity and is not a whitelist. The generic Veil sampler-default repair remains.

World lowering retains original positions, winding, UV and vertex normal bytes in the 128-byte
PBR source. Legacy `useNorm` 0/1 retains its meaning; this policy writes exactly `useNorm==2`,
which native CPU and GPU converters lower to the previously unused packed-material bit 7 without
changing 100/128-byte source layouts or `MaterialVertex`. The compact 100-byte producer rejects
this policy. Both PT packs use the interpolated source normal transformed once by the inverse
transpose as the BRDF normal base, with finite/zero and hemisphere fallback; legal PBR normal maps
apply once on that base. Triangle geometry and `gl_HitKind` remain authoritative for culling,
shadow, ray offset and medium boundaries. Mirrors and reverse-wound coincident source quads are
therefore not reinterpreted from shading normals.

Advanced PT keeps `cache3.xyz` as its original geometric normal, `cache4` texture/atlas and
`cache5` stress/color bytes unchanged. Only bit-7 surfaces, whose source has neither glint nor
UV1 overlay, tag a union in three unused RGBA32F auxiliary lanes (`cache6.x`, `cache6.w`,
`cache7.x`) containing the already-oriented, pre-normal-map BRDF base XYZ. The decoder validates
finite/nonzero data but does not normalize or repeat the near-tangent hemisphere threshold after
FP32 storage. `cache6.yz` remains the genuine emissive-overlay texture ID; all untagged cache
lanes retain their previous bytes. Java ignores UV1 absent from the source SPRING_FORMAT and
rejects glint wrapping; CPU native packing rejects `useNorm==2` with overlay/glint. The GPU
converter's bit-7 path is valid for that trusted Java producer; it is not a general rejection
mechanism for arbitrary forged native source buffers.

Bounded current-source evidence: `SpringProducerGeometryTest` invokes the actual pinned
`SpringRenderer.renderSegment` bytecode rather than a formula transcription. Straight, `7.4°`
twist and mirror cases preserve both reversed quad sets, their different UV lanes, normals and
stress. The actual `SimRenderTypes.spring` through Minecraft `MultiBufferSource.BufferSource`
produces 16 vertices of 32-byte MeshData for one emission; its matching world factory produces
128-byte vertices with the same twisted/mirrored positions, UV and normals. This tests the
segment and buffer ABI, not the full controller/partner world gate. Three native CPU contracts
cover index/facing and `useNorm==2`; 11 actual vanilla/advanced pack entry shaders compile under
their default configuration macros. Two bounded headless GPU tests passed: `entity-conversion-gpu`
checks direct/deferred source conversion, and `spring-material-gpu` checks a ten-case helper/cache
roundtrip with triangle-cross oracle, mirror/nonuniform transform, valid/invalid normal,
near-cutoff FP32, 0.099/0.100/0.101 cutout and independent stress tint. The latter's nonflat
normal-map projection is a fixture formula, not the production `sampleSurfaceState`. GPU command
submission, fence and host readback complete within those fixtures. Logs and shader outputs are
retained under `run/spring-retranslation-20260927/validation/`.

No current result proves final production `ShaderProxy`/diagram draw recording, descriptor image
contents, completed spring FBO cutout, or world S1 appearance. The next bounded gate needs one
isolated mixed-floor/anchor/spring production draw with original resource state, valid extent,
native command/submission/fence evidence and positive spring alpha/depth; a full client run waits
for review because the previous diagram path experienced device loss. Do not present source,
CPU, shader compilation or the helper GPU fixtures as a solved S1/S2 image.

## 2026-09-27: Spring retranslation — uniform-layout cause and bounded runtime gates

Status: the generic shader-layout defect is repaired and the source-grounded flexible-spring
translation passes one-variable GPU, paired GL/Vulkan menu raster, actual diagram initial-FBO,
and one ordinary-world PT producer/screenshot gate. These are bounded cases, not exhaustive
twist/stress, resource-override, torsion or user visual acceptance. S3 plunger was not touched.
No Prism instance was modified by this implementation work. The earlier Prism and isolated
`VK_ERROR_DEVICE_LOST` events retain unknown GPU causes; this layout result does not prove that
either device loss was caused by the spring shader.

The decisive defect was in the shared generated uniform block, beyond the previously repaired
named sampler defaults. `ShaderField` serialized a padded `vec3 Light1_Direction` at offset 272
with a 16-byte CPU field, then wrote `Sampler0Index` at 288. GLSL `std140` legally puts the
following scalar in the vec3's fourth lane. The old generated spring VSH **and** FSH SPIR-V
therefore declared `Sampler0Index` at 284, `ColorModulator` at 288, `FogStart` at 304 and
`FogEnd` at 308, while the actual Java draw bytes used 288/304/320/324. The first index read
zero padding, sampled the wrong texture and could discard every fragment. Earlier analysis
incorrectly called SPIR-V member 9's offset 288 the sampler offset; member **8** is the
sampler at 284. The complete retained disassembly at
`run/spring-retranslation-20260927/validation/spring-vk6-vulkan14.{vert,frag}.spvasm`
corrects that record.

`ShaderTranslator.buildHeader` now writes `layout(offset = ShaderField.offset())` on every
generated `Uniforms` member for both ordinary and external shader translation, with monotonic,
non-overlapping std140 alignment checks. It keeps the established CPU buffer layout rather than
repacking all Java/Veil uniforms. The fixed spring SPIR-V reflects 288/304/320/324/336 for
sampler/color/fog-start/fog-end/fog-color in both stages. Standard JUnit reflection tests compile
synthetic vec3-followed-by-float/int/sampler and mat3/array cases and inspect `OpMemberDecorate`;
the pinned packaged Simulated/Veil spring VSH/FSH test compares every reflected UBO member with
the actual analyzed `ShaderField` list. Six actual packaged Veil programs still compile.

The bounded A/B held the valid independent Vulkan fixture executable, the three original
32-byte `MeshData` vertex streams, QUADS indices, actual 352-byte draw uniforms, body PNG and
white lightmap byte-identical. Only the 13 explicit UBO member offsets in each generated stage
changed. The old SPIR-V produced no alpha-positive pixels, while the corrected SPIR-V produced
spring ROI coverage 552/550/550 after submit, fence and host readback. The 1,724 spring pixels'
coverage mask and RGBA were byte-identical to the GL reference after excluding the shared floor
and anchors; depth differed by at most `5.96e-8` at 618 pixels. The failed first fixture
executable was an unrelated test-harness null Volk function-pointer access violation before a
valid draw; it was fixed by `volkInitialize/volkLoadInstance/volkLoadDevice`, preserved separately
and never counted as spring or device-loss evidence. The RenderDoc-enabled client attempt failed
before its window/FBO at `vkCreateDevice` because the enabled-layer/Streamline condition reported
`VK_NV_optical_flow` unavailable (`VkResult=-7`). No RenderDoc capture or GPU-loss conclusion came
from that attempt; it was not repeated or bypassed by disabling SDK features.

Production candidate `run/spring-retranslation-20260927/candidate-menu-9/` embedded the new
shader-layout translation and an independent native overlay-buffer usage correction. In the
isolated, muted, visible/non-iconified menu run `menu-vk-9`, PID 123020, the **actual** original
spring producer supplied three 32-vertex/48-index draws, including straight unstressed,
twisted/stressed and mirrored/twisted/stressed segments. Native recorded three indexed spring
draws with back cull/CCW, LEQUAL depth write, 256x192 viewport and the new translated shader
key `simulated_spring_spring-88810d468485c9afd74a7cb2`. The subsequent color/depth FBO
readbacks completed and the client exited normally. The full 256x192 RGBA buffer SHA-256
`A462FDBAE5945034AC1A253F80FB7E047AB8CFCA64AF304B17559E0C808BD605` matches the
separate original GL run **byte for byte**; both have 7,356 covered pixels and the same
552/550/550 spring ROI counts. Depth coverage matches; 618 depth floats differ only within
`5.96e-8`. This proves these fixed raster inputs and presentation, not arbitrary diagrams or
all real deformations.

The first short world/diagram run used the original controller/partner pair in a Sable plot.
Its actual diagram opened, and the 767x576 initial Vulkan FBO shows a spring with many alpha
holes; a sample line across the coil had 40 coverage transitions. The source pair, client pair,
native 288-index spring draw, FBO readback, ordinary save and exit all completed without a
recorded device loss. That world screenshot had pitch -90 despite a server pitch request and was
invalid for S1. A corrected camera-only run verified yaw/pitch on both client and render camera
but still showed no ordinary-world spring because `SubLevelAssemblyHelper.assembleBlocks` had
moved the source into a remote plot without establishing a world-visible contraption. Neither
early screenshot is world-PT spring evidence; both remain retained as fixture failures/boundaries.

The final isolated `world-direct-vk-3` fixture instead placed the two original Simulated spring
block entities directly in the ordinary loaded world through their public controller, desired
length and partner-link APIs. It did not create a substitute mesh or a Sable plot. Client
controller/partner lookup passed. Default-off Audit observers counted 7,068 calls to the actual
`SpringRenderer.renderSegment`, 589 `SpringWorldLowering` consumer creations and 589 completed
full 128-byte PBR batches (113,088 vertices). Every finished vertex carried source-normal mode
2, low texture-cutout mode 9 and stress surface-mix mode 2; the body texture ID was stable at
222. The validated render camera was yaw 180°, pitch 18° at (11.5,130.5,19.5). The retained
1280x720 screenshot at
`run/spring-retranslation-20260927/world-direct-vk-3/client/screenshots/spring-main-world.png`
visibly contains the spring and its open texture pattern between anchors in world PT. The
process exited 0 after world/sublevel saves and native SDK shutdown, with no logged device loss.
This scripted same-world pair is valid for rendering, but is not the player's Sable/Aeronautics
connection workflow or a high-stress twisted pose. The separate torsion spring still uses its
ordinary baked model and explicitly dark inside texture; no ribbon-specific correction or
artificial brightening was applied to it.

The native overlay buffer repair is a separate, proven reuse legality bug, not an established
cause of the spring image or historical GPU loss. `Buffers::resetFrame` reuses numeric transient
IDs. Previously `initializeBuffer` checked capacity alone, so a same-capacity slot could keep
`VK_BUFFER_USAGE_VERTEX_BUFFER_BIT` when the next frame bound it as an index buffer. It now
recreates a buffer when the requested usage is absent, unions old/new legal usage flags to avoid
alternating reallocation, and retains the old in-flight buffer through the existing frame
retainer. The `mcvr.overlay-buffer-reuse` CPU behavior test covers same-capacity role changes,
subsequent reuse, growth and old-resource lifetime. No scheduler or arena redesign followed.

The final product main JAR SHA-256 is
`29EC184B8D83378B430B315A7C8DF122DA3959BB5A904A62AB6A3B570B804A1C`; it embeds the
Release core DLL `7EA31C6151C3ECF82C371CA33A19C185484998827A6E8D16979383EA31DD9E46`.
The final ordinary/default-off Audit JAR SHA-256 is
`746CCD5941CC27DDD3D7ABEC11CB01E0BE74B56EE97B26FCCF0845B998B568FB`;
optional RenderDoc compilation is OFF and no RenderDoc DLL is bundled. The Release core build
emitted no matching PDB; the older RelWithDebInfo PDB
must not be associated with this DLL. The actual embedded and world-loaded default
`vanilla-pt.zip` hash is `59E7CEA5ED6FFBCD081C8083F17A56B83DC850BD49AA61F1EC63D38BE4A773D1`;
the embedded Advanced zip is `E7AABB84B89BE9463AE28E9251B4FE84404DC48A67D8F2EC43EC97855641C3CE`.
Earlier standalone candidate zip copies have different ZIP container hashes but the same
110/145 entry payloads; do not relabel those old containers as the final embedded bytes.

Verification scope: Java full suite 241 tests/78 suites and Audit Java 21 tests/7 suites,
all with no failure/error/skip; `verifyDistributedJar`, ordinary `verifyAuditJar`, Audit
native CTest 2/2, native
`mcvr.overlay-buffer-reuse`, native packing/material contracts, 11 compiled vanilla/advanced
entry shaders, the earlier two bounded native GPU tests, one-variable raster GPU A/B, menu GL/VK
FBO comparison, actual diagram initial FBO and ordinary-world producer/PT screenshot. Full
user-observed S1 deformation/black-inside judgment, torsion matched view, resource-pack shader
variants beyond the pinned guard and the old unrelated GPU-loss cause remain open. S3 remains
separate.

## 2026-09-27: User feedback and spring versus hurt surface coloration

Status: user visually accepted spring cutout and stress coloration **inside the structure
diagram**. Twisting/black-through appearance failed user visual acceptance. The authoritative
feedback entry（归档位置：`D:\Workspaces\Artifacts\Radiance\history-archive\Radiance\docs\DEVELOPMENT_LEDGER.md#2026-09-27-user-spring-diagram-acceptance-and-failed-twisting-appearance`）
pins the deployed pair and preserves the earlier tests' narrower scope. This follow-up is
static source/model investigation, not another repair, build, GPU run or visual acceptance.

### Different source signals, shared PT albedo operation

Pinned sources are Simulated 1.3.2 (`FDF9D250996A084B52FCED3A5B0089E879A5CAC05DC7BCF295AC1F4EBE5E2BFF`),
Minecraft 1.21.1 / NeoForge 21.1.251 source/resource artifacts, and the current Radiance/MCVR
worktree. Extracted `OverlayTexture`, `LivingEntityRenderer`, entity-cutout shader and SimColors
bytecode evidence are retained at the non-portable
`D:\Workspaces\Artifacts\RadianceSpringRetranslation\20260927\surface-tint\`.

| Property | Flexible spring | Standard living-entity hurt overlay |
| --- | --- | --- |
| Trigger | `SpringRenderer.getStressColor`: endpoint distance approaching the snapping distance, with a time pulse | `LivingEntityRenderer.getOverlayCoords`: `hurtTime > 0 || deathTime > 0` selects the red overlay row |
| Color | `SimColors.STRESSED_RED`, RGB `(235, 50, 48)` / `#EB3230` | Generated `OverlayTexture` red texel, RGB `(255, 0, 0)` |
| Strength | Ramps from zero and pulses, nominally up to 0.3 before 8-bit quantization | Red row stores alpha `178/255`; source shader uses its inverse, so recoloring weight is `77/255`, about 30.2%, while selected |
| Transport | Per-vertex `Stress` color/alpha in the source 32-byte format; PT uses surface-mix color data | UV1 addresses the separate 16x16 overlay texture; layers independently decide whether to supply this overlay |
| Scope | The procedurally rendered connection span; this function does not tint the static anchor models | The ordinary living-entity model path and layers that opt into the relevant overlay, not necessarily every attachment/effect |

The source spring warning is a **distance/extension warning**, not a direct measurement of the
physics solver's force or stress. Let `D = desiredLength * 4 + 2` and
`L = interpolatedRenderLength - 0.75`; the warning begins at `L + 0.7 * (D - L)`.
Above that point its unclipped strength is the remaining-distance fraction times 0.3,
then multiplied by `0.25 + 0.75 * (sin((playerTicks + partialTicks) / 3) * 0.5 + 0.5)`.
The stored byte uses integer truncation. Hurt red is a state/timer selection, not a red strength
proportional to damage amount; white-flash overlay selection is a separate coordinate rule.

Original raster spring and entity shaders both recolor RGB after vertex color/directional
lighting, before the lightmap multiplication and fog calculation. The spring uses
`mix(color.rgb, stress.rgb, stress.a)`, whereas the entity shader uses
`mix(overlay.rgb, color.rgb, overlay.a)`. The opposite alpha convention is intentional.

Current world PT deliberately expresses both as the same surface operation in
`MCVR/src/shader/util/surface_overlay.glsl`: `mix(base, overlay, coverage)`.
Both Vanilla PT and Advanced call it before LabPBR material evaluation. Spring coverage is
its stress alpha; entity-overlay coverage is `1 - sampledOverlayAlpha`. Geometry opacity,
cutout, transmission and source face rules remain independent. Tinting itself does not add
emission or extra geometry; genuine PBR emission already defined by the material is still
evaluated normally. Thus these two inputs are not identical encodings, but their world-PT
surface-color semantics are already shared. Diagram raster output retains the original shader
lighting/composition rather than substituting the world's lighting result.

### Actual spring geometry, not the apparent coil silhouette

The flexible spring has two separate baked endpoint blocks and a procedural connection span.
The medium anchor is one cuboid `[4,0,4]` to `[12,4,12]` in model pixels; small and large
anchors are respectively 6x4x6 and 10x4x10. Their `*_spring_end.png` assets are not the span's
texture. `SpringRenderer` extends Create's `SmartBlockEntityRenderer`; the superclass adds
filter/link visual helpers, not a kinetic shaft. The paired controller emits the span itself.

`generateSpline` samples a cubic Bezier with `clamp(ceil(endpointDistance), 5, 8)` spans.
Each span has a transported square cross-section with full width 6/16, 8/16 or 10/16 blocks
for the three sizes. `renderSegment` emits the four longitudinal side quads: a zero-thickness,
open-ended square sleeve, not a swept round wire. Every span calls this method twice; the
second call reverses the endpoint tangents/up vectors, giving the same corner sets, reverse
cyclic ordering and a different U lane with negated V. It is not a smaller, separate inner
wall. Before culling/cutout this is 8 quads / 32 submitted vertices / 16 triangles per span,
or 80-128 triangles for the connection, plus the separate endpoint models.

The apparent turns and gaps are supplied by the alpha-cutout body PNG and its longitudinal UV
mapping. UV progression is scaled by `(interpolatedRenderLength - 0.75) / sampledSplineLength`;
stretching bends/stretches this textured sleeve rather than rebuilding physical wire turns.
The medium PNG has 128 opaque and 128 transparent texels. The body's textures are gray and do
not use the separate torsion-inside image. Registered static `spring/middle` assets are not
the runtime spline producer; an asset name alone is not evidence that it supplies this span.

Twisting can make a source quad nonplanar. Earlier bounded geometry evidence shows that the
reverse emission can choose the opposite diagonal under the source QUADS expansion, so equal
corner sets need not mean identical triangulated surfaces after deformation. This is a concrete
topology candidate to inspect for the rejected pose, not proof of the user's black-through cause.
The actual failing pose/indices/UV samples, self-shadowing and shading inputs still need to be
correlated; no normal/cull/UV/brightness workaround or visual rejection dismissal follows here.

Torsion springs are a different producer: a six-element rigid partial model plus separate
kinetic shaft halves, with facing and interpolated rotation applied to the partial. The
renderer and Flywheel visual reuse that partial rather than the flexible spline. Its authored
inside texture is assigned to up/down inside faces and includes opaque black pixels; the baked
base also has an inside-textured top. This fact does not establish that an observed twisted
black-through artifact is correct, or that the user's failure is limited to this model.

Source/asset hashes and the superclass bytecode check are retained in the non-portable
`D:\Workspaces\Artifacts\RadianceSpringRetranslation\20260927\geometry\spring-composition-torsion-addendum.md`.
Current saved Prism options select `vanilla` and `mod_resources`; the SPBR ZIP present in its
resource-pack folder is not selected in that saved file. These are default-asset/static facts,
not a new live resolved-resource or failed-pose capture.

### Actual Create encased-fan geometry

The pinned Create 6.0.10 Chinese language key maps `block.create.encased_fan` to the user's
"鼓风机". The Aeronautics bundle's nested Simulated has a Ponder sentence mentioning it;
Simulated's separate `velocity_sensor/fan_blades` asset is not this block. Create JAR SHA-256:
`EF87FE5709F1BA1F5B8BB20A2925B5AFB4669E178FD6D8BF10C167759EEFE37A`.
The inspected fixed Flywheel dependency is 1.0.6
(`31DDA15C205EB596D3B3449EF03F6AF7363A6CD35B3DA4BFE916B304F9E5337E`).

| Part | Asset and authored geometry | Motion |
| --- | --- | --- |
| Housing | `create:block/encased_fan/block`: 14 quads, comprising 6 outside cube faces, 4 inward walls, and two pairs of up/down faces | Baked blockstate-facing transform; no rotor animation |
| Rotor hub | First element in `encased_fan/propeller`: cuboid `[6,6,1.2]` to `[10,10,8]`, 6 faces | Rotates with the propeller |
| Apparent four blades | Second element in `propeller`: **only its north face**, at z=4, rotated 22.5 degrees around z; `fan_blades.png` alpha draws all four blade silhouettes | The one quad rotates; no blade deformation |
| Rear half-shaft | `shaft_half`: cuboid `[6,6,8]` to `[10,10,16]`, 6 faces | Ordinary shaft speed, separate from rotor speed |

The propeller element's JSON `from/to` box spans z=4..12, but it only defines the north face;
the other five faces do not exist. A bounding box with depth is therefore not proof of blade
thickness. The renderer has 14 housing quads and 13 rotating-part quads before neighbor/face
culling, not four modeled solid blades. The grid/bar appearance on housing textures also does
not correspond to individually modeled grille bars.

`EncasedFanRenderer.renderSafe` uses `SHAFT_HALF` and `ENCASED_FAN_INNER` through
`RenderType.cutoutMipped()`. `FanVisual` instantiates the same partial models as two
`ROTATING` instances; Flywheel does not synthesize extra blades or a more volumetric rotor.
Rotor speed is shaft speed times five, with nonzero magnitude clamped to 80..1280; geometry is
rigidly rotated about the block-facing axis. This is distinct from the spring's changing spline.

`propeller.json` declares `minecraft:cutout`. The fixed Flywheel model-material mapping uses
`CUTOUT_BLOCK` / one-tenth cutoff without mipmapping and default backface culling enabled;
the ordinary renderer explicitly chooses cutout-mipped. Thus source backends share geometry
but should not be called universally material/pixel-identical without runtime checks. There is
only one authored blade face and no no-cull choice in these inspected defaults. The absence
of a JSON `cullface` property concerns neighbor-face omission, not a guarantee of double-sided
raster or PT hits. Current Radiance's Flywheel capture reads the mesh/material and ROTATING
instance parameters; no fresh actual-backend/baked-model/visual run was performed here.

Asset provenance: `encased_fan/block.json`
`8657A092C486D6107DAD549B87E5B40F297542D78D508049B279272AD5F76F7E`,
`encased_fan/propeller.json`
`6C954D68D396EBECB65AACC1C43C6E0752B2E07D9B40F81E9772831D6B50C141`, and
`shaft_half.json` `BBD1D6226933A4B29B365A449E87ECFF1F851965F82F802C0800747B530A0576`.
Extracted pinned assets, class bytecode/decompilation and hashes are in the non-portable
`D:\Workspaces\Artifacts\RadianceSpringRetranslation\20260927\fan\` tree.

These are rendering meshes, not an inventory of collision or physical constraints. Full PT
participation traces the supplied faces and texture coverage; it does not automatically turn
the apparent spring turns or fan-blade silhouettes into metal volumes. This distinction motivates
a matched failing-pose investigation, but does not waive the user's rejected spring appearance.

## 2026-09-27: Material recoloring standard and focused source recheck

Status: the user adopted material-input recoloring as the common implementation standard.
The authoritative decision entry（归档位置：`D:\Workspaces\Artifacts\Radiance\history-archive\Radiance\docs\DEVELOPMENT_LEDGER.md#2026-09-27-standard-material-tint-and-global-face-policy-reassessment`）
preserves source-specific multiply/mix semantics, independent opacity and genuine emission.
This is not a decision to normalize all overlay strengths or animate every tint like a spring.

The flexible spring retains its original producer callback and per-draw stress bytes: threshold,
distance ratio, pulse phase, partial-tick interpolation and integer truncation remain upstream
inputs. Its maximum stored stress coverage is 76/255, not an exact floating-point 0.3. Standard
living hurt/death overlay retains the original UV1 selection and inverse alpha, 77/255 coverage
while active. Both PT shader families operate in the existing base-color encoding before
material color-space conversion. World illumination intentionally comes from PT; matching these
source tint parameters does not promise identical final RGB to original voxel-lit raster.

Torsion springs are outside this stress-color producer: their original renderer interpolates
rigid partial rotation through `RenderType.solid()` and has no flexible-spring stress callback.
Do not invent a torque-red signal for them merely to make the two spring models alike.

A confirmed **additional-layer** omission was repaired during this recheck. Minecraft's
`entity_translucent_emissive` RenderType enables `OVERLAY`, and its original shader applies UV1
hurt/white-flash recoloring after vertex color. Both PT packs mapped it to `transparent_only.rchit`
without consuming that overlay. Those two shaders now sample the existing WorldUBO overlay
texture only when `hasOverlay`, then call `applySurfaceMaterialColor` for source-ordered vertex
multiply/stress mix followed by inverse-alpha UV1 mix. Existing opacity, emission factors,
history and face rules are unchanged. This does not mean every emissive entity layer necessarily
supplies a non-default hurt overlay; the missing consumer now honors it when supplied.

This turn's evidence: 24 targeted Java tests passed, including original `renderSegment` stress
byte transport, PBR/UV1 and persistent-model appearance changes. The changing 0/38/76 stress
inputs test transport, **not** the original time pulse in a running world. The shared production
material-color function passed one actual Vulkan compute submit/fence/readback fixture, including
hurt, white, no-overlay and multiply-before-mix cases. Both actual changed closest-hit files
compiled and passed SPIR-V validation; their descriptor branch was inspected, not GPU-executed
as a full hit group. Evidence: `run/surface-tint-20260927/EVIDENCE.md` (local, ignored).
No new packaged artifact, deployment or visual acceptance is implied by this source correction.

### Fan translation recheck: no special case, but generic material gaps

No encased-fan-specific branch was found in Radiance production Java/resources or MCVR
core/shaders. This conclusion includes tracing the generic paths, not only a name search:
`EntityProxy.queueBlockEntitiesRebuild` yields to a Flywheel-owned visual; `RadianceFlywheelEngine`
captures the original meshes/materials; `FlywheelInstanceAdapter.ROTATING` validates the original
52-byte Create instance layout; native instancing receives its transforms and material flags.
The original `FanVisual` still selects the two partials, facing, speed-times-five clamp and
per-part state. The ordinary BER still selects `cutoutMipped`; no fan mesh thickening, special
back-face override, extra blade producer or replacement animation was found. Ponder uses its
original raster route; archived Ponder PT has not been reactivated.

However, **no special case is not proof of full material equivalence**. A direct
producer-consumer check found two generic Flywheel mapping limits:

- `FlywheelMaterialData.capture` maps every `OPAQUE` material with a non-off cutout shader to
  alpha mode 1. Native `alpha_mode.glsl` gives mode 1 a 0.5 threshold; mode 9 is 0.1. The pinned
  fan's Flywheel `CUTOUT_BLOCK`/`ONE_TENTH` source requires 0.1. `cutout=` in the material key
  is carried as identity/label data; no native parser restores the threshold. This is a
  confirmed generic threshold discrepancy, not a fan-name exception.
- Material `blur`/`mipmap` values are likewise present only in that key. The actual sampler
  comes from the shared texture-ID sampler table; the current model submission does not
  establish a per-material sampler choice. Thus those source properties are not faithfully
  represented for two materials sharing a texture ID with different filtering requirements.

The default `fan_blades.png` has only alpha 0 and 255, so direct unfiltered samples classify
identically under 0.1 and 0.5. Filtered/minified edge samples or replacement textures can expose
the mismatch. No live backend, sampler, matched fan image or visible regression was captured
this turn; do not claim the default fan visibly fails solely from the threshold difference.
Conversely, saving the source key does not make the untranslated semantics correct. These
findings remain an explicit generic Flywheel material follow-up, outside this turn's tint-only
product correction. Preserve original ordinary-BER versus Flywheel material differences when
addressing them; do not force both source backends to use the same guessed threshold.

Evidence anchors: `FlywheelMaterialData.java:31-65`, `FlywheelInstanceAdapter.java:19-22,50-77`,
`RadianceFlywheelEngine.ModelState.capture`, `EntityProxy.queueBlockEntitiesRebuild`, native
`Instancing::uploadModelMesh`, `Textures::samplers` and `src/shader/util/alpha_mode.glsl`.
Pinned Create/Flywheel model and shader sources remain in the previously recorded fan artifact
directory. No product fan/instancing behavior or Prism settings were changed in this audit.

## 结论

本次迁移不实施研究中的提案，也不重新判定视觉、性能或设备丢失根因。旧台账与闭合审计保留在仓库外历史存档。
