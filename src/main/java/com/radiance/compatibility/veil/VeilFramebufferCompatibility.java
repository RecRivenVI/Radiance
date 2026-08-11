package com.radiance.compatibility.veil;

import com.radiance.client.constant.VulkanConstants;
import com.radiance.client.proxy.vulkan.FramebufferProxy;
import com.radiance.client.proxy.vulkan.PipelineStateProxy;
import com.radiance.client.proxy.vulkan.TextureProxy;
import com.radiance.client.proxy.vulkan.RendererProxy;
import com.radiance.client.texture.TextureTracker;
import foundry.veil.api.client.render.framebuffer.AdvancedFbo;
import foundry.veil.api.client.render.framebuffer.AdvancedFboAttachment;
import foundry.veil.api.client.render.framebuffer.AdvancedFboTextureAttachment;
import foundry.veil.api.client.render.texture.TextureFilter;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.Locale;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/** Real Vulkan-backed implementation glue for Veil 4.3.2 framebuffer objects. */
public final class VeilFramebufferCompatibility {
    private static final int FRAMEBUFFER_COMPLETE = 0x8CD5;
    private static final int DEPTH_ATTACHMENT = 0x8D00;

    private static final Deque<FramebufferState> FRAMEBUFFER_STACK = new ArrayDeque<>();
    private static ResourceLocation lastPop;

    private VeilFramebufferCompatibility() {
    }

    @SuppressWarnings("unchecked")
    public static <T> T builder(int width, int height) {
        return (T) AdvancedFbo.withSize(width, height);
    }

    public static void create(AdvancedFbo framebuffer) {
        VeilAdvancedFboAccess access = access(framebuffer);
        if (access.radiance$getFramebufferId() != -1) {
            throw new IllegalStateException("Advanced framebuffer is already created");
        }

        int oldRead = FramebufferProxy.boundFramebuffer(FramebufferProxy.READ_FRAMEBUFFER);
        int oldDraw = FramebufferProxy.boundFramebuffer(FramebufferProxy.DRAW_FRAMEBUFFER);
        int id = -1;
        try {
            for (AdvancedFboAttachment attachment : access.radiance$getColorAttachments()) {
                attachment.create();
            }
            AdvancedFboAttachment depth = access.radiance$getDepthAttachmentOrNull();
            if (depth != null) {
                depth.create();
            }

            id = FramebufferProxy.createFramebuffer();
            access.radiance$setFramebufferId(id);
            FramebufferProxy.bindFramebuffer(FramebufferProxy.FRAMEBUFFER, id);
            AdvancedFboAttachment[] colors = access.radiance$getColorAttachments();
            for (int i = 0; i < colors.length; i++) {
                colors[i].attach(framebuffer, i);
            }
            if (depth != null) {
                depth.attach(framebuffer, 0);
            }
            int[] drawBuffers = framebuffer.getDrawBuffers();
            FramebufferProxy.drawBuffers(drawBuffers);
            FramebufferProxy.readBuffer(colors.length == 0 ? 0 : 0x8CE0);
            int status = FramebufferProxy.checkStatus(FramebufferProxy.FRAMEBUFFER);
            if (status != FRAMEBUFFER_COMPLETE) {
                throw new IllegalStateException("Advanced FBO status did not return "
                    + "GL_FRAMEBUFFER_COMPLETE: 0x"
                    + Integer.toHexString(status).toUpperCase(Locale.ROOT));
            }
            access.radiance$setCurrentDrawBuffers(drawBuffers);
        } catch (RuntimeException | Error failure) {
            access.radiance$setFramebufferId(-1);
            releaseAttachments(access, failure);
            if (id != -1) {
                try {
                    FramebufferProxy.deleteFramebuffer(id);
                } catch (RuntimeException | Error cleanupFailure) {
                    failure.addSuppressed(cleanupFailure);
                }
            }
            throw failure;
        } finally {
            restoreBindings(oldRead, oldDraw);
        }
    }

    public static void free(AdvancedFbo framebuffer) {
        VeilAdvancedFboAccess access = access(framebuffer);
        int id = access.radiance$getFramebufferId();
        if (id == -1) {
            return;
        }
        access.radiance$setFramebufferId(-1);
        RuntimeException failure = null;
        try {
            FramebufferProxy.deleteFramebuffer(id);
        } catch (RuntimeException exception) {
            failure = exception;
        }
        try {
            releaseAttachments(access, failure);
        } catch (RuntimeException exception) {
            if (failure == null) {
                failure = exception;
            } else {
                failure.addSuppressed(exception);
            }
        }
        if (failure != null) {
            throw failure;
        }
    }

