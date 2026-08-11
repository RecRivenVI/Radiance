package io.github.recrivenvi.conventions.compliance;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

final class Compliance {
    private Compliance() {}

    static List<Finding> run(Repository repository) {
        return run(repository, SpecificationChecks.templateHashes());
    }

    static List<Finding> run(Repository repository, Map<String, String> lockedHashes) {
        List<Finding> findings = new ArrayList<>();
        // 按 AGENTS.md 的章节顺序，每一节一个检查类。
        GeneralChecks.check(repository, findings);
        SpecificationChecks.check(repository, lockedHashes, findings);
        LayoutChecks.check(repository, findings);
        FactChecks.check(repository, findings);
        TargetChecks.check(repository, findings);
        ProductChecks.check(repository, findings);
        RunChecks.check(repository, findings);
        ValidationChecks.check(repository, findings);
        DocumentChecks.check(repository, findings);
        CodeChecks.check(repository, findings);
        findings.sort(
                Comparator.comparing((Finding finding) -> finding.rule().ordinal())
                        .thenComparing(Finding::path)
                        .thenComparingInt(Finding::line));
        return List.copyOf(findings);
    }
}
