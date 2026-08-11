package com.radiance.client.proxy.world;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.radiance.client.RadianceClient;
import com.radiance.client.constant.Constants;
import com.radiance.client.vertex.StorageVertexConsumerProvider;
import java.io.IOException;
import java.io.InputStream;
import net.minecraft.client.Camera;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FastColor;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * Implements the cell-based cloud geometry used by upstream Radiance on versions where vanilla's
 * cloud renderer still draws a texture plane. Transparent texture pixels are discarded on the CPU,
 * so they can never turn into opaque black geometry in the Vulkan path.
 */
public final class CloudProxy {
    private static final ResourceLocation CLOUD_TEXTURE =
        ResourceLocation.withDefaultNamespace("textures/environment/clouds.png");

    /**
     * This layer deliberately has no texture. The cloud mask has already been converted to geometry;
     * keeping a cloud-named layer preserves Radiance's WORLD_CLOUD classification.
     */
    private static final RenderType RADIANCE_CLOUDS = RenderType.create(
        "radiance_clouds",
        DefaultVertexFormat.POSITION_COLOR_NORMAL,
        VertexFormat.Mode.QUADS,
        786432,
        false,
        false,
        RenderType.CompositeState.builder()
            .setShaderState(RenderType.RENDERTYPE_CLOUDS_SHADER)
            .setTransparencyState(RenderType.TRANSLUCENT_TRANSPARENCY)
            .setCullState(RenderType.NO_CULL)
            .setWriteMaskState(RenderType.COLOR_DEPTH_WRITE)
            .setOutputState(RenderType.CLOUDS_TARGET)
            .createCompositeState(false));

    private static StorageVertexConsumerProvider storage;
    private static EntityProxy.EntityRenderDataList renderData;
    private static CloudCells cells;
    private static ClientLevel previousLevel;
    private static int previousX = Integer.MIN_VALUE;
    private static int previousZ = Integer.MIN_VALUE;
    private static Vec3 previousColor = Vec3.ZERO;
    private static CloudStatus previousMode;
    private static ViewMode previousViewMode;
    private static volatile boolean textureDirty = true;
    private static long geometryRevision = 1;

    private CloudProxy() {
    }

    public static void queue(ClientLevel level, Camera camera, int ticks, float tickDelta) {
        Minecraft minecraft = Minecraft.getInstance();
        CloudStatus mode = minecraft.options.getCloudsType();
        float cloudHeight = level.effects().getCloudHeight() + 0.33F;
        if (mode == CloudStatus.OFF || Float.isNaN(cloudHeight)) {
            closeBuffers();
            return;
        }

        if (textureDirty || cells == null) {
            reloadCells(minecraft);
        }
        if (cells == null) {
            return;
        }

        Vec3 cameraPosition = camera.getPosition();
        float relativeBottom = cloudHeight - (float) cameraPosition.y;
        float relativeTop = relativeBottom + 4.0F;
        ViewMode viewMode = relativeTop < 0.0F
            ? ViewMode.ABOVE
            : relativeBottom > 0.0F ? ViewMode.BELOW : ViewMode.INSIDE;

        double cloudX = cameraPosition.x + ((float) ticks + tickDelta) * 0.03F;
        double cloudZ = cameraPosition.z + 3.96F;
        double width = cells.width * 12.0;
        double height = cells.height * 12.0;
        cloudX -= Mth.floor(cloudX / width) * width;
        cloudZ -= Mth.floor(cloudZ / height) * height;

        int cellX = Mth.floor(cloudX / 12.0);
        int cellZ = Mth.floor(cloudZ / 12.0);
        float offsetX = (float) (cloudX - cellX * 12.0F);
        float offsetZ = (float) (cloudZ - cellZ * 12.0F);
        Vec3 color = level.getCloudColor(tickDelta);
        boolean rebuild = level != previousLevel
            || cellX != previousX
            || cellZ != previousZ
            || mode != previousMode
            || viewMode != previousViewMode
            || previousColor.distanceToSqr(color) > 2.0E-4;

        if (rebuild) {
            rebuild(level, cellX, cellZ, color, mode, viewMode);
        }
        if (renderData == null || renderData.isEmpty()) {
            return;
        }

        double originX = cameraPosition.x - offsetX;
        double originZ = cameraPosition.z - offsetZ;
        for (EntityProxy.EntityRenderData data : renderData) {
            data.setX(originX);
            data.setY(cloudHeight);
            data.setZ(originZ);
        }
        boolean reused = EntityProxy.beginCachedCloud(geometryRevision, originX, cloudHeight, originZ);
        if (!reused) {
            try {
                EntityProxy.queueBuildWithoutClose(renderData);
            } catch (Throwable failure) {
                try { EntityProxy.endCachedCloud(false); }
                catch (Throwable cleanup) { failure.addSuppressed(cleanup); }
                throw failure;
            }
            EntityProxy.endCachedCloud(true);
        }
        if (com.radiance.api.audit.RenderAuditBridge.accepts("CHUNK_PERF"))
            com.radiance.api.audit.RenderAuditBridge.counter("CHUNK_PERF",
                reused ? "CLOUD_GEOMETRY_REUSED" : "CLOUD_GEOMETRY_BUILT", 1, "");
    }

