package com.radiance.client.vertex;

import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.Optional;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import com.radiance.client.util.ARGB;

public class StorageOutlineVertexConsumerProvider implements MultiBufferSource {

    /**
     * Sink for the solid model geometry of an outline render type. The priority pass paints
     * outline-group geometry as a bright solid, so the silhouette itself must be dropped; only the
     * merged edge graph emitted into the line delegate is kept.
     */
    private static final VertexConsumer DISCARD = new VertexConsumer() {
        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            return this;
        }

        @Override
        public VertexConsumer setColor(int red, int green, int blue, int alpha) {
            return this;
        }

        @Override
        public VertexConsumer setUv(float u, float v) {
            return this;
        }

        @Override
        public VertexConsumer setUv1(int u, int v) {
            return this;
        }

        @Override
        public VertexConsumer setUv2(int u, int v) {
            return this;
        }

        @Override
        public VertexConsumer setNormal(float x, float y, float z) {
            return this;
        }
    };

    private final MultiBufferSource parent;
    private final MultiBufferSource outlineParent;
    private int red = 255;
    private int green = 255;
    private int blue = 255;
    private int alpha = 255;

    public StorageOutlineVertexConsumerProvider(MultiBufferSource parent,
        MultiBufferSource outlineParent) {
        this.parent = parent;
        this.outlineParent = outlineParent;
    }

    @Override
    public VertexConsumer getBuffer(RenderType renderLayer) {
        if (renderLayer.isOutline()) {
            // An invisible-but-glowing entity is routed by vanilla to RenderType.outline(), a solid
            // model silhouette. Drawing that through the priority outline shader would fill the
            // whole model; re-emit the model's merged edges instead so only the glow is drawn,
            // exactly like the visible-glow path below.
            VertexConsumer edgeConsumer = this.outlineParent.getBuffer(RenderType.lines());
            return new WorldOutlineVertexConsumer(DISCARD, edgeConsumer,
                ARGB.color(this.alpha, this.red, this.green, this.blue));
        } else {
            VertexConsumer vertexConsumer = this.parent.getBuffer(renderLayer);
            Optional<RenderType> optional = renderLayer.outline();
            if (optional.isPresent()) {
                VertexConsumer vertexConsumer2 = this.outlineParent.getBuffer(RenderType.lines());
                return new WorldOutlineVertexConsumer(vertexConsumer, vertexConsumer2,
                    ARGB.color(this.alpha, this.red, this.green, this.blue));
            } else {
                return vertexConsumer;
            }
        }
    }

    /**
     * The ordinary delegate receives the original textured model. ModelPart's target mixin
     * separately emits a unioned line graph into {@code outlineDelegate}; this avoids copying
     * every model face into a late silhouette mask.
     */
    public record WorldOutlineVertexConsumer(VertexConsumer delegate,
                                             VertexConsumer outlineDelegate,
                                             int outlineColor) implements VertexConsumer {

        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            this.delegate.addVertex(x, y, z);
            return this;
        }

        @Override
        public VertexConsumer setColor(int red, int green, int blue, int alpha) {
            this.delegate.setColor(red, green, blue, alpha);
            return this;
        }

        @Override
        public VertexConsumer setUv(float u, float v) {
            this.delegate.setUv(u, v);
            return this;
        }

        @Override
        public VertexConsumer setUv1(int u, int v) {
            this.delegate.setUv1(u, v);
            return this;
        }

        @Override
        public VertexConsumer setUv2(int u, int v) {
            this.delegate.setUv2(u, v);
            return this;
        }

        @Override
        public VertexConsumer setNormal(float x, float y, float z) {
            this.delegate.setNormal(x, y, z);
            return this;
        }
    }

    public void setColor(int red, int green, int blue, int alpha) {
        this.red = red;
        this.green = green;
        this.blue = blue;
        this.alpha = alpha;
    }
}
