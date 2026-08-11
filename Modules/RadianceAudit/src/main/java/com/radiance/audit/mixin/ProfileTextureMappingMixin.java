package com.radiance.audit.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.radiance.audit.FrameProfiler;
import com.radiance.audit.FrameTimings.Stage;
import com.radiance.client.proxy.vulkan.BufferProxy;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(value = BufferProxy.class, remap = false)
public abstract class ProfileTextureMappingMixin {
    @WrapMethod(method = "updateWorldUniform(Lnet/minecraft/client/Camera;Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;ILnet/minecraft/client/multiplayer/ClientLevel;III)V")
    private static void audit$world(Camera camera, Matrix4f view, Matrix4f effectedView, Matrix4f projection,
        int overlay, ClientLevel level, int sky, int portal, int light, Operation<Void> original) {
        try (var ignored = FrameProfiler.span(Stage.WORLD_UNIFORM)) {
            original.call(camera, view, effectedView, projection, overlay, level, sky, portal, light);
        }
    }
    @WrapMethod(method = "updateMapping()V")
    private static void audit$mapping(Operation<Void> original) {
        try (var ignored = FrameProfiler.span(Stage.TEXTURE_MAPPING)) { original.call(); }
    }
}
