# 区块 CPU 准备锁范围修复

调查日期：2026-10-15。本次针对已测得的共享锁争用实施最小修复；同世界对照的修复前进程在初次加载即发生设备丢失，已停止本组，没有运行候选客户端。自动验证与世界性能对照分别记录，不以修复锁范围推断 GPU 故障根因。

## 问题

[前轮诊断](investigation-chunk_loading_lock_contention.md)已在 266.43 ms 的帧中记录到约 235.37 ms 的两次主线程共享锁等待。worker 在 `Chunks::queueChunkBuild` 的发布临界区内构造 `ChunkBuildData`，CPU 拓扑配对占据该锁。此次不更改成像、传光、拓扑、背压或资源退休机制，只移动独立 CPU 准备的位置。

## 方法

`chunk_build_submission.hpp` 定义产品实际使用的提交接缝：短锁内解析完整句柄、预留网格版本、记录弱所有者身份；锁外复制 JNI 借用输入并调用原有构造与拓扑处理；重新持锁后核对代际、槽位、所有者和目标版本，再执行现有发光采样、GPU 上传或后台入队。重新进入 GPU 路径前再次检查 sticky fatal。

弱身份不在准备期间保活旧区段 GPU 资源，但能识别世界重置后的同槽位、同代际、同版本 ABA。旧式自动版本在准备前预留，旧的慢任务不能覆盖新的快任务。失败不清除已发布几何，相同显式版本可重试；较新版本和释放仍使旧结果失效。

回归直接调用这个生产提交接缝，并执行真实的孪生四边形拓扑处理。通过受控 gate 阻塞准备，验证其他线程可以获取共享锁，而发布仍独占；覆盖较新版本、主世界重置、外部槽位释放复用、关闭、自动版本完成倒序、准备或发布异常及版本耗尽。没有复制另一套调度算法作为替身。

性能对照使用与前轮相同的 Newport City 固定输入，SHA-256 为 `5e0f1d38256b0e831b10aacad504e866cfe825d0ff508166b374430fdf80e395`，从原始副本重新展开，不沿用前轮保存结果。保持 16 视距、32 模拟距离、2560×1440、Vanilla PT、SHARC、RR Balanced、FG 关闭、Reflex 2 和原有模组。自动客户端静音且输入隔离。仅将流程改为初次加载后 60 秒正常保存退出，关闭 F3+A 阶段，避免盲目重复前次故障路径。

## 发现

### 修复前进程再次失败

本轮基线进程 PID 74508 于 05:29:16 启动；实际加载 DLL 为 `f9ba4c1e537798216a93a270654065cb18e7e62368e7a425f082e535ea5f1f05`，是原锁范围实现，不含本次修复。05:31:42.512 记录 `world-ready`，05:31:45.399 的 `vkWaitForFences(frame)` 检测到 `VkResult=-4`，在初次加载期间、没有执行 F3+A。检测调用不等于 GPU 致因位置。

仅取得 3 个非失败帧和 1 个失败帧，没有形成完整加载或平静窗口，不能计算可信的前后收益。性能输出未丢弃，但 `completed_run=false`；只用 `--partial` 保存失败前证据，默认验证不算通过。候选客户端没有启动，不自动再跑修复前包或改变配置掩盖故障。

Windows 在 05:31:43.546 和 05:31:45.348 记录 `nvlddmkm` Event 153。Java 保留原始设备丢失，05:31:46.652 记录全部维度保存，05:31:47.248 仍报告原始 Vulkan 错误；完整 native close-return 与 GPU 故障原因没有新增证明。未启用 Aftermath 或设备丢失跟踪，未取得 GPU crash dump。仅有跟踪 CSV、错误报告和 Windows 事件，不能把它们称为转储。

### 证据位置

本轮证据根为 `D:/Workspaces/Artifacts/Radiance/chunk-lock-fix-20261015/`。`baseline/` 保留原始源码快照、实际加载 DLL 身份、完整实例、分析结果、进程与 GPU 采样、Gradle 日志和 Windows 事件；`baseline/run-result.json` 明确为退出码 1。前轮第二进程移到原证据根的 `preserved-run-b/`，未覆盖或删除唯一副本。

### 自动验证与候选产物

本轮 `spotlessApply check verifyRelease verifyCompliance --strict` 通过，规范 2.0.2 的失败和警告均为 0。原生非 GPU CTest 64/64 通过，包含新增的生产提交接缝回归；GAME 250、probe 26、bootstrap 11 通过，bootstrap 两项显式硬件对照跳过。采集器 2/2、Python 输入与退出证据工具 5 项、盘点工具 8 项通过；其余 UP-TO-DATE 项不写成本轮重跑。JNI、Maven GAME 边界和发行包 154 个必需条目检查通过。保留既有 Gradle 弃用提示，不视为合规警告。

本次不改 shader 或拓扑算法；260 个 shader 和拓扑输入文件与本轮基线逐字节一致，真实构造函数除空白外不变。没有重跑 GPU 测试，既有 16 项 GPU 结果仍属于查询规则阶段；不以源码一致或构建通过代替新候选的世界、视觉或 GPU 故障验收。

候选发行 JAR SHA-256 为 `ba8c80e2d9227f54920c33f6c552c6c701299ef07d67d496dc985048ede5c157`，DLL 为 `8ddb622be48d5138280b426147d2b2ead8875331a90def78f451c820c810c3e3`。构建、安装与 JAR 内嵌 DLL 一致，匹配 PDB 已保留。候选未启动，故没有新的实例提取或实际加载证明，不将基线旧 DLL 的加载记录转用于候选。

`D:/Workspaces/Artifacts/Radiance/chunk-lock-fix-20261015/evidence-archive.zip!artifact-identity.json`、`candidate-artifacts/`、`D:/Workspaces/Artifacts/Radiance/chunk-lock-fix-20261015/evidence-archive.zip!native-ctest.log`、`D:/Workspaces/Artifacts/Radiance/chunk-lock-fix-20261015/evidence-archive.zip!test-counts.json`、`D:/Workspaces/Artifacts/Radiance/chunk-lock-fix-20261015/evidence-archive.zip!unchanged-rendering-inputs.json` 和 `D:/Workspaces/Artifacts/Radiance/chunk-lock-fix-20261015/evidence-archive.zip!check-verify-release.log` 分别保留产物、符号、回归、输入对照与构建记录。最终源码清单与 ZIP 位于证据根的 `candidate-source/`，包含已跟踪和全部必要未跟踪源码、构建配置与子模块身份；按原始字节 SHA-256，不做换行或编码规范化。本机输入仍由命名输入与单独的哈希固定，不进入源码包。

## 结论

已确认的锁争用机制与此次 GPU 故障分别处理。输入复制和 CPU 拓扑准备不再占据区块共享锁，发布仍受所有权和版本保护。发光采样、重要上传准备及原有批次工作仍可能占锁，未测量新候选时不能宣称所有锁等待已消除；本次原设置的发光收集为关闭。是否缩短此世界的加载帧、提高吞吐，必须等待有效的相同条件前后运行，不能用源码改动或历史成功进程代替。此次停止客户端实验，不扩大 GPU 调查，不向 Prism 投放，不提交或推送。
