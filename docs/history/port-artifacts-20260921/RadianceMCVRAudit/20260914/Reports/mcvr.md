# MCVR 未提交差异：语义归组审计

对象：`D:\Workspaces\Repositories\GitHub\RecRivenVI\MCVR`，HEAD `9905c81b1999f5845bf66d13501d371c16adf561`（develop）。固定输入是 `Evidence/MCVR/units.json`、`files.json`、`unstaged.patch` 与 `worktree/`；源快照包含 207 个文件、662 个 unit。原始逐 hunk 台账仍在 `Reports/mcvr.json`，本文件对应 `Reports/mcvr-reviewed.json` 的 87 个语义组。

这是只读静态审计。没有构建、CTest、shader compile、游戏启动或 UI 验证；`verification` 的动态状态统一为 PENDING。`explained` 描述的是代码目的是否能由 hunk 和相邻调用静态说明，不代表运行时 PASS。

## 归组统计

|分类|组数|
|---|---:|
|渲染 SDK 与依赖维护|8|
|工程维护/有含义补充类|18|
|版本Loader迁移|3|
|主动调整|21|
|第三方兼容共用基础设施|8|
|上游错误修复(证据不足勿认定)|10|
|原版渲染翻译|13|
|vkWaitForFences排查诊断|5|
|第三方逐模组/共用设施|1|

归组保留两个轴：`原版渲染翻译` 记录 Minecraft/Blaze3D 语义映射，`主动调整` 记录 priority、材质 bit、透明合成、history/reload 等产品/架构选择；`上游错误修复(证据不足勿认定)` 只表示候选本地 hardening。

## 子模块 old pin → new pin

递归快照 `Evidence/MCVR/submodules.txt` 显示 15 个工作树均 clean；10 个 gitlink 更新、5 个保持。

|路径|old|new|状态/版本|提交范围与本地关系|
|---|---|---|---|---|
|`extern/glfw`|`7b6aead9fb88`|`d9d6f0f1f967`|updated; 3.5.1|Start 3.5.1; range contains fractional-scale, Win32 input, Wayland event/size/cleanup and Null Vulkan-window fixes used by borrowed GLFW.|
|`extern/volk`|`416de3f7382a`|`776893306c5d`|updated; vulkan-sdk-1.4.357.0|Advances generated loader update/343 to update/357, paired with the Vulkan 1.4.357 headers.|
|`extern/vma`|`1d8f600fd424`|`3aa921224c15`|updated; commit 3aa9212|Moves from the old Version 3.3.0 commit to the sawickiap/master merge range; observed range is mainly docs/examples/tests, so allocator API compatibility remains pending.|
|`extern/glm`|`2d4c4b4dd31f`|`8d1fd52e5ab5`|updated; 1.0.3|New commit adds the 1.0.3 changelog and includes quaternion, NEON, swizzle, reflection and conversion fixes that can affect transform math.|
|`extern/vulkan_headers`|`49f1a381e2ae`|`e3b1eec08173`|updated; Vulkan-Docs 1.4.357|Advances generated headers from 1.4.343 through 1.4.357 and supplies declarations for local device and diagnostic extensions.|
|`extern/stb`|`f58f558c120e`|`f58f558c120e`|unchanged; commit f58f558|Recursive worktree is clean and the gitlink is unchanged.|
|`extern/DLSS`|`a37a8e482734`|`a291cc7d2cc6`|updated; DLSS 310.7.0|New commit is DLSS 310.7.0; old commit was update header integration. Parent CMake imports the SDK library and installs configuration-specific nvngx DLLs plus LICENSE.txt.|
|`extern/nrd`|`36183520b006`|`792eff196afd`|updated; v4.17.3|Range contains REBLUR inactive-lane robustness, hit-distance cleanup and removal of basecolor-metalness/D; local M-00148/149/151/153 remove matching old API uses.|
|`extern/FidelityFX-SDK`|`d08c34ca8a9d`|`d08c34ca8a9d`|unchanged; v1.1.4-6|Recursive worktree is clean and the gitlink is unchanged.|
|`extern/sharc`|`0b9f58bbc8c4`|`0b9f58bbc8c4`|unchanged; v1.6.5.0|Recursive worktree is clean and the gitlink is unchanged; parent SHARC storage code is separate.|
|`extern/minizip-ng`|`27cdb50b66ad`|`7b2387161c54`|updated; 4.2.2|New commit is Version 4.2.2; range includes symlink-escape rejection, AES-GCM fixes and CMake dependency fixes. Parent CMake pins a bzip2 mirror/commit.|
|`extern/json`|`80af29a39a56`|`55f93686c015`|updated; 3.12.0|New commit is the release/3.12.0 merge with parser/serialization fixes and security/CI maintenance; pin alone proves no renderer behavior.|
|`extern/xess`|`b2e8f705ca9a`|`8fe81bdbbaf0`|updated; XeSS 3.0.2|Advances from XeSS SDK 2.1.1 to 3.0.2; local module hooks reset first-frame state on reload.|
|`extern/tinyexpr`|`e9799be7497f`|`e9799be7497f`|unchanged; heads/master|Recursive worktree is clean and the gitlink is unchanged.|
|`extern/DLSS/NVIDIAImageScaling`|`35e13ba316c9`|`35e13ba316c9`|unchanged; v1.0.3|Nested recursive worktree is clean and the nested gitlink is unchanged.|

`.gitmodules` 的 DLSS URL 从 `Ljiong201108/DLSS.git` 改为 `NVIDIA/DLSS.git`，NRD URL 从 `NVIDIAGameWorks/RayTracingDenoiser.git` 改为 `NVIDIA-RTX/NRD.git`。pin/clean 仍不能替代 SDK/header/lib/DLL/hash/许可证验证。

## 关键状态

- VkResult/lifecycle：M-00056–M-00060、M-00155、M-00180、M-00252、M-00259、M-00282、M-00287、M-00311–M-00312 等将 wait/reset/submit 结果保存并传播，作为有效业务候选单独归组；没有上游 issue 或最小复现，不能宣称上游 bug 已修。
- 诊断探针：M-00124–M-00127、M-00181–M-00184、M-00187、M-00190–M-00191、M-00245、M-00249–M-00252、M-00310、M-00317–M-00324、M-00339–M-00346、M-00367–M-00369、M-00613–M-00614、M-00649 等出现 checkpoint、timestamp/profile、validation、device fault、address binding 或 budget 观测；按要求须全部移除，不得当作 vkWaitForFences 或 core 崩溃根因证据。
- 粒子/世界几何：M-00082、M-00087–M-00088 的 draw/group/emission 与 M-00401、M-00410、M-00481、M-00547、M-00565 等 PARTICLE_MASK 只证明静态路径存在；Java→JNI→build→BLAS/TLAS 和第三方粒子场景仍待验证。
- 半透明后渲染：M-00438–M-00454、M-00527–M-00532 接入 priority background/geometry/composite 和 transparent-only/text hit groups，M-00390–M-00397、M-00455–M-00458、M-00533–M-00536 删除旧 raster shaders；排序、alpha、name-tag 遮挡和 clean staging 仍待验证。
- 发光判定箱/线：M-00071、M-00084–M-00088 过滤无效 line width/zero-alpha separator 并传 emissive；M-00497–M-00498、M-00515–M-00516、M-00630、M-00639 提供 shader radiance/outline 证据；texture lifecycle 与视觉结果仍待验证。
- 缓存/预热：M-00196–M-00203、M-00229–M-00238、M-00325–M-00328、M-00615–M-00617 建立 shader object/pipeline cache 与 rebuild reuse；cache identity、损坏回退、并发和时序仍待验证。
- Early/same window：M-00023–M-00025、M-00371、M-00608–M-00609、M-00618–M-00619 与 M-00036/M-00266 形成 borrowed GLFW_NO_API/loading 路径；单 JAR 官方 SPI/bootstrap、exports、classloader 和普通用户首启仍无动态证据。

## 跨仓库运行时边界

`Evidence/RuntimeLogs/20260914-122757.txt` 的首个 Java 异常是 `LevelRenderer.handler$...veil$setupLevelCamera` 中 VeilRenderer 为 null（约 line 601–602）；之后 HotSpot 报告 `core.dll+0x12558d`（约 line 911–917）并退出。该日志与 MCVR 静态变更只构成跨仓库关联，精确归因未知；不能把它认定为 MCVR 上游 bug，也不能宣称 VkWaitForFences 改动解决了它。Radiance Java/工程报告应继续核对 NeoForge/Veil mixin、JNI 时序和 MCVR native ABI。

## 逐组语义记录

### MCVR-G01-submodule-pins

分类：渲染 SDK 与依赖维护；unit 数：11；explained=true

功能：子模块来源固定

HEAD → 当前：HEAD 只引用旧 gitlink 与旧 DLSS fork URL 与旧 NRD NVIDIA 官方命名空间 URL；当前切到 NVIDIA 官方 URL，并将 10 个 gitlink 固定到新 SHA。必要性是让源码、头文件和 SDK 版本可复现；它不等于 ABI 或许可证已验证。

证据：units M-00001、M-00008–M-00017 直接给出 URL 与 old/new Subproject commit；Evidence/MCVR/submodules.txt 另证 15 个递归工作树 clean。

风险：SDK/依赖提交范围可能改变 API、运行时文件和许可证；仅 pin/clean 不能证明 package 可加载。

建议状态：保留

行动细节：状态：静态来源变更已证实；逐项审阅官方 commit、许可、header/lib/DLL/hash 后再纳入发布。

验证：静态状态：已由固定 hunk 证实；动态状态：PENDING（本审计未构建、未编译 shader、未运行 CTest、未启动游戏/UI）。

关联 unit：M-00001, M-00008, M-00009, M-00010, M-00011, M-00012, M-00013, M-00014, M-00015, M-00016, M-00017

### MCVR-G02-cmake-entry

分类：工程维护/有含义补充类；unit 数：1；explained=true

功能：CMake/CTest入口

HEAD → 当前：HEAD 要求 CMake 3.15 且无 MCVR 测试开关；当前提升到 3.25，加入 include(CTest) 和 MCVR_BUILD_TESTS。必要性是给 native 工程和合同测试一个可控入口。

证据：M-00002 的新增语句明确是 cmake_minimum_required(3.25)、include(CTest)、option(MCVR_BUILD_TESTS)。

风险：过低/过高 CMake 与缓存选项会影响依赖配置；测试开关开启不代表测试执行。

建议状态：保留

行动细节：状态：配置意图已证实；在干净 build 目录配置并记录 CTest 发现结果。

验证：静态状态：已由固定 hunk 证实；动态状态：PENDING（本审计未构建、未编译 shader、未运行 CTest、未启动游戏/UI）。

关联 unit：M-00002

### MCVR-G03-jni-header-root

分类：版本Loader迁移；unit 数：2；explained=true

功能：JNI头目录

HEAD → 当前：HEAD 直接拼接 Java_PROJECT_ROOT_DIR 下的 include 路径；当前建立 MCVR_JNI_INCLUDE_DIR，先检查目录和至少一个生成 .h，再把该目录用于 include。必要性是阻止 native 编译误用缺失/错误 JNI 头。

证据：M-00003 检查目录和 header count，M-00004 把 MCVR_INCLUDE_DIR 改为该变量。

风险：生成头文件名、Java 工程路径和当前 Java 版本仍需与 Radiance Java 侧一致；配置阶段失败会阻断构建。

建议状态：保留

行动细节：状态：静态防护已证实；生成 JNI 后运行 configure，并核对每个 native 声明/导出。

验证：静态状态：已由固定 hunk 证实；动态状态：PENDING（本审计未构建、未编译 shader、未运行 CTest、未启动游戏/UI）。

关联 unit：M-00003, M-00004

### MCVR-G04-dlss-import-packaging

分类：渲染 SDK 与依赖维护；unit 数：1；explained=true

功能：DLSS导入打包

HEAD → 当前：HEAD 用 add_subdirectory(extern/DLSS)；当前要求 nvsdk_ngx_vk.h，创建按配置导入的 ngx 静态目标，并安装对应 nvngx DLL、lib 和 LICENSE.txt。必要性是 SDK 头、导入库与运行时 DLL 走同一来源。

证据：M-00005 明确给出 MCVR_DLSS_ROOT/include/lib、Windows_x86_64/khr/x64、Debug/Release nvngx_*.dll 和 LICENSE.txt。

风险：实际 SDK pin、MSVC runtime 变体、DLL 选择和最终 JAR/运行目录仍可能不一致；Linux 分支也有独立文件约束。

建议状态：保留

行动细节：状态：静态打包链已证实；对 Debug/Release 分别检查真实文件、hash、许可和加载路径。

验证：静态状态：已由固定 hunk 证实；动态状态：PENDING（本审计未构建、未编译 shader、未运行 CTest、未启动游戏/UI）。

关联 unit：M-00005

### MCVR-G05-java21-native-contract

分类：版本Loader迁移；unit 数：1；explained=true

功能：Java版本门槛

HEAD → 当前：同一 CMake hunk 还把 Java 开发包要求从未声明改为 find_package(Java 21 EXACT REQUIRED COMPONENTS Development)。必要性是让生成 JNI 与 native 编译器的 Java ABI 版本显式一致。

证据：M-00005 的新增 find_package(Java 21 EXACT REQUIRED COMPONENTS Development) 是直接证据。

风险：Java 21 精确要求与主工程/运行时 JDK 可能不一致；这只验证配置条件，不验证 classloader 或 DLL ABI。

建议状态：保留

行动细节：状态：版本门槛已证实；在目标 Java 21 环境重新生成头并做 export/加载链检查。

验证：静态状态：已由固定 hunk 证实；动态状态：PENDING（本审计未构建、未编译 shader、未运行 CTest、未启动游戏/UI）。

关联 unit：M-00005

### MCVR-G06-minizip-bzip-pin

分类：渲染 SDK 与依赖维护；unit 数：1；explained=true

功能：bzip2依赖固定

HEAD → 当前：HEAD 没有固定 minizip sanitizer/bzip2 来源；当前关闭 MZ_SANITIZER，并把 bzip2 镜像与 1.0.8 commit 写入 CMake cache。必要性是避免 minizip-ng 依赖漂移和 target mismatch。

证据：M-00006 直接出现 MZ_SANITIZER OFF、BZIP2_REPOSITORY 与 BZIP2_TAG。

