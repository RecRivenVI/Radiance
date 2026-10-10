package io.github.recrivenvi.compliance;

import java.math.BigInteger;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
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

final class DocumentChecks {
    private static final String TOPIC = "[a-z0-9]+(?:_[a-z0-9]+)*";
    private static final String NUMBER = "[1-9][0-9]*(?:\\.[1-9][0-9]*)*";
    private static final String LOCALE = "(?:-[a-z]{2,3}_[A-Z]{2})?";
    private static final Set<String> CATEGORIES =
            Set.of("usage", "development", "design", "project", "reference", "research", "release");
    private static final Set<String> PLAYER_CATEGORIES = Set.of("usage", "release");
    private static final Pattern DOCUMENT =
            Pattern.compile("([a-z]+)-(" + TOPIC + ")" + LOCALE + "\\.md");
    private static final Pattern NUMBERED =
            Pattern.compile("(adr|phase)-(" + NUMBER + ")-(" + TOPIC + ")" + LOCALE + "\\.md");
    private static final Pattern STEM = Pattern.compile("(.+?)" + LOCALE + "\\.md");
    private static final Pattern IMAGE =
            Pattern.compile("(.+)-([1-9][0-9]*)\\.(png|jpg|jpeg|gif|webp|svg)");
    private static final Pattern FIGURE_LABEL = Pattern.compile("^\\S+ ([0-9]+)(?:\\D.*)?$");
    private static final Pattern README = Pattern.compile("README(?:-[a-z]{2,3}_[A-Z]{2})?\\.md");
    private static final Pattern REPOSITORY_DETAIL =
            Pattern.compile("gradlew|\\.gradle\\.kts|src/main|versions/|components/|build/libs");
    private static final Map<String, Map<String, List<String>>> STRUCTURES =
            Map.of(
                    "zh",
                    Map.of(
                            "procedure", List.of("适用范围", "步骤", "验收"),
                            "plan", List.of("目标", "范围", "步骤", "结果"),
                            "phase", List.of("目标", "范围", "步骤", "结果"),
                            "adr", List.of("状态", "背景", "决策", "后果"),
                            "research", List.of("问题", "方法", "发现", "结论")),
                    "en",
                    Map.of(
                            "procedure", List.of("Scope", "Steps", "Acceptance"),
                            "plan", List.of("Goal", "Scope", "Steps", "Result"),
                            "phase", List.of("Goal", "Scope", "Steps", "Result"),
                            "adr", List.of("Status", "Context", "Decision", "Consequences"),
                            "research", List.of("Question", "Method", "Findings", "Conclusion")));
    private static final Map<String, Pattern> STATUS =
            Map.of(
                    "zh",
                    Pattern.compile("(提议中|已接受|已弃用|已被 adr-(" + NUMBER + ") 取代)"),
                    "en",
                    Pattern.compile(
                            "(Proposed|Accepted|Deprecated|Superseded by adr-(" + NUMBER + "))"));
    private static final Pattern DATE =
            Pattern.compile("(?<![0-9])([0-9]{4}-[0-9]{2}-[0-9]{2})(?![0-9])");
    private static final Pattern TRANSLATION = Pattern.compile("(.+)-([a-z]{2,3}_[A-Z]{2})\\.md");
    private static final Pattern RELEASE =
            Pattern.compile("\\[([^\\]]+)] - ([0-9]{4}-[0-9]{2}-[0-9]{2})");
    // 语义化版本 2.0.0 的完整语法：数字标识没有前导零，预发布与构建元数据的标识不为空。
    private static final String IDENTIFIER = "(?:0|[1-9][0-9]*|[0-9]*[A-Za-z-][0-9A-Za-z-]*)";
    private static final Pattern SEMVER =
            Pattern.compile(
                    "(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)"
                            + "(?:-("
                            + IDENTIFIER
                            + "(?:\\."
                            + IDENTIFIER
                            + ")*))?(?:\\+[0-9A-Za-z-]+(?:\\.[0-9A-Za-z-]+)*)?");
    private static final Set<String> UNRELEASED = Set.of("[未发布]", "[Unreleased]");
    private static final Set<String> CHANGE_TYPES =
            Set.of(
                    "新增",
                    "变更",
                    "弃用",
                    "移除",
                    "修复",
                    "安全",
                    "Added",
                    "Changed",
                    "Deprecated",
                    "Removed",
                    "Fixed",
                    "Security");

    private DocumentChecks() {}

