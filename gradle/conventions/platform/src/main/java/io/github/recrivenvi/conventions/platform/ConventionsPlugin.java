package io.github.recrivenvi.conventions.platform;

import io.github.recrivenvi.conventions.compliance.CompliancePlugin;
import io.github.recrivenvi.conventions.configuration.ConfigurationPlugin;
import io.github.recrivenvi.conventions.configuration.Instance;
import io.github.recrivenvi.conventions.configuration.RepositoryConfiguration;
import org.gradle.api.Plugin;
import org.gradle.api.initialization.Settings;

// 仓库根目录只应用这一个设置插件：依次启用配置、Target 约定与合规检查。
public abstract class ConventionsPlugin implements Plugin<Settings> {
    @Override
    public void apply(Settings settings) {
        settings.getPluginManager().apply(ConfigurationPlugin.class);
        settings.getPluginManager().apply("org.gradle.toolchains.foojay-resolver-convention");
        ComponentRegistry components =
                settings.getExtensions().create("components", ComponentRegistry.class, settings);
        settings.getGradle()
                .getExtensions()
                .add(ComponentRegistry.class, "componentRegistry", components);
        settings.getGradle()
                .rootProject(
                        root -> {
                            // 合规检查不依赖另外两个模块，实例与组件的名单由这里交给它。
                            RepositoryConfiguration repository =
                                    root.getGradle()
                                            .getExtensions()
                                            .getByType(RepositoryConfiguration.class);
                            var properties = root.getExtensions().getExtraProperties();
                            properties.set(
                                    CompliancePlugin.INSTANCES_PROPERTY,
                                    repository.instances().stream().map(Instance::id).toList());
                            properties.set(
                                    CompliancePlugin.PRODUCTS_PROPERTY, components.products());
                            properties.set(CompliancePlugin.TOOLS_PROPERTY, components.tools());
                            root.getPluginManager().apply(RootConventions.class);
                        });
        settings.getGradle()
                .getLifecycle()
                .beforeProject(
                        project -> {
                            if (project.getParent() != null
                                    && project.getParent().getPath().equals(":version"))
                                project.getPluginManager().apply(TargetConventions.class);
                        });
        settings.getPluginManager().apply(CompliancePlugin.class);
    }
}
