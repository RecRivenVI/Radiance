<p align="center">
  <img src="./.assets/logo.png" alt="Radiance Logo" width="200">
</p>

<h1 align="center">
  Radiance
  <br>

  <a href="https://github.com/RecRivenVI/Radiance">
    <img src="https://img.shields.io/github/stars/RecRivenVI/Radiance?style=flat&logo=github" alt="GitHub Stars">
  </a>

  <a href="https://discord.gg/y4Uzf6acqk">
    <img src="https://img.shields.io/badge/Discord-Join-5865F2?style=flat&logo=discord&logoColor=white" alt="Discord">
  </a>

  <a href="https://modrinth.com/mod/radiance-mod-windows">
    <img src="https://img.shields.io/badge/Modrinth-Windows-5CA424?style=flat&logo=modrinth&logoColor=white" alt="Modrinth Windows">
  </a>

  <a href="https://modrinth.com/mod/radiance-mod-linux">
    <img src="https://img.shields.io/badge/Modrinth-Linux-5CA424?style=flat&logo=modrinth&logoColor=white" alt="Modrinth Linux">
  </a>

  <a href="https://www.curseforge.com/minecraft/mc-mods/radiance">
  <img src="https://img.shields.io/badge/CurseForge-Download-F16436?style=flat&logo=curseforge&logoColor=white" alt="CurseForge">
  </a>

  <a href="https://github.com/RecRivenVI/Radiance/blob/develop/LICENCE">
    <img src="https://img.shields.io/github/license/RecRivenVI/Radiance?style=flat" alt="License">
  </a>

  <a href="https://github.com/RecRivenVI/Radiance/releases">
    <img src="https://img.shields.io/github/v/release/RecRivenVI/Radiance?include_prereleases&label=Release&style=flat&logo=github&v=1" alt="GitHub Release">
  </a>

  <a href="https://www.youtube.com/@RadianceMod">
    <img src="https://img.shields.io/badge/YouTube-Subscribe-FF0000?style=flat&logo=youtube&logoColor=white" alt="YouTube">
  </a>

  <a href="https://b23.tv/bN7CTv1">
    <img src="https://img.shields.io/badge/Bilibili-Follow-00A1D6?style=flat&logo=bilibili&logoColor=white" alt="Bilibili">
  </a>

  <br><br>
</h1>

| English | [简体中文](README-CN.md) |

