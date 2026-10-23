# 存储清理补充核验

调查日期：2026-10-16。本记录执行外部评审第 15 号指令，补充[首轮存储清理](audit-storage_cleanup.md)，不改写首轮审计正文。未提交、推送、启动游戏或操作 Prism。

## 问题

首轮仍保留 8 个原生构建身份，另有未纳入上限的旧回放目录。使用者进一步授权逐套处理超额构建、核实并归档本线程的回放资料，同时规定自动测试与手工实例的默认配置。

## 方法

- 核对当前案例指针、待验状态、实际二进制和真实 GPU/进程转储；跟踪日志和 JFR 不视为 GPU crash dump。
- 用同一 JFR 的 SHA-256、实际加载模块记录与历史研究交叉确认回放资料属于本线程；不访问旧 MCVR 仓库。
- 先登记二进制哈希与源码快照，再删除可再生文件。冷文本和捕获缓冲按调查目录使用 ZIP Deflate，按 SHA-256 去重；逐条读回匹配且原文件未变化后才删除原文件。文档明确引用的原始文件、JFR 与源码 ZIP 保持原文件。
- 在同卷重命名之前和之后核对所有剩余文件的 SHA-256；任务新生成的索引另记原位置和迁移位置，旧证据内容不批量改写。

## 发现

### 旧构建

| 核心 DLL 前缀 | 处理依据 | 处置 |
| --- | --- | --- |
| `dedb2b00` | 首组 SDK 验证包已由最终 `03c77296` 替代，无待验任务或真实转储 | 删除旧 DLL、JAR |
| `f9ba4c1e` | 历史失败基线，不是当前待验包；没有真实转储，后续诊断已换包 | 删除旧 DLL、JAR；根因未知仍保留 |
| `1e511ca3` | 已完成迁移的旧包，已有后续验收和投放版本 | 删除旧 DLL；JAR此前已不在 |

实际删除 5 个文件，共 0.435 GiB，现存 5 个原生构建身份。完整 JAR、DLL、PDB 哈希与源码快照在 `D:/Workspaces/Artifacts/Radiance/storage-cleanup-20261016/manifest.json` 的 `review15.builds`。PDB 已在首轮删除，哈希继承删除前记录，不冒称本轮重新读取；旧迁移 JAR 的哈希来自原验收记录，当前文件不存在。不把删除旧包写成 GPU 故障根因闭合。

### 回放证据

`D:/Workspaces/Artifacts/MCVRSceneReplay/` 从 16.530 GiB 收敛为 0.793 GiB，迁入 `D:/Workspaces/Artifacts/Radiance/history-archive/MCVRSceneReplay/`，原顶层目录已不存在。删除 2,542 个运行环境、旧程序、重复运行库与缓存文件，共 5.824 GiB；17 组冷证据从 10.542 GiB 压为 0.624 GiB，16,860 条原文件记录全部读回校验通过，没有失败或审批阻塞。

33 个原文件保留，包括原始 JFR、14 份源码 ZIP、当前文档明确列出的分析文本/JSON和少量不能证明可再生的二进制测试记录。其余画面与场景捕获缓冲只改变存储形式，索引可恢复原路径和原字节。源码 ZIP、程序 DLL/JAR/PDB 不混入冷证据 ZIP。

