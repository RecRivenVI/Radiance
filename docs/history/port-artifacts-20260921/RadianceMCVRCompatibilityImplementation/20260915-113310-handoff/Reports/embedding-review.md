# Flywheel / Sable native scene embedding review

日期：2026-09-15  
范围：当前 Radiance / MCVR 源码，以及固定的 Flywheel 1.0.6、Sable 2.0.5 原始消费者。只审查 Flywheel engine 的 render origin、embedding pose/normal、Sable scene 坐标、sky scale、`InstanceAppearance`/`InstanceLightSection` ABI 和 instance/model 生命周期。Sable world raster/Levitite extra layer、EndSea shadow、Veil reload 与 Simulated diagram 属于其他工作项，本报告不重复它们。

本轮没有构建、测试、客户端、GPU 或 UI 操作，也没有修改源码或 Git 状态。根代理在本审查期间已按下面的法线发现修改当前 `MCVR/src/shader/util/vertex.glsl`；该改动的静态编译和运行证据仍由根代理补齐。

## 固定输入与版本证据

Radiance 当前固定输入位于 [gradle.properties](D:/Workspaces/Repositories/GitHub/RecRivenVI/Radiance/gradle.properties:5)：Minecraft `1.21.1`、NeoForge `21.1.250`，以及 Create `6.0.10+mc1.21.1`、Sable `2.0.5+mc1.21.1`、Aeronautics `1.3.2+mc1.21.1`、Veil `4.3.2`（15-18 行）。实际 compileOnly/compatibility/runtime 配置在 [build.gradle](D:/Workspaces/Repositories/GitHub/RecRivenVI/Radiance/build.gradle:104) 至 131 行。

本次 scene 对照使用：

- Flywheel：`D:\Workspaces\References\minecraft-references\flywheel-1.0.6-neoforge`；其 `REFERENCE.json` 固定 Maven source 坐标 `dev.engine-room.flywheel:flywheel-neoforge-1.21.1:1.0.6:sources`，source SHA-256 和坐标证据在该文件的 `sourceArtifact`/`versionEvidence`。
- Sable：`D:\Workspaces\References\minecraft-references\sable-2.0.5`；其 `REFERENCE.json` 固定 tag `mc1.21.1-2.0.5-neoforge`、revision `6966d2928340de7631abcecf8549904b877df0a8`，并记录 archive SHA-256。
- MCVR 当前承接点：`src/core/render/instancing.hpp/.cpp`、`instancing_contract.hpp`、`src/core/render/modules/world/ray_tracing/submodules/world_prepare.cpp/.hpp`、`src/common/shared.hpp`、`src/shader/util/vertex.glsl`、`src/core/middleware/com_radiance_client_proxy_world_NativeInstancingProxy.cpp`。

## 结论摘要

