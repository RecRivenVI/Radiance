package io.github.recrivenvi.conventions.compliance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

// 第 2 节"规范文件"。
class SpecificationChecksTest extends ComplianceFixture {
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
        files.put("documents/AGENTS.md", project("2.0"));
        assertEquals(Set.of("S-01", "S-02", "S-03", "S-05", "S-06"), rules());
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
    void templateFilesAreLocked() throws IOException {
        compliant();
        String checker = "gradle/conventions/compliance/src/main/java/Checker.java";
        String settings = "gradle/conventions/settings.gradle.kts";
        String build = "gradle/conventions/compliance/build.gradle.kts";
        files.put(checker, "class Checker {}\n");
        files.put(settings, "rootProject.name = \"conventions\"\n");
        files.put(build, "plugins {}\n");
        locked = hashes();
        for (String file : List.of(checker, settings, build))
            locked.put(
                    file,
                    SpecificationChecks.sha256(files.get(file).getBytes(StandardCharsets.UTF_8)));
        assertEquals(List.of(), run(Map.of()));
        files.put(checker, "class Checker {\n    boolean lenient;\n}\n");
        files.put("gradle/conventions/specification/development/setup-extra.md", "# 额外\n");
        locked.put("gradle/conventions/rumdl.toml", "0".repeat(64));
        assertEquals(
                List.of(
                        "S-06 " + checker,
                        "S-06 gradle/conventions/rumdl.toml",
                        "S-06 gradle/conventions/specification/development/setup-extra.md"),
                run(Map.of()).stream()
                        .map(finding -> finding.rule().id() + " " + finding.path())
                        .sorted()
                        .toList());
    }

    @Test
    void deletedRulesKeepTheirNumber() throws IOException {
        compliant();
        files.put("AGENTS.md", files.get("AGENTS.md") + "\n- **S-04** [已删除] 原规则概要与删除原因。\n");
        assertEquals(List.of(), run(Map.of()));
        files.put(
                "AGENTS.md", files.get("AGENTS.md").replace("- **D-17** [检查]", "- **D-17** [已删除]"));
        assertEquals(
                List.of("S-02 规则 D-17 标记为 [已删除]，检查器按 [检查] 执行"),
                run(Map.of()).stream()
                        .map(finding -> finding.rule().id() + " " + finding.message())
                        .toList());
    }
}
