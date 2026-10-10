import groovy.json.JsonOutput
import io.github.recrivenvi.conventions.TargetExtension
import java.security.MessageDigest
import java.util.zip.ZipFile
import org.gradle.api.tasks.JavaExec
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.Sync
import org.gradle.api.tasks.bundling.Jar
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.api.tasks.testing.Test
import org.gradle.language.jvm.tasks.ProcessResources

plugins {
    id("net.neoforged.moddev")
    `maven-publish`
}

val facts = extensions.getByType<TargetExtension>()
val sources = extensions.getByType<SourceSetContainer>()
val main = sources.named("main").get()
val probe = sources.named("probe").get()
val compatibilityDirectory = layout.buildDirectory.dir("compatibility-compile")
val bootstrapResources = layout.buildDirectory.dir("generated/bootstrapResources")
val bootstrap =
    sources.create("bootstrap") {
        resources.srcDir(bootstrapResources)
        compileClasspath += configurations.getByName("compileClasspath")
        runtimeClasspath += configurations.getByName("runtimeClasspath")
    }
val bootstrapTests =
    sources.create("bootstrapTest") {
        compileClasspath += bootstrap.output + bootstrap.compileClasspath
        runtimeClasspath += output + compileClasspath + bootstrap.runtimeClasspath
    }
val probeTests =
    sources.create("probeTest") {
        compileClasspath += probe.output + probe.compileClasspath
        runtimeClasspath += output + compileClasspath + probe.runtimeClasspath
    }
val packaged =
    sources.create("packagedLaunch") {
        java.setSrcDirs(emptyList<String>())
        resources.setSrcDirs(emptyList<String>())
        compileClasspath += configurations.getByName("compileClasspath")
        runtimeClasspath += configurations.getByName("runtimeClasspath")
    }

main.compileClasspath += bootstrap.output.classesDirs

sources.named("test") {
    compileClasspath += main.compileClasspath
    runtimeClasspath += main.runtimeClasspath
}

configurations.named("bootstrapImplementation") {
    extendsFrom(configurations.getByName("implementation"))
}

configurations.named("bootstrapTestImplementation") {
    extendsFrom(configurations.getByName("testImplementation"))
}

configurations.named("bootstrapTestRuntimeOnly") {
    extendsFrom(configurations.getByName("testRuntimeOnly"))
}

configurations.named("probeTestImplementation") {
    extendsFrom(configurations.getByName("testImplementation"))
}

configurations.named("probeTestRuntimeOnly") {
    extendsFrom(configurations.getByName("testRuntimeOnly"))
}

val compatibility =
    configurations.create("compatibilityCompileArtifacts") {
        isCanBeConsumed = false
        isCanBeResolved = true
        isTransitive = false
    }
val nativeProducts = configurations.getByName("productComponents")

repositories {
    mavenCentral()
    maven("https://maven.blamejared.com/") { content { includeGroup("foundry.veil") } }
    maven("https://api.modrinth.com/maven") { content { includeGroup("maven.modrinth") } }
}

dependencies {
    implementation(libs.snakeyaml)
    add("jarJar", libs.snakeyaml)
    compileOnly(
        facts.module(
            "foundry.veil:veil-neoforge-${facts.fact("minecraft_version")}",
            "veil_version",
        ),
    ) {
        isTransitive = false
    }
    for (name in listOf("create", "sable", "aeronautics_bundled")) {
        compileOnly(facts.input(name))
        add(compatibility.name, facts.input(name))
        testRuntimeOnly(facts.input(name))
    }
    add(
        compatibility.name,
        facts.module(
            "foundry.veil:veil-neoforge-${facts.fact("minecraft_version")}",
            "veil_version",
        ),
    )
    compileOnly(fileTree(compatibilityDirectory) { include("*.jar") })
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.launcher)
    testRuntimeOnly(
        facts.module(
            "foundry.veil:veil-neoforge-${facts.fact("minecraft_version")}",
            "veil_version",
        ),
    ) {
        isTransitive = false
    }
    testRuntimeOnly(fileTree(compatibilityDirectory) { include("*.jar") })
    add("probeImplementation", "components:render_diagnostics")
}