    public static void clear(AdvancedFbo framebuffer, float red, float green, float blue,
        float alpha, float depth, int clearMask, int[] drawBuffers) {
        if (clearMask == 0) {
            return;
        }
        requireCreated(framebuffer);
        int oldDraw = FramebufferProxy.boundFramebuffer(FramebufferProxy.DRAW_FRAMEBUFFER);
        try {
            FramebufferProxy.bindFramebuffer(FramebufferProxy.DRAW_FRAMEBUFFER, framebuffer.getId());
            FramebufferProxy.clear(red, green, blue, alpha, depth, 0, clearMask, drawBuffers);
        } finally {
            FramebufferProxy.bindFramebuffer(FramebufferProxy.DRAW_FRAMEBUFFER, oldDraw);
        }
    }

    public static void drawBuffers(AdvancedFbo framebuffer, int[] buffers) {
        requireCreated(framebuffer);
        int oldDraw = FramebufferProxy.boundFramebuffer(FramebufferProxy.DRAW_FRAMEBUFFER);
        try {
            FramebufferProxy.bindFramebuffer(FramebufferProxy.DRAW_FRAMEBUFFER, framebuffer.getId());
            FramebufferProxy.drawBuffers(buffers);
            access(framebuffer).radiance$setCurrentDrawBuffers(buffers);
        } finally {
            FramebufferProxy.bindFramebuffer(FramebufferProxy.DRAW_FRAMEBUFFER, oldDraw);
        }
    }

    public static void resolve(AdvancedFbo source, int targetId, int width, int height,
        int mask, int filtering) {
        requireCreated(source);
        int oldRead = FramebufferProxy.boundFramebuffer(FramebufferProxy.READ_FRAMEBUFFER);
        int oldDraw = FramebufferProxy.boundFramebuffer(FramebufferProxy.DRAW_FRAMEBUFFER);
        try {
            FramebufferProxy.bindFramebuffer(FramebufferProxy.READ_FRAMEBUFFER, source.getId());
            FramebufferProxy.bindFramebuffer(FramebufferProxy.DRAW_FRAMEBUFFER, targetId);
            FramebufferProxy.blit(0, 0, source.getWidth(), source.getHeight(),
                0, 0, width, height, mask, filtering);
        } finally {
            restoreBindings(oldRead, oldDraw);
        }
    }

    public static void createTextureAttachment(AdvancedFboTextureAttachment attachment) {
        int levels = attachment.getLevels();
        int width = attachment.getWidth();
        int height = attachment.getHeight();
        if (levels <= 0 || width <= 0 || height <= 0) {
            throw new IllegalArgumentException("Invalid Veil attachment dimensions or mip levels");
        }
        validateTextureFilter(attachment.getFilter());
        int id = attachment.getId();
        FramebufferProxy.prepareAttachmentTexture(id, levels, width, height, attachment.getFormat());
        applyTextureFilter(id, attachment.getFilter().blur(), attachment.getFilter().mipmap(),
            attachment.getFilter().wrapX(), attachment.getFilter().wrapY());
        trackColorTexture(id, width, height, levels, attachment.getFormat());
    }

    public static void setTextureFilter(AdvancedFboTextureAttachment attachment,
        boolean blur, boolean mipmap) {
        validateTextureFilter(attachment.getFilter());
        applyTextureFilter(attachment.getId(), blur, mipmap, attachment.getFilter().wrapX(),
            attachment.getFilter().wrapY());
    }

    public static void attachTexture(AdvancedFbo framebuffer, int attachmentType,
        int attachmentIndex, int textureId) {
        requireCreated(framebuffer);
        if (attachmentType >= DEPTH_ATTACHMENT && attachmentIndex != 0) {
            throw new IllegalArgumentException("Only one depth buffer attachment is supported");
        }
        withFramebufferBound(framebuffer.getId(), () -> FramebufferProxy.attachTexture(
            FramebufferProxy.FRAMEBUFFER, attachmentType + attachmentIndex, textureId, 0));
    }

