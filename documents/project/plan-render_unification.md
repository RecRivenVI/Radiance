# 渲染接管与翻译统一计划

## 目标

将分散的接管与翻译收敛为来源无关的合同，并用影子比较、多入口等价和不变式验证查找隐藏差异。阶段 A 已获使用者验收；2026-10-17 执行书 30 授权 B、C、D 连续实施，阶段间不停顿，目视项目累积后统一交付。保持阶段 A 的字体修复与线条不透明策略为基线。

## 范围

- 阶段 A：族 1、2 支撑显式线请求的最小部分及族 3。覆盖原版实体与 BER、Sable、Simulated 拉杆与绳索、Veil 事件线、Create highlight，以及审计确认的 Create 世界事件线、NeoForge bounds 接收缺口。
- 阶段 B：族 1、2 全部来源，族 9 ABI，族 11 按需登记；工程与定向自动回归完成。
- 阶段 C：族 4 材质、族 5 颜色与 alpha、族 6 发光；工程与自动门槛完成。
- 阶段 D：族 8 拓扑与偏移、族 7 时序与尺寸、族 10 shader 共享及总回查；工程与限定自动验证完成，已获部分合并目视通过，准确候选与范围见结果节。
- 不处理模型框等面图元，不重启 Ponder PT，不改变 adr-1，不做性能实验，不提交或推送，不操作外部实例。

## 步骤

