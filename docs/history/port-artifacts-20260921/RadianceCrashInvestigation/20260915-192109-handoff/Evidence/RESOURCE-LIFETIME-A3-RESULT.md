# Resource-lifetime A3 result

运行：`run-resource-lifetime-a3-20260915-200145-881`

- Java PID：`71984`
- 开始：`2026-09-15T20:01:45.8816189+08:00`；JVM fatal elapsed：`39.897992 s`；采集器结束：`2026-09-15T20:03:44.5134313+08:00`。
- 用户接管前即发生 fatal；本次没有用户菜单、移动、暂停或退出操作。
- 实际 JAR SHA-256：`9D8BF6028C6C00604C6A38DD23FDA2E9B19F140F9C4D022D92E97F45D3A4D53B`。
- 实际加载 `core.dll` SHA-256：`96E59835DFBB7E85CCCBE431A07F4F4E4F4DB7625122DA064793B55232465643`。
- 匹配 PDB SHA-256：`FEEF6F9D1A4DBB155DCC656242D6E40052DC03AEE4FF4A286A2DE1840F63B26A`。
- 配置：A3，`VK_LAYER_KHRONOS_validation` + synchronization validation；API dump、GPU-assisted、DebugPrintf、best-practices 关闭。
- 快速进入参数：`--quickPlaySingleplayer 新的世界`。

## Fatal evidence

- `hs_err_pid71984.log`：`169,118` bytes，SHA-256 `8A058705A89B55AA4EF4526C0CEA86FE55EBBF4FEA036A74044DE8C5D0B36050`。
- 完整转储原位保留：`D:\Workspaces\Artifacts\RadianceCrashInvestigation\20260915-192109-handoff\Diagnostic\resource-lifetime-A\hs_err_pid71984.mdmp`，`6,491,297,965` bytes；未复制。
- JVM fatal：`EXCEPTION_ACCESS_VIOLATION`，问题帧 `nvoglv64.dll+0xf1c708`，读取地址 `0x0000000000000104`。
- Java 栈：`ShaderProxy.draw` → `RasterDrawBridge.draw` → `GuiGraphics.drawString` → `ReceivingLevelScreen.render`。
- 这是与原始 `nvoglv64.dll+0xf1c729` / `0xffffffffffffffff` 相邻的驱动代码位置和同一文字 draw 入口，但不是精确相同的地址/故障目标；结果分类器因此正确标为 `OTHER_FATAL`，不能改写成 exact `SAME_NATIVE_AV`。
- 本次 `latest.log`、stdout/stderr 没有 `VUID-`、`Validation Error`、`SYNC-HAZARD`、`WRITE_AFTER_WRITE` 或 `READ_AFTER_WRITE`。A3 层确实已在配置和实际启动链中启用；无消息不代表调用合法。

## Interpretation boundary

这次发生在首次世界加载画面，且没有用户操作；它证明颜色同步组之外仍存在同类文字 draw 触发的 NVIDIA 原生 fatal 变体。它没有提供 `releaseTexture()` 的释放事件、旧 descriptor 表代际或 fence 关联，因此不能确认或排除资源寿命假设。此次运行同时包含 R-01/R-02、颜色同步修复和本次资源寿命保护，不能单独分离这三组代码的因果关系。

