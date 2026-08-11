package com.radiance.client.vertex;

import static com.radiance.client.vertex.PBRVertexFormatElements.PBR_ALBEDO_EMISSION;
import static com.radiance.client.vertex.PBRVertexFormatElements.PBR_COLOR_LAYER;
import static com.radiance.client.vertex.PBRVertexFormatElements.PBR_GLINT_TEXTURE;
import static com.radiance.client.vertex.PBRVertexFormatElements.PBR_GLINT_UV;
import static com.radiance.client.vertex.PBRVertexFormatElements.PBR_LIGHT_UV;
import static com.radiance.client.vertex.PBRVertexFormatElements.PBR_NORM;
import static com.radiance.client.vertex.PBRVertexFormatElements.PBR_OVERLAY_UV;
import static com.radiance.client.vertex.PBRVertexFormatElements.PBR_POS;
import static com.radiance.client.vertex.PBRVertexFormatElements.PBR_POST_BASE;
import static com.radiance.client.vertex.PBRVertexFormatElements.PBR_TEXTURE_ID;
import static com.radiance.client.vertex.PBRVertexFormatElements.PBR_TEXTURE_UV;
import static com.radiance.client.vertex.PBRVertexFormatElements.PBR_USE_COLOR_LAYER;
import static com.radiance.client.vertex.PBRVertexFormatElements.PBR_USE_GLINT;
import static com.radiance.client.vertex.PBRVertexFormatElements.PBR_USE_LIGHT;
import static com.radiance.client.vertex.PBRVertexFormatElements.PBR_USE_NORM;
import static com.radiance.client.vertex.PBRVertexFormatElements.PBR_USE_OVERLAY;
import static com.radiance.client.vertex.PBRVertexFormatElements.PBR_USE_TEXTURE;

import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexFormatElement;
import java.nio.ByteOrder;
import java.util.stream.Collectors;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.system.MemoryUtil;

public class PBRVertexConsumer implements VertexConsumer {

    private static final boolean LITTLE_ENDIAN = ByteOrder.nativeOrder() == ByteOrder.LITTLE_ENDIAN;
    private static final int ALPHA_MODE_OPAQUE = 0;
    private static final int ALPHA_MODE_CUTOUT = 1;
    private static final int ALPHA_MODE_TRANSMISSION = 2;
    public static final int ALPHA_MODE_CUTOUT_LOW = 9;
    private static final int ALPHA_MODE_COVERAGE = 10;
    private static final int ALPHA_MODE_ADDITIVE = 11;
    // Shared with MCVR's existing multiplicative crumbling hit-shader branch.
    private static final int ALPHA_MODE_CRUMBLING = 22;
    // Keep text modes outside the material alpha-mode range. These values are
    // packed alongside the regular alpha mode and are consumed by both the
    // post-text raster path and world text hit shaders.
    private static final int POST_TEXT_MODE_BACKGROUND = 12;
    private static final int POST_TEXT_MODE_INTENSITY = 13;
    private static final int POST_TEXT_MODE_RGBA = 14;
    private static final int POST_TEXT_MODE_BACKGROUND_SEE_THROUGH = 15;
    private static final int POST_TEXT_MODE_INTENSITY_SEE_THROUGH = 16;
    private static final int POST_TEXT_MODE_RGBA_SEE_THROUGH = 17;
    private static final int POST_TEXT_MODE_INTENSITY_POLYGON_OFFSET = 18;
    private static final int POST_TEXT_MODE_RGBA_POLYGON_OFFSET = 19;
    private static final float TEXT_POLYGON_OFFSET_EPSILON = 1.0e-4F;
    public static final int GLINT_MODE_NONE = 0;
    public static final int GLINT_MODE_ITEM = 1;
    public static final int GLINT_MODE_ENTITY = 2;

    static int glintModeForRenderTypeName(String name) {
        return name.startsWith("entity_glint") || name.equals("armor_entity_glint")
            ? GLINT_MODE_ENTITY
            : GLINT_MODE_ITEM;
    }

    private final ByteBufferBuilder allocator;
    private final VertexFormat format;
    private final VertexFormat.Mode drawMode;

    private final int vertexSizeByte;
    private final int writableMask;
    private final int requiredMask;
    private final int[] offsetsByElementId;
    private float defaultAlbedoEmission = 0.0F;
    private long vertexPointer = -1L;
    private int vertexCount = 0;
    private int currentMask = 0;
    private boolean building = true;
    private int textureID;
    private final int alphaMode;
    private final boolean blockTransmissionLayer;
    private final boolean textPolygonOffsetLayer;
    private float baseX = 0;
    private float baseY = 0;
    private float baseZ = 0;

