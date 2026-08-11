# Ponder RR model isolation — 2026-09-18

User authorized continued investigation. Inputs are Prism Balanced capture 17897159619891400 and DLAA capture 17897160438375040 under E:/Minecraft/PrismLauncherDev/instances/Radiance Test/minecraft/radiance-ponder-captures.

## Findings
- Both sets have complete 8-frame readbacks, finite values, identical camera matrices, successful RR API results. Direct RR output shows much more granular floor/shadow noise in Balanced than DLAA.
- NGX resource dimensions and input/render pixel units match the local SDK contract. MVLowRes is enabled, jitter is in input pixels; static upward-facing rough floor median motion is about 0.00142 input pixels for Balanced, 0.00248 for DLAA. No evidence of a gross stationary-floor MV scale error. Dynamic animation phases differ and do not provide matched motion ground truth.
- Foreground linear depth R16F ranges 694..711.5 in both, with only 36 unique values. Half-float step near 700 is 0.5 world units. This is a precision concern, not a demonstrated cause; depth precision has not been changed.
- Hidden model-hint difference: wrapper default info.preset=E and explicitly sets Quality, UltraQuality, Balanced, Performance and UltraPerformance, but omits DLAA. Local SDK defines a distinct DLAA hint. Local header describes D as default transformer, E as latest transformer. Actual DLAA model may depend on SDK/OTA/driver; do not assert DLAA actually used D. Existing DLAA/Balanced comparison therefore does not isolate resolution alone.

## Single-file temporary diagnostic
Only this turn's product edit: MCVR src/core/render/modules/world/dlss/dlss_wrapper.cpp. During Ponder-scoped feature creation, read radiance-ponder-rr-preset.txt from game working directory; accepts exactly token D, E or Default. If accepted, explicitly set all six mode hints including DLAA. Main-world feature creation does not read the override. RAII snapshots/restores shared NGX hint values (unset restored as SDK default 0) on every exit, preventing diagnostics from leaking into later feature creation. Missing/unrecognized token uses previous selection behavior. No hot reload: recreate Ponder pipeline or restart after changing file. Logs RR_CREATE_REQUEST with requested hint, quality mode, override status, input/output sizes. This is requested-hint evidence, not confirmation of driver-selected model.

## Experiment
Prepare D in both existing test instance working directories. User restarts Prism, returns to same scene and Balanced mode with SHARC and jitter enabled. Capture after convergence. If improved, E reversion required to attribute it to model hint. If unchanged, investigate depth precision separately. No camera, rendering resolution, shader, Halton, texture LOD or depth formats changed in this turn. Existing SHARC footprint experiment remains deployed. Running game is not restarted by agent.

Before/after and experiment.patch isolate this turn from extensive pre-existing dirty work. No staging, commits or pushes.
Build final PASS (1m32s); deployed and verified both JARs: 334D02DFAD8248C173C7BF59CADB514511D64C233128627231EABC9E0AE6408E. Ponder diagnostic selector D written in both existing instance working directories. Game not restarted. Runtime effect and actual model selection remain unverified.

Algorithm : SHA256
Hash      : BA60CC4152E39B547BB0E362C1BA146913709FF623429BB6486BB98D8DD1D941
Path      : D:\Workspaces\Repositories\GitHub\RecRivenVI\MCVR\build-radiance-1.21.1-neoforge\src\core\Release\core.dll



## User acceptance update (2026-09-18)
User explicitly reports D restores Balanced Ponder quality to a level similar to DLAA. Current Prism JAR hash reverified as 334D02DFAD8248C173C7BF59CADB514511D64C233128627231EABC9E0AE6408E; selector D. Live log requests hint=4 with diagnosticOverride=1. Later acceptance frames use 2227x1191 -> 3840x2054, so they are not the same resolution as the historical 2560x1440 paired captures. No reverse E test or post-D GPU capture was performed in this documentation turn. D is visually accepted in tested content, not a proven universal E defect. Temporary model override has not yet been converted into permanent product policy. Consolidated handover: ../PONDER-PT-HANDOVER-20260918.md.
