package io.github.recrivenvi.compliance;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.SetProperty;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.UntrackedTask;
import org.gradle.api.tasks.options.Option;

@UntrackedTask(because = "检查整个仓库")
public abstract class VerifyComplianceTask extends DefaultTask {
    @Internal
    public abstract DirectoryProperty getRootDirectory();

    @Input
    public abstract SetProperty<String> getTargets();

    @Input
    public abstract SetProperty<String> getComponents();

    @Input
    public abstract SetProperty<String> getProducts();

    @Input
    public abstract SetProperty<String> getTools();

    @Input
    public abstract SetProperty<String> getInstances();

    @Input
    public abstract SetProperty<String> getSourceSets();

    @Input
    public abstract MapProperty<String, List<String>> getVocabulary();

    @Input
    @Option(option = "strict", description = "把警告视为失败")
    public abstract Property<Boolean> getStrict();

    @Internal
    public abstract DirectoryProperty getReportDirectory();

    @TaskAction
    public void verify() throws IOException {
        Repository repository =
                Repository.scan(
                        getRootDirectory().get().getAsFile().toPath(),
                        getTargets().get(),
                        getComponents().get(),
                        getProducts().get(),
                        getTools().get(),
                        getInstances().get(),
                        getSourceSets().get(),
                        new Vocabulary(getVocabulary().get()));
        Report report =
                new Report(
                        Compliance.run(repository), getStrict().get(), repository.files().size());
        for (String line : report.consoleLines()) getLogger().lifecycle(line);
        getLogger().lifecycle(report.summary());
        Path directory = getReportDirectory().get().getAsFile().toPath();
        Files.createDirectories(directory);
        Files.writeString(directory.resolve("report.json"), report.json());
        Files.writeString(directory.resolve("report.md"), report.markdown());
        if ("true".equals(System.getenv("GITHUB_ACTIONS"))) {
            for (String line : report.annotations()) getLogger().lifecycle(line);
            String summary = System.getenv("GITHUB_STEP_SUMMARY");
            if (summary != null && !summary.isBlank())
                Files.writeString(
                        Path.of(summary),
                        report.markdown(),
                        StandardOpenOption.CREATE,
                        StandardOpenOption.APPEND);
        }
        if (!report.passed()) throw new GradleException(report.summary() + "；规则见 AGENTS.md");
    }
}
