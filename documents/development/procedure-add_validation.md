# 新增验证

## 适用范围

为项目新增一项自动化验证。`<name>` 为验证目录名，写作 `<type>-<topic>`（V-01）；`<component>` 为执行它的 `tool` 组件名；`<target>` 为验证涉及的 Target 名称。验证目录的格式见 [format-validation](../reference/format-validation.md)。

## 步骤

1. 确定这项验证证明什么、涉及哪些 Target、怎样判定通过；使用者没有说明时先询问，不自行假设通过标准。
2. 执行 `.\gradlew.bat printVocabulary`，从 `validations` 一行选择 `<type>`（V-02），确定 `<name>`。
3. 查看根目录 `settings.gradle.kts` 的 `components {}` 中用 `tool(...)` 登记的组件。能执行这项验证的组件已存在时，在该组件中增加功能；否则按 [procedure-add_component](procedure-add_component.md) 新建 `tool` 组件 `<component>`。
4. 在 `<component>` 中实现执行验证的 Gradle 任务：读取 `validations/<name>/validation.toml` 与 `task/`，需要游戏时启动第 6 步生成的验证运行，结果与证据写入 `validations/<name>/result/`，验证失败时任务失败。
5. 需要在游戏中运行的代码，放进每个 `<target>` 的 `versions/<target>/src/probe/`（V-06）：在 `src/probe/resources/` 中写出与 `src/main/resources/` 相同的元数据文件，模组 ID 为 `"${mod_id}_probe"`；探针得到结果后写入系统属性 `validation.result` 指向的目录并退出游戏，见 [format-validation](../reference/format-validation.md)。
6. 创建 `validations/<name>/validation.toml`，顶层写 `tool = "<component>"` 与 `targets = ["<target>"]`，不涉及 Target 时写 `targets = []`（V-08）；需要启动游戏时，为每个角色写一个 `[instances.<角色>]` 表，`<角色>` 为 `client`、`client-multiplayer` 或 `server`（F-03）；其余键按 `<component>` 的需要添加。
7. 执行 `.\gradlew.bat :version:<target>:tasks --all`，确认第 6 步的每个角色都有一个以 `runValidation` 开头的任务，名称规则见 [format-validation](../reference/format-validation.md)。
8. 创建 `validations/<name>/validation.md`，写"目标、运行、通过标准"三节；"运行"一节用代码块给出从仓库根目录执行第 4 步任务的命令（V-09）。
9. 把场景、夹具等输入数据放进 `validations/<name>/task/`，不放源码、脚本或可执行文件（V-04）。
10. 按 `validation.md` 的"运行"一节执行验证，按"通过标准"判断结果。
11. 执行 `.\gradlew.bat spotlessApply`，再执行 `.\gradlew.bat check`。

## 验收

- `.\gradlew.bat check` 成功，V-01 至 V-04、V-08、V-09 没有发现。
- 第 10 步的命令成功，结果满足"通过标准"，`validations/<name>/result/` 中有本次的证据。
- `git status --short validations/<name>/instance` 没有输出。

## 禁止

- 在 Target 的 `build.gradle.kts` 中为验证工具或验证运行写配置（V-05）。
- 在日常实例中加载 `probe` 源码集，或把它的产物放进产品构件（V-06）。
- 为了让验证通过而在 `validation.toml` 的 `[instances]` 中写个人偏好，例如只适合本机的内存或窗口大小。
- 读写 `instances/` 下的日常实例（V-07）。
- 手工编辑 `instance/` 与 `result/` 中的生成内容（G-05）。
