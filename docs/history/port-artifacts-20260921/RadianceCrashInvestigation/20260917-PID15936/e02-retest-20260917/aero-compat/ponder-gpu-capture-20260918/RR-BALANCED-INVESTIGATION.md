# Ponder RR Balanced investigation — 2026-09-18

Scope: read-only product-code investigation following user report that DLAA is excellent but Balanced is severely soft. No product files, deployed JARs or running processes changed in this investigation. Runtime quality/root cause remains unproven.

## Confirmed observations
- Previous complex RR capture 17897143885713154: input 1485x835, output 2560x1440. Balanced mapping is correct in dlss_module.cpp; wrapper passes actual input dimensions as render subrect. No evidence of Super Performance dimensions.
- FSR comparison input 1706x960 contains approximately 32.1% more pixels. This is not a matched-input-resolution denoiser comparison.
- Current saved pipeline is rt_dlss Balanced with jitter and SHARC enabled. No resource packs selected in options.txt.

## Halton generator defect, pre-existing in HEAD
MCVR/src/core/render/buffers.cpp:547: while (s.x > 0 && s.y > 0) terminates when base-3 digits finish, dropping remaining base-2 digits. Index 2 gives x=0 instead of 0.25. This code also exists in HEAD. All rendering paths share it; it is not unique to Ponder or RR.
Independent Python reference used integer divmod digits and exact Fraction(digit, base**position) summation. Legacy algorithm reproduced separately. Centered x statistics:
- indices 0..15: mean -0.109375; reference -0.03125; maximum error 0.25 pixel.
- indices 0..255: mean -0.02447509765625; reference -0.001953125.
- indices 0..4095: mean -0.004639625549316406; reference -0.0001220703125.
- indices 4096..8191: mean -0.0015416145324707031; reference 0; max error 0.0035400390625 pixel.
Thus a real defect, strongest in initial history, insufficient evidence to attribute persistent severe softness to it. No fix bundled into this investigation.

## Orthographic SHARC ray footprint mismatch
vanilla-pt/world/world.rgen initMainRay (around 395) uses gl_LaunchSizeEXT for orthographic coneWidth. SHARC update main explicitly remaps sampled pixels and resolution to full image dimensions, but initMainRay still uses the reduced dispatch dimensions. ray_tracing_module.cpp around 1472 dispatches ceil(traceSize/5), default factor 5. Perspective spread already takes full resolution via the coneSpread parameter. Orthographic path therefore has an inconsistent, approximately 5x larger initial footprint during SHARC updates. advanced/common/primary_trace.glsl and advanced/world/world.rgen have the same orthographic launch-size dependence; validate active pass routing before altering all variants.

Numerical illustration from captured projection P00=0.0515625142, abs(P11)=0.0916666761:
- DLAA 2560x1440: regular cone radius 0.0075757568 world units; SHARC update radius 0.037878784.
- Balanced 1485x835: regular radius 0.0130647782; SHARC update radius 0.065323891.
- For a hypothetical 16-texel-per-world-unit mapping, regular footprints are 0.1212 and 0.2090 texels: both clamp to LOD 0. Thus merely changing regular ray resolution does not establish mip blur for such surfaces.
- For a hypothetical 64-texel-per-world-unit mapping, regular footprints are 0.4848 and 0.8361 (both LOD 0), but SHARC footprints select LOD approximately 1.278 vs 2.064. Actual mesh UV density varies; these are calculations, not measured per-hit LODs.
- Max unclamped LOD increase from using /5 dispatch dimensions is approximately log2(5)=2.322. This can affect sampled cache lighting/materials, but it has NOT been shown to explain the reported RR quality difference.

## Next controlled experiment
Prioritize the orthographic SHARC footprint correction alone (pass the actual full sampling resolution into initialization), preserving mode, scene, camera, jitter and all other settings. Build and deploy per standing user instruction, then capture the same scene after history settles. Keep the shared Halton correction separate so its effect is identifiable. A DLAA/Balanced paired capture remains necessary; no fresh paired capture was taken this turn. Avoid treating historical unequal-resolution NRD-FSR images as a quantitative control.