1. **完成**：读取执行书、仓库约束和[整体审计](../research/audit-render_semantics_consolidation.md)，逐字创建并读回阶段 A Goal。
2. **完成**：深入读取阶段 A 的生产、捕获、提交及消费现场，固定改动前身份，分类现有差异，写出[迁移前研究](../research/investigation-line_unification.md)。
3. **完成**：确定不依赖游戏类的[最小工程合同](../design/mechanism-line_capture.md)，显式登记来源与[权限理由](../design/adr-2-world_raster_permissions.md)；世界物理厚度政策不变。
4. **完成**：各来源接入统一描述符，删除原生批次宽度/重复 owner 参数与名称解释，补充来源布局历史和 Sable 包装器保护；原有代际、资源退休与 adr-1 保留。
5. **完成**：单元、真实 JNI、原生、GPU、配套构建及发行检查通过。进程 18164 完成有界世界、重载、保存退出与重进，非法输入为零；首次探针启动失败与修正分别留存，没有设备丢失或自动重复崩溃。
6. **完成**：完整差异固定到 `D:/Workspaces/Scratches/RadianceUnification/checkpoint-1-lines.patch`；同目录 `checkpoint-1-lines-manifest.json`、`checkpoint-1-lines-source.zip` 固定全部必要源码、子模块和产物身份。补丁包含既有工作树，不只包含阶段 A；不改暂存区。
7. **完成**：阶段 A 的线条获使用者总体良好反馈，[字体生命周期修复](../research/experiment-font_texture_lifecycle.md)候选 F3+T 已获手工通过确认，原候选失败保留。按 [adr-4](../design/adr-4-world_line_opacity.md)将世界线默认改为不透明并保留透明实现；自动验证通过，已投放 Prism，未启动游戏，新默认等待目视核对。adr-3 决定仍待明确，保持现有 End Sea 行为；按后续连续授权不阻断独立的 B、C、D。
8. **完成**：连续执行阶段 B、C、D，各族按深入评估、定合同、先锁回归、逐来源迁移、自动验证与检查点的顺序实施。外观取舍没有明确缺陷证据时保持现状并登记提议；某族阻断时继续不依赖它的工作。不投放 Prism、不提交、不推送、不做性能实验。
9. **完成**：族 1 的世界实例空间解释、三种坐标类别和 3×4 打包收敛到[空间合同](../design/mechanism-scene_space.md)。实体/刚性、Flywheel、区段及静态快照共用决定者，光栅投影保留为目标适配。576 个旧/新坐标比较一致，normal、镜像与大坐标不变式通过；GAME 定向测试通过，非 GPU CTest 67/67。首次直接 CTest 因缺少构建所需 R: 映射而未执行测试，按构建规程映射后通过，未改断言。检查点为 `D:/Workspaces/Scratches/RadianceUnification/checkpoint-2-space.patch`，运行和目视总验收在阶段末统一执行。
10. **完成**：族 2 的[目标决定表](../design/mechanism-draw_targets.md)从客户端适配器提取为无游戏依赖的合同，全部 raw raster 消费者与预览判断迁移，未扩大 WORLD_RASTER 权限。scope×FBO 21 组及预览 14 组旧/新规则一致，真实嵌套、异常、取消和回放顺序回归通过。族 11 本次没有新持久消费者，不重写重载协调器。检查点为 `D:/Workspaces/Scratches/RadianceUnification/checkpoint-3-targets.patch`；继续族 9。
11. **完成**：族 9 的[共同整数表与生成 Java](../design/mechanism-render_abi.md)已接入来源布局、最终位域、坐标/查询/实例 ID 及 shader 解码，原有协议值不变。Java 定向 11、真实 JNI 3、探针反射 1、生成器 4、非 GPU CTest 67/67、GPU CTest 16/16 通过；实际 Java 的 6 组 canonical/compact 记录经 CPU/GPU 转换对照通过。首次 include 缺失、三项旧字符串护栏、独立 GPU 命令借用 Gradle 临时盘导致中途路径消失分别保留；修正护栏并独立持有 R: 映射后通过，无设备丢失。B2 迁移遗漏的探针反射类名已纠正并以实际反射回归覆盖。检查点为 `D:/Workspaces/Scratches/RadianceUnification/checkpoint-4-abi.patch`。阶段 B 的新候选世界/压力运行统一留在最终配套构建后执行，不冒充已经运行。
12. **完成**：族 4 的[材质政策与版本适配](../design/mechanism-surface_materials.md)已收敛；已知程序用 shader shard 对象身份与实际 blend 登记，普通/弹簧政策不依赖游戏类。修正 Flywheel 两种裁切阈值，36 项真实工厂旧值不变；迁移反例和未知程序拒收的纠正顺序见[验证研究](../research/experiment-surface_material_validation.md)。Java 20、真实 JNI 3、非 GPU 67/67、GPU 16/16 与实际 Java 输入 CPU/GPU 对照通过。未知自定义程序保持原策略，严格拒收列为 [adr-5 提议](../design/adr-5-unregistered_surface_programs.md)。检查点为 `D:/Workspaces/Scratches/RadianceUnification/checkpoint-5-materials.patch`。
13. **完成**：族 5 的[批次、颜色与丢弃阶段](../design/mechanism-surface_colour.md)已贯通普通/事件/刚性及两套 PT。定向 GAME 287、JNI 4、probe 28、非 GPU 67/67、GPU 16/16、实际 Java 12 组 CPU/GPU 输入对照、232 个 pack 入口编译与 SPIR-V 检查通过。隔离运行暴露并定位旧 FontSet 缓存，修复后进程 101396 完成重载、保存、重进与正常停止，字体/线条违规为零；失败和边界见[验证研究](../research/experiment-surface_colour_validation.md)。检查点为 `D:/Workspaces/Scratches/RadianceUnification/checkpoint-6-colours.patch`；随后进入族 6，不把本次开发类运行冒充最终发行包或视觉通过。
14. **完成**：族 6 的[发光政策](../design/mechanism-surface_emission.md)贯通普通、事件、刚性与两套 PT，原生名称判断删除，眼睛调色独立保存。GAME 289、JNI 4、probe 28、非 GPU 67/67、GPU 16/16 与 pack 232 编译/验证通过；首次三项旧字符串护栏失败与修正见[验证研究](../research/experiment-surface_emission_validation.md)。全亮物理贡献待 [adr-6](../design/adr-6-fullbright_transport.md)，未知程序待 adr-5；均保持现有取舍。检查点 `D:/Workspaces/Scratches/RadianceUnification/checkpoint-7-emission.patch`，随后直接进入阶段 D。
15. **完成**：族 8 的[最终位置/历史合同](../design/mechanism-final_geometry.md)收敛 CPU、直转准入、GPU 与八个历史读取；保留既有三索引/拆分位保护，没有将已存在的能力写成新修缺陷。定向原生 7/7（GPU 4）与 pack 232 编译通过，见[验证研究](../research/experiment-final_geometry_validation.md)。检查点 `D:/Workspaces/Scratches/RadianceUnification/checkpoint-8-topology.patch`；继续族 7。
16. **完成**：族 7 的[视图帧合同](../design/mechanism-view_frames.md)引用既有 UBO，SDK 映射与阈值保留；修正[FSR 的 slot 计时归属](../research/investigation-fsr_frame_clock.md)。300 组旧/新相机判断、三 slot 48/16ms 反例、物理尺寸、双视图/重载回归及非 GPU 67/67 通过。完整构建首败、修正与实际边界见[验证研究](../research/experiment-view_frame_validation.md)；最终客户端与 W-02 仍待。检查点 `D:/Workspaces/Scratches/RadianceUnification/checkpoint-9-views.patch`；继续族 10。
17. **完成**：族 10 的共同解码、八份历史读取与 celestial 数学已提取；41 组同名异体逐项保留理由，不合并主追踪。总回查修正已知裁切程序的硬件 opaque 资格、可选线 blend、拒收容量与 FSR 失败输出。完整检查、严格合规及最终世界流程见[合并研究](../research/experiment-render_unification_bcd.md)；检查点 10-surfaces 已保存。
18. **完成**：第一轮产品代码的 RR（PID 73176）与 NRD/FSR（PID 65996）分别完成 resize、F3+T、保存重进和正常退出。三边界表面检查、字体与线条违规为零；239 份有界原生输入审计无报告项。检查点 11-total 保留此时的累计状态；随后两个遗漏的表面纯函数由步骤 19 补齐，不能把第一轮结果当最终源码的运行。没有性能、Prism 或 Git 写入。
19. **完成**：最终覆盖回查补齐法线贴图／基底与 UV→基面两个纯函数的十份定义，原样共享，GPU 64 组影子输入通过。完整门槛、GPU 16/16、pack 232 及实际 Java 12 组 CPU/GPU 重测成功；新 DLL F45C284C 的 RR（68932）、NRD/FSR（2636）流程完成且违规为零，见[最终补齐验证](../research/experiment-surface_geometry_completion.md)。检查点 12-surface_geometry 与最终 13-total 分别保留补齐和收尾，之前记录不覆盖。

