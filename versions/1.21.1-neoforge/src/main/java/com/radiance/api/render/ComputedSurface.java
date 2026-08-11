package com.radiance.api.render;

import com.radiance.api.render.internal.ComputedSurfaceNative;
import com.radiance.client.proxy.vulkan.TextureProxy;
import com.radiance.client.texture.TextureTasks;
import java.nio.ByteBuffer;
import java.util.List;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.system.MemoryUtil;

/**
 * Source-owned compute data. Programs use the documented, fixed surface ABI; they do not execute a
 * foreign raster pipeline. Immutable inputs and every dispatch retain their GPU owners.
 */
public final class ComputedSurface implements AutoCloseable {
    private final long generation;
    private long handle;

    private ComputedSurface(long generation, long handle) {
        if (handle == 0) throw new IllegalStateException("Surface resource creation failed");
        this.generation = generation;
        this.handle = handle;
    }

    private static WorldScene.StageContext stage() {
        var stage = WorldScene.currentStage();
        if (stage == null || !WorldScene.isDefaultTarget())
            throw new IllegalStateException("Computed surfaces require a live world stage");
        return stage;
    }

    public static ComputedSurface program(byte[] spirv) {
        long generation = stage().frame().resourceGeneration();
        return new ComputedSurface(generation, ComputedSurfaceNative.program(generation, spirv));
    }

    public static ComputedSurface data(ByteBuffer input) {
        if (!input.isDirect() || input.remaining() == 0 || input.remaining() % 4 != 0)
            throw new IllegalArgumentException("Surface data must contain aligned direct words");
        long generation = stage().frame().resourceGeneration();
        return new ComputedSurface(
                generation,
                ComputedSurfaceNative.data(
                        generation, MemoryUtil.memAddress(input), input.remaining()));
    }

    /** Zero vertices/indices updates a field only; otherwise writes one fixed-topology mesh. */
    public void submit(
            ComputedSurface source,
            ComputedSurface contacts,
            ByteBuffer parameters,
            TextureTasks.Owner noise,
            TextureTasks.Owner field,
            int work,
            int vertices,
            int indices,
            int identity,
            Vec3 origin,
            TextureTasks.Owner... materialImages) {
        var stage = stage();
        if (handle == 0
                || source.handle == 0
                || contacts.handle == 0
                || generation != stage.frame().resourceGeneration()
                || source.generation != generation
                || contacts.generation != generation
                || !parameters.isDirect()
                || parameters.remaining() == 0
                || parameters.remaining() > 4096
                || parameters.remaining() % 16 != 0
                || work <= 0
                || vertices < 0
                || indices < 0
                || (vertices == 0) != (indices == 0)
                || !Double.isFinite(origin.x)
                || !Double.isFinite(origin.y)
                || !Double.isFinite(origin.z))
            throw new IllegalArgumentException("Invalid computed surface submission");
        boolean[] accepted = {false};
        var dependencies = new java.util.ArrayList<TextureTasks.Owner>(2 + materialImages.length);
        dependencies.add(noise);
        dependencies.add(field);
        dependencies.addAll(List.of(materialImages));
        boolean live =
                TextureProxy.TASKS.useAll(
                        dependencies,
                        () ->
                                accepted[0] =
                                        ComputedSurfaceNative.submit(
                                                stage.frame().worldToken(),
                                                stage.frame().frameToken(),
                                                generation,
                                                stage.stageToken(),
                                                stage.stage().ordinal(),
                                                handle,
                                                source.handle,
                                                contacts.handle,
                                                MemoryUtil.memAddress(parameters),
                                                parameters.remaining(),
                                                noise.id(),
                                                field.id(),
                                                work,
                                                vertices,
                                                indices,
                                                identity,
                                                origin.x,
                                                origin.y,
                                                origin.z));
        if (!live || !accepted[0])
            throw new IllegalStateException("Computed surface submission refused");
    }

    public void close() {
        if (handle != 0) {
            ComputedSurfaceNative.release(generation, handle);
            handle = 0;
        }
    }
}
