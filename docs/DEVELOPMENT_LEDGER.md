# Development ledger

## 2026-09-22: native error-boundary follow-up repair

Status: source-fixed; paired automated verification passed; client runtime acceptance pending.
Evidence: remote follow-up review against Radiance `c7ea3e6dc7fea62468b681481a5c7a9f7cdbefb9`
and MCVR `dadb7ecb9fcc3b333d48bb0f76b906c979e63ae3`, native RelWithDebInfo build,
MCVR CTest, and a fresh Radiance JUnit run.
Corrects: the earlier P1-03 source-closure entries, which did not cover four remaining exception
and state-publication gaps.

The follow-up confirmed that the prior source-closure claim was too broad. MCVR still allowed a
later `std::thread` construction failure to destroy joinable workers, performed throwable detail
work before publishing a `noexcept` device failure, conflated the immutable first cause with the
monotonic device-lost cleanup state, and passed a length-bounded JNI UTF-16 borrow directly to
`GetModuleHandleW`. The paired native repair now joins workers on both launch and task failures,
publishes the minimal error code before allocating or locking for diagnostics, preserves the first
cause while independently latching device loss, routes renderer/framework/device/FSR cleanup
through that latch, and copies JNI strings into owned terminated storage with local-reference and
borrow release guards. Direct JNI string borrows in the first-party middleware and loading source
were consolidated through the same helper so a pending Java exception stops normal work without
being replaced.

The paired root-cause pass also checked Radiance's renderer-initialization thread, early-window
scheduled executor, chunk executors, native declarations, fatal-description/close callers, and
physical-client bootstrap boundary. Radiance owns no partially constructed native thread vector;
its one renderer initialization thread already captures the worker `Throwable`, joins, and reports
it on the caller. No matching Java-side product defect was found, so this batch does not alter
Radiance product code.

MCVR's targeted failure contract and final complete CTest suite passed 30/30, including the four
build-host Vulkan tests. Its RelWithDebInfo `core.dll` built with SHA-256
`95EB80A6F0C99A9B377CD66FC187A76AACD58C1B11D698095FA8BAFB60FCEAD5`. Radiance
`gradlew test --rerun-tasks` passed. Radiance product code did not change in this follow-up; its
changes are this ledger, the audit correction, and the two explicitly deferred roadmap items.

No native binary was installed or packaged, and no client was started. A true post-world fatal,
an asynchronous Streamline device-loss callback followed by game shutdown, integrated-server
save/stop, driver behavior after actual device loss, and user-visible error presentation remain
runtime gates. P2-02, generic UI PT, and Ponder memory work were not changed.

## 2026-09-22: single-port-commit history policy correction

Status: adopted as repository-maintenance policy; no product or acceptance claim.
Evidence: explicit user decision and local Git-history verification.
Supersedes: the new paired remediation-commit recommendation in the pre-commit state audit below.

The maintained Radiance and MCVR branches keep upstream history followed by exactly one signed
`Initial port` commit. Later work is amended into that commit with its original author and
committer identities, timestamps and time zones, message, and upstream parent. Backup refs and
bundles are temporary rollback aids: retain the minimum verified recovery bundle while a local or
remote rewrite is not yet closed, then remove superseded project-maintenance backups rather than
accumulating one per amend. Unknown or uniquely evidentiary material is preserved.

The folded paired MCVR commit is `9ebf73cc57290dbbdc9a6dbdd8f19f5989c45dbd`. Radiance records
that native SHA in one direction and does not record its own SHA, so no reciprocal amend loop is
created. This history-policy correction does not change any source, automated, runtime, visual, FG,
generic UI PT, or public-release evidence boundary recorded below.

## 2026-09-22: paired pre-commit state audit

Status: documented; no staging, commit, amend, push, cleanup, product edit, or new validation was
performed by this checkpoint.
Evidence: Git worktree/index/ignored-file inventory, remote/ref comparison, GitHub signature
verification, runtime-file hash comparison, build provenance, and bundled license texts.

Radiance `develop` and `origin/develop` both point to the valid SSH-signed `Initial port` commit
`249f9a63bd861a3273d2d43ff6e594bff980c66e`. The worktree has 27 tracked changed paths and seven
untracked candidate paths; the index is empty. Candidate content consists of product code, build
configuration, tests and maintained documentation. Generated builds, packaged run directories,
logs, the ignored external diagnostic mod/compatibility lab, native install outputs and SDK DLLs
remain excluded. The paired audit contains the complete classification and MCVR counts.

The thirteen-review status is now normalized in the dated audit. P1-01, P1-02, P1-03 source
closure, P2-01, P2-03 through P2-06, P2-08, P2-09's developer-publication decision, and P3-01 have
source and automated evidence at their stated scope. P2-02 remains open. P2-07 has persistent
composite reuse but retains the full-window/shared-pipeline memory and visual design work. The
physical-client source audit and dedicated-server discovery check passed their stated scopes;
post-world fatal injection, integrated-server save/stop, actual device loss, broad gameplay and
visual acceptance remain runtime gates.

The installed ignored runtime matches the sibling MCVR outputs, and its upstream inputs are pinned.
This supports reproducible local packaging; it does not by itself authorize public redistribution.
The NVIDIA SDK distribution, attribution/trademark and notification terms, including their
interaction with a GPL-3.0 release, remain a public-binary-release gate. Streamline and XeSS notices
are present. This is not a source-commit blocker and is not recorded as a legal conclusion.

The preferred next history step is a new paired remediation commit, preserving the two signed
`Initial port` commits as the external-review baseline. An amend remains technically possible only
after explicit approval and exact preservation of author/committer identity, timestamp and message,
fresh SSH signing, backup refs/bundles, force-with-lease, and separate recording of old baseline IDs
and new remediation snapshots.

## 2026-09-22: P1-03 source closure and physical-client boundary

Status: implemented; build-verified; automated-verified; isolated runtime acceptance pending.
Evidence: complete first-party native source/JNI inventory, Java and native builds, JUnit,
bootstrap tests, CTest, build-host Vulkan GPU tests, distribution verification, and hashes.

MCVR now contains no direct first-party process-exit call. Its 182 GAME/SERVICE JNI exports share a
typed boundary: initialization remains retryable, the first runtime/invariant/device-lost failure
is sticky, normal calls are rejected after fatal, and diagnostic read plus idempotent close remain
available. Worker exceptions return to the invoking thread, foreign callbacks cannot leak C++
exceptions, asynchronous FG device loss enters the global failure state, and device-lost cleanup
does not wait for an idle GPU. Cleanup reads an allocation-free atomic device-loss result rather
than copying the diagnostic snapshot inside a destructor. Swapchain recreation preserves typed failures rather than flattening
them. The paired MCVR ledger contains the detailed native classification.

The separate client-boundary audit found that the SERVICE locator could publish the nested GAME
mod during a dedicated-server launch and that a shared resource-manager Mixin could begin a Vulkan
reload for the single-player logical server. All three SERVICE entry points now use the actual
launch target, the nested GAME mod is skipped outside a physical client, and reload work is gated
by identity with Minecraft's client resource manager. Shared-class Mixins are listed as client
Mixins. The PNG identifier stream is a fully delegating `FilterInputStream`, so closing it closes
the pack resource; non-image data-pack streams are no longer wrapped. Static searches found no
custom packet registration or server event subscriber, while world hooks require `ClientLevel`.

The paired product-source snapshots are Radiance
`AE5A85BF886B6773ADD2883EC7BCE7F781221C52BB3F6CF1547CD1B753D9677A` and MCVR
`4CE216AF5378A526243437EE3C9FD062A7807F7307A0DD60EFCBEE138F6F3137`. The MCVR build output,
installed Radiance resource, and distributed-JAR `core.dll` share SHA-256
`5AA4C808C2C766270491FF93389D4ECE39350385CEE204B319A8BD8959860FB3`; the distributable JAR is
`D1D48302BA7885EB5223F07EB6AFEDC20810E13D743ED40D99B7163DDDAF2FFF`.

`prepareRuntime build preparePackagedClient`, the Radiance JUnit suite, and SERVICE bootstrap tests
passed. MCVR's final complete Release CTest result is recorded in its ledger. A later isolated
`runPackagedServer` check installed only the distributable JAR and reached server `Done (3.300s)`
with only Minecraft and NeoForge in the discovered mod list, no nested Radiance GAME mod, and no
extracted native payload. The Gradle wrapper had to be interrupted after readiness because it did
not forward `stop`, so graceful shutdown is not claimed. Post-world injected fatal failure, actual
device loss, integrated-server save/stop, and visual flow remain runtime acceptance rather than
source claims. P2-02 and the generic UI PT design were not changed in this batch.

## 2026-09-22: Ponder `FeatureNotSupported` runtime diagnosis

Status: reproduced and identified; crash contained by the batch-1 fallback; memory repair pending.
Evidence: full compatibility packaged-client runtime and named Streamline result capture.

The complete `runManualFull` set reproduced the Ponder failure after a resize to 3840x2054. The
native wrapper had renamed every Streamline failure to NGX `FeatureNotSupported`; the real result
was `slEvaluateFeature -> Result::eWarnOutOfVRAM (39)`. Multiple full-window Ponder PT/RR pipelines
overlapped during resize and a two-scene transition, while native allocation reporting reached
about 13.8 GiB. The existing current-frame fallback prevented the former Java crash, although the
instance remained severely frame-rate limited under the unresolved memory pressure.

MCVR now preserves the failing stage, named/raw result, feature, viewport, frame and dimensions in
`radiance-streamline.log`, queries SR/RR viewport requirements at startup, and rate limits repeated
identical records in the next build. The full native Release build and CTest suite passed 30/30;
the paired Java build, tests, distribution verification and full-instance preparation passed.
This is runtime proof of the return code and trigger conditions, not yet a fix for Ponder memory
ownership or a visual acceptance result.

## 2026-09-22: native failure containment batch 4, active-path slice

Status: partially implemented; build-verified; automated-verified; deployed to the repository
packaged client; runtime acceptance pending.
Evidence: static, build, automated, build-host Vulkan GPU tests, and artifact hashes.
Applies to: the same uncommitted paired worktrees as batches 1 through 3.

Historical intermediate state: the source-closure entry above supersedes the current P1-03 status
and removes the 61 exits that remained in this slice.

The paired native renderer no longer terminates the JVM from active present, submitted-frame
readback, screenshot-fence, optional NGX-directory, ray-tracing shader-pack, or overlay-buffer
validation paths. Vulkan operational failures in the frame path enter the existing renderer
failure channel; guarded JNI operations translate C++ exceptions into Java
`IllegalStateException`. This is the first bounded slice of P1-03, not completion of the native
failure program.

The remaining 61 direct exits are concentrated in Vulkan construction, resource validation and an
older framework implementation. They have not been mechanically changed: about 173 JNI exports
exist and exception protection is not yet uniform, so an exception that crosses an unguarded JNI
or worker boundary would still terminate the process. The next native slice must first close those
boundaries and classify initialization, runtime/device-lost, and invariant failures.

The paired product build completed `prepareRuntime build preparePackagedClient`. MCVR Release CTest
passed 30/30, including four Vulkan GPU tests. The installable JAR and packaged-client copy share
SHA-256 `326BD801CFE84FF58D61FF27E2CEEFDC7AFE20F5FA96CE509276497F4BCF6560`; the embedded and native
output `core.dll` share SHA-256
`0C58A958D1AD89C8997861733E7240CDBDF67AD574BBFF6A8C24D26487B1B6D7`. These hashes prove deployment
identity, not gameplay or visual correctness.

## 2026-09-22: reusable external sections, Ponder resources, and shader types batch 3

Status: implemented; build-verified; automated-verified; runtime and visual acceptance pending.
Evidence: static, build, automated, and build-host Vulkan GPU tests.
Applies to: the same uncommitted paired worktrees as batches 1 and 2.

External Sable/Aeronautics section handles now carry a slot generation. MCVR reuses released CPU
slots through a free list, rejects stale Java calls and queued build completions, creates fresh slot
state for each generation, and retires the old GPU resources through the existing frame retainer.
The stress contract performs 100,000 allocation/release cycles at constant live cardinality and
proves both bounded slot capacity and stale-handle rejection. Live mod churn remains a runtime gate.

Ponder now keeps its composite output image and descriptor table per scene and swapchain frame
instead of recreating them on every draw. The existing full-window render extent and crop behavior
were deliberately left unchanged: choosing a smaller viewport needs separate framing, GUI-scale,
transition, and visual evidence. The source contract verifies steady-state reuse and scene-owned
retirement.

Veil `bool`/`bvec` and `uint`/`uvec` uniforms now preserve their logical GLSL kinds while retaining
the same 32-bit std140 storage. Both the Veil parser and the generic uniform-array parser retain
these types; upload still copies their underlying integer words. A real Veil translation test
checks branches, Boolean vectors, high-bit unsigned masks and unsigned operators, then compiles the
translated shader to SPIR-V with `glslangValidator`.

The Maven publication is now explicitly the developer-only `Radiance-game` artifact. It contains
the GAME layer and NeoForge metadata but intentionally excludes the SERVICE bootstrap, native
renderer, shaders, and optional runtimes. `distributedJar` remains the only user-installable JAR,
and a build verification task enforces that separation.

MCVR's complete Release CTest suite passed 30/30, including four Vulkan GPU tests. Targeted
Radiance shader translation and compilation tests passed. The later batch-4 product build reran the
complete Radiance suite, distribution verification and packaged-client preparation successfully.
Ponder framing, Veil mod behavior, external-section churn, and Maven consumer use have not received
runtime or visual acceptance.

## 2026-09-22: camera, chunk admission, and Flywheel repair batch 2

Status: implemented; build-verified; automated-verified; runtime and visual acceptance pending.
Evidence: static, build, automated.
Applies to: the same uncommitted paired worktrees as batch 1.

Chunk build capacity is now reserved atomically before `RenderChunkRegion` creation. The permit is
transferred to the accepted worker and is released exactly once by task cleanup, region
unavailability, preparation failure, or executor rejection. A rejected preparation is re-dirtied
and requeued rather than leaving `running` or the in-flight count stuck. `ChunkBuildPermitTest`
covers capacity and idempotent release; the complete Radiance JUnit suite passed.

The Flywheel dynamic light-section pipeline was removed after a cross-repository consumer audit
found no call to its shader sampler. Radiance no longer collects or uploads 18-cube section data,
and the unused Sable relationship helper was removed. The per-instance lighting scene, sky-light
scale, light texture, constant ambient flag, and shader-light directions remain active because
they still feed instance appearance. A bytecode regression test prevents the removed JNI traffic
from returning.

The paired MCVR entry records the native buffer, descriptor, and shader removal and the shared
camera-ray repair. No packaged client was launched or deployed. Required manual acceptance remains
Ponder priority/background alignment, high-distance and F3+A chunk convergence, and Flywheel/Create
appearance under representative lighting.

## 2026-09-22: renderer failure-state repair batch 1

Status: implemented; build-verified; automated-verified; runtime acceptance pending.
Evidence: static, build, automated.
Applies to: uncommitted Radiance worktree based on
`249f9a63bd861a3273d2d43ff6e594bff980c66e`; paired MCVR worktree based on
`b0173ab855d4f693a0a62216ec07197b184490b5`.
Remaining acceptance: packaged-client resize/fullscreen testing and unavailable-Streamline FG-off
testing after deployment.

The Java frame boundary now distinguishes an acquired frame from a transiently unavailable
swapchain image. When MCVR exhausts its bounded out-of-date retries, Radiance skips
`GameRenderer.render`, does not submit or present the old context, and retries acquisition while
the rest of the client tick continues. The same lifecycle state guards the public split submit and
present methods as well as the combined path.

`AppliedShaderStateTest` now waits through a `Future`, so a worker assertion fails the JUnit test
instead of being printed on an unobserved thread. `FrameLifecycleTest` covers retry, fatal failure,
and successful acquisition transitions.

The complete Radiance JUnit suite passed. This is automated evidence only; no packaged client was
launched and no artifact was deployed in this batch. The paired native design and its 27-test
result are recorded in MCVR's ledger. The finding-by-finding scope and remaining work are in
[`audits/2026-09-22-gpt6-pro-code-review-verification.md`](audits/2026-09-22-gpt6-pro-code-review-verification.md).

## 2026-09-21: cross-task investigation import

Status: implemented as documentation only.
Evidence: static task and repository-document inspection; no product runtime or visual claim.

Four Codex investigation tasks were read in full and their user-requested directions were separated
from their preliminary findings:

- Physics Mod and later Modern UI compatibility: `docs/research/COMPATIBILITY_INVESTIGATIONS.md`;
- PT visuals, materials, denoising, Ponder, and special effects:
  `docs/research/PT_VISUAL_AND_PERFORMANCE_INVESTIGATIONS.md`;
- RenderPearl/Sodium dual-backend migration:
  `docs/research/RENDERPEARL_SODIUM_MIGRATION.md`;
- MCVR-native MFG/NR/GUI-FG and renderer findings: sibling MCVR `docs/research/`.

The proposed work packages and acceptance gates were added to `docs/ROADMAP.md`. Investigation
conclusions remain explicitly static or historical unless a future ledger entry records a current
build, runtime, GPU, or user-visual result.

## 2026-09-21: historical dirty-worktree checkpoint

Status: superseded as a repository-state claim; retained as a historical snapshot.
Evidence: mixed; each independent event below records its own boundary.

> Historical snapshot. Its use of "current" and its uncommitted-worktree instructions are
> superseded by the post-amend correction at the end of this ledger.

This checkpoint covers two independent Git repositories:

Machine-local, non-portable paths at the time of the checkpoint:

- Radiance: `D:\Workspaces\Repositories\GitHub\RecRivenVI\Radiance`
- MCVR: `D:\Workspaces\Repositories\GitHub\RecRivenVI\MCVR`

At that checkpoint, both worktrees contained extensive uncommitted product work. They had to be
reviewed and committed as separate repositories. This document records behavioral work packages;
it is not a substitute for the final Git diff.

## 2026-09-21: external diagnostics and rendering ledger

Status: implemented; runtime-observed for the named logged calls; coverage remains incomplete.
Evidence: static and runtime; no pixel-equivalence or exhaustive-coverage claim.

Radiance exposes an audit bridge while the independent diagnostic mod lives under
`dev/radiance-audit/`. The diagnostic mod records screen-effect producers, draw routes, chunk
build counts, missing sections, and native submissions without making the production mod own the
high-volume logging policy.

Relevant product areas:

- `src/main/java/com/radiance/api/audit/`
- `src/main/java/com/radiance/client/render/RenderCaptureContract.java`
- `src/main/java/com/radiance/client/render/ScreenEffectCoordinator.java`
- `dev/radiance-audit/`

Evidence boundary: the ledger proves observed calls and recorded destinations. It does not prove
pixel equivalence, GPU execution, or that an unvisited game state is covered.

## 2026-09-21: chunk loading and native build scheduling

Status: implemented; investigating runtime correctness and performance.
Evidence: static and historical runtime observations; the fresh acceptance matrix remains pending.

The retained path uses section generation/epoch state, task deduplication and distance priority,
Java frame budgets, confirmed-empty-section handling, stale-result rejection, and fenced native
publication. Native code groups new work into a per-frame batch. Camera-dependent geometry
culling is intentionally excluded because Radiance needs all geometry inside the configured view
distance for ray tracing.

Relevant areas:

- `src/main/java/com/radiance/client/proxy/world/ChunkProxy.java`
- `src/main/java/com/radiance/mixins/vulkan_render_integration/ChunkBuilderBuiltChunkMixins.java`
- `src/main/java/com/radiance/mixins/vulkan_render_integration/BuiltChunkStorageMixins.java`
- MCVR `src/core/middleware/com_radiance_client_proxy_world_ChunkProxy.cpp`
- MCVR `src/core/render/chunks.cpp` and `chunks.hpp`

Do not reintroduce the removed independent high-throughput empty-section queue, mixed immediate and
fenced publication, null `RenderRegionCache` interpreted as an empty section, dirty sections that
bypass column-state validation, unbounded concurrency, per-frame blocking waits, or continuous GPU
timestamp queries. Those approaches either caused holes/crashes or added pressure without proving
useful work.

Runtime evidence to date is mixed across intermediate builds. The retained implementation still
requires a fresh matrix covering initial world entry, 32-chunk distance, F3+A, nearby edits,
teleport, and dimension changes.

## 2026-09-21: camera screen effects in the HDR world composite

Status: implemented; build-verified; automated-verified; runtime-observed; visually-accepted;
deployed for the named vanilla/Veil cases. Broader third-party coverage remains pending.
Evidence: static, build, automated, runtime, visual, and deployment, bounded below.

Vanilla block-inside-camera, water/custom-fluid, and fire effects are recorded into the HDR frame
before tone mapping. The captured contracts preserve vanilla geometry:

- block sprite: one atlas sprite stretched to the viewport with vanilla UV orientation;
- vanilla/custom fluid: 4 by 4 repeating texture, eye-position brightness, source-over alpha;
- fire: two projected panels using vanilla translations, rotations, sprite shrink, and alpha.

Veil 4.3.2 has a separate contract for its optimized block and water screen effects. Radiance no
longer selects that contract by checking whether Veil is installed or whether a shader exists.
Veil first executes its real `veil:core/blit_screen_effect` path, writes `TexOffset` and
`ColorModulator`, binds the actual texture, and calls `VeilRenderSystem.drawScreenQuad()`. The Veil
adapter intercepts that draw call and translates those exact values into the HDR block or fluid
contract. If Veil does not consume the helper, execution reaches the vanilla body and Radiance
captures the vanilla values there.

Relevant Radiance areas:

- `src/main/java/com/radiance/client/render/HdrCameraEffectRenderer.java`
- `src/main/java/com/radiance/mixins/vulkan_render_integration/ScreenEffectRendererMixins.java`
- `src/main/java/com/radiance/compatibility/veil/VeilScreenEffectAdapter.java`
- `src/main/java/com/radiance/compatibility/veil/VeilRuntimeAdapter.java`

Relevant MCVR areas:

- `src/common/shared.hpp`
- `src/core/middleware/com_radiance_client_proxy_vulkan_RendererProxy.cpp`
- `src/shader/world/tone_mapping/tone_mapping.frag`

Static and automated evidence:

- the Veil 4.3.2 bytecode changes `renderTex` and `renderWater`, but not fire or public third-party
  `renderFluid(texture)` calls;
- Java tests exercise actual-uniform snapshot preservation and enforce the no-installed-mod-check
  boundary for Veil behavior;
- JNI generation, native compilation, shader compilation, and MCVR CTest 26/26 passed;
- the final call-driven Java refinement passed the complete Java test suite and distributed-JAR
  verification.

Post-deployment review found that the first call-driven revision still read `TexOffset` and
`ColorModulator` through Veil's OpenGL-querying `ShaderUniform.getFloats()`. Radiance suppresses
that OpenGL upload and keeps the authoritative values in Veil's CPU-side uniform buffer, so the
query could return zero or invalid UV, brightness, and alpha values even though the correct
texture ID reached MCVR. The adapter now reads `VeilShaderUniformData` directly, and the shared
CPU-uniform view preserves the native byte order when duplicated. Regression tests cover the
actual vec4 values and reject incomplete CPU snapshots. The complete Java suite and distributed
JAR verification passed after this correction. The user then exercised several affected screen
effects in game and reported that their texture placement and appearance were relatively consistent
with expectations. This is visual acceptance of the tested cases, not proof of exact reference
pixel equivalence or arbitrary third-party-fluid coverage.

Deployment: the final JAR was copied to the `Radiance Test` Prism instance after its previous game
process exited. Source and deployed SHA-256 both equal
`CBBAFD771B24F42EB28668F7D4C71EC9C391295223B2DF2E35C69A9955E3B74D`. This proves artifact identity,
not launch or visual acceptance.

## 2026-09-21: consolidated Veil compatibility boundary

Status: implemented; other Veil adapters remain under investigation.
Evidence: static source inspection; screen-effect behavior has the stronger evidence recorded above.

Veil mixins delegate into `src/main/java/com/radiance/compatibility/veil/`. A class/resource
presence test is allowed only to decide whether an optional mixin can load safely. Product
behavior must follow a real Veil method call, bound object, framebuffer operation, shader
compilation request, or draw submission. Capability queries may report backend support, but must
not be treated as proof that the corresponding Veil feature was invoked.

The screen-effect adapter is the first path explicitly corrected to this rule. Other Veil
adapters still need an audit against the same standard; see the roadmap.

## 2026-09-21: Ponder path tracing and RR model diagnosis

Status: implemented; runtime-observed; visually-accepted for model D in the named Ponder scenes;
deferred correctness and performance work remains.
Evidence: static, runtime, visual, and historical captures; no universal model-quality claim.

Ponder uses an independent world pipeline, world/entities/buffers/history, scoped renderer routing,
overlay recording, hardware BLAS/TLAS, orthographic primary rays, and final GUI composition. The
historical CPU-BVH 640-width preview is no longer the active output path.

The user confirmed that DLSS-RR model D produces acceptable Ponder output while model E at
Quality/Balanced produced severe quality loss. Captures placed the artifact in RR output rather
than only in the GUI composite. This does not prove model E is universally defective or that DLAA
previously selected D internally.

Relevant areas include `compatibility/ponder/PonderPathTracer.java` and MCVR
`src/core/middleware/ponder_path_tracer.cpp`, `scene_scope.hpp`, and `scene_camera.hpp`. Deferred
items are recorded in the roadmap rather than agent memory.

## 2026-09-21: workspace and test-instance cleanup

Status: archived; the recorded cleanup operation was performed through the Windows Recycle Bin.
Evidence: static filesystem inventory, process inspection, and artifact hashes.

The cleanup covered this Radiance checkout, the sibling MCVR checkout, and the `Radiance Test`
Prism instance. It moved 250 reproducible or historical items (about 19.71 GiB): Gradle/CMake
build outputs, the MCVR install tree, old extracted Radiance runtime/bootstrap generations,
diagnostic tool runtime caches, historical logs and crash dumps, instance caches, and raw Ponder
frame buffers.

The following evidence and user data were deliberately retained:

- both Git databases, source trees, dirty worktree changes, and repository documentation;
- the deployed Radiance and RadianceAudit mods, instance configuration, saves, screenshots, and
  resource packs;
- the extracted runtime generation retained at cleanup, `a290c528...c9caa3c`, and bootstrap generation
  `ade81a22...1d1c9d5`;
- the newest instance audit ledger (`20260921-012756`);
- both Ponder capture `analysis.json`, pipeline snapshots, and request metadata, while their 16
  large `frame-*` directories were recycled;
- compact C2ME compatibility version manifests, while downloaded/staged JAR copies were recycled.

After cleanup, the deployed Radiance JAR still had SHA-256
`111A808ACF260785A9EA6B69CAD18847A3590573CCCC372D927270955093B3F3`. No active test-instance Java
process existed during cleanup. Moving data to the Recycle Bin does not reclaim disk space until
the Recycle Bin is emptied.

## 2026-09-21: external Port evidence migration and cleanup

Status: archived; migrated files were hash-verified and discarded payloads were sent to the
Windows Recycle Bin.
Evidence: static filesystem manifests, byte counts, hashes, and Git bundle verification.

The machine-local, non-portable `D:\Workspaces\Artifacts` and `D:\Workspaces\Scratches` Port
material had grown into
mixed report/build/runtime trees. Before cleanup, the useful human-authored records were migrated
to [`history/port-artifacts-20260921/`](history/port-artifacts-20260921/). Its manifest contains 194
source records (3.20 MiB) with source path, archive path, byte count, and SHA-256; verification after
the source cleanup reported no missing files or hash failures.

Repository-history recovery material from the 2026-09-19 develop-only cleanup and the 2026-09-20
pre-amend snapshots was copied to the machine-local, non-portable path
`D:\Workspaces\Backups\RadiancePortCore_20260921\RepositoryHistory`. The retained copy contains 57
files (27.83 MiB); all four Git bundles pass `git bundle verify`.

Forty-two obsolete external targets (28.60 GiB) were then recycled. The removed payload consisted
primarily of native minidumps, duplicate JAR/DLL/PDB files, build trees, copied source/worktrees,
test instances, SDK downloads, raw captures, and historical logs. The older dedicated history,
template, and target-retirement backups in `D:\Workspaces\Backups` were deliberately retained as
recovery assets. Unrelated GLASS and Vulkanite artifacts were outside the cleanup scope.

Follow-up: the user classified the early Stonecutter migration and all older material as safely
discardable. Inspection confirmed that `Radiance_History_20260910-215704`,
`Radiance_Template_20260910-210902`, and `Radiance_TargetRetirement_20260910-234954` contained only
the 2026-09-10 history-integration rehearsal, template snapshot, and retired multi-target backup.
They contained 23,405 files (4.326 GiB), dominated by duplicate Git packs/bundles, native binaries,
libraries, JARs, and source snapshots. All three directories were sent to the Recycle Bin. The
newer verified recovery set at `D:\Workspaces\Backups\RadiancePortCore_20260921` remains retained.

## 2026-09-21: dirty-worktree organization

Status: superseded by the post-amend repository-state correction below.
Evidence: static Git status and source-reference inspection at the historical worktree snapshot.

The repository-local diagnostic mod and compatibility laboratories remain under `dev/`, but the
directory is now explicitly ignored because they are local tooling rather than Radiance product
source. The runtime `logs/` directory is also ignored, including compressed rotations; the existing
tracked `logs/debug-1.log.gz` remains an intentional deletion. Product changes, tests, and project
documentation remain visible to Git. The removed `SableShaderBridge` and
`SimulatedShaderConsumers` types have no remaining source references and are superseded by the
consolidated Veil adapter paths.

At that checkpoint, the paired MCVR worktree remained separate and unstaged. Its shader sources had
explicit LF rules, and the two modified ray-generation shaders were normalized from mixed CRLF/LF
to LF without
changing their shader logic.

## 2026-09-21: Windows native-build generator guard

Status: implemented; build-verified for configuration and generator selection.
Evidence: static and build; no product runtime or visual claim.

`configureRuntimeDependencies` now makes the Windows generator deterministic. It reuses a Visual
Studio generator recorded in an existing MCVR `CMakeCache.txt`, otherwise selects CMake's default
installed Visual Studio generator, and always passes the selected `-G` together with `-A x64`.
`-Pmcvr.cmakeGenerator=...` remains available as an explicit override. A cached or requested
non-Visual-Studio generator is rejected before CMake starts the expensive dependency build.

Validation used Java 21 and reran `configureRuntimeDependencies` against the canonical sibling MCVR
build directory. It selected `Visual Studio 18 2026 (x64)` and completed CMake configure/generate
successfully. The paired MCVR guard was also tested with a fresh Ninja directory; it rejected the
unsupported Windows FidelityFX-plus-NRD combination in 121 ms with the replacement command in the
error message. A regression run with the external `CMAKE_GENERATOR=Ninja` environment variable set
still selected the cached `Visual Studio 18 2026 (x64)` generator and completed successfully.

## 2026-09-21: post-amend repository-state correction

Status: implemented; static Git/remote verification. No new product runtime or visual claim.
Evidence: static Git status, local/remote ref comparison, and commit inspection.

Applies to the implementation baseline immediately before the current documentation-maintenance
batch:

- Radiance `f9dd73bb0ab3463d952e0c720db06ac4010f36ce` (`Initial port`);
- MCVR `5150670796380bf128fecec551c864f480bb05f9` (`Initial port`).

At verification time, each local `develop` matched `origin/develop`, and product source was clean.
The large uncommitted implementation described by the historical dirty-worktree checkpoints had
been reviewed, consolidated into the single repository commit, and pushed. This correction
supersedes only those checkpoints' claim that the implementation was still uncommitted; their
behavior, evidence, deployment, and limitation records remain historical evidence.

The documentation-policy, documentation-audit, and task-extraction files created after this
verification remain intentionally uncommitted for batch review. A later commit record must identify
their final commit rather than treating the hashes above as permanent "current" revisions.

## 2026-09-21: five-task historical extraction

Status: implemented documentation; archived.
Evidence: static, including complete Codex task pagination and current-source cross-checks; no new
build, runtime, GPU, deployment, or visual claim.

Five completed tasks were read in full: 47 cursor pages and 441 recorded turns across
`01a0a3f6-dc2a-7e41-af74-d1ddde4adcd4`,
`01a0a26e-a9c8-7ac0-87a9-862b84dd0bd6`,
`01a08c26-4fb4-76a3-a968-75c8680d4fe6`,
`019ff1cf-fec9-7e90-9a71-e41d5cf31d51`, and
`019fde4f-872e-7200-aad3-31b00811f843`. Their titles understated substantial scope drift, so each
now has a task-level extraction in [`history/threads/`](history/threads/) instead of being reduced
to its title.

The shared crash evidence is consolidated in
[`research/CRASH_AND_RUNTIME_INVESTIGATION_HISTORY.md`](research/CRASH_AND_RUNTIME_INVESTIGATION_HISTORY.md).
It distinguishes Java/Mixin failures, NVIDIA-driver CPU access violations, Vulkan device loss,
native application failures, and launcher/audio failures. It also corrects two easy-to-revive old
claims: whole-descriptor-table E-02 was superseded by the smaller pipeline-layout ownership token,
and the current descriptor binding retains the current resource for a slot rather than every
historical binding generation.

All old branch names, deployment hashes, dependency versions, audit counts, and test totals remain
dated snapshots. Current implementation claims still require the two post-amend baselines named in
the preceding entry and fresh verification where the fact can drift. This extraction intentionally
does not modify product source or upstream root documentation.

After link, formatting, task-state, and worktree-boundary checks, all five tasks were archived in
the Codex app on 2026-09-21. Archival is reversible and is not engineering acceptance.

## 2026-09-22: isolated client lifecycle acceptance preparation

Status: artifact prepared; automated preflight passed; G0/G1/G2 client acceptance pending; G3
deferred unless a safe real-GPU protocol is established.
Evidence: static, Java tests, native RelWithDebInfo build, CTest including build-host Vulkan tests,
distribution verification, and artifact hashes. No user gameplay result is claimed here.

The paired source checkpoint is based on Radiance
`c7ea3e6dc7fea62468b681481a5c7a9f7cdbefb9` and MCVR
`dadb7ecb9fcc3b333d48bb0f76b906c979e63ae3`, with the uncommitted A/B/C/D fixes and the minimal
acceptance hook included. The hook is disabled unless `RADIANCE_LIFECYCLE_CASE` names a supported
case. G1/G2 additionally require the isolated game directory's one-shot
`lifecycle-acceptance.trigger` file. It records through Java file I/O, independently of the Vulkan
renderer, and exposes only a controlled runtime fatal; it does not simulate device loss.

The native probe counts submit attempts and successful submits at and after injection, records the
first fatal and device-lost state, verifies that an ordinary JNI body is rejected after fatal, and
counts repeated close calls. A real-JVM startup probe round-trips a Unicode UTF-16 string and a null
string through the owned JNI conversion path. Thread-launch failure, worker failure, diagnostic
allocation failure, concurrent failure publication, and double-close ownership remain deterministic
native tests rather than destructive client injections.

The preparation build used the existing Visual Studio RelWithDebInfo configuration. Radiance and
JNI-header generation completed before native compilation. An attempted parallel native rebuild
then exposed MSVC C1041 contention on the shared compiler PDB; the accepted build used one build
job, retained `core.pdb`, and passed all 30 CTest entries. The full Java and bootstrap test suites,
runtime-resource check, distributable-JAR check, and isolated base-instance preparation also passed.
Final JAR, DLL, source-checkpoint, runtime-extraction and loaded-path identities are recorded in the
machine-local acceptance evidence; extracted/loaded identity remains pending until G0 starts.

The isolated instance is under the ignored repository `run/lifecycle-acceptance-20260922/base`
directory and contains only the complete distributable Radiance JAR. It does not reuse Prism,
production settings, or an existing world. G0 must create a new world, make an identifiable block
and container change, exit normally, and re-enter before any fatal injection is enabled.

### G0 normal-lifecycle result

Status: passed in the isolated packaged client; G1/G2 pending; G3 deferred.
Evidence: user gameplay observation, client/integrated-server log, process exit, and lifecycle
diagnostic. This is runtime acceptance of G0 only, not visual acceptance of the renderer generally.

The fixed package entered the new isolated `New World` twice. The user confirmed that the test
block and container changes remained after a normal save/exit and re-entry, with no observed crash,
hang, compatibility dialog, or rendering problem. Both exits logged the integrated server stopping
and saving players and all dimensions; the final Gradle/client process exited with code 0.

