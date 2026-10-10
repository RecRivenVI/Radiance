package io.github.recrivenvi.configuration;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import javax.inject.Inject;
import org.gradle.api.GradleException;
import org.gradle.api.initialization.Settings;

public abstract class TargetRegistry {
    private final Settings settings;
    private final List<String> names = new ArrayList<>();

    @Inject
    public TargetRegistry(Settings settings) {
        this.settings = settings;
    }

    public void register(String name) {
        try {
            TargetFacts.parseName(name);
        } catch (IllegalArgumentException e) {
            throw new GradleException(e.getMessage());
        }
        if (names.contains(name)) throw new GradleException("重复的 Target：" + name);
        File directory = new File(settings.getRootDir(), "versions/" + name);
        for (String file : List.of("build.gradle.kts", "target.properties"))
            if (!new File(directory, file).isFile())
                throw new GradleException("Target " + name + " 需要 versions/" + name + "/" + file);
        String path = ":version:" + name;
        settings.include(path);
        settings.project(":version").setProjectDir(new File(settings.getRootDir(), "versions"));
        settings.project(path).setProjectDir(directory);
        names.add(name);
    }

    List<String> names() {
        return List.copyOf(names);
    }
}