| 主题 | 当前源码路径 | 实际缺口 | 最小下一步 | 历史验证范围 |
|---|---|---|---|---|
| engine render origin | `RadianceFlywheelEngine.java:97-107`；`FlywheelRenderBridge.java:25-35` | 没有可实施缺口。当前 `updateRenderOrigin()` 返回 `true` 后会进入原 Flywheel recreate 路径 | 保留现状；只做当前整合后的 runtime origin 移动验证 | 原 Flywheel dispatcher/frame-plan 静态对照；历史 Java/Native/RT 证据没有真实 origin 移动画面 |
| Sable pre-frame hook | `WorldRendererMixins.java:182-187`、`FlywheelRenderBridge.java:33-35` | 没有缺失入口。当前 bridge 调用原 dispatcher `onStartLevelRender` | 保留；runtime 只核实 Mixin 实际装载 | fixed Sable source 对照，未证明 live Mixin 应用 |
| embedding pose / world normal | `RadianceFlywheelEngine.java:292-381,507-527`；`instancing.cpp:159-180` | world/cardinal normal 链已闭合 | 保留；随根的 shader/客户端验证 | 历史 contract 覆盖 normal correction、embedding descendant dirty；无 live 像素证据 |
| explicit scene normal | 当前 `vertex.glsl:400-415` | 原实现曾错误按 `lightTransform` 再变换法线；根已改为传现有 `worldNormal`，当前源码已承接固定 Sable 语义 | 根重新编译最终 237 个 RT variant，并在移动/旋转 sublevel 样例核对法线和 AO | 之前的 normal contract/237 SPIR-V 不能覆盖本轮修复；本轮未运行验证 |
| scene matrix / section coordinate | `SableFlywheelLightSections.java:14-29`；`instancing_contract.hpp:17-21,39-49`；`world_prepare.cpp:211-220` | 未发现源码缺口；world 使用绝对 section，Sable scene 使用 plot-center 相对 section，scene 0 才补 engine origin | 保留；验证 scene ID 变化时关系重排和边界 section | 历史 Java relationship/light section tests 与 native contract；无 live Sable scene 采样 |
| sky scale | `RadianceFlywheelEngine.java:371`；`vertex.glsl:419-424` | metadata、368-byte slot 和 shader 消费链存在；是否与当前光图采样精度完全等价只能由 runtime 画面对照确认 | 用真实 Sable sublevel sky scale 变更核对 sky 通道；不要据待验结果新增另一套光照系统 | 历史 Java snapshot/ABI/237 SPIR-V；没有真实动态 sky 证据 |
| ABI 368 / 6592 | `shared.hpp:170-209`；`vertex.glsl:36-44`；`ray_tracing_module.cpp:1909-1910` | 未发现静态 ABI 缺口；C++ static assert、GLSL 声明和 set 1 binding 对齐 | 以根当前源码重新跑既定 shader/native gate | 历史最终 contract 记录覆盖 368/6592；当前本轮未重跑 |
| delete / reuse | `RadianceFlywheelEngine.java:198-210,401-480,507-583`；`instancing.cpp:187-209,296-307,402-410` | 未发现可达生命周期缺口；instance ID 单调，跨 engine steal 先删旧 native，close 递归清理 child/model/light state | 保留；只验 world recreate、steal、delete 后无 stale GPU instance | 历史 ownership bytecode、native contract；无 GPU resource counter |
| scene `-1` | `FlywheelEmbeddingLighting.java:20-33`；`SableFlywheelLightSections.java:20-29` | 有意保持语义：embedding 的显式 `-1` 不替换为 0；Sable section 分区未注册时按原 LightStorage 默认 scene 0。不是应立即修改的缺口 | 只核对容器 ID 分配与 embedding 更新的同帧时序 | 历史 embedding test 覆盖 `-1` retention 和 scene-0；无 live 未注册瞬间样例 |

结论：在本报告边界内，根已承接唯一发现的 scene 法线修正后，没有第二个已证实、可以直接实施的 engine/embedding/ABI 缺口。剩余项是当前源码改动的统一静态 gate 和实际 Mixin/native/GPU/runtime 证据，不应被写成源码功能已完成。

## 1. render origin 与原 dispatcher 链

当前 Radiance world 主体在 [WorldRendererMixins.java](D:/Workspaces/Repositories/GitHub/RecRivenVI/Radiance/src/main/java/com/radiance/mixins/vulkan_render_integration/WorldRendererMixins.java:182) 创建 `FlywheelRenderBridge.Frame`。`FlywheelRenderBridge.begin()` 在 25-35 行取得 `VisualizationManager`、创建原 `RenderContextImpl` 并反射调用 dispatcher 的 `onStartLevelRender`；API 解析失败会在 45-57 行抛出 `IllegalStateException`，不会静默伪造空 Flywheel 帧。

当前 `RadianceFlywheelEngine.updateRenderOrigin()` 在 97-107 行按 256 格阈值更新 origin，并返回 `true`。这不能单独解释为“只 dirty、没有重建”。固定 Flywheel 1.0.6 的 [VisualizationManagerImpl.java](D:/Workspaces/References/minecraft-references/flywheel-1.0.6-neoforge/src/dev/engine_room/flywheel/impl/visualization/VisualizationManagerImpl.java:107) 至 129 行定义了：`engine.updateRenderOrigin(ctx.camera())` 为真时执行三个 storage 的 `recreateAll`，否则走通常的 `framePlan`；259-277 行显示 `onStartLevelRender()` 进入 `beginFrame()`，再执行这个 `framePlan`，`afterEntities()` 执行 engine render。

