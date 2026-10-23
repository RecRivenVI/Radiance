package com.radiance.mixins.vulkan_render_integration;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.radiance.client.vertex.*;
import com.radiance.compatibility.vanilla.*;
import net.minecraft.client.model.*;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.*;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Original state/animation formulas, direct model requests instead of BER output capture. */
public final class VanillaBlockDefinitionMixins {
    private VanillaBlockDefinitionMixins() {}

    @Mixin(StructureBlockRenderer.class)
    public abstract static class Structures implements VanillaBlockReplacement.Source {
        public void radiance$collect(
                BlockEntity value,
                float partial,
                PoseStack pose,
                StorageVertexConsumerProvider buffers,
                BoundPartCapture requests,
                int light,
                int overlay) {
            VanillaStructureRequests.collect((StructureBlockEntity) value, pose, buffers);
        }
    }

    @Mixin(BeaconRenderer.class)
    public abstract static class Beacon implements VanillaBlockReplacement.Source {
        public void radiance$collect(
                BlockEntity value,
                float partial,
                PoseStack pose,
                StorageVertexConsumerProvider buffers,
                BoundPartCapture requests,
                int light,
                int overlay) {
            var beacon = (BeaconBlockEntity) value;
            var sections = beacon.getBeamSections();
            int start = 0;
            long time = beacon.getLevel().getGameTime();
            for (int i = 0; i < sections.size(); i++) {
                var section = sections.get(i);
                VanillaBeamRequests.collect(
                        BeaconRenderer.BEAM_LOCATION,
                        partial,
                        1,
                        time,
                        start,
                        i == sections.size() - 1 ? 1024 : section.getHeight(),
                        section.getColor(),
                        .2F,
                        .25F,
                        pose,
                        buffers);
                start += section.getHeight();
            }
        }
    }

    @Mixin(TheEndPortalRenderer.class)
    public abstract static class Portals implements VanillaBlockReplacement.Source {
        @Invoker("getOffsetDown")
        protected abstract float radiance$down();

        @Invoker("getOffsetUp")
        protected abstract float radiance$up();

        @Invoker("renderType")
        protected abstract RenderType radiance$portalType();

        public void radiance$collect(
                BlockEntity value,
                float partial,
                PoseStack pose,
                StorageVertexConsumerProvider buffers,
                BoundPartCapture requests,
                int light,
                int overlay) {
            if (value instanceof TheEndGatewayBlockEntity gateway
                    && (gateway.isSpawning() || gateway.isCoolingDown())) {
                float progress =
                        gateway.isSpawning()
                                ? gateway.getSpawnPercent(partial)
                                : gateway.getCooldownPercent(partial);
                double max = gateway.isSpawning() ? gateway.getLevel().getMaxBuildHeight() : 50;
                progress = Mth.sin(progress * (float) Math.PI);
                int size = Mth.floor(progress * max);
                int color =
                        (gateway.isSpawning()
                                        ? net.minecraft.world.item.DyeColor.MAGENTA
                                        : net.minecraft.world.item.DyeColor.PURPLE)
                                .getTextureDiffuseColor();
                VanillaBeamRequests.collect(
                        net.minecraft.resources.ResourceLocation.withDefaultNamespace(
                                "textures/entity/end_gateway_beam.png"),
                        partial,
                        progress,
                        gateway.getLevel().getGameTime(),
                        -size,
                        size * 2,
                        color,
                        .15F,
                        .175F,
                        pose,
                        buffers);
            }
            VanillaPortalRequests.collect(
                    (TheEndPortalBlockEntity) value,
                    radiance$down(),
                    radiance$up(),
                    radiance$portalType(),
                    pose,
                    buffers);
        }
    }

    @Mixin(BannerRenderer.class)
    public abstract static class Banner implements VanillaBlockReplacement.Source {
        @Shadow @Final private ModelPart flag;
        @Shadow @Final private ModelPart pole;
        @Shadow @Final private ModelPart bar;

