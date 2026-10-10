plugins {
    `java-gradle-plugin`
}

group = "io.github.recrivenvi"

dependencies {
    implementation(libs.spotless.plugin)
}

tasks.withType<JavaCompile>().configureEach {
    options.release = 25
    options.encoding = "UTF-8"
}

gradlePlugin {
    plugins {
        create("component") {
            id = "io.github.recrivenvi.component"
            implementationClass = "io.github.recrivenvi.component.ComponentPlugin"
        }
    }
}
