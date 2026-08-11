# VRAM lifecycle investigation: handoff

迁移日期：2026-10-09；原始观察日期：2026-09-30。本次只迁移结构，不将历史结果提升为本轮验证。

## 问题

保留原调查的范围、证据等级与未完成事项。当前实现与批准的产品约束以[项目现状](../project/status-current.md)为索引。

## 方法

原始记录来自 Radiance 迁移前的已提交源码与对应证据。以下保留原文与日期，避免重新解释历史失败。

## 发现

Observed on: 2026-09-30.
Sources: Radiance `8ad46a0` (V3) and MCVR `e6e8153` (V3), both on `develop`, each with a large
uncommitted worktree described below; candidate artifacts H and I; isolated runs under
`run/world-release-20260930/` (ignored).
Status: investigating. Handoff from a Claude Code session back to the previous agent. Nothing in
this document is committed, and candidate I is not deployed.

## Purpose

This document hands the work of one development thread back to the previous agent. The thread ran
from the Flywheel/VRAM reports after draw-intent slice 2 on 2026-09-29 to the cancellation on
2026-09-30. Everything that already has a ledger entry is linked rather than repeated. Everything
that exists only in the worktree, in ignored run directories or in the chat is summarized here:
the world-leave release, the memory diagnostics, the unresolved user report, the run evidence, the
commit plan and the open decisions. At handoff no process started by the thread is running, and
the user has asked the agent to wait for their reply before doing more work.

## Working agreements in force

These are the user's standing instructions from this thread. The ledger and policy files govern
wherever they are stricter.

- Reply to the user in Simplified Chinese. Repository documents stay English (see
  documentation policy（归档位置：`D:\Workspaces\Artifacts\Radiance\history-archive\Radiance\docs\DOCUMENTATION_POLICY.md`）).
- Do not commit or push without a separate, explicit authorization. Never use a bare `--force`; use
  `--force-with-lease=refs/heads/develop:<sha>`.
- Deletions go to the Windows Recycle Bin. Do not reset the worktree, and do not treat research
  documents as junk.
- Product constraints: no reduction of quality, view distance or animation frequency as an
  optimization, and no camera-based PT pruning. The full scene participates in path tracing. Ponder
  PT stays archived.
- Run automated clients only in isolated `run/` instances with master volume 0. Never enable
  active Audit probes in Prism or production worlds.
- Prism: follow `E:\Minecraft\PrismLauncherDev\AGENT_GUIDE.md` (non-portable). It requires an atomic
  instance lock, a verified stopped game and correlated processes. Do not close the user's launcher
  or their Vulkanite client, do not touch other agents' locks (for example `Vulkanite-Auto.lock`),
  and do not read credentials in `accounts.json`. Launching the Prism instance needs explicit user
  authorization; deployment happens only when the user asks.
- Search only inside the Radiance and MCVR repositories, never recursively from their common parent.
- Before starting CPU/GPU work (client runs, builds, synthetic load), tell the user when it starts
  and how much load it adds. Never build while a measured run is active. Before a run, verify that
  the machine is quiet. `Start-Benchmark.ps1` enforces this, and runs under competing load are void.
- An automated permission check blocked reading one r3 census file on 2026-09-29. Do not pursue
  that file.

## Source state at handoff

| Repository | HEAD | Worktree |
| --- | --- | --- |
| Radiance | `8ad46a0` Performance Optimization Test V3 | 25 modified files (+1,183/-51) plus untracked `MemoryLifecycleProbe.java`, `FlywheelLevelRendererTicksAccessor.java` and `docs/research/2026-09-29-draw-intent-and-persistent-scene.md` |
| MCVR | `e6e8153` Performance Optimization Test V3 | 28 modified files (+943/-267) plus untracked `chunk_geometry_layout.hpp`, `frame_retention.hpp`, `hit_groups.hpp`, `rigid_instances.hpp` and their four tests |

The worktree contains these change groups, in chronological order:

