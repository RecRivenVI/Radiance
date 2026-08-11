# Outliner auxiliary initialization fix — 2026-09-18

Evidence from second diagnostic run (11:52):
- CPU PBR IDs/UV/alpha/emission correct.
- GPU readback blank id195: all 256 pixels FFFFFFFF.
- GPU readback checker id207: 128 pixels 1CFFFFFF and 128 pixels 3AFFFFFF. Actual checker is white RGB with alternating alpha, not a coloured missing-texture grid.
- Both resources have allocated specular/normal/flag mappings (blank196/197/198; checker208/209/210).

Confirmed code defect in AuxiliaryTextures.loadAndUpload:
Auxiliary GPU images were allocated and GLIDMapping populated for any identified base texture. Image data selection/default creation occurred only for block/item/entity/models/particle/font paths. textures/special fell through to continue without ever uploading pixels, leaving shaders to sample uninitialized auxiliary images. Base-colour readback alone does not expose this issue.

Fix: move default image construction outside the resource-path conditional. Every allocated mapped auxiliary image now receives explicit existing defaults (specular0, flag0, normal existing alpha255 default). Existing prepared PBR images take precedence. Applies to all excluded paths, not only Outliner. No UV/line width/base colour/geometry changes, no native DLL changes.

Limits: definite uninitialized resource defect; causal explanation for the reported appearance remains a candidate pending visual rerun. No pre-fix auxiliary GPU pixel dump was captured. Existing one-time readback diagnostics retained until acceptance (synchronization may affect timing).

Validation: distributedJar BUILD SUCCESSFUL; git diff --check passed. Deployed to Radiance Test with matching SHA256 36D92E00E7B22C854F6407A7E8AB0A605A96C8827E3BB33665EC5BC307D96710. Previous JAR backed up; manual launch pending. No new unit test added; GPU visual regression requires user rerun.
