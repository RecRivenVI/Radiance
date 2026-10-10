package io.github.recrivenvi.conventions;

import io.github.recrivenvi.configuration.ConfigurationPlugin;
import org.gradle.api.Plugin;
import org.gradle.api.initialization.Settings;

public abstract class ConventionsPlugin implements Plugin<Settings> {
    // compliance 插件读取这两个属性，核对组件已登记（L-08）与每项验证写明的工具（V-08）。
    public static final String PRODUCTS_PROPERTY = "io.github.recrivenvi.products";
    public static final String TOOLS_PROPERTY = "io.github.recrivenvi.tools";

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
                            root.getExtensions()
                                    .getExtraProperties()
                                    .set(PRODUCTS_PROPERTY, components.products());
                            root.getExtensions()
                                    .getExtraProperties()
                                    .set(TOOLS_PROPERTY, components.tools());
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
    }
}
