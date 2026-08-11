# Ponder SHARC footprint isolated experiment

2026-09-18. Authorized by user following RR-BALANCED-INVESTIGATION.md.

Only product edits this turn: MCVR src/shader/world/ray_tracing/internal/vanilla-pt/world/world.rgen and advanced/world/world.rgen. initMainRay receives samplingResolution from the existing full input resolution variable; orthographic coneWidth uses that instead of the reduced SHARC dispatch extent. Regular vanilla primary/hand paths pass their unchanged input dimensions. Advanced primary_trace.glsl is unchanged because it is not the downsampled SHARC update path. Perspective spread and shared Halton sequence are unchanged.

Before/after source snapshots and experiment.patch contain this turn's exact changes independently of pre-existing dirty work.

Validation: prepareRuntime distributedJar PASS (54s). Separate Vulkan 1.4 glslang compilation PASS for vanilla world and all three advanced world passes, each with SHARC_UPDATE=0/1 (8 variants), using defaults and pass definitions from configs.json. Initial standalone compile omitted config definitions and failed on VPT_ATMOSPHERE_RG; corrected invocation supplies config defaults. Final logs named <pack>-<pass-index>-<update>-compile.log. This verifies compilation, not actual game runtime or visual quality.

Built/deployed JAR SHA256: 92A33CD8346BADD7FD517732D99D4B88E9184A6E2B6DA2F6F767648083DE6D52. Copied to existing repository readback session full/mods and existing Prism Radiance Test minecraft/mods. All three hashes match; pre-deployment JARs backed up here. Both nested shader packs verified to contain samplingResolution fix.

No process restarted or terminated; no config changed. Restart required for acceptance. No claim that Balanced quality is fixed. Compare same Ponder scene and Balanced mode after history settles; DLAA control if needed. All product edits remain uncommitted/unstaged.

## Prism Balanced capture
User explicitly authorized capture of their running Prism instance. Identified PID 67596 via instance-specific java.library.path and Prism EntryPoint. No launch, restart or config changes.
JAR on disk matches deployed 92A33CD8346BADD7FD517732D99D4B88E9184A6E2B6DA2F6F767648083DE6D52.
Capture: E:\Minecraft\PrismLauncherDev\instances\Radiance Test\minecraft\radiance-ponder-captures\17897159619891400
Eight frames COMPLETE, all RR results 1. Saved pipeline-at-capture.yaml confirms Balanced with SHARC and jitter enabled. Captured data includes RR input/output and auxiliary buffers. GPU completion and successful API results do not establish reconstruction quality. User reports no improvement with SHARC or jitter toggles; DLAA alone looks normal, Quality and Balanced remain poor. Treat SHARC correction as insufficient for the reported issue. Await same-scene DLAA comparison.


## Matched-camera Prism DLAA capture
DLAA directory: E:\Minecraft\PrismLauncherDev\instances\Radiance Test\minecraft\radiance-ponder-captures\17897160438375040
Balanced directory: E:\Minecraft\PrismLauncherDev\instances\Radiance Test\minecraft\radiance-ponder-captures\17897159619891400
Both bursts contain 8 COMPLETE frames, all RR result=1; byte validation and analysis passed. DLAA inputs/output 2560x1440; Balanced inputs 1485x835, output 2560x1440. Both saved configs have SHARC and jitter enabled. All 16 current camera matrix blocks identical (jitter differs as expected). No nonfinite values in analyzed images. Scene is animated, so animation phase and stochastic sampling are not matched.
Viewed first-frame output-color.png from each using identical Reinhard/gamma preview mapping. Balanced shows pronounced granular noise in floor/shadows and softer detail; DLAA shows substantially cleaner floor, shadows and detail. This difference exists in captured RR output before tone mapping and Ponder UI composite. This rules out downstream composite as the sole cause of the captured difference, but does not distinguish invalid RR inputs from reconstruction behavior. No product edits or process/config changes made.
