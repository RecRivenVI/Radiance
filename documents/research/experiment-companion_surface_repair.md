# 同类附加层与 UV 动画修复

2026-10-17，依据已完成的同类路径核验修复确定缺口，并先核对条件风险。未启动游戏、未进行性能实验；视觉验收由使用者执行，不提交或推送。

## 问题

旋风人风层未传播主 UV 变换，且缺少来源颜色／丢弃阶段登记。监守者、旋风人眼睛和普通装饰使用不同于加法眼睛的 source-over 覆盖，旧最近命中及完整模型 eyes 合成不能保证重合部分模型的覆盖顺序。末影龙的 renderer 不继承 LivingEntityRenderer，实验性持久部件路径可能绕过眼睛合成所需的完整捕获。

旧包 JAR `65A2E09463350081597CC814F56B5781617D5A1B71225ED234E53EB53BC8E422` 的用户验收和旧审计分别保留。源码／模型接缝证明重合与合同缺口，不把所有对象的实际画面都写成已复现失败。

## 方法

复用现有 Audit、JDK 21 生产类接缝及原版 1.21.1 来源。对照 WardenModel 和 BreezeModel 的实际部件、位置、UV、颜色、alpha 和绘制顺序；只实施已知来源程序与混合合同。使用实际原生生产助手、GPU 覆盖采样接缝、两套真实 pack 入口编译和 SPIR-V 布局验证，分别记录证明层级。

## 发现

### 实现范围

- breeze_wind 按 shader 身份捕获当次主 UV，保留普通覆盖、颜色和完整调制后 0.1 丢弃，不改成 energy_swirl 的加法／全亮材质。
- 登记 armor_cutout_no_cull 的完整调制后 0.1 丢弃；不把它继续当 0.5 cutout。EQUAL 深度 decal 不进入有序覆盖资格，仍沿原路径，不能据此声称其深度语义已经修复。
- 已知重合层使用[有序表面层合同](../design/mechanism-ordered_surface_layers.md)。同一对象的最终位置精确匹配、同绕序三角形共享一个物理界面，每层保留独立 UV、RGBA、材质、发光及来源顺序。部分模型和不同 atlas UV 可以匹配；反向薄片、不同对象和近似重合不合并。
- 普通项圈、花纹、裂纹及普通盔甲层不被强制发光。监守者与旋风人全亮层的辐射保留到世界路径，不加显示专用的发光例外。加法 eyes 及 glint 继续保留各自合同，不套用普通覆盖公式。
- 新集合按原生 Entity 独立持有不可变缓冲，沿重要上传队列和现有帧／历史退休；只对相关实体回退完整捕获，不使用全局 GPU idle。未穿有效槽位盔甲的实体不因 renderer 支持盔甲而退出持久模型路径。
- 末影龙增加完整捕获保护。混合加法 eyes 与普通装饰的自定义所有者保持既有路径，不承诺任意自定义混合等价。
- 整批含混合／自定义顶点材质时保持原样；非白全局 RGB 与受击／闪烁 overlay 不交换运算顺序。同一界面若夹有不支持的混合／EQUAL 操作或面归属不同，全部成员保持原路径，不能跨过它们重新组织已知层。这些边界在投放前复核发现并补回归，先前仅完成构建的 F96AD483… 初版候选未投放，其清单单独保留为 companion-pre-guard-artifact.json。
- 布局改变时拒绝不兼容的位置历史；颜色、UV 或动画 alpha 变化保留原拓扑身份。AH/CH 共用 MainRay 入口，按同一查询角色、纹理足迹和不可变光线身份选层，避免命中接受后选择另一个材质。

关键生产入口为 EntityProxy、VanillaMaterialAdapter、VanillaEmissionAdapter；原生入口为 EntityBuildDataBatch::build、surface_layers.hpp、world_prepare.cpp 和 util/surface_layers.glsl。材质集合有序覆盖采用现有 PT 的覆盖采样方法，权重的期望符合 source-over；单样本不是光栅混色截图，时序噪声、PBR 细节和实际附近照明仍需目视。

### 风险处置与纠正顺序

旧接缝确认 Warden 身体 60 个四边形，生物发光 30/30、心脏 6/6、触须 12/12、脉动 36/36 与身体重合；Breeze 眼睛 24 个面与头部 12 个面重合。采用有序材质集合，不用偏移、删除来源层或把所有装饰改成发光来掩盖竞争。

