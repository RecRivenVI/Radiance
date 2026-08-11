package com.radiance.mixins.compatibility.veil;

import net.minecraft.client.renderer.EffectInstance;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Applies Radiance's mixin plugin compatibility pass after Veil augments effect shaders.
 */
@Mixin(EffectInstance.class)
public abstract class VeilShaderEffectInstanceCompatibilityMixins {
}
