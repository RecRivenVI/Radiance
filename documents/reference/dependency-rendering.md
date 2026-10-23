# 渲染与兼容依赖

Minecraft 与加载器组合固定为 `1.21.1-neoforge`。Target 依赖的唯一版本来源是 [target.properties](../../versions/1.21.1-neoforge/target.properties)，公共 Java 库与 Vulkan SDK 的版本由[版本目录](../../gradle/libs.versions.toml)提供。升级结果和运行证据见[当前状态](../project/status-current.md)。

## Java 与兼容模组

| 来源 | 配置位置 | 使用范围 |
| --- | --- | --- |
| NeoForge | `loader_version`、`loader_minimum` | 编译版本与 Radiance 自身的最低运行版本分别声明，不以第三方模组下限替代产品下限 |
| Create、Sable、Create Aeronautics | Target 事实与 [inputs.toml](../../inputs.toml) | 编译兼容接口、契约测试与额外运行实例；实际文件路径只写在 `local.toml` |
| Flywheel、Ponder、Registrate | Target 事实 | 跟随 Create 内嵌版本 |
| Veil、Sable Companion | Target 事实 | 跟随 Sable 内嵌版本，不另外叠装独立最新版 |
| Aeronautics、Offroad、Simulated | Target 事实与 Aeronautics 输入 | 跟随父发行包，避免混合不同嵌入版本 |
| SnakeYAML | 版本目录的 `snakeyaml` | 产品配置读取，随 GAME JAR 内嵌 |
| ASM、Gson | 版本目录的 `asm`、`gson` | 诊断与盘点工具，不因工具升级改变产品运行库边界 |

兼容元数据的固定区间由 Target 事实展开。Sable 自身要求的 NeoForge 下限只约束包含 Sable 的组合；Radiance 单独运行的加载器下限保持不变。

## 兼容范围与证明方式

本节按 [adr-11](../design/adr-11-complete_drawing_scope.md) 区分已登记版本、实际验证和完整绘制覆盖。下面的键均引用 target.properties 或 inputs.toml，版本与哈希仍只从这些事实来源取值；表中的“单版本”不是对任意同模组未来版本的承诺。

| 依赖或兼容目标 | 登记范围与当前证明边界 | 验证方式 |
| --- | --- | --- |
| Minecraft | 当前 Target 的单版本；已取得阶段 0～2 证据，阶段 3 执行中，后续能力仍未闭合 | 原始来源对照、注册表、固定巡检及对应运行 |
| NeoForge | `loader_version` 实际运行；现有声明从 `loader_minimum` 起不设上限，但本阶段未重测下端或未来版本，不能称完整区间已证实 | 比较实际使用接口，候选范围两端分别编译、启动、世界／重载与接管回归；成功后登记可声明的宽范围 |
| Java | `java_version` 的构建／运行版本，不扩大成任意 JVM 版本 | 编译、JNI 与客户端生命周期验证 |
| Create | 输入 `create` 的单一发行版本与哈希，对应 `create_metadata_version`；限定组合功能有证据，全绘制巡检待补 | 按发行包及实际变换核验，来源和回退对照、Create 固定巡检 |
| Flywheel | 父包内 `flywheel_version`；已登记的单个接口哈希不等于其全部绘制已迁移 | 父包解析、实际类／实例程序／生命周期验证；变体逐项核销 |
| Ponder | 父包内 `ponder_version`；保留默认绘制语义，不恢复 Ponder PT | GUI／离屏输出、转场、裁切与重载对照 |
| Registrate | 父包内 `registrate_version`，不另扩兼容范围；不是独立绘制族 | 父包解析、模型注册／烘焙及重载链路 |
| Sable | 输入 `sable` 单版本与哈希，对应 `sable_metadata_version`；既有结构功能与本轮退回分别记录 | 逐版本核对变换、子世界坐标／生命周期、固定结构巡检 |
| Veil | 父包内 `veil_version` 单版本；模型管理器与 RenderType 修改变体尚待翻译 | 逐版本检查实际字节码、状态／程序／离屏依赖与输出，不按相邻版本推断 |
| Sable Companion | 父包内 `sablecompanion_version` 单版本 | 跟随父组合解析与相关来源巡检，不混装独立版本 |
| Aeronautics Bundled | 输入 `aeronautics_bundled` 的单一发行版本与哈希，对应 `aeronautics_metadata_version` | 父包解析、具体适配调用链、结构／GUI 与回退对照 |
| Aeronautics | 父发行包内 `aeronautics_version`，不单独放宽 | 父组合下的实际来源与场景验证 |
| Simulated | 与父包当前登记版本配套；弹簧／绳索等既有结果不等于阶段 5 已完成 | 逐版本比较几何、材质、形变、端点及结构图解输出 |
| Offroad | 与父包当前登记版本配套 | 父组合解析和本来源实际绘制巡检 |
| Voxy | 输入 `voxy` 的确切本地构建及哈希，不按 beta 名称推定其他构建 | 后端 cut／lease／LOD／材质与生命周期验证，完整闭合留阶段 8 |
| 原始／历史城市存档 | 输入 `epicsawrunner_city_original`、`newport_city_snapshot` 各自的确切哈希；不是模组兼容区间 | 副本恢复、原版升级／区域完整性、固定设置与来源证明 |
| 私有 Aftermath SDK | 输入 `nsight_aftermath_sdk` 的单版本与哈希；仅本地诊断 | 设备创建前接通、回调／关闭边界及能力报告；不证明公开许可 |
| Custom Skin Loader、Zume | 对应输入各自单版本；手工辅助，不是产品兼容目标，本轮纯原版验收不加载 | 需要使用时验证对应辅助实例，不写入产品兼容范围 |

