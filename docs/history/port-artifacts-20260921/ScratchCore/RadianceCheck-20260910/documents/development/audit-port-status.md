# Radiance 移植现状静态审计

> 当前项目名称统一为 Radiance。本文保留 2026-09-10 审计快照、结论和历史引用；其中旧称谓仅说明当时语境，不定义当前身份。后续工程与历史整合见 [当前工程记录](migration-template-configuration.md)。

## 本轮范围与进行状态

2026-09-10 开始。只进行源码研究、Git 只读核查与审计记录；不修改实现、配置、依赖、Target、模块边界，不构建/测试/启动游戏，不 stage/commit/push。当前普通源码组件 `components/vulkan-renderer` 是既定路线，不重新迁移。旧构建 PASS 不是本轮结论。

阶段：本轮已定静态审计范围完成并已合并复核；三子代理均已完成，结束内容/索引复查完成。已暂停等待总协调复核。此处“目录覆盖完成”不等于移植功能完整，未穷尽与动态未验证项明确保留。

## 身份和快照

- 当前 cwd/Git root：`D:\Workspaces\Repositories\GitHub\RecRivenVI\Radiance`；旧 port，不是 `Repositories\Local\Radiance`。
- 分支 `stonecutter`；HEAD `9fe7811cf8b27f97b8b77307e982e3cc3942aee5`（Update）。前一提交为 `cd67362ebb5d9f49c7e23e53f51d35d25409e6a2`（Initial port）。
- origin `https://github.com/RecRivenVI/Radiance.git`；upstream `https://github.com/Minecraft-Radiance/Radiance.git`。
- 开场摘要：15 staged 删除、990 tracked unstaged（983 删除、7 修改）、9 个 untracked 目录摘要；完整逐文件状态见外部封存，目录摘要不等于源码文件数。
- 总协调封存：`D:\Workspaces\Artifacts\OldPortAudit\20260910\Radiance-start.json`，SHA256 `713715eb775c3a803a30cf2147b81d46cf01060d935da54cb76ef07991d304c5`；sourceSeal `691c4c01073d2aed9095ec33834b1385b5d84839314b2edb63743a88da6cf95b`。
- 本任务独立封存目录：`D:\Workspaces\Artifacts\RadiancePortAudit\20260910-152809`。选择 index 路径和全部非忽略 untracked，排除 runtime、build/cache、本机秘密配置与正在编辑的 audit-port 文档。包括 vendored source/固定 SDK 二进制；已删除 tracked 路径保留缺失记录。完整规则在 manifest。
- 初版 `start/source-manifest.json` 的文件 SHA 清单有效，但采集脚本 PowerShell 单项字符串索引使 head/headTree/branch 被截为单字符；保留该原始证据，修正采集脚本后重采 `start-v2`，不得以初版元数据作身份结论。
- 内容结论绑定 dirty snapshot，不能归到 HEAD；结尾复采并对比内容/索引漂移。

修正版 `start-v2/source-manifest.json` SHA256：`D686F06D196DCCF6B058181FA32852F6436B45ED2EFECCAC4EDFDAFBD1AB76BD`；MCVR 只读封存 `mcvr-start/source-manifest.json` SHA256：`4B4E2F81F9FF57548089FB8B60FC697622EB7873A813E9B660056F097EFDFED0`。两者均位于本任务外部封存目录。索引原文、全量 status、ignored 摘要、tracked/index raw diff 分别保存。没有复制运行实例/私人配置内容。

## 适用规则

已读 workspace `AGENTS.md`/`CLAUDE.md`、当前仓库 `AGENTS.md`/README、组件 AGENTS 与 multi-target-layout、当前构建记录。当前仓库无 CLAUDE，目标/审计目录未找到更深 AGENTS。

规则漂移：当前 Ravens-mod-template/AGENTS.md SHA256 为 `A796D0C867D3D52B8048C73B07D67054DC1CF06C068FE41F7DD7E6B768E61244`，仓库声明固定基线为 `5527638e1fc256cdd2c4594e1d3021de6665257673e7cc092452db1fa490c6b8`。本轮只按明确审计授权写四份 development/audit-port 文档，不采用模板新规则重组实现。

## 双上游基线

| 轴 | 原始移植父基线 | 当前承接来源 | 状态 |
| --- | --- | --- | --- |
| Radiance | `414d8e330a2fc6cb1e8630cc95f2302b2b97a0e8` | Initial port → Update → 当前 dirty Target | 唯一 Initial port 父树已证实 |
| MCVR | `9905c81b1999f5845bf66d13501d371c16adf561` | `b611d24a044384290beaf93131ad51bf14e3d107` → dirty vendor 集成 | 唯一 Initial port 父树已证实 |

两条父链是当时配对候选；上游 Radiance 未发现 gitlink，目前未取得密码学意义的 cross-repo lock。不能用 VENDOR 当前 port commit 冒充最初上游版本。接下来核对历史构建引用、JNI 参数和版本对应；若不存在显式锁，保持“来源确定、配对有依据但未硬锁”的限制。

配对复核：Radiance 上游 `README.md:123-129` 只给未固定 revision 的 MCVR clone；native README 要求手动传 Java root；各自唯一 Initial port 父树及 0.1.5 发布主题相容。下述 P-03 的上游 80 个 Java native 声明全部有同名、同 JNI C 类型/参数数目的 native 对应，进一步支持该配对；仍不能补造上游不存在的 lock。以这两个父树审计原始移植，不用 upstream/main 当前最新树判缺失。本轮未获取基线后新功能列表，不能把那些变化混入原始遗漏。

独立只读参考副本通过 `git archive` 精确提取到外部 `reference-extracts/Radiance-414d8e3` 与 `MCVR-9905c81`，不是新的活动仓库、不改原仓库/refs。对应 tar SHA256：`0AE2CABBEBBCB945427E88BC1AAE0775F30297E3F7608A989FECB821B9C2D34D`、`137EFE9AA2ADE643C05ED67038CBBBE3E90115BF753067117738D91B383051F0`。gitlink 依赖仅保留指针，未假装 tar 包含子模块文件。

