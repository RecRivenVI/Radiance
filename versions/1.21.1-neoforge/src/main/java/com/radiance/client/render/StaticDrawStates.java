package com.radiance.client.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.radiance.render.contract.DrawStateSnapshot;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.Sheets;

/** State interpretation for the actual vanilla factories used by the first takeover sources. */
public final class StaticDrawStates {
    /** Set by reviewed vanilla factories, not inferred from a RenderType name or texture. */
    public interface SourceAccess {
        void radiance$declareState(DrawStateSnapshot.UvTransform uv);

        DrawStateSnapshot.UvTransform radiance$declaredState();
    }

    public static void declare(RenderType layer, DrawStateSnapshot.UvTransform uv) {
        ((SourceAccess) layer).radiance$declareState(uv);
    }

    private static final Set<RenderType> PAINTINGS =
            Collections.newSetFromMap(new IdentityHashMap<>());
    private static final java.util.Map<
                    RenderType, com.radiance.render.contract.TakeoverSubmission.Program>
            PROGRAMS = new IdentityHashMap<>();
    private static long nextProgram;

    private StaticDrawStates() {}

    public static com.radiance.render.contract.TakeoverSubmission.Program program(
            RenderType layer) {
        RenderSystem.assertOnRenderThread();
        var program = PROGRAMS.get(layer);
        if (program == null) {
            program =
                    new com.radiance.render.contract.TakeoverSubmission.Program(
                            Math.incrementExact(nextProgram), 1);
            nextProgram = program.identity();
            if (PROGRAMS.size() < 4096) PROGRAMS.put(layer, program);
        }
        return program;
    }

    public static void invalidatePrograms() {
        PROGRAMS.clear();
    }

    public static RenderType painting(RenderType layer) {
        PAINTINGS.add(layer);
        return layer;
    }

    public static DrawStateSnapshot capture(RenderType layer) {
        if (!com.radiance.mixin_related.SourceCompatibility.compatible(
                com.radiance.mixin_related.SourceCompatibility.Source.STATIC_STATE)) return null;
        RenderSystem.assertOnRenderThread();
        var declared = ((SourceAccess) layer).radiance$declaredState();
        if (declared == null
                && !PAINTINGS.contains(layer)
                && layer != Sheets.solidBlockSheet()
                && layer != Sheets.cutoutBlockSheet()
                && layer != Sheets.translucentItemSheet()
                && layer != Sheets.translucentCullBlockSheet()) return null;
        // CULL's setup is a no-op (only NO_CULL changes the enable). Cull enable/mode,
        // winding and shader colour are inherited globals and must still be sampled now.
        var colour = RenderSystem.getShaderColor();
        int faces = MaterialFaces.current();
        if (declared != null && layer instanceof RenderType.CompositeRenderType composite) {
            if (composite.state.cullState == net.minecraft.client.renderer.RenderStateShard.NO_CULL)
                faces = MaterialFaces.withEnabled(false);
            else if (composite.state.cullState
                    != net.minecraft.client.renderer.RenderStateShard.CULL) return null;
        }
        return new DrawStateSnapshot(
                faces,
                RenderSystem.getShaderLineWidth(),
                colour[0],
                colour[1],
                colour[2],
                colour[3],
                declared == null ? DrawStateSnapshot.UvTransform.IDENTITY : declared);
    }
}
