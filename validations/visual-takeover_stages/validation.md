# 原版接管与区块流水线目视验收

## 目标

在只装 NeoForge 与冻结 Radiance 的项目内实例验收区块流水线；阶段 1～3 的既有目视通过结果保留，不机械重做。新旧编号见[计划](../../documents/project/plan-render_takeover.md)。没有 probe、Custom Skin Loader、Zume 或其他模组。两份世界入口分别为新城市和小型夹具，后者另有程序化观察区。自动测试不替使用者填写新增视觉结论。

## 运行

从仓库根目录执行准备一次，再启动手工入口；准备不启动游戏、不重建或切换产品：

```powershell
python -X utf8 components/render_diagnostics/benchmark/takeover_acceptance.py refresh --product components/render_diagnostics/build/chunk-pipeline-worker3/radiance.jar --distance 32
.\gradlew.bat :render_diagnostics:runTakeoverAcceptance
```

窗口 2560×1440，全局音量 50%，音乐关闭，玩家 RecRivenVI，本轮视距 32。先选择“城市：合并验收”；小型夹具保留作短回归入口。夹具的资源包、PT 设置与冻结产物身份写在 result/prepared.json。已有实例使用 refresh，更换产品和指定视距并保留两份存档；首次创建才使用 prepare。普通画质与存档位置不被准备任务覆盖，不启动游戏。

阶段 2 收尾的历史自动室内对照使用同一项目内副本，自动静音、1920×1080，正常输入驱动姿态；不用于人工入口，也不同时运行：

```powershell
python -X utf8 components/render_diagnostics/benchmark/crouch_regression.py --product components/render_diagnostics/build/takeover-stage6-closeout/radiance.jar --mode TIMED --indoor
python -X utf8 components/render_diagnostics/benchmark/crouch_regression.py --product components/render_diagnostics/build/takeover-stage6-closeout/radiance.jar --mode FRAME_ONLY --indoor
```

TIMED 保存关联的 CPU／GPU、SHARC、历史与三次单帧 GPU 输入；FRAME_ONLY 不启用专项缓存插桩或双执行来源对照。自然发生设备丢失或失败时留下 STOP，必须分析后才允许另一项实验，不自动重复。

小型夹具内执行下面的原版命令进入程序化观察区；它同时切换创造模式，方便操作按钮。无需探针。

```mcfunction
/function stage67:enter
```

面向北侧，按钮从西到东依次为烟雾、远古守卫者模型、发光粒子、闪电、掉落物、带拖尾烟花、下雨、放晴；邻近篝火、水池、树叶、玻璃和半透明文字可同时观察。靠近并拾取掉落物检查拾取动画。不要连续触发大量烟花或闪电；这是正确性入口，不作性能压力比较。

## 通过标准

2026-10-21，使用者已确认阶段 2（原 6）收尾中发现的问题均已修复，并观察到明显性能提升；反馈和楼内新掉帧的独立调查见[当前记录](../../documents/research/investigation-building_entity_drop.md)。2026-10-22 阶段 3 实例化另已获使用者确认通过。新候选的区块流水线仍待人工观察，不继承旧包未执行的结果；长期稳定性与历史 GPU 故障不在本次短清单中闭合。

本轮区块流水线重点观察：

1. 城市进入与连续移动时，区块应持续补齐；停止后不留下永久空洞，不因后台加载丢失反射、阴影中的几何。
2. 平静后和持续加载时分别放置／挖掘方块，重点跨区段边界；检查最后一个方块删除、重新放回、玻璃、树叶、水与发光方块的依赖更新。
3. 快速跨过一段区域再返回，检查旧位置不出现错误新几何，已就绪内容不被较旧构建覆盖。
4. F3+T、正常保存退出并重开，几何、材质与来源效果恢复，没有卡在退出或重载。

以下是阶段 1～3 的历史回归参考，不要求重做已经通过的全套项目；此前保存的楼内入口和小型夹具继续保留。

旧短清单如下，其他已通过范围保留原结果：

1. 城市建筑内缓慢转视角、短蹲、走动后静止：床、箱子、旗帜、头颅、告示牌和书本不缺部件，不串色；旗面和书页持续运动，连续转动没有明显历史拖影。告示牌附近放挖后，牌体与文字不闪失。
2. 到小型夹具，开合相邻箱子，观察不同装备／颜色的实体和幼年动物；蜘蛛／末影人眼睛、充能苦力怕滚动电弧、foil 和普通装饰层的颜色、顺序及照明保持原样。多个共享模型对象的动作不能同步串用。
3. 骑乘船和矿车并转视角，检查船桨、船内水面、第一人称和手持物；反射与既定折射所有权保持原样。不把已记录的优先级重复成像算本轮已解决。
4. 删除重建一个对象或更换物品框内容，再执行 F3+T；重复前两项观察，不残留旧贴图、旧姿态或旧模型。
5. 正常保存退出，重进城市和小型夹具；场景完整、材质恢复、没有崩溃。未观察项保留待验。

自然发生故障立即保存证据并暂停当前用例，不自动重启；未知 GPU 根因、完整 G3、长期稳定性与公开二进制许可保持独立边界。
