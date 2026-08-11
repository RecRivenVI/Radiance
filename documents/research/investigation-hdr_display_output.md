# HDR 显示输出与 SDR 成像

2026-10-31 调查并实现显示输出、材质发光一致性与 Bloom。自动验证和物理 HDR 目视验收分别记录。

## 问题

原有世界使用浮点 HDR 图像计算，但合成与窗口输出仍为 SDR；内部浮点不是 HDR 显示支持。两套内建 PT 对首次可见与后续命中使用不同发光参数，Advanced 面光源采样又采用首次命中的值；无反射分支还有固定强度。光源代理将 NativeImage 的 ABGR 当作 ARGB，并在编码颜色中求均值，无法与线性材质辐亮度一致。

## 方法

复用原有 Vulkan 呈现、GUI、曝光和补帧结构，先统一输入，再增加输出与独立 Bloom。用 CPU 策略测试、GPU 生产函数及 GUI 合成测试、实际包着色器编译和隔离客户端读回逐层检查。

## 发现

### 发光输入

材质发光采用原有两个配置值的乘积作为共同增益，各处使用同一函数；现有数值不重置，界面名称改为“材质发光增益／参考强度”以说明新含义。Vanilla PT 默认 1×16，Advanced 默认 4×8；同一算法中的直接、反射、折射和面光源采用各自相同的乘积。此项修正改变原先不一致的相对亮度，尤其 Advanced 的材质发光会提高，最终光源与环境比例须目视验收。纹理代理在线性空间平均，正确读取 ABGR。眼睛附加层、来源独立发光等保留各自强度，不把 lightmap 当作光源。

### 显示与合成

根据窗口所在显示器当前 DXGI 色彩模式及 Vulkan 公布的格式选择 HDR10（RGB10／ST2084／Rec.2020），其次 scRGB（FP16 线性）；系统或窗口不支持则 SDR。显示能力每半秒检查一次，换显示器后不沿用旧显示器的缓存，不逐帧枚举硬件。HDR 元数据扩展可用时提交峰值与色域信息。格式变化沿用安全重建、等待在途资源及既有曝光状态继承；仅改变参考白或峰值时原位更新参数和元数据，不重建场景。

世界与 GUI 合成画布保持 FP16 扩展 sRGB 表达，以保留原有 GUI 混合域和高亮余量；最后统一转换到显示编码。世界参考白与 GUI 白独立，GUI 不进入曝光测光或 Bloom。SDR 保留原有色调映射选项；HDR 使用按峰值压缩的平滑亮部肩部。HDR10 下最终图与无界面图一起编码，UI Alpha 仍取 FP16 合成覆盖，不能取 RGB10 的两位 alpha。scRGB 暂停 FG，遵循当前 Streamline 的格式限制。

FP16 画布、最终输出与 Bloom 需要额外带宽和显存，是明确成本，不能称为性能优化。Bloom 复用每帧五层金字塔，在曝光后亮度域筛选亮部与扩散；关闭后停止派发，已分配容量保持有界。没有进行性能实验。

### 依据

- [Vulkan 色彩空间](https://docs.vulkan.org/refpages/latest/refpages/source/VkColorSpaceKHR.html)与 [HDR 元数据](https://docs.vulkan.org/refpages/latest/refpages/source/VK_EXT_hdr_metadata.html)区分格式、编码和显示参数。
- [Windows Advanced Color](https://learn.microsoft.com/en-us/windows/win32/direct3darticles/high-dynamic-range)定义 scRGB 的 1.0 为 80 nit，界面白与内容峰值应分开处理；[DXGI 输出描述](https://learn.microsoft.com/en-us/windows/win32/api/dxgi1_6/ns-dxgi1_6-dxgi_output_desc1)提供当前模式及亮度能力。
- [Streamline DLSS-G 指南](https://github.com/NVIDIA-RTX/Streamline/blob/main/docs/ProgrammingGuideDLSS_G.md)要求匹配的 HDR 输入，当前支持 HDR10 RGB10，FP16 scRGB 不作为 FG 路径。

## 结论

最终候选 `d5514c79b56f0d45c27a33215de4e460278db8874cf663308bb81c1c4ea14e61`，Native `d4faef150e532785dcecd5bdab3dde7d96086735f31d3235a95164acd362329b`，API 16；Extra 未修改，沿用已安装版本。配套身份位于 `versions/1.21.1-neoforge/build/candidates/hdr-display-20261031-release/candidate.json`。已投放 Prism 并核对哈希，未启动外部游戏，不提交或推送。

| 验证 | 结果与边界 |
| --- | --- |
| 构建 | `spotlessApply check verifyRelease verifyCompliance --strict` 成功；82 项非 GPU CTest、28 项 GPU CTest 通过 |
| 实际着色器包 | 268 个配置请求编译成功；最终 JAR 中 221 个相关源码文件与该次编译输入逐字节相同 |
| 完整场景 | 首个候选 `9042e00b…` 的 Vanilla PT 与 Advanced 各九个场景正常退出、无设备丢失；覆盖 SDR／HDR、Bloom 开关、玻璃、反射面、昼夜、室内外、GUI 和重载 |
| SDR 编码 | GUI 白为 1，128 灰为 128/255；经过真实合成、最终输出后保持预期 |
| HDR10 编码 | 原始 160 nit GUI 白读回 160.39 nit；同帧世界最高颜色分量约 532 nit，证明世界高光没有被压回 SDR 范围 |
| 最终控制回归 | `hdr-display-controls-display-20261031-222629`：世界白 180、GUI 白 120、峰值 800 均实际生效；GUI 白读回 120.47 nit，灰为 25.78 nit（预期 25.90）；没有重建世界管线，HDR→SDR→HDR 往返通过 |
| 人工与性能 | 使用者确认 HDR 1000 显示器及系统 HDR 已开启，最终观感由使用者验收；未做性能测试，未证明本轮 HDR 生成帧的动态视觉 |

开发中的 `2110930c…` 虽完成整套场景，额外核对请求值时发现亮度更新仍采用旧参数，故没有投放。原因是表面检查只在窗口事件或 SUBOPTIMAL 后发生。最终补充半秒检查，并区分格式变化与亮度数据变化；亮度检查不执行重建或额外跳过帧率限制。最终候选针对这项变化运行三场景回归，不重复与其无关的整套矩阵。

相关结果位于 `validations/conformance-takeover/result/world-materials-134/` 的 `hdr-display-sdr-display-20261031-220129`、`hdr-display-advanced-display-20261031-220400` 和上述最终控制目录。中间失败控制记录为 `hdr-display-final-display-20261031-221357`，其旧的自动通过标记不能代替新增的请求值核对；实际缺口与修正按本节说明。

构建与输入身份记录在 `D:/Workspaces/Artifacts/Radiance/hdr-display-20261031/`，最终包 Native DLL 的 CodeView 与保留 PDB 一致。按 X-12 回收旧中间包、重复发行包及已结束验证的可再生数据，源码、结论、关键截图、输入、当前性能原始记录与增量编译缓存保留，退役清单亦在该目录。

本机实际使用 HDR10，系统上报峰值 1015 nit；这不是仪器测量。scRGB 路径及缺失能力的回退有自动策略测试，尚无另一设备的运行证据。Windows HDR 开关变化、跨显示器移动、HDR 下实际 FG 生成帧与最终连续视觉仍待体验；普通 PNG 只是 SDR 预览。参考白与峰值不等于重新校准显示器，不以内部浮点、编码正确或测试通过宣称物理 HDR 目视验收完成。