The renderer-independent lifecycle record ended with 60,485 submit attempts and 60,485 successful
submits, zero injections, no fatal state, no device-lost state, and one close call. The loaded native
DLL came from the isolated instance's runtime extraction and matched the packaged DLL SHA-256
`06707FC39F0679B78C066A89AB89611AB2AA7B34D029D1A9ABE6255E575AB8C9`. No crash report or
`hs_err` file was produced. Machine evidence is retained under
`run/lifecycle-acceptance-20260922/evidence`; it is ignored test output, not repository source.

### G1 controlled-runtime-fatal result

Status: passed after one directly attributable lifecycle repair; G2 pending; G3 deferred.
Evidence: two controlled-fatal client processes, a normal re-entry after each, user persistence
checks, lifecycle diagnostics, crash reports, integrated-server logs, and targeted/full Java tests.

The first G1 run propagated the original `VkResult=-13` failure to Java, rejected the post-fatal
ordinary JNI probe before its body ran, recorded no submit attempt or successful submit after the
injection boundary, saved the player and every dimension, and preserved the user's identifiable
world mutation after restart. It also exposed a real shutdown gap: Minecraft's fatal path calls
its static `crash`/process-exit boundary after emergency save and never calls `Minecraft.close()`,
so the existing normal-close tail injection did not release the native renderer.

The repair closes an initialized renderer at Minecraft's common fatal-exit boundary, after the
emergency world save and before process termination. A close failure is logged without replacing
the original crash. Native-independent tests cover pre-initialization, successful close, and close
failure. The full `test` and `bootstrapTest` suites and distribution checks passed before retest.

The repaired G1 run again preserved the original first failure and stopped submission at the exact
injection counts (1,587 attempts and successes, zero later attempts/successes). The integrated
server saved all dimensions, native close ran exactly once, Streamline/NGX shutdown completed, and
the user confirmed the new mutation after a further clean restart. The repaired distributable JAR
has SHA-256 `B8B2287C16509A71A0991BD94C421B38E874ED7898FA5628B109DDDD48389F86`; its native
runtime remains SHA-256 `06707FC39F0679B78C066A89AB89611AB2AA7B34D029D1A9ABE6255E575AB8C9`.
This closes G1 only; it is not real device-loss or general renderer acceptance.

### G2 post-fatal boundary result

Status: passed in a separate isolated client process; G3 deferred.
Evidence: renderer-independent lifecycle report, client log, crash report, and the deterministic
native tests from the fixed artifact.

G2 triggered at the title screen so it did not claim another world-save result. Java received the
same controlled first failure, the ordinary post-fatal JNI probe was rejected before its body ran,
and diagnostic reads remained available. Native state stayed unchanged across two close calls:
first result `-13`, operation `lifecycle-acceptance/G1-runtime-fatal`, `fatal=true`,
`deviceLost=false`, and zero post-injection submit attempts/successes. Close counts advanced from
zero to one and then two; the first call released the live renderer and the second completed as an
idempotent no-op. The later common crash hook correctly observed an already-closed renderer and did
not add a third native close.

Together with the earlier deterministic tests, G2 closes the post-fatal diagnostic, rejection,
worker propagation, JNI string, and repeated-close acceptance requested for this batch. It does not
simulate an asynchronous Streamline callback or a real device loss.

### Final lifecycle candidate and evidence index

Status: candidate complete for local history folding; G3 and the independent roadmap items remain
open. The authoritative machine evidence is retained under the ignored
`run/lifecycle-acceptance-20260922/evidence` directory.

The folded paired MCVR `Initial port` is
`9ebf73cc57290dbbdc9a6dbdd8f19f5989c45dbd`; this is the one-way companion reference and does
not require MCVR to record the eventual Radiance SHA.

G0 and the first G1 run used distributable JAR SHA-256
`F01F2BE306B41F88C5FA6209A3FCF65A60D85FD6E8F9A008DC68C8171B724675` with native DLL
SHA-256 `06707FC39F0679B78C066A89AB89611AB2AA7B34D029D1A9ABE6255E575AB8C9`.
The repaired G1 retest and G2 used JAR SHA-256
`B8B2287C16509A71A0991BD94C421B38E874ED7898FA5628B109DDDD48389F86` with the same native
DLL. The final source snapshot is `evidence/source-snapshot/post-g1-close-fix`; its normalized
Radiance product patch SHA-256 is
`82F07C7A6255454556530E7D446E31604F049E1F74CEB9BD54F48A3ED3B97F27`, and its paired MCVR
product patch SHA-256 is
`7253F8098368158A5A5F52083B8AC9781A19D7368C63A57871CD9ACE5E0AE1AA`. Current product
sources and all necessary untracked source/test files match that snapshot; documentation was
updated afterward and did not require a new client build.

This index does not merge results from different JARs: G0 and the first G1 preserve the original
artifact identity, while repaired G1 and G2 establish the final candidate's fatal-exit behavior.
G3 was not run. FG GUI composition, generic UI PT and Ponder memory work, external-section
backpressure, client resource-type isolation, and NVIDIA runtime redistribution remain separate
open work.

## 2026-09-22: external-section admission and client resource identity

Status: implemented; automated and distribution-verified; named isolated runtime checks passed,
with longer external-section churn and a separate intermittent device-loss event still open.
Evidence: current-source inspection, Minecraft 1.21.1/NeoForge compile, targeted tests, complete
Java/bootstrap tests, distributable checks and the isolated runs recorded below.

External section rebuilding previously bypassed the main chunk builder's admission order: it
created every `RenderChunkRegion` before the single important executor had capacity, submitted an
unbounded frame batch, and then cleared the request set. A null snapshot also invalidated existing
geometry even though the source data could merely be temporarily unavailable. The replacement
scheduler shares the important-build in-flight counter with primary work, limits external
preparation to eight requests per frame, acquires capacity before snapshot creation, and coalesces
each section to its latest request revision. Primary important work is considered first, so
external work cannot reserve the shared slot ahead of it. Capacity pressure defers work without
blocking the render thread or clearing existing geometry.

Preparation failure, executor rejection, worker failure and cancellation restore the permit and
running state before diagnostics run. Release removes the Java owner, cancels pending work and
discards raster state before releasing the encoded native handle. Each prepared task rechecks its
revision, Java owner and origin before Java, raster or native publication; MCVR independently
rechecks the handle generation before publishing native geometry. Static inspection covered Sable
update, dirty, missing-section release, F3+A-style rebuild and world-clear paths. An isolated
Sable/Aeronautics churn run is still required to establish visual convergence and steady queue,
CPU and VRAM behavior.

The resource wrapper previously ran in `FallbackResourceManager.createResource`, a static helper
without resource-pack type, and treated every `.png` path as a client texture. The first repair
intercepted only instance `getResource`; an isolated F3+T run then showed maximum parallax and
water-like auxiliary textures because block-atlas construction also consumes `listResources` and
`listResourceStacks`. The final repair wraps the returned resources from all three instance paths
and requires the manager's actual `PackType` to be `CLIENT_RESOURCES`; `.png` remains only the
texture-file filter. The selected pack, metadata supplier, vanilla debug wrapper and input-stream
ownership are preserved. The reload boundary continues to require identity with Minecraft's
actual client resource manager, so a single-player logical-server data reload cannot begin Vulkan
texture reload merely because it runs in a physical client.

Targeted scheduling tests cover saturated capacity before preparation, later progress without
starvation in the bounded fixture, preparation/submission/worker failures, cancellation and owner
replacement. Resource tests cover equal client/server `.png` identifiers, non-target resources,
identifier retention, bulk reads and exactly-once close after normal or exceptional reads. The
final `build --rerun-tasks` ran 152 GAME tests and seven bootstrap tests (two skipped) with zero
failures and passed
`verifyRuntimeResources`, `verifyDistributedJar` and `verifyMavenDevelopmentArtifact`. The first
targeted compile exposed and corrected a checked-exception declaration in the new task boundary;
the final targeted and complete reruns passed.

The full isolated mod set used Create 6.0.10, Aeronautics 1.3.2, Sable 2.0.5, Sable Companion
1.6.0, Flywheel 1.0.6, Ponder 1.0.82, Simulated 1.3.2 and Veil 4.3.2. With JAR SHA-256
`B84950383D218DD5A323CB879E025C92112326D61E4616D92EB2E07AEAF6561E`, the user generated and
separated Sable structures and ran F3+A without visible holes or failures. After the three-path
resource correction, the user reported normal no-PBR parallax appearance and normal blocks after
F3+T. A later process lost the Vulkan device before `/reload`; Windows recorded two `nvlddmkm`
Event 153 entries, while the Java boundary preserved `VK_ERROR_DEVICE_LOST` and the integrated
server saved every dimension. Because the crash report showed only the initial client reload, this
is not evidence against server-data isolation. A fresh process then completed `/reload`; logs show
only server recipe and advancement reload, no second client resource-manager, atlas or Vulkan
texture reload, followed by a normal save and Gradle exit code 0. Machine evidence is in ignored
`build/manual-acceptance/evidence/20260922-resource-isolation-*` directories. Longer repeated
external-section churn, world switching and bounded queue/CPU/VRAM behavior remain runtime gates.

## 2026-09-22: unified thread-closure candidate

Status: source integration and targeted automation complete; unified isolated client and visual
acceptance pending. No commit, amend, push, tag or public binary release was performed.

The existing external-section admission and `PackType.CLIENT_RESOURCES` fixes remain the integration
baseline. Ponder now calls the generic `UiPathTracingProxy`; Ponder supplies scene identity,
geometry, camera matrices and target texture, while MCVR owns the reusable execution service.
Transition views share one PT world/TLAS so their geometry can affect each other's light transport.
Their camera and temporal histories remain independent. Rendering uses a bounded,
aspect-preserving physical extent and full-size composition instead of unrestricted full-window
PT/history allocation.

The paired native change replaces binary FG UI difference detection with accumulated GUI alpha.
Normal UI preserves RGB blending while accumulating coverage; background-dependent blur, invert
and related full-screen effects report full coverage and remain in final color. This defines a
HUD-less/final/alpha contract, but translucent text, blur, invert and UI PT under real DLSS-G remain
visual acceptance items.

Additional confirmed repairs include bounded texture-name reuse against the 4096-entry descriptor
table, temporal guides for priority geometry, camera-relative Flywheel transforms at large
coordinates, bounded UI PT resource counters and default-off device-loss tracing. Static review
retained existing SR/RR reset/camera-motion logic and the fixed-version Veil failure boundary.
Final automated results, JAR/DLL hashes and the isolated instance are appended after the unified
build so evidence from older artifacts is not merged into this candidate.

The 2026-09-22 real device loss remains partial G3 evidence. It proves observed original-error
propagation and integrated-server save for that process; it does not prove GPU root cause or native
close completion. Public binary distribution is independently blocked: the installable JAR is
marked `local-validation`, and the dedicated public-distribution task fails closed pending the
conditions in `docs/THIRD_PARTY_RUNTIME_AUDIT.md`.

### Unified candidate build and startup preflight

The final paired candidate was rebuilt after two startup-only findings. Migrating the native UI PT
entry point left `PonderProxy` in the Java native-owner list and omitted `UiPathTracingProxy`; the
first preflight therefore failed before the menu, and the ensuing JVM teardown exposed an older
native access violation. The owner list now names the real JNI class and a regression test compares
the declared owners with native methods. JNI headers are generated by a standalone, non-incremental
JavaCompile task over the complete source set, so an incremental `compileJava` cannot silently leave
the native build with only a subset of headers. The failed run is retained under
`build/manual-acceptance/evidence/20260922-unified-thread-closure/startup-crash-pre-fix`.

The next preflight exposed high host-allocation churn after the initial atlas upload. MCVR now polls
the last submitted texture-upload batch even when no new upload is queued, caps retained staging
capacity, and sizes replacement buffers from bytes used by the completed batch rather than the
largest atlas buffer previously attached to it. After that repair, a 78-second menu run held the
reported host allocation near 517-524 MiB after startup and reached intervals with no allocation or
leak delta; animated textures caused only bounded intermittent sub-megabyte batches. This metric is
MCVR's tracked allocation set and does not include driver or SDK-private memory.

The final menu run also found that a DLSS/RR viewport configured but never evaluated was passed to
`slFreeResources`, which Streamline rejected. MCVR now frees only a feature that completed at least
one evaluation. The rebuilt client reached the menu, ran steadily and exited normally with Gradle
code 0; no invalid free, device loss, JVM crash or crash report appeared in that run.

Final automated evidence is 153 GAME tests and seven bootstrap tests (two skipped), all without
failure; MCVR RelWithDebInfo build and all 35 CTest cases passed, including four Vulkan GPU tests and
shader compilation. `verifyRuntimeResources`, `verifyDistributedJar` and
`verifyMavenDevelopmentArtifact` also passed. Artifact identities and the unified manual matrix are
in `build/manual-acceptance/evidence/20260922-unified-thread-closure`. These results establish build
and menu-level startup/exit only. World, Ponder, FG, resource-reload and long-churn observations for
this exact artifact remain manual gates.

## 2026-09-22: successor handoff correction before manual acceptance

Status: directed source/provenance check complete; unified gameplay handoff held. No product change,
rebuild, launch, redeployment, staging or commit occurred. Existing dirty source/tests were retained.

The package and extracted DLL match the prior manifest. The pre-correction tracked diffs also match;
the original untracked-source content fingerprint is incomplete, so new observed hashes are saved
in `build/manual-acceptance/evidence/20260922-unified-thread-closure/HANDOVER-20260922.json` without
claiming retrospective build proof. Existing Java/bootstrap/CTest results were inspected, not rerun.