    static void check(Repository repository, List<Finding> findings) {
        Set<String> reported = new LinkedHashSet<>();
        Map<String, Map<String, String>> records = new TreeMap<>();
        for (String file : repository.filesUnder("documents")) {
            String[] parts = file.split("/");
            if (file.equals(SpecificationChecks.PROJECT)) continue;
            if (parts.length == 2 || !CATEGORIES.contains(parts[1])) {
                String entry = parts.length == 2 ? file : "documents/" + parts[1];
                if (reported.add(entry))
                    findings.add(
                            Finding.of(
                                    Rule.D01,
                                    entry,
                                    "应为 usage、development、design、project、reference、research、release 之一，或 AGENTS.md"));
                continue;
            }
            String category = parts[1];
            if (parts.length == 3)
                document(repository, file, category, parts[2], records, findings);
            else if (parts.length == 4 && parts[2].equals("images"))
                image(repository, file, category, parts[3], findings);
            else if (reported.add(file))
                findings.add(Finding.of(Rule.D01, file, "文档直接放在分类目录中，只有 images/ 可以作为子目录"));
        }
        records.forEach((type, numbers) -> numbering(type, numbers, findings));
        for (String file : repository.files())
            if (file.endsWith(".md") && !file.equals("CLAUDE.md") && !Repository.result(file))
                markdown(repository, file, findings);
        readmes(repository, findings);
    }

    private static void document(
            Repository repository,
            String file,
            String category,
            String name,
            Map<String, Map<String, String>> records,
            List<Finding> findings) {
        String type;
        Matcher numbered = NUMBERED.matcher(name);
        if (numbered.matches()) {
            type = numbered.group(1);
            String home = type.equals("adr") ? "design" : "project";
            if (!category.equals(home))
                findings.add(Finding.of(Rule.D02, file, type + " 记录应放在 documents/" + home));
            String topic =
                    records.computeIfAbsent(type, key -> new TreeMap<>())
                            .putIfAbsent(numbered.group(2), numbered.group(3));
            if (topic != null && !topic.equals(numbered.group(3)))
                findings.add(
                        Finding.of(Rule.D02, file, type + " 编号 " + numbered.group(2) + " 已被使用"));
        } else {
            Matcher matcher = DOCUMENT.matcher(name);
            if (!matcher.matches()) {
                findings.add(Finding.of(Rule.D02, file, "应写作 <type>-<topic>.md：字段之间用 -，组合词用 _"));
                return;
            }
            type = matcher.group(1);
            if (type.equals("adr") || type.equals("phase")) {
                findings.add(
                        Finding.of(
                                Rule.D02,
                                file,
                                type + " 记录命名为 " + type + "-<number>-<topic>.md，编号从 1 开始、不带前导零"));
                return;
            }
            String location = "documents/" + category;
            if (!repository.vocabulary().suggests(location, type))
                findings.add(
                        Finding.of(
                                Rule.D03,
                                file,
                                "类型词 "
                                        + type
                                        + " 不在 "
                                        + location
                                        + " 的建议词中；"
                                        + repository.vocabulary().hint(location, type)));
        }
        Markdown markdown = new Markdown(repository.text(file));
        String language = language(repository, name);
        List<String> required =
                language == null
                        ? null
                        : STRUCTURES
                                .get(language)
                                .get(category.equals("research") ? category : type);
        if (required != null) {
            List<String> titles = markdown.sectionTitles();
            for (String section : required)
                if (!titles.contains(section))
                    findings.add(Finding.of(Rule.D06, file, "缺少小节 ## " + section));
        }
        if (type.equals("adr") && language != null)
            status(repository, file, markdown, language, findings);
        if (category.equals("research") && !dated(String.join("\n", markdown.firstParagraph())))
            findings.add(Finding.of(Rule.D16, file, "第一段应写出调查日期，格式为 YYYY-MM-DD"));
        if (type.equals("changelog")) changelog(file, markdown, findings);
        translation(repository, file, category, name, markdown, findings);
        if (PLAYER_CATEGORIES.contains(category)
                && !namesVersion(repository, String.join("\n", markdown.firstParagraph())))
            findings.add(Finding.of(Rule.D15, file, "第一段应写出至少一个已登记 Target 的 Minecraft 版本号"));
        if (PLAYER_CATEGORIES.contains(category)) {
            List<String> lines = markdown.lines();
            for (int i = 0; i < lines.size(); i++) {
                Matcher detail = REPOSITORY_DETAIL.matcher(lines.get(i));
                if (detail.find()) {
                    findings.add(
                            new Finding(Rule.D07, file, i + 1, "玩家写法的文档出现了 " + detail.group()));
                    break;
                }
            }
        }
        figures(file, name, markdown, findings);
    }

