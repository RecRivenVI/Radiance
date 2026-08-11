# Radiance 审计协调复核

> 当前项目名称统一为 Radiance。本文保留 2026-09-10 审计快照、结论和历史引用；其中旧称谓仅说明当时语境，不定义当前身份。后续工程与历史整合见 [当前工程记录](migration-template-configuration.md)。

审计日期：2026-09-10。此记录由总协调维护；项目主入口为 [正式证据账本](audit-port-status.md)，其余三份研究记录由项目主任务及子代理协调。本轮研究和协调复核已收尾；不能据此宣布移植完整或源码每个分支均已穷尽。

## 范围、身份与封存

- 当前旧 port：`D:/Workspaces/Repositories/GitHub/RecRivenVI/Radiance`。
- origin：`https://github.com/RecRivenVI/Radiance.git`；upstream：`https://github.com/Minecraft-Radiance/Radiance.git`。
- 当前分支 `stonecutter`，HEAD `9fe7811cf8b27f97b8b77307e982e3cc3942aee5`。实际审计对象是此 HEAD 加当前未提交工作树，不能归为 HEAD 的已提交实现。
- 开场 Git 状态：983 个未暂存删除、7 个修改、15 个暂存删除；另有未跟踪迁移内容。删除计数是路径状态，不能解释为删除了同等数量的能力。
- 内容封存：`D:/Workspaces/Artifacts/OldPortAudit/20260910/Radiance-start.json`；manifest SHA-256 `713715eb775c3a803a30cf2147b81d46cf01060d935da54cb76ef07991d304c5`。
- 封存包含 10,007 个实际文件，source seal `691c4c01073d2aed9095ec33834b1385b5d84839314b2edb63743a88da6cf95b`；同时记录缺失 tracked 路径、index 哈希及状态。逐项范围与排除项见 manifest。
- 封存从 tracked 加非 ignored untracked 文件取得，排除 runtime、构建缓存、IDE、本机私密配置和本轮 `audit-port-*` 文档，不读取软链接目标；不是运行实例、所有 ignored 文件或整机的快照。新增记录不改变该 source seal。
- 本轮仅研究及记录，不改产品、构建、配置或既有迁移状态，不启动游戏，不提交或推送。`Repositories/Local/Radiance` 的第二次实现不在范围内。

## 已独立核实的开场事实

### RAD-C001：原始 Java 移植的父提交可以恢复

`git rev-list --parents -n 1 cd67362ebb5d9f49c7e23e53f51d35d25409e6a2` 返回唯一父提交 `414d8e330a2fc6cb1e8630cc95f2302b2b97a0e8`。后者的 `gradle.properties` 明确是 Minecraft 1.21.4、Fabric Loader 0.18.3、Radiance 0.1.5-alpha。因此它有直接 Git 谱系依据，可作为原始移植的上游 Java 基线，不必猜测当前上游默认分支。

限制：谱系能固定源码比较对象，不能证明移植期间从未另行参考其他版本；后者需要具体来源记录支持。

### RAD-C002：MCVR 独立谱系与双上游配对的证据边界

只读核实兄弟 `RecRivenVI/MCVR`：HEAD `b611d24a044384290beaf93131ad51bf14e3d107`，父提交为 `9905c81b1999f5845bf66d13501d371c16adf561`，upstream 是 `Minecraft-Radiance/MCVR`。开场工作树 clean。它仅作为历史来源读取，不是当前 Radiance 构建依赖，不在其中修改文件。

当前 `components/vulkan-renderer/VENDOR.json` 明确以 fork 的 `b611d24...` 为导入基线，并标注导入了未提交的 JNI/CMake/CTest/NRD/FFX 适配。因此当前组件不能被称为“原样的上游 MCVR”。必须继续分解 upstream → fork commit → 导入时适配 → 当前工作树。

