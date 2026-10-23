package com.radiance.render.contract;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class MeshSubmissionTest {
    @Test
    void inputRangeMustCoverAllDeclaredVerticesBeforeNativeCopy() {
        var program = new TakeoverSubmission.Program(1, 1);
        var state = DrawStateSnapshot.IDENTITY;
        var input = new MeshSubmission(2, 3, 4, 5, 6, 128, 4, 4096, 512, program, state);
        assertEquals(512, input.bytes());
        assertThrows(
                IllegalArgumentException.class,
                () -> new MeshSubmission(2, 3, 4, 5, 6, 128, 4, 4096, 511, program, state));
        assertThrows(
                IllegalArgumentException.class,
                () -> new MeshSubmission(2, 3, 4, 5, 6, 128, 4, 0, 512, program, state));
    }
}
