# Ponder denoiser input correction — 2026-09-18

User reports that the full WorldPipeline Ponder preview loads but denoising looks poor.
Observed instance configuration: rt_dlss preset, DLSS Balanced, default shader pack, jitter and SHARC enabled. Java log reports a 2560x1440 output target. This does not imply native-resolution ray tracing.

## Confirmed source defect
Both vanilla-pt and advanced primary sky paths pass a direction (homogeneous w=0) to computeCameraMotionVector. An affine orthographic previous MVP preserves w=0, so the previous unconditional perspective divide produces NaN/Inf motion. These sky pixels enter DLSS RR before the final Ponder transparency composite.

Change: src/shader/util/util.glsl returns zero motion when the projected homogeneous coordinate is degenerate or non-finite. Finite perspective and geometry reprojection retain the original formula. Zero represents a stationary orthographic background, which has no finite sky reprojection.

This is a definite invalid-input correction, NOT proof that it explains the entire reported denoising issue. No denoiser strength, quality mode, sample count or instance configuration was changed.

The perspective-only linear_to_device_depth shader found during investigation is used by FSR/XeSS, not the active DLSS RR path. It was not changed and is not attributed as this issue's cause.

## Remaining investigation
- Geometry history currently identifies the entire captured scene as one entity. Count-based correspondence cannot establish stable primitive identity when animated scene geometry changes.
- Need user comparison of static noise versus motion trails versus silhouette artifacts.
- No visual runtime acceptance in this agent run; user launches the deployed instance manually.

## Validation
- Verify-Shaders.ps1 successfully compiles default vanilla-pt and advanced primary/world variants with glslang for Vulkan 1.3 (shader-check.log).
- Full build and deployment hashes recorded separately below after completion.

Build: prepareRuntime distributedJar exit 0 (build.log).
Deployed JAR SHA256: BBBA04ABE33B8C100219450C08694C2152DC7C1F48273DC63F3F0ACC38C96A8B
DLL SHA256: E8DDC7195D3C78DB155D7E4A29D9EB444FB4F88E63175CCDC289A8541C645097
User clarified: both static noise and edge/transparency artifacts. Composite alpha currently thresholds raw low-resolution jittered first-hit depth separately from reconstructed DLSS color; this is still unresolved.