    public PBRVertexConsumer(ByteBufferBuilder allocator, RenderType renderLayer) {
        this(allocator, VertexFormat.Mode.QUADS, PBRVertexFormats.PBR_TRIANGLE, renderLayer,
            getAlphaMode(renderLayer));
    }

    protected PBRVertexConsumer(ByteBufferBuilder allocator, RenderType renderLayer,
        int alphaMode) {
        this(allocator, VertexFormat.Mode.QUADS, PBRVertexFormats.PBR_TRIANGLE, renderLayer,
            alphaMode);
    }

    private PBRVertexConsumer(ByteBufferBuilder allocator, VertexFormat.Mode drawMode,
        VertexFormat format, RenderType renderLayer, int alphaMode) {
        this.allocator = allocator;
        this.drawMode = drawMode;
        this.format = format;

        this.vertexSizeByte = format.getVertexSize();
        this.writableMask = format.getElementsMask() & ~PBR_POS.mask();
        this.requiredMask = 0;
        this.offsetsByElementId = format.getOffsetsByElement();

        if (this.vertexSizeByte != 128) {
            throw new IllegalStateException(
                "PBR vertex stride must be 128, got " + this.vertexSizeByte);
        }
        if (!format.contains(PBR_POS)) {
            throw new IllegalArgumentException("PBR format must contain POSITION element");
        }

        if (renderLayer instanceof RenderType.CompositeRenderType) {
            ResourceLocation
                identifier =
                ((RenderType.CompositeRenderType) renderLayer).state.textureState.cutoutTexture()
                    .orElse(MissingTextureAtlasSprite.getLocation());
            textureID =
                Minecraft.getInstance()
                    .getTextureManager()
                    .getTexture(identifier)
                    .getId();
        }
        this.alphaMode = alphaMode;
        this.blockTransmissionLayer = renderLayer == Sheets.translucentCullBlockSheet()
            || renderLayer == Sheets.translucentItemSheet();
        String normalizedLayerName = normalizeTextLayerName(renderLayer.name);
        this.textPolygonOffsetLayer = normalizedLayerName.equals("text_polygon_offset")
            || normalizedLayerName.equals("text_intensity_polygon_offset");
    }

    private static void putInt(long ptr, int v) {
        if (LITTLE_ENDIAN) {
            MemoryUtil.memPutInt(ptr, v);
        } else {
            MemoryUtil.memPutShort(ptr, (short) (v & 0xFFFF));
            MemoryUtil.memPutShort(ptr + 2L, (short) ((v >>> 16) & 0xFFFF));
        }
    }

    public static int getAlphaMode(RenderType renderLayer) {
        if (!(renderLayer instanceof RenderType.CompositeRenderType multiPhase)) {
            return ALPHA_MODE_OPAQUE;
        }

        int postTextMode = getPostTextMode(multiPhase.name);
        if (postTextMode != ALPHA_MODE_OPAQUE) {
            return postTextMode;
        }

        if (RenderStateShard.CRUMBLING_TRANSPARENCY.equals(multiPhase.state.transparencyState)) {
            // Vanilla uses DST_COLOR / SRC_COLOR: the stage RGB multiplies the underlying
            // surface by twice its value. Coverage blending loses that destruction pattern.
            return ALPHA_MODE_CRUMBLING;
        }

        if (multiPhase.name.contains("solid")) {
            return ALPHA_MODE_OPAQUE;
        }

        if (multiPhase.name.contains("cutout")) {
            return ALPHA_MODE_CUTOUT;
        }

        if (RenderStateShard.NO_TRANSPARENCY.equals(multiPhase.state.transparencyState)) {
            return ALPHA_MODE_CUTOUT;
        }

        // The two vanilla block layers contain glass, water and similar dielectric media.
        // Other translucent render types use Minecraft's ordered alpha blending and must not
        // be reinterpreted as refractive glass by the path tracer.
        if (multiPhase.name.equals("translucent")
            || multiPhase.name.equals("translucent_moving_block")) {
            return ALPHA_MODE_TRANSMISSION;
        }

        if (RenderStateShard.ADDITIVE_TRANSPARENCY.equals(multiPhase.state.transparencyState)
            || RenderStateShard.LIGHTNING_TRANSPARENCY.equals(
            multiPhase.state.transparencyState)
            || RenderStateShard.GLINT_TRANSPARENCY.equals(multiPhase.state.transparencyState)) {
            return ALPHA_MODE_ADDITIVE;
        }

        return ALPHA_MODE_COVERAGE;
    }

