package com.radiance.compatibility.outliner;

import com.mojang.blaze3d.vertex.PoseStack;
import com.radiance.client.proxy.vulkan.FramebufferProxy;
import com.radiance.client.render.WorldMeshSink;
import com.radiance.mixins.compatibility.catnip.OutlineParamsAccessor;
import java.util.Map;
import net.createmod.catnip.outliner.AABBOutline;
import net.createmod.catnip.outliner.BlockClusterOutline;
import net.createmod.catnip.outliner.LineOutline;
import net.createmod.catnip.outliner.Outline;
import net.createmod.catnip.outliner.Outliner;
import net.createmod.catnip.render.SuperRenderTypeBuffer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * Radiance-owned Outliner renderer: iterates the Catnip outline list itself and emits
 * path-traced line/face geometry through {@link RadianceOutlinerContext}. Unsupported outline
 * types (for example item previews) keep using the original buffer path.
 */
public final class RadianceOutlinerRenderer {
    private RadianceOutlinerRenderer() {
    }

    public static boolean isActive() {
        if (FramebufferProxy.boundFramebuffer(FramebufferProxy.DRAW_FRAMEBUFFER) != 0) {
            return false;
        }
        WorldMeshSink.StageContext stage = WorldMeshSink.currentStage();
        return stage != null && stage.targetKind() == WorldMeshSink.TargetKind.DEFAULT_WORLD;
    }

    public static void render(PoseStack eventPose, SuperRenderTypeBuffer eventBuffer, Vec3 camera,
        float partialTick) {
        PoseStack overlayPose = new PoseStack();
        Outliner outliner = Outliner.getInstance();
        for (Map.Entry<Object, Outliner.OutlineEntry> mapEntry : outliner.getOutlines().entrySet()) {
            Outliner.OutlineEntry entry = mapEntry.getValue();
            Outline outline = entry.getOutline();
            Outline.OutlineParams params = outline.getParams();
            OutlineParamsAccessor accessor = (OutlineParamsAccessor) (Object) params;

            accessor.radiance$setAlpha(1.0F);
            if (entry.isFading()) {
                int previousTicks = entry.getTicksTillRemoval() + 1;
                float fadeTicks = Outliner.OutlineEntry.FADE_TICKS;
                float lastAlpha = previousTicks >= 0 ? 1.0F : 1.0F + (previousTicks / fadeTicks);
                float currentAlpha = 1.0F + (entry.getTicksTillRemoval() / fadeTicks);
                float alpha = Mth.lerp(partialTick, lastAlpha, currentAlpha);
                alpha = alpha * alpha * alpha;
                if (alpha < 1.0F / 8.0F) {
                    continue;
                }
                accessor.radiance$setAlpha(alpha);
            }

            if (!isSupported(outline)) {
                outline.render(eventPose, eventBuffer, camera, partialTick);
                continue;
            }

            try (RadianceOutlinerContext context =
                new RadianceOutlinerContext(outline, mapEntry.getKey())) {
                outline.render(overlayPose, context, camera, partialTick);
                context.submit();
            }
        }
    }

    private static boolean isSupported(Outline outline) {
        return outline instanceof AABBOutline || outline instanceof LineOutline
            || outline instanceof BlockClusterOutline;
    }
}
