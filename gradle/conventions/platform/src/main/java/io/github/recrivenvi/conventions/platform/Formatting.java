package io.github.recrivenvi.conventions.platform;

import com.diffplug.gradle.spotless.SpotlessExtension;
import com.diffplug.spotless.LineEnding;
import io.github.recrivenvi.conventions.component.ComponentConventions;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Callable;
import org.gradle.api.GradleException;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.repositories.IvyArtifactRepository;
import org.gradle.api.file.FileTree;
import org.gradle.api.tasks.Exec;
import org.gradle.api.tasks.Sync;
import org.gradle.api.tasks.TaskProvider;

final class Formatting {
    private static final String RUMDL_RELEASES =
            "https://github.com/rvben/rumdl/releases/download/";
    // 根项目只格式化仓库根、文档、许可、.github 与验证目录中的文本；其余目录在遍历时直接跳过，
    // 被忽略的大型实例目录因此不会被逐个列出。Target、组件与模板插件由各自的构建格式化。
    private static final List<String> ROOT_SKIPPED =
            List.of(
                    ".git/**",
                    ".gradle/**",
                    "build/**",
                    "instances/**",
                    "versions/**",
                    "components/**",
                    "gradle/conventions/**",
                    "local.toml",
                    "validations/*/instance/**",
                    "validations/*/result/**");

    private Formatting() {}

    static void target(Project project) {
        SpotlessExtension spotless = project.getExtensions().getByType(SpotlessExtension.class);
        // Target 与根项目的文本只用 LF（C-02），批处理文件不在它们的格式范围内；
        // 固定换行符也免去按 .gitattributes 判定时对整个仓库的遍历。
        spotless.setLineEndings(LineEnding.UNIX);
        spotless.java(
                java -> {
                    java.target("src/*/java/**/*.java", "src/*/templates/**/*.java");
                    java.googleJavaFormat(
                                    ComponentConventions.version(project, "google-java-format"))
                            .aosp();
                });
        spotless.kotlinGradle(
                kotlin -> {
                    kotlin.target("*.gradle.kts");
                    kotlin.ktfmt(ComponentConventions.version(project, "ktfmt")).kotlinlangStyle();
                });
        spotless.json(
                json -> {
                    json.target("src/**/*.json", "src/**/*.mcmeta");
                    json.gson().indentWithSpaces(4);
                    json.endWithNewline();
                });
        spotless.format(
                "misc",
                misc -> {
                    List<String> files = new ArrayList<>(List.of("*.properties"));
                    for (String extension : ComponentConventions.TEXT_EXTENSIONS)
                        if (!extension.equals("json")) files.add("src/**/*." + extension);
                    misc.target(
                            project.fileTree(
                                    project.getProjectDir(),
                                    tree -> tree.include(files).exclude("build/**", ".gradle/**")));
                    ComponentConventions.whitespace(misc);
                });
    }

    static void root(Project project) {
        SpotlessExtension spotless = project.getExtensions().getByType(SpotlessExtension.class);
        spotless.setLineEndings(LineEnding.UNIX);
        spotless.kotlinGradle(
                kotlin -> {
                    kotlin.target(rootFiles(project, List.of("*.gradle.kts")));
                    kotlin.ktfmt(ComponentConventions.version(project, "ktfmt")).kotlinlangStyle();
                });
        spotless.format(
                "misc",
                misc -> {
                    List<String> files =
                            new ArrayList<>(
                                    List.of(
                                            "*.md",
                                            "*.properties",
                                            "*.toml",
                                            "LICENSE",
                                            "LICENSE.txt",
                                            "NOTICE",
                                            "COPYING",
                                            "COPYING.LESSER",
                                            ".gitmodules",
                                            "licenses/**",
                                            ".gitattributes",
                                            ".editorconfig",
                                            ".gitignore",
                                            "gradle/*.toml",
                                            ".github/**/*.yml",
                                            ".github/**/*.yaml",
                                            ".github/**/*.md",
                                            "documents/**/*.md"));
                    for (String extension : ComponentConventions.TEXT_EXTENSIONS)
                        files.add("validations/**/*." + extension);
                    misc.target(rootFiles(project, files));
                    ComponentConventions.whitespace(misc);
                });
        TaskProvider<Sync> rumdl = installRumdl(project);
        TaskProvider<Exec> fix =
                markdown(project, rumdl, "fixMarkdown", "用 rumdl 修复 Markdown 文件。", "fmt");
        TaskProvider<Exec> lint =
                markdown(project, rumdl, "lintMarkdown", "用 rumdl 检查 Markdown 文件。", "check");
        project.getTasks().named("check").configure(task -> task.dependsOn(lint));
        project.getTasks().named("spotlessApply").configure(task -> task.dependsOn(fix));
        project.getTasks()
                .matching(task -> task.getName().equals("spotlessMiscApply"))
                .configureEach(task -> task.mustRunAfter(fix));
    }

