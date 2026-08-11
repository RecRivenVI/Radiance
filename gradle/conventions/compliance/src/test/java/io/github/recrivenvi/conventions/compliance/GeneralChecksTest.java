package io.github.recrivenvi.conventions.compliance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

// 第 1 节"总则"。
class GeneralChecksTest extends ComplianceFixture {
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
}
