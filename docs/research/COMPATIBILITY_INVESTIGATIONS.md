# Optional-mod compatibility investigations

Observed on: 2026-09-21.
Status: proposed; static investigation only.
Sources: Radiance worktree later consolidated as
`f9dd73bb0ab3463d952e0c720db06ac4010f36ce`; third-party source versions are identified in the
findings below. Revalidate against later commits before implementation.

This record preserves the requested compatibility goals and the preliminary findings from Codex
task `01a0bcc1-b0ea-7fd0-9f88-ed19fab0d2cc`. The task was read on 2026-09-21. It began as a
Physics Mod investigation and was later reused for Modern UI. These are design and static-analysis
results, not implementation or runtime acceptance.

## Requested direction

### Physics Mod

- Keep Physics Mod as an optional external mod. Do not bundle, copy, patch, or add a mandatory
  compile dependency on its JAR or native PhysX components.
- Preserve Physics Mod's simulation and convert its useful scene data into Radiance/MCVR rendering
  rather than trying to revive its OpenGL renderer under a `GLFW_NO_API` window.
- Treat rigid bodies as the first useful compatibility milestone. Cloth, liquids, volumetrics,
  ocean rendering, GUI physics, and Physics Mod debug rendering are separate later projects.

### Modern UI

- First make Radiance and Modern UI coexist safely on Minecraft 1.21.1 NeoForge.
- Preserve CPU-side improvements such as scale, input, scrolling, layout, shaping, and fallback
  wherever they do not require Modern UI's OpenGL renderer.
- Add Vulkan-backed text, blur/tooltip semantics, and full Arc3D UI in separate stages.
- MCVR remains the only owner of the Vulkan instance, device, queues, surface, swapchain, and
  presentation chain.

## Preliminary Physics Mod findings

### Current incompatibility chain

- Radiance creates a `GLFW_NO_API` window, while Physics Mod performs direct VAO state queries and
  restores with LWJGL OpenGL calls during the frame loop. With no OpenGL context this is an early
  failure risk. The investigation did not run a Physics Mod client, so the exact failure remains a
  strong static prediction rather than a reproduced crash.
- Radiance takes over and cancels the vanilla `LevelRenderer.renderLevel` path. Physics Mod's
  fragment, cloth, liquid, smoke, fire, snow, and ocean render hooks depend on vanilla section or
  level-render callbacks and therefore cannot reach their original renderer normally.
- Physics Mod couples non-render lifecycle work to `MainRenderer.renderAll()`: `updateLastSeen()`
  keeps the world active and `PhysicsUpdater.updatePhysics(...)` consumes pending destruction and
  model work. Simply skipping the OpenGL renderer can therefore let a Physics world expire after
  roughly five seconds and can leave production queues unprocessed.
- `MainRenderer.renderAll()` is not a valid compatibility shortcut. It performs OpenGL rendering,
  constructs its own VAO/VBO resources, and can clear CPU mesh data after upload.

### Proposed rigid-body bridge

Use an optional `com.radiance.compatibility.physicsmod` adapter with string targets, `@Pseudo`
mixins, cached reflection or method handles, strict version/capability checks, and fail-closed
diagnostics. A missing or changed Physics Mod must not affect normal Radiance startup.

The bridge should:

1. Suppress only Physics Mod's VAO save/restore calls when Radiance Vulkan mode is active; do not
   suppress the simulation update itself.
2. Maintain `updateLastSeen()` and `PhysicsUpdater.updatePhysics(...)` from a Radiance-owned world
   lifecycle point without invoking the Physics Mod renderer.
3. Read exposed CPU data through `PhysicsWorld.getBodies()`, `IRigidBody.getEntity()`,
   `PhysicsEntity.models`, and `Model.mesh` before Physics Mod clears the mesh.
4. Cache geometry by model identity plus content/material fingerprint, upload it once, and build one
   shared BLAS per stable model.
5. Update only instance transforms and the TLAS for moving rigid bodies. Do not rebuild a BLAS for
   every fragment every frame.
6. Reproduce Physics Mod interpolation, world offset, disappearance, and resource-reload behavior.
   A raw OpenGL `textureID` is not a permanent material identity.
7. Generalize the native instancing facility below Flywheel instead of coupling Physics Mod to the
   Flywheel adapter.

The first runtime milestone is coexistence, at least 60 seconds of active PhysicsWorld lifetime,
block fragments, basic physics particles and ragdolls, correct transforms/disappearance, reflection
and shadow participation, resource reload, dimension changes, and clean operation without Physics
Mod. Cloth, liquids, volumetrics, snow, ocean, GUI physics, and debug renderers remain out of scope.

## Preliminary Modern UI findings

### Current conflicts

- Modern UI 1.21.1 and Radiance redirect the same window-construction `glfwWindowHint` call with
  contradictory OpenGL and `GLFW_NO_API` requirements.
- Radiance replaces `RenderSystem.initRenderer`; Modern UI's 1.21.1 integration expects this path to
  initialize an Arc3D OpenGL context.
- Modern UI directly calls LWJGL `GL33C` for UI surfaces, font atlases, samplers, and composition.
  These calls bypass Radiance's bounded Blaze3D compatibility interception.
- Both mods take over blur/post-processing entry points. Modern UI's OpenGL `PostChain` must not be
  run as if it were a valid Vulkan implementation.

### Staged compatibility plan

1. **Startup-safe mode.** Resolve the window mixin conflict, prevent Modern UI OpenGL backend
   initialization, retain non-GPU features, and fail visibly for unsupported native UI screens.
   Pin the tested Modern UI version; unknown versions degrade safely.
2. **Modern Text Engine MVP.** Retain shaping, fallback, emoji, and layout. Replace `GLFontAtlas`,
   sampler binding, and normal/SDF/stroke shaders with Radiance/MCVR resources. Test GUI and world
   text separately, including see-through, outlines, emoji, RTL, scale, and high DPI.
3. **Blur and tooltip semantics.** Translate requested radius, animation progress, enable state, and
   screen exclusions into Radiance's UI effects. Translate supported tooltip geometry/shaders and
   fall back to vanilla tooltips for unsupported styles.
4. **Arc3D Vulkan UI.** Backport the architectural principle from newer Modern UI: initialize Arc3D
   on MCVR's existing device, render to an off-screen Vulkan image, explicitly synchronize layouts
   and lifetime, and composite it through Radiance. Never create a second device or present chain.

## Evidence boundary

- Physics Mod conclusions were static and based on inspected 3.0.32/3.0.34 structures. No combined
  game launch was performed in the task.
- Modern UI conclusions were based on the official 1.21.1 source line and Radiance source. No local
  Modern UI JAR or combined runtime was tested.
- File locations and class contracts must be rechecked against the exact third-party JAR before
  implementation.
