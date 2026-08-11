# Ponder camera factorization A/B — 2026-09-18

Baseline: RR evaluations accepted, stable handle, first reset only, but severe visual noise. See sibling ponder-rr-diagnostic-20260918/runtime-rr.log. Baseline stable view determinant -1; entry has additional nonuniform scale. This does not prove an RR contract violation.

Candidate: replace uniform-scale removal with affine camera factorization. Preserve original camera center (-A^-1 t) and normalized view Z row; orthogonalize view X against Z, derive Y by cross product for determinant +1. Construct rigid view V', and P'=P V inverse(V'). Thus P' V'=P V, preserving clip positions and unprojected orthographic rays. GUI reflection/nonuniform scale remains in projection. Scene geometry, normals, materials, denoiser mode, sampling and instance configuration are unchanged. Existing bounded RR diagnostic remains enabled.

Changes: middleware/ponder_path_tracer.cpp, new render/scene_camera.hpp; tests/scene_camera_test.cpp and tests/CMakeLists.txt. Tests cover reflection, anisotropic scale and ordinary rotation, orthogonality, determinant, camera center, clip position and unprojected rays. This is not visual acceptance or proof of denoising correctness.

Build/test/deployment results appended after completion. User launches manually; compare same Ponder scene static noise, motion and shape. No commit/staging or game launch by agent.

Build PASS: prepareRuntime distributedJar exit 0. Test target build PASS; ctest -C Release -R ^mcvr.scene-camera$ --output-on-failure: 1/1 passed (0.02s).
Deployment source/target JAR SHA256 match: 419F8D06B1F33C2726FA08CA5316412DBF04D45B2DB213A67675561668D95F92
DLL SHA256: 0B608EF7076DAB54E6B0FD1DF5A162919E397B830F26FD1A44979B1C3743660F
Runtime acceptance pending user launch. Diagnostic log appends: the next CREATE begins the candidate run.

## Manual comparison result
User reports no meaningful denoising improvement, and no newly observed visual problems. Runtime samples now show viewDet=1, confirming candidate factorization reached the RR input. RR records retain success=1; reset=1 occurs at the first evaluation of each recorded handle, with subsequent samples reset=0. Scene 4 maintains the same handle through sampled frame 1200. Input remains 1485x835, output 2560x1440, all resources present. Captured CPU geometry depths remain positive and finite.

Outcome: camera factorization alone did not improve the reported noise. Do not label this a denoising fix or conclude that all camera/input conventions are correct. Next priority is GPU input/output capture (depth, normals/roughness, motion, radiance before RR and output after RR), particularly static-frame temporal correspondence. No additional product changes made during this log review.
