# 开发环境

本仓库需要 JDK 25 与网络；其余工具由 Gradle 自动获取。

## 需要的软件

| 软件 | 说明 |
| --- | --- |
| JDK 25 | 运行 Gradle 守护进程。[gradle-daemon-jvm.properties](../../gradle/gradle-daemon-jvm.properties) 让 Gradle 在本机查找 JDK 25，找不到时自动下载 |
| Target 所需的 JDK | 由 Java 工具链按 `target.properties` 的 `java_version` 自动选择或下载 |
| Git | 合规检查通过 Git 确定哪些文件属于仓库 |
| 网络 | 首次构建需要下载 Gradle、加载器、Minecraft 与格式化工具；Markdown 检查工具 rumdl 从它的 GitHub 发布页下载，版本固定在 [libs.versions.toml](../../gradle/libs.versions.toml) |

## 首次构建

```powershell
.\gradlew.bat build
```

首次构建会下载并处理每个已登记 Target 的 Minecraft 与加载器，耗时较长；之后的构建使用缓存。Unix 使用 `./gradlew`。

## IDE

使用 IntelliJ IDEA 时打开仓库根目录，并把 Gradle JVM 设为 JDK 25。ModDevGradle 与 Loom 会为每个日常实例生成运行配置。

## 本机配置

在仓库根目录创建 `local.toml`，它被 Git 忽略，用于个人偏好、本地输入的路径与 Minecraft EULA 同意：

```toml
eula = true

[client]
memory-max = "6G"

[inputs]
examplemod = "D:/Mods/examplemod-1.0.0.jar"
```

`eula = true` 表示你已阅读并接受 [Minecraft EULA](https://aka.ms/MinecraftEULA)，运行独立服务端需要它，这一行只能由使用者本人写入或明确授权写入。实例设置与 [instances.toml](../../instances.toml) 使用相同的表，写在同名的表中才能替换共享预设，规则见 [configuration-repository](../reference/configuration-repository.md)。

项目在 `inputs.toml` 中声明了本地输入时，按其中的 `description` 与 `source` 取得文件，把路径写进 `[inputs]`，再执行 `.\gradlew.bat verifyInputs` 确认路径与哈希。
