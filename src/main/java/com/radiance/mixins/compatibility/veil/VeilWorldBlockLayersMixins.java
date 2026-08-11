package com.radiance.mixins.compatibility.veil;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.radiance.compatibility.veil.VeilWorldGeometryAdapter;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import org.joml.Matrix4fc;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

/** Supplies actual uploaded ordinary sections to Veil's existing extra-layer renderer. */
@Mixin(value = LevelRenderer.class, priority = 900)
public abstract class VeilWorldBlockLayersMixins {
    @Shadow @Final private ObjectArrayList<SectionRenderDispatcher.RenderSection> visibleSections;

    @WrapMethod(method = "veil$drawBlockLayer", remap = false)
    private void radiance$drawUploadedSections(RenderType layer, double x, double y, double z,
        Matrix4fc view, Matrix4fc projection, Operation<Void> original) {
        VeilWorldGeometryAdapter.withUploadedSections(visibleSections, x, y, z,
            () -> original.call(layer, x, y, z, view, projection));
    }
}
