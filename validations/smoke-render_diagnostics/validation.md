# 诊断启动验证

## 目标

验证迁移后的产品与 `radiance_probe` 在隔离环境下共同加载；本次暂停性能采样，不产生性能结论。

## 运行

```powershell
.\gradlew.bat :version:1.21.1-neoforge:runValidationSmokeRenderDiagnosticsClient
```

只用此验证的 `instance/`；启用已有 smoke 入口时在启动环境中设置 `RADIANCE_AUDIT_SMOKE=1`。工具准备实例标记与主音量 0；不接受 EULA。

## 通过标准

日志包含 `radiance`、`radiance_probe`、`AUDIT_SMOKE title-ready` 与正常关闭；无崩溃报告。基准验证只核对适配器加载，性能运行仍需单独授权。
