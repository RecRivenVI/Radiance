package com.radiance.compatibility.simulated;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.ByteBuffer;
import org.junit.jupiter.api.Test;

class DiagramPresentationTest {
    @Test void physicalResolutionHasNoLowResolutionCapAndRespectsFractionalScale() {
        assertEquals(1056, DiagramPresentation.pixels(264, 3840, 960));
        assertEquals(352, DiagramPresentation.pixels(88, 3840, 960));
        assertEquals(792, DiagramPresentation.pixels(264, 1920, 640));
        assertEquals(528, DiagramPresentation.pixels(264, 1919, 960));
        assertThrows(IllegalArgumentException.class, () -> DiagramPresentation.pixels(88, 0, 1));
    }
    @Test void decorationUsesPostAlphaIncludingCutoutHolesAndFractionalTransparency() {
        var rgba = ByteBuffer.allocate(8 * 8 * 4);
        // Colored cutout holes remain available. Any nonzero final alpha occupies the paper.
        rgba.put(4 * (2 * 8 + 2), (byte) 255);
        assertFalse(DiagramPresentation.occupied(rgba, 8, 8, 4, 4, 1, 1, 1, 1));
        rgba.put(4 * (3 * 8 + 3) + 3, (byte) 1);
        assertTrue(DiagramPresentation.occupied(rgba, 8, 8, 4, 4, 1, 1, 1, 1));
        assertFalse(DiagramPresentation.occupied(rgba, 8, 8, 4, 4, 1, 2, 1, 1));
    }
    @Test void occupancyClipsAndSamplesAllPhysicalPixelsAtFractionalGuiScale() {
        var rgba = ByteBuffer.allocate(7 * 5 * 4);
        rgba.put(4 * (4 * 7 + 6) + 3, (byte) 255);
        assertTrue(DiagramPresentation.occupied(rgba, 7, 5, 4, 3, 3, 2, 2, 2));
        assertFalse(DiagramPresentation.occupied(rgba, 7, 5, 4, 3, -2, -2, 1, 1));
        assertFalse(DiagramPresentation.occupied(rgba, 7, 5, 4, 3, 4, 3, 1, 1));
    }
}
