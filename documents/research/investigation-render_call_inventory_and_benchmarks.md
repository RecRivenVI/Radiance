# Render-call inventory and upstream benchmarks

迁移日期：2026-10-09；原始观察日期：各条目原日期。本次只迁移结构，不将历史结果提升为本轮验证。

## 问题

保留原调查的范围、证据等级与未完成事项。当前实现与批准的产品约束以[项目现状](../project/status-current.md)为索引。

## 方法

原始记录来自 Radiance 迁移前的已提交源码与对应证据。以下保留原文与日期，避免重新解释历史失败。

## 发现

Observed on: 2026-09-25.
Status: investigating; initial offline inventory and common benchmark adapters implemented.
Sources: the artifact identities below, the two repositories' Performance Optimization Test V1
checkpoints, exact installed bytecode/mappings, and the official references linked below.

## Questions and boundaries

The user requested a mechanical inventory of Minecraft/NeoForge/third-party GL drawing, including
translation and semantic changes, plus diagnostic adapters and disposable benchmark scenes for two
upstream packages. This is separate from the retired standalone MCVR replay and does not revive it.

Static enumeration can be complete for the declared input artifacts and recognized bytecode forms.
It cannot prove all runtime producers are reachable, all cancelled drawing was observed, arbitrary
native/reflective/generated calls were discovered, or translated pixels are equivalent. Keep
`UNKNOWN` rather than turning interceptor existence into a completion verdict.

## Fixed upstream references

| Reference | Identity and scope |
| --- | --- |
| 1.21.4 Windows | User explicitly selected the latest public **alpha**, not a Release-classified version: Fabric `0.1.5-alpha`, Modrinth version `Nbyczdf4`, published 2026-04-29. SHA-256 `FC4F36919809C584F922704F29150DA68202C7E6B4DF9E3D35274552C668C86E`. |
| 1.21.1 NeoForge | User-provided `Radiance-0.1.6-alpha-opengl-ui-neoforge-1.21.1.jar`. SHA-256 `A92AD47B966A6F656C6F28068DD7B69D61C0AFAAFFC3583DFD0294B631CB9E09`. Keep its OpenGL-UI behavior and embedded core intact. |
| Fork starting point | Radiance `ebd5038c1e55e06041946f6b5215f589bd496447`, MCVR `265d822e315cd10b864beb49ac5e89dfa0acca20`; initially clean. This tooling batch changes no renderer implementation. |

The local user JAR is at `D:/DownloadsQQ/Radiance-0.1.6-alpha-opengl-ui-neoforge-1.21.1.jar`
(non-portable evidence). The downloaded Fabric artifact and original API response are under
`D:/Workspaces/References/RadianceBenchmarks/` (non-portable). The original packages are not edited,
repacked or combined with a fork DLL.

