import org.gradle.api.tasks.Exec
import org.gradle.api.tasks.JavaExec
import org.gradle.api.tasks.bundling.Jar
import org.gradle.api.tasks.testing.Test

plugins { java }

val inventory = sourceSets.create("inventory")
val inventoryTest =
    sourceSets.create("inventoryTest") {
        compileClasspath += inventory.output
        runtimeClasspath += inventory.output
    }
val benchmark = sourceSets.create("benchmark")
val benchmarkTest =
    sourceSets.create("benchmarkTest") {
        compileClasspath += benchmark.output
        runtimeClasspath += benchmark.output
    }

dependencies {
    add(inventory.implementationConfigurationName, libs.asm.tree)
    add(inventory.implementationConfigurationName, libs.gson)
    add(inventoryTest.implementationConfigurationName, libs.asm.tree)
    add(inventoryTest.implementationConfigurationName, libs.gson)
    add(inventoryTest.implementationConfigurationName, platform(libs.junit.bom))
    add(inventoryTest.implementationConfigurationName, libs.junit.jupiter)
    add(inventoryTest.runtimeOnlyConfigurationName, libs.junit.launcher)
    add(benchmark.implementationConfigurationName, libs.asm.tree)
    add(benchmarkTest.implementationConfigurationName, libs.asm.tree)
    add(benchmarkTest.implementationConfigurationName, platform(libs.junit.bom))
    add(benchmarkTest.implementationConfigurationName, libs.junit.jupiter)
    add(benchmarkTest.runtimeOnlyConfigurationName, libs.junit.launcher)
}

val inventoryTests =
    tasks.register<Test>("inventoryTests") {
        testClassesDirs = inventoryTest.output.classesDirs
        classpath = inventoryTest.runtimeClasspath
    }
val benchmarkTests =
    tasks.register<Test>("benchmarkTests") {
        testClassesDirs = benchmarkTest.output.classesDirs
        classpath = benchmarkTest.runtimeClasspath
    }
val pythonInventoryTest =
    tasks.register<Exec>("pythonInventoryTest") {
        commandLine(
            "python",
            "-m",
            "unittest",
            "discover",
            "-s",
            "inventory",
            "-p",
            "test_inventory.py",
            "-v",
        )
    }
val offlineInventoryClasses = layout.buildDirectory.dir("classes/offlineInventory")
val compileOfflineInventory =
    tasks.register<JavaCompile>("compileOfflineInventory") {
        source(fileTree("inventory") { include("*.java") })
        classpath = configurations.getByName(inventory.compileClasspathConfigurationName)
        destinationDirectory.set(offlineInventoryClasses)
    }
val offlineInventoryTest =
    tasks.register<JavaExec>("offlineInventoryTest") {
        dependsOn(compileOfflineInventory)
        classpath =
            files(offlineInventoryClasses) +
                configurations.getByName(inventory.runtimeClasspathConfigurationName)
        mainClass.set("DrawInventoryTest")
    }
val renderInventory =
    tasks.register<JavaExec>("renderInventory") {
        classpath = inventory.runtimeClasspath
        mainClass.set("com.radiance.audit.tools.GlInventory")
        args(
            providers.gradleProperty("inventory.manifest").getOrElse(""),
            providers.gradleProperty("inventory.output").getOrElse(""),
        )
    }
val configuration = "RelWithDebInfo"
val nativeDirectory = layout.buildDirectory.dir("native/collector")
val windows = System.getProperty("os.name").lowercase().contains("windows")
val nativeJava =
    javaToolchains
        .launcherFor { languageVersion.set(JavaLanguageVersion.of(21)) }
        .get()
        .metadata
        .installationPath
        .asFile
val generator =
    if (windows) {
        val help = providers.exec { commandLine("cmake", "--help") }.standardOutput.asText.get()
        Regex("(?m)^\\s*\\*\\s+(Visual Studio \\d+ \\d+)\\s+=").find(help)?.groupValues?.get(1)
            ?: error("Visual Studio C++ is required")
    } else null
