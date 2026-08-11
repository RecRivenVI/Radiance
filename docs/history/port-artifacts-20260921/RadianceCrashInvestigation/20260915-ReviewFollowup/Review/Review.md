# Radiance 崩溃证据独立复核

日期：2026-09-15。证据内运行时间均为 UTC+08:00。

## 结论

目前不能宣布已找到 `nvoglv64.dll+0xf1c729` 的根因。但调查已能收敛到具体代码，而不是继续泛泛怀疑 NVIDIA、Java 或整个移植工程。

本轮新增的关键发现：

1. 包内实际存在四次 A4 无诊断配置的同址崩溃，不只有交接首页强调的第一次。其中最后两次启动器记录退出码 0，却有匹配 PID 的新 fatal 日志。
2. UI 颜色附件的同步范围确实存在缺口：最终合成前的源 stage、重新开始 UI LOAD 前的目标 stage 都遗漏 `COLOR_ATTACHMENT_OUTPUT`。这与 A3 的 WAW/RAW 验证消息吻合，是可以独立修复的正确性问题；尚未证明它就是 CPU 访问异常的原因。
3. 资源寿命不应简单概括为“没有按帧保留”。源码已有逐帧 fence、旧 descriptor 表保留和 UBO 扩容保留。更具体的待验证点是：纹理释放是否可能发生在旧引用已经录制、但对应 UI 命令尚未提交时。
4. “创建 pipeline 时用的旧 pipeline layout 已销毁”不构成充分定罪证据；E-02 保留整个 descriptor 表的一次未崩溃也没有隔离出真正变量。

建议先分别处理可证实的颜色同步错误和结果分类问题，再核实真实运行中的资源代际与命令录制/提交边界。不要把所有变化混成一个“稳定性修复”。

## 1. 范围与证据可靠性

此次复核读取了 `00-READ-ME-FIRST.md`、交接索引与实验记录、A3/A4 的逐轮结果及对应 fatal、D06 保存的 WinDbg 文本、关键 C++/JNI 路径与源码冻结记录。上传 ZIP 的 CRC 检查通过。

未运行包内脚本、未运行 Minecraft、未修改业务仓库、未提交 Git。原始多 GB 内存转储和 DLL/PDB 二进制未随包上传，因此本报告引用已有调试文本，不声称重新打开原始 dump 完成了调试。

六次 A3/A4 运行记录中的 JAR、实际加载 core DLL 和 PDB 哈希一致：

```text
JAR  21CF6C1451B4021ADE6AEB53A9DFB931A40EFBE15B93C3D5D77FB34FC7B0D550
DLL  20F85170B43A822D93BFBD9FB12770CE5CCD78E2FAFC9B8B5E1B6AA62374BD89
PDB  E177D9DE386308D3B99F112244AC02C47B1E94E5BBB32F64F8824052C081B2BA
```

`source-freeze.json` 列出的 10 个文件，全部与上传源码快照的 SHA-256 匹配，其中包括 `ui_module.cpp` 和 `dynamic_pipeline.cpp/.hpp`。但是 `render_framework.cpp`、`textures.cpp`、`buffers.cpp` 并不在这份十文件冻结清单里；针对这些文件的静态结论，在映射到特定已运行 DLL 前仍需核对构建时源码。不能把“十文件匹配”夸大成“整个源码树与全部历史二进制完全一致”。

来源：`Evidence.md` 的 E01、E08；机器可读核对结果为 `verified-runs-and-freeze.json`。

## 2. 实际运行结果比首页总结更完整

以下启动时间均为 2026-09-15 UTC+08:00。崩溃耗时取自 hs_err 的进程经过时间，不包括启动器等待写转储的额外时间。

| 启动时间 | 配置 | PID | 结果 | 崩溃经过时间 | 记录退出码 |
| --- | --- | ---: | --- | ---: | ---: |
| 16:42:04 | A3：validation + sync | 74556 | 记录约 14 分钟，无新 fatal/dump | 不适用 | 0 |
| 17:13:02 | A4：无显式诊断配置 | 73724 | 同址 NVIDIA AV | 28.247 秒 | 1 |
| 17:37:16 | A3：validation + sync | 87964 | 同址 NVIDIA AV | 25.513 秒 | 1 |
| 17:55:21 | A4：无显式诊断配置 | 75500 | 同址 NVIDIA AV | 32.952 秒 | 1 |
| 18:22:46 | A4：无显式诊断配置 | 91500 | 同址 NVIDIA AV | 21.635 秒 | 0 |
| 18:24:12 | A4：无显式诊断配置 | 87940 | 同址 NVIDIA AV | 32.768 秒 | 0 |

