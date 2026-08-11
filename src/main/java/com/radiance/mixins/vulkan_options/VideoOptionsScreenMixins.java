package com.radiance.mixins.vulkan_options;

import static net.minecraft.client.Options.genericValueLabel;

import com.google.common.collect.ImmutableList;
import com.mojang.blaze3d.platform.Monitor;
import com.mojang.blaze3d.platform.VideoMode;
import com.mojang.blaze3d.platform.Window;
import com.mojang.serialization.Codec;
import com.radiance.client.gui.PotentialValuesBasedCallbacksNoValue;
import com.radiance.client.gui.RenderPipelineScreen;
import com.radiance.client.option.Options;
import com.radiance.client.util.CategoryVideoOptionEntry;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.gui.screens.options.VideoSettingsScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(VideoSettingsScreen.class)
public class VideoOptionsScreenMixins extends GameOptionsScreenMixins {

    @Unique
    private static final PotentialValuesBasedCallbacksNoValue<Boolean> BOOLEAN_NO_KEY = new PotentialValuesBasedCallbacksNoValue<>(
        ImmutableList.of(Boolean.TRUE, Boolean.FALSE), Codec.BOOL
    );

    @Inject(method = "addOptions()V", at = @At(value = "HEAD"), cancellable = true)
    public void redirectAddOptions(CallbackInfo ci) {
        OptionInstance<Integer>
            maxFps =
            new OptionInstance<>("options.framerateLimit",
                OptionInstance.noTooltip(),
                (optionText, value) -> value == 260 ?
                    genericValueLabel(optionText, Component.translatable("options.framerateLimit.max"))
                    :
                        genericValueLabel(optionText,
                            Component.translatable("options.framerate", value)),
                new OptionInstance.IntRange(1, 26).xmap(
                    value -> value * 10, value -> value / 10),
                Codec.intRange(10, 260),
                Options.maxFps,
                value -> {
                    Minecraft.getInstance().getWindow().setFramerateLimit(value);
                    Options.setMaxFps(value, true);
                });

        int i = -1;
        Window
            window =
            Minecraft.getInstance()
                .getWindow();
        Monitor monitor = window.findBestMonitor();
        int j;
        if (monitor == null) {
            j = -1;
        } else {
            Optional<VideoMode> optional = window.getPreferredFullscreenVideoMode();
            j =
                optional.map(monitor::getVideoModeIndex)
                    .orElse(-1);
        }

        OptionInstance<Integer>
            fullScreenResolutionOption =
            new OptionInstance<>("options.fullscreen.resolution", OptionInstance.noTooltip(),
                (optionText, value) -> {
                    if (monitor == null) {
                        return Component.translatable("options.fullscreen.unavailable");
                    } else if (value == -1) {
                        return genericValueLabel(optionText,
                            Component.translatable("options.fullscreen.current"));
                    } else {
                        VideoMode videoMode = monitor.getMode(value);
                        return genericValueLabel(optionText,
                            Component.translatable("options.fullscreen.entry",
                                videoMode.getWidth(),
                                videoMode.getHeight(),
                                videoMode.getRefreshRate(),
                                videoMode.getRedBits() + videoMode.getGreenBits() +
                                    videoMode.getBlueBits()));
                    }
                }, new OptionInstance.IntRange(-1,
                monitor != null ? monitor.getModeCount() - 1 : -1), j, value -> {
                if (monitor != null) {
                    window.setPreferredFullscreenVideoMode(
                        value == -1 ? Optional.empty() : Optional.of(monitor.getMode(value)));
                }
            });

        OptionInstance<Boolean> enableVsync = OptionInstance.createBoolean("options.vsync", Options.vsync,
            value -> {
                if (Minecraft.getInstance()
                    .getWindow() != null) {
                    Options.setVsync(value, true);
                }
            });

        OptionInstance<Integer>
            chunkBuildingBatchSize =
            new OptionInstance<>(Options.CHUNK_BUILDING_BATCH_SIZE_KEY,
                OptionInstance.noTooltip(),
                (optionText, value) -> genericValueLabel(optionText,
                    Component.literal(Integer.toString(value))),
                new OptionInstance.IntRange(1, 32),
                Codec.intRange(1, 32),
                Options.chunkBuildingBatchSize,
                value -> {
                    Options.setChunkBuildingBatchSize(value, true);
                });

        OptionInstance<Integer>
            chunkBuildingTotalBatches =
            new OptionInstance<>(Options.CHUNK_BUILDING_TOTAL_BATCHES_KEY,
                OptionInstance.noTooltip(),
                (optionText, value) -> genericValueLabel(optionText,
                    Component.literal(Integer.toString(value))),
                new OptionInstance.IntRange(1, 32),
                Codec.intRange(1, 32),
                Options.chunkBuildingTotalBatches,
                value -> {
                    Options.setChunkBuildingTotalBatches(value, true);
                });

        OptionInstance<Integer>
            chunkBuildingThreads =
            new OptionInstance<>(Options.CHUNK_BUILDING_THREADS_KEY, OptionInstance.noTooltip(),
                (optionText, value) -> genericValueLabel(optionText,
                    Component.literal(Integer.toString(value))),
                new OptionInstance.IntRange(1,
                    Options.getMaxChunkBuildingThreads()),
                Codec.intRange(1, Options.getMaxChunkBuildingThreads()),
                Options.chunkBuildingThreads,
                value -> Options.setChunkBuildingThreads(value, true));

        OptionInstance<Boolean> collectChunkEmission = OptionInstance.createBoolean(
            Options.COLLECT_CHUNK_EMISSION_KEY,
            Options.collectChunkEmission,
            value -> Options.setCollectChunkEmission(value, true));

        OptionInstance<Boolean> pipelineSettings = new OptionInstance<>(Options.PIPELINE_SETUP_KEY,
            OptionInstance.noTooltip(),
            (optionText, value) -> optionText,
            BOOLEAN_NO_KEY,
            false,
            value -> {
                Minecraft.getInstance()
                    .setScreen(new RenderPipelineScreen((VideoSettingsScreen) (Object) this));
            });

        OptionInstance<Boolean> reconstructionFeatures = new OptionInstance<>("options.video.features",
            OptionInstance.noTooltip(), (label,value)->label, BOOLEAN_NO_KEY, false,
            value -> Minecraft.getInstance().setScreen(new com.radiance.client.gui.RenderFeaturesScreen((VideoSettingsScreen)(Object)this)));
        OptionInstance<Integer> dlssSrModel = new OptionInstance<>("options.video.dlss_sr_model",
            OptionInstance.noTooltip(), (label, value) -> genericValueLabel(label,
                value == 0 ? Component.translatable("options.video.dlss_model.default") : (value == 5 || value == 6)
                    ? Component.translatable("options.video.dlss_model.deprecated", Character.toString((char) ('A' + value - 1)))
                    : Component.literal(Character.toString((char) ('A' + value - 1)))),
            new OptionInstance.Enum<>(Options.DLSS_SR_MODELS, Codec.INT), Options.dlssSrModel,
            value -> Options.setDlssModels(value, Options.dlssRrModel, Options.dlssFgModel, Options.dlssFrameGeneration, true));
        OptionInstance<Integer> dlssRrModel = new OptionInstance<>("options.video.dlss_rr_model",
            OptionInstance.noTooltip(), (label, value) -> genericValueLabel(label,
                value == 0 ? Component.translatable("options.video.dlss_model.default") : Component.literal(Character.toString((char) ('A' + value - 1)))),
            new OptionInstance.Enum<>(Options.DLSS_RR_MODELS, Codec.INT), Options.dlssRrModel,
            value -> Options.setDlssModels(Options.dlssSrModel, value, Options.dlssFgModel, Options.dlssFrameGeneration, true));
        OptionInstance<Boolean> dlssFrameGeneration = OptionInstance.createBoolean("options.video.dlss_frame_generation",
            Options.dlssFrameGeneration, value -> Options.setDlssModels(Options.dlssSrModel, Options.dlssRrModel, 0, value, true));

        OptionInstance<Integer> reflex = new OptionInstance<>("options.video.reflex",
            value -> net.minecraft.client.gui.components.Tooltip.create(Component.translatable("options.video.reflex.tooltip")),
            (label, value) -> genericValueLabel(label, Component.translatable("options.video.reflex." + value)),
            new OptionInstance.Enum<>(java.util.List.of(0, 1, 2), Codec.INT), Options.reflexMode,
            value -> Options.setReflexMode(value, true));

        // Adding categories and options
        this.list.addEntry(
            new CategoryVideoOptionEntry(Component.translatable(Options.CATEGORY_GAMEPLAY), list));
        OptionInstance[] optionsGameplay = new OptionInstance[]{ //
            options.graphicsMode(), //
            options.renderDistance(), //
            options.simulationDistance(), //
            options.guiScale(), //
            options.attackIndicator(), //
            options.gamma(), //
            options.cloudStatus(), //
            options.particles(), //
            options.screenEffectScale(), //
            options.entityDistanceScaling(), //
            options.fovEffectScale(), //
            options.showAutosaveIndicator(), //
            options.glintSpeed(), //
            options.glintStrength(), //
            options.menuBackgroundBlurriness(), //
            options.bobView(), //
        };
        this.list.addBig(options.biomeBlendRadius());
        this.list.addBig(options.mipmapLevels());
        this.list.addSmall(optionsGameplay);

        this.list.addEntry(
            new CategoryVideoOptionEntry(Component.translatable(Options.CATEGORY_WINDOW), list));
        OptionInstance[] optionsWindow = new OptionInstance[]{ //
            maxFps, //
            enableVsync, //
            options.fullscreen(), //
        };
        this.list.addSmall(optionsWindow);
        this.list.addBig(fullScreenResolutionOption);

        this.list.addEntry(
            new CategoryVideoOptionEntry(Component.translatable(Options.CATEGORY_TERRAIN), list));
        this.list.addBig(chunkBuildingBatchSize);
        this.list.addBig(chunkBuildingTotalBatches);
        this.list.addBig(chunkBuildingThreads);
        this.list.addBig(collectChunkEmission);

        this.list.addEntry(
            new CategoryVideoOptionEntry(Component.translatable(Options.CATEGORY_PIPELINE), list));
        this.list.addBig(reconstructionFeatures);
        this.list.addBig(pipelineSettings);
        this.list.addEntry(new CategoryVideoOptionEntry(Component.translatable(Options.CATEGORY_DLSS), list));
        this.list.addBig(dlssSrModel);
        this.list.addBig(dlssRrModel);
        this.list.addBig(dlssFrameGeneration);
        this.list.addBig(reflex);
        var fgButton = this.list.findOption(dlssFrameGeneration);
        if (fgButton != null) {
            fgButton.active = Options.nativeIsDlssFrameGenerationAvailable();
            fgButton.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.translatable(
                fgButton.active ? "options.video.dlss_frame_generation.tooltip" : "options.video.dlss_frame_generation.unavailable")));
        }

        ci.cancel();
    }
}
