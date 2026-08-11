# E-02 重测：pipeline 创建时使用的 descriptor table/layout 保活

状态：A/B/A 对照完成，结果明确正向，但**仍不是已确认修复**。当前工作树已回退 E-02（源码与 `before` 一致），部署实例=最后一段 A2 基线回退版（JAR `760444D2…`，内嵌 DLL `1C1348B5…`）；E-02 源码保留在 `e02-source/` 目录，未提交。

## A/B/A 对照（2026-09-17，用户在场并实际操作）

| 段 | 部署 DLL SHA-256 | 运行 | 结果 |
|---|---|---|---|
| 原始基线对照 | BF6E6A18（冻结基线） | baseline-run1 | 崩溃，0xf1c708 读 0x104，51.5 s |
| A1（回退并重编译） | 1643D241 | A1-run1 / A1-run2 | 2/2 崩溃：0xf1c729 读 -1（52.9 s）、0xf1c708 读 0x104（40.4 s） |
| B（E-02 重编译） | D5E3E0A9 | B-run1 / B-run2 / B-run3 | 3/3 无 hs_err，各 300 s；进入世界 |
| A2（再次回退并重编译） | 1C1348B5 | A2-run1 / A2-run2 | 2/2 崩溃：0xf1c708 读 0x104（94.8 s）、0xf1c729 读 -1（29.5 s） |
| 早前 B 段 | D7FE273E | e02-run1/2/3 | 3/3 无 hs_err，240/300/300 s |

合计本会话：基线（含原始基线）5 次运行全部崩溃；E-02 6 次运行全部未崩溃。用户确认 B 段运行期间由其在世界内压测（tick 率 200→10000 每秒）。

### 自动进入世界（测试辅助）

- 用户要求后续测试直接带进入世界参数。已用 init script（`quickplay.init.gradle`）给 `packagedClient` run 追加 `--quickPlaySingleplayer "Radiance Rebuild Verification"`，不改动仓库文件。
- 注意：Gradle 启动 JVM 默认是 Java 25（Liberica），新 init script 会因 Groovy 不支持 class file 69 编译失败；必须用 `JAVA_HOME=C:\Users\RavenYin\.gradle\jdks\eclipse_adoptium-21-amd64-windows.2` 启动 Gradle。
- 启动方式：`Run-Observe.ps1 -RunName <name> -GradleArgs '-I','<init script>'`。
- 已在 `quickplay-test3` 验证生成的 `packagedClientRunProgramArgs.txt` 末尾出现该参数；该次因基线构建在标题界面即崩溃，未观察到实际进入世界。

## 背景

- 2026-09-15 的 E-02 实验（`20260915-A\Evidence\e02-implementation.diff`）把创建 pipeline 时的 `DescriptorTable` 交给 `DynamicGraphicsPipeline` 持有，使该表的 `VkPipelineLayout` 在 pipeline 存活期内不被释放。
- 该版本在 `run-20260915-143633-077` 中一次未崩溃（带 validation，用户反馈“这一版看起来没什么问题”），此后未再重测；9/15 之后的阶段改做了 R-LIFE-02（descriptor 表底层资源保活）和 9/17 的单点回退实验。9/17 交班记录未包含 E-02。
- 本次按原始 diff 忠实重做，并在同一会话内先做基线对照，再连续 3 次运行。

## 改动内容

见 `e02-change.diff`（3 个 hunk，仅 `dynamic_pipeline.hpp/.cpp`）：

- `DynamicGraphicsPipeline` 增加 `std::shared_ptr<DescriptorTable> experimentDescriptorTable_;`
- `DynamicGraphicsPipelineBuilder::definePipelineLayout` 保存传入表指针
- `build()` 把表指针转移到生成的 pipeline 上

未使用 wait/强制同步/delayed release/跳过绘制，未改变 descriptor 内容、UBO 内容或绘制顺序。

## 构建与产物