1. Draw-intent slices 1 and 2: shared face capture, batched rigid submission, interned hit groups.
   See slice 1（归档位置：`D:\Workspaces\Artifacts\Radiance\history-archive\Radiance\docs\DEVELOPMENT_LEDGER.md#2026-09-29-draw-intent-slice-1---shared-face-capture-and-batched-rigid-submission`）
   and slice 2（归档位置：`D:\Workspaces\Artifacts\Radiance\history-archive\Radiance\docs\DEVELOPMENT_LEDGER.md#2026-09-29-draw-intent-slice-2---interned-sbt-hit-groups`）.
2. Flywheel clock precision（归档位置：`D:\Workspaces\Artifacts\Radiance\history-archive\Radiance\docs\DEVELOPMENT_LEDGER.md#2026-09-29-flywheel-animation-clock-precision`）.
3. Per-section chunk storage（归档位置：`D:\Workspaces\Artifacts\Radiance\history-archive\Radiance\docs\DEVELOPMENT_LEDGER.md#2026-09-29-chunk-memory-retained-after-moving-batch-shared-chunk-storage`）.
4. Flywheel model release（归档位置：`D:\Workspaces\Artifacts\Radiance\history-archive\Radiance\docs\DEVELOPMENT_LEDGER.md#2026-09-29-flywheel-models-released-when-no-instancer-uses-them`）.
5. Benchmark machine-load check（归档位置：`D:\Workspaces\Artifacts\Radiance\history-archive\Radiance\docs\DEVELOPMENT_LEDGER.md#2026-09-29-benchmark-machine-load-check-and-invalidated-evidence`）.
6. Frame retention by fence（归档位置：`D:\Workspaces\Artifacts\Radiance\history-archive\Radiance\docs\DEVELOPMENT_LEDGER.md#2026-09-29-chunk-snapshots-pinned-by-a-swapchain-image-that-left-rotation`）.
   Candidate H, deployed to Prism（归档位置：`D:\Workspaces\Artifacts\Radiance\history-archive\Radiance\docs\DEVELOPMENT_LEDGER.md#2026-09-29-prism-deployment-of-candidate-h`）,
   contains groups 1 to 6. MCVR's own ledger records groups 2 to 4 and 6 in
   "2026-09-29: Instancing clock, per-section chunk storage, Flywheel model deletion and frame retention".
7. **Not yet in any ledger:** the world-leave scene release and the memory diagnostics described
   below. Groups 1 to 6 plus this group make up candidate I, plus the later Audit probe fix.

## The open user report

After testing candidate H in Prism, the user reported the following before V4 (quoted from the
user):

> 32 视距下显存占用 14G，切换至 16 视距后还是 14G，甚至退出回主菜单后依然是 14G，直到关闭游戏才释放

In English: about 14 GB of VRAM at render distance 32. It stays at 14 GB after switching to render
distance 16, and even after leaving to the title screen. It is released only when the game closes.
The user treats this as a blocker for V4.

## Implemented after candidate H (candidate I, not deployed)

### World-leave scene release

Static finding: before I, nothing released the native scene when the client left its world. The
Java side runs `LevelRenderer.setLevel(null)`, then `ViewArea.releaseAllBuffers()`, then
`ChunkProxy.clear()`, all Java-only. Native `Chunks::reset` runs only from `initNative` when the next
`ViewArea.createSections` happens. Chunk buffers and BLASes, entity and cloud batches, and each
`WorldPrepareContext`'s TLAS, TLAS builder, scene buffers and hit groups therefore stayed alive on
the title screen until the next world or shutdown. This explains the title-screen part of the
report (evidence level: static).

Change, with the GPU idle:

- Radiance: `WorldRendererMixins` injects at the `TAIL` of `LevelRenderer.setLevel`. When the level
  is `null` it calls `ChunkProxy.releaseScene()`, which calls the native `releaseSceneNative` and
  emits Audit counter `CHUNK_BUILD/SCENE_RELEASED`. The `-Dradiance.releaseSceneOnLeave=false`
  switch keeps the old behavior for A/B comparison and as a fallback. The default is `true`.
