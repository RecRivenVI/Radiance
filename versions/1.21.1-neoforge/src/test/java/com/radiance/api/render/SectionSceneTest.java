package com.radiance.api.render;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;
import org.joml.Vector3d;
import org.junit.jupiter.api.Test;

class SectionSceneTest {
    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void sourceCallbackMustInvalidateOldBindingsBeforeAcknowledgingNewEpoch() throws Exception {
        try (var space = new RenderSpace();
                var scene = new SectionScene(space, new Vector3d())) {
            assertTrue(scene.enterEpoch(4));
            var field = SectionScene.class.getDeclaredField("members");
            field.setAccessible(true);
            Map bindings = (Map) field.get(scene);
            Object sourceMember = new Object();
            bindings.put(sourceMember, new Object());
            assertFalse(scene.enterEpoch(4));
            assertEquals(1, bindings.size());
            assertTrue(scene.enterEpoch(5));
            assertTrue(bindings.isEmpty(), "Unchanged source identity must bind again after reset");
            assertFalse(scene.enterEpoch(5));
        }
    }
}