风险：关闭 sanitizer 会减少诊断覆盖；镜像和 commit 仍需可访问性、hash 和许可证验证。

建议状态：保留

行动细节：状态：依赖约束已证实；单独记录 bzip2 来源并在 clean configure 中确认实际下载/缓存。

验证：静态状态：已由固定 hunk 证实；动态状态：PENDING（本审计未构建、未编译 shader、未运行 CTest、未启动游戏/UI）。

关联 unit：M-00006

### MCVR-G07-ctest-subdir

分类：工程维护/有含义补充类；unit 数：1；explained=true

功能：CTest子目录

HEAD → 当前：HEAD 在 add_subdirectory(src) 后结束；当前在 MCVR_BUILD_TESTS 时 enable_testing() 并加入 tests。必要性是把 native 合同检查纳入同一工程。

证据：M-00007 的 if(MCVR_BUILD_TESTS)、enable_testing、add_subdirectory(tests) 是直接证据。

风险：测试 source 与生产代码同仓但未执行；开启选项可能改变构建耗时和依赖。

建议状态：保留

行动细节：状态：接线已证实；配置后执行合同测试并把失败与产品代码问题分开。

验证：静态状态：已由固定 hunk 证实；动态状态：PENDING（本审计未构建、未编译 shader、未运行 CTest、未启动游戏/UI）。

关联 unit：M-00007

### MCVR-G08-priority-mask

分类：主动调整；unit 数：1；explained=true

功能：priority可见性位

HEAD → 当前：HEAD 的 mask=4 是 FISHING_BOBBER；当前改成 PRIORITY_MASK，使 priority 几何拥有独立 TLAS visibility bit。必要性是把名称标签/outline 等特殊几何从 fishing-bobber 语义中分离。

证据：M-00018 逐字显示 FISHING_BOBBER_MASK→PRIORITY_MASK；M-00438 以后配置和 M-00634 以后 shader 使用 priority 路径。

风险：旧 Java/native mask 仍需同步；bit 冲突或某一 ray stage 漏 mask 会造成遮挡/命中错误。

建议状态：保留

行动细节：状态：位布局变化已证实；联合核对 Java mask、BLAS instance mask、primary/secondary/shadow rays。

验证：静态状态：已由固定 hunk 证实；动态状态：PENDING（本审计未构建、未编译 shader、未运行 CTest、未启动游戏/UI）。

关联 unit：M-00018

### MCVR-G09-ubo-abi-fields

分类：主动调整；unit 数：4；explained=true

功能：UBO/ABI字段

HEAD → 当前：HEAD 把 MaterialVertex/WorldUBO 的保留 padding 当作无意义字节；当前复用槽传递 emissiveOverlayTextureID、diagram 参数、glintStrength、starBrightness，同时声明 starBrightness 仍保持 80-byte UBO ABI。必要性是向 shader 传新语义而不扩张既有 JNI/UBO 布局。

证据：M-00019–M-00022 直接显示 padding 替换、diagramRect/颜色/params 与 starBrightness 注释；后续 M-00630、M-00639、M-00497 等读取这些字段。

风险：复用 padding 依赖 Java/native/shader 三端布局完全一致；错误 offset 会是静默图像损坏。

建议状态：保留

行动细节：状态：静态 ABI 意图已证实；对生成 JNI、sizeof/static_assert、SPIR-V reflection 做一致性验证。

验证：静态状态：已由固定 hunk 证实；动态状态：PENDING（本审计未构建、未编译 shader、未运行 CTest、未启动游戏/UI）。

关联 unit：M-00019, M-00020, M-00021, M-00022

### MCVR-G10-borrowed-glfw-loader

分类：第三方兼容共用基础设施；unit 数：3；explained=true

功能：借用GLFW加载

HEAD → 当前：HEAD 只声明基本 GLFW 函数；当前增加 GetWindowAttrib、WaitEventsTimeout、WindowShouldClose 的动态指针、extern 和初始化。必要性是让 MCVR 在不再创建第二个 GLFW 的 borrowed-window 路径中检查窗口类型、处理零 drawable 和关闭。

证据：M-00023–M-00025 给出三个 PFN typedef、全局指针、宏和 nullptr 初始化；M-00371 使用 GLFW_CLIENT_API/GLFW_NO_API。

风险：函数指针未绑定、版本符号缺失或误加载另一个 GLFW 会在早期窗口阶段崩溃；静态声明不证明实际模块句柄。

建议状态：保留

行动细节：状态：loader 契约已证实；在真实 LWJGL handle 上核对符号、同窗口指针和关闭/resize 时序。

验证：静态状态：已由固定 hunk 证实；动态状态：PENDING（本审计未构建、未编译 shader、未运行 CTest、未启动游戏/UI）。

关联 unit：M-00023, M-00024, M-00025

### MCVR-G11-jni-option-pipeline

分类：第三方兼容共用基础设施；unit 数：2；explained=true

功能：JNI选项与pipeline

HEAD → 当前：HEAD 的 VSync setter 直接写字段并只在 write 时 needRecreate，pipeline build 也让异常穿过 JNI；当前只在值变化时置 presentationChanged，并用 invokeVoid 将 C++ 异常转 Java。必要性是适配 early renderer 已建 swapchain 且阻止 JNI 边界未处理异常。

证据：M-00026 的 presentationChanged 条件与 M-00027 的 jni::invokeVoid/Build world pipeline 字符串是直接证据。

风险：Java 侧是否在正确线程调用、异常后 renderer 是否仍可关闭、early swapchain 是否重建均未证实。

建议状态：保留

行动细节：状态：入口逻辑已解释；核对 Java caller、线程约束和失败后可恢复性。

验证：静态状态：已由固定 hunk 证实；动态状态：PENDING（本审计未构建、未编译 shader、未运行 CTest、未启动游戏/UI）。

关联 unit：M-00026, M-00027

### MCVR-G12-buffer-jni-and-diagram-upload

分类：第三方兼容共用基础设施；unit 数：3；explained=true

功能：buffer/diagram JNI

HEAD → 当前：HEAD 的 buffer JNI 入口没有统一异常路径且没有 diagram uniform 上传；当前用 invokeVoid 包裹初始化/索引和新增 updateDiagramPostUniform，连接 UI diagram UBO。必要性是让 Java overlay state 进入 Vulkan buffer。

证据：M-00028–M-00030 显示 jni_exception include、buffer lambda 与 Upload diagram post-process uniform。

风险：pointer 生命周期、uniform 对齐和 UI context 当前帧选择仍需 runtime 验证。

建议状态：保留

行动细节：状态：静态入口已证实；与 DiagramState、UIModule frame index 和 UBO reflection 一起验证。

验证：静态状态：已由固定 hunk 证实；动态状态：PENDING（本审计未构建、未编译 shader、未运行 CTest、未启动游戏/UI）。

关联 unit：M-00028, M-00029, M-00030

### MCVR-G13-renderer-jni-early-entry

分类：第三方兼容共用基础设施；unit 数：8；explained=true

功能：renderer JNI入口

HEAD → 当前：HEAD 的 renderer init 在缺符号时 abort，初始化/截图返回 void；当前缺符号改为异常，加入 bindLoadedGlfw，initRendererNative、backendString、takeScreenshotNative 等返回 VkResult/Java 异常并暴露 GPU profile。必要性是让 native 失败可回到 Java，并复用已加载窗口。

证据：M-00031–M-00038 逐步加入 loading_renderer.hpp、throw runtime_error、GLFW symbols、initRendererNative/backendString/takeScreenshotNative。

风险：返回值被 Java 正确消费、backend query 的枚举映射、截图 buffer 生命周期和 单 JAR 官方 SPI/bootstrap/classloader 都未验证。

建议状态：保留

行动细节：状态：JNI 机制已证实；核对生成头/exports/Java caller，再做受控初始化和截图链路。

验证：静态状态：已由固定 hunk 证实；动态状态：PENDING（本审计未构建、未编译 shader、未运行 CTest、未启动游戏/UI）。

关联 unit：M-00031, M-00032, M-00033, M-00034, M-00035, M-00036, M-00037, M-00038

### MCVR-G14-shader-texture-jni

分类：第三方兼容共用基础设施；unit 数：5；explained=true

功能：shader/texture JNI

HEAD → 当前：HEAD 的 shader/texture JNI 入口多处静默 return 或直接访问 renderer；当前统一 invokeVoid、异常和 ID/queue upload/download 调用。必要性是把 resource failure 传回 Java 而不是留下无声半初始化。

证据：M-00039–M-00043 出现 Register Vulkan shader、Allocate Vulkan texture ID、Queue Vulkan texture upload、Upload Vulkan textures 的包装。

风险：异常可能在 Java render thread 之外抛出；ID 重用、队列 flush 与资源 reload 锁顺序仍需验证。

建议状态：保留

行动细节：状态：入口包装已证实；联合核对 Java synchronized contract、纹理状态机和错误关闭路径。

验证：静态状态：已由固定 hunk 证实；动态状态：PENDING（本审计未构建、未编译 shader、未运行 CTest、未启动游戏/UI）。

关联 unit：M-00039, M-00040, M-00041, M-00042, M-00043

### MCVR-G15-world-jni-queue

分类：第三方兼容共用基础设施；unit 数：5；explained=true

功能：world queue JNI

HEAD → 当前：HEAD 的 chunk/entity JNI 入口直接进入 World；当前在 reset/rebuild/queueBuild 外包 JNI 异常，新增 collectEmission 参数进入 ChunkBuildTask。必要性是保持 Java→native world queue 的参数和失败语义可追踪。

证据：M-00044–M-00048 直接显示 Initialize chunk storage、Queue chunk rebuild、Queue entity geometry build 及 jni::invokeVoid。

风险：Java buffer pointer、队列线程、chunk id 范围和 entity 顶点所有权未动态验证。

建议状态：保留

行动细节：状态：调用链意图已证实；核对生成 JNI 签名、线程与释放时序。

验证：静态状态：已由固定 hunk 证实；动态状态：PENDING（本审计未构建、未编译 shader、未运行 CTest、未启动游戏/UI）。

关联 unit：M-00044, M-00045, M-00046, M-00047, M-00048

### MCVR-G16-buffer-history-and-index

分类：主动调整；unit 数：7；explained=true

功能：buffer history与索引

HEAD → 当前：HEAD 用局部 static lastUBO 且四顶点索引逻辑内嵌；当前抽出 LINES/QUADS helper，改成按 frame 的 lastWorldUbo_ 与 atomic worldHistoryValid，并提供 invalidate。必要性是让 temporal history 与新窗口/rebuild 生命周期可控。

证据：M-00049–M-00055 显示 index_patterns.hpp、FourVertexIndexPattern、lastWorldUbo_、worldHistoryValid_ 和 invalidateWorldHistory。

风险：history 初始值、frame index 并发和 Minecraft LINES/QUADS 拓扑必须保持一致；错误会影响运动矢量或 UI 几何。

建议状态：保留

行动细节：状态：状态机意图已证实；做多帧、resize/reload 和 index count contract 验证。

验证：静态状态：已由固定 hunk 证实；动态状态：PENDING（本审计未构建、未编译 shader、未运行 CTest、未启动游戏/UI）。

关联 unit：M-00049, M-00050, M-00051, M-00052, M-00053, M-00054, M-00055

### MCVR-G17-chunk-fence-result

分类：上游错误修复(证据不足勿认定)；unit 数：8；explained=true

功能：chunk fence结果

HEAD → 当前：HEAD 丢弃 chunk poll/wait/reset/submit 的 VkResult，并在 reset 中直接 vkQueueWaitIdle；当前保存 waitResult/submitResult、区分 SUCCESS/TIMEOUT/NOT_READY、在失败时 recordFailure，并用 framework queue-idle。必要性是把 fence 错误和队列失败传播而非静默继续。

证据：M-00056–M-00063 直接出现 vkWaitForFences、vkResetFences、vkQueueSubmit 结果变量、timeout 分支和 recordFailure。

风险：这是本地候选 hardening；没有上游 issue/最小复现，不能认定 HEAD 是上游 bug；错误路径是否避免死锁未运行验证。

建议状态：待修复

行动细节：状态：候选错误传播已静态证实；查上游提交/复现并单独测试失败注入，和诊断日志拆开。

验证：静态状态：已由固定 hunk 证实；动态状态：PENDING（本审计未构建、未编译 shader、未运行 CTest、未启动游戏/UI）。

关联 unit：M-00056, M-00057, M-00058, M-00059, M-00060, M-00061, M-00062, M-00063

### MCVR-G18-external-chunk-transform

分类：主动调整；unit 数：6；explained=true

功能：外部chunk变换

HEAD → 当前：HEAD 没有 external chunk API，所有 chunk 默认为主世界；当前可分配/更新/释放 external chunk，保存 customTransform 与 primaryChunkCount。必要性是让跨模组或辅助世界几何进入 TLAS，同时区分主 chunk 数量。

证据：M-00064、M-00067、M-00068、M-00069 明确出现 allocateExternalChunk/updateExternalChunkTransform/releaseExternalChunk/customTransform/primaryChunkCount。

风险：外部 chunk id、transform 生命周期和 frame resource ownership 未动态证明；误把外部几何计入主世界会破坏 rebuild。

建议状态：保留

行动细节：状态：API 设计已解释；核对 Java caller、TLAS instance、释放后引用和 motion history。

验证：静态状态：已由固定 hunk 证实；动态状态：PENDING（本审计未构建、未编译 shader、未运行 CTest、未启动游戏/UI）。

关联 unit：M-00064, M-00065, M-00066, M-00067, M-00068, M-00069

### MCVR-G19-entity-batch-semantics

分类：原版渲染翻译；unit 数：12；explained=true

功能：entity batch语义

HEAD → 当前：HEAD 对 entity build 的 name/line/emission 分类和空 batch 防护不足；当前加入 NAME_TAG_SEE_THROUGH、一次性无效 line 记录、emissive overlay ID 传递、空几何清理和有限距离检查。必要性是把 Minecraft draw metadata 转成稳定 RT build 输入。

证据：M-00071–M-00081 的新增 case、logInvalidLineWidthOnce、emissiveOverlayTextureIDs、clearBatch、totalGeometryCount 检查和 finite distance 是具体证据。

风险：内容名来自 mod/Java；分类字符串误配会改变材质、发光或 name-tag 路径；log 仍是本地诊断残留。

建议状态：保留

行动细节：状态：静态分支意图已证实；用 vanilla 与第三方 entity 内容逐项验证并移除本地日志。

