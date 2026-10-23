package com.radiance.compatibility.vanilla;

import com.radiance.render.contract.TakeoverSubmission.*;
import java.util.ArrayList;
import net.minecraft.client.renderer.entity.layers.*;

/** Ordered descriptors of actual vanilla layer capabilities; all callbacks still execute. */
public final class VanillaLayerAdapter {
    private VanillaLayerAdapter() {}

    public static LayerPlan describe(Iterable<?> layers, boolean armorActive, boolean dragon) {
        var result = new ArrayList<Layer>();
        int order = 0;
        if (dragon) result.add(new Layer(order++, LayerKind.EYES, Composition.EYE_OWNER));
        for (var layer : layers) {
            LayerKind kind = LayerKind.DECORATION;
            Composition composition = Composition.SOURCE_ORDER;
            if (layer instanceof EyesLayer<?, ?>) {
                kind = LayerKind.EYES;
                composition = Composition.EYE_OWNER;
            } else if (layer instanceof WardenEmissiveLayer<?, ?>
                    || layer instanceof BreezeEyesLayer
                    || layer instanceof CatCollarLayer
                    || layer instanceof WolfCollarLayer
                    || layer instanceof IronGolemCrackinessLayer
                    || layer instanceof HorseMarkingLayer
                    || (armorActive && layer instanceof HumanoidArmorLayer<?, ?, ?>)) {
                kind = LayerKind.SURFACE_COATING;
                composition = Composition.SURFACE_OWNER;
            } else if (layer instanceof EnergySwirlLayer<?, ?>) kind = LayerKind.ENERGY;
            result.add(new Layer(order++, kind, composition));
        }
        return new LayerPlan(result);
    }
}
