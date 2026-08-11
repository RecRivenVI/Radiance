package com.radiance.client.gui;

import com.radiance.client.pipeline.Pipeline;
import com.radiance.client.pipeline.config.AttributeConfig;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Edits the active pack's attribute through its normal rebuild and persistence path. */
public final class TranslucencySettingsScreen extends Screen {
    private static final String ATTRIBUTE =
            "render_pipeline.module.ray_tracing.attribute.coverage_mode";
    private final Screen parent;
    private final AttributeConfig attribute;
    private final String original;
    private String selected;
    private CompletableFuture<Void> rebuild;
    private boolean failed;

    public TranslucencySettingsScreen(Screen parent) {
        super(Component.translatable("options.video.translucency"));
        this.parent = parent;
        attribute =
                Pipeline.getRayTracingShaderPackAttributes().stream()
                        .filter(value -> ATTRIBUTE.equals(value.name))
                        .findFirst()
                        .orElse(null);
        original = attribute == null ? null : attribute.value;
        selected = original == null ? ATTRIBUTE + ".stochastic" : original;
    }

    @Override
    protected void init() {
        clearWidgets();
        if (rebuild != null) return;
        int x = width / 2 - 150;
        int y = height / 2 - 25;
        Button toggle =
                addRenderableWidget(
                        Button.builder(
                                        modeLabel(),
                                        button -> {
                                            selected =
                                                    ATTRIBUTE
                                                            + (selected.endsWith(".blend")
                                                                    ? ".stochastic"
                                                                    : ".blend");
                                            init();
                                        })
                                .bounds(x, y, 300, 20)
                                .build());
        toggle.active = attribute != null;
        Button apply =
                addRenderableWidget(
                        Button.builder(
                                        Component.translatable("options.video.features.apply"),
                                        button -> {
                                            attribute.value = selected;
                                            failed = false;
                                            rebuild = CompletableFuture.runAsync(Pipeline::build);
                                            init();
                                        })
                                .bounds(x, y + 30, 145, 20)
                                .build());
        apply.active = attribute != null && !java.util.Objects.equals(original, selected);
        addRenderableWidget(
                Button.builder(Component.translatable("gui.back"), button -> onClose())
                        .bounds(x + 155, y + 30, 145, 20)
                        .build());
    }

    private Component modeLabel() {
        if (attribute == null)
            return Component.translatable("options.video.translucency.unsupported");
        return Component.translatable(
                "options.video.translucency."
                        + (selected.endsWith(".blend") ? "blend" : "stochastic"));
    }

    @Override
    public void tick() {
        if (rebuild == null || !rebuild.isDone() || Pipeline.isNativeRebuildActive()) return;
        try {
            rebuild.join();
            minecraft.setScreen(parent);
        } catch (RuntimeException failure) {
            attribute.value = original;
            rebuild = null;
            failed = true;
            init();
        }
    }

    @Override
    public void onClose() {
        if (rebuild == null) minecraft.setScreen(parent);
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return rebuild == null;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(font, title, width / 2, 35, 0xffffffff);
        String key =
                rebuild != null
                        ? "render_pipeline_screen.rebuilding"
                        : failed
                                ? "options.video.features.failed"
                                : "options.video.translucency.description";
        graphics.drawWordWrap(
                font,
                Component.translatable(key),
                width / 2 - 150,
                height / 2 + 65,
                300,
                0xffcccccc);
    }
}