MCVR 独立仓库 `D:\Workspaces\Repositories\GitHub\RecRivenVI\MCVR` 仅为本轮明确允许的历史只读来源；HEAD `b611d24…`，开场工作树 clean，14 个 direct submodule 均有固定 revision。禁止任何修改或初始化。

## 审计分工与账本

- 主任务：本文件；配对基线、初始化/销毁、JNI ABI、构建/安装/资源/加载整链，复核高风险结论。
- A `/root/upstream_inventory`：`audit-port-upstream.md`，双上游能力/资源/依赖，ID `U-Jxx` / `U-Nxx`。
- B `/root/current_mapping`：`audit-port-mapping.md`，当前逐 Target 接线与反向新增，ID `M-xx`。
- C `/root/independent_review`：`audit-port-review.md`，独立反例/遗漏/可达性，ID `R-xx`。

三者均实际通过 `collaboration.spawn_agent(agent_type="luna_worker",fork_turns="none")` 创建，工具规定 GPT-5.6 Luna + max。返回真实 canonical task ID 如上；不编造 UUID。总协调独占 `audit-port-coordination.md`，本任务不编辑。

主任务模型也已由总协调从本次turn_context独立读回为 GPT-6 Astra / max，不仅是请求值；过滤后的调度证据 `D:\Workspaces\Artifacts\OldPortAudit\20260910\main-task-models.json`（SHA256 `DFC29E9F3DF5F41A2BF1C69F49AC0C1A9BBB6D69624C2EC220307CE7BD862BFD`），本任务turnId `01a08a35-3c88-7a92-b136-5f6d733875b1`。

最终代理状态：A已完成U-J01..17/U-N01..17共34单元（并完成第二轮措辞/谱系校准）；B已完成M-01..16；C已完成R-01..07并校准路径/原因归类。三个视角实际均检查Java和native，不用Java文件相似度替代承接判定。

状态词严格区分：未检查、尚未找到对应、定位但接入未确认、部分实现、空壳或不可达、确认缺失、静态对应已确认、行为待动态验证。存在/映射不是行为等价，检查覆盖率不是移植完成率。

## 第一批主链映射

### P-01 Java/Native 构建入口迁移

- 上游 Radiance `414d8e3:build.gradle:compileJava` 生成 `src/main/native/include`，Loom/Yarn `1.21.4+build.8`、Fabric API `0.119.4+1.21.4`；上游 MCVR `9905c81:CMakeLists.txt` 要求 `JAVA_PROJECT_ROOT_DIR`，JNI include 指向该源目录，native install 写 Java `src/main/resources`。
- 当前 `versions/1.21.1-neoforge/build.gradle.kts:generatedJniHeaders/configureNative/installNative/testNative/prepareRuntime` 明确链为真实 compileJava → Target/build/generated/jni → 普通 component configure/build/install → CTest → Target 自有 Early Window bridge → stage 验证 → processResources。
- 类型：版本/Loader适配 + 结构/产物边界替换；原因有工程迁移记录直接证据；目前仅静态接线确认，未执行当前任务图。
- 后续：逐符号核 JNI 参数与 native 资源消费；不能把历史 17 CTest/96 export 作为本轮实际执行结果。

### P-02 本次 Target 事实

- 当前 `settings.gradle.kts` 显式映射 `:version:<target>` 到 `versions/<target>`，不是旧历史 Stonecutter source replay。
- `1.21.1-neoforge/target.properties` 唯一 `target.business=true`、client=true、server=false；NeoForge 21.1.248、Java21。其余四节点治理声明的实际空源码/任务接线待逐项记录。
- 类型：后增治理矩阵 + 单业务节点版本/Loader适配；不能宣称 Fabric1.21.4 原分支在当前 Target 矩阵仍可运行。

### P-03 JNI 名称、签名和分层 ABI

- 本轮对源声明进行独立只读语法扫描，不执行 javac/CMake/CTest：上游 80 Java native /16 owners，对应 81 C++ exports；当前主 renderer 96 Java native /17 owners，Early Window 另 9 声明/1 owner，当前共 105 声明。
- 上游与当前分别 name / primitive-or-object JNI C type / arity 对照，均没有缺失声明实现或类型/参数数目不匹配。扫描工具初版把 C++ 顶层 `const jint` 读成 const，产生5个假阳性；回读原声明确认后修正扫描器，原结果保留，不作为产品缺陷。
- 完整新结果：外部 `jni-source-inventory-v2.json`，SHA256 `da357762ef3c76e632163d419aa321c55f7aee72605b6b96fd22e539ba08a98a`。这不是编译器/二进制导出/加载器/指针生命周期验证。
- 真正接口适配：`RendererProxy.{initRenderer,acquireContext,submitCommand,present,close,takeScreenshot}` 从 void native 改成 Java wrapper + int-return `*Native`，调用 `checkVkResult`；不是六项能力删除。当前 `jni_exception.hpp:invokeForVkResult/invokeVoid` 转 IllegalStateException；上游 getproc/init 的 abort 改为 exception。原因属运行错误报告/安全边界，不是必要 MC 版本 API。
- `ChunkProxy.rebuildSingle` 多一个 boolean（opaque/transparency 分类契约）需同时看 Java 类型与 native body；external chunk allocate/transform/release、DiagramState、纹理下载/release generation、GPU计时、资源重载事务与 entity post effect 是本地接口扩展。具体行为见分账本，不能统称命名迁移。
- 反向 export：上游与当前都有无 Java 声明 `TextureProxy_performQueuedUpload`，不是本地新遗漏；Early Window 另有9个本地 Java 不调用的 experimental exports（m4*/semanticFrame/snapshot 等），记为本边界不可达，不将它们声称为已接入渲染功能。
- 当前 native CMake `src/core/CMakeLists.txt:1-4` 对整个 `src/core/*.cpp` 建库，包含真实 middleware；JNI headers 来自真实主 sourceSet。Early Window CMake单独显式4个 cpp。已有 `tests/verify_jni_coverage.cmake` 只查名字子串/文件，并不验证全参数或运行 ABI，也不覆盖独立 Early Window 接入。

### P-04 官方 Early Window 与 main renderer 所有权

