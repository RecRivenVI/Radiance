# Radiance Phase 1B Technical Qualification

日期：2026-08-24（Asia/Shanghai）  
状态：`INVESTIGATION COMPLETE / TEST-PLATFORM PROFILE SCHEMA + S2 CLASSPATH WIRED / IMPLEMENTATION NOT STARTED`

## 1. Scope and decision state

唯一授权的首批业务 Target：

```text
1.21.1-neoforge
```

其余四个 node 仅为已 qualification 的工程 topology，不具有本轮业务授权：

```text
1.20.1-forge
26.1.2-neoforge
26.2-fabric
26.2-neoforge
```

本轮仅修改新 Radiance 的 project-profile adapter 与 `1.21.1-neoforge` S2 external runtime classpath；没有修改旧仓或 shared platform/reference，没有写 renderer/native 业务代码，没有启动 Minecraft，也没有创建 Git metadata 或 commit。MCVR 仅作为历史知识来源；本文不恢复其任何 identity。

## 2. Authority and evidence

### 2.1 Frozen authority

- `References/minecraft-references/minecraft-1.21.1`
- `References/minecraft-references/neoforge-21.1.248`
- `References/stonecutter-reference/Phase0/PROJECT_CONTRACT_V2.md`
- `References/stonecutter-reference/DEVELOPMENT_ACCEPTANCE_ENVIRONMENT_V2.md`

### 2.2 Exact-source completeness issue

冻结的 `minecraft-1.21.1/src` 包含 5,168 个 `net.minecraft` Java 文件，但不包含 `com.mojang.blaze3d`。因此它不能单独回答 `Window`、`RenderSystem`、`GlStateManager`、GL shader/program、FBO 和 GPU query 的完整调用面。

本轮使用当前 v2 qualification 生成的精确组合 source artifact 形成候选：

```text
versions/1.21.1-neoforge/build/moddev/artifacts/neoforge-21.1.248-sources.jar
SHA-256: 45C1C0B48A6C429C2A20B8ED5D19850AECA536D3F208F29264D56CCCE5B3D9AF
Java files: 6317
Blaze3D Java files: 75
```

其对应 merged bytecode artifact：

```text
neoforge-21.1.248-merged.jar
SHA-256: 530A3AEC0593886D8F00EDA8F4687DEC283A16C5731C9499C606191F2C035D42
```

此 artifact 是可复现的 exact-version evidence，但尚未被 governance 封存为 immutable exact reference。正式实现前必须补齐该 authority gap；项目线程不得自行修改 frozen reference。

## 3. A — Reachable OpenGL surface inventory

机器可读候选：

- `opengl-surface-inventory.json`
- SHA-256：`6A21D7ACB90824ADDDFDB195EBD1BD4EBBBF8AAE359E82365CCA879CE01A0F95`

精确 Minecraft shader/resource surface：

- `shader-resource-inventory.json`
- SHA-256：`907A4152E4B20B5D0D26981612C13236C71195ABA69093ADA1BB25B462A1704C`

### 3.1 Static result

| Surface | Unique candidate records |
| --- | ---: |
| Direct LWJGL OpenGL symbols | 106 |
| Direct GLFW symbols | 48 |
| `GlStateManager` wrapper symbols | 109 |
| `RenderSystem` wrapper symbols | 103 |
| Total candidate records | 366 |

这些是 symbol/owner/call-site 记录，不是语义覆盖率。

Direct OpenGL calls are concentrated in:

| Owner | Direct call occurrences | Phase |
| --- | ---: | --- |
| `GlStateManager` | 98 | all render/resource phases |
| `TimerQuery` | 10 | frame profiling/debug |
| `GlDebug` | 8 | renderer initialization/debug callback |
| `NeoForgeLoadingOverlay` | 3 | loading overlay |
| `Window` | 1 | OpenGL capability creation |

