# MCVR native 本地诊断清理明细

## 边界

- 仓库：`D:\\Workspaces\\Repositories\\GitHub\\RecRivenVI\\MCVR`
- 固定对照：`develop@9905c81b1999f5845bf66d13501d371c16adf561`
- 起始证据：`Baseline/MCVR/working-content.zip`、`manifest.json`、`unstaged.patch`、`git-index.raw`
- 未执行 fetch、reset、stage、commit、install、游戏或 GUI 启动；未修改 Radiance 文件。
- 以起始 `manifest.json` 复核后，范围恰为 19 个已存在文件发生内容变化、3 个纯诊断文件缺失；未发现其他起始文件变化。

## 清理与保留

| 原审计 ID | 文件 | 已清理 | 保留的有效逻辑 |
| --- | --- | --- | --- |
| M-00124–M-00128 | `src/core/render/modules/world/dlss/dlss_module.cpp` | 删除 DLSS 输入准备、barrier 和 NGX evaluate 前后的 checkpoint 调用及结果到 marker 的分支。 | 保留所有 input/output layout 与 access barrier、资源绑定、NGX evaluate 调用和 wrapper 内 `NVSDK_NGX_Result` 检查/错误日志。 |
| M-00131, M-00136, M-00143 | `dlss_wrapper.cpp/.hpp` | 删除 `DLSS_RESOURCE_NAMES`、handle 转整数助手、`m_namedImages`、Vk debug object naming 和逐资源 info 日志；同步删除仅为日志传入的 `frameIndex` 参数及全部调用者。 | 保留尺寸/空资源校验、失败日志、resource reset、`NVSDK_NGX_Create_ImageView_Resource_VK` 与 history reset。`dlss_wrapper.cpp` 恢复起始 CRLF，避免纯换行 diff。 |
| M-00181–M-00184, M-00187, M-00190–M-00191 | `ray_tracing_module.cpp`, `world_prepare.cpp` | 删除 world prepare、SHARC、各 ray pass、BLAS/TLAS/metadata 的 begin/done checkpoint。 | 保留原有 pass 顺序、BLAS/TLAS build、barrier、buffer upload 和 SBT/SHARC 错误传播。 |
| M-00198–M-00199, M-00202, M-00207 | `shader_pack.cpp/.hpp` | 删除 `started`、`objectReuseCount`、`ShaderObjectReuse` 四处输出和 `ShaderBatchStats::objectReuseCount`。 | 保留 `shaderObjectCache_`、互斥锁、key、命中直接返回、miss 去重、并行编译、SPIR-V cache hit/miss/read-failure stats 与异常传播。 |
| M-00228–M-00234, M-00236–M-00238 | `pipeline.cpp` | 删除 WorldPipeline/module/resource/context/recreate 的 `steady_clock` 观测和全部 `RebuildTiming` 输出；删除 world module render checkpoint。 | 保留 shader pack 复用与 restart、module rebuild state capture/restore、`preClose`、context 重建、资源/曝光历史承接和 `onResourceReload`（M-00235）。 |
| M-00245, M-00249–M-00252, M-00256 | `render_framework.cpp` | 删除 frame command checkpoint、显存预算轮询和 recreate 各阶段计时/输出；删除 warmup action/elapsed 输出。 | 保留 F3 TimerQuery 的 query pool、timestamp 写入/读取、profile drain；保留 `VkResult`、双队列等待、swapchain 250ms 检查、正常帧率限制、窗口等待超时、recreate/warmup 返回值、曝光/资源历史重建。 |
| M-00310, M-00314 | `command.cpp/.hpp` | 删除 `CommandBuffer::checkpoint` 声明与实现。 | 保留 queue submit `VkResult` 返回及 `Device::recordFailure` 调用。 |
| M-00317–M-00325, M-00327–M-00328 | `device.cpp/.hpp` | 删除 diagnosticsRequested、NV diagnostic checkpoints/config、EXT device fault/address binding 扩展与 feature/create chain、checkpoint label/map、object naming、queue checkpoint 和 fault dump。 | 保留 pipeline cache 构建/回退、graphics/compute/ray tracing wrappers、析构 wait 结果记录、首个真实失败的 `VkResult`/operation 保存与读取。M-00316 的非诊断 device 扩展过滤逻辑未动。 |
| M-00339–M-00347 | `instance.cpp/.hpp` | 删除 `MCVR_VULKAN_DIAGNOSTICS`/`MCVR_VULKAN_VALIDATION`、Release debug-utils 注入、validation/address-binding messengers、tracker ownership/callback/API。 | 保留固定 HEAD 已有的 `DEBUG_LAYER`、`debugCallback`、DEBUG 扩展/日志；保留实例句柄安全析构。另保留 DEBUG 下 validation layer 可用性判断，仅在层存在时写入 createInfo，避免调试构建因缺层改变初始化失败边界。 |
| M-00367–M-00369 | `vma.cpp/.hpp`, `buffer.cpp` | 删除 device-local heap budget 采样、锁、节流状态、启动/帧/分配失败预算日志。 | 保留 VMA allocator 安全析构；分配失败仍抛出含 `VkResult`、buffer kind 和 size 的异常。 |
| M-00613–M-00614 | `address_binding_tracker.cpp/.hpp` | 两个纯本地诊断文件送入 Windows 回收站。 | 无生产逻辑需要承接；起始内容仍在基线 zip/manifest 中可恢复。 |
| M-00644, M-00649 | `tests/CMakeLists.txt`, `verify_device_address_diagnostics_contract.cmake` | 删除专用 `mcvr.device-address-diagnostics` CTest 注册，脚本送入 Windows 回收站。 | 保留其余一方测试；F3 GPU profile marker 仍由 `mcvr.vanilla-effects-contract` 验证。 |

