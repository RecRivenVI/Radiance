# E-02 用户复现检查点

原 CPU 驱动访问异常尚未修复。此版本有明确的 descriptor table 生命周期实验；不作为正式验收产物。

## 单行启动命令

```powershell
& 'C:\Users\RavenYin\.cache\codex-runtimes\codex-primary-runtime\dependencies\native\powershell\pwsh.exe' -NoProfile -File 'D:\Workspaces\Artifacts\RadianceCrashInvestigation\20260915-A\Launch\Run-A-Validation.ps1'
```

命令使用独立 Diagnostic/base，启动前核对已登记输入哈希；没有构建、UI 自动操作或原 Prism/正式 A/B 修改。由用户运行，助手没有启动本版本。

## 一次复现步骤

1. 主菜单停留约两分钟。若崩溃，停止操作，反馈发生时间和步骤。
2. 若稳定，进入复制的“新的世界”，保持原字幕设置，正常移动片刻。
3. 打开暂停菜单，再保存退出。反馈菜单/世界表现、持续时间及是否正常退出；不把不崩溃当成修复证明。

启动窗口会打印本次 Evidence/run-时间戳/console.log。游戏退出后收集 latest.log、hs_err；若原生崩溃，完整 mdmp 在 Diagnostic/base，文件较大，等待命令返回后再分析，避免读到尚未完成的转储。

## 产品与证据

- Products/diagnostic-e2-layout-lifetime/Radiance.jar SHA-256：7CBC7B5915D5DA856D051BB4009F5F1067BC0CFA7B64EA6BDD2DD4144D807E51。
- core.dll SHA-256：5DA0FFEC2BC35B77C496D94C88B85C47BCEF703EB9C02F60B0543DE238B7084A。
- 匹配 PDB、十项源码冻结、全部哈希在同产品目录。
- 诊断目的、影响和清理状态见 DIAGNOSTICS.md 的 E-02；记录时区为本机 Asia/Shanghai。
