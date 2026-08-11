# 共享表面函数与总回归验证

2026-10-17，阶段 D 族 10 的迁移与自动门槛记录。客户端最终流程单独在合并研究中记录，不以此文代填视觉或性能通过。

## 问题

同名函数未必同语义，提取共同正文不能改变资源上下文；同时需要检查贯通前几族之后的实际调用仍执行新合同。

## 方法

固定迁移前 117 组同名清单，逐读 41 组异体及资源绑定。对十个共同解码正文做别名规范化影子比较，八份历史正文和 celestial 数学先证实相同，再抽取；结果保存在 `D:/Workspaces/Scratches/RadianceSemanticsAudit/d10-extraction-proof.json`。GPU 直接调用生产解码、颜色、丢弃、天空数学与历史资格函数；实际 RenderType 工厂验证混合因子，CPU 并发行为测试验证拒收诊断上限。

总回查新增已知裁切程序被误导性名称归为 WORLD_SOLID 的反例，改为已登记来源政策决定硬件 opaque 资格；仍能证明 opaque 的来源保持快速路径。FSR 的时间链进一步暴露输出失败仍被消费的问题，先写[专项调查](investigation-upscaler_output_failure.md)与成功／失败／异常分流回归，再迁移实际 FSR/XeSS 调用，不注入客户端故障。

## 发现

- 第一次完整门槛失败：两个 ABI Python 文件使用 CRLF，严格合规失败；一个旧源码字符串护栏仍寻找已迁入来源 blend 助手的旧返回语句。修正换行和护栏定位，新增实际 blend 工厂行为测试，不删除检查。
- 第二次原命令重测，非 GPU 67/67、严格合规通过，GAME 292 中一项旧 ASM 护栏仍在委派者寻找返回 10。改为验证真实委派，再在实际 blend 函数检查该返回值；原有 CRUMBLING 状态与路径检查保留。
- 第三次原命令 `spotlessApply check verifyRelease verifyCompliance --strict` 成功，日志 `d10-full-gates-3.log`：GAME 292、JNI 4、probe 28，无失败或跳过；bootstrap 13 中 11 通过、2 跳过；原生非 GPU 67/67。严格合规失败 0、警告 0。现有 SDK/CMake、Unsafe 与原生编译器警告仍保留在日志，不称为所有工具无警告。
- `:native_renderer:gpuTest` 实际使用本轮 Gradle 构建的 `build/v`，16/16 通过，无跳过或设备丢失。`d10-gpu-prepare.log` 固定实际目录，避免把旁边的历史 CMake 目录当本次产物。
- 重新执行实际 Java 12 组 canonical/compact 夹具，当前 `build/v` 的 CPU 解码及 GPU 转换、不可变批次和 compute→BLAS 通过。OBS/RTSS 层 API 版本提示如实保留；该独立夹具不证明游戏全部同步正确。
- 两套实际 pack 的 232 个入口／执行变量组合编译成功；232/232 通过 Vulkan 1.4 SPIR-V 检查。固定默认执行变量不覆盖任意用户配置。
- 最终位置检查保留旧浮点归一化的有限大法线行为：有限分量的平方和溢出并不自动等于最终位置非有限。补 1e20 反例，纠正迁移中过强的中间量限制；零、NaN/Inf 的实际启用法线仍拒绝。
- 拒收报告最多 128 个键及固定长度文本，只在拒收入口加锁。合法绘制没有该锁。可选透明线使用实际 ADD、LIGHTNING、GLINT 因子，默认不透明与零 alpha 分隔规则不变。
- FSR 只在真实 FFX Dispatch 接受后消费 SDK 输出，未初始化／失败使用原有 blit；FSR/XeSS 失败使该 view 下一帧历史失效，fatal 原错误先传播。捕获不再用假的 NGX 成功值代表 FSR；SDK 接受仍不等于 GPU 完成。

## 结论

族 10 的同语义函数已共享，41 组异体逐项保留理由；没有合并两套主追踪或擅改水、云、height trace。总回查补充的来源 opaque 资格和 SDK 失败边界已进入行为回归。检查点 10-surfaces 为相对固定 HEAD 的完整累计差异，不是本族增量。

构建发行 JAR 与实际 DLL 身份固定在 `D:/Workspaces/Scratches/RadianceSemanticsAudit/bcd-final-artifact.json`；最终客户端、完整源快照、合并目视与待决定事项由当前计划及合并研究记录收口。本轮无性能结论、Prism 操作或 Git 写入；未知来源严格接收与全亮物理贡献仍待 adr-5、adr-6。
