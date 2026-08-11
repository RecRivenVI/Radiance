package com.radiance.compatibility.veil;

import com.mojang.blaze3d.systems.RenderSystem;
import com.radiance.client.proxy.vulkan.RendererProxy;
import com.radiance.client.render.AfterWorldRender;
import com.radiance.client.render.WorldRasterPass;
import foundry.veil.api.client.render.VeilRenderer;
import foundry.veil.api.client.render.VeilRenderSystem;
import foundry.veil.api.client.render.VeilShaderLimits;
import foundry.veil.api.event.VeilRenderLevelStageEvent;
import java.nio.IntBuffer;
import net.minecraft.client.Minecraft;
import net.minecraft.server.packs.resources.ReloadableResourceManager;
import org.lwjgl.system.MemoryUtil;

/**
 * Vulkan runtime presented to Veil.  Mixins only bridge Veil fields and call this adapter; all
 * capability policy and Radiance render scheduling stays in the compatibility layer.
 */
public final class VeilRuntimeAdapter {
    private static final int GL_MAX_COMBINED_TEXTURE_IMAGE_UNITS = 0x8B4D;
    private static final int GL_MAX_COLOR_ATTACHMENTS = 0x8CDF;
    private static final int GL_MAX_UNIFORM_BUFFER_BINDINGS = 0x8A2F;
    private static final int GL_MAX_VERTEX_ATTRIBS = 0x8869;
    private static final int GL_MAX_VERTEX_ATTRIB_RELATIVE_OFFSET = 0x82D9;
    private static final int GL_MAX_FRAMEBUFFER_WIDTH = 0x9315;
    private static final int GL_MAX_FRAMEBUFFER_HEIGHT = 0x9316;
    private static final int GL_UNIFORM_BUFFER_OFFSET_ALIGNMENT = 0x8A34;
    private static final int GL_MAX_UNIFORM_BLOCK_SIZE = 0x8A30;
    private static final int GL_MAX_VARYING_COMPONENTS = 0x9125;
    private static final int GL_MAX_VERTEX_OUTPUT_COMPONENTS = 0x9122;
    private static final int GL_VERTEX_SHADER = 0x8B31;
    private static final int GL_FRAGMENT_SHADER = 0x8B30;

    private VeilRuntimeAdapter() {
    }

    public static Initialization initialize(VeilRenderer current) {
        RenderSystem.assertOnRenderThreadOrInit();
        if (current != null) {
            return new Initialization(current, -1, null);
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (!(minecraft.getResourceManager() instanceof ReloadableResourceManager resources)) {
            throw new IllegalStateException("Veil requires a reloadable resource manager");
        }
        VeilRenderer renderer = new VeilRenderer(resources, minecraft.getWindow());
        IntBuffer emptySamplers = MemoryUtil.memCallocInt(maxCombinedTextureUnits());
        return new Initialization(renderer, -1, emptySamplers);
    }

    public static void drawScreenQuad() {
        if (!VeilScreenEffectAdapter.captureCurrentScreenQuad()) {
            VeilShaderBridge.drawScreenQuad();
        }
    }

    public static void validateUnallocatedScreenQuad(int id) {
        if (id > 0) {
            throw new IllegalStateException("Unexpected OpenGL VAO in Vulkan Veil lifecycle");
        }
    }

    public static boolean deferPost(VeilRenderLevelStageEvent.Stage stage) {
        if (stage != VeilRenderLevelStageEvent.Stage.AFTER_LEVEL || !AfterWorldRender.isActive()) {
            return false;
        }
        WorldRasterPass.defer(200, "veil:after_level_post", RenderSystem.getModelViewMatrix(),
            RenderSystem.getProjectionMatrix(), () -> VeilRenderSystem.renderPost(stage));
        return true;
    }

    public static boolean unsupportedOpenGlCapability() {
        return false;
    }

    public static boolean tessellationSupported() {
        return RendererProxy.tessellationSupported();
    }

    public static int maxCombinedTextureUnits() {
        return limit(GL_MAX_COMBINED_TEXTURE_IMAGE_UNITS);
    }

    public static int maxColorAttachments() {
        return limit(GL_MAX_COLOR_ATTACHMENTS);
    }

    public static int maxUniformBufferBindings() {
        return limit(GL_MAX_UNIFORM_BUFFER_BINDINGS);
    }

    public static int maxVertexAttributes() {
        return limit(GL_MAX_VERTEX_ATTRIBS);
    }

    public static int maxVertexAttributeRelativeOffset() {
        return limit(GL_MAX_VERTEX_ATTRIB_RELATIVE_OFFSET);
    }

    public static int maxFramebufferWidth() {
        return limit(GL_MAX_FRAMEBUFFER_WIDTH);
    }

    public static int maxFramebufferHeight() {
        return limit(GL_MAX_FRAMEBUFFER_HEIGHT);
    }

    public static int uniformBufferAlignment() {
        return limit(GL_UNIFORM_BUFFER_OFFSET_ALIGNMENT);
    }

    public static int singleSampleOrLayer() {
        return 1;
    }

    public static int unsupportedBindingCount() {
        return 0;
    }

    public static float maxTextureAnisotropy() {
        return 1.0F;
    }

    public static long maxUniformBufferSize() {
        return limit(GL_MAX_UNIFORM_BLOCK_SIZE);
    }

    public static long maxShaderStorageBufferSize() {
        return 0L;
    }

    public static VeilShaderLimits shaderLimits(int shader) {
        boolean vertex = shader == GL_VERTEX_SHADER;
        if (!vertex && shader != GL_FRAGMENT_SHADER) {
            throw new UnsupportedOperationException(
                "Unsupported Veil graphics stage " + shader);
        }
        return new VeilShaderLimits(limit(GL_MAX_UNIFORM_BLOCK_SIZE) / 4,
            limit(GL_MAX_UNIFORM_BUFFER_BINDINGS),
            vertex ? limit(GL_MAX_VERTEX_ATTRIBS) * 4 : limit(GL_MAX_VARYING_COMPONENTS),
            vertex ? limit(GL_MAX_VERTEX_OUTPUT_COMPONENTS) : limit(GL_MAX_COLOR_ATTACHMENTS) * 4,
            limit(GL_MAX_COMBINED_TEXTURE_IMAGE_UNITS), 0, 0, 0, 0);
    }

    public static String backendString(int name) {
        return RendererProxy.backendString(name);
    }

    private static int limit(int capability) {
        return RendererProxy.capabilityLimit(capability);
    }

    public record Initialization(VeilRenderer renderer, int screenQuadVao,
                                 IntBuffer emptySamplers) {
    }
}
