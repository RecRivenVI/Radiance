# 当前：A3配置重试也在同一驱动位置崩溃，原生根因仍未确认

2026-09-15 17:13:02启动的隔离A4使用同一去E-02 JAR/DLL/PDB，未加载Vulkan诊断层；用户直接报告未进行任何操作、主界面尚未加载即崩溃。应用约28.247秒后在`nvoglv64.dll+0xf1c729`发生`EXCEPTION_ACCESS_VIOLATION`，新6,328,407,108-byte转储保留在`Diagnostic/base/hs_err_pid73724.mdmp`。结果见`20260915-152956-handoff/A4-RESULT.md`。

按用户要求，A3配置重试于2026-09-15 17:37:16启动，Java PID `87964`、监控/父PID `73736`，run为`Evidence/run-a3-20260915-173716-468`。复用原A3 validation/sync validation配置和同一JAR/DLL/PDB；约25.512750秒后在`nvoglv64.dll+0xf1c729`发生访问异常，读取`0xffffffffffffffff`，新6,602,860,986-byte转储已保留。崩溃前有一条已知sync hazard，但尚未证明是AV根因；结果见`20260915-152956-handoff/A3-RETRY-RESULT.md`。不自动再启动、不扩展D12或同类fixture。R-01/R-02保留，E-02保持清除。下方历史检查点不覆盖当前阶段。

# 当前：E-02已清理，去实验诊断版已封存并部署，等待用户启动

用户明确要求先清除未经证实的修复。两文件实验hunk已移除，保留有证据的R-01/R-02；当前Products/diagnostic-e2-removed。标准validation/PDB/fatal转储保留以对照。编译和包内核验通过，未自行启动，原崩溃未解决。以下为历史检查点。

# 最新：E-02首次用户复现未出现原异常，根因未确认

run-20260915-143633-077约99秒，标准validation零VUID，世界保存/客户端Stopping；见e02-run-review.json。下一步拆分旧pool/sets与layout生命周期的无窗口复现。E-02整表保留仍是实验，尚未清理或交付正式修复。以下为历史检查点。

# 最新：E-02已编译封存，等待用户复现；未自行启动

当前用户授权允许扩大诊断并禁止自行启动交互游戏。最新台账DIAGNOSTICS.md、命令E02-REPRO.md；D-04已再次崩溃。原崩溃尚未定位，实验及诊断尚待清理，正式回归未完成。下方均为历史检查点。

# 当前检查点：D-04符号版诊断已启动，原崩溃未修复完成

2026-09-15 14:28；最新记录以PROGRESS.md的D-04章节、对应源码冻结及run-20260915-142838-387证据共同核对。以下R-02/D-02初始化状态仅为历史，D-02/D-03已经崩溃结束。

# 当前有效检查点：R-02 / D-02（已授权排查与修复）

最初只读交接早已结束。用户授权修复并启动实例；保留所有未提交成果，不进行UI自动化、stage/commit/push。根代理独占本次8个修复/测试文件，未调度代理。尚未完成原CPU驱动崩溃定位，以下历史R-01/E-01材料不能当作最新成功结论。

## 最新源码与验证
R-01修复D32S8双aspect及HUD附件布局契约。R-02修复世界→HUD深度交接：world命令虽晚录制但先提交，生产者统一导出SHADER_READ_ONLY_OPTIMAL；HUD不读取/恢复录制时仍UNDEFINED的CPU布局。保持原提交顺序，无额外等待、吞绘制或功能关闭。

R-02 Release core、安装、Gradle build/Java/单JAR验证成功，native24/24；framebuffer真实GPU测试在普通validation与GPU-assisted均0 VUID。增加消费者先录制、生产者先提交的双command buffer深度读回。仅证明这些受测路径，R-02世界实际运行仍待复核。

Products/diagnostic-r2/Radiance.jar SHA256：798BEFF493270D1FD2842AD223CE84EF1B83A0A46A6F81FA822B3B41DFE00E16
core.dll SHA256：01E1F7871FE99AFBEF25E2C610F818DE9EDD263D3F16DCB5539794B28174923F
包内DLL一致，8个源码/测试哈希在同目录source-freeze.json。历史正式A/B未覆盖；没有可宣称通过的最终正式产物。

