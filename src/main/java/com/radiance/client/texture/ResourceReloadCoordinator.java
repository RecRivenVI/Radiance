package com.radiance.client.texture;

import com.radiance.client.RadianceClient;
import com.radiance.client.proxy.vulkan.RendererProxy;
import com.radiance.client.shader.ShaderRegistry;
import com.radiance.client.render.WorldMeshSink;
import java.util.HashSet;
import java.util.Set;

/**
 * Publishes a resource-pack reload to Vulkan as one texture generation.
 *
 * <p>Minecraft may rebuild the base atlas and its derived PBR images through separate listeners.
 * Keeping the old native resources alive until every listener has completed prevents descriptor
 * tables from observing a mixture of the old and new generations.</p>
 */
public final class ResourceReloadCoordinator {

    private static final Set<Integer> preparedAuxiliaryTextures = new HashSet<>();
    private static int activeReloads;
    private static boolean nativeTransactionActive;

    private ResourceReloadCoordinator() {
    }

    public static void begin() {
        // The same order as texture uploads: native owner monitor, then reload state.
        synchronized (com.radiance.client.proxy.vulkan.TextureProxy.class) {
            synchronized (ResourceReloadCoordinator.class) {
                if (activeReloads++ != 0) return;
                boolean shaderGenerationStarted = false;
                try {
                    preparedAuxiliaryTextures.clear();
                    com.radiance.client.proxy.vulkan.TextureProxy.TASKS.invalidateUploads();
                    WorldMeshSink.invalidateResources();
                    com.radiance.compatibility.simulated.DiagramSectionMeshes.invalidateResources();
                    ShaderRegistry.beginReloadGeneration();
                    shaderGenerationStarted = true;
                    nativeTransactionActive = RendererProxy.beginResourceReload();
                } catch (RuntimeException | Error failure) {
                    activeReloads = 0;
                    nativeTransactionActive = false;
                    preparedAuxiliaryTextures.clear();
                    if (shaderGenerationStarted) {
                        try { ShaderRegistry.finishReloadGeneration(false); }
                        catch (Throwable cleanup) { failure.addSuppressed(cleanup); }
                    }
                    throw failure;
                }
            }
        }
    }

    public static synchronized boolean claimAuxiliaryTexture(int textureId) {
        return activeReloads > 0 && preparedAuxiliaryTextures.add(textureId);
    }

    public static synchronized void markAuxiliaryTexturePrepared(int textureId) {
        if (activeReloads > 0) {
            preparedAuxiliaryTextures.add(textureId);
        }
    }

    public static void complete(Throwable failure) {
        boolean finishNative;
        synchronized (ResourceReloadCoordinator.class) {
            if (activeReloads == 0 || --activeReloads != 0) {
                return;
            }
            finishNative = nativeTransactionActive;
            nativeTransactionActive = false;
            preparedAuxiliaryTextures.clear();
        }
        ShaderRegistry.finishReloadGeneration(failure == null);

        try {
            RendererProxy.endResourceReload(finishNative);
        } catch (RuntimeException exception) {
            if (failure != null) {
                exception.addSuppressed(failure);
            }
            throw exception;
        }

        if (failure == null) {
            ShaderRegistry.warmupAfterReload();
        } else {
            RadianceClient.LOGGER.warn(
                "Resource reload failed after Vulkan resources were returned to a coherent generation",
                failure);
        }
    }
}
