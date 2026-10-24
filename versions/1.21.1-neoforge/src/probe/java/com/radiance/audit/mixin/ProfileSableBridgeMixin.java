package com.radiance.audit.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.radiance.audit.FrameProfiler;
import com.radiance.audit.FrameTimings.Stage;
import com.radiance.compatibility.sable.SableSubLevelBridge;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(value = SableSubLevelBridge.class, remap = false)
public abstract class ProfileSableBridgeMixin {
    @WrapMethod(method = "entityRenderTransform")
    private static SableSubLevelBridge.EntityRenderTransform audit$entity(
            net.minecraft.world.entity.Entity entity,
            float partial,
            double x,
            double y,
            double z,
            Operation<SableSubLevelBridge.EntityRenderTransform> original) {
        try (var stage = FrameProfiler.span(Stage.ENTITY_SOURCE_LOOKUP)) {
            return original.call(entity, partial, x, y, z);
        }
    }

    @com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation(
            method = {"entitySubLevel", "blockSubLevel", "getSubLevels"},
            at =
                    @org.spongepowered.asm.mixin.injection.At(
                            value = "INVOKE",
                            target =
                                    "Ljava/lang/Class;forName(Ljava/lang/String;)Ljava/lang/Class;"))
    private static Class<?> audit$classLookup(String name, Operation<Class<?>> original)
            throws ClassNotFoundException {
        FrameProfiler.optionalSourceLookup();
        return original.call(name);
    }

    @WrapMethod(method = "update")
    private static void audit$update(
            ClientLevel level, Camera camera, float partialTick, Operation<Void> original) {
        try (var ignored = FrameProfiler.span(Stage.SABLE_UPDATE)) {
            original.call(level, camera, partialTick);
        }
    }

    @WrapMethod(method = "queueSingleBlocks")
    private static void audit$single(
            ClientLevel level, float partialTick, Operation<Void> original) {
        try (var ignored = FrameProfiler.span(Stage.SABLE_SINGLE_BLOCKS)) {
            original.call(level, partialTick);
        }
    }

    @WrapMethod(method = "queueBlockEntities")
    private static void audit$blockEntities(
            ClientLevel level,
            Camera camera,
            BlockEntityRenderDispatcher dispatcher,
            float partialTick,
            Operation<Void> original) {
        try (var ignored = FrameProfiler.span(Stage.SABLE_BLOCK_ENTITIES)) {
            original.call(level, camera, dispatcher, partialTick);
        }
    }
}
