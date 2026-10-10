package io.github.recrivenvi.configuration;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

final class Inputs implements Serializable {
    static final String FILE = "inputs.toml";
    private static final Pattern NAME = Pattern.compile("[a-z][a-z0-9_-]*");
    private static final Pattern SHA256 = Pattern.compile("[0-9a-f]{64}");

    private final Map<String, Input> inputs = new LinkedHashMap<>();

    Inputs(File root, Map<String, Object> declared, Map<String, Object> local) {
        Section file = new Section(FILE, "", declared);
        for (String name : file.keys()) {
            if (!NAME.matcher(name).matches())
                throw file.error(name, "应为以字母开头的小写字母、数字、_ 与 -；模组使用它的模组 ID");
            Section input = file.table(name);
            input.allow(List.of("version", "description", "source", "sha256"), "输入设置");
            String version = input.string("version");
            if (version == null || version.isBlank())
                throw input.error("version", "写明版本；模组使用元数据文件中的版本");
            String description = input.string("description");
            if (description == null || description.isBlank())
                throw input.error("description", "说明这是什么文件");
            List<String> hashes = input.strings("sha256");
            if (hashes == null || hashes.isEmpty())
                throw input.error("sha256", "至少列出一个允许的 SHA-256");
            for (String hash : hashes)
                if (!SHA256.matcher(hash).matches()) throw input.error("sha256", "应为 64 个小写十六进制字符");
            inputs.put(name, new Input(version, description, input.string("source"), hashes, null));
        }
        Section paths = new Section(DailyInstances.LOCAL_FILE, "", local).table("inputs");
        if (paths == null) return;
        for (String name : paths.keys()) {
            Input input = inputs.get(name);
            if (input == null)
                throw paths.error(
                        name,
                        "没有在 "
                                + FILE
                                + " 中登记"
                                + Suggestions.hint(name, inputs.keySet())
                                + "；输入已移除时删除这一行");
            String path = paths.string(name);
            if (path == null || path.isBlank()) throw paths.error(name, "应为文件路径");
            File location = new File(path);
            if (!location.isAbsolute()) location = new File(root, path);
            inputs.put(
                    name,
                    new Input(
                            input.version(),
                            input.description(),
                            input.source(),
                            input.sha256(),
                            location));
        }
    }

    Set<String> names() {
        return inputs.keySet();
    }

    File file(String name) {
        Input input = inputs.get(name);
        if (input == null)
            throw new ConfigurationException(
                    FILE, name, "未知的输入" + Suggestions.hint(name, inputs.keySet()));
        String key = "inputs." + name;
        String obtain = input.source() == null ? "" : "；获取方式：" + input.source();
        if (input.path() == null)
            throw new ConfigurationException(
                    DailyInstances.LOCAL_FILE, key, "写入 " + input.description() + " 的路径" + obtain);
        if (!input.path().isFile())
            throw new ConfigurationException(
                    DailyInstances.LOCAL_FILE,
                    key,
                    input.path() + " 不存在；这里应为 " + input.description() + obtain);
        String hash = sha256(input.path());
        if (!input.sha256().contains(hash))
            throw new ConfigurationException(
                    DailyInstances.LOCAL_FILE,
                    key,
                    input.path()
                            + " 的 SHA-256 为 "
                            + hash
                            + "，但 "
                            + FILE
                            + " 只允许 "
                            + String.join(", ", input.sha256()));
        String identity = identity(name, input);
        if (identity != null) throw new ConfigurationException(FILE, name, identity);
        return input.path();
    }

    File available(String name) {
        Input input = inputs.get(name);
        if (input == null || input.path() == null || !input.path().isFile()) return null;
        if (!input.sha256().contains(sha256(input.path()))) return null;
        return identity(name, input) == null ? input.path() : null;
    }

    // 模组的名称取模组 ID，版本取元数据中的版本，不自拟。
    private static String identity(String name, Input input) {
        List<ModMetadata.Mod> mods = ModMetadata.read(input.path());
        if (mods.isEmpty()) return null;
        for (ModMetadata.Mod mod : mods)
            if (name.equals(mod.id()))
                return input.version().equals(mod.version())
                        ? null
                        : input.path().getName()
                                + " 的元数据中的版本为 "
                                + mod.version()
                                + "；写 version = \""
                                + mod.version()
                                + "\"";
        ModMetadata.Mod mod = mods.getFirst();
        return input.path().getName()
                + " 是模组 "
                + mod.id()
                + " "
                + mod.version()
                + "；把输入命名为 ["
                + mod.id()
                + "]，并写 version = \""
                + mod.version()
                + "\"";
    }

    private static String sha256(File file) {
        try {
            return HexFormat.of()
                    .formatHex(
                            MessageDigest.getInstance("SHA-256")
                                    .digest(Files.readAllBytes(file.toPath())));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private record Input(
            String version, String description, String source, List<String> sha256, File path)
            implements Serializable {
        Input {
            sha256 = List.copyOf(sha256);
        }
    }
}
