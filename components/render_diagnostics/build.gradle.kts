import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID
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
val takeoverToolTest =
    tasks.register<Exec>("takeoverToolTest") {
        commandLine(
            "python",
            "-m",
            "unittest",
            "discover",
            "-s",
            "benchmark",
            "-p",
            "test_takeover_experiment.py",
            "-v",
        )
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
        takeoverToolTest,
        inventoryTests,
        benchmarkTests,
        pythonInventoryTest,
        offlineInventoryTest,
        testNativeAudit,
    )
}

tasks.named("assemble") { dependsOn(benchmarkAgentJar) }

tasks.register<Exec>("prepareQueryValidation") {
    dependsOn(benchmarkAgentJar)
    group = "verification"
    commandLine("python", "benchmark/prepare_query_validation.py")
}

tasks.register<Exec>("prepareLoadingValidation") {
    dependsOn(benchmarkAgentJar)
    group = "verification"
    commandLine("python", "-X", "utf8", "benchmark/prepare_loading_validation.py")
    providers.gradleProperty("loadingRenderDistance").orNull?.let {
        args("--render-distance", it)
    }
}

tasks.register<Exec>("analyzeLoadingValidation") {
    group = "verification"
    commandLine("python", "-X", "utf8", "benchmark/analyze_loading_validation.py")
}

val loadingFixtureTest =
    tasks.register<Exec>("loadingFixtureTest") {
        commandLine(
            "python",
            "-X",
            "utf8",
            "-m",
            "unittest",
            "discover",
            "-s",
            "benchmark",
            "-p",
            "test_loading*.py",
            "-v",
        )
    }

tasks.named("check") { dependsOn(loadingFixtureTest) }

val captureInputTest =
    tasks.register<Exec>("captureInputTest") {
        commandLine(
            "python",
            "-X",
            "utf8",
            "-m",
            "unittest",
            "discover",
            "-s",
            "benchmark",
            "-p",
            "test_gpu_capture.py",
            "-v",
        )
    }
val captureTests =
    tasks.register<Exec>("captureTests") {
        commandLine("python", "-X", "utf8", "benchmark/gpu_capture.py", "test", generator ?: "")
    }

tasks.register<Exec>("buildAftermathAgent") {
    group = "verification"
    onlyIf { windows }
    environment("JAVA_HOME", nativeJava)
    commandLine("python", "-X", "utf8", "benchmark/gpu_capture.py", "build", generator ?: "")
}

val captureFormatCheck =
    tasks.register<Exec>("captureFormatCheck") {
        commandLine("python", "-X", "utf8", "benchmark/gpu_capture.py", "format")
    }

tasks.named("check") { dependsOn(captureInputTest, captureTests, captureFormatCheck) }

tasks.register("prepareSceneDiagnosis") {
    group = "verification"
    doLast {
        val directory =
            file("../../validations/performance-scene_diagnosis/instance/1.21.1-neoforge/client")
        directory.mkdirs()
        val options = directory.resolve("options.txt")
        val overrides =
            mapOf(
                "soundCategory_master" to "0.0",
                "soundCategory_music" to "0.0",
                "onboardAccessibility" to "false",
                "fullscreen" to "false",
                "overrideWidth" to "0",
                "overrideHeight" to "0",
            )
        val rows =
            if (options.isFile)
                options.readLines().filterNot { it.substringBefore(':') in overrides }
            else emptyList()
        options.writeText(
            (rows + overrides.map { (key, value) -> "$key:$value" }).joinToString(
                "\n",
                postfix = "\n",
            ),
        )
    }
}

tasks.register("requestSceneProfile") {
    group = "verification"
    val label = providers.gradleProperty("sceneLabel")
    val seconds = providers.gradleProperty("profileSeconds").orElse("60")
    doLast {
        val scene = label.get()
        val duration = seconds.get().toInt()
        require(scene.matches(Regex("[a-zA-Z0-9_-]{1,64}"))) { "Invalid sceneLabel" }
        require(duration in 1..120) { "profileSeconds must be within 1..120" }
        val result = file("../../validations/performance-scene_diagnosis/result")
        result.mkdirs()
        val id = UUID.randomUUID().toString()
        val destination = result.resolve("profile-request.properties").toPath()
        val pending = result.resolve("profile-request-$id.pending").toPath()
        Files.writeString(
            pending,
            "id=$id\nseconds=$duration\nlabel=$scene\nrequestedAt=${System.currentTimeMillis()}\n",
        )
        Files.move(
            pending,
            destination,
            StandardCopyOption.ATOMIC_MOVE,
            StandardCopyOption.REPLACE_EXISTING,
        )
        logger.lifecycle("Scene profile request: id=$id scene=$scene seconds=$duration")
    }
}

