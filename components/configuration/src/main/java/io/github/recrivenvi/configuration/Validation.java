package io.github.recrivenvi.configuration;

import java.io.File;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

public final class Validation implements Serializable {
    static final String DIRECTORY = "validations";
    static final String FILE = "validation.toml";

    private final String name;
    private final List<String> targets;
    private final List<Instance> roles;
    private final DailyInstances instances;

    Validation(
            String name,
            Map<String, Object> toml,
            Collection<String> registered,
            Collection<String> inputs) {
        this.name = name;
        Section root = new Section(DIRECTORY + "/" + name + "/" + FILE, "", toml);
        // tool 与 targets 的写法由 verifyCompliance 按 V-08 报告；这里只为其中已登记的 Target 生成运行。
        List<String> targets = new ArrayList<>();
        boolean listed = false;
        if (toml.get("targets") instanceof List<?> list)
            for (Object item : list) {
                listed = true;
                if (item instanceof String target
                        && registered.contains(target)
                        && !targets.contains(target)) targets.add(target);
            }
        this.targets = List.copyOf(targets);
        Section declared = root.table("instances");
        Section roles =
                declared == null ? new Section(root.file(), "instances", Map.of()) : declared;
        if (!roles.keys().isEmpty() && !listed)
            throw root.error(
                    "instances",
                    "targets 没有列出 Target，[instances] 不会生成验证运行；在 targets 中写出要运行的 Target，或删除"
                            + " [instances]");
        this.instances = DailyInstances.validation(roles, inputs);
        List<Instance> instances = new ArrayList<>();
        for (String role : roles.keys()) instances.add(this.instances.instance(role));
        this.roles = List.copyOf(instances);
    }

    public String name() {
        return name;
    }

    // 任务名中的写法：smoke-launch 与 visual-title_screen 分别写作 SmokeLaunch 与 VisualTitleScreen。
    public String camelName() {
        StringBuilder builder = new StringBuilder();
        for (String part : name.split("[^A-Za-z0-9]+"))
            if (!part.isEmpty())
                builder.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        return builder.toString();
    }

    public List<String> targets() {
        return targets;
    }

    public List<Instance> roles() {
        return roles;
    }

    public File directory(File root) {
        return new File(root, DIRECTORY + "/" + name);
    }

    LaunchSettings launchSettings(File root, String target, Instance role) {
        return instances.settings(
                new File(directory(root), "instance/" + target + "/" + role.id()), target, role);
    }

    List<String> describe(String target, Instance role) {
        return instances.describe(target, role);
    }
}