`jdeps` against the exact merged bytecode independently confirmed OpenGL class references only in `GlStateManager`, `GlDebug`, `Window`, `TimerQuery`, `RenderTarget` constant use, `ParticleEngine` constant use and `NeoForgeLoadingOverlay`. No reflective OpenGL invocation was found in the base Minecraft/NeoForge Java source.

### 3.2 Semantic groups

- Context/capabilities: `GL.createCapabilities`, `GL.getCapabilities`, GLFW context ownership.
- Fixed state: enable/disable, blend, depth, stencil, cull, scissor, viewport, color mask, logic op, polygon mode/offset and clears.
- Buffers/draw: buffer/VAO create-bind-map-upload-delete, vertex attributes, index types and `glDrawElements`.
- Textures: active unit, bindings, image/subimage/copy, parameters, mip queries, pixel-store and readback.
- Shader/program: source, compile, link, attributes, program lifetime, locations and all uniform shapes.
- Framebuffer/renderbuffer: create, attach, combined depth/stencil, bind, status, blit and destroy.
- Query/debug: timer query lifecycle, KHR/ARB debug callbacks and capability queries.
- Readback/pixel transfer: framebuffer readback, texture readback and legacy draw-pixels.
- Present: GLFW swap, VSync interval, poll/wait events and window lifecycle.

The exact base source contains no direct GL sync/fence API and no separate GL sampler-object API. This negative result applies only to Minecraft 1.21.1 + NeoForge 21.1.248 base sources, not arbitrary mods.

### 3.3 Startup call graph

```text
ModLauncher transformer discovery
  -> ModDirTransformerDiscoverer scans mods for early service providers
  -> ImmediateWindowHandler.load
     -> SERVICE-layer GraphicsBootstrapper(s)
     -> selected ImmediateWindowProvider.initialize
     -> provider early window/render tick
  -> mod discovery / coremods / mixin configs
  -> GAME layer accepted by provider
  -> Minecraft constructor
     -> Window constructor
        -> ImmediateWindowProvider.setupMinecraftWindow
        -> GLFW.glfwMakeContextCurrent
        -> GL.createCapabilities
     -> RenderSystem.initRenderer -> GLX._init -> GlDebug
     -> MainTarget -> texture/FBO creation
     -> resource reload, shaders, textures, renderers
     -> provider loadingOverlay
  -> frame loop
     -> world/UI draws -> RenderTarget blit
     -> RenderSystem.flipFrame -> glfwSwapBuffers
  -> resource/world teardown
  -> Minecraft.close -> Window.close -> GLFW destroy/terminate
```

### 3.4 Indirect and dynamic boundaries

The base reachable surface can be statically closed. The following are not part of that closed base set:

- third-party mods that call LWJGL/OpenGL directly;
- custom `RenderType`, `RenderLevelStageEvent`, shaders, FBOs and resource-pack shader JSON supplied at runtime;
- LWJGL JNI implementation details below the audited Java calls;
- dynamically loaded mod reflection or native libraries.

Before implementation, these must be converted from `UNKNOWN` to one of:

1. supported through the audited Blaze3D semantic boundary;
2. supported by a named adapter;
3. explicitly `UNSUPPORTED_BY_DESIGN` with fail-fast diagnostics;
4. proven `UNREACHABLE` for the authorized profile.

Silent discard is not an acceptable classification.

## 4. B — Translation coverage model

All 366 records are currently `coverageStatus=NOT_STARTED`. Their candidate translation classes are:

| Class | Candidate records | Meaning |
| --- | ---: | --- |
| `DIRECT_VULKAN_MAPPING` | 133 | explicit image/buffer/attachment/query/draw/present ownership |
| `EMULATED_STATE` | 92 | preserve Java-visible GL state while materializing Vulkan state explicitly |
| `SHADER_TRANSLATION_REQUIRED` | 90 | JSON/GLSL/program/uniform contract to SPIR-V and pipeline layouts |
| `CPU_FALLBACK` | 48 | window/input/capability/readback operations that are not GPU draw work |
| `UNSUPPORTED_BY_DESIGN` | 3 | OpenGL context/capability creation on a GLFW no-client-API window |

