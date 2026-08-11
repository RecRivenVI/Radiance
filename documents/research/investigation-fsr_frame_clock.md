# FSR 真实帧时间归属调查

2026-10-17，阶段 D 族 7 的定向代码与固定 SDK 契约核查；没有运行性能采样。

## 问题

FSR 每次 dispatch 的 frameTimeDelta 是否代表上一真实帧，而非同一交换链 slot 上次执行的时间。

## 方法

逐读 `FSRUpscalerModule::build` 的 contexts 创建、`FSRUpscalerModuleContext` 的构造及 `getSmoothDeltaTime`、wrapper 的参数传递，并核对固定子模块 `ffx-api/include/ffx_api/ffx_upscale.h` 第 98 行：该字段定义为自上一帧经过的毫秒数。此处依据仓库实际固定 SDK，不套用未安装版本。

## 发现

- 每个 framework slot 有一个 FSRUpscalerModuleContext；其 lastFrameTime、timingFirstFrame 与 frameTimes 却位于 Context。相机/FSR SDK 历史已经按 view 保存，计时没有遵循这个归属。
- 只有被选中的 slot 调用 getSmoothDeltaTime。若三个 slot 轮换，相邻真实帧相隔 16ms，同一 slot 的样本是约 48ms。它不是 GPU 帧耗时测量，而是 dispatch 间隔传参错误。
- 原逻辑另有首帧 16.67ms、1～100ms 范围和最多十样本平均政策；这些与计时归属是两件事，修复不擅自修改它们。
- 可用注入单调时钟在 CPU 中确定复现，不需要游戏、GPU 同步或计时压力实验。不同 view 必须继续有独立时钟。

## 结论

这是固定 SDK 输入契约与当前对象生命周期之间的确定缺陷，但没有证据证明它造成过设备丢失。把相同的有界平滑机制移入已有 per-view history，使用单调时钟，保留阈值/窗口；在首次构建 SDK 状态时重置计时。新增三 slot 与两 view 的确定性回归后再完成本族门槛。FSR 视觉须单独列入最终验收，不能用默认 RR 运行代替。
