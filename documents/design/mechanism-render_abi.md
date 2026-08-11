# 跨语言渲染整数合同

`components/native_renderer/src/common/render_abi.def` 是图元、格式、几何、坐标、查询掩码、alpha 编码、顶点步长及材料位域的整数来源。`render_abi.hpp` 让 C++ 和 GLSL 直接读取它；Target 的 `generateRenderAbi` 将同一表生成为 build 下的 `RenderAbi.java`，产品源码不维护生成副本。

## 数据边界

同一数值在不同域可有不同含义：compact 输入位、最终 MaterialVertex 位、来源面规则、Flywheel 材料与实例外观分别使用命名前缀，不能互相套用。整数表规定编码，不代替对应的材质、颜色和查询合同。改变字段含义时须同步所有生产者、JNI 和 CPU/GPU 解码，并验证实际字节。

阶段 A 的线描述符版本和布局维持原值。PBR canonical 为 128 字节，compact 为 100 字节；POSITION、MaterialVertex 和线源布局由实际结构体与 Java VertexFormat 校验。生成器只接受有界的无符号整数声明，拒绝重复名称、非法表达式和超出 uint32 范围的值，Java 对高位为 1 的值保留其二进制位，输出保持表中的声明顺序，不带时间戳。

来源 alpha 的高三位保存丢弃阶段，最终材料位 20～22 保存同一规则，不增加顶点步长。实体提交通过 V3 JNI 入口携带逐几何 RGBA，输入版本为 3；刚性提交 V2 入口使用版本 3、152 字节记录与 136 字节颜色偏移，并验证记录步长和完整字节范围。来源 geometry 字的 24～26 位编码发光政策，转换后与 GPU 材质类型隔离；旧输入版本被拒绝。颜色记录不进入模型缓存身份。

## 构建与校验

Java 编译、源码包与 JNI 头生成从生成任务的输出取源码；生成器测试由 native_renderer 的 check 执行。C++ 保留实际 sizeof/布局断言，GLSL 使用同一头和表，离线编译与运行时 shader 包均携带所需 include。

真实 JNI 注册表在不初始化渲染器的情况下返回原生值与实际结构体大小，验证生成 Java 与加载 DLL 的对应；独立基线向量锁定旧协议的值。CPU/GPU 实体转换、材料面/查询与线条 JNI 夹具继续覆盖实际位模式、边界值、源布局和最终展开，不用源码文本相同替代行为回归。

Mojang Uniform 类型码、Veil GL 类型码和 GLSL 类型字符串属于不同来源协议，继续由相应适配器转换；Vulkan/GL 本身定义的常量由各 SDK 拥有，不复制到本表。该合同不建立资源缓存，不改变锁、代际、提交顺序或 GPU 退休。
