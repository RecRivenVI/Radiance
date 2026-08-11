# 第一批：本地诊断清理

已完成授权范围内的清理、Release构建、测试与单JAR校验，暂停等待审查。未开始兼容/视觉/性能整改。

本批修改范围为40个Git可见路径：Radiance18（14编辑、4回收），MCVR22（19编辑、3回收）。这是相对本批起始快照的差异，不是两仓原有全部未提交差异。另刷新ignored的core.dll生成资源，core.lib哈希未变；原payload已备份。

## 清理结果

- 移除Sable帧/编译/布局探针及计数、Flywheel fallback观察包装、早期窗口身份打印/交接计时、启动/JNI装载跟踪、warmup成功计数。
- 移除native checkpoint、device fault/address-binding/对象命名跟踪、相关开关及扩展、显存预算轮询、重建/预热计时和ShaderObjectReuse观察统计。
- 7个纯诊断文件均进入Windows回收站；仅诊断marker存在性测试被删除。没有删除渲染能力、原有图形或有效功能测试。
- 保留上游日志/诊断、有效VkResult和失败报告、同步与资源所有权、缓存/预热/曝光/重建本体及原执行时机。F3+B/F3+G发光、名称牌50%、普通选框等没有调整；MC-04.2裁决没有在本批实施。

详细：[40路径清理台账](Reports/cleanup-ledger.csv)、[Radiance明细](Reports/java-cleanup.md)、[MCVR明细](Reports/native-cleanup-details.md)、[功能与诊断边界纠正](Reports/functional-diagnostic-boundary.md)。台账ID是关联范围，不表示删除整个原审计差异块。

## 验证状态

| 检查 | 结果与范围 | 证据 |
|---|---|---|
| Java/Bootstrap编译与测试 | 37通过，2个硬件窗口测试未启用；无失败 | Evidence/java-tests-final.log、java-tests-final-summary.json、JavaTestsFinal/ |
| MCVR Release core | 通过 | Evidence/native-release-build.log |
| CTest | 17/17通过；含真实Vulkan exposure-gpu，其余为静态/contract检查，不是游戏验收 | Evidence/native-ctest-release.log |
| MCVR DEBUG | 源编译通过；完整链接阻塞：现有shaderc_combined Release CRT与/MDd不匹配，484项LNK2038/LNK1319；未扩大到SDK整改 | Evidence/native-debug-compile.log、native-debug-build.log |
| 单JAR | verifyRuntimeResources、verifyDistributedJar及内容/哈希检查通过 | Evidence/packaging-final.log、artifact-validation.json |
| 业务保护 | 根审重建/预热返回值、DLSS资源与屏障；指定Java保护条件和GPU profile/denoise函数保留 | Evidence/java-business-protection.json、native-preserved-functions-preliminary.json、本批patch |
| Git/输入保全 | HEAD/分支/暂存索引不变；原审计输入与r2保全；已有submodule升级diff不变；本批反向patch检查和恢复干跑通过 | Evidence/final-inventory.json、submodule-preservation.json、restore-preflight-final.log |
| 游戏/画面/交接实测 | 未执行；未启动客户端、GUI或Prism | 后续另行授权 |

## 产物

[Radiance单JAR](Products/Radiance-0.1.5-alpha-neoforge-1.21.1.jar)

- JAR SHA-256：`43d6de347b2c95520c8d0731802b97b9f6413e266f5463943c5a84d020463d5e`
- core.dll SHA-256：`a0cff9bd3c9e76aa33ea60e3d34d7784cca67bbc529cf847f36fa984d7d42030`
- 嵌入GAME SHA-256：`7e83927ffa471cee528c73e6ae35779e01f3e450e9908e144df282746a770f66`

JAR包含本批最终core，已核移除的诊断类/标记不在产物中，F3 TimerQuery及有效bootstrap JNI仍存在。中间产物0873…及artifact-validation-intermediate.json不是最终交付。仅本地构建，未投放用户实例、未发布。

## 保留项与未完成边界

- RJ-45原分类过宽：TimerQuery/native GPU timestamp是F3与原版metrics sampler的实际能力，本批保留，不能按诊断标签删除。
- NativeRuntime.identities仍供生产窗口一致性保护与测试使用；打印已清。captureCanvas仅由真实GPU测试调用、仍编入生产native，未迁到独立测试构建；直接删会破坏像素/交接测试，本批保留此测试接缝，不能宣称生产API已完全隔离。
- RenderCaptureContract一次性不支持操作报告、Sable反射失败警告和正式错误输出保留。它们说明现有功能缺口/失败，不作为观察计数清理成无声丢绘制。已有Veil/Flywheel/假ID绕过没有整改，也未因去掉日志而标完成。
- 原17项待确认、版本漂移、兼容及运行/画面验证仍是后续事项，不是本批删除授权。没有修改SDK/submodule。
- DEBUG链接限制已记录；Release及最终JAR可构建，不据此宣称运行稳定或渲染效果正确。

## 恢复与暂停

Baseline/下包含两仓起始全部Git可见文件的zip、manifest、binary Git diff、暂存diff/index与refs。Evidence/Radiance/batch.patch及Evidence/MCVR/batch.patch只表示本批改动，均通过反向检查；ignored runtime原件另在Baseline/RuntimePayload。

Scripts/Restore-Batch.ps1默认只做哈希预检，只有显式-Apply才恢复源文件及runtime，不改Git索引/分支；如之后有修改会先拒绝。恢复中需要删除本批新增文件时也仅使用回收站。**本轮仅运行干跑，没有执行恢复。**

本轮根整合，Sol实施native，Luna有界核验功能诊断边界；同时最多根+2子Agent。已完成源代码冻结和最终验证，保留全部未提交变更，暂停等待用户审查，不提交、不推送、不开展下一批。