val prepareCompatibilityCompileDependencies =
    tasks.register<Sync>("prepareCompatibilityCompileDependencies") {
        from(provider { compatibility.map { zipTree(it) } })
        include("META-INF/jarjar/*.jar")
        eachFile { path = name }
        includeEmptyDirs = false
        duplicatesStrategy = DuplicatesStrategy.INCLUDE
        into(compatibilityDirectory)
    }

tasks.withType<JavaCompile>().configureEach { dependsOn(prepareCompatibilityCompileDependencies) }

val nativeRuntime = layout.buildDirectory.dir("native-runtime")
val prepareRuntime =
    tasks.register<Sync>("prepareRuntime") {
        dependsOn(nativeProducts)
        from(provider { nativeProducts.map { zipTree(it) } })
        exclude("META-INF/MANIFEST.MF")
        into(nativeRuntime)
    }
val generateBootstrapRuntimeIndex =
    tasks.register("generateBootstrapRuntimeIndex") {
        dependsOn(prepareRuntime)
        inputs.dir(nativeRuntime)
        val output = bootstrapResources.map { it.file("META-INF/radiance/runtime.index") }
        outputs.file(output)
        doLast {
            val root = nativeRuntime.get().asFile
            val entries =
                root
                    .walkTopDown()
                    .filter { it.isFile }
                    .sortedBy { it.relativeTo(root).invariantSeparatorsPath }
                    .map {
                        val hash =
                            MessageDigest.getInstance("SHA-256")
                                .digest(it.readBytes())
                                .joinToString("") { b -> "%02x".format(b) }
                        "$hash  ${it.relativeTo(root).invariantSeparatorsPath}"
                    }
                    .toList()
            output.get().asFile.apply {
                parentFile.mkdirs()
                writeText(entries.joinToString("\n", postfix = "\n"))
            }
        }
    }

tasks.named<ProcessResources>("processBootstrapResources") {
    dependsOn(generateBootstrapRuntimeIndex)
    from(nativeRuntime)
}

val jniOutput = layout.buildDirectory.dir("generated/jni")
val generateJniHeaders =
    tasks.register<JavaCompile>("generateJniHeaders") {
        source(main.allJava)
        classpath = main.compileClasspath
        destinationDirectory.set(layout.buildDirectory.dir("classes/jniHeaderGeneration"))
        options.headerOutputDirectory.set(jniOutput)
        options.isIncremental = false
        options.compilerArgs.addAll(listOf("-proc:none", "-Xmaxerrs", "1000"))
        doLast {
            // Committed generated headers follow repository LF rules on every host.
            jniOutput
                .get()
                .asFile
                .listFiles { f -> f.extension == "h" }
                ?.forEach { header ->
                    header.writeText(header.readText().replace("\r\n", "\n"), Charsets.UTF_8)
                }
        }
    }
val verifyJniHeaders =
    tasks.register("verifyJniHeaders") {
        dependsOn(generateJniHeaders)
        val committed = rootProject.layout.projectDirectory.dir("components/native_renderer/jni")
        inputs.dir(jniOutput)
        inputs.dir(committed)
        doLast {
            val generated =
                jniOutput
                    .get()
                    .asFile
                    .listFiles { f -> f.extension == "h" }!!
                    .associateBy { it.name }
            val saved =
                committed.asFile.listFiles { f -> f.extension == "h" }!!.associateBy { it.name }
            check(generated.keys == saved.keys) {
                "JNI header set differs: generated=${generated.keys}, committed=${saved.keys}"
            }
            for ((name, file) in generated) check(
                file.readBytes().contentEquals(saved.getValue(name).readBytes()),
            ) {
                "Regenerate and review JNI header: $name"
            }
        }
    }
