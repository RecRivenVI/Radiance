# 跨语言渲染 ABI 调查

调查日期：2026-10-17。draw mode、顶点格式 ID、坐标模式、查询掩码、alpha 模式及 packed 位域在 Java、C++ 与 GLSL 间存在手写副本；两套 shader 已共享结构体，但输入 stride 与 packed 位域仍重复。应保持现有二进制值，以一份整数表生成 Java 声明，C++/GLSL 直接包含同一份定义，并以实际 JNI 和 GPU 转换验证。

## 问题

Java 的 `Constants`、`PBRVertexConsumer`、`MaterialFaces`、Flywheel 材料描述，原生的 World/Vertex/line/instancing，以及 shader 的 alpha/vertex/entity_convert 分别声明共同字段。重复本身是确定风险，不等于已有编号或 stride 错误。不能为统一而改变顶点格式、精度、拓扑、alpha 意义或 adr-1。

## 方法

沿完整输入链读取 Java 原始 VertexFormat、compact 打包、JNI 参数、native 源解码与最终 MaterialVertex、GPU 转换器、两套 PT 共用解码。区分各字段所处的域：来源 compact 位、最终 packed 位、材质来源面状态、实例 flags 和查询角色并非同一组位。

| 域 | 共同事实与消费点 | 校验方式 |
| --- | --- | --- |
| 图元/顶点/几何/坐标 ID | `Constants` → JNI → `World`/空间决定者 | 独立基线值、实际格式大小、JNI 注册表 |
| canonical/compact/line stride | Java 格式 → CPU/GPU 源解码 | 真实 VertexFormat 大小、C++ sizeof、现有 CPU/GPU 字节对照 |
| compact 与最终 packed 材料位 | Java 输入打包 → native/GPU → PT 解码 | 分域命名，保留原始位模式与边界向量 |
| alpha 编码 | 表面适配 → 打包 → 共享 shader helper | 不合并 cutout、覆盖率、透射、加法、文字和有序覆盖 |
| 查询与来源面 flags | Java → 原生实例/材料 → 共同查询规则 | 使用 adr-1 现有 GPU 矩阵，不重新定义可见性 |
| Flywheel 材料 flags | 实例来源适配 → native/着色器 | 与普通顶点位域分开，不因相同数字而混用 |

## 发现

1. native 与 GLSL 已有 `common/query_policy.hpp` 的双语言头模式，可以复用这一机制，避免再引入 schema 框架或第三方生成库。
2. Java 与原生 mode/format 的现有值逐项对应；本轮没有确认数字错位。新合同必须锁定原值而不是借迁移重排枚举。
3. Mojang Uniform、Veil GL 类型码、GLSL 类型字符串是不同来源协议；保留各自类型化适配，不能将它们压成同一数字表。bool/uint 旧缺陷已有修复，不重新登记为新缺陷。
4. 阶段 A 的线合同版本与字节布局不变，通过检查而非改版保持一致；新生成内容只在 build 下产生，不手工维护第二份声明。

## 结论

采用一份 `render_abi.def` 整数表及 C++/GLSL 共用头；小型构建生成器从该表生成 Target 的 Java 常量。消费者改读常量或添加尺寸断言，原来的数字协议不变。以独立基线、真实 JNI 注册表、字段范围/位模式与现有 GPU 转换夹具验证，不以源码字符串计数代替行为证明。后续实施、首次失败及重测登记到当前计划与最终研究记录。