    private static void reloadCells(Minecraft minecraft) {
        textureDirty = false;
        cells = null;
        closeBuffers();
        resetBuildKey();

        try (InputStream input = minecraft.getResourceManager().open(CLOUD_TEXTURE);
             NativeImage image = NativeImage.read(input)) {
            int width = image.getWidth();
            int height = image.getHeight();
            long[] packedCells = new long[width * height];
            for (int z = 0; z < height; z++) {
                for (int x = 0; x < width; x++) {
                    int nativeColor = image.getPixelRGBA(x, z);
                    if (isEmpty(nativeColor)) {
                        continue;
                    }

                    int argb = FastColor.ARGB32.color(
                        FastColor.ABGR32.alpha(nativeColor),
                        FastColor.ABGR32.red(nativeColor),
                        FastColor.ABGR32.green(nativeColor),
                        FastColor.ABGR32.blue(nativeColor));
                    long packed = (Integer.toUnsignedLong(argb) << 4)
                        | (isEmpty(image.getPixelRGBA(x, Math.floorMod(z - 1, height))) ? 8L : 0L)
                        | (isEmpty(image.getPixelRGBA(Math.floorMod(x + 1, width), z)) ? 4L : 0L)
                        | (isEmpty(image.getPixelRGBA(x, Math.floorMod(z + 1, height))) ? 2L : 0L)
                        | (isEmpty(image.getPixelRGBA(Math.floorMod(x - 1, width), z)) ? 1L : 0L);
                    packedCells[x + z * width] = packed;
                }
            }
            cells = new CloudCells(packedCells, width, height);
        } catch (IOException exception) {
            RadianceClient.LOGGER.error("Failed to load the cloud mask {}", CLOUD_TEXTURE,
                exception);
        }
    }

    private static boolean isEmpty(int nativeColor) {
        return FastColor.ABGR32.alpha(nativeColor) < 10;
    }

    private static void rebuild(ClientLevel level, int cellX, int cellZ, Vec3 color,
        CloudStatus mode, ViewMode viewMode) {
        closeBuffers();
        previousLevel = level;
        previousX = cellX;
        previousZ = cellZ;
        previousColor = color;
        previousMode = mode;
        previousViewMode = viewMode;

        storage = new StorageVertexConsumerProvider(786432);
        renderData = new EntityProxy.EntityRenderDataList();
        VertexConsumer builder = storage.getBuffer(RADIANCE_CLOUDS);
        buildCloudCells(builder, cellX, cellZ, color, mode == CloudStatus.FANCY, viewMode);
        EntityProxy.processWorldEntityRenderData(storage, System.identityHashCode("clouds"),
            0.0, 0.0, 0.0, Constants.RayTracingFlags.CLOUD, false, renderData);
    }

