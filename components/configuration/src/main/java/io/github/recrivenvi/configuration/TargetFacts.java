package io.github.recrivenvi.configuration;

import java.io.Serializable;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public record TargetFacts(
        String name,
        String minecraftVersion,
        String loader,
        String loaderVersion,
        int javaVersion,
        Map<String, String> properties)
        implements Serializable {
    private static final Pattern NAME =
            Pattern.compile("([0-9][0-9A-Za-z.]*(?:-[0-9A-Za-z.]+)*)-(neoforge|forge|fabric)");

    public TargetFacts {
        properties = Map.copyOf(properties);
    }

    // Mojang 从 26.1 起不再混淆 Minecraft；所有 1.x 版本都是混淆的。
    public boolean obfuscated() {
        return minecraftVersion.startsWith("1.");
    }

    public boolean remapped() {
        return loader.equals("fabric") && obfuscated();
    }

    // Mojang 官方名称与 NeoForge、Forge 一致，因此作为重映射的默认映射表。
    public String mappings() {
        return properties.getOrDefault("mappings", "mojang");
    }

    static Matcher parseName(String name) {
        Matcher matcher = NAME.matcher(name);
        if (!matcher.matches() || name.contains(".."))
            throw new IllegalArgumentException(
                    "无效的 Target 名称：" + name + "；应为 <Minecraft 版本>-<neoforge|forge|fabric>");
        return matcher;
    }
}