// Validation preparation is owned by this tool; it never touches an external game instance.
val selectedValidation = providers.environmentVariable("RADIANCE_VALIDATION_NAME").orNull

require(
    selectedValidation == null ||
        (selectedValidation.matches(Regex("[a-z]+-[a-z0-9_]+")) &&
            file("../../validations/$selectedValidation/validation.toml").isFile),
) {
    "RADIANCE_VALIDATION_NAME must identify a registered validation"
}

val basicValidationNames =
    listOf("smoke-render_diagnostics", "conformance-benchmark").filter { name ->
        selectedValidation == null || selectedValidation == name
    }
val prepareGameValidation =
    tasks.register("prepareGameValidation") {
        dependsOn(benchmarkAgentJar)
        doLast {
            for (name in basicValidationNames) {
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

tasks.register<Exec>("prepareLineValidation") {
    dependsOn(benchmarkAgentJar)
    group = "verification"
    commandLine("python", "-X", "utf8", "benchmark/prepare_line_validation.py")
    providers.gradleProperty("lineValidationPreset").orNull?.let { args("--preset", it) }
}

tasks.register<Exec>("prepareTakeoverValidation") {
    dependsOn(benchmarkAgentJar)
    group = "verification"
    commandLine("python", "-X", "utf8", "benchmark/prepare_takeover_validation.py")
}

tasks.register<Exec>("analyzeTakeoverValidation") {
    group = "verification"
    commandLine("python", "-X", "utf8", "benchmark/analyze_takeover_validation.py")
}

tasks.register<Exec>("prepareBoundaryValidation") {
    dependsOn(benchmarkAgentJar)
    group = "verification"
    commandLine("python", "-X", "utf8", "benchmark/prepare_boundary_validation.py")
}

tasks.register<Exec>("prepareTakeoverStages") {
    dependsOn(benchmarkAgentJar)
    group = "verification"
    commandLine("python", "-X", "utf8", "benchmark/prepare_takeover_stages.py")
}

tasks.register<Exec>("analyzeTakeoverStages") {
    group = "verification"
    commandLine("python", "-X", "utf8", "benchmark/takeover_experiment.py", "analyze")
}

tasks.register<Exec>("runTakeoverComparison") {
    dependsOn(benchmarkAgentJar)
    group = "verification"
    commandLine("python", "-X", "utf8", "benchmark/takeover_experiment.py", "compare")
}

tasks.register<Exec>("analyzeTakeoverComparison") {
    group = "verification"
    commandLine("python", "-X", "utf8", "benchmark/takeover_experiment.py", "report")
}

tasks.register<Exec>("prepareTakeoverCity") {
    group = "verification"
    commandLine("python", "-X", "utf8", "benchmark/takeover_city.py", "prepare")
}

tasks.register<Exec>("upgradeCityFixture") {
    dependsOn(benchmarkAgentJar)
    group = "verification"
    commandLine("python", "-X", "utf8", "benchmark/upgrade_city_fixture.py")
}

tasks.register<Exec>("prepareTakeoverAcceptance") {
    group = "verification"
    commandLine("python", "-X", "utf8", "benchmark/takeover_acceptance.py", "prepare")
}

tasks.register<Exec>("runTakeoverAcceptance") {
    group = "verification"
    commandLine("python", "-X", "utf8", "benchmark/takeover_acceptance.py", "run")
}

tasks.register<Exec>("runEntityTakeover") {
    dependsOn(benchmarkAgentJar)
    group = "verification"
    commandLine("python", "-X", "utf8", "benchmark/entity_takeover.py")
    args(providers.gradleProperty("entityTakeoverCase").orElse("functional").get())
}

tasks.register<Exec>("runVanillaTakeover") {
    workingDir(projectDir)
    commandLine(
        "python",
        "-X",
        "utf8",
        "benchmark/vanilla_takeover.py",
        providers.gradleProperty("case").getOrElse("baseline"),
    )
}

tasks.register<Exec>("runVanillaPerformance") {
    workingDir(projectDir)
    commandLine(
        "python",
        "-X",
        "utf8",
        "benchmark/vanilla_performance.py",
        providers.gradleProperty("case").getOrElse("r32-1080"),
    )
}

tasks.register<Exec>("runSourceEfficiency") {
    workingDir(projectDir)
    commandLine("python", "-B", "-X", "utf8", "benchmark/source_efficiency.py")
    args(
        "--core",
        providers.gradleProperty("sourceCore").get(),
        "--probe",
        providers.gradleProperty("sourceProbe").get(),
        "--label",
        providers.gradleProperty("sourceLabel").get(),
    )
    providers.gradleProperty("sourceExtra").orNull?.let { args("--extra", it) }
    providers.gradleProperty("sourceSeries").orNull?.let { args("--series", it) }
    if (providers.gradleProperty("sourceVoxy").getOrElse("false").toBoolean()) args("--voxy")
    if (providers.gradleProperty("sourceMoving").getOrElse("false").toBoolean()) args("--moving")
    args("--workspace-limit", providers.gradleProperty("sourceWorkspaceLimit").getOrElse("16"))
}

tasks.register<Exec>("runTakeoverCity") {
    dependsOn(benchmarkAgentJar)
    group = "verification"
    commandLine("python", "-X", "utf8", "benchmark/takeover_city.py")
    args(providers.gradleProperty("cityTakeoverSeries").orElse("initial").get())
}

tasks.register<Exec>("snapshotTakeover") {
    group = "verification"
    commandLine("python", "-X", "utf8", "benchmark/takeover_snapshot.py")
    args(providers.gradleProperty("takeoverSnapshot").orElse("source-candidate").get())
}

tasks.register<Exec>("analyzeBoundaryValidation") {
    group = "verification"
    commandLine("python", "-X", "utf8", "benchmark/analyze_boundary_validation.py")
}

tasks.register<Exec>("prepareVoxyValidation") {
    dependsOn(benchmarkAgentJar)
    group = "verification"
    commandLine("python", "-X", "utf8", "benchmark/prepare_voxy_validation.py")
    providers.gradleProperty("voxyValidationDistance").orNull?.let { args("--distance", it) }
}

tasks.register<Exec>("analyzeVoxyValidation") {
    group = "verification"
    commandLine("python", "-X", "utf8", "benchmark/takeover_experiment.py", "voxy")
}

val lineFixtureTest =
    tasks.register<Exec>("lineFixtureTest") {
        commandLine(
            "python",
            "-X",
            "utf8",
            "-m",
            "unittest",
            "discover",
            "-s",
            "benchmark",
            "-p",
            "test_line_validation.py",
            "-v",
        )
    }

tasks.named("check") { dependsOn(lineFixtureTest) }

val queryContinuationFixtureTest =
    tasks.register<Exec>("queryContinuationFixtureTest") {
        commandLine(
            "python",
            "-m",
            "unittest",
            "discover",
            "-s",
            "benchmark",
            "-p",
            "test_query_continuation_fixture.py",
            "-v",
        )
    }

tasks.named("check") { dependsOn(queryContinuationFixtureTest) }

val profileQualityTest =
    tasks.register<Exec>("profileQualityTest") {
        commandLine(
            "python",
            "-m",
            "unittest",
            "discover",
            "-s",
            "benchmark",
            "-p",
            "test_profile_quality.py",
            "-v",
        )
    }

tasks.named("check") { dependsOn(profileQualityTest) }

val jfrCpuTest =
    tasks.register<Exec>("jfrCpuTest") {
        commandLine(
            "python",
            "-m",
            "unittest",
            "discover",
            "-s",
            "benchmark",
            "-p",
            "test_jfr_cpu.py",
            "-v",
        )
    }

tasks.named("check") { dependsOn(jfrCpuTest) }
