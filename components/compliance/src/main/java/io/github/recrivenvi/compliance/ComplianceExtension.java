package io.github.recrivenvi.compliance;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.gradle.api.GradleException;
import org.gradle.api.provider.Property;

public abstract class ComplianceExtension {
    static final Set<String> RESERVED_SOURCE_SETS =
            Set.of("main", "test", "probe", "client", "datagen", "gametest", "generated");

    private final Map<String, List<String>> vocabulary = new LinkedHashMap<>();
    private final Set<String> sourceSets = new TreeSet<>();

    public abstract Property<Boolean> getStrict();

    public void vocabulary(String location, String... words) {
        if (!Vocabulary.BUILT_IN.containsKey(location))
            throw new GradleException(
                    "未知的建议词位置 " + location + "；应为以下之一：" + Vocabulary.BUILT_IN.keySet());
        for (String word : words)
            if (!word.matches("[a-z]+")) throw new GradleException("补充词必须是单个小写英文单词：" + word);
        vocabulary.computeIfAbsent(location, key -> new ArrayList<>()).addAll(List.of(words));
    }

    public void sourceSets(String... names) {
        for (String name : names) {
            if (!name.matches("[a-z][a-zA-Z0-9]*"))
                throw new GradleException("源码集名称必须是小驼峰式的 Gradle 名称：" + name);
            if (RESERVED_SOURCE_SETS.contains(name))
                throw new GradleException(name + " 已由规范定义，不需要登记");
            sourceSets.add(name);
        }
    }

    Set<String> sourceSets() {
        return Set.copyOf(sourceSets);
    }

    Map<String, List<String>> vocabulary() {
        Map<String, List<String>> copy = new LinkedHashMap<>();
        vocabulary.forEach((location, words) -> copy.put(location, List.copyOf(words)));
        return copy;
    }
}
