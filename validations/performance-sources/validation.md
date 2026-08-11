# 已接管来源效率对照

## 目标

在同一冻结城市、32 视距、1920×1080 和相同画质下，比较公共提交和 Voxy 有效覆盖的整帧成本、CPU／GPU 耗时、长帧与内存代价。静止和固定移动分别比较；源计数只用于解释，不作为提速结论。Voxy 场景使用固定 512 方块来源半径，不改变近景视距或删除有效 PT 几何。

无 Voxy 的整机调查使用同一冻结城市，区分 FRAME_ONLY、TIMED、来源审计、最小观察者和带分配普查的生命周期。覆盖不同输出尺寸、实际三分钟移动、实体创建／变化／移除、世界退出重进；不将资源压力序列当作单变量 FPS 对照，不修改产品画质。

## 运行

`<本体包>`、`<Extra包>` 是冻结配套发行 JAR 的绝对路径，`<探针>` 是两侧共用的同一探针包绝对路径，`<标签>` 区分基线／候选及重复顺序。未加载 Voxy 时省略 `--voxy`，移动样本额外传入 `--moving`。容量按当前 X-12 的 20 GiB 软上限预检，超过时先清理可再生内容。

```powershell
.\gradlew.bat :render_diagnostics:runSourceEfficiency -PsourceCore=<本体包> -PsourceExtra=<Extra包> -PsourceProbe=<探针> -PsourceLabel=<标签> -PsourceVoxy=true -PsourceMoving=false
```

本轮实际使用的同一工具入口如下，从仓库根目录运行；`<组名>` 为有界目录标签。已有 STOP 不覆盖；只有查清普通失败或主动停止的原因后才建立独立组，设备丢失不得通过新组重试。

```powershell
python -B -X utf8 components/render_diagnostics/benchmark/source_efficiency.py --core <本体包> --extra <Extra包> --probe <探针> --label <标签> --series <组名> --voxy
```

工具复用现有城市执行器，准备和清理由工具执行，不读写日常或外部实例。静止等待区块和 Voxy 生产计数平静，再依次采集 FRAME_ONLY 和 TIMED；计时口径不混用。移动从每个窗口起点按同一 5 Hz、振幅 4 方块的往返路线推进，结束恢复初始位置。记录实际相机范围及其他进程干扰；这是一条相机诊断路线，不等于手工移动体验。

无 Voxy 调查使用下列入口，`<清单>` 为冻结的 Core／Extra／探针 JSON 清单，`<组名>` 与 `<标签>` 不能覆盖已有用例。`--resolution` 可选 720、1080、1440、2160，必须先核对预算；没有授权不因工具支持而启动压力运行。

```powershell
python -B -X utf8 components/render_diagnostics/benchmark/renderer_campaign.py --manifest <清单> --series <组名> --label <标签> --resolution 1080
```

对齐只读冻结的 Prism 基准时，额外传 `--settings-input <配置输入名>`，该名称必须已在 `inputs.toml` 和 `local.toml` 登记并通过哈希核验；需要对齐来源内存时使用 `--memory-max <上限>`、`--memory-min <初始值>`。配置核验工具依赖 benchmark 的 requirements，PyYAML 复用 inventory 的同一声明。当前设置核验限定于已审阅的内置 Vanilla PT／DLSS 预设，不解释任意自定义算法包。

设置快照另生成 `expected-rendering.json`，探针在计时前读取已加载模块、普通选项及实际资源包顺序，保存 `effective-*.json` 并逐项比较，不改写实际模块。算法包的最大反弹和半透明模式不能由旧 `Options.rayBounces` 或源码默认值代替；缺少生效核验的样本拒收。输出尺寸、静音、窗口及失焦暂停的覆盖单独记录，FG 应关闭后测真实帧时间。

固定相机移动另传 `--moving`，沿用上述 5 Hz 路线并核验实际位移；归因另传 `--plan attribution`。加载链另传 `--plan loading`，相机使用既有高空加载命令，不能和保存相机的稳态 FPS 直接比较。生命周期另传 `--lifecycle --census --probe <生命周期探针>`，最长有界运行，飞行位移／转向不足时失败；`<生命周期探针>` 必须是明确冻结、包含该控制器的独立探针 JAR。实际阶段和内存数据保存在 `lifecycle.jsonl` 及分析文件中。

最小观察者校准另传 `--controller-only --without-aftermath --probe <控制探针>`；只保留一个外层控制／计时钩子，不加载完整来源审计及 Native 采集器。它不提供分段 GPU 时间，CPU 故障面包屑仍保留。生命周期可在客户端运行期间用同一工具的 `observe_wddm(<用例目录>)` 记录本进程 GPU 内存，记录器有时间上限，不操作其他进程。

## 通过标准

同组两侧探针、夹具、选项、来源模组及请求范围身份一致；实际加载 DLL 与冻结 JAR 一致，正常保存退出，数据连续且无设备丢失。设备丢失立即保全并停止该组，不自动重试。收益由相同模式、静止／移动各自的整帧与资源分布判断，变化未超过波动时报告不确定；不要求必须获得正收益才能保留有效证据。重载仍须成功，功能回归和用户目视另行验收。

调查用例不要求取得收益，但必须说明观察者配置、分辨率、实际运行路线、阶段收敛和数据边界。生命周期必须完成移动、实体移除和退出重进，缺阶段不能当作通过；VMA 有效分配、分配块、WDDM 本进程与整卡 NVML 分开报告。最小校准必须确认完整采集钩子与 Aftermath 实际关闭，同一画面下才能判断诊断是否造成主要退化。
