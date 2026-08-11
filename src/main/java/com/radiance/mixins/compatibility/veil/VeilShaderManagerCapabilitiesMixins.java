package com.radiance.mixins.compatibility.veil;

import foundry.veil.api.client.render.shader.ShaderManager;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Supplier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Removes ShaderManager's hard dependency on an OpenGL thread capability object.
 *
 * <p>Veil 4.3.2 obtains {@code GL.getCapabilities()} through a method-handle supplier and only
 * passes it to worker lambdas inside {@code CompletableFuture.supplyAsync}. Since the capability
 * method is no longer a plain {@code INVOKE}, this bridges the supplier call instead and yields
 * {@code null}, matching the previous behavior of redirecting the capability getter itself.</p>
 */
@Pseudo
@Mixin(value = ShaderManager.class, remap = false)
public abstract class VeilShaderManagerCapabilitiesMixins {

    @Redirect(method = "prepare", at = @At(value = "INVOKE",
        target = "Ljava/util/concurrent/CompletableFuture;supplyAsync(Ljava/util/function/Supplier;Ljava/util/concurrent/Executor;)Ljava/util/concurrent/CompletableFuture;"),
        remap = false)
    private static CompletableFuture<Object> radiance$prepareWithoutOpenGlCapabilities(Supplier<?> supplier,
        Executor executor) {
        return CompletableFuture.supplyAsync(() -> null, executor);
    }

    @Redirect(method = "createDynamicProgram", at = @At(value = "INVOKE",
        target = "Ljava/util/concurrent/CompletableFuture;supplyAsync(Ljava/util/function/Supplier;Ljava/util/concurrent/Executor;)Ljava/util/concurrent/CompletableFuture;"),
        remap = false)
    private static CompletableFuture<Object> radiance$dynamicWithoutOpenGlCapabilities(Supplier<?> supplier,
        Executor executor) {
        return CompletableFuture.supplyAsync(() -> null, executor);
    }
}
