package com.radiance.api.render;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

class SectionShapeNotificationTest {
    @Test
    void notifiesShapeOwnersWithoutASectionObjectAndFloorsNegativeCoordinates() throws Exception {
        var field = RenderPlatform.class.getDeclaredField("sources");
        field.setAccessible(true);
        Object previous = field.get(null);
        var seen = new ArrayList<BlockPos>();
        SourceIntegration.World observer =
                new SourceIntegration.World() {
                    public String id() {
                        return "radiance:shape-test";
                    }

                    public void sectionInvalidated(BlockPos origin) {
                        seen.add(origin);
                    }
                };
        try {
            field.set(null, List.of(observer));
            SourceHooks.invalidateSectionShape(new BlockPos(-1, 75, 17));
            SourceHooks.invalidateSectionShape(new BlockPos(-17, -1, -16));
            assertEquals(List.of(new BlockPos(-16, 64, 16), new BlockPos(-32, -16, -16)), seen);
        } finally {
            field.set(null, previous);
        }
    }
}
