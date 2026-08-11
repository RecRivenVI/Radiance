# Resource-lifetime A4 result

运行：`run-resource-lifetime-a4-20260915-202344-080`

- Java PID：`80964`
- 开始：`2026-09-15T20:23:44.0808553+08:00`；JVM fatal elapsed：`23.995901 s`；采集器结束：`2026-09-15T20:25:55.2735595+08:00`。
- 进程在用户接管前发生 fatal；没有收到用户提供的菜单、移动、暂停、退出或其他操作序列，因此不对用户操作作推断。
- 使用 `--quickPlaySingleplayer 新的世界`；日志在 fatal 前已记录进入集成世界并登录玩家。
- 实际 JAR SHA-256：`9D8BF6028C6C00604C6A38DD23FDA2E9B19F140F9C4D022D92E97F45D3A4D53B`。
- 实际加载 `core.dll` SHA-256：`96E59835DFBB7E85CCCBE431A07F4F4E4F4DB7625122DA064793B55232465643`。
- 匹配 PDB SHA-256：`FEEF6F9D1A4DBB155DCC656242D6E40052DC03AEE4FF4A286A2DE1840F63B26A`。
- 配置：A4；没有显式 Vulkan validation/synchronization layer、API dump、GPU-assisted、DebugPrintf 或 best-practices；启动器清除了相关显式层环境变量，但没有证据据此断言系统不存在隐式层。
- 没有强制等待、延迟释放、跳过绘制、关闭文字、E-02 或第三方层屏蔽。

## Fatal evidence

- `hs_err_pid80964.log`：`208,423` bytes，SHA-256 `417F87BB05FE3F4B26EE5BA7674B0AF299F7AA0D308B69CAEECE94EC3AAF84C5`。
- 完整转储原位保留：`D:\Workspaces\Artifacts\RadianceCrashInvestigation\20260915-192109-handoff\Diagnostic\resource-lifetime-A\hs_err_pid80964.mdmp`，`9,359,600,817` bytes；未复制。
- JVM fatal：`EXCEPTION_ACCESS_VIOLATION`，问题帧 `nvoglv64.dll+0xf1c708`，读取地址 `0x0000000000000104`。
- Java 栈：`ShaderProxy.draw` → `RasterDrawBridge.draw` → `BufferUploader` → `GuiGraphics.drawString` → `SubtitleOverlay.render`。
- 这与资源寿命 A3 的 `+0xf1c708` / `0x104` 完全同一故障变体，但仍不是原始 `+0xf1c729` / `0xffffffffffffffff` 精确签名；结果分类器为 `OTHER_FATAL`，不是 `SAME_NATIVE_AV`。
- A4 没有显式 validation，因此日志中没有 `VUID-`、`Validation Error`、`SYNC-HAZARD`、`WRITE_AFTER_WRITE` 或 `READ_AFTER_WRITE`，不能据此证明 API 使用正确或错误。

## Interpretation boundary

A3（validation + synchronization validation）和 A4（未显式启用这些诊断）在相同资源寿命产物上，都在用户接管前的真实文字绘制路径触发了同一 `+0xf1c708` / `0x104` 变体；触发位置分别是 `ReceivingLevelScreen` 与 `SubtitleOverlay`。这说明该变体不依赖显式 validation 层才能出现，也使“仅由 validation 开销导致”缺乏支持，但仍不能证明 R-LIFE-01 是根因。

本次没有记录 `releaseTexture()`、旧 descriptor 表、资源代际或 frame fence 的关联事件，因此资源寿命假设仍然是“证据不足”，不是已排除或已确认修复。A4 的采集器结束时间包含 JVM 写入约 `9.36 GB` 转储的时间；不能把约两分钟的采集器时长当作运行到 fatal 的时长。先停止自动重启，基于 A3/A4 这组重复信号分析共同的真实文字 draw 路径。

