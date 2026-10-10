package io.github.recrivenvi.compliance;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.initialization.IncludedBuild;
import org.gradle.api.initialization.ProjectDescriptor;
import org.gradle.api.initialization.Settings;
import org.gradle.api.tasks.TaskProvider;

public abstract class CompliancePlugin implements Plugin<Settings> {
    private static final String INSTANCES = "io.github.recrivenvi.instances";
    private static final String PRODUCTS = "io.github.recrivenvi.products";
    private static final String TOOLS = "io.github.recrivenvi.tools";

    @Override
    public void apply(Settings settings) {
        ComplianceExtension extension =
                settings.getExtensions().create("compliance", ComplianceExtension.class);
        extension.getStrict().convention(false);
        settings.getGradle().rootProject(root -> configure(root, settings, extension));
    }

    private static void configure(Project root, Settings settings, ComplianceExtension extension) {
        Set<String> targets = registeredTargets(settings);
        Map<String, List<String>> vocabulary = extension.vocabulary();
        boolean strict = extension.getStrict().get();
        TaskProvider<VerifyComplianceTask> verify =
                root.getTasks().register("verifyCompliance", VerifyComplianceTask.class);
        verify.configure(
                task -> {
                    task.setGroup("verification");
                    task.setDescription("按 AGENTS.md 中的规则检查仓库。");
                    task.getRootDirectory().set(root.getLayout().getProjectDirectory());
                    task.getTargets().set(targets);
                    task.getComponents().set(includedComponents(root));
                    task.getProducts().set(names(root, PRODUCTS));
                    task.getTools().set(names(root, TOOLS));
                    task.getInstances().set(instances(root));
                    task.getSourceSets().set(extension.sourceSets());
                    task.getVocabulary().set(vocabulary);
                    task.getStrict().convention(strict);
                    task.getReportDirectory()
                            .set(root.getLayout().getBuildDirectory().dir("reports/compliance"));
                });
        root.getTasks()
                .register(
                        "printVocabulary",
                        PrintVocabularyTask.class,
                        task -> {
                            task.setGroup("help");
                            task.setDescription("列出文档与验证目录的建议类型词。");
                            task.getVocabulary().set(vocabulary);
                        });
        root.getPluginManager()
                .withPlugin(
                        "base",
                        plugin ->
                                root.getTasks()
                                        .named("check")
                                        .configure(task -> task.dependsOn(verify)));
    }

    // configuration 插件发布实例名称，包括登记的额外实例；内置实例总是有效。
    private static Set<String> instances(Project root) {
        Set<String> instances = new TreeSet<>(Repository.BUILT_IN_INSTANCES);
        instances.addAll(names(root, INSTANCES));
        return instances;
    }

    // configuration 插件把实例名称、conventions 插件把登记的组件名称发布为根项目的额外属性。
    private static Set<String> names(Project root, String property) {
        Object names = root.getExtensions().getExtraProperties().getProperties().get(property);
        Set<String> found = new TreeSet<>();
        if (names instanceof Iterable<?> values)
            for (Object name : values) found.add(name.toString());
        return found;
    }

    private static Set<String> includedComponents(Project root) {
        Path components = root.getRootDir().toPath().resolve("components").normalize();
        Set<String> names = new TreeSet<>();
        for (IncludedBuild build : root.getGradle().getIncludedBuilds()) {
            Path directory = build.getProjectDir().toPath().normalize();
            if (components.equals(directory.getParent()))
                names.add(directory.getFileName().toString());
        }
        return names;
    }

    private static Set<String> registeredTargets(Settings settings) {
        Set<String> targets = new TreeSet<>();
        ProjectDescriptor versions = settings.findProject(":version");
        if (versions != null)
            for (ProjectDescriptor child : versions.getChildren()) targets.add(child.getName());
        return targets;
    }
}
