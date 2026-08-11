package com.radiance.mixins.vulkan_render_integration.accessor;

import java.util.Optional;
import java.util.function.Supplier;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.ShaderInstance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(RenderStateShard.ShaderStateShard.class)
public interface RenderTypeShaderStateAccessor {

    @Accessor("shader")
    Optional<Supplier<ShaderInstance>> radiance$getShader();
}
