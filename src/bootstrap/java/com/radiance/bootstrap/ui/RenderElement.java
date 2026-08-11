/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package com.radiance.bootstrap.ui;

import java.util.ArrayList;
import java.util.List;

final class RenderElement {
    static final int FONT = 0;
    static final int TEXTURE = 1;
    static final int BAR = 2;
    static final int INDEX_TEXTURE_OFFSET = 5;

    private final Renderer renderer;

    interface Renderer {
        void accept(FrameBuilder bb, DisplayContext context, LoadingScene.FrameInput input);

        default Renderer then(Renderer r) {
            if (r == null) return this;
            return (bb, ctx, input) -> {
                r.accept(bb, ctx, input);
                this.accept(bb, ctx, input);
            };
        }
    }

    record DisplayContext(int width, int height, int scale, ColourScheme colourScheme) {
        int scaledWidth() { return scale * width; }
        int scaledHeight() { return scale * height; }
    }

    RenderElement(Renderer renderer) {
        this.renderer = renderer;
    }

    void render(FrameBuilder bb, DisplayContext context, LoadingScene.FrameInput input) {
        renderer.accept(bb, context, input);
    }

    static RenderElement logMessageOverlay(SimpleFont font) {
        return new RenderElement((bb, context, input) -> {
            List<LoadingScene.MessageLine> messages = input.messages();
            List<SimpleFont.DisplayText> texts = new ArrayList<>();
            for (int i = messages.size() - 1; i >= 0; i--) {
                LoadingScene.MessageLine message = messages.get(i);
                float fade = clamp((4000.0f - message.ageMillis() - (i - 4) * 1000.0f) / 5000.0f, 0.0f, 1.0f);
                if (fade < 0.01f) continue;
                int colour = context.colourScheme.foreground().packedint(Math.min((int) (fade * 255f), input.globalAlpha()));
                texts.add(new SimpleFont.DisplayText(message.text() + "\n", colour));
            }
            textBatch(bb, font, () -> font.generateVerticesForTexts(10,
                    context.scaledHeight() - texts.size() * font.lineSpacing() + font.descent() - 10,
                    bb, texts.toArray(SimpleFont.DisplayText[]::new)));
        });
    }

    static RenderElement forgeVersionOverlay(SimpleFont font, String version) {
        return new RenderElement((bb, context, input) -> textBatch(bb, font,
                () -> font.generateVerticesForTexts(context.scaledWidth() - font.stringWidth(version) - 10,
                        context.scaledHeight() - font.lineSpacing() + font.descent() - 10, bb,
                        new SimpleFont.DisplayText(version, context.colourScheme.foreground().packedint(input.globalAlpha())))));
    }

    static RenderElement squirrel(int[] size) {
        return new RenderElement((bb, context, input) -> {
            float inset = 5f;
            float x0 = inset;
            float x1 = inset + size[0] * context.scale;
            float y0 = inset;
            float y1 = inset + size[1] * context.scale;
            int fade = (int) (Math.cos(input.animationFrame() * Math.PI / 16) * 16) + 16;
            int colour = (Math.min(fade, input.globalAlpha()) & 0xff) << 24 | 0xffffff;
            textureBatch(bb, LoadingScene.SQUIRREL_TEXTURE, () -> bb.quad(x0, x1, y0, y1, 0f, 1f, 0f, 1f, colour));
        });
    }

    static RenderElement fox(SimpleFont font, int[] size) {
        return new RenderElement((bb, context, input) -> {
            int framecount = 28;
            float aspect = size[0] * (float) framecount / size[1];
            int outsize = size[0];
            int offset = outsize / 6;
            float x0 = context.scaledWidth() - outsize * context.scale + offset;
            float x1 = context.scaledWidth() + offset;
            float y0 = context.scaledHeight() - outsize * context.scale / aspect - font.descent() - font.lineSpacing();
            float y1 = context.scaledHeight() - font.descent() - font.lineSpacing();
            int frameidx = input.animationFrame() % framecount;
            float framesize = 1 / (float) framecount;
            float framepos = frameidx * framesize;
            int colour = input.globalAlpha() << 24 | 0xffffff;
            textureBatch(bb, LoadingScene.FOX_TEXTURE,
                    () -> bb.quad(x0, x1, y0, y1, 0f, 1f, framepos, framepos + framesize, colour));
        });
    }

    static RenderElement mojang(int textureKey, int frameStart) {
        return new RenderElement((bb, context, input) -> {
            int size = 256 * context.scale;
            int x0 = (context.scaledWidth() - 2 * size) / 2;
            int y0 = 64 * context.scale + 32;
            int fade = Math.min((input.animationFrame() - frameStart) * 10, 255);
            int colour = context.colourScheme.foreground().packedint(fade);
            textureBatch(bb, textureKey, () -> {
                bb.quad(x0, x0 + size, y0, y0 + size / 2f, 0f, 1f, 0f, 0.5f, colour);
                bb.quad(x0 + size, x0 + 2 * size, y0, y0 + size / 2f, 0f, 1f, 0.5f, 1f, colour);
            });
        });
    }

    static RenderElement progressBars(SimpleFont font) {
        return new RenderElement((bb, context, input) -> {
            Renderer acc = null;
            int size = input.progress().size();
            for (int i = 0; i < 2 && i < size; i++) {
                Renderer barRenderer = barRenderer(i, 0xff, font, input.progress().get(i), context);
                acc = barRenderer.then(acc);
            }
            if (acc != null) acc.accept(bb, context, input);
        });
    }

    static RenderElement performanceBar(SimpleFont font) {
        return new RenderElement((bb, context, input) -> memoryInfo(font, bb, context, input));
    }

