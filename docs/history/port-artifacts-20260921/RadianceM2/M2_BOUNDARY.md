# Radiance M2 — Blaze3D Clear Semantic Frame

## Status entering M2

`M1 = COMPLETE` and `USER_ACCEPTANCE[M1] = PASS` for the Early Vulkan lifecycle only.

## Product invariant

```text
RADIANCE_RENDERER = PURE_VULKAN
WINDOW_CLIENT_API = GLFW_NO_API
OGL_FALLBACK_PATH_COUNT = 0
```

- The GLFW window remains `GLFW_NO_API` from creation through destruction.
- Vulkan owns instance, physical/logical device, queue, Win32 surface, swapchain, command recording and present.
- No hidden, temporary or compatibility OpenGL context may be created.
- `glfwMakeContextCurrent`, `GL.createCapabilities` and GLX/OpenGL renderer initialization remain blocked.
- OpenGL framebuffer rendering is not an intermediate layer.
- A Blaze3D/OpenGL semantic is input to translation only; the output backend is Vulkan.
- Untranslated calls captured by the interception layer are consumed as deterministic `SWALLOWED_NO_OP`; they report inventory symbol, callsite and count without reaching OpenGL or terminating the runtime.
- Merely having LWJGL OpenGL classes on the Minecraft classpath does not authorize runtime initialization or execution.

## Exact M2 visible slice

The first M2 frame is driven by exactly these Phase 1B inventory symbols:

1. `RenderSystem.viewport`
2. `RenderSystem.clearColor`
3. `RenderSystem.clear`

The GAME layer executes those real Blaze3D methods. Narrow Mixins capture and cancel their OpenGL bodies, publish the semantic state to the SERVICE/native lifecycle, and the native Vulkan command buffer presents the resulting deterministic frame.

Coverage status is explicit:

- `RenderSystem.viewport` → `EMULATED_STATE`
- `RenderSystem.clearColor` → `EMULATED_STATE`
- `RenderSystem.clear` color bit → `TRANSLATED_TO_VULKAN`
- intercepted but untranslated semantics → `SWALLOWED_NO_OP`

`SWALLOWED_NO_OP` is never reported as translated or supported. Return-value calls receive deterministic compatibility values; untranslated mutations do not update emulated state.

`RenderSystem.initRenderer` returns after semantic submission. The M2 hold moves to immediately before `new MainTarget(...)`, so M2 does not instantiate a fake MainTarget or claim shader/resource/UI coverage.

## Explicitly out of scope

- MainTarget implementation
- shader compilation or the 200-file shader/resource corpus
- textures, buffers, draw calls, fonts or Minecraft widgets
- world, particles or post-processing
- Streamline feature activation, DLSS SR, DLSS-FG, DLSS-RR or Reflex
- formal native ABI freeze

All other Phase 1B inventory records remain `NOT_STARTED` for M2. NVIDIA application/project identity remains a `PRE_RELEASE_REQUIREMENT`.

## Acceptance

Automated gates must prove no GL context/capabilities/GLX path and a zero OGL fallback count. Coverage evidence must retain every swallowed symbol/callsite/count without changing its no-op runtime behavior. USER acceptance must confirm that the deterministic M2 frame becomes visible and remains stable through the already-qualified lifecycle operations. It does not constitute Minecraft UI or world renderer acceptance.
