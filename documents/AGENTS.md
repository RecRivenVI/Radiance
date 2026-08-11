# 项目规范

## 项目信息

| 项 | 值 |
| --- | --- |
| 产品显示名 | Radiance |
| 模组 ID | `radiance` |
| Java 包 | `com.radiance` |
| 入口类 | `Radiance` |
| 业务 | Vulkan 渲染与路径追踪 |
| 文档主语言 | 简体中文 |
| 首页语言 | 简体中文 |
| 参考 Target | `1.21.1-neoforge` |

## Target

| Target | 加载器版本 | Java |
| --- | --- | --- |
| `1.21.1-neoforge` | 21.1.259 | 21 |

## 补充规则

- **X-01 [判断]** `bootstrap`：SERVICE 窗口、原生加载及 GAME 定位；`bootstrapTest`：边界测试。
- **X-02 [判断]** `probeTest` 测探针；空源码集 `packagedLaunch` 验发行 JAR，禁用开发 MOD_CLASSES。
- **X-03 [判断]** 自动客户端静音；性能实验另授权，`radiance_probe` 仅限项目内验证，不投放 Prism。见[构建规程](development/procedure-build.md)。
- **X-04 [判断]** 不操作外部游戏实例。`run/`、`dev/`、`logs/` 冻结不写，仅留证据、清单及校验归档。
- **X-05 [判断]** `verifyRelease` 仅核对本地包，不授权发布；原生分发许可未闭合。
- **X-06 [判断]** 构建、自动测试、运行、目视与 GPU 根因分开报告。
- **X-07 [判断]** 保留上游历史，仅维护 `Initial port`，改动折入；作者、提交者、邮箱、两时间戳及时区、消息及上游父提交不变，commitdate=authordate，重签并验证 SSH。Git 操作与公开二进制发布须明确授权。
- **X-08 [判断]** 开发/验证禁用 Computer-Use；用命令行、构建、进程信息和日志；游戏手工操作由使用者完成。
- **X-09 [判断]** 只在 Radiance 开发，原生位于 `components/native_renderer`；旧 MCVR 只读，Git 另需授权。
- **X-10 [判断]** 恢复备份仅留未闭合操作，远端同步确认后回收；X-12 保护唯一证据。不动 `refs/codex/*`、reflog，不强制 GC 或清空回收站。
- **X-11 [判断]** 项目客户端默认轻量取证及本地 Aftermath，重型按需；缺 SDK 明示轻量。不影响发行包与外部实例，见[GPU 取证](development/debugging-gpu_diagnostics.md)。
- **X-12 [判断]** 构建/启动前查占用并自主清理超软量；清后超限或须删不可重建的唯一结论证据才询问。删除进回收站；保留源码、未提交改动、Extra、在用研究/Prism 实例、输入及 Native 增量缓存。细则与退役见[存储清理](research/audit-storage_cleanup.md)。
  - 仓库除 `.git/`、`components/native_renderer/third_party/`：软 20 GiB、硬 25 GiB；日常目标 12～14 GiB。
  - X-04 目录合计不超 2 GiB；`validations/*/{instance,result}/` 合计不超 8 GiB。
  - `D:/Workspaces/Artifacts/Radiance/` 与 `D:/Workspaces/Scratches/Radiance*` 合计软 6 GiB、硬 8 GiB；临时仅写这两处。
  - 完整候选仅留当前、Prism 在用、最近验收、性能基线及追查故障版本，每身份一套；符号仅留当前及追查崩溃版本。其余留哈希、源码快照和退役清单，旧报告不改。
  - 验收通过或任务结束，验证仅留结论、关键截图与性能基线的一套原始记录；可再生实例、模组副本和缓存回收，地图与配置输入保留。
- **X-13 [判断]** Extra 为同级独立仓库 `../Radiance-Extra/`，不登记组件/子模块、不内嵌或反向依赖；两仓库合计执行 X-12，Git 另需授权。
