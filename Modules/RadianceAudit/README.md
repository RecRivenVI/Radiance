# Radiance Audit

Optional client diagnostics maintained in the Radiance repository. The installed mod ID is
`radiance_audit`; Java packages use `com.radiance.audit`. Identity, author attribution, icon and
project links derive from Radiance's upstream metadata. The Audit-specific name and description
distinguish this maintained diagnostic module; this does not claim upstream shipped the module.

## Build and install

Use Java 21 and the root Gradle wrapper:

```powershell
.\gradlew.bat :radiance-audit:check :radiance-audit:jar -Pmcvr.configuration=RelWithDebInfo
```

Install the resulting `Modules/RadianceAudit/build/libs/RadianceAudit-<version>.jar` separately.
The main Radiance installation does not include Audit. Root project dependencies express Java
compile order; no previously generated `build/classes` directory is used as a dependency.
The Windows collector builds through CMake/Visual Studio and contains only first-party code.
Its C ABI header comes from the matched sibling MCVR checkout. Native collector PDBs remain in
the local build directory. Aftermath is a separate explicit local SDK build; no vendor SDK or
vendor crash-capture DLL is bundled here.

The root build and collector share `mcvr.root`, `mcvr.cmakeGenerator` and `mcvr.configuration`
(default `Release`). The generator uses the existing CMake cache when no override is supplied.
Tests write their working files under `build/test-runtime/`; logs and rotated logs at any module
depth are ignored. Necessary module sources and tests remain visible to Git.

## Modes and controls

- Default `basic`: passive session/route observation; no automated gameplay or native performance
  collector. Detailed draw, screen and chunk probes are opt-in.
- `-Dradiance.audit.mode=off`: no observer or diagnostic Mixins are installed.
- `-Dradiance.audit.chunks=true`, `-Dradiance.audit.drawDetails=true`, and
  `-Dradiance.audit.screenEffects=true`: enable their respective Java probes.
- `-Dradiance.audit.nativePerformance=true`: load the matched first-party collector and aggregate
  resource allocations and host-call timing. These are not whole-process VRAM or generated FPS.
  Allocations made before attachment are absent from per-source inventory; VMA totals include
  host-visible allocations. Reports distinguish overwritten snapshots and dropped events.
- `-Dradiance.audit.capture=true`: authorize existing explicit GPU readback request files.
  This does not automatically capture anything or enable early GPU fault tooling.
- `-Dradiance.audit.nativeLibrary=<absolute-path>`: optional local collector override, with ABI
  checks. Normally its content-addressed copy is extracted from this JAR.

Active experiments require **both** `-Dradiance.audit.experiments=true` and a file named
`.radiance-audit-test-instance` in the process working directory, which must be the isolated
game directory. Then select a single existing `RADIANCE_*` probe. Old environment flags alone
cannot trigger scripted block changes, camera movement or automatic exit. `RADIANCE_AUDIT_SMOKE=1`
checks title-screen readiness and requests normal exit after eight seconds. It is not world or
visual acceptance. Lifecycle G1/G2 additionally require their original one-shot trigger file.
Never enable active probes in the user's Prism instance or a production world.

`RADIANCE_LICHEN_PROBE=1` is a bounded isolated experiment: it places floor/wall/ceiling glow
lichen on black concrete near the copied test player's column at Y=192, teleports the test
camera, records baked/PBR vertices, requests three eight-frame GPU captures and screenshots,
and requests normal shutdown. It requires the experiment gate above and integrated singleplayer;
GPU readbacks additionally require `-Dradiance.audit.capture=true`. It edits the copied world
and does not restore the fixture. Never use this probe on a world that must be preserved.
The probe reports execution only; alpha, lighting and visual findings require separate analysis.

Ordinary installation does not clear OpenAL properties or change renderer options. Audit does
not create an OpenGL context. Without Radiance, only available vanilla observation paths apply;
Radiance-specific native features require the matched renderer.

## Frame profiling

For the separately pinned upstream 1.21.1 NeoForge and 1.21.4 Fabric packages, use the
[portable benchmark adapters and startup companion](benchmark/README.md), not this fork-specific
native collector. The [offline GL inventory](tools/README.md) enumerates artifact-scoped bytecode
calls and maintains evidence-backed translation/semantic statuses separately from performance runs.

