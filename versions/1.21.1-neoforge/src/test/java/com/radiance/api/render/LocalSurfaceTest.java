package com.radiance.api.render;

import static org.junit.jupiter.api.Assertions.*;

import com.radiance.render.contract.DrawStateSnapshot;
import com.radiance.render.contract.RenderAbi;
import java.nio.*;
import org.junit.jupiter.api.Test;

class LocalSurfaceTest {
    @Test
    void sourceAttributesHaveTheirOwnChangeIdentity() {
        var input = ByteBuffer.allocateDirect(512).order(ByteOrder.nativeOrder());
        var texture = new com.radiance.client.texture.TextureTasks.Owner(7, 3);
        var uv = new DrawStateSnapshot.UvTransform(2, .25F, .4F, -.2F, .5F, .7F);
        var surface = LocalSurface.uniform(15728880, 3, 0x80402010, uv);
        var owner = new Object();
        try (var batch = new LocalModelBatch(1)) {
            batch.add(
                    7,
                    Long.MIN_VALUE | 31,
                    2,
                    texture,
                    input,
                    4,
                    1,
                    2,
                    3,
                    8,
                    new org.joml.Matrix4f(),
                    "default",
                    DrawStateSnapshot.IDENTITY,
                    null,
                    surface,
                    1);
            var data = batch.records();
            assertTrue(surface.matches(data, RenderAbi.RIGID_SURFACE_OFFSET));
            assertEquals(1, data.getInt(132));
            assertFalse(LocalSurface.NONE.matches(data, RenderAbi.RIGID_SURFACE_OFFSET));
            PersistentInstances.accepted(Long.MIN_VALUE | 31, owner, data, 0, "default");
            var changed =
                    ByteBuffer.allocateDirect(data.remaining()).order(ByteOrder.nativeOrder());
            changed.put(data).clear();
            assertFalse(PersistentInstances.changed(Long.MIN_VALUE | 31, changed, 0, "default"));
            changed.putInt(132, 0);
            assertTrue(PersistentInstances.changed(Long.MIN_VALUE | 31, changed, 0, "default"));
            changed.putInt(132, 1).putFloat(RenderAbi.RIGID_SURFACE_OFFSET + 36, .8F);
            assertTrue(PersistentInstances.changed(Long.MIN_VALUE | 31, changed, 0, "default"));
        } finally {
            PersistentInstances.invalidate();
        }
    }
}
