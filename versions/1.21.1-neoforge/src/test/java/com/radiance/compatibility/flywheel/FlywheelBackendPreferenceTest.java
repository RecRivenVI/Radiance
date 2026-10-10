package com.radiance.compatibility.flywheel;

import static org.junit.jupiter.api.Assertions.*;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

class FlywheelBackendPreferenceTest {
    @Test
    void legacyGlPreferencesSelectTheRealVulkanEngine() {
        assertEquals(
                RadianceFlywheelBackend.ID,
                RadianceFlywheelBackend.translatePreference(
                        ResourceLocation.parse("flywheel:indirect")));
        assertEquals(
                RadianceFlywheelBackend.ID,
                RadianceFlywheelBackend.translatePreference(
                        ResourceLocation.parse("flywheel:instancing")));
    }

    @Test
    void explicitOffAndIndependentBackendsRemainUserChoices() {
        for (String name :
                new String[] {"flywheel:off", "other:indirect", "radiance:vulkan_instancing"}) {
            ResourceLocation id = ResourceLocation.parse(name);
            assertSame(id, RadianceFlywheelBackend.translatePreference(id));
        }
        assertNull(RadianceFlywheelBackend.translatePreference(null));
    }
}
