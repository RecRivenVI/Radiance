# 颜色与覆盖率迁移验证

2026-10-17，阶段 C5 的定向回归与隔离功能验证；首次失败、取证、修正和重测分别记录，不把开发类启动冒充最终发行包验收。

## 问题

来源的 ColorModulator、显式封批、贴图丢弃阶段和原生表面缓存必须贯通；同一个 RenderType 在两次封批间可以合法改变状态。普通覆盖率、Stress 染色、文字覆盖和物理透射不能共用一个 alpha 含义。

## 方法

逐读实际 1.21.1 资源 JAR 的 55 个片元 shader、标准四种粒子 sheet、雨雪和世界边界的生产方法。实际源码语句及哈希保存在 `D:/Workspaces/Scratches/RadianceSemanticsAudit/c5-source-shader-inventory.json`。先让真实提供者的 endBatch 隔离回归失败，再改生产、JNI、CPU/GPU 编码、共享求值和缓存解码；使用真实 Java 记录、JNI 与 GPU 对照。隔离实例覆盖组合变换、镜像、混合宽度、取消、转相机、resize、资源重载和保存重进。

## 发现

### 明确缺陷与修复

- 原 endBatch 对普通捕获不封口，两个 draw 的同层消费者与状态会合并；现在保存真实批次快照。真实回调与异常清理均恢复调用方状态，不把合法动态状态误判为非法复用。
- 普通、事件及刚性实例缺少逐 draw 全局 RGBA；现通过拥有的值记录传递，刚性模型仍共享几何。JNI 校验版本、步长、长度和有限值。
- 旧 alpha 不能区分“先贴图裁切”与“完整调制后裁切”。实际工厂的 12 行旧/新影子表有 6 项已分类纠正，执行组不变。实体 cutout 保留来源 0.1 阈值与 surviving opaque，Spring 的 Stress alpha 不再被当作表面淡出。
- 四种标准粒子必须使用原始 sheet 的混合规则；地形粒子原本启用混合。carrier RenderType 不决定其原始剔除状态。雨雪使用粒子 shader；世界边界已有顶点 RGBA，不能再重复套全局颜色。
- Advanced 当前帧表面缓存丢掉几何身份后，计算 pass 无法取回 draw 颜色；缓存现携带受范围检查的精确整数标签，实例绑定声明计算阶段可读。无新增逐像素图像，既有格式不变。
- B9 后仍残留原生 ABI=1 的拒收判断，已改为共同版本决定者，并覆盖旧版、未知版和内部输入。
- 功能运行暴露字体查找缓存返回退休 FontSet。06:40:02 的真实 `FONT_LOOKUP stale` 显示返回对象与当前注册对象不同；06:40:26 的位图错误与先前 FontManager.apply 关闭同一图像对应。查找边界现核对对象所有权后重新走原版解析，不吞上传异常。它与已获手工通过的字体页旧绘制批次修复是两个层次。

### 失败与重测顺序

1. `c5-draw-boundary-red.log`：真实旧提供者没有执行封口，捕获次数 0，预期 1；锁定缺陷。
2. 首次编译遇到 private 工厂调用及继续编辑造成的旧生成 ABI；纠正后重新生成。一次排除 native jar 的快捷命令没有执行 Target 测试，不计通过。
3. 首轮完整定向检查的 Java 1 项因无 GL 上下文的测试直接执行状态回调而失败，改用该用例的最小状态注入；原生 3 项旧接口/字符串护栏同步到真实新入口。未降低实际行为断言。
4. `c5-final-gates.log`、`c5-final-java.log`：Java 285、真实 JNI 4、probe 28；非 GPU CTest 67/67，JNI 头文件一致。
5. `c5-gpu-final.log`：GPU CTest 16/16。实际 Java 12 组 canonical/compact 输入经真实 CPU/GPU 转换对照通过；GPU 求值覆盖裁切阶段、Stress 顺序、Flywheel 标志隔离及缓存标签边界。
6. 离线实际 pack 入口/定义组合 232 项经 glslang 编译、232 项经 spirv-val 通过；动态 execution 变量在此为固定默认值，只证明语法/类型/SPIR-V 有效，不能冒充实际所有配置调度通过。
7. 进程 49028 首次功能运行完成重载和保存后，在标题文字发生源位图错误；101180 的有界关闭来源记录定位 FontManager.apply；第三次加入真实查找身份观察，证明退休 FontSet 命中。三次均独立留存，不是没有新证据的循环重试，没有设备丢失。
8. 修复后 GAME 287、probe 28 通过；`FontLookupLifecycleTest` 对照实际 Minecraft 查找体及产品 HEAD 接缝。随后进程 101396 于 06:46:36 入世界、06:46:57 完成重载、06:47:22 保存返回标题、06:47:25 重进、06:47:33 正常停止。三个真实回调边界通过，字体违规 0、线条违规 0，4009 次来源回调；日志包含三维度保存、场景释放与 Streamline shutdown，Gradle 成功退出。

## 结论

C5 工程与定向行为验证通过，非最终视觉验收。功能配置为 1920×1080、主音量 0、默认轻量取证和基础 Aftermath；没有故障转储或新的 GPU 故障，不对历史设备丢失根因作结论。实际加载 DLL 路径经进程模块观察，构建、内嵌与提取的 core SHA-256 均为 `6B5D29740F1E2EB73BCABE71879CD98F55946AA834C91BF8696939E0EEC9FB14`，详见 `validations/regression-line_capture/result/c5-font-fixed/receipt.json`；该次为开发类启动，不是发行 JAR 安装启动。

来源计数为 JNI 前采集，不能单独当作 GPU 完成；GPU 行为由独立真实设备回归支持。Create 交互与全部第三方画面、实际弹簧受力、FG 生成帧动态外观仍需最后目视。未知自定义程序按 adr-5 保持现状。眼睛覆盖的独立调制继续在族 6 收敛；不称整个表面系统已完成。新增回调与字体注册表检查有 CPU 成本，未获性能授权，未测量。

首次文本在 `result/c5-first-failure`，两次取证分别在 `result/c5-font-trace-failure`、`result/c5-font-lookup-failure`，成功文本在 `result/c5-font-fixed`；外部日志同 `D:/Workspaces/Scratches/RadianceSemanticsAudit/`。重复运行库副本已回收，原始日志、源码与构建输入保留。各族检查点包含相对 HEAD 的完整未提交工作，不仅是本族增量。
