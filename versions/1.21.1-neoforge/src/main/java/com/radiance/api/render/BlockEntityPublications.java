package com.radiance.api.render;

import com.mojang.blaze3d.vertex.MeshData;
import com.radiance.client.proxy.world.EntityProxy;
import com.radiance.client.proxy.world.EntityProxy.EntityRenderData;
import com.radiance.client.proxy.world.EntityProxy.EntityRenderDataList;
import com.radiance.client.texture.FontAtlasLifecycle;
import com.radiance.client.vertex.RigidModelCapture;
import com.radiance.client.vertex.SourceBindings;
import com.radiance.client.vertex.StorageVertexConsumerProvider;
import java.util.*;
import java.util.function.*;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * Last consumable BER publications. Only an explicit transfer can keep a removed source visible.
 */
public final class BlockEntityPublications {
    private static final Map<BlockEntity, Owner> OWNERS = new IdentityHashMap<>();
    private static final Set<Transfer> TRANSFERS =
            Collections.newSetFromMap(new IdentityHashMap<>());
    private static final Set<MeshData> MESHES = Collections.newSetFromMap(new IdentityHashMap<>());
    private static final Set<StorageVertexConsumerProvider> STORAGES =
            Collections.newSetFromMap(new IdentityHashMap<>());
    private static long frame, retained, staged, completed;

    private BlockEntityPublications() {}

    private static final class Owner {
        final BlockEntity entity;
        final Object source;
        final Set<Object> oldHolds = Collections.newSetFromMap(new IdentityHashMap<>());
        final Set<Object> newHolds = Collections.newSetFromMap(new IdentityHashMap<>());
        Snapshot snapshot;
        long seen;
        boolean removed, publicationDue;

        Owner(BlockEntity entity) {
            this.entity = entity;
            source = SourceHooks.blockSource(entity);
        }
    }

    private static final class Snapshot implements AutoCloseable, FontAtlasLifecycle.BatchOwner {
        final BlockEntity owner;
        final StorageVertexConsumerProvider storage;
        final List<RigidModelCapture.Draw> draws;
        final Map<
                        net.minecraft.client.renderer.RenderType,
                        net.minecraft.client.renderer.texture.AbstractTexture>
                fontPages = new HashMap<>();
        RigidModelCapture.PublicationLease lease;
        final EntityRenderDataList data = new EntityRenderDataList();

        Snapshot(
                BlockEntity owner,
                StorageVertexConsumerProvider storage,
                List<RigidModelCapture.Draw> rigid,
                EntityRenderData body,
                EntityRenderData mask) {
            this.owner = owner;
            this.storage = body.isEmpty() && mask.isEmpty() ? null : storage;
            this.draws = new ArrayList<>(rigid);
            if (!body.isEmpty()) data.add(body);
            if (!mask.isEmpty()) data.add(mask);
            if (this.storage != null) STORAGES.add(storage);
            for (var entity : data) for (var layer : entity) MESHES.add(layer.builtBuffer());
            for (var entity : data)
                for (var layer : entity)
                    FontAtlasLifecycle.capture(this, fontPages, layer.renderLayer());
            for (var draw : draws) FontAtlasLifecycle.capture(this, fontPages, draw.layer());
        }

        void submit(boolean rigid) {
            var geometry = new EntityRenderDataList();
            for (var entity : data) if (!entity.isEmpty()) geometry.add(entity);
            if (rigid) geometry.addRigidDraws(draws);
            EntityProxy.queueBuildWithoutClose(geometry);
        }

        public void close() {
            for (var layer : List.copyOf(fontPages.keySet()))
                FontAtlasLifecycle.release(this, fontPages, layer);
            if (storage != null) STORAGES.remove(storage);
            for (var entity : data)
                for (var layer : entity) {
                    MESHES.remove(layer.builtBuffer());
                    layer.builtBuffer().close();
                }
            if (storage != null) storage.close();
            if (lease != null) lease.close();
        }

