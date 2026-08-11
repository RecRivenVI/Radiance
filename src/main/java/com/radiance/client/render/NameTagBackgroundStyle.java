package com.radiance.client.render;

public final class NameTagBackgroundStyle {

    public static final int ALPHA = 128;

    private NameTagBackgroundStyle() {
    }

    public static int applyOpacity(int backgroundColor) {
        return (backgroundColor & 0x00FFFFFF) | (ALPHA << 24);
    }
}
