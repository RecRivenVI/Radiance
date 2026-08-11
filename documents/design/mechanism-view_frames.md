# 视图帧与 SDK 适配

## 合同

现有 `WorldUBO` 继续作为相机、投影、jitter 和世界位置的唯一事实；不复制第二套相机状态。`mcvr::view::Frame` 只引用它，另携带实际输入/输出物理尺寸、view/slot 身份及可选上一帧 UBO。引用只在当前同步录制期间使用，不留入异步任务或 SDK 缓存。上一帧缓冲、图像和 SDK 历史沿用现有在途所有权。

`viewForSlot` 集中 slot 到 view 的分区，世界场景历史与 SDK 历史使用相同公式。`CameraHistory` 表达首帧、相机位移/转向、资源重载等原因；两套升频器显式传入各自原有的 1.0/0.866 阈值，不改数值。不同视图有独立状态；重载在同一处使其失效。

## 适配差异

- Streamline：负 jitter、倒输入尺寸 motion 尺度；RR 的独立 viewport 与同帧去重保留。FG 继续使用自身的原点平移、64 距离阈值与重置标志。
- NRD：正 jitter、倒输入尺寸 motion 尺度、当前/上一帧 effected-view 与专用投影 Y 适配；首帧和重载 CLEAR_AND_RESTART。
- XeSS/FSR：负 jitter；FSR 的输入 motion 已为像素，scale 保持 1。原 near/far、曝光、锐化与时间步参数留在适配器。
- TemporalAccumulation：当前 view 的 pending reset 决定首帧 alpha=1，后续继续配置值；与 NRD/SDK 不共用数值阈值。

相同事实不等于相同 SDK 参数。封存 Ponder PT 的设置不因整理而恢复。非有限矩阵与尺寸的诊断不能变成未经批准的画面回退。

## 成本与验证

帧描述仅有引用和小型值字段，不分配、不复制矩阵、不创建资源。300 组旧/新重置判断、多视图交错、严格位移边界、资源重载/resize 原因及物理尺寸回归锁定机制。SDK 入口继续使用各自实际尺寸与符号；CPU 合同测试不能证明所有 SDK 模式的动态视觉。

FSR 的有界平滑时钟属于 view，不属于交换链 slot；使用单调时钟，首帧、1～100ms 与十样本平均政策不变。三 slot 16ms 序列的旧 48ms 反例见[专门调查](../research/investigation-fsr_frame_clock.md)。重载仍沿原政策处理时间异常；resize 重建会重新初始化时钟，不把各 SDK 的生命周期强行合并。

## SDK 输出有效性

FSR 与 XeSS 的实际 dispatch 返回是否接受本次计算；只有成功分支可以消费 SDK 输出。失败分支执行已有的合法输入缩放与深度、motion、法线辅助输出，并使该 view 的下一帧历史以 SDK_FAILURE 重置。初始化尚未完成同样不能消费未写入的输出。共同分流助手不捕获异常；已有 fatal 在 SDK 前后检查，原始错误向上传播，不再追加回退命令。

FSR 诊断分别记录 SDK 接受与 GPU 完成，失败捕获只读回退结果，不伪造 NGX 成功值。CPU 注入接缝验证成功、拒绝与异常的回调次数，未在客户端注入 GPU 故障。实际 SDK 接受日志与最终动态画面仍是不同证据。
