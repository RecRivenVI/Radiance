package io.github.recrivenvi.compliance;

import groovy.json.JsonException;
import groovy.json.JsonSlurper;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.tomlj.Toml;
import org.tomlj.TomlArray;
import org.tomlj.TomlParseError;
import org.tomlj.TomlParseResult;
import org.tomlj.TomlTable;

final class LoaderChecks {
    private static final Map<String, String> METADATA =
            Map.of(
                    "forge", "META-INF/mods.toml",
                    "neoforge", "META-INF/neoforge.mods.toml",
                    "fabric", "fabric.mod.json");
    private static final Map<String, String> MOD_FIELDS = new LinkedHashMap<>();
    private static final Map<String, String> JSON_FIELDS = new LinkedHashMap<>();
    private static final Map<String, List<String>> JSON_DEPENDENCIES = new LinkedHashMap<>();

    static {
        MOD_FIELDS.put("modId", "mod_id");
        MOD_FIELDS.put("version", "mod_version");
        MOD_FIELDS.put("displayName", "mod_name");
        MOD_FIELDS.put("authors", "mod_authors");
        MOD_FIELDS.put("description", "mod_description");
        JSON_FIELDS.put("id", "mod_id");
        JSON_FIELDS.put("version", "mod_version");
        JSON_FIELDS.put("name", "mod_name");
        JSON_FIELDS.put("description", "mod_description");
        JSON_FIELDS.put("license", "mod_license");
        JSON_FIELDS.put("environment", "fabric_environment");
        JSON_DEPENDENCIES.put("fabricloader", List.of("loader_version", "loader_minimum"));
        JSON_DEPENDENCIES.put("minecraft", List.of("minecraft_version"));
        JSON_DEPENDENCIES.put("java", List.of("java_version"));
    }

    private static final Pattern JAVA_SOURCE =
            Pattern.compile("versions/[^/]+/src/[^/]+/(?:java|templates)/(.+\\.java)");
    private static final Pattern RESOURCE_FILE =
            Pattern.compile(
                    "versions/[^/]+/src/[^/]+/(?:resources|generated)/(?:assets|data)/(.+)");
    private static final Pattern RESOURCE_PATH = Pattern.compile("[a-z0-9_.-]+(?:/[a-z0-9_.-]+)+");
    // TOML 的表头行；P-01 允许其中出现不加引号的 ${mod_id}。
    static final Pattern TOML_HEADER = Pattern.compile("^\\s*\\[\\[?[^\\[\\]]+]]?\\s*(?:#.*)?$");
    private static final String MOD_ID = "${mod_id}";
    private static final String TEMPLATE_REPOSITORY = "recrivenvi/ravens-mod-template";
    private static final String TEMPLATE_MOD_ID = "ravens_mod_template";
    private static final String TEMPLATE_GROUP = "io.github.recrivenvi.modtemplate";
    private static final Pattern TEMPLATE_NAME =
            Pattern.compile(
                    "ravens_mod_template|modtemplate|raven's mod template",
                    Pattern.CASE_INSENSITIVE);
    // 这些位置按规定保留模板的名称；gradle.properties 中的模板身份由 P-06 报告。
    private static final Set<String> NAMED_FILES =
            Set.of(
                    "AGENTS.md",
                    "CLAUDE.md",
                    ".rumdl.toml",
                    ".github/workflows/specification.yml",
                    "NOTICE",
                    "gradle.properties");

    private LoaderChecks() {}

    static void check(Repository repository, List<Finding> findings) {
        String group = repository.properties("gradle.properties").getProperty("mod_group");
        if (group != null) packages(repository, group.strip(), findings);
        resources(repository, findings);
        for (String target : new TreeSet<>(repository.targets()))
            metadata(repository, target, findings);
        identity(repository, findings);
    }

    // 用模板创建的仓库在改名之前仍带着模板的身份；只有模板仓库本身可以使用它。
    private static void identity(Repository repository, List<Finding> findings) {
        String origin = repository.origin();
        if (origin != null
                && origin.toLowerCase(Locale.ROOT)
                        .replaceAll("\\.git/?$", "")
                        .replaceAll("/$", "")
                        .endsWith(TEMPLATE_REPOSITORY)) return;
        names(repository, findings);
        Properties facts = repository.properties("gradle.properties");
        boolean id = TEMPLATE_MOD_ID.equals(facts.getProperty("mod_id", "").strip());
        boolean group = TEMPLATE_GROUP.equals(facts.getProperty("mod_group", "").strip());
        if (!id && !group) return;
        findings.add(
                Finding.of(
                        Rule.P06,
                        "gradle.properties",
                        "仍在使用模板自身的"
                                + (id && group ? "模组 ID 与 Java 包" : id ? "模组 ID" : "Java 包")
                                + "；按 documents/development/procedure-derive_project.md 改名"));
    }

