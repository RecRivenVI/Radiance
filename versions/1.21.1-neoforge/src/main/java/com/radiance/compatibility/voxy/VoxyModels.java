package com.radiance.compatibility.voxy;

import com.radiance.client.constant.VulkanConstants;
import com.radiance.client.proxy.vulkan.TextureProxy;
import com.radiance.client.texture.TextureTasks;
import com.radiance.mixins.compatibility.voxy.VoxyBiomeUploadAccess;
import com.radiance.mixins.compatibility.voxy.VoxyModelFactoryAccess;
import com.radiance.mixins.compatibility.voxy.VoxyModelUploadAccess;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import me.cortex.voxy.client.core.model.ModelFactory;
import me.cortex.voxy.client.core.model.ModelQueries;
import me.cortex.voxy.common.util.MemoryBuffer;
import org.lwjgl.system.MemoryUtil;

/** Source projection output only: no full model/PBR reconstruction and no GL ModelStore. */
final class VoxyModels implements AutoCloseable {
    private final long epoch;
    final ModelFactory factory;
    private final List<TextureTasks.Owner> pages = new ArrayList<>();
    private int[] records = new int[16 * 256];
    private int count;
    private int[] colours = new int[0];
    private boolean dirty;

    VoxyModels(long epoch, ModelFactory factory) {
        this.epoch = epoch;
        this.factory = factory;
    }

    private void patchColours(MemoryBuffer bytes, int first) {
        if (bytes == null || bytes.size == 0) return;
        if ((bytes.size & 3) != 0 || first < 0 || first + bytes.size / 4 > 8 * 1024 * 1024)
            throw new IllegalArgumentException("Voxy biome table exceeds upload budget");
        int end = Math.toIntExact(first + bytes.size / 4);
        if (colours.length < end) colours = Arrays.copyOf(colours, end);
        for (int i = first; i < end; i++)
            colours[i] = MemoryUtil.memGetInt(bytes.address + (i - first) * 4L);
    }

    void drain() {
        Object result;
        var queue = ((VoxyModelFactoryAccess) factory).radiance$uploads();
        while ((result = queue.poll()) != null) {
            if (result instanceof VoxyModelUploadAccess model) {
                try {
                    accept(model);
                } finally {
                    model.radiance$free();
                }
            } else if (result instanceof VoxyBiomeUploadAccess biome) {
                try {
                    patchColours(biome.radiance$colours(), 0);
                    MemoryBuffer pairs = biome.radiance$pairs();
                    if ((pairs.size & 7) != 0)
                        throw new IllegalArgumentException("Voxy biome pair length");
                    for (long p = 0; p < pairs.size; p += 8) {
                        long pair = MemoryUtil.memGetLong(pairs.address + p);
                        int id = (int) pair;
                        if (id < 0 || id >= count)
                            throw new IllegalArgumentException("Unknown Voxy biome model");
                        records[id * 16 + 7] = (int) (pair >>> 32);
                    }
                    dirty = true;
                } finally {
                    biome.radiance$free();
                }
            } else
                throw new IllegalStateException(
                        "Unsupported Voxy model result " + result.getClass().getName());
        }
    }

    private void accept(VoxyModelUploadAccess model) {
        int id = model.radiance$id();
        if (id < 0 || id >= 65536 || model.radiance$model().size != 64)
            throw new IllegalArgumentException("Voxy model ABI mismatch");
        if (records.length < (id + 1) * 16)
            records =
                    Arrays.copyOf(
                            records,
                            Math.min(65536 * 16, Math.max(records.length * 2, (id + 1) * 16)));
        count = Math.max(count, id + 1);
        for (int i = 0; i < 16; i++)
            records[id * 16 + i] = MemoryUtil.memGetInt(model.radiance$model().address + i * 4L);
        int page = id >>> 8;
        while (pages.size() <= page) {
            int texture = TextureProxy.generateTextureId();
            TextureProxy.prepareImage(
                    texture, 4, 768, 512, VulkanConstants.VkFormat.VK_FORMAT_R8G8B8A8_UNORM);
            TextureProxy.setFilter(texture, 0, 1);
            TextureProxy.setClamp(texture, 2);
            pages.add(TextureProxy.TASKS.owner(texture));
        }
        var owner = pages.get(page);
        int tile = id & 255;
        MemoryBuffer source = model.radiance$texture();
        long offset = 0;
        int levels = model.radiance$hasMips() ? 4 : 1;
        for (int level = 0; level < levels; level++) {
            int w = 48 >> level, h = 32 >> level;
            int bytes = w * h * 4;
            if (offset + bytes > source.size)
                throw new IllegalArgumentException("Voxy model texture length");
            synchronized (TextureProxy.class) {
                if (!owner.equals(TextureProxy.TASKS.owner(owner.id())))
                    throw new IllegalStateException("Voxy model page ownership changed");
                TextureProxy.queueUpload(
                        source.address + offset,
                        bytes,
                        w,
                        owner.id(),
                        0,
                        0,
                        ((tile & 15) * 48) >> level,
                        ((tile >>> 4) * 32) >> level,
                        w,
                        h,
                        level);
            }
            offset += bytes;
        }
        records[id * 16 + 9] = owner.id();
        records[id * 16 + 10] =
                Float.floatToRawIntBits(
                        ModelQueries.lightEmission(factory.getModelMetadataFromClientId(id))
                                / 15.0f);
        records[id * 16 + 11] =
                ModelQueries.isFluid(factory.getModelMetadataFromClientId(id))
                        ? VoxySourceMaterials.fluid(factory, id)
                        : 0;
        records[id * 16 + 12] = tile;
        records[id * 16 + 13] = 0x56585054;
        records[id * 16 + 14] =
                ModelQueries.isDoubleSided(factory.getModelMetadataFromClientId(id)) ? 1 : 0;
        records[id * 16 + 15] = model.radiance$hasMips() ? 3 : 0;
        if (model.radiance$biomeIndex() >= 0)
            patchColours(model.radiance$biome(), model.radiance$biomeIndex());
        dirty = true;
    }

    void publish() {
        if (!dirty || count == 0) return;
        ByteBuffer models = MemoryUtil.memAlloc(Math.multiplyExact(count, 64));
        ByteBuffer tints = MemoryUtil.memAlloc(Math.max(4, colours.length * 4));
        try {
            for (int i = 0; i < count * 16; i++) models.putInt(i * 4, records[i]);
            for (int i = 0; i < colours.length; i++) tints.putInt(i * 4, colours[i]);
            VoxyNative.models(
                    epoch,
                    MemoryUtil.memAddress(models),
                    count,
                    MemoryUtil.memAddress(tints),
                    colours.length);
            dirty = false;
        } finally {
            MemoryUtil.memFree(models);
            MemoryUtil.memFree(tints);
        }
    }

    @Override
    public void close() {
        VoxySourceMaterials.release(factory);
        factory.free();
        for (var page : pages) TextureProxy.releaseTextureId(page, 0);
        pages.clear();
    }
}
