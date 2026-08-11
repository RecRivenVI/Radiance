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
