# 绘画局部几何与加速结构持久化实验

2026-10-19，按使用者授权优先消除完整重复生产，实施绘画局部网格复用，并以同一冻结城市副本核对正确性及整帧收益。本记录将源几何一致性、实际工作减少、性能和人工视觉分别判断。

## 问题

既有烘焙模型持久路径不覆盖 `PaintingRenderer.renderPainting`。最新参考窗口每帧有 223 次绘画绘制、13,632 顶点、1,744,896 逻辑输入字节及 223 个动态 BLAS 输入；绘画约占剩余动态 PBR 顶点的 59%。不能将原版逐格、带厚度的六面网格改为两个矩形，也不能冻结逐格光照或取消材质回调。

物品框已复用局部模型，但仍执行源模型回调和精确 recipe 比较。其模型、tint、事件与源数组可变性不允许只凭模型身份删除原流程；本轮不同时扩大物品框缓存或修改全局场景表。

## 方法

### 实现合同

- `PaintingModelCapture` 只接入准确的原版 Painting 实体及其 renderer、普通实体所属的原生 PBR consumer、合法均匀姿态与标准 atlas sprite。现有 Mixin 插件在 `acceptTargets` 选择阶段检查两个实际来源类：本配置拥有它们且其他配置没有声明同一目标时才发布资格；信息缺失和额外目标保留完整路径，已拒绝的资格不能被后续选择覆盖。只发布一个进程内状态跨越插件／GAME 模块边界，不依赖可选的运行时 ClassInfo 缓存。光栅预览、轮廓包装、其他 consumer、继承 renderer／实体和不满足资格的绘制保留原路径。该守卫不声称识别任意外部 ASM、晚注册的改写或热交换。
- 缓存未命中时仍调用原版私有生成方法，在局部单位姿态中生成全部原有顶点。逐格光照在每次绘制时按原坐标和顺序读取一次；生成阶段通过有界、可嵌套恢复的重放使用同一组结果。源查询顺序或拓扑不符时拒绝缓存，保留原生成路径，不吞实际 renderer 异常。
- 几何身份包含尺寸、前后 sprite 身份及 UV 边界、RenderType、纹理 owner 和完整逐格光照。缓存上限 512 项、16 MiB，120 帧未使用退休；本帧已使用项不被淘汰。世界和资源 epoch 变化通过既有刚性资源生命周期清空。
- 每次仍捕获实际材质状态、当前变换与所有者；沿用同一 `Geometry/Draw/Batch` 和 native 持久 BLAS、历史身份与实际 GPU 完成退休，不修改 shader、查询语义、几何范围或 AS 算法。
- `radiance.rigidPaintings` 为独立启动开关，最终默认 true，且仍受 `radiance.rigidModels` 总开关和上述资格守卫约束；材质批量化和拓扑工作线程保持关闭。中间原型默认 false、实验显式开启，最终版另测默认值，不混用构件。
- 缓存满且不能淘汰本帧使用项时执行完整生成，但重放已采样光照，避免同次绘制重复读取。嵌套后重新检查 ordinal 容量。原动态路径和持久路径均为 `normalOffset=false`，不因迁移移动表面。

### 验证边界

7 项绘画单元回归覆盖逐格读取顺序、四种水平朝向、负坐标 floor、光照变化、精确重放、尺寸／UV 身份、完整回退的嵌套恢复和原始异常不被覆盖。另 4 项直接经过生产 `MixinPlugin.acceptTargets`，覆盖正常选择、不改输入集合、外部目标拒绝、缺失信息、拒绝保持及点号／斜杠命名。显式隔离探针使用真实原版生成方法比较变换后的位置、法线及其余材质字节；该双路径验证不进入正式性能窗口。资源重载前后分别核对模型身份和比较结果。

对照复用 `dynamic-baked-comparison.zip`，SHA-256 为 `468B12B806BB2D705B23C9A123F88439BE00A8E02DAAE2A9062BFB86A4CC0FE5`。32 视距与模拟距离、1920×1080 输出、Vanilla PT、RR Balanced、SHARC、Reflex 2；FG、VSync、独立区块发光收集关闭，主音量和音乐 0。1080p 是输出尺寸，不能称为原生全分辨率追踪。每次恢复相同初始世界并预热、等待队列平静；不冻结实体和动画，不将逐帧样本当作独立重复实验。

证据保存在 `validations/performance-scene_diagnosis/result/painting-20261019/`。项目内默认轻量取证及 Aftermath 保留；自然设备丢失停止并保全证据，不自动重试，不操作外部客户端。

