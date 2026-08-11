# Target layout migration

Current architecture update: the renderer is ordinary Radiance-owned source in `components/vulkan-renderer`, including fixed vendored dependencies. See [renderer component integration](build-vulkan-renderer.md), [current template/configuration integration](migration-template-configuration.md), and [MCVR history preservation](migration-mcvr-history.md). The following original directory-migration record describes the preceding embedded layout and its validation. Its old template hash, runtime preference authority, helper paths, Git state and PASS counts are historical, not current instructions or current build results.

Authority: Ravens-mod-template AGENTS.md SHA-256 `5527638e1fc256cdd2c4594e1d3021de6665257673e7cc092452db1fa490c6b8`.

Before structural edits, the existing tracked and non-ignored work was saved in SSH-signed `Update` commit `9fe7811cf8b27f97b8b77307e982e3cc3942aee5` and pushed to the existing `origin/stonecutter` branch. Its parent is `cd67362ebb5d9f49c7e23e53f51d35d25409e6a2`. No migration changes belong to that save commit.

| Previous path | Current path |
| --- | --- |
| `src/main`, `src/test` | `versions/1.21.1-neoforge/src/main`, `src/test` |
| `cmake/native` | `versions/1.21.1-neoforge/cmake` |
| `build.neoforge.gradle.kts` | `versions/1.21.1-neoforge/build.gradle.kts` |
| `run/profiles/.../s1.json`, S1 instance | `validations/conformance-clean-client/task/profile.json`, `instance/client` |
| `run/profiles/.../s2.json`, S2 instance | `validations/compatibility-standard-client/task/profile.json`, `instance/client` |
| Create/Sable/Aeronautics fixture profiles and instances | `validations/compatibility-{create,sable,aeronautics}/task/profile.json`, `instance/client` |

Native rendering is target-owned: its JNI class names, Minecraft vertex/material semantics and shader contracts are part of this target. It has not been relabeled as a generic shared component. The pinned foreign `extern/` repositories remain unchanged; CMake retains its native entry inside the target. JVM tests and native CTest remain alongside source.

The prior Stonecutter single-root replay had no actual authored cross-target conditions. Explicit Gradle descriptor mapping now expresses `:version:<target>` and target-owned source directly; it does not introduce a second implementation or change Minecraft support. The four other registered targets remain governance-only. Gradle Wrapper and ModDevGradle versions stay unchanged. Target facts move to each target.properties; product identity remains in root gradle.properties. No fake common component is introduced.

All five existing runtime configurations are verification scenarios, so none was promoted into a new daily instance or multiplayer variant. Their independent worlds, config, mod sets and renderer data were moved intact. Runtime profile settings remain the authority for those scenarios. Shared helpers remain in scripts/runtime, preventing five copies of the same downloader/verifier. Existing offline cache stays ignored in run/cache; it is not a game directory.

Historical `run/instances/legacy` and `run/instances/legacy-shared-import` remain in place because their historical/mixed lineage is not a current daily or verification ownership decision. No unknown data was deleted or merged. Earlier evidence outside this repository remains unchanged and is not imported as current PASS.

The initial move manifest and content check cover 1336 files and live under ignored build/layout-migration. Source behavior is preserved; build/path and current documentation references are the intended edits. Profiles are now outside the legacy local `/run/` exclusion. No Git metadata exclusion was changed in this migration. A narrow authored .gitignore exception exposes pre-existing buildSrc Kotlin source whose package directory was previously accidentally covered by `**/build/`.

Validation results must distinguish configuration/compile/native tests from game acceptance. This task never launches Minecraft; all visual and interaction checks remain USER-owned. Use the validation.md in each scenario for its actual root-relative commands.

## Current automated checks

- Wrapper `projects` and `tasks`: PASS; `:version` maps to `versions` and the five descriptors have the intended names. Only NeoForge 1.21.1 has product tasks.
- `scripts/Verify-Layout.ps1`: PASS; five independent profile paths and their physical JAR hashes verified.
- All five run tasks with `--dry-run --configuration-cache`: PASS, including cache reuse; no client was started.
- `:version:1.21.1-neoforge:buildAndCollect`: PASS (7m 14s for the full native rebuild); 26 JVM tests and 17 CTest tests passed, including JNI/native manifest/shader/package checks.
- 353 moved instance files, 224 Java files and 374 non-CMake native/shader files have unchanged content hashes. Ten mapped files intentionally changed: target build script, main CMake entry, three test path lookups and five profile path records.
- Initial failure: old CMake cache retained the prior source root. A new `build/native/target-build` directory was used; the old generated cache was retained, not mistaken for a runtime instance.
- Existing compiler warning: `dlss_wrapper.cpp` reports a potentially uninitialized `result` (C4700). Renderer source was unchanged; resolving it is outside this structural migration.
- Migration working tree is intentionally uncommitted and unpushed. User visual/runtime acceptance is not performed.

Remaining ownership decision: historical `run/instances/legacy*` can be assigned a new location only after identifying which lineage/scenario should own it. Current tested profiles have no references to those historical game directories.
