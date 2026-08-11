# Create 持久来源与普通方块实体交接

2026-10-28，本轮在当前未提交工作区完成 Flywheel 脏实例队列、Catnip 局部模板、世界受光剥离、专用后端及普通 BER 交接。构建与自动功能回归通过；没有性能实验，动态画面仍由使用者验收。

## 问题

此前 Sable 的共同交接保留区段、局部模型与 Flywheel Visual，却没有统一保留普通 BER 最近的可消费版本。物理组装器的杆子使用 Catnip 缓存模型，并非 Flywheel Visual；同一类入口还包括原版 BER 和混合 Visual／BER 的补充表面。不能把“注册了 Visual”误解为可以删去整个 BER。

Flywheel 模型已经可复用，但适配器仍遍历模型／实例查找变化；Catnip 的缓存几何仍逐次展开。世界 PT 也不需要 SBB 固定方向光、逐顶点受光采样，以及已失去消费者的 Flywheel shader-light／embedding 受光传递。配置偏好、实际后端和显示模式还需要一致。

## 方法

读取固定依赖的 Flywheel 1.0.6、Create 6.0.10、Ponder 1.0.82、Sable 2.0.6 及航空学组合 1.3.2 源码，核对实际缓存构造、renderer／visualizer 分工、材质与 UV 算法。具体版本仍以 Target 事实和输入清单为准。

冻结配套产物与源码，最终身份见 `validations/conformance-takeover/result/adapter-source-20261028-b/candidate.json`；本体复用 A 的完全相同 JAR。两仓库分别执行 `spotlessApply check verifyRelease verifyCompliance --strict`，并执行 Native GPU 合同；功能会话使用项目内隔离实例、1920×1080、静音及轻量 Aftermath，未操作外部游戏。复用 K 的自有管线缓存只是缩短功能启动，不作为冷启动或性能证据。

功能命令如下，从 Radiance 根目录执行；若占用超出软上限，先按存储约定清理可再生副本。

```powershell
py -3.14 -B -X utf8 components/render_diagnostics/benchmark/world_materials.py handoff adapter-source-a versions/1.21.1-neoforge/build/candidates/adapter-source-20261028-a/radiance-1.21.1-neoforge-0.1.5-alpha.jar --extra components/Radiance-Extra/versions/1.21.1-neoforge/build/candidates/adapter-source-20261028-a/radiance_extra-1.21.1-neoforge-0.1.0-alpha.jar --reuse-cache validations/conformance-takeover/result/world-materials-134/sable-handoff-k-handoff-20261027-212735/owned-cache.zip
```

## 发现

### 实现

- Flywheel 模型按来源身份缓存，新模型单独排队上传；实例的创建、变化、删除、可见性、steal、交接与原点变更加入脏队列。普通帧不再扫描所有实例寻找 dirty。真实动画、参数旋转／滚动、embedding、crumbling 和来源释放规则继续执行。
- Catnip 的确切不可变 TemplateMesh 通过公共 `LocalGeometry` 接口建立局部几何。姿态、RGBA、UV 仿射及 overlay 独立提交，缓存未命中才展开顶点。自定义／可变模板、非仿射姿态或其他不合资格消费者继续原执行；弹簧、绳索、流体等真实变化的程序化内容没有被强制静态化。
- PT 的 Catnip 路径不再乘入固定方向明暗，也不执行无效逐顶点世界受光查询。Flywheel 移除无消费者的 shader-light 上传、embedding 受光回调传输和空 Native render 调用。纹理、源颜色、受击覆盖、源材质及物理发光能力保留；不从 UV2 满亮推断新的发光。Ponder／GUI 光栅预览保持原始照明路径。
- `BlockEntityPublications` 只借用当前已经生成的批次，给明确交接保留最近发布，不每帧复制或 hash 顶点。替代普通 BER、Visual 和区段共同就绪后发布；正常求值优先，本帧未求值才补交，避免重复绘制。字体页退休会撤销旧文字批次与驻留选择，重载／卸载释放租约；容量回退的局部网格按需复制保活。独立选择提示与破坏请求仍遵守自身当前帧寿命，不缓存为旧世界表面。
- 正式后端为 `radiance_extra:instancing`。激活时配置读取、实际选择、命令建议和重载遵守同一模式；未激活时保留 Flywheel 原选择。测试区分激活／未激活翻译规则，实际会话报告同一后端 ID。

