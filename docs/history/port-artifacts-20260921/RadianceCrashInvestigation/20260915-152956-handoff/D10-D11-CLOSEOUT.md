# D10 / D11 收尾与归档

## 收尾状态

- D10 `keep` 与 `all` 各 40 秒均已完成，退出码 0；合计 3,197,600 indexed draws、4,796,400 像素检查、0 VVL error、0 访问异常。
- D11 `keep` 与 `all` 各 40 秒均已完成，退出码 0；合计 2,755,904 indexed draws、4,133,856 像素检查、0 VVL error、0 访问异常。
- 2026-09-15 16:20 的进程核对未发现 `repro.exe`、`repro-sparse.exe`、`repro-text.exe`，也未发现命令行包含 D10/D11 scratch 的测试进程；没有本任务遗留测试进程需要结束，没有触碰其他进程。
- 没有未完成或结果缺失的 D10/D11 运行。旧 D08/D09 结果已分别记录；D06 的真实运行结果仍明确标为 API 捕获范围不足，不补跑独立 fixture 矩阵。

## 归档内容

- `D10-archive\`：D10 scratch 的 `repro.cpp`、构建/运行脚本、shader 输入、include、SPIR-V、EXE/PDB，以及从 `20260915-A\Evidence` 复制的两份日志和 `d10-runs.json`。
- `D11-archive\`：D11 scratch 的对应源码/输入/EXE/PDB，以及两份日志和 `d11-runs.json`。
- `D10-RESULT.md`、`D11-PLAN.md`、`D11-RESULT.md`：假设、影响边界、精确指标、SHA-256 和负结论。
- 原始 scratch 与 `20260915-A\Evidence` 仍保留；本收尾没有删除、移动或覆盖任何原证据。最终诊断清理前不得把归档内容误当正式产品。

## 结论

D10/D11 均为纯观察边界内的隔离对照结果，没有正式源码修改，没有行为绕过，没有强制同步、延迟释放、跳过绘制或关闭文字。D08-D11 负复现阶段到此结束；下一阶段转入真实 Minecraft A 实例取证。