No base-core record is currently classified `UNREACHABLE`; reachability still has to be established per caller and startup/profile phase.

### 4.1 Required ownership conversion

| GL implicit concept | Required explicit owner |
| --- | --- |
| active texture/bound texture | per-command recording state plus descriptor binding table |
| cached enable/disable state | Java-observable state mirror plus Vulkan pipeline/dynamic-state key |
| program/uniform global state | translated shader module, pipeline layout and per-draw uniform allocation |
| framebuffer binding | named attachment/render-pass or dynamic-rendering graph |
| buffer/VAO binding | typed buffer slices, vertex layout and index/topology contract |
| GL object integer IDs | generation-safe resource handles with fallback and retirement rules |
| `glClear`/blit/readback | explicit image layout, barriers, command buffer and staging ownership |
| swap/present | swapchain image state, semaphores, present history and Streamline interception |
| render-thread assertions | one documented render/submission thread model plus queued work ownership |

### 4.2 Shader surface

The exact client resource artifact contains 200 shader files:

```text
56 core JSON
55 core vertex shaders
55 core fragment shaders
6 post JSON
9 post program JSON
6 post vertex shaders
9 post fragment shaders
4 includes
```

Base shader translation cannot be declared complete until all 56 core configurations and six post chains compile, link semantically, bind every attribute/uniform/sampler, and pass image comparison/user acceptance. Unknown third-party GLSL must not be accepted by a permissive partial translator.

## 5. C — NeoForge Early Window and core-mod qualification

### 5.1 Exact formal extension points

NeoForge/FML 4.0.43 provides two SERVICE-layer SPIs:

- `GraphicsBootstrapper`: executes before early provider selection/window creation.
- `ImmediateWindowProvider`: owns early initialization, window handoff, later loading overlay, periodic tick, game-layer reads, GL version reporting and early crash UI.

`TransformerDiscovererConstants.SERVICES` explicitly includes both. `ModDirTransformerDiscoverer` scans the mods directory before early initialization and places JARs exposing these services into the SERVICE layer.

Default earlydisplay artifact:

```text
net.neoforged.fancymodloader:earlydisplay:4.0.43
binary SHA-256: 76461588A12FD0D1F2A4B8D570676F919FC57EBA0F82066DA0AC0E884FC93242
manifest Git-Commit: 15c77cf6
provider: net.neoforged.fml.earlydisplay.DisplayWindow
license: LGPL-2.1-only source headers
```

Exact official `DisplayWindow.java` at commit `15c77cf6` was reviewed; it is explicitly OpenGL-specific, owns a GL context on its scheduler/main thread, compiles GL shaders, renders FML progress and hands that OpenGL context to Minecraft. It cannot be reused as a Vulkan provider.

### 5.2 Earliest legal Vulkan takeover point

The earliest supported takeover point is a custom `ImmediateWindowProvider.initialize`/`setupMinecraftWindow`, not a later Minecraft constructor patch. It can create the GLFW no-client-API window and native Vulkan instance/device/surface/swapchain before Minecraft renderer initialization.

However, exact Minecraft `Window` immediately performs:

```text
glfwMakeContextCurrent(window)
GL.createCapabilities()
RenderSystem.maxSupportedTextureSize()
```

Those calls must be replaced before `Window` is instantiated. A custom provider alone is insufficient.

### 5.3 Is a core mod required?

Current conclusion: **not proven necessary and not recommended as the default mechanism**.

- The early window itself has an official service SPI.
- Normal mod mixin configs are discovered before Minecraft `Window` is loaded.
- The old port already demonstrates that a target-specific Mixin can reach `Window` constructor behavior, although its late replace-and-reflect technique is not the desired new architecture.
- A narrow Mixin can replace the now-invalid context/capability calls and route later lifecycle operations without introducing an unrestricted bytecode transformer.

The exact FML order is now closed statically:

