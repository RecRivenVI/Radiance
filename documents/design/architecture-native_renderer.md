# 原生渲染组件

`native_renderer` 是原 MCVR 的源码组件，保留渲染与着色器逻辑，仅改变构建位置和依赖路径。

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
