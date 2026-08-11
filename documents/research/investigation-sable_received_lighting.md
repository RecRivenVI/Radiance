# Sable 包装消费者遗漏的 Catnip 受光剥离

2026-10-28，使用者反馈 sublevel 上的安山门仍有额外明暗。本轮在实际发行包中复现了 Catnip 经 Sable 包装后再次乘入固定方向光的缺口，并在 Extra 修正；定向输入对照通过，用户原场景的动态视觉仍待验收。没有性能实验。

## 问题

前一轮移除了 Catnip 直接 PT 请求的固定方向明暗及逐顶点受光查询，但不能据此认为包装后的消费者也都进入相同路径。安山门关闭时可使用区块模型，运动时使用 `SlidingDoorRenderer` 和 Catnip 缓存部件；它不是 Flywheel Visual。

Sable 的 `SubLevelVertexConsumer` 隐藏了底层 `PBRVertexConsumer` 类型。原 Catnip 分支只在直接识别 PT 消费者，或当前主世界阶段满足附加条件时剥离受光。模型准备期间没有当前世界阶段时，已知包装内的 PT 输出仍执行原 `ShadeSeparatingSuperByteBuffer.renderInto`，将固定方向明暗乘进 RGB。

## 方法

核对固定 Create 6.0.10、Catnip／Ponder 1.0.82、Flywheel 1.0.6、Sable 2.0.6 及组合包 Simulated 1.3.2 的来源与现行适配。Sable 2.0.5 留存源码仅用于定位，实际 2.0.6 类的包装行为按登记 JAR 字节码复核。没有下载或升级依赖。

沿用 `SableHandoffProbe` 与 `world_materials.py handoff`，用独立环境标志调用小型属性采样。实际安山门覆盖四个水平朝向、主世界／Sable 动态明暗状态、静态模型、缓存模型、运动部件、单层／双层 Sable 包装和光栅预览。额外常色模板检查源 RGBA、UV、overlay 与法线。采样发生在 PT 输入边界，不将结果当作像素、照明或 FPS 证明。

```powershell
$env:RADIANCE_RECEIVED_LIGHT_CHECK = '1'
py -3.14 -B -X utf8 components/render_diagnostics/benchmark/world_materials.py handoff received-light-fixed versions/1.21.1-neoforge/build/candidates/adapter-source-20261028-a/radiance-1.21.1-neoforge-0.1.5-alpha.jar --extra components/Radiance-Extra/versions/1.21.1-neoforge/build/candidates/received-light-20261028-a/radiance_extra-1.21.1-neoforge-0.1.0-alpha.jar --reuse-cache validations/conformance-takeover/result/world-materials-134/adapter-source-a-handoff-20261028-003452/owned-cache.zip
Remove-Item Env:RADIANCE_RECEIVED_LIGHT_CHECK
```

本轮实际用例名已有结果，后续运行使用新标签。客户端仅项目内、1920×1080、静音，复用自有管线缓存，不采性能数据。未启动或修改 Prism。没有设备丢失或自动故障重试。

## 发现

### 复现与修正

基线记录位于 `validations/conformance-takeover/result/world-materials-134/received-light-wrapped-baseline-handoff-20261028-020506`。同一块门、同一缓存模型，直接 PT 输入只有白色；增加真实 Sable 包装后，八组组合均出现 102、130、139 和 255 的灰度。包装自身仅转发标量属性，变暗来自未关闭的 Catnip diffuse，不能靠反向除色、提升曝光或强制发光修复。

Extra 的 `CatnipTemplateGeometryMixins` 先通过现有 `SourceHooks.bulkConsumer` 解析已登记的来源照明包装，再选择持久模型或 PT 回退。Sable 适配器允许解析嵌套的确切 `SubLevelVertexConsumer`。没有拆除未知装饰器；没有明确 PT 目的地时，仍保留原有 framebuffer／场景域限制和原消费者。`RasterPreviewScope` 仍先返回原路径，Ponder 的特殊 bulk emitter 处理保持不变。

修正会话为 `validations/conformance-takeover/result/world-materials-134/received-light-fixed-handoff-20261028-021330`：

- 八组静态、缓存、单层包装、双层包装、运动 BER 和包装运动 BER 的输入颜色均为白色，没有附加方向明暗；顶点数量分别保持 40、20 或 80，均非空。
- 八组缓存预览仍保留 102、130、139、255 的来源方向明暗，直接与包装后的颜色、UV、overlay、法线和顶点数完全相同。
- 独立模板保留 RGBA `(80,120,160,96)`、四组不同 UV、overlay `(7,3)` 与真实法线；嵌套包装不改变这些值。既有单元测试另覆盖 UV 仿射、非白模板、透明度和受光字段分离。
- 完整功能会话正常退出，`passed=true`；该标志本轮提前完成属性采样，不冒充重新跑完装配／拆解全流程。

### 其他来源的边界

| 来源 | 本轮核对结果 |
| --- | --- |
| Sable 静态烘焙区块与单方块 | 共用本体的 quad／AO 受光剥离；直接输入采样未出现额外明暗。本轮不重新编写区段系统 |
| Flywheel | 世界路径继续不消费来源 lightmap／固定明暗；来源材质和独立发光保留，没有用满亮 lightmap 生成光源 |
| Veil 的 Sable／Simulated 程序 | 保留图解和光栅预览所需的 normal lighting、方向光与 shadow 参数；不能全局清零这些 uniform 来修 PT |
| Simulated 绳索、胶水与航空学粒子 | 所检查路径使用来源颜色与独立 lightmap，没有发现可据以统一删除的额外 RGB 受光乘法；End Sea 的渐变染色是已批准的来源颜色，保留 |
| Voxy 与未知来源 | 继续按各自适配任务核查，不归入本轮全部通过 |

没有按门、贴图或 renderer 名称加白名单，没有取消 PT 的真实接收照明，也没有移除受击覆盖、源发光或动画。

### 构建与证据

Extra 执行 `spotlessApply check verifyRelease verifyCompliance --strict` 和 `probeClasses` 成功；111 项单元测试零失败、零跳过，严格合规零失败、零警告。修改仅涉及 Extra，复用完全相同的本体与 Native 发行包，没有重编或重跑未变化的 Native／GPU 矩阵。

配对身份见 `validations/conformance-takeover/result/received-light-20261028-a/candidate.json`，其中固定源码快照、Extra SHA-256 和本体原有身份。探针不进入发行包。保留前后两份报告及初次探针编译、存储预检失败记录；这些失败不是设备丢失。

构建后将可再生的依赖解包、格式工具、moddev 和中间打包缓存送回收站，保留固定产物、输入与唯一证据；清单见 `components/render_diagnostics/build/received-light-final-cache-cleanup.json`。客户端复制环境在结论记录后回收，原容量上限继续生效。

## 结论

已证实并修复“已知 Sable 包装使 Catnip 漏选 PT 受光政策”的缺口，不只是调整 Native 最终颜色。直接路径和包装路径现在采用同一来源解析，已知预览继续保留光栅照明。

用户原场景的开关交接、结构旋转、Ponder／GUI 动态外观仍需体验，不能从白色顶点推导最终画面无亮度变化：世界 PT 的光照、遮挡、材质及贴图固有色差依然应该存在。本轮不宣布所有第三方 AO、lightmap 和固定明暗已经清除，不提交、不推送。