        public void radiance$collect(
                BlockEntity value,
                float partial,
                PoseStack pose,
                StorageVertexConsumerProvider buffers,
                BoundPartCapture requests,
                int light,
                int overlay) {
            var banner = (BannerBlockEntity) value;
            pose.pushPose();
            long time = 0;
            if (banner.getLevel() == null) {
                pose.translate(.5F, .5F, .5F);
                pole.visible = true;
            } else {
                time = banner.getLevel().getGameTime();
                var state = banner.getBlockState();
                if (state.getBlock() instanceof BannerBlock) {
                    pose.translate(.5F, .5F, .5F);
                    pose.mulPose(
                            Axis.YP.rotationDegrees(
                                    -net.minecraft.world.level.block.state.properties
                                            .RotationSegment.convertToDegrees(
                                            state.getValue(BannerBlock.ROTATION))));
                    pole.visible = true;
                } else {
                    pose.translate(.5F, -.16666667F, .5F);
                    pose.mulPose(
                            Axis.YP.rotationDegrees(
                                    -state.getValue(WallBannerBlock.FACING).toYRot()));
                    pose.translate(0, -.3125F, -.4375F);
                    pole.visible = false;
                }
            }
            pose.pushPose();
            pose.scale(.6666667F, -.6666667F, -.6666667F);
            var material = net.minecraft.client.resources.model.ModelBakery.BANNER_BASE;
            VanillaBlockReplacement.add(
                    VanillaModelPose.extract(pole, pose, -1),
                    material,
                    RenderType::entitySolid,
                    buffers,
                    requests,
                    light,
                    overlay);
            VanillaBlockReplacement.add(
                    VanillaModelPose.extract(bar, pose, -1),
                    material,
                    RenderType::entitySolid,
                    buffers,
                    requests,
                    light,
                    overlay);
            var pos = banner.getBlockPos();
            float phase =
                    ((float)
                                            Math.floorMod(
                                                    (long)
                                                                    (pos.getX() * 7
                                                                            + pos.getY() * 9
                                                                            + pos.getZ() * 13)
                                                            + time,
                                                    100L)
                                    + partial)
                            / 100;
            flag.xRot = (-.0125F + .01F * Mth.cos((float) (Math.PI * 2) * phase)) * (float) Math.PI;
            flag.y = -32;
            VanillaBannerRequests.patterns(
                    flag,
                    material,
                    true,
                    banner.getBaseColor(),
                    banner.getPatterns(),
                    pose,
                    buffers,
                    requests,
                    light,
                    overlay,
                    false);
            pose.popPose();
            pose.popPose();
        }
    }

    @Mixin(SpawnerRenderer.class)
    public abstract static class Spawner implements VanillaBlockReplacement.Source {
        public void radiance$collect(
                BlockEntity value,
                float partial,
                PoseStack pose,
                StorageVertexConsumerProvider buffers,
                BoundPartCapture requests,
                int light,
                int overlay) {
            if (value.getLevel() == null) return;
            var spawner = ((SpawnerBlockEntity) value).getSpawner();
            VanillaSpawnerRequests.add(
                    spawner.getOrCreateDisplayEntity(value.getLevel(), value.getBlockPos()),
                    spawner.getoSpin(),
                    spawner.getSpin(),
                    partial,
                    pose,
                    buffers,
                    light);
        }
    }

    @Mixin(TrialSpawnerRenderer.class)
    public abstract static class TrialSpawner implements VanillaBlockReplacement.Source {
        public void radiance$collect(
                BlockEntity value,
                float partial,
                PoseStack pose,
                StorageVertexConsumerProvider buffers,
                BoundPartCapture requests,
                int light,
                int overlay) {
            if (value.getLevel() == null) return;
            var spawner = ((TrialSpawnerBlockEntity) value).getTrialSpawner();
            var data = spawner.getData();
            VanillaSpawnerRequests.add(
                    data.getOrCreateDisplayEntity(spawner, value.getLevel(), spawner.getState()),
                    data.getOSpin(),
                    data.getSpin(),
                    partial,
                    pose,
                    buffers,
                    light);
        }
    }

    @Mixin(VaultRenderer.class)
    public abstract static class Vault implements VanillaBlockReplacement.Source {
        @Shadow @Final private net.minecraft.util.RandomSource random;

        public boolean radiance$supportsBlock(BlockEntity value) {
            return VanillaItemReplacement.supportsWorldStack(
                    ((net.minecraft.world.level.block.entity.vault.VaultBlockEntity) value)
                            .getSharedData()
                            .getDisplayItem());
        }

