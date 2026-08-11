/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package com.radiance.bootstrap.ui;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import net.neoforged.fml.loading.progress.StartupNotificationManager;

/**
 * Source-faithful NeoForge 4.0.43 loading layout which emits backend-neutral GPU draw lists.
 */
public final class LoadingScene implements AutoCloseable {
    public static final int VERTEX_BYTES = FrameBuilder.VERTEX_BYTES;
    public static final int BATCH_BYTES = FrameBuilder.BATCH_BYTES;
    public static final int ROLE_FONT = RenderElement.FONT;
    public static final int ROLE_TEXTURE = RenderElement.TEXTURE;
    public static final int ROLE_BAR = RenderElement.BAR;
    public static final int FONT_TEXTURE = 1 + RenderElement.INDEX_TEXTURE_OFFSET;
    public static final int FOX_TEXTURE = 2 + RenderElement.INDEX_TEXTURE_OFFSET;
    public static final int SQUIRREL_TEXTURE = 3 + RenderElement.INDEX_TEXTURE_OFFSET;

    private static final long ANIMATION_INTERVAL = TimeUnit.MILLISECONDS.toNanos(50);
    private static final long PERFORMANCE_INTERVAL = TimeUnit.MILLISECONDS.toNanos(500);

    private final DrawSink sink;
    private final RenderElement.DisplayContext context;
    private final List<RenderElement> elements = new ArrayList<>();
    private final PerformanceInfo performanceInfo = new PerformanceInfo();
    private PerformanceSnapshot performance;
    private long nextAnimationTime;
    private long nextPerformanceTime;
    private int animationFrame;
    private Frame lastFrame;
    private boolean closed;

    public LoadingScene(int framebufferScale, ColourScheme colourScheme, String minecraftVersion,
                        String neoForgeVersion, boolean showSquirrel, DrawSink sink) {
        if (framebufferScale < 1) throw new IllegalArgumentException("framebufferScale must be positive");
        this.sink = Objects.requireNonNull(sink, "sink");
        Objects.requireNonNull(colourScheme, "colourScheme");
        Objects.requireNonNull(neoForgeVersion, "neoForgeVersion");
        this.context = new RenderElement.DisplayContext(854, 480, framebufferScale, colourScheme);

        SimpleFont font = new SimpleFont("Monocraft.ttf", framebufferScale, 200000, FONT_TEXTURE, sink);
        int[] foxSize = STBHelper.loadTextureFromClasspath("fox_running.png", 128000, FOX_TEXTURE, sink);
        elements.add(RenderElement.fox(font, foxSize));
        elements.add(RenderElement.logMessageOverlay(font));
        elements.add(RenderElement.forgeVersionOverlay(font,
                minecraftVersion + "-" + neoForgeVersion.split("-", 2)[0]));
        elements.add(RenderElement.performanceBar(font));
        elements.add(RenderElement.progressBars(font));
        if (showSquirrel) {
            int[] squirrelSize = STBHelper.loadTextureFromClasspath("squirrel.png", 45000, SQUIRREL_TEXTURE, sink);
            elements.add(0, RenderElement.squirrel(squirrelSize));
        }

        long now = System.nanoTime();
        this.performance = performanceInfo.update();
        this.nextPerformanceTime = now + PERFORMANCE_INTERVAL;
        this.nextAnimationTime = now;
    }

    /** Captures the current FML messages/progress and emits one frame. */
    public synchronized Frame render(int alpha) {
        ensureOpen();
        long now = System.nanoTime();
        if (now >= nextPerformanceTime) {
            performance = performanceInfo.update();
            nextPerformanceTime = now + PERFORMANCE_INTERVAL;
        }
        FrameInput input = new FrameInput(animationFrame, alpha,
                StartupNotificationManager.getMessages().stream()
                        .map(message -> new MessageLine(message.message().getText(), message.age()))
                        .toList(),
                StartupNotificationManager.getCurrentProgress().stream()
                        .map(progress -> new ProgressBar(progress.label().getText(), progress.steps(), progress.progress()))
                        .toList(),
                performance);
        Frame result = renderInput(input);
        if (now >= nextAnimationTime) {
            animationFrame++;
            nextAnimationTime = now + ANIMATION_INTERVAL;
        }
        return result;
    }