五份 fatal 都报告 `nvoglv64.dll+0xf1c729`、读取 `0xffffffffffffffff`，Java 栈包含 `TitleScreen.render`。这说明异常签名相同，不等于每次是完全相同的绘制调用：例如 PID 91500 的 TitleScreen 字节码偏移为 +226，其余这些样本为 +146。

结论边界：

- 四次已提供的 A4 都崩溃，只能描述这四个观察样本，不是总体“100% 必崩”的概率估计。
- A3 既出现一次长时间无新 fatal，也出现一次约 25.5 秒的同址崩溃，已经推翻“开 validation 就稳定”的说法。
- A4 的 `VulkanLayers=[]`、`Validation=false` 证明运行配置未显式启用这些诊断；不等于系统所有隐式第三方层均被排除。
- 后两次 `ExitCode=0` 不能判成功。各自 `NewHsErr` 指向本次 PID，且对应日志明确包含 fatal 签名。无法仅凭这些记录断言退出码为何成为 0，但分类结果没有歧义。
- PID 87940 的 `NewDumps.Length=0`，不能把“存在 dump 路径”当成“已取得可用完整转储”。PID 91500 的 dump 则记录为约 5.16 GB。
- 各次用户操作没有完整同步记录，不能从 `TitleScreen.render` 自动推断操作完全相同。没有新的用户验收结论。

来源：`Evidence.md` E01，每轮保留了 run-result 和匹配 fatal 的原文件行号。

## 3. 可确认的颜色附件同步缺口

### 3.1 最终合成前遗漏颜色写入阶段

源码位置：`source/MCVR/src/core/render/render_framework.cpp:68–101`，`FrameworkContext::fuseFinal()`。

对 `overlayDrawColorImage` 从当前布局转为 `TRANSFER_SRC_OPTIMAL` 的屏障，使用：

```cpp
.srcStageMask = VK_PIPELINE_STAGE_2_FRAGMENT_SHADER_BIT | VK_PIPELINE_STAGE_2_TRANSFER_BIT,
.srcAccessMask = VK_ACCESS_2_MEMORY_READ_BIT | VK_ACCESS_2_MEMORY_WRITE_BIT,
```

但是这张图像之前作为颜色附件被写入。源 stage 没有覆盖 `COLOR_ATTACHMENT_OUTPUT`，不能靠宽泛的 `MEMORY_WRITE` access mask 弥补 stage 范围的遗漏。

实际链路包括原生加载画面：`loading_renderer.cpp:285–329` 向 UI 颜色目标绘制，并在 323 行结束 render pass；后续 `submitCommand()` 调用 `fuseFinal()`。这解释了为什么颜色同步错误可以在标题文字崩溃之前很早就出现。

### 3.2 UI 的 LOAD 前也遗漏对应消费阶段

源码位置：`source/MCVR/src/core/render/modules/ui_module.cpp:1661–1706`，`UIModuleContext::switchOverlayDraw()`。

颜色图像屏障的目标 stage 同样只有 `FRAGMENT_SHADER | TRANSFER`。紧接着开始的 render pass 使用颜色附件 `loadOp=LOAD`，配置见 682–733 行，需要把颜色附件读取/写入阶段及布局转换依赖正确纳入同步。

这里还有 render pass 自动布局转换，修复必须审查 barrier 与外部子通道依赖的配合，而不是只机械更换两处常量。

### 3.3 验证消息与源码相互支持，但没有完成因果证明

A3 首次运行日志明确报告：

- WAW：`vkCmdEndRenderPass` 的颜色写入与后续 `vkCmdPipelineBarrier2` 对图像的写入缺少同步。
- RAW：`vkCmdBeginRenderPass` 的颜色附件 LOAD，与先前图像布局转换之间缺少同步；消息指出许可范围在 fragment/transfer，而需要颜色附件阶段。

A3 重试再次出现 WAW。它发生在大约 17:37:20，而 fatal 是 17:37:42；不能将较早 hazard 的日志时间当成崩溃 draw 的直接现场。

因此，这是一项高置信度的代码正确性问题，与日志中的访问类型高度吻合；但没有 A3 同进程对象命名映射来为每个日志句柄逐一绑定源码对象，也没有修复前后重复实验来证明其导致 CPU AV。保留这一边界很重要。

修复的依赖目标应明确为：

