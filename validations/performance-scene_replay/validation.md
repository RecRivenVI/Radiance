# 静态世界重放与着色器对照

## 目标

从实际生产路径捕获静态世界，在独立 Native 进程中复用世界渲染管线、重载着色器，并对相同输入和采样序列记录 GPU 阶段、图片和窗口波动。包括介质过滤与嵌套设备地址，不用省略输入代替可比性。

## 运行

先按[开发入口](../../documents/development/debugging-scene_replay.md)构建、捕获并关闭项目客户端；准备当前结果目录中的算法包计划，再从仓库根目录执行：

```powershell
python -B components/render_diagnostics/benchmark/scene_replay.py replay --snapshot validations/performance-scene_replay/result/snapshot --plan validations/performance-scene_replay/result/plan.json --output validations/performance-scene_replay/result/comparison
```

## 通过标准

快照输入完整，游戏与同版本重放的画面差异有解释；相同配置的重复窗口有波动统计。固定视角与转头路线完成，按实际归属帧收集 GPU 时间，切换与预热不混入正式窗口，未返回的 GPU 样本不补零。缺失输入、原生崩溃与设备丢失记录为失败并停止。优化候选另需既有介质 GPU 契约、双算法定向回归及项目客户端复核；重放结果不代替人工体验。
