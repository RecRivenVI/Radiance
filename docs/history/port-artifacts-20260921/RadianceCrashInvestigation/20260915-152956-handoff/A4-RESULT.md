# A4 结果：同产物无诊断真实运行

## 结论

`CONFIRMED_REPRODUCED_IN_REAL_GAME_NO_DIAGNOSTICS`。A4 在没有 Vulkan layer、validation、API dump、GPU-assisted validation 或本轮新增行为调整的条件下，仍发生与 D06 相同的 NVIDIA 原生访问异常。原崩溃未解决，R-01/R-02 继续保留；本结果不是正式修复或性能验收通过。

用户直接报告：本次 A4 未进行任何操作，游戏未能加载/显示主界面即崩溃。该报告与 fatal 栈中的首次 `TitleScreen.render` 文字绘制路径相互一致；不需要从日志推断菜单、世界或暂停操作。

## 运行与归属

- 启动：2026-09-15 17:13:02.0628367 +08:00。
- JVM fatal 报告时间：2026-09-15 17:13:30，报告 elapsed time `28.247038 s`；这是应用运行到崩溃的时间。
- 启动器等待转储写完并结束：2026-09-15 17:14:43.5354018 +08:00，包装器总时长 `101.4725651 s`，不能当作游戏运行时长。
- Java PID `73724`，监控/启动父 PID `91172`；检查结束时两者均已退出，没有发出终止操作。
- 隔离目录：`D:\Workspaces\Artifacts\RadianceCrashInvestigation\20260915-A\Diagnostic\base`。
- 运行目录：`D:\Workspaces\Artifacts\RadianceCrashInvestigation\20260915-A\Evidence\run-a4-20260915-171302-062`。

## 实际加载产物与诊断配置

- `Radiance.jar`（base 与产品）：`21CF6C1451B4021ADE6AEB53A9DFB931A40EFBE15B93C3D5D77FB34FC7B0D550`。
- 实际加载 `core.dll` 与产品：`20F85170B43A822D93BFBD9FB12770CE5CCD78E2FAFC9B8B5E1B6AA62374BD89`。
- `core.pdb`：`E177D9DE386308D3B99F112244AC02C47B1E94E5BBB32F64F8824052C081B2BA`。
- 启动脚本 `Run-A4-NoDiagnostics.ps1`：`AAD7E58AFBAD31DDEEEAF0719954C73C5EF13958FDE6C5E358824952494373FD`。
- `VulkanLayers=[]`；validation、sync validation、GPUAV、DebugPrintf、best-practices、API dump 均为关闭。
- 仅在 A4 子进程环境移除临时 `VK_*` 诊断变量；没有屏蔽 OBS/RTSS，没有强制等待、额外同步、延迟释放、跳过绘制或关闭文字。

## 崩溃证据

- 异常：`EXCEPTION_ACCESS_VIOLATION (0xc0000005)`，读取地址 `0xffffffffffffffff`。
- 问题帧：`nvoglv64.dll+0xf1c729`，与 D06 地址一致。
- 当前线程：Java `Render thread`，处于 `_thread_in_native`。
- Java 链：`ShaderProxy.draw` → `RasterDrawBridge.draw/drawApplied/drawWithShader` → `BufferUploader` 的 draw rewrite → `GuiGraphics.flush/drawString` → `AbstractButton`/`Screen` → `TitleScreen.render`。
- native 栈只有 `nvoglv64.dll+0xf1c729`；没有 validation VUID、API dump 调用序列或 `VK_ERROR_DEVICE_LOST` 可供关联。
- 新大型 JVM 转储原位保留：`D:\Workspaces\Artifacts\RadianceCrashInvestigation\20260915-A\Diagnostic\base\hs_err_pid73724.mdmp`，`6,328,407,108` bytes，未复制；当前环境无可用 native dump 调试器，因此不对其中尚未读取的内容作额外推断。
- fatal 文本副本：`Evidence\run-a4-20260915-171302-062\hs_err_pid73724.log`。
- 当前环境未找到 `cdb`、WinDbg 或 `dumpchk` 可执行文件；本次离线检查以 JVM fatal 文本、寄存器/Java 栈和现有 D06 对照为依据，没有恢复回收站中的旧调试器或修改环境。

## 当前源码调用点对照

- `Radiance/src/main/java/com/radiance/client/render/RasterDrawBridge.java:20-68` 在 render thread 上创建/获取 shader uniform，并调用 `ShaderProxy.draw`。
- `Radiance/src/main/java/com/radiance/mixins/vulkan_render_integration/BufferRendererMixins.java:17-34` 将 Minecraft 的 `BufferUploader._drawWithShader` 路由到该 bridge。
- `MCVR/src/core/middleware/com_radiance_client_proxy_vulkan_ShaderProxy.cpp:173-180` 获取当前 pipeline/UI context、追加 overlay uniform 后进入 `UIModuleContext::drawIndexed`；`MCVR/src/core/render/modules/ui_module.cpp:1973-2008` 绑定 pipeline、descriptor set、vertex/index buffer 并调用 `drawIndexed`，最终落到 `vkCmdDrawIndexed`。
- 这与 A4 fatal 报告的 Java 路径一致，但不能仅凭崩溃点证明传入的某个 buffer、descriptor、uniform 或资源生命周期已错误。

## 封存文件哈希

以下是运行目录中小型证据文件的 SHA-256；大型 `.mdmp` 只记录路径和长度，未进行不必要的全量哈希：

| 文件 | SHA-256 |
|---|---|
| `process.json` | `77C1C31999A0E28B911958B3759414A5B9A701118DDC2C2CB7AD5D7A474D57A1` |
| `run-result.json` | `CE6060DFBBF6183DA8384FF66C9F37773FECB86ED747222FCC9CBA3A0C4A967A` |
| `hs_err_pid73724.log` | `391CF00D4AAA882CCA1DB81541840058DF7A8A99716490E1DB67CDC05D8ABECD` |
| `console.stdout.log` | `9582830FD3F2A581023A2AAE7BD98F56634B3246BA3EB603580E8647AF5776A6` |
| `console.stderr.log` | `E579233B6AAFCA91B906845BC217892F10BAD1443573E4D70EA35599EEEAE850` |
| `latest.log` | `00727A79B81F806366D31C8C94248EA38EF480066026935BA41AD1723CC89744` |

## 后续边界

- 本轮不自动重启、不新增 D12 或其他独立 fixture，不修改源代码、驱动、Prism 设置、存档或其他实例。
- A4 证明“无诊断层导致崩溃”的解释不成立，但没有区分 descriptor/UBO/资源生命周期、命令历史和 NVIDIA 驱动内部状态的具体原因。
- 下一步应先取得用户对 A4 实际操作的直接说明，再对 A4 转储与 D06 转储做离线对比；只有出现可重复的应用侧不变量，才在隔离副本内做有界代码修复或对照。
