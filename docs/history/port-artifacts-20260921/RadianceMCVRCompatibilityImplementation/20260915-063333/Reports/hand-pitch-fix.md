# 普通手部 pitch 插值修复

## 目标依据

- 目标源码：`D:\\Workspaces\\References\\minecraft-references\\minecraft-1.21.1\\src\\net\\minecraft\\client\\renderer\\ItemInHandRenderer.java`
- `renderHandsWithItems` 第 328 行使用 `Mth.lerp(tickDelta, player.xRotO, player.getXRot())`，并将同一插值 pitch 依次传给主手/副手的 `ClientHooks.renderSpecificFirstPersonHand` 与 fallback `renderArmWithItem`。
- 修改仓库固定点：`1.21.1-neoforge@414d8e330a2fc6cb1e8630cc95f2302b2b97a0e8`。

## 改动

文件：`src/main/java/com/radiance/mixins/vulkan_render_integration/HeldItemRendererMixins.java`

```java
float g = Mth.lerp(tickDelta, player.xRotO, player.getXRot());
```

相对本轮新封存 `Baseline/Radiance/working-content.zip`，文件行数仍为 90，只有第 59 行发生变化：从瞬时 `player.getXRot()` 改为上一 tick 与当前 pitch 的 partial-tick 插值。

保持不变：

- 主手与副手的 `ClientHooks.renderSpecificFirstPersonHand` 逐手入口及参数顺序。
- hook 未接管时的 `renderArmWithItem` fallback。
- attack/swing、bob、equip height 计算及矩阵旋转。
- `applyForgeHandTransform` 仍由目标 `renderArmWithItem` 内部语义承接，本文件未绕开。
- 未引用或修改 Plunger 专属 renderer/坐标。

## 验证

- 新封存 baseline 逐行对比：PASS，恰好 1 行变化。
- 固定 1.21.1 参考源码与当前表达式对照：PASS。
- 两个 `renderSpecificFirstPersonHand` 与两个 fallback `renderArmWithItem` 调用仍存在：PASS。
- 旧的 `float g = player.getXRot()` 赋值残留：0。
- `git diff --check`：PASS。
- staged path：0；未 commit、push、restore 或 clean。

本改动没有引入 helper 或单元测试。该表达式是一行目标源码映射；为它增加一个只复述 `Mth.lerp` 的 helper/test 不能验证 mixin 真实调用接线。根任务统一执行 Java test/compile，至少应覆盖 `test`、目标版本 compile/build 与既有手部/材质 contract。

## 验收边界

- 当前已完成源码对照与单行范围验证。
- 尚未启动游戏或 GUI；快速抬头/低头时主手、副手及 Forge hook 接管场景的视觉平滑度仍待用户实际画面验收。