    public static String normalizeTextLayerName(String layerName) {
        return switch (layerName) {
            case "neoforge_text" -> "text";
            case "neoforge_text_intensity" -> "text_intensity";
            case "neoforge_text_polygon_offset" -> "text_polygon_offset";
            case "neoforge_text_intensity_polygon_offset" -> "text_intensity_polygon_offset";
            case "neoforge_text_see_through" -> "text_see_through";
            default -> layerName;
        };
    }

    private static int getPostTextMode(String layerName) {
        return switch (normalizeTextLayerName(layerName)) {
            case "text_background" -> POST_TEXT_MODE_BACKGROUND;
            case "text_intensity" -> POST_TEXT_MODE_INTENSITY;
            case "text" -> POST_TEXT_MODE_RGBA;
            case "text_background_see_through" -> POST_TEXT_MODE_BACKGROUND_SEE_THROUGH;
            case "text_intensity_see_through" -> POST_TEXT_MODE_INTENSITY_SEE_THROUGH;
            case "text_see_through" -> POST_TEXT_MODE_RGBA_SEE_THROUGH;
            case "text_intensity_polygon_offset" -> POST_TEXT_MODE_INTENSITY_POLYGON_OFFSET;
            case "text_polygon_offset" -> POST_TEXT_MODE_RGBA_POLYGON_OFFSET;
            default -> ALPHA_MODE_OPAQUE;
        };
    }

    public VertexFormat getFormat() {
        return this.format;
    }

    public int getVertexCount() {
        return this.vertexCount;
    }

    public void setBase(float x, float y, float z) {
        this.baseX = x;
        this.baseY = y;
        this.baseZ = z;
    }

    public void setDefaultAlbedoEmission(float emission) {
        this.defaultAlbedoEmission = emission;
    }

    private void ensureBuilding() {
        if (!building) {
            throw new IllegalStateException("Not building!");
        }
    }

    @Nullable
    public MeshData endNullable() {
        ensureBuilding();
        endVertex();
        MeshData built = build();
        building = false;
        vertexPointer = -1L;
        return built;
    }

    public MeshData end() {
        MeshData built = endNullable();
        if (built == null) {
            throw new IllegalStateException("PBRBufferBuilder was empty");
        }
        return built;
    }

    @Nullable
    private MeshData build() {
        if (vertexCount == 0) {
            return null;
        }

        ByteBufferBuilder.Result buf = allocator.build();
        if (buf == null) {
            return null;
        }

        int indexCount = drawMode.indexCount(vertexCount);
        VertexFormat.IndexType indexType = VertexFormat.IndexType.least(vertexCount);
        return new MeshData(buf,
            new MeshData.DrawState(format, vertexCount, indexCount, drawMode, indexType));
    }

    private long beginVertex() {
        ensureBuilding();
        endVertex();

        vertexCount++;
        long ptr = allocator.reserve(vertexSizeByte);
        vertexPointer = ptr;
        MemoryUtil.memSet(ptr, 0, vertexSizeByte);

        int emissionOffset = this.offsetsByElementId[PBR_ALBEDO_EMISSION.id()];
        float initialEmission = Math.max(this.defaultAlbedoEmission,
            PBRMaterialContext.albedoEmission());
        if (emissionOffset >= 0 && initialEmission != 0.0F) {
            MemoryUtil.memPutFloat(ptr + emissionOffset, initialEmission);
        }

        if (this.textureID != 0) {
            int off = this.offsetsByElementId[PBR_TEXTURE_ID.id()];
            if (off >= 0) {
                putInt(ptr + off, this.textureID);
            }
        }

        int offBase = this.offsetsByElementId[PBR_POST_BASE.id()];
        if (offBase >= 0) {
            MemoryUtil.memPutFloat(ptr + offBase, baseX);
            MemoryUtil.memPutFloat(ptr + offBase + 4L, baseY);
            MemoryUtil.memPutFloat(ptr + offBase + 8L, baseZ);
            // Reuse the trailing padding word after postBase for alpha mode.
            putInt(ptr + offBase + 12L, effectiveAlphaMode());
        }

        return ptr;
    }

