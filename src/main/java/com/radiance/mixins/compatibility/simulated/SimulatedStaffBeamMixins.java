package com.radiance.mixins.compatibility.simulated;

import com.radiance.compatibility.simulated.SimulatedStaffCapture;
import dev.simulated_team.simulated.content.physics_staff.PhysicsStaffItemRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import net.createmod.catnip.render.SuperRenderTypeBuffer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Pseudo
@Mixin(targets = "dev.simulated_team.simulated.content.physics_staff.PhysicsStaffClientHandler", remap = false)
public abstract class SimulatedStaffBeamMixins {
    @Inject(method = "getStaffFocusPos", at = @At("HEAD"), cancellable = true, remap = false)
    private static void radiance$anchorAtCamera(Player player, boolean mainHand, float tick,
        CallbackInfoReturnable<Vec3> cir) {
        var camera = Minecraft.getInstance().gameRenderer.getMainCamera();
        if (player.isLocalPlayer() && !camera.isDetached())
            cir.setReturnValue(PhysicsStaffItemRenderer.getFirstPersonFocusPos(tick).add(camera.getPosition()));
    }

    @ModifyArg(method = "lambda$onRender$3", at = @At(value = "INVOKE",
        target = "Ldev/simulated_team/simulated/content/physics_staff/PhysicsStaffClientHandler$PhysicsBeam;render(Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/Vec3;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/createmod/catnip/render/SuperRenderTypeBuffer;Lnet/minecraft/world/phys/Vec3;F)V"),
        index = 3, remap = false)
    private static SuperRenderTypeBuffer radiance$captureBeam(SuperRenderTypeBuffer original) {
        return SimulatedStaffCapture.beamBuffer(original);
    }
}