```text
early SERVICE discovery/provider initialization
-> ordinary mod scan
-> LoadingModList.addMixinConfigs()
-> GAME layer creation/ImmediateWindowProvider.updateModuleReads()
-> Minecraft/Window class loading
```

Therefore a normal Radiance mod Mixin is registered before `Window` is loaded, while the early provider is already active. This is the intended Mixin-first qualification boundary.

An `ICoreMod`/transformation service remains a fallback candidate only if a scratch qualification proves Mixin timing cannot cover the exact call sites. No evidence currently justifies a JavaScript coremod or a new transformation service.

### 5.4 Packaging qualification

A JAR exposing `ImmediateWindowProvider` is loaded into the SERVICE layer before ordinary mod loading. FML classifies normal MOD/GAMELIBRARY files separately for the GAME layer, while service candidates are consumed by ModLauncher before that scan. Reusing one physical JAR for both roles has no supported evidence and risks duplicate/module-layer exclusion.

An existing NeoForge 1.21.1 custom early-window implementation (`SimpleCustomEarlyLoading`) corroborates this model: its published source exposes `META-INF/services/...ImmediateWindowProvider` and a service implementation, but no normal `@Mod` entrypoint in the same authored source tree.

High-confidence packaging candidate:

```text
radiance-early-bootstrap service artifact (SERVICE layer)
  + Radiance mod artifact (GAME layer, Mixins/product lifecycle)
  + one Radiance-owned native/runtime package
```

All artifacts must remain Radiance-owned; this does not authorize an MCVR identity. A minimal startup probe is still required to prove the two-artifact handoff, but the one-JAR form is no longer a recommended architecture candidate.

### 5.5 Provider selection and fallback

Provider selection is controlled by `config/fml.toml` `earlyWindowProvider`. A `GraphicsBootstrapper` runs before selection and can technically call the public `FMLConfig.updateConfig`, but forcing/persisting the user's provider choice is a product-policy decision and requires a probe.

No documented first-run auto-selection API was found. The provider SPI explicitly forbids claiming the reserved `fmlearlywindow` name. Existing custom providers require an explicit `fml.toml` selection. M1 should first qualify the explicit-config path; automatic selection must be a separately approved behavior rather than an undocumented bootstrap side effect.

If a configured custom provider is absent, FML 4.0.43 falls to `DummyProvider`, not the normal `fmlearlywindow` provider. Therefore safe fallback must be explicit. For a full-replacement renderer, the recommended failure policy is a clear early crash/report rather than silently starting an unsupported OpenGL renderer.

Non-client launch targets receive `DummyProvider`. Radiance has no server role, so no server-side renderer classes or native libraries should load.

## 6. D — “VulkanUI” qualification

There is no standalone dependency or upstream library named `VulkanUI` in old Radiance/MCVR.

The historical term refers to MCVR's project-local:

```text
src/core/render/modules/ui_module.hpp
src/core/render/modules/ui_module.cpp
UIModule / UIModuleContext
```

It is custom GPL-3.0 code and is historical implementation knowledge only.

### 6.1 Historical coverage

- accepts Minecraft-produced vertex/index/uniform/texture data;
- creates per-swapchain color and depth/stencil images;
- manages a large combined-image-sampler descriptor array and dynamic uniform buffers;
- reproduces blend/depth/stencil/scissor/viewport/polygon state;
- handles UI raster, blur, Creeper/Spider/Invert post effects and later diagram targets;
- is recreated with the renderer pipeline when the swapchain changes.

### 6.2 What it does not own

- input dispatch or GLFW callbacks;
- Minecraft widget/layout/focus semantics;
- font shaping/rasterization or glyph selection;
- GUI scale/DPI policy;
- Early Window selection;
- device-loss recovery;
- frame pacing or present interception.

Input remains Minecraft/Java/GLFW. Fonts are rasterized by Minecraft/FreeType into glyph atlases. GUI scale and matrices are Minecraft state. The new Radiance UI subsystem must rederive these boundaries and cannot copy the GPL historical module into the LGPL project without a separate licensing decision.

