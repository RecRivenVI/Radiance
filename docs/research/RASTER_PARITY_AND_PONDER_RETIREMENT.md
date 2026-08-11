# Raster equivalence and Ponder PT retirement assessment

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
The [Ponder archive](../history/ponder-pt-2026-09-23.md) remains authoritative for the user's decision
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
