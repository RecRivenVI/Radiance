# Radiance / MCVR 审计与参考源码更新

审计基线固定为 Radiance `414d8e330a2fc6cb1e8630cc95f2302b2b97a0e8` 与 MCVR `9905c81b1999f5845bf66d13501d371c16adf561`。两者均与开始时上游main一致；当前相对基线的已提交差异为0，两个暂存区均为空。

| 范围 | 变更路径 | 差异单位 | 已覆盖 | 已解释 | 待确认 | 遗漏 |
|---|---:|---:|---:|---:|---:|---:|
| Radiance | 250 | 587 | 587 | 578 | 9 | 0 |
| MCVR | 207 | 662 | 662 | 649 | 13 | 0 |
| 总计 | 457 | 1249 | 1249 | 1227 | 22 | 0 |

191个功能条目。文件级436项全部差异已解释、21项含待确认单位；所有457项均覆盖。类别可能交叉，不可将分类计数直接相加。22个待确认单位没有冒充已解释；本轮没有游戏运行验收。

## 主要结论

- 兼容并非全部无效，但Veil链路已确认不能正常进入世界：初始化被跳过，而相机矩阵等消费者仍调用空renderer。另有异常退出时core.dll崩溃，代码点未精确定位。
- 编译与用户实际运行版本有漂移：Veil4.1.4→4.3.2、Sable2.0.3→2.0.5、Aeronautics1.3.0→1.3.2、NeoForge21.1.248→21.1.250。当前代码/日志不能证明这些组合完整兼容。
- 必须拆开版本迁移、原版渲染翻译、主动视觉调整和本地诊断。F3三轴指针涉及LINES三角拓扑支持，不是只改显卡字符串；判定箱强制emission、名称标签50%背景、WhiteAsh尺寸覆写删除另有明确条目。
- MCVR上游已有磁盘SPIR-V缓存；本地新增Shader对象复用和Vulkan管线缓存。同步世界管线预热在交接阶段有约1秒实测耗时，不能解释全部数秒停顿。
- 诊断全部移除清单单独列明，同时保留正常错误传播、线程/生命周期保护、缓存/预热和图形功能的拆分边界。本轮没有执行删除或修复。
- 工程上存在Maven发布可能仍输出内层GAME jar、nested依赖平铺残留、运行产物校验不足等风险。

## 交付入口

- [审计基线](D:/Workspaces/Artifacts/RadianceMCVRAudit/20260914/Reports/01-审计基线.md)
- [完整差异台账](D:/Workspaces/Artifacts/RadianceMCVRAudit/20260914/Reports/02-完整差异台账.md) / [CSV](D:/Workspaces/Artifacts/RadianceMCVRAudit/20260914/Reports/完整差异台账.csv)
- [逐差异块反查索引](D:/Workspaces/Artifacts/RadianceMCVRAudit/20260914/Reports/差异块索引.csv) / [逐文件覆盖索引](D:/Workspaces/Artifacts/RadianceMCVRAudit/20260914/Reports/文件覆盖索引.csv)
- [分类与覆盖统计](D:/Workspaces/Artifacts/RadianceMCVRAudit/20260914/Reports/03-分类与覆盖.md)
- [待整改清单及诊断移除边界](D:/Workspaces/Artifacts/RadianceMCVRAudit/20260914/Reports/04-待整改清单.md)
- [关键结论与优先级](D:/Workspaces/Artifacts/RadianceMCVRAudit/20260914/Reports/05-关键结论与整改优先级.md)
- [参考源码最终清单](D:/Workspaces/Artifacts/RadianceMCVRAudit/20260914/Reports/06-参考源码清单.md)
- [待确认项与缺口](D:/Workspaces/Artifacts/RadianceMCVRAudit/20260914/Reports/07-待确认与阻塞.md)

原始Git状态/patch/内容快照/运行日志/工具链证据在Evidence。首轮radiance-java.json、mcvr.json仅为增删摘录索引，不能用其中PASS/all-explained取代最终ledger/coverage。

## 参考源码结果

16/16选定条目实际存在并由根独立校验tree hash。三个MC/Loader目标均已落入 `D:\Workspaces\References\minecraft-references`：Forge47.4.23、NeoForge21.1.250的补丁开发源；Fabric0.19.5配合1.21.4映射反编译源。后者不声称包含Knot/Mixin运行时变换，前两者也不声称所有运行时CoreMod/Mixin均已物化。

3个旧目录（Minecraft1.20.1、1.21.1、Forge47.4.23）在完整ZIP备份且逐文件核验后移入Windows回收站并替换；仍对应现有编译基线或其他目标的旧版本保留。没有清空参考库，没有永久删除。NeoForge21.1.250、Flywheel1.0.6的发布commit仍无可靠证明，但精确发布artifact/source/hash齐全；其余适用commit见清单。

## 实际分工与验证

根负责基线、工程/早期显示审计、语义复核、漏诊断补齐、F3调用链、覆盖统计、发布与状态验证。Luna Max用于Java审计、MCVR审计和有界版本研究；Sol High分别实现第三方参考和MC/Loader生成脚本。峰值并发4（含根），没有启用独立Extra High审查或创建额外用户任务。

语义复核纠正了首轮模板化解释、SPIR-V缓存来源、NRD官方来源、single-JAR/sidecar表述和诊断/业务混分。验证层级：静态差异/真实旧日志/源码生成与哈希分别记录，没有以生成编译成功代替游戏或渲染验收。

## 最终保全检查

两个仓库的文件内容、HEAD、当前分支、暂存索引、refs、Git状态和子模块指针均与初始快照一致；15个子模块内部状态及83个补充运行资源哈希也未变化。未发现外部并发修改。最终记录见Evidence/state-check-*.json、final-supplemental-state.json。
