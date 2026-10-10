# 构建与本地验证

## 适用范围

构建 1.21.1 NeoForge Radiance。本仓库同时包含原生渲染组件，外部 MCVR 不再是构建依赖。

## 步骤

1. 安装 Java 25（Gradle），Java 21（Target）、CMake、Visual Studio C++、完整 Vulkan SDK 与 Python，具体要求见[开发环境](setup-environment.md)。
   `clangFormatCheck` 在任务执行时先从 PATH 查找 clang-format；Windows 上找不到时，调用 `${env:ProgramFiles(x86)}\Microsoft Visual Studio\Installer\vswhere.exe -latest -products * -find VC\Tools\Llvm\x64\bin\clang-format.exe`，使用最新 Visual Studio 安装中的工具。两处都找不到时任务失败；安装 Visual Studio 的“C++ Clang 工具”组件或把 clang-format 加入 PATH 后重试。工具路径不在配置阶段固定，支持该任务的配置缓存复用。
   Windows 下 CMake 调用暂时映射仓库到 `R:`，规避第三方着色器工具的路径长度限制，每次调用结束即解除映射。若盘符已占用，传入 `-Pnative.cmakeDrive=<未使用的大写盘符>`；产物仍在组件 `build/` 内。
2. 初始化固定子模块。

   ```powershell
   git submodule update --init --recursive
   python -m pip install -r components/render_diagnostics/inventory/requirements.txt
   ```

3. 按 `inputs.toml` 下载固定兼容发行包，在 ignored `local.toml` 的 `[inputs]` 指定本机文件；窗口尺寸也写入 `local.toml`，不擅自接受 EULA。
4. 执行格式、测试和发行包结构校验。

   ```powershell
   .\gradlew.bat spotlessApply
   .\gradlew.bat check
   .\gradlew.bat verifyRelease
   .\gradlew.bat :native_renderer:gpuTest
   ```

5. 启动日常客户端或打包客户端；只用仓库内实例，画面由使用者验收。

   ```powershell
   .\gradlew.bat :version:1.21.1-neoforge:runClient
   .\gradlew.bat :version:1.21.1-neoforge:runPackagedClient
   ```

## 验收

发行 JAR 位于 `versions/1.21.1-neoforge/build/libs/`；GAME JAR 位于 Target 的 `build/intermediates/`。目标是 `check`、`verifyRelease` 与显式 GPU 测试成功，标题界面日志包含 Radiance 且无崩溃报告。

二进制云噪声资源由 `.gitattributes` 的 `*.raw binary` 登记，合规检查跳过其文本编码检查。具体执行证据见[当前状态](../project/status-current.md)。Windows CI 安装固定版本的 Python、盘点工具依赖与完整 Vulkan SDK 后执行 `checkRepository`。SDK 位于托管运行器的临时目录；CI 只上传合规报告，不上传原生运行库或发行包。本地验证与实际 GitHub Actions 结果分别记录。
