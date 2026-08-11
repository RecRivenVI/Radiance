package io.github.recrivenvi.conventions.compliance;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.tomlj.Toml;
import org.tomlj.TomlParseResult;
import org.tomlj.TomlTable;

// 第 4 节"事实与配置"。模板定义的工具版本只随模板版本升级（F-06）。项目会在同一个版本目录中增加自己的依赖，
// 所以不能像模板文件那样整文件锁定，只核对模板的条目。
final class FactChecks {
    static final String CATALOG = "gradle/libs.versions.toml";
    private static final List<String> TABLES = List.of("versions", "libraries", "plugins");
    private static final Set<String> MINECRAFT_TOOLS = Set.of("moddev", "loom");
    private static final Pattern USES =
            Pattern.compile("^\\s*(?:-\\s*)?uses:\\s*[\"']?([^@\\s\"']+)@([^\\s\"']+)");
    private static final String UPGRADE = "；模板工具只随模板版本升级，见 procedure-upgrade_template";

    private FactChecks() {}

    static void check(Repository repository, List<Finding> findings) {
        catalog(repository, findings);
        actions(repository, findings);
    }

    // 模板发布时 gradle/libs.versions.toml 的副本。
    static String templateCatalog() {
        return resource("template-catalog.toml");
    }

    private static void catalog(Repository repository, List<Finding> findings) {
        if (!repository.exists(CATALOG)) {
            findings.add(Finding.of(Rule.F06, CATALOG, "缺少版本目录；从模板复制"));
            return;
        }
        TomlParseResult project = Toml.parse(repository.text(CATALOG));
        if (project.hasErrors()) {
            findings.add(Finding.of(Rule.F06, CATALOG, "无法解析：" + project.errors().getFirst()));
            return;
        }
        TomlParseResult template = Toml.parse(templateCatalog());
        for (String table : TABLES) {
            TomlTable expected = template.getTable(table);
            if (expected == null) continue;
            TomlTable actual = project.getTable(table);
            for (String key : expected.keySet()) {
                Object want = expected.get(List.of(key));
                if (minecraft(key, want)) continue;
                Object have = actual == null ? null : actual.get(List.of(key));
                if (have == null)
                    findings.add(
                            Finding.of(Rule.F06, CATALOG, "缺少模板条目 " + table + "." + key + UPGRADE));
                else if (!canonical(want).equals(canonical(have)))
                    findings.add(
                            Finding.of(
                                    Rule.F06,
                                    CATALOG,
                                    table + "." + key + " 应与模板相同：" + canonical(want) + UPGRADE));
            }
        }
    }

    // 工作流中用到模板 check.yml 里的 Action 时，版本必须相同；其他 Action 由项目决定。
    private static void actions(Repository repository, List<Finding> findings) {
        Map<String, String> expected = new TreeMap<>();
        for (String line : resource("template-check.yml").lines().toList()) {
            Matcher matcher = USES.matcher(line);
            if (matcher.find()) expected.put(matcher.group(1), matcher.group(2));
        }
        for (String file : repository.filesUnder(".github/workflows")) {
            if (!file.endsWith(".yml") && !file.endsWith(".yaml")) continue;
            List<String> lines = repository.text(file).lines().toList();
            for (int i = 0; i < lines.size(); i++) {
                Matcher matcher = USES.matcher(lines.get(i));
                if (!matcher.find()) continue;
                String want = expected.get(matcher.group(1));
                if (want != null && !want.equals(matcher.group(2)))
                    findings.add(
                            new Finding(
                                    Rule.F06,
                                    file,
                                    i + 1,
                                    matcher.group(1) + " 应使用模板的版本 @" + want + UPGRADE));
            }
        }
    }

    // Minecraft 构建插件的版本与条目由项目自己升级（F-06），其余工具随模板版本升级。
    private static boolean minecraft(String key, Object value) {
        if (MINECRAFT_TOOLS.contains(key)) return true;
        return value instanceof TomlTable table
                && MINECRAFT_TOOLS.contains(String.valueOf(table.get("version.ref")));
    }

    private static String canonical(Object value) {
        if (!(value instanceof TomlTable table)) return String.valueOf(value);
        Map<String, String> entries = new TreeMap<>();
        for (String key : table.dottedKeySet()) entries.put(key, String.valueOf(table.get(key)));
        return entries.toString();
    }

    private static String resource(String name) {
        try (InputStream input = FactChecks.class.getResourceAsStream(name)) {
            if (input == null) throw new IllegalStateException("缺少 " + name);
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
