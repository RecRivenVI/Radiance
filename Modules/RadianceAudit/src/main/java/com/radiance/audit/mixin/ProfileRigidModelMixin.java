package com.radiance.audit.mixin;
import com.radiance.client.vertex.RigidModelCapture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(value=RigidModelCapture.Draw.class,remap=false)
public abstract class ProfileRigidModelMixin {
    @Inject(method="submit",at=@At("HEAD"))
    private void audit$verify(CallbackInfo ci) { com.radiance.audit.RigidModelProbe.check(this); }
    @com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation(method="submit",
        at=@At(value="INVOKE",target="Lcom/radiance/client/render/MaterialFaces;capture(Lnet/minecraft/client/renderer/RenderType;)I"))
    private int audit$faces(net.minecraft.client.renderer.RenderType layer,
        com.llamalad7.mixinextras.injector.wrapoperation.Operation<Integer> original) {
        try(var attribution=com.radiance.audit.ProducerCensus.rigidFaces(this);
            var census=attribution==null?null:com.radiance.audit.ProducerCensus.face(layer);
            var ignored=com.radiance.audit.FrameProfiler.span(com.radiance.audit.FrameTimings.Stage.GEOMETRY_FACE_STATE)) {
            return original.call(layer);
        }
    }
}
