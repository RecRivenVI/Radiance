package com.radiance.audit.mixin;

import com.mojang.blaze3d.vertex.MeshData;
import com.radiance.audit.AuditHooks;
import net.minecraft.client.renderer.RenderType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = RenderType.class, priority = 2000)
public abstract class RenderTypeAuditMixin {
    private static final String SOURCE = "RenderType.draw";

    @Inject(method = "draw(Lcom/mojang/blaze3d/vertex/MeshData;)V", at = @At("HEAD"))
    private void radianceAudit$begin(MeshData mesh, CallbackInfo ci) {
        MeshData.DrawState state = mesh.drawState();
        AuditHooks.enter("RENDER_TYPE_DRAW", SOURCE,
            ((RenderType) (Object) this).name + "; " + state.mode() + "/"
                + state.format() + "/indices=" + state.indexCount());
    }

    @Inject(method = "draw(Lcom/mojang/blaze3d/vertex/MeshData;)V", at = @At("RETURN"))
    private void radianceAudit$end(MeshData mesh, CallbackInfo ci) {
        AuditHooks.exit(SOURCE);
    }
}
