# 空间、姿态与法线合同调查

调查日期：2026-10-17。活动世界的实体、刚性模型、Flywheel 和区段最终都进入 `WorldPrepareModule`，但相机相对变换和 3×4 序列化分散；来源已烘焙姿态、实例姿态、双精度区段与离屏投影具有有意差异。本调查先固定各自语义，不把所有矩阵再乘一次，也不重新启用 Ponder PT。

## 问题

阶段 A 只统一显式线所需的空间输入。其余入口对 WORLD、CAMERA、CAMERA_SHIFT 的解释依靠 Java 与原生分别声明的数字，实体和刚性模型重复执行相同平移及序列化；Flywheel 和区段则需要保留不同的浮点运算顺序。重复公式是已确认的维护风险，尚不证明当前存在新的肉眼错位。

## 方法

以 HEAD `72d2e4a3275e782418f59142e2f2ea1f01be4120` 加当前工作树为准，接续 `line-opacity-source-manifest.json` 的 1,859 个文件，索引为空。读取全部活动世界 TLAS 实例生产分支、Java 队列中的空间选择、Sable BER、嵌套 Flywheel、手部锚点、区段快照和光栅预览边界。影子比较使用独立保留旧公式的测试夹具，不把双重矩阵运算长期放在逐帧热路径。

| 来源 → 决定点 → 消费者 | 已应用的步骤 | 必须保留的差异 |
| --- | --- | --- |
| 区段、NeoForge 附加几何 → `Chunks` → 普通/静态场景 TLAS | 区段局部顶点，整数原点；外部区段携带 double affine | 先在 double 中减相机，再转 float；静态快照不可退回全槽扫描 |
| 实体、BER、粒子、程序化面 → `EntityProxy.queueBuild` → 实体 TLAS | renderer 的 PoseStack 已烘焙进顶点，原点单独传输 | WORLD 原点差转 float 后加局部实例平移；不重复应用来源 pose |
| Sable 实体/BER → bridge 与 PoseStack → 同一实体 TLAS | 子世界旋转/缩放及 blockPos-pivot 已应用，worldPosition 单独传输 | 子世界不是第二个相机变换；真实法线来自 PoseStack 的 normal matrix |
| 稳定 ModelPart → `RigidModelCapture` → 刚性实例 TLAS | 模型局部几何和本帧实例矩阵分开 | 与实体同一 WORLD 放置；部件身份及历史保留 |
| Flywheel → embedding 组合 → 实例 TLAS | parent×child pose，独立 parentNormal×childNormal | 整数 renderOrigin、local translation 和 camera 在 double 中求差；显式 normal correction 不等于再次逆转姿态 |
| 手臂/持物 → CAMERA；手杖锚点 → 逆 worldView | 手持 FOV 比率已烘焙 | CAMERA 只应用 cameraView inverse 一次；锚点不能再乘手持投影 |
| 天气、绳索、调试和世界事件 → CAMERA_SHIFT | 生产者已给出视图相对顶点 | 只加 cameraView inverse 的平移，不能再旋转 |
| GUI、结构图解、默认 Ponder、Veil FBO → 光栅投影 | shader/model-view/projection 由具体绘制快照拥有 | 不进入世界 TLAS 的相机相对放置；法线、纸张及顺序保持各自光栅语义 |

## 发现

### 可合并的机制

实体和刚性实例的 WORLD 放置公式完全相同，四类 TLAS 都手写列主序到 Vulkan 行主序的相同序列化。共享决定者应区分坐标类别、浮点精度步骤和最终序列化，不改变最终布局。Flywheel 的显式法线修正可归入同一数学合同，但不修改其来源 normal matrix。

### 有意差异与不变式

1. 已烘焙位置和局部几何不能相互替代；空间类别不等于还要应用一次来源矩阵。
2. 法线使用 inverse-transpose 或来源显式 normal matrix；线截面轴和几何绕序不是法线。镜像补偿仍由现有 face 合同唯一执行。
3. 大坐标先求 double 差；实体现有 float 相加顺序与 Flywheel 的 double 合并顺序分别锁定，避免借整理引入舍入变化。
4. 相机、资源代际与历史由原有帧持有，不将空间记录当作新的所有权或退休机制。
5. UI/FBO 的投影转换保留在光栅适配器；与世界实例的不同属于目标依赖，不应强行合并成一个执行算法。

## 结论

实施来源无关的空间枚举与原生矩阵决定者，所有世界实例放置及 Vulkan 序列化复用它；来源生产的实际姿态仍由适配器保留。先以旧公式对新函数作逐元素比较，再覆盖正/负缩放、非均匀缩放、组合变换、大坐标、三种坐标类别、当前/上一帧和显式 normal。没有确认新的视觉缺陷，不授权改变玩家外观；后续自动与运行结果登记在当前计划和状态中。