Dear ImGui/Nuklear samples present in third-party SDK trees are not product dependencies and do not translate Minecraft UI semantics. Dear ImGui may be considered later for a development-only diagnostic overlay, not as the Minecraft UI implementation.

## 7. E — NVIDIA feature qualification

Machine-readable qualification: `nvidia-feature-qualification.json`  
SHA-256: `D70CA4F3B99781B40323B70A493579F00618FF4CF13F3642431A5BBD28821450`

### 7.1 Current formal baselines

| Package | Formal version | Exact identity |
| --- | --- | --- |
| Streamline | `2.12.0` | tag `v2.12.0`, commit `e8aaa6eaac968711fb62473d4ae8256dde20919b` |
| Streamline release ZIP | `streamline-sdk-v2.12.0.zip` | 231,958,617 bytes; SHA-256 `F5C0A3D870707DDDC3570FB4BCD3655CF48A8A68C3A9D342910CFA21B77DCF48` |
| DLSS SDK repository | `310.7.0` | tag `v310.7.0`, commit `a291cc7d2cc642a51566f3dfd5376f635cd1b284` |

Official sources are the immutable GitHub release/tag URLs recorded in the machine-readable qualification. No floating `latest` URL is an implementation input.

### 7.2 Version alignment

Streamline 2.12.0 bundles signed NGX feature DLLs version **310.7.0**. Their exact size and SHA-256 match the latest formal `NVIDIA/DLSS` v310.7.0 binaries:

| Binary | Bytes | SHA-256 |
| --- | ---: | --- |
| `nvngx_dlss.dll` | 58,977,904 | `BE6E434A94CA32499515EB62CA0E6C274526055D568D0426E4C652DCDFB6EE6E` |
| `nvngx_dlssg.dll` | 7,519,856 | `135EAF0733C1E37381A8C28ABCF7A862404A54132B81787C04E35D09EFC5E36F` |
| `nvngx_dlssd.dll` | 40,946,800 | `F4E97624F70FBB769ACB11EBD751B512ECC9463D4BD6AEF04896D3956E6084A0` |

The earlier 2.11.1/310.7.0 mismatch is therefore **RESOLVED**. The only qualified candidate is the atomic 2.12.0 release set; manual substitution or mixed-package staging remains prohibited.

### 7.3 Feature requirements

| Feature | Required inputs/lifecycle | Current first-release position |
| --- | --- | --- |
| DLSS Super Resolution | render-resolution HDR color, output color, depth, dense motion vectors; optional/preferred exposure; jitter/matrices and frame token; command state restoration | first-release required candidate |
| DLSS Frame Generation 2x | depth, dense motion vectors, HUD-less color, optional/preferred UI color+alpha, final backbuffer at present; HWS; Reflex; intercepted acquire/present; `numFramesToGenerate=1` and runtime max check | first-release priority, RTX 40 test only |
| DLSS Ray Reconstruction | noisy ray color, diffuse/specular albedo, normals, roughness, depth, motion vectors, specular motion or hit distance; optional transparency/SSS/DOF guides; full-resolution output | deferred; independent mode overriding ordinary DLSS SR |
| Reflex | `slReflexSleep`, options even when Off, plus separate PCL markers; precise simulation/render/present frame-token ownership | first-release required |

### 7.4 Vulkan-specific constraints

