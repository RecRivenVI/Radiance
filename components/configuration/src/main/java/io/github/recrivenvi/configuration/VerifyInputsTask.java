package io.github.recrivenvi.configuration;

import java.util.ArrayList;
import java.util.List;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.SetProperty;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.UntrackedTask;

@UntrackedTask(because = "读取仓库之外的文件")
public abstract class VerifyInputsTask extends DefaultTask {
    @Internal
    public abstract Property<RepositoryConfiguration> getConfiguration();

    @Input
    public abstract SetProperty<String> getNames();

    @TaskAction
    public void verify() {
        RepositoryConfiguration configuration = getConfiguration().get();
        List<String> problems = new ArrayList<>();
        for (String name : getNames().get()) {
            try {
                getLogger().lifecycle("{}: {}", name, configuration.input(name));
            } catch (ConfigurationException e) {
                problems.add(e.getMessage());
            }
        }
        if (getNames().get().isEmpty()) getLogger().lifecycle("没有需要核对的本地输入");
        if (!problems.isEmpty())
            throw new GradleException(
                    String.join("\n", problems)
                            + "\n拿不到这些文件的环境（例如持续集成）改为运行 checkRepository，它不读取本地输入（W-03）");
    }
}
