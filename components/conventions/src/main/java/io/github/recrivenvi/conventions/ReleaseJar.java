package io.github.recrivenvi.conventions;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

// 发行 JAR 的内容核对：加载器元数据、许可文件与 product 组件都在，探针不在；metadata 为 null 时不核对元数据。
// 以加载器原生的引导层包装模组时，元数据可以位于发行 JAR 中恰好一个内嵌 JAR 里。
final class ReleaseJar {
    private ReleaseJar() {}

    static List<String> problems(
            Path jar,
            String metadata,
            String probeId,
            Collection<String> required,
            Collection<String> forbidden)
            throws IOException {
        List<String> problems = new ArrayList<>();
        try (ZipFile zip = new ZipFile(jar.toFile())) {
            Set<String> entries = new TreeSet<>();
            zip.stream()
                    .filter(entry -> !entry.isDirectory())
                    .forEach(e -> entries.add(e.getName()));
            if (metadata != null) {
                ZipEntry entry = zip.getEntry(metadata);
                List<String> found = new ArrayList<>();
                if (entry == null) found.addAll(nested(zip, metadata));
                else
                    try (InputStream input = zip.getInputStream(entry)) {
                        found.add(text(input));
                    }
                if (found.isEmpty()) problems.add("缺少加载器元数据 " + metadata);
                else if (found.size() > 1)
                    problems.add(found.size() + " 个内嵌 JAR 含有 " + metadata + "；发行 JAR 只能包装一个模组");
                else if (found.getFirst().contains(probeId))
                    problems.add(metadata + " 是探针 " + probeId + " 的元数据");
            }
            for (String name : new TreeSet<>(required))
                if (!entries.contains(name)) problems.add("缺少 " + name);
            for (String name : new TreeSet<>(forbidden))
                if (entries.contains(name)) problems.add("含有探针的 " + name);
        }
        return problems;
    }

    // 没有提供 JAR 的 product 组件不会合并进模组 JAR，它的内容会静默缺失。
    static List<String> unpackaged(Collection<String> registered, Collection<String> packaged) {
        List<String> problems = new ArrayList<>();
        for (String name : new TreeSet<>(registered))
            if (!packaged.contains(name))
                problems.add(
                        "product 组件 "
                                + name
                                + " 没有提供 JAR；在 components/"
                                + name
                                + "/build.gradle.kts 中应用 java 或 java-library");
        return problems;
    }

    private static List<String> nested(ZipFile zip, String metadata) throws IOException {
        List<String> found = new ArrayList<>();
        for (ZipEntry jar : zip.stream().filter(e -> e.getName().endsWith(".jar")).toList())
            try (ZipInputStream input = new ZipInputStream(zip.getInputStream(jar))) {
                for (ZipEntry entry; (entry = input.getNextEntry()) != null; )
                    if (entry.getName().equals(metadata)) found.add(text(input));
            }
        return found;
    }

    private static String text(InputStream input) throws IOException {
        return new String(input.readAllBytes(), StandardCharsets.UTF_8);
    }

    // product 组件合并进模组 JAR 时不带入组件自己的清单与模块描述。
    static final String MANIFEST = "META-INF/MANIFEST.MF";
    static final String MODULE_INFO = "module-info.class";

    static boolean merged(String entry) {
        return !entry.endsWith("/")
                && !entry.equals(MANIFEST)
                && !entry.equals(MODULE_INFO)
                && !entry.endsWith("/" + MODULE_INFO);
    }
}
