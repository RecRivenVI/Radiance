package com.radiance.client.vertex;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;

class BakedModelTakeoverTest {
    @Test
    void sameModelClassDoesNotDeclareAnImmutableResource() {
        var faces = new java.util.EnumMap<Direction, List<BakedQuad>>(Direction.class);
        for (var direction : Direction.values()) faces.put(direction, List.of());
        var model =
                new net.minecraft.client.resources.model.SimpleBakedModel(
                        List.of(),
                        faces,
                        true,
                        true,
                        true,
                        null,
                        null,
                        net.minecraft.client.renderer.block.model.ItemOverrides.EMPTY);
        BakedModelTakeover.publishModels(List.of(model));
        assertFalse(BakedModelTakeover.owns(model));
        BakedModelTakeover.declareBakedResource(model);
        BakedModelTakeover.publishModels(List.of(model));
        assertTrue(BakedModelTakeover.owns(model));
        BakedModelTakeover.publishModels(List.of());
        assertFalse(BakedModelTakeover.owns(model));
    }

    @Test
    void sourceRegistrationChecksTheWholeBudgetBeforeCopyingVertices() {
        var q = new BakedQuad(new int[32], -1, Direction.UP, null, true);
        var faces = new java.util.EnumMap<Direction, List<BakedQuad>>(Direction.class);
        for (var direction : Direction.values()) faces.put(direction, List.of());
        var model =
                new net.minecraft.client.resources.model.SimpleBakedModel(
                        List.of(q, q), faces, true, true, true, null, null, null);
        var resource = new BakedModelTakeover.Source(model, 512);
        assertEquals(2, resource.quads.size());
        assertEquals(512, resource.bytes);
        assertThrows(
                IllegalArgumentException.class, () -> new BakedModelTakeover.Source(model, 256));
        var oversized =
                new net.minecraft.client.resources.model.SimpleBakedModel(
                        java.util.Collections.nCopies(65537, q),
                        faces,
                        true,
                        true,
                        true,
                        null,
                        null,
                        null);
        assertThrows(
                IllegalArgumentException.class,
                () -> new BakedModelTakeover.Source(oversized, 32L * 1024 * 1024));
    }

    @Test
    void itemAppearanceKeepsArgbAlphaAndExistingVertexColour() {
        var plain = new BakedQuad(new int[32], -1, Direction.UP, null, true);
        var tinted = new BakedQuad(new int[32], 2, Direction.DOWN, null, false);
        var input =
                new BakedModelTakeover.Appearance(123, 456, true, 1, 1, 1, List.of(-1, 0x80402010));
        var recipe = BakedModelTakeover.recipe(List.of(plain, tinted), input);
        assertArrayEquals(new float[] {1, 1, 1, 1}, recipe.getFirst().color);
        assertArrayEquals(
                new float[] {64 / 255f, 32 / 255f, 16 / 255f, 128 / 255f}, recipe.getLast().color);
        assertTrue(recipe.getLast().existing);
        assertArrayEquals(new int[] {123, 123, 123, 123}, recipe.getLast().lights);
        assertEquals(456, recipe.getLast().overlay);
    }

    @Test
    void frameModelColourOnlyTintsMarkedFacesAndDoesNotReadExistingColour() {
        var a = new BakedQuad(new int[32], -1, Direction.UP, null, true);
        var b = new BakedQuad(new int[32], 0, Direction.UP, null, true);
        var recipe =
                BakedModelTakeover.recipe(
                        List.of(a, b),
                        new BakedModelTakeover.Appearance(
                                7, 8, false, .2f, .4f, .6f, List.of(-1, -1)));
        assertArrayEquals(new float[] {1, 1, 1, 1}, recipe.getFirst().color);
        assertArrayEquals(new float[] {.2f, .4f, .6f, 1}, recipe.getLast().color);
        assertFalse(recipe.getLast().existing);
        assertArrayEquals(new float[] {1, 1, 1, 1}, recipe.getLast().brightness);
    }

    @Test
    void publishedRecipeOwnsVerticesInsteadOfCachingTheLastWorldPose() {
        var vertices = new int[32];
        vertices[0] = Float.floatToIntBits(.125f);
        var quad = new BakedQuad(vertices, -1, Direction.NORTH, null, true);
        var recipe =
                BakedModelTakeover.recipe(
                        List.of(quad),
                        new BakedModelTakeover.Appearance(0, 0, true, 1, 1, 1, List.of(-1)));
        vertices[0] = 99;
        assertEquals(Float.floatToIntBits(.125f), recipe.getFirst().vertices[0]);
    }
}