验证：静态状态：已由固定 hunk 证实；动态状态：PENDING（本审计未构建、未编译 shader、未运行 CTest、未启动游戏/UI）。

关联 unit：M-00070, M-00071, M-00072, M-00073, M-00074, M-00075, M-00076, M-00077, M-00078, M-00079, M-00080, M-00081

### MCVR-G20-entity-lines-emissive-priority

分类：原版渲染翻译；unit 数：7；explained=true

功能：entity线与发光

HEAD → 当前：HEAD 的 geometry build 只按既有模式生成数据；当前补 TRIANGLES/DEBUG_LINES/LINE_STRIP、无效 line width 过滤、零 alpha 分隔跳过，并把 emissiveDebug/semanticEmission 写为 albedoEmission，再把 overlay texture IDs 送入 batch。必要性是避免把 OpenGL 的线分隔错误地变成不透明 RT 棱柱。

证据：M-00082–M-00088 明确出现 draw mode、lineWidth finite 检查、zero-alpha separator、albedoEmission=1.0 和 composeEmissiveEyeOverlays。

风险：线宽/透明分隔的视觉等价仍需场景验证；emission 乘法与 overlay resource release 可能产生能量或泄漏问题。

建议状态：保留

行动细节：状态：几何翻译已静态证实；执行线框、眼睛、beacon/lightning/dragon-ray 场景验证。

验证：静态状态：已由固定 hunk 证实；动态状态：PENDING（本审计未构建、未编译 shader、未运行 CTest、未启动游戏/UI）。

关联 unit：M-00082, M-00083, M-00084, M-00085, M-00086, M-00087, M-00088

### MCVR-G21-entity-build-data

分类：工程维护/有含义补充类；unit 数：2；explained=true

功能：entity build数据

HEAD → 当前：HEAD 的 EntityBuildData 只保存 vertices/indices；当前携带每组 emissiveOverlayTextureIDs，使 native batch 与 shader MaterialVertex 新槽一致。必要性是完成数据从 entity 分类到 GPU cache 的闭环。

证据：M-00089–M-00090 的 struct 字段和构造函数参数直接对应 M-00073/M-00078。

风险：vector 长度、move 后使用和 reload 时 texture ID 失效未验证。

建议状态：保留

行动细节：状态：数据契约已解释；用大小/长度断言和资源 reload 场景验证。

验证：静态状态：已由固定 hunk 证实；动态状态：PENDING（本审计未构建、未编译 shader、未运行 CTest、未启动游戏/UI）。

关联 unit：M-00089, M-00090

### MCVR-G22-ui-overlay-init

分类：原版渲染翻译；unit 数：15；explained=true

功能：UI overlay资源

HEAD → 当前：HEAD 只初始化基础 overlay；当前增加 UI resize、diagram draw color/depth render pass、overlay post 与 spider blur image/framebuffer/descriptor/pipeline，并把 LINES 映射为稳定 triangle list。必要性是把 Minecraft 实体效果和诊断图层放进 Vulkan overlay。

证据：M-00091–M-00105 直接出现 initDiagramDraw*、initSpiderBlur*、descriptor binding 2/3、render pass、overlay pipeline shader 文件和 LINES 注释。

风险：附件 layout、swapchain image 数量、AMD/非 AMD layout 分支及 blur 采样顺序仍需运行时验证。

建议状态：保留

行动细节：状态：UI 资源拓扑已证实；逐 effect 编译并做 resize/HiDPI/overlay 场景验证。

验证：静态状态：已由固定 hunk 证实；动态状态：PENDING（本审计未构建、未编译 shader、未运行 CTest、未启动游戏/UI）。

关联 unit：M-00091, M-00092, M-00093, M-00094, M-00095, M-00096, M-00097, M-00098, M-00099, M-00100, M-00101, M-00102, M-00103, M-00104, M-00105

### MCVR-G23-ui-context-diagram

分类：原版渲染翻译；unit 数：9；explained=true

功能：UI context/diagram

HEAD → 当前：HEAD 的 UIModuleContext 只保存旧 overlay state；当前保存 diagram/spider 资源、跨 resize 的 persistent state，新增 beginDiagram/postDiagram/postOverlay，并在 end 时结束 diagram render pass。必要性是让同一 frame 的 UI 状态和附件布局可恢复。

证据：M-00106–M-00114 显示新增 context image/framebuffer、copyPersistentStateFrom、beginDiagram、postOverlay、lastActiveContext 和 DIAGRAM_DRAW end 分支。

风险：context 生命周期、render pass 嵌套和异常退出清理未动态验证；diagram 状态可能污染普通 overlay。

建议状态：保留

行动细节：状态：上下文状态机已解释；验证 begin/end 嵌套、resize、异常和同窗口 early overlay。

验证：静态状态：已由固定 hunk 证实；动态状态：PENDING（本审计未构建、未编译 shader、未运行 CTest、未启动游戏/UI）。

关联 unit：M-00106, M-00107, M-00108, M-00109, M-00110, M-00111, M-00112, M-00113, M-00114

### MCVR-G24-ui-declarations

分类：原版渲染翻译；unit 数：7；explained=true

功能：UI接口声明

HEAD → 当前：HEAD 没有 diagram/effect 类型和资源成员；当前枚举 DIAGRAM/CREEPER/SPIDER/INVERT/BLUR，声明 diagram attachments 与 post helpers。必要性是让 Java DiagramState 和 overlay shader 有稳定 native API。

证据：M-00115–M-00121 直接列出类型、resize/init 方法、资源字段以及 postEntityEffect/postSpider/postOverlay。

风险：声明与 JNI 生成头、shader descriptor set 和 Java enum 必须一致；静态声明不证明调用顺序。

建议状态：保留

行动细节：状态：接口形状已证实；生成头并对 Java native 方法/descriptor 做矩阵核对。

验证：静态状态：已由固定 hunk 证实；动态状态：PENDING（本审计未构建、未编译 shader、未运行 CTest、未启动游戏/UI）。

关联 unit：M-00115, M-00116, M-00117, M-00118, M-00119, M-00120, M-00121

### MCVR-G25-dlss-resource-generation

分类：渲染 SDK 与依赖维护；unit 数：22；explained=true

功能：DLSS资源代际

HEAD → 当前：HEAD 的 DLSS applicationPath 是 string，资源尺寸/历史 reset/输入 barrier 与 SDK feature path 较弱；当前使用 filesystem path、官方 feature search path、按 frame setResource、尺寸检查、resetPending、named image handles，并在 reload/deinit 时请求 history reset。必要性是把 DLSS 310.7.0 的资源和窗口 generation 绑定。

证据：M-00122–M-00143 具体给出 dlssPath、featureSearchPaths_、requestHistoryReset、setResource(width/height/frameIndex)、m_namedImages、InReset 和命名资源表。

风险：NGX SDK 行为、Unicode path、资源 layout、frame index 和 `NGX_RETURN_ON_FAIL` 宏展开仍需编译/runtime；旧配置与新官方 SDK ABI 可能不兼容。

建议状态：保留

行动细节：状态：DLSS 资源意图已证实；核对 SDK headers/import/runtime DLL、hash、每资源 view/layout 和失败回退。

验证：静态状态：已由固定 hunk 证实；动态状态：PENDING（本审计未构建、未编译 shader、未运行 CTest、未启动游戏/UI）。

关联 unit：M-00122, M-00123, M-00124, M-00125, M-00126, M-00127, M-00128, M-00129, M-00130, M-00131, M-00132, M-00133, M-00134, M-00135, M-00136, M-00137, M-00138, M-00139, M-00140, M-00141, M-00142, M-00143

### MCVR-G26-fsr-reload

分类：渲染 SDK 与依赖维护；unit 数：4；explained=true

功能：FSR reload

HEAD → 当前：HEAD 在 FSR3 destroy 中无条件两次 vkDeviceWaitIdle，module 没有 reload hook；当前记录 idle 错误并在 reload 后 firstFrame_=true。必要性是让资源重建不会继续消费旧历史，同时让 wait 失败可见。

证据：M-00144–M-00147 直接显示 Renderer/framework recordFailure 和 onResourceReload/firstFrame_。

风险：是否可在 device lost 时安全析构、first frame 的输出质量和 FSR SDK 资源契约未验证；无上游复现不能称 bug 修复。

建议状态：保留

行动细节：状态：本地资源策略已解释；验证 FSR3 destroy/reload/device-failure 注入，并审阅 SDK 版本。

验证：静态状态：已由固定 hunk 证实；动态状态：PENDING（本审计未构建、未编译 shader、未运行 CTest、未启动游戏/UI）。

关联 unit：M-00144, M-00145, M-00146, M-00147

### MCVR-G27-nrd-api-reload

分类：渲染 SDK 与依赖维护；unit 数：9；explained=true

功能：NRD接口与reload

HEAD → 当前：HEAD 代码按当时旧 NRD pin/接口形状访问 hitDistanceParameters.D 与 basecolor-metalness；当前切到 NRD v4.17.3 后，按新 SDK 已移除的 API 清理本地引用，按 GLSL compatibility 传 xyz，reload 时清历史并检查 queue submit/wait，compute pipeline 走 Device wrapper。必要性是完成依赖迁移，不表示旧 pin 当时非法。

证据：M-00148–M-00156 直接显示删除 D/isBaseColorMetalnessAvailable、onResourceReload、recordFailure、Device::createComputePipelines；Evidence/mcvr-audit/submodules.json 记录 NRD old→new pin。

风险：NRD 子模块 pin 与本地 shader compatibility 必须完全匹配；历史清零时序和 driver 行为未运行验证。

建议状态：保留

行动细节：状态：SDK API 对齐意图已证实；以 NRD v4.17.3 headers/shaders 做 compile、resource map 和 scene 验证。

验证：静态状态：已由固定 hunk 证实；动态状态：PENDING（本审计未构建、未编译 shader、未运行 CTest、未启动游戏/UI）。

关联 unit：M-00148, M-00149, M-00150, M-00151, M-00152, M-00153, M-00154, M-00155, M-00156

### MCVR-G28-post-render-replacement

分类：主动调整；unit 数：21；explained=true

功能：post-render替代

HEAD → 当前：HEAD 的 post_render 解析仍有 name_tag/star targets、随机粒子 buffer 和固定 out:ldr/depth assumptions；当前只保留 Text target，按实际 color target/深度附件建立 dynamic pass，并让 priority RT/composite 接替粒子、天气、文字、name-tag、star。必要性是取消多个不再消费的 raster pass，避免旧 SPIR-V/附件残留。

证据：M-00158–M-00174 具体显示删除 target/随机 buffer、colorTarget/usesDepthAttachment、extent 检查和 per-pass barriers；M-00175–M-00177 删除 nameTag/star fields。

风险：这是主动改变渲染顺序的高风险替换；透明排序、first-hit-depth、名称标签遮挡和旧资源消费必须运行验证。

建议状态：待重构

行动细节：状态：替代拓扑已静态证实；先验证 config 引用和 clean staging，再做粒子/文字/star/name-tag 场景回归。

验证：静态状态：已由固定 hunk 证实；动态状态：PENDING（本审计未构建、未编译 shader、未运行 CTest、未启动游戏/UI）。

关联 unit：M-00157, M-00158, M-00159, M-00160, M-00161, M-00162, M-00163, M-00164, M-00165, M-00166, M-00167, M-00168, M-00169, M-00170, M-00171, M-00172, M-00173, M-00174, M-00175, M-00176, M-00177

### MCVR-G29-sharc-clear-and-sbt

分类：上游错误修复(证据不足勿认定)；unit 数：9；explained=true

功能：SHARC清理与SBT

HEAD → 当前：HEAD 在 initSharc 中内联 clear command，SBT upload 丢弃 submit/wait 结果；当前抽出 clearSharcStorage，检查 runtime/buffers，SBT submit 返回 VkResult，并加入 ray pass checkpoint。必要性是把 SHARC storage 生命周期和失败传播分开。

证据：M-00178–M-00186 显示 clearSharcStorage、hasSharcRuntime_ guard、submitMainQueueIndividual result/recordFailure 和 ray/world-prepare/sharc/pass checkpoints。

风险：checkpoint 是待移除本地探针；clear/SBT 的错误传播是候选 hardening，没有上游 issue/复现不能认定上游 bug。

建议状态：待修复

行动细节：状态：有效清理/传播与探针已拆分；保留审查前者，发布前删除后者并做 SHARC 重建验证。

验证：静态状态：已由固定 hunk 证实；动态状态：PENDING（本审计未构建、未编译 shader、未运行 CTest、未启动游戏/UI）。

关联 unit：M-00178, M-00179, M-00180, M-00181, M-00182, M-00183, M-00184, M-00185, M-00186

### MCVR-G30-world-prepare-transform-thread

分类：主动调整；unit 数：8；explained=true

功能：world prepare变换

HEAD → 当前：HEAD 每帧把 chunk 位置硬编码成 identity transform，且没有 previous transform queue；当前保存 previousChunkTransformBatches_、对 customTransform 只暴露有效 device addresses，并用 recursive mutex 保护 transform history，再构造 TLAS/metadata。必要性是支持 external chunk motion 与跨帧 motion vector。

证据：M-00187–M-00194 直接出现 stage checkpoints、chunkTransformBatchesMtx_、previousChunkTransformBatches_、hasCustomTransform 分支和 previousObjectToWorld。

风险：shared_ptr owner_less map、render thread 与 build thread 的锁顺序及前一帧缺失处理未验证；checkpoint 仍需移除。

建议状态：保留

行动细节：状态：线程/变换机制已解释；做并发 reload、chunk relocate、首帧/释放和 motion scene 验证。

验证：静态状态：已由固定 hunk 证实；动态状态：PENDING（本审计未构建、未编译 shader、未运行 CTest、未启动游戏/UI）。

关联 unit：M-00187, M-00188, M-00189, M-00190, M-00191, M-00192, M-00193, M-00194

### MCVR-G31-shader-object-cache

分类：主动调整；unit 数：9；explained=true

功能：shader object cache

HEAD → 当前：HEAD 已有 ShaderSpirvCache::computeDependencyHash/readCachedSpirv/writeCachedSpirv/compileGlslToSpv(cacheDir) 的磁盘 SPIR-V 缓存，但每次 ShaderPack request 仍可能重建 VkShader 对象并重复扫描/进入编译流程；当前新增 shaderObjectCache、restartRuntime、按 source/definitions/stage/device 形成 key 的请求去重，并让 pipeline 创建使用独立 VkPipelineCache。必要性是减少同一 generation 的对象重复构造并更好利用已有磁盘缓存。

