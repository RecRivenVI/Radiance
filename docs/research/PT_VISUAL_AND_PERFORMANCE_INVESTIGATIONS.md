# Path-tracing visual and performance investigations

Observed on: 2026-09-21.
Status: proposed; static and historical-evidence investigation only.
Sources: paired Radiance/MCVR worktrees later consolidated as Radiance
`f9dd73bb0ab3463d952e0c720db06ac4010f36ce` and MCVR
`5150670796380bf128fecec551c864f480bb05f9`. Revalidate every finding against later commits before
implementation.

This record separates requested behavior from preliminary findings in Codex task
`01a0bcc0-15e4-7f50-809c-4dc0062f2e20`, read on 2026-09-21. The task used static source review,
selected historical captures, one read-only Sundial archive inspection, and official/reference
material. It did not implement or runtime-validate these proposals.

## Requested questions and product ideas

- Reduce Ponder steady-state and scene-transition pressure.
- Give the invisible administrator light block a physically useful PT representation.
- Capture Aeronautics physics-staff effects and correct its player-side attachment transform.
- Recreate the Aeronautics levitite crystal effect in a PT-appropriate form.
- Replace noisy non-refractive transparency with a deterministic presentation model.
- Explain and fix NRD shadows that are much softer than DLSS Ray Reconstruction output.
- Correct PBR normal-map black regions beyond grazing angles, using Sundial as a reference.
- Expose celestial-track tilt and physically meaningful shadow angular spread.
- Audit which glass/materials become refractive media and whether their IOR propagation is valid.
- Add separate controls for block-area lights and compatibility-authored emission.
- Give vanilla clouds a dedicated world-fog range.
- Complete one-sided/back-face-culling semantics across all PT geometry and shadow paths.

## Ponder performance

Preliminary evidence identifies duplicated world state and repeated allocation/rebuild work rather
than one expensive shader:

- Java re-expands captured quads and allocates native buffers each frame.
- Output allocation follows the full Minecraft window rather than the Ponder content viewport.
- Each Ponder scene owns an independent world pipeline, scene buffers, history, entities, and
  acceleration structures; transitions can retain two complete scenes.
- The native path was observed creating full-size output images and descriptor tables repeatedly.
- A historical 3840x2054 capture recorded a transition near 12 FPS with roughly 2118 MiB of new
  images and 356 MiB of buffers in one second; the Ponder world pipeline accounted for roughly
  1841 MiB across 79 images. This is historical evidence and must be reproduced on the revision
  selected for implementation.

Proposed order: persist frame-context images/descriptors, allocate at Ponder viewport/internal
resolution, freeze the outgoing scene as color/depth during transitions, fingerprint static layer
geometry for buffer/BLAS reuse, refit only changed transforms, and measure capture/upload/BLAS/
TLAS/trace/upscale separately.

## Special world content

### Invisible light block

`minecraft:light` has no ordinary baked visible quads, so texture-derived emission does not create
a light. Treat it as an invisible analytic light: serialize position and level 0..15, create a
stable light identity, map level through a calibrated monotonic curve, and keep its helper outline/
number as a depth-tested editor overlay. A short-term six-small-area-light approximation is viable;
a real finite-radius point/sphere light is the cleaner native endpoint.

### Aeronautics physics staff

The hover selection uses a captured outliner path, but the beam invokes its own `LineOutline` and
does not pass through the same global outliner interception. Capture it at `PhysicsBeam.render`, use
a stable beam owner, and render a world-space emissive ribbon with explicitly chosen visibility,
shadow, reflection, and GI semantics. The player-side endpoint repeats the same inverse-camera
transform pattern previously found in the launched-plunger rope; reuse that coordinate correction
instead of tuning a new offset. Validate both perspectives, both hands, FOV, view bobbing, and
moving sublevels.

### Levitite crystal

Split the effect into a physical base crystal and post-denoise ghost/trail layers. The base enters
the TLAS with stable object-space noise and normal/material modulation. The displaced ghost layers
are deterministic translucent effects composited after PT denoising, with depth interaction and
motion vectors; they should not automatically cast shadows, appear in reflection rays, or inject
GI. An optional light-only emission contribution may be added deliberately.

## Transparency model

Separate alpha semantics into:

- `CUTOUT/COVERAGE`: binary any-hit masking;
- `PHYSICAL_TRANSMISSION`: refractive/absorptive path-traced media;
- `NON_REFRACTIVE_BLEND`: particles, ghost layers, beams, markers, and ordinary visual blending.

For `NON_REFRACTIVE_BLEND`, compare pure weighted blended OIT with a quality mode that stores the
nearest 4-8 layers, depth-sorts them, composites premultiplied source-over, and sends overflow into
weighted blending. Composite after PT denoising; perform deterministic direct-light/shadow queries
only for layers that should receive world light. Do not use stochastic transparency as the default
for low-SPP gameplay because it preserves the noise problem this change is intended to solve.

## NRD shadow softness

The inspected integration has three high-confidence contract/quality problems:

1. `directRadiance` is merged with diffuse indirect radiance before REBLUR, so sharp direct
   visibility edges are denoised as low-frequency indirect diffuse.
2. Camera-to-primary-surface `first_hit_depth` is supplied as diffuse hit distance, while NRD expects
   the hit distance of the denoised lobe after the primary hit.
3. `maxBlurRadius=100`, a 30-pixel diffuse prepass, 5x5 hit-distance reconstruction, long history,
   and an additional legacy temporal accumulation pass create a strongly over-smoothed result.

First correct the hit-distance signal, then separate direct visibility from indirect diffuse, reset
history, retest near-default NRD parameters, and remove or justify the extra temporal accumulation.
Compare NRD and RR from identical noisy inputs; do not use RR sharpness alone as proof that the path
tracer's shadow signal is correct.

## Shading normals and PBR normal maps

The black grazing regions are consistent with `Ng`/`Ns` hemisphere disagreement and energy loss:
direct lighting can have `Ng.L > 0` but `Ns.L <= 0`, while BSDF samples generated around `Ns` are
discarded when they cross the geometric-normal hemisphere.

Static defects include unsafe Z reconstruction when `x^2+y^2 > 1`, incomplete mirrored-UV TBN
handedness, unconditional normal-channel orientation assumptions, and a view-dependent grazing
correction that can push `Ns.V` toward zero.

