# B：裁决入口

目前没有必须重新裁决的已识别设计项。MC-04.2 已有用户原话“采用默认建议”，本次核对了前文选项；不是仅以Agent转达当作决定。

| ID | 当前效果 | 未定问题 | 默认建议 | 选择影响 | 我的裁决 | 状态 | 来源 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| MC-04.2 | 已定位额外入口：DebugRenderer.render还调用GameTestDebugRenderer；LevelRenderer.renderDebug可输出sectionPath、sectionVisibility及capturedFrustum线/面，均可经共享debug/world提交。它们是否实际启用依赖现场状态，不能声称玩家当前都能看到。 native前缀给这些提交统一emission。 | GameTest调试标记、sectionPath/sectionVisibility和capturedFrustum等额外调试输出，是否也主动发光？它们不属于已经具名的F3+B/F3+G。 | 只保证F3+G/F3+B发光；上述额外输出按原有颜色/光照语义保留。普通黑色方块选框独立核验，不借此改变。 | ①限已具名：范围最清楚，额外debug线不获得主动发光；②扩展：请给效果名单，列出输出者后仅该名单发光，可能增加局部间接照明。不是关闭额外debug线。 | 采用默认建议：保留 F3+B 判定箱与 F3+G 区块线的 PBR 发光；GameTest 标记、sectionPath/sectionVisibility、capturedFrustum 等额外调试输出保留其颜色/光照语义，不额外赋予主动发光。 不是关闭或删除额外调试图形，不改变普通方块选框等独立目标。仅记录裁决，不授权业务整改。 | 已裁决，未实施 | Evidence/Resume/decision-direct-source.json（实际用户原话已核） |

你可按“ID：改为……；希望看到……”修改已有目标。若保持已有决定，无需逐项回复，也不视为已授权实施。

普通方块选框、实体轮廓、名称牌、Text Display与不同特殊效果仍独立列项；17个旧差异待确认属于技术证据缺口，未强行转成设计问题。