        public void radiance$collect(
                BlockEntity value,
                float partial,
                PoseStack pose,
                StorageVertexConsumerProvider buffers,
                BoundPartCapture requests,
                int light,
                int overlay) {
            var vault = (net.minecraft.world.level.block.entity.vault.VaultBlockEntity) value;
            var stack = vault.getSharedData().getDisplayItem();
            if (!net.minecraft.world.level.block.entity.vault.VaultBlockEntity.Client
                            .shouldDisplayActiveEffects(vault.getSharedData())
                    || value.getLevel() == null
                    || stack.isEmpty()) return;
            random.setSeed(
                    net.minecraft.client.renderer.entity.ItemEntityRenderer.getSeedForItemStack(
                            stack));
            var data = vault.getClientData();
            pose.pushPose();
            pose.translate(.5F, .4F, .5F);
            pose.mulPose(
                    Axis.YP.rotationDegrees(
                            Mth.rotLerp(partial, data.previousSpin(), data.currentSpin())));
            var model =
                    net.minecraft.client.Minecraft.getInstance()
                            .getItemRenderer()
                            .getModel(stack, value.getLevel(), null, 0);
            VanillaItemInstances.add(stack, model, random, pose, buffers, light);
            pose.popPose();
        }
    }

    @Mixin(ConduitRenderer.class)
    public abstract static class Conduit implements VanillaBlockReplacement.Source {
        @Shadow @Final private ModelPart eye;
        @Shadow @Final private ModelPart wind;
        @Shadow @Final private ModelPart shell;
        @Shadow @Final private ModelPart cage;
        @Shadow @Final private BlockEntityRenderDispatcher renderer;

        public void radiance$collect(
                BlockEntity value,
                float partial,
                PoseStack pose,
                StorageVertexConsumerProvider buffers,
                BoundPartCapture requests,
                int light,
                int overlay) {
            var conduit = (ConduitBlockEntity) value;
            float time = conduit.tickCount + partial;
            if (!conduit.isActive()) {
                pose.pushPose();
                pose.translate(.5F, .5F, .5F);
                pose.mulPose(
                        new org.joml.Quaternionf()
                                .rotationY(conduit.getActiveRotation(0) * (float) (Math.PI / 180)));
                VanillaBlockReplacement.add(
                        VanillaModelPose.extract(shell, pose, -1),
                        ConduitRenderer.SHELL_TEXTURE,
                        RenderType::entitySolid,
                        buffers,
                        requests,
                        light,
                        overlay);
                pose.popPose();
                return;
            }
            float angle = conduit.getActiveRotation(partial) * (180F / (float) Math.PI),
                    bob = Mth.sin(time * .1F) / 2 + .5F;
            bob = bob * bob + bob;
            pose.pushPose();
            pose.translate(.5F, .3F + bob * .2F, .5F);
            pose.mulPose(
                    new org.joml.Quaternionf()
                            .rotationAxis(
                                    angle * (float) (Math.PI / 180),
                                    new org.joml.Vector3f(.5F, 1, .5F).normalize()));
            VanillaBlockReplacement.add(
                    VanillaModelPose.extract(cage, pose, -1),
                    ConduitRenderer.ACTIVE_SHELL_TEXTURE,
                    RenderType::entityCutoutNoCull,
                    buffers,
                    requests,
                    light,
                    overlay);
            pose.popPose();
            int orientation = conduit.tickCount / 66 % 3;
            pose.pushPose();
            pose.translate(.5F, .5F, .5F);
            if (orientation == 1)
                pose.mulPose(new org.joml.Quaternionf().rotationX((float) (Math.PI / 2)));
            else if (orientation == 2)
                pose.mulPose(new org.joml.Quaternionf().rotationZ((float) (Math.PI / 2)));
            var material =
                    orientation == 1
                            ? ConduitRenderer.VERTICAL_WIND_TEXTURE
                            : ConduitRenderer.WIND_TEXTURE;
            VanillaBlockReplacement.add(
                    VanillaModelPose.extract(wind, pose, -1),
                    material,
                    RenderType::entityCutoutNoCull,
                    buffers,
                    requests,
                    light,
                    overlay);
            pose.popPose();
            pose.pushPose();
            pose.translate(.5F, .5F, .5F);
            pose.scale(.875F, .875F, .875F);
            pose.mulPose(
                    new org.joml.Quaternionf().rotationXYZ((float) Math.PI, 0, (float) Math.PI));
            VanillaBlockReplacement.add(
                    VanillaModelPose.extract(wind, pose, -1),
                    material,
                    RenderType::entityCutoutNoCull,
                    buffers,
                    requests,
                    light,
                    overlay);
            pose.popPose();
            var camera = renderer.camera;
            pose.pushPose();
            pose.translate(.5F, .3F + bob * .2F, .5F);
            pose.scale(.5F, .5F, .5F);
            pose.mulPose(
                    new org.joml.Quaternionf()
                            .rotationYXZ(
                                    -camera.getYRot() * (float) (Math.PI / 180),
                                    camera.getXRot() * (float) (Math.PI / 180),
                                    (float) Math.PI));
            pose.scale(1.3333334F, 1.3333334F, 1.3333334F);
            VanillaBlockReplacement.add(
                    VanillaModelPose.extract(eye, pose, -1),
                    conduit.isHunting()
                            ? ConduitRenderer.OPEN_EYE_TEXTURE
                            : ConduitRenderer.CLOSED_EYE_TEXTURE,
                    RenderType::entityCutoutNoCull,
                    buffers,
                    requests,
                    light,
                    overlay);
            pose.popPose();
        }
    }

