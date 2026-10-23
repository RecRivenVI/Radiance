# 原版请求负责方与阶段 6、7 核对

## 目标

在独立原版实例核对粒子、程序化、GUI、离屏和呈现请求的负责方及语义。沿用阶段 0～3 的已冻结夹具，不测性能，不操作外部实例。

## 运行

从仓库根目录先编译真实发行包及探针，再执行完整路线。不要把 `probeJar` 缩写当作 `probeClasses`。

```powershell
.\gradlew.bat :version:1.21.1-neoforge:probeClasses :version:1.21.1-neoforge:distributedJar
.\gradlew.bat :render_diagnostics:runVanillaTakeover -Pcase=full
```

实体摄像机的苦力怕／蜘蛛／末影人后处理及方块遮挡覆盖另有定向补充，不重复整套粒子和城市路线：

```powershell
.\gradlew.bat :render_diagnostics:runVanillaTakeover -Pcase=effects
```

GUI 共用准备路径的定向回归只执行明暗／移动背景准星、HUD 物品栏、背包、非暂停菜单、真正暂停的模糊菜单、关闭恢复及重载后的离屏读回：

```powershell
python components/render_diagnostics/benchmark/vanilla_takeover.py gui-candidate --capture
```

2026-10-23 的候选先用下面的无人值守入口，额外检查无模糊暂停与返回背包。该入口从启动设置起关闭 FG，只用于原生绘制、Mixin 和生命周期回归，不取代 FG 输入或生成帧验证。

```powershell
python components/render_diagnostics/benchmark/vanilla_takeover.py gui-policy-native --capture
```

随后下列会话需要使用者保持前台，构建和自动回归后先通知，不自行启动；它不是性能实验。

```powershell
python components/render_diagnostics/benchmark/vanilla_takeover.py gui-policy-candidate --product components/render_diagnostics/build/gui-policy/radiance.jar --capture
```

对照已冻结的旧包时用 `gui-baseline --product <仓库内发行包路径>`；`--capture` 仅开启原生输入采集，不改变产品补帧政策。该切片不要求执行无关的完整城市与粒子矩阵。

## 通过标准

必须完成 R0～R5 与 R7：加载与菜单、小型模型／实体夹具、原版城市进入与移动、全部注册粒子及非注册来源、GUI 预览／缩放／resize、离屏读回、屏幕覆盖、重载，以及两个世界保存重进和正常关闭。实际加载 DLL 与发行包内身份一致；设备丢失保全后停止，不自动重试。

每个实际绘制族有正请求数，未消费、多次消费、未知均为零，没有残留未结束请求；SERVICE 加载单独核对。静态无绘制的发射器明确登记，不把零请求当作覆盖。原生成器位置、RGBA、UV／材质与共享面输入对照通过；GPU 离屏的源覆盖、透明叠加、层顺序、字体淡出、视口／裁切、深度／模板与颜色写入按像素断言。

`baseline`、`procedural` 只用于初始计数或排错切片，不满足完整巡检。CPU 提交确认不表示 GPU 已完成；最终动态目视仍由使用者完成。

`effects` 经原版 `setCameraEntity` 选择三种原生后处理，再用本验证世界的实体所在方块触发遮挡覆盖；四种来源必须有实际消费，最后恢复玩家视角、重载、保存和正常关闭。它是完整路线的补充，不替代完整路线。

`gui-*` 必须取得 `GUI_REVIEW PASS`、真实的背包／非暂停菜单世界推进及 PauseScreen 停止推进证据、重载后附件与字体像素断言、保存和正常关闭。提示通过原版绘制 API 触发，不冒充物理鼠标悬停。`gui-policy-native` 全程关闭 FG，检查原生 RGB、Mixin 和生命周期，不要求不存在的 HUD-less／UIAlpha 或生成计数。

启用 FG 的捕获输入须显示准星不在 HUD-less 且 UIAlpha 有覆盖。旧 `gui-candidate` 对应 96 号历史门控；`gui-policy-candidate` 必须根据当前 `FG_INPUT` 理由检查：全屏菜单模糊关闭，未模糊暂停与背包在输入合法时允许，恢复后不遗留上一帧关闭状态；用户开关不被应用策略改写。

生成帧另以呈现所有者的实际 SDK token、status、呈现数关联场景；必须有额外生成正计数，不能只看开关／可用状态。记录 GLFW 状态但不当作 SDK 焦点门槛通过或 Windows 前台证明；没有正计数时记为未验证，并保留最早失败边界，不循环启动。截图是原生帧，离屏 GPU 读回及 SDK 输入都不能代替生成帧动态目视。本切片不充作全部巡检或全部 Screen 兼容。
