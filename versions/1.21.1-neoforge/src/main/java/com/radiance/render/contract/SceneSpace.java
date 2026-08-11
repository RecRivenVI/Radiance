package com.radiance.render.contract;

/** Space of captured positions, independent of the renderer that produced them. */
public enum SceneSpace {
    WORLD(RenderAbi.COORDINATE_WORLD),
    CAMERA(RenderAbi.COORDINATE_CAMERA),
    CAMERA_SHIFT(RenderAbi.COORDINATE_CAMERA_SHIFT);

    private final int value;

    SceneSpace(int value) {
        this.value = value;
    }

    public int value() {
        return value;
    }
}
