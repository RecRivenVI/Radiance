# Veil 4.3.2 vanilla shader reload — implementation checkpoint

日期：2026-09-15。仓库：`D:\Workspaces\Repositories\GitHub\RecRivenVI\Radiance`。旧 `20260915-082227-handoff` Artifact 保持原样。本报告仅涵盖此次 VanillaShaderCompiler 接替与非 external CPU uniform 修复。

## 结果与边界

**源码实施、Java 编译、40 项定向/联合测试与真实 float[6] GLSL→SPIR-V 离线编译 PASS。游戏内 Mixin 应用、资源重载及首次 Vulkan draw 尚未验证。**

Veil 原 `reload(Collection)` 的 `GL.getCapabilities()` 在 NO_API 下会直接失败。新增 redirect 用 null 作为已有 ShaderVersionProcessor Vulkan 接替的标记，继续执行原 Veil scheduler、资源读取、`#moj_import`、VanillaShaderProcessor setup/modify/free、两 stage 的共享 custom map 与原资源错误记录。没有返回空 completed future，也没有伪造 OpenGL capability 或跳过真实预处理。

原 scheduler future 完成仅表示它原有 CPU 调度阶段结束；回调仍可能排在 Minecraft render queue。只有完整 processed vertex/fragment pair 的回调发布后才完成 Java source 替换；native Vulkan program 则按 draw mode 在**首次 draw**编译。此检查点不能以 future done 宣称源码已替换或 native 编译已成功。

## 源码承接

每次原 `compileShader` HEAD 分配独立 ticket，在原 Minecraft.execute 队列 Runnable 内携带 ticket，stage 捕获与结束回调都读取执行 ticket。即使两次重叠 reload 具有同 activeBuffers，也不会把旧 vertex 与新 fragment 拼接。只允许当前 latest ticket 发布；发布检查与元数据/source/registry 修改持有同一锁，后台新 begin 无法插入检查和发布之间。

完整 source pair 发布时先补齐真实 CPU uniforms，再 `unregisterLiveShader` 清该 ShaderInstance 的全部 draw-mode CACHE 和 LIVE resource generation，替换两个真实 processed source，最后重新登记当前 generation。首次 draw 因旧 Java variant 已清除而走新 source 的注册；编译异常继续抛出，不回退旧 program。此路径不显式释放旧 native shader，避免把仍在途命令引用的对象提前释放；native cache/命令资源保活的游戏内验证仍为 pending。

不完整/失败 stage 保留原 source，保留 Veil 原逐资源 Throwable 日志，额外记录“did not install a complete processed stage pair for Vulkan reload”，不更新已提交 activeBuffers，也不标记 source installed。它是失败保留语义，**不是 source 替换 PASS**。最新 ticket 失败时旧完整 source 仍可用；旧 ticket 即使稍后成对到达也不发布。正常 original compileShader RETURN 会排结束回调并清 worker ticket；若原 setup 在原逐资源 try/catch 外灾难性抛出，则 RETURN 接替不会执行，该情形尚无运行验证。

DynamicBufferManager 的 setActiveBuffers 改为请求真正重新预处理，包含从非零返回 0；endFrame 的旧 `veil$applyCompile` 不再触发 raw GL。原 swapShaders 在回调结束时按 pending ticket 清理。getActiveDynamicBuffers 对 vanilla instance 返回最近成功 source pair 的 mask，Veil ShaderProgram wrapper 保持原行为。

## 真实 uniform 数据

固定 Sable 预处理会增加 NormalMat、SableEnableNormalLighting、SableSkyLightScale、ScreenSize、water/shadow 参数；Veil 实际 light.glsl 还声明 `uniform float[6] VeilBlockFaceBrightness`。原 Veil raw GL reflection 被既有兼容插件擦除，因此 ShaderInstanceCompatibility 接替从真实 processed source 建立 vanilla Uniform，放入原 uniforms/uniformMap，使原按名 setter/getter 更新真实 CPU 缓冲。保留未变化动态对象，移除不再声明的动态对象，关闭仅一次；新对象初始化为 GL uniform 默认零值。

ShaderRegistry 保留现有非数组 uniform 顺序，从 source 识别普通 uniform 数组，按真实元素类型/数量创建一个 std140 array field。数组必须有完整 `base[i]` CPU 对象，缺元素或形状不符在 native 注册之前抛出；不静默填替代数据。ShaderProxy 非 external 路径改为按 field 名找 scalar、按 `base[i]` 找数组元素并按 stride 写值，不再依赖 uniforms list 的位置。已有 external 快速路径保持原实现。ShaderField 显式 isArray 元数据保留原 7/8 参数构造行为，只有新 vanilla 数组路径显式标记长度 1 为 array；Translator 因而保留 GLSL `[1]`，CPU 对象仍命名 `base[0]`。

本次支持普通 scalar/vector/mat2/3/4 uniform、单维数组及正整数乘积长度。未承诺宏/任意常量表达式、struct、uniform block 或 sampler array；遇到已识别但不支持的数组类型/维度明确失败。原 sampler 字段与绑定路径保留。

