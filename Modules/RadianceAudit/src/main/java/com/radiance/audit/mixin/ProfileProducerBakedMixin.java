package com.radiance.audit.mixin;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.radiance.audit.ProducerCensus;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.data.ModelData;
import org.spongepowered.asm.mixin.Mixin;
@Mixin(ModelBlockRenderer.class)
public abstract class ProfileProducerBakedMixin {
    @WrapMethod(method="renderModel(Lcom/mojang/blaze3d/vertex/PoseStack$Pose;Lcom/mojang/blaze3d/vertex/VertexConsumer;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/client/resources/model/BakedModel;FFFIILnet/neoforged/neoforge/client/model/data/ModelData;Lnet/minecraft/client/renderer/RenderType;)V")
    private void audit$model(PoseStack.Pose pose,VertexConsumer consumer,BlockState state,BakedModel model,
        float r,float g,float b,int light,int overlay,ModelData data,RenderType layer,Operation<Void> original) {
        try(var scope=ProducerCensus.baked("block-model",model,consumer)) {original.call(pose,consumer,state,model,r,g,b,light,overlay,data,layer);}
    }
}
