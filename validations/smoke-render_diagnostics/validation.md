# 诊断启动验证

## 目标

验证产品与 `radiance_probe` 在隔离环境下共同加载，以及 Windows 默认诊断在 SERVICE 创建的实际 Vulkan 设备上接通并正常关闭；不进入世界、不注入故障、不产生性能结论。

## 运行

```powershell
.\gradlew.bat :version:1.21.1-neoforge:runValidationSmokeRenderDiagnosticsClient
```

只用此验证的 `instance/`；任务自动设置 `RADIANCE_AUDIT_SMOKE=1`。工具准备实例标记与主音量 0；不接受 EULA。默认需要本地 `nsight_aftermath_sdk` 输入，配置见[GPU 故障取证](../../documents/development/debugging-gpu_diagnostics.md)；`-PgpuDiagnostics=light` 只能验证轻量路径，不可据此填报 Aftermath 接通通过。

## 通过标准

日志包含 `radiance`、`radiance_probe`、`Native diagnostic collector attached`、`AUDIT_SMOKE title-ready` 与正常关闭；无崩溃报告，不出现 `Native diagnostic collector unavailable`。基准验证只核对适配器加载，性能运行仍需单独授权。

Windows 默认模式还要求：`Agent_OnLoad before SERVICE` 的 SDK enable 成功，`radiance-gpu-capabilities.log` 确认实际设备创建使用 Aftermath flags，并列出 checkpoint/device fault 能力；留存的实际 SPIR-V 索引有非零 SDK hash；正常关闭日志确认 SDK disable 成功。核对加载的 core、agent 与 SDK 路径和文件身份。正常进程没有真实转储是预期情况，不等于验证了 GPU 崩溃回调或世界稳定性。
