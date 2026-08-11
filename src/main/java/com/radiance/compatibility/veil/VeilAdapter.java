package com.radiance.compatibility.veil;

import java.util.Set;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import org.joml.Quaternionf;
import org.joml.Quaternionfc;
import org.joml.Vector3dc;
import org.objectweb.asm.tree.ClassNode;

/**
 * Stable boundary used by Radiance core and other compatibility modules.
 *
 * <p>Veil-specific implementation classes stay behind this facade.  Veil mixins may call the
 * specialized adapters in this package, while non-Veil code must enter through this class.</p>
 */
public final class VeilAdapter {
    public static foundry.veil.api.event.VeilRenderLevelStageEvent pathTraceStaffStage(
        foundry.veil.api.event.VeilRenderLevelStageEvent original) {
        return VeilStageAdapter.pathTraceStaff(original);
    }

    public static <T> T framebufferBuilder(int width, int height) {
        return VeilFramebufferCompatibility.builder(width, height);
    }

    public static int framebufferWidth(Object framebuffer) {
        return ((foundry.veil.api.client.render.framebuffer.AdvancedFbo) framebuffer).getWidth();
    }

    public static int framebufferHeight(Object framebuffer) {
        return ((foundry.veil.api.client.render.framebuffer.AdvancedFbo) framebuffer).getHeight();
    }

    public static void bindFramebufferRead(Object framebuffer) {
        ((foundry.veil.api.client.render.framebuffer.AdvancedFbo) framebuffer).bindRead();
    }

    private VeilAdapter() {
    }

    public static void postApplyMixin(String targetClassName, ClassNode targetClass) {
        VeilMixinCompatibility.postApply(targetClassName, targetClass);
    }

    public static boolean shouldSkipRenderState(Object stateShard) {
        return VeilRenderStateCompatibility.shouldSkip(stateShard);
    }

    public static void drawIndirect(int commandBuffer, long offset, int drawCount,
        int stride) {
        VeilVertexArrayBridge.drawIndirect(commandBuffer, offset, drawCount, stride);
    }

    public static Runnable captureCameraMatrices() {
        return VeilInteropAdapter.captureCameraMatrices();
    }

    public static <T> T createRenderBridge(Frustum frustum) {
        return VeilInteropAdapter.createRenderBridge(frustum);
    }

    public static void setBlockLayers(Set<RenderType> layers) {
        VeilInteropAdapter.setBlockLayers(layers);
    }

    public static void validateDiagramProgram(ResourceLocation name, Object program) {
        VeilSimulatedShaderAdapter.requireDiagramProgram(name, program);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static <T> T getSableDynamicProgram(
        Map<String, CompletableFuture<T>> programs, ShaderInstance shader) {
        return (T) VeilSableShaderAdapter.getDynamicProgram((Map) programs, shader);
    }

    public static PerspectiveCamera createPerspectiveCamera() {
        return new VeilPerspectiveCameraAdapter();
    }

    public interface PerspectiveCamera {
        void setup(Vector3dc position, Entity entity, ClientLevel level,
            Quaternionfc orientation, float partialTick);

        Quaternionf rotation();
    }
}
