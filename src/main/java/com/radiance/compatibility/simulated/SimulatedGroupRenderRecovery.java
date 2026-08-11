package com.radiance.compatibility.simulated;

import com.mojang.blaze3d.systems.RenderSystem;
import com.radiance.mixins.compatibility.flywheel.FlywheelRenderSystemLightsAccessor;
import com.radiance.mixins.compatibility.simulated.SimulatedGroupBlockEntityCameraAccessor;
import com.radiance.mixins.compatibility.simulated.SimulatedGroupVisualizationAccessor;
import com.radiance.mixins.compatibility.simulated.SimulatedGroupBufferSourceAccessor;
import com.radiance.compatibility.veil.VeilAdapter;
import dev.engine_room.flywheel.api.visualization.VisualizationManager;
import dev.ryanhcode.sable.mixinterface.BlockEntityRenderDispatcherExtension;
import dev.simulated_team.simulated.mixin_interface.diagram.VisualizationManagerExtension;
import dev.simulated_team.simulated.util.SimpleSubLevelGroupRenderer;
import java.util.ArrayDeque;
import java.util.Deque;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.joml.Vector3f;

/** Restores state even when the original group throws before entering its own finally block. */
public final class SimulatedGroupRenderRecovery {
    private static final ThreadLocal<Deque<State>> STATES = ThreadLocal.withInitial(ArrayDeque::new);
    private SimulatedGroupRenderRecovery() {}

    public static void run(ClientLevel level, float partialTick, Runnable draw) {
        Minecraft minecraft = Minecraft.getInstance();
        SimulatedRenderRecovery recovery = new SimulatedRenderRecovery();
        Matrix4fStack stack = RenderSystem.getModelViewStack();
        Matrix4f modelView = new Matrix4f(stack);
        Matrix4f projection = new Matrix4f(RenderSystem.getProjectionMatrix());
        Runnable restoreVeilCamera = VeilAdapter.captureCameraMatrices();
        boolean simple = SimpleSubLevelGroupRenderer.RENDERING_SIMPLE;
        var dispatcher = minecraft.getBlockEntityRenderDispatcher();
        var camera = ((SimulatedGroupBlockEntityCameraAccessor) dispatcher).radiance$getSublevelCamera();
        VisualizationManager manager = VisualizationManager.get(level);
        boolean drawingDiagram = manager instanceof VisualizationManagerExtension
            && SimulatedGroupVisualizationAccessor.radiance$isDrawingDiagram();
        Vector3f[] lights = FlywheelRenderSystemLightsAccessor.radiance$getShaderLightDirections();
        Vector3f first = new Vector3f(lights[0]);
        Vector3f second = new Vector3f(lights[1]);
        State state = new State();
        boolean[] completed = {false};
        recovery.add(() -> {
            if (!completed[0] && state.buffersReady) {
                var buffers = (SimulatedGroupBufferSourceAccessor)
                    minecraft.renderBuffers().bufferSource();
                buffers.radiance$getStartedBuilders().clear();
                buffers.radiance$setLastSharedType(null);
                SimulatedRenderRecovery discard = new SimulatedRenderRecovery();
                discard.add(() -> buffers.radiance$getSharedBuffer().discard());
                buffers.radiance$getFixedBuffers().values().forEach(buffer -> discard.add(buffer::discard));
                discard.run(() -> {});
            }
        });
        recovery.add(() -> { if (state.shader != null) state.shader.clear(); });
        recovery.add(() -> { if (state.layer != null) state.layer.clearRenderState(); });
        recovery.add(() -> SimpleSubLevelGroupRenderer.RENDERING_SIMPLE = simple);
        recovery.add(() -> {
            if (manager instanceof VisualizationManagerExtension extension)
                extension.sable$setDrawingDiagram(drawingDiagram);
        });
        recovery.add(() -> ((BlockEntityRenderDispatcherExtension) dispatcher)
            .sable$setCameraPosition(camera));
        recovery.add(() -> {
            while (state.pushes > 0) { stack.popMatrix(); state.pushes--; }
            stack.set(modelView);
            RenderSystem.applyModelViewMatrix();
        });
        recovery.add(() -> minecraft.gameRenderer.resetProjectionMatrix(projection));
        recovery.add(restoreVeilCamera);
        recovery.add(() -> RenderSystem.setShaderLights(first, second));
        recovery.add(() -> {
            if (!completed[0]) minecraft.gameRenderer.lightTexture().updateLightTexture(partialTick);
        });
        SimulatedFramebufferRecovery.capture(recovery);
        STATES.get().push(state);
        try {
            recovery.run(() -> { draw.run(); completed[0] = true; });
        } finally {
            STATES.get().pop();
            if (STATES.get().isEmpty()) STATES.remove();
        }
    }

    public static void pushed() { STATES.get().peek().pushes++; }
    public static void popped() { STATES.get().peek().pushes--; }
    public static void layer(RenderType layer) { STATES.get().peek().layer = layer; }
    public static void shader(ShaderInstance shader) { STATES.get().peek().shader = shader; }
    public static void buffersReady() { STATES.get().peek().buffersReady = true; }

    private static final class State {
        int pushes;
        RenderType layer;
        ShaderInstance shader;
        boolean buffersReady;
    }
}