兼容元数据只应写已验证范围。当前 NeoForge 开放上限的既有声明与本轮证据存在范围差距；本次是文档修订，保留产品原状，后续兼容范围工作需先完成接口与两端回归再收敛声明，不以此表补写通过。各深度修改者只核销具体版本及实际类变换，范围外／未知修改按 adr-9 保守退回；不能解析的硬依赖或加载器拒绝不在渲染退回能力内。

SnakeYAML、ASM、Gson 与其他构建工具不是来源绘制兼容目标，使用版本目录固定值，分别由配置解析、工具单元测试和构建检查验证，不从工具版本推导模组支持区间。原生库、Streamline／DLSS／XeSS 采用下述确切 gitlink 或官方运行库固定身份，编译、CPU／GPU 回归和 SDK 实际调用分别证明，未执行的硬件／功能边界不泛化为整个版本区间。

## Voxy 可选兼容输入

`voxy` 在 `inputs.toml` 登记为 `0.2.20-beta+1.21.1-neoforge` 的本地测试构建，SHA-256 为 `182d4a19c06cdc769cf371a361c8e83c8741764313fedb3b5dd36546cbc86932`。文件通过 `local.toml` 引用使用者指定的 Voxy 仓库既有构建，不额外复制到接近存储硬上限的外部输入目录。作为可选兼容编译输入及功能验证实例模组使用，不内嵌发行 JAR，不加入其他日常实例。运行时版本限定为已核对的此构建；是否通过以实际功能证据与使用者验收为准。

原路径若被 Voxy 的后续构建覆盖，哈希校验会拒收；需核对新构建并明确更新登记后才能使用，不静默切换测试身份。

JAR 实际元数据要求 Minecraft `[1.21.1]`、NeoForge `[21.1.251,)`，Sodium `[0.8.13,0.9)` 是可选依赖。这不提高 Radiance 自身的加载器下限，也不要求额外安装 Sodium。构建来源、静态接入方案和未闭合的材质合同见[专用 PT 后端设计](../design/architecture-voxy_pt_backend.md)。

## 手工验收辅助模组

CustomSkinLoader Universal 15.1（引导元数据 ID `customskinloader-bootstrap`）与 Zume 1.2.2 来自 Modrinth 的 Minecraft 1.21.1 / NeoForge 兼容发行版，在 `inputs.toml` 按实际元数据与 SHA-256 登记，机器路径只写 `local.toml`。两者仅用于手工实例，不进入产品包、自动基准组合或产品兼容声明；Zume 的可选依赖不额外安装。运行配置见[构建规程](../development/procedure-build.md)。

## 诊断输入

Aftermath 的私有输入 `nsight_aftermath_sdk` 在 [inputs.toml](../../inputs.toml) 登记版本、官方来源与允许的哈希，机器路径只在 `local.toml`。本地 SDK 由工具组件提取并动态加载，不参与产品链接、安装或发行包。项目内默认配置及支持限制见[GPU 故障取证](../development/debugging-gpu_diagnostics.md)。

区块加载验证使用 `newport_city_snapshot` 本地输入，版本 `3955` 表示世界的 DataVersion，而非模组版本。它包含使用者明确授权只读复制的 Newport City 与当时设置；哈希在 `inputs.toml`，路径仅在 `local.toml`，不进入产品包或源码。读取与网格重建均使用验证实例中的副本，不操作原世界。

阶段 3 的纯原版城市输入为 `epicsawrunner_city_original`，取使用者提供的 By Epicsawrunner v1.2 ZIP；其发布名称为 v1.2，存档内部为 Minecraft 1.12.2 / DataVersion 1343。来源与允许哈希在 inputs.toml，原始文件路径仅在 local.toml。原文件只读，须在验证实例中经原版“优化世界”、保持不清除缓存，再冻结派生夹具。它不替代旧调查的历史输入身份。

## 原生依赖

原生子模块的来源写在 [.gitmodules](../../.gitmodules)，精确版本为父仓库登记的 gitlink。Streamline 的版本、官方下载地址与校验值集中在 [Streamline.cmake](../../components/native_renderer/cmake/Streamline.cmake)。

- 选择正式发布；NRD 的预发布不进入常规升级。
- stb 没有正式标签或 Release；不把默认分支提交冒称最新正式版，保留已验证的固定提交。
- FidelityFX 使用现有 Vulkan 分支。官方新版 SDK 的 DirectX 后端不能直接替换此依赖；升级需要先有等价 Vulkan 接口，不能通过停用 FSR 或移除既有功能满足版本数字。
- GPU SDK 更新不授予运行库再分发权。公开二进制许可门禁继续有效。

## 构建解析

原生配置将 `VULKAN_SDK` 作为任务输入，每次 CMake 配置重新查找 Vulkan、shaderc、测试用 glslang 与 NRD 用 DXC，避免复用升级前的缓存路径。SDK 的来源和 CI 安装方式见[构建工具依赖](dependency-build_tools.md)。

`render_diagnostics` 的原生采集器随工具 JAR 产出，Target 将其中 `META-INF/natives/` 合并到 probe 资源。这样开发目录运行与模块内资源查找都能加载采集器；这些文件不进入主模组或外层发行 JAR。探针包装测试和两项启动验证共同检查资源可见性与实际挂接。
