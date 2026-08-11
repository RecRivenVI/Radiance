# 附加层与动态纹理同类路径核验

2026-10-17，使用者正在验收眼睛／电弧修复候选，本轮只核对直接同类的绘制状态与附加层链路。确认旋风人／风弹的主 UV 变换遗漏，以及普通重合附加层尚无通用合成合同；后者的具体视觉失效范围仍待验证。本轮不改产品、不投放新包、不启动客户端、不测性能、不提交或推送。

## 问题

蜘蛛／末影人眼睛的旧故障来自合成资格过严，电弧来自 TextureMat 未进入 PT。需要检查同一机制是否还存在其他生产者，不能因两个对象修好就宣称全部附加层或动态纹理已等价。

当前候选 JAR 为 `65A2E09463350081597CC814F56B5781617D5A1B71225ED234E53EB53BC8E422`，core 为 `328E5F7EEA9C7969ECE37F8BEC2541658C050EB7BDA6AD4B94CC5AA161D56E0A`；修复、构建与回归见[上一轮记录](experiment-emissive_layer_repair.md)。本轮新增核验文档不属于那份冻结源码，但没有改变其产品输入或安装包。

## 方法

- 读取当前来源适配器、状态封批、普通／持久模型入口、原生眼睛合成和两套 PT 的消费者；不把眼睛加法覆盖推广成任意半透明覆盖。
- 从当前构建实际解析的 `neoforge-21.1.259-client-extra-aka-minecraft-resources.jar` 枚举 core vertex shader 的 TextureMat 使用者，并与实际 RenderType、renderer、层源码交叉核对。原版源码参考位于使用者指定的 `D:/Workspaces/References/minecraft-references/minecraft-1.21.1/src/`。
- 使用现有已编译产品类、同一依赖 classpath 与真实 WardenModel／BreezeModel，执行仓库外的 headless 最小接缝。只调用来源分类、UV 参数与模型顶点生成；不创建 Minecraft 客户端、GL context 或 Vulkan 设备。
- 第一遍随 PATH 使用 JDK 25，输出了 LWJGL JNI 版本提示；结果保留但不作为目标 Java 版本验证。随后明确使用 BellSoft JDK 21 重跑，同样的数值结果成立，无该提示。日志另存，不覆盖第一遍事实。
- headless 依赖默认 Log4j 曾向冻结的根 `logs/` 写入新的 debug／空 latest 日志；已按哈希移至仓库外的 `companion-audit-library-logs/` 并留下迁移回执，没有删除旧证据或归档。后续独立接缝必须把工作目录设在仓库外，避免默认文件 appender 写回冻结路径。
- 对实际 Create 6.0.10、Sable 2.0.6、Aeronautics bundled 1.3.2 及内嵌 JAR 做只读字符串／常量候选枚举；14 个 archive、6,930 个 class。它不是完整调用可达性或反射分析。Veil 按实际内嵌 4.3.2 核对，未使用较新的 4.5.0 代替。

机器证据和最小接缝在 `D:/Workspaces/Scratches/RadianceSemanticsAudit/`：`CompanionLayerAudit.java`、`companion-audit-production-behavior-jdk21.json`、`companion-audit-behavior-jdk21.log`、`companion-audit-texture-matrix-inventory.json`、`companion-audit-mod-candidates.json`。接缝使用已存在的测试 classpath，不把本次诊断注册为产品功能或改写已部署包。

## 发现

### 动态纹理与同帧状态

实际资源有 7 个使用 TextureMat 的 core vertex shader：energy_swirl、breeze_wind，以及 5 个 glint 变体。以下只评价相关传播机制，不等于全部颜色／alpha／光照语义通过。

