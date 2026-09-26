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
See the [measured checkpoint](../../../docs/DEVELOPMENT_LEDGER.md#2026-09-25-matched-three-version-pressure-benchmarks).

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
