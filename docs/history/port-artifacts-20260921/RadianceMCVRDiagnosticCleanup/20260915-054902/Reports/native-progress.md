# MCVR native 诊断清理进度

- 固定仓库：`D:\\Workspaces\\Repositories\\GitHub\\RecRivenVI\\MCVR`
- 固定对照：`develop@9905c81b1999f5845bf66d13501d371c16adf561`
- Git 边界：不 fetch、不 reset、不 stage、不 commit；不修改 Radiance。
- 基线证据：`Baseline/MCVR/working-content.zip`、`manifest.json`、`unstaged.patch`、`git-index.raw` 均已存在。

## 当前状态

- [x] 阅读 `04-诊断与绕过边界.md` 及 r2 `C-技术核验与修复事项.md` 的 DIAG 边界。
- [x] 确认 MCVR HEAD、分支、worktree 与起始 dirty 状态。
- [x] 建立第一轮 native/CMake/tests 诊断符号清单。
- [x] 移除 checkpoint 的调用、Command/Device 声明与存储。
- [x] 移除 Device/Instance/device-fault/address-binding 本地排查链。
- [x] 移除 pipeline/rebuild/warmup timing 输出，保留重建、预热、曝光历史与返回值。
- [x] 移除 ShaderObjectReuse 统计输出及 `objectReuseCount` 字段，保留 shaderObjectCache 与异常处理。
- [x] 移除 device-local budget 监测输出，保留分配失败的 VkResult 与 size 异常。
- [x] 将 `address_binding_tracker.{cpp,hpp}` 与专用 `verify_device_address_diagnostics_contract.cmake` 送入 Windows 回收站，并移除其 CTest 注册。
- [x] 全仓残留扫描、Release core 构建、Release CTest、Debug 条件编译与 final audit。

## 明确保留

- F3 TimerQuery 使用的 `beginGpuProfile/isGpuProfileReady/gpuProfileTimeNs` 及 Vulkan timestamp query 实现。
- 所有有效 `VkResult` 传播、barrier/同步/生命周期、正常帧节流与 swapchain 超时计时。
- pipeline cache、shader object cache、warmup 本体、资源/曝光历史迁移。
- 上游 `DEBUG` 日志与 validation 路径仅在逐块核对固定 HEAD 后判定。

## JNI 协调

- 当前未发现必须跨仓移除的 Java JNI 接口；GPU profile JNI 明确保留。

## 最终状态

- source 已冻结；起始 manifest 对比仅有 19 个目标文件变化和 3 个已回收纯诊断文件，无其他越界变化。
- Release core：PASS；最终 DLL SHA-256 `A0CFF9BD3C9E76AA33EA60E3D34D7784CCA67BBC529CF847F36FA984D7D42030`。
- Release CTest：17/17 PASS；其中 16 个为静态/contract，`mcvr.exposure-gpu` 为真实 Vulkan GPU 测试。
- Debug `ClCompile`：PASS；完整 Debug 链接 DEGRADED，原因是现有 Vulkan SDK `shaderc_combined.lib` 的 Release CRT 与 `/MDd` 不匹配（484 个 LNK2038、LNK1319），不是本批源码编译错误。
- `Pipeline.isNativeRebuildActive` 仍由 Java 管线配置界面消费，按有效重建状态保留；其本身不输出 timing。
- 原始日志见 `Evidence/native-*.log`，完整明细见 `native-cleanup-details.md`。
