package io.github.recrivenvi.configuration;

import java.io.File;
import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record LaunchSettings(
        File directory, List<String> jvmArguments, List<String> gameArguments, List<String> mods)
        implements Serializable {
    public LaunchSettings {
        jvmArguments = List.copyOf(jvmArguments);
        gameArguments = List.copyOf(gameArguments);
        mods = List.copyOf(mods);
    }

    Map<String, Object> toJson(File root) {
        Map<String, Object> json = new LinkedHashMap<>();
        json.put(
                "directory",
                root.toPath().relativize(directory.toPath()).toString().replace('\\', '/'));
        json.put("jvmArguments", jvmArguments);
        json.put("gameArguments", gameArguments);
        json.put("mods", mods);
        return json;
    }
}
