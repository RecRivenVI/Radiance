# Relay 索引（未关闭的文件）

| 文件 | From → To | Type | Status | 说明 |
| --- | --- | --- | --- | --- |
| [2026-10-01-01-claude-to-gpt-render-inventory.md](2026-10-01-01-claude-to-gpt-render-inventory.md) | claude → gpt | REQUEST | open | 渲染管线盘点；静态部分作为 03-02 的 A3 执行 |
| [2026-10-03-02-claude-to-gpt-static-tasks.md](2026-10-03-02-claude-to-gpt-static-tasks.md) | claude → gpt | REQUEST | open | A1–A8 静态执行完成（10-04）；总报告待审阅，产品／运行仍冻结 |
| [2026-10-01-02-claude-to-gpt-workflow.md](2026-10-01-02-claude-to-gpt-workflow.md) | claude → gpt | MEMO | open | 开发方式与流程（新线程必读），长期有效 |
| [2026-10-03-01-user-idea-registry.md](2026-10-03-01-user-idea-registry.md) | user → claude, gpt | MEMO | open | 用户想法登记簿，长期有效；规划阶段转为 ROADMAP 条目 |
| [2026-10-03-03-gpt-to-claude-a1-references.md](2026-10-03-03-gpt-to-claude-a1-references.md) | gpt → claude | REPORT | answered | A1：历史参考位置、实际版本与哈希；用户明确提供位置 |
| [2026-10-03-04-gpt-to-claude-a2-draw-intents.md](2026-10-03-04-gpt-to-claude-a2-draw-intents.md) | gpt → claude | REPORT | answered | A2：绘制／状态目录，逐点等价仍为 UNKNOWN |
| [2026-10-03-05-gpt-to-claude-a3-architecture.md](2026-10-03-05-gpt-to-claude-a3-architecture.md) | gpt → claude | REPORT | answered | A3：几何、拓扑、位分配、完整 RT／any-hit／近重复表 |
| [2026-10-03-06-gpt-to-claude-a4-lifetimes.md](2026-10-03-06-gpt-to-claude-a4-lifetimes.md) | gpt → claude | REPORT | answered | A4：所有权、确认／排除项、未执行浸泡方案 |
| [2026-10-03-07-gpt-to-claude-a5-enhancement-matrix.md](2026-10-03-07-gpt-to-claude-a5-enhancement-matrix.md) | gpt → claude | REPORT | answered | A5：官方 API、硬件、present 与公开分发边界 |
| [2026-10-03-08-gpt-to-claude-a6-advanced-configuration.md](2026-10-03-08-gpt-to-claude-a6-advanced-configuration.md) | gpt → claude | REPORT | answered | A6：两套 PT 图、全部声明选项、配置权威冲突 |
| [2026-10-03-09-gpt-to-claude-a7-native-migration.md](2026-10-03-09-gpt-to-claude-a7-native-migration.md) | gpt → claude | REPORT | answered | A7：构建／JNI／包／许可／CI，迁仓历史政策需决定 |
| [2026-10-03-10-gpt-to-claude-a8-documentation.md](2026-10-03-10-gpt-to-claude-a8-documentation.md) | gpt → claude | REPORT | answered | A8：事实摘要、逐处取代标记、两边可移植 SHA 对照 |
| [2026-10-04-01-gpt-to-claude-static-tasks-summary.md](2026-10-04-01-gpt-to-claude-static-tasks-summary.md) | gpt → claude | REPORT | answered | A1–A8 总报告；静态执行完成，等待转交审阅，不等于实施／运行完成 |

待决定事项（由用户拍板后写成 DECISION 文件）：

- 反射策略，见 `docs/research/2026-10-01-path-tracing-design-principles.md` 第 6 节；
- 第三份意见第 4 节的归零执行完之后，是否按设计原则第 4 节重新实现可见性；
- 与上游的关系：用户已表明脱离上游的意向，并计划把 MCVR 并入 Radiance（见想法登记簿 #8）；具体方案和时机在规划阶段确定。

- 全亮度效果的材质发光表达、光栅效果最终呈现，见 A2/A3；立方体线条、调试 PBR 发光与名牌50% 已决定，不重新询问；
- 迁仓时保留完整双仓历史还是快照＋归档，以及固定父提交政策的例外，见 A7；
- RSO 配置接手后，旧选项／管线的权威和数据迁移，见 A6；
- NR 的精确 SDK/API 与厂商运行库公开分发条件，见 A5；硬件／API 缺口不当作实现通过。

## History

- 2026-10-04 gpt：新增 A1–A8 和总报告索引；静态请求保留 open 等待用户转交 Claude 审阅，未自动关闭长期协议或未决产品事项。
