package com.radiance.mixins.compatibility.sable;

import com.radiance.compatibility.flywheel.FlywheelEmbeddingLightingAccess;
import dev.ryanhcode.sable.neoforge.mixinterface.compatibility.flywheel.EmbeddedEnvironmentExtension;
import org.joml.Matrix4fc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;

/** Gives Sable's original block-entity/contraption consumers access to the Vulkan embedding. */
@Pseudo
@Mixin(targets = "com.radiance.compatibility.flywheel.RadianceFlywheelEngine$Embedding", remap = false)
public abstract class SableFlywheelEmbeddingMixins implements EmbeddedEnvironmentExtension {
    @Override
    public void sable$setLightingInfo(Matrix4fc sceneMatrix, int scene, float skyLightScale) {
        ((FlywheelEmbeddingLightingAccess) this).radiance$setEmbeddingLighting(
            sceneMatrix, scene, skyLightScale);
    }
}
