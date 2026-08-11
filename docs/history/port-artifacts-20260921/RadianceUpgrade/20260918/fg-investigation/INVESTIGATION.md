# FG performance investigation — 2026-09-18

## Scope

User requests investigation of negative FG performance. Preserve current UI/material/GUI behavior; no GUI separation or presentation redesign in this diagnostic build. No game launch, restart, config alteration, staging or commit. Existing unrelated Java processes left alone.

## Confirmed observations

- Latest Prism session ended normally at 16:32:13. Its saved **Radiance** options are `vsync=false`, `maxFps=260` (native unlimited), FG=true, RR model F. Vanilla options.txt differs and is not authoritative for this renderer. Saved options prove exit state, not every earlier frame.
- Swapchain chooses IMMEDIATE when vsync is false and supported, otherwise FIFO. There is no MAILBOX selection despite a stale code comment. New diagnostic logs actual selected mode (0=IMMEDIATE, 1=MAILBOX, 2=FIFO).
- Main loop acquires swapchain image, builds/submits whole frame, presents interpolated output, then synchronously invokes extra acquire/submit/present for retained real output. Same main queue; no independent frame presenter or timing schedule.
- Main whole-frame submit waits on swapchain acquisition at ALL_COMMANDS. Extra copy submit also waits at ALL_COMMANDS. Display availability can therefore gate rendering, not just the final swapchain copy. Exact cost is not established without runtime timing.
- Two calls to present do not establish evenly spaced display frames. With IMMEDIATE the generated frame can be overwritten soon afterward; FIFO instead schedules both outputs at refresh boundaries and may back-pressure the main loop.
- NGX integration guide section 7 explicitly leaves timing to the application and describes evenly spaced generated/retained frame presentation, typically asynchronous from the main render thread. Local source: ../ngx-fg-guide.txt, lines 670 onward (guide labels itself 310.7.0 inside current SDK).
- Current FG requires and supplies PT motion/depth; helpers default multiFrameCount=1 and multiFrameIndex=1. Not an accidental MFG 4x request.
- Extra CPU sleep and immediate post-copy fence wait removed in preceding revision. Ring reuse waits remain. Vanilla FPS limiter and OpenGL swap are redirected away; native maxFps=260 does not introduce sleep.
- FG preparation copies HUDless and real images, computes full-resolution binary UI coverage, and converts depth. NGX evaluate and copies are serial with main GPU work. UIR is enabled per NVIDIA log. GUI mask is approximate; this investigation does not repair it.
- radiance-perf.log counts main real-frame loops, not physical displayed frames. Prior FG-specific timers only wrote stdout and were absent from retained Prism logs. No numeric attribution of cost can be claimed from those logs.
- Prior session changed resolution from 2560x1440 to 3840x2054 and switched settings/scenes. Those intervals are not a controlled FG A/B.

## Diagnostic-only changes

Four source files: new `src/core/diagnostics/fg_timing.hpp`; instrumentation in `render_framework.cpp` and `dlss_frame_generation.cpp`; read-only actual present-mode accessor in `swapchain.hpp`.

Logs append to game directory `radiance-fg-timing.log`, once per roughly one second, in both FG on/off modes. Each metric has `average_ms/max_ms/sample_count`. Epoch timestamps identify runs. Counts are successful API acceptance, **not scanout or PresentMon results**.

- CPU: main acquisition/fence/submit, first present, extra ring fence/acquire/submit/second present, native limiter, FG record (includes first-time creation).
- GPU: existing full main submission duration, FG preparation in fuse command, NGX evaluate. FG queries reuse completed context slots and use no WAIT flag. Existing frame timestamp read is unchanged and follows its frame fence.
- `gpu_prepare` excludes earlier HUDless copy, which is included in whole GPU frame; whole GPU frame excludes extra retained-real copy. GPU timestamps do not measure display scanout or queued time before the main submission. CPU metrics can overlap GPU execution and **must not be summed with GPU durations**.
- One-second windows may straddle settings changes; `fg_end` is end-of-window state, GPU samples are delayed until context reuse. Discard startup, change, and subsequent warmup windows. Sample counts of zero mean unavailable, not measured zero cost.
- Existing lifetime-average FG stdout remains; use the new one-second log for analysis.
- Before source snapshots and previous performance/options snapshots retained in this directory. `instrument.py` records exact text transformations; do not rerun on already patched sources.

