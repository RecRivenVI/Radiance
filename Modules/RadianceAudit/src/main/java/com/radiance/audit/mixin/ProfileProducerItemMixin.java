package com.radiance.audit.mixin;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.radiance.audit.ProducerCensus;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
@Mixin(ItemRenderer.class)
public abstract class ProfileProducerItemMixin {
    @WrapMethod(method="renderModelLists")
    private void audit$model(BakedModel model,ItemStack item,int light,int overlay,PoseStack pose,VertexConsumer consumer,Operation<Void> original) {
        try(var scope=ProducerCensus.baked("item-model",model,consumer)) {original.call(model,item,light,overlay,pose,consumer);}
    }
}
