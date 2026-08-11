package com.radiance.audit;

import com.radiance.client.render.WorldMeshSink;

import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexFormatElement;
import com.radiance.client.texture.TextureTracker;
import com.radiance.client.proxy.vulkan.TextureProxy;
import com.radiance.client.vertex.PBRVertexFormatElements;
import com.radiance.client.vertex.PBRVertexFormats;
import com.radiance.mixins.vulkan_render_integration.accessor.RenderTypeCompositeStateAccessor;
import com.radiance.mixins.vulkan_render_integration.accessor.RenderTypeShaderStateAccessor;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;

/**
 * Temporary diagnostics for world mesh submissions: records one line per unique layer/status with
 * the resolved texture identity, texture tracker metadata, UV range and the PT accept/drop result.
 */
public final class WorldMeshSubmissionProbe {
    private static final Set<String> LOGGED = ConcurrentHashMap.newKeySet();
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");

    private WorldMeshSubmissionProbe() {
    }

    public static void record(RenderType renderType, MeshData mesh,

        WorldMeshSink.Submission submission, String scopeLabel) {
        if (!"1".equals(com.radiance.audit.ExperimentAccess.getenv("RADIANCE_AUDIT_MESH_DETAILS"))) return;
        try {
            String key = renderType.toString() + '|' + submission.status();
            if (!LOGGED.add(key)) {
                return;
            }

            StringBuilder line = new StringBuilder();
            line.append('[').append(LocalTime.now().format(TIME)).append("] layer=")
                .append(renderType.name)
                .append(" scope=").append(scopeLabel)
                .append(" status=").append(submission.status())
                .append(" reason=").append(submission.reason());

            if (renderType instanceof RenderType.CompositeRenderType composite) {
                RenderTypeCompositeStateAccessor state =
                    (RenderTypeCompositeStateAccessor) (Object) composite.state;
                line.append(" output=").append(state.radiance$getOutputState())
                    .append(" transparency=").append(state.radiance$getTransparencyState());
                Supplier<ShaderInstance> shaderSupplier =
                    ((RenderTypeShaderStateAccessor) (Object) state.radiance$getShaderState())
                        .radiance$getShader().orElse(null);
                ShaderInstance shader = shaderSupplier == null ? null : shaderSupplier.get();
                line.append(" shader=").append(shader == null ? "null" : shader.getName());

                ResourceLocation texture =
                    composite.state.textureState.cutoutTexture().orElse(null);
                if (texture == null) {
                    line.append(" texture=none");
                } else {
                    int glid = Minecraft.getInstance().getTextureManager().getTexture(texture)
                        .getId();
                    TextureTracker.Texture metadata = TextureTracker.GLID2Texture.get(glid);
                    line.append(" texture=").append(texture).append(" glid=").append(glid)
                        .append(" texMeta=")
                        .append(metadata == null ? "untracked" : metadata.width() + "x"
                            + metadata.height() + "x" + metadata.channel() + " layers="
                            + metadata.maxLayer());
                    if (scopeLabel.startsWith("radiance/outliner/")) {
                        line.append(" auxiliary=[specular=").append(TextureTracker.GLID2SpecularGLID.get(glid))
                            .append(",normal=").append(TextureTracker.GLID2NormalGLID.get(glid))
                            .append(",flag=").append(TextureTracker.GLID2FlagGLID.get(glid)).append(']');
                        line.append(" readback=").append(dumpOutlinerTexture(glid, texture, metadata));
                    }
                }
            }

            MeshData.DrawState drawState = mesh.drawState();
            line.append(" format=").append(drawState.format())
                .append(" mode=").append(drawState.mode())
                .append(" verts=").append(drawState.vertexCount())
                .append(" idx=").append(drawState.indexCount())
                .append(" uv=").append(uvRange(mesh, drawState))
                .append(pbrSummary(mesh, drawState));

            appendLine(line.toString());
        } catch (Throwable ignored) {
        }
    }

    private static String uvRange(MeshData mesh, MeshData.DrawState drawState) {
        VertexFormat format = drawState.format();
        int offset = format.getOffset(format.equals(PBRVertexFormats.PBR_TRIANGLE)
            ? PBRVertexFormatElements.PBR_TEXTURE_UV : VertexFormatElement.UV0);
        if (offset < 0) {
            return "n/a";
        }
        int stride = format.getVertexSize();
        int count = Math.min(drawState.vertexCount(), 4096);
        if (stride <= 0 || count <= 0 || offset + 8 > stride) {
            return "n/a";
        }
        ByteBuffer buffer = mesh.vertexBuffer().duplicate().order(ByteOrder.nativeOrder());
        float minU = Float.POSITIVE_INFINITY;
        float minV = Float.POSITIVE_INFINITY;
        float maxU = Float.NEGATIVE_INFINITY;
        float maxV = Float.NEGATIVE_INFINITY;
        for (int i = 0; i < count; i++) {
            int base = i * stride + offset;
            if (base + 8 > buffer.limit()) {
                break;
            }
            float u = buffer.getFloat(base);
            float v = buffer.getFloat(base + 4);
            if (!Float.isFinite(u) || !Float.isFinite(v)) {
                continue;
            }
            minU = Math.min(minU, u);
            minV = Math.min(minV, v);
            maxU = Math.max(maxU, u);
            maxV = Math.max(maxV, v);
        }
        if (minU == Float.POSITIVE_INFINITY) {
            return "n/a";
        }
        return '[' + fmt(minU) + ',' + fmt(minV) + "]-[" + fmt(maxU) + ',' + fmt(maxV) + "] n="
            + count;
    }

