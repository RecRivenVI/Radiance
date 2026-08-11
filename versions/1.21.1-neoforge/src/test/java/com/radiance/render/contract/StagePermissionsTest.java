package com.radiance.render.contract;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class StagePermissionsTest {
    @Test
    void unknownListenerNeverAcquiresWorldRasterPermission() {
        for (var target : StagePermissions.Target.values()) {
            assertNull(StagePermissions.resolve(null, target));
        }
    }

    @Test
    void explicitPostConsumerIsNarrowAndHasAReason() {
        var permit =
                StagePermissions.resolve(
                        StagePermissions.Producer.VEIL_POST_PROCESS,
                        StagePermissions.Target.DEFAULT_WORLD);
        assertNotNull(permit);
        assertTrue(permit.reason().contains("excludes fixed-buffer"));
        assertNull(
                StagePermissions.resolve(
                        StagePermissions.Producer.VEIL_POST_PROCESS,
                        StagePermissions.Target.OFFSCREEN));
    }
}
