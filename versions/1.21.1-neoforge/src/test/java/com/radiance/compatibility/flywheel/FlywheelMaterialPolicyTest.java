package com.radiance.compatibility.flywheel;

import static org.junit.jupiter.api.Assertions.*;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

class FlywheelMaterialPolicyTest {
    @Test
    void pinnedCutoutShaderIdentitiesHaveTheirActualThresholdModes() {
        assertEquals(0, mode("flywheel", "cutout/off.glsl"));
        assertEquals(1, mode("flywheel", "cutout/half.glsl"));
        assertEquals(9, mode("flywheel", "cutout/one_tenth.glsl"));
        assertEquals(3, mode("flywheel", "cutout/epsilon.glsl"));
    }

    @Test
    void unregisteredProgramsKeepTheirExistingPolicyPendingDecision() {
        assertEquals(0, mode("other", "cutout/off.glsl"));
        assertEquals(1, mode("flywheel", "cutout/custom.glsl"));
        assertEquals(1, mode("other", "cutout/epsilon.glsl"));
    }

    private static int mode(String namespace, String path) {
        return FlywheelMaterialData.cutoutMode(
                ResourceLocation.fromNamespaceAndPath(namespace, path));
    }
}
