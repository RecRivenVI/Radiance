package io.github.recrivenvi.conventions.compliance;

import java.util.List;
import java.util.Set;

// 第 1 节"总则"。
final class GeneralChecks {
    private static final Set<String> BINARIES = Set.of("jar", "class", "dll", "so", "dylib", "exe");
    private static final String WRAPPER = "gradle/wrapper/gradle-wrapper.jar";

    private GeneralChecks() {}

    static void check(Repository repository, List<Finding> findings) {
        ignored(repository, findings);
        binaries(repository, findings);
    }

    private static void ignored(Repository repository, List<Finding> findings) {
        for (String file : repository.trackedIgnoredFiles())
            findings.add(
                    Finding.of(Rule.G06, file, "被 .gitignore 忽略却被 Git 跟踪；用 git rm --cached 移除"));
    }

    private static void binaries(Repository repository, List<Finding> findings) {
        for (String file : repository.files()) {
            String extension = file.substring(file.lastIndexOf('.') + 1).toLowerCase();
            if (file.lastIndexOf('.') > file.lastIndexOf('/')
                    && BINARIES.contains(extension)
                    && !file.equals(WRAPPER))
                findings.add(Finding.of(Rule.G09, file, "构建二进制由构建生成，或作为本地输入登记在 inputs.toml"));
        }
    }
}
