package io.github.recrivenvi.configuration;

import java.util.List;
import java.util.regex.Pattern;

final class Values {
    private static final Pattern MEMORY = Pattern.compile("[1-9][0-9]*[KMG]");
    private static final List<String> MANAGED_JVM_OPTIONS = List.of("-Xms", "-Xmx");
    private static final List<String> REMOVED_JVM_OPTIONS =
            List.of("-XX:PermSize=", "-XX:MaxPermSize=");
    private static final List<String> MANAGED_GAME_OPTIONS =
            List.of("--width", "--height", "--username", "--gameDir", "--port", "--nogui", "nogui");

    private Values() {}

    static String memory(Section section, String key) {
        String value = required(section, key, section.string(key));
        if (!MEMORY.matcher(value).matches()) throw section.error(key, "应为正整数加 K、M 或 G，例如 4G");
        if (bytes(value) < 0) throw section.error(key, value + " 超出可以表示的范围");
        return value;
    }

    // 换算为字节数；超出 long 的范围时返回 -1。
    static long bytes(String memory) {
        int shift =
                switch (memory.charAt(memory.length() - 1)) {
                    case 'K' -> 10;
                    case 'M' -> 20;
                    default -> 30;
                };
        try {
            long amount = Long.parseLong(memory.substring(0, memory.length() - 1));
            return Math.multiplyExact(amount, 1L << shift);
        } catch (NumberFormatException | ArithmeticException e) {
            return -1;
        }
    }

    static List<String> jvmArguments(Section section, String key) {
        List<String> values = required(section, key, section.strings(key));
        for (String value : values) {
            for (String option : MANAGED_JVM_OPTIONS)
                if (value.startsWith(option))
                    throw section.error(key, value + " 与实例设置重复；改用 memory-min 或 memory-max");
            for (String option : REMOVED_JVM_OPTIONS)
                if (value.startsWith(option))
                    throw section.error(key, value + " 已在 Java 8 中移除，Java 17 及以后遇到它会拒绝启动");
        }
        return values;
    }

    static List<String> gameArguments(Section section, String key) {
        List<String> values = required(section, key, section.strings(key));
        for (String value : values)
            for (String option : MANAGED_GAME_OPTIONS)
                if (value.equals(option) || value.startsWith(option + "="))
                    throw section.error(key, option + " 由构建设置；改用对应的实例设置");
        return values;
    }

    private static <T> T required(Section section, String key, T value) {
        if (value == null) throw section.error(key, "缺少取值");
        return value;
    }
}
