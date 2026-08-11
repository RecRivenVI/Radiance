package io.github.recrivenvi.conventions.compliance;

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
    // 只比较随模组发布的源码集；probe、test 等源码集中的代码不属于 product 组件。第 1 组为 Target，
    // 第 2 组为 java 或 templates，第 3 组为类名对应的路径。
    private static final Pattern SHARED_SOURCE =
            Pattern.compile(
                    "versions/([^/]+)/src/(?:main|client)/(java|templates)/((?:[^/]+/)*(?!package-info\\.|module-info\\.)[^/]+)\\.java");
    private static final Pattern IMPORT =
            Pattern.compile(
                    "^\\s*import\\s+(?:static\\s+)?([\\w.]+?)(\\.\\*)?\\s*;", Pattern.MULTILINE);
    private static final Pattern TYPE_NAME = Pattern.compile("\\b[A-Z]\\w*\\b");
    private static final Pattern COMMENT_OR_LITERAL =
            Pattern.compile(
                    "//[^\\n]*|/\\*.*?\\*/|'(?:[^'\\\\\\n]|\\\\.)*'|\"(?:[^\"\\\\\\n]|\\\\.)*\"",
                    Pattern.DOTALL);
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

    // 一个类在含有它的每个 Target 中内容都相同、自身不引用游戏与加载器的包，并且它引用的本项目其他类也
    // 满足同样的条件时，才能原样移入 product 组件；各 Target 内容不同的类与依赖它们的类留在 Target 中。
    // templates/ 中的类按 Target 展开，组件里没有它们，所以它们与依赖它们的类都不提示。
    private static void duplicates(Repository repository, List<Finding> findings) {
        Map<String, Map<String, String>> classes = new TreeMap<>();
        Set<String> templated = new TreeSet<>();
        Map<String, String> paths = new TreeMap<>();
        for (String file : repository.filesUnder("versions")) {
            Matcher matcher = SHARED_SOURCE.matcher(file);
            if (!matcher.matches()) continue;
            String name = matcher.group(3).replace('/', '.');
            if (matcher.group(2).equals("templates")) {
                templated.add(name);
                continue;
            }
            classes.computeIfAbsent(name, key -> new TreeMap<>())
                    .put(matcher.group(1), repository.text(file).replace("\r", ""));
            paths.merge(
                    name, file, (first, second) -> first.compareTo(second) <= 0 ? first : second);
        }
        Set<String> known = new TreeSet<>(classes.keySet());
        known.addAll(templated);
        Map<String, Set<String>> packages = new TreeMap<>();
        for (String name : known)
            packages.computeIfAbsent(packageOf(name), key -> new TreeSet<>()).add(name);
        Map<String, Set<String>> movable = new TreeMap<>();
        classes.forEach(
                (name, copies) -> {
                    Set<String> texts = new TreeSet<>(copies.values());
                    String text = texts.iterator().next();
                    if (copies.size() >= 2
                            && texts.size() == 1
                            && !templated.contains(name)
                            && !GAME_REFERENCE.matcher(text).find())
                        movable.put(name, references(name, text, known, packages));
                });
        boolean changed = true;
        while (changed)
            changed = movable.values().removeIf(used -> !movable.keySet().containsAll(used));
        for (String name : movable.keySet())
            findings.add(
                    Finding.of(
                            Rule.T07,
                            paths.get(name),
                            "在 "
                                    + String.join(", ", classes.get(name).keySet())
                                    + " 中内容相同，它与它引用的本项目其他类都不引用 Minecraft 与加载器的包；"
                                    + "确认它不依赖游戏后移入 product 组件"));
    }

    // 类引用的本项目其他类：导入（包括静态导入与通配导入）、同一个包中按简单名称使用的类，以及写出全名的类。
    private static Set<String> references(
            String name, String text, Set<String> classes, Map<String, Set<String>> packages) {
        String code = COMMENT_OR_LITERAL.matcher(text).replaceAll(" ");
        Set<String> used = new TreeSet<>();
        Matcher imports = IMPORT.matcher(code);
        while (imports.find()) {
            String imported = imports.group(1);
            if (imports.group(2) != null) {
                used.addAll(packages.getOrDefault(imported, Set.of()));
                continue;
            }
            while (!classes.contains(imported) && imported.contains("."))
                imported = packageOf(imported);
            if (classes.contains(imported)) used.add(imported);
        }
        Set<String> words = new TreeSet<>();
        TYPE_NAME.matcher(code).results().forEach(word -> words.add(word.group()));
        for (String other : packages.getOrDefault(packageOf(name), Set.of()))
            if (words.contains(other.substring(other.lastIndexOf('.') + 1))) used.add(other);
        for (String other : classes) if (mentions(code, other)) used.add(other);
        used.remove(name);
        return used;
    }

    private static boolean mentions(String code, String name) {
        for (int at = code.indexOf(name); at >= 0; at = code.indexOf(name, at + 1)) {
            int after = at + name.length();
            boolean start = at == 0 || !Character.isJavaIdentifierPart(code.charAt(at - 1));
            boolean end =
                    after == code.length() || !Character.isJavaIdentifierPart(code.charAt(after));
            if (start && end) return true;
        }
        return false;
    }

    private static String packageOf(String name) {
        int dot = name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(0, dot);
    }
}
