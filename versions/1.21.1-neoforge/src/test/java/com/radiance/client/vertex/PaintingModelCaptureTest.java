package com.radiance.client.vertex;

import static org.junit.jupiter.api.Assertions.*;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;

class PaintingModelCaptureTest {
    @Test
    void tileQueriesKeepVanillaOrderAndFacing() {
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            var snapshot =
                    new PaintingModelCapture.LightSnapshot(3, 2, direction, 10.25, 20.75, 30.25);
            var seen = new ArrayList<List<Integer>>();
            snapshot.read(
                    (x, y, z) -> {
                        seen.add(List.of(x, y, z));
                        return seen.size();
                    });
            assertArrayEquals(new int[] {1, 2, 3, 4, 5, 6}, snapshot.values);
            for (int i = 0; i < 3; i++)
                for (int j = 0; j < 2; j++) {
                    int x =
                            direction == Direction.NORTH
                                    ? 9 + i
                                    : direction == Direction.SOUTH ? 11 - i : 10;
                    int z =
                            direction == Direction.WEST
                                    ? 31 - i
                                    : direction == Direction.EAST ? 29 + i : 30;
                    assertEquals(List.of(x, 20 + j, z), seen.get(i * 2 + j));
                }
        }
    }

    @Test
    void negativeAndOddSizedOriginsUseFloorAndFloatTileCenters() {
        var s = new PaintingModelCapture.LightSnapshot(1, 1, Direction.NORTH, -.125, -.125, -.125);
        assertEquals(-1, s.coordinate(0, 0));
        assertEquals(-1, s.coordinate(0, 1));
        assertEquals(-1, s.coordinate(0, 2));
    }

    @Test
    void cachedGeometryDoesNotSkipCurrentTileLightQueries() {
        var s = new PaintingModelCapture.LightSnapshot(2, 2, Direction.SOUTH, 0, 0, 0);
        int[] calls = {0};
        s.read((x, y, z) -> ++calls[0]);
        int[] first = s.values.clone();
        s.read((x, y, z) -> ++calls[0]);
        assertEquals(8, calls[0]);
        assertFalse(Arrays.equals(first, s.values));
    }

    @Test
    void replayUsesExactlyTheCapturedValuesAndRejectsChangedSourceQueryOrder() {
        var s = new PaintingModelCapture.LightSnapshot(1, 2, Direction.NORTH, 0, 0, 0);
        s.read((x, y, z) -> y + 17);
        var r = new PaintingModelCapture.Replay(s, null);
        for (int i = 0; i < 2; i++)
            assertEquals(
                    s.values[i],
                    r.next(
                            null,
                            new BlockPos(
                                    s.coordinate(i, 0), s.coordinate(i, 1), s.coordinate(i, 2))));
        assertThrows(IllegalStateException.class, () -> r.next(null, BlockPos.ZERO));
        var changed = new PaintingModelCapture.Replay(s, null);
        assertThrows(
                IllegalStateException.class, () -> changed.next(null, new BlockPos(40, 50, 60)));
        assertEquals(0, changed.index);
    }

    @Test
    void dimensionsAndSpriteCoordinatesArePartOfGeometryIdentity() {
        var a = new PaintingModelCapture.Shape(1, 2, null, null, 0, 1, 2, 3, 4, 5, 6, 7);
        assertEquals(a, new PaintingModelCapture.Shape(1, 2, null, null, 0, 1, 2, 3, 4, 5, 6, 7));
        assertNotEquals(
                a, new PaintingModelCapture.Shape(2, 1, null, null, 0, 1, 2, 3, 4, 5, 6, 7));
        assertNotEquals(
                a, new PaintingModelCapture.Shape(1, 2, null, null, 8, 1, 2, 3, 4, 5, 6, 7));
    }

    @Test
    void fullFallbackReusesSampledLightAndRestoresNestedScope() {
        var outer = new PaintingModelCapture.LightSnapshot(1, 2, Direction.NORTH, 0, 0, 0);
        var inner = new PaintingModelCapture.LightSnapshot(1, 1, Direction.SOUTH, 0, 0, 0);
        int[] queries = {0};
        outer.read((x, y, z) -> ++queries[0]);
        inner.read((x, y, z) -> ++queries[0]);
        var observed = new ArrayList<Integer>();
        PaintingModelCapture.renderLit(
                null,
                null,
                outer,
                null,
                (pose, consumer) -> {
                    observed.add(
                            PaintingModelCapture.light(
                                    null, new BlockPos(0, -1, 0), () -> ++queries[0]));
                    PaintingModelCapture.renderLit(
                            null,
                            null,
                            inner,
                            null,
                            (nestedPose, nestedConsumer) ->
                                    observed.add(
                                            PaintingModelCapture.light(
                                                    null, BlockPos.ZERO, () -> ++queries[0])));
                    observed.add(
                            PaintingModelCapture.light(null, BlockPos.ZERO, () -> ++queries[0]));
                });
        assertEquals(List.of(1, 3, 2), observed);
        assertEquals(3, queries[0]);
        assertEquals(0, PaintingModelCapture.light(null, BlockPos.ZERO, () -> ++queries[0]));
        assertEquals(3, queries[0]);
        try (var preview = com.radiance.client.render.RasterPreviewScope.enter()) {
            assertEquals(4, PaintingModelCapture.light(null, BlockPos.ZERO, () -> ++queries[0]));
        }
    }

    @Test
    void sourceExceptionAndIncompleteCaptureRestoreReplayWithoutReplacingOriginalError() {
        var lights = new PaintingModelCapture.LightSnapshot(1, 1, Direction.NORTH, 0, 0, 0);
        var failure = new IllegalArgumentException("original renderer failure");
        assertSame(
                failure,
                assertThrows(
                        IllegalArgumentException.class,
                        () ->
                                PaintingModelCapture.renderLit(
                                        null,
                                        null,
                                        lights,
                                        null,
                                        (pose, consumer) -> {
                                            throw failure;
                                        })));
        assertEquals(0, PaintingModelCapture.light(null, BlockPos.ZERO, () -> 17));
        assertThrows(
                IllegalStateException.class,
                () ->
                        PaintingModelCapture.renderLit(
                                null, null, lights, null, (pose, consumer) -> {}));
        assertEquals(0, PaintingModelCapture.light(null, BlockPos.ZERO, () -> 19));
        try (var preview = com.radiance.client.render.RasterPreviewScope.enter()) {
            assertEquals(19, PaintingModelCapture.light(null, BlockPos.ZERO, () -> 19));
        }
    }
}
