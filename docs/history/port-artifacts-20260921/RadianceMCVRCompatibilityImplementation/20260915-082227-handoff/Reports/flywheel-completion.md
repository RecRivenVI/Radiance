# Flywheel 1.0.6 / Create 6.0.10 completion checkpoint

日期：2026-09-15  
状态：最终源码冻结；Java/JNI、native、CPU contract与built-in RT shader静态编译通过；没有游戏或 GPU 实例画面验收。

## 所有权与保留项

本代理修改：

- Radiance `compatibility/flywheel/*.java` 中 engine、material 与 adapter 链；`NativeInstancingProxy.java`；既有 Flywheel tests。
- Radiance `mixins/compatibility/flywheel/FlywheelRenderSystemLightsAccessor.java` 与 `FlywheelLightTextureAccessor.java`；共享mixins JSON注册由根代理完成。
- MCVR `instancing.cpp/.hpp`、`instancing_contract.hpp`、NativeInstancingProxy middleware、`shared.hpp` 的 InstanceAppearance/InstanceLightSection、`shader/util/vertex.glsl` 的 instance appearance/light 消费、`world_prepare.cpp/.hpp` 的 Flywheel TLAS/light/history 段、`ray_tracing_module.cpp` 的 set1 binding 10/11 段、既有 `instancing_contract_test.cpp`。

根代理先前实现的 per-engine handle、normalCorrection、exact 8 adapter byte-size、model frame retainer 与 backend/JNI 注册均保留。未修改 UI/framebuffer/Veil、physics、通用 buffer/shader 或其它 world pass。

## 实现矩阵

| 项目 | 当前实现 | 证据边界 |
|---|---|---|
| 8 adapter ABI | Java layout字段/offset/byte size严格校验；native `validInstanceBytes`严格校验；normalCorrection保留 explicit normal | 静态与native contract；无真实实例画面 |
| per-engine lifecycle | 根实现distinct engine handle；本轮每engine持有model/instance/light data；delete/close清除child资源；stale handle抛错 | native源码与此前contract build |
| model/BLAS复用 | 每Model只上传一次；BLAS/model由prepared instance持有并由frame retainer保活 | core源码；无GPU计数器 |
| motion history | `(engine,id)`作为稳定键保存上一帧Flywheel object transform，删除/隐藏实例会从下一帧history淘汰 | 源码；待shader/GPU motion验收 |
| Minecraft动态光照采集 | 直接复用Flywheel 1.0.6官方 `LightDataCollector.of(level)`，每section产生6564-byte官方布局：18³邻边、solid bitset、block/sky packed nibble | Java编译检查点；无live level采集测试 |
| light update | `lightSections()`全量脏化；`onLightUpdate()`使受该section边界影响的27个已跟踪section重采；上传完成前native `render()`拒绝继续 | Java/native合同；待运行线程时序验收 |
| GPU light table | 每engine section数据按engine/SectionPos排序，上传binding 11；InstanceAppearance记录对应range及camera-relative object transform | core Release链接检查点；最终源码待根复编 |
| per-vertex light | hit shader对三个triangle vertex以最终object/embedding绝对world transform查section；FLAT直接采样，TRI_LINEAR按官方0.5中心进行8点插值，SMOOTH/INNER_FACE按官方27点、每方向4邻点、8 corner valid-count、normal²规则计算；instance packed light仍按Flywheel语义作为minimum | 源码逐项对应官方`internal/light_lut.glsl`；built-in RT pack待根最终编译 |
| AO | 消费官方18³ solidity数据；SMOOTH按4-corner valid-count得到AO，INNER_FACE在空corner按axis mask选对侧corner | 已移除三轴近似，公式对应Flywheel 1.0.6 |
| overlay/light material flags | `useOverlay/useLight`决定base material位与instance override是否生效；light shader类型和BackendConfig smoothness编码进material flags | 源码；待shader编译/GPU |
| cardinal lighting | OFF/CHUNK/ENTITY均进入shader；CHUNK逐项使用Flywheel官方`diffuse.glsl`权重；ENTITY每帧读取RenderSystem当前两个shader light direction，经JNI归一化并随每engine appearance上传，严格使用官方`(dot0+dot1)*0.6+0.4` | 新JNI/accessor与368-byte ABI待根最终编译 |
| backface cull | homogeneous模型保留TLAS快速剔除；所有Flywheel BLAS geometry保留any-hit，mixed material在default/transparent any-hit按每geometry material flag与HitKind拒绝背面 | 固定模型与mixed模型均有真实per-geometry路径 |
| polygon offset | 请求polygon offset的mesh在BLAS输入沿vertex normal偏移1e-4，避免shadow共面冲突 | 有真实几何效果，但不是GL slope/depth-unit公式 |
| transparency | 0/1/11沿用opaque/cutout/additive；保留12..19 text mode，新增20 LIGHTNING、21 GLINT、22 CRUMBLING、23 TRANSLUCENT。现有`lightning` SBT group承载transparent-only hit，按Flywheel原始src、src*alpha、src²、2*src*dst和front-to-back方程更新radiance/throughput；crumbling duplicate instance强制使用该SBT group | CPU contract用具体RGBA向量逐式断言；shader待根编译 |
| depth/write mask | 固定组合审计中普通材质均COLOR_DEPTH/LEQUAL；GLINT为COLOR/EQUAL，shadow/crumbling为COLOR并走继续光线的transparent hit，不写遮挡深度；仓库固定引用无DEPTH-only material | 固定Flywheel/Create组合已接路径；任意第三方DEPTH-only material不在本轮固定组合证明内 |