- 当前开发链 `PatchOfficialEarlyDisplayTask.transform` 重写官方 `net/neoforged/fml/earlydisplay` 类的 GL32C/GL.createCapabilities/选定 GLFW 调用，保留原 DisplayWindow/provider 类；`OfficialGlfwBridge.glfwCreateWindow` 在官方调用点创建 NO_API handle，native init/present。未找到旧 close-provider/NoVizFallback 替换路径。
- `NeoForgeWindowHandoffMixins` 在 Minecraft Window 构造调用 `ImmediateWindowHandler.setupMinecraftWindow` 的位置转 `NeoForgeVulkanWindowHandoff.handoffOfficialWindow`；验证 NO_API/currentContext=0 后反射调用独立 bridge，要求同一个非零 handle。
- `RadianceEarlyWindowNative.completeOfficialHandoff` capture 最后一帧、shutdown early Vulkan presenter；`NeoForgeLoadingOverlayMixins` 将 framebuffer texture 改用捕获的官方帧，同时保留 loading overlay 类和其 Java流程。静态存在/接线已确认；逐帧 loading 动画等价、全部GL仿真分支和错误退出尚未动态验证，不能把“保留类名”写成“完全等价”。
- `MinecraftClientMixins.initRenderer` 先确保 Java初始化，在独立大栈线程初始化主renderer并 join，再 load/build pipeline；上游本来已有大栈初始化线程，本地增加失败跨线程传递，不算新渲染架构。
- native `RendererProxy_initRendererNative` 绑定 JVM 已加载 GLFW，`Renderer::init` → `Framework` borrowed Window → Vulkan surface；`vk::Window` borrowed 构造验证 NO_API，析构只销毁 surface。另一个 owned-window 构造仍可 terminate/exit，但不是当前传 windowHandle 的调用路径，不能混称当前新开窗口。
- Minecraft close TAIL → RendererProxy.close → native waitDeviceIdle/world.close/framework.close。错误返回非零只记录，不重建设备；Framework.recordFailure 会停后续提交。其它未包裹JNI和 inherited exit 仍在，不能由局部异常包装推出全局不崩溃。

### P-05 高风险：开发启动与普通用户生产启动缺口

状态：**开发启动接线已静态确认；主 Mod JAR 单独交付的 Early Window 前置接入确认未提供**。这不是本轮实际启动失败，也不是世界渲染崩溃根因结论。

主任务亲自复核：

1. `versions/1.21.1-neoforge/build.gradle.kts:192-207` 将 `earlywindow/bridge/**` 排除主 sourceSet，单独编译；`PatchOfficialEarlyDisplayTask.kt` 的 bridgeRoot 遍历将类加入 patched earlydisplay。
2. 同 Target `syncRadianceNativeResources` 的白名单只有主 native、XeSS、shaders、manifest，没有 early DLL/earlydisplay；`RadianceClient.initializeInternal` 也只从主JAR提取主native和资源，没有早期注入逻辑。
3. `PatchOfficialEarlyDisplayClasspathTask` 修改 ModDev `clientLegacyClasspath.txt`；五个 `runClient*` 才显式 dependsOn patch 并设置 `-Dradiance.earlyWindow.nativeLibrary=...`。不是普通模组加载早期的服务安装器。
4. `RadianceEarlyWindowNative.load:59-69` 强制 property 路径且无自动提取；`NeoForgeVulkanWindowHandoff` 强制 NO_API/bridge 类可用。
5. `buildAndCollect` 把 patched JAR 与 DLL 放在独立 `early-window/`；Maven `mavenJava` 仅 `from(components["java"])`。旁置产物存在不等于被用户加载器选中。
6. 已查 target resources/META-INF、全部 buildSrc、根/target Gradle、scripts、documents 和 validations 的非runtime/非result输入；未找到负责生产安装/早期 module layer 替换/启动参数的入口。现有验证说明只指向 Gradle。

影响：原来“用户只放主模组JAR和NVIDIA DLL”的安装边界在此工作树尚无可达完整实现。需要用户决定正式早期交付合同，或者后续授权补齐；不能在本轮擅自改依赖或修复。总协调也独立观察同一边界。

### P-06 Manifest/资源加载与验证强度

- shader CMake 全量编译非 internal GLSL 为 `_stage.spv`；internal `vanilla-pt/advanced` 连同 util/common/SHARC源码打ZIP，priority目录被显式拷入二者；不把“internal未离线SPV”判缺失，因为它们走运行期shaderc。当前 Module资源仍在 Target/resources/modules 并由 RadianceClient复制、Java Pipeline→native constructors 消费。
- `CMakeLists.txt:42-55` 按排序后的 header basename+文件SHA串计算 JNI哈希；Gradle VerifyRadianceNativeStageTask同算法核对target/hash/native/shaders，`processResources`依赖此stage。该hash只覆盖头文件，不覆盖所有native/shader内容，不能代表renderer版本seal。
- JAR gate只检查主native、manifest、至少一个SPV、无根nvngx、无旧Java package；没有校验 P-05 的EarlyWindow生产前置链。故旧“JAR验证PASS”不等于用户端首次启动合同通过。
- `verifySourceTopology`、`verifyPortContracts` 是空任务；`verifyTargetContract` 的实质断言在配置期脚本内。不能靠这些task名字宣称完成语义审计。

### P-07 resize/reload/设备错误边界（只读风险，不定根因）

- 当前 `Framework::recreate:440-521` 仍是递归锁下 wait主queue，零尺寸循环 `GLFW_WaitEvents`，清contexts和重建swapchain/pipeline。`Renderer::close` 等全device；资源重载另有begin/end事务。不要把历史“拟引入timeline retire”的计划当当前实现。
- 这些改动包含错误状态返回/停止提交、按帧资源保活等本地逻辑；是否足够覆盖所有后台BLAS/NGX资源仍需逐提交/资源生命期证明与动态证据。目前源码不能证明已根除device-lost，也不能凭旧故障报告确定本次源树根因。

### P-08 平台覆盖收窄：上游 Linux 与当前 Early Window

