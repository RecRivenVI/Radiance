package com.radiance.mixins.vanilla_resource_tracker;

import com.radiance.client.texture.ClientResourceScope;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.FallbackResourceManager;
import net.minecraft.server.packs.resources.Resource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(FallbackResourceManager.class)
public abstract class NamespaceResourceManagerMixins {

    @Shadow
    @Final
    private PackType type;

    @Inject(method = "getResource(Lnet/minecraft/resources/ResourceLocation;)Ljava/util/Optional;",
        at = @At("RETURN"), cancellable = true)
    private void addIdentifierToSingleResource(ResourceLocation id,
        CallbackInfoReturnable<Optional<Resource>> cir) {
        cir.setReturnValue(cir.getReturnValue().map(
            resource -> ClientResourceScope.wrapClientTexture(type, id, resource)));
    }

    @Inject(method = "listResources", at = @At("RETURN"))
    private void addIdentifiersToListedResources(String path,
        java.util.function.Predicate<ResourceLocation> filter,
        CallbackInfoReturnable<Map<ResourceLocation, Resource>> cir) {
        cir.getReturnValue().replaceAll(
            (id, resource) -> ClientResourceScope.wrapClientTexture(type, id, resource));
    }

    @Inject(method = "listResourceStacks", at = @At("RETURN"))
    private void addIdentifiersToListedResourceStacks(String path,
        java.util.function.Predicate<ResourceLocation> filter,
        CallbackInfoReturnable<Map<ResourceLocation, List<Resource>>> cir) {
        cir.getReturnValue().forEach((id, resources) -> {
            for (int index = 0; index < resources.size(); index++) {
                resources.set(index,
                    ClientResourceScope.wrapClientTexture(type, id, resources.get(index)));
            }
        });
    }
}