> This is the Java part of the Radiance mod. For C++ part, please refer to [Minecraft Vulkan Renderer (MCVR)](https://github.com/RecRivenVI/MCVR)

# Radiance

[Radiance](https://www.minecraft-radiance.com/) is a Minecraft 1.21.1 NeoForge mod that completely replaces the vanilla OpenGL renderer with our high-performance Vulkan C++ renderer, which supports hardware-accelerated ray tracing.
Due to the variety of C++ usage in the modern industrial rendering pipeline, a seamless integration of a modern industrial rendering module (such as DLSS and FSR) into our Vulkan C++ renderer is thus possible.

[Showcase Video (Youtube)](https://www.youtube.com/watch?v=jGIQffPM1Wg)

<img src="https://image.puxuan.cc/PicGo/corridor.png"/>

# PBR Texture Packs

Starting from 0.1.4, no pre-processing  is necessary. Load directly!

Although there are already internal emission textures, it is still recommended to use a PBR texture pack for better experience.

# Installation Guide

We assume that the Minecraft base is installed in `.minecraft` folder. If the installation folder is different, please replace the corresponding part yourself.

## Download and install `.jar` as usual

Install NeoForge 21.1.251 for Minecraft 1.21.1, then place the mod jar in the `.minecraft/mods` folder.

## (Windows Fix) Adjust JDK's runtime libraries

Due to a known [MSVC issue](https://stackoverflow.com/questions/78598141/first-stdmutexlock-crashes-in-application-built-with-latest-visual-studio), some libraries from JDK itself may cause a crash. 

In those circumstances, one possible solution could be:

First, try to manipulate (rename, delete, etc.) the `msvcp140.dll`, `vcruntime140.dll` and `vcruntime140_1.dll` in the JDK's bin folder (`${PATH_TO_JDK}/bin`) so that these files do not exist in that folder. 
This step aims to remove the JDK's dependency on those libraries.

Then, install the [latest Microsoft Visual C++ Redistributable](https://learn.microsoft.com/en-us/cpp/windows/latest-supported-vc-redist?view=msvc-170), with version `Latest supported v14 (for Visual Studio 2017–2026)`.
This step let JDK depends on the latest system libraries.

## DLSS and Streamline runtime libraries

The Windows distributable includes the signed NVIDIA DLSS 310.9.1 runtime and Streamline 2.14.1,
together with their license and third-party notices. Radiance verifies and extracts the native
runtime into `.minecraft/.radiance/runtime` automatically. Do not download DLLs separately or
create dummy files.

The video settings expose DLSS Super Resolution, Ray Reconstruction, 2x Frame Generation, global
model selection, and NVIDIA Reflex when the installed GPU and driver support them. NRD, FidelityFX
Super Resolution, and XeSS remain available through the applicable rendering-pipeline presets.

# Build

Install JDK 21, CMake 3.25 or later, and a supported C++ toolchain. Clone MCVR next to Radiance:

```
git clone https://github.com/RecRivenVI/MCVR.git ../MCVR
```

Build the Java mod, native renderer, shaders, and packaged runtime with:

```
./gradlew test
./gradlew prepareRuntime build
```

On Windows, `prepareRuntime` selects an installed Visual Studio x64 generator. The complete
FidelityFX-plus-NRD build does not support Ninja. To prepare and start the packaged-client test
instance, run `./gradlew runPackagedClient`.

Use `-Pmcvr.configuration=RelWithDebInfo` for a native build with debugging symbols (default:
`Release`). The optional Audit collector shares this setting and the `mcvr.root` /
`mcvr.cmakeGenerator` overrides with the main build.

The `mavenJava` publication is a developer-facing GAME-layer artifact named `Radiance-game`. It is
intended for compile-time integration and is not installable by itself. User installations must use
the single JAR produced by `distributedJar`, which includes the SERVICE bootstrap, nested GAME JAR,
native renderer, shaders, and optional runtime libraries.

Optional diagnostics are maintained as the separate [Radiance Audit module](Modules/RadianceAudit/README.md).
Build it with `./gradlew :radiance-audit:check :radiance-audit:jar`; it is not bundled into Radiance.

# TODO List

- [ ] port to more versions and mod loaders (WIP, first priority)
- [x] XESS support
- [x] DLSS Frame Generation
- [ ] HDR

And more...

# Credits

This project uses Vulkan technology. Please refer to [this page](https://www.vulkan.org/) for more information.

This project uses Nvidia's DLSS (Deep Learning Super Sampling) technology. Please refer to [this page](https://www.nvidia.com/en-us/geforce/technologies/dlss/) and [this page](https://github.com/NVIDIA/DLSS) for more information. 

This project uses FSR3. Please refer to [this page](https://gpuopen.com/fidelityfx-super-resolution-3/) for more information.

This project uses XeSS SR. Please refer to [this page](https://www.intel.com/content/www/us/en/developer/articles/technical/xess-sr-developer-guide.html) for more information.

Special thanks to all contributors of open-source libraries used in this project, including [NRD](https://github.com/NVIDIA-RTX/NRD), [GLFW](https://github.com/glfw/glfw), [GLM](https://github.com/icaven/glm), [STB Image](https://github.com/nothings/stb) and [VMA](https://github.com/GPUOpen-LibrariesAndSDKs/VulkanMemoryAllocator). If any are not credited and should be, please inform the author and credit will be applied where required.

# Disclaimer

* This is a community-made third-party mod and is **not affiliated with, authorized, sponsored, endorsed by, or otherwise officially connected to Mojang Studios or Microsoft**. A commonly used wording on mod platforms is: **"NOT AN OFFICIAL MINECRAFT SERVICE. NOT APPROVED BY OR ASSOCIATED WITH MOJANG OR MICROSOFT."**
* **Minecraft** and related names, logos, and assets are trademarks and/or intellectual property of Mojang Studios and/or Microsoft.
* This project is also **not affiliated with, sponsored by, or endorsed by NVIDIA**. **NVIDIA / GeForce / RTX / DLSS** are trademarks of NVIDIA Corporation and remain the property of their respective owners.
* This mod is provided **"AS IS"** without warranties. You assume all risks arising from its use (including, but not limited to, crashes, visual issues, data loss, or incompatibilities with other mods). Please back up your worlds and configs before installation.
