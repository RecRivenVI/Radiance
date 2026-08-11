# 当前实施索引（唯一当前结论）

## 当前授权与goal
本线程已收到正式接管并连续完成公共承接+完整合法模组组合源码兼容的当前实施授权，覆盖初始只读交接阶段。goal active，无预算。不得退回旧r2报告修订/只读暂停阶段；两项工程工作与分发/A-B交付实际完成后才暂停给用户目视。

## 硬边界
两独立上游结构；保留既有未提交移植/诊断清理。禁止stage/commit/push/发布/历史重写/用户实例或存档配置改动/ComputerUse/交互游戏。删除进回收站。旧Artifacts/r2/审计/诊断清理只读。根+最多2个子Agent，子Agent不派生。

## 基线与执行权
Radiance 1.21.1-neoforge@414d8e330a2fc6cb1e8630cc95f2302b2b97a0e8；MCVR develop@9905c81b1999f5845bf66d13501d371c16adf561。
Baseline含Git可见文件副本、binary patches、原index/status与manifest（1080条目，保存期间无漂移）。未复制或改用户ignored运行数据；子模块记录引用且不改。
旧根read_thread=idle，旧Resume记录两代理interrupted；跨树不能枚举，不把不可枚举视作仍运行。当前仅本树写入。

## 已验证检查点（不是游戏验收）
- public-core-normal-viewport.log：Release core+shaders PASS；含persistent raster、world frame AFTER_LEVEL lifetime、color/depth blit、per-engine Flywheel、normalCorrection144-byte ABI、DiagramTarget FBO、native clear/getViewport/RGB16F、viewport跨目标GL坐标。
- framebuffer-blit-gpu-fixed.log：6case PASS，含D16→D32 fractional clip/flip/scissor；生产shader/原语级，非Java/JNI端到端。
- instancing-contract-run.log：normal correction+八adapter精确bytes PASS。
- persistent-raster.md：Java全树compile与9定向test PASS，已交接root。
- veil-fbo.md：当时Java/签名核对PASS；其中旧固定MRT=1测试已被root移除，实际cap改为native设备值，不能沿用旧5项数认定最新代码。

## 当前新增、尚未最终编译闭环
- Reports/veil-lifecycle-progress.md：真实VeilRenderer/DynamicBuffer纹理、native limits；四个取消生命周期/事件mixins已回收；AFTER_LEVEL renderPost在fuse后执行。14条shader bridge mixins已注册，shader worker仍验证。
- Reports/readback-progress.md：Framework flushForReadback分段提交/续录；FramebufferProxy.readPixels真实RGBA/depth/stencil readback；MainColor alias texture download；Diagram原aabb alpha读取与NativeImage depth接线。最新native集合待统一编译/测试。
- MainAlias作为custom FBO附件逐帧解析，MRT限制真实设备化、depth-only FBO read NONE；最新集合待编译。
- 普通与FBO纹理Y方向由shader worker按sampler来源处理（TextureProxy.isFramebufferTexture根已实现）。

## 当前所有权与下一动作
- 根：Framework/pipeline/UIModule/framebuffers/textures/readback、Veil lifecycle/DynamicBuffer/state与共享mixins JSON/进度/最终整合。下一步完善readback测试、恢复Veil兼容plugin中仅因旧renderer为空而取消的安全handler、核固定consumer；等待worker freeze后统一Java/JNI/native。
- /root/persistent_raster（Sol High，当前任务为Veil shader）：ShaderRegistry/Translator/Field、ShaderProxy单点与VeilShaderBridge及shader/compiler/uniform/block mixins/tests；build.gradle精确Veil测试依赖。不得重派旧persistent模块。
- /root/veil_fbo（Sol High，当前任务为Flywheel完成）：Java flywheel/NativeInstancingProxy；native instancing、InstanceAppearance、vertex.glsl实例段、world_prepare与ray_tracing_module set1 binding11 light表。采用官方LightDataCollector 6564-byte layout，新增JNI uploadLightSection(engine,section,data,size)，实现真实per-vertex/per-hit光照，不能代表点近似。根已释放其native freeze窗口。
- /root/flywheel_audit：已完成释放，只读结果在Reports/flywheel-progress.md，不唤醒。

## 未完成的实质边界
Veil完整shader/lifecycle/dynamic outputs/post/light、Aero levitite tessellation（真实固定consumer，不能因cap=false排除）、Flywheel light/material/config/reload/多manager/motion/复用、world未知shader/SKYBOX/阶段输出、粒子等18包剩余核验；native readback/嵌套targets最终验证、最终Release单JAR/哈希、A/B人工指南均未交付。
52CG→18包索引为Reports/workpackage-status.json，其中旧字段已移入historical_snapshot。源哈希/日志只覆盖对应时点；不把当前源码自动标通过。

## 恢复规则
压缩后先读本索引、当前工作包、最新子报告，再核源码与当前代理状态。保持当前实施授权，不退回只读交接，不中断已授权worker，不改旧报告。发现记录落后时在本handoff更新；无法实现项记录未完成并推进独立范围，不虚报goal完成。
