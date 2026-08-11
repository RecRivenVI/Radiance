package com.radiance.audit.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/** The only woven observer in controller-only runs; no renderer/source/native hooks. */
@Mixin(Minecraft.class)
public abstract class MinimalCityBenchmarkMixin {
    @Unique private int radianceAudit$depth;

    @WrapMethod(method = "runTick")
    private void audit$minimalFrame(boolean render, Operation<Void> original) {
        boolean outer = radianceAudit$depth++ == 0;
        long start = outer ? System.nanoTime() : 0;
        try {
            original.call(render);
        } finally {
            --radianceAudit$depth;
            if (outer)
                com.radiance.audit.MinimalCityBenchmark.frame((Minecraft) (Object) this, start);
        }
    }
}
