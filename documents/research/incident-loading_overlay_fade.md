# NeoForge 加载页淡出异常

2026-10-17，使用者报告 NeoForge 大红色加载页原本的淡出变成“先淡入再淡入然后强切”。本轮登记现象并进行定向静态核对，没有修改产品、启动游戏、构建或替换验收包。

## 问题

此前主菜单首个按钮闪白的状态收尾修复已经获使用者通过；本次是新增的整个加载页过渡异常，不撤销旧反馈，也不将两者直接视为同一故障。当前投放身份见[普通半透明候选](../project/phase-1-before_vanilla_closeout.md#普通半透明候选)，本次没有读取使用者进程或确认发生该现象的实际加载身份，不凭反馈时间推断是哪次修改引入。

## 方法

对照本机 Gradle 缓存中 NeoForge 21.1.259 的 `NeoForgeLoadingOverlay.java`，读取 Minecraft 1.21.1 参考源码的 `GameRenderer`、`TitleScreen`、`PanoramaRenderer`、`CubeMap` 与 `ShaderInstance`。核对当前 `RadianceLoadingOverlay`、`RasterDrawBridge`、SERVICE 的 `onGameFrame`、native `loading_renderer.cpp` 和 loading shader；未运行新的 GPU 或客户端验证。

## 发现

### 淡出参数提前进入下层菜单

[RadianceLoadingOverlay](../../versions/1.21.1-neoforge/src/main/java/com/radiance/client/loading/RadianceLoadingOverlay.java) 在 `renderOverlay` 中先设置 `RenderSystem.setShaderColor(1, 1, 1, fade)`，再调用 `minecraft.screen.render`。固定版本的 NeoForge 原实现先绘制下层 screen，之后才为加载页合成设置该颜色。

这是明确的状态作用域差异。`TitleScreen` 的全景背景首先调用 `CubeMap.render`；该入口没有先重置 shader color。`ShaderInstance.setDefaultUniforms` 从 `RenderSystem.getShaderColor()` 读取 `ColorModulator`，当前 `RasterDrawBridge.drawWithShader` 调用这一方法并将当次 uniform 提交到 Vulkan。因此，下层全景的来源顶点 alpha 还会乘上本应仅用于加载页的 `fade`。后续 `PanoramaRenderer.setColor` 恢复白色以及帧末 `renderWithStateReset`，均不能改变已经提交的这些 draw。

令加载完成后的时间为 t 秒，当前与 NeoForge 的淡出参数均为 `f = 1 - clamp(t - 1, 0, 1)`。在标准标题页第一次于 t=1 开始绘制的条件下，其第一轮全景顶点 alpha 为 `p = clamp(t - 1, 0, 1)`，当前该轮 shader 实际得到 `p × f`：

| t | 全景来源 alpha p | 加载页 fade f | 受污染的第一轮全景 alpha |
| --- | --- | --- | --- |
| 1.00 | 0.00 | 1.00 | 0.00 |
| 1.25 | 0.25 | 0.75 | 0.1875 |
| 1.50 | 0.50 | 0.50 | 0.25 |
| 1.75 | 0.75 | 0.25 | 0.1875 |
| 2.00 | 1.00 | 0.00 | 0.00 |

该表是来源公式的条件推导，不是采集的像素或完整多次全景混合结果。覆盖层结束的那一帧仍先用 f=0 绘制下层菜单，随后才移除覆盖层并在收尾恢复白色；下一帧正常 screen 绘制不再受该因子影响。它能解释菜单背景先出现又被压低、结束时突然恢复的机制，但尚未证明用户描述中的全部红色变化均由这一处产生。

### 不能仅凭重复 alpha 判错

淡出计时与固定 NeoForge 实现一致：完成后先保持一秒，随后一秒从 1 单调降至 0。没有发现计时公式自行再次淡入的分支。

native 合成同时在顶点 alpha 与 opacity 中使用 fade；NeoForge 原实现也同时设置顶点 alpha 与 ColorModulator alpha。因此，不能只看见“两次乘 alpha”就将其删除或认定为新缺陷。完整红色背景、边缘填充与画布 alpha 的实际输出仍需对应合成验证。

### 现有测试的覆盖缺口

`RadianceLoadingOverlayStateTest` 覆盖普通、提前返回及异常后的状态收尾，没有检查嵌套 screen 绘制时读到的颜色。`LoadingSceneGpuParityTest` 的 native 调用使用 `gameFrame=false`、opacity=1，并读取加载画布；它不覆盖 GAME 交接后的菜单背景与淡出合成，不能用这项测试代替本次过渡验收。

## 结论

已定位淡出 alpha 污染下层菜单的具体调用顺序问题，尚未完成真实客户端定因或修复。后续最小修复应限制加载页颜色状态的作用范围，保持下层 screen 的独立淡入、NeoForge 原有计时、原生覆盖层顺序与已有收尾保护；补充嵌套 screen 状态和 GAME 合成过渡的验证，而不是改淡出时长或直接去掉一份 alpha。

本轮仅新增该研究记录并同步状态、路线图；产品与投放身份保持不变，没有性能实验或 Git 写操作。修复后仍需使用者确认完整加载页到主菜单的连续视觉过渡。

## 2026-10-17 后续授权修复

随后使用者批准先修复该问题，再挂载性能探针启动项目内实例。实际 `renderOverlay` 不再把加载页 fade 写入全局 shader color；背景分支通过 `renderBackground` 在嵌套 screen 绘制前建立中性白色。native 仍接收原有 opacity，原计时、画布／边缘混合及帧末收尾保持不变。

新增回归通过生产实际使用的背景分支接缝，覆盖 t=1～2.25 的菜单自身 alpha、t<1 的清屏分支和嵌套 screen 抛错后的原异常／收尾顺序。该测试验证 Java 状态边界，不声称 GPU 完整过渡像素或使用者目视已经通过。构建和启动身份由[性能计划](../project/plan-scene_performance.md)关联的本轮证据记录，不沿用旧候选的通过。

本轮配套门槛已通过，加载页 6 项、全部 GAME 311 项与非 GPU CTest 69/69 通过；其余计数、产物身份及首轮文档失败→修正→重测保存在上述计划。新产品只变更加载页 Java 状态边界，native 产品和 shader 未修改，未重新运行 GPU 套件；不能据此将完整红色过渡视觉标为通过。

随后同日，使用者在项目内实例启动后确认“淡出问题已修复”。此项定向目视通过，不能据此关闭另外报告的 Early Window 到菜单长未响应，或将未测试的任意加载路径一并通过。
