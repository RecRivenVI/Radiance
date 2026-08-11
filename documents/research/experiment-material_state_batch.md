# 材质状态桥接批量化实验

2026-10-19，沿材质状态成本调查实施有界请求计数和相邻 JNI 操作批量化。同一城市的 ABBA 正式窗口没有证实整帧收益，因此批量化保留为默认关闭的实验路径；不将局部请求减少写成 FPS 提升。

## 问题

[上一项调查](investigation-material_state_cost.md)确认约 922 次材质捕获/帧，恢复路径包含大量 JNI 请求，但尚未取得实际字段变化率。需要保留真实 RenderType 回调、Mojang 可观察缓存、纹理写入顺序和帧／视图归属，验证能否减少桥接成本。

此前的容器减负与默认关闭的拓扑并行完整保留，见[CPU 实验](experiment-cpu_task_preparation.md)。本轮没有开启工作线程、修改 shader、减少几何、降低质量或扩大 Voxy 兼容范围。

## 方法

证据根为 `validations/performance-scene_diagnosis/result/state-batch-20261019/`。使用同一已校验的城市 ZIP，每次启动前恢复 206 个文件；夹具 SHA-256 为 `468B12B806BB2D705B23C9A123F88439BE00A8E02DAAE2A9062BFB86A4CC0FE5`。保持原相机、32 视距、1920×1080 输出、Vanilla PT、RR Balanced、SHARC、Reflex 2，FG／VSync／独立区块发光收集关闭，主音量及音乐为 0。至少预热 60 秒且队列连续平静 10 秒，再请求窗口；随后 F3+T、保存并正常关闭。

先取得一个只接入计数的基线，再在同一个性能对照 JAR／DLL 中分别使用细粒度和批量路径。独立 TIMED 窗口开启请求及生产者计数；正式 ABBA 的四个 60 秒 FRAME_ONLY 窗口关闭两种计数、嵌套计时及附加 native profile，不把重型诊断窗口当作产品帧率。线程数始终为 0。

`state_bridge_audit.hpp` 使用固定标签和线程本地计数，每帧汇总一次。只有环境变量 `RADIANCE_STATE_AUDIT=1` 且显式 native profile 窗口活动时计数。记录的是所覆盖 `UIModuleContext` setter 的入口次数、请求字段变化、pass 外请求、middleware 的 context 获取和 FBO 操作；包含 setter 内部调用，不等同于全部 JNI 次数。变化比较包含原始 GL 视口／裁切矩形，不声称已比较所有派生 Vulkan 状态。

每个计数在 profile domain 5 中使用 count，bytes 始终为 0。查询数表示所监测查询 API 的入口次数，不是返回字段数量；上下文获取次数不是锁争用时间。未将计数或包含时间重复相加，也没有逐顶点日志、全局诊断锁或每帧 GPU 等待。

运行前的进程清单仍发现其他项目客户端；没有操作它。全卡监控不等于本进程占用，外部负载与世界 tick／GC 波动保留为比较限制。未修改驱动、系统设置或 TDR。

## 发现

### 请求重复率与实施边界

当前性能产物的两个独立 TIMED 窗口如下；生产者计数与字段计数会扰动耗时，不能直接扣减正式窗口。

| 指标 | 参考路径 | 批量路径 |
| --- | ---: | ---: |
| 有效帧 | 1,130 | 1,156 |
| 材质捕获 | 约 922 次/帧 | 约 922 次/帧 |
| UI setter 入口 | 约 29,122 次/帧 | 约 29,122 次/帧 |
| 请求字段实际变化 | 11.13% | 11.13% |
| pass 外请求 | 99.65% | 99.65% |
| 被监测的 context 获取 | 30,984.33 次/帧 | 27,256.89 次/帧 |
| 被监测的查询入口 | 2,830.08 次/帧 | 986.01 次/帧 |
| FBO 绑定 | 约 1,883 次/帧 | 约 1,883 次/帧 |
| FBO 绑定值变化 | 0 次/帧 | 0 次/帧 |
| 绑定前活跃 pass 结束 | 1 次/帧 | 1 次/帧 |
| 材质捕获包含时间 | 3.939 ms/帧 | 3.658 ms/帧 |
| 其中 GL 恢复 | 1.501 ms/帧 | 1.374 ms/帧 |

字段不变不代表可以删除请求：当前 command buffer、视图、FBO 尺寸与 Mojang 全局缓存并不等价。保留已有的 pass 外延迟写入及绘制入口完整同步，未增加全局相同值过滤。

