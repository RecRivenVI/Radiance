# Radiance targeted Early Window and publication verification

Date: 2026-08-25

## Scope result

- OFFICIAL_EARLY_WINDOW_PRESERVED = TRUE
- PURE_VULKAN_POLICY_PRESERVED = TRUE
- REAL_OPENGL_FALLBACK_PRESENT = FALSE
- CUSTOM_EARLY_WINDOW_REPLACEMENT_PRESENT = FALSE

The active implementation keeps NeoForge `fmlearlywindow`, `DisplayWindow`,
`ImmediateWindowHandler` and `NeoForgeLoadingOverlay`. The official DisplayWindow GLFW/GL
call sites are patched to the Radiance Early Window bridge. The official call site creates a
`GLFW_NO_API` window, native Vulkan presents the translated official frames, and the same
official handle is handed to Minecraft. No provider reflection/replacement, NoVizFallback,
early DisplayWindow close, replacement GLFW window, or real OpenGL context is present.

Runtime evidence:

- `logs/latest.log:5`: `Loading ImmediateWindowProvider fmlearlywindow`
- `logs/latest.log:73`: accepted the official Early Window as the GLFW_NO_API Minecraft window
- `logs/latest.log:123`: complete Vulkan resource generation published
- `s2.log`: non-background official frames translated and presented through Vulkan, followed by
  the official resource shutdown path
- the final runtime passed the official overlay lifecycle and rendered formal game/world frames

Two bridge defects exposed during cold startup were fixed without restoring the replacement
design: a transient FMLConfig reload race and NeoForgeLoadingOverlay direct GL state calls.

The final run later reproduced the separately known renderer failure:
`vkQueueSubmit(CommandBuffer::individual) failed with VkResult=-4`.
Evidence: `crash-reports/crash-2026-08-25_06.33.47-client.txt`. This occurred after the official
window handoff, resource reload, official overlay cleanup, and entry into formal Vulkan world
rendering. It is not classified as an Early Window failure and no recovery/replacement logic was
added.

## Publication model

| Node | Status | Maven publication | publishToMavenLocal |
|---|---|---:|---:|
| 1.20.1-forge | governance-only | absent | absent |
| 1.21.1-neoforge | business target | `mavenJava` | present |
| 26.1.2-neoforge | governance-only | absent | absent |
| 26.2-fabric | governance-only | absent | absent |
| 26.2-neoforge | governance-only | absent | absent |

The `maven-publish` plugin and `MavenPublication` are applied/created only inside the
`businessTarget` model branch. No terminal task disabling or `doFirst` rejection is used.

## Automated validation

- `1.21.1-neoforge` Java compile/test: PASS
- internal native CMake configure/build/install: PASS
- CTest: 17/17 PASS
- JNI hash, native manifest, shader and staging verification: PASS
- JAR and packaged native verification: PASS
- `buildAndCollect`: PASS
- configuration cache reuse observed on repeated build/run task graphs: PASS
- MCVR import map: 391 entries, 391 destinations present, 0 missing
- legacy `com.radiance` production package references: 0
- external sibling MCVR build/runtime references: 0

## Git seals

- Radiance HEAD: `cd67362ebb5d9f49c7e23e53f51d35d25409e6a2`
- Radiance HEAD tree: `05091a9853a3f1dff8f8c67ec36f36aae2e2ea45`
- Radiance staged: 0
- Radiance unstaged entries: 237
- Radiance untracked entries: 13
- MCVR HEAD: `b611d24a044384290beaf93131ad51bf14e3d107`
- MCVR HEAD tree: `03cb37efc7a4768fa3f16b6725983b5439c1b4c0`
- MCVR worktree: clean
- MCVR submodules: 14 exact, no dirty prefix
- commit = 0
- amend = 0
- push = 0

Final invariants:

- MCVR_WORKTREE_MODIFIED = FALSE
- MISSING_MCVR_AUTHORED_CAPABILITY = 0
- EXTERNAL_MCVR_BUILD_DEPENDENCY = 0
- EXTERNAL_MCVR_RUNTIME_DEPENDENCY = 0
