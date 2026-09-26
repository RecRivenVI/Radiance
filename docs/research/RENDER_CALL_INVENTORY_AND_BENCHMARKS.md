# Render-call inventory and upstream benchmarks

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

See the [implementation/preflight record](../DEVELOPMENT_LEDGER.md#2026-09-25-opengl-inventory-and-portable-upstream-benchmark-preflights),
[inventory usage](../../Modules/RadianceAudit/tools/README.md), and
[benchmark usage and limitations](../../Modules/RadianceAudit/benchmark/README.md).
The next measurement gate is effective-settings parity and repeated same-version A/B, with an
independent vanilla cross-version table. No performance ranking is established by the preflights.

## 2026-09-25 follow-up: larger workloads and effective configuration

Status: implemented; automated-verified; runtime-observed in ten bounded measured processes.
The [matched pressure checkpoint](../DEVELOPMENT_LEDGER.md#2026-09-25-matched-three-version-pressure-benchmarks)
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
[pressure checkpoint](../DEVELOPMENT_LEDGER.md#2026-09-25-matched-three-version-pressure-benchmarks).
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
[explicit shader-control checkpoint](../DEVELOPMENT_LEDGER.md#2026-09-25-repeated-comparison-with-explicit-shader-controls)
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
