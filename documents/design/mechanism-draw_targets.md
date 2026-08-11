# 目标、作用域与提交决定

`DrawTargetContract` 定义来源无关的 scope、目标路由和预览规则；它不读取 FBO、调用游戏类或授予权限。`RenderCaptureContract` 是客户端适配器，只保存同线程 LIFO 作用域并在 raw draw 时取得实际绑定目标，将事实交给共同决定表。

## 目标规则

| 当前作用域 | 默认目标的 raw draw | 自定义 FBO |
| --- | --- | --- |
| GUI、CAMERA_OVERLAY | Vulkan 光栅 UI/屏幕覆盖 | 相同光栅入口，实际目标由 FBO 适配器保存 |
| WORLD_STAGE、DIMENSION_EFFECT | 没有世界材料合同则拒绝 | Vulkan 自定义目标光栅 |
| WORLD_RASTER | 仅已登记许可的世界后处理 | 保持现有后处理目标依赖 |
| OUTSIDE、UNSUPPORTED_OPENGL | 拒绝 | 不能凭 FBO 绑定自动获得权限 |

世界面/线有完整的材料、空间与身份时由 `WorldMeshSink` 提交 PT；实体、BER、粒子、区段、Flywheel 与刚性模型继续通过自己的显式构建入口。raw raster 路由不是世界几何的替代执行方式，也不把整帧任意重排。

## 生产与提交

预览的 AO/baked light 判断与 raw draw 权限分开表达：在 GUI/相机覆盖/获准后处理内为预览，显式世界阶段不是预览，其他情况才使用专门的预览作用域。生产时的空间、材质与资源身份必须保留到提交，不能用 flush 时的相机重新解释顶点。

事件线保留生产 stage 与存储所有权，在该 stage 关闭前 flush 或取消；原有 ResourceReloadCoordinator、世界/帧封口、代际检查与 native 在途退休继续生效。同步调用、延迟 endBatch、离屏 FBO 和后续采样/读回保留原先顺序。此合同不增加持久缓存，因而不引入新的重载登记消费者。

## 权限与验证

WORLD_RASTER 的入口仍要求 `StagePermissions.RasterPermit`，未登记 listener 无权使用；不扩大允许名单。End Sea 的 adr-3 尚待决定，保持现状。所有 scope×FBO 的旧/新结果、预览规则、嵌套/异常、回放顺序与取消由行为测试覆盖，实际游戏及最终目视另行记录。
