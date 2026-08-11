# 本轮公共与固定消费者检查点

## 已验证
- 5个Sable/Veil VertexArray Mixin已登记，custom GPU fixture已登记。
- Release core及fixture编译/链接成功（native-custom-array-build.log）。
- 7项定向CTest通过（native-integration-tests.log），包括双binding、UINT16、indirect偏移GPU像素case。
- Sable preRenderChunks/updateCulling恢复源码已通过Java编译，原setupRender截断不再跳过该入口。
- builtin-source-recheck.json：当前shader输入与旧237变体seal完全一致；保留原静态编译证据范围。
- dependency-hash-recheck.json与dependency-ranges-result.log：三个外层JAR一致；35项已安装CLIENT/BOTH依赖版本限制通过Maven VersionRange核对，无缺失required或命中incompatible。此证据只证明声明约束。

## 新增，待Java统一验证
- SimulatedWorldEffects恢复原LevelRenderer主体被替代后未执行的EndSeaRenderer.render调用。
- WorldRasterPass在fuse后使用真实主色彩/深度承接固定raster世界效果，保存/恢复view/projection、fog、shaderColor、GL状态、read/draw FBO和viewport。EndSea完整48层消费者保留，不冒充PT几何。
- Veil AFTER_LEVEL renderPost同样通过WorldRasterPass入队，解决flush后已离开原WORLD_STAGE而被OUTSIDE拒绝的问题。
- 普通WORLD_STAGE仍需真实world material bridge，不因这个固定scope放行未知几何。

## 未完成
Sable/额外section shader消费者及levitite世界绘制、EndSea阴影完整group输出、Veil动态shader重载、18工作包剩余整合、最终一致Release/JAR与A/B指南。没有启动客户端，没有目视验证。构建和primitive GPU成功不等于生产Java/JNI场景完成。