val gameJar =
    tasks.named<Jar>("jar") {
        destinationDirectory.set(layout.buildDirectory.dir("intermediates/radiance-game"))
        archiveFileName.set("Radiance-${version}.jar")
        exclude(
            "core.dll",
            "core.lib",
            "libcore.so",
            "libxess*.dll",
            "XESS_LICENCE.txt",
            "shaders/**",
            "dlss/**",
        )
    }
val metadataDirectory = layout.buildDirectory.dir("generated/bootstrapMetadata")
val generateEmbeddedGameMetadata =
    tasks.register("generateEmbeddedGameMetadata") {
        dependsOn(gameJar)
        inputs.file(gameJar.flatMap { it.archiveFile })
        outputs.dir(metadataDirectory)
        doLast {
            val game = gameJar.get().archiveFile.get().asFile
            val directory = metadataDirectory.get().asFile.apply { mkdirs() }
            val hash =
                MessageDigest.getInstance("SHA-256").digest(game.readBytes()).joinToString("") {
                    "%02x".format(it)
                }
            directory.resolve("game.sha256").writeText("$hash\n", Charsets.US_ASCII)
            directory
                .resolve("game.path")
                .writeText("META-INF/radiance/${game.name}\n", Charsets.US_ASCII)
        }
    }
val launcherDirectory = layout.buildDirectory.dir("generated/launcherMetadata")
val mod =
    listOf(
            "id",
            "name",
            "version",
            "description",
            "authors",
            "license",
            "homepage",
            "issues",
            "logo",
        )
        .associateWith { facts.fact("mod_$it") }
val minecraftVersion = facts.fact("minecraft_version")
val generateLauncherMetadata =
    tasks.register("generateLauncherMetadata") {
        inputs.properties(mod)
        outputs.dir(launcherDirectory)
        doLast {
            val info =
                listOf(
                    mapOf(
                        "modid" to mod["id"],
                        "name" to mod["name"],
                        "version" to mod["version"],
                        "description" to mod["description"],
                        "authorList" to mod.getValue("authors").split(",").map { it.trim() },
                        "url" to mod["homepage"],
                        "issueTrackerURL" to mod["issues"],
                        "license" to mod["license"],
                        "logoFile" to mod["logo"],
                        "mcversion" to minecraftVersion,
                    ),
                )
            launcherDirectory.get().file("mcmod.info").asFile.apply {
                parentFile.mkdirs()
                writeText(JsonOutput.prettyPrint(JsonOutput.toJson(info)) + "\n")
            }
        }
    }
val distributedJar =
    tasks.register<Jar>("distributedJar") {
        dependsOn(generateEmbeddedGameMetadata, generateLauncherMetadata)
        from(bootstrap.output)
        from(launcherDirectory)
        from("src/main/resources") { include(mod.getValue("logo")) }
        from(gameJar.flatMap { it.archiveFile }) { into("META-INF/radiance") }
        from(metadataDirectory) { into("META-INF/radiance") }
        from(rootProject.files("LICENSE", "NOTICE")) { into("META-INF") }
        from(rootProject.file("licenses")) { into("META-INF/licenses") }
        manifest.attributes(
            mapOf(
                "Automatic-Module-Name" to "com.radiance.bootstrap",
                "Implementation-Title" to mod["name"],
                "Implementation-Version" to version,
            ),
        )
        duplicatesStrategy = DuplicatesStrategy.FAIL
        destinationDirectory.set(layout.buildDirectory.dir("libs"))
        archiveFileName.set("${mod.getValue("id")}-${facts.name}-${version}.jar")
    }

tasks.named("assemble") { dependsOn(distributedJar) }

java.withSourcesJar()

tasks.named<Jar>("sourcesJar") {
    from(bootstrap.allJava)
    from("src/bootstrap/resources")
}

val officialReference =
    tasks.register<JavaCompile>("compileOfficialGlReference") {
        source(
            rootProject.fileTree(
                "components/render_diagnostics/fixtures/official_gl_reference/java",
            ),
        )
        classpath = bootstrapTests.compileClasspath
        destinationDirectory.set(layout.buildDirectory.dir("classes/officialGlReference"))
    }

bootstrapTests.compileClasspath += files(officialReference.flatMap { it.destinationDirectory })