## 发现

### 原型正式窗口

同一原型 JAR `4064D878FB976CE3FF392C1C33AE4616E268B7B1F4DBDD4C1503B9FD51BD8999`、core `B023613AE93F98B4D54433ADB97D78D50DB1A380D4F8403F83FBE580F30A712C`，关闭／开启／开启／关闭的四个独立进程各采样 60 秒 `FRAME_ONLY`。没有逐 draw 比较、生产者 census 或 JFR；保留相同基础取证及帧记录。

| 案例 | 开关 | 有效帧间隔数 | 均值 ms | p95 ms | p99 ms |
| --- | --- | --- | --- | --- | --- |
| reference-a | false | 2,536 | 23.653 | 26.529 | 31.530 |
| candidate-a | true | 2,779 | 21.584 | 24.581 | 28.694 |
| candidate-b | true | 2,914 | 20.588 | 23.505 | 26.412 |
| reference-b2 | false | 2,324 | 25.812 | 31.543 | 38.659 |

按窗口等权平均，24.732→21.086 ms，减少 3.646 ms／14.74%，其倒数约 40.43→47.42 真实 FPS。两个开启窗口都低于两个关闭窗口；仍只有每种配置两个进程，不把数千帧当成数千个独立实验。最后一个关闭窗口因存储预检及证据精简延期，活跃实体和其他 GPU 进程仍是运行间波动来源。

### 实际消除的工作

另两个 30 秒 `TIMED`、producer census 窗口只作工作归因，不混入正式平均值。每帧 223 次绘画调用保留，但绘画动态顶点 13,632→0、动态输入 1,744,896→0 逻辑字节，绘画动态 BLAS 输入 223→0；223 次持久 draw 引用同样 13,632 顶点。不是先生成完整数据再 hash，也没有删除画背面、厚度或逐格网格。

| 同城工作量 | 关闭 | 开启 |
| --- | --- | --- |
| 全部动态 PBR 顶点／帧 | 23,065.17 | 9,432.37 |
| 全部动态逻辑输入字节／帧 | 2,952,341.65 | 1,207,343.71 |
| 动态 BLAS 输入／帧 | 407 | 184 |
| 持久 draw／帧 | 494 | 717 |
| warm 常驻模型数 | 147 | 286 |
| TLAS 实例数 | 18,774 | 18,774 |

绘画自身 renderer CPU 约 0.682→0.355 ms，绘画材质面状态约 0.895→0.905 ms，后者没有被删掉。Java 几何打包自身约 1.818→0.993 ms；native 拓扑约 0.713→0.124 ms、BLAS 录制约 1.357→0.650 ms。native 时间嵌在 Java 中，不能直接相加。

GPU 时间戳区间的实体 BLAS 约 0.647→0.290 ms、TLAS 约 0.297→0.260 ms，世界 PT 约 16.128→12.674 ms。两条路径均使用既有 `PREFER_FAST_TRACE`，不是改变构建标志的结果。区间不是纯 ALU 时间，尚未将驻留、外部抢占和 BVH 访问变化单独归因；不把 PT 区间差独占归给本改动。元数据逻辑上传仍约 15.05 MB／帧，未实施全局常驻场景表。

四个正式进程从启动到关闭的整卡显存峰值依次为 14,752／15,264／15,686／15,263 MiB，不是本进程分配或匹配稳态 census。本轮不能据此声称降低显存，增加常驻模型是明确的资源取舍。

### 最终构件和回归

加入来源资格守卫、满容量重放回退与 ordinal 复查的首个候选 JAR 为 `B36112BB21A342E0C09E97BF20FDC6BDBE1B45BE233EAD64F611BF3CCFC4BD78`，内嵌 GAME `70AD63BD2308BAC838EB5853417A5231921EBCE474ABE0EFC40096802AE593A2`，core 沿用上面的 `B023…`。701 个开发 GAME 类与发行 JAR 内嵌类逐字节一致，实际 ModDev 加载仍与真正的安装启动分开。

`spotlessApply check verifyRelease verifyCompliance --strict` 通过：GAME 334、probe 41、line JNI 4、bootstrap 11 通过／2 项既有跳过，非 GPU CTest 74/74；JNI 22 头文件、Maven 开发构件和分发核对通过，严格合规零失败／零警告。另执行持久模型 GPU 1 项，以及构建生命周期／实例 2 项，3/3 通过。首次误用 `Release` 配置导致三个执行文件找不到、全部未运行；核对实际 `RelWithDebInfo` 后执行通过，原日志保留，不算产品失败或 GPU 故障。完整检查较慢期间的 Gradle 栈为输入文件树遍历，没有放宽检查。