The inspected Sundial archive uses safer decode clamping, removes the 8-bit neutral-normal bias,
preserves tangent handedness, continuously rolls the shading normal back toward the visible
geometric hemisphere, reflects invalid reflection directions back above the geometry, and keeps
shading and geometric normals distinct. Use it as an algorithmic reference, not as copied source.

Proposed order: safe decode and explicit flat normal, correct handedness and calibrated channel
orientation, expose normal strength, replace the current grazing correction, add a direct-light
shadow-terminator treatment, and then address indirect-light rejection with constrained sampling or
a higher-quality microfacet normal-mapping model. Add false-color views for `Ng.V`, `Ns.V`, `Ng.L`,
`Ns.L`, `Ng.Ns`, handedness, NaNs, and rejected BSDF samples.

## Celestial controls and cloud fog

- The celestial orbit is currently rotated by a hard-coded 10 degrees. Expose a signed -90..90
  degree track tilt, default 10 degrees, and make sunrise/sunset sky evaluation use the same
  transformed direction as the disk, lighting, atmosphere, and cloud shadows.
- Sun/moon direction sampling uses `SampleVMF(..., 3000)`. Expose a finite angular radius instead of
  the internal concentration value. Suggested range is 0..5 degrees; about 0.27 degrees is physical
  sunlight and roughly 1.5 degrees preserves the current artistic softness. Sample a bounded cone.
- Vanilla cloud hits currently set `skipFog`, so they bypass ordinary world fog. Add a cloud-only
  horizontal-distance fog, tentatively starting near 256 blocks and reaching zero transmittance at
  360-384 blocks, and then keep `skipFog` to avoid applying fog twice. Fog only the current cloud
  radiance, not radiance accumulated earlier in the path.

## Refractive media and emission controls

- Clear glass and panes use the vanilla cutout layer and therefore are not PT refractive media;
  stained/tinted glass usually uses translucent and is only a refractive candidate when alpha is
  below one. Modded glass is not comprehensively covered.
- The inspected translucent classification also catches ice, honey, slime, water, and unrelated
  translucent materials; it is not a material-identity system.
- Water uses IOR 1.333. Other media derive IOR from LabPBR F0; the fallback F0 0.02 yields about
  1.329 rather than ordinary glass, and unchecked high F0 can produce implausible IOR values.
- There is no medium stack, so nested/touching media and camera-inside-media cases are incorrect.
  Absorption is not consistently distance-based Beer-Lambert attenuation.
- There is no shader-pack control that keeps Advanced PT enabled while disabling only block-area
  lights, nor a unified switch for compatibility-authored emission.

Introduce explicit material semantics and validated IOR ranges first, then medium-stack and
distance absorption. Add separate `block area lights` and `compatibility forced emission` controls;
do not overload global direct-light strength.

## One-sided geometry

Back-face semantics are incomplete:

- chunk geometry partially carries cull state, but mixed CULL/NO_CULL data can disable instance
  culling while opaque geometry suppresses the any-hit fallback;
- ordinary entities, block entities, hands, and particles are submitted double-sided;
- `WorldMeshSink` records cull in cache identity but does not carry it as a native material flag;
- shadow any-hit paths do not consistently reject one-sided back faces;
- Flywheel is the closest complete path but should not rely indefinitely on forcing everything
  through non-opaque any-hit geometry.

Propagate one material flag through all geometry producers, group CULL and NO_CULL acceleration
geometry where practical, allow any-hit whenever software culling is required, and apply identical
semantics to camera, reflection, and shadow rays. Validate positive/negative transforms and opaque,
cutout, translucent cases from both sides.

## Evidence boundary

All items above are investigation results. None is recorded as implemented. Historical captures,
static source facts, external algorithm references, build success, GPU execution, and user-visible
acceptance must remain separate in future ledger entries.

## 2026-09-25 Deferred work requests

Status: deferred by user request; no new implementation or algorithm validation in this addendum.
The historical findings above retain their 2026-09-21 evidence boundary.

- **labPBR grazing-angle black correction (reference: Sundial).** The user explicitly renews this
  work item. Reuse the shading-normal investigation above, but do not treat its historical proposed
  causes as a fresh diagnosis. Establish a reproducible normal-map/material case and check grazing
  versus front views, mirrored UVs and flat normals before selecting a correction. Preserve valid
  shadowing and material response rather than hiding black output with an arbitrary brightness floor.
- **Vanilla-cloud rewrite.** This new request covers the rendering implementation, not only the
  dedicated cloud-fog idea above. Determine the replacement contract against actual vanilla calls,
  cloud options and resource-pack inputs before implementation; verify movement, lighting/fog,
  reload, resource lifetime and measured cost. No particular geometry or volumetric design has
  been selected, and this entry does not authorize an unrequested quality tradeoff.

