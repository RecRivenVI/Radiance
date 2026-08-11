plugins {
    base
    alias(libs.plugins.spotless)
}

// 子项目中的 libs 指向子项目自己的扩展，所以先取出根项目的版本目录。
val catalog = libs

subprojects {
    group = "io.github.recrivenvi"
    apply(plugin = "java-gradle-plugin")
    dependencies {
        "testImplementation"(platform(catalog.junit.bom))
        "testImplementation"(catalog.junit.jupiter)
        "testRuntimeOnly"(catalog.junit.launcher)
    }
    tasks.withType<JavaCompile>().configureEach {
        options.release = 25
        options.encoding = "UTF-8"
    }
    tasks.withType<Test>().configureEach { useJUnitPlatform() }
}

tasks.check { dependsOn(subprojects.map { "${it.path}:check" }) }

spotless {
    java {
        target("*/src/*/java/**/*.java")
        googleJavaFormat(libs.versions.google.java.format.get()).aosp()
    }
    kotlinGradle {
        target("*.gradle.kts", "*/*.gradle.kts")
        ktfmt(libs.versions.ktfmt.get()).kotlinlangStyle()
    }
    format("specification") {
        target("specification/**/*.md")
        trimTrailingWhitespace()
        leadingTabsToSpaces(4)
        endWithNewline()
    }
}