该候选的 `final-functional` 在重载前因零缓存比较样本被探针拒绝，后续性能运行随即停止，未损坏几何。静态核查确认 Mixin 的 `ClassInfo` 虽然类与方法为 public，其包未从依赖的 JPMS 模块导出；但首个候选未记录具体 catch／空集分支，不能把现场回退原因完全定为模块拒绝。先改用固定版本导出的 `Mixins.getMixinsForClass`，其字节码内部读取同一 applied-mixin 集合，不添加 exports、反射绕过或放宽资格条件。对应依赖 SHA-256 与模块／访问器字节码见 `mixin-public-api.json`；初步原因判断的收紧见 `source-guard-findings.json`。

失败流程不是设备丢失，保存所有维度、`normal-device-close` 与退出码 0 有原始证据，但未完成约定重载和功能比较，因此不算功能通过；receipt 的 `normalStop=false` 保持不改。诊断日志调整为先记录有效资格及 mixin 名单再判断样本，便于失败时直接定位。随后构件与性能另记，不能将上述原型窗口冒充最终构件性能。

第二个候选 JAR `789B1F1BFC74026595B19F0BAFC619D2E2C380D725F9E565823F6C4B1C62E3CA` 的 `final2-functional` 显示 `enabled=true compatible=false`，两个运行时元数据集合均为空，仍安全回退旧路径并被零样本门槛拒绝。实际启动使用 `net.fabricmc:sponge-mixin:0.16.4+mixin.0.8.7`，不能只按编译 classpath 的 `org.spongepowered:mixin:0.8.7` 推断运行时状态。没有放宽为空时也缓存，而是改用现有插件的选择回调；实际版本、字节码和直接／内嵌模组的定向扫描见 `runtime-mixin-target-contract.json` 与两个 `*-source-target-scan.json`。该失败也未进入性能窗口，保存和 native 关闭证据独立保留。

最终采用选择回调的产品 JAR 为 `A667187A61305E67D1E95F37ECA6F8ED3D7CE5F62710E355E687DC3763BF910B`，GAME `612DA044958D3EBC7C52FBB89CCBB5842A6F802757356421A92CC03E29DB329E`，core 仍为 `B023…`。最终完整命令再次通过，GAME 337、probe 41、line JNI 4、bootstrap 11 通过／2 项既有跳过、非 GPU CTest 74/74；严格合规零失败／零警告。之前的错误配置与两个被拒绝的资格候选不从历史中删除。

`selection-functional` 使用 JAR `5D095315EF3B1FF22DC03DE97C0F0C997BB195FEB85B6BC86182ECC6A15C23C4`，其 701 个产品类、内嵌 GAME 及 core 与最终性能版一致；重打包的容器哈希分别保存，不混称同一 JAR。默认属性未显式覆盖，重载前后均记录 `enabled=true compatible=true targetSelection=independent`。各阶段比较 512 draw、31,056／32,016 顶点，共 1,024 draw／63,072 顶点，变换后位置和法线最大误差均为 0，其余材质字节完全一致，抽样模型身份在重载前后不相交。两次既有材质／GPU 像素合同通过、字体违规 0，完成全部维度保存及正常 native 关闭，未发生设备丢失。

功能核对只证明实际源输入及此来源的资源重载，不代替最终图像、任意自定义绘画、连续相机运动、动态材质包或长期稳定性。正式窗口关闭双路径核对及 producer census，仍在窗口外记录实际开关与选择资格；最终帧时间另按 `retained-reference`／`retained-default` 记录，不拼入原型 ABBA。

### 最终同包帧时间

最终两次各 60 秒 `FRAME_ONLY`，与功能核对分开，JAR 均为 `A667…`。实际加载 DLL 均为 `B023…`，开关关闭／默认开启及 `targetSelection=independent` 均在窗口外记录。两个进程从相同夹具恢复，开始时 Java／native 队列均平静，无失败帧、帧号缺口、writer／native／容量丢弃；完成 F3+T、各维度保存及正常 native 关闭，没有设备丢失。

| 案例 | 有效帧间隔数 | 均值 ms | 均值倒数 FPS | p95 ms | p99 ms | 最大间隔 ms |
| --- | --- | --- | --- | --- | --- | --- |
| retained-reference | 1,906 | 31.477 | 31.77 | 38.217 | 41.384 | 47.449 |
| retained-default | 2,231 | 26.886 | 37.19 | 31.542 | 32.998 | 43.862 |

