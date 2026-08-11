# 公共承接与完整组合兼容：进行中

有效授权：用户已在协调任务明确要求设定goal，连续完成公共Minecraft/NeoForge渲染承接与完整组合源码兼容。当前goal active，无token预算。旧取消、只读报告和第一批暂停不阻止此次实施。
边界：保持诊断清理成果、当前两仓结构/com.radiance/Java-JNI-Vulkan架构；不改暂存/分支/历史，不提交推送。文件删除仅回收站。参考源码及旧报告只读。仅本轮Artifacts写报告。
目视验收：全部由用户负责；禁止ComputerUse/Orca等，禁止自行启动交互游戏或参考GL窗口。允许编译/自动测试/无窗口GPU测试；准备A基础/B完整合法组合的仓库内验收入口，等待用户启动指令。
起点：Radiance1.21.1-neoforge@414d8e3，MCVR develop@9905c81；上批40路径诊断清理保留，当前新快照正在保存。
计划依赖：先固定版本与公共world/target/state/scope，再Veil/Simulated消费者与Sable，真正Flywheel backend独立推进；复用52CG→18工作包，不以BER/off替代实例化，不以假ID/空操作当完成。
代理：根负责整合与GL/target架构；按需复用最多2子Agent，不派生。下一步读取最终r2工作包、建立状态表并实施独立可确定修复。
完成：授权实现与工程验证完成才结束goal；用户目视仍待验。真正外部阻塞需证据并推进其他工作。当前未完成，不在任务一/二之间暂停。

进度：手部pitch一行修复完成/静态核对，尚待本轮最终测试。依赖固定为21.1.250/Create6.0.10/Sable2.0.5/Aero1.3.2/Veil4.3.2，版本隔离编译目录已建，JDK21下compileJava通过；默认JDK25的Groovy major69失败已解决。元数据MC精确1.21.1、NeoForge精确当前版本/FML4。
当前并行：Luna decision_loader_create调研world sink；Sol native_diagnostic_cleanup实施Framebuffer资源层；根GL/JNI/UI集成。FramebufferJava声明/接线已写但native尚未齐全，不得运行或标完成；详见Reports/framebuffer-integration-checkpoint.md。

当前工程检查点：Framebuffer真实资源层+首轮UI/JNI已Release编译通过。Root新增ui_framebuffer.cpp，native桥FramebufferProxy；最新masked-clear方法及clear.vert/frag(含2..32输出SPIRV variants)尚待新编译。必须继续完成，不可据首轮build标最终通过。RenderTargetStorageMixins/helper已由Sol实现，3个分配回滚测试通过；MainTarget/Ponderinit尚未接替。
活跃代理：world_mesh_sink(Sol High)拥有WorldMesh/RenderCaptureContract/BufferRenderer/RenderLayer/WorldRenderer/相关scope/EntityProxy及MCVR entities链；native_diagnostic_cleanup(Sol High)现负责tests/framebuffer_gpu_test.cpp与tests/CMakeLists(无窗口真GPU测试)。Luna decision_loader_create已完成world-sink-design.md。根继续Framebuffer/UI/blit/default目标/版本；不得与上述所有权冲突。
未完成关键边界：default/main snapshot目前仅UI surface，不是world+UI完整resolve；MainTarget仍Unsafe占位；raw GL/Veil/Flywheel还未实施；blit不同depth格式shader resolve、精确分数裁剪、scissor和完整write-mask语义需补；diagram嵌套target恢复待核；MSAA/纯S8尚拒绝；不得宣称全部FBO或完整模组兼容完成。
构建须JAVA_HOME=C:/Program Files/BellSoft/LibericaJDK-21-Full；默认JDK25遇Unsupported class file major69。当前未启动游戏/GUI；goal仍active，完成两重任务才暂停。
