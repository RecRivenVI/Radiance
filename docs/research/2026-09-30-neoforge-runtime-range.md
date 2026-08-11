# NeoForge compile target and runtime interval

Observed on: 2026-09-30.
Status: implemented; build-verified; automated-verified at the two named endpoints.
Historical boundary: the initial interval below was superseded by the subsequent standalone
Radiance investigation at the end of this document. Its 228/252 test results remain historical.
Sources: official NeoForge Maven artifacts, FML sources, the dirty Radiance worktree over
`8ad46a0967d66c4ed5e9b0b9c3a938409e4905bc`, and paired MCVR over
`e6e8153e0ff8c3ab6c3108d8d631beea1f99810e`. This is a bounded loader/API review, not a
revalidation of the accumulated V4 rendering work.

## Decision and ownership

Compile Radiance and its optional Audit module against **NeoForge 21.1.252**. Declare the shared
runtime interval **`[21.1.228,21.2)`**, independently from the compile target. Minecraft remains
exactly 1.21.1 and the language loader remains `[4,5)`. The installation recommendation is 252;
the interval admits 228 and later 21.1 patches without promising that every patch was play-tested.

228 is a conservative dependency-stack and verification floor, not a claim that a Radiance API was
introduced there. The pinned Aeronautics/Simulated/Offroad and Sable packages require at least
21.1.228; Create requires 21.1.219. Extending below 228 would require a separate reduced-stack
assessment and does not help the current target combination.

Bundled versions follow their parent mods. Keep Create 6.0.10 (Flywheel 1.0.6 and Ponder 1.0.82),
Aeronautics 1.3.2 (Simulated/Offroad 1.3.2), and Sable 2.0.5 (Veil 4.3.2, Companion 1.6.0 and
Rapier 2.0.5). Standalone Veil 4.5.1 is not substituted for the parent's embedded 4.3.2.

## Actual version-sensitive surfaces

| Surface | Evidence and consequence |
| --- | --- |
| SERVICE discovery and GAME extraction | `GraphicsBootstrapper`, `ImmediateWindowProvider`, `ILaunchContext`, `IModFileCandidateLocator`, `IDiscoveryPipeline`, discovery attributes and `@Mod.dist` sources are unchanged between the sampled FML 4.0.24/35/41/42/43/44 versions. The used `FMLLoader.getLoadingModList()` remains available. The concrete SERVICE classifier and GAME reader are exercised by the assembled-JAR tests at 228 and 252; no exact-252 dependency was found. |
| FML implementation | 228 carries FML 4.0.42; 248 carries 4.0.43; 250/251/252 carry 4.0.44. Changes include Mixin failure attribution and failed dependency-cycle sorting. These are real error-path differences, but do not change our window/locator signatures or require a single NeoForge patch. Earlier FML also changed bindings/service-resource closure; unchanged SPI names alone would not prove all older versions compatible. |
| NeoForge client API | Twelve of thirteen explicitly imported NeoForge API source files in GAME/Audit are byte-identical at 228 and 252: `ClientHooks`, section geometry, render stages, fluid extensions/cache, GL state backup, dimension effects, model data, render-type set, client commands, config-screen factory and the event bus holder. `ScreenEvent` only adds nullable annotations to the new-screen field/setter in this comparison. |
| Patched Minecraft targets | Same pinned NeoForm 1.21.1 input. Between 228 and 248 the client gains `LevelEvent.Unload` during disconnect, and block-model AO corner values are corrected. Radiance releases its scene from `LevelRenderer.setLevel(null)`, not that newly added event; neither change removes the current injection target. Resource-manager marking still uses actual `PackType`; section compilation still uses the five-argument NeoForge extension. These are source/call-chain findings, not proof that every Mixin has run on 228. |
| Mixin/compiler dependencies | 228 and 252 both carry MixinExtras 0.5.3, bus 8.0.5, ModLauncher 11.0.5 and SecureJarHandler 3.0.8. Older samples have different MixinExtras/ASM/FML versions. The current `WrapMethod` and transformation paths cannot be advertised on all historical 21.1 builds merely because Minecraft's version matches. |
| 251 to 252 | Only `ItemHandlerCopySlot`/`StackCopySlot` Java sources change: preserve underlying slot index and retain the old constructor as deprecated. Minecraft patches are unchanged (only userdev configuration/version coordinates differ). None of these slot classes is used by Radiance. |

The import matrix covers 31 explicit NeoForge/FML imports, not every inherited Minecraft extension
or third-party transitive API. Endpoint compilation supplements that static matrix. Future 21.1
patches can still change injection bodies; an accepted metadata interval is not universal runtime
certification. 21.2 and other Minecraft versions remain excluded.