    private static String language(Repository repository, String name) {
        Matcher translation = TRANSLATION.matcher(name);
        if (!translation.matches()) return SpecificationChecks.language(repository);
        String locale = translation.group(2);
        return locale.startsWith("zh_") ? "zh" : locale.startsWith("en_") ? "en" : null;
    }

    private static void status(
            Repository repository,
            String file,
            Markdown markdown,
            String language,
            List<Finding> findings) {
        String title = STRUCTURES.get(language).get("adr").getFirst();
        List<String> body = markdown.section(title);
        if (body == null) return;
        String value =
                body.stream()
                        .map(String::strip)
                        .filter(line -> !line.isEmpty())
                        .findFirst()
                        .orElse("");
        Matcher status = STATUS.get(language).matcher(value);
        if (!status.matches()) {
            findings.add(
                    Finding.of(
                            Rule.D06,
                            file,
                            language.equals("zh")
                                    ? "状态应为\"提议中\"\"已接受\"\"已弃用\"或\"已被 adr-<number> 取代\""
                                    : "状态应为 Proposed、Accepted、Deprecated 或 Superseded by adr-<number>"));
            return;
        }
        if (status.group(2) == null) return;
        String prefix = "adr-" + status.group(2) + "-";
        boolean exists = false;
        for (String other : repository.filesUnder("documents/design"))
            if (other.substring(other.lastIndexOf('/') + 1).startsWith(prefix)) exists = true;
        if (!exists) findings.add(Finding.of(Rule.D06, file, "adr-" + status.group(2) + " 不存在"));
    }

    private static void translation(
            Repository repository,
            String file,
            String category,
            String name,
            Markdown markdown,
            List<Finding> findings) {
        Matcher translation = TRANSLATION.matcher(name);
        if (!translation.matches()) return;
        String original = "documents/" + category + "/" + translation.group(1) + ".md";
        if (!repository.exists(original)) {
            findings.add(Finding.of(Rule.D13, file, "缺少原文 " + original));
            return;
        }
        int sections = markdown.sectionTitles().size();
        int expected = new Markdown(repository.text(original)).sectionTitles().size();
        if (sections != expected)
            findings.add(
                    Finding.of(Rule.D13, file, "有 " + sections + " 个二级标题，原文有 " + expected + " 个"));
    }

    private static void changelog(String file, Markdown markdown, List<Finding> findings) {
        List<String> lines = markdown.lines();
        String previous = null;
        boolean first = true;
        for (int index : markdown.headings(2)) {
            String title = lines.get(index).substring(3).strip();
            if (UNRELEASED.contains(title)) {
                if (!first) findings.add(new Finding(Rule.D14, file, index + 1, "未发布小节应在最前"));
            } else {
                Matcher release = RELEASE.matcher(title);
                if (!release.matches())
                    findings.add(
                            new Finding(
                                    Rule.D14,
                                    file,
                                    index + 1,
                                    "标题应为 ## [<版本>] - <YYYY-MM-DD> 或 ## [未发布]"));
                else if (!SEMVER.matcher(release.group(1)).matches())
                    findings.add(
                            new Finding(
                                    Rule.D14,
                                    file,
                                    index + 1,
                                    release.group(1) + " 不符合语义化版本 2.0.0"));
                else {
                    try {
                        LocalDate.parse(release.group(2));
                    } catch (DateTimeParseException e) {
                        findings.add(
                                new Finding(
                                        Rule.D14, file, index + 1, release.group(2) + " 不是有效日期"));
                    }
                    if (previous != null && compare(release.group(1), previous) >= 0)
                        findings.add(new Finding(Rule.D14, file, index + 1, "版本应从新到旧排列"));
                    previous = release.group(1);
                }
            }
            first = false;
        }
        for (int index : markdown.headings(3))
            if (!CHANGE_TYPES.contains(lines.get(index).substring(4).strip()))
                findings.add(new Finding(Rule.D14, file, index + 1, "改动类型只能是新增、变更、弃用、移除、修复或安全"));
    }