    private static String dumpOutlinerTexture(int id, ResourceLocation location,
        TextureTracker.Texture metadata) {
        if (metadata == null || metadata.channel() != 4 || metadata.width() > 256
            || metadata.height() > 256) return "skipped";
        ByteBuffer pixels = org.lwjgl.system.MemoryUtil.memCalloc(metadata.width() * metadata.height() * 4);
        try {
            TextureProxy.downloadTexture(id, 0, metadata.width(), metadata.height(), 4,
                org.lwjgl.system.MemoryUtil.memAddress(pixels));
            java.awt.image.BufferedImage image = new java.awt.image.BufferedImage(
                metadata.width(), metadata.height(), java.awt.image.BufferedImage.TYPE_INT_ARGB);
            for (int y = 0; y < metadata.height(); y++) {
                for (int x = 0; x < metadata.width(); x++) {
                    int p = (y * metadata.width() + x) * 4;
                    int rgba = (pixels.get(p + 3) & 255) << 24 | (pixels.get(p) & 255) << 16
                        | (pixels.get(p + 1) & 255) << 8 | (pixels.get(p + 2) & 255);
                    image.setRGB(x, y, rgba);
                }
            }
            Path directory = Minecraft.getInstance().gameDirectory.toPath().resolve("radiance-outliner-readback");
            Files.createDirectories(directory);
            Path output = directory.resolve(System.currentTimeMillis() + "-" + id + "-"
                + location.toString().replaceAll("[^a-zA-Z0-9._-]", "_") + ".png");
            javax.imageio.ImageIO.write(image, "png", output.toFile());
            return output.getFileName().toString();
        } catch (Exception exception) {
            return "failed:" + exception.getClass().getSimpleName() + ':' + exception.getMessage();
        } finally {
            org.lwjgl.system.MemoryUtil.memFree(pixels);
        }
    }

    private static String pbrSummary(MeshData mesh, MeshData.DrawState state) {
        if (!state.format().equals(PBRVertexFormats.PBR_TRIANGLE)) return "";
        VertexFormat format = state.format();
        ByteBuffer data = mesh.vertexBuffer().duplicate().order(ByteOrder.nativeOrder());
        Set<Integer> textures = new java.util.TreeSet<>();
        Set<Integer> modes = new java.util.TreeSet<>();
        Set<Integer> useTexture = new java.util.TreeSet<>();
        float minAlpha = Float.POSITIVE_INFINITY, maxAlpha = Float.NEGATIVE_INFINITY;
        float maxEmission = 0;
        for (int i = 0; i < state.vertexCount(); i++) {
            int base = i * format.getVertexSize();
            textures.add(data.getInt(base + format.getOffset(PBRVertexFormatElements.PBR_TEXTURE_ID)));
            useTexture.add(data.getInt(base + format.getOffset(PBRVertexFormatElements.PBR_USE_TEXTURE)));
            modes.add(data.getInt(base + format.getOffset(PBRVertexFormatElements.PBR_POST_BASE) + 12));
            float alpha = data.getFloat(base + format.getOffset(PBRVertexFormatElements.PBR_COLOR_LAYER) + 12);
            minAlpha = Math.min(minAlpha, alpha);
            maxAlpha = Math.max(maxAlpha, alpha);
            maxEmission = Math.max(maxEmission,
                data.getFloat(base + format.getOffset(PBRVertexFormatElements.PBR_ALBEDO_EMISSION)));
        }
        return " pbrTextures=" + textures + " useTexture=" + useTexture + " alphaModes=" + modes
            + " vertexAlpha=[" + fmt(minAlpha) + "," + fmt(maxAlpha) + "] maxEmission=" + fmt(maxEmission);
    }

    private static String fmt(float value) {
        return String.format(java.util.Locale.ROOT, "%.3f", value);
    }

    private static void appendLine(String line) {
        try {
            Path path = Minecraft.getInstance().gameDirectory.toPath()
                .resolve("radiance-worldmesh-diag.log");
            Files.writeString(path, line + System.lineSeparator(), StandardOpenOption.CREATE,
                StandardOpenOption.APPEND);
        } catch (java.io.IOException ignored) {
        }
    }
}
