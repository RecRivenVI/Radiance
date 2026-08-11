package io.github.recrivenvi.conventions.compliance;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;

// 第 7 节"运行与测试"。
final class RunChecks {
    // 实例目录被 Git 忽略，持续集成里不存在，只会在使用者本机留下。
    private static final String LEFTOVER = "如果它属于已移除的 Target 或额外实例，其中可能有存档，经使用者同意后移出仓库或删除";

    private RunChecks() {}

    static void check(Repository repository, List<Finding> findings) {
        Path directory = repository.root().resolve("instances");
        if (!Files.exists(directory)) return;
        if (!Files.isDirectory(directory)) {
            findings.add(Finding.of(Rule.I04, "instances", "instances 必须是目录"));
            return;
        }
        for (Path target : children(directory)) {
            String name = "instances/" + target.getFileName();
            if (!Files.isDirectory(target)) {
                findings.add(Finding.of(Rule.I04, name, "这里只能有已登记 Target 的实例目录"));
                continue;
            }
            if (!repository.targets().contains(target.getFileName().toString())) {
                findings.add(Finding.of(Rule.I04, name, "不是已登记的 Target；" + LEFTOVER));
                continue;
            }
            for (Path variant : children(target))
                if (!Files.isDirectory(variant)
                        || !repository.instances().contains(variant.getFileName().toString()))
                    findings.add(
                            Finding.of(
                                    Rule.I04,
                                    name + "/" + variant.getFileName(),
                                    "不是已登记的实例（"
                                            + String.join(
                                                    "、", new TreeSet<>(repository.instances()))
                                            + "）；"
                                            + LEFTOVER));
        }
    }

    // 文件管理器在浏览过的目录中留下的文件，.gitignore 同样忽略它们。
    private static final Set<String> SYSTEM_FILES = Set.of(".DS_Store", "Desktop.ini", "Thumbs.db");

    private static List<Path> children(Path directory) {
        try (Stream<Path> entries = Files.list(directory)) {
            return entries.filter(
                            path ->
                                    SYSTEM_FILES.stream()
                                            .noneMatch(
                                                    name ->
                                                            name.equalsIgnoreCase(
                                                                    path.getFileName().toString())))
                    .sorted()
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
