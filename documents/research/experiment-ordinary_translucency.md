# 普通半透明可切换混合实验

2026-10-17，使用者将普通半透明重写提前并批准实现设置入口。本记录对应本轮新候选，保留先前船只候选的验收范围，不将旧包反馈转成新模式通过。

## 问题

普通 `ALPHA_COVERAGE` 原先通过 any-hit 随机接受或穿过来表达覆盖率，接受后按完整表面着色。使用者希望保留该模式，并增加真实背景参与颜色混合的可选表现；物理折射重写与优先级重复成像不在本轮。

## 方法

读取两套 PT 的主命中、材质暂存、独立反射／折射支路、Advanced 表面缓存及 NRD 合成调用。增加一份公共覆盖权重递推和背景分层追踪，复用现有材质 alpha 暂存及背景辐射通道，不扩大射线 payload 或增加每层 GPU 资源。

视频设置通过当前着色器包的属性入口切换；设置与定义、默认值保持同源。构建前按 X-12 复查仓库 15.511075 GiB、受保护的外部输入／证据合计 7.977189 GiB，硬上限通过；本轮只增加小型证据与源码增量，不外存完整二进制。

验证分别执行实际 pack 入口的两种模式编译、生产合成 helper GPU 计算、真实 TLAS 前景／背景射线，以及带最小相交／照明接缝的生产分层遍历。接缝故意使前景分支改写全局 payload，用来验证直行兄弟保留自己的查询身份、UI 所有者状态和辅助资源状态；不把它称为完整游戏光照验收。

## 发现

实现的范围和已知边界见[机制](../design/mechanism-ordinary_translucency.md)。确定分层预算后使用原随机尾部，不裁掉更远几何；接受的不透明前景不会叠加背景。传光、cutout、文字、加法、专用不受光命中组及连续折射后的采样保留既有实现。

首轮离线编译发现 GLSL 预处理常量写法不被接受，以及 Advanced 世界命中仍有独立表面结构未补字段；已按真实编译错误修正并重编。首次完整门槛发现新增 C++ 测试尚未通过 clang-format；使用 Visual Studio 的 clang-format 22.1.3 修正，没有削弱检查。期间测试 CMake 清单仍在变更，触发多项目重复配置并争用 zlib-populate 的 MSBuild tlog，构建失败；停止改写构建输入后重跑，未修改第三方依赖或绕过检查。配置回归另验证实际包的默认值、枚举映射及专用不受光命中组范围。最终 `spotlessApply check verifyRelease :native_renderer:gpuTest verifyCompliance --strict` 通过：GAME 308、JNI 4、probe 28、bootstrap 11 通过／2 跳过，原生非 GPU 69/69、GPU 21/21；严格合规零失败／零警告。两种模式各 237 份实际 pack 入口完成离线编译及 SPIR-V 校验。另有一次先行 21/21 GPU 检查和定向三夹具通过，不将重复计数相加。

覆盖率材质若带水面标记，表面评估仍可能产生真实透射。因此进一步在 any-hit 资格和材质暂存两端排除该交点，保留水面原估计器，避免水面同时进入两种分支后被旧折射合成覆盖。随后重新执行实际 shader 入口与配套门槛。

## 结论

当前为已通过自动门槛的待验候选。JAR 为 `DB5D972E5CC97D7CC3AD6E68C2788683EED37D9AD2FB1BDEFA2B0E271E2CAB3F`，core 为 `C80B26C8819B797C793B3FA1F975DB322437C953DBFBB3351E0B39DF93D3DFFA`。完整产品输入和内嵌 GAME／core／shader 身份见 `D:/Workspaces/Scratches/RadianceSemanticsAudit/coverage-blend-artifact.json`，完整源码由同目录 `coverage-blend-source-manifest.json`、`coverage-blend-source-delta.zip` 和清单声明的既有基档重建；每份文件逐项回读校验，不回填自身 SHA。

构建 DLL、runtime 安装 DLL 与 JAR 内嵌 DLL 哈希一致。core 重新链接后与船只包不逐字节一致，206 个导出一致，`.text` 也有字节差异，因此不宣称两 DLL 位级等价；本轮没有改动原生产品 C++ 源码，对应比较记录为 `coverage-native-comparison.json`。

随后核对 Prism 停止并持有原子锁投放，回执为同目录 `coverage-blend-prism-deployment-receipt.json`；其他模组与设置逐份哈希不变，未启动。上版已通过船只专项的 JAR 按原哈希保留在 ignored、自动任务生成的 `build/acceptance-retained/boat-motion-171F38FC.jar`，替换用的临时副本与本次锁按规则送入回收站，不丢失最近一次验收包。

新模式仍待使用者目视，不宣称视觉、长期稳定性或性能通过。没有启动客户端、性能实验、Git 暂存、提交或推送。公开二进制许可与 GPU 故障根因边界保持独立。

## 后续手工反馈

2026-10-17，使用者随后明确“半透明改进已验收”，同时报告部分半透明叠加明显卡顿。此反馈对应上述已投放候选的手工观察，未新增逐配置清单或进程身份捕获，不反写前述构建／投放阶段的事实。视觉通过与待查性能问题分别保留，后者已与多光源及高视距归入[同一性能专项](../project/plan-scene_performance.md)，尚无本轮采样或优化收益。
