package io.github.recrivenvi.configuration;

import java.io.File;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

final class DailyInstances implements Serializable {
    static final String SHARED_FILE = "instances.toml";
    static final String LOCAL_FILE = "local.toml";
    private static final Pattern PLAYER_NAME = Pattern.compile("[A-Za-z0-9_]{3,16}");
    private static final Pattern INSTANCE_NAME = Pattern.compile("[a-z][a-z0-9]*(?:-[a-z0-9]+)*");
    private static final List<String> PROPERTIES =
            List.of(
                    "memory-min",
                    "memory-max",
                    "width",
                    "height",
                    "player-name",
                    "port",
                    "jvm-args",
                    "game-args",
                    "mods");
    private static final List<String> RESERVED =
            List.of("all", "targets", "variants", "inputs", "eula");

    private final Map<String, Instance> instances = new LinkedHashMap<>();
    private final Map<Scope, Map<String, Value>> values = new LinkedHashMap<>();

    private DailyInstances() {
        for (Variant variant : Variant.values())
            instances.put(variant.id(), new Instance(variant.id(), variant));
    }

    DailyInstances(
            Map<String, Object> shared,
            Map<String, Object> local,
            Collection<String> targets,
            Collection<String> inputs) {
        this();
        Section sharedRoot = new Section(SHARED_FILE, "", shared);
        Section localRoot = new Section(LOCAL_FILE, "", local);
        variants(sharedRoot);
        variants(localRoot);
        read(sharedRoot, targets, inputs, false);
        read(localRoot, targets, inputs, true);
        for (String target : targets)
            for (Instance instance : instances.values()) checkMemory(target, instance);
    }

    // 验证的 [instances.<角色>] 只使用内置实例，不继承 instances.toml 与 local.toml 中的偏好，结果可以复现。
    static DailyInstances validation(Section roles, Collection<String> inputs) {
        DailyInstances daily = new DailyInstances();
        for (String role : roles.keys())
            if (!daily.instances.containsKey(role))
                throw roles.error(
                        role,
                        "应为 client、client-multiplayer 或 server"
                                + Suggestions.hint(role, daily.instances.keySet()));
        for (String role : roles.keys()) {
            daily.readTable(roles.table(role), new Scope(null, role), inputs);
            daily.checkMemory(null, daily.instances.get(role));
        }
        return daily;
    }

    private void checkMemory(String target, Instance instance) {
        Value min = lookup(target, instance, "memory-min");
        Value max = lookup(target, instance, "memory-max");
        if (Values.bytes(min.value().toString()) > Values.bytes(max.value().toString()))
            throw new ConfigurationException(
                    min.source(),
                    null,
                    (target == null ? "" : target + " ")
                            + instance.id()
                            + " 的 memory-min "
                            + min.value()
                            + " 超过 memory-max "
                            + max.value()
                            + "（"
                            + max.source()
                            + "）");
    }

    List<Instance> instances() {
        return List.copyOf(instances.values());
    }

    Instance instance(String id) {
        Instance instance = instances.get(id);
        if (instance == null)
            throw new IllegalArgumentException(
                    "未知的实例：" + id + Suggestions.hint(id, instances.keySet()));
        return instance;
    }

    LaunchSettings launchSettings(File root, String target, Instance instance) {
        return settings(
                new File(root, "instances/" + target + "/" + instance.id()), target, instance);
    }

    LaunchSettings settings(File directory, String target, Instance instance) {
        List<String> jvm = new ArrayList<>();
        jvm.add("-Xms" + lookup(target, instance, "memory-min").value());
        jvm.add("-Xmx" + lookup(target, instance, "memory-max").value());
        jvm.addAll(strings(lookup(target, instance, "jvm-args")));
        List<String> game = new ArrayList<>();
        if (instance.isClient()) {
            option(game, "--width", lookup(target, instance, "width"));
            option(game, "--height", lookup(target, instance, "height"));
            option(game, "--username", lookup(target, instance, "player-name"));
        } else {
            game.add("--nogui");
            option(game, "--port", lookup(target, instance, "port"));
        }
        game.addAll(strings(lookup(target, instance, "game-args")));
        return new LaunchSettings(directory, jvm, game, strings(lookup(target, instance, "mods")));
    }

    List<String> describe(String target, Instance instance) {
        List<String> lines = new ArrayList<>();
        if (!instance.builtIn()) lines.add("base = " + instance.base().id());
        for (String property : PROPERTIES) {
            if (!applies(property, instance)) continue;
            Value value = lookup(target, instance, property);
            lines.add(
                    value == null
                            ? property + "：未设置"
                            : property
                                    + " = "
                                    + display(value.value())
                                    + "  ("
                                    + value.source()
                                    + ")");
        }
        return lines;
    }

    private void variants(Section root) {
        Section variants = root.table("variants");
        if (variants == null) return;
        for (String name : variants.keys()) {
            if (instances.containsKey(name))
                throw variants.error(
                        name,
                        instances.get(name).builtIn()
                                ? name + " 是内置实例"
                                : name + " 已在 " + SHARED_FILE + " 中定义");
            if (!INSTANCE_NAME.matcher(name).matches() || RESERVED.contains(name))
                throw variants.error(name, "应为用 - 连接的小写单词，且不能是 " + String.join("、", RESERVED));
            Section definition = variants.table(name);
            definition.allow(List.of("base"), "额外实例设置");
            String base = definition.string("base");
            Variant variant = null;
            for (Variant candidate : Variant.values())
                if (candidate.id().equals(base)) variant = candidate;
            if (variant == null)
                throw definition.error("base", "应为 client、client-multiplayer 或 server");
            instances.put(name, new Instance(name, variant));
        }
    }

