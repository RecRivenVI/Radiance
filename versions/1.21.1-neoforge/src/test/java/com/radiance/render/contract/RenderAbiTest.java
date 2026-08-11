package com.radiance.render.contract;

import static com.radiance.client.vertex.PBRVertexFormatElements.*;
import static org.junit.jupiter.api.Assertions.*;

import com.radiance.client.constant.Constants;
import com.radiance.client.vertex.PBRVertexFormats;
import org.junit.jupiter.api.Test;

class RenderAbiTest {
    @Test
    void actualJavaFieldOffsetsMatchTheCpuAndGpuSourceContract() {
        var fields =
                java.util.Map.ofEntries(
                        java.util.Map.entry(PBR_POS, RenderAbi.COMPACT_POS_WORD),
                        java.util.Map.entry(PBR_NORM, RenderAbi.COMPACT_NORM_WORD),
                        java.util.Map.entry(PBR_COLOR_LAYER, RenderAbi.COMPACT_COLOR_LAYER_WORD),
                        java.util.Map.entry(PBR_TEXTURE_UV, RenderAbi.COMPACT_TEXTURE_UV_WORD),
                        java.util.Map.entry(PBR_OVERLAY_UV, RenderAbi.COMPACT_OVERLAY_UV_WORD),
                        java.util.Map.entry(PBR_TEXTURE_ID, RenderAbi.COMPACT_TEXTURE_ID_WORD),
                        java.util.Map.entry(PBR_GLINT_UV, RenderAbi.COMPACT_GLINT_UV_WORD),
                        java.util.Map.entry(
                                PBR_GLINT_TEXTURE, RenderAbi.COMPACT_GLINT_TEXTURE_WORD),
                        java.util.Map.entry(PBR_LIGHT_UV, RenderAbi.COMPACT_LIGHT_UV_WORD),
                        java.util.Map.entry(
                                PBR_ALBEDO_EMISSION, RenderAbi.COMPACT_ALBEDO_EMISSION_WORD),
                        java.util.Map.entry(PBR_POST_BASE, RenderAbi.COMPACT_POST_BASE_WORD),
                        java.util.Map.entry(PBR_PACKED_MODES, RenderAbi.COMPACT_PACKED_MODES_WORD));
        fields.forEach(
                (element, word) ->
                        assertEquals(
                                word * 4,
                                PBRVertexFormats.PBR_COMPACT_V1
                                        .getOffsetsByElement()[element.id()]));
        assertEquals(
                RenderAbi.CANONICAL_ALPHA_MODE_WORD * 4,
                PBRVertexFormats.PBR_TRIANGLE.getOffsetsByElement()[PBR_POST_BASE.id()] + 12);
    }

    @Test
    void frozenWireValuesAndRealSourceStridesDoNotChange() {
        int expected = 0;
        for (var mode : Constants.DrawModes.values()) assertEquals(expected++, mode.getValue());
        expected = 0;
        for (var format : Constants.VertexFormats.values())
            assertEquals(expected++, format.getValue());
        assertEquals(128, PBRVertexFormats.PBR_TRIANGLE.getVertexSize());
        assertEquals(100, PBRVertexFormats.PBR_COMPACT_V1.getVertexSize());
        assertEquals(RenderAbi.PBR_CANONICAL_BYTES, PBRVertexFormats.PBR_TRIANGLE.getVertexSize());
        assertEquals(RenderAbi.PBR_COMPACT_BYTES, PBRVertexFormats.PBR_COMPACT_V1.getVertexSize());
        assertEquals(RenderAbi.LINE_STYLE_BYTES, LineContract.STYLE_BYTES);
        assertEquals(RenderAbi.LINE_VERTEX_BYTES, LineContract.VERTEX_BYTES);
        assertEquals(RenderAbi.LINE_MAGIC, LineContract.MAGIC);
        assertEquals(RenderAbi.LINE_VERSION, LineContract.VERSION);
        assertEquals(1, RenderAbi.QUERY_WORLD);
        assertEquals(2, RenderAbi.QUERY_SELF);
        assertEquals(4, RenderAbi.QUERY_PRIORITY);
        assertEquals(8, RenderAbi.QUERY_HAND);
        assertEquals(0, RenderAbi.ALPHA_OPAQUE);
        assertEquals(1, RenderAbi.ALPHA_CUTOUT);
        assertEquals(2, RenderAbi.ALPHA_TRANSMISSION);
        assertEquals(10, RenderAbi.ALPHA_COVERAGE);
        assertEquals(11, RenderAbi.ALPHA_ADDITIVE);
        assertEquals(24, RenderAbi.ALPHA_ORDERED_OPAQUE);
    }
}
