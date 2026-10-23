# 纯原版城市性能诊断

## 目标

对冻结的新原版城市区分 CPU、GPU 与等待，拆分成本并比较视距规模；也用于原版 renderer 替代的同条件候选对照，不混入第三方游戏模组。其他线程可以并行，其负载逐窗记录。历史诊断的产品身份不因后续候选而改变。

## 运行

从仓库根目录先编译探针；依赖任务可能重包组件，运行器始终使用冻结产品，不使用该重包结果：

```powershell
.\gradlew.bat :version:1.21.1-neoforge:probeClasses :version:1.21.1-neoforge:probeTest
.\gradlew.bat :render_diagnostics:runVanillaPerformance -Pcase=r32-1080
.\gradlew.bat :render_diagnostics:runVanillaPerformance -Pcase=r32-1440
.\gradlew.bat :render_diagnostics:runVanillaPerformance -Pcase=r24-1080
.\gradlew.bat :render_diagnostics:runVanillaPerformance -Pcase=r16-1080
.\gradlew.bat :render_diagnostics:runVanillaPerformance -Pcase=report
```

每次恢复同一个城市 ZIP，保持原相机、模拟距离和画质，只改变窗口尺寸或渲染视距。至少预热 60 秒并要求队列连续平静 10 秒；先 FRAME_ONLY，再 TIMED，最后 JFR_ONLY。详细 native TIMED 限 10 秒，避免先前出现过的原始表容量耗尽；其他窗默认 30 秒。自动实例静音、无焦点控制与输入隔离，关闭 FG 的既有夹具设置保持不变。

原阶段 6 的历史 ABBA 见[替代实验](../../documents/research/experiment-vanilla_renderer_replacement.md)，已被收尾候选取代的旧二进制不再作为可执行默认值。当前产品、基线与探针从下文阶段 3 的冻结清单读取，不继承上次运行的全局路径。每窗仍保留 p95/p99、CPU、GPU、阶段及干扰数据，不把帧数不同的进程拼成一个样本。

2026-10-20 有一次分析任务与 24 视距重叠，已在 `result/analysis-interference.json` 留痕；补充窗口使用以下命令，不是新候选或完整性能矩阵：

```powershell
python -X utf8 components/render_diagnostics/benchmark/vanilla_performance.py r24-1080 --plan throughput
```

JFR 解析在客户端全部关闭后进行，使用 `components/render_diagnostics/benchmark/jfr_cpu.py <录制文件> <输出文件> --skip-allocation --jfr-tool <JDK的jfr.exe>`；录制与工具路径从该案例和启动 JDK 取值。执行样本保留深栈，allocation 不在本轮分析范围，原始 JFR 仍保留。

第一节阶段 3（原阶段 7）先在已正常保存的楼内入口做短窗来源核销，不把计数窗口用于性能结论：

```powershell
python -X utf8 components/render_diagnostics/benchmark/takeover_instancing.py census
python -X utf8 components/render_diagnostics/benchmark/takeover_instancing.py analyze-census
```

候选的限定复核用 `census --product <仓库内发行包路径>`，结束后恢复人工入口原包和设置。来源探针不修改原版 renderer 或 ModelPart；云的单独驻留构建与动态实体提交分别计数。首次原始数据仍保留其计数范围差异。

在 `takeover-instancing/manifest.json` 固定产品与探针、`result/instancing/fixtures.json` 固定输入后，只执行一组楼内 r8／1080p、城市 r32／1080p和1440p的 ABBA：

```powershell
python -X utf8 components/render_diagnostics/benchmark/takeover_instancing.py prepare-fixtures
python -X utf8 components/render_diagnostics/benchmark/takeover_instancing.py pairs
```

基线是阶段 2 收尾验收包；沿用原世界内容、动画、质量和模拟设置，只控制规定的视距／分辨率。每个进程先 FRAME_ONLY 20 秒，再 TIMED 10 秒；并行游戏和整卡遥测逐窗记录。运行器仅跳过清单中已完成的进程，不自动重复失败；自然设备丢失停止整组。冻结输入不在 A／B 之间重新保存。

## 通过标准

每窗有完整 STATUS 和连续帧号，真实帧间隔、线程 CPU 与 CPU 墙钟分别记录；GPU 以同窗返回的完整帧取分布，不补零，不相加嵌套区间。生产者 profiling Mixin 不得让 PARTS／BAKED／STATIC_STATE 退回；JFR_ONLY 不开启 native 阶段计时。原始表、JFR、进程与整卡遥测归并后，只保留报告引用的数据和摘要。

其他游戏进程与 GPU 占用随窗口对齐，整卡读数不冒充本进程占用。正常保存关闭后回收客户端副本；自然设备丢失先保全并停止，不自动重试，不操作其他项目或 Prism。
