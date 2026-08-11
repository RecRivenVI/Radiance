package com.radiance.client.texture;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.platform.TextureUtil;
import com.radiance.client.proxy.vulkan.TextureProxy;
import com.radiance.mixin_related.extensions.vanilla_resource_tracker.INativeImageExt;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;
import net.minecraft.client.renderer.texture.atlas.SpriteSource;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import org.lwjgl.system.MemoryUtil;

public enum AuxiliaryTextures {
    SPECULAR("specular", "_s", (identifier, source) -> {
        String namespace = identifier.getNamespace();
        String path = identifier.getPath();
        String[] pathComponents = path.split("/");
        String[] fileNameComponents = pathComponents[pathComponents.length - 1].split("\\.");
        String specularFileName = String.join("",
            new String[]{fileNameComponents[0], "_s.", fileNameComponents[1]});

        pathComponents[pathComponents.length - 1] = specularFileName;
        String specularPath = String.join("/", pathComponents);
        ResourceLocation specularIdentifier = ResourceLocation.fromNamespaceAndPath(namespace, specularPath);
        return List.of(specularIdentifier);
    }, INativeImageExt::radiance$getSpecularNativeImage,
        INativeImageExt::radiance$setSpecularNativeImage, source -> 0,
        TextureTracker.GLID2SpecularGLID),
    NORMAL("normal", "_n", (identifier, source) -> {
        String namespace = identifier.getNamespace();
        String path = identifier.getPath();
        String[] pathComponents = path.split("/");
        String[] fileNameComponents = pathComponents[pathComponents.length - 1].split("\\.");
        String normalFileName = String.join("",
            new String[]{fileNameComponents[0], "_n.", fileNameComponents[1]});

        pathComponents[pathComponents.length - 1] = normalFileName;
        String normalPath = String.join("/", pathComponents);
        ResourceLocation normalIdentifier = ResourceLocation.fromNamespaceAndPath(namespace, normalPath);
        return List.of(normalIdentifier);
    }, INativeImageExt::radiance$getNormalNativeImage,
        INativeImageExt::radiance$setNormalNativeImage,
        source -> source.format().hasAlpha() ? 255 << source.format().alphaOffset() : 0,
        TextureTracker.GLID2NormalGLID),
    FLAG(
        "flag", "_f", (identifier, source) -> {
        String namespace = identifier.getNamespace();
        String path = identifier.getPath();
        String[] pathComponents = path.split("/");
        String[] fileNameComponents = pathComponents[pathComponents.length - 1].split("\\.");
        String flagFileName = String.join("",
            new String[]{fileNameComponents[0], "_f.", fileNameComponents[1]});

        pathComponents[pathComponents.length - 1] = flagFileName;
        String flagPath = String.join("/", pathComponents)
            .replace("textures/", "textures/flag/");
        ResourceLocation flagIdentifier = ResourceLocation.fromNamespaceAndPath(namespace, flagPath);
        return List.of(flagIdentifier);
    }, INativeImageExt::radiance$getFlagNativeImage,
        INativeImageExt::radiance$setFlagNativeImage, source -> 0,
        TextureTracker.GLID2FlagGLID);

    private static final List<AuxiliaryTextures> ALL_TEXTURES = Collections.unmodifiableList(
        Arrays.stream(values()).collect(Collectors.toList()));
    private static final Object DECODED_IMAGE_CACHE_LOCK = new Object();
    private static final Map<CacheKey, CacheEntry> DECODED_IMAGE_CACHE = new ConcurrentHashMap<>();
    private final String suffix;
    private final IdentifierCandidateProvider identifierCandidateProvider;
    private final Getter getter;
    private final Setter setter;
    private final DefaultValueProvider defaultValueProvider;
    private final String name;
    private final Map<Integer, Integer> GLIDMapping;