    public static void createRenderAttachment(int id, int format, int width, int height,
        int samples) {
        if (samples != 1) {
            throw new UnsupportedOperationException(
                "Veil multisampled render attachments are not supported: " + samples);
        }
        FramebufferProxy.bindRenderbuffer(id);
        try {
            FramebufferProxy.renderbufferStorage(format, width, height, samples);
        } finally {
            FramebufferProxy.bindRenderbuffer(0);
        }
    }

    public static int ensureRenderbuffer(int id) {
        return id == 0 ? FramebufferProxy.createRenderbuffer() : id;
    }

    public static void bindRenderbuffer(int id) {
        FramebufferProxy.bindRenderbuffer(id);
    }

    public static int releaseRenderbuffer(int id) {
        if (id != 0) {
            FramebufferProxy.deleteRenderbuffer(id);
        }
        return 0;
    }

    public static void attachRenderbuffer(AdvancedFbo framebuffer, int attachmentType,
        int attachmentIndex, int renderbufferId) {
        requireCreated(framebuffer);
        if (attachmentType >= DEPTH_ATTACHMENT && attachmentIndex != 0) {
            throw new IllegalArgumentException("Only one depth buffer attachment is supported");
        }
        withFramebufferBound(framebuffer.getId(), () -> FramebufferProxy.attachRenderbuffer(
            FramebufferProxy.FRAMEBUFFER, attachmentType + attachmentIndex, renderbufferId));
    }

    public static synchronized void push(@Nullable ResourceLocation name) {
        if (name != null && !FRAMEBUFFER_STACK.isEmpty()
            && name.equals(FRAMEBUFFER_STACK.peekLast().name())) {
            return;
        }
        int[] viewport = PipelineStateProxy.ViewportState.getViewport();
        if (viewport == null || viewport.length != 4) {
            throw new IllegalStateException("Vulkan viewport query did not return four values");
        }
        FRAMEBUFFER_STACK.addLast(new FramebufferState(Arrays.copyOf(viewport, 4),
            FramebufferProxy.boundFramebuffer(FramebufferProxy.READ_FRAMEBUFFER),
            FramebufferProxy.boundFramebuffer(FramebufferProxy.DRAW_FRAMEBUFFER), name));
        lastPop = null;
    }

    public static synchronized void pop(@Nullable ResourceLocation name) {
        if (lastPop != null && lastPop.equals(name)) {
            return;
        }
        if (FRAMEBUFFER_STACK.isEmpty()) {
            lastPop = null;
            return;
        }
        lastPop = name;
        FramebufferState state = FRAMEBUFFER_STACK.removeLast();
        restoreBindings(state.readFramebuffer(), state.drawFramebuffer());
        int[] viewport = state.viewport();
        com.mojang.blaze3d.systems.RenderSystem.viewport(viewport[0], viewport[1], viewport[2],
            viewport[3]);
    }

    public static synchronized void clearStack() {
        if (FRAMEBUFFER_STACK.isEmpty()) {
            return;
        }
        FramebufferState oldest = FRAMEBUFFER_STACK.peekFirst();
        FRAMEBUFFER_STACK.clear();
        lastPop = null;
        restoreBindings(oldest.readFramebuffer(), oldest.drawFramebuffer());
        int[] viewport = oldest.viewport();
        com.mojang.blaze3d.systems.RenderSystem.viewport(viewport[0], viewport[1], viewport[2],
            viewport[3]);
    }

    public static synchronized boolean isStackEmpty() {
        return FRAMEBUFFER_STACK.isEmpty();
    }

    public static int supportedColorAttachments() {
        return RendererProxy.capabilityLimit(0x8CDF);
    }

    public static int supportedSamples() {
        return 1;
    }

    private static void validateTextureFilter(TextureFilter filter) {
        if (filter.anisotropy() > 1.0F || filter.compareFunction() != null || filter.seamless()) {
            throw new UnsupportedOperationException(
                "Unsupported Veil attachment anisotropy, depth comparison, or seamless sampling");
        }
        if (filter.wrapX() != filter.wrapY()) {
            throw new UnsupportedOperationException(
                "Vulkan attachment textures require matching X/Y wrap modes");
        }
        if (filter.wrapX() != TextureFilter.Wrap.CLAMP_TO_EDGE
            && filter.wrapX() != TextureFilter.Wrap.REPEAT) {
            throw new UnsupportedOperationException(
                "Unsupported Veil attachment wrap mode: " + filter.wrapX());
        }
    }

