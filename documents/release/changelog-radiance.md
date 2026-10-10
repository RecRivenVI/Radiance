# Radiance 更新日志

适用于 Minecraft 1.21.1 / NeoForge。

## [未发布]

适用于 Minecraft 1.21.1 / NeoForge。

### 变更

- 将构建迁入 Target 与组件结构，合并 MCVR 为 `native_renderer`，保持玩家可见渲染和引导逻辑。
- 将诊断模组改为验证专用探针，游戏无关工具移入 `render_diagnostics`。
- 编译 NeoForge 更新到 21.1.256，支持下限仍为 21.1.62，兼容发行版本保持固定。
- 历史台账、relay 与闭合审计归档，文档主语言改为简体中文。
- 升级仓库模板至 1.2.1，二进制资源按 Git 属性跳过文本检查，并检查模板名称残留。
- 原生格式检查支持执行时从 PATH 或 Visual Studio 查找 clang-format，缺失时给出安装说明。
- 对齐构建配置、双语首页标题与 NOTICE 来源结构；CI 使用构建自身的原生工具查找。
