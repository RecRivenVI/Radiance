package com.radiance.adapter.vanilla;

import static net.minecraft.client.renderer.RenderStateShard.*;

import com.radiance.render.contract.SourceEmission.*;
import net.minecraft.client.renderer.RenderType;

/** Source-version adapter. Legacy unknown-program differences remain isolated under adr-5. */
public final class VanillaEmissionAdapter {
    private VanillaEmissionAdapter() {}

    public static boolean requiresEyeComposition(Iterable<?> layers) {
        return VanillaLayerAdapter.describe(layers, false, false).eyeOwner();
    }

    public static boolean requiresSurfaceLayers(Iterable<?> layers, boolean armorActive) {
        return VanillaLayerAdapter.describe(layers, armorActive, false).layers().stream()
                .anyMatch(
                        layer ->
                                layer.composition()
                                        == com.radiance.render.contract.TakeoverSubmission
                                                .Composition.SURFACE_OWNER);
    }

    public static boolean hasRenderedArmor(
            java.util.function.Function<
                            net.minecraft.world.entity.EquipmentSlot,
                            net.minecraft.world.item.ItemStack>
                    items) {
        for (var slot :
                java.util.List.of(
                        net.minecraft.world.entity.EquipmentSlot.HEAD,
                        net.minecraft.world.entity.EquipmentSlot.CHEST,
                        net.minecraft.world.entity.EquipmentSlot.LEGS,
                        net.minecraft.world.entity.EquipmentSlot.FEET)) {
            if (items.apply(slot).getItem() instanceof net.minecraft.world.item.ArmorItem armor
                    && armor.getEquipmentSlot() == slot) return true;
        }
        return false;
    }

    public static Policy policy(RenderType layer, Role role, boolean eventEntry) {
        if (role == Role.PRIORITY_OUTLINE)
            return new Policy(Reason.PRIORITY_OUTLINE, true, false, true);
        if (layer instanceof RenderType.CompositeRenderType composite) {
            var shader = composite.state.shaderState;
            boolean background =
                    shader == RENDERTYPE_TEXT_BACKGROUND_SHADER
                            || shader == RENDERTYPE_TEXT_BACKGROUND_SEE_THROUGH_SHADER;
            if (role == Role.NAME_TAG && !background)
                return new Policy(Reason.NAME_TAG, true, false, false);
            if (shader == RENDERTYPE_EYES_SHADER)
                return new Policy(
                        Reason.EYE_OVERLAY,
                        true,
                        composite.state.transparencyState == ADDITIVE_TRANSPARENCY,
                        true);
            if (shader == RENDERTYPE_ENTITY_TRANSLUCENT_EMISSIVE_SHADER
                    || shader == RENDERTYPE_ENERGY_SWIRL_SHADER
                    || shader == RENDERTYPE_BEACON_BEAM_SHADER
                    || shader == RENDERTYPE_LIGHTNING_SHADER
                    || layer == RenderType.dragonRays())
                return new Policy(Reason.SOURCE_FULLBRIGHT, true, false, true);
            if (shader == RENDERTYPE_ENTITY_ALPHA_SHADER)
                return new Policy(Reason.SOURCE_FULLBRIGHT, true, false, false);
            // A known ordinary shader cannot acquire emission merely from a misleading name.
            if (shader == RENDERTYPE_SOLID_SHADER
                    || shader == RENDERTYPE_ENTITY_SOLID_SHADER
                    || shader == RENDERTYPE_ENTITY_CUTOUT_SHADER
                    || shader == RENDERTYPE_ENTITY_CUTOUT_NO_CULL_SHADER
                    || shader == RENDERTYPE_ENTITY_TRANSLUCENT_SHADER
                    || background) return new Policy(Reason.MATERIAL, false, false, false);
        }
        String name = layer.name;
        if (role == Role.NAME_TAG && !name.contains("text_background"))
            return new Policy(Reason.NAME_TAG, true, false, false);
        boolean eye = name.equals("eyes");
        boolean excluded =
                eye
                        || name.equals("entity_translucent_emissive")
                        || name.equals("energy_swirl")
                        || name.equals("beacon_beam")
                        || name.equals("lightning")
                        || name.equals("dragon_rays");
        boolean force = excluded || name.equals("entity_alpha");
        if (eventEntry) force |= name.contains("glowing");
        return new Policy(force ? Reason.LEGACY_PROGRAM : Reason.MATERIAL, force, eye, excluded);
    }

    public static boolean eventForce(RenderType layer, String sourceShader) {
        return policy(layer, Role.ORDINARY, true).forceUnit()
                || sourceShader.endsWith(":glowing_shader")
                || sourceShader.contains("emissive");
    }
}
