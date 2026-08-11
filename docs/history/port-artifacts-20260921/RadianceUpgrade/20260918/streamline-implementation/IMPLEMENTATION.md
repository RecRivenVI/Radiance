# Streamline / DLSS / Reflex 实施交接

日期：2026-09-18。状态：实现、构建和双实例部署完成；游戏内验收由用户执行。

最终检查：原生 CTest 26/26 通过；Java 114/114 通过（38 个测试类，无跳过）；运行资源及分发 JAR 校验通过；GPU 探针退出码 0。两份部署包 SHA-256 均为 `CD6B6476469AAE2AA3534066555CEBE8829CDE1D928DFC1CE5140B467BFE94E8`，内嵌 core.dll 为 `BD0FC527A69CA2F04A47894F6F1D362FFFB9C3768D6F85509BC6B873DC4CE726`。旧包均已备份，详见 `deployment.json`。

## 工作路径与边界

- Java：`D:\Workspaces\Repositories\GitHub\RecRivenVI\Radiance`
- C++：`D:\Workspaces\Repositories\GitHub\RecRivenVI\MCVR`
- 记录：`D:\Workspaces\Artifacts\RadianceUpgrade\20260918\streamline-implementation`
- 两个仓库均保留原有大量未提交修改。本次未暂存、提交、推送或重置。
- 本次未启动 Minecraft、重启用户实例或改写实例配置。隐藏 Win32 窗口探针是独立测试程序。
- 开始时补丁存为 `Radiance-before.patch` / `MCVR-before.patch`；这些不是完整仓库快照，也不包含所有既有未跟踪文件。

## 后端与依赖

NVIDIA 接入改为 Streamline 2.14.1：SR、RR、FG、Reflex、PCL 共用一个 runtime。移除直接 NGX 运行时调用及静态 NGX 链接；适配器暂保留 NGX 类型和原类名，以兼容现有模块接口。NRD、FSR、XeSS 仍使用原有独立后端。

官方 SDK 固定下载地址：
https://github.com/NVIDIA-RTX/Streamline/releases/download/v2.14.1/streamline-sdk-v2.14.1.zip

ZIP SHA-256：`92C4D954631A1710DA86CA3FA8D5034F2B9503838C95FC4AE977AE149319781B`。

`cmake/Streamline.cmake` 固定版本与哈希；打包 11 个正式 DLL，包含 sl.interposer/common/dlss/dlss_d/dlss_g/reflex/pcl、三个 nvngx DLL 与 NvLowLatencyVk。逐个签名验证为 Valid（`runtime-audit.json`）。匹配的 NGX DLL 为 310.9.1。携带原许可证及 Streamline 第三方许可证。运行时验证 interposer 签名，禁用 OTA，避免安装后自行漂移。

Vulkan 使用 manual hooking；保留 volk / VMA 的原生调度方式，接管实例、设备、物理设备枚举、交换链、acquire、present、device idle 等必需入口。特别是物理设备枚举必须经过 SL：探针实际暴露了绕过枚举导致实例映射缺失的问题，现已修正。volk 的 Win32 编译定义已补齐。

## 产品行为

视频设置新增“降噪与重建”选择页，映射到现有渲染图及持久化机制。原管线编辑器继续保留。

| 降噪 | 重建 | 实现 |
|---|---|---|
| NRD | 原生 | 现有 NRD 原生输出 |
| NRD | DLSS | NRD 后调用 SL SR |
| NRD | FSR | 保留现有 FSR |
| NRD | XeSS | 保留现有 XeSS |
| RR | 原生 | 一次 SL RR，DLAA 配置 |
| RR | DLSS | 一次 SL RR 完成降噪与重建，不额外串 SR |
| RR | FSR / XeSS | 明确显示组合不可用，不隐式替换 |

FG 独立于以上重建选择，首轮保持 2×。不要求打开 RR 或 SR，但仍要求硬件支持及有效 PT 深度、运动矢量和 HUDless 输入。每个重建上下文拥有独立 viewport、历史和 reset；主世界与 Ponder 继承同一组全局模型。SR、RR 模型按对应接口分别传入，FG 保留 Auto。

SR/RR 使用 frame token、资源标签、相机常量和 slEvaluateFeature。相同真实帧内重复绘制同一 Ponder viewport 时不重复推进历史。

FG 由 SL 代理交换链管理生成帧和节奏；引擎每帧只提交一个真实 present，删除原手工第二次 acquire/present。输入为 PT 运动矢量、硬件深度、HUDless 颜色、UI alpha。UI 继续使用现有差分覆盖遮罩参与 SDK 重合成；本次没有将所有 GUI 效果改成独立重绘图层，半透明与背景依赖效果仍需游戏验收。重建/销毁前等待 SL 代理 device idle，避免呈现线程使用已释放资源。