    private long beginVertex(int glintTextureID) {
        ensureBuilding();
        endVertex();

        vertexCount++;
        long ptr = allocator.reserve(vertexSizeByte);
        vertexPointer = ptr;
        MemoryUtil.memSet(ptr, 0, vertexSizeByte);

        int emissionOffset = this.offsetsByElementId[PBR_ALBEDO_EMISSION.id()];
        float initialEmission = Math.max(this.defaultAlbedoEmission,
            PBRMaterialContext.albedoEmission());
        if (emissionOffset >= 0 && initialEmission != 0.0F) {
            MemoryUtil.memPutFloat(ptr + emissionOffset, initialEmission);
        }

        if (this.textureID != 0) {
            int off = this.offsetsByElementId[PBR_TEXTURE_ID.id()];
            if (off >= 0) {
                putInt(ptr + off, this.textureID);
            }
        }

        int offBase = this.offsetsByElementId[PBR_POST_BASE.id()];
        if (offBase >= 0) {
            MemoryUtil.memPutFloat(ptr + offBase, baseX);
            MemoryUtil.memPutFloat(ptr + offBase + 4L, baseY);
            MemoryUtil.memPutFloat(ptr + offBase + 8L, baseZ);
            // Reuse the trailing padding word after postBase for alpha mode.
            putInt(ptr + offBase + 12L, effectiveAlphaMode());
        }

        if (glintTextureID != 0) {
            int off = this.offsetsByElementId[PBR_GLINT_TEXTURE.id()];
            if (off >= 0) {
                putInt(ptr + off, glintTextureID);
            }
        }

        return ptr;
    }

    private int effectiveAlphaMode() {
        return PBRMaterialContext.entityTransmissionActive()
            || blockTransmissionLayer && PBRMaterialContext.blockTransmissionActive()
            ? ALPHA_MODE_TRANSMISSION
            : alphaMode;
    }

    private long beginElement(VertexFormatElement element) {
        int mask = currentMask;
        int bit = element.mask();
        if ((mask & bit) == 0) {
            return -1L;
        }

        currentMask = mask & ~bit;

        long base = vertexPointer;
        if (base == -1L) {
            throw new IllegalStateException("Not currently building vertex");
        }

        int id = element.id();
        int off = offsetsByElementId[id];
        if (off < 0) {
            throw new IllegalStateException(
                "Element present in mask but not in format: " + element);
        }
        return base + off;
    }

    private void endVertex() {
        if (vertexCount == 0) {
            return;
        }

        int missing = currentMask & requiredMask;
        if (missing != 0) {
            String
                s =
                VertexFormatElement.elementsFromMask(currentMask)
                    .map(format::getElementName)
                    .collect(Collectors.joining(", "));
            throw new IllegalStateException("Missing elements in vertex: " + s);
        }
    }

    @Override
    public VertexConsumer addVertex(float x, float y, float z) {
        long base = beginVertex();
        currentMask = writableMask;

        int posOff = offsetsByElementId[PBR_POS.id()];
        long p = base + posOff;

        if (Float.isNaN(x) || Float.isNaN(y) || Float.isNaN(z)) {
            MemoryUtil.memPutFloat(p, 0);
            MemoryUtil.memPutFloat(p + 4L, 0);
            MemoryUtil.memPutFloat(p + 8L, 0);
        } else {
            MemoryUtil.memPutFloat(p, x);
            MemoryUtil.memPutFloat(p + 4L, y);
            MemoryUtil.memPutFloat(p + 8L, z);
        }

        return this;
    }

    /**
     * Vanilla's text polygon-offset RenderTypes pull glyphs slightly toward the
     * camera so they remain in front of the text background. World geometry has
     * no raster polygon-offset state, so preserve only this text-specific
     * layering contract in the captured vertex positions. The local +Z axis is
     * transformed by the same pose used by vanilla text rendering.
     */
    @Override
    public VertexConsumer addVertex(Matrix4f matrix4f, float x, float y, float z) {
        Vector3f position = matrix4f.transformPosition(x, y, z, new Vector3f());
        if (textPolygonOffsetLayer) {
            Vector3f normal = matrix4f.transformDirection(0.0F, 0.0F, 1.0F, new Vector3f());
            if (normal.lengthSquared() > 1.0e-12F) {
                normal.normalize().mul(TEXT_POLYGON_OFFSET_EPSILON);
                position.add(normal);
            }
        }
        return addVertex(position.x(), position.y(), position.z());
    }

