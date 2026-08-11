# Path-tracing design principles

迁移日期：2026-10-09；原始观察日期：2026-10-01。本次只迁移结构，不将历史结果提升为本轮验证。

## 问题

保留原调查的范围、证据等级与未完成事项。当前实现与批准的产品约束以[项目现状](../project/status-current.md)为索引。

## 方法

原始记录来自 Radiance 迁移前的已提交源码与对应证据。以下保留原文与日期，避免重新解释历史失败。

## 发现

Recorded on: 2026-10-01.
Sources: the user's statements in an off-field advisory thread, recorded at the user's request.
Quotations in Chinese are the user's own wording and take precedence over the English paraphrase.
Status: user-stated design direction. This is not an implementation record. The reflection policy
(section 6) is still open.

This file was written while another agent was editing `docs/ROADMAP.md`,
`docs/DEVELOPMENT_LEDGER.md` and `docs/research/PT_VISUAL_AND_PERFORMANCE_INVESTIGATIONS.md`.
To avoid interfering with that work, it deliberately changes no other file. Several things are
therefore still to be done:

- linking this file from `docs/README.md` and the roadmap;
- recording, in the ledger, how it relates to the 2026-10-01 per-ray single-sided contract;
- deciding whether these principles become their own document class in
  `DOCUMENTATION_POLICY.md`.

## 1. Goal: a fully Vulkan, path-traced world

> 我的目标从一开始就是全局 Vulkan 化，不留任何 OpenGL 内容，世界几何内不留任何后渲染

- Every piece of world geometry enters the acceleration structures and is imaged by path tracing.
  No OpenGL path and no raster overlay pass remain for world content.
- Behavior that the raster pipeline produced by draw order or depth state, for example
  always-on-top overlays, is expressed as an explicit rule inside the path-traced image. It is
  not drawn by a separate post-render pass.

## 2. One rule set for all geometry, with no geometry-specific rendering

> 我其实非常想避免对某些几何特化渲染……构建一套足够完善的渲染规则一次性覆盖所有几何

- Rules depend only on generic attributes that every geometry can carry:
  - face state (cull mode, winding);
  - material (alpha mode, transmission, emission);
  - visibility attributes (section 4);
  - ray role.
- Rules never depend on mod names, class names, texture names or a list of particular models.
- When one object renders wrongly, the fix identifies which generic attribute or generic rule
  is wrong, and corrects it for every producer. No whitelists, no brightness floors, and no
  per-object exceptions.

## 3. Reuse vanilla geometry, but exclude raster-only "optimizations"

> 尽可能复用原版已经提供的几何特性，并且需要尽可能排除原版光栅为特定效果做出的"优化"（典型的就是原版光照为几何贴图强行施加颜色变化，不小心带入 PT 导致包括但不限于方块六面颜色差异、Sable 物理结构旋转是错误发黑、粒子在光源附近异常亮等问题）

| Keep (geometry and material facts) | Exclude (raster-only lighting and effect shortcuts) |
| --- | --- |
| Positions, winding, normals, UVs | Directional per-face shade factors (the fixed brightness per block face) |
| Texture, true tints (biome, dye, team and similar colors), overlay effects such as hurt flash | Vertex ambient occlusion baked into vertex color |
| Alpha/cutout/translucency modes, authored emission intent | Lightmap brightness (block and sky light) applied to color or treated as emission |
| Source draw state as a fact about the source (cull, depth test, layering) | Raster-only lighting of entities and particles (fixed directional lights, lightmap tinting) |

How to apply this:

- Remove raster lighting **at the source**: capture geometry with shading, ambient occlusion and
  lightmap effects disabled, or intercept the values before they are combined. Do not divide them
  out afterwards. Division cannot separate lighting from true color, and dividing by zero turns
  into black.
- Path tracing computes all illumination. The albedo that enters path tracing is texture × true
  tint and nothing else.