Current priority/status lives in the
[roadmap](../ROADMAP.md#pt-visual-correctness-work-packages); the
[request record](../DEVELOPMENT_LEDGER.md#2026-09-25-veil-underwater-report-retracted-and-two-deferred-visual-tasks)
also records the separate underwater-texture retest correction.

## 2026-09-27: Global one-sided surface and warped-quad reassessment

Status: investigating; source/specification review and bounded automated/GPU evidence.
Applies to Radiance `a39db18f68b95ddea7d67e5c25e48aa2d0c3e3d5` and MCVR
`91301b6eecd5ccf1932de08d38df3673bb2a3664`, with the retained spring-retranslation worktrees.
This is a new assessment; the incomplete face propagation listed in the original September 21
section is historical evidence, not a description of every current path.

### Scope and legal hit semantics

The user classifies the rejected twisted-spring black/inside-through appearance as a **global
geometry/material/PT correctness issue**, with springs as the observed reproducer. This does
not establish the same visible failure in every model, or prove a cause. Keep the flexible
spring's nonplanar quads, reverse-emitted triangle pairs, cutout, geometric/shading normals,
ray offsets and secondary-ray occlusion in scope; do not hide the report with a spring-only
brightness floor or two-sided exception. Diagram cutout/stress acceptance remains separate.

The [Vulkan ray-traversal specification](https://docs.vulkan.org/spec/latest/chapters/raytraversal.html#ray-traversal-culling-face)
explicitly supports per-ray front/back rejection. An object may remain in the acceleration
structure while intersections from one side are rejected; this does not remove it from
reflections, shadows or indirect rays approaching its accepted side. Face culling and opacity
are independent. Opaque triangles bypass software any-hit confirmation, so mixed-face geometry
must retain hardware culling or a reachable software rejection path. Instance flags can disable
hardware face culling or invert facing; the
[instance-flag reference](https://docs.vulkan.org/refpages/latest/refpages/source/VkGeometryInstanceFlagBitsKHR.html)
also distinguishes instance-space and geometry-transform winding.

Consequently, preserving full-scene PT does **not** require force-two-sided surfaces. The desired
contract remains source-derived `cull enabled`, cull mode and front-face/winding applied to each
ray's own direction, with mirror correction exactly once. Shading-normal face-forwarding is
not a substitute for a geometric hit test. No camera-facing geometry pruning, blanket non-opaque
conversion or new spring/fan whitelist is authorized by this reassessment.

### Current implementation and bounded checks

The inspected spring route does not force single-sided draws to become double-sided. Its
source RenderType uses back culling; `EntityProxy` captures `MaterialFaces` beside the geometry
type, and native `Entities` restores the material bits. `faces::ModelRules` and `WorldPrepare`
then use uniform TLAS culling/flip state, or retain per-geometry face bits with instance culling
disabled for a mixed model. `CULL_DISABLE` disables the hardware face rejection; it does not
disable the explicit material predicate in a non-opaque any-hit shader. This interaction is
specified behavior, not an unresolved conflict between the instance and ray flags.

The spring entity geometry does not use the solid/opaque BLAS shortcut. The inspected primary,
world/reflection/continuation, direct-shadow, priority, cloud and volumetric trace callsites
request back-face culling. Both packs' inspected default, shadow, text, transparent-only,
world-no-reflect, cloud, water-mask and portal any-hit paths apply the material face predicate;
Advanced's volumetric/cloud shadow paths also do. Priority `ignore.rahit` unconditionally drops
hits for its selection pass and is not a spring world-hit route. No production ray-query caller
or backend-generated reverse spring copy was found in the inspected shader/core scope; the
source renderer owns its reverse emission. Source anchors are `EntityProxy` serialization,
native `entities.cpp`, `material_faces.hpp`, `world_prepare.cpp`, and the two packs' `.rgen`/
`.rahit` files. No definite face-routing defect was established in this bounded audit.

Root rebuilt and reran `mcvr.material-faces` and `mcvr.material-faces-gpu`: 512 CPU material
combinations and 40 actual Vulkan ray-query cases passed, including mixed opaque/double-sided
models, uniform hardware rejection and mirrors. The tested ray-query fixture exercises the
same policy helpers, not a production ray-query renderer. Evidence and input/executable hashes
are `D:/Workspaces/Artifacts/RadianceSpringRetranslation/20260927/surface-tint/face-policy-ctest.log`
and `face-policy-test-identity.json` (non-portable). No new complete production SBT/hit-group,
actual failed spring pose, arbitrary external shader pack or emitter-sampling visual validation
was performed. This supports retaining the existing per-ray design, not certifying all visual
paths or dismissing the user's failure.

### Physical and visual limits

API legality is not proof that a source mesh represents a closed physical object. A one-sided
open sheet can be invisible and non-occluding from its back; back-side light leakage, missing
back-side reflection or a missing interior is a consequence to evaluate against the source
surface contract, not a reason to silently give it thickness. Closed refractive-medium entry/
exit handling and sided emission require their own consistent material treatment. Authored
double-sided materials and independently authored reverse faces must retain their identities.

In particular, the original flexible-spring producer submits both orientations. Reversed quads
can use opposite diagonals and form distinct triangle surfaces when warped. Enforcing each
triangle's single-sided rule cannot generally make those surfaces coincide or turn a textured
sleeve into a solid helical wire. The observed black appearance still needs a matched failing
pose before assigning causality. This evaluation does not authorize changing the source mesh,
removing its reverse emission, or applying a global culling-policy rewrite.

The bounded 7.4-degree source-derived numeric fixture makes this distinction concrete. Under
the stated QUADS expansion, opposite diagonals form a thin tetrahedral wedge of about
0.00536648 block-cubed; its triangles **do not intersect internally** in this example. A ray
from +Z enters a front face at `t=0.7349416551` and exits a back face at `t=0.7651848167`.
One-sided rejection removes the exit, not the entry or the wedge. From -Z, the reverse-emitted
surface is itself the front entry. This uses analytic ray/triangle intersections and an explicit
geometric-facing convention, not an actual failed client pose or a runtime Vulkan front-face
capture. The shared source vertex normal also need not imply the same lighting response as
geometric facing. Numeric evidence is retained at the non-portable
`D:/Workspaces/Artifacts/RadianceSpringRetranslation/20260927/geometry/spring-quad-topology-ray-addendum.md`
and `spring-quad-ray-witness.json` (SHA-256
`A25E35DA619564981CF57B7658B082A02A9C24F0CC809903685BF14ACCF8F0CE`).
Do not promote this counterexample to proof of the observed black cause or to a blanket claim
that the paired quads always self-intersect.

## 2026-09-27: Audit against the absolute physical-light-direction contract

Status: audit-only; **the current implementation does not satisfy this contract globally**.
Sources: the same Radiance/MCVR HEADs and retained worktrees identified above, including the
source-only UV1 correction. Root performed this follow-up directly, without subagents or
product edits. This explicitly corrects the earlier inference that consistent query-facing
tests are sufficient to establish correct light transport.

### Authoritative meaning

> **Superseded by the user’s 2026-10-01 two-sided-geometry decision; single-sided semantics
> are reserved for later research.** This section preserves historical evidence and must not
> be treated as the current implementation instruction. See the
> [new decision and prerequisite gates](#2026-10-01-two-sided-geometry-decision-and-topology-audit-gates).

User requirement: every light arriving at the source-defined visible side of a one-sided
surface is a valid interaction; arrival at the invisible side is ignored. "Valid" means enter
the normal material evaluation, not force every material to be opaque, bright or emissive.
Cutout coverage, blending and physical transmission remain separate. "Ignored" cannot acquire
shadowing, absorption, scattering or a medium transition solely because a backward query saw
a different facing. The side comes from geometric winding, source cull/front-face state and
the actual transform, not a shading normal or the main camera.

Let `Nv` point out of the visible side and `dLight` be the actual incident light propagation
direction. Non-grazing front arrival has `dot(Nv,dLight) < 0`. A visibility query from receiver
to light has `dQuery = -dLight`, so the same interaction has `dot(Nv,dQuery) > 0`. In contrast,
a camera/BSDF candidate query locates a surface potentially sending radiance back toward the
previous vertex; its outgoing direction is `-dQuery`. Endpoint scattering and intermediate
segment occlusion therefore need distinct interpretation. Do not invert every camera ray or
every normal to repair shadow queries. The geometric zero-area/grazing and source double-sided
cases require their own invariant treatment.

The [Vulkan traversal specification](https://docs.vulkan.org/spec/latest/chapters/raytraversal.html)
defines front/back relative to the query ray, not the physical light path it represents.
[PBRT's path integrator](https://pbr-book.org/4ed/Light_Transport_I_Surface_Reflection/A_Better_Path_Tracer)
separately evaluates surface emission toward `-ray.d` and samples directions toward lights;
[area-light evaluation](https://pbr-book.org/4ed/Light_Sources/Area_Lights) distinguishes outgoing
emission orientation. These are orientation references, not proof that a standard reciprocal
integrator implements this project's direction-dependent ignore rule.

### Coverage and dispositions

> **Superseded by the user’s 2026-10-01 two-sided-geometry decision; single-sided semantics
> are reserved for later research.** This section preserves historical evidence and must not
> be treated as the current implementation instruction. See the
> [new decision and prerequisite gates](#2026-10-01-two-sided-geometry-decision-and-topology-audit-gates).

Inventory: 20 production `traceRayEXT` callsites in 15 files; no production ray-query initializer
found; 20 any-hit files call the material-face predicate. Includes both built-in PT packs,
primary/hand/continuation paths, priority/background, sunlight, area-light visibility, geometric
cloud lighting and volumetric-light shadows. The Java side covers ordinary entities/block
entities, persistent models, particles, chunk/external-section submissions, WorldMeshSink and
Flywheel; native review includes JNI packing, conversion/topology, BLAS flags, TLAS transforms
and per-geometry appearances. Ponder PT is archived; active diagram/UI raster is not light
transport and retains original draw-facing semantics. Counts are inventory evidence, not an
exhaustive behavior certification of every renderer, resource pack or runtime state.

| Area | Evidence and judgment |
| --- | --- |
| Source face capture and transport | `MaterialFaces`, EntityProxy, ChunkProxy snapshots, ParticleTypeCapture, RigidModelCapture and WorldMeshSink retain source cull/mode/winding. JNI stores these separately from packed vertex normal/overlay bits. No universal sign inversion was found. Flywheel's generic state/filtering limitations remain separate; arbitrary custom mid-draw state changes were not runtime-tested. |
| Hardware versus software rejection | `faces::ModelRules` retains uniform hardware culling and mixed-material any-hit, with determinant correction for mirrored instances. Mixed opaque materials needing software rejection are not marked opaque. This remains a useful mechanism, but it currently assumes one query-facing policy regardless of transport role. |
| Sun/moon visibility | **Historical rule-A disposition, superseded; do not invert these queries.** **Confirmed reversed physical-side test.** Both packs' default/no-height sunlight calls and Advanced `direct_light_eval.glsl` trace from surface toward light with `CullBackFacingTriangles`, then shadow any-hit accepts the query's front. Under the new rule this accepts the physical back arrival and drops the physical front arrival. |
| Advanced area-light visibility | **Historical rule-A disposition, superseded; do not invert these queries.** **Same confirmed defect.** `direct_light_visibility.glsl:29-39,82-84` derives direction from sampled light minus receiver, uses the same query flags and shared shadow group. Reservoir generation/reuse do not remove this visibility error. |
| Volumetric and cloud shadowing | **Historical rule-A disposition, superseded; do not invert these queries.** **Same confirmed defect for intervening single-sided surfaces.** Vanilla `world.rgen:252-261`, Advanced `volumetric_light.rgen:111-123`, and both `clouds.rchit:148` trace toward light. The shared/cloud shadow predicates do not invert that direction. CloudProxy itself explicitly requests NO_CULL; its existing backlit response is not newly classified as a single-sided-cloud defect. |
| Receiver's own direct-light response | For an already accepted ordinary opaque surface, `dot(sampledLightDir, geometricNormal)>0` and the BSDF hemisphere gate use the correct *receiver-to-light* convention for front illumination. Do not reverse these dot products with the shadow repair. Transmission, height-map self-visibility and unusual normal/material states still need integrated directional tests. |
| Camera, reflection and indirect continuation | Endpoint front-facing selection is not itself the same bug as a reversed visibility segment. However, current nearest-hit plus ordinary opaque BSDF termination does not implement a separate direction-dependent null continuation; see the counterexample below. The absolute rule is therefore not established for all light paths by a shadow-only change. |
| Priority/background and special hit groups | They share query-facing acceptance while performing visibility/composition roles. Retain the established first-person/priority contracts; this audit does not authorize reversing displayed faces or broadening their masks. Built-in text, portals, water-mask and no-reflect any-hit use the shared rule. `priority/ignore.rahit` deliberately discards a selection-pass hit and is not evidence of a spring back-face acceptance bug. |
| Sampled emissive surfaces | **Source-side identity gap.** `ChunkBuildData::buildLightInfos`, `LightInfo`/`LightData`, and `sampleAreaLightPoint` use winding-derived normals without carrying source face flags. The light cosine has the correct outgoing sign for the assumed normal, but CULL_FRONT/CW authoring can select the opposite visible side while the sampler keeps the default side. Source double-sided behavior is likewise not represented by a sidedness field. Ordinary CCW/BACK emission is not proven wrong by this gap. |

### Why shadow-only inversion cannot certify the absolute rule

> **Superseded by the user’s 2026-10-01 two-sided-geometry decision; single-sided semantics
> are reserved for later research.** This section preserves historical evidence and must not
> be treated as the current implementation instruction. See the
> [new decision and prerequisite gates](#2026-10-01-two-sided-geometry-decision-and-topology-audit-gates).

Use a non-emissive opaque single triangle at z=0, visible normal +Z, camera at z>0, and a light
behind it at z<0. Actual light propagating toward the camera arrives on the triangle's invisible
side and must be ignored under the user rule. The current camera query instead accepts the
front triangle; its opaque closest-hit samples only the permitted reflective hemisphere or
terminates, so it does not carry that straight-through background contribution. The same
construction can occur on an indirect segment. Conversely, simply reversing all camera-facing
tests would remove the visible surface's own valid reflected/emitted contribution.

This is a source-derived transport counterexample, not a captured client image. It demonstrates
that one bit of query-facing acceptance is insufficient to model both potential scattering
endpoints and directional pass-through of light originating behind them. Preserve the absolute
rule explicitly rather than silently reinstating two-sided blockers or approximating it only
for sampled direct light. A consistent implementation must separate endpoint response from
directional segment/null transmission, including sampling weights and history. Uniform
`ModelRules::shaderFlags` currently removes the original face bits after encoding them into TLAS
flags, and `SampledSurface`/surface caches do not retain a distinct sidedness contract. Those
lowered flags cannot simply be treated as authoritative "double-sided" material information.

### Additional topology finding in the audited producer chain

The legacy `Entities::queueBuild` TRIANGLE_STRIP branch (`entities.cpp:1664`) pairs vertices into
quads and triangulates `(0,1,3),(3,2,0)` in original-vertex terms instead of strip triangles
`(0,1,2),(2,1,3)`. It also copies two previous vertices' attributes and substitutes only their
positions. A nonplanar strip can therefore acquire different surfaces/interpolation; this can
affect sided hits without a globally inverted face predicate. For odd counts of at least three,
the `j + 1` read reaches outside the vertex array. Both ordinary entity JNI submission variants
leave explicit-index pointers null, and StorageVertexConsumerProvider sends non-QUADS through
the generic BufferBuilder path, so this is an exposed legacy path, not proven dead code.

The indexed WorldMeshSink route uses the correct alternating
`WorldMeshContract::triangulate` helper and is not this branch. The current flexible spring
emits QUADS; do not attribute its rejected twist to the legacy TRIANGLE_STRIP defect. No unsafe
out-of-bounds runtime experiment was performed; no product topology repair was made in this
audit. Record the memory-boundary and source-topology findings explicitly for the corrective
batch rather than silently expanding this review into implementation.

### New evidence, limitations and corrective scope

A standalone Release C++ audit executable includes the **current production**
`core/render/material_faces.hpp`. It compares the same physical segment evaluated forward and
as the currently unadjusted receiver-to-light query. All eight one-sided cases (BACK/FRONT,
CCW/CW, two incidence sides) disagree; double-sided and reject-both controls agree. The reported
8/12 mismatch is successful reproduction of a **contract failure**, not a product pass. This
new test does not execute a production GPU hit group. Earlier 512 CPU/40 GPU query-facing cases
remain valid for their original oracle and cannot be reused as physical-direction acceptance.

Local evidence and scoped hashes:
`D:/Workspaces/Artifacts/RadianceSingleSidedAudit/20260927/` (non-portable), containing the actual
helper audit source/build/results, callsite inventory and manifest. No new client, deployment,
product build, GPU-loss experiment or visual result was produced. Unknowns include the exact
rejected spring pose, complete reflected/indirect image comparison, external shader packs,
arbitrary custom face-state lifetimes and combined refractive/height-map behavior.

Corrective work must (1) retain authoritative source-sidedness independently of optimized
query flags, (2) define physical-forward versus receiver-to-light visibility roles, (3) update
hardware and mixed-material software rejection together at all shadow producers, (4) define
consistent directional continuation and endpoint response, (5) carry sidedness into sampled
emission, and (6) remove the legacy strip divergence via the already-correct topology contract.
Validate identical physical segments in both tracing directions, BACK/FRONT/CW/mirrors, mixed
opaque/cutout/coverage/transmission, sun/area/emissive/environment light, secondary paths and
source-visible camera faces. Do not call the system correct after changing only a shared
any-hit function or only the shadow ray flag. The independent Flywheel cutoff/sampler gaps and
spring nonplanar reverse-quad candidate remain as separately recorded findings.

### Proposed complete correction, following the physical-direction audit

> **Superseded by the user’s 2026-10-01 two-sided-geometry decision; single-sided semantics
> are reserved for later research.** This section preserves historical evidence and must not
> be treated as the current implementation instruction. See the
> [new decision and prerequisite gates](#2026-10-01-two-sided-geometry-decision-and-topology-audit-gates).

Status: proposed implementation design, not an implemented repair. The rule is a directional
transport contract, not a synonym for ordinary raster back-face culling. In particular, light
from behind a surface may pass through toward a front-side camera while the surface also
reflects front-side incident light. The rule does not simultaneously guarantee the familiar
"opaque foreground always hides the background" image. This consequence must be present in
the reference cases, not discovered only after changing a complex spring scene.

1. **Define one source-side record and one forward interaction law.** Preserve source visible
   sides, winding and transforms independently of hardware acceleration flags. Derive the
   visible-side geometric normal from geometry, never from normal maps. Actual incident light
   on an accepted side invokes the existing material response; an ignored side has identity
   straight-through transport, with no shading, attenuation or medium transition. Do not turn
   alpha cutout, coverage, glass or additive emission into one opaque material. Fix the legacy
   strip topology/attribute/bounds path using the existing correct helper before integration.
2. **Establish a small forward-light reference.** Deterministic tiny scenes enumerate light
   paths through a single oriented sheet, paired sheets and simple reflectors. A backward
   reference evaluates the same forward scattering law with known outgoing direction. Compare
   both numerically before lowering it to production ray flags. This is a local test fixture,
   not restoration of the retired standalone replay product or replacement of the renderer.
3. **Separate query purposes.** Visibility toward a light uses physical incidence opposite
   the query direction; update both uniform hardware rejection and mixed-material any-hit.
   Radiance/BSDF tracing must evaluate possible endpoint contributions and null continuation,
   rather than globally reverse or retain the old closest-visible-face rule. UI raster and
   priority visibility selection retain their distinct source/display roles. Vulkan culling
   optimizations remain only where equivalent to the selected query purpose.
4. **Implement the directional material operator in both PT packs.** At a front-side outgoing
   direction, a backward evaluation can require both front-side material reflection and a
   straight-through contribution from back-side incidence. Conversely, a discarded endpoint
   contribution is not automatically permission for arbitrary background light to pass. Start
   with deterministic branch evaluation in the tiny reference; production may importance-sample
   branches using matching forward-kernel weights/PDFs. Do not normalize away valid energy,
   double-count emission, or silently discard a branch. An ignored crossing is not a physical
   scattering bounce and must not consume the material bounce budget. Direct-light and BSDF
   sampling/MIS must represent the same law; ordinary reciprocity cannot simply be assumed.
5. **Preserve emitter identity and temporal meaning.** Carry source-sidedness into sampled
   lights, outgoing emission evaluation, light PDFs/reservoirs and material invalidation.
   Maintain separate meaning for a real scattering surface and a null crossing in primary/
   secondary caches and depth/normal/motion guides. Validate un-denoised radiance first, then
   specify guide/history ownership for combined reflected and through contributions. A single
   foreground depth must not be silently claimed to describe all layers correctly. Changes in
   face policy must invalidate every dependent optimized/cache/light representation safely,
   without global GPU idle or premature resource retirement.
6. **Gate rollout on end-to-end equivalence to the reference.** Test front/back physical
   incidence independently of camera side; BACK/FRONT/CW/mirrors; mixed BLAS; animated and
   persistent geometry; 3/4/5/6-vertex strips; cutout thresholds and filtering; emissive, sun,
   area and environment lights; reflection/indirect/null chains; transmission and reload.
   Compare light-sampling and BSDF-sampling estimates of the same scene. Then exercise the
   actual production SBT/hit groups on GPU and a bounded isolated client before judging the
   rejected spring pose. Shared-helper passes alone are insufficient. Measure extra traversal,
   noise, frame cost and resource use; do not promise a performance improvement.

Implementation should proceed as one coherent correction with internal gates: source/topology
and reference law, directional traversal/scattering, emitter/history integration, then actual
GPU/gameplay validation. A shadow-only intermediate result remains incomplete and should not
be presented as the corrected single-sided system. No product code, test source or deployment
was changed while recording this design.


## 2026-10-01: Source-sided directional transport corrective candidate after V4

Status: implemented candidate; build/automated evidence and bounded runtime exercise; user
visual acceptance remains pending. This supersedes the proposed implementation status of the
September27 plan, without rewriting its 8/12 failure or the earlier query-only evidence.
Starting revisions: Radiance `ed32fd33a2c3773ad2faab7abad92d452a739b6d`, MCVR
`6b1b0770d32a5420d9fb3350c0e5a074e1a7065c`; no Git rewrite is authorized by this repair.

### Implemented operator and query roles

The original face bits survive uniform hardware optimization. Backend-only bit23 records the
uniform FRONT canonical-facing flip, separate from source winding and mirror handling. Physical
light visibility runs in reverse to actual incidence: both uniform hardware culling and mixed
any-hit use that role. Priority/display selection retains its source-facing role. Radiance
queries allow either endpoint side and evaluate the directional material instead of discarding
potential endpoints. Reject-both remains empty; opaque uniform geometry retains its fast path.

At a source-visible outgoing endpoint, the backward operator has both ordinary accepted-side
material response and the identity contribution from invisible-side incidence. Primary hits
trace both terms using existing split targets. Secondary hits sample the terms with probability
1/2 and throughput weight2; NEE and endpoint emission are evaluated once. Material samples and
NEE reject source-invisible incident directions. Back-outgoing endpoints have no unconditional
identity/reflection term, but retain source-accepted material transmission. Cutout/coverage and
real transmission remain distinct. Null crossings preserve medium prefix and unbroken-camera
ownership and do not spend a material bounce. A 1024-crossing safety bound prevents an unbounded
shader loop; this is a numerical traversal guard, not proof for arbitrarily deep sheet stacks.

Advanced's existing surface-cache lane7.w carries validity plus source-side mode. Its cached
secondary-surface shortcut resumes real hit-shader transport at directional nodes rather than
consuming a reciprocal approximation. The primary identity branch does not reuse the same
foreground as its second surface. SHARC does not insert/query these direction-dependent nodes
as diffuse cache entries; their direct terms/weighted continuation still propagate to earlier
eligible cache nodes. This local correctness fallback can reduce cache coverage and increase
cost; no performance gain is claimed. Existing allocated split images are reused; diffuse
material response remains in diffuse AOVs, separate from the identity/through term. A single
foreground depth/normal/motion guide cannot describe every layer exactly; denoiser/reconstruction
appearance and temporal stability remain part of visual acceptance.

Source face policy also reaches Advanced's sampled chunk emitters through the unused float32
normal.w lane. FRONT/CW emission normals, bilateral emitters, reject-both and source-ID
invalidation follow that policy without changing LightData's GPU stride. Text/default/no-height,
portal/gateway, unlit-overlay/no-reflect and shadow paths were checked in both built-in packs;
volumetric/cloud shadow producers use the reverse visibility rule. CloudProxy's actual layer
is explicitly NO_CULL, so its two-sided cloud endpoint response is retained. UI raster and the
boat-water ownership mask remain separate selection contracts.

The confirmed legacy TRIANGLE_STRIP repair now uses the existing alternating triangulation and
preserves source vertex attributes, with short/odd counts covered. The flexible spring emits
QUADS; this is not evidence that the strip bug caused its twisting failure. Authored opposing
lichen/spring faces and UVs are untouched; no object/texture whitelist or brightness floor exists.

### Evidence and limits

Non-portable evidence: `Radiance/run/one-sided-correction-20260930/` retains the before/final raw-byte
source manifests and copies, compiler commands/results, failed compiler attempts, package and
runtime identities. CPU regressions cover512 face-model combinations, short/odd strip topology
and84 discrete directional-operator reference cases. The latter is a deterministic reference
oracle, not a production renderer. Actual Vulkan ray-query regression covers120 source-selection,
reverse-light-visibility and radiance-endpoint rays, mixed opaque/double-sided geometry, FRONT,
BACK, BOTH, CW, mirrors and UI-owner isolation. This is not a quantitative full-SBT image proof.

All169 configured built-in RT stages/SHARC variants compiled and passed spirv-val. Generic
shaders and Release native install/package gates passed. CTest ran73 cases excluding the face-GPU
case and passed; that GPU case separately passed (74 distinct cases total). Java GAME250 tests and bootstrap11 passed/2 skipped ran in the final package build. Audit
unit/benchmark/inventory tasks were unchanged and UP-TO-DATE, so their older XML results are
not claimed as fresh executions. Distribution, runtime-resource and Maven-development-identity
gates passed. Historical test counts are not substituted for this batch.

Bounded source/PBR-to-PT client exercise uses a fresh quiet, muted, unattended1280x720/r8 world,
SR/RR Balanced, Reflex on, FG off, SHARC on, Create6.0.10 + Aeronautics bundle1.3.2 + Sable2.0.5.
The real straight spring probe verifies source/PBR delivery, captures output and requests normal
stop. Advanced additionally requires emission collection. The first Advanced-labelled run
actually fell back to Vanilla because that prerequisite was absent; it is explicitly not an
Advanced pass. Subsequent runs and final artifacts are indexed in the paired ledger. No Prism
settings/worlds are used for agent automation.

Unclosed: the user's twisted flexible/torsion spring pose; quantitative forward-vs-backward
production-SBT radiance under area/environment/transparent/height-map combinations; generated
frames and long temporal/low-FPS behavior; external custom shader packs; lifetime of arbitrary
custom face-state producers beyond the pinned paths; sustained performance/resource tradeoffs.
The earlier GPU-loss origin and public DLL license gates remain independent. The candidate may
be deployed for user inspection after the final bounded non-crashing runs; that does not certify
all of these cases or the entire rendering system.


## 2026-10-01: Accepted per-ray one-sided visibility contract

> **Superseded by the user’s 2026-10-01 two-sided-geometry decision; single-sided semantics
> are reserved for later research.** This section preserves historical evidence and must not
> be treated as the current implementation instruction. See the
> [new decision and prerequisite gates](#2026-10-01-two-sided-geometry-decision-and-topology-audit-gates).

Status: product decision confirmed; corrective candidate implemented, build/automated-verified,
bounded runtime-observed and deployed; visual acceptance pending. Following rejection
of the transparent opaque-sheet candidate, the user specifies that one geometric side renders
fully and the other is directly passed through, with PT rays following the same rule. Therefore
each camera/reflection/indirect/shadow query uses its own direction relative to source-visible
geometric facing. An ignored intersection does not shade, absorb or consume a material bounce;
it is absent from that query. An accepted intersection uses the original material unchanged.
Sidedness alone never creates transparency, extra identity radiance or a material/null mixture.
Cutout, fractional coverage and PBR transmission remain independent material properties.

This decision supersedes the earlier nonreciprocal actual-photon-direction interpretation and
its reverse-visibility/endpoint kernel. Do not keep one rule for the camera and a different one
for secondary/shadow queries. Preserve full geometry residency, original face state and winding,
mirrors, per-geometry mixed BLAS semantics and first-person ownership. The correction removes
the discarded integrator, payload/cache/SHARC changes together, retaining source-facing hardware
provenance, safe strip topology and sampled-emitter face metadata. It is not a rollback of V4
or of unrelated resource/error/performance work.

New regression uses the production CPU/GLSL face helpers and actual Vulkan ray queries: three
ray roles must agree for each incidence; a red opaque source-visible triangle must own the
pixel and hide a contrasting blue background, whereas the rejected side must expose that
background without a foreground contribution. Uniform opaque, mixed, CW and mirrored cases
are covered. This is a small geometry/visibility GPU test, not full denoised-PT RGB equivalence.
The old84-case nonreciprocal reference and rejected implementation remain only in the retained
previous-source/evidence archive and are no longer active product tests. Final artifacts and
bounded client results are recorded in the
[paired correction](../DEVELOPMENT_LEDGER.md#2026-10-01-per-ray-single-sided-correction-replacing-the-rejected-transparent-candidate).

### Optional translucent-material foreground/through decomposition

Status: proposed alternative, explicitly requested for retention by the user; not enabled.
The rejected implementation split surface response and background straight-through into separate
paths/targets. The user wants this decomposition retained as a possible future implementation
for genuinely semi-transparent materials, not for one-sided opaque faces. Local recoverable
implementation and artifacts are in `run/one-sided-correction-20260930/source-final/MCVR/` and
its package/compiler records; these evidence files are not temporary cleanup targets.

The rejected candidate added a unit background term to a material response. That is not already
a correct general alpha compositor: ordinary source-over requires fractionally weighted
foreground/background (or a consistently premultiplied equivalent). Future evaluation must
specify coverage versus physical transmission, weighting/PDFs and energy, HDR/color space,
multiple layers/order, depth/normal/motion ownership, resource lifetime and denoising/history.
Additive/emissive and refractive materials need their own contracts. Do not repurpose the old
numerical pass as alpha equivalence or enable the strategy merely because it made an opaque
sheet look transparent. Acceptance requires matched compositing relations and actual dynamic
visual evidence before choosing it over the existing material paths.


## 2026-10-01: Two-sided geometry decision and topology audit gates

Status: user-authorized product direction; investigating/implementation preparation. The user
supersedes both earlier one-sided interpretations: after topology repair and related audit gates,
world PT geometry is visible/occluding from both directions, with transparency solely controlled
by material. Source cull/winding data and source-mode tests remain recoverable for later research.
The existing source-facing implementation stays active until the prerequisite evidence is met;
this entry does not claim a completed global audit or a two-sided deployment.

Prior “no global double-sided conversion” constraints rejected a blind workaround for spring
blackness. This is a new user policy, sequenced after investigating/fixing the geometry mechanism.
It does not authorize hiding unknown failures with brightness floors, object whitelists, deleting
source reverse emissions, relaxed lifetimes or camera-based PT scene removal. The prior
foreground/through translucency strategy remains a separate proposed material study.

Required engineering gates: final-position reverse-cyclic quad matching within a submission;
nonplanar twin-diagonal reconciliation retaining both members/attributes; CPU, GPU-conversion,
explicit-index, chunk, instanced/persistent and raster-to-world-consumer coverage; topology/
primitive history compatibility; default-off isolated geometry audit and backside visualization;
classified duplicates, near matches, degeneracy/NaN, topology expansion, offsets and mirrors.
Two-sided thin-sheet pairs must select exactly one authored member per incident direction to
preserve side appearance and avoid double alpha/transmission, even where source NO_CULL alone
would accept both. Unpaired emitters become bilateral; paired emitters preserve each member.
First-person, vehicle, water-self, UI ownership and priority exclusions must be explicit contracts.
Raster/UI source semantics are not automatically changed by a world-PT policy.

Root will validate, rather than assume, the claim that the thin wedge is renderer-only: original
Minecraft QUADS also has triangle indices. The source-derived inward/outward mechanism, actual
production path and failed-pose evidence must stay distinct. A matching optical difference can
justify the user-authorized topology correction without falsely describing the original raster
mesh as analytic or declaring GPU root cause solved. Torsion remains a separate rigid-model case.

Validation/deployment: isolated muted run/ cases only after the user confirms the machine is
ready; no Prism automation or launch. Preserve findings and unclosed rows explicitly. After gates,
perform normal/two-sided tests and a quiet bounded A/B, matched native/Java/shader/package checks,
then deploy main/matched Audit with owned lock, hashes and Recycle Bin replacement. No Git commit,
stage/amend/push, tag or public binary release. Evidence starts at the non-portable
`run/geometry-topology-bilateral-20261001/SOURCE_BEFORE.json` and retained raw source copies.

### Future one-sided texture implementation

Status: deferred, scope to be supplied by the user and external discussion. Retain source-facing
helpers, source-mode test variants, exact input states and previous implementation evidence; do
not revive the rejected nonreciprocal opaque foreground/null operator as current geometry policy.


### 2026-10-01 topology and bilateral-world implementation evidence

Status: implemented; automated-verified; bounded runtime-observed; user visual acceptance pending.
Supersedes the earlier investigating/gated state in this section. Source-facing traversal remains
recoverable with restart-only `RADIANCE_WORLD_TWO_SIDED=0`; default world traversal is bilateral.
This is the user's subsequent policy decision after the topology correction, not a global-double-sided
patch used to hide the spring mechanism. Source cull mode and winding are retained. Raster/UI culling
is unchanged. The rejected opaque surface-plus-unit-background operator remains retired.

The controlled source-renderer +/-7.4-degree runs exercised the actual Simulated producer, both UV
lanes and final PBR geometry: 48 source quads formed 24 exact reverse pairs. Negative original
splitting produced inward wedges/black regions; alignment corrected the 24 later quads. Positive
source geometry remained lit. Four processes saved all dimensions and exited normally. Offline
opaque witnesses and 48 Vulkan visibility probes confirm the self-shadow mechanism; these are not
proof that every user torsion/bending failure has this cause. Original OpenGL also triangulates
QUADS: do not attribute the wedge solely to an invented Vulkan-only mesh.

`quad_topology.hpp` uses final exact positions, reverse cyclic order and nondegenerate source
QUADS. Only the later warped member changes diagonal; no authored face is deleted. Planar pairs
retain indices. Indexed reverse triangles are marked for unique sheet ownership without retessellation.
The final audit caught an unsafe index-pattern inference: a valid tetrahedron can have the same
four triangles. `authoredQuads` now propagates the real draw-mode contract through entity submission
and eye-layer composition. Untrusted triangle meshes are preserved; regression covers the tetrahedron.
Flywheel supplies authored triangle meshes, so arbitrary mesh indices are not guessed into QUADS.
Its post-build pair flags must be copied back to the shared model; that propagation omission was fixed.

The bilateral wrapper keeps opaque traversal for unpaired opaque geometry. Exact paired sheets
select one authored member for every query, including source NO_CULL sheets; per-triangle flags in
PositionVertex padding distinguish pairs from other triangles within a geometry. The leading vertex
is cloned only when shared triangles need different policy tags. Source helpers/source-mode 160-ray
coverage remain executable; the new 320-ray Vulkan matrix checks bilateral opacity, mirrored winding,
paired side ownership and one accepted interface. This does not replace water/PBR visual acceptance.
Eight previous-position readers reject mismatched indices or diagonal variants. Emitters follow the
actual reconciled triangle geometry; unpaired emitter visibility is bilateral, paired sides retain
individual attributes. An Advanced cloud early return that bypassed ownership was corrected without
rewriting its material behavior.

Default-off isolated geometry export records final CPU data or explicitly labeled deferred-GPU CPU
reference data. Source budgets prevent armor stands consuming all 512 records. The three completed
source/bilateral/final-normal audits exported 381/383/372 bounded records: zero local same-winding,
ambiguous, degenerate or nonfinite reports, and zero near reverse candidates in the quantized-bin
postcheck. Captured families include block geometry, plants/water, ordinary/armored entities, held
items, several particle families, cloud pairs, Flywheel and Sable assembly. Source water/plant/cloud
reverse faces are intentional; planar pairs remain planar. This is not an exhaustive cross-instance
transform audit: text/sign glyphs, all mirrored mod instances and arbitrary third-party programs are
not certified by those counts. Original strip/fan/line contracts and offsets remain unchanged;
finite topology/mirror/history tests are separate evidence. Torsion's rigid parts and authored black
inner texture remain a distinct visual case.

Evidence (non-portable): `run/geometry-topology-bilateral-20261001/`, including SOURCE_BEFORE,
SOURCE_MECHANISM_CANDIDATE, SOURCE_CORRECTNESS_CANDIDATE, SOURCE_FINAL, SPRING_RUNTIME_AB,
GEOMETRY_AUDIT_DISPOSITIONS and the per-process inputs, artifacts, loaded-core and logs. Never merge
those candidate identities. The first factory probe failed because a fixture boat was spawned
underwater; its exception/save evidence remains, and the corrected source/bilateral route completed.
Preflight-only cancellations are not crashes or successful runs. An older companion JAR ignored
correctnessOnly and collected incidental timing; those samples are not analyzed as performance.
The rebuilt companion is explicitly verified before final runs. Performance A/B was canceled by
the user and remains suspended; no performance improvement or no-regression claim is made.

Final build identity: JAR `70A505C397740982EDCB82BA9C16DD5BC19F35326E72E3EB778FDBA987C6ABC8`;
core `70F9874686F8C3AFB8EDCBA4FB9626F20AAE9CCB3BFA424A0E0E63B8BB01C450`.
Final source index SHA256 `C928921799BA6939ED24CD37A86430FAE2C258A845B2374EE7B0D70DAEC5F5DA`;
raw hashes plus LF-normalized UTF-8 hashes, required nonignored new files and submodule revisions.
Build: Release INSTALL; native77/77; GAME250/250; bootstrap11 passed/2 skipped; Audit36/36;
169 stages per source/bilateral/backside variant compile and SPIR-V validation. No staging/commit/push.


Delivery-index addendum: `SOURCE_DELIVERED.json` SHA256
`62AE9CA05DA06EF89DE2AE149F125ED23BF9B5611568C45562C127B0CE52E833`
includes the diagnostic-only eye-height fix; the earlier SOURCE_FINAL/C928 index is retained.
Main JAR/core hashes above remain unchanged. Matching passive Audit JAR
`93D11DDC5C3DCC27559FEB55200FC304F4974FF2C80811CDEDDE620B8A5F35B3`.
Final `final-world-2`, `final-advanced-2`, checked `final-backfaces-3` and spring processes retain
per-process identities. The old `final-backfaces-2` capture is explicitly not inside-block proof.
The checked camera eye64.61999988555908 was within source block64; the magenta backside diagnostic
ran and the process saved/released/closed normally. Source NO_CULL models can intentionally have
inward authored winding; a highlighted back hit alone is not proof of extra geometry. No remaining
unclassified local anomalies were found in the bounded records; complete cross-instance/arbitrary
mod certification remains outside the demonstrated coverage.
