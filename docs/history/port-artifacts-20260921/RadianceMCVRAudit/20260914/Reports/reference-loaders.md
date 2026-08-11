# Minecraft 与 Loader reference 生成结果

状态：`PASS_WITH_DECLARED_RUNTIME_BOUNDARIES`。审计保存 gate 已通过；三个隔离 Gradle 工程均生成成功，11 个精确 source JAR 均取得并校验，11/11 官方 SHA-1 sidecar 与实际文件匹配，六个 reference 候选只写入 Artifacts。Radiance、MCVR 与现有 References 均未修改、未构建、未启动。

| 候选 | 状态 | Java 文件 | sourceTreeHash |
|---|---:|---:|---|
| `minecraft-1.20.1` | PASS，Forge 47.4.23 patched development source | 5453 | `1c4e5f962848217994082cf556e9a295ed761c724cae95c74c6bcc887ba27f6c` |
| `minecraft-1.21.1` | PASS，NeoForge 21.1.250 patched development source | 6317 | `a891e8e3efe2db92610b9c57b8ca5378789db1df467dc25444d61b247a303bb1` |
| `minecraft-1.21.4` | PASS | 5744 | `66f2c6084fbb11f3f71bbcdcb922e5ac62949c4968da14d44b68d51609bd93ef` |
| `forge-47.4.23` | PASS | 826 | `e18f3191c2cc7e731b5a7f7690c54cbbff4c7e8e2145ea1ae0b1acf02af27003` |
| `neoforge-21.1.250` | PASS，release commit unavailable | 1142 | `0932269e10680b31308aa1a542f0bca78f51fb3637c452b0c7ab7daee6f6b9d8` |
| `fabric-loader-0.19.5` | PASS | 183 | `3d443192bb006c9e6695ef4c53707bd33889ba01da3cbe92929b344fd05e6ae4` |

Minecraft 1.20.1 与 1.21.1 候选直接机械提取本次精确 ModDev combined sources JAR 的全部 `.java`。1.20.1 含 5453 个 Java 文件及 76 个 Blaze3D 文件，绑定 Forge 47.4.23、MCPConfig `1.20.1-20230612.114412`、ForgeFlower 2.0.629.0、Gradle 8.14.1/JDK 21/Java 17 toolchain 和 `applyNeoforgePatches_62d2…`；1.21.1 含 6317 个 Java 文件及 75 个 Blaze3D 文件，绑定 NeoForge 21.1.250、FML 4.0.44、NeoForm `1.21.1-20240808.144430`、Vineflower 1.10.1、Gradle 8.14.1/JDK/toolchain 21 和 `applyNeoforgePatches_afa1…`。两者均含 `Minecraft`、`MinecraftServer`、`RenderSystem`、`BufferUploader` 以及对应 Loader 主类。`sourceKind` 准确记录为 `loader-patched-development-source`，并说明 combined tree 同时含 Loader 类。

Minecraft 1.21.4 由 Fabric Loom 1.15.4、Gradle 9.2.1、JDK 21、Mojang 官方 mappings 与 Vineflower 1.11.1 生成。sources JAR SHA-256 为 `dfa621ef61725a2abc594ba8e0120cbcdf4a1d6aea0250865637c54f57a1ab4e`。候选机械保留 sources JAR 内全部 `.java`，没有只截取 `net/minecraft`：含 215 个 `com/mojang` Java 文件和 88 个 Blaze3D Java 文件；`Minecraft.java`、`MinecraftServer.java`、`RenderSystem.java`、`BufferUploader.java` 全部存在。

Forge 47.4.23 由 ModDev LegacyForge 2.0.140、Gradle 8.14.1、JDK 21 启动并用 Java 17 toolchain 生成。`applyNeoforgePatches_62d2…` 明确绑定 `forge-1.20.1-47.4.23-userdev.jar`，reject ZIP 无条目，输出 SHA-256 `48e58e1c5711847de2da6114204567a65600f6783c535511b60ef74404946270`；实际 `CrashReport.java` 调用 Forge 的 `CrashReportExtender`/`CrashReportAnalyser`。候选合并七个精确发布 source JAR：Forge、FML Core、FML Loader、FML Early Display、Java FML Language、Low Code Language、Minecraft Language。

NeoForge 21.1.250 由 ModDev 2.0.140、Gradle 8.14.1、JDK/toolchain 21 生成。userdev 明确绑定 NeoForm `1.21.1-20240808.144430` 与 FML `4.0.44`。`applyNeoforgePatches_afa1…` reject ZIP 无条目，输出 SHA-256 `94b3718fc8661ebf3252d2bd46e05638c51ec6144a7eaf4708a9424fd22b7ac9`；实际 `CrashReport.java` 调用 NeoForge `CrashReportExtender`。候选包含 NeoForge sources、FML loader sources（含 `net.neoforged.fml` 与 `net.neoforged.neoforgespi`）和独立 Early Display sources。

Fabric Loader 0.19.5 候选来自官方精确 source JAR，含 Knot，artifact SHA-256 `b5a6ef0f608b62b362aacd3367fb2dba69915e0081b88c14f99209e5005dbf1d`。官方 `refs/tags/0.19.5` 直接指向 `c75cac153757b1a75e63e901bed5bc97eff630d3`。Forge 47.4.23 的 exact universal JAR manifest 给出 `GitCommit: 3c3f4960`，官方 GitHub API 解析为 `3c3f496015632ab84560be77a57cc57ac42c49e1`。NeoForge 21.1.250 的 sources/universal manifests 与 POM 都未给 commit，同名官方 tag/ref 也不存在，因此只以精确 Maven 坐标和 SHA-256 证明 release artifact，不声称取得 release commit。

运行时边界：1.20.1/1.21.1 Minecraft candidates 已包含静态 NeoForm/MCP config 与 Loader userdev 源码补丁及合并 Loader 类；CoreMod/Mixin 的运行时字节码变换仍没有作为“已经应用的 source”声明。Fabric Loom 的 Minecraft 反编译/映射也不能证明 Knot/Mixin 运行时变换已应用。Loader 精确 source-JAR entries 继续与 combined Minecraft development-source entries 分开。

旧目录建议：根先为旧 `minecraft-1.20.1` 与 `minecraft-1.21.1` 创建完整 ZIP 备份并校验，再回收并换为 full-package patched candidates；新树覆盖旧树所有相对路径（两者 old-only 均为 0）。同名 `forge-47.4.23` 候选是内容保持型超集（旧 767、新 826、old-only 0、changed 0、new-only 59），根应独立验 seal 后回收旧目录再部署；`neoforge-21.1.248` 与 `fabric-loader-0.19.3` 是不同精确版本，保留作为旧审计证据，新版本并列部署。

部署矩阵为三个完整 target：`minecraft-1.20.1 + forge-47.4.23`、`minecraft-1.21.1 + neoforge-21.1.250`、`minecraft-1.21.4 + fabric-loader-0.19.5`，实际部署仍交由根执行。

候选根：`D:\Workspaces\Artifacts\RadianceMCVRAudit\20260914\ReferenceCandidates`。部署状态：`DEFERRED_TO_ROOT`。
