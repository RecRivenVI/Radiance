package com.radiance.mixins.compatibility.voxy;

import com.radiance.compatibility.voxy.VoxySourceMaterials;
import me.cortex.voxy.client.core.model.ColourDepthTextureData;
import me.cortex.voxy.client.core.model.ModelFactory;
import me.cortex.voxy.client.core.model.ModelLayer;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = ModelFactory.class, remap = false)
public abstract class VoxySourceMaterialMixins {
    @Inject(method = "processTextureBakeResult", at = @At("RETURN"))
    private void radiance$sourceFluid(
            int id,
            BlockState state,
            ColourDepthTextureData[] faces,
            boolean shaded,
            boolean darkened,
            ModelLayer layer,
            CallbackInfoReturnable<?> ci) {
        ModelFactory factory = (ModelFactory) (Object) this;
        VoxySourceMaterials.record(factory, factory.getModelId(id), state);
    }
}
