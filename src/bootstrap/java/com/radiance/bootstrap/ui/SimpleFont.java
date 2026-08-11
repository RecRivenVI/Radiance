/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package com.radiance.bootstrap.ui;

import static org.lwjgl.stb.STBTruetype.*;
import static org.lwjgl.system.MemoryUtil.NULL;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import org.lwjgl.BufferUtils;
import org.lwjgl.stb.STBTTAlignedQuad;
import org.lwjgl.stb.STBTTFontinfo;
import org.lwjgl.stb.STBTTPackContext;
import org.lwjgl.stb.STBTTPackRange;
import org.lwjgl.stb.STBTTPackedchar;

final class SimpleFont {
    private static final int GLYPH_COUNT = 127 - 32;
    private final int textureNumber;
    private final int lineSpacing;
    private final int descent;
    private Glyph[] glyphs;

    private record Glyph(char c, int charwidth, int[] pos, float[] uv) {
        Pos loadQuad(Pos pos, int colour, FrameBuilder bb) {
            final var x0 = pos.x() + pos()[0];
            final var y0 = pos.y() + pos()[1];
            final var x1 = pos.x() + pos()[2];
            final var y1 = pos.y() + pos()[3];
            bb.quad(x0, x1, y0, y1, uv()[0], uv()[2], uv()[1], uv()[3], colour);
            return new Pos(pos.x() + charwidth(), pos.y(), pos.minx());
        }
    }

    SimpleFont(String fontName, int scale, int bufferSize, int textureNumber, DrawSink sink) {
        ByteBuffer buf = STBHelper.readFromClasspath(fontName, bufferSize);
        var info = STBTTFontinfo.create();
        if (!stbtt_InitFont(info, buf)) throw new IllegalStateException("Bad font");

        var ascent = new float[1];
        var descent = new float[1];
        var lineGap = new float[1];
        int fontSize = 24;
        stbtt_GetScaledFontVMetrics(buf, 0, fontSize, ascent, descent, lineGap);
        this.lineSpacing = (int) (ascent[0] - descent[0] + lineGap[0]);
        this.descent = (int) Math.floor(descent[0]);
        this.textureNumber = textureNumber;

        try (var packedchars = STBTTPackedchar.malloc(GLYPH_COUNT)) {
            int texwidth = 256;
            int texheight = 128;
            ByteBuffer bitmap = BufferUtils.createByteBuffer(texwidth * texheight);
            try (STBTTPackRange.Buffer packRanges = STBTTPackRange.malloc(1)) {
                try (STBTTPackRange packRange = STBTTPackRange.malloc()) {
                    packRanges.put(packRange.set(fontSize, 32, null, GLYPH_COUNT, packedchars, (byte) 1, (byte) 1));
                    packRanges.flip();
                }
                try (STBTTPackContext pc = STBTTPackContext.malloc()) {
                    if (!stbtt_PackBegin(pc, bitmap, texwidth, texheight, 0, 1, NULL)) {
                        throw new IllegalStateException("Unable to initialize font atlas");
                    }
                    stbtt_PackSetOversampling(pc, 1, 1);
                    stbtt_PackSetSkipMissingCodepoints(pc, true);
                    if (!stbtt_PackFontRanges(pc, buf, 0, packRanges)) {
                        throw new IllegalStateException("Unable to pack font atlas");
                    }
                    stbtt_PackEnd(pc);
                }
            }

            ByteBuffer rgba = BufferUtils.createByteBuffer(texwidth * texheight * 4);
            for (int i = 0; i < bitmap.capacity(); i++) {
                rgba.put(bitmap.get(i)).put((byte) 0).put((byte) 0).put((byte) 0xff);
            }
            rgba.flip();
            // SimpleFont explicitly uses GL_CLAMP_TO_EDGE for both font-atlas axes.
            sink.texture(textureNumber, texwidth, texheight, rgba, true, true);

            try (var q = STBTTAlignedQuad.malloc()) {
                float[] x = new float[1];
                float[] y = new float[1];
                glyphs = new Glyph[GLYPH_COUNT];
                for (int i = 0; i < GLYPH_COUNT; i++) {
                    x[0] = 0f;
                    y[0] = fontSize;
                    stbtt_GetPackedQuad(packedchars, texwidth, texheight, i, x, y, q, true);
                    glyphs[i] = new Glyph((char) (i + 32), (int) x[0],
                            new int[] { (int) q.x0(), (int) q.y0(), (int) q.x1(), (int) q.y1() },
                            new float[] { q.s0(), q.t0(), q.s1(), q.t1() });
                }
            }
        }
    }

    int lineSpacing() { return lineSpacing; }
    int textureNumber() { return textureNumber; }
    int descent() { return descent; }

    int stringWidth(String text) {
        var bytes = text.getBytes(StandardCharsets.US_ASCII);
        int len = 0;
        for (byte c : bytes) {
            len += switch (c) {
                case '\n', '\t' -> 0;
                case ' ' -> glyphs[0].charwidth();
                default -> c - 32 < GLYPH_COUNT && c > 32 ? glyphs[c - 32].charwidth() : 0;
            };
        }
        return len;
    }

    private record Pos(int x, int y, int minx) {}

    record DisplayText(String string, int colour) {
        private byte[] asBytes() { return string.getBytes(StandardCharsets.US_ASCII); }

        Pos generateStringArray(SimpleFont font, Pos pos, FrameBuilder bb) {
            for (byte c : asBytes()) {
                pos = switch (c) {
                    case '\n' -> new Pos(pos.minx(), pos.y() + font.lineSpacing(), pos.minx());
                    case '\t' -> new Pos(pos.x() + font.glyphs[0].charwidth() * 4, pos.y(), pos.minx());
                    case ' ' -> new Pos(pos.x() + font.glyphs[0].charwidth(), pos.y(), pos.minx());
                    default -> {
                        if (c - 32 < GLYPH_COUNT && c > 32) pos = font.glyphs[c - 32].loadQuad(pos, colour(), bb);
                        yield pos;
                    }
                };
            }
            return pos;
        }
    }

    void generateVerticesForTexts(int x, int y, FrameBuilder textBB, DisplayText... texts) {
        var pos = new Pos(x, y, x);
        for (DisplayText text : texts) pos = text.generateStringArray(this, pos, textBB);
    }
}