证据：M-00196–M-00203 直接出现 shaderObjectCacheMutex_、shaderObjectCacheKey、missRequestIndices、uniqueShaders、objectReuseCount；HEAD 的 ShaderSpirvCache 符号在相邻 shader.cpp 基线中已查到。

风险：cache identity 若漏 include/driver/defines 会复用错误 SPIR-V；并发 shader creation、cache 清理和失败回退未运行验证。

建议状态：保留

行动细节：状态：缓存/预热意图已证实；验证 key 完整性、失败回退、device generation 和实际 compile timing。

验证：静态状态：已由固定 hunk 证实；动态状态：PENDING（本审计未构建、未编译 shader、未运行 CTest、未启动游戏/UI）。

关联 unit：M-00195, M-00196, M-00197, M-00198, M-00199, M-00200, M-00201, M-00202, M-00203

### MCVR-G32-shaderpack-renderpass-schema

分类：工程维护/有含义补充类；unit 数：7；explained=true

功能：shaderpack renderpass

HEAD → 当前：HEAD RenderPass target 枚举含 NameTag/Star，shader pack 无 object cache/restart 声明；当前改为 colorTarget/usesDepthAttachment，并声明 cache/restart APIs。必要性是让 config 的实际 output 与 runtime pipeline 一致。

证据：M-00204–M-00210 直接列出 mutex、移除 NameTag/Star、colorTarget/usesDepthAttachment、shaderObjectCacheKey/restartRuntime。

风险：配置与 native enum 不一致会在 dynamic pipeline 构建时失败；仅声明不代表所有调用点已更新。

建议状态：保留

行动细节：状态：结构契约已解释；对 configs、resource targets 和 generated staging 做完整引用检查。

验证：静态状态：已由固定 hunk 证实；动态状态：PENDING（本审计未构建、未编译 shader、未运行 CTest、未启动游戏/UI）。

关联 unit：M-00204, M-00205, M-00206, M-00207, M-00208, M-00209, M-00210

### MCVR-G33-temporal-history-reset

分类：主动调整；unit 数：6；explained=true

功能：temporal history

HEAD → 当前：HEAD temporal module 直接用旧 alpha/history，SVGF pipeline 用 raw vkCreate；当前在 reload/重建后设置 resetHistoryPending_，首帧直接输出 current color/normal，并把 compute pipeline 走 Device wrapper。必要性是避免新 image 采到未定义旧 history。

证据：M-00211–M-00216 与 M-00601–M-00602 共同给出 createComputePipelines、onResourceReload、resetHistoryPending_ 和 alpha>=1 首帧旁路。

风险：首帧闪烁、normal fallback、history 与 swapchain generation 绑定未运行验证；Device wrapper 失败仍需正确析构。

建议状态：保留

行动细节：状态：history 策略已证实；验证 resize/reload、首帧和多帧收敛。

验证：静态状态：已由固定 hunk 证实；动态状态：PENDING（本审计未构建、未编译 shader、未运行 CTest、未启动游戏/UI）。

关联 unit：M-00211, M-00212, M-00213, M-00214, M-00215, M-00216

### MCVR-G34-exposure-rebuild

分类：主动调整；unit 数：7；explained=true

功能：exposure rebuild

HEAD → 当前：HEAD 每次重建 exposure buffer 并把两段 padding 当无效状态；当前保存/恢复 ExposureRebuildState、historyValid、initialized/firstExposureFrame，首帧 fill buffer 并保持旧 exposure。必要性是 GPU-idle rebuild 后不把曝光历史重置为垃圾。

证据：M-00217–M-00223 直接出现 captureRebuildState/restoreRebuildState、historyValid、static_assert(16)、exposureInitialized_ 和 firstExposureFrame_。

风险：跨 device/extent 恢复条件、GPU/CPU 并发和 first frame 的视觉过渡未验证。

建议状态：保留

行动细节：状态：rebuild state 语义已证实；做 device generation、resize、reload 和曝光突变场景验证。

验证：静态状态：已由固定 hunk 证实；动态状态：PENDING（本审计未构建、未编译 shader、未运行 CTest、未启动游戏/UI）。

关联 unit：M-00217, M-00218, M-00219, M-00220, M-00221, M-00222, M-00223

### MCVR-G35-worldmodule-reload-xess

分类：渲染 SDK 与依赖维护；unit 数：4；explained=true

功能：module reload/XeSS

HEAD → 当前：HEAD WorldModule 没有统一 reload/rebuild hook，XeSS module 也不重置 firstFrame；当前提供 WorldModuleRebuildState/onResourceReload/capture/restore 默认接口，并让 XeSS reload 后首帧复位。必要性是让各 upscaler/denoiser 在同一资源 generation 边界处理历史。

证据：M-00224–M-00227 直接出现接口和 XessSrModule::onResourceReload/firstFrame_=true。

风险：默认空实现可能让新增 module 忘记清历史；各 module 的 state 尺寸校验仍需独立验证。

建议状态：保留

行动细节：状态：共用生命周期接口已证实；逐 module 列出 reset/restore policy 并做 rebuild matrix。

验证：静态状态：已由固定 hunk 证实；动态状态：PENDING（本审计未构建、未编译 shader、未运行 CTest、未启动游戏/UI）。

关联 unit：M-00224, M-00225, M-00226, M-00227

### MCVR-G36-pipeline-stage-markers

分类：vkWaitForFences排查诊断；unit 数：9；explained=true

功能：pipeline阶段探针

HEAD → 当前：HEAD world pipeline init/render loop 没有阶段计时或 checkpoint；当前记录 world module begin/done、shader/config/resource build timing 和 resource reload 分发。必要性是临时定位 rebuild 期间卡顿/失败阶段。

证据：M-00228–M-00236 的 steady_clock、RebuildTiming、world/module checkpoint 和 onResourceReload 调用是直接证据。

风险：这些是本地日志/探针，按要求发布前全部移除；计时输出不能证明某个阶段是崩溃根因。

建议状态：明确待全部移除

行动细节：状态：诊断用途已证实；把结果保存到隔离证据后移除所有 timing/checkpoint，再独立审查 pipeline 逻辑。

验证：静态状态：已由固定 hunk 证实；动态状态：PENDING（本审计未构建、未编译 shader、未运行 CTest、未启动游戏/UI）。

关联 unit：M-00228, M-00229, M-00230, M-00231, M-00232, M-00233, M-00234, M-00235, M-00236

### MCVR-G37-pipeline-recreate-state

分类：工程维护/有含义补充类；unit 数：7；explained=true

功能：pipeline recreate

HEAD → 当前：HEAD recreate 只有一个无参数模式且直接 preClose/build；当前用 recreateMtx 保护，拆分 resizeTargets/rebuildWorld，capture/restore module state、复用 shader pack，并记录阶段耗时。必要性是把窗口 resize 与世界重建分离并避免丢历史。

证据：M-00237–M-00243 直接出现 lock(recreateMtx)、resizeTargets/rebuildWorld、rebuildStates、shaderPack reuse 和 RebuildTiming。

风险：锁持有范围、world module 旧资源释放、shader cache 复用和 timing 输出仍需动态检查。

建议状态：保留

行动细节：状态：重建状态机已解释；验证并发 resize、resource reload、失败回滚和 clean resource ownership。

验证：静态状态：已由固定 hunk 证实；动态状态：PENDING（本审计未构建、未编译 shader、未运行 CTest、未启动游戏/UI）。

关联 unit：M-00237, M-00238, M-00239, M-00240, M-00241, M-00242, M-00243

### MCVR-G38-frame-acquire-submit-present

分类：上游错误修复(证据不足勿认定)；unit 数：12；explained=true

功能：frame acquire/submit

HEAD → 当前：HEAD acquire/submit/present 多为 void、丢弃 fence/queue result；当前返回 VkResult，检测 device failure、drawable size、timestamp query、suboptimal surface 和 presentationChanged，并记录 upload/world/overlay/fuse checkpoint。必要性是让 frame state 在早期窗口和错误时可停止，而不是继续提交无效 command。

证据：M-00244–M-00255 直接显示 VkQueryPool、acquireContext/submitCommand/present 返回值、vkResetFences/vkQueueSubmit 检查、suboptimalSwapchain_ 和 checkpoints。

风险：这是候选错误传播/lifecycle 修补；其中 checkpoint/timestamp 是待移除探针，没有上游复现不能叫上游 bug 修复。

建议状态：待修复

行动细节：状态：业务错误传播与探针已区分；查上游/注入 VkResult 失败并在移除探针后做真实 frame loop 验证。

验证：静态状态：已由固定 hunk 证实；动态状态：PENDING（本审计未构建、未编译 shader、未运行 CTest、未启动游戏/UI）。

关联 unit：M-00244, M-00245, M-00246, M-00247, M-00248, M-00249, M-00250, M-00251, M-00252, M-00253, M-00254, M-00255

### MCVR-G39-resize-screenshot-retainer

分类：工程维护/有含义补充类；unit 数：7；explained=true

功能：resize/screenshot

HEAD → 当前：HEAD recreate 在零 drawable 时阻塞等待，截图尺寸错只 return，currentContext loop 无运行状态边界；当前加入 nonBlockingResize/WindowShouldClose、surface reconstruction 检测、截图错误 VkResult、download completion 和 FrameResourceRetainer resetFrameCount。必要性是支持 early renderer 与可恢复 resize。

证据：M-00256–M-00262 直接显示 waitForDrawableWindow、GLFW_WaitEventsTimeout、screenshot FORMAT_NOT_SUPPORTED、downloadFromBuffer、isRunning 和 resetFrameCount。

风险：事件线程、recreate mutex、frame retainer 与窗口关闭的交互未运行验证；错误返回需被 Java 消费。

建议状态：保留

行动细节：状态：生命周期意图已证实；做最小化窗口/恢复、关闭中截图和 resource-retainer generation 测试。

验证：静态状态：已由固定 hunk 证实；动态状态：PENDING（本审计未构建、未编译 shader、未运行 CTest、未启动游戏/UI）。

关联 unit：M-00256, M-00257, M-00258, M-00259, M-00260, M-00261, M-00262

### MCVR-G40-framework-thread-state

分类：工程维护/有含义补充类；unit 数：6；explained=true

功能：框架线程状态

HEAD → 当前：HEAD Framework 只有普通 bool running；当前使用 atomic running/closed/worldDrawStarted，记录 GPU profile map/mutex、timestamp validity、nonBlockingResize 和 failure state。必要性是跨 render/event/JNI 线程表达停止与 profile 完成状态。

证据：M-00263–M-00268 逐项列出 atomic、mutex、timestampValidBits、completedGpuProfiles 和 waitForDrawableWindow。

风险：atomic 并不自动保证对象生命周期与 Vulkan queue safe；profile map 清理和线程关闭顺序未动态验证。

建议状态：保留

行动细节：状态：线程状态设计已解释；用 ThreadSanitizer/受控关闭和多线程 resize（移除本地探针后）验证。

验证：静态状态：已由固定 hunk 证实；动态状态：PENDING（本审计未构建、未编译 shader、未运行 CTest、未启动游戏/UI）。

关联 unit：M-00263, M-00264, M-00265, M-00266, M-00267, M-00268

### MCVR-G41-renderer-close-options

分类：工程维护/有含义补充类；unit 数：4；explained=true

功能：renderer关闭选项

HEAD → 当前：HEAD Renderer close 不释放 early renderer，Options 没有 presentationChanged；当前 close 调用 releaseLoadingRenderer，并保存 presentationChanged。必要性是结束 early window 资源并让已创建 swapchain 感知选项变化。

证据：M-00269–M-00272 直接显示 loading_renderer include/releaseLoadingRenderer 和 presentationChanged 字段。

风险：close 的调用线程、重复 close 与 early renderer 仍存活时的 JNI callback 未验证。

建议状态：保留

行动细节：状态：资源责任已解释；验证重复 close、异常 close 和 VSync/resize 组合。

验证：静态状态：已由固定 hunk 证实；动态状态：PENDING（本审计未构建、未编译 shader、未运行 CTest、未启动游戏/UI）。

关联 unit：M-00269, M-00270, M-00271, M-00272

### MCVR-G42-texture-lifecycle-reload

分类：主动调整；unit 数：9；explained=true

功能：纹理生命周期

HEAD → 当前：HEAD texture ID 错误会 exit，release 不保存 fallback，reload 期间仍可能释放/绑定旧 image；当前改为异常，提供 releaseTexture/fallback alias、begin/endResourceReload、旧 image/sampler retain 和 downloadTexture。必要性是让 Java resource reload 在 GPU 使用旧资源期间安全过渡。

证据：M-00273–M-00281 直接显示 releaseTexture、releasedTextureFallbacks_、resourceReloadActive_、retain lists、downloadTexture 和非法 ID异常。

风险：锁顺序（mtx_+recreateMtx）、fallback ID 语义、readback layout 与旧资源何时真正释放未动态验证。

建议状态：保留

行动细节：状态：纹理生命周期意图已证实；做 reload/释放/下载/ID 重用和 device-loss 失败注入。

验证：静态状态：已由固定 hunk 证实；动态状态：PENDING（本审计未构建、未编译 shader、未运行 CTest、未启动游戏/UI）。

关联 unit：M-00273, M-00274, M-00275, M-00276, M-00277, M-00278, M-00279, M-00280, M-00281

### MCVR-G43-texture-upload-fence-barrier

分类：上游错误修复(证据不足勿认定)；unit 数：7；explained=true

功能：纹理上传同步

HEAD → 当前：HEAD texture upload reset/submit 和 image barriers 丢弃结果且只覆盖旧 stage；当前检查 fence reset/queue submit，覆盖 RT/fragment/compute sampled reads，并跟踪 destinationImages 后再提交。必要性是避免上传完成前被 shader 读取或失败后继续。

证据：M-00282–M-00288 直接出现 resetResult、submitResult、destinationImages、VK_PIPELINE_STAGE_2_* 和 recordFailure。

风险：候选 Vulkan hardening 无上游 issue/复现；宽 stage mask 仍是性能/同步风险，未做 validation。

建议状态：待修复

