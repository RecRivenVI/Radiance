package com.radiance.client.texture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class MipmapUtilTest {
    @Test
    void opposedNormalComponentsCancelIndependentlyOfHeight() {
        assertEquals(
                0x808080ff,
                MipmapUtil.blendNormalMap(0x000080ff, 0x000080ff, 0xffff80ff, 0xffff80ff));
    }

    @Test
    void zeroHeightRetainsEveryMaterialChannel() {
        assertEquals(
                0x007080b0,
                MipmapUtil.blendNormalMap(0x007080b0, 0x007080b0, 0x007080b0, 0x007080b0));
    }

    @Test
    void specularMipKeepsMetalIdentityAndDoesNotTreatEmissionAsOpacity() {
        assertEquals(
                0x400ae664,
                MipmapUtil.blendSpecularMap(0x000ae600, 0xff0ae6c8, 0xff0ae7c8, 0xfe780000));
    }

    @Test
    void specularMipDoesNotMixDielectricReflectanceWithMetalCodes() {
        assertEquals(
                0x006e1440,
                MipmapUtil.blendSpecularMap(0xff640a40, 0xff6e1e40, 0xff78e640, 0xff00ff40));
    }

    @Test
    void transparentTexelsDoNotContributeToAnOpaqueGrille() {
        int[] specular = {0xff00ffbf, 0xff000000, 0xff000000, 0xff000000};
        int[] weights = {3, 0, 0, 0};
        assertEquals(
                0x0000ffbf,
                MipmapUtil.blendCoveredMaterial(specular, weights, 3, AuxiliaryTextures.SPECULAR));
        int[] normals = {0xffee7799, 0xff008080, 0xff008080, 0xff008080};
        assertEquals(
                normals[0],
                MipmapUtil.blendCoveredMaterial(normals, weights, 3, AuxiliaryTextures.NORMAL));
    }

    @Test
    void filteredMetalKeepsMaterialChannelsAndOnlyBroadensVaryingNormals() {
        int iron = 0xff0ae6c8;
        assertEquals(iron, MipmapUtil.filteredMetalSmoothness(iron, 1));
        int filtered = MipmapUtil.filteredMetalSmoothness(iron, 0.989);
        assertEquals(iron & ~255, filtered & ~255);
        assertTrue((filtered & 255) < (iron & 255));
        assertEquals(iron & ~255, MipmapUtil.filteredMetalSmoothness(iron, 0));
    }
}
