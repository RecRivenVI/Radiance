# 复制世界的区块加载诊断

## 目标

只读固定使用者最近游玩的世界与设置，输入通过 `inputs.toml` 登记。工具在本验证中展开副本，关闭声音、失焦暂停与输入；默认保留来源视距，也可显式进行 16/32 视距对照，模拟距离、渲染质量和世界内容不变。记录初次进入及随后平静窗口，区分 Java 编译、原生共享锁等待、拓扑处理、批次准备和已有主队列 GPU 计时。GPU 批次完成墙钟包含排队与轮询，不作为纯 GPU 构建时间。

## 运行

```powershell
.\gradlew.bat :render_diagnostics:prepareLoadingValidation -PloadingRenderDistance=16
$env:RADIANCE_CHUNK_PERF = '1'
.\gradlew.bat :version:1.21.1-neoforge:runValidationPerformanceChunkLoadingClient
Remove-Item Env:RADIANCE_CHUNK_PERF
.\gradlew.bat :render_diagnostics:analyzeLoadingValidation
```

运行前检查并记录其他 Minecraft 与 CPU/GPU 负载；使用者明确允许环境波动时记录限制，不据小幅差异宣称收益。准备只允许新建实例，已有实例不会覆盖；下一轮先保全本次完整证据，将旧实例归档或移入回收站，再使用相同命令准备。32 视距轮只将准备参数改成 `-PloadingRenderDistance=32`。不传该参数时保留来源设置；合法范围为整数 2 至 32，输入身份和准备后的配置哈希写入 `FIXTURE.json`。两轮必须从同一原始输入重新展开，不能沿用前一轮保存的世界。

默认 `rebuild=false`、`stopSeconds=115`，进入世界后 115 秒请求正常保存退出，不移动相机、不放置方块。两轮使用相同的有界 JFR profile 采样，退出时写出实例内 `chunk-loading.jfr`，大小上限为 128 MiB、栈深 128。逐秒事件带 Java 单调时钟和 UTC 毫秒锚点；JFR 按同 JVM 的 UTC 窗口归因，不直接相减 Java/native 时钟。JFR 栈采样不是精确函数独占耗时，分配采样也不作为完整分配计数。启动预热、加载与平静窗口分开，115 秒内未取得持续平静就明确标记未取得，不把剩余时间自动当作稳态。重建入口仍可由显式参数启用，但已发生设备丢失的 F3+A 流程不自动重复。自然设备丢失时停止整个对照组并保全证据，不再启动另一视距轮。

历史完整采样及设备丢失部分采样见[锁争用诊断](../../documents/research/investigation-chunk_loading_lock_contention.md)，修复与本次对照边界见[锁范围修复](../../documents/research/investigation-chunk_loading_lock_fix.md)。失败进程不满足通过标准，默认分析任务必须失败。需要读取已保存的失败前数据时，可使用工具脚本的 `--partial`，结果仍明确标记 `completed_run=false`，不将其作为验证通过或自动重启依据。

## 通过标准

正常关闭与性能测量完整性独立判定。分析器将逐秒事件的帧号、墙钟跨度与实际写入的帧间隔核对：未完成帧造成超过一秒的未覆盖间隔时，默认分析失败；`--partial` 只保留部分统计，不把缺失的长停顿从性能结论中删除。该检查允许停止采集时很短的未完成尾帧，不代替 GPU 在途尾部完成证据。

必须有输入哈希、实际配置、加载 DLL 身份、`world-ready`、`normal-stop` 事件、各维度保存和正常进程退出；关闭证据使用停止后的场景释放日志，或本次新鲜单 profile 实例的完整 `normal-device-close result=0` 跟踪。原生 stdout 不一定进入 Java 的 `latest.log`，启动时的场景释放和截断跟踪不能作为关闭证明。显式开启重建时还必须有 `rebuild-request/return`。原生 profiler 已连接，帧与计时 CSV 完整，无丢弃或写入失败。记录初次加载是否平静、实际帧耗时分布、队列进展、CPU/GPU 阶段和诊断采样本身成本。未平静时不得称稳态，CPU/worker/GPU 重叠耗时不得直接相加。前后性能结论还要求两轮完整流程使用同一输入、配置与统计窗口；有效采样不代表性能优化、视觉或长期稳定性通过。