- Derive emission from the material or render type (emissive render types, authored emission
  maps). Never derive it from lightmap values.
- Observed symptoms that this principle covers:
  - colors differing across the six faces of a block;
  - Sable physical structures turning black while they rotate;
  - particles abnormally bright near light sources.

  Any producer that still exhibits such a symptom is a defect under this principle.
- An existing precedent: the Flywheel path applies no lightmap, face shade or Flywheel/Sable
  sampled light inside path tracing. See `applyFlywheelFragmentLighting` in MCVR
  `src/shader/util/vertex.glsl`.

## 4. Full path-tracing participation is separate from imaging

> 我现在重新倾向于让几何完整参与路径追踪……对于特定面不可见的几何，这里我们区分几何本身的成像与参与路径追踪的几何两种，后者完整参与路径追踪，前者负责在面向摄像机绘制时根据特性选择绘制或不绘制

Known special draws that this covers:

- geometry drawn without depth testing;
- geometry invisible from both sides;
- geometry visible from one side and invisible from the other.

| Layer | Rays | Rule |
| --- | --- | --- |
| Full path-tracing participation | Shadow and light visibility, diffuse and rough bounces, sky light, volumetric and cloud lighting, radiance-cache updates | Every geometry participates completely. It is two-sided, it blocks, reflects and emits, whatever its imaging rule says |
| Imaging | Camera rays | The geometry's properties decide whether it is drawn toward the camera: hidden faces are not drawn, invisible geometry is not drawn, and depth-ignoring geometry is drawn on top |

The translucency exception:

> 后者的完整路径追踪应有一个明显的例外：半透明后绘制（这里同时包括薄片半透明与折射体），光线穿过半透明后依然应在面向摄像机绘制时根据特性选择绘制或不绘制，包括多层半透明

- A camera ray that continues through thin translucent surfaces or through refractive volumes
  (glass, water and similar) is still imaging. The imaging rules keep applying after each layer,
  across any number of layers.
- Along an imaging chain, a surface that is not drawn is absent: it causes no absorption, no tint
  and no layer count. Each drawn translucent layer still contributes its own material response.

Implementation consequences (advisor notes, to be confirmed while implementing):

- The guide buffers for the denoiser and upscaler (normal, albedo, depth, motion) come from the
  first surface that is actually imaged, not from a skipped one.
- Depth-ignoring overlays are imaged by a camera-chain query with priority composition (the
  existing priority path is a candidate), not by a post-render pass. In lighting they remain
  ordinary geometry.
- Coincident, opposite-facing face pairs, such as zero-thickness plant models and the water
  surface seen from below, form one two-sided surface whose two sides have different
  attributes. Every ray uses the member that faces it. Otherwise hits at equal distance flicker,
  and translucent pairs are counted as two interfaces.
- Geometry can be invisible to the camera yet still cast shadows and contribute bounce light.
  Examples: a one-sided sheet seen from behind, the first-person body, invisible entities. Whether
  invisible entities may be revealed by their shadows is a gameplay decision for the user.

## 5. One-sided geometry

- Physical note: no real object is opaque from one side and transparent from the other.
  - In a path tracer, rays travel from the eye back toward the light.
  - For such a sheet, the rule that reproduces the intended camera view ("seeing") and the rule
    that reproduces the intended lighting ("lighting") point in opposite directions.
  - Reflection rays carry both an image and illumination, so no single per-face rule satisfies
    both. Section 4 resolves this by separating imaging from participation.
- The renderer must not create faces that the source does not contain. A shader developer
  remarked that back faces should not appear in the visible scene; the user found this persuasive.
  In content that is closed or authored in pairs, a visible back face or an extra surface
  indicates a defect in the renderer.
  - Example found on 2026-10-01: the flexible spring submits each quad twice, the second time in
    reverse cyclic order. Our fixed QUADS split `(0,1,2),(2,3,0)` gives the two copies opposite
    diagonals, so a warped pair forms a thin tetrahedral wedge.
  - An offline recomputation from the retained source transcription shows that the wedge faces
    inward for one sign of the per-segment twist. Its visible face is then occluded by its twin.
    This matches the blackening seen only when the spring twists.
  - The fix is a generic topology rule: a warped coincident pair uses one consistent diagonal.
    It must be applied in every triangulation path.
