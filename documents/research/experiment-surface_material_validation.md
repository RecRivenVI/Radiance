# 表面材质合同迁移验证

调查日期：2026-10-17。阶段 C 族 4 的已知来源材质已迁移，Flywheel 的两项错误阈值已修正；未知程序的可见接收策略保持现状，另列 adr-5。本文记录自动证据，不代表新的游戏运行或目视通过。

## 问题

需区分来源缺陷、迁移中产生的问题与未经批准的兼容性变化。前置[材质调查](investigation-surface_material_contract.md)固定实际内嵌 Flywheel shader 资源；它所建议的未知程序拒收并不是已接受的产品选择，因此没有作为最终默认行为。

## 方法

先在 `FlywheelMaterialData.capture` 实际调用的最小分类接缝锁定来源程序：旧实现将 one_tenth 返回为 1，回归要求 9，得到预期失败。另以 12 个真实 RenderType 工厂固定 alpha 和两种反射执行组，共 36 项取值，迁移前通过。迁移后追加同一 shader 配合不同混合状态的反例，要求 additive，而不能被 solid 程序登记表覆盖。

Java 政策记录从消费者内嵌类型提取到无游戏依赖的 `SurfaceMaterial`；普通/弹簧及测试直接消费者均迁移。`VanillaMaterialAdapter` 统一 alpha、文字与几何执行组的版本解释；已知 shader shard 用对象身份登记，并保留实际透明与层叠状态。未知来源解析只在该适配器保留，不能据此声称任意自定义 shader 已等价。

Flywheel 的精确四项程序映射恢复 0.01/0.1/0.5，新增模式 3 经共同整数表和共享 alpha 求值。GPU 夹具对多个 alpha 值与阈值前后边界验证普通/优先级求值，不只检查常量字符串。原有 canonical/compact 实际 Java 输入继续通过 CPU/GPU 转换。

## 发现

| 项目 | 结果与范围 |
| --- | --- |
| 来源缺陷 | 1 组：Flywheel 的 EPSILON 与 ONE_TENTH 被折为 HALF；已修 |
| 迁移反例 | 首版已知程序登记覆盖了不同的实际混合状态，测试得到 expected 11 / actual 0；加状态约束后通过，不是先前用户故障的证据 |
| 未知程序策略 | 审查取消了未经批准的拒收；一次编辑未覆盖格式化后的 default 分支，回归再次抓到异常，修正后通过。保留首次失败，不将此项算成原产品缺陷 |
| Java 定向 | 20/20；覆盖真实工厂、不同 blend、实际 PBR 输出、弹簧政策、compact 与破坏层 |
| 真实 JNI | 3/3，加载本轮 DLL 的常量与实际布局匹配 |
| 非 GPU | CTest 67/67 |
| GPU | CTest 16/16，新增阈值断言在生产共享 shader 函数上执行 |
| 实际生产者输入 | 6 组 Java canonical/compact 记录经 native 解码及 GPU 转换一致；包括边界、位模式、不可变批次、保护区和 compute→BLAS |

原始日志为 `D:/Workspaces/Scratches/RadianceSemanticsAudit/c4-*`：`cutout-red`、`classifiers-baseline`、`candidate-gates`、`shader-state-red`、`final-java`、`final-java-2`、`gpu-final`、`native-final` 和 `actual-java-*`。最终有效 Java 与 JNI 来自 final-java-2，不与前次失败拼接。GPU 日志存在外部 OBS/RTSS layer API 版本提示，没有把提示当成通过或故障根因；没有设备丢失。

## 结论

保留 opaque、cutout、覆盖率、透射、加法、乘法、有序覆盖与文字域的差异，不改 adr-1。当前没有新增持久资源消费者，shader shard 对象身份不持有重载后的纹理/ShaderInstance；原有重载与 GPU 退休机制不变。完整检查点为 `D:/Workspaces/Scratches/RadianceUnification/checkpoint-5-materials.patch`，它是完整 HEAD 相对源码差异，不是可单独运行的 JAR。

目视需重点检查 Create/Flywheel 带半透明边缘的裁切材质及重载前后，与普通实体、弹簧、文字、玻璃和水区分。未知来源的严格登记由 [adr-5](../design/adr-5-unregistered_surface_programs.md)等待决定；自动验证未证明所有资源包和任意第三方程序等价。继续族 5、6，不投放 Prism，不做性能测量。
