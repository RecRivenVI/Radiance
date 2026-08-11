package com.radiance.adapter.vanilla;

import static org.junit.jupiter.api.Assertions.*;

import com.mojang.blaze3d.vertex.PoseStack;
import com.radiance.client.vertex.PartModelCapture;
import java.util.*;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;

class VanillaPoseMemoTest {
    private static final class Part implements PartModelCapture.SourceAccess {
        final ModelPart model;
        final List<ModelPart.Cube> cubes;
        final Map<String, ModelPart> children;

        Part() {
            this(
                    new ArrayList<>(
                            List.of(
                                    new ModelPart.Cube(
                                            0,
                                            0,
                                            0,
                                            0,
                                            0,
                                            2,
                                            3,
                                            4,
                                            0,
                                            0,
                                            0,
                                            false,
                                            16,
                                            16,
                                            EnumSet.allOf(Direction.class)))),
                    new LinkedHashMap<>());
        }

        private Part(List<ModelPart.Cube> cubes, Map<String, ModelPart> children) {
            model = new ModelPart(cubes, children);
            this.cubes = cubes;
            this.children = children;
        }

        public void radiance$declarePart() {}

        public boolean radiance$declaredPart() {
            return true;
        }

        public List<ModelPart.Cube> radiance$cubes() {
            return cubes;
        }

        public Map<String, ModelPart> radiance$children() {
            return children;
        }
    }

    @Test
    void unchangedSubtreesReuseAndSharedModelOwnersKeepIndependentSnapshots() {
        var root = new Part();
        var child = new Part();
        root.children.put("child", child.model);
        var groups = List.of(VanillaModelPose.Group.ordinary(root.model));
        var one = new VanillaPoseMemo(groups, p -> p == root.model ? root : child);
        var two = new VanillaPoseMemo(groups, p -> p == root.model ? root : child);
        var parent = new PoseStack();
        parent.translate(.125, .5, -2);
        parent.scale(-.5F, .75F, 1.25F);
        root.model.yRot = .3F;
        child.model.x = 4;
        var first = one.evaluate(parent.last(), -1);
        assertSame(first, one.evaluate(parent.last(), -1));
        child.model.xRot = .7F;
        var changed = one.evaluate(parent.last(), -1);
        assertSame(first.getFirst(), changed.getFirst());
        assertNotSame(first.get(1), changed.get(1));
        assertNotEquals(first.get(1).pose().pose(), changed.get(1).pose().pose());
        root.model.zRot = -.4F;
        var second = two.evaluate(parent.last(), 0xffa04020);
        assertNotEquals(changed.getFirst().pose().pose(), second.getFirst().pose().pose());
        assertEquals(-1, changed.getFirst().color());
        root.model.zRot = 0;
        assertSame(changed, one.evaluate(parent.last(), -1));
        root.model.visible = false;
        assertTrue(one.evaluate(parent.last(), -1).isEmpty());
        root.model.visible = true;
        root.model.skipDraw = true;
        assertEquals(
                List.of(child.model),
                one.evaluate(parent.last(), -1).stream().map(VanillaModelPose.Part::part).toList());
        root.children.clear();
        assertTrue(one.evaluate(parent.last(), -1).isEmpty());
        root.model.skipDraw = false;
        var last = one.evaluate(parent.last(), -1).getFirst();
        var expected = new PoseStack();
        expected.last().pose().set(parent.last().pose());
        expected.last().normal().set(parent.last().normal());
        root.model.translateAndRotate(expected);
        assertEquals(expected.last().pose(), last.pose().pose());
        assertEquals(expected.last().normal(), last.pose().normal());
    }
}
