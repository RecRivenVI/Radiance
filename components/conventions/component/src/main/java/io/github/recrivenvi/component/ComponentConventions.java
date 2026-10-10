package io.github.recrivenvi.component;

import com.diffplug.gradle.spotless.FormatExtension;
import com.diffplug.gradle.spotless.SpotlessExtension;
import com.diffplug.spotless.LineEnding;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import org.gradle.api.Project;
import org.gradle.api.artifacts.VersionCatalogsExtension;
import org.gradle.api.plugins.BasePlugin;
import org.gradle.api.tasks.compile.JavaCompile;
import org.gradle.api.tasks.testing.Test;

public final class ComponentConventions {
    // spotlessApply 按这些扩展名识别文本文件；其他扩展名的文本文件只由 C-01 至 C-04 报告。
    public static final List<String> TEXT_EXTENSIONS =
            List.of(
                    ("md txt toml properties json yml yaml xml cfg ini csv html css gradle ps1 psm1"
                                    + " psd1 py sh js mjs cjs ts c h cc cpp cxx hpp hh inl cmake rs"
                                    + " glsl hlsl vert frag comp geom tesc tese rgen rchit rahit rmiss"
                                    + " rint rcall fsh vsh csh lang mcfunction snbt accesswidener"
                                    + " classtweaker")
                            .split(" "));
    private static final List<String> TEXT =
            TEXT_EXTENSIONS.stream().map(extension -> "**/*." + extension).toList();
    private static final List<String> WINDOWS_TEXT =
            Arrays.stream("bat cmd".split(" ")).map(extension -> "**/*." + extension).toList();
    // node_modules/ 与 dist/ 是 JavaScript 工具的依赖与输出目录，由工具生成并被 Git 忽略。
    private static final List<String> EXCLUDED =
            List.of("build/**", ".gradle/**", "third_party/**", "**/node_modules/**", "**/dist/**");

    private ComponentConventions() {}

    static void apply(Project project, int release) {
        project.getPluginManager().apply(BasePlugin.class);
        project.getPluginManager().apply("com.diffplug.spotless");
        SpotlessExtension spotless = project.getExtensions().getByType(SpotlessExtension.class);
        // 读取 .gitattributes 会把组件目录（包括 Gradle 锁定的文件）计入配置缓存的指纹，
        // 导致缓存失效，所以换行符按格式分别设置（C-02）。
        spotless.setLineEndings(LineEnding.UNIX);
        spotless.kotlinGradle(
                kotlin -> {
                    kotlin.target("*.gradle.kts");
                    kotlin.ktfmt(version(project, "ktfmt")).kotlinlangStyle();
                });
        spotless.format(
                "misc",
                misc -> {
                    misc.target(TEXT);
                    misc.targetExclude(EXCLUDED);
                    whitespace(misc);
                });
        spotless.format(
                "windows",
                windows -> {
                    windows.target(WINDOWS_TEXT);
                    windows.targetExclude(EXCLUDED);
                    windows.setLineEndings(LineEnding.WINDOWS);
                    whitespace(windows);
                });
        project.getPluginManager()
                .withPlugin(
                        "java",
                        plugin -> {
                            spotless.java(
                                    java -> {
                                        java.target("src/*/java/**/*.java");
                                        java.googleJavaFormat(
                                                        version(project, "google-java-format"))
                                                .aosp();
                                    });
                            project.getTasks()
                                    .withType(JavaCompile.class)
                                    .configureEach(
                                            task -> {
                                                task.getOptions().setEncoding("UTF-8");
                                                task.getOptions().getRelease().convention(release);
                                            });
                            project.getTasks()
                                    .withType(Test.class)
                                    .configureEach(Test::useJUnitPlatform);
                        });
    }

    public static void whitespace(FormatExtension format) {
        format.trimTrailingWhitespace();
        format.leadingTabsToSpaces(4);
        format.endWithNewline();
    }

    public static String version(Project project, String alias) {
        return project.getExtensions()
                .getByType(VersionCatalogsExtension.class)
                .named("libs")
                .findVersion(alias)
                .orElseThrow()
                .getRequiredVersion();
    }

    static int lowestJava(File versions) {
        int lowest = Runtime.version().feature();
        File[] targets = versions.listFiles(File::isDirectory);
        if (targets == null) return lowest;
        for (File target : targets) {
            File facts = new File(target, "target.properties");
            if (!facts.isFile()) continue;
            Properties properties = new Properties();
            try (FileReader reader = new FileReader(facts)) {
                properties.load(reader);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            String java = properties.getProperty("java_version");
            if (java != null && java.strip().matches("[0-9]+"))
                lowest = Math.min(lowest, Integer.parseInt(java.strip()));
        }
        return lowest;
    }
}
