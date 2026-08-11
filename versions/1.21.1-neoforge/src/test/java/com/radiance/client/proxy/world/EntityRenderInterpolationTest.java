package com.radiance.client.proxy.world;

import static org.junit.jupiter.api.Assertions.*;

import com.mojang.blaze3d.vertex.PoseStack;
import com.radiance.client.UnsafeManager;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import net.minecraft.SharedConstants;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.decoration.Painting;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.entity.vehicle.Minecart;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.loading.LoadingModList;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;

class EntityRenderInterpolationTest {
    private static final int LIGHT = 0x00D000B0;

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        LoadingModList.of(List.of(), List.of(), List.of(), List.of(), Map.of());
        Bootstrap.bootStrap();
    }

    @Test
    void actualWorldDispatchMatchesSourceInterpolationForDifferentEntityFamilies()
            throws Exception {
        RecordingDispatcher dispatcher = allocate(RecordingDispatcher.class);
        LevelRenderer source = allocate(LevelRenderer.class);
        var field = LevelRenderer.class.getDeclaredField("entityRenderDispatcher");
        field.setAccessible(true);
        field.set(source, dispatcher);
        Method original =
                LevelRenderer.class.getDeclaredMethod(
                        "renderEntity",
                        Entity.class,
                        double.class,
                        double.class,
                        double.class,
                        float.class,
                        PoseStack.class,
                        MultiBufferSource.class);
        original.setAccessible(true);
        PoseStack pose = new PoseStack();
        MultiBufferSource buffers =
                ignored -> {
                    throw new AssertionError("No draw expected");
                };

        for (Class<? extends Entity> type :
                List.of(
                        Boat.class,
                        Minecart.class,
                        Painting.class,
                        Display.TextDisplay.class,
                        Cow.class)) {
            Entity entity = entity(type);
            for (float[] yaw : new float[][] {{20, 100}, {359, 361}, {-181, -179}, {170, -170}}) {
                entity.yRotO = yaw[0];
                entity.setYRot(yaw[1]);
                for (float partialTick : new float[] {0, .125f, .25f, .5f, .75f, .875f, 1}) {
                    original.invoke(
                            source, entity, 100.0, 64.0, -100.0, partialTick, pose, buffers);
                    float expectedYaw = dispatcher.yaw;
                    EntityProxy.renderEntityAtOrigin(
                            dispatcher, entity, partialTick, pose, buffers, LIGHT);
                    assertEquals(
                            expectedYaw,
                            dispatcher.yaw,
                            0,
                            type.getSimpleName() + " at " + partialTick);
                    assertSame(entity, dispatcher.entity);
                    assertSame(pose, dispatcher.pose);
                    assertSame(buffers, dispatcher.buffers);
                    assertEquals(partialTick, dispatcher.partialTick);
                    assertEquals(LIGHT, dispatcher.light);
                    assertArrayEquals(new double[] {0, 0, 0}, dispatcher.position);
                }
            }
        }
    }

    @Test
    void turningBoatProducesNewPosesBetweenTicksAndFrozenTimeStaysFrozen() throws Exception {
        Boat boat = entity(Boat.class);
        boat.yRotO = 15;
        boat.setYRot(31);
        RecordingDispatcher dispatcher = allocate(RecordingDispatcher.class);
        var distinct = new HashSet<Float>();
        PoseStack pose = new PoseStack();
        for (int frame = 0; frame <= 16; ++frame) {
            EntityProxy.renderEntityAtOrigin(dispatcher, boat, frame / 16f, pose, null, LIGHT);
            distinct.add(dispatcher.yaw);
            assertEquals(15 + frame, dispatcher.yaw, 0);
        }
        assertEquals(17, distinct.size());
        EntityProxy.renderEntityAtOrigin(dispatcher, boat, 0, pose, null, LIGHT);
        assertEquals(15, dispatcher.yaw, 0);
        EntityProxy.renderEntityAtOrigin(dispatcher, boat, 0, pose, null, LIGHT);
        assertEquals(15, dispatcher.yaw, 0);
    }

    @Test
    void ordinaryAndTextDisplayBranchesBothUseTheTestedDispatch() throws Exception {
        ClassNode proxy = new ClassNode();
        try (var bytes = EntityProxy.class.getResourceAsStream("EntityProxy.class")) {
            assertNotNull(bytes);
            new ClassReader(bytes).accept(proxy, 0);
        }
        var queue =
                proxy.methods.stream()
                        .filter(m -> m.name.equals("queueEntitiesBuild"))
                        .findFirst()
                        .orElseThrow();
        int shared = 0;
        for (var instruction : queue.instructions) {
            if (!(instruction instanceof MethodInsnNode call)) continue;
            assertFalse(
                    call.owner.equals("net/minecraft/client/renderer/entity/EntityRenderDispatcher")
                            && call.name.equals("render"),
                    "Do not bypass interpolated dispatch");
            if (call.owner.equals("com/radiance/client/proxy/world/EntityProxy")
                    && call.name.equals("renderEntityAtOrigin")) ++shared;
        }
        assertEquals(2, shared);
    }

    private static <T> T allocate(Class<T> type) {
        return UnsafeManager.INSTANCE.allocateInstance(type);
    }

    private static <T extends Entity> T entity(Class<T> type) throws Exception {
        T entity = allocate(type);
        var position = Entity.class.getDeclaredField("position");
        position.setAccessible(true);
        position.set(entity, new Vec3(101, 65, -99));
        entity.xOld = 100;
        entity.yOld = 64;
        entity.zOld = -100;
        return entity;
    }

    // No client, textures or native device are constructed; only the real dispatch arguments
    // are intercepted. Both the installed LevelRenderer and product path call this receiver.
    private static class RecordingDispatcher extends EntityRenderDispatcher {
        Entity entity;
        float yaw, partialTick;
        int light;
        double[] position;
        PoseStack pose;
        MultiBufferSource buffers;

        private RecordingDispatcher() {
            super(null, null, null, null, null, null, null);
        }

        @Override
        public <E extends Entity> int getPackedLightCoords(E entity, float partialTick) {
            return LIGHT;
        }

        @Override
        public <E extends Entity> void render(
                E entity,
                double x,
                double y,
                double z,
                float yaw,
                float partialTick,
                PoseStack pose,
                MultiBufferSource buffers,
                int light) {
            this.entity = entity;
            this.position = new double[] {x, y, z};
            this.yaw = yaw;
            this.partialTick = partialTick;
            this.pose = pose;
            this.buffers = buffers;
            this.light = light;
        }
    }
}