- MCVR: `World::releaseScene` waits for the render and backend queues to go idle, returning
  silently if either wait fails. It then calls:
  - `Chunks::releaseScene`: finishes and drops the scheduler; clears queued data, build data, slots
    and packed data; resets the external handles to 0.
  - `Entities::releaseScene`: clears rigid models and instances, clouds and entity batches.
  - `Pipeline::releaseWorldScene`: reaches the new `WorldModule::releaseWorldScene`, which
    `RayTracingModule` overrides to call `WorldPrepare::releaseScene`. That clears history and each
    context's TLAS, builder, scene buffers and hit groups.

  JNI is in `com_radiance_client_proxy_world_ChunkProxy.cpp`, and the headers were regenerated.
- Safety, static review only: every Java-to-native chunk entry resolves through
  `externalHandles_`, so late build submissions after the release are dropped. Per-frame
  scheduling checks for a null scheduler. Re-entering a world goes through `initNative` and
  `Chunks::reset`, which recreates the scheduler.
- Flywheel engines need no extra work: Flywheel deletes its engine on level unload. Native
  `Instancing::deleteEngine` closes the child engine, which owns its own models, and erases it.

Not runtime-verified. The only run that reached the leave was m1b, and m1b crashed in the probe
(see below).

### Memory diagnostics

- `VmaResidency` log, active only with `RADIANCE_CHUNK_CENSUS=1` and written to `stdout.log`. Every
  60 presents it logs per VMA heap the blocks (memory held from the driver, including dedicated
  allocations), the live allocations, and the usage and budget, followed by the chunk orphan
  census. It is logged from presentation, so it also covers the title screen. It lives in
  `render_framework.cpp` (`logVmaResidency`). The allocator is created without
  `VK_EXT_memory_budget`, so VMA `usage`/`budget` are VMA estimates, not the process's real WDDM
  budget.
- `MemoryLifecycleProbe` (Audit, new file), active only with `RADIANCE_MEMORY_PROBE=1` in an
  experiment-enabled isolated instance. Properties `radiance.audit.memoryProbe.worldSeconds`
  (default 150), `.reducedDistance` (16), `.reducedSeconds` (90) and `.titleSeconds` (60). Its
  phases are world, then the reduced render distance, then leaving the world the way the pause
  menu does, then the title screen, then a normal stop. It writes `MEMORY_PROBE ...` markers to
  `latest.log`. It is polled from `MinecraftAuditMixin`.
  - Bug found and fixed: the first version (Audit `64E1AF5B...`) called `minecraft.disconnect(...)`
    while still in the reduced phase. `disconnect` runs nested client ticks that poll the probe
    again. It recursed 776 times and the client died with `EXCEPTION_STACK_OVERFLOW`. The top frame
    was in `nvoglv64.dll` only because that is where the stack ran out. The fix switches the phase
    before disconnecting. Rebuilt Audit: `7EDE06C1AFA08D09F48A74AF2092E839FFE0C4B68048712CE92E4927E4978E8D`,
    `:radiance-audit:test` passed. The fix has not run in a client yet.
- `Start-Benchmark.ps1` samples per-process GPU memory every 5 s as Task Manager reports it: WDDM
  counters `GPUProcessMemory` DedicatedUsage, SharedUsage and TotalCommitted, written to
  `gpu-process-memory.json`. The allowlist now includes `RADIANCE_CHUNK_CENSUS` and
  `RADIANCE_MEMORY_PROBE`. The preflight wait can be extended per case with
  `preflightMaxWaitSeconds` (at most 3,600).
- Build and test of I: JNI headers regenerated, MCVR Release INSTALL, CTest 72/72, and Gradle
  `:test :radiance-audit:test :radiance-audit:benchmarkTest :radiance-audit:jar :distributedJar
  :verifyDistributedJar :bootstrapTest` all successful. Logs are in `run/world-release-20260930/build/`.

### Candidate identities

