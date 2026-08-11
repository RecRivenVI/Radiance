package com.radiance.api.render;

import com.radiance.api.render.internal.RigidSceneNative;
import com.radiance.client.proxy.vulkan.TextureProxy;
import com.radiance.client.texture.TextureTasks;
import java.nio.*;
import java.util.*;
import org.joml.Matrix4f;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryUtil;

public class LocalModelBatch implements AutoCloseable {
    static final int RECORD_BYTES = com.radiance.render.contract.RenderAbi.RIGID_RECORD_BYTES;
    private static final ByteBuffer EMPTY =
            ByteBuffer.allocateDirect(0).order(ByteOrder.nativeOrder());
    private static final java.util.concurrent.ConcurrentLinkedQueue<Storage> FREE =
            new java.util.concurrent.ConcurrentLinkedQueue<>();
    private static final java.util.concurrent.atomic.AtomicInteger FREE_COUNT =
            new java.util.concurrent.atomic.AtomicInteger();
    private final Storage storage;
    private final ByteBuffer records;
    private boolean closed;
    private final Map<String, Integer> groupIndices;
    final List<String> groups;
    private final Set<TextureTasks.Owner> textures;
    private final Map<Long, List<LayerBinding>> bindings;
    private final int capacity;
    private final List<Object> owners;
    private final List<String> drawGroups;

    private record Selection(long identity, int record, Object owner) {}

    private final List<Selection> selections;

    void select(long identity, Object owner) {
        requireOpen();
        selections.add(new Selection(identity, -1, owner));
    }

    /** Select an accepted version before creating bindings, group tables or a packed record. */
    public boolean selectUnchanged(
            long id,
            Object owner,
            long model,
            int type,
            TextureTasks.Owner texture,
            int vertices,
            double x,
            double y,
            double z,
            int mask,
            org.joml.Matrix4fc transform,
            String group,
            com.radiance.render.contract.DrawStateSnapshot colour,
            RenderSpace space) {
        return selectUnchanged(
                id,
                owner,
                model,
                type,
                texture,
                vertices,
                x,
                y,
                z,
                mask,
                transform,
                group,
                colour,
                space,
                LocalSurface.NONE);
    }

    public boolean selectUnchanged(
            long id,
            Object owner,
            long model,
            int type,
            TextureTasks.Owner texture,
            int vertices,
            double x,
            double y,
            double z,
            int mask,
            org.joml.Matrix4fc transform,
            String group,
            com.radiance.render.contract.DrawStateSnapshot colour,
            RenderSpace space,
            LocalSurface surface) {
        return selectUnchanged(
                id, owner, model, type, texture, vertices, x, y, z, mask, transform, group, colour,
                space, surface, 0);
    }

    public boolean selectUnchanged(
            long id,
            Object owner,
            long model,
            int type,
            TextureTasks.Owner texture,
            int vertices,
            double x,
            double y,
            double z,
            int mask,
            org.joml.Matrix4fc transform,
            String group,
            com.radiance.render.contract.DrawStateSnapshot colour,
            RenderSpace space,
            LocalSurface surface,
            int coordinates) {
        requireOpen();
        if (owner == null
                || !PersistentInstances.matches(
                        id,
                        owner,
                        model,
                        type,
                        texture.id(),
                        vertices,
                        x,
                        y,
                        z,
                        mask,
                        transform,
                        group,
                        colour,
                        space == null ? 0 : space.handle(),
                        surface,
                        coordinates)) return false;
        selections.add(new Selection(id, -1, owner));
        return true;
    }

    public record LayerBinding(
            ByteBuffer vertices,
            int vertexCount,
            int type,
            TextureTasks.Owner texture,
            String group,
            com.radiance.render.contract.DrawStateSnapshot state) {
        public LayerBinding {
            if (!vertices.isDirect()
                    || vertexCount <= 0
                    || vertexCount % 4 != 0
                    || vertices.remaining() < Math.multiplyExact(vertexCount, 128))
                throw new IllegalArgumentException("Incomplete canonical layer geometry");
            Objects.requireNonNull(texture);
            Objects.requireNonNull(group);
            Objects.requireNonNull(state);
        }
    }

