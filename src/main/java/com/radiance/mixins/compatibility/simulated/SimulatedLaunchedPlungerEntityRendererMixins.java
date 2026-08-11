package com.radiance.mixins.compatibility.simulated;

import com.radiance.compatibility.simulated.SimulatedHandAnchor;
import dev.simulated_team.simulated.content.items.plunger_launcher.PlungerLauncherItemRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "dev.simulated_team.simulated.content.entities.launched_plunger.LaunchedPlungerEntityRenderer", remap = false)
public abstract class SimulatedLaunchedPlungerEntityRendererMixins {
    @Inject(method = "getFirstPersonFocusPos", at = @At("HEAD"), cancellable = true, remap = false)
    private static void radiance$cameraAnchor(float tick, CallbackInfoReturnable<Vec3> cir) {
        Vec3 offset = SimulatedHandAnchor.offset(PlungerLauncherItemRenderer.focusPos);
        if (offset != null) cir.setReturnValue(offset.add(Minecraft.getInstance().gameRenderer.getMainCamera().getPosition()));
    }
}