实施期间先后纠正了 Gradle 项目路径、独立测试的 Vulkan include、构建期间改 CMake 输入造成的并行再生成冲突、误插入区块分支的实体外观赋值、格式工具导入排序及测试工厂名。独立 javac 接缝发现窄泛型妨碍末影龙类型判断，已改为 EntityRenderer<?>。阶段复核又发现不能用“文件声明了 ShadowRay”判断 incoming ray，已统一 MainRay 入口并重编译。失败日志保留，没有把这些中间候选投放 Prism，也不以最早 SPIR-V 编译成功代替最终合同正确。

Veil 自定义纹理矩阵仍没有确定可达的世界 PT 生产者证据，本轮不改未知程序；任意 GLSL、不同混合、EQUAL decal、近似重合、混合加法／普通层仍保留范围边界。完整捕获回退的性能成本未测，不声称无性能影响。

### 实际自动验证

- spotlessApply 与随后定向 Java 格式应用完成；最终 `check verifyRelease :native_renderer:gpuTest verifyCompliance --strict` 成功。
- GAME 298、真实 JNI 4、probe 28 全通过；bootstrap 11 通过、2 跳过（未设置 RADIANCE_HANDOFF_RUNTIME 与 RADIANCE_PARITY_RUNTIME，未执行对应启动交接／GPU 画面对照）。最终四个 Java 测试任务均实际执行，没有把旧的四项附加层测试冒充本轮新增六项。
- 非 GPU CTest 68/68、GPU 17/17 通过，没有设备丢失。新 CPU 助手覆盖部分模型、独立 UV／RGBA／发光、有序多层、未合并图元、反向／近似面、资格列校验、历史身份、混合材质及非交换调色的保守回退；GPU 接缝每个用例 65536 样本，验证覆盖权重、顺序、发光权重和确定性。此 GPU 接缝不执行完整客户端层 renderer 或新集合 BDA 的整套世界追踪。
- 两套 pack 的 232 个入口编译与 SPIR-V 验证通过，40 个相关变体的缓冲步幅与 CPU 的 384／16／256 字节一致；实际包装的 119 个 pack 来源文件、8 个共享来源条目和 690 个编译类逐字节核对。
- JNI 头、Maven GAME、发行包元数据、154 个必需条目与探针排除通过；严格合规失败 0、警告 0。编译器既有 Unsafe、SDK 和弃用提示未被隐藏。
- 机器证据统一为 `D:/Workspaces/Scratches/RadianceSemanticsAudit/companion-*`；最终完整命令日志为 companion-order-barrier-verification.log，独立结果表为 companion-test-summary.json。

### 空间与证据

构建后工作区曾达到 16.010 GiB，回收了 10 月 14 日已结束 packagedClient 的 .radiance 提取缓存 560726413 字节；文本先 ZIP 归档并读回验证 SHA-256，配置、世界和其他日志保持原状。回执为 companion-obsolete-packaged-runtime-recycle.json，未重试此前回收被拒的验证 client 目录。随后工作区约 15.489 GiB，旧 run/dev/logs 约 1.918 GiB、验证生成内容约 2.805 GiB；外部约 7.971 GiB，沿用已记录的受保护证据／输入／SDK 例外，未超过硬上限。

## 结论

最终候选 JAR `FB3908DB0B83BE9BD71D6DC7FFB3538116D07DEB0D7359E1EBA6682F4030A8CD`，core `41656CE557962B04A366C6D957C09BC472C157775197028EA7E58A21647FEF1E`，GAME `CB3569076D4B587F492BBF16CA895D280BA064A47A360E91CA6965FC66EE0305`。构建／安装／内嵌 DLL 相同，发行 JAR 与实际编译类、shader 源码对应；身份清单为 companion-artifact.json。

完整源码使用 checkpoint-13-total-source.zip 与 companion-source-delta.zip／companion-source-manifest.json 共同固定，包含全部必要未跟踪文件和 14 个 gitlink；按原始字节 SHA-256 记录，并逐项重建校验，不覆盖旧快照。

使用者已回复“可以替换”。最终候选只在确认实例退出且持有实例锁后投放 Prism，不启动；回执为 companion-prism-deployment-receipt.json。提取文件与实际加载新 DLL 须等使用者下一次启动后核对，不由内嵌一致代填。监守者脉动、旋风人风和眼睛、普通装饰、盔甲纹饰／glint、附近照明、运动及 F3+T 仍待此包目视。没有世界运行、长期稳定性或性能通过结论；历史 GPU 根因、G3 与公开原生二进制许可边界保持独立。
