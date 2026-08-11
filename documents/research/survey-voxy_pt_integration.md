# Voxy 远景几何接入路径追踪评估

调查日期：2026-10-16。正式启动对使用者 NeoForge 1.21.1 移植版的静态评估：存在绕过最终 OpenGL 绘制、从紧凑面记录接入世界 PT 的可行方向，但当前不能直接叠装或把现有 GL 缓冲原样当作 AS 输入。未修改 Voxy、Radiance 产品或着色器，未构建、启动游戏或测性能。

## 问题

目标是复用 Voxy 的远景数据与网格生成，在变化时发布持久世界几何，避免每帧恢复 Minecraft 顶点、再走通用捕获翻译。所有被选取的远景表示应共同参与相机、反射、折射、阴影与间接光；不能只翻译主相机看见的绘制列表，或叠加光栅远景代替 PT 场景。

Voxy 本身有 LOD 与模型近似。本次“接入世界几何”不等于恢复远处的原始全部细节，也不证明其现成材质可与 Radiance 的 PBR/物理水面完全等价。远景代理参与全部查询与原始全精度模型等价是两个不同目标。

## 方法

读取 `D:/Workspaces/Repositories/GitHub/RecRivenVI/voxy` 当前工作树，HEAD `11a3799ca0561098c17aea18266381d218de9654`，版本声明 `0.2.20-beta+1.21.1-neoforge`。存在其他执行者的大量修改与未跟踪文件，全部保留，只读。未找到该仓库根目录或树内的 `AGENTS.md`；没有因此扩大写入授权。

沿 `ClientIngestion`、`VoxelIngestService`、`WorldEngine`、模型烘焙、`RenderDataFactory`、`BuiltSection`、`AsyncNodeManager`、GL 几何存储和 shader 解码追踪；同时核对 Radiance 当前外部区段、世界提交和 native AS 边界。读取范围文件的 SHA-256 与工作树状态见 `D:/Workspaces/Artifacts/Radiance/line-voxy-survey-20261016/read-snapshot.json`，不是可构建的完整冻结快照。

