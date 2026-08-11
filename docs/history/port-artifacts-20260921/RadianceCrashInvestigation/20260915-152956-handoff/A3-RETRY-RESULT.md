# A3 重试结果：validation 配置下再次发生 NVIDIA 原生崩溃

## 结论

`CONFIRMED_REPRODUCED_IN_REAL_GAME_WITH_VALIDATION`。A3 重试在加载 `VK_LAYER_KHRONOS_validation`、开启 `validate_sync=true` 的条件下，仍在 `nvoglv64.dll+0xf1c729` 发生访问异常；fatal frame 不是 `VkLayer_khronos_validation.dll`。因此 validation/sync validation 既不是本次崩溃的唯一原因，也没有构成修复。与 A4 的无诊断崩溃结合，原生问题已在两种配置下复现，根因仍未确认。

用户没有在本记录中报告本次 A3 重试的具体操作序列；不从日志推断是否看见主界面或点击了什么。

## 运行与归属

- 启动：2026-09-15 17:37:16.4688399 +08:00。
- JVM fatal 时间：2026-09-15 17:37:42，报告 elapsed time `25.512750 s`。
- 启动器结束：2026-09-15 17:38:57.8095632 +08:00；包装器总时长约 `101.3407233 s`，包含写入大型转储，不作为游戏运行时长。
- Java PID `87964`，父监控 PID `73736`；结果收集时均已退出，未发出终止操作。
- 隔离目录：`D:\Workspaces\Artifacts\RadianceCrashInvestigation\20260915-A\Diagnostic\base`。
- 运行目录：`D:\Workspaces\Artifacts\RadianceCrashInvestigation\20260915-A\Evidence\run-a3-20260915-173716-468`。

## 产物与诊断配置

- `Radiance.jar`：`21CF6C1451B4021ADE6AEB53A9DFB931A40EFBE15B93C3D5D77FB34FC7B0D550`。
- 实际加载 `core.dll`：`20F85170B43A822D93BFBD9FB12770CE5CCD78E2FAFC9B8B5E1B6AA62374BD89`。
- `core.pdb`：`E177D9DE386308D3B99F112244AC02C47B1E94E5BBB32F64F8824052C081B2BA`。
- 加载 `VK_LAYER_KHRONOS_validation`；`validate_sync=true`；error/warn 日志，重复消息上限 1。
- API dump、GPUAV、DebugPrintf、best-practices 均关闭；没有强制等待、额外同步、延迟释放、跳过绘制、关闭文字或 OBS/RTSS 屏蔽。

## 崩溃与先行观察

- 异常：`EXCEPTION_ACCESS_VIOLATION (0xc0000005)`，读取 `0xffffffffffffffff`。
- 当前线程：Java `Render thread`，处于 `_thread_in_native`。
- native 问题帧：`nvoglv64.dll+0xf1c729`，与 D06、A4 完全相同。
- Java 链：`ShaderProxy.draw` → `RasterDrawBridge.draw` → `BufferUploader` draw rewrite → `GuiGraphics.drawString` → `AbstractButton` → `TitleScreen.render`。
- fatal 前 stdout 记录一条 `SYNC-HAZARD-WRITE-AFTER-WRITE`：`vkCmdPipelineBarrier2` 的颜色图像写入与先前另一 command buffer 的 `vkCmdEndRenderPass` 写入之间缺少 `COLOR_ATTACHMENT_OUTPUT` 阶段的充分依赖。这是与先前 A3 相同的同步观察线索，不是已经证明的 AV 根因。
- 未看到 `VkLayer_khronos_validation.dll` 作为问题帧，也未记录 `VK_ERROR_DEVICE_LOST`。
- 新大型 JVM 转储原位保留：`D:\Workspaces\Artifacts\RadianceCrashInvestigation\20260915-A\Diagnostic\base\hs_err_pid87964.mdmp`，`6,602,860,986` bytes，未复制。

## 证据文件哈希

| 文件 | SHA-256 |
|---|---|
| `process.json` | `44816D16301B6D593D834B9ADA9061431E6027E5F57320A9A394624FD97AA371` |
| `run-result.json` | `962059791BD563131D715B434E62D564AF59D5B7D7EFA33C8D60BB0B6C835E32` |
| `hs_err_pid87964.log` | `A089E0EF8FEFDBE2B0BF79AF135FC7CF43FFF43BC7E42C0DDC92FEDEEE315F6A` |
| `console.stdout.log` | `1924C353EC83C15273FD92672A63D30B74C04AA79ED15AE95EA9181BBC95966A` |
| `console.stderr.log` | `395C8BBA40AB8DF43CEA8CCA2A9FBFD9C87F183582F9BC7CF894784BE85E04E1` |
| `latest.log` | `B45A8C82D365A52886ECD69CBB4EFF4DF779C8E8318AEEF237F1C6EB7D56664A` |

## 与 A4 的含义

- A4：无 Vulkan 诊断 layer，约 `28.247038 s` 后同一驱动偏移崩溃。
- A3 重试：有 validation/sync validation，约 `25.512750 s` 后同一驱动偏移崩溃。
- 两次均使用同一 JAR/DLL/PDB，均位于实际文字绘制的 `TitleScreen.render` 路径；这使“只在无诊断配置下才崩溃”不成立。
- A3 重试同时提供了崩溃前的真实 sync hazard，但缺少 API 全量调用和对象关联，不能把该 hazard 直接升级为正式修复依据。

## 后续边界

- 不自动重启、不新增 D12 或同类独立 fixture；不修改代码、驱动、Prism 设置、存档或其他实例。
- 先保存并离线比较 A3 重试、A4 和 D06 的 fatal/同步证据；若要继续修改，必须先把同步 hazard 关联到明确的生产代码不变量。