With the matched Radiance/MCVR and this optional mod installed, run the **client command**
`/radianceaudit profile start 30` (1–120 seconds, default 30). `/radianceaudit profile stop`
ends it early. No JVM flags, cheats, server mod, graphics changes or experiment marker are needed.
Capturing is off by default. Commands remain local and do not modify the world or renderer options.

Results appear below the game directory in `radiance-audit/profiles/<UTC time>-pid<PID>/`:

- `REPORT.md`: readable stage tables and frame-time p50/p95/p99;
- `frames.csv`: real Java frame wall/CPU/interval durations, focus, world/menu/screen,
  framebuffer size, render distance and SDK real/generated-frame rate counters;
- `timings.csv`: correlated Java/native/GPU stage durations;
- `summary.csv`: inclusive and exclusive totals, sample distributions, Java wall-time shares;
- `METADATA.txt` and `STATUS.txt`: measurement contract, omitted/dropped data and completion.

Java wrappers time complete calls (including failures), subtract nested child durations, and cover
tick, world/render orchestration, entities, block entities, hand, particles/weather/debug geometry,
geometry marshaling, auxiliary textures, chunk scheduling, submission/presentation and acquisition.
The Java **exclusive** rows partition the measured `runTick` body. Native CPU rows drill into these
rows: conversion/copy, vertex packing, build-command recording, upload, world modules, queue calls,
fence/acquire waits and the renderer's frame limiter. Worker/no-owner rows run concurrently and are
reported separately. Entity capture includes model dispatch/material handling/vertex generation;
there are deliberately no per-vertex timers. It is not a measurement of transformation arithmetic
alone, and native command-recording duration is not GPU execution duration.

The detailed stages additionally separate the LevelRenderer body from its outer GameRenderer call,
camera picking/world uniforms, level setup/light updates, Flywheel dispatch, Sable
updates/single blocks/block entities, NeoForge render-stage callbacks, clouds, texture mapping and
world-mesh frame closure. Nested marshaling is subtracted from the caller's exclusive duration.
Native PT rows split instance/history/address metadata for entities/Flywheel/chunks, lock acquisition,
BLAS/TLAS build-command preparation, metadata allocation/copy versus upload recording, hit-group
lookup/SBT preparation, descriptor materialization, execution variables and pass recording. These
are host durations, including driver calls and waits, not isolated transformation arithmetic.
`pt.prepare.cleanup` includes temporary-container destruction and lock release. Parent inclusive
durations remain in `summary.csv`; never add them to their child rows. The native CPU per-render-frame
column divides totals by recorded Java frame count, unlike the per-invocation sample mean.

GPU queries bracket main-queue upload/world/UI/fuse buffers and individual world modules. Each
swapchain context owns its query pool; reuse follows its existing completed fence. Results are
read without a new wait and with availability bits. Readback-flushed, unsupported, unavailable and
capture-tail frames are omitted rather than recorded as zero. World-module intervals overlap the
world-buffer interval; GPU overlaps CPU. **Never add the three tables into one 100% chart.** These
are completion intervals, not isolated shader occupancy or exclusive GPU engine utilization.
Separate chunk queues, archived UI-PT commands, and SDK-owned internal FG submissions are not
separately timed. SDK rate counters are not RTSS/PresentMon display measurements.

