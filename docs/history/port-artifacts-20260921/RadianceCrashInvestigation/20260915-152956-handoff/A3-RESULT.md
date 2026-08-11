# A3 结果：低开销 validation-only 真实实例取证

## 运行身份

- 启动：2026-09-15 16:42:04 +08:00。
- 结束：2026-09-15 16:56:05 +08:00，Java 退出码 0。
- 监控 PID：`91132`；Minecraft Java PID：`74556`。
- run：`20260915-A\Evidence\run-a3-20260915-164204-116`。
- JAR SHA-256：`21CF6C1451B4021ADE6AEB53A9DFB931A40EFBE15B93C3D5D77FB34FC7B0D550`。
- 实际加载 `core.dll`：runtime `d4305629ea44667ba1a825dc5ac7b49f69648ab781b9ef580b644455758d5799`，SHA-256 `20F85170B43A822D93BFBD9FB12770CE5CCD78E2FAFC9B8B5E1B6AA62374BD89`；符号 PDB SHA-256 `E177D9DE386308D3B99F112244AC02C47B1E94E5BBB32F64F8824052C081B2BA`。

## 诊断配置

仅加载 `VK_LAYER_KHRONOS_validation`；启用 `validate_sync=true`，错误/警告重复消息上限为 1。API dump、GPU-assisted validation、DebugPrintf、best-practices 均关闭；没有强制等待、额外屏障、延迟释放、跳过绘制、关闭文字或屏蔽 OBS/RTSS。

## 实际结果

- 没有生成新的 hs_err、mdmp 或 API dump。A3 正常退出不能证明原始 NVIDIA `vkCmdDrawIndexed` 访问异常已解决，也不能作为无诊断游戏性能验收。
- stdout/latest.log 捕获到两条同步验证错误：
  - `SYNC-HAZARD-WRITE-AFTER-WRITE`：后续 `vkCmdPipelineBarrier2` 写入的颜色图像，先前由另一命令缓冲区的 `vkCmdEndRenderPass` 写入；当前依赖不足以覆盖颜色附件写入阶段。
  - `SYNC-HAZARD-READ-AFTER-WRITE`：`vkCmdBeginRenderPass` 的 `loadOp=LOAD` 读取颜色附件，而之前的 layout transition 写入没有在 `COLOR_ATTACHMENT_OUTPUT` 阶段提供足够依赖。
- 日志还包含常规 Mixin 缺类、资源/声音警告和 OBS/RTSS loader warning；这些不单独构成根因。

## 证据边界

A3 保留 validation/sync 层指出的资源访问阶段关系和 JVM fatal 现场能力，但主动放弃了 A2 级别的逐调用 create/update/bind/destroy/submit 文本。因此两条 sync hazard 是当前新增线索，需要和 D06 完整转储中的命令提交/资源状态关联；在形成具体不变量前不直接改正式渲染代码。

完整 stdout、stderr、latest.log、process.json、run-result.json 和启动设置保留于上述 run 目录。清理状态见 `CLEANUP-20260915.md`。

## 产物哈希封存

运行时长为 `841.0584573 s`（14 分 1.0584573 秒），由 `run-result.json` 的 `Started=2026-09-15T16:42:04.1161536+08:00` 与 `Ended=2026-09-15T16:56:05.1746109+08:00` 计算；退出码为 0。

| 项目 | SHA-256 |
|---|---|
| A3 `vk_layer_settings.txt` | `A138B1F4648A5A3BBB5E975B692F1681D0E29C0745FA692DD12FEE84D19D9413` |
| A3 `Run-A3-InteractiveValidation.ps1` | `64C94FF7B84889C70CE340F5B1465651FA087BA2AB054F10BA50BDE5A08A4133` |
| A3 `inputs.json` | `E5D94FADE588CD17F69E793E258CD1175F2285415A4E64C83FDD542139FF3D48` |
| 实际加载 `Diagnostic/base/mods/Radiance.jar` | `21CF6C1451B4021ADE6AEB53A9DFB931A40EFBE15B93C3D5D77FB34FC7B0D550` |
| 实际加载 runtime `core.dll` | `20F85170B43A822D93BFBD9FB12770CE5CCD78E2FAFC9B8B5E1B6AA62374BD89` |
| `Products/diagnostic-e2-removed/core.pdb` | `E177D9DE386308D3B99F112244AC02C47B1E94E5BBB32F64F8824052C081B2BA` |
| A3 `process.json` | `CAFD3B20AEEF31FC35FC0A1D6D55B61EB3640D04E7A70859B9C8B7BB97C575DD` |
| A3 `console.stdout.log` | `FDC32F8A08B5408C223070FEDD5A2C37720A96C4BE064E1FC416D74C11C7739D` |
| A3 `console.stderr.log` | `BC83B9A7E10EC0F97F6B99FE84B72FCA0318C4713E184EF743C8C595457DD30E` |
| A3 `latest.log` | `2B393F58575054442FE17CEA03467B5D349E44F85DAE5DEF97C0492761F61FAF` |
| A3 `run-result.json` | `ED269459A7772DC5F73A155269226A824720B94BC4D591BED07774E0E8F2ABBE` |

## 用户操作记录边界

本记录只保存进程、产物、配置、日志和退出事实；没有根据日志中的世界登录、保存或退出消息推断用户点击了哪些菜单。A3 实际操作序列等待用户直接说明。