最终配对平均间隔减少 4.591 ms／14.59%，p95／p99 同向下降；与原型 ABBA 的 14.74% 方向一致，但不合并成同一构件的多轮平均。最终两窗整体比原型慢，外部活动、活跃实体和驻留仍限制绝对帧率解释；不把历史 47.42 FPS 与本次 31.77 FPS 拼成一对，也不把帧样本当作独立进程重复。所有正式结果均保留相同探针与基础故障诊断，不是无插桩发行包的手工测量。

最终两进程整卡启动至关闭峰值为 15,508／15,356 MiB，仅是全卡观察，不能当作本进程显存差值。本轮没有再跑 1440p／2160p，也没有证明 60 FPS、画质完全等价或长期稳定。`retained-performance.json` 保存完整分布、配置、质量检查和身份。

### 身份、存储和保留边界

`candidate-source-manifest.json` 与 `final-product-source-manifest.json` 分别固定原型与首个来源守卫候选。后者聚合指纹 `fdf4ea02d088b45577f111e10bb89424f1137bd91db13fc799416ef5171030b4`；基础档为 `../cpu-tasks-20261019/prototype-source.zip`，SHA-256 `333ddec37cbcdaa1578d0bea5cef1196faa173a803ac7b92872b41daf9a474e9`。各清单包含必要未跟踪源码、测试、配置和 14 个固定 gitlink；增量逐条读回验证。规则是原始字节 SHA-256，无文本规范化；按路径排序，以 `path NUL decimal-size NUL lowercase-sha LF` 聚合。维护文档完成后再固定完整最终清单，不回填自身哈希。

`retained-product-source-manifest.json` 固定实际测量的最终产品范围，聚合指纹为 `0523d45321cfd40fe4905e994ba04cf791f631e88813bcd5a868283ea60ccbbc`；收尾文档及完整源码由 `final-source-manifest.json`／`final-source-delta.zip` 固定，产品输入与测量快照逐文件核对不变。`retained-vs-selection.json` 核对功能与性能版的 701 个产品类及 core。打包资源另按内部路径和内容 SHA-256 比对；两个 PT pack 仅 ZIP 目录项时间／扩展信息不同，shader 文件与 DLSS 内容未改，不把目录项时间当成 shader 变动。

运行收据保留 JAR、core、实际加载 DLL 路径、尺寸、fixture、参数和配置。关闭后，验证复制的运行库与闭合世界按哈希／夹具确认可再生再送入回收站；没有删除来源世界、输入或唯一故障证据。正式原型 JAR 单独保留于 `build/painting-20261019/performance.jar`，避免被最终 JAR 覆盖后失去可回查身份。

存储预检拒绝 `diagnostic-on` 与 `reference-b` 两个案例，均未启动客户端；改进余量估计并清理可再生缓存后使用新案例名，未自动重跑客户端故障。只核对启动前与收尾大小，没有连续峰值占用采样，不宣称旧余量估计保证全过程未超限。

为保全原始证据并腾出空间，每个已结束 profile 的 `timings.csv`、`counters.csv` 写入同目录 `evidence-archive.zip!timings.csv`／`!counters.csv`，索引记录原路径、字节数和 SHA-256，逐条读回一致后才回收原文件。`frames.csv`、`producers.csv`、质量结论和元数据保持原文件。前一材质桥接实验的 `diagnostic-reference`／`diagnostic-candidate`，以及 CPU 任务实验的 `baseline`／`cpu-work-reduced` 同样只归档这两份冷表，不改变其事实或质量结果。现有 `profile_quality.py` 不自动读 ZIP，复算须按索引恢复原 CSV；本轮聚合使用已核验的 `quality.json`。不能根据 ZIP 条目时间反推原始文件时间；最终两案例的索引另记录原修改时间纳秒值。

## 结论

值得保留：实际消除绘画重复生成和动态 BLAS 工作，原型四窗与最终配对分别观察到约 14.7%／14.6% 的整帧时间下降。最终版采用限定来源、显式回退的默认路径，后续是否扩大仍由对应数据决定。没有实现全部实体持久化或全局稳定场景表，也不代表 60 FPS 已达成。没有新的人工视觉、移动路线、长期稳定性或 GPU 根因结论；没有操作 Prism、旧 MCVR、暂存、提交或推送。
