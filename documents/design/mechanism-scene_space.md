# 捕获空间与最终实例变换

世界几何的最终实例放置由 `scene_space.hpp` 决定。Java 的 `SceneSpace` 描述来源顶点所在的空间，`Constants.Coordinates` 只适配现有调用接口；来源 renderer 的 PoseStack、pivot 和动画仍由各适配器执行，不能在提交时再应用一次。

## 坐标与精度

| 合同 | 输入已具备的变换 | 场景放置 |
| --- | --- | --- |
| WORLD | 原 renderer 的局部姿态或显式实例姿态 | double 原点减相机，转 float 后加局部平移；实体与刚性模型共用 |
| CAMERA | 手持视图及 FOV 相关变换 | 只应用 cameraView inverse；不再次应用实例姿态 |
| CAMERA_SHIFT | 生产者已处理相机相对旋转 | 只应用 cameraView inverse 的平移 |
| 稳定原点实例 | Flywheel 的 embedding×local，整数 renderOrigin | 原点、局部平移、相机在 double 中求差后转 float |
| double 世界仿射 | 区段或外部结构的完整世界矩阵 | double 平移减相机，然后序列化 |

不同精度步骤有意保留来源路径的运算顺序。`affineRows` 唯一负责列主序矩阵到原生 3×4 布局的转换；普通实体、刚性实例、Flywheel、活动外部区段和静态区段快照都使用它。静态快照仍只访问有效实例，不恢复容量扫描。

GUI、结构图解、默认 Ponder 和离屏 FBO 的 model-view/projection 属于光栅目标适配，不进入世界 TLAS 放置。将它们与世界输入共用一种提交身份，不意味着混用投影或追踪方式。

## 法线与历史

位置、方向、几何绕序、BRDF 法线和线截面基底具有不同用途。来源显式 normal matrix 经 `explicitNormalCorrection` 保留；普通来源仍使用自己的 normal matrix，不归一化掉非均匀缩放或镜像。来源面与实例 parity 继续由现有面合同补偿一次。

共享空间函数不拥有资源，不修改帧、对象、部件或资源代际，也不缩短 GPU 退休。上一帧矩阵必须对应同一视图及有效几何身份；拓扑身份由独立合同校验。

## 验证

`RenderSpace` 的来源端点插值与最终姿态模式使用同一父子空间。Java 与 Native 各缓存局部版本及父组合版本；更新旁支不清空整个空间表。删除仅结束未来参与，当前批次已持有的执行版本保留到既有帧退休边界。Sable 直接传自己的 `renderPose`，不启用端点插值。

单元测试覆盖端点时间钳制、最短弧、镜像／非均匀缩放、奇异缩放拒绝、最终姿态切回、旁支不失效和父子删除。隔离探针另实际调用 motion／sample JNI；几何／运动历史与人工动态体验仍需分别核对。

`mcvr.scene-space` 以保留旧公式的夹具逐元素比较三种坐标模式及无相机回退，并比较实体/刚性、稳定原点、double 区段、显式 normal 和镜像奇偶。正负缩放、非均匀缩放、连续组合旋转及 ±三千万坐标分别覆盖。旧公式仅存在于测试，不增加正式逐帧双重矩阵执行。