Radiance 上游 `414d8e...` 根树没有 `.gitmodules`；根构建配置、`.github`、README 与 MCVR 的构建说明没有固定另一仓 revision。两条 Initial port 父链、0.1.5 发布信息和主审核对的全部 80 个上游 Java native 源级签名共同支持采用这对基线，但不能补造官方不存在的 cross-repo lock。来源基点已恢复，配对依据和“未显式锁定”的限制同时保留；不是因基线待找而暂停全部研究。

### RAD-C003：五个注册节点不等于五个实现

当前 `settings.gradle.kts` 显式注册五个 `:version:<target>`。`1.21.1-neoforge/target.properties` 声明 `target.business=true` 和 `target.client=true`；其余四个目标声明 `target.business=false`、client/server false。抽查 `versions/26.2-fabric/build.gradle.kts` 仅有 governance-only 描述，无产品插件或源码接入。

因此“当前矩阵有五个节点”与“实际待审计的 NeoForge 1.21.1 产品实现”须分开。其余目标的完整逐项检查由映射记录补全，不能借旧构建汇总推定支持。

## 调度与恢复

已在实际任务清单和 cwd 中核对主任务“梳理 Radiance 与 MCVR 联动上下文”，ID `019ff1cf-fec9-7e90-9a71-e41d5cf31d51`。调度 API 接受 `gpt-6-astra`、`max` 的正式审计请求。主任务已回报三个真实 collaboration 代理 `/root/upstream_inventory`、`/root/current_mapping`、`/root/independent_review`，均以 `luna_worker` 和 `fork_turns=none` 创建；工具角色固定 GPT-5.6 Luna/max。工具返回 canonical task name，未提供额外 UUID。三个代理分别独占 upstream、mapping、review 文档。

总协调已完成本轮双仓配对边界、主要构建/加载链、DLSS 宏、早期窗口及选定材质变化的独立复核；源码封存结果见末节。更深的 GUI、GPU 队列、任意第三方及全硬件行为没有由本轮证明，按主入口的明确未查清单恢复。

补充调度读回：实际 rollout 本次 `turn_context` 的 `turn_id=01a08a35-3c88-7a92-b136-5f6d733875b1`，`model=gpt-6-astra`、`effort=max`，与任务状态接口一致。过滤后的三主任务元数据证据位于 `D:/Workspaces/Artifacts/OldPortAudit/20260910/main-task-models.json`，不包含其他会话内容。

## 第一批链路复核

### RAD-C004：native 组件确有实际构建接线，搬迁不等于功能删除

`versions/1.21.1-neoforge/build.gradle.kts:396-453` 将真实 `compileJava -h` 输出传给 `components/vulkan-renderer` 的 CMake 配置。`:468-535` 顺次依赖 INSTALL、CTest、Early Window 安装、stage 校验与资源同步；`:577-588` 将同步资源纳入 `processResources`。`platform/neoforge/RadianceNeoForge.java:8-13` 的客户端 Mod 构造器调用 `RadianceClient.initialize()`；后者在 `client/RadianceClient.java:36-111` 创建实例路径、提取 native 库和 shaders/modules、加载库并初始化 Options/Pipeline。

对照固定上游 `414d8e...:src/main/java/com/radiance/client/RadianceClient.java:28-109`：Fabric ClientModInitializer、游戏目录获取和 `core.dll/libcore.so` 名称改为 NeoForge 构造器、平台桥与 `radiance_native` 名称；提取库、shader/module、初始化选项与模块的主序列仍有对应实现。状态：本链路静态对应已确认；不据此确认 native 参数、线程、异常或视觉行为全部等价。相关下层语义仍由能力映射逐项展开。

### RAD-C005：开发启动与普通发布物的 Early Window 合同不同

当前 `build.gradle.kts:604-630` 在 Gradle run 任务中修改 earlydisplay classpath，并传 `radiance.earlyWindow.nativeLibrary`。`earlywindow/bridge/RadianceEarlyWindowNative.java:59-69` 强制读取该属性及现有 native 文件，否则抛错，没有在此自动提取桥接库。

