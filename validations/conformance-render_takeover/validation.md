# 独立接管关键路径可行性

## 目标

在不安装 Flywheel 的隔离实例中验证限定的独立资源／实例请求实际进入现有原生世界场景；复用当前刚性提交 ABI，不实施正式接管层。验证模型共享、实例姿态与颜色独立、原版部件几何对照、绘制状态、离屏写入后采样、资源重载和正常保存关闭。探针与试验桥不进入产品 JAR，不做性能采样。

## 运行

```powershell
python -m pip install -r components/render_diagnostics/benchmark/requirements.txt
.\gradlew.bat :render_diagnostics:prepareTakeoverValidation
$env:RADIANCE_VALIDATION_NAME = 'conformance-render_takeover'
.\gradlew.bat :version:1.21.1-neoforge:runValidationConformanceRenderTakeoverClient
Remove-Item Env:RADIANCE_VALIDATION_NAME
.\gradlew.bat :render_diagnostics:analyzeTakeoverValidation
```

实例为本验证的 `instance/`，1920×1080、主音量和音乐为 0；不操作外部实例。原生 GPU 合同另执行 `:native_renderer:gpuTest`。环境默认轻量取证与本地 Aftermath，不启用性能或故障注入。设备丢失后保全证据并停止整组实验，不自动重试。

图像判定使用工具组件固定的 Pillow；已安装匹配版本时不重复下载。分析器同时支持清理生成实例后重新读取已保存的证据，不因此重新运行客户端。

## 通过标准

`result/feasibility.json` 确认 Flywheel 不在加载器模组表；独立局部网格创建次数只随测试代际变化、实例提交非零且两实例共享资源；绘制状态与离屏 GPU 像素断言通过；实际部件比较非零，完成资源重载、世界保存和正常退出。日志无 native fatal、设备丢失或崩溃报告。

报告逐项注明 CPU 数据、JNI 接收、GPU 数值读回和最终图像的证明层级。独立试验不证明所有来源 shader 或全部 GUI／PT 最终视觉等价；没有执行的来源保持待验，不以启动成功宣称完整接管可行性已全部证明。
