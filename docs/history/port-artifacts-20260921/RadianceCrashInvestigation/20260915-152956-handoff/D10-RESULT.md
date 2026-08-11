# D10 结果：生产式稀疏非零 descriptor 索引

## 结果

- 状态：`NOT_REPRODUCED_IN_FIXTURE_SPARSE_NONZERO_IDS`。
- GPU：NVIDIA GeForce RTX 4080 SUPER；driver `2585198592`。
- `keep`：40.0005 s，52,078 frames，1,666,496 indexed draws，2,499,744 像素检查，0 VVL error，11 条显式过滤层提示，退出码 0。
- `all`：40.0008 s，47,847 frames，1,531,104 indexed draws，2,296,656 像素检查，0 VVL error，11 条显式过滤层提示，退出码 0。
- 合计：99,925 frames，3,197,600 indexed draws，4,796,400 像素检查，0 VVL error，0 访问异常。
- 两组 warmup 像素检查均通过；11 条提示是 `VK_LOADER_LAYERS_DISABLE=~implicit~` 的层过滤提示，不是 validation VUID。

## 对照范围

- 继承 D09 的实际 `rendertype_text` SPIR-V、192-byte std140 uniform、PositionColorTexLight 28-byte vertex、uint16 index、D32S8 combined attachment、22 项动态状态、双动态 UBO、fence 前后资源释放和 keep/all 生命周期。
- set0/binding0 为 4096 项数组；索引 0/1/2 保持未写入，索引 3..106 逐项写入；文字 uniform 在 19 和 61 之间交替取样，覆盖 D06 的低索引空洞与非零动态索引形态。
- 仍是独立无窗口程序，未包含真实游戏的 Java 调用节奏、多 render target、world producer、窗口 swapchain 和完整历史命令序列。

## 产物哈希

- `repro.cpp`：`95C82C31C3BF554D8ABA27509C8448F6BA1F931002911685B7C2364FF9D70062`
- `repro.exe`：`F3F96A1938C24F426D5A41460DB0D91AA38E687C106021D0630256D9564AFB04`
- `repro.pdb`：`6F8A6F9C6B5E4736BF9FD18E3E61ACBF103AD75E0D7A646A89CFCC36E8CDC1DD`
- `vert.spv`：`F7E5AA9E209DA502AE8F2C8A3D8E229BE050AAC7C4AC38425022687A2D04B71E`
- `frag.spv`：`80D9B6F42BB8F6D70A31C060B620E33DAA0FA817DE7E8DFE864386636DEB6937`
- `text_structs.inc`：`07A4EAEA426A51FBF1C57A39A7E8FD7740C48E55F052512BCEBEB9D1E3547129`
- `text_render.inc`：`90B306784EB09EB9749249A57D9277CB3A4952D0CE83A64B304A19D85AE0F5AB`

## 结论与清理

“低索引空洞 + 3..106 稀疏写入 + 19/61 非零取样”在该独立 fixture 中不是访问异常的必要条件。该负结果不能证明真实游戏 descriptor 更新、命令历史或资源生命周期正确，也不能作为正式修复。D10 scratch 及日志暂保留，待根因定位或正式交付清理阶段按台账移入 Windows 回收站；正式源码没有因 D10 改动。