只合并连续且没有 Java 回调穿插的操作：

- stencil 的 func／op／write-mask 原先分别调用正、反面 JNI，现在各有一个双面入口；仍依次调用原有正、反面 setter，在两者之间保留 fatal 检查。独立单面接口继续用于真正的 separate 请求。
- 材质快照一次取得 read FBO、draw FBO 和 viewport；不保存跨回调的 native context，也不声称三项查询是跨线程原子快照。
- FBO 恢复一次进入 JNI，但仍按 read 的 end/bind、draw 的 end/bind 执行；即使 ID 相同也不删除绑定或结束 pass。第二项失败时保留第一项已成功的效果。
- `setupRenderState`、`clearRenderState`、`restoreGlState`、viewport 的 Mojang 方法和全部十二个 shader texture 恢复不变；不按 RenderType 永久缓存最终状态，不跳过相同 texture ID 的写入。

依据实际字节码及调用链推导，普通完整捕获在 setup／clear 之外的固定 JNI 路径由 28 次变为 22 次；这是路径推导，不冒充插桩取得的全部 JNI 总数。实际计数只证实 context／查询入口减少，逻辑状态请求和活跃 pass 行为保持。

### 正确性与生命周期验证

新增 `MaterialStateBatchProbe` 经过真实 RenderType 和 JNI，检查不同正反 stencil 操作、组合更新、不同 read／draw FBO、第二次绑定失败、嵌套捕获、异常恢复、首个错误与 suppressed cleanup，以及全部公开 Mojang 备份字段。保留原 `MaterialStateProbe` 的入口和行为，另加城市隔离入口，复用真实离屏绘制与像素读回，覆盖活跃／空闲 pass、回调内绘制、面状态、混合、深度、裁切与颜色遮罩。

性能产物的六次成功客户端均在进入世界和 F3+T 后通过两层验证。十二份完整 64×64 RGBA8 读回逐字节一致，SHA-256 均为 `3701F59BEBD03060B7363D479EB020932622CE7AA213B6A75835323EB345E672`；每例保存 `pixel-readbacks.zip`、逐文件索引与读回校验。它证明该测试图的 GPU 合成结果，不是全部 PT 画面的视觉等价结论。

另一次接线基线未包含新增功能探针，只用作请求归因。七个有效窗口共 13,416 帧，writer/native 丢弃、失败帧、分区错误与 token 缺口均为 0；各例保留真实加载 DLL 路径和哈希、世界各维度保存、关闭跟踪与进程结果。没有设备丢失或 GPU 转储。关闭跟踪文件含历史追加段，只用每例新鲜的最后一个 normal-device-close 段；结合没有关闭警告、Streamline 关闭、Aftermath disable 与进程返回 0，不将 CPU 跟踪称为 GPU crash dump 或完整 G3。

### 正式整帧对照

四个窗口使用同一个性能产物、同一世界副本和相同设置，按参考 A、候选 A、候选 B、参考 B 顺序运行，不裁掉长帧。

| 窗口 | 有效帧 | 平均帧间隔 | p95 | p99 |
| --- | ---: | ---: | ---: | ---: |
| 参考 A | 2,505 | 23.956 ms | 26.736 ms | 29.945 ms |
| 候选 A | 2,542 | 23.610 ms | 26.245 ms | 28.681 ms |
| 候选 B | 2,441 | 24.588 ms | 29.588 ms | 37.048 ms |
| 参考 B | 2,548 | 23.553 ms | 25.991 ms | 28.515 ms |

合并全部已知帧间隔：参考 23.753 ms（约 42.10 FPS），候选 24.089 ms（约 41.51 FPS），候选慢 0.337 ms；合并 p99 为 29.359→33.681 ms。首对较快、末对较慢，运行间波动大于局部节省。没有足够数据把回退归因到批量化，也没有数据支持稳定整帧改善；不能把 TIMED 的 0.281 ms 材质包含时间差直接算作净收益。

同一正式窗口内，全卡 GPU 利用均值依次为 84.33%、84.53%、71.55%、75.47%，采样图形时钟均为 2790 MHz，全卡显存峰值为 15,386 MiB。对应每窗 60 个样本见 `formal-gpu-telemetry.json`；这些包含外部负载，不能解释为本进程 GPU 执行时间或把候选 B 的变化确定归因到外部程序。

