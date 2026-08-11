# RenderPearl and Sodium backport feasibility

Observed on: 2026-09-21.
Status: proposed; static feasibility investigation only.
Sources: paired Radiance/MCVR worktrees later consolidated as Radiance
`f9dd73bb0ab3463d952e0c720db06ac4010f36ce` and MCVR
`5150670796380bf128fecec551c864f480bb05f9`. External RenderPearl/Sodium revisions are recorded in
the findings. Revalidate before implementation.

This document records the large architecture proposed and investigated in Codex task
`01a0bcc7-1289-7201-9770-c4e3ec574347`, read on 2026-09-21. The task title still mentions Reflex,
but its recorded turns concern a RenderPearl/Sodium dual-backend migration and third-party-mod
compatibility. This is preliminary feasibility work, not an approved implementation state.

## Requested product model

- Backport the Minecraft 26.3 RenderPearl-era GPU infrastructure to Minecraft 1.21.1.
- Backport Sodium's modern client chunk pipeline using the 1.21.1 Sodium line as the game/NeoForge
  shell and the 26.3 line as the scheduling, arena, staging, cache, and draw-backend donor.
- Provide a startup-time OpenGL/Vulkan choice.
- OpenGL mode should look and behave like ordinary Minecraft 1.21.1 NeoForge with Sodium-style
  optimization and broad third-party compatibility.
- Vulkan mode should provide Radiance/MCVR capabilities. Missing or degraded unsupported mod visuals
  are acceptable; identifiable unsafe requests should fail closed before they can corrupt native
  state. Process crashes should be avoided where the project controls the boundary.

## Preliminary feasibility result

The direction is structurally feasible, but it is a renderer-host migration rather than a library
swap. The inspected 26.3 client contains a distinct `com.mojang.renderpearl` GPU subsystem with API,
frontend, OpenGL, and Vulkan code, while many Minecraft/Blaze3D callers also changed to submit work
through it. RenderPearl therefore needs a maintained source/compatibility boundary; it is not an
independent SDK that can simply be placed on the 1.21.1 class path.

In the inspected MCVR snapshot, MCVR cannot remain a second Vulkan host. It owns
instance/device/VMA/queue/surface/swapchain,
submits frames, and presents. A migrated RenderPearl Vulkan backend would own the same resources.
The target architecture requires exactly one host and one present chain:

```text
Minecraft 1.21.1 / NeoForge / supported mod semantics
                         |
       1.21.1 compatibility and submission layer
                         |
          RenderPearl API and frontend model
               /                         \
      real OpenGL backend        extended Vulkan backend
                                      |
                  borrowed-device MCVR extensions
                  PT / reconstruction / Streamline
```

The Vulkan device must be created with the union of RenderPearl, ray tracing, acceleration
structure, buffer-device-address, descriptor-indexing, NGX/Streamline, XeSS, and other selected
requirements. Streamline initialization and feature requirements must participate before instance,
device, and swapchain creation. MCVR then borrows handles and participates in the host synchronization
timeline; it does not destroy the host resources or present independently.

## Sodium split

Do not mechanically downgrade all of Sodium 26.3. Use:

- Sodium 1.21.1 for Minecraft types, NeoForge integration, model extraction, mixins, and compatibility;
- Sodium 26.3 for modern scheduler concepts, RenderPearl GPU layer, region arenas, persistent staging,
  draw contexts, upload budgets, asynchronous visibility, and command caching;
- Radiance/MCVR for scene export, section-level BLAS/TLAS, path tracing, and reconstruction.

Produce section geometry once and branch at the build result: Sodium consumes it for raster, while
Radiance consumes it for RT. Region-level allocation/upload/budgeting is useful for both, but RT
should retain section-level BLAS lifetime so a local block edit does not rebuild a giant regional
BLAS. Camera visibility may drive raster work; it must not discard geometry that PT still needs
inside the configured world range.

## Backend compatibility contract

### OpenGL mode

- Create a real GLFW OpenGL context and do not initialize MCVR, Vulkan, Streamline, or RT resources.
- Preserve 1.21.1 `RenderSystem`, `GlStateManager`, `RenderTarget`, `VertexBuffer`, and common callback
  contracts as far as practical.
- Keep GLFW rather than backporting SDL as part of the first migration; older mods commonly treat
  `Window.getWindow()` as a GLFW handle.
- Around third-party callbacks, flush RenderPearl work, invalidate GL state caches, call the mod,
  restore critical bindings/state, and resynchronize caches.

### Vulkan mode

- Do not implement an open-ended fake OpenGL. Translate bounded Minecraft/RenderPearl semantics and
  known adapters only.
- Unknown direct GL, custom contexts, framebuffer/shader ownership, or native graphics hooks are
  skipped, disabled, or rejected before native submission with structured diagnostics.
- Split common, OpenGL, Vulkan, Sodium, and optional-compat mixins so one backend does not load the
  other's takeover logic.
- Add startup risk scanning as advice/default selection, not as proof of incompatibility.
- Use a startup transaction: mark Vulkan pending before initialization, mark stable after the menu
  presents reliably, and fall back to OpenGL on the next launch if startup never reached stable.

This policy cannot protect against arbitrary third-party JNI pointer corruption or driver defects;
it can prevent recognized unsupported requests and stale resources from entering project-owned
native code.

## Dependency and platform findings

- A literal donor baseline uses a newer LWJGL family than 1.21.1. Aligning the Java modules and their
  matching native libraries is the lowest-difference prototype path; adding a second private LWJGL
  copy inside the mod is not a valid upgrade.
- The donor bytecode used a newer Java target, but source-level recompilation to Java 21 remained an
  open possibility. This requires a real compilation prototype.
- LWJGL alignment and SDL migration are separate decisions. The initial compatibility goal favors
  Minecraft 1.21.1 GLFW window/input semantics even if other LWJGL modules are upgraded.
- Source redistribution and licensing/EULA constraints require a separate product decision before
  any public donor-code backport. The technical research does not resolve that boundary.

## Minimum vertical-slice prototype

1. Use an isolated branch/worktree and align the required LWJGL modules while retaining GLFW.
2. Bring up the minimal RenderPearl API/frontend plus OpenGL and Vulkan backends.
3. Add a device-requirements provider before Vulkan device creation.
4. Make MCVR borrow the host instance/device/queue; prohibit a second swapchain and present owner.
5. Draw the same 1.21.1 GUI textured quad through both backends without the observed per-GL-call
   proxy path.
6. Add one MCVR compute/RT stage on the shared device and synchronize its output back to a
   RenderPearl texture.
7. Validate resize, resource reload, focus/background transitions, shutdown, and Vulkan validation.

Only after that vertical slice passes should the project migrate world rendering and the Sodium
chunk pipeline. A buildable donor copy, a visible window, or one successful present alone is not
acceptance.