| Candidate | Radiance JAR SHA-256 | Core | Notes |
| --- | --- | --- | --- |
| H | `0B73AD6FE2E17E1895C814B24490CD7A3A11A2DB630B12CB1E655D6317538F10` | `A5EB0C0C...` | Deployed to Prism 2026-09-29; the version the user tested |
| I | `AA77AD985D5D61F56F31FAA19B415E5241CD1168B94F97823303042C4C439FE1` | `5AF3267F4D311135B13B21627D39F224678AD81432FED36F4A98CC19393049B5` | H plus world-leave release and diagnostics; archived in `run/world-release-20260930/artifacts/I/`; not deployed |
| Audit for I | `64E1AF5B4D63B36E0157B366E4D901D08D817B863333AE299FD99EBAD4E26604` | - | Contains the probe re-entrancy bug |
| Audit, probe fix | `7EDE06C1AFA08D09F48A74AF2092E839FFE0C4B68048712CE92E4927E4978E8D` | - | `artifacts/I/audit-probe-fix/`; the only Radiance-repository change after I |

Source diffs as of the I build are in `run/world-release-20260930/source/`. They were taken before
the probe fix.

## Evidence so far

All cases are in `run/world-release-20260930/` (ignored). The series record is `MEMORY_SERIES.json`.
Settings: 2560x1440, render and simulation distance 32, 28 build threads, 32 x 32 batches,
`rigidModels=false`, and native performance reports on.

| Case | Outcome |
| --- | --- |
| `m1-I-release-off` | Not launched: the preflight measured GPU at 31-56% for 600 s (the user was playing video). |
| `m1b-I-release-off` | Flat model-city world. The r32 and r16 phases are valid. It crashed at leave-world (probe bug), so there is no title-screen data. The client exit code was -1073740791 with `hs_err_pid50240.log`; the launcher exit code does not reflect the client's. |
| `t1-I-terrain-release-off` | Cancelled at the user's request 28 s after launch (`CANCELLED.txt`); not a measurement. |

m1b per phase, from `analyze_memory.py`. Process values are WDDM per-process values. VMA device
values are from heap 0.

| Phase | Process dedicated MB | VMA device blocks / live MB | Chunk slots / MB |
| --- | --- | --- | --- |
| r32 idle (end) | 5,390 | 4,689 / 4,562 | 3,453 / 452 |
| r32 after moving 600 blocks | up to 5,798 | 5,201 / 4,984 | 7,950 / 1,080 |
| r16 just after the switch | 5,798 -> 4,916 | 4,303 / 3,797 -> 4,122 | 147 -> 2,226 / 19 -> 333 |

Conclusions, limited to this flat fixture:

- The r16 switch releases chunk memory. Blocks follow live allocations, and VMA fragmentation is
  not visible.
- About 3.8 GB of live device memory does not depend on distance: render targets at 1440p,
  DLSS RR, textures and similar.
- Chunks are at most about 1.1 GB here. The fixture's flat world (128 stone layers, only top faces
  meshed) is far below the scale of a real world. It cannot reproduce 14 GB.

### Prism session facts (read-only)

- Instance `Radiance 1.21.1-neoforge` (non-portable path under `E:\Minecraft\PrismLauncherDev`).
  Mods: Create 6.0.10, Create Aeronautics bundled 1.3.2, Sable 2.0.5, Zume 1.2.2, Radiance (H) and
  Audit in passive mode.
- The user's H session was 2026-09-29 23:43 to 23:48 in world `新的世界`. It stopped normally. The
  logs have no memory data, because passive Audit writes no native reports and MCVR stdout is not in
  `latest.log`.
- Options after that session: `renderDistance:16`, `simulationDistance:32`,
  `entityDistanceScaling:5.0`, with the same Radiance build settings (28/32/32). The upscaler is
  DLSS Quality with RR model 6.
- GPU: RTX 4080 SUPER, 16,376 MiB. With no Minecraft running, other programs (browser video and so
  on) used about 5.2-5.4 GB.

## Hypotheses and open questions

1. **Title screen (likely explained, fix unverified).** H has no native world-unload path, see
   above. I releases the scene. The t1b/t2b runs must show dedicated memory at the title screen
   dropping toward the no-world baseline with release on and staying high with release off.
