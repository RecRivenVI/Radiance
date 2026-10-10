package com.radiance.audit.mixin;

import com.mojang.blaze3d.systems.RenderSystem;
import com.radiance.audit.SpringMenuProbe;
import com.radiance.client.proxy.vulkan.BufferProxy;
import com.radiance.client.proxy.vulkan.FramebufferProxy;
import com.radiance.client.proxy.vulkan.PipelineStateProxy;
import com.radiance.client.proxy.vulkan.ShaderProxy;
import com.radiance.client.proxy.vulkan.TextureProxy;
import com.radiance.client.render.MaterialFaces;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import net.minecraft.client.Minecraft;
import org.lwjgl.system.MemoryUtil;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Opt-in exact Java→native spring submission inputs for one menu FBO. */
@Mixin(value = ShaderProxy.class, remap = false)
public abstract class SpringMenuDrawStateMixin {
    @Inject(
            method =
                    "draw(Lcom/radiance/client/proxy/vulkan/BufferProxy$VertexIndexBufferHandle;IIIJI)V",
            at = @At("HEAD"),
            remap = false)
    private static void audit$springDraw(
            BufferProxy.VertexIndexBufferHandle buffers,
            int shaderId,
            int indexCount,
            int indexType,
            long uniformPtr,
            int uniformSize,
            CallbackInfo ci) {
        int ordinal = SpringMenuProbe.drawOrdinal();
        if (ordinal < 0) return;
        try {
            var output =
                    Minecraft.getInstance()
                            .gameDirectory
                            .toPath()
                            .resolve("radiance-audit/spring-menu");
            Files.createDirectories(output);
            byte[] uniform = new byte[uniformSize];
            MemoryUtil.memByteBuffer(uniformPtr, uniformSize).get(uniform);
            Files.write(
                    output.resolve("uniform-" + ordinal + ".bin"),
                    uniform,
                    StandardOpenOption.CREATE_NEW);
            int raw0 = RenderSystem.getShaderTexture(0);
            int raw2 = RenderSystem.getShaderTexture(2);
            int effective0 = TextureProxy.effectiveTexture(0, raw0);
            int effective2 = TextureProxy.effectiveTexture(2, raw2);
            SpringMenuProbe.rememberTextures(effective0, effective2);
            int[] viewport = PipelineStateProxy.ViewportState.getViewport();
            int drawFbo = FramebufferProxy.boundFramebuffer(FramebufferProxy.DRAW_FRAMEBUFFER);
            int[] extent = FramebufferProxy.dimensions(FramebufferProxy.DRAW_FRAMEBUFFER);
            int count = SpringMenuProbe.nativeDrawRecorded();
            com.mojang.logging.LogUtils.getLogger()
                    .info(
                            "SPRING_MENU native-call count={} ordinal={} shader={} verticesBuffer={} indexBuffer={} "
                                    + "indices={} indexType={} uniformBytes={} rawSampler0={} effectiveSampler0={} "
                                    + "rawSampler2={} effectiveSampler2={} faceFlags={} viewport={},{},{},{} "
                                    + "drawFbo={} extent={}x{}",
                            count,
                            ordinal,
                            shaderId,
                            buffers.vertexId,
                            buffers.indexId,
                            indexCount,
                            indexType,
                            uniformSize,
                            raw0,
                            effective0,
                            raw2,
                            effective2,
                            MaterialFaces.current(),
                            viewport[0],
                            viewport[1],
                            viewport[2],
                            viewport[3],
                            drawFbo,
                            extent[0],
                            extent[1]);
        } catch (Exception failure) {
            throw new IllegalStateException("Cannot retain spring native-call evidence", failure);
        }
    }
}
