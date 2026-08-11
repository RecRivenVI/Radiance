# 生产 JAR 投放记录（2026-09-17）

## 源码状态

- MCVR 工作树保留最小化 layout 保活修复（`descriptor.hpp/.cpp`、`dynamic_pipeline.hpp/.cpp`，与 `minimal-fix/after` 完全一致）；WAW 归因诊断与 E-02 实验均已撤回。
- 未提交、未暂存。
- 诊断代码（draw-state trace / descriptor lifecycle）仍编译在内，但默认关闭，不随本 JAR 启用。

## 构建

- `.\gradlew.bat distributedJar verifyDistributedJar --no-daemon --console=plain`（Gradle JVM = Java 21 Temurin 21.0.11）：PASS。
- 构建日志：`build.log`。

## 产物

| 项目 | SHA-256 |
|---|---|
| JAR（`Radiance-0.1.5-alpha-neoforge-1.21.1.jar`，146642977 bytes） | `7D325AB5294087BA08216B1D9B77B22E29B0E5E842803EE9813EC4F3E33F7EF5` |
| JAR 内 `core.dll` | `985B8ACDE50413965CAFDD82DB0088CC25673353FCB747A1BD17C2B4B3ED74CB` |
| JAR 内 `META-INF/radiance/radiance-game.jar` | `EA8C8B5014A6218B8F7DB44C7D7DD24C3EAE7C14218400EFFB0AAF0F59991B32`（与基线 Java 内容逐项一致，仅归档元数据不同） |

## 目标

- 路径：`E:\Minecraft\PrismLauncherDev\instances\Radiance-Crash-A-1.21.1-20260915\minecraft\mods\Radiance.jar`
- 投放前目标 mods 目录为空，无旧文件被覆盖。
- 投放后哈希与构建产物一致：`7D325AB5…`。
- 该 JAR 不包含自动进世界参数（quickplay 仅为本地启动脚本注入），行为与正常发行包一致。

## 2026-09-18 命名统一（上游约定）

- 移除全部 `Radiance.jar` 部署重命名/校验：`preparePackagedClient`、`runPackagedClient`、`prepareManualBase/Full`、`runManualBase/Full` 现在统一使用规范产物名
  `Radiance-0.1.5-alpha-neoforge-1.21.1.jar`（`archives_base_name` + `version` + `neoforge` + MC 版本）。
- GAME 层 JAR 由 `radiance-game.jar` 改为上游 `jar` 任务命名 `Radiance-0.1.5-alpha.jar`；`distributedJar` 内嵌路径为
  `META-INF/radiance/Radiance-0.1.5-alpha.jar`，并新增 `META-INF/radiance/game.path` 元数据；
  bootstrap `RadianceModFileCandidateLocator` 改为按 `game.path` 解析（不再硬编码文件名），`verifyDistributedJar` 增加 `game.path` 一致性校验。
- 新产物（2026-09-18）：
  | 项目 | 值 |
  |---|---|
  | 外层 JAR `Radiance-0.1.5-alpha-neoforge-1.21.1.jar` | `FC6DAFD43E5A49DFC689633119208E6A5CDBAB26B35F8A7EDC0DC21343BAE791` |
  | 内嵌 `core.dll`（干净 layout 修复构建） | `985B8ACDE50413965CAFDD82DB0088CC25673353FCB747A1BD17C2B4B3ED74CB` |
  | 内嵌 GAME JAR `Radiance-0.1.5-alpha.jar` | 由 `game.sha256`（`9f45dfda…`）校验 |