| 项目 | 值 |
|---|---|
| 原生构建 | `cmake --build MCVR/build --config Release --target core --parallel` PASS；日志见 `e02-native-build.log`，明确出现 `dynamic_pipeline.cpp` 编译 |
| 实验 DLL | `e02-core.dll` SHA-256 `D7FE273EA5C12F4F734115CFAEE93E7A4D9D408E2E2EB740028508D251739144`，30971904 bytes；`e02-core.pdb` 同目录 |
| 部署实例 JAR | `run\packaged-client\mods\Radiance.jar` SHA-256 `285DD418D3FDDE22C9C1497A3B4572723F23CA603D598E92D62E096B9B5847D8` |
| JAR 内 `core.dll` | 与 `e02-core.dll` 一致（D7FE273E…） |
| JAR 内 `radiance-game.jar` | 895 个条目内容与基线 `radiance-game.jar`（3AD4A0E6…）逐项哈希一致，仅 zip 元数据不同（由本次与基线重打包一致验证） |
| Gradle | `preparePackagedClient verifyDistributedJar` PASS（`e02-package.log`） |

## 运行结果（同一实例、无 trace、无 validation、Java 21 Temurin 21.0.11）

| 运行 | 结果 | 说明 |
|---|---|---|
| 基线对照 `baseline-run1` | **崩溃** | 实例 `2F79…`JAR / `BF6E…`DLL；elapsed 51.52 s；`nvoglv64.dll+0xf1c708` 读 `0x104`；Java 栈 `ShaderProxy.draw → Gui.renderSlot → Gui.renderItemHotbar` |
| `e02-run1` | 240 s 无 hs_err，仍运行 | 进入世界（06:21:40 join）；06:22:00 出现玩家指令 `Set the target tick rate to 200.0 per second` |
| `e02-run2` | 300 s 无 hs_err，仍运行 | 进入世界；出现 200 → 10000 tick/s 压力指令与 `Can't keep up` 警告 |
| `e02-run3` | 300 s 无 hs_err，仍运行 | 进入世界；出现 10000 tick/s 压力指令与 `Can't keep up` 警告 |

三次 E-02 运行期间实例目录内没有新增 `hs_err_pid*.log`；运行结束由 `taskkill /PID <gradlew> /T /F` 终止本任务进程树，会话中既有的无关 java 进程（PID 41712/51812/53160/55456）未受影响。

## 证据边界（不要写成已修复）

1. 本次为同一时间窗内的 1 次基线对照 + 3 次实验；加上 9/15 的一次非崩溃，共 4 次观察。仍不足以排除偶发性；历史上有过带 validation 的 841 秒无崩溃与随后 25 秒崩溃的对照。
2. 运行 1 中游戏在进入世界后很快出现 `Saving and pausing game...`（窗口失焦自动暂停）；运行 2 的 `Can't keep up` 提示存在暂停/恢复与服务器压力两种来源。暂停画面与标题界面同样走 `ShaderProxy.draw` 文字路径，但“HUD/热键栏是否持续绘制”没有被独立确认。
3. 运行 2/3 的 10000 tick/s 压力是显著时序扰动，可能独立于 E-02 抑制崩溃；这与历史上“诊断改变触发条件”的教训同类。
4. E-02 改变的生命周期变量是 pipeline 创建时的 layout/表；`VkGraphicsPipelineCreateInfo::layout` 在 maintenance4 下不要求创建后存活，因此本实验在规范层面可能只是规避了驱动的实现行为，尚需最小化验证（例如只固定专用的稳定 layout，或让 UIModule 持有一个不再销毁的 layout/shader-pipeline 模板）。
5. 未重新开启 validation/sync 对照；未验证颜色同步 hazard 是否仍为零。

## 最小化修复（layout 保活）与 A/B/A 验证

E-02 固定整张 descriptor table（连带 pool/set/资源）；最小化版本只让 pipeline 持有创建时 `VkPipelineLayout` 及其 descriptor set layouts 的强引用，不再固定表本身：

