# Radiance Early Display：NeoForge 21.1.248 / FML 4.0.43 加载可行性

日期：2026-09-14  
范围：只评估“一个普通 Radiance Mod JAR 放进 `mods/`，不增加启动参数、不替换 FML/earlydisplay JAR、不预置文件且首次启动即生效”这一包装条件。没有启动游戏或构建项目，也未修改现有源码/配置；结论来自本地缓存 bytecode/JAR 与官方源码对照。调查中一次临时 manifest 解包目录已移入回收站。

## 结论

原 `9fe7811` 的 patch-sidecar 路线在当前材料中不满足条件：归档构建把 Early Window bridge 放在独立 `earlyWindowBridge` source set，主 mod 排除了该包，只对 `earlydisplay:4.0.43` 做 compile-only；`verifyEarlyWindowLaunchInputs` 又明确因缺少 `PatchOfficialEarlyDisplayTask`、`PatchOfficialEarlyDisplayClasspathTask`、`FilterOriginalEarlyDisplayClasspathAction` 而阻止客户端启动。因此它既不是一个自包含的普通 Radiance Mod JAR，也没有可验证的补丁产物。

NeoForge/FML 4.0.43 确实存在从 `mods/` 参与最早 SERVICE 层的入口，不能仅因普通 `@Mod` 初始化较晚就断言所有单 JAR 方案不可能。但“只向现有 Radiance JAR 增加 `ImmediateWindowProvider` service 文件”仍然不够：FML 会先把该 outer JAR 放进 SERVICE 层，随后 `LaunchContext` 将它标记为已定位，普通 `ModsFolderLocator` 的 `addPath` 会跳过它，故它不会自动成为 GAME 层 `@Mod`。

一个新的单 JAR bootstrap 在代码层有可行性假设：同一 JAR 提供 `GraphicsBootstrapper`、`ImmediateWindowProvider` 与自定义 `IModFileCandidateLocator`；前者在 provider 选择前把 `EARLY_WINDOW_PROVIDER` 改成自定义名称，中者在早期初始化；后者在 FML mod scan 阶段用 `IDiscoveryPipeline.addJarContent` 重新注册自身的 `JarContents` 为 MOD，绕过 `addLocated`，让 `ModValidator` 再把它送入 GAME 层。这是公开 SPI/当前实现允许的方向，尚未是通过结论；必须用最小原型确认 outer JAR 的路径获取、SERVICE/GAME 双 module、类可见性、`@Mod` 元数据、原生库解包和首次启动行为。

## 证据表

