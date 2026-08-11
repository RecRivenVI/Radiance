# Ghost ordinary PBR capture — 2026-09-18
User accepted Outliner emission and requested equivalent Ghost capture without self emission, preserving original translucent animation.

Changes:
- Extracted CatnipWorldGeometryCapture from Outliner capture. Outliner wrapper still sets default albedoEmission=1.
- GhostBlocksRenderMixins wraps Catnip GhostBlocks.renderAll only in the active default world stage. Original method executes once with identity root PoseStack (camera-relative world capture) and the shared capturing buffer at emission0. Other targets retain original rendering.
- Ghost translucent/moving block layers reuse their original atlas in entityTranslucent to encode PBR coverage alpha10 instead of transmission2. Captured radiance/ghost/ geometry bypasses unlit mode23/lightning routing, using ordinary world PBR; WORLD mask retained.
- Calls original GhostBlockRenderer each frame: alphaSupplier * 0.75 * PlacementClient currentAlpha, optional breathingAlpha 2500ms cosine, and centered 0.85 scale retained rather than independently approximated. Standard renderer keeps its original geometry.
- Auxiliary initialization fix retained. Native DLL/shaders unchanged. Aeronautics levititeGhosts unrelated and unchanged.

Validation:
- Java21 distributedJar and existing WorldMeshSinkContractTest (3 cases): BUILD SUCCESSFUL 21s.
- git diff --check scoped to mixin config passed with line-ending warning only.
- Runtime mixin application and visual animation need manual acceptance; compile/tests do not prove game rendering.
- Deployed to Radiance Test; previous JAR backed up. No game launched by agent; source uncommitted.

Deployed SHA256: BE2111E5C662E4D66B71A760237C0A98C6F78D0A0B458BEB8C121B28FBEA61CC