根代理另已修复 SableShaderBridge 同 mask/name 的 source 缓存：immutable source 引用计算 SHA256，并将 digest 加入 key，旧 future 完成后异步 free；此项由根所有权负责，其独立编译证据为 `java-sable-reload-key-build.log`，不计为本报告游戏内证明。

## 源码清单与 Mixin 注册

以下路径均相对仓库；完整 SHA256 见 Evidence/veil-reload/source-freeze.sha256。

| 文件 | 责任 |
|---|---|
| src/main/java/com/radiance/compatibility/veil/VeilDynamicBufferReloadAccess.java | render queue 结束访问接口 |
| src/main/java/com/radiance/compatibility/veil/VeilVanillaShaderMetadataAccess.java | processed uniform 元数据接口 |
| src/main/java/com/radiance/compatibility/veil/VeilVanillaShaderReloadBridge.java | ticket、source pair、registry、CPU uniform discovery |
| src/main/java/com/radiance/mixins/compatibility/veil/VeilVanillaShaderCompilerMixins.java | reload GL 接替、原 worker/render callback 承接 |
| src/main/java/com/radiance/mixins/compatibility/veil/VeilDynamicBufferManagerMixins.java | swap/apply 原 GL 路径接替与 queue 结束 |
| src/main/java/com/radiance/mixins/compatibility/veil/VeilShaderInstanceCompatibilityMixins.java | vanilla CPU uniform 生命周期 |
| src/main/java/com/radiance/client/shader/ShaderRegistry.java | 非 external source 数组字段与完整性检查 |
| src/main/java/com/radiance/client/proxy/vulkan/ShaderProxy.java | 非 external 按名打包 |
| src/main/java/com/radiance/client/shader/ShaderField.java | 显式 isArray，保留原构造兼容 |
| src/main/java/com/radiance/client/shader/ShaderTranslator.java | singleton array 的真实 GLSL 形状 |
| src/test/java/com/radiance/compatibility/veil/VeilVanillaShaderReloadBridgeTest.java | source pair/ticket/all variants/真实 include/单元素数组 |
| src/test/java/com/radiance/client/shader/ShaderRegistryUniformArrayTest.java | float[6] source→field→GLSL→SPIR-V、缺元素反例 |
| src/test/java/com/radiance/client/proxy/vulkan/ShaderProxyUniformArrayTest.java | 逆序真实 CPU list 的六元素值与缺元素反例 |

需新增注册 `compatibility.veil.VeilVanillaShaderCompilerMixins`；根已在 radiance.mixins.json 注册。DynamicBufferManager/ShaderInstanceCompatibility/ShaderVersionProcessor 原注册继续使用。共享 config、build.gradle、MCVR 未由本子任务修改。

## 实际验证

最终串行 Gradle：`compileJava compileTestJava test`，指定 VeilVanillaShaderReloadBridgeTest、ShaderRegistryUniformArrayTest、ShaderProxyUniformArrayTest、ShaderTranslatorTest、VeilShaderBridgeTest、VeilMixinCompatibilityTest、SectionRasterStorageTest、RenderCaptureContractTest、AfterWorldRenderTest，`--no-daemon`。**BUILD SUCCESSFUL in 15s，40 tests，0 failures/errors/skipped**。后 3 套是根新增 world/section 联合检查。git diff --check PASS；共享 config 的既有 CRLF 警告不影响 exit 0。

固定实际 Veil JAR 的 javap `-p -c -s` 10 个 descriptor/call/field 检查 PASS，包括 reload、compileShader、synthetic lambda、GL.getCapabilities、Minecraft.execute、veil$recompile、markRecompiled、swapShaders、veil$swapBuffers、veil$applyCompile。此为静态精确签名证据，不等于运行时 Mixin transformation PASS。

数组反例使用真实分配的 Uniform CPU buffer；逆序输入列表仍写入六个真实值，std140 stride 为 16，字段总长 96。缺 `[5]` 精确报错。实际 Veil JAR light.glsl 被测试读取。生成 brightness.vert 经 `glslangValidator -V --target-env vulkan1.2 -S vert` 编译，SPIR-V 非空 1344 bytes；不是 fake compiler。

证据：Evidence/veil-reload/ 下 9 个 TEST-*.xml、source-freeze.sha256、veil-fixed-jar-javap.txt、java-verification.txt、brightness.vert、brightness.vert.spv。JAR SHA256：`774b83887d882dc47cd4d29d140cef0c8677747a6fe7799649f490926a4e0265`；GLSL SHA256：`477b984deb2d03b83069a6db024506b17362d96ac2337c6fc9755471bfc7ef4f`；SPIR-V SHA256：`56aa1bb6903e2681f2f3137c1d94708bf3cea846da55cfa163c96a23d3f2d908`。

## 尚未验证

未启动游戏或 UI。实际 NO_API reload 的 Mixin 应用、packaged resource preprocess 回调、同 mask 重叠 reload、Sable setter/texture consumers、首次 Vulkan draw 的真实 native 编译及失败传播、frames-in-flight resource 保活均 pending。没有宣称世界画面/GPU reload 成功。没有 stage/commit/push/删除。