## 验证

PASS检查点：

- Radiance `compileJava compileTestJava --no-daemon`：在新增官方LightDataCollector/JNI上传链后成功；之后仅把light section容器从List收敛为Set，最终源码仍由根统一再编。
- MCVR统一 `core + shaders`：根代理在第一版light实现与readback共同源码上报告PASS；该检查点覆盖InstanceLightSection 6592-byte、InstanceAppearance 224-byte、binding 11、world prepare、JNI、polygon offset与SectionPos helper的core链接。之后加入真实shader directions/LightTexture与完整AO，ABI升至256，须重新验证。
- `shaders`常规目标：成功；该目标不编译built-in RT pack，不能作为`vertex.glsl`新light函数通过证据。
- 根代理 `Evidence/instancing-light-contract-run.log` 已证明normal、exact adapter、224/6592 ABI、负SectionPos与18³offset PASS；之后InstanceAppearance最终升至368并加入blend方程/text-slot/diffuse/pre-embedding crumbling断言，须重新运行 `mcvr_instancing_contract_test`。

## 尚未完成和必须保持的边界

- Built-in vanilla-pt/advanced RT shader pack尚未对最终 `vertex.glsl` 编译；binding 11和大数组布局只通过C++ static size，不是shader ABI完成证据。
- 新增 `FlywheelRenderSystemLightsAccessor`、`FlywheelLightTextureAccessor`、`setShaderLights(engine, six-float directions, lightTextureId, constantAmbientLight)` 与368-byte InstanceAppearance之后，Java/JNI/core/RT shader仍待根统一重新生成header并编译。
- lightmap颜色现在采样Minecraft当前16x16 `LightTexture` 后乘Flywheel材质颜色；这是Flywheel raster的实际light consumer。PT随后仍会计算场景光传输，因此需要GPU画面对照确认材质是否发生用户不可接受的双重变暗，不能只凭编译判定视觉等价。
- GL polygon offset按vertex normal做1e-4 BLAS偏移，覆盖固定shadow共面目标，但不是GL slope/depth-unit公式。
- 任意第三方DEPTH-only、非固定custom fragment/light shader和自定义hit group不在固定Flywheel1.0.6/Create6.0.10证明内。
- 没有真实ClientLevel light update、多个VisualizationManager并存、reload/off切换、instance删除后GPU资源释放、BLAS复用计数、motion vector、Create fluid/shadow/glint画面的运行证据。
- 因此本报告是源码实施检查点，不是完整Flywheel完成声明。所有变动未暂存、未提交、未推送。

## 最终构建输入

- Java/JNI：`gradlew.bat compileJava compileTestJava --no-daemon`，随后以生成的`Radiance/src/main/native/include/com_radiance_client_proxy_world_NativeInstancingProxy.h`为准。
- Native：`cmake --build build --config Release --target core -j 4`。
- Contract：`cmake --build build --config Release --target mcvr_instancing_contract_test -j 4`，随后运行生成的Release测试程序。
- RT shader必须编译两个固定包中所有包含`util/vertex.glsl`、`util/alpha_mode.glsl`的stage，不能只运行排除`world/ray_tracing/internal`的普通`shaders` target。
- 官方逐项对照源：`flywheel-1.0.6-neoforge.jar!/assets/flywheel/flywheel/internal/light_lut.glsl`、`diffuse.glsl`、`material.glsl`，以及源码`LightDataCollector.java`、`LightStorage.java`、`Transparency.java`、`MaterialRenderState.java`。

## Astra复核后的第二轮修复

- Any-hit不再读取原始mesh material；新增`loadTriangleCoverage`，共享color multiply/replace、scroll/fluid/shadow UV、overlay/light minimum、texture override与normal correction。覆盖判断和closest-hit看到同一实例材质状态，any-hit不执行昂贵动态光采样。
- Crumbling不再创建共面第二个TLAS instance。原base hit保留，破坏纹理由同一个base fragment按`2 * crumble * base`合成；因此不依赖TLAS遍历顺序或续射epsilon能否再次命中base。
- 动态光与AO从triangle vertex阶段移到default/no-height/transparent closest-hit的实际barycentric位置和插值normal；`flat.glsl`固定走中心fetch，不受BackendConfig smoothness影响。全solid返回AO 0.2，但只有`ambientOcclusion=true`才乘入颜色。
- `Embedding.transforms`通过`Context.dependsOn()`脏化自身及全部后代instancer，父embedding移动会重写静止子实例的组合矩阵。
- 跨engine `stealInstance`现在在迁移锁下删除旧native instance，切换engine引用，分配目标engine新ID并在目标model/context重传；Handle写入和删除均使用当前engine，避免每engine从1开始的ID冲突和旧outer JNI误投。
- CHUNK diffuse逐项采用官方公式；`constantAmbientLight`维度改用官方Nether权重。ENTITY每帧仍使用RenderSystem真实shader directions。JNI `setShaderLights`新增dimension布尔值。
- Crumbling UV新增独立pre-embedding instance transform和normal。裂纹face投影发生在renderOrigin/embedding之前，与官方`common.vert`顺序一致；非整数平移/旋转embedding不会让裂纹沿模型滑动。InstanceAppearance因此由256增至368字节。
- Same-engine steal保留原ID和已存在native instance，只更新owner/model并dirty；跨engine分支才删除旧native、分配目标engine新ID。Java bytecode测试新增engine identity branch断言，防止motion history键无谓失效。

