# C2ME / C2ME OCL compatibility investigation

Date: 2026-09-20

Scope: determine whether Radiance can use C2ME NeoForge and C2ME OCL as third-party chunk/world-generation optimizers. This investigation did not change Radiance or MCVR product source.

## Result

| Combination | Result | Practical meaning |
| --- | --- | --- |
| Radiance + C2ME NeoForge | PASS | Can be offered as an optional user-installed compatibility target. No Radiance-specific patch was required in the tested run. |
| Radiance + C2ME + OCL + ScalableLux | PASS in isolated Radiance profile | OCL initialized the RTX 4080 SUPER, compiled all seven world-generation programs, entered a world, rendered through Radiance, and shut down normally. |
| Radiance + Create + Aeronautics + Sable + C2ME | PASS | The full current mod combination entered a 32-chunk-distance world, survived a logged F3+A chunk reload, and exited normally. |
| Full combination + OCL + ScalableLux | BLOCKED before game startup | Sable explicitly declares itself incompatible with ScalableLux. This is not a demonstrated Radiance/OCL conflict. |

## What these mods optimize

C2ME changes the integrated-server chunk scheduler, generation, storage and I/O paths. Its published client mixins only adjust view-distance/options and fog-distance behavior. Static inspection found no C2ME mixin targeting Radiance's main takeover points such as `LevelRenderer`, `SectionRenderDispatcher`, or `RenderSection`.

C2ME OCL accelerates only the noise and biome stages of world generation through OpenCL. It does not accelerate Radiance section translation, Vulkan uploads, ray-tracing acceleration-structure work, path tracing, denoising, or F3+A rebuilding of already present client sections.

This boundary was visible in the base-C2ME runtime test: the client still produced roughly 83,000 Radiance section work items at 32-chunk view distance, and the logged F3+A reload caused the Radiance section rebuild to drain again. C2ME therefore helps supply new chunks and reduces integrated-server pressure, but does not replace the Radiance-side chunk-rendering work currently under investigation.

## Package and runtime constraints

- C2ME NeoForge tested version: `0.4.0-alpha.0.122+1.21.1`.
- C2ME OCL tested version: `0.4.0-alpha.0.122+1.21.1`.
- ScalableLux tested version: `0.3.0-alpha.0.8+1.21.1`.
- These current 1.21.1 builds are alpha backports rather than stable releases.
- The latest package set effectively requires Java 25. The outer C2ME classes are Java 21-compatible, but its nested native-math module contains class-file major version 69. The repository Gradle Java 21 path could not parse it; the Java 25 direct launch worked.
- OCL requires base C2ME and, in current Modrinth metadata, ScalableLux.
- OCL requires an OpenCL 1.2 device with `cl_khr_fp64`. The tested RTX 4080 SUPER was accepted.
- C2ME is MIT-licensed. C2ME OCL is All Rights Reserved. Both should remain optional user-installed dependencies; OCL in particular must not be bundled or redistributed without permission.

## Runtime evidence

### Base C2ME with the full mod combination

Artifact: `c2me-base-java25-r25-20260920`

- C2ME initialized its modules and native-math AVX2 path.
- It detected 20 physical / 28 logical CPUs and selected global executor parallelism 19.
- Radiance, Create, Aeronautics and Sable initialized together.
- The run entered a 32-chunk-distance world.
- A logged F3+A reload (`[Debug]: Reloading all chunks`) completed without a mixin conflict, native crash or Vulkan device loss.
- Shutdown reached normal world save and C2ME storage-thread termination.

Earlier `r23` and `r24` attempts were harness failures, not compatibility failures: one rejected an unregistered dependency set and one used the repository Java 21/Gradle path against Java 25 bytecode.

### OCL in the full mod combination

Artifact: `c2me-ocl-java25-r26-20260920`

The loader stopped before entering the game because Sable declares an explicit incompatibility with ScalableLux. The later missing `RadianceLoadingOverlay` message is secondary fallout after the game layer failed to form.

### OCL with Radiance, without Sable/Create

Artifact: `c2me-ocl-radiance-only-r27-20260920`

- Started 23:29:58, ended 23:32:05, joined a world, process exit code 0.
- OCL detected `NVIDIA CUDA OpenCL 3.0 CUDA 13.4.89` and `NVIDIA GeForce RTX 4080 SUPER`.
- It applied its NVIDIA incomplete-CL30 workaround.
- All seven programs for overworld, nether and end were generated and compiled.
- Radiance Vulkan and Streamline rendering operated concurrently.
- No native crash, Vulkan device loss or mixin conflict occurred.
- OCL released all three dimension programs and closed the OpenCL device during normal shutdown.

## Recommendation

1. Treat base C2ME as supported optional compatibility now. No product hook is justified until a concrete conflict is observed.
2. Treat C2ME OCL as supported for Radiance profiles that include its required dependencies and do not include Sable.
3. Mark the Sable + ScalableLux combination blocked. Do not simply remove Sable's incompatibility declaration; first test lighting correctness and concurrency across both mods.
4. Add three explicit compatibility profiles to the external test harness:
   - Radiance + C2ME;
   - Radiance + C2ME + OCL + ScalableLux;
   - full mod combination, expected blocked while the Sable/ScalableLux conflict remains.
5. Document Java 25, OpenCL hardware requirements, alpha-version status and the fact that C2ME/OCL optimize world generation rather than Radiance rendering.
6. Benchmark fresh, identical-seed worlds for vanilla, C2ME, and C2ME+OCL. Measure both chunk arrival/generation throughput and client frame-time while continuously entering new terrain. OCL shares the GPU with Radiance, so throughput gains must be checked against path-tracing frame-time contention.

## Cleanup and repository state

- Experimental third-party jars were removed from the active repository test-instance mod directories after the runs.
- The full test instance was restored to Create, Aeronautics, Radiance, RadianceAudit and Sable; the base instance was restored to Radiance only.
- No relevant Java process remained after testing.
- The user's Prism production instance was not modified with C2ME, OCL or ScalableLux.
- Downloaded packages and inspection material remain under the local compatibility lab for reproducibility.
