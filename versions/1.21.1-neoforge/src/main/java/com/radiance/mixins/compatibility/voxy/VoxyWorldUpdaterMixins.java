package com.radiance.mixins.compatibility.voxy;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.radiance.compatibility.voxy.VoxyCaptureLock;
import me.cortex.voxy.common.voxelization.VoxelizedSection;
import me.cortex.voxy.common.world.WorldEngine;
import me.cortex.voxy.common.world.WorldUpdater;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(value = WorldUpdater.class, remap = false)
public abstract class VoxyWorldUpdaterMixins {
    @WrapMethod(method = "insertUpdate")
    private static void radiance$atomicUpdate(
            WorldEngine world, VoxelizedSection section, Operation<Void> original) {
        VoxyCaptureLock.LOCK.writeLock().lock();
        try {
            original.call(world, section);
        } finally {
            VoxyCaptureLock.changed();
            VoxyCaptureLock.LOCK.writeLock().unlock();
        }
    }
}
