# Voxy 远景接入回归

## 目标

在静音、1920×1080 的独立实例验证真实 Voxy 摄入、CPU 模型和压缩网格、Vulkan 持久场景、资源重载及世界退出／重进。只检查功能，不采集或比较性能。使用原版地形、cutout、玻璃、水及发光代理，Voxy 测试半径为 512 方块，近景视距为 4；这些是显式的功能用例设置，不改变产品默认设置。

## 运行

从仓库根目录执行：

```powershell
.\gradlew.bat :render_diagnostics:prepareVoxyValidation
.\gradlew.bat :version:1.21.1-neoforge:runValidationRegressionVoxyIntegrationClient
```

需要已登记的 `voxy` 本地输入。准备任务显式设置 `collectChunkEmission=true`，使既有 Advanced 选择条件成立；不改变产品或 Prism 配置。客户端由探针正常关闭；发生设备丢失则保全证据并停止，不自动重跑。原生压缩格式的独立 GPU 对照使用 `:native_renderer:gpuTest`。

## 通过标准

日志确认 Voxy 的 GL renderer 与 ModelStore 未创建，存储租约、模型及真实压缩区段实际推进；原生 GPU 构建已记录且世界场景包含远景实例。重载后旧 epoch 被拒收，正常退出／重进后新 session 生效，没有悬空引用、崩溃报告或设备丢失。结果文件记录实际执行的步骤与计数；命令返回 0 或短时未崩溃本身不构成通过。视觉效果仍需使用者验收，不声称性能改善或长期稳定性。
