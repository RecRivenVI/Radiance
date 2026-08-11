# MCVR DLSS 产品升级记录（进行中）

日期：2026-09-18。全部改动未提交、未暂存，保留原有独立脏改动。

## 工作路径与授权

- Radiance：D:/Workspaces/Repositories/GitHub/RecRivenVI/Radiance
- MCVR：D:/Workspaces/Repositories/GitHub/RecRivenVI/MCVR
- 本记录及实验产物：D:/Workspaces/Artifacts/RadianceUpgrade/20260918
- Prism 部署：E:/Minecraft/PrismLauncherDev/instances/Radiance Test/minecraft/mods
- 仓库测试实例：Radiance/run/ponder-readback-20260918-144319/full
- 用户明确要求 SR、RR、FG 按功能分别全局选择，主世界和 Ponder 共用；任何修改完成后部署。
- 用户于本次验收明确要求保持当前实例，后续不自动关闭、切换配置或重启。没有执行关闭游戏命令。仓库实例内用户通过 UI 修改的设置不会被测试前备份覆盖。

## 实现范围

1. NVIDIA SDK 310.7.0 → 310.9.1；SR/RR/FG DLL、签名与包内 SHA256 已核对，来源清单同步更新。
2. SR 独立功能（SuperSampling），新预设 PT → NRD → DLSS SR → tone/post；纯 SR 不替代 PT 降噪。RR 使用原独立重建功能。
3. 全局 SR/RR/FG 模型参数持久化；改变设置触发管线重建，使主世界和 Ponder 继承。移除 Ponder 专用临时模型文件读取。
4. SR 可选 Auto、E/F（弃用但仍在 SDK 接口中）、J/K/L/M；RR Auto/D/E/F。FG API 无公开模型提示接口，目前只有 Auto，不伪造字母选择。模型是请求提示，实际 SDK/驱动仍可覆盖，日志不把 requested 写成强制生效。
5. FG 2× 后端接入；按功能分别查询 Vulkan 扩展与硬件能力，不因可选 FG 缺失屏蔽 SR/RR。
6. SR/FG 从 PT view depth 和实际投影矩阵生成 R32F 硬件深度；覆盖透视/正交相机，未改变原 FSR 深度路径。
7. 每个 NGX SR/RR feature 独立参数对象；FG 独立参数、历史、保留真实帧、输出资源，重建/退出在 queue idle 后释放，先于 NGX shutdown。

## 用户验收发现与后续修正

首版 JAR 14507A23D2B0762AEABF9F9E8589C801F0337B275B3077DD2C7ABEB019B5AA6A，DLL 8EF1218930FAB0DAB2D2311D7EFAA818FAF8CF975DC7B885F2FB855A2FEDEBA6。

用户报告：模型文案太长，看不到模型名；FG 开启后帧率从约 80 降至约 20，UI 一同插帧。该首版 **不满足产品验收**。创建/evaluate/present 成功不等于性能或画质合格。

修正版本（构建中）：
- 标签缩短至「DLSS 模型」「RR 模型」「帧生成 2×」，详情移至提示。
- 移除逐帧末尾 CPU fence wait 和人为休眠；额外提交 command/fence/acquire semaphore 使用 ring，仅在复用旧槽时等待。presentation wait semaphore 提供 GPU 顺序，不再 CPU 等 GPU 完成后才能 present。还不是独立呈现线程，不能宣称已解决所有延迟/帧间隔问题。
- 在 world→overlay 合成之后、UI 绘制之前保留逐帧 HUDless 精确副本。
- 提供 SDK HUDless + UIAlpha，启用 UserInterfaceRecompositionEnabled。
- UIAlpha 是保守的已合成像素覆盖区域：同一 UNORM 像素前后 RGB 不同即保留。它不是原材质 alpha，半透明 UI 背后的已混合真实帧像素一起保留；与背景完全同色的 UI 无法由差分识别。这是明确的实现限制。
- FG GPU timestamp、额外 acquire 与复用等待分别统计，日志 gpu_ms/acquire_ms/slot_wait_ms；正常 render fps 不当作包含生成帧的显示 fps。

## 验证证据与界限

- build-sr-rr.log：SR/RR 首轮完整构建成功。
- build-fg.log：曾出现 MSVC class/struct 前向声明不一致导致链接失败；已统一 FrameworkContext 声明。
- build-upgrade.log / build-final.log / build-model-coverage.log：后续完整构建成功。
- ctest.log：25/26 通过。JNI 旧检查要求类与 cpp 同名，PonderProxy 实际在 ponder_path_tracer.cpp；同时发现旧生成头 com_radiance_compatibility_ponder_PonderPathTracer.h 的 trace 已不是当前 Java native。未改动无关 Ponder 实现以迎合测试。
- jni-audit.json：聚合检查当前 middleware，165 个非陈旧声明均有实现；1 个历史生成声明单列。不是 JVM 反射/运行时全面验收。
- depth-math-tests.json：384 个 CPU 参考深度点通过，不能冒充 GPU readback。
- package-audit.json：首版实际 JAR 的 core.dll、三份 NVIDIA DLL、版本/哈希和内嵌 SR module 核对。
- rr-run.json / rr-stdout.log / rr-stderr.log：PID 43040，16:10:37 启动；16:11:53 Dev joined the game。SDK 能力 SR=1 RR=1 FG=1；SDK 日志确认 D 提示生效。用户随后改变模型、窗口尺寸、FG 开关；不是受控 A/B 性能试验。
- 首版 FG 创建和成对 queue present 成功已有日志，但用户明确报告性能与 UI 失败。不得把它标为 FG 通过。
- 纯 SR 尚未在游戏中确认创建/输出；Ponder 各模型、FG 重做版性能/画质/长期稳定性未验收。
- 首版日志另有 Veil Unsupported vertex layout，未在本任务归因或修改。

