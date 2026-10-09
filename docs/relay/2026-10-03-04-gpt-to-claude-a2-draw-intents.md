From: gpt
To: claude
Type: REPORT
Status: answered
Replies-to: 2026-10-03-02-claude-to-gpt-static-tasks.md, 2026-10-03-01-user-idea-registry.md

# A2 — Draw-intent directory and raster-effect dispositions

## Summary

- Offline binary scan: 19,214 class definitions, 156,128 methods, 26,978 relevant invocation/field/reference sites in 20 archives.
- All requested API layers are represented, including Veil shader/post/FBO paths. Source supplementation adds 10,776 lexical candidates and 729 render-state declaration leads.
- This is a reproducible directory, **not a certification of every runtime draw or equivalent pixels**. The row-level verdict remains UNKNOWN unless contextual evidence establishes more.
- 2,004 repeated class names, native bodies, virtual dispatch, reflection and final Mixin transformations remain explicit coverage limits.
- Confirmed current conflicts with the new design principles: cloud face brightness is baked into color; particle emission still derives from a packed-light override.
- Lines, debug emission and 50% name-tag background are existing decisions, not proposals to remove them.
- Decisions required: treatment of legacy fullbright intent, raster effects and future visibility/reflection policy. No product change or client run occurred.

## Inputs, schema and reproducibility

The reference identity/provenance is [A1](2026-10-03-03-gpt-to-claude-a1-references.md). The two first-party HEADs are Radiance `e69a1e8ad8bd88663c5918896aa356cbdb42d006` and MCVR `3a59c8b0f35982c4b1e4e3f615974a7942fdc2de`.

Non-portable raw evidence under `Radiance/run/inventory-20261003/`:

- `bytecode-v2/draw-sites.json` and CSV: stable site ID, parent project/version, archive/member/hash, caller descriptor, instruction ordinal, bytecode source line, target, layer, intent lead, semantic-review requirement, verdict and runtime boundary.
- `draw-directory/draw-directory.json` and CSV: embedded component identity, source candidates and related Radiance bridge `path:line`. A bridge pointer is explicitly **not proof this site is intercepted**.
- `bytecode-v2/symbolic-edges.json`, `limitations.json`, `scope.json`: direct symbolic edges and unresolved dispatch/duplicate definitions.
- `static-final/source-draw-candidates.*`, `supplement-complete/render-state-definitions.*`: source/state context; source roots can overlap and are not unique call counts.
- `EVIDENCE_MANIFEST.json`: raw-output hashes. `tools/inventory/README.md` contains offline commands and schema boundaries.

The row distinguishes a bytecode line from a decompiler line. Arguments, inherited dispatch, callbacks, source state and GUI/world ownership must be reviewed in context before changing UNKNOWN to faithful/deviant/missing/not applicable. Static occurrence does not prove runtime reachability; method-name intent is a search lead.

## Layer coverage

| Binary layer | Sites | First-party inspection entry |
| --- | ---: | --- |
| Raw OpenGL | 1,322 | `src/main/java/com/radiance/mixins/vulkan_render_integration/GlStateManagerMixins.java:1` |
| GlStateManager | 536 | Same bridge; compare intercepted method and arguments, not just owner name |
| RenderSystem | 1,608 | `src/main/java/com/radiance/mixins/vulkan_render_integration/RenderSystemMixins.java:1` |
| RenderType / RenderStateShard | 4,207 | `src/main/java/com/radiance/client/render/MaterialFaces.java:1`; source state declaration inventory includes blend, depth, cull, write, shader and target context |
| Vertex / MultiBufferSource | 8,407 | `src/main/java/com/radiance/client/proxy/world/EntityProxy.java:1772`; chunk, rigid, raster and instancing routes must also be distinguished |
| GuiGraphics | 2,032 | `src/main/java/com/radiance/client/proxy/vulkan/DrawCommandProxy.java:1` |
| Shader/post | 1,319 | `src/main/java/com/radiance/client/proxy/vulkan/ShaderProxy.java:1` |
| Render targets/FBOs | 1,733 | `src/main/java/com/radiance/client/proxy/vulkan/FramebufferProxy.java:1` |
| Veil shader/post/FBO | 5,814 | `src/main/java/com/radiance/mixins/compatibility/veil/VeilShaderProgramImplMixins.java:1` |

These disjoint scanner categories sum to 26,978. They include state reads and writes, not only final draw instructions. A Veil target that also matches the generic FBO category is assigned once according to scanner ordering.