SL 2.14.1 的 Vulkan FG 不支持垂直同步：FG 开启且支持时使用 IMMEDIATE，关闭后恢复保存的 VSync 设置。没有 IMMEDIATE 时停用 FG 并保留真实帧呈现。FG 出错会记录具体结果并停用，等待重建重新初始化。

F3 保留原行数：通过 slDLSSGGetState 的实际呈现增量统计输出帧率，开启 FG 时沿用既有替换数字入口。探针与真实游戏的 RTSS 对照不能互相替代。

Reflex 提供关闭 / 开启 / 开启+Boost。FG 开启时有效模式至少为开启，不改写保存的用户选项。等待点位于输入事件采集前；串联 simulation、render submit、present 标记。Reflex 启用时通过 SDK 限帧，跳过原 CPU 限帧，避免双重等待；关闭或不支持时恢复原限帧。交换链重建后重新应用设置。

## 主要源码入口

MCVR：
- `cmake/Streamline.cmake`、根及 core/tests 的 `CMakeLists.txt`
- `src/core/render/streamline_runtime.hpp/.cpp`
- `src/core/render/modules/world/dlss/dlss_wrapper.hpp/.cpp`
- `src/core/render/modules/world/dlss/dlss_frame_generation.hpp/.cpp`
- `src/core/render/render_framework.hpp/.cpp`、`presentation_rates.hpp`
- Vulkan instance/device/swapchain 初始化与选模，Options / RendererProxy JNI
- `tests/streamline_probe.cpp`、`streamline_evaluate_probe.hpp`、`streamline_present_probe.hpp`

Radiance：
- `client/pipeline/RenderFeaturePlan.java`、`client/gui/RenderFeaturesScreen.java`
- 视频设置、Options、RendererProxy、MinecraftClientMixins、RenderSystemMixins
- 中英文文案、`build.gradle` 的 DLL 完整性校验
- 安装到 resources 的原生 DLL、SDK DLL 和着色器

另修正测试规则以适配新入口，并将已无 Java 对应类的陈旧生成 JNI 头移动至回收站；备份为 `stale-PonderPathTracer.h`。临时 RGBA UI 着色器被 alpha-only 路径替代，源码存 `superseded-streamline_ui_color.comp`，任务产生的失效 SPV 已移至回收站。

## 验证及证据解释

- 原生 Release install 与探针构建：最终日志 `build-install-complete-2.log`。
- Java 测试、资源及分发 JAR 校验：最终日志 `build-package-final.log`。
- 原生 CTest：最终日志 `ctest-final.log`。
- 实际 GPU 探针：最终日志 `probe-final.log`。SR Auto、RR D 在实际 GPU 上执行 evaluate、提交并完成；这证明接口和执行链路，不证明游戏图像质量。
- FG/Reflex 隐藏窗口探针执行 24 个真实帧，检查交换链、资源标签、Reflex 标记、呈现与错误状态。SDK 因窗口未聚焦禁止插帧：24 个输出不能当成生成帧验证成功。生成倍率、GUI、延迟、性能和窗口交互仍待用户验收。
- 探针使用人工图像并每帧等待 idle，不是性能测试。旧 `probe.log` 是最初枚举映射问题；旧 `probe-present.log` 的退出码 8 来自当时强制要求隐藏窗口输出插帧，并非当前产品实测崩溃。
- SDK 在 manual hooking 下会提示部分命令记录 hook 不受支持；引擎自行重新绑定后续管线状态，SR/RR 探针实际完成。保留日志，不将警告等同于已验证无风险。

## 用户验收建议

1. 使用现有配置启动主世界和 Ponder，检查 RR D 与此前画质，切换质量档、调整窗口、退出世界再进入。
2. NRD+DLSS SR，随后 NRD+FSR / XeSS，确认各自能工作；分别切 FG，观察 F3 与 RTSS。
3. 检查 FG 开关前后真实帧耗时、输出帧率、UI 字体/透明效果/背景模糊，以及切出窗口、回到菜单和正常退出。
4. 切换 Reflex 三档；不把“有标记、Sleep 成功”宣称为已测得端到端延迟改善。

Windows 本机为本次验证平台；不宣称 Linux 或其他显卡已经通过验收。部署 JAR 与 core.dll 的完整哈希、两个实例路径及旧 JAR 备份见 `deployment.json`。
