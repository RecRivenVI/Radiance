# Upstream benchmark adapters

Status on 2026-09-25: built, unit tested and exercised in bounded world preflights on the two pinned
upstream packages. This is a common-metrics subset of Audit, not a claim that fork-specific JNI/GPU
instrumentation works on upstream. Do not install this adapter together with the normal Audit JAR:
both deliberately retain `radiance_audit` and upstream-derived identity/icon metadata.

## Build

Use Java 21 and the repository wrapper:

```powershell
.\gradlew.bat :radiance-audit:inventoryTest :radiance-audit:benchmarkTest `
  :radiance-audit:benchmarkAgentJar :radiance-audit:benchmarkNeoForgeJar :radiance-audit:benchmarkFabricJar
python -X utf8 -m unittest discover -s Modules/RadianceAudit/benchmark -p test_fixture.py
```

Python tooling uses `nbtlib==2.0.4`. The launcher is PowerShell 7. Build outputs are in the Audit
module's `build/libs`. The normal Radiance/MCVR artifacts are not rebuilt by these Java-only targets.
The NeoForge adapter uses the existing compile toolchain; no fork classes or native collector are
included. The Fabric entry uses the loader's static-method entrypoint adapter, so it does not need
a parallel Loom build for this loader-neutral implementation.

Install only the matching `benchmark-neoforge-1.21.1.jar` or `benchmark-fabric-1.21.4.jar` in `mods/`.
Place `benchmark-agent.jar` outside `mods/`. The agent is required **before** NeoForge SERVICE can
create its early window. Its private ASM implementation is an extracted nested resource, not a
second ASM on Fabric's classpath. Bootstrap hooks have no Minecraft or renderer dependency.

## Explicit unattended mode

```text
-javaagent:<absolute-path-to-RadianceAudit-...-benchmark-agent.jar>
-Dradiance.audit.benchmark=true
-Dradiance.audit.experiments=true
-Dradiance.audit.unattended=true
-Dradiance.audit.benchmark.version=1.21.1-neoforge
-Dradiance.audit.benchmark.warmupSeconds=30
-Dradiance.audit.benchmark.sampleSeconds=30
```

Use `1.21.4-fabric` for the other adapter. The working directory must have the explicit
`.radiance-audit-test-instance` marker. Without `benchmark=true`, the startup companion is inert.
An explicitly requested benchmark without its experiment/marker prerequisites fails at startup.
Both scripts restrict instances to this repository's `run/`; never use a Prism or production world.

All new automated cases use `soundCategory_master:0.0`. The launcher rejects an unmuted or missing
master-volume setting before starting Java. Apply the same mute setting to every comparison side;
historical evidence and the user's manual-instance audio settings remain unchanged.

The unattended implementation sets GLFW `FOCUSED=false` and `FOCUS_ON_SHOW=false` before creation,
prevents explicit focus/warp/grab, and filters this process's keyboard/mouse callbacks and public
polling APIs. Polling events, resize, closing and OS input to other applications remain available.
Fullscreen is rejected, not silently converted into a different benchmark. No global input hook,
`BlockInput`, cursor movement, fabricated focus or hidden/minimized rendering is used.

GLFW hints only stop GLFW's own focus calls. On Windows the unattended companion therefore also
adds `WS_EX_NOACTIVATE` (plus `WS_EX_APPWINDOW`, keeping the taskbar button) to the created window,
reads it back and reports `activationGuard=applied` in the observer `STATUS.txt`; a failure makes
the observer invalid. Size, z-order, presentation and input filtering are unchanged. The launcher
records each foreground sample's `inputIdleMs` (time since the last user input) and the live
window's extended style in `window-activation.json`; `result.json` reports
`activationGuardObserved`. `compare.py` rejects a reported guard that is not applied or observed,
in addition to any game foreground sample. Observers/launchers before 2026-09-29 report neither.

Machine load (since 2026-09-29). Before launching, the launcher samples 20 s (`preflightSeconds`,
10-120) of whole-machine CPU, as per-process CPU-time deltas over all logical processors, and GPU
utilization via `nvidia-smi`, and looks for other Minecraft clients (Java command lines with
`--gameDir`, `--assetsDir`, `--launchTarget`, `fabric.dli.` or a client main class). It launches
only below 8% CPU and 15% GPU with no other client, retrying every 10 s for up to
`preflightMaxWaitSeconds` (default 600); otherwise it writes `environment.json` and refuses to
start. While the client runs it records, every 5 s, the CPU of all processes other than the client
and its own monitors, plus every process that starts (with the command line for Java, Python,
Node and build tools). The run counts as quiet only with other processes averaging at most 5%
(95th percentile 10%) and no other client started. GPU use cannot be attributed per process on
Windows, so it is checked only before launch. Overlay/capture tools present (RTSS, ToDesk, OBS,
Afterburner, NVIDIA Overlay, Discord) are listed but not rejected. `result.json` carries the
`environment` summary, `environment.json` the samples, and `compare.py` rejects a run without that
evidence or one that was not quiet. Processes this account cannot read (some system services) are
not counted.

The tested boundary is LWJGL GLFW 3.3.3 and the pinned clients. Arbitrary third-party JNI/raw Win32
input, gamepad libraries and native-created windows are not covered by this Java observer. Add an
explicit adapter before including such a producer. The monitor samples Windows foreground PID at
250 ms; the per-frame CSV separately records actual GLFW focus and Minecraft's cached flag. The
two flags differed in both upstream preflights: cached true, actual false. Neither is rewritten.

Background GPU scheduling, occlusion, VSync, inactivity caps and FG/SDK suspension may change work.
Match these conditions on both sides. No-focus results are not automatically comparable with old
foreground samples. Generated FPS is unavailable here; timestamps describe real game-frame calls.

## Disposable scenes and repeated runs

`fixture.py` creates a new world and datapack, with fixed seed/time/weather and scripted camera.
It refuses overwrites. A tier-1 recipe has 64 separate chests, 64 banners, 16 equipped armor stands
and 16 item frames. Tiers 2/4 increase counts without unbounded allocation. Modded 1.21.1 adds 8
Create motors, 32 shafts and 4 Sable assemblies; 1.21.4 rejects the modded recipe. Motors use Create's
real default 16 RPM, not a guessed NBT setting. Sable assembly uses its actual command registration.

```powershell
python -X utf8 Modules/RadianceAudit/benchmark/fixture.py run/my-new-case --version 1.21.1 --modded
```

A fixture on disk is not proof its commands executed. `analyze.py` reads saved Anvil/NBT data,
counts actual objects, checks motor speeds and occupied Sable slots, verifies packaged/extracted/
observed-loaded core identity, and summarizes raw frame distributions. Saved counts do not prove
that every object was submitted to PT/Flywheel or that its visible output is correct.

For a new run, reuse the runtime classpath and exact mod identities from a successful isolated
template, but generate a fresh world and take the current diagnostic build:

```powershell
python -X utf8 Modules/RadianceAudit/benchmark/prepare_case.py `
  run/upstream-benchmarks-20260925/neo-final-preflight run/my-new-neo-case --warmup 45 --sample 60
pwsh -File Modules/RadianceAudit/benchmark/Start-Benchmark.ps1 -LaunchJson run/my-new-neo-case/launch.json
python -X utf8 Modules/RadianceAudit/benchmark/analyze.py run/my-new-neo-case
```

