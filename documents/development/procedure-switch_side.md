# 切换运行端

## 适用范围

改变产品模组的运行端，即 `gradle.properties` 中的 `mod_side`，取值含义见 P-02。

## 步骤

1. 在 `gradle.properties` 中把 `mod_side` 改为 `both`、`client` 或 `server`。Fabric 元数据中的 `fabric_environment` 与 Forge 元数据中的 `display_test` 随之变化；NeoForge 不读取元数据中的运行端，只由第 2、4 步决定。
2. 按新的运行端调整各 Target 的入口：

   | `mod_side` | NeoForge | 1.20.1 Forge | Fabric |
   | --- | --- | --- | --- |
   | `both` | `@Mod(ModIdentity.ID)` 入口 | `@Mod(ModIdentity.ID)` 入口 | `main` 入口 |
   | `client` | `@Mod(value = ModIdentity.ID, dist = Dist.CLIENT)` 入口 | 入口构造器先判断 `FMLEnvironment.dist.isClient()`，客户端逻辑放在只在客户端加载的类中 | `client` 入口，类放在 `src/client/java/`，元数据已限定为客户端 |
   | `server` | `@Mod(ModIdentity.ID)` 入口 | `@Mod(ModIdentity.ID)` 入口 | `main` 入口 |

3. 在 Fabric Target 中，只在客户端运行的代码与资源放在 `src/client/`，`src/main/` 中的代码不引用它们；`fabric.mod.json` 的 `client` 入口指向 `src/client/java/` 中的类。NeoForge 与 Forge 没有客户端源码集，用 `Dist` 区分。
4. `mod_side` 为 `client` 或 `server` 时，确认产品不注册需要另一端同步的内容，如方块、物品或必需的自定义网络负载；NeoForge 在连接时按网络负载判断两端是否兼容，必需的负载会让未安装本模组的一端无法连接。
5. 更新 `documents/usage/installation-basics.md` 的"在服务器上使用"一节，并在更新日志中记录。
6. 执行 `.\gradlew.bat spotlessApply`，再执行 `.\gradlew.bat check`。
7. 启动 `runServer`、`runClient` 与 `runClientMultiplayer`，用 `runClientMultiplayer` 加入 `runServer`，按 [format-metadata](../reference/format-metadata.md) 的"运行端"一节的第二张表判断结果。`runServer` 需要使用者已在 `local.toml` 中接受 EULA（I-02）；没有接受时请使用者决定，不代为写入（G-04），使用者不接受时跳过 `runServer`。
8. `mod_side` 为 `client` 或 `server` 时，按同一节的说明确认与未安装本模组的一端互通；需要使用者操作普通启动器时，请使用者确认并报告结果。

## 验收

- `.\gradlew.bat check` 成功。
- 第 7 步的三个实例符合 [format-metadata](../reference/format-metadata.md) 的"运行端"一节的预期，日志中没有缺少模组的报错；使用者未接受 EULA 时，报告中写明独立服务端未验证。
- `mod_side` 为 `client` 或 `server` 时，第 8 步的互通结果已报告；没有测试时写明未验证。

## 禁止

- 在元数据文件中写死 `environment` 或 `displayTest` 的值，或在 NeoForge 元数据中写 `displayTest`（P-04）。
- 为同一 Target 保留多套入口（G-02）。
