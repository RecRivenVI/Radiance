package io.github.recrivenvi.component;

import java.io.File;
import org.gradle.api.Plugin;
import org.gradle.api.initialization.Settings;

public abstract class ComponentPlugin implements Plugin<Settings> {
    @Override
    public void apply(Settings settings) {
        File repository = settings.getRootDir().getParentFile().getParentFile();
        settings.getDependencyResolutionManagement().getRepositories().mavenCentral();
        settings.getDependencyResolutionManagement()
                .getVersionCatalogs()
                .create(
                        "libs",
                        catalog ->
                                catalog.from(
                                        settings.getLayout()
                                                .getRootDirectory()
                                                .files(
                                                        new File(
                                                                repository,
                                                                "gradle/libs.versions.toml"))));
        int release = ComponentConventions.lowestJava(new File(repository, "versions"));
        settings.getGradle().rootProject(root -> ComponentConventions.apply(root, release));
    }
}
