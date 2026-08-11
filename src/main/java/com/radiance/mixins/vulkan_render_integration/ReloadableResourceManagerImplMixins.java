package com.radiance.mixins.vulkan_render_integration;

import com.radiance.client.shader.ShaderRegistry;
import com.radiance.client.proxy.world.CloudProxy;
import com.radiance.client.texture.ResourceReloadCoordinator;
import com.radiance.client.texture.ClientResourceScope;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.server.packs.resources.ReloadInstance;
import net.minecraft.server.packs.resources.ReloadableResourceManager;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Unit;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ReloadableResourceManager.class)
public class ReloadableResourceManagerImplMixins {

    private boolean radiance$isClientResourceManager() {
        return ClientResourceScope.isClientManager(
            Minecraft.getInstance().getResourceManager(), this);
    }

    @Inject(method = "createReload", at = @At("HEAD"))
    private void beginVulkanResourceReload(Executor prepareExecutor, Executor applyExecutor,
        CompletableFuture<Unit> initialStage, List<PackResources> packs,
        CallbackInfoReturnable<ReloadInstance> cir) {
        ClientResourceScope.runIfClientManager(Minecraft.getInstance().getResourceManager(), this,
            ResourceReloadCoordinator::begin);
    }

    @Inject(
        method = "registerReloadListener(Lnet/minecraft/server/packs/resources/PreparableReloadListener;)V",
        at = @At("HEAD")
    )
    public void addInfo(PreparableReloadListener reloader, CallbackInfo ci) {
//        if (reloader == null) {
//            System.out.println("Reloader: null");
//        } else {
//            System.out.println("Reloader: " + reloader.getClass()
//                .getName());
//        }
    }

    @Inject(method = "createReload", at = @At("RETURN"))
    private void clearShaderCache(Executor prepareExecutor, Executor applyExecutor,
        CompletableFuture<Unit> initialStage, List<PackResources> packs,
        CallbackInfoReturnable<ReloadInstance> cir) {
        if (!radiance$isClientResourceManager()) {
            return;
        }
        ShaderRegistry.clear();
        cir.getReturnValue().done().whenCompleteAsync((unused, failure) -> {
            ResourceReloadCoordinator.complete(failure);
            CloudProxy.markTextureDirty();
        }, applyExecutor);
    }
}
