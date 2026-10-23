package com.radiance.mixins.vulkan_render_integration;

import static com.radiance.compatibility.vanilla.VanillaLayerReplacement.cutout;

import com.radiance.compatibility.vanilla.VanillaLayerReplacement.*;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.*;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.layers.*;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.FastColor;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.animal.*;
import net.minecraft.world.entity.animal.horse.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.DyedItemColor;
import org.spongepowered.asm.mixin.*;

/** Loaded layer definitions and source-owned appearance state, without invoking layer rendering. */
public final class VanillaSurfaceDefinitionMixins {
    private VanillaSurfaceDefinitionMixins() {}

    @Mixin(CatCollarLayer.class)
    public abstract static class CatCollar implements SurfaceAccess {
        @Shadow @Final private CatModel<Cat> catModel;
        @Shadow @Final private static ResourceLocation CAT_COLLAR_LOCATION;

        public EntityModel<?> radiance$surfaceModel() {
            return catModel;
        }

        public List<Surface> radiance$surfaces(
                LivingEntity entity, EntityModel<?> parent, Animation a, int light) {
            Cat cat = (Cat) entity;
            return cat.isTame()
                    ? cutout(
                            catModel,
                            CAT_COLLAR_LOCATION,
                            cat,
                            cat.getCollarColor().getTextureDiffuseColor(),
                            true)
                    : List.of();
        }
    }

    @Mixin(WolfCollarLayer.class)
    public abstract static class WolfCollar implements SurfaceAccess {
        @Shadow @Final private static ResourceLocation WOLF_COLLAR_LOCATION;

        public EntityModel<?> radiance$surfaceModel() {
            return null;
        }

        public List<Surface> radiance$surfaces(
                LivingEntity entity, EntityModel<?> parent, Animation a, int light) {
            Wolf wolf = (Wolf) entity;
            return wolf.isTame() && !wolf.isInvisible()
                    ? List.of(
                            new Surface(
                                    null,
                                    RenderType.entityCutoutNoCull(WOLF_COLLAR_LOCATION),
                                    wolf.getCollarColor().getTextureDiffuseColor(),
                                    OverlayTexture.NO_OVERLAY,
                                    false))
                    : List.of();
        }
    }

    @Mixin(HorseMarkingLayer.class)
    public abstract static class HorseMarkings implements SurfaceAccess {
        @Shadow @Final private static Map<Markings, ResourceLocation> LOCATION_BY_MARKINGS;

        public EntityModel<?> radiance$surfaceModel() {
            return null;
        }

        public List<Surface> radiance$surfaces(
                LivingEntity entity, EntityModel<?> parent, Animation a, int light) {
            var texture = LOCATION_BY_MARKINGS.get(((Horse) entity).getMarkings());
            return texture != null && !entity.isInvisible()
                    ? List.of(
                            new Surface(
                                    null,
                                    RenderType.entityTranslucent(texture),
                                    -1,
                                    LivingEntityRenderer.getOverlayCoords(entity, 0),
                                    false))
                    : List.of();
        }
    }

    @Mixin(IronGolemCrackinessLayer.class)
    public abstract static class GolemCracks implements SurfaceAccess {
        @Shadow @Final private static Map<Crackiness.Level, ResourceLocation> resourceLocations;

        public EntityModel<?> radiance$surfaceModel() {
            return null;
        }

        public List<Surface> radiance$surfaces(
                LivingEntity entity, EntityModel<?> parent, Animation a, int light) {
            var texture = resourceLocations.get(((IronGolem) entity).getCrackiness());
            return texture == null ? List.of() : cutout(null, texture, entity, -1, false);
        }
    }

    @Mixin(SaddleLayer.class)
    public abstract static class Saddle implements SurfaceAccess {
        @Shadow @Final private EntityModel<?> model;
        @Shadow @Final private ResourceLocation textureLocation;

        public EntityModel<?> radiance$surfaceModel() {
            return model;
        }