bootstrapTests.runtimeClasspath += files(officialReference.flatMap { it.destinationDirectory })

// The reference fixture depends only on the loader; avoid a self-dependency on its own output.
officialReference.configure { classpath = bootstrap.output + bootstrap.compileClasspath }

val bootstrapTest =
    tasks.register<Test>("bootstrapTest") {
        dependsOn(distributedJar)
        testClassesDirs = bootstrapTests.output.classesDirs
        classpath = bootstrapTests.runtimeClasspath
        useJUnitPlatform()
        jvmArgs(
            "--add-opens=java.base/java.lang.invoke=ALL-UNNAMED",
            "--add-opens=java.base/java.util.jar=ALL-UNNAMED",
        )
        systemProperty(
            "radiance.test.distribution",
            distributedJar.get().archiveFile.get().asFile.absolutePath,
        )
        systemProperty("radiance.test.neoforge", facts.fact("loader_version"))
        systemProperty("radiance.test.neoforgeRange", "[${facts.fact("loader_minimum")},)")
        doFirst {
            systemProperty(
                "radiance.test.compatibilityArtifacts",
                compatibility.files.joinToString(File.pathSeparator) { it.absolutePath },
            )
        }
    }
val probeTest =
    tasks.register<Test>("probeTest") {
        testClassesDirs = probeTests.output.classesDirs
        classpath = probeTests.runtimeClasspath
        useJUnitPlatform()
        val directory = layout.buildDirectory.dir("test-runtime/probeTest").get().asFile
        workingDir(directory)
        doFirst { directory.mkdirs() }
    }

tasks.withType<Test>().configureEach { useJUnitPlatform() }

val verifyDistributedJar =
    tasks.register("verifyDistributedJar") {
        dependsOn(distributedJar)
        inputs.file(distributedJar.flatMap { it.archiveFile })
        doLast {
            ZipFile(distributedJar.get().archiveFile.get().asFile).use { outer ->
                check(outer.getEntry("META-INF/neoforge.mods.toml") == null) {
                    "SERVICE JAR must not declare a GAME mod"
                }
                for (name in
                    listOf(
                        "mcmod.info",
                        "META-INF/radiance/runtime.index",
                        "META-INF/radiance/game.path",
                        "META-INF/radiance/game.sha256",
                    )) check(outer.getEntry(name) != null) { "Missing $name" }
                val scope =
                    outer
                        .getInputStream(
                            outer.getEntry("META-INF/radiance/distribution-scope.properties"),
                        )
                        .bufferedReader()
                        .readText()
                check(
                    scope.contains("scope=local-validation") &&
                        scope.contains("publicBinaryDistributionApproved=false"),
                )
                val path =
                    outer
                        .getInputStream(outer.getEntry("META-INF/radiance/game.path"))
                        .bufferedReader()
                        .readText()
                        .trim()
                val bytes = outer.getInputStream(outer.getEntry(path)).readAllBytes()
                val hash =
                    MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") {
                        "%02x".format(it)
                    }
                check(
                    hash ==
                        outer
                            .getInputStream(outer.getEntry("META-INF/radiance/game.sha256"))
                            .bufferedReader()
                            .readText()
                            .trim(),
                )
            }
        }
    }

tasks.register("verifyPublicDistributionGate") {
    doLast {
        error(
            "Public native-runtime distribution approval remains unresolved. Local validation is allowed.",
        )
    }
}

tasks.register("publicDistributionJar") { dependsOn("verifyPublicDistributionGate") }

val verifyMavenDevelopmentArtifact =
    tasks.register("verifyMavenDevelopmentArtifact") {
        dependsOn(gameJar)
        doLast {
            ZipFile(gameJar.get().archiveFile.get().asFile).use { game ->
                check(game.getEntry("META-INF/neoforge.mods.toml") != null)
                check(
                    game.entries().asSequence().none {
                        it.name.startsWith("com/radiance/bootstrap/") ||
                            it.name == "core.dll" ||
                            it.name.startsWith("dlss/") ||
                            it.name.startsWith("shaders/")
                    },
                )
            }
        }
    }

