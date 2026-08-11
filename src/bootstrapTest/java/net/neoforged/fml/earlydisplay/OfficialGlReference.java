/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.earlydisplay;

import static org.lwjgl.opengl.GL32C.*;

import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import net.neoforged.fml.loading.progress.ProgressMeter;
import net.neoforged.fml.loading.progress.StartupNotificationManager;
import org.lwjgl.BufferUtils;

/** Direct use of the released FML 4.0.43 renderer for the parity integration test. */
public final class OfficialGlReference {
    private OfficialGlReference() {}

    public static String rendererSourceLocation() {
        return String.valueOf(RenderElement.class.getProtectionDomain().getCodeSource().getLocation());
    }

    public static byte[] render(int width, int height, int scale, ColourScheme colourScheme,
                                String version, int frame, int alpha, float memory, String performanceText,
                                ByteBuffer mojangRgba, int mojangWidth, int mojangHeight, int mojangFrameStart,
                                boolean squirrel, boolean progressBar) {
        int framebuffer = glGenFramebuffers();
        int canvasTexture = glGenTextures();
        int mojangTexture = 0;
        ProgressMeter progress = null;
        ElementShader shader = new ElementShader();
        try {
            glBindTexture(GL_TEXTURE_2D, canvasTexture);
            glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, width, height, 0, GL_RGBA, GL_UNSIGNED_BYTE, 0L);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
            glBindFramebuffer(GL_FRAMEBUFFER, framebuffer);
            glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, canvasTexture, 0);
            if (glCheckFramebufferStatus(GL_FRAMEBUFFER) != GL_FRAMEBUFFER_COMPLETE) {
                throw new IllegalStateException("Official reference framebuffer is incomplete");
            }

            shader.init();
            SimpleFont font = new SimpleFont("Monocraft.ttf", scale, 200000, 1 + RenderElement.INDEX_TEXTURE_OFFSET);
            PerformanceInfo performance = fixedPerformance(memory, performanceText);
            RenderElement.DisplayContext context = new RenderElement.DisplayContext(854, 480, scale, shader, colourScheme, performance);
            List<RenderElement> elements = new ArrayList<>();
            if (mojangRgba != null) {
                mojangTexture = glGenTextures();
                glActiveTexture(GL_TEXTURE0);
                glBindTexture(GL_TEXTURE_2D, mojangTexture);
                glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
                glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
                glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, mojangWidth, mojangHeight, 0, GL_RGBA, GL_UNSIGNED_BYTE, mojangRgba);
                elements.add(RenderElement.mojang(mojangTexture, mojangFrameStart));
            }
            if (squirrel) elements.add(RenderElement.squir());
            elements.add(RenderElement.fox(font));
            elements.add(RenderElement.forgeVersionOverlay(font, version));
            elements.add(RenderElement.performanceBar(font));
            if (progressBar) {
                progress = StartupNotificationManager.addProgressBar("Step", 10);
                progress.setAbsolute(3);
                progress.label("Step");
                elements.add(RenderElement.progressBars(font));
            }

            glBindFramebuffer(GL_FRAMEBUFFER, framebuffer);
            glViewport(0, 0, width, height);
            shader.activate();
            shader.updateScreenSizeUniform(width, height);
            glClearColor(colourScheme.background().redf(), colourScheme.background().greenf(), colourScheme.background().bluef(), alpha / 255f);
            glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
            glEnable(GL_BLEND);
            glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
            RenderElement.globalAlpha = alpha;
            for (RenderElement element : elements) element.render(context, frame);
            glFinish();

            ByteBuffer pixels = BufferUtils.createByteBuffer(width * height * 4);
            glPixelStorei(GL_PACK_ALIGNMENT, 4);
            glReadPixels(0, 0, width, height, GL_RGBA, GL_UNSIGNED_BYTE, pixels);
            byte[] result = new byte[pixels.remaining()];
            pixels.get(result);
            return result;
        } finally {
            if (progress != null) progress.complete();
            shader.clear();
            shader.close();
            SimpleBufferBuilder.destroy();
            if (mojangTexture != 0) glDeleteTextures(mojangTexture);
            glDeleteFramebuffers(framebuffer);
            glDeleteTextures(canvasTexture);
            glBindFramebuffer(GL_FRAMEBUFFER, 0);
        }
    }

    private static PerformanceInfo fixedPerformance(float memory, String text) {
        try {
            PerformanceInfo result = new PerformanceInfo();
            result.memory = memory;
            Field textField = PerformanceInfo.class.getDeclaredField("text");
            textField.setAccessible(true);
            textField.set(result, text);
            return result;
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("FML 4.0.43 PerformanceInfo shape changed", e);
        }
    }
}