## Next controlled capture

User launches deployed instance manually. Same world, camera, resolution, RR/SR mode/model, vsync and all other settings. After warmup, FG OFF 30 seconds → ON 30 seconds → OFF 30 seconds; settle after each settings rebuild. Keep window foreground. No need for screenshots.

Interpretation: large GPU evaluate/prepare increase indicates compute/preparation cost; large host acquire/present/fence increase indicates backpressure/serialization; low loop loss but poor smoothness requires physical presentation timing (PresentMon/ETW) to establish pacing/display loss. No async presenter or performance recovery claimed until measured.

## Build and deployment

- Full `prepareRuntime distributedJar` succeeded in 3m42s, build.log. Four existing regression checks passed (framebuffer GPU, DLSS resource contract, scene scope/camera), ctest.log. These do not exercise live FG display timing.
- Embedded core.dll byte hash matches newly built DLL and contains diagnostic filename. DLL SHA256: 3FB154C04B3FAE29CA7ECB29B8C2348CB87399316D84B47452DE1F40CA319C3A.
- JAR SHA256: C3343C16221B9A1D6A38CD908BC6172961EEB912C811EA85581FBF80C00CE0B3. Deployed to authorized Prism and repository test instance; both hashes verified, deployment.json. Previous JARs retained as before-diagnostic-0.jar and before-diagnostic-1.jar.
- No game launched or settings changed. Actual diagnostic log capture and root-cause cost attribution remain pending user scene run.

## User capture analysed at 16:54

User completed manual run; logs show normal stop at 16:53:56. Snapshot `capture-1654.log`, weighted per-sample metrics in `analysis-1654.json`. All following stable windows use 2560x1440 and actual mode=2 (FIFO). This differs from previously saved vsync=false; current exit configuration is vsync=true. No agent config changes.

| Window (local time) | FG | Real loops/s | Accepted presents/s, not displayed FPS | Main GPU ms | Main fence CPU ms |
|---|---|---:|---:|---:|---:|
| 16:49:33–16:50:10 | off | 80.33 | 80.33 | 12.391 | 1.649 |
| 16:50:24–16:51:43 | on | 78.31 | 156.62 | 12.366 | 2.190 |
| 16:52:05–16:53:24 | off | 97.35 | 97.35 | 10.226 | 0.394 |

FG-on window: NGX evaluate GPU 1.753 ms, fuse input preparation GPU 0.084 ms, record CPU 0.227 ms; extra acquire CPU 0.004 ms, extra submit 0.018 ms, second present 0.086 ms, ring fence 0.002 ms. Main present 0.164 ms, native limiter zero. One successful extra real present per real loop throughout this window. No measured large CPU block in extra acquire/present.

Interpretation update:
- The reported historical 80→20 collapse is **not reproduced in this capture**. Current resolution/present mode differ from earlier observations; cannot generalize to the older 3840x2054 session.
- FG definitely evaluates and pairs are accepted. GPU computation is ~1.75 ms, not tens of milliseconds. The 2.14 ms whole-GPU difference relative to the *later* off window is consistent with added generation/preparation/copies, but is not an exact causal decomposition.
- The two FG-off baselines differ substantially (12.39 vs 10.23 ms). Scene/settings/temporal-state variation is not fully controlled by this log; cannot attribute their difference to FG or claim a precise universal percent loss.
- Correct the prior leading suspicion: long CPU stalls inside second acquire/present are **not supported** here. Main context fence wait rises relative to later off, consistent with waiting for GPU work; it overlaps GPU time and must not be added as a separate serial GPU cost.
- 156.62 successful presentation submissions/s are not proof of 156.62 physical displayed frames/s or good pacing. Current instrumentation cannot decide whether perceived lack of improvement is display timing, dropped/short-lived output, or frame quality. Next discriminator is physical presentation/scanout timing (PresentMon/ETW or equivalent), with one fixed scene and settings. Do not rewrite async presentation or GUI based on an unproved CPU-stall diagnosis.
- No source/deployment/config changes made during this log-analysis turn.