Public references: [Modrinth version](https://modrinth.com/mod/radiance-mod-windows/version/Nbyczdf4),
[version API](https://api.modrinth.com/v2/version/Nbyczdf4),
[GLFW 3.4 window creation/focus](https://www.glfw.org/docs/3.4/window_guide.html),
[GLFW input modes and callbacks](https://www.glfw.org/docs/3.4/input_guide.html).

## Inventory design

ASM scans actual invocation instructions and method references, including nested JARs; class and
method descriptors plus artifact SHA-256 distinguish versions/overloads. Reverse symbolic edges
give investigation leads. Mixin annotations are reported alongside duplicate/multi-release classes,
native bodies and indirect calls. Direct-call enumeration does not resolve virtual dispatch.

A reviewed status must bind to the exact site fingerprint and carry evidence. Translation and
semantic preservation are independent dimensions. Changed artifacts invalidate old IDs. The
runtime complement is intent production, translated/skipped/unsupported disposition and frame-end
accounting in existing Audit; post-Mixin bytecode snapshots and pre-cancellation observation still
need integration. Do not add high-volume observers to a formal timing run.

## Benchmark design and observed pitfalls

The current fork Audit's RendererProxy/native collector is not ABI-compatible merely because an
upstream class has the same name. Common real-frame timing therefore uses a loader-neutral startup
companion and small version-specific installed adapters. Missing GPU/native/FG measurements are
unavailable, not zero. Preserve the richer current-fork Audit separately.

Both upstreams create their GLFW window from Java. Instrumenting startup is necessary before
NeoForge's SERVICE window; a late GAME Mixin cannot undo focus already stolen. Windowed unattended
mode sets no-focus hints before creation, filters local mouse/keyboard delivery and polling, avoids
cursor grab/warp, and leaves close/resize/event pumping intact. It does not control the desktop.
Native/custom input/window libraries remain an explicit boundary. The actual GLFW focus and
Minecraft cached-focus flag are different metrics; both preflights exposed this difference.

Use fresh fixed-seed flat worlds with a deterministic recipe, explicit warmup and raw real-frame
samples. Vanilla geometry recipes are shared across versions; only 1.21.1 gets Create/Flywheel and
Sable/Aeronautics. Source-version differences make 1.21.4 a reference, not causal proof of fork
speedup. The main causal comparison should be upstream/fork 1.21.1 with identical content/settings.

Correction, 2026-09-25 follow-up: even same-version upstream/fork runs with matched world/settings
compare whole products, not an isolated optimization. Until actual geometry/pass coverage and
shader semantics are matched, they must not be used to assign a measured difference to Test V1.

Preflight discovered NGX/DLSS initialization being skipped, retained upstream VSync, different chunk
thread/batch defaults and different emission-collection defaults. These are configuration/capability
differences to resolve before formal timing, not speedups. Verify actual framebuffer size, active
modules, FPS caps, focus/occlusion, backend submission coverage, load completion and diagnostic
overhead. Saved motor speed/slot counts establish fixture state, not visible equivalence or actual
Flywheel/PT submission. No GPU-loss, licensing or visual-acceptance gate is closed by a short run.

## Evidence and next gate

See the implementation/preflight record（归档位置：`D:\Workspaces\Artifacts\Radiance\history-archive\Radiance\docs\DEVELOPMENT_LEDGER.md#2026-09-25-opengl-inventory-and-portable-upstream-benchmark-preflights`）,
inventory usage（迁移前导出：`D:\Workspaces\Artifacts\Radiance\migration-20261009-1800\before\Radiance\Modules\RadianceAudit\tools\README.md`）, and
benchmark usage and limitations（迁移前导出：`D:\Workspaces\Artifacts\Radiance\migration-20261009-1800\before\Radiance\Modules\RadianceAudit\benchmark\README.md`）.
The next measurement gate is effective-settings parity and repeated same-version A/B, with an
independent vanilla cross-version table. No performance ranking is established by the preflights.

## 2026-09-25 follow-up: larger workloads and effective configuration

Status: implemented; automated-verified; runtime-observed in ten bounded measured processes.
The matched pressure checkpoint（归档位置：`D:\Workspaces\Artifacts\Radiance\history-archive\Radiance\docs\DEVELOPMENT_LEDGER.md#2026-09-25-matched-three-version-pressure-benchmarks`）
supersedes the preceding *next measurement* status, while preserving the original preflight facts.
The offline GL review/classification scope is unchanged.

The suite now separates a common vanilla model district (1,024 block/entity rendering producers)
from a 1.21.1-only factory with Create and Sable. Deterministic tier-2 and terrain-city recipes also
exist but were not run. Fresh worlds and fixed camera replace progressive edits to the same save.
The user declared the host ready before formal timing; no other builds ran during the series.

Actual framebuffer dimensions, GLFW focus/minimization, live Java pipeline/options, loaded core and
NVIDIA runtimes, saved camera and workload contents are checked. Common settings are Advanced PT,
RR Balanced, four bounces, SHARC/jitter, 2560x1440, view/simulation 16/5, VSync off, cap 260, FG/Reflex
off and identical NVIDIA runtime DLLs. The upstream install directory was missing those optional
DLLs in the original preflight; local provisioning follows upstream's external `radiance/` runtime
convention, without changing its JAR or core. Notices are retained locally; this grants no public
redistribution clearance. Fixed Java 21 and heap limits are shared.

Two incorrect setup preflights were rejected rather than counted: NeoForge silently selected
Vanilla PT when Advanced was requested with emission collection disabled; Fabric uses `ray_tracing`
instead of NeoForge's `main_render`, so the wrong attribute owner left its pack path at default.
The corrected suite enables emission collection and observes each artifact's real module names.

Exact hidden upstream RR model selection, identical draw/culling coverage, completed mesh queues,
GPU substage time, input guides and visual equality are not established by these gates. The same
low-volume observer is present in all runs, but its overhead has not been isolated. GPU clocks are
recorded and allowed to follow normal driver policy; they are not artificially locked. Native/SDK
versions remain those of each product. Read the measured table as bounded application frame-loop
behavior under matched requested conditions, not proof of an equally rendered-work speedup.

## 2026-09-25 correction: internal shader workload and upstream implementation

Status: investigating. Evidence: static inspection of the actual packages and retained live
settings from all ten formal processes; no new build, performance run or visual acceptance.
This corrects the preceding effective-configuration claim and the
pressure checkpoint（归档位置：`D:\Workspaces\Artifacts\Radiance\history-archive\Radiance\docs\DEVELOPMENT_LEDGER.md#2026-09-25-matched-three-version-pressure-benchmarks`）.
The raw timings and original evidence are retained; they are whole-product measurements with
different shader workloads, **not matched-quality performance measurements**.

### Missed workload differences

The observer recorded `option.rayBounces=4` in every process, but the actual Advanced module has
its own `num_ray_bounces` attribute. The setup gate did not compare all internal shader attributes.
All four upstream NeoForge samples used `main_render`; all fork and Fabric samples used
`ray_tracing`. Their saved attributes map to the following bundled shader definitions:

| Attribute | Upstream NeoForge 0.1.6 | Fork Test V1 | Upstream Fabric 0.1.5 |
| --- | --- | --- | --- |
| Path bounce budget | 3 | 4 | 4 |
| ReSTIR initial candidate samples | 8 | 32 | 32 |
| ReSTIR spatial reuse samples | 2 | 4 | 4 |

These are sample counts within different algorithms, not a direct ratio of total rays or cost.
Other differences include disocclusion budgets, reuse radius/confidence, lighting strengths and
volumetric sampling. Matching only Advanced, RR Balanced and global options did not normalize
them. This investigation has not captured the driver's compiled shader binary; the evidence is
live module attributes plus their packaged configuration/GLSL consumers.

The upstream `quality_tier=low` sets ReSTIR/froxel resolution preferences, but the RR path overrides
ReSTIR resolution to quality mode (`lighting/restir/resources.glsl`) and the froxel size expressions
also select the larger dimensions for RR. Half-rate indirect checkerboarding is guarded by
`MCVR_USE_NRD` (`path/indirect/lobes.glsl`). It must **not** be cited as an active RR-benchmark speedup.
The 43.45 versus 26.56 FPS model-city result and 42.32 versus 30.37 FPS factory result remain real
observations, but cannot establish a same-work speedup or assign a contribution to Test V1.

### Concrete implementation differences

The source of truth is the supplied NeoForge JAR
`A92AD47B966A6F656C6F28068DD7B69D61C0AFAAFFC3583DFD0294B631CB9E09`, compared with fork JAR
`89FDB75B828F06B565133E8818D0DCF74A0D00B2CCACF06080E2EFD6D564BA63` and its matching current
product sources at Radiance `ebd5038c1e55e06041946f6b5215f589bd496447` / MCVR
`265d822e315cd10b864beb49ac5e89dfa0acca20`. Java observations use extracted bytecode decompiled
with Vineflower 1.12.0; shader observations use original bundled text.

| Area | Evidence in the supplied 0.1.6 package | Interpretation and limits |
| --- | --- | --- |
| Compact vertices | `PBRVertexFormats` and `PBRVertexConsumer` distinguish 64-byte entity and 40-byte chunk PBR records; the fork's ordinary PBR record remains 128 bytes. | 50% / 68.75% smaller source records, not equivalent reductions in whole-frame bytes or time. Positions and main texture UVs remain float32; normals use octahedral packing, color uses RGBA8 and glint UVs use half floats. Precision/semantic compatibility needs separate validation before adoption. |
| Transfer of buffer ownership | `TransferredBuildBuffer.takeEntity/takeChunk` detaches the allocator, keeps its `MeshData` alive and sends the ownership token alongside the original address to `queueBuildNative` / `rebuildSingleNative`; failed submission closes the tokens. | This is an explicit Java-to-native ownership path, not merely a renamed pointer getter. The fork already obtains direct addresses, but its ordinary native queue path still copies the PBR source into retained storage before later conversion. The unpublished matching native implementation prevents proving complete end-to-end zero-copy or its lifetime correctness. |
| Compacted ray queues | `path/indirect/classify.comp` groups active diffuse/specular work, `queue.glsl` reserves/writes compact pixel lists, and separate ray-generation shaders consume those lists through `indirect_args` in `configs.json`. | This changes GPU work organization. The fork dispatches its continuation/final world shaders over image extents with internal conditions. Better occupancy or fewer inactive invocations are plausible benefits, not measured per-feature gains. It is not evidence of SER or PTLAS integration. |
| Separate discovery and material resolution | The packaged Advanced graph separates `primary_discover`, hand merge and `primary_resolve`, followed by dedicated transmission, direct-light and indirect stages. | A substantially different shader pipeline, so pass names and counts cannot establish equal work or predict the total benefit. |
| Volumetric lighting grid | Direct and indirect lighting use 3D froxel resources, filtering and integration; RR selects 256x128x64 and 128x64x32 shading grids. | Reuse in a volume grid is a different method from the fork's screen-space volumetric-light sampling. Visual quality, memory and performance are not established as equivalent. |

This is not evidence that upstream eliminated all per-frame Java model expansion: its inspected
entity path still invokes renderers and assembles dynamic batches. The fork's persistent baked
model prototype and pooled metadata remain distinct work. Do not count the existing direct-address
`BufferProxy` path or scheduling/backpressure already present in the fork as wholly new upstream
optimizations.

### Differences that cannot be treated as interchangeable optimizations

Upstream `WorldRendererMixins.shouldRenderEntity` retains entities within 48 blocks, otherwise
delegating to vanilla dispatcher visibility/distance checks, with passenger and section-related
conditions in the caller. The fork's corresponding loop keeps loaded entities except confirmed
Flywheel duplicate visuals and uses unbounded world event frusta. The actual number of omitted
benchmark entities was not measured. Copying the upstream culling rule would conflict with the
fork's requirement that off-camera geometry can participate in reflection, shadow and indirect
queries.

Upstream also defines raster post passes for particles, weather, world overlays, text and name
tags, with corresponding Java routing for selected producers; the supplied build retains OpenGL
UI. The fork's PT/priority/Vulkan-UI semantics differ. This is evidence of different producer/pass
contracts, not proof that every such producer was active or absent in the timed scenes. No visual
parity, complete geometry submission parity or per-pass timing was obtained here.

### Source availability, evidence and next comparison

The queried public branch tips did not provide a source match for this user-supplied 0.1.6 package.
The public main snapshots were still
[Radiance 414d8e3](https://github.com/Minecraft-Radiance/Radiance/tree/414d8e330a2fc6cb1e8630cc95f2302b2b97a0e8)
and [MCVR 9905c81](https://github.com/Minecraft-Radiance/MCVR/tree/9905c81b1999f5845bf66d13501d371c16adf561).
The package contains no checked Git-identity metadata. Its newer archive timestamps alone cannot
identify a source commit or establish when each optimization was introduced. Native C++ internals
and their exact contribution remain unverified.

Non-portable evidence root:
`D:/Workspaces/Repositories/GitHub/RecRivenVI/Radiance/run/upstream-implementation-20260925/`.
`FINDINGS.json` records original artifact/Advanced ZIP identities, all ten settings-file hashes,
raw-byte hashes of inspected extracted/decompiled files and public branch-response locations.
The extracted/decompiled trees are diagnostic evidence, not source intended for redistribution.

Before another comparison, match the actual module-owned bounce/sampling controls and enumerate
remaining algorithm and coverage differences; equal numbers alone will not make different
integrators visually equivalent. Then measure CPU generation/copies and GPU stages independently.
Compact records and explicit ownership transfer are bounded implementation candidates; adopting
the entire new shader pipeline is a separate product/quality decision. No implementation or new
timed experiment is authorized merely by this research conclusion.

## 2026-09-25 follow-up: authorized explicit-control rerun

Status: automated-verified and runtime-observed; not visual/work equivalence. After the user
authorized configuration alignment and new timing, the
explicit shader-control checkpoint（归档位置：`D:\Workspaces\Artifacts\Radiance\history-archive\Radiance\docs\DEVELOPMENT_LEDGER.md#2026-09-25-repeated-comparison-with-explicit-shader-controls`）
completed ten new formal samples with all exposed pack attributes pinned and read back. The
pack-owned settings path, rather than only `pipeline.yaml`, is required to make the newer upstream
values effective; an initial rejected preflight preserves that discovery.

The 4/32/4 controls and shared radius/confidence/light/cloud inputs now match. Model-city means are
43.77 FPS upstream Neo, 29.75 FPS upstream Fabric and 26.32 FPS fork; factory means are 43.08 and
30.81 FPS. Thus the gap persists after exposed-control alignment. This is not per-optimization
attribution: the algorithm, culling/producer coverage, target-only settings and native/SDK limits
identified above remain. Original binaries/shaders were neither rebuilt nor edited. The linked
ledger contains repeat spread, percentiles, telemetry scope, hashes and evidence paths; the old
timings remain historical unmatched-input observations.

## 2026-09-25 follow-up: expanded upstream 1.21.1 optimization inventory

Status: static artifact inspection. This extends the inventory for the same user-supplied
`A92AD47B...` NeoForge 0.1.6 JAR; it is not a claim about an unidentified newer development build,
the introduction date of each mechanism, or matching unpublished native source. No new client,
build or performance experiment ran for this follow-up. Existing dirty benchmark work was retained.

The five mechanisms above remain the largest directly observed differences: compact PBR records,
explicit buffer-ownership transfer, compact diffuse/specular dispatch, primary discovery/resolution
separation, and froxel lighting. The following additional paths were traced to their callers:

| Mechanism | Actual producer/consumer evidence | Relationship to the fork and limits |
| --- | --- | --- |
| Nonempty block-entity section index | `BlockEntitySectionCache.changed/sections` maintains a `BitSet` and cached section array. `EntityProxy.queueBlockEntitiesRebuild` consumes that array; changing the section array rebuilds the index. Invalid section indexing falls back to the source array. | Avoids scanning every empty section each frame. The fork already has `ChunkProxy.blockEntitySections` / `SectionPresenceIndex`, including compiled-owner validation. This is overlapping work, not a newly missing fork feature. |
| Bounded and prioritized chunk preparation | `ChunkProxy.rebuild` calculates normal/important in-flight capacity and per-frame candidate budgets before `submitRebuild` creates a region. A dedicated important worker, normal workers, nearby-coverage reservations and bounded background scans organize admission. | The fork already has admission, interaction priorities, generations and external-section backpressure. Upstream ordinarily admits normal work every third frame outside its coverage-boost period; this may trade loading throughput for frame smoothness, not improve both unconditionally. Native publication correctness and actual throughput were not tested here. |
| Reused worker scratch | Per-thread `SectionBufferBuilderPack` and candidate-index scratch reuse allocations; the worker count is bounded by the configured count and an estimated heap allowance. | A concrete allocation/pressure measure, not evidence that every per-frame allocation is eliminated or that increasing thread count explains the measured gap. |
| Persistent Sable section geometry | `SableBlockGeometry` extends `EntityProxy.BlockGeometrySource`. Bounds allocate section identities, dirty sections call `updatePrebuiltGeometryNative`, and submission references the existing geometry with the current transform. Its compile callback requests a budget of eight sections. | Rigid motion does not recompile all block vertices at the Java level. Block entities inside those sections still run their renderers. The fork already retains external-section geometry and updates transforms separately; native BLAS behavior in this artifact is not proven from the handle name alone. |
| Flywheel model/program/submission reuse | `FlywheelEngine` caches `FlywheelModel` by model identity and `FlywheelProgram` by type/material/embedding; groups and handles cache prepared mesh submissions. Model construction registers geometry once. Embedding matrices are computed once per engine frame, and the instance-record buffer grows geometrically and is reused. | The fork already shares Flywheel models and tracks dirty instances. Upstream still walks active handles and serializes instance records each frame, so this is not proof of an entirely incremental scene table. Unsupported owners keep ordinary rendering when available. |
| Flywheel vertex-program bridge | `FlywheelProgram` assembles instance/material vertex GLSL and registers it through native interfaces; `queueVertexShaderInstancesNative` receives model/program references, instance bytes and uniforms. | Confirms the shader/program input contract rather than per-instance Java mesh expansion. The native evaluation, dispatch, AS update and synchronization implementation remain unavailable; no GPU-cost benefit is asserted solely from this Java interface. |

The supplied graph also requests `acceleration_structure_build_mode=deferred` and declares a
SHARC asynchronous group. These are configuration/dispatch contracts, not proof of actual queue
overlap or a measured faster native AS implementation. SHARC/ReSTIR themselves already exist in
the fork. The declared NRD half-rate checkerboard mode does not explain the RR benchmark. Vista
classes and a nullable source hook exist, but this inspection did not establish an active Vista
provider in the measured scenes; they are not credited with a performance gain.

The upstream ordinary entity/block-entity paths still invoke renderers and assemble dynamic
batches, including fresh metadata allocations. No evidence here supports claiming that all
ordinary models became persistent, all scene metadata became GPU-resident, or that this package
uses SER/PTLAS. Do not infer native optimizations from DLL size, export names or pass labels.

Expanded evidence is in the non-portable directory
`D:/Workspaces/Repositories/GitHub/RecRivenVI/Radiance/run/upstream-implementation-20260925/expanded-inventory/`.
`EVIDENCE.json` records the original JAR, the previous findings index, derived class/Java hashes and
the directly compared fork files. The original `FINDINGS.json` and its indexed evidence were not
rewritten. Decompilation is evidence, not a redistributable source import or a complete native audit.

For future bounded experiments, compact layout and ownership transfer are the clearest new
CPU/data-path candidates; active ray queues are a separate GPU candidate requiring stage timing
and quality checks. Existing section/Flywheel caches should be compared for concrete remaining
differences rather than reimplemented by name. Visibility pruning, raster routing, precision
changes and volumetric algorithms require independent product/correctness decisions. The observed
43.77 vs 26.32 FPS model-city and 43.08 vs 30.81 FPS factory results remain aggregate aligned-control
observations, not a decomposition of these mechanisms' benefits.

## 2026-09-25: Full-scene PT optimization impact and implementation plan

Status: proposed; static assessment against Radiance `ebd5038c1e55e06041946f6b5215f589bd496447`
and MCVR `265d822e315cd10b864beb49ac5e89dfa0acca20`, with the existing dirty benchmark tooling
retained. The user requested assessment and an implementation plan, not a product change in this
batch. No build, GPU test, client launch or new performance measurement was performed.

### Product constraints and candidate decisions

The user reaffirmed physical rendering semantics, complete scene geometry participation and no
post-raster replacement for world rendering. Preserve legitimate source face rules, internal-face
elimination and first-person/priority ray semantics; complete participation does not mean forcing
every surface double-sided. Preserve off-camera reflection, refraction, shadow and indirect paths.
This does not retire existing GUI raster support or reactivate archived Ponder PT.

| Candidate | Impact/risk | Decision |
| --- | --- | --- |
| Source-buffer ownership transfer | Same geometry and arithmetic can eliminate one retained host copy, but delayed allocator release increases live memory and introduces cancellation/exception ownership obligations. | First bounded prototype on the existing eligible dynamic GPU-conversion path. |
| Lossless source layout | Pack discrete modes and preserve original bounded integer channels; changes Java/native/shader ABI and can reduce generated/retained/uploaded bytes. Conversion overhead may offset bandwidth savings. | Second independent prototype, preserving all floating-point precision and the final GPU material representation initially. |
| Compact active ray dispatch | Can remove inactive invocations without removing geometry or changing the estimator. Requires exact eligibility, stable pixel/path identity, output initialization and compute-to-indirect synchronization; added classification/storage may cost more at high occupancy. | Third, GPU-timing-gated prototype on existing continuation passes, not an import of the upstream integrator. |
| Block-entity index, Sable geometry and Flywheel model reuse | Equivalent categories already exist locally. Upstream's implementation has different lifecycle and per-frame serialization details. | Retain local work; only adopt separately measured missing substeps, not wholesale replacement. |
| Upstream frame-count chunk throttling | May smooth busy frames while slowing loading and interaction at low real FPS. It does not address empty-queue steady-state cost. | Do not copy the every-third-frame rule or replace existing fairness/interaction scheduling in this effort. |
| Octahedral normal / half-float UV compression | Changes normal or texture-coordinate precision, potentially affecting grazing reflections, PBR, cutout edges and glint. | Excluded from the lossless plan; upstream's 40/64-byte strides are not targets to meet at any cost. |
| Primary-discovery/material-resolve split | May avoid repeated material work but crosses hand, transparency, temporal-guide and hit-group contracts. | Defer a full split. If needed for a queue prototype, extract only demonstrably identical prepared data and retain PT hand semantics. |
| Froxel volume lighting | Changes spatial integration, filtering and temporal approximation; may leak or blur lighting and increases 3D resources. | Separate rendering-quality investigation, not an equivalent optimization in this plan. |
| Deferred AS / asynchronous SHARC | Package declarations do not establish actual native implementation or queue overlap. More overlap can increase residency and synchronization risk. | No speculative port without matching native evidence and local bottleneck measurements. |
| Visibility pruning / world post-raster routing | Changes geometry participation or light transport. | Rejected under the user's contract. No permanent quality reduction, lower sampling, slower offscreen animation or shorter trace distance. |

### Stage 0: freeze comparable inputs and measure the affected work

Reuse Audit and the aligned model-city/factory fixtures: 2560x1440, view/simulation 16/5,
Advanced + RR Balanced, exposed 4/32/4 controls, fixed SHARC/jitter/emission inputs, FG/Reflex/VSync
off, the same mod lists, world starting copies, camera, entities and focus behavior. The preceding
37.993/32.457 ms fork means are historical context, not a substituted new baseline. Preserve the
existing observer/harness identity and separately measure observer overhead when adding counters.
Use a small vanilla/material fixture and Vanilla PT for correctness, not a mixed aggregate score.

Measure actual copied bytes, live source/staging bytes, source allocation capacity, pending owners,
CPU capture/copy/packing, dynamic GPU conversion, BLAS/TLAS and affected PT passes. Reuse delayed
GPU timestamp retrieval; never stall each frame to read timing. Count active continuation pixels
before committing to dispatch compaction. Do not attribute whole upstream/fork timing differences
to these individual candidates or to render-thread Java alone.

### Stage 1: explicit source ownership, unchanged 128-byte layout

Confirmed path: Java `EntityProxy.queueBuildInternal` closes `MeshData` and its storage in `finally`.
Native `Entities::queueBuild` copies eligible PBR input to `EntityRawGeometry::words`, then
`EntityGpuConversion` copies those words into its owned staging input. The first host copy is the
specific target; the staging transfer and GPU work are not claimed to disappear.

Proposed ownership design:

1. Detach only a sealed, uniquely owned `MeshData` plus allocator from
   `StorageVertexConsumerProvider`. Represent it with a non-reused submission/generation token
   and address/length/format; no allocator reuse or writes while leased. Keep small metadata and
   names copied by native as today. `queueBuildWithoutClose` and external/native callers without
   an explicit lease retain the current safe copy path.
2. Use explicit pending/accepted/rejected/completed receipts. Native acquisition and registration
   are transactional; a partial submission failure must either undo all borrowed references or
   publish completion for adopted tokens. A thrown JNI call alone is not permission to free a
   potentially adopted source. Java keeps the owner registry until the corresponding receipt.
3. Native holds an immutable source view through the last host consumer, including staging fill;
   cancellation, stale generation and build failure release it exactly once. Completion tokens
   return through a bounded thread-safe queue, drained by the owning Java thread. Avoid Java calls
   or cached `JNIEnv*` use from arbitrary native destructors/workers. Cleanup/drain remains callable
   after sticky fatal, without allowing ordinary rendering to resume.
4. World switch, reload and close stop acceptance, finish/cancel host readers, drain every owner,
   then close allocators. Source release after host copying is independent of GPU input/output,
   descriptors and AS retirement, which keep their existing frame/fence lifetimes. No GPU idle per
   release. Capacity is bounded by outstanding bytes as well as tokens; fall back to the existing
   copy path before admission if the lease budget is exhausted, never drop geometry.
5. First cover the real `rawEntityConversionEligible` path only. Its existing exclusions for
   external meshes, explicit indices, eyes, cached/prebuilt and post inputs remain correct legacy
   paths. Chunk conversion currently mutates owned CPU geometry and has worker-scoped allocators;
   do not extend read-only borrowing there without a separate consumer/lifetime proof.

Implementation footprint: Radiance storage provider, a small lease/receipt helper and EntityProxy;
MCVR entity JNI adapter, `EntityRawGeometry`, `Entities` and `EntityGpuConversion`, plus shutdown
drain integration. Keep texture generations, material face capture, topology, history and source
order unchanged. Account for retained allocator capacity, which can exceed used vertex bytes.

Tests must call the real submission seam: accepted delayed reads after Java submission, rejected
and partially accepted batches, failure while filling staging, stale-world cancellation, allocator
reuse attempts, repeated close/fatal drain, bounded capacity fallback and multiple geometries sharing
an owner. Assert exact bytes/output parity, balanced release counts and unchanged GPU retirement.
Compare legacy/candidate alternately before changing the vertex ABI. A lower copy count without a
repeatable total-frame or memory benefit is not approval to broaden the ownership mechanism.

### Stage 2: a versioned lossless source ABI

`PBRVertexFormats`, `PBRVertexFormatElements`, `PBRVertexConsumer`, native `PBRVertex` and the
conversion shader currently agree on 128 bytes. Native conversion explicitly asserts offsets;
rigid-model and ModelPart cache sizing also embeds this stride. A global stride substitution would
break more than the ordinary queue.

Define a new source format ID and explicit stride/layout version, retaining a canonical legacy
decoder. First compact only bounded discrete fields. `useColorLayer` includes multiply and surface
mix modes, `useGlint` is a mode, and alpha/coordinate semantics are not booleans. Preserve full texture
identities and the floating emission bit representation. Keep float32 position, normal, primary UV,
glint UV and post-base values; do not silently remove fields just because one producer leaves them
constant. Keep overlay/light integers at their current range unless all eligible producers prove
a narrower range; unknown custom values select a lossless extension/legacy representation.

RGBA8 is eligible only at a producer whose original channels are proven 0..255 integers. The
current setter divides unrestricted integers by 255.0f; narrowing all callers would be a semantic
change. Float-valued/custom or out-of-range producers retain their original values. Decoding must
reconstruct the existing floating value exactly for eligible channels; test all 256 values and
use an exact conversion table if shader arithmetic would round differently. Do not quantize an
already generated float stream to guess its provenance. No guaranteed final stride is claimed yet.

Initially decode the new source into the existing final position/material/index layout, leaving
ray material consumers and BLAS input semantics unchanged. Select the format before generation;
do not generate 128 bytes and add another full repacking pass as the final optimization. Preserve
rigid/ModelPart/Simulated/Veil and GUI consumers through explicit format capability or a correct
legacy path. Unknown version/stride/length fails before asynchronous use; a pair mismatch cannot
reinterpret memory. Expand to chunks only after the dynamic path has standalone evidence.

Tests cover producer-to-JNI-to-CPU/GPU conversion with mixed formats and all field modes: cutout,
alpha coverage, physical transmission, coating tint/hurt color, overlay/glint/emission, mirrored
and single-sided geometry, unusual UV/normal/light values, reload and reused identities. Compare
decoded fields, indices, material IDs and hit results; final RGB averages alone are insufficient.
Measure Java generation/packing, source/staging/upload bytes, GPU decode cost and total frame time.

### Stage 3: compact continuation work without changing light transport

Start with current Advanced `cont_reflection` / `cont_refraction`, retaining `final_compose` and
the existing integrator. Classification must reproduce the current per-pixel continuation decision,
including nested transparent state; material names, roughness thresholds or main-camera geometry
visibility are not substitutes. All spawned rays still query the same complete TLAS. If equivalent
classification needs expensive duplicated preparation, measure it before expanding the design.

Local prerequisites are real: `vk::CommandBuffer::raytracing` currently calls only
`vkCmdTraceRaysKHR`; `Device` queries ray-tracing features but enables only `rayTracingPipeline`,
not `rayTracingPipelineTraceRaysIndirect`. The shared SERVICE-created device must enable a queried
supported feature before handoff. Unsupported devices retain the direct dispatch with the same
rendering semantics. The loader/runtime resource contract needs explicit indirect argument usage.
See the [Khronos indirect trace contract](https://docs.vulkan.org/refpages/latest/refpages/source/vkCmdTraceRaysIndirectKHR.html).

Queue entries carry original pixel identity; view dimensions remain the original render extent.
Audit all executed ray-generation/hit/miss/helper uses of `gl_LaunchIDEXT` and `gl_LaunchSizeEXT`:
the current code uses them for random seeds, camera rays and primary/secondary cache writes.
Preserve pixel/frame/bounce random streams, sampling PDFs, roulette and history identity independently
of atomic queue order. Preserve exactly the inactive-pixel output initialization now performed at
the start of `world.rgen`; skipping those stores would expose stale reflection/refraction data.
The first prototype must not reorder SHARC updates or other shared accumulation as a side effect.

Allocate bounded per-view/per-in-flight argument/list resources. Prove maximum entry counts;
two one-entry-per-pixel uint32 lists alone cost `8 * width * height` bytes per in-flight view at
the actual internal render size, before argument buffers and other working state. Retaining full
capacity across several views/frames must be included in the VRAM comparison.
overflow must never discard rays (use a safe full-pass decision before partial output if needed).
Handle zero work, odd dimensions, dispatch limits, resizing and reload. Synchronize compute writes
to indirect-command reads and ray-shader list reads, and retire immutable descriptors/lists with
their actual submission. No per-frame CPU count readback or global idle. Validate the actual
feature, usage, alignment, limits and dependencies rather than relying on a general memory barrier.

GPU fixtures use zero/full/sparse masks and alternating masks across frames; assert every required
pixel is visited once, no unwanted pixel is traced, output defaults match and fixed-seed paths
preserve pixel identities. Include off-camera colored reflectors/shadow casters, layered glass,
water, cutout/emissive sheets, negative scale and temporal guides. A queue can be mathematically
correct yet slower on dense work; retain the direct path until independent timing supports use.

### Measurement and rollout gates

Freeze separate snapshots: baseline, ownership-only, layout-only atop the accepted ownership
checkpoint, then queue-only. Use alternating fresh-process runs with identical fixture copies,
prewarm criteria, settings, focus and renderer/diagnostic hashes; collect at least three samples
per side and retain repeat spread, real frame mean/p50/p95/p99 and raw samples. Static and fixed
motion results stay separate from loading/interaction tests. Do not sum savings from different
stages or count generated FPS as real rendering throughput.

Record copied/generated/uploaded bytes, lease/allocator/staging peaks, BLAS/TLAS counts and GPU
phase times alongside total-frame cost. Device-wide memory telemetry is not process-only or
SDK-complete allocation attribution. Adopt a stage only after correctness gates and repeatable
net benefit beyond run variation, with no unacceptable latency or residency regression. Do not
chase the upstream FPS by changing scene coverage, resolution, sample counts or producer routing.

Automated diagnostics use repository run directories and never seize desktop input. User visual
acceptance uses the already authorized Prism deployment workflow only after a matched package is
prepared and the client is closed. Natural device loss stops that case and preserves evidence;
do not automatically retry or turn a timing experiment into another fault campaign. Existing
GPU-loss uncertainty and public-runtime licensing gates remain independent. No product is claimed
implemented or validated by this planning document.

### Implementation decision correction: direct native-owned staging

On 2026-09-25 the user authorized batches 1 and 2, with batch 3 evaluation only after their
validation. Initial static/profile evidence changed the batch-1 mechanism, not the product goal.
Many Java capture providers reserve 786432 bytes each; keeping entire submitted providers across
JNI could retain hundreds of MiB to borrow approximately 17.3 MB of useful vertices per frame.
A 64 MiB owner budget would reject those batches rather than demonstrate the intended optimization.

The chosen implementation therefore copies eligible source spans directly into one native-owned
staging input per submission **before JNI returns**. Final conversion groups jobs by that input,
preserving global output offsets in original geometry order and ordinary frame retirement. This
removes the intermediate native vector copy without a Java/native lease registry, worker callbacks
or a new shutdown protocol. Each immutable source is uploaded once; failure before publication
must not leave caller spans in a persistent batch. Grouped descriptor/dispatch overhead is measured,
not assumed free. The earlier lease design above is retained as rejected design history.

The second batch starts with a versioned 100-byte source record: all existing float/int payload
fields remain exact; only discrete modes are combined. It does not apply the upstream's normal,
UV or color quantization. Both experiments retain independent opt-in controls and legacy paths
during comparison. Root is implementing and validating; no performance success or visual acceptance
is asserted by this decision correction.

Initial muted baseline evidence: `run/fullscene-input-20260925/` in the Radiance checkout. The
native-profile model sample has 626 frames with no observer drops: 135312 deferred vertices and
17319936 source bytes per frame, source-format processing 1.640 ms/frame (includes non-copy work),
staging fill 1.086 ms/frame, GPU conversion 0.052 ms/invocation and main-queue GPU interval
13.268 ms/invocation. Separate unprofiled fresh model/factory processes observed 38.750/32.863 ms
mean real frame intervals and saved/exited normally. These initial samples do not replace the
subsequent alternating same-artifact comparisons required for adoption.

### Implemented experiment and evidence boundaries

The paired worktree implements two independent JVM-start experiments:
`radiance.directEntityInput` and `radiance.compactVertices`. Both are initially off while adoption
is measured. Ordinary renderer submissions use `queueBuildSourcesV1` with exact per-geometry byte
lengths whether or not either optimization is enabled. The old native entry remains compatible
with the baseline artifact; internal/external legacy tasks retain their existing contract.

Direct mode stages eligible sources synchronously before Java's existing `finally` closes its
mesh/allocator. Prepared native data is published only after capture succeeds; destination
capacity is reserved before either destination list receives elements. A shared, noncopyable
input owner reserves at most 64 MiB across active direct sources. Capacity or storage-range
ineligibility falls back to owned copying, not dropped geometry. This limit measures direct source
payload, not all VMA allocations, SDK memory or staging-cache capacity. Each source group retains
its owner, job buffer and immutable descriptor table through the ordinary frame resource retainer.
The existing compute-to-consumer dependency and GPU publication ordering remain in use.

Compact v1 is source format 13, 100 bytes/25 words; legacy format 12 remains 128 bytes/32 words.
It retains position, normal, float4 color, primary/glint UV, signed light/overlay values, texture
IDs, emission bits and post-base fields. Only discrete modes share a word. The packed physical
element aliases an existing UINT element, avoiding another globally allocated Minecraft vertex
element ID. The consumer retains its exact Java class and logical first-write masks, preserving
rigid capture qualification. Chunk and cached rigid/ModelPart constructors, special Spring/Lock
consumers, non-QUADS and raster-preview captures retain their old paths. Native excluded/special
dynamic geometry can decode v1 to canonical PBR on CPU rather than bypass its semantic work.

This private source ABI trusts the renderer's compact producer to write zero in reserved flag
bits on the direct GPU route; the CPU decoder explicitly rejects nonzero reserved bits. It is
not a newly supported arbitrary third-party binary vertex API. Format/version/stride/length
checks occur before asynchronous consumption. No floating-point quantization, new world raster
route, geometry culling, reduced sampling or deferred offscreen animation was added.

Initial paired candidate: JAR `3D4CDF3921763E4E744419AB8E073AE3230B061D53F2556B2575FD58B7C5E8C3`,
core `350B5D3E18ABB25EB3C57BF7E9672F10B635AB274A52D46FC58525A5CE6D13B1`, RelWithDebInfo. Its
artifact/PDB identities and complete raw-byte source archives (including new files and generated
JNI inputs) are under `run/fullscene-input-20260925/artifacts/candidate/`. Artifact-manifest status
is the build-time snapshot; subsequent run evidence supplies actual preflight status.

The candidate's combined preflight passed configuration/save/exit gates, with 400 native-profile
frames and no observer drops. It recorded 135312 deferred vertices, 13531200 source bytes,
13801824 output bytes and 512 rigid instances per frame; two direct sources and two job buffers
were uploaded. These are bounded runtime counts, not visual parity. The earlier profile uses a
different DLL and cannot replace same-artifact per-variant profiles. Native scope labels also
have limits: direct source allocation/copy is inside queue submission; grouped job/descriptor
creation is currently included in `entity.gpu-input-copy`. Do not label either as pure memcpy.

The formal sequence uses one candidate artifact: model-city Latin order L/D/C, D/C/L, C/L/D;
factory L/D/C, C/D/L. Each fresh process warms for 60 seconds and samples for 30, with master
volume zero, no focus and no native profiler. L=(false,false), D=(true,false), C=(true,true).
Formal timing is separate from instrumented diagnosis. Repeated timings, activation/fallback
counts and Java/GPU costs must determine adoption; fewer bytes alone do not justify default C.

## 2026-09-27: Three-scout V2 artifact comparison

Status: static artifact/source investigation, not a new performance measurement or optimization
implementation. Three independent read-only scopes covered geometry production/submission,
scene scheduling/resources, and packaged PT/shader/reconstruction paths. The comparison pins are
Radiance `a39db18f68b95ddea7d67e5c25e48aa2d0c3e3d5` and MCVR
`91301b6eecd5ccf1932de08d38df3673bb2a3664`, against the unchanged user-supplied NeoForge JAR
`A92AD47B966A6F656C6F28068DD7B69D61C0AFAAFFC3583DFD0294B631CB9E09`.
Concurrent Veil/spring work is outside those V2 snapshots. Existing extraction and decompilation
were reused; only the scene scope extracted additional texture classes. No benchmark, client or
GPU run was performed for this comparison. The separately recorded spring validation is not
upstream performance evidence.

### Reconciled findings

| Area | Exact-artifact observation and V2 comparison | Disposition |
| --- | --- | --- |
| Dynamic geometry/source ownership | Upstream still executes ordinary renderers each frame, uses quantized 64-byte entity/40-byte chunk sources, and exposes `TransferredBuildBuffer` ownership transfer. V2 has the versioned byte-length-checked `queueBuildSourcesV1` and native-owned mapped staging before Java returns. | The older V1 two-copy description is not the V2 default. V2 still copies into staging; upstream Java transfer does not prove native zero-copy. This scope established no additional missing equivalent geometry optimization. |
| Geometry visibility and world raster routes | Upstream entity frustum/distance checks and available post-raster world routes can reduce PT input/work. Their actual contribution in historical timed scenes was not measured. | Do not adopt scene omissions or world raster substitution under the full-scene physical-rendering contract. They prevent treating whole-product FPS differences as equal-work optimization attribution. |
| Existing reuse | Nonempty block-entity section indexing, reusable worker scratch, persistent Sable sections and Flywheel model caching exist on both sides. V2 sends dirty Flywheel instance changes and caches immutable published chunk metadata; frame assembly still flattens chunk arrays. | Retain existing mechanisms. Neither artifact proves an entirely persistent GPU scene database; no duplicate cache implementation is proposed. |
| Chunk admission | Upstream normal submission is every third frame outside its nearby-coverage boost. V2 uses CPU/byte/in-flight budgets, interaction priorities and aging. | Do not import the frame-count throttle. It can delay loading/edits at low real FPS and is not a steady-state speedup. |
| External-section capacity | V2 `ChunkProxy` constructs `ExternalSectionBuildScheduler` with its own counter and capacity one, examines at most eight requests per frame, and submits to the normal background executor. It no longer shares the primary important-work counter. | This qualifies the earlier 2026-09-22 shared-important-capacity record. Measure external dirty-to-publish age before tuning capacity; preserve coalescing, generation checks and last valid geometry. No scheduling code changed here. |
| Small texture uploads | Upstream Java batches up to 16384 56-byte descriptors into one JNI submission, with generation/range checks; it also skips unchanged filter/clamp calls in Java. V2 uses per-region JNI entry, synchronous native staging copies, grouped GPU copies and fence retirement; unchanged samplers already avoid native recreation. | A bounded JNI batching experiment is a new measurement candidate only if small-upload entry/locking cost is material. It must retain per-owner generation/range validation and source/destination retirement. Upstream native source-pointer retention and queue behavior are unknown. |
| Active ray dispatch | Upstream classifies shadeable diffuse/specular lobes with remaining path budget, stores original pixel indices and declares indirect dispatch. V2 Advanced continuation/final passes use full-view direct tracing. | Existing third-batch candidate is reaffirmed, not implemented. First isolate active work and per-pass GPU cost; preserve the current estimator rather than importing the upstream integrator. |
| Volume, primary and reconstruction | Upstream primary-discovery/material-resolve splitting and froxel volumes differ from V2. Both already provide temporal/direct-light reuse, SHARC and reconstruction families. NRD-only half-rate configuration does not explain the historical RR cases. | A different estimator, volume integration or precision is not an equivalent optimization. No new reusable denoiser shortcut or matching upstream native AS implementation was established. |

### Decisions and validation prerequisites

Do not repeat V2's completed legacy/direct/direct-plus-compact comparison or the withdrawn compact
producer micro-optimization merely to produce another candidate list. The 100-byte format remains
default-off after its negative incremental result; direct staging remains default-on. The retained
V2 ledger has the actual run counts, timings and final-artifact distinction. Upstream buffer-owner
transfer alone cannot remove V2's required source-to-staging copy. Revisit it only if a new cost
trace establishes an additional avoidable copy or geometry materialization and a safe lifetime.

For a continuation queue, prove exact eligibility, original pixel/frame/bounce identity and random
seeds, transparent/path-budget state, inactive output initialization, shared history/cache ordering,
zero-work handling, capacity/overflow, resize and retirement. Upstream's inspected queue shader
uses opposite-end list writes; this bounded evidence does not establish its backing allocation or
maximum combined lobe counts. That gap is neither a proven upstream overflow nor proof of safety.
Any local prototype needs its own bound and compute-write to indirect/list-read synchronization,
queried indirect-trace support, and a direct-dispatch fallback. Classification/storage overhead can
outweigh saved work for dense frames. No gain percentage follows from static inspection.

Before adding texture-upload batching, collect JNI call counts/CPU cost in atlas, glyph and reload
bursts and distinguish them from steady-state rendering. External-section queue latency is a
separate measurement; increasing its capacity is not authorized by finding a numeric cap. These
are research candidates, not extra implementation work within the spring correctness batch.

### Evidence and remaining limits

Non-portable retained root:
`D:/Workspaces/Artifacts/RadianceUpstreamPerformance/20260927/`.
`INVESTIGATION.json` pins the task; `geometry/geometry-scout.md`,
`geometry/upstream-jar-entries.json` and `geometry/current-source-blobs.json` identify class-entry
offsets/hashes and V2 Git blobs. `scene/REPORT.md` plus `scene/EVIDENCE.json` identify the newly
extracted texture classes and compared source. `gpu/REPORT.md` plus `gpu/EVIDENCE.json` identify
the inspected Advanced pack, classifier/queue/raygen sources and V2 graph/command implementation.
Derived upstream material remains local evidence and is not copied into maintained source.

Matching upstream native source/PDB, actual AS/upload ownership, indirect allocation/synchronization,
driver-compiled permutations, and complete producer/geometry coverage remain unavailable or
unverified. The older aligned-control timing does not become a V2 comparison or a per-feature
benefit because this review uses newer Git pins. No native port, quality reduction, GPU-loss
closure or vendor binary-publication authorization is implied.

## 2026-09-28: Measured high-distance fixed cost in persistent-buffer bookkeeping

Status: confirmed CPU hotspot; batch-ticket implementation and bounded automated/client
verification, including the final overlap follow-up below. The user-supplied
opinion is a candidate inventory, not a proven list of causes. Work began on September 27.
The existing one-sided transport redesign is explicitly deferred.

The runtime baseline uses the retained dirty visual checkpoint above Radiance
`a39db18f68b95ddea7d67e5c25e48aa2d0c3e3d5` / MCVR
`91301b6eecd5ccf1932de08d38df3673bb2a3664`, not those commits alone. Git maintenance froze
that earlier candidate separately; the performance changes described here are later work.

### Controls and evidence boundaries

All cases are new directories under `run/high-distance-20260927/`. Settings were copied once,
read-only, from the authorized Prism instance into a local settings snapshot. Fixed controls:
3840x2054 framebuffer, Vanilla PT, DLSS RR balanced/model 6, SR model 13, FG off, Reflex 2,
simulation distance 32, 28 chunk workers, batch size/count 32, identical Create/Aeronautics/Sable
mod set, no PBR pack, daylight/weather fixed, master audio zero, visible but unfocused window.
Actual frame dimensions/focus and Java-selected renderer options are checked, rather than
inferred from requested configuration. No Prism file, world or setting was changed.

Saved `collectChunkEmission=false` was preserved. Therefore Advanced's neighborhood pass and
the optional full chunk-emission table upload do not explain these runs. The all-air terrain
still submitted 48 dynamic PBR vertices in the profiling runs; it was not a claim of zero GPU
work or literally no scene contribution. The native terrain-ready count was zero.

The initial r8/r32 cases generated the world and are attribution preflights. Formal cases clone
the same pre-generated world, warm up for 45 seconds and measure 30 seconds. Heavy profiling
and continuous chunk logging are disabled for formal timing. Java/native queue snapshots are
taken at sample boundaries, outside measured frames; all queues/in-flight counts were zero,
and native build/submit/complete increments were zero during the sample. FG frame counts are
not used as render FPS. Foreground and minimized samples are rejected.

### What actually consumed the time

The measured native chunk-capacity/metadata scans rose by about 0.9 ms from r8 to r32. The
unpartitioned native submit CPU region instead rose from about 1.37 to 19.84 ms. Additional
default-off profiling phases localized about 19.20 ms to `Buffers::performQueuedUpload`.
It iterated every `persistentBuffers_` entry each flush merely to set `rangeWriteOpen=false`.
The r32 candidate counted **1,419,613 persistent records**, mostly idle placeholders, with
little actual upload work. This is a shared buffer-table cost, not vertex transformation or
the optional light-table upload. World GPU time did not rise with the large CPU delay.

The implementation replaces the global reset with a batch ticket. A full/range upload records
the current ticket; closing a batch advances the ticket in O(1). Only uint32 rollover resets
all tickets to avoid ABA. Actual range staging still performs copy-on-write after a flush,
preserves old content and uses the existing frame retainer and queue/barrier order. The
framework owner is held across replacement allocation and retirement. The helper is the
production staging path, not a separate test-only copy. No view pruning, render-distance or
quality reduction, scheduler deletion, GPU-idle workaround or geometry-format change is used.

### Formal steady-state result

| View distance | Baseline mean real frame ms | Candidate mean real frame ms | Interpretation |
| --- | ---: | ---: | --- |
| 8 | 7.7859 | 7.8076 | Within noise; no improvement claimed |
| 16 | 8.7003 | 7.7898 | About 10.5% lower frame time in this single pair |
| 32 | 25.4405 | 7.8078 | About 69.3% lower frame time; two runs per side |

The r32 A/B/B/A run means were 25.2702, 7.8038, 7.8117, 25.6107 ms; roughly 39.3 to 128.1
real FPS from the mean. Per-run p95/p99 and raw distributions are retained. Candidate profiling
measured the upload stage near 0.024 ms. These results are specific to the fixed all-air scene,
not a universal FPS multiplier or long-term stability proof.

Formal baseline JAR: `32A577FD31484D1EB0AFD2CDBDAB6DB2637EE7D65C7C2B5CCC724ACE483DEE26`.
Formal candidate JAR: `5BF52ECCA17820C367A9AC918CE6ABA4F50F920B04299829758AAB4476834D31`.
Each case independently records copied artifacts, extracted/loaded core, settings and exit.
Those identities must not be silently assigned to later diagnostic changes.

The first GPU CLI sensor attempt had an argument-construction error; its frame-only result is
retained but excluded from the formal matrix. Subsequent CLI loops buffered their tail; those
partial sensor files are not complete peak/clock evidence. A small read-only sampler using the
installed driver's NVML API now flushes each sample and stops via an owned sentinel. Separate
r32 A/B follow-ups captured 93/94 samples over the full processes: device-wide observed peaks
12,567/12,566 MiB, and frame means 25.7806/7.7532 ms. These include WDDM/background/SDK memory,
not process-exclusive VRAM or a local allocator total. See the
[NVML memory-query definition](https://docs.nvidia.com/deploy/nvml-api/api/group__nvmlDeviceQueries.html).

### Loading and interaction: separate experiment

The first island fixture incorrectly tried to fill unloaded positions and produced air; it is
retained as `terrain-seed` and excluded. `terrain-seed-v2` waits for all 64 required chunks,
then creates a 128x128 platform and 16 hollow towers. It reached 80 native-ready sections and
zero pending work, saved and exited normally. Both interaction variants clone that exact world.

The first pair completed 24 normal placement/dig packets, matching client/server state and
normal saving. JNI brackets calibrate clock offsets; the measured endpoint is the first
GPU-completed frame referencing the new revision, not visible display or denoiser settling.
For 12 idle actions the median fell from 102.27 to 39.80 ms; for 12 **after-reload** actions,
100.79 to 37.81 ms. First-action cold work remains in the raw data and raises means/maxima.
All 24 operation windows per side had no trace-drop increase, but startup/F3+A overflow is
explicitly retained. The original script waited two seconds after F3+A, by which time loading
had finished; these twelve actions must not be described as sustained background-load tests.

Follow-up diagnostics add a configurable post-reload burst and pending-work snapshots,
suppress never-present empty-section trace noise, and count ready chunks without allocating a
full snapshot per reserved slot. A default-off audit reference can repeat the old buffer sweep
on the same new binary, permitting matched diagnostics on both sides. These follow-up changes
are not yet covered by the formal JAR identities/results above.

### Validation and remaining scope

The production range-staging seam covers full/range merge, multi-flush copy-on-write, retained
old buffers, allocation failure, zero/bounds input, owner release/recreation and ticket rollover.
Native CTest passed 68/68 before the diagnostic-reference follow-up; Java GAME passed 243,
bootstrap had 10 passes/2 skips, and subsequent Audit tests cover boundary snapshots/timing.
Do not promote source/state tests or scene-ready counts to pixel-level acceptance. The unsafe
legacy strip issue and all one-sided/fan/old-device-loss findings remain outside this batch.

Machine evidence is retained at the non-portable
`D:/Workspaces/Artifacts/RadianceHighDistance/20260927/` (comparison JSON, scripts, build/test
logs and settings snapshot), with raw client records under the repository `run/` paths.
At the interim checkpoint, remaining work was the overlapping rebuild/action comparison and
final artifact/source verification; the next subsection records those results. The opinion's broader sparse-scene/lock/batch changes are not
automatically justified by this hotspot; do not turn the candidate list into an unmeasured
scene-system rewrite.

### Final diagnostic integration and actual overlap follow-up

The same-binary reference/candidate follow-up uses JAR
`8742F6B2C3C3DAE8A0548EB486C0BFD0606CE623FF18967060F50C67376DD9BC`, core
`627666CCE7DE7B5AB02D32E59AE3D544FE7AA70B11AA5A2FBA2511D636877C5F`, Audit
`3AC8CA80426049264AE75580D0FA289CEED7E21BE6D93762F5EFBF77A771D497`, and startup agent
`C152D865FC29489F3BF8B9F9B6E1D5925DC6201CA55C1D29E4DF3412E9B8B3D5`.
The reference explicitly enables the old full-table sweep; normal/default behavior does not.
This comparison isolates that bookkeeping choice with identical updated diagnostics; it is
not retroactively labeled the old binary used in the earlier formal matrix.

Both processes completed24 client/server actions, returned to80 ready sections, saved and
exited0 without foreground samples. Trace loss was0 for the entire captures. The first action
after F3+A merged into the initial build (no separate DIRTY revision); reference additionally
superseded5 transient versions, while candidate published the other11. These are reported,
not counted as lost final updates or filled with fabricated individual latencies.

Five operations (16,17,19,21,23) have surviving matching identities and positive pending-work
observations in both variants. Their first-GPU-completed-frame times were reference
98.9753/99.2620/96.5837/92.8560/93.7103ms versus candidate
43.55645/36.96405/45.88395/44.57425/44.13305ms; medians96.5837 and44.13305ms.
Five samples are not a population p95 guarantee. F3+A trigger-to-first-polled80-ready recovery
was about1207.1 versus899.4ms, including the synchronous reset; GPU completion observations
include polling delay and must not be called pure AS GPU duration. This is a bounded rebuild
case, not a long traversal/new-terrain generation or heavy Sable throughput certification.

The final normal-config `final-smoke-r32` run separately measured7.8070ms mean and8.5668ms p95
over3200 real frames, exited normally and saved all dimensions. No reference-sweep activation
was present. Build output, embedded and actual extracted/loaded core hashes match. Native CTest
again passed68/68; Audit25/25, benchmark5/5 and GPU-monitor unit2/2 passed. The earlier GAME243
and bootstrap10-pass/2-skip gate applies to unchanged main Java sources. No new pixel-level
acceptance, one-sided correctness or historical GPU-loss resolution is implied.

`FINAL_SOURCE_MANIFEST.json` and `FINAL_SOURCE_DELTA.zip` pin101 changed/new files against the
recorded HEADs (raw-byte hashes, no newline normalization); later documentation-only additions
do not change the tested product inputs. `FINAL_ARTIFACTS.json`, `STEADY_COMPARISON.json`,
`RESOURCE_RESULTS.json`, `LATENCY_COMPARISON.json`, `OVERLAP_COMPARISON.json` and
`OVERLAP_MATCHED.json` retain the distinct identities/scopes. All spawned clients and sensor
helpers ended. The original source-amend dependency-placement question remains unresolved;
no branch was moved or pushed, and the frozen old index and verified recovery bundles remain
separate from these new, unstaged performance changes.

## 2026-09-28: 32-distance, tier-2 three-product pressure retest

Status: bounded runtime-observed comparison, five valid formal runs and one failed repetition.
Evidence: artifact/configuration checks, real-frame timings, saved NBT, process/NVML telemetry,
JVM fatal report and Windows events. This is not visual parity, a causal fork before/after A/B,
or a long-duration stability result.

### Fixed inputs and direct setup correction

The common vanilla model-city recipe was doubled to 512 fully equipped armor stands, 512 item
frames (128 each of fence/hopper/brewing stand/anvil), 512 chests and 512 banners. All five valid
runs confirmed the exact counts, equipment and fixed camera. The 1.21.1 products cloned the same
saved world; 1.21.4 used the same verified recipe in its own data version. Each saved 8281 chunk
records, 4761 at full status. Requested recipe chunks are now explicitly awaited before build;
forceload is not treated as synchronous readiness. Formal copies reset initial game time to12000,
retain daylight6000/clear weather and normal animation/ticking, and do not rebuild the fixture.

All targets: 2560x1440, render/simulation32/5, Advanced + RR Balanced, shared exposed 4/32/4
bounce/initial/spatial controls, jitter/SHARC on, emission collection on, VSync/FG/Reflex off,
8 workers and8x8 batches, Java21 2/8GiB heap, no resource packs, muted, unfocused and non-minimized.
The fork retains rigidModels=true and rigidParts=false. Each formal process had90s world warmup
and45s sampling. Order: NeoForge upstream, Fabric upstream, fork, fork, Fabric upstream, NeoForge
upstream. No builds or other intentional test workloads overlapped formal timing. The old-sweep
reference diagnostic remained off. Requested, sample-start/end and saved settings were checked.
Pre-generated terrain and fixed warmup reduce loading confounds; this portable observer does not
prove native queues are empty or prove all saved objects participated in GPU tracing.

The first fork preflight, JAR8742F6B2..., failed before world entry: an ordinary panorama draw
called VeilAdapter.requireSpringRasterDraw, which linked SpringRasterLowering and its absent Veil
ShaderException dependency. It is retained as a real setup failure, not a performance sample or
GPU device loss. The facade now first checks the actual IExternalShaderProgram and spring-program
identity, without installed-mod checks or adding Veil to one side. Actual spring validation is
unchanged. A class-loader-isolated test invokes this facade with a real vanilla ShaderInstance
while refusing Veil/lowering classes. New JAR7B7FBDAE... passed the pure-vanilla world preflight
and both formal runs. Core was unchanged; no shader/geometry/performance algorithm changed here.

### Results, including failures

| Product | Valid / attempted formal runs | Mean real interval ms | Real FPS | Pooled p50 / p95 / p99 ms | Per-run means ms | Peak private commit GiB, valid runs |
| --- | --- | --- | --- | --- | --- | --- |
| Upstream1.21.1 NeoForge0.1.6-alpha | 1/2 | 52.177 | 19.17 | 44.903 /58.219 /63.737 | 52.177 | 55.94 |
| Upstream1.21.4 Fabric0.1.5-alpha | 2/2 | 68.476 | 14.60 | 68.264 /71.052 /75.202 | 68.618 /68.334 | 14.75 |
| Paired fork | 2/2 | 72.264 | 13.84 | 74.786 /83.769 /92.101 | 72.246 /72.283 | 12.77 |

Means weight valid runs equally; percentiles pool raw frame intervals. These are CPU real frame-loop
cadences, not generated/presented-frame counts. Fork frame time is5.53% higher than Fabric in this
sample. The tier and view distance differ from the historical tier1/r16 suite, so those numbers
cannot be used to isolate this latest optimization. The earlier all-air Vanilla PT A/B remains
valid in its own scope; this high-pressure Advanced suite does not reproduce its128FPS result.

The successful NeoForge upstream run includes a4400.806ms interval, retained rather than removed.
The other two products had no comparable pause in these short samples. Their mean render-thread
CPU times were49.11/67.87/70.06ms for Neo/Fabric/fork, and approximate process CPU core-equivalents
1.12/1.04/1.62. Thread CPU includes native/driver execution or busy waiting and is not a Java-only
attribution. Whole-device NVML peaks were11458/12492/12715MiB; desktop/SDK allocations are included,
these are not per-process VRAM. No allocator/SDK-complete GPU-stage attribution is claimed.

The final NeoForge upstream repetition crashed during warmup at2026-09-28 06:40:19 +08:00,
PID100156. hs_err reports EXCEPTION_UNCAUGHT_CXX_EXCEPTION(0xe06d7363), Render thread,
EntityProxy.buildNative; process exit was-1073740791. No completed frames.csv or normal save/close
sequence was produced. The copied initial world must not be counted as a successful post-run save.
Process telemetry peaked at62.34GiB private commit; hs_err recorded64007MiB current/64182MiB peak
private bytes and only4MiB available system commit (TotalPageFile147139MiB). Physical memory still
had16189MiB free. Windows recorded a concurrent dwm.exe/dwmcore.dll crash, code0xc00001ad.
This establishes severe commit pressure and an uncaught native failure, not its precise allocation
site, a confirmed leak, or confirmed VK_ERROR_DEVICE_LOST. No GPU reset/TDR change or automatic
retry occurred. The benchmark group stopped. One successful NeoForge run does not establish its
repeatability/stability or justify omitting the failed attempt.

### Artifacts, tests and retained evidence

| Target | Main JAR SHA-256 | Embedded / observed loaded core SHA-256 |
| --- | --- | --- |
| NeoForge upstream | A92AD47B966A6F656C6F28068DD7B69D61C0AFAAFFC3583DFD0294B631CB9E09 | D1BD513F14E99A563233FF4F724537F767D8958C75887833E6195D9A6737146A |
| Fabric upstream | FC4F36919809C584F922704F29150DA68202C7E6B4DF9E3D35274552C668C86E | A9969B70B6998B1F8F90AF54EFEDA8652CC7E98769B7D36C3C944011B71A5B60 |
| Fork | 7B7FBDAE9440F1E8DB8474B6C0C47AF6E368ECBE536DC3F634DDB59445BA6A08 | 627666CCE7DE7B5AB02D32E59AE3D544FE7AA70B11AA5A2FBA2511D636877C5F |

This batch ran19 selected GAME tests (including the new absent-Veil behavior test and spring/facade
regressions), bootstrap10 passes/2 skips, verifyDistributedJar, and10 fixture/comparability Python
tests. A first unqualified Gradle test selector also selected the Audit subproject and failed on
no matching tests; the explicitly qualified :test retry succeeded. Both logs remain. Native source
and DLL were unchanged, so native68/68 is historical evidence, not rerun or relabeled here.

Local evidence (non-portable):
`D:/Workspaces/Repositories/GitHub/RecRivenVI/Radiance/run/upstream-pressure32-20260928/`.
MANIFEST.json preserves the original setup; MANIFEST-revised.json plus optional-boundary-source.zip
records the two Java file overlays against the previous101-file source manifest/delta under
`D:/Workspaces/Artifacts/RadianceHighDistance/20260927/`. Hashes use raw bytes without normalization.
The run-local driver/profile inputs, frozen-world file manifests, six-case plan, five raw samples,
failed-process stdout/hs_err/Windows events, tests and SUMMARY.json preserve distinct identities.

All exposed controls match, but integrators, culling/full-scene policies, SDK internal model and
loader/game versions differ. Scene counts do not prove equal rendered work or pixels. The observed
ranking is a whole-product comparison under these conditions, not visual parity or proof of the
cause of the remaining frame cost. Public binary licensing, single-sided redesign, spring visual
failures and historical device-loss causes remain independent. No Git refs/index were changed;
no Prism files, production data or previous evidence were modified.

## 2026-09-28: User observation with and without mob spawning disabled

Status: user-reported runtime observation; recording only, no independent retest or attribution.
Repository context when recorded: Radiance `0ddc48b334e3f30cbc44ff61d75bb50c26ec87c9`,
MCVR `e6e8153e0ff8c3ab6c3108d8d631beea1f99810e`. These identify the source checkpoint;
the running artifact was not independently checked for this observation.

The user reports the current version in the same normal world, with time, weather, camera
position and camera angle fixed. At32 view distance, chunks were fully loaded before the
steady reading. The user then switched to16 and8 view distance with the same two configurations
and reported the readings after stabilization.

| View distance | Original configuration, FPS | Additionally enabling the spawn-disabling setting, FPS |
| --- | --- | --- |
| 32 | 13± | 25± |
| 16 | 55± | 64± |
| 8 | 65± | 69± |

The second configuration preserves the user's wording, "额外启用禁用生物生成". No specific
command, mod setting, removal of existing mobs or resulting entity count was supplied; disabling
spawning is not recorded as an empty-entity scene. The ± notation is retained as reported,
without inventing an error bound, sample duration, frame-time distribution or generated/real-FPS
classification. These observations remain separate from earlier automated benchmark samples.
No cause, bottleneck or implementation conclusion is assigned. No subagent, test, game operation
or product change was performed for this entry.

## 结论

本次迁移不实施研究中的提案，也不重新判定视觉、性能或设备丢失根因。旧台账与闭合审计保留在仓库外历史存档。
