# 两套 PT 共同函数与总入口调查

2026-10-17，阶段 D 族 10 的迁移前静态调查；逐函数正文与调用上下文核查，不将同名或同哈希直接解释为语义等价。

## 问题

两套 PT 主算法必须保留，但相同表面规则和解码应只定义一次。需要识别实际重复与有意/未决差异，避免复制旧 alpha 或 emission 规则。

## 方法

对两个实际 pack 的 glsl/rchit/rahit/rgen/rmiss/comp 建立函数体和签名清单，固定到 `D:/Workspaces/Scratches/RadianceSemanticsAudit/d10-function-inventory-before.json`；再阅读差异正文及输入绑定。清单覆盖 117 个跨 pack 同名组，76 个正文一致、41 个有不同正文；后者逐项如下。工具清单是辅助，实际 shared/global/宏输入仍人工核对。

## 发现

### 同名异体的逐项处置

| 函数 | 当前差异与处置理由 |
| --- | --- |
| `applySampledLightChroma` | 亮度助手名字不同，调用处的数据阶段分别为内联体积光与独立体积光 pass。 |
| `applyVolumetricCloud` | 三种采样预算由各自配置提供。 |
| `applyVolumetricCloudBudgeted` | 云模式、半径、阴影、粉末和环境配置宏；主体保持既有 pack。 |
| `cloudBottomRadius` | 使用各 pack 的云底高度和地表半径配置。 |
| `cloudPlanetPos` | 使用各 pack 的大气地表半径常量。 |
| `cloudTime` | 两套既有时间尺度不同（gameTime 与 gameTime*24000/200）；未证明应相同，保留并公开边界。 |
| `cloudTopRadius` | 使用各 pack 的云顶高度和地表半径配置。 |
| `computeEndPortalColor` | 同一公式使用各 pack 的颜色/缩放常量；保留参数适配，不合并整个 portal 程序。 |
| `initMainRay` | 各阶段的 payload/采样与主射线状态初始化不同。 |
| `initTransparent` | 不同透明 continuation payload 和缓存组织。 |
| `integrateAtmosphereSegment` | 大气半径、密度尺度、散射系数属于各 pack 的天空配置。 |
| `intersectCloudLayer` | 大气地表半径属于各 pack 配置。 |
| `loadVolumetricCloudHistory` | Advanced 用 rtArrayCoord 编码数组布局；Vanilla 为直接 ivec3。 |
| `main` | 不同 shader 入口承担不同阶段；同名不意味着同一消费者，不合并主追踪。 |
| `prepareCloudLayer0` | 各 pack 的 coverage/weather 常量及 cloudTime 输入。 |
| `prepareCloudLayer1` | 中层 coverage/weather 常量及 cloudTime 输入。 |
| `prepareCloudLayer2` | 高层 coverage/weather 常量及 cloudTime 输入。 |
| `preparePrimary` | Vanilla 直接追踪与 Advanced 缓存/重用主命中组织不同。 |
| `resetMainRay` | 各管线保留的 payload 字段和累计状态不同。 |
| `sampleCloudAmbientIrradiance` | 各自环境强度配置。 |
| `sampleCloudDensityFromContext` | 各自密度和基础/细节采样尺度宏。 |
| `sampleFftWater` | Vanilla 的解析波形与 Advanced 的法线纹理/流场不是同算法；保留各自水外观。 |
| `sampleFftWaterCaustic` | 位移、软化、强度及有效深度条件随上述水算法不同；不能仅重命名字段宣称等价。 |
| `sampleSurfaceState` | 三种正文保留水采样、侧壁/height、法线基底和 Advanced emission-cache 输出差异；共同颜色/alpha/法线函数已在 util。 |
| `sampleTransmittance` | 等价形式仅参数名不同，分别调用 pack 的 transmittanceUv；不值得另加全局适配。 |
| `skyNeighborhoodBounds` | Vanilla 从 firstHitClearImage 取天空值，Advanced 从 outputImage 及 skyPixelState 获取状态；输入阶段不同。 |
| `softClampVolumetricLight` | 等价有限检查语法与各 pack 亮度上限配置。 |
| `storeVolumetricCloudHistory` | 对应不同数组布局，不能合并成固定索引。 |
| `traceLocalHeightIntersectionAndExit` | Vanilla 与 ReSTIR 的 capped height trace 及 edge-wall UV 起点不同；保留算法预算和 miss 初始化。 |
| `tracePrimary` | 分别执行完整路径与分阶段主命中策略。 |
| `traceSecondary` | 各自反弹/复用、预算和 continuation 算法。 |
| `traceSharcUpdate` | 各自 SHARC 更新入口与路径状态不同，保留主算法。 |
| `transmittanceUv` | 四种正文含不同参数命名/半径常量，云路径有分母下限、shadow miss 直接使用大气厚度；保留上下文。 |
| `volumetricCloudClearAmountScale` | 相同分档算法，各自配置宏。 |
| `volumetricCloudClearCoverageDrift` | 调用 cloudTime 后的换算不同，与各自时间输入绑定；不擅改动画速度。 |
| `volumetricCloudLightVisibility` | 各自模式、投影阴影强度/软度设置。 |
| `volumetricCloudPrimaryLightRadiance` | 使用各自太阳/月亮辐射配置。 |
| `volumetricCloudSkyFullBelowHorizonBase` | 各自地平线下夹角常量。 |
| `volumetricCloudSkyFullHorizonBase` | 各自大气最小视线夹角常量。 |
| `volumetricMediumMaxDistance` | Advanced 有水下 fogEnd 限制，Vanilla 使用既有体积光最大距离；未批准统一外观。 |
| `volumetricStepLength` | Advanced 有独立水下步长与末端约束；Vanilla 保留普通分段。 |

### 相同机制与入口缺口

- 普通表面与 Advanced 当前帧缓存重复解码相同 packed fields；可共用无资源依赖的 material decode，避免 CONST_ONLY 后维护另一份。
- 八个 loadPreviousScenePos 正文相同，资源名和索引合同也相同；可共用读取器，并保留族 8 的三索引/拆分资格。
- 两个 celestial 文件除 include guard 外相同；规范化与南偏角公式可共享，各 pack 的天空输入适配保留。
- 族 5/6 已共同使用 source alpha、颜色调制、叠层和法线函数；不再次合并不同水/高度场或主追踪算法。
- 世界线默认不透明已获决定，但保留的 source-alpha 策略仍把 LIGHTNING 和 GLINT 一并编码为普通 ADD11；来源分别为 SRC_ALPHA/ONE 和 SRC_COLOR/ONE，已有表面模式 20/21 可以表达。这是 opt-in 路径的明确缺口，应从同一来源 blend 适配取值，不改默认不透明。
- RenderCaptureContract 的拒收日志去重 set 无上限，含来源自由 label；应该有固定键数和长度上限，合法绘制热路径不加锁。

## 结论

只合并已核实同语义的表面解码、历史读取与 celestial 数学函数；保留 41 组差异的原输入与算法，不能声称这些差异都已经证明正确或有益。将 source blend 适配复用到可选线条策略，并限制拒收诊断存储。用实际 GPU 数值反例、旧/新函数体影子比较、完整 pack 编译和最终客户端检查验证；不开展性能测试，不把默认关闭路径的静态检查记作动态视觉通过。
