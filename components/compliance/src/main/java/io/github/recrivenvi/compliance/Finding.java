package io.github.recrivenvi.compliance;

public record Finding(Rule rule, String path, int line, String message) {
    public static Finding of(Rule rule, String path, String message) {
        return new Finding(rule, path, 0, message);
    }

    public String location() {
        return line > 0 ? path + ":" + line : path;
    }
}
