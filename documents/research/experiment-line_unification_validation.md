# 阶段 A 线条统一的验证

调查日期：2026-10-17。本记录承接[迁移前调查](investigation-line_unification.md)，分别保存明确缺陷、工程修正、自动结果与目视边界；没有开展性能实验。源码基线为 `72d2e4a3275e782418f59142e2f2ea1f01be4120` 加原有工作树，不能以 HEAD 代替最终源码身份。

## 问题

阶段 A 应闭合事件线拒收、逐绘制线宽被整批参数覆盖、normal 被内容名称解释三个缺陷组，并实现使用者接受的最小姿态与权限合同。其他八族、GPU 根因与公开二进制授权不在本轮闭合范围。

## 方法

先以真实接收和捕获测试锁定基线，再逐入口迁移。合同只使用 JDK 类型，来源适配器登记身份；原生通过版本化描述符消费。使用真实 DLL 的独立 JNI 测试与 Java 捕获夹具检查展开，原生及 GPU 回归检查既有规则，项目内探针执行原始 emitter 与包装器接缝。详见[工程合同](../design/mechanism-line_capture.md)和[专项验证](../../validations/regression-line_capture/validation.md)。

证据根为 `D:/Workspaces/Scratches/RadianceSemanticsAudit/`。相对 HEAD 的完整补丁、原始字节源码清单与快照位于 `D:/Workspaces/Scratches/RadianceUnification/`，最终清单包含必要未跟踪文件、构建事实、固定子模块及发行包身份；不含本机配置或运行环境。

## 发现

### 差异与处理

| 分类 | 发现与证据 | 处理 |
| --- | --- | --- |
| 明确缺陷 1 | 世界接收器拒收四种线；基线 9 项中对应回归失败，普通 BufferSource 也会丢失发射姿态 | 自有线批次在生产阶段封口前提交，目标和帧代际继续校验；不排空别人的表面缓冲 |
| 明确缺陷 2 | 普通实体/BER 的批次厚度覆盖来源，影子计数按显式来源登记 | 每绘制采集原始状态与厚度，普通/调试标尺分别保留 |
| 明确缺陷 3 | 原生按内容名称把 normal 当截面参考 | 独立参考字段；normal 只保留切线含义，原生无此名称分支 |
| 直接同类缺口 | Sable 默认标量转发会丢失线条矩阵；只读取基底存在性不足以发现 | 仅对真实线消费者转发矩阵，非线保留原语义；探针要求该来源无标量回退 |
| 历史约束 | 相同顶点数仍可能更换来源布局 | 线布局身份纳入历史判定；不兼容时不读取旧位置，不含线的实体沿原规则 |
| 已接受语义选择 | 未登记 listener 不自动获得 WORLD_RASTER 权限 | [adr-2](../design/adr-2-world_raster_permissions.md)；Veil 固定缓冲仍在生产阶段排空，后处理单独登记 |
| 待决定 | End Sea 是现有非线世界层，不能称为纯屏幕效果 | 保留现有行为并提出 [adr-3](../design/adr-3-end_sea_transport.md)，不趁线条整理静默取消 |

有意差异分五类：世界物理厚度与像素光栅线不同；普通/调试标尺及发光不同；adr-1 的查询归属保持独立；GUI/离屏仍由所属目标消费；Catnip 模型框及绳索本体属于面几何，不改造成线。

### 失败、纠正与自动回归

1. `stage-a-baseline-tests.log` 的 8 通过、1 失败确认旧接收器缺口；接收、捕获及 JNI 首轮修正后通过。
2. 首次完整门槛仅因 adr-3 状态词不符模板失败，修正为 `提议中`；没有改动模板。
3. 第二次完整门槛暴露真实 DLL 加载污染共享测试 JVM，以及 Veil Mixin 越过适配边界。JNI 行为测试移入独立 `lineJniTest`，仍由 `check` 强制执行；Veil 调用集中到适配器，没有跳过失败测试。
4. 第三次 `spotlessApply`、`check verifyRelease :native_renderer:gpuTest :render_diagnostics:prepareLineValidation` 成功。非 GPU CTest 66/66、GPU 16/16，GAME 266、独立 JNI 1、probe 26、bootstrap 11 通过且 2 项显式跳过；严格合规失败 0、警告 0。日志为 `stage-a-spotless-3.log`、`stage-a-gates-3.log`。
5. Java 实际捕获夹具交给 `mcvr_line_geometry_test.exe`，跨语言字节布局与棱柱截面交叉检查通过。有限、镜像、非均匀缩放、独立参考、宽度、来源身份和错误输入均有行为回归。

### 项目内客户端

首次进程 115016 未进入世界：旧探针只按 `capture` 方法名匹配，新增重载后无法找到四处实际操作，发生 Mixin 注入失败。此前冷启动管线创建约 148 秒，不是性能实验或 GPU 超时结论。无设备丢失；原生关闭及 Aftermath disable 有记录，不能声称完成世界保存。日志、崩溃报告和加载身份保留，未对相同失败盲目重跑。

修正探针为精确方法描述符，并增加 ASM 字节码回归，检查四个真实操作都存在。该回归首次把注解数组误按单项读取而失败，按实际注解结构修正后 probe 27/27 通过。随后启动进程 18164；产品代码相对第三次门槛不变，只有探针修正与覆盖扩展。

