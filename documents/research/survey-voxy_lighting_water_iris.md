# Voxy 光源、水面与 Iris 数据合同

调查日期：2026-10-18。使用者要求在接入设计中重点确认光源、水面，并一并阅读 Iris 路径。本次只读 Voxy 当前工作区；使用者已确认它与登记的 JAR 一致，并接受其有意舍弃的 PBR、动态纹理和精度。结论为源码行为与技术方案，不是游戏或兼容验证。

## 问题

区分 Voxy 代理仍保留的信息、其普通光栅后端的最终效果，以及 Radiance 世界 PT 所需的明确映射。不能把已照亮的代理变成光源，也不能把所有透明代理当成水或假定 Voxy 本身已有折射。

## 方法

读取只读仓库 `D:/Workspaces/Repositories/GitHub/RecRivenVI/voxy/` 的 `ModelFactory`、`ModelQueries`、`RenderDataFactory`、软件流体烘焙、`Mipper`、普通与 Iris 管线、shader patch 入口及 `quad_util.glsl`、`quads.frag`、`lighting.glsl`。对应 HEAD、现成构建身份与工作树边界见[后端设计](../design/architecture-voxy_pt_backend.md#核查身份与测试输入)。

关键来源文件相对上述 Voxy 仓库：

| 路径 | 关键符号 |
| --- | --- |
| `src/main/java/me/cortex/voxy/client/core/model/ModelFactory.java` | `getBlockLightEmission`、`processTextureBakeResult`、`getTintSources` |
| `src/main/java/me/cortex/voxy/client/core/model/ModelQueries.java` | `lightEmission`、`isFluid`、`containsFluid` |
| `src/main/java/me/cortex/voxy/client/core/rendering/building/RenderDataFactory.java` | `applyQuadLight`、各轴 FluidInner／FluidOuter 生成 |
| `src/main/java/me/cortex/voxy/client/core/model/bakery/SoftwareModelTextureBakery.java` | `bakeFluidState`、`renderToOutput` |
| `src/main/java/me/cortex/voxy/client/core/rendering/section/backend/mdic/MDICSectionRenderer.java` | `renderTranslucent`、shader patch 编译 |
| `src/main/java/me/cortex/voxy/common/world/other/Mipper.java` | `mip` |
| `src/main/java/me/cortex/voxy/client/core/IrisVoxyRenderPipeline.java` | `setupExtraModelBakeryData`、`patchOpaqueShader`、`patchTranslucentShader` |
| `src/main/java/me/cortex/voxy/client/iris/IrisShaderPatch.java` | `makePatch`、`createBlendSetup` |
| `src/main/resources/assets/voxy/shaders/lod/gl46/quads.frag` | `computeColour`、`VoxyFragmentParameters`、`voxy_emitFragment` |
| `src/main/resources/assets/voxy/shaders/lod/quad_util.glsl` | `makeRemainingAttributes` |

## 发现

### 光照值与自身发光是两种数据

`ModelFactory.getBlockLightEmission` 在来源 `BlockState.emissiveRendering` 为真时取 15，否则取 `getLightEmission` 并限制为 0–15。值写入 CPU 模型 metadata 的第 55–58 位，`ModelQueries.lightEmission` 读取它。该来源值没有作为独立 emission 字段写入现有 64 字节 GPU `BlockModel`。

网格生成的 `applyQuadLight` 将面的 block light 提升为原值与自身 emission 的最大值；天空光仍独立存在。最终 8 字节面记录中的 light 已经可能包含环境／邻格照明与自身亮度，不能从它的高亮反推这是发光几何。

普通 shader 使用 `lighting.glsl` 采样 Minecraft lightmap，再在 `quad_util.glsl` 应用来源方向明暗，`quads.frag.computeColour` 将这些照明与烘焙颜色和 tint 合成。此路径没有 Radiance 式的发光面光源采样或世界 PT，不能直接把最终 RGB 当 albedo，也不能把 block light 为 15 的墙面登记成光源。

`Mipper.mip` 对有实体素按来源不透明度与固定次序选择代表项；全部为空时才分通道平均光照。它不是原始发光体数量或总光通量的聚合表。按使用者接受的 LOD 语义保留代表选择，不逆向恢复被舍弃的远处细节，也不声称代理发光自动与原始全精度世界能量等价。

### 水和含水方块有专门网格，但普通后端没有折射

`SoftwareModelTextureBakery` 使用 Minecraft `LiquidBlockRenderer` 生成流体输入，再作六方向软件投影；这里是合成的邻域与 atlas 快照，不是每帧恢复远处完整流体表面。流体 tint 通过 `IClientFluidTypeExtensions.getTintColor` 获取。

`ModelFactory` 区分 `isFluid` 与 `containsFluid`，建立含水方块到独立流体模型的 LUT。`RenderDataFactory` 各轴有独立 FluidInner／FluidOuter 处理，必要时把含水方块的面切换到流体模型，并按邻区和模型规则消除内部面；因此不能把含水内容只当普通固体。

普通透明绘制使用 `SRC_ALPHA`／`ONE_MINUS_SRC_ALPHA`，透明通道关闭 GL 背面剔除；片段保留采样 alpha，零 alpha 丢弃。该路径没有内建的物理 IOR、介质栈或折射追踪。其静态纹理、简化高度与缺失波动按来源 LOD 取舍保留，不新造水波或补回原始 PBR。

但是，单个 translucent 标记并不能表示“水”：玻璃与其他透明块也可能处于此组。流体类型需在模型生产时从实际 `FluidState`／来源标签取得，不能通过 texture 名或某个 Iris customId 猜测。水、熔岩与普通透明代理进入既有对应表面合同，不因为 Voxy 的 alpha 绘制就统一变成同一种折射体。

### Iris 提供分离的数据入口，最终效果由 shaderpack 决定

`RenderPipelineFactory` 在安装 Iris、具有有效 pipeline data 时选择 Iris 路径，否则回到普通路径。`VoxyMixinPlugin` 只在 Iris 与 Sodium 均存在时应用 Iris Mixins；源码入口没有被整体删除，但本轮没有安装组合、启动或证明实际运行可达。

`IrisShaderPatch.makePatch` 从 shaderpack 的 `voxy.json` 读取适配，另可读取 `voxy_opaque.glsl`、`voxy_translucent.glsl` 与 `voxy_taa.glsl`。管线提供自定义 uniforms、samplers、SSBO、opaque／translucent 输出目标和混合配置。没有对应 patch 时不能声称任意 shaderpack 自动支持完整 Voxy 材质。

`PATCHED_SHADER` 下，Voxy 不先乘普通 lightmap 与方向明暗；`VoxyFragmentParameters` 分别传递 `sampledColour`、tile、UV、face、modelId、lightMap、tint 和 customId，再由 `voxy_emitFragment` 消费。customId 来自 Iris 的 BlockState 映射，是 shaderpack 自定义身份，不是统一的“水 ID”或“发光 ID”，也没有自动包含完整 PBR。

因此具体 shaderpack 可以给水或某类代理实现自己的表现，但不能从 Voxy 桥接代码单独证明某种折射、发光或波浪已经存在。本次没有选取或分析一个实际 shaderpack，也不扩展为实现 Radiance 的整套 Iris 兼容。

## 结论

光源和水面确实需要有意适配；适配重点是保留 **Voxy 仍有的分类与未受光数据**，不是补回其主动舍弃的所有细节。

1. 从模型 metadata 导出独立的自身发光等级与来源流体分类，和投影颜色、alpha、tint 一起形成轻量 surface 输入；环境 lightmap 不作物理 emission。模型归并保持默认来源规则，若实际发现物理类别冲突再作最小区分，不为恢复完整 PBR 全面改写去重。
2. 发光代理接入 Radiance 的真实照明贡献及光源候选，模型模板与区段候选按变化更新；近远景、父子 LOD 与旧版本必须共享同一覆盖快照，避免重复发光。0–15 是来源亮度等级而非物理辐射单位，沿用现有材质适配与强度政策，不直接解释为新物理单位。
3. 水／熔岩／含水方块保留来源简化网格，接入已有的对应表面、查询与界面规则；不把任意透明块当水，不新加逐帧水面恢复。数值自命中、船内实际排除域及近远景接缝作为接入测试。
4. 借鉴 Iris 的“表面输入与照明分开”方式，用同一份压缩面及模型数据进入公共 PT 材质；不搬运其 GL 资源绑定、后置光栅和 shaderpack 程序。用户已接受的远景 LOD 损失不再列为阻塞。

这些是静态方案，尚未实施。完整接入与性能收益仍需后续分别验证，本轮不启动游戏、不测性能、不提交或推送。
