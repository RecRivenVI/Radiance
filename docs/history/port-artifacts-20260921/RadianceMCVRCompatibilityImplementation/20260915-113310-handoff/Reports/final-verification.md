# 工程验证与交付边界

当前有效阶段是用户本人明确授权的实施阶段。最初只读交接已结束，goal 为 active；工程收口完成后由用户进行 A/B 真机目视。历史 blocked/只读回复不能否定本次源码修改、构建、测试和代理工作。

## 当前源码覆盖

保留两个上游仓库结构及全部既有未提交成果。补齐真实 Flywheel Vulkan backend 选择、实例/embedding与scene光照、公共主/离屏目标、持久buffer、shader/uniform/reload、world/GUI路由，以及固定 Create/Ponder/Sable/Veil/Simulated/Aeronautics/Offroad 消费者。

本次恢复后额外闭合：custom ParticleRenderType.begin 的真实纹理与状态；Sable错误传播；普通与外部section的持久副本及PBR128→BLOCK32格式转换；Levitite/ghost真实shader与extra-layer；EndSea原shadow/group及异常恢复；shadow→sea→post时序；普通破坏覆盖乘色。固定staff实际光束使用Ponder outline，未使用的staff_overlay缺失资源未伪造。

独立审查核对原dispatcher链，确认相机原点变化仍触发原Flywheel manager recreateAll，无需重复重建。另发现scene光照法线偏离固定Sable语义，已改为保留实例/model法线，仅用scene矩阵改变采样位置；最终shader与Release应以修正后记录为准。

MC-04.2 已有实现纳入最终源码：一般debug emission0，F3+G独立scope emission1，F3+B及其他既定视觉目标保留。原第一批诊断清理没有撤销。

最后只读复核确认 NeoForge 模组图标的匿名 DynamicTexture 虽覆盖普通 tracker，仍由 NativeImage/bound-texture 恢复路径上传；固定完整组合未找到自定义 renderSky 注册实现或实际 SKYBOX 生产者，见 icons-dimension-consumers.md。未把假设中的额外消费者扩大为本次实现。

## 验证矩阵

| 层次 | 证据 | 结论范围 |
|---|---|---|
| Java + bootstrap | final-java-test-matrix.json；final-source-verified-build.log | 120项：118通过、2硬件窗口测试跳过，0失败。含实际单元/合同及固定字节码检查，不能全部称GPU或游戏测试 |
| JNI | final-jni-exports.json；native JNI coverage CTest | 161个生成头文件符号均在生产DLL导出；不代表所有JNI方法已在游戏执行 |
| native Release | native-final-build.log；final-reviewed-release.log | core与自动化测试目标构建；最终运行资源由prepareRuntime安装 |
| native测试 | native-reviewed-tests.log | 24/24，包含headless Vulkan framebuffer/custom array/tessellation等fixture及CPU/源码合同；各自范围见CTest记录 |
| 内置shader | builtin-scene-normal-compile.log；BuiltinPacks-final/results.json | 两内置包237/237真实SPIR-V静态编译，含最后scene法线修正 |
| 固定外部shader | simulated-shader-consumers.md；veil-reload.md | 六组12stage及实际float[6]数组shader离线编译，不能代替运行期所有variant |
| 单JAR | final-reviewed-release.log的verifyDistributedJar/verifyRuntimeResources | SERVICE/GAME隔离、GAME内容hash、native与shader/runtime、DLSS文件/许可与单JAR结构检查 |
| 合法依赖 | dependency-ranges.tsv/result.log；final-artifact-hashes.json | 固定依赖范围检查、四外层及嵌套JAR哈希；没有编造上游发布commit |
| Git/源码 | final-git-state.json；final-source-freeze.json | 两仓branch/HEAD、index与接管基线相同，staged为空，diff-check通过；冻结Git可见源码和资源 |
| 运行与画面 | manual-acceptance.md | 待用户。代理没有启动客户端、交互GL窗口、UI自动化或修改用户Prism/存档/资源包 |

所有 Evidence 路径相对本报告父级的 `../Evidence/`。较早失败日志保留：sourcesJar依赖声明、Copy初始化空mods目录、普通RenderType测试缺Loader。前两项已修复并复跑；第三项测试改为固定真实字节码合同，未造假Loader、未跳过失败来宣称通过。

## 兼容与验证限制

- 支持范围是清单固定版本及已核可达消费者；任意新模组、未知shader/顶点布局、额外GL扩展、没有实际消费者的任意SKYBOX或自定义世界目标，不宣称全兼容。已识别不支持输入明确失败/拒绝，不能计成功。
- 实际Mixin合并/装载、完整modpack初始化、动态GPU生命周期、视觉/遮挡/时序/重载/退出仍需A/B验收；无窗口fixture不能证明这些端到端行为。
- Veil uniform承接已核标量/向量/矩阵与固定一维数组；任意宏表达式、struct/任意block或额外sampler数组不由本次编译证据覆盖。
- GPU不确定完成的资源保留路径有源码核验，未执行设备丢失/驱动失败注入；不能声称所有故障恢复已经运行通过。
- 两个硬件窗口测试按边界跳过；Debug完整链接仍有此前shaderc Release/Debug CRT限制，交付为Release。
- `captureCanvas`保留为已有生产native测试接口；未完成测试接口隔离，也未把该接口当游戏画面验收。
- Gradle 8.14.1报告面向Gradle9的deprecated feature，当前构建成功，未扩大范围升级构建系统。

18个工作包/52个兼容ID保留在workpackage-status.json，各包的源码整合、工程验证与待人工状态分别记录。历史remaining-matrix是调查快照，其旧“未开始/待编译”需结合当前索引及本报告，不作为当前最高权威。
