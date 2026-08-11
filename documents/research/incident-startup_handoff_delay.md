# Early Window 到主菜单的长时间未响应

2026-10-17，使用者补充报告 Radiance 会明显延长 Early Window 到主菜单的时间，中途长时间未响应。本轮启动的独立诊断进程也有对应长日志间隔；没有为复现而重启或修改产品。

## 问题

该问题与已经通过使用者验收的加载页淡出状态修复分开。当前进程 PID 46560，项目内 `performance-scene_diagnosis`，1920×1080、主音量和音乐为 0，产品与探针的开发启动入口；不得把新发行 JAR 的结构检查当作这个进程从发行 JAR 安装启动。

## 方法

保留 `D:/Workspaces/Scratches/RadianceSemanticsAudit/scene-diagnosis-client.log`，提取带时间戳日志的长间隔与采集器接通信号；读取进程模块、实际 core 哈希、设备能力日志和当前线程快照。不操作外部客户端、不注入故障，也不启动第二次对照进程。

## 发现

- 23:23:02 进入 `[radiance-recreate]`，`presentation=0`、`worldChanged=1`，23:23:03 记录 RR viewport 1 的输入／输出尺寸；下一条带时间戳日志到 23:24:46 才出现 `rebuild images=3`，两条间隔 103 秒。
- 当前 `render_framework.cpp` 在 `pipeline_->recreate(...)` 返回后输出该 rebuild 日志，因此间隔位于原生重建的时间范围内。不能将日志沉默等同于全程同一种阻塞，也不能仅因前一条是 RR 就认定 SDK 致因；shader 编译、管线／上下文创建、同步等待等阶段尚未细分。
- 使用者报告期间曾观察到该进程窗口 `Responding=false`；抓取 `scene-diagnosis-startup-threads.txt` 时它已经恢复并进入使用者操作的单人世界。该快照不是卡住时的线程栈，不能定位先前等待。
- `Agent_OnLoad before SERVICE` 返回 `enable=0x1`，实际设备能力记录 `aftermathFlags=3`、`diagnosticsConfig=1`、`deviceFault=1`、`checkpoints=1`、`debugNames=1`；native collector 后续以 flags=3 接通。实际加载 core 哈希为 `C80B26C8819B797C793B3FA1F975DB322437C953DBFBB3351E0B39DF93D3DFFA`，与构建及发行包内嵌相同。
- 这次没有发现设备丢失记录，正常进入世界不是 GPU 根因或长期稳定性通过。准备场景阶段没有开始正式 FrameProfiler 取样，不将窗口中的 native 周期指标称为可比较优化结果。

详细时序及模块收据为同目录 `scene-diagnosis-startup.json` 和 `scene-diagnosis-loaded-modules.json`。采集器、Aftermath 和其他程序都可能影响时序，未测各自开销；本次不据此推断未插桩配置的准确耗时。

## 结论

启动长等待已记录到一次具体进程和原生重建时间范围，但尚未确认哪一子阶段主导，未实施启动优化。后续定位应在下一次获授权的正常启动前准备阶段计时或阻塞期抓栈，不自动重启正在供使用者准备场景的实例，不把淡出修复或 RR 日志当作根因结论。