    @Mixin(DecoratedPotRenderer.class)
    public abstract static class DecoratedPot implements VanillaBlockReplacement.Source {
        @Shadow @Final private ModelPart neck;
        @Shadow @Final private ModelPart top;
        @Shadow @Final private ModelPart bottom;
        @Shadow @Final private ModelPart frontSide;
        @Shadow @Final private ModelPart backSide;
        @Shadow @Final private ModelPart leftSide;
        @Shadow @Final private ModelPart rightSide;

        public void radiance$collect(
                BlockEntity value,
                float partial,
                PoseStack pose,
                StorageVertexConsumerProvider buffers,
                BoundPartCapture requests,
                int light,
                int overlay) {
            var pot = (DecoratedPotBlockEntity) value;
            pose.pushPose();
            pose.translate(.5, 0, .5);
            pose.mulPose(Axis.YP.rotationDegrees(180 - pot.getDirection().toYRot()));
            pose.translate(-.5, 0, -.5);
            var style = pot.lastWobbleStyle;
            if (style != null && pot.getLevel() != null) {
                float time =
                        ((float) (pot.getLevel().getGameTime() - pot.wobbleStartedAtTick) + partial)
                                / style.duration;
                if (time >= 0 && time <= 1) {
                    if (style == DecoratedPotBlockEntity.WobbleStyle.POSITIVE) {
                        float angle = time * (float) (Math.PI * 2),
                                x = -1.5F * (Mth.cos(angle) + .5F) * Mth.sin(angle / 2);
                        pose.rotateAround(Axis.XP.rotation(x * .015625F), .5F, 0, .5F);
                        pose.rotateAround(Axis.ZP.rotation(Mth.sin(angle) * .015625F), .5F, 0, .5F);
                    } else
                        pose.rotateAround(
                                Axis.YP.rotation(
                                        Mth.sin(-time * 3 * (float) Math.PI) * .125F * (1 - time)),
                                .5F,
                                0,
                                .5F);
                }
            }
            for (var part : java.util.List.of(neck, top, bottom))
                VanillaBlockReplacement.add(
                        VanillaModelPose.extract(part, pose, -1),
                        net.minecraft.client.renderer.Sheets.DECORATED_POT_BASE,
                        RenderType::entitySolid,
                        buffers,
                        requests,
                        light,
                        overlay);
            var decorations = pot.getDecorations();
            var parts = java.util.List.of(frontSide, backSide, leftSide, rightSide);
            var items =
                    java.util.List.of(
                            decorations.front(),
                            decorations.back(),
                            decorations.left(),
                            decorations.right());
            for (int i = 0; i < parts.size(); i++) {
                var material = net.minecraft.client.renderer.Sheets.DECORATED_POT_SIDE;
                var item = items.get(i);
                if (item.isPresent()) {
                    var selected =
                            net.minecraft.client.renderer.Sheets.getDecoratedPotMaterial(
                                    DecoratedPotPatterns.getPatternFromItem(item.get()));
                    if (selected != null) material = selected;
                }
                VanillaBlockReplacement.add(
                        VanillaModelPose.extract(parts.get(i), pose, -1),
                        material,
                        RenderType::entitySolid,
                        buffers,
                        requests,
                        light,
                        overlay);
            }
            pose.popPose();
        }
    }

