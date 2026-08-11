package com.radiance.api.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.radiance.client.vertex.LocalMeshTakeover;

/** Source-declared immutable templates; pose and appearance never become part of the mesh key. */
public final class LocalGeometry {
    private LocalGeometry() {}

    /** Tests the actual world consumer without creating a template or emitting vertices. */
    public static boolean supported(PoseStack.Pose pose, VertexConsumer consumer) {
        return LocalMeshTakeover.supported(pose, consumer);
    }

    /** Retires a source-owned definition after its inputs change. In-flight users stay alive. */
    public static void release(Object definitionKey) {
        LocalMeshTakeover.release(definitionKey);
    }

    @FunctionalInterface
    public interface Definition {
        void emit(PoseStack.Pose pose, VertexConsumer consumer);
    }

    /** False leaves the consumer untouched; screen previews keep their original renderer. */
    public static boolean submit(
            Object definitionKey,
            PoseStack.Pose pose,
            VertexConsumer consumer,
            LocalSurface surface,
            Definition definition) {
        return LocalMeshTakeover.tryTemplate(
                definitionKey,
                pose,
                consumer,
                surface,
                (local, out, light, color) -> definition.emit(local, out));
    }
}
