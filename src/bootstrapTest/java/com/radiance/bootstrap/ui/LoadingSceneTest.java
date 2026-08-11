/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package com.radiance.bootstrap.ui;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class LoadingSceneTest {
    @Test
    void uploadsExactTextureKindsAndBuildsNativeDrawProtocol() {
        CapturingSink sink = new CapturingSink();
        try (LoadingScene scene = new LoadingScene(1, ColourScheme.RED, "1.21.1", "21.1.248-beta", false, sink)) {
            assertEquals(List.of(LoadingScene.FONT_TEXTURE, LoadingScene.FOX_TEXTURE), sink.uploads.keySet().stream().toList());
            Upload font = sink.uploads.get(LoadingScene.FONT_TEXTURE);
            assertEquals(256, font.width);
            assertEquals(128, font.height);
            assertTrue(font.linear);
            assertTrue(font.clampToEdge);
            assertFalse(sink.uploads.get(LoadingScene.FOX_TEXTURE).clampToEdge);
            boolean foundGlyphPixel = false;
            for (int i = 0; i < font.rgba.length; i += 4) {
                foundGlyphPixel |= Byte.toUnsignedInt(font.rgba[i]) != 0;
                assertEquals(0, font.rgba[i + 1]);
                assertEquals(0, font.rgba[i + 2]);
                assertEquals(255, Byte.toUnsignedInt(font.rgba[i + 3]));
            }
            assertTrue(foundGlyphPixel);

            LoadingScene.Frame frame = scene.render(new LoadingScene.FrameInput(3, 128, List.of(), List.of(),
                    new LoadingScene.PerformanceSnapshot(0.25f, "Memory")));
            assertEquals(854, frame.canvasWidth());
            assertEquals(480, frame.canvasHeight());
            assertEquals(0x803d32ef, frame.backgroundAbgr());
            assertTrue(frame.vertices().isDirect());
            assertTrue(frame.vertices().isReadOnly());
            assertEquals(ByteOrder.nativeOrder(), frame.vertices().order());
            assertTrue(frame.batches().isDirect());
            assertEquals(4, frame.batchCount()); // fox, version, performance text, performance bar
            assertEquals(frame.batchCount() * 16, frame.batches().remaining());
            assertEquals(0, frame.vertices().remaining() % 20);

            ByteBuffer batches = frame.batches().duplicate().order(ByteOrder.nativeOrder());
            assertBatch(batches, 0, 0, 6, RenderElement.TEXTURE, LoadingScene.FOX_TEXTURE);
            assertBatch(batches, 1, 6, stringVertices("1.21.1-21.1.248"), RenderElement.FONT, LoadingScene.FONT_TEXTURE);
            assertEquals(RenderElement.FONT, batches.getInt(2 * 16 + 8));
            assertEquals(RenderElement.BAR, batches.getInt(3 * 16 + 8));

            ByteBuffer vertices = frame.vertices().duplicate().order(ByteOrder.nativeOrder());
            Vertex v0 = vertex(vertices, 0);
            Vertex v1 = vertex(vertices, 1);
            Vertex v2 = vertex(vertices, 2);
            Vertex v3 = vertex(vertices, 3);
            Vertex v4 = vertex(vertices, 4);
            Vertex v5 = vertex(vertices, 5);
            assertEquals(0f, v0.u);
            assertEquals(3 * (1 / 28f), v0.v);
            assertEquals(1f, v1.u);
            assertEquals(v0.v, v1.v);
            assertEquals(v0.x, v2.x);
            assertEquals(v1.x, v3.x);
            assertEquals(v1.y, v3.y);
            assertEquals(v1.u, v3.u);
            assertEquals(v2.x, v5.x);
            assertEquals(v2.y, v5.y);
            assertEquals(v2.v, v5.v);
            assertEquals(v1.x, v4.x);
            assertEquals(v2.y, v4.y);
            for (Vertex vertex : List.of(v0, v1, v2, v3, v4, v5)) assertEquals(0x80ffffff, vertex.colour);
        }
    }

    @Test
    void squirrelAndMojangPreserveOfficialAnimationAndScale() {
        CapturingSink sink = new CapturingSink();
        try (LoadingScene scene = new LoadingScene(2, ColourScheme.BLACK, "1.21.1", "21.1.248", true, sink)) {
            assertTrue(sink.uploads.containsKey(LoadingScene.SQUIRREL_TEXTURE));
            scene.addMojangTexture(77, 10);
            LoadingScene.Frame frame = scene.render(new LoadingScene.FrameInput(12, 64, List.of(), List.of(),
                    new LoadingScene.PerformanceSnapshot(0.5f, "")));
            assertEquals(1708, frame.canvasWidth());
            assertEquals(960, frame.canvasHeight());
            assertEquals(0x40000000, frame.backgroundAbgr());

            ByteBuffer batches = frame.batches().duplicate().order(ByteOrder.nativeOrder());
            assertBatch(batches, 0, 0, 12, RenderElement.TEXTURE, 77);
            assertEquals(RenderElement.TEXTURE, batches.getInt(1 * 16 + 8));
            assertEquals(LoadingScene.SQUIRREL_TEXTURE, batches.getInt(1 * 16 + 12));

            ByteBuffer vertices = frame.vertices().duplicate().order(ByteOrder.nativeOrder());
            Vertex first = vertex(vertices, 0);
            assertEquals((1708 - 1024) / 2f, first.x);
            assertEquals(64 * 2 + 32f, first.y);
            assertEquals(0f, first.u);
            assertEquals(0f, first.v);
            assertEquals(0x14ffffff, first.colour);
            Vertex secondQuad = vertex(vertices, 6);
            assertEquals(first.x + 512, secondQuad.x);
            assertEquals(0.5f, secondQuad.v);

            Vertex squirrel = vertex(vertices, 12);
            int expectedSquirrelAlpha = Math.min((int) (Math.cos(12 * Math.PI / 16) * 16) + 16, 64);
            assertEquals(expectedSquirrelAlpha << 24 | 0xffffff, squirrel.colour);
        }
    }

    @Test
    void deterministicInputDefensivelyCopiesCollectionsAndFramesOwnTheirBuffers() {
        CapturingSink sink = new CapturingSink();
        LoadingScene scene = new LoadingScene(1, ColourScheme.RED, "1.21.1", "21.1.248", false, sink);
        var messages = new java.util.ArrayList<>(List.of(new LoadingScene.MessageLine("First", 0)));
        LoadingScene.FrameInput input = new LoadingScene.FrameInput(0, 255, messages, List.of(),
                new LoadingScene.PerformanceSnapshot(0, ""));
        messages.clear();
        LoadingScene.Frame first = scene.render(input);
        byte firstByte = first.vertices().get(0);
        int firstSize = first.vertices().remaining();
        LoadingScene.Frame second = scene.render(new LoadingScene.FrameInput(99, 255, List.of(), List.of(),
                new LoadingScene.PerformanceSnapshot(1, "A much longer replacement line")));
        assertEquals(firstByte, first.vertices().get(0));
        assertEquals(firstSize, first.vertices().remaining());
        assertSame(second, scene.lastFrame());
        assertNotSame(first, second);
        scene.close();
        assertThrows(IllegalStateException.class, () -> scene.render(input));
    }

    @Test
    void messageFadeAndDeterminateProgressMatchOfficialLayout() {
        CapturingSink sink = new CapturingSink();
        try (LoadingScene scene = new LoadingScene(1, ColourScheme.RED, "1.21.1", "21.1.248", false, sink)) {
            LoadingScene.Frame frame = scene.render(new LoadingScene.FrameInput(50, 255,
                    List.of(new LoadingScene.MessageLine("Fade", 7500)),
                    List.of(new LoadingScene.ProgressBar("Step", 10, 0.3f)),
                    new LoadingScene.PerformanceSnapshot(0.25f, "")));
            assertEquals(6, frame.batchCount());
            ByteBuffer batches = frame.batches().duplicate().order(ByteOrder.nativeOrder());
            assertEquals(RenderElement.FONT, batches.getInt(1 * 16 + 8));
            assertEquals(4 * 6, batches.getInt(1 * 16 + 4));
            assertEquals(RenderElement.BAR, batches.getInt(3 * 16 + 8));
            assertBatch(batches, 4, batches.getInt(4 * 16), 4 * 6, RenderElement.FONT, LoadingScene.FONT_TEXTURE);
            assertEquals(RenderElement.BAR, batches.getInt(5 * 16 + 8));
            assertEquals(18, batches.getInt(5 * 16 + 4));

            ByteBuffer vertices = frame.vertices().duplicate().order(ByteOrder.nativeOrder());
            int logFirst = batches.getInt(1 * 16);
            assertEquals(0x19ffffff, vertex(vertices, logFirst).colour);

            int progressFirst = batches.getInt(5 * 16);
            Vertex fillLeft = vertex(vertices, progressFirst + 12);
            Vertex fillRight = vertex(vertices, progressFirst + 13);
            assertEquals(231f, fillLeft.x);
            assertEquals(351f, fillRight.x);
            assertEquals(0xffffffff, fillLeft.colour);
        }
    }

    private static int stringVertices(String text) {
        return (int) text.chars().filter(c -> c > 32 && c < 127).count() * 6;
    }

    private static void assertBatch(ByteBuffer batches, int index, int first, int count, int role, int key) {
        int offset = index * 16;
        assertEquals(first, batches.getInt(offset));
        assertEquals(count, batches.getInt(offset + 4));
        assertEquals(role, batches.getInt(offset + 8));
        assertEquals(key, batches.getInt(offset + 12));
    }

    private static Vertex vertex(ByteBuffer vertices, int index) {
        int offset = index * 20;
        return new Vertex(vertices.getFloat(offset), vertices.getFloat(offset + 4), vertices.getFloat(offset + 8),
                vertices.getFloat(offset + 12), vertices.getInt(offset + 16));
    }

    private record Vertex(float x, float y, float u, float v, int colour) {}

    private static final class CapturingSink implements DrawSink {
        private final Map<Integer, Upload> uploads = new LinkedHashMap<>();

        @Override
        public void texture(int key, int width, int height, ByteBuffer rgba, boolean linear, boolean clampToEdge) {
            byte[] pixels = new byte[rgba.remaining()];
            rgba.duplicate().get(pixels);
            uploads.put(key, new Upload(width, height, pixels, linear, clampToEdge));
        }
    }

    private record Upload(int width, int height, byte[] rgba, boolean linear, boolean clampToEdge) {}
}
