package com.radiance.audit.mixin;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.blaze3d.vertex.PoseStack;
import com.radiance.audit.ProducerCensus;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
@Mixin(BlockEntityRenderDispatcher.class)
public abstract class ProfileProducerBlockEntityMixin {
    @WrapMethod(method="render")
    private <T extends BlockEntity> void audit$render(T entity,float tick,PoseStack pose,MultiBufferSource buffers,Operation<Void> original) {
        if(!ProducerCensus.collecting()) {original.call(entity,tick,pose,buffers);return;}
        try(var scope=ProducerCensus.renderer("blockentity",((BlockEntityRenderDispatcher)(Object)this).getRenderer(entity))) {
            original.call(entity,tick,pose,buffers);
        }
    }
}
