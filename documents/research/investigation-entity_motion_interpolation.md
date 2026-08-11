# 实体运动插值与船只转向调查

2026-10-17，依据使用者在 AEA65916…548B4 候选中观察到的乘船运动卡顿，定向核对来源姿态、捕获与原生实例更新。不启动客户端，不进行性能实验，不改变已接受的成像、传光、材质或资源退休规则。

## 问题

使用者观察：坐在船内操控时，船只运动更新频率明显低于游戏 FPS。该描述尚不能区分转向、位置或动画。调查不能把顶点转换或持久模型预先当成根因，也不能将一个明确缺陷的修复写成全部运动问题已经目视解决。

## 方法

- 比较当前依赖实际加载的 `LevelRenderer.renderEntity` 与 `EntityProxy.queueEntitiesBuild` 的 dispatcher 参数；来源对照在无客户端测试中执行，不只比较源码字符串。
- 追踪 `BoatRenderer` 的根部旋转、受击／气泡姿态和 `BoatModel` 船桨动画；检查 ordinary 与 TextDisplay 两个 dispatcher 调用入口。
- 检查完整捕获、持久 ModelPart、JNI 批次、native 刚性实例与世界准备，区分稳定局部几何和每帧实例姿态。
- 盘点留存 Minecraft 1.21.1 的 119 个实体渲染器源码文件、37 个 typed render 实现；按 yaw 参数的实际消费方式分类，不把所有实体都当成同一种插值。清单为 `D:/Workspaces/Scratches/RadianceSemanticsAudit/boat-motion-renderer-inventory.json`。

## 发现

### 确认的来源参数缺口

原版 `LevelRenderer.renderEntity` 使用同一个 partial tick 插值位置和 yaw：`Mth.lerp(partialTick, entity.yRotO, entity.getYRot())`。现有 `EntityProxy` 已插值位置，却在普通实体与 TextDisplay 两个分支直接传入 `entity.getYRot()`。该值在游戏 tick 改变，渲染帧再多也不会补出中间根部朝向。

`BoatRenderer` 直接使用传入 yaw 执行 `180 - yaw` 的 Y 轴旋转，未再次插值。因此该缺口足以造成船体转向按 tick 跳变，是已确认的代码缺陷；位置仍使用插值，不能由此断言所有直线移动或网络抖动都已解释。

| 来源或阶段 | 核验结果与范围 |
| --- | --- |
| 普通船、箱船、竹筏及箱筏 | 共用 BoatRenderer，根部消费 dispatcher yaw；同一缺口适用 |
| 矿车及其派生渲染器 | 轨道切线可覆盖 yaw；未取得合法切线的回退仍使用传入 yaw，不能简单称全部矿车始终有同一缺陷 |
| PaintingRenderer | 使用传入 yaw；普通画通常不转动，不能把源码风险当作已观察到的画面故障 |
| 生物、箭矢、展示实体 | 各自对身体／头部、yaw/pitch 或展示姿态另做插值；修复共享输入，不再次对这些内部结果插值 |
| Simulated 图解实体与发射皮搋子 | 留存 1.3.2 的 DiagramEntityRenderer 直接用传入 yaw 旋转，属于统一输入的受益方；该来源自行读取的瞬时 pitch 不属于本轮接管遗漏，未改。LaunchedPlungerEntityRenderer 自行使用 partial tick 计算方向，不叠加第二次插值。只作来源核验，不把两者记成新增目视失败 |
| Sable 宿主实体 | 位置和宿主姿态由 `entityRenderTransform` 使用当帧 partial tick；共享 yaw 缺口仍由统一 dispatcher 输入修复，不新增宿主旋转或重复补偿 |
| 位置、船桨、受击和气泡姿态 | 位置已有逐帧插值；船桨使用 `getRowingTime(partialTick)`，受击／气泡使用来源 partial tick；未发现本轮同一种瞬时 yaw 输入问题 |

### 捕获与实例更新

完整捕获每帧执行来源 renderer，再将插值后的世界原点分别交给实体和船内排除域。持久 ModelPart 只缓存局部几何；每次捕获均复制当前 PoseStack 矩阵，`RigidModelCapture.submitAll` 每帧写入位置与矩阵，native 复制到本帧 `RigidInstance`。`WorldPrepare` 使用该实例建立当帧 TLAS 变换，并独立登记上一帧历史和资源保活。没有在上述共用链路发现 tick 计数节流；该结论是已读代码范围，不是所有模组 renderer 的运行证明。

### 修复与自动对照

普通与 TextDisplay 捕获统一通过 `renderEntityAtOrigin` 的最小 dispatcher 接缝；维持原点捕获、原 pose、buffer、light、partial tick 与回调顺序。接缝先保留瞬时 yaw 作为反例，再与依赖中实际 `LevelRenderer.renderEntity` 的输出对照，确认失败后仅把 yaw 恢复为来源线性插值。无需修改顶点格式、GPU 转换、BLAS/TLAS、历史身份或退休机制。