- `Source/minimal-fix/after/descriptor.hpp|.cpp`：`DescriptorTable` 新增 `pipelineLayoutKeepAlive_`（`shared_ptr<void>`，内部 `PipelineLayoutOwnership` 负责销毁 layout 与 set layouts），析构不再手工销毁它们；新增 `pipelineLayoutKeepAlive()`。
- `Source/minimal-fix/after/dynamic_pipeline.hpp|.cpp`：`DynamicGraphicsPipeline` 与其 builder 各持有该 token，`definePipelineLayout` 取用，`build()` 转移给 pipeline。

自动进世界对照（同一实例、无 trace，`quickplay.init.gradle`，Gradle JVM=Java 21）：

| 运行 | 部署 DLL | 结果 |
|---|---|---|
| A2（基线） | 1C1348B5 | 2/2 崩溃（94.8 s 读 0x104；29.5 s 读 -1） |
| B'（最小化修复） | CE503195 | 3/3 无 hs_err；入世界后观察 269.6 / 271.5 / 271.2 s，全部由超时 taskkill 结束 |
| A3（再次回退基线） | D445751C | 2/2 崩溃：0xf1c729 读 -1（47.4 s、36.3 s），标题界面阶段 |

本会话总计：基线（原始/A1/A2/A3）7/7 崩溃；E-02 6/6 存活；最小化修复 3/3 存活。B'/A3 均在无人操作、自动进世界条件下取得，说明最小化 layout 保活足以复现 E-02 的效果。

证据边界：仍是同一会话、单机、无 validation 对照；30 秒级标题崩溃窗口被跨过，但更长的世界/模组场景与用户验收未做。B' 三次均由强制终止结束，**未验证正常退出路径**（后续补测见下）。当时工作树已回退最小化修复（基线），修复源码在 `Source/minimal-fix/after`。

## 重新应用与 validation 运行（2026-09-17 07:45+）

按用户选择，最小化修复已重新写入工作树并部署：

| 项目 | 值 |
|---|---|
| 重编译确认 | `descriptor.cpp`、`dynamic_pipeline.cpp` 均实际编译，链接成功 |
| 部署 DLL | `deployed-core.dll` SHA-256 `CA5C80C2A32FDC675260F2DF2C6E4F257ED54D47E627C2D23DCD81F5BE8009FD`（与 B' 的 CE503195 同源，链接元数据不同） |
| 部署 JAR | `2473D3F568F101F7F76EF185B8BD2B6122ED79B9BD3E5FD82FEE6A85E73A0914`；内嵌 DLL 与部署 DLL 一致 |
| validation 运行 | quickplay 自动入世界（07:49:06 join），入世界后观察 570.2 s 无 hs_err，由超时 taskkill 结束；加载 DLL `CA5C80C2…` |

validation 设置：`khronos_validation.validate_sync=true`、error/warn、duplicate_message_limit=3（`minimal-fix\validation\vk_layer_settings.txt`）。消息计数上限在 3 条后提示“this will be the last time reporting”，因此**不能认为总共只有 3 条**。已报出的三条 `SYNC-HAZARD-WRITE-AFTER-WRITE`（同一 MessageID 0x5c0ec5d6）为：

1. `vkCmdCopyBuffer` 写 VkBuffer `0x49d…`，与另一 `vkCmdCopyBuffer` 之间缺少 `TRANSFER_WRITE/COPY` 依赖（copy region offset 0，size 12）——见 stdout 第 217 行。
2. `vkCmdPipelineBarrier2` 对 VkImage `0x1fa…` 做布局转换，与先前 `vkCmdEndRenderPass` 的颜色附件写入（`COLOR_ATTACHMENT_WRITE` @ `COLOR_ATTACHMENT_OUTPUT`）之间缺少同步——这是颜色附件类 hazard，见 stdout 第 229 行。具体 pass 已在 2026-09-17 09:05 定位并更正，见下文“颜色附件 WAW 的具体定位”。
3. 另一命令缓冲上的同类 buffer copy WAW——见 stdout 第 243 行。

