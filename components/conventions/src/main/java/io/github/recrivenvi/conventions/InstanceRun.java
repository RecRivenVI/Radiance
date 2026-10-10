package io.github.recrivenvi.conventions;

import io.github.recrivenvi.configuration.LaunchSettings;
import java.io.Serializable;
import java.util.Map;

record InstanceRun(
        String name, boolean client, LaunchSettings launch, Map<String, String> properties)
        implements Serializable {
    InstanceRun {
        properties = Map.copyOf(properties);
    }

    String taskName() {
        return Names.task("run", name);
    }

    String prepareTaskName() {
        return Names.task("prepare", name) + "Instance";
    }
}
