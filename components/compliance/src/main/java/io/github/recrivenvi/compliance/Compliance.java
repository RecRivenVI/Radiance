package io.github.recrivenvi.compliance;

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
        SpecificationChecks.check(repository, lockedHashes, findings);
        LayoutChecks.check(repository, findings);
        LoaderChecks.check(repository, findings);
        ValidationChecks.check(repository, findings);
        DocumentChecks.check(repository, findings);
        TextChecks.check(repository, findings);
        findings.sort(
                Comparator.comparing((Finding finding) -> finding.rule().ordinal())
                        .thenComparing(Finding::path)
                        .thenComparingInt(Finding::line));
        return List.copyOf(findings);
    }
}
