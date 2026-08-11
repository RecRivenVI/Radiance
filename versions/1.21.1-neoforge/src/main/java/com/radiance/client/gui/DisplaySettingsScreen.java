package com.radiance.client.gui;

import com.mojang.serialization.Codec;
import com.radiance.client.option.Options;
import java.util.function.IntConsumer;
import java.util.function.IntUnaryOperator;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class DisplaySettingsScreen extends Screen {
    private final Screen parent;

    public DisplaySettingsScreen(Screen parent) {
        super(Component.translatable("options.video.display"));
        this.parent = parent;
    }

    private void apply() {
        Options.setDisplaySettings(
                Options.hdrEnabled,
                Options.displayWhiteNits,
                Options.uiWhiteNits,
                Options.displayPeakNits,
                Options.bloomEnabled,
                Options.bloomStrength,
                true);
    }

    private void slider(String key, int min, int max, int value, int y, IntConsumer accept) {
        slider(key, min, max, value, y, key.endsWith("peak") ? n -> n * 100 : n -> n, accept);
    }

    private void slider(
            String key,
            int min,
            int max,
            int value,
            int y,
            IntUnaryOperator display,
            IntConsumer accept) {
        var option =
                new OptionInstance<Integer>(
                        key,
                        OptionInstance.noTooltip(),
                        (label, number) ->
                                Component.translatable(
                                        "options.generic_value",
                                        label,
                                        number == 0
                                                        && (key.endsWith("peak")
                                                                || key.endsWith("ui_white"))
                                                ? Component.translatable(
                                                        "options.video.display.auto")
                                                : Component.literal(
                                                        Integer.toString(
                                                                display.applyAsInt(number)))),
                        new OptionInstance.IntRange(min, max),
                        Codec.intRange(min, max),
                        value,
                        next -> {
                            accept.accept(next);
                            apply();
                        });
        addRenderableWidget(option.createButton(minecraft.options, width / 2 - 150, y, 300));
    }

    @Override
    protected void init() {
        int y = 50;
        var hdr =
                OptionInstance.createBoolean(
                        "options.video.display.hdr",
                        Options.hdrEnabled,
                        value -> {
                            Options.hdrEnabled = value;
                            apply();
                        });
        addRenderableWidget(hdr.createButton(minecraft.options, width / 2 - 150, y, 300));
        slider(
                "options.video.display.white",
                80,
                400,
                Math.round(Options.displayWhiteNits),
                y += 24,
                value -> Options.displayWhiteNits = value);
        slider(
                "options.video.display.ui_white",
                0,
                93,
                Options.uiWhiteNits <= 0 ? 0 : Math.round((Options.uiWhiteNits - 70) / 10),
                y += 24,
                value -> value == 0 ? 0 : 70 + value * 10,
                value -> Options.uiWhiteNits = value == 0 ? 0 : 70 + value * 10);
        slider(
                "options.video.display.peak",
                0,
                100,
                Math.round(Options.displayPeakNits / 100),
                y += 24,
                value -> Options.displayPeakNits = value * 100);
        var bloom =
                OptionInstance.createBoolean(
                        "options.video.display.bloom",
                        Options.bloomEnabled,
                        value -> {
                            Options.bloomEnabled = value;
                            apply();
                        });
        addRenderableWidget(bloom.createButton(minecraft.options, width / 2 - 150, y += 24, 300));
        slider(
                "options.video.display.bloom_strength",
                0,
                100,
                Math.round(Options.bloomStrength * 100),
                y += 24,
                value -> Options.bloomStrength = value / 100f);
        addRenderableWidget(
                Button.builder(Component.translatable("gui.done"), button -> onClose())
                        .bounds(width / 2 - 100, height - 28, 200, 20)
                        .build());
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(font, title, width / 2, 12, 0xffffff);
        graphics.drawCenteredString(
                font,
                Component.translatable(
                        "options.video.display.active", Options.nativeDisplayStatus()),
                width / 2,
                32,
                0xcccccc);
    }
}
