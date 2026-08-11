# 公共承接本轮实施检查点

## World frame
- 查明NeoForge21.1.250 GameRenderer补丁在LevelRenderer返回之后派发AFTER_LEVEL；原world frame在LevelRenderer尾部关闭，该阶段收到stale token。
- GameRenderer在Camera.setup后、调用LevelRenderer前建立frame；LevelRenderer借用FrameLease，直接调用仍可自行拥有frame；AFTER_LEVEL与手部后统一commit/close，再Entity.build与fuse。WrapMethod保证异常关闭/回滚。
- 尚待编译和工程验证；AFTER_LEVEL后处理默认目标读取顺序仍待消费者集成，不因生命周期修复标整体完成。

## Framebuffer blit
- 新增color/depth blit shader，以原始source/destination矩形计算逐像素映射、保留fractional clipping和flip；scissor按目标extent应用；blit独立于color/depth write mask。
- depth shader支持可采样深度format转换；renderbuffer资源加入sampled usage与能力核验。
- 正式shader编译PASS；新GPU用例验证D16→D32、source越界裁剪、X翻转、scissor区域外保持，正在构建。
- 仍待：native core集成编译、color shader像素case、同image非重叠blit、stencil精确裁剪、嵌套diagram目标、MainTarget/readback和目标消费者闭环。

本报告仅本轮源码状态；不得宣称游戏或完整组合验收通过。

GPU update: initial new test failed because test depth upload used COLOR aspect. Corrected to actual DEPTH aspect without changing expected pixels; six cases now PASS, Evidence/framebuffer-blit-gpu-fixed.log. Production core still pending. Self-image color/depth blit now snapshots source before sampling.

Core checkpoint: public-core-third.log contains successful Release core link including ui_diagram_target.cpp. Previous linker failure occurred in same-invocation VS CMake glob regeneration; next invocation compiled new file. core hash saved Evidence/public-core-checkpoint-hash.json. Diagram shader include path fixed; standalone diagram_target shader PASS, shaders full build running. No runtime acceptance.