新增行为回归覆盖不同实体家族、同一 tick 内多个渲染时刻、端点、跨角度边界与冻结时间；额外检查两个真实生产入口均调用该接缝。构造测试对象不创建 Minecraft、窗口、纹理系统或 Vulkan 设备，不是客户端运行验证。最终执行结果在完成门槛后追加。

首次定向执行因独立用例未初始化 NeoForge 的 `LoadingModList` 而在 Bootstrap 失败，没有执行到插值行为；按既有无客户端测试初始化空模组元数据后重测。修复前 3 项回归中 2 项发生预期失败：原版在 partial tick 0 输出 20°、旧输入却输出 100°；另一用例应从 15° 起逐帧变化，旧输入一直为 31°。入口接线检查通过。首次夹具失败和真实行为反例分别保留于 `boat-motion-fixture-init-failure.xml`、`boat-motion-red-test.xml`，不将初始化错误充当根因证据。

## 结论

已确认 dispatcher 漏掉 yaw 插值，会使直接消费该参数的船体转向按游戏 tick 更新。正式候选只恢复原版输入合同，不增加平滑滤波、不更改服务端／网络更新、不降低动画频率。用户所述全部卡顿是否因此消失仍待候选目视，不把静态调用链或无客户端对照称为完整游玩通过。

第一人称手臂／其他优先级内容重复成像、折射重做、云和光源性能不纳入本次修复；GPU 故障、浮空水晶与长期稳定性保持各自边界。构建与自动回归通过后，使用者已确认退出才投放 Prism，不代为启动，不暂存、提交或推送。

### 构建、分发与投放结果

修复后三项新增回归通过，其中原版行为对照实际执行五类实体 × 四组角度 × 七个 partial tick 共 140 次。连同 RigidModelCapture、PartModelCapture、BoatWaterExclusion 和 EntityOutlineVisibility 共 28 项定向通过。完整 `gradlew.bat spotlessApply check verifyRelease verifyCompliance --strict` 成功，GAME 308、JNI 4、probe 28、bootstrap 11 通过／2 跳过，CPU CTest 68/68；严格检查 1966 个文件，零失败／零警告。原始日志为 `boat-motion-green-targeted.log`、`boat-motion-full-gates.log`；未受影响的 UP-TO-DATE 任务按复用记录。GPU 专项未重跑，原生 DLL 内容保持不变，不将旧 GPU 18 项结果冒称本轮执行。

新发行 JAR 为 `171F38FC97FD46472B1690014BA2BB76981BDDBD1369B495E898B1D1FF222354`，GAME 为 `022742A0079C1DE378B0C5EBBFC290F11F64A70AA90A847DA2F93F430156BC98`，构建／安装及内嵌 core 均为 `2B48A31D8A73D533DDC231AB5326306BC3186A44CB09ECEDC7D8066F99B6000A`。667 个 GAME 类中仅 EntityProxy 及其八个嵌套类的字节不同，29 个 bootstrap 类不变；嵌套类差异包括源行信息，不据此虚报额外逻辑修复。其他 GAME 资源、原生 DLL 与 shader 内容一致。

首轮包比对因两套 shader ZIP 容器字节变化而停止，没有投放；进一步逐成员比较 Advanced 134 项、Vanilla PT 107 项均一致，差异是重封装元数据及随之更新的 runtime.index 摘要，不是着色器内容变化。首次和最终比对分别保留为 `boat-motion-package-comparison-first.json`、`boat-motion-package-comparison.json`。没有以删除检查或产品绕过掩盖差异。

使用者明确确认退出后，21:23 通过 Prism 原子锁、进程与停机检查完成投放；旧 JAR 经 Windows 回收站收敛，其他模组及现有设置哈希一致，锁已释放，未启动游戏。部署回执为 `boat-motion-prism-deployment-receipt.json`；源码完整清单和基于保留 checkpoint-13 的校验增量为 `boat-motion-source-manifest.json`、`boat-motion-source-delta.zip`。这些文件与产物清单均在 `D:/Workspaces/Scratches/RadianceSemanticsAudit/`。

建议使用原场景先直行，再连续缓慢左右转向，观察船体、船桨及船内水面是否连贯；条件允许再看箱船／竹筏、下船后的外部观察和矿车回退路径。无需重做已反馈的整套视觉验收；本候选的实际加载、乘船目视和剩余平移卡顿仍待反馈。未启动客户端、未做性能实验，没有 Git 写入或旧 MCVR 改动。

### 2026-10-17 手工验收通过

使用者随后明确反馈船只运动修复通过，追加的矿车、船桨和船内水面剔除均通过。结果对应本研究的 171F38FC…222354 投放候选，完整身份与[反馈记录](../project/status-current.md#船只修复后的手工通过)相联；不追溯改写修复前失败、夹具初始化失败或交付时待验的事实。

本次只追加使用者结果，没有产品修改、重建、游戏启动、性能实验或新机器取证；不从目视通过推断全部载具、模组实体、PT 配置或长期稳定性均已验证。船只专项按已反馈范围完成，后续优先工作回到现有路线图。
