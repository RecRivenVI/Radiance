# GPT-6 Pro code-review verification: 2026-09-22

> Current checkpoint correction: see [2026-09-23: Bounded local closeout and acceptance checkpoint](#2026-09-23-bounded-local-closeout-and-acceptance-checkpoint). The opening summary and earlier holds below describe their historical snapshots, not this checkpoint.

Status: remediation in progress; static verification is complete for all thirteen findings.
Batches 1 through 3 and the source/automated portion of batch 5 are implemented. P1-03 is now
closed at the static, build, and automated-test levels, while injected live failure and device-loss
shutdown remain runtime gates. P2-02 remains incomplete, and P2-07 still requires the deferred
shared UI PT/memory redesign. Runtime verification has started for the Ponder/DLSS failure path;
broad gameplay and visual acceptance remain pending.

Evidence level: static, build, automated, build-host Vulkan GPU tests, deployment, and the bounded
full-compatibility Ponder runtime reproduction itemized below. This does not constitute general
Minecraft runtime or visual acceptance.

Applies to: Radiance `249f9a63bd861a3273d2d43ff6e594bff980c66e`; MCVR
`b0173ab855d4f693a0a62216ec07197b184490b5`.

## 2026-09-22 follow-up correction: four remaining native-boundary gaps

A later remote review found four concrete gaps in the earlier P1-03 source-closure statement:
partial `std::thread` construction could terminate while unwinding joinable workers;
`Device::recordFailure noexcept` could throw while locking, copying, or logging before publishing
the minimum error; the immutable first error was also used as the device-loss cleanup flag; and a
borrowed JNI UTF-16 range was passed to `GetModuleHandleW` without creating terminated storage.
The earlier review and validation facts remain historical evidence, but its assertion that these
boundaries were already complete was incorrect.

The current working tree repairs all four at source level. Thread-launch failure cancels and joins
started workers before propagating the launch error. Failure publication uses an allocation-free
packed first-cause atom plus a separate monotonic device-loss latch; optional text and logging can
fail without hiding the result. All relevant idle/close paths consume the same loss latch,
including an asynchronous global Streamline report. JNI module names and the other first-party
middleware/loading string borrows now use an owned, exact-length copy and RAII release; a pending
Java exception stops the call and remains pending. Borrowed module handles are not released.

The same-root inventory covered all first-party native thread creation, Vulkan/Streamline/
FidelityFX callbacks, all 182 JNI exports and direct JNI string borrows, plus Radiance's renderer
initialization thread, early-window executor, chunk executors, native declarations, and close
callers. No analogous Java-side partial-thread ownership was found. Third-party SDK internals,
driver callbacks outside these adapters, and future reflective third-party entry points remain
outside this inventory.

Automated evidence is an injected third-thread-launch failure, task failure and normal execution;
throwing diagnostic storage; ordinary fatal followed by loss; concurrent failure records;
global async-style loss observed by cleanup; concurrent repeated close; and bounded UTF-16 tests
covering non-NUL Unicode, null, acquisition failure, and pending-exception cleanup. The native
RelWithDebInfo build succeeded, MCVR CTest passed 30/30 including four Vulkan GPU tests, and the
paired Radiance JUnit suite passed with `--rerun-tasks`. The built `core.dll` SHA-256 is
`95EB80A6F0C99A9B377CD66FC187A76AACD58C1B11D698095FA8BAFB60FCEAD5`.

This restores the source/build/automated P1-03 closure at the expanded scope; it does not close
runtime acceptance. No client or real JNI VM fixture was launched, no post-world fatal or actual
device loss was induced, and integrated-server save/stop plus user-facing error behavior remain
open. P2-02, generic UI PT, and Ponder memory were outside this follow-up.

## 2026-09-22 working-tree and commit-readiness checkpoint

This checkpoint pauses further remediation and records the repository state without staging,
committing, amending, pushing, or deleting anything. Both local `develop` branches exactly match
`origin/develop` at the commits above. GitHub reports both commits as valid SSH-signed commits.
Each remote exposes only `develop` and no tags. The local repositories also retain the historical
pre-amend backup refs `refs/backup/pre-amend-20260920-085455` and
`refs/backup/pre-amend-20260920-085738`; Radiance additionally has Codex checkpoint refs. These
local refs are evidence and are not part of the proposed source change.

The unstaged candidate consists of the tracked modifications plus every currently untracked
source, test, and audit file. Radiance has 27 tracked changed paths and seven untracked candidate
paths: two product classes, four tests, and this audit. MCVR has 68 tracked changed paths and ten
untracked candidate paths: six product/header/shader files and four tests. The index is empty in
both repositories. Omitting any of those untracked source files would produce an incomplete
repair, so a later staging operation must use an explicit reviewed manifest or verify the result
of `git add -A`; this checkpoint does neither.

Ignored material is not a source-commit candidate. It comprises Gradle/CMake outputs, packaged
client and server instances, logs, the external diagnostic mod and compatibility lab, compiled
objects, installed native outputs, shaders, and SDK runtime DLLs. The installed Radiance native
files currently match the sibling MCVR install hashes. The required runtime is reproducible from
the pinned MCVR source/submodules and the SHA-256-pinned Streamline 2.14.1 archive; the tracked
DLSS notice, manifest and license files remain the distribution record. No ignored diagnostic or
generated file was found to contain a product-source change that must be rescued into this batch.

Source commit readiness and public binary release readiness are separate. The current source can
be reviewed and committed while runtime/visual gates remain open, provided those gates stay
documented. A public JAR release that embeds NVIDIA DLSS/Streamline and Intel XeSS runtimes needs a
separate license checklist. The repository includes the NVIDIA, Streamline and XeSS notices, but
the NVIDIA SDK terms add distribution, attribution/trademark and pre-commercial-notification
conditions, and their interaction with the GPL-3.0 project distribution must be resolved before a
public binary release. This checkpoint records a release blocker, not a legal conclusion and not a
source-commit blocker.

The recommended history shape is a new paired remediation commit in each repository. The existing
signed `Initial port` commits are the immutable baseline used by the external report, comparison
URLs, this audit, and the fixed source/artifact hashes below. Amending them would replace both
commit IDs and signatures, invalidate those direct baselines, and require force-pushing. If a
single-commit history is later chosen explicitly, author and committer name, email, timestamp, and
message must be copied exactly, both new commit objects must be SSH-signed, verified backup refs or
bundles must be retained, and the push must use an expected-old-value lease. The old commit IDs
must remain named in this audit as the review baseline, while the new commit IDs and fresh product
snapshots become a separate remediation checkpoint.

The resulting paired native remediation commit is MCVR
`5cede900812322900e8ad7197d87f3a87ef2906e` (`Update`). The Radiance commit intentionally does
not record its own eventual SHA; the pair is completed by its parent-to-child order and this
one-way reference, without either repository being amended to backfill the other.

### History-policy correction

The preceding recommendation and intermediate MCVR `Update` SHA were superseded later on
2026-09-22 by the repository owner's single-port-commit policy. The maintained branch keeps
upstream history plus one signed `Initial port`; subsequent work is amended into that commit while
its original author, committer, timestamps, message, and parent are retained. The folded MCVR
counterpart for this Radiance snapshot is
`9ebf73cc57290dbbdc9a6dbdd8f19f5989c45dbd`. This correction changes history organization only;
the verification dates, product snapshots, artifact hashes, and incomplete acceptance gates in
this audit are unchanged.

## Scope and counting correction

This audit independently checked the issues reported by the external GPT-6 Pro static review. The
external report says it contains twelve findings, but its numbered list contains thirteen: three
P1, nine P2, and one P3. The external severity is retained only as an identifier; this document's
verdict is based on the pinned local source.

The verdict terms are:

- `confirmed`: the reported control flow, data loss, unbounded growth, or redundant work exists;
- `confirmed with narrower scope`: the underlying issue exists, but part of the report overstates
  or combines behavior;
- `conditional`: the implementation fact exists, but whether it is a product defect depends on an
  explicit product contract;
- `runtime acceptance`: the defect is statically established, while severity or visual
  equivalence still requires a real client or GPU run after a fix.

## Verification table

| ID | Verdict | Repairability | Runtime evidence needed |
| --- | --- | --- | --- |
| P1-01 acquire/recreate state | Confirmed | Implemented; build/automated verified | Resize/fullscreen recovery |
| P1-02 Streamline absent with FG disabled | Confirmed | Implemented; build/automated verified | Resize on a Streamline-unavailable configuration |
| P1-03 native `exit()` | Confirmed; broader than reported | Source repair complete; runtime acceptance open | Injected runtime failure, device loss, and client shutdown |
| P2-01 DLSS failed output | Confirmed | Fallback/history repair implemented; automated and bounded Ponder failure verified | Forced generic evaluate failure and recovery |
| P2-02 binary UI coverage | Confirmed; visual severity pending | Open; explicit premultiplied composition contract required | Semi-transparent UI, text, blur, and invert cases |
| P2-03 orthographic priority rays | Confirmed | Shared camera implementation and shader contracts complete | Ponder priority geometry alignment |
| P2-04 external chunk slots | Confirmed | Generation-aware reuse and 100,000-cycle contract complete | Long Sable/Aeronautics churn |
| P2-05 chunk snapshot before backpressure | Confirmed | Permit-before-snapshot repair and unit test complete | High-distance/F3+A correctness and performance |
| P2-06 unused Flywheel light upload | Confirmed | Producer-to-consumer chain removed; automated verified | Create/Flywheel visual and performance comparison |
| P2-07 Ponder allocation and resolution | Confirmed with narrower scope | Composite reuse complete; viewport/shared-pipeline memory design open | Resize, transition, scale, framing, and VRAM pressure |
| P2-08 Veil bool/uint collapse | Confirmed | Logical types and real shader compile tests complete | Representative third-party Veil programs |
| P2-09 Maven artifact mismatch | Conditional | Product contract resolved as developer-only `Radiance-game` | Consumer check only if that artifact is published for developers |
| P3-01 child-thread assertion | Confirmed | Test repair complete and regression-verified | No game run required |

## Confirmed correctness and failure-state issues

### P1-01: acquire success is confused with recreate success

MCVR `src/core/render/render_framework.cpp` uses one result variable for both
`vkAcquireNextImageKHR` and `recreate(true)`. Two consecutive `VK_ERROR_OUT_OF_DATE_KHR` results,
each followed by a successful recreate, exhaust the retry loop with the variable set to
`VK_SUCCESS` even though no image was acquired. The acquire semaphore was also cleared. The code
can then publish a frame context and later dereference the missing semaphore during submission.

The fix is well bounded: track acquire and recreate results separately, publish a frame context
only after a successful acquire, and return an explicit skipped-frame result when the bounded retry
is exhausted. A deterministic API fixture must cover `OOD -> recreate success -> OOD -> recreate
success` before a live resize test. Vulkan's acquire contract is documented by
[`vkAcquireNextImageKHR`](https://docs.vulkan.org/refpages/latest/refpages/source/vkAcquireNextImageKHR.html).

### P1-02: disabling FG incorrectly requires an initialized Streamline runtime

MCVR `StreamlineRuntime::setFGLoaded` rejects every call when Streamline is uninitialized. The
presentation recreate path calls it even when the requested state is `false`. MCVR initialization
otherwise permits Streamline initialization failure, so a renderer configuration that does not use
FG can start and then fail when presentation is recreated.

The repair must distinguish four results instead of collapsing them into a Boolean: disabled
successfully, unsupported, runtime unavailable, and SDK failure. `loaded=false` with no Streamline
runtime is a successful no-op; `loaded=true` is an unsupported/unavailable result exposed to the
option layer.

### P1-03: runtime native failures can terminate the whole JVM

The report identifies direct process termination in frame acquire and fence-wait handling. The
actual scope is broader: the active MCVR tree contains direct `exit()` calls in frame acquire,
submission/presentation, image/buffer/descriptor and acceleration-structure creation, Vulkan
initialization, and programmer-invariant checks. Java already has `checkVkResult` paths and the JNI
layer has exception conversion helpers, but `exit()` bypasses both.

This should not be repaired by mechanically replacing every call. Each site must first be classified
as initialization failure, recoverable frame/runtime failure, device-lost failure, or internal
invariant. Runtime failures should enter a typed fatal-renderer state, stop further submission, and
cross JNI once so Java can perform the shutdown that remains safe. Device-lost cleanup must avoid
indefinite waits. Initialization failures and invariant failures still need structured diagnostics,
but can use different propagation policies.

The current paired worktree completes that source repair. All 61 remaining direct process exits
were classified and removed, including the compiled legacy Vulkan framework. All 182 JNI exports
under the GAME and SERVICE native surfaces now enter a guarded boundary. Initialization failures
remain retryable and do not poison the global state; the first runtime, invariant, or device-lost
failure becomes sticky; normal calls are rejected after fatal while failure reporting and repeated
close remain available. Device-lost cleanup skips idle waits. Worker failures are joined and
re-thrown to the caller, C ABI callbacks contain exceptions, and asynchronous DLSS-G device loss
publishes the same global fatal state. This is static/build/automated evidence, not proof of Java
shutdown or world saving during a real GPU failure.

### P2-01: failed DLSS evaluation does not invalidate its output

MCVR `dlss_module.cpp` stores the result of SR/RR evaluation, but normal rendering uses it only for
the optional Ponder diagnostic capture. Downstream work continues to consume the designated output
image without a same-frame fallback or an invalid-output state.

The repair must make evaluation success part of the render-graph output contract. On failure, MCVR
must either populate a known same-frame fallback or skip the dependent frame and rebuild the
feature. The recovery frame must reset temporal history. A mocked failure on a chosen frame can
prove that a failed output is never consumed before live GPU testing.

### P2-03: priority rays do not share the primary camera model

The advanced primary ray-generation shader implements orthographic projection with per-pixel ray
origins and a parallel direction. `priority/background.rgen` and `priority/priority.rgen` always
emit perspective rays from the camera position. Ponder can therefore render the physical scene and
priority geometry through different pixel rays.

The repair is to place ray construction in one shared shader helper and use it in every primary,
priority, and background ray-generation path. A shader-side math fixture should prove orthographic
parallel directions and perspective common origins before visual Ponder acceptance.

## Confirmed data lifetime and performance issues

### P2-04: released external chunk slots are never reused

MCVR `Chunks::allocateExternalChunk` appends to `chunks_`, `chunkBuildDatas_`, and
`chunkPackedData_`. `releaseExternalChunk` invalidates and clears the slot but does not return it to
a free list. Capacity therefore follows historical allocations rather than live external sections,
and emission collection can amplify the cost by replacing packed-data buffers.

Simple integer reuse is unsafe. Radiance background work currently carries only the native index;
a late task for an old external section could target a newly allocated owner. The repair needs a
slot plus generation handle, generation validation at every update/build/release boundary, and GPU
frame retirement before reuse. A high-churn fixture must prove bounded capacity and rejection of
stale generations.

### P2-05: chunk backpressure happens after region creation

Radiance `ChunkProxy` creates a `RenderChunkRegion` before it confirms that the native-build
in-flight limit and per-frame task budget can accept the job. Saturated tasks are requeued, so the
same section can repeatedly pay region snapshot cost without entering the builder.

The repair is to reserve a scheduler permit before region creation and release it on every failed,
stale, cancelled, and completed path. A capacity-one fixture with a blocked worker should prove
that region creation stops while capacity is unavailable and resumes without losing or starving
the queued section.

### P2-06: Flywheel light-section data is produced and uploaded without a shader consumer

MCVR still copies prepared Flywheel light sections, allocates/uploads the native buffer, and binds
its descriptor. The active `applyFlywheelFragmentLighting` implementation intentionally omits
vanilla lightmap, cardinal, and dynamic Flywheel/Sable light sampling for path-traced lighting; its
callers therefore do not consume this data. Helper functions that can search and sample the buffer
remain present but have no active call site.

The current product mode should stop the producer, copy, upload, and descriptor chain together.
Other instance appearance and crumbling data must remain. If dynamic section lighting is restored
for another mode later, it should use dirty versions and stable buffer capacity rather than an
unconditional full copy.

### P2-07: Ponder's steady-state allocation claim is valid, but scene caching was understated

Radiance chooses the entire physical window dimensions for the Ponder output. MCVR caches up to two
`PonderSceneRenderer` objects, their independent world pipelines, and their per-swapchain contexts;
therefore the report's wording can be read too broadly if it implies that the complete scene
pipeline is recreated every draw.

The remaining confirmed issue is narrower: each Ponder draw creates a new composite output image
and descriptor table, retains both until frame retirement, and processes the full-window extent.
The safe first stage is to persist one composite output and descriptor per scene frame context,
rebuilding only on size/format changes. Viewport reduction is a separate stage because Ponder bakes
its screen transform into captured vertices and the current result is redrawn as a full-NDC quad;
cropping requires an explicit content rectangle and transform adjustment, not merely smaller image
dimensions.

## Confirmed translation and composition issues

### P2-02: FG coverage is binary rather than the UI's actual alpha

`dlss_ui_coverage.comp` compares final and HUD-less RGB and writes either zero or one to the R32F
UI-alpha image. This deliberately conservative mask loses partial coverage and cannot reconstruct
the alpha of semi-transparent panels, antialiased text, or effects whose output depends on the
background.

The Streamline DLSS-G contract expects the supplied UI alpha to describe actual opacity and gives
the premultiplied composition relation `Final = UI + (1 - alpha) * HUDLess`; see the
[`Streamline 2.14.1 DLSS-G integration guide`](https://raw.githubusercontent.com/NVIDIA-RTX/Streamline/v2.14.1/docs/ProgrammingGuideDLSS_G.md).
Radiance therefore needs an explicit GUI composition target carrying premultiplied UI color and
coverage in the same post-processing space as the HUD-less image. Background blur and inversion
need dedicated handling because they cannot be recovered from a foreground alpha alone. The defect
is static; its visible severity remains a runtime-acceptance question.

### P2-08: Veil logical `bool` and `uint` types are collapsed to signed integers

Radiance `ShaderRegistry` and `VeilShaderBridge` map `bool`, `uint`, `bvec*`, and `uvec*` to
`ShaderField.Kind.INT`; generated GLSL consequently declares `int` or `ivec*`. Equal storage width
does not preserve Boolean conditions, unsigned comparison/high-bit behavior, bit operations, or
overload selection.

The repair must separate logical GLSL type from packed storage layout. Add Boolean and unsigned
logical kinds, retain their standard-buffer alignment, emit the correct declarations/access
expressions, and reject unsupported source types rather than claiming translation. Tests must run
the real translator and shader compiler for Boolean branches, Boolean vectors, unsigned high-bit
values, unsigned operations, and arrays.

## Conditional packaging issue

### P2-09: Maven publication and the installable package are different artifacts

Radiance's `distributedJar` creates the SERVICE bootstrap JAR with the nested GAME JAR and runtime
resources. `publishing.mavenJava` uses `components.java`, which represents the internal GAME Java
component instead of that installable distribution. The configuration difference is confirmed.

It is a product defect only if the Maven coordinate is intended to be installed as the mod. If it
is an API/development artifact, the difference can remain but needs a distinct artifact identity or
classifier and explicit documentation. Once the intended contract is chosen, an artifact-content
test should compare the published file with the required SERVICE metadata, nested GAME JAR, native
manifest, and runtime files. A clean-instance install is required only for a user-facing package.

## Confirmed test defect

### P3-01: a child-thread assertion can escape the JUnit result

`AppliedShaderStateTest.appliedStateIsBoundToTheCallingThread` runs assertions in a raw `Thread`
and only joins it. An `AssertionError` on that thread is not rethrown by the parent test method, so
the tested behavior can regress while the test is reported successful.

Use an executor and `Future.get()`, or capture and rethrow the child `Throwable` after `join()`. A
temporary deliberate failure must be observed as a failed test before restoring the real assertion.

## Repair order and acceptance boundary

The findings are repairable, but they should not be landed as one undifferentiated change.

1. Repair P1-01, P1-02, P2-01, and P3-01 with deterministic failure/state tests. These changes stop
   invalid states from being treated as successful work.
2. Inventory and migrate P1-03 in stages, beginning with active per-frame runtime paths. Do not mix
   a wholesale exception conversion with unrelated rendering changes.
3. Repair P2-03, P2-05, and P2-06 with math/scheduler/producer-consumer tests, then run Ponder and
   high-distance chunk acceptance.
4. Repair P2-04 with a generation-aware handle protocol across both repositories before enabling
   slot reuse.
5. Reuse Ponder resources before changing its viewport. Treat viewport cropping as a separate
   visual change.
6. Implement P2-08 with real shader compilation tests. Implement P2-02 only with captured UI
   composition evidence and user visual acceptance.
7. Resolve P2-09 from the intended publication contract; do not silently make the internal GAME
   artifact and installable distribution share one ambiguous coordinate.

No current finding requires the user to launch the game merely to establish that the reported code
path exists. Gameplay is useful after the relevant fix and diagnostics are built and deployed. The
first requested manual matrix should be narrowly scoped to continuous resize/fullscreen changes,
Streamline-unavailable FG-off presentation, Ponder orthographic priority alignment, high-distance
F3+A scheduling, and selected FG UI composition cases.

## Remediation progress

### Batch 1: failure states cannot become valid frames

Status: implemented; build-verified; automated-verified; runtime acceptance pending.
Evidence: static, build, automated.

Applies to the uncommitted Radiance and MCVR worktrees based on the commits named above.

P1-01, P1-02, P2-01, and P3-01 have an initial complete source repair:

- acquire and recreate results are separate; exhausted out-of-date retries clear the active native
  context and return `VK_NOT_READY`;
- Radiance tracks whether a frame was actually acquired, cancels `GameRenderer.render` while the
  transient acquire is unavailable, and neither submits nor presents an old context;
- disabling FG is a successful no-op when Streamline is absent, while enabling it reports a
  distinct unavailable result;
- a failed DLSS evaluation invalidates the native result, requests a temporal-history reset, omits
  the diagnostic output copy, and supplies a known same-frame spatial fallback before downstream
  consumers run;
- the shader-thread test now observes worker assertion failures through `Future.get()`.

The first P1-03 slice also replaces the direct `exit()` calls in frame acquire and frame-fence wait
with the existing renderer failure channel. The remaining native `exit()` inventory is not marked
resolved and remains a separate staged item.

Regression coverage consists of two Java frame-lifecycle tests, the corrected shader-thread test,
and the native `mcvr.failure-state-contract` state-transition executable. Radiance's complete
JUnit suite passed. The full Release MCVR build passed, followed by all 27 CTest entries, including
four Vulkan GPU tests on the build host. These results do not exercise a real Minecraft resize,
missing-Streamline installation, or forced live DLSS evaluation failure.

### Batch 2: shared camera semantics and admitted work only

Status: implemented; build-verified; automated-verified; runtime and visual acceptance pending.
Evidence: static, build, automated.

P2-03 is repaired across every located camera-producing ray path, not only the two files named by
the external report. Advanced and vanilla world, advanced primary, priority, background,
volumetric light, and direct-light surface reconstruction now share one perspective/orthographic
ray implementation. The source contract and normal shader build passed. Ponder priority geometry,
background, and volumetric alignment remain a visual gate.

P2-05 is repaired with an atomic, transferable chunk-build permit acquired before region snapshot
creation. All local failure paths release the permit; accepted workers own it until `finally`.
Capacity and idempotent release have JUnit coverage. High-distance loading and F3+A convergence
remain runtime and performance gates.

P2-06 is repaired by removing the producer, JNI, native storage, GPU buffer, descriptor, and unused
shader sampler as one chain. Still-consumed per-instance and shader-light inputs were retained.
Radiance's full JUnit suite passed. MCVR's full Release build passed, and the targeted JNI, shader,
camera, instancing, and failure-state CTests passed. No game was launched, so this batch does not
claim Ponder visual correctness, chunk convergence, or Flywheel/Create visual equivalence.

### Batch 3: bounded external handles, stable Ponder composites, and logical shader types

Status: implemented; build-verified; automated-verified; runtime and visual acceptance pending.
Evidence: static, build, automated, and build-host Vulkan GPU tests.

P2-04 is repaired with opaque generation-bearing handles rather than by recycling a bare integer.
Every Java operation resolves the handle, and CPU copy completion, queued build publication and GPU
batch completion revalidate the captured generation. Release invalidates first, installs a fresh
slot object on reuse, and sends old GPU resources through frame retirement. The 100,000-cycle
contract demonstrates bounded CPU slot capacity and stale-handle rejection; live mod churn is not
yet tested.

The confirmed part of P2-07 is repaired: Ponder composite outputs and descriptors are persistent per
scene and swapchain frame. They are rebound on each invocation and retire with the scene. The
report's resolution claim remains intentionally separate; tracing still uses the full window and a
smaller viewport will not be adopted without transition, scale, crop, and visual evidence.

P2-08 is repaired without changing physical storage width. `bool`/`bvec` and `uint`/`uvec` survive
Veil and generic array parsing as distinct logical kinds, generate their real GLSL declarations,
and upload the underlying 32-bit integer words. The regression shader uses Boolean control flow and
unsigned high-bit operations and passes real translation plus SPIR-V compilation.

P2-09 is resolved as a product-contract decision. Maven is a separately named developer-only
`Radiance-game` publication. It is deliberately not installable: the SERVICE bootstrap, nested GAME
packaging, native renderer, shaders and runtime libraries remain exclusive to `distributedJar`.
The build checks both artifact identities and their contents.

The complete MCVR Release CTest run passed 30/30 in 3.76 seconds, including four Vulkan GPU tests.
Targeted Radiance translation/compilation tests passed. The full Radiance build remains to be rerun
after the current batch is closed. No game was launched or deployed, so Ponder visuals, real Veil
programs, Sable/Aeronautics churn, resize and Maven consumer behavior remain outside the evidence.

### Batch 4: native failure containment, active-path slice

Status: partially implemented; build-verified; automated-verified; runtime acceptance pending.
Evidence: static, build, automated, build-host Vulkan GPU tests, and artifact identity.

This is a historical intermediate state. Batch 5 below supersedes its current P1-03 status and
removes the 61 exits that remained at this point.

P1-03 is narrowed but not closed. Direct process exits were removed from active present,
submitted-frame readback, screenshot-fence, optional NGX-directory, ray-tracing shader-pack and
overlay-buffer validation paths. Operational Vulkan failures use the renderer failure channel;
exceptions are used only where the inspected JNI call is already guarded.

A complete inventory still contains 61 direct exits in Vulkan constructors, resource invariants and
an older framework implementation. Replacing them before protecting the remaining JNI and worker
boundaries could convert `exit()` into an uncaught C++ exception and `std::terminate`, so they remain
explicitly open. The next slice must classify reachability and error kind, add safe boundary
propagation, and then remove each class with tests.

After this slice, the paired build completed `prepareRuntime build preparePackagedClient`; MCVR
Release CTest passed 30/30, including four Vulkan GPU tests. Packaged and build JARs match, and the
embedded and MCVR-output DLLs match. This is build/deployment evidence only: no Minecraft runtime,
forced Vulkan fault, device-lost event, or visual acceptance has yet been performed.

### Batch 5: P1-03 source closure and physical-client boundary

Status: implemented; build-verified; automated-verified; isolated runtime acceptance pending.
Evidence: static inventory, native and Java builds, JUnit/bootstrap tests, CTest, build-host Vulkan
GPU tests, distribution verification, and artifact identity.

The P1-03 audit covered every native source file, not only the original frame path. The final
inventory contains no direct `exit`, `abort`, `terminate`, `quick_exit`, or `_Exit` call outside
third-party code. All 182 JNI exports in `src/core/middleware` and `src/core/loading` use the common
boundary. The boundary distinguishes retryable initialization from sticky runtime/device-lost and
invariant failure, refuses normal work after fatal, and explicitly permits only failure reporting
and idempotent cleanup. The first-failure publication is synchronized and immutable. Unknown C++
exceptions become invariant failures instead of allowing later rendering to continue.

Related call paths were checked as part of the same root-cause pass. `parallelFor` retains and
rethrows the first worker exception after joining workers and stops admitting new work. Vulkan,
Streamline, and FidelityFX callbacks contain all C++ exceptions. A Streamline asynchronous
`VK_ERROR_DEVICE_LOST` now enters the global fatal state. Swapchain recreation preserves a typed
fatal error rather than replacing it with a generic initialization result. Renderer and loading
cleanup avoid device-idle waits after device loss; close-before-init and repeated close are safe.
The cleanup decision uses an allocation-free atomic device-loss result, so a destructor does not
need to copy the locked diagnostic snapshot and cannot turn error reporting into another exception.
Two-step `Framework` initialization retains already-created members in a temporary shared owner,
so a later constructor failure unwinds them, and `Singleton::init` remains retryable when
construction throws.

The physical-client audit found two issues beyond P1-03. The SERVICE mod locator previously
materialized the nested GAME JAR without checking the launch target, and the shared
`ReloadableResourceManager` Mixin began Vulkan reload transactions for any resource manager in a
physical client, including the single-player logical server. The graphics bootstrapper, immediate
window provider, and nested-mod locator now share the same client-launch predicate; the GAME JAR
is absent from dedicated-server discovery. Resource reload integration now checks the actual
Minecraft client resource-manager instance. The three shared-list Mixins are declared in the
client list. The resource identifier wrapper is limited to PNGs and now delegates bulk reads and
close through `FilterInputStream`, avoiding both server-data wrapping and leaked pack streams.

No custom packet registration or server event subscriber was found. Imports from
`net.minecraft.server.packs.*` are shared resource APIs, and `BlockDestructionProgress` is a data
type consumed by the client level renderer; neither is evidence of logical-server execution. World
capture entry points take `ClientLevel`, and the Flywheel backend rejects non-client `Level`
instances. These are explicit exclusions, not proof against future third-party reflective calls.

The fixed product-source snapshots, excluding documentation and including untracked product files,
are Radiance `AE5A85BF886B6773ADD2883EC7BCE7F781221C52BB3F6CF1547CD1B753D9677A` and MCVR
`4CE216AF5378A526243437EE3C9FD062A7807F7307A0DD60EFCBEE138F6F3137`. The Release native DLL,
installed Radiance resource, and distributed-JAR `core.dll` all have SHA-256
`5AA4C808C2C766270491FF93389D4ECE39350385CEE204B319A8BD8959860FB3`; the distributable JAR has
SHA-256 `D1D48302BA7885EB5223F07EB6AFEDC20810E13D743ED40D99B7163DDDAF2FFF`.

Automated validation completed `prepareRuntime build preparePackagedClient`, both Java test suites,
and the native Release build. The first complete CTest attempt exposed a flaky scheduling-count
assertion in the new worker failure test; the test was corrected to assert the actual contract,
exception return to the caller, instead of scheduler timing. The final complete CTest result is
recorded in the paired ledgers. Dedicated-server launch, injected post-world fatal failure, actual
device loss, integrated-server save/stop, and user-visible error presentation remain unperformed
runtime gates. Partial initialization inside third-party SDK internals and driver behavior after
device loss remain outside static proof.

The physical-client boundary also received a packaged runtime check. `runPackagedServer` installs
only the distributable JAR into the ignored `run/packaged-server` directory. NeoForge reached
`Done (3.300s)` with only Minecraft and NeoForge in the discovered mod list; Radiance's nested GAME
mod was absent, and no extracted `core.dll` or native directory appeared. The process was then
interrupted through the Gradle wrapper because that wrapper did not forward the server console
`stop` command, so this proves discovery/startup isolation but not graceful server shutdown. An
earlier PTY attempt failed before Gradle configuration because it selected Java 25; the successful
run explicitly used the project's BellSoft Java 21 and is the only runtime evidence counted here.

### Runtime follow-up: the reported DLSS `FeatureNotSupported` was an out-of-VRAM warning

Status: reproduced with the complete compatibility mod set; root return code identified; memory
pressure repair pending.
Evidence: actual packaged-client runtime plus persistent native diagnostics.

The earlier Ponder exception reported NGX value `0xBAD00001` because the compatibility wrapper
collapsed every failed Streamline call to `NVSDK_NGX_Result_FAIL_FeatureNotSupported`. A diagnostic
build retained the original Streamline stage and result. The same scene failed specifically in
`slEvaluateFeature` with `Result::eWarnOutOfVRAM (39)`, not with
`eErrorFeatureNotSupported`.

The reproduction resized the physical window from 2560x1440 to 3840x2054. Ponder created several
full-window independent PT/RR pipelines during resize and a two-scene transition; live native
allocation reporting reached about 13.8 GiB of VRAM on a 16 GiB card. Streamline then warned that
the VRAM budget was exceeded and rejected RR evaluation. The new same-frame spatial fallback kept
the client alive, but performance fell to roughly 2-3 FPS while the pressure remained.

This runtime evidence strengthens P2-07: stable composite reuse fixed per-draw composite churn,
but full-window Ponder pipelines, transition cardinality, resize replacement, and heavy per-scene
buffers still need a bounded-memory design. It also corrects P2-01 diagnostics: original
Streamline errors must remain distinguishable rather than being renamed `FeatureNotSupported`.
The diagnostic records stage, named/raw result, feature, viewport, frame and dimensions in
`radiance-streamline.log`; repeated identical failures are rate limited in the next build.

### Runtime acceptance checkpoint after A/B/C/D

The source closure above is now paired with a default-off, one-shot lifecycle acceptance hook.
Automated checks and a fresh RelWithDebInfo package are complete, but no Minecraft gameplay result
has yet been observed. Accordingly P1-03 remains **source-fixed and automated-verified, runtime
pending** rather than closed by client evidence.

G0 will establish ordinary startup, new-world mutation, normal save/exit, and re-entry on the exact
package. G1 will inject one runtime fatal after a newly confirmed world mutation and then examine
Java receipt, sticky rejection, stopped submit counts, client error reporting, integrated-server
save/stop, process exit, and persistence after restart. G2 uses a separate process for read-after-
fatal and repeated-close behavior; deterministic worker and allocation failures remain automated.
G3 is deliberately unperformed because the current safe substitute proves state/cleanup selection
without proving real device-loss behavior. No healthy device will be marked lost merely to skip
synchronization while real GPU work remains in flight.

#### G0 result

G0 passed on the fixed RelWithDebInfo package in the isolated minimal instance. The same newly
created world was entered twice; the user confirmed the identifiable block and container mutation
persisted after normal save/exit and re-entry and observed no crash, hang, compatibility dialog, or
rendering fault. Machine evidence records two integrated-server save/stop sequences, exit code 0,
60,485 attempted and successful submits, zero injections, no fatal/device-lost state, and one close
call. The runtime-loaded native DLL matched the packaged SHA-256
`06707FC39F0679B78C066A89AB89611AB2AA7B34D029D1A9ABE6255E575AB8C9`.

This closes only the normal-lifecycle G0 gate. The controlled runtime fatal and its persistence
check (G1), post-fatal read/repeated-close boundary (G2), and real device-lost behavior (G3) remain
unaccepted at this point.

#### G1 result and fatal-exit cleanup correction

The first controlled runtime fatal proved the original `VkResult=-13` reached Java, the sticky
fatal rejected an ordinary follow-up before its JNI body, no submission occurred after injection,
and the integrated server saved all dimensions. A normal restart preserved the user's mutation.
It also proved the earlier cleanup claim incomplete: Minecraft's crash boundary terminates the
process without calling `Minecraft.close()`, so native close did not run.

Radiance now closes an initialized renderer at Minecraft's common fatal-exit boundary, after
emergency save and before termination. The close is best effort so a cleanup failure cannot replace
the original crash. Native-independent regression tests cover uninitialized, successful, and
failing close attempts; full Java/Bootstrap tests and package verification passed. On the repaired
G1 run, all dimensions again saved, close ran once, Streamline/NGX shutdown completed, the original
first failure remained unchanged, and post-injection submit attempts and successes both remained
zero. A second normal restart preserved the new mutation. G1 is therefore runtime-accepted; G2 and
real device-loss G3 remain separate gates.

#### G2 result

G2 passed in its own title-screen process. After the controlled fatal, the diagnostic snapshot was
still readable, the normal-call probe was rejected before entering its JNI body, and the first
result/operation remained `-13` and `lifecycle-acceptance/G1-runtime-fatal`. Two consecutive close
calls completed; the counter advanced to two, the first released the renderer, and the second was
an idempotent no-op. No submit attempt or success occurred after injection. The common fatal-exit
hook saw the renderer already closed and did not invoke native close again.

This closes the G2 boundary together with the deterministic thread-launch, worker, diagnostic-
allocation, concurrent-publication, JNI-string, and close-gate tests. It does not turn a controlled
fatal into evidence for an asynchronous Streamline failure or real `VK_ERROR_DEVICE_LOST`. G3
remains unperformed because no safe protocol currently quiesces all real GPU and asynchronous
presentation work before selecting the device-lost destruction branch.

#### Final candidate evidence mapping

The machine evidence remains under the ignored
`run/lifecycle-acceptance-20260922/evidence` directory. G0 and the first G1 run used JAR
`F01F2BE306B41F88C5FA6209A3FCF65A60D85FD6E8F9A008DC68C8171B724675`; repaired G1 and G2
used JAR `B8B2287C16509A71A0991BD94C421B38E874ED7898FA5628B109DDDD48389F86`. Every run used
native DLL `06707FC39F0679B78C066A89AB89611AB2AA7B34D029D1A9ABE6255E575AB8C9`.
The final source snapshot is `evidence/source-snapshot/post-g1-close-fix`, with Radiance product
patch SHA-256 `82F07C7A6255454556530E7D446E31604F049E1F74CEB9BD54F48A3ED3B97F27` and MCVR product
patch SHA-256 `7253F8098368158A5A5F52083B8AC9781A19D7368C63A57871CD9ACE5E0AE1AA`.
The final product files and necessary new source/tests match that snapshot. Later documentation
edits do not change the tested product. G3 and the independent roadmap items remain unaccepted.

### Follow-up: external admission and resource-type isolation

Status: source-fixed and automated-verified on 2026-09-22; named isolated runtime checks passed,
with longer external-section churn still pending.

The earlier P2-05 repair covered the primary chunk path, but did not cover Sable/Aeronautics
external sections. That separate path still prepared `RenderChunkRegion` snapshots and submitted
all queued work without admission. It now shares the primary important-build capacity, uses a
bounded per-frame preparation budget and retains the latest request across capacity pressure and
all failure paths. A temporarily unavailable region no longer clears valid geometry. Java
revision/owner checks complement, rather than replace, MCVR's existing encoded-handle generation
check. Deterministic tests establish bounded preparation and stale-publication rejection; a real
external-section churn run remains required.

The physical-client audit's PNG limitation was also incomplete. Delegated close behavior was
correct, but a static resource factory still had no way to distinguish client assets from logical
server data. A first getResource-only repair was disproved by runtime: F3+T left block-atlas
auxiliary maps stale, causing maximum parallax and water-like textures. The final interception
wraps results from the owning `FallbackResourceManager` instance for `getResource`,
`listResources` and `listResourceStacks`, and gates on `PackType.CLIENT_RESOURCES` before applying
the PNG file filter. Equal `.png` identifiers in server data are left untouched, while client
resource-pack selection, metadata and stream ownership remain intact. Full Java/bootstrap and
distribution verification passed: 152 GAME tests and seven bootstrap tests (two skipped), with
zero failures. The corrected JAR SHA-256 is
`B84950383D218DD5A323CB879E025C92112326D61E4616D92EB2E07AEAF6561E`.

In the full isolated mod set, Sable structure creation/separation and F3+A showed no visible issue;
the corrected resource implementation kept the no-PBR world visually normal through F3+T. One
subsequent process suffered a real `VK_ERROR_DEVICE_LOST` before `/reload`, corroborated by two
Windows `nvlddmkm` Event 153 records. It preserved the original error and saved every integrated
server dimension, but does not establish the device-loss root cause. A fresh process completed
`/reload` with only server recipes and advancements reloaded, no second client resource manager,
atlas or Vulkan texture reload, then exited normally with Gradle code 0. Longer external-section
churn and custom resource decoders outside the three standard manager result paths remain blind
spots.

## 2026-09-22 unified thread-closure correction

This section updates implementation status without rewriting the earlier review or its historical
runtime statements.

| Item | Current source conclusion | Automated evidence | Remaining boundary |
| --- | --- | --- | --- |
| P2-02 FG UI coverage | Implemented candidate: final alpha is accumulated GUI coverage; background-dependent full-screen effects use explicit full coverage | Behavioral coverage math test, shader compilation and resource contract | Real DLSS-G translucent UI, text, blur, invert and UI PT visual acceptance |
| P2-07 Ponder/UI PT | Implemented candidate: generic service, bounded aspect-preserving extent, persistent composites, shared transition world/TLAS and independent per-view history | Budget, resource ownership, scene camera, JNI coverage and shader/native builds | 3840x2054 VRAM/frame-time comparison, resize/framing, transitions and mutual-light visual acceptance; SDK-private allocations are outside counters |
| Texture IDs/descriptors | Confirmed and fixed with checked reuse over IDs 1..4095 | Allocation, exhaustion, release and reuse test | Long resource-pack churn in client |
| SR/RR camera/history/reset | Static path consistent; no source defect confirmed in this follow-up | Existing DLSS resource/reset and camera-ray contracts | Real SR/RR motion/disocclusion and model-specific visual acceptance |
| Priority temporal guides | Confirmed mismatch and fixed for depth, motion, albedo and normal/roughness | Shader compilation and world-text contract | RR/SR visual stability for moving priority geometry |
| Large-coordinate transforms | Visible Flywheel TLAS precision issue confirmed and fixed by delaying float conversion until camera-relative composition | Contract at 30-million-block coordinates | Absolute dynamic-light grid precision remains separate |
| Veil translation | Earlier bool/uint and call-driven screen-effect repairs remain intact; unsupported features still fail explicitly | Existing Veil shader compilation and compatibility tests in the full Java suite | Representative third-party Veil programs and unsupported sampler/layout inventory |
| G3 real device loss | Partial runtime evidence only; root cause unconfirmed | Default-off fixed-capacity trace contract | A future real occurrence must establish pre-loss operation and native close completion; no synthetic driver reset |
| NVIDIA public distribution | Engineering release gate implemented; approval not established | Local-distribution marker verification; public gate must fail | Project-owner/rights-holder and any professional license review described in `THIRD_PARTY_RUNTIME_AUDIT.md` |

External-section admission and client resource identity remain integration regressions in the final
matrix. Their earlier runtime results remain tied to their recorded artifact; the unified artifact
requires one final combined client pass. Pipeline-layout keepalive remains a repeatedly effective
workaround, not a proven Vulkan or driver root cause.

### Final unified-candidate correction

The unified candidate subsequently passed the complete paired build and menu-level startup/exit
preflight. The first preflight failed because the generic UI PT JNI migration had not replaced the
old Java native-owner entry; this was corrected and guarded by an owner test. Full JNI header
generation was separated from incremental Java compilation. Two native lifetime defects found by
allocation and shutdown diagnostics were also corrected: the final texture-upload batch is now
retired even when the queue becomes empty, retained staging memory is capped and right-sized to
actual batch use, and an unevaluated Streamline reconstruction viewport is no longer passed to
`slFreeResources`.

Current automatic evidence is 153 GAME tests plus seven bootstrap tests (two skipped), and 35/35
MCVR CTest cases including shader compilation and four Vulkan GPU tests. The final client remained
stable at the menu and exited normally. This does not convert the pending FG, Ponder, world-reload,
Sable churn or real device-loss boundaries into runtime or visual acceptance; those are exercised
by the unified manual matrix tied to the final artifact manifest.

## 2026-09-22 successor handoff: directed verification correction

Status: handoff checked; full manual acceptance is on hold for the source gaps below. This is a
correction to the implementation claims above, not a reversal of the recorded build/menu results.
Scope was FG background composition, UI PT sharing/transition ownership, texture-name reuse and
artifact provenance. No product code, build, deployment or Git index was changed during this check.

### Package and evidence identity

Before this documentation correction, both HEADs and tracked-diff fingerprints matched the unified
`MANIFEST.txt`; all listed untracked source/tests were present. The build and installed JAR both hash
to `594B2CD04D5F5998FFFD612EAD1629ECAC63BB5BC09948AB8C8C2CEA78BE3629`. The native build,
Radiance resource, JAR entry and extracted runtime DLL all hash to
`44F0FCC4517A133B1ED22909EB6D224B4A061173232827860113C541B0F76428`. The recorded PDB hash
also matches. No isolated client was running during this check. The prior manifest records the
loaded path; the retained final Java log does not independently record that path. A future process
must supply its own loaded-module evidence.

The sources JAR matches all 340 current GAME Java entries byte for byte. The original manifest
fingerprints tracked changes but only lists untracked filenames; it does not freeze their contents.
Thus the original native untracked-source-to-build correspondence is not independently proven by
that manifest. `build/manual-acceptance/evidence/20260922-unified-thread-closure/HANDOVER-20260922.json`
now records the observed candidate file hashes and artifacts, without backdating this observation.
Existing XML results confirm 153 GAME passes and five bootstrap passes/two skips; the latest CTest
log records 35 passes. These were read, not rerun. Earlier G0/G1/G2 keep their earlier artifacts.

### UI PT: partial implementation, not shared execution completion

`UiPathTracingProxy` is a common entry point and shares a `World` and geometry cache. However,
`PonderSceneRenderer` still owns a `WorldPipeline`; each view calls `WorldPipeline::create/init`,
with its own module resources and `WorldPrepareContext` TLAS. The shared composite compute pipeline
does not establish shared expensive PT/RR execution. Persistent composites and the size cap are real
improvements, but the claim of one shared execution pipeline/TLAS is not supported.

`PonderPathTracer.render` still supplies the whole physical window size. The 1920/1920x1080 budget
downscales that full frame (3840x2054 becomes 1920x1027), rather than selecting the actual UI viewport
and adjusting its crop/projection. In addition, `PonderScenePathTracingMixins` renders each captured
scene immediately. Native code updates only that scene's cached geometry before rendering the
combined map, so the first draw cannot include a not-yet-collected new scene's current geometry.
Cached geometry is removed on cache eviction/UI close, not when a scene stops being visible; inverse
pose normalization also needs an explicit common-space transform contract for interacting scenes.
Same-frame collection, removal, common placement and per-view histories remain source work before
the mutual-light transition gate. Do not replace the transition with screenshots.

### FG: real fractional alpha is present, background contract remains open

Tone mapping starts alpha at zero; `UIModuleContext::syncColorAttachments` accumulates source-over
alpha and `dlss_ui_coverage.comp` extracts it instead of binary RGB differences. The override applies
to all enabled main-target blend equations, so correctness for non-source-over RGB modes must not
be inferred from the scalar source-over test.

The full-screen blur path writes alpha one across its post target and copies that target back;
HUD-less is captured before these GUI effects and is not blurred by this path. At alpha one the
recomposition formula assigns no weight to HUD-less and classifies the processed background with
UI. The fixed Streamline guide's input table requires matching HUD-less/backbuffer postprocessing.
This identifies a material contract/experience boundary, not an observed claim that the SDK must
freeze the background: generated-frame behavior still requires capture and user observation.
`overlay/post/invert.frag` is a full-screen effect; it must not be conflated with the separately drawn
inverted crosshair. The current coverage unit test calls a scalar helper unused by production and
does not validate real final/HUD-less colors, blending, blur or crosshair composition. P2-02 stays
open; no acceptance of a visual downgrade is implied.

### Texture reuse: capacity bounded, ownership safety not closed

`TextureNamePool::release` makes an integer immediately reusable. The native upload queue has useful
protection: ordinary release flushes/waits, reload release erases queued entries, and submitted
batches retain actual destination images until their fence. These paths do not alone show that an
already-submitted native upload is redirected to a new image.

The Java producer boundary is different: `UnicodeTextureGlyphMixins.upload` may defer `_upload`
through `RenderSystem.recordRenderCall`, capturing only the integer ID. Native `queueUpload` resolves
that integer to its current owner. Release/reallocate between capture and execution is not rejected
by any generation check. This is a statically admitted stale-producer path; its frequency in ordinary
gameplay has not been measured. Also, retaining an old image does not freeze a descriptor entry:
`RayTracingModule::bindTexture` updates existing descriptor tables, so already-recorded references
need a separate retirement/descriptor-generation proof before reuse can be called safe. Existing
pool tests establish capacity/churn only, not these producer/consumer interleavings.

Release the acceptance hold only after the stale-producer path is repaired and exercised through
the real scheduling/upload boundary, and recorded/in-flight descriptor ownership is established.
The Ponder source gaps must likewise be completed or explicitly reported as unfinished; successful
menu/tests cannot substitute. No real device loss was induced, and neither its GPU root cause/native
close nor public binary licensing has gained evidence in this handoff.

## 2026-09-22: owner tickets, shared UI PT and FG input correction

Status: implemented; automated-verified; isolated runtime validation in progress; visual acceptance
pending. This supersedes the three source gaps in the preceding handoff check, subject to the FG
limitations below. It does not close the thirteen-item audit or the original device-loss case.
Applies to the dirty paired trees above `e1e91a2` / `9ebf73c`; no Git index/history operation.

### Ownership from Java producer to GPU retirement

`TextureTasks` is the production queue boundary for NativeImage and deferred glyph work. Each
allocation has an owner generation, and each image replacement/reload invalidates an image version.
Checking a ticket and invoking JNI use the same `TextureProxy.class` monitor as allocation/release.
Queued upload, cancellation and delayed release cannot acquire a recycled integer's new owner.
Invalidation cleans owned CPU payloads exactly once; renderer close cancels pending work even if
native cleanup fails. The reload lock order is texture owner before reload coordinator.

Native submission retains actual image objects, while RT commands acquire immutable descriptor
snapshots. Rebinding an integer changes the next snapshot; recorded commands retain their old
table, image and sampler until their frame retires. The 1..4095 name pool still rejects exhaustion.
There is no per-release global GPU idle. Tests exercise queued old upload/release/reuse, stale
cancellation, replacement/reload, normal upload, queue rejection and cleanup exceptions through
the production task helper; a separate descriptor test verifies old/new binding contents and
retirement. These deterministic tests do not establish unlimited real-driver churn stability.

### Actual shared execution and same-frame collection

Ponder's fixed-version `renderVisibleScenes` boundary declares the visible view IDs, captures both
scenes and ends the batch only after both have been received. Incomplete/duplicate/unknown batches
fail before execution. GUI quads retain their original draw order; the dedicated command buffer
executes between main-world work and GUI work. Readback rejects an incomplete UI batch.

One `PonderSharedWorld` owns the `WorldPipeline`, RT pipelines and common TLAS. Two bounded view
slots retain independent cameras, DLSS/NRD/upscaler histories and frame-ring descriptors; transient
dispatch images are shared between serial views. Shader-pack temporal images marked `shared` are
per-view, while immutable imported images and the common-world SHARC cache remain shared. Pipeline
builder workers size SBT arrays from the actual descriptor-context count, not thread-local scope.
The generic batch currently supports at most two distinct views; exceeding it reports an error.

Current-frame scene poses map geometry into a common anchor; primary rays select their view owner,
while reflection/shadow/indirect rays see the combined geometry. The second view reuses the TLAS
built after all geometry has been collected. Old nonvisible owners are pruned. Unchanged vertex
data reuses BLAS; material-only changes upload replacement material data without rebuilding BLAS
when converted position/index topology matches. Shape changes build new geometry.

Java supplies the projected physical content rectangle, with an outward 64-pixel allocation grid
and an 8-pixel filter guard. Crop projection and GUI quad NDC coordinates describe that rectangle.
Native execution retains the existing longest-side 1920 / area 1920x1080 budget and can retain a
larger prior extent until close. Per-view outputs are reused per in-flight frame; replaced engines,
images and descriptors retire behind frame fences. This does not claim zero SDK-private memory.

### FG input contract and remaining mathematical limits

World RGB is separated from coverage at the actual world-to-GUI boundary by a compute pass that
sets only alpha to zero. Ordinary source-over uses fractional alpha. A real preflight identified
another producer: vanilla vignette uses ZERO / ONE_MINUS_SRC_COLOR with source alpha one. Treating
that as source-over made almost every pixel UI, even after boundary reset. The production blend
helper now preserves accumulated alpha for source-only multiplication and repeats that modulation
on HUD-less using the same draw, viewport and scissor. This preserves
`M*(U+(1-A)*H) = M*U+(1-A)*(M*H)` without reclassifying the entire world as foreground.

Full-screen menu blur runs the same filter/radius on HUD-less as final color, preserving coverage.
World entity post effects recapture the processed scene before HUD drawing. The resource check uses
the fixed [Streamline 2.14.1 guide](https://github.com/NVIDIA-RTX/Streamline/blob/v2.14.1/docs/ProgrammingGuideDLSS_G.md):
HUD-less and final RGB must share postprocessing, and `F=U+(1-A)*H` must describe ordinary UI.
One-shot `radiance-fg-capture.request` exports final/HUD-less/alpha only after a GPU completion marker.

P2-02 is **not fully closed**. A local inverse-color crosshair depends on the generated background
with negative slope; ordinary nonnegative source-over alpha cannot express that operation exactly.
The fixed public SDK interface has no generic application callback to replay arbitrary GUI draws
on each generated frame. The existing local opaque footprint can therefore retain a real-frame
background detail. In addition, blurring already-composited, spatially varying UI has a covariance
term: `L((1-A)*H)` is not generally `(1-L(A))*L(H)`. Nested background-dependent UI is not declared
equivalent by the matched-blur implementation. Depth/stencil-dependent or self-sampling modulation
is outside the source-only modulation proof. No global FG disable, effect removal, screenshot
replacement or user approval of a visual compromise is implied. These precise cases remain visual
and product-contract gates rather than being silently renamed as complete.

### Failures retained and directly related fixes

The new automated client preflights exposed defects that a menu smoke had not exercised:

1. Uninitialized important-upload batch on the first UI geometry build: initialize it before queueing.
2. A consumed transient upload replayed in the same frame: detach each queued batch before recording
   and retain it until its fence. Validation identified `vkCmdCopyBuffer` with a null source buffer.
3. UI view index 5 addressing an SBT array sized for three physical frames: use the six actual
   descriptor contexts. Bounds-checked access preserves a diagnosable failure instead of undefined
   access.
4. Shared mapped execution parameters were overwritten by a later recorded pass/view: use inline
   `vkCmdUpdateBuffer` data with explicit read/write ordering. A GPU test records two different
   values, mutates the CPU array before submission and verifies both captured values.

Preflights 1..4 and the broad-alpha captures from 5..6 remain failed/intermediate evidence, not
passing results. The older genuine `VK_ERROR_DEVICE_LOST` plus Windows Event 153 incident is a
different, still-unexplained event. The targeted validation run also reported other FG/swapchain
synchronization/private-data diagnostics; this work does not claim a clean full validation-layer
run or treat every SDK diagnostic as a proven application defect.

### Evidence index

Machine evidence is retained in the ignored, non-portable directory
`D:\Workspaces\Repositories\GitHub\RecRivenVI\Radiance\build\manual-acceptance\evidence\20260922-ownership-ui-fg`.
Its `MANIFEST.txt`, source ZIP/JSON, per-process DLL/PDB/logs, capture analysis and `ACCEPTANCE.md`
identify the final candidate separately from every failed preflight. Source manifests include all
tracked and necessary untracked files, deletions, raw hashes, explicit CRLF-to-LF comparison hashes,
generated JNI/diagnostic inputs and dependency identities. They do not repair the old missing
untracked-source fingerprint retroactively. Build/GPU/runtime/visual outcomes remain separate;
the final run counts and artifact identities are appended to the development ledgers.

### Later runtime corrections and explicitly failed paths

The same-date sequence continued after the preceding entry:

- An asynchronous FG present returning OUT_OF_DATE had permanently marked generation failed.
  `fg_present_policy.hpp` now requests a surface check/history reset for OUT_OF_DATE/SUBOPTIMAL,
  preserves a previous fatal, and reserves fatal publication for real errors. The production
  callback policy is exercised by the failure-state tests; no device recovery is introduced.
- The Advanced shader's direct-light include used the shared camera helper without including its
  declaration. The actual client compiler rejected it in preflight 8. Adding `camera_ray.glsl`
  allowed compilation in preflight 9; that later process instead suffered genuine device loss.
- Preflight 9 (PID 61436) failed at 18:53:36, before Ponder, resize or reload, at 2560x1440 with
  Advanced. The first native error was query-pool timestamp result -4. Windows recorded three
  nvlddmkm Event 153 records. All dimensions saved at 18:53:36.797 and renderer close completed
  at 18:53:37.362 with unchanged first error and submission count. No artificial loss was injected.
  GPU root cause remains unknown; neither Event 153 nor memory counters identify it. This is new
  close evidence for this process only, not retrospective proof for the older loss incident.
- Preflight 10 (PID 5696, Vanilla PT/RR/FG) completed normal shutdown, but after the scripted
  3840x2054 resize Streamline reported VRAM-budget warnings. RR treated successful evaluation as
  failed and used its spatial fallback; FG options/state also returned the warning. The source of
  the fixed SDK's common evaluator shows that eWarnOutOfVRAM is appended only after successful
  begin/endEvaluate. The new production completion predicate accepts that result specifically for
  evaluation, preserves valid output/history and records feature allocation for eventual release.
  It does not reinterpret options/state/tagging warnings as successful calls without evidence.
  Budget pressure remains observable and is not solved by reclassifying the result.

Reference for the bounded result correction:
[Streamline 2.14.1 common evaluator](https://github.com/NVIDIA-RTX/Streamline/blob/v2.14.1/source/plugins/sl.common/commonInterface.cpp).
Tests reject all error results and unknown enum values while accepting completed evaluation and its
post-execution budget warning. This helper test is not a GPU-memory exhaustion test.

Preflight 10's initial world FG capture contains 3,609,840 alpha-zero pixels with exactly equal final
and HUD-less RGB, 34,095 fractional pixels and 42,465 opaque pixels at 2560x1440. Its Ponder capture
contains 3,292,854 fractional pixels. Neither exceeds the nonnegative premultiplied foreground bound
beyond the declared 2/255 quantization tolerance. Later requested captures were not produced after
the SDK warnings: those cases are **not passed**. Preflight 7's earlier pause-blur capture belongs to
its older DLL; it must not be relabeled as final-candidate evidence. Actual generated display frames
and local inverse-crosshair experience remain unaccepted.


## 2026-09-22 correction: reachable FG effects and Advanced parameters

The local-inversion argument in the preceding handoff assumed unmodified HUD-less input. Actual
1.21.1 Gui.renderCrosshair is affine in its destination; replaying that transform on HUD-less
preserves fractional alpha and the normal RGB composition. The implementation now snapshots
pre-draw depth/stencil for the replay. This is not evidence that the fixed SDK exactly reconstructs
a post-generated-frame screen-space effect; dynamic anchoring and blur edges remain visual gates.

The ordinary HUD precedes Screen.renderBlurredBackground, so blur over existing UI is reachable.
HUD-less now uses L((1-A)H)/L(1-A), computed from pre-pass coverage before interpolation. The first
new real-client capture exposed the additional std::round/GLSL round half-radius mismatch; this
was repaired without changing the normal final-color pass. First and retest artifacts remain
separately pinned, with quantitative capture checks and production-helper GPU regressions.

Directed Advanced investigation found repeated retargeting of one execution descriptor to different
per-pass buffers. UPDATE_AFTER_BIND does not snapshot binding values per recorded draw. RT and
post stages now share one stable execution buffer per stage and use ordered inline command values.
The real compute regression reads distinct per-pass values; this confirms the fix's contract, not
that it explains the historical device loss. The prior inline-update fix remains necessary.

Historical PID 61436 (preflight9) first detected -4 in timestamp-query retrieval, not a proven
faulting command. It saved all dimensions and returned from native close; later successful Vanilla
PID 84892 used a different DLL/configuration. Exact older source provenance is not fabricated.
See the latest two development-ledger entries and the retained ADVANCED-INVESTIGATION.md under
`build/manual-acceptance/evidence/20260922-fg-boundaries-advanced` for inspected paths, excluded
hypotheses, hashes, limitations and the final unified matrix. P1-03/G3 and P2-02 are not promoted
to whole-system or generated-display acceptance. No staging, commit or publication occurred.


### Correction after final FG capture and new Vanilla loss

The paired ledger's additional capture correction records a real blend-disabled coverage defect,
its Java-to-Vulkan behavior regression, six passing candidate3 input captures, and the subsequent
FG-off natural Vanilla DEVICE_LOST. This supersedes the preceding Advanced-only manual exclusion.
The failure also proved that recorded fatal could return from a lower module and allow a later
frame submission; the stage-boundary repair has an injected no-GPU regression. Neither that repair
nor stable pass execution bindings establishes the cause of either natural GPU loss. Candidate3's
successful and failed processes remain separate from the final stage-guard artifact. G3 has fresh
first-error/save/native-close-return evidence; root cause, exact faulting resource and SDK GPU-side
retirement remain unconfirmed. P2-02 still requires generated-display visual acceptance. See the
latest ledger and evidence MANIFEST before starting any further world acceptance.

## 2026-09-22 directed first-GPU-fault investigation (append-only correction)

Status: investigating; the first GPU fault remains distinct from P1-03 error containment.
The retained evidence root is `build/manual-acceptance/evidence/20260922-first-gpu-fault`
(local/ignored). `EXPERIMENTS.md` fixes variables, actual duration/resolution and stop rules.
The prior final 432BCDAC/A2356F8A menu-only package, earlier Advanced loss, candidate3
Vanilla loss and later diagnostic packages are not combined into one runtime result.

Confirmed source defects repaired in this investigation:

- Device creation did not enable supported Vulkan13 `privateData`, while the fixed
  Streamline2.14.1 `sl.common` creates a private-data slot. Core validation emitted
  VUID04564 before the feature change and did not emit it in the subsequent actual
  SERVICE-to-GAME device runs. The feature change is not proof of GPU-fault causality.
- Tone mapping writes LDR through COLOR_ATTACHMENT_OUTPUT. Three directly related
  post-color handoff/reuse/finalization barriers omitted that producer. The shared
  production helper and real color-attachment/readback+SyncVal regression cover this
  dependency; the application's corresponding warning disappeared in E2b. Opaque
  `nv.ngx.dlssd` clear/fill hazards remain separate, without app/SDK causal attribution.
- Command-buffer begin errors now publish/propagate failure before command recording.
  No global idle, broad barrier or feature disable was introduced as a product fix.

A diagnostic false positive was independently established: matching VVL1.4.341.1
state tracking removes all later AS-address registry entries when one AS is destroyed.
The legal `as-probe` fixture registers256 live BLAS, destroys only a separate unused AS,
then uploads/builds/readbacks a valid TLAS. Core passes four rounds unchanged; GPU-AV's
AS checker emits VUID12281 and rewrites all256 valid references to dummy geometry.
Disabling only that checker restores four unchanged rounds. Actual faulty implementation:
https://raw.githubusercontent.com/KhronosGroup/Vulkan-ValidationLayers/vulkan-sdk-1.4.341.0/layers/state_tracker/state_tracker.cpp
The matching `tlas.comp` rewrites references. Archived source, fixture source/build/logs
and exact CPU ownership correlation are retained. This proves a tool defect, not an
application-AS use-after-free and not the cause of unvalidated Minecraft losses.
E3's real GPU fault is retained but confounded by that validator's input mutation.

E5 selective app-shader GPU-AV (broken AS checker disabled) ran52s without validation
error/loss at1-6fps; it heavily changes timing. The requested10s duration had not reached
the separately built audit binary; the observer stopped it and the diagnostic build
chain was corrected. Planned720p was also superseded by actual2560x1440 launch args;
no earlier record is rewritten as720p. E6 uses the rebuilt duration audit and normal
configuration: no validation layer or loss tracker. PID36856 lost the device after
about5s in-world at2560x1440, Vanilla PT/RR Balanced/FG off/Reflex1/8chunks, before
Ponder, resize, reload or player operation. Java retained the first error,352 successful
submissions did not increase during close, all dimensions saved and native close
returned exactly once. These repairs therefore do NOT remove the world-entry blocker.

Directed inspection covered per-frame descriptor ownership, delayed texture ownership,
BLAS/TLAS shared owners, build-batch retirement and upload input retention. No further
causal defect was established in those inspected paths. It does not prove the entire
resource system correct. CPU command records are not GPU completion. Query/fence/submit
-4 identifies detection, not the instruction that caused loss. Pipeline-layout keepalive
remains an evidenced workaround; no driver/hardware root cause is asserted.

Capture now adds default-off EXT fault queries, GPU checkpoints, shader-module binary
indexing and a fixed AS ownership ring. External local Aftermath is loaded by an ignored
JVM agent before SERVICE creates the actual device, never from the mod archive. SDK
identity, bounded callback storage and once-only maximum5s post-save/pre-native-release
status wait are evidence-indexed. It neither waits for GPU idle nor enables additional
shader-error faulting. The first capture preflight found incorrect literal flags in the
agent; normal stop preserved evidence, and installed Vulkan enum constants corrected
it to shader-debug1|resource-tracking2. Subsequent dump findings and final artifact
identity are recorded in the paired ledger and evidence manifest, not inferred here.

Generated-display FG visuals, full G3/root cause, long churn and public DLL authorization
remain unclosed. All inherited source fixes and necessary untracked files are preserved;
no staging, commit, push, production-world access or evidence deletion is part of this work.

### 2026-09-23 final bounded control

The final paired-ledger entry pins diag6 after explicit diagnostic-feature enablement.
Corrected resource-only Aftermath PID36448 and normal no-agent/no-trace PID60552 each ran
20s in-world and saved/closed normally, with separately verified loaded DLLs. No natural
fault occurred in these processes, so no Aftermath dump or fault-wait runtime result exists.
These bounded successes do not erase E6 or establish the first GPU cause. Full world/Advanced/
FG visual acceptance remains held; the exact missing capture and next discriminating control
are preserved in the evidence ACCEPTANCE.md. The final source snapshot covers all inherited
and new untracked inputs, with later documentation-only sealing; no code or Git history was
rewritten to manufacture a stable baseline.


## 2026-09-23: Bounded local closeout and acceptance checkpoint

Status: implemented and automated-verified within the scopes below; final world preflight failed;
manual visual acceptance pending; first GPU cause investigating; public binary release blocked.
Evidence: static, build, automated, GPU fixtures, deployment, and one failed real-client world run.
Applies to the preserved cumulative worktrees over Radiance `e1e91a2335a5939ef7bb5bf0955bff2c1d03f10f`
and MCVR `9ebf73cc57290dbbdc9a6dbdd8f19f5989c45dbd`, plus the two native boundary repairs below.
Supersedes the preceding unconditional world-acceptance/amend hold by the user's explicit decision.
It does not supersede any failed run or turn previous artifact successes into this artifact's passes.

### Scope and original thirteen findings

This is a bounded check of the cumulative candidate diff and its direct production consumers, not
an exhaustive re-review of unchanged rendering code or third-party implementation. Entry inventory
contained 37 Radiance and 120 MCVR changed/new paths, with empty indices. All entry candidate bytes
matched the preceding `20260922-first-gpu-fault/final-sources.json`. Two required native headers were
then added, making 122 native candidates. Deleted old Ponder JNI code is intentional; the new UI PT
JNI source and all untracked Java/native/shader/test files belong to the source candidate.

| Finding | Locally checked production path and disposition | Evidence and remaining boundary |
| --- | --- | --- |
| P1-01 | Acquire and recreate use separate state; only a successful acquire authorizes a frame | Production `FrameAcquireAttempt` behavior tests pass; continuous resize/fullscreen on this artifact pending |
| P1-02 | Streamline absent plus FG off is an optional-feature no-op; enabling an unavailable feature fails explicitly | Optional-feature policy tests pass; not a new non-NVIDIA-machine runtime claim |
| P1-03 | JNI/C ABI/worker guards, sticky first cause, independent lost latch, save-before-native-close and checked stages retained; additional upload/command holes repaired below | Failure injection and native tests pass; this real loss saved all dimensions and closed native once; first GPU cause/full G3 remain open |
| P2-01 | Failed SR/RR evaluation selects same-frame fallback/reset rather than consuming unconfirmed output; completed-evaluation warning handled separately | Evaluate-state/result tests pass; all vendor failure modes and display recovery not runtime-accepted |
| P2-02 | Real fractional UI coverage, unblended coverage, affine local inversion and weighted post-UI blur reach HUD-less/alpha/final tagging | Shader/GPU reconstruction checks pass; focused SDK-generated-frame motion, hard edges and background effects still require user acceptance |
| P2-03 | Shared camera-ray logic supplies orthographic and perspective priority/background rays | Camera behavior and shader checks pass; current Ponder nameplate/outline alignment visually pending |
| P2-04 | External native slots reuse encoded generation handles; old generation publication rejected and resources retained separately | Handle churn/lifetime contracts pass; long Sable churn with GPU frames still pending |
| P2-05 | Main and external paths reserve capacity before expensive region preparation; external frame budget and recoverable deferral retained | Actual scheduler tests exercise saturation/rejection/failure/old owner; sustained main-priority load fairness is not proved by bounded tests |
| P2-06 | Unconsumed Flywheel dynamic light-section production/upload is stopped; appearance/crumbling data retained | Producer-to-consumer static check and instancing contracts; no measured large-Create FPS claim |
| P2-07 | One shared UI world execution pipeline, per-view histories, current-frame scene batch, real projected viewport/budget and geometry/composite caching | Actual helpers/scene/budget tests and build pass; visual mutual lighting and 3840x2054 whole-process memory/churn comparison pending |
| P2-08 | BOOL/BVEC and UINT/UVEC logical declarations preserved through uniform translation/packing | GAME behavior tests pass; arbitrary Veil programs are not exhaustively accepted |
| P2-09 | Maven GAME artifact intentionally remains a development dependency, distinct from the SERVICE+nested GAME installable package | Both artifact-purpose checks pass; no requirement to turn the GAME publication into an installer |
| P3-01 | Child assertions return through Future.get rather than unobserved Thread.join | Actual JUnit suite passes; no claim that source-only checks prove thread behavior |

### Expanded scope, ownership and known limits

| Area | Source conclusion and retained contract | What is not established |
| --- | --- | --- |
| Native A/B/C/D | Partial thread launch cancels/joins; minimum failure state is allocation-independent; later loss latches without replacing first cause; JNI strings own length-delimited terminated copies and release borrowed chars | No unsafe real resource-exhaustion/loss injection was performed; fake callbacks do not prove vendor callback failure behavior |
| Client and resource boundaries | SERVICE locator/bootstrap/provider check client launch target; client Mixin gate retained; logical server reload differs from client manager identity; getResource/listResources/listResourceStacks use actual CLIENT_RESOURCES and preserve metadata/stream ownership | New dedicated-server launch and every third-party reload path were not run; physical client alone is not the resource discriminator |
| External scheduling | Shared important-executor admission, eight-preparation frame budget, generation/owner checks, deferred unavailable data, release/reset/world-switch cancellation remain in actual ChunkProxy path | Unit scheduler evidence does not replace live Sable split/unload/F3+A/world-switch acceptance on this package |
| Texture ownership | Java queued tasks capture owner/image version and hold the monitor through validation/use; cancellation/release cannot target a reused name; native upload batches retain actual destinations; descriptor snapshots retain old bindings; pool bounded to 1..4095 | Fixture reuse/order tests pass, not indefinite resource-reload stability; no per-release global idle was added |
| Upload/descriptor lifetime | Last-batch retirement is polled even without new upload work; staging cache bounded; immutable cross-pass texture bindings and checked command boundaries retained | General allocation-exhaustion recovery and every GPU interleaving are not proved |
| Shared UI PT | A shared WorldPipeline executes collected current-frame participants into a common scene/TLAS; view-specific camera/history and frame-slot output ownership remain separate; secondary rays can see other participants; unchanged geometry avoids BLAS rebuild | Helper counts do not measure vendor SDK allocation; two-view transition history, resize/high-water capacity retirement and output sharpness need real observation |
| FG composition | Actual UI alpha/weight and source-bound affine replay/weighted blur preserve real-frame drawing order; PT outputs use the same final composition path | Fixed SDK input reconstruction is not a post-generated-frame GUI callback; dynamic inversion anchoring, blur behavior and arbitrary nonstandard logic blends remain visual/compatibility limits |
| Temporal inputs | Priority/background supply depth/motion/albedo/normal-roughness; SR/RR camera/reset paths and evaluated-viewport-only release checked | Moving priority-entity object motion, multi-layer physical alignment and every denoiser combination are not proven by static contracts |
| Precision/Veil | Flywheel integer origin is subtracted from double camera before float conversion; relevant Veil uniform/sampler/capture failure paths retained | Absolute light-grid precision and arbitrary Veil framebuffer/shader paths are outside this bounded pass |
| GPU investigation | privateData, post-color producer stages and per-pass execution-parameter buffer changes are confirmed source repairs; default-off bounded trace/externally enabled Aftermath hooks are diagnostics | No evidence identifies them as this first loss's cause. Installed VVL 1.4.341.1 AS-checker defect is limited to the independently reproduced registry/input-rewrite case; other validation reports cannot be ignored |
| Product semantics | Pipeline-layout token workaround retained; first-person ray visibility, priority redraw, alpha coverage versus physical transmission and joint transition lighting unchanged | Workaround efficacy is not Vulkan driver-root-cause proof |
| Release | Local package marks redistribution unapproved; public-distribution task rejects it; Maven development package contains no bundled runtimes; no repository workflow was found | NVIDIA/vendor redistribution authorization, notice/notification obligations and legal compatibility remain external conditions |

### New confirmed gaps and minimal repairs

1. Texture retirement could record a failed fence poll and return, allowing the same caller to
   prepare more uploads before the outer stage guard ran. Upload-submit failure likewise returned.
   `render/upload_retirement.hpp` now drives the real batch loop: completed batches recycle,
   not-ready batches retain ownership, any error publishes the original failure and unwinds
   immediately, preserving failed/unpolled batches for safe shutdown. `Textures` uses that helper
   and immediately propagates submit failure. The regression invokes this real loop and checks
   poll/recycle counts, retained payloads, sticky second-call rejection and normal retirement.
2. `CommandBuffer::end/reset` ignored VkResult even though callers chained recording/submission.
   `vulkan/command_result.hpp` guards actual begin/end/reset before and after the call and propagates
   the original result. Failure-state tests inject each operation, assert no continuation or second
   Vulkan invocation after fatal, and check successful operation in a fresh test-only state.

These are source-confirmed post-error boundary defects. They do not explain why the GPU first failed.
No GPU loss was fabricated, no extra global idle/barrier/retention workaround was added, and no
feature was disabled as a repair. Existing lifecycle fault injection and local capture remain opt-in.

### This checkpoint's executed validation and real fault

- GAME: 165 tests, zero failures/errors/skips, freshly rerun with bootstrap and artifact checks.
  Bootstrap: 5 passed, 2 opt-in GPU cases skipped. Java sources did not change afterwards.
- Native final RelWithDebInfo INSTALL and shader build passed after both repairs; CTest 43/43
  passed, including seven actual Vulkan GPU cases (tessellation, vertex arrays, exposure,
  framebuffer, post-color synchronization, FG UI composition, execution buffer). Several other
  contract tests are source scans and only supplementary evidence; they are not all behavior tests.
- Final package/bootstrap/runtime-resource/Maven-purpose validation and full isolated deployment
  passed. `verifyPublicDistributionGate` failed with the expected explicit NVIDIA authorization
  blocker; this is a successful gate rejection, not a publicly releasable build.
- Final normal PID30596 started 2026-09-23 00:37:55 +0800, reached world-ready at 00:38:54 and
  naturally lost the device near 00:39:06, before the requested 20-second observation completed.
  Vanilla PT, RR Balanced, FG off, Reflex 1, 8 chunks, 2560x1440, driver 616.92; no VVL,
  Aftermath, host loss trace, Ponder, reload or resize. Initial GPU memory was 7142/16376 MiB.
  The first detected error was `vkWaitForFences(frame)` result -4, not an identified causal command.
  Windows nvlddmkm Event 153 occurred at 00:39:04 and 00:39:06.
- All three dimensions and the integrated server saved. Before/after native close, submit
  attempts/successes stayed 242/242; first operation/result remained unchanged; closeCalls became
  one at 00:39:07. Exit code was 1 at 00:39:08. No automatic restart followed. This validates these
  observed failure/save/close facts, not a normal lifecycle pass, complete async SDK shutdown,
  root-cause repair, full G3 or long stability.

Earlier diag6 normal/capture 20-second successes and earlier G0/G1/G2 remain tied to their original
packages. They are not combined with this failed package to claim an all-passed candidate. The
evolving isolated Minecraft/Sable save is not a byte-identical historical A/B control.

### Exact evidence and acceptance disposition

Paired native source checkpoint: MCVR `2f62a34e768a07c6dd4fd9e024f4bd7a2148ca35` (local SSH-signed Initial port).
This is a one-way reference; the native commit does not embed this Radiance checkpoint SHA.

Non-portable retained evidence root:
`D:/Workspaces/Repositories/GitHub/RecRivenVI/Radiance/build/manual-acceptance/evidence/20260923-local-closeout`.
`product-sources.json` and `product-source.zip` preserve tracked files, required new files, deletions,
gitlink identities, dependency digests and ignored diagnostic/build inputs. ZIP bytes are exact;
explicit text extensions normalize CRLF to LF only for comparison, without trimming or BOM changes.
The normalized product digest excludes docs/Markdown. Later record-only edits are in `final-sources.json`.

- Product source ZIP SHA-256: `5CD7294FBCB98142F77598AF1B065246ACF84298824948008F76F66E46C2CE88`.
- Normalized product SHA-256: `07744F338FF6289BA044224C761D0E3CB32F3B852DAC6FA19CDB7AB5E193CC6F`.
- JAR: `CBDEBEB8EB8011831F04C06920D54531D8092CFAC45E507903C9073DB94D5451`.
- core.dll: `9DC8A04445541A5FB72E84F4D48E3ABFA95456DB122AED46C4DDABEC18B56C99`.
- Matching PDB: `6FE66854C3EBB1C9DAE3B43321456F304A9399A91DF560BBB6A9B88093DE6916`.
- `product-artifacts.json`: complete runtime/shader/mod hashes; built, embedded, extracted and
  PID30596 loaded core.dll agree. `normal-closeout-loaded.json` pins actual module paths.
- `java-gates.log`, `native-final-build.log`, `ctest-final2.log`, `package-final2.log`,
  `product-test-results.zip`, and `public-gate.log` separate this run's checks from historical results.
- `normal-closeout-*` retains process/configuration/crash/latest logs, Windows events and outcome.
  `MANIFEST.txt` and `ACCEPTANCE.md` index the manual launcher, cases and evidence responsibilities.

The user removed an established-first-GPU-cause prerequisite for normal manual acceptance and a
local source checkpoint. That decision does not turn this failed preflight into a pass. The failed
case is paused; no blind rerun or Advanced/high-distance/resize stress is requested. A hash-checked,
non-injecting manual launcher is prepared for a user-chosen normal continuation. Each natural loss
stops that case and preserves evidence. Generated-frame visual acceptance requires focus plus
actual SDK generation evidence. Unknown GPU root/full G3, current manual results, sustained
resource/performance behavior and external binary authorization remain open at amend time.

## 2026-09-23: Manual feedback correction after the frozen checkpoint

Status: limited runtime-observed and user simple-play observation accepted; broader acceptance open.
This appended correction supersedes only the all-manual-results-pending statement above. The
[manual feedback ledger](../DEVELOPMENT_LEDGER.md#2026-09-23-frozen-package-manual-feedback-and-source-synchronization-scope)
records the configuration, exact artifact, timestamps and local evidence index; no product changed.

Paired native checkpoint for this record-only source synchronization:
MCVR `8208f305a71d0ffa56e761cd7b62c1b667572cb4` (SSH-signed Initial port).
It supersedes the earlier native pairing for subsequent source synchronization only; the frozen
runtime/source evidence above remains attributed to its original checkpoint. Native product files
are byte-identical to that checkpoint; only its development ledger changed. No reciprocal SHA is
backfilled into MCVR, and this Radiance commit does not include its own final SHA.

- Manual PID73024 used the frozen CBDEBEB8... JAR / 9DC8A044... core (full hashes above), entered
  New World and Test separately, saved both, closed native once with15175/15175 unchanged
  submissions, fatal/deviceLost=false, and exited0. The user reported no obvious problem and
  ended the observation round. Same-save reopen/edit persistence is not established.
- Manual PID36632 was menu-only. Manual PID92452 instead failed initialization with a null
  TextureManager reached by early framebuffer resize/texture release. Its archived crash and
  zero-submission/native-close evidence establish a separate unresolved startup defect. No repair
  or forced reproduction was authorized in this closeout.
- Automatic PID30596's approximately12s in-world GPU failure remains a failure of this identical
  package, with its original save/close evidence. Later success does not establish its root cause.
- Ponder, actual generated-FG visuals, resource/chunk specialist cases, save/reopen, sustained
  performance, complete G3 and external binary permission retain their separate open boundaries.

This follow-up reviews existing evidence and changes maintenance records only; historical tests
are not claimed as rerun. The user's source synchronization authorization does not depend on
closing every runtime/visual/external gate and does not permit binary publication.

## 2026-09-23: Scoped face-state and chunk scheduling follow-up

Status: implemented; bounded automatic/GPU fixtures passed; live comparison and visual acceptance
pending. This is an appended correction/extension, not a reclassification of the original thirteen
findings as universally accepted. The [paired implementation ledger](../DEVELOPMENT_LEDGER.md#2026-09-23-per-draw-face-rules-and-interaction-aware-chunk-scheduling)
records current source paths, intermediate failures,40-ray GPU behavior,48-test native result,
private diagnostic references and the pending performance gate.

Confirmed roots: per-draw enable/mode/winding was not propagated consistently; mixed one/two-sided
opaque BLAS needed per-geometry rejection; Java interaction priority was delayed/coalesced with
initial loading; blocked heads could retry in one round; native priority/publication semantics and
frame-count batching obscured interaction latency. Fixes retain camera-independent complete scene
participation, hardware fast paths where equivalent, generations, safe publication and retirement.
An intermediate CW bit15 collision and a possible pre-Mixin GL cache-target rewrite were caught
and corrected before delivery. Do not use the initial A artifact to claim corrected acceptance.

Performance tuning is not accepted merely because code now has a bounded selector or priorities.
Matched baseline/A/B isolated worlds, actual loaded identities, p50/p95/p99 interaction/real-frame
samples, throughput and resource peaks are still required. CPU-observed fence completion and
semantic counters are not displayed-pixel proof. No cause of the earlier device losses is inferred.

Scoped checkpoint update (2026-09-23, before client comparison): the first native aging candidate
was corrected to weighted interaction/background admission; an aged queue cannot move wholesale
ahead of a fresh player update. The selector tests include a thousand old owners and continuous
input with one-section batches. Final automatic gates are GAME 174 passed, bootstrap 7 passed /
2 skipped, native CTest 48/48; package identities and evidence are in the paired ledger and the
local `20260923-faces-chunks/deployment.json`. First-publication tracing and byte accounting were
corrected in every comparison build. None is a measured speedup or real producer visual result.
Another user-owned client is a pending comparison condition; no game was launched to bypass it.

2026-09-23 startup correction: real PID46548 disproved the dry-run startup assumption.
A broad SERVICE target set triggered frame resolution of Veil optional Sodium classes.
Targets now require actual GL call sites; bootstrap8 passed /2 skipped and packaging
passed. See the [startup correction ledger](../DEVELOPMENT_LEDGER.md#2026-09-23-face-interception-startup-correction).
Corrected runtime/visual and matched performance results remain pending.

2026-09-23 manual follow-up: PID68896 produced user acceptance of the observed face,
chunk-update and Sable cases, plus six distinct visual reports. Ponder appearance and
resource/history boundaries remain open; no quantitative speedup is inferred. The process
and observer later disappeared without final exit/close evidence; no agent termination
command or established GPU-loss cause. See the [feedback ledger](../DEVELOPMENT_LEDGER.md#2026-09-23-manual-facechunk-feedback-and-six-visual-regressions).

2026-09-23 visual-feedback correction: bounded inspection confirmed unstable moving Ponder
viewports, retired SDK-history overlap, cross-world primary-continuation filtering and
Sable ambient light misclassified as particle emission. These paths were corrected with
behavior/GPU regression coverage; GAME177, bootstrap8/2skip, CTest50/50 and packaging passed.
F3+G replacement belongs to the active Sable loaded-chunk debug setting, not a confirmed
Radiance topology defect. Lichen and complete water/edge appearance remain pending.
See the [feedback correction ledger](../DEVELOPMENT_LEDGER.md#2026-09-23-ponder-transition-and-particle-feedback-corrections).
These results do not close GPU-loss causation or substitute for visual acceptance.

### 2026-09-23 correction: Ponder PT archive and actual Create configuration failure

The user superseded Ponder's PT/mutual-transition-lighting requirement with default raster
rendering. P2-07's implementation/evidence remains preserved; its reported visual failures and
PT crop/reflection follow-up are archived rather than accepted as repaired. The two PT Mixins are
unregistered, and original Ponder scene/camera/drawing order runs through Vulkan GUI translation.
See the [archive](../history/ponder-pt-2026-09-23.md) and
[paired change/evidence ledger](../DEVELOPMENT_LEDGER.md#2026-09-23-ponder-raster-restoration-and-create-stencil-crash).

PID92456 supplied a separate confirmed CPU native crash: DirectFaceState forwarded Catnip's raw
STENCIL_TEST toggle to contextless OpenGL. Supported capability calls now use existing Vulkan
setters; unknown ones fail explicitly. This is not evidence of the historical device-loss cause.
GAME180, bootstrap8/2skip and packaging passed; actual config/Ponder screen smokes are bounded
runtime evidence, not user visual acceptance. No native product changed in this sub-batch.
The user's Sable particle and observed automatic-translation acceptance is recorded with the
original package; missing-pixel lichen, new raster visuals and prior unrelated gates remain open.

### 2026-09-24 targeted raster/Simulated follow-up (not a new whole-tree audit)

The user authorized repairs following the raster/Ponder retirement assessment and source comparison
of diagrams, spring stress and staff rendering. Raster-scope/shadow gaps and external fragment
coordinate spelling differences were repaired. Ponder-only command work is now lazy, with behavioral
tests and original raster Ponder smoke coverage; P2-07's archived PT implementation is not reactivated
or pronounced visually repaired. Shared ownership/synchronization/temporal/fatal fixes remain.

Diagram fade lost gradient levels and used the wrong logical origin; the repair has production-helper
GPU evidence. Spring stress is retained in raster and shares PT albedo recoloring with hurt surfaces.
Staff waves and camera-hidden physical lock geometry now enter the world sink, with depth-independent
priority redraw for locks. Final ordinary cutout and text coverage remain distinct. The first new
Mixin's static signature and subsequent diagnostic-fixture errors were corrected and retained in
the [ledger](../DEVELOPMENT_LEDGER.md#2026-09-24-raster-scope-diagram-fade-and-simulated-staff-integration).

GAME185, bootstrap8/2skip, selected native14 and ten affected hit-shader compilation results are
indexed there with exact candidate identity. No universal raster parity, GL image baseline, measured
performance gain, Advanced runtime acceptance, moving rope alignment or full diagram equivalence
follows from them. Projected-bound greeble placement and cached-refresh differences remain explicit;
the original GPU-loss cause, lichen and redistribution gates are not closed by this work.

Packaging follow-up, 2026-09-24: NeoForge 21.1.251 and outer launcher metadata were implemented
without converting the SERVICE archive into a second GAME mod. Actual-archive/loader tests and
an isolated menu run passed; the [ledger](../DEVELOPMENT_LEDGER.md#2026-09-24-neoforge-211251-and-launcher-visible-distribution-metadata)
pins the replacement Prism artifact. This does not extend P2-09 into a new Maven installation
contract or close outstanding renderer/visual/licensing acceptance.
### 2026-09-24 paired source checkpoint before standalone replay

Paired native checkpoint: MCVR `4778983778d53084132ce84ed0e567579ed6ed7b`
(SSH-signed `Initial port`, original parent/metadata retained). This supersedes the earlier
`8208f305...` source pairing for the accumulated face/chunk/raster/Simulated work, without
rewriting its historical tests or acceptance. The Radiance commit carrying this entry does not
backfill its own SHA; MCVR does not refer back to it.

All product/build/test files were compared against the exact-byte
`20260924-neoforge-metadata/final-source.json` snapshot; only later maintenance records differ.
Necessary untracked source, headers, shaders and tests are included in the candidate. No current
product rebuild or new gameplay result is implied by amend. The original thirteen findings,
archived Ponder PT issues, measured-performance gap, raster/Simulated visuals, first GPU fault
and public-binary licensing retain their preceding evidence boundaries. Standalone same-scene
replay is separate follow-up work, not part of the validated product snapshot.

### 2026-09-24 diagnostic ownership correction

The optional diagnostic implementation is now tracked in `Modules/RadianceAudit`. Lifecycle
acceptance orchestration and native allocation/timing aggregation moved behind optional observers;
actual P1-03 error propagation and safe shutdown stay in the renderer. Bounded launch/close and ABI
checks are new evidence, not a repeat of the historical G1/G2 world-save cases. See the
[paired ledger](../DEVELOPMENT_LEDGER.md#2026-09-24-tracked-optional-radiance-audit-module-and-native-collector)
for artifact identities, the audit-only Mixin failure/fix/retest and remaining native diagnostic code.
No original review item, GPU root cause or licensing gate is closed solely by this separation.

### 2026-09-24 one-shot replay retirement and candidate hygiene correction

The user retired the standalone performance-investigation tool. The active native capture/replay,
Java controller and build/test targets are removed; derived product optimizations and historical
measurements remain. See the [retirement entry](../DEVELOPMENT_LEDGER.md#2026-09-24-one-shot-scene-replay-retirement-and-amend-preparation)
for scope, the compressed-test-log omission in the previous candidate check, new source snapshot
and actual post-removal checks. This does not reclassify any of the thirteen review findings or
GPU/visual/licensing limits as newly accepted. Amend preparation does not authorize amend itself.

### 2026-09-24 post-retirement manual checkpoint

The user reported no issue in actual play and authorized source amend/synchronization. The
[manual evidence record](../DEVELOPMENT_LEDGER.md#2026-09-24-manual-feedback-and-source-checkpoint-after-replay-retirement)
preserves the exact deployed artifacts, two-world saves/exit and residual log messages. This is
limited observation, not blanket closure of the original findings, GPU uncertainty or licensing.
Product source remains byte-identical to the validated retirement snapshot; only records change.

Paired native source checkpoint for this Radiance amendment: MCVR `b70d2a149fd86f8a03c81dc7b9c8bdcc5f7fae57`.
It retains upstream parent `9905c81b1999f5845bf66d13501d371c16adf561`, the original Initial port
identity/timestamps/message, and a verified new SSH signature. This is a source checkpoint for
retained optimizations, optional diagnostics and replay retirement; artifact/runtime boundaries
remain those recorded above. The Radiance commit does not backfill its own SHA.

### 2026-09-24 glow-lichen symptom clarification

The user corrects the missing-pixel report to a 180-degree disagreement between the visible face
and the PT-participating surface. The [targeted evidence](../DEVELOPMENT_LEDGER.md#2026-09-24-glow-lichen-opposite-face-uv-investigation)
confirms this difference in the upstream horizontal-face UV-lock bake; the sampled primary
footprint and final texel centers are intact. This is not a camera-culling finding and is not yet
a product repair. Diagnostics alone changed, with no reclassification of the thirteen original
findings, GPU uncertainty or other outstanding acceptance.

### 2026-09-24 glow-lichen final disposition

The user accepts the explained opposite-face UV behavior as normal upstream model behavior.
The [recorded decision](../DEVELOPMENT_LEDGER.md#2026-09-24-glow-lichen-accepted-as-normal-upstream-behavior)
closes this symptom without a product fix and withdraws the proposed UV normalization. Earlier
measurements and discussion remain historical evidence; no additional runtime, physical-fidelity
or general material acceptance is implied.
