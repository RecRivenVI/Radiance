# 参考更新调查交接（候选，不等于已取得）

Researcher：Luna / Max，任务 `/root/reference_plan`。调查后交接由根保存；本文件只列候选和证据约束，实际取得/验证结果以reference-mods与reference-loaders清单为准。

| 目标 | 候选/精确来源 | 限制 |
|---|---|---|
| Forge 1.20.1 | 官方Maven `net.minecraftforge:forge:1.20.1-47.4.23:sources` | 未找到同名发布Git tag；不能用1.20.1开发分支头冒充发布commit |
| NeoForge 1.21.1 | 官方Maven `net.neoforged:neoforge:21.1.250:sources`，NeoForm1.21.1-20240808.144430/FML4.0.44 | 根再次读取Maven metadata确认最新21.1.250；release无同名Git tag，需要userdev/manifest溯源 |
| Fabric 1.21.4 | 官方Fabric Loader0.19.5，commit c75cac153757b1a75e63e901bed5bc97eff630d3 | 根Maven metadata确认最新数字版本0.19.5；运行时Knot/Mixin变换不在普通反编译源内 |
| Create | mc1.21.1-6.0.10 / ac0c444d9828da3453ae8cc65338e8de063286fb | Creators-of-Create/Create |
| Sable | mc1.21.1-2.0.5-neoforge / 6966d2928340de7631abcecf8549904b877df0a8 | ryanhcode/sable |
| Veil | 当前适用4.5.0 / 8af4e37925febb7309745c5d381f9b8807fd55b8；审计实际4.3.2 / 540ad3778c1a5dafe905420adb21cb05e459b5c4 | FoundryMC/Veil；两份分开 |
| Aeronautics/Simulated/Offroad | main e720946bb5e84d618d0f1aa47e1a19ab2adb5bd7，声明1.3.2/MC1.21.1/NeoForge21.1.247 | Creators-of-Aeronautics/Simulated-Project；无发布tag，明确开发快照 |
| Flywheel | 官方Maven1.0.6 sources | Engine-Room/Flywheel未找到1.0.6 tag；不得用1.0.5 tag冒充 |
| Ponder | 最新1.0.87+mc1.21.1；审计实际1.0.82+mc1.21.1 | Creators-of-Create/Ponder；精确Maven source JAR优先 |
| SableCompanion | 1.6.0 | ryanhcode/sable-companion；需复核commit |
| Zume | release/1.2.2 / 0bc4e9b0f67be27bcb036439bda09a33a942c5d1 | Nolij/Zume |

来源入口：

- https://maven.minecraftforge.net/net/minecraftforge/forge/maven-metadata.xml （根Python直连403；Researcher查询取得版本信息，现有缓存仍可复核artifact）
- https://maven.neoforged.net/releases/net/neoforged/neoforge/maven-metadata.xml
- https://meta.fabricmc.net/v2/versions/loader/1.21.4
- https://maven.fabricmc.net/net/fabricmc/fabric-loader/maven-metadata.xml
- https://github.com/neoforged/ModDevGradle / LEGACY.md：ModDev与NeoForm链可生成补丁开发源；需检查具体stage与rejects，不能根据产物名宣称已应用补丁。

现有缓存包含1.20.1/1.21.1/1.21.4 vanilla输入、Mojang mappings、NeoForm decompile及Forge47.4.23和NeoForge21.1.248补丁阶段；必须将stage日志的输入版本与输出绑定。1.21.4有Loom cache/Yarn1.21.4+build.8，尚无参考库entry。所有工具链执行应在Artifacts内独立生成工程，不能运行审计仓库构建任务。

旧Minecraft REFERENCE.json的Mojang version-manifest package地址可能已变化，但主要client/server/mappings hash不变；复用时必须记录实际artifact，而非把新的manifest文字当作新的游戏字节。
