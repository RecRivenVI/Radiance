package com.radiance.client.gui;

import com.radiance.client.pipeline.Pipeline;
import com.radiance.client.pipeline.Presets;
import com.radiance.client.pipeline.RenderFeaturePlan;
import com.radiance.client.pipeline.RenderFeaturePlan.Denoising;
import com.radiance.client.pipeline.RenderFeaturePlan.Reconstruction;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.util.concurrent.CompletableFuture;

public final class RenderFeaturesScreen extends Screen {
    private final Screen parent;
    private Denoising denoising;
    private Reconstruction reconstruction;
    private CompletableFuture<Void> rebuild;
    private String error;

    public RenderFeaturesScreen(Screen parent) {
        super(Component.translatable("options.video.features"));
        this.parent=parent;
        var preset=Pipeline.getActivePreset();
        denoising=Presets.RT_DLSSRR.key.equals(preset)?Denoising.RR:Denoising.NRD;
        reconstruction=Presets.RT_NRD_FSR.key.equals(preset)?Reconstruction.FSR:
            Presets.RT_NRD_XESS.key.equals(preset)?Reconstruction.XESS:
            Presets.RT_NRD.key.equals(preset)?Reconstruction.NATIVE:Reconstruction.DLSS;
        if (denoising==Denoising.RR) for (var module:Pipeline.INSTANCE.getModules())
            if ("render_pipeline.module.dlss.name".equals(module.name)) for (var attr:module.attributeConfigs)
                if ("render_pipeline.module.dlss.attribute.mode".equals(attr.name) && attr.value.endsWith(".dlaa"))
                    reconstruction=Reconstruction.NATIVE;
    }
    @Override protected void init() {
        clearWidgets();
        if (rebuild!=null) return;
        int x=width/2-150, y=height/2-55;
        addRenderableWidget(Button.builder(Component.translatable("options.video.features.denoising",denoising.name()),b->{
            denoising=denoising==Denoising.NRD?Denoising.RR:Denoising.NRD; init();
        }).bounds(x,y,300,20).build());
        addRenderableWidget(Button.builder(Component.translatable("options.video.features.reconstruction",
            Component.translatable("options.video.features."+reconstruction.name().toLowerCase(java.util.Locale.ROOT))),b->{
            reconstruction=Reconstruction.values()[(reconstruction.ordinal()+1)%Reconstruction.values().length]; init();
        }).bounds(x,y+25,300,20).build());
        var plan=new RenderFeaturePlan(denoising,reconstruction);
        var apply=addRenderableWidget(Button.builder(Component.translatable("options.video.features.apply"),b->{
            plan.assemble();
            rebuild=CompletableFuture.runAsync(Pipeline::build); init();
        }).bounds(x,y+60,145,20).build());
        apply.active=plan.available();
        error=apply.active?null:"options.video.features.unavailable";
        addRenderableWidget(Button.builder(Component.translatable("gui.back"),b->onClose()).bounds(x+155,y+60,145,20).build());
    }
    @Override public void tick() {
        if (rebuild==null || !rebuild.isDone() || Pipeline.isNativeRebuildActive()) return;
        try { rebuild.join(); minecraft.setScreen(parent); }
        catch (RuntimeException failure) { rebuild=null; init(); error="options.video.features.failed"; }
    }
    @Override public void onClose() { if (rebuild==null) minecraft.setScreen(parent); }
    @Override public boolean shouldCloseOnEsc() { return rebuild==null; }
    @Override public void render(GuiGraphics graphics,int mouseX,int mouseY,float partialTick) {
        super.render(graphics,mouseX,mouseY,partialTick);
        graphics.drawCenteredString(font,title,width/2,35,0xffffffff);
        if (rebuild!=null) graphics.drawCenteredString(font,Component.translatable("render_pipeline_screen.rebuilding"),width/2,height/2,0xffffffff);
        else if (error!=null) graphics.drawCenteredString(font,Component.translatable(error),width/2,height/2+55,0xffffbb66);
    }
}
