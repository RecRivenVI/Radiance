# 目标、阶段与提交边界调查

调查日期：2026-10-17。当前世界、屏幕、离屏与获准后处理已通过 `RenderCaptureContract` 路由，但类型声明与实际 FBO 查询放在同一客户端类，预览的另一份 scope 判断与之独立。可把决定表提取为不依赖游戏的合同，保留线程作用域、帧封口和资源所有权；没有证据要求重排绘制或改变已接受权限。

## 问题

族 2 覆盖显式世界提交、BufferUploader/VertexBuffer、Veil VertexArray、GUI、屏幕覆盖、离屏 FBO 和延迟 endBatch。只有默认 FBO 并不足以授权世界后置光栅，提交结束时也不能替代生产时的姿态和资源快照。阶段 A 已解决未知 listener 默认不得进入 WORLD_RASTER，不重新登记为本轮缺陷。

## 方法

逐项读取路由决定与消费者：`RenderCaptureContract`、`RasterDrawBridge`、`RasterPreviewScope`、`WorldMeshSink`、`WorldLineCapture`、`AfterWorldRender`、`WorldRasterPass`，以及 GUI/Screen、NeoForge stage、dimension effect、Veil stage/VertexArray、Simulated 图解与相机覆盖适配器。检查所有 `ScopeKind` 和 `BufferDrawRoute` 调用点，记录迁移时仅变更类型归属、不改变决定表。

| 来源 | 决定与消费者 | 生命周期及有意差异 |
| --- | --- | --- |
| 普通实体、BER、粒子、区段、Flywheel、刚性模型 | 显式世界构建接口 → native 世界几何 | 不经过 raw raster 许可；world/object/resource 代际及 GPU 退休保持原职责 |
| NeoForge、Create、Veil 世界事件 | stage scope → `WorldMeshSink` | listener 同步执行，异常取消，事件线在自己的原 stage 结束时 flush |
| 延迟线 endBatch | `WorldLineCapture.Batch` 保存 stage、来源与存储 | 先移除待提交项再回调，结束 stage 时统一消费或关闭；不跨 stage 挪动旧线 |
| RenderType 面 endBatch | 当前显式 stage + RenderType 状态 → 世界或离屏 | 世界入口没有材料合同则拒绝，不静默转为后置光栅；不越过消费者延迟执行 |
| BufferUploader、VertexBuffer、直接/Veil 数组 | 当前 scope + 实际绑定 FBO → `RasterDrawBridge` | 世界默认目标拒绝无材质请求，自定义 FBO 由原生目标负责资源依赖 |
| GUI、Ponder、物品预览、图解 | GUI 或显式 preview scope → 光栅 | 按生产阶段保留 baked light/AO；独立 FBO 仍由实际绑定决定，不加入世界 TLAS |
| 相机覆盖、Veil 后处理 | 显式 camera scope 或登记许可 → 屏幕效果 | 不把覆盖层当世界几何；`AfterWorldRender` 只回放明确登记的后处理 |
| WORLD_RASTER | `StagePermissions.RasterPermit` → `WorldRasterPass` | 未登记 listener 不获得此权限；End Sea 仍等 adr-3，保持现状 |

## 发现

1. 共享路由枚举、scope 和决定记录实际上不需要 Minecraft 类型，但声明所在类同时访问 FBO 和客户端日志。提取这些类型能使合同独立测试，消除测试依赖原生 FBO 查询的必要性。
2. `RasterPreviewScope` 另写一份 scope→预览判断；它与 raw draw 的合法目标有关但并不等价，需由同一合同分别表达，不能将“不是 PT”统一成“总可以画 UI”。
3. GUI scope 内绑定自定义 FBO 时原决定仍称 VULKAN_UI，实际绘制目标由 FramebufferProxy 决定。名称不是输出到默认屏幕的证据，不因此修改实际目标或强行改序。
4. stage/frame 封口、资源代际、同线程 LIFO、异常取消、帧资源保活已有实现；此族不增持久缓存，不重写其所有权。族 11 没有新的持久消费者需要登记。

## 结论

以纯 `DrawTargetContract` 拥有目标类型、决定表和预览含义，客户端只快照当前作用域与实际 FBO。所有 raw raster 决定者复用该表，已有世界材料提交接口保持独立。以 scope×FBO 全组合与旧表作影子比较，并执行真实嵌套作用域、异常/取消、延迟回放和重载代际回归。没有已证明需要改变画面的缺陷，不新增 WORLD_RASTER 许可，也不改变 End Sea。运行和目视结论在最终统一验证中单列。
