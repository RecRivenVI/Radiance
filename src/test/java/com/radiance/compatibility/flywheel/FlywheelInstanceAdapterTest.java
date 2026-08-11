package com.radiance.compatibility.flywheel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.engine_room.flywheel.api.instance.Instance;
import dev.engine_room.flywheel.api.instance.InstanceHandle;
import dev.engine_room.flywheel.api.layout.FloatRepr;
import dev.engine_room.flywheel.api.layout.IntegerRepr;
import dev.engine_room.flywheel.api.layout.Layout;
import dev.engine_room.flywheel.api.layout.LayoutBuilder;
import dev.engine_room.flywheel.lib.instance.InstanceTypes;
import dev.engine_room.flywheel.lib.instance.SimpleInstanceType;
import org.junit.jupiter.api.Test;

class FlywheelInstanceAdapterTest {
    @Test
    void validatesAllEightSupportedWriterLayouts() {
        assertEquals(FlywheelInstanceAdapter.TRANSFORMED,
            FlywheelInstanceAdapter.require(InstanceTypes.TRANSFORMED));
        assertEquals(FlywheelInstanceAdapter.POSED,
            FlywheelInstanceAdapter.require(InstanceTypes.POSED));
        assertEquals(FlywheelInstanceAdapter.ORIENTED,
            FlywheelInstanceAdapter.require(InstanceTypes.ORIENTED));
        assertEquals(FlywheelInstanceAdapter.SHADOW,
            FlywheelInstanceAdapter.require(InstanceTypes.SHADOW));
        assertEquals(FlywheelInstanceAdapter.ROTATING,
            FlywheelInstanceAdapter.require(type("create:instance/rotating.vert",
                LayoutBuilder.create().vector("color", FloatRepr.NORMALIZED_UNSIGNED_BYTE, 4)
                    .vector("light", IntegerRepr.SHORT, 2).vector("overlay", IntegerRepr.SHORT, 2)
                    .vector("rotation", FloatRepr.FLOAT, 4).vector("pos", FloatRepr.FLOAT, 3)
                    .scalar("speed", FloatRepr.FLOAT).scalar("offset", FloatRepr.FLOAT)
                    .vector("axis", FloatRepr.NORMALIZED_BYTE, 3).build())));
        assertEquals(FlywheelInstanceAdapter.SCROLLING,
            FlywheelInstanceAdapter.require(type("create:instance/scrolling.vert",
                scrollingLayout(false))));
        assertEquals(FlywheelInstanceAdapter.SCROLLING_TRANSFORMED,
            FlywheelInstanceAdapter.require(type("create:instance/scrolling_transformed.vert",
                scrollingLayout(true))));
        assertEquals(FlywheelInstanceAdapter.FLUID,
            FlywheelInstanceAdapter.require(type("create:instance/fluid.vert",
                LayoutBuilder.create().matrix("pose", FloatRepr.FLOAT, 4)
                    .vector("color", FloatRepr.NORMALIZED_UNSIGNED_BYTE, 4)
                    .vector("light", IntegerRepr.SHORT, 2).vector("overlay", IntegerRepr.SHORT, 2)
                    .scalar("progress", FloatRepr.FLOAT).scalar("vScale", FloatRepr.FLOAT)
                    .scalar("v0", FloatRepr.FLOAT).build())));
    }

    private static Layout scrollingLayout(boolean transformed) {
        LayoutBuilder builder = LayoutBuilder.create();
        if (transformed) builder.matrix("pose", FloatRepr.FLOAT, 4);
        builder.vector("color", FloatRepr.NORMALIZED_UNSIGNED_BYTE, 4)
            .vector("light", IntegerRepr.SHORT, 2).vector("overlay", IntegerRepr.SHORT, 2);
        if (!transformed) {
            builder.vector("pos", FloatRepr.FLOAT, 3).vector("rotation", FloatRepr.FLOAT, 4);
        }
        return builder.vector("speed", FloatRepr.FLOAT, 2).vector("diff", FloatRepr.FLOAT, 2)
            .vector("scale", FloatRepr.FLOAT, 2).vector("offset", FloatRepr.FLOAT, 2).build();
    }

    private static SimpleInstanceType<TestInstance> type(String shader, Layout layout) {
        return SimpleInstanceType.builder(TestInstance::new).layout(layout)
            .writer((ptr, instance) -> { })
            .vertexShader(net.minecraft.resources.ResourceLocation.parse(shader))
            .cullShader(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(
                "test", "instance/cull.glsl")).build();
    }

    @Test
    void rejectsUnknownInstanceShaderInsteadOfGuessingFromLayout() {
        var unknown = SimpleInstanceType.builder(TestInstance::new)
            .layout(InstanceTypes.TRANSFORMED.layout())
            .writer((ptr, instance) -> { })
            .vertexShader(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(
                "test", "instance/unknown.vert"))
            .cullShader(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(
                "test", "instance/cull/unknown.glsl"))
            .build();
        assertThrows(UnsupportedOperationException.class,
            () -> FlywheelInstanceAdapter.require(unknown));
    }

    private record TestInstance(dev.engine_room.flywheel.api.instance.InstanceType<?> type,
                                InstanceHandle handle) implements Instance {
    }
}
