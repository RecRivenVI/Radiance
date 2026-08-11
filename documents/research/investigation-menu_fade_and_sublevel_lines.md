# 主菜单淡入与 sublevel 拉杆轮廓调查

调查日期：2026-10-15。使用者在静态场景候选包中报告主菜单“单人游戏”按钮短暂闪白，以及 sublevel 上油门拉杆的活动部分轮廓绕线轴转动。本次完成源码、实际依赖字节码与离线几何调查，没有修改产品、启动客户端或运行性能实验。

## 问题

使用者暂时无法判断上一轮静态场景优化是否改善体验，保留为后续观察；这不否定原有阶段计数，也不将新视觉问题预先归因于静态快照。

部署回执对应 JAR `0345454a53e0ee246bf55e6f07ac0dd7851fccc45624e9673a95c314b75c7f0d`、`core.dll` `63076faae2413ab2c9b0742f25d74c4dbb33f6ea12325d2aeadc82c090790fdc`，NeoForge 21.1.259、Create 6.0.10、Aeronautics bundled 1.3.2 与 Sable 2.0.6。回执只证明投放身份，不补写本次进程的实际加载验证或用户未报告的用例。

两个待解释现象分别为启动时的 GUI 光栅状态与世界 BER 线段转换，按独立调用链核对，不混为同一个 PT 故障。

## 方法

- 读取当前工作区的 `RadianceLoadingOverlay`、`RasterDrawBridge`、`ShaderProxy`、`GlBooleanStateMixins`、原生加载渲染与 `UIModuleContext`。
- 从本地 Gradle 缓存的 NeoForge 21.1.259 sources JAR 提取 `NeoForgeLoadingOverlay.java`，直接核对当前固定加载器的加载覆盖层，而非沿用 21.1.250 结论。
- Minecraft 1.21.1 的 `TitleScreen`、`AbstractButton`、`GuiGraphics`、`RenderStateShard` 与 `LevelRenderer` 使用已有参考源码，核对按钮顺序、alpha、绘制时机与线段 normal 的含义。未据此声称对 Minecraft 全部 Mixin 后字节码完成核验。
- 从登记的实际 Aeronautics bundled 1.3.2 JAR 提取内嵌 Simulated，使用 JDK 21 `javap -p -c` 核对 `ThrottleLeverRenderer` 与 `SimBlockEntityTypes`；同时阅读留存同版本源码和 Create 的 visual 注册合同。
- 追踪 `SableSubLevelBridge.queueBlockEntities`、`EntityProxy.queueTransformedBlockEntities`、普通选中框与模型轮廓，再核对 native `Entities::build` 的线段截面基底。
- 用原生截面公式做离线旋转等变性反例；这是数学与源码对照，不是游戏复现、GPU 回归或新修复的验证。

证据位于 `D:/Workspaces/Artifacts/Radiance/visual-path-investigation-20261015-170633/`。`D:/Workspaces/Artifacts/Radiance/visual-path-investigation-20261015-170633/evidence-archive.zip!findings.json` 固定读取源码的原始字节 SHA-256、HEAD 与算例；工作区存在先前未提交修改，HEAD 本身不代表完整源码身份。`NeoForgeLoadingOverlay-21.1.259.java`、`D:/Workspaces/Artifacts/Radiance/visual-path-investigation-20261015-170633/evidence-archive.zip!ThrottleLeverRenderer.javap.txt`、`D:/Workspaces/Artifacts/Radiance/visual-path-investigation-20261015-170633/evidence-archive.zip!SimBlockEntityTypes.javap.txt` 与 `current-*` 保存可回查输入。

## 发现

### 主菜单淡入的混合状态缺口

NeoForge 21.1.259 的加载覆盖层在绘制加载画面后调用 `RenderSystem.defaultBlendFunc()`、`disableBlend()`，再恢复白色 shader color。这些既是绘制行为，也是后续 GUI 可观察的状态。

