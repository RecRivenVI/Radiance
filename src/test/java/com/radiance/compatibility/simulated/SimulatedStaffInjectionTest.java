package com.radiance.compatibility.simulated;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Path;
import java.util.zip.ZipFile;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;

/** Checks the actual pinned producer bytecode; runtime Mixin application remains a separate gate. */
class SimulatedStaffInjectionTest {
    @Test void beamCallbackMatchesStaticProducerAndActualInvocation() throws Exception {
        var producer = new ClassNode();
        try (var jar = new ZipFile(Path.of("build/radiance-compatibility-compile",
            "6.0.10+mc1.21.1_2.0.5+mc1.21.1_1.3.2+mc1.21.1_4.3.2",
            "dev.simulated_team.simulated.simulated-neoforge-1.21.1-1.3.2.jar").toFile())) {
            var entry = jar.getEntry("dev/simulated_team/simulated/content/physics_staff/PhysicsStaffClientHandler.class");
            assertNotNull(entry);
            try (var in = jar.getInputStream(entry)) { new ClassReader(in).accept(producer, 0); }
        }
        var target = producer.methods.stream().filter(m -> m.name.equals("lambda$onRender$3"))
            .findFirst().orElseThrow();
        var mixin = new ClassNode();
        try (var in = getClass().getClassLoader().getResourceAsStream(
            "com/radiance/mixins/compatibility/simulated/SimulatedStaffBeamMixins.class")) {
            assertNotNull(in);
            new ClassReader(in).accept(mixin, 0);
        }
        var callback = mixin.methods.stream().filter(m -> m.name.equals("radiance$captureBeam"))
            .findFirst().orElseThrow();
        assertEquals(target.access & Opcodes.ACC_STATIC, callback.access & Opcodes.ACC_STATIC);
        long calls = 0;
        for (var instruction : target.instructions) {
            if (instruction instanceof MethodInsnNode call && call.name.equals("render") &&
                call.owner.equals("dev/simulated_team/simulated/content/physics_staff/PhysicsStaffClientHandler$PhysicsBeam")) {
                assertEquals("(Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/Vec3;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/createmod/catnip/render/SuperRenderTypeBuffer;Lnet/minecraft/world/phys/Vec3;F)V", call.desc);
                calls++;
            }
        }
        assertEquals(1, calls);
    }
}
