# Medium-scene CPU hotspot investigation

Observed on: 2026-09-24.
Status: investigating; source findings confirmed within the paths below; optimizations proposed.
Evidence: static inspection and deeper analysis of an existing runtime recording. No new game,
build, product modification or optimization benchmark was performed for this investigation.
Sources: Radiance `b8568cafd222c8169cd6c8fd3d5771690e3425ca` and MCVR
`4778983778d53084132ce84ed0e567579ed6ed7b`, with the existing uncommitted replay work retained;
Minecraft 1.21.1 / NeoForge 21.1.251 local source artifact.

## Question and evidence identity

Investigate the three CPU paths highlighted by the
[Medium comparison](../DEVELOPMENT_LEDGER.md#2026-09-24-medium-scene-sequential-game-and-native-comparison):
block-entity discovery, auxiliary texture work, and entity geometry construction/allocation.
The previous game/native comparison observed 44.50/13.35 ms mean real-frame intervals and
13.64/13.31 ms GPU intervals. That difference is not exclusive Java execution time or an
achievable optimization target: the replay freezes geometry, textures, simulation and GUI.

Non-portable evidence root: `D:\Workspaces\Artifacts\MCVRSceneReplay\20260924`.

| Evidence | Identity and boundary |
| --- | --- |
| Existing JFR | `iteration-6/selection-ready/cpu-profile.jfr`; PID55460, 40 seconds, 05:07:56-05:08:36 local; SHA-256 `15633EFF76F471A78FD1953FBAD234017214D54F6AAD4756764356A833D159D3` |
| Deeper export | `hotspot-investigation/cpu-profile-deep.txt`, stack depth 64; `sample-summary.json` preserves event/leaf counts; `deep-stack-summary.json` preserves grouped stacks |
| Inspected sources | `hotspot-investigation/inspected-source-manifest.json`; SHA-256 `48DF929BAFE1C84ED851EAAFF541538920F2F73C10F9BF3049AFB39958161A06`; includes source hashes, HEADs, dirty-file inventory and local Minecraft source-JAR hash |
| Exact frame/allocation measurements | Separate PID36400 in `iteration-7`; `comparison-metrics.json` and `allocation-summary.json`; not the JFR process or interval |

The inspected-source manifest hashes UTF-8 file bytes after CRLF/CR-to-LF normalization, with
no other transformation. It is a bounded investigation manifest, not a new complete build snapshot.
Artifact and runtime identities remain in the linked comparison record.

Correction to the earlier shallow interpretation: five frames was the JFR export default, not
the recorded stack limit. Re-exporting the same recording exposed the real producers without
another client run. Render-thread samples contain 263 ExecutionSample and 228 NativeMethodSample
events. The recording also contains seven ChatScreen stacks, so its sample composition must not
be treated as the exact ordinary-gameplay interval measured in iteration-7. Counts are statistical
observations, not exclusive milliseconds; inclusive stacks and native samples must not be added
as percentages of a frame.

## 1. Block-entity discovery scans the complete section array every frame

`WorldRendererMixins` calls `EntityProxy.queueBlockEntitiesRebuild` each world frame. The method
iterates every `ViewArea.sections` entry and reads its compiled block-entity list before dispatching
actual renderers. The local `ViewArea.setViewDistance/createSections` implementation allocates
`(2 * distance + 1)^2 * level.getSectionsCount()` entries. For a 24-section-high world this is
26,136 entries at distance 16 and 101,400 at distance 32, including sections without block entities.
These are array entries, not the benchmark's 8,207 ready native chunk meshes.

Of 40 render-thread samples under this method, 32 stop at its list-access loop and seven in
`ChunkProxy`'s compiled-section list accessor; one reaches the block-entity renderer lookup.
This supports investigation of discovery overhead, not a conclusion that block-entity rendering
itself consumes 40 samples of exclusive time or should be disabled.

Proposed change: keep an index of sections with renderable block entities, updated when the
validated compiled-section publication changes that list. `ChunkProxy` publishes both its
empty-mesh and nonempty-mesh `CompiledSection` variants after owner/revision validation; both
can contain block entities. Index removal must follow origin reuse, reset, unload, F3+A and
world switch. Preserve global block entities, external/Sable ownership and Flywheel routing.
Continue rendering indexed animated objects each frame; chunk dirtiness is not an animation key.

This is presence indexing, not main-camera visibility culling. Reflection, shadow and indirect
paths retain all relevant geometry. Required tests include empty mesh with a block entity,
stale compiled publication, origin reuse, global entities and removal/reinsertion during reload.
Measure scanned sections, active sections, actual dispatches and elapsed discovery time separately.

## 2. Animation uploads regenerate and copy complete auxiliary source images

All 100 render-thread samples containing `AuxiliaryTextures.loadAndUpload` originate in:

```text
Minecraft.tick -> TextureManager/TextureAtlas tick -> SpriteContents.Ticker.tickAndUpload
-> AnimatedTexture.uploadFrame -> SpriteContents.upload -> NativeImage.upload
-> TextureTasks owned task -> NativeImageMixins -> AuxiliaryTextures.loadAndUpload
```

There are 74 Java samples (68 in the `mappedCopy` read/write loop, six elsewhere in the helper)
and 26 nested native samples (nine upload, twelve filter, five clamp). Thus the measured producer
is animated atlas upload, not merely a speculative lightmap or GUI path.

`SpriteContents` passes a complete mip-level animation image plus the selected frame rectangle.
For each auxiliary type, the current helper either copies the entire prepared PBR image or, when
absent, calls `source.mappedCopy` to create a complete constant default image. The latter still
reads every original pixel although the mapping ignores it. Only a frame-sized rectangle is
ultimately selected for upload. Resource caching avoids decoding again, but not these copies.

The native boundary repeats the excess: `NativeImageMixins` passes the complete image allocation
size; `Textures::queueUpload` calls `ImageBufferCache::append` with that size; `append` performs
the full host `memcpy`. The subsequent `VkBufferImageCopy` selects the requested rectangle.
Consequently source-image bytes, host staging bytes and actual GPU copy bytes are different.
The current trace does not record animation-strip dimensions, so no measured waste multiplier
or PCIe-throughput estimate is claimed.

Proposed first optimization: prepare/copy only the requested rectangle, pack rows for native
staging, and keep per-generation auxiliary defaults without repeatedly deriving them from main
texture pixels. Preserve source pitch, offsets, mips, format, owner locking, generation checks,
emission tile updates, PBR animation/interpolation and retirement. Missing-PBR initialization
fixed real material corruption earlier; it must remain correct after atlas allocation, reuse and
reload. A byte `memset` is not a general replacement for a multibyte normal default such as
`0xFF000000`. Existing constant-region helpers need format-aware handling before reuse.

Tests should compare actual pixel/region results for nonzero source/destination offsets,
non-square animation strips, mips, missing/present PBR, interpolation, F3+T and texture ID reuse.
Counter gates: unchanged output, no whole-strip default conversion, and host staging growth
proportional to uploaded rectangles rather than source-image size. Run the same saved scene
before claiming frame-time improvement.

Related source observation, not attributed to this benchmark: regular `Textures::setSamplingMode`
compares only the min/mag filter before replacing the sampler, while its frame-alias branch also
compares mipmap mode. A same-filter/different-mipmap request can retain the old mipmap mode on
the regular path. Preserve this as a direct sampler-contract follow-up with a behavior test;
this investigation did not change it or establish a visible symptom. Repeated filter/clamp JNI
calls alone do not prove repeated sampler allocation or descriptor-table replacement.

## 3. Entity batching recreates GPU storage and rebuilds uncached geometry

The Java path creates layer buffers, finalizes meshes and marshals metadata in
`EntityProxy.queueBuildInternal`; native `Entities::queueBuild` converts/copies vertex/index data.
`EntityBuildDataBatch::build` then creates three aggregate position/material/index buffers,
packs and uploads their contents, and defines BLAS builds for entries without a prebuilt handle.
`BLASBatchBuilder::allocateBuffers` adds shared AS storage and scratch buffers. `resetFrame`
retains the prior resources for in-flight work and starts new batches.

The existing steady log averages 114.6 `buffer@Build entity geometry` allocations/s and
262.8 MB/s of allocation bytes. Five aggregate buffers per ordinary batch are consistent with
roughly 23 batches/s; this is not 115 entities, 115 BLAS builds, transfer bandwidth or a leak.
The native batch uses actual vertex/index counts, not the larger Java builder capacity.
The JFR observes 27 native build samples and 16 native queue leaves, but cannot split native
packing, allocation, Vulkan driver and GPU-AS costs into reliable exclusive timings.

A concrete avoidable case exists in `CloudProxy`: it caches Java mesh data until its geometry/
appearance key changes, then reuses that mesh while updating its origin. Nevertheless each frame
calls `queueBuildWithoutClose`. `processWorldEntityRenderData` supplies `prebuiltBLAS = -1`, so
the same cloud mesh still enters native conversion, upload and BLAS rebuilding. One native queue
sample came from this path; fifteen came from regular entity queuing. That proves reachability,
not that clouds dominate the allocation bytes. The entity tag also covers non-mob geometry.

Proposed staged optimization:

1. Instrument batch vertex/index bytes, AS bytes/builds, producer identity and CPU packing/upload
   preparation time. Keep allocation rate separate from transfer and completed GPU build time.
2. Reuse frame-slot buffer capacity after its GPU retirement, and write aggregate packed arrays
   directly where behavior tests prove intermediate copies unnecessary. Do not overwrite in-flight
   buffers, change device addresses without updating consumers, or replace retirement with idle.
3. Give explicitly reusable geometry such as the cloud mesh a native lifetime and update only
   transforms when its geometry/material key is unchanged. Include world/texture reload, cloud
   mode/color/shape changes and cache eviction. Do not infer static entity geometry from a stable
   identity/hash or freeze animated entities, particles or block entities.

Existing Flywheel model sharing and UI cached-entity paths are not evidence that ordinary entity
queuing already caches BLAS. They have separate ownership/contracts and must be preserved.
Tests need multiple in-flight frames, material-only/transform-only versus geometry changes,
cache invalidation, address/content correctness and bounded retirement.

## Proposed priority and remaining evidence

Investigate/implement auxiliary rectangle work first, then block-entity presence indexing; both
have a directly identified repeated operation. Treat entity persistence as a larger change:
separate its costs first, with clouds as a concrete cache-boundary candidate. This ordering is
based on evidence and implementation risk, not predicted FPS. No change is authorized merely
by recording this proposal.

Use the saved Medium scene, unchanged quality/render distance and real-frame timing, plus a
dynamic scene covering animation, block entities and reload. Report p50/p95/p99 and allocation/
transfer/build counters for each change separately. Do not use visibility culling, frozen animation,
FG presentation FPS or the frozen replay's 3.33x difference as an optimization result.

Unmeasured: per-hotspot milliseconds, actual auxiliary region/source byte ratio, per-producer
entity bytes/BLAS cost and independent FPS gains. No new automated/GPU/game test or user visual
acceptance is claimed. Existing replay changes, GPU-fault limits and public-binary license gates
remain unchanged; no Prism/production data was accessed.

## 2026-09-24: Authorized implementation follow-up

The preceding sections retain their investigation-time scope. After the user authorized an
optimization attempt, the first region/index/cloud stage was implemented and exercised. Its
[canonical ledger record](../DEVELOPMENT_LEDGER.md#2026-09-24-region-uploads-block-entity-index-and-submitted-cloud-cache)
contains the exact source/artifact identity, tests, matched game comparison, counter evidence
and Prism deployment. This supersedes the no-implementation/no-new-test boundary for that later
stage, without rewriting the original JFR findings or attributing exclusive milliseconds to them.
Ordinary entity frame-slot buffer pooling, a reusable default-image allocation cache, the sampler
follow-up, per-change performance attribution and user visual acceptance are not completed by it.

## 2026-09-25: Producer attribution and bounded rigid baked-model prototype

Status: implemented, build-verified and runtime-observed for the bounded cases; broader visual
acceptance remains open. This follow-up supersedes the earlier unmeasured-producer boundary only
for the recorded city fixture. It does not revive the retired standalone replay tool.

The user-selected scope is producer attribution followed by one persistent-model experiment.
Stable GPU scene records/render origin, new chunk scheduling, arenas, PTLAS and SER are deferred;
no visibility/quality reduction or animation throttling is authorized by this prototype.
Canonical implementation, exact final identities and same-condition results are in the
[paired implementation ledger](../DEVELOPMENT_LEDGER.md#2026-09-25-persistent-rigid-baked-model-prototype-and-producer-attribution).
Non-portable raw evidence: Radiance `run/model-persistence-20260925/`.

### Measured choice, rather than a renderer-name whitelist

The first census (PID 60276) measured approximately 150,180 PBR vertices per frame. Rounded parent
producer results follow; renderer/face timings occur in different stages and must not be added to
nested child-model timings as independent frame costs.

| Actual producer | Vertices/frame | Renderer inclusive ms/frame | Face capture ms/frame |
| --- | ---: | ---: | ---: |
| ItemFrameRenderer | 56,220 | 2.714 | 1.825 |
| ArmorStandRenderer | 38,800 | 1.449 | 1.094 |
| ChestRenderer | 16,488 | 0.854 | 0.878 |
| BedRenderer | 16,272 | 0.900 | 0.831 |
| PaintingRenderer | 12,144 | 0.559 | 0.725 |
| BoatRenderer | 7,200 | 0.273 | 0.231 |
| MinecartRenderer | 2,184 | 0.173 | separate CSV |
| Other named producers plus unassigned | remainder | separate CSV | separate CSV |

The second census (PID 3420, 618 frames) traced item-frame work into actual baked-model entries:
240 block-model calls/frame, 21,120 vertices, 0.806 ms and two local model identities; 235 item-model
calls, 34,400 vertices, 1.501 ms and 35 identities. Armor-stand item layers add about 3,496 vertices.
The selected capability is therefore a rigid baked-model bulk-quad path, not a hard-coded item-frame
or block-name optimization. Ordinary ModelPart animation is deliberately not rewritten.
Unassigned dynamic input is about 72 vertices/frame. Child model bytes overlap parent producer bytes.
`blas_candidates` counts submitted CPU candidates, not completed GPU builds or per-producer GPU time.

The detailed census measured 1,516.6 face captures/frame and 5.979 ms total: setup 1.210, clear 0.580,
backup 0.098, restore 2.483, remainder 1.608 ms. The remainder includes actual matrix/texture/FBO/state
snapshots, bookkeeping and instrumentation; it is not an allocation-time measurement. This is real
RenderType setup/clear/restore work, not primarily name parsing. Cached material rules cannot replace
the actual callbacks, state source or sampling time. The prototype still captures every submitted
layer's actual face state; it does not claim to eliminate the remaining roughly 5.5 ms.

The first two census CSVs' renderer-self field only excluded nested renderer scopes, not every model
scope. Preserve them as inclusive-like historical evidence. The later Timer stack subtracts actual
nested model/renderer scopes; its controlled-clock exception test establishes that distinction.
Heavy census and dual-path geometry checking are off for the performance comparisons.

The corrected baseline census, PID 79208 (`prototype-5`, path disabled, 623 frames), records
ItemFrame inclusive/self 2.767/0.469 ms, ArmorStand 1.445/0.402, Chest 0.863/0.308, Bed 0.888/0.338,
Painting 0.557/0.557 and Boat 0.272/0.069. Parent self excludes measured child model/renderer scopes,
not all uninstrumented subcalls. Its face split is 5.835 ms total: setup 1.194, clear 0.586, backup
0.096 and restore 2.458; the residual is not a separately measured allocator cost. The source
recipes remain stable enough to remove 59,016 transformed vertices/frame in the measured prototype.
Per-producer GPU build durations are still unavailable: native BLAS/TLAS timestamps and actual
persistent build counters are global, and delayed rigid material capture is explicitly unassigned
instead of being misattributed to the next ordinary producer.

### Implementation and costs that remain

`RigidModelCapture` intercepts the actual block/item baked-model entry only for the concrete vanilla
`SimpleBakedModel` data contract (not subclasses/custom callbacks), with an exact eligible PBR consumer in ordinary world entity capture. Renderer, model, tint, lighting and
quad callbacks still execute each frame. The recorder owns local quad inputs and changing appearance;
on a cache hit it avoids transformed 128-byte vertex expansion. This is not caching the last frame's
transformed mesh, nor generating it in full before hashing. Copying/comparing the 32-int local quad
recipes and callback execution still cost CPU time and allocation; neither is claimed to disappear.

One local model resource and BLAS serves rigid instances with current double world origin, matrix,
visibility and material state. Identity includes local recipe/appearance, texture generation and
RenderType; native variants include actual encoded face/type flags. Histories also require matching
model identity and draw layout/count. Visibility-induced ordinal shifts discard history rather than
borrowing another same-model part's previous transform. Geometry changes rebuild; no unrestricted
AS update/refit is introduced. Only a successfully submitted build can release build inputs, through
normal frame retirement. An abandoned recording must be recorded again.

The Java cache is bounded to 512 entries/32 MiB local vertex storage and the native variant cache to
1,024 entries. Inactive entries age out after 120 frames. Current-frame and GPU references retain
ownership. Reload/world identity changes invalidate; Java direct memory is released on the next
entity-frame boundary, not promised to disappear immediately while sitting in a menu. Unsupported
consumers, custom bulk behavior, nonuniform/skew/singular poses, excess per-entity draw count and
special raster/camera-relative paths retain the original execution. Glint/outline wrappers therefore
remain semantically active instead of forcing a second full old expansion inside the fast path.

Moving scale into the instance exposed a direct input-contract issue: ray-cone width is world-space
but existing LOD derivatives were local-space. The shared hit/any-hit helper now derives from
object-to-world transformed triangle edges. A real compute test covers scale/mirror/rotation and
degenerate UV cases. This does not establish complete PT image equivalence. Both A and B use the
same corrected shaders so their performance difference does not conflate this correction.

Every persistent draw currently uses a JNI submission and a lightweight TLAS instance; there is no
per-cube split. More instances and recipe comparisons are explicit tradeoffs. Evaluate real frame
intervals, upload bytes, dynamic BLAS work, TLAS count/time and process memory together. A lower
vertex count alone is insufficient evidence to expand the prototype.

## 2026-09-25 ModelPart granularity experiment

Status: bounded implementation and measurement, **not a measured expansion win**. The second
prototype and fixed evidence are in the
[ledger](../DEVELOPMENT_LEDGER.md#2026-09-25-bounded-modelpart-persistence-experiment).
It selects exact vanilla ModelPart/Cube + eligible ordinary-entity PBR ownership, preserving original
traversal/poses and all unsupported consumer paths. Each part is a mesh (not each Cube by definition),
but the chosen producers contain many one-cube parts: 1,788 extra rigid instances remove only about
44,352 dynamic vertices/frame. The first baked-model prototype's larger recipes do not establish
that these small parts should also become separate TLAS instances.

The eight-process A/B/B/A static/route comparison has no net benefit: 44.47 to 44.77 ms static and
46.94 to 47.58 ms route. Lower upload and BLAS work is offset by rigid submission and entity metadata;
the existing approximately 5.5-6 ms material state chain also remains. The correct direction is still
avoiding unnecessary production, but unit size and scene/submission overhead must be measured.
There is no authorization or evidence here for arbitrarily freezing animation, grouping unrelated
materials, dropping outlines or removing off-camera geometry. Do not expand wrappers/ModelPart
coverage simply to improve a coverage percentage.

This addendum also corrects the previous menu-retention limitation for the final candidate: Java
recipes now clear at disconnect/normal close; native in-flight resources retain their independent
ownership. Detailed delayed-draw producer mapping is now available, while per-producer GPU time
still is not. The final lifecycle/bounds corrections have separate artifact identity from the timed
prototype. Full GPU temporal/image parity, external model-Mixin semantics, live dimension churn and
long stability remain unproven. Keep this second experiment default off; larger-granularity or batched
instance expression requires a later bounded design and its own A/B, not silent rollout.


The new `evidence/PRODUCERS.json` retains the actual delayed-draw attribution and unassigned remainder.
A fresh parts-off census (`probe-baseline-final`, candidate-3) versus the earlier diagnostic candidate
(`probe-1`, candidate-1) identifies coverage, **not a controlled timing comparison**:

| Producer | Dynamic vertices/frame, parts off | Parts on | Persistent draws, off -> on |
| --- | ---: | ---: | ---: |
| ArmorStand | 35,304 | 0 | 20 -> 1,491 |
| Boat | 7,200 | 0 | 0 -> 240 |
| Chest (wrapped fallback) | 16,488 | 16,488 | 0 -> 0 |
| Bed (wrapped fallback) | 16,272 | 16,272 | 0 -> 0 |
| Minecart | 2,160 | 720 | 1 -> 61 |

Unassigned dynamic input remains explicit (about 72 versus 69 vertices/frame); per-producer native
GPU attribution is unavailable. Material faces are still measured as the actual capture scope,
not a full allocation/lock/state-transition subprofile; the approximately 5.6 ms cannot be declared
all removable. This round does not claim to have solved that separate bottleneck. Final lazy provider
state and close/bounds guards have their own candidate-4 tests/runtime, not a rerun of the timed A/B.