    @Mixin(CampfireRenderer.class)
    public abstract static class Campfire implements VanillaBlockReplacement.Source {
        public boolean radiance$supportsBlock(BlockEntity value) {
            return ((CampfireBlockEntity) value)
                    .getItems().stream().allMatch(VanillaItemReplacement::supportsWorldStack);
        }

        public void radiance$collect(
                BlockEntity value,
                float partial,
                PoseStack pose,
                StorageVertexConsumerProvider buffers,
                BoundPartCapture requests,
                int light,
                int overlay) {
            var campfire = (CampfireBlockEntity) value;
            var facing = value.getBlockState().getValue(CampfireBlock.FACING);
            var items = campfire.getItems();
            for (int i = 0; i < items.size(); i++)
                if (items.get(i) != net.minecraft.world.item.ItemStack.EMPTY) {
                    pose.pushPose();
                    pose.translate(.5F, .44921875F, .5F);
                    var direction = Direction.from2DDataValue((i + facing.get2DDataValue()) % 4);
                    pose.mulPose(Axis.YP.rotationDegrees(-direction.toYRot()));
                    pose.mulPose(Axis.XP.rotationDegrees(90));
                    pose.translate(-.3125F, -.3125F, 0);
                    pose.scale(.375F, .375F, .375F);
                    VanillaItemReplacement.worldItem(
                            value.getLevel(),
                            items.get(i),
                            net.minecraft.world.item.ItemDisplayContext.FIXED,
                            pose,
                            buffers,
                            light,
                            overlay,
                            (int) value.getBlockPos().asLong() + i);
                    pose.popPose();
                }
        }
    }

    @Mixin(BrushableBlockRenderer.class)
    public abstract static class Brushable implements VanillaBlockReplacement.Source {
        @Invoker("translations")
        protected abstract float[] radiance$translations(Direction direction, int dusted);

        public boolean radiance$supportsBlock(BlockEntity value) {
            return VanillaItemReplacement.supportsWorldStack(
                    ((BrushableBlockEntity) value).getItem());
        }

        public void radiance$collect(
                BlockEntity value,
                float partial,
                PoseStack pose,
                StorageVertexConsumerProvider buffers,
                BoundPartCapture requests,
                int light,
                int overlay) {
            var brush = (BrushableBlockEntity) value;
            int dusted =
                    value.getBlockState()
                            .getValue(
                                    net.minecraft.world.level.block.state.properties
                                            .BlockStateProperties.DUSTED);
            var direction = brush.getHitDirection();
            var item = brush.getItem();
            if (value.getLevel() == null || dusted <= 0 || direction == null || item.isEmpty())
                return;
            pose.pushPose();
            pose.translate(0, .5F, 0);
            var offset = radiance$translations(direction, dusted);
            pose.translate(offset[0], offset[1], offset[2]);
            pose.mulPose(Axis.YP.rotationDegrees(75));
            pose.mulPose(
                    Axis.YP.rotationDegrees(
                            (direction == Direction.EAST || direction == Direction.WEST ? 90 : 0)
                                    + 11));
            pose.scale(.5F, .5F, .5F);
            light =
                    net.minecraft.client.renderer.LevelRenderer.getLightColor(
                            value.getLevel(),
                            value.getBlockState(),
                            value.getBlockPos().relative(direction));
            VanillaItemReplacement.worldItem(
                    value.getLevel(),
                    item,
                    net.minecraft.world.item.ItemDisplayContext.FIXED,
                    pose,
                    buffers,
                    light,
                    net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY,
                    0);
            pose.popPose();
        }
    }

    @Mixin(BedRenderer.class)
    public abstract static class Bed implements VanillaBlockReplacement.Source {
        @Shadow @Final private ModelPart headRoot;
        @Shadow @Final private ModelPart footRoot;