        public List<Surface> radiance$surfaces(
                LivingEntity entity, EntityModel<?> parent, Animation a, int light) {
            return ((Saddleable) entity).isSaddled()
                    ? List.of(
                            new Surface(
                                    model,
                                    RenderType.entityCutoutNoCull(textureLocation),
                                    -1,
                                    OverlayTexture.NO_OVERLAY,
                                    true))
                    : List.of();
        }
    }

    @Mixin(DrownedOuterLayer.class)
    public abstract static class DrownedSkin implements SurfaceAccess {
        @Shadow @Final private DrownedModel<?> model;
        @Shadow @Final private static ResourceLocation DROWNED_OUTER_LAYER_LOCATION;

        public EntityModel<?> radiance$surfaceModel() {
            return model;
        }

        public List<Surface> radiance$surfaces(
                LivingEntity entity, EntityModel<?> parent, Animation a, int light) {
            return cutout(model, DROWNED_OUTER_LAYER_LOCATION, entity, -1, true);
        }
    }

    @Mixin(SlimeOuterLayer.class)
    public abstract static class SlimeSkin implements SurfaceAccess {
        @Shadow @Final private EntityModel<?> model;

        public EntityModel<?> radiance$surfaceModel() {
            return model;
        }

        @SuppressWarnings({"rawtypes", "unchecked"})
        public List<Surface> radiance$surfaces(
                LivingEntity entity, EntityModel<?> parent, Animation a, int light) {
            boolean outline =
                    Minecraft.getInstance().shouldEntityAppearGlowing(entity)
                            && entity.isInvisible();
            if (entity.isInvisible() && !outline) return List.of();
            var texture =
                    Minecraft.getInstance()
                            .getEntityRenderDispatcher()
                            .getRenderer(entity)
                            .getTextureLocation(entity);
            return List.of(
                    new Surface(
                            model,
                            outline
                                    ? RenderType.outline(texture)
                                    : RenderType.entityTranslucent(texture),
                            -1,
                            LivingEntityRenderer.getOverlayCoords(entity, 0),
                            true,
                            !entity.isInvisible()));
        }
    }

    @Mixin(SheepFurLayer.class)
    public abstract static class SheepFur implements SurfaceAccess {
        @Shadow @Final private SheepFurModel<Sheep> model;
        @Shadow @Final private static ResourceLocation SHEEP_FUR_LOCATION;

        public EntityModel<?> radiance$surfaceModel() {
            return model;
        }

        public List<Surface> radiance$surfaces(
                LivingEntity entity, EntityModel<?> parent, Animation a, int light) {
            Sheep sheep = (Sheep) entity;
            if (sheep.isSheared()) return List.of();
            if (sheep.isInvisible())
                return Minecraft.getInstance().shouldEntityAppearGlowing(sheep)
                        ? List.of(
                                new Surface(
                                        model,
                                        RenderType.outline(SHEEP_FUR_LOCATION),
                                        -16777216,
                                        LivingEntityRenderer.getOverlayCoords(entity, 0),
                                        true))
                        : List.of();
            int color = Sheep.getColor(sheep.getColor());
            if (sheep.hasCustomName() && "jeb_".equals(sheep.getName().getString())) {
                int tick = sheep.tickCount / 25 + sheep.getId(), length = DyeColor.values().length;
                color =
                        FastColor.ARGB32.lerp(
                                (sheep.tickCount % 25 + a.partial()) / 25F,
                                Sheep.getColor(DyeColor.byId(tick % length)),
                                Sheep.getColor(DyeColor.byId((tick + 1) % length)));
            }
            return cutout(model, SHEEP_FUR_LOCATION, sheep, color, true);
        }
    }

    @Mixin(SkeletonClothingLayer.class)
    public abstract static class SkeletonClothes implements SurfaceAccess {
        @Shadow @Final private SkeletonModel<?> layerModel;
        @Shadow @Final private ResourceLocation clothesLocation;

        public EntityModel<?> radiance$surfaceModel() {
            return layerModel;
        }

