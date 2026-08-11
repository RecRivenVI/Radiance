# SBT 命中表内容复用

2026-10-18，按使用者授权接续小几何查表优化，先测量 SBT 的实际内容变化，再只复用内容完全相同的命中表。两个同城短时窗口显示重复打包与上传已消除，整帧也下降；其他 CPU/GPU 阶段存在波动，不把整帧差异全部归因于缓存，不扩大为长期稳定或所有场景收益。

## 问题

`SBT::setupHitSBT` 原本仅按大小复用设备缓冲，每次仍创建清零数组、复制全部 shader handle、分配暂存、记录完整上传和屏障。前一轮约 1.88 ms/帧只是定位线索；本轮另建带内容 observer 的基线，不混用旧帧时长。

命中记录只有所属管线的 shader handle 和零填充，不含顶点地址、材质纹理或相机状态。在同一 SBT 对象的不可变 handle palette 内，有序命中组编号完全相同足以证明表字节相同；不根据数组长度、几何数量或哈希命中推定相同。不同编号可能对应等价 handle，因此输入变化只作为保守失效条件，不反向声称字节必然不同。

## 方法

### 先观察，再修改

取证基线只加入 profile 开启期间的精确列表比较，原生成／上传路径保留。每个 SBT 在本次 profile epoch 的第一次观察计为 `cold`，其后计 `equal` 或 `changed`；`cold` 不等于产品缓存为空。`upload` 表示逻辑上传字节，不是 PCIe 实际流量或 GPU 完成。基线在设置入口计数，候选在成功记录后计数；两次流程均正常完成，无异常路径混入。

两次均逐文件恢复同一城市 ZIP，SHA-256 为 `40F41782F1A065500B321C6A588B925BD5425233D741E014EBC7F8DC833B3B02`。保持保存的相机、1920×1080、32 视距／模拟距离、Vanilla PT、RR Balanced、Reflex 2、FG 关闭、静音与既有模组。沿用默认轻量取证和 Aftermath，不启用 JFR 或重型归因。预热至少 60 秒且队列平静 10 秒后，采样 60 秒，再重载资源并正常保存关闭。该运行是开发验证入口，不把它写成发行 JAR 的安装启动。

使用现有验证入口，分别设置独立的 `cityComparisonLabel`；具体恢复工具、原始 ZIP 和两次恢复收据保存在下述证据目录。没有访问 Prism 或生产世界。

### 缓存和所有权

- 每个管线／pass／帧槽的 SBT 只缓存最近一份完整有序编号和设备缓冲，完全一致时跳过打包、暂存、上传及新屏障；每次使用仍按当前帧登记资源引用。
- 任何内容变化，包括同尺寸重排，均创建新的不可变缓冲；先准备、保活，再记录 copy 和原有 transfer→ray-tracing 屏障，成功后以不抛异常的 swap 提交缓存。失败保留原版本并传播异常。
- 已记录的旧版本不原地覆盖，交由现有 `FrameResourceRetainer` 按实际完成 serial／fence 退休。同帧 A→B→A 也不改写先前记录使用的数据；不引入全局 idle 或按固定帧数提前释放。
- `buildRayTracingPassPipelines` 重建 query/update pipeline 时清空并重新创建各 SBT，新的 palette 不继承旧缓存。只有纹理变化而管线、编号列表不变时，表本身无需重新上传。
- 补齐 palette 范围、空列表、字节溢出与 handle 获取结果检查；获取失败保留现有首错／device-lost 传播边界，不缓存无效 handle。

生产接缝为 `sbt_hit_table.hpp` 的实际打包和版本选择函数，由 `sbt.cpp` 直接使用；没有另设一套只供测试的缓存逻辑。场景元数据、TLAS、动画、材质、shader 和几何参与范围没有修改。