行动细节：状态：错误传播与 barrier 变化已静态证实；用 validation、GPU-assisted sync 和 upload/reload 场景验证，保留探针隔离。

验证：静态状态：已由固定 hunk 证实；动态状态：PENDING（本审计未构建、未编译 shader、未运行 CTest、未启动游戏/UI）。

关联 unit：M-00282, M-00283, M-00284, M-00285, M-00286, M-00287, M-00288

### MCVR-G44-texture-declarations-world-state

分类：工程维护/有含义补充类；unit 数：7；explained=true

功能：纹理接口状态

HEAD → 当前：HEAD Textures API 没有 destinationImages/reload/download/fallback 声明，World shouldRenderWorld_ 是普通 bool；当前补齐这些状态字段与接口。必要性是让实现、JNI 和 Framework 同步同一资源状态。

证据：M-00289–M-00295 直接列出 destinationImages、releaseTexture、begin/endResourceReload、downloadTexture、fallback map 和 shouldRenderWorld_ 初始化。

风险：声明变化要求生成 JNI/header 与 Java caller 一致；bool/atomic 的线程策略仍需确认。

建议状态：保留

行动细节：状态：接口契约已解释；做编译、JNI header 和线程调用矩阵核对。

验证：静态状态：已由固定 hunk 证实；动态状态：PENDING（本审计未构建、未编译 shader、未运行 CTest、未启动游戏/UI）。

关联 unit：M-00289, M-00290, M-00291, M-00292, M-00293, M-00294, M-00295

### MCVR-G45-vma-buffer-allocation

分类：上游错误修复(证据不足勿认定)；unit 数：12；explained=true

功能：VMA buffer分配

HEAD → 当前：HEAD VMA buffer allocation 失败只打印并 exit，析构无效 handle 也无条件 destroy；当前保存 VkResult，带 size/budget 抛异常，构造失败时清理 staging，析构只销毁有效资源。必要性是把内存压力传给上层并避免半构造泄漏。

证据：M-00296–M-00307 直接显示 throwBufferAllocationFailure、monitorDeviceLocalBudget、createResult/stagingResult 和 handle 非空判断。

风险：没有上游 issue/最小复现，不能认定原版上游错误；异常安全与调用者 recovery 未动态验证。

建议状态：待修复

行动细节：状态：本地 allocation hardening 已静态证实；做预算耗尽/构造失败注入和资源审计。

验证：静态状态：已由固定 hunk 证实；动态状态：PENDING（本审计未构建、未编译 shader、未运行 CTest、未启动游戏/UI）。

关联 unit：M-00296, M-00297, M-00298, M-00299, M-00300, M-00301, M-00302, M-00303, M-00304, M-00305, M-00306, M-00307

### MCVR-G46-buffer-allocation-info

分类：工程维护/有含义补充类；unit 数：2；explained=true

功能：VMA info初始化

HEAD → 当前：HEAD 保存未初始化的 VmaAllocationInfo；当前零初始化 staging/allocation info。必要性是让失败/可选字段在日志与后续查询中有定义值。

证据：M-00308–M-00309 的 `{}` 初始化是全部变化。

风险：这是低风险初始化变化，但 ABI/结构大小仍由 VMA 版本决定。

建议状态：保留

行动细节：状态：静态变化已证实；随 VMA pin 做编译与 allocator smoke。

验证：静态状态：已由固定 hunk 证实；动态状态：PENDING（本审计未构建、未编译 shader、未运行 CTest、未启动游戏/UI）。

关联 unit：M-00308, M-00309

### MCVR-G47-command-submit-results

分类：上游错误修复(证据不足勿认定)；unit 数：6；explained=true

功能：command submit结果

HEAD → 当前：HEAD command submit 返回 void 且直接调用 vkQueueSubmit；当前提供 checkpoint、两个 submit overload 返回 VkResult，并统一 recordFailure。必要性是让所有上层 queue submission 可以停止或记录错误。

证据：M-00310–M-00315 直接显示 checkpoint、submitMainQueueIndividual 返回类型、fence overload 和 VkResult 检查。

风险：checkpoint 是必须移除的本地探针；错误传播是否改变调用者控制流需逐调用点审查，不能认定上游 bug。

建议状态：待修复

行动细节：状态：API/错误传播已解释；更新全部调用点并做失败注入，之后移除 checkpoint。

验证：静态状态：已由固定 hunk 证实；动态状态：PENDING（本审计未构建、未编译 shader、未运行 CTest、未启动游戏/UI）。

关联 unit：M-00310, M-00311, M-00312, M-00313, M-00314, M-00315

### MCVR-G48-device-diagnostics

分类：vkWaitForFences排查诊断；unit 数：9；explained=true

功能：Device诊断

HEAD → 当前：HEAD Device 只配置基础 features/queues；当前按 diagnosticsRequested 启用 NV checkpoints/diagnostics config、EXT device fault/address binding，并保存 lastFailure、checkpoint labels 和 failure mutex。必要性是收集本地 GPU fault 线索。

证据：M-00316–M-00324 直接列出 MCVR_VULKAN_DIAGNOSTICS、deviceFault/addressBinding feature chains、diagnosticCheckpointsEnabled_ 和 record state。

风险：这些本地诊断/日志/探针按要求发布前全部移除；扩展启用链、driver 支持和额外开销未运行验证，不能定位 core 崩溃。

建议状态：明确待全部移除

行动细节：状态：诊断用途已证实；隔离保存证据后删除 instrumentation，再单独验证 Device feature/lifecycle。

验证：静态状态：已由固定 hunk 证实；动态状态：PENDING（本审计未构建、未编译 shader、未运行 CTest、未启动游戏/UI）。

关联 unit：M-00316, M-00317, M-00318, M-00319, M-00320, M-00321, M-00322, M-00323, M-00324

### MCVR-G49-pipeline-cache

分类：工程维护/有含义补充类；unit 数：4；explained=true

功能：pipeline cache

HEAD → 当前：HEAD pipeline creation 直接调用 Vulkan；当前 Device 持有 PipelineCache，并把 graphics/compute/ray tracing creation 路由到 cache wrapper，同时保留 failure/diagnostic state。必要性是跨 rebuild 复用 pipeline binary、减少预热成本。

证据：M-00325–M-00328 直接出现 createGraphicsPipelines/createComputePipelines/createRayTracingPipelines、pipelineCache_ 和 state maps。

风险：cache data 与 driver/device/extension identity 失配会创建失败或复用错误；持久化文件权限/hash/失效策略未验证。

建议状态：保留

行动细节：状态：缓存接入已解释；核对 cache identity/header、失败回退、跨驱动失效和 package 路径。

验证：静态状态：已由固定 hunk 证实；动态状态：PENDING（本审计未构建、未编译 shader、未运行 CTest、未启动游戏/UI）。

关联 unit：M-00325, M-00326, M-00327, M-00328

### MCVR-G50-dynamic-pipeline-image-lifecycle

分类：上游错误修复(证据不足勿认定)；unit 数：10；explained=true

功能：pipeline/image生命周期

HEAD → 当前：HEAD dynamic pipeline/image code 直接 exit，SwapchainImage 析构只重复销毁 imageViews[0]，DeviceLocalImage 无 mipLevels API；当前改为 Device wrapper/异常，遍历有效 view、清理 staging/image，并提供 mipLevels。必要性是避免错误处理和资源析构破坏后续 frames。

证据：M-00329–M-00338 直接给出 createGraphicsPipelines result、imageView loop、mipLevels() 和有效 handle destroy。

风险：看似明确的生命周期修补仍没有上游 issue/最小复现；异常路径和跨 frame retain 未运行验证。

建议状态：待修复

行动细节：状态：候选修补已静态证实；建立上游/最小复现并用 validation 检查所有 destroy/create 失败路径。

验证：静态状态：已由固定 hunk 证实；动态状态：PENDING（本审计未构建、未编译 shader、未运行 CTest、未启动游戏/UI）。

关联 unit：M-00329, M-00330, M-00331, M-00332, M-00333, M-00334, M-00335, M-00336, M-00337, M-00338

### MCVR-G51-instance-diagnostics

分类：vkWaitForFences排查诊断；unit 数：9；explained=true

功能：Instance诊断

HEAD → 当前：HEAD Instance 固定 debug/validation 行为且不追踪 address binding；当前由环境开关控制 diagnostics/validation，检查可用 layer/extension，创建 debug messenger，并把 address binding/fault match 暴露给 Device。必要性是收集 fault 地址和 validation 线索。

证据：M-00339–M-00347 直接出现 MCVR_VULKAN_DIAGNOSTICS/VALIDATION、debugUtilsEnabled_、validationEnabled_、AddressBindingTracker 和 callbacks。

风险：本地 validation/debug messenger/address tracker 属于待移除探针；输出量、driver 兼容和回调线程安全未验证，不能把日志当根因。

建议状态：明确待全部移除

行动细节：状态：诊断功能已解释；导出隔离证据后移除所有本地 instrumentation，保留必要生产错误处理。

验证：静态状态：已由固定 hunk 证实；动态状态：PENDING（本审计未构建、未编译 shader、未运行 CTest、未启动游戏/UI）。

关联 unit：M-00339, M-00340, M-00341, M-00342, M-00343, M-00344, M-00345, M-00346, M-00347

### MCVR-G52-pipeline-device-wrapper

分类：工程维护/有含义补充类；unit 数：3；explained=true

功能：pipeline wrapper

HEAD → 当前：HEAD 三种 pipeline builder 各自调用 raw vkCreate*；当前统一走 Device create* wrapper，使 pipeline cache/failure policy 集中。必要性是让缓存和错误传播不漏在某一种 pipeline。

证据：M-00348–M-00350 分别替换 graphics/ray-tracing/compute 创建调用。

风险：wrapper 与 raw handle ownership、deferred operation 参数和 cache compatibility 未运行验证。

建议状态：保留

行动细节：状态：调用路由已证实；做三类 pipeline compile/cache hit/miss/失败回退验证。

验证：静态状态：已由固定 hunk 证实；动态状态：PENDING（本审计未构建、未编译 shader、未运行 CTest、未启动游戏/UI）。

关联 unit：M-00348, M-00349, M-00350

### MCVR-G53-shader-error-propagation

分类：上游错误修复(证据不足勿认定)；unit 数：7；explained=true

功能：shader错误传播

HEAD → 当前：HEAD shader stage/file/compile/module 失败打印后 exit；当前抛出带 path/result/error message 的异常。必要性是让 JNI/Framework 能统一清理和报告 shader failure。

证据：M-00351–M-00357 逐项显示 unsupported stage、Cannot open、Failed to compile/create shader 的 throw。

风险：没有上游 issue/复现，不能认定上游 bug；调用者是否捕获异常、是否留下 partially-created module 未动态验证。

建议状态：待修复

行动细节：状态：错误传播意图已证实；查所有 shader callers 的 exception safety，并单独记录 compile failure。

验证：静态状态：已由固定 hunk 证实；动态状态：PENDING（本审计未构建、未编译 shader、未运行 CTest、未启动游戏/UI）。

关联 unit：M-00351, M-00352, M-00353, M-00354, M-00355, M-00356, M-00357

### MCVR-G54-swapchain-reconstruction

分类：上游错误修复(证据不足勿认定)；unit 数：6；explained=true

功能：swapchain重建

HEAD → 当前：HEAD 按逻辑 window width/height 重建且旧 image list 不总清空；当前按 framebuffer size，清空 swapchainImages，并提供 needsReconstruction 比较 surface caps、extent、image count。必要性是处理 HiDPI/resize/suboptimal 的真实 drawable。

证据：M-00358–M-00363 直接显示 GLFW_GetFramebufferSize、swapchainImages_.clear、needsReconstruction。M-00363 仅补 EOF newline。

风险：这是 candidate resize hardening，无上游复现不能认定 bug；surface caps race、zero drawable 和 image view ownership 未运行验证。

建议状态：待修复

行动细节：状态：resize 机制已解释；做 HiDPI、最小化/恢复、suboptimal 和关闭中重建验证。

验证：静态状态：已由固定 hunk 证实；动态状态：PENDING（本审计未构建、未编译 shader、未运行 CTest、未启动游戏/UI）。

关联 unit：M-00358, M-00359, M-00360, M-00361, M-00362, M-00363

### MCVR-G55-vertex-vma-window-contract

分类：主动调整；unit 数：8；explained=true

功能：vertex/window/budget

HEAD → 当前：HEAD material packed field 只有四位 alpha/旧 coordinate shift，VMA 无 budget 观测，borrowed window 错误会 terminate；当前加入 color-layer/glint bits、device-local budget 采样和 GLFW_NO_API/null 校验。必要性是扩展材质语义并避免错误窗口/内存条件静默继续。

证据：M-00364–M-00371 直接给出 packed shifts/masks、DeviceLocalBudget、monitorDeviceLocalBudget、GLFW_CLIENT_API/GLFW_NO_API checks；M-00363 的 EOF 仅为格式。

风险：packed ABI 必须与 shader/Java 同步；budget 日志和 window checks 中的诊断输出需移除；无上游复现不能叫 bug 修复。

建议状态：保留

行动细节：状态：位/窗口/预算意图已证实；做 reflection/ABI、内存压力和 borrowed-window smoke，随后删除预算探针。

验证：静态状态：已由固定 hunk 证实；动态状态：PENDING（本审计未构建、未编译 shader、未运行 CTest、未启动游戏/UI）。

关联 unit：M-00364, M-00365, M-00366, M-00367, M-00368, M-00369, M-00370, M-00371

### MCVR-G56-shader-build-staging

分类：工程维护/有含义补充类；unit 数：3；explained=true

功能：shader staging

HEAD → 当前：HEAD shader CMake 只追踪 common/util/world includes，删除 source 后不会清理旧 SPIR-V；当前纳入 NRD GLSL deps，删除 staging 中不再期望的输出，并复制 priority 目录。必要性是让产物与 source/config 拓扑一致。

证据：M-00372–M-00374 直接出现 GLSL_DEPENDS、EXISTING_SPIRV_OUTPUTS 清理和 RT_PACK_STAGING_DIR/priority copy。

风险：clean staging、安装路径和 configs 引用仍需实际检查；删除规则过宽会误删仍被引用 shader。

建议状态：保留

行动细节：状态：产物卫生意图已证实；在干净 staging/package 上核对每个 SPIR-V hash 与引用闭包。

验证：静态状态：已由固定 hunk 证实；动态状态：PENDING（本审计未构建、未编译 shader、未运行 CTest、未启动游戏/UI）。