- Streamline general Windows baseline: Vulkan 1.2+, Win10 20H1 for FG, NVIDIA driver 512.15+.
- Feature support must be obtained through `slGetFeatureRequirements` and `slIsFeatureSupported`; static extension lists are not sufficient.
- Vulkan native optical flow uses `VK_NV_optical_flow`, requires Vulkan 1.1 plus synchronization/format dependencies; Streamline documents Windows driver 527.64+ and recommends Vulkan 1.3. Interop optical flow is the fallback.
- Manual hooking requires Streamline proxies for swapchain create/destroy/images, acquire and present; instance/device proxies are optional only if the host manually merges all feature requirements before creation and calls `slSetVulkanInfo`.
- FG consumes the present semaphore and signals the acquire semaphore according to the documented contract. Tagged resources cannot be recycled before Streamline finishes with them.
- Vulkan VSync with Frame Generation remains explicitly unsupported in Streamline 2.12.0; runtime `bIsVsyncSupportAvailable` does not override the documented Vulkan exclusion.
- `VK_NV_low_latency2` is the current Vulkan Reflex extension family; it depends on Vulkan 1.2 or timeline semaphores and present IDs. Streamline bundles signed `NvLowLatencyVk.dll` and abstracts this path.
- Swapchain recreation must retire present semaphores and old swapchains only after presentation ownership is released; this becomes more important with Streamline present interception.

### 7.5 Licensing and redistribution

Streamline source is MIT, but bundled NGX/DLSS binaries are governed by the NVIDIA RTX SDKs License. That license includes acceptance, protective downstream terms, NVIDIA-only interoperability for DLSS/NGX, commercial release notification and trademark/attribution obligations. It also restricts use that would make the SDK subject to an open-source license.

Consequences:

- source-level Streamline integration code can be evaluated under MIT;
- no NVIDIA binary is authorized for commit or release by this report;
- dynamic separation from LGPL Radiance reduces, but does not by itself settle, license compatibility;
- user/legal acceptance and a packaging/EULA decision are required before SDK use or redistribution;
- no credential/application ID was obtained or exposed; by explicit user decision it will not be requested during development and is a `PRE_RELEASE_REQUIREMENT`, not a current implementation blocker;
- public release binaries must be production, NVIDIA-signed, signature-checked before loading and pinned by SHA-256.

The downloaded public qualification ZIP and selected binaries were retained only temporarily in task Scratch for hashing and must not become a project dependency.

## 8. F — Architecture candidates

| Candidate | Early Window | Coverage | Performance | Mod compatibility | DLSS/Reflex | Risk |
| --- | --- | --- | --- | --- | --- | --- |
| A. Domain-level full renderer replacement | official custom provider; replace Level/Game/UI renderers | must independently port every Minecraft/NeoForge semantic | best opportunity | poor for mods relying on Blaze3D/custom RenderTypes | excellent ownership | very high rewrite and version cost |
| B. GL semantic interception + explicit Vulkan translation | official provider plus narrow Window/RenderSystem mixins | directly aligned with the 366-record inventory; domain adapters only where GL stream lacks identity | good if state/pipeline cache is disciplined | best for mods using normal Blaze3D; direct LWJGL remains unsupported | strong; owns swapchain/present | shader/FBO/state completeness is difficult |
| C. Staged OpenGL-to-Vulkan transition | default OpenGL early display, later no-API replacement | easiest incremental debugging, but has two graphics lifecycles | worst startup complexity | transitional only | present/swapchain ownership conflicts with FG | does not satisfy final first-release scope |

Recommended direction for an implementation experiment is **B with domain-specific specialization**:

```text
official early provider owns no-API window + Vulkan/Streamline lifecycle
-> exact Java semantic interception preserves RenderSystem/GlStateManager state
-> explicit Vulkan UI/resource/draw translation
-> domain-level world renderer paths may replace expensive or identity-poor GL streams
```

This is not a frozen JNI/C++/CMake architecture. Native code must be Radiance-owned from the first commit.

Candidate C is acceptable only as a disposable diagnostic bridge; it must not become the shipping lifecycle because DLSS-G requires unambiguous acquire/present/swapchain ownership.

## 9. G — Acceptance and risk matrix