2. **r16 switch (not reproduced).** The flat fixture shows a drop. Candidates, not yet ranked by
   evidence:
   - WDDM oversubscription. On a 16 GB card with about 5.3 GB used elsewhere, a real r32 world may
     exceed physical VRAM. The driver then demotes allocations to shared memory. After r16 frees
     memory, the demoted allocations come back, so dedicated usage stays near the cap while shared
     usage falls. `gpu-process-memory.json` records both.
   - The user may have read the whole-GPU "dedicated GPU memory" on Task Manager's Performance tab,
     which includes other processes, rather than the Minecraft process's column on the Details tab.
     Ask which one, and whether shared GPU memory changed.
   - VMA block fragmentation with real terrain: long-lived allocations interleaved with chunk
     allocations pin blocks. If the terrain runs show blocks much larger than live allocations
     after r16, isolate chunk geometry and BLAS storage in a dedicated VMA pool. `DeviceLocalBuffer`
     and the AS storage would need an optional pool parameter. A reset then empties whole blocks.
   - Content that is not in the fixture: Create contraptions, Aeronautics/Sable sub-levels,
     `entityDistanceScaling` 5.0. Entities do not depend on render distance.
3. Optional diagnostic: enable `VK_EXT_memory_budget` and
   `VMA_ALLOCATOR_CREATE_EXT_MEMORY_BUDGET_BIT` so that `VmaResidency` shows the real per-process
   budget and usage. This needs the device extension enabled at device creation.

## Next steps (in order)

1. Wait for the user's reply. They asked the agent to cancel everything and wait.
2. Terrain reproduction. The runner is ready: `run/world-release-20260930/run_memory.py`, cases
   `t1b-I-terrain-release-off` then `t2b-I-terrain-release-on`.
   - World: the case's own overworld switched to the noise generator (seed 250925, features on).
     Phases: idle r32 240 s, r16 120 s, title screen 60 s.
   - The runner waits up to 1 h per case for a quiet machine. It stops at the first failure,
     including a client crash or `hs_err`.
   - Load: about 10 min of full CPU (world generation on 28 threads) and full GPU per case. Tell the
     user first.
   - The runner is pinned to Audit `7EDE06C1...`, which prepare copies from
     `Modules/RadianceAudit/build/libs/`. That build must still be this hash when the runner starts.

   Launch it detached so tool timeouts do not kill it, for example:
   `Start-Process python -ArgumentList '-X','utf8','run_memory.py' -RedirectStandardOutput runner.out ...`.
   Analyze with `python -X utf8 analyze_memory.py t1b-I-terrain-release-off t2b-I-terrain-release-on`.
3. From the result, either confirm the title-screen fix and explain r16 (for example WDDM residency)
   or implement the chunk VMA pool. Re-measure after any fix.
4. Records. Append Radiance and MCVR ledger entries for the world-leave release, `VmaResidency`, the
   memory probe and its re-entrancy fix, and per-process GPU memory sampling. Append a correction
   that links the H deployment entry and ROADMAP gate 1: the user reported that VRAM did not return
   at r16 or on the title screen. Update the ROADMAP item "Flywheel motion and chunk memory
   acceptance" accordingly. Decide with the user whether the diagnostics stay. They are opt-in and
   environment-gated; keeping them is recommended.
5. Deploy a fixed candidate to Prism only on the user's request. Use
   `run/candidate-h-deploy-20260929/tools/PrismDeploy.ps1` (`-OwnerPid`, `-TaskId`) under the Prism
   guide.
6. Commit only on authorization. See the next section.

## Commit preparation (draft, awaiting decisions)

- Before any commit, run the full gates on the final tree after the user's Prism test:
  - Radiance `gradlew.bat :test :radiance-audit:test :radiance-audit:benchmarkTest
    :radiance-audit:jar :distributedJar :verifyDistributedJar :bootstrapTest`;
  - MCVR `cmake --build <MCVR>/build-radiance-1.21.1-neoforge --config Release --target INSTALL
    --parallel 4`, then `ctest --test-dir <same> -C Release --output-on-failure`;
  - the benchmark fixture tests.
- Include every untracked file listed above.
- Entangled files mean the work cannot be split by change group without hunk-level patches:
  - Radiance: `RadianceFlywheelEngine.java`, `Start-Benchmark.ps1`, `compare.py`,
    `test_fixture.py`, the Audit README and the ledger;
  - MCVR: `world_prepare.cpp`, `instancing.cpp`, `entities.cpp`, `chunks.cpp`,
    `render_framework.*`, `tests/CMakeLists.txt` and the ledger.
