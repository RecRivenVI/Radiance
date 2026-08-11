# 20260916-004325 状态取证产物索引

时间基准：2026-09-16，Asia/Shanghai。仓库工作树保持原样，未暂存、未提交、未推送。

## 产品

| 文件 | 大小 | SHA-256 |
|---|---:|---|
| `Products/draw-state-trace/Radiance.jar` | 146642489 | `E45BEDAC9A9EE1C6C9B67D7D49E06D4D98D12491783E133D897CA08218F81865` |
| `Products/draw-state-trace/core.dll` | 30963712 | `1E4BB8F518323B7E32F7E34D41CDD67CC82EA03C2D3E37AF868AE08362D06C84` |
| `Products/draw-state-trace/core.pdb` | 45617152 | `0BF874664CAF6A0EE189B4CC832E10C04A1E8815C32B42209FBF24FC1C6192DE` |

对应构建源文件：

- Radiance：`D:\Workspaces\Repositories\GitHub\RecRivenVI\Radiance`，分支 `1.21.1-neoforge`，HEAD `414d8e330a2fc6cb1e8630cc95f2302b2b97a0e8`，工作树有既有未提交改动。
- MCVR：`D:\Workspaces\Repositories\GitHub\RecRivenVI\MCVR`，分支 `develop`，HEAD `9905c81b1999f5845bf66d13501d371c16adf561`，工作树有既有未提交改动。
- `Radiance/src/main/resources/core.dll`、`Radiance/build/resources/main/core.dll`、JAR 内 `core.dll` 与上述 native DLL 均为 `1E4BB8F5...`；未沿用旧 `96E59835...` 或中间的 `1A83C09F...` 产品。

## 源码快照

| 文件 | 大小 | SHA-256 |
|---|---:|---|
| `Source/MCVR/src/core/diagnostics/draw_state_trace.cpp` | 13537 | `F679C0E4A7797405D3E65A579D14B07FFE3D800CFBC8A39019F870000824277F` |
| `Source/MCVR/src/core/diagnostics/draw_state_trace.hpp` | 4010 | `226256CCE50C91A42BE21016D9450806F79B1714C3FECE010AFFA6E84BBEA6D7` |
| `Source/MCVR/src/core/middleware/com_radiance_client_proxy_vulkan_ShaderProxy.cpp` | 9783 | `0E89FBA3BD9AE724D69212A07BCAFAB3EC0B1CEA263001D15E8E8DF1CD26F158` |
| `Source/MCVR/src/core/render/modules/ui_module.cpp` | 127707 | `1AD4E59DF3835472E69B757FDEAB39FE3CCD750BE21FFAA202F08A9B4EFB6797` |
| `Source/MCVR/src/core/render/render_framework.cpp` | 44367 | `4D92D014178313FB168D0B797B5B1B63F1E20E5EA1DEC58AB7C68F54137C2B3F` |
| `Source/MCVR/src/core/render/textures.cpp` | 46609 | `04E64C426D27137E94091301F1D052D9A57AB1F08E38AA2DA7E5CCDE831ED3A5` |
| `Source/Radiance/src/main/java/com/radiance/client/proxy/vulkan/ShaderProxy.java` | 13547 | `D567B214FB1954E61B0D50BAF7E5B71EF5960C277578C8C1C8F5AB7B7A8F770F` |

启动脚本：`Launch/Run-DrawStateTrace-A4.ps1`，最终版本大小 14812 bytes，SHA-256 `2B79131E83C8299707725452C705CDD4F1572502B7D7AE8924BC2B454E14655B`。脚本先前的一次运行记录在 `Evidence/run-draw-state-a4-20260916-005345-618`；hs_err `2D318768B1A79F7039A216F42BCD28D4C57037ED2AEEAC1590E8BEDCB225EF77`，latest.log `C000F2CD6D855F37AD932C6D4356A533EAC28F23127716D5F8F828DA7F9BE908`。该次实际加载了上述新 core，但因 Prism 单实例转发未启用 trace，详见 `Evidence/D-REAL-09-DRAW-STATE-A4-FAILED.md`。

## 测试

- MCVR Release native build：PASS。
- MCVR existing CTest：24/24 PASS。
- Radiance `compileJava`：PASS。
- `preparePackagedClient --rerun-tasks`：PASS。
- 真实客户端：D-REAL-09 已在新产品上复现精确 AV，但未形成有效 trace；D-REAL-10 的隔离启动未产生 Minecraft Java，因此没有新的渲染运行证据。人工操作仍未发生，`USER_ACCEPTANCE=PENDING`。

## 启动失败封存

- D-REAL-10 报告：`Evidence/D-REAL-10-ISOLATED-LAUNCH-NO-JAVA.md`。
- 关键文件：`Evidence/run-draw-state-a4-20260916-010601-000/PrismLauncher-0.log`（19479 bytes，SHA-256 `117A571E2B6DADCA3251171269974353518041FACBA5AC7BBDD0E8549070A6AD`）、`run-result.json`（325 bytes，SHA-256 `19BFAA45C75818CB9E1DE7BAEF594B51B5478E93014BA052ECFDF9927E5FF0D5`）、`launch.json`（2610 bytes，SHA-256 `12FC6E6E6F0BCF8C07C26903535BCE9E185BB5F5B33CA8A6151B9381EEAF40E7`）。
- 临时隔离数据根已移入 Windows Recycle Bin；原 Prism 数据根和原 A 实例未改动。