- 冒烟测试：`runPackagedClient`（quickplay 自动进世界）部署 `FC6DAFD4…`，入世界后观察 69.7 s 无 hs_err（记录 `e02-retest-20260917\rename-smoke\run1`）。
- 投放：`E:\Minecraft\PrismLauncherDev\instances\Radiance Test\minecraft\mods\`，旧 `Radiance.jar` 已删除，新文件哈希 `FC6DAFD4…`。
- 仍未修复（独立事项）：Veil 4.3.2 兼容 Mixin 的 `GL30C`→`GL43C` 重定向目标，`Radiance Test` 实例加入航空学模组后仍会因该注入失败在初始化阶段崩溃。

## 2026-09-18 03:40 投放（stonecutter 图鉴移植 + Ponder/兼容修复）

- 构建：`JAVA_HOME=eclipse_adoptium-21` → `gradlew distributedJar prepareManualFull`；诊断 mixin `BufferBuilderBuildTraceMixins` 已移除（`radiance.mixins.json` 校验：诊断引用 0、`PonderWorldStageMixins` 存在）。
- 产物：
  | 项目 | 值 |
  |---|---|
  | 外层 JAR `Radiance-0.1.5-alpha-neoforge-1.21.1.jar` | `AF125504956DDE7FF377457451E172DFA0FC59AA1015095AD76747BC99FCF4CB`（146,652,998 B） |
  | 内嵌 `core.dll`（`beginDiagram` 清屏移植后的 MCVR 构建） | `5AAB42A3E62B0B0C…`（30,974,464 B） |
  | 内嵌 GAME JAR `Radiance-0.1.5-alpha.jar` | 路径见 `META-INF/radiance/game.path`，`radiance.mixins.json` 校验通过 |
- 内容：stonecutter 图鉴完整方案移植（`renderMain`/`renderNote` + `DiagramState.begin/post`、FBO 跳过、CPU `aabbInFramebuffer`、sticky note 重实现、`DiagramBufferSource` 每层独立 allocator 修复 `Not building!`）+ Ponder 世界绘制延迟 + 此前全部 Veil/Create 兼容修复。
- 投放：`E:\Minecraft\PrismLauncherDev\instances\Radiance Test\minecraft\mods\Radiance-0.1.5-alpha-neoforge-1.21.1.jar`（哈希与构建一致）。原目录中的旧文件 `Radiance-0.1.5-alpha-neoforge-1.21.1.jar.disabled`（146,644,217 B，2026-09-18 01:46）保持原样未动。
- 已知风险：`run16` 中图鉴渲染约 19 s 后出现 `vkWaitForFences failed -4`（`VK_ERROR_DEVICE_LOST`），尚未定位；本次投放用于实例复现观察。测试时建议设置 `DISABLE_RTSS_LAYER=1` 以排除 RivaTuner 层干扰。

## 2026-09-18 05:05 投放（HttpTexture 皮肤/披风上传 targetID 修复）

- 缺陷现象（实例反馈）：进世界/玩家加载瞬间出现文字贴图变披风图案、玩家皮肤变文字/透明/纯色。
- 根因：`HttpTexture.upload(NativeImage)`（玩家皮肤+披风）覆盖了 `SimpleTexture` 的加载路径，既不走 `doLoad` 的 11 参 `upload(...)`，也没有像 `DynamicTexture.upload()` 那样先 `bind()`；`NativeImageMixins` 的 targetID 回退到 `TextureProxy.boundTexture()`，把皮肤/披风像素写入当时绑定的其它纹理（常见为字体图集），目标纹理本身无数据（透明/纯色），跨帧出现文字↔披风/皮肤↔文字的串写。
- 修复：新增 `com.radiance.mixins.vanilla_resource_tracker.HttpTextureMixins`（注入 `HttpTexture.upload` 的 `NativeImage;upload(IIIZ)V` 调用点，设置 `targetID = getId()`），注册进 `radiance.mixins.json`；与 `NativeImageBackedTextureMixins`（DynamicTexture）同模式。
- 产物：
  | 项目 | 值 |
  |---|---|
  | 外层 JAR `Radiance-0.1.5-alpha-neoforge-1.21.1.jar` | `E7C00370CF205DCE9841811D8B9E63B40FBAFA371704F49CA7FA27E188E1B2BF`（146,653,540 B） |
  | 内嵌 `core.dll` | `5AAB42A3E62B0B0C…`（30,974,464 B，未变；本次仅 Java 改动） |
  | mixin 校验 | `HttpTextureMixins` 已注册、诊断 mixin 引用为 0 |
- 投放：`E:\Minecraft\PrismLauncherDev\instances\Radiance Test\minecraft\mods\Radiance-0.1.5-alpha-neoforge-1.21.1.jar`（哈希与构建一致）；目录内旧文件现名为 `….jar.duplicate`，未改动。
- 同前：`vkWaitForFences -4`（device lost）原因不明，用户暂不处理；测试建议 `DISABLE_RTSS_LAYER=1`。

## 2026-09-18 05:35 投放（overlay post 管线 pipelineLayout keep-alive 修复）

- 缺陷（实例 `hs_err_pid63504`，05:15:33）：`EXCEPTION_ACCESS_VIOLATION` at `nvoglv64.dll+0xf1c729`（与历史 layout UAF 同偏移）；栈 `DiagramState.post()` ← `SimulatedDiagramCompatibility.render` ← `renderNote` ← sticky note `renderWidget` ← `DiagramScreen.renderWindow`；进世界约 30s、图鉴便签渲染时触发。
- 根因：`UIModule::initOverlayPostPipelines()` 用 `overlayDescriptorTables_[0]` 创建 DIAGRAM 等后期管线，但 `GraphicsPipelineBuilder::definePipelineLayout(shared_ptr<DescriptorTable>)` 只存裸 `VkPipelineLayout`，未持有 layout 所有权。图鉴每帧 append 2 个 post uniform 触发 `refreshOverlayDescriptorTable()`（uniform buffer 扩容）重建描述符表，旧表随 frame 退休被销毁 → 管线内 layout 悬垂；`postDiagram` 每帧 bind 该管线时驱动踩已释放内存。历史最小化修复只覆盖 `DynamicGraphicsPipeline`，三个通用 builder 漏了同样处理。
- 修复：`MCVR/src/core/vulkan/pipeline.hpp/.cpp` — `GraphicsPipelineBuilder`/`ComputePipelineBuilder`/`RayTracingPipelineBuilder` 的 `definePipelineLayout(DescriptorTable)` 同步保存 `descriptorTable->pipelineLayoutKeepAlive()`，`build()` 将其移交管线对象持有（与 `dynamic_pipeline` 同模式）。
- 验证：本地 `runManualFull`（`aero-compat\run18-post-layout-keepalive`）图鉴打开后运行 147.8s 无 hs_err、无崩溃报告；此前同场景数秒必崩。
- 产物：
  | 项目 | 值 |
  |---|---|
  | 外层 JAR `Radiance-0.1.5-alpha-neoforge-1.21.1.jar` | `48C1E3B727526B8FB508FE81A325687706C000CC49B200DF685CA63EC3FD9915`（146,654,082 B） |
  | 内嵌 `core.dll`（keep-alive 构建） | `4EE769E20F7B6F67…`（30,976,512 B） |
  | mixin 校验 | `HttpTextureMixins` 已注册、诊断引用 0 |
- 投放：`E:\Minecraft\PrismLauncherDev\instances\Radiance Test\minecraft\mods\Radiance-0.1.5-alpha-neoforge-1.21.1.jar`（哈希与构建一致）；`….jar.duplicate` 未改动。

## 2026-09-18 06:20 投放（Sable 实体变换精度修复 + 启动窗口居中修复；诊断代码已清理）

- Sable 实体模型偏移（站上结构时模型偏移）：根因是 `SableSubLevelBridge.inverseTransformPosition` 使用 JOML `Matrix4d.invert()`；在结构坐标 ±2048 万量级下该逆矩阵不精确（单测 `m × m⁻¹` 偏差 1700~2800），导致本地坐标算错、模型被摆放到错误世界位置。修复为与 Sable `Pose3dc` 相同的解析变换（`transformPosition`/`inverseTransformPosition`/`localCameraPosition`），并在 `readPose` 归一化四元数。实例日志验证：修复后 `forward(inverse(pos)) == pos` 且静止结构上 `result == pos`。
- 启动窗口位置：NeoForge `DisplayWindow` 用 `glfwGetVideoMode` + `glfwGetMonitorPos`（整屏）居中，Radiance 早期窗口用 `glfwGetMonitorWorkarea`（工作区）居中，底部任务栏时窗口高约任务栏高度一半（本机 2560×1440/任务栏 48px → 24px）。`RadianceImmediateWindowProvider.createWindow` 改为整屏视频模式居中，与 NeoForge 对齐。实测窗口客户区原点 (427,240) = `((2560−1706)/2, (1440−960)/2)`；旧逻辑为 y=216。
- 实体诊断（`[sable-entity-debug]`、探针与反射辅助）已全部移除。
- 产物：
  | 项目 | 值 |
  |---|---|
  | 外层 JAR `Radiance-0.1.5-alpha-neoforge-1.21.1.jar` | `0B1F315D…`（构建于 06:20，含上述修复） |
  | 内嵌 `core.dll` | `4EE769E2…`（未变；本轮仅 Java 改动） |
- 投放并启动：相同实例路径；启动后实测窗口位置符合整屏居中预期。

## 2026-09-18 06:55 投放（PT 移除原版假明暗，世界/子关卡统一）

- 决策：PT 世界不使用原版"面方向亮度"（物理光照已提供方向性），世界方块与子关卡方块统一处理；AO 与 lightmap 保留。
- 修改：
  - `BlockModelRendererMixins`：原 `isBuildingExternalSection()` 分支改为**无条件**对 `brightness[]` 除以 `world.getShade(quad.getDirection(), quad.isShade())`，即世界与子关卡都不再烘焙原版方向面亮度（保留 AO/tint/emission）。
  - `ChunkProxy`：删除已无用的 `externalSectionBuild` ThreadLocal 与 `isBuildingExternalSection()`，`rebuildSingle` 直接调用 section builder。
  - `FluidRendererMixins`：同样移除液体顶点色里的 `getShade` 方向系数（顶/底/侧面），避免流体仍保留假方向明暗。
- 影响：侧面不再被"AO×方向亮度 + 物理 N·L"双重压暗，整体更符合 PT；原有 `veil:pinwheel` 的 `VeilBlockFaceBrightness` 机制不受影响（那是光栅 Veil 消费者用的）。
- 产物 JAR `5C4CF489…`；内嵌 `core.dll` 未变（纯 Java 改动）。已投放并启动。

## 2026-09-18 07:25 投放（PT 移除 lightmap/方向明暗，仅物理光照）

- 决策：PT 不再应用原版 lightmap 与方向明暗；表面只接受物理光照（AO/tint 仍在）。
- 排查：`render_world_post.*` 在 MCVR 中无任何代码引用（死着色器，仅随包打包），未改动；PT 里唯一的 lightmap 使用点是 chit 的 `applyFlywheelFragmentLighting`。
- 修改 `MCVR/src/shader/util/vertex.glsl#applyFlywheelFragmentLighting`：
  1. 移除 `flywheelCardinalLight` 方向项；
  2. 移除 `sampleFlywheelLight` 动态光照采样与 `sampledAo`；
  3. 移除 `appearance.materialState` 的 lightmap 乘色；
  4. 保留 crumbling 特效。`flywheelCardinalLight`/`sampleFlywheelLight` 函数体保留（无调用，编译器剔除）。
- 构建：`gradlew prepareRuntime`（重建 MCVR `build-radiance-1.21.1-neoforge` 并 INSTALL 到 `src/main/resources`，重生成 `advanced.zip`/`vanilla-pt.zip` 与 `core.dll`）；zip 校验：cardinal 调用 0、materialState 乘色 0、动态采样 0。
- 产物：JAR `87401B299143046538557508406EFF72409CE940A15A04416A79EF3DA18B7D68`（146,669,251 B）；内嵌 `core.dll` 更新为 `FAAE0E05…`（30,971,904 B，由 canonical `build-radiance-1.21.1-neoforge` 安装）。
- 已投放并启动。
