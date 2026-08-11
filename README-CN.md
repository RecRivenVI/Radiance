| [English](README.md) | 简体中文 |

> 这里是Radiance mod的Java部分。C++部分请访问[Minecraft Vulkan Renderer (MCVR)](https://github.com/RecRivenVI/MCVR)

# Radiance

Radiance是一个面向Minecraft 1.21.1 NeoForge的模组，旨在将原版的OpenGL渲染器完全替换成我们的高性能Vulkan C++渲染器，并且支持硬件加速光线追踪。
由于现代工业界广泛在渲染管线中使用C++，所以我们的Vulkan C++渲染器能够将一个现代工业级的渲染模块（例如DLSS和FSR）无缝集成进来。

[演示视频 (B站)](https://www.bilibili.com/video/BV1NevXBCEPg/)

<img src="https://image.puxuan.cc/PicGo/corridor.png"/>

# 关于PBR材质包

从0.1.4开始，不需要任何预处理了，直接加载！

尽管有内置的发光贴图，依旧推荐使用一个PBR材质包来获得更好的体验。

# 安装指南

我们默认Minecraft本体安装在`.minecraft`文件夹下。如若安装路径不同，请自行替代。

## 正常下载和安装模组`.jar`文件

为Minecraft 1.21.1安装NeoForge 21.1.251，然后将模组jar文件放入`.minecraft/mods`文件夹。

## (Windows修复) 调整JDK的运行库

因为一个已知的[MSVC问题](https://stackoverflow.com/questions/78598141/first-stdmutexlock-crashes-in-application-built-with-latest-visual-studio)，有些JDK自带的运行库会导致模组报错，游戏崩溃。

这种情况下，一种可能的解决方案为：

首先，尝试改动（重命名，删除，等）JDK bin目录（`${PATH_TO_JDK}/bin`）下的`msvcp140.dll`，`vcruntime140.dll`和`vcruntime140_1.dll`文件。最终要让这些文件不存在在JDK bin目录下。这一步的目的是移除JDK对于它自带的运行库的依赖。

然后，安装[最新的Microsoft Visual C++ Redistributable](https://learn.microsoft.com/en-us/cpp/windows/latest-supported-vc-redist?view=msvc-170)，使用`Latest supported v14 (for Visual Studio 2017–2026)`版本。这一步的目的是让JDK重新使用最新的系统运行库。

## DLSS与Streamline运行库

Windows发行包内含签名有效的NVIDIA DLSS 310.9.1运行库和Streamline 2.14.1，以及对应的
许可证与第三方声明。Radiance会校验并自动将原生运行库解压至
`.minecraft/.radiance/runtime`，无需另外下载DLL，也不要创建同名空文件。

当显卡和驱动支持时，视频设置可选择DLSS超分、光线重建、2x帧生成、各功能的全局模型和
NVIDIA Reflex。NRD、FidelityFX Super Resolution和XeSS仍可通过相应渲染管线预设使用。

# 构建

安装JDK 21、CMake 3.25或更高版本以及受支持的C++工具链。将MCVR克隆到Radiance相邻目录：

```
git clone https://github.com/RecRivenVI/MCVR.git ../MCVR
```

使用以下命令构建Java模组、原生渲染器、着色器和打包运行库：

```
./gradlew test
./gradlew prepareRuntime build
```

在Windows上，`prepareRuntime`会自动选择已安装的Visual Studio x64生成器；同时启用
FidelityFX与NRD的完整构建不支持Ninja。运行`./gradlew runPackagedClient`可以准备并
启动打包客户端测试实例。

使用`-Pmcvr.configuration=RelWithDebInfo`可构建带调试符号的原生库（默认为`Release`）。
可选的Audit采集器与主构建共用此设置，以及`mcvr.root`和`mcvr.cmakeGenerator`覆盖项。

独立诊断模组 [Radiance Audit](Modules/RadianceAudit/README.md) 的源码在本仓库维护。
使用 `./gradlew :radiance-audit:check :radiance-audit:jar` 构建；它不会内嵌进 Radiance 主体。

# Todo列表

- [ ] 移植到更多版本和mod加载器（WIP，最高优先级)
- [x] XESS支持
- [x] DLSS帧生成
- [ ] HDR

以及更多...

# 致谢

这个项目使用了 Vulkan。获取更多信息请访问[这个页面](https://www.vulkan.org/)。

这个项目同时也使用了 Nvidia 的 DLSS (Deep Learning Super Sampling) 技术。获取更多信息请访问[这个](https://www.nvidia.com/en-us/geforce/technologies/dlss/)和[这个页面](https://github.com/NVIDIA/DLSS)。

这个项目也使用了FSR3。更多信息请访问[这个页面](https://gpuopen.com/fidelityfx-super-resolution-3/)。

这个项目也使用了XeSS SR。更多信息请访问[这个页面](https://www.intel.com/content/www/us/en/developer/articles/technical/xess-sr-developer-guide.html)。

特别感谢所有这个项目使用的开源库的制作者，包括[NRD](https://github.com/NVIDIA-RTX/NRD)、[GLFW](https://github.com/glfw/glfw)、[GLM](https://github.com/icaven/glm)、[STB Image](https://github.com/nothings/stb)和[VMA](https://github.com/GPUOpen-LibrariesAndSDKs/VulkanMemoryAllocator)。
如果有应该致谢但是没有被提及的，请通知本项目作者。我们会把致谢添加在需要添加的位置。

# 免责声明

* 本项目为社区开发的第三方模组（Mod），**不隶属于、亦未获得 Mojang Studios / Microsoft 的授权附属、赞助或支持**。参考常见模组分发平台的表述：“**NOT AN OFFICIAL MINECRAFT SERVICE. NOT APPROVED BY OR ASSOCIATED WITH MOJANG OR MICROSOFT.**”
* **Minecraft** 以及相关名称、标识与资源为 Mojang Studios / Microsoft 的商标或知识产权。
* 本项目同样**不隶属于、亦未获得 NVIDIA 的授权附属、赞助或支持**；**NVIDIA / GeForce / RTX / DLSS** 等名称与标识为 NVIDIA Corporation 的商标，归其各自权利人所有。
* 本项目按“**AS IS**”提供。使用本模组产生的任何风险（包括但不限于崩溃、画面问题、数据丢失、与其他模组冲突等）由使用者自行承担；请在安装前备份存档与配置。

