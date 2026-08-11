package com.radiance.compatibility.neoforge;

import com.radiance.client.render.RenderCaptureContract;
import com.radiance.client.render.WorldMeshSink;
import java.util.Objects;
import java.util.function.Supplier;
import net.neoforged.neoforge.client.extensions.IDimensionSpecialEffectsExtension;

/**
 * Scope adapter for NeoForge's IDimensionSpecialEffectsExtension callbacks.
 *
 * <p>Dimension effects are invoked by the Radiance LevelRenderer replacement,
 * before/after the standard RenderLevelStageEvent sequence.  A callback may
 * return {@code true} after drawing custom sky/cloud/weather content, but that
 * return value does not provide a MeshData-to-TLAS contract.  Callers that can
 * wrap an extension invocation must use this adapter; BufferUploader draws
 * inside the scope are consequently rejected until a world bridge exists.</p>
 */
public final class DimensionSpecialEffectsCompatibility {

    public record InvocationResult<T>(T value, int acceptedWorldMeshes) {
    }

    private DimensionSpecialEffectsCompatibility() {
    }

    public static <T> InvocationResult<T> invoke(IDimensionSpecialEffectsExtension extension,
        String operation, Supplier<T> renderer) {
        Objects.requireNonNull(extension, "extension");
        Objects.requireNonNull(renderer, "renderer");
        String label = "IDimensionSpecialEffectsExtension."
            + Objects.requireNonNullElse(operation, "unknown")
            + "@" + extension.getClass().getName();
        WorldMeshSink.StageKey stage = WorldMeshSink.StageKey.from(operation);
        WorldMeshSink.TargetKind target = stage == WorldMeshSink.StageKey.DIMENSION_RENDER_SKY
            ? WorldMeshSink.TargetKind.SKYBOX : WorldMeshSink.TargetKind.DEFAULT_WORLD;
        WorldMeshSink.CoordinateSpace coordinate = target == WorldMeshSink.TargetKind.SKYBOX
            ? WorldMeshSink.CoordinateSpace.SKYBOX : WorldMeshSink.CoordinateSpace.CAMERA_SHIFT;
        try (WorldMeshSink.StageToken worldStage = WorldMeshSink.enterStage(stage, coordinate,
                 net.minecraft.world.phys.Vec3.ZERO, target, label);
             RenderCaptureContract.ScopeToken ignored = RenderCaptureContract.enter(
                 RenderCaptureContract.ScopeKind.DIMENSION_EFFECT, label)) {
            T value = renderer.get();
            worldStage.commit();
            return new InvocationResult<>(value, worldStage.acceptedCount());
        }
    }
}
