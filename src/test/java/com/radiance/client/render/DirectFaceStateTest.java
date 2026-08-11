package com.radiance.client.render;

import static org.junit.jupiter.api.Assertions.*;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class DirectFaceStateTest {
    @Test void catnipStencilSequenceReachesVulkanStateWithoutOpenGl() {
        List<String> events = new ArrayList<>();
        // StencilElement.prepareStencil / prepareElement / cleanUp.
        for (boolean enabled : new boolean[]{false, true, true, false})
            DirectFaceState.setEnabled(2960, enabled, (cap, on) -> events.add(cap + ":" + on));
        assertEquals(List.of("2960:false", "2960:true", "2960:true", "2960:false"), events);
        assertThrows(UnsupportedOperationException.class, () -> DirectFaceState.glEnable(-1));
        assertThrows(UnsupportedOperationException.class, () -> DirectFaceState.glDisable(-1));
    }
}
