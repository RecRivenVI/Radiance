# Current Radiance port migration inventory

Source authority: read-only `D:\Workspaces\Repositories\GitHub\RecRivenVI\Radiance`  
Destination: `D:\Workspaces\Repositories\Local\Radiance`  
Mode: product port migration plus second implementation re-derivation  
Native-source boundary: the permitted port contains Java/JNI contracts but no authored C/C++ source. Native implementation details not evidenced there remain `UNKNOWN`; prohibited MCVR sources are not consulted.

Status vocabulary: `PORT_PRESENT`, `PORT_PROVEN`, `PORT_BUG_TRAP`, `MIGRATE_AS_IS`, `RE-DERIVE`, `REJECT`, `ALREADY_NEW`, `MISSING_NEW`, `TESTED_NEW`, `USER_ACCEPTED`.

| Area | Port evidence | Status | Migration disposition |
|---|---|---|---|
| Java integration | `RadianceClient`, `MinecraftClientMixins`, loader platform classes, broad Mixin suite | PORT_PRESENT, RE-DERIVE | Map exact 1.21.1/NeoForge hooks into current package/one-root governance; do not copy old package identity. |
| JNI boundary | 100 native-declaration lines; `RendererProxy`, `PipelineStateProxy`, `BufferProxy`, `TextureProxy`, `ShaderProxy`, `DrawCommandProxy`, `WindowProxy` | PORT_PRESENT, PORT_PROVEN, RE-DERIVE | Use as behavioral contract inventory; replace chatty per-call JNI with frame command packets where safe. No ABI freeze yet. |
| Native source modules | No C/C++ source in permitted port | UNKNOWN, RE-DERIVE, TESTED_NEW | N0/N1 are being re-derived in the new Radiance native core. Do not read MCVR. |
| GL interception surface | `GlStateManagerMixins`, `GlBooleanStateMixins`, `RenderSystemMixins`, `GLXMixins`, shader/texture/buffer/window mixins | PORT_PRESENT, PORT_PROVEN, RE-DERIVE | Preserve Mojang state-cache control flow while replacing terminal LWJGL execution; adapt against exact platform inventory. |
| GL state emulator | Explicit viewport/scissor/blend/depth/stencil/raster/clear JNI mapping in `PipelineStateProxy` | PORT_PRESENT, PORT_PROVEN, RE-DERIVE, TESTED_NEW | Native `GlStateSnapshot`, dirty bits and deterministic pipeline keys now exist; exact JNI integration remains N1 work. |
| Shader translation | `ShaderTranslator` rewrites GLSL inputs/outputs, uniforms and projection; `ShaderRegistry` caches by program/format/mode and emits generated shader source | PORT_PRESENT, PORT_BUG_TRAP, RE-DERIVE | Retain translation concepts and tests, but qualify parser/ABI/layout and generate/cache SPIR-V outside authored src. Regex-only rewriting is not sufficient proof. |
| Buffer/index | `BufferProxy` allocates handles, queues uploads, builds sequential indices and exposes upload flush | PORT_PRESENT, PORT_PROVEN, RE-DERIVE | Adopt handle/generation model, persistent staging and dirty incremental uploads. Never submit/wait per GL call. |
| Texture/sampler | `TextureProxy` tracks 32 units, stable IDs, prepare/filter/clamp, queued subregion/mip upload and readback | PORT_PRESENT, PORT_PROVEN, RE-DERIVE | Preserve target/unit semantics; add generation-safe ownership, sampler cache, true mip/sRGB/format qualification and bounded readback. |
| Framebuffer/blit | RenderTarget and OpenGL framebuffer compatibility mixins; clear commands; screenshot/readback paths | PORT_PRESENT, RE-DERIVE | Build native color/depth/stencil attachments and explicit blit/orientation semantics. Do not use CPU backing as renderer. |
| Command submission | `RendererProxy.acquireContext/submitCommand/present`; `BufferProxy.performQueuedUpload` | PORT_PRESENT, PORT_PROVEN, RE-DERIVE, TESTED_NEW | N0 scheduler and N1 frame command stream are connected to acquire/record/submit/present; one submit per lifecycle frame, no normal-frame device idle. |
| Pipeline cache | `ShaderRegistry` Java cache; native pipeline behavior not present in permitted port | PORT_PRESENT, MISSING_NEW, RE-DERIVE | Native graphics-pipeline cache keyed by shader, vertex layout, target formats and fixed-function state. |
| Descriptor/resource ownership | Sampler IDs embedded in uniform packet; release/flush hooks; native ownership absent | PORT_PRESENT, MISSING_NEW, RE-DERIVE | Implement descriptor allocator/cache, deferred destruction by completed frame and typed generation handles. |
| Window/surface/swapchain | `WindowMixins`, `WindowProxy`; GLFW_NO_API intent and resize callback | PORT_PRESENT, PORT_PROVEN, RE-DERIVE, ALREADY_NEW, TESTED_NEW | Current M1/M3 lifecycle and user-accepted resize are the base. Reject port behavior that captures a real Early Window GL context. |
| Official Early Window | Port has a replacement/handoff history, while current project M3 natively translates official DisplayWindow | PORT_BUG_TRAP, REJECT, ALREADY_NEW, USER_ACCEPTED | Keep current official Early Window control flow and M3 visual gate; never migrate replacement UI/context capture. |
| UI subsystem | Port has custom Radiance configuration screens and generic draw translation; these are not a substitute for vanilla GUI | PORT_PRESENT, PORT_BUG_TRAP, RE-DERIVE | Reuse only GL/shader/resource semantics. M4 must render official Minecraft/NeoForge GUI via Vulkan. |
| World renderer | Extensive chunk/entity/particle/PBR proxy and Mixin surface | PORT_PRESENT, RE-DERIVE | Inventory only until GUI user acceptance; no execution or completion claim in N0–N3. |
| Native staging/manifest | Port Gradle has JNI generation, native build/install, CTest, manifest/hash/staging/package verification | PORT_PRESENT, PORT_PROVEN, RE-DERIVE | Keep the workflow principles but make native subsystem Radiance-owned and current-node isolated. |
| ABI | Java native declarations provide a de facto surface; native ABI source absent | PORT_PRESENT, PORT_BUG_TRAP, RE-DERIVE | Version internal command protocol; do not freeze formal ABI during migration. |
| Tests/CTest | Java shader/resource/render contract tests plus Gradle native/CTest gates | PORT_PRESENT, PORT_PROVEN, RE-DERIVE | Port source-backed unit ideas; add N0 scheduler/handle/diagnostic and GPU primitive fixtures. |
| Failure handling | `RendererProxy` maps VkResult/device lost and exposes last native failure; reload transaction hooks | PORT_PRESENT, PORT_PROVEN, RE-DERIVE, TESTED_NEW | Bounded diagnostic ring, deterministic failure classes and partial cleanup are present; runtime device-loss acceptance remains pending. |
| Known traps | Real GL Early Window capture, generic constants, regex-only shader assumptions, synchronous JNI, old package/MCVR boundary | PORT_BUG_TRAP, REJECT | Must fail migration validator or remain isolated historical evidence. |
| Rejected new M4 translator | CPU triangles/lines/quads, full-frame upload, per-event file open, reflection/JNI per semantic | REJECT, MISSING_NEW | Retain only logs/report. Remove from packaged runtime before any next GUI launch. |

