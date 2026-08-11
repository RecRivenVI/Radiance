# Radiance migration decision ledger

Status: FROZEN FOR CURRENT MIGRATION  
Authority: current user specification and confirmed project decisions  
Purpose: non-product, non-committed migration gate  
Updated: 2026-08-24

## Identity

- Repository/root: `Radiance`.
- Brand and mod ID: `Radiance` / `radiance`.
- Group: `io.github.recrivenvi`.
- Package root: `io.github.recrivenvi.radiance`.
- MCVR product, module, package, task, artifact, ABI and runtime identity are prohibited.

## Business target

- The only authorized business target is `1.21.1-neoforge`.
- The five-node Stonecutter topology is engineering governance, not five-node business implementation.
- `1.20.1-forge`, `26.1.2-neoforge`, `26.2-fabric` and `26.2-neoforge` remain business-unimplemented.

## Renderer and interception

- `RADIANCE_RENDERER=PURE_VULKAN`.
- `WINDOW_CLIENT_API=GLFW_NO_API` for the entire window lifetime.
- Real OpenGL context, GL capabilities, GL/GLX initialization and OpenGL fallback are forbidden.
- Minecraft/NeoForge OpenGL calls are intercepted before a real driver/context boundary.
- Implemented semantics become Vulkan execution or explicitly emulated state.
- Unimplemented semantics are consumed as `SWALLOWED_NO_OP` with bounded diagnostics: no fail-fast, no real GL, no fallback.
- A qualified scope is complete only after its `SWALLOWED_NO_OP` set is eliminated.
- Minecraft/GLFW retain authoritative window, input and focus control.

## NeoForge Early Window

- The official NeoForge Early Window remains enabled.
- Official `fmlearlywindow`/`DisplayWindow` resources, layout, progress, messages and control flow remain authoritative.
- Radiance intercepts the official GL/GLFW boundary and translates it natively to Vulkan.
- Deleting, disabling, skipping or replacing the official window with custom Radiance UI is prohibited.
- `M3_USER_ACCEPTANCE=PASS`; the M3 official visual and pure-Vulkan regression is permanent.

## Artifact architecture

- Separate Radiance SERVICE-layer early-bootstrap and GAME-layer mod artifacts.
- Official Early Window SPI plus narrow hooks/Mixins.
- Generated JNI, shaders and native binaries live only in `build`, `Scratches`, `Artifacts` or staging, never authored `src`.
- Diagnostics and failure classification are deterministic and bounded.
- Vulkan resource/device/surface/swapchain lifecycle includes resize, device-loss handling and orderly shutdown.

## GUI and world

- Minecraft/NeoForge GUI stays on its official Java control flow and is rendered by the native Vulkan translator.
- No independent product UI framework, Java `BufferedImage`, Java rasterizer or native CPU rasterizer may substitute for GUI rendering.
- The current M4 translator is `REJECTED_IMPLEMENTATION`: user observed about 1 FPS and widespread artifacts.
- `M4_USER_ACCEPTANCE=FAIL`; `M4_COMPLETE=false`; call coverage is not semantic or performance proof.
- World translation starts only after GUI acceptance. World rendering is not implemented and must not be claimed.
- Exact Minecraft 1.21.1 and NeoForge 21.1.248 sources define the platform call surface, not product behavior.

## NVIDIA feature family

- Streamline atomic package: `2.12.0`.
- DLSS/NGX resources: `310.7.0`.
- First-release intent: complete DLSS Super Resolution, DLSS Frame Generation and NVIDIA Reflex.
- RTX 40 Frame Generation development scope is 2x only (`numFramesToGenerate=1`).
- DLSS Ray Reconstruction is deferred and is an alternative mode replacing ordinary DLSS in that mode.
- Vulkan Frame Generation and VSync limitations follow the qualified current SDK contract.
- Application/project identity, signing, production redistribution eligibility and release notification are `PRE_RELEASE_REQUIREMENT`, not current development blockers.
- No application ID may be invented, borrowed or committed.

## Reference authority

1. Current user specification and frozen product decisions.
2. Current read-only RecRivenVI Radiance port at `D:\Workspaces\Repositories\GitHub\RecRivenVI\Radiance`.
3. Exact platform/dependency sources and SDKs for API and integration evidence only.
4. New reproducible implementation experiments.

Direct product upstream, MCVR and other historical native repositories are prohibited references. Port gaps remain `UNKNOWN` until the user decides or a permitted experiment proves them.

## Acceptance, runtime ownership and Git

- The Agent never operates Minecraft GUI, keyboard or mouse.
- Visual and interaction acceptance belongs to the USER.
- Every launch handoff includes `EXPECTED_VISUAL` and `USER_ACCEPTANCE_STEPS` with PID, game directory and logs.
- An incorrect Radiance instance may be closed only after command line, identity, target/loader, game directory, profile/role, logs and launch record all match.
- GLASS, MoBends, unrelated Java processes and Gradle daemons are never touched.
- No `git init`, commit, push, tag or remote mutation.

## Migration order and gates

1. `N0`: native core/lifecycle, frame scheduler, frames-in-flight, command/sync, resize/device-loss/shutdown, diagnostics and CTest.
2. `N1`: GL object/state/resources, shader/program/uniform, framebuffer/blit, draw batching, caches and incremental uploads.
3. `N2`: accepted official Early Window moved onto the migrated core, with no CPU raster.
4. `N3`: Minecraft/NeoForge GUI through the native Vulkan translator, stopping before world rendering.
5. `N4`: world architecture/inventory only until GUI acceptance.

Mandatory gates: ledger validation; prohibited-identity scan; target isolation; port-to-new mapping; native unit/CTest; Vulkan validation; zero GL context/real GL/fallback; zero Java/native CPU raster; frame trace; deterministic primitive pixel fixtures; M3 official Early Window regression; GUI fidelity; build/package/configuration-cache; generated/authored separation; zero commits.

