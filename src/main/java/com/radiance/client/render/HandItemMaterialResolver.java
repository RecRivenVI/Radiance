package com.radiance.client.render;

import com.radiance.client.vertex.PBRMaterialContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.data.ModelData;

/** Resolves a held block item's PBR material from its authoritative world model layers. */
public final class HandItemMaterialResolver {

    private HandItemMaterialResolver() {
    }

    public static PBRMaterialContext.Scope enter(ItemStack stack) {
        if (!(stack.getItem() instanceof BlockItem blockItem)) {
            return PBRMaterialContext.pushBlockTransmission(false);
        }

        BlockState state = blockItem.getBlock().defaultBlockState();
        BakedModel worldModel = Minecraft.getInstance().getBlockRenderer().getBlockModel(state);
        boolean transmission = usesWorldTransmission(
            worldModel.getRenderTypes(state, RandomSource.create(42L), ModelData.EMPTY));
        return PBRMaterialContext.pushBlockTransmission(transmission);
    }

    static boolean usesWorldTransmission(Iterable<RenderType> renderTypes) {
        return containsIdentity(renderTypes, RenderType.translucent());
    }

    static <T> boolean containsIdentity(Iterable<T> values, T expected) {
        for (T value : values) {
            if (value == expected) {
                return true;
            }
        }
        return false;
    }
}
