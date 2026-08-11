package com.radiance.mixins.compatibility.create;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.radiance.client.render.LineSources;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;

@Pseudo
@Mixin(targets = "com.simibubi.create.foundation.events.ClientEvents", remap = false)
public abstract class CreateEventLineSourceMixins {
    @WrapMethod(method = "onRenderWorld", remap = false)
    private static void radiance$registerWorldLines(
            RenderLevelStageEvent event, Operation<Void> original) {
        try (var source = LineSources.enter(LineSources.CREATE_EVENT)) {
            original.call(event);
        }
    }
}