The Fabric template is `run/upstream-benchmarks-20260925/fabric-final-preflight`. These local runtime
templates are ignored evidence, not shipped installation dependencies. Initial setup used the local
NeoForge development runtime and a separate Fabric installation. A clean-machine installation
recipe still needs to pin all runtime libraries; do not claim a fully portable installer.

Every run retains `FIXTURE.json`, `launch.json`, exact mod/agent hashes, observed core path/hash,
stdout/stderr, client logs, foreground samples, raw `frames.csv`, observer `STATUS.txt` and analysis.
Timeout requests a normal window close only; a still-running process is reported and never force
killed or automatically restarted. Existing evidence is not overwritten. Confirm server save in
the actual log; successful process exit alone does not prove native cleanup or visual acceptance.

## Comparability gate

The first runs are **preflights**, not a fork/upstream speed comparison. In particular, both
upstreams skipped NGX/DLSS initialization, and their own options retained VSync even though vanilla
`enableVsync=false` was written. The upstreams also differ in chunk thread/batch defaults and
emission collection. Align and verify the effective renderer configuration before measuring.

Next formal runs must pin actual framebuffer size, active PT/denoiser/upscaler, VSync and limits,
resource packs, fixture/tier, mod hashes, load completion, fixed warmup and repeated sample order.
Separate vanilla-only 1.21.1 and 1.21.4 reference results from the same-version modded fork A/B.
Report mean/p50/p95/p99 and run-to-run spread. Windows thread CPU time can be quantized; individual
zero rows do not mean no CPU work. GPU stage timings, SDK allocations and presented/generated frames
remain unavailable, not zero. Measure diagnostic overhead before claiming small improvements.