第二轮新增反例断言：text/Flywheel alpha槽隔离、五种blend具体RGBA结果、普通维度上下表面1.0/0.5及constantAmbientLight的Y/X 0.9/0.6。Java另有bytecode反例测试，确认父embedding检查后代、跨engine steal删除旧native/分配新ID，且Handle写入读取当前engine字段。

2026-09-15第二次冻结验证：

- 首次Gradle尝试被会话临时切换的JDK 25阻断于Groovy semantic analysis（major version 69），没有执行项目源码。
- 显式使用`C:\Program Files\BellSoft\LibericaJDK-21-Full`后，`compileJava`、`compileTestJava`及Flywheel Adapter/Mesh/Ownership三组定向测试全部PASS。
- 最新生成JNI header确认`setShaderLights`签名为`(JJIZ)V`；MCVR所有本轮文件`git diff --check` PASS。
- `mcvr_instancing_contract_test`、native core及所有vanilla-pt/advanced shader variant仍由根代理统一执行，结果返回前不得写成PASS。
- 最后两项修复后的Java重跑曾被其它所有权中新加入的`VeilShaderVersionProcessorMixins`缺少`GlslTree` compile依赖阻断，未执行Flywheel测试；Veil owner随后已修该全树构建阻断并正在统一复跑。上一轮Flywheel PASS不覆盖最后新增的same-engine identity断言。
- Veil owner修复compile classpath后，明确JDK21重跑`FlywheelEngineOwnershipBytecodeTest`已BUILD SUCCESSFUL，3 tests PASS；覆盖same-engine identity branch、跨engine delete/re-ID/current-engine write与embedding descendant检查。

Root verification update: 101 builtin source/default-pass variants were compiled with glslang. First pass83/101; real new vertex.glsl integration errors in priority/cloud/water stages exposed missing alpha_mode include, nonuniform extension, and a sampling_helpers dependency. Root corrected these three header dependencies with direct textureLod; rerun pending. Two SHARC resolve and two cubemap FACE jobs need harness runtime compile defines before results are interpretable. Independent Astra review has identified additional material/light/embedding/steal defects; those remain open and will be repaired before completion.

Root compiler result: Evidence/BuiltinPacks/results.json now237/237 PASS (default + SHARC query/update + six FACE variants), includes current six-fix shader source; source-seal.json records files. Uses glslang Vulkan1.4 with ShaderPackLoader-equivalent default attribute substitution and runtime pass defines. This is SPIR-V syntax/type compilation, not native shaderc cache/loader execution or GPU trace acceptance.

## 最终统一验证结果

- Radiance JDK21 `compileJava`、`compileTestJava`、JNI header生成和Flywheel ownership/adapter/mesh定向测试PASS。最新`setShaderLights` JNI签名为`(JJIZ)V`。
- MCVR 368-byte InstanceAppearance版本的`core`与普通`shaders`编译/链接PASS，证据：`Evidence/tessellation-integrated-build-final.log`。
- `mcvr_instancing_contract_test`编译与运行PASS，覆盖normal correction、8 adapter exact bytes、368/6592 ABI、负SectionPos、18³ light offset、text/Flywheel alpha槽隔离、五种blend具体RGBA、两套CHUNK diffuse和pre-embedding crumbling位置，证据：`Evidence/tessellation-and-readback-tests.log`。
- vanilla-pt与advanced两个built-in pack在最终368 ABI及最后两项修复后，default、SHARC query/update、六FACE共237个glslang Vulkan 1.4变体全部生成SPIR-V，结果与冻结源seal：`Evidence/BuiltinPacks/results.json`、`Evidence/BuiltinPacks/source-seal.json`。
- `git diff --check`在Radiance/MCVR本所有权范围PASS；所有改动仍未暂存、未提交、未推送。

上述证据证明源码、Java/JNI ABI、C++合同与SPIR-V静态编译闭合。它不证明真实ClientLevel动态光上传、多VisualizationManager/reload、GPU资源释放、motion、Create fluid/shadow/glint像素或PT lightmap视觉结果；这些仍需根代理接通真实world consumer后运行，并由用户完成画面验收。
