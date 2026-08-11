# 管线配置界面 GUI 缩放修正

只修改 RenderPipelineScreen.java，未改动 MCVR / DLL / 实例配置。
- 移除额外 0.75 倍变换及鼠标逆变换，统一使用 Minecraft GUI 坐标。
- 顶部工具栏按可用宽度换行；下拉菜单与内容区同步跟随实际工具栏高度。
- 属性控件响应可用宽度，长标签省略，避免与控件重叠。
- 在绘制控件前更新位置与可见性，避免滚动/调整大小时的一帧错位。

verifyDistributedJar（含 Java 编译）通过；git diff --check 通过。本次未新增低价值的布局镜像测试，视觉与点击验收留给用户启动游戏。
两个实例已部署并验证 SHA-256，一致性与备份位置见 deployment.json。原生 core.dll 与 Streamline 部署版一致。
修改前源码：RenderPipelineScreen.before.java。构建：build-final.log。
