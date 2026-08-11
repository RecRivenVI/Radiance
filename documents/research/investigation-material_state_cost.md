# 材质状态捕获与恢复的 CPU 成本调查

2026-10-19，使用者决定暂缓多线程，继续排查可减少的 CPU 工作。本轮读取当前工作树、实际 NeoForge 字节码和保留的城市样本，确认下一项优化对象与不能省略的语义；没有修改产品或探针、启动客户端、采集新性能窗口、提交或推送。

## 问题

上一轮仅并行独立拓扑准备，局部预算不到一毫秒，整帧收益没有稳定重复。需要继续减少材质捕获链本身的重复工作，不能跳过 renderer／状态回调、缓存上一帧颜色或删除状态恢复来换速度。上轮未提交的减负与默认关闭的并行原型完整保留。

## 方法

核对 `MaterialFaces.captureSnapshot`、provider 的 seal、普通与持久模型提交、GL 状态 Mixin、纹理最后写入者、JNI 边界、原生 UI 状态及 FBO 实现。用实际安装的 `neoforge-21.1.259.jar` 执行 `javap -p -c`，确认 `_restoreGlState` 的真实方法体；参考源码只用于定位，不以旧 21.1.250 补丁替代实际依赖。

重新汇总[上一轮 CPU 实验](experiment-cpu_task_preparation.md) C2 的三个 TIMED 窗口；生产者次数另来自[诊断可信度调查](investigation-cpu_diagnostic_reliability.md)的 PRODUCERS 窗口。没有将不同模式的包含时间和 self、历史进程和新产物合并成同一性能结果。

证据根为 `validations/performance-scene_diagnosis/result/material-state-20261019/`，`facts.json` 保存原始窗口路径、来源行、源码哈希与调用次数推导；两个 `*.javap.txt` 保存当前依赖的字节码。十三份相关产品／原生文件与生产者窗口的留存源码哈希一致，容器减负没有改写这条状态链。既有 CPU 源码快照不回填为本轮文档快照。

## 发现

### 当前局部预算与主要来源

最近 C2 的 3,643 个有效 TIMED 帧按实际帧数汇总如下。它们属于含观察器的分段窗口，不是无探针耗时，也不与 FRAME_ONLY 相减来推算精确产品收益。

| 阶段 | ms/帧 | 边界 |
| --- | ---: | --- |
| 材质捕获包含时间 | 3.601 | 包含下列子阶段，不能重复相加 |
| 实际 setup 回调 | 0.796 | 必须保留的来源执行，内部仍可能有冗余传递 |
| 实际 clear 回调 | 0.332 | 必须保留的来源执行 |
| GL 状态备份子调用 | 0.022 | 只计 `backupGlState`，不含外层矩阵／纹理／FBO 快照 |
| GL 状态恢复子调用 | 1.446 | 最明确的重复边界候选，不代表整个阶段可删除 |
| 捕获 self | 1.006 | 其余保存、读取、恢复、对象及观察器路径 |

保留的 PRODUCERS 窗口为 1,046 帧，约 922.001 次材质捕获/帧；不是本轮新测量。主要为物品框 587 次/帧、绘画 223、告示牌 34、箱子 31、床 19，柔性弹簧只有 8。物品框使用 57 个不同 layer，绘画 223 次共享一个 layer；这说明重复事务很多，但同 layer 不证明当前颜色、纹理、回调或历史可复用。

### 重复 JNI 与状态事务

`MaterialFaces.captureSnapshot` 先保存调用者状态，执行真实 setup，读取面规则、线宽、颜色和 UV，再执行 clear 并恢复。普通批次在 seal 时保存快照，提交优先消费该快照；刚性 draw 同样优先使用其已保存状态，仅缺失时重新捕获。没有证据支持删掉提交端的合法回退，或宣称全部 draw 都重复捕获两次。

`GlStateManagerMixins` 与 `GlBooleanStateMixins` 在方法 HEAD 镜像请求，因此原版内部缓存即使不需要终端 GL 调用，JNI 仍会执行。当前正常完整捕获在来源 setup／clear 之外有以下固定请求路径：

| 路径 | JNI 请求数 |
| --- | ---: |
| read／draw FBO 与 viewport 查询 | 3 |
| `restoreGlState` 的 16 组恢复，其中 stencil 三组分别下发正反面 | 19 |
| 额外的 cull mode／front face／cull enable 恢复 | 3 |
| read／draw FBO 恢复 | 2 |
| viewport 恢复 | 1 |
| 合计 | 28 |

