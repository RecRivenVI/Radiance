# Radiance / MCVR 移植改动拆解记录

2026-09-14。所有变动未暂存、未提交、未推送。源文件删除采用回收站。

## 基线与工作区

| 仓库 | 当前分支 | 上游基线 | 来源分支提交 | 变更文件（含新增） |
| --- | --- | --- | --- | --- |
| Radiance | 1.21.1-neoforge | 414d8e3 | 9fe7811 | 232（新增 97） |
| MCVR | develop | 9905c81 | b611d24 | 182（新增 46） |

仅使用当前可访问的两个 stonecutter 分支，不恢复此前已清退的未提交整合成果。HEAD 保持基线提交；Radiance 的 develop 仍为上游镜像。

## 结构与来源处理

- Radiance 保留原 build.gradle、settings.gradle、gradle.properties、src/main/java/com/radiance、src/main/resources；Gradle Wrapper 保持上游 8.14.1。改为单项目 NeoForge 1.21.1 / NeoForge 21.1.248 / JDK 21。
- Java 211 个源文件按上游包名映射；保留 Mojmap API、Mixin 描述符和功能逻辑。137 个旧路径对应，74 个真实新增，3 个旧类由来源分支替代并删除。删除 Fabric metadata/accesswidener，不引入 Forge metadata。
- MCVR 保持 src/common、src/core、src/shader、extern 和原 JAVA_PROJECT_ROOT_DIR 接口，输出 core.dll/core.lib/libcore.so。10 个依赖指针及 DLSS URL 变更来自 MCVR stonecutter。未合并进 Radiance。
- 不引入 Stonecutter 插件、版本矩阵、versions/components 目录、嵌入渲染器副本、target 专用 CMake preset、旧 AGENTS 或模板治理。新增的测试和 Early Window 目录均承载真实功能。
- Radiance 的 Early Window 7 个 C++ 文件保留在 src/main/native/early_window；其最小 CMake 入口由旧原生构建中对应两个 target 抽出。
- MCVR 363 个源码文件与来源正文一致（忽略换行差异）；仅两个原生 CMake 入口及 NRD 配置头需要适配原目录/现有配置。没有新增渲染算法修复。

## 审阅补丁

以下按互不重叠的文件分组，涵盖新增文件。它们是审阅分组，不是伪造的历史提交；仅验证组合结果，不声称每组单独可编译。可逐组审阅后决定实际提交边界。

| 仓库 | 分组补丁 | 文件数 |
| --- | --- | --- |
| Radiance | [01-neoforge-build-and-metadata](patches/Radiance/01-neoforge-build-and-metadata.patch) | 11 |
| Radiance | [02-java-port-and-rendering](patches/Radiance/02-java-port-and-rendering.patch) | 153 |
| Radiance | [03-mod-compatibility](patches/Radiance/03-mod-compatibility.patch) | 36 |
| Radiance | [04-early-window](patches/Radiance/04-early-window.patch) | 17 |
| Radiance | [05-rendering-resources](patches/Radiance/05-rendering-resources.patch) | 3 |
| Radiance | [06-java-tests](patches/Radiance/06-java-tests.patch) | 12 |
| MCVR | [01-native-build-and-dependencies](patches/MCVR/01-native-build-and-dependencies.patch) | 13 |
| MCVR | [02-native-abi-and-vulkan](patches/MCVR/02-native-abi-and-vulkan.patch) | 29 |
| MCVR | [03-renderer-and-shaders](patches/MCVR/03-renderer-and-shaders.patch) | 123 |
| MCVR | [04-native-tests](patches/MCVR/04-native-tests.patch) | 17 |

已将全部补丁应用于基于上游的外部临时索引，并验证其与实际工作区一致。真实仓库索引没有暂存内容，也没有创建新提交。完整文件列表、哈希及重建树见 [review-patches.json](review-patches.json)。逐条来源去向见 [source-inventory.json](source-inventory.json)，Java 路径/命名映射见 [java-replay.json](java-replay.json)。

## 本轮实际验证

| 检查 | 结果 |
| --- | --- |
| Radiance Gradle build，JDK 21 + Wrapper 8.14.1 | PASS |
| Java 13 个套件、26 项测试 | PASS，0 failure/error/skip |
| Early Window bridge Java 与独立 Windows DLL | PASS |
| MCVR Release configure/build/install，NRD/FSR/XeSS 保持启用 | PASS |
| MCVR CTest | 16/16 PASS；去除了依赖已移除 target manifest 的一项检查，行为检查保留 |
| 主 renderer JNI | 17 个头、96 个声明全部有 DLL 导出 |
| Early Window JNI | 9 个声明全部有 DLL 导出 |
| 最终 JAR | core.dll 与安装产物相同、73 个 SPIR-V、两套内置 shader ZIP、NeoForge metadata 与 SnakeYAML 均存在 |
| 包内排除 | 无旧 io.github.recrivenvi.radiance 包、Fabric/Forge metadata、Early Window bridge 或 nvngx DLL |
| 两个工作区 diff --check 与真实索引 | PASS，未暂存 |

原 renderer 另有 1 个未由 Java 声明的导出、Early Window 另有 9 个旧实验/辅助导出；均保留来源语义，没有将它们宣称为已调用的能力。二进制符号对应不等于 JNI 生命周期或画面验收。

证据：[最终构建日志](radiance-final-build.log)、[打包与 Java 测试](package-verification.json)、[JNI 导出](jni-exports.json)、[MCVR 编译](mcvr-build-release.log)、[CTest](mcvr-ctest-release.log)、[Early Window 编译](early-window-build.log)。

## 保留的缺口

1. Radiance 9fe7811 引用了 PatchOfficialEarlyDisplayTask、PatchOfficialEarlyDisplayClasspathTask、FilterOriginalEarlyDisplayClasspathAction，但对应源码未进入来源 Git 树。保留 bridge 和原生实现，没有从后来被清退的工作树取回代码，也没有另写一个未经来源支持的补丁器。runClient 系列在启动前明确报告此缺口。完整构建通过不代表正式首装或客户端已可用。
2. MCVR 编译产生原分支 DLSS dlss_wrapper.cpp:472 的 C4700 未初始化 result 告警；本次按来源保留，没有混入额外修复。
3. 没有启动 Minecraft，没有进行视觉、稳定性、兼容模组组合或 Linux 验收。历史结果没有作为本轮通过证据。

后续可基于这些未提交变动逐组审阅，单独决定缺失 Early Display 接入及已知产品缺陷的修复范围。
