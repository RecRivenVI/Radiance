> A 验收已修复 MainTarget 启动错误并更新基础组 JAR。当前版本与启动证据见 [acceptance-a-current.json](Evidence/acceptance-a-current.json)；下方哈希为原工程交付快照，B 尚未同步。

# Radiance / MCVR 工程交付

当前固定版本的源码实施、工程验证与交付材料已收口。**A/B 完整运行、Mixin 实际装载和画面仍待用户验收。**

## 最终产物

- [可分发单 JAR](Acceptance/base/mods/Radiance.jar)：Radiance 0.1.5-alpha / Minecraft 1.21.1 / NeoForge 21.1.250。
- 大小：146,635,121 bytes。
- SHA256：`00B73619F741F7FFEA876B34800DCF2682C5628A9609B66169EAE69DBFC3D836`。
- B 完整组合已经准备在 [Acceptance/full/mods](Acceptance/full/mods)，使用同一个 Radiance.jar，加三个包含合法嵌套依赖的上游外层 JAR。
- [全部外层/嵌套 JAR 哈希](Evidence/final-artifact-hashes.json)、[源码冻结](Evidence/final-source-freeze.json)、[Git 状态](Evidence/final-git-state.json)。

## 验证与使用

- [A/B 准确启动命令和人工步骤](Reports/manual-acceptance.md)。
- [验证矩阵、实际兼容边界与限制](Reports/final-verification.md)。
- [18 工作包 / 52 兼容记录状态](Reports/workpackage-status.json)。
- 最终统一构建：[final-reviewed-release.log](Evidence/final-reviewed-release.log)。
- Java 120 项：118 PASS，2 个窗口测试跳过；native 24/24 PASS；内置 shader 237/237 PASS；161 个 JNI 导出齐全；单 JAR SERVICE/GAME 与运行资源检查 PASS。

本次未提交、推送、发布、调整暂存区或操作用户 Prism/存档/配置/资源包。两仓保留独立上游结构与未提交工作。代理没有启动 Minecraft、参考 GL 窗口或 UI 自动化；工程交付后暂停，等待用户人工验收结果。

历史调查报告与失败日志均保留。当前结论应结合本入口、最终源码哈希和对应验证日志，不把旧顶层进度、压缩摘要或某次中间 PASS 当作最终版本证明。

