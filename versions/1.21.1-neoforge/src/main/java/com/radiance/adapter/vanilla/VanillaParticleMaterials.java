package com.radiance.adapter.vanilla;

import com.radiance.render.contract.RenderAbi;
import com.radiance.render.contract.SourceAlpha;
import net.minecraft.client.particle.ParticleRenderType;

/** The original ParticleRenderType blend, independent of its capture carrier RenderType. */
public final class VanillaParticleMaterials {
    private VanillaParticleMaterials() {}

    public static com.radiance.render.contract.DrawStateSnapshot captureState(
            net.minecraft.client.renderer.RenderType carrier) {
        // None of the four original sheet begin callbacks modifies cull state.
        int faces = com.radiance.client.render.MaterialFaces.current();
        var state = com.radiance.client.render.MaterialFaces.captureSnapshot(carrier);
        return new com.radiance.render.contract.DrawStateSnapshot(
                faces, state.lineWidth(), state.red(), state.green(), state.blue(), state.alpha());
    }

    public static int alphaMode(ParticleRenderType type) {
        if (type == ParticleRenderType.TERRAIN_SHEET
                || type == ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT)
            return SourceAlpha.encode(RenderAbi.ALPHA_COVERAGE, RenderAbi.SOURCE_ALPHA_POST_LOW);
        if (type == ParticleRenderType.PARTICLE_SHEET_OPAQUE
                || type == ParticleRenderType.PARTICLE_SHEET_LIT) return RenderAbi.ALPHA_CUTOUT_LOW;
        throw new IllegalArgumentException(
                "Buffered particle contract requires a registered source sheet");
    }
}
