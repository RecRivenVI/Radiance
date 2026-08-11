# Simulated/Aeronautics diagram shader consumers

日期：2026-09-15。固定 Simulated/Aeronautics 1.3.2、Veil 4.3.2。仓库 `D:\Workspaces\Repositories\GitHub\RecRivenVI\Radiance`。旧 Artifact 只读，本轮证据在 `113310-handoff/Evidence/simulated-shader-consumers`。

## 检查点

**源码承接与 Java 编译 PASS；22 项定向测试 PASS；六个真实固定消费者的 12 个 Vulkan SPIR-V stage 离线编译 PASS。未执行游戏内 Mixin、diagram draw 或实际 uniform/texture 上传。**

原 SimulatedVeilRenderSystemMixins 在 diagram 中截断所有 Veil setShader 请求（除 blit_screen_effect），强制 vanilla shader 并返回 null。它丢失原 laser taper、spring stress 叠色、lens/diode atlas 运算；Aero burner 在 `shader != null` 中设置 FlameRenderTime/Intensity/Palette 并绘制，返回 null 会整个吞掉真实火焰绘制。

本轮改为 setShader **RETURN** 的定向检查。原 Veil ShaderManager 查找、RenderSystem 选择 ShaderProgram wrapper 与返回值完整保留；固定 diagram consumer 返回 null 时抛出含 shader 名的明确错误。没有替代 program、返回伪成功或吞掉绘制。其他 shader 沿原 Veil 语义执行。既有 wrapper 的 IExternalShaderProgram、VeilShaderBridge metadata/uniform writer、ShaderRegistry 和 RasterDrawBridge 是实际 Vulkan 承接路径。

## 固定消费者与输入契约

| Shader | 实际消费者 | 格式/承接 |
|---|---|---|
| simulated:laser/laser | SimRenderTypes.LASER，QUADS | POSITION_TEX_COLOR；原 vsh 使用 UV 名，保留 location 1 与原源码 |
| simulated:laser_pointer/lens | SimRenderTypes.LENS，QUADS | 实际 BLOCK 32-byte buffer；shader 省略不用的 Normal input，仍匹配 BLOCK stride |
| simulated:redstone_accumulator/diode | RedstoneAccumulatorRenderer，QUADS | 实际 BLOCK buffer；同样省略 Normal input，保留 litFrac/双 atlas texel 运算 |
| simulated:rope/rope | SimRenderTypes.ROPE，QUADS | BLOCK；保留 NormalMat、block_brightness、lightmap 与原 rope texture |
| simulated:spring/spring | SpringRenderer→SimRenderTypes.SPRING，QUADS | SPRING_FORMAT 的 Stress 元素与 BLOCK Color 字节相同；真实 vsh 使用 Color，保留原 stress RGBA 和 `<0.1` discard |
| aeronautics:burner_flame | HotAirBurnerRenderer.renderSafe/renderFlame，QUADS | POSITION_TEX；原 renderer 继续计算/设置真实 FlameRenderTime、Intensity、Palette 并调用 BufferUploader.drawWithShader |

VeilShaderBridge 原只按 shader input names 匹配默认 VertexFormat：laser 的 UV 无法匹配，lens/diode 的短 input 列表会选择 28-byte POSITION_COLOR_TEX_LIGHTMAP，与实际 32-byte BLOCK buffer 不符。新增 SimulatedShaderConsumers 仅对表中精确 namespace/path、location/type/name 列表承接真实 format。known shader 签名改变时抛 ShaderException；未按子串猜测 stride，未改 shader 运算或泛化其他模组。

SimulatedDiagramCompatibility 去掉 DiagramSpringVertexConsumer 强制 white/alpha255 的包装，直接使用原 BufferSource。SpringRenderer 计算的 stress RGB/alpha 因而到达真实 spring shader。原 diagram target/post palette 路径保持原有实现，本轮未修改 native diagram post。

## Uniform 真实值

固定消费者的普通 ModelViewMat/ProjMat/fog/ColorModulator 等沿既有 Veil default writer；本轮补足被 wrapper 接替绕过的 lighting defaults：

- NormalMat 从本次 draw 的真实 modelView.normal(Matrix3f) 计算，与原 Veil PipelineShaderInstanceMixin 使用的第一矩阵参数一致。
- Light0_Direction/Light1_Direction 来自 VeilRenderSystem tracked lights；原 PipelineRenderSystemMixin 从 RenderSystem.setShaderLights 保存真实 Lighting 状态。
- VeilBlockFaceBrightness 的六个元素来自当前 ClientLevel.getShade(direction,true)，按 direction.get3DDataValue 排列，写到真实 ShaderUniform float array。没有 level 时沿原 Veil 行为跳过，不构造替代 shade。