        public void radiance$collect(
                BlockEntity value,
                float partial,
                PoseStack pose,
                StorageVertexConsumerProvider buffers,
                BoundPartCapture requests,
                int light,
                int overlay) {
            var bed = (BedBlockEntity) value;
            var material =
                    net.minecraft.client.renderer.Sheets.BED_TEXTURES[bed.getColor().getId()];
            var state = value.getBlockState();
            var level = value.getLevel();
            if (level != null) {
                var combined =
                        DoubleBlockCombiner.combineWithNeigbour(
                                BlockEntityType.BED,
                                BedBlock::getBlockType,
                                BedBlock::getConnectedDirection,
                                ChestBlock.FACING,
                                state,
                                level,
                                value.getBlockPos(),
                                (a, b) -> false);
                light = combined.apply(new BrightnessCombiner<>()).get(light);
                VanillaBedRequests.add(
                        state.getValue(BedBlock.PART)
                                        == net.minecraft.world.level.block.state.properties.BedPart
                                                .HEAD
                                ? headRoot
                                : footRoot,
                        state.getValue(BedBlock.FACING),
                        false,
                        material,
                        pose,
                        buffers,
                        requests,
                        light,
                        overlay);
            } else {
                VanillaBedRequests.add(
                        headRoot,
                        Direction.SOUTH,
                        false,
                        material,
                        pose,
                        buffers,
                        requests,
                        light,
                        overlay);
                VanillaBedRequests.add(
                        footRoot,
                        Direction.SOUTH,
                        true,
                        material,
                        pose,
                        buffers,
                        requests,
                        light,
                        overlay);
            }
        }
    }

    @Mixin(BellRenderer.class)
    public abstract static class Bell implements VanillaBlockReplacement.Source {
        @Shadow @Final private ModelPart bellBody;

        public void radiance$collect(
                BlockEntity value,
                float partial,
                PoseStack pose,
                StorageVertexConsumerProvider buffers,
                BoundPartCapture requests,
                int light,
                int overlay) {
            var bell = (BellBlockEntity) value;
            float time = bell.ticks + partial, x = 0, z = 0;
            if (bell.shaking) {
                float angle = Mth.sin(time / (float) Math.PI) / (4 + time / 3);
                if (bell.clickDirection == Direction.NORTH) x = -angle;
                else if (bell.clickDirection == Direction.SOUTH) x = angle;
                else if (bell.clickDirection == Direction.EAST) z = -angle;
                else if (bell.clickDirection == Direction.WEST) z = angle;
            }
            bellBody.xRot = x;
            bellBody.zRot = z;
            VanillaBlockReplacement.add(
                    VanillaModelPose.extract(bellBody, pose, -1),
                    BellRenderer.BELL_RESOURCE_LOCATION,
                    RenderType::entitySolid,
                    buffers,
                    requests,
                    light,
                    overlay);
        }
    }

    @Mixin(EnchantTableRenderer.class)
    public abstract static class EnchantingBook implements VanillaBlockReplacement.Source {
        @Shadow @Final private BookModel bookModel;

        public void radiance$collect(
                BlockEntity value,
                float partial,
                PoseStack pose,
                StorageVertexConsumerProvider buffers,
                BoundPartCapture requests,
                int light,
                int overlay) {
            var table = (EnchantingTableBlockEntity) value;
            pose.pushPose();
            pose.translate(.5F, .75F, .5F);
            float time = table.time + partial;
            pose.translate(0, .1F + Mth.sin(time * .1F) * .01F, 0);
            float delta = table.rot - table.oRot;
            while (delta >= (float) Math.PI) delta -= (float) (Math.PI * 2);
            while (delta < -(float) Math.PI) delta += (float) (Math.PI * 2);
            pose.mulPose(Axis.YP.rotation(-(table.oRot + delta * partial)));
            pose.mulPose(Axis.ZP.rotationDegrees(80));
            float flip = Mth.lerp(partial, table.oFlip, table.flip);
            bookModel.setupAnim(
                    time,
                    Mth.clamp(Mth.frac(flip + .25F) * 1.6F - .3F, 0, 1),
                    Mth.clamp(Mth.frac(flip + .75F) * 1.6F - .3F, 0, 1),
                    Mth.lerp(partial, table.oOpen, table.open));
            VanillaBlockReplacement.add(
                    VanillaModelPose.extract(bookModel, pose, -1),
                    EnchantTableRenderer.BOOK_LOCATION,
                    RenderType::entitySolid,
                    buffers,
                    requests,
                    light,
                    overlay);
            pose.popPose();
        }
    }