    // 从模板带入、却没有按本项目改写的内容，例如模板示例模组的文档。
    private static void names(Repository repository, List<Finding> findings) {
        for (String file : repository.files()) {
            if (NAMED_FILES.contains(file)
                    || file.startsWith("licenses/")
                    || Repository.result(file)
                    || LayoutChecks.TEMPLATE_COMPONENTS.stream()
                            .anyMatch(name -> file.startsWith("components/" + name + "/")))
                continue;
            byte[] bytes = repository.bytes(file);
            if (repository.binary(file, bytes)) continue;
            List<String> lines = new String(bytes, StandardCharsets.UTF_8).lines().toList();
            for (int i = 0; i < lines.size(); i++)
                if (TEMPLATE_NAME.matcher(lines.get(i)).find()) {
                    findings.add(
                            new Finding(
                                    Rule.P07,
                                    file,
                                    i + 1,
                                    "含有模板自身的名称；模板名称只保留在模板文件、NOTICE 与 licenses/ 中，" + "按本项目改写或删除"));
                    break;
                }
        }
    }

    private static void packages(Repository repository, String group, List<Finding> findings) {
        String directory = group.replace('.', '/') + "/";
        for (String file : repository.filesUnder("versions")) {
            Matcher matcher = JAVA_SOURCE.matcher(file);
            if (matcher.matches() && !matcher.group(1).startsWith(directory))
                findings.add(Finding.of(Rule.L07, file, "Java 源码应位于包 " + group + " 或其子包"));
        }
    }

    private static void resources(Repository repository, List<Finding> findings) {
        for (String file : repository.filesUnder("versions")) {
            Matcher matcher = RESOURCE_FILE.matcher(file);
            if (matcher.matches() && !RESOURCE_PATH.matcher(matcher.group(1)).matches())
                findings.add(Finding.of(Rule.P05, file, "资源路径在命名空间之下只能使用 a-z、0-9、_、-、. 与 /"));
        }
    }

    private static void metadata(Repository repository, String target, List<Finding> findings) {
        String loader = target.substring(target.lastIndexOf('-') + 1);
        String expected = METADATA.get(loader);
        if (expected == null || repository.filesUnder("versions/" + target).isEmpty()) return;
        String resources = "versions/" + target + "/src/main/resources/";
        for (Map.Entry<String, String> entry : METADATA.entrySet()) {
            String file = resources + entry.getValue();
            if (!entry.getKey().equals(loader) && repository.exists(file))
                findings.add(Finding.of(Rule.P03, file, "属于 " + entry.getKey() + "，不属于 " + loader));
        }
        String file = resources + expected;
        if (!repository.exists(file)) {
            findings.add(Finding.of(Rule.P03, file, "缺少 " + loader + " 的元数据文件"));
            return;
        }
        String text = repository.text(file).replace("\r", "");
        if (loader.equals("fabric")) json(file, text, findings);
        else toml(file, text, loader, findings);
    }

    private static void toml(String file, String text, String loader, List<Finding> findings) {
        // P-01 允许表名中不加引号的 ${mod_id}，它不是合法的裸键，解析前加上引号。
        String quoted =
                text.lines()
                        .map(
                                line ->
                                        TOML_HEADER.matcher(line).matches()
                                                ? line.replace("\"" + MOD_ID + "\"", MOD_ID)
                                                        .replace(MOD_ID, "\"" + MOD_ID + "\"")
                                                : line)
                        .collect(Collectors.joining("\n"));
        TomlParseResult toml = Toml.parse(quoted);
        if (toml.hasErrors()) {
            TomlParseError error = toml.errors().getFirst();
            findings.add(
                    new Finding(
                            Rule.P04,
                            file,
                            error.position().line(),
                            "无法按 TOML 解析：" + error.getMessage()));
            return;
        }
        expect(file, toml, "", "license", "mod_license", findings);
        TomlTable mod = single(toml.get(List.of("mods")));
        if (mod == null) findings.add(Finding.of(Rule.P04, file, "必须有且只有一个 [[mods]] 条目"));
        else {
            MOD_FIELDS.forEach(
                    (key, placeholder) ->
                            expect(file, mod, "[[mods]] 的 ", key, placeholder, findings));
            if (loader.equals("forge"))
                expect(file, mod, "[[mods]] 的 ", "displayTest", "display_test", findings);
        }
        if (loader.equals("neoforge")
                && (toml.contains(List.of("displayTest"))
                        || (mod != null && mod.contains(List.of("displayTest")))))
            findings.add(Finding.of(Rule.P04, file, "NeoForge 不读取 displayTest；运行端由入口与网络负载决定"));
        List<TomlTable> dependencies = new ArrayList<>();
        Object owners = toml.get(List.of("dependencies"));
        if (owners instanceof TomlTable table) {
            for (String owner : new TreeSet<>(table.keySet())) {
                Object entries = table.get(List.of(owner));
                if (!owner.equals(MOD_ID))
                    findings.add(
                            Finding.of(
                                    Rule.P04,
                                    file,
                                    "依赖写在 dependencies."
                                            + owner
                                            + " 中；本模组的依赖写作 [[dependencies.${mod_id}]]"));
                else if (tables(entries) != null) dependencies.addAll(tables(entries));
                else
                    findings.add(
                            Finding.of(
                                    Rule.P04,
                                    file,
                                    "dependencies.${mod_id} 必须写作 [[dependencies.${mod_id}]]"));
            }
        } else if (owners != null) findings.add(Finding.of(Rule.P04, file, "dependencies 必须是表"));
        dependency(file, dependencies, "minecraft", List.of("minecraft_version"), findings);
        dependency(
                file, dependencies, loader, List.of("loader_version", "loader_minimum"), findings);
    }