## 游戏证据与当前进程
E-01仅屏蔽OBS/RTSS后曾进入世界，用户确认主菜单正常，但随后在PauseScreen文字绘制再现nvoglv64.dll+0xf1c708。该实验失败，不再作为修复方案。它暴露的新01198/09600由R-02处理，仍需游戏验证。

D-02当前run-20260915-140539-426，PID48008，14:05:39启动。恢复原OBS/RTSS层组合，开启标准GPU-assisted检查动态descriptor/shader访问，尚在初始化。图像操作完全由用户完成。下一动作读取本次首个错误/崩溃或确认实际启动；如仍CPU异常而GPU检查无证据，再选择有界标准原生取证，不预设descriptor/UBO原因。

## 差异与清理门槛
正式修复仅R-01/R-02及必要GPU夹具修正。无新增业务诊断日志、探针、计数或开关。临时D-01/D-02仅Launch标准层配置/脚本，当前明确保留诊断检查点，未称清理完成；E-01的子进程过滤环境已结束，下次未使用。最终必须将Launch专用诊断辅助移至回收站，再构建/验证正式无诊断产物。goal工具历史状态不作为工程完成依据。

---
## 历史 R-01/E-01 快照（后续结果以上文为准）
# R-01 修复与 E-01 诊断检查点

## 结论
已修复标准validation证实的深度/模板布局契约错误；原nvoglv64.dll+0xf1c729 CPU访问异常仍未完成定位。未记录VK_ERROR_DEVICE_LOST。修后游戏在TitleScreen文字绘制时仍崩溃，0 VUID，不能再把问题限定为字幕。

## 已实施正式修复
D32S8屏障同时转换depth/stencil，目标布局匹配render pass的DEPTH_STENCIL_ATTACHMENT_OPTIMAL；附件依赖覆盖early/late测试。DeviceLocalImage全图范围按实际format决定aspect，保留单aspect采样/复制语义。未强制等待/跳过绘制/吞错误。

同类可达路径检查：主深度写入、主目标alias、offscreen、blit、diagram target和framebuffer读回已有formatAspects范围；diagram/worldPostDepth为D32。统一fullSubresourceRange的纹理读回、shader导入及ray tracing调用沿用真实格式。

## 验证
- Release core编译及安装成功；Gradle build、Java/JNI/单JAR资源检查通过。
- Java120项：118通过，2硬件窗口跳过；native CTest24/24。
- framebuffer GPU原先存在夹具错误，已修view创建usage、D16 depth layout usage和shaderDemote feature启用；第二轮标准validation 0 VUID，真实像素深度、stencil掩码、MainDepth、depth blit断言通过。
- 修后默认层组合运行26.93秒后再次同地址崩溃。这不是正式游戏回归通过。

## 差异类别与清理
- 正式修复源码：6个文件，Products/diagnostic-r1/source-freeze.json记录哈希。
- 临时诊断：D-01 Launch配置/参数；新增业务日志、探针、计数、诊断代码为0。
- E-01实验：仅子进程过滤OBS/RTSS Vulkan层；不修改其安装、设置、进程或游戏功能。环境变量随子进程结束消失，未写入正式启动。
- 排查未结束，D-01专用启动辅助明确保留；尚未清理，不宣称完成。最终需将其移到Windows回收站，再验证正式无诊断产物。

## 产物与下一步
Products/diagnostic-r1/Radiance.jar：0FF46AC78AF6812E281D99BAE0E94E1FD731AB4A204620464A04329B0583E430
core.dll：2017B205DD2B4A06C626401C6019B154BF746C94E1492C7B2AE8B7DBC396FBED
包内DLL与实际提取加载DLL哈希一致。历史正式A/B未覆盖；本轮尚无可宣称通过的正式修复产物。

当前E-01运行：PID41476，run-20260915-135756-089。存活时间/错误计数见Evidence/e01-live-checkpoint.json。已请求用户确认主菜单画面，未进行UI自动化。下一步收到反馈后，在安全退出当前诊断实例后进行单层对照，以区分OBS、RTSS或组合影响；不由一次存活认定根因，不把层禁用包装为正式修复。随后决定需要的同步/GPU辅助validation或进一步原生调用证据。两仓index与Baseline一致，未stage/commit/push。






