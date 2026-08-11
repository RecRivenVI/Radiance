# 渲染与兼容依赖

Minecraft 与加载器组合固定为 `1.21.1-neoforge`。Target 依赖的唯一版本来源是 [target.properties](../../versions/1.21.1-neoforge/target.properties)，公共 Java 库与 Vulkan SDK 的版本由[版本目录](../../gradle/libs.versions.toml)提供。升级结果和运行证据见[当前状态](../project/status-current.md)。

## Java 与兼容模组

| 来源 | 配置位置 | 使用范围 |
| --- | --- | --- |
| NeoForge | `loader_version`、`loader_minimum` | 编译版本与 Radiance 自身的最低运行版本分别声明，不以第三方模组下限替代产品下限 |
| Create、Sable、Create Aeronautics | Target 事实与 [inputs.toml](../../inputs.toml) | 编译兼容接口、契约测试与额外运行实例；实际文件路径只写在 `local.toml` |
| Flywheel、Ponder、Registrate | Target 事实 | 跟随 Create 内嵌版本 |
| Veil、Sable Companion | Target 事实 | 跟随 Sable 内嵌版本，不另外叠装独立最新版 |
| Aeronautics、Offroad、Simulated | Target 事实与 Aeronautics 输入 | 跟随父发行包，避免混合不同嵌入版本 |
| SnakeYAML | 版本目录的 `snakeyaml` | 产品配置读取，随 GAME JAR 内嵌 |
| ASM、Gson | 版本目录的 `asm`、`gson` | 诊断与盘点工具，不因工具升级改变产品运行库边界 |

兼容元数据的固定区间由 Target 事实展开。Sable 自身要求的 NeoForge 下限只约束包含 Sable 的组合；Radiance 单独运行的加载器下限保持不变。

## 原生依赖

原生子模块的来源写在 [.gitmodules](../../.gitmodules)，精确版本为父仓库登记的 gitlink。Streamline 的版本、官方下载地址与校验值集中在 [Streamline.cmake](../../components/native_renderer/cmake/Streamline.cmake)。

- 选择正式发布；NRD 的预发布不进入常规升级。
- stb 没有正式标签或 Release；不把默认分支提交冒称最新正式版，保留已验证的固定提交。
- FidelityFX 使用现有 Vulkan 分支。官方新版 SDK 的 DirectX 后端不能直接替换此依赖；升级需要先有等价 Vulkan 接口，不能通过停用 FSR 或移除既有功能满足版本数字。
- GPU SDK 更新不授予运行库再分发权。公开二进制许可门禁继续有效。

## 构建解析

原生配置将 `VULKAN_SDK` 作为任务输入，每次 CMake 配置重新查找 Vulkan、shaderc、测试用 glslang 与 NRD 用 DXC，避免复用升级前的缓存路径。SDK 的来源和 CI 安装方式见[构建工具依赖](dependency-build_tools.md)。

`render_diagnostics` 的原生采集器随工具 JAR 产出，Target 将其中 `META-INF/natives/` 合并到 probe 资源。这样开发目录运行与模块内资源查找都能加载采集器；这些文件不进入主模组或外层发行 JAR。探针包装测试和两项启动验证共同检查资源可见性与实际挂接。
