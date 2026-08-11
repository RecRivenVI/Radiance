package com.radiance.mixins.compatibility.simulated;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.radiance.compatibility.simulated.SimulatedGroupRenderRecovery;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import foundry.veil.api.client.render.framebuffer.AdvancedFbo;
import java.util.Collection;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.ShaderInstance;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.joml.Quaternionf;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

@Pseudo
@Mixin(targets = "dev.simulated_team.simulated.util.SimpleSubLevelGroupRenderer", remap = false)
public abstract class SimulatedSimpleSubLevelGroupMixins {
    @WrapOperation(method = "renderGroup", at = @At(value = "INVOKE",
        target = "Ldev/simulated_team/simulated/mixin_interface/diagram/LightTextureExtension;simulated$makeDiagramLightTexture(F)V"))
    private static void radiance$stageLightmap(
        dev.simulated_team.simulated.mixin_interface.diagram.LightTextureExtension light,
        float multiplier, Operation<Void> original) {
        com.radiance.compatibility.simulated.DiagramLightmaps.generateCurrent(multiplier,
            () -> original.call(light, multiplier));
    }

    @WrapOperation(method = "renderGroup", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/renderer/MultiBufferSource$BufferSource;endBatch()V"))
    private static void radiance$flush(MultiBufferSource.BufferSource buffers, Operation<Void> original) {
        original.call(buffers);
        SimulatedGroupRenderRecovery.buffersReady();
    }

    @WrapMethod(method = "renderGroup")
    private static void radiance$restoreGroup(ClientLevel level, Collection<ClientSubLevel> levels,
        AdvancedFbo fbo, Matrix4f view, Matrix4f projection, Vector3d camera,
        Quaternionf orientation, float tick, boolean players, Operation<Void> original) {
        SimulatedGroupRenderRecovery.run(level, tick, () -> original.call(level, levels, fbo, view,
            projection, camera, orientation, tick, players));
    }

    @WrapOperation(method = "renderGroup", at = @At(value = "INVOKE",
        target = "Lorg/joml/Matrix4fStack;pushMatrix()Lorg/joml/Matrix4fStack;"))
    private static Matrix4fStack radiance$push(Matrix4fStack stack, Operation<Matrix4fStack> original) {
        Matrix4fStack result = original.call(stack);
        SimulatedGroupRenderRecovery.pushed();
        return result;
    }

    @WrapOperation(method = "renderGroup", at = @At(value = "INVOKE",
        target = "Lorg/joml/Matrix4fStack;popMatrix()Lorg/joml/Matrix4fStack;"))
    private static Matrix4fStack radiance$pop(Matrix4fStack stack, Operation<Matrix4fStack> original) {
        Matrix4fStack result = original.call(stack);
        SimulatedGroupRenderRecovery.popped();
        return result;
    }

    @WrapOperation(method = "renderGroup", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/renderer/RenderType;setupRenderState()V"))
    private static void radiance$setupLayer(RenderType layer, Operation<Void> original) {
        SimulatedGroupRenderRecovery.layer(layer);
        original.call(layer);
    }

    @WrapOperation(method = "renderGroup", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/renderer/RenderType;clearRenderState()V"))
    private static void radiance$clearLayer(RenderType layer, Operation<Void> original) {
        original.call(layer);
        SimulatedGroupRenderRecovery.layer(null);
    }

    @WrapOperation(method = "renderGroup", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/renderer/ShaderInstance;apply()V"))
    private static void radiance$apply(ShaderInstance shader, Operation<Void> original) {
        SimulatedGroupRenderRecovery.shader(shader);
        original.call(shader);
    }

    @WrapOperation(method = "renderGroup", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/renderer/ShaderInstance;clear()V"))
    private static void radiance$clear(ShaderInstance shader, Operation<Void> original) {
        original.call(shader);
        SimulatedGroupRenderRecovery.shader(null);
    }
}
