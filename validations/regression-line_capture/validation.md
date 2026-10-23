# 显式线条接管回归

## 目标

验证阶段 A 的真实捕获、原生展开、事件接收和权限边界，以及后续 B/C/D 在相同入口的状态、颜色和发光合同。隔离世界内执行 NeoForge 事件线、Veil 包装器、Sable 顶点包装器，以及 Simulated 拉杆和绳索原始 `renderOutline` 的最小接缝；组合旋转、镜像和非均匀缩放、混合宽度与颜色、取消阶段、快速转动相机、改变窗口尺寸、重载资源和退出重进。只记录功能计数，不做性能采样，不代替使用者目视验收。

## 运行

```powershell
.\gradlew.bat :render_diagnostics:prepareLineValidation
.\gradlew.bat :version:1.21.1-neoforge:runValidationRegressionLineCaptureClient
```

接管可行性专项复用本来源夹具时，在运行前设置 `$env:RADIANCE_TAKEOVER_SOURCES = '1'` 和 `$env:RADIANCE_VALIDATION_NAME = 'regression-line_capture'`，运行结束后移除。后者限制共享准备工具只处理当前验证。该模式额外检查实际 Flywheel 后端已有非零模型、实例和 render 调用，并输出 `takeover-sources.json`；Flywheel 仅为被观察的来源，不能用本项替代无 Flywheel 的[独立接管验证](../conformance-render_takeover/validation.md)。现有来源适配运行通过不代表新统一协议或全部来源效果已经等价。

需要明确检查 FSR 时，先使用下列准备命令，再执行同一客户端任务；它只写带所有权标记的本验证实例，不能修改日常实例。

```powershell
.\gradlew.bat :render_diagnostics:prepareLineValidation -PlineValidationPreset=rt_nrd_fsr
.\gradlew.bat :version:1.21.1-neoforge:runValidationRegressionLineCaptureClient
```

显式 RR 对照使用 `-PlineValidationPreset=rt_dlss`，不把不同预设的运行拼成同一次。`SURFACE_CONTRACT` 在入世界、重载后、重进后检查真实 callback 异常保留、JNI draw colour、封批独立性及眼睛/名称牌来源政策；这些数值检查不能代替完整实体外观。

任务生成自己的实例，起始与最终尺寸为 1920×1080，主音量为 0。验证中临时调整为 1280×720，随后恢复。默认轻量取证与 Aftermath 保持启用。入世界约 45 秒后保存返回标题，再重进同一测试世界观察至少 8 秒并正常退出；该时间不包含冷启动管线编译与重载。发生设备丢失后停止，不自动重试。

## 通过标准

日志包含 `LINE_WORLD world-ready`、来源宽度 1/4、取消阶段、两次尺寸变化、资源重载完成、`left-world`、`reentered` 与 `normal-stop`。`line-contract.json` 中来源 3、4、5、6、7 的样式和顶点计数非零，非法输入计数为零，Sable 的无姿态回退计数为零；原始 emitter 和 Veil listener 调用不出错，未登记 listener 没有 WORLD_RASTER 权限。日志无 native fatal、设备丢失或崩溃报告，并完成世界保存及正常关闭。该计数在 JNI 前采集，还须结合原生接收/构建记录，不能单独作为 GPU 完成或画面通过证据。

源身份循环的 JNI 测试只证明 ABI 与展开等价，不冒充不同模组完整交互。Create 曲线轨道、链式输送机、原版实体/BER、Sable 实际选择姿态仍分别记录覆盖情况；没有触发的入口不得记为运行通过。两套 PT 的查询规则由现有 `gpuTest` 回归，最终外观交给阶段 A 目视清单。
