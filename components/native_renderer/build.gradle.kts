import java.io.ByteArrayOutputStream
import javax.inject.Inject
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.tasks.Exec
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.bundling.Jar
import org.gradle.process.ExecOperations
import org.gradle.work.DisableCachingByDefault

plugins { java }

@DisableCachingByDefault(because = "Runs the installed formatter without producing output files")
abstract class ClangFormatCheck : DefaultTask() {
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val sources: ConfigurableFileCollection

    @get:Inject protected abstract val execOperations: ExecOperations

    private fun findFormatter(): File {
        val windows = System.getProperty("os.name").lowercase().contains("windows")
        val name = if (windows) "clang-format.exe" else "clang-format"
        val fromPath =
            System.getenv("PATH")
                .orEmpty()
                .split(File.pathSeparator)
                .filter { it.isNotBlank() }
                .map { File(it.trim().trim('"'), name) }
                .firstOrNull { it.isFile && (windows || it.canExecute()) }
        if (fromPath != null) return fromPath
        if (windows) {
            val programFiles = System.getenv("ProgramFiles(x86)")
            val vswhere = programFiles?.let {
                File(it, "Microsoft Visual Studio/Installer/vswhere.exe")
            }
            if (vswhere != null && vswhere.isFile) {
                val output = ByteArrayOutputStream()
                execOperations.exec {
                    commandLine(
                        vswhere.absolutePath,
                        "-latest",
                        "-products",
                        "*",
                        "-find",
                        "VC\\Tools\\Llvm\\x64\\bin\\clang-format.exe",
                    )
                    standardOutput = output
                }
                val fromVisualStudio =
                    output
                        .toString(Charsets.UTF_8)
                        .lineSequence()
                        .map { File(it.trim()) }
                        .firstOrNull { it.isFile }
                if (fromVisualStudio != null) return fromVisualStudio
            }
        }
        throw GradleException(
            "找不到 clang-format：请安装 Visual Studio 的“C++ Clang 工具”组件，或把 clang-format 加入 PATH。",
        )
    }

    @TaskAction
    fun checkFormat() {
        // Resolve at execution so cached task configuration does not retain a machine's tool path.
        val formatter = findFormatter()
        val version = ByteArrayOutputStream()
        execOperations.exec {
            commandLine(formatter.absolutePath, "--version")
            standardOutput = version
        }
        logger.lifecycle(
            "clang-format: ${formatter.absolutePath} (${version.toString(Charsets.UTF_8).trim()})",
        )
        execOperations.exec {
            commandLine(
                listOf(formatter.absolutePath, "--dry-run", "--Werror") +
                    sources.files.sortedBy { it.path }.map { it.path },
            )
        }
    }
}

val windows = System.getProperty("os.name").lowercase().contains("windows")
val configuration = "RelWithDebInfo"
val cmakeDirectory = layout.buildDirectory.dir("v")
val shortDrive = providers.gradleProperty("native.cmakeDrive").getOrElse("R")

require(shortDrive.matches(Regex("[A-Z]"))) {
    "native.cmakeDrive must be one uppercase drive letter"
}

val cmakeSource =
    if (windows) "$shortDrive:/components/native_renderer" else projectDir.absolutePath
val cmakeBuild = "$cmakeSource/build/v"
val repositoryDirectory = file("../..").absolutePath
val installDirectory = layout.buildDirectory.dir("runtime")
val javaHome =
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
            ?: error("A supported Visual Studio C++ toolchain is required")
    } else null

// The SDK's Win32 shader tool cannot handle long paths. Each invocation owns and releases
// an alias of this repository; physical source and build directories stay in this component.
fun Exec.shortCmake(arguments: List<Any>) {
    if (!windows) {
        commandLine(listOf("cmake") + arguments)
        return
    }
    fun quote(value: Any) = "'" + value.toString().replace("'", "''") + "'"
    val literal = arguments.joinToString(",") { quote(it) }
    val script =
        """
        if (Test-Path ${shortDrive}:\) { throw 'Temporary CMake drive $shortDrive is already in use; choose -Pnative.cmakeDrive=<unused letter>.' }
        subst ${shortDrive}: ${quote(repositoryDirectory)}
        if (${ '$' }LASTEXITCODE -ne 0) { throw 'Cannot create temporary CMake drive' }
        try { & cmake @($literal); ${ '$' }taskExit = ${ '$' }LASTEXITCODE }
        finally { subst ${shortDrive}: /d }
        exit ${ '$' }taskExit
    """
            .trimIndent()
    commandLine("powershell", "-NoProfile", "-NonInteractive", "-Command", script)
}

