# 表面纯几何补齐与最终候选验证

2026-10-17，补齐族 10 最终回查的两个纯几何函数，并对新的配套产物重新验证。此文接续合并研究，不改写先前候选的源码和运行范围。

## 问题

前一轮收尾仍保留五份 applyNormalMapToBasis 与五份 basePlaneWorldPosAtUv，工程统一尚有遗漏；旧 DLL 26A1FC3A 的成功不能代填补齐后的候选。

## 方法

先固定十份正文及实际 normalizeF/PI 依赖，写 GPU 影子回归，确认缺少新共同实现时编译失败；再把两个正文原样移入 surface_geometry.glsl。五个实际消费者只包含共同文件，参数、公式、运算顺序和调用位置不变。GPU 在 64 组输入中比较生产助手与旧参考，包含镜像、非单位基底、视角及 NaN 材质法线回退；旧参考只在测试中保留。

## 发现

- 两个函数现在各只有一份生产定义。该项是工程覆盖遗漏，不是新增已证实的视觉缺陷；十二组明确缺陷及其他差异仍按[合并研究](experiment-render_unification_bcd.md)分类。
- 补齐后的 spotlessApply、check、verifyRelease、严格合规成功；GAME 292、JNI 4、probe 28、bootstrap 11 通过／2 跳过，非 GPU 67/67。GPU 16/16 通过，包括上述生产助手与旧参考；pack 232 编译和 232/232 SPIR-V 检查通过。实际 Java 12 组 CPU/GPU 输入也在本次 build/v 重新执行成功。
- 新候选 DLL 为 F45C284CC8D341E535D1E5257B4422B29C6E7C1F69AB704FCAD0E4A886D631EB；GAME 内容哈希仍为 5BDCE9C6DBD21E0107D7CC73E793E459B4398499AA3B48C7CC91CB1379261760。原生重新构建产生新二进制身份，所以两种预设均重新运行，不混用前一 DLL 的结果。
- RR 进程 68932：08:50:51 入世界，08:51:12 重载完成，08:51:37 保存返回标题，08:51:41 重进，08:51:49 正常停止，3250 次线回调。
- NRD/FSR 进程 2636：08:55:05 入世界，实际 FSR provider 3.1.4 的 dispatch 接受；08:55:25 重载完成，08:55:50 保存返回标题，08:55:54 重进，08:56:02 正常停止，3496 次线回调。
- 两次均静音、1920×1080，临时 resize 到 1280×720 并恢复；三边界表面检查、字体违规与线条非法输入均为零，Sable 姿态回退为零。全部维度保存、normal-device-close、进程退出码 0 分别有记录，无设备丢失、故障注入或自动重试。
- 有界原生输入审计再次取得 239 条，nonfinite、sameWinding、ambiguous、degenerate 均为零；不是 GPU 位置读回，也不代表所有真实模组场景。eyes 来源面位 4 与 body-no-cull 的 0 不同，保留独立层资格，真实眼睛仍待目视。
- 构建／内嵌／提取／两进程实际加载 DLL 一致。运行准备时重打包的两个 PT ZIP 只改变元数据，所有成员内容相同；其余 141 个原生运行文件精确一致。最终文档检查可能再次重打包，交付容器逐项核对后在清单固定；开发类启动不冒称外层 JAR 安装启动。

## 结论

族 10 的最后两个明确可共享表面函数已补齐，不改水、云、height、天空或两套主追踪算法。累计检查点 12-surface_geometry 保存这次补齐，13-total 与完整源码 ZIP 固定最终状态；早先的 10/11 及 PID 73176/65996 保持原证据范围。

最终配套身份在 D:/Workspaces/Scratches/RadianceSemanticsAudit/bcd-complete-artifact.json；源码与恢复入口在 D:/Workspaces/Scratches/RadianceUnification/complete-source-manifest.json。新证据为 validations/regression-line_capture/result/bcd-complete-rr2-20261017/、bcd-complete-fsr2-20261017/ 以及外部 bcd-complete-*、d10-surface-*。没有提交、推送、Prism、Computer-Use 或性能测试。合并目视、adr-3/5/6、GPU 根因和公开二进制许可仍分别待验／待决定，不把本次有限成功扩写为全部通过。
