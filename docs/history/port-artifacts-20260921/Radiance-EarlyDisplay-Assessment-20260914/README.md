# 原 Early Display 接管方案评估

2026-09-14；目标为 NeoForge 21.1.248 / FML 4.0.43。只做源码与加载顺序评估，没有恢复 Early Display、改变测试配置或启动验证程序。

## 结论

**不建议原样恢复。单个对外分发的 Radiance JAR 是有技术路径的目标，但旧方案没有实现这条交付链；同窗口交接的方向值得保留，旧方案的渲染连续性则没有闭合。**

| 标准 | 原方案现状 | 判断 |
| --- | --- | --- |
| 只安装 Radiance JAR，首次启动生效 | 依赖外部改写的 earlydisplay JAR、classpath 替换和 nativeLibrary JVM 属性；主 JAR 排除 bridge/early DLL | 不满足 |
| 新方案能否仍只分发一个 JAR | FML 存在从 mods/ 加载早期 SPI 的路径；还需解决同 JAR 再进入 GAME 层、类隔离、native 提取和 provider 选择 | 有可行方向，尚无原型 PASS |
| 沿用同一个窗口 | 早期创建 GLFW_NO_API 窗口，交接检查同一个非零 handle；销毁早期 Vulkan 资源但不销毁该 GLFW 窗口 | 静态设计成立 |
| 画面持续更新、无黑闪地交接 | 截取一次画面后关闭早期运行时，之后用缓存纹理；主 renderer 随后重新初始化 | 不足以通过；定格明确，黑闪/时长未测 |

这里允许 JAR 正常解压自身 native 库到本地；要求的是不依赖用户额外修改启动器、替换 Loader 文件、预置文件或首次失败后重启。评估中的“原方案”来自 stonecutter 9fe7811 的接线和已隔离的归档源码，不采用更晚的未提交实验。

## 1. 单 JAR：问题在早期装载，不在容纳 DLL

旧构建单独编译 Early Window bridge，把它装进修改后的官方 earlydisplay JAR，再通过开发运行任务替换 classpath；native 库路径由 `-Dradiance.earlyWindow.nativeLibrary=...` 传入。原始 Git 树还缺少三个 PatchOfficial*/FilterOriginal* 实现。把 DLL 和类文件加入主 JAR，既不会自动提早执行，也不会自动替换已经选定的 earlydisplay 模块。

