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
