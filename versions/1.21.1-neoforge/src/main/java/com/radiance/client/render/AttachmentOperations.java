package com.radiance.client.render;

import com.radiance.api.audit.DrawingAudit;
import com.radiance.client.proxy.vulkan.FramebufferProxy;

/** Immediate attachment dependencies; these operations are never deferred to frame end. */
public final class AttachmentOperations {
    private AttachmentOperations() {}

    public static void readPixels(
            int x, int y, int width, int height, int format, int type, long destination) {
        try (var request =
                DrawingAudit.begin(
                        "READBACK", "readPixels", "VULKAN_RASTER", "BOUND_READ_ATTACHMENT")) {
            FramebufferProxy.readPixels(x, y, width, height, format, type, destination);
            DrawingAudit.consumed("native.framebuffer.readPixels");
        }
    }

    public static void clear(
            float r,
            float g,
            float b,
            float a,
            float depth,
            int stencil,
            int mask,
            int[] attachments) {
        try (var request =
                DrawingAudit.begin(
                        "ATTACHMENT", "clear", "VULKAN_RASTER", "BOUND_DRAW_ATTACHMENT")) {
            FramebufferProxy.clear(r, g, b, a, depth, stencil, mask, attachments);
            DrawingAudit.consumed("native.framebuffer.clear");
        }
    }

    public static void blit(
            int sx0,
            int sy0,
            int sx1,
            int sy1,
            int dx0,
            int dy0,
            int dx1,
            int dy1,
            int mask,
            int filter) {
        try (var request =
                DrawingAudit.begin("ATTACHMENT", "blit", "VULKAN_RASTER", "BOUND_READ_TO_DRAW")) {
            FramebufferProxy.blit(sx0, sy0, sx1, sy1, dx0, dy0, dx1, dy1, mask, filter);
            DrawingAudit.consumed("native.framebuffer.blit");
        }
    }
}
