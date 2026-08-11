# 原版部件与来源退回验证

## 目标

在项目内独立夹具核对阶段 3 的实际部件、贴图包装、姿态、附加层、模型复用、重载和删除重建。另一个进程启用未登记的测试 Mixin，确认实际修改后的来源仍执行，并保留回退原因；正式性能窗口不加载该修改。

## 运行

从仓库根目录执行：

```powershell
.\gradlew.bat :version:1.21.1-neoforge:probeClasses
.\gradlew.bat :render_diagnostics:runEntityTakeover -PentityTakeoverCase=functional
.\gradlew.bat :render_diagnostics:runEntityTakeover -PentityTakeoverCase=foreign
.\gradlew.bat :render_diagnostics:runEntityTakeover -PentityTakeoverCase=modded
.\gradlew.bat :render_diagnostics:runEntityTakeover -PentityTakeoverCase=lifecycle
```

运行使用 1920×1080、静音、固定的独立世界与正式发行 JAR 加载。原始部件逐顶点对照只在此功能用例中启用。结果与加载身份保留后回收复制环境；设备丢失立即保全并停止，不重试。

上述默认命令保留阶段 3 的冻结产品。阶段 6 检查新的 renderer 替代对未知修改的退回时，显式选择新候选：

```powershell
python -X utf8 components/render_diagnostics/benchmark/entity_takeover.py foreign --product components/render_diagnostics/build/takeover-stage6/radiance.jar
```

## 通过标准

实际来源登记、部件和贴图包装比较均执行，姿态与外观不冻结，资源重载／历史身份不跨代际，正常保存关闭。未知 Mixin 用例中部件接管拒绝、改变后的原始 Cube.compile 实际执行、输出属性与测试修改一致，覆盖表包含具体原因。原始输入对照不替代随机 PT 的逐像素等价、运动矢量读回或使用者目视验收。
