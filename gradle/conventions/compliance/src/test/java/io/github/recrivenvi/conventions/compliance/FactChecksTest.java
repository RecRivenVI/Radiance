package io.github.recrivenvi.conventions.compliance;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

// 第 4 节"事实与配置"。
class FactChecksTest extends ComplianceFixture {
    @Test
    void templateToolVersionsFollowTheTemplate() throws IOException {
        compliant();
        String catalog = FactChecks.templateCatalog();
        files.put(
                FactChecks.CATALOG,
                catalog.replace(
                        "[libraries]\n",
                        "[libraries]\nexample-lib = { module = \"org.example:lib\", version = \"1.0\" }\n"));
        files.put(
                ".github/workflows/release.yml",
                "jobs:\n  release:\n    steps:\n      - uses: actions/cache@v4\n");
        assertEquals(List.of(), run(Map.of()));
        files.put(
                FactChecks.CATALOG,
                catalog.replaceFirst("(?m)^spotless = \"[^\"]+\"", "spotless = \"0.0.1\"")
                        .replaceFirst("(?m)^rumdl = .*\\n", ""));
        files.put(
                ".github/workflows/release.yml",
                "jobs:\n  release:\n    steps:\n      - uses: \"actions/checkout@v1\" # pinned\n");
        assertEquals(
                List.of(
                        "F-06 .github/workflows/release.yml",
                        "F-06 gradle/libs.versions.toml",
                        "F-06 gradle/libs.versions.toml"),
                run(Map.of()).stream()
                        .map(finding -> finding.rule().id() + " " + finding.path())
                        .sorted()
                        .toList());
    }

    @Test
    void minecraftBuildPluginsAreProjectOwned() throws IOException {
        compliant();
        files.put(
                FactChecks.CATALOG,
                FactChecks.templateCatalog()
                        .replaceFirst("(?m)^loom = \"[^\"]+\"", "loom = \"9.9.9\"")
                        .replaceFirst("(?m)^moddev = \"[^\"]+\"", "moddev = \"9.9.9\""));
        assertEquals(List.of(), run(Map.of()));
    }
}
