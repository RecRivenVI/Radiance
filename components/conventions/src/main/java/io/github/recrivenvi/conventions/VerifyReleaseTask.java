package io.github.recrivenvi.conventions;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.UntrackedTask;

@UntrackedTask(because = "核对 collectRelease 收集的文件，不生成输出")
public abstract class VerifyReleaseTask extends DefaultTask {
    @Internal
    public abstract DirectoryProperty getDirectory();

    @Input
    public abstract ListProperty<String> getExpected();

    @TaskAction
    public void verify() {
        Set<String> found = new TreeSet<>();
        File[] files = getDirectory().get().getAsFile().listFiles(File::isFile);
        if (files != null) for (File file : files) found.add(file.getName());
        List<String> problems = new ArrayList<>();
        for (String name : getExpected().get()) if (!found.remove(name)) problems.add("缺少 " + name);
        for (String name : found) problems.add("多出 " + name);
        if (!problems.isEmpty())
            throw new GradleException(
                    getDirectory().get().getAsFile() + "：\n" + String.join("\n", problems));
        getLogger()
                .lifecycle(
                        "{} 个 Target 的发行 JAR 均已收集并核对：{}",
                        getExpected().get().size(),
                        getDirectory().get().getAsFile());
    }
}
