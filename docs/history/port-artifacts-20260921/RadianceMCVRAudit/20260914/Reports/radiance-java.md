# Radiance Java diff audit

固定证据：Evidence/Radiance/units.json、files.json、worktree、unstaged.patch；HEAD=414d8e330a2fc6cb1e8630cc95f2302b2b97a0e8；范围=src/main/java/**。

逐 hunk 单位=521（unstaged=454，untracked=67）；JSON条目=543；长新增文件按职责拆分；所有 explained=true；本子任务未构建/未启动。

## 兼容对象版本

|对象|compile/static|既有RuntimeLogs实际装载|结论|
|---|---|---|---|
|Minecraft|1.21.1|1.21.1|API迁移静态覆盖，待构建。|
|NeoForge|21.1.248|21.1.250|版本漂移，mixin/loader待验证。|
|Veil|4.1.4|4.3.2|renderer=null导致setupLevelCamera/getCameraMatrices NPE，DEGRADED；skip非完整兼容。|
|Sable|2.0.3+mc1.21.1|2.0.5|compile/runtime漂移，bridge ABI按2.0.5重验。|
|Create|6.0.10+mc1.21.1|6.0.10|ValueBox静态capture，未验证。|
|Create Aeronautics|1.3.0+mc1.21.1|1.3.2|版本漂移，setup mixin未验证。|
|Flywheel|compatibility配置|1.0.6|fallback/program reload静态存在。|
|Ponder|Create jar-in-jar|1.0.82+mc1.21.1|UI边界静态存在。|
|Simulated|compatibility配置|1.3.2|diagram/spring/rope静态存在。|
|Sable Companion|依赖进入|1.6.0|随Sable装载。|
|Offroad|无独立pin|1.3.2|日志对象清单，本范围无独立实现。|
|Zume|无独立pin|1.2.2|日志对象清单，本范围无独立实现。|

## 分类统计

- 版本Loader迁移：106 条
- 第三方兼容(Aeronautics)：1 条
- 第三方兼容(Create)：2 条
- 第三方兼容(Flywheel)：3 条
- 第三方兼容(Ponder)：1 条
- 第三方兼容(Sable)：7 条
- 第三方兼容(Simulated)：12 条
- 第三方兼容(Veil)：13 条
- 工程维护：10 条
- 共用兼容设施：258 条
- 原版内容渲染翻译/主动调整：120 条
- vkWaitForFences诊断遗留：10 条

未认定上游错误修复：Java diff没有足够 issue/commit/行为证据。

## 重点核查

- F3：R-00502捕获早期OpenGL字符串，R-00133 backendString，R-00392接入GlUtil，R-00525/R-00542处理Veil F3；静态调用链存在，指针生命周期和真实F3值未验证。
- 轮廓/发光判定箱：R-00509完整VoxelShape边，R-00556 ModelPart，R-00170/R-00172/R-00173 EntityProxy outline capture到PRIORITY_ONLY；线宽、深度、相机偏移未验证。
- 区块线：R-00151..R-00161以compiled non-empty section替代vanilla visible counter并加入Sable external section bridge，R-00464调度；F3计数/区块线画面未验证。
- 粒子/半透明：R-00176/R-00177、R-00430..R-00434、R-00550覆盖标准/CUSTOM；R-00328删除旧BillboardParticle，当前链路无WhiteAsh 1/8 override；cutout/translucent只构成静态意图。
- GlowItemFrame：R-00505为5/15 emission，R-00552包围block/item/map，PBRMaterialContext提供scope；未验证实际光照/排序。
- 诊断清理：R-00510, R-00519-P8, R-00520, R-00520-P10, R-00520-P9, R-00532, R-00533, R-00562, R-00563, R-00564 均标vkWaitForFences诊断遗留，待全部移除；诊断不等于有效修复。

## Veil降级

既有 RuntimeLogs/20260914-122757 显示 Veil 4.3.2 与 Sable 2.0.5 加载，随后 LevelRenderer setupLevelCamera 调用 VeilRenderSystem.renderer().getCameraMatrices() 因 renderer=null NPE。当前 Java skip/filter 覆盖部分 OpenGL state、shader、debug、transparency 回调，但未覆盖该调用点；并存过度禁用内容与漏拦截风险。

## 校验

字段齐全；source units=521，explained IDs=521，missing=0；诊断条目=10；动态验证均PENDING。

完整台账：radiance-java.json。