进程 18164 于 00:33:29 进入世界，执行负缩放/非均匀缩放和组合旋转、端点 RGBA、宽度 1/4、取消阶段、快速转相机、1280×720 后恢复 1920×1080，以及一次客户端资源重载。00:34:15 保存退出，00:34:19 重进同一测试世界，00:34:27 正常停止。冷启动预热不算世界验证时长；两次退出均有全部维度保存，native trace 以 `normal-device-close result=0` 和 `END` 结束。Gradle 与客户端退出码 0；Aftermath disable 成功、dump 0，没有真实设备丢失或 GPU 转储。

| 来源 | 样式 | 顶点 | 非法输入 / 标量回退 | 原生排队 / 构建批次纳入 |
| --- | ---: | ---: | --- | --- |
| Sable 包装器 | 3,462 | 13,848 | 0 / 0 | 3,462 / 3,462 |
| Simulated 拉杆原始 emitter | 3,462 | 332,352 | 0 / 0 | 3,462 / 3,462 |
| Simulated 绳索原始 emitter | 3,462 | 166,176 | 0 / 0 | 3,462 / 3,462 |
| NeoForge 事件线与混合宽度 | 10,386 | 27,696 | 0 / 0 | 10,386 / 10,386 |
| Veil listener 包装接缝 | 3,462 | 13,848 | 0 / 0 | 3,462 / 3,462 |

所有来源的原生排队与构建批次纳入数相等；取消阶段没有增加这三个 NeoForge 批次/回调之外的原生提交。`NATIVE_BUILT` 表示加入 entity/BLAS 构建批次，不是逐线 GPU 完成标记。审计关闭时 unknown intent、dropped 都为 0，`outputIncomplete=false`。

本场景没有触发旧固定宽度分支的来源，故运行中的宽度影子差异为 0，不能声称旧路径在所有来源等价。接收缺陷由基线失败和新原生接收记录判别；宽度、截面参考与镜像差异由合同/夹具判别。Create 完整轨道/输送机交互、真实 Sable 宿主选择姿态及所有原版实体/BER 尚无本轮完整交互证据，保留目视项，不把来源 ID 的 JNI 等价测试冒充它们的完整运行验证。大坐标在合同向量中覆盖，本轮没有将世界搬到边界坐标。

启动伴随器为 `correctnessOnly=true`、`frames=0`、`activationGuard=applied`；其性能观察器未绑定，不能用该观察器的 `worldEntered=false` 否定由探针和游戏日志确认的世界流程，也不计算性能。设置为 Vanilla PT、SHARC、RR Balanced、FG 关闭、Reflex 开启、主音量 0；Advanced 的证据限于共用合同、shader 编译和 GPU 回归，没有新的 Advanced 世界流程。

世界验证时的配套发行 JAR 为 `D120323EAABC6BBDC2443940B75B94ACD06AD7292D4B807E3E4DE079E4178F3B`，DLL 为 `04DA0F2DF818EFB6288FFAB20AA758D1C208A6AD9B2E6A9808DE98853D8457D6`。构建 DLL、JAR 内嵌、实际提取/加载 DLL 一致。开发运行的 GAME 来自本轮 classes，不冒充发行 JAR 的独立安装启动；发行结构另由 `verifyRelease` 检查。

最终收尾的 `spotlessApply`、`check verifyRelease verifyCompliance --strict` 均成功，GAME 266、独立 JNI 1、probe 27、bootstrap 11 通过且 2 项显式跳过，非 GPU CTest 66/66，严格合规失败 0、警告 0。对应日志为 `stage-a-spotless-4.log` 与 `stage-a-gates-4.log`；原生与 shader 产品逻辑未变，GPU 16/16 沿用本阶段第三次门槛的同一 DLL 证据。

最终重新打包使外层 JAR 哈希变化：仅两份内嵌 PT ZIP 的成员时间戳变化，Advanced 144 项、Vanilla PT 109 项的名称和内容逐项相同；全部 143 个运行库索引项中只有这两份 ZIP 哈希改变。GAME 哈希不变，668 个类与 120 个预编译 SPIR-V 均匹配构建输出，DLL 不变。实际运行的索引为 `784a70ae15e832e6b461cdf1df5fb6478b50581d2d94726c7b02fafff6da2eeb`，最终包索引及外层 JAR 哈希以检查点清单为准；没有声称新索引路径已再次实际加载。比较读取已回收的本用例副本，没有恢复或启动它；证据为 `stage-a-final-repack-comparison.json`。

运行文本与配置、原生关闭跟踪、逐来源审计和首次崩溃报告保存于 `validations/regression-line_capture/result/stage-a-20261017/`，清单逐文件校验 SHA-256。对应的复制客户端和运行库在记录结论后送入回收站；没有操作其他客户端、Prism、旧 MCVR 或任何 Git 引用。原始发射器为测试构造的最小接缝，不是使用者失败姿态的目视复现。

## 结论

产品语义、自动行为与客户端结果分别判定。真实来源完整交互、两套 PT 最终画面与运动残影仍须按[目视矩阵](../development/testing-line_capture.md)观察；计数不等于 GPU 已执行，更不等于视觉通过。没有性能 A/B，不推导本次线布局增加的成本；每顶点额外 16 字节及每绘制描述符在工程文档中明确保留。
