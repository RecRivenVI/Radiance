package com.radiance.compatibility.veil;

import static org.junit.jupiter.api.Assertions.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class VeilStageAdapterTest {
    @Test void explicitStaffProducerRetainsItsOriginalCallbackAndBypassesRasterDeferral() {
        AtomicInteger calls = new AtomicInteger();
        var staff = VeilStageAdapter.pathTraceStaff((a,b,c,d,e,f,g,h,i,j) -> calls.incrementAndGet());
        assertInstanceOf(WorldGeometryStageListener.class, staff);
        assertSame(staff, VeilStageAdapter.wrap(staff));
        // Outside a world capture (including previews), keep the original callback unchanged.
        staff.onRenderLevelStage(null, null, null, null, null, null, 0, null, null, null);
        assertEquals(1, calls.get());
    }
}