## Matched pressure suite (2026-09-25 follow-up)

`prepare_suite.py` pins three renderer artifacts: upstream NeoForge 1.21.1, upstream Fabric
1.21.4 and the local Performance Optimization Test V1 package. It never edits their JAR/core.
The common `model-city` tier contains 256 full-armored stands, 256 frames displaying repeated
complex baked block models, 256 chests and 256 animated banners. Terraces keep distant rows
exposed; frames face the scripted camera. This targets the producers found expensive in the
earlier city investigations, without importing a production world. Tier 2 doubles those counts.
`terrain-city` has 144 buildings, glass, cutout trees and water. `factory` adds 64 motors, 256
shafts and 16 assembled Sable structures to the tier-1 model district. Factory is only comparable
between the two 1.21.1 builds; it is deliberately rejected for Fabric 1.21.4.

```powershell
python -X utf8 Modules/RadianceAudit/benchmark/prepare_suite.py run/my-fresh-comparison `
  --target upstream-neo --scene model-city --warmup 60 --sample 30
pwsh -NoProfile -File Modules/RadianceAudit/benchmark/Start-Benchmark.ps1 `
  -LaunchJson run/my-fresh-comparison/launch.json
python -X utf8 Modules/RadianceAudit/benchmark/compare.py run/my-fresh-comparison
```

Targets: `upstream-neo`, `upstream-fabric`, `fork`. Defaults: actual framebuffer target 2560x1440,
windowed, 16 render/5 simulation distance, entity range 4, 70-degree FOV, no resource pack, Advanced
PT, four bounces, jitter/SHARC enabled, RR Balanced, native/vanilla VSync off, cap 260, FG/Reflex off,
8 Java chunk workers, native batches 8x8, emission collection on, Java 21 with 2/8 GiB initial/max
heap. Requesting Advanced with emission collection off caused a real upstream fallback and is
not a valid comparison. Module names are adapted per artifact, not copied blindly across versions.
The fork enables the previously accepted `radiance.rigidModels` optimization, while
`radiance.rigidParts` remains off; report this explicit opt-in alongside the source version.

Upstream installation requires external NVIDIA DLLs in `radiance/`. Preparation takes the exact
three release DLLs and notices already shipped by the pinned fork and places local comparison
copies there; the fork keeps its normal bootstrap extraction. Their loaded hashes are checked.
This is not a public distribution or license approval. Upstream's hidden RR preset cannot be
selected through its public UI/config. The historical native header uses E; fork requests E/5.
Treat exact SDK-selected model equivalence as unproven. Different native/SDK/shader implementations
remain part of the tested product, even when requested settings are the same.

The observer now records actual framebuffer size/focus/minimization each frame, and reads live
Java pipeline modules/attributes/options at world entry and sample end. It does not call renderer
setters. `compare.py` rejects silent preset/attribute fallback, missing RR runtime, focus/size
changes, failed fixture checks, observer errors, abnormal exit and device loss. This is a settings
and lifecycle gate, not proof of pixel equivalence, identical frustum policy, GPU guide contents
or every SDK evaluation. A 1.21.4 result is a cross-version reference, not an isolated optimization
measurement. Default cap 260 is inactive only for samples safely below that ceiling.

