package io.github.recrivenvi.conventions.compliance;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// 第 5 节"Target"。
final class TargetChecks {
    // T-02 逐行检查去掉注释后的 Target 脚本：字符串中的版本号（网址除外）与带版本的依赖坐标，以及插件版本。
    private static final Pattern STRING = Pattern.compile("\"(?:[^\"\\\\]|\\\\.)*\"");
    private static final Pattern COORDINATE = Pattern.compile("\"[\\w.${}-]+:[\\w.${}-]+:.*");
    private static final Pattern VERSION_NUMBER = Pattern.compile("\\d+\\.\\d+");
    private static final Pattern PLUGIN_VERSION = Pattern.compile("\\)\\s*version\\b");
    // 只比较随模组发布的源码集；probe、test 等源码集中的代码不属于 product 组件。
    private static final Pattern SHARED_SOURCE =
            Pattern.compile(
                    "versions/[^/]+/src/(?:main|client)/java/(?:.+/)?(?!package-info|module-info)[^/]+\\.java");
    private static final Pattern GAME_REFERENCE =
            Pattern.compile(
                    "\\b(?:net\\.minecraft|com\\.mojang|net\\.neoforged|net\\.minecraftforge"
                            + "|net\\.fabricmc|cpw\\.mods|org\\.spongepowered)\\b");

    private TargetChecks() {}

    static void check(Repository repository, List<Finding> findings) {
        scripts(repository, findings);
        duplicates(repository, findings);
    }

    private static void scripts(Repository repository, List<Finding> findings) {
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
                                    + " 中内容相同，且没有直接引用 Minecraft 与加载器的包；"
                                    + "确认它不依赖游戏后移入 product 组件"));
        }
    }
}
