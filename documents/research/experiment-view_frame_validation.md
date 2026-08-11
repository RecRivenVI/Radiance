# 视图帧与 FSR 时间输入验证

2026-10-17，阶段 D 族 7 的实际验证；GPU 性能和 SDK 动态视觉不由本记录代填。

## 问题

在保留各 SDK 符号、阈值、历史与资源所有者的情况下收敛当前帧事实，修正 FSR 把交换链 slot 间隔误当真实帧间隔。

## 方法

只读 Frame 引用既有 WorldUBO，携带实际输入/输出物理尺寸和 view/slot；SDK 自己转换 jitter/矩阵/motion。300 组旧/新相机重置影子判断、严格位移边界、两个视图交错、重载/resize 原因、六个 slot 映射与空尺寸拒绝都在实际原生测试入口执行。可控单调时钟同时运行三 slot 旧归属和 per-view 新归属，包含 30 个 16ms 帧、20ms 第二视图、重置及原异常值政策。

## 发现

- 第一轮 `:native_renderer:nativeTest` 成功，非 GPU 67/67；此时只完成相机/物理帧迁移，尚未修改 FSR 时钟，不反推旧候选已有修复。
- 固定 FidelityFX SDK 明确 frameTimeDelta 为上一帧毫秒间隔；旧 Context 所有权反例得到 48ms，新 view 时钟得到 16ms。这是 CPU 确定性证据，不是性能采样，也未证明任何设备丢失因果。
- 时钟修改后的完整构建中 core.dll 编译成功，测试目标因缺少 `<array>` 失败；补齐后，第一次独立驱动误用 CMake 的 `-C`（未执行测试），更正为 `--config`。随后 scene-camera 目标编译、非 GPU CTest 67/67、CMake 安装全部成功。原失败日志保留；最终 W-02 仍须重新通过完整任务。
- 显式 FSR fixture 的所有权/预设拒绝测试 1/1 通过；不会改日常实例。最终客户端还须用日志确认实际 FSR/NRD 执行，默认 RR 运行不能替代它。
- 当前/上一帧 UBO 没有复制新相机缓存，Frame 只在同步录制期间使用。原 pipeline 重建、view 历史、SDK viewport、在途资源退休与 Ponder PT 封存状态保留。
- 保留 FSR 的首帧 16.67、1～100ms 和十样本平均；保留不同 SDK 的 jitter 正负号、motion 单位、near/far、曝光与阈值。RESIZE 原因能力有纯测试，实际重建仍按 SDK 现有新状态首帧处理，不虚构已采到每次 resize 的详细原因日志。

## 结论

本族机械事实与重置机制已统一，确定的 FSR 时间归属缺陷已修并有可判别回归。可以继续族 10；最终完整构建与隔离 SDK 运行统一收尾。尚无性能结论，也没有将 FSR 参数正确性写成设备丢失根因。证据为 `D:/Workspaces/Scratches/RadianceSemanticsAudit/d7-*`。
