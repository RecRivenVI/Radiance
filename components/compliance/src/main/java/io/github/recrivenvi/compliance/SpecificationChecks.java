package io.github.recrivenvi.compliance;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class SpecificationChecks {
    static final String VERSION = "1.2.1";
    static final String MANIFEST =
            "components/compliance/src/main/resources/io/github/recrivenvi/compliance/template-files.sha256";
    static final List<String> TEMPLATE_DIRECTORIES =
            List.of("components/configuration", "components/conventions", "components/compliance");
    static final String PROJECT = "documents/AGENTS.md";
    static final int INSTRUCTION_LIMIT = 32 * 1024;
    static final String CLAUDE = "@AGENTS.md\n@documents/AGENTS.md";

    private static final Pattern VERSION_LINE = Pattern.compile("^规范版本：(\\S+)$");
    private static final Pattern RULE_LINE =
            Pattern.compile("^- \\*\\*([A-Z]-\\d{2})\\*\\* \\[(检查|提示|构建|判断)]");
    private static final List<String> VOCABULARY_HEADER = List.of("位置", "建议类型词");
    private static final List<String> PROJECT_HEADER = List.of("项", "值");
    private static final Map<String, String> LANGUAGES =
            new TreeMap<>(Map.of("简体中文", "zh", "English", "en"));
    private static final List<String> TARGET_HEADER = List.of("Target", "加载器版本", "Java");

    private SpecificationChecks() {}

    static void check(
            Repository repository, Map<String, String> lockedHashes, List<Finding> findings) {
        claude(repository, findings);
        hashes(repository, lockedHashes, findings);
        if (!repository.exists("AGENTS.md")) {
            findings.add(Finding.of(Rule.S01, "AGENTS.md", "缺少 AGENTS.md"));
            return;
        }
        Markdown agents = new Markdown(repository.text("AGENTS.md"));
        version(agents, findings);
        rules(agents, findings);
        vocabulary(agents, findings);
        project(repository, findings);
    }

    static String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void claude(Repository repository, List<Finding> findings) {
        if (repository.exists("CLAUDE.md")
                && !repository.text("CLAUDE.md").replace("\r", "").strip().equals(CLAUDE))
            findings.add(
                    Finding.of(
                            Rule.S04,
                            "CLAUDE.md",
                            "CLAUDE.md 只能包含 @AGENTS.md 与 @documents/AGENTS.md"));
    }

    static Map<String, String> templateHashes() {
        Map<String, String> hashes = new TreeMap<>();
        try (InputStream input =
                SpecificationChecks.class.getResourceAsStream("template-files.sha256")) {
            if (input == null) throw new IllegalStateException("缺少 template-files.sha256");
            for (String line :
                    new String(input.readAllBytes(), StandardCharsets.UTF_8).split("\n")) {
                if (line.isBlank()) continue;
                String path = line.substring(64).strip();
                hashes.put(path.startsWith("*") ? path.substring(1) : path, line.substring(0, 64));
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return hashes;
    }

    private static void hashes(
            Repository repository, Map<String, String> lockedHashes, List<Finding> findings) {
        String restore = "；恢复规范 " + VERSION + " 的模板文件";
        for (Map.Entry<String, String> locked : new TreeMap<>(lockedHashes).entrySet()) {
            String file = locked.getKey();
            if (!repository.exists(file)) {
                if (!file.equals("CLAUDE.md"))
                    findings.add(Finding.of(Rule.S06, file, "缺少模板文件" + restore));
            } else if (!sha256(repository.bytes(file)).equals(locked.getValue()))
                findings.add(
                        Finding.of(
                                Rule.S06,
                                file,
                                "与模板不一致"
                                        + restore
                                        + "，不要直接修改（SHA-256 "
                                        + sha256(repository.bytes(file))
                                        + "）"));
        }
        for (String directory : TEMPLATE_DIRECTORIES)
            for (String file : repository.filesUnder(directory))
                if (!lockedHashes.containsKey(file) && !file.equals(MANIFEST))
                    findings.add(Finding.of(Rule.S06, file, "不是模板的一部分；项目代码放进项目组件"));
    }

    private static void version(Markdown agents, List<Finding> findings) {
        List<String> lines = agents.lines();
        for (int i = 0; i < Math.min(lines.size(), 10); i++) {
            Matcher matcher = VERSION_LINE.matcher(lines.get(i).strip());
            if (!matcher.matches()) continue;
            if (!matcher.group(1).equals(VERSION))
                findings.add(
                        new Finding(
                                Rule.S01,
                                "AGENTS.md",
                                i + 1,
                                "声明的规范版本为 " + matcher.group(1) + "，检查器实现的是 " + VERSION));
            return;
        }
        findings.add(Finding.of(Rule.S01, "AGENTS.md", "应声明\"规范版本：" + VERSION + "\""));
    }

    private static void rules(Markdown agents, List<Finding> findings) {
        Map<String, String> documented = new LinkedHashMap<>();
        Map<String, Integer> lines = new LinkedHashMap<>();
        for (int i = 0; i < agents.lines().size(); i++) {
            if (agents.inCode(i)) continue;
            Matcher matcher = RULE_LINE.matcher(agents.lines().get(i));
            if (!matcher.find()) continue;
            if (documented.putIfAbsent(matcher.group(1), matcher.group(2)) != null)
                findings.add(
                        new Finding(
                                Rule.S02, "AGENTS.md", i + 1, "规则 " + matcher.group(1) + " 重复定义"));
            lines.putIfAbsent(matcher.group(1), i + 1);
        }
        Set<String> implemented = new LinkedHashSet<>();
        for (Rule rule : Rule.values()) {
            implemented.add(rule.id());
            String expected = rule.level() == Rule.Level.FAIL ? "检查" : "提示";
            String actual = documented.get(rule.id());
            if (actual == null)
                findings.add(
                        Finding.of(Rule.S02, "AGENTS.md", "规则 " + rule.id() + " 由检查器实现，但规范中没有"));
            else if (!actual.equals(expected))
                findings.add(
                        new Finding(
                                Rule.S02,
                                "AGENTS.md",
                                lines.get(rule.id()),
                                "规则 "
                                        + rule.id()
                                        + " 标记为 ["
                                        + actual
                                        + "]，检查器按 ["
                                        + expected
                                        + "] 执行"));
        }
        documented.forEach(
                (id, tag) -> {
                    if ((tag.equals("检查") || tag.equals("提示")) && !implemented.contains(id))
                        findings.add(
                                new Finding(
                                        Rule.S02,
                                        "AGENTS.md",
                                        lines.get(id),
                                        "规则 " + id + " 标记为 [" + tag + "]，检查器没有实现"));
                });
    }

    private static void vocabulary(Markdown agents, List<Finding> findings) {
        Markdown.Table table = null;
        for (Markdown.Table candidate : agents.tables())
            if (candidate.header().containsAll(VOCABULARY_HEADER)) table = candidate;
        if (table == null) {
            findings.add(Finding.of(Rule.S03, "AGENTS.md", "缺少列为以下内容的建议词表：" + VOCABULARY_HEADER));
            return;
        }
        int locationColumn = table.header().indexOf("位置");
        int wordColumn = table.header().indexOf("建议类型词");
        Map<String, Set<String>> documented = new LinkedHashMap<>();
        for (List<String> row : table.rows()) {
            if (row.size() <= Math.max(locationColumn, wordColumn)) continue;
            List<String> location = Markdown.backticked(row.get(locationColumn));
            if (location.size() != 1) continue;
            documented.put(
                    location.getFirst(), new TreeSet<>(Markdown.backticked(row.get(wordColumn))));
        }
        Set<String> locations = new TreeSet<>(documented.keySet());
        locations.addAll(Vocabulary.BUILT_IN.keySet());
        for (String location : locations) {
            Set<String> expected =
                    new TreeSet<>(Vocabulary.BUILT_IN.getOrDefault(location, List.of()));
            Set<String> actual = documented.getOrDefault(location, Set.of());
            if (!expected.equals(actual))
                findings.add(
                        new Finding(
                                Rule.S03,
                                "AGENTS.md",
                                table.line(),
                                location + " 列出 " + actual + "，检查器建议 " + expected));
        }
    }

    private static void project(Repository repository, List<Finding> findings) {
        if (!repository.exists(PROJECT)) {
            findings.add(Finding.of(Rule.S05, PROJECT, "缺少项目规范"));
            return;
        }
        int size = repository.bytes("AGENTS.md").length + repository.bytes(PROJECT).length;
        if (size > INSTRUCTION_LIMIT)
            findings.add(
                    Finding.of(
                            Rule.S07,
                            PROJECT,
                            "AGENTS.md 与 "
                                    + PROJECT
                                    + " 合计 "
                                    + size
                                    + " 字节，应不超过 "
                                    + INSTRUCTION_LIMIT));
        Markdown project = new Markdown(repository.text(PROJECT));
        information(repository, project, findings);
        targets(repository, project, findings);
    }

    private static void information(
            Repository repository, Markdown project, List<Finding> findings) {
        Markdown.Table table = table(project, PROJECT_HEADER);
        if (table == null) {
            findings.add(Finding.of(Rule.S05, PROJECT, "缺少列为以下内容的项目信息表：" + PROJECT_HEADER));
            return;
        }
        Map<String, String> values = new LinkedHashMap<>();
        for (List<String> row : table.rows())
            if (row.size() >= 2) values.put(row.get(0), row.get(1));
        Properties facts = repository.properties("gradle.properties");
        expect(table, values, "产品显示名", facts.getProperty("mod_name"), false, findings);
        expect(table, values, "模组 ID", facts.getProperty("mod_id"), true, findings);
        expect(table, values, "Java 包", facts.getProperty("mod_group"), true, findings);
        String language = values.get("文档主语言");
        if (language != null && !LANGUAGES.containsKey(language.strip()))
            findings.add(
                    new Finding(
                            Rule.S05,
                            PROJECT,
                            table.line(),
                            "文档主语言必须是以下之一：" + String.join(", ", LANGUAGES.keySet())));
        for (String required : List.of("入口类", "业务", "文档主语言", "首页语言", "参考 Target"))
            if (!values.containsKey(required))
                findings.add(new Finding(Rule.S05, PROJECT, table.line(), "缺少行 " + required));
        String reference = values.get("参考 Target");
        if (reference != null) {
            List<String> names = Markdown.backticked(reference);
            if (names.size() != 1 || !repository.targets().contains(names.getFirst()))
                findings.add(
                        new Finding(
                                Rule.S05,
                                PROJECT,
                                table.line(),
                                "参考 Target 必须是一个用反引号括起的已登记 Target"));
        }
    }

    private static void expect(
            Markdown.Table table,
            Map<String, String> values,
            String row,
            String fact,
            boolean code,
            List<Finding> findings) {
        String value = values.get(row);
        if (value == null) {
            findings.add(new Finding(Rule.S05, PROJECT, table.line(), "缺少行 " + row));
            return;
        }
        if (fact == null) return;
        List<String> backticked = Markdown.backticked(value);
        String actual = code ? (backticked.size() == 1 ? backticked.getFirst() : value) : value;
        if (!actual.equals(fact.strip()))
            findings.add(
                    new Finding(
                            Rule.S05,
                            PROJECT,
                            table.line(),
                            row + " 列出 " + value + "，gradle.properties 中为 " + fact.strip()));
    }

    private static void targets(Repository repository, Markdown project, List<Finding> findings) {
        Markdown.Table table = table(project, TARGET_HEADER);
        if (table == null) {
            findings.add(Finding.of(Rule.S05, PROJECT, "缺少列为以下内容的 Target 表：" + TARGET_HEADER));
            return;
        }
        Set<String> listed = new TreeSet<>();
        for (List<String> row : table.rows()) {
            List<String> names = Markdown.backticked(row.getFirst());
            if (names.size() != 1 || row.size() < 3) continue;
            String target = names.getFirst();
            listed.add(target);
            if (!repository.targets().contains(target)) {
                findings.add(new Finding(Rule.S05, PROJECT, table.line(), target + " 已列出但没有登记"));
                continue;
            }
            Properties facts = repository.properties("versions/" + target + "/target.properties");
            compare(
                    target,
                    "加载器版本",
                    row.get(1),
                    facts.getProperty("loader_version"),
                    table,
                    findings);
            compare(
                    target,
                    "Java 版本",
                    row.get(2),
                    facts.getProperty("java_version"),
                    table,
                    findings);
        }
        for (String target : new TreeSet<>(repository.targets()))
            if (!listed.contains(target))
                findings.add(new Finding(Rule.S05, PROJECT, table.line(), target + " 已登记但没有列出"));
    }

    private static void compare(
            String target,
            String name,
            String documented,
            String actual,
            Markdown.Table table,
            List<Finding> findings) {
        if (actual != null && !documented.strip().equals(actual.strip()))
            findings.add(
                    new Finding(
                            Rule.S05,
                            PROJECT,
                            table.line(),
                            target
                                    + " 列出 "
                                    + name
                                    + " "
                                    + documented
                                    + "，target.properties 中为 "
                                    + actual.strip()));
    }

    static String language(Repository repository) {
        if (!repository.exists(PROJECT)) return "zh";
        Markdown.Table table = table(new Markdown(repository.text(PROJECT)), PROJECT_HEADER);
        if (table == null) return "zh";
        for (List<String> row : table.rows())
            if (row.size() >= 2 && row.get(0).equals("文档主语言"))
                return LANGUAGES.getOrDefault(row.get(1).strip(), "zh");
        return "zh";
    }

    static Markdown.Table table(Markdown markdown, List<String> header) {
        for (Markdown.Table table : markdown.tables())
            if (table.header().equals(header)) return table;
        return null;
    }
}
