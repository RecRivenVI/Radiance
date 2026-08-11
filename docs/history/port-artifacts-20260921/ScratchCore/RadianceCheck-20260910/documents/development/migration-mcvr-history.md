# MCVR 历史接入 Radiance

## 范围与固定来源

用户于本轮明确决定单仓维护：当前项目称为 Radiance，MCVR 实际工程继续作为 `components/vulkan-renderer` 普通 CMake 组件。允许必要 SSH 签名本地提交、历史引用与合并，明确不推送，不删除／归档来源 fork 或本机 MCVR，不重写任何原提交。

来源远端 `https://github.com/RecRivenVI/MCVR.git` 在 2026-09-10 13:57 UTC 前后两次引用公告相同：dev、feat、main、stonecutter 四个分支，无标签，默认分支 stonecutter。远端可达 10 个提交；本机额外保存的引用、reflog 和不可达提交使完整选定集合扩展为 35 个提交。不是只取默认分支，也不是把从未取得的已删除历史宣称已恢复。

本机 MCVR 原仓库保持只读。其恢复材料含过去不同来源／Felix 路线及 amend 前对象，来源通过原 reflog 保存；保全这些对象不启用其产品实现。第三方 SDK 历史不纳入，只保留旧树的原始 gitlink 和当前 VENDOR 固定来源。

## 备份与恢复

完整 Git 备份位于 `D:/Workspaces/Backups/Radiance_History_20260910-215704/`。MCVR-remote、MCVR-local、Radiance-before 三份 bundle 均实际克隆恢复，refs 一致、fsck 成功、无 alternates；本地恢复对象另已进入备份的持久锚点。源仓库没有创建这些锚点。

工作区文件与索引另行保护，见 [工程记录](migration-template-configuration.md) 的可恢复源文件备份；bundle 不被当作未提交文件备份。原始私密配置与运行数据不纳入新提交。

本机 fsck 发现的 576 个不可达对象全部由选定恢复提交覆盖，没有另外遗漏的匿名 blob/tree。选定 MCVR 历史未发现 LFS 指针；高置信私钥／令牌形状扫描未发现匹配，但不是对任何敏感信息的无条件保证。原始对象清单、父关系与恢复验证在备份中保留。

## 合并设计与当前进度

引用采用 `codex/history/mcvr/remote/*`、`local/*`、`recovered/*` 的隔离命名空间。完整“来源引用 → 保存引用 → 原始对象”映射见 [HISTORY.json](../../components/vulkan-renderer/HISTORY.json)。原始分支别名／符号引用关系仍在映射中记录；原始提交 ID、父关系、作者、时间、消息与签名不改变。

隔离库已创建签名的合集节点 `08b0fc33408c1dece228772976bca4d6b46e40d8`，13 个父节点覆盖全部 35 个原提交，文件树选择历史 MCVR 基线。它是来源保存节点，不是当前组件的另一个开发版本。

实际接入已完成：`ac78fbf33cb7cc3e92fc2561b167bafeb4234a79` 第一父为来源说明检查点 `9df674adb150c70e452faba5d4e93cf5b56903e7`，第二父为合集节点。合并前后整树均为 `c953193ad898e10e64ebccd2a491c3efc98a3ca2`，10,036 个工作文件逐一核对未变。工程、称谓、来源说明另有提交，不属于纯历史合并的文件变动。

隔离演练、实际合并与本地独立克隆均通过。新克隆不共享对象、不读取原 MCVR 工作区，35 个原提交全部可达，44 个映射可解析，1,490 个原始 Git 对象与恢复备份逐字节相同；没有 LFS 指针、当前 gitlink 或嵌套 Git。原 Radiance 其他分支、远端跟踪引用和六个标签未改动。新克隆在全新生成目录、禁用任务构建缓存的条件下完整构建通过（9m33s）：65 项配置测试、26 项 JVM 测试、17 项 CTest，以及 Java/JNI/native/Early Window/打包检查。正常使用声明的依赖缓存和外部工具。实际版本仍为 `0.1.5-alpha+1.21.1`；活动构建不从 Git 标签或 describe 推导产品版本。

29 个 recovered 引用是本轮在备份与目标中创建的恢复锚点，不是伪称来源本来存在的正式分支。HISTORY.json 将 sourceRef 与 recoveryBackupRef 分开。所有新提交使用同一现有 SSH 身份签名、标题 Update、作者与提交者时间相同；原始提交没有重签。

## 平台内容与以后推送

已读取来源 GitHub 仓库信息、全部可见 Issues、PR 和 Releases，并补查仓库提交评论；这些列表为空，没有独有附件被重新发布。仓库设置、关注者、Actions 日志等其他平台内容没有迁移，Git 历史保存不等于 GitHub 全站迁移。

本轮不推送。后续必须显式推送 Radiance 主开发分支以及 HISTORY 映射列出的保存分支／合集引用；不能依赖默认 push 或使用 mirror push。原 Radiance 标签原样保留，来源当前无标签；未来如补到来源标签，须明确 namespaced refs 和原 annotated tag 对象。

获得推送授权后，从远程 Radiance 完整独立克隆，逐一核对引用对象、35 个原提交的可达性、当前树、LFS（如有）与构建身份，才评估移除来源 fork 的前置条件。目前远程保存和删除前置均未完成，来源 fork 与本机 MCVR 继续保留。

## 后续发布的明确引用集合

以下步骤仅供以后获得推送授权时使用，本轮没有执行。开发分支一条，加 HISTORY 中 44 条映射和一条合集引用，共 46 条 refspec；不是 mirror push，也不删除远端引用：

```powershell
$history = Get-Content components/vulkan-renderer/HISTORY.json -Raw | ConvertFrom-Json
$refs = @('refs/heads/stonecutter') + @($history.references.savedRef) + @($history.collectionRef)
$refspecs = $refs | ForEach-Object { "${_}:${_}" }
$refspecs                         # 先核对这 46 条明确的源与目标
# 获得授权并核对 origin 身份后，才执行 git push origin @refspecs
```

远程验证须从 `https://github.com/RecRivenVI/Radiance.git` 新建非浅、无 alternates 的独立克隆：核对 HEAD；将映射中的 `refs/heads/` 对照到 `refs/remotes/origin/`；逐条核对原对象 ID；对 originalCommits 中每个提交检查其为开发分支祖先；检查合集、原 Radiance 标签、整合树及普通组件，并重复必要构建。任一缺失都不能据此删除 MCVR fork。备份中的原始对象清单可用于额外逐字节复核，但不是正常开发或克隆的依赖。

固定 SDK 文件中有五个约 57–74 MiB 的二进制，当前单文件最大 77,795,704 字节；工作树约 1.39 GB。远端实际接收与存储限制本轮未测试，不能以本地克隆成功替代。

## 日常追溯方式

当前组件路径沿用 `components/vulkan-renderer/`；原始 MCVR 提交仍保持当年的 `src/`、`cmake/`、`tests/` 等路径。当前修改查 `git log -- components/vulkan-renderer/`；原 MCVR 路线查 `git log codex/history/mcvr/remote/stonecutter -- src/`（普通克隆使用 `origin/codex/history/mcvr/remote/stonecutter`）。其他来源分支与恢复锚点按 HISTORY 映射选择。路径没有经过历史过滤，不承诺一个 `--follow` 命令能替代这两侧的来源映射。
