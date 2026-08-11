package io.github.recrivenvi.conventions.compliance;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// 第 3 节"目录"。
final class LayoutChecks {
    private static final Set<String> ROOT_FILES =
            Set.of(
                    "AGENTS.md",
                    "README.md",
                    "NOTICE",
                    "LICENSE",
                    "LICENSE.md",
                    "LICENSE.txt",
                    "COPYING",
                    "COPYING.LESSER",
                    "CONTRIBUTING.md",
                    "SECURITY.md",
                    "CODE_OF_CONDUCT.md",
                    ".gitattributes",
                    ".gitmodules",
                    ".editorconfig",
                    ".gitignore",
                    "settings.gradle.kts",
                    "build.gradle.kts",
                    "gradle.properties",
                    "inputs.toml",
                    "instances.toml",
                    "local.toml",
                    "gradlew",
                    "gradlew.bat");
    private static final Set<String> ROOT_DIRECTORIES =
            Set.of(
                    ".github",
                    "gradle",
                    "components",
                    "versions",
                    "instances",
                    "validations",
                    "documents",
                    "licenses");
    private static final Set<String> TARGET_FILES = Set.of("build.gradle.kts", "target.properties");
    private static final List<String> SOURCE_DIRECTORIES = List.of("main", "test", "probe");
    private static final Map<String, List<String>> LOADER_DIRECTORIES =
            Map.of(
                    "fabric", List.of("client", "datagen", "gametest"),
                    "neoforge", List.of("generated"),
                    "forge", List.of("generated"));
    private static final Pattern JAVA_SOURCE =
            Pattern.compile("versions/[^/]+/src/[^/]+/(?:java|templates)/(.+\\.java)");
    private static final Pattern LICENSE_TEXT =
            Pattern.compile("[a-z0-9]+(?:_[a-z0-9]+)*-[a-z0-9.]+(?:_[a-z0-9.]+)*\\.(?:txt|md)");
    private static final Set<String> GITHUB_FILES =
            Set.of(
                    ".github/PULL_REQUEST_TEMPLATE.md",
                    ".github/CODEOWNERS",
                    ".github/FUNDING.yml",
                    ".github/dependabot.yml");
    private static final Pattern WORKFLOW =
            Pattern.compile("\\.github/workflows/[a-z0-9]+(?:_[a-z0-9]+)*\\.ya?ml");
    private static final Pattern ISSUE_TEMPLATE =
            Pattern.compile("\\.github/ISSUE_TEMPLATE/[a-z0-9]+(?:_[a-z0-9]+)*\\.(?:md|ya?ml)");
    static final String COMPONENT_PLUGIN_ID = "io.github.recrivenvi.conventions.component";
    private static final Pattern COMPONENT_PLUGIN =
            Pattern.compile("id\\(\\s*\"" + Pattern.quote(COMPONENT_PLUGIN_ID) + "\"\\s*\\)");
    private static final Set<String> VAGUE_COMPONENTS =
            Set.of("common", "misc", "shared", "util", "utils", "helper", "helpers");
    private static final Pattern README_TRANSLATION =
            Pattern.compile("README-[a-z]{2,3}_[A-Z]{2}\\.md");
    private static final Pattern COMPONENT = Pattern.compile("[a-z0-9]+(_[a-z0-9]+)*");
    private static final List<String> IGNORE_RULES =
            List.of(
                    "/local.toml",
                    ".gradle/",
                    "build/",
                    "/instances/**",
                    "!/instances/**/",
                    "/validations/*/instance/");

    private LayoutChecks() {}

    static void check(Repository repository, List<Finding> findings) {
        root(repository, findings);
        versions(repository, findings);
        components(repository, findings);
        ignoreRules(repository, findings);
        String group = repository.properties("gradle.properties").getProperty("mod_group");
        if (group != null) packages(repository, group.strip(), findings);
        licenses(repository, findings);
        github(repository, findings);
    }

    private static void root(Repository repository, List<Finding> findings) {
        Set<String> reported = new LinkedHashSet<>();
        for (String file : repository.files()) {
            int separator = file.indexOf('/');
            String entry = separator < 0 ? file : file.substring(0, separator);
            boolean allowed =
                    separator < 0
                            ? ROOT_FILES.contains(entry)
                                    || README_TRANSLATION.matcher(entry).matches()
                            : ROOT_DIRECTORIES.contains(entry);
            if (!allowed && reported.add(entry))
                findings.add(Finding.of(Rule.L01, entry, "不在根目录允许的条目中"));
        }
    }

