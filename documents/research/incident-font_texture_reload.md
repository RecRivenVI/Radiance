# F3+T 后字体纹理状态异常

2026-10-17，使用者在阶段 A 候选的连续目视观察中，于一次 F3+T 后崩溃。本次只读检查运行证据和直接调用链，保全原始文件，没有修改产品、构建、启动或重现故障。

## 问题

首个致命异常为 `IllegalStateException: Configure Vulkan texture filtering: The given texture id 305 has no initialized sampler`。它发生在资源重载后的聊天文字绘制中；当前日志没有 `VK_ERROR_DEVICE_LOST`，不能合并为此前 GPU 故障的再次复现。

## 方法

读取授权 Prism 实例的完整 `latest.log`、`debug.log`、本次崩溃报告及设置，核对安装 JAR 和磁盘运行库缓存的 SHA-256；以 Minecraft 1.21.1 本地参考源码、NeoForge 21.1.259 源码及当前接管代码追踪字体、批次、纹理分配、过滤与重载。

五份原始文件复制到 `D:/Workspaces/Scratches/RadianceSemanticsAudit/prism-f3t-20261017-022514/`。`evidence-manifest.json` 记录来源、时间、大小与 SHA-256；复制前后来源哈希和副本哈希全部一致。未复制世界或新增二进制副本，没有自动重启。

## 发现

### 身份与时间线

- 本次进程日志从 02:16:58 开始，F3+T 于 02:25:10.617 开始，02:25:13.183 首次抛出异常。崩溃报告为 `crash-2026-10-17_02.25.14-client.txt`，记载手动重载第 2 次、尚未完成。
- 安装 JAR 为 `5DCBAFE1E038F182F70F833F76F94E9194A50D6A6BC6479E74441B04F1C2C339`，与阶段 A 投放一致。磁盘提取 DLL 为 `04DA0F2DF818EFB6288FFAB20AA758D1C208A6AD9B2E6A9808DE98853D8457D6`；没有本进程存活时的模块采样，不把缓存哈希扩大为新增的实时加载证明。
- 世界为 Newport City。FG 关闭，独立发光收集关闭；设置为 DLSS RR、Balanced、SHARC、Reflex 模式 2。崩溃报告实际窗口为 3840×2054，启动参数为 2560×1440，Java 为 BellSoft 25.0.4。这些是本次条件，不自动归因于差异。
- 02:25:12.687–13.115 重建纹理图集，13.144–13.150 完成多项 Veil、Create 与加载器回调；随后聊天绘制在切换文字批次时触发纹理过滤异常。
- 02:25:13.564 明确记录全部维度保存完成，14.189–14.203 写出崩溃报告。没有本次 native close-return 的直接证据，不将世界保存等同于原生关闭完成。

### 已确认的失败边界

调用链为 `ChatComponent.render → Font → BufferSource.getBuffer/endBatch → RenderType.draw → TextureStateShard → AbstractTexture.setFilter → TextureProxy.setFilter → Textures::setSamplingMode`。NeoForge 的 `CustomizableTextureState` 只更新过滤参数后调用父类；当前堆栈没有进入线条展开。

原生 `allocateTexture` 创建图像和 sampler 均为空的槽位，`initializeTextureImpl` 才创建二者；`setSamplingMode` 对不存在的 sampler 项和空 sampler 统一抛出本次错误。因此已确认过滤请求遇到了原生未就绪或失效的纹理状态，尚不能仅凭该消息区分两者。

字体重载关闭旧 `FontTexture` 并清空字体页；其 `close()` 释放 ID，但不会自行从 `TextureManager` 删除对应注册项，且 `FontTexture.load()` 不重新创建存储。`AbstractTexture.getId()` 可再次分配空槽位。若尚存的批次或其他旧字体页引用随后使用该对象，就能进入本次过滤前置条件失败的路径。这是源码支持的候选机制；日志没有纹理 305 对应的资源位置、代际和创建/释放事件，不能宣称已证实其完整因果链。

将 `textures.cpp`、`TextureProxy.java`、Vulkan `AbstractTextureMixins.java` 和 `ResourceReloadCoordinator.java` 与阶段 A 修改前的 `files.json` 比较，四者内容哈希均未变化。此事实说明该失败前置条件已存在，不能据此排除新调用时序的影响，也不能因为堆栈列出 BufferSource 上的 Mixin 就断言由线条修改导致。

### 证据限制

本次 Prism 未使用项目内默认诊断 agent，没有 Aftermath 接通或 GPU 转储记录。现有旧 `hs_err` 不属于本次进程。Java/JNI 异常足以解释本次崩溃，无依据将其升级为设备丢失、驱动重置或显存耗尽。

使用者反馈连续观察了大部分情况，未列出逐项通过清单；只记录该反馈，不补填 Create、Sable、屏幕效果或其他具体项目通过。原自动用例的资源重载成功仍保留其原证据范围，不能覆盖本次失败。

## 结论

阶段 A 当前候选的 F3+T 手工验收失败，阻塞该项通过；其他视觉反馈与 GPU 根因保持独立。产品和候选身份没有变化，本次没有执行测试、重建、部署、提交或推送。

后续定向修复应先确定旧字体页或批次的引用边界，并区分纹理名称、采样参数与图像存储的生命周期；不能只吞掉异常或补建 sampler 后继续消费未初始化图像。相应回归应覆盖多页字体、可见聊天内容与重载、初始化前过滤状态、旧代际拒绝及正常图像上传，不重新开启无关渲染整改。