Vulkan 资源约束另核对 [Khronos AS 规范](https://docs.vulkan.org/spec/latest/chapters/accelstructures.html)：三角形构建必须获得合法位置/索引描述，构建输入保留至相应 GPU 工作完成，TLAS 使用期间被引用 BLAS 仍须有效。压缩面记录不能直接冒充这些输入。

## 发现

### 实际数据链与最早有用的接口

```text
客户端变化 / 导入
→ ClientIngestion / VoxelIngestService
→ WorldEngine / WorldSection 的 32³ 体素数据与父级 LOD
→ 模型状态到 Voxy 模型表
→ RenderDataFactory.generateMesh
→ BuiltSection（CPU 内存中的压缩面、分组、边界与层级身份）
→ RenderGenerationService.resultConsumer
→ AsyncNodeManager / NodeManager
→ GL geometry store + 层级遮挡遍历
→ OpenGL 间接绘制
```

最适合评估的分叉是 **`BuiltSection` 产生之后、交给现有 node/GL 上传链之前**。生成服务已存在结果 consumer，但当前是单消费者所有权交接，不是可任意新增第二个消费者的广播接口。

| 数据 | 当前源码事实 | 对接含义 |
| --- | --- | --- |
| `BuiltSection.position` | 编码层级与区段位置 | 与世界、维度、资源代际组合成稳定身份，不拿 GL draw ID 当世界身份 |
| `geometryBuffer` | CPU `MemoryBuffer`，每面 8 字节 | 可以按变化上传原记录；不必逐帧展开为 Minecraft 顶点 |
| `offsets` | 八组：半透明、双面及六个方向组 | 组别本身不是完整的 Radiance 材质或命中合同，必须显式映射 |
| 模型与面记录 | 面朝向、区段内位置、尺寸、模型 ID、biome/light 信息 | 还依赖模型表里的面尺寸与深度；仅解包 record 不足以重建最终面 |
| `childExistence`、`aabb`、`occupancy` | 子节点存在性、包围与可选占用数据 | 可辅助驻留/LOD；不得当作原始完整网格 |
| 释放 | `BuiltSection.free` 释放直接内存；异步队列关闭时也会 free | 必须明确定义转移、复制或引用计数；记录地址后异步 JNI 消费会有悬空风险 |

`quad_util.glsl::setupQuad` 还使用 `BlockModel.faceData` 解码面缩进与边界，再应用 `1 << lodLevel` 缩放及区段偏移。实际 Java `RenderDataFactory` 的编码与当前启用的 shader 变体必须成套使用：不能只照抄其中一个条件编译分支。本轮没有完成所有边界值的跨语言解码验证。

### 不能复用最终 GL 绘制列表的原因

`VoxyRenderSystem` 创建 `HierarchicalOcclusionTraverser`，结合当前 viewport 的视锥/遮挡及请求回读推进层级。`NodeCleaner` 还以可见性追踪参与驻留管理。把此列表当作 TLAS 的全部实例，会使相机外的远景在反射、阴影或折射中丢失。

需要一个明确有限、与主相机遮挡独立的远景驻留集合和互斥 LOD 覆盖，保留源数据变化通知。不能简单删除所有可见性逻辑：当前网格请求也与遍历链相连，必须让 PT 的数据需求能独立推进到网格生成。

当前 Radiance 的世界渲染接管也不保证执行 Voxy 原来的 `renderSectionLayer` 等注入点。因此，仅取消某个 GL draw 不会自动启动完整的 Voxy 数据/网格服务。

### 模型与材质不是原模型原样保留

`SoftwareModelTextureBakery` 将模型投影到六个方向的颜色/深度图。其 CPU 栅格器不等于完全无 GL：`setupTexture` 仍使用 GL 查询并读回方块 atlas。`ModelFactory.MODEL_TEXTURE_SIZE=16`，`processTextureBakeResult` 将投影结果变为面大小、缩进、cutout 和颜色表；shader 的面缩进按约 1/64、面边界按 1/16 解码。

因此现有压缩网格主要是简化轴对齐面与烘焙外观，并不是所有 BakedQuad 原始拓扑、UV 与 PBR 参数的无损容器。实际水面、交叉植被、非立方模型、透明层、动态纹理、方块实体、Sable 与 Flywheel 内容不能由一个远景模型 ID 自动完整恢复。

`quad_util.glsl` 的标准光栅路径还使用光照表、方向明暗和 biome tint。接入 PT 时颜色 tint 与材质基础色可以保留；不能把已加光的最终光栅颜色再次当作未受光材质，否则会重复照明。现有表没有提供完整对应 Radiance 法线/粗糙度/金属度/发光及物理透射的合同；不能凭模型 ID 强行猜测。

### 初始化与所有权障碍

当前 NeoForge 元数据中 Sodium 已是 **optional**，本地新实现存在不依赖 Sodium 的摄入入口。不能沿用旧研究将“必须装 Sodium”作为当前元数据结论。但渲染部分仍大量依赖 `GlBuffer`、`GlTexture`、GL 能力与直接 GL 函数：

- `VoxyClient.initVoxyClient` 初始化 GL 能力并创建共享索引对象；
- `ModelStore` 直接创建 GL sampler、模型缓冲与 atlas；
- `SoftwareModelTextureBakery.setupTexture` 读回 GL atlas；
- `BasicSectionGeometryData` 和当前层级遍历/渲染后端使用 GL 资源。

本项目不能为此新建并行 GL context 或两个呈现所有者。需要 Voxy 的 PT 后端入口，将模型输入与几何结果交给 Vulkan 所有者，并阻止原 GL 渲染服务初始化。其存储、world lease、摄入和导入能力可优先复用，但应通过实际入口验证可分离程度，不能宣称现在已经 headless 完全可用。

### 建议技术路线与成本

| 候选 | 评价 |
| --- | --- |
| 捕获 Voxy 最终 GL 顶点/间接 draw | 不建议；仍依赖 GL，已丢失 PT 完整驻留集合，违背尽量提前接管目标 |
| `BuiltSection` → Java 展开 → 通用实体捕获 | 可作一次性正确性参考，不作为正式方案；多一次展开/复制，且远景不应每帧走动态实体发布 |
| `BuiltSection` + 材质表 → 按变化的 Vulkan 解码 → 持久 BLAS | 推荐方向；避开通用顶点翻译，复用持久场景与退休机制。先验证精确解码，再决定解码放 CPU 还是 compute |
| 压缩面直接用程序求交 | 备选研究；可以不生成传统三角形输入，但要新增求交及所有命中语义，成本与风险更大，不宜首轮扩张 |

推荐路线仍必须在变化时生成合法 AS 输入并构建 BLAS；“避开通用顶点转换”不等于“没有任何解码、上传或 AS 成本”。可将压缩表同时作为 hit shader 的材质输入，避免每个顶点重复冗长属性，但具体布局尚未设计或验证，不承诺零复制或收益幅度。

Radiance 已有外部区段 ID、代际、背压与安全发布/退休能力可作为机制基础；其 Java 接口当前绑定 `RenderSection` 与 Sable 来源，不能直接把 Voxy key 塞进其主世界空间槽。需要小范围的远景资源身份接口，不能为绕过转换牺牲生命周期保护。

### 进入实施前需要闭合的合同

1. 近景精确几何与远景代理的互斥覆盖及原子交接：不能同区域叠两层、切换时留洞，不能等新层未完成就删旧层。
2. LOD 集合对所有查询一致，不能仅反射时沿用主相机 draw list；父子层不得同时占据同一体积造成双重遮挡。
3. world/dimension、区段、资源与模型表代际贯穿异步任务；空更新、卸载、reload、退出均能正确发布删除并退休在途版本。
4. 模型图、biome tint、cutout、半透明、发光及 PBR 的明确能力范围；原表不足时保留限制或追加明确材质通道，不能暗中合并为 opaque。
5. 大坐标相对原点与运动历史同步，不能在 origin/LOD 切换后读旧模型身份或旧拓扑位置。
6. 内存预算与回收按真实引用；不能依据相机遮挡直接删 PT 仍需的远景，也不能无界保留整个数据库的所有层级。

这些是静态发现的工程合同，不是已经实施的功能。产品层还需明确是否接受 Voxy 自身的远景模型近似，以及缺失 PBR 时的表现边界；在此之前不能把“全查询参与”说成“与原始精确世界物理等价”。

### 许可与验证边界

当前 `LICENSE.md` 原文是 `All rights reserved. Do not redistribute.`，元数据为 `All-Rights-Reserved`。本地评估不等于获得复制、再分发或与本项目 GPL 源码合并的许可；优先通过外部模组接口互操作，任何公开源码搬运或公开二进制分发保持独立审查。现有 NVIDIA 发布门禁继续保留。

建议首轮只验证一段固定的 `BuiltSection`：八分组解码、实际模型表与 atlas、cutout/水面及镜像；再验证近远景切换、相机外反射/阴影、资源重载和安全退休。不得以菜单启动或文件包可加载替代世界几何证据。本轮不恢复性能测试，不给 FPS 预测。

## 结论

**值得按专用数据接口推进评估，不能作为即装即用兼容承诺。** 早期 CPU 压缩面结果是明确接入点，比最终绘制拦截更符合目标；当前 GL 初始化、材质近似、PT 独立驻留需求、近远景交接和许可仍是具体障碍。本轮只完成静态调用链、数据与所有权判断，未修改 Voxy 工作区，也没有新的构建或运行结论。