    AuxiliaryTextures(String name, String suffix,
        IdentifierCandidateProvider identifierCandidateProvider, Getter getter, Setter setter,
        DefaultValueProvider defaultValueProvider, Map<Integer, Integer> GLIDMapping) {
        this.suffix = suffix;
        this.identifierCandidateProvider = identifierCandidateProvider;
        this.getter = getter;
        this.setter = setter;
        this.defaultValueProvider = defaultValueProvider;
        this.name = name;
        this.GLIDMapping = GLIDMapping;
    }

    public static boolean isAuxiliaryTexture(ResourceLocation identifier) {
        if (identifier == null) {
            return false;
        }

        String path = identifier.getPath();
        int dotIndex = path.lastIndexOf('.');
        String baseName = (dotIndex != -1) ? path.substring(0, dotIndex) : path;

        return ALL_TEXTURES.stream().anyMatch(texture -> texture.matchesSuffix(baseName));
    }

    public static boolean shouldSkipAtlasSprite(ResourceManager resourceManager,
        ResourceLocation spriteId) {
        String spritePath = spriteId.getPath();
        for (AuxiliaryTextures auxiliaryTexture : ALL_TEXTURES) {
            if (!auxiliaryTexture.matchesSuffix(spritePath)) {
                continue;
            }

            ResourceLocation baseSpriteId = auxiliaryTexture.toBaseSpriteId(spriteId);
            if (resourceManager.getResource(
                    SpriteSource.TEXTURE_ID_CONVERTER.idToFile(baseSpriteId))
                .isPresent()) {
                return true;
            }
        }

        return false;
    }

    public static void clearDecodedImageCache() {
        synchronized (DECODED_IMAGE_CACHE_LOCK) {
            for (CacheEntry entry : DECODED_IMAGE_CACHE.values()) {
                if (entry.levels == null) {
                    continue;
                }
                for (NativeImage level : entry.levels) {
                    if (level != null) {
                        level.close();
                    }
                }
            }
            DECODED_IMAGE_CACHE.clear();
        }
    }

