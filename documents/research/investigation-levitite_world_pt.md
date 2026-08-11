# Levitite 世界 PT 适配

2026-10-30，按航空学 1.3.2 的实际源程序建立 Extra 世界表面适配。本轮不做性能测试，源码、自动验证与使用者目视验收分别记录。

## 问题

旧入口只保留 `aeronautics:levitite` 与 `_ghosts` 的区块层，世界 PT 没有专门解释其细分、速度、噪声和颜色意图。旧 Veil 绘制拒收属于这里的历史缺口，不能据此认定以前所有偶发崩溃根因相同。

## 方法

来源由使用者指定的本地留存源码核对：`D:/Workspaces/References/minecraft-references/aeronautics_bundled-1.3.2/src/aeronautics/`。重点为 `AeroRenderTypes`、`LevititeShaderManager`、`levitite_utils.glsl`、`levitite.vsh/tcsh/tesh/fsh` 与 `data/sable/tags/block/always_chunk_rendering.json`。不下载其他版本，不修改来源。

Extra 取得平台已发布的只读特殊区段层，按发布版本建立模型。主体固定细分模型与颜色场分离；重影保留同样的四级细分及四边形索引模式，更新当前形变、颜色与透明度，复用来源侧存储。Native 沿用动态几何的历史、构建和退休，不把动态 BLAS 或索引上传称为已经持久化。

两种水晶均被来源标记为始终区段渲染，实际单方块也走区段；不另建推测性的单方块备用实现。主世界与 Sable 使用同一材质解释，结构姿态复用公共父空间。来源原有 SOLID 层仅为深度预绘制，在世界 PT 排除，界面／图解离屏消费者维持各自路径。

## 发现

主体不形变。其噪声幅值按原始方程求出，纹理与真实来源颜色相乘后再执行 HSV 指数变化；不能只用固定 RGB 乘数近似。重影先按每个控制点的局部速度偏移，再细分、执行噪声及速度投影，正负两层分别偏色。速度复用来源管理器的前后平滑状态，重力和快慢材料矩阵从来源配置读取，时钟与原式相同。

世界表面剥离原 lightmap、固定明暗和屏幕雾，不由满亮值制造发光。重影采用非折射覆盖半透明，仍接收及影响世界传光。屏幕深度渐隐改为接近结构局部方块和主世界方块表面时的空间淡化；普通 PT 遮挡继续处理实体与其他几何。这是为反射／折射统一而作的表达转换，并非逐像素复刻屏幕深度。它不查询所有第三方实体的视觉表面距离。

公共颜色程序只解释有界的颜色参数及来源标量场。显式 ABI 标记防止普通顶点误入；Advanced 缓存保留相应浮点场值。没有执行任意 Veil 世界程序，没有增加世界后置光栅。

## 结论

Native 80 项 CPU 测试、25 项 GPU 测试通过；新增 GPU 用例检查 HSV、正反色相、指数及减色。两仓库 `spotlessApply check verifyRelease` 通过，实际发行包的 268 个着色器变体通过编译。Extra 126 项测试包含四项来源公式回归。

首轮隔离运行通过主世界、一个方块与三个方块两种结构、正反注入速度、稳定模型窗口、玻璃后画面和资源重载。两结构实际均为 `VanillaChunkedSubLevelRenderData`，与来源标签一致。探针注入管理器速度以覆盖形变分支，不将它当作实际物理动画或最终视觉验收。最终候选与后续运行结果在交付记录补充。

最终配套候选为本体 `73491785…`、Extra `9f8f373e…`，身份、源码快照与相符 PDB 在 `versions/1.21.1-neoforge/build/candidates/levitite-n-20261030/candidate.json`。Native DLL 为 `41af5eac…`，CodeView age 19，与保留 PDB 一致。

最终 Vanilla PT 和实际 Advanced 两次均正常退出、稳定窗口不重新定义模型、重载恢复，无崩溃、设备丢失或转储。证据分别为 `validations/conformance-takeover/result/world-materials-134/levitite-final-levitite-20261030-184006/` 与 `levitite-advanced-confirmed-levitite-20261030-184556/`。

早一次标记 Advanced 的运行因未开启其必需发光收集而自动回退 Vanilla PT，已另写 `algorithm-disposition.json`，不能计入 Advanced 通过。运行器现在设置该算法的先决项，并核对退出时实际选择；真正 Advanced 的 `algorithm-result.json` 确认请求与实际一致，日志包含 primary、visibility、direct_light、cont_reflection 和 cont_refraction 管线。该更正不用于改写历史功能结果。

客户端环境均已在证据归档读回后回收。当前候选待使用者观察真实速度、角速度、重力／材料变化、细微颜色闪烁、反射／折射及空间交界淡化；没有 FPS 或性能收益结论。

已投放上述冻结的配套候选至授权 Prism 实例，只替换 Radiance 与 Extra，旧包进入回收站，新包哈希与实测候选一致。未启动 Prism，未提交或推送。
