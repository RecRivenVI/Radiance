# 本地 GPU 取证接通核验

2026-10-15 调查并接通项目内默认诊断。轻量事件与 Aftermath 基础采集已在 SERVICE 创建的实际 Vulkan 设备上启用，两项标题界面验证正常关闭；没有生成或伪造 GPU 故障，既有设备丢失根因仍未知。

## 问题

区块加载调查保留了真实设备丢失、Windows 事件和世界保存证据，但没有 GPU crash dump。使用者授权完善已有轻量取证与本地 Aftermath 基础采集，重型验证按需使用；没有授权借此继续压力复现或修改渲染语义。

## 方法

复用原有事件环、设备能力查询、checkpoint、AS 生命周期、SPIR-V 留存和外置 JVM agent。使用本机 Nsight Graphics 已安装的 SDK，经私有输入哈希校验后在工具组件内构建，不下载、安装或公开分发 SDK。

SDK 先由 `Agent_OnLoad` 启用，随后 SERVICE 创建产品设备。主要对象名称、资源地址、命令缓冲、队列和 fence 纳入有界事件，主帧批次逐个记录命令缓冲；CPU 录制与 GPU 完成分开。回调写盘、文件数和字节均有上限。真实 lost 的关闭入口最多等待 SDK 状态 5 秒、每进程一次，不新增故障 GPU idle 等待。

运行只用 `smoke-render_diagnostics` 和 `conformance-benchmark` 自己的隔离实例，主音量 0，探针在标题界面等待后正常退出。未进入世界、未测性能、未启用重型验证、未操作 Prism 或旧 MCVR。

## 发现

### 修正与失败留痕

首次组合构建失败：SDK 的 SPIR-V 解码类型要求完整 Vulkan 头文件宏；原生新增诊断还有局部变量重名、缺少事件头文件和在非 DEBUG 构建引用 DEBUG 字段的问题。修正后原生回归通过；后一次 Kotlin 格式失败经统一格式化修正，没有削弱检查。

首轮接通进程 84588 进入标题界面并正常退出，SDK enable/disable 成功，但部分早期 SERVICE stdout 未进入控制台。随后补充设备能力文件、主帧批次关联和关闭 idle 首次发现 lost 时的 SDK 等待入口。最终版本重新构建与运行，不将首轮当作最终版本的全部证据。

### 首组固定实现运行

| 项目 | 诊断启动 | 基准适配器启动 |
| --- | --- | --- |
| 进程 | 80992 | 92548 |
| 标题界面 | 06:39:03 | 06:40:53 |
| 正常停止请求 | 06:39:11 | 06:41:01 |
| SDK enable/disable | 均为 `0x1`，SDK 的 Success | 均为 `0x1`，SDK 的 Success |
| 实际设备 | `aftermathFlags=3 diagnosticsConfig=1` | `aftermathFlags=3 diagnosticsConfig=1` |
| 驱动能力 | `deviceFault=1 checkpoints=1 debugNames=1` | `deviceFault=1 checkpoints=1 debugNames=1` |
| 附加检查 | 原生采集器挂接 | 原生采集器挂接，适配器 `active=true` |

两进程均无新崩溃报告，保留正常关闭 CPU 事件和实际 SPIR-V 的非零 SDK hash。SDK 回调统计为 0 个 dump、0 个 debug 文件、0 次预算拒绝。正常运行无转储符合延迟回调配置，不能视作真实故障采集已验证。

通过进程模块读取实际 core、agent、SDK 的路径和 SHA-256；core 对应构建、安装和 JAR 内嵌副本，SDK 来自工具的私有提取目录。PDB 与实际 SPIR-V 随最终源码清单保留，优化 SPIR-V 不保证完整源码行映射。

### 自动检查

`spotlessApply check verifyRelease verifyCompliance --strict` 通过，严格合规失败 0、警告 0。最终产品输入对应的执行结果：原生非 GPU CTest 64/64；GAME 250/250；probe 26/26；bootstrap 11 项通过、2 项按条件跳过；诊断采集器 CTest 2/2；外置采集等待/并发写盘 CTest 2/2；SDK 输入 Python 2/2；世界副本工具 5/5；盘点 Python 8/8。已有 Java 基准、盘点及模板单元测试复用未变化输入，不冒称重跑。

回归经过实际事件、文件输出、状态等待与 SDK 输入路径，覆盖并发环覆盖、标签寿命、64 位身份、忙槽不阻塞、写盘去重与预算、终止/错误/超时、哈希/缓存冲突和路径安全。没有制造 Vulkan 故障。

本轮 shader 源码、查询矩阵与锁修复语义保持不变。发行检查排除私有 SDK 和 agent，JNI 头文件与开发 Maven 边界检查通过；公开二进制许可门禁不变。

### 工具链身份收尾

文档收尾的一条命令漏带 `VULKAN_SDK`，构建退回系统的 1.4.341；该命令日志为 `D:/Workspaces/Artifacts/Radiance/gpu-diagnostics-20261015/evidence-archive.zip!documentation-closeout.log`，没有用其产物启动游戏。发现后保全首组实际验证的 DLL、JAR 和 PDB 于 `validated-runtime-artifacts/`，恢复本轮使用的 1.4.363 并重新执行完整检查与发行验证，结果通过。重新构建的二进制不沿用首组哈希，最终启动结果和加载身份分别记录在 `D:/Workspaces/Artifacts/Radiance/gpu-diagnostics-20261015/evidence-archive.zip!final-smoke.log`、`D:/Workspaces/Artifacts/Radiance/gpu-diagnostics-20261015/evidence-archive.zip!final-conformance.log`、`final-*-loaded-modules.json` 与 `D:/Workspaces/Artifacts/Radiance/gpu-diagnostics-20261015/evidence-archive.zip!artifact-identity.json`。产品源码没有因这次环境修正改变。

## 结论

项目内默认诊断已接通，配置和证据边界见[操作说明](../development/debugging-gpu_diagnostics.md)。SDK 初始化、实际设备配置、shader 身份和正常关闭有本轮证据；真实故障回调、故障时 SDK 等待和完整 G3 尚未自然验证，不要求为了补证主动再崩溃。

证据位于 `D:/Workspaces/Artifacts/Radiance/gpu-diagnostics-20261015/`：失败日志、`D:/Workspaces/Artifacts/Radiance/gpu-diagnostics-20261015/evidence-archive.zip!toolchain-restored-gates.log`、最终启动日志、加载模块 JSON、`D:/Workspaces/Artifacts/Radiance/gpu-diagnostics-20261015/evidence-archive.zip!artifact-identity.json`、`D:/Workspaces/Artifacts/Radiance/gpu-diagnostics-20261015/evidence-archive.zip!test-counts.json` 与 `source-final/`。`preliminary-smoke/` 单独保留前一版，`previous-validation/` 保留旧实例，首组运行和最终运行分别保留，不混作一个二进制的结果。没有进行性能归因，首次预热耗时不能单独归因于 SDK。