- 上游 RadianceClient 及 MCVR CMake/README明确存在 Windows/Linux 加载与构建分支。当前主 renderer仍保留 `.so`、dlopen与Linux NGX；这部分不是整个移除。
- 但当前唯一完整 Target configure 写死 `Visual Studio 18 2026`、x64、early DLL filename；Early Window CMake无平台分支定义 `VK_USE_PLATFORM_WIN32_KHR`，native `create_instance:1079` 强制 WIN32_SURFACE extension、`create_surface:1125` 无条件使用 `HMODULE/GetModuleHandleW(L"glfw.dll")`。不存在同链 Linux window bridge。
- 状态：Linux 整体开发/运行链不具备上游那样的静态对应；并非本轮实测 Linux失败。当前项目是否有意只承诺Windows，应由用户明确，不能用Target MC/Loader相同推导OS支持相同，也不能因保留libradiance_native.so分支就宣称Linux完整支持。

### P-09 关键实现差异：Early Window 是 CPU 栅格化后 Vulkan 呈现

主任务独立回读 active call chain，而非日志字符串：

- `OfficialGlBridge.java:15,60-62`：BLIT=20，DRAW=21。glDrawElements将count按quad换算，glDrawArrays丢弃mode/first，只把count送native DRAW。
- `radiance_early_window_vulkan.cpp:1812-1813`：BLIT→`official_submit_frame`，DRAW→`official_draw`；`official_draw:488-500` 每四个vertex调 `official_raster_quad`。
- `official_raster_quad:458-484` 在CPU按矩形包围盒逐x/y循环，最近邻读取CPU texture.pixels，`official_blend`写CPU目标pixel vector。
- `official_submit_frame:866-877` 将CPU pixels转RGBA；`upload_official_rgba:504-511` 保存到pending；`record_official_frame:1220-1258` memcpy至host mapped staging + `vkCmdCopyBufferToImage`，随后 `vkCmdBlitImage` 和present。此路径未创建/提交绘制这些quad的Vulkan graphics pipeline。
- `OfficialGlBridge` ShaderSource/Compile/Link用MARKER，getStatus固定成功；native USE_PROGRAM/ENABLE/BLEND_FUNC/TEX_PARAMETER/MARKER（27..31）直接break。这是面向官方资源的局部软件仿真，不是一般GL shader→Vulkan shader翻译。

状态：**CPU栅格化 + Vulkan上传/呈现的active实现已确认**。不能把native-owned误当GPU-rasterized；NO_API/无真实OpenGL可以成立，但若“PURE_VULKAN”包括用户先前明确的“不以CPU rasterizer作正式GUI renderer”，此更强契约未满足。当前保持官方类/provider不足以证明其全部渲染语义等价。一般GUI/world依然走components/vulkan-renderer的真实GPU路径，不将该缺口扩大成整个Radiance是CPU渲染。

原因证据：迁入Early Window技术基础的结构记录存在；对CPU栅格化作为正式替代的明确接受证据未找到，本轮不自动认定已获接受。需要用户/总协调决策和后续定向实施授权，不在静态审计里修复。文件里的m4 CPU函数另有无Java调用的exports，已经P-03分离；P-09不是把那部分死代码误当active路径。

### P-10 高风险确定缺陷：DLSS 返回值宏发生同名遮蔽

- 当前 `components/vulkan-renderer/src/core/render/modules/world/dlss/dlss_wrapper.cpp:96-100` 的 `NGX_RETURN_ON_FAIL(x)` 宏声明局部 `NVSDK_NGX_Result result = checkNgxResult((x), ...)`，随后检查并返回这个局部 result。
- 当前 `:470-473` 先声明外层 `const NVSDK_NGX_Result result = NGX_VULKAN_EVALUATE_DLSSD_EXT(...)`，再调用 `NGX_RETURN_ON_FAIL(result)`。
- 直接展开会得到内层 `NVSDK_NGX_Result result = checkNgxResult((result), ...)`。C++ 局部名在initializer中已生效，右侧读取的是尚未初始化的内层值，不是外层真实NGX结果。故结果检查和是否继续清 `m_resetPending` 不可靠。这是具体源码错误，不需要用历史警告代替证明。
- 原始 MCVR `9905c81:.../dlss_wrapper.cpp:419-420` 将 evaluate 表达式直接放进宏，不存在该同名输入；MCVR fork `b611d24:.../dlss_wrapper.cpp` 已有错误调用。分类：Initial port内本地行为/错误处理修改引入，不是普通component复制或MC版本命名迁移导致。
- 历史构建记录提到 C4700，只是旁证；本轮未重新编译。现有 `verify_dlss_resource_contract.cmake` 查 m_resetPending/barrier 字符串，不检查宏展开结果，不能由旧CTest PASS否定该缺陷。
- 状态：**已确认局部代码缺陷，动态影响/频繁device-lost因果未确定**。真实NGX evaluate调用在宏前已经发生，此缺陷不自动证明GPU命令非法或驱动崩溃由它造成。建议后续授权下做最小宏/调用修复及返回值回归；本轮不改实现。

总协调已独立回读并确认，记录为 `RAD-C009`。调用者 `dlss_module.cpp:574-578` 只按返回值记 checkpoint，后面仍运行upscale后续pass；所以宏之外的NGX失败输出消费边界亦不能由局部返回码包装宣称完整闭合。

### P-11 WhiteAsh 版本反证（闭合 R-01 的特定待查问题）

不是凭缺同名Mixin下结论。主任务额外读取了两版原生实现：

