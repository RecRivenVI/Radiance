# Vulkan renderer component

`components/vulkan-renderer/` is the ordinary CMake source component used by Radiance. Source, shaders, native tests, fixed dependencies, licenses and original technical material remain here. `VENDOR.json` records source/dependency revisions; `HISTORY.json` and [history preservation](migration-mcvr-history.md) distinguish upstream, fork and recovered local history. No independent MCVR checkout, gitlink or generated product-source overlay is needed. Component and vendored upstream governance, funding and workflow files are retained as source material in their native positions; they do not become root GitHub automation or override current root/component instructions.

## Current build entry

Use the repository Wrapper with the existing JDK/toolchain, MSVC, CMake and Vulkan SDK prerequisites:

```powershell
.\gradlew.bat buildAndCollect --configuration-cache
.\gradlew.bat :version:1.21.1-neoforge:inspectRunConfiguration --configuration-cache
```

The first command includes template configuration tests, Java/JNI generation, the renderer and Target-owned Early Window native build, CTest, JVM tests and package checks. The second executes the real NeoForge run-preparation tasks for the five separate validation scenarios and inspects their prepared arguments; it does not launch Minecraft. See [configuration guide](guide-configuration.md) for the shared/local runtime settings and [this engineering change](migration-template-configuration.md) for current evidence and limits.

The only implemented product target is `1.21.1-neoforge`. Four other registered nodes express governance only. The Target writes `build/template/1.21.1-neoforge/configuration/native-target.json` from the unified configuration model; this generated handoff is not a second source of Target Facts. Java writes JNI headers under `build/template/1.21.1-neoforge/generated/jni`. CMake consumes both, builds directly from the component into `build/template/1.21.1-neoforge/rt`, and installs into `build/template/1.21.1-neoforge/vulkan-renderer/install`. The short native path avoids MSVC path-length limits.

The component retains its native CMake presets. Before using them, run `:version:1.21.1-neoforge:compileJava` and `:version:1.21.1-neoforge:writeNativeFacts`; presets use separate generated directories. The Gradle entry is the verified integrated build path. Existing NRD/ShaderMake/toolchain downloads remain external prerequisites; this is not a claim of a fully offline build.

`radiance_native`, `radiance_early_window`, JNI symbols and manifest names keep their current contracts. Early Window's Target-specific adapter stays under `versions/1.21.1-neoforge/`; its implementation is not merged into a generic renderer abstraction. The five scenario profiles contain fixture inputs, not another source of heap/window/player/extra arguments. Existing runtime and historical data remain protected.

## Historical component import record

The following account and its linked `verification.json` describe the preceding component import. Its file counts, build paths, no-commit statement and PASS results are preserved as historical evidence. Current configuration paths, Git permissions and engineering results are given above and in the linked current records.


`components/vulkan-renderer` is ordinary source owned and tracked by Radiance. It has no gitlink, nested Git repository or symlink. The independent MCVR checkout is no longer a build or runtime dependency and was not modified by this task.

The effective engineering tree derives from `RecRivenVI/MCVR` commit `b611d24a044384290beaf93131ad51bf14e3d107`. The temporary `components/mcvr` checkout received the needed current JNI/CMake/preset/CTest and NRD/FFX adaptations; all of that effective source was copied and hash-checked before its submodule registration was removed. The abandoned generated-overlay proposal was never used by the build.

The copy contains 7,128 files / 1,357,213,470 bytes from the MCVR engineering tree and its 14 direct dependency repositories plus DLSS's NVIDIAImageScaling dependency. `components/vulkan-renderer/VENDOR.json` records each exact revision. Tracked SDK libraries and shader compiler tools are retained; local build/out/runtime files, credentials and Git management information are excluded. Original documentation and license exceptions are retained. Source byte copies were checked individually; subsequent component instruction updates are identified as current authority above their historical text.

Build from the Radiance root with Java 21, MSVC, CMake and Vulkan SDK:

```powershell
.\gradlew.bat :version:1.21.1-neoforge:buildAndCollect --configuration-cache
```

No submodule initialization is required. Java generates real `build/generated/jni` headers. The native component uses `MCVR_JNI_INCLUDE_DIR` and `RADIANCE_JAVA_TARGET_DIR`, builds in the target's fresh `build/renderer`, installs in `build/vulkan-renderer/install`, and runs all 17 native CTest tests against the actual source. The shorter build path avoids FFX-generated header paths reaching MSVC's 260-character limit. Native presets express this parent-layout contract and require JNI generation first. NRD's existing ShaderMake/MathLib/tool downloads remain standard build prerequisites; no external MCVR checkout or nested submodule initialization is needed.

The first ordinary-component configure revealed minizip's default source-tree Git clones. Its four resolved dependencies are now also vendored at their exact observed commits in `extern/minizip-dependencies` (2,241 files / 24,909,579 bytes); CMake explicitly uses these directories through FetchContent source overrides. The initial clones were moved intact to ignored `build/vulkan-renderer/fetched-source-before-vendor`, outside authored source. Additional revisions are recorded in VENDOR.json; existing dependency versions were not upgraded.

Radiance then builds its own `integration/early-window` entry against `src/main/native/early_window`, verifies the combined stage/JNI hash/manifest/shaders, and packages the JAR. `radiance_native` and `radiance-native-manifest.json` names remain the host's explicit packaging contract. NRD encodings, generated NRD headers, and existing FFX build filtering remain unchanged in behavior. Generated dependency mirrors stay in build; the authored renderer is compiled directly from the ordinary component, without an overlay patch layer.

The old 391 import rows are preserved in `validations/conformance-native-integration/result/transfer.json`; they map to 390 distinct embedded files (one merged CMake destination). Those embedded copies and the 14 root extern gitlinks/worktrees were removed. Recoverable Git object caches may remain under `.git/modules`; they are not source or build dependencies. Historical user instances remain untouched.

Only this Radiance working tree contains the final changes. No commits or pushes were made during these architecture changes. No game was started and no runtime deployment or visual acceptance occurred.

Current automated verification is recorded in [verification.json](../../validations/conformance-native-integration/result/verification.json): fresh build and repeat build passed, 17 CTest and 26 JVM tests passed, all 96 JNI exports were found in the DLL, and the five run dry-runs reused configuration cache. The 353 protected instance files remained byte-identical. Initial MSVC path-length and source-tree clone issues were resolved as described above; these checks are not visual acceptance.
