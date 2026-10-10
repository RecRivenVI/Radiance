package io.github.recrivenvi.configuration;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeSet;
import org.gradle.api.GradleException;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.initialization.Settings;

public abstract class ConfigurationPlugin implements Plugin<Settings> {
    public static final String INSTANCES_PROPERTY = "io.github.recrivenvi.instances";

    @Override
    public void apply(Settings settings) {
        TargetRegistry registry =
                settings.getExtensions().create("targets", TargetRegistry.class, settings);
        // 初始化脚本中的 allprojects { repositories {} }（CodeQL 就这样做）会让 Gradle 在根项目的回调之前
        // 执行 Target 的 beforeProject 动作，所以配置在设置求值之后、任何项目创建之前放到 Gradle 对象上。
        settings.getGradle()
                .settingsEvaluated(
                        evaluated ->
                                evaluated
                                        .getGradle()
                                        .getExtensions()
                                        .add(
                                                RepositoryConfiguration.class,
                                                "repositoryConfiguration",
                                                load(evaluated, registry)));
        settings.getGradle().rootProject(ConfigurationPlugin::configure);
    }

    private static RepositoryConfiguration load(Settings settings, TargetRegistry registry) {
        try {
            Map<String, Map<String, String>> targets = new LinkedHashMap<>();
            for (String name : registry.names()) {
                String path = "versions/" + name + "/target.properties";
                targets.put(name, PropertiesFile.parse(read(settings, path, true), path));
            }
            return new RepositoryConfiguration(
                    settings.getRootDir(),
                    PropertiesFile.parse(
                            read(settings, "gradle.properties", true), "gradle.properties"),
                    targets,
                    toml(settings, Inputs.FILE),
                    toml(settings, DailyInstances.SHARED_FILE),
                    toml(settings, DailyInstances.LOCAL_FILE),
                    validations(settings));
        } catch (IllegalArgumentException e) {
            throw new GradleException(e.getMessage(), e);
        }
    }

    private static void configure(Project root) {
        RepositoryConfiguration configuration =
                root.getGradle().getExtensions().getByType(RepositoryConfiguration.class);
        root.getExtensions()
                .getExtraProperties()
                .set(
                        INSTANCES_PROPERTY,
                        new TreeSet<>(
                                configuration.instances().stream().map(Instance::id).toList()));
        var verify =
                root.getTasks()
                        .register(
                                "verifyConfiguration",
                                VerifyConfigurationTask.class,
                                task -> {
                                    task.setGroup("verification");
                                    task.setDescription("校验项目事实、Target 事实与实例设置。");
                                    task.getConfiguration().set(configuration);
                                });
        root.getTasks()
                .register(
                        "printLaunchSettings",
                        PrintLaunchSettingsTask.class,
                        task -> {
                            task.setGroup("help");
                            task.setDescription("列出一个日常实例或验证实例的最终设置。");
                            task.getConfiguration().set(configuration);
                            task.getVariant().convention("client");
                        });
        var inputs =
                root.getTasks()
                        .register(
                                "verifyInputs",
                                VerifyInputsTask.class,
                                task -> {
                                    task.setGroup("verification");
                                    task.setDescription("核对每个本地输入都存在，且哈希在允许列表中。");
                                    task.getConfiguration().set(configuration);
                                    task.getNames().set(configuration.getInputNames());
                                });
        root.getPluginManager()
                .withPlugin(
                        "base",
                        plugin ->
                                root.getTasks()
                                        .named("check")
                                        .configure(task -> task.dependsOn(verify, inputs)));
    }

    private static Map<String, Map<String, Object>> validations(Settings settings) {
        File directory = new File(settings.getRootDir(), Validation.DIRECTORY);
        Map<String, Map<String, Object>> validations = new LinkedHashMap<>();
        for (String name :
                settings.getProviders()
                        .of(
                                ValidationDirectories.class,
                                spec -> spec.getParameters().getDirectory().set(directory))
                        .get())
            validations.put(
                    name,
                    toml(settings, Validation.DIRECTORY + "/" + name + "/" + Validation.FILE));
        return validations;
    }

    private static Map<String, Object> toml(Settings settings, String path) {
        String text = read(settings, path, false);
        return text == null ? Map.of() : TomlFile.parse(text, path);
    }

    private static String read(Settings settings, String path, boolean required) {
        var file = settings.getLayout().getRootDirectory().file(path);
        String text = settings.getProviders().fileContents(file).getAsText().getOrNull();
        if (text == null && required) throw new GradleException("缺少配置文件：" + path);
        return text;
    }
}
