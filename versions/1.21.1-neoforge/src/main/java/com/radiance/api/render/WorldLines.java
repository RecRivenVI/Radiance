package com.radiance.api.render;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.radiance.client.render.*;
import com.radiance.client.vertex.CapturedLineVertexConsumer;
import com.radiance.client.vertex.StorageVertexConsumerProvider;
import com.radiance.render.contract.LineContract.WidthPolicy;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.renderer.RenderType;

/** Owns only explicit line batches produced inside a live world event, never surface/UI buffers. */
public final class WorldLines {
    private record Batch(
            Object owner,
            WorldScene.StageContext stage,
            RenderType layer,
            StorageVertexConsumerProvider storage,
            CapturedLineVertexConsumer consumer,
            int sequence) {}

    private static final ThreadLocal<List<Batch>> BATCHES = ThreadLocal.withInitial(ArrayList::new);

    private WorldLines() {}

    public static VertexConsumer getBuffer(Object owner, RenderType layer) {
        var stage = WorldScene.currentStage();
        if (stage == null
                || !WorldScene.isDefaultTarget()
                || !CapturedLineVertexConsumer.supports(layer.mode(), layer.format())) return null;
        var source = LineSources.current(LineSources.NEOFORGE_EVENT);
        var batches = BATCHES.get();
        for (var batch : batches) {
            if (batch.owner == owner
                    && batch.stage == stage
                    && batch.layer == layer
                    && batch.consumer.source.equals(source)
                    && layer.canConsolidateConsecutiveGeometry()) return batch.consumer;
        }
        var policy =
                layer.mode() == com.mojang.blaze3d.vertex.VertexFormat.Mode.DEBUG_LINES
                                || layer.mode()
                                        == com.mojang.blaze3d.vertex.VertexFormat.Mode
                                                .DEBUG_LINE_STRIP
                        ? WidthPolicy.DEBUG
                        : LineSources.eventWidth(source);
        var storage = new StorageVertexConsumerProvider(4096, 1).lineSource(source, policy);
        var consumer = (CapturedLineVertexConsumer) storage.getBuffer(layer);
        batches.add(
                new Batch(
                        owner,
                        stage,
                        layer,
                        storage,
                        consumer,
                        WorldScene.nextLineSequence(source)));
        return consumer;
    }

    public static void flush(Object owner, RenderType layer) {
        var stage = WorldScene.currentStage();
        if (stage == null) return;
        finish(stage, owner, layer, true);
    }

    static void finishStage(WorldScene.StageContext stage, boolean committed) {
        finish(stage, null, null, committed);
    }

    private static void finish(
            WorldScene.StageContext stage, Object owner, RenderType layer, boolean committed) {
        var batches = BATCHES.get();
        // Remove before submitting: state capture can itself reach a buffer source.
        List<Batch> selected = new ArrayList<>();
        batches.removeIf(
                batch -> {
                    boolean take =
                            batch.stage == stage
                                    && (owner == null || batch.owner == owner)
                                    && (layer == null || batch.layer == layer);
                    if (take) selected.add(batch);
                    return take;
                });
        try {
            for (var batch : selected) {
                if (committed) {
                    LineAudit.event(batch.consumer.source);
                    WorldScene.submitCaptured(
                            batch.storage,
                            batch.owner,
                            "line/" + batch.consumer.source.label() + "/batch/" + batch.sequence);
                } else batch.storage.close();
            }
        } finally {
            // A failed submission must still close every remaining allocation.
            for (var batch : selected) batch.storage.close();
            if (batches.isEmpty()) BATCHES.remove();
        }
    }
}
