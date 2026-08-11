package com.radiance.adapter.vanilla;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import net.minecraft.world.level.block.piston.PistonHeadBlock;
import net.minecraft.world.level.block.piston.PistonMovingBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.PistonType;
import net.neoforged.neoforge.client.ClientHooks;
import net.neoforged.neoforge.client.RenderTypeHelper;
import net.neoforged.neoforge.client.model.data.ModelData;

/**
 * World-material requests retain actual shape, tint and loader dependencies, without raster light.
 */
public final class VanillaWorldBlockRequests {
    private VanillaWorldBlockRequests() {}

    private record Slot(int branch, net.minecraft.client.renderer.RenderType layer) {}

    private record Input(
            Object model,
            BlockState state,
            BlockPos position,
            long seed,
            boolean sides,
            int overlay) {}

    private record DestructionInput(Input block, ModelData data, int stage) {}

    private static final class Definition {
        Object input;
        Object shape;
        VanillaBlockDependencies dependencies;
    }

    public static boolean destruction(
            net.minecraft.server.level.BlockDestructionProgress progress,
            BlockPos position,
            BlockState state,
            PoseStack pose,
            com.radiance.client.vertex.StorageVertexConsumerProvider output,
            net.minecraft.client.multiplayer.ClientLevel level,
            BlockRenderDispatcher blocks) {
        if (state.getRenderShape() != RenderShape.MODEL
                || !stable(progress, state, blocks.getBlockModel(state))) return false;
        int stage = progress.getProgress();
        var model = blocks.getBlockModel(state);
        // Keep the loader callback's actual world and data. The reviewed vanilla model graph
        // permits reuse of its immutable result; custom producers execute the original path.
        var data = model.getModelData(level, position, state, level.getModelData(position));
        var definition =
                ((com.radiance.client.vertex.SourceBindings.Owner) progress)
                        .radiance$bindings()
                        .definition("block-destruction", Definition::new);
        var input =
                new DestructionInput(
                        new Input(
                                model,
                                state,
                                position.immutable(),
                                state.getSeed(position),
                                true,
                                OverlayTexture.NO_OVERLAY),
                        data,
                        stage);
        if (!input.equals(definition.input)
                || definition.dependencies == null
                || !definition.dependencies.matches(level)) {
            definition.input = input;
            definition.shape = new Object();
            definition.dependencies = null;
        }
        output.declaredParts(progress.getId(), "vanilla-block-destruction");
        output.sourceOwner = progress;
        com.radiance.client.vertex.LocalMeshTakeover.request(
                definition.shape,
                pose.last(),
                output.getBuffer(
                        net.minecraft.client.resources.model.ModelBakery.DESTROY_TYPES.get(stage)),
                0,
                -1,
                (p, out, l, c) -> {
                    var view = new VanillaBlockDependencies(level);
                    var stack = new PoseStack();
                    stack.last().pose().set(p.pose());
                    stack.last().normal().set(p.normal());
                    var decal =
                            new com.mojang.blaze3d.vertex.SheetedDecalTextureGenerator(
                                    out, stack.last(), 1);
                    blocks.getModelRenderer()
                            .tesselateBlock(
                                    view,
                                    model,
                                    state,
                                    position,
                                    stack,
                                    decal,
                                    true,
                                    RandomSource.create(),
                                    state.getSeed(position),
                                    OverlayTexture.NO_OVERLAY,
                                    data,
                                    null);
                    definition.dependencies = view;
                });
        return true;
    }

