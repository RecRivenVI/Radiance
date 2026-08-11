pluginManagement {
    includeBuild("gradle/conventions")
    repositories {
        gradlePluginPortal()
        maven("https://maven.neoforged.net/releases")
        maven("https://maven.fabricmc.net/")
    }
}

plugins { id("io.github.recrivenvi.conventions") }

rootProject.name = "Radiance"

targets { register("1.21.1-neoforge") }

components {
    product("native_renderer")
    tool("render_diagnostics")
}

compliance { sourceSets("bootstrap", "bootstrapTest", "probeTest", "packagedLaunch") }
