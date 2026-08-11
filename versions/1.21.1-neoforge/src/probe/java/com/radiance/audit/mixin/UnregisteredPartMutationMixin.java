package com.radiance.audit.mixin;

import net.minecraft.client.model.geom.ModelPart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Deliberately NOT registered as compatible: source output changes, so takeover must retreat. */
@Mixin(ModelPart.Cube.class)
public abstract class UnregisteredPartMutationMixin {
    @ModifyVariable(method = "compile", at = @At("HEAD"), argsOnly = true, ordinal = 2)
    private int audit$sourceColour(int colour) {
        com.radiance.audit.UnregisteredSourceProbe.changedCube();
        return (colour & 0xff00ffff) | 0x00400000;
    }
}