当前 `RadianceLoadingOverlay.render` 在原生覆盖层绘制后只恢复 shader color；结束分支也没有恢复默认混合因子和关闭混合。native 加载渲染器使用自己的管线，结束时将 `ui` 已存状态重放到 command buffer，并不会替 Java/GUI 建立 NeoForge 的这两个状态。

`TitleScreen` 的第一个常规菜单按钮是 `menu.singleplayer`。`AbstractButton.renderWidget` 设置淡入 alpha、调用 `enableBlend()`，随后立即 blit 按钮纹理，但不自己设置混合因子。`GuiGraphics.innerBlit` 立即提交，不是等待下一个按钮统一提交；`RasterDrawBridge` 会在本次 draw 中写入 `ColorModulator`。因此目前没有证据支持“整批按钮的 alpha 被后一次 setColor 统一覆盖”。

如果此时遗留因子是初始 `ONE/ZERO`，或其他不匹配当前直通 RGBA 的组合，首个按钮会忽略淡入权重或被过度提亮。随后文字 RenderType 的透明状态会建立并在清理时恢复默认混合，后续按钮和下一帧恢复正常。用 RGB 0.8、背景 0.1、alpha 0.02 举例，`ONE/ZERO` 输出 0.8，正常 source-over 只有 0.114；这说明为何低 alpha 时差异特别明显，不代表实际闪白像素就是这些值。

**确认项**：加载覆盖层缺失原实现的混合状态收尾。**尚未确认项**：用户发生闪白的那一帧实际混合因子、纹理与像素输出；未捕获该帧，不能将上述条件链写成已经完成运行定因。原版按钮悬停/焦点高亮仍是需由同帧输入排除的替代解释。

最小修复方向是在加载覆盖层原有边界恢复原版混合状态，包括提前结束的分支；不改按钮颜色、淡入时长或整个 GUI 的混合公式。不以强制所有按钮不透明掩盖问题。后续验证应包含首个按钮与后续按钮相同 alpha 下的状态/输出，以及首启、覆盖层淡出和资源重载。

### 油门拉杆是独立 BER 轮廓

实际依赖的 `ThrottleLeverRenderer.renderSafe` 在非虚拟对象且准星命中对应方块时调用私有 `renderOutline`。该调用在 Flywheel 普通部件几何分支之外；visual 登记使用 Create 的默认保留 BER 行为，不能因为拉杆模型有 Flywheel visual 就认为轮廓也已实例化。

`renderOutline` 获取 `RenderType.lines()`，取 `getHandleShape(defaultState)`，在 PoseStack 上叠加安装面、朝向、枢轴平移和当前拉杆摆角，再调用 Minecraft `LevelRenderer.renderShape`。原版为黑色、alpha 0.4 的线，位置来自当时姿态，不是普通块外形框、发光模型轮廓，也不是 Catnip Outliner。

Radiance 的实际链路是：

```text
Sable renderPose 的 orientation / scale / position / rotationPoint
→ queueTransformedBlockEntities 中的 PoseStack
→ ThrottleLeverRenderer 的安装姿态及当前 handle 摆角
→ LevelRenderer.renderShape / RenderType.lines
→ StorageVertexConsumerProvider / processWorldEntityRenderData
→ JNI entityLineFrames
→ native LINES 转方截面细棱柱
```

顶点位置已经应用 sublevel 与拉杆局部变换，但 `queueTransformedBlockEntities` 调用 `processWorldEntityRenderData` 时没有设置 `lineFrameCapture`；该数据由 ThreadLocal 取得，空值在 JNI 打包时变成世界单位基底。因此几何端点转动了，棱柱截面仍以世界轴决定绕线 roll，遇到 native 的 `0.99` 参考轴切换还可能出现不连续变化。这是已确认的转换合同缺口，不是来源弹簧或拉杆模型的单面问题。

已有两条保护没有覆盖这里：普通 sublevel 方块选中框显式传递 owner frame；模型优先级轮廓由 `WorldOutlineGeometry` 将稳定局部参考轴写入 normal，并由 native 的 `radiance:priority/outline` 分支消费。拉杆属于普通 BER 内容，没有该标记；而原版 `renderShape` 的 normal 表示线段切向，不能直接当作垂直截面参考轴。

