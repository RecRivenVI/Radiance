plugins {
    `java-gradle-plugin`
    alias(libs.plugins.spotless)
}

group = "io.github.recrivenvi"

dependencies {
    implementation(libs.tomlj)
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

gradlePlugin {
    plugins {
        create("compliance") {
            id = "io.github.recrivenvi.compliance"
            implementationClass = "io.github.recrivenvi.compliance.CompliancePlugin"
        }
    }
}

spotless {
    java {
        googleJavaFormat(libs.versions.google.java.format.get()).aosp()
    }
    kotlinGradle {
        ktfmt(libs.versions.ktfmt.get()).kotlinlangStyle()
    }
}
