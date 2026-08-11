# Levitite Ponder 偶发拉伸调查

2026-10-30，使用者报告浮空水晶的 Ponder 教程可能出现触发时机、形状和位置异常，像剧烈拉伸；随后再次检查未能复现，决定找到复现方式后再提供。本轮没有修改产品渲染实现，没有性能测量。

## 问题

需要区分浮空水晶的 Ponder 教程与 Simulated 结构图解。使用者确认是前者。不能因世界 PT 已接管水晶，就在 Ponder 中主动启用来源选择关闭的效果。

## 方法

核对航空学 1.3.2 的 `mixin/levitite/PonderUIMixin`、`LevititeShaderManager`、`AeroRenderTypes` 与教程脚本。使用本体 N、Extra O 的既有产品包，扩展仅验证使用的探针，打开实际注册的 `aeronautics:levitite` 教程，沿多个源码时间位置取样并返回开头。

第二次先在真实世界建立三个水晶的 Sable 结构并令来源管理器报告运动，再进入教程。探针记录特效开关及实际 UI 提交的程序名称，分别检查没有活动水晶和已有世界重影的情况。客户端保持 1920×1080、静音；不操作 Prism 客户端。

## 发现

来源在 `PonderUI.renderScene` 开头调用 `LevititeShaderManager.disableShader()`。特殊层的条件状态随后选择关闭分支，用普通 solid 程序并关闭颜色和深度写入；普通模型、教程动画及轮廓继续走各自路径。这是来源明确提供的行为，不是缺失的世界重影适配。

两次隔离运行的各采样点均为 `sourceEffectEnabled=false`。有活动水晶的第二次运行中，UI 提交记录没有 `aeronautics:levitite/levitite` 程序，世界水晶本身仍有主体和重影提交。已查看的教程截图未出现报告的剧烈拉伸。取样请求超过教程长度时，实际进度停在 832；报告保存实际时间，不将 900、1100 当成额外覆盖阶段。

证据为 `validations/conformance-takeover/result/world-materials-134/` 下的 `levitite-ponder-o-levitite-ponder-20261030-203222/` 和 `levitite-ponder-world-o-levitite-ponder-20261030-203935/`，包括截图、`world-materials.json`、实际算法选择及日志。两次正常退出，没有设备丢失；复制客户端已在证据归档读回后回收。

## 结论

保留来源在 Ponder 中关闭特效的决定。当前没有可确认的失败路径，不增加强制启用、额外关闭或猜测性的修复。仅补充可重用的项目内采样入口。

偶发拉伸保持待复现，不能宣布已修复，也不能用这些离散截图证明所有连续动画与进入过程均正确。等待使用者提供可复现的具体场景、播放阶段或画面后再继续。
