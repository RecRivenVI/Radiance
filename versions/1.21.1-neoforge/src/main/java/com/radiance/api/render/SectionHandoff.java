package com.radiance.api.render;

import com.radiance.client.proxy.world.ChunkProxy;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;

/** Holds current section publications until all watched replacements are GPU-ready. */
public final class SectionHandoff implements AutoCloseable {
    private static final org.slf4j.Logger LOG = com.mojang.logging.LogUtils.getLogger();
    private static long refreshedFrame = Long.MIN_VALUE;
    private final long epoch = ChunkProxy.sectionEpoch();
    private final long handle = ChunkProxy.beginHandoffNative();
    private long nextReport = System.nanoTime() + 15_000_000_000L;
    private int blockingParticipant = -1;
    private boolean sealed, nativeSealed, committed, closed;
    private final List<Participant> participants = new ArrayList<>();

    /** A source's prepared replacements; callbacks run before this frame's scene is consumed. */
    public interface Participant {
        boolean ready();

        void publish();

        /** Release holds on invalidation, including before a replacement could be prepared. */
        void cancel();
    }

    public void participant(Participant participant) {
        if (sealed || closed) throw new IllegalStateException("Handoff is not collecting members");
        participants.add(java.util.Objects.requireNonNull(participant));
    }

    public void sections(SectionScene scene) {
        if (sealed || closed) throw new IllegalStateException("Handoff is not collecting members");
        scene.hold(handle);
    }

    public void world(BlockPos minimum, BlockPos maximum) {
        if (sealed || closed) throw new IllegalStateException("Handoff is not collecting members");
        ChunkProxy.holdWorldSections(handle, minimum, maximum);
    }

    /** Discovery is not a transfer unless this area has published content being replaced. */
    public boolean worldChanges(BlockPos minimum, BlockPos maximum) {
        if (sealed || closed) throw new IllegalStateException("Handoff is not collecting members");
        return ChunkProxy.holdChangedWorldSections(handle, minimum, maximum);
    }

    public void seal() {
        sealed = true;
        prepareCommit();
    }

    /** Prepare every joined handoff before trying any commit; no participant may publish early. */
    public void prepareCommit() {
        if (!sealed || closed || nativeSealed) return;
        if (cancelled()) return;
        for (int i = 0; i < participants.size(); i++)
            if (!participants.get(i).ready()) {
                blockingParticipant = i;
                return;
            }
        blockingParticipant = -1;
        ChunkProxy.sealHandoffNative(handle);
        nativeSealed = true;
    }

    public void join(SectionHandoff other) {
        if (other.cancelled()) return;
        if (sealed || closed || other.closed)
            throw new IllegalStateException("Handoff is not collecting members");
        ChunkProxy.joinHandoffNative(handle, other.handle);
    }

    /** Called before selecting this frame's local models, never from a worker or GPU callback. */
    public boolean tryCommit() {
        if (cancelled()) return false;
        prepareCommit();
        if (committed) return true;
        if (nativeSealed && !closed) {
            long frame = WorldScene.activeFrameToken();
            if (frame < 0 || frame != refreshedFrame) {
                // A just-arrived transfer must hold its old geometry before any refresh.
                // Multiple scopes share one refresh per active world frame.
                ChunkProxy.completePendingBuilds();
                refreshedFrame = frame;
            }
        }
        if (!nativeSealed || closed || !ChunkProxy.commitHandoffNative(handle)) {
            long now = System.nanoTime();
            if (!closed && now >= nextReport) {
                nextReport = now + 30_000_000_000L;
                LOG.info("Section handoff {} still waiting: {}", handle, waitingReason());
            }
            return false;
        }
        committed = true;
        participants.forEach(Participant::publish);
        return true;
    }

    /** An expired platform epoch cancels this scope; it never addresses a new world's tokens. */
    public boolean cancelled() {
        if (!closed && epoch != ChunkProxy.sectionEpoch()) close();
        return closed && !committed;
    }

    /** On-demand diagnostics only; no per-frame traversal of held native sections. */
    public String waitingReason() {
        if (cancelled()) return "cancelled";
        if (committed) return "committed";
        return (blockingParticipant >= 0 ? "source-participant=" + blockingParticipant + "; " : "")
                + ChunkProxy.handoffStatusNative(handle);
    }

    @Override
    public void close() {
        if (closed) return;
        if (epoch == ChunkProxy.sectionEpoch()) ChunkProxy.closeHandoffNative(handle);
        closed = true;
        try {
            if (!committed) participants.forEach(Participant::cancel);
        } finally {
            participants.clear();
        }
    }
}