        public List<Surface> radiance$surfaces(
                LivingEntity entity, EntityModel<?> parent, Animation a, int light) {
            return cutout(layerModel, clothesLocation, entity, -1, true);
        }
    }

    @Mixin(HorseArmorLayer.class)
    public abstract static class HorseArmor implements SurfaceAccess {
        @Shadow @Final private HorseModel<Horse> model;

        public EntityModel<?> radiance$surfaceModel() {
            return model;
        }

        public List<Surface> radiance$surfaces(
                LivingEntity entity, EntityModel<?> parent, Animation a, int light) {
            var stack = ((Horse) entity).getBodyArmorItem();
            if (!(stack.getItem() instanceof AnimalArmorItem armor)
                    || armor.getBodyType() != AnimalArmorItem.BodyType.EQUESTRIAN) return List.of();
            int color =
                    stack.is(ItemTags.DYEABLE)
                            ? FastColor.ARGB32.opaque(DyedItemColor.getOrDefault(stack, -6265536))
                            : -1;
            return List.of(
                    new Surface(
                            model,
                            RenderType.entityCutoutNoCull(armor.getTexture()),
                            color,
                            OverlayTexture.NO_OVERLAY,
                            true));
        }
    }

    @Mixin(WolfArmorLayer.class)
    public abstract static class WolfArmor implements SurfaceAccess {
        @Shadow @Final private WolfModel<Wolf> model;
        @Shadow @Final private static Map<Crackiness.Level, ResourceLocation> ARMOR_CRACK_LOCATIONS;

        public EntityModel<?> radiance$surfaceModel() {
            return model;
        }

        public List<Surface> radiance$surfaces(
                LivingEntity entity, EntityModel<?> parent, Animation a, int light) {
            var wolf = (Wolf) entity;
            var stack = wolf.getBodyArmorItem();
            if (!wolf.hasArmor()
                    || !(stack.getItem() instanceof AnimalArmorItem armor)
                    || armor.getBodyType() != AnimalArmorItem.BodyType.CANINE) return List.of();
            var result = new ArrayList<Surface>();
            result.add(
                    new Surface(
                            model,
                            RenderType.entityCutoutNoCull(armor.getTexture()),
                            -1,
                            OverlayTexture.NO_OVERLAY,
                            true));
            if (stack.is(ItemTags.DYEABLE)) {
                int color = DyedItemColor.getOrDefault(stack, 0);
                if (FastColor.ARGB32.alpha(color) != 0 && armor.getOverlayTexture() != null)
                    result.add(
                            new Surface(
                                    model,
                                    RenderType.entityCutoutNoCull(armor.getOverlayTexture()),
                                    FastColor.ARGB32.opaque(color),
                                    OverlayTexture.NO_OVERLAY,
                                    false));
            }
            var texture = ARMOR_CRACK_LOCATIONS.get(Crackiness.WOLF_ARMOR.byDamage(stack));
            if (texture != null)
                result.add(
                        new Surface(
                                model,
                                RenderType.entityTranslucent(texture),
                                -1,
                                OverlayTexture.NO_OVERLAY,
                                false));
            return result;
        }
    }

    @Mixin(LlamaDecorLayer.class)
    public abstract static class LlamaDecor implements SurfaceAccess {
        @Shadow @Final private LlamaModel<Llama> model;
        @Shadow @Final private static ResourceLocation[] TEXTURE_LOCATION;
        @Shadow @Final private static ResourceLocation TRADER_LLAMA;

        public EntityModel<?> radiance$surfaceModel() {
            return model;
        }

        @SuppressWarnings({"rawtypes", "unchecked"})
        public List<Surface> radiance$surfaces(
                LivingEntity entity, EntityModel<?> parent, Animation a, int light) {
            var llama = (Llama) entity;
            var dye = llama.getSwag();
            var texture =
                    dye != null
                            ? TEXTURE_LOCATION[dye.getId()]
                            : llama.isTraderLlama() ? TRADER_LLAMA : null;
            if (texture == null) return List.of();
            ((EntityModel) parent).copyPropertiesTo(model);
            model.setupAnim(llama, a.position(), a.speed(), a.age(), a.yaw(), a.pitch());
            return List.of(
                    new Surface(
                            model,
                            RenderType.entityCutoutNoCull(texture),
                            -1,
                            OverlayTexture.NO_OVERLAY,
                            false));
        }
    }

