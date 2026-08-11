# Template and configuration integration

## Scope and frozen inputs

Engineering implementation started 2026-09-10. This supersedes the implementation order proposed in `documents/project/status-port-readiness.md`: template/engineering work comes first. Product rendering, installation, DLSS/texture/HiDPI defects and visual acceptance remain deferred.

The Radiance repository is `RecRivenVI/Radiance`, branch `stonecutter`, HEAD `9fe7811cf8b27f97b8b77307e982e3cc3942aee5` plus its existing dirty worktree. The initial fifteen staged deletions and all effective unstaged/untracked migration content were first preserved in signed checkpoint `24dede0893337e8fd676939bd98e25d06ed8f34b`, then the engineering changes were committed separately. No remote tree overwrote local work.

Recoverable source backup and Git index/patches: `D:/Workspaces/Backups/Radiance_Template_20260910-210902/`. `baseline.json` SHA-256 `ef6b4d6ea3567ba4e9a719046d2b73797f0648cebc8eefe6a584700614dfb38b`; 10,017 source/config/document files copied with hash verification. Another 1,398 runtime/cache files are sealed and will not be modified; their hash list is not claimed as a backup. No runtime move or cleanup is planned.

Template authority is the actual uncommitted `main` working tree of Ravens-mod-template, frozen into the backup's `Template/`; it has no commit or remote. AGENTS SHA-256 is `a796d0c867d3d52b8048c73b07d67054dc1cf06c068fe41f7dd7e6b768e61244`. The actual parser, plugin, five production Java files, original unit/functional tests and guide are copied from that snapshot. File hashes are in `gradle/configuration/template-source.json`; external updates are not adopted automatically.

## Initial difference list

| State | Content and action |
| --- | --- |
| Already aligned | Target-owned source; one implemented NeoForge target and four explicit governance nodes; ordinary vulkan-renderer CMake component; native tests/resources/docs/licenses retained. |
| Change required | Replace root legacy properties parsing with the real plugin; separate Project/Target/instance/local facts; consume them through native ModDev runs; remove ordinary player/window preferences from scenario JSON; establish traversable runtime ignores; make empty checks meaningful. |
| Preserve | All five independent validation instances, old runtime/cache trees, historical validation evidence and audit ledgers. Existing package/JNI/native artifact names and Early Window behavior stay fixed. |
| Explicit exceptions / unknowns | Historical `run/instances/legacy*` ownership remains unresolved; protected in place. Native Gradle `buildSrc` and upstream `.assets`/standard files have actual build or provenance roles, not fake components. Product audit unknowns are not accepted or repaired by this migration. |

## Implementation and current validation

The configuration suite now ran 65 tests with zero failures/errors/skips: the 60 unchanged template tests plus five project-only real ModDev preparation tests, including local JDK, cascade, explicit empty values, invalid inputs and configuration-cache transitions. The first two project-test runs hit isolated TestKit dependency-download/TLS failures; the successful run uses the invoking Gradle dependency cache with the same declared artifacts. This does not certify a cold network download. Actual ModDev preparation produced argument files for all five unchanged scenario directories. A fresh full build in build/template completed in 7m13s: Java/JNI/native/Early Window/17 CTest/JVM tests/stage/package succeeded. These are current engineering checks, not game or installation acceptance.

The user subsequently authorized signed local checkpoint/engineering/naming/history commits, with no push. MCVR complete remote heads/tags and valuable available local history must be preserved in the Radiance commit graph. Current naming is Radiance. Historical text remains as evidence. Git operations are owned by this task alone; there are no concurrent implementation agents.

Automatic approval review rejected the combined recycle/move retirement command with only “blocked by policy”. The safer implementation copied the two maintained runtime helpers to gradle/runtime, preserved old paths as forwarding compatibility entries, and left pre-existing empty cmake/extern directories. No second properties parser remains; no runtime data was retired.

## Current result and traceability

The independent local Radiance clone completed a fresh build in 9m33s with task build caching disabled: 65 configuration tests, 26 JVM tests, 17 CTest tests, Java/JNI, renderer and Early Window native builds, manifest/shader checks and packaging. Real ModDev preparation covers all five independent scenarios. Its finalized JavaExec has no separate maximum-heap/direct -Xmx setting; the effective heap is supplied once through ModDev's prepared VM arguments. Product version remains `0.1.5-alpha+1.21.1` after history integration. Installed external tools and declared dependency caches are used, not a sibling MCVR checkout.

9,947 product/source/resource/test/fixed-dependency files and 1,398 protected runtime/cache files remain byte-identical to the starting backup. The seven original template production/test Java files remain byte-identical to the frozen template. Project-only tests and guide/build additions are separately identified by template-source.json. The profile changes remove ordinary runtime preferences without changing the five scenes or their fixed mod inputs.

Radiance upstream `414d8e330a2fc6cb1e8630cc95f2302b2b97a0e8` and initial port `cd67362ebb5d9f49c7e23e53f51d35d25409e6a2` remain original commits. Current Mod implementation is in `versions/1.21.1-neoforge/`. MCVR upstream `9905c81b1999f5845bf66d13501d371c16adf561`, fork `b611d24a044384290beaf93131ad51bf14e3d107` and the full selected history are retained; active source, shaders, tests, fixed dependencies, licenses and technical material are in the ordinary `components/vulkan-renderer/`.

Version/Loader adaptation, project rendering changes, compatibility extensions and engineering integration retain their distinct mappings in [the audit ledger](audit-port-status.md). This batch changes configuration/build/path wiring and governance/verification; it does not change rendering algorithms, package/JNI names, compatibility policy, Early Window behavior or the installation contract. Retained implementations are not automatically accepted product choices.

## Explicit limits and handoff

- The unified configuration plugin and native Loader preparation are connected; no active second properties parser or per-profile preference layer remains. `buildSrc`, `.assets` and upstream/component-native material retain their documented ecosystem roles.
- `scripts/` keeps only compatibility forwarding. Pre-existing empty `cmake/extern` directories and unresolved protected `run/instances/legacy*` remain; the retirement attempt was rejected as noted above. This does not claim complete root-directory retirement. A clean clone contains no empty cmake/extern or historical runtime directories.
- Formal installation, known DLSS/texture/HiDPI findings, Early Window CPU rendering and user/visual acceptance remain unexecuted and unresolved. No Minecraft/GUI was started; no product target/platform was added.
- MCVR history is integrated locally, including a pure merge with an identical file tree. Publication, independent remote clone verification and permission to remove the source fork remain outstanding. No push was performed.

Current evidence is [template-configuration.json](../../validations/conformance-native-integration/result/template-configuration.json), with [history/ref details](migration-mcvr-history.md). Earlier transfer/verification JSON and audit records remain evidence for their own snapshots. Recovery and scratch copies are retained; they are not normal source dependencies.

The independent-clone counterexample changed a governance node to implemented and was correctly rejected for missing product source; restoring the file passed and reused configuration cache. A deliberately wrong recorded history-merge tree also failed the boundary check. Both runtime ignore areas accepted explicit nested-file negations while ordinary files and local.properties remained ignored. All test mutations were restored in the isolated clone. The original MCVR refs, index bytes and clean worktree were verified unchanged.
