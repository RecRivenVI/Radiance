dependencies {
    implementation(project(":configuration"))
    implementation(project(":component"))
    implementation(project(":compliance"))
    implementation(libs.foojay.plugin)
    implementation(libs.loom.plugin)
    implementation(libs.moddev.plugin)
    implementation(libs.spotless.plugin)
}

gradlePlugin {
    plugins {
        create("conventions") {
            id = "io.github.recrivenvi.conventions"
            implementationClass = "io.github.recrivenvi.conventions.platform.ConventionsPlugin"
        }
    }
}
