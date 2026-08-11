# Ponder RR activation diagnostics — 2026-09-18

User: previous finite-sky-motion fix improved odd silhouette shapes, but noise remains severe, as if RR is inactive.

This change is diagnostic only; no sampling, denoiser, or instance configuration changes.

## Changed source (MCVR)
- dlss_wrapper.hpp: per-RR-instance Ponder diagnostic frame count.
- dlss_wrapper.cpp: after actual NGX_VULKAN_EVALUATE_DLSSD_EXT, records success/result, reset flag, instance handle, scene pointer, input/output resolution, jitter, resource presence, view determinant and matrices.
- ponder_path_tracer.cpp: records scene creation and concrete pipeline module types; first three captures record CPU geometry view-depth bounds and non-positive/non-finite depth count.

## Output and limits
File: E:\Minecraft\PrismLauncherDev\instances\Radiance Test\minecraft\radiance-ponder-rr.log
Appends on the render thread; CREATE marks scene lifetime. Evaluation records first 3 calls, then every 120 calls through call 1200; resets/failures also recorded through 1200. CPU geometry scan occurs only for first 3 captures per scene. Existing general NGX error logging remains active.

Correlate CREATE scene pointer with RR scene pointer. Repeated new handles/frame=1 show reconstruction rather than accumulated history. A stable handle, increasing frame, success=1 and reset=0 establish accepted NGX recording. They do NOT establish completed GPU execution, correct image values, visual denoising quality, or stable per-primitive history. CPU depth bounds likewise do not substitute for GPU G-buffer readback.

## Acceptance
User launches manually, opens a Ponder scene and holds a mostly static view for roughly 15 seconds. Then inspect the log. No game was launched or stopped by the agent.

Before/after sources archived here; changes remain unstaged and uncommitted. Build/deployment results follow.

Build PASS: prepareRuntime distributedJar, exit 0, 5m18s. git diff --check for the changed files passed.
Deployment source/target JAR SHA256 match: CFDEEC54D1525458D8756FBF66671D149640DD75D03A5C9E56CC6A22613F1AEF
DLL SHA256: 16712898C2FBB98887F9CB08A261127781BFFAC0117048046F2F5B45D35FD94C
Runtime diagnostics: pending user launch; no success claim yet.

## Manual run evidence
Captured after user confirmed run complete. One CREATE, module order RT -> DLSS -> tone mapping -> post render. Same RR handle from frame 1 through sampled frame 1200, all sampled calls result=1/success=1. Only first frame reset=1; remaining recorded samples reset=0. Input 1485x835, output 2560x1440, resources=11111111. First 3 geometry captures contain only finite positive CPU view depth (about 388-533 units).

Conclusion: NGX accepted RR evaluations and recorded history was not repeatedly reset/recreated. This rules out the simple absence-of-RR hypothesis at the CPU recording level, but is not evidence of correct GPU inputs or reconstruction.

Next suspect: camera convention. Stabilized view matrix determinant is -1, indicating a reflected basis rather than a pure rotation; during entry it also contains nonuniform scale (det=-1.0318, -1.00534). Orthographic projection carries GUI offsets (-1,-1); CPU geometry is hundreds of units from the camera. These are verified values, not yet a proven violation of the RR contract or root cause. Prioritize camera/input normalization and GPU G-buffer verification before denoiser tuning. Keep runtime-rr.log/runtime-latest.log as baseline.