    public void layers(long key, List<LayerBinding> layers) {
        requireOpen();
        bindings.putIfAbsent(key, List.copyOf(layers));
    }

    public void texture(TextureTasks.Owner owner) {
        requireOpen();
        textures.add(owner);
    }

    public void source(Object owner) {
        requireOpen();
        owners.add(owner);
    }

    int count;

    public LocalModelBatch(int capacity) {
        if (capacity < 0) throw new IllegalArgumentException("Negative instance capacity");
        this.capacity = capacity;
        Storage reused = FREE.poll();
        if (reused != null) FREE_COUNT.decrementAndGet();
        storage = reused == null ? new Storage() : reused;
        groupIndices = storage.groupIndices;
        groups = storage.groups;
        textures = storage.textures;
        bindings = storage.layerBindings;
        owners = storage.owners;
        drawGroups = storage.drawGroups;
        selections = storage.selections;
        try {
            storage.records = reserve(storage.records, Math.multiplyExact(capacity, RECORD_BYTES));
            records = storage.records == null ? EMPTY : storage.records;
        } catch (RuntimeException | Error failure) {
            storage.close();
            throw failure;
        }
    }

    /** Reuse CPU staging after synchronous submission; accepted snapshots own their bytes. */
    public void reset() {
        requireOpen();
        storage.reusable &=
                selections.size() <= 16384
                        && bindings.size() <= 16384
                        && owners.size() <= 16384
                        && textures.size() <= 16384
                        && groups.size() <= 16384;
        count = 0;
        groupIndices.clear();
        groups.clear();
        textures.clear();
        bindings.clear();
        owners.clear();
        drawGroups.clear();
        selections.clear();
    }

    public void add(
            long model,
            long history,
            int type,
            TextureTasks.Owner texture,
            ByteBuffer vertices,
            int vertexCount,
            double x,
            double y,
            double z,
            int mask,
            Matrix4f transform,
            String group) {
        add(
                model,
                history,
                type,
                texture,
                vertices,
                vertexCount,
                x,
                y,
                z,
                mask,
                transform,
                group,
                com.radiance.render.contract.DrawStateSnapshot.IDENTITY);
    }

    public void add(
            long model,
            long history,
            int type,
            TextureTasks.Owner texture,
            ByteBuffer vertices,
            int vertexCount,
            double x,
            double y,
            double z,
            int mask,
            Matrix4f transform,
            String group,
            com.radiance.render.contract.DrawStateSnapshot snapshot) {
        add(
                model,
                history,
                type,
                texture,
                vertices,
                vertexCount,
                x,
                y,
                z,
                mask,
                transform,
                group,
                snapshot,
                null);
    }

    public void add(
            long model,
            long history,
            int type,
            TextureTasks.Owner texture,
            ByteBuffer vertices,
            int vertexCount,
            double x,
            double y,
            double z,
            int mask,
            Matrix4f transform,
            String group,
            com.radiance.render.contract.DrawStateSnapshot snapshot,
            RenderSpace space) {
        add(
                model,
                history,
                type,
                texture,
                vertices,
                vertexCount,
                x,
                y,
                z,
                mask,
                transform,
                group,
                snapshot,
                space,
                LocalSurface.NONE);
    }

    public void add(
            long model,
            long history,
            int type,
            TextureTasks.Owner texture,
            ByteBuffer vertices,
            int vertexCount,
            double x,
            double y,
            double z,
            int mask,
            Matrix4f transform,
            String group,
            com.radiance.render.contract.DrawStateSnapshot snapshot,
            RenderSpace space,
            LocalSurface surface) {
        add(
                model,
                history,
                type,
                texture,
                vertices,
                vertexCount,
                x,
                y,
                z,
                mask,
                transform,
                group,
                snapshot,
                space,
                surface,
                0);
    }

