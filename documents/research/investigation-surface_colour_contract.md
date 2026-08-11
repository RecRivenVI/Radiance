# 表面颜色与 alpha 阶段调查

调查日期：2026-10-17。继续阶段 C 族 5。读取当前实际 Minecraft 资源包的 55 个 core fragment 程序，发现除了全局调制遗漏，还必须表达 alpha 丢弃发生在哪个阶段。阶段 4 的工厂旧值回归只固定迁移行为，不能证明旧值全部符合来源 shader。

## 问题

一个 alpha 同时被当成材质透明、贴图裁切、顶点覆盖与染色强度，不能准确表示来源运算顺序。世界事件的 `RenderType.draw` 在 HEAD 被接管，不执行 `ShaderInstance.setDefaultUniforms`；`WorldMeshSink` 没有捕获 ColorModulator。StorageVertexConsumerProvider 的三个 endBatch 方法全部为空，因此显式刷新后同一层仍合并进同一个消费者，刷新时的不同状态无法单独保留。

## 方法

直接读取 `versions/1.21.1-neoforge/build/moddev/artifacts/neoforge-21.1.259-client-extra-aka-minecraft-resources.jar` 的 fragment 内容，逐项列出 ColorModulator、discard、vertexColor、overlayColor 的语句顺序与 SHA-256；完整清单在 `D:/Workspaces/Scratches/RadianceSemanticsAudit/c5-source-shader-inventory.json`。同时读取实际 Simulated 1.3.2 内嵌 spring shader，而不是从普通实体 shader 推测受力覆盖顺序。

追踪普通与刚性实体、BER/Sable、原版缓冲粒子和自定义粒子、天气、WorldMeshSink、Flywheel、Spring、GUI/FBO、文字与两套 PT。粒子目前借用 entityCutout/entityTranslucent 作为输入层，这些代理层不是原始粒子 shader，不能把它们当作来源语义。UI 光栅继续执行原 shader；世界颜色与图解/屏幕淡出不得混用政策。

## 发现

| 来源程序 | 实际顺序及影响 |
| --- | --- |
| cutout | texture × vertex × ColorModulator 后按 0.1 丢弃；旧分类按 0.5，是确定阈值差异 |
| cutout_mipped | 同样先调制，再按 0.5 丢弃；不能随 cutout 一起改成 0.1 |
| entity_cutout、no_cull、smooth_cutout | 先按原 texel alpha 0.1 丢弃，再调制和叠加 UV1；NO_TRANSPARENCY 的剩余像素仍不透明，不能拿顶点 alpha 再裁切 |
| entity_translucent | 同样先丢弃 texel，再调制；剩余像素是分数覆盖，不能变成二值，也不能误作折射 |
| entity_translucent_cull、item_entity_translucent_cull | 调制后按 0.1 丢弃，与上一行的阶段不同 |
| particle | 调制后按 0.1 丢弃；其输入代理 RenderType 不能改变原始语义 |
| 普通 text、intensity、background | ColorModulator 参与丢弃前的 alpha；阈值 0.1 |
| see-through text、intensity、background | texel/vertex 的 alpha 先过 0.1，再乘 ColorModulator；简单提前乘全局 alpha 会改变边界 |
| Simulated spring | 原 texel 先过 0.1；texture × vertex lighting × ColorModulator 后，再混合 Stress 覆盖，最后 lightmap。PT 已有替代烘焙光的合同，覆盖与全局调制的先后仍须保留 |
| Flywheel | 材料 cutout predicate 消费其已组合的 color；不能把普通实体的“先裁切 texel”规则套进去 |

确定缺口分三组：全局颜色未传播、丢弃阶段/阈值被合并、显式 endBatch 状态未封口。未经验证的任意自定义程序继续按 adr-5 处理；不擅自增加统一 gamma 转换，也不改变阶段 A 的世界线不透明政策。

现有材质与朝向的打包字段仍有空间保存来源丢弃规则，canonical/compact 的步长无需增加。现有 InstanceAppearance 可携带每次绘制的 RGBA，不需要复制几何或改变 BLAS。Advanced 的 primary/secondary surface cache 均为八层 RGBA32F；cache7.w 只有有效标记，用它记录当前帧 geometryBufferIndex + 1，可让后续表面重采样找回同一份绘制参数，不新增缓存图像。实际记录 368 字节，maxStorageBufferRange 是 uint32：索引上限小于 2^24，因此 float32 能精确保存。仍须检查所有写入者、读者、帧内依赖与越界拒绝，不能只凭数值推导宣布完成。

## 结论

先锁住上述真实入口反例，再分别携带丢弃阶段、颜色运算、实际 draw 快照；不通过重新生成所有顶点或缩减模型参与规避。原有 rgba、overlay、纹理身份、查询掩码、在途描述符和退休保持各自职责。世界和离屏光栅保留不同执行器。当前尚无本族新实现或游戏通过证据，最终目视需核对淡出、文字边缘、受力/受击、半透明和已接受的线条策略。
