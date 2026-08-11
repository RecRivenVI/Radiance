# A2 结果：全量 API 追踪不可用于人工交互

## 结果

- 状态：`DIAGNOSTIC_FAILURE_IN_API_DUMP_LAYER`；不能作为 Radiance/MCVR 原生崩溃结果，也不能作为性能或修复验收。
- 运行：2026-09-15 16:30:51–16:33:35 +08:00，Java PID `48924`，监控 PID `93224`，监控记录退出码 0。
- 进程实际加载 `core.dll`：隔离 runtime `d430...\core.dll`，SHA-256 `20F85170B43A822D93BFBD9FB12770CE5CCD78E2FAFC9B8B5E1B6AA62374BD89`，与去 E-02 产品一致；隔离 JAR 为 `21CF6C1451B4021ADE6AEB53A9DFB931A40EFBE15B93C3D5D77FB34FC7B0D550`。
- API dump 在结束前达到 `12,252,054,062` bytes；启动后约 164 秒时 Java 工作集约 `8,620.1 MiB`，累计用户/内核 CPU 时间约 `209.2/149.8 s`，约为 2.2 个逻辑 CPU 的平均占用。
- `hs_err_pid48924.log` 明确记录 `Problematic frame: C [VkLayer_api_dump.dll+0x498cb9]`，`RIP=0`，Java 调用链为 `RendererProxy.submitCommandNative`；不是 `nvoglv64.dll` 原始崩溃现场。对应 mdmp 为 `9,088,415,268` bytes，留在隔离 base 原位，未复制。

## 开销归因

- 首要来源：`VK_LAYER_LUNARG_api_dump` 的 text + `detailed=true` + 地址/线程/帧信息 + `file=true` + `flush=true`，对每个 Vulkan 调用产生大块结构化文本并逐调用写盘。API dump 尾部仍处于 frame 731 的高频调用附近，未达到 0..2500 上限即已使层崩溃。
- 次要来源：Khronos validation 的 `validate_sync=true`；它不是逐 API 全量记录，且 GPUAV 明确为 false，但仍增加同步跟踪和消息处理。
- 未发现证据把卡顿归因于 Radiance 正常绘制本身；A2 的 `core.dll`/JAR 一致性通过，最终 fatal 位于新增的 API dump 层。

## 证据损失与后续配置

- A3 交互配置移除 API dump 层，因此会失去逐调用 create/update/bind/destroy/submit 文本及 API 地址级重放信息；保留 JAR/DLL/PDB 一致性、JVM 完整 fatal dump、游戏 latest.log、有限 validation/sync 消息和现有 D06 完整转储/源码证据。
- 若必须再次获得 API 调用序列，A2 这类配置只能作为一次有明确操作目标的专门取证，不与人工验收并行；不得把 A2 的可操作性或崩溃算作游戏性能结论。

## 保留与清理

- 原始 `api-dump.txt`、stdout/stderr、latest.log、`hs_err_pid48924.log` 和 `hs_err_pid48924.mdmp` 均保留在 `20260915-A`；没有删除或改动其他实例/程序。
- A2 Launch 配置暂留作为历史专门取证证据；正式交付清理阶段再按诊断台账移入 Windows 回收站，失败则保留。
