package com.radiance.compatibility.voxy;

import com.mojang.blaze3d.systems.RenderSystem;
import me.cortex.voxy.client.config.VoxyConfig;
import net.minecraft.client.Camera;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.material.FogType;

/** Called only at the main world's fog boundary, never from GUI/diagram renderers. */
public final class VoxyWorldFog {
    private VoxyWorldFog() {}

    public static void adjust(Camera camera, float distance, boolean thickFog) {
        if (!VoxyPtBridge.active()) return;
        boolean effect =
                camera.getEntity() instanceof LivingEntity living
                        && (living.hasEffect(MobEffects.BLINDNESS)
                                || living.hasEffect(MobEffects.DARKNESS));
        if (thickFog || camera.getFluidInCamera() != FogType.NONE || effect) return;
        // The GL compositor is absent: use the PT world's fog with the source's far/fade range,
        // instead of letting vanilla's near render distance hide every far proxy.
        if (VoxyConfig.CONFIG.getFogMode().hasFade) {
            float end = Math.max(distance, VoxyConfig.CONFIG.sectionRenderDistance * 512 - 32);
            RenderSystem.setShaderFogStart(Math.max(distance, end * .9f));
            RenderSystem.setShaderFogEnd(end);
        } else {
            RenderSystem.setShaderFogStart(99999999);
            RenderSystem.setShaderFogEnd(99999999);
        }
    }
}