    private void read(
            Section root, Collection<String> targets, Collection<String> inputs, boolean local) {
        List<String> tables = new ArrayList<>(instances.keySet());
        tables.add("all");
        List<String> allowed = new ArrayList<>(tables);
        allowed.addAll(List.of("targets", "variants"));
        if (local) allowed.addAll(List.of("eula", "inputs"));
        for (String key : root.keys()) {
            if (key.equals("eula") && !local) throw root.error(key, "eula 只能写在 " + LOCAL_FILE);
            if (key.equals("inputs") && !local) throw root.error(key, "输入路径只能写在 " + LOCAL_FILE);
            if (!allowed.contains(key))
                throw root.error(key, "未知的表" + Suggestions.hint(key, allowed) + "；额外实例已移除时删除这张表");
        }
        for (String table : tables)
            if (root.has(table)) readTable(root.table(table), new Scope(null, table), inputs);
        Section targetTables = root.table("targets");
        if (targetTables == null) return;
        for (String target : targetTables.keys()) {
            if (!targets.contains(target))
                throw targetTables.error(
                        target,
                        "未登记的 Target" + Suggestions.hint(target, targets) + "；Target 已移除时删除这张表");
            Section targetTable = targetTables.table(target);
            targetTable.allow(tables, "实例表");
            for (String table : targetTable.keys())
                readTable(targetTable.table(table), new Scope(target, table), inputs);
        }
    }

    private void readTable(Section section, Scope scope, Collection<String> inputs) {
        section.allow(PROPERTIES, "实例设置");
        Instance instance = instances.get(scope.table());
        Map<String, Value> table = values.computeIfAbsent(scope, ignored -> new LinkedHashMap<>());
        for (String key : section.keys()) {
            if (!applies(key, instance)) throw section.error(key, key + " 不适用于 " + scope.table());
            Object value =
                    switch (key) {
                        case "memory-min", "memory-max" -> Values.memory(section, key);
                        case "width", "height" -> section.integer(key, 1, Integer.MAX_VALUE);
                        case "player-name" -> playerName(section, key);
                        case "port" -> section.integer(key, 1, 65535);
                        case "jvm-args" -> Values.jvmArguments(section, key);
                        case "mods" -> mods(section, key, inputs);
                        default -> Values.gameArguments(section, key);
                    };
            table.put(key, new Value(value, section.file() + " [" + section.path() + "]"));
        }
    }

    private static String playerName(Section section, String key) {
        String value = section.string(key);
        if (value == null || !PLAYER_NAME.matcher(value).matches())
            throw section.error(key, "应为 3 到 16 个 ASCII 字母、数字或下划线");
        return value;
    }

    private static List<String> mods(Section section, String key, Collection<String> inputs) {
        List<String> names = section.strings(key);
        Set<String> seen = new HashSet<>();
        for (String name : names) {
            if (!inputs.contains(name))
                throw section.error(
                        key,
                        name + " 没有在 " + Inputs.FILE + " 中登记" + Suggestions.hint(name, inputs));
            if (!seen.add(name)) throw section.error(key, name + " 重复出现");
        }
        return names;
    }

    private static boolean applies(String property, Instance instance) {
        boolean server = instance != null && instance.isServer();
        boolean client = instance != null && instance.isClient();
        return switch (property) {
            case "width", "height", "player-name" -> !server;
            case "port" -> !client;
            default -> true;
        };
    }

    // Minecraft 官方的默认值：启动器给客户端 2G；服务端下载页的命令让服务端最小、最大堆都是 1G；
    // 游戏窗口为 854x480；端口为 25565。
    private static Object defaultValue(String property, Instance instance) {
        return switch (property) {
            case "memory-max" -> instance.isServer() ? "1G" : "2G";
            case "width" -> 854L;
            case "height" -> 480L;
            case "player-name" ->
                    instance.base() == Variant.CLIENT_MULTIPLAYER ? "DevGuest" : "DevPlayer";
            case "port" -> 25565L;
            case "jvm-args", "game-args", "mods" -> List.of();
            default -> null;
        };
    }

    private Value lookup(String target, Instance instance, String property) {
        List<Scope> scopes = new ArrayList<>();
        scopes.add(new Scope(target, instance.id()));
        if (!instance.builtIn()) scopes.add(new Scope(target, instance.base().id()));
        scopes.add(new Scope(target, "all"));
        scopes.add(new Scope(null, instance.id()));
        if (!instance.builtIn()) scopes.add(new Scope(null, instance.base().id()));
        scopes.add(new Scope(null, "all"));
        for (Scope scope : scopes) {
            Map<String, Value> table = values.get(scope);
            if (table != null && table.containsKey(property)) return table.get(property);
        }
        if (property.equals("memory-min"))
            return new Value(lookup(target, instance, "memory-max").value(), "默认值，等于 memory-max");
        Object value = defaultValue(property, instance);
        return value == null ? null : new Value(value, "默认值");
    }

    private static void option(List<String> arguments, String option, Value value) {
        if (value != null) arguments.addAll(List.of(option, value.value().toString()));
    }

    @SuppressWarnings("unchecked")
    private static List<String> strings(Value value) {
        return value == null ? List.of() : (List<String>) value.value();
    }

    private static String display(Object value) {
        if (value instanceof String text) return Json.quote(text);
        if (value instanceof List<?> list)
            return "["
                    + String.join(
                            ", ", list.stream().map(item -> Json.quote(item.toString())).toList())
                    + "]";
        return value.toString();
    }

    private record Scope(String target, String table) implements Serializable {}

    private record Value(Object value, String source) implements Serializable {}
}