val configureNativeAudit =
    tasks.register<Exec>("configureNativeAudit") {
        onlyIf { windows }
        environment("JAVA_HOME", nativeJava)
        commandLine(
            "cmake",
            "-S",
            file("native/collector"),
            "-B",
            nativeDirectory.get().asFile,
            "-DMCVR_ROOT=${file("../native_renderer")}",
            "-DRADIANCE_AUDIT_RENDERDOC_CAPTURE=OFF",
        )
        if (generator != null) args("-G", generator, "-A", "x64")
    }
val buildNativeAudit =
    tasks.register<Exec>("buildNativeAudit") {
        onlyIf { windows }
        dependsOn(configureNativeAudit)
        commandLine(
            "cmake",
            "--build",
            nativeDirectory.get().asFile,
            "--config",
            configuration,
            "--parallel",
            "4",
        )
    }
val testNativeAudit =
    tasks.register<Exec>("testNativeAudit") {
        onlyIf { windows }
        dependsOn(buildNativeAudit)
        commandLine(
            "ctest",
            "--test-dir",
            nativeDirectory.get().asFile,
            "-C",
            configuration,
            "--output-on-failure",
        )
    }

tasks.named<Jar>("jar") {
    if (windows) {
        dependsOn(buildNativeAudit)
        from(nativeDirectory.map { it.file("$configuration/radiance-audit-native.dll") }) {
            into("META-INF/natives/windows-x86_64")
        }
    }
}

val benchmarkHooksJar =
    tasks.register<Jar>("benchmarkHooksJar") {
        archiveFileName.set("audit-benchmark-hooks.jar")
        destinationDirectory.set(layout.buildDirectory.dir("benchmark-support"))
        from(benchmark.output) { include("com/radiance/audit/benchmark/RuntimeHooks*.class") }
    }
val benchmarkTransformerJar =
    tasks.register<Jar>("benchmarkTransformerJar") {
        archiveFileName.set("audit-benchmark-transformer.jar")
        destinationDirectory.set(layout.buildDirectory.dir("benchmark-support"))
        from(benchmark.output) {
            include("com/radiance/audit/benchmark/BenchmarkTransformer*.class")
        }
        from(
            provider {
                configurations.getByName(benchmark.runtimeClasspathConfigurationName).map {
                    zipTree(it)
                }
            },
        ) {
            exclude("META-INF/MANIFEST.MF", "module-info.class", "META-INF/versions/**")
            duplicatesStrategy = DuplicatesStrategy.EXCLUDE
        }
        from(files("../../LICENSE", "licenses")) { into("META-INF/licenses") }
    }
val benchmarkAgentJar =
    tasks.register<Jar>("benchmarkAgentJar") {
        archiveFileName.set("radiance-benchmark-agent.jar")
        destinationDirectory.set(layout.buildDirectory.dir("benchmark-support"))
        manifest.attributes("Premain-Class" to "com.radiance.audit.benchmark.AgentBootstrap")
        from(benchmark.output) { include("com/radiance/audit/benchmark/AgentBootstrap*.class") }
        from(benchmarkHooksJar.flatMap { it.archiveFile })
        from(benchmarkTransformerJar.flatMap { it.archiveFile })
        from(files("../../LICENSE", "licenses")) { into("META-INF/licenses") }
    }

tasks.named("check") {
    dependsOn(
        inventoryTests,
        benchmarkTests,
        pythonInventoryTest,
        offlineInventoryTest,
        testNativeAudit,
    )
}

tasks.named("assemble") { dependsOn(benchmarkAgentJar) }

// Validation preparation is owned by this tool; it never touches an external game instance.
val prepareGameValidation =
    tasks.register("prepareGameValidation") {
        dependsOn(benchmarkAgentJar)
        doLast {
            for (name in listOf("smoke-render_diagnostics", "conformance-benchmark")) {
                val directory = file("../../validations/$name/instance/1.21.1-neoforge/client")
                directory.mkdirs()
                directory
                    .resolve(".radiance-audit-test-instance")
                    .writeText("migration validation\n")
                val options = directory.resolve("options.txt")
                val rows =
                    if (options.isFile)
                        options.readLines().filterNot {
                            it.startsWith("soundCategory_master:") ||
                                it.startsWith("onboardAccessibility:")
                        }
                    else emptyList()
                options.writeText(
                    (rows + listOf("soundCategory_master:0.0", "onboardAccessibility:false"))
                        .joinToString("\n", postfix = "\n"),
                )
            }
        }
    }