    private static void applyTextureFilter(int id, boolean blur, boolean mipmap,
        TextureFilter.Wrap wrapX, TextureFilter.Wrap wrapY) {
        if (wrapX != wrapY) {
            throw new UnsupportedOperationException("Attachment wrap modes must match");
        }
        TextureProxy.setFilter(id,
            (blur ? VulkanConstants.VkFilter.VK_FILTER_LINEAR
                : VulkanConstants.VkFilter.VK_FILTER_NEAREST).getValue(),
            (mipmap && blur ? VulkanConstants.VkSamplerMipmapMode.VK_SAMPLER_MIPMAP_MODE_LINEAR
                : VulkanConstants.VkSamplerMipmapMode.VK_SAMPLER_MIPMAP_MODE_NEAREST).getValue());
        TextureProxy.setClamp(id,
            (wrapX == TextureFilter.Wrap.REPEAT
                ? VulkanConstants.VkSamplerAddressMode.VK_SAMPLER_ADDRESS_MODE_REPEAT
                : VulkanConstants.VkSamplerAddressMode.VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE)
                .getValue());
    }

    private static void trackColorTexture(int id, int width, int height, int levels, int format) {
        VulkanConstants.VkFormat vkFormat;
        int channels;
        switch (format) {
            case 0x1908, 0x8058 -> {
                vkFormat = VulkanConstants.VkFormat.VK_FORMAT_R8G8B8A8_UNORM;
                channels = 4;
            }
            case 0x8C43 -> {
                vkFormat = VulkanConstants.VkFormat.VK_FORMAT_R8G8B8A8_SRGB;
                channels = 4;
            }
            case 0x881B, 0x881A -> {
                vkFormat = VulkanConstants.VkFormat.VK_FORMAT_R16G16B16A16_SFLOAT;
                channels = 4;
            }
            default -> {
                return;
            }
        }
        TextureTracker.GLID2Texture.put(id,
            new TextureTracker.Texture(width, height, channels, vkFormat, levels - 1));
    }

    private static void withFramebufferBound(int id, Runnable action) {
        int oldRead = FramebufferProxy.boundFramebuffer(FramebufferProxy.READ_FRAMEBUFFER);
        int oldDraw = FramebufferProxy.boundFramebuffer(FramebufferProxy.DRAW_FRAMEBUFFER);
        try {
            FramebufferProxy.bindFramebuffer(FramebufferProxy.FRAMEBUFFER, id);
            action.run();
        } finally {
            restoreBindings(oldRead, oldDraw);
        }
    }

    private static void restoreBindings(int read, int draw) {
        FramebufferProxy.bindFramebuffer(FramebufferProxy.READ_FRAMEBUFFER, read);
        FramebufferProxy.bindFramebuffer(FramebufferProxy.DRAW_FRAMEBUFFER, draw);
    }

    private static void requireCreated(AdvancedFbo framebuffer) {
        if (framebuffer.getId() < 0) {
            throw new IllegalStateException("Advanced framebuffer has not been created");
        }
    }

    private static VeilAdvancedFboAccess access(AdvancedFbo framebuffer) {
        if (!(framebuffer instanceof VeilAdvancedFboAccess access)) {
            throw new IllegalArgumentException("Unsupported AdvancedFbo implementation: "
                + framebuffer.getClass().getName());
        }
        return access;
    }

    private static void releaseAttachments(VeilAdvancedFboAccess access,
        @Nullable Throwable allocationFailure) {
        RuntimeException cleanupFailure = null;
        for (AdvancedFboAttachment attachment : access.radiance$getColorAttachments()) {
            try {
                attachment.free();
            } catch (RuntimeException exception) {
                if (cleanupFailure == null) cleanupFailure = exception;
                else cleanupFailure.addSuppressed(exception);
            }
        }
        AdvancedFboAttachment depth = access.radiance$getDepthAttachmentOrNull();
        if (depth != null) {
            try {
                depth.free();
            } catch (RuntimeException exception) {
                if (cleanupFailure == null) cleanupFailure = exception;
                else cleanupFailure.addSuppressed(exception);
            }
        }
        if (cleanupFailure != null) {
            if (allocationFailure != null) allocationFailure.addSuppressed(cleanupFailure);
            else throw cleanupFailure;
        }
    }

    private record FramebufferState(int[] viewport, int readFramebuffer, int drawFramebuffer,
                                    @Nullable ResourceLocation name) {
    }
}
