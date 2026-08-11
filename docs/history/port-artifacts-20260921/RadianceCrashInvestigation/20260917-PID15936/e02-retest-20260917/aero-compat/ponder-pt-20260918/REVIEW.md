# Ponder independent PT prototype — 2026-09-18

## Scope
User requested a first implementation because entering Ponder currently crashes. Deploy for manual acceptance; do not launch the instance automatically. No commit or staging.

Workspaces:
- D:\Workspaces\Repositories\GitHub\RecRivenVI\Radiance
- D:\Workspaces\Repositories\GitHub\RecRivenVI\MCVR
Deployment: E:\Minecraft\PrismLauncherDev\instances\Radiance Test\minecraft\mods\Radiance-0.1.5-alpha-neoforge-1.21.1.jar

## Implementation
- Wrap PonderScene.renderScene; execute its original animation/entity/block-entity traversal into a PBR quad collector. Triangulate captured quads without submitting them to the main-world mesh sink/TLAS.
- CPU builds a median BVH per scene invocation. An independent Vulkan compute path tracer writes an RGBA image; GUI compositing preserves the surrounding Ponder interface.
- Invert projection * modelview for primary rays. Captured positions already contain Ponder's scene pose transform.
- Preview quality: window aspect, maximum width 640, 8 samples/pixel/frame, at most 4 bounces. Diffuse cosine sampling, environment illumination, vertex emission, cutout, stochastic coverage, simplified dielectric reflection/refraction with total internal reflection.
- Descriptor tables, geometry buffers and output are retained until frame completion. Pipeline is cached on UIModule; existing ComputePipeline layout-token lifetime support is used. Texture uploads and compute/copy/sampling barriers are recorded before UI compositing.
- UIRenderHelper.init no longer leaves its framebuffer null: create the original CustomRenderTarget through the existing Vulkan framebuffer bridge. Historical report showed this null target in a screen transition, but the user's newest crash has not been reproduced or attributed in this turn.
- First three non-empty scenes log `Ponder PT dispatch` with triangle count and image dimensions. This proves CPU dispatch recording only, not GPU correctness.

## Exact files
Radiance:
- src/main/java/com/radiance/compatibility/ponder/PonderPathTracer.java (new)
- src/main/java/com/radiance/mixins/compatibility/ponder/PonderScenePathTracingMixins.java (new)
- src/main/java/com/radiance/mixins/compatibility/ponder/PonderUIRenderHelperMixins.java
- src/main/resources/radiance.mixins.json
MCVR:
- src/core/middleware/ponder_path_tracer.cpp (new)
- src/shader/preview/ponder.comp (new)
- src/core/render/modules/ui_module.hpp
Generated native/shader runtime resources are installed by prepareRuntime.

## Explicit limitations
This is an independent preview PT prototype, not a migration of the existing main-world ray-tracing pipeline. It does NOT yet reuse full main-world PBR/material auxiliary textures, NRD, DLSS, temporal history or hardware TLAS traversal. No full visual parity claim.
- Non-quad primitives retain the original raster fallback; UI labels, scene controls and other GUI elements stay on their original path.
- Ponder's light-coordinate-based section fade/brightness, glint, text shader modes, additive and other specialized layer semantics are not fully replicated.
- Glass uses a fixed refractive index 1.5; no medium stack or volumetric absorption model.
- BVH and buffers rebuild per scene invocation; performance and allocation pressure need runtime acceptance. Two scenes during transitions invoke this twice.
- Stochastic noise is expected without denoising or temporal accumulation.
- Actual entry, rendering, transitions, exit to world and absence of crashes remain pending user acceptance. Build success does not establish any of these.

## Evidence and acceptance
- java-build.log: initial Java compilation passed.
- native-build.log: initial multi-target invocation ended with successful shader output but contained native compilation errors; its shell exit status was insufficient. Corrected missing JNI and SharedObject headers.
- package-build.log: first complete packaging failed on those native includes. package-build-retry.log records the corrected complete build; see deployment evidence appended below.
- before/: snapshots of three existing touched files and both repository status lists; new files are listed above. Previously deployed jar is backed up before replacement.
- Manual acceptance: enter a simple Ponder scene, play its block/entity animations, rotate scene, switch scenes and return to world. Check orientation, black/missing textures, boundaries and noise/performance. If it crashes, retain latest.log, crash-reports and hs_err; do not infer that a successful native build fixed the reported crash.


## Final build and deployment
Time: 2026-09-18T12:49:14.3149384+08:00
Complete prepareRuntime + distributedJar: BUILD SUCCESSFUL in 1m 6s.
Previous deployed JAR SHA256: BE2111E5C662E4D66B71A760237C0A98C6F78D0A0B458BEB8C121B28FBEA61CC
Source/deployed JAR SHA256: 43102BE11F08A4C0F274D30DA988E1DE87558FDA713D4D1C5FFB6112A27CF905
Embedded and built core.dll SHA256: F3CB9AA7FF33C96B274E4B5853E39E6D0C033E6006C282588A9FD459FAD45756
Verified nested runtime jar contains PonderPathTracer.class and PonderScenePathTracingMixins.class; outer jar contains shaders/preview/ponder_comp.spv (23276 bytes).
Both repository diff --check passed (existing line-ending warnings only).
Game NOT launched. Runtime/visual acceptance PENDING.