    // 按语义化版本的优先级比较，构建元数据不参与比较；数字标识不限长度。
    static int compare(String left, String right) {
        Matcher a = SEMVER.matcher(left);
        Matcher b = SEMVER.matcher(right);
        if (!a.matches() || !b.matches())
            throw new IllegalArgumentException(left + " 或 " + right + " 不是语义化版本");
        for (int i = 1; i <= 3; i++) {
            int order = new BigInteger(a.group(i)).compareTo(new BigInteger(b.group(i)));
            if (order != 0) return order;
        }
        if (a.group(4) == null || b.group(4) == null)
            return Boolean.compare(a.group(4) == null, b.group(4) == null);
        String[] preA = a.group(4).split("\\.");
        String[] preB = b.group(4).split("\\.");
        for (int i = 0; i < Math.min(preA.length, preB.length); i++) {
            boolean numericA = preA[i].matches("[0-9]+");
            boolean numericB = preB[i].matches("[0-9]+");
            int order =
                    numericA && numericB
                            ? new BigInteger(preA[i]).compareTo(new BigInteger(preB[i]))
                            : numericA != numericB
                                    ? (numericA ? -1 : 1)
                                    : preA[i].compareTo(preB[i]);
            if (order != 0) return order;
        }
        return Integer.compare(preA.length, preB.length);
    }

    private static boolean dated(String text) {
        Matcher date = DATE.matcher(text);
        while (date.find()) {
            try {
                LocalDate.parse(date.group(1));
                return true;
            } catch (DateTimeParseException e) {
                continue;
            }
        }
        return false;
    }

    private static boolean namesVersion(Repository repository, String text) {
        for (String target : repository.targets()) {
            int separator = target.lastIndexOf('-');
            if (separator < 0) continue;
            String version = Pattern.quote(target.substring(0, separator));
            if (Pattern.compile("(?<![0-9.])" + version + "(?!\\.?[0-9])").matcher(text).find())
                return true;
        }
        return false;
    }

    private static void numbering(
            String type, Map<String, String> numbers, List<Finding> findings) {
        Map<String, Set<Integer>> levels = new TreeMap<>();
        for (String number : numbers.keySet()) {
            String[] parts = number.split("\\.");
            for (int i = 0; i < parts.length; i++) {
                String parent = String.join(".", List.of(parts).subList(0, i));
                levels.computeIfAbsent(parent, key -> new TreeSet<>())
                        .add(Integer.parseInt(parts[i]));
            }
        }
        levels.forEach(
                (parent, children) -> {
                    int expected = 1;
                    for (int child : children) {
                        if (child != expected) {
                            String missing =
                                    parent.isEmpty() ? "" + expected : parent + "." + expected;
                            findings.add(
                                    Finding.of(
                                            Rule.D02,
                                            "documents/"
                                                    + (type.equals("adr") ? "design" : "project"),
                                            type + " 编号缺少 " + missing));
                            return;
                        }
                        expected++;
                    }
                });
    }

    private static void figures(
            String file, String name, Markdown markdown, List<Finding> findings) {
        Matcher stem = STEM.matcher(name);
        if (!stem.matches()) return;
        String prefix = "images/" + stem.group(1) + "-";
        List<Integer> order = new ArrayList<>();
        for (Markdown.Image image : markdown.images()) {
            if (!image.target().startsWith(prefix)) continue;
            Matcher figure = IMAGE.matcher(image.target().substring("images/".length()));
            if (!figure.matches()) continue;
            int number = Integer.parseInt(figure.group(2));
            Matcher label = FIGURE_LABEL.matcher(image.alt());
            if (!label.matches() || Integer.parseInt(label.group(1)) != number)
                findings.add(
                        new Finding(
                                Rule.D08,
                                file,
                                image.line(),
                                "图 " + number + " 的替代文本应以图号标签与编号开头，例如\"图 " + number + "\""));
            if (!order.contains(number)) order.add(number);
        }
        if (!order.equals(order.stream().sorted().toList()))
            findings.add(Finding.of(Rule.D08, file, "图片首次出现的顺序为 " + order + "，应按编号顺序引用"));
    }