`run_series.py <manifest> --execute` accepts a finite prepared `cases` list and launches sequentially.
It stops on the first failed gate; it never retries a crash. It collects whole-device NVIDIA usage
at 1 Hz (including desktop/SDK allocations, not process-only VRAM), plus per-process CPU/private
memory. Its owned NVIDIA monitor is terminated after each client; Minecraft is only normally closed.
No other builds or heavy analysis should overlap formal timing. Alternate target order, preserve
every raw sample and report repeat spread rather than pooling away a bad run.

`summarize.py <series.json> ... --output <summary.json>` rechecks the configuration gate and saved
camera, armor and frame contents before aggregating. Report frame means with equal weight per run;
pooled percentiles have their own label. Render-thread CPU includes native/driver execution and
possible busy waits, not just Java model work. External one-second telemetry is approximately
aligned to observer startup and trimmed by two seconds at sample edges; it is not GPU pass timing.
The 2026-09-25 follow-up ran model-city twice per target and factory twice per 1.21.1 target. Tier 2,
terrain-city, moving routes, full draw coverage and observer-overhead isolation remain unmeasured.
See the measured checkpoint (archived ledger under D:/Workspaces/Artifacts/Radiance/history-archive/Radiance/docs/DEVELOPMENT_LEDGER.md).

## Explicit shader-control comparison (2026-09-25 correction)

The earlier pressure gate covered outer settings but missed module-owned shader parameters; its
NeoForge 0.1.6 runs used 3/8/2 bounce/initial/spatial controls versus 4/32/4 in the other targets.
Preserve those runs as whole-product observations, not matched-work measurements.

Preparation now reads `configs.json` from the actual pinned Advanced ZIP in each JAR. All exposed
shader attributes are explicitly configured, included in launch-input hashes and checked at world
entry and sample end. Shared controls use the fork's pinned shader defaults (including 4/32/4,
spatial radius 32, disocclusion spatial samples 20 and confidence cap 24). Known renamed controls
are mapped explicitly; unsupported ranges/enums fail instead of silently clamping. Unique controls
retain recorded defaults, except that upstream RR resolution is explicitly quality/high and its
separate initial-disocclusion count is 32. Each case records the mapping and packaged config hashes.

Dynamic attributes require their own persistence path: upstream 0.1.6 uses
`radiance/shader-pack-settings/advanced.zip-<SHA256-of-absolute-pack-path>.properties`; the older
packages use `radiance/shaders/world/ray_tracing/advanced.zip.txt`. Merely adding dynamic attributes
to `pipeline.yaml` does not reliably apply them. The launcher pins the settings file as an input,
and the result gate verifies the actual extracted Advanced ZIP against the original package hash.

This aligns exposed input controls, not different integration algorithms, entity culling,
froxel/screen-space volume processing, tone mapping or hidden SDK-selected RR models. The result
classification explicitly retains that distinction. No shaders, JARs or core DLLs are rewritten.
Use fresh preflights before timed runs; rejected configurations must not enter the performance table.

## Fork high-distance and update-latency evidence (2026-09-28)

The startup companion now captures `sample-start` as well as world-start/sample-end settings;
the boundary I/O frame and its following interval are excluded from timing. The optional JVM
property `radiance.audit.benchmark.chunkBoundaries=true` calls the normal Audit module's
`BenchmarkChunkState` observer. It records Java queues/in-flight counts and the existing native
scheduler snapshot without scanning section contents. It requires the normal fork Audit JAR,
not the upstream portable adapter. Native counters are consumed when read; disable continuous
chunk diagnostics during steady sampling so another consumer cannot erase the observed deltas.
Unavailable data must not be interpreted as zero pending work.

