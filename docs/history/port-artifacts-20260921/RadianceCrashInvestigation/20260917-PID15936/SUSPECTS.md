# 未提交改动嫌疑清单（静态审查，2026-09-17）

范围：Radiance HEAD 414d8e3 / MCVR HEAD 9905c81 对当前工作区，含新文件。未修改源码，未启动新实验。本清单是候选排序，不是根因结论。尚无匹配平台、实例和内容的上游原版运行对照。

## 1. UI 描述符换代频率（优先）
MCVR src/core/render/modules/ui_module.cpp:460-514：bindTexture 从上游逐帧更新现有 descriptor 改成 dirty-all；prepareOverlayDescriptorTable 在 draw/begin 时新建整表，替换 context 指针，并把旧表加入帧保留器。src/core/render/textures.cpp:337-428：即使 filter/address 不变，仍调用 bindTextureAndReleasedAliases，导致 bindTexture 再次 dirty-all。换代本身及旧表保留在上游已有，但上游的普通 bindTexture 不触发这种换代，必须区分。
风险假设：高频 VkDescriptorPool / VkPipelineLayout / sets 创建销毁及绑定代际交错；日志锁、格式化、刷新可能改变压力与时序。尚未证明 UAF，也不能仅凭 pipeline 创建时 layout 后来销毁就判定 Vulkan 违规。
建议最小实验：相同绑定对象/别名不变化时不标 dirty（仅去掉无效换代），trace 关闭，重复真实实例；加低扰动 table generation / create / retire / fence 计数。不能直接全局恢复上游 update-after-bind 行为，可能引入另一类问题。

## 2. 新增早期加载画布与游戏共享渲染路径（次优先）
RadianceLoadingOverlay.java:100-129 在 fade>=1s 时先渲染游戏 screen，再由 provider.onGameFrame 绘制 loading。MCVR loading_renderer.cpp:255-345 直接写同一个 overlayCommandBuffer：结束 UI pass、绑定自己的 pipeline/descriptor、绘制后恢复描述符与动态状态。释放路径 loading_renderer.cpp:223-239 等待 device idle 再销毁加载资源。
风险假设：首次标题界面与 loading 淡出交叠的命令状态/帧归属/资源交接。已看到 renderLock、native loadingMutex、handoff waitDeviceIdle 和 syncToCommandBuffer，不能声称它们缺失；尚未证明当前失败仍在 overlay 内，hs_err 的 TitleScreen 路径也可来自普通主菜单。
建议最小实验：只隔离 loading 绘制交叠或早期窗口，保留游戏侧渲染，关闭 trace；需记录窗口/帧路径变化，不能当作完全相同环境。

## 3. 新增持久化 Vulkan pipeline cache（控制变量）
MCVR src/core/vulkan/device.cpp:426,460；pipeline_cache.cpp:98,156。上游创建 graphics pipeline 传 VK_NULL_HANDLE cache；当前通过带文件持久化的 PipelineCache 创建。
已看到 cache mutex、header/设备身份/hash 检查，没有静态证据证明缓存损坏。运行会写入新缓存，因此“同一 DLL/JAR”不等于所有运行状态冻结：早期 console 载入 pipeline-v1-4a479d3a6a10e55b.bin，而 trace-off console 载入 pipeline-v1-b619145f5e691836.bin。
建议最小实验：保留原缓存，固定同一缓存快照或显式空 cache；不要删除用户缓存。这是较低优先嫌疑，也是一项实验混杂变量。

## 4. 文字 sampler/uniform 移植（保留候选）
ShaderProxy.java:173-244：Map<Object> 解析、slot fallback、framebuffer sampler 高位编码。记录的首次 rendertype_text 样本为192字节、Sampler0=61、Sampler2=19，不能拿这个非故障瞬间的样本排除后续坏值。encodeSamplerTextureId 对 <=0 原样返回，需结合 shader 对无效索引的处理核查。当前没有捕获 trace-off 故障时 uniform，不能宣称索引越界。

## 已降低优先级/避免误归因
- 异步纹理上传、上传 Fence 回收、FrameResourceRetainer、descriptor refresh 函数本身在上游已有。只查具体差异，不能统称为本次新增。
- 当前 acquireContext 在等待对应 frame fence 之后才清理该帧 retained resources；未发现这里简单的“先释放后等 Fence”。
- 当前 ShaderProxy JNI 会复制 uniform 到 CPU storage，Java MemoryStack 生命周期覆盖 JNI 调用；未见该路径明显的提前释放。
- 纯实例未加载 Create/Sable/Aeronautics/Veil；尚未入世界即可崩溃，兼容模组业务和世界光追优先级低于 UI 路径。
- 关闭 trace 的二进制对照支持“追踪运行行为影响故障表现”，但 Java/native 日志同时变化、磁盘缓存有演化，不能直接等同于已证明某个竞态。

建议顺序：先固定 cache 和二进制基线，再做描述符无效换代的单点实验；如仍失败，拆分 Java/native trace，再查 loading overlap。保持改动独立，实际游戏失败签名对比；不通过日志延迟宣称修复。

补充核对：第二次成功诊断启动 rolling-rerun 和失败 trace-off 都载入同一个 pipeline-v1-b619145f5e691836.bin（7382803字节）。因此缓存演化确实存在于更早运行之间，但不能解释这一对成功/失败差异；cache 作为主要原因的优先级进一步降低。后续仍固定快照以控制其他运行状态。

## 2026-09-17 第二轮更新
Early window 已恢复开启；禁用仍崩，因此降级早期窗口候选。新审查详见 dirty-audit-2/AUDIT.md。新增重点为全局 RadianceSampler2D 包装：故障文字 vertex shader 中额外 OpImageQuerySizeLod 在离线优化后保留，恢复上游直接采样的离线对照则没有。四个阶段样本都通过 spirv-val，未证明代码非法或根因。descriptor布局 ALL_GRAPHICS/新增bindings 与 cache创建feedback pNext仍是可隔离候选。动态UBO基础设计上游已有。本轮仅恢复配置、保存审查和离线shader产物，未改源码/未启动游戏。