- **Option 1 (recommended earlier).** One additive signed commit `Performance Optimization Test V4`
  per repository on top of V3, with the configured identity, the real commit time and an English
  Markdown-bullet body. No rewrite, no push. The draft bullets were:
  - Radiance: face-capture reuse and batched rigid submission; Flywheel double-precision clock;
    Flywheel instancer/model release with fallback switch; benchmark activation guard and quiet
    machine gate; records.
  - MCVR: native rigid batching and interned hit groups; double-precision rotation/scroll; per-section
    chunk buffers and BLAS storage; fence-based frame retention; Flywheel model deletion, opt-in
    residency census and regression tests.
  - Both need bullets for the world-leave release and the memory diagnostics.
- **Option 2.** Fold the maintenance fixes into `Initial port` and relink V1 to V3 as in the
  2026-09-28 split, then add V4. This is a history rewrite: backups, conflict resolution in
  `chunks.cpp` and `world_prepare.cpp`, and a later `--force-with-lease` push authorization.
- Open user decisions:
  - commit structure;
  - keep or strip the diagnostics;
  - the Flywheel update-limiter question in ROADMAP gate 2.

## Operational notes

- MCVR logs go to the case's `stdout.log` as `[HH:MM:SS]`. Game logs use a locale timestamp such as
  `[309月2026 13:53:31.220]`. `analyze_memory.py` handles both, and aligns WDDM samples through
  `start.json` (UTC).
- `Start-Benchmark.ps1` refuses a case that already has `environment.json`. A refused preflight
  therefore consumes the case name, so choose a new name. Its exit code is 0 even when the client
  crashes, so check `result.json.exitCode` and `hs_err_pid*.log`.
- If JNI signatures change, regenerate the headers with `gradlew.bat :generateJniHeaders` before
  the MCVR build. Stop the Gradle daemon (`gradlew.bat --stop`) before timing runs.
- Earlier evidence directories (all ignored):
  - `run/chunk-residency-fix-20260929/`: e1-e3, p1-p9, D/E timing;
  - `run/flywheel-model-release-20260929/`: f1, f2;
  - `run/chunk-residency-repro-20260929/`: r1-r5, q1, q2, l1, l2, census scripts, G-H artifacts;
  - `run/load-check-smoke-20260929/`;
  - `run/candidate-h-deploy-20260929/`.

  Frame times from runs without the load check are void. See the
  load-check entry（归档位置：`D:\Workspaces\Artifacts\Radiance\history-archive\Radiance\docs\DEVELOPMENT_LEDGER.md#2026-09-29-benchmark-machine-load-check-and-invalidated-evidence`）.

## Evidence boundary

- World-leave release: static review, build and automated tests only; no runtime evidence.
- r16 behavior: runtime evidence from one flat-world run (m1b). The user's Prism observation is
  unreproduced and its reading (process versus whole GPU, dedicated versus shared) is unconfirmed.
- No user visual or runtime acceptance of I exists, and I is not deployed.

## Follow-up on 2026-09-30

This handoff remains the historical source state at transfer. I was subsequently deployed on the
user's request, with corrected passive Audit. User feedback accepts bounded chunk memory release,
but reports a higher title-screen baseline after world use. The scene-descriptor and DLSS viewport
owners were investigated and corrected as J; attribution remains pending. See the appended
lifecycle records（归档位置：`D:\Workspaces\Artifacts\Radiance\history-archive\Radiance\docs\DEVELOPMENT_LEDGER.md#2026-09-30-candidate-i-handoff-deployment-and-user-memory-lifecycle-feedback`）.
The old instruction to wait and the statement that I was not deployed no longer describe current
work; the refused/crashed/cancelled cases retain their original evidence boundaries.

## 结论

本次迁移不实施研究中的提案，也不重新判定视觉、性能或设备丢失根因。旧台账与闭合审计保留在仓库外历史存档。
