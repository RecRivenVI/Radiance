# A/B人工验收入口（草稿，尚未交付）

当前仍在实施。不要据本草稿运行半成品；最终JAR/hash/工作包矩阵通过后由根交付可用入口。

正式两组：
- A：分发Radiance单JAR + NeoForge/Minecraft运行时 + 单JAR合法嵌入库。
- B：A + Create6.0.10、Sable2.0.5、Aeronautics bundled1.3.2三个官方外层JAR。嵌套JAR已经提供Flywheel1.0.6、Ponder1.0.82、Veil4.3.2、SableCompanion1.6.0、Simulated/Offroad1.3.2与Registrate等；不额外复制重复的内嵌mod。

只读JAR审计：Evidence/runtime-jar-closure.json。3外层、14个含嵌套jar，所声明CLIENT/BOTH硬依赖mod ID无缺失。版本范围另核；这不是实际加载通过。

仓库Gradle新增 prepareManualBase/prepareManualFull（只准备）与 runManualBase/runManualFull（用户启动）。默认写build/manual-acceptance，也可以用 -Pradiance.acceptanceDirectory 指定本handoff/Acceptance。
两组loadedMods为空、packagedLaunch sourceSet、去掉MOD_CLASSES，确保使用最终分发JAR；未拥有的已有game目录会拒绝，未知额外JAR会拒绝。不会删文件或改Prism/既有实例/存档配置资源包。

最终用户命令（待最终产物验证后生效）：
```powershell
$env:JAVA_HOME = 'C:\Program Files\BellSoft\LibericaJDK-21-Full'
.\gradlew.bat prepareManualBase prepareManualFull '-Pradiance.acceptanceDirectory=D:/Workspaces/Artifacts/RadianceMCVRCompatibilityImplementation/20260915-082227-handoff/Acceptance'
.\gradlew.bat runManualBase '-Pradiance.acceptanceDirectory=D:/Workspaces/Artifacts/RadianceMCVRCompatibilityImplementation/20260915-082227-handoff/Acceptance'
.\gradlew.bat runManualFull '-Pradiance.acceptanceDirectory=D:/Workspaces/Artifacts/RadianceMCVRCompatibilityImplementation/20260915-082227-handoff/Acceptance'
```

待完善场景清单：初始化/加载界面/菜单设置与图标、首次建世界、F3+B/G与名称牌、普通第一人称/睡眠detached、粒子/White Ash/天气维度、资源重载、Create实例/embedding/破坏/光照变化、Ponder UI、Sable子世界、Aero/Simulated Diagram/StickyNote/EndSea/热气球/levitite、离开与重进世界、窗口resize及退出。中间模组子集只用于必要归因。

本线程未启动任何客户端或交互窗口；所有真实目视由用户完成。
