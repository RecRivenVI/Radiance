package com.radiance.api.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import com.mojang.blaze3d.vertex.VertexFormat;
import org.junit.jupiter.api.Test;

class WorldSceneContractTest {

    @Test
    void explicitLineRequestsMustReachWorldReceiverAdmission() throws Exception {
        var admission =
                WorldScene.class.getDeclaredMethod("isSurfaceMode", VertexFormat.Mode.class);
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
                WorldScene.StageKey.AFTER_PARTICLES, WorldScene.StageKey.from("after_particles"));
        assertEquals(
                WorldScene.StageKey.DIMENSION_RENDER_SKY, WorldScene.StageKey.from("renderSky"));
        assertEquals(
                WorldScene.StageKey.DIMENSION_RENDER_WEATHER,
                WorldScene.StageKey.from("renderSnowAndRain"));
        assertEquals(
                WorldScene.StageKey.UNKNOWN, WorldScene.StageKey.from("third_party_unknown_stage"));
    }

    @Test
    void sourceIdentityIncludesStableSemanticAndOwnerIdentity() {
        Object ownerA = new Object();
        Object ownerB = new Object();
        int first = WorldScene.stableSourceId("create/value_box", ownerA, null, "outline");
        assertEquals(first, WorldScene.stableSourceId("create/value_box", ownerA, null, "outline"));
        assertNotEquals(
                first, WorldScene.stableSourceId("create/value_box", ownerB, null, "outline"));
    }

    @Test
    void sameDimensionReentryStillRequiresANewWorldToken() {
        Object firstLevel = new Object();
        Object reenteredLevel = new Object();
        assertEquals(
                false,
                WorldScene.requiresNewWorldToken(
                        firstLevel, "minecraft:overworld", firstLevel, "minecraft:overworld"));
        assertEquals(
                true,
                WorldScene.requiresNewWorldToken(
                        firstLevel, "minecraft:overworld", reenteredLevel, "minecraft:overworld"));
    }
}
