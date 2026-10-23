package com.radiance.mixins.vulkan_render_integration;

import com.radiance.compatibility.vanilla.VanillaLivingReplacement.*;
import net.minecraft.client.model.*;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.*;
import org.spongepowered.asm.mixin.*;

/** Original model selection/colour preludes, without invoking their renderer bodies. */
public final class VanillaModelSelectionMixins {
    private VanillaModelSelectionMixins() {}

    @Mixin(PufferfishRenderer.class)
    public abstract static class Puffer implements ModelSelection {
        @Shadow @Final private EntityModel<Pufferfish> small;
        @Shadow @Final private EntityModel<Pufferfish> mid;
        @Shadow @Final private EntityModel<Pufferfish> big;
        @Shadow private int puffStateO;

        public EntityModel<?> radiance$selectedModel(LivingEntity entity) {
            return switch (((Pufferfish) entity).getPuffState()) {
                case 0 -> small;
                case 1 -> mid;
                default -> big;
            };
        }

        public void radiance$prepareSelected(LivingEntity entity) {
            int state = ((Pufferfish) entity).getPuffState();
            if (state != puffStateO)
                ((StateAccess) this).radiance$selectModel(radiance$selectedModel(entity));
            puffStateO = state;
            ((NameAccess) this).radiance$shadowRadius(.1F + .1F * state);
        }

        public void radiance$finishSelected() {}
    }

    @Mixin(TropicalFishRenderer.class)
    public abstract static class Tropical implements ModelSelection {
        @Shadow @Final private ColorableHierarchicalModel<TropicalFish> modelA;
        @Shadow @Final private ColorableHierarchicalModel<TropicalFish> modelB;

        public EntityModel<?> radiance$selectedModel(LivingEntity entity) {
            return ((TropicalFish) entity).getVariant().base() == TropicalFish.Base.SMALL
                    ? modelA
                    : modelB;
        }

        public void radiance$prepareSelected(LivingEntity entity) {
            var model = (ColorableHierarchicalModel<?>) radiance$selectedModel(entity);
            ((StateAccess) this).radiance$selectModel(model);
            model.setColor(((TropicalFish) entity).getBaseColor().getTextureDiffuseColor());
        }

        public void radiance$finishSelected() {
            ((ColorableHierarchicalModel<?>)
                            ((LivingEntityRenderer<?, ?>) (Object) this).getModel())
                    .setColor(-1);
        }
    }
}