当前证据根为 `D:/Workspaces/Artifacts/Radiance/history-archive/MCVRSceneReplay/20260924/`。原研究中旧绝对路径保留历史含义；当前入口在[项目现状](../project/phase-1-before_vanilla_closeout.md#存储清理)更新，迁移对应关系、保留原文件和逐组索引见补充清单，不重新启用已退休的回放产品。

### 占用与保留边界

| 范围 | 补充前 GiB | 补充后 GiB |
| --- | ---: | ---: |
| 回放资料 | 16.530 | 0.793 |
| 外部合计，补充前也纳入旧回放目录 | 24.010 | 7.870 |
| 仓库计入上限部分，不含 Git 与原生第三方源码 | 14.851 | 14.852 |
| 冻结的旧运行目录 | 1.918 | 1.918 |
| 验证实例与结果 | 1.950 | 1.950 |

外部合计低于 8 GiB 硬上限，高于 6 GiB 软上限；SDK 为 1.719 GiB，扣除后仍为 6.151 GiB。故当前软超不再只有 SDK 一项，不能为消除数字继续删除唯一证据。

大小按文件逻辑字节计算，不跟随目录联接，不是卷实际释放空间。原始调查 ZIP、在用 SDK、输入、当前投放、待验文件与受保护 Git 数据未为软上限而继续删减。所有硬上限均满足；外部软上限的剩余超额单独记录在清单，不能继续沿用首轮“仅 SDK 造成软超”的说明。

### 新实例约定

自动验证、自动化打包客户端及基准默认 1920×1080、主音量 0；手工实例默认 2560×1440、主音量 50%、音乐 0、玩家名 `RecRivenVI`。启动参数与准备任务共同处理窗口尺寸，清除旧窗口覆盖值；不手工编辑生成实例。

Modrinth 有当前 Target 适配版的 CustomSkinLoader Universal 15.1 和 Zume 1.2.2，均已下载、验证官方 SHA-512，并登记实际元数据及 SHA-256。二者只加入手工运行，不进入自动基准组合、产品包或兼容声明；可选依赖不额外安装。配置来源与运行边界见[构建规程](../development/procedure-build.md)。本轮没有启动客户端，不能声称已验证皮肤加载、缩放操作或实际画面。

### 自动检查

执行 `gradlew.bat --no-daemon --init-script <补充记录目录>/verify-profiles-and-tests.gradle spotlessApply check verifyCompliance --strict` 成功；临时 init 脚本只做实际配置断言并强制 JVM 测试执行。GAME 257 项、探针 26 项、bootstrap 11 项及模板 JVM 测试 162 项通过，bootstrap 另 2 项 GPU 入口测试跳过。原生 CPU CTest 66/66，诊断采集器和采集辅助各 2/2 通过；发行 JAR、JNI 和开发构件检查通过。严格合规失败 0、警告 0；编译仍保留既有 Unsafe 与弃用 API 提示，没有隐藏或放宽。

实际配置对象确认四个手工组合、四项自动验证和三个原生客户端入口的尺寸/名称/模组边界；未执行游戏启动、GPU 测试或性能实验。

首轮完整检查在 T-02 处失败：固定音量字符串被识别成版本号。按既有数值变量插值方式修正，未修改模板或放宽检查；失败日志保留为 `supplement-review15/verification.log` 和 `strict-first-failure.json`。

完整机器记录位于 `D:/Workspaces/Artifacts/Radiance/storage-cleanup-20261016/supplement-review15/`。其中 `replay-provenance.json`、`build-dispositions.json`、`replay-binary-identities.json`、`archive-receipts.json`、`move-receipt.json`、`retained-originals.json` 和 `integrity-final.json` 分别固定来源、处置、身份、归档、迁移和保护边界。

### 末次归档内容复核

本段为本轮收尾追加，保留上文迁移阶段的计数与体积。末次审查发现 3 份 `radiance/cache/vulkan/pipelines/pipeline-v*.bin` 路径下的再生管线缓存曾被误归为捕获缓冲。已记录它们的原始哈希，重写涉及的 3 个 ZIP；逐条读回确认其他证据 SHA-256 全部不变后才替换旧 ZIP，未通过任何替代删除方式绕过审批。

最终冷证据为 16,857 条原文件记录，原始数据 10.532 GiB，ZIP 0.616 GiB；再生文件的最终分类增加 3 条、共 0.010 GiB。回放保留目录当前约 0.785 GiB。初次计划、删除记录、修正前索引与修正前归档回执保留；最终处置以 `cache-prune-receipt.json`、`cache-prune-summary.json` 与更新后的 `archive-receipts.json` 为准。全局最终占用以清单的最新统计为准。

## 结论

三套超额历史构建已按授权处理，回放资料已纳入同一外部存储范围。唯一证据保持原文件或经校验归档，旧审计结论和设备丢失边界保留。剩余外部软超需要后续保留策略决定，不以删证据消除统计问题；本轮不启动新的渲染、性能或故障调查。