| 交接 | 生产者/消费者范围 |
| --- | --- |
| 颜色附件写入 → 最终 blit 读取 | 源覆盖颜色附件输出/写；目标覆盖 transfer/blit 读 |
| 图像先前用途/布局转换 → UI LOAD 与绘制 | 源覆盖真实上一步使用；目标覆盖颜色附件输出/读写；与自动转换、外部子通道依赖协调 |

这不是建议所有屏障改成 `ALL_COMMANDS`，更不是建议每帧插入 `vkDeviceWaitIdle()`。应按真实生产者、消费者和图像子资源修复。

来源：`Evidence.md` E03、E04。规范依据：Khronos Synchronization Examples 的颜色附件同步及自动布局转换示例。

## 4. 更值得追查的寿命问题：还没提交的命令

### 4.1 已经存在的保护机制不能忽略

`Framework::acquireContext()` 在复用该 swapchain 图像的帧资源前，先等待 `commandFinishedFence`，再调用 `frameResourceRetainer().beginFrame(imageIndex)`；后者清理该帧保留对象。

`UIModule::refreshOverlayDescriptorTable()` 461–476 行在替换表后调用 `frr.retain(oldDescriptorTable)`。UBO 扩容代码也保留旧 buffer。因此，“一换表就立刻释放、完全没有 fence”的判断不符合当前源码。

### 4.2 一个具体待验证的路径

`Textures::releaseTexture()` 92–147 行，在非资源重载分支中：

```text
flushQueuedUploadImpl()
→ waitRenderQueueIdle()
→ 将该 texture id 绑定为 fallback
→ 从纹理及 sampler 容器 erase 原对象
```

`waitRenderQueueIdle()` 633–638 行只是 `vkQueueWaitIdle()`，没有提交当前 UI 命令。`flushQueuedUploadImpl()` 提交的是独立纹理上传命令，并不是当前 UI 帧。

同时，`DescriptorTable::bindSamplerImage()` 67–90 行只写 Vulkan 句柄，不在表对象中保留传入 image/sampler 的 shared_ptr。也就是说，旧 descriptor 表活着不自动等于其旧底层资源活着。

需要验证的时序为：

```text
当前 UI 命令已经录制一次引用纹理 T 的 draw
→ 这一帧还没有 submit
→ releaseTexture(T)，队列已经空闲，因此等待立即返回
→ fallback 替换容器绑定，旧 image/sampler 失去最后一份所有权
→ 后续继续录制或提交旧命令/旧 descriptor 表
```

队列空闲只覆盖已提交的工作，不保护上述尚未提交的引用。若应用允许这条时序，便存在需要修复的寿命缺口；若调用方能保证释放只发生在安全帧边界，则该假设不成立。

当前包没有证明本次失败前发生了这条时序，也没有证明某个涉事纹理失去了最后一个拥有者，因此不能将其宣布为已定位 use-after-free。还需核对该源码路径是否对应当前运行产物。

下一步针对性证据应是：释放调用的线程、帧槽、该帧是否已提交、被释放资源的旧代际、旧表是否仍引用它、保护该引用的具体 fence。比继续只查看“当前表中的 19/61 是 live”更有区分能力。

来源：`Evidence.md` E05。规范依据：Khronos `vkQueueWaitIdle` 与 Descriptor Sets 文档。

## 5. 不能继续沿用的过强推断

### 5.1 不能把旧 pipeline layout 的 destroyed 状态直接当作根因

Khronos 对 `VkGraphicsPipelineCreateInfo::layout` 的规定是：物理设备 API 版本不低于 Vulkan 1.3，或启用 `VK_KHR_maintenance4` 时，实现不得在创建调用结束后继续访问这个输入 layout 对象。

因此，需要区分“创建 pipeline 时使用的旧 layout”和“之后命令实际传入的当前 layout/set”。前者在验证层保存的关联状态中显示 destroyed，不自动证明应用违规。后者仍必须满足当次命令的有效性和兼容性要求。

E-02 保留的是整个 descriptor 表，连带改变 pool/set/layout 的释放和内存复用。一轮未崩溃不足以将变量缩小为 pipeline layout，更不能据此采用永久保留所有旧表作为正式修复。

### 5.2 当前资源看起来有效，不等于完整历史已排除

D05–D11 的隔离测试覆盖了具体 descriptor 形态、真实文字 shader、D32S8、动态状态、19/61 索引和纹理形状等条件。其负结果有价值，但只说明这些局部组合没有独立触发，未重放真实游戏完整的资源替换、命令历史、加载画面交接和帧槽复用。

