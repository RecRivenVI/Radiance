# Voxy 远景 PT 接入实验

2026-10-18：在 Radiance 工作区实施可选后端，Voxy 使用使用者确认与源码一致的本地构建；不修改 Voxy 或旧 MCVR，不进行性能测试。本记录区分实现、功能证据、失败修正和待使用者验收。

## 问题

Voxy 原渲染器和模型上传依赖 OpenGL，不能与 Radiance 的 Vulkan 窗口直接共用。目标是复用来源存储、摄入、软件模型投影和压缩区段，不重新生产完整通用顶点，并保持所选远景共同参与世界 PT。

## 方法

输入身份见[后端设计](../design/architecture-voxy_pt_backend.md#核查身份与测试输入)。条件 Mixin 只替换 GL 初始化、renderer 所有者和图集回读边界；真实 `ModelFactory` 与 `RenderDataFactory` 保留。工作线程使用源区段租约、短锁快照及版本拒收；JNI 同步复制压缩面和模型表。

原生按区段持有压缩面、16 字节位置、索引和 BLAS，按变化构建。不可变模型表、覆盖表、GPU 描述符及节点由实际帧引用退休；八叉树选择不依赖摄像机可见性。主世界以实际已发布近景与明确空版本遮罩远景，卸载交接保留有界的旧近景引用。GUI／离屏场景不追加主世界 Voxy 内容。

项目内[功能用例](../../validations/regression-voxy_integration/validation.md)使用静音 1920×1080、近景 4 区块和显式 512 方块远景，依次检查来源摄入、近远转换、重载拒收旧 epoch、Vanilla PT／Advanced 与保存退出／重进。此配置只服务功能用例，不覆盖产品或 Prism 设置。

## 发现

### 失败与纠正

- 第一次进入世界在 09:24:33 抛出 `UnsatisfiedLinkError: VoxyNative.begin`。原因是 SERVICE 已加载的库必须显式绑定 GAME JNI 类，新增入口漏登记；补入 `RadianceClient.GAME_NATIVE_OWNERS`，保留首次日志与崩溃记录。
- 第二次在 09:26:15 触发纹理上传边界拒收，未提交越界 GPU 工作。图集声明 3 个 mip，却上传 0–3 共 4 层；修正为 4 个 mip，边界检查保持不变。
- 第三次在 09:31:01 由既有地址使用检查拒收：新模型表及几何缓冲未声明 `VK_BUFFER_USAGE_SHADER_DEVICE_ADDRESS_BIT`。在 Voxy 自有分配处补齐用途，不放宽检查；失败发生在取得地址阶段，不是 GPU 访问错误或设备丢失。
- 来源 `ModelStore` 的纹理策略是最近邻 texel／线性 mip，适配层据此修正；无 mip 的来源记录显式限制到第 0 层，不读取未上传层。
- 原生 CPU CTest 已执行 72/72，GPU CTest 已执行 22/22；包括新生产解码器的六方向、缩进／尺寸、LOD 0／1／4、来源绕序及缓冲保护对照。Java 产品测试首轮 321 项无失败；末次完整检查另记。
- 第四次已完成 Vanilla PT 往返与重载，但用例没有启用 Advanced 所需的 `collectChunkEmission`，探针拒收选择。仅修正用例配置，不以它指认产品故障。
- 第五次 PID 32008 完成 Vanilla PT 远景、返回和重载，09:42:36 切换 Advanced 后发生 CPU 访问违例。Windows Application 1000／1001 记录 `nvoglv64.dll` 32.0.16.1714、`0xC0000005`、偏移 `0x140178`；对应 core SHA-256 为 `370791A3A34456D669C7572537824740C7609FBA0078AC4039064EA60F7E497C`。没有 `VK_ERROR_DEVICE_LOST` 或本次 GPU 转储，所查时间窗口也没有 Display／nvlddmkm 重置事件。WER 只保留 Report.wer，临时 CPU 转储已不在；不能认定 GPU 根因或将旧关闭日志归给本次进程。
- 故障后离线检查实际留存的 313 个 SPIR-V，均通过 `spirv-val --target-env vulkan1.3`。随后将 ray hit 内模型表改为平坦 uint 字段读取，避免按值复制整份数组结构；这是缩小着色器代码生成复杂度的候选措施，不是已证明的访问违例根因修复。
- 单独的 Advanced 菜单检查 PID 40212 实际加载 SDK 1.4.341.1 Core Validation，进入标题界面后在 09:57:36 正常退出，没有进入世界。验证层报告 SDK 内部着色器所需的 `ComputeDerivativeGroupQuadsKHR`／`SPV_KHR_compute_shader_derivatives` 未启用（08740／08742），因此此次不能记为验证通过。已在 SERVICE 实际设备创建处按支持情况查询并启用 KHR 扩展与 feature；此 API 缺陷成立，但尚不能证明导致前述 CPU 访问违例。
- 第一次完整配套检查被发行元数据测试阻止：可选兼容项从 10 增为 11，测试的固定数量未同步。更新数量，继续保留实际输入模组 ID、嵌套版本与元数据逐项相等的检查。

### Advanced 检查纠正与接口定位

10:15 的菜单进程 PID 99628 已启用导数 feature 且未报告该项错误，但实际 SPIR-V 索引仍是 Vanilla PT。原因是测试准备脚本把持久属性类型写成 `path`，真实属性是 `string`，因此该覆盖被忽略。撤回此前“Advanced 菜单通过”的判断；09:57 的菜单检查也不能证明 Advanced，导数 API 缺陷及其在 Vanilla PT 的纠正证据分别保留。已修正配置类型，并使菜单探针断言实际选中的 Advanced，不能只根据目标配置或探针标签作结论。

随后完整世界用例在 10:18 再次因 `nvoglv64.dll` 的同偏移 CPU 访问违例失败，core 为 `CA69B65D27132C39B8F396D3B312652918D6538BB3B459C637A4BF33CC734232`。这表明导数 feature 修正及平坦模型表读取均不足以解决该故障；没有设备丢失或 GPU 转储，不继续盲目重跑世界。

10:24:50 的 PID 60520 改为真实 Advanced、无世界的 Core Validation 检查，并增加仅项目诊断开启时输出的 CPU 管线创建首尾记录。验证层在 `vkCreateRayTracingPipelinesKHR` 报告 `layout-07988`：raygen 静态使用 set 1／binding 10，实际布局却只允许 closest-hit、any-hit 和 compute。随后仍发生同一 CPU 访问违例。该次尚未进入世界，因而不需要远景几何或场景上传即可触发；此处已确认 API 合同缺陷，但不据此关闭此前历史 GPU device-lost 根因。

修正实际描述符布局的缺失 raygen 权限，保留其他阶段和原有资源寿命。离线检查本次真实 SPIR-V 的 set／binding 与阶段，避免凭猜测扩大所有描述符权限。额外的 CPU 调试器只附加本次隔离 PID，不注入故障；捕获到自然未处理的 CPU AV，但 `MiniDumpWriteDump` 返回 `0x800703e6`，文件为 0 字节，不能称为取得有效 CPU 或 GPU 转储。原始 JSON 和失败日志保留。

10:29:28 的 PID 90756 使用修正后的 core `79823F29EC343C840D6BBFC2395D3FA3541CBDF8119B61AA0BCF6F2A654714DA`，实际加载验证层及 Advanced 的完整管线，通过实际选中断言，于 10:31:08 正常退出。没有 `07988` 或其他验证错误；Streamline 的 03328 INFO 是删除重复 BDA 扩展的说明，不是该次验证失败。该结果只证明无世界的创建合同，不能代填世界运行。

之后的世界进程 PID 90600 已成功完成 Advanced 管线创建及真实渲染，但在 10:37:04 的退出步骤发生栈溢出。`hs_err_pid90600.log` 显示反复循环 `VoxyWorldProbe.poll → Minecraft.disconnect → updateScreenAndTick → runTick → poll`，驱动函数只是栈耗尽时所在的位置；这与前述 `0x140178` 访问违例及历史 GPU device-lost 分开记录。探针在调用 disconnect 前没有更新阶段，进度页重入时重复退出；新增“关闭中”状态前置及实际 Runnable 接缝的重入行为回归，不修改产品退出语义。

该次 Advanced 截图时只完成 4 个测试模型，因此不将它认定为全部代理材质验证。用例进一步等待固定输入对应的 7 个模型，并在重建后的帧实际返回后才开始 12 秒观察，避免把同步管线编译耗时误当成场景停留。重新执行的结果另记，旧截图和栈溢出日志保留在独立目录。

### 修正后的功能流程

PID 50624（10:39 启动）使用相同 core `79823F29…714DA`，未开启重型验证。日志 `build/reports/voxy-functional-complete.log` 对应以下实际阶段；完整计数在 `validations/regression-voxy_integration/result/voxy-functional.json`。

| 本机时间 | 实际完成 |
| --- | --- |
| 10:40:48 | Vanilla PT 初始场景，7 个模型，296 个选中节点 |
| 10:41:10 | 到远处实际构建并检查 glowstone 来源、摄入与网格 |
| 10:41:32 | 返回原处，400 个驻留节点／298 个选中节点／1,586,912 几何字节 |
| 10:41:33 | 重载新 epoch 2，旧 epoch 更新被拒收；7 个模型已重新就绪 |
| 10:41:49 | Advanced 实际返回画面后继续观察，7 个模型、304 个选中节点、330 次 CPU 已记录构建 |
| 10:41:52–53 | 玩家与世界保存，CPU 网格线程停止、来源租约归还，菜单断言 session 已关闭 |
| 10:41:56–57 | 重进同一世界，epoch 3／7 个模型，探针请求正常停止 |
| 10:41:57 | 第二次保存、session 释放、native 正常关闭、Aftermath 正常 disable，Gradle 返回 0 |

没有该次 device-lost、访问违例或栈溢出。截图确认近景前方的缓存远处结构实际进入两种管线画面；不据此认定全部透明、折射、接缝、反射／阴影视觉等价。几何字节不包含图集、AS、SDK 或整进程显存；构建计数不是 GPU 完成次数。

随后未安装 Voxy 的 `smoke-render_diagnostics` 实例 PID 114244 到达标题界面并正常关闭，日志为 `build/reports/voxy-absent-smoke.log`。没有加载 Voxy 模组或启用其后端；输入检查仍会列出构建输入，不能把输入名称当作已加载模组。只证明该配置下的可选缺席启动，不代填无 Voxy 世界内验收。

### 实现边界

首版每个 Voxy 节点一份 non-opaque geometry，覆盖与 alpha 在 any-hit 中判定；其他世界几何快速路径不变。位置保留现有 16 字节 ABI，未实现设计中更紧的 float3 或共享索引容量。不将这些数据布局称为性能改善。

代理使用来源归并后的颜色、biome、alpha、自身 emission 和流体分类，不恢复源有意舍弃的 PBR 与动画。发光通过实际 PT 命中及间接传光产生贡献；尚未接入 Advanced 的独立远景光源重要性候选，不能宣称远景 NEE／ReSTIR 覆盖已完成。世界雾使用来源距离映射到现有 PT 雾，不声称重现来源 GL 最终合成的逐像素淡出。

## 结论

Radiance 侧核心接入已实现，限定功能流程、构建与自动检查通过，已于 10:57 投放授权 Prism，未启动该实例。视觉、长期运行与性能不因编译或计数通过而代填；上文 NEE／ReSTIR 和精确雾淡出的实现边界保留。

最终检查执行 `spotlessApply check verifyRelease verifyCompliance --strict`，成功，严格模式失败 0／警告 0。最后一轮复用已实际构建、安装并运行验证过的原生结果，排除重复的 `configureCmake`／`buildNative`／`installNative`，没有排除检查或测试；原生源码自最后安装后未修改，内嵌与实际加载 core 均逐字节核对。

| 验证 | 实际结果与边界 |
| --- | --- |
| Java 产品测试 | 322，失败 0 |
| 探针测试 | 35，失败 0，含退出重入反例 |
| 引导测试 | 13，失败 0；其中 2 项可选引导 GPU 测试未启用，不记作通过 |
| 原生 CPU CTest | 72/72，含 JNI、资源退休及 Voxy cut／编码合同 |
| 原生 GPU CTest | 22/22，含实际 Voxy compute 位置解码 |
| 实际留存 SPIR-V | 321 个均通过 SDK 1.4.341.1 的 `spirv-val --target-env vulkan1.3`；只描述留存模块 |
| JNI／发行包 | 头文件一致，155 个必需条目与探针排除检查通过；Voxy 本体没有内嵌 |
| 真实运行 | 上述两个 PT 的完整用例及可选缺席菜单通过；没有性能实验，没有制造 GPU 故障 |

交付身份：

| 内容 | SHA-256 |
| --- | --- |
| 完整发行 JAR（164,758,440 字节） | `B798CA9BFCECED73B85DE1CC546BC62AD8236C4A7CBA4E7A85A92DF2044CBA98` |
| 安装、JAR 内嵌、项目内提取及功能进程实际加载 core.dll | `79823F29EC343C840D6BBFC2395D3FA3541CBDF8119B61AA0BCF6F2A654714DA` |
| 独立 Voxy JAR | 仍为输入登记的 `182d4a19…c86932`，未修改或重建 |
| 产品、测试和组件文件聚合 | `ea1d058c6b142afafdfda0e666d1ab4597361877e548a5480f761c3cd461cb6c` |
| 非文档构建输入聚合 | `e85bd322ca6b2e63db44bf713bd7a59a0da126504ce2bf48406a937b95dce5d6` |

完整文件清单含必要未跟踪源码、JNI、shader、测试、构建配置和固定子模块身份，位于 `validations/regression-voxy_integration/result/source-manifest.json`。文件按磁盘原始字节取 SHA-256，不规范化内容；聚合按 Git 相对路径排序，依次拼接路径 UTF-8、NUL、十进制字节数、NUL、小写内容哈希及 LF。文档不进入构建输入聚合，完整清单仍保留文档各自哈希；子模块工作树干净，暂存区未改变。

Prism 回执为 `E:/Minecraft/PrismLauncherDev/.agent-runs/radiance-voxy-20261018-105713-83d8606b/receipt.json`，项目内副本为本用例 `result/deployment-receipt.json`。已核对实例 Minecraft 1.21.1／NeoForge 21.1.259、停止状态、暂存哈希及替换后哈希；其他模组未改变，没有投放探针。旧 Radiance JAR 留在实例 `.radiance-deployment/before-radiance-voxy-20261018-105713-83d8606b/` 供待验回退，锁已释放。Prism 未启动，因此没有本次 Prism 新提取／实际加载 DLL 的证据，不用项目内加载记录冒充。

完成的旧运行中，与当前安装结果 SHA-256 相同的重复 DLL 已按清单送入回收站；不同的故障 core、实际 SPIR-V 和文本保留，不删除唯一故障证据。源清单、各阶段日志、SPIR-V 结果、发行身份和投放回执分别保存，不提交、不推送，公开二进制许可门禁不变。
