package io.github.recrivenvi.configuration;

import java.io.File;
import java.io.Serializable;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class RepositoryConfiguration implements Serializable {
    private static final Pattern MOD_ID = Pattern.compile("[a-z][a-z0-9_]{1,57}");
    private static final Pattern JAVA_PACKAGE =
            Pattern.compile("[a-z][a-z0-9_]*(\\.[a-z][a-z0-9_]*)*");
    private static final Pattern FACT_KEY = Pattern.compile("[a-z][a-z0-9_]*");
    private static final List<String> PROJECT_FACTS =
            List.of(
                    "mod_id",
                    "mod_name",
                    "mod_version",
                    "mod_group",
                    "mod_side",
                    "mod_authors",
                    "mod_license",
                    "mod_description");
    private static final List<String> SIDES = List.of("both", "client", "server");
    private static final Set<String> DERIVED_TARGET_FACTS =
            Set.of("target", "minecraft_version", "loader");
    private static final List<String> MAPPINGS = List.of("mojang", "yarn");

    private final File rootDirectory;
    private final Map<String, String> projectFacts;
    private final Side side;
    private final Map<String, TargetFacts> targets;
    private final Inputs inputs;
    private final DailyInstances daily;
    private final Map<String, Validation> validations;
    private final boolean eulaAccepted;

    RepositoryConfiguration(
            File rootDirectory,
            Map<String, String> gradleProperties,
            Map<String, Map<String, String>> targetProperties,
            Map<String, Object> inputsToml,
            Map<String, Object> instancesToml,
            Map<String, Object> localToml) {
        this(
                rootDirectory,
                gradleProperties,
                targetProperties,
                inputsToml,
                instancesToml,
                localToml,
                Map.of());
    }

    RepositoryConfiguration(
            File rootDirectory,
            Map<String, String> gradleProperties,
            Map<String, Map<String, String>> targetProperties,
            Map<String, Object> inputsToml,
            Map<String, Object> instancesToml,
            Map<String, Object> localToml,
            Map<String, Map<String, Object>> validationTomls) {
        this.rootDirectory = rootDirectory.getAbsoluteFile();
        this.projectFacts = projectFacts(gradleProperties);
        this.side = Side.of(projectFacts.get("mod_side"));
        Map<String, TargetFacts> targets = new LinkedHashMap<>();
        targetProperties.forEach((name, values) -> targets.put(name, targetFacts(name, values)));
        this.targets = Collections.unmodifiableMap(targets);
        this.inputs = new Inputs(this.rootDirectory, inputsToml, localToml);
        this.daily = new DailyInstances(instancesToml, localToml, targets.keySet(), inputs.names());
        this.validations = validations(validationTomls, targets.keySet(), inputs.names());
        this.eulaAccepted =
                Boolean.TRUE.equals(
                        new Section(DailyInstances.LOCAL_FILE, "", localToml).bool("eula"));
    }

    public File getRootDirectory() {
        return rootDirectory;
    }

    public Map<String, String> getProjectFacts() {
        return projectFacts;
    }

    public String getModId() {
        return projectFacts.get("mod_id");
    }

    public Side getSide() {
        return side;
    }

    public Set<String> getTargetNames() {
        return targets.keySet();
    }

    public TargetFacts target(String name) {
        TargetFacts facts = targets.get(name);
        if (facts == null)
            throw new IllegalArgumentException(
                    "未登记的 Target：" + name + Suggestions.hint(name, targets.keySet()));
        return facts;
    }

    public Map<String, String> metadata(String target) {
        TargetFacts facts = target(target);
        Map<String, String> metadata = new LinkedHashMap<>(projectFacts);
        metadata.putAll(facts.properties());
        metadata.putIfAbsent("loader_minimum", facts.loaderVersion());
        metadata.put("target", facts.name());
        metadata.put("minecraft_version", facts.minecraftVersion());
        metadata.put("loader", facts.loader());
        return Collections.unmodifiableMap(metadata);
    }

    public boolean isEulaAccepted() {
        return eulaAccepted;
    }

    public List<Instance> instances() {
        return daily.instances();
    }

    public Instance instance(String id) {
        return daily.instance(id);
    }

    public LaunchSettings launchSettings(String target, Instance instance) {
        target(target);
        return daily.launchSettings(rootDirectory, target, instance);
    }

    public List<String> describeLaunchSettings(String target, Instance instance) {
        target(target);
        return daily.describe(target, instance);
    }

    public List<Validation> validations() {
        return List.copyOf(validations.values());
    }

    public Validation validation(String name) {
        Validation validation = validations.get(name);
        if (validation == null)
            throw new IllegalArgumentException(
                    "未知的验证："
                            + name
                            + "；验证是 validations/ 中带 "
                            + Validation.FILE
                            + " 的目录"
                            + Suggestions.hint(name, validations.keySet()));
        return validation;
    }

    public LaunchSettings launchSettings(Validation validation, String target, Instance role) {
        check(validation, target, role);
        return validation.launchSettings(rootDirectory, target, role);
    }

    public List<String> describeLaunchSettings(
            Validation validation, String target, Instance role) {
        check(validation, target, role);
        return validation.describe(target, role);
    }

    private static void check(Validation validation, String target, Instance role) {
        String file = Validation.DIRECTORY + "/" + validation.name() + "/" + Validation.FILE;
        if (!validation.targets().contains(target))
            throw new ConfigurationException(
                    file,
                    "targets",
                    "没有列出已登记的 Target " + target + Suggestions.hint(target, validation.targets()));
        if (!validation.roles().contains(role))
            throw new ConfigurationException(
                    file,
                    "instances",
                    "没有声明 [instances."
                            + role.id()
                            + "]；已声明的角色："
                            + String.join(
                                    ", ", validation.roles().stream().map(Instance::id).toList()));
    }

    public Set<String> getInputNames() {
        return inputs.names();
    }

    public File input(String name) {
        return inputs.file(name);
    }

    // 输入缺失或不匹配时返回 null；原因由 verifyInputs 报告。
    public File availableInput(String name) {
        return inputs.available(name);
    }

    private static Map<String, Validation> validations(
            Map<String, Map<String, Object>> tomls, Set<String> targets, Set<String> inputs) {
        Map<String, Validation> validations = new TreeMap<>();
        Map<String, String> names = new LinkedHashMap<>();
        for (var entry : new TreeMap<>(tomls).entrySet()) {
            Validation validation =
                    new Validation(entry.getKey(), entry.getValue(), targets, inputs);
            String other = names.putIfAbsent(validation.camelName(), validation.name());
            if (other != null)
                throw new ConfigurationException(
                        Validation.DIRECTORY + "/" + validation.name(),
                        null,
                        "与 "
                                + Validation.DIRECTORY
                                + "/"
                                + other
                                + " 生成相同的任务名 "
                                + validation.camelName()
                                + "；改用不同的 <topic>");
            validations.put(validation.name(), validation);
        }
        return Collections.unmodifiableMap(validations);
    }

    private static Map<String, String> projectFacts(Map<String, String> gradleProperties) {
        String file = "gradle.properties";
        Map<String, String> facts = new LinkedHashMap<>();
        gradleProperties.forEach(
                (key, value) -> {
                    if (key.startsWith("mod_")) facts.put(key, value.strip());
                    else if (!key.startsWith("org.gradle."))
                        throw new ConfigurationException(
                                file, key, "只能写 mod_* 项目事实或 org.gradle.* 设置");
                });
        for (String key : PROJECT_FACTS)
            if (!facts.containsKey(key) || facts.get(key).isEmpty())
                throw new ConfigurationException(file, key, "缺少必需的项目事实，或取值为空");
        for (String key : facts.keySet())
            if (!FACT_KEY.matcher(key).matches())
                throw new ConfigurationException(file, key, "应为小写字母、数字与下划线");
        if (!MOD_ID.matcher(facts.get("mod_id")).matches())
            throw new ConfigurationException(file, "mod_id", "应为以字母开头的 2 到 58 个小写字母、数字或下划线");
        if (!JAVA_PACKAGE.matcher(facts.get("mod_group")).matches())
            throw new ConfigurationException(file, "mod_group", "应为小写的 Java 包名");
        if (facts.get("mod_version").contains(" "))
            throw new ConfigurationException(file, "mod_version", "版本号不能包含空格");
        if (!SIDES.contains(facts.get("mod_side")))
            throw new ConfigurationException(file, "mod_side", "应为 both、client 或 server");
        return Collections.unmodifiableMap(facts);
    }

    private static TargetFacts targetFacts(String name, Map<String, String> values) {
        Matcher matcher = TargetFacts.parseName(name);
        String file = "versions/" + name + "/target.properties";
        for (String key : values.keySet()) {
            if (DERIVED_TARGET_FACTS.contains(key))
                throw new ConfigurationException(file, key, "由 Target 名称推导；从本文件中删除");
            if (key.startsWith("mod_") || !FACT_KEY.matcher(key).matches())
                throw new ConfigurationException(file, key, "应为小写的 Target 事实");
        }
        Map<String, String> stripped = new LinkedHashMap<>();
        values.forEach((key, value) -> stripped.put(key, value.strip()));
        for (String key : List.of("loader_version", "java_version"))
            if (!stripped.containsKey(key) || stripped.get(key).isEmpty())
                throw new ConfigurationException(file, key, "缺少必需的 Target 事实，或取值为空");
        String java = stripped.get("java_version");
        if (!java.matches("[1-9][0-9]?") || Integer.parseInt(java) < 8)
            throw new ConfigurationException(file, "java_version", "应为 8 到 99 之间的 Java 主版本号");
        TargetFacts facts =
                new TargetFacts(
                        name,
                        matcher.group(1),
                        matcher.group(2),
                        stripped.get("loader_version"),
                        Integer.parseInt(java),
                        stripped);
        mappings(file, facts);
        return facts;
    }

    private static void mappings(String file, TargetFacts facts) {
        String mappings = facts.properties().get("mappings");
        if (!facts.remapped()) {
            for (String key : List.of("mappings", "yarn_version"))
                if (facts.properties().containsKey(key))
                    throw new ConfigurationException(
                            file, key, "只有混淆的 Minecraft 版本的 Fabric Target 可以选择映射表");
            return;
        }
        if (mappings != null && !MAPPINGS.contains(mappings))
            throw new ConfigurationException(file, "mappings", "应为 mojang（默认）或 yarn");
        boolean yarn = facts.properties().containsKey("yarn_version");
        if (facts.mappings().equals("yarn") != yarn)
            throw new ConfigurationException(
                    file, "yarn_version", yarn ? "只在 mappings=yarn 时填写" : "mappings=yarn 时必须填写");
    }
}
