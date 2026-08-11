package com.radiance.client.render;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class DebugEmissionScopeTest {
    @Test void chunkBorderEmissionCannotLeakIntoOtherDebugDrawsAfterFailure() {
        assertFalse(DebugEmissionScope.isChunkBorder());
        assertThrows(IllegalStateException.class, () -> DebugEmissionScope.withChunkBorders(() -> {
            assertTrue(DebugEmissionScope.isChunkBorder());
            DebugEmissionScope.withChunkBorders(() -> assertTrue(DebugEmissionScope.isChunkBorder()));
            assertTrue(DebugEmissionScope.isChunkBorder());
            throw new IllegalStateException("renderer failed");
        }));
        assertFalse(DebugEmissionScope.isChunkBorder());
    }
}