## Validation and artifact boundary

Java 21, ModDevGradle 2.0.140, at most two Gradle workers. At **each of 228 and 252**:

- GAME: 249 tests passed; Audit: 25 passed.
- Bootstrap: 12 tests, 10 passed and 2 opt-in GPU tests skipped.
- `verifyDistributedJar`, `verifyMavenDevelopmentArtifact`, and `:radiance-audit:verifyAuditJar`
  passed. A separate actual-JAR TOML check confirms both modules declare the same interval.
- The existing distribution test parses the real nested GAME declaration with Maven's range
  implementation: 228/250/251/252 and the active build target are accepted; 227, 21.0, 21.2 and
  21.4 are rejected. It also runs the actual FML SERVICE classifier and GAME reader.

Tests executed in both runs; dependency/build tasks reused caches where reported. The initial floor
command passed an unquoted dotted Gradle argument through PowerShell and was interpreted as `21`;
configuration failed before compilation. The corrected quoted command passed. This command failure
is retained and is not evidence of incompatibility with 228.

Both builds produced the same installable JAR content (the compile version is no longer encoded as
an exact runtime restriction):

- Radiance JAR SHA-256: `26C14B4052993C081F474234FE55E4824AE2C8EC8ACB75FD370A4F42220F92D5`.
- Audit JAR SHA-256: `0FF49A194547551E2883BF3F7581360C707984D90DE4B8DCE8B67403065F2DC0`.
- Embedded/existing core DLL: `2447D81A5F67821EC0FF7672B1F3665AA8DD61FF900B1C5A68FEDF47B88A733C`.

Native code, shaders and JNI did not change in this loader task. Native builds/tests and the Audit
native-collector rebuild were deliberately omitted; the existing paired native binaries are reused.
No Minecraft client was started, and no Prism loader, mods or settings were changed. The user is
still testing candidate M on 251 (`BBA536CB7BCA8368C1817B73B35BAA44B2143BABFED12D92D604343AB6DDCDCE`);
its observations must not be assigned to this newly built JAR or to 228/252.

Evidence (non-portable):
`D:\Workspaces\Artifacts\RadianceDependencyReview\20260930\NeoForgeRange` contains official
source/userdev/POM downloads with SHA-256 manifests, per-version change lists, source API matrix,
patch diffs, build logs, and `verified-228` / `verified-252` JARs and test XML. No full logs or
third-party binaries are committed. Fresh packaged-client startup and gameplay at the endpoints
remain unperformed; future patch compatibility and public binary licensing remain separate gates.

## Official references

