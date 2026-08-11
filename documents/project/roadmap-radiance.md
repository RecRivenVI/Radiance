# 项目路线图

当前执行范围截至 2026-10-12。已保留实现与事实边界以[项目现状](status-current.md)为准；本文件只列尚未完成的工作，不把历史方案或验收结果当作当前执行指令。

## 已暂停：双向几何后的区块加载掉帧

使用者报告：将单向几何全部切换为双向后，严重掉帧主要发生在区块加载期间。尚未取得匹配条件的阶段计时，不能直接认定双向遍历、四边形配对、CPU 构建或 GPU 构建是原因。

使用者于 2026-10-12 暂停该调查。既有文档整理、启动与源码线索保留；后续客户端、计时和实现须等待重新授权，其他性能实验继续暂停。

调查顺序：

1. 固定当前源码、实际加载的原生库、模组组合和设置；先记录当前默认双向模式的加载现场。
2. 区分已有区块读取、新区块生成、客户端网格构建和有效几何发布，记录队列积压、真实帧耗时及资源趋势。
3. 追踪数据到达、Java 准入与编译、原生入队与打包、GPU 上传与 BLAS 完成、场景发布；CPU 记录命令不等于 GPU 完成。
4. 检查双向切换与拓扑修正新增的配对、面分类、索引准备、描述符及 AS 工作，先确定成本再修改。
5. 必要时在同一产物中用重启后的来源面规则作定向对照，固定场景和设置；该诊断对照不改变默认双向产品决定。
6. 确认缺陷后修复直接相关路径并增加行为回归，分别报告加载吞吐、交互延迟、真实帧耗时和内存成本。

不减视距、画质、动画频率或 PT 场景几何，不恢复世界后置光栅，不以全局 GPU idle 或关闭功能作为正式修复。自然发生设备丢失时保全证据并停止当前用例，不自动反复重启。

## 已登记的源码缺口

以下来自[最近静态核验](../research/investigation-current_rendering_facts.md)，本次迁移未修复；进入对应修复任务前核对当前实际调用链。

| 工作 | 需要闭合的边界 |
| --- | --- |
| DLSS RR 释放失败 | 保留未成功释放的 viewport 与分配身份，避免失败后丢失所有权；不破坏首次错误与关闭合同 |
| Streamline viewport 编号 | 增加耗尽保护，明确失败行为；没有证据表明已经发生编号回绕 |
| 云的材质颜色 | 排除来源的光栅面亮度，保留真实材质颜色；与完整云重写分开处理 |
| 粒子发光来源 | 不从 packed-light 推导材质发光；保留真实材质或绘制状态提供的发光语义 |
| 遗留配置读写 | 核对五个遗留字段与管线配置的权威关系，避免重写时静默丢失已有值 |
| Flywheel 材质 | 保留来源 cutout 阈值及各材质的 blur/mipmap；同纹理不同采样状态需独立验证 |

