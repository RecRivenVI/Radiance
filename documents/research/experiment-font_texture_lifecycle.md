# 字体图集与重载批次生命周期修复

2026-10-17，按使用者授权修复阶段 A 手工 F3+T 崩溃。保留[首次故障调查](incident-font_texture_reload.md)及原始候选，不把新的代码修复反推为旧进程纹理 305 的完整运行追踪。

## 问题

`FontSet.closeTextures` 只关闭字体页，未注销 `TextureManager` 中的对应项；旧对象随后可再次分配空 ID。`BufferSource` 的未提交字形批次只保存 `RenderType` 和旧 UV，纹理名称被重用后会重新解析到另一代图集，或取到已关闭的字体页。单独容忍缺失 sampler 不能解决旧 UV、旧资源身份和空图像问题。

## 方法

核对 Minecraft 1.21.1 的 `FontManager.apply/updateOptions`、`FontSet.close/resetTextures/stitch`、`FontTexture.close/load`、`TextureManager.register/release` 与 `BufferSource.getBuffer/endBatch`；对照实际 NeoForge 21.1.259 类的成员及方法描述符。使用真正的 `BufferSource`、`BufferBuilder`、`MeshData` 和字体 `RenderType`，只替换纹理存储后端，执行产品 `FontStorageMixins` 的关闭入口和共享生命周期函数。

本轮不启动游戏、性能实验或故障注入；Prism 只在构建与自动回归完成后投放，由使用者验收。自动回归的命令和原始日志保存在 `D:/Workspaces/Scratches/RadianceSemanticsAudit/font-f3t-*.log`。

## 发现

### 生命周期合同

1. 以已注册的字体页对象身份记录待提交批次，不用整数纹理 ID 或名称判断同一所有者。名称和 ID 都可复用，旧对象退休不能清除新对象的批次。
2. 在字体页关闭或替换前，释放仍引用该对象的 CPU 字形网格并清除对应的 `lastSharedType`。这些 UV 已不属于新图集，不在帧外强行绘制，也不重新绑定到新图集；当前字体在后续绘制中重新生成批次。非字体及其他活字体页的批次保留。
3. 字体集合关闭时，只有 `TextureManager` 当前注册对象仍是该字体页，才通过管理器注销。名称已经交给新对象时，只关闭旧对象。直接关闭字体页也经过批次退休入口。
4. 关闭和原生释放按渲染线程顺序执行；非渲染线程的集合关闭复制待退休列表后排入渲染队列，直接页关闭延后整个关闭操作，防止先释放 ID、后处理 CPU 引用。
5. 批次正常提交即释放其 CPU 字体页引用；注册表弱引用批次所有者，不能阻止临时 BufferSource 回收。已提交的 GPU 图像、sampler 和描述符继续由既有所有权与 fence 退休机制管理。

未修改原生纹理过滤的严格检查、JNI、shader、线条 alpha 或 GPU 同步；没有补空 sampler、采样缺失图像、吞掉异常或增加全局 GPU idle。

### 自动验证范围

第一轮五项生命周期测试通过；随后补充渲染队列退休用例，第二轮六项生命周期测试、真实 Minecraft 目标描述符检查及原有纹理任务回归通过。正式整体验证以最终日志为准，不把中途源码的局部通过算作最终构建通过。

回归覆盖旧关闭机制的空存储再分配、真实共享批次切换边界、多字体页与多个 BufferSource、无关批次保留、同名同 ID 新所有者、直接关闭、原始关闭异常传播及异步关闭。纹理存储由受控后端替换，没有运行实际 GPU 字体上传或游戏内 F3+T；真实页号、聊天样式和资源包组合仍需手工复测。

最终执行命令：

```powershell
.\gradlew.bat spotlessApply
.\gradlew.bat check verifyRelease verifyCompliance --strict
```

格式通过，完整门槛成功（135 项任务：53 执行、82 复用）。GAME 273/273、真实 JNI 1/1、probe 27/27、bootstrap 11 通过且 2 跳过；非 GPU CTest 66/66，严格合规失败 0、警告 0。已有 Unsafe 编译警告与 Gradle 弃用提示不混入合规零警告结论。没有启动游戏、重跑 GPU 用例或进行性能测试；原生与 shader 内容未改。

### 产物与投放

- JAR：`D3402E12FBC940F931BCA967546129AD2C5D425BE567001C253F962B846FB063`。
- 内嵌 GAME：`7400227D5FA6D06C20E7C8D58138B1D57A3D80E581467567A1BA13099D7A4D11`。
- DLL：`04DA0F2DF818EFB6288FFAB20AA758D1C208A6AD9B2E6A9808DE98853D8457D6`，构建、内嵌及旧候选内容相同；未宣称新包已实时加载。
- GAME 只改变字体生命周期的 6 个类和 Mixin 配置；所有类与编译输出逐项匹配，120 个外置 SPIR-V 与旧包及构建输出一致。两份 PT ZIP 仅容器元数据不同，解压成员内容完全相同；对应运行库索引随容器哈希更新，引导类不变，探针未混入。
- 比较工具首次遗漏预期的 `game.sha256` 更新而拒绝通过；补上其与实际 GAME 内容哈希的双向校验后通过，没有放宽产品检查。两次日志分别保留。

`font-f3t-artifact-identity.json` 固定产物，`font-f3t-source-manifest.json` 固定全部必要源码、子模块与原始字节哈希。为避免复制完整旧快照，`font-f3t-source-delta.zip` 与清单指向的阶段 A 全量快照共同重建本轮源码；增量各项已读回校验，没有修改暂存区。

03:00:46–03:00:51 按实例锁与停止检查原子替换 Prism 中的旧 JAR，安装哈希与构建相同，其他四个模组及原有实例设置前后哈希相同。旧 JAR 和本次锁送入回收站；回执为 `font-f3t-prism-deployment-receipt.json`，run_id 为 `radiance-font-f3t-deploy-20261017-030046-e4c1b3a4`。没有启动游戏、投放探针或修改设置，先前崩溃原始证据保留。

### 阶段 A 反馈

使用者报告“线条目前看起来情况还不错”，主要可见变化为恢复透明度，并表示偏好不引入透明度。只记录该观察与产品偏好，未补填逐项通过，也未擅自改成不透明。建议将世界几何线不透明作为明确的合同差异单独决定；屏幕线与 Create 模型框保持独立。

## 结论

已实现字体注册项与未提交批次的所有权修复，完成最终构建、自动回归与安全投放。先前 F3+T 失败保留；本次没有证明原进程纹理 305 的具体资源位置，也没有把字体异常解释成 GPU 设备丢失。最终游戏内结果交由使用者确认，不提交、不推送。
