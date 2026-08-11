package com.radiance.client.texture;

import static org.junit.jupiter.api.Assertions.*;
import com.mojang.blaze3d.platform.NativeImage;
import org.junit.jupiter.api.Test;

final class EmissionRegionTest {
    @Test void packedSpecularRectangleMatchesFullImageEmissionAndMissingMaterialClearsIt() {
        int id = 3981;
        TextureTracker.GLID2Texture.put(id, new TextureTracker.Texture(128, 64,
            NativeImage.InternalGlFormat.RGBA, 0));
        try (var albedo = new NativeImage(8, 40, true);
             var specular = new NativeImage(8, 40, true);
             var packed = new NativeImage(3, 2, true)) {
            for (int y = 0; y < 2; y++) for (int x = 0; x < 3; x++) {
                albedo.setPixelRGBA(x + 2, y + 27, (x == 0 ? 0x80 : 0xff) << 24 | 0x007f3fa1);
                int emission = (80 + 15 * x + y) << 24;
                specular.setPixelRGBA(x + 2, y + 27, emission);
                packed.setPixelRGBA(x, y, emission);
            }
            var before = EmissionRecorder.buildTileUpdate(id, albedo, specular, 41, 18, 2, 27, 3, 2);
            var after = EmissionRecorder.buildTileUpdate(id, albedo, packed, 41, 18, 2, 27, 3, 2, 0, 0);
            assertNotNull(before); assertNotNull(after);
            assertEquals(before.tileKey, after.tileKey);
            assertFalse(before.cells.isEmpty());
            assertEquals(before.cells.size(), after.cells.size());
            for (int i = 0; i < before.cells.size(); i++) {
                var a = before.cells.get(i); var b = after.cells.get(i);
                assertArrayEquals(new float[]{a.u0,a.v0,a.u1,a.v1,a.avgEmission,a.avgR,a.avgG,a.avgB},
                    new float[]{b.u0,b.v0,b.u1,b.v1,b.avgEmission,b.avgR,b.avgG,b.avgB});
            }
            var clear = EmissionRecorder.buildTileUpdate(id, albedo, null, 41, 18, 2, 27, 3, 2, 0, 0);
            assertEquals(before.tileKey, clear.tileKey);
            assertTrue(clear.cells.isEmpty());
        } finally { TextureTracker.GLID2Texture.remove(id); }
    }
}
