package io.github.recrivenvi.conventions;

import io.github.recrivenvi.configuration.TargetFacts;
import java.io.File;
import java.util.List;
import java.util.stream.Stream;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.file.Directory;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.SourceSet;

// probe 为 null 表示这个 Target 没有 src/probe/。
record TargetContext(
        Project project,
        TargetFacts facts,
        String modId,
        SourceSet main,
        SourceSet probe,
        Configuration products,
        List<InstanceRun> runs,
        List<InstanceRun> validations) {
    String probeModId() {
        return Names.probe(modId);
    }

    Provider<Directory> directory(InstanceRun run) {
        File directory = run.launch().directory();
        return project.getLayout().dir(project.getProviders().provider(() -> directory));
    }

    boolean managed(String run) {
        return Stream.concat(runs.stream(), validations.stream())
                .anyMatch(instance -> instance.name().equals(run));
    }

    boolean validation(String run) {
        return validations.stream().anyMatch(instance -> instance.name().equals(run));
    }

    Provider<Directory> buildRun(String run) {
        return project.getLayout().getBuildDirectory().dir("run/" + run);
    }
}
