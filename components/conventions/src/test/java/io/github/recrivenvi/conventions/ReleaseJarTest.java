package io.github.recrivenvi.conventions;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ReleaseJarTest {
    private static final String METADATA = "META-INF/neoforge.mods.toml";
    private static final String PRODUCT = "modId = \"examplemod\"\n";
    private static final List<String> REQUIRED =
            List.of("META-INF/LICENSE", "META-INF/licenses/gson-apache_2.0.txt", "a/b/Curve.class");
    private static final List<String> FORBIDDEN = List.of("a/b/probe/Probe.class");

    @TempDir Path directory;

    private Path jar(Map<String, String> entries) throws IOException {
        Path jar = directory.resolve("examplemod.jar");
        try (OutputStream file = Files.newOutputStream(jar);
                ZipOutputStream zip = new ZipOutputStream(file)) {
            for (Map.Entry<String, String> entry : entries.entrySet()) {
                zip.putNextEntry(new ZipEntry(entry.getKey()));
                zip.write(
                        entry.getValue()
                                .getBytes(
                                        entry.getKey().endsWith(".jar")
                                                ? StandardCharsets.ISO_8859_1
                                                : StandardCharsets.UTF_8));
                zip.closeEntry();
            }
        }
        return jar;
    }

    private Map<String, String> complete() {
        Map<String, String> entries = new LinkedHashMap<>();
        entries.put(METADATA, PRODUCT);
        for (String name : REQUIRED) entries.put(name, "x");
        return entries;
    }

    private List<String> problems(Map<String, String> entries) throws IOException {
        return ReleaseJar.problems(jar(entries), METADATA, "examplemod_probe", REQUIRED, FORBIDDEN);
    }

    @Test
    void aCompleteJarPasses() throws IOException {
        assertEquals(List.of(), problems(complete()));
    }

    @Test
    void reportsMissingMetadataAndEntries() throws IOException {
        Map<String, String> entries = complete();
        entries.remove(METADATA);
        entries.remove("META-INF/LICENSE");
        assertEquals(List.of("缺少加载器元数据 " + METADATA, "缺少 META-INF/LICENSE"), problems(entries));
    }

    @Test
    void reportsProbeContent() throws IOException {
        Map<String, String> entries = complete();
        entries.put(METADATA, "modId = \"examplemod_probe\"\n");
        entries.put("a/b/probe/Probe.class", "x");
        assertEquals(
                List.of(METADATA + " 是探针 examplemod_probe 的元数据", "含有探针的 a/b/probe/Probe.class"),
                problems(entries));
    }

    @Test
    void metadataMayLiveInOneNestedJar() throws IOException {
        Map<String, String> entries = complete();
        entries.remove(METADATA);
        entries.put("META-INF/wrapper/examplemod-game.jar", nested(METADATA, PRODUCT));
        assertEquals(List.of(), problems(entries));
        entries.put("META-INF/wrapper/other.jar", nested(METADATA, PRODUCT));
        assertEquals(List.of("2 个内嵌 JAR 含有 " + METADATA + "；发行 JAR 只能包装一个模组"), problems(entries));
        entries.remove("META-INF/wrapper/other.jar");
        entries.put(
                "META-INF/wrapper/examplemod-game.jar",
                nested(METADATA, "modId = \"examplemod_probe\"\n"));
        assertEquals(List.of(METADATA + " 是探针 examplemod_probe 的元数据"), problems(entries));
    }

    // 内嵌 JAR 的字节以 ISO-8859-1 存进字符串，jar() 写出 .jar 条目时按同一编码还原。
    private static String nested(String name, String content) throws IOException {
        java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            zip.putNextEntry(new ZipEntry(name));
            zip.write(content.getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        return bytes.toString(StandardCharsets.ISO_8859_1);
    }

    @Test
    void everyProductComponentProvidesAJar() {
        assertEquals(
                List.of(),
                ReleaseJar.unpackaged(List.of("curves", "shaders"), List.of("shaders", "curves")));
        assertEquals(
                List.of(
                        "product 组件 shaders 没有提供 JAR；在 components/shaders/build.gradle.kts 中应用"
                                + " java 或 java-library"),
                ReleaseJar.unpackaged(List.of("curves", "shaders"), List.of("curves")));
    }

    @Test
    void productEntriesFollowTheMergeRules() {
        assertTrue(ReleaseJar.merged("a/b/Curve.class"));
        assertTrue(ReleaseJar.merged("assets/examplemod/shaders/core.fsh"));
        assertFalse(ReleaseJar.merged("META-INF/MANIFEST.MF"));
        assertFalse(ReleaseJar.merged("module-info.class"));
        assertFalse(ReleaseJar.merged("META-INF/versions/21/module-info.class"));
        assertTrue(ReleaseJar.merged("a/b/module-info.classes.txt"));
        assertTrue(ReleaseJar.merged("a/b/legacy-module-info.class"));
        assertFalse(ReleaseJar.merged("a/b/"));
    }
}