因此最终将 `radiance.materialStateBatch` 的产品默认值设为 false；显式 true 只供后续隔离实验。多线程同样保持默认关闭。计数默认关闭的分支成本没有独立量化，不据此声称新 core 相对更早、不含该诊断的产物已净提速。

### 构建、失败留痕与产物

执行 `spotlessApply check verifyRelease verifyCompliance --strict`。GAME 326、probe 41、line JNI 4 通过；bootstrap 11 通过、2 个既有条件跳过；非 GPU CTest 74/74，包含真实使用的计数汇总头文件测试。JNI 头文件、Maven 开发产物和发行结构核对通过，严格合规零失败／零警告。未新增 shader 修改，未把未运行的全部独立 GPU CTest 写为通过。

最初误用了不存在的 `clangFormatApply` 任务；改用当前 VS 格式化工具及组件现有检查后通过。第一次运行器接线因 included build 中 Target 为空失败，尚未创建客户端；修正空项目防护后采用新案例名，未覆盖失败目录。没有自动重试真实客户端故障。最终默认预检另有两次启动前工具阻塞：Gradle MapProperty 没有 remove 方法，改为不设置该属性；存储余量曾重复计算待替换的旧夹具，修正替换量后继续。两次都未创建客户端，失败目录与说明保留，旧客户端文件不算新产物运行证据。

| 用途 | JAR SHA-256 | core SHA-256 |
| --- | --- | --- |
| 计数接线基线 | `372EF9B9ED71826067CDEB4662FE0C7EB30E85973EB48EE1A4B5943A8D01C5EC` | `FBAD6EE0893C1BB21E328658E69AC54844E8A9457C6FAA0011DAA750CBC2E751` |
| 性能对照共用产物 | `60A72F28B15EC88DE3934CD13462D3C09F244845C4B4F0DB8EEB8EB4155B2D79` | `B023613AE93F98B4D54433ADB97D78D50DB1A380D4F8403F83FBE580F30A712C` |
| 最终默认关闭产物 | `6A85A90A4CF8703411493CAB077FF22A8CC7CCD589EBBEAA68955C652950B392` | `B023613AE93F98B4D54433ADB97D78D50DB1A380D4F8403F83FBE580F30A712C` |

两个后续产物的 GAME 都与开发 class 逐文件一致，共 692 个类；二者只有 `PipelineStateProxy.class` 改变，对应实验开关默认值，native DLL 不变。最终包单独执行不建立 profile 窗口的同城 functional 流程；不复用性能产物的正常运行作为新 JAR 加载身份。该流程 `final-default-functional` 已通过：实际 VM 参数没有设置实验属性，探针读到默认 false；原生实际 extent 为 1920×1080，进入世界和 F3+T 后两层检查及两张像素读回通过，所有维度保存、normal-device-close 与进程返回 0，实际加载 DLL 哈希一致。没有产生 profile 窗口，不把它算作新增性能数据。

每个运行目录保存启动配置、请求、环境、原始帧／计数、加载路径、日志和读回索引。`diagnostic-before-source-manifest.json`、`candidate-source-manifest.json`、`final-default-source-manifest.json` 分别固定对应产品输入；维护结束的完整范围另由 `final-source-manifest.json` 固定。包含必要未跟踪代码、头文件、测试、构建配置与十四个固定 gitlink；按 exact bytes SHA-256、有序路径清单和 ZIP 逐条读回校验，不反推旧源码身份。

按存储约束，每次关闭后核对当前 JAR 的 entry 哈希，再回收项目内解包二进制、相同的资源副本和已完成夹具副本；没有删除 `radiance/cache/` 中的 shader／pipeline 缓存或本地输入。最后一例的当前故障／AS 原始日志与已校验 ZIP 完全相同后，仅回收重复的当前文件，所有原始字节仍保留于该例 `client-evidence.zip`，记录见 `control-b/raw-log-retirement.json`。未删除唯一证据、未清空回收站。

## 结论

实际证实的是约 12% 的已监测 context 获取和约 65% 的已监测查询入口减少，而不是场景、材质处理或 GPU 工作减少。限定回归通过，但未达到稳定整帧收益的推广条件，批量化默认关闭，代码和对照保留；不扩大到全局状态缓存或下一轮持久模型实施。

本轮没有新增人工视觉、移动路线、加载吞吐、2160p 压力、长期稳定或完整设备丢失结论。既有未知 GPU 根因、公开二进制许可、Voxy 初步适配及此前待验边界保持独立。没有使用 Computer-Use、子代理、Prism、旧 MCVR 写入、暂存、提交或推送。