但不能据普通 `@Mod` 初始化较晚就说“单 JAR 不可能”。当前 FML 会在 mods/ 中识别早期服务 JAR，其中包含 `GraphicsBootstrapper` 和 `ImmediateWindowProvider`；bootstrap 在 provider 选择前执行。这是明确的早期入口。[官方服务集合](https://github.com/neoforged/FancyModLoader/blob/1.21.1/loader/src/main/java/net/neoforged/fml/loading/TransformerDiscovererConstants.java)、[发现流程](https://github.com/neoforged/FancyModLoader/blob/1.21.1/loader/src/main/java/net/neoforged/fml/loading/ModDirTransformerDiscoverer.java)、[provider 选择](https://github.com/neoforged/FancyModLoader/blob/1.21.1/loader/src/main/java/net/neoforged/fml/loading/ImmediateWindowHandler.java)。本地 4.0.43 bytecode 已交叉核对，两个 FML JAR 的 SHA-1 与缓存内容标识一致。

另一个约束是：进入 SERVICE 层的路径会被 LaunchContext 标记为已定位，后续普通 mod discovery 的 addPath 会跳过它。**仅增加 service descriptor，可能得到一个早期服务，却失去正常 Radiance Mod 装载。** 自定义候选定位器通过 addJarContent 再登记 MOD，或对外单 JAR 内部拆成早期/普通模块，是需要原型验证的候选方案；不视为已经验证成功。

还必须注意：发现 SPI 并不证明常规 Mixin 能改写 SERVICE 层的官方 Early Display 类。旧方案的调用替换、保留官方绘制逻辑、两个阶段的类可见性，都需要明确实现。不能用同名 provider 抢先匹配或改一个 manifest 值来代替这些工作。

加载侧完整证据见 [loading.md](loading.md)。

## 2. 交接：同一个 HWND，不是同一套渲染资源

原源码的实际顺序是：

```text
官方 DisplayWindow 的调用被桥接到早期渲染器
  → 创建并呈现同一个 GLFW_NO_API 窗口
  → Minecraft 请求窗口交接
  → 捕获一份 RGBA 图像，放入 JVM 属性对象
  → 销毁早期 swapchain/device/surface/instance，并清空模拟 GL 状态
  → 主 MCVR renderer 初始化和构建管线
  → 把之前的 RGBA 上传为主 renderer 纹理
  → NeoForgeLoadingOverlay 使用这张固定纹理淡出
```

正面部分是：窗口身份有校验，游戏不会因为这条交接代码主动新建另一个 GLFW 窗口；官方加载覆盖层的外层淡出逻辑也有保留。静态图作为短暂过渡帧可以有用。

但以下问题仍然存在：

| 问题 | 已核实的源码事实 | 对连续性的影响 |
| --- | --- | --- |
| 画面只采集一次 | completeOfficialHandoff 截图一次；textureId() 首次创建后直接返回缓存 ID | 后续进度/动画不会进入这张纹理 |
| 早期资源退役太早 | shutdown_locked 销毁全部 Vulkan 呈现资源，并将 runtime 整体重置 | 主 renderer 尚未初始化完；没有等主首帧可显示的交接屏障 |
| 官方后半段仍会绘制 | NeoForgeLoadingOverlay 每帧更新 Minecraft 进度并调用 DisplayWindow.render，构造时还加入 Mojang 纹理 | 旧方案已经清空对应早期模拟状态，后半段绘制没有完整接上 |
| 同窗口不保证无黑闪 | 主 MCVR 独立创建自己的 Vulkan 资源；旧交换链已销毁 | 系统可能暂时保留旧画面，但不能把它当成可靠的跨交换链呈现协议 |
| 缩放/方向/颜色尚无对照 | 早期原生呈现和游戏覆盖层分别计算缩放；快照使用 RGBA UNORM | HiDPI、窗口调整、UV方向、色彩与边框匹配都需逐帧验证 |
| 收尾尚需闭合 | 快照纹理缓存和 JVM 中的 byte[] 没有对应的明确释放步骤 | 交接完成后资源所有权不够清晰 |

官方覆盖层并非只贴一张旧截图：它会调用 `displayWindow.render()`、更新进度，在完成后渲染菜单并淡出，最后关闭 DisplayWindow 的绘制资源。见本地精确版本源码 [NeoForgeLoadingOverlay.java](official-sources/net/neoforged/neoforge/client/loading/NeoForgeLoadingOverlay.java:65)。4.0.43 DisplayWindow 的 bytecode 也确认：窗口交接停止后台定时绘制，但保留用于后续调用的绘制对象；close 不销毁交给游戏的 GLFW 窗口。

因此，若只要求“同窗口里保留最后一帧再淡出”，旧方案具备部分基础；若要求持续的官方加载表现及可靠的首帧交接，原实现不满足。没有在本轮启用旧方案，不能断言必然黑屏，也不能报告黑帧数、停帧时长或视觉验收 PASS。

## 3. 建议保留和调整的边界

保留：同一 GLFW_NO_API 窗口、明确窗口所有权、官方加载流程/布局语义、最后一帧作为过渡备用图。

调整：正规的早期装载入口；普通 Mod 与早期服务的装载隔离；早期绘制状态在覆盖层阶段的延续；主首帧就绪与呈现所有权交接；完成后的资源释放。不能只保留截图，便认为保留了官方加载动画。

不需要因为以上结论立刻重组两个仓库或重写整套 MCVR。应先做两个独立的小验证，决定所需实现边界：

1. **包装验证**：全新隔离实例，只放一个实验 JAR，普通启动即先执行 early bootstrap，随后正常发现 Radiance @Mod；没有外部补丁 JAR、额外 JVM 参数、缓存文件修改或首次重启依赖。分别证明 provider 选择、SERVICE/GAME 类可见性和 native 提取。服务端/data 目标也不应误初始化窗口。
2. **交接验证**：先不接复杂世界渲染，用动态进度/运动图案证明早期画面持续更新到主 renderer 首帧，主帧就绪后才退役旧呈现。记录同一 window handle、最后早期 present/首个主 present、帧序列，检查黑闪、定格、DPI/尺寸变化和关闭路径。

通过这两个验证，再接回真实官方加载 UI 与 MCVR。共享 Vulkan device/swapchain，或使用受控的呈现交接，属于后续依据原型选择的实现；当前不提前承诺其中一种。

## 关键原方案证据

- [只截一次图并 shutdown](D:/Workspaces/Artifacts/Radiance-EarlyDisplay-Isolation-20260914/before/src/main/java/com/radiance/earlywindow/bridge/RadianceEarlyWindowNative.java:37)
- [nativeLibrary 依赖 JVM 属性](D:/Workspaces/Artifacts/Radiance-EarlyDisplay-Isolation-20260914/before/src/main/java/com/radiance/earlywindow/bridge/RadianceEarlyWindowNative.java:61)
- [同一窗口校验](D:/Workspaces/Artifacts/Radiance-EarlyDisplay-Isolation-20260914/before/src/main/java/com/radiance/earlywindow/bridge/OfficialGlfwBridge.java:73)
- [缓存快照纹理](D:/Workspaces/Artifacts/Radiance-EarlyDisplay-Isolation-20260914/before/src/main/java/com/radiance/platform/neoforge/OfficialEarlyWindowFrameTexture.java:16)
- [销毁资源并重置整个运行时](D:/Workspaces/Artifacts/Radiance-EarlyDisplay-Isolation-20260914/before/src/main/native/early_window/radiance_early_window_vulkan.cpp:1403)
- [主 renderer 阻塞式初始化与管线构建](D:/Workspaces/Repositories/GitHub/RecRivenVI/Radiance/src/main/java/com/radiance/mixins/vulkan_render_integration/MinecraftClientMixins.java:51)
- [精确 FML JAR 身份](fml-artifacts.json)、[DisplayWindow bytecode](DisplayWindow-bytecode.txt)

原方案实际为 CPU 处理受限绘制语义、Vulkan 上传/呈现。CPU 路径本身不是本次两项标准的否决理由；要验证的是打包入口和可观察的加载/交接行为，而不是日志中的技术标签。