    @Mixin(TropicalFishPatternLayer.class)
    public abstract static class FishPattern implements SurfaceAccess {
        @Shadow @Final
        private TropicalFishModelA<net.minecraft.world.entity.animal.TropicalFish> modelA;

        @Shadow @Final
        private TropicalFishModelB<net.minecraft.world.entity.animal.TropicalFish> modelB;

        @Shadow @Final private static ResourceLocation KOB_TEXTURE;
        @Shadow @Final private static ResourceLocation SUNSTREAK_TEXTURE;
        @Shadow @Final private static ResourceLocation SNOOPER_TEXTURE;
        @Shadow @Final private static ResourceLocation DASHER_TEXTURE;
        @Shadow @Final private static ResourceLocation BRINELY_TEXTURE;
        @Shadow @Final private static ResourceLocation SPOTTY_TEXTURE;
        @Shadow @Final private static ResourceLocation FLOPPER_TEXTURE;
        @Shadow @Final private static ResourceLocation STRIPEY_TEXTURE;
        @Shadow @Final private static ResourceLocation GLITTER_TEXTURE;
        @Shadow @Final private static ResourceLocation BLOCKFISH_TEXTURE;
        @Shadow @Final private static ResourceLocation BETTY_TEXTURE;
        @Shadow @Final private static ResourceLocation CLAYFISH_TEXTURE;

        public EntityModel<?> radiance$surfaceModel() {
            return modelA;
        }

        public boolean radiance$surfaceSupported() {
            return com.radiance.compatibility.vanilla.VanillaModelPose.supports(modelB);
        }

        public List<Surface> radiance$surfaces(
                LivingEntity entity, EntityModel<?> parent, Animation a, int light) {
            var fish = (net.minecraft.world.entity.animal.TropicalFish) entity;
            var variant = fish.getVariant();
            EntityModel<?> model = variant.base() == TropicalFish.Base.SMALL ? modelA : modelB;
            var texture =
                    variant == TropicalFish.Pattern.KOB
                            ? KOB_TEXTURE
                            : variant == TropicalFish.Pattern.SUNSTREAK
                                    ? SUNSTREAK_TEXTURE
                                    : variant == TropicalFish.Pattern.SNOOPER
                                            ? SNOOPER_TEXTURE
                                            : variant == TropicalFish.Pattern.DASHER
                                                    ? DASHER_TEXTURE
                                                    : variant == TropicalFish.Pattern.BRINELY
                                                            ? BRINELY_TEXTURE
                                                            : variant == TropicalFish.Pattern.SPOTTY
                                                                    ? SPOTTY_TEXTURE
                                                                    : variant
                                                                                    == TropicalFish
                                                                                            .Pattern
                                                                                            .FLOPPER
                                                                            ? FLOPPER_TEXTURE
                                                                            : variant
                                                                                            == TropicalFish
                                                                                                    .Pattern
                                                                                                    .STRIPEY
                                                                                    ? STRIPEY_TEXTURE
                                                                                    : variant
                                                                                                    == TropicalFish
                                                                                                            .Pattern
                                                                                                            .GLITTER
                                                                                            ? GLITTER_TEXTURE
                                                                                            : variant
                                                                                                            == TropicalFish
                                                                                                                    .Pattern
                                                                                                                    .BLOCKFISH
                                                                                                    ? BLOCKFISH_TEXTURE
                                                                                                    : variant
                                                                                                                    == TropicalFish
                                                                                                                            .Pattern
                                                                                                                            .BETTY
                                                                                                            ? BETTY_TEXTURE
                                                                                                            : CLAYFISH_TEXTURE;
            return cutout(
                    model, texture, fish, fish.getPatternColor().getTextureDiffuseColor(), true);
        }
    }
}