    private static void image(
            Repository repository,
            String file,
            String category,
            String name,
            List<Finding> findings) {
        Matcher matcher = IMAGE.matcher(name);
        if (!matcher.matches()) {
            findings.add(
                    Finding.of(
                            Rule.D08,
                            file,
                            "图片命名为 <所属文档名>-<编号>.<png|jpg|jpeg|gif|webp|svg>，编号从 1 开始"));
            return;
        }
        String owner = "documents/" + category + "/" + matcher.group(1) + ".md";
        if (!repository.exists(owner)) {
            findings.add(Finding.of(Rule.D08, file, "不存在拥有这张图片的文档 " + owner));
            return;
        }
        int number = Integer.parseInt(matcher.group(2));
        if (number > 1) {
            boolean previous = false;
            for (String sibling : repository.filesUnder("documents/" + category + "/images")) {
                Matcher other = IMAGE.matcher(sibling.substring(sibling.lastIndexOf('/') + 1));
                if (other.matches()
                        && other.group(1).equals(matcher.group(1))
                        && Integer.parseInt(other.group(2)) == number - 1) previous = true;
            }
            if (!previous) findings.add(Finding.of(Rule.D08, file, "缺少图 " + (number - 1)));
        }
        boolean referenced = false;
        for (Markdown.Image image : new Markdown(repository.text(owner)).images())
            if (image.target().equals("images/" + name)) referenced = true;
        if (!referenced) findings.add(Finding.of(Rule.D08, file, owner + " 没有引用这张图片"));
    }

    private static void markdown(Repository repository, String file, List<Finding> findings) {
        Markdown markdown = new Markdown(repository.text(file));
        Path directory = repository.root().resolve(file).getParent();
        for (Markdown.Link link : markdown.links()) {
            String target = link.target();
            if (target.startsWith("#") || target.matches("[a-zA-Z][a-zA-Z0-9+.-]*:.*")) continue;
            int anchor = target.indexOf('#');
            String path = anchor < 0 ? target : target.substring(0, anchor);
            if (path.isEmpty()) continue;
            Path resolved = directory.resolve(path).normalize();
            if (!resolved.startsWith(repository.root()) || !resolved.toFile().exists())
                findings.add(new Finding(Rule.D05, file, link.line(), "链接无效：" + target));
        }
    }

    private static void readmes(Repository repository, List<Finding> findings) {
        List<String> readmes = new ArrayList<>();
        for (String file : repository.files())
            if (!file.contains("/") && README.matcher(file).matches()) readmes.add(file);
        for (String file : readmes) {
            Markdown markdown = new Markdown(repository.text(file));
            Set<String> linked = new TreeSet<>();
            for (Markdown.Link link :
                    new Markdown(String.join("\n", markdown.firstParagraph())).links())
                linked.add(link.target());
            for (String other : readmes)
                if (!other.equals(file) && !linked.contains(other))
                    findings.add(Finding.of(Rule.D12, file, "标题后的第一段必须链接到 " + other));
            targets(repository, file, markdown, findings);
        }
    }

    private static void targets(
            Repository repository, String file, Markdown markdown, List<Finding> findings) {
        Markdown.Table table = null;
        for (Markdown.Table candidate : markdown.tables())
            if (candidate.header().size() >= 3 && candidate.header().getFirst().equals("Target"))
                table = candidate;
        if (table == null) {
            findings.add(Finding.of(Rule.D12, file, "缺少 Target 表"));
            return;
        }
        Map<String, List<String>> rows = new LinkedHashMap<>();
        for (List<String> row : table.rows()) {
            List<String> names = Markdown.backticked(row.getFirst());
            if (names.size() == 1 && row.size() >= 3) rows.put(names.getFirst(), row);
        }
        for (String target : new TreeSet<>(repository.targets())) {
            List<String> row = rows.remove(target);
            if (row == null) {
                findings.add(new Finding(Rule.D12, file, table.line(), target + " 没有列出"));
                continue;
            }
            Properties facts = repository.properties("versions/" + target + "/target.properties");
            String loader = facts.getProperty("loader_version", "").strip();
            String java = facts.getProperty("java_version", "").strip();
            // 加载器列写作"<加载器名称> <版本>"或只写版本，最后一项必须与 loader_version 完全相同。
            String[] words = row.get(1).replace("`", "").strip().split("\\s+");
            if (!loader.isEmpty() && !words[words.length - 1].equals(loader))
                findings.add(
                        new Finding(Rule.D12, file, table.line(), target + " 的加载器版本应为 " + loader));
            if (!java.isEmpty() && !row.get(2).strip().equals(java))
                findings.add(
                        new Finding(Rule.D12, file, table.line(), target + " 的 Java 版本应为 " + java));
        }
        for (String extra : rows.keySet())
            findings.add(new Finding(Rule.D12, file, table.line(), extra + " 已列出但没有登记"));
    }
}
