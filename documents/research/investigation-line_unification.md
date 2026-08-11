# 显式线请求统一前的深入评估

调查日期：2026-10-16。本记录固定阶段 A 迁移前的现场与分类，不宣称已经完成迁移或运行验收。基线 HEAD 为 `72d2e4a3275e782418f59142e2f2ea1f01be4120`，包含既有未提交修复；完整范围与词法索引见[整体审计](audit-render_semantics_consolidation.md)。

## 问题

线条有共用棱柱展开，但端点姿态、宽度、来源身份、事件接收与目标权限尚非同一合同。使用者接受最小姿态/目标合同后再迁移线条，保持世界物理厚度，不扩大到其他八族。

## 方法

读取 EntityProxy 的全部线存储创建、processEntityRenderData、addWorldLines、调试路由及 queueBuild；CapturedLineVertexConsumer、WorldOutlineGeometry、Storage/BufferSource、WorldMeshSink；Sable bridge、Simulated 拉杆/绳索原始 renderer、Create highlight/世界事件、NeoForge bounds 和 Veil stage/fixed-buffer 源码；再追踪 JNI、EntitiesBuildTask、索引和棱柱展开、帧提交及资源保活。

合同与普通单元测试不依赖游戏启动。后续先运行现有捕获/原生夹具及接收回归，再迁移；测试向量应覆盖真正的捕获字节、描述符、JNI 解码和最终截面，不以源码字符串检查代替行为。

## 发现

### 完整来源到消费者链

| 来源 | 决定点 | 当前消费者及差异 |
| --- | --- | --- |
| 原版实体/BER，包括钓鱼线与结构框 | Storage 捕获 Pose，EntityProxy 整批给 0.0125 | Entities 解码四类线并展开；同批次来源宽度被覆盖 |
| 原版调试线与 Create highlight | 专用 Storage、RenderType 声明宽度、调试身份 | 采用 0.0075 物理宽度标尺；调试自发光和可见性是有意差异 |
| Sable 子世界实体、BER 与调试 | bridge/EntityProxy owner orientation，加逐顶点 Pose | 捕获基底优先、owner 回退；不能将同一变换再次应用到端点 |
| Simulated 拉杆与绳索外框 | 原 BER 发出 RenderType.lines；本体是模型 | 已经进入普通 BER；拉杆旋转已获用户通过，不按内容名特化 |
| Create 曲线轨道、输送机、NeoForge bounds、Veil 普通事件线 | BufferSource/RenderType.draw，部分 endBatch 延后 | WorldMeshSink 仅允许面模式，线被拒收；原 BufferBuilder 还会丢失姿态基底 |
| 派生模型轮廓已经发出的线 | WorldOutlineGeometry 把截面参考写入 normal | native 按 priority/outline 内容名前缀改变 normal 解释；应改为显式字段 |
| Veil fixed buffer | 原处理器先排空指定 RenderType，再调用 renderPost | 当前整个处理器被延后为 WORLD_RASTER，混合了世界几何与屏幕后处理 |
| Veil 普通 listener | 注册后由 VeilStageAdapter.wrap 包装 | 默认 FBO 与 AfterWorldRender 活动就放行后置光栅，缺少逐入口权限 |

### 差异分类

明确缺陷共三组：事件线拒收/捕获基底丢失；批次固定宽度覆盖实际来源；来源 normal 被名称分支解释成另一属性。另有使用者明确批准的权限变更：未登记 listener 不自动获得 WORLD_RASTER 权限。不能将该产品决定写成所有既有 listener 已经产生错误画面的证明。

有意差异：调试与普通线的默认物理标尺不同；普通线材质与调试自发光不同；优先级/直接/传光身份遵守 adr-1；离屏/UI 线仍由原光栅目标消费；Catnip 面框、绳索本体和光束不是线请求。

### 不变式与最小工程选择

- 捕获时复制姿态，位置只应用一次变换；基底正交化用于截面，镜像奇偶保留；世界原点保持 double。
- 宽度、材料和目标在真实提交边界采集，不能由整批参数覆盖；不创建 GL context，不查询 GL 状态。
- 来源由适配器显式登记；native 只读描述符，不从 contentName、normal 或 RenderType 名称推断线属性。
- 复用既有实体与世界提交载体，共享同一描述符和 native 展开，不重写 BLAS/TLAS、代际或 GPU 退休。
- 事件线拥有独立缓冲；只排空自己的线，不全局 endBatch。阶段异常、帧取消、重载和封口均拒绝旧提交。
- 保留真实 RGBA 与材质含义，不把线条统一改为双面、自发光或优先级。

热路径不创建逐顶点合同对象：直接写已有捕获缓冲，每绘制生成小描述符。显式截面参考需要增加捕获布局字段；只影响线输入，不改变其他格式。诊断和影子比较有界且默认关闭；本轮不以未测量的性能结论证明成本可忽略。

## 结论

应执行阶段 A。迁移期间将旧输入/输出作为测试参考，不保留永久产品双路径；每项不一致分为上述明确缺陷、有意差异或待决定。工程合同写入 mechanism 文档，权限变更单列 adr-2。最终验证、发现数量和目视结果在[当前计划](../project/plan-render_unification.md)及项目状态记录，不回写本研究为“首次调查已验证全部通过”。