| Gate | Current status | Required evidence |
| --- | --- | --- |
| `STATIC_SOURCE_COVERAGE` | PARTIAL PASS | candidate complete; governance must seal Blaze3D combined source and run bytecode/reference drift gates |
| `AUTOMATED_NATIVE_TEST` | NOT STARTED | state-machine, handles, barriers, resource lifetime, shader compiler, swapchain and Streamline mock tests |
| `CTEST` | NOT STARTED | Radiance-owned native contracts; historical MCVR CTest is not transferable acceptance |
| `HEADLESS/STARTUP_PROBE` | NOT STARTED | early service discovery, no-API Window handoff, clean fail path, shutdown and no server load |
| `USER_CLIENT_ACCEPTANCE` | PENDING | user-owned live Minecraft behavior and visuals |
| `GPU/HARDWARE_BLOCKED` | BLOCKED | RTX 40 2x FG, DLSS SR, Reflex and later RR real-device evidence |
| `DRIVER_BLOCKED` | BLOCKED | record actual driver and runtime feature requirements; do not infer from labels |
| `PRE_RELEASE_NVIDIA_IDENTITY` | DEFERRED | application/project identity, production signing, notification and redistribution confirmation are release gates, not M1/development blockers |

Future authorized Radiance client tests must use:

```text
:1.21.1-neoforge:prepareProjectProfilePrimaryS1 -> :1.21.1-neoforge:runClientPrimaryS1
or
:1.21.1-neoforge:prepareProjectProfilePrimaryS2 -> :1.21.1-neoforge:runClientPrimaryS2
--width 2560 --height 1440
gameDirectory = exact shared record field
```

After launch, the agent reports PID and log path, keeps the process running and pauses. User changes to windows, worlds, options, configs, saves, screenshots and connection state are expected manual-test state and must not be reset, deleted or used as a reason to terminate the client. Visual/interaction results remain user-owned.

## 10. Confirmed feasible items

1. The base Minecraft/NeoForge OpenGL Java surface is finite and can be statically enumerated.
2. NeoForge has an official early service mechanism capable of owning the first window.
3. A no-client-API GLFW window can retain Minecraft's GLFW input/window callbacks while Vulkan owns presentation.
4. Streamline 2.12.0 formally supports Vulkan DLSS SR, FG, RR and Reflex integration paths; Vulkan FG + VSync remains excluded.
5. RTX 40 2x maps cleanly to one generated frame and a runtime support check.
6. Minecraft UI can be translated without introducing an unrelated immediate-mode UI library.

## 11. Required experiments

1. Two-artifact SERVICE/GAME layer packaging, provider selection and shared native ownership.
2. Mixin execution for suppressing `glfwMakeContextCurrent`, `GL.createCapabilities`, GLX init and MainTarget creation without coremod; static registration timing is qualified, runtime application remains untested.
3. Early provider clean failure/crash UI and provider-conflict behavior.
4. Exact 56 core shader and six post-chain translation corpus.
5. Streamline manual-hooking instance/device/swapchain extension/queue requirement capture on the RTX 40 machine.
6. Vulkan FG present/acquire synchronization, resource lifetime, resize/fullscreen and VSync-off behavior.
7. Reflex marker placement across Minecraft tick/simulation/render/present threads.

## 12. Blockers before business implementation

1. Frozen exact reference lacks Blaze3D source; governance must seal the reproducible combined source or an equivalent immutable supplement.
2. The recommended two-artifact SERVICE/GAME packaging has not completed a startup probe.
3. Third-party direct-OpenGL and runtime shader support beyond M1 must be converted from external `UNKNOWN` to named support or fail-fast boundaries; M1 explicitly fails fast.

## 13. Recommended first minimal implementation milestone

After the blockers above are resolved and implementation is explicitly authorized:

```text
M1 — Radiance Early Vulkan Lifecycle Probe
```

Scope:

1. official `GraphicsBootstrapper`/`ImmediateWindowProvider` discovery;
2. GLFW no-client-API window at the exact NeoForge handoff point;
3. Radiance-owned Vulkan instance/device/surface/swapchain;
4. narrow Window/Minecraft Mixin that prevents OpenGL context/capability creation;
5. clear-color frames only, resize/fullscreen/shutdown and deterministic failure report;
6. optional Streamline initialization with no features only after license approval;
7. no world renderer, no UI translator, no DLSS feature evaluation.