    private static final int BAR_HEIGHT = 20;
    private static final int BAR_WIDTH = 400;

    private static Renderer barRenderer(int cnt, int alpha, SimpleFont font, LoadingScene.ProgressBar pm, DisplayContext context) {
        int barSpacing = font.lineSpacing() - font.descent() + BAR_HEIGHT;
        int y = 250 * context.scale + cnt * barSpacing;
        int colour = context.colourScheme.foreground().packedint(alpha);
        Renderer bar;
        if (pm.steps() == 0) {
            bar = progressBar(new int[] { (context.scaledWidth() - BAR_WIDTH * context.scale) / 2,
                    y + font.lineSpacing() - font.descent(), BAR_WIDTH * context.scale }, colour,
                    input -> indeterminateBar(input.animationFrame(), cnt == 0, input.globalAlpha()));
        } else {
            bar = progressBar(new int[] { (context.scaledWidth() - BAR_WIDTH * context.scale) / 2,
                    y + font.lineSpacing() - font.descent(), BAR_WIDTH * context.scale }, colour,
                    input -> new float[] { 0f, pm.progress() });
        }
        Renderer label = (bb, ctx, input) -> textBatch(bb, font,
                () -> font.generateVerticesForTexts((ctx.scaledWidth() - BAR_WIDTH * ctx.scale) / 2, y, bb,
                        new SimpleFont.DisplayText(pm.label(), colour)));
        return bar.then(label);
    }

    private static float[] indeterminateBar(int frame, boolean active, int globalAlpha) {
        if (globalAlpha != 0xff || !active) return new float[] { 0f, 1f };
        int progress = frame % 100;
        return new float[] { clamp((progress - 2) / 100f, 0f, 1f), clamp((progress + 2) / 100f, 0f, 1f) };
    }

    private static void memoryInfo(SimpleFont font, FrameBuilder bb, DisplayContext context, LoadingScene.FrameInput input) {
        int y = 10 * context.scale;
        LoadingScene.PerformanceSnapshot performance = input.performance();
        int colour = hsvToRGB((1.0f - (float) Math.pow(performance.memory(), 1.5f)) / 3f, 1.0f, 0.5f);
        Renderer bar = progressBar(new int[] { (context.scaledWidth() - BAR_WIDTH * context.scale) / 2, y,
                BAR_WIDTH * context.scale }, colour, ignored -> new float[] { 0f, performance.memory() });
        int width = font.stringWidth(performance.text());
        Renderer label = (builder, ctx, state) -> textBatch(builder, font,
                () -> font.generateVerticesForTexts(ctx.scaledWidth() / 2 - width / 2, y + 18, builder,
                        new SimpleFont.DisplayText(performance.text(), ctx.colourScheme.foreground().packedint(state.globalAlpha()))));
        bar.then(label).accept(bb, context, input);
    }

    private interface ProgressDisplay { float[] progress(LoadingScene.FrameInput input); }

    private static Renderer progressBar(int[] pos, int colour, ProgressDisplay display) {
        return (bb, context, input) -> {
            int alpha = (colour & 0xff000000) >> 24;
            float[] progress = display.progress(input);
            bb.begin(BAR, 0);
            int inset = 2;
            float x0 = pos[0];
            float x1 = pos[0] + pos[2] + 4 * inset;
            float y0 = pos[1];
            float y1 = y0 + BAR_HEIGHT;
            bb.quad(x0, x1, y0, y1, 0f, 0f, 0f, 0f, context.colourScheme.foreground().packedint(alpha));
            x0 += inset;
            x1 -= inset;
            y0 += inset;
            y1 -= inset;
            bb.quad(x0, x1, y0, y1, 0f, 0f, 0f, 0f, context.colourScheme.background().packedint(input.globalAlpha()));
            x1 = x0 + inset + (int) (progress[1] * pos[2]);
            x0 += inset + progress[0] * pos[2];
            y0 += inset;
            y1 -= inset;
            bb.quad(x0, x1, y0, y1, 0f, 0f, 0f, 0f, colour);
            bb.end();
        };
    }

    private static void textBatch(FrameBuilder bb, SimpleFont font, Runnable vertices) {
        bb.begin(FONT, font.textureNumber());
        vertices.run();
        bb.end();
    }

    private static void textureBatch(FrameBuilder bb, int textureKey, Runnable vertices) {
        bb.begin(TEXTURE, textureKey);
        vertices.run();
        bb.end();
    }

    static float clamp(float num, float min, float max) {
        if (num < min) return min;
        return num > max ? max : num;
    }

    static int clamp(int num, int min, int max) {
        if (num < min) return min;
        return num > max ? max : num;
    }

    static int hsvToRGB(float hue, float saturation, float value) {
        int i = (int) (hue * 6.0F) % 6;
        float f = hue * 6.0F - i;
        float f1 = value * (1.0F - saturation);
        float f2 = value * (1.0F - f * saturation);
        float f3 = value * (1.0F - (1.0F - f) * saturation);
        float r;
        float g;
        float b;
        switch (i) {
            case 0 -> { r = value; g = f3; b = f1; }
            case 1 -> { r = f2; g = value; b = f1; }
            case 2 -> { r = f1; g = value; b = f3; }
            case 3 -> { r = f1; g = f2; b = value; }
            case 4 -> { r = f3; g = f1; b = value; }
            case 5 -> { r = value; g = f1; b = f2; }
            default -> throw new IllegalStateException("Invalid HSV colour");
        }
        return 0xff000000 | clamp((int) (r * 255), 0, 255) << 16 | clamp((int) (g * 255), 0, 255) << 8 | clamp((int) (b * 255), 0, 255);
    }
}
