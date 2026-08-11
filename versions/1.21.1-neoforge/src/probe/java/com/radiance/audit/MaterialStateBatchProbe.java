package com.radiance.audit;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.radiance.client.proxy.vulkan.FramebufferProxy;
import com.radiance.client.proxy.vulkan.PipelineStateProxy;
import com.radiance.client.render.MaterialFaces;
import com.radiance.client.vertex.PBRVertexFormats;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.client.renderer.RenderType;
import net.neoforged.neoforge.client.GlStateBackup;

/** Explicit functional window: real callbacks, Mojang cache and live native shadow readback. */
final class MaterialStateBatchProbe {
    private MaterialStateBatchProbe() {}

    static void verify(String boundary) {
        if (!Boolean.getBoolean("radiance.audit.materialStateContract")
                || !ExperimentAccess.permitted()) return;
        RenderSystem.assertOnRenderThread();
        var original = new GlStateBackup();
        RenderSystem.backupGlState(original);
        int[] stencil = PipelineStateProxy.DepthStencilState.snapshotStencil();
        int[] routing = FramebufferProxy.captureMaterialRouting();
        var colour = RenderSystem.getShaderColor().clone();
        int a = FramebufferProxy.createFramebuffer(), b = FramebufferProxy.createFramebuffer();
        try {
            // Different per-face operations must survive a combined function/mask update.
            PipelineStateProxy.DepthStencilState.vkSetStencilFrontOp(0, 1, 2);
            PipelineStateProxy.DepthStencilState.vkSetStencilBackOp(3, 4, 5);
            PipelineStateProxy.DepthStencilState.vkSetStencilFunc(2, 17, 111);
            PipelineStateProxy.DepthStencilState.vkSetStencilWriteMask(57);
            require(
                    Arrays.equals(
                            PipelineStateProxy.DepthStencilState.snapshotStencil(),
                            new int[] {2, 17, 111, 0, 1, 2, 57, 2, 17, 111, 3, 4, 5, 57}),
                    "combined function/mask changed independent operations");
            PipelineStateProxy.DepthStencilState.vkSetStencilOp(1, 2, 3);
            require(
                    Arrays.equals(
                            PipelineStateProxy.DepthStencilState.snapshotStencil(),
                            new int[] {2, 17, 111, 1, 2, 3, 57, 2, 17, 111, 1, 2, 3, 57}),
                    "combined operations changed function/mask");

            FramebufferProxy.bindFramebufferPair(a, b);
            RenderSystem.viewport(7, 11, 40, 30);
            require(
                    Arrays.equals(
                            FramebufferProxy.captureMaterialRouting(),
                            new int[] {a, b, 7, 11, 40, 30}),
                    "distinct read/draw routing");
            int dead = FramebufferProxy.createFramebuffer();
            FramebufferProxy.deleteFramebuffer(dead);
            try {
                FramebufferProxy.bindFramebufferPair(b, dead);
                throw new AssertionError("invalid second framebuffer accepted");
            } catch (IllegalStateException expected) {
                require(
                        FramebufferProxy.boundFramebuffer(FramebufferProxy.READ_FRAMEBUFFER) == b,
                        "first successful bind lost on second-bind failure");
            }
            restoreRouting(routing);

            // Synchronize a nondefault cache before comparison; native and Mojang state are
            // independent at arbitrary pass boundaries.
            GlStateManager._stencilFunc(513, 19, 127);
            GlStateManager._stencilMask(63);
            GlStateManager._stencilOp(7680, 7682, 7681);
            RenderSystem.enableBlend();
            RenderSystem.depthMask(false);
            var before = cache();
            var nativeBefore = PipelineStateProxy.DepthStencilState.snapshotStencil();
            var outer =
                    layer(
                            () -> {
                                FramebufferProxy.bindFramebufferPair(a, b);
                                RenderSystem.viewport(3, 5, 23, 29);
                                RenderSystem.setShaderColor(.2f, .4f, .6f, .8f);
                                var nestedCache = cache();
                                var nestedRouting = FramebufferProxy.captureMaterialRouting();
                                var nestedStencil =
                                        PipelineStateProxy.DepthStencilState.snapshotStencil();
                                MaterialFaces.captureSnapshot(
                                        layer(
                                                () -> {
                                                    RenderSystem.disableBlend();
                                                    RenderSystem.depthMask(true);
                                                    FramebufferProxy.bindFramebufferPair(b, a);
                                                    RenderSystem.viewport(9, 2, 17, 19);
                                                    GlStateManager._stencilFunc(516, 31, 95);
                                                },
                                                () -> RenderSystem.setShaderColor(1, 1, 1, 1)));
                                require(nestedCache.equals(cache()), "nested Mojang cache");
                                require(
                                        Arrays.equals(
                                                nestedRouting,
                                                FramebufferProxy.captureMaterialRouting()),
                                        "nested routing");
                                require(
                                        Arrays.equals(
                                                nestedStencil,
                                                PipelineStateProxy.DepthStencilState
                                                        .snapshotStencil()),
                                        "nested native stencil");
                            },
                            () -> RenderSystem.setShaderColor(.9f, .8f, .7f, .6f));
            var snapshot = MaterialFaces.captureSnapshot(outer);
            require(snapshot.red() == .2f && snapshot.alpha() == .8f, "callback colour capture");
            require(before.equals(cache()), "normal Mojang restore");
            require(
                    Arrays.equals(
                            nativeBefore, PipelineStateProxy.DepthStencilState.snapshotStencil()),
                    "normal native restore");
            require(
                    Arrays.equals(routing, FramebufferProxy.captureMaterialRouting()),
                    "normal routing restore");
            try {
                MaterialFaces.captureSnapshot(
                        layer(
                                () -> {
                                    FramebufferProxy.bindFramebufferPair(a, b);
                                    RenderSystem.viewport(1, 2, 3, 4);
                                    throw new IllegalStateException("state-source");
                                },
                                () -> {
                                    throw new IllegalArgumentException("state-clear");
                                }));
                throw new AssertionError("source failure swallowed");
            } catch (IllegalStateException primary) {
                require(
                        "state-source".equals(primary.getMessage())
                                && primary.getSuppressed().length == 1
                                && "state-clear".equals(primary.getSuppressed()[0].getMessage()),
                        "first failure identity");
            }
            require(before.equals(cache()), "exception Mojang restore");
            require(
                    Arrays.equals(
                            nativeBefore, PipelineStateProxy.DepthStencilState.snapshotStencil()),
                    "exception native restore");
            require(
                    Arrays.equals(routing, FramebufferProxy.captureMaterialRouting()),
                    "exception routing restore");
            SurfaceContractProbe.verify(boundary);
            MaterialStateProbe.runForCity(
                    net.minecraft.client.Minecraft.getInstance(), "batch-" + boundary);
            com.mojang.logging.LogUtils.getLogger()
                    .info(
                            "MATERIAL_STATE_CONTRACT boundary={} batch={} stencil=pass routing=pass nested=pass exceptions=pass cache=pass violations=0",
                            boundary,
                            PipelineStateProxy.BATCH_MATERIAL_STATE);
        } finally {
            RenderSystem.restoreGlState(original);
            for (int face = 0; face < 2; face++) restoreStencil(stencil, face);
            restoreRouting(routing);
            RenderSystem.setShaderColor(colour[0], colour[1], colour[2], colour[3]);
            FramebufferProxy.deleteFramebuffer(a);
            FramebufferProxy.deleteFramebuffer(b);
        }
    }

