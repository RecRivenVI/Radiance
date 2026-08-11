package com.radiance.adapter.vanilla;

import com.mojang.blaze3d.systems.RenderSystem;
import com.radiance.api.render.WorldView;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.RenderStateShard;
import org.joml.Matrix4f;

/**
 * Samples current-target camera/atmosphere/glint semantics; Native receives no Minecraft objects.
 */
public final class VanillaViewAdapter {
    private VanillaViewAdapter() {}

    public static WorldView describe(
            Camera camera,
            Matrix4f view,
            Matrix4f effected,
            Matrix4f projection,
            int overlay,
            ClientLevel level,
            int sky,
            int portal,
            int lightmap) {
        RenderStateShard.setupGlintTexturing(.16f);
        Matrix4f glint = new Matrix4f(RenderSystem.getTextureMatrix());
        RenderSystem.resetTextureMatrix();
        float end =
                com.radiance.client.option.Options.boundaryFog
                        ? com.radiance.api.render.SourceHooks.imagingDistance(
                                Math.max(
                                        32,
                                        net.minecraft.client.Minecraft.getInstance()
                                                        .gameRenderer
                                                        .getRenderDistance()
                                                - 16))
                        : 0;
        return new WorldView(
                view,
                effected,
                projection,
                glint,
                RenderSystem.getShaderGameTime(),
                overlay,
                !camera.isDetached(),
                end * .95f,
                end,
                VanillaEnvironment.atmosphere(level, camera.getPosition()),
                0,
                level.effects().skyType().ordinal(),
                RenderSystem.getShaderGlintAlpha(),
                sky,
                portal,
                lightmap);
    }
}
