package com.radiance.mixins.vulkan_render_integration;

import com.radiance.client.vertex.BakedModelTakeover;
import java.util.function.Function;
import net.minecraft.client.renderer.block.model.BlockModel;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.*;
import net.neoforged.neoforge.client.model.geometry.UnbakedGeometryHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Declare actual standard-baker output, not every object of the same baked-model class. */
@Mixin(value = UnbakedGeometryHelper.class, remap = false)
public abstract class BakedResourceDeclarationMixins {
    @Inject(method = "bake", at = @At("RETURN"))
    private static void radiance$declareResource(
            BlockModel model,
            ModelBaker baker,
            BlockModel owner,
            Function<Material, TextureAtlasSprite> sprites,
            ModelState state,
            boolean guiLight,
            CallbackInfoReturnable<BakedModel> result) {
        if (model.getClass() == BlockModel.class && model.customData.getCustomGeometry() == null)
            BakedModelTakeover.declareBakedResource(result.getReturnValue());
    }
}