- [NeoForge metadata and separate compile/runtime properties](https://docs.neoforged.net/docs/1.21.1/gettingstarted/modfiles/)
- [NeoForge versioning and Maven interval semantics](https://docs.neoforged.net/docs/1.21.1/gettingstarted/versioning/)
- [Official NeoForge Maven repository](https://maven.neoforged.net/releases/net/neoforged/neoforge/)

Implementation and later acceptance belong in the [development ledger](../DEVELOPMENT_LEDGER.md).

## Subsequent standalone investigation: minimum 62, no NeoForge upper cap

Status: implemented; build-verified; automated-verified at 62 and 252; bounded runtime-observed at 62.
This supersedes `[21.1.228,21.2)`: the user requested a floor derived solely from Radiance, not its
optional integration stack. Declare **`[21.1.62,)`**, retain the 252 compile target, exact
Minecraft `[1.21.1]`, and the independent FML `[4,5)` API contract. Removing the NeoForge upper
cap does not claim support for another Minecraft version or guarantee future FML/Mixin changes.

### Lower-bound evidence

1. The first 20 published 21.1 patches (1 through 21, excluding unpublished 14) carry MixinExtras
   0.3.5; 22 introduces 0.4.1. The actual 0.3.5 JAR lacks `WrapMethod`, used by core world/GUI
   Mixins; 0.4.1 contains it. Radiance embeds only SnakeYAML, not a replacement MixinExtras.
   This is a necessary floor of 22, but was not sufficient.
2. A real 22 client using the packaged Radiance JAR reached resource loading and failed to apply
   `HumanoidArmorLayerMixins`: its required six-float `renderArmorPiece` selector does not exist.
   It never entered the world. The observer later stopped it normally after its bounded startup
   deadline; exit 0 is not a success for this case. There was no device loss.
3. Official **61 and 62 userdev patch artifacts** identify the exact stronger boundary. 61 lacks
   that overload; 62 introduces it together with `IClientItemExtensions.setupModelAnimations`.
   The originating [NeoForge commit b92c510 / PR 1541](https://github.com/NeoForged/NeoForge/commit/b92c510d64ed9506140d74538f4699277e8193bd)
   changes the four armor calls to pass the six animation values. Current Radiance consumes this
   signature to merge armor glint into the PT base surface. No old-signature fallback or extra
   library was added to lower the floor artificially.
4. `NeoForgeArmorTargetTest` reads the actual active patched class and the compiled Radiance Mixin
   selectors, without initializing Minecraft, and verifies all required HEAD/operation/RETURN
   targets. Endpoint execution at 62 and 252 passes. The 62 real-client qualification verifies
   actual Mixin application and world rendering, supplementing the bytecode check.

The 61 userdev SHA-256 is `B28D29C3297B5C4BCDFAD5511F9559EBABC299A76E340C57A65D5DEC48EFBD7F`;
62 is `DF5EB76C76E684FD0838A2F7A0BBADCD96F4EE0D849692A2911CF4E216F5A7AE`.
`earliest-mixinextras-boundary.json`, `armor-animation-boundary.json`, `armor-61-62.diff`, downloaded
JARs and the upstream commit history are retained in the existing external evidence directory.

### Validation, failures and runtime scope

At 62 and 252: GAME **250 passed**, Audit **25 passed**, bootstrap **10 passed / 2 opt-in GPU
tests skipped**. Actual distribution, Maven GAME and Audit package checks passed. All named test
tasks executed; some 252 compile/package dependencies reused caches. Python preflight tests
executed **3 passed**. Native/shader source and core DLL did not change; native build/CTest were
not repeated. The range test now accepts arbitrarily high NeoForge values while separately
rejecting Minecraft 1.21.2/1.21.4 and NeoForge below 62.

Real clients use repository cases under `run/neoforge-minimum-20260930/`, 1280x720, render distance
8, master volume 0, no Create/Sable/Aeronautics/Veil installed, and only the loader-neutral passive
Audit adapter/startup companion. Native defaults select DLSS balanced with FG off; no driver or
global setting was changed. The companion prevents activation/input changes and normal stop saves
the integrated server. This is loader qualification, not visual or performance acceptance.

- `n22-minimal-world-1`: launcher fixture omitted the Java classpath needed by old FML's userdev
  locator; failed before Minecraft discovery. Not a product compatibility verdict.
- `n22-minimal-world-2` and `-3`: rejected by the quiet-performance preflight; never launched.
- `n22-minimal-world-4`: fixture path rewriting incorrectly moved two classpath artifact references
  to the case directory; failed discovery. Corrected using the exact generated launch/classpath
  files and checking every JAR path before launch. Not a product compatibility verdict.
- `n22-minimal-world-5`: genuine armor-selector failure, `worldEntered=false`, zero world frames,
  `WORLD_NOT_ENTERED`, bounded normal stop at 181 s. The failed evidence remains intact.
- `n62-minimal-world-1`: actual packaged SERVICE/GAME startup and vanilla fixture entry, about
  30 s of world observation after entry, **6,588 sampled world frames**, all dimensions saved,
  world-scene release and SDK shutdown records, client exit 0 after **54.12 s**, no timeout close,
  JVM crash or device loss. Foreground samples 0 and activation guard observed. These frame counts
  are only path-execution evidence; no FPS/throughput conclusion is drawn.
- `n62-armor-world-1`: a reused launcher fixture appended `--quickPlaySingleplayer` twice and
  stopped in argument parsing with a crash report. Corrected before repeating; this is a harness
  error, not an armor, GPU or loader compatibility failure. Its evidence is retained.
- `n62-armor-world-2`: final 252-built package on 62 with an armor stand wearing a Protection I
  diamond helmet. World entry, **7,137 sampled world frames**, normal exit after **63.44 s**,
  all-dimension save, scene-release/SDK-shutdown records, zero foreground samples and activation
  protection are observed. Saved entity NBT confirms the equipped helmet/enchantment; no JVM crash
  or device loss. This exercises the actual armor renderer in the base pipeline without declaring
  its visible glint or wider material semantics visually accepted.

The successful 62 JAR and the final 252 build are byte-identical:
`C3E4DB22F2092354E8C2466C6F589C98FE8585B259F83C3B80EF20F17CA03877`.
Audit JAR: `311E23D4ED9BDEC528317C2A954304F3C92841A316EB8B3C4902FB241FE4F9A4`.
Built/embedded/extracted/actually loaded core remains
`2447D81A5F67821EC0FF7672B1F3665AA8DD61FF900B1C5A68FEDF47B88A733C`.
No Prism files or Git refs/staging changed. Optional-mod compatibility, broader visuals, GPU root
cause and long-term stability remain separate; successful startup is not universal acceptance.

The diagnostic launcher now permits explicitly classified minimum-loader qualification under
moderate unrelated load only with an isolated marker, no other Minecraft, at least 8 GiB free VRAM,
CPU at most 20% and GPU at most 50%. The flag is default-off; absent sensors reject the exception.
Every such qualification is marked invalid for performance comparisons, even if initially quiet.
The normal benchmark gate is unchanged. Heavy/uncertain load is still rejected.

### Optional compatibility constraints

NeoForge's [dependency schema](https://docs.neoforged.net/docs/1.21.1/gettingstarted/modfiles/)
supports `type="optional"`: absence is allowed, but an installed version must satisfy `versionRange`.
`incompatible` rejects installed versions inside the specified interval; `discouraged` warns for
those versions. The actual FML 4.0.24 and 4.0.44 `ModSorter.verifyDependencyVersions` implementations
confirm these semantics. There is no separate compatibility-table keyword; these records use
`[[dependencies.radiance]]` without creating mandatory installation requirements.
No speculative compatibility constraints for third-party versions were added in this task.

## Subsequent decision: temporary exact optional-mod compatibility pins

Status: implemented; build-verified; automated-verified for the packaged declarations.
The user subsequently authorized compatibility intervals limited to the current publications,
with embedded modules following their parent packages, and explicitly excluded Zume. This
supersedes only the preceding statement that no third-party restrictions had been added.

The GAME declaration is the canonical interval list. All entries use `type="optional"`,
`ordering="NONE"`, `side="CLIENT"`. Missing mods remain allowed; ordinary installed release
versions must match. FML's existing development-placeholder/version-support exceptions are not
overridden. The separate Minecraft/NeoForge requirements and `[21.1.62,)` loader range are unchanged.

| Mod ID | Exact interval | Identity source |
| --- | --- | --- |
| `create` | `[6.0.10]` | Latest matching Create parent publication |
| `flywheel` | `[1.0.6]` | Embedded in Create 6.0.10 |
| `ponder` | `[1.0.82+mc1.21.1]` | Embedded in Create 6.0.10, including its actual qualifier |
| `sable` | `[2.0.5]` | Latest matching Sable parent publication |
| `sablecompanion` | `[1.6.0]` | Embedded in Sable 2.0.5 |
| `veil` | `[4.3.2]` | Embedded in Sable 2.0.5, not standalone 4.5.1 |
| `aeronautics_bundled` | `[1.3.2]` | Latest matching Aeronautics parent publication |
| `aeronautics` | `[1.3.2]` | Embedded in Aeronautics bundle 1.3.2 |
| `simulated` | `[1.3.2]` | Embedded in Aeronautics bundle 1.3.2 |
| `offroad` | `[1.3.2]` | Embedded in Aeronautics bundle 1.3.2 |

Current Modrinth version lists were refreshed for Minecraft 1.21.1/NeoForge, including all release
types: Create `UjX6dr61`, Sable `U678xqle`, Aeronautics `44pLdPGg` remain the latest matching
parent packages. The actual TOML mod versions are used, not parent filename qualifiers such as
`6.0.10+mc1.21.1`. Registrate, Sable Rapier, Molang compiler and GLSL processor have no NeoForge mod
declaration; no fictional mod ID or constraint is assigned. This policy freezes the tested parent
combination and does not assert a new dedicated rendering adapter for every embedded component.

The assembled-JAR regression recursively reads the actual compile-source parent/embedded archives,
checks the complete ten-ID set, exact Maven intervals, matching published versions, client/NONE
classification, exclusion of Zume, and that only Minecraft/NeoForge remain mandatory. Unsupported
sample versions are rejected by the same Maven range implementation used by FML. Bootstrap
execution: **13 tests, 11 passed, 2 opt-in GPU tests skipped**. Distribution and Maven GAME checks
passed. GAME/native suites and client runs were not repeated for metadata-only changes.

New build-only JAR: `DF3324CAE8C5D88011E7AD5D0B2B54E1D5030651B32150BB77D409AD47C14639`.
Core remains `2447D81A5F67821EC0FF7672B1F3665AA8DD61FF900B1C5A68FEDF47B88A733C`.
The previous 62 world/armor runs belong to `C3E4...03877`, not this metadata-only new artifact.
Evidence is in the existing external directory, `verified-exact-compatibility`, version-refresh
JSON and `exact-compatibility-validation.log`. An initial JSON reader used the host GBK default
and failed to decode the fetched UTF-8 response; explicit UTF-8 parsing recovered the same fetched
data. No dependency upgrade, client launch, Prism deployment, Git staging/commit/push or public
binary distribution occurred. Updating these temporary intervals requires a later explicit policy
change and matching integration evidence, not a claim that all other releases are intrinsically broken.
