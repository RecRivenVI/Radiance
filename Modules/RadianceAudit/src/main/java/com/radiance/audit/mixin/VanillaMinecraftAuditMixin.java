package com.radiance.audit.mixin;

import com.radiance.audit.ExperimentAccess;
import com.radiance.audit.SmokeProbe;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Contains no Radiance class references, including handler signatures inspected by Mixin. */
@Mixin(Minecraft.class)
public abstract class VanillaMinecraftAuditMixin {
    @Inject(method = "runTick(Z)V", at = @At("RETURN"))
    private void auditVanillaFrame(boolean renderLevel, CallbackInfo ci) {
        if (ExperimentAccess.permitted()) {
            SmokeProbe.poll((Minecraft)(Object)this);
            com.radiance.audit.DiagramParityProbe.poll((Minecraft)(Object)this);
        }
    }
}
