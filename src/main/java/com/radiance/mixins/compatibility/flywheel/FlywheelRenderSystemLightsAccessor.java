package com.radiance.mixins.compatibility.flywheel;

import com.mojang.blaze3d.systems.RenderSystem;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(RenderSystem.class)
public interface FlywheelRenderSystemLightsAccessor {
    @Accessor("shaderLightDirections")
    static Vector3f[] radiance$getShaderLightDirections() {
        throw new AssertionError();
    }
}