## 明确保留的诊断/日志边界

- `RendererProxy_backendString` 的 `Unsupported Vulkan diagnostic query` 是 Blaze3D/F3 的后端字符串查询错误文本；不是本地 GPU 排查生产者。
- `beginGpuProfile/isGpuProfileReady/gpuProfileTimeNs`、`VK_QUERY_TYPE_TIMESTAMP`、`vkCmdWriteTimestamp` 与 `completeGpuProfile` 是原版 TimerQuery/F3 的真实 consumer；按“不因 debug 字样删除 F3”保留。
- 固定 HEAD 已存在的 `#ifdef DEBUG` GLFW/DLSS/XeSS/extension/instance/VMA 日志保留。
- pipeline cache 的 load/status/save 日志、SPIR-V cache stats、NGX callback/API failure、invalid DLSS resource、首个 `VkResult` 失败记录属于缓存状态或真实失败边界，未作为本地排查探针删除。
- `Pipeline::nativeRebuildActive` 由 Radiance 的管线配置界面消费，继续表示重建状态；它不再输出 rebuild timing。

## 验证

- `git diff --check -- CMakeLists.txt src tests`：PASS。
- Release：`cmake --build build --config Release --target core --parallel 8`：PASS。
- CTest：`ctest --test-dir build -C Release --output-on-failure`：17/17 PASS；`mcvr.exposure-gpu` 实际 PASS。
- Release DLL：`build/src/core/Release/core.dll`，30,645,248 bytes，SHA-256 `A0CFF9BD3C9E76AA33EA60E3D34D7784CCA67BBC529CF847F36FA984D7D42030`。
- Import library：`build/src/core/Release/core.lib`，55,346 bytes，SHA-256 `A176B2A988F38A2A76BA53D063E6F512FF512D3663310F7518AB652ABC10F0B0`；导出集合未变化，因此文件未重写。
- Debug `ClCompile`：PASS，覆盖 `instance.cpp` 的 validation-layer 条件编译路径；完整 Debug core 在链接阶段 DEGRADED，现有 Vulkan SDK `shaderc_combined.lib` 是 Release CRT（`MD_DynamicRelease`/`_ITERATOR_DEBUG_LEVEL=0`），与 Debug `/MDd`（level 2）产生 484 个 LNK2038，最终 LNK1319。未修改构建配置或依赖来绕开。
- CTest 中 16 个是编译/静态 contract 或结构检查；只有 `mcvr.exposure-gpu` 是本轮实际 Vulkan GPU 执行测试。没有把 17/17 统一表述为运行时画面验收。

原始日志：

- `Evidence/native-release-build.log`
- `Evidence/native-ctest-release.log`
- `Evidence/native-debug-compile.log`
- `Evidence/native-debug-build.log`
- `Evidence/native-final-audit.log`

## 残留与限制

- 未运行游戏、GUI 或 Prism；本轮证明源码清理、链接与既有 CTest，不宣称启动、画面或运行时性能验收。
- 未实施 MC04.2 材质裁决，未改变 Veil/Flywheel 绕过，未改视觉效果或渲染阶段顺序。
- 工作树仍包含用户原有的大量未提交实现；本轮没有 stage/commit/push。
- 本报告落盘后 source 已冻结；后续仅由根任务复制最终 Release DLL 到已备份的 ignored runtime 资源并重做分发核验。
