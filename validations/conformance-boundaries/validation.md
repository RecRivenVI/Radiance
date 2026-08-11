# 接管边界功能实验

## 目标

在小型自有实例检查 DLSS FG 实际生成计数及输入、复杂表面／GUI 依赖、共享资源独立实例与区段发布。只验证现有后端和最小接缝，不实现完整新接管架构，不测试性能。

## 运行

从仓库根目录执行：

```powershell
$env:RADIANCE_VALIDATION_NAME = 'conformance-boundaries'
$env:RADIANCE_CHUNK_TRACE = '1'
.\gradlew.bat :render_diagnostics:prepareBoundaryValidation
.\gradlew.bat :version:1.21.1-neoforge:runValidationConformanceBoundariesClient
.\gradlew.bat :render_diagnostics:analyzeBoundaryValidation
```

运行前按项目存储规则检查占用。实例固定 1920×1080、静音，不激活窗口，不操作外部游戏。保留轻量取证与 Aftermath，发生设备丢失后停止整组，不自动重试。结论形成后校验归档并回收复制环境。

## 通过标准

功能报告与日志记录重载、独立实例、真实离屏读回、附加区段回调、边界修改及保存／正常关闭；原生跟踪关联入队、GPU 完成、发布、场景记录及完成帧。仅有客户端方块状态或 JNI 返回不能证明显示已更新。

FG 是否实际产生生成帧单独判定：使用现有呈现所有者查询后缓存的 SDK 计数，不创建第二个非线程安全 SDK 调用方；没有非零生成计数时记为未验证。输入图像与 GPU 合成测试不证明生成帧动态视觉正确。复杂来源最终画面及完整新公共合同仍需分别核销，不将小夹具记为全部绘制通过。
