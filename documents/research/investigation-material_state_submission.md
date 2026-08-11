# 材质状态回退与场景提交复用

2026-11-01，依据当前城市手工诊断和实际来源字节码，修正 Ponder 导致的静态状态回退，并减少 Native 场景提交中的重复准备；本轮不据功能计数宣布帧率收益。

## 问题

城市诊断的前台无界面样本平均 33.87 ms，材质状态设置与恢复自身约 3.47 ms。运行日志明确因 Ponder 的 `RenderTypeAccessor` 未登记而将 `STATIC_STATE` 整体退回捕获。SBT 准备约 1.17 ms，元数据内容选择约 0.88 ms；这些区间不能直接相加成可省的整帧时间。

## 方法

核对实际 Ponder 1.0.82 发行类及注册指纹，不按包名放行；复用现有声明状态、SBT 缓存、场景表和帧资源退休。沿用项目内功能夹具验证更新、重载、空间交接和多种 PT 通道，再提供同一城市副本供使用者体验。

## 发现

- Ponder 的目标类只通过 `@Invoker("create")` 暴露原版工厂，没有注入状态执行。Extra 登记该具体类的 SHA-256；其他未知变换仍回退，Veil 的附加状态仍按实际图层判断。
- 场景表的动态数组此前为每次准备的局部变量。现在仅复用录制上下文的 CPU 容量；缓存接受动态内容时仍复制独立所有权，GPU 不借用暂存数组，退出世界释放容量。
- 世界准备按实际命中组内容保留不可变序列。每个通道及帧槽解析一次变化版本，SHARC 与查询共用接受的索引；SBT 命中同一不可变身份时不再扫描或复制整个索引数组。
- 通道映射在对应管线寿命内不可变；管线重建清空解析状态，不跨通道、帧槽或不同场景误用。真实变化继续生成不可变 GPU 版本，旧版本由原有帧退休队列保持。
- 暂存容量保留到该录制上下文的场景释放，代价是保留峰值 CPU 容量；没有新增历史版本缓存。序列仍需在场景组织结束时比较一次，真实内容变化时仍解析和上传，不将全部 SBT 工作描述为已消除。

## 结论

两仓库的 `spotlessApply check verifyRelease verifyCompliance --strict` 通过，Native 82 项非 GPU CTest 和 28 项 GPU CTest 通过。首次完整检查误用了本机 Python 3.15，缺少 `nbtlib` 和 `yaml`；切回已有完整依赖的 Python 3.14 后同一检查通过，没有修改或跳过测试。

实际组合客户端确认 `STATIC_STATE route=DIRECT`，Ponder 工厂入口的 SHA-256 核验通过。Vanilla PT 的 Sable／Flywheel 装拆、分离、合并、删除、重载回归，以及 Advanced 的普通／金属法线与连续介质场景均通过，正常保存退出，没有设备丢失。证据分别位于 `validations/conformance-takeover/result/world-materials-134/material-submit-handoff-20261101-014651/` 和 `material-submit-advanced-normals-20261101-014902/`，后者的通道配置核验为 Advanced。

配套固定于 `versions/1.21.1-neoforge/build/candidates/material-submit-20261101/`：本体 SHA-256 为 `ca2bde5a42866a553882639e03963a9702268bd1a9ae65e3b287632fd41d6c78`，Extra 为 `bb98550ed61af6c93c975a11bbdf193dc062eecab653873641cfa2a5bcacac0d`。包内 DLL 与当前 PDB 的 CodeView GUID／age 一致，仍为 API 16。构建日志与可再生副本的回收清单位于 `D:/Workspaces/Artifacts/Radiance/cpu-submit-20261101/`；城市存档、配置和既有关键证据保留。

实现范围限定为恢复现有快速路径与减少 CPU 准备，不修改水面、反射、折射、采样数、几何范围或画质。原城市副本继续供使用者体验，手工实例不加载性能探针。当前存在其他客户端负载，本轮只作功能回归，没有新的整帧收益结论，人工视觉仍待反馈。没有提交、推送或替换 Prism。
