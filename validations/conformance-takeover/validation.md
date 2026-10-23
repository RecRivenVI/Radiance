# 模型与地形接管合同验证

## 目标

验证阶段 0～2 的当前实现：实际物品框与绘画、共享资源和独立实例、原始生成器双执行对照、热帧不重复生产、材质状态、重载、删除重建，以及边界方块／水／附加区段几何的完成发布。1920×1080、静音，仅使用本项自己的实例，不计性能。

## 运行

从仓库根目录执行：

```powershell
.\gradlew.bat :render_diagnostics:prepareTakeoverStages
$env:RADIANCE_CHUNK_TRACE = "1"
.\gradlew.bat :version:1.21.1-neoforge:runValidationConformanceTakeoverClient
Remove-Item Env:RADIANCE_CHUNK_TRACE
.\gradlew.bat :render_diagnostics:analyzeTakeoverStages
```

自然设备丢失保全证据并停止，不自动重试。准备和分析只由工具生成实例、结果和证据归档；保存的同一初始夹具可以供已授权的阶段 2 性能对照使用。

## 通过标准

实际原生成器对照的顶点位置／法线符合声明容差，其余材质字节一致；稳定热窗资源构建数量不增长，直接提交持续发生。两个实例变换与外观独立，模型重载后使用新身份。地形实际附加 renderer 执行，连续边界更新的有效版本可追踪到 GPU 完成、发布、TLAS 记录及完成帧；旧版本不能覆盖新请求，构建中旧几何仍合法。正常保存与 native 关闭，无设备丢失、字体所有者错误或新崩溃。截图是使用者目视待验材料，不代填视觉通过。
