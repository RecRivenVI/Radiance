# 重载后字形原始位图生命周期调查

2026-10-17，阶段 C5 首次隔离功能运行暴露的 CPU 图像故障。调查时尚未确定释放调用方；此记录保留首次失败，不反写为已通过。

## 问题

进程 49028 于 06:27:10 进入世界，06:27:28 请求资源重载，06:27:30 重载完成；真实来源回调、RGBA JNI 和封批检查均通过。06:27:55 世界保存并断开，06:27:56 返回标题界面时，`BitmapProvider.Glyph` 的惰性字形烘焙上传抛出 `Image is not allocated`。

## 方法

保留完整启动日志、Java 线程快照和崩溃报告，逐读字体页退休、字形源位图、纹理任务代际及 NativeImage 上传包装。增加仅隔离探针启用、容量有界的位图释放来源记录，再以同一功能流程定位 CPU 存储的实际释放者；不注入 GPU 故障，不测试性能。

## 发现

- 抛错来自 `NativeImage.checkAllocated`，位于 CPU 上传之前；目标纹理的任务所有者检查已通过。因此不能把纹理 ID 代际有效写成原始 CPU 位图也有效。
- 堆栈经 `BitmapFontGlyphMixins`、`FontTexture.radiance$bake`、`FontSet.computeBakedGlyph`，最终是标题按钮文字；不是世界线几何异常。
- 日志有三维度保存、场景释放和 Streamline shutdown；没有设备丢失，Aftermath 退出计数为 dumps=0，不能称为 GPU 转储。
- 首次启动 Java 线程停在 `warmupCurrentPipelineNative`，后续正常进入世界；冷管线编译的等待不能记为故障 GPU 卡死。
- 先前人工确认的字体页旧绘制批次修复仍保留。本次是另一个资源层面的证据，不能据此抹去先前通过记录，也不能以先前通过否认当前失败。

## 结论

当前不能交付为重载及退出重进通过。须确认源位图的释放与引用关系，修复实际原因并补足回归；不能吞掉上传异常或跳过按钮文字。首次日志为 `D:/Workspaces/Scratches/RadianceSemanticsAudit/c5-client.log`，实例文本已保留在 `validations/regression-line_capture/result/c5-first-failure/`，新取证运行另用 `c5-font-trace-client.log`，不覆盖首次证据。
