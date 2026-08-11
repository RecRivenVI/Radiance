# SER 在 Photoshop 退出后的设备丢失复测

2026-11-01，使用者明确授权在不运行 Photoshop 的情况下，再试一次 SER。相同故障 JAR 在 SER 关闭时完成四组窗口，启用后再次发生 GPU 读取页错误。两次 Aftermath 的内部故障指令与诊断指纹一致；Photoshop 当前运行不是复现所需条件，具体实现或驱动根因仍未确定。

## 问题

[前轮实验](experiment-sharc_execution.md)首次启用 SER 时设备丢失。同一时间附近 Photoshop 也曾崩溃，需要区分应用自身故障、共享环境干扰及 Radiance 的 SER 路径问题。

## 方法

只进行一次使用者明确授权的客户端运行，不自动重试。复用故障 M 本体 `7e0d9825c89d…`、Native `f8bcfe53d666…`、配套 Extra `bb98550ed61a…` 与既有 Aftermath，不重编译或修改产品代码。

沿用相同冻结城市、相机、32 视距、2560×1440 输出、1485×835 内部尺寸、四次反弹、SHARC、DLSS-RR Balanced、FG 关闭、HDR 开启及此前均衡透明模式。仍依次运行参考、SHARC、末次反弹省算、状态减负、SER，再计划恢复参考。启动前确认没有 Photoshop 或其他 Minecraft 客户端；运行期间定期枚举 Photoshop 进程。没有关闭任何使用者应用，不操作 Prism。

此前静态检查对故障时实际捕获的 23 个相关 SPIR-V 模块进行 Vulkan 1.3 验证，均通过；主射线结构均为相同的 24 个成员，未发现 NV／EXT 指令混用、错误阶段或明显的 hint 位数／SBT 参数差异。该检查不能证明驱动执行、资源内容或跨调用状态完全正确。

## 发现

### 同一故障在无 Photoshop 进程时再次出现

本轮 PID 为 118380。四组 SER 关闭的采样正常完成；10:45:59 请求 SER 管线，10:46:17 客户端报告设备丢失，尚未开始 SER 采样。Aftermath 生成 `gpu-118380-1.nv-gpudmp`，内部故障时间为 10:46:14。340 次 Photoshop 进程检查全部为空。

| 证据 | 首次，PID 114328 | 无 Photoshop 复测，PID 118380 |
| --- | --- | --- |
| Device state | `Error_DMA_PageFault` | `Error_DMA_PageFault` |
| 访问类型 | Read，地址无法翻译 | Read，地址无法翻译 |
| 地址 | `0xffffd0d0bc40c000` | `0x653dbc788000` |
| 内部故障指令 | `ray_tracing_01 @ 0x460` | `ray_tracing_01 @ 0x460` |
| Aftermath 指纹 | `4808388329074481429` | `4808388329074481429` |
| 指纹质量 | 20 | 20 |
| 驱动 | 621.01 | 621.01 |

两次地址不同，不能以地址相同推导同一资源；内部指令和指纹吻合加强了对同一执行路径的怀疑，但尚不能定位具体 GLSL 行、失效资源或责任方。现有证据不足以将其归为硬件故障，也不足以确认 NVIDIA 驱动缺陷。

### Photoshop 本身的日志

Windows 在 09:44:10 和 09:49:39 记录了 Photoshop 27.0.0.25 的崩溃，模块均为 `Photoshop.exe`，异常均为 `c00000fd`，偏移均为 `0x71bb5c1`；WER 的内部时间也与事件时间一致。该异常表示 CPU 调用栈溢出，见[微软说明](https://learn.microsoft.com/en-us/windows-hardware/drivers/debugger/debugging-a-stack-overflow)。第一次早于 SER 启用；09:48:43 和 09:48:45 另有 nvlddmkm 153 事件。

这不能证明两次 Photoshop 故障均由 Radiance 引发，也不能证明 Photoshop 导致 GPU 页错误。新复测说明：即使 Photoshop 当前没有运行，Radiance 的故障仍可复现。没有读取 Photoshop 文档或进程转储。

### 保全与收尾

发生故障后未再运行 GPU 测试。保存转储、实际 SPIR-V、日志、配置和输入变化，ZIP 每项读回 SHA-256 校验通过；恢复冻结地图与配置并逐文件校验，移出临时算法包和探针，恢复实例原有本体链接。原始性能 CSV 无损归档为 `profiles.zip`，64 个条目全部读回一致，松散目录移入回收站。

证据目录为 `validations/performance-sources/result/gpu-ser-no-photoshop-20261101/`。主要入口为 `result.json`、`photoshop-summary.json`、`fault-comparison.json`、`failure-evidence-index.json`、`crash/gpu-118380-1.json`。采样只用于确认 SER 前正常推进，不把没有完成的往返对照写成新的收益结论；没有 SER 帧时收益数据。

## 结论

停止当前复测，SER 保持从正式源码撤下的状态。优先将 hit-object 追踪／执行与线程重排分开，在最小场景中定位，再检查独立启动与管线切换差异；这些是后续试验方向，本轮没有继续启动。

未修改产品代码、提交、推送或替换 Prism。此前保留的 SHARC、末次反弹与状态减负源码不受这次重试改变。