仅给 BER 补 sublevel orientation 能解决一部分 owner 旋转，却仍没有完整表达拉杆安装面与动画摆角。可靠修复应在通用线段捕获边界保留绘制当时的局部基底，并区分原版切向与新增截面参考；让该类 BER、普通轮廓等实际消费者使用同一规则。不按油门拉杆名称特化，也不把普通交互轮廓擅自改为无视深度的优先级轮廓。

### 离线反例与直接同类范围

算例固定局部线方向 `(0.2, 0.9, 0.3)`，比较“先生成截面再整体旋转”和“只旋转端点、仍用世界基底生成截面”。比较方截面四角的无序集合，已经排除正方形相差 90 度其实相同的情况。

| owner 绕 Z 旋转 | 现行单位基底角点误差 | 参考基底随 owner 旋转后的误差 |
| --- | --- | --- |
| 0 度 | 0 | 0 |
| 20 度 | 0.7641 | 小于 3×10⁻¹⁶ |
| 45 度 | 0.2405 | 小于 3×10⁻¹⁶ |
| 80 度 | 0.6931 | 小于 3×10⁻¹⁶ |

误差单位是棱柱半宽；不是游戏测量的位移，也不是截面尺寸改动。此反例证明现行转换在遗漏 owner frame 时不保持旋转等变性，不能代替真实拉杆动画截图。

直接同类源码中还有 `RopeStrandRenderer.renderOutline`：同样用 `RenderType.lines()` 输出交互轮廓，经普通 BER 捕获。它是相同基底遗漏的风险入口，不补写为用户已复现第二个物件。普通实体、显式 debug-line、Catnip Outliner 与模型轮廓已有各自捕获边界，本轮没有宣称所有线条路径已修复或全都损坏。

## 结论

两项均已定位到明确、独立的接管边界：加载覆盖层漏恢复混合状态，以及普通 BER 线条缺失绘制坐标基底。菜单闪白尚缺故障帧状态来直接定因；拉杆存在可离线证明的几何变换不一致，真实姿态下的表现仍待修复后对应验收。

本轮只调查并记录，没有修改产品、着色器、构建配置或测试；没有启动游戏、性能 A/B、重新打包或再次投放 Prism，也没有 Git 写操作。当前验收 JAR/DLL 身份保持不变。后续应分别补最小行为回归并修复两个边界，不能用一次菜单启动、普通静态拉杆或旧选中框测试代替各自的验收。

## 2026-10-15 授权修复与自动验证

上述调查之后，使用者授权修复两项并投放 Prism，不启动游戏、不提交或推送。以下为后续实施事实，不将前述调查阶段改写为已完成运行定因。

### 加载覆盖层状态收尾

`RadianceLoadingOverlay.render` 通过实际使用的 `renderWithStateReset` 接缝，在普通帧、淡出结束的提前返回和异常路径恢复 `defaultBlendFunc`、关闭混合与白色 shader color。原有绘制顺序、fade 时间与原生加载画面保持不变；收尾再次失败时作为 suppressed 保留，不能覆盖最初的渲染异常。

三项 Java 回归检查普通/提前返回后先恢复再允许下一次绘制、错误路径只恢复一次，以及原错误与收尾错误的关系。它们验证实际使用的控制流接缝，没有启动标题界面，不等于捕获到最初闪白的像素或证明悬停/焦点没有作用。首启菜单的视觉结果留待使用者。

### 通用线条捕获与转换

`StorageVertexConsumerProvider` 对受支持的四类线段模式采用 `CapturedLineVertexConsumer`。位置、颜色、原始切向仍保留，另以浮点列向量立即复制提交当时的完整绘制基底；支持 `PoseStack.Pose`、`Matrix4f` 位置入口及仅 normal 带 Pose 的入口。不从切向猜测截面旋转，不依赖方块、模组或纹理名称，不把普通 BER 轮廓改为优先级内容。

