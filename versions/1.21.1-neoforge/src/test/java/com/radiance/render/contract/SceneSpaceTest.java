package com.radiance.render.contract;

import static org.junit.jupiter.api.Assertions.*;

import com.radiance.client.constant.Constants;
import org.junit.jupiter.api.Test;

class SceneSpaceTest {
    @Test
    void allSourceAdaptersRetainTheExistingNativeCoordinateValues() {
        assertEquals(0, SceneSpace.WORLD.value());
        assertEquals(1, SceneSpace.CAMERA.value());
        assertEquals(2, SceneSpace.CAMERA_SHIFT.value());
        for (var adapter : Constants.Coordinates.values())
            assertEquals(SceneSpace.valueOf(adapter.name()).value(), adapter.getValue());
    }
}