    private static void restoreStencil(int[] state, int face) {
        int i = face * 7;
        if (face == 0) {
            PipelineStateProxy.DepthStencilState.vkSetStencilFrontFunc(
                    state[i], state[i + 1], state[i + 2]);
            PipelineStateProxy.DepthStencilState.vkSetStencilFrontOp(
                    state[i + 3], state[i + 4], state[i + 5]);
            PipelineStateProxy.DepthStencilState.vkSetStencilFrontWriteMask(state[i + 6]);
        } else {
            PipelineStateProxy.DepthStencilState.vkSetStencilBackFunc(
                    state[i], state[i + 1], state[i + 2]);
            PipelineStateProxy.DepthStencilState.vkSetStencilBackOp(
                    state[i + 3], state[i + 4], state[i + 5]);
            PipelineStateProxy.DepthStencilState.vkSetStencilBackWriteMask(state[i + 6]);
        }
    }

    private static void restoreRouting(int[] state) {
        FramebufferProxy.bindFramebufferPair(state[0], state[1]);
        RenderSystem.viewport(state[2], state[3], state[4], state[5]);
    }

    private static Map<String, Object> cache() {
        var state = new GlStateBackup();
        RenderSystem.backupGlState(state);
        Map<String, Object> values = new LinkedHashMap<>();
        try {
            for (var field : GlStateBackup.class.getFields())
                values.put(field.getName(), field.get(state));
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException(failure);
        }
        return values;
    }

    private static RenderType layer(Runnable setup, Runnable clear) {
        return new RenderType(
                "material_state_probe",
                PBRVertexFormats.PBR_TRIANGLE,
                VertexFormat.Mode.QUADS,
                256,
                false,
                false,
                setup,
                clear) {};
    }

    private static void require(boolean value, String message) {
        if (!value) throw new IllegalStateException("Material state contract: " + message);
    }
}