- 1.21.1：`D:\Workspaces\References\minecraft-references\minecraft-1.21.1\REFERENCE.json` 声明 exact Mojang client/source provenance；读取 `src/net/minecraft/client/particle/{WhiteAshParticle,BaseAshSmokeParticle,TextureSheetParticle,SingleQuadParticle}.java`。
- 1.21.4：现有 `C:\Users\RavenYin\.gradle\caches\fabric-loom\1.21.4\minecraft-client.jar` 的实测 SHA1 `a7e5a6024bfd3cd614625aa05629adf760020304` 与缓存 `mojang_minecraft_info.json` 的client hash/id一致。Yarn `1.21.4+build.8` mappings指定 `gkr=WhiteAshParticle`、`gia=AscendingParticle`（Mojmap BaseAshSmoke）、`gki=SpriteBillboardParticle`、`gjv=BillboardParticle`。使用JDK21 javap只读字节码，不执行类，不构建产品。
- 两版factory均传scale=1；基础quadSize均是 `0.1*(random*.5+.5)*2`，BaseAshSmoke/Ascending再乘 `.75*scale`，age曲线均为clamp32；默认render顶点均为±1，再乘size并旋转平移。
- 因此在这一组准确版本/类链中，没有“1.21.1原生已经应用1/8缩小”这种替代；上游Radiance mixin额外改±1/8且写light=0，当前普通particle.render没有同样分支。可确认**特定上游顶点缩小/光照覆写没有保留**，不是整个WhiteAsh粒子不渲染，也不是视觉验收失败。
- 保持原因未知/用户决策：原始粒子尺寸与上游Radiance风格哪个是本产品目标，不能因审计发现自动恢复。真实贴图覆盖率、PBR粒子物理化之后的最终像素/光照另待验。

证据：外部 `whiteash-input-hashes.json`、`javap-1.21.4-{gkr,gkr$a,gia,gki,gjv}.txt`。另保存过一次无关 `gim`（AbstractDustParticle）的探索输出，不将其当WhiteAsh父类证据。当前NeoForge参考树按这些类名未发现额外patch；未进一步宣称所有第三方能不改它们。

### P-12 辅助 PBR 纹理回收缺口（主审对 C 生命周期候选复核）

- 当前 `AuxiliaryTextures.loadAndUpload:157-185` 为baseID分配specular/normal/flag子ID，调用native allocate/prepare并记base→aux映射。
- `TextureProxy.releaseTextureId:38-45` 先 `TextureTracker.release(base)`；后者仅删除映射条目，不递归/批量release aux。然后JNI只收到baseID/fallbackID。
- `Textures::releaseTexture:58-92` 只擦除该ID的image/sampler/cache；native并不知道base→aux关系。`ResourceReloadCoordinator`/nativebegin/end只维持generation与retained资源安全，不遍历回收已孤立aux。主任务搜索完整Java release调用和native textures/reset/bind路径，未找到其他子ID销毁入口。
- **限定确认**：当有aux的baseID真正被release而renderer继续运行时，旧子ID失去Java所属映射，native `textures_`/`samplers`仍持有资源，不能通过该局部release回收。不是每次F3+T必增长：未release baseID的重载可能复用mapping并重新prepare同一ID。数量/VRAM增长速度未动态测量。
- 上游缺少当前base release实现，故这不是简单“本地把正确上游释放删坏”；是当前新增/扩展局部释放仍未覆盖所有aux所有权。需后续授权补足base→aux退役契约并用重复创建/销毁/资源包变更量化；本轮不修。

归因补核：`cd67362:src/main/java/com/radiance/client/texture/TextureTracker.java` 与 `.../proxy/vulkan/TextureProxy.java` 已含这条只release base的链，`b611d24`已有native单IDrelease。不是当前ordinary组件搬迁引入。`Textures::reset()`会清全体map，但已检查begin/end资源重载不调用它；同IDprepare替换旧images的路径另有retained保活，不等于回收失去base所有权的子ID。

### P-13 范围拒绝与假FBO成功不是完整GL兼容

- 主任务回读 `RenderCaptureContract.classifyBufferDraw`：仅GUI/CAMERA_OVERLAY允许现有native UI；WORLD_STAGE/DIMENSION_EFFECT/OUTSIDE对应draw关闭MeshData并reportDiscard。`RenderLevelStageCompatibilityMixins`和DimensionSpecialEffectsCompatibility确实包住事件/维度callback。
- 特别反例：维度hook返回true会令 `WorldRendererMixins` 跳过其默认sky/cloud/weather分支，但hook内部普通BufferUploader draw会被丢弃；不能将“事件仍发出/方法有返回值”当翻译完成。
- `OpenGlFramebufferCompatibilityMixins` 返回synthetic ID与`GL_FRAMEBUFFER_COMPLETE`，但bind/storage/attach/blit/delete皆丢弃、bound=0。当前这只是避免调用真实GL的空资源兼容边界，不是可用FBO；其注释“不能当成功no-op”与具体complete返回的含义不同。
- 状态：局部行为已确认；任意模组自定义FBO/维度渲染不在已完成兼容集合。上游普通UI draw路径更宽泛，但同样不能由此证明其任意GL正确；本轮不把未知改成已接受取舍。详细C条目与动态观察点在review。

### P-14 截图 HiDPI 反例：继承的尺寸问题，不是映射改名错误

- C的 `ScreenshotRecorderMixins`/`RendererProxy.takeScreenshotWithoutUI` 用 `getScreenWidth/Height` 分配image；准确 `neoforge-21.1.248-sources.jar` 中的 Window 源码表明这些返回logical width/height，另 `getWidth/Height` 才是framebuffer。
- U的ScreenshotRecorder用Yarn `getWidth/Height`。不能据名字相同说它用framebuffer：主任务读1.21.4 build.8 mappings并对已验证官方client做javap，`fey.m/n`=getWidth/Height→q/r(width/height)，`fey.k/l`=getFramebufferWidth/Height→s/t。故上游同样取logical尺寸。
- U native `Framework::takeScreenshot:482,492` 对byte-size不等裸return；C `:662,672` 返回 `VK_ERROR_FORMAT_NOT_SUPPORTED`，Java wrapper再抛异常。两边源图仍是framebuffer extent。条件是logical与framebuffer尺寸不等（或其他format/bytes不等），不是所有Windows缩放配置都必然触发。
- 分类：继承的尺寸不足 + 本地错误显式化；没有证据称这是1.21.4→1.21.1误用映射造成。也不宣称本轮真的拍照失败。
- 外部证据 `screenshot-input-hashes.json` 绑定准确当前依赖sources JAR/mappings；`dependency-Window-1.21.1.java.txt`与`javap-Window-1.21.4.txt`为只读提取。生成依赖本身不属于start source snapshot，所以另行绑定hash，未借旧构建PASS证明语义。

