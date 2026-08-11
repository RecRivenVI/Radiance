package com.radiance.audit;

import com.mojang.logging.LogUtils;
import com.radiance.client.proxy.world.ChunkProxy;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.ViewArea;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import org.slf4j.Logger;

/** Finds non-empty Minecraft sections that remain absent from the native chunk scene. */
public final class ChunkHoleTracker {
    public static final ChunkHoleTracker INSTANCE = new ChunkHoleTracker();
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final long SAMPLE_INTERVAL_NANOS = TimeUnit.SECONDS.toNanos(1);
    private static final long PERSISTENCE_NANOS = TimeUnit.SECONDS.toNanos(2);
    private static final long REPEAT_INTERVAL_NANOS = TimeUnit.SECONDS.toNanos(10);

    private final Map<String, MissingSection> missing = new HashMap<>();
    private Object levelIdentity;
    private Object viewAreaIdentity;
    private long nextSampleNanos;

    private ChunkHoleTracker() {
    }

    public void sample(ClientLevel level, ViewArea viewArea) {
        long now = System.nanoTime();
        if (level == null || viewArea == null || viewArea.sections == null
            || now < nextSampleNanos) {
            return;
        }
        nextSampleNanos = now + SAMPLE_INTERVAL_NANOS;
        if (levelIdentity != level || viewAreaIdentity != viewArea) {
            missing.clear();
            levelIdentity = level;
            viewAreaIdentity = viewArea;
        }

        Set<String> observed = new HashSet<>();
        int nonEmpty = 0;
        int nativeMissing = 0;
        int persistent = 0;
        for (SectionRenderDispatcher.RenderSection section : viewArea.sections) {
            if (section == null) continue;
            BlockPos origin = section.getOrigin();
            int sectionX = SectionPos.blockToSectionCoord(origin.getX());
            int sectionY = SectionPos.blockToSectionCoord(origin.getY());
            int sectionZ = SectionPos.blockToSectionCoord(origin.getZ());
            ChunkAccess chunk = level.getChunk(sectionX, sectionZ, ChunkStatus.FULL, false);
            if (chunk == null) continue;
            int levelIndex = level.getSectionIndexFromSectionY(sectionY);
            if (levelIndex < 0 || levelIndex >= chunk.getSections().length
                || chunk.getSection(levelIndex).hasOnlyAir()) {
                continue;
            }
            nonEmpty++;
            boolean ready;
            try {
                ready = ChunkProxy.isChunkReady(section);
            } catch (RuntimeException | LinkageError exception) {
                return;
            }
            if (ready) continue;

            nativeMissing++;
            String key = level.dimension().location() + ":" + sectionX + ":" + sectionY + ":"
                + sectionZ + ":" + section.index;
            observed.add(key);
            MissingSection state = missing.computeIfAbsent(key,
                ignored -> new MissingSection(now));
            long age = now - state.firstSeenNanos;
            if (age < PERSISTENCE_NANOS) continue;
            persistent++;
            if (now - state.lastReportedNanos < REPEAT_INTERVAL_NANOS) continue;
            state.lastReportedNanos = now;
            String detail = "dimension=" + level.dimension().location()
                + "; section=" + sectionX + ',' + sectionY + ',' + sectionZ
                + "; origin=" + origin.getX() + ',' + origin.getY() + ',' + origin.getZ()
                + "; nativeId=" + section.index
                + "; dirty=" + section.isDirty()
                + "; playerDirty=" + section.isDirtyFromPlayer()
                + "; compiled=" + compiledName(section)
                + "; missingMs=" + TimeUnit.NANOSECONDS.toMillis(age);
            LOGGER.warn("Persistent Radiance chunk hole candidate: {}", detail);
            AuditHooks.event("CHUNK_HOLE", "ChunkHoleTracker", detail, "OBSERVED",
                "NATIVE_SCENE");
        }
        missing.keySet().removeIf(key -> !observed.contains(key));
        if (nativeMissing != 0 || persistent != 0) {
            AuditHooks.event("CHUNK_HOLE_SUMMARY", "ChunkHoleTracker",
                "nonEmpty=" + nonEmpty + "; nativeMissing=" + nativeMissing
                    + "; persistent=" + persistent,
                "SNAPSHOT", "NATIVE_SCENE");
        }
    }

    private static String compiledName(SectionRenderDispatcher.RenderSection section) {
        SectionRenderDispatcher.CompiledSection compiled = section.getCompiled();
        if (compiled == SectionRenderDispatcher.CompiledSection.UNCOMPILED) return "UNCOMPILED";
        if (compiled == SectionRenderDispatcher.CompiledSection.EMPTY) return "EMPTY";
        return compiled.getClass().getName();
    }

    private static final class MissingSection {
        private final long firstSeenNanos;
        private long lastReportedNanos;

        private MissingSection(long firstSeenNanos) {
            this.firstSeenNanos = firstSeenNanos;
        }
    }
}
