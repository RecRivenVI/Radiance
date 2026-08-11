package com.radiance.client.shader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ShaderWarmupHistoryTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void keepsOnlyMostRecentUniqueHintsWithinLimit() {
        ShaderWarmupHistory history = new ShaderWarmupHistory(3);
        history.record("a", "QUADS");
        history.record("b", "TRIANGLES");
        history.record("c", "LINES");
        history.record("a", "QUADS");
        history.record("d", "QUADS");

        assertEquals(3, history.size());
        assertEquals(List.of(
            new ShaderWarmupHistory.Hint("d", "QUADS"),
            new ShaderWarmupHistory.Hint("a", "QUADS"),
            new ShaderWarmupHistory.Hint("c", "LINES")), history.newestFirst());
    }

    @Test
    void roundTripsUtf8AndEscapedSeparators() throws Exception {
        Path path = temporaryDirectory.resolve("cache/warmup/shader-variants-v1.txt");
        ShaderWarmupHistory history = new ShaderWarmupHistory();
        ShaderWarmupHistory.Hint expected = new ShaderWarmupHistory.Hint(
            "example:着色器\\variant\tline\nnext", "TRIANGLES");
        history.record(expected.shaderName(), expected.drawMode());
        history.save(path);

        assertTrue(Files.readString(path, StandardCharsets.UTF_8).contains("着色器"));
        ShaderWarmupHistory loaded = ShaderWarmupHistory.load(path);
        assertEquals(List.of(expected), loaded.newestFirst());
    }

    @Test
    void ignoresCorruptLinesAndOversizedFiles() throws Exception {
        Path path = temporaryDirectory.resolve("history.txt");
        Files.writeString(path, """
            radiance-shader-warmup-v1
            position_color\tQUADS
            missing-separator
            trailing\\\tQUADS
            position_tex\t
            """, StandardCharsets.UTF_8);

        ShaderWarmupHistory loaded = ShaderWarmupHistory.load(path);
        assertEquals(List.of(new ShaderWarmupHistory.Hint("position_color", "QUADS")),
            loaded.newestFirst());

        Path oversized = temporaryDirectory.resolve("oversized.txt");
        Files.writeString(oversized, "x".repeat(1024 * 1024 + 1), StandardCharsets.UTF_8);
        assertEquals(0, ShaderWarmupHistory.load(oversized).size());
    }

    @Test
    void buildingWarmupCandidatesDoesNotRecordSyntheticUse() throws Exception {
        ShaderWarmupHistory history = new ShaderWarmupHistory();
        history.record("thirdparty:actual", "TRIANGLE_STRIP");
        history.save(temporaryDirectory.resolve("actual-history.txt"));
        List<ShaderWarmupHistory.Hint> before = history.newestFirst();

        List<ShaderWarmupHistory.Hint> candidates = ShaderRegistry.buildWarmupHints(history);

        assertEquals(before, history.newestFirst());
        assertEquals(1, history.size());
        assertTrue(candidates.contains(new ShaderWarmupHistory.Hint("position_color", "QUADS")));
        assertTrue(candidates.contains(new ShaderWarmupHistory.Hint("position_tex", "QUADS")));
        assertTrue(candidates.contains(new ShaderWarmupHistory.Hint("position_tex_color", "QUADS")));
        assertTrue(candidates.contains(before.getFirst()));
        assertFalse(candidates.isEmpty());
        Path syntheticWrite = temporaryDirectory.resolve("synthetic-history.txt");
        history.save(syntheticWrite);
        assertFalse(Files.exists(syntheticWrite));
    }
}