补充证据文件SHA256：`whiteash-input-hashes.json`=`138D004A345E6646C8FCCE9C18AA04DAE1E6CABCD4A835265E5EE8F4145D1637`；`screenshot-input-hashes.json`=`99AAB431DC061FE49EA640D6EDB23C60E69D30FA54F0C4FFDB4544D0721ACC0C`。

### P-15 已见继承的流包装缺口（低优先，未继续扩审）

C曾提示IdentifierInputStream；主任务回读U/current同名类确认两者都仅覆写read()，没有close()转发给originalStream。NamespaceResourceManager的open包装入口存在。因此wrapper本身关闭不会替原stream关闭；哪些底层stream持文件句柄、容器/调用者是否另有生命周期释放未穷尽，不能一概宣称每个资源重载泄漏文件句柄。分类为继承的局部实现不足/后续可核，非本地迁移新增缺失，不作为频繁GPU崩溃原因。本轮不继续扩大普通代码审查。

## 当前目标和运行/发布静态模型

| Target | 当前 authored source | Loader入口/资源 | 产品构建与Maven publication | 客户端场景 |
| --- | --- | --- | --- | --- |
| 1.20.1-forge | 无src | 无 | build脚本仅description；没有产品publication | 无 |
| 1.21.1-neoforge | Java/resources/JVM tests、Early Window native | `@Mod(Dist.CLIENT)`、TOML、Mixin/AT | ModDevGradle2.0.140，真实业务buildAndCollect；仅此节点应用maven-publish | S1、S2、Create、Sable、Aeronautics |
| 26.1.2-neoforge | 无src | 无 | build脚本仅description；没有产品publication | 无 |
| 26.2-fabric | 无src | 无 | build脚本仅description；没有产品publication | 无 |
| 26.2-neoforge | 无src | 无 | build脚本仅description；没有产品publication | 无 |

主任务逐个读取五份build.gradle.kts/target.properties，并检查对应src是否存在；未运行Gradle。根settings显式注册五个描述符；其他四个build脚本没有应用Loader/Java/maven-publish插件，不把它们当空壳产品功能，它们是明确governance-only。当前active源码并非Stonecutter多目标条件重放；旧Stonecutter版本结论不复用。

场景源码输入：`validations/conformance-clean-client/task/profile.json`（无test mods），`compatibility-standard-client/task/profile.json`（CSL15.0.1-Universal、Zume1.2.2，均compatibilityTarget=false/productDependency=false）；三个fixture仍独立。所有profile的userAcceptance是PENDING，不因历史构建结果改为通过。主任务只读profiles，不枚举/修改用户世界或启动客户端。

## 主任务独立复核登记

- P-05/P-09/P-10：主任务直接沿Java→native/构建/消费回读，非转述；总协调分别提供第二视角。
- R-01：回读U `BillboardParticleMixins` 的 WhiteAsh `1/8`顶点、当前粒子capture+Mixin资源；旧分支未找到当前对应，仍记“尚未找到”，不是所有粒子能力缺失。是否应保持这个上游特殊缩小需用户决策，不自动修回。
- R-02：回读U `ClientChunkManagerMixins`的onLightUpdate取消与C `WorldRendererMixins:160-161` light-engine轮询；是callback取消差异，尚无证据称视觉光照缺失/重复。
- R-03：回读U CloudRenderer capture与C CloudProxy调用/资源重载/close，确认有替代，不把缺失Mixin文件判无云。
- U-N08/M-08相关：主任务核两个configs中的priority_background/geometry/composite，以及C `world_miss_eval_impl.glsl:165-184,249` 的程序星空；旧16 shader路径删除并不等于星空/字体能力删光。星空改为环境miss radiance（约3000星的hash分布），不是旧star raster pass，也不是literal BLAS星星；名字/视觉输出不自动等价。
- M-07：主任务与总协调交叉确认alpha_mode/labpbr拓展；它们在b611d24已存在，不能归因本轮ordinary源码copy。详细Java→材质→hit-group链见mapping，仍保留多层透明/随机coverage的动态验证需求。

## 覆盖合并索引

详细输入/状态/输出/来源见 [双上游账本](audit-port-upstream.md)、[当前映射](audit-port-mapping.md)、[反例复核](audit-port-review.md)；本表统一它们的结论，子文档早期“待主代理”以此处已完成的复核范围为准。34个编号是审计单元（跨层有重叠），不是34项互斥功能，也不能将其数量当移植完成率。U-J13/U-J14/U-N17其实是用于反向盘点的本地新增，无对应原始上游能力，不计作原始遗漏。

