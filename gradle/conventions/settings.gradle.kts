dependencyResolutionManagement {
    repositories {
        gradlePluginPortal()
        maven("https://maven.neoforged.net/releases")
        maven("https://maven.fabricmc.net/")
    }
    versionCatalogs { create("libs") { from(files("../libs.versions.toml")) } }
}

rootProject.name = "conventions"

include("configuration", "platform", "component", "compliance")