## 直接依赖核对

| 依赖 | 当前检出 | 初次查询正式版 | commit |
|---|---|---|---|
| extern/glfw | 3.5.1 | 3.5.1 | `d9d6f0f1f967` |
| extern/volk | vulkan-sdk-1.4.357.0 | 1.4.350 | `776893306c5d` |
| extern/vma | v3.4.0 | v3.4.0 | `3aa921224c15` |
| extern/glm | 1.0.3 | 1.0.3 | `8d1fd52e5ab5` |
| extern/vulkan_headers | v1.4.357 | 无正式 release | `e3b1eec08173` |
| extern/stb | f58f558 | 无正式 release | `f58f558c120e` |
| extern/DLSS | v310.9.1 | v310.9.1 | `374959484e79` |
| extern/nrd | v4.17.3 | v4.17.3 | `792eff196afd` |
| extern/FidelityFX-SDK | v1.1.4-6-gd08c34c | 无正式 release | `d08c34ca8a9d` |
| extern/sharc | v1.6.5.0 | v1.6.5.0 | `0b9f58bbc8c4` |
| extern/minizip-ng | 4.2.2 | 4.2.2 | `7b2387161c54` |
| extern/json | v3.12.0 | v3.12.0 | `55f93686c015` |
| extern/xess | v3.0.2 | v3.0.2 | `8fe81bdbbaf0` |
| extern/tinyexpr | e9799be | 无正式 release | `e9799be7497f` |

压缩传递依赖：zlib-ng 固定 2.3.3；XZ 从 v5.8.4 后的开发提交锁回正式 v5.8.4；7-Zip/PPMd 26.00 → 26.03；bzip2 继续固定 1.0.8 的 commit。证据 compression-releases.json。

兼容性/发布边界：
- FidelityFX-SDK 官方 2.3.0 明确不支持 Vulkan，保留 Interstellarss Vulkan 分支，尚无“全部官方最新”结论。用户未答该例外问题，采用保留已有 FSR 的兼容默认。
- stb 没有统一正式 release，tinyexpr 当前是 Radiance 定制 fork；不擅自覆盖定制补丁或把 moving HEAD 称为正式版。
- NRD 自带 ShaderMake/MathLib/NRI 等构建依赖沿 NRD 正式版本指定的 pins；没有宣称它们都独立升级到各自最新。
- 本机 Vulkan SDK 工具链 1.4.341.1 未替换；仓库 Vulkan headers/volk 是 1.4.357 SDK tag。

来源：
- https://github.com/NVIDIA/DLSS/releases/tag/v310.9.1
- NVIDIA/DLSS 同 tag include 与 doc 下 SR、RR、FG 官方指南（提取为 sr-guide.txt / ngx-fg-guide.txt）。
- https://raw.githubusercontent.com/GPUOpen-LibrariesAndSDKs/FidelityFX-SDK/v2.3.0/readme.md
- https://github.com/tukaani-project/xz/releases/tag/v5.8.4
- https://github.com/ip7z/7zip/releases/tag/26.03
- https://github.com/zlib-ng/zlib-ng/releases/tag/2.3.3

## 收尾要求

新 FG 版本完成编译、GPU 覆盖测试后部署两个已授权实例并写独立哈希记录。按用户指示不自动启动。当前任务尚不能标记“所有依赖无例外最新、SR/RR/FG 全面完成”。

## FG 修订版部署结果

- build-fg-rework.log：完整构建成功。
- ui-coverage-gpu.log：真实 Vulkan GPU 覆盖测试通过，51 个像素覆盖不变世界、1-LSB UI 边缘、不透明 UI、仅 alpha 改变和非完整工作组。只证明 coverage shader，不证明 NGX UI 重合成最终画质。
- ctest-fg-rework.log：framebuffer GPU、DLSS resource、scene scope/camera 4/4 通过。
- 新 JAR SHA256：17E266BA79A944C2DE8EBE8D75145901BC61AEE74D39D019FB3F3A30635D235C
- 新 DLL SHA256：4FD9476DA77478823A3B399C3D22DFEF442318305C7934F53EB31AC3C372BA83
- 已替换两个授权实例的 JAR 并核对相同哈希，见 deployment-fg-rework.json。包内短标签、UI coverage SPIR-V、NVIDIA DLL 哈希已核对。
- 未启动或重启游戏，未改用户通过 UI 选择的模型/FG 配置。
- 新版游戏性能/帧间隔、SDK UIR 实际视觉、SR 实际输出仍待验收，不能宣称已恢复 80 FPS。
