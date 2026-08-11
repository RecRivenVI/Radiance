# Radiance / MCVR 行为裁决与源码兼容缺口

本轮是取消整改后的**只读决策准备**，没有恢复五阶段实施授权。未修改业务代码/配置/暂存区/分支，未提交、推送、重置或删除，未构建、启动游戏、操作GUI或创建Prism实例。

## 先从这里裁决

- [行为裁决主表](D:/Workspaces/Artifacts/RadianceMCVRDecisionReview/20260915-030125-bbc60ca3/Reports/01-Minecraft与行为裁决表.md)：37项Minecraft行为、7项Loader扩展以及8项工程/共享入口；每项区分效果与实现。
- [可编辑完整行为CSV](D:/Workspaces/Artifacts/RadianceMCVRDecisionReview/20260915-030125-bbc60ca3/Reports/行为裁决表.csv)
- [60项裁决回复模板](D:/Workspaces/Artifacts/RadianceMCVRDecisionReview/20260915-030125-bbc60ca3/Reports/裁决回复模板.csv)：另含7个模组入口和共享基础入口，可逐项填“效果裁决/实现裁决”。
- [源码兼容缺口主表](D:/Workspaces/Artifacts/RadianceMCVRDecisionReview/20260915-030125-bbc60ca3/Reports/03-源码兼容缺口.md) / [CSV](D:/Workspaces/Artifacts/RadianceMCVRDecisionReview/20260915-030125-bbc60ca3/Reports/源码兼容缺口表.csv)：52项，不代表52个已复现崩溃。
- [全类别与191条语义子项](D:/Workspaces/Artifacts/RadianceMCVRDecisionReview/20260915-030125-bbc60ca3/Reports/02-全类别与归属.md) / [逐差异单位索引](D:/Workspaces/Artifacts/RadianceMCVRDecisionReview/20260915-030125-bbc60ca3/Reports/全差异归属索引.csv)
- [诊断与临时绕过清理边界](D:/Workspaces/Artifacts/RadianceMCVRDecisionReview/20260915-030125-bbc60ca3/Reports/04-诊断与绕过边界.md)
- [两组验收与后续依赖顺序](D:/Workspaces/Artifacts/RadianceMCVRDecisionReview/20260915-030125-bbc60ca3/Reports/05-后续验收与依赖顺序.md)
- [Flywheel历史目标核对](D:/Workspaces/Artifacts/RadianceMCVRDecisionReview/20260915-030125-bbc60ca3/Reports/06-Flywheel历史目标核对.md)

回复示例：

```text
MC-05：保留PBR发光效果；允许修正遮挡和资源生命周期。
MC-09：保留约50%背景强度；实现按名称牌限定作用域处理。
MC-07：轮廓颜色选……；实现建议……。
MOD-02：保留真正实例化目标；方案参考CG-L03/05/09，补充……。
```

空白或“默认建议”不构成执行授权。未获得后续裁决与实施授权前不做业务整改。

## 已明确的用户决定

1. **MC-05 判定箱PBR发光、MC-09名称牌约50%背景强度默认保留**。此前相反建议已被明确覆盖；偏离原版不是删除理由。普通区块/其他调试线是否也发光单独裁决。
2. MC-20 White Ash支持先对齐1.21.1原版行为；旧上游1/8覆写与当前无此覆写已列明，但本轮没有改动，也没有声称画面已验收。
3. 本地vkWaitForFences诊断支持未来清理，正常错误传播/同步/生命周期与上游诊断保留；本轮不清理。
4. 后续正式验收只有基础组和完整组；中间子集只作必要归因，不设强制四级关卡。

用户决定证据：已读取源任务“全面审计 Radiance 分叉差异”中的实际用户消息，保存于Evidence/user-decisions.json。本轮转发要求明确重复了这些决定。旧建议/历史执行流程均不恢复为当前授权。

## 关键重新归属与源码发现

- 普通物品/弓姿态是MC-13；NeoForge接管回调是EXT-01；Plunger额外坐标是CG-V02。手部回调并非全丢，但当前getXRot与1.21.1原版partialTick插值输入有明确差异（CG-L20），属于共享输入契约问题。
- ModelData/额外section几何、FluidSpriteCache/tint和FogRenderer中的Loader回调入口实际保留（CG-R01..03）；不能因renderLevel被替换就全部判缺失。仍需查更新、层、坐标及native消费闭包。
- F3方向标与信息、区块统计、判定箱、选中框、实体发光轮廓、反射身体均独立列项；统一白色outline与团队颜色的取舍未假定已有用户决定。
- Veil的创建者停用而读取者可达是明确冲突。所提方案区分CPU状态、真实Vulkan资源和消费者路由；不以假VeilRenderer或全面关主要功能完成兼容。
- 当前Flywheel没有真实Radiance Backend/Engine；普通BER/图集回退不是实例化。已找到2026-08-21用户的真正backend/共享mesh-BLAS/TLAS实例目标；旧性能百分比与旧分族流程没有继承为本轮门槛。
- Ponder/Catnip设置/UI、Create事件、Flywheel实例布局/lighting/OIT、Sable空间、Simulated图集/便签、Aeronautics离屏效果等按固定源码链分别列项。逻辑ID如果有真实Vulkan对象与生命周期不是假ID；无资源而返回成功才是问题。

## 当前基线、覆盖与不确定性

Radiance：`1.21.1-neoforge`，HEAD `414d8e330a2fc6cb1e8630cc95f2302b2b97a0e8`。MCVR：`develop`，HEAD `9905c81b1999f5845bf66d13501d371c16adf561`。固定上游比较基线仍为这两个commit，没有追随远程更新。

本轮已逐文件hash和Git状态对比20260914审计、20260915取消任务快照：业务内容/HEAD/分支/暂存区/工作区相同。Radiance相对旧快照存在Codex应用自动管理的refs/codex引用差异，已原样记录；没有清理或改写这些引用。本轮开始/结束捕获也一致。

当前核验后可复用的范围为457路径、1249单位；全部已归类，遗漏0。已解释1232（包含内容不变可复用的旧语义），待确认17。比旧1227增加的5个单位只是明确用户选择解决了名称牌/判定箱/WhiteAsh的行为意图，并非新增运行通过。剩余17涉及SDK升级必要性、旧post配置及early provider选择边界，全部有归属与旧证据链接，不计已解释。

52条源码记录含已承接入口、确认契约缺口、静态风险及运行验证空缺。202条引用已由根检查实际路径与行号范围，关键手部、ModelData/流体/fog、F3、名称牌/判定箱、逻辑ID和Flywheel目标另作内容复核；路径通过本身不是语义证明。没有宣称静态审查后绝无崩溃。

## 实际分工与暂停

最多根+两个Luna Max子任务，没有子任务派生。复用audit_mcvr负责Veil/Sable/Aeronautics/Simulated；另一个Explorer负责NeoForge/Create/Flywheel/Ponder。根负责行为裁决、历史用户决定、基线/覆盖、正向源码承接与反例、证据/冲突复核和整合。没有新增用户任务，没有启用额外实施Worker或独立审查。

本轮报告及脚本均在此唯一Artifacts目录。原审计、取消任务恢复材料和参考源码保持原样。现在暂停，等待按ID裁决；不开始实施。
