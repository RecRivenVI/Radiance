package com.radiance.audit.mixin;

import com.radiance.audit.AuditHooks;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SectionRenderDispatcher.RenderSection.class)
public abstract class RenderSectionAuditMixin {
    @Inject(method = "setDirty(Z)V", at = @At("TAIL"))
    private void radianceAudit$playerDirty(boolean playerChanged, CallbackInfo ci) {
        if (!playerChanged) return;
        SectionRenderDispatcher.RenderSection section =
            (SectionRenderDispatcher.RenderSection) (Object) this;
        BlockPos origin = section.getOrigin();
        AuditHooks.event("CHUNK_PIPELINE", "RenderSection.setDirty",
            "nativeId=" + section.index + "; origin=" + origin.getX() + ',' + origin.getY() + ','
                + origin.getZ() + "; compiled=" + section.getCompiled().getClass().getName(),
            "ISSUED", "JAVA_SCHEDULER");
    }
}
