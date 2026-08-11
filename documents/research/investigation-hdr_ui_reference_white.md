# HDR 界面参考白与默认 Bloom

2026-10-31，承接使用者对 HDR 候选的反馈：主体画面看起来正常，Bloom 默认偏强，开启 HDR 后 GUI 明显变暗。本轮只修正界面参考白与默认强度，原始实现和验证见[HDR 显示输出](investigation-hdr_display_output.md)。

## 问题

本机 Windows 的 SDR 白为 480 nit，首版 HDR 界面白固定为 200 nit，切换后仅保留约 42% 的亮度。原有输出编码读回正确，缺口在界面参考白没有跟随系统。Bloom 首版默认 8%。

## 方法

使用 `QueryDisplayConfig` 的活动路径匹配窗口所在显示器，通过 `DISPLAYCONFIG_SDR_WHITE_LEVEL` 读取系统值，按其定义换算为 nit。读取与既有半秒显示能力轮询共用；临时查询失败保留当前值，首次失败回退 200 nit，不修改系统设置。[字段定义](https://learn.microsoft.com/en-us/windows/win32/api/wingdi/ns-wingdi-displayconfig_sdr_white_level)规定 1000 对应 80 nit。

界面亮度 0 表示自动，手动允许 80～1000 nit；世界参考白和曝光保持独立。Bloom 默认降为 3%。首版等于默认 200 nit／8% 的配置只迁移一次，其余值保留；迁移版本登记后再次手选这些数值也不会被改回。

## 发现

自动模式和手动模式复用同一显示输出路径。GUI 采用系统参考白后，世界先按独立参考白换算，不随 GUI 亮度同步变亮。增加默认迁移及策略测试，并在真实隔离客户端读回手动白、自动白与 SDR／HDR 往返。

## 结论

本体候选 `78ba0ed3a04c4c5df386e3bc9031fe0fe57c5b33e06009cb98158f2d7e7b417b`、Native `afe45f200d92daf2bb7ebd1051af88714e08c24c0a59d3d42eb1283c256b313d`，API 16。`spotlessApply check verifyRelease verifyCompliance --strict` 成功，82 项非 GPU CTest、28 项 GPU CTest 及配置迁移测试通过。首轮检查发现 javac 新导出的默认值常量尚未同步到 JNI 头文件，核对后按生成结果更新，随后完整检查通过；JNI 方法签名未变化。

隔离实例 `hdr-ui-white-display-20261031-232857` 使用实际发行 JAR，覆盖手动白、SDR／HDR 往返、自动白及设置界面。实际系统白 480 nit，自动 GUI 白读回 481.56 nit，128 灰也符合 PQ 量化；世界参考白仍为 200 nit。手动 GUI 白 120 nit 读回 120.47 nit；调整期间没有重建世界资源。实例正常退出、无设备丢失，没有性能测量。

结果位于 `validations/conformance-takeover/result/world-materials-134/` 下的同名目录，候选与配套源码身份位于 `versions/1.21.1-neoforge/build/candidates/hdr-ui-white-20261031/candidate.json`。已投放 Prism 并核对哈希，未启动外部游戏；Extra、其他模组、存档及外部配置未修改，不提交或推送。配置迁移由产品在下一次正常启动时执行。

此前主体视觉反馈不扩展为本轮最终观感、HDR 补帧或所有显示器的验收。480 nit 来自系统设置与编码读回，不是亮度计测量；临时失败与多显示器行为有明确回退，尚无第二台设备的运行证据。
