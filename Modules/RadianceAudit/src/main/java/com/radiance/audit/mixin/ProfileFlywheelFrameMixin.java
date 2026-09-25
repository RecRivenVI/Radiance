package com.radiance.audit.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.radiance.audit.FrameProfiler;
import com.radiance.audit.FrameTimings.Stage;
import com.radiance.compatibility.flywheel.FlywheelRenderBridge;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import java.util.SortedSet;
import net.minecraft.server.level.BlockDestructionProgress;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(value = FlywheelRenderBridge.Frame.class, remap = false)
public abstract class ProfileFlywheelFrameMixin {
    @WrapMethod(method = "afterEntities")
    private void audit$entities(Operation<Void> original) {
        try (var ignored = FrameProfiler.span(Stage.FLYWHEEL_DRAW)) { original.call(); }
    }
    @WrapMethod(method = "beforeCrumbling")
    private void audit$crumbling(Long2ObjectMap<SortedSet<BlockDestructionProgress>> progress,
        Operation<Void> original) {
        try (var ignored = FrameProfiler.span(Stage.FLYWHEEL_DRAW)) { original.call(progress); }
    }
}
