package io.github.recrivenvi.configuration;

import java.time.temporal.TemporalAccessor;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.tomlj.Toml;
import org.tomlj.TomlArray;
import org.tomlj.TomlParseError;
import org.tomlj.TomlParseResult;
import org.tomlj.TomlTable;

final class TomlFile {
    private TomlFile() {}

    static Map<String, Object> parse(String text, String file) {
        TomlParseResult result = Toml.parse(text);
        if (result.hasErrors()) {
            TomlParseError error = result.errors().getFirst();
            throw new ConfigurationException(
                    file,
                    null,
                    "第 "
                            + error.position().line()
                            + " 行第 "
                            + error.position().column()
                            + " 列："
                            + error.getMessage());
        }
        return table(result);
    }

    private static Map<String, Object> table(TomlTable table) {
        Map<String, Object> values = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : table.entrySet())
            values.put(entry.getKey(), value(entry.getValue()));
        return values;
    }

    private static Object value(Object value) {
        if (value instanceof TomlTable table) return table(table);
        if (value instanceof TomlArray array) {
            List<Object> values = new ArrayList<>();
            for (int i = 0; i < array.size(); i++) values.add(value(array.get(i)));
            return values;
        }
        if (value instanceof TemporalAccessor) return value.toString();
        return value;
    }
}