    public static void loadAndUpload(NativeImage source, INativeImageExt sourceExt, int level,
        int offsetX, int offsetY, int unpackSkipPixels, int unpackSkipRows, int regionWidth,
        int regionHeight, boolean blur) {
        int targetId = sourceExt.radiance$getTargetID();
        ResourceLocation identifier = sourceExt.radiance$getIdentifier();
        if (regionWidth == 0 || regionHeight == 0) return;
        if (regionWidth < 0 || regionHeight < 0 || unpackSkipPixels < 0 || unpackSkipRows < 0 ||
            (long) unpackSkipPixels + regionWidth > source.getWidth() ||
            (long) unpackSkipRows + regionHeight > source.getHeight())
            throw new IllegalArgumentException("Auxiliary upload exceeds source image");

        if (identifier != null) {
            if (isAuxiliaryTexture(identifier)) {
                return;
            }

            for (AuxiliaryTextures auxiliaryTexture : ALL_TEXTURES) {
                int auxiliaryTargetId;

                // ensure the texture exists
                TextureTracker.Texture texture = TextureTracker.GLID2Texture.get(targetId);
                if (!auxiliaryTexture.GLIDMapping.containsKey(targetId)) {
                    auxiliaryTargetId = TextureProxy.generateTextureId();
//                    System.out.println(
//                        "generate " + auxiliaryTexture.name + " texture for " + targetId + ": "
//                            + auxiliaryTargetId);

                    TextureUtil.prepareImage(texture.format().getNativeImageInternalFormat(),
                        auxiliaryTargetId, texture.maxLayer(), texture.width(), texture.height());
                    auxiliaryTexture.GLIDMapping.put(targetId, auxiliaryTargetId);
                    ResourceReloadCoordinator.markAuxiliaryTexturePrepared(auxiliaryTargetId);
                } else {
                    auxiliaryTargetId = auxiliaryTexture.GLIDMapping.get(targetId);

                    TextureTracker.Texture auxiliaryTrackerTexture = TextureTracker.GLID2Texture.get(
                        auxiliaryTargetId);
                    if (texture.width() != auxiliaryTrackerTexture.width()
                        || texture.height() != auxiliaryTrackerTexture.height()
                        || texture.format() != auxiliaryTrackerTexture.format()
                        || ResourceReloadCoordinator.claimAuxiliaryTexture(auxiliaryTargetId)) {
                        TextureUtil.prepareImage(texture.format().getNativeImageInternalFormat(),
                            auxiliaryTargetId, texture.maxLayer(), texture.width(),
                            texture.height());
                    }
                }

                String path = identifier.getPath();
                boolean lookupMaterial = path.contains("textures/block") || path.contains("textures/item") ||
                    path.contains("textures/entity") || path.contains("textures/models") ||
                    path.contains("textures/particle") || path.contains("textures/font");
                NativeImage auxiliaryImage = lookupMaterial
                    ? auxiliaryTexture.copyPreparedRegion(identifier, level, source.format(),
                        unpackSkipPixels, unpackSkipRows, regionWidth, regionHeight) : null;
                boolean hasMaterial = auxiliaryImage != null;
                if (com.radiance.api.audit.RenderAuditBridge.accepts("CHUNK_PERF")) {
                    com.radiance.api.audit.RenderAuditBridge.counter("CHUNK_PERF", "AUXILIARY_SOURCE_BYTES",
                        (long) source.getWidth() * source.getHeight() * source.format().components(), "");
                    com.radiance.api.audit.RenderAuditBridge.counter("CHUNK_PERF", "AUXILIARY_PREPARED_BYTES",
                        (long) regionWidth * regionHeight * source.format().components(), "");
                }
                if (auxiliaryImage == null) {
                    // Initialize every requested region, including no-PBR and special textures.
                    // A missing material is not permission to leave an atlas slot unwritten.
                    auxiliaryImage = new NativeImage(source.format(), regionWidth, regionHeight, false);
                    AuxiliaryPixelRegion.copy(pointer(auxiliaryImage), regionWidth, regionHeight,
                        source.format().components(), 0, 0, 0, 0, 0, 0,
                        auxiliaryTexture.defaultValueProvider.get(source));
                }
                try {
                    ((INativeImageExt) (Object) auxiliaryImage).radiance$setTargetID(auxiliaryTargetId);
                    if (level == 0 && auxiliaryTexture == SPECULAR) {
                        // Null specular publishes an empty tile, clearing any prior emission.
                        TextureProxy.uploadEmissionTile(EmissionRecorder.buildTileUpdate(targetId,
                            source, hasMaterial ? auxiliaryImage : null, offsetX, offsetY,
                            unpackSkipPixels, unpackSkipRows, regionWidth, regionHeight, 0, 0));
                    }
                    auxiliaryImage.upload(level, offsetX, offsetY, 0, 0,
                        regionWidth, regionHeight, false, false);
                } finally {
                    auxiliaryImage.close();
                }
            }
        }
    }

    /**
     * Dynamic glyph writers bypass NativeImage.upload. Clear every auxiliary atlas region they
     * reuse so no material data from a previous glyph can leak into the new one.
     */
    public static void clearDynamicRegion(int targetId, int offsetX, int offsetY, int width,
        int height) {
        clearMappedRegion(TextureTracker.GLID2SpecularGLID, targetId, offsetX, offsetY, width,
            height);
        clearMappedRegion(TextureTracker.GLID2NormalGLID, targetId, offsetX, offsetY, width,
            height);
        clearMappedRegion(TextureTracker.GLID2FlagGLID, targetId, offsetX, offsetY, width, height);
    }

    private static void clearMappedRegion(Map<Integer, Integer> mapping, int targetId, int offsetX,
        int offsetY, int width, int height) {
        Integer auxiliaryTargetId = mapping.get(targetId);
        if (auxiliaryTargetId == null) {
            return;
        }
        TextureTracker.Texture texture = TextureTracker.GLID2Texture.get(auxiliaryTargetId);
        if (texture == null) {
            return;
        }
        uploadConstantRegion(auxiliaryTargetId, 0, offsetX, offsetY, width, height,
            texture.channel(), 0);
    }

