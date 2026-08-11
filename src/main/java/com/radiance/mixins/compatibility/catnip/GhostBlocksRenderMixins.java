package com.radiance.mixins.compatibility.catnip;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.radiance.compatibility.catnip.CatnipWorldGeometryCapture;
import com.radiance.compatibility.outliner.RadianceOutlinerRenderer;
import net.createmod.catnip.render.SuperRenderTypeBuffer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;

@Pseudo
@Mixin(targets = "net.createmod.catnip.ghostblock.GhostBlocks", remap = false)
public abstract class GhostBlocksRenderMixins {
    @WrapMethod(method = "renderAll")
    private void radiance$captureGhosts(PoseStack pose, SuperRenderTypeBuffer buffer,
        Vec3 camera, Operation<Void> original) {
        if (!RadianceOutlinerRenderer.isActive()) {
            original.call(pose, buffer, camera);
            return;
        }
        try (CatnipWorldGeometryCapture capture =
            new CatnipWorldGeometryCapture(this, "radiance/ghost/", 0.0F) {
                @Override
                public VertexConsumer getBuffer(RenderType type) {
                    // Ghosts use translucent block layers for alpha blending, not glass.
                    // Keep the source atlas while selecting the ordinary coverage material.
                    if ((type.name.equals("translucent") || type.name.equals("translucent_moving_block"))
                        && type instanceof RenderType.CompositeRenderType composite) {
                        type = RenderType.entityTranslucent(composite.state.textureState
                            .cutoutTexture().orElseThrow());
                    }
                    return super.getBuffer(type);
                }
            }) {
            // Let the original renderer evaluate alphaSupplier, PlacementClient animation,
            // model transforms (including 0.85 scale), tint and UVs on every frame.
            original.call(new PoseStack(), capture, camera);
            capture.submit();
        }
    }
}
