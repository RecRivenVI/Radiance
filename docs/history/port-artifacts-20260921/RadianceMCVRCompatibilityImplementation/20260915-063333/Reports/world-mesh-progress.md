# World mesh implementation progress

Date: 2026-09-15

- Implementation: complete pending compiler feedback.
- Static diff hygiene: PASS (`git diff --check`, both repositories).
- Java build/tests: PASS (`compileJava`, `test`, `bootstrapTest`; `Evidence/world-sink-java-tests-1.log`).
- Native contract test: PASS in independent g++ and registered MSVC/CMake Release target; CTest PASS 1/1.
- Full MCVR core compile/link: pending root's final unified integration while shared UI/Textures sources are still changing.
- Native contract test registration: complete as `mcvr.world-mesh-contract`.
- Mixin registration: complete; the three entries are listed in `world-mesh-implementation.md`.
- Runtime/client/visual validation: deferred; prohibited in this subtask.
- Known open behavior: SKYBOX consumer absent, unknown custom shaders rejected, and `AFTER_LEVEL` default-target read ordering remains unresolved before `fuseWorld`.
- Persistent VertexBuffer audit: complete; vanilla/Sable section paths are replaced, while Sable water-occlusion regions and Veil immediate-buffer shader draws still require a separate owned raster-buffer bridge. Raw Ponder BufferUploader sites remain metadata-incomplete unless an explicit custom/UI target is active.
- Source freeze: complete in `Evidence/world-mesh-source-freeze.sha256`.
