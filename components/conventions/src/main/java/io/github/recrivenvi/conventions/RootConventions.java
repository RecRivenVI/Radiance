package io.github.recrivenvi.conventions;

import io.github.recrivenvi.configuration.RepositoryConfiguration;
import java.util.ArrayList;
import java.util.List;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.initialization.IncludedBuild;
import org.gradle.api.plugins.BasePlugin;
import org.gradle.api.tasks.Sync;

public abstract class RootConventions implements Plugin<Project> {
    @Override
    public void apply(Project root) {
        root.getPluginManager().apply(BasePlugin.class);
        root.getPluginManager().apply("com.diffplug.spotless");
        root.getRepositories().mavenCentral();
        RepositoryConfiguration repository =
                root.getGradle().getExtensions().getByType(RepositoryConfiguration.class);
        List<String> targets = List.copyOf(repository.getTargetNames());
        var repositoryCheck =
                root.getTasks()
                        .register(
                                "checkRepository",
                                task -> {
                                    task.setGroup("verification");
                                    task.setDescription("运行不编译 Target、不读取本地输入的全部检查。");
                                    task.dependsOn(
                                            "verifyCompliance",
                                            "verifyConfiguration",
                                            "lintMarkdown",
                                            "spotlessCheck");
                                    for (String target : targets)
                                        task.dependsOn(":version:" + target + ":spotlessCheck");
                                    for (IncludedBuild build : root.getGradle().getIncludedBuilds())
                                        task.dependsOn(build.task(":check"));
                                });
        root.getTasks()
                .named("check")
                .configure(
                        task -> {
                            task.dependsOn(repositoryCheck);
                            for (String target : targets)
                                task.dependsOn(":version:" + target + ":check");
                        });
        root.getTasks()
                .named("spotlessApply")
                .configure(
                        task -> {
                            for (IncludedBuild build : root.getGradle().getIncludedBuilds())
                                task.dependsOn(build.task(":spotlessApply"));
                        });
        root.getTasks()
                .register(
                        "collectRelease",
                        Sync.class,
                        task -> {
                            task.setGroup("build");
                            task.setDescription("把每个 Target 的发行 JAR 收集到 build/release/<version>/。");
                            String version = repository.getProjectFacts().get("mod_version");
                            for (String target : targets) {
                                task.dependsOn(":version:" + target + ":assemble");
                                task.from(
                                        root.file("versions/" + target + "/build/libs"),
                                        spec -> spec.include("*-" + version + ".jar"));
                            }
                            task.into(
                                    root.getLayout().getBuildDirectory().dir("release/" + version));
                        });
        root.getTasks()
                .register(
                        "verifyRelease",
                        VerifyReleaseTask.class,
                        task -> {
                            task.setGroup("verification");
                            task.setDescription("收集全部 Target 的发行 JAR，并逐个核对内容。");
                            String version = repository.getProjectFacts().get("mod_version");
                            task.dependsOn("collectRelease");
                            List<String> expected = new ArrayList<>();
                            for (String target : targets) {
                                task.dependsOn(":version:" + target + ":verifyRelease");
                                expected.add(
                                        repository.getModId()
                                                + "-"
                                                + target
                                                + "-"
                                                + version
                                                + ".jar");
                            }
                            task.getExpected().set(expected);
                            task.getDirectory()
                                    .set(
                                            root.getLayout()
                                                    .getBuildDirectory()
                                                    .dir("release/" + version));
                        });
        root.getTasks()
                .named("assemble")
                .configure(
                        task -> {
                            for (String target : targets)
                                task.dependsOn(":version:" + target + ":assemble");
                        });
        Formatting.root(root);
    }
}
