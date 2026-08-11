package com.radiance.mixins.vulkan_render_integration.accessor;

import com.radiance.adapter.vanilla.VanillaLayerReplacement;
import net.minecraft.client.model.*;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.world.entity.*;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.*;

@Mixin(HumanoidArmorLayer.class)
public interface ArmorLayerStateAccessor extends VanillaLayerReplacement.ArmorAccess {
    @Accessor("innerModel")
    HumanoidModel<?> radiance$inner();

    @Accessor("outerModel")
    HumanoidModel<?> radiance$outer();

    @Accessor("armorTrimAtlas")
    TextureAtlas radiance$trimAtlas();

    @Invoker("setPartVisibility")
    void radiance$visibility(HumanoidModel<?> model, EquipmentSlot slot);

    @Invoker("getArmorModelHook")
    Model radiance$armorHook(
            LivingEntity entity, ItemStack stack, EquipmentSlot slot, HumanoidModel<?> model);
}