    private boolean matchesSuffix(String path) {
        return path.endsWith(suffix);
    }

    private ResourceLocation toBaseSpriteId(ResourceLocation spriteId) {
        String spritePath = spriteId.getPath();
        return spriteId.withPath(spritePath.substring(0, spritePath.length() - suffix.length()));
    }

    private CacheKey toBaseCacheKey(ResourceLocation auxiliaryIdentifier) {
        String path = auxiliaryIdentifier.getPath();
        if (this == FLAG) {
            path = path.replaceFirst("^textures/flag/", "textures/");
        }

        int dotIndex = path.lastIndexOf('.');
        String baseName = path.substring(0, dotIndex);
        if (!baseName.endsWith(suffix)) {
            throw new IllegalArgumentException("Unexpected auxiliary path: " + auxiliaryIdentifier);
        }
        baseName = baseName.substring(0, baseName.length() - suffix.length());
        return new CacheKey(this, ResourceLocation.fromNamespaceAndPath(auxiliaryIdentifier.getNamespace(),
            baseName + path.substring(dotIndex)));
    }

    private CacheEntry getPreparedEntry(ResourceLocation identifier) {
        return DECODED_IMAGE_CACHE.getOrDefault(new CacheKey(this, identifier), CacheEntry.MISSING);
    }

    private static void uploadConstantRegion(int targetId, int level, int offsetX, int offsetY,
        int width, int height, int channels, int value) {
        if (width <= 0 || height <= 0 || channels <= 0) {
            return;
        }
        int byteCount = Math.multiplyExact(Math.multiplyExact(width, height), channels);
        ByteBuffer pixels = MemoryUtil.memAlloc(byteCount);
        try {
            MemoryUtil.memSet(MemoryUtil.memAddress(pixels), value & 0xFF, byteCount);
            TextureProxy.queueUpload(MemoryUtil.memAddress(pixels), byteCount, width, targetId,
                0, 0, offsetX, offsetY, width, height, level);
        } finally {
            MemoryUtil.memFree(pixels);
        }
    }

    private static long pointer(NativeImage image) {
        return ((com.radiance.mixin_related.extensions.vulkan_render_integration.INativeImageExt)
            (Object) image).radiance$getPointer();
    }

    private NativeImage copyPreparedRegion(ResourceLocation identifier, int level,
        NativeImage.Format format, int x, int y, int width, int height) {
        synchronized (DECODED_IMAGE_CACHE_LOCK) {
            NativeImage prepared = this.getPreparedEntry(identifier).getImage(level);
            if (prepared == null) return null;
            NativeImage region = new NativeImage(format, width, height, false);
            try {
                AuxiliaryPixelRegion.copy(pointer(region), width, height, format.components(),
                    pointer(prepared), prepared.getWidth(), prepared.getHeight(),
                    prepared.format().components(), x, y, 0);
                return region;
            } catch (Throwable failure) {
                region.close();
                throw failure;
            }
        }
    }

    private static AuxiliaryTextures classifyAuxiliaryResource(ResourceLocation id) {
        String path = id.getPath();
        if (!path.endsWith(".png")) {
            return null;
        }
        if (isTrackedFlagPath(path) && path.endsWith(FLAG.suffix + ".png")) {
            return FLAG;
        }
        if (isTrackedTexturePath(path) && path.endsWith(SPECULAR.suffix + ".png")) {
            return SPECULAR;
        }
        if (isTrackedTexturePath(path) && path.endsWith(NORMAL.suffix + ".png")) {
            return NORMAL;
        }
        return null;
    }