## 实例归属修正

- `E:\Minecraft\PrismLauncherDev` 是用户手动验收环境；本 handoff 中的 Prism 启动脚本和 D-REAL-10 记录只作为历史证据，脚本已废止，不再执行。
- 代理后续测试位置固定为 `D:\Workspaces\Repositories\GitHub\RecRivenVI\Radiance\run` 下的新隔离子目录；不复制用户 Prism 实例或存档，运行材料不纳入 Git 提交。

## R-LIFE-02 首个资源保活候选（已 superseded）

本节保留首个资源保活候选的完整哈希；前面的 `draw-state-trace` 产品清单保持不变，属于修复前 D-REAL-11 实际运行输入。该候选的 descriptor 槽位覆盖会立即替换旧保活代，经过 update-after-bind 复核后不再作为当前验证输入。

| 文件 | 大小 | SHA-256 |
|---|---:|---|
| `Products/resource-lifetime-keepalive/Radiance.jar` | 146642349 | `9E3B47C9C363374CC99A759BA47D3319E0AD82F7540BD0AAD7251A1D5DF04CD5` |
| `Products/resource-lifetime-keepalive/core.dll` | 30967808 | `48DEA3775CE149D618598146EDFF63EFA1B79150587E0226B14DC9FE873C0C21` |
| `Products/resource-lifetime-keepalive/core.pdb` | 45764608 | `104F8C0DECB45A4075F2CBAE8F89748D6DD1EDB24BDF4504EBC9CD838EBC5547` |
| `Source/resource-lifetime-keepalive/descriptor.hpp` | 6496 | `9722878E8BF8EDA7989250CFC53AFD35C1A8E14C02DECC212BAFB9516F9E46B` |
| `Source/resource-lifetime-keepalive/descriptor.cpp` | 18381 | `0A8DB8817CE6046F2C04B9C0CD4CC1775F085EBA8C891E0123AF2CE29A52239B` |

- JAR 内 `core.dll`：30967808 bytes，SHA-256 `48DEA3775CE149D618598146EDFF63EFA1B79150587E0226B14DC9FE873C0C21`，与 standalone Release DLL 一致。
- 修复说明：该首个候选曾对 descriptor table 的实际写入资源保存 shared ownership，但覆盖槽位时会立即替换旧代；没有恢复 E-02、全局永久保留或额外同步。
- 验证：MCVR Release build PASS；既有 CTest 24/24 PASS；MCVR INSTALL PASS；Radiance `distributedJar` PASS。该产品尚未加载到真实客户端，不能写成游戏回归通过。
- 交付边界：该产品仍保留默认关闭的 D-STATIC-09 draw-state 诊断代码，作为下一次修复后真实对照检查点；最终正式发布包仍需在对照完成后清理临时诊断并重新构建。
- 本轮没有 commit、push、staging、reset 或历史改写。

## R-LIFE-02 v2 资源保活修复产品（2026-09-16 22:19 +08:00）

当前工作树在 `MCVR/src/core/vulkan/descriptor.hpp` 与 `descriptor.cpp` 的修正版差异；保留 R-01/R-02，E-02 未恢复。修正版在相关 update-after-bind descriptor table 内保留每个槽位曾写入的所有资源代，并按 `shared_ptr` 控制块去重，直到该 table 按现有 frame-retainer/fence 退休；不引入全局永久保留或额外同步。

| 文件 | 大小 | SHA-256 |
|---|---:|---|
| `Products/resource-lifetime-keepalive-v2/Radiance.jar` | 146642348 | `CEC66840C01B96EF4ED203810E56C80E18B676A186AD8AE99F2B94BF4535A8D7` |
| `Products/resource-lifetime-keepalive-v2/core.dll` | 30967808 | `44D3B6F8F2013D7B5ACFCFC06331FE09FE4DD5A5FECC1E9BD923DED87CE9A112` |
| `Products/resource-lifetime-keepalive-v2/core.pdb` | 45764608 | `A4F994D67B4466AE09278864E254EA640153490A9FADD46B64DBC8A84AB6B0FD` |
| `Source/resource-lifetime-keepalive-v2/descriptor.hpp` | 6642 | `36EA19963334B820A952E864DD1298614A82DFE793E7D0B97D834D2644CB98F1` |
| `Source/resource-lifetime-keepalive-v2/descriptor.cpp` | 18815 | `A5B54CF216BEBFA0B3C3CCB0AD3DCEC21085DBE5B8EC0E41769A7A8F2EBE587D` |

- JAR 内 `core.dll`：30967808 bytes，SHA-256 `44D3B6F8F2013D7B5ACFCFC06331FE09FE4DD5A5FECC1E9BD923DED87CE9A112`，与 standalone Release DLL、Radiance 资源 DLL 和 Gradle 资源 DLL 一致。
- PE CodeView：RSDS `{C563B7F0-CC62-4BD8-AD56-A4067DC94C82}`，age `10`，指向配对 `core.pdb`；由本机 Visual Studio `dumpbin /headers` 核对。
- 验证：MCVR Release build PASS；既有 CTest 24/24 PASS；MCVR INSTALL PASS；Radiance `distributedJar --no-daemon` PASS。v2 尚未加载到真实客户端，不能写成游戏回归通过。
- v2 仍保留默认关闭的 D-STATIC-09 draw-state 诊断代码，因此是修复验证检查点，不是最终诊断清理后的发布包。