    private static boolean stable(
            Object owner, BlockState state, net.minecraft.client.resources.model.BakedModel model) {
        return stableModel(
                        model,
                        java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>()))
                && owner instanceof com.radiance.client.vertex.SourceBindings.Owner
                && com.radiance.mixin_related.SourceCompatibility.compatible(
                        com.radiance.mixin_related.SourceCompatibility.Source.BAKED)
                && com.radiance.mixin_related.SourceCompatibility.producer(
                        state.getBlock().getClass())
                && com.radiance.mixin_related.SourceCompatibility.producer(
                        net.minecraft.client.color.block.BlockColors.class);
    }

    /** Only the reviewed JSON model graph; an unknown child keeps its original execution. */
    public interface CompositeModel {
        java.util.List<net.minecraft.client.resources.model.BakedModel> radiance$children();
    }

    /** Shared eligibility for adapters retaining the actual seeded selection and tint inputs. */
    public static boolean immutableModel(net.minecraft.client.resources.model.BakedModel model) {
        return com.radiance.client.vertex.BakedModelTakeover.sourceCompatible()
                && stableModel(
                        model,
                        java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>()));
    }

    private static boolean stableModel(
            net.minecraft.client.resources.model.BakedModel model,
            java.util.Set<net.minecraft.client.resources.model.BakedModel> seen) {
        if (!com.radiance.mixin_related.SourceCompatibility.producer(model.getClass()))
            return false;
        if (model.getClass() == net.minecraft.client.resources.model.SimpleBakedModel.class)
            return true;
        if ((model.getClass() != net.minecraft.client.resources.model.WeightedBakedModel.class
                        && model.getClass()
                                != net.minecraft.client.resources.model.MultiPartBakedModel.class)
                || !(model instanceof CompositeModel composite)
                || !seen.add(model)) return false;
        for (var child : composite.radiance$children()) if (!stableModel(child, seen)) return false;
        seen.remove(model);
        return true;
    }

    private static void request(
            Object owner,
            int branch,
            BlockPos position,
            BlockState state,
            PoseStack pose,
            MultiBufferSource output,
            net.minecraft.world.level.Level level,
            boolean sides,
            int overlay,
            BlockRenderDispatcher blocks,
            long seed) {
        var model = blocks.getBlockModel(state);
        // Only the actual vanilla immutable baked-model and tint producer contract permits
        // skipping tessellation. Custom models still execute their loader path normally.
        if (!stable(owner, state, model)) {
            if (owner instanceof FallingBlockEntity) {
                for (var layer :
                        model.getRenderTypes(state, RandomSource.create(seed), ModelData.EMPTY))
                    blocks.getModelRenderer()
                            .tesselateBlock(
                                    level,
                                    model,
                                    state,
                                    position,
                                    pose,
                                    output.getBuffer(
                                            RenderTypeHelper.getMovingBlockRenderType(layer)),
                                    sides,
                                    RandomSource.create(),
                                    seed,
                                    overlay,
                                    ModelData.EMPTY,
                                    layer);
            } else
                ClientHooks.renderPistonMovedBlocks(
                        position, state, pose, output, level, sides, overlay, blocks);
            return;
        }
        for (var layer : model.getRenderTypes(state, RandomSource.create(seed), ModelData.EMPTY)) {
            var definition =
                    ((com.radiance.client.vertex.SourceBindings.Owner) owner)
                            .radiance$bindings()
                            .definition(new Slot(branch, layer), Definition::new);
            var input = new Input(model, state, position.immutable(), seed, sides, overlay);
            if (!input.equals(definition.input)
                    || definition.dependencies == null
                    || !definition.dependencies.matches(level)) {
                definition.input = input;
                definition.shape = new Object();
                definition.dependencies = null;
            }
            com.radiance.client.vertex.LocalMeshTakeover.request(
                    definition.shape,
                    pose.last(),
                    output.getBuffer(RenderTypeHelper.getMovingBlockRenderType(layer)),
                    0,
                    -1,
                    (p, out, l, c) -> {
                        var view = new VanillaBlockDependencies(level);
                        var stack = new PoseStack();
                        stack.last().pose().set(p.pose());
                        stack.last().normal().set(p.normal());
                        blocks.getModelRenderer()
                                .tesselateBlock(
                                        view,
                                        model,
                                        state,
                                        position,
                                        stack,
                                        out,
                                        sides,
                                        RandomSource.create(),
                                        seed,
                                        overlay,
                                        ModelData.EMPTY,
                                        layer);
                        definition.dependencies = view;
                    });
        }
    }

    public static boolean falling(
            FallingBlockEntity entity,
            BlockRenderDispatcher blocks,
            PoseStack pose,
            MultiBufferSource output) {
        var state = entity.getBlockState();
        var level = entity.level();
        if (state.getRenderShape() != RenderShape.MODEL
                || state == level.getBlockState(entity.blockPosition())) return false;
        var position =
                BlockPos.containing(entity.getX(), entity.getBoundingBox().maxY, entity.getZ());
        var model = blocks.getBlockModel(state);
        long seed = state.getSeed(entity.getStartPos());
        pose.pushPose();
        try {
            pose.translate(-.5, 0, -.5);
            request(
                    entity,
                    0,
                    position,
                    state,
                    pose,
                    output,
                    level,
                    false,
                    OverlayTexture.NO_OVERLAY,
                    blocks,
                    seed);
        } finally {
            pose.popPose();
        }
        return true;
    }

    public static void piston(
            PistonMovingBlockEntity entity,
            float partial,
            PoseStack pose,
            MultiBufferSource output,
            int overlay,
            BlockRenderDispatcher blocks) {
        var level = entity.getLevel();
        if (level == null) return;
        var position = entity.getBlockPos().relative(entity.getMovementDirection().getOpposite());
        BlockState state = entity.getMovedState();
        if (state.isAir()) return;
        ModelBlockRenderer.enableCaching();
        pose.pushPose();
        try {
            pose.translate(
                    entity.getXOff(partial), entity.getYOff(partial), entity.getZOff(partial));
            if (state.is(Blocks.PISTON_HEAD) && entity.getProgress(partial) <= 4.0F) {
                state = state.setValue(PistonHeadBlock.SHORT, entity.getProgress(partial) <= .5F);
                request(
                        entity,
                        0,
                        position,
                        state,
                        pose,
                        output,
                        level,
                        false,
                        overlay,
                        blocks,
                        state.getSeed(position));
            } else if (entity.isSourcePiston() && !entity.isExtending()) {
                var type = state.is(Blocks.STICKY_PISTON) ? PistonType.STICKY : PistonType.DEFAULT;
                var head =
                        Blocks.PISTON_HEAD
                                .defaultBlockState()
                                .setValue(PistonHeadBlock.TYPE, type)
                                .setValue(
                                        PistonHeadBlock.FACING,
                                        state.getValue(PistonBaseBlock.FACING))
                                .setValue(
                                        PistonHeadBlock.SHORT, entity.getProgress(partial) >= .5F);
                request(
                        entity,
                        0,
                        position,
                        head,
                        pose,
                        output,
                        level,
                        false,
                        overlay,
                        blocks,
                        head.getSeed(position));
                pose.popPose();
                pose.pushPose();
                state = state.setValue(PistonBaseBlock.EXTENDED, true);
                var base = position.relative(entity.getMovementDirection());
                request(
                        entity,
                        1,
                        base,
                        state,
                        pose,
                        output,
                        level,
                        true,
                        overlay,
                        blocks,
                        state.getSeed(base));
            } else {
                request(
                        entity,
                        0,
                        position,
                        state,
                        pose,
                        output,
                        level,
                        false,
                        overlay,
                        blocks,
                        state.getSeed(position));
            }
        } finally {
            pose.popPose();
            ModelBlockRenderer.clearCache();
        }
    }
}
