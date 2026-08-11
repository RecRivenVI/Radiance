# 合并验收中的眼睛层与电弧动画问题

2026-10-17，使用者验收最终 B/C/D 候选时报告蜘蛛／末影人眼睛颜色和发光缺失；充能苦力怕电弧显示且发光，但动态贴图静止。本记录只读调查与维护记录，不修改产品、重建、投放或启动游戏，不将自动夹具成功覆盖真实失败反馈。

## 问题

当前验收 JAR SHA-256 为 `64058C04E8FFD8D5817FB3D763D4F32D8CEE82D25A17C9F12E1BED00091EC838`，内嵌 core 为 `F45C284CC8D341E535D1E5257B4422B29C6E7C1F69AB704FCAD0E4A886D631EB`。投放回执为 `D:/Workspaces/Scratches/RadianceSemanticsAudit/bcd-complete-prism-deployment-receipt.json`。反馈属于该候选的手工失败，不能沿用阶段 C 的独立眼睛合成测试宣布目视通过。

15:15:51 只读核对运行中 PID 60644：实例 JAR 及实际加载的 `.radiance/runtime/833fac62fd0f0e34729d4dc5ca79edc3cf5c7b9e45cbbae938df46567ccedd65/core.dll` 哈希均与上述候选相同。收据为 `D:/Workspaces/Scratches/RadianceSemanticsAudit/emissive-layer-acceptance-live-20261017.json`，没有启动、关闭或修改游戏。

使用者同时接受 [adr-6](../design/adr-6-fullbright_transport.md)：显示全亮的几何必须物理发光并影响其他世界几何。此决定与上述两项缺陷分别记录。

## 方法

读取当前 Java 发光政策、逐 draw 快照、原生眼睛配对与独立层保留、两套 PT 的发光叠层采样；对照 Minecraft 1.21.1 实际资源 JAR 的 eyes／energy_swirl shader、眼睛纹理，以及原始 EnergySwirlLayer 和 OffsetTexturingStateShard 入口。Prism 日志只读且运行中会变化，不作为冻结快照，不修改实例或中止使用者验收。

## 发现

### 蜘蛛与末影人眼睛

- 使用者确认颜色和发光均缺失；实际对象已明确，不再按泛指的所有附加层归因。
- 当前已登记 EYES 来源，原生有强制发光、可合成覆盖层和不合资格时保留独立几何两条分支。不能从存在这些代码直接推断本次层已被正确命中。
- eyeAttachments 要求来源面位相同并核对索引、位置、UV；前一功能预检已记录 eyes 面位为 4、body-no-cull 为 0，不合资格时保持独立层。重合的独立层是否被身体遮挡是优先验证的候选，尚无本次失败姿态的命中数据，不定为根因，不删除来源面保护。
- 当前默认蜘蛛眼睛纹理有 20 个非黑像素，末影人有 6 个，非黑像素 alpha 均为 255；因此默认资源中“有颜色但 alpha 为零”不能解释该反馈。使用者当前资源包或实际绑定仍须另外核对。
- 既有自动夹具证明字段和合成接缝，不包含失败对象的最终遮挡画面；不得把颜色和发光缺失记为已修复。

### 充能苦力怕电弧

- 使用者确认电弧可见且发光，失败范围是纹理滚动，不能把整层记为丢失。
- 原始 EnergySwirlLayer 每次用 tickCount 与插值生成 U/V 偏移，energySwirl 的 OffsetTexturingStateShard 设置 TextureMat，实际 vertex shader 使用该矩阵变换 UV0。
- 当前普通 PT 捕获的 DrawStateSnapshot 只保存面、线宽及 RGBA；MaterialFaces 保存并恢复纹理矩阵，但未将执行期间的矩阵放进快照。普通 PBR setUv 直接保存原 U/V。GUI 光栅和 Veil 原始 shader 的 TextureMat 上传属于其他路径，不能据此证明普通实体 PT 已保留电弧滚动。
- 这是已定位的来源变换传播缺口，和眼睛层的合成／命中问题不同；本轮没有改产品或执行修复后的验证，不宣称已闭合所有动态纹理入口。

### 线条照明的来源边界

核对当前 NeoForge 21.1.259 实际 RenderType 类及默认资源：lines／lineStrip 使用 rendertype_lines，只计算顶点色、ColorModulator 和雾；debugLineStrip 使用 position_color。均不采样 lightmap 或计算方向光，Normal 用于线宽展开。对应第三方 15 个相关类与留存清单哈希一致，已核对入口使用上述标准程序。

方块选框、F3+B、F3+G、钓鱼线、结构框、Sable 调试线、Simulated 拉杆／绳索外框、Create 真实线、NeoForge bounds、Veil 调试线属于不受光颜色绘制；黑线不会因为不受光变白。Radiance 派生模型边没有一一对应的原版棱柱，原版实体轮廓为后处理。结论限默认资源及上述来源，不覆盖任意自定义程序或资源包改写。当前普通受光线与主动发光调试线是项目选择，并非来源原有两类照明合同。

## 结论

当前候选的蜘蛛／末影人眼睛目视失败，根因仍需命中和提交分支证据；充能苦力怕的颜色／发光可见反馈保留，纹理动画失败，已发现普通 PT 路径没有传递来源 TextureMat 的缺口。两项独立跟进，不在使用者验收期间换包，不回填未执行的通过结果。新的全亮物理发光决定和既有查询例外的交点需要复核；性能、GPU 根因及公开发布许可没有由本反馈关闭。
