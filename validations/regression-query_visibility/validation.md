# 查询规则运行回归

## 目标

用来源生成的隔离世界执行正常世界、手持物、水面两侧、船、旁观者、资源重载与保存关闭路径。复用已有 `GeometryWorldProbe`，以 `correctnessOnly` 模式复用启动伴随器隔离输入与窗口激活，不记录性能，不把截图代替使用者的视觉验收。射线分类和水面过滤的精确命中断言由原生 GPU 回归执行。

## 运行

```powershell
.\gradlew.bat :render_diagnostics:prepareQueryValidation
$env:RADIANCE_GEOMETRY_WORLD_PROBE = '1'
.\gradlew.bat :version:1.21.1-neoforge:runValidationRegressionQueryVisibilityClient
Remove-Item Env:RADIANCE_GEOMETRY_WORLD_PROBE
```

只使用本验证的 `instance/`。准备工具生成独立世界，主音量为 0；环境变量显式启用已有有界探针。进入世界后约 90 秒请求正常停止，期间不执行帧率基准。验证失败时先分析证据，不自动重复崩溃流程。

## 通过标准

日志包含 `GEOMETRY_WORLD world-ready`、各阶段 capture、`client-reload-complete`、`normal-stop`，船阶段记录 `passenger=true`；无 native fatal、设备丢失或对应崩溃报告。最终记录集成服务端全部维度保存、原生场景释放、管线缓存落盘及进程正常结束。加载 DLL 与本轮包身份相符；这不是独立的 native close-return 或 fatal 生命周期用例。

本验证只证明实际调用路径能够执行和正常收尾。两侧材质、照明强度、反射内容、折射连续性与优先级合成是否符合判决仍由手工矩阵验收。
