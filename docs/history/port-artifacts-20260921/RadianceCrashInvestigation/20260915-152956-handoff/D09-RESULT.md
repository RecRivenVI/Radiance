# D09 结果：真实文字 shader + D32S8 深度/模板附件

## 结果

- 状态：`NOT_REPRODUCED_IN_FIXTURE_DEPTH_STENCIL`。
- GPU：NVIDIA GeForce RTX 4080 SUPER；driver `2585198592`。
- `keep`：40.0015 s，54,673 frames，1,749,536 indexed draws，2,624,304 像素检查，0 VVL error，11 条显式过滤层提示，退出码 0。
- `all`：40.0002 s，53,976 frames，1,727,232 indexed draws，2,590,848 像素检查，0 VVL error，11 条显式过滤层提示，退出码 0。
- 合计：108,649 frames，3,476,768 indexed draws，5,215,152 像素检查，0 VVL error，0 访问异常。
- 两组 warmup 像素检查均通过；11 条提示是 `VK_LOADER_LAYERS_DISABLE=~implicit~` 的层过滤提示，不是 validation VUID。

## 对照范围

- 保留 D08 的 `rendertype_text` byte-for-byte shader、192-byte std140 uniform、PositionColorTexLight 28-byte vertex、uint16 index、双动态 UBO offset、descriptor array 4096 项、表释放策略和 22 项 EXT 动态状态。
- 新增 `VK_FORMAT_D32_SFLOAT_S8_UINT`、depth|stencil 双 aspect image、combined color+depth/stencil render pass、D32S8 clear/transition，pipeline 使用 depth/stencil state。
- D09 仍是独立无窗口程序，使用自身 fence 等待和 8x6 读回；它不复现真实游戏的 world producer、多 render target、Java 调用节奏或窗口 swapchain。

## 产物哈希

- `repro.cpp`：`730B28AFA16752DCBCE589BAF5D85C784D189D2F67A4A334159BE52E096CC2C2`
- `repro.exe`：`89181CDDE1D6774B81D2A68AD424CA6E567913DB3DCAC09DB3B9A468D2CF9810`
- `repro.pdb`：`C92266A0CC81806E80344AB96B2F97B2CA7A7C1F18A8B74217BA292A95ADDF0D`
- `vert.spv`：`F7E5AA9E209DA502AE8F2C8A3D8E229BE050AAC7C4AC38425022687A2D04B71E`
- `frag.spv`：`80D9B6F42BB8F6D70A31C060B620E33DAA0FA817DE7E8DFE864386636DEB6937`

## 结论与清理

D32S8 附件不是该独立文字绘制路径触发驱动 CPU 异常的必要条件。该负结果不能证明真实游戏的附件生命周期、跨 pass 状态或此前提交污染无误，也不能作为正式修复。D09 scratch 及日志暂保留，待根因定位或正式交付清理阶段按台账移入回收站；正式源码没有因 D09 改动。
