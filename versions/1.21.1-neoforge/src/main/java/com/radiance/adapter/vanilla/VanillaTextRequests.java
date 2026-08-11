package com.radiance.adapter.vanilla;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.radiance.client.vertex.*;
import com.radiance.mixin_related.SourceCompatibility;
import java.util.*;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import org.joml.Matrix4f;

/**
 * Source-owned text layout recipes. No final world vertices are generated to test an unchanged
 * layout.
 */
public final class VanillaTextRequests {
    private VanillaTextRequests() {}

    private record Slot(Object family, int ordinal) {}

    private record Character(int index, Style style, int codepoint) {}

    private record Parameters(
            Font font,
            int color,
            boolean shadow,
            Font.DisplayMode mode,
            int background,
            Integer outline) {}

    private record Vertex(float x, float y, float z, int r, int g, int b, int a, float u, float v) {
        void emit(com.mojang.blaze3d.vertex.PoseStack.Pose pose, VertexConsumer out, int light) {
            out.addVertex(pose.pose(), x, y, z).setColor(r, g, b, a).setUv(u, v).setLight(light);
        }
    }

    private static final class Run {
        final RenderType layer;
        final List<Vertex> vertices;

        Run(RenderType layer, List<Vertex> vertices) {
            this.layer = layer;
            this.vertices = vertices;
        }
    }

    private static final class Layout {
        long epoch = -1;
        Parameters parameters;
        List<Character> characters = List.of();
        List<Run> runs = List.of();

        boolean matches(FormattedCharSequence text, Parameters value) {
            if (epoch != com.radiance.client.texture.FontAtlasLifecycle.revision()
                    || !value.equals(parameters)) return false;
            int[] at = {0};
            boolean equal =
                    text.accept(
                            (index, style, cp) -> {
                                if (style.isObfuscated() || at[0] >= characters.size())
                                    return false;
                                var previous = characters.get(at[0]++);
                                return previous.index == index
                                        && previous.style.equals(style)
                                        && previous.codepoint == cp;
                            });
            return equal && at[0] == characters.size();
        }
    }

    public static void draw(
            Object owner,
            Object family,
            int ordinal,
            Font font,
            FormattedCharSequence text,
            float x,
            float y,
            int color,
            boolean shadow,
            Matrix4f pose,
            MultiBufferSource output,
            Font.DisplayMode mode,
            int background,
            int light) {
        if (!request(
                owner,
                family,
                ordinal,
                font,
                text,
                x,
                y,
                color,
                shadow,
                pose,
                output,
                mode,
                background,
                light,
                null))
            font.drawInBatch(text, x, y, color, shadow, pose, output, mode, background, light);
    }

    public static void outline(
            Object owner,
            Object family,
            int ordinal,
            Font font,
            FormattedCharSequence text,
            float x,
            float y,
            int color,
            int outline,
            Matrix4f pose,
            MultiBufferSource output,
            int light) {
        if (!request(
                owner,
                family,
                ordinal,
                font,
                text,
                x,
                y,
                color,
                false,
                pose,
                output,
                Font.DisplayMode.NORMAL,
                0,
                light,
                outline))
            font.drawInBatch8xOutline(text, x, y, color, outline, pose, output, light);
    }