    public static CompletableFuture<PreparedImages> prepareDecodedImagesAsync(
        ResourceManager resourceManager, Executor prepareExecutor) {
        List<CompletableFuture<DecodedEntry>> futures = new ArrayList<>();
        Map<ResourceLocation, Resource> resources = resourceManager.listResources("textures",
            id -> classifyAuxiliaryResource(id) != null);

        for (Map.Entry<ResourceLocation, Resource> entry : resources.entrySet()) {
            AuxiliaryTextures auxiliaryTexture = classifyAuxiliaryResource(entry.getKey());
            if (auxiliaryTexture == null) {
                continue;
            }
            CacheKey cacheKey = auxiliaryTexture.toBaseCacheKey(entry.getKey());
            Resource resource = entry.getValue();
            futures.add(CompletableFuture.supplyAsync(
                () -> decodePreparedEntry(cacheKey, resource), prepareExecutor));
        }

        CompletableFuture<Void> allFutures = CompletableFuture.allOf(
            futures.toArray(CompletableFuture[]::new));
        return allFutures.thenApply(unused -> {
            PreparedImages prepared = new PreparedImages();
            for (CompletableFuture<DecodedEntry> future : futures) {
                prepared.add(future.join());
            }
            return prepared;
        });
    }

    private static DecodedEntry decodePreparedEntry(CacheKey cacheKey, Resource resource) {
        try (InputStream inputStream = resource.open()) {
            NativeImage image = NativeImage.read(inputStream);
            NativeImage[] levels = MipmapUtil.buildMipmapChain(image);
            return new DecodedEntry(cacheKey, new CacheEntry(levels));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static void applyPreparedImages(PreparedImages prepared) {
        synchronized (DECODED_IMAGE_CACHE_LOCK) {
            clearDecodedImageCache();
            DECODED_IMAGE_CACHE.putAll(prepared.entries);
        }
    }

    private static boolean isTrackedTexturePath(String path) {
        return path.startsWith("textures/block/")
            || path.startsWith("textures/item/")
            || path.startsWith("textures/entity/")
            || path.startsWith("textures/models/")
            || path.startsWith("textures/particle/")
            || path.startsWith("textures/font/");
    }

    private static boolean isTrackedFlagPath(String path) {
        return path.startsWith("textures/flag/block/")
            || path.startsWith("textures/flag/item/")
            || path.startsWith("textures/flag/entity/")
            || path.startsWith("textures/flag/models/")
            || path.startsWith("textures/flag/particle/")
            || path.startsWith("textures/flag/font/");
    }

    private record CacheKey(AuxiliaryTextures texture, ResourceLocation identifier) {}

    private static final class CacheEntry {

        private static final CacheEntry MISSING = new CacheEntry(null);

        private final NativeImage[] levels;

        private CacheEntry(NativeImage[] levels) {
            this.levels = levels;
        }

        private NativeImage getImage(int level) {
            if (levels == null || levels.length == 0) {
                return null;
            }
            return levels[Math.min(level, levels.length - 1)];
        }
    }

    private record DecodedEntry(CacheKey cacheKey, CacheEntry cacheEntry) {}

    public static final class PreparedImages {

        private final Map<CacheKey, CacheEntry> entries = new ConcurrentHashMap<>();

        private void add(DecodedEntry entry) {
            this.entries.put(entry.cacheKey(), entry.cacheEntry());
        }

        private void close() {
            for (CacheEntry entry : this.entries.values()) {
                if (entry.levels == null) {
                    continue;
                }
                for (NativeImage level : entry.levels) {
                    if (level != null) {
                        level.close();
                    }
                }
            }
            this.entries.clear();
        }
    }

    public interface IdentifierCandidateProvider {

        List<ResourceLocation> get(ResourceLocation identifier, NativeImage source);
    }

    public interface Getter {

        NativeImage get(INativeImageExt nativeImageExt);
    }

    public interface Setter {

        void set(INativeImageExt nativeImageExt, NativeImage nativeImage);
    }

    public interface DefaultValueProvider {

        int get(NativeImage source);
    }
}
