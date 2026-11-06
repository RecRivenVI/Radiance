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
    private static final Set<Owner> CHECK = Collections.newSetFromMap(new IdentityHashMap<>());
    private static final List<Snapshot> RESIDENT_SELECTIONS = new ArrayList<>();
    // Capture moves an owner from the preceding frame to this frame in O(1).
    // End-of-frame work then visits only unseen sources and explicit transfers/removals.
    private static Owner seenHead, seenTail, unseenHead;
    private static long frame, retained, staged, completed;

    /** Equality key of a source whose unchanged publication may rest without being polled. */
    public interface StableInputs {
        /** True only when every change of this key is announced through {@link #wake}. */
        boolean restable();
    }

    // A source that keeps repeating its accepted publication rests: native keeps its instances
    // selected and the frame neither polls nor reselects it. Members rest one by one, grouped by
    // compiled section. Source events wake a section; a staggered poll bounds the delay of a
    // change that no event announced.
    private static final String REST_MODE =
            Objects.requireNonNullElse(System.getenv("RADIANCE_J1_BLOCK_ENTITY_REST"), "0");
    private static final boolean REST = REST_MODE.equals("1") || REST_MODE.equals("verify");
    private static final boolean REST_VERIFY = REST_MODE.equals("verify");
    private static final int REST_POLL = restPoll();
    private static final int REST_FRAMES = 3, REST_TICKS = 5;
    private static final Map<List<BlockEntity>, Section> SECTIONS = new IdentityHashMap<>();
    private static final it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap<Section> SECTION_KEYS =
            new it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap<>();
    private static final it.unimi.dsi.fastutil.longs.LongArrayList WAKES =
            new it.unimi.dsi.fastutil.longs.LongArrayList();
    private static final List<Snapshot> PROBED = new ArrayList<>();
    private static final Map<String, long[]> REST_MISSES =
            new java.util.concurrent.ConcurrentSkipListMap<>();
    private static final Map<String, long[]> REST_BLOCKERS =
            new java.util.concurrent.ConcurrentSkipListMap<>();
    private static long restSuspendedFrames;
    private static final ClassValue<Boolean> PLAIN_OUTLINE =
            new ClassValue<>() {
                @Override
                protected Boolean computeValue(Class<?> type) {
                    try {
                        return type.getMethod(
                                        "hasCustomOutlineRendering",
                                        net.minecraft.world.entity.player.Player.class)
                                .getDeclaringClass()
                                .isInterface();
                    } catch (NoSuchMethodException e) {
                        return false;
                    }
                }
            };
    private static final List<Owner> CANDIDATES = new ArrayList<>();
    private static Section current;
    private static int currentSeen, currentStart;
    private static boolean currentProbe, restSuspended, restApply;
    private static volatile boolean restReset;
    private static long restFrame, restTick, restGroups;
    private static long restRetained, restMembers, restSkipped, restProbes, restProbeFailures;
    private static long restEventWakes, restRetains, restReleases;
    private static java.nio.LongBuffer restIds;

    private static final class Section {
        final List<BlockEntity> list;
        final long key, group;
        final List<Owner> rested = new ArrayList<>();
        // Members still visited every frame; null while nobody rests.
        List<BlockEntity> active;
        long seen, quietTick = Long.MIN_VALUE / 2;
        int members;
        boolean woken;

        Section(List<BlockEntity> list, long key, long group) {
            this.list = list;
            this.key = key;
            this.group = group;
        }
    }

    private static int restPoll() {
        try {
            return Math.max(
                    1,
                    Integer.parseInt(
                            Objects.requireNonNullElse(
                                    System.getenv("RADIANCE_J1_POLL_FRAMES"), "4")));
        } catch (NumberFormatException e) {
            return 4;
        }
    }

    private BlockEntityPublications() {}

    public static boolean resting() {
        return REST;
    }

    /** Announces that the source at this position, or one that reads it, may have changed. */
    public static void wake(BlockPos pos) {
        if (!REST) return;
        int x = pos.getX(), y = pos.getY(), z = pos.getZ();
        synchronized (WAKES) {
            for (int sx = x - 1 >> 4; sx <= x + 1 >> 4; sx++)
                for (int sy = y - 1 >> 4; sy <= y + 1 >> 4; sy++)
                    for (int sz = z - 1 >> 4; sz <= z + 1 >> 4; sz++)
                        WAKES.add(net.minecraft.core.SectionPos.asLong(sx, sy, sz));
        }
    }

    /** Every accepted version was dropped; resting groups must be rebuilt from new versions. */
    public static void restInvalidated() {
        restReset = true;
    }

    private static void restForgetAll() {
        for (var section : SECTIONS.values())
            for (var owner : section.rested) {
                owner.resting = false;
                owner.restSection = null;
            }
        SECTIONS.clear();
        SECTION_KEYS.clear();
        restRetained = restMembers = 0;
        com.radiance.api.render.internal.RigidSceneNative.clearRetained();
    }

    /** Opens the main view section pass. Runs on the render thread inside the scene frame. */
    public static void beginSections(
            it.unimi.dsi.fastutil.longs.Long2ObjectMap<
                            ? extends
                                    Collection<net.minecraft.server.level.BlockDestructionProgress>>
                    breaking) {
        current = null;
        PROBED.clear();
        restApply = false;
        if (!REST) return;
        restFrame++;
        restSkipped = 0;
        var level = Minecraft.getInstance().level;
        restTick = level == null ? 0 : level.getGameTime();
        if (restReset) {
            restReset = false;
            restForgetAll();
        }
        // A handoff stages or holds publications per owner; the ordinary path arbitrates those.
        restSuspended = !TRANSFERS.isEmpty();
        for (var owner : CHECK)
            if (!owner.oldHolds.isEmpty() || !owner.newHolds.isEmpty()) restSuspended = true;
        if (restSuspended) restSuspendedFrames++;
        if (restSuspended && !SECTIONS.isEmpty()) restForgetAll();
        synchronized (WAKES) {
            for (int i = 0; i < WAKES.size(); i++) {
                var section = SECTION_KEYS.get(WAKES.getLong(i));
                if (section != null) section.woken = true;
            }
            WAKES.clear();
        }
        for (var entry : breaking.long2ObjectEntrySet()) {
            if (entry.getValue() == null || entry.getValue().isEmpty()) continue;
            long position = entry.getLongKey();
            var section =
                    SECTION_KEYS.get(
                            net.minecraft.core.SectionPos.asLong(
                                    BlockPos.getX(position) >> 4,
                                    BlockPos.getY(position) >> 4,
                                    BlockPos.getZ(position) >> 4));
            if (section != null) section.woken = true;
        }
    }

    /** Returns the members of this compiled list that must be visited this frame. */
    public static List<BlockEntity> enterSection(List<BlockEntity> list) {
        if (!REST || restSuspended) return list;
        var section = SECTIONS.get(list);
        if (section == null) {
            var origin = list.get(0).getBlockPos();
            section =
                    new Section(
                            list,
                            net.minecraft.core.SectionPos.asLong(
                                    origin.getX() >> 4, origin.getY() >> 4, origin.getZ() >> 4),
                            ++restGroups);
            SECTIONS.put(list, section);
            SECTION_KEYS.put(section.key, section);
        }
        section.seen = restFrame;
        if (section.woken) {
            if (!section.rested.isEmpty()) restEventWakes++;
            release(section);
        }
        boolean poll = REST_VERIFY || (restFrame + section.group) % REST_POLL == 0;
        current = section;
        currentSeen = 0;
        currentStart = RESIDENT_SELECTIONS.size();
        currentProbe = poll && !section.rested.isEmpty();
        CANDIDATES.clear();
        PROBED.clear();
        if (section.rested.isEmpty() || poll) return list;
        restSkipped += section.rested.size();
        return section.active;
    }

    public static void leaveSection() {
        var section = current;
        if (section == null) return;
        if (currentProbe) {
            if (currentSeen == section.rested.size()) restProbes++;
            else failProbe(null);
        }
        PROBED.clear();
        if (!CANDIDATES.isEmpty()) rest(section);
        current = null;
    }

    /** Ends the section pass and releases every group whose member list left the view. */
    public static void endSections() {
        current = null;
        PROBED.clear();
        CANDIDATES.clear();
        if (!REST || restSuspended) return;
        var sections = SECTIONS.values().iterator();
        while (sections.hasNext()) {
            var section = sections.next();
            if (section.seen == restFrame) continue;
            release(section);
            sections.remove();
            SECTION_KEYS.remove(section.key, section);
        }
        restApply = true;
    }

    private static void failProbe(BlockEntity cause) {
        // The group no longer speaks for this section; its confirmed members select normally.
        for (var snapshot : PROBED)
            if (!snapshot.draws.isEmpty()) RESIDENT_SELECTIONS.add(snapshot);
        PROBED.clear();
        currentProbe = false;
        restProbeFailures++;
        REST_MISSES
                .computeIfAbsent(
                        cause == null ? "(member skipped)" : cause.getClass().getName(),
                        ignored -> new long[1])[0]++;
        release(current);
    }

    /**
     * Records one member evaluation. Returns true when the section group already selects this owner
     * for the frame, so the caller must not select it again.
     */
    private static boolean restVisit(Owner owner, BlockEntity entity, boolean ok, String reason) {
        if (owner.resting) {
            var home = owner.restSection;
            if (home == current && currentProbe) {
                if (ok) {
                    currentSeen++;
                    return true;
                }
                failProbe(entity);
            } else {
                // Another path evaluated a resting member: its home group gives way next frame.
                home.woken = true;
            }
            if (!ok) owner.restStreak = 0;
            return false;
        }
        if (current == null) return false;
        if (!ok) {
            owner.restStreak = 0;
            REST_BLOCKERS
                    .computeIfAbsent(
                            reason + ": " + entity.getClass().getName(), ignored -> new long[1])[
                    0]++;
            return false;
        }
        if (owner.restStreak++ == 0) owner.restSince = restTick;
        if (owner.restStreak >= REST_FRAMES
                && restTick - Math.max(owner.restSince, current.quietTick) >= REST_TICKS)
            CANDIDATES.add(owner);
        return false;
    }

    private static void rest(Section section) {
        int count = 0;
        for (var owner : section.rested) count += owner.snapshot.draws.size();
        for (var owner : CANDIDATES) count += owner.snapshot.draws.size();
        if (count > 65536) {
            CANDIDATES.clear();
            return;
        }
        for (var owner : CANDIDATES) {
            owner.resting = true;
            owner.restSection = section;
            section.rested.add(owner);
        }
        restRetained += CANDIDATES.size();
        restRetains += CANDIDATES.size();
        CANDIDATES.clear();
        // The group selects the new members from this frame on.
        RESIDENT_SELECTIONS
                .subList(currentStart, RESIDENT_SELECTIONS.size())
                .removeIf(
                        snapshot ->
                                snapshot.publication != null
                                        && snapshot.publication.resting
                                        && snapshot.publication.restSection == section);
        if (count != 0) {
            if (restIds == null || restIds.capacity() < count)
                restIds =
                        java.nio.ByteBuffer.allocateDirect(Math.max(1024, count * 2) * Long.BYTES)
                                .order(java.nio.ByteOrder.nativeOrder())
                                .asLongBuffer();
            restIds.clear();
            for (var owner : section.rested)
                for (var draw : owner.snapshot.draws) restIds.put(draw.submittedIdentity());
            com.radiance.api.render.internal.RigidSceneNative.retain(
                    section.group, org.lwjgl.system.MemoryUtil.memAddress0(restIds), count);
        } else if (section.members != 0)
            com.radiance.api.render.internal.RigidSceneNative.retain(section.group, 0, 0);
        restMembers += count - section.members;
        section.members = count;
        var active = new ArrayList<BlockEntity>();
        for (var entity : section.list) {
            var owner = OWNERS.get(entity);
            if (owner == null || !owner.resting || owner.restSection != section) active.add(entity);
        }
        section.active = active;
    }

    private static void release(Section section) {
        if (!section.rested.isEmpty()) {
            for (var owner : section.rested) {
                owner.resting = false;
                owner.restSection = null;
            }
            restRetained -= section.rested.size();
            section.rested.clear();
            restReleases++;
        }
        if (section.members != 0) {
            com.radiance.api.render.internal.RigidSceneNative.retain(section.group, 0, 0);
            restMembers -= section.members;
            section.members = 0;
        }
        section.active = null;
        section.woken = false;
        section.quietTick = restTick;
    }

    /**
     * Sections tracked, resting members, resting instances, members skipped this frame, passed
     * polls, failed polls, event wakes, members rested, section releases.
     */
    public static long[] restCounters() {
        return new long[] {
            SECTIONS.size(),
            restRetained,
            restMembers,
            restSkipped,
            restProbes,
            restProbeFailures,
            restEventWakes,
            restRetains,
            restReleases
        };
    }

    /** Sources whose change reached a resting section through the poll instead of an event. */
    /** Member evaluations that kept a section awake, by reason and source; whole process. */
    public static Map<String, Long> restBlockers() {
        var result = new TreeMap<String, Long>();
        REST_BLOCKERS.forEach((name, count) -> result.put(name, count[0]));
        result.put("(suspended frames)", restSuspendedFrames);
        result.put("(frames)", restFrame);
        return result;
    }

    public static Map<String, Long> restMisses() {
        var result = new TreeMap<String, Long>();
        REST_MISSES.forEach((name, count) -> result.put(name, count[0]));
        return result;
    }

    private static final class Owner {
        final BlockEntity entity;
        final Object source;
        final Set<Object> oldHolds = Collections.newSetFromMap(new IdentityHashMap<>());
        final Set<Object> newHolds = Collections.newSetFromMap(new IdentityHashMap<>());
        Snapshot snapshot;
        Object stableInputs;
        long seen;
        boolean removed, publicationDue;
        Section restSection;
        long restSince;
        int restStreak;
        boolean resting;
        Owner previous, next;
        boolean listed;

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
        Owner publication;
        final EntityRenderDataList data = new EntityRenderDataList();
        boolean fontInvalidated;

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
            if (data.isEmpty() && (!rigid || draws.isEmpty())) return;
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
            fontInvalidated = true;
            wake(owner.getBlockPos());
            for (var entity : data)
                entity.removeIf(
                        layer -> {
                            if (!layers.contains(layer.renderLayer())) return false;
                            MESHES.remove(layer.builtBuffer());
                            layer.builtBuffer().close();
                            return true;
                        });
            draws.removeIf(
                    draw -> {
                        if (!layers.contains(draw.layer())) return false;
                        // The accepted board can outlive its CPU recipe. Retire only glyph draws.
                        PersistentInstances.remove(draw.submittedIdentity(), owner);
                        return true;
                    });
            for (var layer : layers) FontAtlasLifecycle.release(this, fontPages, layer);
        }

        void replay() {
            boolean resident = draws.stream().allMatch(RigidModelCapture.Draw::resident);
            if (resident) {
                if (!draws.isEmpty()) RESIDENT_SELECTIONS.add(this);
                submit(false);
            } else {
                if (lease == null) lease = RigidModelCapture.retainPublication(draws);
                else lease.retain(draws);
                submit(true);
            }
        }
    }

    public static void beginFrame() {
        RESIDENT_SELECTIONS.clear();
        if (seenTail != null) {
            seenTail.next = unseenHead;
            if (unseenHead != null) unseenHead.previous = seenTail;
            unseenHead = seenHead;
        }
        seenHead = seenTail = null;
        frame++;
    }

    private static void unlink(Owner owner) {
        if (!owner.listed) return;
        if (owner.previous != null) owner.previous.next = owner.next;
        else if (seenHead == owner) seenHead = owner.next;
        else unseenHead = owner.next;
        if (owner.next != null) owner.next.previous = owner.previous;
        if (seenTail == owner) seenTail = owner.previous;
        owner.previous = owner.next = null;
        owner.listed = false;
    }

    private static void seen(Owner owner) {
        if (owner.listed && owner.seen == frame) return;
        unlink(owner);
        owner.next = seenHead;
        if (seenHead == null) seenTail = owner;
        else seenHead.previous = owner;
        seenHead = owner;
        owner.listed = true;
        owner.seen = frame;
    }

    private static void forget(Owner owner) {
        unlink(owner);
        if (owner.resting) owner.restSection.woken = true;
        if (owner.snapshot != null) owner.snapshot.close();
        if (owner.removed) retire(owner.entity);
        OWNERS.remove(owner.entity);
    }

    public static boolean owns(MeshData mesh) {
        return MESHES.contains(mesh);
    }

    public static boolean owns(StorageVertexConsumerProvider storage) {
        return STORAGES.contains(storage);
    }

    /** Only complete, accepted resident draws can bypass the source on a later frame. */
    public static boolean selectStable(BlockEntity entity, Object inputs) {
        if (inputs == null) return false;
        var owner = OWNERS.get(entity);
        if (owner == null || owner.removed || !inputs.equals(owner.stableInputs)) return false;
        var snapshot = owner.snapshot;
        if (snapshot == null || snapshot.fontInvalidated || !snapshot.data.isEmpty()) return false;
        for (var draw : snapshot.draws) if (!draw.resident()) return false;
        for (var transfer : TRANSFERS) transfer.enroll(owner);
        seen(owner);
        owner.publicationDue = false;
        boolean rested =
                (current != null || owner.resting)
                        && restVisit(
                                owner,
                                entity,
                                inputs instanceof StableInputs rest
                                        && rest.restable()
                                        && owner.newHolds.isEmpty()
                                        && owner.oldHolds.isEmpty()
                                        && PLAIN_OUTLINE.get(entity.getClass()),
                                "stable, not restable");
        if (!owner.newHolds.isEmpty()) staged++;
        else if (rested) PROBED.add(snapshot);
        else if (!snapshot.draws.isEmpty()) RESIDENT_SELECTIONS.add(snapshot);
        return true;
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
        if (current != null || owner.resting) restVisit(owner, entity, false, "produced");
        for (var transfer : TRANSFERS) transfer.enroll(owner);
        var next = new Snapshot(entity, storage, rigid, body, mask);
        next.publication = owner;
        if (owner.snapshot != null) owner.snapshot.close();
        owner.snapshot = next;
        owner.stableInputs = storage.blockEntityStableInputs;
        seen(owner);
        owner.removed = entity.isRemoved();
        if (owner.removed) CHECK.add(owner);
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
        CHECK.add(owner);
        wake(entity.getBlockPos());
        return true;
    }

    public static void reconcileRemovals() {
        for (var owner : CHECK) if (owner.removed && owner.oldHolds.isEmpty()) retire(owner.entity);
    }

    private static void retire(BlockEntity entity) {
        PersistentInstances.removeOwner(entity);
        if (entity instanceof SourceBindings.Owner source) source.radiance$bindings().retire();
    }

    /**
     * Runs after both world and external BERs. Active sources keep evaluating their real animation.
     */
    public static void endFrame() {
        var pending = CHECK.iterator();
        while (pending.hasNext()) {
            var owner = pending.next();
            if ((!owner.oldHolds.isEmpty() || owner.publicationDue)
                    && owner.seen != frame
                    && owner.snapshot != null) {
                owner.snapshot.replay();
                retained++;
            }
            owner.publicationDue = false;
            if (owner.oldHolds.isEmpty()
                    && owner.newHolds.isEmpty()
                    && (owner.removed || owner.seen != frame)) {
                forget(owner);
                pending.remove();
            } else if (owner.oldHolds.isEmpty() && owner.newHolds.isEmpty()) {
                pending.remove();
            }
        }
        while (unseenHead != null) {
            var owner = unseenHead;
            unlink(owner);
            if (owner.stableInputs == null && owner.oldHolds.isEmpty() && owner.newHolds.isEmpty())
                forget(owner);
        }
        if (!RESIDENT_SELECTIONS.isEmpty()) {
            try (var batch = new LocalModelBatch(0)) {
                for (var snapshot : RESIDENT_SELECTIONS) {
                    for (var draw : snapshot.draws) {
                        if (draw.resident()) batch.select(draw.submittedIdentity(), snapshot.owner);
                    }
                }
                if (batch.count() != 0) batch.submit();
            } finally {
                RESIDENT_SELECTIONS.clear();
            }
        }
        if (restApply) {
            restApply = false;
            if (restMembers != 0 && !restReset)
                com.radiance.api.render.internal.RigidSceneNative.selectRetained();
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
        held.forEach(
                o -> {
                    o.oldHolds.add(token);
                    CHECK.add(o);
                });
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
                    CHECK.add(owner);
                    expected.put(owner, target);
                }
            }
            if (owner.source == to && destination.contains(position)) {
                owner.newHolds.add(this);
                held.add(owner);
                CHECK.add(owner);
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
                if (replacement == null
                        || replacement.snapshot == null
                        || replacement.snapshot.fontInvalidated
                        || replacement.removed) return false;
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
        CHECK.clear();
        RESIDENT_SELECTIONS.clear();
        current = null;
        PROBED.clear();
        CANDIDATES.clear();
        restApply = false;
        restReset = true;
        seenHead = seenTail = unseenHead = null;
        MESHES.clear();
        STORAGES.clear();
    }
}
