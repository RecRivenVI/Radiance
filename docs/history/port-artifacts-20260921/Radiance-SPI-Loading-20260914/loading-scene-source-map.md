# Loading scene source map

## Product implementation

The product loading UI is under `src/bootstrap/java/com/radiance/bootstrap/ui/` and
`src/bootstrap/resources/com/radiance/bootstrap/ui/`. It is a source-backed port of
FancyModLoader earlydisplay 4.0.43 layout calculations. OpenGL state, buffers, and
rasterization were replaced by the fixed Radiance draw-list protocol; no CPU pixel
rasterizer or generic OpenGL emulation bridge is present.

| Radiance file | Official 4.0.43 source basis | Official SHA-256 | Preserved behavior |
| --- | --- | --- | --- |
| `ColourScheme.java` | `ColourScheme.java` | `499CAA5F930909F0E4EA13A85AEEF3A029F9AC61472D9A3AAECF51E7AF24F352` | RED/BLACK colours and ABGR packing |
| `RenderElement.java` | `RenderElement.java`, `QuadHelper.java` | `AEC69AFA44E9E75520B03E57A1CC2905404FF96792F5BD9BCC8F04AC1DE3F502`, `E6FE2B442DB843DB8E28B4E768D8C4B56536BA0B2378FD1481B949500A216A1B` | fox/squirrel/Mojang geometry, overlays, progress/performance bars, animation/fades |
| `SimpleFont.java` | `SimpleFont.java` | `544C15506E78D0A85582BDEF187E1D13F237EB32B7133589998FFDFF7B2A1C55` | 24px STB packing, 256x128 atlas, metrics, ASCII layout |
| `STBHelper.java` | `STBHelper.java` | `74576586363FB998ED867EF4838A63B648D3AF9DBD9FBCBD6C7A7DD76601129D` | classpath loading and STB RGBA decode |
| `PerformanceInfo.java` | `PerformanceInfo.java` | `3F23B3826866C33357D52AE3C66005C13D8A1F8CF0756E5E81A2912A99EE87FC` | heap/process CPU text and memory fraction |
| `FrameBuilder.java` | `SimpleBufferBuilder.java`, contract | n/a | native-endian direct buffers, 20-byte vertices, 16-byte batches, quad expansion `0,1,2,1,3,2` |
| loading SPIR-V | `ElementShader.java` | `9C83A9BAEC7518418455DF66FFB1520198C46F930782FEA08A35B575FFF37AAE` | position formula and FONT/TEXTURE/BAR fragment roles |

Official source root:
`D:/Workspaces/Artifacts/NeoForge-21.1.248-Source-Study-20260914/sources/earlydisplay-4.0.43/`.

## Binary resources

| Resource | SHA-256 | Attribution/license |
| --- | --- | --- |
| `Monocraft.ttf` | `B61EE3256F449E96140F54515819540840EF0CADE82711EBA359A5864C779076` | Monocraft 3.0, Copyright 2022 Idrees Hassan, SIL OFL 1.1 |
| `fox_running.png` | `D9D8679957F82DA3F7077FC9CEB403B7FDFD45E7EB25088956EA6F0B45654D7D` | FancyModLoader earlydisplay 4.0.43, project-wide LGPL 2.1 terms |
| `squirrel.png` | `C9A6357CBE873FE8F3623F4A761C34B683F8A557324F53FDD783A8B0A20630DC` | FancyModLoader earlydisplay 4.0.43, project-wide LGPL 2.1 terms |

The cached published earlydisplay 4.0.43 JAR SHA-256 is
`76461588A12FD0D1F2A4B8D570676F919FC57EBA0F82066DA0AC0E884FC93242`.
It and the cached sources JAR contain no separate fox/squirrel license file.
`LGPL-2.1.txt`, `OFL-1.1-Monocraft.txt`, and `NOTICE.txt` are included with the
bootstrap UI resources.

## Shader and sampler checks

The loading SPIR-V disassembly confirms `(position / screenSize) * 2 - 1`, role 0
samples texture R as glyph alpha, role 1 multiplies sampled RGBA by vertex colour,
and role 2 uses vertex colour. Frame opacity only multiplies output alpha.

FML 4.0.43 `SimpleFont` explicitly sets `GL_CLAMP_TO_EDGE`; its other image
textures retain OpenGL's default `GL_REPEAT`. `DrawSink.texture` therefore carries
an explicit `clampToEdge` bit: font uploads use true and PNG uploads use false.

Loading shader hashes used by the first hardware comparison:

- `element_vert.spv`: `8F702F20259CCD0DE1AB0FDF504A9D6A5171090CF99186748C5DF31CF5511A2F`
- `element_frag.spv`: `CA101F712D0B7661D9981A26B881415CD34A9C671741413A64C36A2015C2E6BE`

## Verification status

- `bootstrapTest`: PASS, 4 deterministic model/protocol tests; GPU test skips when
  `RADIANCE_PARITY_RUNTIME` is absent.
- Hardware base scene (`fox+version+performance`), build3 core
  `75144A9CBFCF749D85C3FFB3C43935EB819748C9DFF4D53C1A4F22CA9139E70E`:
  PASS, `diffPixels=0`, `maxChannelDiff=0`.
- Hardware scene with a deliberately edge-sensitive Mojang checker texture on the
  same build3 core: FAIL, `diffPixels=39130`, `maxChannelDiff=14`; this isolated the
  missing GL_REPEAT/CLAMP distinction and led to the explicit sampler flag.
- Final sampler-aware native rerun, core6
  `2620CA1215E3FC8ADDCC82393F0B182C403134A55114BCA2B7DEC9D55E1FA89A`:
  PASS for `mojang+fox+version+performance`, `diffPixels=0`,
  `maxChannelDiff=0`. The official GL and Vulkan PNG files are byte-identical,
  SHA-256 `8EEED852F9D0C2A2C3B9DB70C1674A4293582F4F49F89E7028F3AB1D5ADF0387`.
- Core6 intermediate-alpha run (`scale=1`, `frame=12`, scene alpha 64): PASS,
  `diffPixels=0`, `maxChannelDiff=0`; both PNGs SHA-256
  `845705DD7C1D63F6EFF2B04852C894896BE8B13259AA257E6C34E75A1223D167`.
- Core6 expanded run (`scale=2`, `frame=27`, Mojang fade alpha 170,
  squirrel and determinate 30% progress enabled): PASS, `diffPixels=0`,
  `maxChannelDiff=0`; both 1708x960 PNGs SHA-256
  `CDE9B45CB2FE4CA67CDF0BE5700A6ACC039A890BA58E708ECE25D7671D3EA4DE`.

Artifacts for the two build3 diagnostic runs are in `parity-base-build3/` and
`parity-build3/`; the accepted zero-difference run is in `parity-final-core6/`.
Additional accepted runs are in `parity-final-core6-alpha64/` and
`parity-final-core6-scale2-frame27-squirrel-progress/`.

The accepted scale1/frame12 case was rerun with
`VK_INSTANCE_LAYERS=VK_LAYER_KHRONOS_validation`. Loader logs prove both instance
and device insertion from Vulkan SDK 1.4.341.1. The full 53,094-byte log contains
zero `VUID-` entries, zero validation errors/warnings, and zero loader errors; pixel
parity remained zero-difference. The only loader warnings concern unrelated OBS and
RTSS implicit layers advertising Vulkan API 1.3 while the application requests 1.4.
See `parity-validation-core6/validation-run.log`.
