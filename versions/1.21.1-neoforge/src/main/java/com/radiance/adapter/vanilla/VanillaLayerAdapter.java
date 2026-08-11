package com.radiance.adapter.vanilla;

import com.radiance.render.contract.TakeoverSubmission.*;
import java.util.ArrayList;
import net.minecraft.client.renderer.entity.layers.*;

/** Ordered descriptors of actual vanilla layer capabilities; all callbacks still execute. */
public final class VanillaLayerAdapter {
    private VanillaLayerAdapter() {}

    public record Description(LayerPlan layers, boolean compatible) {}

    private static final Object SOURCE_LAYERS = new Object();

    private static final class SourcePlan {
        java.util.List<Object> inputs = java.util.List.of();
        boolean armor, dragon;
        Description value;

        boolean matches(Iterable<?> actual, boolean armor, boolean dragon) {
            if (value == null || this.armor != armor || this.dragon != dragon) return false;
            int i = 0;
            for (var input : actual)
                if (i >= inputs.size() || inputs.get(i++) != input) return false;
            return i == inputs.size();
        }
    }

    public static Description describeSource(
            Object owner, Iterable<?> layers, boolean armor, boolean dragon) {
        if (!(owner instanceof com.radiance.client.vertex.SourceBindings.Owner source))
            return new Description(describe(layers, armor, dragon), compatible(layers));
        var memo = source.radiance$bindings().definition(SOURCE_LAYERS, SourcePlan::new);
        if (!memo.matches(layers, armor, dragon)) {
            var inputs = new ArrayList<Object>();
            layers.forEach(inputs::add);
            memo.inputs = java.util.List.copyOf(inputs);
            memo.armor = armor;
            memo.dragon = dragon;
            memo.value =
                    new Description(describe(memo.inputs, armor, dragon), compatible(memo.inputs));
            com.radiance.api.audit.RenderAuditBridge.counter(
                    "SOURCE", "LAYER_PLAN_PREPARED", 1, "");
        } else
            com.radiance.api.audit.RenderAuditBridge.counter("SOURCE", "LAYER_PLAN_REUSED", 1, "");
        return memo.value;
    }

    private static boolean compatible(Iterable<?> layers) {
        for (var layer : layers)
            if (!com.radiance.mixin_related.SourceCompatibility.producer(layer.getClass()))
                return false;
        return true;
    }

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
