package com.radiance.mixins.vulkan_render_integration;

import com.radiance.compatibility.vanilla.VanillaModelPose;
import net.minecraft.client.model.*;
import net.minecraft.client.model.geom.ModelPart;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.gen.Invoker;

public final class VanillaAuxiliaryModelMixins {
    private VanillaAuxiliaryModelMixins() {}

    @Mixin(PlayerModel.class)
    public abstract static class Player implements VanillaModelPose.PlayerExtras {
        @Shadow @Final private ModelPart cloak;
        @Shadow @Final private ModelPart ear;

        public ModelPart radiance$cloak() {
            return cloak;
        }

        public ModelPart radiance$ear() {
            return ear;
        }
    }

    @Mixin(ParrotModel.class)
    public abstract static class Shoulder implements VanillaModelPose.ShoulderPose {
        @Invoker("prepare")
        protected abstract void radiance$prepare(ParrotModel.State state);

        @Invoker("setupAnim")
        protected abstract void radiance$animate(
                ParrotModel.State state,
                int ticks,
                float position,
                float speed,
                float age,
                float yaw,
                float pitch);

        public void radiance$shoulder(
                float position, float speed, float yaw, float pitch, int ticks) {
            radiance$prepare(ParrotModel.State.ON_SHOULDER);
            radiance$animate(ParrotModel.State.ON_SHOULDER, ticks, position, speed, 0, yaw, pitch);
        }
    }
}
