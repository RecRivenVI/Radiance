package com.radiance.client.pipeline;

/** User choices are independent; RR fuses denoising and reconstruction in one pass. */
public record RenderFeaturePlan(Denoising denoising, Reconstruction reconstruction) {
    public enum Denoising { NRD, RR }
    public enum Reconstruction { NATIVE, DLSS, FSR, XESS }

    public Presets preset() {
        if (denoising == Denoising.RR) {
            if (reconstruction == Reconstruction.FSR || reconstruction == Reconstruction.XESS)
                throw new IllegalArgumentException("RR followed by an external temporal upscaler is not supported");
            return Presets.RT_DLSSRR;
        }
        return switch (reconstruction) {
            case NATIVE -> Presets.RT_NRD;
            case DLSS -> Presets.RT_NRD_DLSS;
            case FSR -> Presets.RT_NRD_FSR;
            case XESS -> Presets.RT_NRD_XESS;
        };
    }

    public boolean available() {
        try { return Pipeline.isPresetAvailable(preset().key); }
        catch (IllegalArgumentException unsupported) { return false; }
    }

    public void assemble() {
        if (!available()) throw new IllegalStateException("Requested reconstruction combination is unavailable");
        Pipeline.switchToPresetMode(preset().key, false);
        if (denoising == Denoising.RR) {
            for (Module module : Pipeline.INSTANCE.getModules()) {
                if (!"render_pipeline.module.dlss.name".equals(module.name)) continue;
                for (var attribute : module.attributeConfigs) {
                    if (!"render_pipeline.module.dlss.attribute.mode".equals(attribute.name)) continue;
                    if (reconstruction == Reconstruction.NATIVE)
                        attribute.value = "render_pipeline.module.dlss.attribute.mode.dlaa";
                    else if (attribute.value.endsWith(".dlaa"))
                        attribute.value = "render_pipeline.module.dlss.attribute.mode.quality";
                }
            }
        }
    }
}
