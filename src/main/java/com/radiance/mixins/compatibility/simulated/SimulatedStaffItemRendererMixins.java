package com.radiance.mixins.compatibility.simulated;

import com.radiance.compatibility.simulated.SimulatedHandAnchor;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "dev.simulated_team.simulated.content.physics_staff.PhysicsStaffItemRenderer", remap = false)
public abstract class SimulatedStaffItemRendererMixins {
    @Shadow @Final private static Vector3d focusPos;

    @Inject(method = "getFirstPersonFocusPos", at = @At("HEAD"), cancellable = true, remap = false)
    private static void radiance$cameraAnchor(float tick, CallbackInfoReturnable<Vec3> cir) {
        Vec3 offset = SimulatedHandAnchor.offset(focusPos);
        if (offset != null) cir.setReturnValue(offset);
    }
}
