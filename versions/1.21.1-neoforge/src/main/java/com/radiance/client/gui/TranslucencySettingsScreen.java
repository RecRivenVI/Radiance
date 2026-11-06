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
    private static final String OPTICAL_GUIDES =
            "render_pipeline.module.ray_tracing.attribute.optical_guides";
    private final Screen parent;
    private final AttributeConfig attribute;
    private final String original;
    private String selected;
    private final AttributeConfig opticalAttribute;
    private final String originalGuides;
    private String selectedGuides;
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
        opticalAttribute =
                Pipeline.getRayTracingShaderPackAttributes().stream()
                        .filter(value -> OPTICAL_GUIDES.equals(value.name))
                        .findFirst()
                        .orElse(null);
        originalGuides = opticalAttribute == null ? null : opticalAttribute.value;
        selectedGuides = originalGuides == null ? "render_pipeline.false" : originalGuides;
    }

    @Override
    protected void init() {
        clearWidgets();
        if (rebuild != null) return;
        int x = width / 2 - 150;
        int y = Math.max(55, height / 2 - 45);
        Button toggle =
                addRenderableWidget(
                        Button.builder(
                                        modeLabel(),
                                        button -> {
                                            String[] choices =
                                                    attribute
                                                            .type
                                                            .substring("enum:".length())
                                                            .split("-");
                                            int index =
                                                    java.util.Arrays.asList(choices)
                                                            .indexOf(selected);
                                            selected = choices[(index + 1) % choices.length];
                                            init();
                                        })
                                .bounds(x, y, 300, 20)
                                .build());
        toggle.active = attribute != null;
        Button guides =
                addRenderableWidget(
                        Button.builder(
                                        opticalAttribute == null
                                                ? Component.translatable(
                                                        "options.video.translucency.guides.unsupported")
                                                : Component.translatable(
                                                        "options.video.translucency.guides",
                                                        Component.translatable(
                                                                selectedGuides.endsWith(".true")
                                                                        ? "options.video.translucency.guides.on"
                                                                        : "options.video.translucency.guides.off")),
                                        button -> {
                                            selectedGuides =
                                                    selectedGuides.endsWith(".true")
                                                            ? "render_pipeline.false"
                                                            : "render_pipeline.true";
                                            init();
                                        })
                                .bounds(x, y + 25, 300, 20)
                                .build());
        guides.active = opticalAttribute != null;
        Button apply =
                addRenderableWidget(
                        Button.builder(
                                        Component.translatable("options.video.features.apply"),
                                        button -> {
                                            if (attribute != null) attribute.value = selected;
                                            if (opticalAttribute != null)
                                                opticalAttribute.value = selectedGuides;
                                            failed = false;
                                            rebuild = CompletableFuture.runAsync(Pipeline::build);
                                            init();
                                        })
                                .bounds(x, y + 55, 145, 20)
                                .build());
        apply.active =
                (attribute != null && !java.util.Objects.equals(original, selected))
                        || (opticalAttribute != null
                                && !java.util.Objects.equals(originalGuides, selectedGuides));
        addRenderableWidget(
                Button.builder(Component.translatable("gui.back"), button -> onClose())
                        .bounds(x + 155, y + 55, 145, 20)
                        .build());
    }

    private Component modeLabel() {
        if (attribute == null)
            return Component.translatable("options.video.translucency.unsupported");
        return Component.translatable(
                "options.video.translucency."
                        + (selected.endsWith(".layered")
                                ? "layered"
                                : selected.endsWith(".blend") ? "blend" : "stochastic"));
    }

    @Override
    public void tick() {
        if (rebuild == null || !rebuild.isDone() || Pipeline.isNativeRebuildActive()) return;
        try {
            rebuild.join();
            minecraft.setScreen(parent);
        } catch (RuntimeException failure) {
            if (attribute != null) attribute.value = original;
            if (opticalAttribute != null) opticalAttribute.value = originalGuides;
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
                Math.max(55, height / 2 - 45) + 85,
                300,
                0xffcccccc);
    }
}