待定位事项：第 2 条是否属于 UI 路径、是否同样存在于基线，均未确认；不能在摘要里写成“全部是 buffer copy、与颜色附件无关”。该 hazard 与最小化修复（仅改 layout 生命周期）无直接关系，应单独跟踪，不要并入这四文件修复。

## 正常退出验证（2026-09-17 08:23–08:28）

`normal-exit-run2`：quickplay 自动入世界（08:23:47 join），入世界后观察 240 s，随后对命令行可验证的游戏进程（PID 55808，`net.neoforged.devlaunch.Main @…packagedClientRunProgramArgs.txt`）调用 `CloseMainWindow()`：

- Gradle 退出码 **0**，`GracefulClose.ExitedAfterClose=true`，无 hs_err，无强杀（`KilledVerifiedPids` 为空）。
- 游戏日志出现正常关闭序列：`Stopping!` → `Stopping singleplayer server` → `Saving worlds` → 各维度 `All chunks are saved`（08:27:48–49）。
- 从 join 到 Gradle 退出共 247.3 s；加载 DLL `CA5C80C2…`。

## 颜色附件 WAW 的基线对照（2026-09-17 08:31–08:33）

为单独追踪 validation 中报出的颜色附件 hazard，回退到基线（重编译 DLL `6CAC016F…`），使用同一份 validation 设置与 quickplay 自动入世界：

| 运行 | 结果 | hazards |
|---|---|---|
| `baseline-validation\run1` | join 08:32:20，24.8 s elapsed 后 `nvoglv64+0xf1c729` 崩溃 | 与修复运行**完全相同**的 3 条：两条 `vkCmdCopyBuffer` WAW（同一 VkBuffer `0x49d…`）+ 一条 `vkCmdPipelineBarrier2` 颜色附件 WAW（图像句柄 `0x1ee…`，修复运行为 `0x1fa…`），行号同为 217/229/243 |
| `baseline-validation\run2` | 21.8 s elapsed 标题阶段崩溃 | 0 条（未及触发） |

结论：该 3 条 hazard 在基线上已存在，不是最小化修复引入的；颜色附件 WAW 属于独立的既有同步问题，继续单独跟踪（是否属于 UI 路径仍未定位）。另：validation 未阻止基线崩溃（run1 在 validation 启用下仍崩）。

## 颜色附件 WAW 的具体定位（2026-09-17 09:00–09:05）