tasks.named("check") {
    dependsOn(
        verifyJniHeaders,
        bootstrapTest,
        probeTest,
        verifyDistributedJar,
        verifyMavenDevelopmentArtifact,
    )
}

publishing {
    publications.create<MavenPublication>("mavenJava") {
        artifactId = "Radiance-game"
        from(components["java"])
        pom {
            name.set("Radiance GAME development artifact")
            description.set(
                "Internal GAME-layer artifact for compile-time integration; not an installable mod distribution.",
            )
        }
    }
}

neoForge {
    accessTransformers.from(
        file("src/main/resources/META-INF/accesstransformer.cfg"),
        file("src/probe/resources/META-INF/accesstransformer.cfg"),
    )
    mods.create("radianceBootstrap") { sourceSet(bootstrap) }
    runs.configureEach {
        if (name !in listOf("packagedClient", "packagedServer", "manualBase", "manualFull"))
            additionalRuntimeClasspathConfiguration.dependencies.add(libs.snakeyaml.get())
    }
    for (name in listOf("packagedClient", "packagedServer", "manualBase", "manualFull")) {
        runs.create(name) {
            if (name == "packagedServer") server() else client()
            loadedMods.set(emptyList())
            sourceSet.set(packaged)
        }
    }
}

for (name in listOf("packagedClient", "packagedServer", "manualBase", "manualFull")) {
    val runDirectory = layout.buildDirectory.dir("run/$name")
    val prepare =
        tasks.register<Sync>("prepare${name.replaceFirstChar { it.uppercase() }}") {
            dependsOn(distributedJar)
            from(distributedJar.flatMap { it.archiveFile })
            if (name == "manualFull")
                from(
                    listOf(
                        facts.input("create"),
                        facts.input("sable"),
                        facts.input("aeronautics_bundled"),
                    ),
                )
            into(runDirectory.map { it.dir("mods") })
            doLast {
                val directory = runDirectory.get().asFile
                val options = directory.resolve("options.txt")
                val rows =
                    if (options.isFile)
                        options.readLines().filterNot {
                            it.startsWith("soundCategory_master:") ||
                                it.startsWith("onboardAccessibility:")
                        }
                    else emptyList()
                val mutedMaster = 0.0
                options.writeText(
                    (rows +
                            listOf(
                                "soundCategory_master:$mutedMaster",
                                "onboardAccessibility:false",
                            ))
                        .joinToString("\n", postfix = "\n"),
                )
            }
        }
    tasks.withType<JavaExec>().configureEach {
        if (this.name == "run${name.replaceFirstChar { it.uppercase() }}") {
            dependsOn(prepare, verifyDistributedJar)
            doFirst { environment.remove("MOD_CLASSES") }
        }
    }
}

// Preserve the muted automation setting through Gradle preparation rather than editing an instance
// by hand.
tasks
    .matching { it.name.startsWith("prepare") && it.name.endsWith("Instance") }
    .configureEach {
        doLast {
            val directory =
                (this as io.github.recrivenvi.conventions.PrepareInstanceTask)
                    .directory
                    .get()
                    .asFile
            if (directory.resolve("options.txt").isFile) {
                val options = directory.resolve("options.txt")
                val mutedMaster = 0.0
                val rows = options.readLines().filterNot { it.startsWith("soundCategory_master:") }
                options.writeText(
                    (rows + "soundCategory_master:$mutedMaster").joinToString("\n", postfix = "\n"),
                )
            }
        }
    }

tasks.withType<JavaExec>().configureEach {
    if (name.startsWith("runValidation")) {
        dependsOn(gradle.includedBuild("render_diagnostics").task(":prepareGameValidation"))
        environment("RADIANCE_AUDIT_SMOKE", "1")
        if (name.contains("ConformanceBenchmark")) {
            jvmArgs(
                "-javaagent:${rootProject.file("components/render_diagnostics/build/benchmark-support/radiance-benchmark-agent.jar")}",
            )
        }
    }
}