    public VertexConsumer vertex(float x, float y, float z, int glintTextureID) {
        long base = beginVertex(glintTextureID);
        currentMask = writableMask;

        int posOff = offsetsByElementId[PBR_POS.id()];
        long p = base + posOff;

        if (Float.isNaN(x) || Float.isNaN(y) || Float.isNaN(z)) {
            MemoryUtil.memPutFloat(p, 0);
            MemoryUtil.memPutFloat(p + 4L, 0);
            MemoryUtil.memPutFloat(p + 8L, 0);
        } else {
            MemoryUtil.memPutFloat(p, x);
            MemoryUtil.memPutFloat(p + 4L, y);
            MemoryUtil.memPutFloat(p + 8L, z);
        }

        return this;
    }

    @Override
    public VertexConsumer setColor(int red, int green, int blue, int alpha) {
        return setColorLayer(red, green, blue, alpha, 1);
    }

    /**
     * Stores a color that is mixed over the sampled albedo instead of multiplied with it.
     * The alpha component is the mix factor and does not affect surface opacity.
     */
    public VertexConsumer setColorMix(int red, int green, int blue, int alpha) {
        return setColorLayer(red, green, blue, alpha, 2);
    }

    private VertexConsumer setColorLayer(int red, int green, int blue, int alpha, int mode) {
        long f = beginElement(PBR_USE_COLOR_LAYER);
        if (f != -1L) {
            putInt(f, mode);
        }

        long p = beginElement(PBR_COLOR_LAYER);
        if (p != -1L) {
            MemoryUtil.memPutFloat(p, red / 255.0f);
            MemoryUtil.memPutFloat(p + 4L, green / 255.0f);
            MemoryUtil.memPutFloat(p + 8L, blue / 255.0f);
            MemoryUtil.memPutFloat(p + 12L, alpha / 255.0f);
        }
        return this;
    }

    @Override
    public VertexConsumer setUv(float u, float v) {
        long f = beginElement(PBR_USE_TEXTURE);
        if (f != -1L) {
            putInt(f, 1);
        }

        long p = beginElement(PBR_TEXTURE_UV);
        if (p != -1L) {
            MemoryUtil.memPutFloat(p, u);
            MemoryUtil.memPutFloat(p + 4L, v);
        }
        return this;
    }

    @Override
    public VertexConsumer setUv1(int u, int v) {
        long f = beginElement(PBR_USE_OVERLAY);
        if (f != -1L) {
            putInt(f, 1);
        }

        long p = beginElement(PBR_OVERLAY_UV);
        if (p != -1L) {
            putInt(p, u);
            putInt(p + 4L, v);
        }
        return this;
    }

    @Override
    public VertexConsumer setUv2(int u, int v) {
        long f = beginElement(PBR_USE_LIGHT);
        if (f != -1L) {
            putInt(f, 1);
        }

        long p = beginElement(PBR_LIGHT_UV);
        if (p != -1L) {
            putInt(p, u);
            putInt(p + 4L, v);
        }
        return this;
    }

    @Override
    public VertexConsumer setNormal(float x, float y, float z) {
        long f = beginElement(PBR_USE_NORM);
        if (f != -1L) {
            putInt(f, 1);
        }

        long p = beginElement(PBR_NORM);
        if (p != -1L) {
            MemoryUtil.memPutFloat(p, x);
            MemoryUtil.memPutFloat(p + 4L, y);
            MemoryUtil.memPutFloat(p + 8L, z);
        }
        return this;
    }

    public VertexConsumer albedoEmission(float emission) {
        long p = beginElement(PBR_ALBEDO_EMISSION);
        if (p != -1L) {
            float emissionFloor = Math.max(this.defaultAlbedoEmission,
                PBRMaterialContext.albedoEmission());
            MemoryUtil.memPutFloat(p, Math.max(MemoryUtil.memGetFloat(p),
                Math.max(emission, emissionFloor)));
        }
        return this;
    }

    public static class GLint implements VertexConsumer {

        private final PBRVertexConsumer delegate;
        private final int glintMode;
        private int glintTextureID;

        public GLint(PBRVertexConsumer delegate, RenderType glintRenderLayer) {
            this.delegate = delegate;
            this.glintMode = glintMode(glintRenderLayer);
            if (glintRenderLayer instanceof RenderType.CompositeRenderType) {
                ResourceLocation
                    identifier =
                    ((RenderType.CompositeRenderType) glintRenderLayer).state.textureState.cutoutTexture()
                        .orElse(MissingTextureAtlasSprite.getLocation());
                glintTextureID =
                    Minecraft.getInstance()
                        .getTextureManager()
                        .getTexture(identifier)
                        .getId();
            }
        }

        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            delegate.vertex(x, y, z, this.glintTextureID);
            return this;
        }