    private static void buildCloudCells(VertexConsumer builder, int centerX, int centerZ,
        Vec3 cloudColor, boolean fancy, ViewMode viewMode) {
        if (cells == null) {
            return;
        }

        for (int z = -32; z <= 32; z++) {
            for (int x = -32; x <= 32; x++) {
                int textureX = Math.floorMod(centerX + x, cells.width);
                int textureZ = Math.floorMod(centerZ + z, cells.height);
                long cell = cells.cells[textureX + textureZ * cells.width];
                if (cell == 0L) {
                    continue;
                }

                int textureColor = unpackColor(cell);
                int bottomColor = shadeColor(textureColor, cloudColor, 0.7F);
                int topColor = shadeColor(textureColor, cloudColor, 1.0F);
                int northSouthColor = shadeColor(textureColor, cloudColor, 0.9F);
                int eastWestColor = shadeColor(textureColor, cloudColor, 0.8F);
                if (fancy) {
                    buildCloudCellFancy(builder, viewMode, bottomColor, topColor,
                        northSouthColor, eastWestColor, x, z, cell);
                } else {
                    buildCloudCellFast(builder, topColor, x, z);
                }
            }
        }
    }

    private static int shadeColor(int textureColor, Vec3 cloudColor, float shade) {
        int alpha = Mth.clamp(Math.round(FastColor.ARGB32.alpha(textureColor) * 0.8F), 0, 255);
        int red = Mth.clamp(Math.round(FastColor.ARGB32.red(textureColor)
            * (float) cloudColor.x * shade), 0, 255);
        int green = Mth.clamp(Math.round(FastColor.ARGB32.green(textureColor)
            * (float) cloudColor.y * shade), 0, 255);
        int blue = Mth.clamp(Math.round(FastColor.ARGB32.blue(textureColor)
            * (float) cloudColor.z * shade), 0, 255);
        return FastColor.ARGB32.color(alpha, red, green, blue);
    }

    private static void buildCloudCellFast(VertexConsumer builder, int color, int x, int z) {
        float minX = x * 12.0F;
        float maxX = minX + 12.0F;
        float minZ = z * 12.0F;
        float maxZ = minZ + 12.0F;
        quad(builder, color, 0.0F, 1.0F, 0.0F,
            minX, 0.0F, minZ,
            minX, 0.0F, maxZ,
            maxX, 0.0F, maxZ,
            maxX, 0.0F, minZ);
    }

    private static void buildCloudCellFancy(VertexConsumer builder, ViewMode viewMode,
        int bottomColor, int topColor, int northSouthColor, int eastWestColor,
        int x, int z, long cell) {
        float minX = x * 12.0F;
        float maxX = minX + 12.0F;
        float minZ = z * 12.0F;
        float maxZ = minZ + 12.0F;

        if (viewMode != ViewMode.BELOW) {
            quad(builder, topColor, 0.0F, 1.0F, 0.0F,
                minX, 4.0F, minZ,
                minX, 4.0F, maxZ,
                maxX, 4.0F, maxZ,
                maxX, 4.0F, minZ);
        }
        if (viewMode != ViewMode.ABOVE) {
            quad(builder, bottomColor, 0.0F, -1.0F, 0.0F,
                maxX, 0.0F, minZ,
                maxX, 0.0F, maxZ,
                minX, 0.0F, maxZ,
                minX, 0.0F, minZ);
        }
        if (hasBorderNorth(cell) && z > 0) {
            quad(builder, eastWestColor, 0.0F, 0.0F, -1.0F,
                minX, 0.0F, minZ,
                minX, 4.0F, minZ,
                maxX, 4.0F, minZ,
                maxX, 0.0F, minZ);
        }
        if (hasBorderSouth(cell) && z < 0) {
            quad(builder, eastWestColor, 0.0F, 0.0F, 1.0F,
                maxX, 0.0F, maxZ,
                maxX, 4.0F, maxZ,
                minX, 4.0F, maxZ,
                minX, 0.0F, maxZ);
        }
        if (hasBorderWest(cell) && x > 0) {
            quad(builder, northSouthColor, -1.0F, 0.0F, 0.0F,
                minX, 0.0F, maxZ,
                minX, 4.0F, maxZ,
                minX, 4.0F, minZ,
                minX, 0.0F, minZ);
        }
        if (hasBorderEast(cell) && x < 0) {
            quad(builder, northSouthColor, 1.0F, 0.0F, 0.0F,
                maxX, 0.0F, minZ,
                maxX, 4.0F, minZ,
                maxX, 4.0F, maxZ,
                maxX, 0.0F, maxZ);
        }

        if (Math.abs(x) <= 1 && Math.abs(z) <= 1) {
            quad(builder, topColor, 0.0F, 1.0F, 0.0F,
                maxX, 4.0F, minZ,
                maxX, 4.0F, maxZ,
                minX, 4.0F, maxZ,
                minX, 4.0F, minZ);
            quad(builder, bottomColor, 0.0F, 1.0F, 0.0F,
                minX, 0.0F, minZ,
                minX, 0.0F, maxZ,
                maxX, 0.0F, maxZ,
                maxX, 0.0F, minZ);
            quad(builder, eastWestColor, 0.0F, 0.0F, 1.0F,
                maxX, 0.0F, minZ,
                maxX, 4.0F, minZ,
                minX, 4.0F, minZ,
                minX, 0.0F, minZ);
            quad(builder, eastWestColor, 0.0F, 0.0F, -1.0F,
                minX, 0.0F, maxZ,
                minX, 4.0F, maxZ,
                maxX, 4.0F, maxZ,
                maxX, 0.0F, maxZ);
            quad(builder, northSouthColor, 1.0F, 0.0F, 0.0F,
                minX, 0.0F, minZ,
                minX, 4.0F, minZ,
                minX, 4.0F, maxZ,
                minX, 0.0F, maxZ);
            quad(builder, northSouthColor, -1.0F, 0.0F, 0.0F,
                maxX, 0.0F, maxZ,
                maxX, 4.0F, maxZ,
                maxX, 4.0F, minZ,
                maxX, 0.0F, minZ);
        }
    }

