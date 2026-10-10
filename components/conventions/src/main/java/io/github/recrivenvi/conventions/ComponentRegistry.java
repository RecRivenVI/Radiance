package io.github.recrivenvi.conventions;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.inject.Inject;
import org.gradle.api.GradleException;
import org.gradle.api.initialization.Settings;

public abstract class ComponentRegistry {
    static final Set<String> TEMPLATE = Set.of("configuration", "conventions", "compliance");

    private final Settings settings;
    private final Map<String, Boolean> components = new LinkedHashMap<>();

    @Inject
    public ComponentRegistry(Settings settings) {
        this.settings = settings;
    }

    public void product(String name) {
        include(name, true);
    }

    public void tool(String name) {
        include(name, false);
    }

    List<String> products() {
        return named(true);
    }

    List<String> tools() {
        return named(false);
    }

    private List<String> named(boolean product) {
        List<String> names = new ArrayList<>();
        components.forEach(
                (name, kind) -> {
                    if (kind == product) names.add(name);
                });
        return List.copyOf(names);
    }

    static String module(String name) {
        return "components:" + name;
    }

    private void include(String name, boolean product) {
        if (TEMPLATE.contains(name)) throw new GradleException(name + " 是模板组件，已经被包含");
        if (components.containsKey(name)) throw new GradleException("重复的组件：" + name);
        File directory = new File(settings.getRootDir(), "components/" + name);
        for (String file : List.of("settings.gradle.kts", "build.gradle.kts"))
            if (!new File(directory, file).isFile())
                throw new GradleException("组件 " + name + " 需要 components/" + name + "/" + file);
        settings.includeBuild(
                directory,
                build ->
                        build.dependencySubstitution(
                                substitutions ->
                                        substitutions
                                                .substitute(substitutions.module(module(name)))
                                                .using(substitutions.project(":"))));
        components.put(name, product);
    }
}