    @Mixin(LecternRenderer.class)
    public abstract static class LecternBook implements VanillaBlockReplacement.Source {
        @Shadow @Final private BookModel bookModel;

        public void radiance$collect(
                BlockEntity value,
                float partial,
                PoseStack pose,
                StorageVertexConsumerProvider buffers,
                BoundPartCapture requests,
                int light,
                int overlay) {
            var state = value.getBlockState();
            if (!state.getValue(LecternBlock.HAS_BOOK)) return;
            pose.pushPose();
            pose.translate(.5F, 1.0625F, .5F);
            pose.mulPose(
                    Axis.YP.rotationDegrees(
                            -state.getValue(LecternBlock.FACING).getClockWise().toYRot()));
            pose.mulPose(Axis.ZP.rotationDegrees(67.5F));
            pose.translate(0, -.125F, 0);
            bookModel.setupAnim(0, .1F, .9F, 1.2F);
            VanillaBlockReplacement.add(
                    VanillaModelPose.extract(bookModel, pose, -1),
                    EnchantTableRenderer.BOOK_LOCATION,
                    RenderType::entitySolid,
                    buffers,
                    requests,
                    light,
                    overlay);
            pose.popPose();
        }
    }

    @Mixin(ShulkerBoxRenderer.class)
    public abstract static class ShulkerBox implements VanillaBlockReplacement.Source {
        @Shadow @Final private ShulkerModel<?> model;

        public void radiance$collect(
                BlockEntity value,
                float partial,
                PoseStack pose,
                StorageVertexConsumerProvider buffers,
                BoundPartCapture requests,
                int light,
                int overlay) {
            var box = (ShulkerBoxBlockEntity) value;
            Direction direction = Direction.UP;
            if (box.hasLevel()) {
                var state = box.getLevel().getBlockState(box.getBlockPos());
                if (state.getBlock() instanceof ShulkerBoxBlock)
                    direction = state.getValue(ShulkerBoxBlock.FACING);
            }
            var dye = box.getColor();
            var material =
                    dye == null
                            ? net.minecraft.client.renderer.Sheets.DEFAULT_SHULKER_TEXTURE_LOCATION
                            : net.minecraft.client.renderer.Sheets.SHULKER_TEXTURE_LOCATION.get(
                                    dye.getId());
            pose.pushPose();
            pose.translate(.5F, .5F, .5F);
            pose.scale(.9995F, .9995F, .9995F);
            pose.mulPose(direction.getRotation());
            pose.scale(1, -1, -1);
            pose.translate(0, -1, 0);
            var lid = model.getLid();
            lid.setPos(0, 24 - box.getProgress(partial) * .5F * 16, 0);
            lid.yRot = 270 * box.getProgress(partial) * (float) (Math.PI / 180);
            VanillaBlockReplacement.add(
                    VanillaModelPose.extract(model, pose, -1),
                    material,
                    RenderType::entityCutoutNoCull,
                    buffers,
                    requests,
                    light,
                    overlay);
            pose.popPose();
        }
    }

