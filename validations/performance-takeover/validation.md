# 刚性接管样板性能对照

## 目标

仅在 2026-10-19 阶段 2 授权下，使用同一已保存夹具、设置、路线、30 秒预热和 1920×1080 静音配置，按 ABBA 比较冻结旧发行包与新包。双执行及重型来源归因关闭。FRAME_ONLY 静止／移动和 TIMED 静止／移动各 20 秒，真实帧 p50/p95/p99、CPU／GPU 与源工作量分别报告。

## 运行

从仓库根目录执行：

```powershell
.\gradlew.bat :version:1.21.1-neoforge:probeClasses :version:1.21.1-neoforge:distributedJar
.\gradlew.bat -I components/render_diagnostics/benchmark/takeover_classpath.init.gradle dumpTakeoverLaunchClasspath
.\gradlew.bat :render_diagnostics:runTakeoverComparison
.\gradlew.bat :render_diagnostics:analyzeTakeoverComparison
```

工具使用已经生成的 packagedClient VM／程序参数，在本项实例中加载所选的实际发行 JAR 与独立 probe JAR，不使用开发 MOD_CLASSES 替换产品。每个进程结束后校验归档再清理复制环境；自然设备丢失立即停止整个对照，不自动重试。

## 通过标准

每窗有足够真实帧，身份、设置、夹具、模式和相机路线一致；无错误、丢样、设备丢失或运行期间产品变化。报告均值、p50/p95/p99、重复进程波动与分项；不能把嵌套时间相加。只在整帧净收益超过运行间波动时称为优化成功，否则记录持平、退化或未能判定。功能符合度、长期稳定性及目视验收另行核销。