内部来源格式 `CAPTURED_LINES=14`、步长 56 字节，通过原有已检查长度的提交合同进入 native；没有增加 JNI 方法或修改 shader。原生使用捕获基底生成方截面，现有模型优先级轮廓的显式参考轴保留。完全没有 Pose/矩阵信息的原始坐标调用明确沿用现有 owner frame 回退，不虚构已经在任意第三方包装器中恢复丢失的局部姿态。

直接调用方检查同时补全 `EntityProxy` 与 `WorldMeshSink` 对该捕获器的结束/关闭处理。后者仍执行原有场景与图元准入，不因新增来源格式绕过原来的世界光栅隔离。原生数据仍按原有队列所有权复制，在途退休机制未改动。

合并四类线段的通用原生截面生成时，确认旧 `DEBUG_LINES` 分支错误套用 `LINES` 的四顶点重复布局；现在分别按真实输入布局选取端点。`LINES`、`LINE_STRIP` 保留 Minecraft 的重复顶点，两个 DEBUG 模式不重复；跳过零长度、非有限输入和全透明分隔线，不生成越界边。

### 自动证据与产物

首次定向构建被来源格式诊断标签数量的静态断言阻止，补入新格式的计数标签后重新构建成功；原始失败保留在 `targeted-java.log`，成功记录为 `targeted-java-2.log`。没有删除断言或放松检查。

本轮执行 `spotlessApply check verifyRelease :native_renderer:gpuTest verifyCompliance --strict` 成功。GAME 257 项、probe 26 项通过；bootstrap 11 项通过、2 项按原有显式环境门槛跳过；非 GPU CTest 66/66、GPU 16/16。JNI 头文件、发行包的 154 个必需条目及探针排除通过。严格合规失败 0、警告 0；Java 的已有 Unsafe/过时 API 与 Gradle 弃用提示不计为合规零警告。

新增四项 Java 捕获回归覆盖实际 Storage 入口、全基底/镜像与 Pose 可变性、同批次不同变换、原始坐标回退、仅 normal 带 Pose、缓冲增长、alpha、四种模式与缺失字段。原生行为回归验证旋转及镜像等变性、原路径反例、四种模式和边界输入。`java-native-lines-final.log` 记录将 Java 实际生成的二进制网格交给原生产品共用的截面函数解码/展开；它证明该数据接缝，不冒充实际 JNI→GPU 世界提交或视觉验收。

新 JAR 为 `6a26474b0c2e057a33ceacfd520034eb00de5561fd22e30a38e130e2a21388f6`，`core.dll` 为 `6704b26420419a99990fae0c7dc3e4febfd72883f9eb2eb9639421f5ed6e863c`。构建 DLL、安装 DLL 与 JAR 内嵌 DLL 一致，143 条运行库索引逐项校验通过；新捕获类进入 GAME，测试、探针与私有 Aftermath agent 未混入产品。

证据位于 `D:/Workspaces/Artifacts/Radiance/menu-line-fix-20261015-174944/`：定向与完整构建日志、`test-counts.json`、交叉接缝日志、源码清单、产物、包比较及 Prism 回执分别保留。构建后不再修改产品；随后只收尾维护记录。未运行客户端或性能实验，实际提取/加载 DLL 身份待用户启动后核对，不能提前写成通过。

### 手工验收范围

1. 正常启动，观察加载覆盖层退出与主菜单首次淡入，“单人游戏”按钮不应先闪白再恢复；移动鼠标产生的正常悬停高亮不算故障。
2. 在原报告的 sublevel 上观察油门拉杆，改变结构朝向及拉杆摆角。轮廓应贴合拉杆，线截面随绘制姿态转动，不再独立绕线轴旋转或突然翻面。
3. 对照普通世界的拉杆、方块选中框、绳索交互轮廓和已有模型轮廓；原有颜色、透明度、深度与优先级不应被改变。

只有使用者对应反馈才能关闭视觉项；历史性能观察、首次 GPU 故障根因及许可门禁保持独立。
