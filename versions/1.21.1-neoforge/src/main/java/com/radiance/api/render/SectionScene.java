package com.radiance.api.render;

import com.radiance.client.proxy.world.ChunkProxy;
import java.util.*;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import net.minecraft.core.BlockPos;
import org.joml.Matrix4d;
import org.joml.Vector3dc;

/**
 * Source-owned section membership; compilation, backpressure and publication belong to the
 * platform.
 */
public final class SectionScene implements AutoCloseable {
    private final RenderSpace space;
    private final Vector3dc origin;
    private final Map<SectionRenderDispatcher.RenderSection, BlockPos> members =
            new IdentityHashMap<>();
    private boolean closed;
    private long epoch = Long.MIN_VALUE;
    private long priorityVersion = Long.MIN_VALUE;

    public SectionScene(RenderSpace space, Vector3dc origin) {
        this.space = Objects.requireNonNull(space);
        this.origin = new org.joml.Vector3d(origin);
    }

    public void members(Collection<? extends SectionRenderDispatcher.RenderSection> sections) {
        if (closed) throw new IllegalStateException("Section scene closed");
        enterEpoch(ChunkProxy.sectionEpoch());
        Set<SectionRenderDispatcher.RenderSection> current =
                Collections.newSetFromMap(new IdentityHashMap<>());
        current.addAll(sections);
        for (var section : List.copyOf(members.keySet())) {
            if (!current.contains(section)) {
                ChunkProxy.releaseExternalSection(section);
                members.remove(section);
            }
        }
        for (var section : current) {
            BlockPos position = section.getOrigin().immutable();
            if (position.equals(members.get(section))) continue;
            var local =
                    new Matrix4d()
                            .translation(
                                    position.getX() - origin.x(),
                                    position.getY() - origin.y(),
                                    position.getZ() - origin.z());
            ChunkProxy.bindExternalSection(section, space, local);
            members.put(section, position);
        }
        epoch = ChunkProxy.sectionEpoch();
    }

    boolean enterEpoch(long current) {
        if (epoch == current) return false;
        // A source callback can precede prepareFrame after reload. Equal source members still
        // need new platform registrations; do not acknowledge the new epoch with old bindings.
        members.clear();
        epoch = current;
        return true;
    }

    public void prepareFrame() {
        if (closed) throw new IllegalStateException("Section scene closed");
        if (epoch != ChunkProxy.sectionEpoch()) {
            var sections = List.copyOf(members.keySet());
            members.clear();
            members(sections);
        }
        long next = space.version();
        if (priorityVersion == next) return;
        // Moving a source changes queued priorities, not its geometry or request age.
        for (var section : members.keySet()) ChunkProxy.refreshExternalPriority(section);
        priorityVersion = next;
    }

    public int size() {
        return members.size();
    }

    void hold(long handoff) {
        if (closed) throw new IllegalStateException("Section scene closed");
        prepareFrame();
        for (var section : members.keySet()) ChunkProxy.holdSection(handoff, section);
    }

    @Override
    public void close() {
        if (closed) return;
        members.keySet().forEach(ChunkProxy::releaseExternalSection);
        members.clear();
        closed = true;
    }
}
