package io.github.recrivenvi.conventions;

import io.github.recrivenvi.configuration.RepositoryConfiguration;
import java.io.File;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import javax.inject.Inject;
import org.gradle.api.GradleException;
import org.gradle.api.file.FileCollection;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.ProviderFactory;

public abstract class TargetExtension {
    private final String name;
    private final Map<String, String> facts;
    private final RepositoryConfiguration repository;
    private final Set<String> inputs = new LinkedHashSet<>();

    @Inject
    public TargetExtension(
            String name, Map<String, String> facts, RepositoryConfiguration repository) {
        this.name = name;
        this.facts = new TreeMap<>(facts);
        this.repository = repository;
    }

    @Inject
    protected abstract ObjectFactory getObjects();

    @Inject
    protected abstract ProviderFactory getProviders();

    public String getName() {
        return name;
    }

    public String fact(String key) {
        String value = facts.get(key);
        if (value == null)
            throw new GradleException(
                    "Target "
                            + name
                            + " 没有事实 "
                            + key
                            + "；在 versions/"
                            + name
                            + "/target.properties 中添加。已有的事实："
                            + String.join(", ", facts.keySet()));
        return value;
    }

    public String module(String module, String versionFact) {
        String[] parts = module.split(":", -1);
        if (parts.length != 2 || parts[0].isBlank() || parts[1].isBlank())
            throw new GradleException("target.module 需要不带版本的 <group>:<name>，实际为 " + module);
        return module + ":" + fact(versionFact);
    }

    public FileCollection input(String input) {
        if (!repository.getInputNames().contains(input))
            throw new GradleException(
                    "未知的本地输入 "
                            + input
                            + "；在 inputs.toml 中登记。已有的输入："
                            + String.join(", ", repository.getInputNames()));
        inputs.add(input);
        RepositoryConfiguration configuration = repository;
        return getObjects()
                .fileCollection()
                .from(
                        getProviders()
                                .provider(
                                        () -> {
                                            File file = configuration.availableInput(input);
                                            return file == null ? List.of() : List.of(file);
                                        }));
    }

    Set<String> inputs() {
        return Set.copyOf(inputs);
    }
}
