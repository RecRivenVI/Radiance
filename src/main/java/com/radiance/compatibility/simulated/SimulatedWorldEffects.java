package com.radiance.compatibility.simulated;

import com.radiance.client.render.WorldRasterPass;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.GameRenderer;
import net.neoforged.fml.ModList;
import org.joml.Matrix4f;

/** Restores Simulated's call site in the replaced LevelRenderer body. */
public final class SimulatedWorldEffects {
    private SimulatedWorldEffects() {}

    public static void queueEndSea(Camera camera, GameRenderer renderer,
        Matrix4f view, Matrix4f projection) {
        if (ModList.get().isLoaded("simulated")) {
            WorldRasterPass.defer(100, "simulated:end_sea", view, projection,
                () -> EndSea.render(camera, renderer));
        }
    }

    private static final class EndSea {
        private static void render(Camera camera, GameRenderer renderer) {
            dev.simulated_team.simulated.content.end_sea.EndSeaRenderer.render(camera, renderer);
        }
    }
}
