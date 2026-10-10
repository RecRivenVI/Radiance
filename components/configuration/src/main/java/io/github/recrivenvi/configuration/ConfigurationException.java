package io.github.recrivenvi.configuration;

public final class ConfigurationException extends IllegalArgumentException {
    ConfigurationException(String file, String key, String message) {
        super(key == null ? file + ": " + message : file + " [" + key + "]: " + message);
    }
}