因此本组合的可达链是：

```text
Radiance WorldRendererMixins
  -> FlywheelRenderBridge.begin
  -> VisualizationManager$RenderDispatcher.onStartLevelRender
  -> VisualizationManagerImpl.beginFrame
  -> LateInit.framePlan
  -> updateRenderOrigin == true ? recreateAll : normal frame plan
```

不会再要求 Radiance 自己复制一套 `recreateAll`。原 Sable `RenderDispatcherImplMixin.java:20-24` 同样注入这个 `onStartLevelRender`，调用 `preVisualizationFrame` 和 `sable$preFlywheelFrame`；当前 bridge 调的是同一个 dispatcher 方法，所以 Sable pre-frame 入口也可达。这里没有源码缺口，只有 live Mixin 应用、远距移动和重建后的画面待验。

## 2. embedding pose 与 local normal

原 Flywheel 的 embedding 规则在 `EmbeddedEnvironment.java:95-114`：父 pose/normal 先写入，再以 `pose.mul(childPose)`、`normal.mul(childNormal)` 组合。当前 Radiance [RadianceFlywheelEngine.java](D:/Workspaces/Repositories/GitHub/RecRivenVI/Radiance/src/main/java/com/radiance/compatibility/flywheel/RadianceFlywheelEngine.java:372) 的 `writeComposed()` 保持相同乘法顺序；`GlobalContext` 在 292-313 行提供 identity root，`Embedding.renderOrigin()` 在 354-380 行保留每个 embedding 的 local origin 和父链。

`Handle.write()` 在 507-527 行把组合 pose 写入 112-byte scratch buffer 的 0 偏移，把组合 normal 写入 64 偏移，再调用 native `updateInstance()`。native [instancing.cpp](D:/Workspaces/Repositories/GitHub/RecRivenVI/MCVR/src/core/render/instancing.cpp:159) 至 180 行先解出 adapter 的 local normal，再用 [instancing_contract.hpp](D:/Workspaces/Repositories/GitHub/MCVR/src/core/render/instancing_contract.hpp:28) 至 32 行的

```text
transpose(objectToWorld) * (embeddingNormal * adapterNormal)
```

生成 `normalCorrection`。RT shader 的 `inverseTranspose(objectToWorld)` 会抵消这层预校正，得到 Flywheel 原始的 embedding/instance normal。这个 world/cardinal 链与 fixed Sable `common.vert:95-103` 的 model/normal 处理一致。

### 已发现并已由根修正的 scene-normal 问题

原当前 shader 在 `applyFlywheelFragmentLighting()` 中已经算出 `worldNormal`，但曾在动态 light 分支把 `sceneNormal` 计算成 `inverseTranspose(appearance.lightTransform) * localNormal`。对非零 Sable scene，`lightTransform = sceneMatrix * instanceLocal`，而 `localNormal` 已带 `objectToWorld` 的 normal correction；sublevel 的 render pose 与 scene matrix 尤其在旋转时不相同，这会把法线再次按 lighting scene 变换。

固定 Sable 2.0.5 的 [common.vert](D:/Workspaces/References/minecraft-references/sable-2.0.5/src/neoforge/src/main/resources/assets/flywheel/flywheel/internal/common.vert:95) 至 103 行明确只用 `_flw_lightingSceneMatrix` 变换 lookup position，只用 `_flw_normalMatrix` 变换 `flw_vertexNormal`；[smooth_when_embedded.glsl](D:/Workspaces/References/minecraft-references/sable-2.0.5/src/neoforge/src/main/resources/assets/flywheel/flywheel/light/smooth_when_embedded.glsl:9) 至 11 行把同一个 `flw_vertexNormal` 传给 `flw_light`。根已把当前 [vertex.glsl](D:/Workspaces/Repositories/GitHub/RecRivenVI/MCVR/src/shader/util/vertex.glsl:412) 至 415 行改为：scene lookup 继续使用 `appearance.lightTransform` 得到位置，但把已算好的 `worldNormal` 传给 `sampleFlywheelLight()`。这是本审查唯一发现的可实施 scene/normal 修正；当前报告写入时尚未重编或运行。

