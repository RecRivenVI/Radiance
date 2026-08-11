package com.radiance.client.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.util.List;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.Shapes;
import org.junit.jupiter.api.Test;

class WorldOutlineGeometryTest {

    @Test
    void adjacentBoxesBecomeOneOuterCube() {
        var shape = Shapes.or(
            Shapes.create(new AABB(0.0, 0.0, 0.0, 0.5, 1.0, 1.0)),
            Shapes.create(new AABB(0.5, 0.0, 0.0, 1.0, 1.0, 1.0)));

        List<WorldOutlineGeometry.Edge> edges = WorldOutlineGeometry.collectShapeEdges(shape);

        assertEquals(12, edges.size());
        assertFalse(edges.stream().anyMatch(edge -> edge.start().x() == 0.5
            && edge.end().x() == 0.5));
    }

    @Test
    void duplicateOverlappingBoxesDoNotDuplicateEdges() {
        var box = Shapes.create(new AABB(0.0, 0.0, 0.0, 1.0, 1.0, 1.0));
        assertEquals(12, WorldOutlineGeometry.collectShapeEdges(Shapes.or(box, box)).size());
    }

    @Test
    void emittedLinesSatisfyPositionColorNormalFormat() {
        var box = Shapes.create(new AABB(0.0, 0.0, 0.0, 1.0, 1.0, 1.0));
        try (ByteBufferBuilder bytes = new ByteBufferBuilder(4096)) {
            BufferBuilder lines = new BufferBuilder(bytes, VertexFormat.Mode.LINES,
                DefaultVertexFormat.POSITION_COLOR_NORMAL);
            WorldOutlineGeometry.emitShapeEdges(new PoseStack().last(), lines, box, 0xFFFFFFFF);
            try (var mesh = lines.buildOrThrow()) {
                assertNotNull(mesh);
            }
        }
    }
}