| 来源 | 当前路径 | 判断 |
| --- | --- | --- |
| 充能苦力怕电弧 | EnergySwirlLayer → energySwirl → ENERGY_SWIRL shader → 不可变主 UV 变换 | 本轮接缝再次得到变换后的 UV；上一轮修复在当前包中，目视仍由使用者执行 |
| 凋灵护甲 | WitherArmorLayer 与 CreeperPowerLayer 同继承 EnergySwirlLayer，分别计算自己的来源偏移 | 共用已修入口；没有发现独立遗漏，实际动态外观未代填通过 |
| 旋风人风体 | BreezeWindLayer → breezeWind → BREEZE_WIND shader | 确认遗漏：主 UV 变换资格只登记 ENERGY_SWIRL，因此滚动偏移被丢弃 |
| 玩家／旋风人风弹 | WindChargeRenderer → breezeWind | 与上一行同一确定缺口；不因模型自身还在运动就当贴图滚动正常 |
| 附魔光泽 | Java 写入独立 glint UV／模式；world UBO 每帧生成 glint matrix；两套 PT 使用 transformGlintUv | 没有发现相同的“完全未传矩阵”缺口；自定义 glint 变体与最终动态视觉仍有限制 |
| Veil 数据驱动 texturing | Veil 4.3.2 TexturingLayer 可设置滚动／旋转／缩放矩阵；VeilShaderBridge 为光栅提交 TextureMat | 世界 PT 的任意自定义程序没有通用 UV 合同。能力风险成立，但未定位到当前组合中实际使用该 layer 且进入 PT 的具体生产者，不能登记为已复现失效 |

信标与末地水晶光束的偏移直接进入本帧生成的顶点 UV；末地传送门走两套 PT 已消费的 `worldUBO.gameTime`。没有发现这几条路径存在相同的“来源 TextureMat 完全丢失”，但本轮没有执行其动态目视回归。

确定反例：主 UV `(0.25, 0.5)`，来源矩阵平移 `(0.2, 0.4)`。energySwirl 的实际适配器返回 `(0.45, 0.9)`；breezeWind 返回 `(0.25, 0.5)`，`transformsPrimaryUv=false`。来源 vertex shader 明确执行 `TextureMat * vec4(UV0, 0, 1)`，因此该差异不是视觉猜测。

breeze_wind 的 fragment shader 还在完整颜色调制后丢弃 alpha 小于 0.1 的片元。当前来源适配器返回裸覆盖描述符 10，没有登记该丢弃阶段；全局 draw colour 也走未知程序的单位调制回退。这是同一来源登记不完整的另两个字段缺口；默认白调制时不会自动产生可见颜色差。不能只加矩阵资格而遗漏 alpha 和调色，也不能将它误归类成 energy_swirl 的加法／全亮材质。

### 重合附加层

当前合成器只对 `EYE_OVERLAY` 政策匹配完整索引、位置、UV 和统一纹理；每个底层至多附着一个眼睛纹理。它不是原版“按顺序覆盖同一表面”的通用实现。普通 PT 最近命中及独立 alpha 不能单独保证原版 LEQUAL／覆盖顺序。

| 来源 | 确定的源码／接缝事实 | 结论边界 |
| --- | --- | --- |
| 蜘蛛、洞穴蜘蛛、末影人、幻翼 | 采用 EyesLayer／RenderType.eyes；幻翼的实际层对象触发 requiresEyeComposition | 属于当前修复覆盖的同类机制；没有重新执行这些对象的目视验收 |
| 旋风人眼睛 | BreezeEyesLayer 不继承 EyesLayer，breezeEyes 返回 entityTranslucentEmissive；实际政策 forceUnit=true、eyeOverlay=false | 不进入当前眼睛合成与相应持久模型保护；确认独立路径尚未覆盖，不能直接套用加法眼睛公式 |
| 监守者发光、心脏、触须与脉动斑点 | WardenEmissiveLayer 使用原模型的选定部件、vertex alpha 与 entityTranslucentEmissive；不是 EYE_OVERLAY | 确认没有有序表面层合成，存在身体与发光层竞争的风险；尚未取得具体最终画面失效或动画幅度结论 |
| 猫／狼项圈、铁傀儡裂纹、马匹花纹 | 原 renderer 对相同 parent model 再次绘制；使用 cutout／translucent，非 eyes | 同类有序覆盖缺口的直接生产者，不能把它们都改成发光，实际严重程度待验证 |
| 盔甲多材料、皮革细节与纹饰 | HumanoidArmorLayer 对同一 armor model 顺序绘制材料层和 trim；trim 还独立映射 atlas UV | 通用覆盖资格不能要求所有层 UV 一致；普通材质层与 glint 涂层分别核对，不假定附魔适配已经覆盖纹饰 |
| 末影龙眼睛 | 普通来源为 RenderType.eyes，但 EnderDragonRenderer 不是 LivingEntityRenderer | 完整普通捕获可进入眼睛合成；若启用 rigidModels 与 rigidParts，当前按 LivingEntityRenderer.layers 的保护不能覆盖该 renderer，有条件风险，不能称默认配置已复现 |

