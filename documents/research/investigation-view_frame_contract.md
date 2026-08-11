# 视图帧、时序输入与重置调查

2026-10-17，阶段 D 族 7 的迁移前代码核查。对固定 SDK 适配的判断依据当前源码，不把其他配置的成功运行借给本族。

## 问题

相机、物理输入/输出尺寸、jitter、motion vector 与历史重置分散在 UBO、Streamline、NRD、XeSS、FSR 和时序累积中。统一应表达共享事实，并保留各 SDK 的符号、单位与阈值。

## 方法

逐读 `Buffers::setAndUploadWorldUniformBuffer`、`WorldPipeline::viewForSlot`、`UiPathTracingProxy` 的每场景 Buffers、DLSS wrapper/FG、NRD module、XeSS/FSR module 和 TemporalAccumulation。对照现有 UI 视图/退休测试与当前帧资源所有者。

## 发现

- GL 到 Vulkan 的投影映射仅在 UBO 边界执行；逆矩阵、当前/上一帧 UBO 和 Halton jitter 在同一边界更新。每个离屏场景持有独立 Buffers，不能把 `lastWorldUbo_` 字段误判为跨视图共享历史。
- 重型 UI pipeline 共享，历史按视图索引，dispatch 资源按在途 slot。归属与完成退休不能合并成单个全局历史。
- DLSS SR/RR 和 FG 已共用 `slConstants`。SL 使用负 jitter、像素 motion 的倒尺寸尺度、GLM/SL 的既有矩阵存储适配；RR 同帧不重复推进 viewport；FG 另带相机原点平移及 64 距离重置，不能复制 XeSS 的阈值。
- NRD 采用正 jitter、倒尺寸 motion 尺度和单独的投影 Y 适配；首帧 CLEAR_AND_RESTART，重载重置帧序号。不能为了统一而把 jitter 符号改为 SL 的值。
- XeSS/FSR 的相机跳变判断逐字重复：首帧、位移严格大于 1、方向 dot 严格小于 0.866，状态分别属于各视图。它们可以共用判断机制；阈值留在 SDK 适配器。
- FSR 当前使用固定 near/far 0.1/10000，而 SL 从投影推导。是否改变属于算法/SDK 配置选择，不以字段统一为由擅改。FSR motionScale 为 1（输入已经是像素），不是遗漏倒尺寸。
- 默认 Ponder 使用光栅；封存 UI PT 的 jitter 赋值会被 Buffers 的既有设置覆盖。未恢复此产品路径，不将封存实现当成当前已验收能力。

## 结论

引入无游戏/SDK 对象的只读视图帧事实描述与显式重置原因；在 UBO/SDK 边界读取同一组事实，SDK 专属映射和阈值仍在适配器。先锁定旧/新值的数值对照、两个视图交错、首帧/重载/resize 与阈值边界，再迁移。对于已经共享的 SL 和独立的 NRD/FSR 参数，不为统一制造外观变化。全部 SDK 组合与封存 UI PT 的视觉不由 CPU 合同测试代填。
