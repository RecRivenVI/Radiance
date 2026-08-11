package com.radiance.mixins.compatibility.simulated;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.radiance.compatibility.simulated.SimulatedDiagramCompatibility;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.ryanhcode.sable.sublevel.render.vanilla.VanillaChunkedSubLevelRenderData;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher.RenderSection;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = VanillaChunkedSubLevelRenderData.class, remap = false)
public abstract class SimulatedDiagramSectionMixins {
    @Shadow @Final private ClientSubLevel subLevel;

    @WrapOperation(method = "renderChunkedSubLevel", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/renderer/chunk/SectionRenderDispatcher$RenderSection;getBuffer(Lnet/minecraft/client/renderer/RenderType;)Lcom/mojang/blaze3d/vertex/VertexBuffer;"))
    private VertexBuffer radiance$rasterMesh(RenderSection section, RenderType layer,
        Operation<VertexBuffer> original, RenderType renderedLayer, ShaderInstance shader,
        Matrix4f view, double x, double y, double z) {
        return SimulatedDiagramCompatibility.isRenderingDiagram()
            ? SimulatedDiagramCompatibility.buffer(section, layer, subLevel, x, y, z)
            : original.call(section, layer);
    }
    @WrapOperation(method = "renderChunkedSubLevel", at = @At(value = "INVOKE",
        target = "Lcom/mojang/blaze3d/vertex/VertexBuffer;bind()V"))
    private void radiance$bind(VertexBuffer buffer, Operation<Void> original) {
        if (buffer != null) original.call(buffer);
    }

    @WrapOperation(method = "renderChunkedSubLevel", at = @At(value = "INVOKE",
        target = "Lcom/mojang/blaze3d/vertex/VertexBuffer;draw()V"))
    private void radiance$draw(VertexBuffer buffer, Operation<Void> original) {
        if (buffer != null) original.call(buffer);
    }
}
