package io.github.recrivenvi.conventions.platform;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// 把实例设置 options 写进客户端的 options.txt：只改动列出的键，其余行与顺序原样保留。
final class InstanceOptions {
    private InstanceOptions() {}

    static void write(Path file, Map<String, String> options) throws IOException {
        if (options.isEmpty()) return;
        String previous = Files.exists(file) ? Files.readString(file, StandardCharsets.UTF_8) : "";
        Map<String, String> pending = new LinkedHashMap<>(options);
        List<String> lines = new ArrayList<>();
        for (String line : previous.lines().toList()) {
            int separator = line.indexOf(':');
            String key = separator < 0 ? line : line.substring(0, separator);
            if (!options.containsKey(key)) lines.add(line);
            // 同一个键出现多次时游戏取最后一处，这里写在第一处并删掉其余的。
            else if (pending.containsKey(key)) lines.add(key + ":" + pending.remove(key));
        }
        pending.forEach((key, value) -> lines.add(key + ":" + value));
        String updated = String.join("\n", lines) + "\n";
        if (!updated.equals(previous)) Files.writeString(file, updated, StandardCharsets.UTF_8);
    }
}