关联 unit：M-00372, M-00373, M-00374

### MCVR-G57-material-alpha-text-glint

分类：主动调整；unit 数：11；explained=true

功能：材质alpha/text/glint

HEAD → 当前：HEAD 把透明、text 和 glint 共享/重叠的 packed 值；当前把 transmission/cutout-low/coverage/additive 分类独立，text mode 移到 12..19，扩大 alpha mask，加入 color-layer mix 和 item/entity glint UV 变换。必要性是避免 text 被当作 dielectric、普通 cutout 与 glint bits 冲突。

证据：M-00375–M-00385 直接列出 alpha helpers、POST_TEXT_MODE_* 12..19、ALPHA_MODE_MASK 0x1F、COORDINATE_SHIFT 13、GLINT_MODE_SHIFT 18 和 transformGlintUv。

风险：这是主动材质语义变化，任何 Java packedData 漏同步都会改变透明/水/粒子/glint；静态常量不证明视觉正确。

建议状态：保留

行动细节：状态：材质命名空间已证实；用 CPU packed tests、SPIR-V compile 与 text/water/item/entity scene 回归。

验证：静态状态：已由固定 hunk 证实；动态状态：PENDING（本审计未构建、未编译 shader、未运行 CTest、未启动游戏/UI）。

关联 unit：M-00375, M-00376, M-00377, M-00378, M-00379, M-00380, M-00381, M-00382, M-00383, M-00384, M-00385

### MCVR-G58-nrd-glsl-compat

分类：渲染 SDK 与依赖维护；unit 数：4；explained=true

功能：NRD GLSL适配

HEAD → 当前：HEAD shader 直接定义 NRD_GLSL 并包含旧 HLSL；当前使用 nrd_glsl_compat.glsl，并传 gDiffHitDistParams.xyz/gSpecHitDistParams.xyz。必要性是把 NRD v4.17.3 的 GLSL/参数形状固定在本地适配层。

证据：M-00386–M-00389 直接显示 compatibility include 和 xyz 参数调用。

风险：compat layer 与 NRD pin 漂移会导致编译通过但数值错误；未执行 shader compile。

建议状态：保留

行动细节：状态：适配边界已解释；以当前 NRD headers/shaders 编译并对 hit-distance 数值做场景检查。

验证：静态状态：已由固定 hunk 证实；动态状态：PENDING（本审计未构建、未编译 shader、未运行 CTest、未启动游戏/UI）。

关联 unit：M-00386, M-00387, M-00388, M-00389

### MCVR-G59-delete-old-post-shaders

分类：主动调整；unit 数：8；explained=true

功能：旧post shader删除

HEAD → 当前：HEAD 保留 light_map/world_post/world_post_text/star raster shader；当前从 source 删除八个旧 post_render shader，使其由动态 post/priority/UI 路径消费。必要性是避免 stale shader 被 CMake/staging 打包后继续走旧 render pass。

证据：M-00390–M-00397 都是明确的 whole-file deletion，且 M-00373 有 stale SPIR-V cleanup。

风险：删除文件本身不证明 replacement 完整；若 config/packaging 仍引用旧路径会在运行时缺 shader。

建议状态：待删除

行动细节：状态：删除意图已证实；完成引用闭包和 clean package 检查后再保留删除。

验证：静态状态：已由固定 hunk 证实；动态状态：PENDING（本审计未构建、未编译 shader、未运行 CTest、未启动游戏/UI）。

关联 unit：M-00390, M-00391, M-00392, M-00393, M-00394, M-00395, M-00396, M-00397

### MCVR-G60-advanced-alpha-shadow

分类：主动调整；unit 数：20；explained=true

功能：advanced alpha/shadow

HEAD → 当前：HEAD advanced path 的 cloud mask、color layer、shadow alpha 和 object-to-world 使用旧 world-only/四位 alpha；当前加入 PRIORITY_MASK，区分 colorLayerMix/coverage/additive/transmission/text，采用随机 coverage/透射 throughput，并使用 gl_ObjectToWorldEXT。必要性是让 priority/particle/text/透明表面在主光和阴影射线中保持不同语义。

证据：M-00398–M-00417 直接出现 WORLD_MASK|PRIORITY_MASK、hasColorLayerMix、isCoverageAlphaMode、shadowRay.throughput、text_mode 和 gl_ObjectToWorldEXT。

风险：这是多条主动材质/visibility 变化；随机 coverage、shadow throughput、Vulkan extension spelling 与真实第三方材质仍需验证。

建议状态：保留

行动细节：状态：advanced hit/any-hit 语义已解释；做 transparent/text/particle/shadow 逐场景回归，并核对 extension header。

验证：静态状态：已由固定 hunk 证实；动态状态：PENDING（本审计未构建、未编译 shader、未运行 CTest、未启动游戏/UI）。

关联 unit：M-00398, M-00399, M-00400, M-00401, M-00402, M-00403, M-00404, M-00405, M-00406, M-00407, M-00408, M-00409, M-00410, M-00411, M-00412, M-00413, M-00414, M-00415, M-00416, M-00417

### MCVR-G61-advanced-surface-eval

分类：主动调整；unit 数：20；explained=true

功能：advanced surface

HEAD → 当前：HEAD surface evaluator 将 glint 直接加到 tint、所有表面都走 PBR/height/alpha；当前新增 glintRadiance/emissiveOverlayRadiance、textSurface/usePbr 分支、colorLayerMix、water flag entry 和独立 glint material layer。必要性是让 text/overlay/glint/emission 在 surface preparation 中可分开组合。

证据：M-00418–M-00437 直接出现 glintMaterial/text_mode、SampledSurface fields、usePbr、resolveTextTextureColor、transformGlintUv 和 sampleEmissiveOverlay。

风险：surface cache layout、emissive texture ID、glint strength 和 water detection 必须与 primary/secondary/transparent shaders一致；未运行验证。

建议状态：保留

行动细节：状态：surface 组合意图已证实；做 cache ABI、text/water/glint/emissive scene 与 path depth 回归。

验证：静态状态：已由固定 hunk 证实；动态状态：PENDING（本审计未构建、未编译 shader、未运行 CTest、未启动游戏/UI）。

关联 unit：M-00418, M-00419, M-00420, M-00421, M-00422, M-00423, M-00424, M-00425, M-00426, M-00427, M-00428, M-00429, M-00430, M-00431, M-00432, M-00433, M-00434, M-00435, M-00436, M-00437

### MCVR-G62-advanced-config-priority

分类：主动调整；unit 数：21；explained=true

功能：advanced priority config

HEAD → 当前：HEAD advanced configs 将 particle/weather/text/name-tag/star 作为 post_render raster commands，并保留对应 post_render shader 文件；当前增加 priority_geometry/background intermediates，priority ray tracing/background/composite passes 和 text/transparent-only hit groups，从 command list 移除旧 post_render commands，并删除 advanced post-render star/text shader。必要性是把特殊几何按独立 visibility/ordered blending 合成并清掉 stale 产物。

证据：M-00438–M-00454 直接给出 storage bindings 38/39、priority passes、hit groups 和删除 post_render commands；M-00455–M-00458 是对应 whole-file shader deletion。

风险：配置顺序、attachment extent、premultiplied alpha 和 miss/hit group 引用错误会使画面全黑/遮挡错；需先 compile/clean staging。

建议状态：待重构

行动细节：状态：拓扑替换与文件删除已证实；逐 pass 编译并用 priority outline/name-tag/text/particle/weather 场景验证。

验证：静态状态：已由固定 hunk 证实；动态状态：PENDING（本审计未构建、未编译 shader、未运行 CTest、未启动游戏/UI）。

关联 unit：M-00438, M-00439, M-00440, M-00441, M-00442, M-00443, M-00444, M-00445, M-00446, M-00447, M-00448, M-00449, M-00450, M-00451, M-00452, M-00453, M-00454, M-00455, M-00456, M-00457, M-00458

### MCVR-G63-advanced-primary-volumetric

分类：原版渲染翻译；unit 数：23；explained=true

功能：advanced primary

HEAD → 当前：HEAD advanced primary/volumetric shader 仍使用旧 object transform、直接 alpha/color layer 和 world/player shadow mask；当前接入 emissive overlay/glint/text helpers、skip text parallax、color-layer mix、priority/particle shadow mask 与 volumetric light visibility。必要性是把 vanilla surface/visibility 规则带入 primary 和 cloud shadow。

证据：M-00459–M-00481 直接显示 primary default/no_height changes、textSurface、gl_ObjectToWorldEXT、cachedCoatings 和 PRIORITY/PARTICLE masks。

风险：同一语义复制到 default/no_height/volumetric 分支容易漏修；path-traced primary/secondary 与 raster overlay 的视觉等价未验证。

建议状态：保留

行动细节：状态：advanced primary/volumetric 翻译已解释；用 normal/no-height/water/cloud/emissive/priority scene 逐分支验证。

验证：静态状态：已由固定 hunk 证实；动态状态：PENDING（本审计未构建、未编译 shader、未运行 CTest、未启动游戏/UI）。

关联 unit：M-00459, M-00460, M-00461, M-00462, M-00463, M-00464, M-00465, M-00466, M-00467, M-00468, M-00469, M-00470, M-00471, M-00472, M-00473, M-00474, M-00475, M-00476, M-00477, M-00478, M-00479, M-00480, M-00481

### MCVR-G64-advanced-world-rays

分类：原版渲染翻译；unit 数：42；explained=true

功能：advanced world rays

HEAD → 当前：HEAD advanced world rays 将所有表面统一按 PBR/旧 mask 处理，End sky/star 和 first/secondary emission 未纳入新 routing；当前加入 text/usePbr/emissive/glint、priority/particle visibility、cameraEntityMask、End sky cube、path-traced stars 和 overlay emission。必要性是让世界、实体、云、星空和特殊优先几何在每类 ray 中保持一致。

证据：M-00482–M-00523 直接出现 text_mode/emissive/glint helpers、WORLD_MASK|PRIORITY_MASK|PARTICLE_MASK、evalEndSky/evalPathTracedStars、cameraEntityMask 和 emissiveOverlayRadiance。

风险：多 bounce mask/SHARC update 与 first/secondary emission 组合复杂；静态 mask 存在不证明真实 particle/entity 已进入 TLAS。

建议状态：保留

行动细节：状态：advanced world ray 语义已解释；做 first/secondary/shadow/SHARC 和 End/cloud/star/particle 场景回归。

验证：静态状态：已由固定 hunk 证实；动态状态：PENDING（本审计未构建、未编译 shader、未运行 CTest、未启动游戏/UI）。

关联 unit：M-00482, M-00483, M-00484, M-00485, M-00486, M-00487, M-00488, M-00489, M-00490, M-00491, M-00492, M-00493, M-00494, M-00495, M-00496, M-00497, M-00498, M-00499, M-00500, M-00501, M-00502, M-00503, M-00504, M-00505, M-00506, M-00507, M-00508, M-00509, M-00510, M-00511, M-00512, M-00513, M-00514, M-00515, M-00516, M-00517, M-00518, M-00519, M-00520, M-00521, M-00522, M-00523

### MCVR-G65-vanilla-miss-config

分类：原版渲染翻译；unit 数：13；explained=true

功能：vanilla miss/config

HEAD → 当前：HEAD vanilla-pt 把 End sky 直接 stop、post_render special effects 作为 raster passes；当前实现六面 End sky、rain/sun/star brightness，并在 config 中接入 priority passes、text/transparent-only hit groups，同时删除旧 post_render star/text commands。必要性是沿 vanilla LevelRenderer 语义把天空/文字/特殊透明纳入 RT。

证据：M-00524–M-00536 直接出现 evalEndSky、skyType branches、priority resources/commands/hit groups 和 whole-file post_render shader deletion。

风险：End sky face UV/rotation、star brightness、config ordering 与 old resource deletion 未动态验证；不能用静态 replacement 宣称 parity。

建议状态：保留

行动细节：状态：vanilla miss/config 目的已证实；做 End/rain/star/text/name-tag/transparent-only 场景与 clean package 验证。

验证：静态状态：已由固定 hunk 证实；动态状态：PENDING（本审计未构建、未编译 shader、未运行 CTest、未启动游戏/UI）。

关联 unit：M-00524, M-00525, M-00526, M-00527, M-00528, M-00529, M-00530, M-00531, M-00532, M-00533, M-00534, M-00535, M-00536

### MCVR-G66-vanilla-world-material

分类：原版渲染翻译；unit 数：44；explained=true

功能：vanilla world材质

HEAD → 当前：HEAD vanilla world default/no_height/shadow 使用旧 alpha/color layer、旧 glint UV 和 world/player shadow mask；当前与 advanced 对齐 text/coverage/additive/transmission、emissive/glint material、priority/particle mask 和 extension transform。必要性是让默认 vanilla path 与新 packed/material ABI一致。

证据：M-00537–M-00580 逐项显示 PRIORITY_MASK、colorLayerMix、isCoverageAlphaMode、textSurface、transformGlintUv、sampleEmissiveOverlay 和 shadow throughput。

风险：default/no_height/shadow 三套复制代码可能不一致；透明 coverage 的随机采样和粒子 shadow 尚未运行验证。

建议状态：保留

行动细节：状态：vanilla world 翻译已解释；逐 shader compile，并用 cutout/coverage/additive/text/water/glint/emissive/particle 场景回归。

验证：静态状态：已由固定 hunk 证实；动态状态：PENDING（本审计未构建、未编译 shader、未运行 CTest、未启动游戏/UI）。

关联 unit：M-00537, M-00538, M-00539, M-00540, M-00541, M-00542, M-00543, M-00544, M-00545, M-00546, M-00547, M-00548, M-00549, M-00550, M-00551, M-00552, M-00553, M-00554, M-00555, M-00556, M-00557, M-00558, M-00559, M-00560, M-00561, M-00562, M-00563, M-00564, M-00565, M-00566, M-00567, M-00568, M-00569, M-00570, M-00571, M-00572, M-00573, M-00574, M-00575, M-00576, M-00577, M-00578, M-00579, M-00580

### MCVR-G67-vanilla-text-transparent-world

分类：主动调整；unit 数：15；explained=true

功能：vanilla text/transparent

