package com.radiance.audit.mixin;

import com.mojang.blaze3d.vertex.VertexBuffer;
import com.radiance.audit.AuditHooks;
import net.minecraft.client.renderer.ShaderInstance;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = VertexBuffer.class, priority = 2000)
public abstract class VertexBufferAuditMixin {
    private static final String DRAW = "VertexBuffer.draw";
    private static final String DRAW_WITH_SHADER = "VertexBuffer.drawWithShader";

    @Inject(method = "draw()V", at = @At("HEAD"))
    private void radianceAudit$beginDraw(CallbackInfo ci) {
        AuditHooks.enter("PERSISTENT_BUFFER_DRAW", DRAW, "draw()");
    }

    @Inject(method = "draw()V", at = @At("RETURN"))
    private void radianceAudit$endDraw(CallbackInfo ci) {
        AuditHooks.exit(DRAW);
    }

    @Inject(method = "drawWithShader", at = @At("HEAD"))
    private void radianceAudit$beginDrawWithShader(Matrix4f modelView, Matrix4f projection,
        ShaderInstance shader, CallbackInfo ci) {
        AuditHooks.enter("PERSISTENT_BUFFER_DRAW", DRAW_WITH_SHADER,
            shader == null ? "shader=null" : "shader=" + shader.getName());
    }

    @Inject(method = "drawWithShader", at = @At("RETURN"))
    private void radianceAudit$endDrawWithShader(Matrix4f modelView, Matrix4f projection,
        ShaderInstance shader, CallbackInfo ci) {
        AuditHooks.exit(DRAW_WITH_SHADER);
    }
}
