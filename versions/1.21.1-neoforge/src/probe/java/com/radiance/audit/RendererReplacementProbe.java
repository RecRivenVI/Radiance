package com.radiance.audit;

import com.mojang.blaze3d.vertex.*;
import com.radiance.adapter.vanilla.VanillaLivingReplacement;
import com.radiance.client.proxy.world.EntityProxy;
import com.radiance.client.vertex.PBRVertexConsumer;
import com.radiance.client.vertex.StorageVertexConsumerProvider;
import java.nio.ByteOrder;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;

/** Independent renderer execution is confined to the functional probe, never a timed window. */
public final class RendererReplacementProbe {
    private record PartInput(
            Object part,
            PoseStack.Pose pose,
            net.minecraft.client.renderer.RenderType layer,
            int light,
            int overlay,
            int color) {}

    private static List<PartInput> referenceInputs, candidateInputs;

    public static void referencePart(
            Object part,
            List<net.minecraft.client.model.geom.ModelPart.Cube> cubes,
            PoseStack.Pose pose,
            VertexConsumer consumer,
            int light,
            int overlay,
            int color) {
        if (referenceInputs != null && !cubes.isEmpty()) {
            var layer = sourceLayer(consumer);
            if (layer != null)
                referenceInputs.add(new PartInput(part, pose.copy(), layer, light, overlay, color));
        }
    }

    public static void candidateParts(
            List<com.radiance.adapter.vanilla.VanillaModelPose.Part> parts,
            VertexConsumer consumer,
            int light,
            int overlay) {
        if (candidateInputs != null)
            for (var part : parts)
                candidateInputs.add(
                        new PartInput(
                                part.part(),
                                part.pose(),
                                sourceLayer(consumer),
                                light,
                                overlay,
                                part.color()));
    }

    private static net.minecraft.client.renderer.RenderType sourceLayer(VertexConsumer consumer) {
        if (consumer instanceof com.radiance.client.vertex.PartModelCapture.SpriteAccess expanded)
            consumer = expanded.radiance$delegate();
        if (consumer instanceof PBRVertexConsumer.GLint glint) consumer = glint.delegate();
        if (!(consumer instanceof PBRVertexConsumer)) return null;
        try {
            return (net.minecraft.client.renderer.RenderType)
                    RigidModelProbe.field(consumer, "rigidLayer");
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException(failure);
        }
    }

    private RendererReplacementProbe() {}

    private static final class TestPlayer extends net.minecraft.client.player.RemotePlayer {
        private final net.minecraft.client.resources.PlayerSkin skin;
        private final net.minecraft.client.resources.PlayerSkin.Model shape;
        boolean cape, ears, spin;

        TestPlayer(Minecraft mc, net.minecraft.client.resources.PlayerSkin.Model shape) {
            super(
                    mc.level,
                    new com.mojang.authlib.GameProfile(
                            UUID.fromString("00000000-0000-0000-0000-000000001234"),
                            "Stage6Owner"));
            this.skin = mc.player.getSkin();
            this.shape = shape;
        }

        public net.minecraft.client.resources.PlayerSkin getSkin() {
            return new net.minecraft.client.resources.PlayerSkin(
                    skin.texture(),
                    skin.textureUrl(),
                    cape ? skin.texture() : null,
                    null,
                    shape,
                    false);
        }

        public net.minecraft.network.chat.Component getName() {
            return ears
                    ? net.minecraft.network.chat.Component.literal("deadmau5")
                    : super.getName();
        }

        public boolean isModelPartShown(net.minecraft.world.entity.player.PlayerModelPart part) {
            return true;
        }

        public boolean isAutoSpinAttack() {
            return spin;
        }

        void shoulders() {
            var left = new net.minecraft.nbt.CompoundTag();
            left.putString("id", "minecraft:parrot");
            left.putInt("Variant", 1);
            var right = left.copy();
            right.putInt("Variant", 4);
            setShoulderEntityLeft(left);
            setShoulderEntityRight(right);
        }
    }

    private static final class ChangedCow
            extends net.minecraft.client.model.CowModel<net.minecraft.world.entity.animal.Cow> {
        int renders;

        ChangedCow(net.minecraft.client.model.geom.ModelPart root) {
            super(root);
        }

        @Override
        public void renderToBuffer(
                PoseStack pose, VertexConsumer out, int light, int overlay, int color) {
            renders++;
            super.renderToBuffer(pose, out, light, overlay, 0xff40a0d0);
        }
    }

    public static Map<String, Object> compareEventMutation(Minecraft mc) {
        var entity = net.minecraft.world.entity.EntityType.COW.create(mc.level);
        var renderer =
                (net.minecraft.client.renderer.entity.LivingEntityRenderer<?, ?>)
                        mc.getEntityRenderDispatcher().getRenderer(entity);
        var original = renderer.getModel();
        var changed =
                new ChangedCow(
                        mc.getEntityModels()
                                .bakeLayer(net.minecraft.client.model.geom.ModelLayers.COW));
        java.util.function.Consumer<net.neoforged.neoforge.client.event.RenderLivingEvent.Pre>
                listener =
                        event -> {
                            if (event.getEntity() == entity)
                                ((VanillaLivingReplacement.StateAccess) event.getRenderer())
                                        .radiance$selectModel(changed);
                        };
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(listener);
        try (var reference = new Provider();
                var candidate = new Provider()) {
            if (!VanillaLivingReplacement.eligible(
                    mc.getEntityRenderDispatcher(), entity, candidate, candidate))
                throw new AssertionError("Event mutation preflight did not start from vanilla");
            var pose = new PoseStack();
            mc.getEntityRenderDispatcher()
                    .render(entity, 0, 0, 0, 0, .37F, pose, reference, 15728640);
            ((VanillaLivingReplacement.StateAccess) renderer).radiance$selectModel(original);
            candidate.declaredParts(entity.getId(), renderer.getClass().getName());
            VanillaLivingReplacement.render(
                    mc.getEntityRenderDispatcher(), entity, .37F, pose, candidate, 15728640);
            if (changed.renders != 2 || !candidate.takeRigidModels().isEmpty())
                throw new AssertionError(
                        "Changed event model bypassed/duplicated original execution");
            var a = meshes(reference);
            var b = meshes(candidate);
            if (a.size() != b.size() || a.isEmpty())
                throw new AssertionError("Changed event model draw count");
            long vertices = 0;
            for (int i = 0; i < a.size(); i++) {
                var x = a.get(i);
                var y = b.get(i);
                if (!sameLayer(x.layer, y.layer) || x.count != y.count)
                    throw new AssertionError("Changed event layer");
                var u = java.nio.ByteBuffer.wrap(x.bytes).order(ByteOrder.nativeOrder());
                var v = java.nio.ByteBuffer.wrap(y.bytes).order(ByteOrder.nativeOrder());
                for (int quad = 0; quad < x.count / 4; quad++)
                    if (!sameQuad(u, v, quad, quad))
                        throw new AssertionError("Changed event model output");
                vertices += x.count;
            }
            return Map.of(
                    "difference",
                    "identical source output",
                    "changedRendererCalls",
                    changed.renders,
                    "vertices",
                    vertices,
                    "directResources",
                    0);
        } finally {
            net.neoforged.neoforge.common.NeoForge.EVENT_BUS.unregister(listener);
            ((VanillaLivingReplacement.StateAccess) renderer).radiance$selectModel(original);
        }
    }

    public static Map<String, Object> compare(Minecraft mc, String source) {
        if (source.startsWith("ber/")) return compareBlock(mc, source);
        if (source.startsWith("player/")) {
            var shape =
                    net.minecraft.client.resources.PlayerSkin.Model.valueOf(source.substring(7));
            var player = new TestPlayer(mc, shape);
            return compareVariants(mc, source, player);
        }
        if (!source.startsWith("entity/")) return Map.of("route", "NOT_ENTITY");
        var id = ResourceLocation.parse(source.substring("entity/".length()));
        var value = BuiltInRegistries.ENTITY_TYPE.get(id).create(mc.level);
        if (!(value instanceof LivingEntity entity)) {
            var result = compareInstance(mc, source, value);
            var variants = new LinkedHashMap<String, Object>();
            variants.put("default", result);
            if (value instanceof net.minecraft.world.entity.boss.enderdragon.EndCrystal crystal) {
                crystal.setBeamTarget(new net.minecraft.core.BlockPos(5, 74, -8));
                variants.put("target-beam", compareInstance(mc, source + "/target-beam", crystal));
            }
            if (value instanceof net.minecraft.world.entity.decoration.ItemFrame frame) {
                var map =
                        new net.minecraft.world.item.ItemStack(
                                net.minecraft.world.item.Items.FILLED_MAP);
                var mapId = new net.minecraft.world.level.saveddata.maps.MapId(130);
                var data =
                        net.minecraft.world.level.saveddata.maps.MapItemSavedData.createForClient(
                                (byte) 0, false, mc.level.dimension());
                for (int i = 0; i < data.colors.length; i++) data.colors[i] = (byte) (4 + i % 220);
                try {
                    var decorations =
                            (Map<String, net.minecraft.world.level.saveddata.maps.MapDecoration>)
                                    RigidModelProbe.field(data, "decorations");
                    decorations.put(
                            "named",
                            new net.minecraft.world.level.saveddata.maps.MapDecoration(
                                    net.minecraft.world.level.saveddata.maps.MapDecorationTypes
                                            .RED_MARKER,
                                    (byte) -30,
                                    (byte) 48,
                                    (byte) 7,
                                    Optional.of(
                                            net.minecraft.network.chat.Component.literal(
                                                    "Map 130"))));
                } catch (ReflectiveOperationException failure) {
                    throw new IllegalStateException(failure);
                }
                mc.level.overrideMapData(mapId, data);
                map.set(net.minecraft.core.component.DataComponents.MAP_ID, mapId);
                frame.setItem(map);
                frame.setRotation(3);
                variants.put("valid-map", compareInstance(mc, source + "/valid-map", frame));
                data.colors[10] = (byte) 84;
                mc.gameRenderer.getMapRenderer().update(mapId, data);
                variants.put("changed-map", compareInstance(mc, source + "/changed-map", frame));
            }
            if (value instanceof net.minecraft.world.entity.vehicle.Boat boat) {
                for (var variant : net.minecraft.world.entity.vehicle.Boat.Type.values()) {
                    boat.setVariant(variant);
                    boat.setHurtTime(8);
                    boat.setDamage(6);
                    variants.put(
                            "boat-" + variant, compareInstance(mc, source + "/" + variant, value));
                }
            }
            if (value instanceof net.minecraft.world.entity.decoration.ItemFrame
                    || value instanceof net.minecraft.world.entity.item.ItemEntity) {
                for (var item :
                        List.of(
                                net.minecraft.world.item.Items.STONE,
                                net.minecraft.world.item.Items.DIAMOND_SWORD)) {
                    var stack = new net.minecraft.world.item.ItemStack(item, 32);
                    if (item == net.minecraft.world.item.Items.DIAMOND_SWORD)
                        stack.enchant(
                                mc.level
                                        .registryAccess()
                                        .registryOrThrow(
                                                net.minecraft.core.registries.Registries
                                                        .ENCHANTMENT)
                                        .getHolderOrThrow(
                                                net.minecraft.world.item.enchantment.Enchantments
                                                        .UNBREAKING),
                                1);
                    if (value instanceof net.minecraft.world.entity.decoration.ItemFrame frame) {
                        frame.setItem(stack);
                        frame.setRotation(3);
                    }
                    if (value instanceof net.minecraft.world.entity.item.ItemEntity dropped)
                        dropped.setItem(stack);
                    variants.put("item-" + item, compareInstance(mc, source + "/" + item, value));
                }
            }
            if (value instanceof net.minecraft.world.entity.projectile.EvokerFangs fangs) {
                fangs.handleEntityEvent((byte) 4);
                for (int i = 0; i < 6; i++) fangs.tick();
                variants.put("active-fangs", compareInstance(mc, source + "/active", value));
            }
            if (value instanceof net.minecraft.world.entity.projectile.AbstractArrow arrow) {
                arrow.shakeTime = 5;
                variants.put("shake", compareInstance(mc, source + "/shake", value));
            }
            if (value instanceof net.minecraft.world.entity.projectile.FishingHook hook) {
                hook.setOwner(mc.player);
                variants.put("owned-hook", compareInstance(mc, source + "/owned", value));
            }
            if (value instanceof net.minecraft.world.entity.LightningBolt bolt) {
                for (long seed : new long[] {17, 45, 17}) {
                    bolt.seed = seed;
                    variants.put(
                            "seed-" + seed, compareInstance(mc, source + "/seed-" + seed, value));
                }
            }
            if (value instanceof net.minecraft.world.entity.Display display) {
                for (int variant = 0; variant < 3; variant++) {
                    var tag = new net.minecraft.nbt.CompoundTag();
                    display.saveWithoutId(tag);
                    if (display instanceof net.minecraft.world.entity.Display.BlockDisplay)
                        tag.put(
                                "block_state",
                                net.minecraft.nbt.NbtUtils.writeBlockState(
                                        (variant == 1
                                                        ? net.minecraft.world.level.block.Blocks
                                                                .GLASS
                                                        : net.minecraft.world.level.block.Blocks
                                                                .STONE)
                                                .defaultBlockState()));
                    if (display instanceof net.minecraft.world.entity.Display.ItemDisplay) {
                        tag.put(
                                "item",
                                new net.minecraft.world.item.ItemStack(
                                                variant == 1
                                                        ? net.minecraft.world.item.Items.COMPASS
                                                        : net.minecraft.world.item.Items
                                                                .DIAMOND_SWORD)
                                        .save(mc.level.registryAccess()));
                        tag.putString("item_display", variant == 1 ? "ground" : "fixed");
                    }
                    if (display instanceof net.minecraft.world.entity.Display.TextDisplay) {
                        tag.putString(
                                "text",
                                net.minecraft.network.chat.Component.Serializer.toJson(
                                        net.minecraft.network.chat.Component.literal(
                                                "Radiance " + variant),
                                        mc.level.registryAccess()));
                        tag.putByte("text_opacity", (byte) (variant == 1 ? 80 : 255));
                        tag.putBoolean("see_through", variant == 2);
                        tag.putInt("line_width", 45);
                    }
                    tag.putString(
                            "billboard",
                            variant == 0 ? "fixed" : variant == 1 ? "center" : "vertical");
                    display.load(tag);
                    display.tick();
                    variants.put(
                            "display-" + variant,
                            compareInstance(mc, source + "/display-" + variant, value));
                }
            }
            return Map.of("route", result.get("route"), "variants", variants);
        }
        return compareVariants(mc, source, entity);
    }