    // 表数组返回其中的表，其他值返回 null。
    private static List<TomlTable> tables(Object value) {
        if (!(value instanceof TomlArray array)) return null;
        List<TomlTable> tables = new ArrayList<>();
        for (int i = 0; i < array.size(); i++) {
            if (!array.isTable(i)) return null;
            tables.add(array.getTable(i));
        }
        return tables;
    }

    private static TomlTable single(Object value) {
        List<TomlTable> tables = tables(value);
        return tables != null && tables.size() == 1 ? tables.getFirst() : null;
    }

    private static void expect(
            String file,
            TomlTable table,
            String scope,
            String key,
            String placeholder,
            List<Finding> findings) {
        if (!("${" + placeholder + "}").equals(table.get(List.of(key))))
            findings.add(
                    Finding.of(Rule.P04, file, scope + key + " 必须是 \"${" + placeholder + "}\""));
    }

    private static void dependency(
            String file,
            List<TomlTable> dependencies,
            String modId,
            List<String> placeholders,
            List<Finding> findings) {
        for (TomlTable dependency : dependencies)
            if (modId.equals(dependency.get(List.of("modId")))) {
                if (!mentions(dependency.get(List.of("versionRange")), placeholders))
                    findings.add(
                            Finding.of(
                                    Rule.P04,
                                    file,
                                    modId + " 的 versionRange 必须使用 " + names(placeholders)));
                return;
            }
        findings.add(
                Finding.of(Rule.P04, file, "[[dependencies.${mod_id}]] 中缺少对 " + modId + " 的依赖"));
    }

    // 版本要求是字符串，Fabric 还可以写成字符串数组；其中任一项使用占位符即可。
    private static boolean mentions(Object value, List<String> placeholders) {
        List<?> values = value instanceof List<?> list ? list : Collections.singletonList(value);
        return values.stream()
                .anyMatch(
                        item ->
                                item instanceof String text
                                        && placeholders.stream()
                                                .anyMatch(
                                                        name -> text.contains("${" + name + "}")));
    }

    private static String names(List<String> placeholders) {
        return String.join(" 或 ", placeholders.stream().map(name -> "${" + name + "}").toList());
    }

    private static void json(String file, String text, List<Finding> findings) {
        Object parsed;
        try {
            // 用 Gradle 自带的 Groovy JSON 解析器，不另加依赖。
            parsed = new JsonSlurper().parseText(text);
        } catch (JsonException e) {
            findings.add(Finding.of(Rule.P04, file, "无法按 JSON 解析：" + e.getMessage()));
            return;
        }
        if (!(parsed instanceof Map<?, ?> root)) {
            findings.add(Finding.of(Rule.P04, file, "顶层必须是对象"));
            return;
        }
        JSON_FIELDS.forEach(
                (key, placeholder) -> {
                    if (!("${" + placeholder + "}").equals(root.get(key)))
                        findings.add(
                                Finding.of(
                                        Rule.P04, file, key + " 必须是 \"${" + placeholder + "}\""));
                });
        if (!List.of("${mod_authors}").equals(root.get("authors")))
            findings.add(Finding.of(Rule.P04, file, "authors 必须是 [\"${mod_authors}\"]"));
        Map<?, ?> depends = root.get("depends") instanceof Map<?, ?> map ? map : Map.of();
        JSON_DEPENDENCIES.forEach(
                (modId, placeholders) -> {
                    if (!mentions(depends.get(modId), placeholders))
                        findings.add(
                                Finding.of(
                                        Rule.P04,
                                        file,
                                        "depends." + modId + " 必须使用 " + names(placeholders)));
                });
        Object entrypoints = root.get("entrypoints");
        if (entrypoints instanceof Map<?, ?> groups)
            groups.forEach(
                    (group, entries) -> {
                        List<?> items = entries instanceof List<?> list ? list : List.of(entries);
                        for (Object item : items) {
                            Object value =
                                    item instanceof Map<?, ?> entry ? entry.get("value") : item;
                            if (!(value instanceof String name)
                                    || !name.startsWith("${mod_group}."))
                                findings.add(
                                        Finding.of(
                                                Rule.P04,
                                                file,
                                                "entrypoints."
                                                        + group
                                                        + " 中的入口 "
                                                        + value
                                                        + " 必须以 ${mod_group}. 开头"));
                        }
                    });
        else if (entrypoints != null) findings.add(Finding.of(Rule.P04, file, "entrypoints 必须是对象"));
    }
}
