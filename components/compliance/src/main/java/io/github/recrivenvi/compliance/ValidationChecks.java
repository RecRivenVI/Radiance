package io.github.recrivenvi.compliance;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;
import org.tomlj.Toml;
import org.tomlj.TomlArray;
import org.tomlj.TomlParseError;
import org.tomlj.TomlParseResult;

final class ValidationChecks {
    private static final Pattern NAME = Pattern.compile("([a-z]+)-[a-z0-9]+(_[a-z0-9]+)*");
    private static final List<String> FILES = List.of("validation.md", "validation.toml");
    private static final Set<String> DIRECTORIES = Set.of("task", "instance", "result");
    private static final Set<String> CODE_EXTENSIONS =
            Set.of(
                    "bash", "bat", "c", "cc", "cjs", "class", "cmd", "cpp", "cs", "cts", "cxx",
                    "dll", "dylib", "exe", "fish", "go", "gradle", "groovy", "h", "hpp", "jar",
                    "java", "js", "jsx", "kt", "kts", "lua", "mjs", "mts", "php", "pl", "ps1",
                    "psd1", "psm1", "py", "rb", "rs", "scala", "sh", "so", "swift", "ts", "tsx",
                    "vbs", "wasm", "zsh");
    private static final Map<String, List<String>> SECTIONS =
            Map.of(
                    "zh", List.of("目标", "运行", "通过标准"),
                    "en", List.of("Goal", "Run", "Pass criteria"));

    private ValidationChecks() {}

    static void check(Repository repository, List<Finding> findings) {
        Set<String> validations = new LinkedHashSet<>();
        Set<String> reported = new LinkedHashSet<>();
        for (String file : repository.filesUnder("validations")) {
            String[] parts = file.split("/");
            if (parts.length == 2) {
                findings.add(Finding.of(Rule.V01, file, "validations/ 中只能有验证目录"));
                continue;
            }
            validations.add(parts[1]);
            boolean allowed =
                    parts.length == 3 ? FILES.contains(parts[2]) : DIRECTORIES.contains(parts[2]);
            String entry = "validations/" + parts[1] + "/" + parts[2];
            if (!allowed && reported.add(entry))
                findings.add(Finding.of(Rule.V03, entry, "不是验证目录允许的条目"));
            if (!parts[2].equals("instance") && executable(parts[parts.length - 1]))
                findings.add(Finding.of(Rule.V04, file, "可执行代码应放在组件或 probe 源码集中"));
        }
        for (String name : validations) {
            String directory = "validations/" + name;
            var matcher = NAME.matcher(name);
            if (!matcher.matches())
                findings.add(Finding.of(Rule.V01, directory, "应写作 <type>-<topic>"));
            else if (!repository.vocabulary().suggests("validations", matcher.group(1)))
                findings.add(
                        Finding.of(
                                Rule.V02,
                                directory,
                                "类型词 "
                                        + matcher.group(1)
                                        + " 不在建议词中；"
                                        + repository
                                                .vocabulary()
                                                .hint("validations", matcher.group(1))));
            for (String required : FILES)
                if (!repository.exists(directory + "/" + required))
                    findings.add(Finding.of(Rule.V03, directory, "缺少 " + required));
            if (repository.exists(directory + "/validation.toml"))
                configuration(repository, directory + "/validation.toml", findings);
            if (repository.exists(directory + "/validation.md"))
                description(repository, directory + "/validation.md", findings);
        }
    }

    private static void configuration(Repository repository, String file, List<Finding> findings) {
        TomlParseResult toml = Toml.parse(repository.text(file));
        if (toml.hasErrors()) {
            TomlParseError error = toml.errors().getFirst();
            findings.add(new Finding(Rule.V08, file, error.position().line(), error.getMessage()));
            return;
        }
        String tools =
                repository.tools().isEmpty()
                        ? "components {} 中没有登记 tool 组件"
                        : "已登记的 tool 组件：" + String.join(", ", new TreeSet<>(repository.tools()));
        Object tool = toml.get(List.of("tool"));
        if (tool == null)
            findings.add(
                    Finding.of(Rule.V08, file, "缺少 tool = \"<component>\"，写明执行这项验证的工具组件；" + tools));
        else if (!(tool instanceof String name) || !repository.tools().contains(name))
            findings.add(Finding.of(Rule.V08, file, "tool " + tool + " 不是已登记的 tool 组件；" + tools));
        Object targets = toml.get(List.of("targets"));
        if (targets == null)
            findings.add(Finding.of(Rule.V08, file, "缺少 targets = [...]，写明验证涉及的 Target，不涉及时写 []"));
        else if (!(targets instanceof TomlArray array))
            findings.add(Finding.of(Rule.V08, file, "targets 必须是 Target 名称数组"));
        else
            for (Object target : array.toList())
                if (!(target instanceof String name) || !repository.targets().contains(name))
                    findings.add(
                            Finding.of(Rule.V08, file, "targets 中的 " + target + " 不是已登记的 Target"));
    }

    private static void description(Repository repository, String file, List<Finding> findings) {
        Markdown markdown = new Markdown(repository.text(file));
        List<String> sections = SECTIONS.get(SpecificationChecks.language(repository));
        List<String> titles = markdown.sectionTitles();
        for (String section : sections)
            if (!titles.contains(section))
                findings.add(Finding.of(Rule.V09, file, "缺少小节 ## " + section));
        List<String> run = markdown.section(sections.get(1));
        if (run != null && run.stream().noneMatch(line -> line.stripLeading().startsWith("```")))
            findings.add(
                    Finding.of(Rule.V09, file, "\"## " + sections.get(1) + "\"一节应用代码块写出执行这项验证的命令"));
    }

    private static boolean executable(String name) {
        int dot = name.lastIndexOf('.');
        return dot >= 0
                && CODE_EXTENSIONS.contains(name.substring(dot + 1).toLowerCase(Locale.ROOT));
    }
}