HEAD → 当前：HEAD vanilla text any-hit 以固定 alpha threshold 丢弃，transparent-only 不带新 emission/alpha mode，world_no_reflect 直接把 glint/emission 合入 tint；当前使用 stochastic glyph coverage、transparent-only throughput/emission、text mode 和 no-reflect emissive/glint 分离。必要性是保留抗锯齿文字、多层透明和显式 vertex emission。

证据：M-00581–M-00595 直接出现 alpha<=0/rand(mainRay.seed)、transparent_only shaders、albedoEmission、glint material 和 world_no_reflect updates。

风险：随机文字 coverage 的 noise/temporal 稳定性、透明排序和 emission 能量未动态验证；旧 threshold 删除需场景确认。

建议状态：保留

行动细节：状态：文字/透明语义已证实；做 glyph/background/name-tag/energy swirl/dragon ray 和多 bounce shadow 回归。

验证：静态状态：已由固定 hunk 证实；动态状态：PENDING（本审计未构建、未编译 shader、未运行 CTest、未启动游戏/UI）。

关联 unit：M-00581, M-00582, M-00583, M-00584, M-00585, M-00586, M-00587, M-00588, M-00589, M-00590, M-00591, M-00592, M-00593, M-00594, M-00595

### MCVR-G68-svgf-exposure-shaders

分类：主动调整；unit 数：12；explained=true

功能：SVGF/exposure shader

HEAD → 当前：HEAD SVGF files 只补 EOF newline，temporal/exposure shader 没有 historyValid/NaN guard；当前格式变化保持计算语句不变，同时 temporal 首帧旁路、exposure historyValid/finite 检查和 histogram NaN/Inf skip。必要性是避免新 history/无效 HDR 把 denoiser 或曝光推入未定义状态。

证据：M-00596–M-00600 的唯一变化是 EOF newline；M-00601–M-00607 直接出现 alpha>=1、historyValid、isnan/isinf 和 sampleForHistogram。

风险：格式 unit 不改变行为；GPU shader 数值稳定性、barrier 和 multi-frame convergence 未执行验证。

建议状态：保留

行动细节：状态：格式与数值 guard 均已分别解释；编译 shader 并做 NaN/Inf、首帧、resize/reload 与曝光收敛测试。

验证：静态状态：已由固定 hunk 证实；动态状态：PENDING（本审计未构建、未编译 shader、未运行 CTest、未启动游戏/UI）。

关联 unit：M-00596, M-00597, M-00598, M-00599, M-00600, M-00601, M-00602, M-00603, M-00604, M-00605, M-00606, M-00607

### MCVR-G69-early-loading-renderer

分类：原版渲染翻译；unit 数：4；explained=true

功能：early loading renderer

HEAD → 当前：HEAD 没有 loading renderer；当前新增在同一 GLFW_NO_API 窗口上创建 Vulkan loading renderer、批次/纹理/role/opacity 参数和 loading element shaders，并提供 bindLoadedGlfw/releaseLoadingRenderer。必要性是让 early display 使用官方单 JAR SPI/bootstrap 的同一窗口和 native graphics path。

证据：M-00608–M-00609 与 M-00618–M-00619 直接给出 renderer classes、bindLoadedGlfw/releaseLoadingRenderer、Vertex/Batch/Parameters 和 element.vert/frag。

风险：同窗口句柄、LWJGL classloader、单 JAR 官方 SPI/bootstrap 装载、线程和窗口所有权是高风险；静态存在不证明普通用户首启。

建议状态：保留

行动细节：核对实际 bootstrap 的单 JAR 官方 SPI、已加载 GLFW handle、exports、线程和 early→game handoff，再做最小启动/关闭测试。

验证：静态状态：已由固定 hunk 证实；动态状态：PENDING（本审计未构建、未编译 shader、未运行 CTest、未启动游戏/UI）。

关联 unit：M-00608, M-00609, M-00618, M-00619

### MCVR-G69b-native-contract-helpers

分类：第三方兼容共用基础设施；unit 数：3；explained=true

功能：native共用helper

HEAD → 当前：HEAD 没有 DiagramState JNI、统一 JNI 异常 helper 或四顶点索引 helper；当前新增 DiagramState begin/post 入口、C++→Java 异常包装器和 LINES/QUADS 索引构造器。必要性是把 early/UI/geometry 的共用边界收敛到可审 API。

证据：M-00610–M-00612 直接显示 acquireUIContext/DiagramState JNI、throwRuntimeException/invokeForVkResult 与 buildFourVertexIndices。

风险：helper 的线程/context 生命周期、异常后清理和 index pattern 与 Java draw mode 必须联合验证；静态 source 不代表 caller 已覆盖。

建议状态：保留

行动细节：状态：共用边界已解释；核对 generated JNI、调用点和 index/exception contract。

验证：静态状态：已由固定 hunk 证实；动态状态：PENDING（本审计未构建、未编译 shader、未运行 CTest、未启动游戏/UI）。

关联 unit：M-00610, M-00611, M-00612

### MCVR-G70-address-binding-diagnostics

分类：vkWaitForFences排查诊断；unit 数：2；explained=true

功能：address诊断

HEAD → 当前：HEAD 没有 device-address binding tracker；当前新增范围重叠检查、对象名映射、fault address 匹配和 bounded history。必要性是把 device fault 地址关联到 buffer/image 对象。

证据：M-00613–M-00614 直接列出 AddressBindingTracker::record/setObjectName/reportFaultAddress、rangesOverlap 与 mutex/deque/maps。

风险：这是本地探针，按要求发布前全部移除；地址精度、回调线程和 driver extension 支持未验证，不能精确定位 core 崩溃。

建议状态：明确待全部移除

行动细节：状态：诊断用途已证实；导出隔离报告后删除 tracker/回调，并保留必要 VkResult 处理。

验证：静态状态：已由固定 hunk 证实；动态状态：PENDING（本审计未构建、未编译 shader、未运行 CTest、未启动游戏/UI）。

关联 unit：M-00613, M-00614

### MCVR-G71-pipeline-cache-file

分类：工程维护/有含义补充类；unit 数：3；explained=true

功能：pipeline cache文件

HEAD → 当前：HEAD 没有持久化 cache file/identity；当前新增 PipelineCache 文件校验、FNV identity、header/status enum，并为三类 pipeline 提供 cache creation statistics。必要性是让 shader warmup/cache 可失效而不是盲读旧 binary。

证据：M-00615–M-00617 直接出现 contentHashMatches/fileEquals、PipelineCacheIdentity、PipelineCacheDataStatus 和 create* wrappers。

风险：cache format/driver UUID/device feature 比对必须覆盖；统计日志不是功能证明，文件权限和损坏回退未验证。

建议状态：保留

行动细节：状态：cache 文件机制已解释；用损坏/跨 driver/device/版本 cache 做回退测试并记录 hash。

验证：静态状态：已由固定 hunk 证实；动态状态：PENDING（本审计未构建、未编译 shader、未运行 CTest、未启动游戏/UI）。

关联 unit：M-00615, M-00616, M-00617

### MCVR-G72-overlay-post-shaders

分类：原版渲染翻译；unit 数：10；explained=true

功能：overlay post shader

HEAD → 当前：HEAD 没有 overlay post shader；当前新增 creeper/invert/spider/blur/diagram/entity-effect GLSL，按原版实体状态效果采样 frame/blur/depth 并输出 overlay。必要性是把 Java UI post calls 接到 Vulkan descriptor/pipeline。

证据：M-00620–M-00629 直接显示各 fragment 的 sampler bindings、diagram color/depth 和 horizontal/vertical blur shader。

风险：descriptor binding、颜色空间、blur tap 数和 overlay order 未 shader compile/runtime 验证；不能只由文件名证明效果 parity。

建议状态：保留

行动细节：状态：shader 代码意图已证实；编译并逐 effect 做实体状态、resize/HiDPI 和 blend 验证。

验证：静态状态：已由固定 hunk 证实；动态状态：PENDING（本审计未构建、未编译 shader、未运行 CTest、未启动游戏/UI）。

关联 unit：M-00620, M-00621, M-00622, M-00623, M-00624, M-00625, M-00626, M-00627, M-00628, M-00629

### MCVR-G73-shader-utility-layers

分类：主动调整；unit 数：4；explained=true

功能：shader utility层

HEAD → 当前：HEAD 没有 emissive overlay/glint material/priority payload/NRD compatibility helper；当前新增这些共用 GLSL 函数与 payload，供多套 RT hit/raygen 共享。必要性是避免每个 shader 分支复制不同的 emission/glint/priority 编码。

证据：M-00630–M-00633 直接列出 sampleEmissiveOverlay、applyGlintMaterialLayer、PriorityRayPayload 和 NRD encode/linear helpers。

风险：共享 helper 的 include 顺序、descriptor bindings、payload size 和数值范围必须与所有 caller 一致；未编译验证。

建议状态：保留

行动细节：状态：共用层目的已证实；做 include/descriptor/payload reflection 与 shader compile。

验证：静态状态：已由固定 hunk 证实；动态状态：PENDING（本审计未构建、未编译 shader、未运行 CTest、未启动游戏/UI）。

关联 unit：M-00630, M-00631, M-00632, M-00633

### MCVR-G74-priority-shaders

分类：主动调整；unit 数：10；explained=true

功能：priority shader

HEAD → 当前：HEAD 没有 priority raygen/hit/any-hit/composite shader；当前新增 background/geometry raygen、ignore/outline/name-tag/text hit/any-hit、miss payload 和 compute compositor，用 priority mask 在 world occlusion 外独立合成。必要性是实现 outline/name-tag/text 的 ordered foreground/background。

证据：M-00634–M-00643 直接列出 priority images、PriorityRayPayload、priorityRay.color/hitT、text sampling 和 premultiplied composite inputs。

风险：camera first-person visibility、origin epsilon、alpha composition 和 descriptor binding 未运行验证；priority shader 是主动产品语义。

建议状态：保留

行动细节：状态：priority shader 目的已证实；做 compile、payload/layout、first-person/occlusion/alpha scene 回归。

验证：静态状态：已由固定 hunk 证实；动态状态：PENDING（本审计未构建、未编译 shader、未运行 CTest、未启动游戏/UI）。

关联 unit：M-00634, M-00635, M-00636, M-00637, M-00638, M-00639, M-00640, M-00641, M-00642, M-00643

### MCVR-G75-contract-tests

分类：工程维护/有含义补充类；unit 数：4；explained=true

功能：C++合同测试

HEAD → 当前：HEAD 没有 first-party contract tests；当前新增 CMake test registration、C++ contract/index test、GPU exposure test 和 pipeline cache header test。必要性是把 ABI/index/exposure/cache 的静态或可控验证固定下来。

证据：M-00644–M-00647 直接显示 tests/CMakeLists、contract_test.cpp、exposure_gpu_test.cpp 和 pipeline_cache_header_test.cpp。

风险：测试尚未执行，GPU test 可能依赖设备/driver；通过测试只证明 contract marker，不等于游戏视觉或 JNI ABI。

建议状态：保留

行动细节：状态：测试源已解释；配置后运行并保存结果，区分环境 skip 与代码 failure。

验证：静态状态：已读测试 source；执行状态：PENDING（本审计未构建/运行 CTest、未启动游戏/UI）。

关联 unit：M-00644, M-00645, M-00646, M-00647

### MCVR-G76-contract-static-markers

分类：工程维护/有含义补充类；unit 数：15；explained=true

功能：静态合同标记

HEAD → 当前：HEAD 没有针对 borrowed window、diagnostics、DLSS resource、emissive、line safety、glint、JNI、native upload、particle、reload、shader、texture lifecycle、transparency、vanilla effects、world text 的固定门槛；当前新增 CMake marker tests 检查这些 source/config/shader 约束。必要性是防止替代路径或本地 override 回归。

证据：M-00648–M-00662 的文件名与 REQUIRED/FORBIDDEN marker 分别指向 GLFW_NO_API、device diagnostics、DLSS、emissive、line、glint、JNI、particle、reload、shader、texture、transparency、End sky/star/text/priority。

风险：这些是静态门槛，无法证明真实 Java/mod/runtime 链；其中 diagnostics marker 只能帮助移除，不应被当成生产功能。

建议状态：保留

行动细节：状态：合同范围已证实；逐项执行并把 marker PASS 与实际 build/launch/visual acceptance 分开。

验证：静态状态：已读合同文件和 marker；执行状态：PENDING（本审计未配置/运行 tests、未启动游戏/UI）。

关联 unit：M-00648, M-00649, M-00650, M-00651, M-00652, M-00653, M-00654, M-00655, M-00656, M-00657, M-00658, M-00659, M-00660, M-00661, M-00662

### MCVR-X01-vk-result-lifecycle

分类：上游错误修复(证据不足勿认定)；unit 数：14；explained=true

功能：VkResult/lifecycle

HEAD → 当前：HEAD 在 fence wait/reset、queue submit、NRD/SBT/texture/screenshot/frame submit 中丢弃 VkResult；当前把结果存入局部变量、recordFailure 或返回上层，并在失败时停止继续录制/等待。必要性是有效业务错误传播和生命周期保护，独立于日志探针。

证据：M-00056–M-00060、M-00155、M-00180、M-00252、M-00259、M-00282、M-00287、M-00311–M-00312 的新增 `VkResult`/`submitResult`/`resetResult`/`recordFailure` 是直接证据。

风险：未发现本地最小复现或上游 issue；不能称上游已修复。失败注入、device lost、调用者 recovery 和 fence reuse 未验证。

建议状态：保留

行动细节：状态：有效 VkResult/lifecycle 候选已分离；保留前先做失败注入与上游核查，禁止把记录日志当修复证明。

验证：静态状态：已由固定 hunk 证实；动态状态：PENDING（本审计未构建、未编译 shader、未运行 CTest、未启动游戏/UI）。

关联 unit：M-00056, M-00057, M-00058, M-00059, M-00060, M-00061, M-00155, M-00180, M-00252, M-00259, M-00282, M-00287, M-00311, M-00312

### MCVR-X02-local-probes-remove

分类：vkWaitForFences排查诊断；unit 数：38；explained=true

功能：本地诊断探针

HEAD → 当前：HEAD 没有这些阶段日志/时间戳/validation/device-fault/address-binding/budget/checkpoint；当前添加本地探针以定位 frame、DLSS、ray、queue、device 和 memory 阶段。必要性只存在于本轮排查，发布功能不依赖这些输出。