| 检查点 | 4.0.43 实际行为 | 对单 JAR 的含义 | 证据 |
|---|---|---|---|
| 最早 discovery 顺序 | ModLauncher 先从 BOOT 层加载 `ITransformerDiscoveryService`；调用 `candidates(gameDir, launchTarget)`，把返回的 `NamedPath` 放进 SERVICE 层并 build；之后才调用 `earlyInitialization`。 | `mods/` JAR 可以在 FML mod scan 前进入 SERVICE 层。 | 官方 [TransformationServicesHandler](https://github.com/McModLauncher/modlauncher/blob/main/src/main/java/cpw/mods/modlauncher/TransformationServicesHandler.java)；本地 `modlauncher-11.0.5.jar` bytecode 一致。 |
| `mods/` 早期扫描 | `ModDirTransformerDiscoverer` 扫描游戏目录下 `mods/` 深度 1 的普通 `.jar`；它只在 JAR 的 `JarMetadata.providers()` 含受认可 service name 时返回路径。 | 一个普通 mod JAR 带早期 service 描述符即可触发 SERVICE 路径。 | 官方 [ModDirTransformerDiscoverer](https://github.com/neoforged/FancyModLoader/blob/1.21.1/loader/src/main/java/net/neoforged/fml/loading/ModDirTransformerDiscoverer.java)。 |
| 受认可的早期 service | 集合明确包含 `ITransformationService`、`IModFileCandidateLocator`、`IModFileReader`、`IDependencyLocator`、`GraphicsBootstrapper`、`ImmediateWindowProvider`。 | `ImmediateWindowProvider`、`GraphicsBootstrapper`、FML locating SPI 有入口；`ILaunchPluginService` 没有这个入口。 | 官方 [TransformerDiscovererConstants](https://github.com/neoforged/FancyModLoader/blob/1.21.1/loader/src/main/java/net/neoforged/fml/loading/TransformerDiscovererConstants.java)。 |
| provider 选择 | `ImmediateWindowHandler.load` 先运行 SERVICE 层所有 `GraphicsBootstrapper`，再读取 `EARLY_WINDOW_PROVIDER`，按 `name()` 选择 `ImmediateWindowProvider`，随后调用 `initialize`。默认名称是 `fmlearlywindow`，默认 `earlyWindowControl=true`。 | 首次启动无需预置 `fml.toml` 时，自定义 provider 需要同 JAR 的 `GraphicsBootstrapper` 在选择前改名；否则仍会选择官方 `fmlearlywindow`。 | 官方 [ImmediateWindowHandler](https://github.com/neoforged/FancyModLoader/blob/1.21.1/loader/src/main/java/net/neoforged/fml/loading/ImmediateWindowHandler.java)、[FMLConfig](https://github.com/neoforged/FancyModLoader/blob/1.21.1/loader/src/main/java/net/neoforged/fml/loading/FMLConfig.java)。 |
| 普通 `@Mod` 的冲突 | SERVICE 层 build 完成后，`FMLServiceProvider.initialize` 才创建 `LaunchContext`；构造器索引当前已有层的 module 路径，其中包括早期 SERVICE JAR。之后 `ModDiscoverer.DiscoveryPipeline.addPath` 首先 `launchContext.addLocated(primaryPath)`，已定位路径直接跳过。 | 纯 `ImmediateWindowProvider`/`ITransformationService` descriptor 会使同一 outer JAR 早到，但普通 `ModsFolderLocator` 不会再把它当 GAME 层 Radiance mod。 | 官方 [FMLServiceProvider](https://github.com/neoforged/FancyModLoader/blob/1.21.1/loader/src/main/java/net/neoforged/fml/loading/FMLServiceProvider.java)、[LaunchContext](https://github.com/neoforged/FancyModLoader/blob/1.21.1/loader/src/main/java/net/neoforged/fml/loading/LaunchContext.java)、[ModDiscoverer addPath](https://github.com/neoforged/FancyModLoader/blob/1.21.1/loader/src/main/java/net/neoforged/fml/loading/moddiscovery/ModDiscoverer.java)。 |
| 可绕过的 locating SPI | `IDiscoveryPipeline` 暴露 `addJarContent`；实现直接调用 reader 并 `addModFile`，不会经过 `addPath` 的 `addLocated` 检查。`ModValidator.getModResources` 会把已排序 mod 的 `SecureJar` 放到 GAME 层。 | 自定义 `IModFileCandidateLocator` 可以尝试用同一 outer JAR 的 `JarContents` 重新注册 MOD；这是单 JAR 方案的关键假设。 | 官方 [IDiscoveryPipeline](https://github.com/neoforged/FancyModLoader/blob/1.21.1/loader/src/main/java/net/neoforged/neoforgespi/locating/IDiscoveryPipeline.java)、[ModDiscoverer addJarContent](https://github.com/neoforged/FancyModLoader/blob/1.21.1/loader/src/main/java/net/neoforged/fml/loading/moddiscovery/ModDiscoverer.java)、[ModValidator](https://github.com/neoforged/FancyModLoader/blob/1.21.1/loader/src/main/java/net/neoforged/fml/loading/moddiscovery/ModValidator.java)。 |
| `ITransformationService` | SERVICE 层 `ServiceLoader` 在 early initialization 后收集 transformation service；FML 的 `beginScanning` 进入 `ModDiscoverer`。 | 可早加载自定义 transformation service，但它单独不能让被 `addLocated` 跳过的 outer JAR 变成 `@Mod`。 | 官方 [TransformationServicesHandler](https://github.com/McModLauncher/modlauncher/blob/main/src/main/java/cpw/mods/modlauncher/TransformationServicesHandler.java)、[FMLLoader](https://github.com/neoforged/FancyModLoader/blob/1.21.1/loader/src/main/java/net/neoforged/fml/loading/FMLLoader.java)。 |
| `ILaunchPluginService` | `LaunchPluginHandler` 构造时只 `ServiceLoader.load(BOOT, ILaunchPluginService.class)`；该对象在 `Launcher.run` 的 `discoverServices` 之前创建。 | 普通 `mods/` JAR 不能靠该 descriptor 提供早期 launch plugin；即使同 JAR 另有 `ImmediateWindowProvider` 使其进入 SERVICE，也不会回填已构造的 plugin map。 | 官方 [LaunchPluginHandler](https://github.com/McModLauncher/modlauncher/blob/main/src/main/java/cpw/mods/modlauncher/LaunchPluginHandler.java)。 |
| manifest `FMLModType` | 当前枚举只有 `MOD`、`LIBRARY`、`GAMELIBRARY`；没有 `GAME` 或 `LANGPROVIDER`。缺省值是 `MOD`。官方注释说明 `MOD` 进 GAME 层，`LIBRARY` 可在 PLUGIN 层提供 language provider/非 MC 代码，`GAMELIBRARY` 是可引用 MC 代码的 GAME library。 | `FMLModType=GAME/LANGPROVIDER` 不是本版本路由；`LIBRARY` 不能替代普通 `@Mod`，`GAMELIBRARY` 也不是带 mod entries 的推荐类型（接口注释要求有 mod entries 时用 MOD）。 | 官方 [IModFile.Type](https://github.com/neoforged/FancyModLoader/blob/1.21.1/loader/src/main/java/net/neoforged/neoforgespi/locating/IModFile.java)、本地 `ModFile.parseType` bytecode。 |
| module descriptor | `ModJarMetadata` 为带 `neoforge.mods.toml` 的 mod JAR 构造 automatic module，并把 `META-INF/services` 条目写成 `provides`。 | service descriptor 在 SERVICE layer 可被 `ServiceLoader.load(layer, ...)` 看见；但它不解决同 JAR 的 GAME mod 重新注册问题。 | 官方 [ModJarMetadata](https://github.com/neoforged/FancyModLoader/blob/1.21.1/loader/src/main/java/net/neoforged/fml/loading/moddiscovery/ModJarMetadata.java)。 |

## 当前 Radiance/归档材料

- 本地 `build/moddev/clientRunProgramArgs.txt` 的目标为 `forgeclientdev`；该名称在 4.0.43 `ImmediateWindowHandler` 的客户端目标白名单中，所以目标名本身不是这里的阻塞点。
- 本地当前 JAR `build/libs/Radiance-0.1.5-alpha-neoforge-1.21.1.jar` 的 SHA-256 为 `C7F545B9C5A621F54D0FCA60091B186E2FCD0D68906DCF498C18213533D12699`；静态条目包含 `META-INF/neoforge.mods.toml` 与 `core.dll`，没有 `META-INF/services/`。这只是当前产物结构记录，不是运行验收。
- 官方缓存 sibling `earlydisplay-4.0.43.jar` 的 manifest 为 `FMLModType: LIBRARY`，服务文件为 `META-INF/services/net.neoforged.neoforgespi.earlywindow.ImmediateWindowProvider`，实现是 `net.neoforged.fml.earlydisplay.DisplayWindow`；loader sibling 同样标记为 `LIBRARY`。这解释了官方早期窗口为什么作为库进入 bootstrap/SERVICE 体系，不代表 Radiance bridge 已可替换它。
- 归档 `D:\Workspaces\Artifacts\Radiance-EarlyDisplay-Isolation-20260914\before\build.gradle` 的 `earlyWindowBridge` source set 与缺失 patch helper 检查，是原 patch-sidecar 未恢复的直接证据。

## 需要原型才能升级为 PASS 的项目

1. 在隔离临时实例中仅放一个实验 JAR：同 JAR 注册 `GraphicsBootstrapper`、`ImmediateWindowProvider`、`IModFileCandidateLocator` 与普通 `neoforge.mods.toml`；确认 `GraphicsBootstrapper` 首次启动即可把 provider 名称切换到自定义实现。
2. 由自定义 locator 找到自身 outer JAR，调用 `JarContents.of(path)` 和 `pipeline.addJarContent(...)`；确认日志同时出现早期 provider 初始化和 Radiance `@Mod` 被送入 GAME layer，没有 duplicate/invalid-mod 错误。
3. 确认 SERVICE 副本与 GAME 副本的 module name/classloader、`META-INF/services`、Mixin 扫描和原生资源解包；尤其确认 provider 的 native extraction 路径在普通 launcher 下可用。
4. 在全新实例（无 `fml.toml`、无预置文件、无额外参数）验证首次启动和至少一次早窗口到 GAME 层的调用链。当前没有做这些验证，所以不能把该方向写成已通过。