    public void add(
            long model,
            long history,
            int type,
            TextureTasks.Owner texture,
            ByteBuffer vertices,
            int vertexCount,
            double x,
            double y,
            double z,
            int mask,
            Matrix4f transform,
            String group,
            com.radiance.render.contract.DrawStateSnapshot snapshot,
            RenderSpace space,
            LocalSurface surface,
            int coordinates) {
        requireOpen();
        if (coordinates < 0 || coordinates > 2 || coordinates != 0 && space != null)
            throw new IllegalArgumentException("Invalid local instance coordinates");
        if (count == capacity)
            throw new IllegalStateException("Rigid model batch capacity exceeded");
        if (!vertices.isDirect() || vertices.remaining() < Math.multiplyExact(vertexCount, 128))
            throw new IllegalArgumentException("Incomplete local model input");
        drawGroups.add(group);
        int base = count * RECORD_BYTES;
        records.putLong(base, model)
                .putLong(base + 8, history)
                .putLong(base + 16, MemoryUtil.memAddress(vertices))
                .putDouble(base + 24, x)
                .putDouble(base + 32, y)
                .putDouble(base + 40, z);
        transform.get(base + 48, records);
        records.putInt(base + 112, type)
                .putInt(base + 116, texture.id())
                .putInt(base + 120, vertexCount)
                .putInt(base + 124, mask)
                .putInt(
                        base + 128,
                        groupIndices.computeIfAbsent(
                                group,
                                name -> {
                                    groups.add(name);
                                    return groups.size() - 1;
                                }))
                .putInt(base + 132, coordinates);
        snapshot.writeColour(
                records, base + com.radiance.render.contract.RenderAbi.RIGID_COLOUR_OFFSET);
        records.putLong(base + 152, space == null ? 0 : space.handle());
        surface.write(records, base + com.radiance.render.contract.RenderAbi.RIGID_SURFACE_OFFSET);
        textures.add(texture);
        selections.add(new Selection(0, count, null));
        count++;
    }

    public int count() {
        return selections.size();
    }

    public List<String> groups() {
        return List.copyOf(groups);
    }

    public ByteBuffer records() {
        requireOpen();
        return records.slice(0, capacity * RECORD_BYTES)
                .asReadOnlyBuffer()
                .order(ByteOrder.nativeOrder());
    }

    public void submit() {
        requireOpen();
        com.radiance.api.audit.RenderAuditBridge.counter("RIGID", "PACKED_RECORDS", count, "");
        com.radiance.api.audit.RenderAuditBridge.counter(
                "RIGID", "EARLY_REUSED", selections.size() - count, "");
        int totalBindings = 0;
        for (var layers : bindings.values())
            totalBindings = Math.addExact(totalBindings, layers.size());
        final int bindingCount = totalBindings;
        com.radiance.api.audit.RenderAuditBridge.counter(
                "RIGID", "PACKED_BINDINGS", bindingCount, "");
        int bindingStride = com.radiance.render.contract.RenderAbi.RIGID_BINDING_BYTES;
        storage.bindings =
                reserve(storage.bindings, Math.multiplyExact(bindingCount, bindingStride));
        ByteBuffer bindingData = storage.bindings;
        {
            int at = 0;
            for (var resource : bindings.entrySet()) {
                int ordinal = 0;
                for (var binding : resource.getValue()) {
                    String group = binding.group();
                    int groupIndex =
                            groupIndices.computeIfAbsent(
                                    group,
                                    name -> {
                                        groups.add(name);
                                        return groups.size() - 1;
                                    });
                    bindingData
                            .putLong(at, resource.getKey())
                            .putLong(at + 8, MemoryUtil.memAddress(binding.vertices()))
                            .putInt(at + 16, binding.vertexCount())
                            .putInt(at + 20, binding.type())
                            .putInt(at + 24, binding.texture().id())
                            .putInt(at + 28, groupIndex);
                    binding.state().writeColour(bindingData, at + 32);
                    bindingData
                            .putInt(at + 48, ordinal++)
                            .putInt(at + 52, resource.getValue().size());
                    binding.state().writeUv(bindingData, at + 56);
                    at += bindingStride;
                }
            }
            storage.names(groups);
            {
                if (!TextureProxy.TASKS.useAll(
                        textures,
                        () -> {
                            submitChanges(storage.names, bindingData, bindingCount);
                        }))
                    throw new IllegalStateException(
                            "Rigid model texture was retired before submission");
                com.radiance.api.audit.DrawingAudit.consumed("native.scene.instances");
            }
        }
    }

