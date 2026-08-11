package com.radiance.client.proxy.world;

import static org.junit.jupiter.api.Assertions.*;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class PlayerSectionUpdatesTest {
    @AfterEach
    void clearWorld() {
        PlayerSectionUpdates.clear();
    }

    @Test
    void boundaryDependenciesCoverAllAxesWithoutPrioritizingUnrelatedSections() {
        var pos = new BlockPos(15, 15, 15);
        long id = PlayerSectionUpdates.beginAt(pos, pos.above(), 100);
        for (int x : new int[] {0, 16})
            for (int y : new int[] {0, 16})
                for (int z : new int[] {0, 16}) {
                    assertEquals(id, PlayerSectionUpdates.requestAt(new BlockPos(x, y, z), 101));
                }
        assertEquals(0, PlayerSectionUpdates.requestAt(new BlockPos(32, 0, 0), 101));
        assertEquals(0, PlayerSectionUpdates.requestAt(new BlockPos(-16, 0, 0), 101));
    }

    @Test
    void newActionExpiryWorldChangeAndCapacityHaveBoundedOwnership() {
        var pos = new BlockPos(8, 8, 8);
        long first = PlayerSectionUpdates.beginAt(pos, pos, 100);
        long second = PlayerSectionUpdates.beginAt(pos, pos, 200);
        assertNotEquals(first, second);
        assertEquals(second, PlayerSectionUpdates.requestAt(pos, 201));
        assertEquals(0, PlayerSectionUpdates.requestAt(pos, 2_000_000_201L));
        for (int i = 1; i < 140; i++) {
            var next = new BlockPos(i * 32 + 8, 8, 8);
            PlayerSectionUpdates.beginAt(next, next, 201);
        }
        assertEquals(0, PlayerSectionUpdates.requestAt(pos, 202));
        PlayerSectionUpdates.clear();
        assertEquals(0, PlayerSectionUpdates.requestAt(new BlockPos(139 * 32 + 8, 8, 8), 202));
    }
}