实际 WardenModel 身体 60 个四边形：生物发光层 30/30、心脏 6/6、触须 12/12、脉动层 36/36 均与身体位置和 UV 完全重合。实际 BreezeModel 的头部正常提交 12 个面，开启原版眼睛子部件后提交 24 个面，24 个都与这 12 个身体面重合。模型接缝证明来源及重合关系，不证明实际 GPU 最近命中一定选择哪一份，也不是完整 renderer 运行。

不应以偏移、加厚、强制双面、按纹理名称白名单、删除层或给每个发光层直接套 eyes 合成来处理。半透明 emissive、普通 cutout 染色覆盖、加法眼睛和 glint 有不同的颜色、alpha、UV、遮挡及顺序合同。

### 发光与运行边界

本轮没有发现已合成眼睛被两套 PT 限定为“只在主相机显示发光”的同类分支；次级路径仍采样覆盖层辐射。此结论不等于最终附近照明已实测，也不解决 [adr-6](../design/adr-6-fullbright_transport.md) 与既有手部查询排除的独立交点。

第三方候选枚举只发现内嵌 Veil 的 TexturingLayer；未发现 Create／Aeronautics 其他直接同名矩阵或上述层工厂调用。混淆、动态调用、shader 资源与未登记程序不能靠该字符串枚举排除，未将“没有候选”写成第三方全兼容通过。

### 记录验证与包身份

`spotlessApply check verifyCompliance --strict` 成功，非 GPU CTest 67/67 通过，严格合规失败 0／警告 0；Java、JNI 与 probe 测试任务复用上轮已验证输出，不冒称本轮重新执行。没有运行 GPU 测试或客户端。文档另经 rumdl 和 `git diff --check` 核对。

例行 `check` 的原生安装／归档依赖重新生成了 shader ZIP 的目录时间字段，导致本地 build JAR 变为 `0289EBEA9194886F4E3E65AEB80E761DBD657F8A123052A27454E1282945D094`。逐成员比较确认源码输入、DLL、GAME 与全部 shader 内容不变；仅两个 pack 的 ZIP 元数据及对应 runtime.index 不同，没有据此换包。Prism 的实际文件仍为开头冻结的 `65A2E094…`，即当前使用者正在验收的产物。差异、目录时间字段与完整哈希保存于 `companion-audit-package-identity.json`；旧 artifact 清单保持原记录，不能再把它的旧 build 路径视为当前构建输出的哈希。

## 结论

确定需修的是 breeze_wind 来源合同：主 UV、丢弃阶段和实际 draw 调制应一起接通，并保持其受光半透明语义。确认存在的较大缺口是通用同表面附加层表达；旋风人眼睛、监守者及普通装饰层需要按来源运算分别验证，不能以眼睛的单层加法涂层替代。

持久模型的保护边界另列为有条件风险，未知 Veil 程序列为未证明可达的能力风险。使用者当前包及实测继续保持原身份；本轮只记录调查，不擅自形成新修复候选或要求使用者中断当前验收。后续修复必须保留全亮实际贡献世界照明、查询所有权、历史、重载和 GPU 退休，分别补充生产入口／几何及最终行为证据。