    private static void versions(Repository repository, List<Finding> findings) {
        Set<String> reported = new LinkedHashSet<>();
        for (String file : repository.filesUnder("versions")) {
            String[] parts = file.split("/");
            if (parts.length == 2) {
                findings.add(Finding.of(Rule.L02, file, "versions/ 中只能有 Target 目录"));
                continue;
            }
            String target = parts[1];
            if (!repository.targets().contains(target) && reported.add(target))
                findings.add(
                        Finding.of(Rule.L02, "versions/" + target, "没有在 settings.gradle.kts 中登记"));
            String entry = "versions/" + target + "/" + parts[2];
            boolean source = parts[2].equals("src");
            boolean leaf = parts.length == 3;
            if (TARGET_FILES.contains(parts[2]) ? !leaf : !source || leaf) {
                if (reported.add(entry))
                    findings.add(
                            Finding.of(
                                    Rule.L03,
                                    entry,
                                    "Target 只包含文件 build.gradle.kts、target.properties 与目录 src/"));
                continue;
            }
            if (!source) continue;
            List<String> allowed = new ArrayList<>(SOURCE_DIRECTORIES);
            allowed.addAll(
                    LOADER_DIRECTORIES.getOrDefault(
                            target.substring(target.lastIndexOf('-') + 1), List.of()));
            allowed.addAll(new TreeSet<>(repository.sourceSets()));
            String directory = entry + "/" + parts[3];
            if ((parts.length == 4 || !allowed.contains(parts[3])) && reported.add(directory))
                findings.add(
                        Finding.of(
                                Rule.L03,
                                directory,
                                "src/ 下只能有源码集目录：src/" + String.join("、src/", allowed)));
        }
    }

    private static void components(Repository repository, List<Finding> findings) {
        Set<String> reported = new LinkedHashSet<>();
        Set<String> names = new TreeSet<>();
        for (String file : repository.filesUnder("components")) {
            String[] parts = file.split("/");
            if (parts.length == 2) {
                findings.add(Finding.of(Rule.L04, file, "components/ 中只能有组件目录"));
                continue;
            }
            String name = parts[1];
            names.add(name);
            if ((!COMPONENT.matcher(name).matches() || VAGUE_COMPONENTS.contains(name))
                    && reported.add(name))
                findings.add(Finding.of(Rule.L04, "components/" + name, "使用表达职责、用 _ 连接的小写单词"));
        }
        for (String name : names) {
            String directory = "components/" + name;
            for (String script : List.of("settings.gradle.kts", "build.gradle.kts"))
                if (!repository.exists(directory + "/" + script))
                    findings.add(Finding.of(Rule.L08, directory, "缺少 " + directory + "/" + script));
            if (!repository.products().contains(name) && !repository.tools().contains(name))
                findings.add(
                        Finding.of(
                                Rule.L08,
                                directory,
                                (repository.components().contains(name)
                                                ? "直接用 includeBuild 包含的组件不会作为 product 打包，也不能执行验证；"
                                                : "")
                                        + "在 settings.gradle.kts 中用 components { product(\""
                                        + name
                                        + "\") } 或 tool(\""
                                        + name
                                        + "\") 登记"));
            String settings = directory + "/settings.gradle.kts";
            if (repository.exists(settings)
                    && !COMPONENT_PLUGIN.matcher(repository.text(settings)).find())
                findings.add(Finding.of(Rule.L08, settings, "应用 " + COMPONENT_PLUGIN_ID + " 设置插件"));
        }
    }

    private static void ignoreRules(Repository repository, List<Finding> findings) {
        if (!repository.exists(".gitignore")) {
            findings.add(Finding.of(Rule.L05, ".gitignore", "缺少 .gitignore"));
            return;
        }
        Set<String> lines =
                new LinkedHashSet<>(
                        repository.text(".gitignore").lines().map(String::strip).toList());
        for (String rule : IGNORE_RULES)
            if (!lines.contains(rule))
                findings.add(Finding.of(Rule.L05, ".gitignore", "缺少规则 " + rule));
    }

    private static void packages(Repository repository, String group, List<Finding> findings) {
        String directory = group.replace('.', '/') + "/";
        for (String file : repository.filesUnder("versions")) {
            Matcher matcher = JAVA_SOURCE.matcher(file);
            if (matcher.matches() && !matcher.group(1).startsWith(directory))
                findings.add(Finding.of(Rule.L07, file, "Java 源码应位于包 " + group + " 或其子包"));
        }
    }

    private static void licenses(Repository repository, List<Finding> findings) {
        for (String file : repository.filesUnder("licenses")) {
            String name = file.substring("licenses/".length());
            if (!LICENSE_TEXT.matcher(name).matches())
                findings.add(
                        Finding.of(Rule.L09, file, "许可原文直接放在 licenses/ 下，命名为 <来源>-<许可>.txt 或 .md"));
        }
    }

    private static void github(Repository repository, List<Finding> findings) {
        for (String file : repository.filesUnder(".github"))
            if (!GITHUB_FILES.contains(file)
                    && !WORKFLOW.matcher(file).matches()
                    && !ISSUE_TEMPLATE.matcher(file).matches())
                findings.add(
                        Finding.of(
                                Rule.L10,
                                file,
                                "应为 workflows/<name>.yml、ISSUE_TEMPLATE/<name>.md 或 .yml（.yml 也可写作 .yaml）、"
                                        + "PULL_REQUEST_TEMPLATE.md、CODEOWNERS、FUNDING.yml 或"
                                        + " dependabot.yml，名称为用 _ 连接的小写单词"));
    }
}