## Initial port-to-new function map

| Port contract | New destination | State |
|---|---|---|
| `RendererProxy.initRendererNative` / close/failure description | Radiance N0 `NativeRuntime` lifecycle | RE-DERIVE, TESTED_NEW |
| acquire / submit / present | N0 `FrameScheduler` begin/record/end/present | TESTED_NEW |
| `WindowProxy.onFramebufferSizeChanged` | N0 extent state and swapchain generation | ALREADY_NEW, RE-DERIVE |
| `PipelineStateProxy.*` | N1 native `GlStateSnapshot` + pipeline key | PARTIAL_NEW, TESTED_NEW |
| `BufferProxy.*` | N1 generation handles + dirty upload arena | MISSING_NEW |
| `TextureProxy.*` | N1 image/sampler tables + dirty region queue | MISSING_NEW |
| `ShaderProxy.registerShader/draw` | N1 shader module/pipeline/descriptor cache + frame draw packet | MISSING_NEW |
| `DrawCommandProxy.Overlay.glClear` | N1 attachment clear command | PARTIAL_NEW, RE-DERIVE, TESTED_NEW |

## Current implementation checkpoint

- N0 generation-safe resource handles: `TESTED_NEW`.
- N0 two-slot frame scheduler integrated with Vulkan acquire/record/submit/present: `TESTED_NEW`.
- N0 bounded diagnostics and Vulkan validation callback: `TESTED_NEW` statically; real validation output remains runtime pending.
- N0 official-frame staging: persistent per-frame mapping, incremental dirty upload, no upload-time global device idle: `TESTED_NEW`.
- N1 GL state snapshot, pipeline-key inputs, frame command stream and aligned staging arena: `TESTED_NEW` as deterministic native units; not yet GUI-qualified.
- Rejected M4 runtime: disabled and excluded from launch (`radiance.m4.enabled=false`), but historical source is retained temporarily until N1 replacement coverage makes safe deletion possible.
| official Early Window translation | N2 on migrated core | ALREADY_NEW, USER_ACCEPTED, RE-DERIVE |
| GUI draw/resource/font paths | N3 | REJECTED_CURRENT, MISSING_NEW |
| world proxies | N4 inventory only | DEFERRED |