    @Mixin(ChestRenderer.class)
    public abstract static class Chest<T extends BlockEntity & LidBlockEntity>
            implements VanillaBlockReplacement.Source {
        @Shadow @Final private ModelPart lid;
        @Shadow @Final private ModelPart bottom;
        @Shadow @Final private ModelPart lock;
        @Shadow @Final private ModelPart doubleLeftLid;
        @Shadow @Final private ModelPart doubleLeftBottom;
        @Shadow @Final private ModelPart doubleLeftLock;
        @Shadow @Final private ModelPart doubleRightLid;
        @Shadow @Final private ModelPart doubleRightBottom;
        @Shadow @Final private ModelPart doubleRightLock;

        @Invoker("getMaterial")
        protected abstract net.minecraft.client.resources.model.Material radiance$material(
                T entity, net.minecraft.world.level.block.state.properties.ChestType type);

        @SuppressWarnings({"unchecked", "rawtypes"})
        public void radiance$collect(
                BlockEntity value,
                float partial,
                PoseStack pose,
                StorageVertexConsumerProvider buffers,
                BoundPartCapture requests,
                int light,
                int overlay) {
            T chest = (T) value;
            var level = chest.getLevel();
            boolean hasLevel = level != null;
            var state =
                    hasLevel
                            ? chest.getBlockState()
                            : Blocks.CHEST
                                    .defaultBlockState()
                                    .setValue(ChestBlock.FACING, Direction.SOUTH);
            var type =
                    state.hasProperty(ChestBlock.TYPE)
                            ? state.getValue(ChestBlock.TYPE)
                            : net.minecraft.world.level.block.state.properties.ChestType.SINGLE;
            if (!(state.getBlock() instanceof AbstractChestBlock<?> block)) return;
            pose.pushPose();
            pose.translate(.5F, .5F, .5F);
            pose.mulPose(Axis.YP.rotationDegrees(-state.getValue(ChestBlock.FACING).toYRot()));
            pose.translate(-.5F, -.5F, -.5F);
            DoubleBlockCombiner.NeighborCombineResult<? extends ChestBlockEntity> neighbors =
                    hasLevel
                            ? block.combine(state, level, chest.getBlockPos(), true)
                            : DoubleBlockCombiner.Combiner::acceptNone;
            float openness = neighbors.apply(ChestBlock.opennessCombiner(chest)).get(partial);
            openness = 1 - openness;
            openness = 1 - openness * openness * openness;
            int brightness = neighbors.apply(new BrightnessCombiner<>()).applyAsInt(light);
            ModelPart selectedLid = lid, selectedLock = lock, selectedBottom = bottom;
            if (type == net.minecraft.world.level.block.state.properties.ChestType.LEFT) {
                selectedLid = doubleLeftLid;
                selectedLock = doubleLeftLock;
                selectedBottom = doubleLeftBottom;
            } else if (type == net.minecraft.world.level.block.state.properties.ChestType.RIGHT) {
                selectedLid = doubleRightLid;
                selectedLock = doubleRightLock;
                selectedBottom = doubleRightBottom;
            }
            selectedLid.xRot = -(openness * (float) (Math.PI / 2));
            selectedLock.xRot = selectedLid.xRot;
            var parts = new java.util.ArrayList<VanillaModelPose.Part>();
            parts.addAll(VanillaModelPose.extract(selectedLid, pose, -1));
            parts.addAll(VanillaModelPose.extract(selectedLock, pose, -1));
            parts.addAll(VanillaModelPose.extract(selectedBottom, pose, -1));
            VanillaBlockReplacement.add(
                    parts,
                    radiance$material(chest, type),
                    RenderType::entityCutout,
                    buffers,
                    requests,
                    brightness,
                    overlay);
            pose.popPose();
        }
    }

    @Mixin(SkullBlockRenderer.class)
    public abstract static class Skull implements VanillaBlockReplacement.Source {
        @Shadow @Final private java.util.Map<SkullBlock.Type, SkullModelBase> modelByType;

        public boolean radiance$supportsBlock(BlockEntity entity) {
            return VanillaModelPose.supports(
                    modelByType.get(
                            ((AbstractSkullBlock) entity.getBlockState().getBlock()).getType()));
        }

        public void radiance$collect(
                BlockEntity value,
                float partial,
                PoseStack pose,
                StorageVertexConsumerProvider buffers,
                BoundPartCapture requests,
                int light,
                int overlay) {
            var skull = (SkullBlockEntity) value;
            var state = skull.getBlockState();
            Direction wall =
                    state.getBlock() instanceof WallSkullBlock
                            ? state.getValue(WallSkullBlock.FACING)
                            : null;
            int rotation =
                    wall == null
                            ? state.getValue(SkullBlock.ROTATION)
                            : net.minecraft.world.level.block.state.properties.RotationSegment
                                    .convertToSegment(wall.getOpposite());
            var type = ((AbstractSkullBlock) state.getBlock()).getType();
            VanillaSkullRequests.add(
                    wall,
                    net.minecraft.world.level.block.state.properties.RotationSegment
                            .convertToDegrees(rotation),
                    skull.getAnimation(partial),
                    pose,
                    buffers,
                    requests,
                    light,
                    modelByType.get(type),
                    SkullBlockRenderer.getRenderType(type, skull.getOwnerProfile()));
        }
    }
}
