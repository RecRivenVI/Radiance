package com.radiance.mixins.compatibility.simulated;

import com.radiance.compatibility.simulated.SimulatedDiagramCompatibility;
import com.radiance.compatibility.veil.VeilAdapter;
import foundry.veil.api.client.render.shader.program.ShaderProgram;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Keeps diagram consumers on their actual Veil program and exposes failed program selection.
 */
@Pseudo
@Mixin(targets = "foundry.veil.api.client.render.VeilRenderSystem", remap = false)
public abstract class SimulatedVeilRenderSystemMixins {

    @Inject(method = "setShader(Lnet/minecraft/resources/ResourceLocation;)Lfoundry/veil/api/client/render/shader/program/ShaderProgram;",
        at = @At("RETURN"), remap = false)
    private static void radiance$requireDiagramProgram(ResourceLocation shader,
        CallbackInfoReturnable<ShaderProgram> cir) {
        if (!SimulatedDiagramCompatibility.isRenderingDiagram()) {
            return;
        }
        VeilAdapter.validateDiagramProgram(shader, cir.getReturnValue());
    }
}
