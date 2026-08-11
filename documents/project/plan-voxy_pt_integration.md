# Voxy 远景 PT 接入计划

## 目标

采用[专用后端设计](../design/architecture-voxy_pt_backend.md)，在 Radiance 侧复用 Voxy 的存储与压缩面生成，按变化建立持久世界 PT 代理，尽量消除每帧通用顶点翻译。2026-10-18 已获实施授权；构建、项目内功能证据与使用者视觉验收分别记录，不做性能实验。

## 范围

只在 Radiance 开发，Voxy 仓库只读，不再写旧 MCVR。保留既有工作区、近景精确渲染、完整查询参与、历史与资源退休；远景按使用者已接受的 Voxy LOD 语义，不追补源已舍弃的 PBR、动态纹理或精度。不复制 Voxy 算法、不并行 OpenGL context、不恢复性能实验或独立回放。允许项目内自动功能验证，完成后投放授权 Prism 实例而不启动；不提交或推送。

## 步骤

1. **完成**：核查 Voxy 既有 JAR 的 ZIP 完整性、实际元数据、关键 API 和 SHA-256，按本地输入登记原文件；不额外复制到临近硬上限的外部存储。
2. **完成**：沿实际初始化、world lease、摄入、模型上传、压缩网格与 native 世界场景确定接口；记录无 GL 切入点、GPU 表达、材质合同与已接受的 LOD 取舍、近远景联合发布、生命周期及验证门槛。
3. **已实现、限定流程通过**：条件兼容入口及无 GL 模型数据边界实际推进存储、摄入、模型和 `BuiltSection`；退出与重进、重载已有运行证据，不创建 `ModelStore` 或 GL renderer。异常和证明边界见实施记录。
4. **已实现、限定流程通过**：紧凑上传、GPU 位置解码、持久 BLAS 和公共材质读取已接通，独立 GPU 解码与实际两管线画面有证据；完整视觉仍待验。
5. **已实现、视觉待验**：独立 LOD cut、近景覆盖及有界卸载交接已接通，限定用例完成往返和世界重进；不把该流程扩大为全部接缝、折射／阴影或复杂地形均无洞。
6. **已实现基础映射、部分边界保留**：按[来源核查](../research/survey-voxy_lighting_water_iris.md)接入颜色、tint、alpha、流体和实际发光；不恢复源舍弃的 PBR／动画／精度。Advanced 独立远景 NEE／ReSTIR 候选及 GL 雾淡出逐像素等价尚未完成，详见[实施实验](../research/experiment-voxy_pt_integration.md)。
7. **自动检查与投放完成、视觉待验**：最终配套检查及限定功能流程通过，已投放授权 Prism 而未启动；交付身份与回执见实施实验。性能测试须另获明确恢复指令，不在本轮运行。

## 结果

本轮已找到并登记 Voxy `0.2.20-beta+1.21.1-neoforge` 的既有测试输入，已有具体静态设计。使用者明确确认本地源码与构建一致，直接参考当前 Voxy 工作区，不再把来源追查列为前置条件；实际测试仍固定 JAR 哈希。已进入 Radiance 侧实施，Voxy 工作区不写入；原静态检查的结果仅证明当时输入与维护变更，不代表当前实现通过。实施验证见[接入实验](../research/experiment-voxy_pt_integration.md)。

已执行 `spotlessApply`、`check verifyInputs verifyCompliance --strict` 成功，Voxy 新输入的文件、版本与哈希通过模板核对，严格合规失败 0、警告 0。原生非 GPU CTest 71/71、诊断工具两组各 2/2 实际通过；JVM 测试使用已有 UP-TO-DATE 结果，不写作本轮重新执行。此检查针对现有项目与本轮维护变更，不是尚未实现的 Voxy 接入回归。

1,558 个产品源码／资源／JNI 文件的原始字节聚合哈希与检查前一致，暂存区及 Voxy 工作区状态未变。静态阅读文件哈希、JAR 回执、检查日志及完整性结果位于 `D:/Workspaces/Scratches/RadianceSemanticsAudit/voxy-design-20261018/`；这是局部阅读证据，不冒称完整源码快照。后续使用者补充的 LOD 决定与光源／水面／Iris 调查仅更新文档，末次定向复核记录在该目录的 `final-check.log`。
