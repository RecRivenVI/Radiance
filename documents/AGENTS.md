# 项目规范

本文件补充根目录[仓库规范](../AGENTS.md)，不能放宽模板规则。

## 项目信息

| 项 | 值 |
| --- | --- |
| 产品显示名 | Radiance |
| 模组 ID | `radiance` |
| Java 包 | `com.radiance` |
| 入口类 | `Radiance` |
| 业务 | Minecraft 的 Vulkan 渲染与路径追踪 |
| 文档主语言 | 简体中文 |
| 首页语言 | 简体中文 |
| 参考 Target | `1.21.1-neoforge` |

## Target

| Target | 加载器版本 | Java |
| --- | --- | --- |
| `1.21.1-neoforge` | 21.1.259 | 21 |

## 补充规则

- **X-01 [判断]** `bootstrap` 是 SERVICE 早期窗口、原生运行库加载与 GAME 定位源码集；`bootstrapTest` 验证其边界。
- **X-02 [判断]** `probeTest` 保留原诊断模组的自动测试；`packagedLaunch` 是空源码集，用于真实发行 JAR 加载，防止开发 MOD_CLASSES 取代安装流程。
- **X-03 [判断]** 自动客户端静音；性能实验须另行授权，仅用项目内验证实例与 `radiance_probe`，不向 Prism 投放诊断模组。实例设置见[构建规程](development/procedure-build.md)。
- **X-04 [判断]** 不操作外部游戏实例。`run/`、`dev/`、`logs/` 冻结，不写新运行产物，仅留证据、清单及校验归档。
- **X-05 [判断]** 公开原生二进制许可仍未闭合，`verifyRelease` 只核对本地验收包结构，不是公开发布许可。
- **X-06 [判断]** 不把构建、自动测试、客户端运行、视觉反馈或 GPU 故障根因混为一种完成状态。
- **X-07 [判断]** 保留上游历史，其上只维护一个 `Initial port`，后续修改折入该提交。作者、提交者、邮箱、两个时间戳及其时区、消息与上游父提交保持不变，commitdate 等于 authordate，重新生成并验证 SSH 签名；Git 操作与公开二进制发布只在使用者明确授权的范围内执行。
- **X-08 [判断]** 开发与验证不使用 Computer-Use；使用命令行、构建任务、进程信息和日志，游戏内手工操作由使用者完成。
- **X-09 [判断]** 后续开发只在 Radiance 仓库进行，原生组件为 `components/native_renderer`；旧 MCVR 仓库保留作历史参考，不再写入源码、测试、构建配置或文档。旧仓库的 Git 历史维护仍须使用者明确授权。
- **X-10 [判断]** 临时恢复备份只保留未闭合操作所需的最少数量，验证远端同步成功后通过 Windows 回收站收敛；不删除验收证据的唯一副本，不改动 `refs/codex/*`，不清空 reflog、强制 GC 或清空回收站。
- **X-11 [判断]** 项目内客户端默认使用轻量取证与本地 Aftermath 基础采集，重型验证按需启用；入口、输入与证据边界见[GPU 故障取证](development/debugging-gpu_diagnostics.md)。此默认值不改变外部实例或发行包，缺少私有 SDK 时显式选择轻量模式。
- **X-12 [判断]** 构建/启动前查占用，超软先清且不超硬；保留输入和唯一证据，不确定待定。见[存储清理](research/audit-storage_cleanup.md)。
  - 仓库除 `.git/`、`components/native_renderer/third_party/`：软 16 GiB、硬 20 GiB。
  - X-04 目录合计不超 2 GiB；`validations/*/{instance,result}/` 合计不超 4 GiB，每项只留最新。
  - `D:/Workspaces/Artifacts/Radiance/` 与 `D:/Workspaces/Scratches/Radiance*` 合计软 6 GiB、硬 8 GiB；临时仅写这两处。
  - 候选仅留当前、最近验收、文档引用，最多 5 套；超额引用待决定，不擅删。
  - 客户端记结论时清复制环境/二进制，仅留文本证据。
