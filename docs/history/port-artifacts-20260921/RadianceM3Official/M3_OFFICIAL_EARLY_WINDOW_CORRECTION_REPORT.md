# Radiance M3 Official Early Window Correction

Status date: 2026-08-24

## Status

- `PURE_VULKAN_CALL_CLOSURE = PASS`
- `OFFICIAL_DISPLAY_WINDOW_CONTROL_FLOW = PASS`
- `NATIVE_INTERCEPTION_TRANSLATION = PASS`
- `CUSTOM_RADIANCE_EARLY_UI = REMOVED`
- `NEOFORGE_EARLY_WINDOW_VISUAL_FIDELITY = PASS`
- `M3_USER_ACCEPTANCE = PASS`
- `M3_COMPLETE = true`
- `COMMITS_CREATED = 0`

The user confirmed that the visible output matches the NeoForge 21.1.248 official Early Window and accepted native interception plus Vulkan output.

## Why the rejected implementation was custom

The rejected probe selected Radiance's own `ImmediateWindowProvider`, bypassed the official `fmlearlywindow` provider and `DisplayWindow`, and drew a Radiance-branded panel/pixel-font/progress layout in native code. That circularly made the official provider appear unreachable. The custom title, panel, font rasterizer, progress bar, native fields, and JNI entrypoint have now been deleted; authored-tree matches for the rejected UI are zero.

## Corrected control flow

1. Radiance `GraphicsBootstrapper` performs the minimal early bootstrap and creates one hidden `GLFW_NO_API` window plus the Radiance Vulkan instance/device/surface/swapchain.
2. NeoForge still selects `fmlearlywindow` and instantiates the official `net.neoforged.fml.earlydisplay.DisplayWindow`.
3. The exact official earlydisplay 4.0.43 module remains in the ModLauncher legacy/module layer. The ordinary application classpath contains neither the original nor patched earlydisplay JAR, preventing split/duplicate modules.
4. The derived official JAR preserves official classes, provider descriptor, resources, layout, font, progress/message sources, resize callbacks, and handoff. Its only functional rewrites redirect exact LWJGL GL/critical GLFW call owners plus resource/progress diagnostics to Radiance bridges.
5. Java bridge code owns no framebuffer, texture, buffer, VAO, uniform, rasterizer, or frame submission state. It forwards the exact call boundary to the Radiance SERVICE endpoint.
6. C++ native code owns GL semantic state, deterministic compatibility returns, texture/buffer/VAO/uniform state, quad rasterization, framebuffer composition, Vulkan staging/image upload, acquire-submit-present, resize, and destruction.
7. Minecraft is held immediately before `MainTarget` construction. The last official Early Window frame remains presented; the old M2 clear frame is disabled in M3.

## Static gates

- Official earlydisplay classes: 26
- Official GL owner invocations rewritten: 118
- Remaining direct `GL32C` invocations: 0
- Remaining direct `GL.createCapabilities` invocations: 0
- Critical GLFW bridge invocations: 24
- Provider: `net.neoforged.fml.earlydisplay.DisplayWindow`
- Patched official JAR SHA-256: `D682A08BCE85AD7C5B9CCD875D0C0528CEB1C27F925EFD9C2F248AA99F3B49BC`
- Native DLL SHA-256: `3DC2937DA580F13B739D2F5CF957380A6478E8BE489B3C1CD403D26E0F6C39B6`
- SERVICE JAR SHA-256: `C2E3FBEF4A63ECD59DAB5A3901628F852F47D93FB9D011BC622FF83F022BD4E3`
- GAME JAR SHA-256: `4861D8DA9BA192060A314F2AB35055C7852D4C185EB9EBB5C3CA138AD7229F8D`
- Java drawing/raster state matches: 0
- Rejected custom UI authored matches: 0

Official resource hashes are unchanged from the exact dependency:

- `Monocraft.ttf`: `B61EE3256F449E96140F54515819540840EF0CADE82711EBA359A5864C779076`
- `fox_running.png`: `D9D8679957F82DA3F7077FC9CEB403B7FDFD45E7EB25088956EA6F0B45654D7D`
- `neoforged_icon.png`: `A62E5D42835B557034934DB7FECC4C854A631D859360253AE954753528D1DBB0`
- optional `squirrel.png`: `C9A6357CBE873FE8F3623F4A761C34B683F8A557324F53FDD783A8B0A20630DC`

## Consecutive runtime closure

Final A and Final B both reached the MainTarget-preconstruction hold with:

- actual runtime symbols: 51
- events: 544
- `EMULATED_STATE`: 380
- `TRANSLATED_TO_VULKAN`: 107
- `VULKAN_LIFECYCLE_SUBSTITUTION`: 57
- `SWALLOWED_NO_OP`: 0
- `UNCLASSIFIED`: 0
- `REAL_OGL_EXECUTION`: 0
- real OpenGL context/capabilities: 0
- OpenGL fallback paths: 0
- M2 overwrite frame: 0
- official native frames: 6
- official `Monocraft.ttf`, `fox_running.png`, and `neoforged_icon.png` consumed
- official messages and current-progress sources consumed
- Final A last frame: FNV-1a `1030434920938294284`, 8620 non-background pixels
- Final B last frame: FNV-1a `7858375109876744220`, 8556 non-background pixels

The currently running user-acceptance instance independently reached the same hold and has a non-solid official frame. Its exact PID and paths are reported in the handoff, not frozen into this report.

## Build gates

- CTest: 8/8 PASS
- five-node `buildAndCollect`: PASS
- configuration cache: REUSED
- PRIMARY S1/S2 run-task dry resolution: PASS
- only `1.21.1-neoforge` contains M3 product implementation
- no client automation or input was used
- no commit or Git metadata was created

Streamline/DLSS/DLSSFG/DLSSRR/Reflex remain outside M3. NVIDIA application identity remains a pre-release requirement, not a current M3 blocker.
