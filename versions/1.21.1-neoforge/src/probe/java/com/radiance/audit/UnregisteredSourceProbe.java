package com.radiance.audit;

import com.radiance.mixin_related.SourceCompatibility;

/** Counts actual altered-source execution; no diagnostic source is accepted by the product. */
public final class UnregisteredSourceProbe {
    private static long cubes;

    private UnregisteredSourceProbe() {}

    public static void changedCube() {
        cubes++;
    }

    public static void verify() {
        if (!Boolean.getBoolean("radiance.audit.unregisteredSource")) return;
        if (SourceCompatibility.compatible(SourceCompatibility.Source.PARTS) || cubes < 1)
            throw new IllegalStateException(
                    "Unknown source did not use actual changed Cube.compile");
        int[] vertices = {0};
        int sourceColour = 0xffaabbcc;
        var sink =
                new com.mojang.blaze3d.vertex.VertexConsumer() {
                    @Override
                    public void addVertex(
                            float x,
                            float y,
                            float z,
                            int colour,
                            float u,
                            float v,
                            int overlay,
                            int light,
                            float nx,
                            float ny,
                            float nz) {
                        if (colour != ((sourceColour & 0xff00ffff) | 0x00400000))
                            throw new AssertionError("Changed source colour was not retained");
                        vertices[0]++;
                    }

                    public com.mojang.blaze3d.vertex.VertexConsumer addVertex(
                            float x, float y, float z) {
                        throw new AssertionError();
                    }

                    public com.mojang.blaze3d.vertex.VertexConsumer setColor(
                            int r, int g, int b, int a) {
                        throw new AssertionError();
                    }

                    public com.mojang.blaze3d.vertex.VertexConsumer setUv(float u, float v) {
                        throw new AssertionError();
                    }

                    public com.mojang.blaze3d.vertex.VertexConsumer setUv1(int u, int v) {
                        throw new AssertionError();
                    }

                    public com.mojang.blaze3d.vertex.VertexConsumer setUv2(int u, int v) {
                        throw new AssertionError();
                    }

                    public com.mojang.blaze3d.vertex.VertexConsumer setNormal(
                            float x, float y, float z) {
                        throw new AssertionError();
                    }
                };
        var cube =
                new net.minecraft.client.model.geom.ModelPart.Cube(
                        0,
                        0,
                        0,
                        0,
                        0,
                        2,
                        2,
                        2,
                        0,
                        0,
                        0,
                        false,
                        16,
                        16,
                        java.util.EnumSet.allOf(net.minecraft.core.Direction.class));
        cube.compile(
                new com.mojang.blaze3d.vertex.PoseStack().last(),
                sink,
                15728880,
                10 << 16,
                sourceColour);
        if (vertices[0] != 24) throw new AssertionError("Changed source topology");
        int fallbackVertices = verifyCapturedOutput(sourceColour);
        var verdicts = SourceCompatibility.coverage();
        try {
            var directory = java.nio.file.Path.of(System.getProperty("validation.result"));
            java.nio.file.Files.createDirectories(directory);
            java.nio.file.Files.writeString(
                    directory.resolve("unregistered-source.json"),
                    new com.google.gson.Gson()
                                    .toJson(
                                            java.util.Map.of(
                                                    "changedSourceCubeCalls",
                                                    cubes,
                                                    "changedSourceVerticesCompared",
                                                    vertices[0],
                                                    "partsRetreated",
                                                    true,
                                                    "capturedVerticesCompared",
                                                    fallbackVertices,
                                                    "coverage",
                                                    verdicts))
                            + "\n");
        } catch (java.io.IOException failure) {
            throw new java.io.UncheckedIOException(failure);
        }
    }

    private static int verifyCapturedOutput(int colour) {
        var mesh = new net.minecraft.client.model.geom.builders.MeshDefinition();
        mesh.getRoot()
                .addOrReplaceChild(
                        "cube",
                        net.minecraft.client.model.geom.builders.CubeListBuilder.create()
                                .texOffs(0, 0)
                                .addBox(0, 0, 0, 2, 2, 2),
                        net.minecraft.client.model.geom.PartPose.ZERO);
        var root =
                net.minecraft.client.model.geom.builders.LayerDefinition.create(mesh, 16, 16)
                        .bakeRoot();
        var mc = net.minecraft.client.Minecraft.getInstance();
        var layer =
                net.minecraft.client.renderer.RenderType.entityCutoutNoCull(
                        net.minecraft.resources.ResourceLocation.withDefaultNamespace(
                                "textures/entity/zombie/zombie.png"));
        try (var provider = new Provider();
                var allocation = new com.mojang.blaze3d.vertex.ByteBufferBuilder(8192)) {
            provider.enableRigidModels(799001);
            provider.enablePartModels(
                    root, mc.getEntityRenderDispatcher().getRenderer(mc.player), false);
            var actual = (com.radiance.client.vertex.PBRVertexConsumer) provider.getBuffer(layer);
            var pose = new com.mojang.blaze3d.vertex.PoseStack();
            pose.translate(1.25, -.75, 2.5);
            root.render(pose, actual, 15728880, 10 << 16, colour);
            var expected = new com.radiance.client.vertex.PBRVertexConsumer(allocation, layer);
            root.render(pose, expected, 15728880, 10 << 16, colour);
            if (!provider.takeRigidModels().isEmpty())
                throw new AssertionError(
                        "Unknown source unexpectedly submitted persistent geometry");
            try (var a = actual.end();
                    var b = expected.end()) {
                if (a.drawState().vertexCount() != 24
                        || a.vertexBuffer().mismatch(b.vertexBuffer()) != -1)
                    throw new AssertionError(
                            "Unknown source changed through the captured world consumer");
                return a.drawState().vertexCount();
            }
        }
    }

    private static final class Provider
            extends com.radiance.client.vertex.StorageVertexConsumerProvider
            implements AutoCloseable {
        Provider() {
            super(8192);
        }
    }
}
