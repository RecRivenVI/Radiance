# Chunk hole and native crash experiment

## Evidence from the 2026-09-21 04:00 crash

- A player-local section update appeared at about 04:00:03 and produced one priority compile/native submission.
- At about 04:00:05, chunk-load processing jumped by 193 columns, 4,850 dequeues, 5,330 publications, and 2,208 neighbor refreshes. Native ready sections dropped from 10 to 9.
- Windows logged `nvlddmkm` event 153 for `GPUID: 100` at 04:00:05 and 04:00:07.
- `javaw.exe` then failed fast in bundled LWJGL `OpenAL.dll` at 04:00:08 (`0xc0000409`, parameter 7). No JVM `hs_err` or Minecraft crash report was produced.
- This does not prove OpenAL is the initiating fault. The driver errors and chunk rebuild burst precede the terminal OpenAL fail-fast.

## Build under test

- Radiance: `6E3122F61E57864CBF667E7607C1996780A2941881F7C6D717F8291F90EF12DE`
- Radiance Audit: `6185F38A7B3EE6B164CA26A75E95C4DEBFFA71E86580D878D076D15D767CFEA9`
- Root `gradlew build`: PASS.
- Audit mod `gradlew -p dev/radiance-audit build`: PASS.

## Controlled change

`ChunkProxy.invalidateCompiledColumn` still refreshes compiled nearby geometry when a real neighbor arrives, but skips sections previously confirmed by the occupancy fast path to contain only air and no NeoForge additional section geometry. It records `NEIGHBOR_EMPTY_SKIPPED` separately.

The external audit mod now records:

- the coordinates of every `ChunkProxy.onChunkLoaded` notification;
- player-caused `RenderSection.setDirty` calls;
- any non-air section which remains missing from the native chunk scene for at least two seconds;
- exact dimension, section origin, native slot, dirty state, and compiled state for persistent hole candidates.

## Crash capture

Per-process WER LocalDumps is enabled for `javaw.exe` with full dumps in `D:\Workspaces\Artifacts\RadianceCrashInvestigation\20260921-chunk-hole\dumps`. The previous registry state is saved in `localdumps-before.txt` and the active configuration in `localdumps-active.txt`.

## Reproduction 2 — 04:11

- The same crash repeated: NVIDIA `nvlddmkm` event 153 at 04:11:45.125 and 04:11:46.926, followed by bundled LWJGL `OpenAL.dll+0xA2B05` fail-fast at 04:11:49.
- The native crash signature is identical to the 04:00 run. Changing OpenAL Soft versions did not change this bundled-library signature.
- Neighbor refresh caused `native_ready` to fall from 10 to 9. In the same interval, 2,304 additional entries were published as empty while `compiled` and `native_submitted` did not increase.
- This identified a correctness bug: `RenderRegionCache.createRegion(...) == null` was treated as proof that the section was empty. It can instead mean surrounding chunk data is temporarily unavailable.
- Twenty-seven player-dirty notifications began about 12 ms before the first NVIDIA driver event, matching Minecraft's 3x3x3 neighbor notification pattern around a local block update.
- WER LocalDumps did not preserve a dump; WER archived only `Report.wer`.

## Reproduction 3 build

- Radiance: `10CF0EBB2FB163A473022ECF50AD139F06A854A58C979012C728567BD9FE8F35`
- Radiance Audit: `193BBB271864BC6F387FE4F7306CD3774EF14F531D1860FF4A40457EE797581B`
- A null render region now preserves the previous compiled/native geometry and leaves the section dirty for a later chunk-load retry.
- Every Java-to-MCVR chunk native submission now creates an intent before entering JNI and closes it only after JNI returns. A native crash during submission will therefore leave an exact nonterminal record with slot, generation, origin, geometry count, and priority.
- Hole sampling was moved to the verified Minecraft frame-tail path through a LevelRenderer accessor.

## Reproduction 4 A/B build

- Radiance: `80E843FB50D194524C1B5BD45CC4E6601595F8551C16DE2F76CC0940F08EBDC3`
- The crashing update was precisely 
ativeId=0, generation 1, origin (0,-64,0), one geometry group. JNI returned after about 1.18 ms; the first NVIDIA event followed about 5 ms later.
- Player-priority CPU compilation remains enabled. Replacements of an already-published native BLAS now use the fenced normal native batch path; only first publication may use the immediate in-frame path.
- Relocating a ViewArea/native slot now clears the occupancy result inherited from the slot's previous world coordinate.

## Reproduction 5 unified scheduler build

- Radiance: `BD65C37DDA642D3543ADC44118E5A53D68D35698FD71E4F8A19C141415B37171`
- Reproduction 4 crashed during the initial cluster. The cluster contained immediate first publications and a fenced replacement of the same native slot, so replacement-only routing did not isolate the two GPU timelines.
- Every primary chunk generation now uses the fenced native batch scheduler. External geometry keeps its prior immediate behavior.
- Vanilla dirty notifications now request a column occupancy scan instead of directly entering the section compiler. This prevents unloaded and known-empty ViewArea slots from flooding RenderRegionCache and the native scheduler.

## Reproduction 5: unified primary-chunk fenced submission

Recorded: 2026-09-21 04:30:29 +08:00

- Deployed Radiance SHA-256: BD65C37DDA642D3543ADC44118E5A53D68D35698FD71E4F8A19C141415B37171.
- User acceptance in the same run: the visible chunk hole disappeared, placing a block did not crash, and entering the Nether did not crash.
- Observed Java process: PID 46660; it remained alive after those actions.
- No new 
vlddmkm device-error event or javaw.exe Windows Error Reporting event was observed after this run started.
- The audit ledger contained no unclosed CHUNK_NATIVE_SUBMIT intent.
- Primary/main-world chunk submissions all used 
ativeImmediate=false; initial publication and replacement generations therefore shared the fenced native batch scheduler. External geometry retained its prior immediate route.
- Dimension transitions reset the storage grid as expected (17x24x17 / 6936 slots and 17x16x17 / 4624 slots). The run sustained thousands of completed native builds and more than 2,000 ready native sections without a device error.
- After a storage reset, occupancy filtering admitted only confirmed non-empty sections to compilation (initial samples grew from 5 to 32 enqueued), while tens of thousands of empty-section observations were handled by mpty_fast_path instead of native geometry submission.

### Current interpretation

The prior failure required two interacting classes of defects. Java-side section bookkeeping could publish false emptiness or carry stale empty state across a reused ViewArea slot, producing visible holes and excessive rebuild traffic. Separately, primary chunks could be published through both an immediate in-frame path and a fenced background-batch path, allowing consecutive generations of one native slot to overlap in asynchronous GPU work. The JNI call had already returned when the NVIDIA device error occurred, so the later OpenAL fail-fast is treated as a secondary crash manifestation rather than the initiator.

The current candidate fix clears relocated occupancy state, keeps prior geometry when a render region is temporarily unavailable, validates dirty sections through the column occupancy scan, skips known-empty neighbor rebuilds, and routes every primary chunk generation through one fenced native scheduler. This run provides direct runtime evidence for the candidate fix, but does not yet prove long-session or all-mod-combination safety. Repeated generations remain a separate performance-optimization target.
