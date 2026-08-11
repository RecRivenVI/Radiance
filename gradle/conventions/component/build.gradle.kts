// 项目组件应用这个插件；它只依赖 Spotless，不带 Minecraft 构建插件，避免与组件自己的依赖冲突。
dependencies { implementation(libs.spotless.plugin) }

gradlePlugin {
    plugins {
        create("component") {
            id = "io.github.recrivenvi.conventions.component"
            implementationClass = "io.github.recrivenvi.conventions.component.ComponentPlugin"
        }
    }
}
