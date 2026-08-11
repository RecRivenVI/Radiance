package com.radiance.render.contract;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import org.junit.jupiter.api.Test;

class LineContractTest {
    @Test
    void opaquePolicyPreservesEmissionAndQueryIdentityWithoutErasingSourceMaterial() {
        for (int mode : new int[] {0, 1, 10, 11, 22}) {
            var source = new LineContract.Material(mode, .6f, 7);
            var opaque =
                    LineContract.OpacityPolicy.OPAQUE.material(
                            source.alphaMode(), source.emission(), source.queryVisibility());
            assertEquals(0, opaque.alphaMode());
            assertEquals(.6f, opaque.emission());
            assertEquals(7, opaque.queryVisibility());
            assertEquals(mode, source.alphaMode());
            assertEquals(
                    source,
                    LineContract.OpacityPolicy.SOURCE_ALPHA.material(
                            source.alphaMode(), source.emission(), source.queryVisibility()));
        }
    }

    @Test
    void styleVectorUsesPhysicalWidthAndCopiesOwnerParity() {
        var basis = LineContract.Basis3.copyOf(new float[] {-1, 0, 0, 0, 1, 0, 0, 0, 1});
        var style =
                new LineContract.LineStyle(
                        LineContract.Encoding.MINECRAFT_LINES,
                        2.5f,
                        LineContract.WidthPolicy.ORDINARY.thickness(2.5f),
                        new LineContract.Material(10, 1, 1));
        ByteBuffer bytes =
                ByteBuffer.allocate(LineContract.STYLE_BYTES).order(ByteOrder.LITTLE_ENDIAN);
        LineContract.writeStyle(
                bytes, 0, new LineContract.SourceKey(3, "fixture/owner"), style, basis);
        assertEquals(LineContract.MAGIC, bytes.getInt(0));
        assertEquals(1, bytes.getInt(4));
        assertEquals(3, bytes.getInt(8));
        assertEquals(1 | (10 << 8), bytes.getInt(12));
        assertEquals(.0125f, bytes.getFloat(16), 1e-8f);
        assertEquals(2.5f, bytes.getFloat(20));
        assertEquals(-1, bytes.getFloat(24));
        assertEquals(1, bytes.getFloat(60));
        assertEquals(.0075f, LineContract.WidthPolicy.DEBUG.thickness(1), 1e-8f);
    }

    @Test
    void ownedCoordinateSnapshotDoesNotFollowMutableInput() {
        float[] source = {-1, 0, 0, 0, 1, 0, 0, 0, 1};
        var basis = LineContract.Basis3.copyOf(source);
        source[0] = 10;
        assertEquals(-1, basis.x().x());
        assertEquals(-1, basis.determinant());
        var space =
                new LineContract.DrawSpace(
                        LineContract.Coordinates.WORLD, 30_000_000.25, 2, 3, basis);
        assertTrue(space.finite());
        assertEquals(30_000_000.25, space.x());
    }

    @Test
    void invalidValuesAreRejectedBeforeNativeUse() {
        for (float width : new float[] {0, -1, Float.NaN, Float.POSITIVE_INFINITY}) {
            var style =
                    new LineContract.LineStyle(
                            LineContract.Encoding.DEBUG_LINES,
                            width,
                            width,
                            new LineContract.Material(10, 0, 1));
            assertFalse(style.valid());
            assertThrows(
                    IllegalArgumentException.class,
                    () ->
                            LineContract.writeStyle(
                                    ByteBuffer.allocate(64),
                                    0,
                                    new LineContract.SourceKey(1, "test"),
                                    style,
                                    LineContract.Basis3.IDENTITY));
        }
    }
}
