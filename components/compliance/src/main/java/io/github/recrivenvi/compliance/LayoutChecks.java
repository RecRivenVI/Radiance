package io.github.recrivenvi.compliance;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

final class LayoutChecks {
    private static final Set<String> ROOT_FILES =
            Set.of(
                    "AGENTS.md",
                    "CLAUDE.md",
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
                    ".rumdl.toml",
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
    private static final Pattern LICENSE_TEXT =
            Pattern.compile("[a-z0-9]+(?:_[a-z0-9]+)*-[a-z0-9.]+(?:_[a-z0-9.]+)*\\.(?:txt|md)");
    private static final Set<String> BINARIES = Set.of("jar", "class", "dll", "so", "dylib", "exe");
    private static final String WRAPPER = "gradle/wrapper/gradle-wrapper.jar";
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
    // 只比较随模组发布的源码集；probe、test 等源码集中的代码不属于 product 组件。
    private static final Pattern SHARED_SOURCE =
            Pattern.compile(
                    "versions/[^/]+/src/(?:main|client)/java/(?:.+/)?(?!package-info|module-info)[^/]+\\.java");
    private static final Pattern GAME_REFERENCE =
            Pattern.compile(
                    "\\b(?:net\\.minecraft|com\\.mojang|net\\.neoforged|net\\.minecraftforge"
                            + "|net\\.fabricmc|cpw\\.mods|org\\.spongepowered)\\b");
    static final Set<String> TEMPLATE_COMPONENTS =
            Set.of("configuration", "conventions", "compliance");
    private static final Pattern COMPONENT_PLUGIN =
            Pattern.compile("id\\(\\s*\"io\\.github\\.recrivenvi\\.component\"\\s*\\)");
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
    // T-02 逐行检查去掉注释后的 Target 脚本：字符串中的版本号（网址除外）与带版本的依赖坐标，以及插件版本。
    private static final Pattern STRING = Pattern.compile("\"(?:[^\"\\\\]|\\\\.)*\"");
    private static final Pattern COORDINATE = Pattern.compile("\"[\\w.${}-]+:[\\w.${}-]+:.*");
    private static final Pattern VERSION_NUMBER = Pattern.compile("\\d+\\.\\d+");
    private static final Pattern PLUGIN_VERSION = Pattern.compile("\\)\\s*version\\b");
    private static final Pattern METADATA =
            Pattern.compile(
                    "versions/[^/]+/src/[^/]+/resources/(META-INF/mods\\.toml|META-INF/neoforge\\.mods\\.toml|fabric\\.mod\\.json|pack\\.mcmeta)");
    private static final Pattern PLACEHOLDER = Pattern.compile("\\$\\{([^}]*)}");

    // 实例目录被 Git 忽略，持续集成里不存在，只会在使用者本机留下。
    private static final String LEFTOVER = "如果它属于已移除的 Target 或额外实例，其中可能有存档，经使用者同意后移出仓库或删除";

    private LayoutChecks() {}

    static void check(Repository repository, List<Finding> findings) {
        ignored(repository, findings);
        binaries(repository, findings);
        root(repository, findings);
        licenses(repository, findings);
        github(repository, findings);
        duplicates(repository, findings);
        versions(repository, findings);
        components(repository, findings);
        ignoreRules(repository, findings);
        targetScripts(repository, findings);
        metadata(repository, findings);
        instances(repository, findings);
    }

    private static void ignored(Repository repository, List<Finding> findings) {
        for (String file : repository.trackedIgnoredFiles())
            findings.add(
                    Finding.of(Rule.G06, file, "被 .gitignore 忽略却被 Git 跟踪；用 git rm --cached 移除"));
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
                                "应为 workflows/<name>.yml、ISSUE_TEMPLATE/<name>.md 或 .yml、"
                                        + "PULL_REQUEST_TEMPLATE.md、CODEOWNERS、FUNDING.yml 或"
                                        + " dependabot.yml，名称为用 _ 连接的小写单词"));
    }

    private static void duplicates(Repository repository, List<Finding> findings) {
        Map<String, List<String>> groups = new TreeMap<>();
        for (String file : repository.filesUnder("versions")) {
            if (!SHARED_SOURCE.matcher(file).matches()) continue;
            String text = repository.text(file).replace("\r", "");
            if (GAME_REFERENCE.matcher(text).find()) continue;
            groups.computeIfAbsent(
                            SpecificationChecks.sha256(text.getBytes(StandardCharsets.UTF_8)),
                            key -> new ArrayList<>())
                    .add(file);
        }
        for (List<String> files : groups.values()) {
            Set<String> targets = new TreeSet<>();
            for (String file : files) targets.add(file.split("/")[1]);
            if (targets.size() < 2) continue;
            findings.add(
                    Finding.of(
                            Rule.T07,
                            files.getFirst(),
                            "在 "
                                    + String.join(", ", targets)
                                    + " 中内容相同，且不引用 Minecraft 与加载器的类；移入"
                                    + " product 组件"));
        }
    }

    private static void binaries(Repository repository, List<Finding> findings) {
        for (String file : repository.files()) {
            String extension = file.substring(file.lastIndexOf('.') + 1).toLowerCase();
            if (file.lastIndexOf('.') > file.lastIndexOf('/')
                    && BINARIES.contains(extension)
                    && !file.equals(WRAPPER))
                findings.add(Finding.of(Rule.G09, file, "构建二进制由构建生成，或作为本地输入登记在 inputs.toml"));
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

    private static void instances(Repository repository, List<Finding> findings) {
        Path directory = repository.root().resolve("instances");
        if (!Files.exists(directory)) return;
        if (!Files.isDirectory(directory)) {
            findings.add(Finding.of(Rule.I04, "instances", "instances 必须是目录"));
            return;
        }
        for (Path target : children(directory)) {
            String name = "instances/" + target.getFileName();
            if (!Files.isDirectory(target)) {
                findings.add(Finding.of(Rule.I04, name, "这里只能有已登记 Target 的实例目录"));
                continue;
            }
            if (!repository.targets().contains(target.getFileName().toString())) {
                findings.add(Finding.of(Rule.I04, name, "不是已登记的 Target；" + LEFTOVER));
                continue;
            }
            for (Path variant : children(target))
                if (!Files.isDirectory(variant)
                        || !repository.instances().contains(variant.getFileName().toString()))
                    findings.add(
                            Finding.of(
                                    Rule.I04,
                                    name + "/" + variant.getFileName(),
                                    "不是已登记的实例（"
                                            + String.join(
                                                    "、", new TreeSet<>(repository.instances()))
                                            + "）；"
                                            + LEFTOVER));
        }
    }

    private static List<Path> children(Path directory) {
        try (Stream<Path> entries = Files.list(directory)) {
            return entries.sorted().toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
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
            boolean template = TEMPLATE_COMPONENTS.contains(name);
            if (template && !repository.components().contains(name))
                findings.add(Finding.of(Rule.L08, directory, "模板组件由 settings.gradle.kts 包含"));
            else if (!template
                    && !repository.products().contains(name)
                    && !repository.tools().contains(name))
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
            if (!TEMPLATE_COMPONENTS.contains(name)
                    && repository.exists(settings)
                    && !COMPONENT_PLUGIN.matcher(repository.text(settings)).find())
                findings.add(
                        Finding.of(Rule.L08, settings, "应用 io.github.recrivenvi.component 设置插件"));
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

    private static void targetScripts(Repository repository, List<Finding> findings) {
        for (String file : repository.filesUnder("versions")) {
            if (!file.matches("versions/[^/]+/build\\.gradle\\.kts")) continue;
            String[] lines = withoutComments(repository.text(file).replace("\r", "")).split("\n");
            for (int i = 0; i < lines.length; i++) {
                String line = lines[i];
                Matcher literal = STRING.matcher(line);
                boolean version = PLUGIN_VERSION.matcher(literal.replaceAll("\"\"")).find();
                literal.reset();
                while (!version && literal.find())
                    version =
                            COORDINATE.matcher(literal.group()).matches()
                                    || !literal.group().contains("://")
                                            && VERSION_NUMBER.matcher(literal.group()).find();
                if (version)
                    findings.add(
                            new Finding(
                                    Rule.T02,
                                    file,
                                    i + 1,
                                    "版本号写在 target.properties 或 gradle/libs.versions.toml，"
                                            + "依赖用 target.module(...) 或 libs.<别名> 声明"));
            }
        }
    }

    // 去掉行注释与块注释，保留换行以便报告行号；字符串中的 // 与 /* 不是注释，例如网址。
    private static String withoutComments(String text) {
        StringBuilder code = new StringBuilder();
        boolean string = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (!string && text.startsWith("//", i)) {
                while (i + 1 < text.length() && text.charAt(i + 1) != '\n') i++;
            } else if (!string && text.startsWith("/*", i)) {
                int end = text.indexOf("*/", i + 2);
                int stop = end < 0 ? text.length() : end + 2;
                for (int j = i; j < stop; j++) if (text.charAt(j) == '\n') code.append('\n');
                i = stop - 1;
            } else {
                if (string && c == '\\' && i + 1 < text.length()) {
                    code.append(c);
                    c = text.charAt(++i);
                } else if (c == '"') string = !string;
                else if (c == '\n') string = false;
                code.append(c);
            }
        }
        return code.toString();
    }

    private static void metadata(Repository repository, List<Finding> findings) {
        for (String file : repository.files()) {
            if (!METADATA.matcher(file).matches()) continue;
            List<String> lines = repository.text(file).lines().toList();
            for (int i = 0; i < lines.size(); i++) {
                String line = lines.get(i);
                Matcher placeholder = PLACEHOLDER.matcher(line);
                boolean header =
                        file.endsWith(".toml") && LoaderChecks.TOML_HEADER.matcher(line).matches();
                while (placeholder.find())
                    if (!(header && placeholder.group(1).equals("mod_id"))
                            && !quoted(line, placeholder.start()))
                        findings.add(
                                new Finding(
                                        Rule.P01,
                                        file,
                                        i + 1,
                                        placeholder.group() + " 必须写在双引号字符串内"));
            }
        }
    }

    private static boolean quoted(String line, int index) {
        boolean inside = false;
        for (int i = 0; i < index; i++)
            if (line.charAt(i) == '"' && (i == 0 || line.charAt(i - 1) != '\\')) inside = !inside;
        return inside;
    }
}
