# 新 SPI 加载链共同契约

冻结基线见 before-manifest.json。旧 Early Display 17 文件仅为退役历史，不恢复其模拟 GL、截图过桥或外置 patched FML 路线。保留上游工程目录；新增真实 bootstrap source set 与 MCVR loading renderer，不引入多 target 框架。

## 架构

- 外层 Radiance.jar 仅含 `com.radiance.bootstrap` 早期 SPI、公开 candidate locator、UI 状态/模型及 native 入口；内层正常 Mod JAR 的 modId 仍为 radiance。
- SERVICE 外层和 GAME 内层不得包含相同 package。GAME 通过 SERVICE 中唯一的共享状态/API通信。
- MCVR `core` 只初始化一次，尽量让窗口、Vulkan instance/device/swapchain 从 early 到 game 复用。由 SERVICE 装载 core DLL。GAME 的原 JNI 类使用标准 JNI RegisterNatives 绑定同一 DLL 中既有导出，不再次 System.load 同一库。
- 使用标准官方 SPI/locator、公开 module-aware factory 反射、标准 JNI；不改写 FML JAR/缓存，不使用 Java agent、Unsafe 类加载器修改或额外功能性启动参数。
- 默认 provider 名称 `radiance`，不能占用 fmlearlywindow。尊重显式 earlyWindowControl=false；首次全新实例默认可通过 bootstrap 选择自身 provider。

## UI draw-list 协议（native endian，小端机器）

`com.radiance.bootstrap.ui.LoadingScene` 由 UI worker 提供。使用发布版 FML4.0.43 同样的布局、字体/贴图、进度、颜色和动画计算；仅替换后端。所有绘制为 GPU Vulkan rasterization，不做 CPU 像素光栅化。

- `DrawSink.texture(int key,int width,int height,ByteBuffer rgba,boolean linear)` 上传纹理。像素RGBA8；font atlas把灰度放R通道，role0采样R为alpha。key由UI模型管理。
- 顶点统一20字节：float x,y,u,v，随后4字节RGBA（packed int为官方ABGR，native little endian）。固定canvas中的位置；不做CPU变换。
- batches统一16字节：int firstVertex, vertexCount, role, textureKey。role为FONT=0、TEXTURE=1、BAR=2。顶点已按官方QUADS索引0,1,2,1,3,2展开为三角形。
- canvas尺寸=854*scale × 480*scale。坐标逻辑y向下；输出readback第0行是逻辑y=0，对照GL离屏glReadPixels的第0行。屏幕复合不再次翻转。
- GL参考的颜色/alpha混合均为 SRC_ALPHA/ONE_MINUS_SRC_ALPHA，RGBA8 UNORM画布；不得静默用premultiplied alpha或sRGB替代。
- `LoadingScene.Frame` 返回direct vertices/batches buffers，canvasWidth、canvasHeight、backgroundAbgr；调用方保证native消费前不释放。
- UI worker向SPI worker提供可用构造/render/addMojangTexture/close API，并及时协调具体签名。不要把 FML 私有class反射作为产品布局实现。

## NativeRuntime 约定（由 bootstrap worker声明，native worker实现）

类：`com.radiance.bootstrap.NativeRuntime`。避免重载 native 方法。

```java
static native void initialize(String runtimeDirectory, String[] glfwCandidates, long window);
static native void uploadTexture(int key, int width, int height, ByteBuffer rgba, boolean linear);
static native void renderFrame(ByteBuffer vertices, ByteBuffer batches, int batchCount,
    int canvasWidth, int canvasHeight, int backgroundAbgr, int framebufferWidth, int framebufferHeight,
    float opacity, boolean gameFrame, boolean paintBackground);
static native void handoff(long expectedWindow);
static native void bindGameNatives(Class<?> type, String[] names, String[] descriptors, String[] symbols);
static native long[] identities(); // window, instance, physicalDevice, device, swapchain
static native byte[] captureCanvas(); // 用于真实GPU离屏对照，不代替实时画面
static native void releaseLoading(); // 只释放loading资源，不释放主renderer
static native void closeBeforeGame(); // 初始化/用户关闭失败路径
```

early renderFrame管理submit/present并保持下一帧context可用；gameFrame只向当前主renderer的UI目标记录实时loading画面，游戏仍管理submit/present。handoff不销毁device/swapchain或loading模型。GAME initRendererNative需识别已初始化的同窗口并接续，而不是第二次Singleton::init。

当前Renderer Framework构造已有UIModule且worldPipeline可空，bootstrap需先提取core/xess/compiled shaders等必要资源。帧/队列/command pool须避免早晚阶段并发，后台ticker在窗口交接前停止并等待；可利用主线程periodicTick继续推进。size=0时不强行创建0尺寸资源。

## 验证

先做单外层JAR真实Mod加载，再做默认OpenGL与Vulkan固定输入离屏像素比较及实际连续窗口。未通过的视觉场景不能宣称完全一致。保留每轮日志和错误，任何源文件删除移入回收站；无commit/push。
