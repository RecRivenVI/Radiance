# Radiance 清理对照

本批只移除观察逻辑；没有实施兼容修复或视觉裁决。固定上游HEAD为414d8e330a2fc6cb1e8630cc95f2302b2b97a0e8，实际起始未提交内容见Baseline/Radiance。

| 原审计/补充ID | 文件 | 移除与保留边界 |
|---|---|---|
| RJ-54 | src/main/java/com/radiance/compatibility/simulated/SimulatedDiagramCompatibility.java | Sable专用帧/diagram探针调用与import |
| RJ-54 | src/main/java/com/radiance/mixins/vulkan_render_integration/WorldRendererMixins.java | Sable专用帧/diagram探针调用与import |
| RJ-54 | src/main/resources/radiance.mixins.json | 只移除两个纯诊断Mixin注册，保留所有渲染Mixin |
| RJ-54 | src/main/java/com/radiance/compatibility/sable/SableSubLevelBridge.java | 只移除实体/BE/singleBlock观测计数；保留队列调用/变换及反射失败输出和既有回退 |
| RJ-54 | src/main/java/com/radiance/client/proxy/world/EntityProxy.java | 移除Sable变换实体观测计数，保留全部实体捕获 |
| RJ-54 | src/main/java/com/radiance/client/proxy/world/ChunkProxy.java | 删除allocation/release/rebuild日志，保留native调用、builtChunkNum/F3计数及释放 |
| RJ-59/ROOT-DIAG-J02 | src/main/java/com/radiance/client/shader/ShaderRegistry.java | 删除预热完成数量观察，保留warmup调用、generation检查和失败处理 |
| RJ-59/ROOT-DIAG-J02 | src/main/java/com/radiance/client/texture/ResourceReloadCoordinator.java | 删除reload成功观察日志，保留warmup执行时机及失败传播 |
| DIAG-BOOT-01 | src/bootstrap/java/com/radiance/bootstrap/RadianceImmediateWindowProvider.java | 移除交接时长/帧数/identity打印；保留firstGameFrame CAS、window一致性检查、nextTransitionFrame节流和全部锁/失败/销毁 |
| RJ-50/ROOT-DIAG-J01 | src/main/java/com/radiance/mixins/compatibility/flywheel/FlywheelVisualizationManagerMixins.java | 删除仅一次的Flywheel fallback观测调用，保留fallback本身 |
| DIAG-BOOT-01 | src/bootstrap/java/com/radiance/bootstrap/BootstrapResources.java | 启动runtime/JNI装载身份成功观察日志；保留实际装载、哈希校验、绑定和失败异常 |
| DIAG-BOOT-01 | src/bootstrap/java/com/radiance/bootstrap/RadianceModFileCandidateLocator.java | 启动runtime/JNI装载身份成功观察日志；保留实际装载、哈希校验、绑定和失败异常 |
| DIAG-BOOT-01 | src/main/java/com/radiance/client/RadianceClient.java | 启动runtime/JNI装载身份成功观察日志；保留实际装载、哈希校验、绑定和失败异常 |

4个纯诊断文件已回收：SableSubLevelDiagnostics、SableChunkedRenderDataDiagnosticsMixins、SableClientSubLevelDiagnosticsMixins、FlywheelCompatibility；相关渲染Mixin及返回false的既有BER fallback仍保留。证据Evidence/java-recycle.json。

## 有明确用途的保留项

- RJ-45的TimerQuery三个Mixin和RendererProxy/native GPU query：目标Minecraft将真实GPU时长用于gpuUtilization与F3。不是把普通F3图形删除，审计标签不能覆盖代码用途；本批保留，见独立边界报告。
- RendererDiagnostics.backendString：F3硬件信息路径，未因类名含Diagnostics删除。
- RendererProxy.close/checkVkResult、ShaderRegistry失败日志、ResourceReloadCoordinator suppressed/throw：真实失败的输出和传播，不能删成无声失败。
- RenderCaptureContract.reportDiscard及一次性去重：报告实际不支持的MeshData/FBO操作；与既有绕过边界相连，本批不以静默丢绘制代替报告，也未声称修复绕过。
- Sable反射失败一次性警告：现有失败/null回退的唯一输出，保留可见失败；不属于帧/计数探针。若后续替换回退，应配合真实错误契约处理。
- Bootstrap配置/开发SERVICE定位分支说明、默认图标错误、选项读取错误保留；装载身份/数量/成功时点跟踪已去。
- NativeRuntime身份读取/画布读回边界见独立报告；交接的身份打印与时长计数已移除。
- 上游Options/Pipeline/纹理注册的注释日志未清理；不将上游注释当本地排查新增。

## 业务保护证据

Evidence/java-business-protection.json核对关键native提交、释放、变换、异常传播、firstGameFrame/handedOff、节流与锁调用数量不变，以及F3查询、名称牌样式、轮廓几何等关键文件字节不变。与本批patch审查互相补充，不能代替视觉验收。

## 后续清理与最终Java测试

BootstrapState.nativeIdentities无调用转发入口已删除，NativeRuntime实际检查/测试接口保留。最终Java源码修改为14个文件，加4个纯诊断文件回收；源码列表以java-edits.json与final-inventory.json为准。

最终test/bootstrapTest：39项记录，37通过，2个硬件窗口测试因本批禁止GUI而未启用，失败0。见Evidence/java-tests-final-summary.json及JavaTestsFinal原始XML。
