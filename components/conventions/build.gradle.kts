plugins {
    `java-gradle-plugin`
    alias(libs.plugins.spotless)
}

group = "io.github.recrivenvi"

dependencies {
    implementation("io.github.recrivenvi:configuration")
    implementation(project(":component"))
    implementation(libs.foojay.plugin)
    implementation(libs.loom.plugin)
    implementation(libs.moddev.plugin)
    implementation(libs.spotless.plugin)
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.launcher)
}

tasks.withType<JavaCompile>().configureEach {
    options.release = 25
    options.encoding = "UTF-8"
}

tasks.test {
    useJUnitPlatform()
}

tasks.check { dependsOn(":component:check") }

gradlePlugin {
    plugins {
        create("conventions") {
            id = "io.github.recrivenvi.conventions"
            implementationClass = "io.github.recrivenvi.conventions.ConventionsPlugin"
        }
    }
}

spotless {
    java {
        target("src/*/java/**/*.java", "component/src/*/java/**/*.java")
        googleJavaFormat(libs.versions.google.java.format.get()).aosp()
    }
    kotlinGradle {
        target("*.gradle.kts", "component/*.gradle.kts")
        ktfmt(libs.versions.ktfmt.get()).kotlinlangStyle()
    }
}