    private static void quad(VertexConsumer builder, int color,
        float normalX, float normalY, float normalZ,
        float x1, float y1, float z1,
        float x2, float y2, float z2,
        float x3, float y3, float z3,
        float x4, float y4, float z4) {
        vertex(builder, x1, y1, z1, color, normalX, normalY, normalZ);
        vertex(builder, x2, y2, z2, color, normalX, normalY, normalZ);
        vertex(builder, x3, y3, z3, color, normalX, normalY, normalZ);
        vertex(builder, x4, y4, z4, color, normalX, normalY, normalZ);
    }

    private static void vertex(VertexConsumer builder, float x, float y, float z, int color,
        float normalX, float normalY, float normalZ) {
        builder.addVertex(x, y, z)
            .setNormal(normalX, normalY, normalZ)
            .setColor(FastColor.ARGB32.red(color), FastColor.ARGB32.green(color),
                FastColor.ARGB32.blue(color), FastColor.ARGB32.alpha(color));
    }

    private static int unpackColor(long packed) {
        return (int) (packed >>> 4);
    }

    private static boolean hasBorderNorth(long packed) {
        return (packed & 8L) != 0L;
    }

    private static boolean hasBorderEast(long packed) {
        return (packed & 4L) != 0L;
    }

    private static boolean hasBorderSouth(long packed) {
        return (packed & 2L) != 0L;
    }

    private static boolean hasBorderWest(long packed) {
        return (packed & 1L) != 0L;
    }

    public static void markTextureDirty() {
        textureDirty = true;
    }

    public static void close() {
        closeBuffers();
        cells = null;
        textureDirty = true;
        resetBuildKey();
    }

    private static void resetBuildKey() {
        previousLevel = null;
        previousX = Integer.MIN_VALUE;
        previousZ = Integer.MIN_VALUE;
        previousColor = Vec3.ZERO;
        previousMode = null;
        previousViewMode = null;
    }

    private static void closeBuffers() {
        geometryRevision = Math.incrementExact(geometryRevision);
        if (renderData != null) {
            for (EntityProxy.EntityRenderData data : renderData) {
                for (EntityProxy.EntityRenderLayer layer : data) {
                    MeshData buffer = layer.builtBuffer();
                    buffer.close();
                }
            }
        }
        if (storage != null) {
            storage.close();
        }
        storage = null;
        renderData = null;
    }

    private enum ViewMode {
        ABOVE,
        INSIDE,
        BELOW
    }

    private record CloudCells(long[] cells, int width, int height) {
    }
}
