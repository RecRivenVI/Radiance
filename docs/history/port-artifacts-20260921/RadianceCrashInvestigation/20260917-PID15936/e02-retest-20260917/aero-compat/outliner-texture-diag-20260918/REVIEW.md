# Outliner texture/material diagnostics — 2026-09-18
User reports tiny tiled checker pattern on borders; stationary within run, potentially different after restart; appears glowing and glass-like. Root cause NOT established.
Read-only checks: original stroke texture ponder:textures/special/blank.png is white; original geometry UV preserved by current capture; no confirmed texture/UV mismatch yet.
Added one record per RenderType/state to existing radiance-worldmesh-diag.log for captured Outliner meshes. Logs resource texture/GL id/dimensions, PBR UV bounds, per-vertex texture ID set/useTexture/alpha mode, alpha range and maximum encoded emission. PBR alpha 2 would indicate transmission; face expected 10, stroke 0. These logs observe CPU submission, not GPU sampled texture contents.
No visual/material changes in this diagnostic build. DLL unchanged. Build distributedJar successful. Deployed to Radiance Test, hash verified. Previous JAR backed up in before/. User launches manually; runtime diagnosis pending.

Deployed SHA256: ECD717CDA61E3C1386A21012FCAA256E1048FA700419C0D9110CD63EF737E1BD

Run1 (11:41): CPU PBR strokes blank id195 mode0 emission0 UV0..1; checker id203 mode10 emission0 UV0..1; glue id191 mode10 alpha0.498; honey id207 mode10 alpha0.498. No CPU-side glass mode2, UV enlargement or cross-texture ID seen. GPU contents/descriptors not yet verified. Log archived run1-worldmesh.log.
Next diagnostic: read back actual native texture image into minecraft/radiance-outliner-readback/*.png once per logged small RGBA texture/layer; records auxiliary map IDs. Uses existing TextureProxy.downloadTexture; queues may be flushed and GPU waited on (timing perturbation). This checks texture image contents, NOT descriptor sampling or material buffer contents. No visual fixes claimed.
Build successful 20s, deployed JAR verified SHA256 2BAED18711584DE20D3C54420EF458AC5A16A8FB2A23D5F7620C9BFA65683E81. Native DLL unchanged. Await manual rerun.
