# Radiance / MCVR chunk performance diagnostics R2 — 2026-09-20

## Scope

R2 removes one measured source of empty-section work from the stable R1 state machine and adds staged timing counters to Radiance, the external ignored RadianceAudit mod, and MCVR. Product changes remain uncommitted.

## Retained optimization

- Initial chunk columns are scanned incrementally and near-first.
- `ChunkAccess.getSection(...).hasOnlyAir()` classifies known-empty vertical sections before dequeue.
- A known-empty section stays in the existing rebuild queue and is invalidated/published only when its normal queue turn arrives. This preserves R1's native visibility and invalidation ordering.
- Known-empty sections skip `RenderRegionCache.createRegion()` and mesh compilation.
- Column scanning and section dequeue share a small per-frame budget: 16–128 column scans and at most 256 section attempts, bounded by roughly 2 ms of scheduler work.
- A later dirty event clears the cached empty classification, so block changes compile normally.

## Diagnostics

Radiance emits counters for column scanning, region creation, scheduler work, compilation, and JNI submission. RadianceAudit aggregates those counters with client-frame elapsed time. MCVR exposes optional conversion, batch-build, submit, and fence-completion timings through `ChunkProxy.performanceSnapshotNative()`.

The intrusive probes are opt-in:

- `RADIANCE_CHUNK_PERF=1`: enable native chunk timing.
- `RADIANCE_AUDIT_GPU_PROFILE=1`: enable whole-frame Vulkan GPU timestamp sampling.
- `RADIANCE_AUDIT_AUTO_RELOAD_CHUNKS=1`: trigger the controlled `LevelRenderer.allChanged` reload used by the harness.

GPU timestamps are disabled by default. The current probe measures the complete GPU frame; it does not yet split ray tracing, denoising, upscaling, and post-processing into separate GPU ranges.

## Controlled results

R15 used 32-chunk view distance at 2560x1440, entered the test world automatically, triggered one controlled `LevelRenderer.allChanged`, ran for 93.982 seconds after join, then closed through `CloseMainWindow`.

- Gradle exit: 0.
- Clean `Stopping` / `Saving worlds` sequence.
- No new `hs_err` and no `VK_ERROR_DEVICE_LOST`.
- Reload generation: 83,762 section dequeues; 79,603 confirmed empty; 3,471 compiled and submitted to native.
- Occupancy classification: 4,225 columns scanned, 77,732 sections classified empty, 3,379 classified non-empty.
- The expensive region-creation path now follows the few thousand occupied/changed candidates instead of nearly every one of the roughly 84,000 dequeued candidates. The measured avoided share is about 95.9% relative to creating a region for every dequeue.
- Once rebuild work settled, scheduler overhead fell to roughly 0.1–0.3 ms per two-second reporting interval.
- Steady native renderer logs were about 24–27 FPS. Client-frame elapsed time was about 2.0 seconds over 51–55 frames per reporting interval. This timer includes blocking and presentation, so it does not by itself prove a Java CPU bottleneck.

The result supports a narrow conclusion: after chunk rebuild settles, the section scheduler is no longer large enough to explain the persistent 32-chunk steady-state frame rate. The next performance comparison should use the opt-in GPU timestamp in isolation; if total GPU time matches frame time, add phase-level Vulkan timestamps around ray tracing, denoising/upscaling, acceleration-structure maintenance, and post-processing.

## Rejected experiments

- R9/R10 batched Java-to-JNI dirty/relocate/invalidate state transitions and published empty occupancy immediately. Both ended in `VK_ERROR_DEVICE_LOST`.
- R11 disabled the GPU timestamp probe but retained those product changes and still lost the device, excluding the timestamp query as the common cause.
- R13 restored direct JNI state transitions but still published empty occupancy immediately and again lost the device shortly after join.
- R14 disabled occupancy optimization and retained direct JNI calls; it passed the same 32-chunk reload test for 95.531 seconds post-join.
- R15 retained direct JNI calls and changed occupancy to classification-only with normal paced publication; it passed.

The batched JNI state path and immediate empty publication are fully withdrawn. Their failure indicates that state transitions touching native chunk resources require frame-boundary transactional ordering; fewer JNI calls alone is not a safe optimization.

## Validation

- Radiance `gradlew build`: PASS.
- RadianceAudit clean build: PASS.
- MCVR Release build/install: PASS.
- MCVR Release `ctest`: PASS, 26/26.
- `git diff --check`: PASS in both repositories before the final build; rerun after packaging/deployment.

## Evidence

- Stable no-occupancy control: `chunk-perf-r14-no-occupancy-32chunks-20260920/`
- Stable paced-occupancy candidate: `chunk-perf-r15-paced-occupancy-32chunks-20260920/`
- Failed product combinations: `chunk-perf-r9-*` through `chunk-perf-r13-*`

## Acceptance boundary

This automated test proves queue convergence, controlled reload recovery, and graceful shutdown in the fixed test world. It does not prove visual completeness while moving rapidly through new terrain, dimension changes, or long manual play. The steady-state 24–27 FPS observation is a starting point for GPU-stage diagnosis, not a completed attribution to path tracing, denoising, upscaling, or acceleration-structure work.

## Deployment

Prism instance: `E:\Minecraft\PrismLauncherDev\instances\Radiance Test\minecraft\mods`

- Radiance SHA-256: `0D9007FAC86FE37EC9F85F53952FEE36CC01BDD5AF93F2D22A414C81292CDEBC`
- RadianceAudit SHA-256: `794E720C35D4BE18E77BDF8ECA98A2CCEEC7CBB158BA1EF645FD81FFF6277F3A`

Source and deployed hashes match. The instance was not launched after deployment; visual and moving-world acceptance belongs to the user run.
