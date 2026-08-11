# 升频输出有效性与失败传播调查

2026-10-17，阶段 D 总回查时沿 FSR 时间输入继续读取实际 SDK 返回链；没有人为制造 GPU 故障。

## 问题

SDK 未产生有效输出时，不能把旧/未写入图像当作成功帧，也不能将 API 返回与 GPU 完成混淆。

## 方法

逐读 FSR3Upscaler::dispatch、FSRUpscalerModuleContext::render、XeSS wrapper/module、DLSS 的 evaluate-state 与 capture seal。核对真实 SDK ReturnCode、初始化失败分支和现有 fallbackBlit 的同步与附属 depth/motion/normal 输出。

## 发现

- FSR wrapper 返回 void，FFX Dispatch 失败只写日志并返回；调用方仍把 outputImage 标为有效、捕获输出并发布附属数据。这是确定的错误路径，与设备丢失是否发生无关。
- FSR context 初始化失败时，module 直接 return，未走已有合法输入 blit。XeSS 的对应分支已使用 fallback，不应引入另一种无输出行为。
- FSR capture 明确写“wrapper has no result”，此前没有虚报 API 成功；改为 bool 后需要准确记录结果，不能伪造 NGX/Vulkan 错误码。
- DLSS 已按 evaluate-state 分开 SDK 输出与 fallback；XeSS 已检查 bool，但非致命失败后仍须使下一次相机历史重置。重置原因应为 SDK_FAILURE，不冒充资源重载。

## 结论

FSR 明确返回是否产生有效输出；FSR/XeSS 经同一同步结果分流接缝，仅成功回调能够消费 SDK 输出。失败恢复使用已存在的正确 blit 并保留诊断，下一帧重置 SDK 历史；若全局已有 fatal，则原错误向上抛，不继续记录恢复命令。以 CPU 注入的返回/异常测试真实分流函数，不在客户端伪造 device-lost 或注入 GPU 故障。最终正常 SDK 运行只能证明成功路径接通，不能代替真实设备丢失验收。
