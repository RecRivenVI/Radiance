package com.radiance.mixins.vulkan_render_integration;

import com.mojang.blaze3d.vertex.PoseStack;
import com.radiance.client.vertex.BoundPartCapture;
import com.radiance.compatibility.vanilla.*;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.world.entity.*;
import org.joml.Quaternionf;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(DisplayRenderer.class)
public abstract class VanillaDisplayDefinitionMixins implements VanillaEntityReplacement.Source {
    @Shadow
    protected abstract Object getSubState(Display entity);

    @Invoker("calculateOrientation")
    protected abstract Quaternionf radiance$orientation(
            Display.RenderState state, Display entity, float partial, Quaternionf result);

    public boolean radiance$supportsEntity(Entity entity) {
        return VanillaDisplayRequests.supports(getSubState((Display) entity));
    }

    public boolean radiance$collectEntity(
            Entity value,
            float yaw,
            float partial,
            PoseStack pose,
            MultiBufferSource buffers,
            BoundPartCapture requests,
            int light) {
        var display = (Display) value;
        var state = display.renderState();
        if (state == null) return false;
        var sub = getSubState(display);
        if (sub == null) return false;
        float progress = display.calculateInterpolationProgress(partial);
        var names = (VanillaLivingReplacement.NameAccess) this;
        names.radiance$shadowRadius(state.shadowRadius().get(progress));
        names.radiance$shadowStrength(state.shadowStrength().get(progress));
        int brightness = state.brightnessOverride() != -1 ? state.brightnessOverride() : light;
        VanillaEntityReplacement.name(
                (EntityRenderer<?>) (Object) this, display, partial, pose, buffers, brightness);
        pose.pushPose();
        pose.mulPose(radiance$orientation(state, display, partial, new Quaternionf()));
        pose.mulPose(state.transformation().get(progress).getMatrix());
        VanillaDisplayRequests.collect(display, sub, progress, pose, buffers, brightness);
        pose.popPose();
        return false;
    }
}
