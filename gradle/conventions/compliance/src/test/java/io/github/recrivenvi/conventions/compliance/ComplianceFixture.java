package io.github.recrivenvi.conventions.compliance;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import org.junit.jupiter.api.io.TempDir;

// 合规检查测试共用的仓库夹具：在临时目录中写出一个合规的仓库，再按需修改。
abstract class ComplianceFixture {
    static final String NEOFORGE = "26.2-neoforge";
    static final String FABRIC = "26.2-fabric";
    static final String FORGE = "1.20.1-forge";

    @TempDir Path root;

    final Map<String, String> files = new LinkedHashMap<>();
    String target = NEOFORGE;
    Map<String, String> locked;
    Set<String> components = Set.of();
    Set<String> products = Set.of();
    Set<String> tools = Set.of();
    Set<String> instances = Repository.BUILT_IN_INSTANCES;
    Set<String> sourceSets = Set.of();
    Set<String> targets;

    static String agents(String version, Map<String, List<String>> vocabulary) {
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

    String project(String loaderVersion) {
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

    String readme(String title, String links) {
        return "# "
                + title
                + "\n\n"
                + links
                + "\n\n| Target | Loader | Java |\n| --- | --- | --- |\n| `"
                + target
                + "` | Loader 1.0 | 25 |\n\nSee [installation](documents/usage/installation-basics.md).\n";
    }

    void compliant() {
        String sources = "versions/" + target + "/src/main/";
        files.put("AGENTS.md", agents(SpecificationChecks.VERSION, Vocabulary.BUILT_IN));
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
        files.put(FactChecks.CATALOG, FactChecks.templateCatalog());
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
                "documents/release/changelog-example.md",
                "# 更新日志\n\n适用于 Minecraft " + minecraft() + "。\n\n## [未发布]\n\n### 新增\n\n- 文本。\n");
        files.put(
                "documents/design/adr-1-build.md",
                "# 构建\n\n## 状态\n\n已接受\n\n## 背景\n\n文本。\n\n## 决策\n\n文本。\n\n## 后果\n\n文本。\n");
        files.put(
                "documents/development/procedure-add_target.md",
                "# 新增 Target\n\n## 适用范围\n\n文本。\n\n## 步骤\n\n1. 文本。\n\n## 验收\n\n文本。\n");
    }

    void write() throws IOException {
        for (Map.Entry<String, String> file : files.entrySet()) {
            Path path = root.resolve(file.getKey());
            Files.createDirectories(path.getParent());
            Files.writeString(path, file.getValue());
        }
        Path image = root.resolve("documents/usage/images/installation-basics-1.png");
        Files.createDirectories(image.getParent());
        Files.write(image, new byte[] {(byte) 0x89, 'P', 'N', 'G', 0, 0});
    }

    String minecraft() {
        return target.substring(0, target.lastIndexOf('-'));
    }

    Map<String, String> hashes() {
        Map<String, String> hashes = new LinkedHashMap<>();
        for (String file : List.of("AGENTS.md"))
            if (files.containsKey(file))
                hashes.put(
                        file,
                        SpecificationChecks.sha256(
                                files.get(file).getBytes(StandardCharsets.UTF_8)));
        return hashes;
    }

    List<Finding> run(Map<String, List<String>> additions) throws IOException {
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

    Set<String> rules() throws IOException {
        return run(Map.of()).stream()
                .map(finding -> finding.rule().id())
                .collect(Collectors.toCollection(TreeSet::new));
    }

    List<String> documentFindings() throws IOException {
        return run(Map.of()).stream()
                .filter(finding -> finding.rule().id().startsWith("D-"))
                .map(finding -> finding.rule().id() + " " + finding.path())
                .sorted()
                .toList();
    }

    void tool(String name) {
        include(name);
        Set<String> registered = new TreeSet<>(tools);
        registered.add(name);
        tools = registered;
    }

    void validation(String name) {
        if (!tools.contains("launch_runner")) tool("launch_runner");
        String directory = "validations/" + name + "/";
        files.put(
                directory + "validation.md",
                "# 验证\n\n## 目标\n\n启动游戏。\n\n## 运行\n\n```powershell\n.\\gradlew.bat runValidation\n```\n\n## 通过标准\n\n日志中没有错误。\n");
        files.put(directory + "validation.toml", "tool = \"launch_runner\"\ntargets = []\n");
    }

    // 在 components {} 中登记为 product 的组件。
    void component(String name) {
        include(name);
        Set<String> registered = new TreeSet<>(products);
        registered.add(name);
        products = registered;
    }

    void include(String name) {
        files.put(
                "components/" + name + "/settings.gradle.kts",
                "plugins {\n    id(\"" + LayoutChecks.COMPONENT_PLUGIN_ID + "\")\n}\n");
        files.put("components/" + name + "/build.gradle.kts", "plugins {\n    `java-library`\n}\n");
        Set<String> included = new TreeSet<>(components);
        included.add(name);
        components = included;
    }

    List<String> messages(Rule rule) throws IOException {
        return run(Map.of()).stream()
                .filter(finding -> finding.rule() == rule)
                .map(Finding::message)
                .toList();
    }

    static Set<String> ids(List<Finding> findings) {
        return findings.stream()
                .map(finding -> finding.rule().id())
                .collect(Collectors.toCollection(TreeSet::new));
    }

    int git(String... arguments) throws IOException, InterruptedException {
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