## 结果

阶段 A 已有使用者定向验收：线条总体良好，F3+T 字体生命周期修复通过。默认不透明政策已实施并保留显式 `-Dradiance.lines.sourceAlpha=true` 透明入口；此前投放包 `5E81F646…` 与 B/C/D 最终候选分开，不继承旧包的整体视觉结论。

B/C/D 各族的调查、合同和自动结果见步骤 9～17；实际失败、修正与重测分别保留在对应研究记录。最终合并[目视清单](../development/testing-render_unification.md)按字体／批次、材质／眼睛、空间／目标／历史、SDK／屏幕效果排序。未执行的视觉、FG 生成帧、长期稳定性与性能保持未验证。

随后按使用者授权投放同一最终候选，安装回执、实际身份及正在进行的手工反馈见[当前状态](phase-1-before_vanilla_closeout.md#当前投放与新反馈)。蜘蛛／末影人眼睛与电弧动画分别失败，未修复，不回写前述历史研究为通过。使用者已接受 adr-6 的全亮必须物理发光要求，工程接入和交点核对不由决定自动完成；[决策索引](../reference/registry-semantic_decisions.md)汇总各项主动语义调整。

### 2026-10-17 合并手工结果

最新附加层候选 JAR `FB3908DB…4030A8CD` 获使用者六组通过，三项第一人称／水／优先级隐藏失败，按[现状](phase-1-before_vanilla_closeout.md#本轮合并手工验收反馈)和[清单](../development/testing-render_unification.md#2026-10-17-手工反馈)记录，不将早期候选或全部四阶段整体标为视觉通过。末影人眼睛内部照明已另作来源调查；浮空水晶相关绘制拒收按使用者要求留待后续。此前步骤及研究保持对应历史范围，不回填本次反馈到旧包。

随后按授权进行[所有者／查询续接与船内排除域集中修复](../research/investigation-query_visibility_repairs.md)：不扩充其他绘制族，固定 Vanilla 确定性分支的实际失败与 GPU 反例，补齐 priority 续接和来源船底边界。新候选构建、源码身份与投放由[当前状态](phase-1-before_vanilla_closeout.md#所有者与船内水面的集中修复候选)固定，旧包六组通过不自动转为新包的完整验收。

集中修复后的 AEA65916…548B4 候选已获使用者确认自身折射隐藏、自身发光轮廓隐藏和船内水面行为正确，详见[部分反馈](phase-1-before_vanilla_closeout.md#集中修复后的部分手工反馈)。厚玻璃后方清晰度仅为暂定改善。船只运动不连续列为优先调查；手臂及其他优先级重复成像只登记、暂不研究。新增开发方向统一进入[路线图](roadmap-radiance.md#本轮验收新增的优先开发)，不据此重开已通过阶段或当前立即实施新功能。

### 待决定与实现边界

- [adr-3：End Sea](../design/adr-3-end_sea_transport.md)：保留当前明确来源的世界层边界，不能称为完整 PT。
- [adr-5：未登记程序](../design/adr-5-unregistered_surface_programs.md)：未知自定义程序／状态组合保持来源适配器的现有接收行为，严格拒收尚未批准；不宣称名称推断已全数消失。
- [adr-6：全亮与物理发光](../design/adr-6-fullbright_transport.md)已接受，移出待决定项；具体来源归类、与旧查询例外的交点及实现验证仍待核对，不擅改现有强度。

### 检查点与恢复

`D:/Workspaces/Scratches/RadianceUnification/` 已保存 1-lines、2-space、3-targets、4-abi、5-materials、6-colours、7-emission、8-topology、9-views 的 patch 与 manifest。每份都是相对固定 HEAD `72d2e4a3275e782418f59142e2f2ea1f01be4120` 的累计完整差异，含必要未跟踪源码，不是只含本族的增量；已反向 dry-run 校验，不改暂存区。10-surfaces、11-total、12-surface_geometry 与最终 13-total 同样为完整累计补丁。checkpoint-13-total-source.zip、checkpoint-13-total-manifest.json 和 complete-source-manifest.json 固定全部必要未跟踪文件、原始字节哈希、固定子模块和规范化规则；先前补丁与快照不被覆盖。

恢复某个边界时，从固定 HEAD 建立独立副本，按 manifest 还原固定子模块，再应用该边界的完整 patch。先审查当前树与目标边界的差异，只迁移要恢复的部分；不能在使用者当前脏树直接逆用累计补丁，不能把后一族补丁叠加到前一族快照上。

### 验证边界

历史 C5 成功进程 101396 不代表之后 C6/D 修改的世界通过。最终客户端已明确区分 SDK 预设，核对实际模块、重载、保存重进与 normal-device-close 日志；开发类运行不冒充安装发行 JAR 启动。没有 GPU 根因结论、性能实验、Prism 操作、提交或推送；公开二进制许可门禁保持。

### 最终证据与产物

- [合并研究与十二组缺陷](../research/experiment-render_unification_bcd.md)、[族 10 门槛首败与纠正](../research/experiment-shared_surface_validation.md)。GAME 292、JNI 4、probe 28、bootstrap 11 通过／2 跳过，非 GPU 67/67、GPU 16/16，实际 Java 12 组 CPU/GPU 与 pack 232 编译／SPIR-V 通过；补齐及最终文档同步均执行同一 W-02 命令，原始日志单独保留。
- 发行文件为 `versions/1.21.1-neoforge/build/libs/radiance-1.21.1-neoforge-0.1.5-alpha.jar`，固定身份从 `D:/Workspaces/Scratches/RadianceSemanticsAudit/bcd-complete-artifact.json` 读取。`bcd-complete-precheck-artifact.json` 保留运行时参考容器，最终容器如仅 ZIP 元数据改变，逐成员核对结果另存；不把开发类启动冒称安装验证。
- 功能与原始文本／实际 SPIR-V 在 `validations/regression-line_capture/result/bcd-complete-rr2-20261017/` 与 `bcd-complete-fsr2-20261017/`；机器汇总为 `D:/Workspaces/Scratches/RadianceSemanticsAudit/bcd-complete-functional-results.json`。两次实际加载 core 与构建／内嵌一致，完整 hash 在清单中。
- [按优先级的合并目视步骤](../development/testing-render_unification.md)仍待使用者执行；RR 的 API 日志与 FSR 的成功返回均不代表 FG 已生成帧。眼睛／身体来源面位不同，保持不合资格时的独立层，不代填真实眼睛视觉。

复制客户端的回收被 Windows 拒绝，783,883,566 字节保持原位，详见[当前状态](phase-1-before_vanilla_closeout.md#本轮复制环境清理待处理)；没有改用其他删除方式。证据已保全，后续不再启动客户端，最终存储核对结果与源码清单一并保存。此操作边界独立于渲染工程及功能门槛。