Flywheel 的来源阈值与采样问题见[直接生产者到消费者核验](../research/investigation-raster_parity_and_ponder_retirement.md#fan-translation-recheck-no-special-case-but-generic-material-gaps)。不按鼓风机、弹簧或纹理名称特化。

## 渲染语义与视觉待办

### 双向几何与未来成像规则

当前双向基线及来源面状态的可恢复性已实现；不再把旧的光子方向规则或逐查询单面方案列为当前修复合同。翘曲反向四边形的一致拆分已有实现，弹簧真实扭曲姿态、薄片、水面、镜像、第一人称和船内等完整视觉验收仍有边界。

后续成像与完整 PT 参与分离、反射的可见性政策和未来单向贴图实现仍处设计阶段。先确认反射政策，再写统一属性、射线角色、历史与合同测试；不以默认双向实现代替这些未来功能。

依据：[设计方向](../research/investigation-path_tracing_design_principles.md)、[拓扑与双向实现证据](../research/investigation-pt_visual_and_performance_investigations.md#2026-10-01-topology-and-bilateral-world-implementation-evidence)。

### 结构图解、手杖与光栅翻译

- 保留原版图解内容选择、渲染偏好、纸张配色、轮廓与淡出；取消低分辨率像素格及原版帧率上限的决定不变。
- 弹簧在结构图解内的 cutout 与受力染色已有使用者通过反馈，不重新列为未修复问题；扭曲姿态及独立扭簧模型的视觉边界分别保留。
- 继续核对结构图解皮搋子绳端点、透明重叠、纸张边缘、裁切、GUI 缩放、资源包覆盖和附加区段几何。
- 手杖锁定图标、拖拽波动线及皮搋子连接端已有捕获与变换修复；双手、快速移动、反射、优先级和原始发光语义仍需对应视觉证据。
- UI3D 与 Vulkan 光栅的完整 OpenGL 等价性未成立；按相同输入比较状态、着色器、附件、颜色、alpha、深度、排序和采样。

依据：[光栅与结构图解研究](../research/investigation-raster_parity_and_ponder_retirement.md)。

### 材质、透明与环境

- 调查 labPBR 掠射角全黑，参考 Sundial；区分法线解码、TBN、镜像与阴影终止，不加入亮度下限掩盖问题。
- 原版云重写尚未实施；先定义云设置、资源包、运动、光照、雾与资源生命周期的合同。
- 夜视、潮涌、黑暗、状态效果雾、恶心与视角晃动矩阵顺序，以及 Veil 后处理的 UV/alpha/HDR 等价性保留待验。
- Veil 水下纹理“消失”的报告已经撤回；纹理存在但部分场景很淡，不据此提升不透明度。
- 保留普通半透明与物理透射的不同语义；透明分层备选方案、IOR、介质栈和距离吸收仍是独立研究事项。
- NRD 命中距离与直接可见性、阴影柔度、天体轨道与角半径、不可见光源方块和独立发光控制仍需相应合同与验证。

依据：[材质与环境研究](../research/investigation-pt_visual_and_performance_investigations.md)。

### FG 与屏幕效果

真实分数 alpha、局部反色重放及覆盖率加权 HUD-less 模糊已有实现和限定自动证据。普通 UI、抗锯齿文字、暗角、模糊、UI 后再模糊及未混合绘制仍需实际组合与生成帧动态视觉验收。

验收前确认客户端焦点、SDK 实际启用插帧并产生生成帧；真实帧输入捕获、失焦画面或 SDK 停止插帧的结果不能代替该验收。保留绘制顺序、颜色空间、覆盖率及资源有效期。

依据：[FG 与神经渲染研究](../research/investigation-nvidia_frame_generation_and_neural_rendering.md)。

## 故障、同步与运行边界

- GPU 设备丢失根因、完整 G3、长期稳定性与跨 GPU 行为未闭合。历史错误传播、世界保存和 native close 只支持对应进程与产物的结论。
- 历史 Vulkan 屏障、两个短缓冲拷贝写后写冲突及顶点/索引 usage 报告需要核对当前来源和已修范围，不当作已证明的驱动崩溃根因。
- 初始化失败、重复关闭、资源重载、异步呈现、世界切换与设备丢失组合仍保留专项验收边界。
- 排查时优先利用默认关闭、固定容量的诊断；不修改 TDR、重置驱动、制造无效 GPU 访问或在健康设备忙碌时伪造 lost 后销毁资源。

依据：[故障历史](../research/investigation-crash_and_runtime_investigation_history.md)、[原生研究](../research/investigation-native_renderer_investigations.md)。

## 暂停或候选的性能工作

所有性能实验，包括区块加载退化调查，均已暂停，恢复需要使用者另行授权。已有优化不能仅因原型存在就默认扩大或开启。

- 持久模型：先分离每实例 light/overlay/color 与稳定几何；再按测量决定 actor 级姿态及粒子、程序化生产者适配。保留原 renderer 回调、动画、历史和正确回退。
- Flywheel：远处或屏幕外动态内容的更新频率与完整 PT 参与仍需明确；不静默采用相机剔除或动画降频。
- 区块与场景：稀疏有效集合、GPU 常驻场景记录、增量元数据、锁范围、成本预算、稳定原点与 arena 整理均需新测量支持。已有背压、交互小批次、公平性、代际及批次完成票据不是待重新实现的方案。
- 菜单显存：世界卸载释放已有修复；首次菜单分配下限、缓存块保留和延后世界执行资源分配仍为单独提案。
- PTLAS、SER 与 continuation queue：硬件能力不证明收益，先取得对应 GPU 子阶段与整帧关键路径证据。
- 机械绘制盘点：连接静态清单与实际生产者状态，保留 UNKNOWN；现有三个版本基准不自动证明相同算法、几何覆盖或逐优化收益。

依据：[持久场景研究](../research/investigation-draw_intent_and_persistent_scene.md)、[盘点与测量](../research/investigation-render_call_inventory_and_benchmarks.md)、[显存生命周期](../research/investigation-vram_lifecycle_handoff.md)。

## 可选兼容与后端研究

Physics Mod、Modern UI、RenderPearl/Sodium 双后端仍为独立候选，不混入当前加载期调查。需要各自的版本合同、单一 Vulkan 设备与呈现所有权、生命周期和隔离验证；不捆绑或修改 Physics Mod。

依据：[兼容研究](../research/investigation-compatibility_investigations.md)、[后端研究](../research/investigation-renderpearl_sodium_migration.md)。

## 许可与发布

NVIDIA 等第三方运行库的公开二进制分发条件仍未取得确认。门禁只防止误发布，本地构建、源码同步和公开分发分别判断；不因 DLL 外置、动态加载或附带 LICENSE 宣称许可兼容。

依据：[第三方运行库审计](../research/audit-third_party_runtime.md)。

## 已完成、已封存与证据索引

- 迁移到单仓库、模板 1.2.1 对齐及 Windows CI 前置条件修复已完成；当前原生代码在 `components/native_renderer`，诊断在 `src/probe` 与 `components/render_diagnostics`。
- 原始审查的已修源码、背压与 PackType 资源隔离、纹理代际、资源退休、原生错误边界和历史 G0/G1/G2 保留，不因新任务重开或写成全系统验收。
- Ponder PT 和一次性独立场景回放已封存；当前 Ponder 使用默认光栅语义的 Vulkan 翻译，不重新启动 PT、截图替代或回放产品。
- 发光地衣正反面 UV 的差异已接受为原版模型行为，不再列为修复任务。
- 迁移前台账与审计原文位于 `D:/Workspaces/Artifacts/Radiance/history-archive/Radiance/docs/` 和 `D:/Workspaces/Artifacts/Radiance/history-archive/MCVR/docs/`；详细历史路线图可从本次整理前的 Git 版本读取。
- 既有研究记录保留观察日期、失败和限制，不事后改写；构建、自动测试、运行、视觉、性能和 GPU 根因分别记录。
