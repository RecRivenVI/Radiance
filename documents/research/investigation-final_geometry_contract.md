# 最终位置、拓扑与历史入口调查

2026-10-17，阶段 D 族 8 的迁移前静态核查；本记录不把代码路径成立解释为客户端画面已经通过。

## 问题

最终位置的法线偏移在原生 CPU、GPU 直转和直转准入探测中重复。拓扑配对、显式索引、历史图元必须使用相同定义，不能因整理代码改变来源面的绕序或单向可见性。

## 方法

逐读 `entities.cpp` 的 `rawHasTwinQuads`、全部 draw mode、`EntityBuildDataBatch::build`，`entity_convert.comp`、`geometry_topology.hpp`、`quad_topology.hpp`、`world_mesh_contract.hpp`，以及 `world_prepare.cpp` 与两套 PT 的全部 `loadPreviousScenePos`。对照已有 QUADS/strip/fan/line、成对面与 CPU/GPU 实测回归；不启动性能对照。

## 发现

- 源 PBR 直转准入在最终局部位置上识别反向孪生；发现配对时回退 CPU 完成索引协调，普通输入保留 GPU 直转。`hasReversePolygonPair` 已检查所访问位置有限，CPU `prepare` 再检查完整输入。不能称其完全没有有限值保护。
- 1e-5 法线偏移在原始配对探测、CPU TRIANGLES/QUADS/显式索引与 GPU deferred 转换中重复；CPU 与 GPU 使用相同公式。非零有限法线保持现有算术顺序；零法线没有可定义的归一方向，不能静默伪造方向。
- TRIANGLE_STRIP/FAN 的既有路径没有启用该法线偏移；Flywheel polygon offset、文字/贴花偏移是来源独立语义，不能改为同一数值。
- 全部八个 `loadPreviousScenePos` 已核对三个索引与 alternate-diagonal 位。不是“只核对数量”，无需新增全量索引 CPU 副本。几何数/顶点数/索引数和线条来源布局先在世界准备处阻断不兼容历史。
- 历史读取重复八份；其中相同图元身份检查可共享，主追踪算法无需合并。任意第三方 renderer 在相同索引下更换顶点语义，仍需要来源提供稳定身份，不能从浮点位置猜测。
- 空/退化、奇数条带、显式索引越界、负缩放、孪生对角线与旧代际保护已有行为测试。整理应增加最终位置算术及历史拒绝反例，而非重新修改弹簧或 adr-1。

## 结论

将最终位置的共同算术收敛为 CPU/GLSL 共用小函数，保留所有调用点的启用条件、偏移顺序与距离；在 CPU 边界拒绝无法定义的已启用法线偏移。将 shader 图元历史判定抽为共同函数并以 GPU 反例锁定。没有证据支持改变来源拓扑、剔除语义、第一人称所有权或常规资源退休；这些保持现状。完整运行与视觉仍须分别记录。
