package com.radiance.mixins.vulkan_render_integration;

import com.radiance.client.render.StaticDrawStates;
import com.radiance.render.contract.DrawStateSnapshot.UvTransform;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * These factories own their immutable shards and UV parameters; custom factories remain captured.
 */
@Mixin(RenderType.class)
public abstract class VanillaDrawStateDeclarationMixins implements StaticDrawStates.SourceAccess {
    @Unique private UvTransform radiance$sourceState;

    public void radiance$declareState(UvTransform uv) {
        radiance$sourceState = uv;
    }

    public UvTransform radiance$declaredState() {
        return radiance$sourceState;
    }

    @Inject(
            method = {
                "armorCutoutNoCull",
                "entitySolid",
                "entityCutout",
                "entityCutoutNoCull",
                "entityCutoutNoCullZOffset",
                "itemEntityTranslucentCull",
                "entityTranslucentCull",
                "entityTranslucent",
                "entityTranslucentEmissive",
                "entitySmoothCutout",
                "entityDecal",
                "entityNoOutline",
                "eyes",
                "breezeEyes"
            },
            at = @At("RETURN"))
    private static void radiance$declarePlain(CallbackInfoReturnable<RenderType> ci) {
        StaticDrawStates.declare(ci.getReturnValue(), UvTransform.IDENTITY);
    }

    @Inject(
            method = {"energySwirl", "breezeWind"},
            at = @At("RETURN"))
    private static void radiance$declareScrolling(
            ResourceLocation texture, float u, float v, CallbackInfoReturnable<RenderType> ci) {
        StaticDrawStates.declare(ci.getReturnValue(), new UvTransform(1, 0, u, 0, 1, v));
    }
}
