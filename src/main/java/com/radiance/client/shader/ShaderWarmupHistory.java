package com.radiance.client.shader;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;

final class ShaderWarmupHistory {

    static final int DEFAULT_LIMIT = 128;
    private static final String HEADER = "radiance-shader-warmup-v1";
    private static final long MAX_FILE_BYTES = 1024L * 1024L;
    private static final int MAX_LINE_LENGTH = 1024;
    private static final int MAX_READ_LINES = 4096;

    record Hint(String shaderName, String drawMode) {
        Hint {
            if (shaderName == null || shaderName.isBlank()) {
                throw new IllegalArgumentException("shaderName must not be blank");
            }
            if (shaderName.length() > 768) {
                throw new IllegalArgumentException("shaderName is too long");
            }
            if (drawMode == null || drawMode.isBlank()) {
                throw new IllegalArgumentException("drawMode must not be blank");
            }
            if (drawMode.length() > 64) {
                throw new IllegalArgumentException("drawMode is too long");
            }
        }
    }

    private final int limit;
    private final LinkedHashSet<Hint> hints = new LinkedHashSet<>();
    private boolean dirty;

    ShaderWarmupHistory() {
        this(DEFAULT_LIMIT);
    }

    ShaderWarmupHistory(int limit) {
        if (limit < 1) {
            throw new IllegalArgumentException("limit must be positive");
        }
        this.limit = limit;
    }

    synchronized void record(String shaderName, String drawMode) {
        Hint hint;
        try {
            hint = new Hint(shaderName, drawMode);
        } catch (IllegalArgumentException ignored) {
            return;
        }
        boolean changed = hints.remove(hint);
        changed |= hints.add(hint);
        while (hints.size() > limit) {
            Hint oldest = hints.iterator().next();
            hints.remove(oldest);
        }
        dirty |= changed;
    }

    synchronized List<Hint> newestFirst() {
        ArrayList<Hint> result = new ArrayList<>(hints);
        Collections.reverse(result);
        return List.copyOf(result);
    }

    synchronized int size() {
        return hints.size();
    }

    static ShaderWarmupHistory load(Path path) {
        return load(path, DEFAULT_LIMIT);
    }

    static ShaderWarmupHistory load(Path path, int limit) {
        ShaderWarmupHistory result = new ShaderWarmupHistory(limit);
        try {
            if (!Files.isRegularFile(path) || Files.size(path) > MAX_FILE_BYTES) {
                return result;
            }
            try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
                if (!HEADER.equals(reader.readLine())) {
                    return result;
                }
                for (int lines = 0; lines < MAX_READ_LINES; lines++) {
                    String line = reader.readLine();
                    if (line == null) {
                        break;
                    }
                    Hint hint = decode(line);
                    if (hint != null) {
                        result.record(hint.shaderName(), hint.drawMode());
                    }
                }
            }
            result.dirty = false;
        } catch (IOException | RuntimeException ignored) {
            return new ShaderWarmupHistory(limit);
        }
        return result;
    }

    synchronized void save(Path path) throws IOException {
        if (!dirty) {
            return;
        }
        Path parent = path.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        StringBuilder content = new StringBuilder(HEADER).append('\n');
        for (Hint hint : hints) {
            content.append(encode(hint.shaderName()))
                .append('\t')
                .append(encode(hint.drawMode()))
                .append('\n');
        }
        Path temporary = path.resolveSibling(path.getFileName() + ".part-"
            + ProcessHandle.current().pid() + "-" + Thread.currentThread().threadId());
        Files.writeString(temporary, content, StandardCharsets.UTF_8);
        try {
            Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
        }
        dirty = false;
    }

    private static Hint decode(String line) {
        if (line.length() > MAX_LINE_LENGTH) {
            return null;
        }
        int separator = findSeparator(line);
        if (separator < 1 || separator == line.length() - 1) {
            return null;
        }
        try {
            return new Hint(unescape(line.substring(0, separator)),
                unescape(line.substring(separator + 1)));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static int findSeparator(String line) {
        boolean escaped = false;
        for (int i = 0; i < line.length(); i++) {
            char character = line.charAt(i);
            if (escaped) {
                escaped = false;
            } else if (character == '\\') {
                escaped = true;
            } else if (character == '\t') {
                return i;
            }
        }
        return -1;
    }

    private static String encode(String value) {
        return value.replace("\\", "\\\\")
            .replace("\t", "\\t")
            .replace("\r", "\\r")
            .replace("\n", "\\n");
    }

    private static String unescape(String value) {
        StringBuilder result = new StringBuilder(value.length());
        boolean escaped = false;
        for (int i = 0; i < value.length(); i++) {
            char character = value.charAt(i);
            if (!escaped) {
                if (character == '\\') {
                    escaped = true;
                } else {
                    result.append(character);
                }
                continue;
            }
            result.append(switch (character) {
                case '\\' -> '\\';
                case 't' -> '\t';
                case 'r' -> '\r';
                case 'n' -> '\n';
                default -> throw new IllegalArgumentException("Invalid escape");
            });
            escaped = false;
        }
        if (escaped) {
            throw new IllegalArgumentException("Trailing escape");
        }
        return result.toString();
    }
}
