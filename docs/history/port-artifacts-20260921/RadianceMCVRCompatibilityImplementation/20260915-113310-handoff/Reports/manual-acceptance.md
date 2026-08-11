# A/B 人工验收（由用户执行）

工程产物：Radiance 0.1.5-alpha，Minecraft 1.21.1，NeoForge 21.1.250，Java 21。
本次代理未启动 Minecraft、参考 GL 窗口或任何 UI 自动化；以下均待用户执行。

## 准备好的两组

- A：`../Acceptance/base/mods/Radiance.jar`。SnakeYAML 2.6 已随 GAME 子包提供，native/runtime/shaders 在外层单 JAR。
- B：`../Acceptance/full/mods/` 的四个外层 JAR：Radiance、Create 6.0.10+mc1.21.1、Sable 2.0.5+mc1.21.1、Create Aeronautics 1.3.2+mc1.21.1。
- B 的合法嵌套依赖包括 Flywheel 1.0.6、Ponder 1.0.82（含 Catnip）、Registrate、Veil 4.3.2、Sable Companion 1.6.0、Simulated/Offroad/Aeronautics 1.3.2，以及 Veil 的 GLSL/Molang 库。无需重复安装嵌套 JAR。
- 完整外层/嵌套文件 SHA256 在 `../Evidence/final-artifact-hashes.json`，版本范围原始核验在 dependency-ranges.tsv / dependency-ranges-result.log。

## 准确启动方式

在 PowerShell 中执行下面公共准备，再选择一条启动命令。这里只给命令，代理没有执行启动。

```powershell
Set-Location 'D:\Workspaces\Repositories\GitHub\RecRivenVI\Radiance'
$env:JAVA_HOME = 'C:\Program Files\BellSoft\LibericaJDK-21-Full'
$acceptanceArg = '-Pradiance.acceptanceDirectory=D:/Workspaces/Artifacts/RadianceMCVRCompatibilityImplementation/20260915-113310-handoff/Acceptance'
```

A 基础组：

```powershell
.\gradlew.bat runManualBase $acceptanceArg --console=plain
```

B 完整组：

```powershell
.\gradlew.bat runManualFull $acceptanceArg --console=plain
```

这两个配置使用 packagedLaunch 和实际分发 JAR，排除源码 MOD_CLASSES 注入；启动前校验外层 JAR 集合。默认窗口参数为 2560×1440。首次运行可下载所需运行依赖。目录与用户 Prism 实例分离；不会迁入用户存档/资源包。后续重新执行构建可能更新此目录中的 Radiance.jar，验收时应先确认哈希与当前清单一致。

## A 基础组检查

1. 从启动、加载画面进入主菜单，打开设置、资源包与模组列表；检查文字、图标、窗口缩放与全屏切换。
2. 在本验收目录新建测试世界：昼夜、晴雨、生物群系雾、主世界/下界/末地切换；检查天空、云、天气、流体/tint、透明材质、阴影。
3. 检查普通/副手、第一人称自身模型间接光与阴影、第三人称、睡眠与 detached 例外；检查粒子（含 White Ash）。这些遵守既定视觉规则，不以“偏离原版”本身判错。
4. F3+B/F3+G 保留原配色并参与世界 PT/发光；一般 debug 不额外主动发光。检查名称牌约50%背景、发光文字、物理和置顶语义；检查既定轮廓、旁观与全屏/GUI效果。
5. 放置/破坏普通方块及方块实体，检查10级破坏纹理乘色；加载/卸载区块，资源重载、切换世界、退出再进入，检查资源销毁和错误日志。保留 F3 真 GPU 计时。

## B 完整组检查

1. 模组列表核对上述完整组合；Flywheel 应使用 `radiance:vulkan_instancing`。不能以 `flywheel:off`、原 GL backend 或普通 BER 回退作为通过。
2. Create：旋转/移动机械、实例增删与跨大距离相机原点更新、透明/发光材质、破坏覆盖、ValueBox/轮廓、菜单/设置、图集；Ponder 教学页、场景、文字和便签正常绘制。
3. Sable：移动/旋转子空间、区块和实体/方块实体、剔除边界、轮廓、光照场景切换与 skyScale；跨区块移位、资源重载、卸载后重新进入。
4. Aeronautics：Levitite 与 ghost 层、burner 火焰、balloon heating、tessellation/固定阶段绘制；检查主目标 depth 与离屏效果互相影响。
5. Simulated：diagram 内 block/fluid/entity/Flywheel、spring stress、laser/lens/diode、StickyNote/嵌套屏幕；检查 alpha、深度、灯光、矩阵和 viewport 恢复。
6. EndSea：真实 shadow FBO、spread 与最终海面绘制；进入/退出相关维度和菜单，检查 shadow→sea→post 的遮挡与时序。
7. Offroad：多目标挖掘的共享破坏进度、普通目标及有 visual 的目标。Physics Staff 实际光束走 Ponder outline；固定版本未使用的 staff_overlay 缺失资源不作为画面通过证据。
8. 对整个组合执行资源重载（包括连续触发）、切换世界/维度、关闭屏幕、正常退出。记录 shader 编译失败、未知材质拒绝、Vulkan 错误、资源泄漏或恢复错误。

## 回传证据与判定

每项记录组别、JAR哈希、GPU/驱动、具体场景和操作、预期/实际、截图或视频、对应时间日志。日志位于各自目录 `logs/latest.log`，崩溃文件在该目录 `crash-reports`。首次失败即可保留日志；中间模组子集仅在必要时用于归因，不是额外正式验收关卡。

编译、合同和无窗口 GPU PASS 不代替本表的画面、Mixin 装载与完整组合运行验收。一般未知 shader、未登记世界目标、未支持的额外 GL 消费者会明确失败/拒绝，不能把静默丢绘制当成功。
