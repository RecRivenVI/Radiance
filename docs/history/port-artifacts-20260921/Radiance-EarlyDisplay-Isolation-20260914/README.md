# Early Display 临时隔离与客户端启动

- 17 个 Early Display Java/native/CMake 源文件已复制到 before/，SHA-256 逐项验证后从源码工作树移入回收站。
- before/ 另保存 build.gradle、Mixin 配置及中英文 README，before-manifest.json 共 21 个文件。
- 移除两个 Early Display Mixin 注册、独立 bridge sourceSet/dependency/check、缺失补丁辅助类造成的启动阻断任务。
- 测试实例 run/client/config/fml.toml 设置 earlyWindowControl=false；FML 日志确认 splash screen disabled。
- NVIDIA DLL 从本次构建的相邻 MCVR/bin 复制到 run/client/radiance，没有覆盖不同文件。
- 隔离后完整 build 和 26 项 Java 测试通过，JAR 不含已隔离的 Early Display 类。
- 首次启动发现 SnakeYAML 不在 ModDev 开发运行类路径中；已在 runs.configureEach 中补入同一已声明版本 2.6，然后重试成功。
- 客户端 PID 78260，JDK 21；窗口 Minecraft NeoForge* 1.21.1 - Singleplayer 有响应，日志持续记录渲染帧。保持运行，不代替用户确认视觉效果。
- 未提交、未推送；MCVR 源码本轮未修改。此前拆解报告与补丁仍是隔离前快照。

恢复时可按 before-manifest.json 的原相对路径找回隔离源文件；build/Mixin 接入的恢复应逐项审阅，避免覆盖本轮补齐的 SnakeYAML 运行依赖。FML 配置是实例设置，独立控制。
