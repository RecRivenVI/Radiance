package com.radiance.client.proxy.world;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Comparator;
import org.junit.jupiter.api.Test;

class SectionBuildPriorityTest {
    private record Request(
            int id, SectionBuildPriority distance, long source, boolean interactive) {}

    private SectionBuildQueue<Request> queue() {
        return new SectionBuildQueue<>(
                Request::id,
                Comparator.comparing(Request::interactive)
                        .reversed()
                        .thenComparing(Request::distance),
                Request::source,
                r -> r.distance().ring(),
                r -> !r.interactive());
    }

    @Test
    void distanceIncludesHeightAndMeasuresTheSectionBoundary() {
        var below = SectionBuildPriority.at(56, 8, 8, 56, 152, 8);
        var eyeLevel = SectionBuildPriority.at(184, 152, 8, 56, 152, 8);
        assertTrue(eyeLevel.compareTo(below) < 0);
        assertEquals(0, SectionBuildPriority.at(56, 152, 8, 48, 144, 0).distanceSquared());
        assertEquals(0.25, SectionBuildPriority.at(56, 152, 8, 72, 152, 8).distanceSquared());
    }

    @Test
    void negativeAndFarOriginsRemainEquivalentAfterSpaceTransformation() {
        var primary = SectionBuildPriority.at(-8, 72, -24, -1, 75, -20);
        var source =
                SectionBuildPriority.at(
                        20_000_008.0 - 20_000_016.0, 72, 20_000_008.0 - 20_000_032.0, -1, 75, -20);
        assertEquals(primary, source);
        assertEquals(new SectionBuildPriority(0), primary);
        assertEquals(0, SectionBuildPriority.at(0, 0, 0, 16, 8, 16, 12, 0, 12).distanceSquared());
    }

    @Test
    void spawnColumnCannotBypassNearbyWorkThroughReservedBackgroundSlots() {
        var queue = queue();
        var spawn = new Request(1, SectionBuildPriority.at(-40, 216, 200, 56, 152, 8), 4, false);
        var near = new Request(2, SectionBuildPriority.at(56, 152, 24, 56, 152, 8), 0, false);
        var interaction = new Request(3, spawn.distance(), 4, true);
        queue.offer(spawn);
        queue.offer(near);
        queue.offer(interaction);
        assertEquals(interaction, queue.poll());
        assertEquals(near, queue.poll(true));
        assertEquals(spawn, queue.poll(true));
    }

    @Test
    void equalBandsRotateWorldAndMovingSourcesWithoutReorderingEachSourcesNearestTask() {
        var queue = queue();
        for (int i = 0; i < 100; i++)
            queue.offer(new Request(i, new SectionBuildPriority(.01), 7, false));
        queue.offer(new Request(100, new SectionBuildPriority(.9), 0, false));
        assertEquals(100, queue.poll().id());
        assertEquals(7, queue.poll().source());
        queue.offer(new Request(101, new SectionBuildPriority(.8), 0, false));
        assertEquals(101, queue.poll().id());
    }

    @Test
    void blockedNearOwnerDoesNotIdleTheRoundOrLoseItsRequest() {
        var queue = queue();
        var near = new Request(1, new SectionBuildPriority(0), 0, false);
        var far = new Request(2, new SectionBuildPriority(180), 7, false);
        queue.offer(near);
        queue.offer(far);
        try (var round = new ChunkBuildDispatch.Round<>(queue, 8)) {
            assertEquals(near, round.pollBackground());
            round.defer(near);
            assertEquals(far, round.pollBackground());
        }
        assertEquals(near, queue.poll());
        assertTrue(queue.isEmpty());
    }

    @Test
    void movedSourceReentersTheNearestBand() {
        var queue = queue();
        queue.offer(new Request(1, new SectionBuildPriority(180), 7, false));
        queue.offer(new Request(2, new SectionBuildPriority(1), 0, false));
        queue.update(1, r -> new Request(r.id(), new SectionBuildPriority(0), r.source(), false));
        assertEquals(1, queue.poll(true).id());
        queue.reprioritize(
                r -> new Request(r.id(), new SectionBuildPriority(0), r.source(), false));
        assertEquals(2, queue.poll().id());
    }
}
