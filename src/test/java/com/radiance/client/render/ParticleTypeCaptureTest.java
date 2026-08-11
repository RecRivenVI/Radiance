package com.radiance.client.render;

import net.neoforged.neoforge.client.GlStateBackup;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ParticleTypeCaptureTest {
    @Test void createCubeUsesFinalSourceOverStateAfterItsEarlierAdditiveSetup() {
        GlStateBackup state = new GlStateBackup();
        state.blendEnabled = true;
        state.blendSrcRgb = 1;
        state.blendDestRgb = 1;
        assertEquals(ParticleTypeCapture.Blend.ADDITIVE, ParticleTypeCapture.blend(state));
        state.blendSrcRgb = 770;
        state.blendDestRgb = 771;
        assertEquals(ParticleTypeCapture.Blend.SOURCE_OVER, ParticleTypeCapture.blend(state));
    }
    @Test void disabledBlendIgnoresStaleFactorsButUnsupportedActiveBlendFails() {
        GlStateBackup state = new GlStateBackup();
        state.blendSrcRgb = 774;
        state.blendDestRgb = 0;
        assertEquals(ParticleTypeCapture.Blend.OPAQUE, ParticleTypeCapture.blend(state));
        state.blendEnabled = true;
        assertThrows(UnsupportedOperationException.class, () -> ParticleTypeCapture.blend(state));
    }
}
