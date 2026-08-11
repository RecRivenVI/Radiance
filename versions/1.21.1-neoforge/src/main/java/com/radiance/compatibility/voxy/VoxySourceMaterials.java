package com.radiance.compatibility.voxy;

import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;
import me.cortex.voxy.client.core.model.ModelFactory;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Retains only classification still present in the source proxy, never original PBR or animation.
 */
public final class VoxySourceMaterials {
    private static final Map<ModelFactory, Map<Integer, Integer>> FLUIDS = new WeakHashMap<>();

    private VoxySourceMaterials() {}

    public static synchronized void record(ModelFactory factory, int id, BlockState state) {
        int fluid =
                state.getFluidState().is(FluidTags.WATER)
                        ? 1
                        : state.getFluidState().is(FluidTags.LAVA) ? 2 : 0;
        // ModelFactory deliberately interns projections; retain its first canonical source.
        FLUIDS.computeIfAbsent(factory, key -> new HashMap<>()).putIfAbsent(id, fluid);
    }

    public static synchronized int fluid(ModelFactory factory, int id) {
        var values = FLUIDS.get(factory);
        return values == null ? 0 : values.getOrDefault(id, 0);
    }

    public static synchronized void release(ModelFactory factory) {
        FLUIDS.remove(factory);
    }
}
