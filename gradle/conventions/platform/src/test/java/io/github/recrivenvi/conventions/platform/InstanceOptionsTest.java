package io.github.recrivenvi.conventions.platform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class InstanceOptionsTest {
    @TempDir Path directory;

    private static Map<String, String> options(String... pairs) {
        Map<String, String> options = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) options.put(pairs[i], pairs[i + 1]);
        return options;
    }

    @Test
    void newInstancesGetOnlyTheListedOptions() throws IOException {
        Path file = directory.resolve("options.txt");
        InstanceOptions.write(
                file, options("onboardAccessibility", "false", "soundCategory_master", "0.5"));
        assertEquals(
                "onboardAccessibility:false\nsoundCategory_master:0.5\n", Files.readString(file));
    }

    @Test
    void otherLinesKeepTheirOrderAndRepeatedKeysCollapse() throws IOException {
        Path file = directory.resolve("options.txt");
        Files.writeString(
                file,
                "version:3955\r\nsoundCategory_master:1.0\r\nguiScale:3\r\nsoundCategory_master:0.2\r\n");
        InstanceOptions.write(file, options("soundCategory_master", "0.5", "lang", "zh_cn"));
        assertEquals(
                "version:3955\nsoundCategory_master:0.5\nguiScale:3\nlang:zh_cn\n",
                Files.readString(file));
        String written = Files.readString(file);
        InstanceOptions.write(file, options("soundCategory_master", "0.5", "lang", "zh_cn"));
        assertEquals(written, Files.readString(file));
    }

    @Test
    void nothingIsWrittenWithoutOptions() throws IOException {
        Path file = directory.resolve("options.txt");
        InstanceOptions.write(file, Map.of());
        assertFalse(Files.exists(file));
    }
}
