# Radiance / MCVR 决策材料 r2

状态：本次报告恢复与修订完成，等待用户裁决或另行实施授权。

优先看 [B：裁决入口](Reports/B-真正未定的设计取舍.md)：目前无必须重复裁决项，MC-04.2已采用默认。已定目标及历史覆盖关系见 [A](Reports/A-已有明确用户决定.md)；具体实现事项见 [C](Reports/C-技术核验与修复事项.md)。

**工作树已晚于原r2快照。** 请同时看 [恢复复核与源码漂移](Reports/恢复复核与源码漂移.md)：旧缺口分类与当前核验分列，不把后续半成品说成完成，也不重复建议已做的修改。

完整材料：[行为表](Reports/行为表.md)、[JSON](Reports/behavior-r2.json)、[可编辑CSV](Reports/behavior-r2.csv)、[18兼容工作包](Reports/兼容工作包汇总.md)、[修订说明](Reports/修订说明.md)。旧新ID与反查见 Reports/id-map-final.json、id-map-final.csv、全差异反查索引.csv、coverage-r2.json。

原范围457路径/1249单位全部有入口：1232复用或解释，17待确认，0映射遗漏；这是历史范围，不包括恢复前新增源码。52 CG归18包，原分类21确认缺口/14已有承接待验/9历史目标/8风险；当前状态不能沿用旧数量。

剩余未知：17单位、特殊材质/遮挡/相机语义、完整兼容调用链及后续新增代码完整性、NeoForge21.1.250/Flywheel1.0.6产物与源码commit对应。本轮未构建、启动、运行或观察画面。

保全记录：Evidence/Resume/start.json、historical-drift.json、preservation.json；整合验证：Evidence/Resume/integration-validation.json。原Evidence/final-validation.json仅历史验证，不作为本次证据。

本次只写本r2目录，未改业务仓库、暂存区、分支或历史；未删除、构建、启动游戏或GUI。已停止两名恢复前仍运行的实施代理，没有新建代理或用户任务。后续实施必须另行授权。

本次结束保全结果：通过。恢复开始至结束两仓库的Git可见内容、HEAD、分支、暂存条目和status不变，父报告及原Evidence不变；恢复前的113个变动/新增路径已单列，不能将原r2快照保全误写为一直没有后续变化。