    private static boolean request(
            Object owner,
            Object family,
            int ordinal,
            Font font,
            FormattedCharSequence text,
            float x,
            float y,
            int color,
            boolean shadow,
            Matrix4f pose,
            MultiBufferSource output,
            Font.DisplayMode mode,
            int background,
            int light,
            Integer outline) {
        light = VanillaMaterialAdapter.lightmap(light);
        if (!PartModelCapture.ENABLED
                || !(owner instanceof SourceBindings.Owner source)
                || !SourceCompatibility.producer(Font.class)
                || !SourceCompatibility.producer(
                        net.minecraft.client.gui.font.glyphs.BakedGlyph.class)
                || com.radiance.client.render.RasterPreviewScope.active()) return false;
        var layout = source.radiance$bindings().definition(new Slot(family, ordinal), Layout::new);
        var parameters = new Parameters(font, color, shadow, mode, background, outline);
        if (!layout.matches(text, parameters)) {
            var characters = new ArrayList<Character>();
            boolean[] animated = {false};
            text.accept(
                    (index, style, cp) -> {
                        animated[0] |= style.isObfuscated();
                        characters.add(new Character(index, style, cp));
                        return true;
                    });
            if (animated[0]) return false;
            var recorder = new Recorder();
            if (outline == null)
                font.drawInBatch(
                        text, 0, 0, color, shadow, new Matrix4f(), recorder, mode, background, 0);
            else font.drawInBatch8xOutline(text, 0, 0, color, outline, new Matrix4f(), recorder, 0);
            layout.characters = List.copyOf(characters);
            layout.parameters = parameters;
            layout.runs = recorder.runs();
            layout.epoch = com.radiance.client.texture.FontAtlasLifecycle.revision();
            com.radiance.api.audit.RenderAuditBridge.counter(
                    "SOURCE", "TEXT_LAYOUT_PREPARED", 1, "");
        } else
            com.radiance.api.audit.RenderAuditBridge.counter("SOURCE", "TEXT_LAYOUT_REUSED", 1, "");
        var transform = new Matrix4f(pose).translate(x, y, 0);
        // Resolve the actual consumer for each font page; requests may mix resident and transient
        // pages.
        var consumers = new ArrayList<VertexConsumer>(layout.runs.size());
        for (var run : layout.runs) consumers.add(output.getBuffer(run.layer));
        for (int i = 0; i < layout.runs.size(); i++) {
            var run = layout.runs.get(i);
            if (!GlyphLocalModel.layout(
                    run,
                    transform,
                    consumers.get(i),
                    light,
                    (p, out, l, c) -> run.vertices.forEach(v -> v.emit(p, out, l)))) {
                // A resource budget can require a transient replay of this local definition. The
                // original layout has already been evaluated; do not rerun it or duplicate prior
                // pages.
                var p = new com.mojang.blaze3d.vertex.PoseStack();
                p.last().pose().set(transform);
                var out = consumers.get(i);
                for (var vertex : run.vertices) vertex.emit(p.last(), out, light);
            }
        }
        return true;
    }

    private static final class Recorder implements MultiBufferSource {
        final Map<RenderType, Vertices> layers = new LinkedHashMap<>();

        public VertexConsumer getBuffer(RenderType layer) {
            if (layer.mode() != com.mojang.blaze3d.vertex.VertexFormat.Mode.QUADS)
                throw new IllegalArgumentException("Vanilla font emitted a non-quad layout");
            return layers.computeIfAbsent(layer, key -> new Vertices());
        }

        List<Run> runs() {
            var runs = new ArrayList<Run>();
            layers.forEach(
                    (layer, out) -> {
                        out.finish();
                        if (out.vertices.size() % 4 != 0)
                            throw new IllegalStateException("Incomplete font layout quad");
                        if (!out.vertices.isEmpty())
                            runs.add(new Run(layer, List.copyOf(out.vertices)));
                    });
            return List.copyOf(runs);
        }
    }

    private static final class Vertices implements VertexConsumer {
        final List<Vertex> vertices = new ArrayList<>();
        boolean started;
        float x, y, z, u, v;
        int r, g, b, a;

        void finish() {
            if (started) {
                vertices.add(new Vertex(x, y, z, r, g, b, a, u, v));
                started = false;
            }
        }

        public VertexConsumer addVertex(float x, float y, float z) {
            finish();
            this.x = x;
            this.y = y;
            this.z = z;
            started = true;
            return this;
        }

        public VertexConsumer setColor(int r, int g, int b, int a) {
            this.r = r;
            this.g = g;
            this.b = b;
            this.a = a;
            return this;
        }

        public VertexConsumer setUv(float u, float v) {
            this.u = u;
            this.v = v;
            return this;
        }

        public VertexConsumer setUv1(int u, int v) {
            throw new IllegalArgumentException("Vanilla text has no overlay field");
        }

        public VertexConsumer setUv2(int u, int v) {
            return this;
        }

        public VertexConsumer setNormal(float x, float y, float z) {
            throw new IllegalArgumentException("Vanilla text has no source normal field");
        }
    }
}