证据：这些 unit 的新增标记包括 `checkpoint(`、`gpuProfile`、`timestamp`、`diagnosticsRequested`、`VK_EXT_device_fault`、`AddressBindingTracker`、`monitorDeviceLocalBudget` 和 verify_device_address contract。

风险：探针会改变时序/开销并污染崩溃判断；按用户要求必须全部移除，不能宣称它们定位了 vkWaitForFences 或 core.dll+0x12558d。

建议状态：明确待全部移除

行动细节：状态：诊断项明确待全部移除；先把原始结果复制到隔离 Evidence，再从生产代码/测试 marker 中删除，留下独立有效错误传播。

验证：静态状态：已由固定 hunk 证实；动态状态：PENDING（本审计未构建、未编译 shader、未运行 CTest、未启动游戏/UI）。

关联 unit：M-00124, M-00125, M-00126, M-00127, M-00181, M-00182, M-00183, M-00184, M-00187, M-00190, M-00191, M-00245, M-00249, M-00250, M-00251, M-00252, M-00310, M-00317, M-00318, M-00319, M-00320, M-00321, M-00323, M-00324, M-00339, M-00340, M-00341, M-00342, M-00343, M-00344, M-00345, M-00346, M-00367, M-00368, M-00369, M-00613, M-00614, M-00649

### MCVR-X03-particle-world-geometry

分类：原版渲染翻译；unit 数：13；explained=true

功能：粒子世界几何

HEAD → 当前：HEAD 中 Java 选择 post 路径，native 已有 EntityPost/EntityBuildDataBatch 双路；当前新增 RT + visibility mask 路由并保留 particle/emission 分支。必要性是给世界几何提供 RT 入口；是否属于上游错误尚未证实。

证据：M-00082、M-00087–M-00088 显示 draw/group/emission build；M-00401、M-00410、M-00481、M-00547、M-00565、M-00585、M-00589 显示 PARTICLE_MASK；M-00656 固定该路径的合同标记。

风险：静态路由只证明新增 RT/mask 选择，不能证明真实第三方粒子经过 Java→JNI→build→BLAS/TLAS；原有 EntityPost/EntityBuildDataBatch 双路和 Java post 选择仍需运行时核对。

建议状态：保留

行动细节：用 vanilla/第三方粒子、beam/lightning/dragon-ray 和 shadow 场景确认完整链路；若要声称 bug 修复，另找上游 issue 或最小复现。

验证：静态状态：已由固定 hunk 证实；动态状态：PENDING（本审计未构建、未编译 shader、未运行 CTest、未启动游戏/UI）。

关联 unit：M-00082, M-00087, M-00088, M-00401, M-00410, M-00481, M-00489, M-00507, M-00547, M-00565, M-00585, M-00589, M-00656

### MCVR-X04-transparency-after-render

分类：主动调整；unit 数：20；explained=true

功能：透明后渲染

HEAD → 当前：HEAD 半透明、文字、天气和名称标签在多个旧 raster post pass 中顺序合成；当前 normal text/coverage/additive 进入 any-hit/transparent-only，priority background/geometry/composite 按前景 alpha 做 ordered blend，并删除旧 post commands。必要性是把半透明与 RT hit depth/visibility 绑定。

证据：M-00414–M-00416、M-00426–M-00428、M-00577–M-00579、M-00582–M-00584 给出 alpha/throughput；M-00438–M-00454、M-00527–M-00532 给出 priority targets/config。

风险：ordered blend、alpha coverage stochastic acceptance、text/name-tag depth 和 post-render deletion 尚未 compile/runtime 证实；不能只凭配置宣称透明正确。

建议状态：保留

行动细节：状态：替代机制已解释；执行 shader/config contract，再用 transparent entity/text/name-tag/weather/particle 场景验证。

验证：静态状态：已由固定 hunk 证实；动态状态：PENDING（本审计未构建、未编译 shader、未运行 CTest、未启动游戏/UI）。

关联 unit：M-00414, M-00415, M-00416, M-00426, M-00427, M-00428, M-00577, M-00578, M-00579, M-00582, M-00583, M-00584, M-00438, M-00439, M-00440, M-00454, M-00527, M-00528, M-00529, M-00532

### MCVR-X05-emissive-box-line

分类：原版渲染翻译；unit 数：21；explained=true

功能：发光判定箱/线

HEAD → 当前：HEAD 没有 emissive overlay ID/radiance，debug line separators 可能生成 opaque prism；当前把 emissive overlay texture/semantic emission 写入 MaterialVertex/cache，过滤无效 line/zero-alpha separator，并让 priority outline 输出独立白色 payload。必要性是保持发光判定箱/线与 vanilla blending 语义。

证据：M-00019、M-00071–M-00073、M-00080、M-00084–M-00088、M-00405/M-00409/M-00419/M-00423、M-00497–M-00498/M-00515–M-00516、M-00630/M-00639 给出字段、过滤和 shader radiance 证据。

风险：overlay texture lifecycle、line width/zero-alpha 视觉和 outline priority depth 未动态验证；emission 能量可能重复计算。

建议状态：保留

行动细节：状态：数据/判断/路径静态已证实；做 glow/box/line/outline/eyes/beam 场景与 texture release/reload 验证。

验证：静态状态：已由固定 hunk 证实；动态状态：PENDING（本审计未构建、未编译 shader、未运行 CTest、未启动游戏/UI）。

关联 unit：M-00019, M-00071, M-00072, M-00073, M-00080, M-00084, M-00085, M-00086, M-00087, M-00088, M-00405, M-00409, M-00419, M-00423, M-00484, M-00497, M-00498, M-00515, M-00516, M-00630, M-00639

### MCVR-X06-cache-warmup

分类：工程维护/有含义补充类；unit 数：26；explained=true

功能：缓存与预热

HEAD → 当前：HEAD 已有 ShaderSpirvCache 的磁盘 SPIR-V 依赖 hash/read/write/compile cache；当前补充 ShaderPack VkShader 对象复用、WorldPipeline rebuild reuse 和持久化 VkPipelineCache identity/header，使同一 device/generation 的构造与 pipeline warmup 更少重复。必要性是降低 rebuild/warmup 成本并在 cache 损坏或 device 不匹配时可回退。

证据：M-00196–M-00203、M-00229–M-00238、M-00325–M-00328、M-00615–M-00617、M-00647 共同给出 object cache、rebuild reuse、Device pipeline wrappers、PipelineCacheIdentity/status 和 header test；HEAD 已有 ShaderSpirvCache 磁盘缓存。

风险：cache key、failure fallback、device generation、parallel shader safety 和实际 warmup 速度仍未知；日志统计本身是辅助证据。

建议状态：保留

行动细节：状态：缓存/预热机制已静态解释；验证 identity、corrupt/mismatch cache、并发 rebuild、首次/重复启动耗时。

验证：静态状态：已由固定 hunk 证实；动态状态：PENDING（本审计未构建、未编译 shader、未运行 CTest、未启动游戏/UI）。

关联 unit：M-00096, M-00105, M-00196, M-00197, M-00198, M-00199, M-00200, M-00201, M-00202, M-00203, M-00207, M-00208, M-00209, M-00210, M-00229, M-00231, M-00234, M-00238, M-00325, M-00326, M-00327, M-00328, M-00615, M-00616, M-00617, M-00647

### MCVR-X07-early-window-same-instance

分类：版本Loader迁移；unit 数：17；explained=true

功能：early同窗口

HEAD → 当前：HEAD 的 early/loading 与生产 renderer 没有可见的同窗口交接；当前通过动态绑定已加载 GLFW、GLFW_NO_API 校验、borrowed Window surface 和 loading renderer 共享同一 window。必要性是避免两个 GLFW/window 与 swapchain/event loop 分裂；实际 bootstrap 仍需核对。

证据：M-00023–M-00025、M-00031/M-00034–M-00038、M-00269–M-00270、M-00370–M-00371、M-00608–M-00609、M-00618–M-00619 的函数/符号直接形成同窗口证据。

风险：仅有 native source 和 单 JAR 官方 SPI/bootstrap 不能证明 classloader、exports、window ownership、普通 end-user first-start；错误时序可能产生空 renderer。

建议状态：保留

行动细节：核对实际 bootstrap 的单 JAR 官方 SPI、已加载 GLFW handle、exports、线程和 early→game handoff，再做最小启动/关闭测试。

验证：静态状态：已由固定 hunk 证实；动态状态：PENDING（本审计未构建、未编译 shader、未运行 CTest、未启动游戏/UI）。

关联 unit：M-00023, M-00024, M-00025, M-00031, M-00034, M-00035, M-00036, M-00037, M-00038, M-00269, M-00270, M-00370, M-00371, M-00608, M-00609, M-00618, M-00619

### MCVR-X08-thread-synchronization

分类：工程维护/有含义补充类；unit 数：28；explained=true

功能：线程同步

HEAD → 当前：HEAD 多处使用普通 bool、无锁 map 或阻塞 event wait；当前在 chunk/resource/world rebuild 处加入 recursive mutex/scoped_lock/atomic running，借用窗口用非阻塞事件循环，diagnostic/cache map 使用 mutex。必要性是防止 render/JNI/event/build 线程同时释放或读取资源。

证据：M-00061/M-00062、M-00093/M-00099/M-00107、M-00188/M-00193/M-00194、M-00237/M-00248/M-00252/M-00256/M-00261/M-00266/M-00268、M-00274/M-00275/M-00279/M-00281/M-00287、M-00316/M-00328/M-00339/M-00343/M-00345/M-00613/M-00614 具体出现 lock/atomic/nonBlocking/owner_less。

风险：锁顺序、递归锁覆盖范围、JNI callback 与 close/recreate 的 deadlock/Use-after-free 未运行验证；diagnostic locks 随探针必须删除。

建议状态：保留

行动细节：状态：线程意图已解释；做受控 resize/reload/close/queue stress 和 lock-order review，移除本地诊断结构后再评估。

验证：静态状态：已由固定 hunk 证实；动态状态：PENDING（本审计未构建、未编译 shader、未运行 CTest、未启动游戏/UI）。

关联 unit：M-00061, M-00062, M-00093, M-00099, M-00107, M-00113, M-00188, M-00193, M-00194, M-00237, M-00248, M-00252, M-00256, M-00261, M-00266, M-00268, M-00274, M-00275, M-00279, M-00281, M-00287, M-00316, M-00328, M-00339, M-00343, M-00345, M-00613, M-00614

### MCVR-X09-third-party-compat-native

分类：第三方兼容共用基础设施；unit 数：51；explained=true

功能：第三方兼容native基础设施

HEAD → 当前：HEAD 的 Java→native proxies、entity metadata 和 UI overlay 入口分散在既有调用中；当前统一 JNI exception/result 边界、draw mode/geometry group 分类、emissive/line safety 和 overlay interface，使 Create/Sable/Veil 等外部 caller 使用同一 native 协议。必要性是把第三方 caller 的失败与几何语义收敛到可审边界；这不证明任一模组已兼容。

证据：M-00026–M-00048 覆盖 Options/Pipeline/Renderer/Texture/Chunk/Entity JNI proxies；M-00070–M-00090 覆盖 geometry group、line width、emission 和 EntityBuildData；M-00115–M-00121 覆盖 UI effect/diagram API。

风险：第三方 Java caller 的实际版本、线程、pointer ownership 和 mixin 时序不在 MCVR 静态 hunk 内；外部 mod 兼容必须以真实 caller/runtime 为证。

建议状态：保留

行动细节：按实际 Create/Sable/Veil caller 建立 native method/ABI/geometry matrix，再做目标场景验证；不要把 SDK pin 或静态 contract 当作模组兼容 PASS。

验证：静态状态：proxy/geometry/UI 边界已由固定 hunk 证实；动态状态：PENDING（本审计未加载第三方模组或启动游戏）。

关联 unit：M-00026, M-00027, M-00028, M-00029, M-00030, M-00031, M-00032, M-00033, M-00034, M-00035, M-00036, M-00037, M-00038, M-00039, M-00040, M-00041, M-00042, M-00043, M-00044, M-00045, M-00046, M-00047, M-00048, M-00070, M-00071, M-00072, M-00073, M-00074, M-00075, M-00076, M-00077, M-00078, M-00079, M-00080, M-00081, M-00082, M-00083, M-00084, M-00085, M-00086, M-00087, M-00088, M-00089, M-00090, M-00115, M-00116, M-00117, M-00118, M-00119, M-00120, M-00121

### MCVR-X10-third-party-content-boundary

分类：第三方逐模组/共用设施；unit 数：21；explained=true

功能：第三方entity内容边界

HEAD → 当前：HEAD 的 EntityPost/EntityBuildDataBatch 接收 Java geometry group/content 与 draw mode；当前在同一共用 batch 中增加 line width/zero-alpha separator、emission、overlay texture 和 priority 选择。必要性是为不同第三方 entity 内容提供明确的 native 几何分流；这不证明任一模组的实际 caller 已兼容。

证据：M-00070–M-00090 覆盖 postRenderFlagName、geometryContentNames/geometryGroupNames、draw mode、line safety、emissiveOverlayTextureIDs、EntityBuildDataBatch 和构造参数。

风险：不同模组的 group/content 名称、顶点计数、线程和 pointer ownership 仍需真实 caller 证据；本组不能替代 Create/Sable/Veil 的运行时兼容测试。

建议状态：保留

行动细节：建立按模组的 geometry group/ABI/scene matrix，先验证普通实体、name-tag、line、beam 和 emissive content，再更新兼容结论。

验证：静态状态：entity content 分流已由固定 hunk 证实；动态状态：PENDING（本审计未加载第三方模组或启动游戏）。

关联 unit：M-00070, M-00071, M-00072, M-00073, M-00074, M-00075, M-00076, M-00077, M-00078, M-00079, M-00080, M-00081, M-00082, M-00083, M-00084, M-00085, M-00086, M-00087, M-00088, M-00089, M-00090

## 覆盖结论

`reviewed-index.json` 证明 662 个 unit 全部落入 87 个语义组，当前没有 unexplained group。格式变化 unit（M-00271、M-00334、M-00363、M-00379、M-00596–M-00600）按 EOF newline/缩进的直接证据归入工程维护；它们不改变运行时行为。

MCVR 当前差异的静态意图、依赖 pin、替代渲染拓扑和候选风险已经归组；诊断 instrumentation 必须移除，真正的 VkResult/lifecycle 保护须独立审查，构建/打包/JNI ABI/模组场景/视觉和 core 崩溃定位仍未完成。
