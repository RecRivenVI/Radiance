package io.github.recrivenvi.configuration;

import java.util.List;
import java.util.Map;

public final class Json {
    private Json() {}

    public static String quote(String value) {
        StringBuilder builder = new StringBuilder("\"");
        for (char character : value.toCharArray()) {
            switch (character) {
                case '"' -> builder.append("\\\"");
                case '\\' -> builder.append("\\\\");
                case '\n' -> builder.append("\\n");
                case '\r' -> builder.append("\\r");
                case '\t' -> builder.append("\\t");
                default -> {
                    if (character < 0x20) builder.append("\\u%04x".formatted((int) character));
                    else builder.append(character);
                }
            }
        }
        return builder.append('"').toString();
    }

    public static String write(Object value) {
        StringBuilder builder = new StringBuilder();
        write(builder, value, 0);
        return builder.append('\n').toString();
    }

    private static void write(StringBuilder builder, Object value, int depth) {
        switch (value) {
            case null -> builder.append("null");
            case String text -> builder.append(quote(text));
            case Boolean flag -> builder.append(flag);
            case Integer number -> builder.append(number);
            case Long number -> builder.append(number);
            case Double number -> {
                if (!Double.isFinite(number))
                    throw new IllegalArgumentException("JSON 无法表示 " + number);
                builder.append(number);
            }
            case Map<?, ?> map -> {
                if (map.isEmpty()) {
                    builder.append("{}");
                    return;
                }
                builder.append("{\n");
                int index = 0;
                for (Map.Entry<?, ?> entry : map.entrySet()) {
                    builder.append("    ".repeat(depth + 1))
                            .append(quote(entry.getKey().toString()))
                            .append(": ");
                    write(builder, entry.getValue(), depth + 1);
                    builder.append(++index < map.size() ? ",\n" : "\n");
                }
                builder.append("    ".repeat(depth)).append('}');
            }
            case List<?> list -> {
                if (list.stream().noneMatch(item -> item instanceof Map || item instanceof List)) {
                    builder.append('[');
                    for (int i = 0; i < list.size(); i++) {
                        if (i > 0) builder.append(", ");
                        write(builder, list.get(i), depth);
                    }
                    builder.append(']');
                    return;
                }
                builder.append("[\n");
                for (int i = 0; i < list.size(); i++) {
                    builder.append("    ".repeat(depth + 1));
                    write(builder, list.get(i), depth + 1);
                    builder.append(i + 1 < list.size() ? ",\n" : "\n");
                }
                builder.append("    ".repeat(depth)).append(']');
            }
            default ->
                    throw new IllegalArgumentException("JSON 无法表示 " + value.getClass().getName());
        }
    }
}
