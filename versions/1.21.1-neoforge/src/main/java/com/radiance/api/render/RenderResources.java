package com.radiance.api.render;

import com.radiance.client.constant.VulkanConstants;
import com.radiance.client.proxy.vulkan.BufferProxy;
import com.radiance.client.proxy.vulkan.TextureProxy;
import com.radiance.client.texture.TextureTasks;
import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicLong;
import org.lwjgl.system.MemoryUtil;

/**
 * Identities are never reused. Native resource generations and retirement determine storage life.
 */
public final class RenderResources {
    private static final AtomicLong GEOMETRY = new AtomicLong();

    private RenderResources() {}

    /** The current vanilla adapter can prove this complete baked graph immutable. */
    public static boolean immutableBlockModel(
            net.minecraft.client.resources.model.BakedModel model) {
        return com.radiance.adapter.vanilla.VanillaWorldBlockRequests.immutableModel(model);
    }

    /** Source tint emission is independent of received light and sampled alongside its colour. */
    public static float blockTintEmission(
            net.minecraft.world.level.block.state.BlockState state,
            net.minecraft.world.level.BlockAndTintGetter level,
            net.minecraft.core.BlockPos pos,
            int tint) {
        return ((com.radiance.mixin_related.extensions.vulkan_render_integration.IBlockColorsExt)
                        net.minecraft.client.Minecraft.getInstance().getBlockColors())
                .radiance$getEmission(state, level, pos, tint);
    }

    public static long geometryIdentity() {
        long id = GEOMETRY.incrementAndGet();
        if (id > Integer.MAX_VALUE) throw new IllegalStateException("Geometry identity exhausted");
        return id;
    }

    public enum BufferUse {
        VERTEX(VulkanConstants.VkBufferUsageFlagBits.VK_BUFFER_USAGE_VERTEX_BUFFER_BIT.getValue()),
        INDEX(VulkanConstants.VkBufferUsageFlagBits.VK_BUFFER_USAGE_INDEX_BUFFER_BIT.getValue()),
        INDIRECT(
                VulkanConstants.VkBufferUsageFlagBits.VK_BUFFER_USAGE_INDIRECT_BUFFER_BIT
                        .getValue());
        private final int nativeUsage;

        BufferUse(int nativeUsage) {
            this.nativeUsage = nativeUsage;
        }
    }

    /**
     * Owns an engine ID, not a Vulkan object/address. Queued uploads copy CPU input before return.
     */
    public static final class Buffer implements AutoCloseable {
        private final int id;
        private int bytes;
        private boolean closed;

        private Buffer() {
            id = BufferProxy.allocatePersistentBuffer();
            if (id < 0) throw new IllegalStateException("Buffer allocation failed");
        }

        public int id() {
            requireLive();
            return id;
        }

        public void initialize(int size, BufferUse usage) {
            requireLive();
            if (size < 0) throw new IllegalArgumentException("Negative buffer size");
            BufferProxy.initializeBuffer(
                    id, size, java.util.Objects.requireNonNull(usage).nativeUsage);
            bytes = size;
        }

        public void upload(int offset, ByteBuffer input) {
            requireLive();
            if (!input.isDirect() || offset < 0 || (long) offset + input.remaining() > bytes)
                throw new IllegalArgumentException("Upload exceeds owned buffer");
            if (input.hasRemaining())
                BufferProxy.queuePersistentUploadRange(
                        MemoryUtil.memAddress(input), input.remaining(), id, offset);
        }

        private void requireLive() {
            if (closed) throw new IllegalStateException("Buffer is closed");
        }

        public void close() {
            if (!closed) {
                BufferProxy.releasePersistentBuffer(id);
                closed = true;
            }
        }
    }

    public static Buffer buffer() {
        return new Buffer();
    }

    public enum Sampling {
        NEAREST,
        LINEAR
    }

    /** An owned RGBA8 image with the same generation protection used by vanilla texture uploads. */
    public static final class Texture implements AutoCloseable {
        private final TextureTasks.Owner owner;
        private final int width, height, levels;
        private boolean closed;

        private Texture(int width, int height, int levels, Sampling sampling, Sampling mipmap) {
            this(
                    width,
                    height,
                    levels,
                    sampling,
                    mipmap,
                    VulkanConstants.VkFormat.VK_FORMAT_R8G8B8A8_UNORM);
        }

        private Texture(
                int width,
                int height,
                int levels,
                Sampling sampling,
                Sampling mipmap,
                VulkanConstants.VkFormat format) {
            if (width <= 0
                    || height <= 0
                    || levels <= 0
                    || levels > 32 - Integer.numberOfLeadingZeros(Math.max(width, height)))
                throw new IllegalArgumentException("Invalid image dimensions/mip levels");
            java.util.Objects.requireNonNull(sampling);
            java.util.Objects.requireNonNull(mipmap);
            int id = TextureProxy.generateTextureId();
            owner = TextureProxy.TASKS.owner(id);
            this.width = width;
            this.height = height;
            this.levels = levels;
            try {
                TextureProxy.prepareImage(id, levels, width, height, format);
                TextureProxy.setFilter(id, sampling.ordinal(), mipmap.ordinal());
                TextureProxy.setClamp(id, 2);
            } catch (RuntimeException | Error failure) {
                try {
                    TextureProxy.releaseTextureId(owner, 0);
                } catch (RuntimeException | Error cleanup) {
                    failure.addSuppressed(cleanup);
                }
                throw failure;
            }
        }

        public TextureTasks.Owner owner() {
            return owner;
        }

        public int id() {
            if (closed) throw new IllegalStateException("Image is closed");
            return owner.id();
        }

        public void upload(int level, int x, int y, int w, int h, ByteBuffer input) {
            if (closed
                    || level < 0
                    || level >= levels
                    || x < 0
                    || y < 0
                    || w < 0
                    || h < 0
                    || (long) x + w > Math.max(1, width >> level)
                    || (long) y + h > Math.max(1, height >> level)
                    || !input.isDirect()
                    || (long) w * h * 4 != input.remaining())
                throw new IllegalArgumentException("Invalid owned RGBA image upload");
            if (!TextureProxy.TASKS.use(
                    owner,
                    () ->
                            TextureProxy.queueUpload(
                                    MemoryUtil.memAddress(input),
                                    input.remaining(),
                                    w,
                                    owner.id(),
                                    0,
                                    0,
                                    x,
                                    y,
                                    w,
                                    h,
                                    level)))
                throw new IllegalStateException("Image generation has retired");
        }

        public void close() {
            if (!closed) {
                TextureProxy.releaseTextureId(owner, 0);
                closed = true;
            }
        }
    }

    public static Texture rgba8(
            int width, int height, int levels, Sampling sampling, Sampling mipmap) {
        return new Texture(width, height, levels, sampling, mipmap);
    }

    /** One finite source scalar per texel; uploads use native-order IEEE-754 floats. */
    public static Texture scalarField(int width, int height) {
        return new Texture(
                width,
                height,
                1,
                Sampling.NEAREST,
                Sampling.NEAREST,
                VulkanConstants.VkFormat.VK_FORMAT_R32_SFLOAT);
    }
}
