package com.radiance.mixins.compatibility.sable;

import com.radiance.compatibility.veil.VeilAdapter;
import dev.ryanhcode.sable.sublevel.render.dispatcher.FancySubLevelRenderDispatcher;
import foundry.veil.api.client.render.shader.program.ShaderProgram;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.renderer.ShaderInstance;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(value = FancySubLevelRenderDispatcher.class, remap = false)
public abstract class SableFancySubLevelRenderDispatcherMixins {

    @Shadow @Final
    private Map<String, CompletableFuture<ShaderProgram>> dynamicPrograms;

    @Inject(method = "getDynamicProgram(Lnet/minecraft/client/renderer/ShaderInstance;)Lfoundry/veil/api/client/render/shader/program/ShaderProgram;",
        at = @At("HEAD"), cancellable = true, remap = false)
    private void radiance$getDynamicProgram(ShaderInstance shader,
        CallbackInfoReturnable<ShaderProgram> cir) {
        cir.setReturnValue(VeilAdapter.getSableDynamicProgram(this.dynamicPrograms, shader));
    }
}