    private static Map<String, Object> compareVariants(
            Minecraft mc, String source, LivingEntity entity) {
        var results = new LinkedHashMap<String, Object>();
        results.put("adult", compareInstance(mc, source, entity));
        if (entity instanceof net.minecraft.world.entity.boss.enderdragon.EnderDragon dragon) {
            var crystal = net.minecraft.world.entity.EntityType.END_CRYSTAL.create(mc.level);
            crystal.setPos(4, 75, -7);
            dragon.nearestCrystal = crystal;
            results.put(
                    "connected-crystal",
                    compareInstance(mc, source + "/connected-crystal", dragon));
            dragon.nearestCrystal = null;
            for (int death : new int[] {1, 80, 190}) {
                dragon.dragonDeathTime = death;
                results.put(
                        "death-" + death, compareInstance(mc, source + "/death-" + death, dragon));
            }
        }
        if (entity instanceof net.minecraft.world.entity.monster.Illusioner illusioner) {
            illusioner.setInvisible(true);
            illusioner.setAggressive(true);
            illusioner.setItemSlot(
                    net.minecraft.world.entity.EquipmentSlot.MAINHAND,
                    new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.BOW));
            try {
                var offsets =
                        (net.minecraft.world.phys.Vec3[][])
                                RigidModelProbe.field(illusioner, "clientSideIllusionOffsets");
                for (int i = 0; i < 4; i++) {
                    offsets[0][i] = new net.minecraft.world.phys.Vec3(i * .2, -i * .1, i * .3);
                    offsets[1][i] = new net.minecraft.world.phys.Vec3(-i * .15, i * .3, -i * .1);
                }
            } catch (ReflectiveOperationException failure) {
                throw new IllegalStateException(failure);
            }
            results.put("invisible-copies", compareInstance(mc, source + "/copies", entity));
            illusioner.setInvisible(false);
        }
        if (entity instanceof net.minecraft.world.entity.monster.Guardian guardian) {
            try {
                var method =
                        net.minecraft.world.entity.monster.Guardian.class.getDeclaredMethod(
                                "setActiveAttackTarget", int.class);
                method.setAccessible(true);
                method.invoke(guardian, mc.player.getId());
                var target = net.minecraft.world.entity.EntityType.COW.create(mc.level);
                target.setPos(2, 70, 4);
                var cached =
                        net.minecraft.world.entity.monster.Guardian.class.getDeclaredField(
                                "clientSideCachedAttackTarget");
                cached.setAccessible(true);
                cached.set(guardian, target);
                var time =
                        net.minecraft.world.entity.monster.Guardian.class.getDeclaredField(
                                "clientSideAttackTime");
                time.setAccessible(true);
                time.setInt(guardian, 35);
                results.put("active-beam", compareInstance(mc, source + "/beam", entity));
                method.invoke(guardian, 0);
            } catch (ReflectiveOperationException failure) {
                throw new IllegalStateException(failure);
            }
        }
        if (entity instanceof TestPlayer player) {
            player.cape = true;
            player.xCloakO = -.1;
            player.xCloak = .2;
            player.yCloakO = .1;
            player.yCloak = .3;
            player.zCloakO = -.2;
            player.zCloak = .4;
            results.put("cape", compareInstance(mc, source + "/cape", entity));
            player.cape = false;
            player.ears = true;
            results.put("ears", compareInstance(mc, source + "/ears", entity));
            player.ears = false;
            player.shoulders();
            results.put("shoulder-parrots", compareInstance(mc, source + "/shoulders", entity));
            results.put("name-and-score", compareName(mc, entity));
            player.setArrowCount(3);
            player.setStingerCount(2);
            results.put("stuck-arrows-stingers", compareInstance(mc, source + "/stuck", entity));
            player.setArrowCount(0);
            player.setStingerCount(0);
            player.spin = true;
            results.put("riptide", compareInstance(mc, source + "/riptide", entity));
            player.spin = false;
            player.setItemSlot(
                    net.minecraft.world.entity.EquipmentSlot.MAINHAND,
                    new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.TRIDENT));
            player.setItemSlot(
                    net.minecraft.world.entity.EquipmentSlot.OFFHAND,
                    new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.SHIELD));
            results.put("builtin-hands", compareInstance(mc, source + "/builtin-hands", entity));
            player.setItemSlot(
                    net.minecraft.world.entity.EquipmentSlot.MAINHAND,
                    new net.minecraft.world.item.ItemStack(
                            net.minecraft.world.item.Items.SPYGLASS));
            player.startUsingItem(net.minecraft.world.InteractionHand.MAIN_HAND);
            results.put("spyglass", compareInstance(mc, source + "/spyglass", entity));
            player.stopUsingItem();
            player.setItemSlot(
                    net.minecraft.world.entity.EquipmentSlot.MAINHAND,
                    net.minecraft.world.item.ItemStack.EMPTY);
            player.setItemSlot(
                    net.minecraft.world.entity.EquipmentSlot.OFFHAND,
                    net.minecraft.world.item.ItemStack.EMPTY);
        }
        if (entity instanceof net.minecraft.world.entity.animal.Fox
                || entity instanceof net.minecraft.world.entity.animal.Dolphin
                || entity instanceof net.minecraft.world.entity.animal.Panda) {
            entity.setItemSlot(
                    net.minecraft.world.entity.EquipmentSlot.MAINHAND,
                    new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.COD));
            if (entity instanceof net.minecraft.world.entity.animal.Panda panda) {
                panda.sit(true);
                panda.eat(true);
            }
            results.put("carried-item", compareInstance(mc, source + "/carried-item", entity));
            entity.setItemSlot(
                    net.minecraft.world.entity.EquipmentSlot.MAINHAND,
                    net.minecraft.world.item.ItemStack.EMPTY);
        }
        if (entity instanceof net.minecraft.world.entity.animal.Cow
                || entity instanceof net.minecraft.world.entity.decoration.ArmorStand
                || entity instanceof net.minecraft.client.player.AbstractClientPlayer)
            results.put("visible-outline", compareOutline(mc, source, entity));
        if (entity instanceof net.minecraft.world.entity.AgeableMob young) {
            young.setAge(-24000);
            results.put("young", compareInstance(mc, source + "/young", entity));
            young.setAge(0);
        }
        if (entity instanceof net.minecraft.world.entity.decoration.ArmorStand
                || entity instanceof net.minecraft.world.entity.monster.Zombie
                || entity instanceof net.minecraft.world.entity.player.Player) {
            for (var slot :
                    List.of(
                            net.minecraft.world.entity.EquipmentSlot.HEAD,
                            net.minecraft.world.entity.EquipmentSlot.CHEST,
                            net.minecraft.world.entity.EquipmentSlot.LEGS,
                            net.minecraft.world.entity.EquipmentSlot.FEET)) {
                var item =
                        switch (slot) {
                            case HEAD -> net.minecraft.world.item.Items.LEATHER_HELMET;
                            case CHEST -> net.minecraft.world.item.Items.LEATHER_CHESTPLATE;
                            case LEGS -> net.minecraft.world.item.Items.LEATHER_LEGGINGS;
                            default -> net.minecraft.world.item.Items.LEATHER_BOOTS;
                        };
                var stack = new net.minecraft.world.item.ItemStack(item);
                stack.set(
                        net.minecraft.core.component.DataComponents.DYED_COLOR,
                        new net.minecraft.world.item.component.DyedItemColor(0x40a0ff, true));
                stack.enchant(
                        mc.level
                                .registryAccess()
                                .registryOrThrow(
                                        net.minecraft.core.registries.Registries.ENCHANTMENT)
                                .getHolderOrThrow(
                                        net.minecraft.world.item.enchantment.Enchantments
                                                .PROTECTION),
                        1);
                stack.set(
                        net.minecraft.core.component.DataComponents.TRIM,
                        new net.minecraft.world.item.armortrim.ArmorTrim(
                                mc.level
                                        .registryAccess()
                                        .registryOrThrow(
                                                net.minecraft.core.registries.Registries
                                                        .TRIM_MATERIAL)
                                        .getHolderOrThrow(
                                                net.minecraft.world.item.armortrim.TrimMaterials
                                                        .GOLD),
                                mc.level
                                        .registryAccess()
                                        .registryOrThrow(
                                                net.minecraft.core.registries.Registries
                                                        .TRIM_PATTERN)
                                        .getHolderOrThrow(
                                                net.minecraft.world.item.armortrim.TrimPatterns
                                                        .SENTRY)));
                entity.setItemSlot(slot, stack);
            }
            var armor = compareInstance(mc, source + "/armor", entity);
            if (!"RENDERER_REPLACED_COMPARED".equals(armor.get("route")))
                throw new AssertionError(source + " armor remained captured");
            results.put("dyed-trimmed-foil-armor", armor);
            entity.setItemSlot(
                    net.minecraft.world.entity.EquipmentSlot.MAINHAND,
                    new net.minecraft.world.item.ItemStack(
                            net.minecraft.world.item.Items.DIAMOND_SWORD));
            entity.setItemSlot(
                    net.minecraft.world.entity.EquipmentSlot.OFFHAND,
                    new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.BOW));
            var hands = compareInstance(mc, source + "/hands", entity);
            if (!"RENDERER_REPLACED_COMPARED".equals(hands.get("route")))
                throw new AssertionError(source + " hands remained captured");
            results.put("held-items", hands);
            for (var head :
                    List.of(
                            net.minecraft.world.item.Items.OAK_PLANKS,
                            net.minecraft.world.item.Items.DRAGON_HEAD)) {
                entity.setItemSlot(
                        net.minecraft.world.entity.EquipmentSlot.HEAD,
                        new net.minecraft.world.item.ItemStack(head));
                var result = compareInstance(mc, source + "/head/" + head, entity);
                if (!"RENDERER_REPLACED_COMPARED".equals(result.get("route")))
                    throw new AssertionError(source + " head item remained captured");
                results.put("head/" + head, result);
            }
            entity.setItemSlot(
                    net.minecraft.world.entity.EquipmentSlot.HEAD,
                    net.minecraft.world.item.ItemStack.EMPTY);
            entity.setItemSlot(
                    net.minecraft.world.entity.EquipmentSlot.CHEST,
                    new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.ELYTRA));
            results.put("elytra", compareInstance(mc, source + "/elytra", entity));
        }
        if (entity instanceof net.minecraft.world.entity.monster.Creeper) {
            var tag = new net.minecraft.nbt.CompoundTag();
            entity.saveWithoutId(tag);
            tag.putBoolean("powered", true);
            entity.load(tag);
            results.put("powered-energy", compareInstance(mc, source + "/powered", entity));
        }
        if (entity instanceof net.minecraft.world.entity.animal.Pufferfish fish) {
            for (int state : new int[] {0, 1, 2, 0}) {
                fish.setPuffState(state);
                results.put(
                        "puff-" + state, compareInstance(mc, source + "/puff-" + state, entity));
            }
        }
        if (entity instanceof net.minecraft.world.entity.animal.TropicalFish fish) {
            for (var pattern : net.minecraft.world.entity.animal.TropicalFish.Pattern.values()) {
                fish.setVariant(pattern);
                results.put(
                        "fish-" + pattern,
                        compareInstance(mc, source + "/fish-" + pattern, entity));
            }
        }
        return Map.of(
                "route", ((Map<?, ?>) results.get("adult")).get("route"), "variants", results);
    }

    private static Map<String, Object> compareOutline(
            Minecraft mc, String source, LivingEntity entity) {
        var dispatcher = mc.getEntityRenderDispatcher();
        try (var reference = new Provider();
                var candidate = new Provider();
                var refEdges = new Provider();
                var candidateEdges = new Provider()) {
            if (!VanillaLivingReplacement.eligible(dispatcher, entity, candidate, candidate))
                return Map.of("route", "CAPTURE");
            candidate.declaredParts(
                    entity.getId(), dispatcher.getRenderer(entity).getClass().getName());
            var refOut =
                    new com.radiance.client.vertex.StorageOutlineVertexConsumerProvider(
                            reference, refEdges);
            var newOut =
                    new com.radiance.client.vertex.StorageOutlineVertexConsumerProvider(
                            candidate, candidateEdges);
            refOut.setColor(21, 110, 224, 255);
            newOut.setColor(21, 110, 224, 255);
            var pose = new PoseStack();
            pose.mulPose(new org.joml.Quaternionf().rotationXYZ(.13F, -.21F, .31F));
            dispatcher.render(
                    entity,
                    0,
                    0,
                    0,
                    net.minecraft.util.Mth.lerp(.37F, entity.yRotO, entity.getYRot()),
                    .37F,
                    pose,
                    refOut,
                    15728640);
            VanillaLivingReplacement.render(
                    dispatcher, entity, .37F, pose, candidate, newOut, 15728640);
            var a = meshes(reference);
            var b = meshes(candidate);
            if (a.size() != b.size()) throw new AssertionError(source + " outline body layers");
            for (int i = 0; i < a.size(); i++) {
                var x = a.get(i);
                var y = b.get(i);
                if (x.count != y.count || !sameLayer(x.layer, y.layer))
                    throw new AssertionError(source + " outline body topology");
                var xb = java.nio.ByteBuffer.wrap(x.bytes).order(ByteOrder.nativeOrder());
                var yb = java.nio.ByteBuffer.wrap(y.bytes).order(ByteOrder.nativeOrder());
                for (int q = 0; q < x.count / 4; q++)
                    if (!sameQuad(xb, yb, q, q))
                        throw new AssertionError(source + " outline body material");
            }
            refEdges.endBatch();
            candidateEdges.endBatch();
            var x = refEdges.getLayerBatches().iterator();
            var y = candidateEdges.getLayerBatches().iterator();
            long count = 0;
            while (x.hasNext() && y.hasNext()) {
                var left =
                        (com.radiance.client.vertex.CapturedLineVertexConsumer) x.next().getValue();
                var right =
                        (com.radiance.client.vertex.CapturedLineVertexConsumer) y.next().getValue();
                try (var u = left.build();
                        var v = right.build()) {
                    if (u == null && v == null) continue;
                    if (u == null || v == null)
                        throw new AssertionError(source + " outline empty/nonempty mismatch");
                    if (u.drawState().vertexCount() != v.drawState().vertexCount())
                        throw new AssertionError(source + " outline edge count");
                    var ub = u.vertexBuffer().order(ByteOrder.nativeOrder());
                    var vb = v.vertexBuffer().order(ByteOrder.nativeOrder());
                    for (int i = 0; i < u.drawState().vertexCount(); i++) {
                        int at = i * com.radiance.client.vertex.CapturedLineVertexConsumer.STRIDE;
                        if (distance(ub, vb, at, at) > 3e-5)
                            throw new AssertionError(source + " outline position");
                        for (int field = 12; field < 19; field++)
                            if (ub.get(at + field) != vb.get(at + field))
                                throw new AssertionError(source + " outline colour/normal");
                        for (int field = 20; field < 56; field += 4)
                            if (Math.abs(ub.getFloat(at + field) - vb.getFloat(at + field)) > 3e-5)
                                throw new AssertionError(source + " outline frame");
                    }
                    count += u.drawState().vertexCount();
                }
            }
            if (x.hasNext() || y.hasNext() || count == 0)
                throw new AssertionError(source + " outline missing output");
            return Map.of("route", "RENDERER_REPLACED_COMPARED", "lineVertices", count);
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static Map<String, Object> compareBlock(Minecraft mc, String source) {
        var type =
                BuiltInRegistries.BLOCK_ENTITY_TYPE.get(
                        ResourceLocation.parse(source.substring(4)));
        var state =
                BuiltInRegistries.BLOCK.stream()
                        .map(net.minecraft.world.level.block.Block::defaultBlockState)
                        .filter(type::isValid)
                        .findFirst()
                        .orElseThrow();
        var value = type.create(new net.minecraft.core.BlockPos(0, 69, 0), state);
        var renderer =
                (net.minecraft.client.renderer.blockentity.BlockEntityRenderer)
                        mc.getBlockEntityRenderDispatcher().getRenderer(value);
        if (!com.radiance.adapter.vanilla.VanillaBlockReplacement.eligible(renderer, value))
            return Map.of(
                    "route",
                    "CAPTURE",
                    "reason",
                    "World BER has no direct source adapter: " + renderer.getClass().getName());
        var variants = new ArrayList<Map<String, Object>>();
        for (int variant = 0; variant < 3; variant++) {
            if (value instanceof net.minecraft.world.level.block.piston.PistonMovingBlockEntity) {
                var moved =
                        variant == 0
                                ? net.minecraft.world.level.block.Blocks.PISTON_HEAD
                                        .defaultBlockState()
                                : variant == 1
                                        ? net.minecraft.world.level.block.Blocks.PISTON
                                                .defaultBlockState()
                                        : net.minecraft.world.level.block.Blocks.GLASS
                                                .defaultBlockState();
                value =
                        new net.minecraft.world.level.block.piston.PistonMovingBlockEntity(
                                value.getBlockPos(),
                                net.minecraft.world.level.block.Blocks.MOVING_PISTON
                                        .defaultBlockState(),
                                moved,
                                net.minecraft.core.Direction.EAST,
                                variant != 1,
                                true);
                value.setLevel(mc.level);
                setProbeField(value, "progressO", variant * .2F);
                setProbeField(value, "progress", variant * .4F);
            }
            if (value
                    instanceof
                    net.minecraft.world.level.block.entity.StructureBlockEntity structure) {
                structure.setLevel(mc.level);
                structure.setMode(
                        net.minecraft.world.level.block.state.properties.StructureMode.SAVE);
                structure.setStructureSize(new net.minecraft.core.Vec3i(3, 2, 4));
                structure.setStructurePos(new net.minecraft.core.BlockPos(1, 0, 1));
                structure.setShowAir(variant == 2);
                structure.setRotation(net.minecraft.world.level.block.Rotation.values()[variant]);
                structure.setMirror(net.minecraft.world.level.block.Mirror.values()[variant]);
            }
            if (value instanceof net.minecraft.world.level.block.entity.BeaconBlockEntity beacon) {
                beacon.setLevel(mc.level);
                setProbeField(beacon, "levels", variant == 0 ? 0 : 4);
                var first =
                        new net.minecraft.world.level.block.entity.BeaconBlockEntity
                                .BeaconBeamSection(0xffff4040);
                var second =
                        new net.minecraft.world.level.block.entity.BeaconBlockEntity
                                .BeaconBeamSection(0xff40a0ff);
                setProbeField(first, "height", 10);
                setProbeField(beacon, "beamSections", List.of(first, second));
            }
            if (value instanceof net.minecraft.world.level.block.entity.TheEndPortalBlockEntity)
                value.setLevel(mc.level);
            if (value
                    instanceof
                    net.minecraft.world.level.block.entity.TheEndGatewayBlockEntity gateway) {
                setProbeField(gateway, "age", variant == 0 ? 250L : 100L);
                setProbeField(gateway, "teleportCooldown", variant == 2 ? 20 : 0);
            }
            if (value
                    instanceof net.minecraft.world.level.block.entity.ConduitBlockEntity conduit) {
                setProbeField(conduit, "isActive", variant != 0);
                setProbeField(conduit, "isHunting", variant == 2);
                conduit.tickCount = variant * 66;
            }
            if (value
                    instanceof net.minecraft.world.level.block.entity.DecoratedPotBlockEntity pot) {
                pot.setLevel(mc.level);
                pot.lastWobbleStyle =
                        variant == 0
                                ? null
                                : variant == 1
                                        ? net.minecraft.world.level.block.entity
                                                .DecoratedPotBlockEntity.WobbleStyle.POSITIVE
                                        : net.minecraft.world.level.block.entity
                                                .DecoratedPotBlockEntity.WobbleStyle.NEGATIVE;
                pot.wobbleStartedAtTick = mc.level.getGameTime() - 4;
            }
            if (value
                    instanceof
                    net.minecraft.world.level.block.entity.CampfireBlockEntity campfire) {
                campfire.setLevel(mc.level);
                campfire.setBlockState(
                        campfire.getBlockState()
                                .setValue(
                                        net.minecraft.world.level.block.CampfireBlock.FACING,
                                        net.minecraft.core.Direction.from2DDataValue(variant)));
                var items =
                        List.of(
                                net.minecraft.world.item.Items.COD,
                                net.minecraft.world.item.Items.BEEF,
                                net.minecraft.world.item.Items.CARROT,
                                net.minecraft.world.item.Items.BOW);
                for (int i = 0; i < items.size(); i++)
                    campfire.getItems()
                            .set(i, new net.minecraft.world.item.ItemStack(items.get(i)));
            }
            if (value
                    instanceof net.minecraft.world.level.block.entity.BrushableBlockEntity brush) {
                brush.setLevel(mc.level);
                brush.setBlockState(
                        brush.getBlockState()
                                .setValue(
                                        net.minecraft.world.level.block.state.properties
                                                .BlockStateProperties.DUSTED,
                                        variant));
                setProbeField(
                        brush,
                        "item",
                        new net.minecraft.world.item.ItemStack(
                                net.minecraft.world.item.Items.DIAMOND_SWORD));
                setProbeField(
                        brush,
                        "hitDirection",
                        new net.minecraft.core.Direction[] {
                                    net.minecraft.core.Direction.UP,
                                    net.minecraft.core.Direction.EAST,
                                    net.minecraft.core.Direction.SOUTH
                                }
                                [variant]);
            }
            if (value instanceof net.minecraft.world.level.block.entity.SpawnerBlockEntity
                    || value
                            instanceof
                            net.minecraft.world.level.block.entity.TrialSpawnerBlockEntity)
                value.setLevel(mc.level);
            if (value instanceof net.minecraft.world.level.block.entity.SignBlockEntity sign) {
                sign.setLevel(mc.level);
                var text =
                        new net.minecraft.world.level.block.entity.SignText()
                                .setMessage(
                                        0, net.minecraft.network.chat.Component.literal("Radiance"))
                                .setMessage(
                                        1,
                                        net.minecraft.network.chat.Component.literal("Reload 123"))
                                .setColor(
                                        variant == 1
                                                ? net.minecraft.world.item.DyeColor.BLACK
                                                : net.minecraft.world.item.DyeColor.BLUE)
                                .setHasGlowingText(variant != 0);
                sign.setText(text, true);
                sign.setText(text.setColor(net.minecraft.world.item.DyeColor.RED), false);
            }
            if (value instanceof net.minecraft.world.level.block.entity.BellBlockEntity bell) {
                bell.shaking = variant != 0;
                bell.ticks = 3 + variant;
                bell.clickDirection =
                        variant == 1
                                ? net.minecraft.core.Direction.NORTH
                                : net.minecraft.core.Direction.EAST;
            }
            if (value
                    instanceof
                    net.minecraft.world.level.block.entity.EnchantingTableBlockEntity book) {
                book.time = 52;
                book.oRot = 3.1F;
                book.rot = -3.1F;
                book.oFlip = .2F;
                book.flip = .8F;
                book.oOpen = .1F;
                book.open = .9F;
            }
            if (value instanceof net.minecraft.world.level.block.entity.LecternBlockEntity)
                value.setBlockState(
                        value.getBlockState()
                                .setValue(
                                        net.minecraft.world.level.block.LecternBlock.HAS_BOOK,
                                        variant != 0));
            float partial = new float[] {0, .37F, .91F}[variant];
            try (var reference = new Provider();
                    var candidate = new Provider()) {
                var pose = new PoseStack();
                pose.translate(.25, -.75, 1.5);
                pose.mulPose(new org.joml.Quaternionf().rotationXYZ(.13F, -.21F, .31F));
                referenceInputs = new ArrayList<>();
                renderer.render(
                        value,
                        partial,
                        pose,
                        reference,
                        15728640,
                        net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY);
                var originalParts = referenceInputs;
                referenceInputs = null;
                candidateInputs = new ArrayList<>();
                candidate.declaredParts(
                        System.identityHashCode(value), renderer.getClass().getName());
                candidate.sourceOwner = value;
                var requests = new com.radiance.client.vertex.BoundPartCapture(candidate);
                ((com.radiance.adapter.vanilla.VanillaBlockReplacement.Source) renderer)
                        .radiance$collect(
                                value,
                                partial,
                                pose,
                                candidate,
                                requests,
                                15728640,
                                net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY);
                requests.submit();
                var replacementParts = candidateInputs;
                candidateInputs = null;
                if (originalParts.size() != replacementParts.size())
                    throw new AssertionError(source + " BER part count");
                for (int i = 0; i < originalParts.size(); i++) {
                    var a = originalParts.get(i);
                    var b = replacementParts.get(i);
                    if (a.part != b.part
                            || !sameLayer(a.layer, b.layer)
                            || a.color != b.color
                            || a.light != b.light
                            || a.overlay != b.overlay
                            || !a.pose.pose().equals(b.pose.pose(), 3e-5F)
                            || !a.pose.normal().equals(b.pose.normal(), 5e-5F))
                        throw new AssertionError(source + " BER ordered input " + i);
                }
                var a = meshes(reference);
                var b = meshes(candidate);
                long vertices = 0;
                if (a.size() != b.size()) throw new AssertionError(source + " BER draw count");
                for (int i = 0; i < a.size(); i++) {
                    var x = a.get(i);
                    var y = b.get(i);
                    if (!sameLayer(x.layer, y.layer)
                            || x.count != y.count
                            || !x.state.equals(y.state))
                        throw new AssertionError(source + " BER layer state");
                    if (x.stride != 128 || y.stride != 128) {
                        if (x.stride != y.stride || !Arrays.equals(x.bytes, y.bytes))
                            throw new AssertionError(source + " BER line request");
                        vertices += x.count;
                        continue;
                    }
                    var xbuf = java.nio.ByteBuffer.wrap(x.bytes).order(ByteOrder.nativeOrder());
                    var ybuf = java.nio.ByteBuffer.wrap(y.bytes).order(ByteOrder.nativeOrder());
                    boolean[] found = new boolean[y.count / 4];
                    for (int q = 0; q < x.count / 4; q++) {
                        int match = -1;
                        for (int r = 0; r < found.length; r++)
                            if (!found[r] && sameQuad(xbuf, ybuf, q, r)) {
                                match = r;
                                break;
                            }
                        if (match < 0) throw new AssertionError(source + " BER quad " + q);
                        found[match] = true;
                    }
                    vertices += x.count;
                }
                variants.add(
                        Map.of(
                                "partial",
                                partial,
                                "parts",
                                originalParts.size(),
                                "vertices",
                                vertices,
                                "difference",
                                "within position 3e-5 and normal 5e-5; other bytes exact"));
            } finally {
                referenceInputs = candidateInputs = null;
            }
        }
        return Map.of("route", "RENDERER_REPLACED_COMPARED", "variants", variants);
    }

    private static void setProbeField(Object value, String name, Object replacement) {
        try {
            var field = value.getClass().getDeclaredField(name);
            field.setAccessible(true);
            field.set(value, replacement);
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException(failure);
        }
    }

    private static Map<String, Object> compareName(
            Minecraft mc, net.minecraft.world.entity.Entity entity) {
        var renderer = mc.getEntityRenderDispatcher().getRenderer(entity);
        var original = (VanillaLivingReplacement.NameAccess) renderer;
        var oldText = EntityProxy.postTextVertexConsumerProvider;
        var oldPosition = entity.position();
        var scoreboard = mc.level.getScoreboard();
        var slot = net.minecraft.world.scores.DisplaySlot.BELOW_NAME;
        var oldObjective = scoreboard.getDisplayObjective(slot);
        var objective =
                scoreboard.addObjective(
                        "stage6_" + entity.getId(),
                        net.minecraft.world.scores.criteria.ObjectiveCriteria.DUMMY,
                        net.minecraft.network.chat.Component.literal("Score"),
                        net.minecraft.world.scores.criteria.ObjectiveCriteria.RenderType.INTEGER,
                        false,
                        null);
        scoreboard.setDisplayObjective(slot, objective);
        scoreboard.getOrCreatePlayerScore(entity, objective).set(42);
        entity.setPos(mc.player.position().add(1, 0, 1));
        long vertices = 0;
        try {
            for (boolean split : new boolean[] {false, true})
                try (var reference = new Provider();
                        var candidate = new Provider()) {
                    candidate.declaredParts(entity.getId(), "vanilla-name-probe");
                    candidate.sourceOwner = entity;
                    var pose = new PoseStack();
                    var content = net.minecraft.network.chat.Component.literal("deadmau5");
                    EntityProxy.postTextVertexConsumerProvider = split ? reference : null;
                    original.radiance$name(entity, content, pose, reference, 15728640, .37F);
                    EntityProxy.postTextVertexConsumerProvider = split ? candidate : null;
                    com.radiance.adapter.vanilla.VanillaNameRequests.collect(
                            renderer, entity, content, pose, candidate, 15728640, .37F);
                    var a = meshes(reference);
                    var b = meshes(candidate);
                    if (a.size() != b.size()) throw new AssertionError("Name layer count");
                    for (int i = 0; i < a.size(); i++) {
                        var x = a.get(i);
                        var y = b.get(i);
                        if (x.layer != y.layer || x.count != y.count || !x.state.equals(y.state))
                            throw new AssertionError("Name/score/background output " + i);
                        var left = java.nio.ByteBuffer.wrap(x.bytes).order(ByteOrder.nativeOrder());
                        var right =
                                java.nio.ByteBuffer.wrap(y.bytes).order(ByteOrder.nativeOrder());
                        for (int quad = 0; quad < x.count / 4; quad++)
                            if (!sameQuad(left, right, quad, quad))
                                throw new AssertionError(
                                        "Name material/pose differs layer="
                                                + x.layer
                                                + " quad="
                                                + quad
                                                + " firstByte="
                                                + Arrays.mismatch(x.bytes, y.bytes));
                        vertices += x.count;
                    }
                }
        } finally {
            EntityProxy.postTextVertexConsumerProvider = oldText;
            entity.setPos(oldPosition);
            scoreboard.removeObjective(objective);
            scoreboard.setDisplayObjective(slot, oldObjective);
        }
        if (vertices == 0) throw new AssertionError("Empty name oracle");
        return Map.of(
                "vertices",
                vertices,
                "difference",
                "glyph/background attributes exact, position 3e-5 and normalized normal 5e-5, including below-name score");
    }

    public static Map<String, Object> compareLifecycleStand(
            Minecraft mc, net.minecraft.world.entity.decoration.ArmorStand observed) {
        // Copy the delivered live state; the comparison varies partial tick and hurt
        // without modifying the entity that the integrated server owns.
        var copy = net.minecraft.world.entity.EntityType.ARMOR_STAND.create(mc.level);
        copy.load(observed.saveWithoutId(new net.minecraft.nbt.CompoundTag()));
        return compareInstance(mc, "lifecycle/" + observed.getId(), copy);
    }

    private static Map<String, Object> compareInstance(
            Minecraft mc, String source, net.minecraft.world.entity.Entity entity) {
        var dispatcher = mc.getEntityRenderDispatcher();
        try (var empty = new Provider()) {
            if (!(entity instanceof LivingEntity
                            && VanillaLivingReplacement.eligible(dispatcher, entity, empty, empty)
                    || com.radiance.adapter.vanilla.VanillaEntityReplacement.eligible(
                            dispatcher, entity, empty, empty)))
                return Map.of(
                        "route",
                        "CAPTURE",
                        "reason",
                        "Actual renderer/model/layers not yet replaced");
        }
        entity.setPos(0, 69, 0);
        entity.tickCount = 48;
        entity.setYRot(37);
        entity.yRotO = -14;
        if (entity instanceof LivingEntity living) {
            living.yBodyRot = 24;
            living.yBodyRotO = 12;
            living.yHeadRot = -17;
            living.yHeadRotO = 19;
        }
        entity.setXRot(9);
        entity.xRotO = -7;
        long vertices = 0;
        long reorderedQuads = 0;
        double maxPosition = 0, maxNormal = 0;
        var previousText = EntityProxy.postTextVertexConsumerProvider;
        EntityProxy.postTextVertexConsumerProvider = null;
        try {
            for (float partial : new float[] {0, .37F, .91F}) {
                if (entity instanceof LivingEntity living) living.hurtTime = partial > .5F ? 8 : 0;
                try (var reference = new Provider();
                        var candidate = new Provider()) {
                    candidate.takeoverStaticState(true);
                    candidate.declaredParts(
                            entity.getId(), dispatcher.getRenderer(entity).getClass().getName());
                    candidate.sourceOwner = entity;
                    var pose = new PoseStack();
                    pose.translate(.25, -.75, 1.5);
                    pose.mulPose(new org.joml.Quaternionf().rotationXYZ(.13F, -.21F, .31F));
                    float yaw =
                            net.minecraft.util.Mth.lerp(partial, entity.yRotO, entity.getYRot());
                    float ex = 0, ey = 0, ez = 0;
                    Object oldModel = null;
                    int oldPuff = 0;
                    if (dispatcher.getRenderer(entity)
                            instanceof
                            com.radiance.adapter.vanilla.VanillaLivingReplacement.ModelSelection) {
                        oldModel =
                                ((net.minecraft.client.renderer.entity.LivingEntityRenderer<?, ?>)
                                                dispatcher.getRenderer(entity))
                                        .getModel();
                        if (entity instanceof net.minecraft.world.entity.animal.Pufferfish)
                            try {
                                oldPuff =
                                        (int)
                                                RigidModelProbe.field(
                                                        dispatcher.getRenderer(entity),
                                                        "puffStateO");
                            } catch (ReflectiveOperationException failure) {
                                throw new IllegalStateException(failure);
                            }
                    }
                    if (entity instanceof net.minecraft.client.player.AbstractClientPlayer player) {
                        ex = player.elytraRotX;
                        ey = player.elytraRotY;
                        ez = player.elytraRotZ;
                    }
                    referenceInputs = new ArrayList<>();
                    try {
                        dispatcher.render(entity, 0, 0, 0, yaw, partial, pose, reference, 15728640);
                    } finally {
                        candidateInputs = new ArrayList<>();
                    }
                    var originalParts = referenceInputs;
                    referenceInputs = null;
                    if (oldModel != null) {
                        ((VanillaLivingReplacement.StateAccess) dispatcher.getRenderer(entity))
                                .radiance$selectModel(
                                        (net.minecraft.client.model.EntityModel<?>) oldModel);
                        if (entity instanceof net.minecraft.world.entity.animal.Pufferfish)
                            try {
                                var field =
                                        dispatcher
                                                .getRenderer(entity)
                                                .getClass()
                                                .getDeclaredField("puffStateO");
                                field.setAccessible(true);
                                field.setInt(dispatcher.getRenderer(entity), oldPuff);
                            } catch (ReflectiveOperationException failure) {
                                throw new IllegalStateException(failure);
                            }
                    }
                    if (entity instanceof net.minecraft.client.player.AbstractClientPlayer player) {
                        player.elytraRotX = ex;
                        player.elytraRotY = ey;
                        player.elytraRotZ = ez;
                    }
                    try {
                        if (entity instanceof LivingEntity living
                                && VanillaLivingReplacement.eligible(
                                        dispatcher, entity, candidate, candidate))
                            VanillaLivingReplacement.render(
                                    dispatcher, living, partial, pose, candidate, 15728640);
                        else
                            com.radiance.adapter.vanilla.VanillaEntityReplacement.render(
                                    dispatcher,
                                    entity,
                                    partial,
                                    pose,
                                    candidate,
                                    candidate,
                                    15728640);
                        com.radiance.adapter.vanilla.VanillaEntityAttachments.collect(
                                dispatcher, entity, partial, pose, candidate);
                    } finally {
                        referenceInputs = null;
                    }
                    var replacementParts = candidateInputs;
                    candidateInputs = null;
                    if (originalParts.size() != replacementParts.size())
                        throw new AssertionError(source + " part count");
                    for (int i = 0; i < originalParts.size(); i++) {
                        var x = originalParts.get(i);
                        var y = replacementParts.get(i);
                        if (x.part != y.part
                                || !sameLayer(x.layer, y.layer)
                                || x.light != y.light
                                || x.overlay != y.overlay
                                || x.color != y.color
                                || !x.pose.pose().equals(y.pose.pose(), 3e-5F)
                                || !x.pose.normal().equals(y.pose.normal(), 5e-5F))
                            throw new AssertionError(source + " ordered part state " + i);
                    }
                    var a = meshes(reference);
                    var b = meshes(candidate);
                    {
                        if (a.size() != b.size())
                            throw new AssertionError(
                                    source + " draw count " + a.size() + "/" + b.size());
                        for (int i = 0; i < a.size(); ++i) {
                            var x = a.get(i);
                            var y = b.get(i);
                            if (!sameLayer(x.layer, y.layer) || x.count != y.count)
                                throw new AssertionError(source + " layer/order/vertex count " + i);
                            if (!x.state.equals(y.state))
                                throw new AssertionError(
                                        source + " source draw state " + x.state + "/" + y.state);
                            if (x.stride != 128 || y.stride != 128) {
                                if (x.stride != y.stride || !Arrays.equals(x.bytes, y.bytes))
                                    throw new AssertionError(source + " line request");
                                vertices += x.count;
                                continue;
                            }
                            var xbuf =
                                    java.nio.ByteBuffer.wrap(x.bytes)
                                            .order(ByteOrder.nativeOrder());
                            var ybuf =
                                    java.nio.ByteBuffer.wrap(y.bytes)
                                            .order(ByteOrder.nativeOrder());
                            if (xbuf.remaining() != ybuf.remaining())
                                throw new AssertionError("layout");
                            int count = x.count;
                            if (xbuf.remaining() != count * 128)
                                throw new AssertionError("Expected full PBR source layout");
                            if (count % 4 != 0) throw new AssertionError("Incomplete source quad");
                            boolean[] matched = new boolean[count / 4];
                            for (int quad = 0; quad < count / 4; quad++) {
                                int match = -1;
                                for (int j = 0; j < count / 4; j++)
                                    if (!matched[j] && sameQuad(xbuf, ybuf, quad, j)) {
                                        match = j;
                                        break;
                                    }
                                if (match < 0)
                                    throw new AssertionError(
                                            source + " unmatched source quad " + quad);
                                if (match != quad) reorderedQuads++;
                                matched[match] = true;
                                for (int corner = 0; corner < 4; ++corner) {
                                    int at = (quad * 4 + corner) * 128,
                                            bt = (match * 4 + corner) * 128;
                                    double p = distance(xbuf, ybuf, at, bt);
                                    double n = distance(xbuf, ybuf, at + 16, bt + 16);
                                    maxPosition = Math.max(maxPosition, p);
                                    maxNormal = Math.max(maxNormal, n);
                                    if (p > 3e-5 || n > 5e-5)
                                        throw new AssertionError(source + " pose " + p + "/" + n);
                                    for (int j = 12; j < 128; ++j)
                                        if ((j < 16 || j >= 28)
                                                && xbuf.get(at + j) != ybuf.get(bt + j))
                                            throw new AssertionError(
                                                    source + " material byte " + j);
                                }
                            }
                            vertices += count;
                        }
                    }
                }
            }
        } finally {
            referenceInputs = candidateInputs = null;
            EntityProxy.postTextVertexConsumerProvider = previousText;
        }
        return Map.of(
                "route",
                "RENDERER_REPLACED_COMPARED",
                "vertices",
                vertices,
                "maxPositionError",
                maxPosition,
                "maxNormalError",
                maxNormal,
                "reorderedOutputQuads",
                reorderedQuads,
                "partialTicks",
                List.of(0, .37, .91),
                "hurtColor",
                true);
    }

    private static boolean sameLayer(
            net.minecraft.client.renderer.RenderType a,
            net.minecraft.client.renderer.RenderType b) {
        return a == b
                || a != null
                        && b != null
                        && a.mode() == b.mode()
                        && a.format() == b.format()
                        && a.toString().equals(b.toString());
    }

    public static List<Map<String, Object>> compareItems(Minecraft mc) {
        var results = new ArrayList<Map<String, Object>>();
        var renderer = mc.getItemRenderer();
        for (var item :
                List.of(
                        net.minecraft.world.item.Items.STONE,
                        net.minecraft.world.item.Items.GLASS,
                        net.minecraft.world.item.Items.LEATHER_CHESTPLATE,
                        net.minecraft.world.item.Items.DIAMOND_SWORD,
                        net.minecraft.world.item.Items.COMPASS,
                        net.minecraft.world.item.Items.BOW,
                        net.minecraft.world.item.Items.RED_STAINED_GLASS,
                        net.minecraft.world.item.Items.SHIELD,
                        net.minecraft.world.item.Items.TRIDENT,
                        net.minecraft.world.item.Items.SPYGLASS,
                        net.minecraft.world.item.Items.CHEST,
                        net.minecraft.world.item.Items.TRAPPED_CHEST,
                        net.minecraft.world.item.Items.ENDER_CHEST,
                        net.minecraft.world.item.Items.WHITE_BANNER,
                        net.minecraft.world.item.Items.RED_BED,
                        net.minecraft.world.item.Items.SHULKER_BOX,
                        net.minecraft.world.item.Items.CONDUIT,
                        net.minecraft.world.item.Items.DECORATED_POT,
                        net.minecraft.world.item.Items.DRAGON_HEAD)) {
            var stack = new net.minecraft.world.item.ItemStack(item);
            if (item == net.minecraft.world.item.Items.DIAMOND_SWORD
                    || item == net.minecraft.world.item.Items.COMPASS
                    || item == net.minecraft.world.item.Items.SHIELD
                    || item == net.minecraft.world.item.Items.TRIDENT)
                stack.enchant(
                        mc.level
                                .registryAccess()
                                .registryOrThrow(
                                        net.minecraft.core.registries.Registries.ENCHANTMENT)
                                .getHolderOrThrow(
                                        net.minecraft.world.item.enchantment.Enchantments
                                                .UNBREAKING),
                        1);
            for (var context :
                    List.of(
                            net.minecraft.world.item.ItemDisplayContext.GROUND,
                            net.minecraft.world.item.ItemDisplayContext.FIXED,
                            net.minecraft.world.item.ItemDisplayContext.THIRD_PERSON_LEFT_HAND)) {
                for (float emission : new float[] {0, .25F, 0, 1, 0}) {
                    try (var scope =
                                    com.radiance.client.vertex.PBRMaterialContext
                                            .pushAlbedoEmission(emission);
                            var reference = new Provider();
                            var candidate = new Provider()) {
                        candidate.declaredParts(3333333, "world-item-comparison");
                        var a = renderer.getModel(stack, mc.level, mc.player, 42);
                        var b = renderer.getModel(stack, mc.level, mc.player, 42);
                        if (!com.radiance.adapter.vanilla.VanillaItemReplacement.eligible(
                                stack, context, candidate, b))
                            throw new AssertionError("Expected declared vanilla item " + item);
                        var pose = new PoseStack();
                        pose.translate(.25, -.75, 1.5);
                        pose.mulPose(new org.joml.Quaternionf().rotationXYZ(.13F, -.21F, .31F));
                        renderer.render(
                                stack,
                                context,
                                true,
                                pose,
                                reference,
                                15728640,
                                net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY,
                                a);
                        com.radiance.adapter.vanilla.VanillaItemReplacement.render(
                                stack,
                                context,
                                true,
                                pose,
                                candidate,
                                15728640,
                                net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY,
                                b);
                        var x = meshes(reference);
                        var y = meshes(candidate);
                        if (x.size() != y.size())
                            throw new AssertionError("Item layer count " + item);
                        long count = 0;
                        for (int i = 0; i < x.size(); i++) {
                            var u = x.get(i);
                            var v = y.get(i);
                            if (!sameLayer(u.layer, v.layer)
                                    || u.count != v.count
                                    || !u.state.equals(v.state))
                                throw new AssertionError(
                                        "Item layer state " + item + " " + context);
                            var left =
                                    java.nio.ByteBuffer.wrap(u.bytes)
                                            .order(ByteOrder.nativeOrder());
                            var right =
                                    java.nio.ByteBuffer.wrap(v.bytes)
                                            .order(ByteOrder.nativeOrder());
                            for (int quad = 0; quad < u.count / 4; quad++)
                                if (!sameQuad(left, right, quad, quad))
                                    throw new AssertionError(
                                            "Item geometry/material "
                                                    + item
                                                    + " "
                                                    + context
                                                    + " quad="
                                                    + quad);
                            count += u.count;
                        }
                        results.add(
                                Map.of(
                                        "item",
                                        BuiltInRegistries.ITEM.getKey(item).toString(),
                                        "context",
                                        context.toString(),
                                        "inheritedEmission",
                                        emission,
                                        "vertices",
                                        count,
                                        "route",
                                        "RENDERER_REPLACED_COMPARED"));
                    }
                }
            }
        }
        return results;
    }

    /** Uses the same action evaluator, but independently executes original and requested leaves. */
    public static List<Map<String, Object>> compareFirstPerson(Minecraft mc)
            throws ReflectiveOperationException {
        var player = mc.player;
        var renderer = mc.gameRenderer.itemInHandRenderer;
        var hands =
                (com.radiance.mixin_related.extensions.vulkan_render_integration
                                .IHeldItemRendererExt)
                        renderer;
        var oldMain = player.getMainHandItem();
        var oldOff = player.getOffhandItem();
        if (player.isSpectator())
            throw new AssertionError(
                    "First-person fixture must have received creative player info");
        boolean oldInvisible = player.isInvisible();
        var saved = new LinkedHashMap<java.lang.reflect.Field, Object>();
        for (var name :
                List.of(
                        "mainHandItem",
                        "offHandItem",
                        "mainHandHeight",
                        "oMainHandHeight",
                        "offHandHeight",
                        "oOffHandHeight")) {
            var f = probeField(renderer, name);
            saved.put(f, f.get(renderer));
        }
        var result = new ArrayList<Map<String, Object>>();
        try {
            player.setInvisible(false);
            var map =
                    new net.minecraft.world.item.ItemStack(
                            net.minecraft.world.item.Items.FILLED_MAP);
            map.set(
                    net.minecraft.core.component.DataComponents.MAP_ID,
                    new net.minecraft.world.level.saveddata.maps.MapId(130));
            var items = net.minecraft.world.item.Items.STONE;
            var cases =
                    List.of(
                            new net.minecraft.world.item.ItemStack[] {
                                net.minecraft.world.item.ItemStack.EMPTY,
                                net.minecraft.world.item.ItemStack.EMPTY
                            },
                            new net.minecraft.world.item.ItemStack[] {
                                new net.minecraft.world.item.ItemStack(items),
                                new net.minecraft.world.item.ItemStack(
                                        net.minecraft.world.item.Items.SHIELD)
                            },
                            new net.minecraft.world.item.ItemStack[] {
                                map, net.minecraft.world.item.ItemStack.EMPTY
                            },
                            new net.minecraft.world.item.ItemStack[] {
                                map, new net.minecraft.world.item.ItemStack(items)
                            },
                            new net.minecraft.world.item.ItemStack[] {
                                new net.minecraft.world.item.ItemStack(items), map
                            });
            for (var item :
                    List.of(
                            net.minecraft.world.item.Items.BOW,
                            net.minecraft.world.item.Items.CROSSBOW,
                            net.minecraft.world.item.Items.APPLE,
                            net.minecraft.world.item.Items.POTION,
                            net.minecraft.world.item.Items.SPYGLASS,
                            net.minecraft.world.item.Items.TRIDENT,
                            net.minecraft.world.item.Items.SHIELD))
                for (var hand : net.minecraft.world.InteractionHand.values()) {
                    var pair =
                            new net.minecraft.world.item.ItemStack[] {
                                new net.minecraft.world.item.ItemStack(items),
                                new net.minecraft.world.item.ItemStack(items)
                            };
                    pair[hand.ordinal()] = new net.minecraft.world.item.ItemStack(item);
                    result.add(firstPersonCase(mc, renderer, hands, pair, hand));
                }
            for (var pair : cases) result.add(firstPersonCase(mc, renderer, hands, pair, null));
        } finally {
            player.stopUsingItem();
            player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, oldMain);
            player.setItemInHand(net.minecraft.world.InteractionHand.OFF_HAND, oldOff);
            player.setInvisible(oldInvisible);
            for (var e : saved.entrySet()) e.getKey().set(renderer, e.getValue());
        }
        return result;
    }

    private static Map<String, Object> firstPersonCase(
            Minecraft mc,
            Object renderer,
            com.radiance.mixin_related.extensions.vulkan_render_integration.IHeldItemRendererExt
                    hands,
            net.minecraft.world.item.ItemStack[] pair,
            net.minecraft.world.InteractionHand use)
            throws ReflectiveOperationException {
        var player = mc.player;
        player.stopUsingItem();
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, pair[0]);
        player.setItemInHand(net.minecraft.world.InteractionHand.OFF_HAND, pair[1]);
        probeField(renderer, "mainHandItem").set(renderer, pair[0]);
        probeField(renderer, "offHandItem").set(renderer, pair[1]);
        for (var name :
                List.of("mainHandHeight", "oMainHandHeight", "offHandHeight", "oOffHandHeight"))
            probeField(renderer, name).setFloat(renderer, .75F);
        if (use != null) {
            player.startUsingItem(use);
            probeField(player, "useItemRemaining")
                    .setInt(player, Math.max(1, player.getUseItemRemainingTicks() - 12));
        }
        long vertices = 0;
        for (float partial : new float[] {0, .37F, .91F})
            try (var original = new Provider();
                    var candidate = new Provider()) {
                candidate.firstPerson(player);
                hands.radiance$renderItem(partial, new PoseStack(), original, player, 15728640);
                try (var evaluation =
                        com.radiance.adapter.vanilla.VanillaModelPose.source(player)) {
                    hands.radiance$renderItem(
                            partial, new PoseStack(), candidate, player, 15728640);
                }
                vertices +=
                        compareMeshes(
                                "first-person " + pair[0] + " / " + pair[1] + " use=" + use,
                                original,
                                candidate);
            }
        if (use == null && vertices == 0)
            throw new AssertionError("First-person fixture failed to expose hands/items");
        return Map.of(
                "main",
                pair[0].toString(),
                "off",
                pair[1].toString(),
                "using",
                String.valueOf(use),
                "vertices",
                vertices,
                "partials",
                List.of(0, .37, .91));
    }

    public static List<Map<String, Object>> compareNestedParticles(Minecraft mc)
            throws ReflectiveOperationException {
        var out = new ArrayList<Map<String, Object>>();
        var camera = mc.gameRenderer.getMainCamera();
        var apparition =
                new net.minecraft.client.particle.MobAppearanceParticle.Provider()
                        .createParticle(
                                net.minecraft.core.particles.ParticleTypes.ELDER_GUARDIAN,
                                mc.level,
                                0,
                                0,
                                0,
                                0,
                                0,
                                0);
        var item =
                new net.minecraft.world.entity.item.ItemEntity(
                        mc.level,
                        playerX(mc) + 2,
                        mc.player.getY(),
                        mc.player.getZ(),
                        new net.minecraft.world.item.ItemStack(
                                net.minecraft.world.item.Items.DIAMOND));
        var pickup =
                new net.minecraft.client.particle.ItemPickupParticle(
                        mc.getEntityRenderDispatcher(),
                        mc.renderBuffers(),
                        mc.level,
                        item,
                        mc.player);
        var buffer = probeField(mc.renderBuffers(), "bufferSource");
        var previous = buffer.get(mc.renderBuffers());
        try {
            for (var particle : List.of(apparition, pickup))
                for (int tick = 0; tick < 3; tick++) {
                    for (float partial : new float[] {0, .37F, .91F})
                        try (var original = new Provider();
                                var candidate = new Provider()) {
                            buffer.set(mc.renderBuffers(), original);
                            try (var emission =
                                    com.radiance.client.vertex.PBRMaterialContext
                                            .pushAlbedoEmission(particle == apparition ? 1 : 0)) {
                                particle.render(null, camera, partial);
                                if (!((com.radiance.adapter.vanilla.VanillaNestedParticleRequests
                                                        .Source)
                                                particle)
                                        .radiance$nested(candidate, camera, partial))
                                    throw new AssertionError("Nested source fell back");
                            }
                            long vertices =
                                    compareMeshes(
                                            "nested " + particle.getClass() + " tick=" + tick,
                                            original,
                                            candidate);
                            out.add(
                                    Map.of(
                                            "source",
                                            particle.getClass().getName(),
                                            "tick",
                                            tick,
                                            "partial",
                                            partial,
                                            "vertices",
                                            vertices));
                        }
                    particle.tick();
                }
        } finally {
            buffer.set(mc.renderBuffers(), previous);
            apparition.remove();
            pickup.remove();
        }
        return out;
    }

    private static double playerX(Minecraft mc) {
        return mc.player.getX();
    }

    public static Map<String, Object> compareWorldLighting(Minecraft mc)
            throws ReflectiveOperationException {
        try (var worldScope =
                com.radiance.client.render.RenderCaptureContract.enter(
                        com.radiance.render.contract.DrawTargetContract.ScopeKind.WORLD_STAGE,
                        "world-material-probe")) {
            var setting =
                    net.neoforged.neoforge.common.NeoForgeConfig.CLIENT
                            .experimentalForgeLightPipelineEnabled;
            boolean previous = setting.get();
            try {
                var modes = new LinkedHashMap<String, Object>();
                for (boolean experimental : new boolean[] {false, true}) {
                    setting.set(experimental);
                    modes.put(Boolean.toString(experimental), compareWorldLightingScoped(mc));
                }
                return modes;
            } finally {
                setting.set(previous);
            }
        }
    }

    private static Map<String, Object> compareWorldLightingScoped(Minecraft mc)
            throws ReflectiveOperationException {
        int[] illumination = {0}, tint = {0x429a31}, queries = {0};
        var querySites = new ArrayList<String>();
        var world =
                (net.minecraft.world.level.BlockAndTintGetter)
                        java.lang.reflect.Proxy.newProxyInstance(
                                RendererReplacementProbe.class.getClassLoader(),
                                new Class<?>[] {net.minecraft.world.level.BlockAndTintGetter.class},
                                (proxy, method, args) -> {
                                    if (querySites.size() < 3
                                            && (method.getName().equals("getShade")
                                                    || method.getName().equals("getBrightness")
                                                    || method.getName().equals("getRawBrightness")))
                                        querySites.add(
                                                method.getName()
                                                        + Arrays.toString(
                                                                Thread.currentThread()
                                                                        .getStackTrace()));
                                    if (method.getName().equals("getShade")) {
                                        queries[0]++;
                                        return illumination[0] == 0 ? .2F : 1F;
                                    }
                                    if (method.getName().equals("getBrightness")
                                            || method.getName().equals("getRawBrightness")) {
                                        queries[0]++;
                                        return illumination[0] == 0 ? 0 : 15;
                                    }
                                    if (method.getName().equals("getBlockTint")) return tint[0];
                                    return method.invoke(mc.level, args);
                                });
        var state = net.minecraft.world.level.block.Blocks.GRASS_BLOCK.defaultBlockState();
        var pos = new net.minecraft.core.BlockPos(36, 70, 4);
        var samples = new ArrayList<List<Draw>>();
        for (int sample = 0; sample < 3; sample++)
            try (var provider = new Provider()) {
                illumination[0] = sample == 0 ? 0 : 1;
                if (sample == 2) tint[0] = 0xac2561;
                mc.getBlockRenderer()
                        .renderBatched(
                                state,
                                pos,
                                world,
                                new PoseStack(),
                                provider.getBuffer(
                                        net.minecraft.client.renderer.RenderType.solid()),
                                true,
                                net.minecraft.util.RandomSource.create(42));
                samples.add(meshes(provider));
            }
        if (queries[0] != 0)
            throw new AssertionError(
                    "World producer evaluated discarded raster illumination: "
                            + queries[0]
                            + " preview="
                            + com.radiance.client.render.RasterPreviewScope.active()
                            + " sites="
                            + querySites);
        boolean tintChanged = false;
        long vertices = 0;
        for (int i = 0; i < samples.get(0).size(); i++) {
            var a = samples.get(0).get(i);
            var b = samples.get(1).get(i);
            var c = samples.get(2).get(i);
            if (!Arrays.equals(a.bytes, b.bytes))
                throw new AssertionError("Ambient changed PT material/geometry");
            tintChanged |= !Arrays.equals(a.bytes, c.bytes);
            var data = java.nio.ByteBuffer.wrap(a.bytes).order(ByteOrder.nativeOrder());
            for (int at = 0; at < a.bytes.length; at += 128)
                if (data.getInt(at + 96) != 0
                        || data.getInt(at + 100) != 0
                        || data.getFloat(at + 108) != 0)
                    throw new AssertionError("Ordinary material acquired raster light or emission");
            vertices += a.count;
        }
        if (vertices == 0 || !tintChanged)
            throw new AssertionError("World material fixture did not expose tint");
        List<Draw> preview;
        try (var guiScope =
                        com.radiance.client.render.RenderCaptureContract.enter(
                                com.radiance.render.contract.DrawTargetContract.ScopeKind.GUI,
                                "preview-material-probe");
                var scope = com.radiance.client.render.RasterPreviewScope.enter();
                var provider = new Provider()) {
            illumination[0] = 0;
            tint[0] = 0x429a31;
            mc.getBlockRenderer()
                    .renderBatched(
                            state,
                            pos,
                            world,
                            new PoseStack(),
                            provider.getBuffer(net.minecraft.client.renderer.RenderType.solid()),
                            true,
                            net.minecraft.util.RandomSource.create(42));
            preview = meshes(provider);
        }
        if (queries[0] == 0
                || Arrays.equals(preview.getFirst().bytes, samples.getFirst().getFirst().bytes))
            throw new AssertionError("Preview lost its original raster lighting");
        var cow = net.minecraft.world.entity.EntityType.COW.create(mc.level);
        var identities = new ArrayList<List<Long>>();
        for (int light : new int[] {0, 15728880})
            try (var provider = new Provider()) {
                provider.sourceOwner = cow;
                provider.declaredParts(cow.getId(), "light-dependency-probe");
                VanillaLivingReplacement.render(
                        mc.getEntityRenderDispatcher(),
                        cow,
                        .37F,
                        new PoseStack(),
                        provider,
                        light);
                var ids = new ArrayList<Long>();
                for (var draw : provider.takeRigidModels()) {
                    var geometry = RigidModelProbe.field(draw, "entry");
                    var id = geometry.getClass().getMethod("id");
                    id.setAccessible(true);
                    ids.add((Long) id.invoke(geometry));
                }
                identities.add(ids);
            }
        cow.discard();
        if (identities.getFirst().isEmpty() || !identities.getFirst().equals(identities.getLast()))
            throw new AssertionError("Light-only change rebuilt local bindings");
        var particleGlow = new LinkedHashMap<String, Integer>();
        for (var particleType :
                List.of(
                        net.minecraft.core.particles.ParticleTypes.SMOKE,
                        net.minecraft.core.particles.ParticleTypes.LAVA,
                        net.minecraft.core.particles.ParticleTypes.END_ROD)) {
            var particle = mc.particleEngine.createParticle(particleType, 36, 70, 4, 0, 0, 0);
            if (particle == null) throw new AssertionError("Particle factory returned no source");
            try {
                int emission =
                        com.radiance.adapter.vanilla.VanillaParticleEmission.emissionStep(
                                particle,
                                () ->
                                        ((com.radiance.mixin_related.extensions
                                                                .vulkan_render_integration
                                                                .IParticleExt)
                                                        particle)
                                                .radiance$getLightColor(.37F));
                if ((particleType == net.minecraft.core.particles.ParticleTypes.SMOKE)
                        != (emission == 0))
                    throw new AssertionError(
                            "Particle ambient/glow intent was lost: "
                                    + particle.getClass()
                                    + "="
                                    + emission);
                particleGlow.put(particle.getClass().getSimpleName(), emission);
            } finally {
                particle.remove();
            }
        }
        return Map.of(
                "worldLightQueries",
                0,
                "previewLightQueries",
                queries[0],
                "tintChangesMaterial",
                true,
                "ordinaryEmission",
                0,
                "vertices",
                vertices,
                "lightOnlyResourceIdentityStable",
                true,
                "particleGlow",
                particleGlow,
                "signGlow",
                compareSignGlow(mc));
    }

    private static List<Map<String, Object>> compareSignGlow(Minecraft mc) {
        var sign =
                new net.minecraft.world.level.block.entity.SignBlockEntity(
                        new net.minecraft.core.BlockPos(36, 70, 4),
                        net.minecraft.world.level.block.Blocks.OAK_SIGN.defaultBlockState());
        sign.setLevel(mc.level);
        var text =
                new net.minecraft.world.level.block.entity.SignText()
                        .setMessage(0, net.minecraft.network.chat.Component.literal("Radiance"))
                        .setColor(net.minecraft.world.item.DyeColor.BLUE);
        var result = new ArrayList<Map<String, Object>>();
        for (boolean glow : new boolean[] {false, true, false}) {
            sign.setText(text.setHasGlowingText(glow), true);
            try (var provider = new Provider()) {
                provider.enablePartModels(
                        sign, mc.getBlockEntityRenderDispatcher().getRenderer(sign), true);
                com.radiance.adapter.vanilla.VanillaSignRequests.text(
                        sign,
                        mc.font,
                        new PoseStack(),
                        provider,
                        15728880,
                        1,
                        net.minecraft.world.phys.Vec3.ZERO);
                int vertices = 0;
                for (var draw : meshes(provider)) {
                    if (draw.stride != 128) throw new AssertionError("Unexpected text encoding");
                    var bytes = java.nio.ByteBuffer.wrap(draw.bytes).order(ByteOrder.nativeOrder());
                    for (int offset = 0; offset < draw.bytes.length; offset += 128) {
                        if (bytes.getFloat(offset + 108) != (glow ? 1F : 0F))
                            throw new AssertionError(
                                    "Explicit sign emission lost or leaked: " + glow);
                        vertices++;
                    }
                }
                if (vertices == 0) throw new AssertionError("No actual sign glyphs");
                result.add(Map.of("glowing", glow, "vertices", vertices, "emission", glow ? 1 : 0));
            }
        }
        return result;
    }

    public static List<Map<String, Object>> compareWorldBlockChanges(Minecraft mc)
            throws ReflectiveOperationException {
        var position = new net.minecraft.core.BlockPos(36, 70, 4);
        var saved =
                new LinkedHashMap<
                        net.minecraft.core.BlockPos,
                        net.minecraft.world.level.block.state.BlockState>();
        saved.put(position, mc.level.getBlockState(position));
        for (var direction : net.minecraft.core.Direction.values())
            saved.put(
                    position.relative(direction),
                    mc.level.getBlockState(position.relative(direction)));
        var progress = new net.minecraft.server.level.BlockDestructionProgress(130, position);
        var results = new ArrayList<Map<String, Object>>();
        var blocks = mc.getBlockRenderer();
        try {
            for (var state :
                    List.of(
                            net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(),
                            net.minecraft.world.level.block.Blocks.GRASS_BLOCK
                                    .defaultBlockState())) {
                mc.level.setBlock(position, state, 0);
                for (boolean occupied : new boolean[] {false, false, true, true, false}) {
                    for (var direction : net.minecraft.core.Direction.values())
                        mc.level.setBlock(
                                position.relative(direction),
                                occupied
                                        ? net.minecraft.world.level.block.Blocks.STONE
                                                .defaultBlockState()
                                        : net.minecraft.world.level.block.Blocks.AIR
                                                .defaultBlockState(),
                                0);
                    for (int stage : new int[] {0, 4, 9}) {
                        progress.setProgress(stage);
                        for (int warm = 0; warm < 2; warm++)
                            try (var original = new Provider();
                                    var candidate = new Provider()) {
                                var pose = new PoseStack();
                                pose.mulPose(
                                        new org.joml.Quaternionf().rotationXYZ(.13F, .25F, -.17F));
                                blocks.renderBreakingTexture(
                                        state,
                                        position,
                                        mc.level,
                                        pose,
                                        new SheetedDecalTextureGenerator(
                                                original.getBuffer(
                                                        net.minecraft.client.resources.model
                                                                .ModelBakery.DESTROY_TYPES
                                                                .get(stage)),
                                                pose.last(),
                                                1),
                                        mc.level.getModelData(position));
                                if (!com.radiance.adapter.vanilla.VanillaWorldBlockRequests
                                        .destruction(
                                                progress, position, state, pose, candidate,
                                                mc.level, blocks))
                                    throw new AssertionError(
                                            "Vanilla destruction fell back: "
                                                    + state
                                                    + " model="
                                                    + blocks.getBlockModel(state).getClass()
                                                    + " owner="
                                                    + (progress
                                                            instanceof
                                                            com.radiance.client.vertex
                                                                    .SourceBindings.Owner));
                                // Only decal UV has the inverse-pose float round trip (observed
                                // 1.2e-7).
                                results.add(
                                        Map.of(
                                                "state",
                                                state.toString(),
                                                "occupiedNeighbours",
                                                occupied,
                                                "stage",
                                                stage,
                                                "warm",
                                                warm,
                                                "projectedUvTolerance",
                                                1e-6,
                                                "vertices",
                                                compareMeshes(
                                                        "world destruction",
                                                        original,
                                                        candidate,
                                                        1e-6F)));
                            }
                    }
                }
            }
        } finally {
            for (var entry : saved.entrySet())
                mc.level.setBlock(entry.getKey(), entry.getValue(), 0);
        }
        return results;
    }

    private static java.lang.reflect.Field probeField(Object owner, String name)
            throws NoSuchFieldException {
        for (Class<?> type = owner.getClass(); type != null; type = type.getSuperclass())
            try {
                var f = type.getDeclaredField(name);
                f.setAccessible(true);
                return f;
            } catch (NoSuchFieldException next) {
            }
        throw new NoSuchFieldException(name);
    }

    private static long compareMeshes(String scope, Provider original, Provider candidate) {
        return compareMeshes(scope, original, candidate, 0);
    }

    private static long compareMeshes(
            String scope, Provider original, Provider candidate, float projectedUvTolerance) {
        var a = meshes(original);
        var b = meshes(candidate);
        long vertices = 0;
        if (a.size() != b.size())
            throw new AssertionError(scope + " layer count " + a.size() + "/" + b.size());
        for (int i = 0; i < a.size(); i++) {
            var x = a.get(i);
            var y = b.get(i);
            if (!sameLayer(x.layer, y.layer)
                    || x.count != y.count
                    || !x.state.equals(y.state)
                    || x.stride != y.stride)
                throw new AssertionError(
                        scope
                                + " layer/state "
                                + i
                                + " "
                                + x.layer
                                + "/"
                                + y.layer
                                + " "
                                + x.count
                                + "/"
                                + y.count
                                + " "
                                + x.state
                                + "/"
                                + y.state);
            if (x.stride == 128) {
                var u = java.nio.ByteBuffer.wrap(x.bytes).order(ByteOrder.nativeOrder());
                var v = java.nio.ByteBuffer.wrap(y.bytes).order(ByteOrder.nativeOrder());
                for (int q = 0; q < x.count / 4; q++)
                    if (!sameQuad(u, v, q, q, projectedUvTolerance))
                        throw new AssertionError(
                                scope
                                        + " quad "
                                        + q
                                        + " byte="
                                        + Arrays.mismatch(x.bytes, y.bytes)
                                        + " normal="
                                        + u.getFloat(q * 512 + 16)
                                        + ","
                                        + u.getFloat(q * 512 + 20)
                                        + ","
                                        + u.getFloat(q * 512 + 24)
                                        + " / "
                                        + v.getFloat(q * 512 + 16)
                                        + ","
                                        + v.getFloat(q * 512 + 20)
                                        + ","
                                        + v.getFloat(q * 512 + 24)
                                        + " uv="
                                        + u.getFloat(q * 512 + 56)
                                        + ","
                                        + u.getFloat(q * 512 + 60)
                                        + " / "
                                        + v.getFloat(q * 512 + 56)
                                        + ","
                                        + v.getFloat(q * 512 + 60));
            } else if (!Arrays.equals(x.bytes, y.bytes))
                throw new AssertionError(scope + " raw geometry");
            vertices += x.count;
        }
        return vertices;
    }

    private static double distance(java.nio.ByteBuffer a, java.nio.ByteBuffer b, int at, int bt) {
        double x = a.getFloat(at) - b.getFloat(bt),
                y = a.getFloat(at + 4) - b.getFloat(bt + 4),
                z = a.getFloat(at + 8) - b.getFloat(bt + 8);
        return Math.sqrt(x * x + y * y + z * z);
    }

    private static boolean sameQuad(java.nio.ByteBuffer a, java.nio.ByteBuffer b, int x, int y) {
        return sameQuad(a, b, x, y, 0);
    }

    private static boolean sameQuad(
            java.nio.ByteBuffer a,
            java.nio.ByteBuffer b,
            int x,
            int y,
            float projectedUvTolerance) {
        for (int corner = 0; corner < 4; corner++) {
            int at = (x * 4 + corner) * 128, bt = (y * 4 + corner) * 128;
            if (distance(a, b, at, bt) > 3e-5 || distance(a, b, at + 16, bt + 16) > 5e-5)
                return false;
            for (int j = 12; j < 128; j++)
                if ((j < 16 || j >= 28)
                        && !(projectedUvTolerance > 0 && j >= 56 && j < 64)
                        && a.get(at + j) != b.get(bt + j)) return false;
            if (projectedUvTolerance > 0
                    && (!(Math.abs(a.getFloat(at + 56) - b.getFloat(bt + 56))
                                    <= projectedUvTolerance)
                            || !(Math.abs(a.getFloat(at + 60) - b.getFloat(bt + 60))
                                    <= projectedUvTolerance))) return false;
        }
        return true;
    }

    private record Draw(
            net.minecraft.client.renderer.RenderType layer,
            byte[] bytes,
            int count,
            com.radiance.render.contract.DrawStateSnapshot state,
            int stride) {}

    private record GeometryState(
            Object geometry, com.radiance.render.contract.DrawStateSnapshot state) {}

    private static List<GeometryState> geometryStates(
            Object resource, Object draw, Provider provider) throws ReflectiveOperationException {
        if (resource.getClass().getName().endsWith("BoundPartCapture$Resource")) {
            var result = new ArrayList<GeometryState>();
            var bindings =
                    (List<?>)
                            RigidModelProbe.field(
                                    RigidModelProbe.field(resource, "key"), "bindings");
            for (var binding : bindings)
                result.add(
                        new GeometryState(
                                RigidModelProbe.field(binding, "geometry"),
                                (com.radiance.render.contract.DrawStateSnapshot)
                                        RigidModelProbe.field(binding, "state")));
            return result;
        }
        var consumer = (VertexConsumer) RigidModelProbe.field(draw, "captureConsumer");
        return List.of(new GeometryState(resource, provider.drawState(consumer)));
    }

    private static List<Draw> meshes(Provider provider) {
        provider.endBatch();
        var result = new LinkedHashMap<net.minecraft.client.renderer.RenderType, Draw>();
        var order = new IdentityHashMap<net.minecraft.client.renderer.RenderType, Integer>();
        for (var entry : provider.getLayerBatches()) {
            order.putIfAbsent(entry.getKey(), order.size());
            if (entry.getValue()
                    instanceof com.radiance.client.vertex.CapturedLineVertexConsumer line) {
                var mesh = line.build();
                if (mesh != null)
                    try (mesh) {
                        byte[] bytes = new byte[mesh.vertexBuffer().remaining()];
                        mesh.vertexBuffer().duplicate().get(bytes);
                        append(
                                result,
                                entry.getKey(),
                                bytes,
                                mesh.drawState().vertexCount(),
                                provider.drawState(line),
                                com.radiance.client.vertex.CapturedLineVertexConsumer.STRIDE);
                    }
                continue;
            }
            if (!(entry.getValue() instanceof PBRVertexConsumer pbr)) {
                if (!(entry.getValue() instanceof BufferBuilder builder))
                    throw new AssertionError("Unsupported comparison consumer " + entry.getKey());
                var raw = builder.build();
                if (raw != null)
                    try (raw) {
                        byte[] bytes = new byte[raw.vertexBuffer().remaining()];
                        raw.vertexBuffer().duplicate().get(bytes);
                        append(
                                result,
                                entry.getKey(),
                                bytes,
                                raw.drawState().vertexCount(),
                                provider.drawState(entry.getValue()),
                                raw.drawState().format().getVertexSize());
                    }
                continue;
            }
            var mesh = pbr.endNullable();
            if (mesh != null)
                try (mesh) {
                    byte[] bytes = new byte[mesh.vertexBuffer().remaining()];
                    mesh.vertexBuffer().duplicate().get(bytes);
                    append(
                            result,
                            entry.getKey(),
                            bytes,
                            mesh.drawState().vertexCount(),
                            provider.drawState(entry.getValue()));
                }
        }
        try {
            for (var draw : provider.takeRigidModels()) {
                Object resource = RigidModelProbe.field(draw, "entry");
                var transform = (org.joml.Matrix4f) RigidModelProbe.field(draw, "transform");
                for (var binding : geometryStates(resource, draw, provider)) {
                    var geometry = binding.geometry;
                    var mesh = (MeshData) RigidModelProbe.field(geometry, "mesh");
                    var layerMethod = geometry.getClass().getMethod("layer");
                    layerMethod.setAccessible(true);
                    var layer =
                            (net.minecraft.client.renderer.RenderType) layerMethod.invoke(geometry);
                    if (!order.containsKey(layer)) {
                        int sourceOrder =
                                order.entrySet().stream()
                                        .filter(e -> sameLayer(e.getKey(), layer))
                                        .mapToInt(Map.Entry::getValue)
                                        .min()
                                        .orElse(order.size());
                        order.put(layer, sourceOrder);
                    }
                    var state = binding.state;
                    byte[] bytes = new byte[mesh.vertexBuffer().remaining()];
                    mesh.vertexBuffer().duplicate().get(bytes);
                    var buffer = java.nio.ByteBuffer.wrap(bytes).order(ByteOrder.nativeOrder());
                    var normal = new org.joml.Matrix3f(transform).invert().transpose();
                    var surface =
                            (com.radiance.api.render.LocalSurface)
                                    RigidModelProbe.field(draw, "surface");
                    var v = new org.joml.Vector3f();
                    for (int at = 0; at < bytes.length; at += 128) {
                        transform.transformPosition(
                                v.set(
                                        buffer.getFloat(at),
                                        buffer.getFloat(at + 4),
                                        buffer.getFloat(at + 8)));
                        buffer.putFloat(at, v.x).putFloat(at + 4, v.y).putFloat(at + 8, v.z);
                        normal.transform(
                                v.set(
                                        buffer.getFloat(at + 16),
                                        buffer.getFloat(at + 20),
                                        buffer.getFloat(at + 24)));
                        buffer.putFloat(at + 16, v.x).putFloat(at + 20, v.y).putFloat(at + 24, v.z);
                        expandSurface(buffer, at, surface);
                    }
                    append(result, layer, bytes, mesh.drawState().vertexCount(), state);
                }
            }
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException(failure);
        }
        return result.values().stream()
                .sorted(Comparator.comparingInt(draw -> order.get(draw.layer)))
                .toList();
    }

    private static void append(
            Map<net.minecraft.client.renderer.RenderType, Draw> result,
            net.minecraft.client.renderer.RenderType layer,
            byte[] bytes,
            int count,
            com.radiance.render.contract.DrawStateSnapshot state) {
        append(result, layer, bytes, count, state, 128);
    }

    private static void append(
            Map<net.minecraft.client.renderer.RenderType, Draw> result,
            net.minecraft.client.renderer.RenderType layer,
            byte[] bytes,
            int count,
            com.radiance.render.contract.DrawStateSnapshot state,
            int stride) {
        state = com.radiance.adapter.vanilla.VanillaMaterialAdapter.sourceColour(layer, state);
        if (stride == 128) {
            var data = java.nio.ByteBuffer.wrap(bytes).order(ByteOrder.nativeOrder());
            var n = new org.joml.Vector3f();
            for (int at = 0; at < bytes.length; at += stride) {
                if (data.getInt(at + 12) != 0) {
                    n.set(data.getFloat(at + 16), data.getFloat(at + 20), data.getFloat(at + 24));
                    if (n.lengthSquared() != 0) n.normalize();
                    data.putFloat(at + 16, n.x).putFloat(at + 20, n.y).putFloat(at + 24, n.z);
                }
                if (data.getInt(at + 48) != 0) {
                    float u = data.getFloat(at + 56), v = data.getFloat(at + 60);
                    data.putFloat(at + 56, state.uvTransform().u(u, v))
                            .putFloat(at + 60, state.uvTransform().v(u, v));
                }
            }
            state = state.withoutUvTransform();
        }
        var previous = result.get(layer);
        if (previous == null) result.put(layer, new Draw(layer, bytes, count, state, stride));
        else {
            if (!previous.state.equals(state) || previous.stride != stride)
                throw new AssertionError("State changed within one source layer");
            byte[] combined = Arrays.copyOf(previous.bytes, previous.bytes.length + bytes.length);
            System.arraycopy(bytes, 0, combined, previous.bytes.length, bytes.length);
            result.put(layer, new Draw(layer, combined, previous.count + count, state, stride));
        }
    }

    /** Reconstruct the shader's source attributes, separately from global draw colour. */
    private static void expandSurface(
            java.nio.ByteBuffer data, int at, com.radiance.api.render.LocalSurface surface) {
        int flags = surface.flags();
        if ((flags & com.radiance.render.contract.RenderAbi.APPEARANCE_COLOR_MULTIPLY) != 0) {
            data.putFloat(at + 32, data.getFloat(at + 32) * surface.red())
                    .putFloat(at + 36, data.getFloat(at + 36) * surface.green())
                    .putFloat(at + 40, data.getFloat(at + 40) * surface.blue())
                    .putFloat(at + 44, data.getFloat(at + 44) * surface.alpha());
        }
        if ((flags & com.radiance.render.contract.RenderAbi.APPEARANCE_OVERRIDE_LIGHT) != 0
                && data.getInt(at + 92) != 0)
            data.putInt(at + 96, surface.light() & 65535).putInt(at + 100, surface.light() >>> 16);
        if ((flags & com.radiance.render.contract.RenderAbi.APPEARANCE_OVERRIDE_OVERLAY) != 0
                && data.getInt(at + 52) != 0)
            data.putInt(at + 64, surface.overlay() & 65535)
                    .putInt(at + 68, surface.overlay() >>> 16);
        if ((flags & com.radiance.render.contract.RenderAbi.APPEARANCE_UV_AFFINE) != 0
                && data.getInt(at + 48) != 0) {
            float u = data.getFloat(at + 56), v = data.getFloat(at + 60);
            data.putFloat(at + 56, surface.uv().u(u, v)).putFloat(at + 60, surface.uv().v(u, v));
        }
    }

    private static final class Provider extends StorageVertexConsumerProvider
            implements AutoCloseable {
        Provider() {
            super(256);
        }
    }
}
