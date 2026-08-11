package com.radiance.client.proxy.world;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.radiance.client.constant.Constants.RayTracingFlags;
import org.junit.jupiter.api.Test;

class EntityOutlineVisibilityTest {

    @Test
    void attachedAwakeCameraEntityUsesFirstPersonHiddenVisibility() {
        assertEquals(RayTracingFlags.PLAYER,
            EntityProxy.worldVisibilityFor(true, false, false));
        assertEquals(RayTracingFlags.PLAYER,
            EntityProxy.priorityOnlyVisibilityFor(true, false, false));
    }

    @Test
    void sleepingCameraEntityUsesVisibleWorldGeometry() {
        assertEquals(RayTracingFlags.WORLD,
            EntityProxy.worldVisibilityFor(true, false, true));
        assertEquals(RayTracingFlags.PRIORITY_ONLY,
            EntityProxy.priorityOnlyVisibilityFor(true, false, true));
    }

    @Test
    void detachedCameraEntityUsesVisibleWorldGeometry() {
        assertEquals(RayTracingFlags.WORLD,
            EntityProxy.worldVisibilityFor(true, true, false));
        assertEquals(RayTracingFlags.PRIORITY_ONLY,
            EntityProxy.priorityOnlyVisibilityFor(true, true, false));
    }

    @Test
    void otherEntityGeometryUsesASeparatePriorityOnlyInstance() {
        assertEquals(RayTracingFlags.WORLD,
            EntityProxy.worldVisibilityFor(false, false, false));
        assertEquals(RayTracingFlags.PRIORITY_ONLY,
            EntityProxy.priorityOnlyVisibilityFor(false, false, false));
    }
}
