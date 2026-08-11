# 眼睛附加层与电弧纹理变换修复

2026-10-17，接续[首次手工反馈](incident-emissive_layer_acceptance.md)，按使用者授权修复蜘蛛／末影人眼睛和充能苦力怕电弧动画。未启动游戏、性能实验、故障注入、提交或推送；修复候选仍需对应的目视复测。

## 问题

旧验收 JAR `64058C04…`、core `F45C284C…` 中，使用者报告蜘蛛／末影人眼睛颜色与发光缺失，充能苦力怕电弧显示且发光但纹理静止。原自动夹具没有覆盖双面身体与单面眼睛的真实组合，也没有验证 energy_swirl 的 TextureMat 传播，不能据其成功排除反馈。

## 方法

沿来源工厂、模型顶点、批次状态、JNI、原生合成和两套 PT 消费者定位缺口。以旧资格条件在 body=0、eyes=4 时必不配对的反例，和真实蜘蛛／末影人模型生成的相同身体／眼睛网格验证机制；GPU 查询检查直接成像的面规则及双面传光辐射。纹理变换通过实际 EntityRenderLayer 入口测试原版、canonical、compact 三种布局，覆盖跨帧、单次应用及非法输入。

## 发现

### 眼睛

旧资格要求身体与附加层面位完全相等。当前实际来源的身体为双面、眼睛剔除背面；位置、索引和 UV 相同仍不能合成，留下重合独立层的遮挡竞争。修复允许底层覆盖附加层全部可见面的组合，并将眼睛的有效面规则随有界纹理 ID 保存；直接成像独立过滤眼睛，传光查询仍取得其有色辐射。身体本身的面规则不变，不通过加厚、位置偏移或全局改双面掩盖问题。

除面规则外的材料／所有权、几何、索引、UV 和纹理一致性保护保留；新的不同面规则合成只扩展到不透明底层，不扩展到分数覆盖／物理透射底层。镜像和硬件正面补偿只处理一次。Advanced 同帧缓存写入的是当前查询过滤后的纹理 ID，不误把元数据当作描述符索引。

启用持久模型实验时，真实 EyesLayer 要求身体与层共同捕获，保留完整路径，不让预构建部件绕过合成；其他模型保留原有快路径。当前规则不保证任意自定义附加层、透明身体或资源包变体的普遍等价。

### 电弧

原版按 tickCount 与插值更新 OffsetTexturingStateShard，vertex shader 使用 TextureMat 变换 UV0。旧捕获只保存／恢复矩阵，未把绘制时矩阵传给普通 PT；已确认主 UV 传播缺口。

修复按实际 energy_swirl shader 身份保存不可变二维变换，在普通实体和世界事件交接 native 前应用一次；不另造动画时钟、不改附魔 UV、覆盖率、颜色或发光。这个实际主 UV 变换能力暂不使用刚性快路径，保持完整捕获回退，不冻结 UV。非法变换在修改任何顶点前拒绝；矩阵仍按原顺序恢复，不查询无 context 的 GL。

### 实际自动验证

- 首轮定向 EmissiveLayerSourceTest 4/4 通过；随后补齐实验快路径保护，完整测试重新执行，不把首轮当最终源码验证。
- `spotlessApply` 通过。`check verifyRelease :native_renderer:gpuTest verifyCompliance --strict` 通过；GAME 296、真实 JNI 4、probe 28 全通过，bootstrap 11 通过／2 跳过，非 GPU CTest 67/67、GPU 16/16，无设备丢失。
- CPU 回归覆盖真实 0/4 面位组合、不可覆盖的面、UV／位置／所有权不一致、重复附加层、描述符边界和列删除对齐。GPU 回归覆盖查询正反面、硬件翻转、传光保留及有色辐射；该夹具不冒充完整世界照明或最终眼睛画面。
- 两套 pack 的 232 个入口编译、232 份 SPIR-V 验证通过；JNI、必需发行条目和探针排除检查通过。原有 Unsafe、SDK/CMake 与弃用提示保留，严格合规零失败／零警告不指所有编译日志无警告。
- 日志与机器汇总在 `D:/Workspaces/Scratches/RadianceSemanticsAudit/eyes-energy-*`。没有实际客户端、全配置视觉、长期稳定性或性能结论。

## 结论

新候选 JAR 为 `65A2E09463350081597CC814F56B5781617D5A1B71225ED234E53EB53BC8E422`；core 为 `328E5F7EEA9C7969ECE37F8BEC2541658C050EB7BDA6AD4B94CC5AA161D56E0A`；GAME 为 `F1C7BBE6D57F610A0343D97ADE85CE639075CFDCD545BCB603E9D22FFCB98E76`。身份清单为 `D:/Workspaces/Scratches/RadianceSemanticsAudit/eyes-energy-artifact.json`，源码以 checkpoint-13 全量快照和 `eyes-energy-source-delta.zip`／`eyes-energy-source-manifest.json` 共同固定，不覆盖旧证据。

工程缺口和针对性回归已处理，是否完整解决本次实际眼睛缺失仍由使用者复测确认。全亮层保留实际路径辐射，不改为只显示发光。Prism 只在确认游戏退出并持有实例锁后投放，不启动；实际提取和加载新 DLL 要等使用者下一次运行后核对，不能由 JAR 内嵌校验代填。
