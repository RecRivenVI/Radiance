package com.radiance.client.render;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class NameTagBackgroundStyleTest {

    @Test
    void appliesFiftyPercentOpacityAndPreservesRgb() {
        assertEquals(0x80123456, NameTagBackgroundStyle.applyOpacity(0x40123456));
    }
}