| 上游/反向单元 | 对应M/R/P | 本轮静态覆盖结论 |
| --- | --- | --- |
| U-J01 启动/提取 | M-02, P-01/04/05/06 | 主库/资源链静态对应；生产早期接线有明确缺口 |
| U-J02 options | U-N02/05, P-03 | 字段/setters/JNI对应；每项选项副作用等价未穷尽 |
| U-J03 pipeline图/持久化 | U-N05, P-03/06 | 配置→JNI→constructor→图像静态对应；所有图组合/错误回退待验 |
| U-J04 shaderpack发现/属性 | U-N07, P-06 | builtin ZIP/运行shaderc路径闭合；任意外部pack兼容未知 |
| U-J05 proxy ABI | M-15, P-03/04 | 80上游声明和当前105声明都有源级对应；实际二进制/指针契约未动态证明 |
| U-J06 chunk capture | M-04/15, R-02, P-07 | API/事件/BLAS接入明确；light callback取消差异与GPU时序仍未知 |
| U-J07 entity/particle/hand等 | M-05/06/08/09/11/15 | 实体基础保留，粒子/优先副本/outline等有主动重写；非上游等价宣称 |
| U-J08 帧/camera/GUI/截图 | P-04/09/14, R-05 | 主帧对应，Early CPU路径与截图条件反例已复核 |
| U-J09 PBR vertex | M-07/10/12, U-N09 | 位域/材质与glyph/coating数据链对应；增加独立材质语义 |
| U-J10 辅助纹理/atlas | M-12, R-04, P-12 | 分配/上传对应，base销毁不释放aux缺口确认 |
| U-J11 mipmap/emission tiles | M-04/07, U-N10 | 准备/提交入口已盘点；所有PBR包/lights采样等价未穷尽 |
| U-J12 pipeline设置GUI | U-J03/04, M-02 | 控件/图编辑类和接线保留；每种鼠标/键盘状态未动态验收 |
| U-J13 第三方compat（本地新增） | M-14, P-13 | 专属hook真实存在，但Veil/Flywheel仍有禁用/fallback；不是完整恢复 |
| U-J14 NeoForge Early（本地新增） | P-04/05/08/09 | 官方类/handle接线保留；CPU栅格化、生产侧载与OS边界需决策 |
| U-J15 三个旧Mixin差异 | R-01/02/03, P-11 | Cloud有替代；WhiteAsh两版本反证已做；light回调差异未定等价 |
| U-J16 静态assets/module资源 | M-13, P-06 | Minecraft资产集合内容对应；post-star属性主动删除，AW→AT非路径同一 |
| U-J17 Java/Loader构建 | M-01/02/03, P-01/02/05 | 单业务NeoForge明确，其他治理无src；无现存Fabric1.21.4业务节点 |
| U-N01 frame/swapchain | P-04/07 | 原架构与当前主链对应，关闭/resize有局部检查；跨queue完整证明欠缺 |
| U-N02 native ABI | P-03 | 返回码wrapper/新增methods/extra exports已分清；不是生成头名字相等即ABI全验 |
| U-N03 staging/BLAS/TLAS | M-04/05/15, P-07 | 输入→batch→AS→SBT已对应；prebuiltBLAS未实现是上游继承 |
| U-N04 resource/device/sync | P-07/12 | 原包装层保留并扩展diagnostics；全部barrier/所有权不变量未逐项证明 |
| U-N05 module graph | P-06, U-N13 | constructors/blueprint/image契约已查；SVGF两边本就未注册 |
| U-N06 GPU UI/post | M-08/11/13, P-06/13 | 主GPUUI存在；旧world-post资源有替代，泛化FBO明确未完成 |
| U-N07 RT/packs | M-15, P-06 | 两个builtin pack/运行compile/dispatch已对应；所有pass数学等价未逐行验证 |
| U-N08 16旧post shader路径 | M-08/09/11/13, P-06 | exact路径删，文字/星空有priority/sky-miss替代；不是16项功能丢失 |
| U-N09 material/shader | M-07/10/12 | transmission/coverage/additive/text/coating为本地算法扩展 |
| U-N10 textures/lights | M-04/12, R-04, P-12 | 上传映射保留；aux回收缺口已确认；灯光全部采样结果未知 |
| U-N11 DLSS | P-03/07/10 | register与evaluate链存在；返回码宏局部缺陷确定，不指认DEVICE_LOST根因 |
| U-N12 FSR/XeSS | P-01/06/08, U-N16 | source/compile flags/注册与artifact路径对应；实际设备支持/输出待验 |
| U-N13 NRD/SVGF/temporal | P-06 | NRD/temporal有效注册；SVGF源存在但基线/当前均不注册，不能计有效移植功能 |
| U-N14 tone/exposure/post | P-06, M-13 | 基本module/shader输入输出对应；全部exposure参数与history分支未证明等价 |
| U-N15 sky/cloud | R-03, M-13, P-06/11复核登记 | CloudProxy及sky-miss替代已找到；星空图样/路径改变，不宣称原版一致 |
| U-N16 CMake/SDK/license | P-01/06/08 | 内部component+固定vendor+CMake适配存在；版本更新非单纯Loader改名 |
| U-N17 CTest/manifest（本地新增） | P-03/06 | 测试源码/注册强度已查；17个旧PASS未当成本轮结论，存在空任务/子串检查局限 |

## 当前新增/改动来源分层

1. **Minecraft/映射适配**：Yarn1.21.4→Mojmap1.21.1，VertexConsumer、SectionCompiler、FontTexture、LevelRenderer等接口与结构不同。代码定位明确；每个方法为何特定改写不总有原因记录，不能把所有算法差异塞进此类。
2. **Fabric→NeoForge**：@Mod/metadata/AT、ClientHooks、render-stage与自定义fluid/dimension入口；EarlyWindow是加载器新增需求。此需求不自动授权或证明某个特定替代实现正确。
3. **产品行为重写**：particles/weather世界几何、text/name-tag priority、ModelPart白色发光outline、自身模型射线mask、coverage/透射拆分、glint/eyes coating、glyph PBR排除、sky miss星空；很多已存在Initial port。历史审计中的设计意图与用户曾要求path tracing化可解释方向，但不是本次验收证据。
4. **兼容专属新增**：Create/Flywheel/Ponder、Sable/Veil、Simulated/Aeronautics及相关桥。compileOnly、reference source、fixture、required test mod必须分开；CSL/Zume不因此升级为产品依赖。
5. **native维护/错误边界扩展**：VkResult wrappers、borrowed-window验证、resource generation、地址诊断、GPU计时、scratch/line/input防护和SDK更新。发现P-10具体新缺陷、P-12释放未闭合；不能笼统称“多余保护”或“已根治”。
6. **工程整合**：Java/JNI namespace、普通component/native库名、Target源码根、vendored依赖、生成/实例边界与门禁。当前dirty这层与Initial port算法改动分开；MCVR siblings不再是构建依赖，但仍可按本轮授权作历史只读来源。

没有足够的逐改动原因记录来给出互斥准确百分比；同一能力同时跨Java/native、版本/Loader/行为多层。此审计提供可追踪项，而不伪造“必要迁移占比”。

## 风险优先级与后续决策