调用范围仅为六个固定消费者。原 renderer 自有参数、Veil definition textures（lens/diode TextureSheet、burner FirePalette）以及 Sampler0/1/2 的实际 texture writer 保持原路径。本轮没有以 CPU 转发测试宣称 GPU 数据已验证。

## 修改文件与注册

五个 Java 文件，完整 SHA256 在 source-freeze.sha256：

- src/main/java/com/radiance/mixins/compatibility/simulated/SimulatedVeilRenderSystemMixins.java
- src/main/java/com/radiance/compatibility/simulated/SimulatedShaderConsumers.java（新增）
- src/main/java/com/radiance/compatibility/simulated/SimulatedDiagramCompatibility.java
- src/main/java/com/radiance/compatibility/veil/VeilShaderBridge.java
- src/test/java/com/radiance/compatibility/simulated/SimulatedShaderConsumersTest.java（新增）

沿已有 SimulatedVeilRenderSystemMixins 注册，不需新增 Mixin/config 项。没有修改 WorldRenderer/WorldRaster/ShaderRegistry/Translator/shared JSON/native。所有权按根授权扩展到必要 VeilShaderBridge 和 DiagramCompatibility；未回退并行改动。

## 验证证据

最终命令：`gradlew.bat compileJava compileTestJava test --tests '*SimulatedShaderConsumersTest' --tests '*VeilShaderBridgeTest' --tests '*ShaderTranslatorTest' --tests '*VeilMixinCompatibilityTest' --no-daemon`。

session 79989：**BUILD SUCCESSFUL in 19s**，7 actionable tasks（4 executed/3 up-to-date）。XML：新增 consumer 4、Veil bridge 11、Translator 3、Veil Mixin 4，共22，0 failures/errors/skipped。git diff --check exit 0；共享 config 的既有 CRLF 警告仍在。

新增测试直接 ZipFile 读取本仓固定 compile dependency 的 Simulated/Aero/Veil JAR，展开其实际 packaged includes，再通过真实 VeilShaderBridge.analyze 与 ShaderTranslator.translateExternal，使用 `glslangValidator -V --target-env vulkan1.2 -S vert/frag` 编译六组12 stage。所有模块非空；shader-artifacts.sha256 记录生成源码/SPIR-V 的大小与 SHA256，24 个实际文件已复制。该离线 fixture include 展开不代替生产原 Veil compiler/preprocessor 或 resource reload。

反例证明 fixed lens 改变 input 类型会明确失败、其他 namespace 不被覆盖、实际 stride32；program selection 返回原对象，已知 null 程序明确报错；非单位 modelView、两组非默认 light direction、六个 shade 的数值转发均按输入保存。

真实 JAR SHA256：Simulated `fdf9d250996a084b52fced3a5b0089e879a5cac05dc7bcf295ac1f4ebe5e2bff`；Aero `97fbf1e27f38674145b8521b140613dc8258bea7efca456dc2686e22bd683933`。固定消费者 javap `-p -c -s` 输出已保存，证明原 shaderState、BLOCK/Stress format、burner program/uniform/draw call。

首轮 Java 编译成功但2资源测试因 Sim/Aero 不在 testRuntime classpath 失败，已改为固定 ZipFile 读取并通过最终复跑。中间一轮被根并行 ParticleTypeCapture 的 PARTICLE_SHADER 编译符号错误阻断；根修复后最终联合编译通过，未修改该文件。

## 剩余边界

SimRenderTypes 仍引用 simulated:staff_overlay/staff_overlay，但固定 Simulated 1.3.2 JAR 没有相应 vsh/fsh/json。没有伪造资源；若 diagram 实际选择它则明确报 failed program。此上游资源缺失不能记为本轮支持 PASS。

实际 Mixin RETURN 应用、原 Veil 资源处理与 dynamic variant、diagram/BE 顶点提交、stress 画面、texture/lightmap/overlay/fog 上传、burner native draw 仍 pending。Aero balloon heating 的 world event/FBO、Levitite world/diagram consumer 与原 diagram outline post 的其他承接不计入本轮新增验证。

未启动游戏/UI，未 stage/commit/push，未删除文件。Gradle 窗口已释放。