主 Mod 资源同步白名单 `build.gradle.kts:530-533` 不包含 `radiance_early_window.dll` 或 patched earlydisplay JAR；`buildAndCollect:654-660` 则把它们作为 `early-window/` 旁边的独立交付物复制。Maven publication `:667-674` 只发布 Java component。因此“Gradle 开发启动接上了桥接”不能推导为“将普通 Mod JAR 放入 mods 即可获得同一接入链”。上游 README 的普通 JAR 安装流程不能未经核实直接沿用为当前发行保证。

状态更新：主任务 P-05 已排查目标资源/META-INF、buildSrc、根和 Target Gradle、scripts、documents 与验证输入，确认当前没有负责生产安装、早期 module layer 替换和相应启动参数的完整消费者。开发接线和旁置交付物已确认，普通主 JAR 交付链的该前置未提供。没有本轮实际启动失败证据，也未确定这一合同差异已获用户接受；后续先明确交付方式，再做全新普通 Launcher 环境验证。

### RAD-C006：两个验证任务名称不提供检查内容

`build.gradle.kts:592-597` 的 `verifySourceTopology` 与 `verifyPortContracts` 只设置 group，无断言、action 或依赖；`:598-599` 被 `check` 聚合。仅这些任务执行成功不构成源码拓扑或移植合同的证明。本条不否认其他 JVM/CTest/stage 检查，但它们各自实际覆盖必须另行核对。没有修改这些空任务。

### RAD-C007：透明材质处理包含原始 port 阶段就存在的语义改写

固定 MCVR 上游 `9905c81...:src/shader/util/alpha_mode.glsl` 只定义 opaque/cutout/transparent。当前组件同文件新增 `CUTOUT_LOW=9`、`COVERAGE=10`、`ADDITIVE=11`，并将值 2 明确为 transmission，旧 transparent 名保留别名；低 cutout 阈值为 0.1。`src/shader/util/labpbr.glsl:29,64` 新增 `allowAlphaTransmission` 参数，半透明 albedo 不再无条件启用材质 transmission。

当前 Java `client/vertex/PBRVertexConsumer.java:152-189` 将 vanilla translucent/moving-block 层识别为 transmission，将加色/lightning/glint 层识别为 additive，其余半透明层识别为 coverage。`src/shader/world/ray_tracing/internal/advanced/common/default.rahit:82-93` 对 coverage 按 alpha 随机拒绝交点，对 additive 拒绝成为默认不透明/折射遮挡；vanilla-pt/world/default.rahit 有对应分支。因此至少已经定位 Java 分类与两套 shader 消费逻辑，不能将该差异仅称包名或目录适配。完整顶点编码、各类材质的输出等价性仍由主任务映射继续检查。

来源追踪：上述 alpha_mode.glsl 和 labpbr.glsl 当前内容在仅规范 CRLF/LF 后，与 fork `b611d24...` 完全一致，且均不同于上游 `9905c81...`。这证明改动最迟已经存在于原始 MCVR port 提交，不能误归因于后来复制到 vulkan-renderer。

原因证据：PBRVertexConsumer 源码注释说明要区分 Minecraft alpha blend 与折射介质，这是实现意图的直接记录；尚不能据此证明该方案是唯一必要适配或已经获得用户接受。风险范围包括半透明实体、发光/叠加、阴影、透明资源包；应以同一内容场景分别对照两套 shader pack，不以静态分支存在宣称视觉等价。本轮仅记录，不调整算法。

### RAD-C008：复核主审 P-09，Early Window 活动路径确实含 CPU 栅格化

总协调回到当前源文件独立复核：`earlywindow/bridge/OfficialGlBridge.java:61-62` 将 draw 转为 native DRAW 操作；`src/main/native/early_window/radiance_early_window_vulkan.cpp:1812-1814` 的操作 20 为提交帧、21 为绘制、22 为屏幕尺寸。不是因为文件里某个未接入的实验函数才作判断。