临时诊断（已撤回；源码归档在 `waw-attribution\diag-source\`，由 `MCVR_RENDER_DIAG_PATH` 开启）：记录每个 framebuffer 的附着图像句柄、每次 `beginRenderPass`、tone mapping HDR 屏障图像，以及 world 共享图像 ↔ 模块/角色/索引映射。

诊断运行（最小化修复版 + validation + quickplay，150 s 存活）结果：

- validation 报告的 hazard 图像 `0x1e100000001e1` 映射为共享图像 **imageIndex=20 = `tone_mapping.mapped_output` = `post_render.ldr_input`**（2560x1440，R8G8B8A8_UNORM；修复 validation 运行中的 `0x1fa…`、基线运行中的 `0x1ee…` 是同一逻辑图像的不同次分配）。
- 写它的 pass：**tone mapping 模块的最终 render pass**（`tone_mapping_module.cpp:588`；诊断日志中 `framebuffer=0x263 attachments=0x1e100000001e1`），随后 `endRenderPass`。
- 冲突屏障：**`post_render_module.cpp:1442-1455`** 将 ldrImage 从 `COLOR_ATTACHMENT_OPTIMAL` 转为 `TRANSFER_SRC_OPTIMAL`，其 `srcStageLdr` fallback = `RAY_TRACING|COMPUTE|TRANSFER`，缺少 `COLOR_ATTACHMENT_OUTPUT` → `SYNC-HAZARD-WRITE-AFTER-WRITE`。
- **更正此前记录**：该颜色 hazard 不是 tone mapping 的 HDR 屏障（HDR 屏障作用于 imageIndex=16 = DLSS `processed`，诊断显示其 oldLayout=GENERAL，掩码为 src COMPUTE|RT|TRANSFER / dst FRAGMENT|COMPUTE|TRANSFER）；实际冲突点是 post_render 的 LDR 屏障。属独立既有同步问题，继续单独跟踪，不并入四文件修复。

同一 validation 运行还报出此前未归档的既有 VUID（独立事项）：`VUID-vkCmdBindVertexBuffers-pBuffers-00627`、`VUID-vkCmdBindIndexBuffer-buffer-08784`——部分 buffer 未带 VERTEX/INDEX usage 却被绑定（命令缓冲 `0xde2f1640d0`/`0xde2f1720d0`）。

## 当前部署状态（2026-09-17 08:36+）

- 基线对照后已恢复最小化修复：四文件与 `minimal-fix/after` 完全一致（SAME）。
- WAW 定位诊断已全部撤回（`render_diag.hpp` 删除，framebuffer/command/tone_mapping/pipeline 还原；诊断版本归档在 `waw-attribution\diag-source\`），随后重编译干净修复并部署：DLL `985B8ACDE50413965CAFDD82DB0088CC25673353FCB747A1BD17C2B4B3ED74CB`，JAR `7D325AB5294087BA08216B1D9B77B22E29B0E5E842803EE9813EC4F3E33F7EF5`（内嵌 DLL 一致）。
- 工作树保持最小化修复，未提交；此前经手过的同源部署物为 `CA5C80C2…`/`4AFA608C…`（已被重编译产物覆盖，仅哈希留存）。

## 修复定位与结论边界

- 本修复应定位为“**重复对照有效的生命周期规避方案**”，不是已证明原实现违反 Vulkan 规范。Vulkan 1.3 / maintenance4 下实现不应在管线创建调用结束后继续访问输入 layout（[规范](https://docs.vulkan.org/refpages/latest/refpages/source/VkGraphicsPipelineCreateInfo.html)），因此当前证据仍不足以解释驱动行为。
- 保活对象只有 Device、pipeline layout 和 set layouts；不含描述符池、集、纹理资源。`DynamicGraphicsPipeline` 析构先 `vkDestroyPipeline`，随后释放 layout token；表仍负责销毁池，layout 销毁只在 token 内，未发现重复销毁或循环引用。
- 四文件与 `minimal-fix/after` 完全一致；部署 JAR / 内嵌 DLL 哈希与记录一致。

## 复现与回退

- 最小化修复应用：复制 `minimal-fix\after\` 下四个文件到 `MCVR\src\core\vulkan\`，更新时间戳后重编译并部署；当前工作树即为该状态（未提交）。
- 回退基线：用 `minimal-fix\before\` 同名文件覆盖并重编译；E-02 基线另有 `dynamic-pipeline-direct\before`（BF6E… / 2F79…）。
- 观察脚本用法：
  - 自动进世界：`-GradleArgs '-I','<quickplay.init.gradle>'`（Gradle JVM 必须是 Java 21，见上文）。
  - validation：`-ValidationSettingsDir '<dir with vk_layer_settings.txt>'`。
  - 正常退出：`-GracefulCloseAfterSeconds <n>`，脚本只对命令行可证明属于本仓库/实例的进程操作，并记录完整命令行。
  - 超时防呆：`-KillOnTimeout` 仅杀本 Gradle 进程树及命令行匹配的残留进程。
- 诚实计时：`WallSeconds` 从启动 Gradle 起算；`PostJoinSeconds` 只解析本次启动后新增的日志内容（修正了读取旧 `latest.log` 的缺陷）。
