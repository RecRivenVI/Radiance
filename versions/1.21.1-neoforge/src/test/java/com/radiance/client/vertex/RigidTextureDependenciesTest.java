package com.radiance.client.vertex;

import static org.junit.jupiter.api.Assertions.*;

import com.mojang.blaze3d.vertex.MeshData;
import com.radiance.client.texture.TextureTasks;
import com.radiance.render.contract.DrawStateSnapshot;
import java.util.*;
import net.minecraft.client.renderer.RenderType;
import org.junit.jupiter.api.Test;

class RigidTextureDependenciesTest {
    private record Geometry(List<TextureTasks.Owner> textures)
            implements RigidModelCapture.Geometry {
        public long id() {
            return 1;
        }

        public RenderType layer() {
            throw new AssertionError("No rendering required");
        }

        public TextureTasks.Owner texture() {
            return textures.getFirst();
        }

        public MeshData mesh() {
            throw new AssertionError("No rendering required");
        }
    }

    @Test
    void boundFoilCannotSubmitWhenItsAuxiliaryTextureGenerationHasRetired() {
        var tasks = new TextureTasks(new Object());
        var base = tasks.register(1);
        var foil = tasks.register(2);
        var trim = tasks.register(3);
        var baseGeometry = new Geometry(List.of(base, foil));
        var trimGeometry = new Geometry(List.of(trim));
        var bindings =
                List.of(
                        new RigidModelCapture.Binding(baseGeometry, 0, DrawStateSnapshot.IDENTITY),
                        new RigidModelCapture.Binding(trimGeometry, 0, DrawStateSnapshot.IDENTITY));
        Set<TextureTasks.Owner> owners = new HashSet<>();
        RigidModelCapture.collectTextures(baseGeometry, bindings, owners);
        assertEquals(Set.of(base, foil, trim), owners);
        assertTrue(tasks.useAll(owners, () -> {}));
        tasks.release(foil, () -> {});
        tasks.register(2);
        assertFalse(tasks.useAll(owners, () -> fail("Retired foil must not cross JNI")));
    }
}
