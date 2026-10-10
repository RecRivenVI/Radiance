package io.github.recrivenvi.configuration;

import java.io.IOException;
import java.io.StringReader;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;

final class PropertiesFile {
    private PropertiesFile() {}

    static Map<String, String> parse(String text, String file) {
        Properties properties =
                new Properties() {
                    @Override
                    public synchronized Object put(Object key, Object value) {
                        if (containsKey(key))
                            throw new ConfigurationException(file, key.toString(), "重复的键");
                        return super.put(key, value);
                    }
                };
        try {
            properties.load(new StringReader(text));
        } catch (IOException e) {
            throw new IllegalStateException(e);
        } catch (IllegalArgumentException e) {
            if (e instanceof ConfigurationException) throw e;
            throw new ConfigurationException(file, null, e.getMessage());
        }
        Map<String, String> values = new TreeMap<>();
        for (String key : properties.stringPropertyNames())
            values.put(key, properties.getProperty(key));
        return values;
    }
}