This milestone proves lifecycle and packaging without prematurely freezing the renderer ABI.

## 14. User decisions/approvals required

1. Approve governance work to complete the frozen exact Blaze3D reference.
2. Approve or reject the recommended separate Radiance early-bootstrap artifact.
3. Approve Mixin-first; a coremod fallback should be reconsidered only if the startup probe proves a concrete Mixin timing limitation.
4. **RESOLVED:** development use under the applicable NVIDIA terms is approved; account-bound application/project identity is intentionally deferred to pre-release.
5. **RESOLVED:** the atomic Streamline 2.12.0 + bundled NGX 310.7.0 package is approved; mixed-package experiments remain prohibited.
6. Approve the explicit unsupported policy for third-party direct LWJGL/OpenGL calls.
7. Choose the native failure policy: fail-fast client crash report, feature fallback, or renderer fallback boundaries.
8. **RESOLVED:** M1 implementation is explicitly authorized for `1.21.1-neoforge` only.

Phase 1B stops here.

## 15. Shared test-platform v2.1 consumption

The prior shared infrastructure blocker was resolved by governance through test-platform v2.1 requalification. Radiance consumes the frozen shared `Stage-ProjectProfile.ps1` and `Verify-ProjectProfile.ps1` adapters and does not create a project-local auxiliary-mod registry or support matrix.

Actual CustomSkinLoader/Zume class loading still has no client runtime evidence and remains `USER_ACCEPTANCE=PENDING`.

### 15.1 Radiance profile/role consumption matrix

Authorized business node:

```text
1.21.1-neoforge
```

Radiance maintains only the `client` role.

| Profile | Project-local role/task | Runtime content | Auxiliary policy | Current status |
| --- | --- | --- | --- | --- |
| S1 | `client` / `:1.21.1-neoforge:runClientPrimaryS1` | Radiance + required dependencies | CustomSkinLoader forbidden; Zume forbidden | adapter/staging verified; runtime pending |
| S2 | `client` / `:1.21.1-neoforge:runClientPrimaryS2` | Radiance + required dependencies + CustomSkinLoader `15.0.1-Universal` + Zume `1.2.2` | both supported on this exact target; consumed only through shared platform v2.1 | adapter/staging verified; runtime pending |
| M1 | not maintained by Radiance | no project-local `client-multiplayer` role | do not synthesize a secondary role or auxiliary stack | not authorized |
| M2 | not maintained by Radiance | no project-local server or secondary-client role | dedicated server must never receive CSL/Zume | not authorized |

Every future Radiance client launch uses official arguments equivalent to:

```text
--width 2560 --height 1440
```

After launch the agent reports Target, role, PID and log path, leaves the client running and waits. User changes to window state, worlds, options, config, saves, screenshots and connection state are expected manual-test behavior and must not be reset or cleaned. Visual and interaction results remain `USER_ACCEPTANCE=PENDING` until the user reports them.

No current MoBends process or runtime directory is in Radiance scope.

### 15.2 Adapter verification

- project-local adapter exists only on `1.21.1-neoforge`;
- both run tasks depend on shared Stage then Verify processing;
- the adapter reads shared `modsDirectory` and `requiredJvmArg` record fields directly, with no derived-path fallback;
- S1 returned an empty mods directory, no Zume state path and no JVM override;
- S2 returned only CSL + Zume and its unique `-Dzume.configPathOverride`;
- ModDevGradle's generated legacy runtime classpath contained 107 entries for S1 with zero auxiliary JARs, and 109 entries for S2 with exactly the staged CSL and Zume JARs;
- both profiles returned PRIMARY `RecRivenVI` and `2560x1440`;
- preparation tasks completed twice; the second run reused configuration cache;
- `buildAndCollect` completed twice; the second run reused configuration cache;
- S1/S2 run task graphs resolved in offline dry-run twice; the second run reused configuration cache;
- no client was launched and no Git commit was created.
