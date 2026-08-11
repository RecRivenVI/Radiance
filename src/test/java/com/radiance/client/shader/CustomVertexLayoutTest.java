package com.radiance.client.shader;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.Test;

class CustomVertexLayoutTest {

    @Test
    void encodesTheFixedSableTwoBindingLayout() {
        assertArrayEquals(new int[]{0, 8, 0, 1, 8, 1},
            CustomVertexLayout.SABLE_FANCY.bindingData());
        assertArrayEquals(new int[]{
                0, 0, 3, CustomVertexLayout.BYTE, 0, 0, 0,
                1, 0, 3, CustomVertexLayout.BYTE, 0, 0, 4,
                2, 1, 2, CustomVertexLayout.UNSIGNED_INT, 0, 1, 0},
            CustomVertexLayout.SABLE_FANCY.attributeData());
    }

    @Test
    void rejectsAttributesThatReferenceMissingBindings() {
        assertThrows(IllegalArgumentException.class, () -> new CustomVertexLayout(
            List.of(new CustomVertexLayout.Binding(0, 8, false)),
            List.of(new CustomVertexLayout.Attribute(0, 1, 3,
                CustomVertexLayout.BYTE, false, false, 0))));
    }
}
