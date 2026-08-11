package com.radiance.compatibility.veil;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.radiance.compatibility.simulated.SpringShaderVerification;
import foundry.veil.impl.client.render.shader.program.ShaderProgramImpl;
import it.unimi.dsi.fastutil.ints.Int2ObjectArrayMap;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class SpringShaderVerificationTest {
    @Test
    void realReplacementOrderTransfersSameIdAndDifferentIdWithoutLosingOtherOwners() {
        Object owner = new Object(), otherOwner = new Object();
        Object oldModel = new Object(), sameIdModel = new Object(), differentIdModel = new Object();
        var programs = new Int2ObjectArrayMap<ShaderProgramImpl.CompiledProgram>();
        programs.put(0, compiled(77));
        Object[] active = {oldModel};
        Map<Integer, Object> modelById = new HashMap<>();
        modelById.put(77, oldModel);
        SpringShaderVerification.verified(oldModel);
        SpringShaderVerification.attached(owner, oldModel);
        var events = new ArrayList<String>();

        // Compilation precedes replaceProgram. A failed compile never releases the old owner.
        assertThrows(
                IllegalStateException.class,
                () -> {
                    ShaderProgramImpl.CompiledProgram failed = failedCompile();
                    VeilShaderAdapter.replaceProgram(
                            programs,
                            0,
                            failed,
                            ignored -> {},
                            ignored -> {},
                            ignored -> {},
                            ignored -> {});
                });
        assertSame(oldModel, active[0]);
        assertEquals(77, programs.get(0).program());
        assertDoesNotThrow(SpringShaderVerification::requireActive);

        // Native may return the same ID for a new, source-identical compiled model.
        modelById.put(77, sameIdModel);
        SpringShaderVerification.verified(sameIdModel);
        var sameId = compiled(77);
        VeilShaderAdapter.replaceProgram(
                programs,
                0,
                sameId,
                applied -> events.add("apply:" + applied.program()),
                released -> {
                    events.add("release:" + released);
                    SpringShaderVerification.released(owner, active[0]);
                    active[0] = null;
                },
                attached -> {
                    events.add("attach:" + attached);
                    active[0] = modelById.get(attached);
                    SpringShaderVerification.attached(owner, active[0]);
                },
                freed -> events.add("free:" + freed.program()));
        assertEquals(java.util.List.of("release:77", "free:77", "attach:77", "apply:77"), events);
        assertSame(sameId, programs.get(0));
        assertSame(sameIdModel, active[0]);
        assertDoesNotThrow(SpringShaderVerification::requireActive);

        modelById.put(88, differentIdModel);
        SpringShaderVerification.verified(differentIdModel);
        VeilShaderAdapter.replaceProgram(
                programs,
                0,
                compiled(88),
                ignored -> {},
                released -> {
                    SpringShaderVerification.released(owner, active[0]);
                    active[0] = null;
                },
                attached -> {
                    active[0] = modelById.get(attached);
                    SpringShaderVerification.attached(owner, active[0]);
                },
                ignored -> {});
        assertEquals(88, programs.get(0).program());
        assertSame(differentIdModel, active[0]);

        SpringShaderVerification.attached(otherOwner, sameIdModel);
        SpringShaderVerification.released(owner, differentIdModel);
        assertDoesNotThrow(SpringShaderVerification::requireActive);
        SpringShaderVerification.released(otherOwner, sameIdModel);
        assertThrows(IllegalStateException.class, SpringShaderVerification::requireActive);
    }

    private static ShaderProgramImpl.CompiledProgram failedCompile() {
        throw new IllegalStateException("expected compile failure");
    }

    private static ShaderProgramImpl.CompiledProgram compiled(int id) {
        return new ShaderProgramImpl.CompiledProgram(
                id, new Int2ObjectArrayMap<>(), new Int2ObjectArrayMap<>(), null, Set.of(), 0);
    }
}