正常框架在设置之后提交 world 命令；失败走 fatal 停止后续工作。读回续接在重置命令前已提交并等待原命令。缓存的提交指 CPU 录制成功，不等于 GPU 已完成。同步依据见 [Vulkan SBT 读取规则](https://docs.vulkan.org/spec/latest/chapters/raytracing.html)和[内存依赖规则](https://docs.vulkan.org/spec/latest/chapters/synchronization.html)。

## 发现

### 内容变化与行为回归

基线 PID 88068 的 1,494 帧有 5,976 次设置：8 次首次观察、5,968 次相同、0 次变化，仍全部请求整表上传。去除首次观察后实际相同率 100%，不是对所有内容或动画状态的普遍假设。

候选 PID 107796 的 1,740 帧有 6,960 次设置，全部复用暖机时已准备的表，正式窗口内上传记录与逻辑字节均为 0；首次观察仍为 8 次。每次表大小 1,636,224 字节，平均四次设置／帧；这不代表整次启动、加载及重载都没有上传。

新 native 回归以生产接缝覆盖 500 组随机 palette／stride／编号布局，逐字节核对 handle 和零填充，并覆盖精确复用、同尺寸重排、尺寸变化、同帧 A/B/A、跨帧复用、不同 pipeline/pass/帧槽身份、SBT 销毁／重建、未完成 GPU 工作不得退休、完成后释放，以及 prepare／retain／record 失败。空输入、未知组、溢出和空资源均拒绝；共执行 261,446 条断言，不能将其称作 261,446 项独立测试。

执行以下完整检查通过：

```powershell
.\gradlew.bat spotlessApply fixMarkdown check verifyRelease verifyCompliance --strict --console=plain
```

本轮实际执行 GAME 314、probe 34、JNI 4、bootstrap 11 项通过／2 项跳过；native 非 GPU CTest 70/70。JNI 头文件、shader 配套构建、发行结构与探针排除检查通过；严格合规零失败／零警告。没有新增独立 SBT GPU 合成夹具，也未重跑与本次表缓存无直接关联的旧 GPU 矩阵；真实 GPU 覆盖是下面两个城市流程。没有新增人工视觉或多视图客户端验收。

### 同城 32 视距对照

基线北京时间 02:46:30～02:47:30；候选 03:03:08～03:04:09。两边开始采样时 Java／native 编译和 GPU 构建队列平静，无失败帧或 writer/native/容量丢弃。

| 指标 | 新取证基线 | 缓存候选 |
| --- | ---: | ---: |
| SBT 生成／记录 CPU 包含时间，ms/帧 | 1.928 | 0.135 |
| 其中内容 observer，ms/帧 | 0.074 | 0.063 |
| SBT 组映射 CPU，ms/帧 | 0.156 | 0.148 |
| 正式窗口逻辑 SBT 上传，字节/帧 | 6,544,896 | 0 |
| 真实帧间隔均值，ms | 40.168 | 34.496 |
| 真实帧间隔 p50，ms | 38.814 | 33.740 |
| 真实帧间隔 p95，ms | 51.579 | 42.528 |
| 真实帧间隔 p99，ms | 62.433 | 49.004 |
| 最大帧间隔，ms | 88.385 | 66.077 |
| 主队列 GPU 完成区间，ms/帧 | 18.105 | 15.517 |
| world-buffer GPU 区间，ms/帧 | 17.046 | 14.833 |
| entity BLAS GPU 区间，ms/帧 | 0.990 | 0.932 |
| TLAS GPU 区间，ms/帧 | 0.310 | 0.238 |

SBT 包含时间减少约 1.793 ms、93.0%；帧间隔观察下降 5.672 ms、14.1%，约 24.9→29.0 真实 FPS。两边都没有超过 100 ms 的帧。CPU 和 GPU 区间重叠，不能相加；不把全部 5.672 ms 归因于 SBT。没有修改的世界准备也从 10.050 变为 9.134 ms，实体、方块实体和 tick 阶段同样波动，说明全帧仍受运行状态、调度及其他负载影响。

静态记录 17,705、静态几何 31,559、TLAS 实例 18,482 两边逐帧相同；PBR 输入顶点均值约 75,619.882→75,618.202，动态世界并非逐 tick 完全相同。相机位置和角度逐帧一致，雨／雷为零，候选采样起点比基线早 27 游戏 tick。旧 Options 的反弹值未被当成实际 shader 参数读回。缓存焦点字段均 true 不证明实际 GLFW 焦点；生成帧计数均为 0，不涉及 FG 动态视觉结论。

各 59 次 VMA 观测的分配字节峰值为 9,724,159,680→9,694,826,896，分配数峰值 72,868→72,892；不是驻留显存测量。整卡显存和 GPU 时钟记录于负载上下文，包含其他程序，不据此承诺显存收益。每边只有一次 60 秒窗口，不是重复实验、长期稳定性或加载吞吐验证。

两个流程均完成资源重载，字体违规为 0，各维度保存、`normal-device-close result=0` 与 Aftermath disable 完成；无设备丢失、GPU 转储或自动重试。这不关闭历史 GPU 根因。

### 源码、产物与存储

证据目录为 `D:/Workspaces/Scratches/RadianceSemanticsAudit/`：

- `sbt-observer-source-manifest.json` 在 `pair-table-final` 范围上固定仅两处 observer 修改；取证基线 JAR `63D6607DEACC74E63B79F40DA6D2C520ADD1BCC2A0832BE2418CF73C45CD83A8`，core `EE75B7B8946921128D19D3D4ED1400B27FCAA7D831A402F7B96ECDFDF979D61C`。
- `sbt-candidate-source-manifest.json` 固定 1,994 文件、14 个 gitlink，实际产品变化为 `sbt.cpp`、`sbt.hpp`、`sbt_hit_table.hpp`，另有测试及其 CMake 登记。原始字节 SHA-256、不做换行归一化，按父档和增量逐文件读回；必要未跟踪文件在范围内。最终维护记录由 `sbt-final-source-manifest.json` 单独固定。
- 当前 JAR `77825162E53D4D8AB4FE9B67656D7407BBADA14FAC3C117BF6B2ABB3E2915E01`，core `6429702ABE622BA329EE667D71C2497516BB0D28C3F682D7142DE83FAD9023CB`；GAME 仍为 `839135CADFDF427781C70F152B887CDA2DC474911AE454E2BA2BC22F3F690C9A`。构建、JAR 内嵌、提取及实际加载 DLL 身份一致，见两个 `sbt-*-loaded-modules.json`。
- `sbt-observer-resources.json`／`sbt-candidate-resources.json` 按解包后的原始字节比较 1,715 条目，忽略 ZIP 元数据，只有 core 与运行库索引变化。没有将 shader ZIP 的重新打包时间视为 shader 内容变化。
- `sbt-candidate-check.log` 保留完整构建结果；`sbt-comparison.json`、`sbt-metrics.json`、`sbt-resources.json` 与两次 client／load-context 日志保存计时、工作量及资源边界。原始帧表分别在本项 `result/profiles/2026-10-17T18-46-30.412901Z-pid88068/`、`2026-10-17T19-03-08.939260200Z-pid107796/`，对应关闭 ZIP 已逐条校验。

构建／启动前存储预检通过硬上限；外部受保护输入／SDK／证据软超仍按既有记录保留。为预留验证上限内空间，仅将已关闭旧 smoke 实例与当前构建逐文件 SHA-256 相同的重复运行库送入回收站，保留 core 和异体文件；本轮两次解包副本也在保全实际加载身份和关闭证据后按哈希回收，当前构建仍在。收据为 `sbt-completed-smoke-runtime-recycle.json`、`sbt-observer-runtime-recycle.json`、`sbt-candidate-runtime-recycle.json`。没有清理唯一文本证据、夹具或源码归档。

## 结论

保留精确内容缓存：本场景的实际重复率和 CPU 节省已确认，正式窗口的无效整表上传已消除，完整几何数量保持一致。新表按不可变版本记录，旧表按原有 GPU 完成条件退休；重载与错误传播保持有效。

每帧组映射仍存在，未同时重写全局场景表、TLAS 或动态几何。内容频繁变化时，新版本可能增加短时分配与在途占用，本轮没有给所有场景作性能保证。单帧多视图的资源隔离由接缝回归和调用链核对支持，真实多视图视觉仍未新验收。未投放 Prism，不暂存、提交或推送；历史设备丢失与公开二进制许可边界不变。
