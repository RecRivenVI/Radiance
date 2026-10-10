package io.github.recrivenvi.compliance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ComplianceTest {
    private static final String NEOFORGE = "26.2-neoforge";
    private static final String FABRIC = "26.2-fabric";
    private static final String FORGE = "1.20.1-forge";

    @TempDir Path root;

    private final Map<String, String> files = new LinkedHashMap<>();
    private String target = NEOFORGE;
    private Map<String, String> locked;
    private Set<String> components = Set.of();
    private Set<String> products = Set.of();
    private Set<String> tools = Set.of();
    private Set<String> instances = Repository.BUILT_IN_INSTANCES;
    private Set<String> sourceSets = Set.of();
    private Set<String> targets;

    private static String agents(String version, Map<String, List<String>> vocabulary) {
        StringBuilder text = new StringBuilder("# 仓库规范\n\n规范版本：" + version + "\n\n");
        for (Rule rule : Rule.values())
            text.append("- **")
                    .append(rule.id())
                    .append("** [")
                    .append(rule.level() == Rule.Level.FAIL ? "检查" : "提示")
                    .append("] ")
                    .append(rule.title())
                    .append("\n");
        text.append("\n| 位置 | 默认写法 | 建议类型词 |\n| --- | --- | --- |\n");
        vocabulary.forEach(
                (location, words) ->
                        text.append("| `")
                                .append(location)
                                .append("` | 开发者 | ")
                                .append(
                                        words.stream()
                                                .map(word -> "`" + word + "`")
                                                .collect(Collectors.joining("、")))
                                .append(" |\n"));
        return text.toString();
    }

    private String project(String loaderVersion) {
        return "# 项目规范\n\n## 项目信息\n\n| 项 | 值 |\n| --- | --- |\n"
                + "| 产品显示名 | Example Mod |\n| 模组 ID | `example` |\n"
                + "| Java 包 | `io.github.example` |\n| 入口类 | `Example` |\n"
                + "| 业务 | 输出日志 |\n| 文档主语言 | 简体中文 |\n| 首页语言 | 英文 |\n"
                + "| 参考 Target | `"
                + target
                + "` |\n\n## Target\n\n| Target | 加载器版本 | Java |\n| --- | --- | --- |\n| `"
                + target
                + "` | "
                + loaderVersion
                + " | 25 |\n";
    }

    private String readme(String title, String links) {
        return "# "
                + title
                + "\n\n"
                + links
                + "\n\n| Target | Loader | Java |\n| --- | --- | --- |\n| `"
                + target
                + "` | Loader 1.0 | 25 |\n\nSee [installation](documents/usage/installation-basics.md).\n";
    }

    private void compliant() {
        String sources = "versions/" + target + "/src/main/";
        files.put("AGENTS.md", agents(SpecificationChecks.VERSION, Vocabulary.BUILT_IN));
        files.put("CLAUDE.md", "@AGENTS.md\n@documents/AGENTS.md\n");
        files.put("documents/AGENTS.md", project("1.0"));
        files.put("README.md", readme("Example", "English | [简体中文](README-zh_CN.md)"));
        files.put("README-zh_CN.md", readme("示例", "[English](README.md) | 简体中文"));
        files.put("instances.toml", "[all]\nmemory = \"4G\"\n");
        files.put(
                ".gitignore",
                "/local.toml\n.gradle/\nbuild/\n/instances/**\n!/instances/**/\n/validations/*/instance/\n");
        files.put(
                "gradle.properties",
                "mod_id=example\nmod_name=Example Mod\nmod_group=io.github.example\n");
        files.put("gradle/wrapper/gradle-wrapper.properties", "distributionSha256Sum=abc\n");
        files.put("gradlew.bat", "@echo off\r\nexit /b 0\r\n");
        files.put(
                "versions/" + target + "/target.properties",
                "loader_version=1.0\njava_version=25\n");
        files.put(sources + "java/io/github/example/Example.java", "package io.github.example;\n");
        files.put(
                "versions/" + target + "/src/probe/java/io/github/example/probe/ExampleProbe.java",
                "package io.github.example.probe;\n");
        files.put(sources + "resources/assets/example/lang/en_us.json", "{}\n");
        if (target.endsWith("fabric")) {
            files.put(
                    "versions/" + target + "/build.gradle.kts",
                    "plugins {\n    id(\"net.fabricmc.fabric-loom\")\n}\n");
            files.put(
                    sources + "resources/fabric.mod.json",
                    """
                    {
                        "schemaVersion": 1,
                        "id": "${mod_id}",
                        "version": "${mod_version}",
                        "name": "${mod_name}",
                        "description": "${mod_description}",
                        "authors": ["${mod_authors}"],
                        "license": "${mod_license}",
                        "environment": "${fabric_environment}",
                        "entrypoints": {
                            "main": ["${mod_group}.Example"]
                        },
                        "depends": {
                            "fabricloader": ">=${loader_version}",
                            "minecraft": "${minecraft_version}",
                            "java": ">=${java_version}"
                        }
                    }
                    """);
        } else {
            boolean forge = target.endsWith("-forge");
            files.put(
                    "versions/" + target + "/build.gradle.kts",
                    "plugins {\n    id(\"net.neoforged.moddev"
                            + (forge ? ".legacyforge" : "")
                            + "\")\n}\n");
            files.put(
                    sources + "resources/META-INF/" + (forge ? "mods.toml" : "neoforge.mods.toml"),
                    """
                    license = "${mod_license}"

                    [[mods]]
                    modId = "${mod_id}"
                    version = "${mod_version}"
                    displayName = "${mod_name}"
                    authors = "${mod_authors}"
                    description = "${mod_description}"
                    """
                            + (forge ? "displayTest = \"${display_test}\"\n" : "")
                            + """

                    [[dependencies.${mod_id}]]
                    modId = "LOADER"
                    versionRange = "[${loader_version},)"

                    [[dependencies.${mod_id}]]
                    modId = "minecraft"
                    versionRange = "[${minecraft_version}]"
                    """
                                    .replace("LOADER", forge ? "forge" : "neoforge"));
        }
        files.put(
                "documents/usage/installation-basics.md",
                "# 安装\n\n适用于 Minecraft "
                        + minecraft()
                        + "。把文件放入 `mods` 文件夹。\n\n![图 1：mods 文件夹](images/installation-basics-1.png)\n");
        files.put(
                "documents/design/adr-1-build.md",
                "# 构建\n\n## 状态\n\n已接受\n\n## 背景\n\n文本。\n\n## 决策\n\n文本。\n\n## 后果\n\n文本。\n");
        files.put(
                "documents/development/procedure-add_target.md",
                "# 新增 Target\n\n## 适用范围\n\n文本。\n\n## 步骤\n\n1. 文本。\n\n## 验收\n\n文本。\n");
    }

    private void write() throws IOException {
        for (Map.Entry<String, String> file : files.entrySet()) {
            Path path = root.resolve(file.getKey());
            Files.createDirectories(path.getParent());
            Files.writeString(path, file.getValue());
        }
        Path image = root.resolve("documents/usage/images/installation-basics-1.png");
        Files.createDirectories(image.getParent());
        Files.write(image, new byte[] {(byte) 0x89, 'P', 'N', 'G', 0, 0});
    }

    private String minecraft() {
        return target.substring(0, target.lastIndexOf('-'));
    }

    private Map<String, String> hashes() {
        Map<String, String> hashes = new LinkedHashMap<>();
        for (String file : List.of("AGENTS.md", "CLAUDE.md"))
            if (files.containsKey(file))
                hashes.put(
                        file,
                        SpecificationChecks.sha256(
                                files.get(file).getBytes(StandardCharsets.UTF_8)));
        return hashes;
    }

    private List<Finding> run(Map<String, List<String>> additions) throws IOException {
        write();
        Repository repository =
                Repository.scan(
                        root,
                        targets != null ? targets : Set.of(target),
                        components,
                        products,
                        tools,
                        instances,
                        sourceSets,
                        new Vocabulary(additions));
        return Compliance.run(repository, locked != null ? locked : hashes());
    }

    private Set<String> rules() throws IOException {
        return run(Map.of()).stream()
                .map(finding -> finding.rule().id())
                .collect(Collectors.toCollection(TreeSet::new));
    }

    @Test
    void compliantRepositoryHasNoFindings() throws IOException {
        compliant();
        assertEquals(List.of(), run(Map.of()));
    }

    @Test
    void compliantFabricRepositoryHasNoFindings() throws IOException {
        target = FABRIC;
        compliant();
        assertEquals(List.of(), run(Map.of()));
    }

    @Test
    void compliantForgeRepositoryHasNoFindings() throws IOException {
        target = FORGE;
        compliant();
        assertEquals(List.of(), run(Map.of()));
    }

    @Test
    void neoForgeMetadataHasNoDisplayTest() throws IOException {
        compliant();
        String metadata = "versions/" + target + "/src/main/resources/META-INF/neoforge.mods.toml";
        files.put(
                metadata,
                files.get(metadata)
                        .replace(
                                "description = \"${mod_description}\"\n",
                                "description = \"${mod_description}\"\ndisplayTest = \"${display_test}\"\n"));
        assertEquals(List.of(Rule.P04), run(Map.of()).stream().map(Finding::rule).toList());
    }

    @Test
    void forgeMetadataNeedsDisplayTest() throws IOException {
        target = FORGE;
        compliant();
        String metadata = "versions/" + target + "/src/main/resources/META-INF/mods.toml";
        files.put(metadata, files.get(metadata).replace("displayTest = \"${display_test}\"\n", ""));
        assertEquals(List.of(Rule.P04), run(Map.of()).stream().map(Finding::rule).toList());
    }

    @Test
    void specificationDriftIsReported() throws IOException {
        compliant();
        locked = hashes();
        Map<String, List<String>> vocabulary = new LinkedHashMap<>(Vocabulary.BUILT_IN);
        vocabulary.put("documents/usage", List.of("installation"));
        String text =
                agents("0.9", vocabulary).replace("- **C-06** [检查]", "- **C-06** [判断]")
                        + "- **X-01** [检查] Unknown rule\n";
        files.put("AGENTS.md", text);
        files.put("CLAUDE.md", "Read AGENTS.md\n");
        files.put("documents/AGENTS.md", project("2.0"));
        assertEquals(Set.of("S-01", "S-02", "S-03", "S-04", "S-05", "S-06"), rules());
    }

    @Test
    void projectSpecificationMustMatchProjectFacts() throws IOException {
        compliant();
        files.put(
                "documents/AGENTS.md",
                project("1.0")
                        .replace("`example`", "`other`")
                        .replace(
                                "| 参考 Target | `" + NEOFORGE + "` |",
                                "| 参考 Target | `1.0-forge` |"));
        List<Finding> findings = run(Map.of());
        assertEquals(2, findings.size());
        assertTrue(findings.stream().allMatch(finding -> finding.rule() == Rule.S05));
    }

    @Test
    void layoutViolationsAreReported() throws IOException {
        compliant();
        files.put("notes.txt", "notes\n");
        files.put("README-zh.md", "# 示例\n");
        files.put("versions/1.19-forge/build.gradle.kts", "plugins {}\n");
        files.put("versions/" + target + "/run/options.txt", "x\n");
        files.put("versions/" + target + "/src/gametest/java/A.java", "class A {}\n");
        files.put(
                "versions/" + target + "/src/main/java/org/example/Stray.java", "class Stray {}\n");
        files.put("components/utils/build.gradle.kts", "plugins {}\n");
        files.put(".gitignore", "build/\n");
        files.put(
                "versions/" + target + "/build.gradle.kts",
                "plugins {\n    id(\"net.neoforged.moddev\") version \"2.0\"\n}\n");
        String metadata = "versions/" + target + "/src/main/resources/META-INF/neoforge.mods.toml";
        files.put(metadata, files.get(metadata) + "credits = '''${mod_authors}'''\n");
        assertEquals(
                Set.of("L-01", "L-02", "L-03", "L-04", "L-05", "L-07", "L-08", "T-02", "P-01"),
                rules());
    }

    @Test
    void instancesAreLimitedToRegisteredTargets() throws IOException {
        compliant();
        files.put("instances/" + target + "/client/options.txt", "generated\r\n");
        files.put("instances/" + target + "/debug/options.txt", "x\n");
        files.put("instances/1.0-forge/client/options.txt", "x\n");
        files.put("instances/notes.txt", "x\n");
        List<Finding> findings = run(Map.of());
        assertEquals(
                List.of(
                        "I-04 instances/1.0-forge",
                        "I-04 instances/" + target + "/debug",
                        "I-04 instances/notes.txt"),
                findings.stream()
                        .map(finding -> finding.rule().id() + " " + finding.path())
                        .sorted()
                        .toList());
        Map<String, String> messages = new TreeMap<>();
        for (Finding finding : findings) messages.put(finding.path(), finding.message());
        assertTrue(
                messages.get("instances/1.0-forge").startsWith("不是已登记的 Target；")
                        && messages.get("instances/1.0-forge").contains("经使用者同意后移出仓库或删除"),
                messages.toString());
        assertTrue(
                messages.get("instances/" + target + "/debug").startsWith("不是已登记的实例（client、"),
                messages.toString());
        assertEquals("这里只能有已登记 Target 的实例目录", messages.get("instances/notes.txt"));
    }

    @Test
    void loaderMetadataIsChecked() throws IOException {
        compliant();
        String resources = "versions/" + target + "/src/main/resources/";
        files.put(resources + "fabric.mod.json", "{}\n");
        files.put(resources + "assets/example/textures/Block.png", "x\n");
        String metadata = resources + "META-INF/neoforge.mods.toml";
        files.put(
                metadata,
                files.get(metadata)
                        .replace("\"${mod_version}\"", "\"1.0\"")
                        .replace("[${minecraft_version}]", "[26.2]"));
        List<Finding> findings = run(Map.of());
        assertEquals(Set.of("P-03", "P-04", "P-05"), ids(findings));
        assertEquals(2, findings.stream().filter(finding -> finding.rule() == Rule.P04).count());
    }

    @Test
    void fabricMetadataIsChecked() throws IOException {
        target = FABRIC;
        compliant();
        String metadata = "versions/" + target + "/src/main/resources/fabric.mod.json";
        files.put(
                metadata,
                files.get(metadata)
                        .replace("\"${mod_group}.Example\"", "\"io.github.example.Example\"")
                        .replace("\"java\": \">=${java_version}\"", "\"java\": \">=25\"")
                        .replace("[\"${mod_authors}\"]", "[\"Someone\"]"));
        List<Finding> findings = run(Map.of());
        assertEquals(Set.of("P-04"), ids(findings));
        assertEquals(3, findings.size());
    }

    @Test
    void documentViolationsAreReported() throws IOException {
        compliant();
        files.put("documents/guides/setup-local.md", "# x\n");
        files.put("documents/usage/Installation.md", "# x\n");
        files.put("documents/usage/tutorial-backpack.md", "# 背包\n\n适用于 26.2。运行 gradlew build。\n");
        files.put("documents/usage/nested/guide-x.md", "# x\n");
        files.put("documents/project/adr-1-build.md", "# x\n");
        files.put("documents/design/overview-system.md", "no heading\n\n# Late\n");
        files.put("documents/reference/api-core.md", "# API\n\n[missing](api-missing.md)\n");
        files.put("documents/development/procedure-port.md", "# 移植\n\n## 步骤\n\n1. 文本。\n");
        files.put("documents/usage/images/screenshot.png", "not really an image\n");
        assertEquals(Set.of("D-01", "D-02", "D-03", "D-05", "D-06", "D-07", "D-08"), rules());
    }

    @Test
    void namesJoinFieldsWithHyphensAndWordsWithUnderscores() throws IOException {
        compliant();
        String figure = "\n\n![图 1：机器](images/guide-first_machine-1.png)\n";
        files.put("documents/usage/guide-first_machine.md", "# 第一台机器\n\n适用于 26.2。" + figure);
        files.put(
                "documents/usage/guide-first_machine-en_US.md",
                "# First machine\n\nFor Minecraft 26.2." + figure);
        files.put("documents/usage/images/guide-first_machine-1.png", "image\n");
        files.put(
                "documents/project/phase-1-start.md", "# 启动\n\n## 目标\n\n## 范围\n\n## 步骤\n\n## 结果\n");
        files.put(
                "documents/project/phase-1.1-tools.md",
                "# 工具\n\n## 目标\n\n## 范围\n\n## 步骤\n\n## 结果\n");
        validation("visual-title_screen");
        assertEquals(List.of(), run(Map.of()));
        files.put("documents/usage/guide-first-machine.md", "# 机器\n");
        files.put("documents/usage/guide-backpack.en_us.md", "# Backpack\n");
        files.put("documents/usage/guide-backpack-en_us.md", "# Backpack\n");
        files.put("README_zh-CN.md", "# 示例\n");
        files.put("components/launch-runner/build.gradle.kts", "plugins {}\n");
        validation("visual-title-screen");
        List<Finding> findings = run(Map.of());
        assertEquals(Set.of("D-02", "L-01", "L-04", "L-08", "V-01"), ids(findings));
        assertEquals(3, findings.stream().filter(finding -> finding.rule() == Rule.D02).count());
    }

    @Test
    void decisionRecordsDeclareTheirStatus() throws IOException {
        compliant();
        String body = "\n\n## 背景\n\n文本。\n\n## 决策\n\n文本。\n\n## 后果\n\n文本。\n";
        files.put("documents/design/adr-2-cache.md", "# 缓存\n\n## 状态\n\n已被 adr-1 取代" + body);
        assertEquals(List.of(), run(Map.of()));
        files.put("documents/design/adr-2-cache.md", "# 缓存\n\n## 状态\n\n已被 adr-9 取代" + body);
        files.put("documents/design/adr-3-store.md", "# 存储\n\n## 状态\n\n还在讨论" + body);
        List<Finding> findings = run(Map.of());
        assertEquals(Set.of("D-06"), ids(findings));
        assertEquals(2, findings.size());
    }

    @Test
    void translationsFollowTheirOriginal() throws IOException {
        compliant();
        files.put("documents/usage/guide-start.md", "# 入门\n\n适用于 26.2。\n\n## 准备\n\n## 步骤\n");
        files.put(
                "documents/usage/guide-start-en_US.md",
                "# Start\n\nFor Minecraft 26.2.\n\n## Preparation\n\n## Steps\n");
        assertEquals(List.of(), run(Map.of()));
        files.put(
                "documents/usage/guide-start-en_US.md",
                "# Start\n\nFor Minecraft 26.2.\n\n## Preparation\n");
        files.put("documents/usage/guide-other-en_US.md", "# Other\n\nFor Minecraft 26.2.\n");
        List<Finding> findings = run(Map.of());
        assertEquals(Set.of("D-13"), ids(findings));
        assertEquals(2, findings.size());
    }

    @Test
    void changelogsFollowKeepAChangelog() throws IOException {
        compliant();
        files.put(
                "documents/release/changelog-v1.md",
                "# 更新日志\n\n适用于 26.2。\n\n## [未发布]\n\n### 新增\n\n- 文本。\n\n"
                        + "## [1.10.0] - 2026-10-01\n\n### 修复\n\n- 文本。\n\n"
                        + "## [1.9.0-beta.2] - 2026-09-01\n\n### 变更\n\n- 文本。\n\n"
                        + "## [1.9.0-beta.1] - 2026-08-01\n\n### 安全\n\n- 文本。\n");
        assertEquals(List.of(), run(Map.of()));
        files.put(
                "documents/release/changelog-v1.md",
                "# 更新日志\n\n适用于 26.2。\n\n## [1.0.0] - 2026-02-30\n\n### 新功能\n\n- 文本。\n\n"
                        + "## [未发布]\n\n## [1.1.0] - 2026-03-01\n\n## 1.2.0\n");
        List<Finding> findings = run(Map.of());
        assertEquals(Set.of("D-14"), ids(findings));
        assertEquals(5, findings.size());
    }

    @Test
    void playerDocumentsNameAVersionFirst() throws IOException {
        compliant();
        files.put("documents/usage/guide-start.md", "# 入门\n\n先打开游戏。\n\n适用于 26.2。\n");
        files.put("documents/usage/faq-general.md", "# 常见问题\n\n适用于 26.20 与 126.2。\n");
        files.put("documents/release/support-versions.md", "# 支持范围\n\n适用于 Minecraft 26.2。\n");
        List<Finding> findings = run(Map.of());
        assertEquals(
                List.of(
                        "D-15 documents/usage/faq-general.md",
                        "D-15 documents/usage/guide-start.md"),
                findings.stream()
                        .map(finding -> finding.rule().id() + " " + finding.path())
                        .sorted()
                        .toList());
    }

    @Test
    void specificationsFitTheInstructionLimit() throws IOException {
        compliant();
        files.put(
                "documents/AGENTS.md",
                project("1.0")
                        + "\n## 补充规则\n\n"
                        + "规则说明。".repeat(SpecificationChecks.INSTRUCTION_LIMIT / 15 + 1)
                        + "\n");
        assertEquals(List.of(Rule.S07), run(Map.of()).stream().map(Finding::rule).toList());
    }

    @Test
    void recordNumbersAreConsistent() throws IOException {
        compliant();
        String adr = "\n\n## 状态\n\n已接受\n\n## 背景\n\n文本。\n\n## 决策\n\n文本。\n\n## 后果\n\n文本。\n";
        String phase = "\n\n## 目标\n\n文本。\n\n## 范围\n\n文本。\n\n## 步骤\n\n1. 文本。\n\n## 结果\n\n文本。\n";
        files.put("documents/design/adr-2-cache.md", "# 缓存" + adr);
        files.put(
                "documents/design/adr-2-cache-en_US.md",
                "# Cache\n\n## Status\n\nAccepted\n\n## Context\n\nText.\n\n## Decision\n\nText.\n\n"
                        + "## Consequences\n\nText.\n");
        files.put("documents/project/phase-1-start.md", "# 启动" + phase);
        files.put("documents/project/phase-1.1-tools.md", "# 工具" + phase);
        files.put("documents/project/phase-1.2-docs.md", "# 文档" + phase);
        assertEquals(List.of(), run(Map.of()));
        files.put("documents/design/adr-03-cache.md", "# 缓存" + adr);
        files.put("documents/design/adr-2-other.md", "# 其他" + adr);
        files.put("documents/project/phase-3-release.md", "# 发布" + phase);
        files.put("documents/project/phase-1.4-tests.md", "# 测试" + phase);
        List<Finding> findings = run(Map.of());
        assertEquals(Set.of("D-02"), ids(findings));
        assertEquals(4, findings.size());
    }

    @Test
    void figuresAreNumberedAndShownInOrder() throws IOException {
        compliant();
        files.put("documents/usage/images/installation-basics-3.png", "image\n");
        files.put("documents/usage/images/installation-basics-01.png", "image\n");
        files.put(
                "documents/usage/guide-start.md",
                "# 入门\n\n适用于 26.2。\n\n![图 2：第二步](images/guide-start-2.png)\n\n![第一步](images/guide-start-1.png)\n");
        files.put("documents/usage/images/guide-start-1.png", "image\n");
        files.put("documents/usage/images/guide-start-2.png", "image\n");
        List<Finding> findings = run(Map.of());
        assertEquals(Set.of("D-08"), ids(findings));
        assertEquals(5, findings.size());
    }

    @Test
    void readmesLinkEachOtherAndListTargets() throws IOException {
        compliant();
        files.put("README-zh_CN.md", readme("示例", "简体中文").replace("| 25 |", "| 21 |"));
        files.put(
                "README.md",
                readme("Example", "English | [简体中文](README-zh_CN.md)")
                        .replace(target, "1.0-forge"));
        List<Finding> findings = run(Map.of());
        assertEquals(Set.of("D-12"), ids(findings));
        assertEquals(4, findings.size());
    }

    @Test
    void textViolationsAreReported() throws IOException {
        compliant();
        files.put("documents/development/setup-bom.md", "﻿# 配置\n");
        files.put("documents/development/setup-crlf.md", "# 配置\r\n");
        files.put("documents/development/setup-end.md", "# 配置\n\n");
        files.put("documents/development/setup-space.md", "# 配置 \n");
        files.put("gradle/wrapper/gradle-wrapper.properties", "distributionUrl=x\n");
        assertEquals(Set.of("C-01", "C-02", "C-03", "C-04", "C-06"), rules());
    }

    @Test
    void gitBinaryAttributesSkipTextRules() throws IOException, InterruptedException {
        assumeTrue(git("init", "-q") == 0, "git is not available");
        compliant();
        component("archive_format");
        Path noise = root.resolve("components/archive_format/data/noise.raw");
        Files.createDirectories(noise.getParent());
        Files.write(noise, new byte[] {(byte) 0xff, (byte) 0xfd, (byte) 0xfb, ' ', '\n'});
        assertEquals(Set.of("C-01"), rules());
        files.put(".gitattributes", "* text=auto eol=lf\n*.raw binary\n");
        assertEquals(List.of(), run(Map.of()));
    }

    @Test
    void trackedIgnoredFilesAreReported() throws IOException, InterruptedException {
        assumeTrue(git("init", "-q") == 0, "git is not available");
        compliant();
        files.put("build/output.txt", "generated\n");
        write();
        assertEquals(0, git("add", "-A"));
        assertEquals(0, git("add", "-f", "build/output.txt"));
        List<Finding> findings =
                Compliance.run(
                        Repository.scan(
                                root,
                                Set.of(target),
                                components,
                                products,
                                tools,
                                instances,
                                sourceSets,
                                new Vocabulary(Map.of())),
                        hashes());
        assertEquals(
                List.of("G-06 build/output.txt"),
                findings.stream()
                        .filter(finding -> finding.rule() == Rule.G06)
                        .map(finding -> finding.rule().id() + " " + finding.path())
                        .toList());
    }

    @Test
    void projectVocabularySilencesWarnings() throws IOException {
        compliant();
        files.put("documents/usage/tutorial-backpack.md", "# 背包\n\n适用于 26.2。\n");
        assertTrue(rules().contains("D-03"));
        assertFalse(
                run(Map.of("documents/usage", List.of("tutorial"))).stream()
                        .anyMatch(finding -> finding.rule() == Rule.D03));
    }

    @Test
    void validationContentsAreDefinedByTheProject() throws IOException {
        compliant();
        validation("conformance-archive");
        files.put(
                "validations/conformance-archive/validation.toml",
                "tool = \"launch_runner\"\ntargets = [\""
                        + NEOFORGE
                        + "\"]\n\n[archive]\nentry = \"mod.json\"\n");
        files.put(
                "validations/conformance-archive/task/scenarios/archive.toml",
                "entries = [\"mod.json\"]\n");
        files.put("validations/conformance-archive/task/fixtures/input.txt", "input\n");
        files.put(
                "validations/conformance-archive/result/archive/check.txt",
                "FAIL: missing entry\n");
        assertEquals(List.of(), run(Map.of()));
    }

    @Test
    void validationResultsStayAsTheToolWroteThem() throws IOException {
        compliant();
        validation("smoke-crash");
        String result = "validations/smoke-crash/result/";
        files.put(result + "latest.log", "Exception\r\n\tat Main.run(Main.java:1) \r\n");
        files.put(result + "report.md", "no heading\n\n[missing](nowhere.md)\n");
        files.put(result + "summary.txt", "﻿no final newline");
        files.put("validations/smoke-crash/task/notes.md", "# 说明\n\n[missing](nowhere.md)\n");
        files.put("validations/smoke-crash/task/plan.txt", "tab\there\n");
        assertEquals(
                List.of(
                        "C-04 validations/smoke-crash/task/plan.txt",
                        "D-05 validations/smoke-crash/task/notes.md"),
                run(Map.of()).stream()
                        .map(finding -> finding.rule().id() + " " + finding.path())
                        .sorted()
                        .toList());
    }

    @Test
    void validationCodeIsReported() throws IOException {
        compliant();
        validation("smoke-launch");
        files.put("validations/smoke-launch/task/scripts/check.py", "print('launch')\n");
        files.put("validations/smoke-launch/task/Runner.JAVA", "class Runner {}\n");
        files.put("validations/smoke-launch/result/run.ps1", "exit 0\n");
        files.put("validations/smoke-launch/task/scenario.toml", "steps = []\n");
        files.put("validations/smoke-launch/instance/mods/probe.jar", "generated\n");
        assertEquals(
                List.of(
                        "V-04 validations/smoke-launch/result/run.ps1",
                        "V-04 validations/smoke-launch/task/Runner.JAVA",
                        "V-04 validations/smoke-launch/task/scripts/check.py"),
                run(Map.of()).stream()
                        .map(finding -> finding.rule().id() + " " + finding.path())
                        .sorted()
                        .toList());
    }

    @Test
    void validationLayoutViolationsAreReported() throws IOException {
        compliant();
        files.put("validations/notes.txt", "notes\n");
        validation("Smoke-bad");
        files.put("validations/Smoke-bad/extra.txt", "extra\n");
        files.put("validations/Smoke-bad/task", "not a directory\n");
        validation("smoke-missing");
        files.remove("validations/smoke-missing/validation.toml");
        validation("smoke-nested");
        files.remove("validations/smoke-nested/validation.md");
        files.put("validations/smoke-nested/validation.md/nested.md", "# 验证\n");
        assertEquals(Set.of("V-01", "V-03"), rules());
    }

    @Test
    void validationVocabularyCanBeExtended() throws IOException {
        compliant();
        validation("audit-archive");
        List<Finding> findings = run(Map.of());
        assertEquals(List.of(Rule.V02), findings.stream().map(Finding::rule).toList());
        assertTrue(new Report(findings, false, files.size()).passed());
        assertFalse(new Report(findings, true, files.size()).passed());
        assertEquals(List.of(), run(Map.of("validations", List.of("audit"))));
    }

    @Test
    void validationInstancesAreExcludedWithoutGit() throws IOException {
        compliant();
        validation("smoke-runtime");
        files.put("validations/smoke-runtime/instance/custom/options.txt", "generated\r\n");
        assertEquals(List.of(), run(Map.of()));
    }

    @Test
    void validationsNameTheirToolAndTargets() throws IOException {
        compliant();
        component("archive_format");
        validation("smoke-empty");
        files.put("validations/smoke-empty/validation.toml", "[scenario]\nsteps = []\n");
        validation("smoke-unknown");
        files.put(
                "validations/smoke-unknown/validation.toml",
                "tool = \"archive_format\"\ntargets = [\"26.9-neoforge\", 1]\n");
        validation("smoke-shape");
        files.put(
                "validations/smoke-shape/validation.toml",
                "tool = [\"launch_runner\"]\ntargets = \"" + NEOFORGE + "\"\n");
        validation("smoke-broken");
        files.put("validations/smoke-broken/validation.toml", "tool = [\n");
        assertEquals(
                List.of(
                        "V-08 validations/smoke-broken/validation.toml",
                        "V-08 validations/smoke-empty/validation.toml",
                        "V-08 validations/smoke-empty/validation.toml",
                        "V-08 validations/smoke-shape/validation.toml",
                        "V-08 validations/smoke-shape/validation.toml",
                        "V-08 validations/smoke-unknown/validation.toml",
                        "V-08 validations/smoke-unknown/validation.toml",
                        "V-08 validations/smoke-unknown/validation.toml"),
                run(Map.of()).stream()
                        .map(finding -> finding.rule().id() + " " + finding.path())
                        .sorted()
                        .toList());
    }

    @Test
    void validationDescriptionsStateGoalCommandAndCriteria() throws IOException {
        compliant();
        validation("smoke-sections");
        files.put("validations/smoke-sections/validation.md", "# 验证\n\n## 目标\n\n启动。\n");
        validation("smoke-command");
        files.put(
                "validations/smoke-command/validation.md",
                "# 验证\n\n## 目标\n\n启动。\n\n## 运行\n\n运行任务。\n\n## 通过标准\n\n没有错误。\n");
        assertEquals(
                List.of(
                        "V-09 validations/smoke-command/validation.md: \"## 运行\"一节应用代码块写出执行这项验证的命令",
                        "V-09 validations/smoke-sections/validation.md: 缺少小节 ## 运行",
                        "V-09 validations/smoke-sections/validation.md: 缺少小节 ## 通过标准"),
                run(Map.of()).stream()
                        .map(
                                finding ->
                                        finding.rule().id()
                                                + " "
                                                + finding.path()
                                                + ": "
                                                + finding.message())
                        .sorted()
                        .toList());
    }

    @Test
    void derivedProjectsLeaveTheTemplateIdentity() throws IOException {
        compliant();
        files.put(
                "gradle.properties",
                files.get("gradle.properties")
                        .replace("mod_id=example", "mod_id=ravens_mod_template"));
        List<Finding> identity =
                run(Map.of()).stream().filter(finding -> finding.rule() == Rule.P06).toList();
        assertEquals(1, identity.size());
        assertEquals(Rule.Level.WARN, identity.getFirst().rule().level());
        assertTrue(identity.getFirst().message().contains("模组 ID"), identity.toString());
    }

    @Test
    void templateNamesStayInTemplateFiles() throws IOException, InterruptedException {
        compliant();
        files.put("NOTICE", "本项目的模板组件来自 Raven's Mod Template。\n");
        files.put("licenses/ravens_mod_template-mit.txt", "MIT License\n");
        files.put(
                "documents/release/changelog-example.md",
                "# 更新日志\n\n适用于 26.2。\n\n## [未发布]\n\n### 新增\n\n- 输出 Raven's Mod Template loaded。\n");
        int readmeLine = files.get("README.md").split("\n", -1).length + 1;
        files.put(
                "README.md",
                files.get("README.md") + "\nPackage io.github.recrivenvi.ModTemplate.\n");
        assertEquals(
                List.of("README.md:" + readmeLine, "documents/release/changelog-example.md:9"),
                run(Map.of()).stream()
                        .filter(finding -> finding.rule() == Rule.P07)
                        .map(finding -> finding.path() + ":" + finding.line())
                        .sorted()
                        .toList());
        assumeTrue(git("init", "-q") == 0);
        assumeTrue(
                git(
                                "remote",
                                "add",
                                "origin",
                                "https://github.com/RecRivenVI/Ravens-Mod-Template.git")
                        == 0);
        assertTrue(run(Map.of()).stream().noneMatch(finding -> finding.rule() == Rule.P07));
    }

    @Test
    void validationInstanceIgnoreRuleIsRequired() throws IOException {
        compliant();
        files.put(".gitignore", "/local.toml\n.gradle/\nbuild/\n/instances/**\n!/instances/**/\n");
        assertEquals(Set.of("L-05"), rules());
    }

    @Test
    void reportsHonourStrictMode() {
        List<Finding> warning = List.of(Finding.of(Rule.D03, "documents/usage/x-y.md", "type x"));
        assertTrue(new Report(warning, false, 1).passed());
        assertFalse(new Report(warning, true, 1).passed());
        assertEquals(
                List.of("::error file=documents/usage/x-y.md,title=D-03 文档类型取自建议词::type x"),
                new Report(warning, true, 1).annotations());
    }

    private void tool(String name) {
        include(name);
        Set<String> registered = new TreeSet<>(tools);
        registered.add(name);
        tools = registered;
    }

    private void validation(String name) {
        if (!tools.contains("launch_runner")) tool("launch_runner");
        String directory = "validations/" + name + "/";
        files.put(
                directory + "validation.md",
                "# 验证\n\n## 目标\n\n启动游戏。\n\n## 运行\n\n```powershell\n.\\gradlew.bat runValidation\n```\n\n## 通过标准\n\n日志中没有错误。\n");
        files.put(directory + "validation.toml", "tool = \"launch_runner\"\ntargets = []\n");
    }

    // 在 components {} 中登记为 product 的组件。
    private void component(String name) {
        include(name);
        Set<String> registered = new TreeSet<>(products);
        registered.add(name);
        products = registered;
    }

    private void include(String name) {
        files.put(
                "components/" + name + "/settings.gradle.kts",
                "plugins {\n    id(\"io.github.recrivenvi.component\")\n}\n");
        files.put("components/" + name + "/build.gradle.kts", "plugins {\n    `java-library`\n}\n");
        Set<String> included = new TreeSet<>(components);
        included.add(name);
        components = included;
    }

    @Test
    void loaderDirectoriesDependOnTheLoader() throws IOException {
        target = FABRIC;
        compliant();
        String sources = "versions/" + target + "/src/";
        files.put(sources + "client/java/io/github/example/client/ExampleClient.java", "x\n");
        files.put(sources + "datagen/java/io/github/example/data/ExampleData.java", "x\n");
        files.put(sources + "gametest/java/io/github/example/test/ExampleTest.java", "x\n");
        files.put(sources + "main/generated/assets/example/lang/en_us.json", "{}\n");
        assertEquals(List.of(), run(Map.of()));
        files.put(sources + "main/generated/assets/example/lang/zh cn.json", "{}\n");
        files.put(sources + "generated/resources/assets/example/lang/en_us.json", "{}\n");
        assertEquals(Set.of("L-03", "P-05"), rules());
    }

    @Test
    void neoForgeKeepsGeneratedResources() throws IOException {
        compliant();
        String sources = "versions/" + target + "/src/";
        files.put(sources + "generated/resources/assets/example/lang/en_us.json", "{}\n");
        assertEquals(List.of(), run(Map.of()));
        files.put(sources + "client/java/io/github/example/ExampleClient.java", "x\n");
        assertEquals(Set.of("L-03"), rules());
    }

    @Test
    void componentsAreRegisteredGradleBuilds() throws IOException {
        compliant();
        component("runtime_safety");
        tool("launch_runner");
        files.put("components/runtime_safety/third_party/library/README.md", "\tvendored \n");
        assertEquals(List.of(), run(Map.of()));
        files.put("components/acceptance_gates/build.gradle.kts", "plugins {}\n");
        files.put(
                "components/native_bridge/settings.gradle.kts",
                "rootProject.name = \"native_bridge\"\n");
        files.put("components/native_bridge/build.gradle.kts", "plugins {}\n");
        components = Set.of("runtime_safety", "launch_runner", "native_bridge");
        List<Finding> findings = run(Map.of());
        assertEquals(Set.of("L-08"), ids(findings));
        assertEquals(
                List.of(
                        "components/acceptance_gates",
                        "components/acceptance_gates",
                        "components/native_bridge",
                        "components/native_bridge/settings.gradle.kts"),
                findings.stream().map(Finding::path).sorted().toList());
        assertTrue(
                findings.stream()
                        .anyMatch(
                                finding ->
                                        finding.path().equals("components/native_bridge")
                                                && finding.message()
                                                        .startsWith("直接用 includeBuild 包含")),
                findings.toString());
    }

    @Test
    void templateFilesAreLocked() throws IOException {
        compliant();
        String checker = "components/compliance/src/main/java/Checker.java";
        String settings = "components/compliance/settings.gradle.kts";
        String build = "components/compliance/build.gradle.kts";
        files.put(checker, "class Checker {}\n");
        files.put(settings, "rootProject.name = \"compliance\"\n");
        files.put(build, "plugins {}\n");
        components = Set.of("compliance");
        locked = hashes();
        for (String file : List.of(checker, settings, build))
            locked.put(
                    file,
                    SpecificationChecks.sha256(files.get(file).getBytes(StandardCharsets.UTF_8)));
        assertEquals(List.of(), run(Map.of()));
        files.put(checker, "class Checker {\n    boolean lenient;\n}\n");
        files.put("components/compliance/src/main/java/Extra.java", "class Extra {}\n");
        locked.put(".rumdl.toml", "0".repeat(64));
        assertEquals(
                List.of(
                        "S-06 .rumdl.toml",
                        "S-06 " + checker,
                        "S-06 components/compliance/src/main/java/Extra.java"),
                run(Map.of()).stream()
                        .map(finding -> finding.rule().id() + " " + finding.path())
                        .sorted()
                        .toList());
    }

    @Test
    void researchDocumentsAreDatedAndStructured() throws IOException {
        compliant();
        files.put(
                "documents/research/audit-tick_timing.md",
                "# Tick 时序审计\n\n调查日期 2026-09-17，依据提交 abc123。\n\n"
                        + "## 问题\n\n文本。\n\n## 方法\n\n文本。\n\n## 发现\n\n文本。\n\n## 结论\n\n文本。\n");
        assertEquals(List.of(), run(Map.of()));
        files.put(
                "documents/research/incident-structure_loss.md",
                "# 结构丢失\n\n发生在 2026-02-30。\n\n## 问题\n\n文本。\n");
        List<Finding> findings = run(Map.of());
        assertEquals(Set.of("D-06", "D-16"), ids(findings));
        assertEquals(4, findings.size());
    }

    @Test
    void targetScriptsTakeVersionsFromFacts() throws IOException {
        compliant();
        String script = "versions/" + target + "/build.gradle.kts";
        files.put(
                script,
                files.get(script)
                        + "\ndependencies {\n"
                        + "    compileOnly(target.module(\"maven.modrinth:sodium\", \"sodium_version\"))\n"
                        + "    implementation(libs.snakeyaml)\n}\n");
        assertEquals(List.of(), run(Map.of()));
        files.put(
                script,
                files.get(script)
                        + "dependencies {\n"
                        + "    compileOnly(\"maven.modrinth:iris:\" + target.fact(\"iris_version\"))\n}\n");
        assertEquals(Set.of("T-02"), rules());
    }

    @Test
    void licenseTextsAndBinariesAreChecked() throws IOException {
        compliant();
        files.put("COPYING", "GPL\n");
        files.put("COPYING.LESSER", "LGPL\n");
        files.put("inputs.toml", "");
        files.put("licenses/dlss-nvidia_sdk.txt", "text\n");
        files.put("licenses/joml-mit.md", "# MIT\n");
        files.put("licenses/glfw-zlib_1.0.txt", "text\n");
        files.put("gradle/wrapper/gradle-wrapper.jar", "wrapper\n");
        assertEquals(List.of(), run(Map.of()));
        files.put("licenses/Readme.txt", "x\n");
        files.put("licenses/nested/glm-mit.txt", "x\n");
        files.put("libs/examplemod.jar", "x\n");
        files.put("versions/" + target + "/src/main/resources/natives/renderer.dll", "x\n");
        List<Finding> findings = run(Map.of());
        assertEquals(Set.of("G-09", "L-01", "L-09"), ids(findings));
        assertEquals(2, findings.stream().filter(finding -> finding.rule() == Rule.G09).count());
        assertEquals(2, findings.stream().filter(finding -> finding.rule() == Rule.L09).count());
    }

    @Test
    void customInstancesAreAllowed() throws IOException {
        compliant();
        instances = Set.of("client", "client-multiplayer", "server", "secondary-skins");
        files.put("instances/" + target + "/secondary-skins/options.txt", "x\n");
        assertEquals(List.of(), run(Map.of()));
        files.put("instances/" + target + "/primary-skins/options.txt", "x\n");
        assertEquals(Set.of("I-04"), rules());
    }

    @Test
    void englishDocumentsUseEnglishSections() throws IOException {
        compliant();
        files.put(
                "documents/AGENTS.md",
                project("1.0").replace("| 文档主语言 | 简体中文 |", "| 文档主语言 | English |"));
        files.put(
                "documents/design/adr-1-build.md",
                "# Build\n\n## Status\n\nSuperseded by adr-2\n\n## Context\n\nText.\n\n"
                        + "## Decision\n\nText.\n\n## Consequences\n\nText.\n");
        files.put(
                "documents/design/adr-2-cache.md",
                "# Cache\n\n## Status\n\nAccepted\n\n## Context\n\nText.\n\n"
                        + "## Decision\n\nText.\n\n## Consequences\n\nText.\n");
        files.put(
                "documents/development/procedure-add_target.md",
                "# Add a Target\n\n## Scope\n\nText.\n\n## Steps\n\n1. Text.\n\n## Acceptance\n\nText.\n");
        files.put(
                "documents/development/procedure-add_target-zh_CN.md",
                "# 新增 Target\n\n## 适用范围\n\n文本。\n\n## 步骤\n\n1. 文本。\n\n## 验收\n\n文本。\n");
        files.put(
                "documents/research/audit-timing.md",
                "# Timing audit\n\nRecorded on 2026-09-17.\n\n"
                        + "## Question\n\n## Method\n\n## Findings\n\n## Conclusion\n");
        assertEquals(List.of(), run(Map.of()));
        files.put(
                "documents/design/adr-2-cache.md",
                "# Cache\n\n## 状态\n\n已接受\n\n## Context\n\nText.\n\n"
                        + "## Decision\n\nText.\n\n## Consequences\n\nText.\n");
        assertEquals(Set.of("D-06"), rules());
        files.put(
                "documents/AGENTS.md",
                project("1.0").replace("| 文档主语言 | 简体中文 |", "| 文档主语言 | 日本語 |"));
        assertTrue(rules().contains("S-05"));
    }

    @Test
    void onlyBatchFilesUseCrlf() throws IOException {
        compliant();
        component("acceptance_gates");
        files.put("components/acceptance_gates/scripts/run.cmd", "@echo off\r\n");
        files.put("components/acceptance_gates/scripts/run.ps1", "Write-Output 1\n");
        assertEquals(List.of(), run(Map.of()));
        files.put("components/acceptance_gates/scripts/run.cmd", "@echo off\n");
        files.put("components/acceptance_gates/scripts/run.ps1", "Write-Output 1\r\n");
        assertEquals(
                List.of(
                        "C-02 components/acceptance_gates/scripts/run.cmd",
                        "C-02 components/acceptance_gates/scripts/run.ps1",
                        "C-03 components/acceptance_gates/scripts/run.cmd"),
                run(Map.of()).stream()
                        .map(finding -> finding.rule().id() + " " + finding.path())
                        .sorted()
                        .toList());
    }

    @Test
    void extraSourceSetsAreRegistered() throws IOException {
        compliant();
        files.put(
                "versions/" + target + "/src/bootstrap/java/io/github/example/boot/Boot.java",
                "package io.github.example.boot;\n");
        assertEquals(Set.of("L-03"), rules());
        sourceSets = Set.of("bootstrap");
        assertEquals(List.of(), run(Map.of()));
    }

    @Test
    void githubHoldsOnlyKnownFiles() throws IOException {
        compliant();
        files.put(".github/workflows/check.yml", "name: Check\n");
        files.put(".github/ISSUE_TEMPLATE/bug_report.md", "# Bug\n");
        files.put(".github/ISSUE_TEMPLATE/config.yml", "blank_issues_enabled: false\n");
        files.put(".github/PULL_REQUEST_TEMPLATE.md", "# Change\n");
        assertEquals(List.of(), run(Map.of()));
        files.put(".github/ISSUE_TEMPLATE/bug-report.md", "# Bug\n");
        files.put(".github/workflows/scripts/run.sh", "exit 0\n");
        files.put(".github/notes.md", "# Notes\n");
        List<Finding> findings = run(Map.of());
        assertEquals(Set.of("L-10"), ids(findings));
        assertEquals(3, findings.size());
    }

    @Test
    void identicalPlainJavaAcrossTargetsIsHinted() throws IOException {
        compliant();
        String other = "1.21.1-neoforge";
        targets = Set.of(target, other);
        String plain = "package io.github.example.math;\n\npublic final class Curve {}\n";
        String game =
                "package io.github.example;\n\nimport net.minecraft.world.level.Level;\n\nclass Hook {}\n";
        for (String version : List.of(target, other)) {
            String sources = "versions/" + version + "/src/main/java/io/github/example/";
            files.put(sources + "math/Curve.java", plain);
            files.put(sources + "Hook.java", game);
            files.put(sources + "package-info.java", "package io.github.example;\n");
            files.put(
                    "versions/" + version + "/src/probe/java/io/github/example/probe/Results.java",
                    plain.replace("math", "probe"));
        }
        List<Finding> hints =
                run(Map.of()).stream().filter(finding -> finding.rule() == Rule.T07).toList();
        assertEquals(
                List.of("versions/" + other + "/src/main/java/io/github/example/math/Curve.java"),
                hints.stream().map(Finding::path).toList());
        assertTrue(new Report(hints, false, files.size()).passed());
    }

    @Test
    void loaderRangesMayUseTheLoaderMinimum() throws IOException {
        compliant();
        String metadata = "versions/" + target + "/src/main/resources/META-INF/neoforge.mods.toml";
        files.put(
                metadata,
                files.get(metadata).replace("[${loader_version},)", "[${loader_minimum},)"));
        assertEquals(List.of(), run(Map.of()));
        files.put(metadata, files.get(metadata).replace("[${loader_minimum},)", "[21.1.62,)"));
        assertEquals(Set.of("P-04"), rules());
    }

    @Test
    void tomlMetadataFieldsAreCheckedWhereTheLoaderReadsThem() throws IOException {
        compliant();
        String metadata = "versions/" + target + "/src/main/resources/META-INF/neoforge.mods.toml";
        String original = files.get(metadata);
        files.put(
                metadata,
                original.replace("[[dependencies.${mod_id}]]", "[[dependencies.unrelated]]"));
        List<Finding> findings = run(Map.of());
        assertEquals(Set.of("P-04"), ids(findings));
        assertEquals(3, findings.size(), findings.toString());
        files.put(
                metadata,
                original.replace("[[dependencies.${mod_id}]]", "[[dependencies.\"${mod_id}\"]]"));
        assertEquals(List.of(), run(Map.of()));
        files.put(
                metadata,
                original.replace("modId = \"${mod_id}\"", "modId = \"wrong\"")
                        + "\n[[mods]]\nmodId = \"${mod_id}\"\n");
        assertEquals(List.of("必须有且只有一个 [[mods]] 条目"), messages(Rule.P04));
        files.put(metadata, original.replace("license = \"${mod_license}\"", "license = \"MIT\""));
        assertEquals(List.of("license 必须是 \"${mod_license}\""), messages(Rule.P04));
        files.put(metadata, original + "modId = \"twice\"\n");
        assertEquals(Set.of("P-04"), rules());
    }

    @Test
    void fabricMetadataFieldsAreReadAtTheTopLevel() throws IOException {
        target = FABRIC;
        compliant();
        String metadata = "versions/" + target + "/src/main/resources/fabric.mod.json";
        String original = files.get(metadata);
        files.put(
                metadata,
                original.replace("\"id\": \"${mod_id}\"", "\"id\": \"wrong_mod\"")
                        .replace(
                                "\"schemaVersion\": 1,",
                                "\"schemaVersion\": 1,\n    \"custom\": {\"id\": \"${mod_id}\"},"));
        assertEquals(List.of("id 必须是 \"${mod_id}\""), messages(Rule.P04));
        files.put(
                metadata,
                original.replace(
                        "\"main\": [\"${mod_group}.Example\"]",
                        "\"main\": [{\"adapter\": \"kotlin\", \"value\": \"${mod_group}.Example\"}],"
                                + " \"client\": [\"org.other.Client\"]"));
        assertEquals(
                List.of("entrypoints.client 中的入口 org.other.Client 必须以 ${mod_group}. 开头"),
                messages(Rule.P04));
        files.put(
                metadata,
                original.replace(
                        "\"minecraft\": \"${minecraft_version}\"",
                        "\"minecraft\": [\"~${minecraft_version}\", \"26.2.1\"]"));
        assertEquals(List.of(), run(Map.of()));
        files.put(metadata, original.replace("\"id\": \"${mod_id}\"", "\"id\": ${mod_id}"));
        assertEquals(Set.of("P-01", "P-04"), rules());
    }

    @Test
    void targetSourceDirectoryHoldsOnlySourceSets() throws IOException {
        compliant();
        files.put("versions/" + target + "/src/scratch.py", "print(1)\n");
        files.put("versions/" + target + "/src/test", "not a directory\n");
        files.put("versions/" + target + "/src/notes/readme.txt", "x\n");
        List<Finding> findings = run(Map.of());
        assertEquals(Set.of("L-03"), ids(findings));
        assertEquals(
                List.of(
                        "versions/" + target + "/src/notes",
                        "versions/" + target + "/src/scratch.py",
                        "versions/" + target + "/src/test"),
                findings.stream().map(Finding::path).sorted().toList());
    }

    @Test
    void targetScriptVersionsAreFoundInStringsAndCode() throws IOException {
        compliant();
        String script = "versions/" + target + "/build.gradle.kts";
        String plugins = files.get(script);
        files.put(
                script,
                plugins
                        + "\n// version \"2.0\" of the run setup\n"
                        + "println(project.version)\n"
                        + "val url = \"https://maven.example.com:8443/releases\" // 1.21\n"
                        + "repositories {\n"
                        + "    maven(\"https://192.168.1.10/repository/releases\")\n"
                        + "}\n"
                        + "/* The loader \"0.19.5\" comes from\n   target.properties. */\n"
                        + "dependencies {\n"
                        + "    compileOnly(target.module(\"maven.modrinth:sodium\", \"sodium_version\"))\n"
                        + "}\n");
        assertEquals(List.of(), run(Map.of()));
        files.put(
                script,
                plugins
                        + "val requiredMinecraft = \"1.21.1\"\n"
                        + "dependencies {\n"
                        + "    compileOnly(\"maven.modrinth:iris:${target.fact(\"iris_version\")}\")\n"
                        + "}\n"
                        + "val sodium = \"maven.modrinth:sodium:$sodiumVersion\"\n");
        List<Finding> findings = run(Map.of());
        assertEquals(Set.of("T-02"), ids(findings));
        assertEquals(List.of(4, 6, 8), findings.stream().map(Finding::line).sorted().toList());
    }

    @Test
    void readmeLoaderVersionsMatchExactly() throws IOException {
        compliant();
        files.put("README.md", files.get("README.md").replace("Loader 1.0 |", "Loader 1.0.9 |"));
        assertEquals(List.of(target + " 的加载器版本应为 1.0"), messages(Rule.D12));
        files.put("README.md", files.get("README.md").replace("Loader 1.0.9 |", "`1.0` |"));
        assertEquals(List.of(), run(Map.of()));
    }

    @Test
    void changelogVersionsFollowSemanticVersioning() throws IOException {
        compliant();
        String header = "# 更新日志\n\n适用于 26.2。\n\n";
        files.put(
                "documents/release/changelog-v1.md",
                header
                        + "## [1.0.0+build.2] - 2026-10-02\n\n"
                        + "## [1.0.0-rc.1+build.1] - 2026-10-01\n\n"
                        + "## [1.0.0-alpha.99999999999999999999] - 2026-09-01\n\n"
                        + "## [1.0.0-alpha.2] - 2026-08-01\n");
        assertEquals(List.of(), run(Map.of()));
        files.put(
                "documents/release/changelog-v1.md",
                header
                        + "## [01.0.0] - 2026-10-01\n\n"
                        + "## [1.0.0-alpha..1] - 2026-09-01\n\n"
                        + "## [1.0.0-01] - 2026-08-01\n\n"
                        + "## [1.0.0+] - 2026-07-01\n");
        assertEquals(
                List.of(
                        "01.0.0 不符合语义化版本 2.0.0",
                        "1.0.0-alpha..1 不符合语义化版本 2.0.0",
                        "1.0.0-01 不符合语义化版本 2.0.0",
                        "1.0.0+ 不符合语义化版本 2.0.0"),
                messages(Rule.D14));
        assertTrue(DocumentChecks.compare("1.0.0+b", "1.0.0+a") == 0);
        assertTrue(DocumentChecks.compare("1.0.0", "1.0.0-rc.1") > 0);
        assertTrue(DocumentChecks.compare("1.0.0-alpha.10", "1.0.0-alpha.9") > 0);
        assertTrue(DocumentChecks.compare("1.0.0-alpha.beta", "1.0.0-alpha.1") > 0);
    }

    private List<String> messages(Rule rule) throws IOException {
        return run(Map.of()).stream()
                .filter(finding -> finding.rule() == rule)
                .map(Finding::message)
                .toList();
    }

    private static Set<String> ids(List<Finding> findings) {
        return findings.stream()
                .map(finding -> finding.rule().id())
                .collect(Collectors.toCollection(TreeSet::new));
    }

    private int git(String... arguments) throws IOException, InterruptedException {
        List<String> command = new java.util.ArrayList<>(List.of("git", "-C", root.toString()));
        command.addAll(List.of(arguments));
        try {
            Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
            process.getInputStream().readAllBytes();
            return process.waitFor();
        } catch (IOException e) {
            return -1;
        }
    }
}
