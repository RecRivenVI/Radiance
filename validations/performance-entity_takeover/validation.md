# 原版实体接管城市性能对照

## 目标

2026-10-20 使用者已暂停当前和后续阶段的性能对比，直到重新明确授权。原定 1080p／1440p 的 A/B/C/C/B/A 未完成，六次城市对照不再作为阶段 3 门槛，Goal 与正确性要求不变。以下命令保留供复查历史方法，当前不要执行。已完成的 MCP 暂停后 FRAME_ONLY 窗口只作为初步数据，不下收益结论。

已采窗口使用原始城市经原版优化后冻结的新 ZIP、固定相机、32 视距与原图形设置，只加载 NeoForge、Radiance 和必要诊断。A 为接管前冻结包，B 为阶段 2 实际验收包，C 为阶段 3 最终包，不用当前构建目录猜测身份；旧带模组初步结果只保留为历史。

## 运行

小型夹具使用功能验证正常保存的同一副本，执行 B→C→C→B，每进程预热至少 60 秒，再取 FRAME_ONLY 与 TIMED 各 20 秒；不与城市 60 秒样本混算：

```powershell
.\gradlew.bat :render_diagnostics:runTakeoverCity -PcityTakeoverSeries=small
python components/render_diagnostics/benchmark/takeover_city.py report-small
```

从仓库根目录执行：

```powershell
.\gradlew.bat :render_diagnostics:prepareTakeoverCity
.\gradlew.bat :version:1.21.1-neoforge:probeClasses
.\gradlew.bat -I components/render_diagnostics/benchmark/takeover_classpath.init.gradle dumpTakeoverLaunchClasspath
.\gradlew.bat :render_diagnostics:runTakeoverCity -PcityTakeoverSeries=final1080
.\gradlew.bat :render_diagnostics:runTakeoverCity -PcityTakeoverSeries=final1440
```

2026-10-20 首轮 final1080 的 A 与 B 和外部 MCP 客户端有重叠，原案例保留并逐窗标注。暂停后的主对照另用独立名称，保持同一 A/B/C、探针、夹具与顺序，不覆盖旧数据：

```powershell
.\gradlew.bat :render_diagnostics:runTakeoverCity -PcityTakeoverSeries=clean1080
python components/render_diagnostics/benchmark/takeover_city.py report-clean1080
```

干扰前序只用于检查环境敏感性，不混入 clean1080 主表。具体重叠时间、正常结束与环境门槛的记录见 result/environment-overlap-summary.json；环境暂停不记为产品或设备丢失。

clean1080 的首次 C 因 TIMED 采集器超容而整组停止，其完整有效 FRAME_ONLY 单列保留。固定队列修正后另建 final1080r2，完成 A、B 和 C 的 FRAME_ONLY；C 的 TIMED 按使用者暂停决定中断。后半组和 final1440 没有运行，不跨 probe 拼接，也没有完整最终主表：

```powershell
.\gradlew.bat :render_diagnostics:runTakeoverCity -PcityTakeoverSeries=final1080r2
python components/render_diagnostics/benchmark/takeover_city.py report-final1080r2
```

先完成[城市升级](../migration-city_world/validation.md)。仅在环境就绪后计时。每进程至少预热 60 秒且队列平静 10 秒，分别执行 FRAME_ONLY 与 TIMED 的 60 秒窗口；两窗结束后另有 3 秒仅计数的来源窗口，不作为性能数据。主音量和音乐为 0。每次保留原始表、加载身份、实际设置、正常保存／关闭与经 SHA-256 读回的归档，回收复制环境。发生设备丢失保全后停止整个用例组，不自动重试。

## 通过标准

现有初步记录位于 result/preliminary-frame-only.json，按 probe 版本分组，保留原始归档、身份、时长、状态和分位数。只有窗口本身完整、无丢样／失败帧且设置不变的 FRAME_ONLY 入表；中断的 TIMED 没有完整 STATUS.txt，不作为有效采样。原 MCP 重叠索引、采集器超容与使用者正常停止的分类分别保留。当前不补跑、不计算整组收益、不将不完整数据写为性能通过；来源正确性与[合并目视验收](../visual-takeover_stages/validation.md)继续独立核销。
