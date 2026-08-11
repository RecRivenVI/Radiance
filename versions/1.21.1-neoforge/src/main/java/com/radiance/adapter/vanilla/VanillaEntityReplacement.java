package com.radiance.adapter.vanilla;

import com.mojang.blaze3d.vertex.PoseStack;
import com.radiance.client.vertex.*;
import com.radiance.mixin_related.SourceCompatibility;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.world.entity.*;
import net.neoforged.neoforge.client.event.RenderNameTagEvent;
import net.neoforged.neoforge.common.NeoForge;

/** World source parameters, shared model requests and the original base name-tag contract. */
public final class VanillaEntityReplacement {
    private VanillaEntityReplacement() {}

    public interface Source {
        default boolean radiance$supportsEntity(Entity entity) {
            return true;
        }

        default boolean radiance$nameFirst() {
            return false;
        }

        boolean radiance$collectEntity(
                Entity entity,
                float yaw,
                float partial,
                PoseStack pose,
                MultiBufferSource buffers,
                BoundPartCapture requests,
                int light);
    }

    public static boolean eligible(
            EntityRenderDispatcher dispatcher,
            Entity entity,
            MultiBufferSource buffers,
            StorageVertexConsumerProvider owner) {
        var renderer = dispatcher.getRenderer(entity);
        return PartModelCapture.ENABLED
                && (buffers == owner || buffers instanceof StorageOutlineVertexConsumerProvider)
                && !com.radiance.client.render.RasterPreviewScope.active()
                && renderer instanceof Source source
                && source.radiance$supportsEntity(entity)
                && SourceCompatibility.producer(renderer.getClass())
                && PartModelCapture.sourceCompatible()
                && SourceCompatibility.compatible(SourceCompatibility.Source.STATIC_STATE);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    public static void render(
            EntityRenderDispatcher dispatcher,
            Entity entity,
            float partial,
            PoseStack pose,
            StorageVertexConsumerProvider owner,
            MultiBufferSource buffers,
            int light) {
        render(
                dispatcher,
                entity,
                net.minecraft.util.Mth.lerp(partial, entity.yRotO, entity.getYRot()),
                partial,
                pose,
                owner,
                buffers,
                light);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    public static void render(
            EntityRenderDispatcher dispatcher,
            Entity entity,
            float yaw,
            float partial,
            PoseStack pose,
            StorageVertexConsumerProvider owner,
            MultiBufferSource buffers,
            int light) {
        EntityRenderer renderer = dispatcher.getRenderer(entity);
        var source = (Source) renderer;
        var offset = renderer.getRenderOffset(entity, partial);
        pose.pushPose();
        pose.translate(offset.x, offset.y, offset.z);
        try (var evaluation =
                VanillaModelPose.source(owner.sourceOwner != null ? owner.sourceOwner : entity)) {
            var requests = new BoundPartCapture(owner);
            if (source.radiance$nameFirst()) name(renderer, entity, partial, pose, buffers, light);
            boolean name =
                    source.radiance$collectEntity(
                            entity, yaw, partial, pose, buffers, requests, light);
            requests.submit();
            if (name && !source.radiance$nameFirst())
                name(renderer, entity, partial, pose, buffers, light);
        } finally {
            pose.popPose();
        }
    }

    public static void name(
            EntityRenderer<?> renderer,
            Entity entity,
            float partial,
            PoseStack pose,
            MultiBufferSource buffers,
            int light) {
        VanillaEntityAttachments.leash(renderer, entity, partial, pose, buffers);
        var event =
                new RenderNameTagEvent(
                        entity, entity.getDisplayName(), renderer, pose, buffers, light, partial);
        NeoForge.EVENT_BUS.post(event);
        var names = (VanillaLivingReplacement.NameAccess) renderer;
        if (event.canRender().isTrue()
                || event.canRender().isDefault() && names.radiance$showName(entity))
            VanillaNameRequests.collect(
                    renderer,
                    entity,
                    event.getContent(),
                    pose,
                    com.radiance.client.proxy.world.EntityProxy.postTextVertexConsumerProvider
                                    != null
                            ? com.radiance.client.proxy.world.EntityProxy
                                    .postTextVertexConsumerProvider
                            : buffers,
                    light,
                    partial);
    }
}
