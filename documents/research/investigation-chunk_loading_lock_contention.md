# 区块加载共享锁争用诊断

调查日期：2026-10-15。本次在使用者重新授权后，只读复制最近进入的 Prism 世界，在项目内运行两轮有界诊断。确认一条造成长帧的共享锁等待链；第二轮在 F3+A 后发生真实设备丢失，立即停止本组，没有自动重启。不将性能定位写成 GPU 故障根因或修复完成。

## 问题

使用者在查询规则候选包中总体目视未发现问题，但再次观察到区块加载严重卡顿。需要区分服务端读取与生成、Java 网格构建、原生拓扑处理、批次准备、共享锁等待和 GPU 执行，不能仅从“双向几何之后出现”推断原因。

## 方法

来源为使用者最新进入的 `Newport City`，最后保存于 2026-10-15 04:30:36，DataVersion 3955。只读持有源 `session.lock` 并确认客户端停止，复制前后文件列表的大小和修改时间一致。194 个文件原始字节共 136,917,107；完整原世界归档 SHA-256 为 `b5f2b918ce6142c7f63a31199421bd864b0accfdb783980ed97bdf052b7f4427`。包含设置的诊断输入排除会话锁，SHA-256 为 `5e0f1d38256b0e831b10aacad504e866cfe825d0ff508166b374430fdf80e395`；源世界未写入。

输入在 `inputs.toml` 登记，本机路径仅在 `local.toml`。工具每轮重新展开同一快照到 `performance-chunk_loading` 验证实例，不沿用前轮保存结果。保持玩家位置 `(-25.5030, 135, -16.2146)`、朝向 `(-57.7167, 7.6167)`、16 视距、32 模拟距离、2560×1440、Vanilla PT、SHARC、RR Balanced、FG 关闭、Reflex 2、28 个区块线程及 32/32 的批次设置。只关闭声音、失焦暂停和输入；未关闭实体生成、减少几何或改画质。

项目使用 Java 21.0.11、NeoForge 21.1.259，源 Prism 为 Java 25.0.4、NeoForge 21.1.252；项目未加载 Zume。这是项目内的阻塞诊断，不是精确复刻两个运行环境的帧率 A/B。实际玩家位置与角度逐秒记录且保持不变；世界正常模拟，未固定昼夜或删除实体。使用者允许轻微环境波动，本次不依据小幅差异宣称优化收益。

复用现有 FrameProfiler 与非阻塞 GPU 查询；新增默认关闭的原生 Scope，细分输入锁等待、持锁拓扑处理、只读 getter 和 runtime-buffer 三阶段。探针进入世界后约 60 秒执行一次 `allChanged()`，目标 115 秒正常停止。逐秒记录队列、服务端磁盘反序列化与新生成计数；数值单位及运行命令见[验证说明](../../validations/performance-chunk_loading/validation.md)。未修改渲染合同或调度策略。

## 发现

### 第一轮：完整流程和性能证据

进程 60168，04:54:47 启动，115 秒世界流程完成，全部维度保存、场景释放、进程退出码 0。原生 DLL 为 `39211640cb8728f384c8e1c8d170ebc457e3c656edce2a6dd7c0f4b5b7ee3515`。捕获 5,109 帧，写入、原生与输出上限丢弃均为 0。初次进入与 F3+A 后最终均为 5,241 个 ready 区段；这个计数不等于首次呈现时刻。

以下为真实渲染帧的 `start_interval_ns`，不是生成帧 FPS。连续四次逐秒采样的 Java/native 队列均为空才划入平静窗口；初次进入前几帧包含 JIT 和首次渲染开销。

| 窗口 | 样本 | 平均 ms | p95 ms | p99 ms |
| --- | ---: | ---: | ---: | ---: |
| 初次加载 | 549 | 43.23 | 104.26 | 177.25 |
| 初次加载后平静 | 2,128 | 17.03 | 20.10 | 22.95 |
| F3+A 重建 | 555 | 39.72 | 102.58 | 152.93 |
| 重建后平静 | 1,876 | 17.56 | 20.90 | 25.08 |

初次进入最大帧间隔 593.31 ms；F3+A 后最大 288.43 ms。`allChanged()` 调用本身另耗时约 168.42 ms，不作为后台构建成本，也不和之后的帧间隔重复相加。记录到 1,523 次磁盘反序列化事件，新服务端区块为 0；F3+A 阶段无需新服务端区块也可重现卡顿。

主队列 GPU 帧均值约 12.1 ms，最大约 26.1 ms；帧 fence 等待通常接近 0。它不包含独立区块队列与 SDK 内部全部工作，不能由此宣称所有 GPU 成本已排除。批次完成墙钟包含排队和 CPU 轮询，不称为纯 BLAS GPU 时间。设备级 NVML 峰值 12,695 MiB，不是 Minecraft 独占显存。

两次加载共捕获约 10,489 个 worker 拓扑样本，均值 2.58 ms、p95 18.78 ms、最大 70.19 ms；几乎占满对应持锁区间。worker 墙钟包含调度等待，重叠时间不能叠加为主线程百分比。逐秒采样本身均值约 0.39 ms、最大 5.01 ms，未出现能解释这些长帧的持续采样停顿。

### 第二轮：定点证实共享锁，随后设备丢失

进程 82552，05:02:17 启动，捕获 2,509 帧。初次加载窗口帧间隔均值 42.47 ms、p95 95.77 ms；平静窗口均值 18.25 ms、p95 22.48 ms。没有丢弃，但完整运行失败，不能写成第二次稳定性通过或 F3+A 性能通过。

