package com.radiance.client.render;
import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
class MaterialFacesTest {
    @Test
    void sourceStateIsIndependentOfTextureAndAlpha() {
        assertEquals(0, MaterialFaces.flags(false, 1029, 2305));
        assertEquals(MaterialFaces.BACK, MaterialFaces.flags(true, 1029, 2305));
        assertEquals(MaterialFaces.FRONT, MaterialFaces.flags(true, 1028, 2305));
        assertEquals(
            MaterialFaces.BACK | MaterialFaces.FRONT, MaterialFaces.flags(true, 1032, 2305));
        assertEquals(
            MaterialFaces.BACK | MaterialFaces.CLOCKWISE, MaterialFaces.flags(true, 1029, 2304));
        assertNotEquals(MaterialFaces.encode(0, 0), MaterialFaces.encode(0, MaterialFaces.BACK));
    }
    @Test
    void invalidStateIsNotGuessed() {
        assertThrows(IllegalArgumentException.class, () -> MaterialFaces.flags(true, 123, 2305));
        assertThrows(IllegalArgumentException.class, () -> MaterialFaces.flags(true, 1029, 123));
    }
    @Test
    void faceFieldsDoNotOverlapFlywheelDepthOrUiOwner() {
        int mask = MaterialFaces.BACK | MaterialFaces.FRONT | MaterialFaces.CLOCKWISE;
        // Flywheel DepthTest has nine values, including ALWAYS (ordinal 8).
        assertEquals(0, mask & (15 << 12));
        assertEquals(0, mask & (255 << 24));
        assertEquals(0, mask & (3 << 20));
    }
}