    private void submitChanges(PointerBuffer names, ByteBuffer bindingData, int bindingCount) {
        PersistentInstances.collect();
        long[] removed = PersistentInstances.removals();
        storage.selected = reserve(storage.selected, selections.size());
        storage.retired = reserve(storage.retired, removed.length);
        storage.changes = reserve(storage.changes, Math.multiplyExact(count, RECORD_BYTES));
        LongBuffer selected = storage.selected, retired = storage.retired;
        ByteBuffer changed = storage.changes;
        List<Integer> updated = storage.updated;
        updated.clear();
        try {
            var admitted = storage.admitted;
            admitted.clear();
            int available = count == 0 ? 0 : PersistentInstances.available();
            for (var selection : selections) {
                if (selection.record < 0) {
                    selected.put(selection.identity);
                    continue;
                }
                int i = selection.record;
                int at = i * RECORD_BYTES;
                long id = records.getLong(at + 8);
                Object owner = i < owners.size() ? owners.get(i) : null;
                boolean persistent =
                        owner != null
                                && !(owner instanceof PersistentInstances.OwnerState state
                                        && state.radiance$isRetired())
                                && (PersistentInstances.known(id)
                                        || admitted.contains(id)
                                        || admitted.size() < available);
                if (persistent && !PersistentInstances.known(id)) admitted.add(id);
                // A zero selected ID means this record is transient; it is never stored as a
                // persistent object.
                selected.put(persistent ? id : 0);
                if (!persistent
                        || PersistentInstances.changed(id, records, at, drawGroups.get(i))) {
                    appendRecord(records, at, changed);
                    updated.add(i);
                }
            }
            if (retired != null) retired.put(removed).flip();
            if (selected != null) selected.flip();
            if (changed != null) changed.flip();
            long[] refused =
                    RigidSceneNative.submit(
                            com.radiance.render.contract.RenderAbi.RIGID_INPUT_VERSION,
                            RECORD_BYTES,
                            updated.size() * RECORD_BYTES,
                            MemoryUtil.memAddressSafe(changed),
                            updated.size(),
                            MemoryUtil.memAddressSafe(names),
                            groups.size(),
                            com.radiance.render.contract.RenderAbi.RIGID_BINDING_VERSION,
                            bindingCount
                                    * com.radiance.render.contract.RenderAbi.RIGID_BINDING_BYTES,
                            MemoryUtil.memAddressSafe(bindingData),
                            bindingCount,
                            MemoryUtil.memAddressSafe(selected),
                            selections.size(),
                            MemoryUtil.memAddressSafe(retired),
                            removed.length);
            Set<Long> rejected = storage.rejected;
            rejected.clear();
            if (refused != null)
                for (long id : refused) {
                    rejected.add(id);
                    PersistentInstances.refused(id);
                }
            for (int i : updated) {
                Object owner = i < owners.size() ? owners.get(i) : null;
                if (owner != null
                        && (rejected.isEmpty()
                                || !rejected.contains(records.getLong(i * RECORD_BYTES + 8)))
                        && (PersistentInstances.known(records.getLong(i * RECORD_BYTES + 8))
                                || admitted.contains(records.getLong(i * RECORD_BYTES + 8))))
                    PersistentInstances.accepted(
                            records.getLong(i * RECORD_BYTES + 8),
                            owner,
                            records,
                            i * RECORD_BYTES,
                            drawGroups.get(i));
            }
            PersistentInstances.removed(removed);
            PersistentInstances.selected(selections.size());
        } finally {
            // Weak lifetime tracking must not retire a sampled source before JNI finishes.
            java.lang.ref.Reference.reachabilityFence(selections);
            java.lang.ref.Reference.reachabilityFence(owners);
            storage.updated.clear();
            storage.admitted.clear();
            storage.rejected.clear();
        }
    }

    static void appendRecord(ByteBuffer source, int at, ByteBuffer destination) {
        int position = destination.position();
        // memAddress(buffer) already includes its position. Use the bounded bulk
        // operation so a packed offset can neither be applied twice nor overrun storage.
        destination.put(position, source, at, RECORD_BYTES);
        destination.position(position + RECORD_BYTES);
    }

