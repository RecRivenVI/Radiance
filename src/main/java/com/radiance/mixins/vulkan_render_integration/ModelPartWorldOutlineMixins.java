package com.radiance.mixins.vulkan_render_integration;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.radiance.client.render.WorldOutlineGeometry;
import com.radiance.client.vertex.StorageOutlineVertexConsumerProvider.WorldOutlineVertexConsumer;
import java.util.List;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ModelPart.class)
public abstract class ModelPartWorldOutlineMixins {

    @Shadow
    @Final
    private List<ModelPart.Cube> cubes;

    @Inject(method = "compile", at = @At("HEAD"))
    private void radiance$emitMergedWorldOutline(PoseStack.Pose pose, VertexConsumer consumer,
        int light, int overlay, int color, CallbackInfo ci) {
        if (!(consumer instanceof WorldOutlineVertexConsumer outlineConsumer)
            || this.cubes.isEmpty()) {
            return;
        }

        VoxelShape partShape = Shapes.empty();
        for (ModelPart.Cube cube : this.cubes) {
            AABB bounds = new AABB(cube.minX / 16.0F, cube.minY / 16.0F, cube.minZ / 16.0F,
                cube.maxX / 16.0F, cube.maxY / 16.0F, cube.maxZ / 16.0F);
            partShape = Shapes.or(partShape, Shapes.create(bounds));
        }
        WorldOutlineGeometry.emitShapeEdges(pose, outlineConsumer.outlineDelegate(),
            partShape, outlineConsumer.outlineColor());
    }
}