        @Override
        public VertexConsumer setColor(int red, int green, int blue, int alpha) {
            delegate.setColor(red, green, blue, alpha);
            return this;
        }

        @Override
        public VertexConsumer setUv(float u, float v) {
            delegate.setUv(u, v);

            long f = delegate.beginElement(PBR_USE_GLINT);
            if (f != -1L) {
                putInt(f, this.glintMode);
            }

            long p = delegate.beginElement(PBR_GLINT_UV);
            if (p != -1L) {
                MemoryUtil.memPutFloat(p, u);
                MemoryUtil.memPutFloat(p + 4L, v);
            }
            return this;
        }

        @Override
        public VertexConsumer setUv1(int u, int v) {
            delegate.setUv1(u, v);
            return this;
        }

        @Override
        public VertexConsumer setUv2(int u, int v) {
            delegate.setUv2(u, v);
            return this;
        }

        @Override
        public VertexConsumer setNormal(float x, float y, float z) {
            delegate.setNormal(x, y, z);
            return this;
        }

        private static int glintMode(RenderType renderLayer) {
            return glintModeForRenderTypeName(renderLayer.name);
        }
    }

    public static class GLintOverlay implements VertexConsumer {

        private final PBRVertexConsumer delegate;
        private final Matrix4f inverseTextureMatrix;
        private final Matrix3f inverseNormalMatrix;
        private final float textureScale;
        private final Vector3f normal = new Vector3f();
        private final Vector3f pos = new Vector3f();
        private int glintTextureID;
        private float x;
        private float y;
        private float z;

        public GLintOverlay(PBRVertexConsumer delegate, RenderType glintRenderLayer,
            PoseStack.Pose matrix, float textureScale) {
            this.delegate = delegate;
            if (glintRenderLayer instanceof RenderType.CompositeRenderType) {
                ResourceLocation
                    identifier =
                    ((RenderType.CompositeRenderType) glintRenderLayer).state.textureState.cutoutTexture()
                        .orElse(MissingTextureAtlasSprite.getLocation());
                glintTextureID =
                    Minecraft.getInstance()
                        .getTextureManager()
                        .getTexture(identifier)
                        .getId();
            }

            this.inverseTextureMatrix = new Matrix4f(matrix.pose()).invert();
            this.inverseNormalMatrix = new Matrix3f(matrix.normal()).invert();
            this.textureScale = textureScale;
        }

        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            this.x = x;
            this.y = y;
            this.z = z;
            delegate.vertex(x, y, z, this.glintTextureID);
            return this;
        }

        @Override
        public VertexConsumer setColor(int red, int green, int blue, int alpha) {
            delegate.setColor(red, green, blue, alpha);
            return this;
        }

        @Override
        public VertexConsumer setUv(float u, float v) {
            delegate.setUv(u, v);
            return this;
        }

        @Override
        public VertexConsumer setUv1(int u, int v) {
            delegate.setUv1(u, v);
            return this;
        }

        @Override
        public VertexConsumer setUv2(int u, int v) {
            delegate.setUv2(u, v);
            return this;
        }

        @Override
        public VertexConsumer setNormal(float x, float y, float z) {
            delegate.setNormal(x, y, z);
            Vector3f vector3f = this.inverseNormalMatrix.transform(x, y, z, this.pos);
            Direction direction = Direction.getNearest(vector3f.x(), vector3f.y(), vector3f.z());
            Vector3f vector3f2 = this.inverseTextureMatrix.transformPosition(this.x, this.y, this.z,
                this.normal);
            vector3f2.rotateY((float) Math.PI);
            vector3f2.rotateX((float) (-Math.PI / 2));
            vector3f2.rotate(direction.getRotation());

            long f = delegate.beginElement(PBR_USE_GLINT);
            if (f != -1L) {
                putInt(f, GLINT_MODE_ITEM);
            }

            long p = delegate.beginElement(PBR_GLINT_UV);
            if (p != -1L) {
                MemoryUtil.memPutFloat(p, -vector3f2.x() * this.textureScale);
                MemoryUtil.memPutFloat(p + 4L, -vector3f2.y() * this.textureScale);
            }
            return this;
        }
    }
}
