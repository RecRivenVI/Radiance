package io.github.recrivenvi.configuration;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

final class Section {
    private static final Pattern BARE_KEY = Pattern.compile("[A-Za-z0-9_-]+");

    private final String file;
    private final String path;
    private final Map<String, Object> values;

    Section(String file, String path, Map<String, Object> values) {
        this.file = file;
        this.path = path;
        this.values = values;
    }

    String file() {
        return file;
    }

    String path() {
        return path;
    }

    Set<String> keys() {
        return values.keySet();
    }

    boolean has(String key) {
        return values.containsKey(key);
    }

    String key(String key) {
        String segment = BARE_KEY.matcher(key).matches() ? key : Json.quote(key);
        return path.isEmpty() ? segment : path + "." + segment;
    }

    ConfigurationException error(String key, String message) {
        return new ConfigurationException(
                file, key == null ? emptyToNull(path) : key(key), message);
    }

    void allow(Collection<String> allowed, String description) {
        for (String key : values.keySet())
            if (!allowed.contains(key))
                throw error(key, "未知的" + description + Suggestions.hint(key, allowed));
    }

    String string(String key) {
        Object value = values.get(key);
        if (value == null) return null;
        if (value instanceof String text) return text;
        throw error(key, "应为字符串");
    }

    Long integer(String key) {
        Object value = values.get(key);
        if (value == null) return null;
        if (value instanceof Long number) return number;
        throw error(key, "应为整数");
    }

    int integer(String key, long minimum, long maximum) {
        long value = integer(key);
        if (value < minimum || value > maximum)
            throw error(key, "应为 " + minimum + " 到 " + maximum + " 之间的整数");
        return (int) value;
    }

    Boolean bool(String key) {
        Object value = values.get(key);
        if (value == null) return null;
        if (value instanceof Boolean flag) return flag;
        throw error(key, "应为 true 或 false");
    }

    List<String> strings(String key) {
        Object value = values.get(key);
        if (value == null) return null;
        List<String> strings = new ArrayList<>();
        if (value instanceof List<?> list) {
            for (Object item : list) {
                if (!(item instanceof String text)) break;
                strings.add(text);
            }
            if (strings.size() == list.size()) return strings;
        }
        throw error(key, "应为字符串数组");
    }

    Section table(String key) {
        Object value = values.get(key);
        if (value == null) return null;
        if (value instanceof Map<?, ?> map) return new Section(file, key(key), cast(map));
        throw error(key, "应为表");
    }

    List<Section> tables(String key) {
        Object value = values.get(key);
        if (value == null) return null;
        List<Section> tables = new ArrayList<>();
        if (value instanceof List<?> list) {
            for (Object item : list) {
                if (!(item instanceof Map<?, ?> map)) break;
                tables.add(
                        new Section(file, key(key) + "[" + (tables.size() + 1) + "]", cast(map)));
            }
            if (tables.size() == list.size()) return tables;
        }
        throw error(key, "应为表数组");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> cast(Map<?, ?> map) {
        return (Map<String, Object>) map;
    }

    private static String emptyToNull(String value) {
        return value.isEmpty() ? null : value;
    }
}
