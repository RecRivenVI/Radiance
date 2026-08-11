package com.radiance.mixins.vulkan_render_integration;

import com.radiance.adapter.vanilla.VanillaWorldBlockRequests;
import java.util.List;
import net.minecraft.client.resources.model.*;
import org.spongepowered.asm.mixin.*;

/** Exposes immutable JSON children without replacing seeded selection or loader callbacks. */
public final class VanillaCompositeBlockModelMixins {
    private VanillaCompositeBlockModelMixins() {}

    @Mixin(WeightedBakedModel.class)
    public abstract static class Weighted implements VanillaWorldBlockRequests.CompositeModel {
        @Shadow @Final
        private List<net.minecraft.util.random.WeightedEntry.Wrapper<BakedModel>> list;

        @Unique private List<BakedModel> radiance$children;

        public List<BakedModel> radiance$children() {
            if (radiance$children == null)
                radiance$children =
                        list.stream()
                                .map(net.minecraft.util.random.WeightedEntry.Wrapper::data)
                                .toList();
            return radiance$children;
        }
    }

    @Mixin(MultiPartBakedModel.class)
    public abstract static class Multipart implements VanillaWorldBlockRequests.CompositeModel {
        @Shadow @Final
        private List<
                        org.apache.commons.lang3.tuple.Pair<
                                java.util.function.Predicate<
                                        net.minecraft.world.level.block.state.BlockState>,
                                BakedModel>>
                selectors;

        @Unique private List<BakedModel> radiance$children;

        public List<BakedModel> radiance$children() {
            if (radiance$children == null)
                radiance$children =
                        selectors.stream()
                                .map(org.apache.commons.lang3.tuple.Pair::getRight)
                                .toList();
            return radiance$children;
        }
    }
}
