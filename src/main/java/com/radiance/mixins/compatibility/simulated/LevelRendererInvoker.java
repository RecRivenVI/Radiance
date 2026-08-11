package com.radiance.mixins.compatibility.simulated;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(LevelRenderer.class)
public interface LevelRendererInvoker {

    @Invoker("renderEntity")
    void radiance$renderEntity(Entity entity, double cameraX, double cameraY, double cameraZ,
        float partialTick, PoseStack poseStack, MultiBufferSource bufferSource);

    /** Vanilla's block outline edge emitter, used instead of a duplicated local implementation. */
    @Invoker("renderShape")
    static void radiance$renderShape(PoseStack poseStack, VertexConsumer consumer, VoxelShape shape,
        double x, double y, double z, float red, float green, float blue, float alpha) {
        throw new AssertionError("Mixin invoker was not transformed");
    }
}