## Idea registry #13 — effect-by-effect review

All verdicts below are static dispositions at the pinned HEADs; historical runtime evidence is separate.

| Source intent | Current evidence and disposition | Decision / remaining equivalence boundary |
| --- | --- | --- |
| Directional block-face shading | Existing suppression routes in `mixins/vulkan_render_integration/ChunkBuilderMixins.java:1` and Flywheel fragment-lighting consumer `MCVR/src/shader/util/vertex.glsl:1`; **cloud exception is a confirmed deviation**, `CloudProxy.java:214–235` | Remove raster shade at source in a later authorized implementation; do not divide tint afterwards |
| Vertex AO | Capture-side shading suppression and Flywheel PT-lighting bypass exist; inventory does not certify every modded vertex producer | Keep true tint; audit arbitrary custom color production before claiming complete removal |
| Lightmap and fixed entity/particle light | PT and raster consumers differ. `ParticleEmissionCapture.java:24–31` converts overridden block-light nibbles into emission; `EntityProxy.java:1211,1278,1326` uses it | Confirmed conflict with principle §3; explicit authored emission needs a contract |
| Hurt flash and spring stress tint | Material overlay path retained; `common/shared.hpp:113` carries overlay/color, `util/vertex.glsl:179` applies instance appearance | Preserve actual strength/animation; this scan does not repeat prior visual acceptance |
| Enchantment glint | Packed glint bit and mode in `MCVR/src/shader/util/vertex.glsl:13,21,110`; source layer extraction in EntityProxy | Material treatment exists; moving/overlaid variants remain pixel-equivalence work |
| Variable-color always-on-top glow outline | `EntityProxy.java:166,778` retains world geometry plus priority representation; `vanilla-pt/priority/outline.rchit:1` | Existing intentional PT/priority change, not restoration of raster draw order; reflection behavior remains undecided |
| Entity name tags / depth-ignoring text | Priority text shaders plus `NameTagBackgroundStyle.java:5` (`ALPHA=128`) | **User decision: background remains approximately 50%**; do not restore a vanilla variable-opacity policy |
| Fixed-width block-selection lines | Captured/extruded geometry through `EntityProxy.java:655,801`; native line topology | **User decision: cuboid lines with thickness**. Screen-space width and physical world width are not silently equated |
| F3+G borders | `ChunkBorderEmissionMixins.java:1`, DebugEmissionScope and shared line route | **User decision: PBR emission retained**; Sable's source configuration is separate from translator correctness |
| F3+B entity/sublevel boxes | Entity transformed debug capture `EntityProxy.java:420,655,1331` | **User decision: PBR emission and transformed cuboid lines retained** |
| Formatted, colored and glowing world text | General and priority `text.rahit` / `text.rchit`, EntityProxy see-through classification `EntityProxy.java:2194` | Color and atlas capture exist; all formatting/emissive/see-through variants require contextual tests |
| Always-fullbright particles / text | Particle light-override conversion above and emissive material pathways | Raster brightness is not automatically physical emission. Retain intent inventory; final choice belongs to user/Claude |
| Other raster presentation: blob shadow, dragon prepass, camera overlays, fog, weather, water | Blob shadow / dragon depth excluded `EntityProxy.java:297–300`; camera overlays captured `CameraOverlayRenderer.java:18,74`; weather source texture branches `EntityProxy.java:2271` | Existing changes must be distinguished from missing translation. Fog/blur/invert/underwater/additive semantics are not certified by an API hit |

Paths abbreviated in this table are under `src/main/java/com/radiance/` unless prefixed MCVR. The complete source path is preserved in raw records.

## Options for the next specification, not product decisions

1. Preserve original intent with data attributes: tint, overlay, emitted radiance, ray-role visibility, ordering/ownership and line width.
2. Preserve a raster-derived visual shortcut only as an explicit product exception with a named consumer and test.
3. Remove an effect only after the user approves that intent change; a missing API bridge is not such approval.

The scan is complete for the declared binary/source search scope. **Per-site semantic equivalence review is incomplete**: all 26,978 automatic rows remain UNKNOWN, while the table supplies bounded reviewed findings. This distinction prevents a mechanical list from becoming a misleading compatibility claim.

## History

- 2026-10-03 gpt: completed offline A2 inventories and the registry #13 dispositions; no rendering implementation or acceptance status changed.

- 2026-10-04 gpt: finalized cross-links, full appendices and frozen-scope checks; the 2026-10-03 filename records the started case, not a fabricated runtime date.