val configureCmake =
    tasks.register<Exec>("configureCmake") {
        inputs.files(
            fileTree("src"),
            fileTree("cmake"),
            fileTree("tests"),
            fileTree("jni"),
            "CMakeLists.txt",
        )
        outputs.file(cmakeDirectory.map { it.file("CMakeCache.txt") })
        environment("JAVA_HOME", javaHome)
        shortCmake(
            listOf(
                "-S",
                cmakeSource,
                "-B",
                cmakeBuild,
                "-DCMAKE_INSTALL_PREFIX=${installDirectory.get().asFile}",
                "-DCMAKE_BUILD_TYPE=$configuration",
                "-DMCVR_ENABLE_NRD=ON",
                "-DUSE_AMD=ON",
                "-DFETCHCONTENT_UPDATES_DISCONNECTED=ON",
            ) + if (generator != null) listOf("-G", generator, "-A", "x64") else emptyList(),
        )
    }
val buildNative =
    tasks.register<Exec>("buildNative") {
        dependsOn(configureCmake)
        environment("JAVA_HOME", javaHome)
        shortCmake(listOf("--build", cmakeBuild, "--config", configuration, "--parallel", "4"))
    }
val installNative =
    tasks.register<Exec>("installNative") {
        dependsOn(buildNative)
        shortCmake(listOf("--install", cmakeBuild, "--config", configuration))
    }
val nativeTest =
    tasks.register<Exec>("nativeTest") {
        group = "verification"
        dependsOn(installNative)
        shortCmake(
            listOf(
                "-E",
                "env",
                "ctest",
                "--test-dir",
                cmakeBuild,
                "-C",
                configuration,
                "-LE",
                "gpu",
                "--output-on-failure",
            ),
        )
    }
val gpuTest =
    tasks.register<Exec>("gpuTest") {
        group = "verification"
        dependsOn(installNative)
        shortCmake(
            listOf(
                "-E",
                "env",
                "ctest",
                "--test-dir",
                cmakeBuild,
                "-C",
                configuration,
                "-L",
                "gpu",
                "--output-on-failure",
            ),
        )
    }
// Small groups keep command lines within the Windows process argument limit.
val formatFiles =
    fileTree(projectDir) {
            include(
                "src/**/*.cpp",
                "src/**/*.hpp",
                "src/**/*.h",
                "tests/**/*.cpp",
                "tests/**/*.hpp",
            )
            exclude("jni/**", "third_party/**", "build/**")
        }
        .files
        .sortedBy { it.path }
val formatChecks =
    formatFiles.chunked(24).mapIndexed { index, files ->
        tasks.register<ClangFormatCheck>("clangFormatCheck$index") {
            sources.from(files)
        }
    }
val clangFormatCheck = tasks.register("clangFormatCheck") { dependsOn(formatChecks) }

tasks.named("check") { dependsOn(nativeTest, clangFormatCheck) }

tasks.named<Jar>("jar") {
    dependsOn(installNative)
    from(installDirectory) {
        include(
            "core.dll",
            "libcore.so",
            "shaders/**",
            "dlss/**",
            "libxess*.dll",
            "XESS_LICENCE.txt",
        )
    }
}

// Bound the first-party formatting roots so Gradle never scans a live SDK shader scratch tree.
configure<com.diffplug.gradle.spotless.SpotlessExtension> {
    format("misc") {
        val extensions = io.github.recrivenvi.component.ComponentConventions.TEXT_EXTENSIONS
        val sourceFiles =
            files("LICENSE.md", "CMakeLists.txt") +
                files(
                    listOf("src", "tests", "cmake", "jni").map { directory ->
                        fileTree(directory) { extensions.forEach { include("**/*.$it") } }
                    },
                )
        target(sourceFiles)
    }
}
