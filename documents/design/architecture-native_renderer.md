# 原生渲染组件

`native_renderer` 是原 MCVR 的源码组件，在统一仓库中维护渲染、着色器与资源生命周期；迁移前的源码位于历史 MCVR 仓库。

## 组件边界

Java/JNI 声明保留在 Target；已提交头文件在 `components/native_renderer/jni/`。Target 的 `generateJniHeaders` 生成到 `build/generated/jni/`，`verifyJniHeaders` 比较全部文件名称与字节。

组件通过 CMake 配置、编译、安装到自身 `build/`，独立 JAR 只包含原生运行库与着色器资源。十四个子模块保持原 URL 和固定提交。着色器包内的 `extern/sharc/include` 路径保持不变。

## 加载与发布

外层 SERVICE 发行 JAR 是安装入口；它包含引导类、许可、原生资源、资源索引和内嵌 GAME JAR。GAME Maven 构件 `Radiance-game` 只服务开发者编译集成。

## 验证

```powershell
.\gradlew.bat :native_renderer:check
.\gradlew.bat :native_renderer:gpuTest
.\gradlew.bat :version:1.21.1-neoforge:verifyJniHeaders
```

普通 `check` 只运行无 GPU 的 CTest；GPU 合同测试带 `gpu` 标签，需显式运行。Windows 使用 CMake 检出的默认 Visual Studio x64 生成器，完整 FFX/NRD 构建不支持 Ninja。

## 配置缓存

[gradle.properties](../../gradle.properties) 只保留配置缓存、JVM 参数与工作线程上限三项 Gradle 设置；JVM 堆上限保留 4G，工作线程上限为 2，不显式启用并行执行或构建缓存。配置文件仍通过模板的文件内容提供器读取，验证目录由 `ValidationDirectories` 列出。

当前关闭配置缓存。开启后的 `verifyRelease` 实测报告 `render_diagnostics` 的 `configureNativeAudit`、`buildNativeAudit`，以及参考 Target 的 `generateBootstrapRuntimeIndex`、`generateEmbeddedGameMetadata`、`generateLauncherMetadata` 捕获 Gradle 脚本对象，缓存无法保存；关闭原因与任务名写在配置项上方。不修改模板插件来绕过检查。

`native_renderer` 的 clang-format 检查使用注入的 `ExecOperations`，在任务执行时从 PATH 或 Visual Studio 查找工具；它可单独使用配置缓存，不表示整条构建链已经支持配置缓存。

## 本地故障取证

原生代码中的诊断由进程环境控制，产品普通启动默认不启用。项目内 Gradle 客户端通过[诊断配置](../development/debugging-gpu_diagnostics.md)设置轻量事件，并在 Windows 的 SERVICE 设备创建之前加载工具组件的外置 JVM agent。SDK 不进入 native_renderer 或产品包，现有 GPU 资源所有权、错误传播、保存和关闭顺序保持不变。

主要对象名称和有界 CPU 事件关联资源、命令缓冲、队列、fence 与 pass；逐实例 AS 成员追踪另行开启。CPU 录制记录与 GPU 完成证据分开，故障查询和 SDK 转储等待均有界。证据写入失败不得覆盖首个错误；未取得转储不等于没有 GPU 故障。

## 查询与场景参与

主世界静态区块维护按槽位排序的有效发布集合。发布、删除、重定位和资源释放使不可变场景快照失效；世界重置使用新的集合身份。不变帧复用已整理的地址、材质、来源面规则和命中组表，按实际动态前缀调整偏移，保持实体、Flywheel、区块的原有顺序。相机相对变换与 TLAS 仍逐帧生成，静态几何的上一帧地址和变换规则保持不变；外部区段继续使用逐视图变换历史。

快照持有发布时的不可变资源所有者，由现有帧退休机制保活，不持有可变槽位来代替 GPU 资源寿命。CPU 表的批量复制、元数据上传与 TLAS 构建仍存在，没有引入 GPU 常驻场景表或动态几何缓存。隔离诊断可设置 `MCVR_STATIC_SCENE_CACHE=0` 对照原有组织路径；`MCVR_CHUNK_SCENE_VERIFY=1` 检查快照与实际发布资源的对应关系，其额外遍历不纳入正常性能数据。

区块提交在短临界区内取得完整句柄、网格版本和弱对象身份，随后在锁外复制 JNI 借用输入并执行 CPU 拓扑准备。发布前重新持锁，核对代际、当前所有者、目标版本及 fatal 状态；世界重置、槽位复用或新版本使旧准备结果失效。准备阶段不持有旧区段的 GPU 资源；发光采样、上传与 GPU 完成后的发布和退休继续使用原有同步路径。拓扑、材料与查询语义不因缩小锁范围改变。

成像与传光的权威矩阵见[查询规则决策](adr-1-query_visibility.md)。`query_policy.hpp` 同时供 C++ 与 GLSL 的行为回归和 shader 使用；固定几何通道沿用现有 Java/JNI 数值，查询用途通过 payload 贯穿折射与分支，并保存在 Advanced 的跨 pass 状态中。

来源面状态继续供直接成像的硬件与 any-hit 路径使用。传光查询移除朝向剔除，重合成对成员只贡献一个界面。优先级覆盖率与照明分别执行，照明复用普通 PT 命中路径，不使用 lightmap 代替。

船内排除体由来源网格提取，按实例变换写入每个帧上下文的 `waterExclusionBuffer`，与场景元数据一起上传并退休。控制网格不计入 TLAS。普通水面及发光水面候选通过同一位置过滤；发光记录的备用通道保存源 UV，记录大小不变。JNI 签名不变。