实际 `official_draw` 调用 `official_raster_quad:458-484`，后者双层遍历矩形像素、纹理采样与混合并写 CPU 像素存储；`record_official_frame:1220-1257` 再 memcpy 到 staging 并执行 vkCmdCopyBufferToImage。由此能够确认“CPU 绘制后通过 Vulkan 上传/呈现”的活动链。

范围限定：这是 Target 自有的早期窗口适配，不是对整个主 renderer 的判断。无真实 OpenGL context、Vulkan presentation 与 GPU graphics-pipeline 栅格化是不同命题。先前诊断文本中的 TRANSLATED_TO_VULKAN 不能单独证明更强的 GPU 绘制合同。若既有用户要求还明确排除正式 CPU rasterizer，需以具体原始决策记录核定是否违反；不凭当前目录规则替用户补造接受或否决。主审已将本条纳入 P-09，后续验收须分别观察早期窗口与主世界。

### RAD-C009：独立确认 DLSS 返回码宏的局部变量遮蔽缺陷

主任务提出后，总协调直接回读 `components/vulkan-renderer/src/core/render/modules/world/dlss/dlss_wrapper.cpp:96-100,470-475`。当前先把 `NGX_VULKAN_EVALUATE_DLSSD_EXT` 的返回值存入外部 `const NVSDK_NGX_Result result`，然后调用 `NGX_RETURN_ON_FAIL(result)`。宏内部又声明同名变量，展开关键语句为：

```cpp
{
    NVSDK_NGX_Result result = checkNgxResult((result), __func__, __LINE__);
    if (NVSDK_NGX_FAILED(result)) return result;
}
```

内部变量的作用域在初始化表达式之前已开始，右侧 result 指向尚未初始化的内部变量，遮蔽真正的 API 返回码。因此该分支没有按预期检查实际 DLSS evaluate 结果，后续返回值及 `m_resetPending=false` 的执行不可靠。此结论来自源码宏展开，不依赖历史构建日志或一次未执行的运行实验。

来源：上游 `9905c81...:src/core/render/modules/world/dlss/dlss_wrapper.cpp:419-420` 直接把 API 调用表达式交给宏；fork `b611d24...:470-472` 已引入当前两步调用。因此属于原始 port 已存在的本地缺陷，不是后续组件目录搬迁新引入。

状态：静态缺陷已由主任务与总协调独立确认；范围为启用并到达 DLSS evaluate 的当前 native 路径。不能由此声称它是全部 DEVICE_LOST、黑屏或图像错误的根因。本轮没有构建、运行或修复；后续优先修复返回码保真并分别验证成功、失败与 reset 状态处理。

## 本轮交付与结束核查

主任务与三个 Luna/max 代理均已交付。正式入口用 34 个交叉审计单元连接双上游清单、当前映射、反例及主审证据；其中本地新增单元与上游能力分开，不把编号数量当功能完成率。Radiance 本体、MCVR 承接和整合链分别下结论，保留未知与后续八类验证任务。

总协调结束封存 `D:/Workspaces/Artifacts/OldPortAudit/20260910/Radiance-end.json`，SHA-256 `7c8a5502476b603e89050a30ea9368e6054df4bb7674a0188f704c14238e4fe4`。与开场同范围的 10,007 个现存文件逐项比较，变化 0；缺失 tracked 路径、HEAD、分支、origin/upstream 和 index 均相同。既有 15 个暂存删除保持，未把迁移改动提交。

跨仓核查结果为 `D:/Workspaces/Artifacts/OldPortAudit/20260910/coordination-verification.json`，SHA-256 `92347f94f9acc06929bf18090539531411cb902e4ca85603d3130164bd5b11d4`。本仓仅新增五份 audit-port 文档，`git diff --check` 通过；未改产品源码/资源/配置、未构建或运行测试/游戏、未 commit/push。报告的封存范围不包括运行实例和缓存，不能据此宣称逐文件验证了它们。