    @Override
    public void close() {
        if (closed) return;
        reset();
        closed = true;
        if (storage.reusable && storage.bytes() <= 4 * 1024 * 1024) {
            int cached;
            do {
                cached = FREE_COUNT.get();
                if (cached >= 2) {
                    storage.close();
                    return;
                }
            } while (!FREE_COUNT.compareAndSet(cached, cached + 1));
            FREE.offer(storage);
        } else storage.close();
    }

    private void requireOpen() {
        if (closed) throw new IllegalStateException("Instance batch is closed");
    }

    private static int grownCapacity(int required) {
        if (required <= 0) return 0;
        int power = Integer.highestOneBit(required - 1);
        return power > 0 && power < 1 << 30 ? power << 1 : required;
    }

    private static ByteBuffer reserve(ByteBuffer buffer, int bytes) {
        if (bytes < 0) throw new IllegalArgumentException("Negative staging size");
        if (buffer == null && bytes == 0) return null;
        if (buffer == null || buffer.capacity() < bytes) {
            ByteBuffer replacement = MemoryUtil.memAlloc(grownCapacity(bytes));
            MemoryUtil.memFree(buffer);
            buffer = replacement;
        }
        return buffer.clear().limit(bytes);
    }

    private static LongBuffer reserve(LongBuffer buffer, int count) {
        if (buffer == null && count == 0) return null;
        if (buffer == null || buffer.capacity() < count) {
            LongBuffer replacement = MemoryUtil.memAllocLong(grownCapacity(count));
            MemoryUtil.memFree(buffer);
            buffer = replacement;
        }
        return buffer.clear().limit(count);
    }

    /** Exclusive during submission; all source owners are cleared before returning to the pool. */
    private static final class Storage implements AutoCloseable {
        boolean reusable = true;
        ByteBuffer records, bindings, changes, utf8;
        LongBuffer selected, retired;
        PointerBuffer names;
        final List<String> encodedNames = new ArrayList<>();
        final List<Integer> updated = new ArrayList<>();
        final Set<Long> admitted = new HashSet<>(), rejected = new HashSet<>();
        final Map<String, Integer> groupIndices = new HashMap<>();
        final List<String> groups = new ArrayList<>(), drawGroups = new ArrayList<>();
        final Set<TextureTasks.Owner> textures = new LinkedHashSet<>();
        final Map<Long, List<LayerBinding>> layerBindings = new LinkedHashMap<>();
        final List<Object> owners = new ArrayList<>();
        final List<Selection> selections = new ArrayList<>();

        void names(List<String> groups) {
            if (names == null || names.capacity() < groups.size()) {
                PointerBuffer replacement =
                        groups.isEmpty()
                                ? null
                                : MemoryUtil.memAllocPointer(grownCapacity(groups.size()));
                MemoryUtil.memFree(names);
                names = replacement;
            }
            if (names != null) names.clear().limit(groups.size());
            if (encodedNames.equals(groups)) return;
            int bytes = 0;
            for (String group : groups)
                bytes = Math.addExact(bytes, MemoryUtil.memLengthUTF8(group, true));
            utf8 = reserve(utf8, bytes);
            int at = 0;
            for (int i = 0; i < groups.size(); i++) {
                names.put(i, MemoryUtil.memAddress(utf8) + at);
                at += MemoryUtil.memUTF8(groups.get(i), true, utf8, at);
            }
            encodedNames.clear();
            encodedNames.addAll(groups);
        }

        long bytes() {
            return capacity(records)
                    + capacity(bindings)
                    + capacity(changes)
                    + capacity(utf8)
                    + (selected == null ? 0 : selected.capacity() * 8L)
                    + (retired == null ? 0 : retired.capacity() * 8L)
                    + (names == null ? 0 : names.capacity() * (long) PointerBuffer.POINTER_SIZE);
        }

        private static long capacity(ByteBuffer buffer) {
            return buffer == null ? 0 : buffer.capacity();
        }

        public void close() {
            MemoryUtil.memFree(records);
            MemoryUtil.memFree(bindings);
            MemoryUtil.memFree(changes);
            MemoryUtil.memFree(utf8);
            MemoryUtil.memFree(selected);
            MemoryUtil.memFree(retired);
            MemoryUtil.memFree(names);
        }
    }
}
