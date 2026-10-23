# 当前项目状态

截至 2026-10-23，按 [adr-13](../design/adr-13-takeover_instancing_stages.md)将第一节统一为五阶段：阶段 1 初步调查与打基础（原 0～5）、阶段 2 接管原版渲染层（原 6）均完成；阶段 3 接管层实例化（原 7）已由使用者确认目视验收通过。使用者直接授权实施 GUI 背景变暗分离、统一 FG 启停和前台采证准备；保留已求值反色，实际全屏菜单模糊关闭 FG，取消 105 号全屏模糊继续补帧的目标。当前构建、自动回归、原生及前台会话通过，SDK 额外生成与模糊启停已有实测证据；使用者已决定 GUI 阶段工程收口，尚未进行人工视觉验收，后续补做。本次仅按新授权保存并推送源码检查点，阶段 5、第三方及其他功能不自动恢复，不操作外部实例。

## 阶段 2 的验收基线

本次收尾候选的唯一身份为 `components/render_diagnostics/build/takeover-stage6-closeout/manifest.json`，同目录 radiance.jar；完整源码为 `validations/conformance-vanilla/result/source-stage6-closeout-final.{json,zip}`，包含必要新文件及 14 个 gitlink。三处回归、七类原版来源对照、室内同步复核与普通配置 8,197 帧通过，实际加载 core 与内嵌／构建／安装一致。完整门槛通过，严格合规 0 失败／0 警告；收尾目视已由使用者确认通过，反馈保存在楼内调查案例的 `user-feedback.json`。

旧候选 `components/render_diagnostics/build/takeover-stage6/manifest.json` 及 `source-stage6-final.{json,zip}` 保留为人工失败历史。它此前完整路线 882,927 个请求无未消费、重复或未知，仍不能据此抵消后来发现的缺陷，也不把旧构建结果转记到新候选。

[短清单与启动方式](../../validations/visual-takeover_stages/validation.md)对应 `result/prepared.json` 的实际候选；仅 NeoForge＋Radiance、2560×1440、音量 50%、音乐关闭、RecRivenVI、视距 8。楼内定向诊断后已恢复人工设置，不启动。

## 已完成部分的证据入口

阶段 1 的原版请求与输出、原阶段 0～3 的部件对照和人工结果保存在[原版收尾](../research/investigation-vanilla_drawing_closeout.md)及[实体接管实验](../research/experiment-entity_takeover.md)。历史清单与源码快照保留；被当前收尾包取代的旧发行包按共同规则 v1 收敛，不再充当本轮可运行基线。

## 阶段 3 的验收与当前检查点

本轮唯一冻结身份为 `components/render_diagnostics/build/takeover-instancing/manifest.json`，同目录 radiance.jar；完整源码为 `validations/conformance-vanilla/result/source-instancing-final.{json,zip}`。来源归因、实现、修正过的测试入口、失败历史与配对细节集中在[本轮实验](../research/experiment-takeover_instancing.md)，不在这里重复全部数据。

1. 已实现字节预算、跨外观共享几何／BLAS、完整 TLAS 输入与九张元数据表内容判变；原版来源、未知修改者退回及生命周期对照通过。楼内 185 帧的 Java／native 输入均为 59；原来的 603 项逐来源核销。三个场景的正式 ABBA 共 12 个进程均正常保存关闭，无设备丢失。
2. 使用者于 2026-10-22 明确确认阶段 3 目视验收通过，对应同一个纯原版[目视入口](../../validations/visual-takeover_stages/validation.md)及上述冻结候选。此反馈不扩张为阶段 5 GPU 开放项或长期稳定性通过。
3. 96 号[修复与新调查](../research/investigation-gui_frame_generation.md)到达唯一检查点：反色准星移出 HUD-less、暂停／实际全屏模糊按帧停 FG、移除输入不足的自动菜单检测；修复插件未加载却不触发表面重建的缺口。两次限定 GUI、F3+T 与离屏回归正常；最终 2,042 次 SDK 查询均未产生额外生成正计数，明确日志停在窗口焦点门槛，GLFW 与 SDK 的焦点差异尚未解释，不记作生成帧动态视觉通过。实际测试包为 `components/render_diagnostics/build/gui-fg-fix/manifest.json`，源码为 `validations/conformance-takeover/result/source-gui96-final.{json,zip}`。旧 [91 号](../research/investigation-vulkan_gui_takeover.md)与 [81 号](../research/experiment-gui_separation.md)保留历史；96 号当轮未追加客户端，随后直接授权的前台结果见本轮实现记录。
4. 105 号[深入调查](../research/investigation-gui_fg_classification.md)保留历史；当前[分步计划](plan-gui_frame_generation.md)按 2026-10-23 直接决定收敛。背景与真实 UI 角色通过批次边界及 native 录制状态区分，压暗保持已有 HUD 覆盖率并在 HUD-less 重放；暂停不单独否决 FG，全屏菜单模糊与替换背景由同一规则关闭。已加入只读窗口／前台关联，SDK 实际 HWND 仍需对照其日志，不能把应用 HWND 当成 SDK 返回值。局部模糊与 loading 淡出提前恢复未实施；本次前台会话已完成，结果见下一项。
5. 本轮[实现与验证](../research/experiment-gui_fg_policy.md)已完成非 GPU／GPU、Java、发行包与严格合规检查；关闭 FG 的原生路线验证暂停／恢复、F3+T、保存和关闭，无设备丢失。唯一候选为 `components/render_diagnostics/build/gui-policy/manifest.json`，源码为 `validations/conformance-takeover/result/source-gui-policy-final.{json,zip}`。本次前台会话显式使用同一冻结包，取得 SDK 额外生成、全屏模糊关闭、无模糊暂停与返回恢复证据；工程收口，人工视觉验收尚未进行，后续补做。原 FG-off 与 96 号无正计数结果保持独立。

