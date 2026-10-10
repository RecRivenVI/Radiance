package io.github.recrivenvi.conventions;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.gradle.api.GradleException;

// 实例的 mods/ 也可能放着使用者自己的文件，所以只覆盖或删除清单中记录、且内容未被改动的副本。
final class InstanceMods {
    static final String MANIFEST = ".managed-mods";
    private static final Pattern ENTRY = Pattern.compile("([0-9a-f]{64})  (.+)");

    private InstanceMods() {}

    // inputs 为输入名称到文件的映射；返回被使用者改动过、因此不再管理也不删除的文件名。
    static List<String> sync(Path instance, Map<String, Path> inputs) throws IOException {
        Path mods = instance.resolve("mods").toAbsolutePath().normalize();
        Path manifest = instance.resolve(MANIFEST);
        Map<String, Entry> previous = read(manifest);
        Map<String, Copy> copies = plan(mods, inputs);
        conflicts(mods, previous, copies);

        if (!copies.isEmpty()) Files.createDirectories(mods);
        // 文件名只改了大小写时，区分大小写的文件系统上旧副本是另一个文件，不先删除就会留下两个模组；
        // 不区分大小写时它就是目标文件，删除后按新名称复制。
        for (Map.Entry<String, Copy> copy : copies.entrySet()) {
            Entry managed = previous.get(copy.getKey());
            if (renamed(managed, copy.getValue()))
                Files.deleteIfExists(mods.resolve(managed.name()));
        }
        for (Copy copy : copies.values()) {
            Path target = mods.resolve(copy.name());
            if (Files.isRegularFile(target) && sha256(target).equals(copy.hash())) continue;
            Path part = mods.resolve("." + copy.name() + ".part");
            Files.copy(copy.source(), part, StandardCopyOption.REPLACE_EXISTING);
            Files.move(part, target, StandardCopyOption.REPLACE_EXISTING);
        }
        List<String> released = new ArrayList<>();
        for (Map.Entry<String, Entry> entry : previous.entrySet()) {
            if (copies.containsKey(entry.getKey())) continue;
            Path target = mods.resolve(entry.getValue().name());
            if (!Files.isRegularFile(target)) continue;
            if (sha256(target).equals(entry.getValue().hash())) Files.delete(target);
            else released.add(entry.getValue().name());
        }
        write(manifest, copies);
        return released;
    }

    private static Map<String, Copy> plan(Path mods, Map<String, Path> inputs) {
        Map<String, Copy> copies = new LinkedHashMap<>();
        List<String> problems = new ArrayList<>();
        for (Map.Entry<String, Path> input : inputs.entrySet()) {
            Path source = input.getValue().toAbsolutePath().normalize();
            if (source.startsWith(mods)) {
                problems.add(input.getKey() + " 的文件位于实例的 mods 文件夹中；把它移到实例之外，并在 local.toml 中更新路径");
                continue;
            }
            String name = source.getFileName().toString();
            Copy copy = new Copy(input.getKey(), source, name, sha256(source));
            Copy other = copies.putIfAbsent(key(name), copy);
            if (other != null)
                problems.add(
                        other.input()
                                + " 与 "
                                + input.getKey()
                                + " 的文件名都是 "
                                + name
                                + "，复制进同一个 mods 文件夹会互相覆盖；把其中一个文件改名，并在 local.toml 中更新路径");
        }
        if (!problems.isEmpty()) throw new GradleException(String.join("\n", problems));
        return copies;
    }

    private static void conflicts(Path mods, Map<String, Entry> previous, Map<String, Copy> copies)
            throws IOException {
        List<String> problems = new ArrayList<>();
        for (Map.Entry<String, Copy> copy : copies.entrySet()) {
            Path target = mods.resolve(copy.getValue().name());
            Entry managed = previous.get(copy.getKey());
            if (renamed(managed, copy.getValue())) {
                Path old = mods.resolve(managed.name());
                if (Files.exists(old) && !sameFile(old, target)) {
                    if (!Files.isRegularFile(old))
                        problems.add("mods/" + managed.name() + " 不是文件；移走它后重试");
                    else if (!sha256(old).equals(managed.hash()))
                        problems.add("mods/" + managed.name() + " 在放入后被改动过；为避免覆盖，移走它后重试");
                }
            }
            if (!Files.exists(target)) continue;
            if (!Files.isRegularFile(target)) {
                problems.add("mods/" + copy.getValue().name() + " 不是文件；移走它后重试");
                continue;
            }
            String hash = sha256(target);
            if (hash.equals(copy.getValue().hash())
                    || (managed != null && hash.equals(managed.hash()))) continue;
            problems.add(
                    "mods/"
                            + copy.getValue().name()
                            + (managed == null ? " 已存在，不是由实例设置 mods 放入的" : " 在放入后被改动过")
                            + "；为避免覆盖，移走它后重试");
        }
        if (!problems.isEmpty()) throw new GradleException(String.join("\n", problems));
    }

    private static Map<String, Entry> read(Path manifest) throws IOException {
        Map<String, Entry> entries = new LinkedHashMap<>();
        if (!Files.isRegularFile(manifest)) return entries;
        for (String line : Files.readAllLines(manifest, StandardCharsets.UTF_8)) {
            Matcher entry = ENTRY.matcher(line);
            if (entry.matches())
                entries.put(key(entry.group(2)), new Entry(entry.group(2), entry.group(1)));
        }
        return entries;
    }

    private static void write(Path manifest, Map<String, Copy> copies) throws IOException {
        if (copies.isEmpty()) {
            Files.deleteIfExists(manifest);
            return;
        }
        Map<String, String> lines = new TreeMap<>();
        for (Copy copy : copies.values()) lines.put(copy.name(), copy.hash() + "  " + copy.name());
        Files.write(manifest, lines.values(), StandardCharsets.UTF_8);
    }

    // Windows 与 macOS 的文件名不区分大小写，按小写比较才能发现会互相覆盖的文件。
    private static String key(String name) {
        return name.toLowerCase(Locale.ROOT);
    }

    private static boolean renamed(Entry managed, Copy copy) {
        return managed != null && !managed.name().equals(copy.name());
    }

    private static boolean sameFile(Path file, Path other) throws IOException {
        return Files.exists(other) && Files.isSameFile(file, other);
    }

    static String sha256(Path file) {
        try {
            return HexFormat.of()
                    .formatHex(
                            MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file)));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private record Copy(String input, Path source, String name, String hash) {}

    private record Entry(String name, String hash) {}
}
