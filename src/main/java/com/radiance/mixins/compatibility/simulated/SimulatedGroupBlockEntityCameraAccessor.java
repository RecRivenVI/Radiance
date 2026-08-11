package com.radiance.mixins.compatibility.simulated;

import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(BlockEntityRenderDispatcher.class)
public interface SimulatedGroupBlockEntityCameraAccessor {
    @Accessor(value = "sable$cameraPos", remap = false)
    Vec3 radiance$getSublevelCamera();
}