        public void radiance$discardFontBatches(
                Set<net.minecraft.client.renderer.texture.AbstractTexture> pages) {
            var layers = new HashSet<net.minecraft.client.renderer.RenderType>();
            fontPages.forEach(
                    (layer, page) -> {
                        if (pages.contains(page)) layers.add(layer);
                    });
            if (layers.isEmpty()) return;
            for (var entity : data)
                entity.removeIf(
                        layer -> {
                            if (!layers.contains(layer.renderLayer())) return false;
                            MESHES.remove(layer.builtBuffer());
                            layer.builtBuffer().close();
                            return true;
                        });
            draws.removeIf(draw -> layers.contains(draw.layer()));
            // Retire the old glyph selections too; the next source evaluation creates fresh ones.
            PersistentInstances.removeOwner(owner);
            if (lease == null) lease = RigidModelCapture.retainPublication(draws);
            else lease.retain(draws);
            for (var layer : layers) FontAtlasLifecycle.release(this, fontPages, layer);
        }

        void replay(Object owner) {
            boolean resident = draws.stream().allMatch(RigidModelCapture.Draw::resident);
            if (resident) {
                PersistentInstances.selectOwner(owner);
                submit(false);
            } else {
                if (lease == null) lease = RigidModelCapture.retainPublication(draws);
                else lease.retain(draws);
                submit(true);
            }
        }
    }

    public static void beginFrame() {
        frame++;
    }

    public static boolean owns(MeshData mesh) {
        return MESHES.contains(mesh);
    }

    public static boolean owns(StorageVertexConsumerProvider storage) {
        return STORAGES.contains(storage);
    }

    /**
     * Borrows this frame's existing buffers; no vertex copy/hash or duplicate renderer evaluation.
     */
    public static boolean capture(
            BlockEntity entity,
            StorageVertexConsumerProvider storage,
            List<RigidModelCapture.Draw> rigid,
            EntityRenderData body,
            EntityRenderData mask) {
        var owner = OWNERS.computeIfAbsent(entity, Owner::new);
        for (var transfer : TRANSFERS) transfer.enroll(owner);
        var next = new Snapshot(entity, storage, rigid, body, mask);
        if (owner.snapshot != null) owner.snapshot.close();
        owner.snapshot = next;
        owner.seen = frame;
        owner.removed = entity.isRemoved();
        owner.publicationDue = false;
        if (!owner.newHolds.isEmpty()) {
            staged++;
            return true;
        }
        return false;
    }

    /** Delay removal until the source update has had the opportunity to declare a real transfer. */
    public static boolean removed(BlockEntity entity) {
        var owner = OWNERS.get(entity);
        if (owner == null) return false;
        owner.removed = true;
        return true;
    }

    public static void reconcileRemovals() {
        for (var owner : OWNERS.values())
            if (owner.removed && owner.oldHolds.isEmpty()) retire(owner.entity);
    }

    private static void retire(BlockEntity entity) {
        PersistentInstances.removeOwner(entity);
        if (entity instanceof SourceBindings.Owner source) source.radiance$bindings().retire();
    }

    /**
     * Runs after both world and external BERs. Active sources keep evaluating their real animation.
     */
    public static void endFrame() {
        for (var owner : OWNERS.values()) {
            if ((!owner.oldHolds.isEmpty() || owner.publicationDue)
                    && owner.seen != frame
                    && owner.snapshot != null) {
                owner.snapshot.replay(owner.entity);
                retained++;
            }
            owner.publicationDue = false;
        }
        var iterator = OWNERS.values().iterator();
        while (iterator.hasNext()) {
            var owner = iterator.next();
            if (owner.oldHolds.isEmpty()
                    && owner.newHolds.isEmpty()
                    && (owner.removed || owner.seen != frame)) {
                if (owner.snapshot != null) owner.snapshot.close();
                if (owner.removed) retire(owner.entity);
                iterator.remove();
            }
        }
        completed++;
    }

    public static SectionHandoff.Participant retainSource(
            Level level, Object source, BooleanSupplier ready) {
        var token = new Object();
        var held =
                OWNERS.values().stream()
                        .filter(o -> o.entity.getLevel() == level && o.source == source)
                        .toList();
        held.forEach(o -> o.oldHolds.add(token));
        return new SectionHandoff.Participant() {
            public boolean ready() {
                return ready.getAsBoolean();
            }

            public void publish() {
                cancel();
            }

            public void cancel() {
                held.forEach(o -> o.oldHolds.remove(token));
            }
        };
    }

