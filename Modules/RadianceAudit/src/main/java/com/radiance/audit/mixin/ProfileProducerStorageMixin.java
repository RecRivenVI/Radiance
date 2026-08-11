package com.radiance.audit.mixin;
import com.radiance.audit.ProducerCensus;
import com.radiance.client.vertex.StorageVertexConsumerProvider;
import net.minecraft.client.renderer.RenderType;
import com.mojang.blaze3d.vertex.VertexConsumer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(value=StorageVertexConsumerProvider.class,remap=false)
public abstract class ProfileProducerStorageMixin {
    @Inject(method="getBuffer",at=@At("HEAD"))
    private void audit$owner(RenderType type,CallbackInfoReturnable<VertexConsumer> callback) {ProducerCensus.provider(this);}
    @Inject(method="takeRigidModels",at=@At("RETURN"))
    private void audit$rigid(CallbackInfoReturnable<java.util.List<com.radiance.client.vertex.RigidModelCapture.Draw>> callback) {
        ProducerCensus.rigidProduced(this,callback.getReturnValue());
    }
}
