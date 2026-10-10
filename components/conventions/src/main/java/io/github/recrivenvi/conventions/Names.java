package io.github.recrivenvi.conventions;

final class Names {
    private Names() {}

    static String run(String instance) {
        StringBuilder builder = new StringBuilder();
        for (String part : instance.split("-"))
            builder.append(
                    builder.isEmpty()
                            ? part
                            : Character.toUpperCase(part.charAt(0)) + part.substring(1));
        return builder.toString();
    }

    static String probe(String modId) {
        return modId + "_probe";
    }

    static String validation(String validation, String role) {
        return task("validation" + validation, run(role));
    }

    static String task(String prefix, String name) {
        return prefix + Character.toUpperCase(name.charAt(0)) + name.substring(1);
    }
}