    /** Emits a frame without consulting clocks, MXBeans, or mutable FML loading state. */
    public synchronized Frame render(FrameInput input) {
        ensureOpen();
        return renderInput(Objects.requireNonNull(input, "input"));
    }

    private Frame renderInput(FrameInput input) {
        FrameBuilder builder = new FrameBuilder();
        for (RenderElement element : elements) element.render(builder, context, input);
        lastFrame = builder.finish(context.scaledWidth(), context.scaledHeight(),
                context.colourScheme().background().packedint(input.globalAlpha()));
        return lastFrame;
    }

    public synchronized Frame lastFrame() {
        return lastFrame;
    }

    public int backgroundAbgr() {
        return context.colourScheme().background().packedint(255);
    }

    public synchronized int animationFrame() {
        return animationFrame;
    }

    public synchronized int animatedFrame() {
        return animationFrame;
    }

    /** Adds the already-uploaded Mojang texture at the current live animation frame. */
    public synchronized void addMojangTexture(int textureKey) {
        addMojangTexture(textureKey, animationFrame);
    }

    /** Adds the already-uploaded Mojang texture with an explicit fade start for reference rendering. */
    public synchronized void addMojangTexture(int textureKey, int frameStart) {
        ensureOpen();
        elements.add(0, RenderElement.mojang(textureKey, frameStart));
    }

    /** Uploads and adds a Mojang texture when the caller owns RGBA pixels rather than a native texture key. */
    public synchronized void addMojangTexture(int textureKey, int width, int height, ByteBuffer rgba, boolean linear) {
        addMojangTexture(textureKey, width, height, rgba, linear, true);
    }

    /** Uploads and adds a Mojang texture with its source texture's explicit wrap mode. */
    public synchronized void addMojangTexture(int textureKey, int width, int height, ByteBuffer rgba,
                                               boolean linear, boolean clampToEdge) {
        ensureOpen();
        Objects.requireNonNull(rgba, "rgba");
        if (width < 1 || height < 1 || rgba.remaining() < Math.multiplyExact(Math.multiplyExact(width, height), 4)) {
            throw new IllegalArgumentException("Invalid RGBA texture dimensions or buffer");
        }
        sink.texture(textureKey, width, height, rgba, linear, clampToEdge);
        addMojangTexture(textureKey);
    }

    @Override
    public synchronized void close() {
        closed = true;
        elements.clear();
        lastFrame = null;
    }

    private void ensureOpen() {
        if (closed) throw new IllegalStateException("LoadingScene is closed");
    }

    public record MessageLine(String text, int ageMillis) {
        public MessageLine { Objects.requireNonNull(text, "text"); }
    }

    public record ProgressBar(String label, int steps, float progress) {
        public ProgressBar { Objects.requireNonNull(label, "label"); }
    }

    public record PerformanceSnapshot(float memory, String text) {
        public PerformanceSnapshot { Objects.requireNonNull(text, "text"); }
    }

    public record FrameInput(int animationFrame, int globalAlpha, List<MessageLine> messages,
                             List<ProgressBar> progress, PerformanceSnapshot performance) {
        public FrameInput {
            if (globalAlpha < 0 || globalAlpha > 255) throw new IllegalArgumentException("globalAlpha must be 0..255");
            messages = List.copyOf(messages);
            progress = List.copyOf(progress);
            Objects.requireNonNull(performance, "performance");
        }
    }

    public record Frame(ByteBuffer vertices, ByteBuffer batches, int batchCount,
                        int canvasWidth, int canvasHeight, int backgroundAbgr) {
        public Frame {
            Objects.requireNonNull(vertices, "vertices");
            Objects.requireNonNull(batches, "batches");
            if (!vertices.isDirect() || !batches.isDirect()) throw new IllegalArgumentException("Frame buffers must be direct");
            if (vertices.remaining() % FrameBuilder.VERTEX_BYTES != 0) throw new IllegalArgumentException("Invalid vertex buffer");
            if (batches.remaining() != batchCount * FrameBuilder.BATCH_BYTES) throw new IllegalArgumentException("Invalid batch buffer");
            vertices = vertices.asReadOnlyBuffer().order(ByteOrder.nativeOrder());
            batches = batches.asReadOnlyBuffer().order(ByteOrder.nativeOrder());
        }
    }
}