## 3. Sable scene 坐标、light bytes 与 sky scale

固定 Sable 的真实消费者在 `BlockEntityStorageMixin.java:145-169`：

- embedding physical pose 使用 `position - visualizationContext.renderOrigin()`；
- scene matrix 只把 embedding local origin 平移到 `plot.centerChunk` 的相对坐标；
- scene ID 来自 `container.getLightingSceneId(subLevel)`；
- sky scale 是 `latestSkyLightScale / 15.0f`。

Create contraption consumer `ContraptionVisualMixin.java:75-95` 同样把 entity position/LocalTransforms 写入 scene matrix，把 render pose 写入 embedding pose，并发送 scene ID/sky scale。当前 Radiance [SableFlywheelEmbeddingMixins.java](D:/Workspaces/Repositories/GitHub/RecRivenVI/Radiance/src/main/java/com/radiance/mixins/compatibility/sable/SableFlywheelEmbeddingMixins.java:11) 至 17 行只做现有 Sable interface 到 Radiance lighting snapshot 的转发，没有另造矩阵来源，避免与原消费者重复。

当前 Java [SableFlywheelLightSections.java](D:/Workspaces/Repositories/GitHub/RecRivenVI/Radiance/src/main/java/com/radiance/compatibility/flywheel/SableFlywheelLightSections.java:14) 至 29 行对每个绝对 requested section：

1. 用 Sable helper 找 containing `ClientSubLevel`；
2. 从同一个 `ClientSubLevelContainer` 取得 scene ID；
3. 以 plot center chunk 相减得到 relative section；
4. 普通世界保留 absolute section。

`RadianceFlywheelEngine.uploadDirtyLightSections()` 在 250-266 行仍以绝对 section 调用原 `LightDataCollector.collectSection()`，只把 scene/relative metadata 另传给 JNI。native `uploadLightSectionScene()` 在 `instancing.cpp:339-343` 保留 absolute key，同时保存 `{scene,relativeSection}`。`preparedLightSections()` 在 385-400 行把 relative key 和 scene 写给 world prepare；[world_prepare.cpp](D:/Workspaces/Repositories/GitHub/RecRivenVI/MCVR/src/core/render/modules/world/ray_tracing/submodules/world_prepare.cpp:211) 至 220 行再编码 `section.xyz` 和 `section.w=scene`。这条链没有发现 section 坐标缺口。

`FlywheelEmbeddingLighting.resolve()` 保留原分支：scene 0 使用 composed pose，非零 scene 使用 Sable 自己的 scene matrix；未配置的嵌套 embedding 继承父 scene snapshot 并乘自身 local pose。显式 scene `-1` 仍保持 missing，不伪造成 scene 0。Sable light storage 对尚未注册 scene 的 requested section 采用默认 scene 0 分区；当前 helper 的 `scene == -1` 到 0 fallback 与此原分区策略相同，但 embedding 的显式 `-1` 不会匹配该数据，这是既有固定语义，需由生命周期时序验证而不是立即改掉。

Sky scale 的数据链也闭合：native `preparedInstances()` 在 `instancing.cpp:367-374` 将每实例 `skyScale` 放入 `InstanceAppearance.entityLight0.w`；当前 shader `vertex.glsl:419-424` 在 light texture 消费前只缩放 sky 通道。固定 Sable 原实现把同一值写入 embedding slot 28 并在 `smooth_when_embedded.glsl:18-19` 作用于 sky channel。当前源码没有缺失字段或丢失入口；光图离散采样与真实 sublevel sky 变化的像素等价性仍属于运行对照。

## 4. 368 / 6592 ABI 与 bindings