The preceding unified entry overstated completion: UI PT still allocates a full `WorldPipeline`
per view and scales a full-window input; sequential scene capture does not establish current-frame
transition composition. FG has fractional alpha, but full-screen blur versus unblurred HUD-less and
nonstandard blend modes retain contract/visual questions. Immediate texture-name reuse has a stale
Java deferred-upload path without owner generation, beyond the native submitted-image retention.
Findings, exclusions and release gates are in the audit's
[successor correction](audits/2026-09-22-gpt6-pro-code-review-verification.md#2026-09-22-successor-handoff-directed-verification-correction).
The existing unified matrix remains the intended order after blockers are resolved. G0/G1/G2,
partial real-device-loss evidence and licensing retain their original, separate boundaries.

## 2026-09-22: texture ownership and actual shared UI PT acceptance candidate

Status: implemented; build-verified; automated-verified; bounded runtime-observed; visually pending.
Evidence: source, JVM/native tests, Vulkan tests, per-process client/capture records and deployment.
Applies to the retained dirty trees above Radiance `e1e91a2` and MCVR `9ebf73c`; no Git index change.
Supersedes: the immediately preceding handoff's three source-blocker conclusions, only to the
extent described in the [directed remediation audit](audits/2026-09-22-gpt6-pro-code-review-verification.md#2026-09-22-owner-tickets-shared-ui-pt-and-fg-input-correction).

### Final implementation and boundaries

Java texture work now captures allocation/image-generation tickets. Checking a ticket and using
its JNI resource occur under the allocation/release monitor. Deferred NativeImage/glyph upload,
cancel, delayed release, replacement, reload and close cannot mutate a new owner after ID reuse.
Native recorded RT draws retain immutable descriptor/image/sampler snapshots until fence retirement.

The Ponder UI wrapper collects all current visible scenes before one native batch executes. Actual
physical bounds drive crop projection and GUI placement. One expensive executor and common TLAS
serve two independent view histories; serial transient resources are shared, frame-ring outputs
remain independent, unchanged geometry/BLAS are reused, and departed scenes are pruned. This is an
actual pipeline/resource change, not a proxy rename. The two-view limit is explicit; lighting
interaction, framing and long churn still require visual/runtime acceptance.

FG coverage now begins at the real world/GUI boundary, retains fractional source-over alpha, mirrors
source-only vignette modulation onto HUD-less, and uses matching background blur. Inversion and
blur over already-composited varying UI retain the mathematical limits documented in the audit.
No global FG disable, screenshot replacement or user acceptance of those limits is implied.

### Verification, failures and deployment

This task ran 164 GAME tests (all pass), bootstrap 5 pass/2 skip, RelWithDebInfo build/shader install
and 41/41 CTest cases, including five real Vulkan GPU tests. JNI coverage checks include 189 exports
and 21 native-owner classes. CTest also contains supplemental source-contract checks; those do not
substitute for the behavior/GPU tests. Full build, runtime/distributed-JAR and developer-Maven gates
were run. A deployment invocation without the diagnostic init script correctly rejected the extra
audit JAR; the corrected invocation explicitly declares that isolated dependency.

The first four new client runs exposed important-upload initialization, reused consumed staging,
SBT slot-count and per-pass constant lifetime defects; fixes and failed evidence are retained.
Broad alpha in runs 5/6 led to the vignette correction. Run 8 found an Advanced shader include error,
fixed before run 9. Run 9 then suffered genuine device loss before Ponder: original error, all-world
save and native close are observed, GPU cause unknown. Run 10 saved/exited normally but its 4K SDK
budget warning was incorrectly treated as failed reconstruction. The final small native correction
accepts that warning only for the SDK's completed-evaluation path, leaving other errors rejected.
Neither that correction nor the successful normal runs closes G3/root cause.

The candidate is deployed only to `build/manual-acceptance/full`:

- JAR SHA-256: `FAC591721430B18171A1243BFD31B7F7332D79FC7D0E78C49C8F65A2CCEEBCE5`.
- core.dll SHA-256: `EF6EE40675B257DA7AED26FEF812A1D0D09C196AB85C468264017FAF2C4B07D1`.
- Matching PDB SHA-256: `584DA1BFD5725A56503F60401D9F94A14BC99FEEEA7E3BCE12EFC0747A0D93FF`.

The non-portable evidence root is
`D:\Workspaces\Repositories\GitHub\RecRivenVI\Radiance\build\manual-acceptance\evidence\20260922-ownership-ui-fg`.
`MANIFEST.txt` maps each failed/intermediate/final process to its own artifact, test results and
captures; `final-sources.json`/ZIP include tracked and necessary untracked sources, explicit newline
normalization rules, generated JNI/diagnostic inputs and fixed dependency fingerprints. This is new
provenance, not reconstructed proof for the earlier 594B2C... package. `ACCEPTANCE.md` and
`Start-ManualAcceptance.ps1` provide one continuous isolated matrix with the automatic driver off.

Remaining acceptance: actual generated-display/UI experience, transition mutual lighting, resource
pack and section churn, high-resolution budget behavior, and the unexplained Advanced loss. Earlier
G0/G1/G2 keep their original artifacts/scopes. Public binary distribution remains blocked by the
external conditions in `THIRD_PARTY_RUNTIME_AUDIT.md`, irrespective of local build/test success.

### Final-candidate automatic client result

Preflight 11, PID 84892, loaded the final DLL above and ran from world-ready 19:07:43 to the normal
stop request at 19:09:58 (135 seconds in-world; Gradle total 3m12s, exit 0). It exercised Ponder
open/pause/transition/close/reopen, 3840x2054 resize, client resource reload, server `/reload` and
chunk refresh. All dimensions saved; native close completed once with 3,983 successful submissions,
no fatal and no device loss. No SDK budget warning appeared in this run. Earlier failures remain.

Actual logs show one active heavy pipeline, at most three live pipelines briefly while obsolete
engines retire, then one again. Both current scene IDs appear in the same batch with one TLAS build.
At 3840x2054, a stable Ponder interval reported 6,701-6,702 MiB in VMA allocations and median real
frame rate 25.5; after close it reported 6,011 MiB, and reopening 6,683-6,706 MiB. These 5-10 second
samples exclude SDK/driver-private memory, do not measure generated-display FPS, and lack a matched
old-build baseline. They show retirement, not universal performance or unlimited stability.

Four FG input captures were produced. The initial world capture has 3,597,196 zero-alpha pixels
whose final/HUD-less RGB match exactly, and 41,855 fractional pixels. Ponder and pause captures
retain fractional coverage; foreground bounds pass the declared 2/255 quantization tolerance.
The last request was labelled crosshair by the driver but retains the pause-style full-screen alpha
distribution (focus-based pause was enabled); it is not independent proof of a post-pause crosshair
scene. The initial capture is the usable world-input check. The SDK reported interpolation enabled
for an early focused interval and disabled after focus loss. No user/RTSS display acceptance is
inferred from either inputs or SDK state. The final public-distribution gate failed for its intended
unapproved-license reason; local runtime/package gates passed.


## 2026-09-22: FG affine replay, weighted blur and Advanced pass parameters

Status: implemented; build-verified; automated-verified; runtime/visual acceptance pending final preflight.
Evidence: source, native GPU regressions, Java/bootstrap/package checks; client capture results below.
Applies to: retained dirty paired worktrees above Radiance e1e91a2 / MCVR 9ebf73c; no Git rewrite.
Supersedes: the previous statement that local inversion necessarily requires opaque UI coverage;
that argument assumed an unchanged HUD-less background. Historical runs and limitations remain.

### Source changes and direct evidence

- Actual Minecraft 1.21.1 / NeoForge 21.1.250 Gui.renderCrosshair uses
  ONE_MINUS_DST_COLOR/ONE_MINUS_SRC_COLOR. The source-generated affine transform
  T(D)=S+(1-2S)D can be applied to both final RGB and HUD-less while preserving UI alpha:
  U'=(1-2S)U+S*A. The ordinary RGB draw and order are unchanged. The same geometry/shader,
  viewport and scissor are replayed using a pre-draw depth/stencil copy so writes/tests do not
  discard the second draw or change main depth. Per-frame scratch is bounded; it costs an
  additional depth/stencil copy for each affine draw, not a GPU idle or opaque screen mask.
- GameRenderer renders the HUD before ClientHooks.drawScreen; Screen.renderBlurredBackground
  calls processBlurEffect. Thus blur after UI is reachable in ordinary pause screens, not just
  hypothetical third-party nesting. The isolated audit mod also calls the actual route after
  fractional text/panel and local inverse, before later UI, including over Ponder.
- For a linear blur L, HUD-less is now L((1-A)*H)/L(1-A), with zero fallback only where
  transmission is zero. A half-float intermediate preweights texels BEFORE linear sampling.
  Normal final-color blur still computes L(F), and its alpha is L(A); no ordering or RGB rewrite.
  Scratch/descriptors are per physical frame; pipeline resources are reused and die with the
  framework. GPU tests cover varying alpha, moving inputs, opaque UI PT-like regions and edges.
- First client candidate 4BB7D6DB... / 5D62DD89... exposed negative foreground residuals after
  blur. Investigation found CPU std::round versus GLSL round disagreement for half-integer
  radii (5*0.5). Host constants now preserve the fraction and the compute shader uses the same
  GPU rounding as the fragment reference. A test invokes the production packing helper at
  radii 2, 2.5 and 1.25. First failure/capture and later artifact remain separate.
- Directed Advanced audit found per-pass execution buffers repeatedly retargeting a shared
  UPDATE_AFTER_BIND descriptor before submission. RT and post-render stages now each keep one
  stable execution-buffer address; existing inline command updates and barriers carry pass
  values in queue order. A real compute test reads three distinct values through one descriptor
  and repeats a frame. This defect is confirmed; the natural device-loss cause is NOT proven.
- The existing default-off fixed 256-entry loss ring records numeric pass fingerprints/frame
  indices. These are CPU recording breadcrumbs, not GPU-completed checkpoint evidence.

### Evidence and remaining boundaries

Evidence root (non-portable): Radiance/build/manual-acceptance/evidence/20260922-fg-boundaries-advanced.
See ADVANCED-INVESTIGATION.md, inherited-evidence.json, source manifests, native-build*.log,
ctest-affected.log, ctest-final-affected.log, java-test-results.json, capture checks and final MANIFEST.
This run executed 19 selected Java tests, bootstrap 5 passed/2 skipped, and affected native tests
including actual FG/framebuffer/execution-buffer GPU cases. Old 164/41 counts are historical,
not claimed as a fresh whole-suite run. Native RelWithDebInfo + INSTALL and paired package gates
were executed. No cross-repository JNI signature changed.

The Advanced PID 61436 has first-error propagation, all-three-dimension saves and native-close
return evidence; this corrects any implication that G3 has no real evidence. The failed artifact
and configuration differ from the later 135-second successful Vanilla run. No complete exact
preflight9 source snapshot is fabricated. GPU faulting command/address, SDK-private pressure
and all asynchronous GPU retirement remain unproven. No Advanced stress repeat, TDR change or
unsafe injection is part of this acceptance.

The fixed Streamline 2.14.1 public contract does not expose a post-generated-frame GUI replay
callback in this integration. The implemented background transforms modify the tagged scene
inputs; real-frame algebra and captures do not prove that SDK interpolation keeps a moving
local inverse perfectly anchored, blur edges artifact-free, or every generated pixel equivalent
to re-executing the effect on a generated scene. Those dynamic checks remain user acceptance;
no background freeze, hard edge or experience downgrade is pre-approved. Public binary license
clearance and unrelated G3/root-cause boundaries remain open. All prior texture ownership,
shared UI PT, resource-type isolation and section admission work is retained.


### Additional capture correction and new Vanilla failure (same work session)

Candidate 84037426... / 3190953E... passed the weighted-blur inputs but its returned-world capture
had three hotbar pixels with invalid reconstructed foreground (minimum -0.0718). A direct contract
check found that blend-disabled GUI draws copied RGB without blending while leaving fractional
fragment alpha in the coverage channel. ShaderTranslator now wraps the actual fragment main and
uses a native push constant to force opaque coverage for default-target, blend-disabled draws.
Original RGB/discard/depth testing remain; blended/custom-framebuffer draws set the flag to zero.
The new ShaderCoverageGpuTest compiles BOTH real vanilla and external translations, executes the
native Vulkan harness and verifies discard/RGB/alpha over flag transitions 0 -> 1 -> 0. It passed.
This does not assert that every unsupported third-party fragment layout has an equivalent capture.

Candidate3 JAR 6FB7F409FDA2461057C00296F0CC3576E09AEC555B5B0BE328698912C9E9E1B1 /
DLL FFD37BD714920E619F9BF8FBD4C72180E82477775BB7716260C697976DF8EE53 passed the
88-second preflight4 (PID50564, 21:03:03..21:04:31, 2727 successful main submissions).
All six FG input captures passed foreground bounds at 2/255; zero-alpha world pixels matched
HUD-less RGB exactly. Ordinary pause, fractional UI -> inverse -> blur -> later UI, Ponder and
transition were reached. The SDK reported window-not-focused and disabled interpolation; these
are input-path/runtime observations, NOT generated-display acceptance. All dimensions saved and
native close completed once. 63 selected GAME tests passed; bootstrap5 passed/2 skipped, and five
native targeted tests passed before this run. Native GPU cases are not display-frame observations.

The same candidate3 in preflight5 with FG OFF then suffered natural DEVICE_LOST at21:06:02,
six seconds after world-ready, BEFORE any capture/Ponder/reload/resize. The first error was
vkQueueSubmit(chunk build)=-4. 123 Event153 entries span21:05:58.669..21:06:02.009, GPUID100.
All dimensions saved; original chunk-submit cause persisted across native close, completed21:06:03.
339 main-submit attempts/338 successes include an unwanted subsequent frame submission after fatal.
The process died before module sampling: its PID/live-module enumeration is unavailable; do not
reuse preflight4's loaded-module observation as if it belonged to this failed process. Deployed
candidate3 identity, source snapshot, launch log, crash report, new trace sections and Windows XML
are retained in the evidence root/preflight5. The old Advanced trace sections remain separately.

The confirmed post-fatal continuation is repaired by runCheckedStage at upload/world-module
boundaries and a fatal recheck before final/readback submissions. Its behavior regression uses a
callee that RECORDS an error and returns normally; subsequent callbacks are rejected and first
cause preserved, without a real GPU loss injection. This is a failure-boundary repair, not the GPU
root cause. The latter remains unknown; no extra speculative allocator/driver/shader setting fix
was applied. No further world run is included in this handoff's validated scope.

The final package below supersedes candidate3 after this guard. Its normal rendering shader changes
are the same, but candidate3's gameplay results are not relabelled as final-artifact runtime results.
Only separate final build/regression/menu preflight is claimed. Unified manual WORLD acceptance is
paused by the new natural Vanilla fault; the previous Advanced-only exclusion is insufficient.
The matrix remains available, with paused portions explicit. No FG visual downgrade is approved.

### Final artifact and bounded preflight

Status at handoff: implemented; automated-verified within the tests below; deployed;
runtime-observed for final menu/load/close only. Full world acceptance is blocked by the new
natural loss. This is not a conclusion that FG-off causes the loss or FG-on prevents it.

Final JAR: `432BCDAC53446B92247ABA39E9C977578ADF7547F266064601DD0B479BA02DD6`.
Final core.dll: `A2356F8A1517A051A1502F460E1AB31C01F68B9E1D257F00AD0378E58D51873F`.
Matching PDB: `5DE50AC3D02F6C45F63261423ECA63FB1EA9B0CDBDC0FF0F0BB0DE9809E03622`.
Non-portable deployment: `build/manual-acceptance/full` in this checkout. Build/install,
embedded DLL, deployed JAR, extracted DLL and live PID88792 module hashes agree; the full path,
15 runtime-DLL fingerprints, notices, mod/configuration identities are in `artifact-identity.json`.

`java-package3.log` and `java63-xml/` retain 63 selected GAME passes, including the real
Java-to-Vulkan translated-coverage test, texture tasks, UI viewport, resource type and external
admission regressions. Bootstrap: 5 passed, 2 skipped. The later native-only stage-guard changes
used `native-build8/9.log` (RelWithDebInfo INSTALL, shader build), `ctest-stage-guard.log` (7/7,
including three GPU cases), then `ctest-final9.log` (2/2 failure-state/JNI after the last direct
call-site guards). `package-final.log` reran the actual shader-translation GPU test (1/1);
`package-final9.log` passed final pairing, JAR/runtime checks, Maven development-artifact checks
and bootstrap 5/2. This is a sequence of affected gates, not a freshly executed full-suite count.

Final `preflight6-menu`: PID88792, title observed 21:21:12..21:21:37 local, normal stop,
19776 successful loading/menu submissions, fatal=false, deviceLost=false, native close once,
Gradle exit0. No world was entered; no save, Ponder, FG interpolation or visual claim applies
to this final process. Earlier world captures remain attributed to candidate3.

Complete source evidence: `final-sources.json` + `final-source.zip` in the evidence root above;
`MANIFEST.txt` records their hashes. The non-document product manifest is
`5BA1C61BB02F5CAD7A8FF10974575AF9C0CD25792EC155D76C40FB643B5B98EC`, equal to the
preflighted `stage-guard-sources.json`. Exact bytes, CRLF-to-LF normalization rules, tracked and
necessary untracked paths, deleted paths and ignored build inputs are explicit. Final documentation
is sealed afterward without rebuilding unchanged product code. Logs/worlds/binaries remain out
of the source candidate, and their unique evidence copies are retained.

`ACCEPTANCE.md` preserves the full continuous matrix but marks world steps on hold. The provided
menu-only launcher checks source and artifact identity and closes automatically after 25 seconds.
There is no recommendation to repeat Advanced or FG-off world-entry pressure. GPU cause, generated
frame visuals, full G3 and public redistribution clearance remain open. No staging, commit, amend,
push, tag, publication or evidence cleanup was performed.

## 2026-09-23 first GPU fault investigation and bounded world evidence

Status: investigating. First GPU causality and error/save/close handling remain separate.
The new local evidence root is `build/manual-acceptance/evidence/20260922-first-gpu-fault`;
its MANIFEST.txt, EXPERIMENTS.md, CASE-INDEX.json and ACCEPTANCE.md supersede the earlier
menu-only package identity without erasing any older result. The dated review's appended
investigation and the paired MCVR ledger describe source changes and direct call-site scope.

The inherited external admission, PackType isolation, texture ownership, shared UI PT,
FG composition and stage-fatal guards are preserved. This task adds no rendering feature.
Confirmed native repairs: supported privateData feature for Streamline's actual slot use;
missing COLOR_ATTACHMENT_OUTPUT producer in three LDR handoff/reuse barriers; checked
command-buffer begin. These are independently valid defects, not proven causes of loss.
Core/SyncVal observed the first two defects and their disappearance after repair; the
barrier has a production-helper real-GPU/readback regression. SDK-private SyncVal hazards
remain unclassified. No speculative global synchronization or feature disable was retained.

Evidence corrections matter: actual diagnostic extent was2560x1440, not planned720p;
one requested10s audit duration was absent from its separately built ignored JAR and the
observer stopped at52s. The audit mod was then explicitly rebuilt/deployed and its selected
duration verified. A legal independent fixture proved the installed VVL AS checker corrupts
its live-address registry and rewrites valid TLAS references after an unrelated AS destroy.
Its warnings and the E3 instrumented fault cannot prove app AS UAF. Source/control logs and
CPU ownership correlation remain archived. Selective app GPU-AV without that checker ran
52s clean at1-6fps, which is not normal timing. No old evidence has been relabeled.

E6 normal PID36856, diag4, no VVL/trace, Vanilla PT/RR Balanced/FG off/Reflex1/8chunks,
lost the device around5s after world entry before Ponder/reload/resize. Original first error
survived;352 submissions did not increase during close; all dimensions saved and native
close returned once. The first fault remains unresolved despite the source repairs.

The final local diagnostic hook loads no SDK itself. An ignored JVM agent explicitly
loads the installed signed Aftermath DLL before SERVICE device creation; bounded callbacks,
actual extension/feature enabling and a once-only maximum5s post-save/pre-native-release
status wait are implemented. Its initialization/unload and fake-clock timeout tests pass.
Agent flag and device-feature preflight omissions were detected, preserved and corrected.
Neither the external SDK nor the agent enters the mod package or clears any license gate.
No real fault occurred under the corrected capture, so actual dump production, fault-to-shader
mapping and that wait during genuine loss have no runtime pass yet.

Final investigation package (supersedes432BCDAC/A2356F8A):

- JAR `3B735ECF7C8DA943EF3A94235F4C6194AD0180DD759BF55F40A72371FDA2164C`.
- DLL `78AD12E7F816B94564924E20C65E57F3D42D14DCFE2A93233FF1EBA9A6B4B192`.
- PDB `46F68F42CC2490E765E40814B004539DA0F20C8E45DD6A42C162B1E85EB0C93C`.
- Diag6 product manifest `8815A38FE5D298056838278BAE81B526FD26FAE5F57FE7A1B970337010FB0F4F`;
  exact source ZIP `CD45EC30E04C04B955B6C11CF76632F7146AE74F30C4821EB667985909608EF7`.
  Tracked changes, necessary untracked inputs, deletions, normalization and ignored diagnostic
  build inputs are explicit; final documentation-only snapshot is separately indexed.

Native RelWithDebInfo INSTALL/shader build and final selected CTest5/5 passed. Matched
bootstrap, distributed-JAR, runtime-resource and Maven-development-artifact gates passed;
bootstrap retains5 executed passes/2 skips in its reports. No new whole-GAME153/full-native35
claim: those counts are historical. Java product source was unchanged in this investigation;
the only tracked Radiance build change lets isolated launchers explicitly turn the loss
tracker off. The ignored audit probe changes are separately built and hashed.

Configured resource-only Aftermath PID36448 ran00:02:40..00:03:00 and saved/closed811/811,
exit0. Normal PID60552, no Aftermath/VVL/host trace, ran00:04:53..00:05:13 and saved/closed
768/768,exit0. Build DLL, embedded JAR entry, extracted DLL and loaded path match separately
for both processes; driver/SDK identities are captured. Neither process window has nvlddmkm
System events. Shader ZIP container metadata differs across builds, but every inner file
matches. No Ponder, resize, reload, FG generation or visual acceptance is claimed here.

The final normal20s world run is a bounded success, not proof that the earlier intermittent
loss is fixed. Unified manual world/Advanced/FG acceptance stays paused. The next useful
capture and missing evidence are explicit in ACCEPTANCE.md; there is no automatic restart,
unsafe loss injection, GPU reset, TDR change or production/Prism access. Files and source
snapshots are evidence, not disposable backups. No staging, commit, amend, push or publication.

World-state caveat: successive runs reused the isolated save while normal server/Sable
simulation advanced. Matching settings do not mean byte-identical world states. A single
post-E11 stopped-world evidence ZIP is preserved for subsequent cloned-state controls;
it is not a reconstructed pre-fault snapshot. Natural structure evolution remains a
confounder, without an established Sable cause. See EXPERIMENTS.md before any further trial.


## 2026-09-23: Bounded local closeout and acceptance checkpoint

Status: implemented; build-verified; automated-verified; final world preflight failed;
manual/visual acceptance pending; first GPU cause investigating.
Evidence: static, build, automated, GPU fixtures, deployment and observed fatal save/close.
Applies to the accumulated worktree over `e1e91a2335a5939ef7bb5bf0955bff2c1d03f10f` with paired
MCVR commit recorded in the linked dated review closeout. No Java product change was needed in this pass.
Supersedes only the previous unconditional acceptance/amend hold, by explicit user decision.

The bounded candidate check retained all texture-task ownership, external scheduler, PackType
isolation, shared UI PT and FG composition work. All 37 Radiance candidate files, including 14
necessary new product/test/notice inputs, belong to the source checkpoint. The
[dated review closeout](audits/2026-09-22-gpt6-pro-code-review-verification.md#2026-09-23-bounded-local-closeout-and-acceptance-checkpoint) is the
canonical thirteen-item/expanded-scope status table, source findings and unresolved limits.

Direct native upload-retirement/submit and command begin/end/reset error continuations were repaired
and behavior-tested, without changing product rendering semantics. These are post-error safeguards,
not proof of the first GPU-loss cause. GAME 165 passed; bootstrap 5 passed/2 opt-in skips; final native
CTest 43/43 including seven GPU fixtures passed. RelWithDebInfo INSTALL/shaders, matched package,
runtime and Maven-development checks passed. Public binary gate rejected unapproved redistribution.

Final JAR `CBDEBEB8EB8011831F04C06920D54531D8092CFAC45E507903C9073DB94D5451`;
DLL `9DC8A04445541A5FB72E84F4D48E3ABFA95456DB122AED46C4DDABEC18B56C99`;
PDB `6FE66854C3EBB1C9DAE3B43321456F304A9399A91DF560BBB6A9B88093DE6916`.
Isolated deployment: `build/manual-acceptance/full`. Evidence: non-portable local
`build/manual-acceptance/evidence/20260923-local-closeout`, including full source/dependency/mod
manifests, exact source ZIP, test XML/logs, symbols and MANIFEST/ACCEPTANCE. Product ZIP
`5CD7294FBCB98142F77598AF1B065246ACF84298824948008F76F66E46C2CE88`; normalized product
`07744F338FF6289BA044224C761D0E3CB32F3B852DAC6FA19CDB7AB5E193CC6F`. Later record-only snapshot is indexed separately.

Final normal PID30596 lost the device about 12 seconds after world-ready (00:38:54..00:39:06 +0800).
No Ponder/reload/resize/FG/VVL/capture was active. Fence wait first detected -4; Event153 is correlated,
not causal proof. All dimensions saved, native close returned once, first error survived, and submit
counts stayed 242/242. Exit1; no automatic restart. This preflight failed; earlier diag6/G0/G1/G2
successes retain their original artifact identities and do not establish this package's success.

Manual normal continuation is allowed under the user's accepted unknown-root boundary, but the
failed case is paused and no pressure replay is requested. Launcher and continuous cases are
prepared; current visual, Ponder/FG, reload/chunk churn and ordinary save/reopen results remain
pending. Full G3, first-cause diagnosis, long stability and public vendor authorization remain open.
Local source checkpointing is authorized independently; no push or binary publication is included.

## 2026-09-23: Frozen-package manual feedback and source synchronization scope

Status: runtime-observed; visually-accepted for the user's limited simple-play observation only.
Evidence: user feedback, loaded module hashes, client/server lifecycle logs and process results.
Applies to frozen product checkpoints Radiance `c197ea228ec66f6dce14e7b86fd60e6e1d5ec8d4` and
MCVR `2f62a34e768a07c6dd4fd9e024f4bd7a2148ca35`; later documentation-only amend preserves their
product trees and the preceding entry's artifact hashes. This updates the pending-observation
state, not the historical failed preflight or any earlier package's results.

The user reported no obvious problem during simple play and chose to end this manual round.
Latest manual PID73024 ran01:24:03-01:27:04 +0800 on2026-09-23. Loaded core.dll and the unchanged
installed JAR match the preceding final-package hashes. New World was entered at01:25:33 and saved
all dimensions at01:25:43.194; the different world Test was entered at01:26:25 and saved all
dimensions at01:27:02.178. Approximate in-world intervals were10s and36s. Native close completed
once at01:27:03.450, with15175/15175 submissions unchanged across close and no native fatal/loss;
process exit0 followed at01:27:04.464. Same-world reopen and recognizable edit persistence remain
unverified. The complete process duration is not world soak time.

Settings: Vanilla PT, DLSS RR Balanced/model6, Reflex1, FGoff, jitter/SHARC on, view8/simulation32,
GUI scale4, no resource packs. Window output changed2560x1440 ->3840x2054; later RR input was
2227x1191. This observed change is not a controlled resize stress pass. Logged UI PT recreation
counters were zero; no Ponder result or generated-frame FG result is inferred. Host trace and
Aftermath were off; the bounded System/nvlddmkm query found no events in this manual process window.

Keep all three manual processes separate: PID36632 was a menu-only exit0; PID92452 exited-1 during
initialization; PID73024 supplied the successful world observations. PID92452's crash shows an
early framebuffer resize -> RenderTarget.destroyBuffers -> GlStateManagerMixins.releaseVulkanTexture
-> MissingTextureAtlasSprite.getTexture path accessing a null Minecraft TextureManager. Native
submissions were0, fatal/deviceLost=false and native close completed once. This is a recorded,
unrepaired startup defect, not another proven GPU loss or a known user-triggered resize sequence.
Automatic PID30596's earlier real device loss on the same package remains a failed preflight.

The non-portable local evidence root remains
`build/manual-acceptance/evidence/20260923-local-closeout`; see `ACCEPTANCE.md` and
`manual-20260923-012242-547`, `manual-20260923-012334-936`, `manual-20260923-012403-334` for separate
start/configuration/module/result/lifecycle/crash records. The manual launcher was corrected in
ignored evidence to launch frozen Java inputs directly instead of invoking rebuilding Gradle
prerequisites; its check-only JVM dry-run did not start Minecraft. Product and runtime identities
were unchanged. The evidence files remain outside the source commit.

No new build, test or game run was needed for these documentation-only changes. New feedback does
not pass current-package F3+T, /reload, chunk/Sable churn, Ponder, FG, save/reopen, full G3 or sustained
resource/performance testing. First GPU cause and public NVIDIA/vendor permission remain open.
By user decision, source-only synchronization is now authorized without reopening investigation;
no further manual run is scheduled. No tag, Release or public binary distribution is authorized.

## 2026-09-23: Per-draw face rules and interaction-aware chunk scheduling

Status: implemented; automated-verified for the named fixtures; runtime and performance comparison pending.
Evidence: static, build, automated, GPU. Applies to the uncommitted paired worktree based on
Radiance `e4c5bc73c0270e8079ccc30a8ec1fc02dea75fb0` and
MCVR `8208f305a71d0ffa56e761cd7b62c1b667572cb4`.
Supersedes: the deferred status of the scoped chunk scheduling work only; existing GPU failure and
manual-package evidence retain their original identities and limits.

### Requested behavior and source findings

Face rejection belongs to the source draw's requested cull enable, face mode and winding. It is
neither a texture/alpha classification nor camera-space removal. Minecraft 1.21.1 / NeoForge
21.1.250 sources show that the enabled CullStateShard itself is a no-op: inherited requested state
matters. Fixed Flywheel 1.0.6 MaterialRenderState changes cull enable only; mode/winding are inherited.
The port previously reduced these distinctions to a BACK bit in some producers and omitted them
in other producers. Opaque geometry in a mixed-face BLAS bypassed the necessary any-hit decision.

The primary rebuild path routed interaction through column scans, used horizontal distance, shared
near-initial-load priority with player updates, and retried capacity-blocked heads in the same
round. Native priority was entangled with publication mode and partial batches waited by frame
count. Those are distinct from server/network latency and temporal image history.

### Implementation

- MaterialFaces captures actual RenderType callbacks and requested state without GL queries or a
  context. Direct GAME GL11/GL11C face calls have a SERVICE transformer; existing Mojang cached-state
  Mixin targets are excluded from that transform. RenderSystem entry points and particle begin/end
  capture retain their own state semantics. Chunk workers receive render-thread snapshots; entity,
  world-mesh/Sable, Flywheel and UI PT producers carry per-geometry flags. Full callback capture
  restores cached state, face state, textures/shader, texture/projection matrices, FBO and viewport.
- Shared Java/native/GLSL bits encode BACK, FRONT and CW separately. CW uses bit22: an intermediate
  bit15 assignment collided with Flywheel ALWAYS depth mode and was corrected before delivery.
  The intermediate `A.jar` is not the corrected A comparison artifact.
- Uniform single-face models retain hardware culling/opaque fast paths; mixed one/two-sided models
  run any-hit only where needed. Instance determinant and requested winding are applied once.
  Primary, reflection, indirect, shadow and special hit groups share the face rule in both bundled
  PT pipelines. Geometry stays in the scene; first-person and priority visibility remain separate.
- Real player action/dependency invalidations can promote an already queued section and enter a
  reserved interaction worker lane. Three-dimensional distance orders peers. Background and external
  work use bounded FIFO worker admission; an external lane of one no longer occupies the reserved
  player capacity. Capacity-blocked requests remain queued after the round instead of monopolizing it.
  Source-unavailable preparation retains valid geometry; only confirmed empty data removes it.
- JNI carries priority independently of immediate/ordered publication. Native batches can dispatch
  interaction without waiting to fill; background waits use 25 ms of monotonic time. After 250 ms, background work receives every
  fourth admitted slot; three remain available to interaction. The ordinal persists across batches,
  including byte-limited/single-section batches. A first candidate gave all aged work precedence and
  could bury new interaction behind an old backlog; the production selector and regression were
  corrected before client comparison. These are admission policies, not a promise that an
  overloaded server/GPU or a non-rendering client can meet an end-to-end deadline.
- Native top-K selection no longer copies/sorts the complete queue. Preparation has 2 ms/8 MiB per-poll
  budgets plus 32 MiB in-flight build input accounting and the existing fence-count limit. A single
  oversized section may progress alone. These counters do not bound SDK allocations or constitute
  total VRAM measurement. Publication still requires the original GPU completion/version guards.
- Default-off bounded native tracing links mesh revisions, build submission/completion, publication,
  TLAS recording and completed frame fences. Java/native clocks use bracketed calibration. A fence
  observed on CPU is an upper bound on GPU completion, not a GPU timestamp or displayed-pixel proof.
  External audit sources remain in ignored `dev/radiance-audit`; no automatic world probe runs in
  ordinary product use.

### Validation and evidence boundaries

Non-portable evidence root: `build/manual-acceptance/evidence/20260923-faces-chunks`.
`baseline-source.zip/json` preserves the pre-change source; `A-source.zip/json` preserves the first
A candidate, including the subsequently corrected bit assignment. Private comparison copies are
under `D:/Workspaces/Scratches/RadianceFacesChunks/{baseline-trace,A-trace}` and add common observation
hooks only to the selected reference behavior. Active checkouts are not reverted. `baseline-trace-source-final.zip/json`,
`A-trace-source-final.zip/json`, `B-final-source.zip/json`, `native-dependency-inputs.json` and
`audit-source-final.zip/json` identify the selected inputs. `deployment.json` owns exact package, runtime,
shader, PDB, Java dependency, mod and isolated-world identities.

Native `ctest-final.log`: 48/48 tests passed, including a real 40-ray Vulkan fixture for mixed opaque/
double-sided geometry, FRONT/BACK/BOTH, CW and mirrored instances. Scheduler fixtures execute the
production top-K selector and Java admission/round helpers; they cover saturation before snapshot,
other-lane progress, rejection/failure release, deferred-head fairness and bounded trace storage.
Java boundary tests cover dependency sections in all axes, expiry, ownership replacement and world
clear. Existing external generation/lifecycle tests remain part of the regression suite.
Two earlier supplementary assertions failed because they encoded the old ray flag and a fixed JNI
export count; the behavior fixtures passed. The assertions were updated without relaxing JNI
body checks or generated-header/export comparison. Failed logs are retained.

Not yet evidence: live SERVICE callback interception, complete real producer visuals, comparable
world throughput/interaction distributions, generation versus disk-read cost, high-distance/Sable
churn and final loaded-package preflight. Another user-owned vulkanite client is active; the user
asked to continue code/tests and will notify when it is closed. Do not run or claim uncontaminated
client performance comparison before that notification and a fresh resource check. Do not control
that client. No existing GPU failure is declared solved by these changes.

Additional automated evidence: GAME 174 tests passed; bootstrap 7 passed and 2 skipped
(`java-B9.log`, `java-results-index.json`). `B-native-final.log` / `B-package-final.log` cover the
RelWithDebInfo INSTALL, shader/JNI build and full JAR/runtime verification; `maven-final.log` checks
the unchanged development-artifact contract. Reference packages were built and package-checked,
but their complete historical CTest suites were not rerun. `analyzer-test.log` is synthetic parser
verification only. The face GPU fixture now queries the actual scratch alignment and bounds memory
type selection instead of assuming 4096-byte alignment. No renderer behavior changed for that test.

The bounded trace additionally records submitted chunk-batch payload bytes (not AS/TLAS/SDK totals).
Its scene marker now distinguishes absent and present geometry at the same revision; otherwise a
first publication could be missed. This observation-only correction was applied to all three
comparison versions. Java lifecycle waiting was checked: the interaction futures are waited only
at world clear/switch, not in the per-frame admission path.

### Delivery boundary

Paired packages are deployed to independent `build/manual-acceptance/faces-chunks/*-2` directories.
The candidate/manual copies use the same B artifact. Their Test world and mod/settings fixture is
frozen; comparison runs are single-use, and the launcher has no build or automatic restart step.
No new manual feedback, source commit, staging, tag, push or binary release is claimed. Preserve
fatal propagation, slot/texture generations, in-flight retirement, pipeline-layout workaround and
public vendor-license gate. Long-term memory is not the home for this plan or its acceptance state.

Final local B candidate JAR SHA-256: `C94D647DE56AC0CF806CFB348411AC52034DA66AA74703D7C19AA315F4B878B1`.
Core SHA-256: `CFB6294C5F28FBFC058297B3F8F08151457663A29C0DCC62F1EB4EBE829B6A7F`. Built, embedded and deployed JAR identities agree; runtime
extraction/loading and real SERVICE interception remain pending a client run. Matching PDB and
shader manifest are in the evidence artifact directory. `MANIFEST.txt`, `ACCEPTANCE.md` and
`Start-ManualAcceptance.ps1` provide the protocol. The three launch entries passed identity/JVM
dry-run checks only when recorded under `runs/*/result.json`; this is not a world preflight.
The first bounded probe covers idle interaction and F3+A reconstruction. A repeatable travel/new
generation versus existing-region-read comparison still requires preparation after the user releases
the competing client. No p50/p95/p99 improvement, higher throughput or reduced VRAM is claimed yet.

## 2026-09-23: Face interception startup correction

Status: implemented; build-verified; automated-verified; corrected startup pending.
Evidence: failed real startup PID46548, bytecode, bootstrap regression and packaging.
Applies to: current face/chunk worktree; native product unchanged.

PID46548 exited1 before the menu: registering every GAME class forced ModLauncher11.0.5
to recompute even unchanged class frames. Veil's optional Sodium mixin then resolved absent
ChunkShaderOptions before its optional applicability gate. This is a Java startup failure,
not evidence of device loss or a reason to install Sodium/remove Veil. The failed JAR was
C94D647DE56AC0CF806CFB348411AC52034DA66AA74703D7C19AA315F4B878B1; its loaded
core matched CFB6294C5F28FBFC058297B3F8F08151457663A29C0DCC62F1EB4EBE829B6A7F. Logs and module paths remain in
`build/manual-acceptance/evidence/20260923-faces-chunks/runs/manual-20260923-220550-982`.

FaceStateLaunchPlugin now reads class bytes without resolving types and registers only
classes containing the exact GL call sites it translates. Target discovery and rewrite
share a predicate; unrelated optional classes are not frame-recomputed by this service.
The production discovery regression scans an absent-supertype fixture and real GL calls,
retaining GlStateManager's existing redirect exclusion. Bootstrap8 passed /2 skipped;
full JAR/runtime verification passed in startup-fix-build.log. GAME/native code and runtime
entries are unchanged; their earlier test results are not claimed as rerun.

New manual/candidate JAR: 1CB6A559DC800C9B61C73542901B535EB9EB46EE6BA8C6A901E16E42D8221E60. Native core/PDB/shaders unchanged.
`B-startup-fix-source.zip/json` include necessary untracked files; deployment.json selects
this snapshot. Old source/artifacts/failed run remain intact. A-trace still has the older
transformer and must be corrected before a matched comparison; no performance gain or
visual pass is claimed. This correction supersedes only the prior startup-readiness
assumption; normal manual acceptance remains the next gate.

Corrected startup observation: PID68896 (`runs/manual-20260923-221257-300`) passed the
previous Mixin preparation point, registered 6 actual-call GAME targets, completed
pipeline warmup and resource loading, and resumed responsive 2560x1440 presentation.
The observer verified loaded core CFB6294C5F28FBFC058297B3F8F08151457663A29C0DCC62F1EB4EBE829B6A7F.
No new fatal was present through 22:14:42 local time. This is startup evidence only;
manual world/visual/save results remain pending. The user controls all manual interaction.

## 2026-09-23: Manual face/chunk feedback and six visual regressions

Status: visually-accepted for the user's observed face/chunk/Sable cases; six new
visual reports investigating; current process disappearance unresolved.
Evidence: user observation, PID68896 logs, loaded binary identity and bounded source inspection.
Applies to: JAR `1CB6A559DC800C9B61C73542901B535EB9EB46EE6BA8C6A901E16E42D8221E60`,
core `CFB6294C5F28FBFC058297B3F8F08151457663A29C0DCC62F1EB4EBE829B6A7F`.
No product code or deployment changed during this investigation.

The user accepted observed single-sided materials, chunk-update scheduling and Sable
structure operation. This is not quantitative throughput/latency comparison, exhaustive
producer coverage, long stability or approval of the six cases below. Runtime output
later became3840x2054, so the entire session is not a fixed2560x1440 benchmark.

| Report | Bounded evidence / unresolved check |
| --- | --- |
| Ponder previous/next scene becomes flat-colored, particularly after dwelling | Both views are collected; logs show viewports shrinking to1pixel, repeated executor/composite replacement and DLSS-D warning about exceeding4 viewports. Trace output, GUI target and history must be distinguished before assigning cause. |
| Ponder water reflection content is abnormal | Report confirmed by user; no captured image or shader-stage evidence yet proves normal, transform or history cause. |
| Ponder clipping edges leak/flicker | Composite alpha is a binary threshold of sampled raw first-hit depth while color is temporally reconstructed. This input mismatch and changing crop are concrete investigation paths, not an accepted visual fix. |
| Ground-facing glow lichen emission footprint/texture looks rotated or incorrect | Requires orientation/UV/emission-mask producer-consumer comparison; no root cause established. |
| Sable campfire incorrectly lights particles | EntityProxy derives emission from particle block light minus main-level block light at particle coordinates. Verify Sable light/coordinate contribution before changing intrinsic emission. |
| F3+G fine grid/red surroundings disappear, replaced by coarse section lines/top green grid | Pinned1.21.1 source emits2-block grid lines, colored subdivisions,16-block coarse boundaries and surrounding red verticals. Current source still calls that renderer; investigate capture/strip conversion/publication. This report concerns lost geometry, not the previously accepted fixed PT line width policy. |

Preserved evidence: `build/manual-acceptance/evidence/20260923-faces-chunks/runs/`
`manual-20260923-221257-300/feedback-20260923`, plus original run logs/modules/metrics.
Last observer sample is22:34:14 local; Java68896 and its observer subsequently disappeared
without result.json, a current crash report, native-close record or normal server-stop record.
No agent close/kill command was issued; possible tool-session process-tree termination
is not established as cause. Queried Windows Application/System events supplied no matching
application error or nvlddmkm event around disappearance (an AutoHDR notice is unrelated).
Last recorded integrated-server dimension/sublevel save is22:33:36; this does not prove
normal exit or final unsaved edits persisted. No automatic restart is performed.

## 2026-09-23: Ponder transition and particle feedback corrections

Status: implemented; build-verified; automated-verified; deployment prepared; visual retest pending.
Evidence: preserved PID68896 feedback/logs, pinned Sable bytecode, production-helper tests,
Vulkan shader fixtures and paired packaging. Applies to the uncommitted face/chunk worktree.
Supersedes the investigation-only interpretation in the preceding six-report entry, not its observations.

### Confirmed changes and exclusions

- Ponder outgoing view rectangles no longer collapse against the window to1px. Content-size
  quantization preserves the full translated camera rectangle; the original GUI clipping remains.
  Pixel budget is unchanged. Native capacity replacement/reopen waits for actually submitted
  frame fences before closing prior reconstruction histories, avoiding overlapping retired SDK
  viewports (PID68896 reported the4-viewport limit). It preserves cached geometry/BLAS. A same-frame
  unsubmitted batch cannot be retired by waiting an older fence and is rejected explicitly.
  This rare replacement may still stall; normal frames do not globally wait idle.
- Both primary PT loops now apply the UI owner restriction only to the initial camera segment.
  Reflected/refracted continuations see both collected transition worlds. This fixes a concrete
  contract defect; it does not establish the cause of all reported single-scene water artifacts.
- Ponder composite aligns first-hit coverage with actual input jitter, classifies finite/miss
  samples before bilinear coverage filtering, and bounds-checks depth fetches. Resolved color
  stays in its existing path. Color halos/temporal edge appearance remain visual checks.
- Particle emission samples the actual base Particle.getLightColor implementation, including
  Sable ambient light, within the subclass call. Only the subclass's extra block-light component
  becomes emission; intrinsically full-bright overrides remain intact. Nested/throwing calls
  restore scope. No Sable physics or light-source geometry was disabled.
- F3+G is explained by Sable2.0.5 ChunkBorderRendererMixin: enabled
  `debug_draw_loaded_chunks` cancels vanilla and emits Sable's loaded-chunk grid. The isolated
  user's setting is true. No grid geometry, fixed PT line width, emission or configuration was
  changed. Turning that setting off is an optional vanilla-grid comparison, not a Radiance fix.
- Ground lichen remains investigating. Its bundled specular/emission mask aligns numerically
  with vanilla's unrotated texture; arbitrary rotation is unsupported. The reported run had no
  PBR pack and collectChunkEmission=false, excluding that CPU light-collection path for this case.
  No lichen texture/material change was made. Existing request-file captures now accept explicit
  main-world requests and both Ponder views (at most8 evaluations); they stay off without a request.

### Actual validation and deployment

GAME177 passed; bootstrap8 passed/2 skipped. CTest50/50 passed, including actual GPU execution
of the production Ponder coverage shader and UI-owner predicate, plus history-retirement failure
and ordering tests. Full RelWithDebInfo INSTALL compiled native and shader changes; complete JAR,
runtime resources and Maven development-artifact checks passed. The initial GPU-fixture compile
error (test helper names) was corrected; its failed log is retained, not counted as a passing build.
No game visual result or throughput improvement is inferred from these tests.

Isolated manual/candidate package: JAR F155373DDBF3E69589386D25862C5B67646C820E63BC13B345FCE85C431CAC22;
core.dll DEFA112156C982207A1C9600FEEFE2E63818682F22866EF3C577C6FEEE30559A. Matching PDB, CMake cache, embedded shader/runtime hashes and test XML are
under `build/manual-acceptance/evidence/20260923-faces-chunks/artifacts/visual-feedback/B`.
`B-visual-feedback-source.zip/json` include tracked and necessary untracked files with raw and
CRLF-to-LF hashes. `deployment.json` selects this new pair; prior sources, packages and runs remain.
Only JAR deployment changes the isolated instances; saved worlds and user settings are preserved.

The launcher now has an independent hidden supervisor (`Start-ManualAcceptanceDetached.ps1`).
A harmless12-second child survived its launching tool and completed; this is process-hosting
evidence, not proof of why PID68896 disappeared. No new client is killed automatically.

Remaining: user Ponder dwell/forward/back transitions, water, clipping, Sable campfire particles,
and lichen capture if still abnormal. Earlier face/chunk/Sable acceptance belongs to the old pair;
quantitative A/B performance, prior GPU-loss root cause and public binary licensing remain open.
No staging, commit, history rewrite, remote update or public distribution occurred.

Startup observation for the visual-feedback package: PID92456, supervised independently by
PID74100, completed pipeline warmup/resource loading and resumed responsive2560x1440
presentation by23:14:17 local. Actual extracted/loaded core matched
DEFA112156C982207A1C9600FEEFE2E63818682F22866EF3C577C6FEEE30559A.
Run `manual-20260923-231305-345/startup-identity.json` records this startup-only evidence;
world, Ponder and particle visuals remain unaccepted. Existing unsupported Veil shader/layout
messages are not new fatal evidence or proof those optional features work. No client was stopped.

## 2026-09-23: Ponder raster restoration and Create stencil crash

Status: implemented; build-verified; automated-verified; bounded runtime-observed; final user
visual acceptance pending. Ponder PT archived by user decision.
Evidence: pinned Create 6.0.10 / Ponder 1.0.82 bytecode, actual native crash, real screen/scene
smokes, behavior tests and paired package identity. Applies to the existing uncommitted face/chunk
worktree; no Git history change. Supersedes the active Ponder PT acceptance requirement in the
preceding feedback entry. [Archive inventory](history/ponder-pt-2026-09-23.md).

### Failure and implementation

PID92456 (`manual-20260923-231305-345`) ended at 23:26:50 with EXCEPTION_ACCESS_VIOLATION,
not an agent close or established Vulkan device loss. Retained `hs_err_pid92456.log` identifies
ConfigScreen -> StencilElement.prepareStencil -> DirectFaceState.glDisable -> GL11.glDisable,
capability2960 (STENCIL_TEST), with no OpenGL context. The transformer intercepted glEnable/Disable
for face capture but the helper delegated non-cull capabilities back to native GL. Last recorded
pause/save was23:25:51; no normal final/native-close evidence exists for this process. Do not
confuse it with PID68896's separately unresolved disappearance.

DirectFaceState now routes supported direct boolean state calls to the existing Vulkan state
setters (including stencil); unsupported capabilities fail explicitly in Java instead of calling
contextless GL. No OpenGL context was added. DirectFaceStateTest exercises the actual dispatch
seam, Catnip's disable/enable sequence and unsupported-call behavior.

PonderScenePathTracingMixins and PonderUILifecycleMixins are unregistered, with implementation,
proxies, native service/shaders/tests and historical evidence preserved. The actual original
Ponder draw/camera/transition sequence runs in the existing Vulkan raster GUI translator.
CatnipRasterScreenMixins enters a nested, exception-safe RasterPreviewScope around the original
AbstractSimiScreen.render. Block/fluid previews preserve original shade/light and vanilla fluid
production; the existing exact Sable bulk-consumer bridge remains when required. World PT behavior
is unchanged. RasterPreviewScopeTest tests nested exception restoration; PonderDefaultContractTest
is a supplemental registration guard, not a rendering test. Catnip's real Vulkan-backed framebuffer
helper remains active. No shared PT scene is created by the restored Ponder entry.

### Validation and evidence identity

GAME180 passed, bootstrap8 passed/2 skipped (the final packaging invocation reused the already
passing bootstrap outputs). Full distributedJar/runtime/Maven developer-artifact checks passed.
MCVR product files did not change in this sub-batch; its earlier CTest50/50 is historical evidence,
not a new execution. Both native DLL and embedded SPIR-V are checked unchanged during deployment.

Retained local evidence root: `build/manual-acceptance/evidence/20260923-faces-chunks`.
Final raster JAR: `9B13C9E8CFAED8F861F4526DB84B902DC20AC595AE1DCF68C262547FF25146F4`.
Native core: `DEFA112156C982207A1C9600FEEFE2E63818682F22866EF3C577C6FEEE30559A`.
Matching PDB remains in `artifacts/visual-feedback/B`. `artifacts/ponder-raster/final`,
`B-ponder-raster-final-product-source.zip/json`, final documentation-inclusive snapshot and
`deployment.json` preserve exact inputs, all necessary new files and selected deployment.
Snapshot normalization is exact bytes plus CRLF-to-LF hashes; docs-only updates do not change
product identity. Ignored diagnostic sources and their JAR are retained separately, default off.

Earlier preflights PID53364 and PID22160 used JAR323E72F94DED4465BDE274484E14788A28FF3CA2819CF6E43D56D3A641D208A3
with the same core. Both actually opened the registered Create config factory, rendered Ponder
mechanical-piston scenes, scrolled forward/back, saved every dimension/sublevel and completed
native close with exit0. Each spent47seconds after world-ready. First screenshots showed missing
widget icons after switching; a second same-product run did not reproduce it. Sampled GUI depth
clear requests had depth writes enabled, so no guessed depth-mask change was made. First images
were viewed in the task; the initial probe reused screenshot names, so only the second run's PNGs
remain on disk. The probe now prefixes unique capture IDs. This evidence limitation is retained.
Final-package runtime/deployment results are appended below when observed; early smokes are not
relabeled as tests of the final JAR (whose Sable helper debug-line table was rebuilt after removing
an incidental blank line).

### User decisions and remaining acceptance

The user confirms Sable particles repaired, considers the F3+G configuration explanation known,
and accepts the automatic translation of observed Sable rendering. These observations belong to
the earlier F155373D package; they are not new-package or exhaustive mod-compatibility passes.
Ponder PT flat color/reflection/crop issues are archived, not visually fixed. Lichen is clarified
as partial missing pixels; no rotation or mask edit was made. Default Ponder/config visuals,
intermittent widget icons and water examples await user observation. Quantitative A/B performance,
prior GPU-loss cause, full lifecycle/G3 and public binary licensing remain separate open boundaries.

Final-package runtime observation: PID38908 (`runs/raster-20260923-235016-497`) loaded the
recorded DEFA1121 core from the isolated extraction path and the final9B13C9E8 JAR. It reached
world-ready at23:50:47, opened the registered Create config, rendered mechanical-piston Ponder,
scrolled forward/back, requested normal stop at23:51:34 and completed all-dimension/sublevel
saves and native close (`fatal=false`, `deviceLost=false`, `closeCalls=1`), exit0 at23:51:35.
The retained unique PNG captures show scene geometry/text/outlines and widget icons in this run;
this is agent observation, not user visual acceptance or exhaustive water/GUI/long-stability proof.
Configuration:2560x1440, view8/simulation32, Vanilla PT, RR Balanced with dlssRrModel=6, FGoff, Reflexon;
loss tracing on, Aftermathoff, no injected faults. No runtime UI PT evaluation was requested by
Ponder. The47-second world interval is a bounded screen/scene/lifecycle smoke, not a benchmark.

Manual deployment/startup observation: the final pair is deployed to the preserved `manual-2`
isolated world/settings. Independent supervisor63652 launched PID46312 at23:55:37;
resource loading completed by23:56:02 and the responsive window loaded the exact DEFA1121 core.
`manual-20260923-235537-286/startup-identity.json` fixes package/path evidence. Automatic Catnip
smoke and stop are explicitly disabled for this user run. User interaction/visual results remain
pending. This appended startup record is documentation-only after the final product snapshot.

## 2026-09-24: Raster equivalence and Ponder shared-code assessment

Status: investigating; proposed isolation, no product change.
Evidence: static source, historical diff and test inspection; no new execution evidence.
Applies to the retained dirty Radiance `e4c5bc7` / MCVR `8208f30` trees.
Supersedes: no historical test result; narrows any inference that restoring the Ponder producer
proves complete OpenGL-equivalent output.

The user requested raster equivalence evidence and a disposition of engine-wide Ponder changes.
The [targeted assessment](research/RASTER_PARITY_AND_PONDER_RETIREMENT.md) records unconditional
entity-shadow cancellation, Catnip-only original-lighting scope, bounded shader translation and
GUI-alpha observability as limits needing differential coverage. Existing Vulkan fixtures and
screen smoke are not a paired GL/Vulkan proof. The same report distinguishes archived UI-only
execution, dormant multi-view infrastructure, independent correctness repairs and actual avoided
allocation work. Multi-view sharing provides no corresponding saving for a single main view.

Only records and a 31-file exact-byte inspection manifest were added; product code, artifacts,
client process/settings and Git index were not changed. No build/test rerun was appropriate to
this documentation-only assessment. The manifest identity and proposed reference/comparison
gates are in the report. Runtime parity, measured performance, lichen, GPU-root-cause and license
boundaries remain open. Retirement recommendations are not recorded as implemented or accepted.

## 2026-09-24: Raster scope, diagram fade and Simulated staff integration

Status: implemented, build/automated-verified; isolated runtime observations and manual boundaries
below. Supersedes the preceding assessment-only authorization boundary. Ponder PT remains archived.
The existing dirty single-face/chunk/particle/raster-Ponder work is preserved; no index or history
operation is part of this batch. Detailed source reasoning and remaining differences live in
[the raster/Simulated assessment](research/RASTER_PARITY_AND_PONDER_RETIREMENT.md#2026-09-24-authorized-implementation-and-simulated-source-comparison).

Changes: actual raster scopes preserve original block/fluid lighting and entity shadows, including
non-Catnip GUI producers; nested world stages keep PT policy. External shader translation handles
all `gl_FragCoord` forms with one origin conversion. Simulated diagram vertices retain spring stress.
The original staff-only Veil stage now captures original wave geometry into PT and lock geometry
into `PRIORITY_ONLY`; custom FBO/non-world calls retain the original route. Lock full-bright/cutout
semantics drive ordinary PBR emission and matching priority coverage. First-person staff/plunger
anchors use the same inverse camera transform as captured held-item geometry; third-person producer
placement and original one-rope-per-link topology are retained.

Native changes in the paired dirty MCVR tree: lazy UI PT command allocation/recording/submission;
correct diagram gradient and logical origin; shared surface-albedo overlay helper; shared priority
material coverage preserving fractional text and opaque surviving lock cutouts. Independent texture,
upload, descriptor, frame/history retirement, synchronization and fatal guards are preserved.

### Build and machine evidence

Root: `build/manual-acceptance/evidence/20260924-raster-simulated/` (ignored acceptance evidence,
not disposable backup). The pinned reference jar/assets/bytecode are under `reference/`.
`candidate-final-source.json/.zip` include all tracked and necessary untracked source inputs:
1,044 Radiance and 530 MCVR files, exact bytes plus CRLF-to-LF comparison hashes. Post-run documentation
appendices do not change the product files in that snapshot. `deployment.json` records source,
CMake configuration, runtime/shader members, dependencies and artifacts; matching PDB retained.

- `final-java.log`: GAME 185/185; bootstrap 8 passed, 2 skipped; package/runtime checks passed.
- `native-build-3.log`: RelWithDebInfo INSTALL and affected GPU-test target succeeded. This build
  regenerated dependency/shader outputs and relinked the DLL; the older D34B76C8 DLL hash is not
  the final native identity.
- `ctest-priority-final.log`: selected 14/14 native tests passed, including real GPU framebuffer,
  face, diagram/surface/priority-coverage behavior and deferred UI command lifecycle.
- `shader-check/results.json`: ten affected Vanilla PT/Advanced hit-shader variants compiled
  with pinned preset defaults; this is not runtime acceptance of Advanced or every preset option.
- `package-final.log`: full distributed JAR plus `verifyDistributedJar`/`verifyRuntimeResources`.

Final JAR SHA-256: `56D8B9C44B6A01B3DC1EFDD9C06C213B418E7387E2244F7049AFFA0D758CFA5D`.
Final core.dll SHA-256: `D9257915FBAA5E69F7C36746173BC95E9D78F9FF4E633137C29673A7C34828A0`.
Artifacts are under `artifacts/candidate-final/`; manual deployment uses the isolated
`build/manual-acceptance/raster-simulated/manual/` copy, with no automated fixture enabled.

### Failures and scope of acceptance

PID81192 failed Mixin preparation: the new beam callback lacked `static` for the pinned lambda.
It was corrected and a test now checks the actual producer/callback bytecode and invocation.
Closing its mod-error window returned process code 0: that is **not** a startup pass.
PID88592 then exposed an audit-fixture typo (`physics_staff` instead of `creative_physics_staff`);
the Java exception saved/closed normally and the fixture was corrected. PID55632 completed the
diagram/config/Ponder flow but supplied a world-coordinate beam target where the producer requires
plot coordinates: its lock result is usable, its beam result is not a positive test.
PID80028 exercised both beam and lock through `NATIVE_BUILT` and the screen sequence, with normal
save/close and exit 0. Those three latter runs used B71A8FCF / D34B76C8, before the final priority
cutout correction; their results are not silently transferred to the final package.

The automated fixture drives actual original renderers/screens in an isolated world and creates
only diagnostic beam/lock/client diagram state. It does not verify server locking mechanics,
all connected diagrams, spring stress under load or user's visual approval. No controlled fault,
driver reset, production/Prism data operation or public binary distribution was performed.
Full GL parity, diagram greeble alpha occupancy/12-Hz differences, fast moving rope attachment,
spring/hurt visual comparison, historical GPU fault cause and licensing remain explicit limits.

Final-product runtime: `runs/manual-20260924-013107-162`, PID3896, loaded the recorded D9257915
DLL (matching build, embedded and extracted hashes). It reached the world at 01:31:59 local time,
exercised lock/wave capture, diagram opening/rotation at GUI scale 2, Create configuration, and
original Ponder next/back navigation. After approximately 79 seconds in-world it requested normal
shutdown; all dimensions including Sable storage saved, native close completed with `fatal=false`
and `deviceLost=false`, and the process exited 0. Submission counters were 5,719 attempts/successes.
The scoped ledger recorded 5,534 lock and 818 wave intents reaching `NATIVE_BUILT`; these are capture
observations, not proof of physical locking or pixel correctness. The final run does not inherit
the earlier candidate's multi-scale observations. See `RESULTS.json`, `MANIFEST.txt` and
`ACCEPTANCE.md` in the evidence root for exact identities, machine records and manual steps.

`Start-ManualAcceptance.ps1` defaults to the separate `raster-simulated/manual` instance, validates
the frozen source/artifact/dependency identities, and does not build or deploy. Automated screen,
beam and lock fixtures are disabled for that entry. Only its JVM dry-run has been executed; manual
visual acceptance remains pending. The external audit source is retained in
`external-audit-source.zip`, separately from the product snapshot.

### Authorized Prism deployment

At the user's subsequent request, the same final Radiance JAR and audit JAR were deployed to
`E:\Minecraft\PrismLauncherDev\instances\Radiance 1.21.1-neoforge\minecraft\mods` (non-portable).
Product JAR remains `56D8B9C4...`; audit SHA-256 is
`61F003B8A2815AF206A54A17F09703AEFF4FA75107FF25A87EA15D83BC630560`.
No prior Radiance/audit JAR existed. Existing mods/worlds/render settings were preserved.
The instance retains NeoForge 21.1.251, distinct from the 21.1.250 isolated preflight.
A dangling OpenAL DLL override was removed from `instance.cfg`, and unrelated screen-effect
audit sampling was disabled. The original config and deployment hashes are retained under the
instance's `.radiance-deployment/20260924-020551/`; the evidence root also contains
`Prism-deployment-20260924-020551.json`. No automatic fixture/fault injection was enabled.
This is deployment only: the user will launch; extracted/loaded identity and visuals are pending.

## 2026-09-24: NeoForge 21.1.251 and launcher-visible distribution metadata

Status: implemented, build/automated-verified, isolated menu runtime-observed, deployed; user
Prism/world acceptance pending. Supersedes the preceding deployment's loader mismatch, not its
historical artifact or runtime evidence. All earlier dirty product work is retained.

The user requested alignment with their Prism instance's NeoForge 21.1.251 and complete mod
information. Radiance and the external audit project's compile dependency and exact dependency
declaration now use 21.1.251. Installation text was updated; no native or rendering logic changed.

The single installable JAR retains SERVICE/GAME isolation. The outer service must run before
normal transformed Minecraft/mod classes to own early Vulkan/window initialization; the nested
GAME JAR carries the ordinary mod declaration, classes and mixins. Prism's archive parser does not
follow our custom `META-INF/radiance/game.path` to read nested mod information. Its supported
`mcmod.info` display format and the real logo are now generated into the outer JAR; this is not a
claim of support for legacy Forge. Both representations share one build metadata map, preventing
name/version/author/description/icon drift. The outer JAR still has no NeoForge mod declaration.

`DistributionMetadataTest` reads the actual assembled outer/nested archives, compares metadata
and logo bytes and checks the pinned loader's real SERVICE classifier and normal mod reader.
The first classifier test lacked SecureJarHandler's JVM lookup access; bootstrap test JVM options
were aligned with the launcher's opens (classpath test target `ALL-UNNAMED`) and the test passed.
No product exception was swallowed. A first menu command had an unquoted dotted PowerShell
property argument and failed before launching; the corrected command and both logs are retained.

Evidence: `build/manual-acceptance/evidence/20260924-neoforge-metadata/` (ignored, retained).
GAME 185/185 passed on the upgraded dependencies; bootstrap 10 passed and 2 opt-in GPU tests
skipped, including the two new distribution tests. `verifyDistributedJar`,
`verifyRuntimeResources` and `verifyMavenDevelopmentArtifact` passed; audit build passed (no audit
unit tests). The unrelated native suite was not rerun and core.dll was not rebuilt.

PID3528 used the new package in `build/manual-acceptance/neoforge-21.1.251/full`, a menu-only
isolated directory without a test world. It reached TitleScreen, remained there approximately
38 seconds, then stopped normally. Logs report NeoForge 21.1.251 and one Radiance mod, with no
legacy-format/repeated-mod rejection. Native close completed, 6,019 submission attempts/successes,
`fatal=false`, `deviceLost=false`, Gradle/client process success. `loaded.json` identifies the
actual native module. This is loader/menu acceptance, not renewed world/visual or stability proof.

Deployed to the user-authorized Prism instance
`E:\Minecraft\PrismLauncherDev\instances\Radiance 1.21.1-neoforge\minecraft\mods` (non-portable):

- Radiance JAR SHA-256: `39F76BC4FC232E85B3776B21188AED9FD5E05D3B730D61C085A8A89104E5EF62`.
- Audit JAR SHA-256: `C32B59935F3626F06C0E9C0C93CEA9AFFA87DEE99A2B48CF5D057A3A00664B1D`.
- Unchanged core.dll SHA-256: `D9257915FBAA5E69F7C36746173BC95E9D78F9FF4E633137C29673A7C34828A0`;
  build resource, new JAR member, extracted file and actual loaded module agree.

`Prism-deployment.json` records identities and recovery paths. Existing accepted-candidate
artifacts provide verified rollback copies; no duplicate binary backup was created. The instance
configuration, other mods and worlds were untouched in this upgrade. Display in the actual Prism
UI and gameplay remain user observations; public binary licensing and all existing visual/GPU
limits remain open. No staging, commit, push, tag or public distribution occurred.

## 2026-09-24: Runtime testing and Prism handoff policy

Status: user-approved workflow, documented; no product or instance change.
Subsequent automated Minecraft runs use isolated repository `run/` subdirectories. Manual
handoff consists of deploying matched mod artifacts to the designated Prism instance for the
user to launch and operate. The agent reads machine evidence; visual acceptance remains the
user's observation. Existing repository manual scripts and historical results remain evidence,
not the default future handoff. The canonical rule is in
[the maintenance policy](DOCUMENTATION_POLICY.md#automated-runtime-and-user-acceptance-locations).
## 2026-09-24: Reconcile accumulated work for the signed source checkpoint

Status: source/evidence reconciliation; no new product change or runtime acceptance.
Evidence: static, existing build/automated/runtime records and freshly checked artifact hashes.

The user authorized amend and source synchronization before standalone MCVR replay work.
Every product/test/build input, including necessary untracked source, matches
`build/manual-acceptance/evidence/20260924-neoforge-metadata/final-source.json` exactly in both
repositories. Only this repository's development ledger and documentation policy changed after
that snapshot. Historical GAME185, bootstrap10 passed/2 skipped, selected native14, shader and
bounded client results keep their recorded scope; no build or GPU test is claimed as rerun here.
The verified JAR remains `39F76BC4FC232E85B3776B21188AED9FD5E05D3B730D61C085A8A89104E5EF62`
with native `D9257915FBAA5E69F7C36746173BC95E9D78F9FF4E633137C29673A7C34828A0`.

The candidate includes face-state and scheduling work, Ponder PT archival/default raster return,
raster/Simulated corrections, NeoForge 21.1.251 metadata and their tests/records. Generated files,
runtime DLLs, logs, instances, worlds and the external diagnostic project stay outside the source
commit. The separately archived diagnostic source remains reproducible from its evidence ZIP.
Recovery/evidence location: `D:\Workspaces\Artifacts\RadianceCommitMaintenance\20260924-amend-push`
(non-portable). Original commit metadata/parent and the single `Initial port` policy are preserved.
New standalone replay work is not included in this checkpoint. Existing visual/performance,
first GPU fault and public-binary permission limits remain open; no new user acceptance is inferred.

## 2026-09-24: Repository fixture capture for native scene replay

Status: paired package built; capture/native replay exercised; user scene selection pending.
Evidence: source manifests, package checks, isolated runtime logs and GPU readbacks.
Applies to: Radiance after `b8568cafd222c8169cd6c8fd3d5771690e3425ca`; MCVR worktree after
`4778983778d53084132ce84ed0e567579ed6ed7b`. Replay changes are not part of those source checkpoints.

The user selected the repository isolated test world for the first native replay, then explicitly
requested an interactive client left open to choose a high-pressure/high-distance scene. This is
a task-specific exception to the default Prism manual handoff rule; no Prism data was accessed.

MCVR now exports the actual static world inputs and renders them in a native process without
Minecraft/JVM. See the historical tool contract (retired; see [the later correction](#2026-09-24-one-shot-scene-replay-retirement-and-amend-preparation)) in the sibling
checkout (local cross-repository reference) and MCVR's same-date ledger. Radiance product/Java
source was unchanged; complete distributed JARs were rebuilt with the matching RelWithDebInfo DLL,
not patched by copying a DLL into an old installation. `distributedJar`, `verifyDistributedJar`
and `verifyRuntimeResources` passed. Earlier in this task bootstrap10 passed/2 skipped; the final
packaging-only pass did not rerun all GAME/GPU tests. Final selected native tests8/8 include the
material-faces GPU test and the new scene replay contract test.

Evidence (non-portable): `D:\Workspaces\Artifacts\MCVRSceneReplay\20260924`.
Earlier two capture attempts were rejected by exporter preconditions, not client crashes; both
completed normal saves/close. `iteration-3` PID83828 exported972 instances/unique BLAS and317MiB,
then saved all dimensions and exited0, native close complete/fatalfalse. `replay-loader-2`
PID82060 rendered that snapshot for40seconds/4,497frames and exited0 without jvm.dll. The native
window corresponded to the captured scene during agent inspection; no user visual/performance
acceptance is inferred. Reference/replay images, timing CSV, loaded DLL evidence and rejected
attempts are retained separately. This is not a fixed-condition performance comparison.

The final interactive package is recorded under `iteration-4/MANIFEST.json`, source ZIP/manifest
and runtime scripts. JAR `6D95BC22B2BB0D1011E2EE44ACC5421C7A9951B3AF0BEBDA984E6AB40C1B6673`;
core `681AE67F1D50AA2F9AC8E5B5C73EC9749EE567936800AE8178A30D9110D18D34` (same DLL as the
successful standalone run). Capture iteration3 used BEB74D66... before the standalone startup and
output-diagnostic containment fixes; do not merge their identities. Build DLL, embedded package
and extracted/loaded module are checked separately.

Interactive instance: `run/scene-replay-20260924/capture-4`, Test world copied from the prior
isolated fixture. NeoForge21.1.251, Create6.0.10, Aeronautics1.3.2, Sable2.0.5 and audit mod.
Initial configuration:2560x1440, view8, simulation32, Vanilla PT, RR balanced (1485x835), modelF,
Reflex1, FGoff, vsyncoff, SHARC/jitter on. The user may choose higher view distance after startup;
do not retroactively attach such settings to the first snapshot. `Start-Manual.ps1` has automatic
probes/exit/capture disabled; capture is armed from startup but waits for a later explicit request.
Device-loss tracking stays on, Aftermath off. No automatic stress or further source submission.
First-fault causation, broader stability, specialist visuals and public-binary permission remain open.

## 2026-09-24: User pressure scene capture and corrected native replay

Status: implemented; build-verified; automated-verified for named checks; runtime-observed.
Evidence: source/artifact manifests, contract test, real GPU capture/replay, normal close logs.
Applies to: dirty replay work after Radiance `b8568cafd222c8169cd6c8fd3d5771690e3425ca` and
MCVR `4778983778d53084132ce84ed0e567579ed6ed7b`; no new source submission.
Supersedes: the preceding entry's pending scene selection and native RR equivalence/timing scope.
Remaining acceptance: controlled performance comparison and user inspection of native output.

### Capture and evidence correction

The user selected view distance 32 in a newly created **Extreme** world, then explicitly saved
and exited PID50464. Logs confirm saving all dimensions and native close, exit 0. The first
pressure capture safely rejected its 512 MiB limit; it did not produce a valid snapshot. Its
per-allocation registry pruning also contaminated CPU performance and is not a normal-game
baseline. MCVR now prunes periodically, stops registration once owners are resolved, and requires
an explicit larger budget with host-only readback, RAM/disk preflight and retained GPU owners.

The exact saved Extreme world and settings were copied from `capture-4` to
`run/scene-replay-20260924/capture-5`; the original save was preserved. The passive external audit
probe logs frame/focus data and accepts a normal shutdown request without changing camera or
world. Capture PID41104 used 2560x1440, view32/simulation32, Vanilla PT, RR balanced 1485x835,
model F, four bounces, SHARC/jitter on, Reflex1, FG off, vsync off, no PBR pack. NeoForge21.1.251,
Create6.0.10, Aeronautics1.3.2, Sable2.0.5; RTX4080SUPER/616.92. The user's saved position was
(0.5,512,0.5), yaw -90/pitch90. Device-loss trace was on; Aftermath was off.

At about 150 seconds in-world it captured 13,038 instances/unique BLAS, 12,501,568 triangles,
5,988 buffers and 272 textures: 2,913,864,624 readback bytes under a 4 GiB limit. This is the
actual partially loaded scene, not a claim that the whole 32-distance region finished loading.
The owning swapchain frame was not reacquired before shutdown; its readbacks published during
normal resource retirement at close after the GPU completion check. At about 218 seconds the
probe requested normal stop. All three dimensions saved; native close reported fatal=false,
deviceLost=false, one close call and 3,964 successful submissions; process exited 0.

### Corrected native comparison

The standalone launcher initially omitted Streamline beginFrame/Reflex/PCL entry calls normally
driven by Java. History readbacks exposed first-frame agreement followed by overbright/black
output. This invalidates RR timing/visual equivalence from `replay-loader-2`,
`replay-pressure-1` and `replay-pressure-history`; retain their process/geometry evidence only.
It is a replay integration defect, not proof of a Minecraft RR defect. The corrected loop uses
the same SDK frame lifecycle and rejects repeated tokens. Initialization batches uploads/BLAS
with real fences, and static parsing/remapping occurs before the measured loop.

`replay-pressure-2`, PID51796: no jvm.dll, 40.007 seconds, 2,783 frames with 2,783 unique SDK
tokens, exit0. First/32nd/256th/final readbacks retain reference colors; final RGB MAE is
2.856/255 across independent temporal histories. This is agent inspection, not visual acceptance.
Excluding initial10 seconds, real-frame p50/p95/p99=12.791/20.739/27.058ms (mean14.030ms);
completed GPU p50/p95/p99=12.368/13.247/14.003ms. Minecraft's focused pre-capture140-150s
interval was86.817/92.351/95.782ms (mean86.697ms), GPU21.792/25.006/26.868ms. Native focus
varied (149/2,139 focused frames), Minecraft still loaded chunks, and replay excludes streaming,
ticks, GUI and physics. These are diagnostic distributions, not a proven speedup or JVM-only cost.
Full-card memory peaks13,945MiB capture/13,029MiB replay include desktop/SDK/other allocations;
VMA totals also include host readback and exclude SDK ownership, so neither proves VRAM savings.

### Artifact and validation index

Non-portable evidence: `D:\Workspaces\Artifacts\MCVRSceneReplay\20260924`.
`iteration-5` contains source ZIP/raw+LF-normalized hashes, separately archived external audit
source, settings, logs, loaded DLL record and capture inputs. Source-manifest SHA-256:
`E25C0024DDFAAFCAB1285B7DC4D5C23DF197F108916993F933851FF070EFC819`.
Capture JAR `49EB9C8A862D1493203B3756AE021219C944449BD62229DC2516F2D55603E819`;
build/embedded/extracted/loaded core
`CDE37551C50E95F654FC79C966F2DCA54038696254D20274DEB281BD5B029F4A`.
`snapshot-1/scene.json` SHA-256
`0A75888F3DD2C791F755E99F2E8B809A694E295572AD824ADA9302BABABDA05E`.
`replay-pressure-2` contains final source ZIP/manifest
`E260784F7C0C0182AE4B8161273BD4D63D687B95559C1B518A52096D93A5BB60`, matching PDB and loaded
replay core `56CF0A565481FB074EB207B978EA7A93E15F4653AC9071F79E4F12D7C3E10F10`.
The final DLL changes the standalone path; capture JAR identity remains distinct and was not
rewritten to imply it contains the later loader. Current documentation is appended after snapshots.

This round ran native RelWithDebInfo core/tool/test builds, scene-replay CTest1/1 after contract
and loader preparation changes, external audit build, and Radiance `distributedJar`,
`verifyDistributedJar`, `verifyRuntimeResources`. Later standalone SDK-entry build and real replay
provide bounded integration evidence; no full GAME/bootstrap/GPU suite rerun is claimed.
All capture/replay runs here exited normally; no device-loss stability closure is inferred.
No Prism access, new rendering optimization, Git mutation or public binary distribution occurred.

## 2026-09-24: Interactive movement in the captured native scene

Status: implemented; build-verified; bounded runtime-observed; user input/visual inspection pending.
Evidence: native behavior test, loaded-module identity, camera CSV and before/moved/restored images.
Applies to: standalone MCVR replay after paired source checkpoints above; Radiance product unchanged.

The user requested camera movement and turning. Interactive native runs now support RMB look,
WASD flight, Space/C vertical motion, Shift/Ctrl speed, R restore and Escape exit. Timed runs remain
fixed unless an explicit bounded camera smoke test is enabled. MCVR's same-date free-camera ledger
and tool README own implementation details and constraints. Captured world inputs remain unchanged;
current/previous instance origins move with the camera and R safely resets temporal histories.

Non-portable launch/evidence path:
`D:\Workspaces\Artifacts\MCVRSceneReplay\20260924\replay-free-camera\Start-Interactive.ps1`.
New standalone core `E06B2AD5A6C76528EC5E59DFF4622E6F45D5AA7BDEED549A057C64C022DEA29F`,
source-manifest `280EB8F902EA4A615E55213A3F597DDA30BF064957D66DD1AD257DA135DEB113`, matching
PDB and original `iteration-5/snapshot-1` are pinned by the launcher/manifest. Capture JAR and
prior `replay-pressure-2` benchmark package were preserved; no rebuild/deployment to Minecraft.
Core/tool/test builds and selected native contract1/1 passed. Scripted GPU PID53140 ran20.0084s,
1,595frames, moved39.905blocks, restored the eye position (Y513.62, versus saved player feet Y512),
and exited0. Restored image RGB MAE2.218/255 versus pre-movement; agent inspection only. No new
performance comparison, user visual acceptance or broader stability conclusion is implied.

Free flight has no collision/new chunks and leaves captured hands, entities, textures, time and
camera environment frozen. Moving outside the captured region cannot reveal uncaptured data.
No Prism/world mutation, staging, commit, push or public binary distribution. Historical fault,
quantitative comparison and license boundaries remain unchanged.

## 2026-09-24: Medium-distance comparison scene selection

Status: deployed; runtime-observed startup/world entry; scene selection and measurements pending.
Evidence: new process logs, loaded DLL hash and isolated launch manifest; no product rebuild.

At the user's request, launched `run/scene-replay-20260924/capture-6` to select a moderate-pressure
scene. Copied capture-5's saved Extreme world, mods and settings, preserving the source fixture;
initial render distance was explicitly changed to16, simulation32 and other quality settings
retained. Quickplay was removed so the user controls world selection. Automatic probes, capture
requests and shutdown are disabled; the bounded exporter is armed for a later manual request.
The external launcher records logs and full-card memory without modifying the user's gameplay.

Non-portable evidence: `D:\Workspaces\Artifacts\MCVRSceneReplay\20260924\iteration-6`.
PID55460 loaded capture core `CDE37551C50E95F654FC79C966F2DCA54038696254D20274DEB281BD5B029F4A`
from the instance's extracted runtime; JAR
`49EB9C8A862D1493203B3756AE021219C944449BD62229DC2516F2D55603E819` is unchanged from iteration5.
Logs show player login, world frame commits and successful presents; the client is left running
for scene selection. No snapshot request or comparable performance interval has been declared.
Prism and the earlier captured/replayed artifacts remain untouched; no Git operation occurred.


## 2026-09-24: Medium scene sequential game and native comparison

Status: runtime-observed; bounded GPU benchmark; agent image inspection. User visual acceptance
and renderer optimization are not claimed. Evidence: raw per-frame CSV, native queue counters,
separate JFR, snapshot readbacks, loaded-module identities and normal close records.

The user selected Medium in capture-6 at render16/simulation32, 2560x1440, position
(0.5,100,0.5), yaw -120.89997, pitch10.199996, Vanilla PT, RR balanced (1485x835), model F,
four bounces, SHARC/jitter enabled, Reflex1, FG disabled and vsync disabled. Native max/inactivity
limits were260; vanilla options retained maxFps120. The user froze daylight/weather. Selection
PID55460 exited0 after all dimensions saved and native close completed, fatal/deviceLost false.
The prior Extreme capture is not this baseline.

Preserved selection evidence in `iteration-6`: a separate 40-second JFR and a reference capture
(scene SHA `D205AF6E2D60267B442A938D2BF88AC1AFA0377374A0DD7E4650EA4125F0D71C`). That process
had no exact frame probe; its results are not substituted for capture-7's timing. The new
`run/scene-replay-20260924/capture-7` copies the saved Medium world/settings/mods, with the
pre-run world hashes retained. Only the ignored external diagnostic changed: SceneReplayProbe
samples existing ready/queue counters every5s, keeps per-frame CPU/GPU samples and performs
bounded normal stop. It does not move the player or edit the world. Diagnostic Gradle build
passed; no product rebuild or unrelated tests were repeated.

PID36400 entered the world51.45s after process launch. Ready count reached8207 by30.68s after
entry (5s sampling resolution), remaining there through212.26s. This is observed client geometry
settling for an existing saved world, not isolated disk-read/new-generation throughput or proof
of zero future updates. Occasional live world rebuilds continued. Sampled native queues were
empty over the final35s before capture. Capture was requested at151.22s, exported during normal
close after its retained context completed, and its readback/write interval is excluded.
All dimensions saved;4982/4982 submit attempts succeeded, closeCalls1, fatal/deviceLost false,
exit0. No GPU-fault retry occurred.

| Sample | Real frame mean / p50 / p95 / p99, ms | Mean real FPS | GPU mean / p95 / p99, ms |
| --- | --- | --- | --- |
| Game entry0-30s,643 frames | 46.59 / 43.01 / 57.21 / 89.44 | 21.46 | 13.54 / 17.12 / 24.58 |
| Game steady90-150s,1348 frames | 44.50 / 43.94 / 50.69 / 58.40 | 22.47 | 13.64 / 14.99 / 15.89 |
| Native steady20-60s,2997 frames | 13.35 / 13.26 / 14.38 / 15.34 | 74.93 | 13.31 / 14.15 / 14.74 |

All reported steady samples had focus. The first60s native run PID57368 lost focus and is
retained separately as `replay-medium-1`, not used for this matched comparison. A second60s
run PID24520 used the same binary/snapshot; computer-use activation preceded the selected
20-60s interval. It completed4386 frames with4386 unique Streamline tokens, exit0, no JVM.
The native loop still executes world preparation/TLAS, PT/RR, post and present. It freezes
geometry/textures/time and excludes Java/game simulation, streaming, GUI and dynamic geometry
production. Therefore3.33x observed FPS is not an achieved Minecraft optimization or a promised
speedup. Similar GPU times and the profiled render thread using about0.96 logical CPU support
a CPU-side limit here; the frame-time difference is not exclusively Java or CPU active time.

Separate JFR leaf samples implicated queueBlockEntitiesRebuild's section scan and
AuxiliaryTextures.loadAndUpload/NativeImage.mappedCopy; native samples included submitCommand,
entity build/queue and texture upload/state calls. Counts are not exclusive-time percentages.
Six JFR GC pauses totaled45.43ms over40s (largest20.01ms), insufficient evidence to attribute
the sustained44ms frame interval to GC. The steady allocation log averaged114.6 entity-buffer
allocations/s and262.8MB/s allocated; these are allocation bytes, not proven upload throughput,
leak or independently measured cost. No speculative cache/removal was implemented.

Snapshot:8511 instances/BLAS,17112 geometries,11544798 triangles,3552 buffers,264 textures,
2628051232 readback bytes; scene SHA
`120844AACB8B1BF6F366AF167766C667A4688EC8EC7CB61751E14363686A3E82`.
Agent inspection shows corresponding terrain/camera/lighting; final RGB MAE1.222/255 and95th
absolute channel difference5/255. Output alpha is unused zero, so previews convert RGB before
resizing. Independent temporal histories mean this is not pixel identity or dynamic acceptance.

Non-portable evidence root: `D:\Workspaces\Artifacts\MCVRSceneReplay\20260924`.
`iteration-7/MANIFEST.json`, `snapshot-files.json`, `runtime-manifest.json`, `comparison-metrics.json`,
`allocation-summary.json`, `source-verification.json`, `REPORT.md` and the two native run folders
retain input/output identities. JAR remains
`49EB9C8A862D1493203B3756AE021219C944449BD62229DC2516F2D55603E819`, extracted/loaded capture DLL
`CDE37551C50E95F654FC79C966F2DCA54038696254D20274DEB281BD5B029F4A`. Diagnostic JAR is
`D3D76F389E448B1BA2F700E665709A8EA4DF558772E7312EC381DBE9DB9C2922`, source ZIP retained.
Native replay reuses the tested free-camera core
`E06B2AD5A6C76528EC5E59DFF4622E6F45D5AA7BDEED549A057C64C022DEA29F` and matching PDB, whose
source manifest is `280EB8F902EA4A615E55213A3F597DDA30BF064957D66DD1AD257DA135DEB113`.
Those are distinct capture/replay builds: the replay incorporates the corrected SDK frame loop
and camera support; all current product sources match that snapshot under LF normalization.
Only documentation changed since it. The exact captured shader/runtime files accompany the scene.

Whole-card nvidia-smi peaks were13662MiB(game) and13454MiB(native), including other applications;
not renderer-only VRAM. Logged vram_mb is VMA aggregate allocation (including host/readback),
not device-local-only memory. This one sequential run pair does not isolate OS/driver/cache
variation, full dynamic content or transfer/build subpass GPU timings. Both clients are closed.
No Prism/production changes, Git staging/commit/push or public binary release.

## 2026-09-24: Directed investigation of three Medium-scene CPU paths

Status: investigating; optimization proposed, no product implementation in this step.
Evidence: static and reanalysis of existing runtime evidence, not a new runtime/test result.
Applies to: Radiance `b8568cafd222c8169cd6c8fd3d5771690e3425ca` and MCVR
`4778983778d53084132ce84ed0e567579ed6ed7b` with existing replay work retained.
Supersedes: no benchmark results; refines the shallow-stack attribution in the Medium record above.

The requested investigation followed block-entity discovery, auxiliary texture preparation and
entity construction through their direct Java/native consumers. The
[canonical findings and proposed gates](research/2026-09-24-medium-scene-cpu-hotspots.md) distinguish
confirmed repeated work from unmeasured costs. Re-exporting the existing JFR at depth64 proves
all100 auxiliary-path samples originate in atlas animation ticks; the earlier five-frame display
was an export default. Block-entity scanning accounts for39 list/scan leaves within40 inclusive
samples. Ordinary entity batching allocates aggregate storage each frame; Java-cached clouds
still request native rebuilding. These counts are not exclusive timing or promised speedup.

Non-portable evidence:
`D:\Workspaces\Artifacts\MCVRSceneReplay\20260924\hotspot-investigation` contains the deeper export,
grouped stacks, compact sample counts and inspected-source manifest
`48DF929BAFE1C84ED851EAAFF541538920F2F73C10F9BF3049AFB39958161A06`.
The JFR belongs to selection PID55460, not exact-timing PID36400, and contains ChatScreen samples.
Existing package/deployment identity is unchanged. No new build, test, game launch, computer-use,
Prism access or Git staging/commit/push occurred. Remaining work is measured implementation and
regression/visual acceptance if authorized; GPU-fault and license boundaries are unchanged.

## 2026-09-24: Region uploads, block-entity index and submitted cloud cache

Status: implemented; build-verified; automated-verified; runtime-observed; deployed.
Evidence: source, build, behavioral tests, bounded isolated clients and artifact hashes; no new
user visual acceptance. Applies to the dirty Radiance/MCVR pair based on
`b8568cafd222c8169cd6c8fd3d5771690e3425ca` / `4778983778d53084132ce84ed0e567579ed6ed7b`.
Supersedes the preceding investigation's no-implementation boundary for this authorized stage.
Existing standalone replay work is retained; no Git staging, commit, amend or push occurred.

### Design and scope

- `AuxiliaryTextures` prepares only the requested animation/mip rectangle. `AuxiliaryPixelRegion`
  preserves tiling, channel conversion and multibyte defaults, including missing-PBR normal data;
  every target rectangle is initialized. `EmissionRecorder` separates the original albedo offset
  from compact specular coordinates; missing specular clears the prior emission tile. Native
  staging also packs only the selected rows, validates both bounds and aligns texel offsets.
  Texture generation/queued-task ownership and in-flight upload retirement are unchanged.
- `ChunkProxy` publishes a section-presence index alongside each compiled owner. `EntityProxy`
  traverses its immutable ordered snapshot and rejects replaced compiled owners, while still
  rendering each live block entity every frame. Reset/reposition/reload/world-clear retire entries;
  separately handled global and external/Sable block entities keep their original paths. This
  is presence indexing, not camera/visibility filtering or cached animated render output.
- Native entity packing appends directly into aggregate arrays. `CloudProxy` supplies a revision
  from its existing geometry/appearance invalidation key. MCVR owns separate cloud buffers/BLAS,
  clones per-frame transforms without changing prior frames, and permits cache reuse only after
  successful queue submission. Unsubmitted builds cannot become cache hits. One retained revision
  and inactivity eviction bound the cache; actual GPU references independently keep resources alive.
  Original failure propagation is preserved if capture cleanup also fails. Ordinary entities,
  particles and animated block entities still rebuild normally; no general entity freeze or buffer
  pool was introduced, and no global GPU-idle wait was added.

### Tests and measured comparison

`gradlew build` passed, including 191 GAME tests, 10 passed/2 skipped bootstrap cases,
`verifyRuntimeResources`, `verifyDistributedJar` and `verifyMavenDevelopmentArtifact`.
`generateJniHeaders` and the native RelWithDebInfo INSTALL build passed. CTest passed 56/56,
including the existing Vulkan GPU tests. New behavior tests cover selected/tiled/missing auxiliary
pixels, multibyte defaults and guard bytes; compact emission equivalence/clearing; index ordering,
owner replacement/empty removal/world reset; native packed-row bounds and copies; real vertex
packing equivalence; submitted-only revision reuse, abandonment and in-flight cache eviction.
These seams do not alone prove the entire JNI/GPU integration; the following clients exercise it.

Two fresh copies of the same saved Medium world used identical initial files except the product
JAR, the same diagnostic JAR, 16 render / 32 simulation distance, 2560x1440, Vanilla PT,
balanced RR/model F, SHARC/jitter and Reflex, FG off. The camera/time/weather and quality remained
fixed. Both were focused throughout the measured 90-150 s interval with 8,207 native-ready sections.
No build or second game ran during that interval. Baseline PID16960 and candidate PID54140 each
ran about 162 s in world and saved all dimensions/closed native normally (exit 0, no device loss).

| Metric, 90-150 s | Baseline | Candidate |
| --- | ---: | ---: |
| Real-frame samples | 1,341 | 1,457 |
| Mean real frame, ms | 44.718 | 41.180 |
| Real FPS from mean | 22.362 | 24.284 |
| Real frame p50 / p95 / p99, ms | 44.362 / 50.123 / 55.419 | 40.834 / 46.907 / 50.104 |
| Instrumented GPU mean, ms | 14.261 | 13.433 |
| Entity geometry allocation rate, MB/s | 258.97 | 273.78 |
| VMA aggregate allocation mean, MiB (host + device) | 5,864.3 | 5,742.5 |

Observed real FPS increased 8.6%, mean frame time decreased 7.9%. This is one sequential pair,
not a per-change ablation, universal gain or long-term proof. Higher allocation MB/s remains:
more frames are produced and ordinary entity buffers are still recreated. Approximate allocation
bytes per real frame fell only 2.7%; do not claim this entire allocation path was eliminated.
VMA aggregate is not device-local-only VRAM; whole-card nvidia-smi includes other applications.
The baseline CDE37551 DLL predates dormant standalone replay/free-camera changes in the initial
dirty tree. The archived source comparison confines those changes to replay-only code/tests;
the independent replay runner was not entered in either game. Native replay's old 3.33x difference
is not this optimization's result.

Separate candidate PID46336 enabled the external `RADIANCE_HOTSPOT_REGRESSION=1` probe in an
isolated copy. It compared the actual presence index with every compiled section, added/removed
a chest through the integrated server, performed client resource reload and all-chunk rebuild,
disabled/restored clouds, then saved and closed after 110.18 s. All six index comparisons matched;
26,136 slots held 104 active sections in the final state, and ready count returned to 8,207.
The cloud path reported 7 build requests and 2,404 reuse requests across load/reload/invalidation;
these are production-path decisions, not completed-GPU timestamp counts. In 41 stable counter
intervals, region preparation was 399,594,096 bytes versus 8,701,822,892 full-source bytes (4.59%).
Counter logging was enabled only in this regression, not either timing comparison. The probe is
default-off, requires the isolated marker and is archived separately from product sources.

### Identity, deployment and remaining acceptance

Non-portable evidence root:
`D:\Workspaces\Artifacts\MCVRSceneReplay\20260924\optimization`.
`before-source.*`, `product-source.*`, `source-final.*`, build/test logs and XML, `comparison.json`,
three process folders with loaded-module records, `regression/counters-summary.json`,
`diagnostic-source.zip`, packaged shader/runtime hashes and matching `core.pdb` retain the chain.
Product source manifest SHA-256:
`C7EDE053BB8DE2597A6459D2D70820B9991B4E90B9721C6FB49F9A8DFBE29DA4`.
It includes tracked and necessary untracked sources/tests/build files, recording raw bytes and
CRLF-to-LF hashes for UTF-8 text; documentation has a separate final archive.

Final JAR: `5508403622385BD98B07B76A42C2FD2AC64A3B19D61818D8DED7ADD96C21E605`.
Built/embedded/extracted/actually loaded DLL:
`D296C930B0E65E71646695B6B7E42019570F0779DAB65310623D75571434CCA2`.
Regression/manual diagnostic JAR:
`518A1B29BAC15188550FD7CE19D06C8EDC4BC630D8BCF5DFE48E87EEE6725293`.
The preliminary 3663C6DF JAR was never run and is superseded by the final Java failure-cleanup
packaging above; its test log is not a separate gameplay result.

Only the two mod JARs were deployed to the authorized Prism instance
`E:\Minecraft\PrismLauncherDev\instances\Radiance 1.21.1-neoforge\minecraft\mods`
(non-portable); no worlds, settings or other mods changed. One previous mod pair remains under
`optimization/prism-previous` for manual-acceptance rollback, not as a permanent history backup.
All three automated clients are closed; no computer-use session was opened.

User checks still needed: missing/present PBR animation and material appearance across F3+T;
chests/signs/animated machines during placement/removal/F3+A; clouds while moving, changing view
height and changing worlds. A same-product world-switch run and visual equivalence were not
performed here. Ordinary entity pooling and per-producer/per-change attribution remain follow-ups.
The sampler mipmap comparison, existing GPU-fault uncertainty and binary-license gates remain open.

## 2026-09-24: Tracked optional Radiance Audit module and native collector

Status: implemented; build-verified; automated-verified; runtime-observed (bounded launch/close only).
Evidence: local source, Gradle/JNI/distribution gates, behavioral tests, CTest and isolated clients.
Applies to: Radiance `b8568cafd222c8169cd6c8fd3d5771690e3425ca` and MCVR
`4778983778d53084132ce84ed0e567579ed6ed7b` with all pre-existing replay/hotspot work retained.
Supersedes the ignored-only diagnostic-source/build arrangement, not earlier runtime evidence.

### Scope and design

`Modules/RadianceAudit` is a tracked-source Gradle subproject (`:radiance-audit`) and a separate
installable JAR. It uses an explicit project dependency, not an ignored `build/classes` folder.
The upstream `com.radiance` namespace, authors, icon, homepage and issue link are reused; the audit
package is `com.radiance.audit` and its unique mod ID remains `radiance_audit`. MCVR's upstream
project/library identity remains unchanged. Audit-specific wording does not claim it shipped upstream.
The outer SERVICE and nested GAME metadata share the same identity map; the original upstream icon
bytes were compared with the Audit JAR. Root distribution verification rejects embedded audit classes.

Lifecycle experiment orchestration and its test moved out of the main GAME source. Optional Audit
Mixins observe real close and explicitly armed tick probes. Actual fatal propagation, single-player
save/close order, texture retirement, native shutdown and first-error state remain product behavior.
The versioned Java observer detaches a failing sink without replacing renderer errors. Native
failure injection now also requires the explicitly armed native diagnostic capability.

The first-party optional native collector owns allocation tables, timing aggregation and report
formatting. MCVR keeps a versioned C ABI/event boundary with borrowed data and no cross-DLL STL
ownership. No observer means no allocation-statistics locks/maps and no periodic performance file.
The collector is pinned and its bounded state lives until process termination to cover late retirements.
GPU/VMA sampling only runs when requested. Reports identify host-call timing, native present calls,
VMA totals (including host allocations), per-window net growth and dropped snapshots; these are not
proof of leaks, generated FPS or total SDK/process VRAM. GPU capture request-file polling requires
an attached capture capability. Early device-fault/Aftermath hooks, GPU-safe readback primitives,
replay internals and some explicitly enabled traces remain native; their migration is not claimed.

Default installation no longer clears OpenAL overrides. Active scenario flags additionally require
an explicit JVM option and an isolated-directory marker. Passive Java categories and native capture/
performance are independently opt-in. Ledger open intents, detail length and output size are bounded;
async work survives frame boundaries and overflow is recorded as incomplete evidence.
See [module controls and boundaries](../Modules/RadianceAudit/README.md).

### Validation and corrections

Java GAME: 192 tests passed. Audit: 6 tests passed, including actual ledger capacity/async/close/write
behavior and Mixin dependency selection. Bootstrap: 10 passed, 2 skipped. The native collector's
behavior test passed; MCVR RelWithDebInfo INSTALL and CTest 57/57 passed (including GPU fixtures).
JNI generation and SERVICE/GAME package/development-artifact gates passed. No shader behavior changed.
Initial attempts exposed Java 25 launcher incompatibility (rerun under Java 21), missing test runtime
classpath, and concurrent JNI generation versus native compilation (rerun sequentially). These were
not counted as successful builds and the earlier packaged DLL was not used for final native acceptance.

The first audit-only client failed before launch because a vanilla-target Mixin referenced absent
Radiance classes. Dedicated vanilla/Radiance client Mixins and pre-transformation selection fixed it;
regression tests and a real audit-only title/normal-stop run passed (20.59 s, PID 29964). Its collector
was not loaded; a later collector-only lifetime adjustment does not extend that run into native proof.
Final native-on run PID 93720 lasted 47.44 s: title-ready, native aggregation, actual loaded DLL hashes,
UTF-16/null JNI probe, no injected fatal, 1,651 successful submissions (see machine log for exact count),
and real before/after close records. The preliminary native-on/passive runs remain separate artifact
records; passive PID 76064 stopped normally without loading the collector or creating radiance-perf.log.
No world, G1/G2 failure, GPU-loss, visual or performance-gain acceptance is implied by these menu checks.

### Identity and evidence

Non-portable retained evidence: `Radiance/run/audit-formalization-20260924/` (relative to checkout),
including per-process artifacts/start/result/loaded identities, initial failure logs, tests, source ZIP,
manifest and matching PDBs. Source manifest SHA-256:
`969E808D599392F296F316A3602976A89383F4DFC2AD9C02A365CDD6FBC6D52F` (1,427 tracked or necessary
untracked build/source inputs; exact bytes, no line-ending normalization; documentation excluded).

- Main JAR: `1FA7220739FEEA2D2730B3A34A95A55018FD302B71AE67920BD6A6E111168522`.
- Core DLL: `31A3357C8E690A29738BB5F38F6BEB6439E96025BA0DBC6B3FCB47B6A277A6B6`.
- Audit JAR: `E309B01D5BCA8B809AE86762623B41A80B99D78AEC3A28FA98385584712BB685`.
- First-party collector DLL: `6F4AA891A6B23E464078D461C6801C5050EFFD911B83216E0C61CCBD486509E1`.

Build, embedded, extracted and loaded native hashes agree for the final native-on process.
Legacy ignored diagnostic sources/build/evidence are preserved, with a compact source ZIP; the root
Gradle subproject is now the supported entry. No staging/commit/amend/push/public release. Vendor SDK
redistribution, original GPU-loss cause and prior renderer/visual acceptance boundaries remain open.

Deployment and final product-only check (same event): PID 45548 ran the final main JAR without any
Audit JAR for 36.29 s and exited 0 after a normal window-close request. No optional collector was
loaded and no radiance-perf.log was created. This is bounded startup/close evidence, not a title-screen
instrumented or world test. The final two JARs above were then deployed to the authorized Prism mods
folder (`E:\Minecraft\PrismLauncherDev\instances\Radiance 1.21.1-neoforge\minecraft\mods`, non-portable).
Only the previous main/Audit JARs were recycled; settings, worlds and other mods were untouched.
Audit's installed filename is now `RadianceAudit-0.1.5-alpha.jar`. Deployed SHA-256 values match the
manifest. Prism was not launched; manual acceptance remains pending. No computer-use session was used.

## 2026-09-24: One-shot scene replay retirement and amend preparation

Status: implemented; build-verified; automated-verified; deployed (no new gameplay acceptance).
Evidence: static, build, automated, GPU fixtures and deployment; no new Minecraft runtime evidence.
Supersedes: active maintenance of the preceding standalone capture/replay/free-camera experiment;
its historical measurements and artifact identities remain unchanged.

### User decision and source scope

The user considers the one-shot same-scene replay investigation finished. Remove the native
capture/replay/export path, buffer-address registry, BLAS capture-only fields, pipeline serialization,
standalone executable/test and Audit SceneReplayProbe. Retain the derived region-sized upload,
block-entity presence index, direct vertex packing and submitted cloud cache, together with their
regression tests. General GPU timing, explicit FG readback, fault diagnostics and the optional
Audit provider remain independent facilities.

The native sources and Java controller are recycled, not moved into a second active module.
Existing ignored source snapshots, capture images/data, tests, timing records and worlds under
`run/audit-formalization-20260924`, `run/scene-replay-20260924` and the non-portable evidence root
`D:\Workspaces\Artifacts\MCVRSceneReplay\20260924` remain historical evidence. No production
or Prism world/settings are touched.

### Repository preparation

Ignore logs at every module depth, including rotated `.log.gz`; recycle the old Audit test log
directory and run its tests in `build/test-runtime/<task>`. The preceding statement that no generated
files were visible as untracked was incorrect: 13 compressed logs were exposed (4,135 bytes), and
were also included in the old exact-byte source manifest. That old manifest is retained as produced,
not retroactively altered. The new manifest explicitly excludes runtime/log directories.

Share `mcvr.root`, CMake generator selection and `mcvr.configuration` between the core preparation
and Audit collector. Default configuration stays Release; this verification uses RelWithDebInfo.
Existing CMake caches supply the generator unless explicitly overridden. Root GAME tests retain
their established working directory because several existing tests resolve source/fixture paths.
The two mixed-EOL Java files are normalized to their existing index LF style; no whole-repository
renormalization or new commit policy is introduced.

### Verification and remaining boundaries

Evidence is collected in ignored `run/replay-retirement-20260924`. The product snapshot covers all
tracked and necessary untracked product/build/test inputs with exact byte hashes. No staging,
commit/amend/push or public binary distribution is part of this preparation. Historic performance,
world/visual acceptance, GPU-loss uncertainty and third-party binary licensing keep their existing
limits; removal and build checks do not constitute new gameplay or stability acceptance.

### Executed validation and final identity

- Root `prepareRuntime -Pmcvr.configuration=RelWithDebInfo`: passed; JNI headers, native INSTALL
  and shader/runtime installation are sequential. The existing FFX macro-redefinition warning
  remains a warning, not a new compile failure.
- Forced Java/root/module build: GAME 192/192; Audit 6/6; bootstrap 10 passed / 2 skipped.
  Native collector 1/1. The Audit build also ran separately; the final formatting-only EOF cleanup
  was followed by incremental Audit build and package checks, not claimed as another whole-suite run.
- MCVR CTest 56/56 including existing GPU fixtures. The removed 57th case was the retired replay
  contract, not a skipped or suppressed failure.
- DLL exports contain no `MCVR_ReplayMain` or replay API; the Audit provider export remains.
  Neither packaged JAR contains SceneReplayProbe. Build/install/embedded core hashes agree;
  GAME/SERVICE separation, runtime resources and Maven developer-artifact checks pass.
- Untracked-file whitespace and local documentation-link checks are included; one imported
  WorldMeshSubmissionProbe surplus EOF blank line was corrected. Mixed-EOL warnings in other
  new imported files are conversion notices, not hidden code changes or whitespace failures.

Final exact-byte product/build/test manifest: 1,417 inputs,
SHA-256 `9180BCD9DC6B599A54678A2FB7FFA1030118672ABEF3AF1A1D985FBE7A040CD5`. The pre-EOF snapshot remains separately identified;
only that non-executable final blank line differs. Evidence: `run/replay-retirement-20260924/MANIFEST.json`,
source ZIP/manifest, package/test logs, CTest XML and DLL exports. No old game result is assigned
to this rebuilt candidate.

Final Radiance JAR SHA-256: `0700C4BDB4C0727456866B3EDDE29400CEC580DC2504BFFB5250A8808FD25206`.
Final core DLL SHA-256: `ABDA7889AF78F8C6F7FF7B3B897D53619580C1D1FABFE395C36D4246BBD17FB6`.
Final Audit JAR SHA-256: `72343C44391BAD39B5CA527072A0DCC513DE9AC90E0335E5DEF02A78C88A514D`.
The collector DLL is unchanged at `6F4AA891A6B23E464078D461C6801C5050EFFD911B83216E0C61CCBD486509E1`.

The two JARs were deployed to the authorized Prism `Radiance 1.21.1-neoforge/minecraft/mods`
(non-portable instance root documented above); replaced JARs went to the Recycle Bin. The game
was not started and settings/worlds were not changed. Deployment hashes match the final artifacts.
No source is staged or committed, and the pair remains on its existing HEADs. This prepares a
source checkpoint; specialist visual/runtime acceptance and public binary licensing stay open.

## 2026-09-24: Manual feedback and source checkpoint after replay retirement

Status: runtime-observed; limited user visual acceptance; source-checkpoint preparation authorized.
Evidence: user feedback, client logs, deployment hashes and unchanged product snapshot.
Supersedes: the preceding retirement entry's no-new-gameplay-evidence boundary only for this run.

The user reports no issue encountered in actual play and authorizes amend/source synchronization.
The authorized Prism log spans 08:44:56--08:56:25 (+0800), Audit PID 54064. It records entering
`新的世界`, then `Newport City`, game-mode changes, F3+G, saves for all three dimensions and
client stopping; the Audit shutdown ledger closed with no dropped/incomplete output. This does
not prove full drawing coverage, and no specific Ponder/FG/PBR stress acceptance was provided.

Installed main/Audit hashes match the preceding final deployment manifest and their file times
precede launch: Radiance `0700C4BDB4C0727456866B3EDDE29400CEC580DC2504BFFB5250A8808FD25206`,
Audit `72343C44391BAD39B5CA527072A0DCC513DE9AC90E0335E5DEF02A78C88A514D`.
The process has exited; a new per-process loaded-DLL hash, native-close completion assertion and
exit status were not independently captured. Do not promote Java stopping/audit shutdown into
proof of every native close stage. No device-lost marker was found in these client logs.

The same logs contain unsupported Veil layout/uniform messages and saved-map-data errors
(`Invalid map dimension: null`). These are recorded limitations, not a claim of an error-free log
or a proven regression caused by this change. No world data was edited during this checkpoint.
Raw logs are retained at the non-portable maintenance evidence path
`D:\Workspaces\Artifacts\RadianceCommitMaintenance\20260924-audit-retirement`.

Product/build/test input hashes still match `run/replay-retirement-20260924/source-manifest.json`
(SHA-256 `9180BCD9DC6B599A54678A2FB7FFA1030118672ABEF3AF1A1D985FBE7A040CD5`). Only maintenance
records are added now; previous build/test results retain their original scopes and are not rerun.
GPU root cause, specialist visual/long-duration testing and public binary licensing remain open.

Paired native source checkpoint: MCVR `b70d2a149fd86f8a03c81dc7b9c8bdcc5f7fae57`. The single upstream-parent Initial port and
original metadata are retained with a new verified SSH signature; no own-SHA backfill is used.

## 2026-09-24: Glow lichen opposite-face UV investigation

Status: investigating; specific upstream UV-lock mechanism automated/runtime-observed.
Evidence: Minecraft 1.21.1 / NeoForge 21.1.251 sources, upstream-baker tests, actual baked/PBR
vertices, GPU readbacks and user symptom clarification. No product fix or visual acceptance.
Applies to Radiance `43568d910d7af088f3557ebf6bb5f638d729e403` and MCVR
`b70d2a149fd86f8a03c81dc7b9c8bdcc5f7fae57`; only the optional Audit probe/tests and records change.
Supersedes the earlier missing-pixel/rotation hypotheses with the user clarification below.

### Symptom, mechanism and rejected explanations

The user first described missing pixels at close overhead viewing, including ordinary and Sable
blocks, and identified the black-concrete diagnostic screenshot as showing the issue. On seeing
an aligned original/final comparison, the user corrected the description: the visible front
pattern and PT-participating surface differ by a 180-degree rotation, rather than front texels
being absent. Preserve this correction instead of treating either earlier description as proof.

The vanilla resource model is a zero-thickness plane at Z=0.1/16, with NORTH and SOUTH quads.
The blockstate applies X=90 (floor) or X=270 (ceiling), with UV lock. Actual baked vertices and
`FaceBakery.recomputeUVs` / `BlockMath.getUVLockTransform` agree: at the **same physical point**,
horizontal opposite faces use `(u,v)` versus `(1-u,1-v)` in normalized sprite space. The wall
pair agrees. All three PBR transfers retain the eight source vertices/UVs unchanged; native
packing retains those values, and primary/secondary hit shaders interpolate the selected face's
UV. This difference already exists at the source-model bake, before Vulkan/PT translation.

For the original 16x16 alpha mask, each face has 105 opaque texels. A half-turn leaves only 48
opaque texels coincident: 57 front-only and 57 back-only locations. The camera-facing pattern
can therefore be intact while rays arriving from the support side see a different emitting/
occluding pattern. This is a thin-surface material-coordinate inconsistency exposed by PT, not
evidence of geometry deletion or camera-dependent culling. Ordinary and Sable scenes share the
model input; this run exercised ordinary sections only, not a new Sable runtime acceptance.

Do not rotate the complete texture, disable culling, remove the reverse face, or change emission
strength as a speculative repair. Preserve the outward visible appearance. A prospective scoped
model correction must align the opposite surface while avoiding a global rewrite of deliberate
two-sided materials. Product correction and ground-lighting/reflection acceptance remain open.

### Actual validation and evidence identity

Non-portable evidence: repository `run/lichen-20260924-black/RESULTS.json`, `inputs.json`,
`loaded.json`, `result.json`, `client/lichen-models`, `client/radiance-world-captures` and
`lichen-comparison.png`. Main JAR remains
`0700C4BDB4C0727456866B3EDDE29400CEC580DC2504BFFB5250A8808FD25206`; loaded core is
`ABDA7889AF78F8C6F7FF7B3B897D53619580C1D1FABFE395C36D4246BBD17FB6`; isolated Audit JAR is
`E3F9294C34B1E76D05E2208694AEED8924F6DCAC23D2B816D809A60F12A70ED8`.

PID80096: 100.688 seconds including startup; exit 0 after the probe's normal stop. Saved all
three dimensions. Vanilla PT, RR preset D/balanced, jitter/SHARC enabled, FG off, Reflex on,
1280x720 output / 742x418 tracing, no external resource pack, six-chunk view distance, emission
collection off. The test uses a copy of the repository lifecycle test world, never Prism data.
Three views produced 8 COMPLETE captures each. Floor primary depth matches the original alpha
at all 256 texel centers per frame (105 opaque), and 54,880 interior pixel samples across eight
frames have zero mismatches; a 0.03-texel boundary band is excluded for float-boundary ambiguity.
The final screenshot's opaque texel centers are also present. This does not claim subpixel edge
or denoiser equivalence, every camera angle, indirect-light radiometry, or long-term stability.

A first stone-support run (PID88580, exit 0) ended before ceiling capture; preserve its floor/wall
evidence without calling it a complete sequence. The user requested a repeat; the second launch
(PID24788) was stopped by the agent through CloseMainWindow to adopt the requested black support
(exit -1 during startup, not claimed as successful acceptance). No device-lost diagnosis is
inferred from that shutdown status. The complete black-support run above is the authoritative
three-view case. An initial Gradle argument parsing failure is retained before successful build.

Audit JAR compilation passed. Existing Audit tests passed before the new evidence test; then
three actual upstream-baker evidence tests passed (wall agreement, floor/ceiling half-turn, and
rotation-without-UV-lock control). These tests document the source behavior, not a product repair.
The final full Audit test run passed all nine tests with no failures or skips; retained output is
`run/lichen-20260924-black/audit-tests-final.log`.
No native/source shader changed, so no unrelated native rebuild was performed. Diagnostics stay
behind the existing JVM-plus-isolated-marker gate and `RADIANCE_LICHEN_PROBE=1`.

### Deployment and remaining boundary

No product artifact or Prism setting/world was changed. The new Audit JAR was used only in the
repository diagnostic instance. No staging, commit, amend, push, or public binary distribution.
Existing GPU-fault uncertainty and licensing conditions remain independent and open.

## 2026-09-24: Glow lichen accepted as normal upstream behavior

Status: reported symptom closed by user acceptance; no product correction.
Evidence: preceding source/bake/runtime investigation and explicit user decision, "record as normal".
Applies to the unchanged Radiance/MCVR product pair and artifacts identified in the
[investigation](#2026-09-24-glow-lichen-opposite-face-uv-investigation).
Supersedes its pending UV-correction proposal and symptom-specific lighting/reflection acceptance.

After clarification that vanilla uses two coincident, oppositely facing single-sided quads with
one shared texture and separate UVs, the user accepts the observed horizontal-face half-turn as
normal inherited model behavior. Preserve both quads, their authored/baked UVs, culling and the
existing PT material/emission behavior. Do not add a lichen-specific rotation or a general
opposite-face UV normalization for this report.

This is acceptance of the explained behavior, not a claim that the thin-sheet model is a fully
physical description or that all materials/indirect-light paths have passed new visual tests.
The prior diagnostic results and proposed alternatives remain historical evidence; no fresh
client run, build or product deployment is required for this documentation-only disposition.
The optional default-off Audit probe/tests and all machine evidence are retained. No staging,
commit, amend or push was performed; unrelated runtime and licensing limits remain unchanged.

Paired native source checkpoint for this Audit-probe and documentation amendment: MCVR
`ead8d47d80ad2bc9cf81740c29f0ec230b61f92f`. Native product content is unchanged; only its
investigation/acceptance ledger was amended with the original metadata and a new SSH signature.

## 2026-09-24: Diagram rendering-preference re-audit corrects incomplete scope

Status: investigating; static findings, no product repair or new runtime/visual acceptance.
Applies to Radiance `6e9b97a53049fad833e673da647ac517efde5fe3` and MCVR
`ead8d47d80ad2bc9cf81740c29f0ec230b61f92f` (both clean before this documentation change).
Supersedes the assessment that occupancy and cadence were the only remaining diagram differences;
does not rewrite the earlier bounded shader/GPU tests or user evidence.

The user identified omitted spring/rope preferences and requested a new investigation. This
re-audit follows config/selection, renderer eligibility/material/state, layer submission,
lightmap upload/native consumption, postprocessing, placement/cache and cleanup. Its
[canonical findings and coverage](research/RASTER_PARITY_AND_PONDER_RETIREMENT.md#2026-09-24-diagram-semantic-re-audit-and-correction)
separate eight static contract gaps (including conditional inputs and failure paths) from open
runtime questions. In particular, Sampler2 is bound, but repeated image mutation across deferred
draws loses the original stage lightmap contents. Ownership/attachment rules are distinct from
those resource defects.

Original Simulated 1.3.2/Sable 2.0.5 and NeoForge 21.1.251 buffer/compiler sources were inspected.
New ignored evidence: `build/research/20260924-diagram-reaudit/EVIDENCE.json` and referenced
sources; original assets/bytecode remain in the prior raster evidence directory. No product,
tests, dependencies, settings or deployed artifacts changed; no game, build or GPU test ran.
Documentation whitespace/link checks are not renderer validation.

Remaining acceptance follows the revised roadmap: actual-entry stage lightmaps/order, owner/BE
sources, placement/coverage/cache, overrides/extensions and failure cleanup, followed by matched
original raster comparison. Preserve pixelation removal, archived Ponder PT, world PT policies
and historical GPU/redistribution limits. No staging, commit/amend or push.

## 2026-09-24: Original diagram producer restored with physical-resolution presentation

Status: implemented; build-verified; automated-verified; bounded runtime-observed; deployed.
Evidence: static, build, automated, GL/Vulkan attachment readback, runtime, deployment.
Applies to the uncommitted worktree above Radiance `6e9b97a53049fad833e673da647ac517efde5fe3`
and unchanged MCVR product `ead8d47d80ad2bc9cf81740c29f0ec230b61f92f`.
Supersedes the preceding re-audit's implementation status, not its historical findings/tests.
Remaining acceptance: mechanism preferences, note details, final-image limits and user visuals.

### Requested behavior and implementation

User decision: cancel the original approximately 12-Hz refresh cap and low-resolution enlargement;
preserve paper colors, outlines and original fade. The original palette/dither pipeline remains,
sampled at physical resolution rather than enlarging a coarse pixel grid. No Ponder PT reactivation.

The custom diagram renderer is replaced by a wrapper around original Simulated draw/group/post/GUI
methods. Their chain membership, spring/rope controller/owner/attachment/virtual/hover rules, entity
and force selection now remain the active producers. `DiagramLightmaps` retains separate original
stage contents. `DiagramSectionMeshes` uses the original raster section compiler/extensions with
generation/origin/reload cache invalidation and exception cleanup. Main/note targets use UI physical
dimensions; fractional placement and clipping stay in original GUI composition. Greeble placement
uses final alpha at its original initialization point, with one readback for that pass. Original
resource-resolved Veil post/palette/dither replace the former hardcoded native post for this path.

Real image comparison additionally exposed incorrect diagram-camera translucent re-sorting and
global AO suppression. Published `RasterCompiledSection` sorting is retained, while raster previews
honor original AO settings through `RasterPreviewScope`; world PT still suppresses raster AO.
No native shader, descriptor, material or lifecycle product code changed. See
[D1-D8 dispositions and measured limits](research/RASTER_PARITY_AND_PONDER_RETIREMENT.md#implementation-and-measured-corrections).

### Validation and exact evidence

Executed `:test :bootstrapTest :radiance-audit:test :distributedJar :verifyRuntimeResources
:verifyDistributedJar`: GAME **197 passed**, bootstrap **10 passed / 2 skipped**, Audit **9 passed**.
The packaged original diagram post also compiles through the real shader translation test. Earlier
test attempts exposed an adapter-boundary import and an unbootstrapped MC static initializer; the
former was corrected, and sorting-generation behavior moved into the real-client diagnostic
assertion. These earlier failures remain in build logs, not counted as successful executions.
Audit jar/test reran after the final default-off chain/entity capture addition. Native CTest/build
was not rerun because native product content is unchanged.

Non-portable repository evidence: `run/diagram-semantics-20260924/evidence/`, including gate logs,
test XML, `SOURCE_MANIFEST-handoff.json`, source ZIPs, `PIXEL_COMPARISON-members.json`, and
`DEPLOYMENT.json`. Manifest includes tracked and necessary untracked source/config/tests, raw and
CRLF-to-LF hashes, excluding docs and runtime binaries. Radiance file-list hash:
`6369b3d0e51ef0897fd0adf8644979ae48546d691763d2fcc9aa6cfbc769052a` (899 files);
MCVR: `cb4364f6fb213f2fa1efb59e287272ebe208fbcf0866c91af158aa07171b3065` (535 files).
Only the Audit probe changed since the preceding final product source snapshot.

Original GL `gl-final` (PID 21844, 67.433 s) and Vulkan `vk-members` (PID 75476, 128.106 s) use
the same 71-block fixture: iron, leaves, cobwebs, overlapping colored glass, slab and chest. Raw
RGBA is identical for initial/rotated/note-activation captures. Final alpha is identical; final
paper RGB still differs at 19/81 edge pixels, maximum channel difference 59. Depth precision and
the original strict outline comparisons are a candidate explanation, not proved causation.
At GUI scale 3, the actual 767x576 targets are identical before/after resource reload. Sorting
generation assertions passed; all dimensions saved and the final client exited normally.

Earlier final-product runs contain small time-varying extra regions; their cause remains unknown.
The final chain/entity capture run did not reproduce them. They are preserved as unmatched evidence,
not retrospectively dismissed. Unrelated GPU workload was not controlled; elapsed times are not a
performance benchmark. A blank note activation does not verify a populated note's slide/scope/fade.

### Deployment and remaining acceptance

Copied only Radiance and Audit into the authorized (non-portable) Prism instance
`E:\Minecraft\PrismLauncherDev\instances\Radiance 1.21.1-neoforge\minecraft\mods`:

- Radiance JAR: `2A9F6A7BA77FAB33E4FEDE58C00615A26E253DC06A25A535FD7E06EE53F07EFA`.
- Audit JAR: `40560A18AA84576DB16485896ACB8BE07636CE292321D4F5C409767168E3F071`.
- Unchanged embedded/extracted/actually loaded core DLL:
  `ABDA7889AF78F8C6F7FF7B3B897D53619580C1D1FABFE395C36D4246BBD17FB6`.

Prism has not been launched by this task. Its worlds/settings/other mods were untouched. The probe
requires the existing explicit experiment/capture opt-in and isolated marker plus its environment
flag; it cannot assemble fixtures or change options in normal Prism use. The prior Radiance JAR is
already retained in `run/lichen-20260924-black/client/mods/Radiance.jar`; one previous Audit JAR is
retained under `evidence/pre-deployment/` until manual acceptance. These are rollback artifacts,
separate from unique runtime evidence.

User acceptance: spring/rope owner/controller inside versus outside the selected chain; attached,
virtual and hover states; cutouts/glass overlap while rotating; populated note sliding/cropping and
paper fade; GUI scales and F3+T. Arbitrary resource overrides, actual extension geometry, alternate
backends and live renderer-exception recovery remain unexecuted. No full raster equivalence,
long-term stability, GPU-loss cause or public binary licensing claim. No Git staging or history
change; all required new source and tests remain present as untracked candidates.

## 2026-09-24: Spring and plunger observations retained for later repair

Status: deferred by user; manual symptom reports, no cause confirmed.
The user reports spring blackening/apparent inner texture exposure under twisting, diagram spring
texture/cutout loss to black, and a displaced diagram rope endpoint while the attached plunger itself
renders. [S1-S3](research/RASTER_PARITY_AND_PONDER_RETIREMENT.md#2026-09-24-deferred-spring-and-plunger-visual-reports)
own the detailed observations and acceptance criteria; the roadmap links to those deferred cases.

These reports follow the preceding deployment but its actual running identity was not rechecked.
They do not supersede the bounded block-material fixture evidence or establish mechanism parity.
No product/native code, tests, settings or deployed files changed in this record-only turn; no
client was launched and no Git staging, commit/amend or push occurred. Existing worktree and
runtime evidence remain intact.

## 2026-09-24: Optional frame-stage profiler with correlated Java, native and GPU samples

Status: implemented; automated-verified; bounded runtime-observed; deployed as recorded below.
Evidence: static, build, automated, GPU/client observation, artifact identity; no new visual acceptance.
Applies to Radiance worktree above `6e9b97a53049fad833e673da647ac517efde5fe3` and MCVR worktree above
`ead8d47d80ad2bc9cf81740c29f0ec230b61f92f`. Existing uncommitted diagram corrections are preserved.

### Requested behavior and implementation

The user requested attribution of rendered-frame duration. The formal optional Audit mod now provides
`/radianceaudit profile start 30` and `stop`, disabled until explicitly requested (or the documented
bounded automation property). Exception-safe Java method scopes partition render-thread wall time
into exclusive stages. Nested JNI/native CPU scopes separate conversion/copy, packing, command
recording, waits, submission/present and pacing. Worker activity is separate. An independent optional
native profile ABI correlates these with monotonic Java frame IDs; clocks are not subtracted across
JNI. GPU queries have per-context ownership, reuse only after the existing fence, availability reads
without a new wait, explicit readback invalidation and bounded slots. Pipeline modules and main
command buffers are timed separately. Acquisition at Java frame end prepares the next frame, so GPU
ownership is assigned at submission, not at acquisition.

All aggregation, raw CSV, quantiles, human-readable reports, controls and bounded asynchronous writing
live in Audit. MCVR contains the necessary inactive observer/timestamp hooks only. No per-vertex timer,
OpenGL context, permanent idle, renderer optimization, settings override or vendor DLL was introduced.
The native sink remains process-lived/pinned. Queue/slot/output/sample limits and measurement caveats
are documented in [Audit's profiling guide](../Modules/RadianceAudit/README.md#frame-profiling).

### Tests and actual observation

- `:radiance-audit:check`: 15 JUnit tests, zero failures; two collector CTests passed. Six new Java
  tests cover nesting/self sums, exception/duplicate close, recursive stages, report arithmetic,
  backpressure, and late prior-capture GPU samples. Native collector concurrency/drop bounds tested.
- MCVR RelWithDebInfo full build/install and CTest **58/58 passed**. New production-helper tests cover
  nested/worker/exception scopes and query allocation/reuse, unavailable reads, no WAIT flag and
  partial-frame invalidation. Existing GPU fixtures passed; these remain distinct from client evidence.
- `verifyDistributedJar` and `verifyAuditJar` passed. Product GAME/bootstrap sources were unchanged
  by this profiling batch; their earlier unit results were not rerun or relabeled as new results.
- Isolated cloned `Test` world, Create 6.0.10 + Sable 2.0.5 + Aeronautics 1.3.2, NeoForge 21.1.251,
  1280x720, six-chunk view, DLSS RR balanced/preset pipeline, Reflex 1, FG off; settings preserved in
  the manifest. PID 99568: 12-second delay, 30-second capture, **2,124 frames**, zero queue/output
  drops, 50-second world observation then normal save/exit (process 85.35 s, exit 0).
- Its frame-body wall mean/p50/p95/p99: **13.956/12.817/18.149/20.500 ms**. Exclusive shares:
  submit/present 34.68%, world-render remainder 19.37%, geometry marshal 17.50%, entities 11.00%,
  tick 4.67%, frame remainder 4.13%, auxiliary textures 3.52%, acquire 2.38%; other stages smaller.
  GPU main-queue mean **3.742 ms**, with world-module intervals PT 1.943 and DLSS 1.319 ms.
  These overlap CPU and cannot be added to it. Native PT-module CPU time means recording/preparation,
  not GPU tracing. Entity capture is not isolated vertex arithmetic. These are scene observations,
  not a controlled speedup, universal bottleneck or instrumentation-overhead estimate.
- PID 58284: fresh copy, capture at world entry for 15 s, **930 frames**, zero drops; 1,519 Java
  worker compilation calls and 1,189 native chunk-copy samples verify the background route. Normal
  world save/exit after 22 s in world (process 56.99 s, exit 0). Worker elapsed sums overlap frames.
  All focus flags in both captures were true and SDK generated-frame counters zero.

Evidence (ignored, retained): `run/frame-profile-20260924/{smoke,loading,evidence}/`.
`evidence/MANIFEST.json` identifies every tracked/nonignored untracked source, native dependencies,
raw/UTF-8-LF hashes, source ZIPs, commands, settings, mod hashes, matched PDB and loaded DLLs. Source
file-list hashes, before this documentation entry: Radiance
`288A3B79939933A175C09D7973737AB5B76BD4BB1A135E86E01F1840EFF775C4`, MCVR
`B8C6BC9148EA72A5711A8EA0F94370B1812EAF267AE50DD8CCB0F7E68F3E1FE1`.
These snapshots pin the first tested pair. The device-error propagation follow-up below has its own
final DLL and runtime evidence; the first pair is not silently relabeled. Prior evidence was not removed.

### Initial profiling artifact and remaining acceptance

- Radiance JAR: `BBC7F6A37DB00F4B3EC76FD086BD55323D3FF1B5D5982672FA9DEE99FE155ACD`.
- Audit JAR: `D03E8B1D69C54DDD9626820C5F26F78968FAD8CB28963668B3E335CE66079DDC`.
- core.dll: `8F7772C3547A9B800C13D210474B30DC1ECD0250F210DB191459DC079380098A`.

Build, installed resource, embedded DLL, extracted runtime and both actual loaded paths agree.
This initial profiling pair was used only in the two isolated cases above. Manual delivery uses
the final follow-up pair below; no worlds/settings/other mods were changed.

The user will prepare a slow **eight-chunk** scene in Prism. Capture standing still and normal movement
separately with unchanged settings; user input/visual acceptance and representative bottleneck results
remain pending. GPU query completion windows do not measure separate chunk queues, archived UI-PT
commands, or SDK-internal FG submissions. Profiling overhead has not been isolated by a matched A/B;
Windows ThreadMXBean per-frame quantiles are coarse. Fault root cause, rendering defects, diagram
visual issues and redistribution licensing keep their independent preceding states. No Git staging,
commit/amend, push, tag, or public binary release occurred.

### Same-day follow-up: propagate real device errors from optional queries; final delivery

The query observer must not classify a real `VK_ERROR_DEVICE_LOST` as an ordinary unavailable
sample. `GpuFrame` now preserves its result; Framework passes this result through the existing
fatal/device-close boundary if query creation or reading detects device loss first. Other optional
query failures remain explicitly missing samples. A substitute-only regression verifies preservation
of `VK_ERROR_DEVICE_LOST`; no real device-loss injection or GPU reset was performed.

After this limited change: matched RelWithDebInfo rebuild/install and `verifyDistributedJar` passed;
`frame-profile`, `gpu-profile`, `audit-sink`, and `failure-state-contract` CTests **4/4 passed**.
The preceding 58-test run belongs to the initial pair, not a second full rerun. Audit Java/collector
code and its 15+2 passing tests/artifact were unchanged. The subsequent locked-invalidation
follow-up below has its own delivered identity; these results remain pinned to this candidate.

PID **86080** used the final pair without experiment opt-in or an isolated experiment marker:
ordinary late attachment logged native observer **flags=0**, profile `native=true`. Automatic
read-only profiling ran 10 seconds after a two-second world delay: **680 frames**, no writer/native/
output-cap drops, real GPU samples. The external case runner requested normal window close only
after the report finished; all dimensions saved and process exited 0 after **46.60 seconds**. This
verifies that passive installation can attach the profiler without enabling world-mutating probes.
It does not replace the user's actual slow-scene sampling or dynamic visual acceptance.

Final paired artifacts, deployed only to the authorized Prism `minecraft/mods`:

- Radiance JAR: `957B30FEA9CAC254987E225D489226BD0817C9B941974BFB81F27C3E75BDBA7D`.
- Audit JAR: `D03E8B1D69C54DDD9626820C5F26F78968FAD8CB28963668B3E335CE66079DDC` (unchanged).
- core.dll: `5D4E0AD4C194F9DF8DCBF6B6F3EF33EE82FE2DD4039013AA4FBAD51105939915`.

`run/frame-profile-20260924/final/` retains that distinct run. `evidence/final/MANIFEST.json`, source
ZIPs, `core.pdb`, build/test logs and `DEPLOYMENT.json` retain the final chain and copied-JAR hashes.
DLL build/installed/embedded/extracted/actual-load identities match; the loaded first-party collector
also matches its extracted hash. Normalized source file-list hashes before this appended follow-up:
Radiance `17062461567BA4CA84DF89C5CCA38F2AD378FA7FA4EC36965280CA7F27C6303C`; MCVR
`B5E9268B13DF6EC1D2D2010B2C7C506E0338C0B650E93392DE92D6D9B7E5227A`.
The first pair and its reports remain historical evidence, not deleted or silently reassigned.

### Final delivery identity after locking the readback invalidation hook

The optional readback-profile invalidation was moved under the existing recreation mutex, after
current-context validation; the observer must not add an unprotected context access. This is the
last native source adjustment. Incremental RelWithDebInfo install/package verification passed;
profile/query/audit/failure/readback contract CTests **5/5 passed**. Audit JAR remained unchanged.

Final delivery, superseding the two preceding candidate Radiance/core identities:

- Radiance JAR: `9C2C343B1B6E9E0D482C9C558EBCF5B7B380FA4932D9DB83EFAB53EEE25CBCA3`.
- Audit JAR: `D03E8B1D69C54DDD9626820C5F26F78968FAD8CB28963668B3E335CE66079DDC`.
- core.dll: `1913C869934F63399EDF17F41D25A7FA67DAC60CEA917ABC403E56B1430A832B`.

PID **79228**, no experiment opt-in, captured **685 frames / 10 s**, zero writer/native/output-cap
drops, then normal window close saved all dimensions and exited 0 (process 46.65 s). Build, embedded,
extracted and loaded DLL identity agree. No new visual, device-loss or broad stability claim.

`run/frame-profile-20260924/{delivery,evidence/delivery}/` retains the final case, source ZIPs,
manifest, matching PDB, five-test/build logs and verified two-JAR Prism deployment. Source-list hashes
before this documentation append: Radiance `4EB8AC704792C1225D9612AB961AFEBD9DA78A08CDE8C3363ED58E707AB48941`;
MCVR `D760F21F5A728A748DB217D7F5E076F0658F61635819E01B5445BE9D621943EC`.
Only these final paired JARs are the current manual handoff. No further product changes, staging,
commit/amend or push occurred. The user's eight-chunk scene is still pending.

## 2026-09-24: Static Prism profile and detailed preparation timing

Status: representative static runtime-observed; finer diagnostic stages implemented, build-verified,
automated-verified and isolated-runtime-observed. No rendering optimization or GPU migration yet.
Evidence: user-operated capture, raw frame-correlated CSV, source tracing, tests and paired loading.
Applies to worktrees above Radiance `6e9b97a53049fad833e673da647ac517efde5fe3` and MCVR
`ead8d47d80ad2bc9cf81740c29f0ec230b61f92f`; all preceding diagram/profiling work is preserved.

### Representative observation and direction

User reported a static capture, PID 73276, 2026-09-24 13:05:16-13:05:46 +08. Its 1,381 frames were
2560x1440/eight chunks, all focused, no failed frames and SDK-generated FPS always zero. The first
705 frames had no screen; the remaining 676 had ChatScreen. Their frame-wall means (21.47/21.32 ms)
were close; do not describe the whole sample as an invariant HUD-only view. Recorded wall mean was
21.398 ms, frame-start interval mean 21.739 ms (about 46 real FPS), p95/p99 25.512/27.693 ms using
linear interpolation over raw intervals. GPU main-queue mean was 11.072 ms across 1,379 available
frames; the absent tail is not zero. No writer/native/output-cap loss was reported.

Host self costs: submission/recording 8.81 ms (including PT-module CPU 6.82 ms), geometry marshaling
4.21 ms, world-other 3.03 ms, block entities 1.45 ms and entities 1.22 ms. GPU PT/DLSS intervals were
8.33/2.15 ms, overlapping host work. Thirty-six background section compilations totaled 32.23 ms;
frame-fence wait averaged 0.0015 ms. These support a host preparation bottleneck in this capture,
not an all-world conclusion or proof that pure vertex transforms dominate. Config files last written
before capture select DLSS balanced, FG off, Reflex mode 2; per-frame telemetry confirms FG/focus/size.

Original deployed Radiance JAR `9C2C343B1B6E9E0D482C9C558EBCF5B7B380FA4932D9DB83EFAB53EEE25CBCA3`,
Audit `D03E8B1D69C54DDD9626820C5F26F78968FAD8CB28963668B3E335CE66079DDC`, and actually loaded core
`1913C869934F63399EDF17F41D25A7FA67DAC60CEA917ABC403E56B1430A832B` matched the prior delivery.
The raw capture, settings and log copy are retained at `run/frame-profile-detail-20260924/evidence/prism-static-baseline/`.
Its original non-portable location is the authorized Prism game directory's
`radiance-audit/profiles/2026-09-24T05-05-16.685276800Z-pid73276/`.

User direction is to investigate moving suitable CPU computations to GPU. The immediate change is
measurement only: distinguish arithmetic/packing from scene management, driver recording, allocation
and waits before choosing migration targets. GPU time, memory and real frame distributions must be
measured after any future offload; it must preserve current material and lifetime contracts.

### Detailed observer implementation and checks

Audit wrappers split LevelRenderer from its outer GameRenderer, picking/world uniforms, level/light
setup, Flywheel, Sable, NeoForge callbacks, clouds, texture mapping and world-mesh closure. Logical
server/worker calls cannot become render-owner spans. Nested geometry durations remain exclusive.
MCVR's optional observer splits PT metadata/history, locks, BLAS/TLAS command preparation, SBT,
descriptor binding, allocation/copy/upload and pass recording. No GPU commands, scene selection or
production rendering semantics were intentionally changed. No per-vertex timer was introduced.
The report now shows native CPU ms per recorded Java frame separately from per-invocation means.

- Audit **17 Java tests + 2 native collector tests passed**. New behavior tests cover nested world
  stages and several native invocations in one frame without counting concurrent worker time.
- MCVR RelWithDebInfo build/install and **3/3 targeted CTests** passed; phase scopes exercise nesting,
  failure unwinding and inactive observation. Full historical CTest/GAME counts are not new results.
- Paired distribution verification passed. Initial diagnostic candidate PID 1236 captured 634 frames
  and exited normally; its Audit JAR was `32FD5DCD0D1DF0026D391188E956DDA283AE35E2D5CCDCFF00741A628D566992`.
  The final Audit adds outer/inner world attribution and was separately exercised by PID 51976:
  **633 frames / 10 s**, zero drops, all new Java/native scope families observed, all dimensions saved,
  process exit 0 after 70.14 s including startup. Both used the same final core.dll below.
- These 1280x720/six-chunk isolated runs prove capture plumbing, not an improvement over the user's
  eight-chunk scene. Instrumentation overhead lacks a matched on/off measurement. The original
  GPU-fault, visual, spring/diagram and public DLL licensing boundaries remain unchanged.

### Final identity and manual handoff

Final matched Radiance JAR: `0BC8AA0BEE31E7927B51650F1D41B81B302137F45D4038D1CED7CA17122C84DE`.
Final Audit JAR: `049754B8250437B09641DCFA706AB489D2798047EBEE4920E2664E181877F246`.
core.dll: `3A09E358A5BE8C7636469D29ECAC17748A7B6612469A31CAE7046DC3F5D69025`, matching build,
installed resource, outer JAR, extracted and loaded isolated-client module.

Evidence and complete tracked/nonignored source snapshots, build inputs and matching PDB are indexed
by `run/frame-profile-detail-20260924/evidence/MANIFEST.json`; final runtime is in `final/`.
Two-JAR-only deployment to the authorized Prism instance is recorded by `evidence/DEPLOYMENT.json`.
No world/settings changes, computer-use, staging, commit, amend or push are part of this handoff.
User next action: start Prism, keep the same scene/view/settings, let loading settle, run
`/radianceaudit profile start 30`, close chat and remain still/focused. Normal movement can be captured
separately afterward. The agent reads outputs and decides what the finer measurements support.

## 2026-09-24: Sixteen-chunk stationary profile identifies recurring host scene work

Status: runtime-observed; investigating optimization targets. Evidence: raw correlated timings,
loaded artifact identity and direct source tracing; no product change or new build/test run.
Applies to the detailed profiler delivery above; the deployed Radiance/Audit/core hashes are unchanged.

User PID 102228 captured 478 frames over 30 seconds, 13:35:13-13:35:43 +08, at **3840x2054,
16 chunks**. All frames were focused, in-world and non-failed; SDK-generated FPS was zero. Only
25 frames had no screen; 453 had ChatScreen. Settings saved before capture select DLSS balanced,
FG off, Reflex 2, four bounces, SHARC/jitter enabled. F3+A preceded capture by approximately 43 s;
no background section compilation/worker work was recorded during the sample. No writer/native/
output-cap loss was reported. This differs from the eight-chunk sample in resolution, scene,
observer detail and screen distribution; it is not a controlled scaling or observer-overhead test.

Frame-start intervals averaged **62.761 ms** (about 15.93 real FPS), p50/p95/p99
62.251/68.599/72.738 ms, linearly interpolated from 477 positive raw intervals. Measured runTick
wall averaged 62.026 ms. Main-queue GPU completion intervals averaged **20.004 ms** across 476
available GPU frames, including PT 14.021 ms and DLSS 4.335 ms. CPU and GPU overlap; these figures
cannot be summed or converted into a guaranteed post-optimization FPS.

The exclusive Java partition attributes 24.110 ms to submission/recording, 14.109 ms to geometry
marshaling, 6.640 ms to world orchestration, 6.424 ms to entities and 2.112 ms to block entities.
Nested native measurements identify chunk metadata **10.441 ms**, BLAS command recording 2.935 ms,
entity metadata 1.739 ms, native conversion/copy/enqueue **4.831 ms**, and entity batch construction
**5.866 ms inclusive**, including vertex packing **3.172 ms**. PT host work totals 18.184 ms
inclusive, already within submission. Chunk metadata has two consecutive scopes per frame, not
evidence of two full scans. Frame-fence waits average 0.0017 ms and measured locks about 0.0005 ms;
waiting and ongoing section compilation do not explain the dominant cost in this sample.

Attribution correction to the previous conversational interpretation: `Entities::queueBuild` is
inside Java geometry marshaling, but `EntityProxy.build()` runs from GameRenderer's world-render
tail. Its native batch construction and vertex packing belong to **WORLD_RENDER_OTHER**, not
GEOMETRY_MARSHAL. Their nested durations must not be counted in both categories. The remaining
marshaling cost is not yet isolated into pure Java arithmetic, allocation and JNI transfer.

Static follow-up found `shaderMaterialFlags` calling `faces::uniform(model)` for each geometry;
uniform single-sided models can repeatedly scan the same material vector. This is confirmed
repeated computation, but its share of the 10.441 ms has not been measured. The next bounded
targets are invariant scene metadata reuse and entity conversion/packing suitable for GPU work;
preserve per-ray face semantics, history and GPU retirement. The 5.178 ms native
`submit-record-other` residual also remains incompletely attributed. Small measured Flywheel/Sable
bridge spans do not exclude costs from their generated geometry elsewhere in the pipeline.

Raw capture, copied log/settings, artifact hashes and recomputed per-frame distributions are kept
in `run/frame-profile-detail-20260924/evidence/prism-static-16/{ANALYSIS.json,FILES.json}` and the
adjacent original profile directory. The preceding `MANIFEST.json` remains the source/build identity;
it was not overwritten by this analysis. No Prism settings/world edits, client restart, optimization,
staging, commit or push. No additional user sampling is needed to select the first bounded target;
performance gains require a later same-scene/settings comparison, including GPU and memory cost.

## 2026-09-24: First host preparation optimization and bounded A/B/A measurement

Status: implemented; build-verified; automated-verified; bounded runtime-observed; manual comparison
pending. Evidence: native behavior/GPU tests and three isolated client profiles. Applies to the
existing paired dirty worktrees above Radiance `6e9b97a53049fad833e673da647ac517efde5fe3` / MCVR
`ead8d47d80ad2bc9cf81740c29f0ec230b61f92f`; all earlier diagram/profiler work is preserved.

### Implemented scope

MCVR now evaluates the model-wide face rule once per model traversal, sharing its value between
BLAS opaque decisions, TLAS flags and per-geometry shader flags. The direct chunk, entity and
Flywheel consumers retain mixed single/double-sided and mirrored-transform semantics. This is a
local value snapshot, not a cache across material changes. WorldPrepare reserves CPU metadata
arrays from already published geometry counts and avoids temporary hit-group string copies.
Entity conversion reserves known geometry/vertex counts before filling vectors. Flywheel's prepared
instance list is produced once and reused for capacity prediction and actual traversal.

No GPU offload, cross-frame geometry cache, shader/JNI changes, view culling, history simplification
or retirement changes were introduced. The new capacity prepass still visits all published owners;
actual traversal continues generation checks and resource retention. These are local reductions
in repeated host work, not a complete response to the longer-term GPU migration direction.

### Tests and measurement

RelWithDebInfo native INSTALL and paired `distributedJar verifyDistributedJar` passed. The first
Gradle invocation split an unquoted PowerShell `-Pmcvr.configuration` argument and did not execute
packaging; quoting the property fixed the invocation, with no build configuration change. Logs are
preserved separately. **7/7 targeted CTests passed**, including the existing Vulkan material-faces
GPU fixture, JNI contract, vertex packing, world mesh, instancing and profiler tests. Expanded face
tests execute the production value helper for 512 mixed models, mirrored/zero transforms, unrelated
material bits and same-allocation material replacement. An optional synthetic benchmark confirms
equal checksums and avoids repeated scans; its speedup is not a game-FPS prediction. GAME/bootstrap
source was unchanged and its old test counts are not represented as new execution.

A/B/A used fresh copies of the same repository test world and identical initial settings/mods:
1280x720, 16 chunks, DLSS balanced/RR model 4, four bounces, FG off, Reflex 1, vsync on, cap 260.
The setup script initially wrote only Radiance's vsync field to false; Minecraft's retained
`enableVsync:true` is applied by WindowMixins at startup and all three final configs record true.
This is not a vsync-off test; the correction does not change the equal settings across A/B/A.
Each process settled for 30 s after world entry, then captured 30 s; no build or other game client
ran during sampling. All recorded frames were focused with no screen and no failed frames or
sample drops. No background section worker work was recorded. All three saved every dimension
and exited 0. This is a live stationary world, not a deterministic replay of entity animation.

| Metric (ms unless stated) | Old A, PID 47024 | New B, PID 101248 | Old A2, PID 10524 |
| --- | ---: | ---: | ---: |
| Recorded frames | 953 | 1072 | 958 |
| Mean real frame interval | 31.482 | 28.003 | 31.326 |
| Interval p50 / p95 / p99 | 31.419 / 36.986 / 43.072 | 28.369 / 31.940 / 35.462 | 31.705 / 35.600 / 40.271 |
| Chunk metadata, host self | 9.205 | 6.447 | 9.137 |
| Metadata init, host self | 0.255 | 0.487 | 0.249 |
| WorldPrepare, host inclusive | 11.384 | 8.655 | 11.315 |
| Java geometry marshaling | 2.883 | 2.893 | 2.948 |
| Main-queue GPU completion | 7.410 | 6.985 | 5.854 |

Quantiles use linear interpolation over positive raw frame-start intervals. The new capacity
prepass adds metadata-init work; compare total preparation rather than counting only the cheaper
chunk loop. The measured frame interval reduction is approximately 11%, or about 32 to 36 real
FPS, in this isolated scene. A2 returning to the old host costs strengthens the comparison, but does
not separate each optimization's contribution. Geometry marshaling shows no clear gain here;
GPU intervals varied and no GPU-speedup claim follows. Peak VRAM, upload/allocation bytes and
moving/player-update workloads were not measured in this batch. Do not extrapolate this result to
the user's different 3840x2054 scene or treat it as long-duration/visual acceptance.

Diagnostic precision correction: the Java exclusive scopes partition their outer FRAME_OTHER
scope, not every nanosecond of the enclosing wall timer. These captures have a mean wrapper gap
below 0.001 ms/frame, retained in `COMPARISON.json`; it is not missing data or normalized away.
Earlier statements of exact wall equality should be read with this timer-boundary qualification.

### Identity and handoff

New Radiance JAR: `C5507DC8F1F1A112229CF17041CF472EBE0242DE1BDEDB6E56A98BE62A1D8A77`.
New core.dll: `5C1CD35F6EABA6C5EDFB45BC49518064AF2FCA3E80EC9B8B627C7FA75FF908C6`.
Unchanged matched Audit JAR: `049754B8250437B09641DCFA706AB489D2798047EBEE4920E2664E181877F246`.
Build, embedded, extracted and actually loaded isolated-client DLL agree. A/A2 used the previous
`0BC8AA...` JAR / `3A09E3...` DLL; their results remain separate from B.

`run/host-preparation-opt-20260924/evidence/` holds the complete source snapshots and build inputs
(`MANIFEST.json`), local candidate diff, PDB, tests, benchmark and `COMPARISON.json`; the three
adjacent case directories retain raw profiles/logs/settings/world copies. The original world source
is the earlier repository `run/frame-profile-detail-20260924/final/client/saves/Test`, not Prism.
The matched JAR handoff to the authorized Prism instance is recorded in `DEPLOYMENT.json`; no
settings or worlds are changed. User comparison should keep the original 16-chunk scene, position,
view and resolution, capture 30 s after settling, and then observe normal movement/interaction.
The agent reads identities and metrics. No new visual acceptance, staging, commit, amend or push.
GPU-loss uncertainty, deferred diagram/spring issues and public DLL licensing remain independent.

## 2026-09-24: Prism follow-up of the host preparation optimization

Status: runtime-observed; performance attribution remains bounded.
Evidence: two user-operated 30 s profiles and installed/loaded artifact hashes; no new build or tests.
Applies to: the preceding host-preparation worktree and deployment, unchanged product source.
Supersedes: no prior run; supplements the isolated A/B/A with the user's different 4K workload.
Remaining acceptance: moving/interaction visuals, memory/upload trends and broader workloads.

The user completed sampling and explicitly reported a slightly different view angle. PID 72800
captured at 14:08:44 and 14:11:25 +08:00. Both used 3840x2054, 16 chunks, focused frames, FG off,
and predominantly ChatScreen, matching the earlier profile's presentation conditions. The saved
Radiance option values match the old capture (ignoring timestamp comments); pipeline.yaml is
byte-identical. No sample drops or failed frames were recorded. This does not establish an identical
scene workload: native per-geometry vertex-packing calls increased from 856.62 to 1040.82 per frame.
Those are call counts, not entity or vertex counts, and do not provide an exact work normalization.

| Metric (ms unless stated) | Old PID 102228 | New first | New settled |
| --- | ---: | ---: | ---: |
| Recorded frames | 478 | 464 | 485 |
| Mean real frame interval | 62.761 | 64.667 | 61.913 |
| Interval p50 / p95 / p99 | 62.251 / 68.599 / 72.738 | 64.100 / 70.563 / 76.626 | 61.193 / 66.191 / 70.902 |
| Chunk metadata, native CPU self | 10.441 | 7.927 | 7.977 |
| Metadata init, native CPU self | 0.239 | 0.550 | 0.578 |
| Java geometry marshaling | 14.109 | 15.312 | 15.491 |
| Entity batch construction, native CPU inclusive | 5.866 | 6.992 | 6.514 |
| Vertex packing within that batch | 3.172 | 3.813 | 3.439 |
| Main-queue GPU completion | 20.004 | 22.353 | 21.411 |
| Background Java section compilations | 0 | 848 | 0 |

Quantiles are recomputed consistently from raw positive frame intervals with linear interpolation;
the generated reports use a different quantile convention. GPU rows omit unavailable tail frames.
The first new sample includes 848 concurrent section compilations and 833 native worker enqueue
events, so it is not a settled stationary comparison. The second contains neither. Nested CPU rows
and overlapping GPU/worker work must not be summed into a frame cost.

The settled sample retains lower chunk-metadata cost (about 24%), including a net reduction of
about 2.12 ms when its more expensive capacity prepass is included. End-to-end interval only moves
from 62.76 to 61.91 ms (about 1.4%, roughly 15.9 to 16.2 real FPS). This small uncontrolled difference
is not a proven overall speedup or an extrapolation of the isolated 11% result. Geometry marshaling,
entity batch work and GPU duration increased alongside the changed workload; these samples alone
cannot label those changes an optimization regression. Recurring host data preparation remains
the measured bottleneck. No GPU offload or new product change was made for this follow-up.

Prism's installed JAR is `C5507DC8F1F1A112229CF17041CF472EBE0242DE1BDEDB6E56A98BE62A1D8A77`;
PID 72800 actually loaded core.dll `5C1CD35F6EABA6C5EDFB45BC49518064AF2FCA3E80EC9B8B627C7FA75FF908C6`.
Audit remains `049754B8250437B09641DCFA706AB489D2798047EBEE4920E2664E181877F246`.
`run/host-preparation-opt-20260924/evidence/prism-followup/` preserves both raw captures, settings,
log, `LOADED.json`, reproducible `Analyze.py`, `ANALYSIS.json` and SHA-256 `FILES.json`. The preceding
source snapshot/build manifest is retained unchanged. The baseline remains in the earlier
`run/frame-profile-detail-20260924/evidence/prism-static-16/` directory.

The observed log has no new device-loss report, but includes server saved-map decoding errors
also present before optimization, outside these capture windows. They are not attributed to this
native optimization. The process was still running at the identity check; this is not a final
save/close result. The user's sampling feedback does not imply visual or interaction acceptance.
Only evidence and maintenance records changed; no game/settings/world mutation or Git action.

## 2026-09-24: User authorizes copied-scene performance fixtures

Status: user-approved test policy; no world copy or fixture run performed by this entry.
Evidence: user permits copying the Prism scene into a test fixture and, when necessary, fixing
time/weather or controlling entity variation. Applies to future paired performance measurements.

Copy the selected world into an isolated repository `run/` case after a consistent save; retain
the user's original world/settings unchanged. Record source identity, copied player pose, renderer
settings and every fixture adjustment. Time/weather and entity controls belong only to the copy.
Prefer controlling motion/state while preserving entity counts, models and visible geometry;
removing entities or effects changes the workload and must not be reported as an optimization gain.
Use identical fresh fixture copies and controls on both sides of a comparison. This extends the
earlier repository-only source-world restriction for benchmark preparation, not authorization to
edit or automate gameplay in the user's Prism world. Future fixture work is tracked in the roadmap.

## 2026-09-24: Submission-local strings and unchanged sampler fast path

Status: implemented; build-verified; automated-verified; bounded runtime-observed.
Evidence: production-helper tests, matched package, copied-scene A/B/A and scripted client regression.
Applies to: Radiance worktree over `6e9b97a53049fad833e673da647ac517efde5fe3` and MCVR worktree over
`ead8d47d80ad2bc9cf81740c29f0ec230b61f92f`, including earlier uncommitted work.
Supersedes: no earlier measurements; establishes a controlled follow-up to the Prism captures.
Remaining acceptance: broad moving/visual workloads, memory/transfer costs and long-duration stability.

### Scope and implementation

The user delegates subsequent testing and repair to the agent. Automated gameplay remains confined
to repository copies; this does not turn automated assertions into user visual approval.
`EntityProxy` now uses `SubmissionStrings` to own one UTF-8 allocation per distinct group/content
name within a synchronous JNI submission. Native conversion copies the names before returning;
the existing finally path releases the table. Nested submissions are independent, and no string
pointer or cached material decision survives a frame/reload. Every geometry still captures its
actual RenderType face state. Vertex math, JNI signatures and geometry selection are unchanged.

MCVR skips sampler creation and descriptor/alias republishing for unchanged ordinary-texture
settings. The comparison now includes mipmap mode, correcting a previously missed mipmap-only
change. Changed settings allocate first, retain the old sampler with the existing frame/reload
retirement mechanism, then replace/publish. Frame aliases still republish because their image can
change with the same sampler. Texture initialization, bind-all, reload commit and pipeline creation
retain their own publication paths. This is CPU work avoidance, not GPU vertex offload.

### Fixture and comparison

After the user's Prism world was normally saved and closed, `Newport City` was copied to
`run/entity-preparation-opt-20260924/fixture/`. `SOURCE.json` hashes every original world file and
records controls. Only the copy disables time/weather progression and mob spawning (already false);
saved time/weather, existing entities/AI and geometry are preserved. All timed cases use fresh
copies with the same saved position `(33.042726, 95, 223.112289)` and rotation `(100.08612, -34.41608)`.
Source-world hashes were checked again after testing; no source-world change was made.

Conditions: Java 21, NeoForge 21.1.251, identical copied mods/config/pipeline, 3840x2054, 16 chunks,
four bounces, DLSS balanced/RR model 6, SR model setting 13, Reflex 2, FG off, vsync off, cap 260.
Only vanilla and mod resources were enabled; a copied PBR pack was not active. Each process settled
45 s in-world and captured 30 s, focused with no screen. No concurrent build or game ran during
measurement. The separate JFR diagnostic process is excluded from timing comparisons.

| Metric (ms unless stated) | A, PID 98536 | B, PID 33464 | A2, PID 100912 |
| --- | ---: | ---: | ---: |
| Recorded frames | 465 | 497 | 465 |
| Mean real frame interval | 64.530 | 60.434 | 64.641 |
| Interval p50 / p95 / p99 | 63.603 / 69.847 / 75.299 | 59.919 / 65.122 / 69.856 | 63.679 / 70.426 / 76.602 |
| Java geometry marshaling | 15.545 | 13.145 | 15.545 |
| Java auxiliary textures | 2.266 | 0.638 | 2.325 |
| Native conversion/copy/enqueue | 5.080 | 5.064 | 4.994 |
| Native vertex packing | 4.182 | 4.239 | 4.170 |
| Native entity build, inclusive | 7.907 | 8.032 | 7.883 |
| Chunk metadata, host self | 8.138 | 8.298 | 8.249 |
| Main-queue GPU completion | 25.414 | 26.257 | 25.362 |

Raw positive frame-start intervals use linearly interpolated quantiles. Nested/overlapping rows
are not additive. Mean interval improves about 6.4% (roughly 15.5 to 16.5 real FPS); A2 restores
the earlier costs. Combined changes are not individually attributed. Per-frame vertex-packing call
counts are approximately 1040.7/1040.5/1040.6; final saved poses and renderer settings match. Live
entity animation is not deterministic. A/B/A recorded 1/0/7 background section compilations
(2.04/0/8.68 ms total), no sample drops or failed frames, and all saved every dimension and exited 0.
Native vertex packing and GPU time did not improve. Peak VRAM/upload bytes, moving scenes and
long-duration stability were not measured. This does not establish an all-world speedup.

### Tests, failure and retest

- RelWithDebInfo native INSTALL and `:distributedJar :verifyDistributedJar` passed.
- GAME `:test` passed 5 targeted cases (submission-string ownership and material-face capture).
  An initial unqualified `test --tests ...` also selected the Audit project and failed because it had
  no matching tests; qualified `:test` passed. This was a task-selection failure, not hidden success.
- Eight targeted CTests passed: contract, texture upload region, entity vertex packing, texture
  lifecycle contract, texture name pool, texture binding snapshots, sampler update and material
  faces. Source scanning in the lifecycle contract is supplementary. New helper cases exercise
  repeated settings, mipmap-only change, allocation failure, retirement, reload and new ownership.
  After tightening the exception assertion, the sampler target was rebuilt and its CTest rerun.
- The first scripted regression (PID 67348) failed with `IllegalClassLoadError`: the optional Audit
  scenario used `LevelRendererAccessor`, but its Mixin was disabled with CHUNK_BUILD logging.
  World save evidence is retained. This was a diagnostic Java failure, not device loss.
- Audit now enables that accessor for explicitly permitted experiments without enabling heavy
  chunk observers. Normal operation remains default-off. Audit Java tests passed 18/18; Audit JAR
  verification and both native collector tests passed. Main product artifacts were unchanged by
  this diagnostic repair.
- Retest PID 38168 passed the seven-stage scenario in 110.30 s after world entry: compare the active
  block-entity index with a full section scan, add/remove a chest, F3+T, F3+A and temporary clouds-off/
  restore. Assertions matched at each checkpoint; all dimensions saved and process exited 0 after
  144.64 s total. This checks state/progression and safe termination, not final rendered pixels or
  exact interaction latency. No device loss occurred. Pre-existing map-data parse errors and Veil
  unsupported-particle messages remain in these logs and are not attributed to this optimization.

### Identity, evidence and deployment

Product JAR SHA-256: `A23143A7191D249688CE4C0635623DE524F825A2FDBF0AFE402D8D832AD64633`.
Core DLL SHA-256: `D3A5A20087ED99C683698CD9A5FDF2BF5CAC650D85FD1313DCCF3B13B3F355CF`.
Final Audit JAR SHA-256: `741CF08C1FE4F40C3AF96A606A4471ADA15BFF86A3B0BB2681DA73D9953CF8F6`.
A/A2 used the preceding `C5507D...` JAR / `5C1CD3...` DLL. All three timed runs used the same earlier
`049754...` Audit; the final Audit repair is exercised separately by the regression retest.
Build, embedded, extracted and actually loaded core DLL agree for B and the retest.

`run/entity-preparation-opt-20260924/evidence/` retains the full tracked/nonignored source snapshots,
raw and LF-normalized hashes, matching PDB, build/dependency inputs, tests, per-batch diff,
`COMPARISON.json` and final `MANIFEST.json`. Adjacent case directories hold raw profiles, logs,
settings and world copies; the initial diagnostic failure is retained separately. `DEPLOYMENT.json`
records the matched two-JAR handoff to the authorized Prism mods directory; no settings/worlds are
changed. No new user visual acceptance is inferred, and no further manual test is requested.
Remaining GPU offload/measurement work is in the roadmap. Earlier GPU-loss uncertainty, deferred
spring/diagram/plunger defects and public binary licensing stay open. No staging or Git rewrite.

## 2026-09-25: Entity packing writes directly into owned upload storage

Status: implemented; build-verified; automated-verified; bounded runtime-observed.
Evidence: field-equivalence tests, real-client B/A/B comparison, package and loaded-DLL identities.
Applies to: paired dirty worktrees over Radiance `6e9b97a53049fad833e673da647ac517efde5fe3` and
MCVR `ead8d47d80ad2bc9cf81740c29f0ec230b61f92f`; prior changes remain intact.
Supersedes: no prior measurement; this isolates the next native packing optimization.
Remaining acceptance: longer/moving workloads and visual breadth; no GPU offload is implemented.

### Scope and ownership

The MCVR entity batch previously packed PBR data into three temporary CPU vectors, then copied them
into staging allocations. It now writes position, material and index streams directly into their
owned mapped staging buffers. `Vertex::writePackedVertices` uses the same field/flag conversions,
checks exact destination sizes and supports separate layer offsets. The source PBR data remains:
eye-overlay matching, topology/normal offsets, material emission and other consumers still need it.
No RenderType/face rule, shader, vertex layout, JNI signature or geometry selection changed.

`DeviceLocalBuffer::writeToStagingBuffer` shares the existing staging preparation with ordinary
uploads. Its synchronous callback cannot retain the mapped pointer or submit work. It flushes
before returning; allocation/writer/flush failure prevents the batch from reaching upload submission.
Flush errors preserve their VkResult through the existing fatal boundary. The existing transient
staging owner, frame retirement, important upload queue and BLAS ordering remain unchanged.
There is no global idle, new buffer cache, GPU compute pass or modification to the Prism world.

Optional native profiling now aggregates repeated format/topology/material phases before calling
the external Audit collector, rather than emitting per-vertex events. Batch layout, device buffer
allocation, packing, staging and BLAS preparation are separate scopes. The `entity.blas-allocate`
last scope also includes destruction of later-declared batch locals; its reduction must NOT be
reported as isolated BLAS allocation or GPU build acceleration. Disabled profiling has no timing
reads or observer output; its branch/stack overhead has not been independently benchmarked.

### Controlled comparison and limits

Use the preserved `run/entity-preparation-opt-20260924/fixture`, with the same saved pose, fixed
time/weather progression, existing entities/AI and geometry. No new Prism world read or edit was
needed. Fresh independent copies use Java 21, NeoForge 21.1.251, identical third-party mods/options/
pipeline, 3840x2054, 16 chunks, four bounces, DLSS balanced/RR model 6, FG off, vsync off, cap 260.
Each run settles 45 s in-world and captures 30 s, focused with no screen. No other game or build runs
during capture. Process/WDDM counters are sampled identically every 5 s.

| Metric (ms unless stated) | New B, PID 15976 | Reference A, PID 75160 | Final B2, PID 93952 |
| --- | ---: | ---: | ---: |
| Recorded frames | 557 | 519 | 554 |
| Mean real frame interval | 53.937 | 57.880 | 54.243 |
| Interval p50 / p95 / p99 | 53.421 / 57.600 / 61.323 | 57.359 / 60.780 / 66.919 | 53.689 / 58.367 / 62.355 |
| Native format allocation/decode/copy | 2.350 | 2.364 | 2.386 |
| Native topology/coordinate processing | 1.787 | 1.746 | 1.811 |
| Native material overrides | 0.068 | 0.068 | 0.066 |
| Entire conversion/copy/enqueue | 4.988 | 4.976 | 5.078 |
| Host packing, inclusive | 2.047 | 4.277 | 2.063 |
| Staging preparation/copy | 0.006 | 0.813 | 0.008 |
| Entity build dispatch, inclusive | 3.583 | 7.627 | 3.648 |
| Main-queue GPU completion | 24.847 | 25.012 | 24.949 |
| WDDM process dedicated memory, sampled peak MiB | 8970.01 | 8969.51 | 8969.51 |
| Process private bytes, sampled peak MiB | 15395.53 | 15001.75 | 15091.81 |

The final comparison reduces mean interval about 6.3% (roughly 17.3 to 18.4 real FPS); both new runs
retain the cheaper packing/upload path. Host packing plus staging drops from 5.090 to 2.070 ms.
The reference retains the old temporary-vector implementation with exactly the same phase probes;
its full source differs from the candidate only in `entities.cpp`. Candidate and final rebuilt
product sources match byte-for-byte, but their relinked binaries are identified separately below.
All three runs saved every dimension, exited 0 and reported no lost/failed samples. Final saved poses,
renderer options and pipeline hashes match. Background compiles were 0/4/4, totaling 0/6.67/3.07 ms.
Quantiles use linear interpolation over raw positive frame intervals. Nested CPU scopes, workers
and GPU completion overlap and are not additive. Live animations are not deterministic replay.

WDDM reports process memory accounting, including allocations outside MCVR's own counters; the
5-second samples are not an exact allocation inventory or continuous peak. JVM/private memory
varies; no overall memory reduction or GPU speedup is claimed. GPU upload byte volume was not
independently sampled. The first two stages still cost about 4.2 ms and include allocation/copy,
index construction, line expansion and texture-ID collection, not pure vertex arithmetic. This
supports retaining the CPU optimization, not a prediction that a GPU migration would save all of it.

### Validation, evidence correction and identity

RelWithDebInfo INSTALL and `:distributedJar :verifyDistributedJar` passed for the final package.
Ten distinct targeted CTests passed across two runs: contract, frame-profile, entity-vertex-packing,
instancing-contract, world-mesh-contract, upload-staging-budget, material-faces, material-faces-gpu,
failure-state-contract and pending-uploads. The GPU face test executes Vulkan but does not directly
test the new staging method. Mapped-output tests compare every packed field/byte, two layer offsets,
guard regions, empty input and rejection before writing an undersized destination. Aggregate timing
tests verify parent self-time, bounded observer events and stale-epoch rejection. Real clients
exercise the combined staging/upload/render path. Java/bootstrap source is unchanged; old Java test
counts are not presented as new execution.

The initial profiling attempt PID 77856 is excluded from the matched comparison: an edit script
failed on Windows default text decoding, and its correction overlapped compilation. The loaded DLL
did not emit the new phases, despite a newer object timestamp. Its retained source ZIP therefore
cannot establish exact build identity. `instrumented/IDENTITY-CORRECTION.md` preserves this failure.
Later builds finish source edits first, freeze all tracked/nonignored source and necessary new files,
check phase labels in the binary and actual trace, and verify loaded modules. A temporary substitution
of this batch's own `entities.cpp` produced the reference; the candidate was restored byte-for-byte
and rebuilt. No other work was reset or hidden.

Final JAR SHA-256: `2800DD4CF34DAA3161BB1D2F3628098FB2B9B32C76DD78AA042C6DEF1ED5AD3C`.
Final core.dll SHA-256: `30CFAB078EDFA85D899E0F1730D52B3F56ECB58C1D7F66DA7C181CD87D6C7AEB`.
Audit unchanged: `741CF08C1FE4F40C3AF96A606A4471ADA15BFF86A3B0BB2681DA73D9953CF8F6`.
B used JAR `E82AC198...` / DLL `0E08A422...`; reference A used `3F1AD134...` / `1C3222F...`.
Full hashes, source ZIPs, raw/LF-normalized file hashes, PDB, CMakeCache and embedded dependencies are
retained per build in `run/vertex-packing-opt-20260925/evidence/`. `COMPARISON.json`,
`RUNTIME_CHECKS.json` and adjacent cases retain raw profiles, settings, counters and saved copies.

Final functional regression PID 39668 used the final package with profiling off and the explicit
isolated probe enabled. Index/full-scan assertions matched after chest add/remove, F3+T, F3+A and
cloud-mode restore; all seven stages passed, all dimensions saved, and process exit was 0 after
143.26 s total. Its actual module hash matches the final DLL. Existing saved-map parsing errors
remain separate from these assertions; no new device loss was observed. This is functional runtime
coverage, not pixel equivalence or exact interaction-latency measurement.

`DEPLOYMENT.json` records the final Radiance JAR handoff to the authorized Prism mods directory;
Audit remains byte-identical. Original worlds, settings and other mods are untouched. Maintenance
records written after the build are preserved in `MAINTENANCE_ADDENDUM.json`; product/test source
still matches the frozen final manifest. No further manual sampling is requested. No visual approval,
device-loss root-cause closure, long-duration guarantee or public binary permission follows.
No staging, commit, amend or push was performed.


## 2026-09-25: GPU conversion of recurring native PBR entity batches

Status: implemented; build-verified; automated-verified; runtime-observed.
Evidence: static, build, automated, GPU, bounded runtime measurements; no new visual approval.
Applies to: the paired dirty worktree after the owned-staging optimization. Git HEADs remain
Radiance `6e9b97a53049fad833e673da647ac517efde5fe3` and MCVR
`ead8d47d80ad2bc9cf81740c29f0ec230b61f92f`; neither identifies the uncommitted candidate alone.
Supersedes: none; extends the previous CPU optimization without discarding its reference path.

### Actual workload and bounded implementation

The optional format census found about 150,180 PBR vertices (19.22 MB input) per recorded frame,
not the compact entity format initially considered. Native raw PBR quads/triangles can now defer
coordinate flags, normal offsets, emission overrides, index construction and final position/material
packing to `entity_convert.comp`. The existing Java model traversal, pose transforms and PBR
production remain on the CPU. This is not a complete GPU model transform implementation.

The raw capture owns its bytes before JNI returns. Eligibility excludes explicit-index inputs,
post draws, external world-token captures, cached clouds, prebuilt geometry and whole entities
with an eye layer. Those retain their CPU semantic/topology processing; ordinary processed PBR
can still use GPU final packing. Eye-overlay matching, line expansion, cloud reuse and the archived
UI scene recording path retain their existing consumers. Geometry order, face flags, material
modes and BLAS/TLAS visibility remain unchanged. There is no per-vertex texture lookup on the GPU.
The old unused texture-ID collection is also avoided by the deferred path; the measured host
reduction must not be attributed entirely to arithmetic offload.

Each batch has owned immutable input/jobs/descriptors and output buffers. Only the compute pipeline
is shared. Compute writes the final BLAS inputs and ray-tracing material stream directly, with no
production readback. Existing upload-to-compute ordering is followed by a compute-write dependency
to AS input and ray-tracing shader reads; recorded batches join the existing frame retainer. No
queue ownership change, global idle, forced serialization or unrelated visibility policy was added.
Normal operation enables this path; `MCVR_ENTITY_GPU_CONVERSION=0` before launch selects the retained
CPU reference. This is a restart-only diagnostic comparison, not a new UI option.

### Same-artifact stationary comparison

Evidence root (local, ignored): `run/gpu-vertex-opt-20260925/`. `before/`, `instrumented/`, `trial/`
and `candidate/` under `evidence/` retain distinct full source manifests/ZIPs and artifacts. The early
format-census and first-GPU trial are exploratory and are not combined with the final comparisons.
The candidate manifest contains tracked and nonignored untracked sources, raw and UTF-8 CRLF-to-LF
hashes, CMake configuration, embedded native/shader hashes and matching PDB. Later documentation-only
changes are recorded separately; they do not create a new renderer binary.

Fresh copies of `run/entity-preparation-opt-20260924/fixture` use the same saved pose, mods and
settings: Java 21, NeoForge 21.1.251, 3840x2054, 16 render/32 simulation distance, `rt_dlss`, balanced
RR model 6, four bounces, Reflex 2, FG/vsync off, cap 260. Time/weather controls and existing entities
are inherited unchanged. Each stationary run settles 45 s and captures 30 s. No build or other game
runs concurrently. All captured frames are focused, screen-free, nonfailed and report zero FG FPS.
The GPU/CPU/GPU ordering uses one JAR/DLL/Audit pair; only the native reference switch changes.

| Metric (ms unless stated) | GPU A, PID 99840 | CPU reference, PID 99172 | GPU B, PID 99360 |
| --- | ---: | ---: | ---: |
| Recorded frames | 605 | 553 | 595 |
| Mean real frame interval | 49.594 | 54.249 | 50.466 |
| p50 / p95 / p99 | 49.159 / 53.887 / 58.866 | 53.793 / 57.077 / 62.734 | 50.073 / 53.372 / 57.987 |
| Java geometry marshaling, inclusive | 10.812 | 13.099 | 10.819 |
| Native conversion/copy/enqueue, inclusive | 2.983 | 5.218 | 2.926 |
| Entity batch dispatch, inclusive | 3.006 | 3.693 | 2.995 |
| GPU conversion timestamp | 0.0454 | not executed | 0.0457 |
| Main-queue GPU completion | 25.196 | 24.585 | 25.369 |

Mean real frame interval is 7.0-8.6% lower, about 18.4 to 19.8-20.2 real FPS. This is a bounded
scene result, not a universal FPS forecast. GPU conversion is cheap but overall GPU completion is
not reduced. Inclusive scopes overlap; do not add Java/native/GPU rows. Quantiles interpolate raw
positive frame intervals; GPU unavailable/tail samples remain omitted, not zero-filled.

Logical conversion uploads are about 19.49 MB/frame (two buffers), versus about 15.32 MB for the
previous final-stream layout at the corresponding vertex/index count: roughly +4.18 MB/frame.
The former is directly counted; the reference byte figure is calculated from the final layout,
not independent PCIe telemetry. Counter rows are separate from duration CSVs and tested as such.
Sampled WDDM dedicated peaks stay around 8,970 MiB; JVM/private memory varies. Five-second WDDM
process samples are neither continuous peaks nor an exact allocation inventory, and no memory
reduction is claimed. The preserved fixture emits the same invalid map-dimension startup warnings
in reference and candidate cases; they occur before settled sampling and are not silently erased.

### Tests and artifact identity

RelWithDebInfo native INSTALL, shader compilation and `:distributedJar :verifyDistributedJar`
passed. Audit has 19 passing tests, including counter/duration separation. Twelve distinct targeted
CTest cases passed: frame-profile, gpu-profile, entity-vertex-packing, entity-conversion-gpu,
instancing-contract, jni-coverage, shaders, world-mesh-contract, upload-staging-budget,
pending-uploads, material-faces and material-faces-gpu. Some existing contract cases are static
supplements; they are not all GPU behavior tests.

The new real Vulkan test calls the production shader and command recorder, compares CPU packed
fields and indices, checks guard regions and position tolerance, uses processed/deferred PBR,
quads/triangles, two immutable batches and deliberately split dispatches, then builds BLAS from the
GPU outputs. It also passed with the installed Khronos core/synchronization validation and an
error-counting callback. OBS/RTSS older API warnings remain in the log. This proves the fixture's
conversion and synchronization, not full-client validation or final pixel equivalence. An initial
manual test invocation supplied an incorrect SPIR-V path and failed before dispatch; the corrected
CTest invocation and its successful log are retained separately.

Frozen candidate artifacts (unchanged for the comparisons):

- Radiance JAR: `22D6B9788151AB3E90AEA384AF59716595CC1FC30011F331A5D97EB2E828BC63`.
- core.dll: `930910337A6CBD189220D999BC290AAA0CB53F7CC56D6DC205541553B9757006`.
- Audit JAR: `077D78792CE42F90379621648B209EA5A204820DE13B0A3CAC4DE925AF8931D4`.
- Matching PDB: `DB42A30F2D4C7843BCB73EB316150F21F0F479FCE7CFCF645D8391EA05106DBA`.

Build, embedded, extracted and actually loaded DLL identities are checked in each case's manifest
and `loaded.json`. Existing dirty diagram/diagnostic/CPU optimization changes remain intact.
No Git staging, commit, history rewrite, push, public distribution or production-world change is
part of this batch. Long-duration stability, unrelated spring/diagram/plunger defects, historical
GPU-loss root cause and public DLL licensing remain open.


### Moving-route comparison and remaining measurement limits

Fresh GPU/CPU cases use the same frozen candidate and a 40-second, 5 Hz server-driven camera path
inside a 50-second profile: eight-block X excursion, +/-35-degree yaw, same Y/Z/pitch, then return
to the saved pose. Both execute all 201 targets with zero skips. This is a repeatable movement
fixture, not natural walking or deterministic animation replay. GPU PID 100100 and CPU PID 99436
exit 0 after 128.30/129.12 seconds, save all dimensions and report zero dropped/failed samples.

| Metric (ms unless stated) | CPU route | GPU route |
| --- | ---: | ---: |
| Recorded frames | 885 | 947 |
| Mean real frame interval | 56.534 | 52.813 |
| p50 / p95 / p99 | 56.646 / 61.013 / 66.137 | 52.676 / 56.800 / 63.096 |
| Native conversion/copy/enqueue | 5.619 | 3.142 |
| Entity batch dispatch | 4.039 | 3.234 |
| Main-queue GPU completion | 23.473 | 23.678 |
| GPU conversion | not executed | 0.0477 |
| Input PBR vertices/frame | 160,152.6 | 160,167.3 |
| WDDM dedicated peak MiB | 8,976.02 | 8,976.02 |
| Process private peak MiB | 15,191.70 | 15,285.75 |

The moving case reduces mean real frame interval about 6.6%. Captured settings/focus are unchanged;
GPU/native timing availability is reported independently. Logical GPU conversion upload averages
20.79 MB/frame on this route. Stationary private peaks varied from 14,951 to 15,874 MiB across GPU
runs versus 15,289 MiB on CPU: this batch does not establish a reliable private-memory saving.
`STATIC_COMPARISON.json`, `ROUTE_COMPARISON.json`, `RESOURCE_COMPARISON.json`, raw profiles, options,
launches, module identities and logs preserve the actual evidence. Profiling overhead has not been
separately controlled; CPU/GPU/copy costs must not be summed into an exclusive frame pie chart.


### Update regression, final snapshot and deployment

The default GPU path also ran without frame profiling in PID 91280. The isolated probe passed all
seven stages in 110.11 world seconds: initial block-entity index equivalence, chest insertion and
removal, F3+T, F3+A, clouds off/restore, and final index equivalence (359 active sections restored).
All dimensions saved and the process exited 0 after 142.69 seconds. This checks internal ownership
and live update/reload execution, not every final pixel or interaction latency. Existing Veil
unsupported-layout and map-data warnings remain visible; they are not declared solved here.

All six final cases used the same JAR/DLL/Audit, completed normal save/exit and showed no device-loss
result. `CASE_VERIFICATION.json` records per-process identity/results and fixed mod hashes. The
host is RTX 4080 SUPER, driver 616.92, 16,376 MiB reported memory. Final `evidence/final/MANIFEST.json`
and source ZIPs include the documentation addendum; product/test/build source hashes match the
compiled `candidate` snapshot. No mechanical rebuild was needed for those documentation changes.
The raw tests/XML, normalized/raw source hashes, SPIR-V identities, matching DLL/PDB, run scripts
and individual logs are retained under the same ignored evidence root. This is acceptance evidence,
not a disposable maintenance backup.

The same matched Radiance/Audit artifacts were deployed to the authorized non-portable path
`E:\Minecraft\PrismLauncherDev\instances\Radiance 1.21.1-neoforge\minecraft\mods`.
`DEPLOYMENT.json` records before/after hashes. Only the two mod JARs were replaced; prior artifacts
remain in the before snapshot. No Prism settings/world data were changed or played automatically.
All automated clients have exited; no computer-use session was used. There is no request for a new
manual sample. This does not add user visual approval, long-duration stability or cross-GPU evidence.


## 2026-09-25 — Incremental published chunk scene metadata and rendering parity

Status: implemented, build-verified and automated-verified; bounded runtime/performance results
are recorded below. No new user visual acceptance or general GPU stability claim.

The user requested the next measured optimization and explicit rendering-correctness checks.
The previous GPU conversion remains enabled. This batch targets the approximately 8 ms/frame
native chunk metadata phase; Java geometry generation, shaders, TLAS building, scheduling,
view-distance policy and GPU completion/retirement contracts are not redesigned.

MCVR stores one immutable CPU metadata snapshot per published chunk: owned buffers and address
arrays, hit-group names, versioned face rules and instance appearance data. Frames retain the
snapshot as one resource set instead of separately retaining nine leaves. Version/resource
replacement, invalidation and emission-resource release invalidate it. Current double-precision
camera-relative transforms and determinant-dependent winding remain per-frame work. Only primary
world slots omit their unused transform history; every external slot retains history, including
the frame before its first custom transform. This last boundary was caught during this batch's
source review and added before final comparisons. No camera-based geometry removal is introduced.

CPU reference assembly remains available after restart with `MCVR_CHUNK_SCENE_CACHE=0`.
Default-off `MCVR_CHUNK_SCENE_VERIFY=1` independently compares the actual appended addresses,
hit groups, instance appearance bytes and face rules with the live published source. Profiling
exports separate hit/build/custom-history work counters; those values are not time measurements.
Verification is disabled in all performance captures. Flat GPU metadata tables still upload each
frame, and TLAS construction remains unchanged: this is not a fully persistent GPU scene.

Evidence (non-portable, ignored): `run/chunk-scene-opt-20260925/`. `before` preserves the incoming
worktree and prior artifacts; `candidate` is the first tested implementation; `validated` includes
the external first-transform guard and final Audit fixture. Complete tracked/nonignored-untracked
source ZIPs and raw/LF-normalized hashes distinguish these snapshots. They are acceptance evidence,
not disposable Git recovery backups. Existing dirty work was retained without staging or commits.

The final RelWithDebInfo native INSTALL and matched `distributedJar`/`verifyDistributedJar` builds
passed. Thirteen targeted CTests passed, including actual GPU entity conversion/BLAS and material
face tests, plus cache append/invalidation, ownership retention, mixed face rules, mirrored/large
coordinate history, external first-transform, view separation and failure-safe factory tests.
Audit tests/build passed; its optional existing hotspot probe now supports per-stage screenshots
and a three-block Sable iron/leaves/glass fixture. These experiments require explicit access and
isolated markers, and are not enabled for normal Prism use.

### Same-artifact performance and correctness evidence

RTX 4080 SUPER / driver 616.92; 3840 x 2054, render distance 16, simulation distance 32,
RR Balanced (recorded RR model 6), four bounces, Reflex mode 2, FG/vsync off, cap 260. Each process
starts from a fresh copy of the same isolated fixed-time/weather city fixture and waits 45 world
seconds. Static profiles last 30 seconds; movement profiles last 50 seconds and include the same
40-second, 5 Hz eight-block X / +/-35-degree yaw route. All captured frames are focused, world-active,
without a GUI or failed-frame marker; generated-frame FPS is zero. Neither quality nor view distance
was reduced. Pose animation and OS scheduling remain nondeterministic.

| Case, in execution order | Frames | Real interval mean / p50 / p95 / p99 (ms) | Chunk metadata self (ms/frame) |
| --- | ---: | ---: | ---: |
| cached-static-a | 662 | 45.313 / 44.908 / 48.264 / 53.197 | 4.469 |
| reference-static | 494 | 60.721 / 64.734 / 72.453 / 80.174 | 7.989 |
| cached-static-b | 615 | 48.809 / 47.422 / 66.443 / 69.626 | 4.781 |
| reference-static-b | 608 | 49.357 / 48.973 / 52.462 / 56.235 | 7.945 |
| cached-route | 1022 | 48.955 / 48.801 / 52.876 / 58.383 | 4.704 |
| reference-route | 969 | 51.614 / 51.487 / 56.166 / 60.762 | 7.856 |

Metadata work falls from 7.85-7.99 to 4.47-4.78 ms/frame (about 40-44%). The two entries per frame
under that label are consecutive phases in one preparation call, not two full scene traversals.
The moving comparison reduces real frame interval 5.2% (51.614 -> 48.955 ms), p95 56.166 -> 52.876 ms.
Both moving cases finish all 201 targets with zero skips and identical initial camera position/angles.
This small route is not a new-chunk throughput or large-distance travel benchmark.

Do not attribute the entire 60.721 -> 45.313 ms static difference to this cache. The first reference
run has 10.84 ms `acquire-other` mean versus 1.25 ms on its repeat; cached runs also vary (0.97/2.10 ms).
The extra frame-start variation is not localized further here. All samples are retained, including
the slower reference and noisier cache run. GPU main-queue duration stays approximately 24-25 ms in
static runs. Profiling overhead was not separately controlled; inclusive Java/native/GPU values are
not additive exclusive portions of a frame.

Steady captures contain approximately 8,199 reused chunk snapshots per frame. Cached static A has
zero rebuilds during capture; static B has three total and the moving run six, matching occasional
live publications rather than continual rebuilding. Static logical PBR input is approximately
150,168 vertices/frame in both paths; moving input is approximately 160,110. Native/GPU mesh input
volume is not reduced by culling. All static WDDM dedicated peaks are 8,970.01 MiB; moving peaks are
8,975.52/8,976.02 MiB. Process-private peaks range 15,067-15,873 MiB; the moving cache/reference peaks
are 15,232/15,014 MiB. No process-memory saving is claimed. WDDM samples are every five seconds over
the entire process, not continuous allocation peaks or a statistic excluding SDK-owned resources.

The earlier candidate PID 100092 passed the original seven-stage update case in 110.16 world
seconds and exited 0, but predates the external-history safeguard and must not be merged with final
results. Final candidate PID 7216 passes the extended 110.20-second case: chest add/remove, mixed
Sable fixture assembly/client delivery, F3+T, F3+A, cloud off/restore, final index consistency and
normal save/exit (143.97 seconds total). Its default-off source verifier reports at least 9,540,582
chunk comparisons with no mismatch, including a custom-transform external slot across both reloads.
The block-entity index goes 359 -> 360 -> 359 before reload and returns to 359 after rebuilding.
Stage screenshots are retained for comparison; this is no new user visual verdict. Prior fixture
invalid-map-dimension warnings and Veil unsupported-layout reload errors remain visible and are not
claimed solved by this optimization.

Final matched build identity (SHA-256):

- Radiance JAR: `9A537E4398BB292271384C7373F7C318E8392A818CF694B648581F17A3C67813`.
- core.dll: `B7BBB90BBCAC5906F25EFBB684E898CD9FE64B4E919CA8711EB275767502F6F4`.
- Audit JAR: `2A69CA08C4D62CEDC4201896861BA2C94560306B16038F8A4D97FF2150006521`.
- Matching local PDB: `125B492BA193CD8750C7A5E408AE113EA9EC618A8ABD4501C1EF0411BA9FF833`.

The 19 Audit tests pass with no skips. Native validation is 13/13, not the entire historical suite.
`STATIC_COMPARISON.json`, `ROUTE_COMPARISON.json`, `RESOURCE_COMPARISON.json`, raw profiles, process
logs, test XML and source/packaged manifests preserve the evidence. The 15 fixed git submodules
and ignored Streamline headers / NRD-generated includes were freshly inventoried and unchanged
from the prior GPU conversion batch. Build, embedded and actually loaded DLL hashes agree.


### Reference reload control and final handoff

The same final artifact with cache disabled also passes the Sable/edit/F3+T/F3+A/cloud regression
(PID 100060, 110.14 world seconds / 143.81 process seconds, exit 0, all dimensions saved, final index
359). Agent inspection of the two final stage screenshots found no gross missing-building or texture
replacement difference in that view. PT noise/cloud samples differ; no pixel-exact or user visual
approval is inferred, and the Sable fixture's delivery/input checks are not a comprehensive visual
physics test. Eight processes with the final artifact (four static, two moving, two update controls)
all save/exit normally without a reported device loss. The first pre-safeguard candidate is kept as
separate evidence. `CASE_VERIFICATION.json` verifies hashes of all six installed mods, embedded and
extracted/loaded DLL identity, per-process results and the actual pass lines.

`evidence/validated/MANIFEST.json` retains the exact compiled product/test/build source bytes.
The final maintenance snapshot adds documentation and removes spaces from one otherwise empty C++
line; `FINAL_CHECK.json` records that sole non-document difference explicitly. It changes no token,
line number or behavior and did not trigger a mechanical rebuild or reuse of a new binary hash.
The tested artifacts remain exactly those listed above. Both staged areas remain empty and the
preexisting worktree changes remain uncommitted. Whitespace checks pass.

The matched Radiance/Audit JARs are deployed to the authorized non-portable Prism instance
`E:\Minecraft\PrismLauncherDev\instances\Radiance 1.21.1-neoforge\minecraft\mods`.
Only the two existing mod JARs are replaced, with before/after identity in `DEPLOYMENT.json`;
Prism settings, saves and other mods remain untouched. No automatic Prism launch or computer-use
session was used. All repository test clients have exited. No further manual sample is requested.
This does not close historical GPU-loss, other PT configurations, large-distance travel, long-term
stability or NVIDIA public-redistribution gates.

## 2026-09-25 — Defer idle raster commands during real material-state capture

Status: implemented and build-verified; targeted automated/GPU checks and bounded runtime comparisons
are recorded below. This is not renewed user visual acceptance or a general stability conclusion.
Applies to the uncommitted Radiance/MCVR worktrees based on `6e9b97a` / `ead8d47`; existing changes
are retained. Local, non-portable evidence: `run/submission-opt-20260925/`.

The next Java-to-native submission investigation first separated argument allocation, cleanup and
actual RenderType face-state capture. The original product with finer external-Audit profiling
measures about 0.05 ms/frame for argument allocation/free, versus 6.80 ms for face capture (setup
1.44, clear 0.59, GL-state backup 0.03, restore 3.21 ms; nested, not additive frame shares). Therefore
this batch does not add an argument arena, skip callbacks, cache faces by RenderType/name, or begin
GPU pose/model migration. JNI copies the owned input before returning; that lifetime is unchanged.

MCVR's raster setters immediately update their shadow fields but no longer record transient GPU
state commands while `overlayMode == NONE`. Real draw entries already replay the complete state;
the NONE-to-POST entry now does so too. Active-pass changes remain immediate. Actual setup/clear
callbacks, callback-internal draws, GL-cache restoration, face/winding rules, target/viewport changes
and JNI fatal checks remain in place. Main/FBO/diagram/post entry points and masked-clear/fixed
post-effect paths were checked directly. There are no shader, geometry, queue, resource-retirement,
JNI ABI or Java product changes in this batch. `MCVR_IDLE_UI_STATE=0` is a restart-only eager
reference; default operation enables idle deferral, not a function-disabling workaround.

External Audit adds nested capture stages and a default-off `MaterialStateProbe`. In a marked
isolated client it renders/readbacks a 64x64 FBO through production TextureTarget/BufferUploader:
idle-to-draw state, active-pass blend/depth/scissor/color-mask changes, a real custom RenderType draw,
FRONT/CW capture, and restoration after an intentionally throwing callback. Failed expectations
log FAIL even if normal client shutdown returns zero. The probe is never enabled in timed samples
or Prism. The initial, post-F3+T and final reads passed in PID 97684, alongside chest add/remove,
Sable iron/leaves/glass assembly/delivery, F3+A, cloud restoration and published-chunk verification.
Its seven stages took 110.11 world seconds, process 165.52 seconds, then all dimensions saved and
exit 0. The earlier PID 93884 basic regression also passed but did not enable the new pixel/Sable
switches; it is not presented as equivalent coverage. A failed preparation command created an
unused partial fixture, and an earlier UTF-8 script-edit failure left that basic run's switches off;
both are tooling errors, not game crashes or successful enhanced-probe results.

RelWithDebInfo native INSTALL and paired `distributedJar`/`verifyDistributedJar` passed. Audit build
executes 19 Java tests and two native collector tests successfully. Seven targeted MCVR CTests pass:
framebuffer contract/GPU, JNI coverage, FG UI GPU, material-faces CPU/GPU and diagram-surface GPU.
The standalone GPU tests supplement, rather than replace, the real client's changed UIModule path.
No full historical suite or fresh full-client validation-layer run is claimed. One unquoted Gradle
property invocation failed before task execution; the quoted corrected command passed and both
logs remain. The initial census collector used Release; detailed census and final candidate use
RelWithDebInfo. Only same-final-artifact comparisons below support the performance conclusion.

### Same-artifact performance

RTX 4080 SUPER / driver 616.92; 3840x2054, render distance 16, simulation distance 32, four bounces,
RR Balanced/model 6, Reflex 2, FG/vsync off, cap 260. Every case uses a fresh copy of the same city
fixture with fixed time/weather. Static captures are 30 seconds after 45 world seconds settling;
movement captures are 50 seconds, containing the same 40-second, 5 Hz, eight-block X / +/-35 degree
yaw route. Both movement runs reach 201 targets with zero skips. All captured frames are focused,
world-active, no GUI/failed-frame marker, and generated FPS zero. No quality/view-distance reduction.

| Execution case | Frames | Real interval mean / p50 / p95 / p99 (ms) | Face capture (ms/frame) |
| --- | ---: | ---: | ---: |
| idle-static-a | 638 | 47.046 / 46.490 / 52.551 / 56.614 | 5.597 |
| eager-static | 603 | 49.789 / 49.260 / 54.683 / 60.507 | 7.114 |
| idle-static-b | 652 | 46.076 / 45.681 / 48.967 / 55.722 | 5.526 |
| idle-route | 1032 | 48.462 / 48.598 / 52.165 / 57.526 | 5.924 |
| eager-route | 975 | 51.299 / 51.083 / 55.353 / 62.594 | 7.525 |

Static real intervals improve 5.5-7.5%, moving interval 5.5%. Face capture falls about 21-22%; the
restore portion falls 3.309 -> 2.377-2.394 ms in static runs. Overall Java marshaling is 11.868 ->
10.118-10.295 ms static, 12.568 -> 10.772 ms moving. Static frame-start-other is approximately
0.98-1.00 ms in all three runs; GPU main-queue duration remains 27.2-27.6 ms. Do not credit every
fraction of total-frame improvement exclusively to this one nested timer or predict the same gain
on another scene/GPU. Render-thread profiling overhead was held constant, not independently measured.

Static GPU-conversion input stays about 150,166-150,167 vertices/frame and logical upload 19.49 MB;
moving input is about 160,126/160,190 (animation and time-weighted sampling are not deterministic).
This does not remove geometry. Static WDDM dedicated peaks are 8,969.5-8,970.0 MiB; moving peaks are
8,976.0/9,246.1 MiB. Private process peaks are 14,738.7-15,555.0 MiB static and 15,854.5/16,324.2 MiB
moving. These five-second, whole-process samples include startup/SDK activity and are not allocation
peaks or proof of memory savings. The implementation introduces no persistent GPU cache.

`STATIC_COMPARISON.json`, `ROUTE_COMPARISON.json`, `RESOURCE_COMPARISON.json`, raw CSVs, per-process
logs and `CASE_VERIFICATION.json` retain distributions, identity and limits. `before`, `instrumented`,
`detailed` and `candidate` preserve separate complete tracked/nonignored-untracked source snapshots;
raw and UTF-8 CRLF-to-LF hashes are recorded. The final product is compiled from `candidate`;
subsequent maintenance text does not require a binary rebuild. Fifteen pinned submodules and the
ignored Streamline/NRD-generated build inputs were freshly checked and match the preceding batch.

Final tested identity (SHA-256):

- Radiance JAR: `AB94FD035B83E454B35C1FAD189DFED0C7C49E9A637F044FA0E0DB25AFDCC672`.
- core.dll: `FB2760E8D299C65A7F553837DAA1D133E2B56277AEF001342D2E94EFA443A7E6`.
- Audit JAR: `417428E7D73D428084A80D3976BEAB59EAE65E461EA6A92FD0F4454308F2433F`.
- Local matching PDB: `0822C00D121AE73E7873B80CCC691AC38D34C7ACDF937CAA7CA5A764F90B367E`.

Build/embedded/extracted/loaded DLL identities agree in the completed cases. No staging, commit,
amend, push or public release occurs. Deferred spring/diagram/plunger visuals, historical GPU-loss
root cause, other PT/FG configurations, large travel and long-duration/cross-GPU behavior remain
open. This measured CPU command reduction does not complete Java model/pose GPU migration.

### Final reference pixels, GUI check and deployment

The eager reference PID 7072 passes the same seven-stage/Sable/reload case and all three pixel
assertions (110.18 world seconds / 146.10 process seconds, exit 0). All six 64x64 RGBA readbacks
(initial, after reload, final in both modes) are byte-identical. This is exact parity for the test
pattern, not all world images. The native source verifier and block-entity-index assertions remain
separate from pixel evidence.

PID 72340 also opens the actual Create config factory, opens mechanical-piston Ponder, scrolls
forward/back successfully, captures each scene and closes normally (47-second world sequence,
81.86-second process). Agent inspection of retained config and Ponder-next images found the
expected controls, textured preview and blurred world background; it is not user approval or a
pixel-exact reference comparison. No Ponder PT was reintroduced. Nine processes on the final
artifact have distinct normal-save/exit evidence and no reported device loss. The two earlier
census processes use the earlier product and are not merged into that count. Existing invalid-map
saved-data warnings and Veil unsupported-layout/type errors remain visible; this optimization does
not claim to repair them.

The tested Radiance/Audit JARs above were deployed to the authorized non-portable Prism
`E:\Minecraft\PrismLauncherDev\instances\Radiance 1.21.1-neoforge\minecraft\mods`, replacing only
those two files. Before/after hashes are in `DEPLOYMENT.json`; old artifacts remain in `before`
evidence. Prism was not running and was not launched; its settings, worlds and other mods were not
changed. No computer-use session or further manual sample is required. Final source maintenance
changes after the compiled `candidate` are documentation only, recorded by `FINAL_CHECK.json`.
Full snapshots include the new Audit probe/Mixin and all preexisting necessary untracked sources.
Both staging areas remain empty and whitespace checks pass. No acceptance evidence was deleted.

## 2026-09-25 Persistent rigid baked-model prototype and producer attribution

Status: implemented / build-verified / automated-verified / runtime-observed within the cases below;
not general visual acceptance. Evidence: source, build, CPU behavior tests, bounded Vulkan compute
and ray-rule tests, real copied-city runs. No staging/commit/amend/push/public release.
Starting HEADs remain Radiance `6e9b97a53049fad833e673da647ac517efde5fe3` and MCVR
`ead8d47d80ad2bc9cf81740c29f0ec230b61f92f`; all incoming dirty/untracked optimizations are retained.

### Scope, measured selection and implementation

The user authorized attribution followed by one persistent-model prototype, not six parallel
optimization projects. The [bounded research follow-up](research/2026-09-24-medium-scene-cpu-hotspots.md#2026-09-25-producer-attribution-and-bounded-rigid-baked-model-prototype)
contains producer timings, overlap rules, source contracts and remaining costs. Actual city
ItemFrameRenderer input (about 56,220 vertices/frame) led to the rigid baked-model bulk-quad target;
no renderer/block/texture-name whitelist was introduced. ModelPart skeletons are not rewritten.

Product areas: `RigidModelCapture`, ordinary EntityProxy/StorageVertexConsumerProvider capture,
block/item model entry Mixins and resource reload; native `Entities` local model resources,
WorldPrepare instance/history assembly, submission commit/retirement, JNI and shared ray-cone LOD.
The renderer/model/quad/tint callbacks still execute. Stable local quad recipes avoid transformed
PBR vertex expansion, native conversion/upload and BLAS rebuild on hits. Current transforms,
visibility/appearance and real RenderType face callbacks remain. Unsupported wrappers/poses and
special consumers use the original path; no second complete ModelPart expansion is introduced.
Cache limits, epoch invalidation, texture-owner check-and-use and in-flight retirement are explicit.
The restart-only `-Dradiance.rigidModels=true` prototype is **off by default** pending wider coverage.

A direct correctness gap found during evaluation was fixed: per-owner draw-count changes can shift
ordinals, so the final history identity includes owner, draw count and ordinal; matching native
model identity is also required. Missing/replaced draws cannot inherit another identical part's
old transform merely because the ordinal matches. Maximum persistent draws per owner is 1,024;
excess draws retain original expansion. This does not solve general semantic identity for arbitrary
renderers that reorder indistinguishable same-count draws; their existing order-based history
contract remains a limitation, not a claim of universal temporal equivalence.

The shared LOD helper now uses transformed triangle edges with the world-space ray cone. This is
required when moving scale from expanded vertices into an instance matrix and also affects direct
users of the same helper. It is applied to both A and B, not credited as a persistence speedup.
No visibility deletion, quality reduction, global GPU idle, unrestricted AS update, new stable
world origin, scene-table rewrite, scheduling rewrite, arena, PTLAS or SER was added.

### Automated verification and failed attempts

Final GAME tests: 206 passed. Bootstrap: 10 passed, two skipped. Audit: 21 passed plus its two native
collector tests. The seven rigid Java tests exercise the actual quad recorder/fallback, mutable input
ownership, appearance changes, supported/refused poses and history identities. The native lifecycle
helper is used by production and tests failure, abandoned recording, actual submit and repeat frames.

RelWithDebInfo native build/INSTALL and paired package, JNI, shader and Maven-development-artifact
checks passed. MCVR CTest initially passed 61/63: two supplemental shader-source contract tests still
expected the old LOD helper spelling. After updating those two expectations, their 2/2 retest passed;
the real new rigid-model GPU test and existing face/GPU tests had already passed. This is a complete
successful union of that suite plus targeted retest, not a claim that its first invocation was 63/63.
The new GPU math test covers 263,168 scale/mirror/rotation/nonuniform/degenerate-UV cases; it does not
by itself prove complete PT image parity.

Preserved failed tooling: an unqualified Gradle `--tests` filter also reached Audit (no matching test),
and `verifyDistributionJar` was an incorrect task spelling. Correct qualified/full test tasks and
`verifyDistributedJar` passed. An early packaging mistake detected by the embedded-DLL assertion
still continued into an invalid mixed Java/old-native package because the shell sequence lacked an
exit guard. PID 50496 (`verify-1`) crashed during startup in `sl.reflex.dll`, before world entry;
its hs_err/logs remain. Artifact mismatch is confirmed, **its causal role in that Reflex crash is not**.
The invalid run is excluded from correctness/performance evidence. Preparation now validates hashes
before copying and every build/prepare stage stops on failure. No automatic identical crash retry.

### Source and artifact chain

Non-portable evidence root: `Radiance/run/model-persistence-20260925/`. `before`, `prototype-3`,
`prototype-4` and `prototype-5` retain separate full tracked/nonignored-untracked source archives,
raw hashes and UTF-8 CRLF-to-LF normalized hashes, configuration and matching symbols. Partial failed
snapshots are not promoted to valid identities. `DEPENDENCIES.json` freshly rechecks all 15 pinned
clean submodules and 117 ignored Streamline/NRD header/shader build inputs. Runtime DLLs/SPIR-V and
shader archives are hashed inside each packaged JAR. No retired replay product was reintroduced.

Final product source is `evidence/prototype-6/MANIFEST.json`; `prototype-7` adds only the diagnostic
correction described below and maintenance text. Product JAR/DLL/PDB hashes are unchanged:

- JAR: `C8DA1FCC146CFEFC74526195D343BF277602481FA374BD46B686529FB8450B39`.
- core.dll: `B015D09C3E8306038BC126EC50A3A412C1B6C3AD882F3311D04110333BBAD5A6`.
- Audit used for final product lifecycle/A/B: `6B016E79EE5A850A197C217AFDEC0661ED8F21C39708B4FDCD58A84B3AE715A9`.
- Corrected census/deployment Audit: `A277EE2450A79FF8A6DD93AF783D9886B5C39C50E2719E3728FF1530B8C77E4D`.
- Matching local PDB: `2738513119D22A58F685FB68A7529E9196A7BEC19EB8255AA471CBE5542FC8B9`.

`prototype-4` formal repeated comparisons precede the draw-count history correction and use JAR
`04CAA4DEA99059C2BE17747CE0EAF3A2292216C366F232C5AB1B8854706280FC` and Audit
`68BD114B35FE6646C46AED9EA781FA1B3ADDCD835765FF7399A9E455BF655877`, with the same native DLL.
Its results are retained separately, not merged into final-artifact regression claims. The earlier
`verify-2` sampled 4,096 draws on `prototype-3`; it is not substituted for final regression evidence.

### Final lifecycle and geometry checks

The intermediate `prototype-5` JAR `22EFFFDCBDD925D1FB76C9ACF6058E3B99517C44E4470508D18F462936D75ACD`
and Audit `936B09FCDF14069546387015CE7D0DF03090DAF156076F4DD0B3C5CE1ADA3F6C` passed
`lifecycle-final`, PID 88040, in 134.00 process seconds, exit 0. Its evidence remains in
`LIFECYCLE.json`; it is not labeled as the final artifact. Final changes additionally snapshot the
recorded pose before the original renderer mutates its PoseStack, restrict eligibility to exact
vanilla SimpleBakedModel inputs (custom subclasses retain their callbacks/fallback), and fix the
diagnostic's delayed rigid-face accounting so it cannot steal legacy producer labels. Unresolved
rigid material ownership is explicitly `unattributed/rigid-material`.

Final `prototype-6` case `lifecycle-verified`, PID 96068, runs 133.49 process seconds and exits 0.
Eight stage reports pass after actual item-frame rotation, cutout/glass substitution, glint/glowing
fallback, empty/invisible state, restoration, F3+T, deletion/replacement and F3+A. Expected item,
rotation, invisibility and glowing state are checked after delivery to the actual client. Cache IDs
from before reload are absent afterward. Each stage samples original PBR expansion versus local
geometry transformed by its current matrix; all observed position/normal errors are zero and other
material bytes match exactly. `VERIFIED_LIFECYCLE.json` indexes its eight stages (4,096 initial
draws / 493,044 vertices, then 752-1,015 sampled draws per stage) and practical limits.

This is CPU input parity plus real lifecycle evidence, not per-pixel reflection/shadow/motion-vector
acceptance. The process records saving all dimensions, Streamline/NGX shutdown and empty delayed
resource list, then normal exit. It does not establish a historical GPU-loss root cause, dangerous
G3 injection success, long-duration stability or cross-GPU compatibility. Fresh processes and resource
reload are tested; a live cross-dimension transition is not claimed by these stage logs.

### Same-artifact static and moving comparison

Final `prototype-6` A disables only `radiance.rigidModels`; B enables it. Both retain idle-state
optimization, GPU PBR conversion, chunk metadata cache and the corrected ray-cone shaders. Fresh
copies of the same Newport City fixture use 3840x2054, 16 view distance, 32 simulation distance,
four bounces, RR Balanced/model option 6, Reflex 2, FG/vsync off and the same mods/settings. The
profile records focus throughout, zero SDK generated FPS and no dropped/failed samples. Heavy
producer census and dual-path verification are disabled in both formal variants.

After 45 seconds of world warmup, static samples last 30 seconds. Moving samples last 50 seconds
and contain the same 40-second route (201 targets, zero skipped, eight-block X excursion and
plus/minus 35-degree yaw, returning to the original pose). Separate processes never compete for
the GPU. Frame-start intervals are real frames, not FG/display FPS; CPU/GPU inclusive timers are
not added together as savings.

| Final case / PID | Intervals | Mean ms | p50 ms | p95 ms | p99 ms |
| --- | ---: | ---: | ---: | ---: | ---: |
| static A / 80532 | 630 | 47.615 | 47.080 | 51.751 | 57.372 |
| static B / 42848 | 698 | 42.952 | 42.638 | 46.920 | 51.661 |
| route A / 100276 | 1,001 | 49.935 | 49.796 | 54.772 | 59.395 |
| route B / 100176 | 1,093 | 45.713 | 45.392 | 51.032 | 56.475 |

The final product pair reduces mean real interval 9.8% static and 8.5% moving. Earlier independent
`prototype-4` pairs showed 6.8-9.7% static and 3.9-7.7% moving improvement; their different Java
history/diagnostic revision is retained separately. The intermediate `prototype-5` static A had
additional between-frame tail gaps (mean 48.354, p95 65.948 ms), which are not all credited to this
optimization. `PROTOTYPE4_COMPARISON.json`, `PROTOTYPE5_COMPARISON.json` and
`VERIFIED_COMPARISON.json` retain per-case raw/distribution links rather than pooling artifacts.
This is bounded repeat evidence, not a universal speedup or formal confidence interval.

| Final static metric, per frame | A | B | Interpretation |
| --- | ---: | ---: | --- |
| Java/native PBR input vertices | 150,180 | 91,163 | 59,016 persistent vertices reused; about 39.3% less expansion |
| Logical GPU input upload bytes | 19,492,556 | 11,828,146 | About 7.66 MB less; not measured PCIe traffic |
| Dynamic BLAS inputs | 1,040.8 | 808.6 | Persistent models are already built in steady state |
| Persistent instances / cached models | 0 / 0 | 490 / 132 | No new persistent geometry upload/build events in this complete steady capture |
| Total TLAS instances | 9,240.8 | 9,498.6 | About 258 extra instances; cost is not hidden |
| Java ENTITIES inclusive / self ms | 13.149 / 6.185 | 10.756 / 4.869 | Inclusive contains nested stages; do not add both |
| Geometry marshal inclusive / self ms | 10.237 / 3.596 | 9.048 / 2.664 | Producer work remains |
| Face capture inclusive ms | 5.686 | 5.419 | Callbacks retained; not a new material-state optimization |
| Native conversion inclusive ms | 3.032 | 1.971 | Conversion/format/queue scopes overlap |
| Entity metadata ms | 1.843 | 1.611 | No global scene-table migration |
| Additional rigid JNI/queue time ms | 0 | 0.176 | Extra per-instance submissions |
| Entity BLAS GPU ms | 1.473 | 1.098 | Nonblocking retired query |
| TLAS GPU ms | 0.207 | 0.194 | Run variance; route increases 0.197 to 0.204 ms |
| Main queue GPU interval ms | 26.037 | 25.217 | Excludes separate queues and SDK-private work |

Sampled static process dedicated GPU peaks are 9.408/9.399 GB and private committed bytes
16.276/15.950 GB; route peaks are 9.412/9.405 GB GPU and 15.695/15.578 GB private. These are
five-second WDDM/process samples including startup, not exact peaks or proof of reduced memory.
All four final comparisons save/close and exit 0 in approximately 110/112/130/130 process seconds.

Decision: retain the bounded, default-off prototype; the recurring work and total interval fall
beyond the observed repeat spread, with a small extra instance cost. Controlled expansion is worth
considering only after its next producer's actual capability/consumers are established. This does
not authorize skeleton animation caching, general scene-table work or feature/quality reduction.
Optional observation steps and a hash-checked no-build launcher are in the task's isolated
`ACCEPTANCE.md`; no new user performance sample is required. Prism/production data are unchanged.

### Diagnostic correction after the formal comparison

`verified-census`, PID 31312, exposed an Audit-only omission: the rigid capture wrapper selected an
explicit unassigned owner but did not enter the face-count/timing scope. Product rendering and
global FrameProfiler face timers were correct; the producer CSV omitted 490 rigid face captures
per frame rather than charging them to another renderer. Its 44.048 ms interval is a heavy-census
observation, excluded from formal performance samples. Preserve the CSV as incomplete face attribution.

`prototype-7` adds the missing scope only when that census is active; no product code, shader,
JAR, native DLL or PDB changes. Audit build and its 21 Java tests/two native collector tests pass
again. The earlier formal A/B and lifecycle retain their Audit-6 identity; they are not relabeled
as executions of the corrected Audit. Ordinary heavy-census-disabled measurements do not execute
the newly added `ProducerCensus.face` call. Exact uninstrumented observer overhead remains unmeasured.

Corrected real-client census `census-corrected`, PID 87436, exits normally after 109.88 process
seconds with all dimensions saved and the exact final DLL loaded. Its 665 profiled frames record
exactly 490 rigid face captures/frame (1.911 ms), explicitly unassigned to a concrete producer;
ordinary chest counts remain 229/frame and item-frame legacy counts 12/frame. Nothing steals the
legacy attribution queue. No dropped samples are reported. The heavy census's mean interval is
45.158 ms versus the separate lightweight static candidate's 42.952 ms: it is visibly intrusive,
not a formal speed comparison or an exact observer-overhead measurement. Formal A/B has it off.
`CENSUS_CORRECTION.json` preserves this scope and artifact identity.

The isolated optional `manual-observation` copy contains the unchanged product and corrected Audit;
its no-build launcher is syntax-checked and verifies both mod hashes before startup. It is not
automatically launched or visually approved. `evidence/final/MANIFEST.json` retains all necessary
tracked/nonignored-untracked source and build identities; `FINAL_CHECK.json` proves only maintenance
text differs from prototype-7 and that product sources are unchanged from prototype-6. No new backup
refs, commits, staging or cleanup of historical evidence occurred.

## 2026-09-25 Prism handoff for the rigid baked-model prototype

Status: deployed; user runtime/visual acceptance pending. Evidence: artifact/configuration identity
only; no new game run, rebuild or Git operation. This supersedes the preceding optional repository
manual-launch handoff: the user requires the authorized Prism instance for actual observation.

The unchanged final Radiance product and corrected Audit from the
[prototype entry](#2026-09-25-persistent-rigid-baked-model-prototype-and-producer-attribution) were
copied to `E:\Minecraft\PrismLauncherDev\instances\Radiance 1.21.1-neoforge\minecraft\mods`
(non-portable). Both destination hashes and the packaged core.dll hash match that entry.
No matching client was running during deployment. The instance now overrides the previously empty
inherited JVM arguments with `-Dradiance.rigidModels=true`, so manual startup actually enables the
otherwise default-off experiment. Heavy census, scripted mutations and automatic shutdown remain off.
Other mods, game settings and worlds are untouched; the agent did not launch Prism or Minecraft.

Machine record: `run/model-persistence-20260925/evidence/prism-deployment-20260925-081723/DEPLOYMENT.json`.
The prior instance.cfg is retained there for this acceptance's configuration rollback. Prior mod
bytes already match the retained `evidence/before/` artifacts, so no duplicate binary backup was
created. Loaded-runtime identity and user observations must be checked after the user's launch;
deployment alone does not extend the previous automated correctness or performance evidence.

## 2026-09-25 Bounded Prism visual feedback for persistent models

Status: runtime-observed; visually-accepted as a bounded user smoke observation, not every checklist
item. The user reports no apparent issue after receiving the targeted rotation, transparency,
special-layer, reflection/shadow and reload observation list; individual actions are not inferred.
Product/Audit identity is unchanged from the preceding Prism handoff.

Read-only evidence captured after process 44340 ended confirms installed JAR/Audit hashes and the
matching extracted core.dll (created at 08:18:20). There is no fresh live module snapshot or launcher
exit code. The log records Newport City entry at 08:20:06, client resource reload with
SPBR-22-reverse-1.21.1.zip at 08:23:37, all dimensions saved at 08:24:20.802, then Minecraft stopping
and Audit closure at 08:24:21 (zero unknown intents/dropped records). No device-lost message is
present in this latest.log. Existing unsupported Veil shader/layout warnings remain, so this is not
a claim of an error-free log. The stale 06:38 radiance-perf.log is excluded from this observation.

Non-portable evidence: `run/model-persistence-20260925/evidence/prism-observation-20260925-44340/`.
The instance retains the rigid-model opt-in. Its observed Java is 25.0.4, distinct from the automated
Java 21 comparison; no new performance claim is made. Detailed per-item visual parity, live dimension
changes, long-duration stability, native shutdown internals, historical GPU-loss root cause and
public redistribution remain outside this acceptance. No product, instance or world changes,
rebuild, staging, commit or push occurred while recording this feedback.

## 2026-09-25 Screen-effect static audit and missed Veil underwater overlay

Superseded symptom report: the user later retracted the missing-texture observation after retesting;
see the [dated correction below](#2026-09-25-veil-underwater-report-retracted-and-two-deferred-visual-tasks).
The original report and audit boundary are retained as history.

Status: investigating; user-reported visual failure remains unresolved. Evidence: bounded source,
dependency bytecode/shader inspection, and the user's observation; no new client run, image capture,
automated test, build or deployment in this audit/recording step.

Scope: the dirty Radiance worktree above `6e9b97a53049fad833e673da647ac517efde5fe3` and paired MCVR
above `ead8d47d80ad2bc9cf81740c29f0ec230b61f92f`, Minecraft 1.21.1 / NeoForge 21.1.251 sources and
Veil 4.3.2 bytecode/resources. Existing optimizations and tests are preserved. The current handoff
identity is retained in the local historical evidence at
`run/model-persistence-20260925/evidence/prototype-6/` (not a source artifact of this audit);
this feedback has not been independently tied to a new process/time/configuration capture and must
not be assigned to PID 44340 merely because that earlier log is available.

### User correction and audit limitation

After the static audit, the user explicitly reports: **Veil's underwater texture is currently
missing**. The agent did not find this visible failure in the code-only audit. Record the symptom
as reported, with root cause and affected configurations still unconfirmed; do not relabel it as
normal, visually accepted, or fixed. The earlier broad "no apparent issue" persistent-model smoke
feedback does not cover this screen effect. Historical successful overlay observations remain
historical and do not override the new report.

The audit established an interception route and uniform forwarding for Veil's actual water/block
draws, not the visibility of their final output. Its coverage judgment stopped too early: it did
not establish that the same frame's captured texture/parameters survive native recording, uniform
updates, descriptor/sampler selection, HDR composition and subsequent draws. This is an evidence
gap in the audit, not proof of any one failure mechanism. Tests of parameter extraction or a
`BACKEND_RECORDED` audit event cannot certify that the underwater texture appears on screen.

### Bounded static findings retained for follow-up

| Area | Finding and evidence boundary |
| --- | --- |
| Night vision, conduit brightening, darkness pulse | `LightmapTextureManagerMixins` computes these lightmap adjustments, but the corresponding strength getters have no PT consumer and native `LightMapUBO` is only declared. PT main-surface brightening/dimming is incomplete; this does not mean fog or remaining raster consumers are unaffected. |
| Blindness/darkness fog | `WorldRendererMixins` preserves vanilla fog input, but both PT world shaders select vanilla fog by camera submersion; air remains on ordinary atmospheric/volumetric branches. The Advanced volumetric pass returns empty for these effects. Short-range status-effect fog is not established as equivalent. |
| Combined camera effects | Moving bob/hurt transform B into the view changes vanilla P * B * N * V into P * N * B * V when nausea transform N is active. Individual inputs remain present; combined visual equivalence is not established. |
| GameRenderer post effects | `loadEffect` replaces loading with three fixed native effects. Other resources are explicitly skipped; `EntityPostEffect.resolve` compares the path without namespace, so a foreign same-path resource can be misidentified. Resource-pack post-chain changes are not automatically translated. |
| Veil water/block adapter | Actual calls/uniforms are captured, but non-underwater textures using the same shader are treated as block overlays, RGB is reduced to the red component, and the block route does not retain alpha. Built-in Veil calls fit these assumptions; arbitrary reuse is not proven safe. This alone does not explain the reported missing built-in water texture. |
| Veil first-person post stage | `VeilFirstPersonRendererMixins` cancels bind/unbind, including the original post-pipeline invocation. The default depth-copy operation may be replaced by PT, but custom effects depending on that stage are not thereby translated. |

Vanilla GUI overlays (pumpkin, powder snow, spyglass, portal, sleep, vignette), totem animation,
camera distortion inputs, menu blur, the three spectator post effects, and NeoForge camera/fluid
hooks have retained or replacement entry paths. This is an inventory, not pixel parity or proof
that each effect is visible. Veil's water/block replacement was checked separately from vanilla;
installed-mod presence was not accepted as behavioral evidence.

Next investigation must prioritize the reported underwater failure and trace one actual Veil draw
through the final output, contrasting Veil-active and vanilla-direct routes under matched settings.
Check texture visibility separately from fluid fog/color, retain the original Veil UV/alpha contract,
and account for reload and later overwrites. Any future acceptance needs final-frame evidence;
do not close it with an available callback, a successful parameter test or the older generic smoke.
This entry corrects the static coverage inference only; no product code or acceptance artifact was
changed, and no staging, commit, amend or push was performed.

## 2026-09-25 Veil underwater report retracted and two deferred visual tasks

Status: superseded for the missing-texture report; deferred for the two requested work items.
Evidence: user visual retest and bounded static investigation, not a new automated/client test.
Applies to the unchanged product worktrees above Radiance `6e9b97a53049fad833e673da647ac517efde5fe3`
and MCVR `ead8d47d80ad2bc9cf81740c29f0ec230b61f92f`.

The user corrects the earlier report: Veil's underwater texture **is present**, but is sufficiently
faint to become almost imperceptible in some scenes. Close the missing-texture investigation as a
retracted observation, not as a code fix. No alpha, brightness, texture or composition change is
justified by that observation alone. This does not certify exact parity with Veil's original output,
nor close the separate screen-effect findings in the preceding audit. No process/time identity or
new capture was supplied for this retest; do not attribute it to the earlier PID 44340 evidence.

Before the correction, the directed source investigation checked Veil 4.3.2's real water call,
`VeilScreenEffectAdapter`, `HdrCameraEffectRenderer`, the native camera-effect writer and tone
mapping. Veil writes its vec4 CPU shadow before `upload()`, so cancelling the OpenGL upload does
not by itself erase those parameters. Its built-in water draw supplies alpha 0.1 and a brightness
multiplier; the native HDR path additionally samples texture alpha. `fuseWorld()` records the
composition request rather than immediately executing the normal frame's world pipeline, so its
position before overlay capture is not by itself proof of late capture. None of these static facts
proves why a particular scene looks faint. No confirmed disappearance mechanism or repair resulted.

At the user's request, record two later work items in the existing
[PT visual roadmap](ROADMAP.md#pt-visual-correctness-work-packages):

- **labPBR grazing-angle black correction, referencing Sundial.** Consolidate with the existing
  shading-normal research instead of creating a duplicate implementation plan. Revalidate the
  suspected normal/hemisphere/energy paths against the eventual source snapshot.
- **Vanilla-cloud rewrite.** Keep it distinct from the older cloud-fog-only proposal. The concrete
  replacement design, compatibility contract and performance acceptance remain to be established.

Both are deferred; this request records them and does not implement them. The
[research addendum](research/PT_VISUAL_AND_PERFORMANCE_INVESTIGATIONS.md#2026-09-25-deferred-work-requests)
preserves their relationship to the historical investigation. Only maintenance documents changed;
no product/Audit source, instance setting, world or artifact changed, and no build, runtime test,
deployment, staging, commit, amend or push was performed.

## 2026-09-25 bounded ModelPart persistence experiment

Status: implemented/build-verified/automated-verified and bounded runtime-observed; **not a measured
performance win, not promoted or visually accepted**. Preserve the first baked-model prototype and
its earlier evidence. This is a separate opt-in extension (`radiance.rigidParts`, also requiring
`radiance.rigidModels`), not a replacement for the first prototype or an enabled-by-default feature.
HEADs remain Radiance `6e9b97a53049fad833e673da647ac517efde5fe3` and MCVR
`ead8d47d80ad2bc9cf81740c29f0ec230b61f92f`; all inherited dirty work remains.

The remaining producer evidence selected vanilla `ModelPart` rigid parts: ArmorStand model expansion
was about 0.95 ms with 35,304 vertices/frame, Boat about 0.23 ms with 7,200. Chest/Bed remain original:
their wrapped consumers and block-entity capture are outside this prototype's ownership contract.
Eligibility follows exact ModelPart/Cube/PBR capabilities, ordinary entity ownership and equivalent
uniform/mirrored transforms; no renderer/texture-name material whitelist is introduced.
`PartModelCapture` and `ModelPartPersistentMixins` retain original model traversal, animation,
visibility and callbacks. Stable local polygon inputs are compared before use, not transformed and
hashed every frame. Local mesh resources include current color/light/overlay, texture generation and
layer. Mutable inputs invalidate the recipe; pose-only changes submit a current instance transform.
Unsupported poses, consumers, outlines/glint wrappers and raster previews use the original path.
The existing native rigid-model JNI/BLAS ownership path is reused without a native ABI change.

Part/layer/owner history has its own namespace; changing another part cannot shift this identity.
Per-provider/layer face capture preserves the original grouped-layer callback boundary, never caching
a face rule across frames. The part recipe cache is bounded to 512 entries/16 MiB of local vertex
storage; source count is 512, layer identities at most 64 per source, with inactive expiry and original
fallback at capacity. Current-frame recipes are not evicted. Native resources remain independently
retired by the existing frame retainer. Directed review also found that the first and second Java
recipe caches could pin the last world until a later entity frame. Both now clear on disconnect and
normal client close, in addition to reload/world changes; GPU ownership is not prematurely freed.

Audit now maps delayed rigid draws through their real storage providers to producer rows, instead
of charging them to the next ordinary draw or hiding all of them as unknown. Persistent referenced
vertices are separate from generated/uploaded bytes; native GPU timing remains global. Default-off
`partVerify` compares sampled output with actual original `Cube.compile`. `partLifecycle` uses only
explicitly marked disposable worlds; it is not a normal product path or performance measurement.

### Measurement and decision

Local, ignored evidence: `run/model-parts-20260925/` (non-portable full root
`D:\Workspaces\Repositories\GitHub\RecRivenVI\Radiance\run\model-parts-20260925`). `before` and
`candidate-1` manifests archive both repositories including necessary untracked sources, raw SHA-256
and UTF-8 CRLF-to-LF normalized hashes. `PERFORMANCE.json`, eight per-process raw profiles, options,
launch inputs, sampled memory, loaded DLL hashes and route logs preserve the comparison.

Both sides enable the existing baked-model path; only the new part path differs. Each process starts
from the same immutable city fixture, 3840x2054, 16 chunks, RR Balanced, jitter/SHARC and Reflex settings
unchanged, FG off, `MCVR_IDLE_UI_STATE=1` on both sides. Each warms for 45 seconds after entering the world, then records 30 seconds static
or 50 seconds containing the fixed 40-second/201-step translation-and-turn route. A/B/B/A order is
used separately for both cases, two processes per side. Every recorded frame is focused, in-world,
no screen, actual resolution/view distance unchanged, with zero SDK generated FPS. All route runs
report 201 steps and zero skipped steps; all eight processes save and exit 0, with no dropped profile
batches/events. Detailed census/dual-path checking is off in these eight samples. Hardware recorded
locally is RTX 4080 SUPER, driver 616.92; these finite samples are not a stability or hardware survey.

| Real frame interval, equal weight per process | Baseline | Part candidate |
| --- | ---: | ---: |
| Static mean / p95 / p99, ms | 44.47 / 48.20 / 51.58 | 44.77 / 48.63 / 53.94 |
| Route mean / p95 / p99, ms | 46.94 / 50.97 / 55.95 | 47.58 / 51.77 / 57.31 |

Mean differences are +0.69% static and +1.36% route, within the limited run-to-run spread and in the
wrong direction for a speedup. Quantiles above average each process's quantile; they are not a
confidence interval or a pooled tail estimate. Static dynamic PBR vertices fall 91,163 to 46,810 and
logical GPU upload ranges 11.83 to 6.08 MB/frame, but rigid instances rise 490 to 2,278, total TLAS
instances about 9,499 to 11,163. Entity BLAS GPU time falls 1.107 to 0.928 ms; TLAS time rises 0.189 to
0.214 ms, native rigid queue time 0.182 to 0.720 ms, entity metadata 1.648 to 2.347 ms. These nested and
overlapping costs must not be summed into an invented frame saving. Main-queue interval drops only
24.754 to 24.466 ms; material capture remains 5.585 versus 5.687 ms. Steady complete captures have
zero new rigid-model/BLAS events, with 132 versus 612 resident models. More tiny persistent instances
have displaced work rather than producing a useful overall win. Five-second process GPU-memory
samples stay around 9.4 GB; CPU heap/private peaks fluctuate, not proof of lower total memory.

Decision: retain a bounded, default-off experimental path and its evidence, **do not expand or enable
it by default**. The first baked-model improvement is not undone. Further work would need to reduce
the instance/submission granularity cost, with a new bounded design and comparison; do not simply add
more ModelPart consumers, GPU features or claim the approximately halved vertex count as a speedup.

### Verification, correction and final artifact

This round ran GAME 211/211, bootstrap 10 passed/2 skipped, Audit Java 21/21, its two native collector
tests, and the affected MCVR CTest subset 5/5 (entity conversion GPU, material faces CPU/GPU, rigid
model GPU math, rigid build lifecycle). The five new part tests exercise actual Cube.compile parity,
mutable geometry/UV/normals, history namespace and live/expired layer identity bounds. Full matching
local packaging, distributed-JAR and GAME Maven identity verification passed in RelWithDebInfo.
An unquoted PowerShell `-Pmcvr.configuration` invocation failed task parsing before build execution;
`build-2-retry.log` preserves the successful quoted retry, not a concealed source/test failure.

`probe-1` compares 4,096 draws/101,640 vertices against actual Cube.compile with zero observed
position/normal difference and exact material bytes; it is CPU geometry evidence, not GPU hit/visual
parity. The first lifecycle process (PID 53980) passed pose, visibility flags, equipment, glow state,
reload, replacement and F3+A stages but **failed its exit probe**: the diagnostic omitted
ClientLevel.disconnect and reentered on Minecraft's nested tick. It never demonstrated successful
saving/close. A thread dump and the actual PauseScreen/Minecraft bytecode established the diagnostic
error. After a normal window-close request failed, only that verified disposable process was stopped
(exit -1); retain `forced-stop.json`, first logs and thread dump. No GPU-loss root cause is implied.
The probe was corrected to stop itself before matching the vanilla level/client disconnect order.
`lifecycle-retest` then checks target-owner geometry per non-glowing stage, reload identity change,
replacement, F3+A, and both Java recipe caches empty after disconnect, plus normal save/exit. The glow
stage verifies synchronized fallback state, not its final outline image. `probe-baseline-final` gives
a fresh bounded census/default-part-off runtime on the final package. Results are not substituted for
new user visual acceptance, a live dimension test, arbitrary Cube.compile-modifying Mixins or long
stability. Historical device-loss and license gates remain independent.

The timed prototype used JAR `F088DDB2FA6276598B737D49F3D70620A94638B5827BBD24D1537E5F9466F939`
and Audit `588B988A8CF9AAC30467584E864DCF8CB064AE5AF70A59E7D80EDE979E30BCC1`. Subsequent bounds,
cleanup and diagnostic corrections are **not retroactively timed** as this same version. Final
`candidate-3/MANIFEST.json` fixed the intermediate product/test sources and these isolated artifacts (superseded by the final closeout below):

- Radiance JAR: `C397C5A7ABDE8ED08483F55AD3D1CFD9D8731C0BD5C176B98FC4B46731ADCD78`.
- Audit JAR: `95D785AA99A0AF65DA7E4CD0A3E0421C9F5E01D405601599F96B8A159876C534`.
- core.dll (unchanged this round): `B015D09C3E8306038BC126EC50A3A412C1B6C3AD882F3311D04110333BBAD5A6`.
- Matching local PDB: `2738513119D22A58F685FB68A7529E9196A7BEC19EB8255AA471CBE5542FC8B9`.

Build DLL, embedded JAR DLL and each final test's extracted/loaded DLL are hash-checked separately.
The final documentation overlay/manifest records only later maintenance changes, without rebuilding
or claiming new product tests. `ACCEPTANCE.md` preserves focused optional visual checks. No Prism
files/settings were changed, no user visual pass was filled in, and no staging, commit/amend, push
or binary publication occurred. The new prototype is not recommended for normal enablement.


### Final default-off allocation correction

Final candidate review found two newly introduced IdentityHashMap allocations on every storage
provider, including ordinary/default-off paths. The part-only material/occurrence state is now lazy,
created only after part eligibility and source identity checks, and dropped on close. A sixth part
regression constructs real StorageVertexConsumerProvider instances and checks absent default state,
shared opt-in state and close cleanup. This removes an unintended cost from keeping the experiment
disabled; it is not a claim that the opt-in experiment now has a measured net speedup.

`build-final.log` reruns affected full Java/bootstrap/Audit builds and package/identity checks:
GAME **212/212**, bootstrap **10 passed/2 skipped**, Audit Java **21/21**, Audit native collectors
**2/2**. Raw XML and counts are retained in `evidence/final-test-results.zip` and `FINAL-TESTS.json`.
The prior native subset remains this round's 5/5 evidence; no native product changed or redundant
full CTest run was claimed. The measured eight-run comparison still belongs only to candidate-1.

`evidence/candidate-4/MANIFEST.json` and both ZIPs capture all tracked plus necessary nonignored
untracked source/build inputs. Raw SHA256 and UTF-8 CRLF-to-LF normalized SHA256 are recorded;
non-UTF8 bytes are unchanged. Final artifact identities supersede candidate-3 for further testing:

- Radiance JAR: `89FDB75B828F06B565133E8818D0DCF74A0D00B2CCACF06080E2EFD6D564BA63`.
- Audit JAR: `7294EBE93029324828306E004C38AFEC9371D8961680C3F86C2CFCFDDD0EE7CA`.
- core.dll and PDB: unchanged from the exact hashes above.

Final candidate-4 runtime: `lifecycle-final` PID 4116 ran 126.73 seconds and exited 0; the six
non-glowing target-owner comparison checkpoints had zero observed position/normal differences and
exact appearance. Its lifecycle assertion passed after pose/flags/equipment, glow fallback state,
reload, replacement, F3+A and empty caches after disconnect. All three dimensions saved.
`probe-default-final` PID 82520 ran 109.67 seconds, completed its bounded default-part-off profile,
saved all dimensions and exited 0. Both loaded the expected DLL; neither showed device loss.
`evidence/FINAL-RUNTIME.json` indexes those precise checks. The first diagnostic failure remains in
its separate directory and has not been relabelled a pass. This is finite automated evidence, not
visual acceptance, a dimension-change test or long-term stability proof. Prism remains unchanged.

## 2026-09-25: Split the non-performance source checkpoint

Status: source separation verified; build-verified and automated-verified within the stated scope.
Evidence: complete working-file backup/hash comparison, archived diagram source, isolated build,
test XML and candidate diffs. This is source-history maintenance, not new gameplay acceptance.
Applies to the split above Radiance `6e9b97a53049fad833e673da647ac517efde5fe3` and MCVR
`ead8d47d80ad2bc9cf81740c29f0ec230b61f92f`.

### Scope and preservation

Amend the diagram producer/presentation/raster corrections, their opt-in parity probe and tests,
screen-effect audit and retracted underwater report, and deferred spring/plunger/labPBR/cloud
records into `Initial port`. Native product content is unchanged; its amendment contains only
corresponding ledger records. Shared Java lifecycle/Mixin files and the ledgers/roadmap are split
by content. No working product file is rewritten during the split.

Leave frame/producers profiling, host preparation, submission strings, sampler fast paths, owned
staging packing, GPU conversion, published-chunk metadata, idle-raster deferral and persistent
baked/ModelPart work, with their necessary correctness tests and evidence, uncommitted for the
later `Performance Optimization Test V1`. In particular, persistent-instance LOD/history/lifetime
corrections remain with that implementation. The ModelPart experiment stays default-off; its
negative timing result is neither removed nor relabeled as a speedup. The bounded Git exception
is recorded in [maintenance policy](DOCUMENTATION_POLICY.md#git-history-and-backup-policy).

### Isolated verification and evidence boundary

All 29 selected Java/Audit source/test/registration changes match the pre-performance diagram
handoff after UTF-8 CRLF-to-LF normalization. The complete native product matches the old native
HEAD. Tracked wrapper/build inputs are included in the isolated validation tree; all necessary
new diagram files are included. The actual working product files, including untracked performance
sources, remain byte-identical to the pre-split inventory.

This round executes GAME tests: 196 passed / 1 skipped (native GPU coverage harness not installed
in the isolated build); bootstrap: 10 passed / 2 GPU opt-ins skipped; Audit: 9 passed; original
Audit native collector: 1/1 passed. Real shader translation/compilation cases run within GAME.
The distribution JAR, runtime index, Audit payload and Maven development artifact checks pass.
A separate public-distribution gate check rejects the unapproved NVIDIA payload as expected.
These are new split-tree results, not the historical 197-pass diagram run or later performance
suite. MCVR core/shaders are not rebuilt; the isolated package reuses the hash-verified historical
diagram runtime (`ABDA7889AF78F8C6F7FF7B3B897D53619580C1D1FABFE395C36D4246BBD17FB6`).

The first package check caught incomplete runtime copying in the scratch tree, not a missing
source dependency. Complete extraction from the original diagram JAR and its runtime index
resolved it; the subsequent local package/Maven gates pass. No bypass or product change was
made. Retain both attempts. No Minecraft run, Prism modification or public binary upload occurs.

Non-portable operation evidence: `D:/Workspaces/Artifacts/RadianceCommitSplit/20260925/`:
`BACKUP.json`, both candidate diffs/file inventories, `VALIDATION.json`, test XML and gate logs.
Initial-product hashes use sorted repository-relative paths and Git-normalized file bytes;
original working-file inventories also preserve raw hashes. The validated product tree is
unchanged by the final documentation/pair-reference addition. Original diagram/persistence/
ModelPart source archives and runtime evidence remain in place with their original identities.

### Pair and remaining work

Paired amended native Initial port: `3e19fa36ea2f0053b0a0404df1cfc05ce034b361`.
Preserve original Initial port identities, author/committer dates/time zones, message and upstream
parents, and generate fresh SSH signatures. Do not create the performance commit in this step.
Remaining diagram/screen-effect reports, GPU-loss cause, broader runtime/visual acceptance and
public runtime licensing remain open. Push/backup-cleanup results are reported separately rather
than causing a second documentation amendment.

## 2026-09-25: Performance Optimization Test V1 source checkpoint

Status: user-authorized additional performance commit; existing verification evidence retained.
Applies above Radiance Initial port `59d17117a9cb5d0027119a1ccae2cd309c8b5e8a`.
Paired native performance commit: `265d822e315cd10b864beb49ac5e89dfa0acca20`.
This completes the performance side of the preceding subject split. Both Initial port commits
remain unchanged; the separate checkpoint uses the configured identity, current time and SSH
signature. It includes submission strings, persistent baked-model capture, the bounded ModelPart
experiment, lifecycle/fallback guards, Audit profiling/probes, regressions and performance records.
Diagram corrections and unrelated visual investigations remain in Initial port.

All product, test and build inputs match the normalized candidate-4 source manifest in
`run/model-parts-20260925/evidence/candidate-4/MANIFEST.json`; only later maintenance records differ.
The source archives and JAR/DLL/PDB hashes were rechecked. The retained Radiance JAR is
`89FDB75B828F06B565133E8818D0DCF74A0D00B2CCACF06080E2EFD6D564BA63`, Audit JAR is
`7294EBE93029324828306E004C38AFEC9371D8961680C3F86C2CFCFDDD0EE7CA`, and DLL is
`B015D09C3E8306038BC126EC50A3A412C1B6C3AD882F3311D04110333BBAD5A6`.
The earlier GAME 212/212, bootstrap 10 passed/2 skipped, Audit Java 21/21, collector 2/2 and native
5/5 subset remain prior execution evidence. Candidate-4's two bounded runtime checks, candidate-1's
timed comparison and older visual feedback retain their separate identities. No build, test,
client run or deployment is repeated for this source-only checkpoint.

Complete candidate lists, snapshot/artifact checks and staged diffs are retained outside Git at
`D:/Workspaces/Artifacts/RadiancePerformanceV1/20260925/`. Necessary new source and tests are included;
generated artifacts, runtime libraries, logs, instances and worlds are excluded. ModelPart remains
default-off without a measured net gain. GPU-loss cause, broader temporal/visual acceptance and
public binary licensing remain open. This operation creates local commits only; no push is authorized.