`launch.json` may explicitly set `diagnosticEnvironment` entries to `"1"` for
`RADIANCE_CHUNK_PERF`, `RADIANCE_CHUNK_TRACE`, `RADIANCE_CHUNK_BENCH`, `RADIANCE_CHUNK_CENSUS`
(logs a `ChunkResidency` line about every 60 frames: published slots with their unique device
bytes, queued builds and in-flight batches), and
`RADIANCE_AUDIT_REFERENCE_BUFFER_SWEEP`. Other entries/values are rejected. The launcher still
clears inherited renderer/driver experiment variables. The reference sweep deliberately
repeats old CPU bookkeeping on the same new binary, logs its activation, and is off by default;
it changes neither scene content nor quality/GPU synchronization. Never label such a run normal
optimized behavior or mix it with the independently retained old-artifact comparison.

For actual rebuild/interaction overlap, `radiance.audit.chunkBench.reloadDelayMs=0` and
`radiance.audit.chunkBench.reloadIntervalMs=100` begin the second interaction group immediately
after F3+A. Defaults preserve the historical two-second delay and1.5-second interval.
`radiance.audit.chunkBench.stateAtOperation=true` records pending work before each operation.
The probe still uses normal client interaction packets and requires both isolated markers.
Initial rebuilds can absorb an operation without a distinct revision, and rapid actions can
supersede intermediate revisions. Report these outcomes rather than inventing missing latency.
Correlate only surviving identities, calibrate native/Java clocks using the retained brackets,
and distinguish first GPU-completed referencing frame from displayed pixels/temporal settling.

`monitorGpu=true` with `gpuMonitorBackend="nvml"` starts the bounded, read-only
`gpu_monitor.py` helper against the installed Windows driver. It flushes each CSV sample and
exits on an owned stop file; the launcher stops any remaining owned helper on exit. It does not
create a CUDA context, alter clocks/TDR, download or bundle a vendor runtime. Measurements are
device-wide WDDM usage, not isolated process VRAM or SDK allocation totals. Legacy CLI looping
is retained only for old manifests; its buffered/truncated tail is not complete-window evidence.
The source uses Python standard-library ctypes and the
[NVML query ABI](https://docs.nvidia.com/deploy/nvml-api/api/group__nvmlDeviceQueries.html).

Validation commands include `:radiance-audit:test :radiance-audit:benchmarkTest` and
`python -m unittest discover -s Modules/RadianceAudit/benchmark -p test_gpu_monitor.py`.
The measured cases and artifact boundaries are in the maintained performance research record;
machine evidence remains under the repository `run/high-distance-20260927/` and the corresponding
local artifact directory, not in the mod distribution.

### Isolated memory-lifecycle attribution

`classification: MEMORY_LIFECYCLE` with explicit `allowCompetingLoadForMemoryAttribution: true`
may admit unrelated CPU/GPU load only with the isolated marker, no other Minecraft client and at
least 8 GiB free GPU memory. This threshold is for the bounded model-city fixture, not a generic
memory guarantee for arbitrary worlds. An admitted exception is recorded as non-quiet and cannot
supply performance comparison results. The normal performance gate stays unchanged.

Optional diagnostic environment flags `RADIANCE_KEEP_SCENE_DESCRIPTORS_ON_LEAVE=1` and
`RADIANCE_KEEP_RECONSTRUCTION_ON_LEAVE=1` isolate ownership costs. Native overrides require the
isolated marker; ordinary/Prism clients release both by default. `RADIANCE_AUDIT_SMOKE=1` uses the
existing short title-only probe. Active probes remain restricted to isolated instances.

With `RADIANCE_CHUNK_CENSUS=1`, isolated world unload can export `vma-scene-release-*.json` if the
allocator has at most 10,000 allocations. Detailed device maps distinguish live allocations,
fragmented blocks and empty-block hysteresis; SDK/driver allocations outside VMA are excluded.

### Minimum-loader compatibility qualification

For bounded non-performance loader checks only, `classification: NEOFORGE_MINIMUM_COMPATIBILITY`
and explicit `allowModerateLoadForCompatibility: true` can accept moderate competing load. The
isolated marker, no other Minecraft, at least 8 GiB free VRAM, CPU at most 20%, and GPU at most
50% are mandatory; unavailable measurements reject this exception. The flag is default-off.
Every compatibility run is marked invalid for performance comparison, including runs which met
the ordinary quiet preflight. Do not pass their frame statistics off as benchmark results.
The performance and memory-only gates retain their original rules.
