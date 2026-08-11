package com.radiance.mixins.compatibility.create;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.radiance.client.render.LineSources;
import net.neoforged.neoforge.client.event.RenderHighlightEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;

@Pseudo
@Mixin(targets = "com.simibubi.create.content.trains.track.TrackBlockOutline", remap = false)
public abstract class CreateHighlightLineSourceMixins {
    @WrapMethod(method = "drawCustomBlockSelection", remap = false)
    private static void radiance$registerHighlight(
            RenderHighlightEvent.Block event, Operation<Void> original) {
        try (var source = LineSources.enter(LineSources.CREATE_HIGHLIGHT)) {
            original.call(event);
        }
    }
}
