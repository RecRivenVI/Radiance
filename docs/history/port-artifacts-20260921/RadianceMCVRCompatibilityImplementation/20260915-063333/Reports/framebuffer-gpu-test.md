# 无窗口 Vulkan framebuffer 原语回归测试

## 实现

- 新增 `MCVR/tests/framebuffer_gpu_test.cpp`。
- 在 `tests/CMakeLists.txt` 注册 `mcvr.framebuffer-gpu`，依赖正式 `shaders` target，并传入：
  - `clear_vert.spv`
  - `clear_frag.spv`
  - `clear_2_frag.spv`
- 测试只创建 Vulkan instance/device/graphics queue、image/buffer/render pass/pipeline/command buffer/fence；不创建 surface、GLFW window、游戏或 GUI。
- 没有修改生产源码或 `src/shader/CMakeLists.txt`。

## 实测设备

- GPU：NVIDIA GeForce RTX 4080 SUPER
- Vulkan API：1.4.351
- 测试进程：`build/tests/Release/mcvr_framebuffer_gpu_test.exe`
- CTest：`mcvr.framebuffer-gpu`，0.46 秒，PASS。

## 四个 GPU case

1. **Scissor + partial color write mask**
   - RGBA8 image 先用 transfer clear 写入已知 `(0.1, 0.2, 0.3, 0.4)`。
   - 正式 `clear_vert.spv + clear_frag.spv` 绘制 fullscreen triangle。
   - 使用与生产算法相同的 `CONSTANT_COLOR/CONSTANT_ALPHA + ZERO` replacement blend。
   - scissor 为 `(2,1) 3x2`，write mask 仅 R/B。
   - GPU readback 证明区域内只把 R/B 改为 blend constants，G/A 保留；区域外四通道不变。

2. **MRT 两附件**
   - 创建两个独立 RGBA8 attachment，并以不同初始颜色填充。
   - 使用正式 `clear_2_frag.spv`，同一 subpass 两个 color outputs。
   - GPU readback 证明两个 attachment 都收到完整 clear value，不是只写 attachment 0。

3. **Depth + stencil mask**
   - 实测设备支持 `VK_FORMAT_D32_SFLOAT_S8_UINT` 的 attachment 与 transfer src/dst，因此该 case 没有 skip。
   - image 初始 depth=`0.25`、stencil=`0xAA`。
   - fullscreen triangle viewport `minDepth=maxDepth=0.75`，depth compare `ALWAYS`、write enabled。
   - stencil op 为 `REPLACE`，reference=`0x55`，write mask=`0x0F`，scissor 为中间 2x2。
   - 分别 copy depth/stencil aspect 回 host；覆盖区得到 depth=`0.75`、stencil=`0xA5`，区域外仍为 `0.25/0xAA`。这直接证明未覆盖 stencil 高半字节得到保留。

4. **Color region blit + flip**
   - source 4x4 用逐像素唯一 RG 值初始化，destination 清零。
   - `vkCmdBlitImage` 只复制 source `(1,1)-(3,3)`，destination X offsets 反向 `(3,1)-(1,3)`。
   - readback 证明目标 2x2 区域水平翻转正确，区域外仍为零。

## 与生产算法的对应边界

- clear 使用正式生产 SPIR-V，而不是 CPU 模拟或测试专用 shader。
- blend factors、color write mask、depth viewport 值、depth compare/write、stencil replace/reference/write mask 与 `UIModuleContext::clearMaskedFramebuffer` 一致。
- 测试用 fixed pipeline state 表达部分生产动态 state；它验证这些 Vulkan 原语组合的 GPU 结果，但不覆盖 `vkCmdSetColorBlend*EXT/vkCmdSetColorWriteMaskEXT` 的设备扩展调度，也不覆盖生产 cache、Snapshot、RenderPassBuilder 或 active-pass 协调。
- blit case 验证区域与翻转的 Vulkan primitive；不覆盖 Java top-left 坐标换算、`clipAxis` 或 JNI 参数转发。
- 本轮未发现生产原语算法与断言冲突，没有放宽任何预期。

## Skip 与失败语义

- Vulkan 1.4 loader、physical device、graphics queue或所需 memory type缺失时，进程输出明确 `[SKIP] reason` 并返回 CTest skip code 77。
- D32F_S8 attachment/transfer capability单独不足时，只输出明确的 depth/stencil case skip reason；其他真实 color/MRT/blit case仍运行。当前实测设备未触发该分支。
- shader打不开、SPIR-V 非 word aligned、任何 Vulkan API失败或任何像素不符都会返回 1；没有静默成功路径。

## 证据

- 构建日志：`Evidence/framebuffer-gpu-build.log`
- 直接 GPU 运行日志：`Evidence/framebuffer-gpu-run.log`
- 定向 CTest 日志：`Evidence/framebuffer-gpu-ctest.log`
- `clear_vert.spv`：1,144 bytes，SHA-256 `75252E952D97988224201DF66612E3D2CDF8D09E2A10DB4D5274EFE39A4DF9E4`
- `clear_frag.spv`：732 bytes，SHA-256 `07A158CFA0C15D50DAF544AE24C359604DFD8B37CC9D9085B1DE1F638E24807B`
- `clear_2_frag.spv`：748 bytes，SHA-256 `53FECE1B7B46D929DB84CEBF53627292DA3C2D1275F94E4947CC657F912788C2`
- 测试 EXE：75,776 bytes，SHA-256 `875312783F829D2E147432BAB8F7E355D51BD3CB5261A21E042613DD6E7E6C22`
- `git diff --check`：PASS；未 stage/commit/push。

## 根任务集成清单

- 构建：`cmake --build build --config Release --target shaders mcvr_framebuffer_gpu_test --parallel 8`
- 定向运行：`ctest --test-dir build -C Release -R ^mcvr\\.framebuffer-gpu$ --output-on-failure`
- 全套 CTest 可保留该 test 的 `SKIP_RETURN_CODE 77`；报告时需区分 whole-test skip 与仅 depth/stencil case skip。
- 后续仍需 Java → JNI → Framebuffers Snapshot → UIModule active pass 的端到端测试和游戏画面验收。