| 优先 | 条目 | 建议，不是本轮实施授权 |
| --- | --- | --- |
| 高 | P-10 DLSS结果宏遮蔽 | 后续最小修复+可控制成功/失败值回归；不要先引入设备恢复 |
| 高 | P-05 生产EarlyWindow链未闭合 | 用户先明确单JAR/侧载安装合同，再补实际消费者，重新做用户态首次启动验收 |
| 高 | P-09 active CPU Early raster | 重新确认强Pure Vulkan含义；若要求GPU栅格化，现实现不能宣称满足 |
| 高 | P-12/R-04 auxiliary所有权 | 按base→aux所有权补资源退役方案；先限定release触发，别将每次reload当漏 |
| 中高 | P-13 未翻译事件/维度/FBO | 对明确compat对象补路径，不将synthetic complete当成功支持；未授权不通用扩写 |
| 中 | P-08 Linux链 | 明确OS支持范围；现有.so分支不是完整Linux支持证明 |
| 中 | P-14/R-05 logical截图尺寸 | 继承问题，后续针对framebuffer!=window条件修；不是错误mapping归因 |
| 待决定 | P-11/R-01、M-06..13 | 判断哪些上游风格要保留，哪些本地PT风格继续；无证据不自动回退 |

## 后续动态验收任务（本轮全部未执行）

前置共同要求：另获启动/测试授权；先绑定新的source seal、准确JAR/native/shader/依赖hash，测试产物必须与之匹配；不借旧实例成功代替当前结果。GUI与视觉通过仍由用户判定。

| 场景 | 特定前置 | 观察点 | 判定边界 |
| --- | --- | --- | --- |
| 普通用户首次启动 | 先解决/明确P-05早期安装合同；干净目录 | 官方early provider/NO_API同handle/资源/主窗可达 | 只有主JAR或明确交付组合真实被加载才证明合同，不是Gradle run成功 |
| DLSS返回码 | 后续修宏并有可控API结果验证 | success/failure不被遮蔽、resetPending规则、失败输出不误用 | 不等于已证明驱动/DEVICE_LOST根因 |
| 资源生命周期 | 同一进程反复创建/销毁带aux基础纹理及资源包变化 | base/aux IDs与活image/sampler数量、generation结束后的释放 | 分清同ID替换vs新ID，未增长/归基线才证明局部回收 |
| 多层材质与文字 | 固定资源包/场景/两内置pack | 玻璃后粒子、半透明背景、glint/eyes、中文glyph、shadow/secondary | 用户视觉判定+实际资源链，不用“全世界几何”口号代替 |
| priority/self/outline | 固定玩家姿态、第三/第一人称、sleep/detached | 玩家本体与光线mask、名牌黑底字、outline及遮挡/发光 | 先固定本地设计合同，不强行原版轮廓等价 |
| GL/维度/FBO | 只加入明确compat fixture | discard日志、callback true后默认渲染是否被取消、offscreen真实attachment | 不崩溃不等于画对；complete返回不算资源存在 |
| resize/minimize/reload | 固定GPU/驱动/DLSS配置 | 尺寸、队列retirement、冻结、history重置/validation | 当前未执行，不推测通过或归因已有dump |
| 截图 | 保证logical与framebuffer真实不同 | 有/无UI图的尺寸/内容/返回码 | 1:1比例通过不能覆盖HiDPI反例 |

## 静态覆盖与功能完整性分开结论

**本轮静态覆盖**：双上游的能力/资源/依赖目录、五Target真实输入、当前反向新增和重点Java→JNI→native→shader→package链已形成编号映射；主任务额外复核了关键缺口、宏错误与两版依赖反证。没有把复制数量、编译、旧记录PASS当语义验证。

**移植功能完整性**：不能宣称完整或与上游等价。Radiance本体存在大量已定位的版本/Loader适配和主动行为重写；MCVR主体/模块被承接，不是整片丢失，但有具体局部缺陷和未证实的时序/材质行为；整合链的开发构建输入静态闭合，但普通用户EarlyWindow交付与CPU栅格化/OS契约仍有实质问题。未知项没有被标成已接受取舍。

明确尚未静态穷尽：全部GUI状态机/每个选项setter副作用、每个原版与第三方renderer分支、所有shader数学/随机采样与buffer layout的逐字节跨GPU证明、全体barrier/queue所有权/NGX内部资源、第三方SDK内部实现、所有OS/硬件组合、基线之后上游新增功能。现有Reference不等于已研究；这些是后续范围，不包装为本轮完成。

## 收尾与恢复点

基线提取、三代理创建、主链复核、三个分账本最终校准与末尾内容/索引封存全部已经做完，不再列为未来步骤。现在停止等总协调静态复核，不启动游戏/修复/提交。恢复研究时以本页ID与源seal定位，先检查source是否漂移，再按上面未穷尽列表或用户批准修复范围继续。完整文档末版哈希另存外部 `audit-document-hashes.json`；不以主文档内嵌自哈希制造递归。

## 结束封存结果

- Radiance `end/source-manifest.json` SHA256 `B525004186A6A5320E5FA5A8C8AEA8328834EFB95DE98A2C6E405E48343FC243`；与start-v2比较10992个选定路径（10009个present、983个deleted tracked）内容变化 **0**，index原文hash不变。结果在外部 `source-drift.json`。
- MCVR `mcvr-end/source-manifest.json` SHA256 `1A8CE75ACAC4816669AEFEFCAF182E7F58E173A2F21E850C82E767CF2F53C9AB`；413个tracked条目中399个普通文件，14个submodule记录按gitlink/index+recursive submodule status核对；普通文件内容变化 **0**、index不变、工作树clean。结果 `mcvr-drift.json`；开/末submodule revision列表一致。本任务不复制或修改其源码。
- Radiance HEAD/branch未变；仍15 staged删除、990 tracked unstaged、9 untracked目录摘要。结束全量status有9998条untracked文件（包括既有大量迁移源码与本轮审计文档），11003条总状态记录；index 1001条。15 staged删除是开场既有 `.gitmodules` +14extern gitlinks，不是本次审计stage。
- 本轮仓库写入仅四份audit-port文档；总协调自己的coordination文档由它维护。审计脚本、source/index封存、精确git archive、JNI声明清单、依赖javap输出与hash证据均在Artifacts。
- 运行实例/缓存/私密配置被明确排除，故不宣称这些被逐字节验证；没有对其实施写入。既有构建/验证记录保留，只作为绑定各自旧快照的辅助。
- 本轮 build=0、产品测试=0、game/GUI=0、stage=0、commit=0、amend=0、push=0。静态声明扫描、javap、文档diff检查是源码研究，不冒充产品测试或用户验收。
