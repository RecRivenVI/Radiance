package com.radiance.client.loading;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.radiance.bootstrap.RadianceImmediateWindowProvider;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.LoadingOverlay;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ReloadInstance;
import net.minecraft.util.Mth;
import net.neoforged.fml.loading.progress.ProgressMeter;
import net.neoforged.fml.loading.progress.StartupNotificationManager;

/**
 * GAME-side loading overlay for the window and renderer owned by the Radiance
 * SERVICE provider.
 *
 * <p>The loading scene is kept live after the window handoff. The game owns
 * submission and presentation at this point, so the provider records the
 * overlay into the current Vulkan UI target with {@code gameFrame=true}.</p>
 */
public final class RadianceLoadingOverlay extends LoadingOverlay {
    private static final ResourceLocation MOJANG_LOGO =
            ResourceLocation.withDefaultNamespace("textures/gui/title/mojangstudios.png");
    private static final int MOJANG_TEXTURE_KEY = 4;

    private final Minecraft minecraft;
    private final ReloadInstance reload;
    private final Consumer<Optional<Throwable>> onFinish;
    private final RadianceImmediateWindowProvider provider;
    private final ProgressMeter progressMeter;
    private float currentProgress;
    private long fadeOutStart = -1L;

    public RadianceLoadingOverlay(final Minecraft minecraft,
                                  final ReloadInstance reload,
                                  final Consumer<Optional<Throwable>> errorConsumer,
                                  final RadianceImmediateWindowProvider provider,
                                  final boolean fadeIn) {
        // Match NeoForgeLoadingOverlay: the custom overlay owns its fade and
        // must not let the vanilla superclass issue OpenGL work.
        super(minecraft, reload, errorConsumer, false);
        this.minecraft = Objects.requireNonNull(minecraft, "minecraft");
        this.reload = Objects.requireNonNull(reload, "reload");
        this.onFinish = Objects.requireNonNull(errorConsumer, "errorConsumer");
        this.provider = Objects.requireNonNull(provider, "provider");
        this.progressMeter = StartupNotificationManager.prependProgressBar("Minecraft Progress", 1000);
        loadMojangTexture();
    }

    /**
     * Factory resolved reflectively by the SERVICE-side provider after the
     * GAME layer has been created.
     */
    public static Supplier<LoadingOverlay> newInstance(
            final Supplier<Minecraft> minecraft,
            final Supplier<ReloadInstance> reload,
            final Consumer<Optional<Throwable>> errorConsumer,
            final RadianceImmediateWindowProvider provider,
            final boolean fadeIn) {
        return () -> new RadianceLoadingOverlay(
                minecraft.get(), reload.get(), errorConsumer, provider, fadeIn);
    }

    @Override
    public void render(final GuiGraphics graphics, final int mouseX, final int mouseY,
                       final float partialTick) {
        long millis = Util.getMillis();
        float fadeouttimer = fadeOutStart > -1L
                ? (float) (millis - fadeOutStart) / 1000.0F
                : -1.0F;

        currentProgress = Mth.clamp(
                currentProgress * 0.95F + reload.getActualProgress() * 0.05F,
                0.0F, 1.0F);
        progressMeter.setAbsolute(Mth.ceil(currentProgress * 1000));

        float fade = 1.0F - Mth.clamp(fadeouttimer - 1.0F, 0.0F, 1.0F);
        float opacity = Mth.clamp(fade, 0.0F, 1.0F);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, fade);

        // This is the same late-loading ordering as NeoForgeLoadingOverlay:
        // once the fade has begun, let the current game screen form the base
        // and record the loading scene above it.
        if (fadeouttimer >= 1.0F) {
            if (minecraft.screen != null) minecraft.screen.render(graphics, 0, 0, partialTick);
        } else {
            int background = provider.backgroundAbgr();
            // Preserve NeoForge's clear-state side effect as well as its visible background.
            // The existing GlStateManager mixins route these calls to Vulkan.
            GlStateManager._clearColor((background & 255) / 255.0F,
                    ((background >>> 8) & 255) / 255.0F,
                    ((background >>> 16) & 255) / 255.0F, 1.0F);
            GlStateManager._clear(16384, Minecraft.ON_OSX);
        }

        // GuiGraphics batches can otherwise be submitted after the native
        // loading draw and cover it. Flush the game-side UI before recording
        // the loading frame.
        graphics.flush();

        if (fadeouttimer >= 2.0F) {
            progressMeter.complete();
            minecraft.setOverlay(null);
            // Release only loading resources. The shared game renderer,
            // device and swapchain remain owned by the game/native runtime.
            provider.releaseLoading();
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
            return;
        }

        int framebufferWidth = minecraft.getWindow().getWidth();
        int framebufferHeight = minecraft.getWindow().getHeight();
        // The provider renders the scene at the official opaque alpha (255)
        // and applies this value only while compositing into the game's UI
        // target. Keep the background pass enabled so the clear/background
        // behavior remains the same throughout the fade.
        provider.onGameFrame(opacity, framebufferWidth, framebufferHeight, true);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);

        if (fadeOutStart == -1L && reload.isDone()) {
            fadeOutStart = millis;
            try {
                reload.checkExceptions();
                onFinish.accept(Optional.empty());
            } catch (Throwable throwable) {
                onFinish.accept(Optional.of(throwable));
            }

            if (minecraft.screen != null) {
                minecraft.screen.init(minecraft,
                        minecraft.getWindow().getGuiScaledWidth(),
                        minecraft.getWindow().getGuiScaledHeight());
            }
        }
    }

    private void loadMojangTexture() {
        try (InputStream input = minecraft.getResourceManager().open(MOJANG_LOGO);
             NativeImage image = NativeImage.read(input)) {
            int width = image.getWidth();
            int height = image.getHeight();
            ByteBuffer rgba = ByteBuffer.allocateDirect(Math.multiplyExact(
                    Math.multiplyExact(width, height), Integer.BYTES))
                    .order(ByteOrder.nativeOrder());
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    // NativeImage stores the official ABGR packed value. On
                    // the supported little-endian hosts, putInt emits the
                    // required raw RGBA8 byte order for the loading upload.
                    rgba.putInt(image.getPixelRGBA(x, y));
                }
            }
            rgba.flip();
            // LoadingScene's DrawSink consumes RGBA8 bytes immediately while
            // the provider holds its render lock; the direct buffer remains
            // valid for the native upload call.
            provider.addMojangTexture(MOJANG_TEXTURE_KEY,
                    width, height, rgba, true, true);
        } catch (IOException e) {
            throw new IllegalStateException("Unable to load the Mojang loading texture", e);
        }
    }
}
