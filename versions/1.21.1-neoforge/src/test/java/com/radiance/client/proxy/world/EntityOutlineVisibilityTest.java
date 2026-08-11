package com.radiance.client.proxy.world;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.radiance.client.constant.Constants.RayTracingFlags;
import com.radiance.render.contract.RenderAbi;
import org.junit.jupiter.api.Test;

class EntityOutlineVisibilityTest {

    @Test
    void derivedOwnerChannelHidesOnlyTheAttachedAwakeCameraAndRetainsTransport() {
        int transport = RenderAbi.QUERY_WORLD | RenderAbi.QUERY_SELF | RenderAbi.QUERY_PRIORITY;
        for (boolean camera : new boolean[] {false, true})
            for (boolean detached : new boolean[] {false, true})
                for (boolean asleep : new boolean[] {false, true}) {
                    int mask =
                            EntityProxy.priorityOnlyVisibilityFor(camera, detached, asleep)
                                    .getValue();
                    assertEquals(
                            !camera || detached || asleep, (mask & RenderAbi.QUERY_PRIORITY) != 0);
                    org.junit.jupiter.api.Assertions.assertTrue((mask & transport) != 0);
                    assertEquals(0, mask & RenderAbi.QUERY_WORLD);
                }
    }

    @Test
    void attachedAwakeCameraEntityUsesFirstPersonHiddenVisibility() {
        assertEquals(RayTracingFlags.PLAYER, EntityProxy.worldVisibilityFor(true, false, false));
        assertEquals(
                RayTracingFlags.PLAYER, EntityProxy.priorityOnlyVisibilityFor(true, false, false));
    }

    @Test
    void sleepingCameraEntityUsesVisibleWorldGeometry() {
        assertEquals(RayTracingFlags.WORLD, EntityProxy.worldVisibilityFor(true, false, true));
        assertEquals(
                RayTracingFlags.PRIORITY_ONLY,
                EntityProxy.priorityOnlyVisibilityFor(true, false, true));
    }

    @Test
    void detachedCameraEntityUsesVisibleWorldGeometry() {
        assertEquals(RayTracingFlags.WORLD, EntityProxy.worldVisibilityFor(true, true, false));
        assertEquals(
                RayTracingFlags.PRIORITY_ONLY,
                EntityProxy.priorityOnlyVisibilityFor(true, true, false));
    }

    @Test
    void otherEntityGeometryUsesASeparatePriorityOnlyInstance() {
        assertEquals(RayTracingFlags.WORLD, EntityProxy.worldVisibilityFor(false, false, false));
        assertEquals(
                RayTracingFlags.PRIORITY_ONLY,
                EntityProxy.priorityOnlyVisibilityFor(false, false, false));
    }
}
