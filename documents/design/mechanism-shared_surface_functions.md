# 共同表面函数与版本适配

## 合同

两套 PT 共用 `alpha_mode`、`surface_overlay`、`source_draw_colour`、`material_faces`、顶点着色法线及 packed material 解码。普通命中与 Advanced 当前帧表面缓存读取相同 ABI 字段；不能从 hit group 名称重新猜测透明度、发光或所有权。

`material_decode.glsl` 只依赖共同 ABI，不依赖资源绑定，CONST_ONLY 与普通顶点路径都使用它。`previous_scene_position.glsl` 在当前/上一帧场景描述符已声明的上下文使用，沿用三个索引与拆分位的同一资格；没有延长资源寿命或改变上一帧所有者。

`celestial_math.glsl` 只接收来源向量，提供规范化、南偏角、太阳/月亮方向；各 pack 的短适配器只读取自己的 skyUBO。颜色、alpha、眼睛叠层和 Stress/UV1 继续使用 C 族的共同求值。旧的无调用文字覆盖率函数移除，实际文字调用都经过来源 alpha 丢弃阶段，不保留第二份旧实现。

surface_geometry.glsl 是两套命中与 Advanced 表面缓存共同的法线贴图／基底和 UV→基面换算，保留原 normalizeF、PI、调用参数及数值顺序；不把 caller 的不同水面、height 或 shading-normal 输入合并。最终覆盖补充见[调查](../research/investigation-surface_geometry_extraction.md)。

## 保留差异

[逐函数调查](../research/investigation-shared_surface_functions.md)列出 41 组异体函数及原因。水、体积云、天空资源布局、height trace 与主追踪的输入/预算仍可能不同；本轮既不合并主算法，也不把保留的差异全部宣称为正确或有益。来源未知程序仍按 adr-5 维持回退，显示全亮的物理贡献仍待 adr-6；两者不是 shader 自行猜来源的许可。

来源 blend 只在 `VanillaMaterialAdapter.drawBlendMode` 解释：普通 ADD、LIGHTNING、GLINT、CRUMBLING 分别保留实际因子。可选透明线也使用它，默认线不透明政策不变。

## 诊断与成本

拒收去重最多 128 个键，操作/来源 label 长度分别不超 128/256，理由日志不超 512。锁仅在实际拒收报告入口使用，合法顶点/绘制不加该锁；截断只影响诊断，不影响权限或来源匹配。并发测试等待所有工作 Future 后在主测试线程判断结果。

共同函数不增加 GPU dispatch、缓冲、材质数据量或每顶点分配。独立眼睛 draw 状态与新封批回调的成本已在相应合同披露；没有进行性能实验，不宣称无可测回退。

## 验证

迁移前核对十组重复解码在别名规范化后等价；八份历史正文相同，天空数学正文相同。实际 GPU 覆盖 packed 位域、jitter 外的纯天空数值、source-alpha/颜色和历史反例；实际 RenderType blend 工厂与诊断容量有行为回归。完整 pack 编译、SPIR-V、最终构建和隔离客户端分别留证据，不以源码比较替代 GPU 或视觉结论。