### 实际证据

两仓库完整检查与发行核验通过，严格合规均为零失败、零警告。Native 非 GPU 79 项与 GPU 24 项通过。Java 主测试 308 项、JNI 4 项、probe 49 项通过；bootstrap 保留既有跳过项，Extra 111 项通过。最初 Python 3.15 缺少诊断依赖的环境失败和测试夹具编译错误已修正，用已有 Python 3.14 重新验证，没有降低检查标准。

会话 `validations/conformance-takeover/result/world-materials-134/adapter-source-a-handoff-20261028-003452` 通过装配、返回世界、合并、分离、父结构消失、直接删除与多次资源重载。追加物理组装器、杜鹃钟和文字告示牌；普通 BER 可消费发布的最低数量为 1，交接最终无积压，重载后均继续存在。这个计数证明出版物门槛和来源路径，没有把它当作逐像素可见性证明。

90 帧稳定窗口内，Catnip 请求／接受计数由 260 增至 710，定义始终为 5 份、136 顶点；Flywheel 模型生产／上传始终为 3／3。Flywheel 实例 writer 仍由来源触发了 20 次变化处理，没有宣称零实例更新或零来源动画求值。没有记录或推算 FPS 收益。

原会话 `finalHandoffs` 字段位于先行 Flywheel 子阶段；整场收尾以 `handoff-trace.jsonl` 末段及普通 BER 重载结果核查，不把早期字段当作全部操作的最终总数。截图及文本证据已保留，客户端环境在结论记录后送回收站。没有设备丢失，没有自动故障重试。

### 边界

会话后自审进一步将包装消费者的受光剥离限制在主 framebuffer 与默认世界阶段，防止世界事件内部的独立 FBO 预览被改写。最终 Extra B 重新通过完整构建／回归／发行／严格合规；没有重跑整个客户端会话。上面的实际计数属于 A；B 只收紧非直接 PT 消费者的回退域，PBR 直接请求和 RasterPreviewScope 行为不变，不把 A 的整场运行记成 B 的实测。

模板均匀颜色改为 GPU 浮点相乘，避免源端重复展开；相较来源每次乘色后转八位整数的路径，允许不超过一个八位颜色单位的舍入差异。颜色、透明度、overlay、UV 映射和法线有定向测试，非平凡 UV 包含贴图范围外坐标。没有降低几何或材质精度来换性能。

本轮实际运行覆盖所列夹具，不宣称已人工验收全部注册 BER、所有源程序、Ponder 场景、透明层或极端容量回退。未激活后端的分支有自动合同检查，没有专门启动一个关闭 Radiance 的客户端。各类 source renderer 的必要回调及真正形变仍然存在。

## 结论

稳定几何现在能在来源适配边界复用，而非仅由 Native 缓存最终顶点；普通 BER 的交接也接入已有共同发布机制。候选已具备交付条件，性能结论及动态目视结论单独取得，不扩大为全项目接管完成。

已将配套候选投放至 Prism 的 Radiance 1.21.1-neoforge 实例，本体及 Extra 均与候选 SHA-256 一致；回执为 `validations/conformance-takeover/result/adapter-source-20261028-b/prism-deployment.json`。其他模组、配置与存档未改动，没有启动游戏、提交或推送。清理继续保留当前／最近验收版本、源码和唯一证据，原容量上限恢复执行，可再生输出及没有文档引用的过时试验包按校验记录送回收站。
