package com.radiance.audit.mixin;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.radiance.audit.ProducerCensus;
import net.minecraft.client.model.geom.ModelPart;
import org.spongepowered.asm.mixin.Mixin;
@Mixin(ModelPart.class)
public abstract class ProfileProducerModelMixin {
    @org.spongepowered.asm.mixin.Shadow @org.spongepowered.asm.mixin.Final private java.util.List<ModelPart.Cube> cubes;
    @WrapMethod(method="compile")
    private void audit$compile(PoseStack.Pose pose,VertexConsumer consumer,int light,int overlay,int color,Operation<Void> original) {
        try(var scope=ProducerCensus.model(this,consumer)) {
            com.radiance.audit.PartModelProbe.compile(cubes,pose,consumer,light,overlay,color,
                () -> original.call(pose,consumer,light,overlay,color));
        }
    }
}
