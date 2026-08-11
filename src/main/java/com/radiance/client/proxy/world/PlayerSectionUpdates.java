package com.radiance.client.proxy.world;

import com.radiance.api.audit.RenderAuditBridge;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;

/**
 * Short-lived correlation of real local actions with their normal dependency
 * invalidations.
 */
public final class PlayerSectionUpdates {
    private record Request(long id, long expires) {}
    private static final Map<Long, Request> recent = new LinkedHashMap<>();
    private static final AtomicLong sequence = new AtomicLong();
    private PlayerSectionUpdates() {}
    public static synchronized void begin(BlockPos first, BlockPos second) {
        beginAt(first, second, System.nanoTime());
    }
    static synchronized long beginAt(BlockPos first, BlockPos second, long now) {
        long id = sequence.incrementAndGet();
        recent.values().removeIf(r -> r.expires < now);
        mark(first, id, now);
        mark(second, id, now);
        while (recent.size() > 128) recent.remove(recent.keySet().iterator().next());
        if (RenderAuditBridge.accepts("CHUNK_UPDATE"))
            RenderAuditBridge.counter("CHUNK_UPDATE", "ACTION", id,
                "java_ns=" + now + "; first=" + first + "; second=" + second);
        return id;
    }
    private static void mark(BlockPos pos, long id, long now) {
        for (int x = -1; x <= 1; x++)
            for (int y = -1; y <= 1; y++)
                for (int z = -1; z <= 1; z++) {
                    long section = SectionPos.asLong(
                        (pos.getX() + x) >> 4, (pos.getY() + y) >> 4, (pos.getZ() + z) >> 4);
                    recent.put(section, new Request(id, now + 2_000_000_000L));
                }
    }
    public static synchronized long request(BlockPos origin) {
        return requestAt(origin, System.nanoTime());
    }
    static synchronized long requestAt(BlockPos origin, long now) {
        Request request = recent.get(SectionPos.asLong(origin));
        return request != null && request.expires >= now ? request.id : 0;
    }
    public static synchronized void clear() {
        recent.clear();
    }
}
