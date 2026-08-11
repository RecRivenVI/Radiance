# Sable 局部模型复用与几何交接

2026-10-27，修复 Sable 单方块逐帧展开，以及主世界、sublevel 和单块／区段表示之间的几何交接缺口。配套候选为 API 8，身份见 `validations/conformance-takeover/result/sable-handoff-20261027-e/candidate.json`；功能自动验证通过，连续视觉待使用者验收，没有性能测量。

## 问题

此前单方块结构逐帧调用来源 renderer，生成完整顶点并进入动态几何。区段成员删除与表示替换则立即撤下旧几何，没有将替代网格的 GPU 完成版本作为同一交接的门槛。共用区段编译流水线本身不能补上跨来源的发布协调。

## 方法

读取实际 Sable 2.0.6 JAR 字节码，结合留存的 2.0.5 源码核对跟踪包、Finalize、拆分姿态、表示切换和关闭；没有将留存源码误记为当前依赖。沿用局部模型、共同空间、区段编译与帧退休能力，不新增另一套渲染算法或固定帧延迟。

本体与 Extra 分别执行 `spotlessApply check verifyRelease`。本体包含 79 项非 GPU 原生检查，另执行 24 项 GPU 回归、JNI 头核对及 Java／探针回归；Extra 执行其 Java 回归。严格合规收尾另行记录。用户本轮暂停工作区容量限制，验证仍限 8 GiB，外部目录经单独批准临时为 9 GiB。

冻结配套发行包后执行：

```powershell
py -3.14 -X utf8 components/render_diagnostics/benchmark/world_materials.py handoff sable-handoff-e versions/1.21.1-neoforge/build/candidates/sable-handoff-20261027-e/radiance-1.21.1-neoforge-0.1.5-alpha.jar --extra components/Radiance-Extra/versions/1.21.1-neoforge/build/candidates/sable-handoff-20261027-e/radiance_extra-1.21.1-neoforge-0.1.0-alpha.jar --reuse-cache validations/conformance-takeover/result/world-materials-134/sable-handoff-d-handoff-20261027-192045/owned-cache.zip --workspace-limit-suspended --external-limit-gib 9
```

运行仅用项目内 1920×1080 静音实例，通过真实 Sable／Simulated API 建立结构、改变方块、拆分、返回主世界并重载。显式复用本项目先前正常退出会话的管线缓存，不清理全局驱动缓存，不据此评价启动性能。

## 发现

### 局部模型与来源状态

`LocalModel` 保存不可变局部网格，`Instance` 保存独立历史。Extra 复用原版适配器已有的简单、加权和组合模型检查，保留实际种子选出的面、图层、模型数据、方块偏移、染色及染色发光值。输入未变时不重新展开；相同实际定义共享资源，各结构选择实例并更新父空间。不按方块名称或 renderer 类缓存上一帧最终顶点。

未知模型继续实时细分；非 MODEL 方块不构建空模型，其 BER 继续执行。图层顺序沿用 Sable 的地形图层遍历，细分保留来源 RNG 的采样顺序。重载后旧定义、实例绑定及区段登记分别失效。

### 替代版本共同发布

`SectionHandoff` 收集主世界与外部区段，重叠或关联的交接合并。旧活动几何保留，新完成的几何／空版本暂存；全部关联收集完且当前请求版本就绪后，在渲染线程共同发布。旧构建不能满足较新请求，确认空也是合法完成。新局部模型利用同帧既有 BLAS／TLAS 命令依赖，不增加 GPU idle。

主世界目标重新请求当前内容，不用转移前的已发布版本冒充就绪。拆分连接父结构的交接；返回主世界时覆盖末次插值和逻辑姿态边界。卸载取消与实际移除分别处理，旧外部句柄在交接后释放，在途引用沿用原退休机制。

### 资格核查与受控失败

早期候选分别被未登记的 Flywheel 注入、生产者统计探针、Ponder 只读访问器及 Extra 缓存访问器拒绝。失败均保留，未投放，没有吞掉断言或将退回统计当成优化成功。

按实际字节码和精确 SHA-256 登记 Flywheel 1.0.6 只处理其专用 `NeoforgeMeshEmitter` 的钩子、Ponder 1.0.82 纹理管理器只读访问器、Extra 图解缓存访问器；没有按模组名整体放行。仅正确性会话停用改变来源资格的生产者统计包装，保留运行驱动、功能断言及故障取证，探针选择有独立回归。最终会话保存完整加载变换清单。

### 实际功能结果

最终会话为 `validations/conformance-takeover/result/world-materials-134/sable-handoff-e-handoff-20261027-192720/`：

- 两个相同金块结构共享一份定义；稳定窗口构建数 1→1，共享命中 1→1，实例选择 180→360，动态退回 0。
- 完成单方块→多区段→单方块、实际拆分、返回主世界、重叠交接和 F3+T。11 次交接开始、11 次完成、0 项积压，结束有两个活动结构。
- 实际模型包含 `WeightedBakedModel` 和 `SimpleBakedModel`。重载后累计构建 4 份定义、共享命中 2 次、动态退回 0 次。
- 报告通过、退出码 0、Native 正常关闭，没有设备丢失或 GPU 转储。复制环境校验归档后回收，六张截图保留。

截图确认稳定单块与最终结构存在，但抓取可能落后 CPU 发布一帧，不能证明所有交接帧均无闪烁。计数证明实际路由及门槛，不能转换为 FPS 收益。

## 结论

稳定单块已进入局部模型与持久实例路径，已知表示转换接入共同发布。新旧版本短时共存是连续性的资源代价，本轮没有测量峰值或性能改善。

人工重点观察原场景的建立、拆分、回收、单块／多块切换、运动、材质和 F3+T。没有为任意自定义 BER 冻结历史快照；跨网络延迟、非单位动态缩放、任意远距离服务端搬移和全部第三方组合未独立触发。来源没有通用跨主世界事务身份，本轮依赖现行跟踪包、来源关系和目标空间范围，不宣称任意服务器扩展也有原子交接保证。

Core 与 Extra 必须配套更新。Prism 只替换两份 JAR，实际哈希与状态以候选清单关联回执为准；不启动游戏、不改其他模组、配置或存档，不提交或推送。
