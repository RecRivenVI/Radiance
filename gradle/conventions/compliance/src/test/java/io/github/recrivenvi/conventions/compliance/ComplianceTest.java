package io.github.recrivenvi.conventions.compliance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

// 合规检查的整体行为：合规仓库没有发现，严格模式的判定。
class ComplianceTest extends ComplianceFixture {
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
    void reportsHonourStrictMode() {
        List<Finding> warning = List.of(Finding.of(Rule.D03, "documents/usage/x-y.md", "type x"));
        assertTrue(new Report(warning, false, 1).passed());
        assertFalse(new Report(warning, true, 1).passed());
        assertEquals(
                List.of("::error file=documents/usage/x-y.md,title=D-03 文档类型取自建议词::type x"),
                new Report(warning, true, 1).annotations());
    }
}