Reference: [Vulkan timestamp queries](https://docs.vulkan.org/samples/latest/samples/api/timestamp_queries/README.html)
and [query result availability](https://docs.vulkan.org/refpages/latest/refpages/source/vkGetQueryPoolResults.html).
The optional native profile observer is a separate install-once ABI; no vendor library is added.
Only fixed-cost inactive checks and observer hooks remain in the product renderer. Aggregation,
CSV generation and the bounded background writer reside here, not in Radiance core.

Limits: 20,000 Java frames/capture; 8,192 native events between drains; 96 GPU spans/context/frame;
256 writer batches; 128 MiB raw CSV/capture; quantiles retain the first 20,000 samples per metric.
Overflow is counted. CPU tables include waits; render-thread CPU time and frame-start intervals
are separate columns. Profiling itself adds CPU/timestamp overhead, and setup/drain/reporting lie
outside the timed frame body but inside frame-start intervals. Do not compare focused/unfocused,
menu/world, different settings or unrelated scenes as a controlled performance result. Use raw
frame tokens when joining GPU/native data; Java/native clocks are never directly subtracted.
Windows thread CPU time can be coarsely quantized (15.625 ms on the validation host); its per-frame
percentiles are not substitutes for high-resolution wall-time percentiles. Check `STATUS.txt` for
output-cap omissions as well as queue overflow. Preserve the renderer settings alongside captures
when comparing scenes; the profiler never freezes or silently changes those settings for you.

For isolated automation only, `-Dradiance.audit.profileSeconds=30` starts once, 10 seconds after
world entry (`-Dradiance.audit.profileDelaySeconds=...` overrides delay). This does not enter a
world or stop the client; those actions still require the existing explicit isolated probe.
If the process terminates during capture, incomplete raw data is useful evidence but absence of
`STATUS.txt` means the report did not finish. No GPU-fault root cause or visual acceptance is implied.

Native work-volume observations are written separately to `counters.csv`
(`frame,stage,count,bytes`). `entity.format.*` counts the actual input vertices/bytes;
`entity.gpu-deferred`, `entity.gpu-upload` and `entity.gpu-output` describe the GPU conversion batch.
These are logical buffer payloads, not measured PCIe traffic, elapsed time or allocation peaks.
They must not enter duration summaries. `entity-convert-gpu` is a separate timestamp interval.

The optional `RADIANCE_FIXED_SCENE_ROUTE=1` experiment requires the standard experiments JVM
option and isolated markers. After 45 seconds it runs a 40-second, 5 Hz server-driven camera
fixture: an eight-block X excursion and +/-35-degree yaw sweep, returning to the original pose.
It records skipped targets and completion; it neither starts profiling nor exits the client.
Use identical fresh world copies and a capture long enough to include the whole route. This is
translation/rotation regression evidence, not natural walking, deterministic animation replay,
multiplayer/network acceptance or a visual verdict. Do not enable it in the user's Prism instance.

`chunk.scene.cache-hit`, `chunk.scene.cache-build` and `chunk.scene.custom-history` are per-frame
chunk snapshot work counts in `counters.csv`, not durations. For isolated correctness checks,
`MCVR_CHUNK_SCENE_VERIFY=1` compares the assembled chunk metadata against its live published source
and reports bounded periodic totals; it is off by default and must stay off in timed comparisons.
`MCVR_CHUNK_SCENE_CACHE=0` selects the per-geometry reference assembly after restart.

The existing `RADIANCE_HOTSPOT_REGRESSION=1` isolated probe additionally accepts
`RADIANCE_REGRESSION_SCREENSHOTS=1` for stage images and `RADIANCE_REGRESSION_SABLE=1` to assemble a
three-block iron/leaves/glass external fixture, then check delivery across resource/chunk reloads.
Both require experiment access and the isolated marker. The fixture refuses occupied positions;
its world changes are confined to disposable test copies. Screenshots and metadata comparisons do
not constitute user visual acceptance. Never enable these experiments in Prism.

## Dynamic producer and rigid-model experiments

`-Dradiance.audit.producerCensus=true` adds render-thread attribution during an existing frame
profile: actual entity/block-entity renderer, ModelPart and baked-model entry, source geometry,
material capture and submission candidates. `producers.csv` preserves parent and child rows;
child bytes/time overlap their parents. Exclusive timing subtracts instrumented child scopes,
not arbitrary uninstrumented work. Unknown/mixed owners remain explicit. Identity sets/rows are
bounded and the census avoids per-vertex callbacks, strings and locks. It is off by default and
must be off in formal low-overhead performance comparisons. Persistent draws are mapped through
their actual storage provider to the renderer, separately from the legacy submission queue. Missing
owners remain `unattributed/rigid-material`. `persistent_draws` and `persistent_vertices` count
references, not newly generated or uploaded geometry. Native persistent counters are global, not
per-renderer GPU measurements.

The experimental product switch is restart-only `-Dradiance.rigidModels=true` (default false).
`-Dradiance.audit.rigidVerify=true` separately samples local-model expansion against the original
PBR consumer, with exact material bytes and explicit position/normal tolerances. Its bounded dual
execution is a diagnostic, not a performance mode or proof of GPU hits/visual equivalence.

`-Dradiance.audit.rigidLifecycle=true` additionally requires experiment access, an isolated marker,
and a copied city with multiple item frames. It mutates one copied-world frame through rotation,
cutout/glass items, glint/glowing fallback, invisibility/empty state, resource reload, deletion and
replacement, then F3+A. Per-stage geometry reports and `RIGID_LIFECYCLE PASS/FAIL` are authoritative;
exit 0 alone is not a pass. Never use it on Prism/production data or in timed comparisons.

The second, separately opt-in product experiment is `-Dradiance.rigidParts=true`; it also requires
`radiance.rigidModels`. `-Dradiance.audit.partVerify=true` samples the actual original
`ModelPart.Cube.compile` against captured local parts. This is a CPU geometry/material comparison,
not proof of GPU hits or final visual parity. `-Dradiance.audit.partLifecycle=true` requires the
same isolated-instance experiment guards and an existing visible armor stand. It checks synchronized
pose, visible-part flags and equipment changes, glowing fallback state, reload, replacement, F3+A
and recipe retirement on disconnect. Its `PART_LIFECYCLE` result distinguishes target-owner
comparisons from the glowing wrapped-consumer stage, for which no fast-path equality is claimed.
Do not enable these mutation or dual-path probes in Prism or performance comparisons.

Native `entity.rigid.*`, `entity.dynamic.blas-inputs` and `world.tlas.instances` appear in
`counters.csv`: referenced/new vertices, resident models, actual recorded new-model BLAS commands,
dynamic build inputs and TLAS instances are distinct quantities. Bytes are known logical input/
upload ranges, not PCIe traffic measurements. `entity-blas-gpu` and `tlas-build-gpu` use the existing
nonblocking, fence-retired timestamp mechanism. Nested CPU and GPU durations cannot be summed into
a single frame percentage. A missing steady-state new-model event means zero only inside a complete,
non-dropped capture; it does not describe startup or all prior frames.

## Ownership and evidence boundaries

Submission profiling also separates `GEOMETRY_FACE_STATE`, argument-buffer allocation/free,
buffer/provider closing, and the real face-capture callbacks (`FACE_SETUP`, `FACE_CLEAR`,
`FACE_BACKUP`, `FACE_RESTORE`). These are nested timings, not additive frame percentages.
Profiling executes the original callbacks and does not cache a RenderType's face semantics.

`RADIANCE_MATERIAL_STATE_PROBE=1` extends the isolated hotspot regression with a 64x64 Vulkan
framebuffer readback before and after reload and at the final stage. It checks idle/active state
changes, a draw inside a real RenderType callback, face flags, exception restoration, fractional
alpha blending, depth rejection, scissor and color masks. Raw RGBA outputs are retained under
`radiance-audit/material-state/`; failed assertions produce `HOTSPOT_REGRESSION FAIL`, even when
the client subsequently exits normally. It requires experiment access and both isolated-instance
markers. Never enable it in timed profiles or the user's Prism instance. Restart-only
`MCVR_IDLE_UI_STATE=0` selects eager native state recording for same-artifact comparison; normal
operation defers GPU commands for idle raster state while preserving the immediately updated
shadow values and real callback execution. Bounded pixels do not establish complete visual parity.

Radiance retains the versioned `api.audit` observer boundary, read-only runtime identity, native
test primitives and real failure/save/close behavior. Observer exceptions detach the observer;
they cannot replace a renderer failure. Active experiment failures still propagate through the
real game failure path. The collector ABI is install-once and process-lived, uses no STL ownership
across DLLs, and pins its Windows module because resources may retire after game-world closure.

The Java ledger bounds open intents (8,192), detail length (4,096 characters) and per-session output
(64 MiB). Asynchronous open work survives frame boundaries. Dropped samples, output exhaustion,
unobserved routes and unresolved work at process end are different evidence states. Zero unknowns
does not prove coverage of unexecuted scenarios. Detailed historical scenario probes remain
experimental and may have their own output policies; the ledger limit is not a process-wide quota.

GPU-owned readback operations, early device-fault/Aftermath hooks and some
explicit native traces remain in MCVR. They are not claimed to have all moved into this JAR.
Early device diagnostics must be selected before SERVICE device creation; attaching this GAME
mod later cannot retroactively enable those extensions. Resource retirement and fatal handling
remain renderer responsibilities.

The one-shot native scene capture/replay system and its Java controller were retired after the
performance investigation. Existing benchmark evidence remains historical; this module no longer
provides that capture, replay or camera tool. Other explicit FG/GPU readback diagnostics remain.

Policy and implementation history: [documentation policy](../../docs/DOCUMENTATION_POLICY.md),
[development ledger](../../docs/DEVELOPMENT_LEDGER.md), [roadmap](../../docs/ROADMAP.md).
The old ignored `dev/radiance-audit` tree is retained historical source/build evidence, not a
second supported build entry point. Do not erase existing evidence during migration.