A3 的两种结果进一步表明，一次未崩溃不能作为稳定性证明。继续增加几乎相同的 headless draw 循环，其区分能力可能低于补齐真实时序。

### 5.3 uniform 内容的归属还需核验

交接索引称“当前 mapped UBO 内为 sampler 61/19”。然而 `appendOverlayDrawUniform()` 432–460 行先把数据写入 CPU 暂存向量，`buildAndUploadOverlayUniformBuffer()` 521–542 行才在提交阶段上传；本次崩溃位于 draw 命令录制期间。

所以，仅从映射 buffer 某位置读到 61/19，不能无条件视为失败 draw 已准备的当前输入。应同时核对 CPU 暂存区、该 draw 的动态 offset、descriptor offset/range、所绑定 buffer 代际及上传进度。若此前证据已完成这组核对，61/19 的归属可以成立；当前报告不把“原文没展开”当成它一定错误。

这是一项证据解读限制，不是说“延迟到提交前上传 UBO”本身就是 bug。

来源：`Evidence.md` E06、E07。

## 6. 下一轮建议的最小任务

### 第一项：修正结果判定并冻结新基线

继续保留 R-01/R-02；不恢复 E-02 的全表保留；不回滚整份 working diff。将每次运行按 PID、开始时间、实际加载 DLL 哈希、新 fatal 和可用 dump 共同归档。退出码仅作字段，不能独立判成功；无新 fatal 也不自动等于用户验收通过。

这项改变不应修改渲染行为。

### 第二项：独立修复颜色交接同步

把 `fuseFinal()`、`switchOverlayDraw()` 及相关 render pass 外部依赖放在同一条颜色资源交接链中检查，保留既有 depth/stencil 修复。使用精确的 stage/access/subresource 范围，不用全局等待掩盖。

在同一冻结产物上重复真实游戏的 validation+sync 与无诊断运行，分别记录两个结果：

```text
颜色 WAW/RAW 是否清除？
原始 nvoglv64+0xf1c729 AV 是否仍出现？
```

hazard 清除而 AV 仍在，说明已修复一个独立 bug，但根因需继续找。两者同时消失也仍需足够重复运行和用户验收，不能靠一次启动宣布解决。

### 第三项：只围绕真实录制/提交边界补证据

优先从已有可用 dump 和本机匹配符号抽取小体积数据，或采用有界的真实调用记录；不要再次无上限输出完整 API dump。D06 的 API 详细记录仅覆盖 0–299 帧，而失败在第 1599 帧，不能把早期 API 片段冒充失败前的完整调用历史。A2 的 API-dump 层崩溃要与原始 NVIDIA AV 分开归档。

首要对象是旧 descriptor 表及其引用的 image/view/sampler/buffer 的代际、释放时间、命令录制状态和保护 fence；其次核实失败 draw 的 CPU uniform 暂存数据，而不只查看映射内存。若确认纹理释放在未提交帧中发生，再做围绕该引用的最小生命周期修复，不预先引入永久保留或全局串行化。

## 7. 最终判断

优先级是：**已证实的颜色同步错误 → 尚未提交命令的资源寿命 → 更精确的驱动内部状态关联**。

当前异常是 CPU 在 `vkCmdDrawIndexed` 录制调用内的访问异常，D06 文本显示 `r9=-1`，指令读取 `[r9]`。这定位了直接失败点，但没有回答驱动内部状态为何成为该值。栈中的 `vkGetInstanceProcAddr+大偏移` 是缺少私有符号时的标注，不能当成真正正在调用获取函数地址 API 的证据。

没有必要因为异常落在 NVIDIA DLL 就重新启动一轮无针对性的驱动重装、关闭文字或禁用渲染功能。现阶段也没有证据将这次 CPU AV 与历史上已经解决的 `VK_ERROR_DEVICE_LOST` 驱动问题合并。

## 规范来源

以下为此次核验使用的 Khronos 官方文档；用于判断 API 语义，不用于替代包内运行证据。

- Synchronization Examples：`https://docs.vulkan.org/guide/latest/synchronization_examples.html`
- VkGraphicsPipelineCreateInfo：`https://docs.vulkan.org/refpages/latest/refpages/source/VkGraphicsPipelineCreateInfo.html`
- VkSubpassDependency：`https://docs.vulkan.org/refpages/latest/refpages/source/VkSubpassDependency.html`
- vkQueueWaitIdle：`https://docs.vulkan.org/refpages/latest/refpages/source/vkQueueWaitIdle.html`
- Descriptor Sets：`https://docs.vulkan.org/spec/latest/chapters/descriptorsets.html`