- Direction recorded on 2026-10-01:
  1. Repair renderer-created extra faces and audit the whole project for similar defects.
  2. Then reset all geometry to two-sided and remove every existing one-sided culling semantic,
     as a clean baseline.
  3. Re-implement visibility from scratch according to section 4.

  The user chose the reset because the current geometry system already shows many layered
  patches. The one-sided implementation and its tests stay recoverable (disabled, not deleted)
  for future research.
- Superseded exploration from the same day: an earlier wish that a one-sided sheet should neither
  reflect nor cast a shadow when its open side faces the sun. Under the full participation of
  section 4, a sheet blocks and reflects from both sides.

## 6. Open question: reflections

The user is undecided. Advisor proposal, not adopted:

- Split imaging rules into two kinds:
  - **Intrinsic appearance**, valid for any observer: a one-sided back face has no image; an
    invisible entity is invisible.
  - **Player-viewpoint presentation**, meaningful only for the player's eye: hiding one's own
    first-person body, depth-ignoring overlays such as through-wall name tags or the selection
    outline, held-item and spectator views.
- Mirror-like and glossy reflections, and any transmission that continues from them, form a
  reflection imaging chain that applies only intrinsic appearance. A mirror then shows what an eye
  at the mirror would see, including the player's own body, but no through-wall overlays.
- The camera chain, including its continuation through translucency, applies both kinds.
- Diffuse and rough bounces are full participation. The boundary follows the material sampler's
  own lobe choice (specular lobe means imaging, diffuse lobe means participation), optionally
  fading toward participation as roughness grows, so that no hard threshold is visible.
- Alternatives:
  - Reflections fully participating: simplest. Mirrors then show back faces, invisible entities
    and interiors that the eye never sees.
  - Reflections copying all camera rules: the own body disappears from mirrors, and overlays
    appear through walls in reflections.

## 7. Engineering approach (context, not decided)

> 整个 Radiance 和 MCVR 都已经充满的修补痕迹，而且和上游严重分叉……很可能距离重构不远了

Measurements made on 2026-10-01, read-only:

- Vanilla PT `default.rchit` and `no_height.rchit` are 1,133 lines each and differ by one line.
  Similar pairs exist in the Advanced pack.
- 261 shader files, of which 21 are any-hit shaders, each repeating the face and material
  predicates.
- Relative to the pre-port upstream base:
  - Radiance: +47.6k/−4.9k lines across 640 files;
  - MCVR: +34.5k/−4.6k lines across 420 files.
- The single-sided contract changed five times between 2026-09-21 and 2026-10-01.

Advisor recommendation, not adopted:

- Write a rendering specification from these principles first, then contract tests derived from
  it, then code.
- Keep few central modules, generate shader variants from single sources, express special
  behavior as data, and count deleting the old path as part of done.
- Migrate incrementally rather than rewrite in one step.
- Use the visibility re-implementation of section 5 as the pilot vertical slice.
- Whether to treat both repositories as independent of upstream (taking behavior as reference,
  not merging code) is a decision for the user. It would change the current policy of
  upstream-shaped repositories.

## 8. Existing constraints that remain

These principles keep the earlier constraints recorded in the roadmap:

- the full scene participates in path tracing;
- quality, view distance and animation frequency are not reduced as optimizations;
- no camera-based pruning of path-traced geometry;
- Ponder PT stays archived.

## 结论

本次迁移不实施研究中的提案，也不重新判定视觉、性能或设备丢失根因。旧台账与闭合审计保留在仓库外历史存档。
