package io.github.recrivenvi.configuration;

import java.util.List;
import org.gradle.api.DefaultTask;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.options.Option;
import org.gradle.work.DisableCachingByDefault;

@DisableCachingByDefault(because = "只输出配置，不生成文件")
public abstract class PrintLaunchSettingsTask extends DefaultTask {
    @Internal
    public abstract Property<RepositoryConfiguration> getConfiguration();

    @Input
    @Option(option = "target", description = "已登记的 Target 名称")
    public abstract Property<String> getTarget();

    @Input
    @Option(option = "variant", description = "实例名称，例如 client 或 server；与 --validation 一起使用时为验证的角色")
    public abstract Property<String> getVariant();

    @Input
    @Optional
    @Option(option = "validation", description = "验证目录名，列出这项验证的实例")
    public abstract Property<String> getValidation();

    @TaskAction
    public void print() {
        RepositoryConfiguration configuration = getConfiguration().get();
        String target = getTarget().get();
        Instance variant = configuration.instance(getVariant().get());
        List<String> lines;
        LaunchSettings settings;
        if (getValidation().isPresent()) {
            Validation validation = configuration.validation(getValidation().get());
            lines = configuration.describeLaunchSettings(validation, target, variant);
            settings = configuration.launchSettings(validation, target, variant);
        } else {
            lines = configuration.describeLaunchSettings(target, variant);
            settings = configuration.launchSettings(target, variant);
        }
        for (String line : lines) getLogger().lifecycle(line);
        getLogger()
                .lifecycle(
                        Json.write(settings.toJson(configuration.getRootDirectory()))
                                .stripTrailing());
    }
}
