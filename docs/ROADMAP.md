# Project roadmap and deferred work

Only user-approved or explicitly deferred work belongs here. An item is not permission to begin it
automatically; it preserves scope, constraints, and evidence for a later request.

## Standalone same-scene MCVR comparison

Status: archived by user decision on 2026-09-24. The one-shot capture/native replay/free-camera
system has served its performance-investigation purpose and is removed from active source and
build targets. No further replay implementation or pressure run is planned. See the
[retirement record](DEVELOPMENT_LEDGER.md#2026-09-24-one-shot-scene-replay-retirement-and-amend-preparation).

The [Extreme capture](DEVELOPMENT_LEDGER.md#2026-09-24-user-pressure-scene-capture-and-corrected-native-replay)
and [Medium comparison](DEVELOPMENT_LEDGER.md#2026-09-24-medium-scene-sequential-game-and-native-comparison)
remain historical evidence with their original artifact and timing limits. Frozen-scene speed is
not a performance guarantee for a dynamic Minecraft world.

Retain the resulting [region-upload, section-index, vertex-packing and cloud-cache optimizations](DEVELOPMENT_LEDGER.md#2026-09-24-region-uploads-block-entity-index-and-submitted-cloud-cache)
and their tests. Broader moving-world/PBR/world-switch acceptance and per-change attribution stay
open. The separate same-filter/different-mipmap sampler follow-up remains recorded; retirement
does not implement or dismiss it.

## Static-review remediation program

Status: manual observation round ended by user on 2026-09-23 after limited simple play without
observed problems. Specialist acceptance remains open; no further run is scheduled by this record.
The directed texture-owner and shared UI PT source gaps have been implemented and exercised;
FG background-dependent semantics, high-resolution SDK budget behavior and visual acceptance
remain open. Earlier batch repairs are retained.
Evidence: the linked dated audit plus the two repository ledgers.

Supporting audit:
[`audits/2026-09-22-gpt6-pro-code-review-verification.md`](audits/2026-09-22-gpt6-pro-code-review-verification.md).

Remaining work is intentionally split by dependency and risk:

1. retain the earlier exact-artifact G0/G1/G2 results without treating them as full regression of the
   unified renderer candidate; real device loss lacks a GPU root cause. A later Advanced incident
   has first-error, all-dimension save and native-close evidence, separately from the earlier event;
2. complete FG's background-dependent composition contract and real-input verification; fractional
   alpha, affine local inversion replay and coverage-weighted HUD-less blur are implemented;
   GPU tests and same-frame input checks do not close generated-frame dynamic visual acceptance;
3. Ponder PT is archived by the user on 2026-09-23. Accept the restored default raster path;
   generic UI PT expansion and unresolved PT transition/reflection/crop behavior are deferred.
   See the [archive inventory](history/ponder-pt-2026-09-23.md); earlier PT evidence remains intact;
4. complete runtime and visual acceptance for the implemented batches, including external-section
   churn, Veil shaders, Ponder, chunk convergence, Create/Flywheel, resize and feature fallbacks.

Resolved implementation items remain described in the dated audit and ledgers. Maven now publishes
the separately named developer-only `Radiance-game` artifact; `distributedJar` is the installable
product.

Packaging maintenance on 2026-09-24 aligned both Radiance and its external audit mod with
NeoForge 21.1.251 and added launcher-readable metadata without changing SERVICE/GAME ownership.
Package tests and an isolated menu launch passed; user Prism display/gameplay observation remains
pending. See the [deployment record](DEVELOPMENT_LEDGER.md#2026-09-24-neoforge-211251-and-launcher-visible-distribution-metadata).

Do not mark the program complete from unit, CTest, build, or one gameplay run. Each item retains the
runtime, GPU, packaging, and visual gates named in the audit.

### External-section admission and backpressure

Status: source-fixed and automated-verified on 2026-09-22; isolated Sable structure creation,
separation and F3+A smoke passed. Longer churn acceptance remains pending.

External builds now share the important-build capacity with primary important work and have an
eight-request per-frame preparation budget. Admission precedes `RenderChunkRegion` creation;
rejection, failure and cancellation restore the permit and running state, while latest-revision
coalescing preserves deferred updates. Java owner/revision checks and MCVR's existing encoded-handle
generation check prevent stale publication after release or reuse. Deterministic tests cover
saturation before snapshot creation, resumed progress, preparation/submission/worker failure and
owner replacement. The full isolated mod set produced no visible hole or failure while creating and
separating a Sable structure and running F3+A. Remaining acceptance requires longer repeated churn
proving no starvation or unbounded queue/CPU/VRAM growth during load, unload and world switch.

### Resource identity without a `.png` suffix heuristic

Status: source-fixed, automated-verified and accepted for the named isolated `F3+T` and
logical-server `/reload` cases on 2026-09-22.

The wrapper now processes `FallbackResourceManager.getResource`, `listResources` and
`listResourceStacks`, where the manager's real `PackType` is available, and requires both
`CLIENT_RESOURCES` and the PNG file filter. A matching server-data location therefore remains
unwrapped. The returned `Resource` preserves the selected pack, metadata supplier, vanilla debug
wrapper and stream ownership; tests cover identifier retention, delegated reads and
close-on-success/failure. The initial getResource-only version failed F3+T because atlas listing
lost auxiliary texture identities; that failed run is retained in the ledger. The corrected build
kept no-PBR blocks flat and visually normal through F3+T. A separate process completed `/reload`
with server recipe/advancement reload only, no client atlas or Vulkan texture reload, and normal
exit. Custom decoders that bypass these standard manager results remain a compatibility boundary.

Public binary release is a separate gate from source commit readiness. Before distributing a JAR
that embeds the NVIDIA runtimes, complete a license checklist covering permitted object-code
distribution, bundled notices, required attribution/trademark use, pre-commercial notification,
and compatibility with the GPL-3.0 distribution. Preserve the pinned runtime manifest and verify
the final artifact contents. This gate does not require unfinished renderer work to be falsely
closed and does not prevent a clearly documented source-only remediation commit.

## Chunk pipeline: high view-distance efficiency

Status: scoped scheduling work resumed by user on2026-09-23; implemented, comparison/visual acceptance pending.
Evidence: source and named automated/GPU fixtures in the
[follow-up ledger](DEVELOPMENT_LEDGER.md#2026-09-23-per-draw-face-rules-and-interaction-aware-chunk-scheduling).
The broader geometry-cache/arena redesign below remains deferred; it is not part of this batch.

The active batch also unifies face-state enable/mode/winding per source draw. Required acceptance:
mixed one/two-sided opaque geometry, FRONT/BACK/BOTH and CW/mirrors across ordinary and special
producers; baseline -> corrected-A -> B matched isolated-world comparisons; idle/backlogged edits,
read versus new-generation routes, real-frame distributions, completion-linked revisions and
resource peaks. Other-client GPU use must end before comparable performance runs. No reduction of
view distance/quality/mods may be presented as an optimization. Preserve natural-loss evidence and
stop the affected run without a blind restart.

Supporting implementation snapshot:
[`DEVELOPMENT_LEDGER.md`](DEVELOPMENT_LEDGER.md#2026-09-21-chunk-loading-and-native-build-scheduling).

Retain the safety boundaries in the linked 2026-09-21 implementation snapshot. When work resumes:

1. Build a persistent section/column occupancy index so high view distance and F3+A do not rescan
   the complete dense `ViewArea` repeatedly.
2. Generate candidates from genuinely loaded columns instead of enumerating every allocated slot.
3. Split and independently budget CPU mesh construction, upload, and BLAS construction; regulate
   throughput using queue backlog and frame time.
4. Reuse staging buffers, temporary build memory, and arenas to reduce allocation, copying, and
   peak VRAM pressure.
5. Add a stable geometry cache so unchanged sections reuse mesh and BLAS input across generations.
6. Raise native batch throughput only after diagnostics identify that stage as the bottleneck.

Acceptance must separately measure correctness, loading convergence, and steady frame rate for
initial entry, 32-chunk distance, F3+A, continuous block edits, teleport, and dimension changes.
Record holes and recovery time, Java/native queue states, CPU build/upload/BLAS time, VRAM peak,
and device-loss events. Historical enqueue reductions are not acceptance evidence for new code.

## Screen effects and Veil takeover

Status: investigating for the remaining screen-effect contracts. The user retracted the missing
Veil underwater-texture report after retesting on 2026-09-25: the texture exists but is very faint
in some scenes. No missing-texture repair or opacity adjustment is scheduled from that report.
Earlier bounded runtime/visual observations remain historical, not current completeness proof.
Evidence: static, build, automated, runtime, visual, and deployment for the linked named historical
cases; the latest correction is user visual feedback, not a measured opacity-parity result.

Supporting implementation records:
[`DEVELOPMENT_LEDGER.md`](DEVELOPMENT_LEDGER.md#2026-09-21-camera-screen-effects-in-the-hdr-world-composite)
and the
[Veil compatibility boundary](DEVELOPMENT_LEDGER.md#2026-09-21-consolidated-veil-compatibility-boundary).

Latest correction and static findings:
[underwater report retracted after retest](DEVELOPMENT_LEDGER.md#2026-09-25-veil-underwater-report-retracted-and-two-deferred-visual-tasks)
and the earlier
[bounded static audit](DEVELOPMENT_LEDGER.md#2026-09-25-screen-effect-static-audit-and-missed-veil-underwater-overlay).

- Retain the distinction between texture visibility and exact Veil UV/alpha/HDR parity. The former
  is confirmed by the user's retest; the latter has not been measured. A future parity check should
  compare matched scenes and final frames; callback execution and `BACKEND_RECORDED` alone are
  insufficient. Do not increase opacity merely to make the texture easier to notice.
- Resolve the bounded audit findings: PT night-vision/conduit/darkness strengths, status-effect fog,
  combined nausea/bob matrix order and post-effect resource identity. Keep Veil generic color/alpha
  and first-person post-stage compatibility limits explicit. The withdrawn water-texture report
  neither proves nor dismisses those separate findings; they have no new implementation/runtime
  verification in this recording step.

- Prove with audit records that Veil-active block/water frames terminate as
  `camera.*.veil_call`, while Veil-inactive and third-party-fluid calls use their own producer
  paths.
- Compare vanilla and Radiance captures for geometry, UV orientation, tiling/cropping, alpha,
  brightness, ordering, and tone-mapping interaction.
- Audit every remaining Veil adapter: optional-mod checks may gate class loading only; behavior
  must be driven by real calls and real bound state.
- Extend the external audit mod so a capability or installed-mod check can never close a producer
  intent as translated.

## Transparency model consolidation

Status: deferred by user.
Evidence: user decision; the linked research is static and historical only.

Supporting research:
[`research/PT_VISUAL_AND_PERFORMANCE_INVESTIGATIONS.md`](research/PT_VISUAL_AND_PERFORMANCE_INVESTIGATIONS.md#transparency-model).

The intended product-facing set is PT opaque, PT cutout, PT translucent, and PT physical
transmission. Inventory remaining legacy alpha-blend routes, identify their real producers and
material semantics, then migrate or reject them explicitly. Do not collapse ordinary alpha,
refraction, and physical transmission merely because all three can appear transparent.

## Known Vulkan validation findings

Status: investigating; tracked separately from the historical native crash workaround.
Evidence: historical Vulkan validation output; controlled baseline comparison remains pending.

Supporting historical evidence:
[`history/port-artifacts-20260921/RadianceCrashInvestigation/20260917-PID15936/e02-retest-20260917/EXPERIMENT.md`](history/port-artifacts-20260921/RadianceCrashInvestigation/20260917-PID15936/e02-retest-20260917/EXPERIMENT.md).
The broader failure-class and diagnostic boundary is summarized in
[`research/CRASH_AND_RUNTIME_INVESTIGATION_HISTORY.md`](research/CRASH_AND_RUNTIME_INVESTIGATION_HISTORY.md).

- LDR barrier source scope around the tone-mapping/render-post transition needs a controlled
  baseline and validation rerun.
- Two 12-byte `vkCmdCopyBuffer` write-after-write hazards need attribution and baseline comparison.
- Vertex/index buffer usage VUIDs need producer attribution and correct usage flags.

These findings must not be described as the cause of the historical driver access violation
without an A/B reproduction.

## Ponder PT follow-up

Status: archived by explicit user decision on 2026-09-23; inactive in the Ponder path.
The retained investigation list below is historical, not the next automatic development batch.
See [archive and reactivation boundary](history/ponder-pt-2026-09-23.md).
Evidence: user decision plus bounded runtime, visual, and capture evidence in the linked records.

Supporting implementation and research:
[`DEVELOPMENT_LEDGER.md`](DEVELOPMENT_LEDGER.md#2026-09-21-ponder-path-tracing-and-rr-model-diagnosis)
and
[`research/PT_VISUAL_AND_PERFORMANCE_INVESTIGATIONS.md`](research/PT_VISUAL_AND_PERFORMANCE_INVESTIGATIONS.md#ponder-performance).

- Fix and test the shared Halton/early-termination condition.
- Diagnose low-entropy R16F Ponder depth and alternating zero/non-finite converted FSR depth.
- Verify raw-depth alpha against upscaled-color edges.
- Validate dynamic-topology correspondence, transitions, full material parity, and sustained
  performance.
- Bound Ponder memory before further visual tuning. At 3840x2054, full-window main plus Ponder
  transition pipelines reproduced `slEvaluateFeature -> eWarnOutOfVRAM` at about 13.8 GiB reported
  VRAM usage. Design a capped or cropped Ponder render extent, lightweight per-scene buffers, and
  resize retirement that does not overlap multiple complete old/new pipelines longer than GPU
  safety requires.
- If model behavior is revisited, compare D and E at matched resolution, scene state, and capture
  point. Do not turn a temporary Ponder model preset into a permanent default without a product
  decision.

### Shared UI path-tracing pipeline

Status: archived for Ponder on 2026-09-23 after subsequent implementation and reported visual
failures. The original design below is retained for context; it is not an unimplemented active
plan or authorization to re-enable PT. General UI PT remains deferred.

Replace Ponder's per-scene complete `WorldPipeline` instances with one reusable UI path-tracing
service. Keep the main-world execution state isolated initially. Split the reusable pipeline
definition, shaders, layouts, material bindings, and transient-image pool from lightweight
per-scene state containing geometry, BLAS/TLAS, camera, generation, output, and only the temporal
history that scene requires.

The implementation should proceed in bounded stages:

1. Define mod-independent create, update, render, and release scene handles, then migrate Ponder
   without changing its current framing or material behavior.
2. Pass the real physical UI viewport to native code and enforce a configurable output-pixel
   budget. Reducing only the DLSS input resolution is insufficient because full-size output and
   history images remain allocated.
3. Reuse transient frame resources across sequential UI scene renders. Give temporal scenes
   generation-safe history slots and bounded Streamline viewport IDs, respecting the queried
   `maxNumViewports` capability and explicitly falling back when capacity is unavailable.
4. Cache geometry and BLAS by content version; update transforms or refit TLAS when possible.
   Static scenes should reuse converged output until camera, animation, material, or lighting state
   changes.
5. Preserve Ponder transition semantics. First verify how the pinned Create/Ponder version draws
   both worlds and applies their transforms. When both are live in the same UI viewport, represent
   them as independently versioned sub-scenes in one PT scene and one TLAS, rather than freezing
   the outgoing world or retaining two complete PT/RR pipelines. Their geometry must share the
   camera and participate in mutual visibility, shadows, reflections, indirect lighting, and the
   transition's transparency/coverage semantics. Scene membership, topology changes, motion data,
   and temporal-history reset/reactive handling must prevent cross-transition ghosting.
6. After Ponder reaches runtime and visual parity, expose the generic capture API to other UI 3D
   scenes. Unsupported primitives and untranslatable third-party calls must report or take an
   explicit raster fallback rather than disappear silently.

Acceptance requires a fixed pipeline-object count across scene churn, bounded VRAM through entry,
exit, transition, and resize, no unintended camera or temporal-history contamination, preserved
Ponder orthographic framing/material/transparency behavior, correct mutual PT interaction between
simultaneously rendered transition worlds, and a 4K reproduction that no longer returns Streamline
`eWarnOutOfVRAM`. Build and fixture results remain separate from actual runtime and visual
acceptance.

## Optional compatibility projects

Status: proposed; static investigation is recorded and implementation is not automatically
authorized.
Evidence: static source and API investigation in the linked research.

### Physics Mod rigid-body bridge

Implement an optional external adapter without bundling, modifying, or taking a mandatory build
dependency on Physics Mod or PhysX. Restore the simulation lifecycle without invoking its OpenGL
renderer, extract stable CPU meshes before they are cleared, share one BLAS per model, and update
TLAS transforms per rigid body. The first milestone covers fragments, basic particles, ragdolls,
reload, world changes, and clean absence of Physics Mod. Cloth, liquids, volumetrics, ocean, GUI
physics, and debug rendering are later work.

### Modern UI compatibility

Proceed in independent gates: startup-safe coexistence, Vulkan Modern Text Engine, blur/tooltip
semantics, then Arc3D Vulkan off-screen UI on MCVR's existing device. A second Vulkan device or
present chain is prohibited. Exact third-party versions and runtime JAR contracts must be locked
before implementation.

The supporting findings and acceptance boundaries are in
[`research/COMPATIBILITY_INVESTIGATIONS.md`](research/COMPATIBILITY_INVESTIGATIONS.md).

## PT visual-correctness work packages

Status: proposed; prioritize by a later user decision.
Evidence: static and historical investigation in the linked research.

- shared UI PT pipeline, Ponder resource reuse, and same-scene transition-world interaction;
- deterministic non-refractive transparency after denoising;
- correct NRD hit-distance/direct-visibility separation and conservative defaults;
- **Deferred, requested 2026-09-25:** correct labPBR surfaces turning fully black at grazing angles,
  using Sundial as an algorithmic reference. Consolidates the existing safe normal-map decode,
  TBN handedness, grazing/shadow-terminator and indirect-energy work; recheck the cause on current
  source before implementing. Acceptance must cover grazing/front views, mirrored UVs and a flat
  normal reference without replacing legitimate shadows with artificial brightness.
- explicit refractive material identity, validated IOR, medium stack, and distance absorption;
- complete one-sided geometry semantics for camera, reflection, and shadow rays;
- celestial-track tilt, bounded angular light radius, and dedicated vanilla-cloud fog;
- **Deferred, requested 2026-09-25:** rewrite vanilla-cloud rendering. This is broader than the
  existing cloud-fog proposal; define the replacement contract before implementation. Preserve
  cloud settings/resource-pack behavior and check motion, lighting, fog, reload, render cost and
  resource lifetime in the eventual acceptance. No replacement design is approved by this entry.
- invisible light-block analytic lighting and Aeronautics staff/crystal PT representations;
- separate block-area-light and compatibility-emission controls.

Each item must start with a diagnostic view or deterministic fixture, then record static, GPU,
runtime, and visual evidence separately. See
[`research/PT_VISUAL_AND_PERFORMANCE_INVESTIGATIONS.md`](research/PT_VISUAL_AND_PERFORMANCE_INVESTIGATIONS.md)
for the preliminary findings.

### Unified 2026-09-22 acceptance gate

The uncommitted paired candidate retains bounded Ponder allocation, fractional FG coverage,
priority temporal guides, large-coordinate Flywheel transforms, external admission and client
resource-type isolation. Existing builds/menu preflight passed, but successor inspection corrected
the claimed completion of shared UI PT execution/transition ownership and safe texture-name reuse.
The later owner-ticket/shared-executor implementation supersedes those source gaps; see the current gates below and the
[directed handoff correction](audits/2026-09-22-gpt6-pro-code-review-verification.md#2026-09-22-successor-handoff-directed-verification-correction).

The real device-loss incident remains partial evidence: first error and integrated-server save are
known, while GPU root cause and native close completion are not. A default-off trace is ready for a
future natural occurrence; unsafe device reset or TDR manipulation is not an acceptance method.
Public binary distribution remains blocked until the external conditions in
`THIRD_PARTY_RUNTIME_AUDIT.md` are resolved. Neither boundary blocks local isolated validation.

## RenderPearl, Sodium, and dual-backend migration

Status: proposed; preliminary static feasibility evidence exists, and no implementation approval is
implied by this record.
Evidence: static source, architecture, and external-version investigation in the linked research.

Target OpenGL mode is real Minecraft 1.21.1 NeoForge with Sodium-style optimization and broad
third-party compatibility. Target Vulkan mode is an extended RenderPearl host with Radiance/MCVR
features and fail-closed handling of unsupported rendering. Keep GLFW in the first prototype.

The mandatory first step is an isolated vertical slice proving one host-owned Vulkan device and
swapchain, pre-device Streamline/RT requirements, borrowed-device MCVR execution, one common GUI
draw on both real OpenGL and Vulkan, and clean synchronization/lifetime behavior. Only after this
passes should world submission and Sodium's chunk pipeline migrate. The detailed architecture and
evidence boundary are in
[`research/RENDERPEARL_SODIUM_MIGRATION.md`](research/RENDERPEARL_SODIUM_MIGRATION.md).

## Native lifecycle acceptance

Status: active validation; source and automated checks and G0/G1/G2 are complete; G3 remains
deferred.

- G0: **passed 2026-09-22** in the isolated minimal packaged client. A new world, identifiable
  block/container mutation, normal save/exit, re-entry and persistence were observed; logs show two
  complete integrated-server save/stop sequences, clean renderer close and process exit code 0.
- G1: **passed 2026-09-22** after fixing the fatal-exit cleanup boundary. The controlled error
  reached Java unchanged, sticky rejection held, no later submission occurred, all dimensions
  saved, native/Streamline shutdown completed, and two restart checks preserved the new mutations.
- G2: **passed 2026-09-22** in a separate title-screen process. Post-fatal diagnostics remained
  readable, the ordinary probe was rejected before its body, and two close calls completed while
  preserving the first cause. Synthetic thread launch, worker, allocation, and JNI-string failures
  remain covered by automated tests.
- G3: retain as not runtime-accepted until a protocol can quiesce actual GPU and asynchronous
  presentation work before entering a device-lost cleanup branch. A fake flag on a healthy busy
  device is prohibited. During the later resource-isolation acceptance, one ordinary process did
  encounter a real driver-confirmed `VK_ERROR_DEVICE_LOST` before the requested `/reload`:
  Windows logged two `nvlddmkm` Event 153 records, Java preserved the original failure, and the
  integrated server saved every dimension. This establishes observed propagation and save behavior
  for that incident, but does not identify the GPU fault or replace the controlled G3 cleanup
  protocol; the same artifact passed the named tests in the preceding and following processes.

These gates validate the A/B/C/D failure-boundary batch only. They do not close FG GUI composition,
generic UI path tracing, external-section backpressure, Ponder VRAM work, NVIDIA DLL redistribution,
or the rest of the renderer acceptance matrix.

## Unified thread-closure acceptance

Status: directed source repairs implemented and automated/runtime-observed; remaining experience
and GPU failures explicitly limit the new isolated candidate. This does not imply visual acceptance.

- FG now separates world alpha at the GUI boundary, preserves fractional coverage, mirrors vanilla
  vignette modulation onto HUD-less, and applies matching menu blur. Real input captures contain
  fractional alpha and exact final/HUD-less agreement outside UI. Local inversion uses affine replay; nested blur uses preweighted transmission and GPU radius rounding.
  Generated-frame motion/edge quality still needs visual acceptance under the fixed SDK contract. Validate generated display frames,
  not only real-frame inputs; no background-freeze or edge compromise is pre-approved.
- UI PT now has one active expensive executor, two independent view-history slots, serial shared
  dispatch resources and a common same-frame TLAS. Capture precedes execution; cropped physical
  viewports and bounded caches replace full-window per-view pipelines. Paused geometry and retired
  resource counts have bounded observations, but long churn and transition light interaction need
  acceptance. The 3840x2054 preflight produced SDK VRAM-budget warnings, so no performance success
  percentage or hardware-wide capacity claim is available.
- Texture tasks now carry owner/image generations and use an atomic check/JNI-use boundary; RT
  descriptor snapshots retain recorded bindings until frame retirement. Production helper tests
  cover stale upload, cancel, release and reload. Sustained resource-pack/section churn remains a
  runtime gate; pool size alone is still not the correctness argument.
- Priority temporal guides and visible Flywheel large-coordinate transforms retain their existing
  source fixes. Absolute light-grid precision and real SR/RR history remain separate boundaries.
- G3 retains the earlier partial observation. On the later candidate, Advanced at 2560x1440 failed
  naturally before Ponder with query-result `VK_ERROR_DEVICE_LOST` and Windows Event 153; Java
  preserved the first error, all dimensions saved and native close completed. GPU root cause is
  unknown; this does not validate that shader path or retrospectively prove the earlier close.
  Do not manufacture device loss or change TDR. Keep the failed path out of user stress repeats.
- Public binaries remain blocked until `THIRD_PARTY_RUNTIME_AUDIT.md` is resolved. Local isolated
  validation does not imply public redistribution approval.


### FG boundaries and Advanced directed follow-up (2026-09-22)

Status: implemented and automated-verified; final candidate client preflight and visual matrix are
tracked in the latest paired ledger entry. Preserve the inherited texture ownership, shared UI PT,
resource isolation and external admission implementation; no parallel redesign is authorized.

- FG real-frame composition: source-bound affine inversion with pre-draw depth/stencil and weighted
  blur after existing UI; no opaque full-screen mask or draw-order change. Half-integer radii use
  matching GPU rounding. Recheck generated crosshair anchoring and background motion/blur edges;
  fixed SDK scene-input processing is not a post-generated-frame GUI callback. No visual downgrade
  has been approved, and mathematical reconstruction alone cannot close P2-02.
- Advanced: shared execution-variable descriptor retargeting is source-repaired in RT/post stages.
  The earlier natural loss has propagation/save/native-close evidence, but GPU causality and SDK
  completion are not established. Keep the failed Advanced pressure/reproduction path out of this
  user matrix; do not relabel Vanilla preflight as whole-product or Advanced acceptance.
- Exact source/dependency/artifact/capture identities and commands are in the isolated evidence root
  `build/manual-acceptance/evidence/20260922-fg-boundaries-advanced`. Complete user acceptance before
  a future candidate review/amend. NVIDIA binary publication remains separately blocked.


### Acceptance hold after the Vanilla FG-off fault (2026-09-22)

Status: investigating; world acceptance paused. Supersedes the Advanced-only exclusion above.
Candidate3 passed six FG input checks, then its FG-off reference lost the device before the first
capture/Ponder/resize/reload. The exact incident and post-fatal stage-guard repair are in the latest
paired ledger and `20260922-fg-boundaries-advanced/preflight5` evidence. Preserve positive input/GPU
tests, but do not close generated-frame visual acceptance, real loss root cause or full G3. Do not
repeat this world-entry/FG-off path or Advanced stress for user acceptance until a bounded safe
investigation is prepared; menu/loading checks remain separate. The final artifact's menu result
must not inherit candidate3's world result. All existing source fixes and publication gates remain.

### First GPU fault: directed investigation update (2026-09-23)

Status: investigating; unified world acceptance remains paused. Supersedes only the
prior final artifact identity, not its historical findings. See the latest paired ledgers,
the appended dated review and `build/manual-acceptance/evidence/20260922-first-gpu-fault`.

- privateData enablement, three post-color producer barriers and command-buffer begin
  error propagation are source-repaired with the stated validation/test scope. A later
  normal-world loss disproves treating these as the established root cause.
- Installed VVL AS-checker corruption is independently proven; its false positives/input
  rewriting are excluded from unqualified app-UAF evidence. Do not patch AS retention merely
  to silence this checker. Preserve the existing pipeline-layout workaround and its limits.
- Corrected local Aftermath capture is default-off, external to the mod package and enabled
  before SERVICE device creation. It is ready for bounded isolated collection, but no dump
  was produced yet. Natural first-fault shader/resource or pre-fault invariant evidence is
  still missing; do not repeat blind crash loops or identify a detection fence as the cause.
- Final diag6 normal world20s and diagnostic world20s both saved/closed successfully; the
  earlier diag4 normal loss retains separate save/native-close evidence. Short success does
  not establish causal repair, long stability, actual capture completion on loss or full G3.
- Next evidence gate: obtain and decode a useful unvalidated natural-fault capture on an
  identified isolated triggering operation; if capture changes timing, prepare a valid matched
  SDK/non-SDK control with complete non-DLSS inputs before running it. No such bypass is
  implemented or accepted as a permanent workaround. Full manual stress remains on hold.

FG generated-frame visual acceptance, UI PT churn/transition acceptance, Advanced causality
and public NVIDIA binary authorization remain open. No independent feature/Git work is implied.


### 2026-09-23 local closeout: acceptance and checkpoint policy correction

Status: source/automated portions implemented; current manual/visual acceptance pending;
first GPU cause investigating; public binary authorization blocked.
Supersedes the unconditional world-acceptance and pre-amend holds above by explicit user decision.
See the [bounded closeout status table](audits/2026-09-22-gpt6-pro-code-review-verification.md#2026-09-23-bounded-local-closeout-and-acceptance-checkpoint)
and [implementation ledger](DEVELOPMENT_LEDGER.md#2026-09-23-bounded-local-closeout-and-acceptance-checkpoint) for the fixed source/package evidence.

- An unknown first GPU cause is not itself a prerequisite for normal isolated manual acceptance or
  local amend. Definite invalid ownership/submission paths must still be repaired; two direct
  upload/command error continuations were repaired in this pass. No new architecture is authorized.
- Final-package normal PID30596 failed about12s after world-ready. All dimensions saved and native
  close completed once with unchanged242/242 submissions; first cause retained. The case is paused;
  no automatic retry/pressure replay. This does not cancel the user's decision or turn the failure
  into a normal-flow pass. Full G3 and causal diagnosis remain distinct from save/close evidence.
- Current-package lifecycle/save-reopen, PackType F3+T versus /reload, chunk/Sable churn, shared UI PT
  viewport/transition/mutual lighting/retirement and FG generated-frame visuals need the prepared
  continuous manual matrix. Earlier accepted G0/G1/G2 and other artifacts retain their own scopes.
- Require actual focused FG generation before judging generated frames. Preserve fractional alpha,
  local inversion/blur order and joint PT scene semantics; no screenshot or disabled-effect substitute.
- Sustained important-queue fairness, high-resolution SDK-inclusive memory trends, moving priority
  object temporal inputs, arbitrary Veil paths and long stability remain bounded investigation or
  acceptance limits, not new implementation promises in this checkpoint.
- Public NVIDIA/vendor binary permission remains external. The local distribution gate's expected
  rejection proves engineering enforcement only. No new remote push or publication is authorized.

### 2026-09-23 manual feedback and source-only closeout

Status: limited simple-play observation accepted; remaining runtime/visual gates deferred beyond
this observation round; first GPU cause unknown; binary authorization blocked.
See the [manual feedback ledger](DEVELOPMENT_LEDGER.md#2026-09-23-frozen-package-manual-feedback-and-source-synchronization-scope)
and [audit correction](audits/2026-09-22-gpt6-pro-code-review-verification.md#2026-09-23-manual-feedback-correction-after-the-frozen-checkpoint).

- PID73024's two different world sessions saved and closed normally; no same-world reopen/edit
  persistence, specialist Ponder/FG, reload/chunk churn or long stability pass is inferred.
- Retain PID30596's real GPU failure and PID92452's separate initialization null TextureManager
  defect. Later targeted startup work must cover resize callbacks before client texture-manager
  readiness, without creating the missing-texture fallback prematurely; verify normal release,
  partial initialization and native close. This record does not authorize implementing that work.
- Preserve existing thirteen-item/expanded-scope boundaries, full G3, generated-frame visuals,
  shared UI PT memory/transition acceptance and public runtime license gates. No root-cause or
  binary compatibility conclusion follows from the user's simple-play feedback.
- The user now authorizes maintenance-record-only amend and source synchronization, superseding
  the earlier no-push scope for this closeout. No tag, Release, binary upload or further test run
  is authorized or needed to record these observations.

2026-09-23 scoped delivery checkpoint: face semantics and interaction/background admission are
build/automated verified; the paired ledger records the isolated packages. Next gate is live
SERVICE capture plus baseline / faces-only / combined measurements after the user closes the
competing client. Idle/F3+A probe and raw clock/revision/CPU/payload/VRAM collection are prepared;
repeatable existing-region-read and new-generation travel cases, complete producer visuals and
manual interaction/throughput acceptance remain pending. No measured improvement is claimed.

2026-09-23 face/chunk startup correction: optional Veil/Sodium class frame resolution
blocked PID46548 before the menu. Actual-call-only SERVICE discovery is built/tested;
manual acceptance uses the corrected B package. Correct A-trace before any baseline/A/B
performance comparison; neither dry-run nor bootstrap tests establish client readiness.
See the [startup correction ledger](DEVELOPMENT_LEDGER.md#2026-09-23-face-interception-startup-correction).

### 2026-09-23 face/chunk manual acceptance follow-up

Status: observed face/chunk/Sable cases accepted; six visual cases investigating.
See the [manual feedback ledger](DEVELOPMENT_LEDGER.md#2026-09-23-manual-facechunk-feedback-and-six-visual-regressions).
Preserve the accepted single-sided rules, safe publication and fixed PT line-width policy.

- Ponder: previous/next transitions after dwelling must retain scene colors; verify reflection
  content and stable crop edges. Correlate changing viewport/output/history and SDK viewport
  counts; preserve same-frame shared-world lighting and in-flight ownership.
- Glow lichen: compare floor/wall/ceiling UV, alpha/emission footprint and orientation.
- Sable particles: isolate structural environmental light from intrinsic particle emission;
  campfire smoke must not acquire emission merely from a coordinate/light-source mismatch.
- F3+G: retain all original emitted grid/red-surrounding segments and colors, apart from the
  already approved PT thickness/emission semantics; verify actual captured/converted counts.
- PID68896 disappearance: no normal exit evidence; preserve partial logs. Resolve launcher
  ownership before another manual launch. No automatic replay or GPU-crash attribution.

These are reported failures and acceptance gates, not source fixes or passed regressions.
Matched performance comparison remains pending and must not reuse this changing-resolution run.

2026-09-23 visual-feedback status: Ponder moving viewport/history retirement, secondary
visibility and coverage alignment plus particle ambient/emission separation are implemented
and automatically checked. Retest scene dwell/forward/back transitions, water, crop edges
and Sable campfire smoke in the visual-feedback package. Ground lichen remains investigating:
capture the actual material/normal/depth output before editing its aligned texture mask.
F3+G's alternate topology is the enabled Sable loaded-chunk renderer; no grid policy changed.
See [evidence and limits](DEVELOPMENT_LEDGER.md#2026-09-23-ponder-transition-and-particle-feedback-corrections).
Matched performance measurement, unknown GPU-loss cause and binary licensing remain open.

### 2026-09-23: Ponder archive and accepted Sable feedback

Status: Ponder PT archived; default raster restoration implemented, automated/runtime checks
recorded in the [ledger](DEVELOPMENT_LEDGER.md#2026-09-23-ponder-raster-restoration-and-create-stencil-crash).
This supersedes the preceding PT transition/water/crop retest requirement; those defects are
retained in the archive rather than declared repaired. User acceptance of default Ponder and
Create configuration behavior remains pending. Keep the observed intermittent widget-icon issue
separate from PT scene flat color; do not guess a depth-state repair without evidence.

Sable campfire particles are visually accepted by the user on the visual-feedback package.
F3+G's replacement is the known Sable configuration; the user accepts automatic translation
for the observed Sable structures/debug output, not every possible third-party renderer.
Ground lichen is still investigating: the clarified symptom is missing pixels, not established
rotation. Acquire a precise affected view before modifying its aligned mask. No arbitrary texture
rotation, Sable setting change or geometry removal is authorized by this record.

2026-09-24 clarification and targeted evidence: the user corrects this to a 180-degree mismatch
between the visible face and the surface participating in PT, not missing front-face texels.
Minecraft 1.21.1's UV-locked floor/ceiling bake gives coincident opposite faces different UVs;
the wall pair agrees. The [targeted investigation](DEVELOPMENT_LEDGER.md#2026-09-24-glow-lichen-opposite-face-uv-investigation)
records the real baked vertices, primary GPU mask, final image comparison and upstream-baker
tests. Final user disposition on 2026-09-24: accepted as normal upstream model behavior; no
correction is requested. This supersedes the preceding lichen investigation/retest requirement
and withdraws the proposed opposite-face UV normalization. Preserve both single-sided faces,
their original UVs and existing PT material behavior. See the
[acceptance decision](DEVELOPMENT_LEDGER.md#2026-09-24-glow-lichen-accepted-as-normal-upstream-behavior).
This closes this reported symptom, not unrelated material behavior or exhaustive PT validation.

### 2026-09-24: Raster equivalence and Ponder-only execution isolation

Status: investigating; comparison protocol and retirement recommendations proposed, not implemented.
The user requests evidence of original OpenGL raster behavior and a distinction between Ponder-only
complexity and reusable engine improvements. The [targeted assessment](research/RASTER_PARITY_AND_PONDER_RETIREMENT.md)
owns source findings, the inspection fingerprint, proposed retention table and differential gates.

Acceptance requires original producer coverage (including early canceled calls), same-input GL/Vulkan
state/shader/attachment comparisons and deterministic real-screen replay. First verify global
shadow/light scope gaps and observable alpha semantics; neither translated draws nor a few correct
screenshots establish universal parity. Keep main-world PT semantics and the archived Ponder decision.

Any later isolation must eliminate unnecessary active UI PT work while preserving normal in-flight
resources, histories, synchronization and independent correctness fixes. Report matched real-frame
time, resource counts and memory before claiming optimization. This entry authorizes no automatic
rollback, new renderer migration or reactivation of Ponder PT; no such code change was performed.

### 2026-09-24: Authorized raster and Simulated rendering follow-up

Status: implemented/build-verified and bounded automated/runtime checks; user visual acceptance
and exact GL parity remain open. Implementation details and remaining differences are appended to
[the targeted research](research/RASTER_PARITY_AND_PONDER_RETIREMENT.md#2026-09-24-authorized-implementation-and-simulated-source-comparison).
Supersedes the preceding assessment's no-implementation boundary, not its evidence limitations.

1. Repair evidenced UI 3D translation gaps and use actual rendering to investigate additional
   differences. Keep world PT policy separate from all raster producers, including non-Catnip UI.
2. Isolate Ponder-only active work according to the retention assessment, with regression checks;
   preserve independent ownership/synchronization fixes. Ponder PT remains archived.
3. Re-evaluate Simulated diagrams, especially UI fade/composition, against the pinned original
   implementation. Preserve original behavior except the user-rejected pixelated presentation.
4. Audit spring stress red coloration and integrate its surface-color meaning with the PT
   surface-overlay contract used for entity hurt coloration; do not interpret stress as opacity
   or invent emission.
5. Investigate restoring staff lock icons as ordinary-camera-invisible geometry participating
   fully in PT plus a separate depth-independent redraw, matching nameplate/glowing geometry.
   Determine emission from the original producer/shader rather than the icon's appearance alone.
6. Bring staff drag waves into PT and correct the staff-side attachment. Inspect the original
   plunger and staff first/third-person producers before changing endpoints: distinguish two
   actual ropes from one world rope with a first-person-aligned origin. Audit other staff draws.

Acceptance: production-path tests and an isolated paired build; original-versus-translated
diagram fade/draw order, GUI scales, spring stress and hurt overlays under PT, staff lock
occlusion/reflection and source-derived emission, staff/plunger endpoints across both hands,
camera modes and orientations. Machine/runtime evidence and user visual acceptance remain
separate; no production/Prism changes or Git staging/commit/push/public binary release.

Completed source work in this batch: raster scope/shadows and external fragment coordinates;
lazy UI PT command recording/submission; diagram gradient fade/origin and preserved stress data;
shared PT surface recoloring; original staff lock/beam PT capture, correct priority cutout semantics
and held-anchor transform. Ponder PT stays archived; independent texture, upload, descriptor,
synchronization, temporal-input and failure fixes stay active.

Remaining diagram semantic differences are concrete: decorative greebles use projected block
bounds rather than final alpha occupancy, and the former 12-Hz cached redraw is not reproduced.
Do not fold these into the authorized removal of pixelated enlargement or declare the whole diagram
equivalent. Exact coverage placement needs the actual diagram image/coverage at the original
initialization point; acceptance covers cutouts, partial models, faded edges and entities without
extra synchronous readback every frame. Sticky notes, force arrows and linked structures need
their own producer/visual comparison. Staff/plunger acceptance includes cached-anchor timing during
fast movement and both hands; a matrix round-trip alone does not prove attachment on screen.
Matched GL replay and quantitative before/after performance were not performed. These remain
explicit verification work, not newly approved blanket rendering rewrites.

### 2026-09-24: Diagram parity scope correction after rendering-preference re-audit

Status: investigating; static differences identified, repair and matched runtime/visual
acceptance pending. Supersedes the preceding implication that only greeble placement and the
12-Hz refresh differ. See the [bounded re-audit](research/RASTER_PARITY_AND_PONDER_RETIREMENT.md#2026-09-24-diagram-semantic-re-audit-and-correction).

Preserve darker block/brighter BE-entity stages, source-defined spring/rope ownership and
eligibility, original entity/force selection, Flywheel fallback and producer-internal overlays.
Do not equate physical connections with diagram membership or append all world/global renderers.
Pixelated enlargement is the only authorized semantic exception.

Repair scope: stage lightmap content lifetime, layer order with safe nested buffers, fractional
placement/unclipped viewport, final-alpha occupancy, cached refresh, resource-pack post assets,
compiled-section extension inputs and cleanup on the actual replacement entry. Confirm Sable
lighting/fog and backend-specific BE populations before prescribing changes. This re-audit
modified documentation only.

Gates: original-versus-translated stage lighting and transparent overlap; owner/attachment/hover
and Flywheel cases; note sliding/crop/GUI scales; cutout/entity/fade occupancy; refresh/reload/close;
asset override and section-extension fixtures; renderer failure preserving first error and acquired
state. Existing shader fixtures/ordinary play do not establish these results. Keep Ponder PT
archived and world PT ownership, material and synchronization work independent.

Decision update: the user subsequently cancels the 12-Hz cap and low-resolution pixel enlargement;
keep paper colors, outlines and original fade/palette/dither. D5 restoration is superseded, not
implemented. Follow the [updated presentation contract](research/RASTER_PARITY_AND_PONDER_RETIREMENT.md#2026-09-24-diagram-presentation-decision-and-implementation-acceptance).
All remaining original semantics require production-path and final-image checks, especially
cutout, fractional alpha, depth and ordered composition; calling a renderer alone is insufficient.

Implementation update: D1/D2/D3/D4/D6/D7/D8 now use the original producer/post/GUI chain with scoped
Vulkan resources; D5 is intentionally superseded by uncapped physical-resolution presentation.
Matched GL/Vulkan fixture testing also corrected compile-time translucent sorting and raster AO.
See the [implementation and artifact record](DEVELOPMENT_LEDGER.md#2026-09-24-original-diagram-producer-restored-with-physical-resolution-presentation).
Status: build-verified; bounded runtime-observed; deployed for user acceptance, not full parity.
Raw RGBA/alpha and reload checks pass for the recorded fixture; final paper edge RGB differences
and intermittent extra regions in earlier runs remain explicitly unresolved. Next acceptance is
mechanism owner/controller/attachment/virtual/hover cases, populated note scope/slide/crop/fade,
GUI scaling, asset overrides, section extensions, alternate backends and failure recovery. Do not
restore the retired frame/resolution cap or reactivate Ponder PT as part of those checks.

Deferred user reports (2026-09-24): **S1** twisted spring shows apparent inner surfaces/texture and
turns black; **S2** diagram spring loses its apparent textured cutout and becomes black; **S3** an
attached plunger renders but its diagram rope endpoint is offset. Root causes and cross-path scope
are unconfirmed. Follow the [separate reports and acceptance conditions](research/RASTER_PARITY_AND_PONDER_RETIREMENT.md#2026-09-24-deferred-spring-and-plunger-visual-reports).
Record-only authorization; do not count these mechanism cases as visually accepted or infer that
the existing block cutout fixture covers them.

## 2026-09-24: Optional diagnostic module formalization

Status: implemented; automated-verified; bounded runtime-observed. The maintained source is
`Modules/RadianceAudit`, not ignored `dev/`. See the
[implementation and evidence](DEVELOPMENT_LEDGER.md#2026-09-24-tracked-optional-radiance-audit-module-and-native-collector).

Remaining acceptance: detailed category sampling in representative worlds, active G1/G2 scenarios
with the external controller, and broader vanilla-only coverage. Early Aftermath/device-fault hooks,
GPU-owned readback code and explicit native traces remain in MCVR. The one-shot scene replay
system was subsequently retired as recorded above. Further physical extraction
must preserve device-creation timing, in-flight retirement and shutdown; no complete diagnostic-code
removal or rendering-coverage proof is claimed. Legacy evidence is retained; vendor redistribution
and all previously open GPU/visual conditions stay independent.

## 2026-09-24: Replay-retirement manual observation checkpoint

Status: user observed no issue during limited actual play; source amend/synchronization authorized.
The [manual record](DEVELOPMENT_LEDGER.md#2026-09-24-manual-feedback-and-source-checkpoint-after-replay-retirement)
identifies the deployment, two-world save/exit observations and remaining log limitations. The
one-shot replay tool stays retired; derived optimizations and the formal optional Audit mod remain.
No exhaustive Ponder/FG/PBR, fault-injection, native-close or long-duration acceptance is inferred.
GPU-loss investigation and public binary licensing keep their preceding independent boundaries.