    // 一次遍历取得全部文件：交给 Spotless 的每个通配符都会从项目目录起各遍历一次，排除项在遍历之后才剔除。
    private static FileTree rootFiles(Project project, List<String> include) {
        return project.fileTree(
                project.getProjectDir(), tree -> tree.include(include).exclude(ROOT_SKIPPED));
    }

    // rumdl 是发布在 GitHub Releases 上的原生程序。Gradle 把发布包当作依赖解析，
    // 因此它缓存在 Gradle 用户目录中，不需要另外安装。
    private static TaskProvider<Sync> installRumdl(Project project) {
        String platform = rumdlPlatform();
        Configuration archive =
                project.getConfigurations()
                        .create(
                                "rumdl",
                                configuration -> {
                                    configuration.setCanBeConsumed(false);
                                    configuration.setTransitive(false);
                                });
        if (platform != null) {
            project.getRepositories()
                    .exclusiveContent(
                            content -> {
                                content.forRepository(() -> rumdlReleases(project));
                                content.filter(filter -> filter.includeModule("rvben", "rumdl"));
                            });
            // 版本目录在本插件运行之后才登记，所以在解析时再读取。
            archive.defaultDependencies(
                    dependencies ->
                            dependencies.add(
                                    project.getDependencies()
                                            .create(
                                                    "rvben:rumdl:"
                                                            + ComponentConventions.version(
                                                                    project, "rumdl")
                                                            + ":"
                                                            + platform
                                                            + "@"
                                                            + (windows() ? "zip" : "tar.gz"))));
        }
        String system = System.getProperty("os.name") + " " + System.getProperty("os.arch");
        return project.getTasks()
                .register(
                        "installRumdl",
                        Sync.class,
                        task -> {
                            task.setDescription("解压适用于本机平台的 rumdl 发布包。");
                            task.from(
                                    (Callable<FileTree>)
                                            () -> {
                                                if (platform == null)
                                                    throw new GradleException(
                                                            "rumdl 没有为以下平台发布程序：" + system);
                                                File file = archive.getSingleFile();
                                                return windows()
                                                        ? project.zipTree(file)
                                                        : project.tarTree(file);
                                            });
                            task.into(rumdlDirectory(project));
                            task.filePermissions(permissions -> permissions.unix("rwxr-xr-x"));
                        });
    }

    private static IvyArtifactRepository rumdlReleases(Project project) {
        return project.getRepositories()
                .ivy(
                        ivy -> {
                            ivy.setName("rumdl releases");
                            ivy.setUrl(RUMDL_RELEASES);
                            ivy.patternLayout(
                                    layout ->
                                            layout.artifact(
                                                    "v[revision]/[module]-v[revision]-[classifier].[ext]"));
                            ivy.metadataSources(IvyArtifactRepository.MetadataSources::artifact);
                        });
    }

    private static TaskProvider<Exec> markdown(
            Project project,
            TaskProvider<Sync> rumdl,
            String name,
            String description,
            String command) {
        return project.getTasks()
                .register(
                        name,
                        Exec.class,
                        task -> {
                            task.setGroup(name.startsWith("lint") ? "verification" : "formatting");
                            task.setDescription(description);
                            task.dependsOn(rumdl);
                            task.setWorkingDir(project.getProjectDir());
                            task.setExecutable(
                                    new File(
                                            rumdlDirectory(project),
                                            windows() ? "rumdl.exe" : "rumdl"));
                            task.args(command, "--config", RUMDL_CONFIG, ".");
                        });
    }

    // rumdl 的规则配置随模板插件分发；排除规则中的路径仍从仓库根目录算起。
    static final String RUMDL_CONFIG = "gradle/conventions/rumdl.toml";

    private static File rumdlDirectory(Project project) {
        return project.getLayout().getBuildDirectory().dir("tools/rumdl").get().getAsFile();
    }

    // Windows on Arm 运行 x86_64 版本；Linux 使用静态链接的 musl 版本。
    private static String rumdlPlatform() {
        String os = System.getProperty("os.name").toLowerCase(Locale.ROOT);
        String arch = System.getProperty("os.arch").toLowerCase(Locale.ROOT);
        String cpu;
        if (arch.equals("amd64") || arch.equals("x86_64")) cpu = "x86_64";
        else if (arch.equals("aarch64") || arch.equals("arm64")) cpu = "aarch64";
        else return null;
        if (os.startsWith("windows")) return "x86_64-pc-windows-msvc";
        if (os.startsWith("mac")) return cpu + "-apple-darwin";
        if (os.startsWith("linux")) return cpu + "-unknown-linux-musl";
        return null;
    }

    private static boolean windows() {
        return System.getProperty("os.name").toLowerCase(Locale.ROOT).startsWith("windows");
    }
}