第 298 帧耗时 266.43 ms，其中 `chunk-grid-info-lock-wait` 为 155.77 ms，`chunk-scheduler-get-lock-wait` 为 79.60 ms；这两个不重叠的主线程等待合计约 235.37 ms。`pt.runtime-buffer-refresh` 的最大耗时约 0.0012 ms，绑定最大约 0.108 ms。此前名字类似“资源准备”的长时间，实际主要在读取网格参数时等锁，而非创建大缓冲。

直接调用链为：

```text
worker queueChunkBuild
  -> 持有 Chunks::mutex_
  -> ChunkBuildData 构造
  -> geometry::prepare / 孪生面配对与索引处理

render refreshRuntimeBuffers -> executionExpressionVariables
  -> chunkGridInfo -> 等待同一 mutex_
render WorldPrepareContext::render
  -> chunkBuildScheduler getter / 调度 / 场景准备 -> 等待同一 mutex_
```

拓扑的单次持锁可以达到几十毫秒，多个 worker 连续取得锁后，渲染线程等待可以超过单次处理时间。这已是实际长帧中的阻塞证据，不再只是源码候选；尚未实施移出临界区的修复，不能据此宣称已经取得收益。

本轮 F3+A 返回于 05:04:14。Windows 在 05:04:14 和 05:04:16 记录 `nvlddmkm` Event 153；原生在 05:04:16 的 `vkWaitForFences(frame)` 检测到 `VkResult=-4`。检测位置不是 GPU 致因位置，也未证明锁争用导致此次故障。故障帧约 1,260.57 ms，单独标为失败，不纳入正常性能分布；F3+A 后仅有极少帧，相关统计不得作性能结论。

Java 保留原始错误，全部维度保存、RR viewport 释放和管线缓存落盘有日志；故障后的完整 native close-return 没有独立证据。启动时的 `Scene released` 不能冒充故障关闭。进程退出码 1，未自动重启。本轮未启用 Aftermath 或 `MCVR_DEVICE_LOSS_TRACE`，未取得 GPU crash dump；性能 CSV、Java 报告和 Windows 事件不是 GPU 转储。设备级 NVML 峰值 12,305 MiB，不足以证明或排除显存、驱动或资源生命周期原因。

### 构建、产物与证据

`spotlessApply check verifyRelease` 通过：GAME 250、probe 26、bootstrap 11 通过且 2 项显式硬件对照跳过；非 GPU CTest 63/63、采集器 2/2 通过。复制工具初次 3 项通过后，收尾另补“启动释放不能代替故障关闭”和“正常停止后才算释放”两项回归，合计 5 项。已有 Unsafe 与 Gradle 弃用提示保留。没有重跑不受计时改动影响的 GPU 合同矩阵，不将历史 GPU 结果冒充本轮执行。

第二轮诊断 JAR SHA-256 为 `2f536da8305a715e10b56c9ad76bd08e77c96b64a7078744f1c4c34c97edc6f8`，DLL 为 `f9ba4c1e537798216a93a270654065cb18e7e62368e7a425f082e535ea5f1f05`。构建、安装、JAR 内嵌、实例提取和实际加载模块均一致，匹配 PDB 保留。该诊断包没有投放 Prism，也不是新的通过验收候选。

本次客户端是加载 GAME/probe 开发输出的验证运行，不是外层发行 JAR 的安装式启动。收尾再次执行 `check verifyRelease` 后，构建目录 JAR 为 `440a3f1163cf24fd37ba6a1a308c1665df403dae41ceff05c553c2c2cef57191`；它未再运行。逐条对照仅两个 shader ZIP 容器字节及 `runtime.index` 改变，ZIP 内全部成员字节相同，其余外层条目及 DLL 均不变。原第二轮 JAR 已在证据根单独保留，不把新哈希替换进旧运行记录，详见 `D:/Workspaces/Artifacts/Radiance/chunk-loading-20261015-044501/evidence-archive.zip!post-check-packaging-difference.json`。

证据根为 `D:/Workspaces/Artifacts/Radiance/chunk-loading-20261015-044501/`。`D:/Workspaces/Artifacts/Radiance/chunk-loading-20261015-044501/evidence-archive.zip!world-source-manifest.json` 固定来源；`run-a/` 保留完整第一轮实例、性能结果、源码与退出记录；`source-manifest.json`、`source-snapshot.zip` 和 `D:/Workspaces/Artifacts/Radiance/chunk-loading-20261015-044501/evidence-archive.zip!artifact-identity.json` 固定第二轮输入与产物。`run-b-result/`、`client-gradle.log`、`run-result.json`、`crash-reports/`、`D:/Workspaces/Artifacts/Radiance/chunk-loading-20261015-044501/evidence-archive.zip!failure-timeline.txt`、`D:/Workspaces/Artifacts/Radiance/chunk-loading-20261015-044501/evidence-archive.zip!windows-gpu-events.json` 与 `.xml` 保留第二轮失败。源码按原始文件字节计算 SHA-256，不靠 HEAD 代表脏工作区。

## 结论

本次区块加载卡顿已定位到共享锁内的拓扑处理及其造成的渲染线程排队，至少能解释所测 266 ms 帧中的约 235 ms。应优先把持有独立输入所有权的昂贵准备移出共享锁，仅在短临界区内重新校验槽位代际、网格版本并发布；保留背压、过时结果拒绝、成像规则与 GPU 完成退休合同，再进行同条件验证。不要通过关闭双向传光或孪生配对掩盖成本。

本轮只完成诊断，没有性能修复、跨产物收益 A/B 或长期稳定性结论。计时会扰动时序，未单独量化全部 profiler 开销，不能把两轮间的小幅变化当作收益。第二轮真实设备丢失保持独立未知边界；后续实验须先分析此次证据，不重复本组故障流程。不提交、不推送，不使用 Computer-Use，不写入旧 MCVR 或原 Prism 世界。
