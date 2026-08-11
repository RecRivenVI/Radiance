package com.radiance.audit;

import com.radiance.api.render.RenderResources;
import com.radiance.api.render.ScreenEffects;
import com.radiance.client.constant.VulkanConstants;
import com.radiance.client.proxy.vulkan.TextureProxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.lwjgl.system.MemoryUtil;

/** Actual native graph, resource and reload consumers; runs after the pose sample, never in it. */
public final class PlatformCloseoutProbe {
    private static final boolean ENABLED = Boolean.getBoolean("radiance.audit.platformCloseout");
    private static final Path OUT = Path.of(System.getProperty("validation.result", "."));
    private static final java.util.Map<String, Object> results = new LinkedHashMap<>();
    private static ScreenEffects.SpirvProgram custom;
    private static java.util.concurrent.CompletableFuture<Void> reload;
    private static int stage, rendered, draws, reference;
    private static boolean active, done;

    private PlatformCloseoutProbe() {}

    static void register() {
        if (ENABLED) NeoForge.EVENT_BUS.addListener(PlatformCloseoutProbe::render);
    }

    static boolean complete(Minecraft mc) {
        if (!ENABLED || done) return true;
        try {
            if (!active) {
                active = true;
                resourceContract();
                results.put("sources", com.radiance.api.render.RenderPlatform.integrations());
                custom =
                        new ScreenEffects.SpirvProgram(
                                "probe:invert",
                                Files.readAllBytes(
                                        Path.of(System.getProperty("radiance.audit.screenSpirv"))),
                                1);
                stage = 1;
            }
            if (stage == 4) {
                if (!reload.isDone() || mc.getOverlay() != null) return false;
                reload.join();
                stage = 5;
            }
            if (rendered == stage && draws >= 4 && stage != 4) {
                try (var image = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
                    int pixel = image.getPixelRGBA(60, 60);
                    results.put("stage" + stage, Integer.toUnsignedString(pixel, 16));
                    image.writeToFile(OUT.resolve("effect-" + stage + ".png"));
                    if (stage == 1) reference = pixel;
                    if ((stage == 2 || stage == 5) && pixel != reference)
                        throw new AssertionError(
                                "Registered program differs from the original invert shader");
                    if (stage == 3) {
                        for (int n = 0; n < 3; n++) {
                            int expected = Math.round(204f - .6f * ((reference >>> (n * 8)) & 255));
                            if (Math.abs(((pixel >>> (n * 8)) & 255) - expected) > 2)
                                throw new AssertionError(
                                        "Declared intermediate input/size was not consumed: "
                                                + pixel);
                        }
                    }
                }
                if (stage == 3) {
                    results.put(
                            "generationBeforeReload",
                            com.radiance.api.render.internal.ScreenEffectsNative.generation());
                    stage = 4;
                    reload = mc.reloadResourcePacks();
                } else if (stage == 7) {
                    custom.close();
                    done = true;
                    results.put("normalReload", true);
                    results.put(
                            "generationAfterReload",
                            com.radiance.api.render.internal.ScreenEffectsNative.generation());
                    results.put("visualAcceptance", "pending");
                    Files.writeString(
                            OUT.resolve("effect-resources.json"),
                            new com.google.gson.GsonBuilder()
                                            .setPrettyPrinting()
                                            .create()
                                            .toJson(results)
                                    + "\n");
                    com.mojang.logging.LogUtils.getLogger()
                            .info("PLATFORM_CLOSEOUT PASS native graph/resources/reload");
                } else stage++;
            }
            return done;
        } catch (Exception | AssertionError failure) {
            throw new IllegalStateException("Platform closeout failed", failure);
        }
    }

    private static void render(RenderGuiEvent.Post event) {
        if (!active || done || stage == 4) return;
        if (rendered != stage) draws = 0;
        var g = event.getGuiGraphics();
        g.fill(0, 0, g.guiWidth(), g.guiHeight(), 0xffd02040);
        g.flush();
        if (stage >= 6) {
            if (!ScreenEffects.worldPost(
                    stage == 6 ? ScreenEffects.Program.CREEPER : ScreenEffects.Program.SPIDER))
                throw new AssertionError("Native world effect graph was not executed");
            rendered = stage;
            draws++;
            return;
        }
        ScreenEffects.Kernel program = stage == 1 ? ScreenEffects.Program.INVERT : custom;
        var sizes =
                stage == 3
                        ? List.of(new ScreenEffects.Size(2, 2), ScreenEffects.Size.full())
                        : List.of(ScreenEffects.Size.full());
        var first =
                new ScreenEffects.Pass(
                        program, 1, List.of(0), RenderResources.Sampling.NEAREST, 0, 0, 0, 0);
        var passes =
                stage == 3
                        ? List.of(
                                first,
                                new ScreenEffects.Pass(ScreenEffects.Program.INVERT, 2, List.of(1)))
                        : List.of(first);
        if (!ScreenEffects.submit(
                new ScreenEffects.Plan(
                        ScreenEffects.Role.GUI_POST, sizes, passes, stage == 3 ? 2 : 1)))
            throw new AssertionError("Native effect graph was not executed");
        rendered = stage;
        draws++;
    }

    private static void resourceContract() {
        int id = TextureProxy.generateTextureId();
        try {
            TextureProxy.setFilter(id, 1, 0);
            TextureProxy.setClamp(id, 2);
            TextureProxy.prepareImage(
                    id, 1, 2, 2, VulkanConstants.VkFormat.VK_FORMAT_R8G8B8A8_UNORM);
            TextureProxy.setFilter(id, 1, 0);
            results.put("parametersBeforeStorage", true);
        } finally {
            TextureProxy.releaseTextureId(id, 0);
        }
        var data = MemoryUtil.memAlloc(16);
        try (var texture =
                        RenderResources.rgba8(
                                2,
                                2,
                                1,
                                RenderResources.Sampling.NEAREST,
                                RenderResources.Sampling.NEAREST);
                var buffer = RenderResources.buffer()) {
            for (int i = 0; i < 4; i++) data.putInt(0xff302010);
            data.flip();
            texture.upload(0, 0, 0, 2, 2, data);
            buffer.initialize(16, RenderResources.BufferUse.VERTEX);
            buffer.upload(0, data);
            buffer.initialize(32, RenderResources.BufferUse.VERTEX);
            buffer.upload(16, data);
            results.put("ownedResourceUploadReplaceClose", true);
        } finally {
            MemoryUtil.memFree(data);
        }
    }
}
