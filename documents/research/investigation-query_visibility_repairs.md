# 所有者续接与船内排除域修复

2026-10-17，依据已接受的[成像决策](../design/adr-1-query_visibility.md)与[集成核验](audit-query_visibility_integration.md)，集中修复自身派生表示、实际射线续接及船内有界排除域。不处理浮空水晶，不进行性能实验，不启动客户端，不提交或推送。新候选的目视结果不继承旧包的六组通过。

## 问题

使用者确认折射重现时观察的是自身身体，当时尚未检查自身轮廓和单面背面。此前确认的三个失败分别为自身折射重现、船内水面未隐藏、自身优先级发光效果未隐藏。旧证据和原始调查保留，不用后续修复改写首次失败。

## 方法

- 核对 Prism 保留配置和内嵌／提取的 Vanilla PT 包，排除旧 shader 包复用；透明分支默认配置为 deterministic。
- 比较 Vanilla PT 确定性分支的全局 payload 与 Advanced 独立的 primary／secondary 状态；核对 priority 的真实循环。
- 使用原版 BoatModel、ChestBoatModel、BoatRenderer 的模型和姿态生成控制顶点，再检查有限凸域和 GPU 包含判定。
- 使用真实 TLAS 的 GPU 接缝执行产品 priority 循环，并原样提取产品 `initTransparent` 与确定性分支调度代码；仅注入受控命中／光学事件，保留实际分支顺序和状态辅助函数。

## 发现

### 所有者与材质是不同维度

自身派生轮廓／优先级文字现在复用主体的摄像机所有者决定：附着、清醒的自身归入 SELF，仍参与传光，但不进入普通或优先级成像。其他实体、脱离摄像机与睡眠状态保留原有行为。不能通过 SELF 与 PRIORITY 按位相加实现隐藏，因为 Vulkan 按位相交。

这些派生生产者独立保留原有物理材质捕获资格，不再让自身改分 SELF 后顺带降级到 no-reflect。普通和 Sable 的四个直接调用方一同调整，不改变已通过的其他轮廓颜色、厚度、发光或来源材质。

### 确定性折射的实际失败路径

Vanilla PT 先调用 `traceTransparent` 追踪反射，该函数经过 `traceSecondary` 改写全局 `mainRay`。随后折射再次把这个全局值当作分支来源，继承反射已经切换的传光身份；即使折射的 lobe 标为 2，也无法从传光恢复成像。实际查询因此允许 SELF，符合自身折射重现的反馈。水面辅助状态与随机种子也可能受同一次分支污染。

修复在调度两条分支前固定 `splitSourceRay`，反射、折射均从同一份首命中状态初始化。反射仍为传光，折射继承摄像机身份；不是把反射中的自身也隐藏。Advanced 反射使用 `primaryViewState`，折射使用其自身或 secondary scratch，没有沿用同一个全局污染入口，不重复改写其架构。

priority 原循环则无条件把第二步切为传光。现改为先读取上一事件，再清除继续位：覆盖率续接和连续折射保留直接成像；反射／漫反射不可逆地转为传光。首次优先级选择后，续接使用用途对应的世界通道，不反复选择优先级对象。

### 船内排除域

原版 water mask 是深度盖片，并非其自身薄体积就是船内空间。原模型静止水位反例仍保留在旧调查。新来源适配器由 BoatModel 的实际 bottom 部件取得船底，在遮水盖片局部空间中，以盖片的厚度轴和源船底近侧表面生成有限控制体积；不使用世界高度或船尺寸常量，不删除来源船体表面。

适配发生在 BoatRenderer 原有 waterPatch 绘制位置，只改 PT 捕获的控制顶点，保留未完全潜水条件。船／箱船继承同一来源；竹筏没有 WaterPatchModel，不凭空创建排除域。缺少来源 bottom 的自定义模型保留原有限遮罩，不能宣称任意第三方船型均已适配。

控制网格保持完整捕获，不进入持久表面快路径或 TLAS；既有原生凸平面、每帧缓冲与资源保活保持不变。所有水面查询和 Advanced 发光候选继续消费同一个域。排除范围到源船底截止，船外、船底以下及其他合法水面保留；所有者与 Sable 变换仅应用一次。

## 结论

产品修复和针对性回归已实现；最终构建、GPU 与分发结果由本节后续追加和[当前状态](../project/status-current.md)固定。现有 GUI、线条、附加层、代际与退休代码保留。没有用未知 GPU 根因、静态读取或输入夹具代替新候选的目视验收；浮空水晶继续后续处理。

### 首轮门槛与纠正

首次定向命令使用了缺少 `version:` 前缀的 Target 路径，Gradle 在任务选择时拒绝，未执行测试；随后正确路径的船模型及所有者测试通过。首轮完整门槛的非 GPU CTest 为 67/68，`vanilla-effects-contract` 的源码形态断言仍要求 priority 中出现固定 `queryMask(queryTransport`，与已实现的事件驱动续接不一致；没有删掉该检查或恢复错误的固定掩码，改为同时要求实际事件推进和用途掩码，并单独重测该检查通过。