    public static SectionHandoff.Participant transfer(
            Level level,
            Object from,
            Object to,
            Function<BlockPos, BlockPos> mapping,
            Collection<BlockPos> destination,
            Function<BlockPos, BlockEntity> lookup) {
        var transfer = new Transfer(level, from, to, mapping, Set.copyOf(destination), lookup);
        TRANSFERS.add(transfer);
        OWNERS.values().forEach(transfer::enroll);
        return transfer;
    }

    private static final class Transfer implements SectionHandoff.Participant {
        final Level level;
        final Object from, to;
        final Function<BlockPos, BlockPos> mapping;
        final Set<BlockPos> destination;
        final Function<BlockPos, BlockEntity> lookup;
        final Set<Owner> held = Collections.newSetFromMap(new IdentityHashMap<>());
        final Map<Owner, BlockPos> expected = new IdentityHashMap<>();
        final long before = completed;
        boolean closed;

        Transfer(
                Level level,
                Object from,
                Object to,
                Function<BlockPos, BlockPos> mapping,
                Set<BlockPos> destination,
                Function<BlockPos, BlockEntity> lookup) {
            this.level = level;
            this.from = from;
            this.to = to;
            this.mapping = mapping;
            this.destination = destination;
            this.lookup = lookup;
        }

        void enroll(Owner owner) {
            if (closed || owner.entity.getLevel() != level) return;
            var position = owner.entity.getBlockPos();
            if (owner.source == from) {
                var target = mapping.apply(position);
                if (target != null && destination.contains(target)) {
                    owner.oldHolds.add(this);
                    held.add(owner);
                    expected.put(owner, target);
                }
            }
            if (owner.source == to && destination.contains(position)) {
                owner.newHolds.add(this);
                held.add(owner);
            }
        }

        public boolean ready() {
            var dispatcher = Minecraft.getInstance().getBlockEntityRenderDispatcher();
            for (var entry : expected.entrySet()) {
                var entity = lookup.apply(entry.getValue());
                if (entity == null) {
                    if (completed > before
                            && level.hasChunkAt(entry.getValue())
                            && !level.getBlockState(entry.getValue()).hasBlockEntity()) continue;
                    return false;
                }
                if (dispatcher.getRenderer(entity) == null
                        || SourceHooks.ownsBlockEntity(
                                (net.minecraft.client.multiplayer.ClientLevel) level, entity))
                    continue;
                var replacement = OWNERS.get(entity);
                if (replacement == null || replacement.snapshot == null || replacement.removed)
                    return false;
            }
            return true;
        }

        public void publish() {
            release(true);
        }

        public void cancel() {
            release(false);
        }

        private void release(boolean publish) {
            if (closed) return;
            closed = true;
            TRANSFERS.remove(this);
            for (var owner : held) {
                owner.oldHolds.remove(this);
                owner.newHolds.remove(this);
            }
            for (var owner : held) {
                if (publish
                        && owner.source == to
                        && owner.newHolds.isEmpty()
                        && !owner.removed
                        && owner.snapshot != null) owner.publicationDue = true;
                if (owner.removed && owner.oldHolds.isEmpty()) retire(owner.entity);
            }
            held.clear();
            expected.clear();
        }
    }

    public static long[] counters() {
        return new long[] {OWNERS.size(), TRANSFERS.size(), retained, staged};
    }

    /** On-demand source census: consumable publications, retained owners, staged owners. */
    public static Map<String, long[]> census() {
        var result = new LinkedHashMap<String, long[]>();
        for (var owner : OWNERS.values()) {
            var row =
                    result.computeIfAbsent(
                            owner.entity.getClass().getName(), ignored -> new long[3]);
            if (owner.snapshot != null
                    && owner.newHolds.isEmpty()
                    && (!owner.removed || !owner.oldHolds.isEmpty())) row[0]++;
            if (!owner.oldHolds.isEmpty()) row[1]++;
            if (!owner.newHolds.isEmpty()) row[2]++;
        }
        return result;
    }

    public static void clear() {
        List.copyOf(TRANSFERS).forEach(Transfer::cancel);
        for (var owner : OWNERS.values()) if (owner.snapshot != null) owner.snapshot.close();
        OWNERS.clear();
        MESHES.clear();
        STORAGES.clear();
    }
}
