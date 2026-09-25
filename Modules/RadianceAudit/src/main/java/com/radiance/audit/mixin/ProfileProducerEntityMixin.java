package com.radiance.audit.mixin;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.blaze3d.vertex.PoseStack;
import com.radiance.audit.ProducerCensus;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
@Mixin(EntityRenderDispatcher.class)
public abstract class ProfileProducerEntityMixin {
    @WrapMethod(method="render")
    private <T extends Entity> void audit$render(T entity,double x,double y,double z,float yaw,float tick,
        PoseStack pose,MultiBufferSource buffers,int light,Operation<Void> original) {
        if(!ProducerCensus.collecting()) {original.call(entity,x,y,z,yaw,tick,pose,buffers,light);return;}
        try(var scope=ProducerCensus.renderer("entity",((EntityRenderDispatcher)(Object)this).getRenderer(entity))) {
            original.call(entity,x,y,z,yaw,tick,pose,buffers,light);
        }
    }
}
