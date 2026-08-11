package com.radiance.audit;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.radiance.client.proxy.world.EntityProxy;
import com.radiance.client.render.MaterialFaces;
import com.radiance.client.vertex.PBRVertexFormats;
import com.radiance.client.vertex.StorageVertexConsumerProvider;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import net.minecraft.client.renderer.RenderType;

/** Real callback transaction and sealed-producer/JNI checks; no timings or permanent state. */
public final class SurfaceContractProbe {
    private SurfaceContractProbe() {}

    public static void verify(String boundary) {
        RenderSystem.assertOnRenderThread();
        var saved = RenderSystem.getShaderColor().clone();
        float[] expected = {.25f, .5f, .75f, .125f};
        var layer =
                layer(
                        () ->
                                RenderSystem.setShaderColor(
                                        expected[0], expected[1], expected[2], expected[3]),
                        () -> RenderSystem.setShaderColor(.9f, .8f, .7f, .6f));
        try {
            RenderSystem.setShaderColor(.6f, .7f, .8f, .9f);
            var caller = RenderSystem.getShaderColor().clone();
            var snapshot = MaterialFaces.captureSnapshot(layer);
            require(
                    Arrays.equals(caller, RenderSystem.getShaderColor()),
                    "draw callback leaked global colour");
            var bytes = ByteBuffer.allocateDirect(16).order(ByteOrder.nativeOrder());
            snapshot.writeColour(bytes, 0);
            require(
                    Arrays.equals(expected, EntityProxy.inspectDrawColour(bytes, 0)),
                    "draw colour JNI mismatch");
            var failure =
                    layer(
                            () -> {
                                RenderSystem.setShaderColor(.1f, .2f, .3f, .4f);
                                throw new IllegalStateException("source-setup");
                            },
                            () -> {
                                RenderSystem.setShaderColor(1, 1, 1, 1);
                                throw new IllegalArgumentException("source-clear");
                            });
            try {
                MaterialFaces.captureSnapshot(failure);
                throw new AssertionError("source failure was swallowed");
            } catch (IllegalStateException primary) {
                require(
                        primary.getMessage().equals("source-setup")
                                && primary.getSuppressed().length == 1
                                && primary.getSuppressed()[0].getMessage().equals("source-clear"),
                        "source/cleanup error identity");
            }
            require(
                    Arrays.equals(caller, RenderSystem.getShaderColor()),
                    "exception leaked global colour");
            var provider = new StorageVertexConsumerProvider(256);
            try {
                var first = provider.getBuffer(layer);
                first.addVertex(0, 0, 0).setColor(255, 255, 255, 128);
                provider.endBatch(layer);
                var captured = provider.drawState(first);
                var next = provider.getBuffer(layer);
                require(first != next, "sealed source consumer was reused");
                provider.endBatch();
                require(
                        captured.equals(provider.drawState(first))
                                && provider.getLayerBatches().size() == 2,
                        "sealed batch state was replaced");
            } finally {
                provider.close();
            }
            var texture =
                    net.minecraft.resources.ResourceLocation.withDefaultNamespace(
                            "textures/entity/spider_eyes.png");
            int eyeFaces = MaterialFaces.capture(RenderType.eyes(texture));
            int bodyFaces = MaterialFaces.capture(RenderType.entityCutoutNoCull(texture));
            com.mojang.logging.LogUtils.getLogger()
                    .info(
                            "SURFACE_SOURCE boundary={} eye-faces={} sample-body-faces={} different-facing-keeps-independent-overlay={}",
                            boundary,
                            eyeFaces,
                            bodyFaces,
                            eyeFaces != bodyFaces);
            var eyePolicy =
                    com.radiance.adapter.vanilla.VanillaEmissionAdapter.policy(
                            RenderType.eyes(texture),
                            com.radiance.render.contract.SourceEmission.Role.ORDINARY,
                            false);
            require(
                    eyePolicy.eyeOverlay() && eyePolicy.forceUnit() && eyePolicy.excludedEyeBase(),
                    "live eye program policy");
            require(
                    com.radiance.adapter.vanilla.VanillaEmissionAdapter.policy(
                                    RenderType.text(texture),
                                    com.radiance.render.contract.SourceEmission.Role.NAME_TAG,
                                    false)
                            .forceUnit(),
                    "live name glyph policy");
            require(
                    !com.radiance.adapter.vanilla.VanillaEmissionAdapter.policy(
                                    RenderType.textBackground(),
                                    com.radiance.render.contract.SourceEmission.Role.NAME_TAG,
                                    false)
                            .forceUnit(),
                    "live name background policy");
            com.mojang.logging.LogUtils.getLogger()
                    .info(
                            "SURFACE_CONTRACT boundary={} callbacks=normal+exception colour-jni=pass sealed-batches=pass emission-source-policy=pass violations=0",
                            boundary);
        } finally {
            RenderSystem.setShaderColor(saved[0], saved[1], saved[2], saved[3]);
        }
    }

    private static RenderType layer(Runnable setup, Runnable clear) {
        return new RenderType(
                "surface_contract_probe",
                PBRVertexFormats.PBR_TRIANGLE,
                VertexFormat.Mode.QUADS,
                256,
                false,
                false,
                setup,
                clear) {};
    }

    private static void require(boolean value, String message) {
        if (!value) throw new IllegalStateException(message);
    }
}