## 已接受语义

唯一入口为[语义决策索引](../reference/registry-semantic_decisions.md)和 adr-9～11。世界不后置光栅，全几何参与 PT；全亮实际影响世界照明；默认世界线不透明且使用物理厚度；来源被未登记变换修改时按实际加载记录退回。Ponder PT 封存，模组 GUI 不纳入本轮范围。云、介质和折射语义重做另行批准，不顺带实现。

## 开放项

- GPU 设备丢失的根因与 291 ms 异常窗口转入阶段 5，仍未知；既有真实故障、保存／关闭证据和轻量／Aftermath 设施保留。自然发生故障就保全并停止该用例，不自动重试或要求再崩溃取证。
- 严格 FG 后 GUI 仍未实现：Recomposition 仍处理 UI，不是完全绕过。本轮背景变暗与启停切片已通过自动回归，实际全屏菜单模糊关闭；局部模糊和加载淡出提前恢复待后续。本次前台许可和 SDK 正计数已验证，历史焦点差异机制仍未定因；本阶段已按使用者决定工程收口，人工视觉验收尚未进行，后续补做，不能以机器证据代填。
- 当前原版保留路径及其他捕获残留逐项登记：阶段 3 中迁移的项目与剩余原因分开，不用“直接”改名替代生产链迁移。
- 已知 Veil、Sable、Create 行为变体属于第二节后续范围；精确验证过的 Flywheel accessor 是单一接口登记，不是整体模组放行。
- 原版云、统一折射／发光账本、世界最低高度以下黑天空、浮空水晶拒收、优先级重复成像等既有待办以[路线图](roadmap-radiance.md)为准，未经本轮授权不扩张。
- 公开原生二进制许可仍未闭合。`verifyRelease` 只验证本地包结构，不构成公开发布许可。

## 性能与存储

下面是 FRAME_ONLY 帧间隔均值，两次基线与两次候选分列，不拼接帧数，也不与旧 SHARC 修正前数据拼组。

| 场景 | 阶段 2 基线 ms | 阶段 3 候选 ms |
| --- | --- | --- |
| 楼内 r8／1080p | 30.94／29.48 | 20.44／20.33 |
| 城市 r32／1080p | 51.89／52.64 | 36.29／36.67 |
| 城市 r32／1440p | 92.06／49.75 | 36.91／39.60 |

1080p 两场景的两对差值均改善；1440p A1 主队列长间隔未定因，不能给稳定收益幅度。当前仍未达到真实 60 FPS。生成、上传、更多实例带来的成本、GPU 分段、资源峰值与逐窗 MCP 干扰详见实验；整卡占用不冒充本进程，时间戳跨度不冒充硬件忙碌时间。

当前保留阶段 2 验收基线、阶段 3 已验收构建及 91／96 号仍被调查引用的 GUI 候选；被取代二进制按存储规则回收，旧清单、失败证据和源码快照保留。正式窗口的原始 CSV／日志逐条读回验证后归入各案例 raw-evidence.zip，摘要、索引与输入保留，复制客户端正常关闭后回收。人工实例不启动；继续遵守 X-12，不操作 Prism、旧 MCVR 或 Git 历史。