GPU 接缝另以旧包的原样 split 调度和旧 priority 循环分别作反例，两者均发生预期断言失败，无设备丢失。旧 priority 仅去掉测试接缝自带的 payload 声明，不改其控制流。候选行为回归执行产品循环与原样提取的生产调度，不能把源码字符串检查作为行为证据。随后加强 coverage 用例为继续位加 lobe 0，以覆盖“先读取继续事件，再清位”的顺序；最终门槛重新执行。

原始命令、首次失败与定向结果分别保留在 `D:/Workspaces/Scratches/RadianceSemanticsAudit/query-repair-java-first.log`、`query-repair-java-second.log`、`query-repair-targeted.log`、`query-repair-full-gates.log` 与 `query-repair-source-guard-retest.log`。这些日志不覆盖或冒充最终重测。

### 最终自动验证与投放

最终执行 `gradlew.bat spotlessApply check verifyRelease :native_renderer:gpuTest verifyCompliance --strict` 成功，日志为 `D:/Workspaces/Scratches/RadianceSemanticsAudit/query-repair-full-gates-2.log`。GAME 305、JNI 4、probe 28 通过；bootstrap 11 通过、2 项既有硬件对照跳过；非 GPU CTest 68/68、GPU 18/18 通过。两套 pack 的 232 个离线变体编译和 SPIR-V 校验通过；696 个内嵌类字节码与编译输出一致，119 个 pack 入口和 8 个共享查询／排除域成员与源码一致，发行结构及 JNI 头文件检查通过。未受影响且标为 UP-TO-DATE 的构建任务只按复用记录，不冒称全部重新执行。

最终受控 GPU 用例使用完全相同的 TLAS、光学事件接缝和 coverage lobe 0：旧 split 调度 80/80 用例发生预期断言失败，旧 priority 循环 50/80 失败，新候选 80/80 通过。这是行为反例，不是 GPU 崩溃；完整着色、降噪与客户端显示没有被这个接缝测试代替。原始结果、源片段哈希和可重复脚本为 `query-repair-final-red-green.json` 与 `query_repair_gpu_comparison.py`，位于同一外部证据根。

真实 BoatModel／ChestBoatModel 测试覆盖静止水位、实际多边形膨胀、负缩放及宿主姿态；BoatRenderer 的实际字节码确认唯一 waterPatch 调用点及对应 ListModel 局部变量。没有启动客户端，实际 Mixin 合入后的游戏画面仍待使用者验证。来源缺少 bottom 时保留原有限盖片，GUI 光栅预览不改写控制几何。

20:21 按实例原子锁、停机检查、暂存哈希与原子替换流程完成 Prism 投放，未启动游戏。JAR 为 `AEA65916F589775E00A03437AE220E5DF27774571204A05818FF43B95FE548B4`，core 为 `2B48A31D8A73D533DDC231AB5326306BC3186A44CB09ECEDC7D8066F99B6000A`；构建、安装目录与内嵌 DLL 一致，收集发行 JAR 与构建 JAR 一致。旧 JAR 经 Windows 回收站收敛，其他模组、实例配置及已有选项哈希不变，回执为 `D:/Workspaces/Scratches/RadianceSemanticsAudit/query-repair-prism-deployment-receipt.json`。

完整源码及必要未跟踪文件由 `query-repair-source-manifest.json` 与相对 checkpoint-13 的 `query-repair-source-delta.zip` 固定，使用原始字节 SHA-256、不作换行规范化，14 个 Git 子模块按固定提交核对。产物及源码关联为 `query-repair-artifact.json`。新候选的提取和实际加载 DLL 证据必须等待使用者启动后取得；本轮只有内容寻址索引确定的预期路径，没有伪造实际加载记录。三个针对性目视结果、长期稳定性、既有 GPU 根因和公开二进制许可仍未因此关闭。

### 2026-10-17 后续部分手工反馈

使用者自行启动并继续验收后，确认本候选的自身折射隐藏、自身发光轮廓隐藏和船内水面行为正确。此前交付时尚待目视的记录保持原时间范围；最新反馈、完整候选身份和待办入口统一见[当前状态](../project/status-current.md#集中修复后的部分手工反馈)。未据此重跑客户端、采集实际加载证据或补写未反馈的模式组合。

厚玻璃后方此前的磨砂观感似乎也改善，但使用者要求随折射重做同步检查；不把可行的分支状态关联当作该观感的已证实根因。新发现乘船操控时船只运动更新频率明显低于游戏 FPS，列为优先系统性调查，当前没有顶点转换／实例化缺陷结论。手臂与折射手臂同时出现及其他优先级内容的类似重复仅登记，明确暂不研究或修改。

原版云、多种可切换半透明方案、统一折射参数、光源数量相关开销和原版无 PBR 发光账本已合并到现有路线图，本次只更新维护记录，不开展新功能、性能实验、部署或 Git 写入。
