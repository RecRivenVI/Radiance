# D11 结果：真实尺寸/用途纹理对的稀疏 descriptor 对照

## 结果

- 状态：`NOT_REPRODUCED_IN_FIXTURE_REAL_TEXTURE_PAIR`。
- GPU：NVIDIA GeForce RTX 4080 SUPER；driver `2585198592`。
- `keep`：40.0007 s，42,084 frames，1,346,688 indexed draws，2,020,032 像素检查，0 VVL error，11 条显式过滤层提示，退出码 0。
- `all`：40.0021 s，44,038 frames，1,409,216 indexed draws，2,113,824 像素检查，0 VVL error，11 条显式过滤层提示，退出码 0。
- 合计：86,122 frames，2,755,904 indexed draws，4,133,856 像素检查，0 VVL error，0 访问异常。
- 两组 warmup 像素检查均通过；11 条提示是 `VK_LOADER_LAYERS_DISABLE=~implicit~` 的层过滤提示，不是 validation VUID。

## 对照范围

- 继承 D10 的实际 `rendertype_text` SPIR-V、192-byte std140 uniform、PositionColorTexLight 28-byte vertex、uint16 index、D32S8 combined attachment、22 项动态状态、双动态 UBO、fence 前后资源释放和 keep/all 生命周期。
- 保持 set0/binding0 的 0/1/2 空洞及 3..106 占用；uniform 继续使用 19/61。索引 19 指向 16x16、索引 61 指向 256x256 的 `R8G8B8A8_UNORM` 纹理；两者均使用 sampled + transfer src/dst（`usage=0x7`）。sampler 参数为 nearest、repeat U/V/W、关闭 anisotropy、`maxLod=1000`，与 D06 现场记录一致。
- 仍是独立无窗口程序，未包含真实游戏的 Java 调用节奏、多 render target、world producer、窗口 swapchain 和完整历史命令序列。

## 产物哈希

- `repro.cpp`：`29F888696374CD4FFD1B021E8A1BC523940CF1FA6BFBB777D587B5751CEB16D7`
- `repro.exe`：`96836A133DC9FAC2D20E76E42E7A6E62171C3A2D229904BBEAA8A5808F0157D8`
- `repro.pdb`：`943E422ABDADB6DD1645E3614223E9213D53C551DCF27AEAAEDB90A233B1727F`
- `vert.spv`：`F7E5AA9E209DA502AE8F2C8A3D8E229BE050AAC7C4AC38425022687A2D04B71E`
- `frag.spv`：`80D9B6F42BB8F6D70A31C060B620E33DAA0FA817DE7E8DFE864386636DEB6937`
- `text_structs.inc`：`07A4EAEA426A51FBF1C57A39A7E8FD7740C48E55F052512BCEBEB9D1E3547129`
- `text_render.inc`：`90B306784EB09EB9749249A57D9277CB3A4952D0CE83A64B304A19D85AE0F5AB`

## 结论与清理

D06 现场的纹理尺寸/用途、sampler 参数与稀疏 descriptor 形态在该独立 fixture 中仍未触发 NVIDIA CPU 访问异常。因此，简单的 descriptor 越界、低索引空洞、当前纹理尺寸/usage 或 sampler 参数均没有被该组证据单独支持为根因；D06 的真实命令历史、跨帧状态污染和真实资源关系仍未排除。D11 scratch 及日志暂保留，待根因定位或正式交付清理阶段按台账移入 Windows 回收站；正式源码没有因 D11 改动。
