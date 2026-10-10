package io.github.recrivenvi.configuration;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.jar.Manifest;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;
import org.tomlj.Toml;
import org.tomlj.TomlArray;
import org.tomlj.TomlTable;

final class ModMetadata {
    private static final List<String> TOML_FILES =
            List.of("META-INF/neoforge.mods.toml", "META-INF/mods.toml");

    private ModMetadata() {}

    record Mod(String id, String version) {}

    // 返回 JAR 声明的模组；文件不是模组时返回空列表。
    static List<Mod> read(File file) {
        try (ZipFile zip = new ZipFile(file)) {
            List<Mod> mods = new ArrayList<>();
            for (String name : TOML_FILES) {
                String text = text(zip, name);
                if (text == null) continue;
                TomlArray entries = Toml.parse(text).getArray("mods");
                if (entries == null) continue;
                for (int i = 0; i < entries.size(); i++) {
                    TomlTable mod = entries.getTable(i);
                    String version = mod.getString("version");
                    if ("${file.jarVersion}".equals(version)) version = manifestVersion(zip);
                    mods.add(new Mod(mod.getString("modId"), version));
                }
            }
            String fabric = text(zip, "fabric.mod.json");
            if (fabric != null) {
                Map<String, String> values = topLevelStrings(fabric);
                mods.add(new Mod(values.get("id"), values.get("version")));
            }
            return mods;
        } catch (ZipException e) {
            return List.of();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String text(ZipFile zip, String name) throws IOException {
        ZipEntry entry = zip.getEntry(name);
        if (entry == null) return null;
        try (InputStream input = zip.getInputStream(entry)) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static String manifestVersion(ZipFile zip) throws IOException {
        ZipEntry entry = zip.getEntry("META-INF/MANIFEST.MF");
        if (entry == null) return null;
        try (InputStream input = zip.getInputStream(entry)) {
            return new Manifest(input).getMainAttributes().getValue("Implementation-Version");
        }
    }

    static Map<String, String> topLevelStrings(String json) {
        Map<String, String> values = new LinkedHashMap<>();
        int depth = 0;
        String key = null;
        boolean value = false;
        for (int i = 0; i < json.length(); i++) {
            char c = json.charAt(i);
            if (c == '"') {
                StringBuilder text = new StringBuilder();
                int end = i + 1;
                while (end < json.length() && json.charAt(end) != '"') {
                    if (json.charAt(end) == '\\') end++;
                    if (end < json.length()) text.append(json.charAt(end));
                    end++;
                }
                i = end;
                if (depth != 1) continue;
                if (value && key != null) values.putIfAbsent(key, text.toString());
                else key = text.toString();
                value = false;
            } else if (c == '{' || c == '[') {
                depth++;
                if (depth > 1) value = false;
            } else if (c == '}' || c == ']') {
                depth--;
            } else if (c == ':' && depth == 1) {
                value = true;
            } else if (c == ',' && depth == 1) {
                key = null;
                value = false;
            }
        }
        return values;
    }
}