`shared.hpp:170-195` 的 `InstanceAppearance` 字段顺序与 `vertex.glsl:36-44` 的 SSBO 元素声明一致；`shared.hpp:197-200` 的 `InstanceLightSection` 是 `ivec4 section + uint data[1641] + uint pad[3]`。C++ [shared.hpp](D:/Workspaces/Repositories/GitHub/RecRivenVI/MCVR/src/common/shared.hpp:205) 至 209 行保留：

```text
sizeof(InstanceAppearance)   == 368
sizeof(InstanceLightSection) == 6592
```

light data 的 6564 bytes 来自 `instancing.hpp:57-65`；C++ `lightByteOffset()` 的 732-byte solid header、18³ padded light bytes 与 shader `FLYWHEEL_LIGHT_WORD_OFFSET=183` 对齐。set 1 binding 10/11 在 [ray_tracing_module.cpp](D:/Workspaces/Repositories/GitHub/RecRivenVI/MCVR/src/core/render/modules/world/ray_tracing/ray_tracing_module.cpp:1909) 至 1910 行，正对应 `vertex.glsl` 的 binding 10/11。这里未发现静态 ABI 或 binding 缺口。

历史最终报告曾记录 368/6592 contract、237 个 built-in RT variant、Java/JNI/native 检查点，但根在本轮法线修复后需要按当前源码重新跑同一 gate；本报告不把旧结果升级为本轮 PASS。

## 5. 删除、steal 与复用

当前 Java 生命周期：

- `RadianceFlywheelEngine.delete()`（198-210）先删除所有 instancer handles、关闭 model capture，再删除 native engine 并清空 light relationships；
- `RadianceInstancer.flush()`（449-465）在 context/handle deleted 时调用 native `deleteInstance()`；
- `deleteAll()`（471-480）立即删除已有 native instance；
- 跨 engine `stealInstance()`（410-447）先在迁移锁下删除旧 engine 的 native ID，再在目标 engine 分配新单调 ID；同 engine steal 复用 ID 但标 dirty，下一次写入目标 model/context；
- `Handle.write()`（507-534）每次先普通 `updateInstance()`，再发送当前 lighting snapshot，native 普通 update 会在 289-292 行清掉旧 explicit lighting，避免迁移后的 stale scene。

native `Instancing::deleteInstance()`（296）同时清掉 crumbling；`deleteEngine()`（204-209）递归 close child；`close()`（402-410）清空 instance/model/light maps。engine ID 使用静态单调计数器（187-195），Java instance ID 使用每 engine 单调计数器（401-404），没有回收到旧 ID 的路径。固定组合中 native model 没有 engine 存活期间的 deleteModel API，但 model 是按 Java `Model` identity 缓存、上传一次并在 engine close 时整体释放，这不是当前实际可达的重复消费者缺口。

## 历史验证边界

可引用但不能扩大含义的历史材料：

- `D:\Workspaces\Artifacts\RadianceMCVRCompatibilityImplementation\20260915-082227-handoff\Reports\flywheel-completion.md:89-97`：最终 368/6592 contract、Java/JNI/native 和 237 个 SPIR-V variant 的历史记录；同时明确无真实 ClientLevel 动态光上传、GPU resource counter、Create 画面和用户目视证据。
- `D:\Workspaces\Artifacts\RadianceMCVRCompatibilityImplementation\20260915-113310-handoff\Reports\flywheel-sable-embedding.md:55-72`：Sable embedding Java tests、scene `-1`、scene 0、嵌套继承/覆盖、section reassignment 和 ownership bytecode 的历史范围；明确未证明 live Mixin、native scene sampling、moving sublevel/contraption pixels。
- `D:\Workspaces\Artifacts\RadianceMCVRCompatibilityImplementation\20260915-113310-handoff\PROGRESS.md:63-65`：本轮 scene lighting 实施里程碑和旧 237 SPIR-V 记录；法线修复后必须以根的新编译结果为准。

本报告不把历史 PASS 变成当前改动后的新 PASS，也不把待客户端验收当成源码缺口。根后续只需闭合当前 shader 修复的静态编译、JNI/native 整合和固定 Sable moving/rotating scene 的运行验证。
