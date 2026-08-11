package com.radiance.client.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import com.mojang.blaze3d.vertex.VertexFormat;
import org.junit.jupiter.api.Test;

class WorldMeshSinkContractTest {

    @Test
    void explicitLineRequestsMustReachWorldReceiverAdmission() throws Exception {
        var admission =
                WorldMeshSink.class.getDeclaredMethod("isSurfaceMode", VertexFormat.Mode.class);
        admission.setAccessible(true);
        for (var mode :
                java.util.List.of(
                        VertexFormat.Mode.LINES,
                        VertexFormat.Mode.LINE_STRIP,
                        VertexFormat.Mode.DEBUG_LINES,
                        VertexFormat.Mode.DEBUG_LINE_STRIP)) {
            assertEquals(true, admission.invoke(null, mode), "world receiver rejects " + mode);
        }
    }

    @Test
    void mapsDimensionAndNeoForgeStageNamesWithoutGuessingCoordinates() {
        assertEquals(
                WorldMeshSink.StageKey.AFTER_PARTICLES,
                WorldMeshSink.StageKey.from("after_particles"));
        assertEquals(
                WorldMeshSink.StageKey.DIMENSION_RENDER_SKY,
                WorldMeshSink.StageKey.from("renderSky"));
        assertEquals(
                WorldMeshSink.StageKey.DIMENSION_RENDER_WEATHER,
                WorldMeshSink.StageKey.from("renderSnowAndRain"));
        assertEquals(
                WorldMeshSink.StageKey.UNKNOWN,
                WorldMeshSink.StageKey.from("third_party_unknown_stage"));
    }

    @Test
    void sourceIdentityIncludesStableSemanticAndOwnerIdentity() {
        Object ownerA = new Object();
        Object ownerB = new Object();
        int first = WorldMeshSink.stableSourceId("create/value_box", ownerA, null, "outline");
        assertEquals(
                first, WorldMeshSink.stableSourceId("create/value_box", ownerA, null, "outline"));
        assertNotEquals(
                first, WorldMeshSink.stableSourceId("create/value_box", ownerB, null, "outline"));
    }

    @Test
    void sameDimensionReentryStillRequiresANewWorldToken() {
        Object firstLevel = new Object();
        Object reenteredLevel = new Object();
        assertEquals(
                false,
                WorldMeshSink.requiresNewWorldToken(
                        firstLevel, "minecraft:overworld", firstLevel, "minecraft:overworld"));
        assertEquals(
                true,
                WorldMeshSink.requiresNewWorldToken(
                        firstLevel, "minecraft:overworld", reenteredLevel, "minecraft:overworld"));
    }
}
