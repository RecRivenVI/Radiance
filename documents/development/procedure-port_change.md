# 移植改动

## 适用范围

把已在参考 Target 上完成并在游戏中确认的改动，移植到其他 Target（T-05）。`<reference>` 为项目规范中的参考 Target，`<target>` 为其余已登记的 Target。

## 前置条件

- 改动在 `<reference>` 上已通过 `.\gradlew.bat :version:<reference>:build`，并已在日常实例中确认。

## 步骤

1. 按与 `<reference>` 的接近程度依次处理其余 Target：先处理 Minecraft 版本相同的其他加载器，再按 Minecraft 版本由近到远处理。
2. 对每个 `<target>`：
   1. 用该 Target 加载器与版本的原生 API 实现相同行为，不照搬其他加载器的写法。
   2. 执行 `.\gradlew.bat :version:<target>:build`，修正编译错误。
   3. 用该 Target 的日常实例确认行为与参考 Target 一致。
3. 把各版本、各加载器之间的 API 差异整理为对照表，写入 `documents/development/porting-<topic>.md`；已有同主题文档时更新它。
4. 执行 `.\gradlew.bat spotlessApply`，再执行 `.\gradlew.bat check`。
5. 行为对玩家可见时，按 W-01 更新 `documents/usage/` 与更新日志。

## 验收

- `.\gradlew.bat check` 成功。
- 所有 Target 的 `build` 成功，行为已在各自的日常实例中确认。

## 禁止

- 在 Target 之间共享源码目录、使用预处理器或条件编译（T-04）。
- 因某个 Target 难以实现而静默跳过；无法实现时停止，并向使用者说明原因。
