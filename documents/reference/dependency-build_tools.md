# 构建工具依赖

Java 与 Gradle 插件的版本由[版本目录](../../gradle/libs.versions.toml)固定；Target 的 Java 版本由 `target.properties` 定义。

## 原生与盘点工具

| 工具 | 唯一版本来源 | 用途 |
| --- | --- | --- |
| CI Python | 版本目录的 `python` | 执行盘点工具的行为测试 |
| PyYAML | [requirements.txt](../../components/render_diagnostics/inventory/requirements.txt) | 读取盘点输入中的 YAML 模块声明 |
| CI Vulkan SDK | 版本目录的 `vulkan-sdk` | 提供 Vulkan 头文件、链接库、shaderc 与 glslangValidator |

Windows CI 使用 LunarG [官方安装器的 copy_only 模式](https://vulkan.lunarg.com/doc/view/1.4.341.1/windows/getting_started.html) 将完整 SDK 放入运行器临时目录，不注册系统图层或修改系统环境。安装后检查 Vulkan 与 shaderc 的头文件、链接库和着色器编译器，再导出当前作业的环境变量。工具下载不进入版本控制或发行包；公开运行库许可状态不因此改变。

本地环境按[开发环境](../development/setup-environment.md)准备，CI 使用 `checkRepository`，本地还需验证带本地输入的 `check` 与 `verifyRelease`。
