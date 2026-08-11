package com.radiance.client.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.radiance.mixins.vulkan_render_integration.accessor.LineStateShardAccessor;
import com.radiance.mixins.vulkan_render_integration.accessor.RenderTypeCompositeStateAccessor;
import com.radiance.render.contract.LineContract;
import com.radiance.render.contract.LineContract.Basis3;
import com.radiance.render.contract.LineContract.LineStyle;
import com.radiance.render.contract.LineContract.SourceKey;
import com.radiance.render.contract.LineContract.WidthPolicy;
import java.nio.ByteBuffer;
import java.util.OptionalDouble;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;

/** The only game-state to line-style adapter. Native code receives values, not source names. */
public final class LineDrawAdapter {
    private static final LineContract.OpacityPolicy WORLD_OPACITY =
            Boolean.getBoolean("radiance.lines.sourceAlpha")
                    ? LineContract.OpacityPolicy.SOURCE_ALPHA
                    : LineContract.OpacityPolicy.OPAQUE;

    private LineDrawAdapter() {}

    public static LineContract.Material worldMaterial(
            int sourceAlphaMode, float emission, int visibility) {
        return WORLD_OPACITY.material(sourceAlphaMode, emission, visibility);
    }

    public static boolean isLine(VertexFormat.Mode mode) {
        return mode == VertexFormat.Mode.LINES
                || mode == VertexFormat.Mode.LINE_STRIP
                || mode == VertexFormat.Mode.DEBUG_LINES
                || mode == VertexFormat.Mode.DEBUG_LINE_STRIP;
    }

    public static LineContract.Encoding encoding(VertexFormat.Mode mode) {
        return switch (mode) {
            case LINES -> LineContract.Encoding.MINECRAFT_LINES;
            case LINE_STRIP -> LineContract.Encoding.MINECRAFT_LINE_STRIP;
            case DEBUG_LINES -> LineContract.Encoding.DEBUG_LINES;
            case DEBUG_LINE_STRIP -> LineContract.Encoding.DEBUG_LINE_STRIP;
            default -> throw new IllegalArgumentException("Not an explicit line request: " + mode);
        };
    }

    public static float declaredWidth(RenderType layer) {
        if (layer instanceof RenderType.CompositeRenderType composite) {
            var line =
                    ((RenderTypeCompositeStateAccessor) (Object) composite.state)
                            .radiance$getLineState();
            if (line != null) {
                OptionalDouble width = ((LineStateShardAccessor) (Object) line).radiance$getWidth();
                return width.isPresent() ? (float) width.getAsDouble() : 2.5f;
            }
        }
        return RenderSystem.getShaderLineWidth();
    }

    public static int alphaMode(RenderType layer) {
        return com.radiance.compatibility.vanilla.VanillaMaterialAdapter.drawBlendMode(layer);
    }

    public static LineStyle capture(
            RenderType layer, WidthPolicy policy, float emission, int visibility) {
        return capture(layer, policy, emission, visibility, MaterialFaces.captureSnapshot(layer));
    }

    public static LineStyle capture(
            RenderType layer,
            WidthPolicy policy,
            float emission,
            int visibility,
            com.radiance.render.contract.DrawStateSnapshot snapshot) {
        float declared = declaredWidth(layer);
        float actual = snapshot.lineWidth();
        // Vanilla's empty-width shard performs a window-dependent pixel expansion. Physical
        // PT thickness intentionally keeps its nominal value; an actual override remains live.
        float windowWidth = Minecraft.getInstance().getWindow().getWidth();
        float nominalExpanded = Math.max(2.5f, windowWidth / 1920.0f * 2.5f);
        boolean adaptive =
                layer instanceof RenderType.CompositeRenderType composite
                        && ((LineStateShardAccessor)
                                        (Object)
                                                ((RenderTypeCompositeStateAccessor)
                                                                (Object) composite.state)
                                                        .radiance$getLineState())
                                .radiance$getWidth()
                                .isEmpty();
        float width =
                adaptive && Math.abs(actual - nominalExpanded) < 1e-5 ? declared : (float) actual;
        var material = worldMaterial(alphaMode(layer), emission, visibility);
        return new LineStyle(encoding(layer.mode()), width, policy.thickness(width), material);
    }

    public static boolean write(
            ByteBuffer output,
            int offset,
            RenderType layer,
            SourceKey source,
            WidthPolicy policy,
            float emission,
            int visibility,
            float[] owner) {
        return write(output, offset, layer, source, policy, emission, visibility, owner, Float.NaN);
    }

    public static boolean write(
            ByteBuffer output,
            int offset,
            RenderType layer,
            SourceKey source,
            WidthPolicy policy,
            float emission,
            int visibility,
            float[] owner,
            float previousBatchWidth) {
        return write(
                output,
                offset,
                layer,
                source,
                policy,
                emission,
                visibility,
                owner,
                previousBatchWidth,
                MaterialFaces.captureSnapshot(layer));
    }

    public static boolean write(
            ByteBuffer output,
            int offset,
            RenderType layer,
            SourceKey source,
            WidthPolicy policy,
            float emission,
            int visibility,
            float[] owner,
            float previousBatchWidth,
            com.radiance.render.contract.DrawStateSnapshot snapshot) {
        LineStyle style = capture(layer, policy, emission, visibility, snapshot);
        Basis3 basis = Basis3.copyOf(owner);
        LineAudit.style(source, style, basis, previousBatchWidth);
        if (!style.valid() || !basis.finite() || Math.abs(basis.determinant()) <= 1e-12f) {
            RenderCaptureContract.reportDiscard(
                    "line request", "invalid line style or owner basis: " + source.label());
            return false;
        }
        LineContract.writeStyle(output, offset, source, style, basis);
        return true;
    }
}
