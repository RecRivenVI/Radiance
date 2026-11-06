# Native 静态场景重放

开发重放器使用当前生产世界管线，在 JVM 退出后对同一份冻结输入做着色器对照。它不模拟 Minecraft、区段流送、GUI 或动态来源生产，重放帧间隔不能代替游戏整帧性能。

已通过生产入口的小场景和城市捕获、退出后独立出图、固定视角、短转头及同进程着色器重载。冻结输入只解码一次，TextureMapping 存于堆上，ZIP 和设备地址在载入时建立索引。游戏与重放分别预热时间历史，反射和运动物体的历史差异仍须单列；结果见[本轮调查](../research/investigation-scene_replay.md)。

## 构建与捕获

从仓库根目录执行开发构建。普通构建不启用重放代码，加载器和快照不进入发行包。

```powershell
.\gradlew.bat :native_renderer:buildSceneReplay :version:1.21.1-neoforge:distributedJar :version:1.21.1-neoforge:probeClasses '-Pnative.sceneReplay=true'
python -B components/render_diagnostics/benchmark/scene_replay.py capture-city --jar versions/1.21.1-neoforge/build/libs/radiance-1.21.1-neoforge-0.1.5-alpha.jar --extra ../Radiance-Extra/versions/1.21.1-neoforge/build/libs/radiance_extra-1.21.1-neoforge-0.1.0-alpha.jar --fixture validations/performance-sources/result/gpu-lossless-20261105/inputs/city.zip --settings validations/performance-sources/result/gpu-lossless-20261105/inputs/settings.zip --snapshot validations/performance-scene_replay/result/snapshot
```

此命令沿用已冻结城市和配置，以 2560×1440、32 视距运行一个静音项目实例，计时后捕获并正常退出。输入 ZIP 是可复用的地图／配置输入，不从用户实例现场复制。其他项目自有捕获流程可先准备目录，再在启动前设置环境：

```powershell
python -B components/render_diagnostics/benchmark/scene_replay.py prepare --jar versions/1.21.1-neoforge/build/libs/radiance-1.21.1-neoforge-0.1.5-alpha.jar --snapshot validations/performance-scene_replay/result/snapshot
$env:RADIANCE_SCENE_CAPTURE = "$PWD/validations/performance-scene_replay/result/snapshot"
$env:RADIANCE_SCENE_CAPTURE_MAX_MIB = '4096'
```

启动匹配开发包的项目内验证实例；环境变量必须在建立设备与几何之前设置。城市探针在正式计时结束后自动请求快照。其他项目自有捕获流程在目标视角稳定后执行下列命令，不使用键鼠注入：

```powershell
Set-Content -LiteralPath "$env:RADIANCE_SCENE_CAPTURE/capture.request" -Value capture -NoNewline
```

`scene.json` 发布才表示该帧读回完成；`capture-error.txt` 表示失败。捕获会扰动 CPU／GPU，不能计入稳定性能窗口。一次进程只捕获一次；确认完成后正常关闭游戏，再启动重放。

## 重放与着色器重载

在快照旁准备 `plan.json`，每一项代表同进程的一个窗口。`shader_pack` 使用绝对路径；A/B 必须相同相机路线、预热帧数、正式帧数及设置。示例中的 `<算法包>` 替换为快照 `runtime/shaders/world/ray_tracing/vanilla-pt.zip` 的绝对路径。

```json
[
  {"name":"A1","shader_pack":"<算法包>","warmup":256,"frames":480,"route":"fixed"},
  {"name":"A2","shader_pack":"<算法包>","warmup":256,"frames":480,"route":"fixed"},
  {"name":"turn","shader_pack":"<算法包>","warmup":256,"frames":480,"route":"turn"}
]
```

```powershell
python -B components/render_diagnostics/benchmark/scene_replay.py replay --snapshot validations/performance-scene_replay/result/snapshot --plan validations/performance-scene_replay/result/plan.json --output validations/performance-scene_replay/result/comparison
```

`--core` 默认使用组件的 `build/runtime/scene_replay_core.dll`，普通构建不会覆盖它。依赖库需要留在该 DLL 的同一目录。若使用本次开发构建的完整 CMake 安装，也可指定 `--core components/native_renderer/build/v/bin/core.dll`。加载器和私有 DLL 均不进入发行 JAR。构建生成匹配的 PDB；若已按哈希确认与 `build/v/src/core/RelWithDebInfo/core.pdb` 相同，只保留该份并记录符号位置。后续重编前保留仍在调查故障的匹配身份。每个窗口通过现有 GPU 同步与管线重建切换包，重新初始化时间历史和相同的采样序列；不在在途管线中替换资源。`turn` 按帧执行一段有限的原地转头，世界、姿态和动画时间冻结。工具不读取或注入桌面键鼠。

Windows 上遇到着色器压缩包解包路径过长时，可用 `subst` 将验证结果目录映射到空闲盘符，再通过该盘符传入快照、计划、输出目录及计划中的着色器包路径。

结果包含 `frames.csv`、`gpu.csv`、`host.csv`、`result.json`、`analysis.json`、每个窗口的 PNG 和 `game-reference.png`。`host.csv` 区分交换链获取、槽位 fence、提交、呈现和限帧等主机调用。图片来自窗口之外的额外捕获帧，A/B 使用相同的局部帧号。GPU 阶段存在包含与并行关系，不能相加。`pipeline_prepare_ms`、`scene_prepare_ms`、`warmup_ms` 分开记录。启动时 JSON／ZIP 验证早于这些阶段。预热帧不进入正式统计。异常退出或设备丢失保全结果并停止，不自动重试。

有重复窗口时，`analysis.json` 的 `repeatedImageDifferences` 按相同着色器包路径、路线、帧数和格式记录相对首个窗口的 RGB 8 位逐通道平均绝对差。差值用于提醒检查原图，无法单独判定画面正确或 HDR 输出一致。若同配置在进程内重建后出现明显画面差异，用独立进程重放每个窗口，保留失败窗口，等画面一致后再比较耗时。

## 格式和边界

格式 `radiance-native-scene-v2` 由 `scene.json` 和 ZIP／Deflate 的 `blobs.zip` 组成，属于本地构建 ABI，不是跨版本交换格式。记录来源 JAR、Native、Extra 和算法包身份，以及实际管线、选项、相机、天空、纹理／采样器、BLAS 输入、实例、附加层、船内排水域和当前消费的介质接触过滤数据。原设备地址仅作索引；载入时重定位基础表与嵌套地址，缺失引用明确失败。

启用主动光源采样时，快照包含该帧实际使用的区块光源表及其地址引用。FG、LoD 覆盖表和世界后置光栅批次仍无法捕获，检测到这些输入时会明确报错。含模组场景使用 Extra 已交给本体的数据，工具不重新解释模组。历史位置取冻结姿态，SHARC、降噪和曝光独立预热，因此游戏捕获帧与重放帧不要求逐像素相等；需同时观察可定位差异与重复窗口波动。HDR 内部画布和 SDR PNG 不能证明显示器 HDR 呈现正确。

只保留一份城市快照，按 X-12 清理可再生环境和旧窗口图片。原型及旧调查保留历史含义，不用于填补当前工具缺失的计时项。
