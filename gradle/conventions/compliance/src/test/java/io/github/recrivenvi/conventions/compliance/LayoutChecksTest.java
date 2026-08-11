package io.github.recrivenvi.conventions.compliance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

// 第 3 节"目录"。
class LayoutChecksTest extends ComplianceFixture {
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
    void validationInstanceIgnoreRuleIsRequired() throws IOException {
        compliant();
        files.put(".gitignore", "/local.toml\n.gradle/\nbuild/\n/instances/**\n!/instances/**/\n");
        assertEquals(Set.of("L-05"), rules());
    }
}