这是根据当前字节码、Mixin 与 native 声明推导的正常完整路径，不是已插桩取得的 native 次数，更不是 Vulkan 命令数。`RenderSystem.lineWidth` 在当前版本只写 Java 字段，未误计为 JNI。按旧生产者窗口的 922 次作量级参考，约 25,816 次/帧，setup／clear 还会增加请求；没有实际“相同值”比例，不能由此推算可省毫秒。

原生大多数状态 setter 每次都会取得 framework、调用 `safeAcquireCurrentContext`、取得 pipeline context 与 UI 状态。`safeAcquireCurrentContext` 在主世界路径使用 recreation 递归互斥锁，shared ownership 也有重复开销。锁的执行次数不等于锁争用，更没有证据说这些锁就是当前最长等待。

### 必须保留的失效与观察边界

1. 原版 GL 缓存是进程状态，Vulkan 当前 command buffer、视图和 pass 可以独立改变动态状态。现有 boolean 镜像已经明确为此保留 HEAD 请求；直接挪到终端 GL 调用或加全局“值相同就跳过”会丢掉恢复。
2. native 已有 `deferIdleStateWrite`：光栅 pass 外仍更新 shadow state，绘制入口完整重放。下一轮不能把它重新列为待实现，也不能把捕获期间所有请求都等同于已录制 GPU 命令。
3. `TextureProxy` 的 shader slot 与 legacy bind 使用写入序号决定最后写入者。即使 texture ID 相等，也不能直接跳过一次写入或只按整数 ID 缓存；资源代际、替换与 F3+T 仍有效。
4. FBO bind 当前会调用 `endPass`，包括恢复同一 ID。此处存在重复路径，但实际中断了多少活跃 pass 尚未测量，不把“调用 end”直接写成“每次重启 GPU pass”。附件修改、读回和绘制依赖也不能重排。
5. 自定义 setup／clear 可以改变状态、嵌套捕获或抛出异常。真实回调顺序、Mojang 可观察缓存和首个错误／suppressed cleanup 必须保留，不能以 RenderType、纹理或 renderer 名称猜测静态结果。

### 排除和后续候选

- 普通纹理的 `updateSampler` 已在过滤参数相同时返回，不创建采样器、不退休或重发同一代资源。JFR 中出现 `TextureProxy.setFilter` 只证明该入口有成本，不证明仍在每次重建／全表更新。frame alias 另有通知路径，不能按普通纹理结论全部跳过。
- `VulkanConstants` 中 `values()`／stream 只初始化静态映射；不能把它描述为逐请求建立映射。装箱查表仍存在，但没有独立毫秒证据，不作为首选。
- JNI fatal 检查在健康路径是原子读取，详细快照只在 fatal 后取得；allocation tag 是 thread-local 标记。没有依据削弱错误传播和取证来换速度。
- `SpringDrawContract` 仅在名称初筛命中后检查来源类和真实工厂身份。此城市场景每帧只有 8 次弹簧捕获，不应先优化弹簧或删掉来源校验。
- 绘画仍走程序化顶点入口，未使用当前 ModelPart／BakedModel 持久路径；223 次/帧的捕获与其生成工作是下一独立候选。方案必须保留变体、分块属性、轮廓、姿态、重载和历史，不能缓存上一帧最终顶点。

## 结论

优先级改为“减少状态桥接工作”，不是增加线程，也不是把材质状态永久缓存。建议下一项先为状态请求、实际字段变化、上下文／pass 切换补有界汇总，仅在显式诊断窗开启；随后在现有捕获事务内批量取得和恢复 native 状态，减少逐字段 JNI 与重复 context 获取。先保留全部真实 setup／clear，不跨绘制或读回边界合并，不改变完整 PT、材质、历史或退休。

实施门槛是：覆盖嵌套与异常恢复、同 layer 不同颜色／UV、shader 与 legacy 最后写入者、重载代际、多视图／command buffer 切换、GUI 与世界边界、深度／裁切／混合及原始错误保留；再用同一冻结城市副本分别比较 FRAME_ONLY 与独立诊断窗。未确认上下文归属时回退原路径，不用全局 idle。没有单独的重复率和安全失效证明前，不直接跳过回调或改写全局镜像策略。

当前明确的局部包含预算约 3.60 ms，其中 1.13 ms 直接属于来源 setup／clear；并非全部都能消除，也不能将其直接从其他进程的整帧时间扣除来承诺 60 FPS。若此路径之后仍不足，再选择绘画持久化或剩余 native 批次准备中的一个独立实验。多线程按使用者决定搁置，默认 0 的实现与回归保留，另获授权再评估。
