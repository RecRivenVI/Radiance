# 表面材质分类收敛调查

调查日期：2026-10-17。范围是阶段 C 族 4 的生产者、分类与执行组；当前证据是源码与实际 Create 发行包内嵌资源，不是新增游戏视觉结果。发现 Flywheel 三种 cutout 阈值被合并为 0.5；其他尚未证明的外观差异不在整理过程中自行改变。

## 问题

`PBRVertexConsumer.getAlphaMode`、`Constants.GeometryTypes`、`WorldMeshSink.RenderTypeSnapshot` 与 `FlywheelMaterialData` 分别决定 alpha 和执行组。alpha、查询所有权、接受照明以及染色不是一个“透明”开关。文字、覆盖率、物理透射、乘法破坏层和有序覆盖不能合并。

## 方法

沿区段与 StorageVertexConsumerProvider、ModelPart 刚性捕获、原版实体/BER/粒子、WorldMeshSink、Sable 包装、SpringWorldLowering、Flywheel 实例和 Veil 原始绘制读取实际决定点。Ponder/结构图解的离屏光栅继续由原 shader 与目标适配器负责，不改为世界 PT。原生 CPU/GPU 转换消费 alpha 编码，两套 PT 与优先级经 `util/alpha_mode.glsl` 求值。

本地输入 Create 6.0.10 内嵌 `flywheel-neoforge-1.21.1-1.0.6.jar` SHA-256 为 `31DDA15C205EB596D3B3449EF03F6AF7363A6CD35B3DA4BFE916B304F9E5337E`。直接读取其中 `assets/flywheel/flywheel/cutout/{off,epsilon,one_tenth,half}.glsl`，阈值分别是无丢弃、0.01、0.1、0.5。与 `FlywheelMaterialData.capture` 的 OPAQUE 分支逐项对照；不借名称推测纹理内容。

## 发现

| 来源 → 决定者 → 消费者 | 处理 |
| --- | --- |
| 原版 RenderType 的透明 shard、文字模式 → PBR alpha → canonical/compact/native/shader | 将来源解析移到原版适配器，编码与来源外观保持；没有当前 shader 证明时不猜测新的阈值 |
| WorldMeshSink 的来源材质与显式目标 → 事件几何 → 同一原生转换 | 复用同一 alpha 适配，保留其有序预览、shader 能力检查和资源代际 |
| Simulated 真实 spring factory → 低阈值/表面染色/顶点 BRDF → 完整 PBR | 保留已经验证的 0.1、染色覆盖与无继承发光合同，不根据纹理或模组名选择 |
| Flywheel Material.cutout 的实际 shader 身份 → 实例材料 → 共享 alpha 求值 | 确认缺陷：epsilon 与 one_tenth 均被映射为 0.5；注册精确的四个上游 shader，未知 shader 显式拒绝 |
| GUI/FBO、Veil 自定义 shader → 原光栅适配 | 与世界 PT 有意分开；完整 GLSL 运算不能归约成单个 alpha 类型 |

现有文字模式 12–19 和 Flywheel 混合模式 20–23 是独立协议域，不能通过 enum ordinal 重新编号。有序覆盖 24 不等同于物理透射。普通动态、刚性和实例化的材质字段需要共同声明，来源版本解析留在适配器。

影子向量应逐项锁定旧分类，对 Flywheel 的两项阈值差异单列为有源码证据的纠正。多入口用实际消费者输出、canonical/compact 转换和共享 GPU alpha 求值验证；同一语义不能仅以类名相同证明。alpha 范围、有限值与不支持 shader 的拒绝是明确不变式。

## 结论

采用无 Minecraft/模组类型依赖的表面政策描述，将来源识别留在边界；不让原生根据来源名称重判 alpha。纠正 Flywheel 两种阈值；保留其余来源外观，后续族分别处理颜色与发光。新的极低阈值编码必须经过整数表、实际 JNI/CPU/GPU 路径及两套 PT 编译验证。最终动态、PBR、重载、模组资源替换与视觉结果仍需对应候选验证，本文不签发通过。
